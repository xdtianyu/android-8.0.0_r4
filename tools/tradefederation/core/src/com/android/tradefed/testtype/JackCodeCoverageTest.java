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

import static com.android.tradefed.testtype.JackCodeCoverageReportFormat.CSV;
import static com.android.tradefed.testtype.JackCodeCoverageReportFormat.HTML;
import static com.android.tradefed.testtype.JackCodeCoverageReportFormat.XML;

import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.ZipUtil2;

import com.google.common.collect.Lists;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.PathMatcher;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * An {@link IRemoteTest} which runs installed instrumentation test(s) and generates a code coverage
 * report. This test type supports collecting coverage information from classes that have already
 * been instrumented by the Jack compiler.
 */
@OptionClass(alias = "jack-coverage")
public class JackCodeCoverageTest extends CodeCoverageTestBase<JackCodeCoverageReportFormat> {

    /** Location of the report generation tool. */
    private File mCoverageReporter = null;

    @Option(name = "metadata-zip-artifact",
            description = "The name of the build artifact that contains the coverage metadata " +
            "files. Defaults to emma_meta.zip")
    private String mMetadataZipArtifact = "emma_meta.zip" ;

    @Option(name = "metadata-files-filter",
            description = "Glob pattern used to select the metadata files to use when " +
            "generating the coverage report. May be repeated.")
    private List<String> mMetadataFilesFilter = new ArrayList<>();

    @Option(name = "report-timeout", isTimeVal = true,
            description = "Maximum time to wait for coverage report generation to complete in ms")
    private long mReportTimeout = 5 * 60 * 1000; // 5 Minutes

    @Option(name = "report-format",
            description = "Which output format(s) to use when generating the coverage report. " +
            "May be repeated to output multiple formats. Defaults to HTML if unspecified.")
    private List<JackCodeCoverageReportFormat> mReportFormat = new ArrayList<>();

    /**
     * {@inheritDoc}
     */
    @Override
    protected List<JackCodeCoverageReportFormat> getReportFormat() {
        return mReportFormat.isEmpty() ? Arrays.asList(HTML) : mReportFormat;
    }

    /**
     * Returns the name of the build artifact that contains the coverage metadata.
     */
    protected String getMetadataZipArtifact() {
        return mMetadataZipArtifact;
    }

    /**
     * Returns the list of glob patterns used to select metadata files to use when generating the
     * coverage report. Exposed for unit testing.
     */
    protected List<String> getMetadataFilesFilter() {
        return mMetadataFilesFilter.isEmpty() ? Arrays.asList("glob:**.em") : mMetadataFilesFilter;
    }

    /** Returns the maximum time to wait for the coverage report to be generated. */
    protected long getReportTimeout() {
        return mReportTimeout;
    }

    /** Returns the location of the report generation tool. */
    protected File getCoverageReporter() throws IOException {
        if (mCoverageReporter == null) {
            // The coverage reporter tool should already be on our class path. This code computes
            // the location of the jar file containing the coverage tool's main class.
            Class<?> mainClass = com.android.jack.tools.jacoco.Main.class;
            CodeSource source = mainClass.getProtectionDomain().getCodeSource();
            if (source == null) {
                throw new IOException("Failed to find coverage reporter tool");
            }
            mCoverageReporter = new File(source.getLocation().getFile());
        }
        return mCoverageReporter;
    }

    private File mMetadataCache = null;

    /**
     * Returns the set of metadata files that should be used to generate the coverage report.
     *
     * @return The set of metadata files that match at least one of the metadata-files-filters.
     */
    protected Set<File> getMetadataFiles() throws IOException {
        // Extract the metadata files if we haven't already
        if (mMetadataCache == null) {
            File metadataZip = getBuild().getFile(getMetadataZipArtifact());
            mMetadataCache = ZipUtil2.extractZipToTemp(metadataZip, "metadata");
        }

        // Convert the filter strings to PathMatchers
        FileSystem fs = FileSystems.getDefault();
        Set<PathMatcher> filters = getMetadataFilesFilter().stream()
                .map(f -> fs.getPathMatcher(f))
                .collect(Collectors.toSet());

        // Find metadata files that match the specified filters
        return Files.walk(mMetadataCache.toPath())
                .filter(p -> filters.stream().anyMatch(f -> f.matches(p)))
                .map(p -> p.toFile())
                .collect(Collectors.toSet());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected File generateCoverageReport(Collection<File> executionFiles,
            JackCodeCoverageReportFormat format) throws IOException {

        // Construct a command line for running the report generation tool
        File dest = FileUtil.createTempDir("coverage");
        File coverageReporter = getCoverageReporter();
        List<String> cmd = Lists.newArrayList("java", "-jar",
                coverageReporter.getAbsolutePath());
        for (File metadata : getMetadataFiles()) {
            cmd.add("--metadata-file");
            cmd.add(metadata.getAbsolutePath());
        }
        for (File coverageFile : executionFiles) {
            cmd.add("--coverage-file");
            cmd.add(coverageFile.getAbsolutePath());
        }
        cmd.add("--report-dir");
        cmd.add(dest.getAbsolutePath());
        cmd.add("--report-type");
        cmd.add(format.getReporterArg());

        // Run the command
        CommandResult result = runTimedCmd(getReportTimeout(), cmd.toArray(new String[0]));
        if (!CommandStatus.SUCCESS.equals(result.getStatus())) {
            FileUtil.recursiveDelete(dest);
            throw new IOException(String.format(
                    "Failed to generate code coverage report: %s", result.getStderr()));
        }

        // CSV reports (and XML) get put in dest/report.csv. Return the actual report file.
        if (CSV.equals(format) || XML.equals(format)) {
            try {
                // Make a copy of the file, so we can clean up the dest directory
                String ext = format.getLogDataType().getFileExt();
                return copyFile(new File(dest, String.format("report.%s", ext)));
            } finally {
                FileUtil.recursiveDelete(dest);
            }
        } else {
            return dest;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void cleanup() {
        FileUtil.recursiveDelete(mMetadataCache);
        mMetadataCache = null;
    }

    /** Returns a copy of the given {@code source} file. */
    File copyFile(File source) throws IOException {
        // Create a new file with the same prefix and extension as the source file
        String filename = source.getPath();
        int pos = filename.lastIndexOf(".");
        File ret = FileUtil.createTempFile(filename.substring(0, pos), filename.substring(pos));

        // Copy the contents of the source file to the new file and return it
        FileUtil.copyFile(source, ret);
        return ret;
    }

    /** Calls {@link RunUtil#runTimedCmd(long, String[])}. Exposed for unit testing. */
    CommandResult runTimedCmd(long timeout, String[] command) {
        return RunUtil.getDefault().runTimedCmd(timeout, command);
    }
}
