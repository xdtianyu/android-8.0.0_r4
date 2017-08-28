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
package com.android.tradefed.invoker.shard;

import static org.junit.Assert.*;

import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.command.CommandOptions;
import com.android.tradefed.config.Configuration;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.IRescheduler;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.result.ILogSaver;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.StubTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;

/** Unit tests for {@link StrictShardHelper}. */
@RunWith(JUnit4.class)
public class StrictShardHelperTest {

    private StrictShardHelper mHelper;
    private IConfiguration mConfig;
    private ILogSaver mMockLogSaver;
    private IInvocationContext mContext;
    private IRescheduler mRescheduler;

    @Before
    public void setUp() {
        mHelper = new StrictShardHelper();
        mConfig = new Configuration("fake_sharding_config", "desc");
        mContext = new InvocationContext();
        mContext.addDeviceBuildInfo("default", new BuildInfo());
        mRescheduler = Mockito.mock(IRescheduler.class);
        mMockLogSaver = Mockito.mock(ILogSaver.class);
        mConfig.setLogSaver(mMockLogSaver);
    }

    /** Test sharding using Tradefed internal algorithm. */
    @Test
    public void testShardConfig_internal() throws Exception {
        CommandOptions options = new CommandOptions();
        OptionSetter setter = new OptionSetter(options);
        setter.setOptionValue("disable-strict-sharding", "true");
        setter.setOptionValue("shard-count", "5");
        mConfig.setCommandOptions(options);
        mConfig.setCommandLine(new String[] {"empty"});
        StubTest test = new StubTest();
        setter = new OptionSetter(test);
        setter.setOptionValue("num-shards", "5");
        mConfig.setTest(test);
        assertEquals(1, mConfig.getTests().size());
        assertTrue(mHelper.shardConfig(mConfig, mContext, mRescheduler));
        // Ensure that we did split 1 tests per shard rescheduled.
        Mockito.verify(mRescheduler, Mockito.times(5))
                .scheduleConfig(
                        Mockito.argThat(
                                new ArgumentMatcher<IConfiguration>() {
                                    @Override
                                    public boolean matches(IConfiguration argument) {
                                        assertEquals(1, argument.getTests().size());
                                        return true;
                                    }
                                }));
    }

    /** Test sharding using Tradefed internal algorithm. */
    @Test
    public void testShardConfig_internal_shardIndex() throws Exception {
        CommandOptions options = new CommandOptions();
        OptionSetter setter = new OptionSetter(options);
        setter.setOptionValue("disable-strict-sharding", "true");
        setter.setOptionValue("shard-count", "5");
        setter.setOptionValue("shard-index", "2");
        mConfig.setCommandOptions(options);
        mConfig.setCommandLine(new String[] {"empty"});
        StubTest test = new StubTest();
        setter = new OptionSetter(test);
        setter.setOptionValue("num-shards", "5");
        mConfig.setTest(test);
        assertEquals(1, mConfig.getTests().size());
        // We do not shard, we are relying on the current invocation to run.
        assertFalse(mHelper.shardConfig(mConfig, mContext, mRescheduler));
        // Rescheduled is NOT called because we use the current invocation to run the index.
        Mockito.verify(mRescheduler, Mockito.times(0)).scheduleConfig(Mockito.any());
        assertEquals(1, mConfig.getTests().size());
        // Original IRemoteTest was replaced by the sharded one in the configuration.
        assertNotEquals(test, mConfig.getTests().get(0));
    }

    /**
     * Test sharding using Tradefed internal algorithm. On a non shardable IRemoteTest and getting
     * the shard 0.
     */
    @Test
    public void testShardConfig_internal_shardIndex_notShardable_shard0() throws Exception {
        CommandOptions options = new CommandOptions();
        OptionSetter setter = new OptionSetter(options);
        setter.setOptionValue("disable-strict-sharding", "true");
        setter.setOptionValue("shard-count", "5");
        setter.setOptionValue("shard-index", "0");
        mConfig.setCommandOptions(options);
        mConfig.setCommandLine(new String[] {"empty"});
        IRemoteTest test =
                new IRemoteTest() {
                    @Override
                    public void run(ITestInvocationListener listener)
                            throws DeviceNotAvailableException {
                        // do nothing.
                    }
                };
        mConfig.setTest(test);
        assertEquals(1, mConfig.getTests().size());
        // We do not shard, we are relying on the current invocation to run.
        assertFalse(mHelper.shardConfig(mConfig, mContext, mRescheduler));
        // Rescheduled is NOT called because we use the current invocation to run the index.
        Mockito.verify(mRescheduler, Mockito.times(0)).scheduleConfig(Mockito.any());
        assertEquals(1, mConfig.getTests().size());
        // Original IRemoteTest is the same since the test was not shardable
        assertSame(test, mConfig.getTests().get(0));
    }

    /**
     * Test sharding using Tradefed internal algorithm. On a non shardable IRemoteTest and getting
     * the shard 1.
     */
    @Test
    public void testShardConfig_internal_shardIndex_notShardable_shard1() throws Exception {
        CommandOptions options = new CommandOptions();
        OptionSetter setter = new OptionSetter(options);
        setter.setOptionValue("disable-strict-sharding", "true");
        setter.setOptionValue("shard-count", "5");
        setter.setOptionValue("shard-index", "1");
        mConfig.setCommandOptions(options);
        mConfig.setCommandLine(new String[] {"empty"});
        IRemoteTest test =
                new IRemoteTest() {
                    @Override
                    public void run(ITestInvocationListener listener)
                            throws DeviceNotAvailableException {
                        // do nothing.
                    }
                };
        mConfig.setTest(test);
        assertEquals(1, mConfig.getTests().size());
        // We do not shard, we are relying on the current invocation to run.
        assertFalse(mHelper.shardConfig(mConfig, mContext, mRescheduler));
        // Rescheduled is NOT called because we use the current invocation to run the index.
        Mockito.verify(mRescheduler, Mockito.times(0)).scheduleConfig(Mockito.any());
        // We have no tests to put in shard-index 1 so it's empty.
        assertEquals(0, mConfig.getTests().size());
    }
}
