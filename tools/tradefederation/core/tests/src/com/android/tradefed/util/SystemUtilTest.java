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
 * limitations under the License.
 */
package com.android.tradefed.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

/** Unit tests for {@link SystemUtil} */
@RunWith(JUnit4.class)
public class SystemUtilTest {

    /**
     * test {@link SystemUtil#getTestCasesDirs()} to make sure it gets directories from environment
     * variables.
     */
    @Test
    public void testGetTestCasesDirs() throws IOException {
        File targetOutDir = null;
        File hostOutDir = null;
        try {
            targetOutDir = FileUtil.createTempDir("target_out_dir");
            hostOutDir = FileUtil.createTempDir("host_out_dir");

            SystemUtil.singleton = Mockito.mock(SystemUtil.class);
            Mockito.when(SystemUtil.singleton.getEnv(SystemUtil.ENV_ANDROID_TARGET_OUT_TESTCASES))
                    .thenReturn(targetOutDir.getAbsolutePath());

            Set<File> testCasesDirs = new HashSet<File>(SystemUtil.getTestCasesDirs());
            assertTrue(testCasesDirs.contains(targetOutDir));
            assertTrue(!testCasesDirs.contains(hostOutDir));
        } finally {
            FileUtil.recursiveDelete(targetOutDir);
            FileUtil.recursiveDelete(hostOutDir);
        }
    }

    /**
     * test {@link SystemUtil#getTestCasesDirs()} to make sure no exception thrown if no environment
     * variable is set or the directory does not exist.
     */
    @Test
    public void testGetTestCasesDirsNoDir() {
        File targetOutDir = new File("/path/not/exist_1");

        SystemUtil.singleton = Mockito.mock(SystemUtil.class);
        Mockito.when(SystemUtil.singleton.getEnv(SystemUtil.ENV_ANDROID_TARGET_OUT_TESTCASES))
                .thenReturn(targetOutDir.getAbsolutePath());

        Set<File> testCasesDirs = new HashSet<File>(SystemUtil.getTestCasesDirs());
        assertEquals(testCasesDirs.size(), 0);
    }
}
