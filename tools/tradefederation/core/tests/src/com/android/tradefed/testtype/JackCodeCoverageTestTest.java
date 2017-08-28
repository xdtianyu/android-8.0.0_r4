// Copyright 2016 Google Inc. All Rights Reserved.
package com.android.tradefed.testtype;

import static com.android.tradefed.testtype.JackCodeCoverageReportFormat.CSV;
import static com.android.tradefed.testtype.JackCodeCoverageReportFormat.HTML;
import static com.android.tradefed.testtype.JackCodeCoverageReportFormat.XML;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.doReturn;

import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.io.Files;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;

import junit.framework.TestCase;

import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/** Unit tests for {@link JackCodeCoverageTest}. */
public class JackCodeCoverageTestTest extends TestCase {

    private static final File COVERAGE_FILE1 = new File("/some/example/coverage.ec");
    private static final File COVERAGE_FILE2 = new File("/another/example/coverage.ec");
    private static final File COVERAGE_FILE3 = new File("/different/extension/example.exec");

    private static final File METADATA_FILE1 = new File("/some/example/coverage.em");
    private static final File METADATA_FILE2 = new File("/another/example/coverage.em");
    private static final File METADATA_FILE3 = new File("/different/extension/example.meta");

    private static final String EMMA_METADATA_RESOURCE_PATH = "/testdata/emma_meta.zip";
    private static final String EMMA_METADATA_ARTIFACT_NAME = "emma_meta.zip";
    private static final String FOO_METADATA =
            "/out/target/common/obj/JAVA_LIBRARIES/foo_intermediates/coverage.em";
    private static final String BAR_METADATA =
            "/out/target/common/obj/APPS/bar_intermediates/coverage.em";
    private static final String BAZ_METADATA =
            "/out/target/common/obj/APPS/baz_intermediates/coverage.em";

    private static final CommandResult SUCCESS = new CommandResult(CommandStatus.SUCCESS);
    private static final File FAKE_REPORT_TOOL = new File("/path/to/jack-jacoco-reporter.jar");

    public void testGetCoverageReporter() throws IOException {
        // Try to get the coverage reporter
        JackCodeCoverageTest coverageTest = new JackCodeCoverageTest();
        File coverageReporter = coverageTest.getCoverageReporter();

        // Verify that we were able to find the coverage reporter tool and that the tool exists
        assertNotNull(coverageReporter);
        assertTrue(coverageReporter.exists());
    }

    public void testGetMetadataFiles() throws IOException {
        // Prepare test data
        File metadataZipFile = extractResource(EMMA_METADATA_RESOURCE_PATH);

        // Set up mocks
        IBuildInfo mockBuild = Mockito.mock(IBuildInfo.class);
        doReturn(metadataZipFile).when(mockBuild).getFile(EMMA_METADATA_ARTIFACT_NAME);
        JackCodeCoverageTest coverageTest = Mockito.spy(new JackCodeCoverageTest());
        doReturn(mockBuild).when(coverageTest).getBuild();
        doReturn(EMMA_METADATA_ARTIFACT_NAME).when(coverageTest).getMetadataZipArtifact();

        // Get the metadata files
        Set<File> metadataFiles = null;
        try {
            metadataFiles = coverageTest.getMetadataFiles();
        } finally {
            // Cleanup
            FileUtil.deleteFile(metadataZipFile);
            coverageTest.cleanup();
        }

        // Verify that we got all of the metdata files
        assertNotNull(metadataFiles);
        assertEquals(3, metadataFiles.size());
        Set<String> expectedFiles = Sets.newHashSet(FOO_METADATA, BAR_METADATA, BAZ_METADATA);
        for (File metadata : metadataFiles) {
            for (String expected : expectedFiles) {
                if (metadata.getAbsolutePath().endsWith(expected)) {
                    expectedFiles.remove(expected);
                    break;
                }
            }
        }
        assertTrue(expectedFiles.isEmpty());
    }

