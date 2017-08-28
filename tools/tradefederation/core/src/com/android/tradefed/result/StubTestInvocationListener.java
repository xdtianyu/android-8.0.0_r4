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
package com.android.tradefed.result;

/**
 * This class used to provide stub implementations for each {@link ITestInvocationListener} method.
 * With the arrival of default methods for interface definitions in Java 8, this class is no longer
 * needed. The stubs have been moved to {@link ITestInvocationListener}, but this class is kept for
 * backwards compatibility.
 *
 * @deprecated Classes should implement ITestInvocationListener directly.
 */
@Deprecated
public class StubTestInvocationListener extends StubTestRunListener
        implements ITestInvocationListener {

}

