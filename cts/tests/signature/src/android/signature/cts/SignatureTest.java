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

import android.content.res.Resources;
import android.signature.cts.JDiffClassDescription.JDiffConstructor;
import android.signature.cts.JDiffClassDescription.JDiffField;
import android.signature.cts.JDiffClassDescription.JDiffMethod;
import android.test.AndroidTestCase;
import android.util.Log;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Scanner;

/**
 * Performs the signature check via a JUnit test.
 */
public class SignatureTest extends AndroidTestCase {

    private static final String TAG = SignatureTest.class.getSimpleName();

    private HashSet<String> mKeyTagSet;
    private TestResultObserver mResultObserver;

    private class TestResultObserver implements ResultObserver {
        boolean mDidFail = false;
        StringBuilder mErrorString = new StringBuilder();

        @Override
        public void notifyFailure(FailureType type, String name, String errorMessage) {
            mDidFail = true;
            mErrorString.append("\n");
            mErrorString.append(type.toString().toLowerCase());
            mErrorString.append(":\t");
            mErrorString.append(name);
            mErrorString.append("\tError: ");
            mErrorString.append(errorMessage);
        }
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mKeyTagSet = new HashSet<String>();
        mKeyTagSet.addAll(Arrays.asList(new String[] {
                TAG_PACKAGE, TAG_CLASS, TAG_INTERFACE, TAG_IMPLEMENTS, TAG_CONSTRUCTOR,
                TAG_METHOD, TAG_PARAM, TAG_EXCEPTION, TAG_FIELD }));
        mResultObserver = new TestResultObserver();
    }

    /**
     * Tests that the device's API matches the expected set defined in xml.
     * <p/>
     * Will check the entire API, and then report the complete list of failures
     */
    public void testSignature() {
        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            XmlPullParser parser = factory.newPullParser();
            parser.setInput(new FileInputStream(new File(CURRENT_API_FILE)), null);
            start(parser);
        } catch (Exception e) {
            mResultObserver.notifyFailure(FailureType.CAUGHT_EXCEPTION, e.getMessage(),
                    e.getMessage());
        }
        if (mResultObserver.mDidFail) {
            fail(mResultObserver.mErrorString.toString());
        }
    }

    private void beginDocument(XmlPullParser parser, String firstElementName)
            throws XmlPullParserException, IOException {
        int type;
        while ((type=parser.next()) != XmlPullParser.START_TAG
                   && type != XmlPullParser.END_DOCUMENT) { }

        if (type != XmlPullParser.START_TAG) {
            throw new XmlPullParserException("No start tag found");
        }

        if (!parser.getName().equals(firstElementName)) {
            throw new XmlPullParserException("Unexpected start tag: found " + parser.getName() +
                    ", expected " + firstElementName);
        }
    }

    /**
     * Signature test entry point.
     */
    private void start(XmlPullParser parser) throws XmlPullParserException, IOException {
        logd(String.format("Name: %s", parser.getName()));
        logd(String.format("Text: %s", parser.getText()));
        logd(String.format("Namespace: %s", parser.getNamespace()));
        logd(String.format("Line Number: %s", parser.getLineNumber()));
        logd(String.format("Column Number: %s", parser.getColumnNumber()));
        logd(String.format("Position Description: %s", parser.getPositionDescription()));
        JDiffClassDescription currentClass = null;
        String currentPackage = "";
        JDiffMethod currentMethod = null;

        beginDocument(parser, TAG_ROOT);
        int type;
        while (true) {
            type = XmlPullParser.START_DOCUMENT;
            while ((type=parser.next()) != XmlPullParser.START_TAG
                       && type != XmlPullParser.END_DOCUMENT
                       && type != XmlPullParser.END_TAG) {

            }

            if (type == XmlPullParser.END_TAG) {
                if (TAG_CLASS.equals(parser.getName())
                        || TAG_INTERFACE.equals(parser.getName())) {
                    currentClass.checkSignatureCompliance();
                } else if (TAG_PACKAGE.equals(parser.getName())) {
                    currentPackage = "";
                }
                continue;
            }

            if (type == XmlPullParser.END_DOCUMENT) {
                break;
            }

            String tagname = parser.getName();
            if (!mKeyTagSet.contains(tagname)) {
                continue;
            }

            if (type == XmlPullParser.START_TAG && tagname.equals(TAG_PACKAGE)) {
                currentPackage = parser.getAttributeValue(null, ATTRIBUTE_NAME);
            } else if (tagname.equals(TAG_CLASS)) {
                currentClass = CurrentApi.loadClassInfo(
                            parser, false, currentPackage, mResultObserver);
            } else if (tagname.equals(TAG_INTERFACE)) {
                currentClass = CurrentApi.loadClassInfo(
                            parser, true, currentPackage, mResultObserver);
            } else if (tagname.equals(TAG_IMPLEMENTS)) {
                currentClass.addImplInterface(parser.getAttributeValue(null, ATTRIBUTE_NAME));
            } else if (tagname.equals(TAG_CONSTRUCTOR)) {
                JDiffConstructor constructor =
                        CurrentApi.loadConstructorInfo(parser, currentClass);
                currentClass.addConstructor(constructor);
                currentMethod = constructor;
            } else if (tagname.equals(TAG_METHOD)) {
                currentMethod = CurrentApi.loadMethodInfo(currentClass.getClassName(), parser);
                currentClass.addMethod(currentMethod);
            } else if (tagname.equals(TAG_PARAM)) {
                currentMethod.addParam(parser.getAttributeValue(null, ATTRIBUTE_TYPE));
            } else if (tagname.equals(TAG_EXCEPTION)) {
                currentMethod.addException(parser.getAttributeValue(null, ATTRIBUTE_TYPE));
            } else if (tagname.equals(TAG_FIELD)) {
                JDiffField field = CurrentApi.loadFieldInfo(currentClass.getClassName(), parser);
                currentClass.addField(field);
            } else {
                throw new RuntimeException(
                        "unknown tag exception:" + tagname);
            }
            if (currentPackage != null) {
                logd(String.format("currentPackage: %s", currentPackage));
            }
            if (currentClass != null) {
                logd(String.format("currentClass: %s", currentClass.toSignatureString()));
            }
            if (currentMethod != null) {
                logd(String.format("currentMethod: %s", currentMethod.toSignatureString()));
            }
        }
    }

    public static void loge(String msg, Exception e) {
        Log.e(TAG, msg, e);
    }

    public static void logd(String msg) {
        Log.d(TAG, msg);
    }
}
