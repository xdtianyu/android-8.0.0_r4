/******************************************************************************
 *
 *  Copyright (C) 2017 The Android Open Source Project
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

#pragma once

#include <vector>

class AdvertiseDataParser {
 public:
  /**
   * Return true if this |ad| represent properly formatted advertising data.
   */
  static bool IsValid(const std::vector<uint8_t>& ad) {
    size_t position = 0;

    size_t ad_len = ad.size();
    while (position != ad_len) {
      uint8_t len = ad[position];

      // A field length of 0 would be invalid as it should at least contain the
      // EIR field type.
      if (len == 0) return false;

      // If the length of the current field would exceed the total data length,
      // then the data is badly formatted.
      if (position + len >= ad_len) {
        return false;
      }

      position += len + 1;
    }

    return true;
  }

  /**
   * This function returns a pointer inside the |ad| array of length |ad_len|
   * where a field of |type| is located, together with its length in |p_length|
   */
  static const uint8_t* GetFieldByType(const uint8_t* ad, size_t ad_len,
                                       uint8_t type, uint8_t* p_length) {
    size_t position = 0;

    while (position != ad_len) {
      uint8_t len = ad[position];

      if (len == 0) break;
      if (position + len >= ad_len) break;

      uint8_t adv_type = ad[position + 1];

      if (adv_type == type) {
        /* length doesn't include itself */
        *p_length = len - 1; /* minus the length of type */
        return ad + position + 2;
      }

      position += len + 1; /* skip the length of data */
    }

    *p_length = 0;
    return NULL;
  }

  /**
   * This function returns a pointer inside the |adv| where a field of |type| is
   * located, together with it' length in |p_length|
   */
  static const uint8_t* GetFieldByType(std::vector<uint8_t> const& ad,
                                       uint8_t type, uint8_t* p_length) {
    return GetFieldByType(ad.data(), ad.size(), type, p_length);
  }
};
