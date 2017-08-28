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
"""Analyze ext4 trace with custom open trace"""
import collections
import math
import os
import re
import string
import sys

DBG = False

# hard coded maps to detect partition for given device or the other way around
# this can be different per each device. This works for marlin.
DEVICE_TO_PARTITION = { "253,0": "/system/", "253,1": "/vendor/", "259,19": "/data/" }
PARTITION_TO_DEVICE = {}
for key, value in DEVICE_TO_PARTITION.iteritems():
  PARTITION_TO_DEVICE[value] = key

RE_DO_SYS_OPEN = r""".+\s+([0-9]+\.[0-9]+):\s+do_sys_open:\s+(\S+):\sopen..(\S+).,\s([0-9]+).\s+.+inode\s=\s([0-9]+)"""
RE_EXT4_MA_BLOCKS_ENTER = r"""\s+(\S+)-([0-9]+).+\s+([0-9]+\.[0-9]+):\s+ext4_ext_map_blocks_enter:\s+dev\s+(\S+)\s+ino\s+([0-9]+)\s+lblk\s+([0-9]+)\s+len\s+([0-9]+)"""

class FileEvent:
  def __init__(self, open_time, file_name, process_name, inode, flags):
    self.file_name = file_name
    self.inode = inode
    self.processes = []
    self.processes.append((open_time, process_name, flags))
    self.reads = []
    self.total_reads = 0
    self.total_open = 1
    self.blocks = {}
    self.total_rereads = 0
    self.read_size_histogram = {} #key: read size, value: occurrence
    self.single_block_reads = {} # process name, occurrence

  def add_open(self, open_time, process_name, flags):
    self.processes.append((open_time, process_name, flags))
    self.total_open += 1

  def add_read(self, time, offset, size, process_name):
    self.reads.append((time, offset, size, process_name))
    self.total_reads += size
    for i in range(offset, offset + size):
      if not self.blocks.get(i):
        self.blocks[i] = 1
      else:
        self.blocks[i] += 1
        self.total_rereads += 1
    if not self.read_size_histogram.get(size):
      self.read_size_histogram[size] = 1
    else:
      self.read_size_histogram[size] += 1
    if size == 1:
      if not self.single_block_reads.get(process_name):
        self.single_block_reads[process_name] = 1
      else:
        self.single_block_reads[process_name] += 1

  def dump(self):
    print " filename %s, total reads %d, total open %d total rereads %d inode %s" \
      % (self.file_name, self.total_reads, self.total_open, self.total_rereads, self.inode)
    process_names = []
    for opener in self.processes:
      process_names.append(opener[1])
    print "  Processes opened this file:", ','.join(process_names)
    if len(self.read_size_histogram) > 1:
      print "  Read size histograms:", collections.OrderedDict( \
        sorted(self.read_size_histogram.items(), key = lambda item: item[0]))
    if len(self.single_block_reads) > 1 and len(self.reads) > 1:
      print "  Single block reads:", collections.OrderedDict( \
        sorted(self.single_block_reads.items(), key = lambda item: item[1], reverse = True))

class Trace:
  def __init__(self):
    self.files_per_device = {} # key: device, value: { key: inode, value; FileEvent }
    self.re_open = re.compile(RE_DO_SYS_OPEN)
    self.re_read = re.compile(RE_EXT4_MA_BLOCKS_ENTER)

  def handle_line(self, line):
    match = self.re_open.match(line)
    if match:
      self.handle_open(match)
      return
    match = self.re_read.match(line)
    if match:
      self.handle_read(match)
      return

  def handle_open(self, match):
    time = match.group(1)
    process_name = match.group(2)
    file_name = match.group(3)
    flag = match.group(4)
    inode = match.group(5)
    dev_name = None
    for p in PARTITION_TO_DEVICE:
      if file_name.startswith(p):
        dev_name = PARTITION_TO_DEVICE[p]
    if not dev_name:
      if DBG:
        print "Ignore open for file", file_name
      return
    files = self.files_per_device[dev_name]
    fevent = files.get(inode)
    if not fevent:
      fevent = FileEvent(time, file_name, process_name, inode, flag)
      files[inode] = fevent
    else:
      fevent.add_open(time, process_name, flag)

  def handle_read(self, match):
    process_name = match.group(1)
    pid = match.group(2)
    time = match.group(3)
    dev = match.group(4)
    inode = match.group(5)
    offset = int(match.group(6))
    size = int(match.group(7))
    files = self.files_per_device.get(dev)
    if not files:
      if DEVICE_TO_PARTITION.get(dev):
        files = {}
        self.files_per_device[dev] = files
      else:
        if DBG:
          print "read ignored for device", dev
        return
    fevent = files.get(inode)
    if not fevent:
      if DBG:
        print 'no open for device %s with inode %s' % (dev, inode)
      fevent = FileEvent(time, "unknown", process_name, inode, 0)
      files[inode] = fevent
    fevent.add_read(time, offset, size, process_name + "-" + pid)


  def dump_partition(self, partition_name, files):
    print "**Dump partition:", partition_name, "toal number of files:", len(files)
    total_reads = 0
    total_rereads = 0
    vs = files.values()
    vs.sort(key=lambda f : f.total_reads, reverse = True)
    for f in vs:
      f.dump()
      total_reads += f.total_reads
      total_rereads += f.total_rereads
    print " Total reads for partition", total_reads, "rereads", total_rereads
    return total_reads, total_rereads, len(files)


  def dump(self):
    print "Dump read per each partition"
    total_reads = 0
    total_rereads = 0
    summaries = []
    for d in self.files_per_device:
      reads, rereads, num_files = self.dump_partition(DEVICE_TO_PARTITION[d], \
        self.files_per_device[d])
      total_reads += reads
      total_rereads += rereads
      summaries.append((DEVICE_TO_PARTITION[d], reads, rereads, num_files))
    print "**Summary**"
    print "Total blocks read", total_reads, "reread", total_rereads
    print "Partition total_reads total_rereads num_files"
    for s in summaries:
      print s[0], s[1], s[2], s[3]

def main(argv):
  if (len(argv) < 2):
    print "check_fule_read.py filename"
    return
  filename = argv[1]
  trace = Trace()
  with open(filename) as f:
    for l in f:
      trace.handle_line(l)
  trace.dump()

if __name__ == '__main__':
  main(sys.argv)
