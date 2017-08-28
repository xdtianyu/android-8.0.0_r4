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

import logging
import random

from vts.runners.host import base_test
from vts.runners.host import test_runner
from vts.utils.python.controllers import android_device
from vts.utils.python.fuzzer import GenePool


class LightFuzzTest(base_test.BaseTestClass):
    """A sample fuzz testcase for the legacy lights HAL."""

    def setUpClass(self):
        required_params = ["gene_pool_size", "iteartion_count"]
        self.getUserParams(required_params)
        self.dut = self.registerController(android_device)[0]
        self.dut.hal.InitConventionalHal(target_type="light",
                                         target_basepaths=["/system/lib64/hw"],
                                         target_version=1.0,
                                         bits=64,
                                         target_package="hal.conventional.light")
        self.dut.hal.light.OpenConventionalHal("backlight")
        module_name = random.choice(
            [self.dut.hal.light.LIGHT_ID_BACKLIGHT,
             self.dut.hal.light.LIGHT_ID_NOTIFICATIONS,
             self.dut.hal.light.LIGHT_ID_ATTENTION])

        # TODO: broken on bullhead
        #   self.dut.hal.light.LIGHT_ID_KEYBOARD
        #   self.dut.hal.light.LIGHT_ID_BUTTONS
        #   self.dut.hal.light.LIGHT_ID_BATTERY
        #   self.dut.hal.light.LIGHT_ID_BLUETOOTH
        #   self.dut.hal.light.LIGHT_ID_WIFI

    def testTurnOnLightBlackBoxFuzzing(self):
        """A fuzz testcase which calls a function using different values."""
        logging.info("blackbox fuzzing")
        genes = GenePool.CreateGenePool(
            self.gene_pool_size,
            self.dut.hal.light.light_state_t,
            self.dut.hal.light.light_state_t_fuzz,
            color=0xffffff00,
            flashMode=self.dut.hal.light.LIGHT_FLASH_HARDWARE,
            flashOnMs=100,
            flashOffMs=200,
            brightnessMode=self.dut.hal.light.BRIGHTNESS_MODE_USER)
        for iteration in range(self.iteartion_count):
            index = 0
            logging.info("blackbox iteration %d", iteration)
            for gene in genes:
                logging.debug("Gene %d", index)
                self.dut.hal.light.set_light(None, gene)
                index += 1
            evolution = GenePool.Evolution()
            genes = evolution.Evolve(genes,
                                     self.dut.hal.light.light_state_t_fuzz)

    def testTurnOnLightWhiteBoxFuzzing(self):
        """A fuzz testcase which calls a function using different values."""
        logging.info("whitebox fuzzing")
        genes = GenePool.CreateGenePool(
            self.gene_pool_size,
            self.dut.hal.light.light_state_t,
            self.dut.hal.light.light_state_t_fuzz,
            color=0xffffff00,
            flashMode=self.dut.hal.light.LIGHT_FLASH_HARDWARE,
            flashOnMs=100,
            flashOffMs=200,
            brightnessMode=self.dut.hal.light.BRIGHTNESS_MODE_USER)

        for iteration in range(self.iteartion_count):
            index = 0
            logging.info("whitebox iteration %d", iteration)
            coverages = []
            for gene in genes:
                logging.debug("Gene %d", index)
                result = self.dut.hal.light.set_light(None, gene)
                if len(result.processed_coverage_data) > 0:
                    logging.info("coverage: %s", result.processed_coverage_data)
                    gene_coverage = []
                    for coverage_data in result.processed_coverage_data:
                        gene_coverage.append(coverage_data)
                    coverages.append(gene_coverage)
                index += 1
            evolution = GenePool.Evolution()
            genes = evolution.Evolve(genes,
                                     self.dut.hal.light.light_state_t_fuzz,
                                     coverages=coverages)


if __name__ == "__main__":
    test_runner.main()
