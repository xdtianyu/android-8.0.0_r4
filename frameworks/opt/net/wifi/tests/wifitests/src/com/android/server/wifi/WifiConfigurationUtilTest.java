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

package com.android.server.wifi;

import static org.junit.Assert.*;

import android.content.pm.UserInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiEnterpriseConfig;
import android.net.wifi.WifiScanner;
import android.os.UserHandle;
import android.test.suitebuilder.annotation.SmallTest;

import org.junit.Test;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Unit tests for {@link com.android.server.wifi.WifiConfigurationUtil}.
 */
@SmallTest
public class WifiConfigurationUtilTest {
    static final int CURRENT_USER_ID = 0;
    static final int CURRENT_USER_MANAGED_PROFILE_USER_ID = 10;
    static final int OTHER_USER_ID = 11;
    static final String TEST_SSID = "test_ssid";
    static final String TEST_SSID_1 = "test_ssid_1";
    static final List<UserInfo> PROFILES = Arrays.asList(
            new UserInfo(CURRENT_USER_ID, "owner", 0),
            new UserInfo(CURRENT_USER_MANAGED_PROFILE_USER_ID, "managed profile", 0));

    /**
     * Test for {@link WifiConfigurationUtil.isVisibleToAnyProfile}.
     */
    @Test
    public void isVisibleToAnyProfile() {
        // Shared network configuration created by another user.
        final WifiConfiguration configuration = new WifiConfiguration();
        configuration.creatorUid = UserHandle.getUid(OTHER_USER_ID, 0);
        assertTrue(WifiConfigurationUtil.isVisibleToAnyProfile(configuration, PROFILES));

        // Private network configuration created by another user.
        configuration.shared = false;
        assertFalse(WifiConfigurationUtil.isVisibleToAnyProfile(configuration, PROFILES));

        // Private network configuration created by the current user.
        configuration.creatorUid = UserHandle.getUid(CURRENT_USER_ID, 0);
        assertTrue(WifiConfigurationUtil.isVisibleToAnyProfile(configuration, PROFILES));

        // Private network configuration created by the current user's managed profile.
        configuration.creatorUid = UserHandle.getUid(CURRENT_USER_MANAGED_PROFILE_USER_ID, 0);
        assertTrue(WifiConfigurationUtil.isVisibleToAnyProfile(configuration, PROFILES));
    }

    /**
     * Verify that new WifiEnterpriseConfig is detected.
     */
    @Test
    public void testEnterpriseConfigAdded() {
        EnterpriseConfig eapConfig = new EnterpriseConfig(WifiEnterpriseConfig.Eap.TTLS)
                .setPhase2(WifiEnterpriseConfig.Phase2.MSCHAPV2)
                .setIdentity("username", "password")
                .setCaCerts(new X509Certificate[]{FakeKeys.CA_CERT0});

        assertTrue(WifiConfigurationUtil.hasEnterpriseConfigChanged(
                null, eapConfig.enterpriseConfig));
    }

    /**
     * Verify WifiEnterpriseConfig eap change is detected.
     */
    @Test
    public void testEnterpriseConfigEapChangeDetected() {
        EnterpriseConfig eapConfig = new EnterpriseConfig(WifiEnterpriseConfig.Eap.TTLS);
        EnterpriseConfig peapConfig = new EnterpriseConfig(WifiEnterpriseConfig.Eap.PEAP);

        assertTrue(WifiConfigurationUtil.hasEnterpriseConfigChanged(eapConfig.enterpriseConfig,
                peapConfig.enterpriseConfig));
    }

    /**
     * Verify WifiEnterpriseConfig phase2 method change is detected.
     */
    @Test
    public void testEnterpriseConfigPhase2ChangeDetected() {
        EnterpriseConfig eapConfig =
                new EnterpriseConfig(WifiEnterpriseConfig.Eap.TTLS)
                        .setPhase2(WifiEnterpriseConfig.Phase2.MSCHAPV2);
        EnterpriseConfig papConfig =
                new EnterpriseConfig(WifiEnterpriseConfig.Eap.TTLS)
                        .setPhase2(WifiEnterpriseConfig.Phase2.PAP);

        assertTrue(WifiConfigurationUtil.hasEnterpriseConfigChanged(eapConfig.enterpriseConfig,
                papConfig.enterpriseConfig));
    }

