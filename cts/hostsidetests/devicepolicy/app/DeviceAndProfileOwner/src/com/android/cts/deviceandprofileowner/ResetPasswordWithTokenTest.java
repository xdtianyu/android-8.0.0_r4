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
package com.android.cts.deviceandprofileowner;


public class ResetPasswordWithTokenTest extends BaseDeviceAdminTest {

    private static final String PASSWORD = "1234";

    private static final byte[] TOKEN0 = "abcdefghijklmnopqrstuvwxyz0123456789".getBytes();
    private static final byte[] TOKEN1 = "abcdefghijklmnopqrstuvwxyz012345678*".getBytes();

    public void testResetPasswordWithToken() {
        testResetPasswordWithToken(false);
    }

    public void testResetPasswordWithTokenMayFail() {
        // If this test is executed on a device with password token disabled, allow the test to
        // pass.
        testResetPasswordWithToken(true);
    }

    private void testResetPasswordWithToken(boolean acceptFailure) {
        try {
            // set up a token
            assertFalse(mDevicePolicyManager.isResetPasswordTokenActive(ADMIN_RECEIVER_COMPONENT));

            try {
                // On devices with password token disabled, calling this method will throw
                // a security exception. If that's anticipated, then return early without failing.
                assertTrue(mDevicePolicyManager.setResetPasswordToken(ADMIN_RECEIVER_COMPONENT,
                        TOKEN0));
            } catch (SecurityException e) {
                if (acceptFailure &&
                        e.getMessage().equals("Escrow token is disabled on the current user")) {
                    return;
                } else {
                    throw e;
                }
            }
            assertTrue(mDevicePolicyManager.isResetPasswordTokenActive(ADMIN_RECEIVER_COMPONENT));

            // resetting password with wrong token should fail
            assertFalse(mDevicePolicyManager.resetPasswordWithToken(ADMIN_RECEIVER_COMPONENT, PASSWORD,
                    TOKEN1, 0));
            // try changing password with token
            assertTrue(mDevicePolicyManager.resetPasswordWithToken(ADMIN_RECEIVER_COMPONENT, PASSWORD,
                    TOKEN0, 0));
            // clear password with token
            assertTrue(mDevicePolicyManager.resetPasswordWithToken(ADMIN_RECEIVER_COMPONENT, null,
                    TOKEN0, 0));

            // remove token and check it succeeds
            assertTrue(mDevicePolicyManager.clearResetPasswordToken(ADMIN_RECEIVER_COMPONENT));
            assertFalse(mDevicePolicyManager.isResetPasswordTokenActive(ADMIN_RECEIVER_COMPONENT));
            assertFalse(mDevicePolicyManager.resetPasswordWithToken(ADMIN_RECEIVER_COMPONENT, PASSWORD,
                    TOKEN0, 0));
        } finally {
            mDevicePolicyManager.clearResetPasswordToken(ADMIN_RECEIVER_COMPONENT);
        }
    }

    //TODO: add test to reboot device and reset password while user is still locked.
}