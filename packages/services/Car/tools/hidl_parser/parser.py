#!/usr/bin/env python3
#
# Copyright (C) 2017 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# A parser for enum types defined in HIDL.
# This script can parse HIDL files and generate a parse tree.
# To use, import and call parse("path/to/file.hal")
# It will return a Python dictionary with two keys:
#  - header: an instance of Header
#  - enums: a dictionary of EnumDecl objects by name
# This script cannot parse structs for now, but that would be easy to add.

# It requires 'ply' (Python Lex/Yacc).

import ply

tokens = ('package', 'import', 'enum',
    'COLON', 'IDENTIFIER', 'COMMENT', 'NUMBER', 'HEX', 'OR', 'EQUALS',
    'LPAREN', 'RPAREN', 'LBRACE', 'RBRACE', 'DOT', 'SEMICOLON', 'VERSION',
    'COMMA', 'SHIFT')

t_COLON = r':'
t_NUMBER = r'[0-9]+'
t_HEX = r'0x[0-9A-Fa-f]+'
t_OR = r'\|'
t_EQUALS = r'='
t_LPAREN = r'\('
t_RPAREN = r'\)'
t_SHIFT = r'<<'

def t_COMMENT(t):
    r'(/\*(.|\n)*?\*/)|(//.*)'
    pass

t_LBRACE = r'{'
t_RBRACE = r'}'
t_DOT = r'\.'
t_SEMICOLON = r';'
t_VERSION = r'@[0-9].[0-9]'
t_COMMA = r','
t_ignore = ' \n\t'

def t_IDENTIFIER(t):
    r'[a-zA-Z_][a-zA-Z_0-9]*'
    if t.value == 'package':
        t.type = 'package'
    elif t.value == 'import':
        t.type = 'import'
    elif t.value == 'enum':
        t.type = 'enum'
    return t

def t_error(t):
    t.type = t.value[0]
    t.value = t.value[0]
    t.lexer.skip(1)
    return t

import ply.lex as lex
lexer = lex.lex()

class EnumHeader(object):
    def __init__(self, name, base):
        self.name = name
        self.base = base

    def __str__(self):
        return '%s%s' % (self.name, ' %s' % self.base if self.base else '')

class EnumDecl(object):
    def __init__(self, header, cases):
        self.header = header
        self.cases = cases

    def __str__(self):
        return '%s {\n%s\n}' % (self.header,
            '\n'.join(str(x) for x in self.cases))

    def __repr__(self):
        return self.__str__()

class EnumCase(object):
    def __init__(self, name, value):
        self.name = name
        self.value = value

    def __str__(self):
        return '%s = %s' % (self.name, self.value)

class PackageID(object):
    def __init__(self, name, version):
        self.name = name
        self.version = version

    def __str__(self):
        return '%s%s' % (self.name, self.version)

class Package(object):
    def __init__(self, package):
        self.package = package

    def __str__(self):
        return 'package %s' % self.package

class Import(object):
    def __init__(self, package):
        self.package = package

    def __str__(self):
        return 'import %s' % self.package

class Header(object):
    def __init__(self, package, imports):
        self.package = package
        self.imports = imports

    def __str__(self):
        return str(self.package) + "\n" + \
            '\n'.join(str(x) for x in self.imports)

# Error rule for syntax errors
def p_error(p):
    print("Syntax error in input: %s" % p)

def p_document(t):
    'document : header enum_decls'
    enums = {}
    for enum in t[2]:
        enums[enum.header.name] = enum
    t[0] = {'header' : t[1], 'enums' : enums}

def p_enum_decls_1(t):
    'enum_decls : enum_decl'
    t[0] = [t[1]]
def p_enum_decls_2(t):
    'enum_decls : enum_decls enum_decl'
    t[0] = t[1] + [t[2]]

def p_enum_cases_1(t):
    'enum_cases : enum_case'
    t[0] = [t[1]]
def p_enum_cases_2(t):
    'enum_cases : enum_cases COMMA enum_case'
    t[0] = t[1] + [t[3]]

def p_enum_base_1(t):
    'enum_base : VERSION COLON COLON IDENTIFIER'
    t[0] = '%s::%s' % (t[1], t[4])
def p_enum_base_2(t):
    'enum_base : IDENTIFIER'
    t[0] = t[1]

def p_enum_header_1(t):
    'enum_header : enum IDENTIFIER'
    t[0] = EnumHeader(t[2], None)
def p_enum_header_2(t):
    'enum_header : enum IDENTIFIER COLON enum_base'
    t[0] = EnumHeader(t[2], t[4])

def p_enum_decl_1(t):
    'enum_decl : enum_header LBRACE enum_cases RBRACE SEMICOLON'
    t[0] = EnumDecl(t[1], t[3])
def p_enum_decl_2(t):
    'enum_decl : enum_header LBRACE enum_cases COMMA RBRACE SEMICOLON'
    t[0] = EnumDecl(t[1], t[3])

def p_enum_value_1(t):
    '''enum_value : NUMBER
                  | HEX
                  | IDENTIFIER'''
    t[0] = t[1]
def p_enum_value_2(t):
    'enum_value : enum_value SHIFT NUMBER'
    t[0] = '%s << %s' % (t[1], t[3])
def p_enum_value_3(t):
    'enum_value : enum_value OR enum_value'
    t[0] = "%s | %s" % (t[1], t[3])
def p_enum_value_4(t):
    'enum_value : LPAREN enum_value RPAREN'
    t[0] = t[2]
def p_enum_value_5(t):
    'enum_value : IDENTIFIER COLON IDENTIFIER'
    t[0] = '%s:%s' % (t[1],t[3])

def p_enum_case(t):
    'enum_case : IDENTIFIER EQUALS enum_value'
    t[0] = EnumCase(t[1], t[3])

def p_header_1(t):
    'header : package_decl'
    t[0] = Header(t[1], [])

def p_header_2(t):
    'header : package_decl import_decls'
    t[0] = Header(t[1], t[2])

def p_import_decls_1(t):
    'import_decls : import_decl'
    t[0] = [t[1]]

def p_import_decls_2(t):
    'import_decls : import_decls import_decl'
    t[0] = t[1] + [t[2]]

def p_package_decl(t):
    'package_decl : package package_ID SEMICOLON'
    t[0] = Package(t[2])

def p_import_decl(t):
    'import_decl : import package_ID SEMICOLON'
    t[0] = Import(t[2])

def p_package_ID(t):
    'package_ID : dotted_identifier VERSION'
    t[0] = PackageID(t[1], t[2])

def p_dotted_identifier_1(t):
    'dotted_identifier : IDENTIFIER'
    t[0] = t[1]
def p_dotted_identifier_2(t):
    'dotted_identifier : dotted_identifier DOT IDENTIFIER'
    t[0] = t[1] + '.' + t[3]

class SilentLogger(object):
    def warning(*args):
        pass

import ply.yacc as yacc
parser = yacc.yacc(debug=False, write_tables=False, errorlog=SilentLogger())
import sys

def parse(filename):
    return parser.parse(open(filename, 'r').read())
