/*
 * Copyright (C) 2017 Google Inc.
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


import android.location.GnssMeasurement;
import android.location.GnssMeasurementsEvent;
import android.location.GnssStatus;
import android.util.Log;
import com.android.compatibility.common.util.CddTest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Test computing and verifying the pseudoranges based on the raw measurements
 * reported by the GNSS chipset
 */
public class GnssPseudorangeVerificationTest extends GnssTestCase {
  private static final String TAG = "GnssPseudorangeValTest";
  private static final int LOCATION_TO_COLLECT_COUNT = 5;
  private static final int MEASUREMENT_EVENTS_TO_COLLECT_COUNT = 10;
  private static final int MIN_SATELLITES_REQUIREMENT = 4;
  private static final double SECONDS_PER_NANO = 1.0e-9;
  // GPS/GLONASS: according to http://cdn.intechopen.com/pdfs-wm/27712.pdf, the pseudorange in time
  // is 65-83 ms, which is 18 ms range.
  // GLONASS: orbit is a bit closer than GPS, so we add 0.003ms to the range, hence deltaiSeconds
  // should be in the range of [0.0, 0.021] seconds.
  // QZSS and BEIDOU: they have higher orbit, which will result in a small svTime, the deltai can be
  // calculated as follows:
  // assume a = QZSS/BEIDOU orbit Semi-Major Axis(42,164km for QZSS);
  // b = GLONASS orbit Semi-Major Axis (25,508km);
  // c = Speed of light (299,792km/s);
  // e = earth radius (6,378km);
  // in the extremely case of QZSS is on the horizon and GLONASS is on the 90 degree top
  // max difference should be (sqrt(a^2-e^2) - (b-e))/c,
  // which is around 0.076s.
  private static final double PSEUDORANGE_THRESHOLD_IN_SEC = 0.021;
  // Geosync constellations have a longer range vs typical MEO orbits
  // that are the short end of the range.
  private static final double PSEUDORANGE_THRESHOLD_BEIDOU_QZSS_IN_SEC = 0.076;

