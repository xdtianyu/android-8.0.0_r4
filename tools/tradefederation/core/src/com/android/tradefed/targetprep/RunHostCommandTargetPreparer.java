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

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;

import com.google.common.annotations.VisibleForTesting;

import java.util.LinkedList;
import java.util.List;

/** Target preparer to run arbitrary host commands before and after running the test. */
@OptionClass(alias = "run-host-command")
public class RunHostCommandTargetPreparer implements ITargetCleaner {

    @Option(
        name = "host-setup-command",
        description = "Command to be run before the test. Can be repeated."
    )
    private List<String> mSetUpCommands = new LinkedList<>();

    @Option(
        name = "host-teardown-command",
        description = "Command to be run after the test. Can be repeated."
    )
    private List<String> mTearDownCommands = new LinkedList<>();

    @Option(
        name = "host-cmd-timeout",
        isTimeVal = true,
        description = "Timeout for each command specified."
    )
    private long mTimeout = 60000L;

    /** {@inheritDoc} */
    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo)
            throws TargetSetupError, BuildError, DeviceNotAvailableException {
        runCommandList(mSetUpCommands);
    }

    /** {@inheritDoc} */
    @Override
    public void tearDown(ITestDevice device, IBuildInfo buildInfo, Throwable e)
            throws DeviceNotAvailableException {
        runCommandList(mTearDownCommands);
    }

    /**
     * Sequentially runs command from specified list.
     *
     * @param commands list of commands to run.
     */
    private void runCommandList(final List<String> commands) {
        for (final String command : commands) {
            final CommandResult result = getRunUtil().runTimedCmd(mTimeout, command.split("\\s+"));
            switch (result.getStatus()) {
                case SUCCESS:
                    CLog.i(
                            "Command %s finished successfully, stdout = [%s].",
                            command, result.getStdout());
                    break;
                case FAILED:
                    CLog.e(
                            "Command %s failed, stdout = [%s], stderr = [%s].",
                            command, result.getStdout(), result.getStderr());
                    break;
                case TIMED_OUT:
                    CLog.e("Command %s timed out.", command);
                    break;
                case EXCEPTION:
                    CLog.e("Exception occurred when running command %s.", command);
                    break;
            }
        }
    }

    /**
     * Gets instance of {@link IRunUtil}.
     *
     * @return instance of {@link IRunUtil}.
     */
    @VisibleForTesting
    IRunUtil getRunUtil() {
        return RunUtil.getDefault();
    }
}