    public void testGetMetadataFiles_withFilter() throws IOException {
        // Prepare test data
        File metadataZipFile = extractResource(EMMA_METADATA_RESOURCE_PATH);

        // Set up mocks
        IBuildInfo mockBuild = Mockito.mock(IBuildInfo.class);
        doReturn(metadataZipFile).when(mockBuild).getFile(EMMA_METADATA_ARTIFACT_NAME);
        JackCodeCoverageTest coverageTest = Mockito.spy(new JackCodeCoverageTest());
        doReturn(mockBuild).when(coverageTest).getBuild();
        doReturn(EMMA_METADATA_ARTIFACT_NAME).when(coverageTest).getMetadataZipArtifact();
        doReturn(Arrays.asList("glob:**/foo_intermediates/coverage.em")).when(coverageTest)
                .getMetadataFilesFilter();

        // Get the metadata files
        Set<File> metadataFiles = null;
        try {
            metadataFiles = coverageTest.getMetadataFiles();
        } finally {
            // Cleanup
            FileUtil.deleteFile(metadataZipFile);
            coverageTest.cleanup();
        }

        // Verify that only the framework metadata file was returned
        assertNotNull(metadataFiles);
        assertEquals(1, metadataFiles.size());
        File metadataFile = Iterables.getOnlyElement(metadataFiles);
        assertTrue(metadataFile.getAbsolutePath().endsWith(FOO_METADATA));
    }

    private File extractResource(String resourcePath) throws IOException {
        // Write the resource to a file and return it
        File dest = FileUtil.createTempFile(Files.getNameWithoutExtension(resourcePath),
                "." + Files.getFileExtension(resourcePath));
        FileUtil.writeToFile(getClass().getResourceAsStream(resourcePath), dest);
        return dest;
    }

    private void verifyCommandLine(List<String> commandLine, File reportTool,
            Collection<File> coverageFiles, Collection<File> metadataFiles, String format) {

        // Verify positional arguments
        assertEquals("java", commandLine.get(0));
        assertEquals("-jar", commandLine.get(1));
        assertEquals(reportTool.getAbsolutePath(), commandLine.get(2));

        // Verify the rest of the command line arguments
        assertTrue(commandLine.contains("--report-dir"));
        for (int i = 3; i < commandLine.size() - 1; i++) {
            switch (commandLine.get(i)) {
                case "--coverage-file":
                    File coverageFile = new File(commandLine.get(++i));
                    assertTrue(String.format("Unexpected coverage file: %s", coverageFile),
                            coverageFiles.remove(coverageFile));
                    break;
                case "--metadata-file":
                    File metadataFile = new File(commandLine.get(++i));
                    assertTrue(String.format("Unexpected metadata file: %s", metadataFile),
                            metadataFiles.remove(metadataFile));
                    break;
                case "--report-dir":
                    i++;
                    break;
                case "--report-type":
                    assertEquals(format, commandLine.get(++i));
                    break;
            }
        }
        assertTrue(coverageFiles.isEmpty());
        assertTrue(metadataFiles.isEmpty());
    }

    public void testGenerateCoverageReport_html() throws IOException {
        // Prepare some test data
        Set<File> coverageFiles = Sets.newHashSet(COVERAGE_FILE1, COVERAGE_FILE2, COVERAGE_FILE3);
        Set<File> metadataFiles = Sets.newHashSet(METADATA_FILE1, METADATA_FILE2, METADATA_FILE3);

        // Set up mocks
        JackCodeCoverageTest coverageTest = Mockito.spy(new JackCodeCoverageTest());
        IBuildInfo mockBuild = Mockito.mock(IBuildInfo.class);
        doReturn(mockBuild).when(coverageTest).getBuild();
        doReturn(metadataFiles).when(coverageTest).getMetadataFiles();
        doReturn(FAKE_REPORT_TOOL).when(coverageTest).getCoverageReporter();
        doReturn(SUCCESS).when(coverageTest).runTimedCmd(anyLong(), any(String[].class));

        File report = null;
        try {
            // Generate a coverage report
            report = coverageTest.generateCoverageReport(coverageFiles, HTML);

            // Verify that the command was called with the right arguments
            ArgumentCaptor<String[]> cmdLineCaptor = ArgumentCaptor.forClass(String[].class);
            Mockito.verify(coverageTest).runTimedCmd(anyLong(), cmdLineCaptor.capture());
            verifyCommandLine(Arrays.asList(cmdLineCaptor.getValue()), FAKE_REPORT_TOOL,
                    coverageFiles, metadataFiles, HTML.getReporterArg());
        } finally {
            FileUtil.recursiveDelete(report);
            coverageTest.cleanup();
        }
    }

