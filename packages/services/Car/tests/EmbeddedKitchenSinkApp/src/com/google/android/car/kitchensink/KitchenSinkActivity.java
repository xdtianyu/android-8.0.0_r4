/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.google.android.car.kitchensink;

import android.car.hardware.hvac.CarHvacManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.car.Car;
import android.support.car.CarAppFocusManager;
import android.support.car.CarConnectionCallback;
import android.support.car.CarNotConnectedException;
import android.support.car.hardware.CarSensorManager;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import com.android.car.app.CarDrawerActivity;
import com.android.car.app.CarDrawerAdapter;
import com.android.car.app.DrawerItemViewHolder;
import com.google.android.car.kitchensink.assistant.CarAssistantFragment;
import com.google.android.car.kitchensink.audio.AudioTestFragment;
import com.google.android.car.kitchensink.bluetooth.BluetoothHeadsetFragment;
import com.google.android.car.kitchensink.bluetooth.MapMceTestFragment;
import com.google.android.car.kitchensink.cluster.InstrumentClusterFragment;
import com.google.android.car.kitchensink.cube.CubesTestFragment;
import com.google.android.car.kitchensink.hvac.HvacTestFragment;
import com.google.android.car.kitchensink.input.InputTestFragment;
import com.google.android.car.kitchensink.job.JobSchedulerFragment;
import com.google.android.car.kitchensink.orientation.OrientationTestFragment;
import com.google.android.car.kitchensink.radio.RadioTestFragment;
import com.google.android.car.kitchensink.sensor.SensorsTestFragment;
import com.google.android.car.kitchensink.setting.CarServiceSettingsActivity;
import com.google.android.car.kitchensink.touch.TouchTestFragment;
import com.google.android.car.kitchensink.volume.VolumeTestFragment;

public class KitchenSinkActivity extends CarDrawerActivity {
    private static final String TAG = "KitchenSinkActivity";

    private static final String MENU_AUDIO = "audio";
    private static final String MENU_ASSISTANT = "assistant";
    private static final String MENU_HVAC = "hvac";
    private static final String MENU_QUIT = "quit";
    private static final String MENU_JOB = "job_scheduler";
    private static final String MENU_CLUSTER = "inst cluster";
    private static final String MENU_INPUT_TEST = "input test";
    private static final String MENU_RADIO = "radio";
    private static final String MENU_SENSORS = "sensors";
    private static final String MENU_VOLUME_TEST = "volume test";
    private static final String MENU_TOUCH_TEST = "touch test";
    private static final String MENU_CUBES_TEST = "cubes test";
    private static final String MENU_CAR_SETTINGS = "car service settings";
    private static final String MENU_ORIENTATION = "orientation test";
    private static final String MENU_BLUETOOTH_HEADSET = "bluetooth headset";
    private static final String MENU_MAP_MESSAGING = "bluetooth messaging test";

    private Car mCarApi;
    private CarHvacManager mHvacManager;
    private CarSensorManager mCarSensorManager;
    private CarAppFocusManager mCarAppFocusManager;

    private AudioTestFragment mAudioTestFragment;
    private RadioTestFragment mRadioTestFragment;
    private SensorsTestFragment mSensorsTestFragment;
    private HvacTestFragment mHvacTestFragment;
    private JobSchedulerFragment mJobFragment;
    private InstrumentClusterFragment mInstrumentClusterFragment;
    private InputTestFragment mInputTestFragment;
    private VolumeTestFragment mVolumeTestFragment;
    private TouchTestFragment mTouchTestFragment;
    private CubesTestFragment mCubesTestFragment;
    private OrientationTestFragment mOrientationFragment;
    private MapMceTestFragment mMapMceTestFragment;
    private BluetoothHeadsetFragment mBluetoothHeadsetFragement;
    private CarAssistantFragment mAssistantFragment;

    private final CarSensorManager.OnSensorChangedListener mListener = (manager, event) -> {
        switch (event.sensorType) {
            case CarSensorManager.SENSOR_TYPE_DRIVING_STATUS:
                Log.d(TAG, "driving status:" + event.intValues[0]);
                break;
        }
    };

