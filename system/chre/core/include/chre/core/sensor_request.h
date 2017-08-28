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

#ifndef CHRE_CORE_SENSOR_REQUEST_H_
#define CHRE_CORE_SENSOR_REQUEST_H_

#include <cstdint>

#include "chre_api/chre/sensor.h"
#include "chre/core/nanoapp.h"
#include "chre/util/time.h"

namespace chre {

// TODO: Move SensorType and related functions to a new file called
// sensor_type.h and include it here. This will allow using this logic in util
// code withput pulling in the entire SensorRequest class which is only intended
// to be used by the CHRE implementation.

//! The union of possible CHRE sensor data event type with one sample.
union ChreSensorData {
  struct chreSensorThreeAxisData threeAxisData;
  struct chreSensorOccurrenceData occurrenceData;
  struct chreSensorFloatData floatData;
  struct chreSensorByteData byteData;
};

/**
 * This SensorType is designed to wrap constants provided by the CHRE API
 * to improve type-safety. In addition, an unknown sensor type is provided
 * for dealing with sensors that are not defined as per the CHRE API
 * specification. The details of these sensors are left to the CHRE API
 * sensor definitions.
 */
enum class SensorType {
  Unknown,
  Accelerometer,
  InstantMotion,
  StationaryDetect,
  Gyroscope,
  GeomagneticField,
  Pressure,
  Light,
  Proximity,
  AccelerometerTemperature,
  GyroscopeTemperature,
  UncalibratedAccelerometer,
  UncalibratedGyroscope,
  UncalibratedGeomagneticField,

  // Note to future developers: don't forget to update the implementation of
  // 1) getSensorTypeName,
  // 2) getSensorTypeFromUnsignedInt,
  // 3) getUnsignedIntFromSensorType,
  // 4) getSensorSampleTypeFromSensorType
  // 5) sensorTypeIsOneShot
  // 6) sensorTypeIsOnChange
  // when adding or removing a new entry here :)
  // Have a nice day.

  //! The number of sensor types including unknown. This entry must be last.
  SENSOR_TYPE_COUNT,
};

/**
 * This SensorSampleType is designed to help classify SensorType's data type in
 * event handling.
 */
enum class SensorSampleType {
  Byte,
  Float,
  Occurrence,
  ThreeAxis,
  Unknown,
};

/**
 * Returns a string representation of the given sensor type.
 *
 * @param sensorType The sensor type to obtain a string for.
 * @return A string representation of the sensor type.
 */
const char *getSensorTypeName(SensorType sensorType);

/**
 * Returns a sensor sample event type for a given sensor type. The sensor type
 * must not be SensorType::Unknown. This is a fatal error.
 *
 * @param sensorType The type of the sensor.
 * @return The event type for a sensor sample of the given sensor type.
 */
uint16_t getSampleEventTypeForSensorType(SensorType sensorType);

/**
 * Returns a sensor type for a given sensor sample event type.
 *
 * @param eventType The event type for a sensor sample.
 * @return The type of the sensor.
 */
SensorType getSensorTypeForSampleEventType(uint16_t eventType);

/**
 * @return An index into an array for a given sensor type. This is useful to map
 * sensor type to array index quickly. The range returned corresponds to any
 * SensorType except for Unknown starting from zero to the maximum value sensor
 * with no gaps.
 */
constexpr size_t getSensorTypeArrayIndex(SensorType sensorType);

/**
 * @return The number of valid sensor types in the SensorType enum.
 */
constexpr size_t getSensorTypeCount();

/**
 * Translates an unsigned integer as provided by a CHRE-compliant nanoapp to a
 * SensorType. If the integer sensor type does not match one of the internal
 * sensor types, SensorType::Unknown is returned.
 *
 * @param sensorType The integer sensor type.
 * @return The strongly-typed sensor if a match is found or SensorType::Unknown.
 */
SensorType getSensorTypeFromUnsignedInt(uint8_t sensorType);

/**
 * Translates a SensorType to an unsigned integer as provided by CHRE API. If
 * the sensor type is SensorType::Unknown, 0 is returned.
 *
 * @param sensorType The strongly-typed sensor.
 * @return The integer sensor type if sensorType is not SensorType::Unknown.
 */
uint8_t getUnsignedIntFromSensorType(SensorType sensorType);

/**
 * Provides a stable handle for a CHRE sensor type. This handle is exposed to
 * CHRE nanoapps as a way to refer to sensors that they are subscribing to. This
 * API is not expected to be called with SensorType::Unknown as nanoapps are not
 * able to subscribe to the Unknown sensor type.
 *
 * @param sensorType The type of the sensor to obtain a handle for.
 * @return The handle for a given sensor.
 */
constexpr uint32_t getSensorHandleFromSensorType(SensorType sensorType);

/**
 * Maps a sensor handle to a SensorType or returns SensorType::Unknown if the
 * provided handle is invalid.
 *
 * @param handle The sensor handle for a sensor.
 * @return The sensor type for a given handle.
 */
constexpr SensorType getSensorTypeFromSensorHandle(uint32_t handle);

/**
 * Maps a sensorType to its SensorSampleType.
 *
 * @param sensorType The type of the sensor to obtain its SensorSampleType for.
 * @return The SensorSampleType of the sensorType.
 */
SensorSampleType getSensorSampleTypeFromSensorType(SensorType sensorType);

/**
 * This SensorMode is designed to wrap constants provided by the CHRE API to
 * imrpove type-safety. The details of these modes are left to the CHRE API mode
 * definitions contained in the chreSensorConfigureMode enum.
 */
enum class SensorMode {
  Off,
  ActiveContinuous,
  ActiveOneShot,
  PassiveContinuous,
  PassiveOneShot,
};

/**
 * @return true if the sensor mode is considered to be active and would cause a
 * sensor to be powered on in order to get sensor data.
 */
constexpr bool sensorModeIsActive(SensorMode sensorMode);

/**
 * @return true if the sensor mode is considered to be contunuous.
 */
constexpr bool sensorModeIsContinuous(SensorMode sensorMode);

/**
 * @return true if the sensor mode is considered to be one-shot.
 */
constexpr bool sensorModeIsOneShot(SensorMode sensorMode);

/**
 * Translates a CHRE API enum sensor mode to a SensorMode. This function also
 * performs input validation and will default to SensorMode::Off if the provided
 * value is not a valid enum value.
 *
 * @param enumSensorMode A potentially unsafe CHRE API enum sensor mode.
 * @return Returns a SensorMode given a CHRE API enum sensor mode.
 */
SensorMode getSensorModeFromEnum(enum chreSensorConfigureMode enumSensorMode);

/**
 * Indicates whether the sensor type is a one-shot sensor.
 *
 * @param sensorType The sensor type of the sensor.
 * @return true if the sensor is a one-shot sensor.
 */
bool sensorTypeIsOneShot(SensorType sensorType);

/**
 * Indicates whether the sensor type is an on-change sensor.
 *
 * @param sensorType The sensor type of the sensor.
 * @return true if the sensor is an on-change sensor.
 */
bool sensorTypeIsOnChange(SensorType sensorType);

/**
 * Models a request for sensor data. This class implements the API set forth by
 * the RequestMultiplexer container.
 */
class SensorRequest {
 public:
  /**
   * Default constructs a sensor request to the minimal possible configuration.
   * The sensor is disabled and the interval and latency are both set to zero.
   */
  SensorRequest();

