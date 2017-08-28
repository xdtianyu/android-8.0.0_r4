/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.tradefed.result;

import com.android.ddmlib.IDevice;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.CodeCoverageTest;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.ZipUtil2;

import org.apache.commons.compress.archivers.zip.ZipFile;
import org.junit.Assert;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A {@link ITestInvocationListener} that will generate code coverage reports.
 * <p/>
 * Used in conjunction with {@link CodeCoverageTest}. This assumes that emmalib.jar
 * is in same filesystem location as ddmlib jar.
 */
@OptionClass(alias = "code-coverage-reporter")
public class CodeCoverageReporter implements ITestInvocationListener {
    @Option(name = "coverage-metadata-file-path", description =
            "The path of the Emma coverage meta data file used to generate the report.")
    private String mCoverageMetaFilePath = null;

    @Option(name = "coverage-output-path", description =
            "The location where to store the html coverage reports.",
            mandatory = true)
    private String mReportRootPath = null;

    @Option(name = "coverage-metadata-label", description =
            "The label of the Emma coverage meta data zip file inside the IBuildInfo.")
    private String mCoverageMetaZipFileName = "emma_meta.zip";

    @Option(name = "log-retention-days", description =
            "The number of days to keep generated coverage files")
    private Integer mLogRetentionDays = null;

    private static final int REPORT_GENERATION_TIMEOUT_MS = 3 * 60 * 1000;

    public static final String XML_REPORT_NAME = "report.xml";

    private IBuildInfo mBuildInfo;
    private LogFileSaver mLogFileSaver;

    private File mLocalTmpDir = null;
    private List<File> mCoverageFilesList = new ArrayList<File>();
    private File mCoverageMetaFile = null;
    private File mXMLReportFile = null;
    private File mReportOutputPath = null;

    public void setMetaZipFilePath(String filePath) {
        mCoverageMetaFilePath = filePath;
    }

    public void setReportRootPath(String rootPath) {
        mReportRootPath = rootPath;
    }

    public void setMetaZipFileName(String filename) {
        mCoverageMetaZipFileName = filename;
    }

