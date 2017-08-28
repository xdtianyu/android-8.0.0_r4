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
package com.android.tradefed.testtype;

import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.util.FileUtil;

import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;

/**
 * Unit test for {@link NoisyDryRunTest}.
 */
@RunWith(JUnit4.class)
public class NoisyDryRunTestTest {

    private File mFile;
    private ITestInvocationListener mMockListener;

    @Before
    public void setUp() throws Exception {
        mFile = FileUtil.createTempFile("NoisyDryRunTestTest", "tmpFile");
        mMockListener = EasyMock.createMock(ITestInvocationListener.class);
    }

    @After
    public void tearDown() {
        FileUtil.deleteFile(mFile);
    }

    @Test
    public void testRun() throws Exception {
        FileUtil.writeToFile("tf/fake\n"
                + "tf/fake", mFile);
        mMockListener.testRunStarted("com.android.tradefed.testtype.NoisyDryRunTest_parseFile", 1);
        mMockListener.testStarted(EasyMock.anyObject());
        mMockListener.testEnded(EasyMock.anyObject(), EasyMock.anyObject());
        mMockListener.testRunEnded(EasyMock.eq(0l), EasyMock.anyObject());

        mMockListener.testRunStarted("com.android.tradefed.testtype.NoisyDryRunTest_parseCommands",
                2);
        mMockListener.testStarted(EasyMock.anyObject());
        mMockListener.testEnded(EasyMock.anyObject(), EasyMock.anyObject());
        mMockListener.testStarted(EasyMock.anyObject());
        mMockListener.testEnded(EasyMock.anyObject(), EasyMock.anyObject());
        mMockListener.testRunEnded(EasyMock.eq(0l), EasyMock.anyObject());
        replayMocks();

        NoisyDryRunTest noisyDryRunTest = new NoisyDryRunTest();
        noisyDryRunTest.setCmdFile(mFile.getAbsolutePath());
        noisyDryRunTest.run(mMockListener);
        verifyMocks();
    }

    @Test
    public void testRun_invalidCmdFile() throws Exception {
        FileUtil.deleteFile(mFile);
        mMockListener.testRunStarted("com.android.tradefed.testtype.NoisyDryRunTest_parseFile", 1);
        mMockListener.testStarted(EasyMock.anyObject());
        mMockListener.testEnded(EasyMock.anyObject(), EasyMock.anyObject());
        mMockListener.testFailed(EasyMock.anyObject(), EasyMock.anyObject());
        mMockListener.testRunEnded(EasyMock.eq(0l), EasyMock.anyObject());
        replayMocks();

        NoisyDryRunTest noisyDryRunTest = new NoisyDryRunTest();
        noisyDryRunTest.setCmdFile(mFile.getAbsolutePath());
        noisyDryRunTest.run(mMockListener);
        verifyMocks();
    }

    @Test
    public void testRun_invalidCmdLine() throws Exception {
        FileUtil.writeToFile("tf/fake\n"
                + "invalid --option value2", mFile);
        mMockListener.testRunStarted("com.android.tradefed.testtype.NoisyDryRunTest_parseFile", 1);
        mMockListener.testStarted(EasyMock.anyObject());
        mMockListener.testEnded(EasyMock.anyObject(), EasyMock.anyObject());
        mMockListener.testRunEnded(EasyMock.eq(0l), EasyMock.anyObject());

        mMockListener.testRunStarted("com.android.tradefed.testtype.NoisyDryRunTest_parseCommands",
                2);
        mMockListener.testStarted(EasyMock.anyObject());
        mMockListener.testEnded(EasyMock.anyObject(), EasyMock.anyObject());
        mMockListener.testStarted(EasyMock.anyObject());
        mMockListener.testFailed(EasyMock.anyObject(), EasyMock.anyObject());
        mMockListener.testEnded(EasyMock.anyObject(), EasyMock.anyObject());
        mMockListener.testRunEnded(EasyMock.eq(0l), EasyMock.anyObject());
        replayMocks();

        NoisyDryRunTest noisyDryRunTest = new NoisyDryRunTest();
        noisyDryRunTest.setCmdFile(mFile.getAbsolutePath());
        noisyDryRunTest.run(mMockListener);
        verifyMocks();
    }

    private void replayMocks() {
        EasyMock.replay(mMockListener);
    }

    private void verifyMocks() {
        EasyMock.verify(mMockListener);
    }
}
