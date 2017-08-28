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

import com.android.annotations.VisibleForTesting;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.command.CommandFileParser;
import com.android.tradefed.command.CommandFileParser.CommandLine;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.ConfigurationFactory;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.util.QuotationAwareTokenizer;
import com.android.tradefed.util.StreamUtil;
import com.android.tradefed.util.keystore.StubKeyStoreClient;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;

/**
 * Run noisy dry run on a command file.
 */
public class NoisyDryRunTest implements IRemoteTest {

    @Option(name = "cmdfile", description = "The cmdfile to run noisy dry run on.")
    private String mCmdfile = null;

    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        List<CommandLine> commands = testCommandFile(listener, mCmdfile);
        if (commands != null) {
            testCommandLines(listener, commands);
        }
    }

    private List<CommandLine> testCommandFile(ITestInvocationListener listener, String filename) {
        listener.testRunStarted(NoisyDryRunTest.class.getCanonicalName() + "_parseFile", 1);
        TestIdentifier parseFileTest = new TestIdentifier(NoisyDryRunTest.class.getCanonicalName(),
                "parseFile");
        listener.testStarted(parseFileTest);
        CommandFileParser parser = new CommandFileParser();
        try {
            return parser.parseFile(new File(filename));
        } catch (IOException | ConfigurationException e) {
            listener.testFailed(parseFileTest, StreamUtil.getStackTrace(e));
            return null;
        } finally {
            listener.testEnded(parseFileTest, new HashMap<String, String>());
            listener.testRunEnded(0, new HashMap<String, String>());
        }
    }

    private void testCommandLines(ITestInvocationListener listener, List<CommandLine> commands) {
        listener.testRunStarted(NoisyDryRunTest.class.getCanonicalName() + "_parseCommands",
                commands.size());
        for (int i = 0; i < commands.size(); ++i) {
            TestIdentifier parseCmdTest = new TestIdentifier(
                    NoisyDryRunTest.class.getCanonicalName(), "parseCommand" + i);
            listener.testStarted(parseCmdTest);

            String[] args = commands.get(i).asArray();
            String cmdLine = QuotationAwareTokenizer.combineTokens(args);
            try {
                IConfiguration config = ConfigurationFactory.getInstance()
                        .createConfigurationFromArgs(args, null, new StubKeyStoreClient());
                config.validateOptions();
            } catch (ConfigurationException e) {
                CLog.e("Failed to parse comand line %s.", cmdLine);
                CLog.e(e);
                listener.testFailed(parseCmdTest, StreamUtil.getStackTrace(e));
            } finally {
                listener.testEnded(parseCmdTest, new HashMap<String, String>());
            }
        }
        listener.testRunEnded(0, new HashMap<String, String>());
    }

    @VisibleForTesting
    void setCmdFile(String cmdfile) {
        mCmdfile = cmdfile;
    }
}
