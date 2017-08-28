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

import com.android.tradefed.result.LogDataType;

/** The output formats supported by the jack code coverage report tool. */
enum JackCodeCoverageReportFormat implements CodeCoverageReportFormat {
    CSV("csv", LogDataType.JACOCO_CSV),
    XML("xml", LogDataType.JACOCO_XML),
    HTML("html", LogDataType.HTML);

    private String mReporterArg;
    private LogDataType mLogDataType;

    /**
     * Initialize a JackCodeCoverageReportFormat.
     *
     * @param reporterArg The command line argument to pass the report generation tool.
     * @param logDataType The {@link LogDataType} of the generated report.
     */
    private JackCodeCoverageReportFormat(String reporterArg, LogDataType logDataType) {
        mReporterArg = reporterArg;
        mLogDataType = logDataType;
    }

    /** Returns the command line argument used to configure the report generation tool. */
    public String getReporterArg() { return mReporterArg; }

    /**
     * {@inheritDoc}
     */
    @Override
    public LogDataType getLogDataType() { return mLogDataType; }
}
