/**
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

package com.android.cellbroadcastreceiver;

import android.telephony.SmsManager;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.internal.telephony.ISms;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Method;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;


/**
 * Cell broadcast config service tests
 */
public class CellBroadcastConfigServiceTest extends CellBroadcastTest {

    @Mock
    ISms.Stub mSmsService;

    private CellBroadcastConfigService mConfigService;

    private SmsManager mSmsManager = SmsManager.getDefault();

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mConfigService = new CellBroadcastConfigService();

        //mServiceManagerMockedServices.put("isms", mSmsService);
        mMockedServiceManager.replaceService("isms", mSmsService);
        doReturn(mSmsService).when(mSmsService).queryLocalInterface(anyString());
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    private boolean setCellBroadcastRange(boolean enable, int type, int start, int end)
            throws Exception {

        Class[] cArgs = new Class[5];
        cArgs[0] = SmsManager.class;
        cArgs[1] = Boolean.TYPE;
        cArgs[2] = cArgs[3] = cArgs[4] = Integer.TYPE;

        Method method =
                CellBroadcastConfigService.class.getDeclaredMethod("setCellBroadcastRange", cArgs);
        method.setAccessible(true);

        return (boolean) method.invoke(mConfigService, mSmsManager, enable, type, start, end);
    }

    private boolean setCellBroadcastOnSub(int subId, boolean enableForSub)
            throws Exception {

        Class[] cArgs = new Class[3];
        cArgs[0] = SmsManager.class;
        cArgs[1] = Integer.TYPE;
        cArgs[2] = Boolean.TYPE;

        Method method =
                CellBroadcastConfigService.class.getDeclaredMethod("setCellBroadcastOnSub", cArgs);
        method.setAccessible(true);

        return (boolean) method.invoke(mConfigService, mSmsManager, subId, enableForSub);
    }

    /**
     * Test enable cell broadcast range
     */
    @Test
    @SmallTest
    public void testEnableCellBroadcastRange() throws Exception {
        setCellBroadcastRange(true, 0, 10, 20);
        ArgumentCaptor<Integer> captorStart = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> captorEnd = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> captorType = ArgumentCaptor.forClass(Integer.class);

        verify(mSmsService, times(1)).enableCellBroadcastRangeForSubscriber(anyInt(),
                captorStart.capture(), captorEnd.capture(), captorType.capture());

        assertEquals(10, captorStart.getValue().intValue());
        assertEquals(20, captorEnd.getValue().intValue());
        assertEquals(0, captorType.getValue().intValue());
    }

    /**
     * Test disable cell broadcast range
     */
    @Test
    @SmallTest
    public void testDisableCellBroadcastRange() throws Exception {
        setCellBroadcastRange(false, 0, 10, 20);
        ArgumentCaptor<Integer> captorStart = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> captorEnd = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> captorType = ArgumentCaptor.forClass(Integer.class);

        verify(mSmsService, times(1)).disableCellBroadcastRangeForSubscriber(anyInt(),
                captorStart.capture(), captorEnd.capture(), captorType.capture());

        assertEquals(10, captorStart.getValue().intValue());
        assertEquals(20, captorEnd.getValue().intValue());
        assertEquals(0, captorType.getValue().intValue());
    }
}