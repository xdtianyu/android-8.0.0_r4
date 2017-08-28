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

%{

#include "Annotation.h"
#include "AST.h"
#include "ArrayType.h"
#include "CompoundType.h"
#include "ConstantExpression.h"
#include "EnumType.h"
#include "Interface.h"
#include "Location.h"
#include "Method.h"
#include "VectorType.h"
#include "RefType.h"

#include "hidl-gen_y.h"

#include <android-base/logging.h>
#include <hidl-util/FQName.h>
#include <hidl-util/StringHelper.h>
#include <stdio.h>

using namespace android;

extern int yylex(yy::parser::semantic_type *, yy::parser::location_type *, void *);

#define scanner ast->scanner()

::android::Location convertYYLoc(const yy::parser::location_type &loc) {
    return ::android::Location(
            ::android::Position(*(loc.begin.filename), loc.begin.line, loc.begin.column),
            ::android::Position(*(loc.end.filename), loc.end.line, loc.end.column)
    );
}

bool isValidInterfaceField(const char *identifier, std::string *errorMsg) {
    static const std::vector<std::string> reserved({
        // Injected names to interfaces by auto-generated code
        "isRemote", "descriptor", "hidlStaticBlock", "onTransact",
        "castFrom", "Proxy", "Stub",

        // Inherited methods from IBase is detected in addMethod. Not added here
        // because we need hidl-gen to compile IBase.

        // Inherited names by interfaces from IInterface / IBinder
        "onAsBinder", "asBinder", "queryLocalInterface", "getInterfaceDescriptor", "isBinderAlive",
        "pingBinder", "dump", "transact", "checkSubclass", "attachObject", "findObject",
        "detachObject", "localBinder", "remoteBinder", "mImpl",
    });
    std::string idstr(identifier);
    if (std::find(reserved.begin(), reserved.end(), idstr) != reserved.end()) {
        *errorMsg = idstr + " cannot be a name inside an interface";
        return false;
    }
    return true;
}

bool isValidStructField(const char *identifier, std::string *errorMsg) {
    static const std::vector<std::string> reserved({
        // Injected names to structs and unions by auto-generated code
        "readEmbeddedFromParcel", "writeEmbeddedToParcel", "readVectorFromParcel",
        "writeVectorToParcel", "writeEmbeddedToBlob",
    });
    std::string idstr(identifier);
    if (std::find(reserved.begin(), reserved.end(), idstr) != reserved.end()) {
        *errorMsg = idstr + " cannot be a name inside an struct or union";
        return false;
    }
    return true;
}

bool isValidIdentifier(const char *identifier, std::string *errorMsg) {
    static const std::vector<std::string> keywords({
        "uint8_t", "uint16_t", "uint32_t", "uint64_t",
        "int8_t", "int16_t", "int32_t", "int64_t", "bool", "float", "double",
        "interface", "struct", "union", "string", "vec", "enum", "ref", "handle",
        "package", "import", "typedef", "generates", "oneway", "extends",
        "fmq_sync", "fmq_unsync",
    });
    static const std::vector<std::string> cppKeywords({
        "alignas", "alignof", "and", "and_eq", "asm", "atomic_cancel", "atomic_commit",
        "atomic_noexcept", "auto", "bitand", "bitor", "bool", "break", "case", "catch",
        "char", "char16_t", "char32_t", "class", "compl", "concept", "const", "constexpr",
        "const_cast", "continue", "decltype", "default", "delete", "do", "double",
        "dynamic_cast", "else", "enum", "explicit", "export", "extern", "false", "float",
        "for", "friend", "goto", "if", "inline", "int", "import", "long", "module", "mutable",
        "namespace", "new", "noexcept", "not", "not_eq", "nullptr", "operator", "or", "or_eq",
        "private", "protected", "public", "register", "reinterpret_cast", "requires", "return",
        "short", "signed", "sizeof", "static", "static_assert", "static_cast", "struct",
        "switch", "synchronized", "template", "this", "thread_local", "throw", "true", "try",
        "typedef", "typeid", "typename", "union", "unsigned", "using", "virtual", "void",
        "volatile", "wchar_t", "while", "xor", "xor_eq",
    });
    static const std::vector<std::string> javaKeywords({
        "abstract", "continue", "for", "new", "switch", "assert", "default", "goto", "package",
        "synchronized", "boolean", "do", "if", "private", "this", "break", "double",
        "implements", "protected", "throw", "byte", "else", "import", "public", "throws",
        "case", "enum", "instanceof", "return", "transient", "catch", "extends", "int",
        "short", "try", "char", "final", "interface", "static", "void", "class", "finally",
        "long", "strictfp", "volatile", "const", "float", "native", "super", "while",
    });
    static const std::vector<std::string> cppCollide({
        "size_t", "offsetof",
        "DECLARE_SERVICE_MANAGER_INTERACTIONS", "IMPLEMENT_HWBINDER_META_INTERFACE",
        "IMPLEMENT_SERVICE_MANAGER_INTERACTIONS"
    });
    static const std::vector<std::string> hidlReserved({
        // Part of HidlSupport
        "hidl_string", "hidl_vec", "hidl_array", "hidl_version", "toBinder", "castInterface",
        "make_hidl_version"
    });

    // errors
    std::string idstr(identifier);
    if (std::find(keywords.begin(), keywords.end(), idstr) != keywords.end()) {
        *errorMsg = idstr + " is a HIDL keyword "
            "and is therefore not a valid identifier";
        return false;
    }
    if (std::find(cppKeywords.begin(), cppKeywords.end(), idstr) != cppKeywords.end()) {
        *errorMsg = idstr + " is a C++ keyword "
            "and is therefore not a valid identifier";
        return false;
    }
    if (std::find(javaKeywords.begin(), javaKeywords.end(), idstr) != javaKeywords.end()) {
        *errorMsg = idstr + " is a Java keyword "
            "and is therefore not a valid identifier";
        return false;
    }
    if (std::find(cppCollide.begin(), cppCollide.end(), idstr) != cppCollide.end()) {
        *errorMsg = idstr + " collides with reserved names in C++ code "
            "and is therefore not a valid identifier";
        return false;
    }
    if (StringHelper::StartsWith(idstr, "_hidl_")) {
        *errorMsg = idstr + " starts with _hidl_ "
            "and is therefore not a valid identifier";
        return false;
    }
    if (StringHelper::EndsWith(idstr, "_cb")) {
        *errorMsg = idstr + " ends with _cb "
            "and is therefore not a valid identifier";
        return false;
    }

    // warnings
    if (std::find(hidlReserved.begin(), hidlReserved.end(), idstr) != hidlReserved.end()) {
        *errorMsg = idstr + " is a name reserved by HIDL and should be avoided";
    }
    return true;
}

%}

