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

package com.android.tradefed.targetprep;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.StreamUtil;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.NoSuchElementException;
import java.util.TreeSet;

/**
 * Sets up a Python virtualenv on the host and installs packages. To activate it, the working
 * directory is changed to the root of the virtualenv.
 *
 * This's a fork of PythonVirtualenvPreparer and is forked in order to simplify the change
 * deployment process and reduce the deployment time, which are critical for VTS services.
 * That means changes here will be upstreamed gradually.
 */
@OptionClass(alias = "python-venv")
public class VtsPythonVirtualenvPreparer implements ITargetPreparer, ITargetCleaner {

    private static final String PIP = "pip";
    private static final String PATH = "PATH";
    private static final String OS_NAME = "os.name";
    private static final String WINDOWS = "Windows";
    private static final String LOCAL_PYPI_PATH_ENV_VAR_NAME = "VTS_PYPI_PATH";
    private static final String VENDOR_TEST_CONFIG_FILE_PATH =
            "/config/google-tradefed-vts-config.config";
    private static final String LOCAL_PYPI_PATH_KEY = "pypi_packages_path";
    protected static final String PYTHONPATH = "PYTHONPATH";
    protected static final String VIRTUAL_ENV_PATH = "VIRTUALENVPATH";
    private static final int BASE_TIMEOUT = 1000 * 60;
    private static final String[] DEFAULT_DEP_MODULES = {"enum", "future", "futures",
            "google-api-python-client", "httplib2", "oauth2client", "protobuf", "requests"};

    @Option(name = "venv-dir", description = "path of an existing virtualenv to use")
    private File mVenvDir = null;

    @Option(name = "requirements-file", description = "pip-formatted requirements file")
    private File mRequirementsFile = null;

    @Option(name = "script-file", description = "scripts which need to be executed in advance")
    private Collection<String> mScriptFiles = new TreeSet<>();

    @Option(name = "dep-module", description = "modules which need to be installed by pip")
    private Collection<String> mDepModules = new TreeSet<>(Arrays.asList(DEFAULT_DEP_MODULES));

