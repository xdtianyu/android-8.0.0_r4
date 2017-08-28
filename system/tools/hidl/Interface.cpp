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

#include "Interface.h"

#include "Annotation.h"
#include "ArrayType.h"
#include "ConstantExpression.h"
#include "DeathRecipientType.h"
#include "Method.h"
#include "ScalarType.h"
#include "StringType.h"
#include "VectorType.h"

#include <unistd.h>

#include <iostream>
#include <sstream>

#include <android-base/logging.h>
#include <hidl-hash/Hash.h>
#include <hidl-util/Formatter.h>
#include <hidl-util/StringHelper.h>

namespace android {

#define B_PACK_CHARS(c1, c2, c3, c4) \
         ((((c1)<<24)) | (((c2)<<16)) | (((c3)<<8)) | (c4))

/* It is very important that these values NEVER change. These values
 * must remain unchanged over the lifetime of android. This is
 * because the framework on a device will be updated independently of
 * the hals on a device. If the hals are compiled with one set of
 * transaction values, and the framework with another, then the
 * interface between them will be destroyed, and the device will not
 * work.
 */
enum {
    // These values are defined in hardware::IBinder.
    /////////////////// User defined transactions
    FIRST_CALL_TRANSACTION  = 0x00000001,
    LAST_CALL_TRANSACTION   = 0x0effffff,
    /////////////////// HIDL reserved
    FIRST_HIDL_TRANSACTION  = 0x0f000000,
    HIDL_PING_TRANSACTION                     = B_PACK_CHARS(0x0f, 'P', 'N', 'G'),
    HIDL_DESCRIPTOR_CHAIN_TRANSACTION         = B_PACK_CHARS(0x0f, 'C', 'H', 'N'),
    HIDL_GET_DESCRIPTOR_TRANSACTION           = B_PACK_CHARS(0x0f, 'D', 'S', 'C'),
    HIDL_SYSPROPS_CHANGED_TRANSACTION         = B_PACK_CHARS(0x0f, 'S', 'Y', 'S'),
    HIDL_LINK_TO_DEATH_TRANSACTION            = B_PACK_CHARS(0x0f, 'L', 'T', 'D'),
    HIDL_UNLINK_TO_DEATH_TRANSACTION          = B_PACK_CHARS(0x0f, 'U', 'T', 'D'),
    HIDL_SET_HAL_INSTRUMENTATION_TRANSACTION  = B_PACK_CHARS(0x0f, 'I', 'N', 'T'),
    HIDL_GET_REF_INFO_TRANSACTION             = B_PACK_CHARS(0x0f, 'R', 'E', 'F'),
    HIDL_DEBUG_TRANSACTION                    = B_PACK_CHARS(0x0f, 'D', 'B', 'G'),
    HIDL_HASH_CHAIN_TRANSACTION               = B_PACK_CHARS(0x0f, 'H', 'S', 'H'),
    LAST_HIDL_TRANSACTION   = 0x0fffffff,
};

Interface::Interface(const char *localName, const Location &location, Interface *super)
    : Scope(localName, location),
      mSuperType(super),
      mIsJavaCompatibleInProgress(false) {
}

std::string Interface::typeName() const {
    return "interface " + localName();
}

bool Interface::fillPingMethod(Method *method) const {
    if (method->name() != "ping") {
        return false;
    }

    method->fillImplementation(
        HIDL_PING_TRANSACTION,
        {
            {IMPL_INTERFACE,
                [](auto &out) {
                    out << "return ::android::hardware::Void();\n";
                }
            },
            {IMPL_STUB_IMPL,
                [](auto &out) {
                    out << "return ::android::hardware::Void();\n";
                }
            }
        }, /*cppImpl*/
        {
            {IMPL_INTERFACE,
                [this](auto &out) {
                    out << "return;\n";
                }
            },
            {IMPL_STUB, nullptr /* don't generate code */}
        } /*javaImpl*/
    );

    return true;
}

bool Interface::fillLinkToDeathMethod(Method *method) const {
    if (method->name() != "linkToDeath") {
        return false;
    }

    method->fillImplementation(
            HIDL_LINK_TO_DEATH_TRANSACTION,
            {
                {IMPL_INTERFACE,
                    [](auto &out) {
                        out << "(void)cookie;\n"
                            << "return (recipient != nullptr);\n";
                    }
                },
                {IMPL_PROXY,
                    [](auto &out) {
                        out << "::android::hardware::ProcessState::self()->startThreadPool();\n";
                        out << "::android::hardware::hidl_binder_death_recipient *binder_recipient"
                            << " = new ::android::hardware::hidl_binder_death_recipient(recipient, cookie, this);\n"
                            << "std::unique_lock<std::mutex> lock(_hidl_mMutex);\n"
                            << "_hidl_mDeathRecipients.push_back(binder_recipient);\n"
                            << "return (remote()->linkToDeath(binder_recipient)"
                            << " == ::android::OK);\n";
                    }
                },
                {IMPL_STUB, nullptr}
            }, /*cppImpl*/
            {
                {IMPL_INTERFACE,
                    [this](auto &out) {
                        out << "return true;";
                    }
                },
                {IMPL_PROXY,
                    [this](auto &out) {
                        out << "return mRemote.linkToDeath(recipient, cookie);\n";
                    }
                },
                {IMPL_STUB, nullptr}
            } /*javaImpl*/
    );
    return true;
}

bool Interface::fillUnlinkToDeathMethod(Method *method) const {
    if (method->name() != "unlinkToDeath") {
        return false;
    }

    method->fillImplementation(
            HIDL_UNLINK_TO_DEATH_TRANSACTION,
            {
                {IMPL_INTERFACE,
                    [](auto &out) {
                        out << "return (recipient != nullptr);\n";
                    }
                },
                {IMPL_PROXY,
                    [](auto &out) {
                        out << "std::unique_lock<std::mutex> lock(_hidl_mMutex);\n"
                            << "for (auto it = _hidl_mDeathRecipients.begin();"
                            << "it != _hidl_mDeathRecipients.end();"
                            << "++it) {\n";
                        out.indent([&] {
                            out.sIf("(*it)->getRecipient() == recipient", [&] {
                                out << "::android::status_t status = remote()->unlinkToDeath(*it);\n"
                                    << "_hidl_mDeathRecipients.erase(it);\n"
                                    << "return status == ::android::OK;\n";
                                });
                            });
                        out << "}\n";
                        out << "return false;\n";
                    }
                },
                {IMPL_STUB, nullptr /* don't generate code */}
            }, /*cppImpl*/
            {
                {IMPL_INTERFACE,
                    [this](auto &out) {
                        out << "return true;\n";
                    }
                },
                {IMPL_PROXY,
                    [this](auto &out) {
                        out << "return mRemote.unlinkToDeath(recipient);\n";
                    }
                },
                {IMPL_STUB, nullptr /* don't generate code */}
            } /*javaImpl*/
    );
    return true;
}
bool Interface::fillSyspropsChangedMethod(Method *method) const {
    if (method->name() != "notifySyspropsChanged") {
        return false;
    }

    method->fillImplementation(
            HIDL_SYSPROPS_CHANGED_TRANSACTION,
            { { IMPL_INTERFACE, [this](auto &out) {
                out << "::android::report_sysprop_change();\n";
                out << "return ::android::hardware::Void();";
            } } }, /*cppImpl */
            { { IMPL_INTERFACE, [](auto &out) { /* javaImpl */
                out << "android.os.SystemProperties.reportSyspropChanged();";
            } } } /*javaImpl */
    );
    return true;
}

bool Interface::fillSetHALInstrumentationMethod(Method *method) const {
    if (method->name() != "setHALInstrumentation") {
        return false;
    }

    method->fillImplementation(
            HIDL_SET_HAL_INSTRUMENTATION_TRANSACTION,
            {
                {IMPL_INTERFACE,
                    [this](auto &out) {
                        // do nothing for base class.
                        out << "return ::android::hardware::Void();\n";
                    }
                },
                {IMPL_STUB,
                    [](auto &out) {
                        out << "configureInstrumentation();\n";
                    }
                },
                {IMPL_PASSTHROUGH,
                    [](auto &out) {
                        out << "configureInstrumentation();\n";
                        out << "return ::android::hardware::Void();\n";
                    }
                },
            }, /*cppImpl */
            { { IMPL_INTERFACE, [](auto & /*out*/) { /* javaImpl */
                // Not support for Java Impl for now.
            } } } /*javaImpl */
    );
    return true;
}

bool Interface::fillDescriptorChainMethod(Method *method) const {
    if (method->name() != "interfaceChain") {
        return false;
    }

    method->fillImplementation(
        HIDL_DESCRIPTOR_CHAIN_TRANSACTION,
        { { IMPL_INTERFACE, [this](auto &out) {
            std::vector<const Interface *> chain = typeChain();
            out << "_hidl_cb(";
            out.block([&] {
                for (const Interface *iface : chain) {
                    out << iface->fullName() << "::descriptor,\n";
                }
            });
            out << ");\n";
            out << "return ::android::hardware::Void();";
        } } }, /* cppImpl */
        { { IMPL_INTERFACE, [this](auto &out) {
            std::vector<const Interface *> chain = typeChain();
            out << "return new java.util.ArrayList<String>(java.util.Arrays.asList(\n";
            out.indent(); out.indent();
            for (size_t i = 0; i < chain.size(); ++i) {
                if (i != 0)
                    out << ",\n";
                out << chain[i]->fullJavaName() << ".kInterfaceName";
            }
            out << "));";
            out.unindent(); out.unindent();
        } } } /* javaImpl */
    );
    return true;
}

static void emitDigestChain(
        Formatter &out,
        const std::string &prefix,
        const std::vector<const Interface *> &chain,
        std::function<std::string(const ConstantExpression &)> byteToString) {
    out.join(chain.begin(), chain.end(), ",\n", [&] (const auto &iface) {
        const Hash &hash = Hash::getHash(iface->location().begin().filename());
        out << prefix;
        out << "{";
        out.join(hash.raw().begin(), hash.raw().end(), ",", [&](const auto &e) {
            // Use ConstantExpression::cppValue / javaValue
            // because Java used signed byte for uint8_t.
            out << byteToString(ConstantExpression::ValueOf(ScalarType::Kind::KIND_UINT8, e));
        });
        out << "} /* ";
        out << hash.hexString();
        out << " */";
    });
}

bool Interface::fillHashChainMethod(Method *method) const {
    if (method->name() != "getHashChain") {
        return false;
    }
    const VectorType *chainType = static_cast<const VectorType *>(&method->results()[0]->type());
    const ArrayType *digestType = static_cast<const ArrayType *>(chainType->getElementType());

    method->fillImplementation(
        HIDL_HASH_CHAIN_TRANSACTION,
        { { IMPL_INTERFACE, [this, digestType](auto &out) {
            std::vector<const Interface *> chain = typeChain();
            out << "_hidl_cb(";
            out.block([&] {
                emitDigestChain(out, "(" + digestType->getInternalDataCppType() + ")",
                    chain, [](const auto &e){return e.cppValue();});
            });
            out << ");\n";
            out << "return ::android::hardware::Void();\n";
        } } }, /* cppImpl */
        { { IMPL_INTERFACE, [this, digestType, chainType](auto &out) {
            std::vector<const Interface *> chain = typeChain();
            out << "return new "
                << chainType->getJavaType(false /* forInitializer */)
                << "(java.util.Arrays.asList(\n";
            out.indent(2, [&] {
                // No need for dimensions when elements are explicitly provided.
                emitDigestChain(out, "new " + digestType->getJavaType(false /* forInitializer */),
                    chain, [](const auto &e){return e.javaValue();});
            });
            out << "));\n";
        } } } /* javaImpl */
    );
    return true;
}

bool Interface::fillGetDescriptorMethod(Method *method) const {
    if (method->name() != "interfaceDescriptor") {
        return false;
    }

    method->fillImplementation(
        HIDL_GET_DESCRIPTOR_TRANSACTION,
        { { IMPL_INTERFACE, [this](auto &out) {
            out << "_hidl_cb("
                << fullName()
                << "::descriptor);\n"
                << "return ::android::hardware::Void();";
        } } }, /* cppImpl */
        { { IMPL_INTERFACE, [this](auto &out) {
            out << "return "
                << fullJavaName()
                << ".kInterfaceName;\n";
        } } } /* javaImpl */
    );
    return true;
}

bool Interface::fillGetDebugInfoMethod(Method *method) const {
    if (method->name() != "getDebugInfo") {
        return false;
    }

    static const std::string sArch =
            "#if defined(__LP64__)\n"
            "::android::hidl::base::V1_0::DebugInfo::Architecture::IS_64BIT\n"
            "#else\n"
            "::android::hidl::base::V1_0::DebugInfo::Architecture::IS_32BIT\n"
            "#endif\n";

    method->fillImplementation(
        HIDL_GET_REF_INFO_TRANSACTION,
        {
            {IMPL_INTERFACE,
                [this](auto &out) {
                    // getDebugInfo returns N/A for local objects.
                    out << "_hidl_cb({ -1 /* pid */, 0 /* ptr */, \n"
                        << sArch
                        << "});\n"
                        << "return ::android::hardware::Void();";
                }
            },
            {IMPL_STUB_IMPL,
                [this](auto &out) {
                    out << "_hidl_cb(";
                    out.block([&] {
                        out << "::android::hardware::details::debuggable()"
                            << "? getpid() : -1 /* pid */,\n"
                            << "::android::hardware::details::debuggable()"
                            << "? reinterpret_cast<uint64_t>(this) : 0 /* ptr */,\n"
                            << sArch << "\n";
                    });
                    out << ");\n"
                        << "return ::android::hardware::Void();";
                }
            }
        }, /* cppImpl */
        { { IMPL_INTERFACE, [this, method](auto &out) {
            const Type &refInfo = method->results().front()->type();
            out << refInfo.getJavaType(false /* forInitializer */) << " info = new "
                << refInfo.getJavaType(true /* forInitializer */) << "();\n"
                // TODO(b/34777099): PID for java.
                << "info.pid = -1;\n"
                << "info.ptr = 0;\n"
                << "info.arch = android.hidl.base.V1_0.DebugInfo.Architecture.UNKNOWN;"
                << "return info;";
        } } } /* javaImpl */
    );

    return true;
}

bool Interface::fillDebugMethod(Method *method) const {
    if (method->name() != "debug") {
        return false;
    }

    method->fillImplementation(
        HIDL_DEBUG_TRANSACTION,
        {
            {IMPL_INTERFACE,
                [this](auto &out) {
                    out << "(void)fd;\n"
                        << "(void)options;\n"
                        << "return ::android::hardware::Void();";
                }
            },
        }, /* cppImpl */
        {
            /* unused, as the debug method is hidden from Java */
        } /* javaImpl */
    );

    return true;
}

static std::map<std::string, Method *> gAllReservedMethods;

bool Interface::addMethod(Method *method) {
    if (isIBase()) {
        if (!gAllReservedMethods.emplace(method->name(), method).second) {
            LOG(ERROR) << "ERROR: hidl-gen encountered duplicated reserved method "
                       << method->name();
            return false;
        }
        // will add it in addAllReservedMethods
        return true;
    }

    CHECK(!method->isHidlReserved());
    if (lookupMethod(method->name()) != nullptr) {
        LOG(ERROR) << "Redefinition of method " << method->name();
        return false;
    }
    size_t serial = FIRST_CALL_TRANSACTION;

    serial += userDefinedMethods().size();

    const Interface *ancestor = mSuperType;
    while (ancestor != nullptr) {
        serial += ancestor->userDefinedMethods().size();
        ancestor = ancestor->superType();
    }

    CHECK(serial <= LAST_CALL_TRANSACTION) << "More than "
            << LAST_CALL_TRANSACTION << " methods are not allowed.";
    method->setSerialId(serial);
    mUserMethods.push_back(method);

    return true;
}

bool Interface::addAllReservedMethods() {
    // use a sorted map to insert them in serial ID order.
    std::map<int32_t, Method *> reservedMethodsById;
    for (const auto &pair : gAllReservedMethods) {
        Method *method = pair.second->copySignature();
        bool fillSuccess = fillPingMethod(method)
            || fillDescriptorChainMethod(method)
            || fillGetDescriptorMethod(method)
            || fillHashChainMethod(method)
            || fillSyspropsChangedMethod(method)
            || fillLinkToDeathMethod(method)
            || fillUnlinkToDeathMethod(method)
            || fillSetHALInstrumentationMethod(method)
            || fillGetDebugInfoMethod(method)
            || fillDebugMethod(method);

        if (!fillSuccess) {
            LOG(ERROR) << "ERROR: hidl-gen does not recognize a reserved method "
                       << method->name();
            return false;
        }
        if (!reservedMethodsById.emplace(method->getSerialId(), method).second) {
            LOG(ERROR) << "ERROR: hidl-gen uses duplicated serial id for "
                       << method->name() << " and "
                       << reservedMethodsById[method->getSerialId()]->name()
                       << ", serialId = " << method->getSerialId();
            return false;
        }
    }
    for (const auto &pair : reservedMethodsById) {
        this->mReservedMethods.push_back(pair.second);
    }
    return true;
}

const Interface *Interface::superType() const {
    return mSuperType;
}

std::vector<const Interface *> Interface::typeChain() const {
    std::vector<const Interface *> v;
    const Interface *iface = this;
    while (iface != nullptr) {
        v.push_back(iface);
        iface = iface->mSuperType;
    }
    return v;
}

std::vector<const Interface *> Interface::superTypeChain() const {
    return superType()->typeChain(); // should work even if superType is nullptr
}

bool Interface::isElidableType() const {
    return true;
}

bool Interface::isInterface() const {
    return true;
}

bool Interface::isBinder() const {
    return true;
}

const std::vector<Method *> &Interface::userDefinedMethods() const {
    return mUserMethods;
}

const std::vector<Method *> &Interface::hidlReservedMethods() const {
    return mReservedMethods;
}

std::vector<Method *> Interface::methods() const {
    std::vector<Method *> v(mUserMethods);
    v.insert(v.end(), mReservedMethods.begin(), mReservedMethods.end());
    return v;
}

std::vector<InterfaceAndMethod> Interface::allMethodsFromRoot() const {
    std::vector<InterfaceAndMethod> v;
    std::vector<const Interface *> chain = typeChain();
    for (auto it = chain.rbegin(); it != chain.rend(); ++it) {
        const Interface *iface = *it;
        for (Method *userMethod : iface->userDefinedMethods()) {
            v.push_back(InterfaceAndMethod(iface, userMethod));
        }
    }
    for (Method *reservedMethod : hidlReservedMethods()) {
        v.push_back(InterfaceAndMethod(
                *chain.rbegin(), // IBase
                reservedMethod));
    }
    return v;
}

Method *Interface::lookupMethod(std::string name) const {
    for (const auto &tuple : allMethodsFromRoot()) {
        Method *method = tuple.method();
        if (method->name() == name) {
            return method;
        }
    }

    return nullptr;
}

std::string Interface::getBaseName() const {
    return fqName().getInterfaceBaseName();
}

std::string Interface::getProxyName() const {
    return fqName().getInterfaceProxyName();
}

std::string Interface::getStubName() const {
    return fqName().getInterfaceStubName();
}

std::string Interface::getHwName() const {
    return fqName().getInterfaceHwName();
}

std::string Interface::getPassthroughName() const {
    return fqName().getInterfacePassthroughName();
}

FQName Interface::getProxyFqName() const {
    return fqName().getInterfaceProxyFqName();
}

FQName Interface::getStubFqName() const {
    return fqName().getInterfaceStubFqName();
}

FQName Interface::getPassthroughFqName() const {
    return fqName().getInterfacePassthroughFqName();
}

std::string Interface::getCppType(StorageMode mode,
                                  bool specifyNamespaces) const {
    const std::string base =
          std::string(specifyNamespaces ? "::android::" : "")
        + "sp<"
        + (specifyNamespaces ? fullName() : partialCppName())
        + ">";

    switch (mode) {
        case StorageMode_Stack:
        case StorageMode_Result:
            return base;

        case StorageMode_Argument:
            return "const " + base + "&";
    }
}

std::string Interface::getJavaType(bool /* forInitializer */) const {
    return fullJavaName();
}

std::string Interface::getVtsType() const {
    if (StringHelper::EndsWith(localName(), "Callback")) {
        return "TYPE_HIDL_CALLBACK";
    } else {
        return "TYPE_HIDL_INTERFACE";
    }
}

void Interface::emitReaderWriter(
        Formatter &out,
        const std::string &name,
        const std::string &parcelObj,
        bool parcelObjIsPointer,
        bool isReader,
        ErrorMode mode) const {
    const std::string parcelObjDeref =
        parcelObj + (parcelObjIsPointer ? "->" : ".");

    if (isReader) {
        out << "{\n";
        out.indent();

        const std::string binderName = "_hidl_" + name + "_binder";

        out << "::android::sp<::android::hardware::IBinder> "
            << binderName << ";\n";

        out << "_hidl_err = ";
        out << parcelObjDeref
            << "readNullableStrongBinder(&"
            << binderName
            << ");\n";

        handleError(out, mode);

        out << name
            << " = "
            << "::android::hardware::fromBinder<"
            << fqName().cppName()
            << ","
            << getProxyFqName().cppName()
            << ","
            << getStubFqName().cppName()
            << ">("
            << binderName
            << ");\n";

        out.unindent();
        out << "}\n\n";
    } else {
        out << "if (" << name << " == nullptr) {\n";
        out.indent();
        out << "_hidl_err = ";
        out << parcelObjDeref
            << "writeStrongBinder(nullptr);\n";
        out.unindent();
        out << "} else {\n";
        out.indent();
        out << "::android::sp<::android::hardware::IBinder> _hidl_binder = "
            << "::android::hardware::toBinder<\n";
        out.indent(2, [&] {
            out << fqName().cppName()
                << ", "
                << getProxyFqName().cppName()
                << ">("
                << name
                << ");\n";
        });
        out << "if (_hidl_binder.get() != nullptr) {\n";
        out.indent([&] {
            out << "_hidl_err = "
                << parcelObjDeref
                << "writeStrongBinder(_hidl_binder);\n";
        });
        out << "} else {\n";
        out.indent([&] {
            out << "_hidl_err = ::android::UNKNOWN_ERROR;\n";
        });
        out << "}\n";
        out.unindent();
        out << "}\n";

        handleError(out, mode);
    }
}

status_t Interface::emitGlobalTypeDeclarations(Formatter &out) const {
    status_t status = Scope::emitGlobalTypeDeclarations(out);
    if (status != OK) {
        return status;
    }
    out << "std::string toString("
        << getCppArgumentType()
        << ");\n";
    return OK;
}


status_t Interface::emitTypeDefinitions(
        Formatter &out, const std::string prefix) const {
    std::string space = prefix.empty() ? "" : (prefix + "::");
    status_t err = Scope::emitTypeDefinitions(out, space + localName());
    if (err != OK) {
        return err;
    }

    out << "std::string toString("
        << getCppArgumentType()
        << " o) ";

    out.block([&] {
        out << "std::string os = \"[class or subclass of \";\n"
            << "os += " << fullName() << "::descriptor;\n"
            << "os += \"]\";\n"
            << "os += o->isRemote() ? \"@remote\" : \"@local\";\n"
            << "return os;\n";
    }).endl().endl();

    return OK;
}

void Interface::emitJavaReaderWriter(
        Formatter &out,
        const std::string &parcelObj,
        const std::string &argName,
        bool isReader) const {
    if (isReader) {
        out << fullJavaName()
            << ".asInterface("
            << parcelObj
            << ".readStrongBinder());\n";
    } else {
        out << parcelObj
            << ".writeStrongBinder("
            << argName
            << " == null ? null : "
            << argName
            << ".asBinder());\n";
    }
}

status_t Interface::emitVtsAttributeDeclaration(Formatter &out) const {
    for (const auto &type : getSubTypes()) {
        // Skip for TypeDef as it is just an alias of a defined type.
        if (type->isTypeDef()) {
            continue;
        }
        out << "attribute: {\n";
        out.indent();
        status_t status = type->emitVtsTypeDeclarations(out);
        if (status != OK) {
            return status;
        }
        out.unindent();
        out << "}\n\n";
    }
    return OK;
}

status_t Interface::emitVtsMethodDeclaration(Formatter &out) const {
    for (const auto &method : methods()) {
        if (method->isHidlReserved()) {
            continue;
        }

        out << "api: {\n";
        out.indent();
        out << "name: \"" << method->name() << "\"\n";
        // Generate declaration for each return value.
        for (const auto &result : method->results()) {
            out << "return_type_hidl: {\n";
            out.indent();
            status_t status = result->type().emitVtsAttributeType(out);
            if (status != OK) {
                return status;
            }
            out.unindent();
            out << "}\n";
        }
        // Generate declaration for each input argument
        for (const auto &arg : method->args()) {
            out << "arg: {\n";
            out.indent();
            status_t status = arg->type().emitVtsAttributeType(out);
            if (status != OK) {
                return status;
            }
            out.unindent();
            out << "}\n";
        }
        // Generate declaration for each annotation.
        for (const auto &annotation : method->annotations()) {
            out << "callflow: {\n";
            out.indent();
            std::string name = annotation->name();
            if (name == "entry") {
                out << "entry: true\n";
            } else if (name == "exit") {
                out << "exit: true\n";
            } else if (name == "callflow") {
                const AnnotationParam *param =
                        annotation->getParam("next");
                if (param != nullptr) {
                    for (auto value : *param->getValues()) {
                        out << "next: " << value << "\n";
                    }
                }
            } else {
                std::cerr << "Unrecognized annotation '"
                          << name << "' for method: " << method->name()
                          << ". A VTS annotation should be one of: "
                          << "entry, exit, callflow. \n";
            }
            out.unindent();
            out << "}\n";
        }
        out.unindent();
        out << "}\n\n";
    }
    return OK;
}

status_t Interface::emitVtsAttributeType(Formatter &out) const {
    out << "type: " << getVtsType() << "\n"
        << "predefined_type: \""
        << fullName()
        << "\"\n";
    return OK;
}

bool Interface::hasOnewayMethods() const {
    for (auto const &method : methods()) {
        if (method->isOneway()) {
            return true;
        }
    }

    const Interface* superClass = superType();

    if (superClass != nullptr) {
        return superClass->hasOnewayMethods();
    }

    return false;
}

bool Interface::isJavaCompatible() const {
    if (mIsJavaCompatibleInProgress) {
        // We're currently trying to determine if this Interface is
        // java-compatible and something is referencing this interface through
        // one of its methods. Assume we'll ultimately succeed, if we were wrong
        // the original invocation of Interface::isJavaCompatible() will then
        // return the correct "false" result.
        return true;
    }

    if (mSuperType != nullptr && !mSuperType->isJavaCompatible()) {
        mIsJavaCompatibleInProgress = false;
        return false;
    }

    mIsJavaCompatibleInProgress = true;

    if (!Scope::isJavaCompatible()) {
        mIsJavaCompatibleInProgress = false;
        return false;
    }

    for (const auto &method : methods()) {
        if (!method->isJavaCompatible()) {
            mIsJavaCompatibleInProgress = false;
            return false;
        }
    }

    mIsJavaCompatibleInProgress = false;

    return true;
}

}  // namespace android

