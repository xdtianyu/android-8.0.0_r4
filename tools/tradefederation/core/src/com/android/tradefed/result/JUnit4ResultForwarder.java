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
package com.android.tradefed.result;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner.MetricAnnotation;

import org.junit.runner.Description;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Result forwarder from JUnit4 Runner.
 */
public class JUnit4ResultForwarder extends RunListener {

    ITestInvocationListener mListener;

    public JUnit4ResultForwarder(ITestInvocationListener listener) {
        mListener = listener;
    }

    @Override
    public void testFailure(Failure failure) throws Exception {
        Description description = failure.getDescription();
        TestIdentifier testid = new TestIdentifier(description.getClassName(),
                description.getMethodName());
        mListener.testFailed(testid, failure.getTrace());
    }

    @Override
    public void testAssumptionFailure(Failure failure) {
        Description description = failure.getDescription();
        TestIdentifier testid = new TestIdentifier(description.getClassName(),
                description.getMethodName());
        mListener.testAssumptionFailure(testid, failure.getTrace());
    }

    @Override
    public void testStarted(Description description) {
        TestIdentifier testid = new TestIdentifier(description.getClassName(),
                description.getMethodName());
        mListener.testStarted(testid);
    }

    @Override
    public void testFinished(Description description) {
        TestIdentifier testid = new TestIdentifier(description.getClassName(),
                description.getMethodName());
        // Explore the Description to see if we find any Annotation metrics carrier
        Map<String, String> metrics = new HashMap<>();
        for (Description child : description.getChildren()) {
            for (Annotation a : child.getAnnotations()) {
                if (a instanceof MetricAnnotation) {
                    metrics.putAll(((MetricAnnotation) a).mMetrics);
                }
            }
        }
        //description.
        mListener.testEnded(testid, metrics);
    }

    @Override
    public void testIgnored(Description description) throws Exception {
        TestIdentifier testid = new TestIdentifier(description.getClassName(),
                description.getMethodName());
        // We complete the event life cycle since JUnit4 fireIgnored is not within fireTestStarted
        // and fireTestEnded.
        mListener.testStarted(testid);
        mListener.testIgnored(testid);
        mListener.testEnded(testid, Collections.emptyMap());
    }
}
