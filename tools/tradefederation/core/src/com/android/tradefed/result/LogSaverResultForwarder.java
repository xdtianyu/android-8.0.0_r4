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

package com.android.tradefed.result;

import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.log.LogUtil.CLog;

import java.io.IOException;
import java.util.List;

/**
 * A {@link ResultForwarder} for saving logs with the global file saver.
 */
public class LogSaverResultForwarder extends ResultForwarder {

    ILogSaver mLogSaver;

    public LogSaverResultForwarder(ILogSaver logSaver,
            List<ITestInvocationListener> listeners) {
        super(listeners);
        mLogSaver = logSaver;
        for (ITestInvocationListener listener : listeners) {
            if (listener instanceof ILogSaverListener) {
                ((ILogSaverListener) listener).setLogSaver(mLogSaver);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invocationStarted(IInvocationContext context) {
        // Intentionally call invocationStarted for the log saver first.
        mLogSaver.invocationStarted(context);
        for (ITestInvocationListener listener : getListeners()) {
            try {
                listener.invocationStarted(context);
            } catch (RuntimeException e) {
                // don't let the listener leave the invocation in a bad state
                CLog.e("Caught runtime exception from ITestInvocationListener");
                CLog.e(e);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invocationEnded(long elapsedTime) {
        InvocationSummaryHelper.reportInvocationEnded(getListeners(), elapsedTime);
        // Intentionally call invocationEnded for the log saver last.
        mLogSaver.invocationEnded(elapsedTime);
    }

    /**
     * {@inheritDoc}
     * <p/>
     * Also, save the log file with the global {@link ILogSaver} and call
     * {@link ILogSaverListener#testLogSaved(String, LogDataType, InputStreamSource, LogFile)}
     * for those listeners implementing the {@link ILogSaverListener} interface.
     */
    @Override
    public void testLog(String dataName, LogDataType dataType, InputStreamSource dataStream) {
        super.testLog(dataName, dataType, dataStream);
        try {
            LogFile logFile = mLogSaver.saveLogData(dataName, dataType,
                    dataStream.createInputStream());
            for (ITestInvocationListener listener : getListeners()) {
                if (listener instanceof ILogSaverListener) {
                    ((ILogSaverListener) listener).testLogSaved(dataName, dataType,
                            dataStream, logFile);
                }
            }
        } catch (IOException e) {
            CLog.e("Failed to save log data");
            CLog.e(e);
        }
    }
}