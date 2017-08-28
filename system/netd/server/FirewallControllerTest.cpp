/*
 * Copyright 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * FirewallControllerTest.cpp - unit tests for FirewallController.cpp
 */

#include <string>
#include <vector>
#include <stdio.h>

#include <gtest/gtest.h>

#include <android-base/strings.h>

#include "FirewallController.h"
#include "IptablesBaseTest.h"


class FirewallControllerTest : public IptablesBaseTest {
protected:
    FirewallControllerTest() {
        FirewallController::execIptables = fakeExecIptables;
        FirewallController::execIptablesSilently = fakeExecIptables;
        FirewallController::execIptablesRestore = fakeExecIptablesRestore;
    }
    FirewallController mFw;

    std::string makeUidRules(IptablesTarget a, const char* b, bool c,
                             const std::vector<int32_t>& d) {
        return mFw.makeUidRules(a, b, c, d);
    }

    int createChain(const char* a, FirewallType b) {
        return mFw.createChain(a, b);
    }
};


TEST_F(FirewallControllerTest, TestCreateWhitelistChain) {
    std::vector<std::string> expectedRestore4 = {
        "*filter",
        ":fw_whitelist -",
        "-A fw_whitelist -m owner --uid-owner 0-9999 -j RETURN",
        "-A fw_whitelist -i lo -j RETURN",
        "-A fw_whitelist -o lo -j RETURN",
        "-A fw_whitelist -p tcp --tcp-flags RST RST -j RETURN",
        "-A fw_whitelist -j DROP",
        "COMMIT\n"
    };
    std::vector<std::string> expectedRestore6 = {
        "*filter",
        ":fw_whitelist -",
        "-A fw_whitelist -m owner --uid-owner 0-9999 -j RETURN",
        "-A fw_whitelist -i lo -j RETURN",
        "-A fw_whitelist -o lo -j RETURN",
        "-A fw_whitelist -p tcp --tcp-flags RST RST -j RETURN",
        "-A fw_whitelist -p icmpv6 --icmpv6-type packet-too-big -j RETURN",
        "-A fw_whitelist -p icmpv6 --icmpv6-type router-solicitation -j RETURN",
        "-A fw_whitelist -p icmpv6 --icmpv6-type router-advertisement -j RETURN",
        "-A fw_whitelist -p icmpv6 --icmpv6-type neighbour-solicitation -j RETURN",
        "-A fw_whitelist -p icmpv6 --icmpv6-type neighbour-advertisement -j RETURN",
        "-A fw_whitelist -p icmpv6 --icmpv6-type redirect -j RETURN",
        "-A fw_whitelist -j DROP",
        "COMMIT\n"
    };
    std::vector<std::pair<IptablesTarget, std::string>> expectedRestoreCommands = {
        { V4, android::base::Join(expectedRestore4, '\n') },
        { V6, android::base::Join(expectedRestore6, '\n') },
    };

    createChain("fw_whitelist", WHITELIST);
    expectIptablesRestoreCommands(expectedRestoreCommands);
}

TEST_F(FirewallControllerTest, TestCreateBlacklistChain) {
    std::vector<std::string> expectedRestore = {
        "*filter",
        ":fw_blacklist -",
        "-A fw_blacklist -i lo -j RETURN",
        "-A fw_blacklist -o lo -j RETURN",
        "-A fw_blacklist -p tcp --tcp-flags RST RST -j RETURN",
        "COMMIT\n"
    };
    std::vector<std::pair<IptablesTarget, std::string>> expectedRestoreCommands = {
        { V4, android::base::Join(expectedRestore, '\n') },
        { V6, android::base::Join(expectedRestore, '\n') },
    };

    createChain("fw_blacklist", BLACKLIST);
    expectIptablesRestoreCommands(expectedRestoreCommands);
}

TEST_F(FirewallControllerTest, TestSetStandbyRule) {
    ExpectedIptablesCommands expected = {
        { V4V6, "*filter\n-D fw_standby -m owner --uid-owner 12345 -j DROP\nCOMMIT\n" }
    };
    mFw.setUidRule(STANDBY, 12345, ALLOW);
    expectIptablesRestoreCommands(expected);

    expected = {
        { V4V6, "*filter\n-A fw_standby -m owner --uid-owner 12345 -j DROP\nCOMMIT\n" }
    };
    mFw.setUidRule(STANDBY, 12345, DENY);
    expectIptablesRestoreCommands(expected);
}

TEST_F(FirewallControllerTest, TestSetDozeRule) {
    ExpectedIptablesCommands expected = {
        { V4V6, "*filter\n-I fw_dozable -m owner --uid-owner 54321 -j RETURN\nCOMMIT\n" }
    };
    mFw.setUidRule(DOZABLE, 54321, ALLOW);
    expectIptablesRestoreCommands(expected);

    expected = {
        { V4V6, "*filter\n-D fw_dozable -m owner --uid-owner 54321 -j RETURN\nCOMMIT\n" }
    };
    mFw.setUidRule(DOZABLE, 54321, DENY);
    expectIptablesRestoreCommands(expected);
}

