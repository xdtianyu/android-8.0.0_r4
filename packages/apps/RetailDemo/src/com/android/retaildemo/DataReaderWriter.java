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
import android.support.annotation.VisibleForTesting;
import android.util.Log;
import libcore.io.IoUtils;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

class DataReaderWriter {
    private static final String TAG = "DataReaderWriter";
    private static final boolean DEBUG = false;

    private static final String FILE_NAME = "last_download_info.txt";

    public static void writeLastBootCount(Context context, int bootCount) {
        FileOutputStream fileOutputStream = null;
        DataOutputStream out = null;
        try {
            final String filePath = getFilePath(context);
            final File file = new File(filePath);
            if (!file.exists()) {
                file.createNewFile();
            }
            fileOutputStream = new FileOutputStream(filePath);
            out = new DataOutputStream(fileOutputStream);
            if (DEBUG) Log.d(TAG, "Writing value bootCount=" + bootCount);
            out.writeInt(bootCount);
        } catch (IOException e) {
            Log.e(TAG, "Error writing bootCount=" + bootCount + " to file", e);
        } finally {
            IoUtils.closeQuietly(out);
            IoUtils.closeQuietly(fileOutputStream);
        }
    }

    /**
     * @return 0 if reading the value for the first time,
     *         -1 if there is an error reading the saved count,
     *         last saved boot count otherwise.
     */
    public static int readLastBootCount(Context context) {
        int bootCount = 0;
        final String filePath = getFilePath(context);
        if (!new File(filePath).exists()) {
            return bootCount;
        }
        FileInputStream fileInputStream = null;
        DataInputStream in = null;
        try {
            fileInputStream = new FileInputStream(filePath);
            in = new DataInputStream(fileInputStream);
            bootCount = in.readInt();
            if (DEBUG) Log.d(TAG, "Read value bootCount=" + bootCount);
        } catch (IOException e) {
            Log.e(TAG, "Error reading bootCount value from file", e);
            return -1;
        } finally {
            IoUtils.closeQuietly(in);
            IoUtils.closeQuietly(fileInputStream);
        }
        return bootCount;
    }

    @VisibleForTesting
    static String getFilePath(Context context) {
        return context.getObbDir() + File.separator + FILE_NAME;
    }
}