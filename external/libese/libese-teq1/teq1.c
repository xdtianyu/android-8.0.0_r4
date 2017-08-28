/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "include/ese/teq1.h"
#include "../libese/include/ese/ese.h"
#include "../libese/include/ese/log.h"

#include "teq1_private.h"

const char *teq1_rule_result_to_name(enum RuleResult result) {
  switch (result) {
  case kRuleResultComplete:
    return "Complete";
  case kRuleResultAbort:
    return "Abort";
  case kRuleResultContinue:
    return "Continue";
  case kRuleResultHardFail:
    return "Hard failure";
  case kRuleResultResetDevice:
    return "Reset device";
  case kRuleResultResetSession:
    return "Reset session";
  case kRuleResultRetransmit:
    return "Retransmit";
  case kRuleResultSingleShot:
    return "Single shot";
  };
}

const char *teq1_pcb_to_name(uint8_t pcb) {
  switch (pcb) {
  case I(0, 0):
    return "I(0, 0)";
  case I(0, 1):
    return "I(0, 1)";
  case I(1, 0):
    return "I(1, 0)";
  case I(1, 1):
    return "I(1, 1)";
  case R(0, 0, 0):
    return "R(0, 0, 0)";
  case R(0, 0, 1):
    return "R(0, 0, 1)";
  case R(0, 1, 0):
    return "R(0, 1, 0)";
  case R(0, 1, 1):
    return "R(0, 1, 1)";
  case R(1, 0, 0):
    return "R(1, 0, 0)";
  case R(1, 0, 1):
    return "R(1, 0, 1)";
  case R(1, 1, 0):
    return "R(1, 1, 0)";
  case R(1, 1, 1):
    return "R(1, 1, 1)";
  case S(RESYNC, REQUEST):
    return "S(RESYNC, REQUEST)";
  case S(RESYNC, RESPONSE):
    return "S(RESYNC, RESPONSE)";
  case S(IFS, REQUEST):
    return "S(IFS, REQUEST)";
  case S(IFS, RESPONSE):
    return "S(IFS, RESPONSE)";
  case S(ABORT, REQUEST):
    return "S(ABORT, REQUEST)";
  case S(ABORT, RESPONSE):
    return "S(ABORT, RESPONSE)";
  case S(WTX, REQUEST):
    return "S(WTX, REQUEST)";
  case S(WTX, RESPONSE):
    return "S(WTX, RESPONSE)";
  case 255:
    return "INTERNAL-ERROR";
  default:
    return "???";
  }
}

void teq1_dump_buf(const char *prefix, const uint8_t *buf, uint32_t len) {
  uint32_t recvd = 0;
  for (recvd = 0; recvd < len; ++recvd)
    ALOGV("%s[%u]: %.2X", prefix, recvd, buf[recvd]);
}

int teq1_transmit(struct EseInterface *ese,
                  const struct Teq1ProtocolOptions *opts,
                  struct Teq1Frame *frame) {
  /* Set correct node address. */
  frame->header.NAD = opts->node_address;

  /* Compute the LRC */
  frame->INF[frame->header.LEN] = teq1_compute_LRC(frame);

  /*
   * If the card does something weird, like expect an CRC/LRC based on a
   * different header value, the preprocessing can handle it.
   */
  if (opts->preprocess) {
    opts->preprocess(opts, frame, 1);
  }

  /*
   * Begins transmission and ignore errors.
   * Failed transmissions will result eventually in a resync then reset.
   */
  teq1_trace_transmit(frame->header.PCB, frame->header.LEN);
  teq1_dump_transmit(frame->val, sizeof(frame->header) + frame->header.LEN + 1);
  ese->ops->hw_transmit(ese, frame->val,
                        sizeof(frame->header) + frame->header.LEN + 1, 1);
  /*
   * Even though in practice any WTX BWT extension starts when the above
   * transmit ends, it is easier to implement it in the polling timeout of
   * receive.
   */
  return 0;
}

