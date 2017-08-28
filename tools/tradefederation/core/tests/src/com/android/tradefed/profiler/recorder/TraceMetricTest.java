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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit Tests for {@link TraceMetric}. */
@RunWith(JUnit4.class)
public class TraceMetricTest {

    @Test
    public void testParse_noExpectedVal() {
        String line = "mmc:mmc_cmd_rw_start:response:COUNT";
        TraceMetric m = TraceMetric.parse(line);
        Assert.assertEquals("mmc", m.getPrefix());
        Assert.assertEquals("mmc_cmd_rw_start", m.getFuncName());
        Assert.assertEquals("response", m.getParam());
        Assert.assertEquals(MetricType.COUNT, m.getMetricType());
    }

    @Test
    public void testParse_expectedValHex() {
        String line = "mmc:mmc_cmd_rw_start:response=0x00000090:COUNT";
        TraceMetric m = TraceMetric.parse(line);
        Assert.assertEquals("mmc", m.getPrefix());
        Assert.assertEquals("mmc_cmd_rw_start", m.getFuncName());
        Assert.assertEquals("response", m.getParam());
        Assert.assertEquals(Double.valueOf(144), m.getExpectedVal());
        Assert.assertEquals(MetricType.COUNT, m.getMetricType());
    }

    @Test
    public void testParse_expectedValDec() {
        String line = "mmc:mmc_cmd_rw_start:response=90:COUNT";
        TraceMetric m = TraceMetric.parse(line);
        Assert.assertEquals("mmc", m.getPrefix());
        Assert.assertEquals("mmc_cmd_rw_start", m.getFuncName());
        Assert.assertEquals("response", m.getParam());
        Assert.assertEquals(Double.valueOf(90), m.getExpectedVal());
        Assert.assertEquals(MetricType.COUNT, m.getMetricType());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParse_badFmt() {
        String line = "mmc:mmcfoobar:zz;fjdkls";
        TraceMetric.parse(line);
    }
}
