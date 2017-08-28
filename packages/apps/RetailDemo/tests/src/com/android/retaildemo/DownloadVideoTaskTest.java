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

package com.android.retaildemo;

import android.app.DownloadManager;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import com.android.retaildemo.DemoPlayer;
import com.android.retaildemo.DownloadVideoTask;
import com.android.retaildemo.DownloadVideoTask.ResultListener;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;

import static android.support.test.InstrumentationRegistry.getTargetContext;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class DownloadVideoTaskTest {

    private static final String TEST_URL = "https://example.com/demo.mp4";
    private static final long TEST_DOWNLOAD_ID = 1000;

    private @Mock Context mContext;
    private @Mock DownloadManager mDownloadManager;
    private @Mock ResultListener mResultListener;
    private @Mock ConnectivityManager mConnectivityManager;
    private @Mock ProgressDialog mProgressDialog;
    private @Mock HttpURLConnection mConnection;

    private String mDownloadPath;
    private File mPreloadedVideo;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        final String videoFileName = getTargetContext().getString(
                R.string.retail_demo_video_file_name);
        mDownloadPath = getTargetContext().getObbDir().getPath() + File.separator
                + videoFileName;
        clearIfFileExists(mDownloadPath);
        mPreloadedVideo = new File(Environment.getDataPreloadsDemoDirectory(), videoFileName);
        setNetworkConnected(true);
    }

    @After
    public void tearDown() throws Exception {
        clearIfFileExists(mDownloadPath);
    }

    private void clearIfFileExists(String path) {
        final File file = new File(path);
        if (file.exists()) {
            file.delete();
        }
    }

    @Test
    public void testDownloadVideo() throws Exception {
        final DownloadVideoTask task = new DownloadVideoTask(mContext,
                mDownloadPath, mPreloadedVideo, mResultListener, new TestInjector(mContext));
        when(mDownloadManager.enqueue(any(DownloadManager.Request.class)))
                .thenReturn(TEST_DOWNLOAD_ID);

        task.run();

        final ArgumentCaptor<BroadcastReceiver> downloadReceiver =
                verifyIfDownloadCompleteReceiverRegistered();

        verify(mProgressDialog, times(1)).show();

        final Cursor cursor = createCursor(DownloadManager.STATUS_SUCCESSFUL, mDownloadPath);
        when(mDownloadManager.query(any(DownloadManager.Query.class))).thenReturn(cursor);

        final Intent downloadCompleteIntent = new Intent(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
                .putExtra(DownloadManager.EXTRA_DOWNLOAD_ID, TEST_DOWNLOAD_ID);
        downloadReceiver.getValue().onReceive(mContext, downloadCompleteIntent);

        verify(mContext).unregisterReceiver(downloadReceiver.getValue());

        verify(mResultListener, times(1)).onFileDownloaded(mDownloadPath);
        verifyNoMoreInteractions(mResultListener);

        verify(mProgressDialog, times(1)).dismiss();
    }

    @Test
    public void testDownloadVideo_noNetwork() throws Exception {
        setNetworkConnected(false);
        final DownloadVideoTask task = new DownloadVideoTask(mContext,
                mDownloadPath, mPreloadedVideo, mResultListener, new TestInjector(mContext));

        task.run();

        verify(mResultListener, times(1)).onError();
        // Verify that broadcast receivers are registered for ACTION_DOWNLOAD_COMPLETE and
        // ConnectivityManager.CONNECTIVITY_ACTION.
        final ArgumentCaptor<BroadcastReceiver> broadcastReceiver =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        final ArgumentCaptor<IntentFilter> intentFilter =
                ArgumentCaptor.forClass(IntentFilter.class);
        verify(mContext, times(2)).registerReceiver(
                broadcastReceiver.capture(), intentFilter.capture());
        final BroadcastReceiver downloadReceiver = broadcastReceiver.getAllValues().get(0);
        assertEquals(intentFilter.getAllValues().get(0).getAction(0),
                DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        assertEquals(intentFilter.getAllValues().get(1).getAction(0),
                ConnectivityManager.CONNECTIVITY_ACTION);
        final BroadcastReceiver networkReceiver = broadcastReceiver.getAllValues().get(1);

        when(mDownloadManager.enqueue(any(DownloadManager.Request.class)))
                .thenReturn(TEST_DOWNLOAD_ID);
        setNetworkConnected(true);

        networkReceiver.onReceive(mContext,
                new Intent(ConnectivityManager.CONNECTIVITY_ACTION));

        final Cursor cursor = createCursor(DownloadManager.STATUS_SUCCESSFUL, mDownloadPath);
        when(mDownloadManager.query(any(DownloadManager.Query.class))).thenReturn(cursor);

        final Intent downloadCompleteIntent = new Intent(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
                .putExtra(DownloadManager.EXTRA_DOWNLOAD_ID, TEST_DOWNLOAD_ID);
        downloadReceiver.onReceive(mContext, downloadCompleteIntent);

        verify(mContext).unregisterReceiver(downloadReceiver);
        verify(mContext).unregisterReceiver(networkReceiver);

        verify(mResultListener).onFileDownloaded(mDownloadPath);
        verifyNoMoreInteractions(mResultListener);

        verify(mProgressDialog).dismiss();
    }

    @Test
    public void testDownloadVideo_downloadFailed() throws Exception {
        final DownloadVideoTask task = new DownloadVideoTask(mContext,
                mDownloadPath, mPreloadedVideo, mResultListener, new TestInjector(mContext));
        when(mDownloadManager.enqueue(any(DownloadManager.Request.class)))
                .thenReturn(TEST_DOWNLOAD_ID);

        task.run();

        final ArgumentCaptor<BroadcastReceiver> downloadReceiver =
                verifyIfDownloadCompleteReceiverRegistered();

        verify(mProgressDialog, times(1)).show();

        final Cursor cursor = createCursor(DownloadManager.STATUS_FAILED, mDownloadPath);
        when(mDownloadManager.query(any(DownloadManager.Query.class))).thenReturn(cursor);

        final Intent downloadCompleteIntent = new Intent(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
                .putExtra(DownloadManager.EXTRA_DOWNLOAD_ID, TEST_DOWNLOAD_ID);
        downloadReceiver.getValue().onReceive(mContext, downloadCompleteIntent);

        verify(mContext).unregisterReceiver(downloadReceiver.getValue());

        verify(mResultListener, times(1)).onError();
        verifyNoMoreInteractions(mResultListener);

        verify(mProgressDialog, times(1)).dismiss();
    }

    @Test
    public void testDownloadUpdatedVideo() throws Exception {
        new File(mDownloadPath).createNewFile();

        final TestInjector injector = new TestInjector(mContext);
        final DownloadVideoTask task = new DownloadVideoTask(mContext,
                mDownloadPath, mPreloadedVideo, mResultListener, injector);
        final Handler handler = injector.getHandler(task);

        when(mConnection.getResponseCode()).thenReturn(HttpURLConnection.HTTP_OK);
        handler.handleMessage(handler.obtainMessage(DownloadVideoTask.MSG_CHECK_FOR_UPDATE));

        verify(mConnection).setIfModifiedSince(anyLong());
        verify(mDownloadManager).enqueue(any(DownloadManager.Request.class));
    }

    @Test
    public void testDownloadUpdatedVideo_notModified() throws Exception {
        new File(mDownloadPath).createNewFile();

        final TestInjector injector = new TestInjector(mContext);
        final DownloadVideoTask task = new DownloadVideoTask(mContext,
                mDownloadPath, mPreloadedVideo, mResultListener, injector);
        final Handler handler = injector.getHandler(task);

        when(mConnection.getResponseCode()).thenReturn(HttpURLConnection.HTTP_NOT_MODIFIED);
        handler.handleMessage(handler.obtainMessage(DownloadVideoTask.MSG_CHECK_FOR_UPDATE));

        verify(mConnection).setIfModifiedSince(anyLong());
        verifyZeroInteractions(mDownloadManager);
    }

    private ArgumentCaptor<BroadcastReceiver> verifyIfDownloadCompleteReceiverRegistered() {
        final ArgumentCaptor<BroadcastReceiver> broadcastReceiver =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        final ArgumentCaptor<IntentFilter> intentFilter =
                ArgumentCaptor.forClass(IntentFilter.class);
        verify(mContext).registerReceiver(
                broadcastReceiver.capture(), intentFilter.capture());
        assertEquals(intentFilter.getValue().getAction(0),
                DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        return broadcastReceiver;
    }

    private Cursor createCursor(int status, String filePath) {
        final MatrixCursor cursor = new MatrixCursor(new String[] {
                DownloadManager.COLUMN_STATUS,
                DownloadManager.COLUMN_LOCAL_URI
        });
        cursor.addRow(new Object[] {
                status,
                Uri.fromFile(new File(filePath))
        });
        return cursor;
    }

    private void setNetworkConnected(boolean connected) {
        NetworkInfo networkInfo = Mockito.mock(NetworkInfo.class);
        when(networkInfo.isConnected()).thenReturn(connected);
        when(mConnectivityManager.getActiveNetworkInfo()).thenReturn(networkInfo);
    }

    private class TestInjector extends DownloadVideoTask.Injector {
        TestInjector(Context context) {
            super(context);
        }

        @Override
        DownloadManager getDownloadManager() {
            return mDownloadManager;
        }

        @Override
        String getDownloadUrl() {
            return TEST_URL;
        }

        @Override
        ConnectivityManager getConnectivityManager() {
            return mConnectivityManager;
        }

        @Override
        Handler getHandler(DownloadVideoTask task) {
            return task.new ThreadHandler(Looper.getMainLooper());
        }

        @Override
        ProgressDialog getProgressDialog() {
            return mProgressDialog;
        }

        @Override
        HttpURLConnection openConnection(String downloadUri) {
            return mConnection;
        }
    }
}