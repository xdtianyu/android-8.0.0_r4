/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.car.hardware;

import android.annotation.IntDef;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * This class is a container for the indices of integer and float diagnostic sensors.
 * These values are extracted from types.hal by packages/services/Car/tools/update-obd2-sensors.py
 *
 * DO NOT EDIT MANUALLY
 *
 * @hide
 */
public final class CarDiagnosticSensorIndices {
    public static final class Obd2IntegerSensorIndex {
        private Obd2IntegerSensorIndex() {}

        public static final int FUEL_SYSTEM_STATUS = 0;
        public static final int MALFUNCTION_INDICATOR_LIGHT_ON = 1;
        public static final int IGNITION_MONITORS_SUPPORTED = 2;
        public static final int IGNITION_SPECIFIC_MONITORS = 3;
        public static final int INTAKE_AIR_TEMPERATURE = 4;
        public static final int COMMANDED_SECONDARY_AIR_STATUS = 5;
        public static final int NUM_OXYGEN_SENSORS_PRESENT = 6;
        public static final int RUNTIME_SINCE_ENGINE_START = 7;
        public static final int DISTANCE_TRAVELED_WITH_MALFUNCTION_INDICATOR_LIGHT_ON = 8;
        public static final int WARMUPS_SINCE_CODES_CLEARED = 9;
        public static final int DISTANCE_TRAVELED_SINCE_CODES_CLEARED = 10;
        public static final int ABSOLUTE_BAROMETRIC_PRESSURE = 11;
        public static final int CONTROL_MODULE_VOLTAGE = 12;
        public static final int AMBIENT_AIR_TEMPERATURE = 13;
        public static final int TIME_WITH_MALFUNCTION_LIGHT_ON = 14;
        public static final int TIME_SINCE_TROUBLE_CODES_CLEARED = 15;
        public static final int MAX_FUEL_AIR_EQUIVALENCE_RATIO = 16;
        public static final int MAX_OXYGEN_SENSOR_VOLTAGE = 17;
        public static final int MAX_OXYGEN_SENSOR_CURRENT = 18;
        public static final int MAX_INTAKE_MANIFOLD_ABSOLUTE_PRESSURE = 19;
        public static final int MAX_AIR_FLOW_RATE_FROM_MASS_AIR_FLOW_SENSOR = 20;
        public static final int FUEL_TYPE = 21;
        public static final int FUEL_RAIL_ABSOLUTE_PRESSURE = 22;
        public static final int ENGINE_OIL_TEMPERATURE = 23;
        public static final int DRIVER_DEMAND_PERCENT_TORQUE = 24;
        public static final int ENGINE_ACTUAL_PERCENT_TORQUE = 25;
        public static final int ENGINE_REFERENCE_PERCENT_TORQUE = 26;
        public static final int ENGINE_PERCENT_TORQUE_DATA_IDLE = 27;
        public static final int ENGINE_PERCENT_TORQUE_DATA_POINT1 = 28;
        public static final int ENGINE_PERCENT_TORQUE_DATA_POINT2 = 29;
        public static final int ENGINE_PERCENT_TORQUE_DATA_POINT3 = 30;
        public static final int ENGINE_PERCENT_TORQUE_DATA_POINT4 = 31;
        public static final int LAST_SYSTEM = ENGINE_PERCENT_TORQUE_DATA_POINT4;
        public static final int VENDOR_START = LAST_SYSTEM + 1;
    }

    public static final class Obd2FloatSensorIndex {
        private Obd2FloatSensorIndex() {}

