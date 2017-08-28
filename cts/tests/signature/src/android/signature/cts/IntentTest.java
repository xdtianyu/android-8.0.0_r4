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
package android.signature.cts;

import static android.signature.cts.CurrentApi.CURRENT_API_FILE;
import static android.signature.cts.CurrentApi.SYSTEM_CURRENT_API_FILE;
import static android.signature.cts.CurrentApi.TAG_ROOT;
import static android.signature.cts.CurrentApi.TAG_PACKAGE;
import static android.signature.cts.CurrentApi.TAG_CLASS;
import static android.signature.cts.CurrentApi.TAG_INTERFACE;
import static android.signature.cts.CurrentApi.TAG_IMPLEMENTS;
import static android.signature.cts.CurrentApi.TAG_CONSTRUCTOR;
import static android.signature.cts.CurrentApi.TAG_METHOD;
import static android.signature.cts.CurrentApi.TAG_PARAM;
import static android.signature.cts.CurrentApi.TAG_EXCEPTION;
import static android.signature.cts.CurrentApi.TAG_FIELD;

import static android.signature.cts.CurrentApi.MODIFIER_ABSTRACT;
import static android.signature.cts.CurrentApi.MODIFIER_FINAL;
import static android.signature.cts.CurrentApi.MODIFIER_NATIVE;
import static android.signature.cts.CurrentApi.MODIFIER_PRIVATE;
import static android.signature.cts.CurrentApi.MODIFIER_PROTECTED;
import static android.signature.cts.CurrentApi.MODIFIER_PUBLIC;
import static android.signature.cts.CurrentApi.MODIFIER_STATIC;
import static android.signature.cts.CurrentApi.MODIFIER_SYNCHRONIZED;
import static android.signature.cts.CurrentApi.MODIFIER_TRANSIENT;
import static android.signature.cts.CurrentApi.MODIFIER_VOLATILE;
import static android.signature.cts.CurrentApi.MODIFIER_VISIBILITY;

import static android.signature.cts.CurrentApi.ATTRIBUTE_NAME;
import static android.signature.cts.CurrentApi.ATTRIBUTE_EXTENDS;
import static android.signature.cts.CurrentApi.ATTRIBUTE_TYPE;
import static android.signature.cts.CurrentApi.ATTRIBUTE_RETURN;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.signature.cts.JDiffClassDescription.JDiffConstructor;
import android.signature.cts.JDiffClassDescription.JDiffField;
import android.signature.cts.JDiffClassDescription.JDiffMethod;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import com.android.compatibility.common.util.DynamicConfigDeviceSide;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.File;
import java.io.FileInputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Assert;
import org.junit.runner.RunWith;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

/**
 * Validate that the android intents used by APKs on this device are part of the
 * platform.
 */
@RunWith(AndroidJUnit4.class)
public class IntentTest {
    private static final String TAG = IntentTest.class.getSimpleName();

    private static final File SIGNATURE_TEST_PACKGES =
            new File("/data/local/tmp/signature-test-packages");
    private static final String ANDROID_INTENT_PREFIX = "android.intent.action";
    private static final String ACTION_LINE_PREFIX = "          Action: ";
    private static final String MODULE_NAME = "CtsSignatureTestCases";

    private PackageManager mPackageManager;
    private Set<String> intentWhitelist;

    @Before
    public void setupPackageManager() throws Exception {
      mPackageManager = InstrumentationRegistry.getContext().getPackageManager();
      intentWhitelist = getIntentWhitelist();
    }

    @Test
    public void shouldNotFindUnexpectedIntents() throws Exception {
        Set<String> platformIntents = lookupPlatformIntents();
        platformIntents.addAll(intentWhitelist);

        Set<String> allInvalidIntents = new HashSet<>();

        Set<String> errors = new HashSet<>();
        List<ApplicationInfo> packages =
            mPackageManager.getInstalledApplications(PackageManager.GET_META_DATA);
        for (ApplicationInfo appInfo : packages) {
            if (!isSystemApp(appInfo) && !isUpdatedSystemApp(appInfo)) {
                // Only examine system apps
                continue;
            }
            Set<String> invalidIntents = new HashSet<>();
            Set<String> activeIntents = lookupActiveIntents(appInfo.packageName);

            for (String activeIntent : activeIntents) {
              String intent = activeIntent.trim();
              if (!platformIntents.contains(intent) &&
                    intent.startsWith(ANDROID_INTENT_PREFIX)) {
                  invalidIntents.add(activeIntent);
                  allInvalidIntents.add(activeIntent);
              }
            }

            String error = String.format("Package: %s Invalid Intent: %s",
                  appInfo.packageName, invalidIntents);
            if (!invalidIntents.isEmpty()) {
                errors.add(error);
            }
        }

        // Log the whitelist line to make it easy to update.
        for (String intent : allInvalidIntents) {
           Log.d(TAG, String.format("whitelist.add(\"%s\");", intent));
        }

        Assert.assertTrue(errors.toString(), errors.isEmpty());
    }

