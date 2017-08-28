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

import android.util.Slog;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import libcore.tzdata.shared2.DistroException;
import libcore.tzdata.shared2.DistroVersion;
import libcore.tzdata.shared2.FileUtils;
import libcore.tzdata.shared2.StagedDistroOperation;
import libcore.tzdata.shared2.TimeZoneDistro;
import libcore.util.TimeZoneFinder;
import libcore.util.ZoneInfoDB;

/**
 * A distro-validation / extraction class. Separate from the services code that uses it for easier
 * testing. This class is not thread-safe: callers are expected to handle mutual exclusion.
 */
public class TimeZoneDistroInstaller {
    /** {@link #stageInstallWithErrorCode(byte[])} result code: Success. */
    public final static int INSTALL_SUCCESS = 0;
    /** {@link #stageInstallWithErrorCode(byte[])} result code: Distro corrupt. */
    public final static int INSTALL_FAIL_BAD_DISTRO_STRUCTURE = 1;
    /** {@link #stageInstallWithErrorCode(byte[])} result code: Distro version incompatible. */
    public final static int INSTALL_FAIL_BAD_DISTRO_FORMAT_VERSION = 2;
    /** {@link #stageInstallWithErrorCode(byte[])} result code: Distro rules too old for device. */
    public final static int INSTALL_FAIL_RULES_TOO_OLD = 3;
    /** {@link #stageInstallWithErrorCode(byte[])} result code: Distro content failed validation. */
    public final static int INSTALL_FAIL_VALIDATION_ERROR = 4;

    // This constant must match one in system/core/tzdatacheck.cpp.
    private static final String STAGED_TZ_DATA_DIR_NAME = "staged";
    // This constant must match one in system/core/tzdatacheck.cpp.
    private static final String CURRENT_TZ_DATA_DIR_NAME = "current";
    private static final String WORKING_DIR_NAME = "working";
    private static final String OLD_TZ_DATA_DIR_NAME = "old";

    /**
     * The name of the file in the staged directory used to indicate a staged uninstallation.
     */
    // This constant must match one in system/core/tzdatacheck.cpp.
    // VisibleForTesting.
    public static final String UNINSTALL_TOMBSTONE_FILE_NAME = "STAGED_UNINSTALL_TOMBSTONE";

    private final String logTag;
    private final File systemTzDataFile;
    private final File oldStagedDataDir;
    private final File stagedTzDataDir;
    private final File currentTzDataDir;
    private final File workingDir;

    public TimeZoneDistroInstaller(String logTag, File systemTzDataFile, File installDir) {
        this.logTag = logTag;
        this.systemTzDataFile = systemTzDataFile;
        oldStagedDataDir = new File(installDir, OLD_TZ_DATA_DIR_NAME);
        stagedTzDataDir = new File(installDir, STAGED_TZ_DATA_DIR_NAME);
        currentTzDataDir = new File(installDir, CURRENT_TZ_DATA_DIR_NAME);
        workingDir = new File(installDir, WORKING_DIR_NAME);
    }

    // VisibleForTesting
    File getOldStagedDataDir() {
        return oldStagedDataDir;
    }

    // VisibleForTesting
    File getStagedTzDataDir() {
        return stagedTzDataDir;
    }

    // VisibleForTesting
    File getCurrentTzDataDir() {
        return currentTzDataDir;
    }

    // VisibleForTesting
    File getWorkingDir() {
        return workingDir;
    }

    /**
     * Stage an install of the supplied content, to be installed the next time the device boots.
     *
     * <p>Errors during unpacking or staging will throw an {@link IOException}.
     * If the distro content is invalid this method returns {@code false}.
     * If the installation completed successfully this method returns {@code true}.
     */
    public boolean install(byte[] content) throws IOException {
        int result = stageInstallWithErrorCode(content);
        return result == INSTALL_SUCCESS;
    }

