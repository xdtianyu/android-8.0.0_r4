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

package com.android.car;

import android.annotation.Nullable;
import android.bluetooth.BluetoothDevice;
import android.util.Log;

import java.util.List;
import java.util.ArrayList;

import android.bluetooth.BluetoothProfile;

/**
 * BluetoothDevicesInfo contains all the information pertinent to connection on a Bluetooth Profile.
 * It holds
 * 1. a list of devices {@link #mDeviceInfoList} that has previously paired and connected on this
 * profile.
 * 2. a Connection Info object {@link #mConnectionInfo} that has following book keeping information:
 * a) profile
 * b) Current Connection status
 * c) If there are any devices available for connection
 * d) Index of the Device list that a connection is being tried upon currently.
 * e) Number of devices that have been previously paired and connected on this profile.
 * f) How many retry attempts have been made
 *
 * This is used by the {@link BluetoothDeviceConnectionPolicy} to find the device to attempt
 * a connection on for a profile.  The policy also updates this object with the connection
 * results.
 */
public class BluetoothDevicesInfo {

    private static final String TAG = "CarBluetoothDevicesInfo";
    private static final boolean DBG = false;
    private final int DEVICE_NOT_FOUND = -1;
    // The device list and the connection state information together have all the information
    // that is required to know which device(s) to connect to, when we need to connect/
    private List<DeviceInfo> mDeviceInfoList;
    private ConnectionInfo mConnectionInfo;

    /**
     * This class holds on to information regarding this bluetooth profile's connection state.
     */
    private class ConnectionInfo {
        // which bluetooth profile this Device Info is for
        private int mProfile;
        // are there any devices available to connect. It is false either if
        // 1. no device has been paired to connect on this profile
        // 2. all paired devices have been tried to connect to, but unsuccessful (not in range etc)
        private boolean mDeviceAvailableToConnect;
        // index of device in the mDeviceInfoList that the next connection attempt should be made
        private int mDeviceIndex;
        // Connection Retry counter
        private int mRetryAttempt;
        // Current number of active connections on this profile
        private int mNumActiveConnections;
        // number of concurrent active connections supported.
        private int mNumConnectionsSupported;

        public ConnectionInfo(int profile) {
            // Default the number of concurrent active connections supported to 1.
            this(profile, 1);
        }

        public ConnectionInfo(int profile, int numConnectionsSupported) {
            mProfile = profile;
            mNumConnectionsSupported = numConnectionsSupported;
            initConnectionInfo();
        }

        private void initConnectionInfo() {
            mDeviceAvailableToConnect = true;
            mDeviceIndex = 0;
            mRetryAttempt = 0;
            mNumActiveConnections = 0;
        }
    }

    /**
     * This class holds information about the list of devices that can connect (have connected in
     * the past) and their current connection state.
     */
    public class DeviceInfo {

        private BluetoothDevice mBluetoothDevice;
        private int mConnectionState;

        public DeviceInfo(BluetoothDevice device, int state) {
            mBluetoothDevice = device;
            mConnectionState = state;
        }

        public void setConnectionState(int state) {
            mConnectionState = state;
        }

        public int getConnectionState() {
            return mConnectionState;
        }

        public BluetoothDevice getBluetoothDevice() {
            return mBluetoothDevice;
        }
    }

    public BluetoothDevicesInfo(int profile) {
        mDeviceInfoList = new ArrayList<>();
        mConnectionInfo = new ConnectionInfo(profile);
    }

    public BluetoothDevicesInfo(int profile, int numConnectionsSupported) {
        mDeviceInfoList = new ArrayList<>();
        mConnectionInfo = new ConnectionInfo(profile, numConnectionsSupported);
    }

    /**
     * Get the position of the given device in the list of connectable devices for this profile.
     *
     * @param device - {@link BluetoothDevice}
     * @return postion in the {@link #mDeviceInfoList}, DEVICE_NOT_FOUND if the device is not in the
     * list.
     */
    private int getPositionInListLocked(BluetoothDevice device) {
        int index = DEVICE_NOT_FOUND;
        if (mDeviceInfoList != null) {
            int i = 0;
            for (DeviceInfo devInfo : mDeviceInfoList) {
                if (devInfo.mBluetoothDevice.getAddress().equals(device.getAddress())) {
                    index = i;
                    break;
                }
                i++;
            }
        }
        return index;
    }

    /**
     * Check if the given device is in the {@link #mDeviceInfoList}
     *
     * @param device - {@link BluetoothDevice} to look for
     * @return true if found, false if not found
     */
    private boolean checkDeviceInListLocked(BluetoothDevice device) {
        boolean isPresent = false;
        if (device == null) {
            return isPresent;
        }
        for (DeviceInfo devInfo : mDeviceInfoList) {
            if (devInfo.mBluetoothDevice.getAddress().equals(device.getAddress())) {
                isPresent = true;
                break;
            }
        }
        return isPresent;
    }

