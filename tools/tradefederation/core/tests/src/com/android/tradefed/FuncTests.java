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
package com.android.tradefed;

import com.android.tradefed.build.FileDownloadCacheFuncTest;
import com.android.tradefed.command.CommandSchedulerFuncTest;
import com.android.tradefed.command.remote.RemoteManagerFuncTest;
import com.android.tradefed.device.TestDeviceFuncTest;
import com.android.tradefed.targetprep.AppSetupFuncTest;
import com.android.tradefed.targetprep.DeviceSetupFuncTest;
import com.android.tradefed.testtype.DeviceTestSuite;
import com.android.tradefed.testtype.InstrumentationTestFuncTest;
import com.android.tradefed.util.FileUtilFuncTest;
import com.android.tradefed.util.RunUtilFuncTest;
import com.android.tradefed.util.net.HttpHelperFuncTest;

import junit.framework.Test;

/**
 * A test suite for all Trade Federation functional tests.
 * <p/>
 * This suite requires a device.
 */
public class FuncTests extends DeviceTestSuite {

    public FuncTests() {
        super();
        // build
        this.addTestSuite(FileDownloadCacheFuncTest.class);
        // command
        this.addTestSuite(CommandSchedulerFuncTest.class);
        // command.remote
        this.addTestSuite(RemoteManagerFuncTest.class);
        // device
        this.addTestSuite(TestDeviceFuncTest.class);
        // targetprep
        this.addTestSuite(AppSetupFuncTest.class);
        this.addTestSuite(DeviceSetupFuncTest.class);
        // testtype
        this.addTestSuite(InstrumentationTestFuncTest.class);
        // util
        this.addTestSuite(FileUtilFuncTest.class);
        // TODO: temporarily remove from suite until we figure out how to install gtest data
        //this.addTestSuite(GTestFuncTest.class);
        this.addTestSuite(HttpHelperFuncTest.class);
        this.addTestSuite(RunUtilFuncTest.class);
    }

    public static Test suite() {
        return new FuncTests();
    }
}
