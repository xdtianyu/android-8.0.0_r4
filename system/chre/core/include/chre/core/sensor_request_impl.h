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

#ifndef CHRE_CORE_SENSOR_REQUEST_IMPL_H_
#define CHRE_CORE_SENSOR_REQUEST_IMPL_H_

#include "chre/core/sensor_request.h"

namespace chre {

constexpr bool sensorModeIsActive(SensorMode sensorMode) {
  return (sensorMode == SensorMode::ActiveContinuous
      || sensorMode == SensorMode::ActiveOneShot);
}

constexpr bool sensorModeIsContinuous(SensorMode sensorMode) {
  return (sensorMode == SensorMode::ActiveContinuous
      || sensorMode == SensorMode::PassiveContinuous);
}

constexpr bool sensorModeIsOneShot(SensorMode sensorMode) {
  return (sensorMode == SensorMode::ActiveOneShot
      || sensorMode == SensorMode::PassiveOneShot);
}

constexpr size_t getSensorTypeArrayIndex(SensorType sensorType) {
  return static_cast<size_t>(sensorType) - 1;
}

constexpr size_t getSensorTypeCount() {
  // The number of valid entries in the SensorType enum (not including Unknown).
  return static_cast<size_t>(SensorType::SENSOR_TYPE_COUNT) - 1;
}

constexpr uint32_t getSensorHandleFromSensorType(SensorType sensorType) {
  return static_cast<uint32_t>(sensorType);
}

constexpr SensorType getSensorTypeFromSensorHandle(uint32_t handle) {
  return (handle > static_cast<uint32_t>(SensorType::Unknown)
          && handle < static_cast<uint32_t>(SensorType::SENSOR_TYPE_COUNT))
      ? static_cast<SensorType>(handle) : SensorType::Unknown;
}

}  // namespace chre

#endif  // CHRE_CORE_SENSOR_REQUEST_IMPL_H_
