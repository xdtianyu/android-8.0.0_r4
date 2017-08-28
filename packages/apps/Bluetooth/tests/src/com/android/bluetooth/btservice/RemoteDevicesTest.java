package com.android.bluetooth.btservice;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelUuid;
import android.util.Log;
import com.android.bluetooth.Utils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;
import java.lang.Thread;

import android.test.AndroidTestCase;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class RemoteDevicesTest extends AndroidTestCase {
    public void testSendUuidIntent() {
        if (Looper.myLooper() == null) Looper.prepare();

        AdapterService mockService = mock(AdapterService.class);
        RemoteDevices devices = new RemoteDevices(mockService);
        BluetoothDevice device =
                BluetoothAdapter.getDefaultAdapter().getRemoteDevice("00:11:22:33:44:55");
        devices.updateUuids(device);

        Looper.myLooper().quitSafely();
        Looper.loop();

        verify(mockService).sendBroadcast(any(), anyString());
        verifyNoMoreInteractions(mockService);
    }
}
