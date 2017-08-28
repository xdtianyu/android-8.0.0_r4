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
 * BandwidthControllerTest.cpp - unit tests for BandwidthController.cpp
 */

#include <string>
#include <vector>

#include <inttypes.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/socket.h>

#include <gtest/gtest.h>

#include <android-base/strings.h>
#include <android-base/stringprintf.h>

#include "BandwidthController.h"
#include "IptablesBaseTest.h"
#include "tun_interface.h"

using android::base::StringPrintf;
using android::net::TunInterface;

class BandwidthControllerTest : public IptablesBaseTest {
public:
    BandwidthControllerTest() {
        BandwidthController::execFunction = fake_android_fork_exec;
        BandwidthController::popenFunction = fake_popen;
        BandwidthController::iptablesRestoreFunction = fakeExecIptablesRestoreWithOutput;
    }
    BandwidthController mBw;
    TunInterface mTun;

    void SetUp() {
        ASSERT_EQ(0, mTun.init());
    }

    void TearDown() {
        mTun.destroy();
    }

    void addIptablesRestoreOutput(std::string contents) {
        sIptablesRestoreOutput.push_back(contents);
    }

    void addIptablesRestoreOutput(std::string contents1, std::string contents2) {
        sIptablesRestoreOutput.push_back(contents1);
        sIptablesRestoreOutput.push_back(contents2);
    }

    void clearIptablesRestoreOutput() {
        sIptablesRestoreOutput.clear();
    }

    void expectSetupCommands(const std::string& expectedClean, std::string expectedAccounting) {
        std::string expectedList =
            "*filter\n"
            "-S\n"
            "COMMIT\n";

        std::string expectedFlush =
            "*filter\n"
            ":bw_INPUT -\n"
            ":bw_OUTPUT -\n"
            ":bw_FORWARD -\n"
            ":bw_happy_box -\n"
            ":bw_penalty_box -\n"
            ":bw_data_saver -\n"
            ":bw_costly_shared -\n"
            "COMMIT\n"
            "*raw\n"
            ":bw_raw_PREROUTING -\n"
            "COMMIT\n"
            "*mangle\n"
            ":bw_mangle_POSTROUTING -\n"
            "COMMIT\n";

        ExpectedIptablesCommands expected = {{ V4, expectedList }};
        if (expectedClean.size()) {
            expected.push_back({ V4V6, expectedClean });
        }
        expected.push_back({ V4V6, expectedFlush });
        if (expectedAccounting.size()) {
            expected.push_back({ V4V6, expectedAccounting });
        }

        expectIptablesRestoreCommands(expected);
    }

    using IptOp = BandwidthController::IptOp;

    int runIptablesAlertCmd(IptOp a, const char *b, int64_t c) {
        return mBw.runIptablesAlertCmd(a, b, c);
    }

    int runIptablesAlertFwdCmd(IptOp a, const char *b, int64_t c) {
        return mBw.runIptablesAlertFwdCmd(a, b, c);
    }
};

TEST_F(BandwidthControllerTest, TestSetupIptablesHooks) {
    // Pretend some bw_costly_shared_<iface> rules already exist...
    addIptablesRestoreOutput(
        "-P OUTPUT ACCEPT\n"
        "-N bw_costly_rmnet_data0\n"
        "-N bw_costly_shared\n"
        "-N unrelated\n"
        "-N bw_costly_rmnet_data7\n");

    // ... and expect that they be flushed and deleted.
    std::string expectedCleanCmds =
        "*filter\n"
        ":bw_costly_rmnet_data0 -\n"
        "-X bw_costly_rmnet_data0\n"
        ":bw_costly_rmnet_data7 -\n"
        "-X bw_costly_rmnet_data7\n"
        "COMMIT\n";

    mBw.setupIptablesHooks();
    expectSetupCommands(expectedCleanCmds, "");
}

