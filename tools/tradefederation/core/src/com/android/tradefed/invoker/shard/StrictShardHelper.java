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

import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.IRescheduler;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.IShardableTest;
import com.android.tradefed.testtype.IStrictShardableTest;
import com.android.tradefed.testtype.suite.ITestSuite;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Sharding strategy to create strict shards that do not report together, */
public class StrictShardHelper extends ShardHelper {

    /** {@inheritDoc} */
    @Override
    public boolean shardConfig(
            IConfiguration config, IInvocationContext context, IRescheduler rescheduler) {
        Integer shardCount = config.getCommandOptions().getShardCount();
        Integer shardIndex = config.getCommandOptions().getShardIndex();

        if (shardIndex == null) {
            return super.shardConfig(config, context, rescheduler);
        }
        if (shardCount == null) {
            throw new RuntimeException("shard-count is null while shard-index is " + shardIndex);
        }

        // Split tests in place, without actually sharding.
        if (!config.getCommandOptions().shouldUseTfSharding()) {
            // TODO: remove when IStrictShardableTest is removed.
            updateConfigIfSharded(config, shardCount, shardIndex);
        } else {
            List<IRemoteTest> listAllTests = getAllTests(config, shardCount, context);
            config.setTests(splitTests(listAllTests, shardCount, shardIndex));
        }
        return false;
    }

    // TODO: Retire IStrictShardableTest for IShardableTest and have TF balance the list of tests.
    private void updateConfigIfSharded(IConfiguration config, int shardCount, int shardIndex) {
        List<IRemoteTest> testShards = new ArrayList<>();
        for (IRemoteTest test : config.getTests()) {
            if (!(test instanceof IStrictShardableTest)) {
                CLog.w(
                        "%s is not shardable; the whole test will run in shard 0",
                        test.getClass().getName());
                if (shardIndex == 0) {
                    testShards.add(test);
                }
                continue;
            }
            IRemoteTest testShard =
                    ((IStrictShardableTest) test).getTestShard(shardCount, shardIndex);
            testShards.add(testShard);
        }
        config.setTests(testShards);
    }

    /**
     * Helper to return the full list of {@link IRemoteTest} based on {@link IShardableTest} split.
     *
     * @param config the {@link IConfiguration} describing the invocation.
     * @param shardCount the shard count hint to be provided to some tests.
     * @param context the {@link IInvocationContext} of the parent invocation.
     * @return the list of all {@link IRemoteTest}.
     */
    private List<IRemoteTest> getAllTests(
            IConfiguration config, Integer shardCount, IInvocationContext context) {
        List<IRemoteTest> allTests = new ArrayList<>();
        for (IRemoteTest test : config.getTests()) {
            if (test instanceof IShardableTest) {
                // Inject current information to help with sharding
                if (test instanceof IBuildReceiver) {
                    ((IBuildReceiver) test).setBuild(context.getBuildInfos().get(0));
                }
                if (test instanceof IDeviceTest) {
                    ((IDeviceTest) test).setDevice(context.getDevices().get(0));
                }
                // Handling of the ITestSuite is a special case, we do not allow pool of tests
                // since each shard needs to be independent.
                if (test instanceof ITestSuite) {
                    ((ITestSuite) test).setShouldMakeDynamicModule(false);
                }

                Collection<IRemoteTest> subTests = ((IShardableTest) test).split(shardCount);
                if (subTests == null) {
                    // test did not shard so we add it as is.
                    allTests.add(test);
                } else {
                    allTests.addAll(subTests);
                }
            } else {
                // if test is not shardable we add it as is.
                allTests.add(test);
            }
        }
        return allTests;
    }

    /**
     * Split the list of tests to run however the implementation see fit. Sharding needs to be
     * consistent. It is acceptable to return an empty list if no tests can be run in the shard.
     *
     * <p>Implement this in order to provide a test suite specific sharding. The default
     * implementation strictly split by number of tests which is not always optimal. TODO: improve
     * the splitting criteria.
     *
     * @param fullList the initial full list of {@link IRemoteTest} containing all the tests that
     *     need to run.
     * @param shardCount the total number of shard that need to run.
     * @param shardIndex the index of the current shard that needs to run.
     * @return a list of {@link IRemoteTest} that need to run in the current shard.
     */
    private List<IRemoteTest> splitTests(
            List<IRemoteTest> fullList, int shardCount, int shardIndex) {
        if (shardCount == 1) {
            // Not sharded
            return fullList;
        }
        if (shardIndex >= fullList.size()) {
            // Return empty list when we don't have enough tests for all the shards.
            return new ArrayList<IRemoteTest>();
        }
        int numPerShard = (int) Math.ceil(fullList.size() / (float) shardCount);
        if (shardIndex == shardCount - 1) {
            // last shard take everything remaining.
            return fullList.subList(shardIndex * numPerShard, fullList.size());
        }
        return fullList.subList(shardIndex * numPerShard, numPerShard + (shardIndex * numPerShard));
    }
}
