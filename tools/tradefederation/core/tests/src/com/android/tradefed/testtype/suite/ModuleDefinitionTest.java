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
package com.android.tradefed.testtype.suite;

import static org.junit.Assert.*;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.command.remote.DeviceDescriptor;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.ITargetCleaner;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;

import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Unit tests for {@link ModuleDefinition} */
@RunWith(JUnit4.class)
public class ModuleDefinitionTest {

    private static final String MODULE_NAME = "fakeName";
    private ModuleDefinition mModule;
    private List<IRemoteTest> mTestList;
    private ITestInterface mMockTest;
    private ITargetPreparer mMockPrep;
    private ITargetCleaner mMockCleaner;
    private List<ITargetPreparer> mTargetPrepList;
    private ITestInvocationListener mMockListener;
    private IBuildInfo mMockBuildInfo;
    private ITestDevice mMockDevice;

    private interface ITestInterface extends IRemoteTest, IBuildReceiver, IDeviceTest {}

    /** Test implementation that allows us to exercise different use cases * */
    private class TestObject implements ITestInterface {

        private ITestDevice mDevice;
        private String mRunName;
        private int mNumTest;
        private boolean mShouldThrow;

        public TestObject(String runName, int numTest, boolean shouldThrow) {
            mRunName = runName;
            mNumTest = numTest;
            mShouldThrow = shouldThrow;
        }

        @Override
        public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
            listener.testRunStarted(mRunName, mNumTest);
            for (int i = 0; i < mNumTest; i++) {
                TestIdentifier test = new TestIdentifier(mRunName + "class", "test" + i);
                listener.testStarted(test);
                if (mShouldThrow && i == mNumTest / 2) {
                    throw new DeviceNotAvailableException();
                }
                listener.testEnded(test, Collections.emptyMap());
            }
            listener.testRunEnded(0, Collections.emptyMap());
        }

        @Override
        public void setBuild(IBuildInfo buildInfo) {
            // ignore
        }

        @Override
        public void setDevice(ITestDevice device) {
            mDevice = device;
        }

