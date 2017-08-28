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
package com.android.tradefed.testtype.suite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.ConfigurationFactory;
import com.android.tradefed.config.DeviceConfigurationHolder;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IDeviceConfiguration;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.IRescheduler;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.invoker.shard.StrictShardHelper;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ILogSaver;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.ResultForwarder;
import com.android.tradefed.result.suite.SuiteResultReporter;
import com.android.tradefed.suite.checker.ISystemStatusChecker;
import com.android.tradefed.suite.checker.ISystemStatusCheckerReceiver;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.FileUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Tests an {@link ITestSuite} end-to-end: A lesson learnt from CTS is that we need to ensure final
 * results seen by the top level {@link ITestInvocationListener} needs to be understood and
 * predictable for all sort of scenario.
 */
@RunWith(JUnit4.class)
public class ITestSuiteIntegrationTest {

    private static final String CONFIG =
            "<configuration description=\"Auto Generated File\">\n"
                    + "<test class=\"com.android.tradefed.testtype.suite.%s\">\n"
                    + "    <option name=\"report-test\" value=\"%s\" />\n"
                    + "    <option name=\"run-complete\" value=\"%s\" />\n"
                    + "    <option name=\"test-fail\" value=\"%s\" />\n"
                    + "    <option name=\"internal-retry\" value=\"%s\" />\n"
                    + "    <option name=\"throw-device-not-available\" value=\"%s\" />\n"
                    + "</test>\n"
                    + "</configuration>";
    private static final String FILENAME = "%s.config";
    private static final String TEST_STUB = "TestSuiteStub"; // Trivial test stub

    private File mTestConfigFolder;
    private ITestDevice mMockDevice;
    private IBuildInfo mMockBuildInfo;
    private SuiteResultReporter mListener;
    private IInvocationContext mContext;

    /**
     * Create a CTS configuration with a fake tests to exercise all cases.
     *
     * @param testsDir The testcases/ dir where to put the module
     * @param name the name of the module.
     * @param moduleClass the fake test class to use.
     * @param reportTest True if the test report some tests
     * @param runComplete True if the test run is complete
     * @param doesOneTestFail True if one of the test is going to fail
     * @param internalRetry True if the test will retry the module itself once
     * @param throwEx True if the module is going to throw a {@link DeviceNotAvailableException}.
     */
    private void createConfig(
            File testsDir,
            String name,
            String moduleClass,
            boolean reportTest,
            boolean runComplete,
            boolean doesOneTestFail,
            boolean internalRetry,
            boolean throwEx)
            throws IOException {
        File config = new File(testsDir, String.format(FILENAME, name));
        FileUtil.deleteFile(config);
        if (!config.createNewFile()) {
            throw new IOException(String.format("Failed to create '%s'", config.getAbsolutePath()));
        }

        FileUtil.writeToFile(
                String.format(
                        CONFIG,
                        moduleClass,
                        reportTest,
                        runComplete,
                        doesOneTestFail,
                        internalRetry,
                        throwEx),
                config);
    }

    @Before
    public void setUp() throws IOException {
        mTestConfigFolder = FileUtil.createTempDir("suite-integration");
        mMockDevice = mock(ITestDevice.class);
        mMockBuildInfo = mock(IBuildInfo.class);
        mListener = new SuiteResultReporter();
        mContext = new InvocationContext();
        mContext.addAllocatedDevice("device", mMockDevice);
        mContext.addDeviceBuildInfo("device", mMockBuildInfo);
    }

    @After
    public void tearDown() {
        FileUtil.recursiveDelete(mTestConfigFolder);
    }

    /** Very basic implementation of {@link ITestSuite} to load the config from the folder */
    public static class TestSuiteFolderImpl extends ITestSuite {

        private File mConfigFolder;
        private List<TestIdentifier> mTests;

        public TestSuiteFolderImpl() {}

        public TestSuiteFolderImpl(File configFolder) {
            mConfigFolder = configFolder;
        }

        public TestSuiteFolderImpl(File configFolder, List<TestIdentifier> tests) {
            this(configFolder);
            mTests = tests;
        }

