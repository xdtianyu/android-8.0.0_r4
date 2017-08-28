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
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.config.OptionCopier;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.JUnit4ResultForwarder;
import com.android.tradefed.util.JUnit4TestFilter;
import com.android.tradefed.util.TestFilterHelper;

import com.google.common.annotations.VisibleForTesting;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.junit.internal.runners.ErrorReportingRunner;
import org.junit.runner.Description;
import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.junit.runner.RunWith;
import org.junit.runner.Runner;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.Suite.SuiteClasses;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A test runner for JUnit host based tests. If the test to be run implements {@link IDeviceTest}
 * this runner will pass a reference to the device.
 */
@OptionClass(alias = "host")
public class HostTest
        implements IDeviceTest,
                ITestFilterReceiver,
                ITestAnnotationFilterReceiver,
                IRemoteTest,
                ITestCollector,
                IBuildReceiver,
                IAbiReceiver,
                IShardableTest,
                IStrictShardableTest,
                IRuntimeHintProvider {

    @Option(name = "class", description = "The JUnit test classes to run, in the format "
            + "<package>.<class>. eg. \"com.android.foo.Bar\". This field can be repeated.",
            importance = Importance.IF_UNSET)
    private Set<String> mClasses = new LinkedHashSet<>();

    @Option(name = "method", description = "The name of the method in the JUnit TestCase to run. "
            + "eg. \"testFooBar\"",
            importance = Importance.IF_UNSET)
    private String mMethodName;

    @Option(name = "set-option", description = "Options to be passed down to the class "
            + "under test, key and value should be separated by colon \":\"; for example, if class "
            + "under test supports \"--iteration 1\" from a command line, it should be passed in as"
            + " \"--set-option iteration:1\"; escaping of \":\" is currently not supported")
    private List<String> mKeyValueOptions = new ArrayList<>();

    @Option(name = "include-annotation",
            description = "The set of annotations a test must have to be run.")
    private Set<String> mIncludeAnnotations = new HashSet<>();

    @Option(name = "exclude-annotation",
            description = "The set of annotations to exclude tests from running. A test must have "
                    + "none of the annotations in this list to run.")
    private Set<String> mExcludeAnnotations = new HashSet<>();

    @Option(name = "collect-tests-only",
            description = "Only invoke the instrumentation to collect list of applicable test "
                    + "cases. All test run callbacks will be triggered, but test execution will "
                    + "not be actually carried out.")
    private boolean mCollectTestsOnly = false;

    @Option(
        name = "runtime-hint",
        isTimeVal = true,
        description = "The hint about the test's runtime."
    )
    private long mRuntimeHint = 60000; // 1 minute

    private ITestDevice mDevice;
    private IBuildInfo mBuildInfo;
    private IAbi mAbi;
    private TestFilterHelper mFilterHelper;
    private boolean mSkipTestClassCheck = false;

    private static final String EXCLUDE_NO_TEST_FAILURE = "org.junit.runner.manipulation.Filter";
    private static final String TEST_FULL_NAME_FORMAT = "%s#%s";

    public HostTest() {
        mFilterHelper = new TestFilterHelper(new ArrayList<String>(), new ArrayList<String>(),
                mIncludeAnnotations, mExcludeAnnotations);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    /** {@inheritDoc} */
    @Override
    public long getRuntimeHint() {
        return mRuntimeHint;
    }

    /** {@inheritDoc} */
    @Override
    public void setAbi(IAbi abi) {
        mAbi = abi;
    }

    /** {@inheritDoc} */
    @Override
    public IAbi getAbi() {
        return mAbi;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mBuildInfo = buildInfo;
    }

    /**
     * Get the build info received by HostTest.
     *
     * @return the {@link IBuildInfo}
     */
    protected IBuildInfo getBuild() {
        return mBuildInfo;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addIncludeFilter(String filter) {
        mFilterHelper.addIncludeFilter(filter);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addAllIncludeFilters(Set<String> filters) {
        mFilterHelper.addAllIncludeFilters(filters);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addExcludeFilter(String filter) {
        mFilterHelper.addExcludeFilter(filter);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addAllExcludeFilters(Set<String> filters) {
        mFilterHelper.addAllExcludeFilters(filters);
    }

    /**
     * Return the number of test cases across all classes part of the tests
     */
    public int countTestCases() {
        // Ensure filters are set in the helper
        mFilterHelper.addAllIncludeAnnotation(mIncludeAnnotations);
        mFilterHelper.addAllExcludeAnnotation(mExcludeAnnotations);

        int count = 0;
        for (Class<?> classObj : getClasses()) {
            if (IRemoteTest.class.isAssignableFrom(classObj)
                    || Test.class.isAssignableFrom(classObj)) {
                TestSuite suite = collectTests(collectClasses(classObj));
                int suiteCount = suite.countTestCases();
                if (suiteCount == 0
                        && IRemoteTest.class.isAssignableFrom(classObj)
                        && !Test.class.isAssignableFrom(classObj)) {
                    // If it's a pure IRemoteTest we count the run() as one test.
                    count++;
                } else {
                    count += suiteCount;
                }
            } else if (hasJUnit4Annotation(classObj)) {
                Request req = Request.aClass(classObj);
                req = req.filterWith(new JUnit4TestFilter(mFilterHelper));
                Runner checkRunner = req.getRunner();
                // If no tests are remaining after filtering, checkRunner is ErrorReportingRunner.
                // testCount() for ErrorReportingRunner returns 1, skip this classObj in this case.
                if (checkRunner instanceof ErrorReportingRunner) {
                    if (!EXCLUDE_NO_TEST_FAILURE.equals(
                            checkRunner.getDescription().getClassName())) {
                        // If after filtering we have remaining tests that are malformed, we still
                        // count them toward the total number of tests. (each malformed class will
                        // count as 1 in the testCount()).
                        count += checkRunner.testCount();
                    }
                } else {
                    count += checkRunner.testCount();
                }
            } else {
                count++;
            }
        }
        return count;
    }

    /**
     * Clear then set a class name to be run.
     */
    protected void setClassName(String className) {
        mClasses.clear();
        mClasses.add(className);
    }

    @VisibleForTesting
    public Set<String> getClassNames() {
        return mClasses;
    }

    void setMethodName(String methodName) {
        mMethodName = methodName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addIncludeAnnotation(String annotation) {
        mIncludeAnnotations.add(annotation);
        mFilterHelper.addIncludeAnnotation(annotation);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addAllIncludeAnnotation(Set<String> annotations) {
        mIncludeAnnotations.addAll(annotations);
        mFilterHelper.addAllIncludeAnnotation(annotations);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addExcludeAnnotation(String notAnnotation) {
        mExcludeAnnotations.add(notAnnotation);
        mFilterHelper.addExcludeAnnotation(notAnnotation);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addAllExcludeAnnotation(Set<String> notAnnotations) {
        mExcludeAnnotations.addAll(notAnnotations);
        mFilterHelper.addAllExcludeAnnotation(notAnnotations);
    }

    /**
     * Helper to set the information of an object based on some of its type.
     */
    private void setTestObjectInformation(Object testObj) {
        if (testObj instanceof IBuildReceiver) {
            if (mBuildInfo == null) {
                throw new IllegalArgumentException("Missing build information");
            }
            ((IBuildReceiver)testObj).setBuild(mBuildInfo);
        }
        if (testObj instanceof IDeviceTest) {
            if (mDevice == null) {
                throw new IllegalArgumentException("Missing device");
            }
            ((IDeviceTest)testObj).setDevice(mDevice);
        }
        // We are more flexible about abi info since not always available.
        if (testObj instanceof IAbiReceiver) {
            ((IAbiReceiver)testObj).setAbi(mAbi);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        // Ensure filters are set in the helper
        mFilterHelper.addAllIncludeAnnotation(mIncludeAnnotations);
        mFilterHelper.addAllExcludeAnnotation(mExcludeAnnotations);

        List<Class<?>> classes = getClasses();
        if (!mSkipTestClassCheck) {
            if (classes.isEmpty()) {
                throw new IllegalArgumentException("Missing Test class name");
            }
        }
        if (mMethodName != null && classes.size() > 1) {
            throw new IllegalArgumentException("Method name given with multiple test classes");
        }
        for (Class<?> classObj : classes) {
            if (IRemoteTest.class.isAssignableFrom(classObj)) {
                IRemoteTest test = (IRemoteTest) loadObject(classObj);
                applyFilters(classObj, test);
                if (mCollectTestsOnly) {
                    // Collect only mode is propagated to the test.
                    if (test instanceof ITestCollector) {
                        ((ITestCollector) test).setCollectTestsOnly(true);
                    } else {
                        throw new IllegalArgumentException(
                                String.format(
                                        "%s does not implement ITestCollector", test.getClass()));
                    }
                }
                test.run(listener);
            } else if (Test.class.isAssignableFrom(classObj)) {
                if (mCollectTestsOnly) {
                    // Collect only mode, fake the junit test execution.
                    TestSuite junitTest = collectTests(collectClasses(classObj));
                    listener.testRunStarted(classObj.getName(), junitTest.countTestCases());
                    Map<String, String> empty = Collections.emptyMap();
                    for (int i = 0; i < junitTest.countTestCases(); i++) {
                        Test t = junitTest.testAt(i);
                        // Test does not have a getName method.
                        // using the toString format instead: <testName>(className)
                        String testName = t.toString().split("\\(")[0];
                        TestIdentifier testId =
                                new TestIdentifier(t.getClass().getName(), testName);
                        listener.testStarted(testId);
                        listener.testEnded(testId, empty);
                    }
                    Map<String, String> emptyMap = Collections.emptyMap();
                    listener.testRunEnded(0, emptyMap);
                } else {
                    JUnitRunUtil.runTest(listener, collectTests(collectClasses(classObj)),
                            classObj.getName());
                }
            } else if (hasJUnit4Annotation(classObj)) {
                // Running in a full JUnit4 manner, no downgrade to JUnit3 {@link Test}
                JUnitCore runnerCore = new JUnitCore();
                JUnit4ResultForwarder list = new JUnit4ResultForwarder(listener);
                runnerCore.addListener(list);
                Request req = Request.aClass(classObj);
                // Include the method name filtering
                Set<String> includes = mFilterHelper.getIncludeFilters();
                if (mMethodName != null) {
                    includes.add(String.format(TEST_FULL_NAME_FORMAT, classObj.getName(),
                            mMethodName));
                }

                req = req.filterWith(new JUnit4TestFilter(mFilterHelper));
                // If no tests are remaining after filtering, it returns an Error Runner.
                Runner checkRunner = req.getRunner();
                if (!(checkRunner instanceof ErrorReportingRunner)) {
                    long startTime = System.currentTimeMillis();
                    listener.testRunStarted(classObj.getName(), checkRunner.testCount());
                    if (mCollectTestsOnly) {
                        fakeDescriptionExecution(checkRunner.getDescription(), list);
                    } else {
                        setTestObjectInformation(checkRunner);
                        runnerCore.run(checkRunner);
                    }
                    listener.testRunEnded(System.currentTimeMillis() - startTime,
                            Collections.emptyMap());
                } else {
                    // Special case where filtering leaves no tests to run, we report no failure
                    // in this case.
                    if (EXCLUDE_NO_TEST_FAILURE.equals(
                            checkRunner.getDescription().getClassName())) {
                        listener.testRunStarted(classObj.getName(), 0);
                        listener.testRunEnded(0, Collections.emptyMap());
                    } else {
                        // Run the Error runner to get the failures from test classes.
                        listener.testRunStarted(classObj.getName(), checkRunner.testCount());
                        RunNotifier failureNotifier = new RunNotifier();
                        failureNotifier.addListener(list);
                        checkRunner.run(failureNotifier);
                        listener.testRunEnded(0, Collections.emptyMap());
                    }
                }
            } else {
                throw new IllegalArgumentException(
                        String.format("%s is not a supported test", classObj.getName()));
            }
        }
    }

    /**
     * Helper to fake the execution of JUnit4 Tests, using the {@link Description}
     */
    private void fakeDescriptionExecution(Description desc, JUnit4ResultForwarder listener) {
        if (desc.getMethodName() == null) {
            for (Description child : desc.getChildren()) {
                fakeDescriptionExecution(child, listener);
            }
        } else {
            listener.testStarted(desc);
            listener.testFinished(desc);
        }
    }

    private Set<Class<?>> collectClasses(Class<?> classObj) {
        Set<Class<?>> classes = new HashSet<>();
        if (TestSuite.class.isAssignableFrom(classObj)) {
            TestSuite testObj = (TestSuite) loadObject(classObj);
            classes.addAll(getClassesFromSuite(testObj));
        } else {
            classes.add(classObj);
        }
        return classes;
    }

    private Set<Class<?>> getClassesFromSuite(TestSuite suite) {
        Set<Class<?>> classes = new HashSet<>();
        Enumeration<Test> tests = suite.tests();
        while (tests.hasMoreElements()) {
            Test test = tests.nextElement();
            if (test instanceof TestSuite) {
                classes.addAll(getClassesFromSuite((TestSuite) test));
            } else {
                classes.addAll(collectClasses(test.getClass()));
            }
        }
        return classes;
    }

    private TestSuite collectTests(Set<Class<?>> classes) {
        TestSuite suite = new TestSuite();
        for (Class<?> classObj : classes) {
            String packageName = classObj.getPackage().getName();
            String className = classObj.getName();
            Method[] methods = null;
            if (mMethodName == null) {
                methods = classObj.getMethods();
            } else {
                try {
                    methods = new Method[] {
                            classObj.getMethod(mMethodName, (Class[]) null)
                    };
                } catch (NoSuchMethodException e) {
                    throw new IllegalArgumentException(
                            String.format("Cannot find %s#%s", className, mMethodName), e);
                }
            }

            for (Method method : methods) {
                if (!Modifier.isPublic(method.getModifiers())
                        || !method.getReturnType().equals(Void.TYPE)
                        || method.getParameterTypes().length > 0
                        || !method.getName().startsWith("test")
                        || !mFilterHelper.shouldRun(packageName, classObj, method)) {
                    continue;
                }
                Test testObj = (Test) loadObject(classObj, false);
                if (testObj instanceof TestCase) {
                    ((TestCase)testObj).setName(method.getName());
                }

                suite.addTest(testObj);
            }
        }
        return suite;
    }

    protected List<Class<?>> getClasses() throws IllegalArgumentException  {
        List<Class<?>> classes = new ArrayList<>();
        for (String className : mClasses) {
            try {
                classes.add(Class.forName(className, true, getClassLoader()));
            } catch (ClassNotFoundException e) {
                throw new IllegalArgumentException(String.format("Could not load Test class %s",
                        className), e);
            }
        }
        return classes;
    }

    /** Returns the default classloader. */
    @VisibleForTesting
    protected ClassLoader getClassLoader() {
        return this.getClass().getClassLoader();
    }

    /** load the class object and set the test info (device, build). */
    protected Object loadObject(Class<?> classObj) {
        return loadObject(classObj, true);
    }

    /**
     * Load the class object and set the test info if requested.
     *
     * @param classObj the class object to be loaded.
     * @param setInfo True the the test infos need to be set.
     * @return The loaded object from the class.
     */
    private Object loadObject(Class<?> classObj, boolean setInfo) throws IllegalArgumentException {
        final String className = classObj.getName();
        try {
            Object testObj = classObj.newInstance();
            // set options
            if (!mKeyValueOptions.isEmpty()) {
                try {
                    OptionSetter setter = new OptionSetter(testObj);
                    for (String item : mKeyValueOptions) {
                        String[] fields = item.split(":");
                        if (fields.length == 2) {
                            setter.setOptionValue(fields[0], fields[1]);
                        } else if (fields.length == 3) {
                            setter.setOptionValue(fields[0], fields[1], fields[2]);
                        } else {
                            throw new RuntimeException(
                                String.format("invalid option spec \"%s\"", item));
                        }
                    }
                } catch (ConfigurationException ce) {
                    throw new RuntimeException("error passing options down to test class", ce);
                }
            }
            // Set the test information if needed.
            if (setInfo) {
                setTestObjectInformation(testObj);
            }
            return testObj;
        } catch (InstantiationException e) {
            throw new IllegalArgumentException(String.format("Could not load Test class %s",
                    className), e);
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException(String.format("Could not load Test class %s",
                    className), e);
        }
    }

    /**
     * Check if an elements that has annotation pass the filter. Exposed for unit testing.
     * @param annotatedElement
     * @return false if the test should not run.
     */
    protected boolean shouldTestRun(AnnotatedElement annotatedElement) {
        return mFilterHelper.shouldTestRun(annotatedElement);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setCollectTestsOnly(boolean shouldCollectTest) {
        mCollectTestsOnly = shouldCollectTest;
    }

    /**
     * Helper to determine if we are dealing with a Test class with Junit4 annotations.
     */
    protected boolean hasJUnit4Annotation(Class<?> classObj) {
        if (classObj.isAnnotationPresent(SuiteClasses.class)) {
            return true;
        }
        if (classObj.isAnnotationPresent(RunWith.class)) {
            return true;
        }
        for (Method m : classObj.getMethods()) {
            if (m.isAnnotationPresent(org.junit.Test.class)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Helper method to apply all the filters to an IRemoteTest.
     */
    private void applyFilters(Class<?> classObj, IRemoteTest test) {
        Set<String> includes = mFilterHelper.getIncludeFilters();
        if (mMethodName != null) {
            includes.add(String.format(TEST_FULL_NAME_FORMAT, classObj.getName(), mMethodName));
        }
        Set<String> excludes = mFilterHelper.getExcludeFilters();
        if (test instanceof ITestFilterReceiver) {
            ((ITestFilterReceiver) test).addAllIncludeFilters(includes);
            ((ITestFilterReceiver) test).addAllExcludeFilters(excludes);
        } else if (!includes.isEmpty() || !excludes.isEmpty()) {
            throw new IllegalArgumentException(String.format(
                    "%s does not implement ITestFilterReceiver", classObj.getName()));
        }
        if (test instanceof ITestAnnotationFilterReceiver) {
            ((ITestAnnotationFilterReceiver) test).addAllIncludeAnnotation(
                    mIncludeAnnotations);
            ((ITestAnnotationFilterReceiver) test).addAllExcludeAnnotation(
                    mExcludeAnnotations);
        }
    }

    /**
     * We split by --class, and if each individual IRemoteTest is splitable we split them too.
     */
    @Override
    public Collection<IRemoteTest> split() {
        List<IRemoteTest> listTests = new ArrayList<>();
        List<Class<?>> classes = getClasses();
        if (classes.isEmpty()) {
            throw new IllegalArgumentException("Missing Test class name");
        }
        if (mMethodName != null && classes.size() > 1) {
            throw new IllegalArgumentException("Method name given with multiple test classes");
        }
        if (classes.size() == 1) {
            // Cannot shard if only no class or one class specified
            // TODO: Consider doing class sharding too if its a suite.
            return null;
        }
        for (Class<?> classObj : classes) {
            HostTest test = createHostTest(classObj);
            test.mRuntimeHint = mRuntimeHint / classes.size();
            // Carry over non-annotation filters to shards.
            test.addAllExcludeFilters(mFilterHelper.getExcludeFilters());
            test.addAllIncludeFilters(mFilterHelper.getIncludeFilters());
            listTests.add(test);
        }
        return listTests;
    }

    /**
     * Add a class to be ran by HostTest.
     */
    private void addClassName(String className) {
        mClasses.add(className);
    }

    /**
     * Helper to create a HostTest instance when sharding. Override to return any child from
     * HostTest.
     */
    protected HostTest createHostTest(Class<?> classObj) {
        HostTest test;
        try {
            test = this.getClass().newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        OptionCopier.copyOptionsNoThrow(this, test);
        if (classObj != null) {
            test.setClassName(classObj.getName());
        }
        // Copy the abi if available
        test.setAbi(mAbi);
        return test;
    }

    @Override
    public IRemoteTest getTestShard(int shardCount, int shardIndex) {
        IRemoteTest test = null;
        List<Class<?>> classes = getClasses();
        if (classes.isEmpty()) {
            throw new IllegalArgumentException("Missing Test class name");
        }
        if (mMethodName != null && classes.size() > 1) {
            throw new IllegalArgumentException("Method name given with multiple test classes");
        }
        int numTotalTestCases = countTestCases();
        int i = 0;
        for (Class<?> classObj : classes) {
            if (i % shardCount == shardIndex) {
                if (test == null) {
                    test = createHostTest(classObj);
                } else {
                    ((HostTest) test).addClassName(classObj.getName());
                }
                // Carry over non-annotation filters to shards.
                ((HostTest) test).addAllExcludeFilters(mFilterHelper.getExcludeFilters());
                ((HostTest) test).addAllIncludeFilters(mFilterHelper.getIncludeFilters());
            }
            i++;
        }
        // In case we don't have enough classes to shard, we return a Stub.
        if (test == null) {
            test = createHostTest(null);
            ((HostTest) test).mSkipTestClassCheck = true;
            ((HostTest) test).mClasses.clear();
            ((HostTest) test).mRuntimeHint = 0l;
        } else {
            int newCount = ((HostTest) test).countTestCases();
            // In case of counting inconsistency we raise the issue. Should not happen if we are
            // counting properly. Here as a security.
            if (newCount > numTotalTestCases) {
                throw new RuntimeException(
                        "Tests count number after sharding is higher than initial count.");
            }
            // update the runtime hint on pro-rate of number of tests.
            if (newCount == 0) {
                // In case there is not tests left.
                ((HostTest) test).mRuntimeHint = 0l;
            } else {
                ((HostTest) test).mRuntimeHint = (mRuntimeHint * newCount) / numTotalTestCases;
            }
        }
        return test;
    }
}
