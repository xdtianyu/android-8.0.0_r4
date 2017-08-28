/**
 * Copyright (c) 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef _NETD_NATIVE_SERVICE_H_
#define _NETD_NATIVE_SERVICE_H_

#include <vector>

#include <binder/BinderService.h>

#include "android/net/BnNetd.h"
#include "android/net/UidRange.h"

namespace android {
namespace net {

class NetdNativeService : public BinderService<NetdNativeService>, public BnNetd {
  public:
    static status_t start();
    static char const* getServiceName() { return "netd"; }
    virtual status_t dump(int fd, const Vector<String16> &args) override;

    binder::Status isAlive(bool *alive) override;
    binder::Status firewallReplaceUidChain(
            const String16& chainName, bool isWhitelist,
            const std::vector<int32_t>& uids, bool *ret) override;
    binder::Status bandwidthEnableDataSaver(bool enable, bool *ret) override;
    binder::Status networkRejectNonSecureVpn(bool enable, const std::vector<UidRange>& uids)
            override;
    binder::Status socketDestroy(const std::vector<UidRange>& uids,
            const std::vector<int32_t>& skipUids) override;
    binder::Status setResolverConfiguration(int32_t netId, const std::vector<std::string>& servers,
            const std::vector<std::string>& domains, const std::vector<int32_t>& params) override;
    binder::Status getResolverInfo(int32_t netId, std::vector<std::string>* servers,
            std::vector<std::string>* domains, std::vector<int32_t>* params,
            std::vector<int32_t>* stats) override;

    // Tethering-related commands.
    binder::Status tetherApplyDnsInterfaces(bool *ret) override;

    binder::Status interfaceAddAddress(const std::string &ifName,
            const std::string &addrString, int prefixLength) override;
    binder::Status interfaceDelAddress(const std::string &ifName,
            const std::string &addrString, int prefixLength) override;

    binder::Status setProcSysNet(
            int32_t family, int32_t which, const std::string &ifname, const std::string &parameter,
            const std::string &value) override;

    // Metrics reporting level set / get (internal use only).
    binder::Status getMetricsReportingLevel(int *reportingLevel) override;
    binder::Status setMetricsReportingLevel(const int reportingLevel) override;

    binder::Status ipSecAllocateSpi(
            int32_t transformId,
            int32_t direction,
            const std::string& localAddress,
            const std::string& remoteAddress,
            int32_t inSpi,
            int32_t* outSpi);

    binder::Status ipSecAddSecurityAssociation(
            int32_t transformId,
            int32_t mode,
            int32_t direction,
            const std::string& localAddress,
            const std::string& remoteAddress,
            int64_t underlyingNetworkHandle,
            int32_t spi,
            const std::string& authAlgo,
            const std::vector<uint8_t>& authKey,
            int32_t authTruncBits,
            const std::string& cryptAlgo,
            const std::vector<uint8_t>& cryptKey,
            int32_t cryptTruncBits,
            int32_t encapType,
            int32_t encapLocalPort,
            int32_t encapRemotePort,
            int32_t* allocatedSpi);

    binder::Status ipSecDeleteSecurityAssociation(
            int32_t transformId,
            int32_t direction,
            const std::string& localAddress,
            const std::string& remoteAddress,
            int32_t spi);

    binder::Status ipSecApplyTransportModeTransform(
            const android::base::unique_fd& socket,
            int32_t transformId,
            int32_t direction,
            const std::string& localAddress,
            const std::string& remoteAddress,
            int32_t spi);

    binder::Status ipSecRemoveTransportModeTransform(
            const android::base::unique_fd& socket);

};

}  // namespace net
}  // namespace android

#endif  // _NETD_NATIVE_SERVICE_H_