        @Override
        public LinkedHashMap<String, IConfiguration> loadTests() {
            LinkedHashMap<String, IConfiguration> testConfig = new LinkedHashMap<>();
            List<File> files = Arrays.asList(mConfigFolder.listFiles());
            Collections.sort(files);
            for (File configFile : files) {
                try {
                    IConfiguration config =
                            ConfigurationFactory.getInstance()
                                    .createConfigurationFromArgs(
                                            new String[] {configFile.getAbsolutePath()});
                    testConfig.put(configFile.getName(), config);
                    for (IRemoteTest test : config.getTests()) {
                        if (test instanceof TestSuiteStub) {
                            ((TestSuiteStub) test).mShardedTestToRun = mTests;
                        }
                    }
                } catch (ConfigurationException e) {
                    CLog.e(e);
                    throw new RuntimeException(e);
                }
            }
            return testConfig;
        }
    }

    /** ============================== TESTS ============================== */

    /** Tests that a normal run with 2 modules with 3 tests each reports correctly. */
    @Test
    public void testSimplePassRun() throws Exception {
        createConfig(mTestConfigFolder, "module1", TEST_STUB, true, true, false, false, false);
        createConfig(mTestConfigFolder, "module2", TEST_STUB, true, true, false, false, false);
        ITestSuite suite = new TestSuiteFolderImpl(mTestConfigFolder);
        suite.setDevice(mMockDevice);
        suite.setBuild(mMockBuildInfo);
        suite.setSystemStatusChecker(new ArrayList<ISystemStatusChecker>());
        mListener.invocationStarted(mContext);
        suite.run(mListener);
        mListener.invocationEnded(System.currentTimeMillis());
        // check results
        assertEquals(2, mListener.getTotalModules());
        assertEquals(2, mListener.getCompleteModules());
        assertEquals(6, mListener.getTotalTests());
        assertEquals(6, mListener.getPassedTests());
        assertEquals(0, mListener.getFailedTests());
    }

    /** Tests that a normal run with 2 modules with 3 tests each, 1 failed test in second module. */
    @Test
    public void testSimpleRun_withFail() throws Exception {
        createConfig(mTestConfigFolder, "module1", TEST_STUB, true, true, false, false, false);
        createConfig(mTestConfigFolder, "module2", TEST_STUB, true, true, true, false, false);
        ITestSuite suite = new TestSuiteFolderImpl(mTestConfigFolder);
        suite.setDevice(mMockDevice);
        suite.setBuild(mMockBuildInfo);
        suite.setSystemStatusChecker(new ArrayList<ISystemStatusChecker>());
        mListener.invocationStarted(mContext);
        suite.run(mListener);
        mListener.invocationEnded(System.currentTimeMillis());
        // check results
        assertEquals(2, mListener.getTotalModules());
        assertEquals(2, mListener.getCompleteModules());
        assertEquals(6, mListener.getTotalTests());
        assertEquals(5, mListener.getPassedTests());
        assertEquals(1, mListener.getFailedTests());
    }

    /**
     * Tests that a normal run with 2 modules with 3 tests each but only one module reports all its
     * test, the other one is missing one.
     */
    @Test
    public void testRun_incomplete() throws Exception {
        createConfig(mTestConfigFolder, "module1", TEST_STUB, true, true, false, false, false);
        createConfig(mTestConfigFolder, "module2", TEST_STUB, true, false, false, false, false);
        ITestSuite suite = new TestSuiteFolderImpl(mTestConfigFolder);
        suite.setDevice(mMockDevice);
        suite.setBuild(mMockBuildInfo);
        suite.setSystemStatusChecker(new ArrayList<ISystemStatusChecker>());
        mListener.invocationStarted(mContext);
        suite.run(mListener);
        mListener.invocationEnded(System.currentTimeMillis());
        // check results
        assertEquals(2, mListener.getTotalModules());
        assertEquals(1, mListener.getCompleteModules());
        assertEquals(6, mListener.getTotalTests());
        assertEquals(5, mListener.getPassedTests());
        assertEquals(0, mListener.getFailedTests());
    }

