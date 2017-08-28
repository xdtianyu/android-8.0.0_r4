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

import android.signature.cts.JDiffClassDescription.JDiffConstructor;
import android.signature.cts.JDiffClassDescription.JDiffField;
import android.signature.cts.JDiffClassDescription.JDiffMethod;

import java.lang.reflect.Modifier;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

/**
 * Helper methods and constants used for parsing the current api file.
 */
class CurrentApi {

    private CurrentApi() {}

    static final String CURRENT_API_FILE =
            "/data/local/tmp/signature-test/current.api";
    static final String SYSTEM_CURRENT_API_FILE =
            "/data/local/tmp/signature-test/system-current.api";

    static final String TAG_ROOT = "api";
    static final String TAG_PACKAGE = "package";
    static final String TAG_CLASS = "class";
    static final String TAG_INTERFACE = "interface";
    static final String TAG_IMPLEMENTS = "implements";
    static final String TAG_CONSTRUCTOR = "constructor";
    static final String TAG_METHOD = "method";
    static final String TAG_PARAM = "parameter";
    static final String TAG_EXCEPTION = "exception";
    static final String TAG_FIELD = "field";

    static final String MODIFIER_ABSTRACT = "abstract";
    static final String MODIFIER_FINAL = "final";
    static final String MODIFIER_NATIVE = "native";
    static final String MODIFIER_PRIVATE = "private";
    static final String MODIFIER_PROTECTED = "protected";
    static final String MODIFIER_PUBLIC = "public";
    static final String MODIFIER_STATIC = "static";
    static final String MODIFIER_SYNCHRONIZED = "synchronized";
    static final String MODIFIER_TRANSIENT = "transient";
    static final String MODIFIER_VOLATILE = "volatile";
    static final String MODIFIER_VISIBILITY = "visibility";

    static final String ATTRIBUTE_NAME = "name";
    static final String ATTRIBUTE_VALUE = "value";
    static final String ATTRIBUTE_EXTENDS = "extends";
    static final String ATTRIBUTE_TYPE = "type";
    static final String ATTRIBUTE_RETURN = "return";

    /**
     * Load field information from xml to memory.
     *
     * @param className of the class being examined which will be shown in error messages
     * @param parser The XmlPullParser which carries the xml information.
     * @return the new field
     */
    static JDiffField loadFieldInfo(String className, XmlPullParser parser) {
        String fieldName = parser.getAttributeValue(null, ATTRIBUTE_NAME);
        String fieldType = parser.getAttributeValue(null, ATTRIBUTE_TYPE);
        int modifier = jdiffModifierToReflectionFormat(className, parser);
        String value = parser.getAttributeValue(null, ATTRIBUTE_VALUE);
        return new JDiffField(fieldName, fieldType, modifier, value);
    }

    /**
     * Load method information from xml to memory.
     *
     * @param className of the class being examined which will be shown in error messages
     * @param parser The XmlPullParser which carries the xml information.
     * @return the newly loaded method.
     */
    static JDiffMethod loadMethodInfo(String className, XmlPullParser parser) {
        String methodName = parser.getAttributeValue(null, ATTRIBUTE_NAME);
        String returnType = parser.getAttributeValue(null, ATTRIBUTE_RETURN);
        int modifier = jdiffModifierToReflectionFormat(className, parser);
        return new JDiffMethod(methodName, modifier, returnType);
    }

    /**
     * Load constructor information from xml to memory.
     *
     * @param parser The XmlPullParser which carries the xml information.
     * @param currentClass the current class being loaded.
     * @return the new constructor
     */
    static JDiffConstructor loadConstructorInfo(
                XmlPullParser parser, JDiffClassDescription currentClass) {
        String name = currentClass.getClassName();
        int modifier = jdiffModifierToReflectionFormat(name, parser);
        return new JDiffConstructor(name, modifier);
    }

    /**
     * Load class or interface information to memory.
     *
     * @param parser The XmlPullParser which carries the xml information.
     * @param isInterface true if the current class is an interface, otherwise is false.
     * @param pkg the name of the java package this class can be found in.
     * @return the new class description.
     */
    static JDiffClassDescription loadClassInfo(
            XmlPullParser parser, boolean isInterface, String pkg,
            ResultObserver resultObserver) {
        String className = parser.getAttributeValue(null, ATTRIBUTE_NAME);
        JDiffClassDescription currentClass;
        if (resultObserver != null) {
            currentClass = new JDiffClassDescription(pkg, className, resultObserver);
        } else {
            currentClass = new JDiffClassDescription(pkg, className);
        }

        currentClass.setModifier(jdiffModifierToReflectionFormat(className, parser));
        currentClass.setType(isInterface ? JDiffClassDescription.JDiffType.INTERFACE :
                             JDiffClassDescription.JDiffType.CLASS);
        currentClass.setExtendsClass(parser.getAttributeValue(null, ATTRIBUTE_EXTENDS));
        return currentClass;
    }

    /**
     * Convert string modifier to int modifier.
     *
     * @param name of the class/method/field being examined which will be shown in error messages
     * @param key modifier name
     * @param value modifier value
     * @return converted modifier value
     */
    static int modifierDescriptionToReflectedType(String name, String key, String value) {
        if (key.equals(MODIFIER_ABSTRACT)) {
            return value.equals("true") ? Modifier.ABSTRACT : 0;
        } else if (key.equals(MODIFIER_FINAL)) {
            return value.equals("true") ? Modifier.FINAL : 0;
        } else if (key.equals(MODIFIER_NATIVE)) {
            return value.equals("true") ? Modifier.NATIVE : 0;
        } else if (key.equals(MODIFIER_STATIC)) {
            return value.equals("true") ? Modifier.STATIC : 0;
        } else if (key.equals(MODIFIER_SYNCHRONIZED)) {
            return value.equals("true") ? Modifier.SYNCHRONIZED : 0;
        } else if (key.equals(MODIFIER_TRANSIENT)) {
            return value.equals("true") ? Modifier.TRANSIENT : 0;
        } else if (key.equals(MODIFIER_VOLATILE)) {
            return value.equals("true") ? Modifier.VOLATILE : 0;
        } else if (key.equals(MODIFIER_VISIBILITY)) {
            if (value.equals(MODIFIER_PRIVATE)) {
                throw new RuntimeException("Private visibility found in API spec: " + name);
            } else if (value.equals(MODIFIER_PROTECTED)) {
                return Modifier.PROTECTED;
            } else if (value.equals(MODIFIER_PUBLIC)) {
                return Modifier.PUBLIC;
            } else if ("".equals(value)) {
                // If the visibility is "", it means it has no modifier.
                // which is package private. We should return 0 for this modifier.
                return 0;
            } else {
                throw new RuntimeException("Unknown modifier found in API spec: " + value);
            }
        }
        return 0;
    }

    /**
     * Transfer string modifier to int one.
     *
     * @param name of the class/method/field being examined which will be shown in error messages
     * @param parser XML resource parser
     * @return converted modifier
     */
    static int jdiffModifierToReflectionFormat(String name, XmlPullParser parser){
        int modifier = 0;
        for (int i = 0;i < parser.getAttributeCount();i++) {
            modifier |= modifierDescriptionToReflectedType(name, parser.getAttributeName(i),
                    parser.getAttributeValue(i));
        }
        return modifier;
    }
}
