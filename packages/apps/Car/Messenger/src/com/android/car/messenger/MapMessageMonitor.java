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

package com.android.car.messenger;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothMapClient;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Component that monitors for incoming messages and posts/updates notifications.
 * <p>
 * It also handles notifications requests e.g. sending auto-replies and message play-out.
 * <p>
 * It will receive broadcasts for new incoming messages as long as the MapClient is connected in
 * {@link MessengerService}.
 */
class MapMessageMonitor {
    private static final String TAG = "Messenger.MsgMonitor";
    private static final boolean DBG = MessengerService.DBG;

    private final Context mContext;
    private final BluetoothMapReceiver mBluetoothMapReceiver;
    private final NotificationManager mNotificationManager;
    private final Map<MessageKey, MapMessage> mMessages = new HashMap<>();
    private final Map<SenderKey, NotificationInfo> mNotificationInfos = new HashMap<>();
    private final TTSHelper mTTSHelper;

    MapMessageMonitor(Context context) {
        mContext = context;
        mBluetoothMapReceiver = new BluetoothMapReceiver();
        mNotificationManager =
                (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        mTTSHelper = new TTSHelper(mContext);
    }

    private void handleNewMessage(Intent intent) {
        if (DBG) {
            Log.d(TAG, "Handling new message");
        }
        try {
            MapMessage message = MapMessage.parseFrom(intent);
            if (MessengerService.VDBG) {
                Log.v(TAG, "Parsed message: " + message);
            }
            MessageKey messageKey = new MessageKey(message);
            boolean repeatMessage = mMessages.containsKey(messageKey);
            mMessages.put(messageKey, message);
            if (!repeatMessage) {
                updateNotificationInfo(message, messageKey);
            }
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Dropping invalid MAP message", e);
        }
    }

    private void updateNotificationInfo(MapMessage message, MessageKey messageKey) {
        SenderKey senderKey = new SenderKey(message);
        NotificationInfo notificationInfo = mNotificationInfos.get(senderKey);
        if (notificationInfo == null) {
            notificationInfo =
                    new NotificationInfo(message.getSenderName(), message.getSenderContactUri());
            mNotificationInfos.put(senderKey, notificationInfo);
        }
        notificationInfo.mMessageKeys.add(messageKey);
        updateNotificationFor(senderKey, notificationInfo, false /* ttsPlaying */);
    }

    private void updateNotificationFor(SenderKey senderKey,
            NotificationInfo notificationInfo, boolean ttsPlaying) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(mContext);
        // TODO(sriniv): Use right icon when switching to correct layout. b/33280056.
        builder.setSmallIcon(android.R.drawable.btn_plus);
        builder.setContentTitle(notificationInfo.mSenderName);
        builder.setContentText(mContext.getResources().getQuantityString(
                R.plurals.notification_new_message, notificationInfo.mMessageKeys.size(),
                notificationInfo.mMessageKeys.size()));

        Intent deleteIntent = new Intent(mContext, MessengerService.class)
                .setAction(MessengerService.ACTION_CLEAR_NOTIFICATION_STATE)
                .putExtra(MessengerService.EXTRA_SENDER_KEY, senderKey);
        builder.setDeleteIntent(
                PendingIntent.getService(mContext, notificationInfo.mNotificationId, deleteIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT));

        String messageActions[] = {
                MessengerService.ACTION_AUTO_REPLY,
                MessengerService.ACTION_PLAY_MESSAGES
        };
        // TODO(sriniv): Actual spec does not have any of these strings. Remove later. b/33280056.
        // is implemented for notifications.
        String actionTexts[] = { "Reply", "Play" };
        if (ttsPlaying) {
            messageActions[1] = MessengerService.ACTION_STOP_PLAYOUT;
            actionTexts[1] = "Stop";
        }
        for (int i = 0; i < messageActions.length; i++) {
            Intent intent = new Intent(mContext, MessengerService.class)
                    .setAction(messageActions[i])
                    .putExtra(MessengerService.EXTRA_SENDER_KEY, senderKey);
            PendingIntent pendingIntent = PendingIntent.getService(mContext,
                    notificationInfo.mNotificationId, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            builder.addAction(android.R.drawable.ic_media_play, actionTexts[i], pendingIntent);
        }
        mNotificationManager.notify(notificationInfo.mNotificationId, builder.build());
    }

    void clearNotificationState(SenderKey senderKey) {
        if (DBG) {
            Log.d(TAG, "Clearing notification state for: " + senderKey);
        }
        mNotificationInfos.remove(senderKey);
    }

    void playMessages(SenderKey senderKey) {
        NotificationInfo notificationInfo = mNotificationInfos.get(senderKey);
        if (notificationInfo == null) {
            Log.e(TAG, "Unknown senderKey! " + senderKey);
            return;
        }

        StringBuilder ttsMessage = new StringBuilder();
        ttsMessage.append(notificationInfo.mSenderName)
                .append(" ").append(mContext.getString(R.string.tts_says_verb));
        for (MessageKey messageKey : notificationInfo.mMessageKeys) {
            MapMessage message = mMessages.get(messageKey);
            if (message != null) {
                ttsMessage.append(". ").append(message.getText());
            }
        }

        mTTSHelper.requestPlay(ttsMessage.toString(),
                new TTSHelper.Listener() {
            @Override
            public void onTTSStarted() {
                updateNotificationFor(senderKey, notificationInfo, true);
            }

            @Override
            public void onTTSStopped() {
                updateNotificationFor(senderKey, notificationInfo, false);
            }

            @Override
            public void onTTSError() {
                Toast.makeText(mContext, R.string.tts_failed_toast, Toast.LENGTH_SHORT).show();
                onTTSStopped();
            }
        });
    }

    void stopPlayout() {
        mTTSHelper.requestStop();
    }

    boolean sendAutoReply(SenderKey senderKey, BluetoothMapClient mapClient) {
        if (DBG) {
            Log.d(TAG, "Sending auto-reply to: " + senderKey);
        }
        BluetoothDevice device =
                BluetoothAdapter.getDefaultAdapter().getRemoteDevice(senderKey.mDeviceAddress);
        NotificationInfo notificationInfo = mNotificationInfos.get(senderKey);
        if (notificationInfo == null) {
            Log.w(TAG, "No notificationInfo found for senderKey: " + senderKey);
            return false;
        }
        if (notificationInfo.mSenderContactUri == null) {
            Log.w(TAG, "Do not have contact URI for sender!");
            return false;
        }
        Uri recipientUris[] = { Uri.parse(notificationInfo.mSenderContactUri) };

        final int requestCode = senderKey.hashCode();
        PendingIntent sentIntent =
                PendingIntent.getBroadcast(mContext, requestCode, new Intent(
                        BluetoothMapClient.ACTION_MESSAGE_SENT_SUCCESSFULLY),
                        PendingIntent.FLAG_ONE_SHOT);
        String message = mContext.getString(R.string.auto_reply_message);
        return mapClient.sendMessage(device, recipientUris, message, sentIntent, null);
    }

    void handleMapDisconnect() {
        cleanupMessagesAndNotifications((key) -> true);
    }

    void handleDeviceDisconnect(BluetoothDevice device) {
        cleanupMessagesAndNotifications((key) -> key.matches(device.getAddress()));
    }

    private void cleanupMessagesAndNotifications(Predicate<CompositeKey> predicate) {
        Iterator<Map.Entry<MessageKey, MapMessage>> messageIt = mMessages.entrySet().iterator();
        while (messageIt.hasNext()) {
            if (predicate.test(messageIt.next().getKey())) {
                messageIt.remove();
            }
        }
        Iterator<Map.Entry<SenderKey, NotificationInfo>> notificationIt =
                mNotificationInfos.entrySet().iterator();
        while (notificationIt.hasNext()) {
            Map.Entry<SenderKey, NotificationInfo> entry = notificationIt.next();
            if (predicate.test(entry.getKey())) {
                mNotificationManager.cancel(entry.getValue().mNotificationId);
                notificationIt.remove();
            }
        }
    }

    void cleanup() {
        mBluetoothMapReceiver.cleanup();
        mTTSHelper.cleanup();
    }

    // Used to monitor for new incoming messages and sent-message broadcast.
    private class BluetoothMapReceiver extends BroadcastReceiver {
        BluetoothMapReceiver() {
            if (DBG) {
                Log.d(TAG, "Registering receiver for new messages");
            }
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(BluetoothMapClient.ACTION_MESSAGE_SENT_SUCCESSFULLY);
            intentFilter.addAction(BluetoothMapClient.ACTION_MESSAGE_RECEIVED);
            mContext.registerReceiver(this, intentFilter);
        }

        void cleanup() {
            mContext.unregisterReceiver(this);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (BluetoothMapClient.ACTION_MESSAGE_SENT_SUCCESSFULLY.equals(intent.getAction())) {
                if (DBG) {
                    Log.d(TAG, "SMS was sent successfully!");
                }
            } else if (BluetoothMapClient.ACTION_MESSAGE_RECEIVED.equals(intent.getAction())) {
                handleNewMessage(intent);
            } else {
                Log.w(TAG, "Ignoring unknown broadcast " + intent.getAction());
            }
        }
    }

    /**
     * Key used in HashMap that is composed from a BT device-address and device-specific "sub key"
     */
    private abstract static class CompositeKey {
        final String mDeviceAddress;
        final String mSubKey;

        CompositeKey(String deviceAddress, String subKey) {
            mDeviceAddress = deviceAddress;
            mSubKey = subKey;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            CompositeKey that = (CompositeKey) o;
            return Objects.equals(mDeviceAddress, that.mDeviceAddress)
                    && Objects.equals(mSubKey, that.mSubKey);
        }

        boolean matches(String deviceAddress) {
            return mDeviceAddress.equals(deviceAddress);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mDeviceAddress, mSubKey);
        }

        @Override
        public String toString() {
            return String.format("%s, deviceAddress: %s, subKey: %s",
                    getClass().getSimpleName(), mDeviceAddress, mSubKey);
        }
    }