int teq1_receive(struct EseInterface *ese,
                 const struct Teq1ProtocolOptions *opts, float timeout,
                 struct Teq1Frame *frame) {
  /* Poll the bus until we see the start of frame indicator, the interface NAD.
   */
  int bytes_consumed = ese->ops->poll(ese, opts->host_address, timeout, 0);
  if (bytes_consumed < 0 || bytes_consumed > 1) {
    /* Timed out or comm error. */
    ALOGV("%s: comm error: %d", __func__, bytes_consumed);
    return -1;
  }
  /* We polled for the NAD above -- if it was consumed, set it here. */
  if (bytes_consumed) {
    frame->header.NAD = opts->host_address;
  }
  /* Get the remainder of the header, but keep the line &open. */
  ese->ops->hw_receive(ese, (uint8_t *)(&frame->header.NAD + bytes_consumed),
                       sizeof(frame->header) - bytes_consumed, 0);
  teq1_dump_receive((uint8_t *)(&frame->header.NAD + bytes_consumed),
                    sizeof(frame->header) - bytes_consumed);
  if (frame->header.LEN == 255) {
    ALOGV("received invalid LEN of 255");
    /* Close the receive window and return failure. */
    ese->ops->hw_receive(ese, NULL, 0, 1);
    return -1;
  }
  /*
   * Get the data and the first byte of CRC data.
   * Note, CRC support is not implemented. Only a single LRC byte is expected.
   */
  ese->ops->hw_receive(ese, (uint8_t *)(&(frame->INF[0])),
                       frame->header.LEN + 1, 1);
  teq1_dump_receive((uint8_t *)(&(frame->INF[0])), frame->header.LEN + 1);
  teq1_trace_receive(frame->header.PCB, frame->header.LEN);

  /*
   * If the card does something weird, like expect an CRC/LRC based on a
   * different
   * header value, the preprocessing should fix up here prior to the LRC check.
   */
  if (opts->preprocess) {
    opts->preprocess(opts, frame, 0);
  }

  /* LRC and other protocol goodness checks are not done here. */
  return frame->header.LEN; /* Return data bytes read. */
}

uint8_t teq1_fill_info_block(struct Teq1State *state, struct Teq1Frame *frame) {
  uint32_t inf_len = INF_LEN;
  if (state->ifs < inf_len) {
    inf_len = state->ifs;
  }
  switch (bs_get(PCB.type, frame->header.PCB)) {
  case kPcbTypeInfo0:
  case kPcbTypeInfo1: {
    uint32_t len = state->app_data.tx_len;
    if (len > inf_len) {
      len = inf_len;
    }
    ese_memcpy(frame->INF, state->app_data.tx_buf, len);
    frame->header.LEN = (len & 0xff);
    ALOGV("Copying %x bytes of app data for transmission", frame->header.LEN);
    /* Incrementing here means the caller MUST handle retransmit with prepared
     * data. */
    state->app_data.tx_len -= len;
    state->app_data.tx_buf += len;
    /* Perform chained transmission if needed. */
    bs_assign(&frame->header.PCB, PCB.I.more_data, 0);
    if (state->app_data.tx_len > 0) {
      frame->header.PCB |= bs_mask(PCB.I.more_data, 1);
    }
    return len;
  }
  case kPcbTypeSupervisory:
  case kPcbTypeReceiveReady:
  default:
    break;
  }
  return 255; /* Invalid block type. */
}

void teq1_get_app_data(struct Teq1State *state, struct Teq1Frame *frame) {
  switch (bs_get(PCB.type, frame->header.PCB)) {
  case kPcbTypeInfo0:
  case kPcbTypeInfo1: {
    uint8_t len = frame->header.LEN;
    /* TODO(wad): Some data will be left on the table. Should this error out? */
    if (len > state->app_data.rx_len) {
      len = state->app_data.rx_len;
    }
    ese_memcpy(state->app_data.rx_buf, frame->INF, len);
    /* The original caller must retain the starting pointer to determine
     * actual available data.
     */
    state->app_data.rx_len -= len;
    state->app_data.rx_buf += len;
    return;
  }
  case kPcbTypeReceiveReady:
  case kPcbTypeSupervisory:
  default:
    break;
  }
  return;
}

