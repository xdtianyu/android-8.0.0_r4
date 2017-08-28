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
package com.android.tradefed.build;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.tradefed.util.FileUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;

/** Unit tests for {@link DeviceBuildInfo}. */
@RunWith(JUnit4.class)
public class DeviceBuildInfoTest {

    private static final String VERSION = "1";
    private DeviceBuildInfo mBuildInfo;
    private File mImageFile;

    @Before
    public void setUp() throws Exception {
        mBuildInfo = new DeviceBuildInfo("2", "target");
        mImageFile = FileUtil.createTempFile("image", "tmp");
        FileUtil.writeToFile("filedata", mImageFile);
        mBuildInfo.setBasebandImage(mImageFile, VERSION);
    }

    @After
    public void tearDown() throws Exception {
        FileUtil.deleteFile(mImageFile);
    }

    /** Test method for {@link DeviceBuildInfo#clone()}. */
    @Test
    public void testClone() throws Exception {
        DeviceBuildInfo copy = (DeviceBuildInfo)mBuildInfo.clone();
        try {
            // ensure a copy of mImageFile was created
            assertEquals(VERSION, copy.getBasebandVersion());
            assertTrue(
                    !mImageFile
                            .getAbsolutePath()
                            .equals(copy.getBasebandImageFile().getAbsolutePath()));
            assertTrue(FileUtil.compareFileContents(mImageFile, copy.getBasebandImageFile()));
        } finally {
            if (copy.getBasebandImageFile() != null) {
                copy.getBasebandImageFile().delete();
            }
        }
    }

    /** Test method for {@link DeviceBuildInfo#cleanUp()}. */
    @Test
    public void testCleanUp() {
        assertTrue(mBuildInfo.getBasebandImageFile().exists());
        mBuildInfo.cleanUp();
        assertNull(mBuildInfo.getBasebandImageFile());
        assertFalse(mImageFile.exists());
    }
}
