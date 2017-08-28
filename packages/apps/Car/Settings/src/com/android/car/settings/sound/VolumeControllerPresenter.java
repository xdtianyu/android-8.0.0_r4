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
 * limitations under the License
 */

package com.android.car.settings.sound;

import android.car.Car;
import android.car.CarNotConnectedException;
import android.car.media.CarAudioManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.media.AudioManager;
import android.media.IVolumeController;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

import com.android.car.settings.R;

/**
 * Contains logic about volume controller UI.
 */
public class VolumeControllerPresenter implements OnSeekBarChangeListener {

    private static final String TAG = "SeekBarVolumizer";
    private static final int AUDIO_FEEDBACK_DELAY_MS = 1500;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final SeekBar mSeekBar;
    private final int mStreamType;
    private final Ringtone mRingtone;
    private final VolumnCallback mVolumeCallback = new VolumnCallback();

    private CarAudioManager mCarAudioManager;

    public void onServiceConnected(Car car) {
        try {
            mCarAudioManager = (CarAudioManager) car.getCarManager(Car.AUDIO_SERVICE);
            mCarAudioManager.setVolumeController(mVolumeCallback);
            mSeekBar.setMax(mCarAudioManager.getStreamMaxVolume(mStreamType));
            mSeekBar.setProgress(mCarAudioManager.getStreamVolume(mStreamType));
            mSeekBar.setOnSeekBarChangeListener(VolumeControllerPresenter.this);
        } catch (CarNotConnectedException e) {
            Log.e(TAG, "Car is not connected!", e);
        }
    }

    public void onServiceDisconnected() {
        mSeekBar.setOnSeekBarChangeListener(null);
        mCarAudioManager = null;
    }

    public VolumeControllerPresenter(Context context, View volumeControllerView,
            int streamType, Uri sampleUri, int titleStringResId, int iconResId) {
        mSeekBar = (SeekBar) volumeControllerView.findViewById(R.id.seekbar);
        mStreamType = streamType;
        Uri ringtoneUri;

        if (sampleUri == null) {
            switch (mStreamType) {
                case AudioManager.STREAM_RING:
                    ringtoneUri = Settings.System.DEFAULT_RINGTONE_URI;
                    break;
                case AudioManager.STREAM_NOTIFICATION:
                    ringtoneUri = Settings.System.DEFAULT_NOTIFICATION_URI;
                    break;
                default:
                    ringtoneUri = Settings.System.DEFAULT_ALARM_ALERT_URI;
            }
        } else {
            ringtoneUri = sampleUri;
        }
        mRingtone = RingtoneManager.getRingtone(context, ringtoneUri);
        if (mRingtone != null) {
            mRingtone.setStreamType(mStreamType);
        }
        ((ImageView) volumeControllerView.findViewById(R.id.icon)).setImageResource(iconResId);
        ((TextView) volumeControllerView.findViewById(R.id.stream_name)).setText(titleStringResId);
    }

    public void stop() {
        mHandler.removeCallbacksAndMessages(null);
        mRingtone.stop();
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        try {
            if (mCarAudioManager == null) {
                Log.w(TAG, "CarAudiomanager not available, Car is not connected!");
                return;
            }
            mCarAudioManager.setStreamVolume(mStreamType, progress, AudioManager.FLAG_PLAY_SOUND);
            playAudioFeedback();
        } catch (CarNotConnectedException e) {
            Log.e(TAG, "Car is not connected!", e);
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        playAudioFeedback();
    }

    private void playAudioFeedback() {
        mHandler.removeCallbacksAndMessages(null);
        mRingtone.play();
        mHandler.postDelayed(() -> {
            if (mRingtone.isPlaying()) {
                mRingtone.stop();
            }
        }, AUDIO_FEEDBACK_DELAY_MS);
    }

    /**
     * The interface has a terrible name, it is actually a callback, so here name it accordingly.
     */
    private final class VolumnCallback extends IVolumeController.Stub {

        private final String TAG = VolumeControllerPresenter.TAG + ".cb";

        @Override
        public void displaySafeVolumeWarning(int flags) throws RemoteException {
        }

        @Override
        public void volumeChanged(int streamType, int flags) throws RemoteException {
            if (streamType != mStreamType) {
                return;
            }
            try {
                if (mCarAudioManager == null) {
                    Log.w(TAG, "CarAudiomanager not available, Car is not connected!");
                    return;
                }
                int volume = mCarAudioManager.getStreamVolume(mStreamType);
                if (mSeekBar.getProgress() == volume) {
                    return;
                }
                mSeekBar.setProgress(volume);
            } catch (CarNotConnectedException e) {
                Log.e(TAG, "Car is not connected!", e);
            }
        }

        // this is not mute of this stream
        @Override
        public void masterMuteChanged(int flags) throws RemoteException {
        }

        @Override
        public void setLayoutDirection(int layoutDirection) throws RemoteException {
        }

        @Override
        public void dismiss() throws RemoteException {
        }

        @Override
        public void setA11yMode(int mode) {
        }
    }
}
