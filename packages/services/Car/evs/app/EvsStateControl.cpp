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

#define LOG_TAG "EVSAPP"

#include "EvsStateControl.h"

#include <stdio.h>
#include <string.h>

#include <log/log.h>


// TODO:  Seems like it'd be nice if the Vehicle HAL provided such helpers (but how & where?)
inline constexpr VehiclePropertyType getPropType(VehicleProperty prop) {
    return static_cast<VehiclePropertyType>(
            static_cast<int32_t>(prop)
            & static_cast<int32_t>(VehiclePropertyType::MASK));
}


EvsStateControl::EvsStateControl(android::sp <IVehicle>       pVnet,
                                 android::sp <IEvsEnumerator> pEvs,
                                 android::sp <IEvsDisplay>    pDisplay,
                                 const ConfigManager&         config) :
    mVehicle(pVnet),
    mEvs(pEvs),
    mDisplay(pDisplay),
    mCurrentState(OFF) {

    // Initialize the property value containers we'll be updating (they'll be zeroed by default)
    static_assert(getPropType(VehicleProperty::GEAR_SELECTION) == VehiclePropertyType::INT32,
                  "Unexpected type for GEAR_SELECTION property");
    static_assert(getPropType(VehicleProperty::TURN_SIGNAL_STATE) == VehiclePropertyType::INT32,
                  "Unexpected type for TURN_SIGNAL_STATE property");

    mGearValue.prop       = static_cast<int32_t>(VehicleProperty::GEAR_SELECTION);
    mTurnSignalValue.prop = static_cast<int32_t>(VehicleProperty::TURN_SIGNAL_STATE);

    // Build our set of cameras for the states we support
    ALOGD("Requesting camera list");
    mEvs->getCameraList([this, &config]
                        (hidl_vec<CameraDesc> cameraList) {
                            ALOGI("Camera list callback received %zu cameras",
                                  cameraList.size());
                            for (auto&& cam: cameraList) {
                                ALOGD("Found camera %s", cam.cameraId.c_str());
                                bool cameraConfigFound = false;

                                // Check our configuration for information about this camera
                                // Note that a camera can have a compound function string
                                // such that a camera can be "right/reverse" and be used for both.
                                for (auto&& info: config.getCameras()) {
                                    if (cam.cameraId == info.cameraId) {
                                        // We found a match!
                                        if (info.function.find("reverse") != std::string::npos) {
                                            mCameraInfo[State::REVERSE] = info;
                                        }
                                        if (info.function.find("right") != std::string::npos) {
                                            mCameraInfo[State::RIGHT] = info;
                                        }
                                        if (info.function.find("left") != std::string::npos) {
                                            mCameraInfo[State::LEFT] = info;
                                        }
                                        cameraConfigFound = true;
                                        break;
                                    }
                                }
                                if (!cameraConfigFound) {
                                    ALOGW("No config information for hardware camera %s",
                                          cam.cameraId.c_str());
                                }
                            }
                        }
    );
    ALOGD("State controller ready");
}


bool EvsStateControl::configureForVehicleState() {
    ALOGD("configureForVehicleState");

    static int32_t sDummyGear   = int32_t(VehicleGear::GEAR_REVERSE);
    static int32_t sDummySignal = int32_t(VehicleTurnSignal::NONE);

    if (mVehicle != nullptr) {
        // Query the car state
        if (invokeGet(&mGearValue) != StatusCode::OK) {
            ALOGE("GEAR_SELECTION not available from vehicle.  Exiting.");
            return false;
        }
        if (invokeGet(&mTurnSignalValue) != StatusCode::OK) {
            // Silently treat missing turn signal state as no turn signal active
            mTurnSignalValue.value.int32Values.setToExternal(&sDummySignal, 1);
        }
    } else {
        // While testing without a vehicle, behave as if we're in reverse for the first 20 seconds
        static const int kShowTime = 20;    // seconds

        // See if it's time to turn off the default reverse camera
        static std::chrono::steady_clock::time_point start = std::chrono::steady_clock::now();
        std::chrono::steady_clock::time_point now = std::chrono::steady_clock::now();
        if (std::chrono::duration_cast<std::chrono::seconds>(now - start).count() > kShowTime) {
            // Switch to drive (which should turn off the reverse camera)
            sDummyGear = int32_t(VehicleGear::GEAR_DRIVE);
        }

        // Build the dummy vehicle state values (treating single values as 1 element vectors)
        mGearValue.value.int32Values.setToExternal(&sDummyGear, 1);
        mTurnSignalValue.value.int32Values.setToExternal(&sDummySignal, 1);
    }

    // Choose our desired EVS state based on the current car state
    State desiredState = OFF;
    if (mGearValue.value.int32Values[0] == int32_t(VehicleGear::GEAR_REVERSE)) {
        desiredState = REVERSE;
    } else if (mTurnSignalValue.value.int32Values[0] == int32_t(VehicleTurnSignal::RIGHT)) {
        desiredState = RIGHT;
    } else if (mTurnSignalValue.value.int32Values[0] == int32_t(VehicleTurnSignal::LEFT)) {
        desiredState = LEFT;
    }

    // Apply the desire state
    ALOGV("Selected state %d.", desiredState);
    configureEvsPipeline(desiredState);

    // Operation was successful
    return true;
}


