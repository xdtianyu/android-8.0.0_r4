/*
 * Copyright (C) 2012 The Android Open Source Project
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

#define LOG_TAG "BluetoothHidServiceJni"

#define LOG_NDEBUG 1

#include "android_runtime/AndroidRuntime.h"
#include "com_android_bluetooth.h"
#include "hardware/bt_hh.h"
#include "utils/Log.h"

#include <string.h>

namespace android {

static jmethodID method_onConnectStateChanged;
static jmethodID method_onGetProtocolMode;
static jmethodID method_onGetReport;
static jmethodID method_onHandshake;
static jmethodID method_onVirtualUnplug;

static const bthh_interface_t* sBluetoothHidInterface = NULL;
static jobject mCallbacksObj = NULL;

static jbyteArray marshall_bda(bt_bdaddr_t* bd_addr) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return NULL;

  jbyteArray addr = sCallbackEnv->NewByteArray(sizeof(bt_bdaddr_t));
  if (!addr) {
    ALOGE("Fail to new jbyteArray bd addr");
    return NULL;
  }
  sCallbackEnv->SetByteArrayRegion(addr, 0, sizeof(bt_bdaddr_t),
                                   (jbyte*)bd_addr);
  return addr;
}

static void connection_state_callback(bt_bdaddr_t* bd_addr,
                                      bthh_connection_state_t state) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;
  if (!mCallbacksObj) {
    ALOGE("%s: mCallbacksObj is null", __func__);
    return;
  }
  ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
  if (!addr.get()) {
    ALOGE("Fail to new jbyteArray bd addr for HID channel state");
    return;
  }

  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onConnectStateChanged,
                               addr.get(), (jint)state);
}

static void get_protocol_mode_callback(bt_bdaddr_t* bd_addr,
                                       bthh_status_t hh_status,
                                       bthh_protocol_mode_t mode) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;
  if (!mCallbacksObj) {
    ALOGE("%s: mCallbacksObj is null", __func__);
    return;
  }
  if (hh_status != BTHH_OK) {
    ALOGE("BTHH Status is not OK!");
    return;
  }

  ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
  if (!addr.get()) {
    ALOGE("Fail to new jbyteArray bd addr for get protocal mode callback");
    return;
  }

  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onGetProtocolMode,
                               addr.get(), (jint)mode);
}

static void get_report_callback(bt_bdaddr_t* bd_addr, bthh_status_t hh_status,
                                uint8_t* rpt_data, int rpt_size) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;
  if (!mCallbacksObj) {
    ALOGE("%s: mCallbacksObj is null", __func__);
    return;
  }
  if (hh_status != BTHH_OK) {
    ALOGE("BTHH Status is not OK!");
    return;
  }

  ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
  if (!addr.get()) {
    ALOGE("Fail to new jbyteArray bd addr for get report callback");
    return;
  }
  ScopedLocalRef<jbyteArray> data(sCallbackEnv.get(),
                                  sCallbackEnv->NewByteArray(rpt_size));
  if (!data.get()) {
    ALOGE("Fail to new jbyteArray data for get report callback");
    return;
  }

  sCallbackEnv->SetByteArrayRegion(data.get(), 0, rpt_size, (jbyte*)rpt_data);
  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onGetReport, addr.get(),
                               data.get(), (jint)rpt_size);
}

static void virtual_unplug_callback(bt_bdaddr_t* bd_addr,
                                    bthh_status_t hh_status) {
  ALOGV("call to virtual_unplug_callback");
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;
  if (!mCallbacksObj) {
    ALOGE("%s: mCallbacksObj is null", __func__);
    return;
  }
  ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
  if (!addr.get()) {
    ALOGE("Fail to new jbyteArray bd addr for HID channel state");
    return;
  }
  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onVirtualUnplug,
                               addr.get(), (jint)hh_status);
}

static void handshake_callback(bt_bdaddr_t* bd_addr, bthh_status_t hh_status) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;
  if (!mCallbacksObj) {
    ALOGE("%s: mCallbacksObj is null", __func__);
    return;
  }

  ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
  if (!addr.get()) {
    ALOGE("Fail to new jbyteArray bd addr for handshake callback");
    return;
  }
  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onHandshake, addr.get(),
                               (jint)hh_status);
}

static bthh_callbacks_t sBluetoothHidCallbacks = {
    sizeof(sBluetoothHidCallbacks),
    connection_state_callback,
    NULL,
    get_protocol_mode_callback,
    NULL,
    get_report_callback,
    virtual_unplug_callback,
    handshake_callback};

// Define native functions

static void classInitNative(JNIEnv* env, jclass clazz) {
  method_onConnectStateChanged =
      env->GetMethodID(clazz, "onConnectStateChanged", "([BI)V");
  method_onGetProtocolMode =
      env->GetMethodID(clazz, "onGetProtocolMode", "([BI)V");
  method_onGetReport = env->GetMethodID(clazz, "onGetReport", "([B[BI)V");
  method_onHandshake = env->GetMethodID(clazz, "onHandshake", "([BI)V");
  method_onVirtualUnplug = env->GetMethodID(clazz, "onVirtualUnplug", "([BI)V");

  ALOGI("%s: succeeds", __func__);
}

static void initializeNative(JNIEnv* env, jobject object) {
  const bt_interface_t* btInf = getBluetoothInterface();
  if (btInf == NULL) {
    ALOGE("Bluetooth module is not loaded");
    return;
  }

  if (sBluetoothHidInterface != NULL) {
    ALOGW("Cleaning up Bluetooth HID Interface before initializing...");
    sBluetoothHidInterface->cleanup();
    sBluetoothHidInterface = NULL;
  }

  if (mCallbacksObj != NULL) {
    ALOGW("Cleaning up Bluetooth GID callback object");
    env->DeleteGlobalRef(mCallbacksObj);
    mCallbacksObj = NULL;
  }

  sBluetoothHidInterface =
      (bthh_interface_t*)btInf->get_profile_interface(BT_PROFILE_HIDHOST_ID);
  if (sBluetoothHidInterface == NULL) {
    ALOGE("Failed to get Bluetooth HID Interface");
    return;
  }

  bt_status_t status = sBluetoothHidInterface->init(&sBluetoothHidCallbacks);
  if (status != BT_STATUS_SUCCESS) {
    ALOGE("Failed to initialize Bluetooth HID, status: %d", status);
    sBluetoothHidInterface = NULL;
    return;
  }

  mCallbacksObj = env->NewGlobalRef(object);
}

static void cleanupNative(JNIEnv* env, jobject object) {
  const bt_interface_t* btInf = getBluetoothInterface();

  if (btInf == NULL) {
    ALOGE("Bluetooth module is not loaded");
    return;
  }

  if (sBluetoothHidInterface != NULL) {
    ALOGW("Cleaning up Bluetooth HID Interface...");
    sBluetoothHidInterface->cleanup();
    sBluetoothHidInterface = NULL;
  }

  if (mCallbacksObj != NULL) {
    ALOGW("Cleaning up Bluetooth GID callback object");
    env->DeleteGlobalRef(mCallbacksObj);
    mCallbacksObj = NULL;
  }
}

static jboolean connectHidNative(JNIEnv* env, jobject object,
                                 jbyteArray address) {
  if (!sBluetoothHidInterface) return JNI_FALSE;

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    ALOGE("Bluetooth device address null");
    return JNI_FALSE;
  }

  jboolean ret = JNI_TRUE;
  bt_status_t status = sBluetoothHidInterface->connect((bt_bdaddr_t*)addr);
  if (status != BT_STATUS_SUCCESS) {
    ALOGE("Failed HID channel connection, status: %d", status);
    ret = JNI_FALSE;
  }
  env->ReleaseByteArrayElements(address, addr, 0);

  return ret;
}

static jboolean disconnectHidNative(JNIEnv* env, jobject object,
                                    jbyteArray address) {
  jbyte* addr;
  jboolean ret = JNI_TRUE;
  if (!sBluetoothHidInterface) return JNI_FALSE;

  addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    ALOGE("Bluetooth device address null");
    return JNI_FALSE;
  }

  bt_status_t status = sBluetoothHidInterface->disconnect((bt_bdaddr_t*)addr);
  if (status != BT_STATUS_SUCCESS) {
    ALOGE("Failed disconnect hid channel, status: %d", status);
    ret = JNI_FALSE;
  }
  env->ReleaseByteArrayElements(address, addr, 0);

  return ret;
}

static jboolean getProtocolModeNative(JNIEnv* env, jobject object,
                                      jbyteArray address) {
  if (!sBluetoothHidInterface) return JNI_FALSE;

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    ALOGE("Bluetooth device address null");
    return JNI_FALSE;
  }

  jboolean ret = JNI_TRUE;
  // TODO: protocolMode is unused by the backend: see b/28908173
  bthh_protocol_mode_t protocolMode = BTHH_UNSUPPORTED_MODE;
  bt_status_t status = sBluetoothHidInterface->get_protocol(
      (bt_bdaddr_t*)addr, (bthh_protocol_mode_t)protocolMode);
  if (status != BT_STATUS_SUCCESS) {
    ALOGE("Failed get protocol mode, status: %d", status);
    ret = JNI_FALSE;
  }
  env->ReleaseByteArrayElements(address, addr, 0);

  return ret;
}

static jboolean virtualUnPlugNative(JNIEnv* env, jobject object,
                                    jbyteArray address) {
  if (!sBluetoothHidInterface) return JNI_FALSE;

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    ALOGE("Bluetooth device address null");
    return JNI_FALSE;
  }

  jboolean ret = JNI_TRUE;
  bt_status_t status =
      sBluetoothHidInterface->virtual_unplug((bt_bdaddr_t*)addr);
  if (status != BT_STATUS_SUCCESS) {
    ALOGE("Failed virual unplug, status: %d", status);
    ret = JNI_FALSE;
  }
  env->ReleaseByteArrayElements(address, addr, 0);
  return ret;
}

static jboolean setProtocolModeNative(JNIEnv* env, jobject object,
                                      jbyteArray address, jint protocolMode) {
  if (!sBluetoothHidInterface) return JNI_FALSE;

  ALOGD("%s: protocolMode = %d", __func__, protocolMode);

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    ALOGE("Bluetooth device address null");
    return JNI_FALSE;
  }

  bthh_protocol_mode_t mode;
  switch (protocolMode) {
    case 0:
      mode = BTHH_REPORT_MODE;
      break;
    case 1:
      mode = BTHH_BOOT_MODE;
      break;
    default:
      ALOGE("Unknown HID protocol mode");
      return JNI_FALSE;
  }

  jboolean ret = JNI_TRUE;
  bt_status_t status =
      sBluetoothHidInterface->set_protocol((bt_bdaddr_t*)addr, mode);
  if (status != BT_STATUS_SUCCESS) {
    ALOGE("Failed set protocol mode, status: %d", status);
    ret = JNI_FALSE;
  }
  env->ReleaseByteArrayElements(address, addr, 0);

  return ret;
}

static jboolean getReportNative(JNIEnv* env, jobject object, jbyteArray address,
                                jbyte reportType, jbyte reportId,
                                jint bufferSize) {
  ALOGV("%s: reportType = %d, reportId = %d, bufferSize = %d", __func__,
        reportType, reportId, bufferSize);
  if (!sBluetoothHidInterface) return JNI_FALSE;

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    ALOGE("Bluetooth device address null");
    return JNI_FALSE;
  }

  jint rType = reportType;
  jint rId = reportId;

  bt_status_t status = sBluetoothHidInterface->get_report(
      (bt_bdaddr_t*)addr, (bthh_report_type_t)rType, (uint8_t)rId, bufferSize);
  jboolean ret = JNI_TRUE;
  if (status != BT_STATUS_SUCCESS) {
    ALOGE("Failed get report, status: %d", status);
    ret = JNI_FALSE;
  }
  env->ReleaseByteArrayElements(address, addr, 0);

  return ret;
}

static jboolean setReportNative(JNIEnv* env, jobject object, jbyteArray address,
                                jbyte reportType, jstring report) {
  ALOGV("%s: reportType = %d", __func__, reportType);
  if (!sBluetoothHidInterface) return JNI_FALSE;

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    ALOGE("Bluetooth device address null");
    return JNI_FALSE;
  }
  jint rType = reportType;
  const char* c_report = env->GetStringUTFChars(report, NULL);

  jboolean ret = JNI_TRUE;
  bt_status_t status = sBluetoothHidInterface->set_report(
      (bt_bdaddr_t*)addr, (bthh_report_type_t)rType, (char*)c_report);
  if (status != BT_STATUS_SUCCESS) {
    ALOGE("Failed set report, status: %d", status);
    ret = JNI_FALSE;
  }
  env->ReleaseStringUTFChars(report, c_report);
  env->ReleaseByteArrayElements(address, addr, 0);

  return ret;
}

static jboolean sendDataNative(JNIEnv* env, jobject object, jbyteArray address,
                               jstring report) {
  ALOGV("%s", __func__);
  jboolean ret = JNI_TRUE;
  if (!sBluetoothHidInterface) return JNI_FALSE;

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    ALOGE("Bluetooth device address null");
    return JNI_FALSE;
  }

  const char* c_report = env->GetStringUTFChars(report, NULL);

  bt_status_t status =
      sBluetoothHidInterface->send_data((bt_bdaddr_t*)addr, (char*)c_report);
  if (status != BT_STATUS_SUCCESS) {
    ALOGE("Failed set report, status: %d", status);
    ret = JNI_FALSE;
  }
  env->ReleaseStringUTFChars(report, c_report);
  env->ReleaseByteArrayElements(address, addr, 0);

  return ret;
}

static JNINativeMethod sMethods[] = {
    {"classInitNative", "()V", (void*)classInitNative},
    {"initializeNative", "()V", (void*)initializeNative},
    {"cleanupNative", "()V", (void*)cleanupNative},
    {"connectHidNative", "([B)Z", (void*)connectHidNative},
    {"disconnectHidNative", "([B)Z", (void*)disconnectHidNative},
    {"getProtocolModeNative", "([B)Z", (void*)getProtocolModeNative},
    {"virtualUnPlugNative", "([B)Z", (void*)virtualUnPlugNative},
    {"setProtocolModeNative", "([BB)Z", (void*)setProtocolModeNative},
    {"getReportNative", "([BBBI)Z", (void*)getReportNative},
    {"setReportNative", "([BBLjava/lang/String;)Z", (void*)setReportNative},
    {"sendDataNative", "([BLjava/lang/String;)Z", (void*)sendDataNative},
};

int register_com_android_bluetooth_hid(JNIEnv* env) {
  return jniRegisterNativeMethods(env, "com/android/bluetooth/hid/HidService",
                                  sMethods, NELEM(sMethods));
}
}
