# Copyright (C) 2017 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Set of error prone rules to ensure code quality of tests

# Goal is to eventually merge with error_prone_rules.mk
LOCAL_ERROR_PRONE_FLAGS:= -Xep:JUnit3TestNotRun:ERROR \
                          -Xep:JUnitAmbiguousTestClass:ERROR \
                          -Xep:TryFailThrowable:ERROR

