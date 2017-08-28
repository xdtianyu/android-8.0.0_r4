/******************************************************************************
 *
 *  Copyright (C) 2016 The Android Open Source Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

#ifndef BTA_CLOSURE_INT_H
#define BTA_CLOSURE_INT_H

#include <stdbool.h>

#include "bta/sys/bta_sys.h"
#include "bta_api.h"
#include "include/bt_trace.h"

/* Accept bta_sys_register, and bta_sys_sendmsg. Those parameters can be used to
 * override system methods for tests.
 */
void bta_closure_init(tBTA_SYS_REGISTER registerer, tBTA_SYS_SENDMSG sender);
bool bta_closure_execute(BT_HDR* p_msg);

#endif /* BTA_CLOSURE_INT_H */
