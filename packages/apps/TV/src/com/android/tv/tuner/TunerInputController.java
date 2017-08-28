/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.tv.tuner;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v4.os.BuildCompat;
import android.util.Log;
import android.widget.Toast;

import com.android.tv.Features;
import com.android.tv.TvApplication;
import com.android.tv.tuner.R;
import com.android.tv.tuner.setup.TunerSetupActivity;
import com.android.tv.tuner.tvinput.TunerTvInputService;
import com.android.tv.tuner.util.TunerInputInfoUtils;

import java.util.Map;

/**
 * Controls the package visibility of {@link TunerTvInputService}.
 * <p>
 * Listens to broadcast intent for {@link Intent#ACTION_BOOT_COMPLETED},
 * {@code UsbManager.ACTION_USB_DEVICE_ATTACHED}, and {@code UsbManager.ACTION_USB_DEVICE_ATTACHED}
 * to update the connection status of the supported USB TV tuners.
 */
public class TunerInputController extends BroadcastReceiver {
    private static final boolean DEBUG = true;
    private static final String TAG = "TunerInputController";

    private static final TunerDevice[] TUNER_DEVICES = {
        new TunerDevice(0x2040, 0xb123),  // WinTV-HVR-955Q
        new TunerDevice(0x07ca, 0x0837)   // AverTV Volar Hybrid Q
    };

    private static final int MSG_ENABLE_INPUT_SERVICE = 1000;
    private static final long DVB_DRIVER_CHECK_DELAY_MS = 300;

    private DvbDeviceAccessor mDvbDeviceAccessor;
    private final Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_ENABLE_INPUT_SERVICE:
                    Context context = (Context) msg.obj;
                    if (mDvbDeviceAccessor == null) {
                        mDvbDeviceAccessor = new DvbDeviceAccessor(context);
                    }
                    enableTunerTvInputService(context, mDvbDeviceAccessor.isDvbDeviceAvailable());
                    break;
            }
        }
    };

    /**
     * Simple data holder for a USB device. Used to represent a tuner model, and compare
     * against {@link UsbDevice}.
     */
    private static class TunerDevice {
        private final int vendorId;
        private final int productId;

        private TunerDevice(int vendorId, int productId) {
            this.vendorId = vendorId;
            this.productId = productId;
        }

        private boolean equals(UsbDevice device) {
            return device.getVendorId() == vendorId && device.getProductId() == productId;
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (DEBUG) Log.d(TAG, "Broadcast intent received:" + intent);
        TvApplication.setCurrentRunningProcess(context, true);
        if (!Features.TUNER.isEnabled(context)) {
            enableTunerTvInputService(context, false);
            return;
        }

        switch (intent.getAction()) {
            case Intent.ACTION_BOOT_COMPLETED:
            case TvApplication.ACTION_APPLICATION_FIRST_LAUNCHED:
            case UsbManager.ACTION_USB_DEVICE_ATTACHED:
            case UsbManager.ACTION_USB_DEVICE_DETACHED:
                if (TunerInputInfoUtils.isBuiltInTuner(context)) {
                    enableTunerTvInputService(context, true);
                    break;
                }
                // Falls back to the below to check USB tuner devices.
                boolean enabled = isUsbTunerConnected(context);
                mHandler.removeMessages(MSG_ENABLE_INPUT_SERVICE);
                if (enabled) {
                    // Need to check if DVB driver is accessible. Since the driver creation
                    // could be happen after the USB event, delay the checking by
                    // DVB_DRIVER_CHECK_DELAY_MS.
                    mHandler.sendMessageDelayed(
                            mHandler.obtainMessage(MSG_ENABLE_INPUT_SERVICE, context),
                            DVB_DRIVER_CHECK_DELAY_MS);
                } else {
                    enableTunerTvInputService(context, false);
                }
                break;
        }
    }

    /**
     * See if any USB tuner hardware is attached in the system.
     *
     * @param context {@link Context} instance
     * @return {@code true} if any tuner device we support is plugged in
     */
    private boolean isUsbTunerConnected(Context context) {
        UsbManager manager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        Map<String, UsbDevice> deviceList = manager.getDeviceList();
        for (UsbDevice device : deviceList.values()) {
            if (DEBUG) {
                Log.d(TAG, "Device: " + device);
            }
            for (TunerDevice tuner : TUNER_DEVICES) {
                if (tuner.equals(device)) {
                    Log.i(TAG, "Tuner found");
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Enable/disable the component {@link TunerTvInputService}.
     *
     * @param context {@link Context} instance
     * @param enabled {@code true} to enable the service; otherwise {@code false}
     */
    private void enableTunerTvInputService(Context context, boolean enabled) {
        if (DEBUG) Log.d(TAG, "enableTunerTvInputService: " + enabled);
        PackageManager pm  = context.getPackageManager();
        ComponentName componentName = new ComponentName(context, TunerTvInputService.class);

        // Don't kill app by enabling/disabling TvActivity. If LC is killed by enabling/disabling
        // TvActivity, the following pm.setComponentEnabledSetting doesn't work.
        ((TvApplication) context.getApplicationContext()).handleInputCountChanged(
                true, enabled, true);
        // Since PackageManager.DONT_KILL_APP delays the operation by 10 seconds
        // (PackageManagerService.BROADCAST_DELAY), we'd better avoid using it. It is used only
        // when the LiveChannels app is active since we don't want to kill the running app.
        int flags = TvApplication.getSingletons(context).getMainActivityWrapper().isCreated()
                ? PackageManager.DONT_KILL_APP : 0;
        int newState = enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        if (newState != pm.getComponentEnabledSetting(componentName)) {
            // Send/cancel the USB tuner TV input setup recommendation card.
            TunerSetupActivity.onTvInputEnabled(context, enabled);
            // Enable/disable the USB tuner TV input.
            pm.setComponentEnabledSetting(componentName, newState, flags);
            if (!enabled) {
                Toast.makeText(
                        context, R.string.msg_usb_device_detached, Toast.LENGTH_SHORT).show();
            }
            if (DEBUG) Log.d(TAG, "Status updated:" + enabled);
        } else if (enabled) {
            // When # of USB tuners is changed or the device just boots.
            TunerInputInfoUtils.updateTunerInputInfo(context);
        }
    }
}
