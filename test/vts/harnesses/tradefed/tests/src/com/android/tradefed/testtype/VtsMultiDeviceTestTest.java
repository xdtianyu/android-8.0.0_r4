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
package com.android.tradefed.testtype;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IFolderBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.StreamUtil;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;
import junit.framework.TestCase;
import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.json.JSONObject;

/**
 * Unit tests for {@link VtsMultiDeviceTest}.
 * This class requires testcase config files.
 * The working directory is assumed to be
 * test/
 * which contains the same config as the build output
 * out/host/linux-x86/vts/android-vts/testcases/
 */
public class VtsMultiDeviceTestTest extends TestCase {

    private ITestInvocationListener mMockInvocationListener = null;
    private VtsMultiDeviceTest mTest = null;
    private static final String TEST_CASE_PATH =
        "vts/testcases/host/sample/SampleLightTest";
    private static final String TEST_CONFIG_PATH =
        "vts/testcases/host/camera/conventional/3_4/SampleCameraV3Test.config";

    /**
     * Helper to initialize the various EasyMocks we'll need.
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mMockInvocationListener = EasyMock.createMock(ITestInvocationListener.class);
        mTest = new VtsMultiDeviceTest();
        mTest.setBuild(createMockBuildInfo());
        mTest.setTestCasePath(TEST_CASE_PATH);
        mTest.setTestConfigPath(TEST_CONFIG_PATH);
    }

    /**
     * Create a mock IBuildInfo with necessary getter methods.
     */
    private static IBuildInfo createMockBuildInfo() {
        Map<String, String> buildAttributes = new HashMap<String, String>();
        buildAttributes.put("ROOT_DIR", "DIR_NOT_EXIST");
        buildAttributes.put("ROOT_DIR2", "DIR_NOT_EXIST");
        buildAttributes.put("SUITE_NAME", "JUNIT_TEST_SUITE");
        IFolderBuildInfo buildInfo = EasyMock.createMock(IFolderBuildInfo.class);
        EasyMock.expect(buildInfo.getBuildId()).
                andReturn("BUILD_ID");
        EasyMock.expect(buildInfo.getBuildTargetName()).
                andReturn("BUILD_TARGET_NAME");
        EasyMock.expect(buildInfo.getTestTag()).
                andReturn("TEST_TAG").atLeastOnce();
        EasyMock.expect(buildInfo.getDeviceSerial()).
                andReturn("1234567890ABCXYZ").atLeastOnce();
        EasyMock.expect(buildInfo.getRootDir()).
                andReturn(new File("DIR_NOT_EXIST"));
        EasyMock.expect(buildInfo.getBuildAttributes()).
                andReturn(buildAttributes).atLeastOnce();
        EasyMock.expect(buildInfo.getFile(EasyMock.eq("vts"))).
                andReturn(null);
        EasyMock.expect(buildInfo.getFile(EasyMock.eq("PYTHONPATH"))).
                andReturn(new File("DIR_NOT_EXIST")).atLeastOnce();
        EasyMock.expect(buildInfo.getFile(EasyMock.eq("VIRTUALENVPATH"))).
                andReturn(new File("DIR_NOT_EXIST"));
        EasyMock.replay(buildInfo);
        return buildInfo;
    }

