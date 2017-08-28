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

#include "Formatter.h"

#include <assert.h>

namespace android {

Formatter::Formatter(FILE *file)
    : mFile(file == NULL ? stdout : file),
      mIndentDepth(0),
      mAtStartOfLine(true) {
}

Formatter::~Formatter() {
    if (mFile != stdout) {
        fclose(mFile);
    }
    mFile = NULL;
}

void Formatter::indent(size_t level) {
    mIndentDepth += level;
}

void Formatter::unindent(size_t level) {
    assert(mIndentDepth >= level);
    mIndentDepth -= level;
}

Formatter &Formatter::indent(size_t level, std::function<void(void)> func) {
    this->indent(level);
    func();
    this->unindent(level);
    return *this;
}

Formatter &Formatter::indent(std::function<void(void)> func) {
    return this->indent(1, func);
}

Formatter &Formatter::block(std::function<void(void)> func) {
    (*this) << "{\n";
    this->indent(func);
    return (*this) << "}";
}

void Formatter::setLinePrefix(const std::string &prefix) {
    mLinePrefix = prefix;
}

void Formatter::unsetLinePrefix() {
    mLinePrefix = "";
}

Formatter &Formatter::endl() {
    return (*this) << "\n";
}

Formatter &Formatter::sIf(const std::string &cond, std::function<void(void)> block) {
    (*this) << "if (" << cond << ") ";
    return this->block(block);
}

Formatter &Formatter::sElseIf(const std::string &cond, std::function<void(void)> block) {
    (*this) << " else if (" << cond << ") ";
    return this->block(block);
}

Formatter &Formatter::sElse(std::function<void(void)> block) {
    (*this) << " else ";
    return this->block(block);
}

Formatter &Formatter::sFor(const std::string &stmts, std::function<void(void)> block) {
    (*this) << "for (" << stmts << ") ";
    return this->block(block);
}

Formatter &Formatter::sTry(std::function<void(void)> block) {
    (*this) << "try ";
    return this->block(block);
}

Formatter &Formatter::sCatch(const std::string &exception, std::function<void(void)> block) {
    (*this) << " catch (" << exception << ") ";
    return this->block(block);
}

Formatter &Formatter::sFinally(std::function<void(void)> block) {
    (*this) << " finally ";
    return this->block(block);
}

Formatter &Formatter::sWhile(const std::string &cond, std::function<void(void)> block) {
    (*this) << "while (" << cond << ") ";
    return this->block(block);
}

Formatter &Formatter::operator<<(const std::string &out) {
    const size_t len = out.length();
    size_t start = 0;
    while (start < len) {
        size_t pos = out.find("\n", start);

        if (pos == std::string::npos) {
            if (mAtStartOfLine) {
                fprintf(mFile, "%s", mLinePrefix.c_str());
                fprintf(mFile, "%*s", (int)(4 * mIndentDepth), "");
                mAtStartOfLine = false;
            }

            output(out.substr(start));
            break;
        }

        if (pos == start) {
            fprintf(mFile, "\n");
            mAtStartOfLine = true;
        } else if (pos > start) {
            if (mAtStartOfLine) {
                fprintf(mFile, "%s", mLinePrefix.c_str());
                fprintf(mFile, "%*s", (int)(4 * mIndentDepth), "");
            }

            output(out.substr(start, pos - start + 1));

            mAtStartOfLine = true;
        }

        start = pos + 1;
    }

    return *this;
}

#define FORMATTER_INPUT_INTEGER(__type__)               \
    Formatter &Formatter::operator<<(__type__ n) {      \
        return (*this) << std::to_string(n);            \
    }                                                   \

FORMATTER_INPUT_INTEGER(short);
FORMATTER_INPUT_INTEGER(unsigned short);
FORMATTER_INPUT_INTEGER(int);
FORMATTER_INPUT_INTEGER(unsigned int);
FORMATTER_INPUT_INTEGER(long);
FORMATTER_INPUT_INTEGER(unsigned long);
FORMATTER_INPUT_INTEGER(long long);
FORMATTER_INPUT_INTEGER(unsigned long long);
FORMATTER_INPUT_INTEGER(float);
FORMATTER_INPUT_INTEGER(double);
FORMATTER_INPUT_INTEGER(long double);

#undef FORMATTER_INPUT_INTEGER

#define FORMATTER_INPUT_CHAR(__type__)                  \
    Formatter &Formatter::operator<<(__type__ c) {      \
        return (*this) << std::string(1, (char)c);    \
    }                                                   \

FORMATTER_INPUT_CHAR(char);
FORMATTER_INPUT_CHAR(signed char);
FORMATTER_INPUT_CHAR(unsigned char);

#undef FORMATTER_INPUT_CHAR

void Formatter::setNamespace(const std::string &space) {
    mSpace = space;
}

void Formatter::output(const std::string &text) const {
    const size_t spaceLength = mSpace.size();
    if (spaceLength > 0) {
        // Remove all occurences of "mSpace" and output the filtered result.
        size_t matchPos = text.find(mSpace);
        if (matchPos != std::string::npos) {
            std::string newText = text.substr(0, matchPos);
            size_t startPos = matchPos + spaceLength;
            while ((matchPos = text.find(mSpace, startPos))
                    != std::string::npos) {
                newText.append(text.substr(startPos, matchPos - startPos));
                startPos = matchPos + spaceLength;
            }
            newText.append(text.substr(startPos));
            fprintf(mFile, "%s", newText.c_str());
            return;
        }
    }

    fprintf(mFile, "%s", text.c_str());
}

}  // namespace android