        @Override
        public ITestDevice getDevice() {
            return mDevice;
        }
    }

    @Before
    public void setUp() {
        mMockListener = EasyMock.createMock(ITestInvocationListener.class);
        mTestList = new ArrayList<>();
        mMockTest = EasyMock.createMock(ITestInterface.class);
        mTestList.add(mMockTest);
        mTargetPrepList = new ArrayList<>();
        mMockPrep = EasyMock.createMock(ITargetPreparer.class);
        mMockCleaner = EasyMock.createMock(ITargetCleaner.class);
        mTargetPrepList.add(mMockPrep);
        mTargetPrepList.add(mMockCleaner);
        mMockBuildInfo = EasyMock.createMock(IBuildInfo.class);
        mMockDevice = EasyMock.createMock(ITestDevice.class);
        mModule = new ModuleDefinition(MODULE_NAME, mTestList, mTargetPrepList);
    }

    /**
     * Helper for replaying mocks.
     */
    private void replayMocks() {
        EasyMock.replay(mMockListener);
        for (IRemoteTest test : mTestList) {
            EasyMock.replay(test);
        }
        for (ITargetPreparer prep : mTargetPrepList) {
            try {
                EasyMock.replay(prep);
            } catch (IllegalArgumentException e) {
                // ignore
            }
        }
    }

    /**
     * Helper for verifying mocks.
     */
    private void verifyMocks() {
        EasyMock.verify(mMockListener);
        for (IRemoteTest test : mTestList) {
            EasyMock.verify(test);
        }
        for (ITargetPreparer prep : mTargetPrepList) {
            try {
                EasyMock.verify(prep);
            } catch (IllegalArgumentException e) {
                // ignore
            }
        }
    }

    /**
     * Test that {@link ModuleDefinition#run(ITestInvocationListener)} is properly going through the
     * execution flow.
     */
    @Test
    public void testRun() throws Exception {
        mModule.setBuild(mMockBuildInfo);
        mModule.setDevice(mMockDevice);
        mMockPrep.setUp(EasyMock.eq(mMockDevice), EasyMock.eq(mMockBuildInfo));
        mMockCleaner.setUp(EasyMock.eq(mMockDevice), EasyMock.eq(mMockBuildInfo));
        mMockTest.setBuild(EasyMock.eq(mMockBuildInfo));
        mMockTest.setDevice(EasyMock.eq(mMockDevice));
        mMockTest.run((ITestInvocationListener)EasyMock.anyObject());
        mMockCleaner.tearDown(EasyMock.eq(mMockDevice), EasyMock.eq(mMockBuildInfo),
                EasyMock.isNull());
        mMockListener.testRunStarted(MODULE_NAME, 0);
        mMockListener.testRunEnded(EasyMock.anyLong(), EasyMock.anyObject());
        replayMocks();
        mModule.run(mMockListener);
        verifyMocks();
    }

    /**
     * Test that {@link ModuleDefinition#run(ITestInvocationListener)}
     */
    @Test
    public void testRun_failPreparation() throws Exception {
        final String exceptionMessage = "ouch I failed";
        mTargetPrepList.clear();
        mTargetPrepList.add(new ITargetPreparer() {
            @Override
            public void setUp(ITestDevice device, IBuildInfo buildInfo)
                    throws TargetSetupError, BuildError, DeviceNotAvailableException {
                DeviceDescriptor nullDescriptor = null;
                throw new TargetSetupError(exceptionMessage, nullDescriptor);
            }
        });
        mModule = new ModuleDefinition(MODULE_NAME, mTestList, mTargetPrepList);
        mMockCleaner.tearDown(EasyMock.eq(mMockDevice), EasyMock.eq(mMockBuildInfo),
                EasyMock.isNull());
        mMockListener.testRunStarted(EasyMock.eq(MODULE_NAME), EasyMock.eq(1));
        mMockListener.testStarted(EasyMock.anyObject());
        mMockListener.testFailed(EasyMock.anyObject(), EasyMock.contains(exceptionMessage));
        mMockListener.testEnded(EasyMock.anyObject(), EasyMock.anyObject());
        mMockListener.testRunFailed(EasyMock.contains(exceptionMessage));
        mMockListener.testRunEnded(EasyMock.anyLong(), EasyMock.anyObject());
        replayMocks();
        mModule.run(mMockListener);
        verifyMocks();
    }

    /**
     * Test that {@link ModuleDefinition#run(ITestInvocationListener)} is properly going through the
     * execution flow with actual test callbacks.
     */
    @Test
    public void testRun_fullPass() throws Exception {
        final int testCount = 5;
        List<IRemoteTest> testList = new ArrayList<>();
        testList.add(new TestObject("run1", testCount, false));
        mModule = new ModuleDefinition(MODULE_NAME, testList, mTargetPrepList);
        mModule.setBuild(mMockBuildInfo);
        mModule.setDevice(mMockDevice);
        mMockPrep.setUp(EasyMock.eq(mMockDevice), EasyMock.eq(mMockBuildInfo));
        mMockCleaner.setUp(EasyMock.eq(mMockDevice), EasyMock.eq(mMockBuildInfo));
        mMockCleaner.tearDown(
                EasyMock.eq(mMockDevice), EasyMock.eq(mMockBuildInfo), EasyMock.isNull());
        mMockListener.testRunStarted(MODULE_NAME, testCount);
        for (int i = 0; i < testCount; i++) {
            mMockListener.testStarted((TestIdentifier) EasyMock.anyObject(), EasyMock.anyLong());
            mMockListener.testEnded(
                    (TestIdentifier) EasyMock.anyObject(),
                    EasyMock.anyLong(),
                    EasyMock.anyObject());
        }
        mMockListener.testRunEnded(EasyMock.anyLong(), EasyMock.anyObject());
        replayMocks();
        mModule.run(mMockListener);
        verifyMocks();
    }

    /**
     * Test that {@link ModuleDefinition#run(ITestInvocationListener)} is properly going through the
     * execution flow with actual test callbacks.
     */
    @Test
    public void testRun_partialRun() throws Exception {
        final int testCount = 4;
        List<IRemoteTest> testList = new ArrayList<>();
        testList.add(new TestObject("run1", testCount, true));
        mModule = new ModuleDefinition(MODULE_NAME, testList, mTargetPrepList);
        mModule.setBuild(mMockBuildInfo);
        mModule.setDevice(mMockDevice);
        mMockPrep.setUp(EasyMock.eq(mMockDevice), EasyMock.eq(mMockBuildInfo));
        mMockCleaner.setUp(EasyMock.eq(mMockDevice), EasyMock.eq(mMockBuildInfo));
        mMockCleaner.tearDown(
                EasyMock.eq(mMockDevice), EasyMock.eq(mMockBuildInfo), EasyMock.isNull());
        EasyMock.expectLastCall().andThrow(new DeviceNotAvailableException());
        mMockListener.testRunStarted(MODULE_NAME, testCount);
        for (int i = 0; i < 3; i++) {
            mMockListener.testStarted((TestIdentifier) EasyMock.anyObject(), EasyMock.anyLong());
            mMockListener.testEnded(
                    (TestIdentifier) EasyMock.anyObject(),
                    EasyMock.anyLong(),
                    EasyMock.anyObject());
        }
        mMockListener.testFailed(EasyMock.anyObject(), EasyMock.anyObject());
        mMockListener.testRunFailed(EasyMock.anyObject());
        mMockListener.testRunEnded(EasyMock.anyLong(), EasyMock.anyObject());
        replayMocks();
        try {
            mModule.run(mMockListener);
            fail("Should have thrown an exception.");
        } catch (DeviceNotAvailableException expected) {
            // expected
        }
        // Only one module
        assertEquals(1, mModule.getTestsResults().size());
        assertEquals(2, mModule.getTestsResults().get(0).getNumCompleteTests());
        verifyMocks();
    }
}
