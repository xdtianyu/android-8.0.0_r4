/*
 * Copyright (C) 2010 The Android Open Source Project
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

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.result.ITestInvocationListener;

import junit.framework.TestCase;

import org.easymock.EasyMock;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Unit tests for {@link DeviceTestCase}.
 */
public class DeviceTestCaseTest extends TestCase {

    public static class MockTest extends DeviceTestCase {

        public void test1() {
            // test adding a metric during the test.
            addTestMetric("test", "value");
        }
        public void test2() {}
    }

    @MyAnnotation1
    public static class MockAnnotatedTest extends DeviceTestCase {

        @MyAnnotation1
        public void test1() {}
        @MyAnnotation2
        public void test2() {}
    }

    /** A test class that illustrate duplicate names but from only one real test. */
    public static class DuplicateTest extends DeviceTestCase {

        public void test1() {
            test1("");
        }

        private void test1(String arg) {
            assertTrue(arg.isEmpty());
        }

        public void test2() {}
    }

    /**
     * Simple Annotation class for testing
     */
    @Retention(RetentionPolicy.RUNTIME)
    public @interface MyAnnotation1 {
    }

    /**
     * Simple Annotation class for testing
     */
    @Retention(RetentionPolicy.RUNTIME)
    public @interface MyAnnotation2 {
    }

    public static class MockAbortTest extends DeviceTestCase {

        private static final String EXCEP_MSG = "failed";
        private static final String FAKE_SERIAL = "fakeserial";

        public void test1() throws DeviceNotAvailableException {
            throw new DeviceNotAvailableException(EXCEP_MSG, FAKE_SERIAL);
        }
    }

    /**
     * Verify that calling run on a DeviceTestCase will run all test methods.
     */
    @SuppressWarnings("unchecked")
    public void testRun_suite() throws Exception {
        MockTest test = new MockTest();

        ITestInvocationListener listener = EasyMock.createMock(ITestInvocationListener.class);
        listener.testRunStarted(MockTest.class.getName(), 2);
        final TestIdentifier test1 = new TestIdentifier(MockTest.class.getName(), "test1");
        final TestIdentifier test2 = new TestIdentifier(MockTest.class.getName(), "test2");
        listener.testStarted(test1);
        Map<String, String> metrics = new HashMap<>();
        metrics.put("test", "value");
        listener.testEnded(test1, metrics);
        listener.testStarted(test2);
        listener.testEnded(test2, Collections.EMPTY_MAP);
        listener.testRunEnded(EasyMock.anyLong(), (Map<String, String>) EasyMock.anyObject());
        EasyMock.replay(listener);

        test.run(listener);
        EasyMock.verify(listener);
    }

    /**
     * Verify that calling run on a {@link DeviceTestCase}
     * will only run methods included by filtering.
     */
    @SuppressWarnings("unchecked")
    public void testRun_includeFilter() throws Exception {
        MockTest test = new MockTest();
        test.addIncludeFilter("com.android.tradefed.testtype.DeviceTestCaseTest$MockTest#test1");
        ITestInvocationListener listener = EasyMock.createMock(ITestInvocationListener.class);
        listener.testRunStarted(MockTest.class.getName(), 1);
        final TestIdentifier test1 = new TestIdentifier(MockTest.class.getName(), "test1");
        listener.testStarted(test1);
        Map<String, String> metrics = new HashMap<>();
        metrics.put("test", "value");
        listener.testEnded(test1, metrics);
        listener.testRunEnded(EasyMock.anyLong(), (Map<String, String>) EasyMock.anyObject());
        EasyMock.replay(listener);

        test.run(listener);
        EasyMock.verify(listener);
    }