    private Set<String> lookupPlatformIntents() {
        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();

            Set<String> intents = new HashSet<>();
            XmlPullParser parser = factory.newPullParser();
            parser.setInput(new FileInputStream(new File(CURRENT_API_FILE)), null);
            intents.addAll(parse(parser));

            parser = factory.newPullParser();
            parser.setInput(new FileInputStream(new File(SYSTEM_CURRENT_API_FILE)), null);
            intents.addAll(parse(parser));
            return intents;
        } catch (XmlPullParserException | IOException e) {
            throw new RuntimeException("failed to parse", e);
        }
    }

    private static Set<String> parse(XmlPullParser parser)
            throws XmlPullParserException, IOException {
        JDiffClassDescription currentClass = null;
        String currentPackage = "";
        JDiffMethod currentMethod = null;

        Set<String> androidIntents = new HashSet<>();
        Set<String> keyTagSet = new HashSet<String>();
        keyTagSet.addAll(Arrays.asList(new String[] {
                TAG_PACKAGE, TAG_CLASS, TAG_INTERFACE, TAG_IMPLEMENTS, TAG_CONSTRUCTOR,
                TAG_METHOD, TAG_PARAM, TAG_EXCEPTION, TAG_FIELD }));

        int type;
        while ((type=parser.next()) != XmlPullParser.START_TAG
                   && type != XmlPullParser.END_DOCUMENT) { }

        if (type != XmlPullParser.START_TAG) {
            throw new XmlPullParserException("No start tag found");
        }

        if (!parser.getName().equals(TAG_ROOT)) {
            throw new XmlPullParserException(
                  "Unexpected start tag: found " + parser.getName() + ", expected " + TAG_ROOT);
        }

        while (true) {
            type = XmlPullParser.START_DOCUMENT;
            while ((type=parser.next()) != XmlPullParser.START_TAG
                       && type != XmlPullParser.END_DOCUMENT
                       && type != XmlPullParser.END_TAG) {
            }

            if (type == XmlPullParser.END_TAG) {
                if (TAG_PACKAGE.equals(parser.getName())) {
                    currentPackage = "";
                }
                continue;
            }

            if (type == XmlPullParser.END_DOCUMENT) {
                break;
            }

            String tagname = parser.getName();
            if (!keyTagSet.contains(tagname)) {
                continue;
            }

            if (type == XmlPullParser.START_TAG && tagname.equals(TAG_PACKAGE)) {
                currentPackage = parser.getAttributeValue(null, ATTRIBUTE_NAME);
            } else if (tagname.equals(TAG_CLASS)) {
                currentClass = CurrentApi.loadClassInfo(
                      parser, false, currentPackage, null /*resultObserver*/);
            } else if (tagname.equals(TAG_INTERFACE)) {
                currentClass = CurrentApi.loadClassInfo(
                      parser, true, currentPackage, null /*resultObserver*/);
            } else if (tagname.equals(TAG_FIELD)) {
                JDiffField field =
                    CurrentApi.loadFieldInfo(currentClass.getClassName(), parser);
                currentClass.addField(field);
            }

            if (currentClass != null) {
                for (JDiffField diffField : currentClass.getFieldList()) {
                    String fieldValue = diffField.getValueString();
                    if (fieldValue != null) {
                        fieldValue = fieldValue.replace("\"", "");
                        if (fieldValue.startsWith(ANDROID_INTENT_PREFIX)) {
                            androidIntents.add(fieldValue);
                        }
                    }
                }
            }
        }

        return androidIntents;
    }

    private static boolean isSystemApp(ApplicationInfo applicationInfo) {
        return (applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
    }

    private static boolean isUpdatedSystemApp(ApplicationInfo applicationInfo) {
        return (applicationInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
    }

    private static Set<String> lookupActiveIntents(String packageName) {
        HashSet<String> activeIntents = new HashSet<>();
        File dumpsysPackage = new File(SIGNATURE_TEST_PACKGES, packageName + ".txt");
        if (!dumpsysPackage.exists() || dumpsysPackage.length() == 0) {
          throw new RuntimeException("Missing package info: " + dumpsysPackage.getAbsolutePath());
        }
        try (
            BufferedReader in = new BufferedReader(
                  new InputStreamReader(new FileInputStream(dumpsysPackage)))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.startsWith(ACTION_LINE_PREFIX)) {
                    String intent = line.substring(
                          ACTION_LINE_PREFIX.length(), line.length() - 1);
                    activeIntents.add(intent.replace("\"", ""));
                }
            }
            return activeIntents;
        } catch (Exception e) {
          throw new RuntimeException("While retrieving dumpsys", e);
        }
    }

    private static Set<String> getIntentWhitelist() throws Exception {
        Set<String> whitelist = new HashSet<>();

        DynamicConfigDeviceSide dcds = new DynamicConfigDeviceSide(MODULE_NAME);
        List<String> intentWhitelist = dcds.getValues("intent_whitelist");

        // Log the whitelist Intent
        for (String intent : intentWhitelist) {
           Log.d(TAG, String.format("whitelist add: %s", intent));
           whitelist.add(intent);
        }

        return whitelist;
    }
}
