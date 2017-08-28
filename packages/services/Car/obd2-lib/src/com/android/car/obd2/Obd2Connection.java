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

package com.android.car.obd2;

import android.util.Log;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** This class represents a connection between Java code and a "vehicle" that talks OBD2. */
public class Obd2Connection {

    /**
     * The transport layer that moves OBD2 requests from us to the remote entity and viceversa. It
     * is possible for this to be USB, Bluetooth, or just as simple as a pty for a simulator.
     */
    public interface UnderlyingTransport {
        String getAddress();

        boolean reconnect();

        boolean isConnected();

        InputStream getInputStream();

        OutputStream getOutputStream();
    }

    private final UnderlyingTransport mConnection;

    private static final String[] initCommands =
            new String[] {"ATD", "ATZ", "AT E0", "AT L0", "AT S0", "AT H0", "AT SP 0"};

    public Obd2Connection(UnderlyingTransport connection) {
        mConnection = Objects.requireNonNull(connection);
        runInitCommands();
    }

    public String getAddress() {
        return mConnection.getAddress();
    }

    private void runInitCommands() {
        for (final String initCommand : initCommands) {
            try {
                runImpl(initCommand);
            } catch (IOException | InterruptedException e) {
            }
        }
    }

    public boolean reconnect() {
        if (!mConnection.reconnect()) return false;
        runInitCommands();
        return true;
    }

    static int toDigitValue(char c) {
        if ((c >= '0') && (c <= '9')) return c - '0';
        switch (c) {
            case 'a':
            case 'A':
                return 10;
            case 'b':
            case 'B':
                return 11;
            case 'c':
            case 'C':
                return 12;
            case 'd':
            case 'D':
                return 13;
            case 'e':
            case 'E':
                return 14;
            case 'f':
            case 'F':
                return 15;
            default:
                throw new IllegalArgumentException(c + " is not a valid hex digit");
        }
    }

    int[] toHexValues(String buffer) {
        int[] values = new int[buffer.length() / 2];
        for (int i = 0; i < values.length; ++i) {
            values[i] =
                    16 * toDigitValue(buffer.charAt(2 * i))
                            + toDigitValue(buffer.charAt(2 * i + 1));
        }
        return values;
    }

    private String runImpl(String command) throws IOException, InterruptedException {
        InputStream in = Objects.requireNonNull(mConnection.getInputStream());
        OutputStream out = Objects.requireNonNull(mConnection.getOutputStream());

        out.write((command + "\r").getBytes());
        out.flush();

        StringBuilder response = new StringBuilder();
        while (true) {
            int value = in.read();
            if (value < 0) continue;
            char c = (char) value;
            // this is the prompt, stop here
            if (c == '>') break;
            if (c == '\r' || c == '\n' || c == ' ' || c == '\t' || c == '.') continue;
            response.append(c);
        }

        String responseValue = response.toString();
        return responseValue;
    }

    String removeSideData(String response, String... patterns) {
        for (String pattern : patterns) {
            if (response.contains(pattern)) response = response.replaceAll(pattern, "");
        }
        return response;
    }

    public int[] run(String command) throws IOException, InterruptedException {
        String responseValue = runImpl(command);
        String originalResponseValue = responseValue;
        if (responseValue.startsWith(command))
            responseValue = responseValue.substring(command.length());
        //TODO(egranata): should probably handle these intelligently
        responseValue =
                removeSideData(
                        responseValue,
                        "SEARCHING",
                        "ERROR",
                        "BUS INIT",
                        "BUSINIT",
                        "BUS ERROR",
                        "BUSERROR",
                        "STOPPED");
        if (responseValue.equals("OK")) return new int[] {1};
        if (responseValue.equals("?")) return new int[] {0};
        if (responseValue.equals("NODATA")) return new int[] {};
        if (responseValue.equals("UNABLETOCONNECT")) throw new IOException("connection failure");
        try {
            return toHexValues(responseValue);
        } catch (IllegalArgumentException e) {
            Log.e(
                    "OBD2",
                    String.format(
                            "conversion error: command: '%s', original response: '%s'"
                                    + ", processed response: '%s'",
                            command, originalResponseValue, responseValue));
            throw e;
        }
    }

    static class FourByteBitSet {
        private static final int[] masks =
                new int[] {
                    0b0000_0001,
                    0b0000_0010,
                    0b0000_0100,
                    0b0000_1000,
                    0b0001_0000,
                    0b0010_0000,
                    0b0100_0000,
                    0b1000_0000
                };

        private final byte mByte0;
        private final byte mByte1;
        private final byte mByte2;
        private final byte mByte3;

        FourByteBitSet(byte b0, byte b1, byte b2, byte b3) {
            mByte0 = b0;
            mByte1 = b1;
            mByte2 = b2;
            mByte3 = b3;
        }

        private byte getByte(int index) {
            switch (index) {
                case 0:
                    return mByte0;
                case 1:
                    return mByte1;
                case 2:
                    return mByte2;
                case 3:
                    return mByte3;
                default:
                    throw new IllegalArgumentException(index + " is not a valid byte index");
            }
        }

        private boolean getBit(byte b, int index) {
            if (index < 0 || index >= masks.length)
                throw new IllegalArgumentException(index + " is not a valid bit index");
            return 0 != (b & masks[index]);
        }

        public boolean getBit(int b, int index) {
            return getBit(getByte(b), index);
        }
    }

    public Set<Integer> getSupportedPIDs() throws IOException, InterruptedException {
        Set<Integer> result = new HashSet<>();
        String[] pids = new String[] {"0100", "0120", "0140", "0160"};
        int basePid = 0;
        for (String pid : pids) {
            int[] responseData = run(pid);
            if (responseData.length >= 6) {
                byte byte0 = (byte) (responseData[2] & 0xFF);
                byte byte1 = (byte) (responseData[3] & 0xFF);
                byte byte2 = (byte) (responseData[4] & 0xFF);
                byte byte3 = (byte) (responseData[5] & 0xFF);
                FourByteBitSet fourByteBitSet = new FourByteBitSet(byte0, byte1, byte2, byte3);
                for (int byteIndex = 0; byteIndex < 4; ++byteIndex) {
                    for (int bitIndex = 7; bitIndex >= 0; --bitIndex) {
                        if (fourByteBitSet.getBit(byteIndex, bitIndex)) {
                            result.add(basePid + 8 * byteIndex + 7 - bitIndex);
                        }
                    }
                }
            }
            basePid += 0x20;
        }

        return result;
    }
}
