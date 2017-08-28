/******************************************************************************
 *
 *  Copyright (C) 2016 The Android Open Source Project
 *  Copyright (C) 2009-2012 Broadcom Corporation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

#define LOG_TAG "bt_btif_a2dp_control"

#include <base/logging.h>
#include <stdbool.h>
#include <stdint.h>

#include "audio_a2dp_hw/include/audio_a2dp_hw.h"
#include "bt_common.h"
#include "btif_a2dp.h"
#include "btif_a2dp_control.h"
#include "btif_a2dp_sink.h"
#include "btif_a2dp_source.h"
#include "btif_av.h"
#include "btif_av_co.h"
#include "btif_hf.h"
#include "osi/include/osi.h"
#include "uipc.h"

#define A2DP_DATA_READ_POLL_MS 10

static void btif_a2dp_data_cb(tUIPC_CH_ID ch_id, tUIPC_EVENT event);
static void btif_a2dp_ctrl_cb(tUIPC_CH_ID ch_id, tUIPC_EVENT event);

/* We can have max one command pending */
static tA2DP_CTRL_CMD a2dp_cmd_pending = A2DP_CTRL_CMD_NONE;

void btif_a2dp_control_init(void) {
  UIPC_Init(NULL);
  UIPC_Open(UIPC_CH_ID_AV_CTRL, btif_a2dp_ctrl_cb);
}

void btif_a2dp_control_cleanup(void) {
  /* This calls blocks until UIPC is fully closed */
  UIPC_Close(UIPC_CH_ID_ALL);
}