    private DeviceInfo findDeviceInfoInListLocked(@Nullable BluetoothDevice device) {
        if (device == null) {
            return null;
        }
        for (DeviceInfo devInfo : mDeviceInfoList) {
            if (devInfo.mBluetoothDevice.getAddress().equals(device.getAddress())) {
                return devInfo;
            }
        }
        return null;
    }
    /**
     * Get the current list of connectable devices for this profile.
     *
     * @return Device list for this profile.
     */
    public List<BluetoothDevice> getDeviceList() {
        List<BluetoothDevice> bluetoothDeviceList = new ArrayList<>();
        for (DeviceInfo deviceInfo : mDeviceInfoList) {
            bluetoothDeviceList.add(deviceInfo.mBluetoothDevice);
        }
        return bluetoothDeviceList;
    }

    public List<DeviceInfo> getDeviceInfoList() {
        return mDeviceInfoList;
    }

    public void setNumberOfConnectionsSupported(int num) {
        mConnectionInfo.mNumConnectionsSupported = num;
    }

    public int getNumberOfConnectionsSupported() {
        return mConnectionInfo.mNumConnectionsSupported;
    }

    /**
     * Add a device to the device list.  Used during pairing.
     *
     * @param dev - device to add for further connection attempts on this profile.
     */
    public void addDeviceLocked(BluetoothDevice dev) {
        // Check if this device is already in the device list
        if (checkDeviceInListLocked(dev)) {
            if (DBG) {
                Log.d(TAG, "Device " + dev + " already in list.  Not adding");
            }
            return;
        }
        // Add new device and set the connection state to DISCONNECTED.
        if (mDeviceInfoList != null) {
            DeviceInfo deviceInfo = new DeviceInfo(dev, BluetoothProfile.STATE_DISCONNECTED);
            mDeviceInfoList.add(deviceInfo);
        } else {
            if (DBG) {
                Log.d(TAG, "Device List is null");
            }
        }
    }

    /**
     * Set the connection state for this device to the given connection state.
     *
     * @param device - Bluetooth device to update the state for
     * @param state  - the Connection state to set.
     */
    public void setConnectionStateLocked(BluetoothDevice device, int state) {
        if (device == null) {
            Log.e(TAG, "setConnectionStateLocked() device null");
            return;
        }
        for (DeviceInfo devInfo : mDeviceInfoList) {
            BluetoothDevice dev = devInfo.mBluetoothDevice;
            if (dev == null) {
                continue;
            }
            if (dev.getAddress().equals(device.getAddress())) {
                if (DBG) {
                    Log.d(TAG, "Setting " + dev + " state to " + state);
                }
                devInfo.setConnectionState(state);
                break;
            }
        }
    }

    /**
     * Returns the current connection state for the given device
     *
     * @param device - device to get the bluetooth connection state for
     * @return - Connection State.  If passed device is null, returns DEVICE_NOT_FOUND.
     */
    public int getConnectionStateLocked(BluetoothDevice device) {
        int state = DEVICE_NOT_FOUND;
        if (device == null) {
            Log.e(TAG, "getConnectionStateLocked() device null");
            return state;
        }

        for (DeviceInfo devInfo : mDeviceInfoList) {
            BluetoothDevice dev = devInfo.mBluetoothDevice;
            if (dev == null) {
                continue;
            }
            if (dev.getAddress().equals(device.getAddress())) {
                state = devInfo.getConnectionState();
                break;
            }
        }
        return state;
    }

    /**
     * Remove a device from the list.  Used when a device is unpaired
     *
     * @param dev - device to remove from the list.
     */
    public void removeDeviceLocked(BluetoothDevice dev) {
        if (mDeviceInfoList != null) {
            DeviceInfo devInfo = findDeviceInfoInListLocked(dev);
            if (devInfo != null) {
                mDeviceInfoList.remove(devInfo);
                // If the device was connected when it was unpaired, we wouldn't have received the
                // Profile disconnected intents.  Hence check if the device was connected and if it
                // was, then decrement the number of active connections.
                if (devInfo.getConnectionState() == BluetoothProfile.STATE_CONNECTED) {
                    mConnectionInfo.mNumActiveConnections--;
                }
            }
        } else {
            if (DBG) {
                Log.d(TAG, "Device List is null");
            }
        }
        Log.d(TAG, "Device List size: " + mDeviceInfoList.size());
    }

