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

#ifndef CHRE_CORE_SENSOR_H_
#define CHRE_CORE_SENSOR_H_

#include "chre/platform/platform_sensor.h"
#include "chre/util/non_copyable.h"
#include "chre/util/optional.h"

namespace chre {

class Sensor : public NonCopyable {
 public:
  /**
   * Default constructs a Sensor with an unknown sensor type.
   */
  Sensor();

  /**
   * Constructs a Sensor by moving a PlatformSensor.
   *
   * @param platformSensor The platform implementation of this Sensor.
   */
  Sensor(PlatformSensor&& platformSensor);

  /**
   * @return The type of this sensor.
   */
  SensorType getSensorType() const;

  /**
   * @return true if this Sensor instance has an instance of the underlying
   * PlatformSensor. This is useful to determine if this sensor is supplied by
   * the platform.
   */
  bool isValid() const;

  /**
   * Sets the current request of this sensor. If this request is a change from
   * the previous request, it is sent to the underlying platform. If isValid()
   * returns false this function will also return false and do nothing.
   *
   * @param request The new request for this sensor.
   * @return true if there was no change required or the platform has set the
   *         request successfully.
   */
  bool setRequest(const SensorRequest& request);

  /**
   * Performs a move-assignment of a Sensor.
   *
   * @param other The other sensor to move.
   * @return a reference to this object.
   */
  Sensor& operator=(Sensor&& other);

  /**
   * @return The minimal interval in nanoseconds of this sensor.
   */
  uint64_t getMinInterval() const;

  /**
   * @return The name (type and model) of this sensor.
   */
  const char *getSensorName() const;

  /**
   * @return Pointer to this sensor's last event. It returns a nullptr if the
   * the platform doesn't provide it or the last data event is invalid.
   */
  ChreSensorData *getLastEvent() const;

  /**
   * Copies the supplied event to the sensor's last event.
   *
   * @param event The pointer to the event to copy from.
   */
  void setLastEvent(const ChreSensorData *event);

 private:
  //! The most recent sensor request sent to this sensor.
  SensorRequest mSensorRequest;

  //! The validity of this sensor's last event.
  bool mLastEventValid = false;

  //! The underlying platform sensor that is managed by this common interface.
  Optional<PlatformSensor> mPlatformSensor;
};

}  // namespace chre

#endif  // CHRE_CORE_SENSOR_H_