%initial-action {
    // Initialize the initial location.
    @$.begin.filename = @$.end.filename =
        const_cast<std::string *>(&ast->getFilename());
}

%parse-param { android::AST *ast }
%lex-param { void *scanner }
%pure-parser
%glr-parser
%skeleton "glr.cc"

%expect-rr 0

%token<str> ENUM
%token<str> EXTENDS
%token<str> FQNAME
%token<str> GENERATES
%token<str> IDENTIFIER
%token<str> IMPORT
%token<str> INTEGER
%token<str> FLOAT
%token<str> INTERFACE
%token<str> PACKAGE
%token<type> TYPE
%token<str> STRUCT
%token<str> STRING_LITERAL
%token<str> TYPEDEF
%token<str> UNION
%token<templatedType> TEMPLATED
%token<void> ONEWAY

/* Operator precedence and associativity, as per
 * http://en.cppreference.com/w/cpp/language/operator_precedence */
/* Precedence level 15 ternary operator */
%right '?' ':'
/* Precedence level 13 - 14, LTR, logical operators*/
%left LOGICAL_OR
%left LOGICAL_AND
/* Precedence level 10 - 12, LTR, bitwise operators*/
%left '|'
%left '^'
%left '&'
/* Precedence level 9, LTR */
%left EQUALITY NEQ
/* Precedence level 8, LTR */
%left '<' '>' LEQ GEQ
/* Precedence level 7, LTR */
%left LSHIFT RSHIFT
/* Precedence level 6, LTR */
%left '+' '-'
/* Precedence level 5, LTR */
%left '*' '/' '%'
/* Precedence level 3, RTL; but we have to use %left here */
%left UNARY_MINUS UNARY_PLUS '!' '~'