    /**
     * Test that when a module throw a {@link DeviceNotAvailableException} the subsequent modules
     * are reported but skipped.
     */
    @Test
    public void testRun_DeviceNotAvailable() throws Exception {
        createConfig(mTestConfigFolder, "module1", TEST_STUB, true, true, false, false, true);
        createConfig(mTestConfigFolder, "module2", TEST_STUB, true, true, false, false, false);
        ITestSuite suite = new TestSuiteFolderImpl(mTestConfigFolder);
        suite.setDevice(mMockDevice);
        suite.setBuild(mMockBuildInfo);
        suite.setSystemStatusChecker(new ArrayList<ISystemStatusChecker>());
        mListener.invocationStarted(mContext);
        try {
            suite.run(mListener);
            fail("Should have thrown an exception");
        } catch (DeviceNotAvailableException expected) {
            // expected
        }
        mListener.invocationEnded(System.currentTimeMillis());
        // check results
        assertEquals(2, mListener.getTotalModules());
        assertEquals(0, mListener.getCompleteModules());
        assertEquals(3, mListener.getTotalTests());
        assertEquals(1, mListener.getPassedTests());
        assertEquals(1, mListener.getFailedTests());
    }

    /** ===================== TESTS WITH SHARDING ===================== */

    /** Helper rescheduler to simulate one shard running after another. */
    private class TestShardRescheduler implements IRescheduler {
        @Override
        public boolean scheduleConfig(IConfiguration config) {
            new ResultForwarder(config.getTestInvocationListeners()).invocationStarted(mContext);
            for (IRemoteTest test : config.getTests()) {
                if (test instanceof ISystemStatusCheckerReceiver) {
                    ((ISystemStatusCheckerReceiver) test)
                            .setSystemStatusChecker(config.getSystemStatusCheckers());
                }
                try {
                    test.run(new ResultForwarder(config.getTestInvocationListeners()));
                } catch (DeviceNotAvailableException e) {
                    throw new RuntimeException(e);
                }
            }
            new ResultForwarder(config.getTestInvocationListeners()).invocationEnded(500);
            return true;
        }

        @Override
        public boolean rescheduleCommand() {
            throw new RuntimeException("Should not be called.");
        }
    }

    /** Helper rescheduler to simulate all shards running concurrently. */
    private class TestParallelShardRescheduler implements IRescheduler {

        public List<Thread> mRunning = new ArrayList<>();

        @Override
        public boolean scheduleConfig(IConfiguration config) {
            Thread t =
                    new Thread(
                            new Runnable() {
                                @Override
                                public void run() {
                                    new ResultForwarder(config.getTestInvocationListeners())
                                            .invocationStarted(mContext);
                                    for (IRemoteTest test : config.getTests()) {
                                        if (test instanceof ISystemStatusCheckerReceiver) {
                                            ((ISystemStatusCheckerReceiver) test)
                                                    .setSystemStatusChecker(
                                                            config.getSystemStatusCheckers());
                                        }
                                        try {
                                            test.run(
                                                    new ResultForwarder(
                                                            config.getTestInvocationListeners()));
                                        } catch (DeviceNotAvailableException e) {
                                            throw new RuntimeException(e);
                                        }
                                    }
                                    new ResultForwarder(config.getTestInvocationListeners())
                                            .invocationEnded(500);
                                }
                            });
            mRunning.add(t);
            t.setName("shard-integration-test" + config.getName());
            t.start();
            return true;
        }

        @Override
        public boolean rescheduleCommand() {
            throw new RuntimeException("Should not be called.");
        }
    }

    /** Tests running a split ITestSuite. Without parallelism, the first pool will run all tests. */
    @Test
    public void testRun_sharding_firstModuleRunsAll() throws Exception {
        createConfig(mTestConfigFolder, "module1", TEST_STUB, true, true, false, false, false);
        createConfig(mTestConfigFolder, "module2", TEST_STUB, true, true, true, false, false);
        ITestSuite suite = new TestSuiteFolderImpl(mTestConfigFolder);
        IConfiguration config =
                ConfigurationFactory.getInstance()
                        .createConfigurationFromArgs(new String[] {"empty"});
        config.setLogSaver(mock(ILogSaver.class));
        config.setTest(suite);
        config.setSystemStatusCheckers(new ArrayList<ISystemStatusChecker>());
        suite.setSystemStatusChecker(new ArrayList<ISystemStatusChecker>());
        config.setTestInvocationListener(mListener);
        config.getCommandOptions().setShardCount(5);
        IDeviceConfiguration deviceConfig = new DeviceConfigurationHolder("device");
        config.setDeviceConfig(deviceConfig);
        // invocation context
        mMockBuildInfo = new BuildInfo("9999", "test-target");
        mContext = new InvocationContext();
        mContext.addAllocatedDevice("device", mMockDevice);
        mContext.addDeviceBuildInfo("device", mMockBuildInfo);

        StrictShardHelper helper = new StrictShardHelper();
        helper.shardConfig(config, mContext, new TestShardRescheduler());

        assertEquals(2, mListener.getTotalModules());
        assertEquals(2, mListener.getCompleteModules());
        assertEquals(6, mListener.getTotalTests());
        assertEquals(5, mListener.getPassedTests());
        assertEquals(1, mListener.getFailedTests());
    }

