/*
 * Copyright 2016 The Android Open Source Project
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

#include "VtsDriverCommUtil.h"

#include <errno.h>
#include <netdb.h>
#include <netinet/in.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <sys/un.h>
#include <unistd.h>

#include <iostream>
#include <sstream>

#include "test/vts/proto/VtsDriverControlMessage.pb.h"

using namespace std;

#define MAX_HEADER_BUFFER_SIZE 128

namespace android {
namespace vts {

bool VtsDriverCommUtil::Connect(const string& socket_name) {
  struct sockaddr_un serv_addr;
  struct hostent* server;

  sockfd_ = socket(PF_UNIX, SOCK_STREAM, 0);
  if (sockfd_ < 0) {
    cerr << __func__ << " ERROR opening socket" << endl;
    return false;
  }

  server = gethostbyname("127.0.0.1");
  if (server == NULL) {
    cerr << __func__ << " ERROR can't resolve the host name, 127.0.0.1" << endl;
    return false;
  }

  bzero((char*)&serv_addr, sizeof(serv_addr));
  serv_addr.sun_family = AF_UNIX;
  strcpy(serv_addr.sun_path, socket_name.c_str());

  if (connect(sockfd_, (struct sockaddr*)&serv_addr, sizeof(serv_addr)) < 0) {
    cerr << __func__ << " ERROR connecting to " << socket_name
         << " errno = " << errno << " " << strerror(errno) << endl;
    sockfd_ = -1;
    return false;
  }
  return true;
}

int VtsDriverCommUtil::Close() {
  cout << getpid() << " " << __func__ << endl;
  int result = 0;
  if (sockfd_ != -1) {
    result = close(sockfd_);
    if (result != 0) {
      cerr << getpid() << " " << __func__ << ":" << __LINE__
           << " ERROR closing socket (errno = " << errno << ")" << endl;
    }

    sockfd_ = -1;
  }

  return result;
}

bool VtsDriverCommUtil::VtsSocketSendBytes(const string& message) {
  cout << getpid() << " " << __func__ << endl;
  if (sockfd_ == -1) {
    cerr << __func__ << " ERROR sockfd not set" << endl;
    return false;
  }
  std::stringstream header;
  header << message.length() << "\n";
  cout << getpid() << " [agent->driver] len = " << message.length() << endl;
  int n = write(sockfd_, header.str().c_str(), header.str().length());
  if (n < 0) {
    cerr << getpid() << " " << __func__ << ":" << __LINE__
         << " ERROR writing to socket" << endl;
    return false;
  }

  int bytes_sent = 0;
  int msg_len = message.length();
  while (bytes_sent < msg_len) {
    n = write(sockfd_, &message.c_str()[bytes_sent], msg_len - bytes_sent);
    if (n <= 0) {
      cerr << getpid() << " " << __func__ << ":" << __LINE__
           << " ERROR writing to socket" << endl;
      return false;
    }
    bytes_sent += n;
  }
  return true;
}

string VtsDriverCommUtil::VtsSocketRecvBytes() {
  cout << getpid() << " " << __func__ << endl;
  if (sockfd_ == -1) {
    cerr << getpid() << " " << __func__ << " ERROR sockfd not set" << endl;
    return string();
  }

  int header_index = 0;
  char header_buffer[MAX_HEADER_BUFFER_SIZE];

  for (header_index = 0; header_index < MAX_HEADER_BUFFER_SIZE;
       header_index++) {
    int ret = read(sockfd_, &header_buffer[header_index], 1);
    if (ret != 1) {
      int errno_save = errno;
      cerr << getpid() << " " << __func__
           << " ERROR reading the length ret = " << ret
           << " sockfd = " << sockfd_ << " "
           << " errno = " << errno_save << " " << strerror(errno_save) << endl;
      return string();
    }
    if (header_buffer[header_index] == '\n' ||
        header_buffer[header_index] == '\r') {
      header_buffer[header_index] = '\0';
      break;
    }
  }

  int msg_len = atoi(header_buffer);
  char* msg = (char*)malloc(msg_len + 1);
  if (!msg) {
    cerr << getpid() << " " << __func__ << " ERROR malloc failed" << endl;
    return string();
  }

  int bytes_read = 0;
  while (bytes_read < msg_len) {
    int result = read(sockfd_, &msg[bytes_read], msg_len - bytes_read);
    if (result <= 0) {
      cerr << getpid() << " " << __func__ << " ERROR read failed" << endl;
      return string();
    }
    bytes_read += result;
  }
  msg[msg_len] = '\0';
  cout << getpid() << " " << __func__ << " recv" << endl;
  return string(msg, msg_len);
}

bool VtsDriverCommUtil::VtsSocketSendMessage(
    const google::protobuf::Message& message) {
  cout << getpid() << " " << __func__ << endl;
  if (sockfd_ == -1) {
    cerr << getpid() << " " << __func__ << " ERROR sockfd not set" << endl;
    return false;
  }

  string message_string;
  if (!message.SerializeToString(&message_string)) {
    cerr << getpid() << " " << __func__
         << " ERROR can't serialize the message to a string." << endl;
    return false;
  }
  return VtsSocketSendBytes(message_string);
}

bool VtsDriverCommUtil::VtsSocketRecvMessage(
    google::protobuf::Message* message) {
  cout << getpid() << " " << __func__ << endl;
  if (sockfd_ == -1) {
    cerr << getpid() << " " << __func__ << " ERROR sockfd not set" << endl;
    return false;
  }

  string message_string = VtsSocketRecvBytes();
  if (message_string.length() == 0) {
    cerr << getpid() << " " << __func__ << " ERROR message string zero length"
         << endl;
    return false;
  }

  return message->ParseFromString(message_string);
}

}  // namespace vts
}  // namespace android
