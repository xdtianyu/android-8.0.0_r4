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

#ifndef __VTS_HAL_HIDL_TARGET_TEST_BASE_H
#define __VTS_HAL_HIDL_TARGET_TEST_BASE_H

#include <gtest/gtest.h>
#include <hidl/HidlSupport.h>
#include <utils/Log.h>
#include <utils/RefBase.h>

#define VTS_HAL_HIDL_GET_STUB "VTS_HAL_HIDL_GET_STUB"

namespace testing {

using ::android::sp;

// VTS target side test template
class VtsHalHidlTargetTestBase : public ::testing::Test {
 public:
  virtual void SetUp() override {
    ALOGI("[Test Case] %s.%s BEGIN", getTestSuiteName().c_str(),
          getTestCaseName().c_str());
    ::std::string testCaseInfo = getTestCaseInfo();
    if (testCaseInfo.size()) {
      ALOGD("Test case info: %s", testCaseInfo.c_str());
    }
  }

  virtual void TearDown() override {
    ALOGI("[Test Case] %s.%s END", getTestSuiteName().c_str(),
          getTestCaseName().c_str());
  }

  /*
   * Return test suite name as string.
   */
  ::std::string getTestSuiteName() {
    return ::testing::UnitTest::GetInstance()->current_test_info()->name();
  }

  /*
   * Return test case name as string.
   */
  ::std::string getTestCaseName() {
    return ::testing::UnitTest::GetInstance()
        ->current_test_info()
        ->test_case_name();
  }

  /*
   * Return test case info as string.
   */
  ::std::string getTestCaseInfo() { return ""; }

  /*
   * Get value of system property as string on target
   */
  static ::std::string PropertyGet(const char* name);

  /*
   * Call interface's getService and use passthrough mode if set from host.
   */
  template <class T>
  static sp<T> getService(const ::std::string& serviceName = "default") {
    return T::getService(serviceName, VtsHalHidlTargetTestBase::VtsGetStub());
  }

  /*
   * Call interface's getService and use passthrough mode if set from host.
   */
  template <class T>
  static sp<T> getService(const char serviceName[]) {
    return T::getService(serviceName, VtsHalHidlTargetTestBase::VtsGetStub());
  }

  /*
   * Call interface's getService and use passthrough mode if set from host.
   */
  template <class T>
  static sp<T> getService(const ::android::hardware::hidl_string& serviceName) {
    return T::getService(serviceName, VtsHalHidlTargetTestBase::VtsGetStub());
  }

 private:
  /*
   * Decide bool val for getStub option. Will read environment variable set
   * from host. If environment variable is not set, return will default to
   * false.
   */
  static bool VtsGetStub();
};

}  // namespace testing

#endif  // __VTS_HAL_HIDL_TARGET_TEST_BASE_H