/* Returns an R(0) frame with error bits set. */
uint8_t teq1_frame_error_check(struct Teq1State *state,
                               struct Teq1Frame *tx_frame,
                               struct Teq1Frame *rx_frame) {
  uint8_t lrc = 0;
  int chained = 0;
  if (rx_frame->header.PCB == 255) {
    return R(0, 1, 0); /* Other error */
  }

  lrc = teq1_compute_LRC(rx_frame);
  if (rx_frame->INF[rx_frame->header.LEN] != lrc) {
    ALOGE("Invalid LRC %x instead of %x", rx_frame->INF[rx_frame->header.LEN],
          lrc);
    return R(0, 0, 1); /* Parity error */
  }

  /* Check if we were chained and increment the last sent sequence. */
  switch (bs_get(PCB.type, tx_frame->header.PCB)) {
  case kPcbTypeInfo0:
  case kPcbTypeInfo1:
    chained = bs_get(PCB.I.more_data, tx_frame->header.PCB);
    state->card_state->seq.interface =
        bs_get(PCB.I.send_seq, tx_frame->header.PCB);
  }

  /* Check if we've gone down an easy to catch error hole. The rest will turn up
   * on the
   * txrx switch.
   */
  switch (bs_get(PCB.type, rx_frame->header.PCB)) {
  case kPcbTypeSupervisory:
    if (rx_frame->header.PCB != S(RESYNC, RESPONSE) &&
        rx_frame->header.LEN != 1) {
      return R(0, 1, 0);
    }
    break;
  case kPcbTypeReceiveReady:
    if (rx_frame->header.LEN != 0) {
      return R(0, 1, 0);
    }
    break;
  case kPcbTypeInfo0:
  case kPcbTypeInfo1:
    /* I-blocks must always alternate for each endpoint. */
    if ((bs_get(PCB.I.send_seq, rx_frame->header.PCB)) ==
        state->card_state->seq.card) {
      ALOGW("Got seq %d expected %d",
            bs_get(PCB.I.send_seq, rx_frame->header.PCB),
            state->card_state->seq.card);
      return R(0, 1, 0);
    }
    /* Update the card's last I-block seq. */
    state->card_state->seq.card = bs_get(PCB.I.send_seq, rx_frame->header.PCB);
  default:
    break;
  };
  return 0;
}

