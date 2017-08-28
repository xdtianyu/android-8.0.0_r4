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

#include "AST.h"

#include "Coordinator.h"
#include "Interface.h"
#include "Method.h"
#include "Scope.h"

#include <hidl-util/Formatter.h>
#include <android-base/logging.h>

namespace android {

void AST::emitJavaReaderWriter(
        Formatter &out,
        const std::string &parcelObj,
        const TypedVar *arg,
        bool isReader,
        bool addPrefixToName) const {
    if (isReader) {
        out << arg->type().getJavaType()
            << " "
            << (addPrefixToName ? "_hidl_out_" : "")
            << arg->name()
            << " = ";
    }

    arg->type().emitJavaReaderWriter(out, parcelObj,
            (addPrefixToName ? "_hidl_out_" : "") + arg->name(),
            isReader);
}

status_t AST::generateJavaTypes(
        const std::string &outputPath, const std::string &limitToType) const {
    // Splits types.hal up into one java file per declared type.

    for (const auto &type : mRootScope->getSubTypes()) {
        std::string typeName = type->localName();

        if (type->isTypeDef()) {
            continue;
        }

        if (!limitToType.empty() && typeName != limitToType) {
            continue;
        }

        std::string path = outputPath;
        path.append(mCoordinator->convertPackageRootToPath(mPackage));
        path.append(mCoordinator->getPackagePath(mPackage, true /* relative */,
                true /* sanitized */));
        path.append(typeName);
        path.append(".java");

        CHECK(Coordinator::MakeParentHierarchy(path));
        FILE *file = fopen(path.c_str(), "w");

        if (file == NULL) {
            return -errno;
        }

        Formatter out(file);

        std::vector<std::string> packageComponents;
        getPackageAndVersionComponents(
                &packageComponents, true /* cpp_compatible */);

        out << "package " << mPackage.javaPackage() << ";\n\n";

        out << "\n";

        status_t err =
            type->emitJavaTypeDeclarations(out, true /* atTopLevel */);

        if (err != OK) {
            return err;
        }
    }

    return OK;
}

status_t AST::generateJava(
        const std::string &outputPath, const std::string &limitToType) const {
    if (!isJavaCompatible()) {
        fprintf(stderr,
                "ERROR: This interface is not Java compatible. The Java backend"
                " does NOT support union types nor native handles. "
                "In addition, vectors of arrays are limited to at most "
                "one-dimensional arrays and vectors of {vectors,interfaces} are"
                " not supported.\n");

        return UNKNOWN_ERROR;
    }

    std::string ifaceName;
    if (!AST::isInterface(&ifaceName)) {
        return generateJavaTypes(outputPath, limitToType);
    }

    const Interface *iface = mRootScope->getInterface();

    const std::string baseName = iface->getBaseName();

    std::string path = outputPath;
    path.append(mCoordinator->convertPackageRootToPath(mPackage));
    path.append(mCoordinator->getPackagePath(mPackage, true /* relative */,
            true /* sanitized */));
    path.append(ifaceName);
    path.append(".java");

    CHECK(Coordinator::MakeParentHierarchy(path));
    FILE *file = fopen(path.c_str(), "w");

    if (file == NULL) {
        return -errno;
    }

    Formatter out(file);

    std::vector<std::string> packageComponents;
    getPackageAndVersionComponents(
            &packageComponents, true /* cpp_compatible */);

    out << "package " << mPackage.javaPackage() << ";\n\n";

    out.setNamespace(mPackage.javaPackage() + ".");

    const Interface *superType = iface->superType();

    out << "public interface " << ifaceName << " extends ";

    if (superType != NULL) {
        out << superType->fullJavaName();
    } else {
        out << "android.os.IHwInterface";
    }

    out << " {\n";
    out.indent();

    out << "public static final String kInterfaceName = \""
        << mPackage.string()
        << "::"
        << ifaceName
        << "\";\n\n";

    out << "/* package private */ static "
        << ifaceName
        << " asInterface(android.os.IHwBinder binder) {\n";

    out.indent();

    out << "if (binder == null) {\n";
    out.indent();
    out << "return null;\n";
    out.unindent();
    out << "}\n\n";

    out << "android.os.IHwInterface iface =\n";
    out.indent();
    out.indent();
    out << "binder.queryLocalInterface(kInterfaceName);\n\n";
    out.unindent();
    out.unindent();

    out << "if ((iface != null) && (iface instanceof "
        << ifaceName
        << ")) {\n";

    out.indent();
    out << "return (" << ifaceName << ")iface;\n";
    out.unindent();
    out << "}\n\n";

    out << ifaceName << " proxy = new " << ifaceName << ".Proxy(binder);\n\n";
    out << "try {\n";
    out.indent();
    out << "for (String descriptor : proxy.interfaceChain()) {\n";
    out.indent();
    out << "if (descriptor.equals(kInterfaceName)) {\n";
    out.indent();
    out << "return proxy;\n";
    out.unindent();
    out << "}\n";
    out.unindent();
    out << "}\n";
    out.unindent();
    out << "} catch (android.os.RemoteException e) {\n";
    out.indent();
    out.unindent();
    out << "}\n\n";

    out << "return null;\n";

    out.unindent();
    out << "}\n\n";

    out << "public static "
        << ifaceName
        << " castFrom(android.os.IHwInterface iface) {\n";
    out.indent();

    out << "return (iface == null) ? null : "
        << ifaceName
        << ".asInterface(iface.asBinder());\n";

    out.unindent();
    out << "}\n\n";

    out << "@Override\npublic android.os.IHwBinder asBinder();\n\n";

    out << "public static "
        << ifaceName
        << " getService(String serviceName) throws android.os.RemoteException {\n";

    out.indent();

    out << "return "
        << ifaceName
        << ".asInterface(android.os.HwBinder.getService(\""
        << iface->fqName().string()
        << "\",serviceName));\n";

    out.unindent();

    out << "}\n\n";

    out << "public static "
        << ifaceName
        << " getService() throws android.os.RemoteException {\n";

    out.indent();

    out << "return "
        << ifaceName
        << ".asInterface(android.os.HwBinder.getService(\""
        << iface->fqName().string()
        << "\",\"default\"));\n";

    out.unindent();

    out << "}\n\n";

    status_t err = emitJavaTypeDeclarations(out);

    if (err != OK) {
        return err;
    }

    for (const auto &method : iface->methods()) {
        if (method->isHiddenFromJava()) {
            continue;
        }

        const bool returnsValue = !method->results().empty();
        const bool needsCallback = method->results().size() > 1;

        if (needsCallback) {
            out << "\npublic interface "
                << method->name()
                << "Callback {\n";

            out.indent();

            out << "public void onValues(";
            method->emitJavaResultSignature(out);
            out << ");\n";

            out.unindent();
            out << "}\n\n";
        }

        if (returnsValue && !needsCallback) {
            out << method->results()[0]->type().getJavaType();
        } else {
            out << "void";
        }

        out << " "
            << method->name()
            << "(";
        method->emitJavaArgSignature(out);

        if (needsCallback) {
            if (!method->args().empty()) {
                out << ", ";
            }

            out << method->name()
                << "Callback cb";
        }

        out << ")\n";
        out.indent();
        out << "throws android.os.RemoteException;\n";
        out.unindent();
    }

    out << "\npublic static final class Proxy implements "
        << ifaceName
        << " {\n";

    out.indent();

    out << "private android.os.IHwBinder mRemote;\n\n";
    out << "public Proxy(android.os.IHwBinder remote) {\n";
    out.indent();
    out << "mRemote = java.util.Objects.requireNonNull(remote);\n";
    out.unindent();
    out << "}\n\n";

    out << "@Override\npublic android.os.IHwBinder asBinder() {\n";
    out.indent();
    out << "return mRemote;\n";
    out.unindent();
    out << "}\n\n";


    out << "@Override\npublic String toString() ";
    out.block([&] {
        out.sTry([&] {
            out << "return this.interfaceDescriptor() + \"@Proxy\";\n";
        }).sCatch("android.os.RemoteException ex", [&] {
            out << "/* ignored; handled below. */\n";
        }).endl();
        out << "return \"[class or subclass of \" + "
            << ifaceName << ".kInterfaceName + \"]@Proxy\";\n";
    }).endl().endl();

    const Interface *prevInterface = nullptr;
    for (const auto &tuple : iface->allMethodsFromRoot()) {
        const Method *method = tuple.method();

        if (method->isHiddenFromJava()) {
            continue;
        }

        const Interface *superInterface = tuple.interface();
        if (prevInterface != superInterface) {
            out << "// Methods from "
                << superInterface->fullName()
                << " follow.\n";
            prevInterface = superInterface;
        }
        const bool returnsValue = !method->results().empty();
        const bool needsCallback = method->results().size() > 1;

        out << "@Override\npublic ";
        if (returnsValue && !needsCallback) {
            out << method->results()[0]->type().getJavaType();
        } else {
            out << "void";
        }

        out << " "
            << method->name()
            << "(";
        method->emitJavaArgSignature(out);

        if (needsCallback) {
            if (!method->args().empty()) {
                out << ", ";
            }

            out << method->name()
                << "Callback cb";
        }

        out << ")\n";
        out.indent();
        out.indent();
        out << "throws android.os.RemoteException {\n";
        out.unindent();

        if (method->isHidlReserved() && method->overridesJavaImpl(IMPL_PROXY)) {
            method->javaImpl(IMPL_PROXY, out);
            out.unindent();
            out << "}\n";
            continue;
        }
        out << "android.os.HwParcel _hidl_request = new android.os.HwParcel();\n";
        out << "_hidl_request.writeInterfaceToken("
            << superInterface->fullJavaName()
            << ".kInterfaceName);\n";

        for (const auto &arg : method->args()) {
            emitJavaReaderWriter(
                    out,
                    "_hidl_request",
                    arg,
                    false /* isReader */,
                    false /* addPrefixToName */);
        }

        out << "\nandroid.os.HwParcel _hidl_reply = new android.os.HwParcel();\n";

        out.sTry([&] {
            out << "mRemote.transact("
                << method->getSerialId()
                << " /* "
                << method->name()
                << " */, _hidl_request, _hidl_reply, ";

            if (method->isOneway()) {
                out << "android.os.IHwBinder.FLAG_ONEWAY";
            } else {
                out << "0 /* flags */";
            }

            out << ");\n";

            if (!method->isOneway()) {
                out << "_hidl_reply.verifySuccess();\n";
            } else {
                CHECK(!returnsValue);
            }

            out << "_hidl_request.releaseTemporaryStorage();\n";

            if (returnsValue) {
                out << "\n";

                for (const auto &arg : method->results()) {
                    emitJavaReaderWriter(
                            out,
                            "_hidl_reply",
                            arg,
                            true /* isReader */,
                            true /* addPrefixToName */);
                }

                if (needsCallback) {
                    out << "cb.onValues(";

                    bool firstField = true;
                    for (const auto &arg : method->results()) {
                        if (!firstField) {
                            out << ", ";
                        }

                        out << "_hidl_out_" << arg->name();
                        firstField = false;
                    }

                    out << ");\n";
                } else {
                    const std::string returnName = method->results()[0]->name();
                    out << "return _hidl_out_" << returnName << ";\n";
                }
            }
        }).sFinally([&] {
            out << "_hidl_reply.release();\n";
        }).endl();

        out.unindent();
        out << "}\n\n";
    }

    out.unindent();
    out << "}\n";

    ////////////////////////////////////////////////////////////////////////////

    out << "\npublic static abstract class Stub extends android.os.HwBinder "
        << "implements "
        << ifaceName << " {\n";

    out.indent();

    out << "@Override\npublic android.os.IHwBinder asBinder() {\n";
    out.indent();
    out << "return this;\n";
    out.unindent();
    out << "}\n\n";

    for (Method *method : iface->hidlReservedMethods()) {
        if (method->isHiddenFromJava()) {
            continue;
        }

        // b/32383557 this is a hack. We need to change this if we have more reserved methods.
        CHECK_LE(method->results().size(), 1u);
        std::string resultType = method->results().size() == 0 ? "void" :
                method->results()[0]->type().getJavaType();
        out << "@Override\npublic final "
            << resultType
            << " "
            << method->name()
            << "(";
        method->emitJavaArgSignature(out);
        out << ") {\n";

        out.indent();
        method->javaImpl(IMPL_INTERFACE, out);
        out.unindent();
        out << "\n}\n\n";
    }

    out << "@Override\n"
        << "public android.os.IHwInterface queryLocalInterface(String descriptor) {\n";
    out.indent();
    // XXX what about potential superClasses?
    out << "if (kInterfaceName.equals(descriptor)) {\n";
    out.indent();
    out << "return this;\n";
    out.unindent();
    out << "}\n";
    out << "return null;\n";
    out.unindent();
    out << "}\n\n";

    out << "public void registerAsService(String serviceName) throws android.os.RemoteException {\n";
    out.indent();

    out << "registerService(serviceName);\n";

    out.unindent();
    out << "}\n\n";

    out << "@Override\npublic String toString() ";
    out.block([&] {
        out << "return this.interfaceDescriptor() + \"@Stub\";\n";
    }).endl().endl();

    out << "@Override\n"
        << "public void onTransact("
        << "int _hidl_code, "
        << "android.os.HwParcel _hidl_request, "
        << "final android.os.HwParcel _hidl_reply, "
        << "int _hidl_flags)\n";
    out.indent();
    out.indent();
    out << "throws android.os.RemoteException {\n";
    out.unindent();

    out << "switch (_hidl_code) {\n";

    out.indent();

    for (const auto &tuple : iface->allMethodsFromRoot()) {
        const Method *method = tuple.method();

        const Interface *superInterface = tuple.interface();
        const bool returnsValue = !method->results().empty();
        const bool needsCallback = method->results().size() > 1;

        out << "case "
            << method->getSerialId()
            << " /* "
            << method->name()
            << " */:\n{\n";

        out.indent();

        if (method->isHidlReserved() && method->overridesJavaImpl(IMPL_STUB)) {
            method->javaImpl(IMPL_STUB, out);
            out.unindent();
            out << "break;\n";
            out << "}\n\n";
            continue;
        }

        out << "_hidl_request.enforceInterface("
            << superInterface->fullJavaName()
            << ".kInterfaceName);\n\n";

        if (method->isHiddenFromJava()) {
            // This is a method hidden from the Java side of things, it must not
            // return any value and will simply signal success.
            CHECK(!returnsValue);

            out << "_hidl_reply.writeStatus(android.os.HwParcel.STATUS_SUCCESS);\n";
            out << "_hidl_reply.send();\n";
            out << "break;\n";
            out.unindent();
            out << "}\n\n";
            continue;
        }

        for (const auto &arg : method->args()) {
            emitJavaReaderWriter(
                    out,
                    "_hidl_request",
                    arg,
                    true /* isReader */,
                    false /* addPrefixToName */);
        }

        if (!needsCallback && returnsValue) {
            const TypedVar *returnArg = method->results()[0];

            out << returnArg->type().getJavaType()
                << " _hidl_out_"
                << returnArg->name()
                << " = ";
        }

        out << method->name()
            << "(";

        bool firstField = true;
        for (const auto &arg : method->args()) {
            if (!firstField) {
                out << ", ";
            }

            out << arg->name();

            firstField = false;
        }

        if (needsCallback) {
            if (!firstField) {
                out << ", ";
            }

            out << "new " << method->name() << "Callback() {\n";
            out.indent();

            out << "@Override\n"
                << "public void onValues(";
            method->emitJavaResultSignature(out);
            out << ") {\n";

            out.indent();
            out << "_hidl_reply.writeStatus(android.os.HwParcel.STATUS_SUCCESS);\n";

            for (const auto &arg : method->results()) {
                emitJavaReaderWriter(
                        out,
                        "_hidl_reply",
                        arg,
                        false /* isReader */,
                        false /* addPrefixToName */);
                // no need to add _hidl_out because out vars are are scoped
            }

            out << "_hidl_reply.send();\n"
                      << "}}";

            out.unindent();
            out.unindent();
        }

        out << ");\n";

        if (!needsCallback && !method->isOneway()) {
            out << "_hidl_reply.writeStatus(android.os.HwParcel.STATUS_SUCCESS);\n";

            if (returnsValue) {
                const TypedVar *returnArg = method->results()[0];

                emitJavaReaderWriter(
                        out,
                        "_hidl_reply",
                        returnArg,
                        false /* isReader */,
                        true /* addPrefixToName */);
            }

            out << "_hidl_reply.send();\n";
        }

        out << "break;\n";
        out.unindent();
        out << "}\n\n";
    }

    out.unindent();
    out << "}\n";

    out.unindent();
    out << "}\n";

    out.unindent();
    out << "}\n";

    out.unindent();
    out << "}\n";

    return OK;
}

status_t AST::emitJavaTypeDeclarations(Formatter &out) const {
    return mRootScope->emitJavaTypeDeclarations(out, false /* atTopLevel */);
}

}  // namespace android
