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

#include "ConstantExpression.h"

#include <stdio.h>
#include <string>
#include <android-base/parseint.h>
#include <android-base/logging.h>
#include <sstream>

#include "EnumType.h"

// The macros are really nasty here. Consider removing
// as many macros as possible.

#define STREQ(__x__, __y__) (strcmp((__x__), (__y__)) == 0)
#define OPEQ(__y__) STREQ(op, __y__)
#define COMPUTE_UNARY(__op__)  if(OPEQ(#__op__)) return __op__ val;
#define COMPUTE_BINARY(__op__) if(OPEQ(#__op__)) return lval __op__ rval;
#define OP_IS_BIN_ARITHMETIC  (OPEQ("+") || OPEQ("-") || OPEQ("*") || OPEQ("/") || OPEQ("%"))
#define OP_IS_BIN_BITFLIP     (OPEQ("|") || OPEQ("^") || OPEQ("&"))
#define OP_IS_BIN_COMP        (OPEQ("<") || OPEQ(">") || OPEQ("<=") || OPEQ(">=") || OPEQ("==") || OPEQ("!="))
#define OP_IS_BIN_SHIFT       (OPEQ(">>") || OPEQ("<<"))
#define OP_IS_BIN_LOGICAL     (OPEQ("||") || OPEQ("&&"))
#define SK(__x__) ScalarType::Kind::KIND_##__x__
#define SHOULD_NOT_REACH() CHECK(false) << __LINE__ << ": should not reach here: "

#define SWITCH_KIND(__cond__, __action__, __def__)           \
        switch(__cond__) {                                        \
            case SK(BOOL): __action__(bool)                         \
            case SK(UINT8): __action__(uint8_t)                     \
            case SK(INT8): __action__(int8_t)                       \
            case SK(UINT16): __action__(uint16_t)                   \
            case SK(INT16): __action__(int16_t)                     \
            case SK(UINT32): __action__(uint32_t)                   \
            case SK(INT32): __action__(int32_t)                     \
            case SK(UINT64): __action__(uint64_t)                   \
            case SK(INT64): __action__(int64_t)                     \
            default: __def__                                        \
        }                                                         \

