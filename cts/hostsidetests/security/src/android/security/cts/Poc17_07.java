/**
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

package android.security.cts;

import android.platform.test.annotations.SecurityTest;

@SecurityTest
public class Poc17_07 extends SecurityTestCase {

    /**
     *  b/33863407
     */
    @SecurityTest
    public void testPocBug_33863407() throws Exception {
	enableAdbRoot(getDevice());
        if(containsDriver(getDevice(), "/sys/kernel/debug/mdp/reg")) {
            AdbUtils.runPoc("Bug-33863407", getDevice(), 60);
        }
    }

    /**
     *  b/36604779
     */
    @SecurityTest
    public void testPocBug_36604779() throws Exception {
      enableAdbRoot(getDevice());
        if(containsDriver(getDevice(), "/dev/port")) {
          AdbUtils.runCommandLine("cat /dev/port", getDevice());
        }
    }

    /**
     *  b/34973477
     */
    @SecurityTest
    public void testPocCVE_2017_0705() throws Exception {
        enableAdbRoot(getDevice());
        if(containsDriver(getDevice(), "/proc/net/psched")) {
            AdbUtils.runPoc("CVE-2017-0705", getDevice(), 60);
        }
    }

    /**
     *  b/34126808
     */
    @SecurityTest
    public void testPocCVE_2017_8263() throws Exception {
        enableAdbRoot(getDevice());
        if(containsDriver(getDevice(), "/dev/ashmem")) {
            AdbUtils.runPoc("CVE-2017-8263", getDevice(), 60);
        }
    }
}