    public void setLogRetentionDays(Integer logRetentionDays) {
        mLogRetentionDays = logRetentionDays;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testLog(String dataName, LogDataType dataType, InputStreamSource dataStream) {
        if (LogDataType.COVERAGE.equals(dataType)) {
            File coverageFile = saveLogAsFile(dataName, dataType, dataStream);
            mCoverageFilesList.add(coverageFile);
            CLog.d("Saved a new device coverage file saved at %s", coverageFile.getAbsolutePath());
        }
    }

    private File saveLogAsFile(String dataName, LogDataType dataType,
            InputStreamSource dataStream) {
        try {
            File logFile = mLogFileSaver.saveLogData(dataName, dataType,
                    dataStream.createInputStream());
            return logFile;
        } catch (IOException e) {
            CLog.e(e);
        }
        return null;
    }

    public File getXMLReportFile() {
        return mXMLReportFile;
    }

    public File getReportOutputPath() {
        return mReportOutputPath;
    }

    public File getHTMLReportFile() {
        return new File(mReportOutputPath, "index.html");
    }

    /** {@inheritDoc} */
    @Override
    public void invocationStarted(IInvocationContext context) {
        // FIXME: do code coverage reporting for each different build info (for multi-device case)
        mBuildInfo = context.getBuildInfos().get(0);

        // Append build and branch information to output directory.
        mReportOutputPath = generateReportLocation(mReportRootPath);
        CLog.d("ReportOutputPath: %s", mReportOutputPath);

        mXMLReportFile = new File(mReportOutputPath, XML_REPORT_NAME);
        CLog.d("ReportOutputPath: %s", mXMLReportFile);

        // We want to save all other files in the same directory as the report.
        mLogFileSaver = new LogFileSaver(mReportOutputPath);

        CLog.d("ReportOutputPath %s", mReportOutputPath.getAbsolutePath());
        CLog.d("LogfileSaver file dir %s", mLogFileSaver.getFileDir().getAbsolutePath());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invocationEnded(long elapsedTime) {
        // Generate report
        generateReport();
    }

    public void generateReport() {
        CLog.d("Generating report for code coverage");
        try {
            fetchAppropriateMetaDataFile();

            if (!mCoverageFilesList.isEmpty()) {
                generateCoverageReport(mCoverageFilesList, mCoverageMetaFile);
            } else {
                CLog.w("No coverage files were generated by the test. " +
                        "Perhaps test failed to run successfully.");
            }
        } finally {
            // Cleanup residual files.
            if (!mCoverageFilesList.isEmpty()) {
                for (File coverageFile : mCoverageFilesList) {
                    FileUtil.recursiveDelete(coverageFile);
                }
            }
            if (mLocalTmpDir != null) {
                FileUtil.recursiveDelete(mLocalTmpDir);

            }
        }
    }

    private void fetchAppropriateMetaDataFile() {
        File coverageZipFile = mBuildInfo.getFile(mCoverageMetaZipFileName);
        Assert.assertNotNull("Failed to get the coverage metadata zipfile from the build.",
                coverageZipFile);
        CLog.d("Coverage zip file: %s", coverageZipFile.getAbsolutePath());

        try {
            mLocalTmpDir = FileUtil.createTempDir("emma-meta");
            ZipFile zipFile = new ZipFile(coverageZipFile);
            ZipUtil2.extractZip(zipFile, mLocalTmpDir);
            File coverageMetaFile;
            if (mCoverageMetaFilePath == null) {
                coverageMetaFile = FileUtil.findFile(mLocalTmpDir, "coverage.em");
            } else {
                coverageMetaFile = new File(mLocalTmpDir, mCoverageMetaFilePath);
            }
            if (coverageMetaFile.exists()) {
                mCoverageMetaFile = coverageMetaFile;
                CLog.d("Coverage meta data file %s", mCoverageMetaFile.getAbsolutePath());
            }
        } catch (IOException e) {
            CLog.e(e);
        }
    }

    private File generateReportLocation(String rootPath) {
        String branchName = mBuildInfo.getBuildBranch();
        String buildId = mBuildInfo.getBuildId();
        String testTag = mBuildInfo.getTestTag();
        File branchPath = new File(rootPath, branchName);
        File buildIdPath = new File(branchPath, buildId);
        File testTagPath = new File(buildIdPath, testTag);
        FileUtil.mkdirsRWX(testTagPath);
        if (mLogRetentionDays != null) {
            RetentionFileSaver f = new RetentionFileSaver();
            f.writeRetentionFile(testTagPath, mLogRetentionDays);
        }
        return testTagPath;
    }

    private void generateCoverageReport(List<File> coverageFileList, File metaFile) {
        Assert.assertFalse("Could not find a valid coverage file.", coverageFileList.isEmpty());
        Assert.assertNotNull("Could not find a valid meta data coverage file.", metaFile);
        String emmaPath = findEmmaJarPath();
        List<String> cmdList = new ArrayList<String>();
        cmdList.addAll(Arrays.asList("java", "-cp", emmaPath, "emma", "report", "-r", "html",
                "-r", "xml", "-in", metaFile.getAbsolutePath(), "-Dreport.html.out.encoding=UTF-8",
                "-Dreport.html.out.file=" + mReportOutputPath.getAbsolutePath() + "/index.html",
                "-Dreport.xml.out.file=" + mReportOutputPath.getAbsolutePath() + "/report.xml"));
        // Now append all the coverage files collected.
        for (File coverageFile : coverageFileList) {
            cmdList.add("-in");
            cmdList.add(coverageFile.getAbsolutePath());
        }
        String[] cmd = cmdList.toArray(new String[cmdList.size()]);
        IRunUtil runUtil = RunUtil.getDefault();
        CommandResult result = runUtil.runTimedCmd(REPORT_GENERATION_TIMEOUT_MS, cmd);
        if (!result.getStatus().equals(CommandStatus.SUCCESS)) {
            CLog.e("Failed to generate coverage report. stderr: %s",
                    result.getStderr());
        } else {
            // Make the report world readable.
            boolean setPerms = FileUtil.chmodRWXRecursively(mReportOutputPath);
            if (!setPerms) {
                CLog.w("Failed to set %s to be world accessible.",
                        mReportOutputPath.getAbsolutePath());
            }
        }
    }

    /**
     * Tries to find emma.jar in same location as ddmlib.jar.
     *
     * @return full path to emma jar file
     * @throws AssertionError if could not find emma jar
     */
    String findEmmaJarPath() {
        String ddmlibPath = IDevice.class.getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .getFile();
        Assert.assertFalse("failed to find ddmlib path", ddmlibPath.isEmpty());
        File parentFolder = new File(ddmlibPath);
        File emmaJar = new File(parentFolder.getParent(), "emmalib.jar");
        Assert.assertTrue(
                String.format("Failed to find emma.jar in %s", emmaJar.getAbsolutePath()),
                emmaJar.exists());
        CLog.d("Found emma jar at %s", emmaJar.getAbsolutePath());
        return emmaJar.getAbsolutePath();
    }
}
