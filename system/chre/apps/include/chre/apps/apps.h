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

#ifndef CHRE_APPS_APPS_H_
#define CHRE_APPS_APPS_H_

#include "chre/core/nanoapp.h"
#include "chre/util/unique_ptr.h"

namespace chre {

extern UniquePtr<Nanoapp> *gNanoappGnssWorld;
extern UniquePtr<Nanoapp> *gNanoappHelloWorld;
extern UniquePtr<Nanoapp> *gNanoappImuCal;
extern UniquePtr<Nanoapp> *gNanoappMessageWorld;
extern UniquePtr<Nanoapp> *gNanoappSensorWorld;
extern UniquePtr<Nanoapp> *gNanoappTimerWorld;
extern UniquePtr<Nanoapp> *gNanoappWifiWorld;
extern UniquePtr<Nanoapp> *gNanoappWwanWorld;

} // namespace chre

#endif  // CHRE_APPS_APPS_H_
