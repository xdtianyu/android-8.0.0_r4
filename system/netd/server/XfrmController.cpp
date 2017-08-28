/*
 *
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

#include <string>
#include <vector>

#include <ctype.h>
#include <errno.h>
#include <fcntl.h>
#include <getopt.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <inttypes.h>

#include <arpa/inet.h>
#include <netinet/in.h>

#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/wait.h>

#include <linux/in.h>
#include <linux/netlink.h>
#include <linux/xfrm.h>

#include "android-base/stringprintf.h"
#include "android-base/strings.h"
#include "android-base/unique_fd.h"
#define LOG_TAG "XfrmController"
#include "NetdConstants.h"
#include "NetlinkCommands.h"
#include "ResponseCode.h"
#include "XfrmController.h"
#include <cutils/log.h>
#include <cutils/properties.h>
#include <logwrap/logwrap.h>

#define VDBG 1 // STOPSHIP if true

namespace android {
namespace net {

namespace {

constexpr uint32_t ALGO_MASK_AUTH_ALL = ~0;
constexpr uint32_t ALGO_MASK_CRYPT_ALL = ~0;

constexpr uint8_t REPLAY_WINDOW_SIZE = 4;

constexpr uint32_t RAND_SPI_MIN = 1;
constexpr uint32_t RAND_SPI_MAX = 0xFFFFFFFE;

constexpr uint32_t INVALID_SPI = 0;

#define XFRM_MSG_TRANS(x)                                                                          \
    case x:                                                                                        \
        return #x;

const char* xfrmMsgTypeToString(uint16_t msg) {
    switch (msg) {
        XFRM_MSG_TRANS(XFRM_MSG_NEWSA)
        XFRM_MSG_TRANS(XFRM_MSG_DELSA)
        XFRM_MSG_TRANS(XFRM_MSG_GETSA)
        XFRM_MSG_TRANS(XFRM_MSG_NEWPOLICY)
        XFRM_MSG_TRANS(XFRM_MSG_DELPOLICY)
        XFRM_MSG_TRANS(XFRM_MSG_GETPOLICY)
        XFRM_MSG_TRANS(XFRM_MSG_ALLOCSPI)
        XFRM_MSG_TRANS(XFRM_MSG_ACQUIRE)
        XFRM_MSG_TRANS(XFRM_MSG_EXPIRE)
        XFRM_MSG_TRANS(XFRM_MSG_UPDPOLICY)
        XFRM_MSG_TRANS(XFRM_MSG_UPDSA)
        XFRM_MSG_TRANS(XFRM_MSG_POLEXPIRE)
        XFRM_MSG_TRANS(XFRM_MSG_FLUSHSA)
        XFRM_MSG_TRANS(XFRM_MSG_FLUSHPOLICY)
        XFRM_MSG_TRANS(XFRM_MSG_NEWAE)
        XFRM_MSG_TRANS(XFRM_MSG_GETAE)
        XFRM_MSG_TRANS(XFRM_MSG_REPORT)
        XFRM_MSG_TRANS(XFRM_MSG_MIGRATE)
        XFRM_MSG_TRANS(XFRM_MSG_NEWSADINFO)
        XFRM_MSG_TRANS(XFRM_MSG_GETSADINFO)
        XFRM_MSG_TRANS(XFRM_MSG_GETSPDINFO)
        XFRM_MSG_TRANS(XFRM_MSG_NEWSPDINFO)
        XFRM_MSG_TRANS(XFRM_MSG_MAPPING)
        default:
            return "XFRM_MSG UNKNOWN";
    }
}

// actually const but cannot be declared as such for reasons
uint8_t kPadBytesArray[] = {0, 0, 0};
void* kPadBytes = static_cast<void*>(kPadBytesArray);

#if VDBG
#define LOG_HEX(__desc16__, __buf__, __len__) \
    do{ logHex(__desc16__, __buf__, __len__); }while(0)
#define LOG_IOV(__iov__, __iov_len__) \
    do{ logIov(__iov__, __iov_len__); }while(0)

void logHex(const char* desc16, const char* buf, size_t len) {
    char* printBuf = new char[len * 2 + 1 + 26]; // len->ascii, +newline, +prefix strlen
    int offset = 0;
    if (desc16) {
        sprintf(printBuf, "{%-16s}", desc16);
        offset += 18; // prefix string length
    }
    sprintf(printBuf + offset, "[%4.4u]: ", (len > 9999) ? 9999 : (unsigned)len);
    offset += 8;

    for (uint32_t j = 0; j < (uint32_t)len; j++) {
        sprintf(&printBuf[j * 2 + offset], "%0.2x", buf[j]);
    }
    ALOGD("%s", printBuf);
    delete[] printBuf;
}

void logIov(const iovec* iov, size_t iovLen) {
    for (uint32_t i = 0; i < (uint32_t)iovLen; i++) {
        const iovec* row = &iov[i];
        logHex(0, reinterpret_cast<char*>(row->iov_base), row->iov_len);
    }
}

#else
#define LOG_HEX(__desc16__, __buf__, __len__)
#define LOG_IOV(__iov__, __iov_len__)
#endif

class XfrmSocketImpl : public XfrmSocket {
private:
    static constexpr int NLMSG_DEFAULTSIZE = 8192;

    union NetlinkResponse {
        nlmsghdr hdr;
        struct _err_ {
            nlmsghdr hdr;
            nlmsgerr err;
        } err;

        struct _buf_ {
            nlmsghdr hdr;
            char buf[NLMSG_DEFAULTSIZE];
        } buf;
    };

public:
    virtual bool open() {
        mSock = openNetlinkSocket(NETLINK_XFRM);
        if (mSock <= 0) {
            ALOGW("Could not get a new socket, line=%d", __LINE__);
            return false;
        }

        return true;
    }

    static int validateResponse(NetlinkResponse response, size_t len) {
        if (len < sizeof(nlmsghdr)) {
            ALOGW("Invalid response message received over netlink");
            return -EBADMSG;
        }

        switch (response.hdr.nlmsg_type) {
            case NLMSG_NOOP:
            case NLMSG_DONE:
                return 0;
            case NLMSG_OVERRUN:
                ALOGD("Netlink request overran kernel buffer");
                return -EBADMSG;
            case NLMSG_ERROR:
                if (len < sizeof(NetlinkResponse::_err_)) {
                    ALOGD("Netlink message received malformed error response");
                    return -EBADMSG;
                }
                return response.err.err.error; // Netlink errors are negative errno.
            case XFRM_MSG_NEWSA:
                break;
        }

        if (response.hdr.nlmsg_type < XFRM_MSG_BASE /*== NLMSG_MIN_TYPE*/ ||
            response.hdr.nlmsg_type > XFRM_MSG_MAX) {
            ALOGD("Netlink message responded with an out-of-range message ID");
            return -EBADMSG;
        }

        // TODO Add more message validation here
        return 0;
    }

    virtual int sendMessage(uint16_t nlMsgType, uint16_t nlMsgFlags, uint16_t nlMsgSeqNum,
                            iovec* iov, int iovLen) const {
        nlmsghdr nlMsg = {
            .nlmsg_type = nlMsgType, .nlmsg_flags = nlMsgFlags, .nlmsg_seq = nlMsgSeqNum,
        };

        iov[0].iov_base = &nlMsg;
        iov[0].iov_len = NLMSG_HDRLEN;
        for (int i = 0; i < iovLen; ++i) {
            nlMsg.nlmsg_len += iov[i].iov_len;
        }

        ALOGD("Sending Netlink XFRM Message: %s", xfrmMsgTypeToString(nlMsgType));
        if (VDBG)
            LOG_IOV(iov, iovLen);

        int ret;

        if (writev(mSock, iov, iovLen) < 0) {
            ALOGE("netlink socket writev failed (%s)", strerror(errno));
            return -errno;
        }

        NetlinkResponse* response = new NetlinkResponse{};

        if ((ret = recv(mSock, response, sizeof(*response), 0)) < 0) {
            ALOGE("netlink response contains error (%s)", strerror(errno));
            delete response;
            return -errno;
        }

        LOG_HEX("netlink msg resp", reinterpret_cast<char*>(response), ret);

        ret = validateResponse(*response, ret);
        delete response;
        if (ret < 0)
            ALOGE("netlink response contains error (%s)", strerror(-ret));
        return ret;
    }
};

