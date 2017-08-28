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

package com.android.server.wifi.aware;

import static android.hardware.wifi.V1_0.NanDataPathChannelCfg.REQUEST_CHANNEL_SETUP;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.app.test.TestAlarmManager;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkFactory;
import android.net.NetworkRequest;
import android.net.NetworkSpecifier;
import android.net.wifi.aware.AttachCallback;
import android.net.wifi.aware.ConfigRequest;
import android.net.wifi.aware.DiscoverySession;
import android.net.wifi.aware.DiscoverySessionCallback;
import android.net.wifi.aware.IWifiAwareDiscoverySessionCallback;
import android.net.wifi.aware.IWifiAwareEventCallback;
import android.net.wifi.aware.IWifiAwareManager;
import android.net.wifi.aware.PeerHandle;
import android.net.wifi.aware.PublishConfig;
import android.net.wifi.aware.PublishDiscoverySession;
import android.net.wifi.aware.SubscribeConfig;
import android.net.wifi.aware.SubscribeDiscoverySession;
import android.net.wifi.aware.WifiAwareManager;
import android.net.wifi.aware.WifiAwareNetworkSpecifier;
import android.net.wifi.aware.WifiAwareSession;
import android.os.Handler;
import android.os.INetworkManagementService;
import android.os.Message;
import android.os.Messenger;
import android.os.test.TestLooper;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.Pair;

import com.android.internal.util.AsyncChannel;

import libcore.util.HexEncoding;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.util.Arrays;

/**
 * Unit test harness for WifiAwareDataPathStateManager class.
 */
@SmallTest
public class WifiAwareDataPathStateManagerTest {
    private static final String sAwareInterfacePrefix = "aware_data";

    private TestLooper mMockLooper;
    private Handler mMockLooperHandler;
    private WifiAwareStateManager mDut;
    @Mock private WifiAwareNativeApi mMockNative;
    @Mock private Context mMockContext;
    @Mock private IWifiAwareManager mMockAwareService;
    @Mock private ConnectivityManager mMockCm;
    @Mock private INetworkManagementService mMockNwMgt;
    @Mock private WifiAwareDataPathStateManager.NetworkInterfaceWrapper mMockNetworkInterface;
    @Mock private IWifiAwareEventCallback mMockCallback;
    @Mock IWifiAwareDiscoverySessionCallback mMockSessionCallback;
    TestAlarmManager mAlarmManager;

    @Rule
    public ErrorCollector collector = new ErrorCollector();

    /**
     * Initialize mocks.
     */
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mAlarmManager = new TestAlarmManager();
        when(mMockContext.getSystemService(Context.ALARM_SERVICE))
                .thenReturn(mAlarmManager.getAlarmManager());

        when(mMockContext.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(mMockCm);

        mMockLooper = new TestLooper();
        mMockLooperHandler = new Handler(mMockLooper.getLooper());

        mDut = new WifiAwareStateManager();
        mDut.setNative(mMockNative);
        mDut.start(mMockContext, mMockLooper.getLooper());
        mDut.startLate();

        when(mMockNative.getCapabilities(anyShort())).thenReturn(true);
        when(mMockNative.enableAndConfigure(anyShort(), any(), anyBoolean(),
                anyBoolean())).thenReturn(true);
        when(mMockNative.disable(anyShort())).thenReturn(true);
        when(mMockNative.publish(anyShort(), anyInt(), any())).thenReturn(true);
        when(mMockNative.subscribe(anyShort(), anyInt(), any()))
                .thenReturn(true);
        when(mMockNative.sendMessage(anyShort(), anyInt(), anyInt(), any(),
                any(), anyInt())).thenReturn(true);
        when(mMockNative.stopPublish(anyShort(), anyInt())).thenReturn(true);
        when(mMockNative.stopSubscribe(anyShort(), anyInt())).thenReturn(true);
        when(mMockNative.createAwareNetworkInterface(anyShort(), any())).thenReturn(true);
        when(mMockNative.deleteAwareNetworkInterface(anyShort(), any())).thenReturn(true);
        when(mMockNative.initiateDataPath(anyShort(), anyInt(), anyInt(), anyInt(),
                any(), any(), any(), any(),
                any())).thenReturn(true);
        when(mMockNative.respondToDataPathRequest(anyShort(), anyBoolean(), anyInt(), any(),
                any(), any(), any())).thenReturn(true);
        when(mMockNative.endDataPath(anyShort(), anyInt())).thenReturn(true);

        when(mMockNetworkInterface.configureAgentProperties(any(), any(), anyInt(), any(), any(),
                any())).thenReturn(true);

        installDataPathStateManagerMocks();
    }