    /**
     * Verify that calling run on a {@link DeviceTestCase}
     * will not run methods excluded by filtering.
     */
    @SuppressWarnings("unchecked")
    public void testRun_excludeFilter() throws Exception {
        MockTest test = new MockTest();
        test.addExcludeFilter("com.android.tradefed.testtype.DeviceTestCaseTest$MockTest#test1");
        ITestInvocationListener listener = EasyMock.createMock(ITestInvocationListener.class);
        listener.testRunStarted(MockTest.class.getName(), 1);
        final TestIdentifier test2 = new TestIdentifier(MockTest.class.getName(), "test2");
        listener.testStarted(test2);
        listener.testEnded(test2, Collections.EMPTY_MAP);
        listener.testRunEnded(EasyMock.anyLong(), (Map<String, String>) EasyMock.anyObject());
        EasyMock.replay(listener);

        test.run(listener);
        EasyMock.verify(listener);
    }

    /**
     * Verify that calling run on a {@link DeviceTestCase} only runs AnnotatedElements
     * included by filtering.
     */
    @SuppressWarnings("unchecked")
    public void testRun_includeAnnotationFiltering() throws Exception {
        MockAnnotatedTest test = new MockAnnotatedTest();
        test.addIncludeAnnotation(
                "com.android.tradefed.testtype.DeviceTestCaseTest$MyAnnotation1");
        test.addExcludeAnnotation("com.android.tradefed.testtype.DeviceTestCaseTest$MyAnnotation2");
        ITestInvocationListener listener = EasyMock.createMock(ITestInvocationListener.class);
        listener.testRunStarted(MockAnnotatedTest.class.getName(), 1);
        final TestIdentifier test1 = new TestIdentifier(MockAnnotatedTest.class.getName(), "test1");
        listener.testStarted(test1);
        listener.testEnded(test1, Collections.EMPTY_MAP);
        listener.testRunEnded(EasyMock.anyLong(), (Map<String, String>) EasyMock.anyObject());
        EasyMock.replay(listener);

        test.run(listener);
        EasyMock.verify(listener);
    }

    /**
     * Verify that calling run on a {@link DeviceTestCase} does not run AnnotatedElements
     * excluded by filtering.
     */
    @SuppressWarnings("unchecked")
    public void testRun_excludeAnnotationFiltering() throws Exception {
        MockAnnotatedTest test = new MockAnnotatedTest();
        test.addExcludeAnnotation(
                "com.android.tradefed.testtype.DeviceTestCaseTest$MyAnnotation2");
        ITestInvocationListener listener = EasyMock.createMock(ITestInvocationListener.class);
        listener.testRunStarted(MockAnnotatedTest.class.getName(), 1);
        final TestIdentifier test1 = new TestIdentifier(MockAnnotatedTest.class.getName(), "test1");
        listener.testStarted(test1);
        listener.testEnded(test1, Collections.EMPTY_MAP);
        listener.testRunEnded(EasyMock.anyLong(), (Map<String, String>) EasyMock.anyObject());
        EasyMock.replay(listener);

        test.run(listener);
        EasyMock.verify(listener);
    }

    /**
     * Regression test to verify a single test can still be run.
     */
    @SuppressWarnings("unchecked")
    public void testRun_singleTest() throws DeviceNotAvailableException {
        MockTest test = new MockTest();
        test.setName("test1");

        ITestInvocationListener listener = EasyMock.createMock(ITestInvocationListener.class);
        listener.testRunStarted(MockTest.class.getName(), 1);
        final TestIdentifier test1 = new TestIdentifier(MockTest.class.getName(), "test1");
        listener.testStarted(test1);
        Map<String, String> metrics = new HashMap<>();
        metrics.put("test", "value");
        listener.testEnded(test1, metrics);
        listener.testRunEnded(EasyMock.anyLong(), (Map<String, String>) EasyMock.anyObject());
        EasyMock.replay(listener);

        test.run(listener);
        EasyMock.verify(listener);
    }