int convertToXfrmAddr(const std::string& strAddr, xfrm_address_t* xfrmAddr) {
    if (strAddr.length() == 0) {
        memset(xfrmAddr, 0, sizeof(*xfrmAddr));
        return AF_UNSPEC;
    }

    if (inet_pton(AF_INET6, strAddr.c_str(), reinterpret_cast<void*>(xfrmAddr))) {
        return AF_INET6;
    } else if (inet_pton(AF_INET, strAddr.c_str(), reinterpret_cast<void*>(xfrmAddr))) {
        return AF_INET;
    } else {
        return -EAFNOSUPPORT;
    }
}

void fillXfrmNlaHdr(nlattr* hdr, uint16_t type, uint16_t len) {
    hdr->nla_type = type;
    hdr->nla_len = len;
}

void fillXfrmCurLifetimeDefaults(xfrm_lifetime_cur* cur) {
    memset(reinterpret_cast<char*>(cur), 0, sizeof(*cur));
}
void fillXfrmLifetimeDefaults(xfrm_lifetime_cfg* cfg) {
    cfg->soft_byte_limit = XFRM_INF;
    cfg->hard_byte_limit = XFRM_INF;
    cfg->soft_packet_limit = XFRM_INF;
    cfg->hard_packet_limit = XFRM_INF;
}

