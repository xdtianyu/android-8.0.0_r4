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

#ifndef LIBTEXTCLASSIFIER_COMMON_VECTOR_SPAN_H_
#define LIBTEXTCLASSIFIER_COMMON_VECTOR_SPAN_H_

#include <vector>

namespace libtextclassifier {

// StringPiece analogue for std::vector<T>.
template <class T>
class VectorSpan {
 public:
  VectorSpan() : begin_(), end_() {}
  VectorSpan(const std::vector<T>& v)  // NOLINT(runtime/explicit)
      : begin_(v.begin()), end_(v.end()) {}
  VectorSpan(typename std::vector<T>::const_iterator begin,
             typename std::vector<T>::const_iterator end)
      : begin_(begin), end_(end) {}

  const T& operator[](typename std::vector<T>::size_type i) const {
    return *(begin_ + i);
  }

  int size() const { return end_ - begin_; }
  typename std::vector<T>::const_iterator begin() const { return begin_; }
  typename std::vector<T>::const_iterator end() const { return end_; }

 private:
  typename std::vector<T>::const_iterator begin_;
  typename std::vector<T>::const_iterator end_;
};

}  // namespace libtextclassifier

#endif  // LIBTEXTCLASSIFIER_COMMON_VECTOR_SPAN_H_
