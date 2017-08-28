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
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner.TestMetrics;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.easymock.Capture;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.runner.RunWith;
import org.junit.runners.Suite.SuiteClasses;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.AnnotatedElement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Unit tests for {@link HostTest}.
 */
@SuppressWarnings("unchecked")
public class HostTestTest extends TestCase {

    private HostTest mHostTest;
    private ITestInvocationListener mListener;
    private ITestDevice mMockDevice;
    private IBuildInfo mMockBuildInfo;

    @MyAnnotation
    @MyAnnotation3
    public static class SuccessTestCase extends TestCase {
        public SuccessTestCase() {
        }

        public SuccessTestCase(String name) {
            super(name);
        }

        @MyAnnotation
        public void testPass() {
        }

        @MyAnnotation
        @MyAnnotation2
        public void testPass2() {
        }
    }

    public static class TestMetricTestCase extends MetricTestCase {

        public void testPass() {
            addTestMetric("key1", "metric1");
        }

        public void testPass2() {
            addTestMetric("key2", "metric2");
        }
    }

    @MyAnnotation
    public static class AnotherTestCase extends TestCase {
        public AnotherTestCase() {
        }

        public AnotherTestCase(String name) {
            super(name);
        }

        @MyAnnotation
        @MyAnnotation2
        @MyAnnotation3
        public void testPass3() {}

        @MyAnnotation
        public void testPass4() {
        }
    }

    /**
     * Test class, we have to annotate with full org.junit.Test to avoid name collision in import.
     */
    @RunWith(DeviceJUnit4ClassRunner.class)
    public static class Junit4TestClass {

        public Junit4TestClass() {}

        @Rule public TestMetrics metrics = new TestMetrics();

        @MyAnnotation
        @MyAnnotation2
        @org.junit.Test
        public void testPass5() {
            // test log through the rule.
            metrics.addTestMetric("key", "value");
        }

        @MyAnnotation
        @org.junit.Test
        public void testPass6() {
            metrics.addTestMetric("key2", "value2");
        }
    }

    /**
     * Test class, we have to annotate with full org.junit.Test to avoid name collision in import.
     * And with one test marked as Ignored
     */
    @RunWith(DeviceJUnit4ClassRunner.class)
    public static class Junit4TestClassWithIgnore implements IDeviceTest {
        private ITestDevice mDevice;

        public Junit4TestClassWithIgnore() {}

        @org.junit.Test
        public void testPass5() {}

        @Ignore
        @org.junit.Test
        public void testPass6() {}

        @Override
        public void setDevice(ITestDevice device) {
            mDevice = device;
        }

        @Override
        public ITestDevice getDevice() {
            return mDevice;
        }
    }

    @RunWith(DeviceSuite.class)
    @SuiteClasses({
        Junit4TestClass.class,
        SuccessTestCase.class,
    })
    public class Junit4SuiteClass {
    }

    /**
     * Malformed on purpose test class.
     */
    public static class Junit4MalformedTestClass {
        public Junit4MalformedTestClass() {
        }

        @Before
        protected void setUp() {
            // @Before should be on a public method.
        }

        @org.junit.Test
        public void testPass() {
        }
    }

    /**
     * Simple Annotation class for testing
     */
    @Retention(RetentionPolicy.RUNTIME)
    public @interface MyAnnotation {
    }

    /**
     * Simple Annotation class for testing
     */
    @Retention(RetentionPolicy.RUNTIME)
    public @interface MyAnnotation2 {
    }

    /** Simple Annotation class for testing */
    @Retention(RetentionPolicy.RUNTIME)
    public @interface MyAnnotation3 {}

    public static class SuccessTestSuite extends TestSuite {
        public SuccessTestSuite() {
            super(SuccessTestCase.class);
        }
    }

    public static class SuccessHierarchySuite extends TestSuite {
        public SuccessHierarchySuite() {
            super();
            addTestSuite(SuccessTestCase.class);
        }
    }

    public static class SuccessDeviceTest extends DeviceTestCase {
        public SuccessDeviceTest() {
            super();
        }

        public void testPass() {
            assertNotNull(getDevice());
        }
    }

    @MyAnnotation
    public static class SuccessDeviceTest2 extends DeviceTestCase {
        public SuccessDeviceTest2() {
            super();
        }

        @MyAnnotation3
        public void testPass1() {
            assertNotNull(getDevice());
        }

        public void testPass2() {
            assertNotNull(getDevice());
        }
    }

    @MyAnnotation
    public static class InheritedDeviceTest3 extends SuccessDeviceTest2 {
        public InheritedDeviceTest3() {
            super();
        }

        @Override
        public void testPass1() {
            super.testPass1();
        }

        @MyAnnotation3
        public void testPass3() {}
    }

    public static class TestRemoteNotCollector implements IDeviceTest, IRemoteTest {
        @Override
        public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {}

        @Override
        public void setDevice(ITestDevice device) {}

        @Override
        public ITestDevice getDevice() {
            return null;
        }
    }

    /** Non-public class; should fail to load. */
    private static class PrivateTest extends TestCase {
        @SuppressWarnings("unused")
        public void testPrivate() {
        }
    }

    /** class without default constructor; should fail to load */
    public static class NoConstructorTest extends TestCase {
        public NoConstructorTest(String name) {
            super(name);
        }
        public void testNoConstructor() {
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mHostTest = new HostTest();
        mListener = EasyMock.createMock(ITestInvocationListener.class);
        mMockDevice = EasyMock.createMock(ITestDevice.class);
        mMockBuildInfo = EasyMock.createMock(IBuildInfo.class);
        mHostTest.setDevice(mMockDevice);
        mHostTest.setBuild(mMockBuildInfo);
    }

    /**
     * Test success case for {@link HostTest#run(ITestInvocationListener)}, where test to run is a
     * {@link TestCase}.
     */
    public void testRun_testcase() throws Exception {
        mHostTest.setClassName(SuccessTestCase.class.getName());
        TestIdentifier test1 = new TestIdentifier(SuccessTestCase.class.getName(), "testPass");
        TestIdentifier test2 = new TestIdentifier(SuccessTestCase.class.getName(), "testPass2");
        mListener.testRunStarted((String)EasyMock.anyObject(), EasyMock.eq(2));
        mListener.testStarted(EasyMock.eq(test1));
        mListener.testEnded(EasyMock.eq(test1), (Map<String, String>)EasyMock.anyObject());
        mListener.testStarted(EasyMock.eq(test2));
        mListener.testEnded(EasyMock.eq(test2), (Map<String, String>)EasyMock.anyObject());
        mListener.testRunEnded(EasyMock.anyLong(), (Map<String, String>)EasyMock.anyObject());
        EasyMock.replay(mListener);
        mHostTest.run(mListener);
        EasyMock.verify(mListener);
    }

    /**
     * Test success case for {@link HostTest#run(ITestInvocationListener)}, where test to run is a
     * {@link MetricTestCase}.
     */
    public void testRun_MetricTestCase() throws Exception {
        mHostTest.setClassName(TestMetricTestCase.class.getName());
        TestIdentifier test1 = new TestIdentifier(TestMetricTestCase.class.getName(), "testPass");
        TestIdentifier test2 = new TestIdentifier(TestMetricTestCase.class.getName(), "testPass2");
        mListener.testRunStarted((String) EasyMock.anyObject(), EasyMock.eq(2));
        mListener.testStarted(EasyMock.eq(test1));
        // test1 should only have its metrics
        Map<String, String> metric1 = new HashMap<>();
        metric1.put("key1", "metric1");
        mListener.testEnded(test1, metric1);
        // test2 should only have its metrics
        mListener.testStarted(EasyMock.eq(test2));
        Map<String, String> metric2 = new HashMap<>();
        metric2.put("key2", "metric2");
        mListener.testEnded(test2, metric2);
        mListener.testRunEnded(EasyMock.anyLong(), (Map<String, String>) EasyMock.anyObject());
        EasyMock.replay(mListener);
        mHostTest.run(mListener);
        EasyMock.verify(mListener);
    }

