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
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.VisibleForTesting;
import android.util.Log;
import android.view.ContextThemeWrapper;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Downloads the video from the specified url. If the video is previously downloaded, then uses
 * that but checks if there is a more recent version of the video available.
 */
class DownloadVideoTask {
    private static final String TAG = "DownloadVideoTask";
    private static final boolean DEBUG = false;

    static final int MSG_CHECK_FOR_UPDATE = 1;
    static final int MSG_DOWNLOAD_COMPLETE = 2;
    static final int MSG_CLEANUP_DOWNLOAD_DIR = 3;

    private static final int CLEANUP_DELAY_MILLIS = 2 * 1000; // 2 seconds

    private final Injector mInjector;
    private final Context mContext;
    private final DownloadManager mDlm;
    private final File mDownloadFile;
    private final ResultListener mListener;

    private Handler mHandler;

    private ProgressDialog mProgressDialog;
    private DownloadResultReceiver mDownloadReceiver;
    private NetworkChangeReceiver mNetworkChangeReceiver;
    private String mDownloadUrl;
    private long mVideoDownloadId;
    private long mVideoUpdateDownloadId;
    private String mDownloadedPath;
    private boolean mVideoAlreadySet;
    private File mPreloadVideoFile;

    public DownloadVideoTask(Context context, String downloadPath, File preloadVideoFile,
            ResultListener listener) {
        this(context, downloadPath, preloadVideoFile, listener, new Injector(context));
    }

    @VisibleForTesting
    DownloadVideoTask(Context context, String downloadPath, File preloadVideoFile,
            ResultListener listener, Injector injector) {
        mInjector = injector;
        mContext = context;
        mDownloadFile = new File(downloadPath);
        mListener = listener;
        mPreloadVideoFile = preloadVideoFile;
        mDlm = injector.getDownloadManager();
        mDownloadUrl = injector.getDownloadUrl();
    }

    public void run() {
        mDownloadReceiver = new DownloadResultReceiver();
        mContext.registerReceiver(mDownloadReceiver,
                new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));

        mHandler = mInjector.getHandler(this);

        mVideoAlreadySet =
                mDownloadFile.exists() || mPreloadVideoFile.exists();
        // If file already exists, no need to download it again.
        if (mVideoAlreadySet) {
            if (DEBUG) Log.d(TAG, "Video already exists at either " + mDownloadFile.getPath()
                    + " or " + mPreloadVideoFile + ", checking for an update... ");
            mHandler.sendMessage(mHandler.obtainMessage(MSG_CHECK_FOR_UPDATE));
        } else {
            if (!isConnectedToNetwork()) {
                mListener.onError();
                mNetworkChangeReceiver = new NetworkChangeReceiver();
                mContext.registerReceiver(mNetworkChangeReceiver,
                        new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
                return;
            }
            startDownload();
        }
    }

    private void startDownload() {
        final DownloadManager.Request request = createDownloadRequest();
        mVideoDownloadId = mDlm.enqueue(request);
        if (DEBUG) Log.d(TAG, "Started downloading the video at " + mDownloadUrl
                + " to " + mDownloadFile.getPath());
        showProgressDialog();
    }

    private DownloadManager.Request createDownloadRequest() {
        final DownloadManager.Request request = new DownloadManager.Request(
                Uri.parse(mDownloadUrl));
        request.setDestinationUri(Uri.fromFile(mDownloadFile));
        return request;
    }