    /**
     * Verify that a device not available exception is thrown up.
     */
    @SuppressWarnings("unchecked")
    public void testRun_deviceNotAvail() {
        MockAbortTest test = new MockAbortTest();
        // create a mock ITestInvocationListener, because results are easier to verify
        ITestInvocationListener listener = EasyMock.createMock(ITestInvocationListener.class);

        final TestIdentifier test1 = new TestIdentifier(MockAbortTest.class.getName(), "test1");
        listener.testRunStarted(MockAbortTest.class.getName(), 1);
        listener.testStarted(test1);
        listener.testFailed(EasyMock.eq(test1),
                EasyMock.contains(MockAbortTest.EXCEP_MSG));
        listener.testEnded(test1, Collections.EMPTY_MAP);
        listener.testRunFailed(EasyMock.contains(MockAbortTest.EXCEP_MSG));
        listener.testRunEnded(EasyMock.anyLong(), (Map<String, String>) EasyMock.anyObject());
        EasyMock.replay(listener);
        try {
            test.run(listener);
            fail("DeviceNotAvailableException not thrown");
        } catch (DeviceNotAvailableException e) {
            // expected
        }
        EasyMock.verify(listener);
    }

    /**
     * Test success case for {@link DeviceTestCase#run(ITestInvocationListener)} in collector mode,
     * where test to run is a {@link TestCase}
     */
    @SuppressWarnings("unchecked")
    public void testRun_testcaseCollectMode() throws Exception {
        ITestInvocationListener listener = EasyMock.createMock(ITestInvocationListener.class);
        MockTest test = new MockTest();
        test.setCollectTestsOnly(true);
        listener.testRunStarted((String)EasyMock.anyObject(), EasyMock.eq(2));
        listener.testStarted((TestIdentifier) EasyMock.anyObject());
        listener.testEnded((TestIdentifier) EasyMock.anyObject(),
                (Map<String, String>)EasyMock.anyObject());
        listener.testStarted((TestIdentifier) EasyMock.anyObject());
        listener.testEnded((TestIdentifier) EasyMock.anyObject(),
                (Map<String, String>)EasyMock.anyObject());
        listener.testRunEnded(EasyMock.anyLong(), (Map<String, String>)EasyMock.anyObject());
        EasyMock.replay(listener);
        test.run(listener);
        EasyMock.verify(listener);
    }

    /**
     * Test success case for {@link DeviceTestCase#run(ITestInvocationListener)} in collector mode,
     * where test to run is a {@link TestCase}
     */
    @SuppressWarnings("unchecked")
    public void testRun_testcaseCollectMode_singleMethod() throws Exception {
        ITestInvocationListener listener = EasyMock.createMock(ITestInvocationListener.class);
        MockTest test = new MockTest();
        test.setName("test1");
        test.setCollectTestsOnly(true);
        listener.testRunStarted((String)EasyMock.anyObject(), EasyMock.eq(1));
        listener.testStarted((TestIdentifier) EasyMock.anyObject());
        listener.testEnded((TestIdentifier) EasyMock.anyObject(),
                (Map<String, String>)EasyMock.anyObject());
        listener.testRunEnded(EasyMock.anyLong(), (Map<String, String>)EasyMock.anyObject());
        EasyMock.replay(listener);
        test.run(listener);
        EasyMock.verify(listener);
    }

    /**
     * Test that when a test class has some private method with a method name we properly ignore it
     * and only consider the actual real method that can execute in the filtering.
     */
    public void testRun_duplicateName() throws Exception {
        DuplicateTest test = new DuplicateTest();
        ITestInvocationListener listener = EasyMock.createMock(ITestInvocationListener.class);

        listener.testRunStarted(DuplicateTest.class.getName(), 2);
        final TestIdentifier test1 = new TestIdentifier(DuplicateTest.class.getName(), "test1");
        final TestIdentifier test2 = new TestIdentifier(DuplicateTest.class.getName(), "test2");
        listener.testStarted(test1);
        listener.testEnded(test1, Collections.emptyMap());
        listener.testStarted(test2);
        listener.testEnded(test2, Collections.emptyMap());
        listener.testRunEnded(EasyMock.anyLong(), EasyMock.anyObject());
        EasyMock.replay(listener);

        test.run(listener);
        EasyMock.verify(listener);
    }
}
