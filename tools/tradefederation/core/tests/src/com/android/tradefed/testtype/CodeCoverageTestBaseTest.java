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
package com.android.tradefed.testtype;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyCollection;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.android.ddmlib.testrunner.IRemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.ITestRunListener;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.ddmlib.testrunner.TestRunResult;
import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.ITestLogger;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.util.ICompressionStrategy;
import com.android.tradefed.util.ListInstrumentationParser;
import com.android.tradefed.util.ListInstrumentationParser.InstrumentationTarget;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import junit.framework.TestCase;

import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** Unit tests for {@link CodeCoverageTestBase}. */
public class CodeCoverageTestBaseTest extends TestCase {

    private static final String PACKAGE_NAME1 = "com.example.foo.test";
    private static final String PACKAGE_NAME2 = "com.example.bar.test";
    private static final String PACKAGE_NAME3 = "com.example.baz.test";

    private static final String RUNNER_NAME1 = "android.support.test.runner.AndroidJUnitRunner";
    private static final String RUNNER_NAME2 = "android.test.InstrumentationTestRunner";
    private static final String RUNNER_NAME3 = "com.example.custom.Runner";

    private static final TestIdentifier FOO_TEST1 = new TestIdentifier(".FooTest", "test1");
    private static final TestIdentifier FOO_TEST2 = new TestIdentifier(".FooTest", "test2");
    private static final TestIdentifier FOO_TEST3 = new TestIdentifier(".FooTest", "test3");
    private static final List<TestIdentifier> FOO_TESTS =
            Arrays.asList(FOO_TEST1, FOO_TEST2, FOO_TEST3);

    private static final TestIdentifier BAR_TEST1 = new TestIdentifier(".BarTest", "test1");
    private static final TestIdentifier BAR_TEST2 = new TestIdentifier(".BarTest", "test2");
    private static final List<TestIdentifier> BAR_TESTS = Arrays.asList(BAR_TEST1, BAR_TEST2);

    private static final TestIdentifier BAZ_TEST1 = new TestIdentifier(".BazTest", "test1");
    private static final TestIdentifier BAZ_TEST2 = new TestIdentifier(".BazTest", "test2");
    private static final List<TestIdentifier> BAZ_TESTS = Arrays.asList(BAZ_TEST1, BAZ_TEST2);

    private static final File FAKE_COVERAGE_REPORT = new File("/some/fake/report/");

    private static final IBuildInfo BUILD_INFO = new BuildInfo("123456", "device-userdebug");

    static enum FakeReportFormat implements CodeCoverageReportFormat {
        CSV(LogDataType.JACOCO_CSV),
        XML(LogDataType.JACOCO_XML),
        HTML(LogDataType.HTML);

        private LogDataType mLogDataType;

        private FakeReportFormat(LogDataType logDataType) {
            mLogDataType = logDataType;
        }

        @Override
        public LogDataType getLogDataType() { return mLogDataType; }
    }

    /**
     * A subclass of {@link CodeCoverageTest} with certain methods stubbed out for testing.
     */
    static class CodeCoverageTestStub extends CodeCoverageTestBase<FakeReportFormat> {
        private static final Answer<Void> CALL_RUNNER =
                new Answer<Void>() {
                    @Override
                    public Void answer(InvocationOnMock invocation) throws Throwable {
                        Object[] args = invocation.getArguments();
                        ((IRemoteAndroidTestRunner) args[0]).run((ITestRunListener) args[1]);
                        return null;
                    }
                };

        private Map<InstrumentationTarget, List<TestIdentifier>> mTests = new HashMap<>();
        private Map<String, Boolean> mShardingEnabled = new HashMap<>();

        public CodeCoverageTestStub() throws DeviceNotAvailableException {
            // Set up a mock device that simply calls the runner
            ITestDevice device = mock(ITestDevice.class);
            doAnswer(CALL_RUNNER).when(device).runInstrumentationTests(
                    any(IRemoteAndroidTestRunner.class), any(ITestRunListener.class));
            doReturn(true).when(device).doesFileExist(anyString());
            setDevice(device);
        }

        public void addTests(InstrumentationTarget target, Collection<TestIdentifier> tests) {
            mTests.putIfAbsent(target, new ArrayList<TestIdentifier>());
            mTests.get(target).addAll(tests);
        }

        public void setShardingEnabled(String runner, boolean shardingEnabled) {
            mShardingEnabled.put(runner, shardingEnabled);
        }

        @Override
        public IBuildInfo getBuild() { return BUILD_INFO; }

        @Override
        ICompressionStrategy getCompressionStrategy() {
            return mock(ICompressionStrategy.class);
        }

