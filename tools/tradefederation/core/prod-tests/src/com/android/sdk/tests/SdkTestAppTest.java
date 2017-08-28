/*
 * Copyright (C) 2011 The Android Open Source Project
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
package com.android.sdk.tests;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.ISdkBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.xml.AndroidManifestWriter;

import org.junit.Assert;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.Properties;

/**
 * A class that builds all the test-apps included in an SDK with ant, against all targets included
 * in SDK, and verifies success.
 */
public class SdkTestAppTest implements IRemoteTest, IBuildReceiver {

    private ISdkBuildInfo mSdkBuild;

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mSdkBuild = (ISdkBuildInfo)buildInfo;
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
         Assert.assertNotNull("missing sdk build to test", mSdkBuild);
         Assert.assertNotNull("missing sdk build to test", mSdkBuild.getSdkDir());

         CLog.i(String.format("Running test-app tests in sdk %s",
                 mSdkBuild.getSdkDir().getAbsolutePath()));

         // get the path to the test-apps
         File sdkTestAppDir = FileUtil.getFileForPath(mSdkBuild.getSdkDir(), "tests",
                 "testapps");
         Assert.assertTrue(String.format("could not find tests/testapps folder in sdk %s",
                 mSdkBuild.getSdkDir()), sdkTestAppDir.isDirectory() &&
                 sdkTestAppDir.listFiles() != null);
         Assert.assertTrue(String.format("Could not find targets for sdk %s",
                 mSdkBuild.getSdkDir()), mSdkBuild.getSdkTargets() != null &&
                 mSdkBuild.getSdkTargets().length > 0);

         long startTime = System.currentTimeMillis();
         listener.testRunStarted("testapp", 0);

         for (String target : mSdkBuild.getSdkTargets()) {
             buildTestAppsForTarget(target, sdkTestAppDir.listFiles(), listener);
         }

