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

#include "Method.h"

#include "Annotation.h"
#include "ScalarType.h"
#include "Type.h"

#include <android-base/logging.h>
#include <hidl-util/Formatter.h>

namespace android {

Method::Method(const char *name,
       std::vector<TypedVar *> *args,
       std::vector<TypedVar *> *results,
       bool oneway,
       std::vector<Annotation *> *annotations)
    : mName(name),
      mArgs(args),
      mResults(results),
      mOneway(oneway),
      mAnnotations(annotations) {
}

void Method::fillImplementation(
        size_t serial,
        MethodImpl cppImpl,
        MethodImpl javaImpl) {
    mIsHidlReserved = true;
    mSerial = serial;
    mCppImpl = cppImpl;
    mJavaImpl = javaImpl;

    CHECK(mJavaImpl.find(IMPL_STUB_IMPL) == mJavaImpl.end())
            << "FATAL: mJavaImpl should not use IMPL_STUB_IMPL; use IMPL_INTERFACE instead.";
    CHECK(mCppImpl.find(IMPL_STUB_IMPL) == mCppImpl.end() ||
          mCppImpl.find(IMPL_STUB) == mCppImpl.end())
            << "FATAL: mCppImpl IMPL_STUB will override IMPL_STUB_IMPL.";
}

std::string Method::name() const {
    return mName;
}

const std::vector<TypedVar *> &Method::args() const {
    return *mArgs;
}

const std::vector<TypedVar *> &Method::results() const {
    return *mResults;
}

const std::vector<Annotation *> &Method::annotations() const {
    return *mAnnotations;
}

void Method::cppImpl(MethodImplType type, Formatter &out) const {
    CHECK(mIsHidlReserved);
    auto it = mCppImpl.find(type);
    if (it != mCppImpl.end()) {
        if (it->second != nullptr) {
            it->second(out);
        }
    }
}

void Method::javaImpl(MethodImplType type, Formatter &out) const {
    CHECK(mIsHidlReserved);
    auto it = mJavaImpl.find(type);
    if (it != mJavaImpl.end()) {
        if (it->second != nullptr) {
            it->second(out);
        }
    }
}

bool Method::isHiddenFromJava() const {
    return isHidlReserved() && name() == "debug";
}

bool Method::overridesCppImpl(MethodImplType type) const {
    CHECK(mIsHidlReserved);
    return mCppImpl.find(type) != mCppImpl.end();
}

bool Method::overridesJavaImpl(MethodImplType type) const {
    CHECK(mIsHidlReserved);
    return mJavaImpl.find(type) != mJavaImpl.end();
}

Method *Method::copySignature() const {
    return new Method(mName.c_str(), mArgs, mResults, mOneway, mAnnotations);
}

void Method::setSerialId(size_t serial) {
    CHECK(!mIsHidlReserved);
    mSerial = serial;
}

size_t Method::getSerialId() const {
    return mSerial;
}

void Method::generateCppSignature(Formatter &out,
                                  const std::string &className,
                                  bool specifyNamespaces) const {
    const bool returnsValue = !results().empty();

    const TypedVar *elidedReturn = canElideCallback();

    std::string space = (specifyNamespaces ? "::android::hardware::" : "");

    if (elidedReturn == nullptr) {
        out << space << "Return<void> ";
    } else {
        out << space
            << "Return<"
            << elidedReturn->type().getCppResultType( specifyNamespaces)
            << "> ";
    }

    if (!className.empty()) {
        out << className << "::";
    }

    out << name()
        << "(";
    emitCppArgSignature(out, specifyNamespaces);

    if (returnsValue && elidedReturn == nullptr) {
        if (!args().empty()) {
            out << ", ";
        }

        out << name() << "_cb _hidl_cb";
    }

    out << ")";
}

static void emitCppArgResultSignature(Formatter &out,
                         const std::vector<TypedVar *> &args,
                         bool specifyNamespaces) {
    out.join(args.begin(), args.end(), ", ", [&](auto arg) {
        out << arg->type().getCppArgumentType(specifyNamespaces);
        out << " ";
        out << arg->name();
    });
}

static void emitJavaArgResultSignature(Formatter &out, const std::vector<TypedVar *> &args) {
    out.join(args.begin(), args.end(), ", ", [&](auto arg) {
        out << arg->type().getJavaType();
        out << " ";
        out << arg->name();
    });
}

void Method::emitCppArgSignature(Formatter &out, bool specifyNamespaces) const {
    emitCppArgResultSignature(out, args(), specifyNamespaces);
}
void Method::emitCppResultSignature(Formatter &out, bool specifyNamespaces) const {
    emitCppArgResultSignature(out, results(), specifyNamespaces);
}
void Method::emitJavaArgSignature(Formatter &out) const {
    emitJavaArgResultSignature(out, args());
}
void Method::emitJavaResultSignature(Formatter &out) const {
    emitJavaArgResultSignature(out, results());
}

void Method::dumpAnnotations(Formatter &out) const {
    if (mAnnotations->size() == 0) {
        return;
    }

    out << "// ";
    for (size_t i = 0; i < mAnnotations->size(); ++i) {
        if (i > 0) {
            out << " ";
        }
        mAnnotations->at(i)->dump(out);
    }
    out << "\n";
}

bool Method::isJavaCompatible() const {
    if (isHiddenFromJava()) {
        return true;
    }

    for (const auto &arg : *mArgs) {
        if (!arg->isJavaCompatible()) {
            return false;
        }
    }

    for (const auto &result : *mResults) {
        if (!result->isJavaCompatible()) {
            return false;
        }
    }

    return true;
}

const TypedVar* Method::canElideCallback() const {
    // Can't elide callback for void or tuple-returning methods
    if (mResults->size() != 1) {
        return nullptr;
    }

    const TypedVar *typedVar = mResults->at(0);

    if (typedVar->type().isElidableType()) {
        return typedVar;
    }

    return nullptr;
}

////////////////////////////////////////////////////////////////////////////////

TypedVar::TypedVar(const char *name, Type *type)
    : mName(name),
      mType(type) {
}

std::string TypedVar::name() const {
    return mName;
}

const Type &TypedVar::type() const {
    return *mType;
}

bool TypedVar::isJavaCompatible() const {
    return mType->isJavaCompatible();
}

////////////////////////////////////////////////////////////////////////////////
bool TypedVarVector::add(TypedVar *v) {
    if (mNames.emplace(v->name()).second) {
        push_back(v);
        return true;
    }
    return false;
}

}  // namespace android

