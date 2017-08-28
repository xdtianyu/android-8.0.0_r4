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

#include "dns_responder_client.h"

#include <android-base/stringprintf.h>

// TODO: make this dynamic and stop depending on implementation details.
#define TEST_OEM_NETWORK "oem29"
#define TEST_NETID 30

// TODO: move this somewhere shared.
static const char* ANDROID_DNS_MODE = "ANDROID_DNS_MODE";

// The only response code used in this class. See
// frameworks/base/services/java/com/android/server/NetworkManagementService.java
// for others.
static constexpr int ResponseCodeOK = 200;

using android::base::StringPrintf;

static int netdCommand(const char* sockname, const char* command) {
    int sock = socket_local_client(sockname,
                                   ANDROID_SOCKET_NAMESPACE_RESERVED,
                                   SOCK_STREAM);
    if (sock < 0) {
        perror("Error connecting");
        return -1;
    }

    // FrameworkListener expects the whole command in one read.
    char buffer[256];
    int nwritten = snprintf(buffer, sizeof(buffer), "0 %s", command);
    if (write(sock, buffer, nwritten + 1) < 0) {
        perror("Error sending netd command");
        close(sock);
        return -1;
    }

    int nread = read(sock, buffer, sizeof(buffer));
    if (nread < 0) {
        perror("Error reading response");
        close(sock);
        return -1;
    }
    close(sock);
    return atoi(buffer);
}

static bool expectNetdResult(int expected, const char* sockname, const char* format, ...) {
    char command[256];
    va_list args;
    va_start(args, format);
    vsnprintf(command, sizeof(command), format, args);
    va_end(args);
    int result = netdCommand(sockname, command);
    if (expected != result) {
        return false;
    }
    return (200 <= expected && expected < 300);
}

void DnsResponderClient::SetupMappings(unsigned num_hosts, const std::vector<std::string>& domains,
        std::vector<Mapping>* mappings) {
    mappings->resize(num_hosts * domains.size());
    auto mappings_it = mappings->begin();
    for (unsigned i = 0 ; i < num_hosts ; ++i) {
        for (const auto& domain : domains) {
            mappings_it->host = StringPrintf("host%u", i);
            mappings_it->entry = StringPrintf("%s.%s.", mappings_it->host.c_str(),
                    domain.c_str());
            mappings_it->ip4 = StringPrintf("192.0.2.%u", i%253 + 1);
            mappings_it->ip6 = StringPrintf("2001:db8::%x", i%65534 + 1);
            ++mappings_it;
        }
    }
}

bool DnsResponderClient::SetResolversForNetwork(const std::vector<std::string>& servers,
        const std::vector<std::string>& domains, const std::vector<int>& params) {
    auto rv = mNetdSrv->setResolverConfiguration(TEST_NETID, servers, domains, params);
    return rv.isOk();
}

bool DnsResponderClient::SetResolversForNetwork(const std::vector<std::string>& searchDomains,
            const std::vector<std::string>& servers, const std::string& params) {
    std::string cmd = StringPrintf("resolver setnetdns %d \"", mOemNetId);
    if (!searchDomains.empty()) {
        cmd += searchDomains[0].c_str();
        for (size_t i = 1 ; i < searchDomains.size() ; ++i) {
            cmd += " ";
            cmd += searchDomains[i];
        }
    }
    cmd += "\"";

    for (const auto& str : servers) {
        cmd += " ";
        cmd += str;
    }

    if (!params.empty()) {
        cmd += " --params \"";
        cmd += params;
        cmd += "\"";
    }

    int rv = netdCommand("netd", cmd.c_str());
    if (rv != ResponseCodeOK) {
        return false;
    }
    return true;
}

void DnsResponderClient::SetupDNSServers(unsigned num_servers, const std::vector<Mapping>& mappings,
        std::vector<std::unique_ptr<test::DNSResponder>>* dns,
        std::vector<std::string>* servers) {
    const char* listen_srv = "53";
    dns->resize(num_servers);
    servers->resize(num_servers);
    for (unsigned i = 0 ; i < num_servers ; ++i) {
        auto& server = (*servers)[i];
        auto& d = (*dns)[i];
        server = StringPrintf("127.0.0.%u", i + 100);
        d = std::make_unique<test::DNSResponder>(server, listen_srv, 250,
                ns_rcode::ns_r_servfail, 1.0);
        for (const auto& mapping : mappings) {
            d->addMapping(mapping.entry.c_str(), ns_type::ns_t_a, mapping.ip4.c_str());
            d->addMapping(mapping.entry.c_str(), ns_type::ns_t_aaaa, mapping.ip6.c_str());
        }
        d->startServer();
    }
}

void DnsResponderClient::ShutdownDNSServers(std::vector<std::unique_ptr<test::DNSResponder>>* dns) {
    for (const auto& d : *dns) {
        d->stopServer();
    }
    dns->clear();
}

int DnsResponderClient::SetupOemNetwork() {
    netdCommand("netd", "network destroy " TEST_OEM_NETWORK);
    if (!expectNetdResult(ResponseCodeOK, "netd",
                         "network create %s", TEST_OEM_NETWORK)) {
        return -1;
    }
    int oemNetId = TEST_NETID;
    setNetworkForProcess(oemNetId);
    if ((unsigned) oemNetId != getNetworkForProcess()) {
        return -1;
    }
    return oemNetId;
}

void DnsResponderClient::TearDownOemNetwork(int oemNetId) {
    if (oemNetId != -1) {
        expectNetdResult(ResponseCodeOK, "netd",
                         "network destroy %s", TEST_OEM_NETWORK);
    }
}

void DnsResponderClient::SetUp() {
    // Ensure resolutions go via proxy.
    setenv(ANDROID_DNS_MODE, "", 1);
    mOemNetId = SetupOemNetwork();

    // binder setup
    auto binder = android::defaultServiceManager()->getService(android::String16("netd"));
    mNetdSrv = android::interface_cast<android::net::INetd>(binder);
}

void DnsResponderClient::TearDown() {
    TearDownOemNetwork(mOemNetId);
}