static void btif_a2dp_recv_ctrl_data(void) {
  tA2DP_CTRL_CMD cmd = A2DP_CTRL_CMD_NONE;
  int n;

  uint8_t read_cmd = 0; /* The read command size is one octet */
  n = UIPC_Read(UIPC_CH_ID_AV_CTRL, NULL, &read_cmd, 1);
  cmd = static_cast<tA2DP_CTRL_CMD>(read_cmd);

  /* detach on ctrl channel means audioflinger process was terminated */
  if (n == 0) {
    APPL_TRACE_EVENT("CTRL CH DETACHED");
    UIPC_Close(UIPC_CH_ID_AV_CTRL);
    return;
  }

  APPL_TRACE_DEBUG("a2dp-ctrl-cmd : %s", audio_a2dp_hw_dump_ctrl_event(cmd));
  a2dp_cmd_pending = cmd;

  switch (cmd) {
    case A2DP_CTRL_CMD_CHECK_READY:
      if (btif_a2dp_source_media_task_is_shutting_down()) {
        APPL_TRACE_WARNING("%s: A2DP command %s while media task shutting down",
                           __func__, audio_a2dp_hw_dump_ctrl_event(cmd));
        btif_a2dp_command_ack(A2DP_CTRL_ACK_FAILURE);
        return;
      }

      /* check whether AV is ready to setup A2DP datapath */
      if (btif_av_stream_ready() || btif_av_stream_started_ready()) {
        btif_a2dp_command_ack(A2DP_CTRL_ACK_SUCCESS);
      } else {
        APPL_TRACE_WARNING("%s: A2DP command %s while AV stream is not ready",
                           __func__, audio_a2dp_hw_dump_ctrl_event(cmd));
        btif_a2dp_command_ack(A2DP_CTRL_ACK_FAILURE);
      }
      break;

    case A2DP_CTRL_CMD_START:
      /*
       * Don't send START request to stack while we are in a call.
       * Some headsets such as "Sony MW600", don't allow AVDTP START
       * while in a call, and respond with BAD_STATE.
       */
      if (!btif_hf_is_call_idle()) {
        btif_a2dp_command_ack(A2DP_CTRL_ACK_INCALL_FAILURE);
        break;
      }

      if (btif_a2dp_source_is_streaming()) {
        APPL_TRACE_WARNING("%s: A2DP command %s while source is streaming",
                           __func__, audio_a2dp_hw_dump_ctrl_event(cmd));
        btif_a2dp_command_ack(A2DP_CTRL_ACK_FAILURE);
        break;
      }

      if (btif_av_stream_ready()) {
        /* Setup audio data channel listener */
        UIPC_Open(UIPC_CH_ID_AV_AUDIO, btif_a2dp_data_cb);

        /*
         * Post start event and wait for audio path to open.
         * If we are the source, the ACK will be sent after the start
         * procedure is completed, othewise send it now.
         */
        btif_dispatch_sm_event(BTIF_AV_START_STREAM_REQ_EVT, NULL, 0);
        if (btif_av_get_peer_sep() == AVDT_TSEP_SRC)
          btif_a2dp_command_ack(A2DP_CTRL_ACK_SUCCESS);
        break;
      }

      if (btif_av_stream_started_ready()) {
        /*
         * Already started, setup audio data channel listener and ACK
         * back immediately.
         */
        UIPC_Open(UIPC_CH_ID_AV_AUDIO, btif_a2dp_data_cb);
        btif_a2dp_command_ack(A2DP_CTRL_ACK_SUCCESS);
        break;
      }
      APPL_TRACE_WARNING("%s: A2DP command %s while AV stream is not ready",
                         __func__, audio_a2dp_hw_dump_ctrl_event(cmd));
      btif_a2dp_command_ack(A2DP_CTRL_ACK_FAILURE);
      break;

    case A2DP_CTRL_CMD_STOP:
      if (btif_av_get_peer_sep() == AVDT_TSEP_SNK &&
          !btif_a2dp_source_is_streaming()) {
        /* We are already stopped, just ack back */
        btif_a2dp_command_ack(A2DP_CTRL_ACK_SUCCESS);
        break;
      }

      btif_dispatch_sm_event(BTIF_AV_STOP_STREAM_REQ_EVT, NULL, 0);
      btif_a2dp_command_ack(A2DP_CTRL_ACK_SUCCESS);
      break;

    case A2DP_CTRL_CMD_SUSPEND:
      /* Local suspend */
      if (btif_av_stream_started_ready()) {
        btif_dispatch_sm_event(BTIF_AV_SUSPEND_STREAM_REQ_EVT, NULL, 0);
        break;
      }
      /* If we are not in started state, just ack back ok and let
       * audioflinger close the channel. This can happen if we are
       * remotely suspended, clear REMOTE SUSPEND flag.
       */
      btif_av_clear_remote_suspend_flag();
      btif_a2dp_command_ack(A2DP_CTRL_ACK_SUCCESS);
      break;

    case A2DP_CTRL_GET_INPUT_AUDIO_CONFIG: {
      tA2DP_SAMPLE_RATE sample_rate = btif_a2dp_sink_get_sample_rate();
      tA2DP_CHANNEL_COUNT channel_count = btif_a2dp_sink_get_channel_count();

      btif_a2dp_command_ack(A2DP_CTRL_ACK_SUCCESS);
      UIPC_Send(UIPC_CH_ID_AV_CTRL, 0, reinterpret_cast<uint8_t*>(&sample_rate),
                sizeof(tA2DP_SAMPLE_RATE));
      UIPC_Send(UIPC_CH_ID_AV_CTRL, 0, &channel_count,
                sizeof(tA2DP_CHANNEL_COUNT));
      break;
    }

    case A2DP_CTRL_GET_OUTPUT_AUDIO_CONFIG: {
      btav_a2dp_codec_config_t codec_config;
      btav_a2dp_codec_config_t codec_capability;
      codec_config.sample_rate = BTAV_A2DP_CODEC_SAMPLE_RATE_NONE;
      codec_config.bits_per_sample = BTAV_A2DP_CODEC_BITS_PER_SAMPLE_NONE;
      codec_config.channel_mode = BTAV_A2DP_CODEC_CHANNEL_MODE_NONE;
      codec_capability.sample_rate = BTAV_A2DP_CODEC_SAMPLE_RATE_NONE;
      codec_capability.bits_per_sample = BTAV_A2DP_CODEC_BITS_PER_SAMPLE_NONE;
      codec_capability.channel_mode = BTAV_A2DP_CODEC_CHANNEL_MODE_NONE;

      A2dpCodecConfig* current_codec = bta_av_get_a2dp_current_codec();
      if (current_codec != nullptr) {
        codec_config = current_codec->getCodecConfig();
        codec_capability = current_codec->getCodecCapability();
      }

      btif_a2dp_command_ack(A2DP_CTRL_ACK_SUCCESS);
      // Send the current codec config
      UIPC_Send(UIPC_CH_ID_AV_CTRL, 0,
                reinterpret_cast<const uint8_t*>(&codec_config.sample_rate),
                sizeof(btav_a2dp_codec_sample_rate_t));
      UIPC_Send(UIPC_CH_ID_AV_CTRL, 0,
                reinterpret_cast<const uint8_t*>(&codec_config.bits_per_sample),
                sizeof(btav_a2dp_codec_bits_per_sample_t));
      UIPC_Send(UIPC_CH_ID_AV_CTRL, 0,
                reinterpret_cast<const uint8_t*>(&codec_config.channel_mode),
                sizeof(btav_a2dp_codec_channel_mode_t));
      // Send the current codec capability
      UIPC_Send(UIPC_CH_ID_AV_CTRL, 0,
                reinterpret_cast<const uint8_t*>(&codec_capability.sample_rate),
                sizeof(btav_a2dp_codec_sample_rate_t));
      UIPC_Send(UIPC_CH_ID_AV_CTRL, 0, reinterpret_cast<const uint8_t*>(
                                           &codec_capability.bits_per_sample),
                sizeof(btav_a2dp_codec_bits_per_sample_t));
      UIPC_Send(UIPC_CH_ID_AV_CTRL, 0, reinterpret_cast<const uint8_t*>(
                                           &codec_capability.channel_mode),
                sizeof(btav_a2dp_codec_channel_mode_t));
      break;
    }

    case A2DP_CTRL_SET_OUTPUT_AUDIO_CONFIG: {
      btav_a2dp_codec_config_t codec_config;
      codec_config.sample_rate = BTAV_A2DP_CODEC_SAMPLE_RATE_NONE;
      codec_config.bits_per_sample = BTAV_A2DP_CODEC_BITS_PER_SAMPLE_NONE;
      codec_config.channel_mode = BTAV_A2DP_CODEC_CHANNEL_MODE_NONE;

      btif_a2dp_command_ack(A2DP_CTRL_ACK_SUCCESS);
      // Send the current codec config
      if (UIPC_Read(UIPC_CH_ID_AV_CTRL, 0,
                    reinterpret_cast<uint8_t*>(&codec_config.sample_rate),
                    sizeof(btav_a2dp_codec_sample_rate_t)) !=
          sizeof(btav_a2dp_codec_sample_rate_t)) {
        APPL_TRACE_ERROR("Error reading sample rate from audio HAL");
        break;
      }
      if (UIPC_Read(UIPC_CH_ID_AV_CTRL, 0,
                    reinterpret_cast<uint8_t*>(&codec_config.bits_per_sample),
                    sizeof(btav_a2dp_codec_bits_per_sample_t)) !=
          sizeof(btav_a2dp_codec_bits_per_sample_t)) {
        APPL_TRACE_ERROR("Error reading bits per sample from audio HAL");
        break;
      }
      if (UIPC_Read(UIPC_CH_ID_AV_CTRL, 0,
                    reinterpret_cast<uint8_t*>(&codec_config.channel_mode),
                    sizeof(btav_a2dp_codec_channel_mode_t)) !=
          sizeof(btav_a2dp_codec_channel_mode_t)) {
        APPL_TRACE_ERROR("Error reading channel mode from audio HAL");
        break;
      }
      APPL_TRACE_DEBUG(
          "%s: A2DP_CTRL_SET_OUTPUT_AUDIO_CONFIG: "
          "sample_rate=0x%x bits_per_sample=0x%x "
          "channel_mode=0x%x",
          __func__, codec_config.sample_rate, codec_config.bits_per_sample,
          codec_config.channel_mode);
      btif_a2dp_source_feeding_update_req(codec_config);
      break;
    }

    case A2DP_CTRL_CMD_OFFLOAD_START:
      btif_dispatch_sm_event(BTIF_AV_OFFLOAD_START_REQ_EVT, NULL, 0);
      break;

    default:
      APPL_TRACE_ERROR("UNSUPPORTED CMD (%d)", cmd);
      btif_a2dp_command_ack(A2DP_CTRL_ACK_FAILURE);
      break;
  }
  APPL_TRACE_DEBUG("a2dp-ctrl-cmd : %s DONE",
                   audio_a2dp_hw_dump_ctrl_event(cmd));
}