        @Override
        protected File generateCoverageReport(Collection<File> executionDataFiles,
                FakeReportFormat format) {

            return FAKE_COVERAGE_REPORT;
        }

        @Override
        protected List<FakeReportFormat> getReportFormat() {
            return Arrays.asList(FakeReportFormat.HTML);
        }

        @Override
        void doLogReport(String dataName, LogDataType dataType, File data, ITestLogger logger) {
            // Don't actually log anything
        }

        @Override
        CodeCoverageTest internalCreateCoverageTest() {
            return Mockito.spy(super.internalCreateCoverageTest());
        }

        @Override
        IRemoteAndroidTestRunner internalCreateTestRunner(String packageName, String runnerName) {
            // Look up tests for this target
            InstrumentationTarget target = new InstrumentationTarget(packageName, runnerName, "");
            List<TestIdentifier> tests = mTests.getOrDefault(target,
                    new ArrayList<TestIdentifier>());

            // Return a fake AndroidTestRunner
            boolean shardingEnabled = mShardingEnabled.getOrDefault(runnerName, false);
            return Mockito.spy(new FakeTestRunner(packageName, runnerName, tests, shardingEnabled));
        }

        @Override
        ListInstrumentationParser internalCreateListInstrumentationParser() {
            // Return a fake ListInstrumentationParser
            return new ListInstrumentationParser() {
                @Override
                public List<InstrumentationTarget> getInstrumentationTargets() {
                    return new ArrayList<>(mTests.keySet());
                }
            };
        }
    }

    public void testRun() throws DeviceNotAvailableException {
        // Prepare some test data
        InstrumentationTarget target = new InstrumentationTarget(PACKAGE_NAME1, RUNNER_NAME1, "");

        TestRunResult success = mock(TestRunResult.class);
        doReturn(false).when(success).isRunFailure();
        Map<String, String> fakeResultMap = new HashMap<>();
        fakeResultMap.put(CodeCoverageTest.COVERAGE_REMOTE_FILE_LABEL, "fakepath");
        doReturn(fakeResultMap).when(success).getRunMetrics();

        // Mocking boilerplate
        ITestInvocationListener mockListener = mock(ITestInvocationListener.class);
        CodeCoverageTestStub coverageTest = Mockito.spy(new CodeCoverageTestStub());
        coverageTest.addTests(target, FOO_TESTS);

        doReturn(success).when(coverageTest).runTest(any(CodeCoverageTest.class),
                any(ITestInvocationListener.class));

        // Run the test
        coverageTest.run(mockListener);
        // Verify that the test was run, and that the report was logged
        verify(coverageTest).runTest(any(CodeCoverageTest.class),
                any(ITestInvocationListener.class));
        verify(coverageTest).generateCoverageReport(anyCollection(), eq(FakeReportFormat.HTML));
        verify(coverageTest).doLogReport(anyString(), eq(FakeReportFormat.HTML.getLogDataType()),
                eq(FAKE_COVERAGE_REPORT), any(ITestLogger.class));
    }

    public void testRun_multipleInstrumentationTargets() throws DeviceNotAvailableException {
        // Prepare some test data
        InstrumentationTarget target1 = new InstrumentationTarget(PACKAGE_NAME1, RUNNER_NAME1, "");
        InstrumentationTarget target2 = new InstrumentationTarget(PACKAGE_NAME2, RUNNER_NAME1, "");
        InstrumentationTarget target3 = new InstrumentationTarget(PACKAGE_NAME3, RUNNER_NAME1, "");

        TestRunResult success = mock(TestRunResult.class);
        doReturn(false).when(success).isRunFailure();

        // Mocking boilerplate
        ITestInvocationListener mockListener = mock(ITestInvocationListener.class);
        CodeCoverageTestStub coverageTest = Mockito.spy(new CodeCoverageTestStub());
        coverageTest.addTests(target1, FOO_TESTS);
        coverageTest.addTests(target2, BAR_TESTS);
        coverageTest.addTests(target3, BAZ_TESTS);

        doReturn(success).when(coverageTest).runTest(any(CodeCoverageTest.class),
                any(ITestInvocationListener.class));

        // Run the test
        coverageTest.run(mockListener);

        // Verify that all targets were run
        verify(coverageTest).runTest(eq(target1), eq(0), eq(1), any(ITestInvocationListener.class));
        verify(coverageTest).runTest(eq(target2), eq(0), eq(1), any(ITestInvocationListener.class));
        verify(coverageTest).runTest(eq(target3), eq(0), eq(1), any(ITestInvocationListener.class));
    }