/*
 * Allocate SPIs within an (inclusive) range of min-max.
 * returns 0 (INVALID_SPI) once the entire range has been parsed.
 */
class RandomSpi {
public:
    RandomSpi(int min, int max) : mMin(min) {
        time_t t;
        srand((unsigned int)time(&t));
        // TODO: more random random
        mNext = rand();
        mSize = max - min + 1;
        mCount = mSize;
    }

    uint32_t next() {
        if (!mCount)
            return 0;
        mCount--;
        return (mNext++ % mSize) + mMin;
    }

private:
    uint32_t mNext;
    uint32_t mSize;
    uint32_t mMin;
    uint32_t mCount;
};

} // namespace

//
// Begin XfrmController Impl
//
//
XfrmController::XfrmController(void) {}

int XfrmController::ipSecAllocateSpi(int32_t transformId, int32_t direction,
                                     const std::string& localAddress,
                                     const std::string& remoteAddress, int32_t inSpi,
                                     int32_t* outSpi) {

    ALOGD("XfrmController:%s, line=%d", __FUNCTION__, __LINE__);
    ALOGD("transformId=%d", transformId);
    ALOGD("direction=%d", direction);
    ALOGD("localAddress=%s", localAddress.c_str());
    ALOGD("remoteAddress=%s", remoteAddress.c_str());
    ALOGD("inSpi=%0.8x", inSpi);

    XfrmSaInfo saInfo{};
    int ret;

    if ((ret = fillXfrmSaId(direction, localAddress, remoteAddress, INVALID_SPI, &saInfo)) < 0) {
        return ret;
    }

    XfrmSocketImpl sock;
    if (!sock.open()) {
        ALOGD("Sock open failed for XFRM, line=%d", __LINE__);
        return -1; // TODO: return right error; for whatever reason the sock
                   // failed to open
    }

    int minSpi = RAND_SPI_MIN, maxSpi = RAND_SPI_MAX;

    if (inSpi)
        minSpi = maxSpi = inSpi;
    ret = allocateSpi(saInfo, minSpi, maxSpi, reinterpret_cast<uint32_t*>(outSpi), sock);
    if (ret < 0) {
        ALOGD("Failed to Allocate an SPI, line=%d", __LINE__);
        *outSpi = INVALID_SPI;
    }

    return ret;
}

