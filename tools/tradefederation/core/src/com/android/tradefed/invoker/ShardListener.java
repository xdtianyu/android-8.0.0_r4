/*
 * Copyright (C) 2011 The Android Open Source Project
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

import com.android.ddmlib.Log.LogLevel;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.ddmlib.testrunner.TestResult;
import com.android.ddmlib.testrunner.TestResult.TestStatus;
import com.android.ddmlib.testrunner.TestRunResult;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;

import java.util.Map;

/**
 * A {@link ITestInvocationListener} that collects results from a invocation shard (aka an
 * invocation split to run on multiple resources in parallel), and forwards them to another
 * listener.
 */
public class ShardListener extends CollectingTestListener {

    private ITestInvocationListener mMasterListener;

    /**
     * Create a {@link ShardListener}.
     *
     * @param master the {@link ITestInvocationListener} the results should be forwarded. To prevent
     *     collisions with other {@link ShardListener}s, this object will synchronize on
     *     <var>master</var> when forwarding results. And results will only be sent once the
     *     invocation shard completes.
     */
    public ShardListener(ITestInvocationListener master) {
        mMasterListener = master;
    }

    /**
     * {@inheritDoc}
     * @deprecated use {@link #invocationStarted(IInvocationContext)} instead.
     */
    @Override
    @Deprecated
    public void invocationStarted(IInvocationContext context) {
        super.invocationStarted(context);
        synchronized (mMasterListener) {
            mMasterListener.invocationStarted(context);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invocationFailed(Throwable cause) {
        super.invocationFailed(cause);
        synchronized (mMasterListener) {
            mMasterListener.invocationFailed(cause);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testLog(String dataName, LogDataType dataType, InputStreamSource dataStream) {
        // forward testLog results immediately, since they are not order dependent and there are
        // not stored by CollectingTestListener
        synchronized (mMasterListener) {
            mMasterListener.testLog(dataName, dataType, dataStream);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testRunEnded(long elapsedTime, Map<String, String> runMetrics) {
        super.testRunEnded(elapsedTime, runMetrics);
        CLog.logAndDisplay(LogLevel.INFO, "Sharded test completed: %s",
                getCurrentRunResults().getName());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testRunFailed(String failureMessage) {
        super.testRunFailed(failureMessage);
        CLog.logAndDisplay(LogLevel.ERROR, "FAILED: %s failed with message: %s",
                getCurrentRunResults().getName(), failureMessage);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invocationEnded(long elapsedTime) {
        super.invocationEnded(elapsedTime);
        synchronized (mMasterListener) {
            for (TestRunResult runResult : getRunResults()) {
                mMasterListener.testRunStarted(runResult.getName(), runResult.getNumTests());
                forwardTestResults(runResult.getTestResults());
                if (runResult.isRunFailure()) {
                    mMasterListener.testRunFailed(runResult.getRunFailureMessage());
                }
                mMasterListener.testRunEnded(runResult.getElapsedTime(), runResult.getRunMetrics());
            }
            mMasterListener.invocationEnded(elapsedTime);
        }
    }

    private void forwardTestResults(Map<TestIdentifier, TestResult> testResults) {
        for (Map.Entry<TestIdentifier, TestResult> testEntry : testResults.entrySet()) {
            mMasterListener.testStarted(testEntry.getKey(), testEntry.getValue().getStartTime());
            switch (testEntry.getValue().getStatus()) {
                case FAILURE:
                    mMasterListener.testFailed(testEntry.getKey(),
                            testEntry.getValue().getStackTrace());
                    break;
                case ASSUMPTION_FAILURE:
                    mMasterListener.testAssumptionFailure(testEntry.getKey(),
                            testEntry.getValue().getStackTrace());
                    break;
                case IGNORED:
                    mMasterListener.testIgnored(testEntry.getKey());
                    break;
                default:
                    break;
            }
            if (!testEntry.getValue().getStatus().equals(TestStatus.INCOMPLETE)) {
                mMasterListener.testEnded(
                        testEntry.getKey(),
                        testEntry.getValue().getEndTime(),
                        testEntry.getValue().getMetrics());
            }
        }
    }
}
