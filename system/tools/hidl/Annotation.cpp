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

#include "Annotation.h"

#include <android-base/logging.h>
#include <hidl-util/Formatter.h>
#include <hidl-util/StringHelper.h>
#include <vector>

namespace android {


AnnotationParam::AnnotationParam(const std::string &name,
                std::vector<std::string> *values)
: mName(name), mValues(values) {}

AnnotationParam::AnnotationParam(const std::string &name,
                std::vector<ConstantExpression *> *values)
        : mName(name) {
    mValues = new std::vector<std::string>();
    for(ConstantExpression *ce : *values) {
        mValues->push_back(ce->value() + " /* " + ce->description() + " */");
    }
}

const std::string &AnnotationParam::getName() const {
    return mName;
}

const std::vector<std::string> *AnnotationParam::getValues() const {
    return mValues;
}

const std::string &AnnotationParam::getSingleValue() const {
    CHECK_EQ(mValues->size(), 1u) << mName << " requires one values but has multiple";
    return mValues->at(0);
}

std::string AnnotationParam::getSingleString() const {
    std::string value = getSingleValue();

    CHECK(value.size() >= 2 && value[0] == '"' && value[value.size() - 1] == '"')
        << mName << " must be a string";

    // unquote string
    value = value.substr(1, value.size() - 2);

    return value;
}

bool AnnotationParam::getSingleBool() const {
    std::string value = getSingleString();

    if (value == "true") {
        return true;
    } else if (value == "false") {
        return false;
    }

    CHECK(false) << mName << " must be of boolean value (true/false).";
    return false;
}

Annotation::Annotation(const char *name,AnnotationParamVector *params)
        : mName(name),
          mParams(params) {
}

std::string Annotation::name() const {
    return mName;
}

const AnnotationParamVector &Annotation::params() const {
    return *mParams;
}

const AnnotationParam *Annotation::getParam(const std::string &name) const {
    for (auto *i: *mParams) {
        if (i->getName() == name) {
            return i;
        }
    }

    return nullptr;
}

void Annotation::dump(Formatter &out) const {
    out << "@" << mName;

    if (mParams->size() == 0) {
        return;
    }

    out << "(";

    for (size_t i = 0; i < mParams->size(); ++i) {
        if (i > 0) {
            out << ", ";
        }

        const AnnotationParam* param = mParams->at(i);

        out << param->getName() << "=";

        const std::vector<std::string> *values = param->getValues();
        if (values->size() > 1) {
            out << "{";
        }

        out << StringHelper::JoinStrings(*values, ", ");

        if (values->size() > 1) {
            out << "}";
        }
    }

    out << ")";
}

}  // namespace android