    /**
     * {@link CompositeKey} subclass used to identify specific messages; it uses message-handle as
     * the secondary key.
     */
    private static class MessageKey extends CompositeKey {
        MessageKey(MapMessage message) {
            super(message.getDevice().getAddress(), message.getHandle());
        }
    }

    /**
     * CompositeKey used to identify Notification info for a sender; it uses a combination of
     * senderContactUri and senderContactName as the secondary key.
     */
    static class SenderKey extends CompositeKey implements Parcelable {
        private SenderKey(String deviceAddress, String key) {
            super(deviceAddress, key);
        }

        SenderKey(MapMessage message) {
            // Use a combination of senderName and senderContactUri for key. Ideally we would use
            // only senderContactUri (which is encoded phone no.). However since some phones don't
            // provide these, we fall back to senderName. Since senderName may not be unique, we
            // include senderContactUri also to provide uniqueness in cases it is available.
            this(message.getDevice().getAddress(),
                    message.getSenderName() + "/" + message.getSenderContactUri());
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(mDeviceAddress);
            dest.writeString(mSubKey);
        }

        public static final Parcelable.Creator<SenderKey> CREATOR =
                new Parcelable.Creator<SenderKey>() {
            @Override
            public SenderKey createFromParcel(Parcel source) {
                return new SenderKey(source.readString(), source.readString());
            }

            @Override
            public SenderKey[] newArray(int size) {
                return new SenderKey[size];
            }
        };
    }

    /**
     * Information about a single notification that is displayed.
     */
    private static class NotificationInfo {
        private static int NEXT_NOTIFICATION_ID = 0;

        final int mNotificationId = NEXT_NOTIFICATION_ID++;
        final String mSenderName;
        @Nullable
        final String mSenderContactUri;
        final List<MessageKey> mMessageKeys = new LinkedList<>();

        NotificationInfo(String senderName, @Nullable String senderContactUri) {
            mSenderName = senderName;
            mSenderContactUri = senderContactUri;
        }
    }
}
