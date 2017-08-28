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
#ifndef ESE_SYSDEPS_H__
#define ESE_SYSDEPS_H__ 1

#include <stdbool.h> /* bool */
#include <stdint.h> /* uint*_t */

#ifndef NULL
#define NULL ((void *)(0))
#endif

extern void *ese_memcpy(void *__dest, const void *__src, uint64_t __n);
extern void *ese_memset(void *__s, int __c, uint64_t __n);

#endif  /* ESE_SYSDEPS_H__ */
