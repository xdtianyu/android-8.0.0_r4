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

#ifndef CHRE_UTIL_MACROS_H_
#define CHRE_UTIL_MACROS_H_

/**
 * Obtains the number of elements in a C-style array.
 */
#define ARRAY_SIZE(array) (sizeof(array) / sizeof((array)[0]))

/**
 * Performs macro expansion then converts the value into a string literal
 */
#define STRINGIFY(x) STRINGIFY2(x)
#define STRINGIFY2(x) #x

#endif  // CHRE_UTIL_MACROS_H_
