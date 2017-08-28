/*
 * Copyright (C) 2015 The Android Open Source Project
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
package libcore.tzdata.update2;

import junit.framework.AssertionFailedError;
import junit.framework.TestCase;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import libcore.io.IoUtils;
import libcore.io.Streams;
import libcore.tzdata.shared2.DistroVersion;
import libcore.tzdata.shared2.FileUtils;
import libcore.tzdata.shared2.StagedDistroOperation;
import libcore.tzdata.shared2.TimeZoneDistro;
import libcore.tzdata.testing.ZoneInfoTestHelper;
import libcore.tzdata.update2.tools.TimeZoneDistroBuilder;

import static org.junit.Assert.assertArrayEquals;

/**
 * Tests for {@link TimeZoneDistroInstaller}.
 */
public class TimeZoneDistroInstallerTest extends TestCase {

    // OLDER_RULES_VERSION < SYSTEM_RULES_VERSION < NEW_RULES_VERSION < NEWER_RULES_VERSION
    private static final String OLDER_RULES_VERSION = "2030a";
    private static final String SYSTEM_RULES_VERSION = "2030b";
    private static final String NEW_RULES_VERSION = "2030c";
    private static final String NEWER_RULES_VERSION = "2030d";

    private TimeZoneDistroInstaller installer;
    private File tempDir;
    private File testInstallDir;
    private File testSystemTzDataDir;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        tempDir = createDirectory("tempDir");
        testInstallDir =  createDirectory("testInstall");
        testSystemTzDataDir =  createDirectory("testSystemTzData");

        // Create a file to represent the tzdata file in the /system partition of the device.
        File testSystemTzDataFile = new File(testSystemTzDataDir, "tzdata");
        byte[] systemTzDataBytes = createTzData(SYSTEM_RULES_VERSION);
        createFile(testSystemTzDataFile, systemTzDataBytes);

