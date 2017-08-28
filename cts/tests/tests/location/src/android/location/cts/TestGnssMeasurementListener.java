/*
 * Copyright (C) 2015 Google Inc.
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
package android.location.cts;

import junit.framework.Assert;

import android.location.GnssMeasurementsEvent;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Used for receiving GPS satellite measurements from the GPS engine.
 * Each measurement contains raw and computed data identifying a satellite.
 * Only counts measurement events with more than one actual Measurement in them (not just clock)
 */
class TestGnssMeasurementListener extends GnssMeasurementsEvent.Callback {
    // Timeout in sec for count down latch wait
    private static final int STATUS_TIMEOUT_IN_SEC = 10;
    private static final int MEAS_TIMEOUT_IN_SEC = 60;

    private volatile int mStatus = -1;

    private final String mTag;
    private final List<GnssMeasurementsEvent> mMeasurementsEvents;
    private final CountDownLatch mCountDownLatch;
    private final CountDownLatch mCountDownLatchStatus;

    TestGnssMeasurementListener(String tag) {
        this(tag, 0);
    }

    TestGnssMeasurementListener(String tag, int eventsToCollect) {
        mTag = tag;
        mCountDownLatch = new CountDownLatch(eventsToCollect);
        mCountDownLatchStatus = new CountDownLatch(1);
        mMeasurementsEvents = new ArrayList<>(eventsToCollect);
    }

    @Override
    public void onGnssMeasurementsReceived(GnssMeasurementsEvent event) {
        // Only count measurement events with more than one actual Measurement in them
        // (not just clock)
        if (event.getMeasurements().size() > 0) {
            synchronized(mMeasurementsEvents) {
                mMeasurementsEvents.add(event);
            }
            mCountDownLatch.countDown();
        }
    }

    @Override
    public void onStatusChanged(int status) {
        mStatus = status;
        mCountDownLatchStatus.countDown();
    }

    public boolean awaitStatus() throws InterruptedException {
        return TestUtils.waitFor(mCountDownLatchStatus, STATUS_TIMEOUT_IN_SEC);
    }

    public boolean await() throws InterruptedException {
        return TestUtils.waitFor(mCountDownLatch, MEAS_TIMEOUT_IN_SEC);
    }


    /**
     * @return {@code true} if the state of the test ensures that data is expected to be collected,
     *         {@code false} otherwise.
     */
    public boolean verifyStatus(boolean testIsStrict) {
        switch (getStatus()) {
            case GnssMeasurementsEvent.Callback.STATUS_NOT_SUPPORTED:
                String message = "GnssMeasurements is not supported in the device:"
                        + " verifications performed by this test may be skipped on older devices.";
                if (testIsStrict) {
                    Assert.fail(message);
                } else {
                    SoftAssert.failAsWarning(mTag, message);
                }
                return false;
            case GnssMeasurementsEvent.Callback.STATUS_READY:
                return true;
            case GnssMeasurementsEvent.Callback.STATUS_LOCATION_DISABLED:
                message =  "Location or GPS is disabled on the device:"
                        + " enable location to continue the test";
                if (testIsStrict) {
                    Assert.fail(message);
                } else {
                    SoftAssert.failAsWarning(mTag, message);
                }
                return false;
            default:
                Assert.fail("GnssMeasurementsEvent status callback was not received.");
        }
        return false;
    }

    /**
     * Get GPS Measurements Status.
     *
     * @return mStatus Gps Measurements Status
     */
    public int getStatus() {
        return mStatus;
    }

    /**
     * Get the current list of GPS Measurements Events.
     *
     * @return the current list of GPS Measurements Events
     */
    public List<GnssMeasurementsEvent> getEvents() {
        synchronized(mMeasurementsEvents) {
            List<GnssMeasurementsEvent> clone = new ArrayList<>();
            clone.addAll(mMeasurementsEvents);
            return clone;
        }
    }
}