%type<str> error_stmt opt_error_stmt error
%type<str> package
%type<fqName> fqname
%type<type> fqtype
%type<str> valid_identifier

%type<type> type enum_storage_type
%type<type> array_type_base
%type<arrayType> array_type
%type<type> opt_extends
%type<type> type_declaration type_declaration_body interface_declaration typedef_declaration
%type<type> named_struct_or_union_declaration named_enum_declaration
%type<type> compound_declaration annotated_compound_declaration

%type<field> field_declaration
%type<fields> field_declarations struct_or_union_body
%type<constantExpression> const_expr
%type<enumValue> enum_value
%type<enumValues> enum_values enum_declaration_body
%type<typedVars> typed_vars
%type<typedVar> typed_var
%type<method> method_declaration
%type<compoundStyle> struct_or_union_keyword
%type<stringVec> annotation_string_values annotation_string_value
%type<constExprVec> annotation_const_expr_values annotation_const_expr_value
%type<annotationParam> annotation_param
%type<annotationParams> opt_annotation_params annotation_params
%type<annotation> annotation
%type<annotations> opt_annotations

%start program

%union {
    const char *str;
    android::Type *type;
    android::ArrayType *arrayType;
    android::TemplatedType *templatedType;
    android::FQName *fqName;
    android::CompoundType *compoundType;
    android::CompoundField *field;
    std::vector<android::CompoundField *> *fields;
    android::EnumValue *enumValue;
    android::ConstantExpression *constantExpression;
    std::vector<android::EnumValue *> *enumValues;
    android::TypedVar *typedVar;
    android::TypedVarVector *typedVars;
    android::Method *method;
    android::CompoundType::Style compoundStyle;
    std::vector<std::string> *stringVec;
    std::vector<android::ConstantExpression *> *constExprVec;
    android::AnnotationParam *annotationParam;
    android::AnnotationParamVector *annotationParams;
    android::Annotation *annotation;
    std::vector<android::Annotation *> *annotations;
}

%%

valid_identifier
    : IDENTIFIER
      {
        std::string errorMsg;
        if (!isValidIdentifier($1, &errorMsg)) {
            std::cerr << "ERROR: " << errorMsg << " at " << @1 << "\n";
            YYERROR;
        }
        if (!errorMsg.empty()) {
            std::cerr << "WARNING: " << errorMsg << " at " << @1 << "\n";
        }
        $$ = $1;
      }
    ;

opt_annotations
    : /* empty */
      {
          $$ = new std::vector<Annotation *>;
      }
    | opt_annotations annotation
      {
          $$ = $1;
          $$->push_back($2);
      }
    ;

annotation
    : '@' IDENTIFIER opt_annotation_params
      {
          $$ = new Annotation($2, $3);
      }
    ;

opt_annotation_params
    : /* empty */
      {
          $$ = new AnnotationParamVector;
      }
    | '(' annotation_params ')'
      {
          $$ = $2;
      }
    ;

annotation_params
    : annotation_param
      {
          $$ = new AnnotationParamVector;
          $$->push_back($1);
      }
    | annotation_params ',' annotation_param
      {
          $$ = $1;
          $$->push_back($3);
      }
    ;

annotation_param
    : IDENTIFIER '=' annotation_string_value
      {
          $$ = new AnnotationParam($1, $3);
      }
    | IDENTIFIER '=' annotation_const_expr_value
      {
          $$ = new AnnotationParam($1, $3);
      }
    ;