TEST_F(BandwidthControllerTest, TestEnableBandwidthControl) {
    // Pretend no bw_costly_shared_<iface> rules already exist...
    addIptablesRestoreOutput(
        "-P OUTPUT ACCEPT\n"
        "-N bw_costly_shared\n"
        "-N unrelated\n");

    // ... so none are flushed or deleted.
    std::string expectedClean = "";

    std::string expectedAccounting =
        "*filter\n"
        "-A bw_INPUT -m owner --socket-exists\n"
        "-A bw_OUTPUT -m owner --socket-exists\n"
        "-A bw_costly_shared --jump bw_penalty_box\n"
        "-A bw_penalty_box --jump bw_happy_box\n"
        "-A bw_happy_box --jump bw_data_saver\n"
        "-A bw_data_saver -j RETURN\n"
        "-I bw_happy_box -m owner --uid-owner 0-9999 --jump RETURN\n"
        "COMMIT\n"
        "*raw\n"
        "-A bw_raw_PREROUTING -m owner --socket-exists\n"
        "COMMIT\n"
        "*mangle\n"
        "-A bw_mangle_POSTROUTING -m owner --socket-exists\n"
        "COMMIT\n";

    mBw.enableBandwidthControl(false);
    expectSetupCommands(expectedClean, expectedAccounting);
}

TEST_F(BandwidthControllerTest, TestDisableBandwidthControl) {
    // Pretend some bw_costly_shared_<iface> rules already exist...
    addIptablesRestoreOutput(
        "-P OUTPUT ACCEPT\n"
        "-N bw_costly_rmnet_data0\n"
        "-N bw_costly_shared\n"
        "-N unrelated\n"
        "-N bw_costly_rmnet_data7\n");

    // ... and expect that they be flushed.
    std::string expectedCleanCmds =
        "*filter\n"
        ":bw_costly_rmnet_data0 -\n"
        ":bw_costly_rmnet_data7 -\n"
        "COMMIT\n";

    mBw.disableBandwidthControl();
    expectSetupCommands(expectedCleanCmds, "");
}

TEST_F(BandwidthControllerTest, TestEnableDataSaver) {
    mBw.enableDataSaver(true);
    std::vector<std::string> expected = {
        "*filter\n"
        "-R bw_data_saver 1 --jump REJECT\n"
        "COMMIT\n"
    };
    expectIptablesRestoreCommands(expected);

    mBw.enableDataSaver(false);
    expected = {
        "*filter\n"
        "-R bw_data_saver 1 --jump RETURN\n"
        "COMMIT\n"
    };
    expectIptablesRestoreCommands(expected);
}

std::string kIPv4TetherCounters = android::base::Join(std::vector<std::string> {
    "Chain natctrl_tether_counters (4 references)",
    "    pkts      bytes target     prot opt in     out     source               destination",
    "      26     2373 RETURN     all  --  wlan0  rmnet0  0.0.0.0/0            0.0.0.0/0",
    "      27     2002 RETURN     all  --  rmnet0 wlan0   0.0.0.0/0            0.0.0.0/0",
    "    1040   107471 RETURN     all  --  bt-pan rmnet0  0.0.0.0/0            0.0.0.0/0",
    "    1450  1708806 RETURN     all  --  rmnet0 bt-pan  0.0.0.0/0            0.0.0.0/0",
}, '\n');

std::string kIPv6TetherCounters = android::base::Join(std::vector<std::string> {
    "Chain natctrl_tether_counters (2 references)",
    "    pkts      bytes target     prot opt in     out     source               destination",
    "   10000 10000000 RETURN     all      wlan0  rmnet0  ::/0                 ::/0",
    "   20000 20000000 RETURN     all      rmnet0 wlan0   ::/0                 ::/0",
}, '\n');

std::string readSocketClientResponse(int fd) {
    char buf[32768];
    ssize_t bytesRead = read(fd, buf, sizeof(buf));
    if (bytesRead < 0) {
        return "";
    }
    for (int i = 0; i < bytesRead; i++) {
        if (buf[i] == '\0') buf[i] = '\n';
    }
    return std::string(buf, bytesRead);
}

void expectNoSocketClientResponse(int fd) {
    char buf[64];
    EXPECT_EQ(-1, read(fd, buf, sizeof(buf)));
}