    /**
     * Validates that creating and deleting all interfaces works based on capabilities.
     */
    @Test
    public void testCreateDeleteAllInterfaces() throws Exception {
        final int numNdis = 3;
        final int failCreateInterfaceIndex = 1;

        Capabilities capabilities = new Capabilities();
        capabilities.maxNdiInterfaces = numNdis;

        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);
        ArgumentCaptor<String> interfaceName = ArgumentCaptor.forClass(String.class);
        InOrder inOrder = inOrder(mMockNative);

        // (1) get capabilities
        mDut.queryCapabilities();
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).getCapabilities(transactionId.capture());
        mDut.onCapabilitiesUpdateResponse(transactionId.getValue(), capabilities);
        mMockLooper.dispatchAll();

        // (2) create all interfaces
        mDut.createAllDataPathInterfaces();
        mMockLooper.dispatchAll();
        for (int i = 0; i < numNdis; ++i) {
            inOrder.verify(mMockNative).createAwareNetworkInterface(transactionId.capture(),
                    interfaceName.capture());
            collector.checkThat("interface created -- " + i, sAwareInterfacePrefix + i,
                    equalTo(interfaceName.getValue()));
            mDut.onCreateDataPathInterfaceResponse(transactionId.getValue(), true, 0);
            mMockLooper.dispatchAll();
        }

        // (3) delete all interfaces [one unsuccessfully] - note that will not necessarily be
        // done sequentially
        boolean[] done = new boolean[numNdis];
        Arrays.fill(done, false);
        mDut.deleteAllDataPathInterfaces();
        mMockLooper.dispatchAll();
        for (int i = 0; i < numNdis; ++i) {
            inOrder.verify(mMockNative).deleteAwareNetworkInterface(transactionId.capture(),
                    interfaceName.capture());
            int interfaceIndex = Integer.valueOf(
                    interfaceName.getValue().substring(sAwareInterfacePrefix.length()));
            done[interfaceIndex] = true;
            if (interfaceIndex == failCreateInterfaceIndex) {
                mDut.onDeleteDataPathInterfaceResponse(transactionId.getValue(), false, 0);
            } else {
                mDut.onDeleteDataPathInterfaceResponse(transactionId.getValue(), true, 0);
            }
            mMockLooper.dispatchAll();
        }
        for (int i = 0; i < numNdis; ++i) {
            collector.checkThat("interface deleted -- " + i, done[i], equalTo(true));
        }

        // (4) create all interfaces (should get a delete for the one which couldn't delete earlier)
        mDut.createAllDataPathInterfaces();
        mMockLooper.dispatchAll();
        for (int i = 0; i < numNdis; ++i) {
            if (i == failCreateInterfaceIndex) {
                inOrder.verify(mMockNative).deleteAwareNetworkInterface(transactionId.capture(),
                        interfaceName.capture());
                collector.checkThat("interface delete pre-create -- " + i,
                        sAwareInterfacePrefix + i, equalTo(interfaceName.getValue()));
                mDut.onDeleteDataPathInterfaceResponse(transactionId.getValue(), true, 0);
                mMockLooper.dispatchAll();
            }
            inOrder.verify(mMockNative).createAwareNetworkInterface(transactionId.capture(),
                    interfaceName.capture());
            collector.checkThat("interface created -- " + i, sAwareInterfacePrefix + i,
                    equalTo(interfaceName.getValue()));
            mDut.onCreateDataPathInterfaceResponse(transactionId.getValue(), true, 0);
            mMockLooper.dispatchAll();
        }

        verifyNoMoreInteractions(mMockNative);
    }

    /*
     * Initiator tests
     */

    /**
     * Validate the success flow of the Initiator: using session network specifier with a non-null
     * token.
     */
    @Test
    public void testDataPathInitiatorMacTokenSuccess() throws Exception {
        testDataPathInitiatorUtility(false, true, true, true, false);
    }

    /**
     * Validate the fail flow of the Initiator: using session network specifier with a 0
     * peer ID.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testDataPathInitiatorNoMacFail() throws Exception {
        testDataPathInitiatorUtility(false, false, true, true, false);
    }

    /**
     * Validate the success flow of the Initiator: using a direct network specifier with a non-null
     * peer mac and non-null token.
     */
    @Test
    public void testDataPathInitiatorDirectMacTokenSuccess() throws Exception {
        testDataPathInitiatorUtility(true, true, true, true, false);
    }

    /**
     * Validate the fail flow of the Initiator: using a direct network specifier with a null peer
     * mac and non-null token.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testDataPathInitiatorDirectNoMacTokenFail() throws Exception {
        testDataPathInitiatorUtility(true, false, true, true, false);
    }

    /**
     * Validate the fail flow of the Initiator: using a direct network specifier with a null peer
     * mac and null token.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testDataPathInitiatorDirectNoMacNoTokenFail() throws Exception {
        testDataPathInitiatorUtility(true, false, false, true, false);
    }

    /**
     * Validate the fail flow of the Initiator: use a session network specifier with a non-null
     * token, but don't get a confirmation.
     */
    @Test
    public void testDataPathInitiatorNoConfirmationTimeoutFail() throws Exception {
        testDataPathInitiatorUtility(false, true, true, false, false);
    }

    /**
     * Validate the fail flow of the Initiator: use a session network specifier with a non-null
     * token, but get an immediate failure
     */
    @Test
    public void testDataPathInitiatorNoConfirmationHalFail() throws Exception {
        testDataPathInitiatorUtility(false, true, true, true, true);
    }

    /**
     * Validate the fail flow of a mis-configured request: Publisher as Initiator
     */
    @Test
    public void testDataPathInitiatorOnPublisherError() throws Exception {
        testDataPathInitiatorResponderMismatchUtility(true);
    }

    /*
     * Responder tests
     */

    /**
     * Validate the success flow of the Responder: using session network specifier with a non-null
     * token.
     */
    @Test
    public void testDataPathResonderMacTokenSuccess() throws Exception {
        testDataPathResponderUtility(false, true, true, true);
    }

    /**
     * Validate the success flow of the Responder: using session network specifier with a null
     * token.
     */
    @Test
    public void testDataPathResonderMacNoTokenSuccess() throws Exception {
        testDataPathResponderUtility(false, true, false, true);
    }

    /**
     * Validate the success flow of the Responder: using session network specifier with a
     * token and no peer ID (i.e. 0).
     */
    @Test
    public void testDataPathResonderMacTokenNoPeerIdSuccess() throws Exception {
        testDataPathResponderUtility(false, false, true, true);
    }

    /**
     * Validate the success flow of the Responder: using session network specifier with a null
     * token and no peer ID (i.e. 0).
     */
    @Test
    public void testDataPathResonderMacTokenNoPeerIdNoTokenSuccess() throws Exception {
        testDataPathResponderUtility(false, false, false, true);
    }

    /**
     * Validate the success flow of the Responder: using a direct network specifier with a non-null
     * peer mac and non-null token.
     */
    @Test
    public void testDataPathResonderDirectMacTokenSuccess() throws Exception {
        testDataPathResponderUtility(true, true, true, true);
    }

    /**
     * Validate the success flow of the Responder: using a direct network specifier with a non-null
     * peer mac and null token.
     */
    @Test
    public void testDataPathResonderDirectMacNoTokenSuccess() throws Exception {
        testDataPathResponderUtility(true, true, false, true);
    }

    /**
     * Validate the success flow of the Responder: using a direct network specifier with a null peer
     * mac and non-null token.
     */
    @Test
    public void testDataPathResonderDirectNoMacTokenSuccess() throws Exception {
        testDataPathResponderUtility(true, false, true, true);
    }

    /**
     * Validate the success flow of the Responder: using a direct network specifier with a null peer
     * mac and null token.
     */
    @Test
    public void testDataPathResonderDirectNoMacNoTokenSuccess() throws Exception {
        testDataPathResponderUtility(true, false, false, true);
    }

    /**
     * Validate the fail flow of the Responder: use a session network specifier with a non-null
     * token, but don't get a confirmation.
     */
    @Test
    public void testDataPathResponderNoConfirmationTimeoutFail() throws Exception {
        testDataPathResponderUtility(false, true, true, false);
    }

    /**
     * Validate the fail flow of a mis-configured request: Subscriber as Responder
     */
    @Test
    public void testDataPathResponderOnSubscriberError() throws Exception {
        testDataPathInitiatorResponderMismatchUtility(false);
    }

    /*
     * Utilities
     */

    private void testDataPathInitiatorResponderMismatchUtility(boolean doPublish) throws Exception {
        final int clientId = 123;
        final int pubSubId = 11234;
        final int ndpId = 2;
        final byte[] pmk = "some bytes".getBytes();
        final PeerHandle peerHandle = new PeerHandle(1341234);
        final byte[] peerDiscoveryMac = HexEncoding.decode("000102030405".toCharArray(), false);

        InOrder inOrder = inOrder(mMockNative, mMockCm, mMockCallback, mMockSessionCallback);

        // (0) initialize
        Pair<Integer, Messenger> res = initDataPathEndPoint(clientId, pubSubId, peerHandle,
                peerDiscoveryMac, inOrder, doPublish);

        // (1) request network
        NetworkRequest nr = getSessionNetworkRequest(clientId, res.first, peerHandle, pmk,
                doPublish);

        // corrupt the network specifier: reverse the role (so it's mis-matched)
        WifiAwareNetworkSpecifier ns =
                (WifiAwareNetworkSpecifier) nr.networkCapabilities.getNetworkSpecifier();
        ns = new WifiAwareNetworkSpecifier(
                ns.type,
                1 - ns.role, // corruption hack
                ns.clientId,
                ns.sessionId,
                ns.peerId,
                ns.peerMac,
                ns.pmk,
                ns.passphrase
                );
        nr.networkCapabilities.setNetworkSpecifier(ns);

        Message reqNetworkMsg = Message.obtain();
        reqNetworkMsg.what = NetworkFactory.CMD_REQUEST_NETWORK;
        reqNetworkMsg.obj = nr;
        reqNetworkMsg.arg1 = 0;
        res.second.send(reqNetworkMsg);
        mMockLooper.dispatchAll();

        // consequences of failure:
        //   Responder (publisher): responds with a rejection to any data-path requests
        //   Initiator (subscribe): doesn't initiate (i.e. no HAL requests)
        if (doPublish) {
            // (2) get request & respond
            mDut.onDataPathRequestNotification(pubSubId, peerDiscoveryMac, ndpId);
            mMockLooper.dispatchAll();
            inOrder.verify(mMockNative).respondToDataPathRequest(anyShort(), eq(false),
                    eq(ndpId), eq(""), eq(null), eq(null), any());
        }

        verifyNoMoreInteractions(mMockNative, mMockCm);
    }

    private void testDataPathInitiatorUtility(boolean useDirect, boolean provideMac,
            boolean providePmk, boolean getConfirmation, boolean immediateHalFailure)
            throws Exception {
        final int clientId = 123;
        final int pubSubId = 11234;
        final PeerHandle peerHandle = new PeerHandle(1341234);
        final int ndpId = 2;
        final byte[] pmk = "some bytes".getBytes();
        final String peerToken = "let's go!";
        final byte[] peerDiscoveryMac = HexEncoding.decode("000102030405".toCharArray(), false);
        final byte[] peerDataPathMac = HexEncoding.decode("0A0B0C0D0E0F".toCharArray(), false);

        ArgumentCaptor<Messenger> messengerCaptor = ArgumentCaptor.forClass(Messenger.class);
        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);
        InOrder inOrder = inOrder(mMockNative, mMockCm, mMockCallback, mMockSessionCallback);

        if (immediateHalFailure) {
            when(mMockNative.initiateDataPath(anyShort(), anyInt(), anyInt(), anyInt(), any(),
                    any(), any(), any(), any())).thenReturn(false);

        }

        // (0) initialize
        Pair<Integer, Messenger> res = initDataPathEndPoint(clientId, pubSubId, peerHandle,
                peerDiscoveryMac, inOrder, false);

        // (1) request network
        NetworkRequest nr;
        if (useDirect) {
            nr = getDirectNetworkRequest(clientId,
                    WifiAwareManager.WIFI_AWARE_DATA_PATH_ROLE_INITIATOR,
                    provideMac ? peerDiscoveryMac : null, providePmk ? pmk : null);
        } else {
            nr = getSessionNetworkRequest(clientId, res.first, provideMac ? peerHandle : null,
                    providePmk ? pmk : null, false);
        }

        Message reqNetworkMsg = Message.obtain();
        reqNetworkMsg.what = NetworkFactory.CMD_REQUEST_NETWORK;
        reqNetworkMsg.obj = nr;
        reqNetworkMsg.arg1 = 0;
        res.second.send(reqNetworkMsg);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).initiateDataPath(transactionId.capture(),
                eq(useDirect ? 0 : peerHandle.peerId),
                eq(REQUEST_CHANNEL_SETUP), eq(2437), eq(peerDiscoveryMac),
                eq(sAwareInterfacePrefix + "0"), eq(pmk), eq(null), any());
        if (immediateHalFailure) {
            // short-circuit the rest of this test
            verifyNoMoreInteractions(mMockNative, mMockCm);
            return;
        }

        mDut.onInitiateDataPathResponseSuccess(transactionId.getValue(), ndpId);
        mMockLooper.dispatchAll();

        // (2) get confirmation OR timeout
        if (getConfirmation) {
            mDut.onDataPathConfirmNotification(ndpId, peerDataPathMac, true, 0,
                    peerToken.getBytes());
            mMockLooper.dispatchAll();
            inOrder.verify(mMockCm).registerNetworkAgent(messengerCaptor.capture(), any(), any(),
                    any(), anyInt(), any());
        } else {
            assertTrue(mAlarmManager.dispatch(
                    WifiAwareStateManager.HAL_DATA_PATH_CONFIRM_TIMEOUT_TAG));
            mMockLooper.dispatchAll();
            inOrder.verify(mMockNative).endDataPath(transactionId.capture(), eq(ndpId));
            mDut.onEndDataPathResponse(transactionId.getValue(), true, 0);
            mMockLooper.dispatchAll();
        }

        // (3) end data-path (unless didn't get confirmation)
        if (getConfirmation) {
            Message endNetworkReqMsg = Message.obtain();
            endNetworkReqMsg.what = NetworkFactory.CMD_CANCEL_REQUEST;
            endNetworkReqMsg.obj = nr;
            res.second.send(endNetworkReqMsg);

            Message endNetworkUsageMsg = Message.obtain();
            endNetworkUsageMsg.what = AsyncChannel.CMD_CHANNEL_DISCONNECTED;
            messengerCaptor.getValue().send(endNetworkUsageMsg);

            mMockLooper.dispatchAll();
            inOrder.verify(mMockNative).endDataPath(transactionId.capture(), eq(ndpId));
            mDut.onEndDataPathResponse(transactionId.getValue(), true, 0);
            mMockLooper.dispatchAll();
        }

        verifyNoMoreInteractions(mMockNative, mMockCm);
    }

    private void testDataPathResponderUtility(boolean useDirect, boolean provideMac,
            boolean providePmk, boolean getConfirmation) throws Exception {
        final int clientId = 123;
        final int pubSubId = 11234;
        final PeerHandle peerHandle = new PeerHandle(1341234);
        final int ndpId = 2;
        final byte[] pmk = "some bytes".getBytes();
        final String peerToken = "let's go!";
        final byte[] peerDiscoveryMac = HexEncoding.decode("000102030405".toCharArray(), false);
        final byte[] peerDataPathMac = HexEncoding.decode("0A0B0C0D0E0F".toCharArray(), false);

        ArgumentCaptor<Messenger> messengerCaptor = ArgumentCaptor.forClass(Messenger.class);
        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);
        InOrder inOrder = inOrder(mMockNative, mMockCm, mMockCallback, mMockSessionCallback);

        // (0) initialize
        Pair<Integer, Messenger> res = initDataPathEndPoint(clientId, pubSubId, peerHandle,
                peerDiscoveryMac, inOrder, true);

        // (1) request network
        NetworkRequest nr;
        if (useDirect) {
            nr = getDirectNetworkRequest(clientId,
                    WifiAwareManager.WIFI_AWARE_DATA_PATH_ROLE_RESPONDER,
                    provideMac ? peerDiscoveryMac : null, providePmk ? pmk : null);
        } else {
            nr = getSessionNetworkRequest(clientId, res.first, provideMac ? peerHandle : null,
                    providePmk ? pmk : null, true);
        }

        Message reqNetworkMsg = Message.obtain();
        reqNetworkMsg.what = NetworkFactory.CMD_REQUEST_NETWORK;
        reqNetworkMsg.obj = nr;
        reqNetworkMsg.arg1 = 0;
        res.second.send(reqNetworkMsg);
        mMockLooper.dispatchAll();

        // (2) get request & respond
        mDut.onDataPathRequestNotification(pubSubId, peerDiscoveryMac, ndpId);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).respondToDataPathRequest(transactionId.capture(), eq(true),
                eq(ndpId), eq(sAwareInterfacePrefix + "0"), eq(providePmk ? pmk : null), eq(null),
                any());
        mDut.onRespondToDataPathSetupRequestResponse(transactionId.getValue(), true, 0);
        mMockLooper.dispatchAll();

        // (3) get confirmation OR timeout
        if (getConfirmation) {
            mDut.onDataPathConfirmNotification(ndpId, peerDataPathMac, true, 0,
                    peerToken.getBytes());
            mMockLooper.dispatchAll();
            inOrder.verify(mMockCm).registerNetworkAgent(messengerCaptor.capture(), any(), any(),
                    any(), anyInt(), any());
        } else {
            assertTrue(mAlarmManager.dispatch(
                    WifiAwareStateManager.HAL_DATA_PATH_CONFIRM_TIMEOUT_TAG));
            mMockLooper.dispatchAll();
            inOrder.verify(mMockNative).endDataPath(transactionId.capture(), eq(ndpId));
            mDut.onEndDataPathResponse(transactionId.getValue(), true, 0);
            mMockLooper.dispatchAll();
        }

        // (4) end data-path (unless didn't get confirmation)
        if (getConfirmation) {
            Message endNetworkMsg = Message.obtain();
            endNetworkMsg.what = NetworkFactory.CMD_CANCEL_REQUEST;
            endNetworkMsg.obj = nr;
            res.second.send(endNetworkMsg);

            Message endNetworkUsageMsg = Message.obtain();
            endNetworkUsageMsg.what = AsyncChannel.CMD_CHANNEL_DISCONNECTED;
            messengerCaptor.getValue().send(endNetworkUsageMsg);

            mMockLooper.dispatchAll();
            inOrder.verify(mMockNative).endDataPath(transactionId.capture(), eq(ndpId));
            mDut.onEndDataPathResponse(transactionId.getValue(), true, 0);
            mMockLooper.dispatchAll();
        }

        verifyNoMoreInteractions(mMockNative, mMockCm);
    }

    private void installDataPathStateManagerMocks() throws Exception {
        Field field = WifiAwareStateManager.class.getDeclaredField("mDataPathMgr");
        field.setAccessible(true);
        Object mDataPathMgr = field.get(mDut);

        field = WifiAwareDataPathStateManager.class.getDeclaredField("mNwService");
        field.setAccessible(true);
        field.set(mDataPathMgr, mMockNwMgt);

        field = WifiAwareDataPathStateManager.class.getDeclaredField("mNiWrapper");
        field.setAccessible(true);
        field.set(mDataPathMgr, mMockNetworkInterface);
    }

    private NetworkRequest getSessionNetworkRequest(int clientId, int sessionId,
            PeerHandle peerHandle, byte[] pmk, boolean doPublish)
            throws Exception {
        final WifiAwareManager mgr = new WifiAwareManager(mMockContext, mMockAwareService);
        final ConfigRequest configRequest = new ConfigRequest.Builder().build();
        final PublishConfig publishConfig = new PublishConfig.Builder().build();
        final SubscribeConfig subscribeConfig = new SubscribeConfig.Builder().build();

        ArgumentCaptor<WifiAwareSession> sessionCaptor = ArgumentCaptor.forClass(
                WifiAwareSession.class);
        ArgumentCaptor<IWifiAwareEventCallback> clientProxyCallback = ArgumentCaptor
                .forClass(IWifiAwareEventCallback.class);
        ArgumentCaptor<IWifiAwareDiscoverySessionCallback> sessionProxyCallback = ArgumentCaptor
                .forClass(IWifiAwareDiscoverySessionCallback.class);
        ArgumentCaptor<DiscoverySession> discoverySession = ArgumentCaptor
                .forClass(DiscoverySession.class);

        AttachCallback mockCallback = mock(AttachCallback.class);
        DiscoverySessionCallback mockSessionCallback = mock(
                DiscoverySessionCallback.class);

        mgr.attach(mMockLooperHandler, configRequest, mockCallback, null);
        verify(mMockAwareService).connect(any(), any(),
                clientProxyCallback.capture(), eq(configRequest), eq(false));
        clientProxyCallback.getValue().onConnectSuccess(clientId);
        mMockLooper.dispatchAll();
        verify(mockCallback).onAttached(sessionCaptor.capture());
        if (doPublish) {
            sessionCaptor.getValue().publish(publishConfig, mockSessionCallback,
                    mMockLooperHandler);
            verify(mMockAwareService).publish(eq(clientId), eq(publishConfig),
                    sessionProxyCallback.capture());
        } else {
            sessionCaptor.getValue().subscribe(subscribeConfig, mockSessionCallback,
                    mMockLooperHandler);
            verify(mMockAwareService).subscribe(eq(clientId), eq(subscribeConfig),
                    sessionProxyCallback.capture());
        }
        sessionProxyCallback.getValue().onSessionStarted(sessionId);
        mMockLooper.dispatchAll();
        if (doPublish) {
            verify(mockSessionCallback).onPublishStarted(
                    (PublishDiscoverySession) discoverySession.capture());
        } else {
            verify(mockSessionCallback).onSubscribeStarted(
                    (SubscribeDiscoverySession) discoverySession.capture());
        }

        NetworkSpecifier ns;
        if (pmk == null) {
            ns = discoverySession.getValue().createNetworkSpecifierOpen(peerHandle);
        } else {
            ns = discoverySession.getValue().createNetworkSpecifierPmk(peerHandle, pmk);
        }

        NetworkCapabilities nc = new NetworkCapabilities();
        nc.clearAll();
        nc.addTransportType(NetworkCapabilities.TRANSPORT_WIFI_AWARE);
        nc.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).addCapability(
                NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
        nc.setNetworkSpecifier(ns);
        nc.setLinkUpstreamBandwidthKbps(1);
        nc.setLinkDownstreamBandwidthKbps(1);
        nc.setSignalStrength(1);

        return new NetworkRequest(nc, 0, 0, NetworkRequest.Type.NONE);
    }

    private NetworkRequest getDirectNetworkRequest(int clientId, int role, byte[] peer,
            byte[] pmk) throws Exception {
        final ConfigRequest configRequest = new ConfigRequest.Builder().build();
        final WifiAwareManager mgr = new WifiAwareManager(mMockContext, mMockAwareService);

        ArgumentCaptor<WifiAwareSession> sessionCaptor = ArgumentCaptor.forClass(
                WifiAwareSession.class);
        ArgumentCaptor<IWifiAwareEventCallback> clientProxyCallback = ArgumentCaptor
                .forClass(IWifiAwareEventCallback.class);

        AttachCallback mockCallback = mock(AttachCallback.class);

        mgr.attach(mMockLooperHandler, configRequest, mockCallback, null);
        verify(mMockAwareService).connect(any(), any(),
                clientProxyCallback.capture(), eq(configRequest), eq(false));
        clientProxyCallback.getValue().onConnectSuccess(clientId);
        mMockLooper.dispatchAll();
        verify(mockCallback).onAttached(sessionCaptor.capture());

        NetworkSpecifier ns;
        if (pmk == null) {
            ns = sessionCaptor.getValue().createNetworkSpecifierOpen(role, peer);
        } else {
            ns = sessionCaptor.getValue().createNetworkSpecifierPmk(role, peer, pmk);
        }
        NetworkCapabilities nc = new NetworkCapabilities();
        nc.clearAll();
        nc.addTransportType(NetworkCapabilities.TRANSPORT_WIFI_AWARE);
        nc.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).addCapability(
                NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
        nc.setNetworkSpecifier(ns);
        nc.setLinkUpstreamBandwidthKbps(1);
        nc.setLinkDownstreamBandwidthKbps(1);
        nc.setSignalStrength(1);

        return new NetworkRequest(nc, 0, 0, NetworkRequest.Type.REQUEST);
    }

    private Pair<Integer, Messenger> initDataPathEndPoint(int clientId, int pubSubId,
            PeerHandle peerHandle, byte[] peerDiscoveryMac, InOrder inOrder,
            boolean doPublish)
            throws Exception {
        final int uid = 1000;
        final int pid = 2000;
        final String callingPackage = "com.android.somePackage";
        final String someMsg = "some arbitrary message from peer";
        final ConfigRequest configRequest = new ConfigRequest.Builder().build();
        final PublishConfig publishConfig = new PublishConfig.Builder().build();
        final SubscribeConfig subscribeConfig = new SubscribeConfig.Builder().build();

        Capabilities capabilities = new Capabilities();
        capabilities.maxNdiInterfaces = 1;

        ArgumentCaptor<Short> transactionId = ArgumentCaptor.forClass(Short.class);
        ArgumentCaptor<Integer> sessionId = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Messenger> messengerCaptor = ArgumentCaptor.forClass(Messenger.class);
        ArgumentCaptor<String> strCaptor = ArgumentCaptor.forClass(String.class);

        // (0) start/registrations
        inOrder.verify(mMockCm).registerNetworkFactory(messengerCaptor.capture(),
                strCaptor.capture());
        collector.checkThat("factory name", "WIFI_AWARE_FACTORY", equalTo(strCaptor.getValue()));

        // (1) get capabilities
        mDut.queryCapabilities();
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).getCapabilities(transactionId.capture());
        mDut.onCapabilitiesUpdateResponse(transactionId.getValue(), capabilities);
        mMockLooper.dispatchAll();

        // (2) enable usage (creates interfaces)
        mDut.enableUsage();
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).createAwareNetworkInterface(transactionId.capture(),
                strCaptor.capture());
        collector.checkThat("interface created -- 0", sAwareInterfacePrefix + 0,
                equalTo(strCaptor.getValue()));
        mDut.onCreateDataPathInterfaceResponse(transactionId.getValue(), true, 0);
        mMockLooper.dispatchAll();

        // (3) create client & session & rx message
        mDut.connect(clientId, uid, pid, callingPackage, mMockCallback, configRequest, false);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockNative).enableAndConfigure(transactionId.capture(),
                eq(configRequest), eq(false), eq(true));
        mDut.onConfigSuccessResponse(transactionId.getValue());
        mMockLooper.dispatchAll();
        inOrder.verify(mMockCallback).onConnectSuccess(clientId);
        if (doPublish) {
            mDut.publish(clientId, publishConfig, mMockSessionCallback);
        } else {
            mDut.subscribe(clientId, subscribeConfig, mMockSessionCallback);
        }
        mMockLooper.dispatchAll();
        if (doPublish) {
            inOrder.verify(mMockNative).publish(transactionId.capture(), eq(0), eq(publishConfig));
        } else {
            inOrder.verify(mMockNative).subscribe(transactionId.capture(), eq(0),
                    eq(subscribeConfig));
        }
        mDut.onSessionConfigSuccessResponse(transactionId.getValue(), doPublish, pubSubId);
        mMockLooper.dispatchAll();
        inOrder.verify(mMockSessionCallback).onSessionStarted(sessionId.capture());
        mDut.onMessageReceivedNotification(pubSubId, peerHandle.peerId, peerDiscoveryMac,
                someMsg.getBytes());
        mMockLooper.dispatchAll();
        inOrder.verify(mMockSessionCallback).onMessageReceived(peerHandle.peerId,
                someMsg.getBytes());

        return new Pair<>(sessionId.getValue(), messengerCaptor.getValue());
    }
}