    public void testRun_multipleShards() throws DeviceNotAvailableException {
        // Prepare some test data
        InstrumentationTarget target = new InstrumentationTarget(PACKAGE_NAME1, RUNNER_NAME1, "");

        TestRunResult success = mock(TestRunResult.class);
        doReturn(false).when(success).isRunFailure();

        // Mocking boilerplate
        ITestInvocationListener mockListener = mock(ITestInvocationListener.class);
        CodeCoverageTestStub coverageTest = Mockito.spy(new CodeCoverageTestStub());
        coverageTest.addTests(target, FOO_TESTS);
        coverageTest.setShardingEnabled(RUNNER_NAME1, true);

        doReturn(success).when(coverageTest).runTest(any(CodeCoverageTest.class),
                any(ITestInvocationListener.class));

        // Indicate that the test should be split into 3 shards
        int numShards = 3;
        doReturn(numShards).when(coverageTest).getNumberOfShards(any(InstrumentationTarget.class));

        // Run the test
        coverageTest.run(mockListener);

        // Verify that each shard was run
        for (int i = 0; i < numShards; i++) {
            verify(coverageTest).runTest(eq(target), eq(i), eq(numShards),
                    any(ITestInvocationListener.class));
        }
    }

    public void testRun_rerunIndividualTests_failedRun() throws DeviceNotAvailableException {
        // Prepare some test data
        InstrumentationTarget target = new InstrumentationTarget(PACKAGE_NAME1, RUNNER_NAME1, "");

        TestRunResult success = mock(TestRunResult.class);
        doReturn(false).when(success).isRunFailure();
        TestRunResult failure = mock(TestRunResult.class);
        doReturn(true).when(failure).isRunFailure();

        // Mocking boilerplate
        ITestInvocationListener mockListener = mock(ITestInvocationListener.class);
        CodeCoverageTestStub coverageTest = Mockito.spy(new CodeCoverageTestStub());
        coverageTest.addTests(target, FOO_TESTS);

        doReturn(failure).when(coverageTest).runTest(eq(target), eq(0), eq(1),
                any(ITestInvocationListener.class));
        doReturn(success).when(coverageTest).runTest(eq(target), eq(FOO_TEST1),
                any(ITestInvocationListener.class));
        doReturn(failure).when(coverageTest).runTest(eq(target), eq(FOO_TEST2),
                any(ITestInvocationListener.class));
        doReturn(success).when(coverageTest).runTest(eq(target), eq(FOO_TEST3),
                any(ITestInvocationListener.class));

        // Run the test
        coverageTest.run(mockListener);

        // Verify that individual tests are rerun
        verify(coverageTest).runTest(eq(target), eq(0), eq(1), any(ITestInvocationListener.class));
        verify(coverageTest).runTest(eq(target), eq(FOO_TEST1), any(ITestInvocationListener.class));
        verify(coverageTest).runTest(eq(target), eq(FOO_TEST2), any(ITestInvocationListener.class));
        verify(coverageTest).runTest(eq(target), eq(FOO_TEST3), any(ITestInvocationListener.class));
    }

    public void testRun_rerunIndividualTests_missingCoverageFile()
            throws DeviceNotAvailableException {
        // Prepare some test data
        InstrumentationTarget target = new InstrumentationTarget(PACKAGE_NAME1, RUNNER_NAME1, "");

        TestRunResult success = mock(TestRunResult.class);
        doReturn(false).when(success).isRunFailure();

        // Mocking boilerplate
        ITestInvocationListener mockListener = mock(ITestInvocationListener.class);
        CodeCoverageTestStub coverageTest = Mockito.spy(new CodeCoverageTestStub());
        coverageTest.addTests(target, FOO_TESTS);

        doReturn(success).when(coverageTest).runTest(any(CodeCoverageTest.class),
                any(ITestInvocationListener.class));
        ITestDevice mockDevice = coverageTest.getDevice();
        doReturn(false).doReturn(true).when(mockDevice).doesFileExist(anyString());

        // Run the test
        coverageTest.run(mockListener);

        // Verify that individual tests are rerun
        verify(coverageTest).runTest(eq(target), eq(0), eq(1), any(ITestInvocationListener.class));
        verify(coverageTest).runTest(eq(target), eq(FOO_TEST1), any(ITestInvocationListener.class));
        verify(coverageTest).runTest(eq(target), eq(FOO_TEST2), any(ITestInvocationListener.class));
        verify(coverageTest).runTest(eq(target), eq(FOO_TEST3), any(ITestInvocationListener.class));
    }

