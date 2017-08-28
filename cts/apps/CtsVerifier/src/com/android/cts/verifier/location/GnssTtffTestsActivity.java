package com.android.cts.verifier.location;

import com.android.cts.verifier.location.base.GnssCtsTestActivity;
import android.location.cts.GnssTtffTests;

/**
 * Activity to execute CTS GnssStatusTest.
 * It is a wrapper for {@link GnssTtffTests} running with AndroidJUnitRunner.
 */

public class GnssTtffTestsActivity extends GnssCtsTestActivity {
  public GnssTtffTestsActivity() {
    super(GnssTtffTests.class);
  }
}