TEST_F(BandwidthControllerTest, TestGetTetherStats) {
    int socketPair[2];
    ASSERT_EQ(0, socketpair(AF_UNIX, SOCK_STREAM, 0, socketPair));
    ASSERT_EQ(0, fcntl(socketPair[0], F_SETFL, O_NONBLOCK | fcntl(socketPair[0], F_GETFL)));
    ASSERT_EQ(0, fcntl(socketPair[1], F_SETFL, O_NONBLOCK | fcntl(socketPair[1], F_GETFL)));
    SocketClient cli(socketPair[0], false);

    std::string err;
    BandwidthController::TetherStats filter;

    // If no filter is specified, both IPv4 and IPv6 counters must have at least one interface pair.
    addIptablesRestoreOutput(kIPv4TetherCounters);
    ASSERT_EQ(-1, mBw.getTetherStats(&cli, filter, err));
    expectNoSocketClientResponse(socketPair[1]);
    clearIptablesRestoreOutput();

    addIptablesRestoreOutput(kIPv6TetherCounters);
    ASSERT_EQ(-1, mBw.getTetherStats(&cli, filter, err));
    clearIptablesRestoreOutput();

    // IPv4 and IPv6 counters are properly added together.
    addIptablesRestoreOutput(kIPv4TetherCounters, kIPv6TetherCounters);
    filter = BandwidthController::TetherStats();
    std::string expected =
            "114 wlan0 rmnet0 10002373 10026 20002002 20027\n"
            "114 bt-pan rmnet0 107471 1040 1708806 1450\n"
            "200 Tethering stats list completed\n";
    ASSERT_EQ(0, mBw.getTetherStats(&cli, filter, err));
    ASSERT_EQ(expected, readSocketClientResponse(socketPair[1]));
    expectNoSocketClientResponse(socketPair[1]);
    clearIptablesRestoreOutput();

    // Test filtering.
    addIptablesRestoreOutput(kIPv4TetherCounters, kIPv6TetherCounters);
    filter = BandwidthController::TetherStats("bt-pan", "rmnet0", -1, -1, -1, -1);
    expected = "221 bt-pan rmnet0 107471 1040 1708806 1450\n";
    ASSERT_EQ(0, mBw.getTetherStats(&cli, filter, err));
    ASSERT_EQ(expected, readSocketClientResponse(socketPair[1]));
    expectNoSocketClientResponse(socketPair[1]);
    clearIptablesRestoreOutput();

    addIptablesRestoreOutput(kIPv4TetherCounters, kIPv6TetherCounters);
    filter = BandwidthController::TetherStats("wlan0", "rmnet0", -1, -1, -1, -1);
    expected = "221 wlan0 rmnet0 10002373 10026 20002002 20027\n";
    ASSERT_EQ(0, mBw.getTetherStats(&cli, filter, err));
    ASSERT_EQ(expected, readSocketClientResponse(socketPair[1]));
    clearIptablesRestoreOutput();

    // Select nonexistent interfaces.
    addIptablesRestoreOutput(kIPv4TetherCounters, kIPv6TetherCounters);
    filter = BandwidthController::TetherStats("rmnet0", "foo0", -1, -1, -1, -1);
    expected = "200 Tethering stats list completed\n";
    ASSERT_EQ(0, mBw.getTetherStats(&cli, filter, err));
    ASSERT_EQ(expected, readSocketClientResponse(socketPair[1]));
    clearIptablesRestoreOutput();

    // No stats with a filter: no error.
    addIptablesRestoreOutput("", "");
    ASSERT_EQ(0, mBw.getTetherStats(&cli, filter, err));
    ASSERT_EQ("200 Tethering stats list completed\n", readSocketClientResponse(socketPair[1]));
    clearIptablesRestoreOutput();

    addIptablesRestoreOutput("foo", "foo");
    ASSERT_EQ(0, mBw.getTetherStats(&cli, filter, err));
    ASSERT_EQ("200 Tethering stats list completed\n", readSocketClientResponse(socketPair[1]));
    clearIptablesRestoreOutput();

    // No stats and empty filter: error.
    filter = BandwidthController::TetherStats();
    addIptablesRestoreOutput("", kIPv6TetherCounters);
    ASSERT_EQ(-1, mBw.getTetherStats(&cli, filter, err));
    expectNoSocketClientResponse(socketPair[1]);
    clearIptablesRestoreOutput();

    addIptablesRestoreOutput(kIPv4TetherCounters, "");
    ASSERT_EQ(-1, mBw.getTetherStats(&cli, filter, err));
    expectNoSocketClientResponse(socketPair[1]);
    clearIptablesRestoreOutput();

    // Include only one pair of interfaces and things are fine.
    std::vector<std::string> counterLines = android::base::Split(kIPv4TetherCounters, "\n");
    std::vector<std::string> brokenCounterLines = counterLines;
    counterLines.resize(4);
    std::string counters = android::base::Join(counterLines, "\n") + "\n";
    addIptablesRestoreOutput(counters, counters);
    expected =
            "114 wlan0 rmnet0 4746 52 4004 54\n"
            "200 Tethering stats list completed\n";
    ASSERT_EQ(0, mBw.getTetherStats(&cli, filter, err));
    ASSERT_EQ(expected, readSocketClientResponse(socketPair[1]));
    clearIptablesRestoreOutput();

    // But if interfaces aren't paired, it's always an error.
    err = "";
    counterLines.resize(3);
    counters = android::base::Join(counterLines, "\n") + "\n";
    addIptablesRestoreOutput(counters, counters);
    ASSERT_EQ(-1, mBw.getTetherStats(&cli, filter, err));
    expectNoSocketClientResponse(socketPair[1]);
    clearIptablesRestoreOutput();

    // Token unit test of the fact that we return the stats in the error message which the caller
    // ignores.
    std::string expectedError = counters;
    EXPECT_EQ(expectedError, err);

    // popen() failing is always an error.
    addIptablesRestoreOutput(kIPv4TetherCounters);
    ASSERT_EQ(-1, mBw.getTetherStats(&cli, filter, err));
    expectNoSocketClientResponse(socketPair[1]);
    clearIptablesRestoreOutput();
    addIptablesRestoreOutput(kIPv6TetherCounters);
    ASSERT_EQ(-1, mBw.getTetherStats(&cli, filter, err));
    expectNoSocketClientResponse(socketPair[1]);
    clearIptablesRestoreOutput();
}