int XfrmController::ipSecAddSecurityAssociation(
    int32_t transformId, int32_t mode, int32_t direction, const std::string& localAddress,
    const std::string& remoteAddress, int64_t /* underlyingNetworkHandle */, int32_t spi,
    const std::string& authAlgo, const std::vector<uint8_t>& authKey, int32_t authTruncBits,
    const std::string& cryptAlgo, const std::vector<uint8_t>& cryptKey, int32_t cryptTruncBits,
    int32_t encapType, int32_t encapLocalPort, int32_t encapRemotePort, int32_t* allocatedSpi) {
    android::RWLock::AutoWLock lock(mLock);

    ALOGD("XfrmController::%s, line=%d", __FUNCTION__, __LINE__);
    ALOGD("transformId=%d", transformId);
    ALOGD("mode=%d", mode);
    ALOGD("direction=%d", direction);
    ALOGD("localAddress=%s", localAddress.c_str());
    ALOGD("remoteAddress=%s", remoteAddress.c_str());
    ALOGD("spi=%0.8x", spi);
    ALOGD("authAlgo=%s", authAlgo.c_str());
    ALOGD("authTruncBits=%d", authTruncBits);
    ALOGD("cryptAlgo=%s", cryptAlgo.c_str());
    ALOGD("cryptTruncBits=%d,", cryptTruncBits);
    ALOGD("encapType=%d", encapType);
    ALOGD("encapLocalPort=%d", encapLocalPort);
    ALOGD("encapRemotePort=%d", encapRemotePort);

    XfrmSaInfo saInfo{};
    int ret;

    if ((ret = fillXfrmSaId(direction, localAddress, remoteAddress, spi, &saInfo)) < 0) {
        return ret;
    }

    saInfo.transformId = transformId;

    // STOPSHIP : range check the key lengths to prevent puncturing and overflow
    saInfo.auth = XfrmAlgo{
        .name = authAlgo, .key = authKey, .truncLenBits = static_cast<uint16_t>(authTruncBits)};

    saInfo.crypt = XfrmAlgo{
        .name = cryptAlgo, .key = cryptKey, .truncLenBits = static_cast<uint16_t>(cryptTruncBits)};

    saInfo.direction = static_cast<XfrmDirection>(direction);

    switch (static_cast<XfrmMode>(mode)) {
        case XfrmMode::TRANSPORT:
        case XfrmMode::TUNNEL:
            saInfo.mode = static_cast<XfrmMode>(mode);
            break;
        default:
            return -EINVAL;
    }

    XfrmSocketImpl sock;
    if (!sock.open()) {
        ALOGD("Sock open failed for XFRM, line=%d", __LINE__);
        return -1; // TODO: return right error; for whatever reason the sock
                   // failed to open
    }

    ret = createTransportModeSecurityAssociation(saInfo, sock);
    if (ret < 0) {
        ALOGD("Failed creating a Security Association, line=%d", __LINE__);
        return ret; // something went wrong creating the SA
    }

    *allocatedSpi = spi;
    return 0;
}

int XfrmController::ipSecDeleteSecurityAssociation(int32_t transformId, int32_t direction,
                                                   const std::string& localAddress,
                                                   const std::string& remoteAddress, int32_t spi) {

    ALOGD("XfrmController:%s, line=%d", __FUNCTION__, __LINE__);
    ALOGD("transformId=%d", transformId);
    ALOGD("direction=%d", direction);
    ALOGD("localAddress=%s", localAddress.c_str());
    ALOGD("remoteAddress=%s", remoteAddress.c_str());
    ALOGD("spi=%0.8x", spi);

    XfrmSaId saId;
    int ret;

    if ((ret = fillXfrmSaId(direction, localAddress, remoteAddress, spi, &saId)) < 0) {
        return ret;
    }

    XfrmSocketImpl sock;
    if (!sock.open()) {
        ALOGD("Sock open failed for XFRM, line=%d", __LINE__);
        return -1; // TODO: return right error; for whatever reason the sock
                   // failed to open
    }

    ret = deleteSecurityAssociation(saId, sock);
    if (ret < 0) {
        ALOGD("Failed to delete Security Association, line=%d", __LINE__);
        return ret; // something went wrong deleting the SA
    }

    return ret;
}

int XfrmController::fillXfrmSaId(int32_t direction, const std::string& localAddress,
                                 const std::string& remoteAddress, int32_t spi, XfrmSaId* xfrmId) {
    xfrm_address_t localXfrmAddr{}, remoteXfrmAddr{};

    int addrFamilyLocal, addrFamilyRemote;
    addrFamilyRemote = convertToXfrmAddr(remoteAddress, &remoteXfrmAddr);
    addrFamilyLocal = convertToXfrmAddr(localAddress, &localXfrmAddr);
    if (addrFamilyRemote < 0 || addrFamilyLocal < 0) {
        return -EINVAL;
    }

    if (addrFamilyRemote == AF_UNSPEC ||
        (addrFamilyLocal != AF_UNSPEC && addrFamilyLocal != addrFamilyRemote)) {
        ALOGD("Invalid or Mismatched Address Families, %d != %d, line=%d", addrFamilyLocal,
              addrFamilyRemote, __LINE__);
        return -EINVAL;
    }

    xfrmId->addrFamily = addrFamilyRemote;

    xfrmId->spi = htonl(spi);

    switch (static_cast<XfrmDirection>(direction)) {
        case XfrmDirection::IN:
            xfrmId->dstAddr = localXfrmAddr;
            xfrmId->srcAddr = remoteXfrmAddr;
            break;

        case XfrmDirection::OUT:
            xfrmId->dstAddr = remoteXfrmAddr;
            xfrmId->srcAddr = localXfrmAddr;
            break;

        default:
            ALOGD("Invalid XFRM direction, line=%d", __LINE__);
            // Invalid direction for Transport mode transform: time to bail
            return -EINVAL;
    }
    return 0;
}

