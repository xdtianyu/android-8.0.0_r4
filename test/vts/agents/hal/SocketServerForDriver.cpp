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

#include "SocketServerForDriver.h"

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

#include <VtsDriverCommUtil.h>

#include "test/vts/proto/AndroidSystemControlMessage.pb.h"

using namespace std;

namespace android {
namespace vts {

static const int kCallbackServerPort = 5010;

void SocketServerForDriver::RpcCallToRunner(
    const AndroidSystemCallbackRequestMessage& message) {
  cout << __func__ << ":" << __LINE__ << " " << message.id() << endl;
  struct sockaddr_in serv_addr;
  struct hostent* server;

  int sockfd;
  sockfd = socket(AF_INET, SOCK_STREAM, 0);
  if (sockfd < 0) {
    cerr << __func__ << " ERROR opening socket" << endl;
    exit(-1);
    return;
  }
  server = gethostbyname("127.0.0.1");
  if (server == NULL) {
    cerr << __func__ << " ERROR can't resolve the host name, localhost" << endl;
    exit(-1);
    return;
  }
  bzero((char*)&serv_addr, sizeof(serv_addr));
  serv_addr.sin_family = AF_INET;
  bcopy((char*)server->h_addr, (char*)&serv_addr.sin_addr.s_addr,
        server->h_length);
  serv_addr.sin_port = htons(runner_port_);

  if (connect(sockfd, (struct sockaddr*)&serv_addr, sizeof(serv_addr)) < 0) {
    cerr << __func__ << " ERROR connecting" << endl;
    exit(-1);
    return;
  }

  VtsDriverCommUtil util(sockfd);
  if (!util.VtsSocketSendMessage(message)) return;
}

void SocketServerForDriver::Start() {
  AndroidSystemCallbackRequestMessage message;
  if (!VtsSocketRecvMessage(&message)) return;
  cout << __func__ << " Callback ID: " << message.id() << endl;
  RpcCallToRunner(message);
  Close();
}

int StartSocketServerForDriver(const string& callback_socket_name,
                               int runner_port) {
  struct sockaddr_un serv_addr;
  int pid = fork();
  if (pid < 0) {
    cerr << __func__ << " ERROR on fork" << endl;
    return -1;
  } else if (pid > 0) {
    return 0;
  }

  if (runner_port == -1) {
    runner_port = kCallbackServerPort;
  }
  // only child process continues;
  int sockfd;
  sockfd = socket(PF_UNIX, SOCK_STREAM, 0);
  if (sockfd < 0) {
    cerr << __func__ << " ERROR opening socket" << endl;
    return -1;
  }

  bzero((char*) &serv_addr, sizeof(serv_addr));
  serv_addr.sun_family = AF_UNIX;
  strcpy(serv_addr.sun_path, callback_socket_name.c_str());
  cout << "[agent] callback server at " << callback_socket_name << endl;

  if (::bind(sockfd, (struct sockaddr*) &serv_addr, sizeof(serv_addr)) < 0) {
    int error_save = errno;
    cerr << getpid() << " " << __func__ << " ERROR on binding "
         << callback_socket_name << " errno = " << error_save << " "
         << strerror(error_save) << endl;
    return -1;
  }

  if (listen(sockfd, 5) < 0) {
    cerr << __func__ << " ERROR on listening" << endl;
    return -1;
  }

  while (1) {
    int newsockfd;
    struct sockaddr_in cli_addr;
    socklen_t clilen;

    clilen = sizeof(cli_addr);
    newsockfd = accept(sockfd, (struct sockaddr*) &cli_addr, &clilen);
    if (newsockfd < 0) {
      cerr << __func__ << " ERROR on accept " << strerror(errno) << endl;
      break;
    }
    cout << "[agent] new callback connection." << endl;
    pid = fork();
    if (pid == 0) {
      close(sockfd);
      SocketServerForDriver server(newsockfd, runner_port);
      server.Start();
      exit(0);
    } else if (pid > 0) {
      close(newsockfd);
    } else {
      cerr << __func__ << " ERROR on fork" << endl;
      break;
    }
  }
  close(sockfd);
  exit(0);
}

}  // namespace vts
}  // namespace android