  /**
   * Constructs a sensor request given a mode, interval and latency.
   *
   * @param mode The mode of the sensor request.
   * @param interval The interval between samples.
   * @param latency The maximum amount of time to batch samples before
   *        delivering to a client.
   */
  SensorRequest(SensorMode mode, Nanoseconds interval, Nanoseconds latency);

  /**
   * Constructs a sensor request given an owning nanoapp, mode, interval and
   * latency.
   *
   * @param nanoapp The nanoapp that made this request.
   * @param mode The mode of the sensor request.
   * @param interval The interval between samples.
   * @param latency The maximum amount of time to batch samples before
   *        delivering to a client.
   */
  SensorRequest(Nanoapp *nanoapp, SensorMode mode, Nanoseconds interval,
                Nanoseconds latency);

  /**
   * Performs an equivalency comparison of two sensor requests. This determines
   * if the effective request for sensor data is the same as another.
   *
   * @param request The request to compare against.
   * @return Returns true if this request is equivalent to another.
   */
  bool isEquivalentTo(const SensorRequest& request) const;

  /**
   * Assigns the current request to the maximal superset of the mode, rate
   * and latency of the other request.
   *
   * @param request The other request to compare the attributes of.
   * @return true if any of the attributes of this request changed.
   */
  bool mergeWith(const SensorRequest& request);

  /**
   * @return Returns the interval of samples for this request.
   */
  Nanoseconds getInterval() const;

  /**
   * @return Returns the maximum amount of time samples can be batched prior to
   * dispatching to the client.
   */
  Nanoseconds getLatency() const;

  /**
   * @return The mode of this request.
   */
  SensorMode getMode() const;

  /**
   * @return The nanoapp that owns this sensor request.
   */
  Nanoapp *getNanoapp() const;

 private:
  //! The nanoapp that made this request. This will be nullptr when returned by
  //! the generateIntersectionOf method.
  Nanoapp *mNanoapp = nullptr;

  //! The interval between samples for this request.
  Nanoseconds mInterval;

  //! The maximum amount of time samples can be batched prior to dispatching to
  //! the client
  Nanoseconds mLatency;

  //! The mode of this request.
  SensorMode mMode;
};

}  // namespace chre

#include "chre/core/sensor_request_impl.h"

#endif  // CHRE_CORE_SENSOR_REQUEST_H_
