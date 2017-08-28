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

#ifndef CHRE_PLATFORM_SLPI_PLATFORM_SENSOR_BASE_H_
#define CHRE_PLATFORM_SLPI_PLATFORM_SENSOR_BASE_H_

extern "C" {

#include "sns_smgr_api_v01.h"

}  // extern "C"

#include "chre/core/sensor_request.h"

namespace chre {

/**
 * Storage for the SLPI implementation of the PlatformSensor class.
 */
class PlatformSensorBase {
 public:
  //! The handle to uniquely identify this sensor.
  uint8_t sensorId;

  //! The type of data that this sensor uses. SMGR overloads sensor IDs and
  //! allows them to behave as two sensors. The data type differentiates which
  //! sensor this object refers to.
  uint8_t dataType;

  //! The calibration type of this sensor. SMGR overloads sensorId and dataType
  //! and allows them to represent both uncalibrated and calibrated sensors.
  uint8_t calType;

  //! The name (type and model) of this sensor.
  char sensorName[SNS_SMGR_MAX_SENSOR_NAME_SIZE_V01];

  //! The minimum interval of this sensor.
  uint64_t minInterval;

  //! The pointer to the sensor's last event.
  ChreSensorData *lastEvent = nullptr;

  //! The size of the sensor's last event storage.
  size_t lastEventSize = 0;
};

}  // namespace chre

#endif  // CHRE_PLATFORM_SLPI_PLATFORM_SENSOR_BASE_H_
