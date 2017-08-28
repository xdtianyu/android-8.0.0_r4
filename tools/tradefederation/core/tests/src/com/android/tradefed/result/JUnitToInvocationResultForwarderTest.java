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

import com.android.ddmlib.testrunner.TestIdentifier;

import junit.framework.AssertionFailedError;
import junit.framework.Test;
import junit.framework.TestCase;

import org.easymock.EasyMock;

import java.util.Collections;
import java.util.Map;

/**
 * Unit tests for {@link JUnitToInvocationResultForwarder}.
 */
public class JUnitToInvocationResultForwarderTest extends TestCase {

    private ITestInvocationListener mListener;
    private JUnitToInvocationResultForwarder mForwarder;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mListener = EasyMock.createMock(ITestInvocationListener.class);
        mForwarder = new JUnitToInvocationResultForwarder(mListener);
    }

    /**
     * Test method for
     * {@link JUnitToInvocationResultForwarder#addFailure(Test, AssertionFailedError)}.
     */
    public void testAddFailure() {
        final AssertionFailedError a = new AssertionFailedError();
        mListener.testFailed(
                EasyMock.eq(new TestIdentifier(JUnitToInvocationResultForwarderTest.class.getName(),
                        "testAddFailure")), (String)EasyMock.anyObject());
        EasyMock.replay(mListener);
        mForwarder.addFailure(this, a);
    }

    /**
     * Test method for {@link JUnitToInvocationResultForwarder#endTest(Test)}.
     */
    public void testEndTest() {
        Map<String, String> emptyMap = Collections.emptyMap();
        mListener.testEnded(EasyMock.eq(new TestIdentifier(
                JUnitToInvocationResultForwarderTest.class.getName(), "testEndTest")),
                EasyMock.eq(emptyMap));
        EasyMock.replay(mListener);
        mForwarder.endTest(this);
    }

    /**
     * Test method for {@link JUnitToInvocationResultForwarder#startTest(Test)}.
     */
    public void testStartTest() {
        mListener.testStarted(EasyMock.eq(new TestIdentifier(
                JUnitToInvocationResultForwarderTest.class.getName(), "testStartTest")));
        EasyMock.replay(mListener);
        mForwarder.startTest(this);
    }
}