    /**
     * Stage an install of the supplied content, to be installed the next time the device boots.
     *
     * <p>Errors during unpacking or staging will throw an {@link IOException}.
     * Returns {@link #INSTALL_SUCCESS} or an error code.
     */
    public int stageInstallWithErrorCode(byte[] content) throws IOException {
        if (oldStagedDataDir.exists()) {
            FileUtils.deleteRecursive(oldStagedDataDir);
        }
        if (workingDir.exists()) {
            FileUtils.deleteRecursive(workingDir);
        }

        Slog.i(logTag, "Unpacking / verifying time zone update");
        try {
            unpackDistro(content, workingDir);

            DistroVersion distroVersion;
            try {
                distroVersion = readDistroVersion(workingDir);
            } catch (DistroException e) {
                Slog.i(logTag, "Invalid distro version: " + e.getMessage());
                return INSTALL_FAIL_BAD_DISTRO_STRUCTURE;
            }
            if (distroVersion == null) {
                Slog.i(logTag, "Update not applied: Distro version could not be loaded");
                return INSTALL_FAIL_BAD_DISTRO_STRUCTURE;
            }
            if (!DistroVersion.isCompatibleWithThisDevice(distroVersion)) {
                Slog.i(logTag, "Update not applied: Distro format version check failed: "
                        + distroVersion);
                return INSTALL_FAIL_BAD_DISTRO_FORMAT_VERSION;
            }

            if (!checkDistroDataFilesExist(workingDir)) {
                Slog.i(logTag, "Update not applied: Distro is missing required data file(s)");
                return INSTALL_FAIL_BAD_DISTRO_STRUCTURE;
            }

            if (!checkDistroRulesNewerThanSystem(systemTzDataFile, distroVersion)) {
                Slog.i(logTag, "Update not applied: Distro rules version check failed");
                return INSTALL_FAIL_RULES_TOO_OLD;
            }

            // Validate the tzdata file.
            File zoneInfoFile = new File(workingDir, TimeZoneDistro.TZDATA_FILE_NAME);
            ZoneInfoDB.TzData tzData = ZoneInfoDB.TzData.loadTzData(zoneInfoFile.getPath());
            if (tzData == null) {
                Slog.i(logTag, "Update not applied: " + zoneInfoFile + " could not be loaded");
                return INSTALL_FAIL_VALIDATION_ERROR;
            }
            try {
                tzData.validate();
            } catch (IOException e) {
                Slog.i(logTag, "Update not applied: " + zoneInfoFile + " failed validation", e);
                return INSTALL_FAIL_VALIDATION_ERROR;
            } finally {
                tzData.close();
            }

            // Validate the tzlookup.xml file.
            File tzLookupFile = new File(workingDir, TimeZoneDistro.TZLOOKUP_FILE_NAME);
            if (!tzLookupFile.exists()) {
                Slog.i(logTag, "Update not applied: " + tzLookupFile + " does not exist");
                return INSTALL_FAIL_BAD_DISTRO_STRUCTURE;
            }
            try {
                TimeZoneFinder timeZoneFinder =
                        TimeZoneFinder.createInstance(tzLookupFile.getPath());
                timeZoneFinder.validate();
            } catch (IOException e) {
                Slog.i(logTag, "Update not applied: " + tzLookupFile + " failed validation", e);
                return INSTALL_FAIL_VALIDATION_ERROR;
            }

            // TODO(nfuller): Add validity checks for ICU data / canarying before applying.
            // http://b/31008728

            Slog.i(logTag, "Applying time zone update");
            FileUtils.makeDirectoryWorldAccessible(workingDir);

            // Check if there is already a staged install or uninstall and remove it if there is.
            if (!stagedTzDataDir.exists()) {
                Slog.i(logTag, "Nothing to unstage at " + stagedTzDataDir);
            } else {
                Slog.i(logTag, "Moving " + stagedTzDataDir + " to " + oldStagedDataDir);
                // Move stagedTzDataDir out of the way in one operation so we can't partially delete
                // the contents.
                FileUtils.rename(stagedTzDataDir, oldStagedDataDir);
            }

            // Move the workingDir to be the new staged directory.
            Slog.i(logTag, "Moving " + workingDir + " to " + stagedTzDataDir);
            FileUtils.rename(workingDir, stagedTzDataDir);
            Slog.i(logTag, "Install staged: " + stagedTzDataDir + " successfully created");
            return INSTALL_SUCCESS;
        } finally {
            deleteBestEffort(oldStagedDataDir);
            deleteBestEffort(workingDir);
        }
    }

    /**
     * Stage an uninstall of the current timezone update in /data which, on reboot, will return the
     * device to using data from /system. Returns {@code true} if staging the uninstallation was
     * successful, {@code false} if there was nothing installed in /data to uninstall. If there was
     * something else staged it will be replaced by this call.
     *
     * <p>Errors encountered during uninstallation will throw an {@link IOException}.
     */
    public boolean stageUninstall() throws IOException {
        Slog.i(logTag, "Uninstalling time zone update");

        if (oldStagedDataDir.exists()) {
            // If we can't remove this, an exception is thrown and we don't continue.
            FileUtils.deleteRecursive(oldStagedDataDir);
        }
        if (workingDir.exists()) {
            FileUtils.deleteRecursive(workingDir);
        }

        try {
            // Check if there is already an install or uninstall staged and remove it.
            if (!stagedTzDataDir.exists()) {
                Slog.i(logTag, "Nothing to unstage at " + stagedTzDataDir);
            } else {
                Slog.i(logTag, "Moving " + stagedTzDataDir + " to " + oldStagedDataDir);
                // Move stagedTzDataDir out of the way in one operation so we can't partially delete
                // the contents.
                FileUtils.rename(stagedTzDataDir, oldStagedDataDir);
            }

            // If there's nothing actually installed, there's nothing to uninstall so no need to
            // stage anything.
            if (!currentTzDataDir.exists()) {
                Slog.i(logTag, "Nothing to uninstall at " + currentTzDataDir);
                return false;
            }

            // Stage an uninstall in workingDir.
            FileUtils.ensureDirectoriesExist(workingDir, true /* makeWorldReadable */);
            FileUtils.createEmptyFile(new File(workingDir, UNINSTALL_TOMBSTONE_FILE_NAME));

            // Move the workingDir to be the new staged directory.
            Slog.i(logTag, "Moving " + workingDir + " to " + stagedTzDataDir);
            FileUtils.rename(workingDir, stagedTzDataDir);
            Slog.i(logTag, "Uninstall staged: " + stagedTzDataDir + " successfully created");

            return true;
        } finally {
            deleteBestEffort(oldStagedDataDir);
            deleteBestEffort(workingDir);
        }
    }

