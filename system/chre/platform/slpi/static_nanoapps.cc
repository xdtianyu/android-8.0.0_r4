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

#include <cstddef>

#include "chre/apps/apps.h"
#include "chre/platform/static_nanoapps.h"
#include "chre/util/macros.h"

namespace chre {

//! The list of static nanoapps to load for the SLPI platform. All nanoapps are
//! disabled by default, uncomment them as needed for local testing. This is
//! supplied as a weak symbol so that device builds can override this list.
__attribute__((weak))
UniquePtr<Nanoapp> *const kStaticNanoappList[] = {
//  gNanoappGnssWorld,
//  gNanoappHelloWorld,
//  gNanoappImuCal,
//  gNanoappMessageWorld,
//  gNanoappSensorWorld,
//  gNanoappTimerWorld,
//  gNanoappWifiWorld,
//  gNanoappWwanWorld,
};

//! The size of the static nanoapp list.
__attribute__((weak))
const size_t kStaticNanoappCount = ARRAY_SIZE(kStaticNanoappList);

}  // namespace chre