    public void clearDeviceListLocked() {
        if (mDeviceInfoList != null) {
            mDeviceInfoList.clear();
        }
    }

    /**
     * Returns the next device to attempt a connection on for this profile.
     *
     * @return {@link BluetoothDevice} that is next in the Queue. null if the Queue has been
     * exhausted
     * (no known device nearby)
     */
    public BluetoothDevice getNextDeviceInQueueLocked() {
        BluetoothDevice device = null;
        int numberOfPairedDevices = getNumberOfPairedDevicesLocked();
        if (mConnectionInfo.mDeviceIndex >= numberOfPairedDevices) {
            if (DBG) {
                Log.d(TAG,
                        "No device available for profile "
                                + mConnectionInfo.mProfile + " "
                                + mConnectionInfo.mDeviceIndex + "/"
                                + numberOfPairedDevices);
            }
            // mDeviceIndex is the index of the device in the mDeviceInfoList, that the next
            // connection attempt would be made on.  It is moved ahead on
            // updateConnectionStatusLocked() so it always holds the index of the next device to
            // connect to.  But here, when we get the next device to connect to, if we see that
            // the index is greater than the number of devices in the list, then we move the index
            // back to the first device in the list and don't return anything.
            // The reason why this is reset is to imply that connection attempts on this profile has
            // been exhausted and if you want to retry connecting on this profile, we will start
            // from the first device.
            // The reason to reset here rather than in updateConnectionStatusLocked() is to make
            // sure we have the latest view of the numberOfPairedDevices before we say we have
            // exhausted the list.
            mConnectionInfo.mDeviceIndex = 0;
            return null;
        }
        device = mDeviceInfoList.get(mConnectionInfo.mDeviceIndex).mBluetoothDevice;
        if (DBG) {
            Log.d(TAG, "Getting device " + mConnectionInfo.mDeviceIndex + " from list: "
                    + device);
        }
        return device;
    }

    /**
     * Update the connection Status for connection attempts made on this profile.
     * If the attempt was successful, mark it and keep track of the device that was connected.
     * If unsuccessful, check if we can retry on the same device. If no more retry attempts,
     * move to the next device in the Queue.
     *
     * @param device  - {@link BluetoothDevice} that connected.
     * @param success - connection result
     * @param retry   - If Retries are available for the same device.
     */
    public void updateConnectionStatusLocked(BluetoothDevice device, boolean success,
            boolean retry) {
        if (device == null) {
            Log.w(TAG, "Updating Status with null BluetoothDevice");
            return;
        }
        if (success) {
            if (DBG) {
                Log.d(TAG, mConnectionInfo.mProfile + " connected to " + device);
            }
            // b/34722344 - TODO
            // Get the position of this device in the device list maintained for this profile.
            int positionInQ = getPositionInListLocked(device);
            if (DBG) {
                Log.d(TAG, "Position of " + device + " in Q: " + positionInQ);
            }
            // If the device that connected is not in the list, it could be because it is being
            // paired and getting added to the device list for this profile for the first time.
            if (positionInQ == DEVICE_NOT_FOUND) {
                Log.d(TAG, "Connected device not in Q: " + device);
                addDeviceLocked(device);
                positionInQ = mDeviceInfoList.size() - 1;
            } else if (positionInQ != mConnectionInfo.mDeviceIndex) {
                // This will happen if auto-connect request a connect on a device from its list,
                // but the device that connected was different.  Maybe there was another requestor
                // and the Bluetooth services chose to honor the other request.  What we do here,
                // is to make sure we note which device connected and not assume that the device
                // that connected is the device we requested.  The ultimate goal of the policy is
                // to remember which devices connected on which profile (regardless of the origin
                // of the connection request) so it knows which device to connect the next time.
                if (DBG) {
                    Log.d(TAG, "Different device connected: " + device + " CurrIndex: "
                            + mConnectionInfo.mDeviceIndex);
                }
            }

            // At this point positionInQ reflects where in the list the device that connected is,
            // i.e, its index.  Move the device to the front of the device list, since the policy is
            // to try to connect to the last connected device first.  Hence by moving the device
            // to the front of the list, the next time auto connect triggers, this will be the
            // device that the policy will try to connect on for this profile.
            if (positionInQ != 0) {
                moveToFrontLocked(positionInQ);
                // reset the device Index back to the first in the Queue
                //mConnectionInfo.mDeviceIndex = 0;
            }
            setConnectionStateLocked(device, BluetoothProfile.STATE_CONNECTED);
            // Reset the retry count
            mConnectionInfo.mRetryAttempt = 0;
            mConnectionInfo.mNumActiveConnections++;
            mConnectionInfo.mDeviceIndex++;
            if (DBG) {
                Log.d(TAG,
                        "Profile: " + mConnectionInfo.mProfile + " Number of Active Connections: "
                                + mConnectionInfo.mNumActiveConnections);
            }
        } else {
            // if no more retries, move to the next device
            if (DBG) {
                Log.d(TAG, "Connection fail or Disconnected");
            }
            // only if the given device was already connected decrement this.
            if (getConnectionStateLocked(device) == BluetoothProfile.STATE_CONNECTED) {
                mConnectionInfo.mNumActiveConnections--;
            }
            setConnectionStateLocked(device, BluetoothProfile.STATE_DISCONNECTED);
            if (!retry) {
                mConnectionInfo.mDeviceIndex++;
                if (DBG) {
                    Log.d(TAG, "Moving to device: " + mConnectionInfo.mDeviceIndex);
                }
                // Reset the retry count
                mConnectionInfo.mRetryAttempt = 0;
            } else {
                if (DBG) {
                    Log.d(TAG, "Staying with the same device - retrying: "
                            + mConnectionInfo.mDeviceIndex);
                }
            }
        }
    }

