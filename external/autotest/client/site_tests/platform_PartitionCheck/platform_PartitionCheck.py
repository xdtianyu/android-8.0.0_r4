# Copyright (c) 2014 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging
import os

from autotest_lib.client.bin import site_utils, test, utils
from autotest_lib.client.common_lib import error

ROOTFS_SIZE = 2 * 1024 * 1024 * 1024

class platform_PartitionCheck(test.test):
    """
    Verify partition size is correct.
    """
    version = 1

    def get_block_size(self, device):
        """
        Check the block size of a block device.

        Args:
            @param device: string, name of the block device.

        Returns:
            int, size of block in bytes.
        """

        # Construct a pathname to find the logic block size for this device
        sysfs_path = os.path.join('/sys', 'block', device,
                                  'queue', 'logical_block_size')

        return int(utils.read_one_line(sysfs_path))

    def get_partition_size(self, device, partition):
        """
        Get the number of blocks in the partition.

        Args:
            @param device: string, name of the block device.
            @param partition: string, partition name

        Returns:
            int, number of blocks
        """

        part_file = os.path.join('/sys', 'block', device, partition, 'size')
        part_blocks = int(utils.read_one_line(part_file))
        return part_blocks

    def run_once(self):
        errors = []
        device = os.path.basename(site_utils.get_fixed_dst_drive())
        mmcpath = os.path.join('/sys', 'block', device)

        if os.path.exists(mmcpath) and device.startswith('mmc'):
            partitions = [device + 'p3', device + 'p5']
        else:
            partitions = [device + '3', device + '5']

        block_size = self.get_block_size(device)

        for p in partitions:
            pblocks = self.get_partition_size(device, p)
            psize = pblocks * block_size
            if psize != ROOTFS_SIZE:
                errmsg = ('%s is %d bytes, expected %d' %
                          (p, psize, ROOTFS_SIZE))
                logging.warning(errmsg)
                errors.append(errmsg)

        # If self.error is not zero, there were errors.
        if errors:
            raise error.TestFail('There were %d partition errors: %s' %
                                 (len(errors), ': '.join(errors)))
