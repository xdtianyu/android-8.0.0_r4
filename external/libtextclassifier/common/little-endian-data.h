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

#ifndef LIBTEXTCLASSIFIER_COMMON_LITTLE_ENDIAN_DATA_H_
#define LIBTEXTCLASSIFIER_COMMON_LITTLE_ENDIAN_DATA_H_

#include <algorithm>
#include <string>
#include <vector>

#include "base.h"
#include "util/base/logging.h"

namespace libtextclassifier {
namespace nlp_core {

// Swaps the sizeof(T) bytes that start at addr.  E.g., if sizeof(T) == 2,
// then (addr[0], addr[1]) -> (addr[1], addr[0]).  Useful for little endian
// <-> big endian conversions.
template <class T>
void SwapBytes(T *addr) {
  char *char_ptr = reinterpret_cast<char *>(addr);
  std::reverse(char_ptr, char_ptr + sizeof(T));
}

// Assuming addr points to a piece of data of type T, with its bytes in the
// little/big endian order specific to the machine this code runs on, this
// method will re-arrange the bytes (in place) in little-endian order.
template <class T>
void HostToLittleEndian(T *addr) {
  if (LittleEndian::IsLittleEndian()) {
    // Do nothing: current machine is little-endian.
  } else {
    SwapBytes(addr);
  }
}

// Reverse of HostToLittleEndian.
template <class T>
void LittleEndianToHost(T *addr) {
  // It turns out it's the same function: on little-endian machines, do nothing
  // (source and target formats are identical).  Otherwise, swap bytes.
  HostToLittleEndian(addr);
}

// Returns string obtained by concatenating the bytes of the elements from a
// vector (in order: v[0], v[1], etc).  If the type T requires more than one
// byte, the byte for each element are first converted to little-endian format.
template<typename T>
std::string GetDataBytesInLittleEndianOrder(const std::vector<T> &v) {
  std::string data_bytes;
  for (const T element : v) {
    T little_endian_element = element;
    HostToLittleEndian(&little_endian_element);
    data_bytes.append(
        reinterpret_cast<const char *>(&little_endian_element),
        sizeof(T));
  }
  return data_bytes;
}

// Performs reverse of GetDataBytesInLittleEndianOrder.
//
// I.e., decodes the data bytes from parameter bytes into num_elements Ts, and
// places them in the vector v (previous content of that vector is erased).
//
// We expect bytes to contain the concatenation of the bytes for exactly
// num_elements elements of type T.  If the type T requires more than one byte,
// those bytes should be arranged in little-endian form.
//
// Returns true on success and false otherwise (e.g., bytes has the wrong size).
// Note: we do not want to crash on corrupted data (some clients, e..g, GMSCore,
// have asked us not to do so).  Instead, we report the error and let the client
// decide what to do.  On error, we also fill the vector with zeros, such that
// at least the dimension of v matches expectations.
template<typename T>
bool FillVectorFromDataBytesInLittleEndian(
    const std::string &bytes, int num_elements, std::vector<T> *v) {
  if (bytes.size() != num_elements * sizeof(T)) {
    TC_LOG(ERROR) << "Wrong number of bytes: actual " << bytes.size()
                  << " vs expected " << num_elements
                  << " elements of sizeof(element) = " << sizeof(T)
                  << " bytes each ; will fill vector with zeros";
    v->assign(num_elements, static_cast<T>(0));
    return false;
  }
  v->clear();
  v->reserve(num_elements);
  const T *start = reinterpret_cast<const T *>(bytes.data());
  if (LittleEndian::IsLittleEndian() || (sizeof(T) == 1)) {
    // Fast in the common case ([almost] all hardware today is little-endian):
    // if same endianness (or type T requires a single byte and endianness
    // irrelevant), just use the bytes.
    v->assign(start, start + num_elements);
  } else {
    // Slower (but very rare case): this code runs on a big endian machine and
    // the type T requires more than one byte.  Hence, some conversion is
    // necessary.
    for (int i = 0; i < num_elements; ++i) {
      T temp = start[i];
      SwapBytes(&temp);
      v->push_back(temp);
    }
  }
  return true;
}

}  // namespace nlp_core
}  // namespace libtextclassifier

#endif  // LIBTEXTCLASSIFIER_COMMON_LITTLE_ENDIAN_DATA_H_