namespace android {

static inline bool isSupported(ScalarType::Kind kind) {
    return SK(BOOL) == kind || ScalarType(kind).isValidEnumStorageType();
}

/* See docs at the end for details on integral promotion. */
ScalarType::Kind integralPromotion(ScalarType::Kind in) {
    return SK(INT32) < in ? in : SK(INT32); // note that KIND_INT32 < KIND_UINT32
}

/* See docs at the end for details on usual arithmetic conversion. */
ScalarType::Kind usualArithmeticConversion(ScalarType::Kind lft,
                                           ScalarType::Kind rgt) {
    CHECK(isSupported(lft) && isSupported(rgt));
    // Kinds in concern: bool, (u)int[8|16|32|64]
    if(lft == rgt) return lft; // easy case
    if(lft == SK(BOOL)) return rgt;
    if(rgt == SK(BOOL)) return lft;
    bool isLftSigned = (lft == SK(INT8))  || (lft == SK(INT16))
                    || (lft == SK(INT32)) || (lft == SK(INT64));
    bool isRgtSigned = (rgt == SK(INT8))  || (rgt == SK(INT16))
                    || (rgt == SK(INT32)) || (rgt == SK(INT64));
    if(isLftSigned == isRgtSigned) return lft < rgt ? rgt : lft;
    ScalarType::Kind unsignedRank = isLftSigned ? rgt : lft;
    ScalarType::Kind signedRank   = isLftSigned ? lft : rgt;
    if(unsignedRank >= signedRank) return unsignedRank;
    if(signedRank > unsignedRank)  return signedRank;

    // Although there is such rule to return "the unsigned counterpart of
    // the signed operand", it should not reach here in our HIDL grammar.
    LOG(FATAL) << "Could not do usual arithmetic conversion for type "
               << lft << "and" << rgt;
    switch(signedRank) {
        case SK(INT8):  return SK(UINT8);
        case SK(INT16): return SK(UINT16);
        case SK(INT32): return SK(UINT32);
        case SK(INT64): return SK(UINT64);
        default: return SK(UINT64);
    }
}

template <class T>
T handleUnary(const char *op, T val) {
    COMPUTE_UNARY(+)
    COMPUTE_UNARY(-)
    COMPUTE_UNARY(!)
    COMPUTE_UNARY(~)
    // Should not reach here.
    SHOULD_NOT_REACH() << "Could not handleUnary for " << op << " " << val;
    return static_cast<T>(0xdeadbeef);
}

template <class T>
T handleBinaryCommon(T lval, const char *op, T rval) {
    COMPUTE_BINARY(+)
    COMPUTE_BINARY(-)
    COMPUTE_BINARY(*)
    COMPUTE_BINARY(/)
    COMPUTE_BINARY(%)
    COMPUTE_BINARY(|)
    COMPUTE_BINARY(^)
    COMPUTE_BINARY(&)
    // comparison operators: return 0 or 1 by nature.
    COMPUTE_BINARY(==)
    COMPUTE_BINARY(!=)
    COMPUTE_BINARY(<)
    COMPUTE_BINARY(>)
    COMPUTE_BINARY(<=)
    COMPUTE_BINARY(>=)
    // Should not reach here.
    SHOULD_NOT_REACH() << "Could not handleBinaryCommon for "
                       << lval << " " << op << " " << rval;
    return static_cast<T>(0xdeadbeef);
}

template <class T>
T handleShift(T lval, const char *op, int64_t rval) {
    // just cast rval to int64_t and it should fit.
    COMPUTE_BINARY(>>)
    COMPUTE_BINARY(<<)
    // Should not reach here.
    SHOULD_NOT_REACH() << "Could not handleShift for"
                       << lval << " " << op << " " << rval;
    return static_cast<T>(0xdeadbeef);
}

bool handleLogical(bool lval, const char *op, bool rval) {
    COMPUTE_BINARY(||);
    COMPUTE_BINARY(&&);
    // Should not reach here.
    SHOULD_NOT_REACH() << "Could not handleLogical for"
                       << lval << " " << op << " " << rval;
    return false;
}

ConstantExpression::ConstantExpression() {
}

// static
ConstantExpression ConstantExpression::Zero(ScalarType::Kind kind) {
    ConstantExpression ce = ValueOf(kind, 0);
    ce.mExpr = "0";
    return ce;
}

// static
ConstantExpression ConstantExpression::One(ScalarType::Kind kind) {
    ConstantExpression ce = ValueOf(kind, 1);
    ce.mExpr = "1";
    return ce;
}

// static
ConstantExpression ConstantExpression::ValueOf(ScalarType::Kind kind, uint64_t value) {
    ConstantExpression ce;
    CHECK(isSupported(kind));

    ce.mExpr = "";
    ce.mType = kConstExprLiteral;
    ce.mValueKind = kind;
    ce.mValue = value;
    ce.mTrivialDescription = true;
    return ce;
}
ConstantExpression::ConstantExpression(const ConstantExpression& other) {
    *this = other;
}

/* Copy constructor, with the expr overriden and treated non-trivial */
ConstantExpression::ConstantExpression(const ConstantExpression& other, std::string expr) {
    *this = other;
    mExpr = expr;
    mTrivialDescription = false;
}

ConstantExpression& ConstantExpression::operator=(const ConstantExpression& other) {
    mType = other.mType;
    mValueKind = other.mValueKind;
    mValue = other.mValue;
    mExpr = other.mExpr;
    mTrivialDescription = other.mTrivialDescription;
    return *this;
}

/* Literals. */
ConstantExpression::ConstantExpression(const char *value)
        : mExpr(value), mType(kConstExprLiteral), mTrivialDescription(true) {
    const char* head = value, *tail = head + strlen(value) - 1;
    bool isLong = false, isUnsigned = false;
    bool isHex = (value[0] == '0' && (value[1] == 'x' || value[1] == 'X'));
    while(tail >= head && (*tail == 'u' || *tail == 'U' || *tail == 'l' || *tail == 'L')) {
        isUnsigned |= (*tail == 'u' || *tail == 'U');
        isLong     |= (*tail == 'l' || *tail == 'L');
        tail--;
    }
    char *newVal = strndup(value, tail - head + 1);
    bool parseOK = base::ParseUint(newVal, &mValue);
    free(newVal);
    CHECK(parseOK) << "Could not parse as integer: " << value;

    // guess literal type.
    if(isLong) {
        if(isUnsigned) // ul
            mValueKind = SK(UINT64);
        else // l
            mValueKind = SK(INT64);
    } else { // no l suffix
        if(isUnsigned) { // u
            if(mValue <= UINT32_MAX)
                mValueKind = SK(UINT32);
            else
                mValueKind = SK(UINT64);
        } else { // no suffix
            if(isHex) {
                if(mValue <= INT32_MAX) // mValue always >= 0
                    mValueKind = SK(INT32);
                else if(mValue <= UINT32_MAX)
                    mValueKind = SK(UINT32);
                else if(mValue <= INT64_MAX) // mValue always >= 0
                    mValueKind = SK(INT64);
                else if(mValue <= UINT64_MAX)
                    mValueKind = SK(UINT64);
            } else {
                if(mValue <= INT32_MAX) // mValue always >= 0
                    mValueKind = SK(INT32);
                else
                    mValueKind = SK(INT64);
            }
        }
    }
}

/* Unary operations. */
ConstantExpression::ConstantExpression(const char *op,
                                       const ConstantExpression *value)
        : mExpr(std::string("(") + op + value->mExpr + ")"),
          mType(kConstExprUnary),
          mValueKind(value->mValueKind) {

#define CASE_UNARY(__type__)\
            mValue = handleUnary(op, static_cast<__type__>(value->mValue)); return;

    SWITCH_KIND(mValueKind, CASE_UNARY, SHOULD_NOT_REACH(); return;)
}

/* Binary operations. */
ConstantExpression::ConstantExpression(const ConstantExpression *lval,
                                       const char *op,
                                       const ConstantExpression* rval)
        : mExpr(std::string("(") + lval->mExpr + " " + op + " " + rval->mExpr + ")"),
          mType(kConstExprBinary)
{

    bool isArithmeticOrBitflip = OP_IS_BIN_ARITHMETIC || OP_IS_BIN_BITFLIP;

    // CASE 1: + - *  / % | ^ & < > <= >= == !=
    if(isArithmeticOrBitflip || OP_IS_BIN_COMP) {
        // promoted kind for both operands.
        ScalarType::Kind promoted = usualArithmeticConversion(
                integralPromotion(lval->mValueKind),
                integralPromotion(rval->mValueKind));
        // result kind.
        mValueKind = isArithmeticOrBitflip
                    ? promoted // arithmetic or bitflip operators generates promoted type
                    : SK(BOOL); // comparison operators generates bool

#define CASE_BINARY_COMMON(__type__)\
            mValue = handleBinaryCommon(static_cast<__type__>(lval->mValue), op, static_cast<__type__>(rval->mValue)); return;

        SWITCH_KIND(promoted, CASE_BINARY_COMMON, SHOULD_NOT_REACH(); return;)
    }

    // CASE 2: << >>
    if(OP_IS_BIN_SHIFT) {
        mValueKind = integralPromotion(lval->mValueKind);
        // instead of promoting rval, simply casting it to int64 should also be good.
        int64_t numBits = rval->cast<int64_t>();
        if(numBits < 0) {
            // shifting with negative number of bits is undefined in C. In HIDL it
            // is defined as shifting into the other direction.
            op = OPEQ("<<") ? ">>" : "<<";
            numBits = -numBits;
        }

#define CASE_SHIFT(__type__)\
            mValue = handleShift(static_cast<__type__>(lval->mValue), op, numBits); return;

        SWITCH_KIND(mValueKind, CASE_SHIFT, SHOULD_NOT_REACH(); return;)
    }

    // CASE 3: && ||
    if(OP_IS_BIN_LOGICAL) {
        mValueKind = SK(BOOL);
        // easy; everything is bool.
        mValue = handleLogical(lval->mValue, op, rval->mValue);
        return;
    }

    SHOULD_NOT_REACH();
}

/* Ternary ?: operation. */
ConstantExpression::ConstantExpression(const ConstantExpression *cond,
                                       const ConstantExpression *trueVal,
                                       const ConstantExpression *falseVal)
        : mExpr(std::string("(") + cond->mExpr + "?" + trueVal->mExpr
                + ":" + falseVal->mExpr + ")"),
          mType(kConstExprTernary) {

    // note: for ?:, unlike arithmetic ops, integral promotion is not necessary.
    mValueKind = usualArithmeticConversion(trueVal->mValueKind,
                                           falseVal->mValueKind);

#define CASE_TERNARY(__type__)\
        mValue = cond->mValue ? (static_cast<__type__>(trueVal->mValue)) : (static_cast<__type__>(falseVal->mValue)); return;

    SWITCH_KIND(mValueKind, CASE_TERNARY, SHOULD_NOT_REACH(); return;)
}

ConstantExpression ConstantExpression::addOne() const {
    ConstantExpression myOne = ConstantExpression::One(mValueKind);
    return ConstantExpression(this, "+", &myOne).toLiteral();
}

ConstantExpression &ConstantExpression::toLiteral() {
    mExpr = value();
    mType = kConstExprLiteral;
    return *this;
}

const std::string &ConstantExpression::description() const {
    return mExpr;
}

bool ConstantExpression::descriptionIsTrivial() const {
    return mTrivialDescription;
}

std::string ConstantExpression::value() const {
    return rawValue(mValueKind);
}

std::string ConstantExpression::value(ScalarType::Kind castKind) const {
    return rawValue(castKind);
}

std::string ConstantExpression::cppValue() const {
    return cppValue(mValueKind);
}

std::string ConstantExpression::cppValue(ScalarType::Kind castKind) const {
    std::string literal(rawValue(castKind));
    // this is a hack to translate
    //       enum x : int64_t {  y = 1l << 63 };
    // into
    //       enum class x : int64_t { y = (int64_t)-9223372036854775808ull };
    // by adding the explicit cast.
    // Because 9223372036854775808 is uint64_t, and
    // -(uint64_t)9223372036854775808 == 9223372036854775808 could not
    // be narrowed to int64_t.
    if(castKind == SK(INT64) && (int64_t)mValue == INT64_MIN) {
        return strdup(("static_cast<"
            + ScalarType(SK(INT64)).getCppStackType() // "int64_t"
            + ">(" + literal + "ull)").c_str());
    }

    // add suffix if necessary.
    if(castKind == SK(UINT32) || castKind == SK(UINT64)) literal += "u";
    if(castKind == SK(UINT64) || castKind == SK(INT64)) literal += "ll";
    return literal;
}

std::string ConstantExpression::javaValue() const {
    return javaValue(mValueKind);
}

std::string ConstantExpression::javaValue(ScalarType::Kind castKind) const {
    switch(castKind) {
        case SK(UINT64): return rawValue(SK(INT64)) + "L";
        case SK(INT64):  return rawValue(SK(INT64)) + "L";
        case SK(UINT32): return rawValue(SK(INT32));
        case SK(UINT16): return rawValue(SK(INT16));
        case SK(UINT8) : return rawValue(SK(INT8));
        case SK(BOOL)  :
            return this->cast<bool>() ? strdup("true") : strdup("false");
        default: break;
    }
    return rawValue(castKind);
}

std::string ConstantExpression::rawValue(ScalarType::Kind castKind) const {

#define CASE_STR(__type__) return std::to_string(this->cast<__type__>());

    SWITCH_KIND(castKind, CASE_STR, SHOULD_NOT_REACH(); return 0; );
}

template<typename T>
T ConstantExpression::cast() const {

#define CASE_CAST_T(__type__) return static_cast<T>(static_cast<__type__>(mValue));

    SWITCH_KIND(mValueKind, CASE_CAST_T, SHOULD_NOT_REACH(); return 0; );
}

size_t ConstantExpression::castSizeT() const {
    return this->cast<size_t>();
}

/*

Evaluating expressions in HIDL language

The following rules are mostly like that in:
http://en.cppreference.com/w/cpp/language/operator_arithmetic
http://en.cppreference.com/w/cpp/language/operator_logical
http://en.cppreference.com/w/cpp/language/operator_comparison
http://en.cppreference.com/w/cpp/language/operator_other

The type of literal is the first type which the value
can fit from the list of types depending on the suffix and bases.

suffix              decimal bases           hexadecimal bases
no suffix           int32_t                 int32_t
                    int64_t                 uint32_t
                                            int64_t
                                            uint64_t

u/U                 uint32_t                (same as left)
                    uint64_t

l/L                 int64_t                 int64_t

ul/UL/uL/Ul         uint64_t                uint64_t


Note: There are no negative integer literals.
      -1 is the unary minus applied to 1.

Unary arithmetic and bitwise operators (~ + -):
  don't change the type of the argument.
  (so -1u = -(1u) has type uint32_t)

Binary arithmetic and bitwise operators (except shifts) (+ - * / % & | ^):
1. Integral promotion is first applied on both sides.
2. If both operands have the same type, no promotion is necessary.
3. Usual arithmetic conversions.

Integral promotion: if an operand is of a type with less than 32 bits,
(including bool), it is promoted to int32_t.

Usual arithmetic conversions:
1. If operands are both signed or both unsigned, lesser conversion rank is
   converted to greater conversion rank.
2. Otherwise, if unsigned's rank >= signed's rank, -> unsigned's type
3. Otherwise, if signed's type can hold all values in unsigned's type,
   -> signed's type
4. Otherwise, both converted to the unsigned counterpart of the signed operand's
   type.
rank: bool < int8_t < int16_t < int32_t < int64_t


Shift operators (<< >>):
1. Integral promotion is applied on both sides.
2. For unsigned a, a << b discards bits that shifts out.
   For signed non-negative a, a << b is legal if no bits shifts out, otherwise error.
   For signed negative a, a << b gives error.
3. For unsigned and signed non-negative a, a >> b discards bits that shifts out.
   For signed negative a, a >> b discards bits that shifts out, and the signed
   bit gets extended. ("arithmetic right shift")
4. Shifting with negative number of bits is undefined. (Currently, the
   parser will shift into the other direction. This behavior may change.)
5. Shifting with number of bits exceeding the width of the type is undefined.
   (Currently, 1 << 32 == 1. This behavior may change.)

Logical operators (!, &&, ||):
1. Convert first operand to bool. (true if non-zero, false otherwise)
2. If short-circuited, return the result as type bool, value 1 or 0.
3. Otherwise, convert second operand to bool, evaluate the result, and return
   the result in the same fashion.

Arithmetic comparison operators (< > <= >= == !=):
1. Promote operands in the same way as binary arithmetic and bitwise operators.
   (Integral promotion + Usual arithmetic conversions)
2. Return type bool, value 0 or 1 the same way as logical operators.

Ternary conditional operator (?:):
1. Evaluate the conditional and evaluate the operands.
2. Return type of expression is the type under usual arithmetic conversions on
   the second and third operand. (No integral promotions necessary.)

*/

}  // namespace android

