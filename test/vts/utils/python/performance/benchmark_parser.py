#
# Copyright (C) 2017 The Android Open Source Project
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

import json


class GoogleBenchmarkJsonParser(object):
    """This class parses the JSON output of Google benchmark.

    Example
    {
      "context": {
        "date": "2017-05-16 11:57:21",
        "num_cpus": 4,
        "mhz_per_cpu": 19,
        "cpu_scaling_enabled": true,
        "library_build_type": "release"
      },
      "benchmarks": [
        {
          "name": "BM_sendVec_binder/4",
          "iterations": 27744,
          "real_time": 51485,
          "cpu_time": 23655,
          "time_unit": "ns"
        },
        ...
      ]
    }

    Attributes:
        _benchmarks: The "benchmarks" property of the JSON object.
    """

    _BENCHMARKS = "benchmarks"
    _NAME = "name"
    _REAL_TIME = "real_time"

    def __init__(self, json_string):
        """Converts the JSON string to internal data structure.

        Args:
            json_string: The output of Google benchmark in JSON format.
        """
        json_obj = json.loads(json_string)
        self._benchmarks = json_obj[self._BENCHMARKS]

    def getArguments(self):
        """Returns the "name" properties with function names stripped.

        Returns:
            A list of strings.
        """
        args = []
        for bm in self._benchmarks:
            name = bm[self._NAME].split("/", 1)
            args.append(name[1].encode("utf-8") if len(name) >= 2 else "")
        return args

    def getRealTime(self):
        """Returns the "real_time" properties.

        Returns:
            A list of integers.
        """
        return [x[self._REAL_TIME] for x in self._benchmarks]
