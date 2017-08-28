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

#define LOG_TAG "hidl_test_servers"

#include <android-base/logging.h>

#include <android/hardware/tests/foo/1.0/BnHwSimple.h>
#include <android/hardware/tests/foo/1.0/BsSimple.h>
#include <android/hardware/tests/foo/1.0/BpHwSimple.h>
#include <android/hardware/tests/bar/1.0/IBar.h>
#include <android/hardware/tests/baz/1.0/IBaz.h>
#include <android/hardware/tests/hash/1.0/IHash.h>
#include <android/hardware/tests/inheritance/1.0/IFetcher.h>
#include <android/hardware/tests/inheritance/1.0/IParent.h>
#include <android/hardware/tests/inheritance/1.0/IChild.h>
#include <android/hardware/tests/memory/1.0/IMemoryTest.h>
#include <android/hardware/tests/pointer/1.0/IGraph.h>
#include <android/hardware/tests/pointer/1.0/IPointer.h>

#include <hidl/LegacySupport.h>

#include <hwbinder/IPCThreadState.h>

#include <sys/wait.h>
#include <signal.h>

#include <string>
#include <utility>
#include <vector>

using ::android::hardware::tests::bar::V1_0::IBar;
using ::android::hardware::tests::baz::V1_0::IBaz;
using ::android::hardware::tests::hash::V1_0::IHash;
using ::android::hardware::tests::inheritance::V1_0::IFetcher;
using ::android::hardware::tests::inheritance::V1_0::IParent;
using ::android::hardware::tests::inheritance::V1_0::IChild;
using ::android::hardware::tests::pointer::V1_0::IGraph;
using ::android::hardware::tests::pointer::V1_0::IPointer;
using ::android::hardware::tests::memory::V1_0::IMemoryTest;

using ::android::hardware::defaultPassthroughServiceImplementation;
using ::android::hardware::IPCThreadState;

static std::vector<std::pair<std::string, pid_t>> gPidList;

void signal_handler_server(int signal) {
    if (signal == SIGTERM) {
        IPCThreadState::shutdown();
        exit(0);
    }
}

template <class T>
static void forkServer(const std::string &serviceName) {
    pid_t pid;

    if ((pid = fork()) == 0) {
        // in child process
        signal(SIGTERM, signal_handler_server);
        int status = defaultPassthroughServiceImplementation<T>(serviceName);
        exit(status);
    }

    gPidList.push_back({serviceName, pid});
    return;
}

static void killServer(pid_t pid, const char *serverName) {
    if (kill(pid, SIGTERM)) {
        ALOGE("Could not kill %s; errno = %d", serverName, errno);
    } else {
        int status;
        ALOGE("Waiting for %s to exit...", serverName);
        waitpid(pid, &status, 0);
        if (status != 0) {
            ALOGE("%s terminates abnormally with status %d", serverName, status);
        } else {
            ALOGE("%s killed successfully", serverName);
        }
        ALOGE("Continuing...");
    }
}

void signal_handler(int signal) {
    if (signal == SIGTERM) {
        for (auto p : gPidList) {
            killServer(p.second, p.first.c_str());
        }
        exit(0);
    }
}

int main(int /* argc */, char* /* argv */ []) {
    setenv("TREBLE_TESTING_OVERRIDE", "true", true);

    forkServer<IMemoryTest>("memory");
    forkServer<IChild>("child");
    forkServer<IParent>("parent");
    forkServer<IFetcher>("fetcher");
    forkServer<IBar>("foo");
    forkServer<IHash>("default");
    forkServer<IBaz>("dyingBaz");
    forkServer<IGraph>("graph");
    forkServer<IPointer>("pointer");

    signal(SIGTERM, signal_handler);
    // Parent process should not exit before the forked child processes.
    pause();

    return 0;
}
