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

package com.android.tradefed.targetprep;

import static org.easymock.EasyMock.anyLong;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;

import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.IRunUtil;

import junit.framework.TestCase;

import java.io.File;

public class VtsPythonVirtualenvPreparerTest extends TestCase {
    private VtsPythonVirtualenvPreparer mPreparer;
    private IRunUtil mMockRunUtil;

    @Override
    public void setUp() throws Exception {
        mMockRunUtil = createNiceMock(IRunUtil.class);
        mPreparer = new VtsPythonVirtualenvPreparer();
        mPreparer.mRunUtil = mMockRunUtil;
    }

    public void testInstallDeps_reqFile_success() throws Exception {
        mPreparer.setRequirementsFile(new File("foobar"));
        expect(mMockRunUtil.runTimedCmd(anyLong(),
                (String)anyObject(), (String)anyObject(), (String)anyObject(), (String)anyObject()))
            .andReturn(new CommandResult(CommandStatus.SUCCESS));
        replay(mMockRunUtil);
        IBuildInfo buildInfo = new BuildInfo();
        mPreparer.installDeps(buildInfo);
        assertTrue(buildInfo.getFile("PYTHONPATH") != null);
    }

    public void testInstallDeps_depModule_success() throws Exception {
        mPreparer.addDepModule("blahblah");
        expect(mMockRunUtil.runTimedCmd(anyLong(),
                (String)anyObject(), (String)anyObject(), (String)anyObject())).andReturn(
                new CommandResult(CommandStatus.SUCCESS));
        replay(mMockRunUtil);
        IBuildInfo buildInfo = new BuildInfo();
        mPreparer.installDeps(buildInfo);
        assertTrue(buildInfo.getFile("PYTHONPATH") != null);
    }

    public void testInstallDeps_reqFile_failure() throws Exception {
        mPreparer.setRequirementsFile(new File("foobar"));
        expect(mMockRunUtil.runTimedCmd(anyLong(),
                (String)anyObject(), (String)anyObject(), (String)anyObject(), (String)anyObject()))
            .andReturn(new CommandResult(CommandStatus.TIMED_OUT));
        replay(mMockRunUtil);
        IBuildInfo buildInfo = new BuildInfo();
        try {
            mPreparer.installDeps(buildInfo);
            fail("installDeps succeeded despite a failed command");
        } catch (TargetSetupError e) {
            assertTrue(buildInfo.getFile("PYTHONPATH") == null);
        }
    }

    public void testInstallDeps_depModule_failure() throws Exception {
        mPreparer.addDepModule("blahblah");
        expect(mMockRunUtil.runTimedCmd(anyLong(),
                (String)anyObject(), (String)anyObject(), (String)anyObject())).andReturn(
                new CommandResult(CommandStatus.TIMED_OUT));
        replay(mMockRunUtil);
        IBuildInfo buildInfo = new BuildInfo();
        try {
            mPreparer.installDeps(buildInfo);
            fail("installDeps succeeded despite a failed command");
        } catch (TargetSetupError e) {
            assertTrue(buildInfo.getFile("PYTHONPATH") == null);
        }
    }

    public void testInstallDeps_noDeps() throws Exception {
        BuildInfo buildInfo = new BuildInfo();
        mPreparer.installDeps(buildInfo);
        assertTrue(buildInfo.getFile("PYTHONPATH") == null);
    }
}