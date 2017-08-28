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

#ifndef _CONTROLLERS_H__
#define _CONTROLLERS_H__

#include <sysutils/FrameworkListener.h>

#include "BandwidthController.h"
#include "ClatdController.h"
#include "EventReporter.h"
#include "FirewallController.h"
#include "IdletimerController.h"
#include "InterfaceController.h"
#include "IptablesRestoreController.h"
#include "NatController.h"
#include "NetworkController.h"
#include "PppController.h"
#include "ResolverController.h"
#include "StrictController.h"
#include "TetherController.h"
#include "XfrmController.h"

namespace android {
namespace net {

class Controllers {
public:
    Controllers();

    NetworkController netCtrl;
    TetherController tetherCtrl;
    NatController natCtrl;
    PppController pppCtrl;
    BandwidthController bandwidthCtrl;
    IdletimerController idletimerCtrl;
    ResolverController resolverCtrl;
    FirewallController firewallCtrl;
    ClatdController clatdCtrl;
    StrictController strictCtrl;
    EventReporter eventReporter;
    IptablesRestoreController iptablesRestoreCtrl;
    XfrmController xfrmCtrl;

    void init();

private:
    void initIptablesRules();
};

extern Controllers* gCtls;

}  // namespace net
}  // namespace android

#endif  // _CONTROLLERS_H__
