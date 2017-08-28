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

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.ddmlib.testrunner.TestResult.TestStatus;
import com.android.tradefed.result.ITestInvocationListener;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Collections;

/** Unit tests for {@link ModuleListener} * */
@RunWith(JUnit4.class)
public class ModuleListenerTest {

    private ModuleListener mListener;
    private ITestInvocationListener mStubListener;

    @Before
    public void setUp() {
        // We pipe all the events to nowhere
        mStubListener = new ITestInvocationListener() {};
        mListener = new ModuleListener(mStubListener);
    }

    /** Test that a regular execution yield the proper number of tests. */
    @Test
    public void testRegularExecution() {
        final int numTests = 5;
        mListener.testRunStarted("run1", numTests);
        for (int i = 0; i < numTests; i++) {
            TestIdentifier tid = new TestIdentifier("class", "test" + i);
            mListener.testStarted(tid);
            mListener.testEnded(tid, Collections.emptyMap());
        }
        mListener.testRunEnded(0, Collections.emptyMap());
        assertEquals(numTests, mListener.getNumTotalTests());
        assertEquals(numTests, mListener.getNumTestsInState(TestStatus.PASSED));
    }

    /** All the tests did not execute, so the amount of TestIdentifier seen is lower. */
    @Test
    public void testRun_missingTests() {
        final int numTests = 5;
        mListener.testRunStarted("run1", numTests);
        TestIdentifier tid = new TestIdentifier("class", "test" + numTests);
        // Only one test execute
        mListener.testStarted(tid);
        mListener.testEnded(tid, Collections.emptyMap());
        mListener.testRunEnded(0, Collections.emptyMap());

        assertEquals(numTests, mListener.getNumTotalTests());
        assertEquals(1, mListener.getNumTestsInState(TestStatus.PASSED));
    }

    /**
     * Some tests internally restart testRunStart to retry not_executed tests. We should not
     * aggregate the number of expected tests.
     */
    @Test
    public void testInternalRerun() {
        final int numTests = 5;
        mListener.testRunStarted("run1", numTests);
        TestIdentifier tid = new TestIdentifier("class", "test" + numTests);
        // Only one test execute the first time
        mListener.testStarted(tid);
        mListener.testEnded(tid, Collections.emptyMap());
        mListener.testRunEnded(0, Collections.emptyMap());

        // Runner restart to execute all the remaining
        mListener.testRunStarted("run1", numTests - 1);
        for (int i = 0; i < numTests - 1; i++) {
            TestIdentifier tid2 = new TestIdentifier("class", "test" + i);
            mListener.testStarted(tid2);
            mListener.testEnded(tid2, Collections.emptyMap());
        }
        mListener.testRunEnded(0, Collections.emptyMap());

        assertEquals(numTests, mListener.getNumTotalTests());
        assertEquals(numTests, mListener.getNumTestsInState(TestStatus.PASSED));
    }

    /** Some test runner calls testRunStart several times. We need to count all their tests. */
    @Test
    public void testMultiTestRun() {
        final int numTests = 5;
        mListener.testRunStarted("run1", numTests);
        for (int i = 0; i < numTests; i++) {
            TestIdentifier tid = new TestIdentifier("class", "test" + i);
            mListener.testStarted(tid);
            mListener.testEnded(tid, Collections.emptyMap());
        }
        mListener.testRunEnded(0, Collections.emptyMap());

        mListener.testRunStarted("run2", numTests);
        for (int i = 0; i < numTests; i++) {
            TestIdentifier tid = new TestIdentifier("class2", "test" + i);
            mListener.testStarted(tid);
            mListener.testEnded(tid, Collections.emptyMap());
        }
        mListener.testRunEnded(0, Collections.emptyMap());
        assertEquals(numTests * 2, mListener.getNumTotalTests());
        assertEquals(numTests * 2, mListener.getNumTestsInState(TestStatus.PASSED));
    }
}
