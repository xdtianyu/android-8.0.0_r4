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

#ifndef LIBTEXTCLASSIFIER_TEXTCLASSIFIER_JNI_H_
#define LIBTEXTCLASSIFIER_TEXTCLASSIFIER_JNI_H_

#include <jni.h>
#include <string>

#include "smartselect/types.h"

#ifdef __cplusplus
extern "C" {
#endif

// SmartSelection.
JNIEXPORT jlong JNICALL
Java_android_view_textclassifier_SmartSelection_nativeNew(JNIEnv* env,
                                                          jobject thiz,
                                                          jint fd);

JNIEXPORT jintArray JNICALL
Java_android_view_textclassifier_SmartSelection_nativeSuggest(
    JNIEnv* env, jobject thiz, jlong ptr, jstring context, jint selection_begin,
    jint selection_end);

JNIEXPORT jobjectArray JNICALL
Java_android_view_textclassifier_SmartSelection_nativeClassifyText(
    JNIEnv* env, jobject thiz, jlong ptr, jstring context, jint selection_begin,
    jint selection_end, jint input_flags);

JNIEXPORT void JNICALL
Java_android_view_textclassifier_SmartSelection_nativeClose(JNIEnv* env,
                                                            jobject thiz,
                                                            jlong ptr);

JNIEXPORT jstring JNICALL
Java_android_view_textclassifier_SmartSelection_nativeGetLanguage(JNIEnv* env,
                                                                  jobject clazz,
                                                                  jint fd);

JNIEXPORT jint JNICALL
Java_android_view_textclassifier_SmartSelection_nativeGetVersion(JNIEnv* env,
                                                                 jobject clazz,
                                                                 jint fd);

// LangId.
JNIEXPORT jlong JNICALL Java_android_view_textclassifier_LangId_nativeNew(
    JNIEnv* env, jobject thiz, jint fd);

JNIEXPORT jobjectArray JNICALL
Java_android_view_textclassifier_LangId_nativeFindLanguages(JNIEnv* env,
                                                            jobject thiz,
                                                            jlong ptr,
                                                            jstring text);

JNIEXPORT void JNICALL Java_android_view_textclassifier_LangId_nativeClose(
    JNIEnv* env, jobject thiz, jlong ptr);

JNIEXPORT int JNICALL Java_android_view_textclassifier_LangId_nativeGetVersion(
    JNIEnv* env, jobject clazz, jint fd);

#ifdef __cplusplus
}
#endif

namespace libtextclassifier {

// Given a utf8 string and a span expressed in Java BMP (basic multilingual
// plane) codepoints, converts it to a span expressed in utf8 codepoints.
libtextclassifier::CodepointSpan ConvertIndicesBMPToUTF8(
    const std::string& utf8_str, libtextclassifier::CodepointSpan bmp_indices);

// Given a utf8 string and a span expressed in utf8 codepoints, converts it to a
// span expressed in Java BMP (basic multilingual plane) codepoints.
libtextclassifier::CodepointSpan ConvertIndicesUTF8ToBMP(
    const std::string& utf8_str, libtextclassifier::CodepointSpan utf8_indices);

}  // namespace libtextclassifier

#endif  // LIBTEXTCLASSIFIER_TEXTCLASSIFIER_JNI_H_
