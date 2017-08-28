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

package com.android.tradefed.util;

import com.android.loganalysis.item.LogcatItem;
import com.android.loganalysis.item.MiscLogcatItem;
import com.android.loganalysis.parser.LogcatParser;
import com.android.tradefed.device.ILogcatReceiver;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Parse logcat input for system updater related events.
 *
 * In any system with A/B updates, the updater will log its progress to logcat. This class
 * interprets updater-related logcat messages and can inform listeners of events in both
 * a blocking and non-blocking fashion.
 */
public class LogcatUpdaterEventParser {

    private static final String ERR_REGEX = "onPayloadApplicationComplete\\(ErrorCode\\.*$";
    private Map<UpdaterEventTrigger, UpdaterEventType> mEventTriggerMap;
    private ILogcatReceiver mLogcatReceiver;
    private LogcatParser mInternalParser;
    private BufferedReader mStreamReader = null;

    private class UpdaterEventTrigger {
        public String mTag;
        public String mMsg;

        public UpdaterEventTrigger(String tag, String msg) {
            mTag = tag;
            mMsg = msg;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof UpdaterEventTrigger)) {
                return false;
            }
            UpdaterEventTrigger u = (UpdaterEventTrigger) other;
            // Allow either this.mMsg or u.mMsg to be a substring, since we can't rely on precisely
            // matching log lines all the time
            return mTag.equals(u.mTag) && (u.mMsg.contains(mMsg) || mMsg.contains(u.mMsg));
        }

        /*
         * Lines received from logcat will not always exactly match triggers, since the messages
         * may contain things like execution times, context-dependent variable values etc. This
         * implementation of hashCode will look for substring matches between {@code this.mMsg} and
         * the triggers in {@code mEventTriggerMap}; if any are found, it returns a value based on
         * the matching trigger. This allows substring matches to cause a hit from
         * {@code mEventTriggerMap#get}.
         *
         * TODO: overriding hashCode with a non-constant operation could have performance
         * implications.
         *
         */
        @Override
        public int hashCode() {
            for (UpdaterEventTrigger t : mEventTriggerMap.keySet()) {
                String r = t.mMsg;
                if (mMsg.contains(r) || r.contains(mMsg)) {
                    return mTag.hashCode() + r.hashCode();
                }
            }
            return mTag.hashCode() + mMsg.hashCode();
        }

        @Override
        public String toString() {
            return "UpdaterEventTrigger[" + mTag + "," + mMsg + "]";
        }

        public boolean isUpdateEngineFailure() {
            return Pattern.matches(ERR_REGEX, mMsg);
        }
    }

    /**
     * A monitor object which allows callers to receive events asynchronously.
     */
    public class AsyncUpdaterEvent {

        private boolean mIsCompleted;
        private boolean mIsWaiting;

        public AsyncUpdaterEvent() {
            mIsCompleted = false;
            mIsWaiting = false;
        }

        public boolean isCompleted() {
            synchronized(this) {
                return mIsCompleted;
            }
        }

        public void setCompleted(boolean isCompleted) {
            synchronized(this) {
                mIsCompleted = isCompleted;
            }
        }

        public boolean isWaiting() {
            synchronized(this) {
                return mIsWaiting;
            }
        }

        public void setWaiting(boolean isWaiting) {
            synchronized(this) {
                mIsWaiting = isWaiting;
            }
        }
    }

    public LogcatUpdaterEventParser(ILogcatReceiver logcatReceiver) {
        mEventTriggerMap = new HashMap<>();
        mLogcatReceiver = logcatReceiver;
        mInternalParser = new LogcatParser();

        registerEventTrigger("update_verifier", "Leaving update_verifier.",
                UpdaterEventType.UPDATE_VERIFIER_COMPLETE);
        registerEventTrigger("update_engine", "Update successfully applied",
                UpdaterEventType.UPDATE_COMPLETE);
        // TODO: confirm that the actual log tag is dex2oat
        registerEventTrigger("dex2oat", "dex2oat took ",
                UpdaterEventType.D2O_COMPLETE);

        mStreamReader = new BufferedReader(new InputStreamReader(
                mLogcatReceiver.getLogcatData().createInputStream()));
    }

    protected void registerEventTrigger(String tag, String msg, UpdaterEventType response) {
        mEventTriggerMap.put(new UpdaterEventTrigger(tag, msg), response);
        mInternalParser.addPattern(null, "I", tag, "updaterEvents");
    }

    /**
     * Block until any event is encountered, then return.
     */
    public void waitForEvent() {
        try {
            String lastLine = mStreamReader.readLine();
            while(!mEventTriggerMap.containsValue(parseEventType(lastLine))) {
                lastLine = mStreamReader.readLine();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void waitForEvent(UpdaterEventType expectedEvent) {
        waitForEvent(expectedEvent, 0);
    }

    /**
     * Block until the specified event is encountered or the timeout is reached, then return.
     * @param expectedEvent the event to wait for
     * @param timeout the maximum time in milliseconds to wait
     */
    public UpdaterEventType waitForEvent(UpdaterEventType expectedEvent, long timeout) {
        // Parse only a single line at a time, otherwise the LogcatParser won't return until
        // the logcat stream is over
        try {
            long startTime = System.currentTimeMillis();
            while (timeout == 0 || System.currentTimeMillis() - startTime <= timeout) {
                String lastLine;
                while ((lastLine = mStreamReader.readLine()) != null) {
                    UpdaterEventType parsedEvent = parseEventType(lastLine);
                    if (parsedEvent == UpdaterEventType.ERROR ||
                            expectedEvent.equals(parsedEvent)) {
                        return parsedEvent;
                    }
                }
                // if the stream returns null, we lost our logcat. Wait for a small amount
                // of time, then try to reobtain it.
                refreshLogcatStream();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return UpdaterEventType.ERROR;
    }

    /**
     * Wait for an event but do not block.
     * @param expectedEvent the event type which will trigger a message sent to the caller
     * @return an {@link AsyncUpdaterEvent} monitor which the caller may wait on
     */
    public AsyncUpdaterEvent waitForEventAsync(final UpdaterEventType expectedEvent) {
        final AsyncUpdaterEvent event = new AsyncUpdaterEvent();
        final Thread callingThread = Thread.currentThread();

        Thread waitThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String lastLine = mStreamReader.readLine();
                    while (!expectedEvent.equals(parseEventType(lastLine))) {
                        lastLine = mStreamReader.readLine();
                    }
                } catch (IOException e) {
                    // Unblock calling thread
                    callingThread.interrupt();
                    throw new RuntimeException(e);
                } finally {
                    // ensure that the waiting thread drops out of the wait loop
                    synchronized(event) {
                        event.setCompleted(true);
                        event.notifyAll();
                    }
                }
            }
        });
        waitThread.setName(getClass().getCanonicalName());
        waitThread.start();
        return event;
    }

    protected UpdaterEventType parseEventType(String lastLine) {
        LogcatItem item = mInternalParser.parse(ArrayUtil.list(lastLine));
        if (item == null) {
            return null;
        }
        List<MiscLogcatItem> miscItems = item.getMiscEvents("updaterEvents");
        if (miscItems.size() == 0) {
            return null;
        }
        MiscLogcatItem mi = miscItems.get(0);
        UpdaterEventTrigger trigger = new UpdaterEventTrigger(mi.getTag(), mi.getStack());
        if (trigger.isUpdateEngineFailure()) {
            return UpdaterEventType.ERROR;
        }
        mInternalParser.clear();
        return mEventTriggerMap.get(trigger);
    }

    private void refreshLogcatStream() throws IOException {
        mStreamReader.close();

        mStreamReader = new BufferedReader(new InputStreamReader(
                mLogcatReceiver.getLogcatData().createInputStream()));
    }
}