         listener.testRunEnded(System.currentTimeMillis() - startTime, Collections.EMPTY_MAP);
    }

    /**
     * Builds all test apps found in given SDK, for given target
     *
     * @param target the SDK target to build against
     * @param testAppDirs the list of test-app directories
     * @param listener the test result listener
     */
    private void buildTestAppsForTarget(String target, File[] testAppDirs,
            ITestInvocationListener listener) {
        // first build libraries, since other projects may have dependency on them
        for (File testAppDir : testAppDirs) {
            if (isLibrary(testAppDir)) {
                buildTestApp(target, listener, testAppDir, true);
            }
        }
        // now build the test apps and record the results as dynamically generated test names
        for (File testAppDir : testAppDirs) {
            if (isAndroidApp(testAppDir)) {
                buildTestApp(target, listener, testAppDir, false);

            }
        }
    }

    /**
     * Determines if given test app is an Android library project.
     * <p/>
     * Looks for the 'android.library' property in this app's default.properties file
     *
     * @param testAppDir the app's directory
     * @return <code>true</code> if app is an Android library
     */
    private boolean isLibrary(File testAppDir) {
        if (!testAppDir.isDirectory()) {
            return false;
        }
        File propertyFile = new File(testAppDir, "default.properties");
        if (propertyFile.isFile()) {
            Properties defaultProp = new Properties();
            InputStream propStream;
            try {
                propStream = new BufferedInputStream(new FileInputStream(propertyFile));
                defaultProp.load(propStream);
                String libPropString = defaultProp.getProperty("android.library");
                return libPropString != null && Boolean.parseBoolean(libPropString);
            } catch (IOException e) {
                CLog.e("Failed to parse %s", propertyFile.getAbsolutePath());
                CLog.e(e);
            }
        }
        return false;
    }

    /**
     * Determines if given test app is a Android application project
     *
     * @param testAppDir the given test app directory root to evaluate
     * @return <code>true</code> if <var>testAppDir</var> is a Android application project
     */
    private boolean isAndroidApp(File testAppDir) {
        if (!testAppDir.isDirectory()) {
            return false;
        }
        if (isLibrary(testAppDir)) {
            return false;
        }
        File manifestFile = new File(testAppDir, "AndroidManifest.xml");
        return manifestFile.exists();
    }

    /**
     * Build test app, and report results to the <var>listener</var> using test name
     * 'com.android.tradefed.testtype.SdkTestAppTest#(testAppName)(sdkTarget)'
     *
     * @param target the sdk target to build against
     * @param listener the {@link ITestInvocationListener}
     * @param testAppDir the {@link File} pointing to test app's root directory
     */
    private void buildTestApp(String target, ITestInvocationListener listener, File testAppDir,
            boolean isLibrary) {
        CLog.i("Building %s test-app for target %s", testAppDir.getName(), target);
        // dynamically generate a test name
        TestIdentifier testId = new TestIdentifier(this.getClass().getName(), String.format(
                "%s_%s", testAppDir.getName(), target));
        listener.testStarted(testId);
        try {
            runTestAppTest(target, testAppDir, isLibrary);
        } catch (Throwable t) {
            CLog.w("%s failed.", testId);
            CLog.e(t);
            listener.testFailed(testId, getThrowableTraceAsString(t));
        }
        listener.testEnded(testId, Collections.emptyMap());
    }

    private void runTestAppTest(String target, File testAppDir, boolean isLibrary) {
         updateMinSdkVersion(target, testAppDir);
         File buildFile = updateProject(target, testAppDir, isLibrary);
         doAntBuild(testAppDir.getName(), buildFile, isLibrary);

         // TODO: deploy to emulator and verify success
    }

    private void updateMinSdkVersion(String target, File testAppDir) {
        // if target is a preview, test apps will fail to build unless their manifest declares
        // minSdkVersion = target
        // always update minSdkVersion == target for simplicity
        String api = parseApiFromTarget(target);
        CLog.i("Updating minSdkVersion to %s for %s", api, testAppDir.getName());
        File manifestFile = new File(testAppDir, "AndroidManifest.xml");
        Assert.assertTrue(String.format("Could not find AndroidManifest.xml file for %s",
                testAppDir.getName()), manifestFile.exists());
        AndroidManifestWriter manifestWriter = AndroidManifestWriter.parse(
                manifestFile.getAbsolutePath());
        Assert.assertNotNull(String.format("Could not parse AndroidManifest.xml file for %s",
                testAppDir.getName()), manifestWriter);
        manifestWriter.setMinSdkVersion(api);
    }

    /**
     * Parse out the API level from the SDK target string.
     * <p/>
     * Assumes SDK target is in form: 'android-[API level]'
     *
     * @param target the SDK target
     * @return the API level
     */
    private String parseApiFromTarget(String target) {
        int sepIndex = target.lastIndexOf('-');
        Assert.assertTrue(String.format("Could not determine API level from SDK target %s",
                target), sepIndex != -1);
        return target.substring(sepIndex+1, target.length());
    }

    /**
     * Calls 'android update project' on given test app.
     *
     * @param target the sdk target
     * @param testAppDir the test app directory
     * @param isLibrary <code>true</code> if app is a library project
     * @throws AssertionError if update project failed
     * @return a {@link File} representing the app's build.xml
     */
    private File updateProject(String target, File testAppDir, boolean isLibrary) {
        CLog.i("Updating project for %s", testAppDir.getName());
        String projectCmd = isLibrary ? "lib-project" : "project";
        CommandResult result = getRunUtil().runTimedCmd(15 * 1000, mSdkBuild.getAndroidToolPath(),
                "update", projectCmd, "--path", testAppDir.getAbsolutePath(), "--target", target);
        Assert.assertEquals(String.format("update project for %s failed. stderr: %s",
                testAppDir.getName(), result.getStderr()), CommandStatus.SUCCESS,
                result.getStatus());
        File buildFile = new File(testAppDir, "build.xml");
        Assert.assertTrue(String.format("%s was not generated", buildFile.getAbsolutePath()),
                buildFile.exists());
        return buildFile;
    }

    /**
     * Build the test app using ant, and verify success
     *
     * @param appName the name of the test app. Used for logging
     * @param buildFile
     */
    private void doAntBuild(String appName, File buildFile, boolean isLibrary) {
        String antTarget = isLibrary ? "compile" : "debug";
        CLog.i("Doing 'ant %s' for %s", antTarget, appName);
        CommandResult result = getRunUtil().runTimedCmd(30 * 1000, "ant", "-f",
                buildFile.getAbsolutePath(), antTarget);
        CLog.d("ant output:\n%s", result.getStdout());
        // check that the command returned successful, and that 'BUILD SUCCESSFUL' appeared in
        // output
        Assert.assertEquals(String.format("ant %s for %s failed. stderr: %s", antTarget,
                appName, result.getStderr()), CommandStatus.SUCCESS,
                result.getStatus());
        Assert.assertTrue(String.format("ant %s for %s failed. stderr: %s", antTarget,
                appName, result.getStderr()),
                result.getStdout().contains("BUILD SUCCESSFUL"));
    }

    /**
     * Gets the {@link IRunUtil} instance to use.
     * <p/>
     * Exposed for mocking
     */
    IRunUtil getRunUtil() {
        return RunUtil.getDefault();
    }

    /**
     * Gets a {@link Throwable}'s stack trace as a {@link String}
     *
     * @param t the {@link Throwable}
     * @return the stack trace in {@link String} form
     */
    String getThrowableTraceAsString(Throwable t) {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        PrintWriter printWriter = new PrintWriter(byteStream, true);
        t.printStackTrace(printWriter);
        printWriter.close();
        return new String(byteStream.toByteArray());
    }
}