    /**
     * Verify WifiEnterpriseConfig added Certificate is detected.
     */
    @Test
    public void testCaCertificateAddedDetected() {
        EnterpriseConfig eapConfigNoCerts = new EnterpriseConfig(WifiEnterpriseConfig.Eap.TTLS)
                .setPhase2(WifiEnterpriseConfig.Phase2.MSCHAPV2)
                .setIdentity("username", "password");

        EnterpriseConfig eapConfig1Cert = new EnterpriseConfig(WifiEnterpriseConfig.Eap.TTLS)
                .setPhase2(WifiEnterpriseConfig.Phase2.MSCHAPV2)
                .setIdentity("username", "password")
                .setCaCerts(new X509Certificate[]{FakeKeys.CA_CERT0});

        assertTrue(WifiConfigurationUtil.hasEnterpriseConfigChanged(
                eapConfigNoCerts.enterpriseConfig, eapConfig1Cert.enterpriseConfig));
    }

    /**
     * Verify WifiEnterpriseConfig Certificate change is detected.
     */
    @Test
    public void testDifferentCaCertificateDetected() {
        EnterpriseConfig eapConfig = new EnterpriseConfig(WifiEnterpriseConfig.Eap.TTLS)
                .setPhase2(WifiEnterpriseConfig.Phase2.MSCHAPV2)
                .setIdentity("username", "password")
                .setCaCerts(new X509Certificate[]{FakeKeys.CA_CERT0});

        EnterpriseConfig eapConfigNewCert = new EnterpriseConfig(WifiEnterpriseConfig.Eap.TTLS)
                .setPhase2(WifiEnterpriseConfig.Phase2.MSCHAPV2)
                .setIdentity("username", "password")
                .setCaCerts(new X509Certificate[]{FakeKeys.CA_CERT1});

        assertTrue(WifiConfigurationUtil.hasEnterpriseConfigChanged(eapConfig.enterpriseConfig,
                eapConfigNewCert.enterpriseConfig));
    }

    /**
     * Verify WifiEnterpriseConfig added Certificate changes are detected.
     */
    @Test
    public void testCaCertificateChangesDetected() {
        EnterpriseConfig eapConfig = new EnterpriseConfig(WifiEnterpriseConfig.Eap.TTLS)
                .setPhase2(WifiEnterpriseConfig.Phase2.MSCHAPV2)
                .setIdentity("username", "password")
                .setCaCerts(new X509Certificate[]{FakeKeys.CA_CERT0});

        EnterpriseConfig eapConfigAddedCert = new EnterpriseConfig(WifiEnterpriseConfig.Eap.TTLS)
                .setPhase2(WifiEnterpriseConfig.Phase2.MSCHAPV2)
                .setIdentity("username", "password")
                .setCaCerts(new X509Certificate[]{FakeKeys.CA_CERT0, FakeKeys.CA_CERT1});

        assertTrue(WifiConfigurationUtil.hasEnterpriseConfigChanged(eapConfig.enterpriseConfig,
                eapConfigAddedCert.enterpriseConfig));
    }

    /**
     * Verify that WifiEnterpriseConfig does not detect changes for identical configs.
     */
    @Test
    public void testWifiEnterpriseConfigNoChanges() {
        EnterpriseConfig eapConfig = new EnterpriseConfig(WifiEnterpriseConfig.Eap.TTLS)
                .setPhase2(WifiEnterpriseConfig.Phase2.MSCHAPV2)
                .setIdentity("username", "password")
                .setCaCerts(new X509Certificate[]{FakeKeys.CA_CERT0, FakeKeys.CA_CERT1});

        // Just to be clear that check is not against the same object
        EnterpriseConfig eapConfigSame = new EnterpriseConfig(WifiEnterpriseConfig.Eap.TTLS)
                .setPhase2(WifiEnterpriseConfig.Phase2.MSCHAPV2)
                .setIdentity("username", "password")
                .setCaCerts(new X509Certificate[]{FakeKeys.CA_CERT0, FakeKeys.CA_CERT1});

        assertFalse(WifiConfigurationUtil.hasEnterpriseConfigChanged(eapConfig.enterpriseConfig,
                eapConfigSame.enterpriseConfig));
    }

    /**
     * Verify the instance of {@link android.net.wifi.WifiScanner.PnoSettings.PnoNetwork} created
     * for an open network using {@link WifiConfigurationUtil#createPnoNetwork(
     * WifiConfiguration, int)}.
     */
    @Test
    public void testCreatePnoNetworkWithOpenNetwork() {
        WifiConfiguration network = WifiConfigurationTestUtil.createOpenNetwork();
        WifiScanner.PnoSettings.PnoNetwork pnoNetwork =
                WifiConfigurationUtil.createPnoNetwork(network, 1);
        assertEquals(network.SSID, pnoNetwork.ssid);
        assertEquals(
                WifiScanner.PnoSettings.PnoNetwork.FLAG_A_BAND
                        | WifiScanner.PnoSettings.PnoNetwork.FLAG_G_BAND, pnoNetwork.flags);
        assertEquals(WifiScanner.PnoSettings.PnoNetwork.AUTH_CODE_OPEN, pnoNetwork.authBitField);
    }