    /**
     * Create a mock IRunUtil which
     * 1. finds Python binary file and returns a mock path
     * 2. executes the found Python binary,
     *    creates empty log file and returns expectedStatus
     */
    private static IRunUtil createMockRunUtil(
            String findFileCommand, String pythonBin,
            CommandStatus expectedStatus) {
        IRunUtil runUtil = EasyMock.createMock(IRunUtil.class);
        CommandResult findResult = new CommandResult();
        findResult.setStatus(CommandStatus.SUCCESS);
        findResult.setStdout("mock/" + pythonBin);
        EasyMock.expect(runUtil.runTimedCmd(EasyMock.anyLong(),
                EasyMock.eq(findFileCommand), EasyMock.eq(pythonBin))).
            andReturn(findResult);

        IAnswer<CommandResult> answer = new IAnswer<CommandResult>() {
            @Override
            public CommandResult answer() throws Throwable {
                // find log path
                String logPath = null;
                try (FileInputStream fi = new FileInputStream(
                        (String) EasyMock.getCurrentArguments()[4])) {
                    JSONObject configJson = new JSONObject(
                            StreamUtil.getStringFromStream(fi));
                    logPath = (String) configJson.get(VtsMultiDeviceTest.LOG_PATH);
                }
                // create a test result on log path
                try (FileWriter fw = new FileWriter(
                        logPath + File.separator + "test_run_summary.json")) {
                    JSONObject outJson = new JSONObject();
                    fw.write(outJson.toString());
                }
                CommandResult commandResult = new CommandResult();
                commandResult.setStatus(expectedStatus);
                return commandResult;
            }
        };
        EasyMock.expect(runUtil.runTimedCmd(EasyMock.anyLong(),
                EasyMock.eq("mock/" + pythonBin), EasyMock.eq("-m"),
                EasyMock.eq(TEST_CASE_PATH.replace("/", ".")),
                EasyMock.endsWith(".json"))).
            andAnswer(answer).times(0, 1);
        EasyMock.replay(runUtil);
        return runUtil;
    }

    /**
     * Create a mock ITestDevice with necessary getter methods.
     */
    private static ITestDevice createMockDevice() {
        // TestDevice
        ITestDevice device = EasyMock.createMock(ITestDevice.class);
        try {
            EasyMock.expect(device.getSerialNumber()).
                andReturn("1234567890ABCXYZ").atLeastOnce();
            EasyMock.expect(device.getBuildAlias()).
                andReturn("BUILD_ALIAS");
            EasyMock.expect(device.getBuildFlavor()).
                andReturn("BUILD_FLAVOR");
            EasyMock.expect(device.getBuildId()).
                andReturn("BUILD_ID");
            EasyMock.expect(device.getProperty("ro.vts.coverage")).
                andReturn(null);
            EasyMock.expect(device.getProductType()).
                andReturn("PRODUCT_TYPE");
            EasyMock.expect(device.getProductVariant()).
                andReturn("PRODUCT_VARIANT");
        } catch (DeviceNotAvailableException e) {
            fail();
        }
        EasyMock.replay(device);
        return device;
    }

    /**
     * Test the run method with a normal input.
     */
    public void testRunNormalInput() {
        mTest.setDevice(createMockDevice());
        mTest.setRunUtil(createMockRunUtil("which", "python", CommandStatus.SUCCESS));
        try {
            mTest.run(mMockInvocationListener);
        } catch (IllegalArgumentException e) {
            // not expected
            fail();
            e.printStackTrace();
        } catch (DeviceNotAvailableException e) {
            // not expected
            fail();
            e.printStackTrace();
        }
    }

    /**
     * Test the run method with a normal input and Windows environment variable.
     */
    public void testRunNormalInputOnWindows()
            throws IllegalArgumentException, DeviceNotAvailableException {
        String originalName = System.getProperty(VtsMultiDeviceTest.OS_NAME);
        System.setProperty(VtsMultiDeviceTest.OS_NAME, VtsMultiDeviceTest.WINDOWS);
        mTest.setDevice(createMockDevice());
        mTest.setRunUtil(createMockRunUtil("where", "python.exe", CommandStatus.SUCCESS));
        try {
            mTest.run(mMockInvocationListener);
        } finally {
            System.setProperty(VtsMultiDeviceTest.OS_NAME, originalName);
        }
    }

    /**
     * Test the run method when the device is set null.
     */
    public void testRunDeviceNotAvailable() {
        mTest.setDevice(null);
        try {
            mTest.run(mMockInvocationListener);
            fail();
       } catch (IllegalArgumentException e) {
            // not expected
            fail();
       } catch (DeviceNotAvailableException e) {
            // expected
       }
    }

    /**
     * Test the run method with abnormal input data.
     */
    public void testRunNormalInputCommandFailed() {
        mTest.setDevice(createMockDevice());
        mTest.setRunUtil(createMockRunUtil("which", "python", CommandStatus.FAILED));
        try {
            mTest.run(mMockInvocationListener);
            fail();
        } catch (RuntimeException e) {
            if (!"Failed to run VTS test".equals(e.getMessage())) {
                fail();
            }
            // expected
        } catch (DeviceNotAvailableException e) {
            // not expected
            fail();
            e.printStackTrace();
        }
    }
}
