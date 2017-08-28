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

#include "TcpServerForRunner.h"

#include <errno.h>

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include <dirent.h>

#include <netdb.h>
#include <netinet/in.h>

#include <sys/socket.h>
#include <sys/types.h>

#include <utils/RefBase.h>

#include <iostream>
#include <sstream>

#include "AgentRequestHandler.h"
#include "BinderClientToDriver.h"
#include "test/vts/proto/AndroidSystemControlMessage.pb.h"

using namespace std;

namespace android {
namespace vts {

// Starts to run a TCP server (foreground).
int StartTcpServerForRunner(const char* spec_dir_path,
                            const char* fuzzer_path32,
                            const char* fuzzer_path64, const char* shell_path32,
                            const char* shell_path64) {
  int sockfd;
  socklen_t clilen;
  struct sockaddr_in serv_addr;
  struct sockaddr_in cli_addr;

  sockfd = socket(AF_INET, SOCK_STREAM, 0);
  if (sockfd < 0) {
    cerr << "Can't open the socket." << endl;
    return -1;
  }

  bzero((char*)&serv_addr, sizeof(serv_addr));
  serv_addr.sin_family = AF_INET;
  serv_addr.sin_addr.s_addr = INADDR_ANY;
  serv_addr.sin_port = htons(0);

  if (::bind(sockfd, (struct sockaddr*)&serv_addr, sizeof(serv_addr)) == -1) {
    cerr << __func__ << " binding failed. errno = " << errno << endl;
    cerr << __func__ << " " << strerror(errno) << endl;
    return -1;
  }

  socklen_t sa_len = sizeof(serv_addr);
  if (getsockname(sockfd, (struct sockaddr*) &serv_addr, &sa_len) == -1) {
    cerr << __func__ << " getsockname failed. errno = " << errno << endl;
    cerr << __func__ << " " << strerror(errno) << endl;
    return -1;
  }

  cout << "[agent] TCP server port is " << (int) ntohs(serv_addr.sin_port)
       << endl;
  FILE* fp = fopen("/data/local/tmp/vts_tcp_server_port", "wt");
  if (!fp) {
    cerr << __func__ << " can't write to "
         << "/data/local/tmp/vts_tcp_server_port" << endl;
    return -1;
  }
  fprintf(fp, "%d", (int) ntohs(serv_addr.sin_port));
  fclose(fp);

  cout << "[agent] listening" << endl;
  if (listen(sockfd, 5) == -1) {
    cerr << __func__ << " listen failed." << endl;
    return -1;
  }
  clilen = sizeof(cli_addr);
  while (true) {
    cout << "[agent] accepting" << endl;
    int newsockfd = ::accept(sockfd, (struct sockaddr*)&cli_addr, &clilen);
    if (newsockfd < 0) {
      cerr << __func__ << " accept failed" << endl;
      return -1;
    }

    cout << "[runner->agent] NEW SESSION" << endl;
    cout << "[runner->agent] ===========" << endl;
    pid_t pid = fork();
    if (pid == 0) {  // child
      close(sockfd);
      cout << "[agent] process for a runner - pid = " << getpid() << endl;
      AgentRequestHandler handler(spec_dir_path, fuzzer_path32, fuzzer_path64,
                                  shell_path32, shell_path64);
      handler.SetSockfd(newsockfd);
      while (handler.ProcessOneCommand())
        ;
      exit(-1);
    } else if (pid < 0) {
      cerr << "can't fork a child process to handle a session." << endl;
      return -1;
    } else {
      close(newsockfd);
    }
  }
  return 0;
}

}  // namespace vts
}  // namespace android