enum RuleResult teq1_rules(struct Teq1State *state, struct Teq1Frame *tx_frame,
                           struct Teq1Frame *rx_frame,
                           struct Teq1Frame *next_tx) {
  /* Rule 1 is enforced by first call∴ Start with I(0,M). */
  /* 0 = TX, 1 = RX */
  /* msb = tx pcb, lsb = rx pcb */
  /* BUG_ON(!rx_frame && !tx_frame && !next_tx); */
  uint16_t txrx = TEQ1_RULE(tx_frame->header.PCB, rx_frame->header.PCB);
  uint8_t R_err;

  while (1) {
    /* Timeout errors come like invalid frames: 255. */
    if ((R_err = teq1_frame_error_check(state, tx_frame, rx_frame)) != 0) {
      ALOGW("incoming frame failed the error check");
      state->last_error_message = "Invalid frame received";
      /* Mark the frame as bad for our rule evaluation. */
      txrx = TEQ1_RULE(tx_frame->header.PCB, 255);
      state->errors++;
      /* Rule 6.4 */
      if (state->errors >= 6) {
        return kRuleResultResetDevice;
      }
      /* Rule 7.4.2 */
      if (state->errors >= 3) {
        /* Rule 7.4.1: state should start with error count = 2 */
        next_tx->header.PCB = S(RESYNC, REQUEST);
        /* Resync result in a fresh session, so we should just continue here. */
        return kRuleResultContinue;
      }
    }

    /* Specific matches */
    switch (txrx) {
    /*** Rule 2.1: I() -> I() ***/
    /* Error check will determine if the card seq is right. */
    case TEQ1_RULE(I(0, 0), I(0, 0)):
    case TEQ1_RULE(I(0, 0), I(1, 0)):
    case TEQ1_RULE(I(1, 0), I(1, 0)):
    case TEQ1_RULE(I(1, 0), I(0, 0)):
      /* Read app data & return. */
      teq1_get_app_data(state, rx_frame);
      return kRuleResultComplete;

    /* Card begins chained response. */
    case TEQ1_RULE(I(0, 0), I(0, 1)):
    case TEQ1_RULE(I(1, 0), I(1, 1)):
      /* Prep R(N(S)) */
      teq1_get_app_data(state, rx_frame);
      next_tx->header.PCB =
          TEQ1_R(!bs_get(PCB.I.send_seq, rx_frame->header.PCB), 0, 0);
      next_tx->header.LEN = 0;
      return kRuleResultContinue;

    /*** Rule 2.2, Rule 5: Chained transmission ***/
    case TEQ1_RULE(I(0, 1), R(1, 0, 0)):
    case TEQ1_RULE(I(1, 1), R(0, 0, 0)):
      /* Send next block -- error-checking assures the R seq is our next seq. */
      next_tx->header.PCB =
          TEQ1_I(bs_get(PCB.R.next_seq, rx_frame->header.PCB), 0);
      teq1_fill_info_block(state, next_tx); /* Sets M-bit and LEN. */
      return kRuleResultContinue;

    /*** Rule 3 ***/
    case TEQ1_RULE(I(0, 0), S(WTX, REQUEST)):
    case TEQ1_RULE(I(1, 0), S(WTX, REQUEST)):
      /* Note: Spec is unclear on if WTX can occur during chaining so we make it
      an error for now.
      case TEQ1_RULE(I(0, 1), S(WTX, REQUEST)):
      case TEQ1_RULE(I(1, 1), S(WTX, REQUEST)):
      */
      /* Send S(WTX, RESPONSE) with same INF */
      next_tx->header.PCB = S(WTX, RESPONSE);
      next_tx->header.LEN = 1;
      next_tx->INF[0] = rx_frame->INF[0];
      state->wait_mult = rx_frame->INF[0];
      /* Then wait BWT*INF[0] after transmission. */
      return kRuleResultSingleShot; /* Send then call back in with same tx_frame
                                       and new rx_frame. */

    /*** Rule 4 ***/
    case TEQ1_RULE(S(IFS, REQUEST), S(IFS, RESPONSE)):
      /* XXX: Check INFs match. */
      return kRuleResultComplete; /* This is treated as an unique operation. */
    case TEQ1_RULE(I(0, 0), S(IFS, REQUEST)):
    case TEQ1_RULE(I(0, 1), S(IFS, REQUEST)):
    case TEQ1_RULE(I(1, 0), S(IFS, REQUEST)):
    case TEQ1_RULE(I(1, 1), S(IFS, REQUEST)):
    /* Don't support a IFS_REQUEST if we sent an error R-block. */
    case TEQ1_RULE(R(0, 0, 0), S(IFS, REQUEST)):
    case TEQ1_RULE(R(1, 0, 0), S(IFS, REQUEST)):
      next_tx->header.PCB = S(IFS, RESPONSE);
      next_tx->header.LEN = 1;
      next_tx->INF[0] = rx_frame->INF[0];
      state->ifs = rx_frame->INF[0];
      return kRuleResultSingleShot;

    /*** Rule 5  (see Rule 2.2 for the chained-tx side. ) ***/
    case TEQ1_RULE(R(0, 0, 0), I(0, 0)):
    case TEQ1_RULE(R(1, 0, 0), I(1, 0)):
      /* Chaining ended with terminal I-block. */
      teq1_get_app_data(state, rx_frame);
      return kRuleResultComplete;
    case TEQ1_RULE(R(0, 0, 0), I(0, 1)):
    case TEQ1_RULE(R(1, 0, 0), I(1, 1)):
      /* Chaining continued; consume partial data and send R(N(S)) */
      teq1_get_app_data(state, rx_frame);
      /* The card seq bit will be tracked/validated earlier. */
      next_tx->header.PCB =
          TEQ1_R(!bs_get(PCB.I.send_seq, rx_frame->header.PCB), 0, 0);
      return kRuleResultContinue;

    /* Rule 6: Interface can send a RESYNC */
    /* Rule 6.1: timeout BWT right. No case here. */
    /* Rule 6.2, 6.3 */
    case TEQ1_RULE(S(RESYNC, REQUEST), S(RESYNC, RESPONSE)):
      /* Rule 6.5: indicates that the card should assume its prior
       * block was lost _and_ the interface gets transmit privilege,
       * so we just start fresh.
       */
      return kRuleResultResetSession; /* Start a new exchange (rule 6.3) */
    case TEQ1_RULE(S(RESYNC, REQUEST), 255):
      /* Retransmit the same frame up to 3 times. */
      return kRuleResultRetransmit;

    /* Rule 7.1, 7.5, 7.6 */
    case TEQ1_RULE(I(0, 0), 255):
    case TEQ1_RULE(I(1, 0), 255):
    case TEQ1_RULE(I(0, 1), 255):
    case TEQ1_RULE(I(1, 1), 255):
      next_tx->header.PCB = R_err;
      bs_assign(&next_tx->header.PCB, PCB.R.next_seq,
                bs_get(PCB.I.send_seq, tx_frame->header.PCB));
      ALOGW("Rule 7.1,7.5,7.6: bad rx - sending error R: %x = %s",
            next_tx->header.PCB, teq1_pcb_to_name(next_tx->header.PCB));
      return kRuleResultSingleShot; /* So we still can retransmit the original.
                                       */

    /* Caught in the error check. */
    case TEQ1_RULE(I(0, 0), R(1, 0, 0)):
    case TEQ1_RULE(I(0, 0), R(1, 0, 1)):
    case TEQ1_RULE(I(0, 0), R(1, 1, 0)):
    case TEQ1_RULE(I(0, 0), R(1, 1, 1)):
    case TEQ1_RULE(I(1, 0), R(0, 0, 0)):
    case TEQ1_RULE(I(1, 0), R(0, 0, 1)):
    case TEQ1_RULE(I(1, 0), R(0, 1, 0)):
    case TEQ1_RULE(I(1, 0), R(0, 1, 1)):
      next_tx->header.PCB =
          TEQ1_R(bs_get(PCB.I.send_seq, tx_frame->header.PCB), 0, 0);
      ALOGW("Rule 7.1,7.5,7.6: weird rx - sending error R: %x = %s",
            next_tx->header.PCB, teq1_pcb_to_name(next_tx->header.PCB));
      return kRuleResultSingleShot;

    /* Rule 7.2: Retransmit the _same_ R-block. */
    /* The remainder of this rule is implemented in the next switch. */
    case TEQ1_RULE(R(0, 0, 0), 255):
    case TEQ1_RULE(R(0, 0, 1), 255):
    case TEQ1_RULE(R(0, 1, 0), 255):
    case TEQ1_RULE(R(0, 1, 1), 255):
    case TEQ1_RULE(R(1, 0, 0), 255):
    case TEQ1_RULE(R(1, 0, 1), 255):
    case TEQ1_RULE(R(1, 1, 0), 255):
    case TEQ1_RULE(R(1, 1, 1), 255):
      return kRuleResultRetransmit;

    /* Rule 7.3 request */
    /* Note, 7.3 for transmission of S(*, RESPONSE) won't be seen because they
     * are
     * single shots.
     * Instead, the invalid block will be handled as invalid for the prior TX.
     * This should yield the correct R-block.
     */
    case TEQ1_RULE(I(0, 0), R(0, 0, 0)):
    case TEQ1_RULE(I(0, 0), R(0, 0, 1)):
    case TEQ1_RULE(I(0, 0), R(0, 1, 0)):
    case TEQ1_RULE(I(0, 0), R(0, 1, 1)):
    case TEQ1_RULE(I(1, 0), R(1, 0, 0)):
    case TEQ1_RULE(I(1, 0), R(1, 1, 0)):
    case TEQ1_RULE(I(1, 0), R(1, 0, 1)):
    case TEQ1_RULE(I(1, 0), R(1, 1, 1)):
    case TEQ1_RULE(I(0, 1), R(0, 0, 0)):
    case TEQ1_RULE(I(0, 1), R(0, 1, 0)):
    case TEQ1_RULE(I(0, 1), R(0, 0, 1)):
    case TEQ1_RULE(I(0, 1), R(0, 1, 1)):
    case TEQ1_RULE(I(1, 1), R(1, 0, 0)):
    case TEQ1_RULE(I(1, 1), R(1, 1, 0)):
    case TEQ1_RULE(I(1, 1), R(1, 0, 1)):
    case TEQ1_RULE(I(1, 1), R(1, 1, 1)):
      /* Retransmit I-block */
      return kRuleResultRetransmit;

    /* Rule 8 is card only. */
    /* Rule 9: aborting a chain.
     * If a S(ABORT) is injected into this engine, then we may have sent an
     * abort.
     */
    case TEQ1_RULE(S(ABORT, REQUEST), S(ABORT, RESPONSE)):
      /* No need to send back a R() because we want to keep transmit. */
      return kRuleResultComplete; /* If we sent it, then we are complete. */
    case TEQ1_RULE(S(ABORT, RESPONSE), R(0, 0, 0)):
    case TEQ1_RULE(S(ABORT, RESPONSE), R(1, 0, 0)):
      /* Card triggered abortion complete but we can resume sending. */
      return kRuleResultAbort;
    /* An abort request can interrupt a chain anywhere and could occur
     * after a failure path too.
     */
    case TEQ1_RULE(I(0, 1), S(ABORT, REQUEST)):
    case TEQ1_RULE(I(1, 1), S(ABORT, REQUEST)):
    case TEQ1_RULE(R(0, 0, 0), S(ABORT, REQUEST)):
    case TEQ1_RULE(R(0, 0, 1), S(ABORT, REQUEST)):
    case TEQ1_RULE(R(0, 1, 0), S(ABORT, REQUEST)):
    case TEQ1_RULE(R(0, 1, 1), S(ABORT, REQUEST)):
    case TEQ1_RULE(R(1, 0, 0), S(ABORT, REQUEST)):
    case TEQ1_RULE(R(1, 0, 1), S(ABORT, REQUEST)):
    case TEQ1_RULE(R(1, 1, 0), S(ABORT, REQUEST)):
    case TEQ1_RULE(R(1, 1, 1), S(ABORT, REQUEST)):
      next_tx->header.PCB = S(ABORT, REQUEST);
      return kRuleResultContinue; /* Takes over prior flow. */
    case TEQ1_RULE(S(ABORT, RESPONSE), 255):
      return kRuleResultRetransmit;
    /* Note, other blocks should be caught below. */
    default:
      break;
    }

    /* Note, only S(ABORT, REQUEST) and S(IFS, REQUEST) are supported
     * for transmitting to the card. Others will result in error
     * flows.
     *
     * For supported flows: If an operation was paused to
     * send it, the caller may then switch to that state and resume.
     */
    if (rx_frame->header.PCB != 255) {
      ALOGW("Unexpected frame. Marking error and re-evaluating.");
      rx_frame->header.PCB = 255;
      continue;
    }

    return kRuleResultHardFail;
  }
}