    private class DownloadResultReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) {
                return;
            }

            final long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0);
            if (id == mVideoDownloadId) {
                final int status = checkDownloadsAndSetVideo(id);
                if (status == DownloadManager.STATUS_SUCCESSFUL ||
                        status == DownloadManager.STATUS_FAILED) {
                    if (mProgressDialog != null) {
                        mProgressDialog.dismiss();
                    }
                    if (mDownloadReceiver != null) {
                        mContext.unregisterReceiver(mDownloadReceiver);
                        mDownloadReceiver = null;
                    }
                    if (status == DownloadManager.STATUS_FAILED) {
                        mListener.onError();
                    }
                }
            } else if (id == mVideoUpdateDownloadId) {
                mHandler.sendMessage(mHandler.obtainMessage(MSG_DOWNLOAD_COMPLETE));
            }
        }
    };

    final class ThreadHandler extends Handler {
        public ThreadHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_CHECK_FOR_UPDATE:
                    if (!isConnectedToNetwork()) {
                        mNetworkChangeReceiver = new NetworkChangeReceiver();
                        mContext.registerReceiver(mNetworkChangeReceiver,
                                new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
                        return;
                    }
                    HttpURLConnection conn = null;
                    try {
                        conn = mInjector.openConnection(mDownloadUrl);
                        final long lastModified = mDownloadFile.lastModified();
                        conn.setIfModifiedSince(lastModified);
                        conn.connect();
                        if (conn.getResponseCode() == HttpURLConnection.HTTP_NOT_MODIFIED) {
                            return;
                        }
                        final DownloadManager.Request request = createDownloadRequest();
                        mVideoUpdateDownloadId = mDlm.enqueue(request);
                        if (DEBUG) Log.d(TAG, "Started downloading the updated video at "
                                + mDownloadUrl);
                    } catch (IOException e) {
                        Log.e(TAG, "Error while checking for an updated video", e);
                    } finally {
                        if (conn != null) {
                            conn.disconnect();
                        }
                    }
                    break;
                case MSG_DOWNLOAD_COMPLETE:
                    checkDownloadsAndSetVideo(mVideoUpdateDownloadId);
                    break;
                case MSG_CLEANUP_DOWNLOAD_DIR:
                    // If the video was downloaded to the same location as we needed, then
                    // nothing else to do.
                    if (mDownloadFile.getPath().equals(mDownloadedPath)) {
                        return;
                    }
                    if (mDownloadFile.exists()) {
                        mDownloadFile.delete();
                    }
                    if (new File(mDownloadedPath).renameTo(mDownloadFile)) {
                        mListener.onFileDownloaded(mDownloadFile.getPath());
                        final String downloadFileName = getFileBaseName(mDownloadFile.getName());
                        // Delete other files in the directory
                        for (File file : mDownloadFile.getParentFile().listFiles()) {
                            if (getFileBaseName(file.getName()).startsWith(downloadFileName)
                                    && !file.getPath().equals(mDownloadFile.getPath())) {
                                file.delete();
                            }
                        }
                    }
                    break;
            }
        }
    }

    private int checkDownloadsAndSetVideo(long downloadId) {
        final DownloadManager.Query query =
                new DownloadManager.Query().setFilterById(downloadId);
        Cursor cursor = mDlm.query(query);
        try {
            if (cursor != null & cursor.moveToFirst()) {
                final int columnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                final int status = cursor.getInt(columnIndex);
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    final String fileUri = cursor.getString(
                            cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
                    mDownloadedPath = Uri.parse(fileUri).getPath();
                    if (DEBUG) Log.d(TAG, "Video successfully downloaded at " + mDownloadedPath);
                    mListener.onFileDownloaded(mDownloadedPath);
                    mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_CLEANUP_DOWNLOAD_DIR),
                            CLEANUP_DELAY_MILLIS);
                }
                return status;
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            if (mNetworkChangeReceiver != null) {
                mContext.unregisterReceiver(mNetworkChangeReceiver);
                mNetworkChangeReceiver = null;
            }
        }
        return -1;
    }

    private class NetworkChangeReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())
                    && isConnectedToNetwork()) {
                if (mVideoAlreadySet) {
                    mHandler.sendMessage(mHandler.obtainMessage(MSG_CHECK_FOR_UPDATE));
                } else {
                    startDownload();
                }
            }
        }
    };

    private void showProgressDialog() {
        mProgressDialog = mInjector.getProgressDialog();
        mProgressDialog.show();
    }

    private boolean isConnectedToNetwork() {
        ConnectivityManager cm = mInjector.getConnectivityManager();
        NetworkInfo info = cm.getActiveNetworkInfo();
        return info != null && info.isConnected();
    }

    private String getFileBaseName(String fileName) {
        final int pos = fileName.lastIndexOf(".");
        return pos > 0 ? fileName.substring(0, pos) : fileName;
    }

    interface ResultListener {
        void onFileDownloaded(String downloadedFilePath);
        void onError();
    }

    /**
     * Unit test will subclass this to inject mocks.
     */
    @VisibleForTesting
    static class Injector {
        private final Context mContext;

        Injector(Context context) {
            mContext = context;
        }

        DownloadManager getDownloadManager() {
            return (DownloadManager) mContext.getSystemService(Context.DOWNLOAD_SERVICE);
        }

        String getDownloadUrl() {
            return mContext.getString(R.string.retail_demo_video_download_url);
        }

        ConnectivityManager getConnectivityManager() {
            return (ConnectivityManager) mContext.getSystemService(
                    Context.CONNECTIVITY_SERVICE);
        }

        Handler getHandler(DownloadVideoTask task) {
            // Initialize handler
            HandlerThread thread = new HandlerThread(TAG);
            thread.start();
            return task.new ThreadHandler(thread.getLooper());
        }

        ProgressDialog getProgressDialog() {
            final ProgressDialog dialog = new ProgressDialog(
                    new ContextThemeWrapper(mContext, android.R.style.Theme_Material_Light_Dialog));
            dialog.setMessage(mContext.getString(R.string.downloading_video_msg));
            dialog.setIndeterminate(false);
            dialog.setCancelable(false);
            dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            return dialog;
        }

        HttpURLConnection openConnection(String downloadUri) throws IOException {
            return (HttpURLConnection) new URL(downloadUri).openConnection();
        }
    }
}