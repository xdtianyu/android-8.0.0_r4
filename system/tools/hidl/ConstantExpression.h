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

#ifndef CONSTANT_EXPRESSION_H_

#define CONSTANT_EXPRESSION_H_

#include <android-base/macros.h>
#include <string>
#include "ScalarType.h"

namespace android {

/**
 * A constant expression is represented by a tree.
 */
struct ConstantExpression {

    enum ConstExprType {
        kConstExprLiteral,
        kConstExprUnary,
        kConstExprBinary,
        kConstExprTernary
    };

    /* Default constructor. */
    ConstantExpression();
    /* Copy constructor. */
    ConstantExpression(const ConstantExpression& other);
    /* Copy constructor, with the expr overriden. */
    ConstantExpression(const ConstantExpression& other, std::string expr);
    /* Literals */
    ConstantExpression(const char *value);
    /* binary operations */
    ConstantExpression(const ConstantExpression *value1,
        const char *op, const ConstantExpression* value2);
    /* unary operations */
    ConstantExpression(const char *op, const ConstantExpression *value);
    /* ternary ?: */
    ConstantExpression(const ConstantExpression *cond,
                       const ConstantExpression *trueVal,
                       const ConstantExpression *falseVal);

    static ConstantExpression Zero(ScalarType::Kind kind);
    static ConstantExpression One(ScalarType::Kind kind);
    static ConstantExpression ValueOf(ScalarType::Kind kind, uint64_t value);

    /* Evaluated result in a string form. */
    std::string value() const;
    /* Evaluated result in a string form. */
    std::string cppValue() const;
    /* Evaluated result in a string form. */
    std::string javaValue() const;
    /* Evaluated result in a string form, with given contextual kind. */
    std::string value(ScalarType::Kind castKind) const;
    /* Evaluated result in a string form, with given contextual kind. */
    std::string cppValue(ScalarType::Kind castKind) const;
    /* Evaluated result in a string form, with given contextual kind. */
    std::string javaValue(ScalarType::Kind castKind) const;
    /* Original expression with type. */
    const std::string &description() const;
    /* See mTrivialDescription */
    bool descriptionIsTrivial() const;
    /* Return a ConstantExpression that is 1 plus the original. */
    ConstantExpression addOne() const;
    /* Assignment operator. */
    ConstantExpression& operator=(const ConstantExpression& other);

    size_t castSizeT() const;

private:
    /* The formatted expression. */
    std::string mExpr;
    /* The type of the expression. Hints on its original form. */
    ConstExprType mType;
    /* The kind of the result value. */
    ScalarType::Kind mValueKind;
    /* The stored result value. */
    uint64_t mValue;
    /* true if description() does not offer more information than value(). */
    bool mTrivialDescription = false;

    /*
     * Helper function for all cpp/javaValue methods.
     * Returns a plain string (without any prefixes or suffixes, just the
     * digits) converted from mValue.
     */
    std::string rawValue(ScalarType::Kind castKind) const;
    /* Trim unnecessary information. Only mValue and mValueKind is kept. */
    ConstantExpression &toLiteral();

    /*
     * Return the value casted to the given type.
     * First cast it according to mValueKind, then cast it to T.
     * Assumes !containsIdentifiers()
     */
    template <typename T> T cast() const;
};

}  // namespace android

#endif  // CONSTANT_EXPRESSION_H_