const std::vector<std::string> makeInterfaceQuotaCommands(const char *iface, int ruleIndex,
                                                          int64_t quota) {
    std::vector<std::string> cmds = {
        StringPrintf("-F bw_costly_%s", iface),
        StringPrintf("-N bw_costly_%s", iface),
        StringPrintf("-A bw_costly_%s -j bw_penalty_box", iface),
        StringPrintf("-D bw_INPUT -i %s --jump bw_costly_%s", iface, iface),
        StringPrintf("-I bw_INPUT %d -i %s --jump bw_costly_%s", ruleIndex, iface, iface),
        StringPrintf("-D bw_OUTPUT -o %s --jump bw_costly_%s", iface, iface),
        StringPrintf("-I bw_OUTPUT %d -o %s --jump bw_costly_%s", ruleIndex, iface, iface),
        StringPrintf("-D bw_FORWARD -o %s --jump bw_costly_%s", iface, iface),
        StringPrintf("-A bw_FORWARD -o %s --jump bw_costly_%s", iface, iface),
        StringPrintf("-A bw_costly_%s -m quota2 ! --quota %" PRIu64 " --name %s --jump REJECT",
                     iface, quota, iface),
    };
    return cmds;
}

const std::vector<std::string> removeInterfaceQuotaCommands(const char *iface) {
    std::vector<std::string> cmds = {
        StringPrintf("-D bw_INPUT -i %s --jump bw_costly_%s", iface, iface),
        StringPrintf("-D bw_OUTPUT -o %s --jump bw_costly_%s", iface, iface),
        StringPrintf("-D bw_FORWARD -o %s --jump bw_costly_%s", iface, iface),
        StringPrintf("-F bw_costly_%s", iface),
        StringPrintf("-X bw_costly_%s", iface),
    };
    return cmds;
}