int XfrmController::ipSecApplyTransportModeTransform(const android::base::unique_fd& socket,
                                                     int32_t transformId, int32_t direction,
                                                     const std::string& localAddress,
                                                     const std::string& remoteAddress,
                                                     int32_t spi) {
    ALOGD("XfrmController::%s, line=%d", __FUNCTION__, __LINE__);
    ALOGD("transformId=%d", transformId);
    ALOGD("direction=%d", direction);
    ALOGD("localAddress=%s", localAddress.c_str());
    ALOGD("remoteAddress=%s", remoteAddress.c_str());
    ALOGD("spi=%0.8x", spi);

    struct sockaddr_storage saddr;

    socklen_t len = sizeof(saddr);
    int err;
    int userSocket = socket.get();

    if ((err = getsockname(userSocket, reinterpret_cast<struct sockaddr*>(&saddr), &len)) < 0) {
        ALOGE("Failed to get socket info in %s", __FUNCTION__);
        return -err;
    }

    XfrmSaInfo saInfo{};
    saInfo.transformId = transformId;
    saInfo.direction = static_cast<XfrmDirection>(direction);
    saInfo.spi = spi;

    if ((err = fillXfrmSaId(direction, localAddress, remoteAddress, spi, &saInfo)) < 0) {
        ALOGE("Couldn't build SA ID %s", __FUNCTION__);
        return -err;
    }

    if (saInfo.addrFamily != saddr.ss_family) {
        ALOGE("Transform address family(%d) differs from socket address "
                "family(%d)!",
                saInfo.addrFamily, saddr.ss_family);
        return -EINVAL;
    }

    struct {
        xfrm_userpolicy_info info;
        xfrm_user_tmpl tmpl;
    } policy{};

    fillTransportModeUserSpInfo(saInfo, &policy.info);
    fillUserTemplate(saInfo, &policy.tmpl);

    LOG_HEX("XfrmUserPolicy", reinterpret_cast<char*>(&policy), sizeof(policy));

    int sockOpt, sockLayer;
    switch (saInfo.addrFamily) {
        case AF_INET:
            sockOpt = IP_XFRM_POLICY;
            sockLayer = SOL_IP;
            break;
        case AF_INET6:
            sockOpt = IPV6_XFRM_POLICY;
            sockLayer = SOL_IPV6;
            break;
        default:
            return -EAFNOSUPPORT;
    }

    err = setsockopt(userSocket, sockLayer, sockOpt, &policy, sizeof(policy));
    if (err < 0) {
        err = errno;
        ALOGE("Error setting socket option for XFRM! (%s)", strerror(err));
    }

    return -err;
}

int XfrmController::ipSecRemoveTransportModeTransform(const android::base::unique_fd& socket) {
    (void)socket;
    return 0;
}

void XfrmController::fillTransportModeSelector(const XfrmSaInfo& record, xfrm_selector* selector) {
    selector->family = record.addrFamily;
    selector->proto = AF_UNSPEC;      // TODO: do we need to match the protocol? it's
                                      // possible via the socket
    selector->ifindex = record.netId; // TODO : still need to sort this out
}

