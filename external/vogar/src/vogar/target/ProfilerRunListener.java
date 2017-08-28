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

package vogar.target;

import org.junit.runner.Description;
import org.junit.runner.notification.RunListener;
import vogar.target.Profiler;

/**
 * A {@link RunListener} that will notify the {@link Profiler} before and after the start of every
 * test.
 */
public class ProfilerRunListener extends RunListener {
    private final Profiler profiler;

    public ProfilerRunListener(Profiler profiler) {
        this.profiler = profiler;
    }

    @Override
    public void testStarted(Description description) throws Exception {
        profiler.start();
    }

    @Override
    public void testFinished(Description description) throws Exception {
        profiler.stop();
    }
}
