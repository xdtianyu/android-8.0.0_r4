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

package com.android.media.tests;

import com.android.ddmlib.Log;
import com.android.tradefed.config.Option;
import com.android.tradefed.result.EmailResultReporter;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.TestSummary;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.List;

/**
 * Media reporter that send the test summary through email.
 */
public class MediaResultReporter extends EmailResultReporter {

    private static final String LOG_TAG = "MediaResultReporter";

    public StringBuilder mEmailBodyBuilder;
    private String mSummaryUrl = "";

    @Option(name = "log-name", description = "Name of the report that attach to email")
    private String mLogName = null;

    public MediaResultReporter() {
        mEmailBodyBuilder = new StringBuilder();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String generateEmailBody() {
        return mEmailBodyBuilder.toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void putSummary(List<TestSummary> summaries) {
        // Get the summary url
        if (summaries.isEmpty()) {
           return;
        }
        mSummaryUrl = summaries.get(0).getSummary().getString();
        mEmailBodyBuilder.append(mSummaryUrl);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testLog(String dataName, LogDataType dataType, InputStreamSource dataStream) {

        char[] buf = new char[2048];

        try {
            //Attached the test summary to the email body
            if (mLogName.equals(dataName)) {
                Reader r = new InputStreamReader(dataStream.createInputStream());
                while (true) {
                    int n = r.read(buf);
                    if (n < 0) {
                        break;
                    }
                    mEmailBodyBuilder.append(buf, 0, n);
                  }
                mEmailBodyBuilder.append('\n');
            }
        } catch (IOException e) {
            Log.w(LOG_TAG, String.format("Exception while parsing %s: %s", dataName, e));
        }
    }
}