    public void testRun_multipleFormats() throws DeviceNotAvailableException {
        // Prepare some test data
        InstrumentationTarget target = new InstrumentationTarget(PACKAGE_NAME1, RUNNER_NAME1, "");
        File fakeHtmlReport = FAKE_COVERAGE_REPORT;
        File fakeXmlReport = new File("/some/fake/xml/report.xml");

        TestRunResult success = mock(TestRunResult.class);
        doReturn(false).when(success).isRunFailure();
        Map<String, String> fakeResultMap = new HashMap<>();
        fakeResultMap.put(CodeCoverageTest.COVERAGE_REMOTE_FILE_LABEL, "fakepath");
        doReturn(fakeResultMap).when(success).getRunMetrics();

        // Mocking boilerplate
        ITestInvocationListener mockListener = mock(ITestInvocationListener.class);
        CodeCoverageTestStub coverageTest = Mockito.spy(new CodeCoverageTestStub());
        coverageTest.addTests(target, FOO_TESTS);
        doReturn(Arrays.asList(FakeReportFormat.XML, FakeReportFormat.HTML))
                .when(coverageTest).getReportFormat();
        doReturn(fakeHtmlReport)
                .when(coverageTest)
                .generateCoverageReport(anyCollection(), eq(FakeReportFormat.HTML));
        doReturn(fakeXmlReport)
                .when(coverageTest)
                .generateCoverageReport(anyCollection(), eq(FakeReportFormat.XML));
        doReturn(success).when(coverageTest).runTest(any(CodeCoverageTest.class),
                any(ITestInvocationListener.class));

        // Run the test
        coverageTest.run(mockListener);

        // Verify that the test was run, and that the reports were logged
        verify(coverageTest).runTest(any(CodeCoverageTest.class),
                any(ITestInvocationListener.class));
        verify(coverageTest).generateCoverageReport(anyCollection(), eq(FakeReportFormat.HTML));
        verify(coverageTest).doLogReport(anyString(), eq(FakeReportFormat.HTML.getLogDataType()),
                eq(fakeHtmlReport), any(ITestLogger.class));
        verify(coverageTest).generateCoverageReport(anyCollection(), eq(FakeReportFormat.XML));
        verify(coverageTest).doLogReport(anyString(), eq(FakeReportFormat.XML.getLogDataType()),
                eq(fakeXmlReport), any(ITestLogger.class));
    }

    public void testGetInstrumentationTargets() throws DeviceNotAvailableException {
        // Prepare some test data
        InstrumentationTarget target1 = new InstrumentationTarget(PACKAGE_NAME1, RUNNER_NAME1, "");
        InstrumentationTarget target2 = new InstrumentationTarget(PACKAGE_NAME2, RUNNER_NAME2, "");
        InstrumentationTarget target3 = new InstrumentationTarget(PACKAGE_NAME3, RUNNER_NAME1, "");
        InstrumentationTarget target4 = new InstrumentationTarget(PACKAGE_NAME3, RUNNER_NAME3, "");

        // Set up mocks
        CodeCoverageTestStub coverageTest = Mockito.spy(new CodeCoverageTestStub());
        coverageTest.addTests(target1, FOO_TESTS);
        coverageTest.addTests(target2, BAR_TESTS);
        coverageTest.addTests(target3, BAZ_TESTS);
        coverageTest.addTests(target4, BAZ_TESTS);

        // Get the instrumentation targets
        Collection<InstrumentationTarget> targets = coverageTest.getInstrumentationTargets();

        // Verify that all of the instrumentation targets were found
        assertEquals(Sets.newHashSet(target1, target2, target3, target4), targets);
    }

    public void testGetInstrumentationTargets_packageFilterSingleFilterSingleResult()
            throws DeviceNotAvailableException {
        // Prepare some test data
        InstrumentationTarget target1 = new InstrumentationTarget(PACKAGE_NAME1, RUNNER_NAME1, "");
        InstrumentationTarget target2 = new InstrumentationTarget(PACKAGE_NAME2, RUNNER_NAME2, "");
        InstrumentationTarget target3 = new InstrumentationTarget(PACKAGE_NAME3, RUNNER_NAME1, "");
        InstrumentationTarget target4 = new InstrumentationTarget(PACKAGE_NAME3, RUNNER_NAME3, "");

        // Set up mocks
        CodeCoverageTestStub coverageTest = Mockito.spy(new CodeCoverageTestStub());
        coverageTest.addTests(target1, FOO_TESTS);
        coverageTest.addTests(target2, BAR_TESTS);
        coverageTest.addTests(target3, BAZ_TESTS);
        coverageTest.addTests(target4, BAZ_TESTS);
        doReturn(Arrays.asList(PACKAGE_NAME1)).when(coverageTest).getPackageFilter();

        // Get the instrumentation targets
        Collection<InstrumentationTarget> targets = coverageTest.getInstrumentationTargets();

        // Verify that only the PACKAGE_NAME1 target was returned
        assertEquals(Sets.newHashSet(target1), targets);
    }

