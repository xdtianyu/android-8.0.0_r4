/*
 * Copyright (C) 2011 The Android Open Source Project
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
package com.android.tradefed.invoker;

import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ILogSaver;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogSaverResultForwarder;
import com.android.tradefed.result.ResultForwarder;
import com.android.tradefed.util.TimeUtil;

import java.util.List;

/**
 * A {@link ResultForwarder} that combines the results of a sharded test invocations. It only
 * reports completion of the invocation to the listeners once all sharded invocations are complete.
 *
 * <p>This class is not thread safe. It is expected that clients will lock on this class when
 * sending test results, to prevent invocation callbacks from being called out of order.
 */
public class ShardMasterResultForwarder extends LogSaverResultForwarder {

    private final int mInitCount;
    private int mShardsRemaining;
    private int mTotalElapsed = 0;
    private boolean mStartReported = false;

    private long mFirstShardEndTime = 0l;

    /**
     * Create a {@link ShardMasterResultForwarder}.
     *
     * @param listeners the list of {@link ITestInvocationListener} to forward results to when all
     *            shards are completed
     * @param expectedShards the number of shards
     */
    public ShardMasterResultForwarder(ILogSaver logSaver,
            List<ITestInvocationListener> listeners, int expectedShards) {
        super(logSaver, listeners);
        mShardsRemaining = expectedShards;
        mInitCount = expectedShards;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invocationStarted(IInvocationContext context) {
        if (!mStartReported) {
            super.invocationStarted(context);
            mStartReported = true;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invocationFailed(Throwable cause) {
        // one of the shards failed. Fail the whole invocation
        // TODO: does any extra logging need to be done ?
        super.invocationFailed(cause);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invocationEnded(long elapsedTime) {
        mTotalElapsed += elapsedTime;
        if (mInitCount == mShardsRemaining) {
            mFirstShardEndTime = System.currentTimeMillis();
        }
        mShardsRemaining--;
        if (mShardsRemaining <= 0) {
            // TODO: consider logging all shard final times.
            CLog.i(
                    "There was %s between the first and last shard ended.",
                    TimeUtil.formatElapsedTime(System.currentTimeMillis() - mFirstShardEndTime));
            super.invocationEnded(mTotalElapsed);
        }
    }
}