    /**
     * Verify the instance of {@link android.net.wifi.WifiScanner.PnoSettings.PnoNetwork} created
     * for an open hidden network using {@link WifiConfigurationUtil#createPnoNetwork(
     * WifiConfiguration, int)}.
     */
    @Test
    public void testCreatePnoNetworkWithOpenHiddenNetwork() {
        WifiConfiguration network = WifiConfigurationTestUtil.createOpenHiddenNetwork();
        WifiScanner.PnoSettings.PnoNetwork pnoNetwork =
                WifiConfigurationUtil.createPnoNetwork(network, 1);
        assertEquals(network.SSID, pnoNetwork.ssid);
        assertEquals(
                WifiScanner.PnoSettings.PnoNetwork.FLAG_A_BAND
                        | WifiScanner.PnoSettings.PnoNetwork.FLAG_G_BAND
                        | WifiScanner.PnoSettings.PnoNetwork.FLAG_DIRECTED_SCAN, pnoNetwork.flags);
        assertEquals(WifiScanner.PnoSettings.PnoNetwork.AUTH_CODE_OPEN, pnoNetwork.authBitField);
    }

    /**
     * Verify the instance of {@link android.net.wifi.WifiScanner.PnoSettings.PnoNetwork} created
     * for a PSK network using {@link WifiConfigurationUtil#createPnoNetwork(WifiConfiguration, int)
     * }.
     */
    @Test
    public void testCreatePnoNetworkWithPskNetwork() {
        WifiConfiguration network = WifiConfigurationTestUtil.createPskNetwork();
        WifiScanner.PnoSettings.PnoNetwork pnoNetwork =
                WifiConfigurationUtil.createPnoNetwork(network, 1);
        assertEquals(network.SSID, pnoNetwork.ssid);
        assertEquals(
                WifiScanner.PnoSettings.PnoNetwork.FLAG_A_BAND
                        | WifiScanner.PnoSettings.PnoNetwork.FLAG_G_BAND, pnoNetwork.flags);
        assertEquals(WifiScanner.PnoSettings.PnoNetwork.AUTH_CODE_PSK, pnoNetwork.authBitField);
    }

    /**
     * Verify that WifiConfigurationUtil.isSameNetwork returns true when two WifiConfiguration
     * objects have the same parameters.
     */
    @Test
    public void testIsSameNetworkReturnsTrueOnSameNetwork() {
        WifiConfiguration network = WifiConfigurationTestUtil.createPskNetwork(TEST_SSID);
        WifiConfiguration network1 = WifiConfigurationTestUtil.createPskNetwork(TEST_SSID);
        assertTrue(WifiConfigurationUtil.isSameNetwork(network, network1));
    }

    /**
     * Verify that WifiConfigurationUtil.isSameNetwork returns false when two WifiConfiguration
     * objects have the different SSIDs.
     */
    @Test
    public void testIsSameNetworkReturnsFalseOnDifferentSSID() {
        WifiConfiguration network = WifiConfigurationTestUtil.createPskNetwork(TEST_SSID);
        WifiConfiguration network1 = WifiConfigurationTestUtil.createPskNetwork(TEST_SSID_1);
        assertFalse(WifiConfigurationUtil.isSameNetwork(network, network1));
    }

    /**
     * Verify that WifiConfigurationUtil.isSameNetwork returns false when two WifiConfiguration
     * objects have the different security type.
     */
    @Test
    public void testIsSameNetworkReturnsFalseOnDifferentSecurityType() {
        WifiConfiguration network = WifiConfigurationTestUtil.createPskNetwork(TEST_SSID);
        WifiConfiguration network1 = WifiConfigurationTestUtil.createEapNetwork(TEST_SSID);
        assertFalse(WifiConfigurationUtil.isSameNetwork(network, network1));
    }


    /**
     * Verify the instance of {@link android.net.wifi.WifiScanner.PnoSettings.PnoNetwork} created
     * for a EAP network using {@link WifiConfigurationUtil#createPnoNetwork(WifiConfiguration, int)
     * }.
     */
    @Test
    public void testCreatePnoNetworkWithEapNetwork() {
        WifiConfiguration network = WifiConfigurationTestUtil.createEapNetwork();
        WifiScanner.PnoSettings.PnoNetwork pnoNetwork =
                WifiConfigurationUtil.createPnoNetwork(network, 1);
        assertEquals(network.SSID, pnoNetwork.ssid);
        assertEquals(
                WifiScanner.PnoSettings.PnoNetwork.FLAG_A_BAND
                        | WifiScanner.PnoSettings.PnoNetwork.FLAG_G_BAND, pnoNetwork.flags);
        assertEquals(WifiScanner.PnoSettings.PnoNetwork.AUTH_CODE_EAPOL, pnoNetwork.authBitField);
    }

