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
import time

from vts.runners.host import asserts
from vts.runners.host import base_test
from vts.runners.host import const
from vts.runners.host import keys
from vts.runners.host import test_runner
from vts.utils.python.controllers import android_device
from vts.utils.python.precondition import precondition_utils


class VtsHalAutomotiveVehicleV2_0HostTest(base_test.BaseTestClass):
    """A simple testcase for the VEHICLE HIDL HAL."""

    def setUpClass(self):
        """Creates a mirror and init vehicle hal."""
        self.dut = self.registerController(android_device)[0]

        self.dut.shell.InvokeTerminal("one")
        self.dut.shell.one.Execute("setenforce 0")  # SELinux permissive mode
        if not precondition_utils.CanRunHidlHalTest(
            self, self.dut, self.dut.shell.one):
            self._skip_all_testcases = True
            return

        results = self.dut.shell.one.Execute("id -u system")
        system_uid = results[const.STDOUT][0].strip()
        logging.info("system_uid: %s", system_uid)

        if self.coverage.enabled:
            self.coverage.LoadArtifacts()
            self.coverage.InitializeDeviceCoverage(self.dut)

        if self.profiling.enabled:
            self.profiling.EnableVTSProfiling(self.dut.shell.one)

        self.dut.hal.InitHidlHal(
            target_type="vehicle",
            target_basepaths=self.dut.libPaths,
            target_version=2.0,
            target_package="android.hardware.automotive.vehicle",
            target_component_name="IVehicle",
            bits=int(self.abi_bitness))

        self.vehicle = self.dut.hal.vehicle  # shortcut
        self.vehicle.SetCallerUid(system_uid)
        self.vtypes = self.dut.hal.vehicle.GetHidlTypeInterface("types")
        logging.info("vehicle types: %s", self.vtypes)
        asserts.assertEqual(0x00ff0000, self.vtypes.VehiclePropertyType.MASK)
        asserts.assertEqual(0x0f000000, self.vtypes.VehicleArea.MASK)

    def tearDownClass(self):
        """Disables the profiling.

        If profiling is enabled for the test, collect the profiling data
        and disable profiling after the test is done.
        """
        if self._skip_all_testcases:
            return

        if self.profiling.enabled:
            self.profiling.ProcessTraceDataForTestCase(self.dut)
            self.profiling.ProcessAndUploadTraceData()

        if self.coverage.enabled:
            self.coverage.SetCoverageData(dut=self.dut, isGlobal=True)

    def setUp(self):
        self.propToConfig = {}
        for config in self.vehicle.getAllPropConfigs():
            self.propToConfig[config['prop']] = config
        self.configList = self.propToConfig.values()

    def testListProperties(self):
        """Checks whether some PropConfigs are returned.

        Verifies that call to getAllPropConfigs is not failing and
        it returns at least 1 vehicle property config.
        """
        logging.info("all supported properties: %s", self.configList)
        asserts.assertLess(0, len(self.configList))

    def testMandatoryProperties(self):
        """Verifies that all mandatory properties are supported."""
        # 1 property so far
        mandatoryProps = set([self.vtypes.VehicleProperty.DRIVING_STATUS])
        logging.info(self.vtypes.VehicleProperty.DRIVING_STATUS)

        for config in self.configList:
            mandatoryProps.discard(config['prop'])

        asserts.assertEqual(0, len(mandatoryProps))

    def emptyValueProperty(self, propertyId, areaId=0):
        """Creates a property structure for use with the Vehicle HAL.

        Args:
            propertyId: the numeric identifier of the output property.
            areaId: the numeric identifier of the vehicle area of the output
                    property. 0, or omitted, for global.

        Returns:
            a property structure for use with the Vehicle HAL.
        """
        return {
            'prop' : propertyId,
            'timestamp' : 0,
            'areaId' : areaId,
            'value' : {
                'int32Values' : [],
                'floatValues' : [],
                'int64Values' : [],
                'bytes' : [],
                'stringValue' : ""
            }
        }

    def readVhalProperty(self, propertyId, areaId=0):
        """Reads a specified property from Vehicle HAL.

        Args:
            propertyId: the numeric identifier of the property to be read.
            areaId: the numeric identifier of the vehicle area to retrieve the
                    property for. 0, or omitted, for global.

        Returns:
            the value of the property as read from Vehicle HAL, or None
            if it could not read successfully.
        """
        vp = self.vtypes.Py2Pb("VehiclePropValue",
                               self.emptyValueProperty(propertyId, areaId))
        logging.info("0x%x get request: %s", propertyId, vp)
        status, value = self.vehicle.get(vp)
        logging.info("0x%x get response: %s, %s", propertyId, status, value)
        if self.vtypes.StatusCode.OK == status:
            return value
        else:
            logging.warning("attempt to read property 0x%x returned error %d",
                            propertyId, status)

    def setVhalProperty(self, propertyId, value, areaId=0,
                        expectedStatus=0):
        """Sets a specified property in the Vehicle HAL.

        Args:
            propertyId: the numeric identifier of the property to be set.
            value: the value of the property, formatted as per the Vehicle HAL
                   (use emptyValueProperty() as a helper).
            areaId: the numeric identifier of the vehicle area to set the
                    property for. 0, or omitted, for global.
            expectedStatus: the StatusCode expected to be returned from setting
                    the property. 0, or omitted, for OK.
        """
        propValue = self.emptyValueProperty(propertyId, areaId)
        for k in propValue["value"]:
            if k in value:
                if k == "stringValue":
                    propValue["value"][k] += value[k]
                else:
                    propValue["value"][k].extend(value[k])
        vp = self.vtypes.Py2Pb("VehiclePropValue", propValue)
        logging.info("0x%x set request: %s", propertyId, vp)
        status = self.vehicle.set(vp)
        logging.info("0x%x set response: %s", propertyId, status)
        if 0 == expectedStatus:
            expectedStatus = self.vtypes.StatusCode.OK
        asserts.assertEqual(expectedStatus, status, "Prop 0x%x" % propertyId)

    def setAndVerifyIntProperty(self, propertyId, value, areaId=0):
        """Sets a integer property in the Vehicle HAL and reads it back.

        Args:
            propertyId: the numeric identifier of the property to be set.
            value: the int32 value of the property to be set.
            areaId: the numeric identifier of the vehicle area to set the
                    property for. 0, or omitted, for global.
        """
        self.setVhalProperty(propertyId, {"int32Values" : [value]}, areaId=areaId)

        propValue = self.readVhalProperty(propertyId, areaId=areaId)
        asserts.assertEqual(1, len(propValue["value"]["int32Values"]))
        asserts.assertEqual(value, propValue["value"]["int32Values"][0])

    def testObd2SensorProperties(self):
        """Test reading the live and freeze OBD2 frame properties.

        OBD2 (On-Board Diagnostics 2) is the industry standard protocol
        for retrieving diagnostic sensor information from vehicles.
        """
        class CheckRead(object):
            """This class wraps the logic of an actual property read.

            Attributes:
                testobject: the test case this object is used on behalf of.
                propertyId: the identifier of the Vehiche HAL property to read.
                name: the engineer-readable name of this test operation.
            """

            def __init__(self, testobject, propertyId, name):
                self.testobject = testobject
                self.propertyId = propertyId
                self.name = name

            def onReadSuccess(self, propValue):
                """Override this to perform any post-read validation.

                Args:
                    propValue: the property value obtained from Vehicle HAL.
                """
                pass

            def __call__(self):
                """Reads the specified property and validates the result."""
                propValue = self.testobject.readVhalProperty(self.propertyId)
                asserts.assertNotEqual(propValue, None,
                                       msg="reading %s should not return None" %
                                       self.name)
                logging.info("%s = %s", self.name, propValue)
                self.onReadSuccess(propValue)
                logging.info("%s pass" % self.name)

        def checkLiveFrameRead():
            """Validates reading the OBD2_LIVE_FRAME (if available)."""
            checker = CheckRead(self,
                                self.vtypes.VehicleProperty.OBD2_LIVE_FRAME,
                                "OBD2_LIVE_FRAME")
            checker()

        def checkFreezeFrameRead():
            """Validates reading the OBD2_FREEZE_FRAME (if available)."""
            checker = CheckRead(self,
                                self.vtypes.VehicleProperty.OBD2_FREEZE_FRAME,
                                "OBD2_FREEZE_FRAME")
            checker()

        isLiveSupported = self.vtypes.VehicleProperty.OBD2_LIVE_FRAME in self.propToConfig
        isFreezeSupported = self.vtypes.VehicleProperty.OBD2_FREEZE_FRAME in self.propToConfig
        logging.info("isLiveSupported = %s, isFreezeSupported = %s",
                     isLiveSupported, isFreezeSupported)
        if isLiveSupported:
            checkLiveFrameRead()
        if isFreezeSupported:
            checkFreezeFrameRead()

    def testDrivingStatus(self):
        """Checks that DRIVING_STATUS property returns correct result."""
        propValue = self.readVhalProperty(
            self.vtypes.VehicleProperty.DRIVING_STATUS)
        asserts.assertEqual(1, len(propValue["value"]["int32Values"]))
        drivingStatus = propValue["value"]["int32Values"][0]

        allStatuses = (self.vtypes.VehicleDrivingStatus.UNRESTRICTED
                       | self.vtypes.VehicleDrivingStatus.NO_VIDEO
                       | self.vtypes.VehicleDrivingStatus.NO_KEYBOARD_INPUT
                       | self.vtypes.VehicleDrivingStatus.NO_VOICE_INPUT
                       | self.vtypes.VehicleDrivingStatus.NO_CONFIG
                       | self.vtypes.VehicleDrivingStatus.LIMIT_MESSAGE_LEN)

        asserts.assertEqual(allStatuses, allStatuses | drivingStatus)

    def extractZonesAsList(self, supportedAreas):
        """Converts bitwise area flags to list of zones"""
        allZones = [
            self.vtypes.VehicleAreaZone.ROW_1_LEFT,
            self.vtypes.VehicleAreaZone.ROW_1_CENTER,
            self.vtypes.VehicleAreaZone.ROW_1_RIGHT,
            self.vtypes.VehicleAreaZone.ROW_1,
            self.vtypes.VehicleAreaZone.ROW_2_LEFT,
            self.vtypes.VehicleAreaZone.ROW_2_CENTER,
            self.vtypes.VehicleAreaZone.ROW_2_RIGHT,
            self.vtypes.VehicleAreaZone.ROW_2,
            self.vtypes.VehicleAreaZone.ROW_3_LEFT,
            self.vtypes.VehicleAreaZone.ROW_3_CENTER,
            self.vtypes.VehicleAreaZone.ROW_3_RIGHT,
            self.vtypes.VehicleAreaZone.ROW_3,
            self.vtypes.VehicleAreaZone.ROW_4_LEFT,
            self.vtypes.VehicleAreaZone.ROW_4_CENTER,
            self.vtypes.VehicleAreaZone.ROW_4_RIGHT,
            self.vtypes.VehicleAreaZone.ROW_4,
            self.vtypes.VehicleAreaZone.WHOLE_CABIN,
        ]

        extractedZones = []
        for zone in allZones:
            if (zone & supportedAreas == zone):
                extractedZones.append(zone)
        return extractedZones


    def testHvacPowerOn(self):
        """Test power on/off and properties associated with it.

        Gets the list of properties that are affected by the HVAC power state
        and validates them.

        Turns power on to start in a defined state, verifies that power is on
        and properties are available.  State change from on->off and verifies
        that properties are no longer available, then state change again from
        off->on to verify properties are now available again.
        """

        # Checks that HVAC_POWER_ON property is supported and returns valid
        # result initially.
        hvacPowerOnConfig = self.propToConfig[self.vtypes.VehicleProperty.HVAC_POWER_ON]
        if hvacPowerOnConfig is None:
            logging.info("HVAC_POWER_ON not supported")
            return

        zones = self.extractZonesAsList(hvacPowerOnConfig['supportedAreas'])
        asserts.assertLess(0, len(zones), "supportedAreas for HVAC_POWER_ON property is invalid")

        # TODO(pavelm): consider to check for all zones
        zone = zones[0]

        propValue = self.readVhalProperty(
            self.vtypes.VehicleProperty.HVAC_POWER_ON, areaId=zone)

        asserts.assertEqual(1, len(propValue["value"]["int32Values"]))
        asserts.assertTrue(
            propValue["value"]["int32Values"][0] in [0, 1],
            "%d not a valid value for HVAC_POWER_ON" %
                propValue["value"]["int32Values"][0]
            )

        # Checks that HVAC_POWER_ON config string returns valid result.
        requestConfig = [self.vtypes.Py2Pb(
            "VehicleProperty", self.vtypes.VehicleProperty.HVAC_POWER_ON)]
        logging.info("HVAC power on config request: %s", requestConfig)
        responseConfig = self.vehicle.getPropConfigs(requestConfig)
        logging.info("HVAC power on config response: %s", responseConfig)
        hvacTypes = set([
            self.vtypes.VehicleProperty.HVAC_FAN_SPEED,
            self.vtypes.VehicleProperty.HVAC_FAN_DIRECTION,
            self.vtypes.VehicleProperty.HVAC_TEMPERATURE_CURRENT,
            self.vtypes.VehicleProperty.HVAC_TEMPERATURE_SET,
            self.vtypes.VehicleProperty.HVAC_DEFROSTER,
            self.vtypes.VehicleProperty.HVAC_AC_ON,
            self.vtypes.VehicleProperty.HVAC_MAX_AC_ON,
            self.vtypes.VehicleProperty.HVAC_MAX_DEFROST_ON,
            self.vtypes.VehicleProperty.HVAC_RECIRC_ON,
            self.vtypes.VehicleProperty.HVAC_DUAL_ON,
            self.vtypes.VehicleProperty.HVAC_AUTO_ON,
            self.vtypes.VehicleProperty.HVAC_ACTUAL_FAN_SPEED_RPM,
        ])
        status = responseConfig[0]
        asserts.assertEqual(self.vtypes.StatusCode.OK, status)
        configString = responseConfig[1][0]["configString"]
        configProps = []
        if configString != "":
            for prop in configString.split(","):
                configProps.append(int(prop, 16))
        for prop in configProps:
            asserts.assertTrue(prop in hvacTypes,
                               "0x%X not an HVAC type" % prop)

        # Turn power on.
        self.setAndVerifyIntProperty(
            self.vtypes.VehicleProperty.HVAC_POWER_ON, 1, areaId=zone)

        # Check that properties that require power to be on can be set.
        propVals = {}
        for prop in configProps:
            v = self.readVhalProperty(prop, areaId=zone)["value"]
            self.setVhalProperty(prop, v, areaId=zone)
            # Save the value for use later when trying to set the property when
            # HVAC is off.
            propVals[prop] = v

        # Turn power off.
        self.setAndVerifyIntProperty(
            self.vtypes.VehicleProperty.HVAC_POWER_ON, 0, areaId=zone)

        # Check that properties that require power to be on can't be set.
        for prop in configProps:
            self.setVhalProperty(
                prop, propVals[prop],
                areaId=zone,
                expectedStatus=self.vtypes.StatusCode.NOT_AVAILABLE)

        # Turn power on.
        self.setAndVerifyIntProperty(
            self.vtypes.VehicleProperty.HVAC_POWER_ON, 1, areaId=zone)

        # Check that properties that require power to be on can be set.
        for prop in configProps:
            self.setVhalProperty(prop, propVals[prop], areaId=zone)

    def testVehicleStaticProps(self):
        """Verifies that static properties are configured correctly"""
        staticProperties = set([
            self.vtypes.VehicleProperty.INFO_VIN,
            self.vtypes.VehicleProperty.INFO_MAKE,
            self.vtypes.VehicleProperty.INFO_MODEL,
            self.vtypes.VehicleProperty.INFO_MODEL_YEAR,
            self.vtypes.VehicleProperty.INFO_FUEL_CAPACITY,
            self.vtypes.VehicleProperty.HVAC_FAN_DIRECTION_AVAILABLE,
            self.vtypes.VehicleProperty.AUDIO_HW_VARIANT,
            self.vtypes.VehicleProperty.AP_POWER_BOOTUP_REASON,
        ])
        for c in self.configList:
            prop = c['prop']
            msg = "Prop 0x%x" % prop
            if (c["prop"] in staticProperties):
                asserts.assertEqual(self.vtypes.VehiclePropertyChangeMode.STATIC, c["changeMode"], msg)
                asserts.assertEqual(self.vtypes.VehiclePropertyAccess.READ, c["access"], msg)
                propValue = self.readVhalProperty(prop)
                asserts.assertEqual(prop, propValue['prop'])
                self.setVhalProperty(prop, propValue["value"],
                    expectedStatus=self.vtypes.StatusCode.ACCESS_DENIED)
            else:  # Non-static property
                asserts.assertNotEqual(self.vtypes.VehiclePropertyChangeMode.STATIC, c["changeMode"], msg)

    def testPropertyRanges(self):
        """Retrieve the property ranges for all areas.

        This checks that the areas noted in the config all give valid area
        configs.  Once these are validated, the values for all these areas
        retrieved from the HIDL must be within the ranges defined."""
        for c in self.configList:
            # Continuous properties need to have a sampling frequency.
            if c["changeMode"] & self.vtypes.VehiclePropertyChangeMode.CONTINUOUS != 0:
                asserts.assertLess(0.0, c["minSampleRate"],
                                   "minSampleRate should be > 0. Config list: %s" % c)
                asserts.assertLess(0.0, c["maxSampleRate"],
                                   "maxSampleRate should be > 0. Config list: %s" % c)
                asserts.assertFalse(c["minSampleRate"] > c["maxSampleRate"],
                                    "Prop 0x%x minSampleRate > maxSampleRate" %
                                        c["prop"])

            areasFound = 0
            for a in c["areaConfigs"]:
                # Make sure this doesn't override one of the other areas found.
                asserts.assertEqual(0, areasFound & a["areaId"])
                areasFound |= a["areaId"]

                # Do some basic checking the min and max aren't mixed up.
                checks = [
                    ("minInt32Value", "maxInt32Value"),
                    ("minInt64Value", "maxInt64Value"),
                    ("minFloatValue", "maxFloatValue")
                ]
                for minName, maxName in checks:
                    asserts.assertFalse(
                        a[minName] > a[maxName],
                        "Prop 0x%x Area 0x%X %s > %s: %d > %d" %
                            (c["prop"], a["areaId"],
                             minName, maxName, a[minName], a[maxName]))

                # Get a value and make sure it's within the bounds.
                propVal = self.readVhalProperty(c["prop"], a["areaId"])
                # Some values may not be available, which is not an error.
                if propVal is None:
                    continue
                val = propVal["value"]
                valTypes = {
                    "int32Values": ("minInt32Value", "maxInt32Value"),
                    "int64Values": ("minInt64Value", "maxInt64Value"),
                    "floatValues": ("minFloatValue", "maxFloatValue"),
                }
                for valType, valBoundNames in valTypes.items():
                    for v in val[valType]:
                        # Make sure value isn't less than the minimum.
                        asserts.assertFalse(
                            v < a[valBoundNames[0]],
                            "Prop 0x%x Area 0x%X %s < min: %s < %s" %
                                (c["prop"], a["areaId"],
                                 valType, v, a[valBoundNames[0]]))
                        # Make sure value isn't greater than the maximum.
                        asserts.assertFalse(
                            v > a[valBoundNames[1]],
                            "Prop 0x%x Area 0x%X %s > max: %s > %s" %
                                (c["prop"], a["areaId"],
                                 valType, v, a[valBoundNames[1]]))

    def getValueIfPropSupported(self, propertyId):
        """Returns tuple of boolean (indicating value supported or not) and the value itself"""
        if (propertyId in self.propToConfig):
            propValue = self.readVhalProperty(propertyId)
            asserts.assertNotEqual(None, propValue, "expected value, prop: 0x%x" % propertyId)
            asserts.assertEqual(propertyId, propValue['prop'])
            return True, self.extractValue(propValue)
        else:
            return False, None

    def testInfoVinMakeModel(self):
        """Verifies INFO_VIN, INFO_MAKE, INFO_MODEL properties"""
        stringProperties = set([
            self.vtypes.VehicleProperty.INFO_VIN,
            self.vtypes.VehicleProperty.INFO_MAKE,
            self.vtypes.VehicleProperty.INFO_MODEL])
        for prop in stringProperties:
            supported, val = self.getValueIfPropSupported(prop)
            if supported:
                asserts.assertEqual(str, type(val), "prop: 0x%x" % prop)
                asserts.assertLess(0, (len(val)), "prop: 0x%x" % prop)

    def testGlobalFloatProperties(self):
        """Verifies that values of global float properties are in the correct range"""
        floatProperties = {
            self.vtypes.VehicleProperty.ENV_CABIN_TEMPERATURE: (-50, 100),  # celsius
            self.vtypes.VehicleProperty.ENV_OUTSIDE_TEMPERATURE: (-50, 100),  # celsius
            self.vtypes.VehicleProperty.ENGINE_RPM : (0, 30000),  # RPMs
            self.vtypes.VehicleProperty.ENGINE_OIL_TEMP : (-50, 150),  # celsius
            self.vtypes.VehicleProperty.ENGINE_COOLANT_TEMP : (-50, 150),  #
            self.vtypes.VehicleProperty.PERF_VEHICLE_SPEED : (0, 150),  # m/s, 150 m/s = 330 mph
            self.vtypes.VehicleProperty.PERF_ODOMETER : (0, 1000000),  # km
            self.vtypes.VehicleProperty.INFO_FUEL_CAPACITY : (0, 1000000),  # milliliter
            self.vtypes.VehicleProperty.INFO_MODEL_YEAR : (1901, 2101),  # year
        }

        for prop, validRange in floatProperties.iteritems():
            supported, val = self.getValueIfPropSupported(prop)
            if supported:
                asserts.assertEqual(float, type(val))
                self.assertValueInRangeForProp(val, validRange[0], validRange[1], prop)

    def testGlobalBoolProperties(self):
        """Verifies that values of global boolean properties are in the correct range"""
        booleanProperties = set([
            self.vtypes.VehicleProperty.PARKING_BRAKE_ON,
            self.vtypes.VehicleProperty.FUEL_LEVEL_LOW,
            self.vtypes.VehicleProperty.NIGHT_MODE,
            self.vtypes.VehicleProperty.DOOR_LOCK,
            self.vtypes.VehicleProperty.MIRROR_LOCK,
            self.vtypes.VehicleProperty.MIRROR_FOLD,
            self.vtypes.VehicleProperty.SEAT_BELT_BUCKLED,
            self.vtypes.VehicleProperty.WINDOW_LOCK,
        ])
        for prop in booleanProperties:
            self.verifyEnumPropIfSupported(prop, [0, 1])

    def testGlobalEnumProperties(self):
        """Verifies that values of global enum properties are in the correct range"""
        enumProperties = {
            self.vtypes.VehicleProperty.GEAR_SELECTION : self.vtypes.VehicleGear,
            self.vtypes.VehicleProperty.CURRENT_GEAR : self.vtypes.VehicleGear,
            self.vtypes.VehicleProperty.TURN_SIGNAL_STATE : self.vtypes.VehicleTurnSignal,
            self.vtypes.VehicleProperty.IGNITION_STATE : self.vtypes.VehicleIgnitionState,
        }
        for prop, enum in enumProperties.iteritems():
            self.verifyEnumPropIfSupported(prop, vars(enum).values())

    def testDebugDump(self):
        """Verifies that call to IVehicle#debugDump is not failing"""
        dumpStr = self.vehicle.debugDump()
        asserts.assertNotEqual(None, dumpStr)

    def extractValue(self, propValue):
        """Extracts value depending on data type of the property"""
        if propValue == None:
            return None

        # Extract data type
        dataType = propValue['prop'] & self.vtypes.VehiclePropertyType.MASK
        val = propValue['value']
        if self.vtypes.VehiclePropertyType.STRING == dataType:
            asserts.assertNotEqual(None, val['stringValue'])
            return val['stringValue']
        elif self.vtypes.VehiclePropertyType.INT32 == dataType or \
                self.vtypes.VehiclePropertyType.BOOLEAN == dataType:
            asserts.assertEqual(1, len(val["int32Values"]))
            return val["int32Values"][0]
        elif self.vtypes.VehiclePropertyType.INT64 == dataType:
            asserts.assertEqual(1, len(val["int64Values"]))
            return val["int64Values"][0]
        elif self.vtypes.VehiclePropertyType.FLOAT == dataType:
            asserts.assertEqual(1, len(val["floatValues"]))
            return val["floatValues"][0]
        elif self.vtypes.VehiclePropertyType.INT32_VEC == dataType:
            asserts.assertLess(0, len(val["int32Values"]))
            return val["int32Values"]
        elif self.vtypes.VehiclePropertyType.FLOAT_VEC == dataType:
            asserts.assertLess(0, len(val["floatValues"]))
            return val["floatValues"]
        elif self.vtypes.VehiclePropertyType.BYTES == dataType:
            asserts.assertLess(0, len(val["bytes"]))
            return val["bytes"]
        else:
            return val

    def verifyEnumPropIfSupported(self, propertyId, validValues):
        """Verifies that if given property supported it is one of the value in validValues set"""
        supported, val = self.getValueIfPropSupported(propertyId)
        if supported:
            asserts.assertEqual(int, type(val))
            self.assertIntValueInRangeForProp(val, validValues, propertyId)

    def assertLessOrEqual(self, first, second, msg=None):
        """Asserts that first <= second"""
        if second < first:
            fullMsg = "%s is not less or equal to %s" % (first, second)
            if msg:
                fullMsg = "%s %s" % (fullMsg, msg)
            fail(fullMsg)

    def assertIntValueInRangeForProp(self, value, validValues, prop):
        """Asserts that given value is in the validValues range"""
        asserts.assertTrue(value in validValues,
                "Invalid value %d for property: 0x%x, expected one of: %s" % (value, prop, validValues))

    def assertValueInRangeForProp(self, value, rangeBegin, rangeEnd, prop):
        """Asserts that given value is in the range [rangeBegin, rangeEnd]"""
        msg = "Value %s is out of range [%s, %s] for property 0x%x" % (value, rangeBegin, rangeEnd, prop)
        self.assertLessOrEqual(rangeBegin, value, msg)
        self.assertLessOrEqual(value, rangeEnd,  msg)

    def getPropConfig(self, propertyId):
        return self.propToConfig[propertyId]

    def isPropSupported(self, propertyId):
        return self.getPropConfig(propertyId) is not None

    def testEngineOilTemp(self):
        """tests engine oil temperature.

        This also tests an HIDL async callback.
        """
        self.onPropertyEventCalled = 0
        self.onPropertySetCalled = 0
        self.onPropertySetErrorCalled = 0

        def onPropertyEvent(vehiclePropValues):
            logging.info("onPropertyEvent received: %s", vehiclePropValues)
            self.onPropertyEventCalled += 1

        def onPropertySet(vehiclePropValue):
            logging.info("onPropertySet notification received: %s", vehiclePropValue)
            self.onPropertySetCalled += 1

        def onPropertySetError(erroCode, propId, areaId):
            logging.info("onPropertySetError, error: %d, prop: 0x%x, area: 0x%x",
                         erroCode, prop, area)
            self.onPropertySetErrorCalled += 1

        config = self.getPropConfig(self.vtypes.VehicleProperty.ENGINE_OIL_TEMP)
        if (config is None):
            logging.info("ENGINE_OIL_TEMP property is not supported")
            return  # Property not supported, we are done here.

        propValue = self.readVhalProperty(self.vtypes.VehicleProperty.ENGINE_OIL_TEMP)
        asserts.assertEqual(1, len(propValue['value']['floatValues']))
        oilTemp = propValue['value']['floatValues'][0]
        logging.info("Current oil temperature: %f C", oilTemp)
        asserts.assertLess(oilTemp, 200)    # Check it is in reasinable range
        asserts.assertLess(-50, oilTemp)

        if (config["changeMode"] == self.vtypes.VehiclePropertyChangeMode.CONTINUOUS):
            logging.info("ENGINE_OIL_TEMP is continuous property, subscribing...")
            callback = self.vehicle.GetHidlCallbackInterface("IVehicleCallback",
                onPropertyEvent=onPropertyEvent,
                onPropertySet=onPropertySet,
                onPropertySetError=onPropertySetError)

            subscribeOptions = {
                "propId" : self.vtypes.VehicleProperty.ENGINE_OIL_TEMP,
                "vehicleAreas" : 0,
                "sampleRate" : 10.0,  # Hz
                "flags" : self.vtypes.SubscribeFlags.HAL_EVENT,
            }
            pbSubscribeOptions = self.vtypes.Py2Pb("SubscribeOptions", subscribeOptions)

            self.vehicle.subscribe(callback, [pbSubscribeOptions])
            for _ in range(5):
                if (self.onPropertyEventCalled > 0 or
                    self.onPropertySetCalled > 0 or
                    self.onPropertySetErrorCalled > 0):
                    return
                time.sleep(1)
            asserts.fail("Callback not called in 5 seconds.")

if __name__ == "__main__":
    test_runner.main()
