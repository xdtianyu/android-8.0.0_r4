/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.networkrecommendation.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;

/**
 * Utilities for parsing and serializing Comma-Separated Value data.
 * See http://en.wikipedia.org/wiki/Comma-separated_values and
 * http://tools.ietf.org/html/rfc4180 for details of the format.
 */
public class Csv {
    /** Field delimiter.  The C in CSV. */
    public static final String COMMA = ",";

    /** Record delimiter.  "Proper" CSV uses CR-LF, but that would be annoying. */
    public static final String NEWLINE = "\n";

    /**
     * Appends a single value to an output string.  If the value
     * contains quotes, commas, newlines, or leading or trailing whitespace,
     * the value will be written in quotes (with internal quotes doubled).
     * Newlines are converted to standard CR-LF form.  This function does not
     * write field delimiters -- append {@link COMMA} yourself between values.
     *
     * @param value to write, quoted as necessary; must be non-null.
     * @param output to append (possibly quoted) value to
     * @throws java.io.IOException if writing to 'output' fails
     */
    public static void writeValue(String value, Appendable output) throws IOException {
        int len = value.length();
        if (len == 0) return;

        char first = value.charAt(0);
        char last = value.charAt(len - 1);
        if (first != ' ' && first != '\t' && last != ' ' && last != '\t' &&
                value.indexOf('"') < 0 && value.indexOf(',') < 0 &&
                value.indexOf('\r') < 0 && value.indexOf('\n') < 0) {
            // No quoting needed.
            output.append(value);
            return;
        }

        output.append('"').append(value.replace("\"", "\"\"")).append('"');
    }

    /**
     * Parse a record of comma separated values from an input file.
     * May read multiple physical lines if values contain embedded newlines.
     *
     * @param reader to read one or more physical lines from
     * @param out array to append unquoted CSV values to
     * @return true if values were read, false on EOF
     * @throws java.io.IOException if reading from 'reader' fails
     */
    public static boolean parseLine(BufferedReader reader, List<String> out) throws IOException {
        String text = reader.readLine();
        if (text == null) return false;

        int pos = 0;
        do {
            StringBuilder buf = new StringBuilder();
            int comma;
            for (;;) {
                comma = text.indexOf(',', pos);
                int quote = text.indexOf('"', pos);
                if (quote == -1 || (comma != -1 && comma < quote)) break;

                if (pos > 0 && text.charAt(pos - 1) == '"') buf.append('"');
                buf.append(text, pos, quote);
                while ((quote = text.indexOf('"', (pos = quote + 1))) == -1) {
                    buf.append(text, pos, text.length()).append('\n');
                    text = reader.readLine();
                    if (text == null) {
                        out.add(buf.toString());
                        return true;
                    }
                    quote = -1;
                }

                buf.append(text, pos, quote);
                pos = quote + 1;
            }

            buf.append(text, pos, comma == -1 ? text.length() : comma);
            out.add(buf.toString());
            pos = comma + 1;
        } while (pos > 0);
        return true;
    }

    private Csv() {}  // Do not instantiate
}