int XfrmController::createTransportModeSecurityAssociation(const XfrmSaInfo& record,
                                                           const XfrmSocket& sock) {
    xfrm_usersa_info usersa{};
    nlattr_algo_crypt crypt{};
    nlattr_algo_auth auth{};

    enum { NLMSG_HDR, USERSA, USERSA_PAD, CRYPT, CRYPT_PAD, AUTH, AUTH_PAD, iovLen };

    iovec iov[] = {
        {NULL, 0},      // reserved for the eventual addition of a NLMSG_HDR
        {&usersa, 0},   // main usersa_info struct
        {kPadBytes, 0}, // up to NLMSG_ALIGNTO pad bytes of padding
        {&crypt, 0},    // adjust size if crypt algo is present
        {kPadBytes, 0}, // up to NLATTR_ALIGNTO pad bytes
        {&auth, 0},     // adjust size if auth algo is present
        {kPadBytes, 0}, // up to NLATTR_ALIGNTO pad bytes
    };

    int len;
    len = iov[USERSA].iov_len = fillUserSaInfo(record, &usersa);
    iov[USERSA_PAD].iov_len = NLMSG_ALIGN(len) - len;

    len = iov[CRYPT].iov_len = fillNlAttrXfrmAlgoEnc(record.crypt, &crypt);
    iov[CRYPT_PAD].iov_len = NLA_ALIGN(len) - len;

    len = iov[AUTH].iov_len = fillNlAttrXfrmAlgoAuth(record.auth, &auth);
    iov[AUTH_PAD].iov_len = NLA_ALIGN(len) - len;

    return sock.sendMessage(XFRM_MSG_UPDSA, NETLINK_REQUEST_FLAGS, 0, iov, iovLen);
}

int XfrmController::fillNlAttrXfrmAlgoEnc(const XfrmAlgo& inAlgo, nlattr_algo_crypt* algo) {
    int len = NLA_HDRLEN + sizeof(xfrm_algo);
    strncpy(algo->crypt.alg_name, inAlgo.name.c_str(), sizeof(algo->crypt.alg_name));
    algo->crypt.alg_key_len = inAlgo.key.size() * 8;      // bits
    memcpy(algo->key, &inAlgo.key[0], inAlgo.key.size()); // FIXME :safety checks
    len += inAlgo.key.size();
    fillXfrmNlaHdr(&algo->hdr, XFRMA_ALG_CRYPT, len);
    return len;
}

int XfrmController::fillNlAttrXfrmAlgoAuth(const XfrmAlgo& inAlgo, nlattr_algo_auth* algo) {
    int len = NLA_HDRLEN + sizeof(xfrm_algo_auth);
    strncpy(algo->auth.alg_name, inAlgo.name.c_str(), sizeof(algo->auth.alg_name));
    algo->auth.alg_key_len = inAlgo.key.size() * 8; // bits

    // This is the extra field for ALG_AUTH_TRUNC
    algo->auth.alg_trunc_len = inAlgo.truncLenBits;

    memcpy(algo->key, &inAlgo.key[0], inAlgo.key.size()); // FIXME :safety checks
    len += inAlgo.key.size();

    fillXfrmNlaHdr(&algo->hdr, XFRMA_ALG_AUTH_TRUNC, len);
    return len;
}

int XfrmController::fillUserSaInfo(const XfrmSaInfo& record, xfrm_usersa_info* usersa) {
    fillTransportModeSelector(record, &usersa->sel);

    usersa->id.proto = IPPROTO_ESP;
    usersa->id.spi = record.spi;
    usersa->id.daddr = record.dstAddr;

    usersa->saddr = record.srcAddr;

    fillXfrmLifetimeDefaults(&usersa->lft);
    fillXfrmCurLifetimeDefaults(&usersa->curlft);
    memset(&usersa->stats, 0, sizeof(usersa->stats)); // leave stats zeroed out
    usersa->reqid = record.transformId;
    usersa->family = record.addrFamily;
    usersa->mode = static_cast<uint8_t>(record.mode);
    usersa->replay_window = REPLAY_WINDOW_SIZE;
    usersa->flags = 0; // TODO: should we actually set flags, XFRM_SA_XFLAG_DONT_ENCAP_DSCP?
    return sizeof(*usersa);
}

int XfrmController::fillUserSaId(const XfrmSaId& record, xfrm_usersa_id* said) {
    said->daddr = record.dstAddr;
    said->spi = record.spi;
    said->family = record.addrFamily;
    said->proto = IPPROTO_ESP;

    return sizeof(*said);
}

