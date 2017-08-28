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

#include "util/utf8/unicodetext.h"

#include "base.h"
#include "util/strings/utf8.h"

namespace libtextclassifier {

// *************** Data representation **********
// Note: the copy constructor is undefined.

void UnicodeText::Repr::PointTo(const char* data, int size) {
  if (ours_ && data_) delete[] data_;  // If we owned the old buffer, free it.
  data_ = const_cast<char*>(data);
  size_ = size;
  capacity_ = size;
  ours_ = false;
}

void UnicodeText::Repr::Copy(const char* data, int size) {
  resize(size);
  memcpy(data_, data, size);
}

void UnicodeText::Repr::resize(int new_size) {
  if (new_size == 0) {
    clear();
  } else {
    if (!ours_ || new_size > capacity_) reserve(new_size);
    // Clear the memory in the expanded part.
    if (size_ < new_size) memset(data_ + size_, 0, new_size - size_);
    size_ = new_size;
    ours_ = true;
  }
}

void UnicodeText::Repr::reserve(int new_capacity) {
  // If there's already enough capacity, and we're an owner, do nothing.
  if (capacity_ >= new_capacity && ours_) return;

  // Otherwise, allocate a new buffer.
  capacity_ = std::max(new_capacity, (3 * capacity_) / 2 + 20);
  char* new_data = new char[capacity_];

  // If there is an old buffer, copy it into the new buffer.
  if (data_) {
    memcpy(new_data, data_, size_);
    if (ours_) delete[] data_;  // If we owned the old buffer, free it.
  }
  data_ = new_data;
  ours_ = true;  // We own the new buffer.
  // size_ is unchanged.
}

void UnicodeText::Repr::append(const char* bytes, int byte_length) {
  reserve(size_ + byte_length);
  memcpy(data_ + size_, bytes, byte_length);
  size_ += byte_length;
}

void UnicodeText::Repr::clear() {
  if (ours_) delete[] data_;
  data_ = nullptr;
  size_ = capacity_ = 0;
  ours_ = true;
}

// *************** UnicodeText ******************

UnicodeText::UnicodeText() {}

UnicodeText::UnicodeText(const UnicodeText& src) { Copy(src); }

UnicodeText& UnicodeText::Copy(const UnicodeText& src) {
  repr_.Copy(src.repr_.data_, src.repr_.size_);
  return *this;
}

UnicodeText& UnicodeText::PointToUTF8(const char* buffer, int byte_length) {
  repr_.PointTo(buffer, byte_length);
  return *this;
}

UnicodeText& UnicodeText::CopyUTF8(const char* buffer, int byte_length) {
  repr_.Copy(buffer, byte_length);
  return *this;
}

UnicodeText& UnicodeText::AppendUTF8(const char* utf8, int len) {
  repr_.append(utf8, len);
  return *this;
}

void UnicodeText::clear() { repr_.clear(); }

std::string UnicodeText::UTF8Substring(const const_iterator& first,
                                       const const_iterator& last) {
  return std::string(first.it_, last.it_ - first.it_);
}

UnicodeText::~UnicodeText() {}

// ******************* UnicodeText::const_iterator *********************

// The implementation of const_iterator would be nicer if it
// inherited from boost::iterator_facade
// (http://boost.org/libs/iterator/doc/iterator_facade.html).

UnicodeText::const_iterator::const_iterator() : it_(0) {}

UnicodeText::const_iterator& UnicodeText::const_iterator::operator=(
    const const_iterator& other) {
  if (&other != this) it_ = other.it_;
  return *this;
}

UnicodeText::const_iterator UnicodeText::begin() const {
  return const_iterator(repr_.data_);
}

UnicodeText::const_iterator UnicodeText::end() const {
  return const_iterator(repr_.data_ + repr_.size_);
}

bool operator<(const UnicodeText::const_iterator& lhs,
               const UnicodeText::const_iterator& rhs) {
  return lhs.it_ < rhs.it_;
}

char32 UnicodeText::const_iterator::operator*() const {
  // (We could call chartorune here, but that does some
  // error-checking, and we're guaranteed that our data is valid
  // UTF-8. Also, we expect this routine to be called very often. So
  // for speed, we do the calculation ourselves.)

  // Convert from UTF-8
  unsigned char byte1 = static_cast<unsigned char>(it_[0]);
  if (byte1 < 0x80) return byte1;

  unsigned char byte2 = static_cast<unsigned char>(it_[1]);
  if (byte1 < 0xE0) return ((byte1 & 0x1F) << 6) | (byte2 & 0x3F);

  unsigned char byte3 = static_cast<unsigned char>(it_[2]);
  if (byte1 < 0xF0) {
    return ((byte1 & 0x0F) << 12) | ((byte2 & 0x3F) << 6) | (byte3 & 0x3F);
  }

  unsigned char byte4 = static_cast<unsigned char>(it_[3]);
  return ((byte1 & 0x07) << 18) | ((byte2 & 0x3F) << 12) |
         ((byte3 & 0x3F) << 6) | (byte4 & 0x3F);
}

UnicodeText::const_iterator& UnicodeText::const_iterator::operator++() {
  it_ += GetNumBytesForNonZeroUTF8Char(it_);
  return *this;
}

UnicodeText::const_iterator& UnicodeText::const_iterator::operator--() {
  while (IsTrailByte(*--it_)) {
  }
  return *this;
}

UnicodeText UTF8ToUnicodeText(const char* utf8_buf, int len, bool do_copy) {
  UnicodeText t;
  if (do_copy) {
    t.CopyUTF8(utf8_buf, len);
  } else {
    t.PointToUTF8(utf8_buf, len);
  }
  return t;
}

UnicodeText UTF8ToUnicodeText(const std::string& str, bool do_copy) {
  return UTF8ToUnicodeText(str.data(), str.size(), do_copy);
}

}  // namespace libtextclassifier