    public void testGetInstrumentationTargets_packageFilterSingleFilterMultipleResults()
            throws DeviceNotAvailableException {
        // Prepare some test data
        InstrumentationTarget target1 = new InstrumentationTarget(PACKAGE_NAME1, RUNNER_NAME1, "");
        InstrumentationTarget target2 = new InstrumentationTarget(PACKAGE_NAME2, RUNNER_NAME2, "");
        InstrumentationTarget target3 = new InstrumentationTarget(PACKAGE_NAME3, RUNNER_NAME1, "");
        InstrumentationTarget target4 = new InstrumentationTarget(PACKAGE_NAME3, RUNNER_NAME3, "");

        // Set up mocks
        CodeCoverageTestStub coverageTest = Mockito.spy(new CodeCoverageTestStub());
        coverageTest.addTests(target1, FOO_TESTS);
        coverageTest.addTests(target2, BAR_TESTS);
        coverageTest.addTests(target3, BAZ_TESTS);
        coverageTest.addTests(target4, BAZ_TESTS);
        doReturn(Arrays.asList(PACKAGE_NAME3)).when(coverageTest).getPackageFilter();

        // Get the instrumentation targets
        Collection<InstrumentationTarget> targets = coverageTest.getInstrumentationTargets();

        // Verify that both PACKAGE_NAME3 targets were returned
        assertEquals(Sets.newHashSet(target3, target4), targets);
    }

    public void testGetInstrumentationTargets_packageFilterMultipleFilters()
            throws DeviceNotAvailableException {
        // Prepare some test data
        InstrumentationTarget target1 = new InstrumentationTarget(PACKAGE_NAME1, RUNNER_NAME1, "");
        InstrumentationTarget target2 = new InstrumentationTarget(PACKAGE_NAME2, RUNNER_NAME2, "");
        InstrumentationTarget target3 = new InstrumentationTarget(PACKAGE_NAME3, RUNNER_NAME1, "");
        InstrumentationTarget target4 = new InstrumentationTarget(PACKAGE_NAME3, RUNNER_NAME3, "");

        // Set up mocks
        CodeCoverageTestStub coverageTest = Mockito.spy(new CodeCoverageTestStub());
        coverageTest.addTests(target1, FOO_TESTS);
        coverageTest.addTests(target2, BAR_TESTS);
        coverageTest.addTests(target3, BAZ_TESTS);
        coverageTest.addTests(target4, BAZ_TESTS);
        doReturn(Arrays.asList(PACKAGE_NAME1, PACKAGE_NAME2)).when(coverageTest).getPackageFilter();

        // Get the instrumentation targets
        Collection<InstrumentationTarget> targets = coverageTest.getInstrumentationTargets();

        // Verify that the PACKAGE_NAME1 and PACKAGE_NAME2 targets were returned
        assertEquals(Sets.newHashSet(target1, target2), targets);
    }

    public void testGetInstrumentationTargets_runnerFilterSingleFilterSingleResult()
            throws DeviceNotAvailableException {
        // Prepare some test data
        InstrumentationTarget target1 = new InstrumentationTarget(PACKAGE_NAME1, RUNNER_NAME1, "");
        InstrumentationTarget target2 = new InstrumentationTarget(PACKAGE_NAME2, RUNNER_NAME2, "");
        InstrumentationTarget target3 = new InstrumentationTarget(PACKAGE_NAME3, RUNNER_NAME1, "");
        InstrumentationTarget target4 = new InstrumentationTarget(PACKAGE_NAME3, RUNNER_NAME3, "");

        // Set up mocks
        CodeCoverageTestStub coverageTest = Mockito.spy(new CodeCoverageTestStub());
        coverageTest.addTests(target1, FOO_TESTS);
        coverageTest.addTests(target2, BAR_TESTS);
        coverageTest.addTests(target3, BAZ_TESTS);
        coverageTest.addTests(target4, BAZ_TESTS);
        doReturn(Arrays.asList(RUNNER_NAME2)).when(coverageTest).getRunnerFilter();

        // Get the instrumentation targets
        Collection<InstrumentationTarget> targets = coverageTest.getInstrumentationTargets();

        // Verify that only the RUNNER_NAME2 target was returned
        assertEquals(Sets.newHashSet(target2), targets);
    }

