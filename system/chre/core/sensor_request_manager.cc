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

#include "chre/core/sensor_request_manager.h"

#include "chre/core/event_loop_manager.h"
#include "chre/platform/fatal_error.h"
#include "chre_api/chre/version.h"

namespace chre {
namespace {

bool isSensorRequestValid(const Sensor& sensor,
                          const SensorRequest& sensorRequest) {
  bool isRequestContinuous = sensorModeIsContinuous(
      sensorRequest.getMode());
  bool isRequestOneShot = sensorModeIsOneShot(sensorRequest.getMode());
  uint64_t requestedInterval = sensorRequest.getInterval().toRawNanoseconds();
  uint64_t requestedLatency = sensorRequest.getLatency().toRawNanoseconds();
  SensorType sensorType = sensor.getSensorType();

  bool success = true;
  if (isRequestContinuous) {
    if (sensorTypeIsOneShot(sensorType)) {
      success = false;
      LOGE("Invalid continuous request for a one-shot sensor.");
    } else if (requestedInterval < sensor.getMinInterval()) {
      success = false;
      LOGE("Invalid requested interval %" PRIu64 " for a continuous sensor"
           " with minInterval %" PRIu64,
           requestedInterval, sensor.getMinInterval());
    }
  } else if (isRequestOneShot) {
    if (!sensorTypeIsOneShot(sensorType)) {
      success = false;
      LOGE("Invalid one-shot request for a continuous sensor.");
    } else if (requestedInterval != CHRE_SENSOR_INTERVAL_DEFAULT ||
               requestedLatency != CHRE_SENSOR_LATENCY_DEFAULT) {
      success = false;
      LOGE("Invalid interval and/or latency for a one-shot request.");
    }
  }
  return success;
}

}  // namespace

SensorRequestManager::SensorRequestManager() {
  mSensorRequests.resize(mSensorRequests.capacity());

  DynamicVector<PlatformSensor> platformSensors;
  if (!PlatformSensor::getSensors(&platformSensors)) {
    LOGE("Failed to query the platform for sensors");
    return;
  }

  if (platformSensors.empty()) {
    LOGW("Platform returned zero sensors");
  }

  for (size_t i = 0; i < platformSensors.size(); i++) {
    SensorType sensorType = platformSensors[i].getSensorType();
    size_t sensorIndex = getSensorTypeArrayIndex(sensorType);
    LOGD("Found sensor: %s", getSensorTypeName(sensorType));

    mSensorRequests[sensorIndex].sensor =
        Sensor(std::move(platformSensors[i]));
  }
}

SensorRequestManager::~SensorRequestManager() {
  SensorRequest nullRequest = SensorRequest();
  for (size_t i = 0; i < mSensorRequests.size(); i++) {
    // Disable sensors that have been enabled previously.
    Sensor& sensor = mSensorRequests[i].sensor;
    sensor.setRequest(nullRequest);
  }
}

bool SensorRequestManager::getSensorHandle(SensorType sensorType,
                                           uint32_t *sensorHandle) const {
  CHRE_ASSERT(sensorHandle);

  bool sensorHandleIsValid = false;
  if (sensorType == SensorType::Unknown) {
    LOGW("Querying for unknown sensor type");
  } else {
    size_t sensorIndex = getSensorTypeArrayIndex(sensorType);
    sensorHandleIsValid = mSensorRequests[sensorIndex].sensor.isValid();
    if (sensorHandleIsValid) {
      *sensorHandle = getSensorHandleFromSensorType(sensorType);
    }
  }

  return sensorHandleIsValid;
}

bool SensorRequestManager::setSensorRequest(Nanoapp *nanoapp,
    uint32_t sensorHandle, const SensorRequest& sensorRequest) {
  CHRE_ASSERT(nanoapp);

  // Validate the input to ensure that a valid handle has been provided.
  SensorType sensorType = getSensorTypeFromSensorHandle(sensorHandle);
  if (sensorType == SensorType::Unknown) {
    LOGW("Attempting to configure an invalid handle");
    return false;
  }

  // Ensure that the runtime is aware of this sensor type.
  size_t sensorIndex = getSensorTypeArrayIndex(sensorType);
  SensorRequests& requests = mSensorRequests[sensorIndex];
  const Sensor& sensor = requests.sensor;

  if (!sensor.isValid()) {
    LOGW("Attempting to configure non-existent sensor");
    return false;
  } else if (!isSensorRequestValid(sensor, sensorRequest)) {
    return false;
  }

  size_t requestIndex;
  uint16_t eventType = getSampleEventTypeForSensorType(sensorType);
  bool nanoappHasRequest = (requests.find(nanoapp, &requestIndex) != nullptr);

  bool success;
  bool requestChanged;
  if (sensorRequest.getMode() == SensorMode::Off) {
    if (nanoappHasRequest) {
      // The request changes the mode to off and there was an existing request.
      // The existing request is removed from the multiplexer. The nanoapp is
      // unregistered from events of this type if this request was successful.
      success = requests.remove(requestIndex, &requestChanged);
      if (success) {
        nanoapp->unregisterForBroadcastEvent(eventType);
      }
    } else {
      // The sensor is being configured to Off, but is already Off (there is no
      // existing request). We assign to success to be true and no other
      // operation is required.
      requestChanged = false;
      success = true;
    }
  } else if (!nanoappHasRequest) {
    // The request changes the mode to the enabled state and there was no
    // existing request. The request is newly created and added to the
    // multiplexer. The nanoapp is registered for events if this request was
    // successful.
    success = requests.add(sensorRequest, &requestChanged);
    if (success) {
      nanoapp->registerForBroadcastEvent(eventType);

      // Deliver last valid event to new clients of on-change sensors
      if (sensorTypeIsOnChange(sensor.getSensorType())
          && sensor.getLastEvent() != nullptr) {
        EventLoopManagerSingleton::get()->postEvent(
            getSampleEventTypeForSensorType(sensorType), sensor.getLastEvent(),
            nullptr, kSystemInstanceId, nanoapp->getInstanceId());
      }
    }
  } else {
    // The request changes the mode to the enabled state and there was an
    // existing request. The existing request is updated.
    success = requests.update(requestIndex, sensorRequest, &requestChanged);
  }

  if (requestChanged) {
    // TODO: Send an event to nanoapps to indicate the rate change.
  }

  return success;
}

bool SensorRequestManager::getSensorInfo(uint32_t sensorHandle,
                                         const Nanoapp *nanoapp,
                                         struct chreSensorInfo *info) const {
  CHRE_ASSERT(nanoapp);
  CHRE_ASSERT(info);

