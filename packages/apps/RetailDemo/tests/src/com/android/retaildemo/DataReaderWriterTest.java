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

package com.android.retaildemo;

import android.content.Context;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import java.io.File;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static android.support.test.InstrumentationRegistry.getTargetContext;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class DataReaderWriterTest {

    private @Mock Context mContext;

    private File mDataFile;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        when(mContext.getObbDir()).thenReturn(getTargetContext().getCacheDir());
        mDataFile = new File(DataReaderWriter.getFilePath(mContext));
        deleteDataFile();
    }

    @After
    public void tearDown() {
        deleteDataFile();
    }

    @Test
    public void testWriteReadLastBootCount() {
        final int testBootCount = 11;
        DataReaderWriter.writeLastBootCount(mContext, testBootCount);
        assertEquals(testBootCount, DataReaderWriter.readLastBootCount(mContext));
    }

    @Test
    public void testWriteReadLastBootCount_fileAlreadyExists() throws Exception {
        mDataFile.createNewFile();

        final int testBootCount = 11;
        DataReaderWriter.writeLastBootCount(mContext, testBootCount);
        assertEquals(testBootCount, DataReaderWriter.readLastBootCount(mContext));
    }

    @Test
    public void testReadLastBootCount_firstTime() {
        assertEquals(0, DataReaderWriter.readLastBootCount(mContext));
    }

    @Test
    public void testReadLastBootCount_error() throws Exception {
        mDataFile.createNewFile();
        assertEquals(-1, DataReaderWriter.readLastBootCount(mContext));
    }

    private void deleteDataFile() {
        if (mDataFile.exists()) {
            mDataFile.delete();
        }
    }
}