    public void testGetInstrumentationTargets_runnerFilterSingleFilterMultipleResults()
            throws DeviceNotAvailableException {
        // Prepare some test data
        InstrumentationTarget target1 = new InstrumentationTarget(PACKAGE_NAME1, RUNNER_NAME1, "");
        InstrumentationTarget target2 = new InstrumentationTarget(PACKAGE_NAME2, RUNNER_NAME2, "");
        InstrumentationTarget target3 = new InstrumentationTarget(PACKAGE_NAME3, RUNNER_NAME1, "");
        InstrumentationTarget target4 = new InstrumentationTarget(PACKAGE_NAME3, RUNNER_NAME3, "");

        // Set up mocks
        CodeCoverageTestStub coverageTest = Mockito.spy(new CodeCoverageTestStub());
        coverageTest.addTests(target1, FOO_TESTS);
        coverageTest.addTests(target2, BAR_TESTS);
        coverageTest.addTests(target3, BAZ_TESTS);
        coverageTest.addTests(target4, BAZ_TESTS);
        doReturn(Arrays.asList(RUNNER_NAME1)).when(coverageTest).getRunnerFilter();

        // Get the instrumentation targets
        Collection<InstrumentationTarget> targets = coverageTest.getInstrumentationTargets();

        // Verify that both RUNNER_NAME1 targets were returned
        assertEquals(Sets.newHashSet(target1, target3), targets);
    }

    public void testGetInstrumentationTargets_runnerFilterMultipleFilters()
            throws DeviceNotAvailableException {
        // Prepare some test data
        InstrumentationTarget target1 = new InstrumentationTarget(PACKAGE_NAME1, RUNNER_NAME1, "");
        InstrumentationTarget target2 = new InstrumentationTarget(PACKAGE_NAME2, RUNNER_NAME2, "");
        InstrumentationTarget target3 = new InstrumentationTarget(PACKAGE_NAME3, RUNNER_NAME1, "");
        InstrumentationTarget target4 = new InstrumentationTarget(PACKAGE_NAME3, RUNNER_NAME3, "");

        // Set up mocks
        CodeCoverageTestStub coverageTest = Mockito.spy(new CodeCoverageTestStub());
        coverageTest.addTests(target1, FOO_TESTS);
        coverageTest.addTests(target2, BAR_TESTS);
        coverageTest.addTests(target3, BAZ_TESTS);
        coverageTest.addTests(target4, BAZ_TESTS);
        doReturn(Arrays.asList(RUNNER_NAME2, RUNNER_NAME3)).when(coverageTest).getRunnerFilter();

        // Get the instrumentation targets
        Collection<InstrumentationTarget> targets = coverageTest.getInstrumentationTargets();

        // Verify that the RUNNER_NAME2 and RUNNER_NAME3 targets were returned
        assertEquals(Sets.newHashSet(target2, target4), targets);
    }

    public void testDoesRunnerSupportSharding_true() throws DeviceNotAvailableException {
        // Prepare some test data
        InstrumentationTarget target = new InstrumentationTarget(PACKAGE_NAME1, RUNNER_NAME1, "");

        // Set up mocks. Return fewer tests when sharding is enabled.
        CodeCoverageTestStub coverageTest = Mockito.spy(new CodeCoverageTestStub());
        coverageTest.addTests(target, FOO_TESTS);
        coverageTest.setShardingEnabled(RUNNER_NAME1, true);

        // Verify that the method returns true
        assertTrue(coverageTest.doesRunnerSupportSharding(target));
    }

    public void testDoesRunnerSupportSharding_false() throws DeviceNotAvailableException {
        // Prepare some test data
        InstrumentationTarget target = new InstrumentationTarget(PACKAGE_NAME1, RUNNER_NAME1, "");

        // Set up mocks. Return the same number of tests for any number of shards.
        CodeCoverageTestStub coverageTest = Mockito.spy(new CodeCoverageTestStub());
        coverageTest.addTests(target, FOO_TESTS);
        coverageTest.setShardingEnabled(RUNNER_NAME1, false);

        // Verify that the method returns false
        assertFalse(coverageTest.doesRunnerSupportSharding(target));
    }

    public void testGetNumberOfShards() throws DeviceNotAvailableException {
        // Prepare some test data
        InstrumentationTarget target = new InstrumentationTarget(PACKAGE_NAME1, RUNNER_NAME1, "");
        List<TestIdentifier> tests = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tests.add(new TestIdentifier(PACKAGE_NAME1, String.format("test%d", i)));
        }

        // Set up mocks
        CodeCoverageTestStub coverageTest = Mockito.spy(new CodeCoverageTestStub());
        coverageTest.addTests(target, tests);
        doReturn(1).when(coverageTest).getMaxTestsPerChunk();

