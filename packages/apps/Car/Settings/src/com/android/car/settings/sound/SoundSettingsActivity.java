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
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;

import com.android.car.settings.common.CarSettingActivity;
import com.android.car.settings.R;

/**
 * Activity hosts sound related settings.
 */
public class SoundSettingsActivity extends CarSettingActivity {
    private static final String TAG = "SoundSettingsActivity";
    private Car mCar;

    private VolumeControllerPresenter mMediaVolumeControllerPresenter;
    private VolumeControllerPresenter mRingVolumeControllerPresenter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.volume_list);

        View mediaVolumeControllerView = findViewById(
                R.id.media_volume);
        View ringVolumeControllerView = findViewById(
                R.id.ring_volume);
        mMediaVolumeControllerPresenter = new VolumeControllerPresenter(
                this /* context*/,
                mediaVolumeControllerView,
                AudioManager.STREAM_MUSIC,
                null /* Uri sampleUri */,
                R.string.media_volume_title,
                com.android.internal.R.drawable.ic_audio_media);
        mRingVolumeControllerPresenter = new VolumeControllerPresenter(
                this /* context*/,
                ringVolumeControllerView,
                AudioManager.STREAM_RING,
                null /* Uri sampleUri */,
                R.string.ring_volume_title,
                com.android.internal.R.drawable.ic_audio_ring_notif);
        mCar = Car.createCar(this /* context */, mServiceConnection);
    }

    ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mMediaVolumeControllerPresenter.onServiceConnected(mCar);
            mRingVolumeControllerPresenter.onServiceConnected(mCar);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mMediaVolumeControllerPresenter.onServiceDisconnected();
            mRingVolumeControllerPresenter.onServiceDisconnected();
        }
    };

    @Override
    public void onStart() {
        super.onStart();
        mCar.connect();
    }

    @Override
    public void onStop() {
        super.onStop();
        mMediaVolumeControllerPresenter.stop();
        mRingVolumeControllerPresenter.stop();
        mCar.disconnect();
    }
}
