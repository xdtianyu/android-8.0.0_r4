/*
 * Copyright (C) 2017 The Android Open Source Project
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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RunWith(JUnit4.class)
public class TraceParserTest {

    TraceParser mParser;

    @Before
    public void setUp() throws Exception {
        mParser = new TraceParser();
    }

    @Test
    public void testParseTaskName() {
        String line = "         mmcqd/0-260 ";
        Matcher m = Pattern.compile(TraceParser.REGEX_TASK_NAME).matcher(line);
        m.find();
        Assert.assertEquals("mmcqd/0", m.group(1).trim());
    }

    @Test
    public void testParseCpuNum() {
        String line = "   [002] ";
        Matcher m = Pattern.compile(TraceParser.REGEX_CPU_NUM).matcher(line);
        m.find();
        Assert.assertEquals("002", m.group(1).trim());
    }

    @Test
    public void testParseFlagCluster() {
        String line = " d.h4 ";
        Matcher m = Pattern.compile(TraceParser.REGEX_FLAG_CLUSTER).matcher(line);
        m.find();
        Assert.assertEquals("d", m.group(1).trim());
        Assert.assertEquals(".", m.group(2).trim());
        Assert.assertEquals("h", m.group(3).trim());
        Assert.assertEquals("4", m.group(4).trim());
    }

    @Test
    public void testParseTimestamp() {
        String line = " 87062.464667: ";
        Matcher m = Pattern.compile(TraceParser.REGEX_TIMESTAMP).matcher(line);
        m.find();
        Assert.assertEquals("87062.464667", m.group(1).trim());
    }

    @Test
    public void testParseFunctionName() {
        String line = " mmc_cmd_rw_end: ";
        Matcher m = Pattern.compile(TraceParser.REGEX_FUNCTION_NAME).matcher(line);
        m.find();
        Assert.assertEquals("mmc_cmd_rw_end", m.group(1));
    }

    @Test
    public void testParseFunctionParams() {
        String line = "cmd=25,int_status=0x00000001,response=0x00000010";
        Map<String, Long> params = mParser.parseFunctionParams(line);
        Assert.assertTrue(params.containsKey("cmd"));
        Assert.assertTrue(params.containsKey("int_status"));
        Assert.assertTrue(params.containsKey("response"));
        Assert.assertEquals(Long.valueOf(25), params.get("cmd"));
        Assert.assertEquals(Long.valueOf(1), params.get("int_status"));
        Assert.assertEquals(Long.valueOf(16), params.get("response"));
    }

    @Test
    public void testParseWholeTraceLine() {
        String line =
                "         mmcqd/0-260   [000] d.h2 87062.464736: mmc_cmd_rw_end: cmd=25,int_status=0x00000001,response=0x00000900";
        Matcher m = TraceParser.TRACE_LINE.matcher(line);
        Assert.assertTrue(m.find());
        Assert.assertEquals("mmcqd/0", m.group(1));
        Assert.assertEquals("000", m.group(2));
        Assert.assertEquals("d", m.group(3));
        Assert.assertEquals(".", m.group(4));
        Assert.assertEquals("h", m.group(5));
        Assert.assertEquals("2", m.group(6));
        Assert.assertEquals("87062.464736", m.group(7));
        Assert.assertEquals("mmc_cmd_rw_end", m.group(8));
        Assert.assertEquals("cmd=25,int_status=0x00000001,response=0x00000900", m.group(9));
        line =
                "          mmcqd/0-260   [000] ...1    58.216426: mmc_blk_rw_start: cmd=18,addr=0x0039f640,size=0x00000020";
        m = TraceParser.TRACE_LINE.matcher(line);
        Assert.assertTrue(m.find());
    }
}