        // Verify that each test will run in a separate shard
        assertEquals(tests.size(), coverageTest.getNumberOfShards(target));
    }

    public void testGetNumberOfShards_allTestsInSingleShard() throws DeviceNotAvailableException {
        // Prepare some test data
        InstrumentationTarget target = new InstrumentationTarget(PACKAGE_NAME1, RUNNER_NAME1, "");
        List<TestIdentifier> tests = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tests.add(new TestIdentifier(PACKAGE_NAME1, String.format("test%d", i)));
        }

        // Set up mocks
        CodeCoverageTestStub coverageTest = Mockito.spy(new CodeCoverageTestStub());
        coverageTest.addTests(target, tests);
        doReturn(10).when(coverageTest).getMaxTestsPerChunk();

        // Verify that all tests will run in a single shard
        assertEquals(1, coverageTest.getNumberOfShards(target));
    }

    public void testCollectTests() throws DeviceNotAvailableException {
        // Prepare some test data
        InstrumentationTarget target = new InstrumentationTarget(PACKAGE_NAME1, RUNNER_NAME1, "");
        List<TestIdentifier> tests = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tests.add(new TestIdentifier(PACKAGE_NAME1, String.format("test%d", i)));
        }

        // Set up mocks
        CodeCoverageTestStub coverageTest = Mockito.spy(new CodeCoverageTestStub());
        coverageTest.addTests(target, tests);

        // Collect the tests
        Collection<TestIdentifier> collectedTests = coverageTest.collectTests(target, 0, 1);

        // Verify that all of the tests were returned
        assertEquals(new HashSet<TestIdentifier>(tests), collectedTests);
    }

    public void testCollectTests_withShards() throws DeviceNotAvailableException {
        // Prepare some test data
        InstrumentationTarget target = new InstrumentationTarget(PACKAGE_NAME1, RUNNER_NAME1, "");
        int numShards = 3;
        List<TestIdentifier> tests = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tests.add(new TestIdentifier(PACKAGE_NAME1, String.format("test%d", i)));
        }

        // Set up mocks
        CodeCoverageTestStub coverageTest = Mockito.spy(new CodeCoverageTestStub());
        coverageTest.addTests(target, tests);
        coverageTest.setShardingEnabled(RUNNER_NAME1, true);

        // Collect the tests in shards
        ArrayList<TestIdentifier> allCollectedTests = new ArrayList<>();
        for (int shardIndex = 0; shardIndex < numShards; shardIndex++) {
            // Verify that each shard contains some tests
            Collection<TestIdentifier> collectedTests =
                    coverageTest.collectTests(target, shardIndex, numShards);
            assertFalse(collectedTests.isEmpty());

            allCollectedTests.addAll(collectedTests);
        }
        // Verify that all of the tests were returned in the end
        assertEquals(tests, allCollectedTests);
    }

    public void testCreateTestRunner() throws DeviceNotAvailableException {
        // Prepare some test data
        InstrumentationTarget target = new InstrumentationTarget(PACKAGE_NAME1, RUNNER_NAME1, "");

        // Set up mocks
        CodeCoverageTestStub coverageTest = Mockito.spy(new CodeCoverageTestStub());

        // Create a test runner
        IRemoteAndroidTestRunner runner = coverageTest.createTestRunner(target, 0, 1);

        // Verify that the runner has the correct values
        assertEquals(PACKAGE_NAME1, runner.getPackageName());
        assertEquals(RUNNER_NAME1, runner.getRunnerName());
    }

    public void testCreateTestRunner_withArgs() throws DeviceNotAvailableException {
        // Prepare some test data
        InstrumentationTarget target = new InstrumentationTarget(PACKAGE_NAME1, RUNNER_NAME1, "");
        Map<String, String> args = ImmutableMap.of("arg1", "value1", "arg2", "value2");

        // Set up mocks
        CodeCoverageTestStub coverageTest = Mockito.spy(new CodeCoverageTestStub());
        doReturn(args).when(coverageTest).getInstrumentationArgs();

        // Create a test runner
        IRemoteAndroidTestRunner runner = coverageTest.createTestRunner(target, 0, 1);

        // Verify that the addInstrumentationArg(..) method was called with each argument
        for (Map.Entry<String, String> entry : args.entrySet()) {
            verify(runner).addInstrumentationArg(entry.getKey(), entry.getValue());
        }
    }

    public void testCreateTestRunner_withShards() throws DeviceNotAvailableException {
        // Prepare some test data
        InstrumentationTarget target = new InstrumentationTarget(PACKAGE_NAME1, RUNNER_NAME1, "");
        int shardIndex = 3;
        int numShards = 5;

        // Set up mocks
        CodeCoverageTestStub coverageTest = Mockito.spy(new CodeCoverageTestStub());

        // Create a test runner
        IRemoteAndroidTestRunner runner =
                coverageTest.createTestRunner(target, shardIndex, numShards);

        // Verify that the addInstrumentationArg(..) method was called to configure the shards
        verify(runner).addInstrumentationArg("shardIndex", Integer.toString(shardIndex));
        verify(runner).addInstrumentationArg("numShards", Integer.toString(numShards));
    }

    public void testCreateCoverageTest() throws DeviceNotAvailableException {
        // Prepare some test data
        InstrumentationTarget target = new InstrumentationTarget(PACKAGE_NAME1, RUNNER_NAME1, "");

        // Set up mocks
        CodeCoverageTestStub coverageTest = Mockito.spy(new CodeCoverageTestStub());

        // Create a CodeCoverageTest instance
        CodeCoverageTest test = coverageTest.createCoverageTest(target);

        // Verify that the test has the correct values
        assertEquals(PACKAGE_NAME1, test.getPackageName());
        assertEquals(RUNNER_NAME1, test.getRunnerName());
    }

    public void testCreateCoverageTest_withArgs() throws DeviceNotAvailableException {
        // Prepare some test data
        InstrumentationTarget target = new InstrumentationTarget(PACKAGE_NAME1, RUNNER_NAME1, "");
        Map<String, String> args = ImmutableMap.of("arg1", "value1", "arg2", "value2");

        // Set up mocks
        CodeCoverageTestStub coverageTest = Mockito.spy(new CodeCoverageTestStub());
        doReturn(args).when(coverageTest).getInstrumentationArgs();

        // Create a CodeCoverageTest instance
        CodeCoverageTest test = coverageTest.createCoverageTest(target, 0, 3);

        // Verify that the addInstrumentationArg(..) method was called with each argument
        for (Map.Entry<String, String> entry : args.entrySet()) {
            verify(test).addInstrumentationArg(entry.getKey(), entry.getValue());
        }
    }

    public void testCreateCoverageTest_withShards() throws DeviceNotAvailableException {
        // Prepare some test data
        InstrumentationTarget target = new InstrumentationTarget(PACKAGE_NAME1, RUNNER_NAME1, "");
        int shardIndex = 3;
        int numShards = 5;

        // Set up mocks
        CodeCoverageTestStub coverageTest = Mockito.spy(new CodeCoverageTestStub());

        // Create a CodeCoverageTest instance
        CodeCoverageTest test = coverageTest.createCoverageTest(target, shardIndex, numShards);

        // Verify that the addInstrumentationArg(..) method was called to configure the shards
        verify(test).addInstrumentationArg("shardIndex", Integer.toString(shardIndex));
        verify(test).addInstrumentationArg("numShards", Integer.toString(numShards));
    }

    /**
     * A fake {@link IRemoteAndroidTestRunner} which simulates a test run by notifying the
     * {@link ITestRunListener}s but does not actually run anything.
     */
    static class FakeTestRunner extends RemoteAndroidTestRunner {
        private List<TestIdentifier> mTests;
        private boolean mShardingEnabled = false;
        private int mShardIndex = 0;
        private int mNumShards = 1;

        public FakeTestRunner(String packageName, String runnerName, List<TestIdentifier> tests,
                boolean shardingEnabled) {
            super(packageName, runnerName, null);

            mTests = tests;
            mShardingEnabled = shardingEnabled;
        }

        @Override
        public void addInstrumentationArg(String name, String value) {
            super.addInstrumentationArg(name, value);

            if ("shardIndex".equals(name)) {
                mShardIndex = Integer.parseInt(value);
            }
            if ("numShards".equals(name)) {
                mNumShards = Integer.parseInt(value);
            }
        }

        @Override
        public void run(Collection<ITestRunListener> listeners) {
            List<TestIdentifier> tests = mTests;
            if (mShardingEnabled) {
                int shardSize = (int)Math.ceil((double)tests.size() / mNumShards);
                tests = Lists.partition(tests, shardSize).get(mShardIndex);
            }

            // Start the test run
            for (ITestRunListener listener : listeners) {
                listener.testRunStarted(getPackageName(), tests.size());
            }
            // Run each of the tests
            for (TestIdentifier test : tests) {
                // Start test
                for (ITestRunListener listener : listeners) {
                    listener.testStarted(test);
                }
                // Finish test
                for (ITestRunListener listener : listeners) {
                    listener.testEnded(test, new HashMap<String, String>());
                }
            }
            // End the test run
            for (ITestRunListener listener : listeners) {
                listener.testRunEnded(1000, new HashMap<String, String>());
            }
        }
    }
}