        installer = new TimeZoneDistroInstaller(
                "TimeZoneDistroInstallerTest", testSystemTzDataFile, testInstallDir);
    }

    private static File createDirectory(String prefix) throws Exception {
        File dir = File.createTempFile(prefix, "");
        assertTrue(dir.delete());
        assertTrue(dir.mkdir());
        return dir;
    }

    @Override
    public void tearDown() throws Exception {
        if (testSystemTzDataDir.exists()) {
            FileUtils.deleteRecursive(testInstallDir);
        }
        if (testInstallDir.exists()) {
            FileUtils.deleteRecursive(testInstallDir);
        }
        if (tempDir.exists()) {
            FileUtils.deleteRecursive(tempDir);
        }
        super.tearDown();
    }

    /** Tests the an update on a device will fail if the /system tzdata file cannot be found. */
    public void testStageInstallWithErrorCode_badSystemFile() throws Exception {
        File doesNotExist = new File(testSystemTzDataDir, "doesNotExist");
        TimeZoneDistroInstaller brokenSystemInstaller = new TimeZoneDistroInstaller(
                "TimeZoneDistroInstallerTest", doesNotExist, testInstallDir);
        TimeZoneDistro tzData = createValidTimeZoneDistro(NEW_RULES_VERSION, 1);

        try {
            brokenSystemInstaller.stageInstallWithErrorCode(tzData.getBytes());
            fail();
        } catch (IOException expected) {}

        assertNoDistroOperationStaged();
        assertNoInstalledDistro();
    }

    /** Tests the first successful update on a device */
    public void testStageInstallWithErrorCode_successfulFirstUpdate() throws Exception {
        TimeZoneDistro distro = createValidTimeZoneDistro(NEW_RULES_VERSION, 1);

        assertEquals(
                TimeZoneDistroInstaller.INSTALL_SUCCESS,
                installer.stageInstallWithErrorCode(distro.getBytes()));
        assertInstallDistroStaged(distro);
        assertNoInstalledDistro();
    }

    /**
     * Tests we can install an update the same version as is in /system.
     */
    public void testStageInstallWithErrorCode_successfulFirstUpdate_sameVersionAsSystem()
            throws Exception {
        TimeZoneDistro distro = createValidTimeZoneDistro(SYSTEM_RULES_VERSION, 1);
        assertEquals(
                TimeZoneDistroInstaller.INSTALL_SUCCESS,
                installer.stageInstallWithErrorCode(distro.getBytes()));
        assertInstallDistroStaged(distro);
        assertNoInstalledDistro();
    }

    /**
     * Tests we cannot install an update older than the version in /system.
     */
    public void testStageInstallWithErrorCode_unsuccessfulFirstUpdate_olderVersionThanSystem()
            throws Exception {
        TimeZoneDistro distro = createValidTimeZoneDistro(OLDER_RULES_VERSION, 1);
        assertEquals(
                TimeZoneDistroInstaller.INSTALL_FAIL_RULES_TOO_OLD,
                installer.stageInstallWithErrorCode(distro.getBytes()));
        assertNoDistroOperationStaged();
        assertNoInstalledDistro();
    }

    /**
     * Tests an update on a device when there is a prior update already staged.
     */
    public void testStageInstallWithErrorCode_successfulFollowOnUpdate_newerVersion()
            throws Exception {
        TimeZoneDistro distro1 = createValidTimeZoneDistro(NEW_RULES_VERSION, 1);
        assertEquals(
                TimeZoneDistroInstaller.INSTALL_SUCCESS,
                installer.stageInstallWithErrorCode(distro1.getBytes()));
        assertInstallDistroStaged(distro1);

        TimeZoneDistro distro2 = createValidTimeZoneDistro(NEW_RULES_VERSION, 2);
        assertEquals(
                TimeZoneDistroInstaller.INSTALL_SUCCESS,
                installer.stageInstallWithErrorCode(distro2.getBytes()));
        assertInstallDistroStaged(distro2);

        TimeZoneDistro distro3 = createValidTimeZoneDistro(NEWER_RULES_VERSION, 1);
        assertEquals(
                TimeZoneDistroInstaller.INSTALL_SUCCESS,
                installer.stageInstallWithErrorCode(distro3.getBytes()));
        assertInstallDistroStaged(distro3);
        assertNoInstalledDistro();
    }

    /**
     * Tests an update on a device when there is a prior update already applied, but the follow
     * on update is older than in /system.
     */
    public void testStageInstallWithErrorCode_unsuccessfulFollowOnUpdate_olderVersion()
            throws Exception {
        TimeZoneDistro distro1 = createValidTimeZoneDistro(NEW_RULES_VERSION, 2);
        assertEquals(
                TimeZoneDistroInstaller.INSTALL_SUCCESS,
                installer.stageInstallWithErrorCode(distro1.getBytes()));
        assertInstallDistroStaged(distro1);

        TimeZoneDistro distro2 = createValidTimeZoneDistro(OLDER_RULES_VERSION, 1);
        assertEquals(
                TimeZoneDistroInstaller.INSTALL_FAIL_RULES_TOO_OLD,
                installer.stageInstallWithErrorCode(distro2.getBytes()));
        assertInstallDistroStaged(distro1);
        assertNoInstalledDistro();
    }

    /** Tests that a distro with a missing tzdata file will not update the content. */
    public void testStageInstallWithErrorCode_missingTzDataFile() throws Exception {
        TimeZoneDistro stagedDistro = createValidTimeZoneDistro(NEW_RULES_VERSION, 1);
        assertEquals(
                TimeZoneDistroInstaller.INSTALL_SUCCESS,
                installer.stageInstallWithErrorCode(stagedDistro.getBytes()));
        assertInstallDistroStaged(stagedDistro);

        TimeZoneDistro incompleteDistro =
                createValidTimeZoneDistroBuilder(NEWER_RULES_VERSION, 1)
                        .clearTzDataForTests()
                        .buildUnvalidated();
        assertEquals(
                TimeZoneDistroInstaller.INSTALL_FAIL_BAD_DISTRO_STRUCTURE,
                installer.stageInstallWithErrorCode(incompleteDistro.getBytes()));
        assertInstallDistroStaged(stagedDistro);
        assertNoInstalledDistro();
    }

    /** Tests that a distro with a missing ICU file will not update the content. */
    public void testStageInstallWithErrorCode_missingIcuFile() throws Exception {
        TimeZoneDistro stagedDistro = createValidTimeZoneDistro(NEW_RULES_VERSION, 1);
        assertEquals(
                TimeZoneDistroInstaller.INSTALL_SUCCESS,
                installer.stageInstallWithErrorCode(stagedDistro.getBytes()));
        assertInstallDistroStaged(stagedDistro);

        TimeZoneDistro incompleteDistro =
                createValidTimeZoneDistroBuilder(NEWER_RULES_VERSION, 1)
                        .clearIcuDataForTests()
                        .buildUnvalidated();
        assertEquals(
                TimeZoneDistroInstaller.INSTALL_FAIL_BAD_DISTRO_STRUCTURE,
                installer.stageInstallWithErrorCode(incompleteDistro.getBytes()));
        assertInstallDistroStaged(stagedDistro);
        assertNoInstalledDistro();
    }

    /** Tests that a distro with a missing tzlookup file will not update the content. */
    public void testStageInstallWithErrorCode_missingTzLookupFile() throws Exception {
        TimeZoneDistro stagedDistro = createValidTimeZoneDistro(NEW_RULES_VERSION, 1);
        assertEquals(
                TimeZoneDistroInstaller.INSTALL_SUCCESS,
                installer.stageInstallWithErrorCode(stagedDistro.getBytes()));
        assertInstallDistroStaged(stagedDistro);

        TimeZoneDistro incompleteDistro =
                createValidTimeZoneDistroBuilder(NEWER_RULES_VERSION, 1)
                        .setTzLookupXml(null)
                        .buildUnvalidated();
        assertEquals(
                TimeZoneDistroInstaller.INSTALL_FAIL_BAD_DISTRO_STRUCTURE,
                installer.stageInstallWithErrorCode(incompleteDistro.getBytes()));
        assertInstallDistroStaged(stagedDistro);
        assertNoInstalledDistro();
    }

    /** Tests that a distro with a bad tzlookup file will not update the content. */
    public void testStageInstallWithErrorCode_badTzLookupFile() throws Exception {
        TimeZoneDistro stagedDistro = createValidTimeZoneDistro(NEW_RULES_VERSION, 1);
        assertEquals(
                TimeZoneDistroInstaller.INSTALL_SUCCESS,
                installer.stageInstallWithErrorCode(stagedDistro.getBytes()));
        assertInstallDistroStaged(stagedDistro);

        TimeZoneDistro incompleteDistro =
                createValidTimeZoneDistroBuilder(NEWER_RULES_VERSION, 1)
                        .setTzLookupXml("<foo />")
                        .buildUnvalidated();
        assertEquals(
                TimeZoneDistroInstaller.INSTALL_FAIL_VALIDATION_ERROR,
                installer.stageInstallWithErrorCode(incompleteDistro.getBytes()));
        assertInstallDistroStaged(stagedDistro);
        assertNoInstalledDistro();
    }

    /**
     * Tests that an update will be unpacked even if there is a partial update from a previous run.
     */
    public void testStageInstallWithErrorCode_withWorkingDir() throws Exception {
        File workingDir = installer.getWorkingDir();
        assertTrue(workingDir.mkdir());
        createFile(new File(workingDir, "myFile"), new byte[] { 'a' });

        TimeZoneDistro distro = createValidTimeZoneDistro(NEW_RULES_VERSION, 1);
        assertEquals(
                TimeZoneDistroInstaller.INSTALL_SUCCESS,
                installer.stageInstallWithErrorCode(distro.getBytes()));
        assertInstallDistroStaged(distro);
        assertNoInstalledDistro();
    }

    /**
     * Tests that a distro without a distro version file will be rejected.
     */
    public void testStageInstallWithErrorCode_withMissingDistroVersionFile() throws Exception {
        // Create a distro without a version file.
        TimeZoneDistro distro = createValidTimeZoneDistroBuilder(NEW_RULES_VERSION, 1)
                .clearVersionForTests()
                .buildUnvalidated();
        assertEquals(
                TimeZoneDistroInstaller.INSTALL_FAIL_BAD_DISTRO_STRUCTURE,
                installer.stageInstallWithErrorCode(distro.getBytes()));
        assertNoDistroOperationStaged();
        assertNoInstalledDistro();
    }

    /**
     * Tests that a distro with an newer distro version will be rejected.
     */
    public void testStageInstallWithErrorCode_withNewerDistroVersion() throws Exception {
        // Create a distro that will appear to be newer than the one currently supported.
        TimeZoneDistro distro = createValidTimeZoneDistroBuilder(NEW_RULES_VERSION, 1)
                .replaceFormatVersionForTests(2, 1)
                .buildUnvalidated();
        assertEquals(
                TimeZoneDistroInstaller.INSTALL_FAIL_BAD_DISTRO_FORMAT_VERSION,
                installer.stageInstallWithErrorCode(distro.getBytes()));
        assertNoDistroOperationStaged();
        assertNoInstalledDistro();
    }

    /**
     * Tests that a distro with a badly formed distro version will be rejected.
     */
    public void testStageInstallWithErrorCode_withBadlyFormedDistroVersion() throws Exception {
        // Create a distro that has an invalid major distro version. It should be 3 numeric
        // characters, "." and 3 more numeric characters.
        DistroVersion validDistroVersion = new DistroVersion(1, 1, NEW_RULES_VERSION, 1);
        byte[] invalidFormatVersionBytes = validDistroVersion.toBytes();
        invalidFormatVersionBytes[0] = 'A';

        TimeZoneDistro distro = createTimeZoneDistroWithVersionBytes(invalidFormatVersionBytes);
        assertEquals(
                TimeZoneDistroInstaller.INSTALL_FAIL_BAD_DISTRO_STRUCTURE,
                installer.stageInstallWithErrorCode(distro.getBytes()));
        assertNoDistroOperationStaged();
        assertNoInstalledDistro();
    }

    /**
     * Tests that a distro with a badly formed revision will be rejected.
     */
    public void testStageInstallWithErrorCode_withBadlyFormedRevision() throws Exception {
        // Create a distro that has an invalid revision. It should be 3 numeric characters.
        DistroVersion validDistroVersion = new DistroVersion(1, 1, NEW_RULES_VERSION, 1);
        byte[] invalidRevisionBytes = validDistroVersion.toBytes();
        invalidRevisionBytes[invalidRevisionBytes.length - 3] = 'A';

        TimeZoneDistro distro = createTimeZoneDistroWithVersionBytes(invalidRevisionBytes);
        assertEquals(
                TimeZoneDistroInstaller.INSTALL_FAIL_BAD_DISTRO_STRUCTURE,
                installer.stageInstallWithErrorCode(distro.getBytes()));
        assertNoDistroOperationStaged();
        assertNoInstalledDistro();
    }

    /**
     * Tests that a distro with a badly formed rules version will be rejected.
     */
    public void testStageInstallWithErrorCode_withBadlyFormedRulesVersion() throws Exception {
        // Create a distro that has an invalid rules version. It should be in the form "2016c".
        DistroVersion validDistroVersion = new DistroVersion(1, 1, NEW_RULES_VERSION, 1);
        byte[] invalidRulesVersionBytes = validDistroVersion.toBytes();
        invalidRulesVersionBytes[invalidRulesVersionBytes.length - 6] = 'B';

        TimeZoneDistro distro = createTimeZoneDistroWithVersionBytes(invalidRulesVersionBytes);
        assertEquals(
                TimeZoneDistroInstaller.INSTALL_FAIL_BAD_DISTRO_STRUCTURE,
                installer.stageInstallWithErrorCode(distro.getBytes()));
        assertNoDistroOperationStaged();
        assertNoInstalledDistro();
    }

    public void testStageUninstall_noExistingDistro() throws Exception {
        // To stage an uninstall, there would need to be installed rules.
        assertFalse(installer.stageUninstall());

        assertNoDistroOperationStaged();
        assertNoInstalledDistro();
    }

    public void testStageUninstall_existingStagedDataDistro() throws Exception {
        // To stage an uninstall, we need to have some installed rules.
        TimeZoneDistro installedDistro = createValidTimeZoneDistro(NEW_RULES_VERSION, 1);
        simulateInstalledDistro(installedDistro);

        File stagedDataDir = installer.getStagedTzDataDir();
        assertTrue(stagedDataDir.mkdir());

        assertTrue(installer.stageUninstall());
        assertDistroUninstallStaged();
        assertInstalledDistro(installedDistro);
    }

    public void testStageUninstall_oldDirsAlreadyExists() throws Exception {
        // To stage an uninstall, we need to have some installed rules.
        TimeZoneDistro installedDistro = createValidTimeZoneDistro(NEW_RULES_VERSION, 1);
        simulateInstalledDistro(installedDistro);

        File oldStagedDataDir = installer.getOldStagedDataDir();
        assertTrue(oldStagedDataDir.mkdir());

        File workingDir = installer.getWorkingDir();
        assertTrue(workingDir.mkdir());

        assertTrue(installer.stageUninstall());

        assertDistroUninstallStaged();
        assertFalse(workingDir.exists());
        assertFalse(oldStagedDataDir.exists());
        assertInstalledDistro(installedDistro);
    }

    public void testGetSystemRulesVersion() throws Exception {
        assertEquals(SYSTEM_RULES_VERSION, installer.getSystemRulesVersion());
    }

    public void testGetInstalledDistroVersion() throws Exception {
        // Check result when nothing installed.
        assertNull(installer.getInstalledDistroVersion());
        assertNoDistroOperationStaged();
        assertNoInstalledDistro();

        // Now simulate there being an existing install active.
        TimeZoneDistro distro = createValidTimeZoneDistro(NEW_RULES_VERSION, 1);
        simulateInstalledDistro(distro);
        assertInstalledDistro(distro);

        // Check result when something installed.
        assertEquals(distro.getDistroVersion(), installer.getInstalledDistroVersion());
        assertNoDistroOperationStaged();
        assertInstalledDistro(distro);
    }

    public void testGetStagedDistroOperation() throws Exception {
        TimeZoneDistro distro1 = createValidTimeZoneDistro(NEW_RULES_VERSION, 1);
        TimeZoneDistro distro2 = createValidTimeZoneDistro(NEWER_RULES_VERSION, 1);

        // Check result when nothing staged.
        assertNull(installer.getStagedDistroOperation());
        assertNoDistroOperationStaged();
        assertNoInstalledDistro();

        // Check result after unsuccessfully staging an uninstall.
        // Can't stage an uninstall without an installed distro.
        assertFalse(installer.stageUninstall());
        assertNull(installer.getStagedDistroOperation());
        assertNoDistroOperationStaged();
        assertNoInstalledDistro();

        // Check result after staging an install.
        assertTrue(installer.install(distro1.getBytes()));
        StagedDistroOperation expectedStagedInstall =
                StagedDistroOperation.install(distro1.getDistroVersion());
        assertEquals(expectedStagedInstall, installer.getStagedDistroOperation());
        assertInstallDistroStaged(distro1);
        assertNoInstalledDistro();

        // Check result after unsuccessfully staging an uninstall (but after removing a staged
        // install). Can't stage an uninstall without an installed distro.
        assertFalse(installer.stageUninstall());
        assertNull(installer.getStagedDistroOperation());
        assertNoDistroOperationStaged();
        assertNoInstalledDistro();

        // Now simulate there being an existing install active.
        simulateInstalledDistro(distro1);
        assertInstalledDistro(distro1);

        // Check state after successfully staging an uninstall.
        assertTrue(installer.stageUninstall());
        StagedDistroOperation expectedStagedUninstall = StagedDistroOperation.uninstall();
        assertEquals(expectedStagedUninstall, installer.getStagedDistroOperation());
        assertDistroUninstallStaged();
        assertInstalledDistro(distro1);

        // Check state after successfully staging an install.
        assertEquals(TimeZoneDistroInstaller.INSTALL_SUCCESS,
                installer.stageInstallWithErrorCode(distro2.getBytes()));
        StagedDistroOperation expectedStagedInstall2 =
                StagedDistroOperation.install(distro2.getDistroVersion());
        assertEquals(expectedStagedInstall2, installer.getStagedDistroOperation());
        assertInstallDistroStaged(distro2);
        assertInstalledDistro(distro1);
    }

    private static TimeZoneDistro createValidTimeZoneDistro(
            String rulesVersion, int revision) throws Exception {
        return createValidTimeZoneDistroBuilder(rulesVersion, revision).build();
    }

    private static TimeZoneDistroBuilder createValidTimeZoneDistroBuilder(
            String rulesVersion, int revision) throws Exception {

        byte[] bionicTzData = createTzData(rulesVersion);
        byte[] icuData = new byte[] { 'a' };
        String tzlookupXml = "<timezones>\n"
                + "  <countryzones>\n"
                + "    <country code=\"us\">\n"
                + "      <id>America/New_York\"</id>\n"
                + "      <id>America/Los_Angeles</id>\n"
                + "    </country>\n"
                + "    <country code=\"gb\">\n"
                + "      <id>Europe/London</id>\n"
                + "    </country>\n"
                + "  </countryzones>\n"
                + "</timezones>\n";
        DistroVersion distroVersion = new DistroVersion(
                DistroVersion.CURRENT_FORMAT_MAJOR_VERSION,
                DistroVersion.CURRENT_FORMAT_MINOR_VERSION,
                rulesVersion,
                revision);
        return new TimeZoneDistroBuilder()
                .setDistroVersion(distroVersion)
                .setTzDataFile(bionicTzData)
                .setIcuDataFile(icuData)
                .setTzLookupXml(tzlookupXml);
    }

    private void assertInstallDistroStaged(TimeZoneDistro expectedDistro) throws Exception {
        assertTrue(testInstallDir.exists());

        File stagedTzDataDir = installer.getStagedTzDataDir();
        assertTrue(stagedTzDataDir.exists());

        File distroVersionFile =
                new File(stagedTzDataDir, TimeZoneDistro.DISTRO_VERSION_FILE_NAME);
        assertTrue(distroVersionFile.exists());

        File bionicFile = new File(stagedTzDataDir, TimeZoneDistro.TZDATA_FILE_NAME);
        assertTrue(bionicFile.exists());

        File icuFile = new File(stagedTzDataDir, TimeZoneDistro.ICU_DATA_FILE_NAME);
        assertTrue(icuFile.exists());

        File tzLookupFile = new File(stagedTzDataDir, TimeZoneDistro.TZLOOKUP_FILE_NAME);
        assertTrue(tzLookupFile.exists());

        // Assert getStagedDistroState() is reporting correctly.
        StagedDistroOperation stagedDistroOperation = installer.getStagedDistroOperation();
        assertNotNull(stagedDistroOperation);
        assertFalse(stagedDistroOperation.isUninstall);
        assertEquals(expectedDistro.getDistroVersion(), stagedDistroOperation.distroVersion);

        try (ZipInputStream zis = new ZipInputStream(
                new ByteArrayInputStream(expectedDistro.getBytes()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();
                File actualFile;
                if (entryName.endsWith(TimeZoneDistro.DISTRO_VERSION_FILE_NAME)) {
                   actualFile = distroVersionFile;
                } else if (entryName.endsWith(TimeZoneDistro.ICU_DATA_FILE_NAME)) {
                    actualFile = icuFile;
                } else if (entryName.endsWith(TimeZoneDistro.TZDATA_FILE_NAME)) {
                    actualFile = bionicFile;
                } else if (entryName.endsWith(TimeZoneDistro.TZLOOKUP_FILE_NAME)) {
                    actualFile = tzLookupFile;
                } else {
                    throw new AssertionFailedError("Unknown file found");
                }
                assertContentsMatches(zis, actualFile);
            }
        }

        // Also check no working directory is left lying around.
        File workingDir = installer.getWorkingDir();
        assertFalse(workingDir.exists());
    }

    private void assertContentsMatches(InputStream expected, File actual)
            throws Exception {
        byte[] actualBytes = IoUtils.readFileAsByteArray(actual.getPath());
        byte[] expectedBytes = Streams.readFullyNoClose(expected);
        assertArrayEquals(expectedBytes, actualBytes);
    }

    private void assertNoDistroOperationStaged() throws Exception {
        assertNull(installer.getStagedDistroOperation());

        File stagedTzDataDir = installer.getStagedTzDataDir();
        assertFalse(stagedTzDataDir.exists());

        // Also check no working directories are left lying around.
        File workingDir = installer.getWorkingDir();
        assertFalse(workingDir.exists());

        File oldDataDir = installer.getOldStagedDataDir();
        assertFalse(oldDataDir.exists());
    }

    private void assertDistroUninstallStaged() throws Exception {
        assertEquals(StagedDistroOperation.uninstall(), installer.getStagedDistroOperation());

        File stagedTzDataDir = installer.getStagedTzDataDir();
        assertTrue(stagedTzDataDir.exists());
        assertTrue(stagedTzDataDir.isDirectory());

        File uninstallTombstone =
                new File(stagedTzDataDir, TimeZoneDistroInstaller.UNINSTALL_TOMBSTONE_FILE_NAME);
        assertTrue(uninstallTombstone.exists());
        assertTrue(uninstallTombstone.isFile());

        // Also check no working directories are left lying around.
        File workingDir = installer.getWorkingDir();
        assertFalse(workingDir.exists());

        File oldDataDir = installer.getOldStagedDataDir();
        assertFalse(oldDataDir.exists());
    }

    private void simulateInstalledDistro(TimeZoneDistro timeZoneDistro) throws Exception {
        File currentTzDataDir = installer.getCurrentTzDataDir();
        assertFalse(currentTzDataDir.exists());
        assertTrue(currentTzDataDir.mkdir());
        timeZoneDistro.extractTo(currentTzDataDir);
    }

    private void assertNoInstalledDistro() {
        assertFalse(installer.getCurrentTzDataDir().exists());
    }

    private void assertInstalledDistro(TimeZoneDistro timeZoneDistro) throws Exception {
        File currentTzDataDir = installer.getCurrentTzDataDir();
        assertTrue(currentTzDataDir.exists());
        File versionFile = new File(currentTzDataDir, TimeZoneDistro.DISTRO_VERSION_FILE_NAME);
        assertTrue(versionFile.exists());
        byte[] expectedVersionBytes = timeZoneDistro.getDistroVersion().toBytes();
        byte[] actualVersionBytes = FileUtils.readBytes(versionFile, expectedVersionBytes.length);
        assertArrayEquals(expectedVersionBytes, actualVersionBytes);
    }

    private static byte[] createTzData(String rulesVersion) {
        return new ZoneInfoTestHelper.TzDataBuilder()
                .initializeToValid()
                .setHeaderMagic("tzdata" + rulesVersion)
                .build();
    }

    private static void createFile(File file, byte[] bytes) {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(bytes);
        } catch (IOException e) {
            fail(e.getMessage());
        }
    }

    /**
     * Creates a TimeZoneDistro containing arbitrary bytes in the version file. Used for testing
     * distros with badly formed version info.
     */
    private static TimeZoneDistro createTimeZoneDistroWithVersionBytes(byte[] versionBytes)
            throws Exception {

        // Create a valid distro, then manipulate the version file.
        TimeZoneDistro distro = createValidTimeZoneDistro(NEW_RULES_VERSION, 1);
        byte[] distroBytes = distro.getBytes();

        ByteArrayOutputStream baos = new ByteArrayOutputStream(distroBytes.length);
        try (ZipInputStream zipInputStream =
                     new ZipInputStream(new ByteArrayInputStream(distroBytes));
             ZipOutputStream zipOutputStream = new ZipOutputStream(baos)) {

            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                zipOutputStream.putNextEntry(entry);
                if (entry.getName().equals(TimeZoneDistro.DISTRO_VERSION_FILE_NAME)) {
                    // Replace the content.
                    zipOutputStream.write(versionBytes);
                }  else {
                    // Just copy the content.
                    Streams.copy(zipInputStream, zipOutputStream);
                }
                zipOutputStream.closeEntry();
                zipInputStream.closeEntry();
            }
        }
        return new TimeZoneDistro(baos.toByteArray());
    }
}