    /**
     * Move the item in the given position to the front of the list and push the rest down.
     *
     * @param position - position of the device to move from
     */
    private void moveToFrontLocked(int position) {
        DeviceInfo deviceInfo = mDeviceInfoList.get(position);
        if (deviceInfo.mBluetoothDevice == null) {
            if (DBG) {
                Log.d(TAG, "Unexpected: deviceToMove is null");
            }
            return;
        }
        mDeviceInfoList.remove(position);
        mDeviceInfoList.add(0, deviceInfo);
    }

    /**
     * Returns the profile that this devicesInfo is for.
     */
    public Integer getProfileLocked() {
        return mConnectionInfo.mProfile;
    }

    /**
     * Get the number of devices in the {@link #mDeviceInfoList} - paired and previously connected
     * devices
     *
     * @return number of paired devices on this profile.
     */
    public int getNumberOfPairedDevicesLocked() {
        return mDeviceInfoList.size();
    }

    /**
     * Increment the retry count. Called when a connection is made on the profile.
     */
    public void incrementRetryCountLocked() {
        if (mConnectionInfo != null) {
            mConnectionInfo.mRetryAttempt++;
        }
    }

    /**
     * Get the number of times a connection attempt has been tried on a device for this profile.
     *
     * @return number of retry attempts.
     */
    public Integer getRetryCountLocked() {
        return mConnectionInfo.mRetryAttempt;
    }

    /**
     * Set the mDeviceAvailableToConnect with the passed value.
     *
     * @param deviceAvailable - true or false.
     */
    public void setDeviceAvailableToConnectLocked(boolean deviceAvailable) {
        mConnectionInfo.mDeviceAvailableToConnect = deviceAvailable;
    }

    /**
     * Returns if there are any devices available to connect on this profile.
     *
     * @return true if a device is available, false
     * 1. if number of active connections on this profile has been maxed out or
     * 2. if all devices in the list have failed to connect already.
     */
    public boolean isProfileConnectableLocked() {
        if (DBG) {
            Log.d(TAG, "Profile: " + mConnectionInfo.mProfile + " Num of connections: "
                    + mConnectionInfo.mNumActiveConnections + " Conn Supported: "
                    + mConnectionInfo.mNumConnectionsSupported);
        }

        if (mConnectionInfo.mDeviceAvailableToConnect &&
                mConnectionInfo.mNumActiveConnections < mConnectionInfo.mNumConnectionsSupported) {
            return true;
        }
        return false;

    }

    /**
     * Return the current number of active connections on this profile.
     *
     * @return number of active connections.
     */
    public int getNumberOfActiveConnectionsLocked() {
        return mConnectionInfo.mNumActiveConnections;
    }

    /**
     * Reset the connection related bookkeeping information.
     * Called on a BluetoothAdapter Off to clean slate
     */
    public void resetConnectionInfoLocked() {
        mConnectionInfo.mNumActiveConnections = 0;
        mConnectionInfo.mDeviceIndex = 0;
        mConnectionInfo.mRetryAttempt = 0;
        mConnectionInfo.mDeviceAvailableToConnect = true;
        for (DeviceInfo info : mDeviceInfoList) {
            setConnectionStateLocked(info.getBluetoothDevice(),
                    BluetoothProfile.STATE_DISCONNECTED);
        }
    }

    public void resetDeviceListLocked() {
        if (mDeviceInfoList != null) {
            mDeviceInfoList.clear();
        }
        resetConnectionInfoLocked();
    }

}
