/******************************************************************************
 *
 *  Copyright (C) 2014 Google, Inc.
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

#include <gtest/gtest.h>

#include "btcore/include/bdaddr.h"

static const char* test_addr = "12:34:56:78:9a:bc";
static const char* test_addr2 = "cb:a9:87:65:43:21";

TEST(BdaddrTest, test_empty) {
  bt_bdaddr_t empty;
  string_to_bdaddr("00:00:00:00:00:00", &empty);
  ASSERT_TRUE(bdaddr_is_empty(&empty));

  bt_bdaddr_t not_empty;
  string_to_bdaddr("00:00:00:00:00:01", &not_empty);
  ASSERT_FALSE(bdaddr_is_empty(&not_empty));
}

TEST(BdaddrTest, test_to_from_str) {
  char ret[19];
  bt_bdaddr_t bdaddr;
  string_to_bdaddr(test_addr, &bdaddr);

  ASSERT_EQ(0x12, bdaddr.address[0]);
  ASSERT_EQ(0x34, bdaddr.address[1]);
  ASSERT_EQ(0x56, bdaddr.address[2]);
  ASSERT_EQ(0x78, bdaddr.address[3]);
  ASSERT_EQ(0x9A, bdaddr.address[4]);
  ASSERT_EQ(0xBC, bdaddr.address[5]);

  bdaddr_to_string(&bdaddr, ret, sizeof(ret));

  ASSERT_STREQ(test_addr, ret);
}

TEST(BdaddrTest, test_equals) {
  bt_bdaddr_t bdaddr1;
  bt_bdaddr_t bdaddr2;
  bt_bdaddr_t bdaddr3;
  string_to_bdaddr(test_addr, &bdaddr1);
  string_to_bdaddr(test_addr, &bdaddr2);
  EXPECT_TRUE(bdaddr_equals(&bdaddr1, &bdaddr2));

  string_to_bdaddr(test_addr2, &bdaddr3);
  EXPECT_FALSE(bdaddr_equals(&bdaddr2, &bdaddr3));
}

TEST(BdaddrTest, test_copy) {
  bt_bdaddr_t bdaddr1;
  bt_bdaddr_t bdaddr2;
  string_to_bdaddr(test_addr, &bdaddr1);
  bdaddr_copy(&bdaddr2, &bdaddr1);

  EXPECT_TRUE(bdaddr_equals(&bdaddr1, &bdaddr2));
}
