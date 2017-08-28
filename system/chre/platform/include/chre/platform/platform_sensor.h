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

#ifndef CHRE_PLATFORM_PLATFORM_SENSOR_H_
#define CHRE_PLATFORM_PLATFORM_SENSOR_H_

#include "chre/core/sensor_request.h"
#include "chre/target_platform/platform_sensor_base.h"
#include "chre/util/dynamic_vector.h"
#include "chre/util/non_copyable.h"

namespace chre {

/**
 * Provides an interface to obtain a platform-independent description of a
 * sensor. The PlatformSensorBase is subclassed here to allow platforms to
 * inject their own storage for their implementation.
 */
class PlatformSensor : public PlatformSensorBase,
                       public NonCopyable {
 public:
  /**
   * Default constructs a PlatformSensor.
   */
  PlatformSensor();

  /**
   * Constructs a PlatformSensor by moving another.
   *
   * @param other The PlatformSensor to move.
   */
  PlatformSensor(PlatformSensor&& other);

  /**
   * Destructs the PlatformSensor object.
   */
  ~PlatformSensor();

  /**
   * Initializes the platform sensors subsystem. This must be called as part of
   * the initialization of the runtime.
   */
  static void init();

  /**
   * Obtains a list of the sensors that the platform provides. The supplied
   * DynamicVector should be empty when passed in. If this method returns false
   * the vector may be partially filled.
   *
   * @param sensors A non-null pointer to a DynamicVector to populate with the
   *                list of sensors.
   * @return Returns true if the query was successful.
   */
  static bool getSensors(DynamicVector<PlatformSensor> *sensors);

  /*
   * Deinitializes the platform sensors subsystem. This must be called as part
   * of the deinitialization of the runtime.
   */
  static void deinit();

  /**
   * Sends the sensor request to the platform sensor. The implementation
   * of this method is supplied by the platform. If the request is
   * invalid/unsupported by this sensor, for example because it requests an
   * interval that is too short, then this function must return false. If
   * setting this new request fails due to a transient failure (example:
   * inability to communicate with the sensor) false must also be returned.
   *
   * @param request The new request to set this sensor to.
   * @return true if the platform sensor was successfully configured with the
   *         supplied request.
   */
  bool setRequest(const SensorRequest& request);

  /**
   * Obtains the SensorType of this platform sensor. The implementation of this
   * method is supplied by the platform as the mechanism for determining the
   * type may vary across platforms.
   *
   * @return The type of this sensor.
   */
  SensorType getSensorType() const;

  /**
   * @return The minimum interval in nanoseconds of this sensor.
   */
  uint64_t getMinInterval() const;

  /**
   * Returns the name (type and model) of this sensor.
   *
   * @return A pointer to a static string.
   */
  const char *getSensorName() const;

  /**
   * @return Pointer to this sensor's last data event. It returns a nullptr if
   *         the the platform doesn't provide it.
   */
  ChreSensorData *getLastEvent() const;

  /**
   * Copies the supplied event to the sensor's last event.
   *
   * @param event The pointer to the event to copy from.
   */
  void setLastEvent(const ChreSensorData *event);

  /**
   * Performs a move-assignment of a PlatformSensor.
   *
   * @param other The other PlatformSensor to move.
   * @return a reference to this object.
   */
  PlatformSensor& operator=(PlatformSensor&& other);
};

}  // namespace chre

#endif  // CHRE_PLATFORM_PLATFORM_SENSOR_H_
