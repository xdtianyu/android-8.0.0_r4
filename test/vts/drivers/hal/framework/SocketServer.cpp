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

#ifndef VTS_AGENT_DRIVER_COMM_BINDER  // socket

#include "SocketServer.h"

#define LOG_TAG "VtsDriverHalSocketServer"
#include <utils/Log.h>
#include <utils/String8.h>

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
#include <sys/un.h>

#include <utils/RefBase.h>

#include <fstream>
#include <iostream>
#include <sstream>
#include <string>

#include <VtsDriverCommUtil.h>

#include <google/protobuf/text_format.h>
#include "test/vts/proto/VtsDriverControlMessage.pb.h"

#include "binder/VtsFuzzerBinderService.h"
#include "specification_parser/SpecificationBuilder.h"

#include "test/vts/proto/ComponentSpecificationMessage.pb.h"

using namespace std;

namespace android {
namespace vts {

void VtsDriverHalSocketServer::Exit() { printf("VtsFuzzerServer::Exit\n"); }

int32_t VtsDriverHalSocketServer::LoadHal(const string& path, int target_class,
                                          int target_type, float target_version,
                                          const string& target_package,
                                          const string& target_component_name,
                                          const string& hw_binder_service_name,
                                          const string& module_name) {
  printf("VtsFuzzerServer::LoadHal(%s)\n", path.c_str());
  bool success = spec_builder_.LoadTargetComponent(
      path.c_str(), lib_path_, target_class, target_type, target_version,
      target_package.c_str(), target_component_name.c_str(),
      hw_binder_service_name.c_str(),
      module_name.c_str());
  cout << "Result: " << success << std::endl;
  if (success) {
    return 0;
  } else {
    return -1;
  }
}

int32_t VtsDriverHalSocketServer::Status(int32_t type) {
  printf("VtsFuzzerServer::Status(%i)\n", type);
  return 0;
}

const char* VtsDriverHalSocketServer::ReadSpecification(
    const string& name, int target_class, int target_type, float target_version,
    const string& target_package) {
  printf("VtsFuzzerServer::ReadSpecification(%s)\n", name.c_str());
  ComponentSpecificationMessage* msg =
      spec_builder_.FindComponentSpecification(
          target_class, target_type, target_version,
          "", target_package, name);
  string* result = new string();
  google::protobuf::TextFormat::PrintToString(*msg, result);
  return result->c_str();
}

const char* VtsDriverHalSocketServer::Call(const string& arg) {
  cout << "VtsFuzzerServer::Call(" << arg << ")" << endl;
  FunctionSpecificationMessage* func_msg = new FunctionSpecificationMessage();
  cout << __func__ << ":" << __LINE__ << endl;
  google::protobuf::TextFormat::MergeFromString(arg, func_msg);
  cout << __func__ << ":" << __LINE__ << endl;
  const string& result = spec_builder_.CallFunction(func_msg);
  cout << __func__ << ":" << __LINE__ << endl;
  return result.c_str();
}

const char* VtsDriverHalSocketServer::GetAttribute(const string& arg) {
  printf("%s(%s)\n", __func__, arg.c_str());
  FunctionSpecificationMessage* func_msg = new FunctionSpecificationMessage();
  google::protobuf::TextFormat::MergeFromString(arg, func_msg);
  const string& result = spec_builder_.GetAttribute(func_msg);
  printf("%s: done\n", __func__);
  return result.c_str();
}

string VtsDriverHalSocketServer::ListFunctions() const {
  cout << "VtsFuzzerServer::" << __func__ << endl;
  vts::ComponentSpecificationMessage* spec =
      spec_builder_.GetComponentSpecification();
  string output;
  if (!spec) {
    return output;
  }
  cout << "VtsFuzzerServer::" << __func__ << " serialize" << endl;
  if (google::protobuf::TextFormat::PrintToString(*spec, &output)) {
    cout << "VtsFuzzerServer::" << __func__ << " result length "
         << output.length() << endl;
    return output;
  } else {
    cout << "can't serialize the interface spec message to a string." << endl;
    return output;
  }
}

bool VtsDriverHalSocketServer::ProcessOneCommand() {
  cout << __func__ << ":" << __LINE__ << " entry" << endl;
  VtsDriverControlCommandMessage command_message;
  if (!VtsSocketRecvMessage(&command_message)) return false;
  cout << __func__ << ":" << __LINE__ << " command_type "
       << command_message.command_type() << endl;
  switch (command_message.command_type()) {
    case EXIT: {
      Exit();
      VtsDriverControlResponseMessage response_message;
      response_message.set_response_code(VTS_DRIVER_RESPONSE_SUCCESS);
      if (VtsSocketSendMessage(response_message)) {
        cout << getpid() << " " << __func__ << " exiting" << endl;
        return false;
      }
      break;
    }
    case LOAD_HAL: {
      int32_t result = LoadHal(
          command_message.file_path(), command_message.target_class(),
          command_message.target_type(), command_message.target_version(),
          command_message.target_package(),
          command_message.target_component_name(),
          command_message.hw_binder_service_name(),
          command_message.module_name());
      VtsDriverControlResponseMessage response_message;
      response_message.set_response_code(VTS_DRIVER_RESPONSE_SUCCESS);
      response_message.set_return_value(result);
      if (VtsSocketSendMessage(response_message)) return true;
      break;
    }
    case GET_STATUS: {
      int32_t result = Status(command_message.status_type());
      VtsDriverControlResponseMessage response_message;
      response_message.set_response_code(VTS_DRIVER_RESPONSE_SUCCESS);
      response_message.set_return_value(result);
      if (VtsSocketSendMessage(response_message)) return true;
      break;
    }
    case CALL_FUNCTION: {
      if (command_message.has_driver_caller_uid()) {
        setuid(atoi(command_message.driver_caller_uid().c_str()));
      }
      const char* result = Call(command_message.arg());
      VtsDriverControlResponseMessage response_message;
      response_message.set_response_code(VTS_DRIVER_RESPONSE_SUCCESS);
      response_message.set_return_message(result);
      if (VtsSocketSendMessage(response_message)) return true;
      break;
    }
    case VTS_DRIVER_COMMAND_READ_SPECIFICATION: {
      const char* result = ReadSpecification(
          command_message.module_name(),
          command_message.target_class(),
          command_message.target_type(),
          command_message.target_version(),
          command_message.target_package());
      VtsDriverControlResponseMessage response_message;
      response_message.set_response_code(VTS_DRIVER_RESPONSE_SUCCESS);
      response_message.set_return_message(result);
      if (VtsSocketSendMessage(response_message)) return true;
      break;
    }
    case GET_ATTRIBUTE: {
      const char* result = GetAttribute(command_message.arg());
      VtsDriverControlResponseMessage response_message;
      response_message.set_response_code(VTS_DRIVER_RESPONSE_SUCCESS);
      response_message.set_return_message(result);
      if (VtsSocketSendMessage(response_message)) return true;
      break;
    }
    case LIST_FUNCTIONS: {
      string result = ListFunctions();
      VtsDriverControlResponseMessage response_message;
      if (result.size() > 0) {
        response_message.set_response_code(VTS_DRIVER_RESPONSE_SUCCESS);
        response_message.set_return_message(result.c_str());
      } else {
        response_message.set_response_code(VTS_DRIVER_RESPONSE_FAIL);
      }
      if (VtsSocketSendMessage(response_message)) return true;
      break;
    }
    default:
      break;
  }
  cerr << __func__ << " failed." << endl;
  return false;
}

// Starts to run a UNIX socket server (foreground).
int StartSocketServer(const string& socket_port_file,
                      android::vts::SpecificationBuilder& spec_builder,
                      const char* lib_path) {
  int sockfd;
  socklen_t clilen;
  struct sockaddr_in cli_addr;
  struct sockaddr_un serv_addr;

  sockfd = socket(PF_UNIX, SOCK_STREAM, 0);
  if (sockfd < 0) {
    cerr << "Can't open the socket." << endl;
    return -1;
  }

  unlink(socket_port_file.c_str());
  bzero((char*)&serv_addr, sizeof(serv_addr));
  serv_addr.sun_family = AF_UNIX;
  strcpy(serv_addr.sun_path, socket_port_file.c_str());

  cout << "[driver:hal] trying to bind (port file: " << socket_port_file
       << ")" << endl;

  if (::bind(sockfd, (struct sockaddr*)&serv_addr, sizeof(serv_addr)) == -1) {
    int error_save = errno;
    cerr << getpid() << " " << __func__
         << " ERROR binding failed. errno = " << error_save << " "
         << strerror(error_save) << endl;
    return -1;
  }

  listen(sockfd, 5);
  clilen = sizeof(cli_addr);

  while (true) {
    cout << "[driver:hal] waiting for a new connection from the agent" << endl;
    int newsockfd = ::accept(sockfd, (struct sockaddr*)&cli_addr, &clilen);
    if (newsockfd < 0) {
      cerr << __func__ << " ERROR accept failed." << endl;
      return -1;
    }

    cout << "New session" << endl;
    pid_t pid = fork();
    if (pid == 0) {  // child
      close(sockfd);
      cout << "[driver:hal] process for an agent - pid = " << getpid() << endl;
      VtsDriverHalSocketServer* server =
          new VtsDriverHalSocketServer(spec_builder, lib_path);
      server->SetSockfd(newsockfd);
      while (server->ProcessOneCommand())
        ;
      delete server;
      exit(0);
    } else if (pid < 0) {
      cerr << "can't fork a child process to handle a session." << endl;
      return -1;
    }
    close(newsockfd);
  }
  cerr << "[driver] exiting" << endl;
  return 0;
}

}  // namespace vts
}  // namespace android

#endif
