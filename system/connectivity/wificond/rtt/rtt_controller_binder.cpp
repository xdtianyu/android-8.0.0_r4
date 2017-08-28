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

#include "wificond/rtt/rtt_controller_binder.h"

#include "wificond/rtt/rtt_controller_impl.h"

namespace android {
namespace wificond {

RttControllerBinder::RttControllerBinder(RttControllerImpl* impl) : impl_{impl} {
}

RttControllerBinder::~RttControllerBinder() {
}

}  // namespace wificond
}  // namespace android
