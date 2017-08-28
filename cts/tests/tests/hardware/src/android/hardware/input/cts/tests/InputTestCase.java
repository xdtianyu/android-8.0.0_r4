/*
 * Copyright 2015 The Android Open Source Project
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

package android.hardware.input.cts.tests;

import android.app.UiAutomation;
import android.hardware.input.cts.InputCtsActivity;
import android.hardware.input.cts.InputCallback;
import android.system.ErrnoException;
import android.system.Os;
import android.test.ActivityInstrumentationTestCase2;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.UUID;

public class InputTestCase extends ActivityInstrumentationTestCase2<InputCtsActivity> {
    private static final String TAG = "InputTestCase";
    private static final String HID_EXECUTABLE = "hid";
    private static final int SHELL_UID = 2000;
    private static final String[] KEY_ACTIONS = {"DOWN", "UP", "MULTIPLE"};

    private File mFifo;
    private Writer mWriter;

    private BlockingQueue<KeyEvent> mKeys;
    private BlockingQueue<MotionEvent> mMotions;
    private InputListener mInputListener;

    public InputTestCase() {
        super(InputCtsActivity.class);
        mKeys = new LinkedBlockingQueue<KeyEvent>();
        mMotions = new LinkedBlockingQueue<MotionEvent>();
        mInputListener = new InputListener();
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mFifo = setupFifo();
        clearKeys();
        clearMotions();
        getActivity().setInputCallback(mInputListener);
    }

    @Override
    protected void tearDown() throws Exception {
        if (mFifo != null) {
            mFifo.delete();
            mFifo = null;
        }
        closeQuietly(mWriter);
        mWriter = null;
        super.tearDown();
    }

    /**
     * Sends the HID commands designated by the given resource id.
     * The commands must be in the format expected by the `hid` shell command.
     *
     * @param id The resource id from which to load the HID commands. This must be a "raw"
     * resource.
     */
    public void sendHidCommands(int id) {
        try {
            Writer w = getWriter();
            w.write(getEvents(id));
            w.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Asserts that the application received a {@link android.view.KeyEvent} with the given action
     * and keycode.
     *
     * If other KeyEvents are received by the application prior to the expected KeyEvent, or no
     * KeyEvents are received within a reasonable amount of time, then this will throw an
     * AssertionFailedError.
     *
     * @param action The action to expect on the next KeyEvent
     * (e.g. {@link android.view.KeyEvent#ACTION_DOWN}).
     * @param keyCode The expected key code of the next KeyEvent.
     */
    public void assertReceivedKeyEvent(int action, int keyCode) {
        KeyEvent k = waitForKey();
        if (k == null) {
            fail("Timed out waiting for " + KeyEvent.keyCodeToString(keyCode)
                    + " with action " + KEY_ACTIONS[action]);
            return;
        }
        assertEquals(action, k.getAction());
        assertEquals(keyCode, k.getKeyCode());
    }

    /**
     * Asserts that no more events have been received by the application.
     *
     * If any more events have been received by the application, this throws an
     * AssertionFailedError.
     */
    public void assertNoMoreEvents() {
        KeyEvent key;
        MotionEvent motion;
        if ((key = mKeys.poll()) != null) {
            fail("Extraneous key events generated: " + key);
        }
        if ((motion = mMotions.poll()) != null) {
            fail("Extraneous motion events generated: " + motion);
        }
    }

    private KeyEvent waitForKey() {
        try {
            return mKeys.poll(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            return null;
        }
    }

    private void clearKeys() {
        mKeys.clear();
    }

    private void clearMotions() {
        mMotions.clear();
    }

    private File setupFifo() throws ErrnoException {
        File dir = getActivity().getCacheDir();
        String filename = dir.getAbsolutePath() + File.separator +  UUID.randomUUID().toString();
        Os.mkfifo(filename, 0666);
        File f = new File(filename);
        return f;
    }

    private Writer getWriter() throws IOException {
        if (mWriter == null) {
            UiAutomation ui = getInstrumentation().getUiAutomation();
            ui.executeShellCommand("hid " + mFifo.getAbsolutePath());
            mWriter = new FileWriter(mFifo);
        }
        return mWriter;
    }

    private String getEvents(int id) throws IOException {
        InputStream is =
            getInstrumentation().getTargetContext().getResources().openRawResource(id);
        return readFully(is);
    }


    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (RuntimeException rethrown) {
                throw rethrown;
            } catch (Exception ignored) { }
        }
    }

    private static String readFully(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int read = 0;
        byte[] buffer = new byte[1024];
        while ((read = is.read(buffer)) >= 0) {
            baos.write(buffer, 0, read);
        }
        return baos.toString();
    }

    private class InputListener implements InputCallback {
        public void onKeyEvent(KeyEvent ev) {
            boolean done = false;
            do {
                try {
                    mKeys.put(new KeyEvent(ev));
                    done = true;
                } catch (InterruptedException ignore) { }
            } while (!done);
        }

        public void onMotionEvent(MotionEvent ev) {
            boolean done = false;
            do {
                try {
                    mMotions.put(MotionEvent.obtain(ev));
                    done = true;
                } catch (InterruptedException ignore) { }
            } while (!done);
        }
    }
}