    /**
     * Tests running a split ITestSuite. With parallelism when rescheduling, we should still obtain
     * the same final results.
     */
    @Test
    public void testRun_sharding_parallelRun() throws Exception {
        createConfig(mTestConfigFolder, "module1", TEST_STUB, true, true, false, false, false);
        createConfig(mTestConfigFolder, "module2", TEST_STUB, true, true, true, false, false);
        ITestSuite suite = new TestSuiteFolderImpl(mTestConfigFolder);
        IConfiguration config =
                ConfigurationFactory.getInstance()
                        .createConfigurationFromArgs(new String[] {"empty"});
        config.setLogSaver(mock(ILogSaver.class));
        config.setTest(suite);
        config.setSystemStatusCheckers(new ArrayList<ISystemStatusChecker>());
        suite.setSystemStatusChecker(new ArrayList<ISystemStatusChecker>());
        config.setTestInvocationListener(mListener);
        config.getCommandOptions().setShardCount(5);
        IDeviceConfiguration deviceConfig = new DeviceConfigurationHolder("device");
        config.setDeviceConfig(deviceConfig);
        // invocation context
        mMockBuildInfo = new BuildInfo("9999", "test-target");
        mContext = new InvocationContext();
        mContext.addAllocatedDevice("device", mMockDevice);
        mContext.addDeviceBuildInfo("device", mMockBuildInfo);

        StrictShardHelper helper = new StrictShardHelper();
        TestParallelShardRescheduler rescheduler = new TestParallelShardRescheduler();
        helper.shardConfig(config, mContext, rescheduler);
        for (Thread t : rescheduler.mRunning) {
            t.join(2000);
        }

        assertEquals(2, mListener.getTotalModules());
        assertEquals(2, mListener.getCompleteModules());
        assertEquals(6, mListener.getTotalTests());
        assertEquals(5, mListener.getPassedTests());
        assertEquals(1, mListener.getFailedTests());
    }

    /**
     * Test sharding of ITestSuite with shard-count and shard-index and with TF internal sharding
     * (--disable-strict-sharding).
     */
    @Test
    public void testRun_sharding_withIndex() throws Exception {
        createConfig(mTestConfigFolder, "module1", TEST_STUB, true, true, false, false, false);
        createConfig(mTestConfigFolder, "module2", TEST_STUB, true, true, true, false, false);
        ITestSuite suite = new TestSuiteFolderImpl(mTestConfigFolder);
        IConfiguration config =
                ConfigurationFactory.getInstance()
                        .createConfigurationFromArgs(new String[] {"empty"});
        config.setLogSaver(mock(ILogSaver.class));
        config.setTest(suite);
        config.setSystemStatusCheckers(new ArrayList<ISystemStatusChecker>());
        suite.setSystemStatusChecker(new ArrayList<ISystemStatusChecker>());
        config.setTestInvocationListener(mListener);
        config.getCommandOptions().setShardCount(2);
        config.getCommandOptions().setShardIndex(0);
        OptionSetter setter = new OptionSetter(config.getCommandOptions());
        setter.setOptionValue("disable-strict-sharding", "true");
        IDeviceConfiguration deviceConfig = new DeviceConfigurationHolder("device");
        config.setDeviceConfig(deviceConfig);
        // invocation context
        mMockBuildInfo = new BuildInfo("9999", "test-target");
        mContext = new InvocationContext();
        mContext.addAllocatedDevice("device", mMockDevice);
        mContext.addDeviceBuildInfo("device", mMockBuildInfo);

        StrictShardHelper helper = new StrictShardHelper();
        helper.shardConfig(config, mContext, null);
        // rescheduler is not called, execution is in the same invocation.
        new ResultForwarder(config.getTestInvocationListeners()).invocationStarted(mContext);
        for (IRemoteTest test : config.getTests()) {
            if (test instanceof ISystemStatusCheckerReceiver) {
                ((ISystemStatusCheckerReceiver) test)
                        .setSystemStatusChecker(config.getSystemStatusCheckers());
            }
            test.run(new ResultForwarder(config.getTestInvocationListeners()));
        }
        new ResultForwarder(config.getTestInvocationListeners()).invocationEnded(500);
        // Only the first module is ran, which is passing.
        assertEquals(1, mListener.getTotalModules());
        assertEquals(1, mListener.getCompleteModules());
        assertEquals(3, mListener.getTotalTests());
        assertEquals(3, mListener.getPassedTests());
        assertEquals(0, mListener.getFailedTests());
    }