annotation_string_value
    : STRING_LITERAL
      {
          $$ = new std::vector<std::string>;
          $$->push_back($1);
      }
    | '{' annotation_string_values '}' { $$ = $2; }
    ;

annotation_string_values
    : STRING_LITERAL
      {
          $$ = new std::vector<std::string>;
          $$->push_back($1);
      }
    | annotation_string_values ',' STRING_LITERAL
      {
          $$ = $1;
          $$->push_back($3);
      }
    ;

annotation_const_expr_value
    : const_expr
      {
          $$ = new std::vector<ConstantExpression *>;
          $$->push_back($1);
      }
    | '{' annotation_const_expr_values '}' { $$ = $2; }
    ;

annotation_const_expr_values
    : const_expr
      {
          $$ = new std::vector<ConstantExpression *>;
          $$->push_back($1);
      }
    | annotation_const_expr_values ',' const_expr
      {
          $$ = $1;
          $$->push_back($3);
      }
    ;

error_stmt
  : error ';'
    {
      $$ = $1;
      ast->addSyntaxError();
      // std::cerr << "WARNING: skipping errors until " << @2 << ".\n";
    }
  ;

opt_error_stmt
  : /* empty */ { $$ = NULL; }
  | error_stmt  { $$ = $1; }
  ;

require_semicolon
    : ';'
    | /* empty */
      {
          std::cerr << "ERROR: missing ; at " << @$ << "\n";
          ast->addSyntaxError();
      }
    ;

program
    : opt_error_stmt
      package
      imports
      body
    ;

fqname
    : FQNAME
      {
          $$ = new FQName($1);
          if(!$$->isValid()) {
              std::cerr << "ERROR: FQName '" << $1 << "' is not valid at "
                        << @1
                        << ".\n";
              YYERROR;
          }
      }
    | valid_identifier
      {
          $$ = new FQName($1);
          if(!$$->isValid()) {
              std::cerr << "ERROR: FQName '" << $1 << "' is not valid at "
                        << @1
                        << ".\n";
              YYERROR;
          }
      }
    ;

fqtype
    : fqname
      {
          $$ = ast->lookupType(*($1));
          if ($$ == NULL) {
              std::cerr << "ERROR: Failed to lookup type '" << $1->string() << "' at "
                        << @1
                        << "\n";

              YYERROR;
          }
      }
    | TYPE
    ;

package
    : PACKAGE FQNAME require_semicolon
      {
          if (!ast->setPackage($2)) {
              std::cerr << "ERROR: Malformed package identifier '"
                        << $2
                        << "' at "
                        << @2
                        << "\n";

              YYERROR;
          }
      }

import_stmt
    : IMPORT FQNAME require_semicolon
      {
          if (!ast->addImport($2)) {
              std::cerr << "ERROR: Unable to import '" << $2 << "' at " << @2
                        << "\n";
              ast->addSyntaxError();
          }
      }
    | IMPORT valid_identifier require_semicolon
      {
          if (!ast->addImport($2)) {
              std::cerr << "ERROR: Unable to import '" << $2 << "' at " << @2
                        << "\n";
              ast->addSyntaxError();
          }
      }
    | IMPORT error_stmt
    ;


imports
    : /* empty */
    | imports import_stmt
    ;

opt_extends
    : /* empty */ { $$ = NULL; }
    | EXTENDS fqtype { $$ = $2; }

body
    : type_declarations
    ;

