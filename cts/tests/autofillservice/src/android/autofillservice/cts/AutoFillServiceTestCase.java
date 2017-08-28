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

package android.autofillservice.cts;

import static android.autofillservice.cts.Helper.getLoggingLevel;
import static android.autofillservice.cts.Helper.hasAutofillFeature;
import static android.autofillservice.cts.Helper.runShellCommand;
import static android.autofillservice.cts.Helper.setLoggingLevel;
import static android.provider.Settings.Secure.AUTOFILL_SERVICE;

import static com.google.common.truth.Truth.assertWithMessage;

import android.autofillservice.cts.InstrumentedAutoFillService.Replier;
import android.content.Context;
import android.content.pm.PackageManager;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;
import android.widget.RemoteViews;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.runner.RunWith;

/**
 * Base class for all other tests.
 */
@RunWith(AndroidJUnit4.class)
abstract class AutoFillServiceTestCase {
    private static final String TAG = "AutoFillServiceTestCase";

    private static final String SERVICE_NAME =
            InstrumentedAutoFillService.class.getPackage().getName()
            + "/." + InstrumentedAutoFillService.class.getSimpleName();

    protected static UiBot sUiBot;

    protected static final Replier sReplier = InstrumentedAutoFillService.getReplier();

    @Rule
    public final RetryRule mRetryRule = new RetryRule(2);

    @Rule
    public final RequiredFeatureRule mRequiredFeatureRule =
            new RequiredFeatureRule(PackageManager.FEATURE_AUTOFILL);

    /**
     * Stores the previous logging level so it's restored after the test.
     */
    private String mLoggingLevel;

    @BeforeClass
    public static void removeLockScreen() {
        if (!hasAutofillFeature()) return;

        runShellCommand("input keyevent KEYCODE_WAKEUP");
    }

    @BeforeClass
    public static void setUiBot() throws Exception {
        if (!hasAutofillFeature()) return;

        sUiBot = new UiBot(InstrumentationRegistry.getInstrumentation());
    }

    @AfterClass
    public static void resetSettings() {
        runShellCommand("settings delete secure %s", AUTOFILL_SERVICE);
    }

    @Before
    public void disableService() {
        if (!hasAutofillFeature()) return;

        if (!isServiceEnabled()) return;

        final OneTimeSettingsListener observer = new OneTimeSettingsListener(getContext(),
                AUTOFILL_SERVICE);
        runShellCommand("settings delete secure %s", AUTOFILL_SERVICE);
        observer.assertCalled();
        assertServiceDisabled();

        InstrumentedAutoFillService.setIgnoreUnexpectedRequests(false);
    }

    @Before
    public void reset() {
        destroyAllSessions();
        sReplier.reset();
        InstrumentedAutoFillService.resetStaticState();
        AuthenticationActivity.resetStaticState();
    }

    @Before
    public void setVerboseLogging() {
        try {
            mLoggingLevel = getLoggingLevel();
        } catch (Exception e) {
            Log.w(TAG, "Could not get previous logging level: " + e);
            mLoggingLevel = "debug";
        }
        try {
            setLoggingLevel("verbose");
        } catch (Exception e) {
            Log.w(TAG, "Could not change logging level to verbose: " + e);
        }
    }

    @After
    public void resetVerboseLogging() {
        try {
            setLoggingLevel(mLoggingLevel);
        } catch (Exception e) {
            Log.w(TAG, "Could not restore logging level to " + mLoggingLevel + ": " + e);
        }
    }

    // TODO: we shouldn't throw exceptions on @After / @AfterClass because if the test failed, these
    // exceptions would mask the real cause. A better approach might be using a @Rule or some other
    // visitor pattern.
    @After
    public void assertNoPendingRequests() {
        sReplier.assertNumberUnhandledFillRequests(0);
        sReplier.assertNumberUnhandledSaveRequests(0);
    }

    @After
    public void ignoreFurtherRequests() {
        InstrumentedAutoFillService.setIgnoreUnexpectedRequests(true);
    }

    /**
     * Enables the {@link InstrumentedAutoFillService} for autofill for the current user.
     */
    protected void enableService() {
        if (isServiceEnabled()) return;

        final OneTimeSettingsListener observer = new OneTimeSettingsListener(getContext(),
                AUTOFILL_SERVICE);
        runShellCommand("settings put secure %s %s default", AUTOFILL_SERVICE, SERVICE_NAME);
        observer.assertCalled();
        assertServiceEnabled();
    }

    /**
     * Asserts that the {@link InstrumentedAutoFillService} is enabled for the default user.
     */
    protected static void assertServiceEnabled() {
        assertServiceStatus(true);
    }

    /**
     * Asserts that the {@link InstrumentedAutoFillService} is disabled for the default user.
     */
    protected static void assertServiceDisabled() {
        assertServiceStatus(false);
    }

    /**
     * Asserts that there is no session left in the service.
     */
    protected void assertNoDanglingSessions() {
        final String command = "cmd autofill list sessions";
        final String result = runShellCommand(command);
        assertWithMessage("Dangling sessions ('%s'): %s'", command, result).that(result).isEmpty();
    }

    /**
     * Destroys all sessions.
     */
    protected void destroyAllSessions() {
        runShellCommand("cmd autofill destroy sessions");
        assertNoDanglingSessions();
    }

    protected static Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getContext();
    }

    protected static RemoteViews createPresentation(String message) {
        final RemoteViews presentation = new RemoteViews(getContext()
                .getPackageName(), R.layout.list_item);
        presentation.setTextViewText(R.id.text1, message);
        return presentation;
    }

    private static boolean isServiceEnabled() {
        final String service = runShellCommand("settings get secure %s", AUTOFILL_SERVICE);
        return SERVICE_NAME.equals(service);
    }

    private static void assertServiceStatus(boolean enabled) {
        final String actual = runShellCommand("settings get secure %s", AUTOFILL_SERVICE);
        final String expected = enabled ? SERVICE_NAME : "null";
        assertWithMessage("Invalid value for secure setting %s", AUTOFILL_SERVICE)
                .that(actual).isEqualTo(expected);
    }
}