/*
 * TODO(wad): Consider splitting teq1_transcieve() into
 *   teq1_transcieve_init() and teq1_transceive_process_one()
 *   if testing becomes onerous given the loop below.
 */

API uint32_t teq1_transceive(struct EseInterface *ese,
                             const struct Teq1ProtocolOptions *opts,
                             const uint8_t *const tx_buf, uint32_t tx_len,
                             uint8_t *rx_buf, uint32_t rx_len) {
  struct Teq1Frame tx_frame[2];
  struct Teq1Frame rx_frame;
  struct Teq1Frame *tx = &tx_frame[0];
  int active = 0;
  bool was_reset = false;
  bool done = false;
  enum RuleResult result = kRuleResultComplete;
  struct Teq1CardState *card_state = (struct Teq1CardState *)(&ese->pad[0]);
  struct Teq1State init_state =
      TEQ1_INIT_STATE(tx_buf, tx_len, rx_buf, rx_len, card_state);
  struct Teq1State state =
      TEQ1_INIT_STATE(tx_buf, tx_len, rx_buf, rx_len, card_state);

  _static_assert(TEQ1HEADER_SIZE == sizeof(struct Teq1Header),
                 "Ensure compiler alignment/padding matches wire protocol.");
  _static_assert(TEQ1FRAME_SIZE == sizeof(struct Teq1Frame),
                 "Ensure compiler alignment/padding matches wire protocol.");

  /* First I-block is always I(0, M). After that, modulo 2. */
  tx->header.PCB = TEQ1_I(!card_state->seq.interface, 0);
  teq1_fill_info_block(&state, tx);

  teq1_trace_header();
  while (!done) {
    /* Populates the node address and LRC prior to attempting to transmit. */
    teq1_transmit(ese, opts, tx);

    /* If tx was pointed to the inactive frame for a single shot, restore it
     * now. */
    tx = &tx_frame[active];

    /* Clear the RX frame. */
    ese_memset(&rx_frame, 0xff, sizeof(rx_frame));

    /* -1 indicates a timeout or failure from hardware. */
    if (teq1_receive(ese, opts, opts->bwt * (float)state.wait_mult, &rx_frame) <
        0) {
      /* TODO(wad): If the ese_error(ese) == 1, should this go ahead and fail?
       */
      /* Failures are considered invalid blocks in the rule engine below. */
      rx_frame.header.PCB = 255;
    }
    /* Always reset |wait_mult| once we have calculated the timeout. */
    state.wait_mult = 1;

    /* Clear the inactive frame header for use as |next_tx|. */
    ese_memset(&tx_frame[!active].header, 0, sizeof(tx_frame[!active].header));

    result = teq1_rules(&state, tx, &rx_frame, &tx_frame[!active]);
    ALOGV("[ %s ]", teq1_rule_result_to_name(result));
    switch (result) {
    case kRuleResultComplete:
      done = true;
      break;
    case kRuleResultRetransmit:
      /* TODO(wad) Find a clean way to move into teq1_rules(). */
      if (state.retransmits++ < 3) {
        continue;
      }
      if (tx->header.PCB == S(RESYNC, REQUEST)) {
        ese_set_error(ese, kTeq1ErrorHardFail);
        return 0;
      }
      /* Fall through */
      tx_frame[!active].header.PCB = S(RESYNC, REQUEST);
    case kRuleResultContinue:
      active = !active;
      tx = &tx_frame[active];
      state.retransmits = 0;
      state.errors = 0;
      continue;
    case kRuleResultHardFail:
      ese_set_error(ese, kTeq1ErrorHardFail);
      return 0;
    case kRuleResultAbort:
      ese_set_error(ese, kTeq1ErrorAbort);
      return 0;
    case kRuleResultSingleShot:
      /*
       * Send the next_tx on loop, but tell the rule engine that
       * the last sent state hasn't changed. This allows for easy error
       * and supervisory block paths without nesting state.
       */
      tx = &tx_frame[!active];
      continue;
    case kRuleResultResetDevice:
      if (was_reset || !ese->ops->hw_reset || ese->ops->hw_reset(ese) == -1) {
        ese_set_error(ese, kTeq1ErrorDeviceReset);
        return 0; /* Don't keep resetting -- hard fail. */
      }
      was_reset = true;
    /* Fall through to session reset. */
    case kRuleResultResetSession:
      /* Roll back state and reset. */
      state = init_state;
      TEQ1_INIT_CARD_STATE(state.card_state);
      /* Reset the active frame. */
      ese_memset(tx, 0, sizeof(*tx));
      /* Load initial I-block. */
      tx->header.PCB = I(0, 0);
      teq1_fill_info_block(&state, tx);
      continue;
    }
  }
  /* Return the number of bytes used in rx_buf. */
  return rx_len - state.app_data.rx_len;
}

API uint8_t teq1_compute_LRC(const struct Teq1Frame *frame) {
  uint8_t lrc = 0;
  const uint8_t *buffer = frame->val;
  const uint8_t *end = buffer + frame->header.LEN + sizeof(frame->header);
  while (buffer < end) {
    lrc ^= *buffer++;
  }
  return lrc;
}