interface_declarations
    : /* empty */
    | interface_declarations type_declaration
      {
          std::string errorMsg;
          if ($2 != nullptr &&
              $2->isNamedType() &&
              !isValidInterfaceField(static_cast<NamedType *>($2)->localName().c_str(),
                    &errorMsg)) {
              std::cerr << "ERROR: " << errorMsg << " at "
                        << @2 << "\n";
              YYERROR;
          }
      }
    | interface_declarations method_declaration
      {
          std::string errorMsg;
          if ($2 != nullptr &&
              !isValidInterfaceField($2->name().c_str(), &errorMsg)) {
              std::cerr << "ERROR: " << errorMsg << " at "
                        << @2 << "\n";
              YYERROR;
          }

          if ($2 != nullptr) {
            if (!ast->scope()->isInterface()) {
                std::cerr << "ERROR: unknown error in interface declaration at "
                    << @2 << "\n";
                YYERROR;
            }

            Interface *iface = static_cast<Interface *>(ast->scope());
            if (!iface->addMethod($2)) {
                std::cerr << "ERROR: Unable to add method '" << $2->name()
                          << "' at " << @2 << "\n";

                YYERROR;
            }
          }
          // ignore if $2 is nullptr (from error recovery)
      }
    ;

type_declarations
    : /* empty */
    | error_stmt
    | type_declarations type_declaration
    ;

type_declaration
    : opt_annotations type_declaration_body
      {
          if ($2 != nullptr) {
              $2->setAnnotations($1);
          } else if (!$1->empty()) {
              // Since typedefs are always resolved to their target it makes
              // little sense to annotate them and have their annotations
              // impose semantics other than their target type.
              std::cerr << "ERROR: typedefs cannot be annotated. at " << @2
                        << "\n";

              YYERROR;
          }
          $$ = $2;
      }
    ;

type_declaration_body
    : named_struct_or_union_declaration require_semicolon
    | named_enum_declaration require_semicolon
    | typedef_declaration require_semicolon
    | interface_declaration require_semicolon
    ;

interface_declaration
    : INTERFACE valid_identifier opt_extends
      {
          Type *parent = $3;

          if (ast->package() != gIBasePackageFqName) {
              if (!ast->addImport(gIBaseFqName.string().c_str())) {
                  std::cerr << "ERROR: Unable to automatically import '"
                            << gIBaseFqName.string()
                            << "' at " << @$
                            << "\n";
                  YYERROR;
              }
              if (parent == nullptr) {
                parent = ast->lookupType(gIBaseFqName);
              }
          }

          if (parent != NULL && !parent->isInterface()) {
              std::cerr << "ERROR: You can only extend interfaces. at " << @3
                        << "\n";

              YYERROR;
          }

          if ($2[0] != 'I') {
              std::cerr << "ERROR: All interface names must start with an 'I' "
                        << "prefix. at " << @2 << "\n";

              YYERROR;
          }

          Interface *iface = new Interface($2, convertYYLoc(@2), static_cast<Interface *>(parent));

          // Register interface immediately so it can be referenced inside
          // definition.
          std::string errorMsg;
          if (!ast->addScopedType(iface, &errorMsg)) {
              std::cerr << "ERROR: " << errorMsg << " at " << @2 << "\n";
              YYERROR;
          }

          ast->enterScope(iface);
      }
      '{' interface_declarations '}'
      {
          if (!ast->scope()->isInterface()) {
              std::cerr << "ERROR: unknown error in interface declaration at "
                  << @5 << "\n";
              YYERROR;
          }

          Interface *iface = static_cast<Interface *>(ast->scope());
          if (!iface->addAllReservedMethods()) {
              std::cerr << "ERROR: unknown error in adding reserved methods at "
                  << @5 << "\n";
              YYERROR;
          }

          ast->leaveScope();

          $$ = iface;
      }
    ;

typedef_declaration
    : TYPEDEF type valid_identifier
      {
          std::string errorMsg;
          if (!ast->addTypeDef($3, $2, convertYYLoc(@3), &errorMsg)) {
              std::cerr << "ERROR: " << errorMsg << " at " << @3 << "\n";
              YYERROR;
          }

          $$ = nullptr;
      }
    ;

