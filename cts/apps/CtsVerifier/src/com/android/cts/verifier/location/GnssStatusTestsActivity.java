package com.android.cts.verifier.location;

import com.android.cts.verifier.location.base.GnssCtsTestActivity;
import android.location.cts.GnssStatusTest;

/**
 * Activity to execute CTS GnssStatusTest.
 * It is a wrapper for {@link GnssStatusTest} running with AndroidJUnitRunner.
 */

public class GnssStatusTestsActivity extends GnssCtsTestActivity {
  public GnssStatusTestsActivity() {
    super(GnssStatusTest.class);
  }
}
