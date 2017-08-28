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

#ifndef CHRE_UTIL_FIXED_SIZE_BLOCKING_QUEUE_H_
#define CHRE_UTIL_FIXED_SIZE_BLOCKING_QUEUE_H_

#include <deque>

#include "chre/platform/condition_variable.h"
#include "chre/platform/mutex.h"
#include "chre/util/array_queue.h"
#include "chre/util/non_copyable.h"

namespace chre {

/**
 * Implements a thread-safe blocking queue that blocks when popping an element
 * if necessary.
 */
template <typename ElementType, size_t kSize>
class FixedSizeBlockingQueue : public NonCopyable {
 public:
  /**
   * Pushes an element into the queue and notifies any waiting threads that an
   * element is available.
   *
   * @param The element to be pushed.
   *
   * @return true if the element is pushed successfully.
   */
  bool push(const ElementType& element);

  /**
   * Pops one element from the queue. If the queue is empty, the thread will
   * block until an element has been pushed.
   *
   * @return The element that was popped.
   */
  ElementType pop();

  /**
   * Determines whether or not the BlockingQueue is empty.
   */
  bool empty();

 private:
  //! The mutex used to ensure thread-safety.
  Mutex mMutex;

  //! The condition variable used to implement the blocking behavior of the
  //! queue.
  ConditionVariable mConditionVariable;

  //! The underlying fixed size container backing the queue.
  ArrayQueue<ElementType, kSize> mQueue;
};

}  // namespace chre

#include "chre/util/fixed_size_blocking_queue_impl.h"

#endif  // CHRE_UTIL_FIXED_SIZE_BLOCKING_QUEUE_H_
