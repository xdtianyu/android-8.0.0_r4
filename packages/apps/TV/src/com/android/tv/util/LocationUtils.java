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

package com.android.tv.util;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;


import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * A utility class to get the current location.
 */
public class LocationUtils {
    private static final String TAG = "LocationUtils";
    private static final boolean DEBUG = false;

    private static Context sApplicationContext;
    private static Address sAddress;
    private static IOException sError;

    /**
     * Checks the current location.
     */
    public static synchronized Address getCurrentAddress(Context context) throws IOException,
            SecurityException {
        if (sAddress != null) {
            return sAddress;
        }
        if (sError != null) {
            throw sError;
        }
        if (sApplicationContext == null) {
            sApplicationContext = context.getApplicationContext();
        }
        LocationUtilsHelper.startLocationUpdates();
        return null;
    }

    private static void updateAddress(Location location) {
        if (DEBUG) Log.d(TAG, "Updating address with " + location);
        if (location == null) {
            return;
        }
        Geocoder geocoder = new Geocoder(sApplicationContext, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(
                    location.getLatitude(), location.getLongitude(), 1);
            if (addresses != null) {
                sAddress = addresses.get(0);
                if (DEBUG) Log.d(TAG, "Got " + sAddress);
            } else {
                if (DEBUG) Log.d(TAG, "No address returned");
            }
            sError = null;
        } catch (IOException e) {
            Log.w(TAG, "Error in updating address", e);
            sError = e;
        }
    }

    private LocationUtils() { }

    private static class LocationUtilsHelper {
        private static final LocationListener LOCATION_LISTENER = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                updateAddress(location);
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) { }

            @Override
            public void onProviderEnabled(String provider) { }

            @Override
            public void onProviderDisabled(String provider) { }
        };

        private static LocationManager sLocationManager;

        public static void startLocationUpdates() {
            if (sLocationManager == null) {
                sLocationManager = (LocationManager) sApplicationContext.getSystemService(
                        Context.LOCATION_SERVICE);
                try {
                    sLocationManager.requestLocationUpdates(
                            LocationManager.NETWORK_PROVIDER, 1000, 10, LOCATION_LISTENER, null);
                } catch (SecurityException e) {
                    // Enables requesting the location updates again.
                    sLocationManager = null;
                    throw e;
                }
            }
        }
    }
}