static void btif_a2dp_ctrl_cb(UNUSED_ATTR tUIPC_CH_ID ch_id,
                              tUIPC_EVENT event) {
  APPL_TRACE_DEBUG("A2DP-CTRL-CHANNEL EVENT %s", dump_uipc_event(event));

  switch (event) {
    case UIPC_OPEN_EVT:
      break;

    case UIPC_CLOSE_EVT:
      /* restart ctrl server unless we are shutting down */
      if (btif_a2dp_source_media_task_is_running())
        UIPC_Open(UIPC_CH_ID_AV_CTRL, btif_a2dp_ctrl_cb);
      break;

    case UIPC_RX_DATA_READY_EVT:
      btif_a2dp_recv_ctrl_data();
      break;

    default:
      APPL_TRACE_ERROR("### A2DP-CTRL-CHANNEL EVENT %d NOT HANDLED ###", event);
      break;
  }
}

static void btif_a2dp_data_cb(UNUSED_ATTR tUIPC_CH_ID ch_id,
                              tUIPC_EVENT event) {
  APPL_TRACE_DEBUG("BTIF MEDIA (A2DP-DATA) EVENT %s", dump_uipc_event(event));

  switch (event) {
    case UIPC_OPEN_EVT:
      /*
       * Read directly from media task from here on (keep callback for
       * connection events.
       */
      UIPC_Ioctl(UIPC_CH_ID_AV_AUDIO, UIPC_REG_REMOVE_ACTIVE_READSET, NULL);
      UIPC_Ioctl(UIPC_CH_ID_AV_AUDIO, UIPC_SET_READ_POLL_TMO,
                 reinterpret_cast<void*>(A2DP_DATA_READ_POLL_MS));

      if (btif_av_get_peer_sep() == AVDT_TSEP_SNK) {
        /* Start the media task to encode the audio */
        btif_a2dp_source_start_audio_req();
      }

      /* ACK back when media task is fully started */
      break;

    case UIPC_CLOSE_EVT:
      APPL_TRACE_EVENT("## AUDIO PATH DETACHED ##");
      btif_a2dp_command_ack(A2DP_CTRL_ACK_SUCCESS);
      /*
       * Send stop request only if we are actively streaming and haven't
       * received a stop request. Potentially, the audioflinger detached
       * abnormally.
       */
      if (btif_a2dp_source_is_streaming()) {
        /* Post stop event and wait for audio path to stop */
        btif_dispatch_sm_event(BTIF_AV_STOP_STREAM_REQ_EVT, NULL, 0);
      }
      break;

    default:
      APPL_TRACE_ERROR("### A2DP-DATA EVENT %d NOT HANDLED ###", event);
      break;
  }
}

void btif_a2dp_command_ack(tA2DP_CTRL_ACK status) {
  uint8_t ack = status;

  APPL_TRACE_EVENT("## a2dp ack : %s, status %d ##",
                   audio_a2dp_hw_dump_ctrl_event(a2dp_cmd_pending), status);

  /* Sanity check */
  if (a2dp_cmd_pending == A2DP_CTRL_CMD_NONE) {
    APPL_TRACE_ERROR("warning : no command pending, ignore ack");
    return;
  }

  /* Clear pending */
  a2dp_cmd_pending = A2DP_CTRL_CMD_NONE;

  /* Acknowledge start request */
  UIPC_Send(UIPC_CH_ID_AV_CTRL, 0, &ack, sizeof(ack));
}
