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
#ifndef _XFRM_CONTROLLER_H
#define _XFRM_CONTROLLER_H

#include <atomic>
#include <list>
#include <map>
#include <string>
#include <utility> // for pair

#include <linux/netlink.h>
#include <linux/xfrm.h>
#include <sysutils/SocketClient.h>
#include <utils/RWLock.h>

#include "NetdConstants.h"

namespace android {
namespace net {

// Suggest we avoid the smallest and largest ints
class XfrmMessage;
class TransportModeSecurityAssociation;

class XfrmSocket {
public:
    virtual void close() {
        if (mSock > 0) {
            ::close(mSock);
        }
        mSock = -1;
    }

    virtual bool open() = 0;

    virtual ~XfrmSocket() { close(); }

    virtual int sendMessage(uint16_t nlMsgType, uint16_t nlMsgFlags, uint16_t nlMsgSeqNum,
                            iovec* iov, int iovLen) const = 0;

protected:
    int mSock;
};

enum struct XfrmDirection : uint8_t {
    IN = XFRM_POLICY_IN,
    OUT = XFRM_POLICY_OUT,
    FORWARD = XFRM_POLICY_FWD,
    MASK = XFRM_POLICY_MASK,
};

enum struct XfrmMode : uint8_t {
    TRANSPORT = XFRM_MODE_TRANSPORT,
    TUNNEL = XFRM_MODE_TUNNEL,
};

struct XfrmAlgo {
    std::string name;
    std::vector<uint8_t> key;
    uint16_t truncLenBits;
};

struct XfrmSaId {
    XfrmDirection direction;
    xfrm_address_t dstAddr; // network order
    xfrm_address_t srcAddr;
    int addrFamily;  // AF_INET or AF_INET6
    int transformId; // requestId
    int spi;
};

struct XfrmSaInfo : XfrmSaId {
    XfrmAlgo auth;
    XfrmAlgo crypt;
    int netId;
    XfrmMode mode;
};

class XfrmController {
public:
    XfrmController();

    int ipSecAllocateSpi(int32_t transformId, int32_t direction, const std::string& localAddress,
                         const std::string& remoteAddress, int32_t inSpi, int32_t* outSpi);

    int ipSecAddSecurityAssociation(
        int32_t transformId, int32_t mode, int32_t direction, const std::string& localAddress,
        const std::string& remoteAddress, int64_t underlyingNetworkHandle, int32_t spi,
        const std::string& authAlgo, const std::vector<uint8_t>& authKey, int32_t authTruncBits,
        const std::string& cryptAlgo, const std::vector<uint8_t>& cryptKey, int32_t cryptTruncBits,
        int32_t encapType, int32_t encapLocalPort, int32_t encapRemotePort, int32_t* allocatedSpi);

    int ipSecDeleteSecurityAssociation(int32_t transformId, int32_t direction,
                                       const std::string& localAddress,
                                       const std::string& remoteAddress, int32_t spi);

    int ipSecApplyTransportModeTransform(const android::base::unique_fd& socket,
                                         int32_t transformId, int32_t direction,
                                         const std::string& localAddress,
                                         const std::string& remoteAddress, int32_t spi);

    int ipSecRemoveTransportModeTransform(const android::base::unique_fd& socket);

private:
    // prevent concurrent modification of XFRM
    android::RWLock mLock;

    static constexpr size_t MAX_ALGO_LENGTH = 128;

/*
 * Below is a redefinition of the xfrm_usersa_info struct that is part
 * of the Linux uapi <linux/xfrm.h> to align the structures to a 64-bit
 * boundary.
 */
#ifdef NETLINK_COMPAT32
    // Shadow the kernel definition of xfrm_usersa_info with a 64-bit aligned version
    struct xfrm_usersa_info : ::xfrm_usersa_info {
    } __attribute__((aligned(8)));
    // Shadow the kernel's version, using the aligned version of xfrm_usersa_info
    struct xfrm_userspi_info {
        struct xfrm_usersa_info info;
        __u32 min;
        __u32 max;
    };