    /**
     * Reads the currently installed distro version. Returns {@code null} if there is no distro
     * installed.
     *
     * @throws IOException if there was a problem reading data from /data
     * @throws DistroException if there was a problem with the installed distro format/structure
     */
    public DistroVersion getInstalledDistroVersion() throws DistroException, IOException {
        if (!currentTzDataDir.exists()) {
            return null;
        }
        return readDistroVersion(currentTzDataDir);
    }

    /**
     * Reads information about any currently staged distro operation. Returns {@code null} if there
     * is no distro operation staged.
     *
     * @throws IOException if there was a problem reading data from /data
     * @throws DistroException if there was a problem with the staged distro format/structure
     */
    public StagedDistroOperation getStagedDistroOperation() throws DistroException, IOException {
        if (!stagedTzDataDir.exists()) {
            return null;
        }
        if (new File(stagedTzDataDir, UNINSTALL_TOMBSTONE_FILE_NAME).exists()) {
            return StagedDistroOperation.uninstall();
        } else {
            return StagedDistroOperation.install(readDistroVersion(stagedTzDataDir));
        }
    }

    /**
     * Reads the timezone rules version present in /system. i.e. the version that would be present
     * after a factory reset.
     *
     * @throws IOException if there was a problem reading data
     */
    public String getSystemRulesVersion() throws IOException {
        return readSystemRulesVersion(systemTzDataFile);
    }

    private void deleteBestEffort(File dir) {
        if (dir.exists()) {
            Slog.i(logTag, "Deleting " + dir);
            try {
                FileUtils.deleteRecursive(dir);
            } catch (IOException e) {
                // Logged but otherwise ignored.
                Slog.w(logTag, "Unable to delete " + dir, e);
            }
        }
    }

    private void unpackDistro(byte[] content, File targetDir) throws IOException {
        Slog.i(logTag, "Unpacking update content to: " + targetDir);
        TimeZoneDistro distro = new TimeZoneDistro(content);
        distro.extractTo(targetDir);
    }

    private boolean checkDistroDataFilesExist(File unpackedContentDir) throws IOException {
        Slog.i(logTag, "Verifying distro contents");
        return FileUtils.filesExist(unpackedContentDir,
                TimeZoneDistro.TZDATA_FILE_NAME,
                TimeZoneDistro.ICU_DATA_FILE_NAME);
    }

    private DistroVersion readDistroVersion(File distroDir) throws DistroException, IOException {
        Slog.i(logTag, "Reading distro format version");
        File distroVersionFile = new File(distroDir, TimeZoneDistro.DISTRO_VERSION_FILE_NAME);
        if (!distroVersionFile.exists()) {
            throw new DistroException("No distro version file found: " + distroVersionFile);
        }
        byte[] versionBytes =
                FileUtils.readBytes(distroVersionFile, DistroVersion.DISTRO_VERSION_FILE_LENGTH);
        return DistroVersion.fromBytes(versionBytes);
    }

    /**
     * Returns true if the the distro IANA rules version is >= system IANA rules version.
     */
    private boolean checkDistroRulesNewerThanSystem(
            File systemTzDataFile, DistroVersion distroVersion) throws IOException {

        // We only check the /system tzdata file and assume that other data like ICU is in sync.
        // There is a CTS test that checks ICU and bionic/libcore are in sync.
        Slog.i(logTag, "Reading /system rules version");
        String systemRulesVersion = readSystemRulesVersion(systemTzDataFile);

        String distroRulesVersion = distroVersion.rulesVersion;
        // canApply = distroRulesVersion >= systemRulesVersion
        boolean canApply = distroRulesVersion.compareTo(systemRulesVersion) >= 0;
        if (!canApply) {
            Slog.i(logTag, "Failed rules version check: distroRulesVersion="
                    + distroRulesVersion + ", systemRulesVersion=" + systemRulesVersion);
        } else {
            Slog.i(logTag, "Passed rules version check: distroRulesVersion="
                    + distroRulesVersion + ", systemRulesVersion=" + systemRulesVersion);
        }
        return canApply;
    }

    private String readSystemRulesVersion(File systemTzDataFile) throws IOException {
        if (!systemTzDataFile.exists()) {
            Slog.i(logTag, "tzdata file cannot be found in /system");
            throw new FileNotFoundException("system tzdata does not exist: " + systemTzDataFile);
        }
        return ZoneInfoDB.TzData.getRulesVersion(systemTzDataFile);
    }
}