const_expr
    : INTEGER                   { $$ = new ConstantExpression($1); }
    | fqname
      {
          if(!$1->isValidValueName()) {
              std::cerr << "ERROR: '" << $1->string()
                        << "' does not refer to an enum value at "
                        << @1 << ".\n";
              YYERROR;
          }
          if($1->isIdentifier()) {
              std::string identifier = $1->name();
              LocalIdentifier *iden = ast->scope()->lookupIdentifier(identifier);
              if(!iden) {
                  std::cerr << "ERROR: identifier " << $1->string()
                            << " could not be found at " << @1 << ".\n";
                  YYERROR;
              }
              if(!iden->isEnumValue()) {
                  std::cerr << "ERROR: identifier " << $1->string()
                            << " is not an enum value at " << @1 << ".\n";
                  YYERROR;
              }
              $$ = new ConstantExpression(
                      *(static_cast<EnumValue *>(iden)->constExpr()), $1->string());
          } else {
              std::string errorMsg;
              EnumValue *v = ast->lookupEnumValue(*($1), &errorMsg);
              if(v == nullptr) {
                  std::cerr << "ERROR: " << errorMsg << " at " << @1 << ".\n";
                  YYERROR;
              }
              $$ = new ConstantExpression(*(v->constExpr()), $1->string());
          }
      }
    | const_expr '?' const_expr ':' const_expr
      {
          $$ = new ConstantExpression($1, $3, $5);
      }
    | const_expr LOGICAL_OR const_expr  { $$ = new ConstantExpression($1, "||", $3); }
    | const_expr LOGICAL_AND const_expr { $$ = new ConstantExpression($1, "&&", $3); }
    | const_expr '|' const_expr { $$ = new ConstantExpression($1, "|" , $3); }
    | const_expr '^' const_expr { $$ = new ConstantExpression($1, "^" , $3); }
    | const_expr '&' const_expr { $$ = new ConstantExpression($1, "&" , $3); }
    | const_expr EQUALITY const_expr { $$ = new ConstantExpression($1, "==", $3); }
    | const_expr NEQ const_expr { $$ = new ConstantExpression($1, "!=", $3); }
    | const_expr '<' const_expr { $$ = new ConstantExpression($1, "<" , $3); }
    | const_expr '>' const_expr { $$ = new ConstantExpression($1, ">" , $3); }
    | const_expr LEQ const_expr { $$ = new ConstantExpression($1, "<=", $3); }
    | const_expr GEQ const_expr { $$ = new ConstantExpression($1, ">=", $3); }
    | const_expr LSHIFT const_expr { $$ = new ConstantExpression($1, "<<", $3); }
    | const_expr RSHIFT const_expr { $$ = new ConstantExpression($1, ">>", $3); }
    | const_expr '+' const_expr { $$ = new ConstantExpression($1, "+" , $3); }
    | const_expr '-' const_expr { $$ = new ConstantExpression($1, "-" , $3); }
    | const_expr '*' const_expr { $$ = new ConstantExpression($1, "*" , $3); }
    | const_expr '/' const_expr { $$ = new ConstantExpression($1, "/" , $3); }
    | const_expr '%' const_expr { $$ = new ConstantExpression($1, "%" , $3); }
    | '+' const_expr %prec UNARY_PLUS  { $$ = new ConstantExpression("+", $2); }
    | '-' const_expr %prec UNARY_MINUS { $$ = new ConstantExpression("-", $2); }
    | '!' const_expr { $$ = new ConstantExpression("!", $2); }
    | '~' const_expr { $$ = new ConstantExpression("~", $2); }
    | '(' const_expr ')' { $$ = $2; }
    | '(' error ')'
      {
        ast->addSyntaxError();
        // to avoid segfaults
        $$ = new ConstantExpression(ConstantExpression::Zero(ScalarType::KIND_INT32));
      }
    ;

method_declaration
    : error_stmt { $$ = nullptr; }
    | opt_annotations valid_identifier '(' typed_vars ')' require_semicolon
      {
          $$ = new Method($2, $4, new std::vector<TypedVar *>, false, $1);
      }
    | opt_annotations ONEWAY valid_identifier '(' typed_vars ')' require_semicolon
      {
          $$ = new Method($3, $5, new std::vector<TypedVar *>, true, $1);
      }
    | opt_annotations valid_identifier '(' typed_vars ')' GENERATES '(' typed_vars ')' require_semicolon
      {
          $$ = new Method($2, $4, $8, false, $1);
      }
    ;