    IRunUtil mRunUtil = new RunUtil();
    String mPip = PIP;
    String mLocalPypiPath = null;

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo)
            throws TargetSetupError, BuildError, DeviceNotAvailableException {
        startVirtualenv(buildInfo);
        setLocalPypiPath();
        installDeps(buildInfo);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void tearDown(ITestDevice device, IBuildInfo buildInfo, Throwable e)
            throws DeviceNotAvailableException {
        if (mVenvDir != null) {
            FileUtil.recursiveDelete(mVenvDir);
            CLog.i("Deleted the virtual env's temp working dir, %s.", mVenvDir);
            mVenvDir = null;
        }
    }

    /**
     * This method sets mLocalPypiPath, the local PyPI package directory to
     * install python packages from in the installDeps method.
     *
     * @throws IOException
     * @throws JSONException
     */
    protected void setLocalPypiPath() throws RuntimeException {
        CLog.i("Loading vendor test config %s", VENDOR_TEST_CONFIG_FILE_PATH);
        InputStream config = getClass().getResourceAsStream(VENDOR_TEST_CONFIG_FILE_PATH);

        // First try to load local PyPI directory path from vendor config file
        if (config != null) {
            try {
                String content = StreamUtil.getStringFromStream(config);
                CLog.i("Loaded vendor test config %s", content);
                if (content != null) {
                    JSONObject vendorConfigJson = new JSONObject(content);
                    try {
                        String pypiPath = vendorConfigJson.getString(LOCAL_PYPI_PATH_KEY);
                        if (pypiPath.length() > 0 && dirExistsAndHaveReadAccess(pypiPath)) {
                            mLocalPypiPath = pypiPath;
                            CLog.i(String.format(
                                    "Loaded %s: %s", LOCAL_PYPI_PATH_KEY, mLocalPypiPath));
                        }
                    } catch (NoSuchElementException e) {
                        CLog.i("Vendor test config file does not define %s", LOCAL_PYPI_PATH_KEY);
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to read vendor config json file");
            } catch (JSONException e) {
                throw new RuntimeException("Failed to parse vendor config json data");
            }
        } else {
            CLog.i("Vendor test config file %s does not exist", VENDOR_TEST_CONFIG_FILE_PATH);
        }

        // If loading path from vendor config file is unsuccessful,
        // check local pypi path defined by LOCAL_PYPI_PATH_ENV_VAR_NAME
        if (mLocalPypiPath == null) {
            CLog.i("Checking whether local pypi packages directory exists");
            String pypiPath = System.getenv(LOCAL_PYPI_PATH_ENV_VAR_NAME);
            if (pypiPath == null) {
                CLog.i("Local pypi packages directory not specified by env var %s",
                        LOCAL_PYPI_PATH_ENV_VAR_NAME);
            } else if (dirExistsAndHaveReadAccess(pypiPath)) {
                mLocalPypiPath = pypiPath;
                CLog.i("Set local pypi packages directory to %s", pypiPath);
            }
        }

        if (mLocalPypiPath == null) {
            CLog.i("Failed to set local pypi packages path. Therefore internet connection to "
                    + "https://pypi.python.org/simple/ must be available to run VTS tests.");
        }
    }

    /**
     * This method returns whether the given path is a dir that exists and the user has read access.
     */
    private boolean dirExistsAndHaveReadAccess(String path) {
        File pathDir = new File(path);
        if (!pathDir.exists() || !pathDir.isDirectory()) {
            CLog.i("Directory %s does not exist.", pathDir);
            return false;
        }

        if (!isOnWindows()) {
            CommandResult c = mRunUtil.runTimedCmd(BASE_TIMEOUT * 5, "ls", path);
            if (c.getStatus() != CommandStatus.SUCCESS) {
                CLog.i(String.format("Failed to read dir: %s. Result %s. stdout: %s, stderr: %s",
                        path, c.getStatus(), c.getStdout(), c.getStderr()));
                return false;
            }
            return true;
        } else {
            try {
                String[] pathDirList = pathDir.list();
                if (pathDirList == null) {
                    CLog.i("Failed to read dir: %s. Please check access permission.", pathDir);
                    return false;
                }
            } catch (SecurityException e) {
                CLog.i(String.format(
                        "Failed to read dir %s with SecurityException %s", pathDir, e));
                return false;
            }
            return true;
        }
    }

    protected void installDeps(IBuildInfo buildInfo) throws TargetSetupError {
        boolean hasDependencies = false;
        if (!mScriptFiles.isEmpty()) {
            for (String scriptFile : mScriptFiles) {
                CLog.i("Attempting to execute a script, %s", scriptFile);
                CommandResult c = mRunUtil.runTimedCmd(BASE_TIMEOUT * 5, scriptFile);
                if (c.getStatus() != CommandStatus.SUCCESS) {
                    CLog.e("Executing script %s failed", scriptFile);
                    throw new TargetSetupError("Failed to source a script");
                }
            }
        }
        if (mRequirementsFile != null) {
            CommandResult c = mRunUtil.runTimedCmd(BASE_TIMEOUT * 5, mPip,
                    "install", "-r", mRequirementsFile.getAbsolutePath());
            if (c.getStatus() != CommandStatus.SUCCESS) {
                CLog.e("Installing dependencies from %s failed",
                        mRequirementsFile.getAbsolutePath());
                throw new TargetSetupError("Failed to install dependencies with pip");
            }
            hasDependencies = true;
        }
        if (!mDepModules.isEmpty()) {
            for (String dep : mDepModules) {
                CommandResult result = null;
                if (mLocalPypiPath != null) {
                    CLog.i("Attempting installation of %s from local directory", dep);
                    result = mRunUtil.runTimedCmd(BASE_TIMEOUT * 5, mPip, "install", dep,
                            "--no-index", "--find-links=" + mLocalPypiPath);
                    CLog.i(String.format("Result %s. stdout: %s, stderr: %s", result.getStatus(),
                            result.getStdout(), result.getStderr()));
                    if (result.getStatus() != CommandStatus.SUCCESS) {
                        CLog.e(String.format("Installing %s from %s failed", dep, mLocalPypiPath));
                    }
                }
                if (mLocalPypiPath == null || result.getStatus() != CommandStatus.SUCCESS) {
                    CLog.i("Attempting installation of %s from PyPI", dep);
                    result = mRunUtil.runTimedCmd(BASE_TIMEOUT * 5, mPip, "install", dep);
                    CLog.i(String.format("Result %s. stdout: %s, stderr: %s", result.getStatus(),
                            result.getStdout(), result.getStderr()));
                    if (result.getStatus() != CommandStatus.SUCCESS) {
                        CLog.e("Installing %s from PyPI failed.", dep);
                        CLog.i("Attempting to upgrade %s", dep);
                        result = mRunUtil.runTimedCmd(
                                BASE_TIMEOUT * 5, mPip, "install", "--upgrade", dep);
                        if (result.getStatus() != CommandStatus.SUCCESS) {
                            throw new TargetSetupError(String.format(
                                    "Failed to install dependencies with pip. "
                                            + "Result %s. stdout: %s, stderr: %s",
                                    result.getStatus(), result.getStdout(), result.getStderr()));
                        } else {
                            CLog.i(String.format("Result %s. stdout: %s, stderr: %s",
                                    result.getStatus(), result.getStdout(), result.getStderr()));
                        }
                    }
                }
                hasDependencies = true;
            }
        }
        if (!hasDependencies) {
            CLog.i("No dependencies to install");
        } else {
            // make the install directory of new packages available to other classes that
            // receive the build
            buildInfo.setFile(PYTHONPATH, new File(mVenvDir,
                    "local/lib/python2.7/site-packages"),
                    buildInfo.getBuildId());
        }
    }

    protected void startVirtualenv(IBuildInfo buildInfo) throws TargetSetupError {
        if (mVenvDir != null) {
            CLog.i("Using existing virtualenv based at %s", mVenvDir.getAbsolutePath());
            activate();
            return;
        }
        try {
            mVenvDir = buildInfo.getFile(VIRTUAL_ENV_PATH);
            if (mVenvDir == null) {
                mVenvDir = FileUtil.createTempDir(buildInfo.getTestTag() + "-virtualenv");
            }
            String virtualEnvPath = mVenvDir.getAbsolutePath();
            mRunUtil.runTimedCmd(BASE_TIMEOUT, "virtualenv", virtualEnvPath);
            CLog.i(VIRTUAL_ENV_PATH + " = " + virtualEnvPath + "\n");
            buildInfo.setFile(VIRTUAL_ENV_PATH, new File(virtualEnvPath),
                              buildInfo.getBuildId());
            activate();
        } catch (IOException e) {
            CLog.e("Failed to create temp directory for virtualenv");
            throw new TargetSetupError("Error creating virtualenv", e);
        }
    }

    protected void addDepModule(String module) {
        mDepModules.add(module);
    }

    protected void setRequirementsFile(File f) {
        mRequirementsFile = f;
    }

    /**
     * This method returns whether the OS is Windows.
     */
    private static boolean isOnWindows() {
        return System.getProperty(OS_NAME).contains(WINDOWS);
    }

    private void activate() {
        File binDir = new File(mVenvDir, isOnWindows() ? "Scripts" : "bin");
        mRunUtil.setWorkingDir(binDir);
        String path = System.getenv(PATH);
        mRunUtil.setEnvVariable(PATH, binDir + File.pathSeparator + path);
        File pipFile = new File(binDir, PIP);
        pipFile.setExecutable(true);
        mPip = pipFile.getAbsolutePath();
    }
}