        public static final int CALCULATED_ENGINE_LOAD = 0;
        public static final int ENGINE_COOLANT_TEMPERATURE = 1;
        public static final int SHORT_TERM_FUEL_TRIM_BANK1 = 2;
        public static final int LONG_TERM_FUEL_TRIM_BANK1 = 3;
        public static final int SHORT_TERM_FUEL_TRIM_BANK2 = 4;
        public static final int LONG_TERM_FUEL_TRIM_BANK2 = 5;
        public static final int FUEL_PRESSURE = 6;
        public static final int INTAKE_MANIFOLD_ABSOLUTE_PRESSURE = 7;
        public static final int ENGINE_RPM = 8;
        public static final int VEHICLE_SPEED = 9;
        public static final int TIMING_ADVANCE = 10;
        public static final int MAF_AIR_FLOW_RATE = 11;
        public static final int THROTTLE_POSITION = 12;
        public static final int OXYGEN_SENSOR1_VOLTAGE = 13;
        public static final int OXYGEN_SENSOR1_SHORT_TERM_FUEL_TRIM = 14;
        public static final int OXYGEN_SENSOR1_FUEL_AIR_EQUIVALENCE_RATIO = 15;
        public static final int OXYGEN_SENSOR2_VOLTAGE = 16;
        public static final int OXYGEN_SENSOR2_SHORT_TERM_FUEL_TRIM = 17;
        public static final int OXYGEN_SENSOR2_FUEL_AIR_EQUIVALENCE_RATIO = 18;
        public static final int OXYGEN_SENSOR3_VOLTAGE = 19;
        public static final int OXYGEN_SENSOR3_SHORT_TERM_FUEL_TRIM = 20;
        public static final int OXYGEN_SENSOR3_FUEL_AIR_EQUIVALENCE_RATIO = 21;
        public static final int OXYGEN_SENSOR4_VOLTAGE = 22;
        public static final int OXYGEN_SENSOR4_SHORT_TERM_FUEL_TRIM = 23;
        public static final int OXYGEN_SENSOR4_FUEL_AIR_EQUIVALENCE_RATIO = 24;
        public static final int OXYGEN_SENSOR5_VOLTAGE = 25;
        public static final int OXYGEN_SENSOR5_SHORT_TERM_FUEL_TRIM = 26;
        public static final int OXYGEN_SENSOR5_FUEL_AIR_EQUIVALENCE_RATIO = 27;
        public static final int OXYGEN_SENSOR6_VOLTAGE = 28;
        public static final int OXYGEN_SENSOR6_SHORT_TERM_FUEL_TRIM = 29;
        public static final int OXYGEN_SENSOR6_FUEL_AIR_EQUIVALENCE_RATIO = 30;
        public static final int OXYGEN_SENSOR7_VOLTAGE = 31;
        public static final int OXYGEN_SENSOR7_SHORT_TERM_FUEL_TRIM = 32;
        public static final int OXYGEN_SENSOR7_FUEL_AIR_EQUIVALENCE_RATIO = 33;
        public static final int OXYGEN_SENSOR8_VOLTAGE = 34;
        public static final int OXYGEN_SENSOR8_SHORT_TERM_FUEL_TRIM = 35;
        public static final int OXYGEN_SENSOR8_FUEL_AIR_EQUIVALENCE_RATIO = 36;
        public static final int FUEL_RAIL_PRESSURE = 37;
        public static final int FUEL_RAIL_GAUGE_PRESSURE = 38;
        public static final int COMMANDED_EXHAUST_GAS_RECIRCULATION = 39;
        public static final int EXHAUST_GAS_RECIRCULATION_ERROR = 40;
        public static final int COMMANDED_EVAPORATIVE_PURGE = 41;
        public static final int FUEL_TANK_LEVEL_INPUT = 42;
        public static final int EVAPORATION_SYSTEM_VAPOR_PRESSURE = 43;
        public static final int CATALYST_TEMPERATURE_BANK1_SENSOR1 = 44;
        public static final int CATALYST_TEMPERATURE_BANK2_SENSOR1 = 45;
        public static final int CATALYST_TEMPERATURE_BANK1_SENSOR2 = 46;
        public static final int CATALYST_TEMPERATURE_BANK2_SENSOR2 = 47;
        public static final int ABSOLUTE_LOAD_VALUE = 48;
        public static final int FUEL_AIR_COMMANDED_EQUIVALENCE_RATIO = 49;
        public static final int RELATIVE_THROTTLE_POSITION = 50;
        public static final int ABSOLUTE_THROTTLE_POSITION_B = 51;
        public static final int ABSOLUTE_THROTTLE_POSITION_C = 52;
        public static final int ACCELERATOR_PEDAL_POSITION_D = 53;
        public static final int ACCELERATOR_PEDAL_POSITION_E = 54;
        public static final int ACCELERATOR_PEDAL_POSITION_F = 55;
        public static final int COMMANDED_THROTTLE_ACTUATOR = 56;
        public static final int ETHANOL_FUEL_PERCENTAGE = 57;
        public static final int ABSOLUTE_EVAPORATION_SYSTEM_VAPOR_PRESSURE = 58;
        public static final int SHORT_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK1 = 59;
        public static final int SHORT_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK2 = 60;
        public static final int SHORT_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK3 = 61;
        public static final int SHORT_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK4 = 62;
        public static final int LONG_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK1 = 63;
        public static final int LONG_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK2 = 64;
        public static final int LONG_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK3 = 65;
        public static final int LONG_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK4 = 66;
        public static final int RELATIVE_ACCELERATOR_PEDAL_POSITION = 67;
        public static final int HYBRID_BATTERY_PACK_REMAINING_LIFE = 68;
        public static final int FUEL_INJECTION_TIMING = 69;
        public static final int ENGINE_FUEL_RATE = 70;
        public static final int LAST_SYSTEM = ENGINE_FUEL_RATE;
        public static final int VENDOR_START = LAST_SYSTEM + 1;
    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
        Obd2IntegerSensorIndex.FUEL_SYSTEM_STATUS,
        Obd2IntegerSensorIndex.MALFUNCTION_INDICATOR_LIGHT_ON,
        Obd2IntegerSensorIndex.IGNITION_MONITORS_SUPPORTED,
        Obd2IntegerSensorIndex.IGNITION_SPECIFIC_MONITORS,
        Obd2IntegerSensorIndex.INTAKE_AIR_TEMPERATURE,
        Obd2IntegerSensorIndex.COMMANDED_SECONDARY_AIR_STATUS,
        Obd2IntegerSensorIndex.NUM_OXYGEN_SENSORS_PRESENT,
        Obd2IntegerSensorIndex.RUNTIME_SINCE_ENGINE_START,
        Obd2IntegerSensorIndex.DISTANCE_TRAVELED_WITH_MALFUNCTION_INDICATOR_LIGHT_ON,
        Obd2IntegerSensorIndex.WARMUPS_SINCE_CODES_CLEARED,
        Obd2IntegerSensorIndex.DISTANCE_TRAVELED_SINCE_CODES_CLEARED,
        Obd2IntegerSensorIndex.ABSOLUTE_BAROMETRIC_PRESSURE,
        Obd2IntegerSensorIndex.CONTROL_MODULE_VOLTAGE,
        Obd2IntegerSensorIndex.AMBIENT_AIR_TEMPERATURE,
        Obd2IntegerSensorIndex.TIME_WITH_MALFUNCTION_LIGHT_ON,
        Obd2IntegerSensorIndex.TIME_SINCE_TROUBLE_CODES_CLEARED,
        Obd2IntegerSensorIndex.MAX_FUEL_AIR_EQUIVALENCE_RATIO,
        Obd2IntegerSensorIndex.MAX_OXYGEN_SENSOR_VOLTAGE,
        Obd2IntegerSensorIndex.MAX_OXYGEN_SENSOR_CURRENT,
        Obd2IntegerSensorIndex.MAX_INTAKE_MANIFOLD_ABSOLUTE_PRESSURE,
        Obd2IntegerSensorIndex.MAX_AIR_FLOW_RATE_FROM_MASS_AIR_FLOW_SENSOR,
        Obd2IntegerSensorIndex.FUEL_TYPE,
        Obd2IntegerSensorIndex.FUEL_RAIL_ABSOLUTE_PRESSURE,
        Obd2IntegerSensorIndex.ENGINE_OIL_TEMPERATURE,
        Obd2IntegerSensorIndex.DRIVER_DEMAND_PERCENT_TORQUE,
        Obd2IntegerSensorIndex.ENGINE_ACTUAL_PERCENT_TORQUE,
        Obd2IntegerSensorIndex.ENGINE_REFERENCE_PERCENT_TORQUE,
        Obd2IntegerSensorIndex.ENGINE_PERCENT_TORQUE_DATA_IDLE,
        Obd2IntegerSensorIndex.ENGINE_PERCENT_TORQUE_DATA_POINT1,
        Obd2IntegerSensorIndex.ENGINE_PERCENT_TORQUE_DATA_POINT2,
        Obd2IntegerSensorIndex.ENGINE_PERCENT_TORQUE_DATA_POINT3,
        Obd2IntegerSensorIndex.ENGINE_PERCENT_TORQUE_DATA_POINT4,
        Obd2IntegerSensorIndex.LAST_SYSTEM,
        Obd2IntegerSensorIndex.VENDOR_START,
    })
    public @interface IntegerSensorIndex {}

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
        Obd2FloatSensorIndex.CALCULATED_ENGINE_LOAD,
        Obd2FloatSensorIndex.ENGINE_COOLANT_TEMPERATURE,
        Obd2FloatSensorIndex.SHORT_TERM_FUEL_TRIM_BANK1,
        Obd2FloatSensorIndex.LONG_TERM_FUEL_TRIM_BANK1,
        Obd2FloatSensorIndex.SHORT_TERM_FUEL_TRIM_BANK2,
        Obd2FloatSensorIndex.LONG_TERM_FUEL_TRIM_BANK2,
        Obd2FloatSensorIndex.FUEL_PRESSURE,
        Obd2FloatSensorIndex.INTAKE_MANIFOLD_ABSOLUTE_PRESSURE,
        Obd2FloatSensorIndex.ENGINE_RPM,
        Obd2FloatSensorIndex.VEHICLE_SPEED,
        Obd2FloatSensorIndex.TIMING_ADVANCE,
        Obd2FloatSensorIndex.MAF_AIR_FLOW_RATE,
        Obd2FloatSensorIndex.THROTTLE_POSITION,
        Obd2FloatSensorIndex.OXYGEN_SENSOR1_VOLTAGE,
        Obd2FloatSensorIndex.OXYGEN_SENSOR1_SHORT_TERM_FUEL_TRIM,
        Obd2FloatSensorIndex.OXYGEN_SENSOR1_FUEL_AIR_EQUIVALENCE_RATIO,
        Obd2FloatSensorIndex.OXYGEN_SENSOR2_VOLTAGE,
        Obd2FloatSensorIndex.OXYGEN_SENSOR2_SHORT_TERM_FUEL_TRIM,
        Obd2FloatSensorIndex.OXYGEN_SENSOR2_FUEL_AIR_EQUIVALENCE_RATIO,
        Obd2FloatSensorIndex.OXYGEN_SENSOR3_VOLTAGE,
        Obd2FloatSensorIndex.OXYGEN_SENSOR3_SHORT_TERM_FUEL_TRIM,
        Obd2FloatSensorIndex.OXYGEN_SENSOR3_FUEL_AIR_EQUIVALENCE_RATIO,
        Obd2FloatSensorIndex.OXYGEN_SENSOR4_VOLTAGE,
        Obd2FloatSensorIndex.OXYGEN_SENSOR4_SHORT_TERM_FUEL_TRIM,
        Obd2FloatSensorIndex.OXYGEN_SENSOR4_FUEL_AIR_EQUIVALENCE_RATIO,
        Obd2FloatSensorIndex.OXYGEN_SENSOR5_VOLTAGE,
        Obd2FloatSensorIndex.OXYGEN_SENSOR5_SHORT_TERM_FUEL_TRIM,
        Obd2FloatSensorIndex.OXYGEN_SENSOR5_FUEL_AIR_EQUIVALENCE_RATIO,
        Obd2FloatSensorIndex.OXYGEN_SENSOR6_VOLTAGE,
        Obd2FloatSensorIndex.OXYGEN_SENSOR6_SHORT_TERM_FUEL_TRIM,
        Obd2FloatSensorIndex.OXYGEN_SENSOR6_FUEL_AIR_EQUIVALENCE_RATIO,
        Obd2FloatSensorIndex.OXYGEN_SENSOR7_VOLTAGE,
        Obd2FloatSensorIndex.OXYGEN_SENSOR7_SHORT_TERM_FUEL_TRIM,
        Obd2FloatSensorIndex.OXYGEN_SENSOR7_FUEL_AIR_EQUIVALENCE_RATIO,
        Obd2FloatSensorIndex.OXYGEN_SENSOR8_VOLTAGE,
        Obd2FloatSensorIndex.OXYGEN_SENSOR8_SHORT_TERM_FUEL_TRIM,
        Obd2FloatSensorIndex.OXYGEN_SENSOR8_FUEL_AIR_EQUIVALENCE_RATIO,
        Obd2FloatSensorIndex.FUEL_RAIL_PRESSURE,
        Obd2FloatSensorIndex.FUEL_RAIL_GAUGE_PRESSURE,
        Obd2FloatSensorIndex.COMMANDED_EXHAUST_GAS_RECIRCULATION,
        Obd2FloatSensorIndex.EXHAUST_GAS_RECIRCULATION_ERROR,
        Obd2FloatSensorIndex.COMMANDED_EVAPORATIVE_PURGE,
        Obd2FloatSensorIndex.FUEL_TANK_LEVEL_INPUT,
        Obd2FloatSensorIndex.EVAPORATION_SYSTEM_VAPOR_PRESSURE,
        Obd2FloatSensorIndex.CATALYST_TEMPERATURE_BANK1_SENSOR1,
        Obd2FloatSensorIndex.CATALYST_TEMPERATURE_BANK2_SENSOR1,
        Obd2FloatSensorIndex.CATALYST_TEMPERATURE_BANK1_SENSOR2,
        Obd2FloatSensorIndex.CATALYST_TEMPERATURE_BANK2_SENSOR2,
        Obd2FloatSensorIndex.ABSOLUTE_LOAD_VALUE,
        Obd2FloatSensorIndex.FUEL_AIR_COMMANDED_EQUIVALENCE_RATIO,
        Obd2FloatSensorIndex.RELATIVE_THROTTLE_POSITION,
        Obd2FloatSensorIndex.ABSOLUTE_THROTTLE_POSITION_B,
        Obd2FloatSensorIndex.ABSOLUTE_THROTTLE_POSITION_C,
        Obd2FloatSensorIndex.ACCELERATOR_PEDAL_POSITION_D,
        Obd2FloatSensorIndex.ACCELERATOR_PEDAL_POSITION_E,
        Obd2FloatSensorIndex.ACCELERATOR_PEDAL_POSITION_F,
        Obd2FloatSensorIndex.COMMANDED_THROTTLE_ACTUATOR,
        Obd2FloatSensorIndex.ETHANOL_FUEL_PERCENTAGE,
        Obd2FloatSensorIndex.ABSOLUTE_EVAPORATION_SYSTEM_VAPOR_PRESSURE,
        Obd2FloatSensorIndex.SHORT_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK1,
        Obd2FloatSensorIndex.SHORT_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK2,
        Obd2FloatSensorIndex.SHORT_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK3,
        Obd2FloatSensorIndex.SHORT_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK4,
        Obd2FloatSensorIndex.LONG_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK1,
        Obd2FloatSensorIndex.LONG_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK2,
        Obd2FloatSensorIndex.LONG_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK3,
        Obd2FloatSensorIndex.LONG_TERM_SECONDARY_OXYGEN_SENSOR_TRIM_BANK4,
        Obd2FloatSensorIndex.RELATIVE_ACCELERATOR_PEDAL_POSITION,
        Obd2FloatSensorIndex.HYBRID_BATTERY_PACK_REMAINING_LIFE,
        Obd2FloatSensorIndex.FUEL_INJECTION_TIMING,
        Obd2FloatSensorIndex.ENGINE_FUEL_RATE,
        Obd2FloatSensorIndex.LAST_SYSTEM,
        Obd2FloatSensorIndex.VENDOR_START,
    })
    public @interface FloatSensorIndex {}

}