typed_vars
    : /* empty */
      {
          $$ = new TypedVarVector();
      }
    | typed_var
      {
          $$ = new TypedVarVector();
          if (!$$->add($1)) {
              std::cerr << "ERROR: duplicated argument or result name "
                  << $1->name() << " at " << @1 << "\n";
              ast->addSyntaxError();
          }
      }
    | typed_vars ',' typed_var
      {
          $$ = $1;
          if (!$$->add($3)) {
              std::cerr << "ERROR: duplicated argument or result name "
                  << $3->name() << " at " << @3 << "\n";
              ast->addSyntaxError();
          }
      }
    ;

typed_var : type valid_identifier { $$ = new TypedVar($2, $1); }
    ;


struct_or_union_keyword
    : STRUCT { $$ = CompoundType::STYLE_STRUCT; }
    | UNION { $$ = CompoundType::STYLE_UNION; }
    ;

named_struct_or_union_declaration
    : struct_or_union_keyword valid_identifier
      {
          CompoundType *container = new CompoundType($1, $2, convertYYLoc(@2));
          ast->enterScope(container);
      }
      struct_or_union_body
      {
          if (!ast->scope()->isCompoundType()) {
              std::cerr << "ERROR: unknown error in struct or union declaration at "
                  << @4 << "\n";
              YYERROR;
          }
          CompoundType *container = static_cast<CompoundType *>(ast->scope());

          std::string errorMsg;
          if (!container->setFields($4, &errorMsg)) {
              std::cerr << "ERROR: " << errorMsg << " at " << @4 << "\n";
              YYERROR;
          }

          ast->leaveScope();

          if (!ast->addScopedType(container, &errorMsg)) {
              std::cerr << "ERROR: " << errorMsg << " at " << @2 << "\n";
              YYERROR;
          }

          $$ = container;
      }
    ;

struct_or_union_body
    : '{' field_declarations '}' { $$ = $2; }
    ;

field_declarations
    : /* empty */ { $$ = new std::vector<CompoundField *>; }
    | field_declarations field_declaration
      {
          $$ = $1;

          if ($2 != NULL) {
              $$->push_back($2);
          }
      }
    ;

field_declaration
    : error_stmt { $$ = nullptr; }
    | type valid_identifier require_semicolon
      {
        std::string errorMsg;
        if (ast->scope()->isCompoundType() &&
            static_cast<CompoundType *>(ast->scope())->style() == CompoundType::STYLE_STRUCT &&
            !isValidStructField($2, &errorMsg)) {
            std::cerr << "ERROR: " << errorMsg << " at "
                      << @2 << "\n";
            YYERROR;
        }
        $$ = new CompoundField($2, $1);
      }
    | annotated_compound_declaration ';'
      {
        std::string errorMsg;
        if (ast->scope()->isCompoundType() &&
            static_cast<CompoundType *>(ast->scope())->style() == CompoundType::STYLE_STRUCT &&
            $1 != nullptr &&
            $1->isNamedType() &&
            !isValidStructField(static_cast<NamedType *>($1)->localName().c_str(), &errorMsg)) {
            std::cerr << "ERROR: " << errorMsg << " at "
                      << @2 << "\n";
            YYERROR;
        }
        $$ = NULL;
      }
    ;

annotated_compound_declaration
    : opt_annotations compound_declaration
      {
          $2->setAnnotations($1);
          $$ = $2;
      }
    ;

compound_declaration
    : named_struct_or_union_declaration { $$ = $1; }
    | named_enum_declaration { $$ = $1; }
    ;

enum_storage_type
    : ':' fqtype
      {
          $$ = $2;

          if ($$ != NULL && !$$->isValidEnumStorageType()) {
              std::cerr << "ERROR: Invalid enum storage type specified. at "
                        << @2 << "\n";

              YYERROR;
          }
      }
    ;

