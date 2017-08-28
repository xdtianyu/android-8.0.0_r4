#ifndef DNS_RESPONDER_CLIENT_H
#define DNS_RESPONDER_CLIENT_H

#include <cutils/sockets.h>

#include <private/android_filesystem_config.h>
#include <utils/StrongPointer.h>

#include "android/net/INetd.h"
#include "binder/IServiceManager.h"
#include "NetdClient.h"
#include "dns_responder.h"
#include "resolv_params.h"

class DnsResponderClient {
public:
    struct Mapping {
        std::string host;
        std::string entry;
        std::string ip4;
        std::string ip6;
    };

    virtual ~DnsResponderClient() = default;

    void SetupMappings(unsigned num_hosts, const std::vector<std::string>& domains,
            std::vector<Mapping>* mappings);

    bool SetResolversForNetwork(const std::vector<std::string>& servers,
            const std::vector<std::string>& domains, const std::vector<int>& params);

    bool SetResolversForNetwork(const std::vector<std::string>& searchDomains,
            const std::vector<std::string>& servers, const std::string& params);

    static void SetupDNSServers(unsigned num_servers, const std::vector<Mapping>& mappings,
            std::vector<std::unique_ptr<test::DNSResponder>>* dns,
            std::vector<std::string>* servers);

    static void ShutdownDNSServers(std::vector<std::unique_ptr<test::DNSResponder>>* dns);

    static int SetupOemNetwork();

    static void TearDownOemNetwork(int oemNetId);

    virtual void SetUp();

    virtual void TearDown();

public:
    android::sp<android::net::INetd> mNetdSrv = nullptr;
    int mOemNetId = -1;
};

#endif  // DNS_RESPONDER_CLIENT_H