TEST_F(BandwidthControllerTest, TestSetInterfaceQuota) {
    const char *iface = mTun.name().c_str();
    std::vector<std::string> expected = makeInterfaceQuotaCommands(iface, 1, 123456);

    // prepCostlyInterface assumes that exactly one of the "-F chain" and "-N chain" commands fails.
    // So pretend that the first two commands (the IPv4 -F and the IPv6 -F) fail.
    std::deque<int> returnValues(expected.size() * 2, 0);
    returnValues[0] = 1;
    returnValues[1] = 1;
    setReturnValues(returnValues);

    EXPECT_EQ(0, mBw.setInterfaceQuota(iface, 123456));
    expectIptablesCommands(expected);

    expected = removeInterfaceQuotaCommands(iface);
    EXPECT_EQ(0, mBw.removeInterfaceQuota(iface));
    expectIptablesCommands(expected);
}

TEST_F(BandwidthControllerTest, IptablesAlertCmd) {
    std::vector<std::string> expected = {
        "*filter\n"
        "-I bw_INPUT -m quota2 ! --quota 123456 --name MyWonderfulAlert\n"
        "-I bw_OUTPUT -m quota2 ! --quota 123456 --name MyWonderfulAlert\n"
        "COMMIT\n"
    };
    EXPECT_EQ(0, runIptablesAlertCmd(IptOp::IptOpInsert, "MyWonderfulAlert", 123456));
    expectIptablesRestoreCommands(expected);

    expected = {
        "*filter\n"
        "-D bw_INPUT -m quota2 ! --quota 123456 --name MyWonderfulAlert\n"
        "-D bw_OUTPUT -m quota2 ! --quota 123456 --name MyWonderfulAlert\n"
        "COMMIT\n"
    };
    EXPECT_EQ(0, runIptablesAlertCmd(IptOp::IptOpDelete, "MyWonderfulAlert", 123456));
    expectIptablesRestoreCommands(expected);
}

TEST_F(BandwidthControllerTest, IptablesAlertFwdCmd) {
    std::vector<std::string> expected = {
        "*filter\n"
        "-I bw_FORWARD -m quota2 ! --quota 123456 --name MyWonderfulAlert\n"
        "COMMIT\n"
    };
    EXPECT_EQ(0, runIptablesAlertFwdCmd(IptOp::IptOpInsert, "MyWonderfulAlert", 123456));
    expectIptablesRestoreCommands(expected);

    expected = {
        "*filter\n"
        "-D bw_FORWARD -m quota2 ! --quota 123456 --name MyWonderfulAlert\n"
        "COMMIT\n"
    };
    EXPECT_EQ(0, runIptablesAlertFwdCmd(IptOp::IptOpDelete, "MyWonderfulAlert", 123456));
    expectIptablesRestoreCommands(expected);
}

TEST_F(BandwidthControllerTest, ManipulateSpecialApps) {
    std::vector<const char *> appUids = { "1000", "1001", "10012" };

    std::vector<std::string> expected = {
        "*filter\n"
        "-I bw_happy_box -m owner --uid-owner 1000 --jump RETURN\n"
        "-I bw_happy_box -m owner --uid-owner 1001 --jump RETURN\n"
        "-I bw_happy_box -m owner --uid-owner 10012 --jump RETURN\n"
        "COMMIT\n"
    };
    EXPECT_EQ(0, mBw.addNiceApps(appUids.size(), const_cast<char**>(&appUids[0])));
    expectIptablesRestoreCommands(expected);

    expected = {
        "*filter\n"
        "-D bw_penalty_box -m owner --uid-owner 1000 --jump REJECT\n"
        "-D bw_penalty_box -m owner --uid-owner 1001 --jump REJECT\n"
        "-D bw_penalty_box -m owner --uid-owner 10012 --jump REJECT\n"
        "COMMIT\n"
    };
    EXPECT_EQ(0, mBw.removeNaughtyApps(appUids.size(), const_cast<char**>(&appUids[0])));
    expectIptablesRestoreCommands(expected);
}
