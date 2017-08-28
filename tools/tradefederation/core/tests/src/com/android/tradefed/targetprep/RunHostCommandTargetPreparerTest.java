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

package com.android.tradefed.targetprep;

import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.IRunUtil;

import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;

/** Unit test for {@link RunHostCommandTargetPreparer}. */
public final class RunHostCommandTargetPreparerTest {

    private RunHostCommandTargetPreparer mPreparer;
    private IRunUtil mRunUtil;

    @Before
    public void setUp() {
        mRunUtil = EasyMock.createMock(IRunUtil.class);
        mPreparer =
                new RunHostCommandTargetPreparer() {
                    @Override
                    protected IRunUtil getRunUtil() {
                        return mRunUtil;
                    }
                };
    }

    @Test
    public void testSetUp() throws Exception {
        final String command = "command    \t\t\t  \t  argument";
        final String[] commandArray = new String[] {"command", "argument"};

        final OptionSetter optionSetter = new OptionSetter(mPreparer);
        optionSetter.setOptionValue("host-setup-command", command);
        optionSetter.setOptionValue("host-cmd-timeout", "10");

        CommandResult result = new CommandResult(CommandStatus.SUCCESS);
        result.setStdout("");
        result.setStderr("");

        EasyMock.expect(mRunUtil.runTimedCmd(10L, commandArray)).andReturn(result);

        EasyMock.replay(mRunUtil);
        mPreparer.setUp(null, null);
        EasyMock.verify(mRunUtil);
    }

    @Test
    public void testTearDown() throws Exception {
        final String command = "command    \t\t\t  \t  argument";
        final String[] commandArray = new String[] {"command", "argument"};

        final OptionSetter optionSetter = new OptionSetter(mPreparer);
        optionSetter.setOptionValue("host-teardown-command", command);
        optionSetter.setOptionValue("host-cmd-timeout", "10");

        CommandResult result = new CommandResult(CommandStatus.SUCCESS);
        result.setStdout("");
        result.setStderr("");

        EasyMock.expect(mRunUtil.runTimedCmd(10L, commandArray)).andReturn(result);

        EasyMock.replay(mRunUtil);
        mPreparer.tearDown(null, null, null);
        EasyMock.verify(mRunUtil);
    }
}
