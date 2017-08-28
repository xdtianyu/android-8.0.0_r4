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

package android.net.wifi;

import android.net.wifi.IPnoScanEvent;
import android.net.wifi.IScanEvent;
import com.android.server.wifi.wificond.NativeScanResult;
import com.android.server.wifi.wificond.PnoSettings;
import com.android.server.wifi.wificond.SingleScanSettings;

interface IWifiScannerImpl {
  // Returns an array of available frequencies for 2.4GHz channels.
  // Returrns null on failure.
  @nullable int[] getAvailable2gChannels();

  // Returns an array of available frequencies for 5GHz non-DFS channels.
  // Returrns null on failure.
  @nullable int[] getAvailable5gNonDFSChannels();

  // Returns an array of available frequencies for DFS channels.
  // Returrns null on failure.
  @nullable int[] getAvailableDFSChannels();

  // Get the latest scan results from kernel.
  NativeScanResult[] getScanResults();

  // Request a single scan using a SingleScanSettings parcelable object.
  boolean scan(in SingleScanSettings scanSettings);

  // Subscribe single scanning events.
  // Scanner assumes there is only one subscriber.
  // This call will replace any existing |handler|.
  oneway void subscribeScanEvents(IScanEvent handler);

  // Unsubscribe single scanning events .
  oneway void unsubscribeScanEvents();

  // Subscribe Pno scanning events.
  // Scanner assumes there is only one subscriber.
  // This call will replace any existing |handler|.
  oneway void subscribePnoScanEvents(IPnoScanEvent handler);

  // Unsubscribe Pno scanning events .
  oneway void unsubscribePnoScanEvents();

  // Request a scheduled scan.
  boolean startPnoScan(in PnoSettings pnoSettings);

  // Stop any existing scheduled scan.
  // Returns true on success.
  // Returns false on failure or there is no existing scheduled scan.
  boolean stopPnoScan();

  // Abort ongoing scan.
  void abortScan();

  // TODO(nywang) add more interfaces.
}
