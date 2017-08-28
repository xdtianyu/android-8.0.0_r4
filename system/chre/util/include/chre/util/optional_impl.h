/*
 * Copyright (C) 2016 The Android Open Source Project
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

#ifndef UTIL_CHRE_OPTIONAL_IMPL_H_
#define UTIL_CHRE_OPTIONAL_IMPL_H_

#include "chre/util/optional.h"

namespace chre {

template<typename ObjectType>
Optional<ObjectType>::Optional() {}

template<typename ObjectType>
Optional<ObjectType>::Optional(const ObjectType& object)
    : mObject(object), mHasValue(true) {}

template<typename ObjectType>
Optional<ObjectType>::Optional(ObjectType&& object)
    : mObject(std::move(object)), mHasValue(true) {}

template<typename ObjectType>
bool Optional<ObjectType>::has_value() const {
  return mHasValue;
}

template<typename ObjectType>
void Optional<ObjectType>::reset() {
  mObject.~ObjectType();
  mHasValue = false;
}

template<typename ObjectType>
Optional<ObjectType>& Optional<ObjectType>::operator=(ObjectType&& other) {
  mObject = std::move(other);
  mHasValue = true;
  return *this;
}

template<typename ObjectType>
Optional<ObjectType>& Optional<ObjectType>::operator=(
    Optional<ObjectType>&& other) {
  mObject = std::move(other.mObject);
  mHasValue = other.mHasValue;
  other.mHasValue = false;
  return *this;
}

template<typename ObjectType>
Optional<ObjectType>& Optional<ObjectType>::operator=(const ObjectType& other) {
  mObject = other;
  mHasValue = true;
  return *this;
}

template<typename ObjectType>
Optional<ObjectType>& Optional<ObjectType>::operator=(
    const Optional<ObjectType>& other) {
  mObject = other.mObject;
  mHasValue = other.mHasValue;
  return *this;
}

template<typename ObjectType>
ObjectType& Optional<ObjectType>::operator*() {
  return mObject;
}

template<typename ObjectType>
const ObjectType& Optional<ObjectType>::operator*() const {
  return mObject;
}

template<typename ObjectType>
ObjectType *Optional<ObjectType>::operator->() {
  return &mObject;
}

template<typename ObjectType>
const ObjectType *Optional<ObjectType>::operator->() const {
  return &mObject;
}

}  // namespace chre

#endif  // UTIL_CHRE_OPTIONAL_IMPL_H_