    /**
     * Verify that the generalized
     * {@link com.android.server.wifi.WifiConfigurationUtil.WifiConfigurationComparator}
     * can be used to sort a List given a 'compareNetworkWithSameStatus' method.
     */
    @Test
    public void testPnoListComparator() {
        List<WifiConfiguration> networks = new ArrayList<>();
        final WifiConfiguration enabledNetwork1 = WifiConfigurationTestUtil.createEapNetwork();
        enabledNetwork1.getNetworkSelectionStatus().setNetworkSelectionStatus(
                WifiConfiguration.NetworkSelectionStatus.NETWORK_SELECTION_ENABLED);
        final WifiConfiguration enabledNetwork2 = WifiConfigurationTestUtil.createEapNetwork();
        enabledNetwork2.getNetworkSelectionStatus().setNetworkSelectionStatus(
                WifiConfiguration.NetworkSelectionStatus.NETWORK_SELECTION_ENABLED);
        final WifiConfiguration tempDisabledNetwork1 = WifiConfigurationTestUtil.createEapNetwork();
        tempDisabledNetwork1.getNetworkSelectionStatus().setNetworkSelectionStatus(
                WifiConfiguration.NetworkSelectionStatus.NETWORK_SELECTION_TEMPORARY_DISABLED);
        final WifiConfiguration tempDisabledNetwork2 = WifiConfigurationTestUtil.createEapNetwork();
        tempDisabledNetwork2.getNetworkSelectionStatus().setNetworkSelectionStatus(
                WifiConfiguration.NetworkSelectionStatus.NETWORK_SELECTION_TEMPORARY_DISABLED);
        WifiConfiguration permDisabledNetwork = WifiConfigurationTestUtil.createEapNetwork();
        permDisabledNetwork.getNetworkSelectionStatus().setNetworkSelectionStatus(
                WifiConfiguration.NetworkSelectionStatus.NETWORK_SELECTION_PERMANENTLY_DISABLED);

        // Add all the networks to the list.
        networks.add(tempDisabledNetwork1);
        networks.add(enabledNetwork1);
        networks.add(permDisabledNetwork);
        networks.add(tempDisabledNetwork2);
        networks.add(enabledNetwork2);

        // Prefer |enabledNetwork1| over |enabledNetwork2| and |tempDisabledNetwork1| over
        // |tempDisabledNetwork2|.
        WifiConfigurationUtil.WifiConfigurationComparator comparator =
                new WifiConfigurationUtil.WifiConfigurationComparator() {
                    @Override
                    public int compareNetworksWithSameStatus(
                            WifiConfiguration a, WifiConfiguration b) {
                        if (a == enabledNetwork1 && b == enabledNetwork2) {
                            return -1;
                        } else if (b == enabledNetwork1 && a == enabledNetwork2) {
                            return 1;
                        } else if (a == tempDisabledNetwork1 && b == tempDisabledNetwork2) {
                            return -1;
                        } else if (b == tempDisabledNetwork1 && a == tempDisabledNetwork2) {
                            return 1;
                        }
                        return 0;
                    }
                };
        Collections.sort(networks, comparator);

        // Now ensure that the networks were sorted correctly.
        assertEquals(enabledNetwork1, networks.get(0));
        assertEquals(enabledNetwork2, networks.get(1));
        assertEquals(tempDisabledNetwork1, networks.get(2));
        assertEquals(tempDisabledNetwork2, networks.get(3));
        assertEquals(permDisabledNetwork, networks.get(4));
    }

    private static class EnterpriseConfig {
        public String eap;
        public String phase2;
        public String identity;
        public String password;
        public X509Certificate[] caCerts;
        public WifiEnterpriseConfig enterpriseConfig;

        EnterpriseConfig(int eapMethod) {
            enterpriseConfig = new WifiEnterpriseConfig();
            enterpriseConfig.setEapMethod(eapMethod);
            eap = WifiEnterpriseConfig.Eap.strings[eapMethod];
        }

        public EnterpriseConfig setPhase2(int phase2Method) {
            enterpriseConfig.setPhase2Method(phase2Method);
            phase2 = "auth=" + WifiEnterpriseConfig.Phase2.strings[phase2Method];
            return this;
        }

        public EnterpriseConfig setIdentity(String identity, String password) {
            enterpriseConfig.setIdentity(identity);
            enterpriseConfig.setPassword(password);
            this.identity = identity;
            this.password = password;
            return this;
        }

        public EnterpriseConfig setCaCerts(X509Certificate[] certs) {
            enterpriseConfig.setCaCertificates(certs);
            caCerts = certs;
            return this;
        }
    }
}
