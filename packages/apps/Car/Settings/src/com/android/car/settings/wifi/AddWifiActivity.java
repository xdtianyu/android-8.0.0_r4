/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.car.settings.wifi;

import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewSwitcher;

import com.android.car.settings.common.CarSettingActivity;
import com.android.car.settings.R;
import com.android.settingslib.wifi.AccessPoint;

import java.util.regex.Pattern;

/**
 * Adds a wifi network, the network can be public or private. If ADD_NETWORK_MODE is not specified
 * in the intent, then it needs to contain AccessPoint information, which is be use that to
 * render UI, e.g. show SSID etc.
 */
public class AddWifiActivity extends CarSettingActivity {
    public static final String ADD_NETWORK_MODE = "addNetworkMode";
    private static final String TAG = "AddWifiActivity";
    private static final Pattern HEX_PATTERN = Pattern.compile("^[0-9A-F]+$");
    private AccessPoint mAccessPoint;
    private WifiManager mWifiManager;
    private final WifiManager.ActionListener mConnectionListener = new WifiManager.ActionListener() {
        @Override
        public void onSuccess() {
        }

        @Override
        public void onFailure(int reason) {
            Toast.makeText(AddWifiActivity.this,
                    R.string.wifi_failed_connect_message,
                    Toast.LENGTH_SHORT).show();
        }
    };
    // Switch between display a ssid and entering ssid.
    private ViewSwitcher mSSidViewSwitcher;
    private TextView mWifiNameDisplay;
    private EditText mWifiNameInput;
    private boolean mAddNetworkMode;
    private EditText mWifiPasswordInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mWifiManager = (WifiManager) getSystemService(WifiManager.class);

        setContentView(R.layout.add_wifi);
        mSSidViewSwitcher = (ViewSwitcher) findViewById(R.id.wifi_name);
        mWifiNameDisplay = (TextView) findViewById(R.id.wifi_name_display);
        mWifiNameInput = (EditText) findViewById(R.id.wifi_name_input);
        mWifiPasswordInput = (EditText) findViewById(R.id.wifi_password);
        Button addWifiButton = (Button) findViewById(R.id.wifi_connect);
        bootstrap();

        addWifiButton.setOnClickListener(v -> {
                WifiConfiguration wifiConfig = new WifiConfiguration();
                wifiConfig.SSID = String.format("\"%s\"", getSsId());
                wifiConfig.preSharedKey = String.format(
                        "\"%s\"", mWifiPasswordInput.getText().toString());

                int netId = mWifiManager.addNetwork(wifiConfig);
                mWifiManager.disconnect();
                mWifiManager.enableNetwork(netId, true);
                mWifiManager.reconnect();
                finish();
            });
    }

    private void connectToAccessPoint() {
        WifiConfiguration wifiConfig = new WifiConfiguration();
        wifiConfig.SSID = String.format("\"%s\"", getSsId());
        wifiConfig.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
        wifiConfig.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
        wifiConfig.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
        wifiConfig.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
        wifiConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
        wifiConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104);
        switch (mAccessPoint.getSecurity()) {
            case AccessPoint.SECURITY_NONE:
                wifiConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                wifiConfig.allowedAuthAlgorithms.clear();
                wifiConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
                wifiConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
            case AccessPoint.SECURITY_WEP:
                wifiConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                wifiConfig.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
                wifiConfig.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.SHARED);
                String password = mWifiPasswordInput.getText().toString();
                wifiConfig.wepKeys[0] = isHexString(password) ? password : "\"" + password + "\"";
                wifiConfig.wepTxKeyIndex = 0;
            case AccessPoint.SECURITY_PSK:
            case AccessPoint.SECURITY_EAP:
                wifiConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
                wifiConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
                wifiConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
                wifiConfig.preSharedKey = String.format(
                        "\"%s\"", mWifiPasswordInput.getText().toString());
        }
        int netId = mWifiManager.addNetwork(wifiConfig);
        if (netId != -1) {
            mWifiManager.enableNetwork(netId, true);
        }
    }

    private boolean isHexString(String password) {
        return HEX_PATTERN.matcher(password).matches();
    }

    // TODO: handle null case, show warning message etc.
    private String getSsId() {
        if (mAddNetworkMode) {
            return mWifiNameInput.getText().toString();
        } else {
            return mAccessPoint.getSsid().toString();
        }
    }

    /**
     * Sets up fields based on Intent content, and setup UI accordingly.
     */
    private void bootstrap() {
        Bundle bundle = getIntent().getExtras();
        if (bundle == null) {
            return;
        }
        if (bundle.containsKey(ADD_NETWORK_MODE)) {
            mAddNetworkMode = true;
        } else {
            mAccessPoint = new AccessPoint(this, bundle);
        }
        if (mAddNetworkMode && mSSidViewSwitcher.getCurrentView() == mWifiNameDisplay) {
            mSSidViewSwitcher.showNext();
        }
        if (!mAddNetworkMode) {
            mWifiNameDisplay.setText(mAccessPoint.getSsid());
        }
    }
}
