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

#include "include/ese/ese.h"
#include "include/ese/log.h"

#include "ese_private.h"

static const char kUnknownHw[] = "unknown hw";
static const char kNullEse[] = "NULL EseInterface";
static const char *kEseErrorMessages[] = {
    "Hardware supplied no transceive implementation.",
    "Timed out polling for value.",
};
#define ESE_MESSAGES(x) (sizeof(x) / sizeof((x)[0]))

API const char *ese_name(const struct EseInterface *ese) {
  if (!ese) {
    return kNullEse;
  }
  if (ese->ops->name) {
    return ese->ops->name;
  }
  return kUnknownHw;
}

API int ese_open(struct EseInterface *ese, void *hw_opts) {
  if (!ese) {
    return -1;
  }
  ALOGV("opening interface '%s'", ese_name(ese));
  if (ese->ops->open) {
    return ese->ops->open(ese, hw_opts);
  }
  return 0;
}

API const char *ese_error_message(const struct EseInterface *ese) {
  return ese->error.message;
}

API int ese_error_code(const struct EseInterface *ese) {
  return ese->error.code;
}

API bool ese_error(const struct EseInterface *ese) { return ese->error.is_err; }

API void ese_set_error(struct EseInterface *ese, int code) {
  if (!ese) {
    return;
  }
  /* Negative values are reserved for API wide messages. */
  ese->error.code = code;
  ese->error.is_err = true;
  if (code < 0) {
    code = -(code + 1); /* Start at 0. */
    if ((uint32_t)(code) >= ESE_MESSAGES(kEseErrorMessages)) {
      LOG_ALWAYS_FATAL("Unknown global error code passed to ese_set_error(%d)",
                       code);
    }
    ese->error.message = kEseErrorMessages[code];
    return;
  }
  if ((uint32_t)(code) >= ese->ops->errors_count) {
    LOG_ALWAYS_FATAL("Unknown hw error code passed to ese_set_error(%d)", code);
  }
  ese->error.message = ese->ops->errors[code];
}

/* Blocking. */
API int ese_transceive(struct EseInterface *ese, const uint8_t *tx_buf,
                       uint32_t tx_len, uint8_t *rx_buf, uint32_t rx_max) {
  uint32_t recvd = 0;
  if (!ese) {
    return -1;
  }

  if (ese->ops->transceive) {
    recvd = ese->ops->transceive(ese, tx_buf, tx_len, rx_buf, rx_max);
    return ese_error(ese) ? -1 : recvd;
  }

  if (ese->ops->hw_transmit && ese->ops->hw_receive) {
    ese->ops->hw_transmit(ese, tx_buf, tx_len, 1);
    if (!ese_error(ese)) {
      recvd = ese->ops->hw_receive(ese, rx_buf, rx_max, 1);
    }
    return ese_error(ese) ? -1 : recvd;
  }

  ese_set_error(ese, kEseGlobalErrorNoTransceive);
  return -1;
}

API void ese_close(struct EseInterface *ese) {
  if (!ese) {
    return;
  }
  ALOGV("closing interface '%s'", ese_name(ese));
  if (!ese->ops->close) {
    return;
  }
  ese->ops->close(ese);
}
