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
 * limitations under the License
 */

package android.cts.backup;

import static junit.framework.Assert.assertTrue;

import static org.junit.Assume.assumeTrue;

import com.android.compatibility.common.tradefed.testtype.CompatibilityHostTestBase;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;

import java.util.Scanner;

/**
 * Base class for CTS backup/restore hostside tests
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public abstract class BaseBackupHostSideTest extends CompatibilityHostTestBase {
    /** Value of PackageManager.FEATURE_BACKUP */
    private static final String FEATURE_BACKUP = "android.software.backup";

    private static final String LOCAL_TRANSPORT =
            "android/com.android.internal.backup.LocalTransport";

    private boolean mIsBackupSupported;

    @Before
    public void setUp() throws DeviceNotAvailableException, Exception {
        mIsBackupSupported = mDevice.hasFeature("feature:" + FEATURE_BACKUP);
        assumeTrue(mIsBackupSupported);
    }

    @After
    public void tearDown() throws Exception {
        // Not deleting to avoid breaking the tests calling super.tearDown()
    }

    /**
     * Execute shell command "bmgr backupnow <packageName>" and return output from this command.
     */
    protected String backupNow(String packageName) throws DeviceNotAvailableException {
        return mDevice.executeShellCommand("bmgr backupnow " + packageName);
    }

    /**
     * Execute shell command "bmgr backupnow <packageName>" and assert success.
     */
    protected void backupNowAndAssertSuccess(String packageName)
            throws DeviceNotAvailableException {
        String backupnowOutput = backupNow(packageName);

        assertBackupIsSuccessful(packageName, backupnowOutput);
    }

    /**
     * Execute shell command "bmgr restore <packageName>" and assert success.
     */
    protected void restoreAndAssertSuccess(String packageName) throws DeviceNotAvailableException {
        String restoreOutput = restore(packageName);
        assertRestoreIsSuccessful(restoreOutput);
    }

    /**
     * Execute shell command "bmgr restore <packageName>" and return output from this command.
     */
    protected String restore(String packageName) throws DeviceNotAvailableException {
        return mDevice.executeShellCommand("bmgr restore " + packageName);
    }

    /**
     * Run test <testName> in test <className> found in package <packageName> on the device, and
     * assert it is successful.
     */
    protected void checkDeviceTest(String packageName, String className, String testName)
            throws DeviceNotAvailableException {
        boolean result = runDeviceTests(packageName, className, testName);
        assertTrue("Device test failed: " + testName, result);
    }

    /**
     * Parsing the output of "bmgr backupnow" command and checking that the package under test
     * was backed up successfully.
     *
     * Expected format: "Package <packageName> with result: Success"
     */
    protected void assertBackupIsSuccessful(String packageName, String backupnowOutput) {
        // Assert backup was successful.
        Scanner in = new Scanner(backupnowOutput);
        boolean success = false;
        while (in.hasNextLine()) {
            String line = in.nextLine();

            if (line.contains(packageName)) {
                String result = line.split(":")[1].trim();
                if ("Success".equals(result)) {
                    success = true;
                }
            }
        }
        in.close();
        assertTrue(success);
    }

    /**
     * Parsing the output of "bmgr restore" command and checking that the package under test
     * was restored successfully.
     *
     * Expected format: "restoreFinished: 0"
     */
    protected void assertRestoreIsSuccessful(String restoreOutput) {
        assertTrue("Restore not successful", restoreOutput.contains("restoreFinished: 0"));
    }

    protected void startActivityInPackageAndWait(String packageName, String className)
            throws DeviceNotAvailableException {
        mDevice.executeShellCommand(String.format(
                "am start -W -a android.intent.action.MAIN -n %s/%s.%s", packageName,
                packageName,
                className));
    }

    /**
     * Clears backup data stored in Local Transport for a package.
     * NB: 'bmgr wipe' does not produce any useful output if the package or transport not found,
     * so we cannot really check the success of the operation
      */
    protected void clearBackupDataInLocalTransport(String packageName)
            throws DeviceNotAvailableException {
        mDevice.executeShellCommand(String.format("bmgr wipe %s %s", LOCAL_TRANSPORT, packageName));
    }

    /**
     * Clears package data
     */
    protected void clearPackageData(String packageName) throws DeviceNotAvailableException {
        mDevice.executeShellCommand(String.format("pm clear %s", packageName));
    }
}