  private TestGnssMeasurementListener mMeasurementListener;
  private TestLocationListener mLocationListener;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    mTestLocationManager = new TestLocationManager(getContext());
  }

  @Override
  protected void tearDown() throws Exception {
    // Unregister listeners
    if (mLocationListener != null) {
      mTestLocationManager.removeLocationUpdates(mLocationListener);
    }
    if (mMeasurementListener != null) {
      mTestLocationManager.unregisterGnssMeasurementCallback(mMeasurementListener);
    }
    super.tearDown();
  }

  /**
   * Tests that one can listen for {@link GnssMeasurementsEvent} for collection purposes.
   * It only performs sanity checks for the measurements received.
   * This tests uses actual data retrieved from Gnss HAL.
   */
  @CddTest(requirement="7.3.3")
  public void testPseudorangeValue() throws Exception {
    // Checks if Gnss hardware feature is present, skips test (pass) if not,
    // and hard asserts that Location/Gnss (Provider) is turned on if is Cts Verifier.
    if (!TestMeasurementUtil.canTestRunOnCurrentDevice(mTestLocationManager,
        TAG, MIN_HARDWARE_YEAR_MEASUREMENTS_REQUIRED, isCtsVerifierTest())) {
      return;
    }

    mLocationListener = new TestLocationListener(LOCATION_TO_COLLECT_COUNT);
    mTestLocationManager.requestLocationUpdates(mLocationListener);

    mMeasurementListener = new TestGnssMeasurementListener(TAG,
                                                MEASUREMENT_EVENTS_TO_COLLECT_COUNT);
    mTestLocationManager.registerGnssMeasurementCallback(mMeasurementListener);

    boolean success = mLocationListener.await();
    success &= mMeasurementListener.await();
    SoftAssert.failOrWarning(isMeasurementTestStrict(),
        "Time elapsed without getting enough location fixes."
            + " Possibly, the test has been run deep indoors."
            + " Consider retrying test outdoors.",
        success);

    Log.i(TAG, "Location status received = " + mLocationListener.isLocationReceived());

    if (!mMeasurementListener.verifyStatus(isMeasurementTestStrict())) {
      // If test is strict and verifyStatus reutrns false, an assert exception happens and
      // test fails.   If test is not strict, we arrive here, and:
      return; // exit (with pass)
    }

    List<GnssMeasurementsEvent> events = mMeasurementListener.getEvents();
    int eventCount = events.size();
    Log.i(TAG, "Number of GNSS measurement events received = " + eventCount);
    SoftAssert.failOrWarning(isMeasurementTestStrict(),
        "GnssMeasurementEvent count: expected > 0, received = " + eventCount,
        eventCount > 0);

    SoftAssert softAssert = new SoftAssert(TAG);

    boolean hasEventWithEnoughMeasurements = false;
    // we received events, so perform a quick sanity check on mandatory fields
    for (GnssMeasurementsEvent event : events) {
      // Verify Gnss Event mandatory fields are in required ranges
      assertNotNull("GnssMeasurementEvent cannot be null.", event);

      long timeInNs = event.getClock().getTimeNanos();
      TestMeasurementUtil.assertGnssClockFields(event.getClock(), softAssert, timeInNs);

      ArrayList<GnssMeasurement> filteredMeasurements = filterMeasurements(event.getMeasurements());
      validatePseudorange(filteredMeasurements, softAssert, timeInNs);

      // we need at least 4 satellites to calculate the pseudorange
      if(event.getMeasurements().size() >= MIN_SATELLITES_REQUIREMENT) {
        hasEventWithEnoughMeasurements = true;
      }
    }

    SoftAssert.failOrWarning(isMeasurementTestStrict(),
        "Should have at least one GnssMeasurementEvent with at least 4"
            + "GnssMeasurement. If failed, retry near window or outdoors?",
        hasEventWithEnoughMeasurements);

    softAssert.assertAll();
  }

  private ArrayList<GnssMeasurement> filterMeasurements(Collection<GnssMeasurement> measurements) {
    ArrayList<GnssMeasurement> filteredMeasurement = new ArrayList<>();
    for (GnssMeasurement measurement: measurements){
      int constellationType = measurement.getConstellationType();
      if (constellationType == GnssStatus.CONSTELLATION_GLONASS) {
        if ((measurement.getState()
            & (measurement.STATE_GLO_TOD_DECODED | measurement.STATE_GLO_TOD_KNOWN)) != 0) {
          filteredMeasurement.add(measurement);
        }
      }
      else if ((measurement.getState()
            & (measurement.STATE_TOW_DECODED | measurement.STATE_TOW_KNOWN)) != 0) {
          filteredMeasurement.add(measurement);
        }
    }
    return filteredMeasurement;
  }

  /**
   * Uses the common reception time approach to calculate pseudorange time
   * measurements reported by the receiver according to http://cdn.intechopen.com/pdfs-wm/27712.pdf.
   */
  private void validatePseudorange(Collection<GnssMeasurement> measurements,
      SoftAssert softAssert, long timeInNs) {
    long largestReceivedSvTimeNanosTod = 0;
    // closest satellite has largest time (closest to now), as of nano secs of the day
    // so the largestReceivedSvTimeNanosTod will be the svTime we got from one of the GPS/GLONASS sv
    for(GnssMeasurement measurement : measurements) {
      long receivedSvTimeNanosTod =  measurement.getReceivedSvTimeNanos()
                                        % TimeUnit.DAYS.toNanos(1);
      if (largestReceivedSvTimeNanosTod < receivedSvTimeNanosTod) {
        largestReceivedSvTimeNanosTod = receivedSvTimeNanosTod;
      }
    }
    for (GnssMeasurement measurement : measurements) {
      double threshold = PSEUDORANGE_THRESHOLD_IN_SEC;
      int constellationType = measurement.getConstellationType();
      // BEIDOU and QZSS's Orbit are higher, so the value of ReceivedSvTimeNanos should be small
      if (constellationType == GnssStatus.CONSTELLATION_BEIDOU
          || constellationType == GnssStatus.CONSTELLATION_QZSS) {
        threshold = PSEUDORANGE_THRESHOLD_BEIDOU_QZSS_IN_SEC;
      }
      double deltaiNanos = largestReceivedSvTimeNanosTod
                          - (measurement.getReceivedSvTimeNanos() % TimeUnit.DAYS.toNanos(1));
      double deltaiSeconds = deltaiNanos * SECONDS_PER_NANO;

      softAssert.assertTrue("deltaiSeconds in Seconds.",
          timeInNs,
          "0.0 <= deltaiSeconds <= " + String.valueOf(threshold),
          String.valueOf(deltaiSeconds),
          (deltaiSeconds >= 0.0 && deltaiSeconds <= threshold));
    }

  }
}
