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
package org.mockito.compat;

import org.hamcrest.Description;
import org.mockito.internal.util.Decamelizer;

/**
 * Base class for code that has to compile against Mockito 1.x and Mockito 2.x.
 */
public abstract class CapturingMatcher<T> extends org.mockito.internal.matchers.CapturingMatcher<T> {

    @Override
    public boolean matches(Object argument) {
        return matchesObject(argument);
    }

    public abstract boolean matchesObject(Object o);

    public final void describeTo(Description description) {
        description.appendText(toString());
    }

    @Override
    public String toString() {
        String className = getClass().getSimpleName();
        return Decamelizer.decamelizeMatcher(className);
    }
}