    /*
     * Anyone who encounters a failure when sending netlink messages should look here
     * first. Hitting the static_assert() below should be a strong hint that Android
     * IPsec will probably not work with your current settings.
     *
     * Again, experimentally determined, the "flags" field should be the first byte in
     * the final word of the xfrm_usersa_info struct. The check validates the size of
     * the padding to be 7.
     *
     * This padding is verified to be correct on gcc/x86_64 kernel, and clang/x86 userspace.
     */
    static_assert(sizeof(::xfrm_usersa_info) % 8 != 0, "struct xfrm_usersa_info has changed "
                                                       "alignment. Please consider whether this "
                                                       "patch is needed.");
    static_assert(sizeof(xfrm_usersa_info) - offsetof(xfrm_usersa_info, flags) == 8,
                  "struct xfrm_usersa_info probably misaligned with kernel struct.");
    static_assert(sizeof(xfrm_usersa_info) % 8 == 0, "struct xfrm_usersa_info_t is not 64-bit  "
                                                     "aligned. Please consider whether this patch "
                                                     "is needed.");
    static_assert(sizeof(::xfrm_userspi_info) - sizeof(::xfrm_usersa_info) ==
                      sizeof(xfrm_userspi_info) - sizeof(xfrm_usersa_info),
                  "struct xfrm_userspi_info has changed and does not match the kernel struct.");
#endif

    struct nlattr_algo_crypt {
        nlattr hdr;
        xfrm_algo crypt;
        uint8_t key[MAX_ALGO_LENGTH]; // 1024 bit key, TODO: move off stack
    };

    struct nlattr_algo_auth {
        nlattr hdr;
        xfrm_algo_auth auth;
        uint8_t key[MAX_ALGO_LENGTH]; // 1024 bit key, TODO: move off stack
    };

    struct nlattr_user_tmpl {
        nlattr hdr;
        xfrm_user_tmpl tmpl;
    };

    // helper function for filling in the XfrmSaInfo structure
    static int fillXfrmSaId(int32_t direction, const std::string& localAddress,
                            const std::string& remoteAddress, int32_t spi, XfrmSaId* xfrmId);

    // Top level functions for managing a Transport Mode Transform
    static int addTransportModeTransform(const XfrmSaInfo& record);
    static int removeTransportModeTransform(const XfrmSaInfo& record);

    // TODO(messagerefactor): FACTOR OUT ALL MESSAGE BUILDING CODE BELOW HERE
    // Shared between SA and SP
    static void fillTransportModeSelector(const XfrmSaInfo& record, xfrm_selector* selector);

    // Shared between Transport and Tunnel Mode
    static int fillNlAttrXfrmAlgoEnc(const XfrmAlgo& in_algo, nlattr_algo_crypt* algo);
    static int fillNlAttrXfrmAlgoAuth(const XfrmAlgo& in_algo, nlattr_algo_auth* algo);

    // Functions for Creating a Transport Mode SA
    static int createTransportModeSecurityAssociation(const XfrmSaInfo& record,
                                                      const XfrmSocket& sock);
    static int fillUserSaInfo(const XfrmSaInfo& record, xfrm_usersa_info* usersa);

    // Functions for deleting a Transport Mode SA
    static int deleteSecurityAssociation(const XfrmSaId& record, const XfrmSocket& sock);
    static int fillUserSaId(const XfrmSaId& record, xfrm_usersa_id* said);
    static int fillUserTemplate(const XfrmSaInfo& record, xfrm_user_tmpl* tmpl);
    static int fillTransportModeUserSpInfo(const XfrmSaInfo& record, xfrm_userpolicy_info* usersp);

    static int allocateSpi(const XfrmSaInfo& record, uint32_t minSpi, uint32_t maxSpi,
                           uint32_t* outSpi, const XfrmSocket& sock);

    // END TODO(messagerefactor)
};

} // namespace net
} // namespace android

#endif /* !defined(XFRM_CONTROLLER_H) */
