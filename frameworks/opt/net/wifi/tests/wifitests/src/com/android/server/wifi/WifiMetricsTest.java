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
 * limitations under the License
 */
package com.android.server.wifi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

import android.net.NetworkAgent;
import android.net.wifi.ScanResult;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiSsid;
import android.os.Handler;
import android.os.test.TestLooper;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.Base64;


import com.android.server.wifi.hotspot2.NetworkDetail;
import com.android.server.wifi.nano.WifiMetricsProto;
import com.android.server.wifi.nano.WifiMetricsProto.StaEvent;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Unit tests for {@link com.android.server.wifi.WifiMetrics}.
 */
@SmallTest
public class WifiMetricsTest {

    WifiMetrics mWifiMetrics;
    WifiMetricsProto.WifiLog mDeserializedWifiMetrics;
    TestLooper mTestLooper;
    @Mock Clock mClock;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mDeserializedWifiMetrics = null;
        when(mClock.getElapsedSinceBootMillis()).thenReturn((long) 0);
        mTestLooper = new TestLooper();
        mWifiMetrics = new WifiMetrics(mClock, mTestLooper.getLooper());
    }

    /**
     * Test that startConnectionEvent and endConnectionEvent can be called repeatedly and out of
     * order. Only tests no exception occurs. Creates 3 ConnectionEvents.
     */
    @Test
    public void startAndEndConnectionEventSucceeds() throws Exception {
        //Start and end Connection event
        mWifiMetrics.startConnectionEvent(null, "RED",
                WifiMetricsProto.ConnectionEvent.ROAM_ENTERPRISE);
        mWifiMetrics.endConnectionEvent(
                WifiMetrics.ConnectionEvent.FAILURE_AUTHENTICATION_FAILURE,
                WifiMetricsProto.ConnectionEvent.HLF_DHCP);
        //end Connection event without starting one
        mWifiMetrics.endConnectionEvent(
                WifiMetrics.ConnectionEvent.FAILURE_AUTHENTICATION_FAILURE,
                WifiMetricsProto.ConnectionEvent.HLF_DHCP);
        //start two ConnectionEvents in a row
        mWifiMetrics.startConnectionEvent(null, "BLUE",
                WifiMetricsProto.ConnectionEvent.ROAM_ENTERPRISE);
        mWifiMetrics.startConnectionEvent(null, "GREEN",
                WifiMetricsProto.ConnectionEvent.ROAM_ENTERPRISE);
    }

    private static final long TEST_RECORD_DURATION_SEC = 12 * 60 * 60;
    private static final long TEST_RECORD_DURATION_MILLIS = TEST_RECORD_DURATION_SEC * 1000;

    /**
     * Simulate how dumpsys gets the proto from mWifiMetrics, filter the proto bytes out and
     * deserialize them into mDeserializedWifiMetrics
     */
    public void dumpProtoAndDeserialize() throws Exception {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(stream);
        String[] args = new String[0];

        when(mClock.getElapsedSinceBootMillis()).thenReturn(TEST_RECORD_DURATION_MILLIS);
        //Test proto dump, by passing in proto arg option
        args = new String[]{WifiMetrics.PROTO_DUMP_ARG};
        mWifiMetrics.dump(null, writer, args);
        writer.flush();
        Pattern pattern = Pattern.compile(
                "(?<=WifiMetrics:\\n)([\\s\\S]*)(?=EndWifiMetrics)");
        Matcher matcher = pattern.matcher(stream.toString());
        assertTrue("Proto Byte string found in WifiMetrics.dump():\n" + stream.toString(),
                matcher.find());
        String protoByteString = matcher.group(1);
        byte[] protoBytes = Base64.decode(protoByteString, Base64.DEFAULT);
        mDeserializedWifiMetrics = WifiMetricsProto.WifiLog.parseFrom(protoBytes);
    }

    /**
     * Gets the 'clean dump' proto bytes from mWifiMetrics & deserializes it into
     * mDeserializedWifiMetrics
     */
    public void cleanDumpProtoAndDeserialize() throws Exception {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(stream);
        String[] args = new String[0];

        when(mClock.getElapsedSinceBootMillis()).thenReturn(TEST_RECORD_DURATION_MILLIS);
        //Test proto dump, by passing in proto arg option
        args = new String[]{WifiMetrics.PROTO_DUMP_ARG, WifiMetrics.CLEAN_DUMP_ARG};
        mWifiMetrics.dump(null, writer, args);
        writer.flush();
        String protoByteString = stream.toString();
        byte[] protoBytes = Base64.decode(protoByteString, Base64.DEFAULT);
        mDeserializedWifiMetrics = WifiMetricsProto.WifiLog.parseFrom(protoBytes);
    }

    /** Verifies that dump() includes the expected header */
    @Test
    public void stateDumpIncludesHeader() throws Exception {
        assertStringContains(getStateDump(), "WifiMetrics");
    }

    /** Verifies that dump() includes correct alert count when there are no alerts. */
    @Test
    public void stateDumpAlertCountIsCorrectWithNoAlerts() throws Exception {
        assertStringContains(getStateDump(), "mWifiLogProto.alertReasonCounts=()");
    }

    /** Verifies that dump() includes correct alert count when there is one alert. */
    @Test
    public void stateDumpAlertCountIsCorrectWithOneAlert() throws Exception {
        mWifiMetrics.incrementAlertReasonCount(1);
        assertStringContains(getStateDump(), "mWifiLogProto.alertReasonCounts=(1,1)");
    }

    /** Verifies that dump() includes correct alert count when there are multiple alerts. */
    @Test
    public void stateDumpAlertCountIsCorrectWithMultipleAlerts() throws Exception {
        mWifiMetrics.incrementAlertReasonCount(1);
        mWifiMetrics.incrementAlertReasonCount(1);
        mWifiMetrics.incrementAlertReasonCount(16);
        assertStringContains(getStateDump(), "mWifiLogProto.alertReasonCounts=(1,2),(16,1)");
    }

    @Test
    public void testDumpProtoAndDeserialize() throws Exception {
        setAndIncrementMetrics();
        dumpProtoAndDeserialize();
        assertDeserializedMetricsCorrect();
    }

    private static final int NUM_OPEN_NETWORKS = 2;
    private static final int NUM_PERSONAL_NETWORKS = 3;
    private static final int NUM_ENTERPRISE_NETWORKS = 5;
    private static final int NUM_SAVED_NETWORKS = NUM_OPEN_NETWORKS + NUM_PERSONAL_NETWORKS
            + NUM_ENTERPRISE_NETWORKS;
    private static final int NUM_HIDDEN_NETWORKS = NUM_OPEN_NETWORKS;
    private static final int NUM_PASSPOINT_NETWORKS = NUM_ENTERPRISE_NETWORKS;
    private static final int NUM_NETWORKS_ADDED_BY_USER = 1;
    private static final int NUM_NETWORKS_ADDED_BY_APPS = NUM_SAVED_NETWORKS
            - NUM_NETWORKS_ADDED_BY_USER;
    private static final boolean TEST_VAL_IS_LOCATION_ENABLED = true;
    private static final boolean IS_SCANNING_ALWAYS_ENABLED = true;
    private static final int NUM_EMPTY_SCAN_RESULTS = 19;
    private static final int NUM_NON_EMPTY_SCAN_RESULTS = 23;
    private static final int NUM_SCAN_UNKNOWN = 1;
    private static final int NUM_SCAN_SUCCESS = 2;
    private static final int NUM_SCAN_FAILURE_INTERRUPTED = 3;
    private static final int NUM_SCAN_FAILURE_INVALID_CONFIGURATION = 5;
    private static final int NUM_WIFI_UNKNOWN_SCREEN_OFF = 3;
    private static final int NUM_WIFI_UNKNOWN_SCREEN_ON = 5;
    private static final int NUM_WIFI_ASSOCIATED_SCREEN_OFF = 7;
    private static final int NUM_WIFI_ASSOCIATED_SCREEN_ON = 11;
    private static final int NUM_CONNECTIVITY_WATCHDOG_PNO_GOOD = 11;
    private static final int NUM_CONNECTIVITY_WATCHDOG_PNO_BAD = 12;
    private static final int NUM_CONNECTIVITY_WATCHDOG_BACKGROUND_GOOD = 13;
    private static final int NUM_CONNECTIVITY_WATCHDOG_BACKGROUND_BAD = 14;
    private static final int NUM_LAST_RESORT_WATCHDOG_TRIGGERS = 1;
    private static final int NUM_LAST_RESORT_WATCHDOG_BAD_ASSOCIATION_NETWORKS_TOTAL = 2;
    private static final int NUM_LAST_RESORT_WATCHDOG_BAD_AUTHENTICATION_NETWORKS_TOTAL = 3;
    private static final int NUM_LAST_RESORT_WATCHDOG_BAD_DHCP_NETWORKS_TOTAL = 4;
    private static final int NUM_LAST_RESORT_WATCHDOG_BAD_OTHER_NETWORKS_TOTAL = 5;
    private static final int NUM_LAST_RESORT_WATCHDOG_AVAILABLE_NETWORKS_TOTAL = 6;
    private static final int NUM_LAST_RESORT_WATCHDOG_TRIGGERS_WITH_BAD_ASSOCIATION = 7;
    private static final int NUM_LAST_RESORT_WATCHDOG_TRIGGERS_WITH_BAD_AUTHENTICATION = 8;
    private static final int NUM_LAST_RESORT_WATCHDOG_TRIGGERS_WITH_BAD_DHCP = 9;
    private static final int NUM_LAST_RESORT_WATCHDOG_TRIGGERS_WITH_BAD_OTHER = 10;
    private static final int NUM_LAST_RESORT_WATCHDOG_SUCCESSES = 5;
    private static final int NUM_RSSI_LEVELS_TO_INCREMENT = 20;
    private static final int FIRST_RSSI_LEVEL = -80;
    private static final int NUM_OPEN_NETWORK_SCAN_RESULTS = 1;
    private static final int NUM_PERSONAL_NETWORK_SCAN_RESULTS = 4;
    private static final int NUM_ENTERPRISE_NETWORK_SCAN_RESULTS = 3;
    private static final int NUM_HIDDEN_NETWORK_SCAN_RESULTS = 1;
    private static final int NUM_HOTSPOT2_R1_NETWORK_SCAN_RESULTS = 1;
    private static final int NUM_HOTSPOT2_R2_NETWORK_SCAN_RESULTS = 2;
    private static final int NUM_SCANS = 5;
    private static final int NUM_TOTAL_SCAN_RESULTS = 8;
    private static final int MIN_RSSI_LEVEL = -127;
    private static final int MAX_RSSI_LEVEL = 0;
    private static final int WIFI_SCORE_RANGE_MIN = 0;
    private static final int NUM_WIFI_SCORES_TO_INCREMENT = 20;
    private static final int WIFI_SCORE_RANGE_MAX = 60;
    private static final int NUM_OUT_OF_BOUND_ENTRIES = 10;
    private static final int MAX_NUM_SOFTAP_RETURN_CODES = 3;
    private static final int NUM_SOFTAP_START_SUCCESS = 3;
    private static final int NUM_SOFTAP_FAILED_GENERAL_ERROR = 2;
    private static final int NUM_SOFTAP_FAILED_NO_CHANNEL = 1;
    private static final int NUM_HAL_CRASHES = 11;
    private static final int NUM_WIFICOND_CRASHES = 12;
    private static final int NUM_WIFI_ON_FAILURE_DUE_TO_HAL = 13;
    private static final int NUM_WIFI_ON_FAILURE_DUE_TO_WIFICOND = 14;

    private ScanDetail buildMockScanDetail(boolean hidden, NetworkDetail.HSRelease hSRelease,
            String capabilities) {
        ScanDetail mockScanDetail = mock(ScanDetail.class);
        NetworkDetail mockNetworkDetail = mock(NetworkDetail.class);
        ScanResult mockScanResult = mock(ScanResult.class);
        when(mockScanDetail.getNetworkDetail()).thenReturn(mockNetworkDetail);
        when(mockScanDetail.getScanResult()).thenReturn(mockScanResult);
        when(mockNetworkDetail.isHiddenBeaconFrame()).thenReturn(hidden);
        when(mockNetworkDetail.getHSRelease()).thenReturn(hSRelease);
        mockScanResult.capabilities = capabilities;
        return mockScanDetail;
    }

    private List<ScanDetail> buildMockScanDetailList() {
        List<ScanDetail> mockScanDetails = new ArrayList<ScanDetail>();
        mockScanDetails.add(buildMockScanDetail(true, null, "[ESS]"));
        mockScanDetails.add(buildMockScanDetail(false, null, "[WPA2-PSK-CCMP][ESS]"));
        mockScanDetails.add(buildMockScanDetail(false, null, "[WPA-PSK-CCMP]"));
        mockScanDetails.add(buildMockScanDetail(false, null, "[WPA-PSK-CCMP]"));
        mockScanDetails.add(buildMockScanDetail(false, null, "[WEP]"));
        mockScanDetails.add(buildMockScanDetail(false, NetworkDetail.HSRelease.R2,
                "[WPA-EAP-CCMP]"));
        mockScanDetails.add(buildMockScanDetail(false, NetworkDetail.HSRelease.R2,
                "[WPA2-EAP+FT/EAP-CCMP]"));
        mockScanDetails.add(buildMockScanDetail(false, NetworkDetail.HSRelease.R1,
                "[WPA-EAP-CCMP]"));
        return mockScanDetails;
    }

    private List<WifiConfiguration> buildSavedNetworkList() {
        List<WifiConfiguration> testSavedNetworks = new ArrayList<WifiConfiguration>();
        for (int i = 0; i < NUM_OPEN_NETWORKS; i++) {
            testSavedNetworks.add(WifiConfigurationTestUtil.createOpenHiddenNetwork());
        }
        for (int i = 0; i < NUM_PERSONAL_NETWORKS; i++) {
            testSavedNetworks.add(WifiConfigurationTestUtil.createPskNetwork());
        }
        for (int i = 0; i < NUM_ENTERPRISE_NETWORKS; i++) {
            // Passpoint networks are counted in both Passpoint and Enterprise counters
            testSavedNetworks.add(WifiConfigurationTestUtil.createPasspointNetwork());
        }
        testSavedNetworks.get(0).selfAdded = true;
        return testSavedNetworks;
    }

    /**
     * Set simple metrics, increment others
     */
    public void setAndIncrementMetrics() throws Exception {
        mWifiMetrics.updateSavedNetworks(buildSavedNetworkList());
        mWifiMetrics.setIsLocationEnabled(TEST_VAL_IS_LOCATION_ENABLED);
        mWifiMetrics.setIsScanningAlwaysEnabled(IS_SCANNING_ALWAYS_ENABLED);

        for (int i = 0; i < NUM_EMPTY_SCAN_RESULTS; i++) {
            mWifiMetrics.incrementEmptyScanResultCount();
        }
        for (int i = 0; i < NUM_NON_EMPTY_SCAN_RESULTS; i++) {
            mWifiMetrics.incrementNonEmptyScanResultCount();
        }
        mWifiMetrics.incrementScanReturnEntry(WifiMetricsProto.WifiLog.SCAN_UNKNOWN,
                NUM_SCAN_UNKNOWN);
        mWifiMetrics.incrementScanReturnEntry(WifiMetricsProto.WifiLog.SCAN_SUCCESS,
                NUM_SCAN_SUCCESS);
        mWifiMetrics.incrementScanReturnEntry(
                WifiMetricsProto.WifiLog.SCAN_FAILURE_INTERRUPTED,
                NUM_SCAN_FAILURE_INTERRUPTED);
        mWifiMetrics.incrementScanReturnEntry(
                WifiMetricsProto.WifiLog.SCAN_FAILURE_INVALID_CONFIGURATION,
                NUM_SCAN_FAILURE_INVALID_CONFIGURATION);
        for (int i = 0; i < NUM_WIFI_UNKNOWN_SCREEN_OFF; i++) {
            mWifiMetrics.incrementWifiSystemScanStateCount(WifiMetricsProto.WifiLog.WIFI_UNKNOWN,
                    false);
        }
        for (int i = 0; i < NUM_WIFI_UNKNOWN_SCREEN_ON; i++) {
            mWifiMetrics.incrementWifiSystemScanStateCount(WifiMetricsProto.WifiLog.WIFI_UNKNOWN,
                    true);
        }
        for (int i = 0; i < NUM_WIFI_ASSOCIATED_SCREEN_OFF; i++) {
            mWifiMetrics.incrementWifiSystemScanStateCount(WifiMetricsProto.WifiLog.WIFI_ASSOCIATED,
                    false);
        }
        for (int i = 0; i < NUM_WIFI_ASSOCIATED_SCREEN_ON; i++) {
            mWifiMetrics.incrementWifiSystemScanStateCount(WifiMetricsProto.WifiLog.WIFI_ASSOCIATED,
                    true);
        }
        for (int i = 0; i < NUM_CONNECTIVITY_WATCHDOG_PNO_GOOD; i++) {
            mWifiMetrics.incrementNumConnectivityWatchdogPnoGood();
        }
        for (int i = 0; i < NUM_CONNECTIVITY_WATCHDOG_PNO_BAD; i++) {
            mWifiMetrics.incrementNumConnectivityWatchdogPnoBad();
        }
        for (int i = 0; i < NUM_CONNECTIVITY_WATCHDOG_BACKGROUND_GOOD; i++) {
            mWifiMetrics.incrementNumConnectivityWatchdogBackgroundGood();
        }
        for (int i = 0; i < NUM_CONNECTIVITY_WATCHDOG_BACKGROUND_BAD; i++) {
            mWifiMetrics.incrementNumConnectivityWatchdogBackgroundBad();
        }
        for (int i = 0; i < NUM_LAST_RESORT_WATCHDOG_TRIGGERS; i++) {
            mWifiMetrics.incrementNumLastResortWatchdogTriggers();
        }
        mWifiMetrics.addCountToNumLastResortWatchdogBadAssociationNetworksTotal(
                NUM_LAST_RESORT_WATCHDOG_BAD_ASSOCIATION_NETWORKS_TOTAL);
        mWifiMetrics.addCountToNumLastResortWatchdogBadAuthenticationNetworksTotal(
                NUM_LAST_RESORT_WATCHDOG_BAD_AUTHENTICATION_NETWORKS_TOTAL);
        mWifiMetrics.addCountToNumLastResortWatchdogBadDhcpNetworksTotal(
                NUM_LAST_RESORT_WATCHDOG_BAD_DHCP_NETWORKS_TOTAL);
        mWifiMetrics.addCountToNumLastResortWatchdogBadOtherNetworksTotal(
                NUM_LAST_RESORT_WATCHDOG_BAD_OTHER_NETWORKS_TOTAL);
        mWifiMetrics.addCountToNumLastResortWatchdogAvailableNetworksTotal(
                NUM_LAST_RESORT_WATCHDOG_AVAILABLE_NETWORKS_TOTAL);
        for (int i = 0; i < NUM_LAST_RESORT_WATCHDOG_TRIGGERS_WITH_BAD_ASSOCIATION; i++) {
            mWifiMetrics.incrementNumLastResortWatchdogTriggersWithBadAssociation();
        }
        for (int i = 0; i < NUM_LAST_RESORT_WATCHDOG_TRIGGERS_WITH_BAD_AUTHENTICATION; i++) {
            mWifiMetrics.incrementNumLastResortWatchdogTriggersWithBadAuthentication();
        }
        for (int i = 0; i < NUM_LAST_RESORT_WATCHDOG_TRIGGERS_WITH_BAD_DHCP; i++) {
            mWifiMetrics.incrementNumLastResortWatchdogTriggersWithBadDhcp();
        }
        for (int i = 0; i < NUM_LAST_RESORT_WATCHDOG_TRIGGERS_WITH_BAD_OTHER; i++) {
            mWifiMetrics.incrementNumLastResortWatchdogTriggersWithBadOther();
        }
        for (int i = 0; i < NUM_LAST_RESORT_WATCHDOG_SUCCESSES; i++) {
            mWifiMetrics.incrementNumLastResortWatchdogSuccesses();
        }
        for (int i = 0; i < NUM_RSSI_LEVELS_TO_INCREMENT; i++) {
            for (int j = 0; j <= i; j++) {
                mWifiMetrics.incrementRssiPollRssiCount(MIN_RSSI_LEVEL + i);
            }
        }
        for (int i = 1; i < NUM_OUT_OF_BOUND_ENTRIES; i++) {
            mWifiMetrics.incrementRssiPollRssiCount(MIN_RSSI_LEVEL - i);
        }
        for (int i = 1; i < NUM_OUT_OF_BOUND_ENTRIES; i++) {
            mWifiMetrics.incrementRssiPollRssiCount(MAX_RSSI_LEVEL + i);
        }
        // Test alert-reason clamping.
        mWifiMetrics.incrementAlertReasonCount(WifiLoggerHal.WIFI_ALERT_REASON_MIN - 1);
        mWifiMetrics.incrementAlertReasonCount(WifiLoggerHal.WIFI_ALERT_REASON_MAX + 1);
        // Simple cases for alert reason.
        mWifiMetrics.incrementAlertReasonCount(1);
        mWifiMetrics.incrementAlertReasonCount(1);
        mWifiMetrics.incrementAlertReasonCount(1);
        mWifiMetrics.incrementAlertReasonCount(2);
        List<ScanDetail> mockScanDetails = buildMockScanDetailList();
        for (int i = 0; i < NUM_SCANS; i++) {
            mWifiMetrics.countScanResults(mockScanDetails);
        }
        for (int score = WIFI_SCORE_RANGE_MIN; score < NUM_WIFI_SCORES_TO_INCREMENT; score++) {
            for (int offset = 0; offset <= score; offset++) {
                mWifiMetrics.incrementWifiScoreCount(WIFI_SCORE_RANGE_MIN + score);
            }
        }
        for (int i = 1; i < NUM_OUT_OF_BOUND_ENTRIES; i++) {
            mWifiMetrics.incrementWifiScoreCount(WIFI_SCORE_RANGE_MIN - i);
        }
        for (int i = 1; i < NUM_OUT_OF_BOUND_ENTRIES; i++) {
            mWifiMetrics.incrementWifiScoreCount(WIFI_SCORE_RANGE_MAX + i);
        }

        // increment soft ap start return codes
        for (int i = 0; i < NUM_SOFTAP_START_SUCCESS; i++) {
            mWifiMetrics.incrementSoftApStartResult(true, 0);
        }
        for (int i = 0; i < NUM_SOFTAP_FAILED_GENERAL_ERROR; i++) {
            mWifiMetrics.incrementSoftApStartResult(false, WifiManager.SAP_START_FAILURE_GENERAL);
        }
        for (int i = 0; i < NUM_SOFTAP_FAILED_NO_CHANNEL; i++) {
            mWifiMetrics.incrementSoftApStartResult(false,
                    WifiManager.SAP_START_FAILURE_NO_CHANNEL);
        }
        for (int i = 0; i < NUM_HAL_CRASHES; i++) {
            mWifiMetrics.incrementNumHalCrashes();
        }
        for (int i = 0; i < NUM_WIFICOND_CRASHES; i++) {
            mWifiMetrics.incrementNumWificondCrashes();
        }
        for (int i = 0; i < NUM_WIFI_ON_FAILURE_DUE_TO_HAL; i++) {
            mWifiMetrics.incrementNumWifiOnFailureDueToHal();
        }
        for (int i = 0; i < NUM_WIFI_ON_FAILURE_DUE_TO_WIFICOND; i++) {
            mWifiMetrics.incrementNumWifiOnFailureDueToWificond();
        }
    }

    /**
     * Assert that values in deserializedWifiMetrics match those set in 'setAndIncrementMetrics'
     */
    public void assertDeserializedMetricsCorrect() throws Exception {
        assertEquals("mDeserializedWifiMetrics.numSavedNetworks == NUM_SAVED_NETWORKS",
                mDeserializedWifiMetrics.numSavedNetworks, NUM_SAVED_NETWORKS);
        assertEquals("mDeserializedWifiMetrics.numOpenNetworks == NUM_OPEN_NETWORKS",
                mDeserializedWifiMetrics.numOpenNetworks, NUM_OPEN_NETWORKS);
        assertEquals("mDeserializedWifiMetrics.numPersonalNetworks == NUM_PERSONAL_NETWORKS",
                mDeserializedWifiMetrics.numPersonalNetworks, NUM_PERSONAL_NETWORKS);
        assertEquals("mDeserializedWifiMetrics.numEnterpriseNetworks "
                        + "== NUM_ENTERPRISE_NETWORKS",
                mDeserializedWifiMetrics.numEnterpriseNetworks, NUM_ENTERPRISE_NETWORKS);
        assertEquals("mDeserializedWifiMetrics.numNetworksAddedByUser "
                        + "== NUM_NETWORKS_ADDED_BY_USER",
                mDeserializedWifiMetrics.numNetworksAddedByUser, NUM_NETWORKS_ADDED_BY_USER);
        assertEquals(NUM_HIDDEN_NETWORKS, mDeserializedWifiMetrics.numHiddenNetworks);
        assertEquals(NUM_PASSPOINT_NETWORKS, mDeserializedWifiMetrics.numPasspointNetworks);
        assertEquals("mDeserializedWifiMetrics.numNetworksAddedByApps "
                        + "== NUM_NETWORKS_ADDED_BY_APPS",
                mDeserializedWifiMetrics.numNetworksAddedByApps, NUM_NETWORKS_ADDED_BY_APPS);
        assertEquals("mDeserializedWifiMetrics.isLocationEnabled == TEST_VAL_IS_LOCATION_ENABLED",
                mDeserializedWifiMetrics.isLocationEnabled, TEST_VAL_IS_LOCATION_ENABLED);
        assertEquals("mDeserializedWifiMetrics.isScanningAlwaysEnabled "
                        + "== IS_SCANNING_ALWAYS_ENABLED",
                mDeserializedWifiMetrics.isScanningAlwaysEnabled, IS_SCANNING_ALWAYS_ENABLED);
        assertEquals("mDeserializedWifiMetrics.numEmptyScanResults == NUM_EMPTY_SCAN_RESULTS",
                mDeserializedWifiMetrics.numEmptyScanResults, NUM_EMPTY_SCAN_RESULTS);
        assertEquals("mDeserializedWifiMetrics.numNonEmptyScanResults == "
                        + "NUM_NON_EMPTY_SCAN_RESULTS",
                mDeserializedWifiMetrics.numNonEmptyScanResults, NUM_NON_EMPTY_SCAN_RESULTS);
        assertScanReturnEntryEquals(WifiMetricsProto.WifiLog.SCAN_UNKNOWN,
                NUM_SCAN_UNKNOWN);
        assertScanReturnEntryEquals(WifiMetricsProto.WifiLog.SCAN_SUCCESS,
                NUM_SCAN_SUCCESS);
        assertScanReturnEntryEquals(WifiMetricsProto.WifiLog.SCAN_FAILURE_INTERRUPTED,
                NUM_SCAN_FAILURE_INTERRUPTED);
        assertScanReturnEntryEquals(WifiMetricsProto.WifiLog.SCAN_FAILURE_INVALID_CONFIGURATION,
                NUM_SCAN_FAILURE_INVALID_CONFIGURATION);
        assertSystemStateEntryEquals(WifiMetricsProto.WifiLog.WIFI_UNKNOWN, false,
                NUM_WIFI_UNKNOWN_SCREEN_OFF);
        assertSystemStateEntryEquals(WifiMetricsProto.WifiLog.WIFI_UNKNOWN, true,
                NUM_WIFI_UNKNOWN_SCREEN_ON);
        assertSystemStateEntryEquals(
                WifiMetricsProto.WifiLog.WIFI_ASSOCIATED, false, NUM_WIFI_ASSOCIATED_SCREEN_OFF);
        assertSystemStateEntryEquals(WifiMetricsProto.WifiLog.WIFI_ASSOCIATED, true,
                NUM_WIFI_ASSOCIATED_SCREEN_ON);
        assertEquals(mDeserializedWifiMetrics.numConnectivityWatchdogPnoGood,
                NUM_CONNECTIVITY_WATCHDOG_PNO_GOOD);
        assertEquals(mDeserializedWifiMetrics.numConnectivityWatchdogPnoBad,
                NUM_CONNECTIVITY_WATCHDOG_PNO_BAD);
        assertEquals(mDeserializedWifiMetrics.numConnectivityWatchdogBackgroundGood,
                NUM_CONNECTIVITY_WATCHDOG_BACKGROUND_GOOD);
        assertEquals(mDeserializedWifiMetrics.numConnectivityWatchdogBackgroundBad,
                NUM_CONNECTIVITY_WATCHDOG_BACKGROUND_BAD);
        assertEquals(NUM_LAST_RESORT_WATCHDOG_TRIGGERS,
                mDeserializedWifiMetrics.numLastResortWatchdogTriggers);
        assertEquals(NUM_LAST_RESORT_WATCHDOG_BAD_ASSOCIATION_NETWORKS_TOTAL,
                mDeserializedWifiMetrics.numLastResortWatchdogBadAssociationNetworksTotal);
        assertEquals(NUM_LAST_RESORT_WATCHDOG_BAD_AUTHENTICATION_NETWORKS_TOTAL,
                mDeserializedWifiMetrics.numLastResortWatchdogBadAuthenticationNetworksTotal);
        assertEquals(NUM_LAST_RESORT_WATCHDOG_BAD_DHCP_NETWORKS_TOTAL,
                mDeserializedWifiMetrics.numLastResortWatchdogBadDhcpNetworksTotal);
        assertEquals(NUM_LAST_RESORT_WATCHDOG_BAD_OTHER_NETWORKS_TOTAL,
                mDeserializedWifiMetrics.numLastResortWatchdogBadOtherNetworksTotal);
        assertEquals(NUM_LAST_RESORT_WATCHDOG_AVAILABLE_NETWORKS_TOTAL,
                mDeserializedWifiMetrics.numLastResortWatchdogAvailableNetworksTotal);
        assertEquals(NUM_LAST_RESORT_WATCHDOG_TRIGGERS_WITH_BAD_ASSOCIATION,
                mDeserializedWifiMetrics.numLastResortWatchdogTriggersWithBadAssociation);
        assertEquals(NUM_LAST_RESORT_WATCHDOG_TRIGGERS_WITH_BAD_AUTHENTICATION,
                mDeserializedWifiMetrics.numLastResortWatchdogTriggersWithBadAuthentication);
        assertEquals(NUM_LAST_RESORT_WATCHDOG_TRIGGERS_WITH_BAD_DHCP,
                mDeserializedWifiMetrics.numLastResortWatchdogTriggersWithBadDhcp);
        assertEquals(NUM_LAST_RESORT_WATCHDOG_TRIGGERS_WITH_BAD_OTHER,
                mDeserializedWifiMetrics.numLastResortWatchdogTriggersWithBadOther);
        assertEquals(NUM_LAST_RESORT_WATCHDOG_SUCCESSES,
                mDeserializedWifiMetrics.numLastResortWatchdogSuccesses);
        assertEquals(TEST_RECORD_DURATION_SEC,
                mDeserializedWifiMetrics.recordDurationSec);
        for (int i = 0; i < NUM_RSSI_LEVELS_TO_INCREMENT; i++) {
            assertEquals(MIN_RSSI_LEVEL + i, mDeserializedWifiMetrics.rssiPollRssiCount[i].rssi);
            assertEquals(i + 1, mDeserializedWifiMetrics.rssiPollRssiCount[i].count);
        }
        StringBuilder sb_rssi = new StringBuilder();
        sb_rssi.append("Number of RSSIs = " + mDeserializedWifiMetrics.rssiPollRssiCount.length);
        assertTrue(sb_rssi.toString(), (mDeserializedWifiMetrics.rssiPollRssiCount.length
                     <= (MAX_RSSI_LEVEL - MIN_RSSI_LEVEL + 1)));
        assertEquals(2, mDeserializedWifiMetrics.alertReasonCount[0].count);  // Clamped reasons.
        assertEquals(3, mDeserializedWifiMetrics.alertReasonCount[1].count);
        assertEquals(1, mDeserializedWifiMetrics.alertReasonCount[2].count);
        assertEquals(3, mDeserializedWifiMetrics.alertReasonCount.length);
        assertEquals(NUM_TOTAL_SCAN_RESULTS * NUM_SCANS,
                mDeserializedWifiMetrics.numTotalScanResults);
        assertEquals(NUM_OPEN_NETWORK_SCAN_RESULTS * NUM_SCANS,
                mDeserializedWifiMetrics.numOpenNetworkScanResults);
        assertEquals(NUM_PERSONAL_NETWORK_SCAN_RESULTS * NUM_SCANS,
                mDeserializedWifiMetrics.numPersonalNetworkScanResults);
        assertEquals(NUM_ENTERPRISE_NETWORK_SCAN_RESULTS * NUM_SCANS,
                mDeserializedWifiMetrics.numEnterpriseNetworkScanResults);
        assertEquals(NUM_HIDDEN_NETWORK_SCAN_RESULTS * NUM_SCANS,
                mDeserializedWifiMetrics.numHiddenNetworkScanResults);
        assertEquals(NUM_HOTSPOT2_R1_NETWORK_SCAN_RESULTS * NUM_SCANS,
                mDeserializedWifiMetrics.numHotspot2R1NetworkScanResults);
        assertEquals(NUM_HOTSPOT2_R2_NETWORK_SCAN_RESULTS * NUM_SCANS,
                mDeserializedWifiMetrics.numHotspot2R2NetworkScanResults);
        assertEquals(NUM_SCANS,
                mDeserializedWifiMetrics.numScans);
        for (int score_index = 0; score_index < NUM_WIFI_SCORES_TO_INCREMENT; score_index++) {
            assertEquals(WIFI_SCORE_RANGE_MIN + score_index,
                    mDeserializedWifiMetrics.wifiScoreCount[score_index].score);
            assertEquals(score_index + 1,
                    mDeserializedWifiMetrics.wifiScoreCount[score_index].count);
        }
        StringBuilder sb_wifi_score = new StringBuilder();
        sb_wifi_score.append("Number of wifi_scores = "
                + mDeserializedWifiMetrics.wifiScoreCount.length);
        assertTrue(sb_wifi_score.toString(), (mDeserializedWifiMetrics.wifiScoreCount.length
                <= (WIFI_SCORE_RANGE_MAX - WIFI_SCORE_RANGE_MIN + 1)));
        StringBuilder sb_wifi_limits = new StringBuilder();
        sb_wifi_limits.append("Wifi Score limit is " +  NetworkAgent.WIFI_BASE_SCORE
                + ">= " + WIFI_SCORE_RANGE_MAX);
        assertTrue(sb_wifi_limits.toString(), NetworkAgent.WIFI_BASE_SCORE <= WIFI_SCORE_RANGE_MAX);
        assertEquals(MAX_NUM_SOFTAP_RETURN_CODES, mDeserializedWifiMetrics.softApReturnCode.length);
        assertEquals(WifiMetricsProto.SoftApReturnCodeCount.SOFT_AP_STARTED_SUCCESSFULLY,
                     mDeserializedWifiMetrics.softApReturnCode[0].startResult);
        assertEquals(NUM_SOFTAP_START_SUCCESS, mDeserializedWifiMetrics.softApReturnCode[0].count);
        assertEquals(WifiMetricsProto.SoftApReturnCodeCount.SOFT_AP_FAILED_GENERAL_ERROR,
                     mDeserializedWifiMetrics.softApReturnCode[1].startResult);
        assertEquals(NUM_SOFTAP_FAILED_GENERAL_ERROR,
                     mDeserializedWifiMetrics.softApReturnCode[1].count);
        assertEquals(WifiMetricsProto.SoftApReturnCodeCount.SOFT_AP_FAILED_NO_CHANNEL,
                     mDeserializedWifiMetrics.softApReturnCode[2].startResult);
        assertEquals(NUM_SOFTAP_FAILED_NO_CHANNEL,
                     mDeserializedWifiMetrics.softApReturnCode[2].count);
        assertEquals(NUM_HAL_CRASHES, mDeserializedWifiMetrics.numHalCrashes);
        assertEquals(NUM_WIFICOND_CRASHES, mDeserializedWifiMetrics.numWificondCrashes);
        assertEquals(NUM_WIFI_ON_FAILURE_DUE_TO_HAL,
                mDeserializedWifiMetrics.numWifiOnFailureDueToHal);
        assertEquals(NUM_WIFI_ON_FAILURE_DUE_TO_WIFICOND,
                mDeserializedWifiMetrics.numWifiOnFailureDueToWificond);
    }

    /**
     *  Assert deserialized metrics Scan Return Entry equals count
     */
    public void assertScanReturnEntryEquals(int returnCode, int count) {
        for (int i = 0; i < mDeserializedWifiMetrics.scanReturnEntries.length; i++) {
            if (mDeserializedWifiMetrics.scanReturnEntries[i].scanReturnCode == returnCode) {
                assertEquals(mDeserializedWifiMetrics.scanReturnEntries[i].scanResultsCount, count);
                return;
            }
        }
        assertEquals(null, count);
    }

    /**
     *  Assert deserialized metrics SystemState entry equals count
     */
    public void assertSystemStateEntryEquals(int state, boolean screenOn, int count) {
        for (int i = 0; i < mDeserializedWifiMetrics.wifiSystemStateEntries.length; i++) {
            if (mDeserializedWifiMetrics.wifiSystemStateEntries[i].wifiState == state
                    && mDeserializedWifiMetrics.wifiSystemStateEntries[i].isScreenOn == screenOn) {
                assertEquals(mDeserializedWifiMetrics.wifiSystemStateEntries[i].wifiStateCount,
                        count);
                return;
            }
        }
        assertEquals(null, count);
    }
    /**
     * Combination of all other WifiMetrics unit tests, an internal-integration test, or functional
     * test
     */
    @Test
    public void setMetricsSerializeDeserializeAssertMetricsSame() throws Exception {
        setAndIncrementMetrics();
        startAndEndConnectionEventSucceeds();
        dumpProtoAndDeserialize();
        assertDeserializedMetricsCorrect();
        assertEquals("mDeserializedWifiMetrics.connectionEvent.length",
                2, mDeserializedWifiMetrics.connectionEvent.length);
        //<TODO> test individual connectionEvents for correctness,
        // check scanReturnEntries & wifiSystemStateEntries counts and individual elements
        // pending their implementation</TODO>
    }

    private static final String SSID = "red";
    private static final int CONFIG_DTIM = 3;
    private static final int NETWORK_DETAIL_WIFIMODE = 5;
    private static final int NETWORK_DETAIL_DTIM = 7;
    private static final int SCAN_RESULT_LEVEL = -30;
    /**
     * Test that WifiMetrics is correctly getting data from ScanDetail and WifiConfiguration
     */
    @Test
    public void testScanDetailAndWifiConfigurationUsage() throws Exception {
        //Setup mock configs and scan details
        NetworkDetail networkDetail = mock(NetworkDetail.class);
        when(networkDetail.getWifiMode()).thenReturn(NETWORK_DETAIL_WIFIMODE);
        when(networkDetail.getSSID()).thenReturn(SSID);
        when(networkDetail.getDtimInterval()).thenReturn(NETWORK_DETAIL_DTIM);
        ScanResult scanResult = mock(ScanResult.class);
        scanResult.level = SCAN_RESULT_LEVEL;
        WifiConfiguration config = mock(WifiConfiguration.class);
        config.SSID = "\"" + SSID + "\"";
        config.dtimInterval = CONFIG_DTIM;
        WifiConfiguration.NetworkSelectionStatus networkSelectionStat =
                mock(WifiConfiguration.NetworkSelectionStatus.class);
        when(networkSelectionStat.getCandidate()).thenReturn(scanResult);
        when(config.getNetworkSelectionStatus()).thenReturn(networkSelectionStat);
        ScanDetail scanDetail = mock(ScanDetail.class);
        when(scanDetail.getNetworkDetail()).thenReturn(networkDetail);
        when(scanDetail.getScanResult()).thenReturn(scanResult);

        //Create a connection event using only the config
        mWifiMetrics.startConnectionEvent(config, "Red",
                WifiMetricsProto.ConnectionEvent.ROAM_NONE);
        mWifiMetrics.endConnectionEvent(
                WifiMetrics.ConnectionEvent.FAILURE_NONE,
                WifiMetricsProto.ConnectionEvent.HLF_NONE);

        //Create a connection event using the config and a scan detail
        mWifiMetrics.startConnectionEvent(config, "Green",
                WifiMetricsProto.ConnectionEvent.ROAM_NONE);
        mWifiMetrics.setConnectionScanDetail(scanDetail);
        mWifiMetrics.endConnectionEvent(
                WifiMetrics.ConnectionEvent.FAILURE_NONE,
                WifiMetricsProto.ConnectionEvent.HLF_NONE);

        //Dump proto from mWifiMetrics and deserialize it to mDeserializedWifiMetrics
        dumpProtoAndDeserialize();

        //Check that the correct values are being flowed through
        assertEquals(mDeserializedWifiMetrics.connectionEvent.length, 2);
        assertEquals(mDeserializedWifiMetrics.connectionEvent[0].routerFingerprint.dtim,
                CONFIG_DTIM);
        assertEquals(mDeserializedWifiMetrics.connectionEvent[0].signalStrength, SCAN_RESULT_LEVEL);
        assertEquals(mDeserializedWifiMetrics.connectionEvent[1].routerFingerprint.dtim,
                NETWORK_DETAIL_DTIM);
        assertEquals(mDeserializedWifiMetrics.connectionEvent[1].signalStrength,
                SCAN_RESULT_LEVEL);
        assertEquals(mDeserializedWifiMetrics.connectionEvent[1].routerFingerprint.routerTechnology,
                NETWORK_DETAIL_WIFIMODE);
    }

    /**
     * Test that WifiMetrics is being cleared after dumping via proto
     */
    @Test
    public void testMetricsClearedAfterProtoRequested() throws Exception {
        // Create 3 ConnectionEvents
        mWifiMetrics.startConnectionEvent(null, "RED",
                WifiMetricsProto.ConnectionEvent.ROAM_ENTERPRISE);
        mWifiMetrics.endConnectionEvent(
                WifiMetrics.ConnectionEvent.FAILURE_NONE,
                WifiMetricsProto.ConnectionEvent.HLF_NONE);
        mWifiMetrics.startConnectionEvent(null, "YELLOW",
                WifiMetricsProto.ConnectionEvent.ROAM_ENTERPRISE);
        mWifiMetrics.endConnectionEvent(
                WifiMetrics.ConnectionEvent.FAILURE_NONE,
                WifiMetricsProto.ConnectionEvent.HLF_NONE);
        mWifiMetrics.startConnectionEvent(null, "GREEN",
                WifiMetricsProto.ConnectionEvent.ROAM_ENTERPRISE);
        mWifiMetrics.endConnectionEvent(
                WifiMetrics.ConnectionEvent.FAILURE_NONE,
                WifiMetricsProto.ConnectionEvent.HLF_NONE);
        mWifiMetrics.startConnectionEvent(null, "ORANGE",
                WifiMetricsProto.ConnectionEvent.ROAM_ENTERPRISE);
        mWifiMetrics.endConnectionEvent(
                WifiMetrics.ConnectionEvent.FAILURE_NONE,
                WifiMetricsProto.ConnectionEvent.HLF_NONE);

        //Dump proto and deserialize
        //This should clear all the metrics in mWifiMetrics,
        dumpProtoAndDeserialize();
        //Check there are only 3 connection events
        assertEquals(mDeserializedWifiMetrics.connectionEvent.length, 4);
        assertEquals(mDeserializedWifiMetrics.rssiPollRssiCount.length, 0);
        assertEquals(mDeserializedWifiMetrics.alertReasonCount.length, 0);

        // Create 2 ConnectionEvents
        mWifiMetrics.startConnectionEvent(null,  "BLUE",
                WifiMetricsProto.ConnectionEvent.ROAM_ENTERPRISE);
        mWifiMetrics.endConnectionEvent(
                WifiMetrics.ConnectionEvent.FAILURE_NONE,
                WifiMetricsProto.ConnectionEvent.HLF_NONE);
        mWifiMetrics.startConnectionEvent(null, "RED",
                WifiMetricsProto.ConnectionEvent.ROAM_ENTERPRISE);
        mWifiMetrics.endConnectionEvent(
                WifiMetrics.ConnectionEvent.FAILURE_NONE,
                WifiMetricsProto.ConnectionEvent.HLF_NONE);

        //Dump proto and deserialize
        dumpProtoAndDeserialize();
        //Check there are only 2 connection events
        assertEquals(mDeserializedWifiMetrics.connectionEvent.length, 2);
    }

    /**
     * Tests that after setting metrics values they can be serialized and deserialized with the
     *   $ adb shell dumpsys wifi wifiMetricsProto clean
     */
    @Test
    public void testClearMetricsDump() throws Exception {
        setAndIncrementMetrics();
        startAndEndConnectionEventSucceeds();
        cleanDumpProtoAndDeserialize();
        assertDeserializedMetricsCorrect();
        assertEquals("mDeserializedWifiMetrics.connectionEvent.length",
                2, mDeserializedWifiMetrics.connectionEvent.length);
    }

    private static final int NUM_REPEATED_DELTAS = 7;
    private static final int REPEATED_DELTA = 0;
    private static final int SINGLE_GOOD_DELTA = 1;
    private static final int SINGLE_TIMEOUT_DELTA = 2;
    private static final int NUM_REPEATED_BOUND_DELTAS = 2;
    private static final int MAX_DELTA_LEVEL = 127;
    private static final int MIN_DELTA_LEVEL = -127;
    private static final int ARBITRARY_DELTA_LEVEL = 20;

    /**
     * Sunny day RSSI delta logging scenario.
     * Logs one rssi delta value multiple times
     * Logs a different delta value a single time
     */
    @Test
    public void testRssiDeltasSuccessfulLogging() throws Exception {
        // Generate some repeated deltas
        for (int i = 0; i < NUM_REPEATED_DELTAS; i++) {
            generateRssiDelta(MIN_RSSI_LEVEL, REPEATED_DELTA,
                    WifiMetrics.TIMEOUT_RSSI_DELTA_MILLIS);
        }
        // Generate a single delta
        generateRssiDelta(MIN_RSSI_LEVEL, SINGLE_GOOD_DELTA,
                WifiMetrics.TIMEOUT_RSSI_DELTA_MILLIS);
        dumpProtoAndDeserialize();
        assertEquals(2, mDeserializedWifiMetrics.rssiPollDeltaCount.length);
        // Check the repeated deltas
        assertEquals(NUM_REPEATED_DELTAS, mDeserializedWifiMetrics.rssiPollDeltaCount[0].count);
        assertEquals(REPEATED_DELTA, mDeserializedWifiMetrics.rssiPollDeltaCount[0].rssi);
        // Check the single delta
        assertEquals(1, mDeserializedWifiMetrics.rssiPollDeltaCount[1].count);
        assertEquals(SINGLE_GOOD_DELTA, mDeserializedWifiMetrics.rssiPollDeltaCount[1].rssi);
    }

    /**
     * Tests that Rssi Delta events whose scanResult and Rssi Poll come too far apart, timeout,
     * and are not logged.
     */
    @Test
    public void testRssiDeltasTimeout() throws Exception {
        // Create timed out rssi deltas
        generateRssiDelta(MIN_RSSI_LEVEL, REPEATED_DELTA,
                WifiMetrics.TIMEOUT_RSSI_DELTA_MILLIS + 1);
        generateRssiDelta(MIN_RSSI_LEVEL, SINGLE_TIMEOUT_DELTA,
                WifiMetrics.TIMEOUT_RSSI_DELTA_MILLIS + 1);
        dumpProtoAndDeserialize();
        assertEquals(0, mDeserializedWifiMetrics.rssiPollDeltaCount.length);
    }

    /**
     * Tests the exact inclusive boundaries of RSSI delta logging.
     */
    @Test
    public void testRssiDeltaSuccessfulLoggingExactBounds() throws Exception {
        generateRssiDelta(MIN_RSSI_LEVEL, MAX_DELTA_LEVEL,
                WifiMetrics.TIMEOUT_RSSI_DELTA_MILLIS);
        generateRssiDelta(MAX_RSSI_LEVEL, MIN_DELTA_LEVEL,
                WifiMetrics.TIMEOUT_RSSI_DELTA_MILLIS);
        dumpProtoAndDeserialize();
        assertEquals(2, mDeserializedWifiMetrics.rssiPollDeltaCount.length);
        assertEquals(MIN_DELTA_LEVEL, mDeserializedWifiMetrics.rssiPollDeltaCount[0].rssi);
        assertEquals(1, mDeserializedWifiMetrics.rssiPollDeltaCount[0].count);
        assertEquals(MAX_DELTA_LEVEL, mDeserializedWifiMetrics.rssiPollDeltaCount[1].rssi);
        assertEquals(1, mDeserializedWifiMetrics.rssiPollDeltaCount[1].count);
    }

    /**
     * Tests the exact exclusive boundaries of RSSI delta logging.
     * This test ensures that too much data is not generated.
     */
    @Test
    public void testRssiDeltaOutOfBounds() throws Exception {
        generateRssiDelta(MIN_RSSI_LEVEL, MAX_DELTA_LEVEL + 1,
                WifiMetrics.TIMEOUT_RSSI_DELTA_MILLIS);
        generateRssiDelta(MAX_RSSI_LEVEL, MIN_DELTA_LEVEL - 1,
                WifiMetrics.TIMEOUT_RSSI_DELTA_MILLIS);
        dumpProtoAndDeserialize();
        assertEquals(0, mDeserializedWifiMetrics.rssiPollDeltaCount.length);
    }

    /**
     * This test ensures no rssi Delta is logged after an unsuccessful ConnectionEvent
     */
    @Test
    public void testUnsuccesfulConnectionEventRssiDeltaIsNotLogged() throws Exception {
        generateRssiDelta(MIN_RSSI_LEVEL, ARBITRARY_DELTA_LEVEL,
                WifiMetrics.TIMEOUT_RSSI_DELTA_MILLIS,
                false, // successfulConnectionEvent
                true, // completeConnectionEvent
                true, // useValidScanResult
                true // dontDeserializeBeforePoll
        );

        dumpProtoAndDeserialize();
        assertEquals(0, mDeserializedWifiMetrics.rssiPollDeltaCount.length);
    }

    /**
     * This test ensures rssi Deltas can be logged during a ConnectionEvent
     */
    @Test
    public void testIncompleteConnectionEventRssiDeltaIsLogged() throws Exception {
        generateRssiDelta(MIN_RSSI_LEVEL, ARBITRARY_DELTA_LEVEL,
                WifiMetrics.TIMEOUT_RSSI_DELTA_MILLIS,
                true, // successfulConnectionEvent
                false, // completeConnectionEvent
                true, // useValidScanResult
                true // dontDeserializeBeforePoll
        );
        dumpProtoAndDeserialize();
        assertEquals(1, mDeserializedWifiMetrics.rssiPollDeltaCount.length);
        assertEquals(ARBITRARY_DELTA_LEVEL, mDeserializedWifiMetrics.rssiPollDeltaCount[0].rssi);
        assertEquals(1, mDeserializedWifiMetrics.rssiPollDeltaCount[0].count);
    }

    /**
     * This test ensures that no delta is logged for a null ScanResult Candidate
     */
    @Test
    public void testRssiDeltaNotLoggedForNullCandidateScanResult() throws Exception {
        generateRssiDelta(MIN_RSSI_LEVEL, ARBITRARY_DELTA_LEVEL,
                WifiMetrics.TIMEOUT_RSSI_DELTA_MILLIS,
                true, // successfulConnectionEvent
                true, // completeConnectionEvent
                false, // useValidScanResult
                true // dontDeserializeBeforePoll
        );
        dumpProtoAndDeserialize();
        assertEquals(0, mDeserializedWifiMetrics.rssiPollDeltaCount.length);
    }

    /**
     * This test ensures that Rssi Deltas are not logged over a 'clear()' call (Metrics Serialized)
     */
    @Test
    public void testMetricsSerializedDuringRssiDeltaEventLogsNothing() throws Exception {
        generateRssiDelta(MIN_RSSI_LEVEL, ARBITRARY_DELTA_LEVEL,
                WifiMetrics.TIMEOUT_RSSI_DELTA_MILLIS,
                true, // successfulConnectionEvent
                true, // completeConnectionEvent
                true, // useValidScanResult
                false // dontDeserializeBeforePoll
        );
        dumpProtoAndDeserialize();
        assertEquals(0, mDeserializedWifiMetrics.rssiPollDeltaCount.length);
    }

    private static final int DEAUTH_REASON = 7;
    private static final int ASSOC_STATUS = 11;
    private static final int ASSOC_TIMEOUT = 1;
    private static final int LOCAL_GEN = 1;
    private static final int AUTH_FAILURE_REASON = WifiManager.ERROR_AUTH_FAILURE_WRONG_PSWD;
    private static final int NUM_TEST_STA_EVENTS = 14;
    private static final String   sSSID = "\"SomeTestSsid\"";
    private static final WifiSsid sWifiSsid = WifiSsid.createFromAsciiEncoded(sSSID);
    private static final String   sBSSID = "01:02:03:04:05:06";

    private final StateChangeResult mStateDisconnected =
            new StateChangeResult(0, sWifiSsid, sBSSID, SupplicantState.DISCONNECTED);
    private final StateChangeResult mStateCompleted =
            new StateChangeResult(0, sWifiSsid, sBSSID, SupplicantState.COMPLETED);
    // Test bitmasks of supplicant state changes
    private final int mSupBm1 = WifiMetrics.supplicantStateToBit(mStateDisconnected.state);
    private final int mSupBm2 = WifiMetrics.supplicantStateToBit(mStateDisconnected.state)
            | WifiMetrics.supplicantStateToBit(mStateCompleted.state);
    // An invalid but interesting wifiConfiguration that exercises the StaEvent.ConfigInfo encoding
    private final WifiConfiguration mTestWifiConfig = createComplexWifiConfig();
    // <msg.what> <msg.arg1> <msg.arg2>
    private int[][] mTestStaMessageInts = {
        {WifiMonitor.ASSOCIATION_REJECTION_EVENT,   ASSOC_TIMEOUT,      ASSOC_STATUS},
        {WifiMonitor.AUTHENTICATION_FAILURE_EVENT,  0,                  AUTH_FAILURE_REASON},
        {WifiMonitor.NETWORK_CONNECTION_EVENT,      0,                  0},
        {WifiMonitor.NETWORK_DISCONNECTION_EVENT,   LOCAL_GEN,          DEAUTH_REASON},
        {WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0,                  0},
        {WifiStateMachine.CMD_ASSOCIATED_BSSID,     0,                  0},
        {WifiStateMachine.CMD_TARGET_BSSID,         0,                  0},
        {WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0,                  0},
        {WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT, 0,                  0}
    };
    private Object[] mTestStaMessageObjs = {
        null,
        null,
        null,
        null,
        mStateDisconnected,
        null,
        null,
        mStateDisconnected,
        mStateCompleted
    };
    // Values used to generate the StaEvent log calls from WifiStateMachine
    // <StaEvent.Type>, <StaEvent.FrameworkDisconnectReason>, <1|0>(testWifiConfiguration, null)
    private int[][] mTestStaLogInts = {
        {StaEvent.TYPE_CMD_IP_CONFIGURATION_SUCCESSFUL, 0,                          0},
        {StaEvent.TYPE_CMD_IP_CONFIGURATION_LOST,       0,                          0},
        {StaEvent.TYPE_CMD_IP_REACHABILITY_LOST,        0,                          0},
        {StaEvent.TYPE_CMD_START_CONNECT,               0,                          1},
        {StaEvent.TYPE_CMD_START_ROAM,                  0,                          1},
        {StaEvent.TYPE_CONNECT_NETWORK,                 0,                          1},
        {StaEvent.TYPE_NETWORK_AGENT_VALID_NETWORK,     0,                          0},
        {StaEvent.TYPE_FRAMEWORK_DISCONNECT,            StaEvent.DISCONNECT_API,    0}
    };
    // Values used to generate the StaEvent log calls from WifiMonitor
    // <type>, <reason>, <status>, <local_gen>,
    // <auth_fail_reason>, <assoc_timed_out> <supplicantStateChangeBitmask> <1|0>(has ConfigInfo)
    private int[][] mExpectedValues = {
        {StaEvent.TYPE_ASSOCIATION_REJECTION_EVENT,     -1,  ASSOC_STATUS,         0,
            /**/                               0, ASSOC_TIMEOUT,        0, 0},    /**/
        {StaEvent.TYPE_AUTHENTICATION_FAILURE_EVENT,    -1,            -1,         0,
            /**/StaEvent.AUTH_FAILURE_WRONG_PSWD,             0,        0, 0},    /**/
        {StaEvent.TYPE_NETWORK_CONNECTION_EVENT,        -1,            -1,         0,
            /**/                               0,             0,        0, 0},    /**/
        {StaEvent.TYPE_NETWORK_DISCONNECTION_EVENT, DEAUTH_REASON,     -1, LOCAL_GEN,
            /**/                               0,             0,        0, 0},    /**/
        {StaEvent.TYPE_CMD_ASSOCIATED_BSSID,            -1,            -1,         0,
            /**/                               0,             0,  mSupBm1, 0},    /**/
        {StaEvent.TYPE_CMD_TARGET_BSSID,                -1,            -1,         0,
            /**/                               0,             0,        0, 0},    /**/
        {StaEvent.TYPE_CMD_IP_CONFIGURATION_SUCCESSFUL, -1,            -1,         0,
            /**/                               0,             0,  mSupBm2, 0},    /**/
        {StaEvent.TYPE_CMD_IP_CONFIGURATION_LOST,       -1,            -1,         0,
            /**/                               0,             0,        0, 0},    /**/
        {StaEvent.TYPE_CMD_IP_REACHABILITY_LOST,        -1,            -1,         0,
            /**/                               0,             0,        0, 0},    /**/
        {StaEvent.TYPE_CMD_START_CONNECT,               -1,            -1,         0,
            /**/                               0,             0,        0, 1},    /**/
        {StaEvent.TYPE_CMD_START_ROAM,                  -1,            -1,         0,
            /**/                               0,             0,        0, 1},    /**/
        {StaEvent.TYPE_CONNECT_NETWORK,                 -1,            -1,         0,
            /**/                               0,             0,        0, 1},    /**/
        {StaEvent.TYPE_NETWORK_AGENT_VALID_NETWORK,     -1,            -1,         0,
            /**/                               0,             0,        0, 0},    /**/
        {StaEvent.TYPE_FRAMEWORK_DISCONNECT,            -1,            -1,         0,
            /**/                               0,             0,        0, 0}     /**/
    };

    /**
     * Generates events from all the rows in mTestStaMessageInts, and then mTestStaLogInts
     */
    private void generateStaEvents(WifiMetrics wifiMetrics) {
        Handler handler = wifiMetrics.getHandler();
        for (int i = 0; i < mTestStaMessageInts.length; i++) {
            int[] mia = mTestStaMessageInts[i];
            handler.sendMessage(
                    handler.obtainMessage(mia[0], mia[1], mia[2], mTestStaMessageObjs[i]));
        }
        mTestLooper.dispatchAll();
        for (int i = 0; i < mTestStaLogInts.length; i++) {
            int[] lia = mTestStaLogInts[i];
            wifiMetrics.logStaEvent(lia[0], lia[1], lia[2] == 1 ? mTestWifiConfig : null);
        }
    }
    private void verifyDeserializedStaEvents(WifiMetricsProto.WifiLog wifiLog) {
        assertEquals(NUM_TEST_STA_EVENTS, wifiLog.staEventList.length);
        int j = 0; // De-serialized event index
        for (int i = 0; i < mTestStaMessageInts.length; i++) {
            StaEvent event = wifiLog.staEventList[j];
            int[] mia = mTestStaMessageInts[i];
            int[] evs = mExpectedValues[j];
            if (mia[0] != WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT) {
                assertEquals(evs[0], event.type);
                assertEquals(evs[1], event.reason);
                assertEquals(evs[2], event.status);
                assertEquals(evs[3] == 1 ? true : false, event.localGen);
                assertEquals(evs[4], event.authFailureReason);
                assertEquals(evs[5] == 1 ? true : false, event.associationTimedOut);
                assertEquals(evs[6], event.supplicantStateChangesBitmask);
                assertConfigInfoEqualsWifiConfig(
                        evs[7] == 1 ? mTestWifiConfig : null, event.configInfo);
                j++;
            }
        }
    }

    /**
     * Generate StaEvents of each type, ensure all the different values are logged correctly,
     * and that they survive serialization & de-serialization
     */
    @Test
    public void testStaEventsLogSerializeDeserialize() throws Exception {
        generateStaEvents(mWifiMetrics);
        dumpProtoAndDeserialize();
        verifyDeserializedStaEvents(mDeserializedWifiMetrics);
    }

    /**
     * Ensure the number of StaEvents does not exceed MAX_STA_EVENTS by generating lots of events
     * and checking how many are deserialized
     */
    @Test
    public void testStaEventBounding() throws Exception {
        for (int i = 0; i < (WifiMetrics.MAX_STA_EVENTS + 10); i++) {
            mWifiMetrics.logStaEvent(StaEvent.TYPE_CMD_START_CONNECT);
        }
        dumpProtoAndDeserialize();
        assertEquals(WifiMetrics.MAX_STA_EVENTS, mDeserializedWifiMetrics.staEventList.length);
    }

    /**
     * Ensure WifiMetrics doesn't cause a null pointer exception when called with null args
     */
    @Test
    public void testDumpNullArg() {
        mWifiMetrics.dump(new FileDescriptor(), new PrintWriter(new StringWriter()), null);
    }

    /**
     * Generate an RSSI delta event by creating a connection event and an RSSI poll within
     * 'interArrivalTime' milliseconds of each other.
     * Event will not be logged if interArrivalTime > mWifiMetrics.TIMEOUT_RSSI_DELTA_MILLIS
     * successfulConnectionEvent, completeConnectionEvent, useValidScanResult and
     * dontDeserializeBeforePoll
     * each create an anomalous condition when set to false.
     */
    private void generateRssiDelta(int scanRssi, int rssiDelta,
            long interArrivalTime, boolean successfulConnectionEvent,
            boolean completeConnectionEvent, boolean useValidScanResult,
            boolean dontDeserializeBeforePoll) throws Exception {
        when(mClock.getElapsedSinceBootMillis()).thenReturn((long) 0);
        ScanResult scanResult = null;
        if (useValidScanResult) {
            scanResult = mock(ScanResult.class);
            scanResult.level = scanRssi;
        }
        WifiConfiguration config = mock(WifiConfiguration.class);
        WifiConfiguration.NetworkSelectionStatus networkSelectionStat =
                mock(WifiConfiguration.NetworkSelectionStatus.class);
        when(networkSelectionStat.getCandidate()).thenReturn(scanResult);
        when(config.getNetworkSelectionStatus()).thenReturn(networkSelectionStat);
        mWifiMetrics.startConnectionEvent(config, "TestNetwork",
                WifiMetricsProto.ConnectionEvent.ROAM_ENTERPRISE);
        if (completeConnectionEvent) {
            if (successfulConnectionEvent) {
                mWifiMetrics.endConnectionEvent(
                        WifiMetrics.ConnectionEvent.FAILURE_NONE,
                        WifiMetricsProto.ConnectionEvent.HLF_NONE);
            } else {
                mWifiMetrics.endConnectionEvent(
                        WifiMetrics.ConnectionEvent.FAILURE_AUTHENTICATION_FAILURE,
                        WifiMetricsProto.ConnectionEvent.HLF_NONE);
            }
        }
        when(mClock.getElapsedSinceBootMillis()).thenReturn(interArrivalTime);
        if (!dontDeserializeBeforePoll) {
            dumpProtoAndDeserialize();
        }
        mWifiMetrics.incrementRssiPollRssiCount(scanRssi + rssiDelta);
    }
    /**
     * Generate an RSSI delta event, with all extra conditions set to true.
     */
    private void generateRssiDelta(int scanRssi, int rssiDelta,
            long interArrivalTime) throws Exception {
        generateRssiDelta(scanRssi, rssiDelta, interArrivalTime, true, true, true, true);
    }

    private void assertStringContains(
            String actualString, String expectedSubstring) {
        assertTrue("Expected text not found in: " + actualString,
                actualString.contains(expectedSubstring));
    }

    private String getStateDump() {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(stream);
        String[] args = new String[0];
        mWifiMetrics.dump(null, writer, args);
        writer.flush();
        return stream.toString();
    }

    private static final int TEST_ALLOWED_KEY_MANAGEMENT = 83;
    private static final int TEST_ALLOWED_PROTOCOLS = 22;
    private static final int TEST_ALLOWED_AUTH_ALGORITHMS = 11;
    private static final int TEST_ALLOWED_PAIRWISE_CIPHERS = 67;
    private static final int TEST_ALLOWED_GROUP_CIPHERS = 231;
    private static final int TEST_CANDIDATE_LEVEL = -80;
    private static final int TEST_CANDIDATE_FREQ = 2345;

    private WifiConfiguration createComplexWifiConfig() {
        WifiConfiguration config = new WifiConfiguration();
        config.allowedKeyManagement = intToBitSet(TEST_ALLOWED_KEY_MANAGEMENT);
        config.allowedProtocols = intToBitSet(TEST_ALLOWED_PROTOCOLS);
        config.allowedAuthAlgorithms = intToBitSet(TEST_ALLOWED_AUTH_ALGORITHMS);
        config.allowedPairwiseCiphers = intToBitSet(TEST_ALLOWED_PAIRWISE_CIPHERS);
        config.allowedGroupCiphers = intToBitSet(TEST_ALLOWED_GROUP_CIPHERS);
        config.hiddenSSID = true;
        config.ephemeral = true;
        config.getNetworkSelectionStatus().setHasEverConnected(true);
        ScanResult candidate = new ScanResult();
        candidate.level = TEST_CANDIDATE_LEVEL;
        candidate.frequency = TEST_CANDIDATE_FREQ;
        config.getNetworkSelectionStatus().setCandidate(candidate);
        return config;
    }

    private void assertConfigInfoEqualsWifiConfig(WifiConfiguration config,
            StaEvent.ConfigInfo info) {
        if (config == null && info == null) return;
        assertEquals(config.allowedKeyManagement,   intToBitSet(info.allowedKeyManagement));
        assertEquals(config.allowedProtocols,       intToBitSet(info.allowedProtocols));
        assertEquals(config.allowedAuthAlgorithms,  intToBitSet(info.allowedAuthAlgorithms));
        assertEquals(config.allowedPairwiseCiphers, intToBitSet(info.allowedPairwiseCiphers));
        assertEquals(config.allowedGroupCiphers,    intToBitSet(info.allowedGroupCiphers));
        assertEquals(config.hiddenSSID, info.hiddenSsid);
        assertEquals(config.ephemeral, info.isEphemeral);
        assertEquals(config.getNetworkSelectionStatus().getHasEverConnected(),
                info.hasEverConnected);
        assertEquals(config.getNetworkSelectionStatus().getCandidate().level, info.scanRssi);
        assertEquals(config.getNetworkSelectionStatus().getCandidate().frequency, info.scanFreq);
    }

    /**
     * Sets the values of bitSet to match an int mask
     */
    private static BitSet intToBitSet(int mask) {
        BitSet bitSet = new BitSet();
        for (int bitIndex = 0; mask > 0; mask >>>= 1, bitIndex++) {
            if ((mask & 1) != 0) bitSet.set(bitIndex);
        }
        return bitSet;
    }
}
