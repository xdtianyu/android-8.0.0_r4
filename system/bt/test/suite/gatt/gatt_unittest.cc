/******************************************************************************
 *
 *  Copyright (C) 2015 Google, Inc.
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

#include <stdlib.h>
#include <time.h>
#include <unistd.h>

#include "gatt/gatt_test.h"

#define DEFAULT_RANDOM_SEED 42

namespace {

static void create_random_uuid(bt_uuid_t* uuid, int seed) {
  srand(seed < 0 ? time(NULL) : seed);
  for (int i = 0; i < 16; ++i) {
    uuid->uu[i] = (uint8_t)(rand() % 256);
  }
}

}  // namespace

namespace bttest {

TEST_F(GattTest, GattClientRegister) {
  // Registers gatt client.
  bt_uuid_t gatt_client_uuid;
  create_random_uuid(&gatt_client_uuid, DEFAULT_RANDOM_SEED);
  gatt_client_interface()->register_client(&gatt_client_uuid);
  semaphore_wait(register_client_callback_sem_);
  EXPECT_TRUE(status() == BT_STATUS_SUCCESS)
      << "Error registering GATT client app callback.";

  // Unregisters gatt client. No callback is expected.
  gatt_client_interface()->unregister_client(client_interface_id());
}

TEST_F(GattTest, GattServerRegister) {
  // Registers gatt server.
  bt_uuid_t gatt_server_uuid;
  create_random_uuid(&gatt_server_uuid, DEFAULT_RANDOM_SEED);
  gatt_server_interface()->register_server(&gatt_server_uuid);
  semaphore_wait(register_server_callback_sem_);
  EXPECT_TRUE(status() == BT_STATUS_SUCCESS)
      << "Error registering GATT server app callback.";

  // Unregisters gatt server. No callback is expected.
  gatt_server_interface()->unregister_server(server_interface_id());
}

TEST_F(GattTest, GattServerBuild) {
  // Registers gatt server.
  bt_uuid_t gatt_server_uuid;
  create_random_uuid(&gatt_server_uuid, DEFAULT_RANDOM_SEED);
  gatt_server_interface()->register_server(&gatt_server_uuid);
  semaphore_wait(register_server_callback_sem_);
  EXPECT_TRUE(status() == BT_STATUS_SUCCESS)
      << "Error registering GATT server app callback.";

  // Service UUID.
  bt_uuid_t srvc_uuid;
  create_random_uuid(&srvc_uuid, -1);

  // Characteristics UUID.
  bt_uuid_t char_uuid;
  create_random_uuid(&char_uuid, -1);

  // Descriptor UUID.
  bt_uuid_t desc_uuid;
  create_random_uuid(&desc_uuid, -1);

  // Adds service.
  int server_if = server_interface_id();

  std::vector<btgatt_db_element_t> service = {
      {.type = BTGATT_DB_PRIMARY_SERVICE, .uuid = srvc_uuid},
      {.type = BTGATT_DB_CHARACTERISTIC,
       .uuid = char_uuid,
       .properties = 0x10 /* notification */,
       .permissions = 0x01 /* read only */},
      {.type = BTGATT_DB_DESCRIPTOR, .uuid = desc_uuid, .permissions = 0x01}};

  gatt_server_interface()->add_service(server_if, service);
  semaphore_wait(service_added_callback_sem_);
  EXPECT_TRUE(status() == BT_STATUS_SUCCESS) << "Error adding service.";

  // Stops server.
  gatt_server_interface()->stop_service(server_if, service_handle());
  semaphore_wait(service_stopped_callback_sem_);
  EXPECT_TRUE(status() == BT_STATUS_SUCCESS) << "Error stopping server.";

  // Deletes service.
  gatt_server_interface()->delete_service(server_if, service_handle());
  semaphore_wait(service_deleted_callback_sem_);
  EXPECT_TRUE(status() == BT_STATUS_SUCCESS) << "Error deleting service.";

  // Unregisters gatt server. No callback is expected.
  gatt_server_interface()->unregister_server(server_if);
}

}  // bttest
