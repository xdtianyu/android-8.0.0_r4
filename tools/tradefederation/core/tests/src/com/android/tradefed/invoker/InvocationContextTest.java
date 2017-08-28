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
package com.android.tradefed.invoker;

import static org.junit.Assert.*;

import com.android.tradefed.device.ITestDevice;

import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link InvocationContext} */
@RunWith(JUnit4.class)
public class InvocationContextTest {

    private InvocationContext mContext;

    @Before
    public void setUp() {
        mContext = new InvocationContext();
    }

    /** Test the reverse look up of the device name in the configuration for an ITestDevice */
    @Test
    public void testGetDeviceName() {
        ITestDevice device1 = EasyMock.createMock(ITestDevice.class);
        ITestDevice device2 = EasyMock.createMock(ITestDevice.class);
        // assert that at init, map is empty.
        assertNull(mContext.getDeviceName(device1));
        mContext.addAllocatedDevice("test1", device1);
        assertEquals("test1", mContext.getDeviceName(device1));
        assertNull(mContext.getDeviceName(device2));
    }
}
