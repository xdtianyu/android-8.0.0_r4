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

package android.platform.test.helpers;

import android.app.Instrumentation;

public abstract class AbstractAutoDialHelper extends AbstractStandardAppHelper{

    public AbstractAutoDialHelper(Instrumentation instr) {
    super(instr);
  }

    /**
     * Setup expectations: The app is open.
     *
     * This method is used to enter a dial a number and make calls.
     * @param phoneNumber - phone number to dial.
     */
    public abstract void dialNumber(String phoneNumber);

    /**
     * Setup expectations: The app is open.
     *
     * This method is used to end call.
     */
    public abstract void endCall();

    /**
     * Setup expectations: The app is open.
     *
     * This method is used to open call history details.
     */
    public abstract void openCallHistory();

    /**
     * Setup expectations: The app is open.
     *
     * This method is used to open missed call details.
     */
    public abstract void openMissedCall();

}
