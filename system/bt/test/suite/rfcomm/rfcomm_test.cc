/******************************************************************************
 *
 *  Copyright (C) 2016 Google, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

#include "rfcomm/rfcomm_test.h"
#include "adapter/bluetooth_test.h"

#include "btcore/include/bdaddr.h"
#include "btcore/include/uuid.h"

namespace bttest {

const bt_uuid_t RFCommTest::HFP_UUID = {{0x00, 0x00, 0x11, 0x1E, 0x00, 0x00,
                                         0x10, 0x00, 0x80, 0x00, 0x00, 0x80,
                                         0x5F, 0x9B, 0x34, 0xFB}};

void RFCommTest::SetUp() {
  BluetoothTest::SetUp();

  ASSERT_EQ(bt_interface()->enable(false), BT_STATUS_SUCCESS);
  semaphore_wait(adapter_state_changed_callback_sem_);
  ASSERT_TRUE(GetState() == BT_STATE_ON);
  socket_interface_ =
      (const btsock_interface_t*)bt_interface()->get_profile_interface(
          BT_PROFILE_SOCKETS_ID);
  ASSERT_NE(socket_interface_, nullptr);

  // Find a bonded device that supports HFP
  string_to_bdaddr("00:00:00:00:00:00", &bt_remote_bdaddr_);
  char value[1280];

  bt_property_t* bonded_devices_prop =
      GetProperty(BT_PROPERTY_ADAPTER_BONDED_DEVICES);
  bt_bdaddr_t* devices = (bt_bdaddr_t*)bonded_devices_prop->val;
  int num_bonded_devices = bonded_devices_prop->len / sizeof(bt_bdaddr_t);

  for (int i = 0; i < num_bonded_devices && bdaddr_is_empty(&bt_remote_bdaddr_);
       i++) {
    ClearSemaphore(remote_device_properties_callback_sem_);
    bt_interface()->get_remote_device_property(&devices[i], BT_PROPERTY_UUIDS);
    semaphore_wait(remote_device_properties_callback_sem_);

    bt_property_t* uuid_prop =
        GetRemoteDeviceProperty(&devices[i], BT_PROPERTY_UUIDS);
    if (uuid_prop == nullptr) continue;
    bt_uuid_t* uuids = (bt_uuid_t*)uuid_prop->val;
    int num_uuids = uuid_prop->len / sizeof(bt_uuid_t);

    for (int j = 0; j < num_uuids; j++) {
      uuid_to_string(&uuids[j], (uuid_string_t*)value);
      if (!memcmp(uuids + j, &HFP_UUID, sizeof(bt_uuid_t))) {
        bdaddr_copy(&bt_remote_bdaddr_, devices + i);
        break;
      }
    }
  }

  ASSERT_FALSE(bdaddr_is_empty(&bt_remote_bdaddr_))
      << "Could not find paired device that supports HFP";
}

void RFCommTest::TearDown() {
  socket_interface_ = NULL;

  ASSERT_EQ(bt_interface()->disable(), BT_STATUS_SUCCESS);
  semaphore_wait(adapter_state_changed_callback_sem_);

  BluetoothTest::TearDown();
}

}  // bttest