    /**
     * Test success case for {@link HostTest#run(ITestInvocationListener)}, where test to run is a
     * {@link TestSuite}.
     */
    public void testRun_testSuite() throws Exception {
        mHostTest.setClassName(SuccessTestSuite.class.getName());
        TestIdentifier test1 = new TestIdentifier(SuccessTestCase.class.getName(), "testPass");
        TestIdentifier test2 = new TestIdentifier(SuccessTestCase.class.getName(), "testPass2");
        mListener.testRunStarted((String)EasyMock.anyObject(), EasyMock.eq(2));
        mListener.testStarted(EasyMock.eq(test1));
        mListener.testEnded(EasyMock.eq(test1), (Map<String, String>)EasyMock.anyObject());
        mListener.testStarted(EasyMock.eq(test2));
        mListener.testEnded(EasyMock.eq(test2), (Map<String, String>)EasyMock.anyObject());
        mListener.testRunEnded(EasyMock.anyLong(), (Map<String, String>)EasyMock.anyObject());
        EasyMock.replay(mListener);
        mHostTest.run(mListener);
        EasyMock.verify(mListener);
    }

    /**
     * Test success case for {@link HostTest#run(ITestInvocationListener)}, where test to run is a
     * hierarchy of {@link TestSuite}s.
     */
    public void testRun_testHierarchySuite() throws Exception {
        mHostTest.setClassName(SuccessHierarchySuite.class.getName());
        TestIdentifier test1 = new TestIdentifier(SuccessTestCase.class.getName(), "testPass");
        TestIdentifier test2 = new TestIdentifier(SuccessTestCase.class.getName(), "testPass2");
        mListener.testRunStarted((String)EasyMock.anyObject(), EasyMock.eq(2));
        mListener.testStarted(EasyMock.eq(test1));
        mListener.testEnded(EasyMock.eq(test1), (Map<String, String>)EasyMock.anyObject());
        mListener.testStarted(EasyMock.eq(test2));
        mListener.testEnded(EasyMock.eq(test2), (Map<String, String>)EasyMock.anyObject());
        mListener.testRunEnded(EasyMock.anyLong(), (Map<String, String>)EasyMock.anyObject());
        EasyMock.replay(mListener);
        mHostTest.run(mListener);
        EasyMock.verify(mListener);
    }

    /**
     * Test success case for {@link HostTest#run(ITestInvocationListener)}, where test to run is a
     * {@link TestCase} and methodName is set.
     */
    public void testRun_testMethod() throws Exception {
        mHostTest.setClassName(SuccessTestCase.class.getName());
        mHostTest.setMethodName("testPass");
        TestIdentifier test1 = new TestIdentifier(SuccessTestCase.class.getName(), "testPass");
        mListener.testRunStarted((String)EasyMock.anyObject(), EasyMock.eq(1));
        mListener.testStarted(EasyMock.eq(test1));
        mListener.testEnded(EasyMock.eq(test1), (Map<String, String>)EasyMock.anyObject());
        mListener.testRunEnded(EasyMock.anyLong(), (Map<String, String>)EasyMock.anyObject());
        EasyMock.replay(mListener);
        mHostTest.run(mListener);
        EasyMock.verify(mListener);
    }

