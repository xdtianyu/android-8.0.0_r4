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

// IApInterface represents a network interface configured to act as a
// WiFi access point.
interface IApInterface {

  const int ENCRYPTION_TYPE_NONE = 0;
  const int ENCRYPTION_TYPE_WPA = 1;
  const int ENCRYPTION_TYPE_WPA2 = 2;

  // Start up an instance of hostapd associated with this interface.
  // @return true on success.
  boolean startHostapd();

  // Stop a previously started instance of hostapd.
  // @return true on success.
  boolean stopHostapd();

  // Write out a configuration file for hostapd.  This will be used on the next
  // successful call to StartHostapd().  Returns true on success.
  //
  // @param ssid string of <=32 bytes to use as the SSID for this AP.
  // @param isHidden True iff the AP should not broadcast its SSID.
  // @param channel WiFi channel to expose the AP on.
  // @param encryptionType one of ENCRYPTION_TYPE* above.
  // @param passphrase string of bytes to use as the passphrase for this AP.
  //        Ignored if encryptionType is None.
  // @return true on success.
  boolean writeHostapdConfig(in byte[] ssid, boolean isHidden, int channel,
                             int encryptionType, in byte[] passphrase);

  // Retrieve the name of the network interface corresponding to this
  // IApInterface instance (e.g. "wlan0")
  @utf8InCpp
  String getInterfaceName();

  // @return Returns the number of associated devices to this hotspot.
  // Returns -1 on failure.
  int getNumberOfAssociatedStations();

}