opt_comma
    : /* empty */
    | ','
    ;

named_enum_declaration
    : ENUM valid_identifier enum_storage_type
      {
          ast->enterScope(new EnumType($2, convertYYLoc(@2), $3));
      }
      enum_declaration_body
      {
          if (!ast->scope()->isEnum()) {
              std::cerr << "ERROR: unknown error in enum declaration at "
                  << @5 << "\n";
              YYERROR;
          }

          EnumType *enumType = static_cast<EnumType *>(ast->scope());
          ast->leaveScope();

          std::string errorMsg;
          if (!ast->addScopedType(enumType, &errorMsg)) {
              std::cerr << "ERROR: " << errorMsg << " at " << @2 << "\n";
              YYERROR;
          }

          $$ = enumType;
      }
    ;

enum_declaration_body
    : '{' enum_values opt_comma '}' { $$ = $2; }
    ;

enum_value
    : valid_identifier { $$ = new EnumValue($1); }
    | valid_identifier '=' const_expr { $$ = new EnumValue($1, $3); }
    ;

enum_values
    : /* empty */
      { /* do nothing */ }
    | enum_value
      {
          if (!ast->scope()->isEnum()) {
              std::cerr << "ERROR: unknown error in enum declaration at "
                  << @1 << "\n";
              YYERROR;
          }

          static_cast<EnumType *>(ast->scope())->addValue($1);
      }
    | enum_values ',' enum_value
      {
          if (!ast->scope()->isEnum()) {
              std::cerr << "ERROR: unknown error in enum declaration at "
                  << @3 << "\n";
              YYERROR;
          }

          static_cast<EnumType *>(ast->scope())->addValue($3);
      }
    ;

array_type_base
    : fqtype { $$ = $1; }
    | TEMPLATED '<' type '>'
      {
          if (!$1->isCompatibleElementType($3)) {
              std::cerr << "ERROR: " << $1->typeName() << " of " << $3->typeName()
                        << " is not supported. at " << @3 << "\n";

              YYERROR;
          }
          $1->setElementType($3);
          $$ = $1;
      }
    | TEMPLATED '<' TEMPLATED '<' type RSHIFT
      {
          if (!$3->isCompatibleElementType($5)) {
              std::cerr << "ERROR: " << $3->typeName() << " of " << $5->typeName()
                        << " is not supported. at " << @3 << "\n";

              YYERROR;
          }
          $3->setElementType($5);
          if (!$1->isCompatibleElementType($3)) {
              std::cerr << "ERROR: " << $1->typeName() << " of " << $3->typeName()
                        << " is not supported. at " << @3 << "\n";

              YYERROR;
          }
          $1->setElementType($3);
          $$ = $1;
      }
    ;

array_type
    : array_type_base '[' const_expr ']'
      {
          if ($1->isBinder()) {
              std::cerr << "ERROR: Arrays of interface types are not supported."
                        << " at " << @1 << "\n";

              YYERROR;
          }
          if ($1->isArray()) {
              $$ = new ArrayType(static_cast<ArrayType *>($1), $3);
          } else {
              $$ = new ArrayType($1, $3);
          }
      }
    | array_type '[' const_expr ']'
      {
          $$ = $1;
          $$->appendDimension($3);
      }
    ;

type
    : array_type_base { $$ = $1; }
    | array_type { $$ = $1; }
    | annotated_compound_declaration { $$ = $1; }
    | INTERFACE
      {
          // "interface" is a synonym of android.hidl.base@1.0::IBase
          $$ = ast->lookupType(gIBaseFqName);
          if ($$ == nullptr) {
              std::cerr << "FATAL: Cannot find "
                        << gIBaseFqName.string()
                        << " at " << @1 << "\n";

              YYERROR;
      }
    }
    ;

%%

#include <android-base/logging.h>

void yy::parser::error(
        const yy::parser::location_type &where,
        const std::string &errstr) {
    std::cerr << "ERROR: " << errstr << " at " << where << "\n";
}

