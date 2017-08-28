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

import static junit.framework.Assert.assertNull;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileNotFoundException;

/**
 * Test for checking that key/value backup and restore works correctly.
 * It interacts with the app that saves values in different shared preferences and files.
 * The app uses BackupAgentHelper to do key/value backup of those values.
 *
 * NB: The tests use "bmgr backupnow" for backup, which works on N+ devices.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class KeyValueBackupRestoreHostSideTest extends BaseBackupHostSideTest {

    /** The name of the package of the app under test */
    private static final String KEY_VALUE_RESTORE_APP_PACKAGE =
            "android.cts.backup.keyvaluerestoreapp";

    /** The name of the device side test class */
    private static final String KEY_VALUE_RESTORE_DEVICE_TEST_NAME =
            KEY_VALUE_RESTORE_APP_PACKAGE + ".KeyValueBackupRestoreTest";

    /** The name of the apk of the app under test */
    private static final String KEY_VALUE_RESTORE_APP_APK = "CtsKeyValueBackupRestoreApp.apk";

    @Before
    public void setUp() throws Exception {
        super.setUp();
        installPackage(KEY_VALUE_RESTORE_APP_APK);
        clearPackageData(KEY_VALUE_RESTORE_APP_PACKAGE);
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();

        // Clear backup data and uninstall the package (in that order!)
        clearBackupDataInLocalTransport(KEY_VALUE_RESTORE_APP_PACKAGE);
        assertNull(uninstallPackage(KEY_VALUE_RESTORE_APP_PACKAGE));
    }

    /**
     * Test that verifies key/value backup and restore.
     *
     * The flow of the test:
     * 1. Check that app has no saved data
     * 2. App saves the predefined values to shared preferences and files.
     * 3. Backup the app's data
     * 4. Uninstall the app
     * 5. Install the app back
     * 6. Check that all the shared preferences and files were restored.
     */
    @Test
    public void testKeyValueBackupAndRestore() throws Exception {
        checkDeviceTest("checkSharedPrefIsEmpty");

        checkDeviceTest("saveSharedPreferencesAndNotifyBackupManager");

        backupNowAndAssertSuccess(KEY_VALUE_RESTORE_APP_PACKAGE);

        assertNull(uninstallPackage(KEY_VALUE_RESTORE_APP_PACKAGE));

        installPackage(KEY_VALUE_RESTORE_APP_APK);

        // Shared preference should be restored
        checkDeviceTest("checkSharedPreferencesAreRestored");
    }

    private void checkDeviceTest(String methodName)
            throws DeviceNotAvailableException {
        super.checkDeviceTest(KEY_VALUE_RESTORE_APP_PACKAGE, KEY_VALUE_RESTORE_DEVICE_TEST_NAME,
                methodName);
    }
}
