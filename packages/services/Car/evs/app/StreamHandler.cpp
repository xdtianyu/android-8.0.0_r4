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

#define LOG_TAG "EvsTest"

#include "StreamHandler.h"

#include <stdio.h>
#include <string.h>

#include <android/log.h>
#include <cutils/native_handle.h>
#include <ui/GraphicBuffer.h>

#include <algorithm>    // std::min


// For the moment, we're assuming that the underlying EVS driver we're working with
// is providing 4 byte RGBx data.  This is fine for loopback testing, although
// real hardware is expected to provide YUV data -- most likly formatted as YV12
static const unsigned kBytesPerPixel = 4;   // assuming 4 byte RGBx pixels


StreamHandler::StreamHandler(android::sp<IEvsCamera> pCamera, android::sp<IEvsDisplay> pDisplay) :
    mCamera(pCamera),
    mDisplay(pDisplay) {
}


void StreamHandler::startStream() {
    // Mark ourselves as running
    mLock.lock();
    mRunning = true;
    mLock.unlock();

    // Tell the camera to start streaming
    mCamera->startVideoStream(this);
}


void StreamHandler::asyncStopStream() {
    // Tell the camera to stop streaming.
    // This will result in a null frame being delivered when the stream actually stops.
    mCamera->stopVideoStream();
}


void StreamHandler::blockingStopStream() {
    // Tell the stream to stop
    asyncStopStream();

    // Wait until the stream has actually stopped
    std::unique_lock<std::mutex> lock(mLock);
    mSignal.wait(lock, [this](){ return !mRunning; });
}


bool StreamHandler::isRunning() {
    std::unique_lock<std::mutex> lock(mLock);
    return mRunning;
}


unsigned StreamHandler::getFramesReceived() {
    std::unique_lock<std::mutex> lock(mLock);
    return mFramesReceived;
};


unsigned StreamHandler::getFramesCompleted() {
    std::unique_lock<std::mutex> lock(mLock);
    return mFramesCompleted;
};


Return<void> StreamHandler::deliverFrame(const BufferDesc& bufferArg) {
    ALOGD("Received a frame from the camera (%p)", bufferArg.memHandle.getNativeHandle());

    // Local flag we use to keep track of when the stream is stopping
    bool timeToStop = false;

    if (bufferArg.memHandle.getNativeHandle() == nullptr) {
        // Signal that the last frame has been received and that the stream should stop
        timeToStop = true;
        ALOGI("End of stream signaled");
    } else {
        // Get the output buffer we'll use to display the imagery
        BufferDesc tgtBuffer = {};
        mDisplay->getTargetBuffer([&tgtBuffer]
                                  (const BufferDesc& buff) {
                                      tgtBuffer = buff;
                                      ALOGD("Got output buffer (%p) with id %d cloned as (%p)",
                                            buff.memHandle.getNativeHandle(),
                                            tgtBuffer.bufferId,
                                            tgtBuffer.memHandle.getNativeHandle());
                                  }
        );

        if (tgtBuffer.memHandle == nullptr) {
            printf("Didn't get target buffer - frame lost\n");
            ALOGE("Didn't get requested output buffer -- skipping this frame.");
        } else {
            // Copy the contents of the of buffer.memHandle into tgtBuffer
            copyBufferContents(tgtBuffer, bufferArg);

            // TODO:  Add a bit of overlay graphics?
            // TODO:  Use OpenGL to render from texture?
            // NOTE:  If we mess with the frame contents, we'll need to update the frame inspection
            //        logic in the default (test) display driver.

            // Send the target buffer back for display
            ALOGD("Calling returnTargetBufferForDisplay (%p)",
                  tgtBuffer.memHandle.getNativeHandle());
            Return<EvsResult> result = mDisplay->returnTargetBufferForDisplay(tgtBuffer);
            if (!result.isOk()) {
                printf("HIDL error on display buffer (%s)- frame lost\n",
                       result.description().c_str());
                ALOGE("Error making the remote function call.  HIDL said %s",
                      result.description().c_str());
            } else if (result != EvsResult::OK) {
                printf("Display reported error - frame lost\n");
                ALOGE("We encountered error %d when returning a buffer to the display!",
                      (EvsResult)result);
            } else {
                // Everything looks good!  Keep track so tests or watch dogs can monitor progress
                mLock.lock();
                mFramesCompleted++;
                mLock.unlock();
                printf("frame OK\n");
            }
        }

        // Send the camera buffer back now that we're done with it
        ALOGD("Calling doneWithFrame");
        // TODO:  Why is it that we get a HIDL crash if we pass back the cloned buffer?
        mCamera->doneWithFrame(bufferArg);

        ALOGD("Frame handling complete");
    }


    // Update our received frame count and notify anybody who cares that things have changed
    mLock.lock();
    if (timeToStop) {
        mRunning = false;
    } else {
        mFramesReceived++;
    }
    mLock.unlock();
    mSignal.notify_all();


    return Void();
}


bool StreamHandler::copyBufferContents(const BufferDesc& tgtBuffer,
                                       const BufferDesc& srcBuffer) {
    bool success = true;

    // Make sure we don't run off the end of either buffer
    const unsigned width     = std::min(tgtBuffer.width,
                                        srcBuffer.width);
    const unsigned height    = std::min(tgtBuffer.height,
                                        srcBuffer.height);

    sp<android::GraphicBuffer> tgt = new android::GraphicBuffer(
            tgtBuffer.memHandle, android::GraphicBuffer::CLONE_HANDLE,
            tgtBuffer.width, tgtBuffer.height, tgtBuffer.format, 1,
            tgtBuffer.usage, tgtBuffer.stride);
    sp<android::GraphicBuffer> src = new android::GraphicBuffer(
            srcBuffer.memHandle, android::GraphicBuffer::CLONE_HANDLE,
            srcBuffer.width, srcBuffer.height, srcBuffer.format, 1,
            srcBuffer.usage, srcBuffer.stride);

    // Lock our source buffer for reading
    unsigned char* srcPixels = nullptr;
    src->lock(GRALLOC_USAGE_SW_READ_OFTEN, (void **) &srcPixels);

    // Lock our target buffer for writing
    unsigned char* tgtPixels = nullptr;
    tgt->lock(GRALLOC_USAGE_SW_WRITE_OFTEN, (void **) &tgtPixels);

    if (srcPixels && tgtPixels) {
        for (unsigned row = 0; row < height; row++) {
            // Copy the entire row of pixel data
            memcpy(tgtPixels, srcPixels, width * kBytesPerPixel);

            // Advance to the next row (keeping in mind that stride here is in units of pixels)
            tgtPixels += tgtBuffer.stride * kBytesPerPixel;
            srcPixels += srcBuffer.stride * kBytesPerPixel;
        }
    } else {
        ALOGE("Failed to copy buffer contents");
        success = false;
    }

    if (srcPixels) {
        src->unlock();
    }
    if (tgtPixels) {
        tgt->unlock();
    }

    return success;
}