  bool success = false;

  // Validate the input to ensure that a valid handle has been provided.
  SensorType sensorType = getSensorTypeFromSensorHandle(sensorHandle);
  if (sensorType == SensorType::Unknown) {
    LOGW("Attempting to access sensor with an invalid handle %" PRIu32,
         sensorHandle);
  } else {
    success = true;

    // Platform-independent properties.
    info->sensorType = getUnsignedIntFromSensorType(sensorType);
    info->isOnChange = sensorTypeIsOnChange(sensorType);
    info->isOneShot = sensorTypeIsOneShot(sensorType);
    info->unusedFlags = 0;

    // Platform-specific properties.
    size_t sensorIndex = getSensorTypeArrayIndex(sensorType);
    const Sensor& sensor = mSensorRequests[sensorIndex].sensor;
    info->sensorName = sensor.getSensorName();

    // minInterval was added in CHRE API 1.1.
    if (nanoapp->getTargetApiVersion() >= CHRE_API_VERSION_1_1) {
      info->minInterval = info->isOneShot ? CHRE_SENSOR_INTERVAL_DEFAULT :
          sensor.getMinInterval();
    }
  }
  return success;
}

bool SensorRequestManager::removeAllRequests(SensorType sensorType) {
  bool success = false;
  if (sensorType == SensorType::Unknown) {
    LOGW("Attempting to remove all requests of an invalid sensor type");
  } else {
    size_t sensorIndex = getSensorTypeArrayIndex(sensorType);
    SensorRequests& requests = mSensorRequests[sensorIndex];
    uint16_t eventType = getSampleEventTypeForSensorType(sensorType);

    for (const SensorRequest& request : requests.multiplexer.getRequests()) {
      Nanoapp *nanoapp = request.getNanoapp();
      nanoapp->unregisterForBroadcastEvent(eventType);
    }

    success = requests.removeAll();
  }
  return success;
}

Sensor *SensorRequestManager::getSensor(SensorType sensorType) {
  Sensor *sensorPtr = nullptr;
  if (sensorType == SensorType::Unknown) {
    LOGW("Attempting to get Sensor of an invalid SensorType");
  } else {
    size_t sensorIndex = getSensorTypeArrayIndex(sensorType);
    sensorPtr = &mSensorRequests[sensorIndex].sensor;
  }
  return sensorPtr;
}

const SensorRequest *SensorRequestManager::SensorRequests::find(
    const Nanoapp *nanoapp, size_t *index) const {
  CHRE_ASSERT(index);

  const auto& requests = multiplexer.getRequests();
  for (size_t i = 0; i < requests.size(); i++) {
    const SensorRequest& sensorRequest = requests[i];
    if (sensorRequest.getNanoapp() == nanoapp) {
      *index = i;
      return &sensorRequest;
    }
  }

  return nullptr;
}

bool SensorRequestManager::SensorRequests::add(const SensorRequest& request,
                                               bool *requestChanged) {
  CHRE_ASSERT(requestChanged != nullptr);

  size_t addIndex;
  bool success = true;
  if (!multiplexer.addRequest(request, &addIndex, requestChanged)) {
    *requestChanged = false;
    success = false;
    LOG_OOM();
  } else if (*requestChanged) {
    success = sensor.setRequest(multiplexer.getCurrentMaximalRequest());
    if (!success) {
      // Remove the newly added request since the platform failed to handle it.
      // The sensor is expected to maintain the existing request so there is no
      // need to reset the platform to the last maximal request.
      multiplexer.removeRequest(addIndex, requestChanged);

      // This is a roll-back operation so the maximal change in the multiplexer
      // must not have changed. The request changed state is forced to false.
      *requestChanged = false;
    }
  }

  return success;
}

bool SensorRequestManager::SensorRequests::remove(size_t removeIndex,
                                                  bool *requestChanged) {
  CHRE_ASSERT(requestChanged != nullptr);

  bool success = true;
  multiplexer.removeRequest(removeIndex, requestChanged);
  if (*requestChanged) {
    success = sensor.setRequest(multiplexer.getCurrentMaximalRequest());
    if (!success) {
      LOGE("SensorRequestManager failed to remove a request");

      // If the platform fails to handle this request in a debug build there is
      // likely an error in the platform. This is not strictly a programming
      // error but it does make sense to use assert semantics when a platform
      // fails to handle a request that it had been sent previously.
      CHRE_ASSERT(false);

      // The request to the platform to set a request when removing has failed
      // so the request has not changed.
      *requestChanged = false;
    }
  }

  return success;
}

bool SensorRequestManager::SensorRequests::update(size_t updateIndex,
                                                  const SensorRequest& request,
                                                  bool *requestChanged) {
  CHRE_ASSERT(requestChanged != nullptr);

  bool success = true;
  SensorRequest previousRequest = multiplexer.getRequests()[updateIndex];
  multiplexer.updateRequest(updateIndex, request, requestChanged);
  if (*requestChanged) {
    success = sensor.setRequest(multiplexer.getCurrentMaximalRequest());
    if (!success) {
      // Roll back the request since sending it to the sensor failed. The
      // request will roll back to the previous maximal. The sensor is
      // expected to maintain the existing request if a request fails so there
      // is no need to reset the platform to the last maximal request.
      multiplexer.updateRequest(updateIndex, previousRequest, requestChanged);

      // This is a roll-back operation so the maximal change in the multiplexer
      // must not have changed. The request changed state is forced to false.
      *requestChanged = false;
    }
  }

  return success;
}

bool SensorRequestManager::SensorRequests::removeAll() {
  bool requestChanged;
  multiplexer.removeAllRequests(&requestChanged);

  bool success = true;
  if (requestChanged) {
    SensorRequest maximalRequest = multiplexer.getCurrentMaximalRequest();
    success = sensor.setRequest(maximalRequest);
    if (!success) {
      LOGE("SensorRequestManager failed to remove all request");

      // If the platform fails to handle this request in a debug build there is
      // likely an error in the platform. This is not strictly a programming
      // error but it does make sense to use assert semantics when a platform
      // fails to handle a request that it had been sent previously.
      CHRE_ASSERT(false);
    }
  }
  return success;
}

}  // namespace chre
