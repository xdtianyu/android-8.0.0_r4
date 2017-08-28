#!/usr/bin/env python
#
# Copyright (C) 2010,2016 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# Usage:
#  Serve the docs build directory on localhost:8080 (default):
#  $ ./docs/source.android.com/scripts/micro-httpd.py
#  Serve a different output directory (appends target/common/docs/online-sac):
#  $ OUT_DIR=/path/to/out ./docs/source.android.com/scripts/micro-httpd.py
#  Serve using a different port on localhost:
#  $ HTTP_PORT=9090 ./docs/source.android.com/scripts/micro-httpd.py

import SimpleHTTPServer
import SocketServer
import os
import sys
import socket

PORT = int(os.environ.get('HTTP_PORT', 8080))
OUT_DIR = os.environ.get('OUT_DIR', os.path.join(os.path.dirname(__file__),
                                                 '../../../out'))

out_dir_sac = os.path.abspath(os.path.join(OUT_DIR,
                                           'target/common/docs/online-sac'))

if not os.path.isdir(out_dir_sac):
    sys.exit("Error: Docs build directory doesn't exist: {0}".format(out_dir_sac))

Handler = SimpleHTTPServer.SimpleHTTPRequestHandler

try:
    httpd = SocketServer.TCPServer(('0.0.0.0', PORT), Handler)
    httpd.allow_reuse_address = True
except socket.error as sockerr:
    sys.exit("Error: Address already in use. Kill blocking process (or wait a moment)")

print("Serving directory: {0}".format(out_dir_sac))
print("Web server address: http://{0}:{1}".format(socket.gethostname(), PORT))

os.chdir(out_dir_sac)
httpd.serve_forever()