int XfrmController::deleteSecurityAssociation(const XfrmSaId& record, const XfrmSocket& sock) {
    xfrm_usersa_id said{};

    enum { NLMSG_HDR, USERSAID, USERSAID_PAD, iovLen };

    iovec iov[] = {
        {NULL, 0},      // reserved for the eventual addition of a NLMSG_HDR
        {&said, 0},     // main usersa_info struct
        {kPadBytes, 0}, // up to NLMSG_ALIGNTO pad bytes of padding
    };

    int len;
    len = iov[USERSAID].iov_len = fillUserSaId(record, &said);
    iov[USERSAID_PAD].iov_len = NLMSG_ALIGN(len) - len;

    return sock.sendMessage(XFRM_MSG_DELSA, NETLINK_REQUEST_FLAGS, 0, iov, iovLen);
}

int XfrmController::allocateSpi(const XfrmSaInfo& record, uint32_t minSpi, uint32_t maxSpi,
                                uint32_t* outSpi, const XfrmSocket& sock) {
    xfrm_userspi_info spiInfo{};

    enum { NLMSG_HDR, USERSAID, USERSAID_PAD, iovLen };

    iovec iov[] = {
        {NULL, 0},      // reserved for the eventual addition of a NLMSG_HDR
        {&spiInfo, 0},  // main userspi_info struct
        {kPadBytes, 0}, // up to NLMSG_ALIGNTO pad bytes of padding
    };

    int len;
    if (fillUserSaInfo(record, &spiInfo.info) == 0) {
        ALOGE("Failed to fill transport SA Info");
    }

    len = iov[USERSAID].iov_len = sizeof(spiInfo);
    iov[USERSAID_PAD].iov_len = NLMSG_ALIGN(len) - len;

    RandomSpi spiGen = RandomSpi(minSpi, maxSpi);
    int spi, ret;
    while ((spi = spiGen.next()) != INVALID_SPI) {
        spiInfo.min = spi;
        spiInfo.max = spi;
        ret = sock.sendMessage(XFRM_MSG_ALLOCSPI, NETLINK_REQUEST_FLAGS, 0, iov, iovLen);

        /* If the SPI is in use, we'll get ENOENT */
        if (ret == -ENOENT)
            continue;

        if (ret == 0) {
            *outSpi = spi;
            ALOGD("Allocated an SPI: %d", *outSpi);
        } else {
            *outSpi = INVALID_SPI;
            ALOGE("SPI Allocation Failed with error %d", ret);
        }

        return ret;
    }

    // Should always be -ENOENT if we get here
    return ret;
}

int XfrmController::fillTransportModeUserSpInfo(const XfrmSaInfo& record,
                                                xfrm_userpolicy_info* usersp) {
    fillTransportModeSelector(record, &usersp->sel);
    fillXfrmLifetimeDefaults(&usersp->lft);
    fillXfrmCurLifetimeDefaults(&usersp->curlft);
    /* if (index) index & 0x3 == dir -- must be true xfrm_user.c:verify_newpolicy_info() */
    usersp->index = 0;
    usersp->dir = static_cast<uint8_t>(record.direction);
    usersp->action = XFRM_POLICY_ALLOW;
    usersp->flags = XFRM_POLICY_LOCALOK;
    usersp->share = XFRM_SHARE_UNIQUE;
    return sizeof(*usersp);
}

int XfrmController::fillUserTemplate(const XfrmSaInfo& record, xfrm_user_tmpl* tmpl) {
    tmpl->id.daddr = record.dstAddr;
    tmpl->id.spi = record.spi;
    tmpl->id.proto = IPPROTO_ESP;

    tmpl->family = record.addrFamily;
    tmpl->saddr = record.srcAddr;
    tmpl->reqid = record.transformId;
    tmpl->mode = static_cast<uint8_t>(record.mode);
    tmpl->share = XFRM_SHARE_UNIQUE;
    tmpl->optional = 0; // if this is true, then a failed state lookup will be considered OK:
                        // http://lxr.free-electrons.com/source/net/xfrm/xfrm_policy.c#L1492
    tmpl->aalgos = ALGO_MASK_AUTH_ALL;  // TODO: if there's a bitmask somewhere of
                                        // algos, we should find it and apply it.
                                        // I can't find one.
    tmpl->ealgos = ALGO_MASK_CRYPT_ALL; // TODO: if there's a bitmask somewhere...
    return 0;
}

} // namespace net
} // namespace android