TEST_F(FirewallControllerTest, TestSetFirewallRule) {
    ExpectedIptablesCommands expected = {
        { V4V6, "*filter\n"
                "-A fw_INPUT -m owner --uid-owner 54321 -j DROP\n"
                "-A fw_OUTPUT -m owner --uid-owner 54321 -j DROP\n"
                "COMMIT\n" }
    };
    mFw.setUidRule(NONE, 54321, DENY);
    expectIptablesRestoreCommands(expected);

    expected = {
        { V4V6, "*filter\n"
                "-D fw_INPUT -m owner --uid-owner 54321 -j DROP\n"
                "-D fw_OUTPUT -m owner --uid-owner 54321 -j DROP\n"
                "COMMIT\n" }
    };
    mFw.setUidRule(NONE, 54321, ALLOW);
    expectIptablesRestoreCommands(expected);
}

TEST_F(FirewallControllerTest, TestReplaceWhitelistUidRule) {
    std::string expected =
            "*filter\n"
            ":FW_whitechain -\n"
            "-A FW_whitechain -m owner --uid-owner 10023 -j RETURN\n"
            "-A FW_whitechain -m owner --uid-owner 10059 -j RETURN\n"
            "-A FW_whitechain -m owner --uid-owner 10124 -j RETURN\n"
            "-A FW_whitechain -m owner --uid-owner 10111 -j RETURN\n"
            "-A FW_whitechain -m owner --uid-owner 110122 -j RETURN\n"
            "-A FW_whitechain -m owner --uid-owner 210153 -j RETURN\n"
            "-A FW_whitechain -m owner --uid-owner 210024 -j RETURN\n"
            "-A FW_whitechain -m owner --uid-owner 0-9999 -j RETURN\n"
            "-A FW_whitechain -i lo -j RETURN\n"
            "-A FW_whitechain -o lo -j RETURN\n"
            "-A FW_whitechain -p tcp --tcp-flags RST RST -j RETURN\n"
            "-A FW_whitechain -p icmpv6 --icmpv6-type packet-too-big -j RETURN\n"
            "-A FW_whitechain -p icmpv6 --icmpv6-type router-solicitation -j RETURN\n"
            "-A FW_whitechain -p icmpv6 --icmpv6-type router-advertisement -j RETURN\n"
            "-A FW_whitechain -p icmpv6 --icmpv6-type neighbour-solicitation -j RETURN\n"
            "-A FW_whitechain -p icmpv6 --icmpv6-type neighbour-advertisement -j RETURN\n"
            "-A FW_whitechain -p icmpv6 --icmpv6-type redirect -j RETURN\n"
            "-A FW_whitechain -j DROP\n"
            "COMMIT\n";

    std::vector<int32_t> uids = { 10023, 10059, 10124, 10111, 110122, 210153, 210024 };
    EXPECT_EQ(expected, makeUidRules(V6, "FW_whitechain", true, uids));
}

TEST_F(FirewallControllerTest, TestReplaceBlacklistUidRule) {
    std::string expected =
            "*filter\n"
            ":FW_blackchain -\n"
            "-A FW_blackchain -i lo -j RETURN\n"
            "-A FW_blackchain -o lo -j RETURN\n"
            "-A FW_blackchain -p tcp --tcp-flags RST RST -j RETURN\n"
            "-A FW_blackchain -m owner --uid-owner 10023 -j DROP\n"
            "-A FW_blackchain -m owner --uid-owner 10059 -j DROP\n"
            "-A FW_blackchain -m owner --uid-owner 10124 -j DROP\n"
            "COMMIT\n";

    std::vector<int32_t> uids = { 10023, 10059, 10124 };
    EXPECT_EQ(expected, makeUidRules(V4 ,"FW_blackchain", false, uids));
}

TEST_F(FirewallControllerTest, TestEnableChildChains) {
    std::vector<std::string> expected = {
        "*filter\n"
        "-A fw_INPUT -j fw_dozable\n"
        "-A fw_OUTPUT -j fw_dozable\n"
        "COMMIT\n"
    };
    EXPECT_EQ(0, mFw.enableChildChains(DOZABLE, true));
    expectIptablesRestoreCommands(expected);

    expected = {
        "*filter\n"
        "-D fw_INPUT -j fw_powersave\n"
        "-D fw_OUTPUT -j fw_powersave\n"
        "COMMIT\n"
    };
    EXPECT_EQ(0, mFw.enableChildChains(POWERSAVE, false));
    expectIptablesRestoreCommands(expected);
}
