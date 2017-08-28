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

package com.android.tradefed.profiler.recorder;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A parser for interpreting lines from /d/tracing/trace and encoding them as {@link TraceLine}s.
 */
public class TraceParser {
    protected static final String REGEX_TASK_NAME = "^\\s*([a-zA-Z0-9_/\\-:<>]*)-\\d+";
    protected static final String REGEX_CPU_NUM = "\\s*\\[(\\d\\d\\d)\\]";
    protected static final String REGEX_FLAG_CLUSTER = "\\s*([d\\.])(.)(.)(\\d)";
    protected static final String REGEX_TIMESTAMP = "\\s*(\\d{1,5}\\.\\d{1,6}):";
    protected static final String REGEX_FUNCTION_NAME = "\\s*(\\w+):";
    protected static final String REGEX_FUNCTION_PARAMS = " (.*)$";

    private static final int GRP_TASK_NAME = 1;
    private static final int GRP_CPU_NUM = 2;
    private static final int GRP_IRQS_OFF = 3;
    private static final int GRP_RESCHED = 4;
    private static final int GRP_HARD_IRQ = 5;
    private static final int GRP_PREEMPT = 6;
    private static final int GRP_TIMESTAMP = 7;
    private static final int GRP_FUNC_NAME = 8;
    private static final int GRP_FUNC_PARAM = 9;

    protected static final Pattern TRACE_LINE =
            Pattern.compile(
                    REGEX_TASK_NAME
                            + REGEX_CPU_NUM
                            + REGEX_FLAG_CLUSTER
                            + REGEX_TIMESTAMP
                            + REGEX_FUNCTION_NAME
                            + REGEX_FUNCTION_PARAMS);

    protected Map<String, Long> parseFunctionParams(String paramString) {
        String[] pairs = paramString.split(",");
        Map<String, Long> paramMap = new HashMap<>();
        for (String param : pairs) {
            String[] splitParam = param.split("=");
            String k = splitParam[0];
            String v = splitParam[1];
            if (v.length() >= 2 && v.charAt(1) == 'x') {
                paramMap.put(k, Long.parseLong(v.substring(2), 16));
            } else {
                paramMap.put(k, Long.parseLong(v));
            }
        }
        return paramMap;
    }

    public TraceLine parseTraceLine(String line) {
        Matcher m = TRACE_LINE.matcher(line);
        if (!m.find()) {
            throw new IllegalArgumentException("Didn't match line: " + line);
        }
        TraceLine descriptor = new TraceLine();
        descriptor.setTaskName(m.group(GRP_TASK_NAME));
        descriptor.setCoreNum(Integer.parseInt(m.group(GRP_CPU_NUM)));
        descriptor.setIrqsOff(!m.group(GRP_IRQS_OFF).equals("."));
        descriptor.setNeedsResched(!m.group(GRP_RESCHED).equals("."));
        descriptor.setHardIrq(m.group(GRP_HARD_IRQ).equals("H"));
        descriptor.setPreemptDelay(Integer.parseInt(m.group(GRP_PREEMPT)));
        descriptor.setTimestamp(Double.parseDouble(m.group(GRP_TIMESTAMP)));
        descriptor.setFunctionName(m.group(GRP_FUNC_NAME));
        descriptor.setFunctionParams(parseFunctionParams(m.group(GRP_FUNC_PARAM)));
        return descriptor;
    }
}
