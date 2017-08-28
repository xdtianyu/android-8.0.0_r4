/*
 * Copyright (C) 2016 The Android Open Source Project
 * Copyright (C) 2016 Mopria Alliance, Inc.
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

package com.android.bips.discovery;

import android.content.Context;
import android.net.Uri;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;

import com.android.bips.BuiltInPrintService;

import java.net.Inet4Address;
import java.util.Locale;
import java.util.Map;

/**
 * Search the local network for devices advertising IPP print services
 */
public class MdnsDiscovery extends Discovery {
    private static final String TAG = MdnsDiscovery.class.getSimpleName();
    private static final boolean DEBUG = false;

    // Prepend this to a UUID to create a proper URN
    private static final String PREFIX_URN_UUID = "urn:uuid:";

    // Keys for expected txtRecord attributes
    private static final String ATTRIBUTE_RP = "rp";
    private static final String ATTRIBUTE_UUID = "UUID";
    private static final String ATTRIBUTE_NOTE = "note";
    private static final String ATTRIBUTE_PRINT_WFDS = "print_wfds";
    private static final String VALUE_PRINT_WFDS_OPT_OUT = "F";

    // Service name of interest
    private static final String SERVICE_IPP = "_ipp._tcp";

    /** Network Service Discovery Manager */
    private final NsdManager mNsdManager;

    /** Handler used for posting to main thread */
    private final Handler mMainHandler;

    /** Handle to listener when registered */
    private NsdServiceListener mServiceListener;

    public MdnsDiscovery(BuiltInPrintService printService) {
        this(printService, (NsdManager) printService.getSystemService(Context.NSD_SERVICE));
    }

    /** Constructor for use by test */
    MdnsDiscovery(BuiltInPrintService printService, NsdManager nsdManager) {
        super(printService);
        mNsdManager = nsdManager;
        mMainHandler = new Handler(printService.getMainLooper());
    }

    /** Return a valid {@link DiscoveredPrinter} from {@link NsdServiceInfo}, or null if invalid */
    private static DiscoveredPrinter toNetworkPrinter(NsdServiceInfo info) {
        // Honor printers that deliberately opt-out
        if (VALUE_PRINT_WFDS_OPT_OUT.equals(getStringAttribute(info, ATTRIBUTE_PRINT_WFDS))) {
            if (DEBUG) Log.d(TAG, "Opted out: " + info);
            return null;
        }

        // Collect resource path
        String resourcePath = getStringAttribute(info, ATTRIBUTE_RP);
        if (TextUtils.isEmpty(resourcePath)) {
            if (DEBUG) Log.d(TAG, "Missing RP" + info);
            return null;
        }
        if (resourcePath.startsWith("/")) {
            resourcePath = resourcePath.substring(1);
        }

        // Hopefully has a UUID
        Uri uuidUri = null;
        String uuid = getStringAttribute(info, ATTRIBUTE_UUID);
        if (!TextUtils.isEmpty(uuid)) {
            uuidUri = Uri.parse(PREFIX_URN_UUID + uuid);
        }

        // Must be IPv4
        if (!(info.getHost() instanceof Inet4Address)) {
            if (DEBUG) Log.d(TAG, "Not IPv4" + info);
            return null;
        }

        Uri path = Uri.parse("ipp://" + info.getHost().getHostAddress() +
                ":" + info.getPort() + "/" + resourcePath);
        String location = getStringAttribute(info, ATTRIBUTE_NOTE);

        return new DiscoveredPrinter(uuidUri, info.getServiceName(), path, location);
    }

    /** Return the value of an attribute or null if not present */
    private static String getStringAttribute(NsdServiceInfo info, String key) {
        key = key.toLowerCase(Locale.US);
        for (Map.Entry<String, byte[]> entry : info.getAttributes().entrySet()) {
            if (entry.getKey().toLowerCase(Locale.US).equals(key) && entry.getValue() != null) {
                return new String(entry.getValue());
            }
        }
        return null;
    }

    @Override
    void onStart() {
        if (DEBUG) Log.d(TAG, "onStart()");
        mServiceListener = new NsdServiceListener();
        mNsdManager.discoverServices(SERVICE_IPP, NsdManager.PROTOCOL_DNS_SD, mServiceListener);
    }

    @Override
    void onStop() {
        if (DEBUG) Log.d(TAG, "onStop()");

        if (mServiceListener != null) {
            mNsdManager.stopServiceDiscovery(mServiceListener);
            mServiceListener = null;
        }
        mMainHandler.removeCallbacksAndMessages(null);
        NsdResolveQueue.getInstance(getPrintService()).clear();
    }

    /**
     * Manage notifications from NsdManager
     */
    private class NsdServiceListener implements NsdManager.DiscoveryListener,
            NsdManager.ResolveListener {
        @Override
        public void onStartDiscoveryFailed(String serviceType, int errorCode) {
            Log.w(TAG, "onStartDiscoveryFailed: " + errorCode);
            mServiceListener = null;
        }

        @Override
        public void onStopDiscoveryFailed(String s, int errorCode) {
            Log.w(TAG, "onStopDiscoveryFailed: " + errorCode);
        }

        @Override
        public void onDiscoveryStarted(String s) {
            if (DEBUG) Log.d(TAG, "onDiscoveryStarted");
        }

        @Override
        public void onDiscoveryStopped(String s) {
            if (DEBUG) Log.d(TAG, "onDiscoveryStopped");

            // On the main thread, notify loss of all known printers
            mMainHandler.post(() -> allPrintersLost());
        }

        @Override
        public void onServiceFound(final NsdServiceInfo info) {
            if (DEBUG) Log.d(TAG, "onServiceFound - " + info.getServiceName());
            NsdResolveQueue.getInstance(getPrintService()).resolve(mNsdManager, info, this);
        }

        @Override
        public void onServiceLost(final NsdServiceInfo info) {
            if (DEBUG) Log.d(TAG, "onServiceLost - " + info.getServiceName());

            // On the main thread, seek the missing printer by name and notify its loss
            mMainHandler.post(() -> {
                for (DiscoveredPrinter printer : getPrinters()) {
                    if (TextUtils.equals(printer.name, info.getServiceName())) {
                        printerLost(printer.getUri());
                        return;
                    }
                }
            });
        }

        @Override
        public void onResolveFailed(final NsdServiceInfo info, final int errorCode) {
        }

        @Override
        public void onServiceResolved(final NsdServiceInfo info) {
            final DiscoveredPrinter printer = toNetworkPrinter(info);
            if (DEBUG) Log.d(TAG, "Service " + info.getServiceName() + " resolved to " + printer);
            if (printer == null) {
                return;
            }

            mMainHandler.post(() -> printerFound(printer));
        }
    }
}