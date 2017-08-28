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

import com.android.tradefed.device.ILogcatReceiver;
import com.android.tradefed.result.InputStreamSource;

import junit.framework.TestCase;

import org.easymock.EasyMock;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

/**
 * Unit tests for {@link LogcatUpdaterEventParser}.
 */
public class LogcatUpdaterEventParserTest extends TestCase {

    private static final long SHORT_WAIT_MS = 100L;

    private ILogcatReceiver mMockReceiver = null;
    private LogcatUpdaterEventParser mParser = null;
    private PipedOutputStream mMockPipe = null;

    @Override
    public void setUp() {
        mMockReceiver = EasyMock.createMock(ILogcatReceiver.class);
        mMockPipe = new PipedOutputStream();
        final PipedInputStream p = new PipedInputStream();
        try {
            mMockPipe.connect(p);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        EasyMock.expect(mMockReceiver.getLogcatData()).andReturn(new InputStreamSource() {
            @Override
            public InputStream createInputStream() {
                return p;
            }
            @Override
            public void cancel() {
                // ignore
            }
            @Override
            public long size() {
                return 0;
            }
        });

        EasyMock.replay(mMockReceiver);
        mParser = new LogcatUpdaterEventParser(mMockReceiver);
    }

    /**
     * Test that a known event parses to the expected {@link UpdaterEventType} when a portion
     * of the trigger contains the matched line.
     */
    public void testParseEventType_mappedEventPartialLine() {
        String mappedLogLine = "11-11 00:00:00.001  123 321 I update_engine:" +
                " Update successfully applied";
        assertEquals(UpdaterEventType.UPDATE_COMPLETE, mParser.parseEventType(mappedLogLine));
    }

    /**
     * Test that a known event parses to the expected {@link UpdaterEventType} when the
     * expected trigger contains only a portion of the matched line.
     */
    public void testParseEventType_mappedEventPartialTrigger() {
        String mappedLogLine = "11-11 00:00:00.001  123 321 I dex2oat: dex2oat took 12345 seconds";
        assertEquals(mParser.parseEventType(mappedLogLine), UpdaterEventType.D2O_COMPLETE);
    }

    /**
     * Test that a known event parses to the expected {@link UpdaterEventType} when there is
     * an exact match.
     */
    public void testParseEventType_mappedEventFull() {
        String mappedLogLine =
                "11-11 00:00:00.001  123 321 I update_verifier: Leaving update_verifier.";
        assertEquals(mParser.parseEventType(mappedLogLine),
                UpdaterEventType.UPDATE_VERIFIER_COMPLETE);
    }

    /**
     * Test that unknown events parse to null.
     */
    public void testParseEventType_unmappedEvent() {
        String unmappedLogLine = "11-11 00:00:00.001  123 321 I update_engine: foo bar baz";
        assertEquals(mParser.parseEventType(unmappedLogLine), null);
        unmappedLogLine = "11-11 00:00:00.001  123 321 I foobar_engine: Update succeeded";
        assertEquals(mParser.parseEventType(unmappedLogLine), null);
        unmappedLogLine = "11-11 00:00:00.001  123 321 I foobar_engine: foo bar baz";
        assertEquals(mParser.parseEventType(unmappedLogLine), null);
    }

    /**
     * Test that a thread exits its wait loop when it sees any event.
     */
    public void testWaitForEvent_any() {
        Thread waitThread = new Thread(new Runnable() {
            @Override
            public void run() {
                mParser.waitForEvent();
            }
        });
        waitThread.setName(getClass().getCanonicalName());
        String[] logLines = {
                "11-11 00:00:00.001  123 321 I update_engine: foo bar baz\n",
                "11-11 00:00:00.001  123 321 I update_engine: foo bar baz\n",
                "11-11 00:00:00.001  123 321 I update_engine: foo bar baz\n",
                "11-11 00:00:00.001  123 321 I update_engine: foo bar baz\n",
                "11-11 00:00:00.001  123 321 I update_engine: Update successfully applied\n"
            };
        waitAndAssertTerminated(waitThread, logLines);
    }

    /**
     * Test that a thread exits its wait loop when it sees a specific expected event.
     */
    public void testWaitForEvent_specific() {
        Thread waitThread = new Thread(new Runnable() {
            @Override
            public void run() {
                mParser.waitForEvent(UpdaterEventType.UPDATE_COMPLETE);
            }
        });
        waitThread.setName(getClass().getCanonicalName());
        String[] logLines = {
                "11-11 00:00:00.001  123 321 I update_engine: foo bar baz\n",
                "11-11 00:00:00.001  123 321 I update_engine: foo bar baz\n",
                "11-11 00:00:00.001  123 321 I update_engine: foo bar baz\n",
                "11-11 00:00:00.001  123 321 I update_engine: foo bar baz\n",
                "11-11 00:00:00.001  123 321 I update_engine: Update successfully applied\n"
            };
        waitAndAssertTerminated(waitThread, logLines);
    }

    private void waitAndAssertTerminated(Thread waitThread, String[] logLines) {
        waitThread.start();
        for (String line : logLines) {
            try {
                mMockPipe.write(line.getBytes());
            } catch (IOException e) {
                fail(e.getLocalizedMessage());
            }
        }
        // Allow short time for thread to switch state.
        RunUtil.getDefault().sleep(SHORT_WAIT_MS);
        assertEquals(Thread.State.TERMINATED, waitThread.getState());
    }
}