StatusCode EvsStateControl::invokeGet(VehiclePropValue *pRequestedPropValue) {
    ALOGD("invokeGet");

    StatusCode status = StatusCode::TRY_AGAIN;
    bool called = false;

    // Call the Vehicle HAL, which will block until the callback is complete
    mVehicle->get(*pRequestedPropValue,
                  [pRequestedPropValue, &status, &called]
                  (StatusCode s, const VehiclePropValue& v) {
                       status = s;
                       *pRequestedPropValue = v;
                       called = true;
                  }
    );
    // This should be true as long as the get call is block as it should
    // TODO:  Once we've got some milage on this code and the underlying HIDL services,
    // we should remove this belt-and-suspenders check for correct operation as unnecessary.
    if (!called) {
        ALOGE("VehicleNetwork query did not run as expected.");
    }

    return status;
}


bool EvsStateControl::configureEvsPipeline(State desiredState) {
    ALOGD("configureEvsPipeline");

    if (mCurrentState == desiredState) {
        // Nothing to do here...
        return true;
    }

    // See if we actually have to change cameras
    if (mCameraInfo[mCurrentState].cameraId != mCameraInfo[desiredState].cameraId) {
        ALOGI("Camera change required");
        ALOGD("  Current cameraId (%d) = %s", mCurrentState,
              mCameraInfo[mCurrentState].cameraId.c_str());
        ALOGD("  Desired cameraId (%d) = %s", desiredState,
              mCameraInfo[desiredState].cameraId.c_str());

        // Yup, we need to change cameras, so close the previous one, if necessary.
        if (mCurrentCamera != nullptr) {
            mCurrentStreamHandler->blockingStopStream();
            mCurrentStreamHandler = nullptr;
            mCurrentCamera = nullptr;
        }

        // Now do we need a new camera?
        if (!mCameraInfo[desiredState].cameraId.empty()) {
            // Need a new camera, so open it
            ALOGD("Open camera %s", mCameraInfo[desiredState].cameraId.c_str());
            mCurrentCamera = mEvs->openCamera(mCameraInfo[desiredState].cameraId);

            // If we didn't get the camera we asked for, we need to bail out and try again later
            if (mCurrentCamera == nullptr) {
                ALOGE("Failed to open EVS camera.  Skipping state change.");
                return false;
            }
        }

        // Now set the display state based on whether we have a camera feed to show
        if (mCurrentCamera == nullptr) {
            ALOGD("Turning off the display");
            mDisplay->setDisplayState(DisplayState::NOT_VISIBLE);
        } else {
            // Create the stream handler object to receive and forward the video frames
            mCurrentStreamHandler = new StreamHandler(mCurrentCamera, mDisplay);

            // Start the camera stream
            ALOGD("Starting camera stream");
            mCurrentStreamHandler->startStream();

            // Activate the display
            ALOGD("Arming the display");
            mDisplay->setDisplayState(DisplayState::VISIBLE_ON_NEXT_FRAME);
        }
    }

    // Record our current state
    ALOGI("Activated state %d.", desiredState);
    mCurrentState = desiredState;

    return true;
}
