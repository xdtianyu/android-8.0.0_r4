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
package com.android.tradefed.testtype;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.result.JUnitToInvocationResultForwarder;

import junit.framework.AssertionFailedError;
import junit.framework.Protectable;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestListener;
import junit.framework.TestResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An specialization of {@link junit.framework.TestResult} that will abort when a
 * {@link DeviceNotAvailableException} occurs
 */
public class DeviceTestResult extends TestResult {

    @SuppressWarnings("serial")
    public class RuntimeDeviceNotAvailableException extends RuntimeException {
        private DeviceNotAvailableException mException;

        RuntimeDeviceNotAvailableException(DeviceNotAvailableException e) {
            super(e.getMessage());
            mException = e;
        }

        DeviceNotAvailableException getDeviceException() {
            return mException;
        }
    }

    /**
     * Runs a TestCase.
     *
     * @throws RuntimeDeviceNotAvailableException if a DeviceNotAvailableException occurs
     */
    @Override
    public void runProtected(final Test test, Protectable p) {
        // this is a copy of the superclass runProtected code, with the extra clause
        // for DeviceNotAvailableException
        try {
            p.protect();
        }
        catch (AssertionFailedError e) {
            addFailure(test, e);
        }
        catch (ThreadDeath e) { // don't catch ThreadDeath by accident
            throw e;
        }
        catch (DeviceNotAvailableException e) {
            addError(test, e);
            throw new RuntimeDeviceNotAvailableException(e);
        }
        catch (Throwable e) {
            addError(test, e);
        }
    }

    @Override
    protected void run(final TestCase test) {
        // this is a copy of the superclass run code, with the extra finally clause
        // to ensure endTest is called when RuntimeDeviceNotAvailableException occurs
        startTest(test);
        Protectable p = new Protectable() {
            @Override
            public void protect() throws Throwable {
                test.runBare();
            }
        };
        try {
            runProtected(test, p);
        } finally {
            endTest(test);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void endTest(Test test) {
        Map<String, String> metrics = new HashMap<>();
        if (test instanceof MetricTestCase) {
            metrics.putAll(((MetricTestCase) test).mMetrics);
            // reset the metric for next test.
            ((MetricTestCase) test).mMetrics = new HashMap<String, String>();
        }
        for (TestListener each : cloneListeners()) {
            // when possible pass the metrics collected from the tests to our reporters.
            if (!metrics.isEmpty() && each instanceof JUnitToInvocationResultForwarder) {
                ((JUnitToInvocationResultForwarder) each).endTest(test, metrics);
            } else {
                each.endTest(test);
            }
        }
    }

    /**
     * Returns a copy of the listeners. Copied from {@link TestResult} to enable overriding {@link
     * #endTest(Test)} in a similar way. This allows to override {@link #endTest(Test)} and report
     * our metrics.
     */
    private synchronized List<TestListener> cloneListeners() {
        List<TestListener> result = new ArrayList<TestListener>();
        result.addAll(fListeners);
        return result;
    }
}