    /**
     * Test for {@link HostTest#run(ITestInvocationListener)}, where className is not set.
     */
    public void testRun_missingClass() throws Exception {
        try {
            mHostTest.run(mListener);
            fail("IllegalArgumentException not thrown");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    /**
     * Test for {@link HostTest#run(ITestInvocationListener)}, for an invalid class.
     */
    public void testRun_invalidClass() throws Exception {
        try {
            mHostTest.setClassName("foo");
            mHostTest.run(mListener);
            fail("IllegalArgumentException not thrown");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    /**
     * Test for {@link HostTest#run(ITestInvocationListener)},
     * for a valid class that is not a {@link Test}.
     */
    public void testRun_notTestClass() throws Exception {
        try {
            mHostTest.setClassName(String.class.getName());
            mHostTest.run(mListener);
            fail("IllegalArgumentException not thrown");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    /**
     * Test for {@link HostTest#run(ITestInvocationListener)},
     * for a private class.
     */
    public void testRun_privateClass() throws Exception {
        try {
            mHostTest.setClassName(PrivateTest.class.getName());
            mHostTest.run(mListener);
            fail("IllegalArgumentException not thrown");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    /**
     * Test for {@link HostTest#run(ITestInvocationListener)},
     * for a test class with no default constructor.
     */
    public void testRun_noConstructorClass() throws Exception {
        try {
            mHostTest.setClassName(NoConstructorTest.class.getName());
            mHostTest.run(mListener);
            fail("IllegalArgumentException not thrown");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    /**
     * Test for {@link HostTest#run(ITestInvocationListener)}, for multiple test classes.
     */
    public void testRun_multipleClass() throws Exception {
        OptionSetter setter = new OptionSetter(mHostTest);
        setter.setOptionValue("class", SuccessTestCase.class.getName());
        setter.setOptionValue("class", AnotherTestCase.class.getName());
        TestIdentifier test1 = new TestIdentifier(SuccessTestCase.class.getName(), "testPass");
        TestIdentifier test2 = new TestIdentifier(SuccessTestCase.class.getName(), "testPass2");
        TestIdentifier test3 = new TestIdentifier(AnotherTestCase.class.getName(), "testPass3");
        TestIdentifier test4 = new TestIdentifier(AnotherTestCase.class.getName(), "testPass4");
        mListener.testRunStarted((String)EasyMock.anyObject(), EasyMock.eq(2));
        EasyMock.expectLastCall().times(2);
        mListener.testStarted(EasyMock.eq(test1));
        mListener.testEnded(EasyMock.eq(test1), (Map<String, String>)EasyMock.anyObject());
        mListener.testStarted(EasyMock.eq(test2));
        mListener.testEnded(EasyMock.eq(test2), (Map<String, String>)EasyMock.anyObject());
        mListener.testStarted(EasyMock.eq(test3));
        mListener.testEnded(EasyMock.eq(test3), (Map<String, String>)EasyMock.anyObject());
        mListener.testStarted(EasyMock.eq(test4));
        mListener.testEnded(EasyMock.eq(test4), (Map<String, String>)EasyMock.anyObject());
        mListener.testRunEnded(EasyMock.anyLong(), (Map<String, String>)EasyMock.anyObject());
        EasyMock.expectLastCall().times(2);
        EasyMock.replay(mListener);
        mHostTest.run(mListener);
        EasyMock.verify(mListener);
    }

    /**
     * Test for {@link HostTest#run(ITestInvocationListener)},
     * for multiple test classes with a method name.
     */
    public void testRun_multipleClassAndMethodName() throws Exception {
        try {
            OptionSetter setter = new OptionSetter(mHostTest);
            setter.setOptionValue("class", SuccessTestCase.class.getName());
            setter.setOptionValue("class", AnotherTestCase.class.getName());
            mHostTest.setMethodName("testPass3");
            mHostTest.run(mListener);
            fail("IllegalArgumentException not thrown");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    /**
     * Test for {@link HostTest#run(ITestInvocationListener)}, for a {@link IDeviceTest}.
     */
    public void testRun_deviceTest() throws Exception {
        final ITestDevice device = EasyMock.createMock(ITestDevice.class);
        mHostTest.setClassName(SuccessDeviceTest.class.getName());
        mHostTest.setDevice(device);

        TestIdentifier test1 = new TestIdentifier(SuccessDeviceTest.class.getName(), "testPass");
        mListener.testRunStarted((String)EasyMock.anyObject(), EasyMock.eq(1));
        mListener.testStarted(EasyMock.eq(test1));
        mListener.testEnded(EasyMock.eq(test1), (Map<String, String>)EasyMock.anyObject());
        mListener.testRunEnded(EasyMock.anyLong(), (Map<String, String>)EasyMock.anyObject());
        EasyMock.replay(mListener);
        mHostTest.run(mListener);
        EasyMock.verify(mListener);
    }

    /**
     * Test for {@link HostTest#run(ITestInvocationListener)},
     * for a {@link IDeviceTest} where no device has been provided.
     */
    public void testRun_missingDevice() throws Exception {
        mHostTest.setClassName(SuccessDeviceTest.class.getName());
        mHostTest.setDevice(null);
        try {
            mHostTest.run(mListener);
            fail("expected IllegalArgumentException not thrown");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    /**
     * Test for {@link HostTest#countTestCases()}
     */
    public void testCountTestCases() throws Exception {
        mHostTest.setClassName(SuccessTestCase.class.getName());
        assertEquals("Incorrect test case count", 2, mHostTest.countTestCases());
    }

    /**
     * Test for {@link HostTest#countTestCases()} with filtering on JUnit4 tests
     */
    public void testCountTestCasesJUnit4WithFiltering() throws Exception {
        mHostTest.setClassName(Junit4TestClass.class.getName());
        mHostTest.addIncludeFilter(
                "com.android.tradefed.testtype.HostTestTest$Junit4TestClass#testPass5");
        assertEquals("Incorrect test case count", 1, mHostTest.countTestCases());
    }

    /**
     * Test for {@link HostTest#countTestCases()}, if JUnit4 test class is malformed it will
     * count as 1 in the total number of tests.
     */
    public void testCountTestCasesJUnit4Malformed() throws Exception {
        mHostTest.setClassName(Junit4MalformedTestClass.class.getName());
        assertEquals("Incorrect test case count", 1, mHostTest.countTestCases());
    }

    /**
     * Test for {@link HostTest#countTestCases()} with filtering on JUnit4 tests and no test
     * remain.
     */
    public void testCountTestCasesJUnit4WithFiltering_no_more_tests() throws Exception {
        mHostTest.setClassName(Junit4TestClass.class.getName());
        mHostTest.addExcludeFilter(
                "com.android.tradefed.testtype.HostTestTest$Junit4TestClass#testPass5");
        mHostTest.addExcludeFilter(
                "com.android.tradefed.testtype.HostTestTest$Junit4TestClass#testPass6");
        assertEquals("Incorrect test case count", 0, mHostTest.countTestCases());
    }

    /**
     * Test for {@link HostTest#countTestCases()} with tests of varying JUnit versions
     */
    public void testCountTestCasesJUnitVersionMixed() throws Exception {
        OptionSetter setter = new OptionSetter(mHostTest);
        setter.setOptionValue("class", SuccessTestCase.class.getName()); // 2 tests
        setter.setOptionValue("class", Junit4TestClass.class.getName()); // 2 tests
        setter.setOptionValue("class", Junit4SuiteClass.class.getName()); // 4 tests
        assertEquals("Incorrect test case count", 8, mHostTest.countTestCases());
    }

    /**
     * Test for {@link HostTest#countTestCases()} with filtering on tests of varying JUnit versions
     */
    public void testCountTestCasesJUnitVersionMixedWithFiltering() throws Exception {
        OptionSetter setter = new OptionSetter(mHostTest);
        setter.setOptionValue("class", SuccessTestCase.class.getName()); // 2 tests
        setter.setOptionValue("class", Junit4TestClass.class.getName()); // 2 tests
        mHostTest.addIncludeFilter(
                "com.android.tradefed.testtype.HostTestTest$SuccessTestCase#testPass");
        mHostTest.addIncludeFilter(
                "com.android.tradefed.testtype.HostTestTest$Junit4TestClass#testPass5");
        assertEquals("Incorrect test case count", 2, mHostTest.countTestCases());
    }

    /**
     * Test for {@link HostTest#countTestCases()} with annotation filtering
     */
    public void testCountTestCasesAnnotationFiltering() throws Exception {
        mHostTest.setClassName(SuccessTestCase.class.getName());
        mHostTest.addExcludeAnnotation("com.android.tradefed.testtype.HostTestTest$MyAnnotation2");
        assertEquals("Incorrect test case count", 1, mHostTest.countTestCases());
    }

    /**
     * Test success case for {@link HostTest#run(ITestInvocationListener)}, where test to run is a
     * {@link TestCase} with annotation filtering.
     */
    public void testRun_testcaseAnnotationFiltering() throws Exception {
        mHostTest.setClassName(SuccessTestCase.class.getName());
        mHostTest.addIncludeAnnotation("com.android.tradefed.testtype.HostTestTest$MyAnnotation");
        TestIdentifier test1 = new TestIdentifier(SuccessTestCase.class.getName(), "testPass");
        TestIdentifier test2 = new TestIdentifier(SuccessTestCase.class.getName(), "testPass2");
        mListener.testRunStarted((String)EasyMock.anyObject(), EasyMock.eq(2));
        mListener.testStarted(EasyMock.eq(test1));
        mListener.testEnded(EasyMock.eq(test1), (Map<String, String>)EasyMock.anyObject());
        mListener.testStarted(EasyMock.eq(test2));
        mListener.testEnded(EasyMock.eq(test2), (Map<String, String>)EasyMock.anyObject());
        mListener.testRunEnded(EasyMock.anyLong(), (Map<String, String>)EasyMock.anyObject());
        EasyMock.replay(mListener);
        mHostTest.run(mListener);
        EasyMock.verify(mListener);
    }

    /**
     * Test success case for {@link HostTest#run(ITestInvocationListener)}, where test to run is a
     * {@link TestCase} with notAnnotationFiltering
     */
    public void testRun_testcaseNotAnnotationFiltering() throws Exception {
        mHostTest.setClassName(SuccessTestCase.class.getName());
        mHostTest.addExcludeAnnotation("com.android.tradefed.testtype.HostTestTest$MyAnnotation2");
        TestIdentifier test1 = new TestIdentifier(SuccessTestCase.class.getName(), "testPass");
        // Only test1 will run, test2 should be filtered out.
        mListener.testRunStarted((String)EasyMock.anyObject(), EasyMock.eq(1));
        mListener.testStarted(EasyMock.eq(test1));
        mListener.testEnded(EasyMock.eq(test1), (Map<String, String>)EasyMock.anyObject());
        mListener.testRunEnded(EasyMock.anyLong(), (Map<String, String>)EasyMock.anyObject());
        EasyMock.replay(mListener);
        mHostTest.run(mListener);
        EasyMock.verify(mListener);
    }

    /**
     * Test success case for {@link HostTest#run(ITestInvocationListener)}, where test to run is a
     * {@link TestCase} with both annotation filtering.
     */
    public void testRun_testcaseBothAnnotationFiltering() throws Exception {
        mHostTest.setClassName(AnotherTestCase.class.getName());
        mHostTest.addIncludeAnnotation("com.android.tradefed.testtype.HostTestTest$MyAnnotation");
        mHostTest.addExcludeAnnotation("com.android.tradefed.testtype.HostTestTest$MyAnnotation2");
        TestIdentifier test4 = new TestIdentifier(AnotherTestCase.class.getName(), "testPass4");
        // Only a test with MyAnnotation and Without MyAnnotation2 will run. Here testPass4
        mListener.testRunStarted((String)EasyMock.anyObject(), EasyMock.eq(1));
        mListener.testStarted(EasyMock.eq(test4));
        mListener.testEnded(EasyMock.eq(test4), (Map<String, String>)EasyMock.anyObject());
        mListener.testRunEnded(EasyMock.anyLong(), (Map<String, String>)EasyMock.anyObject());
        EasyMock.replay(mListener);
        mHostTest.run(mListener);
        EasyMock.verify(mListener);
    }

    /**
     * Test success case for {@link HostTest#run(ITestInvocationListener)}, where test to run is a
     * {@link TestCase} with multiple include annotation, test must contains them all.
     */
    public void testRun_testcaseMultiInclude() throws Exception {
        mHostTest.setClassName(AnotherTestCase.class.getName());
        mHostTest.addIncludeAnnotation("com.android.tradefed.testtype.HostTestTest$MyAnnotation");
        mHostTest.addIncludeAnnotation("com.android.tradefed.testtype.HostTestTest$MyAnnotation2");
        TestIdentifier test3 = new TestIdentifier(AnotherTestCase.class.getName(), "testPass3");
        // Only a test with MyAnnotation and with MyAnnotation2 will run. Here testPass3
        mListener.testRunStarted((String)EasyMock.anyObject(), EasyMock.eq(1));
        mListener.testStarted(EasyMock.eq(test3));
        mListener.testEnded(EasyMock.eq(test3), (Map<String, String>)EasyMock.anyObject());
        mListener.testRunEnded(EasyMock.anyLong(), (Map<String, String>)EasyMock.anyObject());
        EasyMock.replay(mListener);
        mHostTest.run(mListener);
        EasyMock.verify(mListener);
    }

    /**
     * Test success case for {@link HostTest#shouldTestRun(AnnotatedElement)}, where a class is
     * properly annotated to run.
     */
    public void testRun_shouldTestRun_Success() throws Exception {
        mHostTest.addIncludeAnnotation("com.android.tradefed.testtype.HostTestTest$MyAnnotation");
        assertTrue(mHostTest.shouldTestRun(SuccessTestCase.class));
    }

    /**
     * Test success case for {@link HostTest#shouldTestRun(AnnotatedElement)}, where a class is
     * properly annotated to run with multiple annotation expected.
     */
    public void testRun_shouldTestRunMulti_Success() throws Exception {
        mHostTest.addIncludeAnnotation("com.android.tradefed.testtype.HostTestTest$MyAnnotation");
        mHostTest.addExcludeAnnotation("com.android.tradefed.testtype.HostTestTest$MyAnnotation2");
        assertTrue(mHostTest.shouldTestRun(AnotherTestCase.class));
    }

    /**
     * Test case for {@link HostTest#shouldTestRun(AnnotatedElement)}, where a class is
     * properly annotated to be filtered.
     */
    public void testRun_shouldNotRun() throws Exception {
        mHostTest.addExcludeAnnotation("com.android.tradefed.testtype.HostTestTest$MyAnnotation");
        assertFalse(mHostTest.shouldTestRun(SuccessTestCase.class));
    }

    /**
     * Test case for {@link HostTest#shouldTestRun(AnnotatedElement)}, where a class is
     * properly annotated to be filtered because one of its two annotations is part of the exclude.
     */
    public void testRun_shouldNotRunMulti() throws Exception {
        mHostTest.addExcludeAnnotation("com.android.tradefed.testtype.HostTestTest$MyAnnotation");
        assertFalse(mHostTest.shouldTestRun(SuccessTestCase.class));
        mHostTest = new HostTest();
        // If only the other annotation is excluded.
        mHostTest.addExcludeAnnotation("com.android.tradefed.testtype.HostTestTest$MyAnnotation3");
        assertFalse(mHostTest.shouldTestRun(SuccessTestCase.class));
    }

    /**
     * Test success case for {@link HostTest#shouldTestRun(AnnotatedElement)}, where a class is
     * annotated with a different annotation from the exclude filter.
     */
    public void testRun_shouldRun_exclude() throws Exception {
        mHostTest.addExcludeAnnotation("com.android.tradefed.testtype.HostTestTest$MyAnnotation2");
        assertTrue(mHostTest.shouldTestRun(SuccessTestCase.class));
    }

    /**
     * Test success case for {@link HostTest#run(ITestInvocationListener)}, where test to run is a
     * {@link TestCase} with annotation filtering.
     */
    public void testRun_testcaseCollectMode() throws Exception {
        mHostTest.setClassName(SuccessTestCase.class.getName());
        mHostTest.setCollectTestsOnly(true);
        mListener.testRunStarted((String)EasyMock.anyObject(), EasyMock.eq(2));
        mListener.testStarted((TestIdentifier) EasyMock.anyObject());
        mListener.testEnded((TestIdentifier) EasyMock.anyObject(),
                (Map<String, String>)EasyMock.anyObject());
        mListener.testStarted((TestIdentifier) EasyMock.anyObject());
        mListener.testEnded((TestIdentifier) EasyMock.anyObject(),
                (Map<String, String>)EasyMock.anyObject());
        mListener.testRunEnded(EasyMock.anyLong(), (Map<String, String>)EasyMock.anyObject());
        EasyMock.replay(mListener);
        mHostTest.run(mListener);
        EasyMock.verify(mListener);
    }

    /**
     * Test success case for {@link HostTest#run(ITestInvocationListener)}, where the
     * {@link IRemoteTest} does not implements {@link ITestCollector}
     */
    public void testRun_testcaseCollectMode_IRemotedevice() throws Exception {
        final ITestDevice device = EasyMock.createMock(ITestDevice.class);
        mHostTest.setClassName(TestRemoteNotCollector.class.getName());
        mHostTest.setDevice(device);
        mHostTest.setCollectTestsOnly(true);
        EasyMock.replay(mListener);
        try {
            mHostTest.run(mListener);
        } catch (IllegalArgumentException expected) {
            EasyMock.verify(mListener);
            return;
        }
        fail("HostTest run() should have thrown an exception.");
    }

    /**
     * Test for {@link HostTest#run(ITestInvocationListener)}, for test with Junit4 style.
     */
    public void testRun_junit4style() throws Exception {
        mHostTest.setClassName(Junit4TestClass.class.getName());
        TestIdentifier test1 = new TestIdentifier(Junit4TestClass.class.getName(), "testPass5");
        TestIdentifier test2 = new TestIdentifier(Junit4TestClass.class.getName(), "testPass6");
        mListener.testRunStarted((String)EasyMock.anyObject(), EasyMock.eq(2));
        mListener.testStarted(EasyMock.eq(test1));
        Map<String, String> metrics = new HashMap<>();
        metrics.put("key", "value");
        mListener.testEnded(test1, metrics);
        mListener.testStarted(EasyMock.eq(test2));
        // test cases do not share metrics.
        Map<String, String> metrics2 = new HashMap<>();
        metrics2.put("key2", "value2");
        mListener.testEnded(test2, metrics2);
        mListener.testRunEnded(EasyMock.anyLong(), EasyMock.anyObject());
        EasyMock.replay(mListener);
        mHostTest.run(mListener);
        EasyMock.verify(mListener);
    }

    /**
     * Test for {@link HostTest#run(ITestInvocationListener)}, for test with Junit4 style and
     * handling of @Ignored.
     */
    public void testRun_junit4style_ignored() throws Exception {
        mHostTest.setClassName(Junit4TestClassWithIgnore.class.getName());
        TestIdentifier test1 =
                new TestIdentifier(Junit4TestClassWithIgnore.class.getName(), "testPass5");
        TestIdentifier test2 =
                new TestIdentifier(Junit4TestClassWithIgnore.class.getName(), "testPass6");
        mListener.testRunStarted((String) EasyMock.anyObject(), EasyMock.eq(2));
        mListener.testStarted(EasyMock.eq(test1));
        mListener.testEnded(EasyMock.eq(test1), (Map<String, String>) EasyMock.anyObject());
        mListener.testStarted(EasyMock.eq(test2));
        mListener.testIgnored(EasyMock.eq(test2));
        mListener.testEnded(EasyMock.eq(test2), (Map<String, String>) EasyMock.anyObject());
        mListener.testRunEnded(EasyMock.anyLong(), (Map<String, String>) EasyMock.anyObject());
        EasyMock.replay(mListener);
        mHostTest.run(mListener);
        EasyMock.verify(mListener);
    }

    /**
     * Test for {@link HostTest#run(ITestInvocationListener)}, for test with Junit4 style and with
     * method filtering. Only run the expected method.
     */
    public void testRun_junit4_withMethodFilter() throws Exception {
        mHostTest.setClassName(Junit4TestClass.class.getName());
        TestIdentifier test2 = new TestIdentifier(Junit4TestClass.class.getName(), "testPass6");
        mHostTest.setMethodName("testPass6");
        mListener.testRunStarted((String)EasyMock.anyObject(), EasyMock.eq(1));
        mListener.testStarted(EasyMock.eq(test2));
        mListener.testEnded(EasyMock.eq(test2), (Map<String, String>)EasyMock.anyObject());
        mListener.testRunEnded(EasyMock.anyLong(), (Map<String, String>)EasyMock.anyObject());
        EasyMock.replay(mListener);
        mHostTest.run(mListener);
        EasyMock.verify(mListener);
    }

    /**
     * Test for {@link HostTest#run(ITestInvocationListener)}, for a mix of test junit3 and 4
     */
    public void testRun_junit_version_mix() throws Exception {
        OptionSetter setter = new OptionSetter(mHostTest);
        setter.setOptionValue("class", SuccessTestCase.class.getName());
        setter.setOptionValue("class", Junit4TestClass.class.getName());
        runMixJunitTest(mHostTest, 2, 2);
    }

    /**
     * Test for {@link HostTest#run(ITestInvocationListener)}, for a mix of test junit3 and 4 in
     * collect only mode
     */
    public void testRun_junit_version_mix_collect() throws Exception {
        OptionSetter setter = new OptionSetter(mHostTest);
        setter.setOptionValue("class", SuccessTestCase.class.getName());
        setter.setOptionValue("class", Junit4TestClass.class.getName());
        setter.setOptionValue("collect-tests-only", "true");
        runMixJunitTest(mHostTest, 2, 2);
    }

    /**
     * Test for {@link HostTest#run(ITestInvocationListener)}, for a mix of test junit3 and 4 in
     * a Junit 4 suite class.
     */
    public void testRun_junit_suite_mix() throws Exception {
        OptionSetter setter = new OptionSetter(mHostTest);
        setter.setOptionValue("class", Junit4SuiteClass.class.getName());
        runMixJunitTest(mHostTest, 4, 1);
    }

    /**
     * Test for {@link HostTest#run(ITestInvocationListener)}, for a mix of test junit3 and 4 in
     * a Junit 4 suite class, in collect only mode.
     */
    public void testRun_junit_suite_mix_collect() throws Exception {
        OptionSetter setter = new OptionSetter(mHostTest);
        setter.setOptionValue("class", Junit4SuiteClass.class.getName());
        setter.setOptionValue("collect-tests-only", "true");
        runMixJunitTest(mHostTest, 4, 1);
    }

    /**
     * Helper for test option variation and avoid repeating the same setup
     */
    private void runMixJunitTest(HostTest hostTest, int expectedTest, int expectedRun)
            throws Exception {
        TestIdentifier test1 = new TestIdentifier(SuccessTestCase.class.getName(), "testPass");
        TestIdentifier test2 = new TestIdentifier(SuccessTestCase.class.getName(), "testPass2");
        TestIdentifier test3 = new TestIdentifier(Junit4TestClass.class.getName(), "testPass5");
        TestIdentifier test4 = new TestIdentifier(Junit4TestClass.class.getName(), "testPass6");
        mListener.testRunStarted((String)EasyMock.anyObject(), EasyMock.eq(expectedTest));
        EasyMock.expectLastCall().times(expectedRun);
        mListener.testStarted(EasyMock.eq(test1));
        mListener.testEnded(EasyMock.eq(test1), (Map<String, String>)EasyMock.anyObject());
        mListener.testStarted(EasyMock.eq(test2));
        mListener.testEnded(EasyMock.eq(test2), (Map<String, String>)EasyMock.anyObject());
        mListener.testStarted(EasyMock.eq(test3));
        mListener.testEnded(EasyMock.eq(test3), (Map<String, String>)EasyMock.anyObject());
        mListener.testStarted(EasyMock.eq(test4));
        mListener.testEnded(EasyMock.eq(test4), (Map<String, String>)EasyMock.anyObject());
        mListener.testRunEnded(EasyMock.anyLong(), (Map<String, String>)EasyMock.anyObject());
        EasyMock.expectLastCall().times(expectedRun);
        EasyMock.replay(mListener);
        hostTest.run(mListener);
        EasyMock.verify(mListener);
    }

    /**
     * Test success case for {@link HostTest#run(ITestInvocationListener)} with a filtering and
     * junit 4 handling.
     */
    public void testRun_testcase_Junit4TestNotAnnotationFiltering() throws Exception {
        mHostTest.setClassName(Junit4TestClass.class.getName());
        mHostTest.addExcludeAnnotation("com.android.tradefed.testtype.HostTestTest$MyAnnotation2");
        TestIdentifier test1 = new TestIdentifier(Junit4TestClass.class.getName(), "testPass6");
        // Only test1 will run, test2 should be filtered out.
        mListener.testRunStarted((String)EasyMock.anyObject(), EasyMock.eq(1));
        mListener.testStarted(EasyMock.eq(test1));
        mListener.testEnded(EasyMock.eq(test1), (Map<String, String>)EasyMock.anyObject());
        mListener.testRunEnded(EasyMock.anyLong(), (Map<String, String>)EasyMock.anyObject());
        EasyMock.replay(mListener);
        mHostTest.run(mListener);
        EasyMock.verify(mListener);
    }

    /**
     * Test success case for {@link HostTest#run(ITestInvocationListener)}, where filtering is
     * applied and results in 0 tests to run.
     */
    public void testRun_testcase_Junit4Test_filtering_no_more_tests() throws Exception {
        mHostTest.setClassName(Junit4TestClass.class.getName());
        mHostTest.addExcludeAnnotation("com.android.tradefed.testtype.HostTestTest$MyAnnotation");
        mListener.testRunStarted((String)EasyMock.anyObject(), EasyMock.eq(0));
        mListener.testRunEnded(EasyMock.anyLong(), (Map<String, String>)EasyMock.anyObject());
        EasyMock.replay(mListener);
        mHostTest.run(mListener);
        EasyMock.verify(mListener);
    }

    /**
     * Test that in case the class attempted to be ran is malformed we bubble up the test failure.
     */
    public void testRun_Junit4Test_malformed() throws Exception {
        mHostTest.setClassName(Junit4MalformedTestClass.class.getName());
        mListener.testRunStarted((String)EasyMock.anyObject(), EasyMock.eq(1));
        Capture<TestIdentifier> captured = new Capture<>();
        mListener.testStarted(EasyMock.capture(captured));
        mListener.testFailed((TestIdentifier)EasyMock.anyObject(), (String)EasyMock.anyObject());
        mListener.testEnded((TestIdentifier)EasyMock.anyObject(), EasyMock.anyObject());
        mListener.testRunEnded(EasyMock.anyLong(), (Map<String, String>)EasyMock.anyObject());
        EasyMock.replay(mListener);
        mHostTest.run(mListener);
        assertEquals(Junit4MalformedTestClass.class.getName(), captured.getValue().getClassName());
        assertEquals("initializationError", captured.getValue().getTestName());
        EasyMock.verify(mListener);
    }

    /**
     * Test for {@link HostTest#run(ITestInvocationListener)}, for a mix of test junit3 and 4 in
     * a Junit 4 suite class, and filtering is applied.
     */
    public void testRun_junit_suite_mix_filtering() throws Exception {
        OptionSetter setter = new OptionSetter(mHostTest);
        setter.setOptionValue("class", Junit4SuiteClass.class.getName());
        runMixJunitTestWithFilter(mHostTest);
    }

    /**
     * Test for {@link HostTest#run(ITestInvocationListener)}, for a mix of test junit3 and 4 in
     * a Junit 4 suite class, and filtering is applied, in collect mode
     */
    public void testRun_junit_suite_mix_filtering_collect() throws Exception {
        OptionSetter setter = new OptionSetter(mHostTest);
        setter.setOptionValue("class", Junit4SuiteClass.class.getName());
        setter.setOptionValue("collect-tests-only", "true");
        runMixJunitTestWithFilter(mHostTest);
    }

    /**
     * Helper for test option variation and avoid repeating the same setup
     */
    private void runMixJunitTestWithFilter(HostTest hostTest) throws Exception {
        hostTest.addExcludeAnnotation("com.android.tradefed.testtype.HostTestTest$MyAnnotation2");
        TestIdentifier test1 = new TestIdentifier(SuccessTestCase.class.getName(), "testPass");
        TestIdentifier test4 = new TestIdentifier(Junit4TestClass.class.getName(), "testPass6");
        mListener.testRunStarted((String)EasyMock.anyObject(), EasyMock.eq(2));
        EasyMock.expectLastCall().times(1);
        mListener.testStarted(EasyMock.eq(test1));
        mListener.testEnded(EasyMock.eq(test1), (Map<String, String>)EasyMock.anyObject());
        mListener.testStarted(EasyMock.eq(test4));
        mListener.testEnded(EasyMock.eq(test4), (Map<String, String>)EasyMock.anyObject());
        mListener.testRunEnded(EasyMock.anyLong(), (Map<String, String>)EasyMock.anyObject());
        EasyMock.expectLastCall().times(1);
        EasyMock.replay(mListener);
        hostTest.run(mListener);
        EasyMock.verify(mListener);
    }

    /**
     * Test for {@link HostTest#split()} making sure each test type is properly handled and added
     * with a container or directly.
     */
    public void testRun_junit_suite_split() throws Exception {
        OptionSetter setter = new OptionSetter(mHostTest);
        mHostTest.setDevice(mMockDevice);
        mHostTest.setBuild(mMockBuildInfo);
        setter.setOptionValue("class", Junit4SuiteClass.class.getName());
        setter.setOptionValue("class", SuccessTestSuite.class.getName());
        setter.setOptionValue("class", TestRemoteNotCollector.class.getName());
        List<IRemoteTest> list = (ArrayList<IRemoteTest>) mHostTest.split();
        assertEquals(3, list.size());
        assertEquals("com.android.tradefed.testtype.HostTest",
                list.get(0).getClass().getName());
        assertEquals("com.android.tradefed.testtype.HostTest",
                list.get(1).getClass().getName());
        assertEquals("com.android.tradefed.testtype.HostTest",
                list.get(2).getClass().getName());

        // We expect all the test from the JUnit4 suite to run under the original suite classname
        // not under the container class name.
        mListener.testRunStarted(
                EasyMock.eq("com.android.tradefed.testtype.HostTestTest$Junit4SuiteClass"),
                EasyMock.eq(4));
        TestIdentifier test1 = new TestIdentifier(Junit4TestClass.class.getName(), "testPass5");
        TestIdentifier test2 = new TestIdentifier(SuccessTestCase.class.getName(), "testPass");
        TestIdentifier test3 = new TestIdentifier(SuccessTestCase.class.getName(), "testPass2");
        TestIdentifier test4 = new TestIdentifier(Junit4TestClass.class.getName(), "testPass6");
        mListener.testStarted(test1);
        mListener.testEnded(EasyMock.eq(test1), (Map<String, String>)EasyMock.anyObject());
        mListener.testStarted(EasyMock.eq(test2));
        mListener.testEnded(EasyMock.eq(test2), (Map<String, String>)EasyMock.anyObject());
        mListener.testStarted(EasyMock.eq(test3));
        mListener.testEnded(EasyMock.eq(test3), (Map<String, String>)EasyMock.anyObject());
        mListener.testStarted(EasyMock.eq(test4));
        mListener.testEnded(EasyMock.eq(test4), (Map<String, String>)EasyMock.anyObject());
        mListener.testRunEnded(EasyMock.anyLong(), (Map<String, String>)EasyMock.anyObject());
        EasyMock.replay(mListener);
        // Run the JUnit4 Container
        ((IBuildReceiver)list.get(0)).setBuild(mMockBuildInfo);
        ((IDeviceTest)list.get(0)).setDevice(mMockDevice);
        list.get(0).run(mListener);
        EasyMock.verify(mListener);
    }

    /**
     * Test for {@link HostTest#split()} when no class is specified throws an exception
     */
    public void testSplit_noClass() throws Exception {
        try {
            mHostTest.split();
            fail("Should have thrown an exception");
        } catch (IllegalArgumentException e) {
            assertEquals("Missing Test class name", e.getMessage());
        }
    }

    /**
     * Test for {@link HostTest#split()} when multiple classes are specified with a method option
     * too throws an exception
     */
    public void testSplit_methodAndMultipleClass() throws Exception {
        OptionSetter setter = new OptionSetter(mHostTest);
        setter.setOptionValue("class", Junit4SuiteClass.class.getName());
        setter.setOptionValue("class", SuccessTestSuite.class.getName());
        mHostTest.setMethodName("testPass2");
        try {
            mHostTest.split();
            fail("Should have thrown an exception");
        } catch (IllegalArgumentException e) {
            assertEquals("Method name given with multiple test classes", e.getMessage());
        }
    }

    /**
     * Test for {@link HostTest#split()} when a single class is specified, no splitting can occur
     * and it returns null.
     */
    public void testSplit_singleClass() throws Exception {
        OptionSetter setter = new OptionSetter(mHostTest);
        setter.setOptionValue("class", SuccessTestSuite.class.getName());
        mHostTest.setMethodName("testPass2");
        assertNull(mHostTest.split());
    }

    /**
     * Test for {@link HostTest#getTestShard(int, int)} with one shard per classes, the runtime hint
     * is also split across tests based on number of tests.
     */
    public void testGetTestStrictShardable() throws Exception {
        OptionSetter setter = new OptionSetter(mHostTest);
        setter.setOptionValue("class", Junit4SuiteClass.class.getName());
        setter.setOptionValue("class", SuccessTestSuite.class.getName());
        setter.setOptionValue("class", TestRemoteNotCollector.class.getName());
        setter.setOptionValue("runtime-hint", "2m");
        IRemoteTest shard0 = mHostTest.getTestShard(3, 0);
        assertTrue(shard0 instanceof HostTest);
        assertEquals(1, ((HostTest)shard0).getClasses().size());
        assertEquals("com.android.tradefed.testtype.HostTestTest$Junit4SuiteClass",
                ((HostTest)shard0).getClasses().get(0).getName());
        // This shard contains 4 out of 7 tests: (4/7) * 2 minutes
        assertEquals(68571, ((HostTest) shard0).getRuntimeHint());

        IRemoteTest shard1 = mHostTest.getTestShard(3, 1);
        assertTrue(shard1 instanceof HostTest);
        assertEquals(1, ((HostTest)shard1).getClasses().size());
        assertEquals("com.android.tradefed.testtype.HostTestTest$SuccessTestSuite",
                ((HostTest)shard1).getClasses().get(0).getName());
        // This shard contains 2 out of 7 tests: (2/7) * 2 minutes
        assertEquals(34285, ((HostTest) shard1).getRuntimeHint());

        IRemoteTest shard2 = mHostTest.getTestShard(3, 2);
        assertTrue(shard2 instanceof HostTest);
        assertEquals(1, ((HostTest)shard2).getClasses().size());
        assertEquals("com.android.tradefed.testtype.HostTestTest$TestRemoteNotCollector",
                ((HostTest)shard2).getClasses().get(0).getName());
        // This shard contains 1 out of 7 tests: (1/7) * 2 minutes
        assertEquals(17142, ((HostTest) shard2).getRuntimeHint());
    }

    /**
     * Test for {@link HostTest#getTestShard(int, int)} when more shard than classes are requested,
     * the empty shard will have no test (StubTest).
     */
    public void testGetTestStrictShardable_tooManyShards() throws Exception {
        OptionSetter setter = new OptionSetter(mHostTest);
        setter.setOptionValue("class", Junit4SuiteClass.class.getName());
        setter.setOptionValue("class", SuccessTestSuite.class.getName());
        IRemoteTest shard0 = mHostTest.getTestShard(4, 0);
        assertTrue(shard0 instanceof HostTest);
        assertEquals(1, ((HostTest)shard0).getClasses().size());
        assertEquals("com.android.tradefed.testtype.HostTestTest$Junit4SuiteClass",
                ((HostTest)shard0).getClasses().get(0).getName());

        IRemoteTest shard1 = mHostTest.getTestShard(4, 1);
        assertTrue(shard1 instanceof HostTest);
        assertEquals(1, ((HostTest)shard1).getClasses().size());
        assertEquals("com.android.tradefed.testtype.HostTestTest$SuccessTestSuite",
                ((HostTest)shard1).getClasses().get(0).getName());

        IRemoteTest shard2 = mHostTest.getTestShard(4, 2);
        assertTrue(shard2 instanceof HostTest);
        assertEquals(0, ((HostTest)shard2).getClasses().size());
        IRemoteTest shard3 = mHostTest.getTestShard(4, 3);
        assertTrue(shard3 instanceof HostTest);
        assertEquals(0, ((HostTest)shard3).getClasses().size());
        // empty shard that can run and be skipped without reporting anything
        ITestInvocationListener mockListener = EasyMock.createMock(ITestInvocationListener.class);
        EasyMock.replay(mockListener);
        shard3.run(mockListener);
        EasyMock.verify(mockListener);
    }

    /**
     * Test for {@link HostTest#getTestShard(int, int)} with one shard per classes.
     */
    public void testGetTestStrictShardable_wrapping() throws Exception {
        final ITestDevice device = EasyMock.createMock(ITestDevice.class);
        mHostTest.setDevice(device);
        OptionSetter setter = new OptionSetter(mHostTest);
        setter.setOptionValue("class", Junit4SuiteClass.class.getName());
        setter.setOptionValue("class", SuccessTestSuite.class.getName());
        setter.setOptionValue("class", TestRemoteNotCollector.class.getName());
        setter.setOptionValue("class", SuccessHierarchySuite.class.getName());
        setter.setOptionValue("class", SuccessDeviceTest.class.getName());
        IRemoteTest shard0 = mHostTest.getTestShard(3, 0);
        assertTrue(shard0 instanceof HostTest);
        assertEquals(2, ((HostTest)shard0).getClasses().size());
        assertEquals("com.android.tradefed.testtype.HostTestTest$Junit4SuiteClass",
                ((HostTest)shard0).getClasses().get(0).getName());
        assertEquals("com.android.tradefed.testtype.HostTestTest$SuccessHierarchySuite",
                ((HostTest)shard0).getClasses().get(1).getName());

        IRemoteTest shard1 = mHostTest.getTestShard(3, 1);
        assertTrue(shard1 instanceof HostTest);
        assertEquals(2, ((HostTest)shard1).getClasses().size());
        assertEquals("com.android.tradefed.testtype.HostTestTest$SuccessTestSuite",
                ((HostTest)shard1).getClasses().get(0).getName());
        assertEquals("com.android.tradefed.testtype.HostTestTest$SuccessDeviceTest",
                ((HostTest)shard1).getClasses().get(1).getName());

        IRemoteTest shard2 = mHostTest.getTestShard(3, 2);
        assertTrue(shard2 instanceof HostTest);
        assertEquals(1, ((HostTest)shard2).getClasses().size());
        assertEquals("com.android.tradefed.testtype.HostTestTest$TestRemoteNotCollector",
                ((HostTest)shard2).getClasses().get(0).getName());
    }

    /** An annotation on the class exclude it. All the method of the class should be excluded. */
    public void testClassAnnotation_excludeAll() throws Exception {
        mHostTest.setClassName(SuccessTestCase.class.getName());
        mHostTest.addExcludeAnnotation("com.android.tradefed.testtype.HostTestTest$MyAnnotation3");
        assertEquals(0, mHostTest.countTestCases());
        // nothing run.
        EasyMock.replay(mListener);
        mHostTest.run(mListener);
        EasyMock.verify(mListener);
    }

    /** An annotation on the class include it. We include all the method inside it. */
    public void testClassAnnotation_includeAll() throws Exception {
        mHostTest.setClassName(SuccessTestCase.class.getName());
        mHostTest.addIncludeAnnotation("com.android.tradefed.testtype.HostTestTest$MyAnnotation3");
        assertEquals(2, mHostTest.countTestCases());
        TestIdentifier test1 = new TestIdentifier(SuccessTestCase.class.getName(), "testPass");
        TestIdentifier test2 = new TestIdentifier(SuccessTestCase.class.getName(), "testPass2");
        mListener.testRunStarted((String) EasyMock.anyObject(), EasyMock.eq(2));
        mListener.testStarted(EasyMock.eq(test1));
        mListener.testEnded(EasyMock.eq(test1), (Map<String, String>) EasyMock.anyObject());
        mListener.testStarted(EasyMock.eq(test2));
        mListener.testEnded(EasyMock.eq(test2), (Map<String, String>) EasyMock.anyObject());
        mListener.testRunEnded(EasyMock.anyLong(), (Map<String, String>) EasyMock.anyObject());
        EasyMock.replay(mListener);
        mHostTest.run(mListener);
        EasyMock.verify(mListener);
    }

    /**
     * An annotation on the method (no annotation on class) exclude it. This method does not run.
     */
    public void testMethodAnnotation_excludeAll() throws Exception {
        mHostTest.setClassName(AnotherTestCase.class.getName());
        mHostTest.addExcludeAnnotation("com.android.tradefed.testtype.HostTestTest$MyAnnotation3");
        assertEquals(1, mHostTest.countTestCases());
        TestIdentifier test1 = new TestIdentifier(AnotherTestCase.class.getName(), "testPass4");
        mListener.testRunStarted((String) EasyMock.anyObject(), EasyMock.eq(1));
        mListener.testStarted(EasyMock.eq(test1));
        mListener.testEnded(EasyMock.eq(test1), (Map<String, String>) EasyMock.anyObject());
        mListener.testRunEnded(EasyMock.anyLong(), (Map<String, String>) EasyMock.anyObject());
        EasyMock.replay(mListener);
        mHostTest.run(mListener);
        EasyMock.verify(mListener);
    }

    /** An annotation on the method (no annotation on class) include it. Only this method run. */
    public void testMethodAnnotation_includeAll() throws Exception {
        mHostTest.setClassName(AnotherTestCase.class.getName());
        mHostTest.addIncludeAnnotation("com.android.tradefed.testtype.HostTestTest$MyAnnotation3");
        assertEquals(1, mHostTest.countTestCases());
        TestIdentifier test1 = new TestIdentifier(AnotherTestCase.class.getName(), "testPass3");
        mListener.testRunStarted((String) EasyMock.anyObject(), EasyMock.eq(1));
        mListener.testStarted(EasyMock.eq(test1));
        mListener.testEnded(EasyMock.eq(test1), (Map<String, String>) EasyMock.anyObject());
        mListener.testRunEnded(EasyMock.anyLong(), (Map<String, String>) EasyMock.anyObject());
        EasyMock.replay(mListener);
        mHostTest.run(mListener);
        EasyMock.verify(mListener);
    }

    /**
     * Check that a method annotation in a {@link DeviceTestCase} is properly included with an
     * include filter during collect-tests-only
     */
    public void testMethodAnnotation_includeAll_collect() throws Exception {
        mHostTest.setCollectTestsOnly(true);
        mHostTest.setClassName(SuccessDeviceTest2.class.getName());
        mHostTest.addIncludeAnnotation("com.android.tradefed.testtype.HostTestTest$MyAnnotation3");
        assertEquals(1, mHostTest.countTestCases());
        TestIdentifier test1 = new TestIdentifier(SuccessDeviceTest2.class.getName(), "testPass1");
        mListener.testRunStarted((String) EasyMock.anyObject(), EasyMock.eq(1));
        mListener.testStarted(EasyMock.eq(test1));
        mListener.testEnded(EasyMock.eq(test1), (Map<String, String>) EasyMock.anyObject());
        mListener.testRunEnded(EasyMock.anyLong(), (Map<String, String>) EasyMock.anyObject());
        EasyMock.replay(mListener);
        mHostTest.run(mListener);
        EasyMock.verify(mListener);
    }

    /**
     * Test that a method annotated and overriden is not included because the child method is not
     * annotated (annotation are not inherited).
     */
    public void testMethodAnnotation_inherited() throws Exception {
        mHostTest.setCollectTestsOnly(true);
        mHostTest.setClassName(InheritedDeviceTest3.class.getName());
        mHostTest.addIncludeAnnotation("com.android.tradefed.testtype.HostTestTest$MyAnnotation3");
        assertEquals(1, mHostTest.countTestCases());
        TestIdentifier test1 =
                new TestIdentifier(InheritedDeviceTest3.class.getName(), "testPass3");
        mListener.testRunStarted((String) EasyMock.anyObject(), EasyMock.eq(1));
        mListener.testStarted(EasyMock.eq(test1));
        mListener.testEnded(EasyMock.eq(test1), (Map<String, String>) EasyMock.anyObject());
        mListener.testRunEnded(EasyMock.anyLong(), (Map<String, String>) EasyMock.anyObject());
        EasyMock.replay(mListener);
        mHostTest.run(mListener);
        EasyMock.verify(mListener);
    }

    /**
     * Test that a method annotated and overriden is not excluded if the child method does not have
     * the annotation.
     */
    public void testMethodAnnotation_inherited_exclude() throws Exception {
        mHostTest.setCollectTestsOnly(true);
        mHostTest.setClassName(InheritedDeviceTest3.class.getName());
        mHostTest.addExcludeAnnotation("com.android.tradefed.testtype.HostTestTest$MyAnnotation3");
        assertEquals(2, mHostTest.countTestCases());
        TestIdentifier test1 =
                new TestIdentifier(InheritedDeviceTest3.class.getName(), "testPass1");
        TestIdentifier test2 =
                new TestIdentifier(InheritedDeviceTest3.class.getName(), "testPass2");
        mListener.testRunStarted((String) EasyMock.anyObject(), EasyMock.eq(2));
        mListener.testStarted(EasyMock.eq(test1));
        mListener.testEnded(EasyMock.eq(test1), (Map<String, String>) EasyMock.anyObject());
        mListener.testStarted(EasyMock.eq(test2));
        mListener.testEnded(EasyMock.eq(test2), (Map<String, String>) EasyMock.anyObject());
        mListener.testRunEnded(EasyMock.anyLong(), (Map<String, String>) EasyMock.anyObject());
        EasyMock.replay(mListener);
        mHostTest.run(mListener);
        EasyMock.verify(mListener);
    }

    /** Check that a {@link DeviceTestCase} is properly excluded when the class is excluded. */
    public void testDeviceTestCase_excludeClass() throws Exception {
        mHostTest.setClassName(SuccessDeviceTest2.class.getName());
        mHostTest.addExcludeAnnotation("com.android.tradefed.testtype.HostTestTest$MyAnnotation");
        assertEquals(0, mHostTest.countTestCases());
        EasyMock.replay(mListener);
        mHostTest.run(mListener);
        EasyMock.verify(mListener);
    }

    /**
     * Check that a {@link DeviceTestCase} is properly excluded when the class is excluded in
     * collect-tests-only mode (yielding the same result as above).
     */
    public void testDeviceTestCase_excludeClass_collect() throws Exception {
        mHostTest.setCollectTestsOnly(true);
        mHostTest.setClassName(SuccessDeviceTest2.class.getName());
        mHostTest.addExcludeAnnotation("com.android.tradefed.testtype.HostTestTest$MyAnnotation");
        assertEquals(0, mHostTest.countTestCases());
        EasyMock.replay(mListener);
        mHostTest.run(mListener);
        EasyMock.verify(mListener);
    }

    /**
     * Test that {@link HostTest#getTestShard(int, int)} returns a {@link IRemoteTest} with no
     * runtime and no tests when filtered out of all its tests.
     */
    public void testShardAndUpdateRuntime() {
        mHostTest.setClassName(SuccessTestCase.class.getName());
        mHostTest.addExcludeAnnotation("com.android.tradefed.testtype.HostTestTest$MyAnnotation");
        HostTest test = (HostTest) mHostTest.getTestShard(2, 0);
        assertEquals(0l, test.getRuntimeHint());
        assertEquals(0, test.countTestCases());
    }

    /**
     * {@link HostTest#getTestShard(int, int)} returns a {@link IRemoteTest} with no runtime and no
     * tests when filtered out of all its tests with a exclude-filter.
     */
    public void testShardAndUpdateRuntime_filters() {
        mHostTest.setClassName(SuccessDeviceTest.class.getName());
        mHostTest.addExcludeFilter(
                "com.android.tradefed.testtype.HostTestTest$SuccessDeviceTest#testPass");
        HostTest test = (HostTest) mHostTest.getTestShard(2, 0);
        assertEquals(0l, test.getRuntimeHint());
        assertEquals(0, test.countTestCases());
    }

    /**
     * Test for {@link HostTest#split()} when the exclude-filter is set, it should be carried over
     * to shards.
     */
    public void testSplit_withExclude() throws Exception {
        OptionSetter setter = new OptionSetter(mHostTest);
        setter.setOptionValue("class", SuccessTestCase.class.getName());
        setter.setOptionValue("class", AnotherTestCase.class.getName());
        mHostTest.addExcludeFilter(
                "com.android.tradefed.testtype.HostTestTest$SuccessTestCase#testPass");
        Collection<IRemoteTest> res = mHostTest.split();
        assertEquals(2, res.size());

        // only one tests in the SuccessTestCase because it's been filtered out.
        mListener.testRunStarted(
                EasyMock.eq("com.android.tradefed.testtype.HostTestTest$SuccessTestCase"),
                EasyMock.eq(1));
        TestIdentifier tid2 =
                new TestIdentifier(
                        "com.android.tradefed.testtype.HostTestTest$SuccessTestCase", "testPass2");
        mListener.testStarted(tid2);
        mListener.testEnded(tid2, Collections.emptyMap());
        mListener.testRunEnded(EasyMock.anyLong(), EasyMock.anyObject());

        mListener.testRunStarted(
                EasyMock.eq("com.android.tradefed.testtype.HostTestTest$AnotherTestCase"),
                EasyMock.eq(2));
        TestIdentifier tid3 =
                new TestIdentifier(
                        "com.android.tradefed.testtype.HostTestTest$AnotherTestCase", "testPass3");
        mListener.testStarted(tid3);
        mListener.testEnded(tid3, Collections.emptyMap());
        TestIdentifier tid4 =
                new TestIdentifier(
                        "com.android.tradefed.testtype.HostTestTest$AnotherTestCase", "testPass4");
        mListener.testStarted(tid4);
        mListener.testEnded(tid4, Collections.emptyMap());
        mListener.testRunEnded(EasyMock.anyLong(), EasyMock.anyObject());

        EasyMock.replay(mListener, mMockDevice);
        for (IRemoteTest test : res) {
            assertTrue(test instanceof HostTest);
            ((HostTest) test).setDevice(mMockDevice);
            test.run(mListener);
        }
        EasyMock.verify(mListener, mMockDevice);
    }
}