    /**
     * Test when sharding a single module into several shard and requesting only a subset to run.
     */
    @Test
    public void testRun_intraModuleSharding_shard0() throws Exception {
        helperTestShardIndex(2, 0);
        // Only a subpart of the module runs. 2 out of 3 tests.
        assertEquals(1, mListener.getTotalModules());
        assertEquals(1, mListener.getCompleteModules());
        assertEquals(2, mListener.getTotalTests());
        assertEquals(2, mListener.getPassedTests());
        assertEquals(0, mListener.getFailedTests());
    }

    /**
     * Test when sharding a single module into several shard and requesting only a subset to run.
     */
    @Test
    public void testRun_intraModuleSharding_shard1() throws Exception {
        helperTestShardIndex(2, 1);
        // Only a subpart of the module runs. 1 out of 3 tests.
        assertEquals(1, mListener.getTotalModules());
        assertEquals(1, mListener.getCompleteModules());
        assertEquals(1, mListener.getTotalTests());
        assertEquals(1, mListener.getPassedTests());
        assertEquals(0, mListener.getFailedTests());
    }

    private void helperTestShardIndex(int shardCount, int shardIndex) throws Exception {
        List<TestIdentifier> tests = new ArrayList<>();
        tests.add(new TestIdentifier("class1", "test1"));
        tests.add(new TestIdentifier("class1", "test2"));
        tests.add(new TestIdentifier("class1", "test3"));
        createConfig(mTestConfigFolder, "module1", TEST_STUB, true, true, false, false, false);
        ITestSuite suite = new TestSuiteFolderImpl(mTestConfigFolder, tests);
        IConfiguration config =
                ConfigurationFactory.getInstance()
                        .createConfigurationFromArgs(new String[] {"empty"});
        config.setLogSaver(mock(ILogSaver.class));
        config.setTest(suite);
        config.setSystemStatusCheckers(new ArrayList<ISystemStatusChecker>());
        suite.setSystemStatusChecker(new ArrayList<ISystemStatusChecker>());
        config.setTestInvocationListener(mListener);
        config.getCommandOptions().setShardCount(shardCount);
        config.getCommandOptions().setShardIndex(shardIndex);
        OptionSetter setter = new OptionSetter(config.getCommandOptions());
        setter.setOptionValue("disable-strict-sharding", "true");
        IDeviceConfiguration deviceConfig = new DeviceConfigurationHolder("device");
        config.setDeviceConfig(deviceConfig);
        // invocation context
        mMockBuildInfo = new BuildInfo("9999", "test-target");
        mContext = new InvocationContext();
        mContext.addAllocatedDevice("device", mMockDevice);
        mContext.addDeviceBuildInfo("device", mMockBuildInfo);

        StrictShardHelper helper = new StrictShardHelper();
        helper.shardConfig(config, mContext, null);
        // rescheduler is not called, execution is in the same invocation.
        new ResultForwarder(config.getTestInvocationListeners()).invocationStarted(mContext);
        for (IRemoteTest test : config.getTests()) {
            if (test instanceof ISystemStatusCheckerReceiver) {
                ((ISystemStatusCheckerReceiver) test)
                        .setSystemStatusChecker(config.getSystemStatusCheckers());
            }
            test.run(new ResultForwarder(config.getTestInvocationListeners()));
        }
        new ResultForwarder(config.getTestInvocationListeners()).invocationEnded(500);
    }
}