    public void testGenerateCoverageReport_csv() throws IOException {
        // Prepare some test data
        Set<File> coverageFiles = Sets.newHashSet(COVERAGE_FILE1, COVERAGE_FILE2, COVERAGE_FILE3);
        Set<File> metadataFiles = Sets.newHashSet(METADATA_FILE1, METADATA_FILE2, METADATA_FILE3);

        // Set up mocks
        JackCodeCoverageTest coverageTest = Mockito.spy(new JackCodeCoverageTest());
        IBuildInfo mockBuild = Mockito.mock(IBuildInfo.class);
        doReturn(mockBuild).when(coverageTest).getBuild();
        doReturn(metadataFiles).when(coverageTest).getMetadataFiles();
        doReturn(FAKE_REPORT_TOOL).when(coverageTest).getCoverageReporter();
        doReturn(SUCCESS).when(coverageTest).runTimedCmd(anyLong(), any(String[].class));
        doReturn(new File("/copy/of/report.csv")).when(coverageTest).copyFile(any(File.class));

        // Generate a coverage report
        coverageTest.generateCoverageReport(coverageFiles, CSV);

        // Verify that the command was called with the right arguments
        ArgumentCaptor<String[]> cmdLineCaptor = ArgumentCaptor.forClass(String[].class);
        Mockito.verify(coverageTest).runTimedCmd(anyLong(), cmdLineCaptor.capture());
        verifyCommandLine(Arrays.asList(cmdLineCaptor.getValue()), FAKE_REPORT_TOOL,
                coverageFiles, metadataFiles, CSV.getReporterArg());
    }

    public void testGenerateCoverageReport_xml() throws IOException {
        // Prepare some test data
        Set<File> coverageFiles = Sets.newHashSet(COVERAGE_FILE1, COVERAGE_FILE2, COVERAGE_FILE3);
        Set<File> metadataFiles = Sets.newHashSet(METADATA_FILE1, METADATA_FILE2, METADATA_FILE3);

        // Set up mocks
        JackCodeCoverageTest coverageTest = Mockito.spy(new JackCodeCoverageTest());
        IBuildInfo mockBuild = Mockito.mock(IBuildInfo.class);
        doReturn(mockBuild).when(coverageTest).getBuild();
        doReturn(metadataFiles).when(coverageTest).getMetadataFiles();
        doReturn(FAKE_REPORT_TOOL).when(coverageTest).getCoverageReporter();
        doReturn(SUCCESS).when(coverageTest).runTimedCmd(anyLong(), any(String[].class));
        doReturn(new File("/copy/of/report.xml")).when(coverageTest).copyFile(any(File.class));

        // Generate a coverage report
        coverageTest.generateCoverageReport(coverageFiles, XML);

        // Verify that the command was called with the right arguments
        ArgumentCaptor<String[]> cmdLineCaptor = ArgumentCaptor.forClass(String[].class);
        Mockito.verify(coverageTest).runTimedCmd(anyLong(), cmdLineCaptor.capture());
        verifyCommandLine(Arrays.asList(cmdLineCaptor.getValue()), FAKE_REPORT_TOOL,
                coverageFiles, metadataFiles, XML.getReporterArg());
    }

    public void testGenerateCoverageReport_error() throws IOException {
        // Prepare some test data
        Set<File> coverageFiles = Sets.newHashSet(COVERAGE_FILE1, COVERAGE_FILE2, COVERAGE_FILE3);
        Set<File> metadataFiles = Sets.newHashSet(METADATA_FILE1, METADATA_FILE2, METADATA_FILE3);
        String stderr = "some error message";

        // Set up mocks
        CommandResult failed = new CommandResult(CommandStatus.FAILED);
        failed.setStderr(stderr);

        JackCodeCoverageTest coverageTest = Mockito.spy(new JackCodeCoverageTest());
        IBuildInfo mockBuild = Mockito.mock(IBuildInfo.class);
        doReturn(mockBuild).when(coverageTest).getBuild();
        doReturn(metadataFiles).when(coverageTest).getMetadataFiles();
        doReturn(FAKE_REPORT_TOOL).when(coverageTest).getCoverageReporter();
        doReturn(failed).when(coverageTest).runTimedCmd(anyLong(), any(String[].class));

        // Generate a coverage report
        try {
            coverageTest.generateCoverageReport(coverageFiles, HTML);
            fail("IOException not thrown");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains(stderr));
        }
    }
}
