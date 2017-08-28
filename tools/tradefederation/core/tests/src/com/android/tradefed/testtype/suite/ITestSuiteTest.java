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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.ConfigurationFactory;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.DeviceUnresponsiveException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ByteArrayInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.suite.checker.ISystemStatusChecker;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.StubTest;

import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Unit tests for {@link ITestSuite}
 */
@RunWith(JUnit4.class)
public class ITestSuiteTest {

    private static final String EMPTY_CONFIG = "empty";
    private static final String TEST_CONFIG_NAME = "test";

    private TestSuiteImpl mTestSuite;
    private ITestInvocationListener mMockListener;
    private ITestDevice mMockDevice;
    private IBuildInfo mMockBuildInfo;
    private ISystemStatusChecker mMockSysChecker;

    /**
     * Very basic implementation of {@link ITestSuite} to test it.
     */
    static class TestSuiteImpl extends ITestSuite {
        private int mNumTests = 1;

        public TestSuiteImpl() {}

        public TestSuiteImpl(int numTests) {
            mNumTests = numTests;
        }

        @Override
        public LinkedHashMap<String, IConfiguration> loadTests() {
            LinkedHashMap<String, IConfiguration> testConfig = new LinkedHashMap<>();
            try {
                IConfiguration config =
                        ConfigurationFactory.getInstance()
                                .createConfigurationFromArgs(new String[] {EMPTY_CONFIG});
                config.setTest(new StubCollectingTest());
                testConfig.put(TEST_CONFIG_NAME, config);

                for (int i = 1; i < mNumTests; i++) {
                    IConfiguration extraConfig =
                            ConfigurationFactory.getInstance()
                                    .createConfigurationFromArgs(new String[] {EMPTY_CONFIG});
                    extraConfig.setTest(new StubTest());
                    testConfig.put(TEST_CONFIG_NAME + i, extraConfig);
                }
            } catch (ConfigurationException e) {
                CLog.e(e);
                throw new RuntimeException(e);
            }
            return testConfig;
        }
    }

    public static class StubCollectingTest implements IRemoteTest {
        private DeviceNotAvailableException mException;
        private RuntimeException mRunException;

        public StubCollectingTest() {}

        public StubCollectingTest(DeviceNotAvailableException e) {
            mException = e;
        }

        public StubCollectingTest(RuntimeException e) {
            mRunException = e;
        }

