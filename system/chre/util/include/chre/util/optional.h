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

#ifndef UTIL_CHRE_OPTIONAL_H_
#define UTIL_CHRE_OPTIONAL_H_

namespace chre {

/**
 * This container keeps track of an optional object. The container is similar to
 * std::optional introduced in C++17.
 */
template<typename ObjectType>
class Optional {
 public:
  /**
   * Default constructs the optional object with no initial value.
   */
  Optional();

  /**
   * Constructs an optional instance with an initial value.
   *
   * @param object The initial value of the object.
   */
  Optional(const ObjectType& object);

  /**
   * Constructs an optional instance with an initial value by moving it.
   *
   * @param object The instance of the initial object to take ownership of.
   */
  Optional(ObjectType&& object);

  /**
   * @return Returns true if the object tracked by this container has been
   * assigned.
   */
  bool has_value() const;

  /**
   * Resets the optional container by invoking the destructor on the underlying
   * object.
   */
  void reset();

  /**
   * Performs a move assignment operation to the underlying object managed by
   * this container.
   *
   * @param other The other object to move from.
   * @return Returns a reference to this object.
   */
  Optional<ObjectType>& operator=(ObjectType&& other);

  /**
   * Performs a move assignment from one optional to another. The other is left
   * in a state with no value.
   *
   * @param other The other object to move.
   * @return Returns a reference to this object.
   */
  Optional<ObjectType>& operator=(Optional<ObjectType>&& other);

  /**
   * Performs a copy assignment operation to the underlying object managed by
   * this container.
   *
   * @param other The other object to copy from.
   * @return Returns a reference to this object.
   */
  Optional<ObjectType>& operator=(const ObjectType& other);

  /**
   * Performs a copy assignment from one optional to another.
   *
   * @param other The other object to copy.
   * @return Returns a reference to this object.
   */
  Optional<ObjectType>& operator=(const Optional<ObjectType>& other);

  /**
   * Obtains a reference to the underlying object managed by this container.
   * The behavior of this is undefined if has_value() returns false.
   *
   * @return Returns a reference to the underlying object tracked by this
   *         container.
   */
  ObjectType& operator*();

  /**
   * Obtains a const reference to the underlying object managed by this
   * container. The behavior of this is undefined if has_value() returns false.
   *
   * @return Returns a const reference to the underlying object tracked by this
   *         container.
   */
  const ObjectType& operator*() const;

  /**
   * Obtains a pointer to the underlying object managed by this container. The
   * object may not be well-formed if has_value() returns false.
   *
   * @return Returns a pointer to the underlying object tracked by this
   *         container.
   */
  ObjectType *operator->();

  /**
   * Obtains a const pointer to the underlying object managed by this container.
   * The object may not be well-formed if has_value() returns false.
   *
   * @return Returns a const pointer to the underlying object tracked by this
   *         container.
   */
  const ObjectType *operator->() const;

 private:
  //! The optional object being tracked by this container.
  ObjectType mObject;

  //! Whether or not the object is set.
  bool mHasValue = false;
};

}  // namespace chre

#include "chre/util/optional_impl.h"

#endif  // UTIL_CHRE_OPTIONAL_H_