    @Override
    protected CarDrawerAdapter getRootAdapter() {
        return new DrawerAdapter();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setMainContent(R.layout.kitchen_content);
        // Connection to Car Service does not work for non-automotive yet.
        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            mCarApi = Car.createCar(this, mCarConnectionCallback);
            mCarApi.connect();
        }
        Log.i(TAG, "onCreate");
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.i(TAG, "onStart");
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Log.i(TAG, "onRestart");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "onResume");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "onPause");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.i(TAG, "onStop");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mCarSensorManager != null) {
            mCarSensorManager.removeListener(mListener);
        }
        if (mCarApi != null) {
            mCarApi.disconnect();
        }
        Log.i(TAG, "onDestroy");
    }

    private void showFragment(Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.kitchen_content, fragment)
                .commit();
    }

    private final CarConnectionCallback mCarConnectionCallback =
            new CarConnectionCallback() {
        @Override
        public void onConnected(Car car) {
            Log.d(TAG, "Connected to Car Service");
            try {
                mHvacManager = (CarHvacManager) mCarApi.getCarManager(android.car.Car.HVAC_SERVICE);
                mCarSensorManager = (CarSensorManager) mCarApi.getCarManager(Car.SENSOR_SERVICE);
                mCarSensorManager.addListener(mListener,
                        CarSensorManager.SENSOR_TYPE_DRIVING_STATUS,
                        CarSensorManager.SENSOR_RATE_NORMAL);
                mCarAppFocusManager =
                        (CarAppFocusManager) mCarApi.getCarManager(Car.APP_FOCUS_SERVICE);
            } catch (CarNotConnectedException e) {
                Log.e(TAG, "Car is not connected!", e);
            }
        }

        @Override
        public void onDisconnected(Car car) {
            Log.d(TAG, "Disconnect from Car Service");
        }
    };

    public Car getCar() {
        return mCarApi;
    }

    private final class DrawerAdapter extends CarDrawerAdapter {

        private final String mAllMenus[] = {
                MENU_AUDIO, MENU_ASSISTANT, MENU_RADIO, MENU_HVAC, MENU_JOB,
                MENU_CLUSTER, MENU_INPUT_TEST, MENU_SENSORS, MENU_VOLUME_TEST,
                MENU_TOUCH_TEST, MENU_CUBES_TEST, MENU_CAR_SETTINGS, MENU_ORIENTATION,
                MENU_BLUETOOTH_HEADSET, MENU_MAP_MESSAGING, MENU_QUIT
        };

        public DrawerAdapter() {
            super(KitchenSinkActivity.this, true /* showDisabledOnListOnEmpty */,
                    true /* smallLayout */);
            setTitle(getString(R.string.app_title));
        }

        @Override
        protected int getActualItemCount() {
            return mAllMenus.length;
        }

        @Override
        protected void populateViewHolder(DrawerItemViewHolder holder, int position) {
            holder.getTitle().setText(mAllMenus[position]);
        }

        @Override
        public void onItemClick(int position) {

            switch (mAllMenus[position]) {
                case MENU_AUDIO:
                    if (mAudioTestFragment == null) {
                        mAudioTestFragment = new AudioTestFragment();
                    }
                    showFragment(mAudioTestFragment);
                    break;
                case MENU_ASSISTANT:
                    if (mAssistantFragment == null) {
                        mAssistantFragment = new CarAssistantFragment();
                    }
                    showFragment(mAssistantFragment);
                    break;
                case MENU_RADIO:
                    if (mRadioTestFragment == null) {
                        mRadioTestFragment = new RadioTestFragment();
                    }
                    showFragment(mRadioTestFragment);
                    break;
                case MENU_SENSORS:
                    if (mSensorsTestFragment == null) {
                        mSensorsTestFragment = new SensorsTestFragment();
                    }
                    showFragment(mSensorsTestFragment);
                    break;
                case MENU_HVAC:
                    if (mHvacManager != null) {
                        if (mHvacTestFragment == null) {
                            mHvacTestFragment = new HvacTestFragment();
                            mHvacTestFragment.setHvacManager(mHvacManager);
                        }
                        // Don't allow HVAC fragment to start if we don't have a manager.
                        showFragment(mHvacTestFragment);
                    }
                    break;
                case MENU_JOB:
                    if (mJobFragment == null) {
                        mJobFragment = new JobSchedulerFragment();
                    }
                    showFragment(mJobFragment);
                    break;
                case MENU_CLUSTER:
                    if (mInstrumentClusterFragment == null) {
                        mInstrumentClusterFragment = new InstrumentClusterFragment();
                    }
                    showFragment(mInstrumentClusterFragment);
                    break;
                case MENU_INPUT_TEST:
                    if (mInputTestFragment == null) {
                        mInputTestFragment = new InputTestFragment();
                    }
                    showFragment(mInputTestFragment);
                    break;
                case MENU_VOLUME_TEST:
                    if (mVolumeTestFragment == null) {
                        mVolumeTestFragment = new VolumeTestFragment();
                    }
                    showFragment(mVolumeTestFragment);
                    break;
                case MENU_TOUCH_TEST:
                    if (mTouchTestFragment == null) {
                        mTouchTestFragment = new TouchTestFragment();
                    }
                    showFragment(mTouchTestFragment);
                    break;
                case MENU_CUBES_TEST:
                    if (mCubesTestFragment == null) {
                        mCubesTestFragment = new CubesTestFragment();
                    }
                    showFragment(mCubesTestFragment);
                    break;
                case MENU_CAR_SETTINGS:
                    Intent intent = new Intent(KitchenSinkActivity.this,
                            CarServiceSettingsActivity.class);
                    startActivity(intent);
                    break;
                case MENU_ORIENTATION:
                    if (mOrientationFragment == null) {
                        mOrientationFragment = new OrientationTestFragment();
                    }
                    showFragment(mOrientationFragment);
                    break;
                case MENU_BLUETOOTH_HEADSET:
                    if (mBluetoothHeadsetFragement == null) {
                        mBluetoothHeadsetFragement = new BluetoothHeadsetFragment();
                    }
                    showFragment(mBluetoothHeadsetFragement);
                    break;
                case MENU_MAP_MESSAGING:
                    if (mMapMceTestFragment == null) {
                        mMapMceTestFragment = new MapMceTestFragment();
                    }
                    showFragment(mMapMceTestFragment);
                    break;
                case MENU_QUIT:
                    finish();
                    break;
                default:
                    Log.wtf(TAG, "Unknown menu item: " + mAllMenus[position]);
            }
            closeDrawer();
        }
    }
}
