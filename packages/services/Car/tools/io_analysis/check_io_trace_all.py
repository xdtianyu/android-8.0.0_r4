#!/usr/bin/env python
#
# Copyright (C) 2016 The Android Open Source Project
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
"""Analyze block trace"""

import collections
import os
import re
import string
import sys

RE_BLOCK = r'.+-([0-9]+).*\s+([0-9]+\.[0-9]+):\s+block_bio_queue:\s+([0-9]+)\,([0-9]+)\s+([RW]\S*)\s+([0-9]+)\s+\+\s+([0-9]+)\s+\[([^\]]+)'

# dev_num = major * MULTIPLIER + minor
DEV_MAJOR_MULTIPLIER = 1000

# dm access is remapped to disk access. So account differently
DM_MAJOR = 253

MAX_PROCESS_DUMP = 10

class RwEvent:
  def __init__(self, block_num, start_time, size):
    self.block_num = block_num
    self.start_time = start_time
    self.size = size

def get_string_pos(strings, string_to_find):
  for i, s in enumerate(strings):
    if s == string_to_find:
      return i
  return -1

class ProcessData:
  def __init__(self, name):
    self.name = name
    self.reads = {} # k : dev_num, v : [] of reads
    self.per_device_total_reads = {}
    self.writes = {}
    self.per_device_total_writes = {}
    self.total_reads = 0
    self.total_writes = 0
    self.total_dm_reads = 0
    self.total_dm_writes = 0

  def add_read_event(self, major, minor, event):
    devNum = major * DEV_MAJOR_MULTIPLIER + minor;
    events = self.reads.get(devNum)
    if not events:
      events = []
      self.reads[devNum] = events
      self.per_device_total_reads[devNum] = 0
    events.append(event)
    self.total_reads += event.size
    self.per_device_total_reads[devNum] += event.size

  def add_write_event(self, major, minor, event):
    devNum = major * DEV_MAJOR_MULTIPLIER + minor;
    events = self.writes.get(devNum)
    if not events:
      events = []
      self.writes[devNum] = events
      self.per_device_total_writes[devNum] = 0
    events.append(event)
    self.total_writes += event.size
    self.per_device_total_writes[devNum] += event.size

  def add_dm_read(self, size):
    self.total_dm_reads += size

  def add_dm_write(self, size):
    self.total_dm_writes += size

  def dump(self):
    print "Process,", self.name
    print " total reads,", self.total_reads
    print " total writes,", self.total_writes
    print " total dm reads,", self.total_dm_reads
    print " total dm writes,", self.total_dm_writes
    print " R per device"
    sorted_r = collections.OrderedDict(sorted(self.per_device_total_reads.items(), \
      key = lambda item: item[1], reverse = True))
    for i in range(len(sorted_r)):
      dev = sorted_r.popitem(last=False)
      print " ", dev[0],dev[1]

    print " W per device"
    sorted_w = collections.OrderedDict(sorted(self.per_device_total_writes.items(), \
      key = lambda item: item[1], reverse = True))
    for i in range(len(sorted_w)):
      dev = sorted_w.popitem(last=False)
      print " ", dev[0],dev[1]

class Trace:

  def __init__(self):
    self.ios = {} #K: process name, v:ProcessData
    self.total_reads = 0
    self.total_writes = 0
    self.total_reads_per_device = {} #K: block num, V: total blocks
    self.total_writes_per_device = {}
    self.total_dm_reads = {} #K: devnum, V: blocks
    self.total_dm_writes = {}

  def parse_bio_queue(self, l, match):
    pid = match.group(1)
    start_time = int(float(match.group(2))*1000000) #us
    major = int(match.group(3))
    minor =  int(match.group(4))
    devNum = major * DEV_MAJOR_MULTIPLIER + minor;
    operation =  match.group(5)
    block_num = int(match.group(6))
    size = int(match.group(7))
    process = match.group(8) + "-" + pid
    event = RwEvent(block_num, start_time, size)
    io = self.ios.get(process)
    if not io:
      io = ProcessData(process)
      self.ios[process] = io
    if major == DM_MAJOR:
      devNum = major * DEV_MAJOR_MULTIPLIER + minor;
      if operation[0] == 'R':
        if devNum not in self.total_dm_reads:
          self.total_dm_reads[devNum] = 0
        self.total_dm_reads[devNum] += size
        io.add_dm_read(size)
      elif operation[0] == 'W':
        if devNum not in self.total_dm_writes:
          self.total_dm_writes[devNum] = 0
        self.total_dm_writes[devNum] += size
        io.add_dm_write(size)
      return
    if operation[0] == 'R':
      io.add_read_event(major, minor, event)
      self.total_reads += size
      per_device = self.total_reads_per_device.get(devNum)
      if not per_device:
        self.total_reads_per_device[devNum] = 0
      self.total_reads_per_device[devNum] += size
    elif operation[0] == 'W':
      io.add_write_event(major, minor, event)
      self.total_writes += size
      per_device = self.total_writes_per_device.get(devNum)
      if not per_device:
        self.total_writes_per_device[devNum] = 0
      self.total_writes_per_device[devNum] += size

  def parse_block_trace(self, l, match):
    try:
      self.parse_bio_queue(l, match)
    except ValueError:
      print "cannot parse:", l
      raise

  def dump(self):
    print "total read blocks,", self.total_reads
    print "total write blocks,", self.total_writes
    print "Total DM R"
    for dev,size in self.total_dm_reads.items():
      print dev, size
    print "Total DM W"
    for dev,size in self.total_dm_writes.items():
      print dev, size
    print "**Process total R/W"
    sorted_by_total_rw = collections.OrderedDict(sorted(self.ios.items(), \
      key = lambda item: item[1].total_reads + item[1].total_writes, reverse = True))
    for i in range(MAX_PROCESS_DUMP):
      process = sorted_by_total_rw.popitem(last=False)
      if not process:
        break
      process[1].dump()

    print "**Process total W"
    sorted_by_total_w = collections.OrderedDict(sorted(self.ios.items(), \
      key = lambda item: item[1].total_writes, reverse = True))
    for i in range(5):
      process = sorted_by_total_w.popitem(last=False)
      if not process:
        break
      process[1].dump()

    print "**Device total R"
    sorted_by_total_r = collections.OrderedDict(sorted(self.total_reads_per_device.items(), \
      key = lambda item: item[1], reverse = True))
    for i in range(len(sorted_by_total_r)):
      dev = sorted_by_total_r.popitem(last=False)
      print dev[0],dev[1]

    print "**Device total W"
    sorted_by_total_w = collections.OrderedDict(sorted(self.total_writes_per_device.items(), \
      key = lambda item: item[1], reverse = True))
    for i in range(len(sorted_by_total_w)):
      dev = sorted_by_total_w.popitem(last=False)
      print dev[0],dev[1]

def main(argv):
  if (len(argv) < 2):
    print "check_io_trace_all.py filename"
    return
  filename = argv[1]

  trace = Trace()
  prog = re.compile(RE_BLOCK)
  with open(filename) as f:
    for l in f:
      result = prog.match(l)
      if result:
        trace.parse_block_trace(l, result)
  trace.dump()

if __name__ == '__main__':
  main(sys.argv)
