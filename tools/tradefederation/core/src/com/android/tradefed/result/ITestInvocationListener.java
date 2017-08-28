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
package com.android.tradefed.result;

import com.android.ddmlib.testrunner.ITestRunListener;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.command.ICommandScheduler;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.log.ITestLogger;

import java.util.Map;

/**
 * Listener for test results from the test invocation.
 * <p/>
 * A test invocation can itself include multiple test runs, so the sequence of calls will be
 * <ul>
 * <li>invocationStarted(BuildInfo)</li>
 * <li>testRunStarted</li>
 * <li>testStarted</li>
 * <li>[testFailed]</li>
 * <li>testEnded</li>
 * <li>...</li>
 * <li>testRunEnded</li>
 * <li>...</li>
 * <li>testRunStarted</li>
 * <li>...</li>
 * <li>testRunEnded</li>
 * <li>[invocationFailed]</li>
 * <li>[testLog+]</li>
 * <li>invocationEnded</li>
 * <li>getSummary</li>
 * </ul>
 * <p/>
 * Note that this is re-using the {@link com.android.ddmlib.testrunner.ITestRunListener}
 * because it's a generic interface. The results being reported are not necessarily device specific.
 */
public interface ITestInvocationListener extends ITestRunListener, ITestLogger {

    /**
     * Reports the start of the test invocation.
     *
     * <p>Will be automatically called by the TradeFederation framework. Reporters need to override
     * this method to support multiple devices reporting.
     *
     * @param context information about the invocation
     */
    public default void invocationStarted(IInvocationContext context) {}

    /**
     * Reports that the invocation has terminated, whether successfully or due to some error
     * condition.
     * <p/>
     * Will be automatically called by the TradeFederation framework.
     *
     * @param elapsedTime the elapsed time of the invocation in ms
     */
    default public void invocationEnded(long elapsedTime) { }

    /**
     * Reports an incomplete invocation due to some error condition.
     * <p/>
     * Will be automatically called by the TradeFederation framework.
     *
     * @param cause the {@link Throwable} cause of the failure
     */
    default public void invocationFailed(Throwable cause) { }

    /**
     * Allows the InvocationListener to return a summary.
     *
     * @return A {@link TestSummary} summarizing the run, or null
     */
    default public TestSummary getSummary() { return null; }

    /**
     * Called on {@link ICommandScheduler#shutdown()}, gives the invocation the opportunity to do
     * something before terminating.
     */
    default public void invocationInterrupted() {
        // do nothing in default implementation.
    }

    /**
     * {@inheritDoc}
     */
    @Override
    default public void testEnded(TestIdentifier test, Map<String, String> testMetrics) { }

    /** {@inheritDoc} */
    @Override
    public default void testEnded(
            TestIdentifier test, long endTime, Map<String, String> testMetrics) {
        testEnded(test, testMetrics);
    }

    /** {@inheritDoc} */
    @Override
    public default void testFailed(TestIdentifier test, String trace) {}

    /**
     * {@inheritDoc}
     */
    @Override
    default public void testAssumptionFailure(TestIdentifier test, String trace) { }

    /**
     * {@inheritDoc}
     */
    @Override
    default public void testIgnored(TestIdentifier test) { }

    /**
     * {@inheritDoc}
     */
    @Override
    default public void testRunEnded(long elapsedTimeMillis, Map<String, String> runMetrics) { }

    /**
     * {@inheritDoc}
     */
    @Override
    default public void testRunFailed(String errorMessage) { }

    /**
     * {@inheritDoc}
     */
    @Override
    default public void testRunStarted(String runName, int testCount) { }

    /**
     * {@inheritDoc}
     */
    @Override
    default public void testRunStopped(long elapsedTime) { }

    /**
     * {@inheritDoc}
     */
    @Override
    default public void testStarted(TestIdentifier test) { }

    /** {@inheritDoc} */
    @Override
    default void testStarted(TestIdentifier test, long startTime) {
        testStarted(test);
    }
}
