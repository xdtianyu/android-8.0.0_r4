#!/bin/bash
#
# Copyright 2016 Google Inc. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Only tested on linux so far
echo "Install Python SDK"
sudo apt-get -y install python-dev

echo "Install Protocol Buffer packages"
sudo apt-get -y install python-protobuf
sudo apt-get -y install protobuf-compiler

echo "Install Python virtualenv and pip tools for VTS TradeFed and Runner"
sudo apt-get -y install python-setuptools
sudo apt-get -y install python-pip
sudo apt-get -y install python3-pip
sudo apt-get -y install python-virtualenv

echo "Install Python modules for VTS Runner"
sudo pip install future
sudo pip install futures
sudo pip install enum
sudo pip install protobuf
sudo pip install setuptools
sudo pip install requests
sudo pip install httplib2
sudo pip install oauth2client
sudo pip install google-api-python-client
sudo pip install google-cloud-pubsub

echo "Install packages for Kernel tests"
sudo pip install parse
sudo pip install ply

echo "Install packages for Camera ITS tests"
sudo apt-get -y install python-tk
sudo pip install numpy
sudo pip install scipy
sudo pip install matplotlib
sudo apt-get -y install libjpeg-dev
sudo apt-get -y install libtiff-dev
sudo pip install Pillow