        @Override
        public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
            listener.testRunStarted(TEST_CONFIG_NAME, 1);
            try {
            if (mException != null) {
                throw mException;
            }
                if (mRunException != null) {
                    throw mRunException;
                }
                TestIdentifier test = new TestIdentifier(EMPTY_CONFIG, EMPTY_CONFIG);
                listener.testStarted(test, 0);
                listener.testEnded(test, 5, Collections.emptyMap());
            } finally {
                listener.testRunEnded(0, Collections.emptyMap());
            }
        }
    }

    @Before
    public void setUp() {
        mTestSuite = new TestSuiteImpl();
        mMockListener = EasyMock.createMock(ITestInvocationListener.class);
        mMockDevice = EasyMock.createMock(ITestDevice.class);
        EasyMock.expect(mMockDevice.getSerialNumber()).andStubReturn("SERIAL");
        mMockBuildInfo = EasyMock.createMock(IBuildInfo.class);
        mMockSysChecker = EasyMock.createMock(ISystemStatusChecker.class);
        mTestSuite.setDevice(mMockDevice);
        mTestSuite.setBuild(mMockBuildInfo);
    }

    /**
     * Helper for replaying mocks.
     */
    private void replayMocks() {
        EasyMock.replay(mMockListener, mMockDevice, mMockBuildInfo, mMockSysChecker);
    }

    /**
     * Helper for verifying mocks.
     */
    private void verifyMocks() {
        EasyMock.verify(mMockListener, mMockDevice, mMockBuildInfo, mMockSysChecker);
    }

    /** Helper to expect the test run callback. */
    private void expectTestRun(ITestInvocationListener listener) {
        listener.testRunStarted(TEST_CONFIG_NAME, 1);
        TestIdentifier test = new TestIdentifier(EMPTY_CONFIG, EMPTY_CONFIG);
        listener.testStarted(test, 0);
        listener.testEnded(test, 5, Collections.emptyMap());
        listener.testRunEnded(EasyMock.anyLong(), EasyMock.anyObject());
    }

    /** Test for {@link ITestSuite#run(ITestInvocationListener)}. */
    @Test
    public void testRun() throws Exception {
        List<ISystemStatusChecker> sysChecker = new ArrayList<ISystemStatusChecker>();
        sysChecker.add(mMockSysChecker);
        mTestSuite.setSystemStatusChecker(sysChecker);
        EasyMock.expect(mMockSysChecker.preExecutionCheck(EasyMock.eq(mMockDevice)))
                .andReturn(true);
        EasyMock.expect(mMockSysChecker.postExecutionCheck(EasyMock.eq(mMockDevice)))
                .andReturn(true);
        expectTestRun(mMockListener);
        replayMocks();
        mTestSuite.run(mMockListener);
        verifyMocks();
    }

    /**
     * Test for {@link ITestSuite#run(ITestInvocationListener)} when the System status checker is
     * failing.
     */
    @Test
    public void testRun_failedSystemChecker() throws Exception {
        final byte[] fakeData = "fakeData".getBytes();
        InputStreamSource fakeSource = new ByteArrayInputStreamSource(fakeData);
        List<ISystemStatusChecker> sysChecker = new ArrayList<ISystemStatusChecker>();
        sysChecker.add(mMockSysChecker);
        mTestSuite.setSystemStatusChecker(sysChecker);
        EasyMock.expect(mMockSysChecker.preExecutionCheck(EasyMock.eq(mMockDevice)))
                .andReturn(false);
        EasyMock.expect(mMockDevice.getBugreport()).andReturn(fakeSource).times(2);
        mMockListener.testLog((String)EasyMock.anyObject(), EasyMock.eq(LogDataType.BUGREPORT),
                EasyMock.eq(fakeSource));
        EasyMock.expectLastCall().times(2);
        EasyMock.expect(mMockSysChecker.postExecutionCheck(EasyMock.eq(mMockDevice)))
                .andReturn(false);
        expectTestRun(mMockListener);
        replayMocks();
        mTestSuite.run(mMockListener);
        verifyMocks();
    }

    /**
     * Test for {@link ITestSuite#run(ITestInvocationListener)} when the System status checker is
     * passing pre-check but failing post-check and we enable reporting a failure for it.
     */
    @Test
    public void testRun_failedSystemChecker_reportFailure() throws Exception {
        OptionSetter setter = new OptionSetter(mTestSuite);
        setter.setOptionValue("report-system-checkers", "true");
        final byte[] fakeData = "fakeData".getBytes();
        InputStreamSource fakeSource = new ByteArrayInputStreamSource(fakeData);
        List<ISystemStatusChecker> sysChecker = new ArrayList<ISystemStatusChecker>();
        sysChecker.add(mMockSysChecker);
        mTestSuite.setSystemStatusChecker(sysChecker);
        EasyMock.expect(mMockSysChecker.preExecutionCheck(EasyMock.eq(mMockDevice)))
                .andReturn(true);
        EasyMock.expect(mMockDevice.getBugreport()).andReturn(fakeSource).times(1);
        mMockListener.testLog(
                (String) EasyMock.anyObject(),
                EasyMock.eq(LogDataType.BUGREPORT),
                EasyMock.eq(fakeSource));
        EasyMock.expectLastCall().times(1);
        EasyMock.expect(mMockSysChecker.postExecutionCheck(EasyMock.eq(mMockDevice)))
                .andReturn(false);
        expectTestRun(mMockListener);

        mMockListener.testRunStarted(ITestSuite.MODULE_CHECKER_PRE + "_test", 0);
        mMockListener.testRunEnded(EasyMock.anyLong(), EasyMock.anyObject());

        mMockListener.testRunStarted(ITestSuite.MODULE_CHECKER_POST + "_test", 0);
        mMockListener.testRunFailed(EasyMock.anyObject());
        mMockListener.testRunEnded(EasyMock.anyLong(), EasyMock.anyObject());

        replayMocks();
        mTestSuite.run(mMockListener);
        verifyMocks();
    }

    /**
     * Test for {@link ITestSuite#run(ITestInvocationListener)} when the System status checker is
     * disabled and we request reboot before module run.
     */
    @Test
    public void testRun_rebootBeforeModule() throws Exception {
        OptionSetter setter = new OptionSetter(mTestSuite);
        setter.setOptionValue("skip-all-system-status-check", "true");
        setter.setOptionValue("reboot-per-module", "true");
        EasyMock.expect(mMockDevice.getProperty("ro.build.type")).andReturn("userdebug");
        mMockDevice.reboot();
        expectTestRun(mMockListener);
        replayMocks();
        mTestSuite.run(mMockListener);
        verifyMocks();
    }

    /**
     * Test for {@link ITestSuite#run(ITestInvocationListener)} when the test throw an
     * unresponsive device exception. The run can continue since device has been recovered in this
     * case.
     */
    @Test
    public void testRun_unresponsiveDevice() throws Exception {
        mTestSuite =
                new TestSuiteImpl() {
                    @Override
                    public LinkedHashMap<String, IConfiguration> loadTests() {
                        LinkedHashMap<String, IConfiguration> testConfig = new LinkedHashMap<>();
                        try {
                            IConfiguration fake =
                                    ConfigurationFactory.getInstance()
                                            .createConfigurationFromArgs(
                                                    new String[] {EMPTY_CONFIG});
                            fake.setTest(new StubCollectingTest(new DeviceUnresponsiveException()));
                            testConfig.put(TEST_CONFIG_NAME, fake);
                        } catch (ConfigurationException e) {
                            CLog.e(e);
                            throw new RuntimeException(e);
                        }
                        return testConfig;
                    }
                };
        mTestSuite.setDevice(mMockDevice);
        mTestSuite.setBuild(mMockBuildInfo);
        OptionSetter setter = new OptionSetter(mTestSuite);
        setter.setOptionValue("skip-all-system-status-check", "true");
        setter.setOptionValue("reboot-per-module", "true");
        EasyMock.expect(mMockDevice.getProperty("ro.build.type")).andReturn("user");
        mMockListener.testRunStarted(TEST_CONFIG_NAME, 1);
        EasyMock.expectLastCall().times(1);
        mMockListener.testRunFailed("Module test only ran 0 out of 1 expected tests.");
        mMockListener.testRunEnded(EasyMock.anyLong(), EasyMock.anyObject());
        EasyMock.expectLastCall().times(1);
        replayMocks();
        mTestSuite.run(mMockListener);
        verifyMocks();
    }

    /**
     * Test for {@link ITestSuite#run(ITestInvocationListener)} when the test throw a runtime
     * exception. The run can continue in this case.
     */
    @Test
    public void testRun_runtimeException() throws Exception {
        mTestSuite =
                new TestSuiteImpl() {
                    @Override
                    public LinkedHashMap<String, IConfiguration> loadTests() {
                        LinkedHashMap<String, IConfiguration> testConfig = new LinkedHashMap<>();
                        try {
                            IConfiguration fake =
                                    ConfigurationFactory.getInstance()
                                            .createConfigurationFromArgs(
                                                    new String[] {EMPTY_CONFIG});
                            fake.setTest(new StubCollectingTest(new RuntimeException()));
                            testConfig.put(TEST_CONFIG_NAME, fake);
                        } catch (ConfigurationException e) {
                            CLog.e(e);
                            throw new RuntimeException(e);
                        }
                        return testConfig;
                    }
                };
        mTestSuite.setDevice(mMockDevice);
        mTestSuite.setBuild(mMockBuildInfo);
        OptionSetter setter = new OptionSetter(mTestSuite);
        setter.setOptionValue("skip-all-system-status-check", "true");
        setter.setOptionValue("reboot-per-module", "true");
        EasyMock.expect(mMockDevice.getProperty("ro.build.type")).andReturn("user");
        mMockListener.testRunStarted(TEST_CONFIG_NAME, 1);
        EasyMock.expectLastCall().times(1);
        mMockListener.testRunFailed("Module test only ran 0 out of 1 expected tests.");
        mMockListener.testRunEnded(EasyMock.anyLong(), EasyMock.anyObject());
        EasyMock.expectLastCall().times(1);
        replayMocks();
        mTestSuite.run(mMockListener);
        verifyMocks();
    }

    /**
     * Test for {@link ITestSuite#split(int)} for modules that are not shardable. We end up with a
     * list of all tests. Note that the shardCountHint of 3 in this case does not drive the final
     * number of tests.
     */
    @Test
    public void testShardModules_notShardable() {
        mTestSuite = new TestSuiteImpl(5);
        Collection<IRemoteTest> tests = mTestSuite.split(3);
        assertEquals(5, tests.size());
        for (IRemoteTest test : tests) {
            assertTrue(test instanceof TestSuiteImpl);
        }
    }

    /** Test that when splitting a single non-splitable test we end up with only one IRemoteTest. */
    @Test
    public void testGetTestShard_onlyOneTest() {
        Collection<IRemoteTest> tests = mTestSuite.split(2);
        assertEquals(1, tests.size());
        for (IRemoteTest test : tests) {
            assertTrue(test instanceof TestSuiteImpl);
        }
    }
}
