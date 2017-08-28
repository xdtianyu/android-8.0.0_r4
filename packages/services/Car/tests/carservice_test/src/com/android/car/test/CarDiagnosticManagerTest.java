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

package com.android.car.test;

import static java.lang.Integer.toHexString;

import android.car.Car;
import android.car.hardware.CarDiagnosticEvent;
import android.car.hardware.CarDiagnosticEvent.FuelSystemStatus;
import android.car.hardware.CarDiagnosticEvent.FuelType;
import android.car.hardware.CarDiagnosticEvent.IgnitionMonitors.CommonIgnitionMonitors;
import android.car.hardware.CarDiagnosticEvent.IgnitionMonitors.CompressionIgnitionMonitors;
import android.car.hardware.CarDiagnosticEvent.IgnitionMonitors.SparkIgnitionMonitors;
import android.car.hardware.CarDiagnosticEvent.SecondaryAirStatus;
import android.car.hardware.CarDiagnosticManager;
import android.car.hardware.CarDiagnosticSensorIndices.Obd2FloatSensorIndex;
import android.car.hardware.CarDiagnosticSensorIndices.Obd2IntegerSensorIndex;
import android.hardware.automotive.vehicle.V2_0.VehiclePropValue;
import android.hardware.automotive.vehicle.V2_1.VehicleProperty;
import android.os.SystemClock;
import android.test.suitebuilder.annotation.MediumTest;
import android.util.Log;
import com.android.car.internal.FeatureConfiguration;
import com.android.car.vehiclehal.DiagnosticEventBuilder;
import com.android.car.vehiclehal.VehiclePropValueBuilder;
import com.android.car.vehiclehal.test.MockedVehicleHal.VehicleHalPropertyHandler;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/** Test the public entry points for the CarDiagnosticManager */
@MediumTest
public class CarDiagnosticManagerTest extends MockedCarTestBase {
    private static final String TAG = CarDiagnosticManagerTest.class.getSimpleName();

    private final DiagnosticEventBuilder mLiveFrameEventBuilder =
            new DiagnosticEventBuilder(VehicleProperty.OBD2_LIVE_FRAME);
    private final DiagnosticEventBuilder mFreezeFrameEventBuilder =
            new DiagnosticEventBuilder(VehicleProperty.OBD2_FREEZE_FRAME);
    private final FreezeFrameProperties mFreezeFrameProperties = new FreezeFrameProperties();

    private CarDiagnosticManager mCarDiagnosticManager;

    private static final String DTC = "P1010";

    /**
     * This class is a central repository for freeze frame data. It ensures that timestamps and
     * events are kept in sync and provides a consistent access model for diagnostic properties.
     */
    class FreezeFrameProperties {
        private final HashMap<Long, VehiclePropValue> mEvents = new HashMap<>();

        public final VehicleHalPropertyHandler mFreezeFrameInfoHandler =
                new FreezeFrameInfoHandler();
        public final VehicleHalPropertyHandler mFreezeFrameHandler = new FreezeFrameHandler();
        public final VehicleHalPropertyHandler mFreezeFrameClearHandler =
                new FreezeFrameClearHandler();

        synchronized VehiclePropValue addNewEvent(DiagnosticEventBuilder builder) {
            long timestamp = SystemClock.elapsedRealtimeNanos();
            return addNewEvent(builder, timestamp);
        }

        synchronized VehiclePropValue addNewEvent(DiagnosticEventBuilder builder, long timestamp) {
            VehiclePropValue newEvent = builder.build(timestamp);
            mEvents.put(timestamp, newEvent);
            return newEvent;
        }

        synchronized VehiclePropValue removeEvent(long timestamp) {
            return mEvents.remove(timestamp);
        }

        synchronized void removeEvents() {
            mEvents.clear();
        }

        synchronized long[] getTimestamps() {
            return mEvents.keySet().stream().mapToLong(Long::longValue).toArray();
        }

        synchronized VehiclePropValue getEvent(long timestamp) {
            return mEvents.get(timestamp);
        }

        class FreezeFramePropertyHandler implements VehicleHalPropertyHandler {
            private boolean mSubscribed = false;

            protected final int VEHICLE_PROPERTY;

            protected FreezeFramePropertyHandler(int propertyId) {
                VEHICLE_PROPERTY = propertyId;
            }

            @Override
            public synchronized void onPropertySet(VehiclePropValue value) {
                assertEquals(VEHICLE_PROPERTY, value.prop);
            }

            @Override
            public synchronized VehiclePropValue onPropertyGet(VehiclePropValue value) {
                assertEquals(VEHICLE_PROPERTY, value.prop);
                return null;
            }

            @Override
            public synchronized void onPropertySubscribe(
                    int property, int zones, float sampleRate) {
                assertEquals(VEHICLE_PROPERTY, property);
                mSubscribed = true;
            }

            @Override
            public synchronized void onPropertyUnsubscribe(int property) {
                assertEquals(VEHICLE_PROPERTY, property);
                if (!mSubscribed) {
                    throw new IllegalArgumentException(
                            "Property was not subscribed 0x" + toHexString(property));
                }
                mSubscribed = false;
            }
        }

        class FreezeFrameInfoHandler extends FreezeFramePropertyHandler {
            FreezeFrameInfoHandler() {
                super(VehicleProperty.OBD2_FREEZE_FRAME_INFO);
            }

            @Override
            public synchronized VehiclePropValue onPropertyGet(VehiclePropValue value) {
                super.onPropertyGet(value);
                VehiclePropValueBuilder builder =
                        VehiclePropValueBuilder.newBuilder(VEHICLE_PROPERTY);
                builder.setInt64Value(getTimestamps());
                return builder.build();
            }
        }

        class FreezeFrameHandler extends FreezeFramePropertyHandler {
            FreezeFrameHandler() {
                super(VehicleProperty.OBD2_FREEZE_FRAME);
            }

            @Override
            public synchronized VehiclePropValue onPropertyGet(VehiclePropValue value) {
                super.onPropertyGet(value);
                long timestamp = value.value.int64Values.get(0);
                return getEvent(timestamp);
            }
        }

        class FreezeFrameClearHandler extends FreezeFramePropertyHandler {
            FreezeFrameClearHandler() {
                super(VehicleProperty.OBD2_FREEZE_FRAME_CLEAR);
            }

            @Override
            public synchronized void onPropertySet(VehiclePropValue value) {
                super.onPropertySet(value);
                if (0 == value.value.int64Values.size()) {
                    removeEvents();
                } else {
                    for (long timestamp : value.value.int64Values) {
                        removeEvent(timestamp);
                    }
                }
            }
        }
    }

    @Override
    protected synchronized void configureMockedHal() {
        java.util.Collection<Integer> numVendorSensors = Arrays.asList(0, 0);
        addProperty(VehicleProperty.OBD2_LIVE_FRAME, mLiveFrameEventBuilder.build())
                .setConfigArray(numVendorSensors);
        addProperty(
                VehicleProperty.OBD2_FREEZE_FRAME_INFO,
                mFreezeFrameProperties.mFreezeFrameInfoHandler);
        addProperty(VehicleProperty.OBD2_FREEZE_FRAME, mFreezeFrameProperties.mFreezeFrameHandler)
                .setConfigArray(numVendorSensors);
        addProperty(
                VehicleProperty.OBD2_FREEZE_FRAME_CLEAR,
                mFreezeFrameProperties.mFreezeFrameClearHandler);
    }

    private boolean isFeatureEnabled() {
        return FeatureConfiguration.ENABLE_DIAGNOSTIC;
    }

    @Override
    protected void setUp() throws Exception {
        mLiveFrameEventBuilder.addIntSensor(Obd2IntegerSensorIndex.AMBIENT_AIR_TEMPERATURE, 30);
        mLiveFrameEventBuilder.addIntSensor(
                Obd2IntegerSensorIndex.FUEL_SYSTEM_STATUS,
                FuelSystemStatus.OPEN_ENGINE_LOAD_OR_DECELERATION);
        mLiveFrameEventBuilder.addIntSensor(
                Obd2IntegerSensorIndex.RUNTIME_SINCE_ENGINE_START, 5000);
        mLiveFrameEventBuilder.addIntSensor(Obd2IntegerSensorIndex.CONTROL_MODULE_VOLTAGE, 2);
        mLiveFrameEventBuilder.addFloatSensor(Obd2FloatSensorIndex.CALCULATED_ENGINE_LOAD, 0.125f);
        mLiveFrameEventBuilder.addFloatSensor(Obd2FloatSensorIndex.VEHICLE_SPEED, 12.5f);

        mFreezeFrameEventBuilder.addIntSensor(Obd2IntegerSensorIndex.AMBIENT_AIR_TEMPERATURE, 30);
        mFreezeFrameEventBuilder.addIntSensor(
                Obd2IntegerSensorIndex.RUNTIME_SINCE_ENGINE_START, 5000);
        mFreezeFrameEventBuilder.addIntSensor(Obd2IntegerSensorIndex.CONTROL_MODULE_VOLTAGE, 2);
        mFreezeFrameEventBuilder.addFloatSensor(
                Obd2FloatSensorIndex.CALCULATED_ENGINE_LOAD, 0.125f);
        mFreezeFrameEventBuilder.addFloatSensor(Obd2FloatSensorIndex.VEHICLE_SPEED, 12.5f);
        mFreezeFrameEventBuilder.setDTC(DTC);

        super.setUp();

        if (isFeatureEnabled()) {
            Log.i(TAG, "attempting to get DIAGNOSTIC_SERVICE");
            mCarDiagnosticManager =
                    (CarDiagnosticManager) getCar().getCarManager(Car.DIAGNOSTIC_SERVICE);
        } else {
            Log.i(TAG, "skipping diagnostic tests as ENABLE_DIAGNOSTIC flag is false");
        }
    }

    public void testLiveFrameRead() throws Exception {
        if (!isFeatureEnabled()) {
            Log.i(TAG, "skipping testLiveFrameRead as diagnostics API is not enabled");
            return;
        }

        CarDiagnosticEvent liveFrame = mCarDiagnosticManager.getLatestLiveFrame();

        assertNotNull(liveFrame);
        assertTrue(liveFrame.isLiveFrame());
        assertFalse(liveFrame.isFreezeFrame());
        assertFalse(liveFrame.isEmptyFrame());

        assertEquals(
                5000,
                liveFrame
                        .getSystemIntegerSensor(Obd2IntegerSensorIndex.RUNTIME_SINCE_ENGINE_START)
                        .intValue());
        assertEquals(
                30,
                liveFrame
                        .getSystemIntegerSensor(Obd2IntegerSensorIndex.AMBIENT_AIR_TEMPERATURE)
                        .intValue());
        assertEquals(
                2,
                liveFrame
                        .getSystemIntegerSensor(Obd2IntegerSensorIndex.CONTROL_MODULE_VOLTAGE)
                        .intValue());
        assertEquals(
                0.125f,
                liveFrame
                        .getSystemFloatSensor(Obd2FloatSensorIndex.CALCULATED_ENGINE_LOAD)
                        .floatValue());
        assertEquals(
                12.5f,
                liveFrame.getSystemFloatSensor(Obd2FloatSensorIndex.VEHICLE_SPEED).floatValue());
    }

    public void testLiveFrameEvent() throws Exception {
        if (!isFeatureEnabled()) {
            Log.i(TAG, "skipping testLiveFrameEvent as diagnostics API is not enabled");
            return;
        }

        Listener listener = new Listener();
        mCarDiagnosticManager.registerListener(
                listener,
                CarDiagnosticManager.FRAME_TYPE_FLAG_LIVE,
                android.car.hardware.CarSensorManager.SENSOR_RATE_NORMAL);

        listener.reset();
        long time = SystemClock.elapsedRealtimeNanos();
        mLiveFrameEventBuilder.addIntSensor(
                Obd2IntegerSensorIndex.RUNTIME_SINCE_ENGINE_START, 5100);

        getMockedVehicleHal().injectEvent(mLiveFrameEventBuilder.build(time));
        assertTrue(listener.waitForEvent(time));

        CarDiagnosticEvent liveFrame = listener.getLastEvent();

        assertEquals(
                5100,
                liveFrame
                        .getSystemIntegerSensor(Obd2IntegerSensorIndex.RUNTIME_SINCE_ENGINE_START)
                        .intValue());
    }

    public void testMissingSensorRead() throws Exception {
        if (!isFeatureEnabled()) {
            Log.i(TAG, "skipping testMissingSensorRead as diagnostics API is not enabled");
            return;
        }

        Listener listener = new Listener();
        mCarDiagnosticManager.registerListener(
                listener,
                CarDiagnosticManager.FRAME_TYPE_FLAG_LIVE,
                android.car.hardware.CarSensorManager.SENSOR_RATE_NORMAL);

        getMockedVehicleHal().injectEvent(mLiveFrameEventBuilder.build());
        assertTrue(listener.waitForEvent());

        CarDiagnosticEvent liveFrame = listener.getLastEvent();
        assertNotNull(liveFrame);

        assertNull(
                liveFrame.getSystemIntegerSensor(
                        Obd2IntegerSensorIndex.DRIVER_DEMAND_PERCENT_TORQUE));
        assertEquals(
                -1,
                liveFrame.getSystemIntegerSensor(
                        Obd2IntegerSensorIndex.DRIVER_DEMAND_PERCENT_TORQUE, -1));

        assertNull(liveFrame.getSystemFloatSensor(Obd2FloatSensorIndex.OXYGEN_SENSOR6_VOLTAGE));
        assertEquals(
                0.25f,
                liveFrame.getSystemFloatSensor(Obd2FloatSensorIndex.OXYGEN_SENSOR5_VOLTAGE, 0.25f));

        assertNull(liveFrame.getVendorIntegerSensor(Obd2IntegerSensorIndex.VENDOR_START));
        assertEquals(-1, liveFrame.getVendorIntegerSensor(Obd2IntegerSensorIndex.VENDOR_START, -1));

        assertNull(liveFrame.getVendorFloatSensor(Obd2FloatSensorIndex.VENDOR_START));
        assertEquals(
                0.25f, liveFrame.getVendorFloatSensor(Obd2FloatSensorIndex.VENDOR_START, 0.25f));
    }

    public void testFuelSystemStatus() throws Exception {
        if (!isFeatureEnabled()) {
            Log.i(TAG, "skipping testFuelSystemStatus as diagnostics API is not enabled");
            return;
        }

        Listener listener = new Listener();
        mCarDiagnosticManager.registerListener(
                listener,
                CarDiagnosticManager.FRAME_TYPE_FLAG_LIVE,
                android.car.hardware.CarSensorManager.SENSOR_RATE_NORMAL);

        getMockedVehicleHal().injectEvent(mLiveFrameEventBuilder.build());
        assertTrue(listener.waitForEvent());

        CarDiagnosticEvent liveFrame = listener.getLastEvent();
        assertNotNull(liveFrame);

        assertEquals(
                FuelSystemStatus.OPEN_ENGINE_LOAD_OR_DECELERATION,
                liveFrame
                        .getSystemIntegerSensor(Obd2IntegerSensorIndex.FUEL_SYSTEM_STATUS)
                        .intValue());
        assertEquals(
                FuelSystemStatus.OPEN_ENGINE_LOAD_OR_DECELERATION,
                liveFrame.getFuelSystemStatus().intValue());
    }

    public void testSecondaryAirStatus() throws Exception {
        if (!isFeatureEnabled()) {
            Log.i(TAG, "skipping testSecondaryAirStatus as diagnostics API is not enabled");
            return;
        }

        Listener listener = new Listener();
        mCarDiagnosticManager.registerListener(
                listener,
                CarDiagnosticManager.FRAME_TYPE_FLAG_LIVE,
                android.car.hardware.CarSensorManager.SENSOR_RATE_NORMAL);

        mLiveFrameEventBuilder.addIntSensor(
                Obd2IntegerSensorIndex.COMMANDED_SECONDARY_AIR_STATUS,
                SecondaryAirStatus.FROM_OUTSIDE_OR_OFF);
        long timestamp = SystemClock.elapsedRealtimeNanos();
        getMockedVehicleHal().injectEvent(mLiveFrameEventBuilder.build(timestamp));

        assertTrue(listener.waitForEvent(timestamp));

        CarDiagnosticEvent liveFrame = listener.getLastEvent();
        assertNotNull(liveFrame);

        assertEquals(
                SecondaryAirStatus.FROM_OUTSIDE_OR_OFF,
                liveFrame
                        .getSystemIntegerSensor(
                                Obd2IntegerSensorIndex.COMMANDED_SECONDARY_AIR_STATUS)
                        .intValue());
        assertEquals(
                SecondaryAirStatus.FROM_OUTSIDE_OR_OFF,
                liveFrame.getSecondaryAirStatus().intValue());
    }

    public void testIgnitionMonitors() throws Exception {
        if (!isFeatureEnabled()) {
            Log.i(TAG, "skipping testIgnitionMonitors as diagnostics API is not enabled");
            return;
        }

        Listener listener = new Listener();
        mCarDiagnosticManager.registerListener(
                listener,
                CarDiagnosticManager.FRAME_TYPE_FLAG_LIVE,
                android.car.hardware.CarSensorManager.SENSOR_RATE_NORMAL);

        // cfr. CarDiagnosticEvent for the meaning of the several bits
        final int sparkMonitorsValue =
                0x1 | (0x1 << 2) | (0x1 << 3) | (0x1 << 6) | (0x1 << 10) | (0x1 << 11);

        final int compressionMonitorsValue =
                (0x1 << 2) | (0x1 << 3) | (0x1 << 6) | (0x1 << 12) | (0x1 << 13);

        mLiveFrameEventBuilder.addIntSensor(Obd2IntegerSensorIndex.IGNITION_MONITORS_SUPPORTED, 0);
        mLiveFrameEventBuilder.addIntSensor(
                Obd2IntegerSensorIndex.IGNITION_SPECIFIC_MONITORS, sparkMonitorsValue);

        long timestamp = SystemClock.elapsedRealtimeNanos();
        getMockedVehicleHal().injectEvent(mLiveFrameEventBuilder.build(timestamp));

        assertTrue(listener.waitForEvent(timestamp));

        CarDiagnosticEvent liveFrame = listener.getLastEvent();
        assertNotNull(liveFrame);

        CommonIgnitionMonitors commonIgnitionMonitors = liveFrame.getIgnitionMonitors();
        assertNotNull(commonIgnitionMonitors);
        assertTrue(commonIgnitionMonitors.components.available);
        assertFalse(commonIgnitionMonitors.components.incomplete);
        assertTrue(commonIgnitionMonitors.fuelSystem.available);
        assertTrue(commonIgnitionMonitors.fuelSystem.incomplete);
        assertFalse(commonIgnitionMonitors.misfire.available);
        assertFalse(commonIgnitionMonitors.misfire.incomplete);

        SparkIgnitionMonitors sparkIgnitionMonitors =
                commonIgnitionMonitors.asSparkIgnitionMonitors();
        assertNotNull(sparkIgnitionMonitors);
        assertNull(commonIgnitionMonitors.asCompressionIgnitionMonitors());

        assertTrue(sparkIgnitionMonitors.EGR.available);
        assertFalse(sparkIgnitionMonitors.EGR.incomplete);
        assertFalse(sparkIgnitionMonitors.oxygenSensorHeater.available);
        assertFalse(sparkIgnitionMonitors.oxygenSensorHeater.incomplete);
        assertTrue(sparkIgnitionMonitors.oxygenSensor.available);
        assertTrue(sparkIgnitionMonitors.oxygenSensor.incomplete);
        assertFalse(sparkIgnitionMonitors.ACRefrigerant.available);
        assertFalse(sparkIgnitionMonitors.ACRefrigerant.incomplete);
        assertFalse(sparkIgnitionMonitors.secondaryAirSystem.available);
        assertFalse(sparkIgnitionMonitors.secondaryAirSystem.incomplete);
        assertFalse(sparkIgnitionMonitors.evaporativeSystem.available);
        assertFalse(sparkIgnitionMonitors.evaporativeSystem.incomplete);
        assertFalse(sparkIgnitionMonitors.heatedCatalyst.available);
        assertFalse(sparkIgnitionMonitors.heatedCatalyst.incomplete);
        assertFalse(sparkIgnitionMonitors.catalyst.available);
        assertFalse(sparkIgnitionMonitors.catalyst.incomplete);

        mLiveFrameEventBuilder.addIntSensor(Obd2IntegerSensorIndex.IGNITION_MONITORS_SUPPORTED, 1);
        mLiveFrameEventBuilder.addIntSensor(
                Obd2IntegerSensorIndex.IGNITION_SPECIFIC_MONITORS, compressionMonitorsValue);

        timestamp += 1000;
        getMockedVehicleHal().injectEvent(mLiveFrameEventBuilder.build(timestamp));

        assertTrue(listener.waitForEvent(timestamp));

        liveFrame = listener.getLastEvent();
        assertNotNull(liveFrame);
        assertEquals(timestamp, liveFrame.timestamp);

        commonIgnitionMonitors = liveFrame.getIgnitionMonitors();
        assertNotNull(commonIgnitionMonitors);
        assertFalse(commonIgnitionMonitors.components.available);
        assertFalse(commonIgnitionMonitors.components.incomplete);
        assertTrue(commonIgnitionMonitors.fuelSystem.available);
        assertTrue(commonIgnitionMonitors.fuelSystem.incomplete);
        assertFalse(commonIgnitionMonitors.misfire.available);
        assertFalse(commonIgnitionMonitors.misfire.incomplete);
        CompressionIgnitionMonitors compressionIgnitionMonitors =
                commonIgnitionMonitors.asCompressionIgnitionMonitors();
        assertNull(commonIgnitionMonitors.asSparkIgnitionMonitors());
        assertNotNull(compressionIgnitionMonitors);

        assertTrue(compressionIgnitionMonitors.EGROrVVT.available);
        assertFalse(compressionIgnitionMonitors.EGROrVVT.incomplete);
        assertFalse(compressionIgnitionMonitors.PMFilter.available);
        assertFalse(compressionIgnitionMonitors.PMFilter.incomplete);
        assertFalse(compressionIgnitionMonitors.exhaustGasSensor.available);
        assertFalse(compressionIgnitionMonitors.exhaustGasSensor.incomplete);
        assertTrue(compressionIgnitionMonitors.boostPressure.available);
        assertTrue(compressionIgnitionMonitors.boostPressure.incomplete);
        assertFalse(compressionIgnitionMonitors.NOxSCR.available);
        assertFalse(compressionIgnitionMonitors.NOxSCR.incomplete);
        assertFalse(compressionIgnitionMonitors.NMHCCatalyst.available);
        assertFalse(compressionIgnitionMonitors.NMHCCatalyst.incomplete);
    }

    public void testFuelType() throws Exception {
        if (!isFeatureEnabled()) {
            Log.i(TAG, "skipping testFuelType as diagnostics API is not enabled");
            return;
        }

        Listener listener = new Listener();
        mCarDiagnosticManager.registerListener(
                listener,
                CarDiagnosticManager.FRAME_TYPE_FLAG_LIVE,
                android.car.hardware.CarSensorManager.SENSOR_RATE_NORMAL);

        mLiveFrameEventBuilder.addIntSensor(
                Obd2IntegerSensorIndex.FUEL_TYPE, FuelType.BIFUEL_RUNNING_LPG);
        long timestamp = SystemClock.elapsedRealtimeNanos();
        getMockedVehicleHal().injectEvent(mLiveFrameEventBuilder.build(timestamp));

        assertTrue(listener.waitForEvent(timestamp));

        CarDiagnosticEvent liveFrame = listener.getLastEvent();
        assertNotNull(liveFrame);

        assertEquals(
                FuelType.BIFUEL_RUNNING_LPG,
                liveFrame.getSystemIntegerSensor(Obd2IntegerSensorIndex.FUEL_TYPE).intValue());
        assertEquals(FuelType.BIFUEL_RUNNING_LPG, liveFrame.getFuelType().intValue());
    }

    public void testMultipleListeners() throws Exception {
        if (!isFeatureEnabled()) {
            Log.i(TAG, "skipping testMultipleListeners as diagnostics API is not enabled");
            return;
        }

        Listener listener1 = new Listener();
        Listener listener2 = new Listener();

        mCarDiagnosticManager.registerListener(
                listener1,
                CarDiagnosticManager.FRAME_TYPE_FLAG_LIVE,
                android.car.hardware.CarSensorManager.SENSOR_RATE_NORMAL);
        mCarDiagnosticManager.registerListener(
                listener2,
                CarDiagnosticManager.FRAME_TYPE_FLAG_LIVE,
                android.car.hardware.CarSensorManager.SENSOR_RATE_NORMAL);

        listener1.reset();
        listener2.reset();

        long time = SystemClock.elapsedRealtimeNanos();
        getMockedVehicleHal().injectEvent(mLiveFrameEventBuilder.build(time));
        assertTrue(listener1.waitForEvent(time));
        assertTrue(listener2.waitForEvent(time));

        CarDiagnosticEvent event1 = listener1.getLastEvent();
        CarDiagnosticEvent event2 = listener2.getLastEvent();
        assertEquals(
                5000,
                event1.getSystemIntegerSensor(Obd2IntegerSensorIndex.RUNTIME_SINCE_ENGINE_START)
                        .intValue());
        assertEquals(
                5000,
                event2.getSystemIntegerSensor(Obd2IntegerSensorIndex.RUNTIME_SINCE_ENGINE_START)
                        .intValue());

        listener1.reset();
        listener2.reset();

        mCarDiagnosticManager.unregisterListener(listener1);

        time += 1000;
        getMockedVehicleHal().injectEvent(mLiveFrameEventBuilder.build(time));
        assertFalse(listener1.waitForEvent(time));
        assertTrue(listener2.waitForEvent(time));

        assertNull(listener1.getLastEvent());
        event2 = listener2.getLastEvent();

        assertTrue(event1.isEarlierThan(event2));

        assertEquals(
                5000,
                event2.getSystemIntegerSensor(Obd2IntegerSensorIndex.RUNTIME_SINCE_ENGINE_START)
                        .intValue());
    }

    public void testFreezeFrameEvent() throws Exception {
        if (!isFeatureEnabled()) {
            Log.i(TAG, "skipping testFreezeFrameEvent as diagnostics API is not enabled");
            return;
        }

        Listener listener = new Listener();
        mCarDiagnosticManager.registerListener(
                listener,
                CarDiagnosticManager.FRAME_TYPE_FLAG_FREEZE,
                android.car.hardware.CarSensorManager.SENSOR_RATE_NORMAL);

        listener.reset();
        VehiclePropValue injectedEvent =
                mFreezeFrameProperties.addNewEvent(mFreezeFrameEventBuilder);
        getMockedVehicleHal().injectEvent(injectedEvent);
        assertTrue(listener.waitForEvent(injectedEvent.timestamp));

        CarDiagnosticEvent freezeFrame = listener.getLastEvent();

        assertEquals(DTC, freezeFrame.dtc);

        mFreezeFrameEventBuilder.addIntSensor(
                Obd2IntegerSensorIndex.ABSOLUTE_BAROMETRIC_PRESSURE, 22);
        injectedEvent = mFreezeFrameProperties.addNewEvent(mFreezeFrameEventBuilder);
        getMockedVehicleHal().injectEvent(injectedEvent);
        assertTrue(listener.waitForEvent(injectedEvent.timestamp));

        freezeFrame = listener.getLastEvent();

        assertNotNull(freezeFrame);
        assertFalse(freezeFrame.isLiveFrame());
        assertTrue(freezeFrame.isFreezeFrame());
        assertFalse(freezeFrame.isEmptyFrame());

        assertEquals(DTC, freezeFrame.dtc);
        assertEquals(
                22,
                freezeFrame
                        .getSystemIntegerSensor(Obd2IntegerSensorIndex.ABSOLUTE_BAROMETRIC_PRESSURE)
                        .intValue());
    }

    public void testFreezeFrameTimestamps() throws Exception {
        if (!isFeatureEnabled()) {
            Log.i(TAG, "skipping testFreezeFrameTimestamps as diagnostics API is not enabled");
            return;
        }

        Listener listener = new Listener();
        mCarDiagnosticManager.registerListener(
                listener,
                CarDiagnosticManager.FRAME_TYPE_FLAG_FREEZE,
                android.car.hardware.CarSensorManager.SENSOR_RATE_NORMAL);

        Set<Long> generatedTimestamps = new HashSet<>();

        VehiclePropValue injectedEvent =
                mFreezeFrameProperties.addNewEvent(mFreezeFrameEventBuilder);
        getMockedVehicleHal().injectEvent(injectedEvent);
        generatedTimestamps.add(injectedEvent.timestamp);
        assertTrue(listener.waitForEvent(injectedEvent.timestamp));

        injectedEvent =
                mFreezeFrameProperties.addNewEvent(
                        mFreezeFrameEventBuilder, injectedEvent.timestamp + 1000);
        getMockedVehicleHal().injectEvent(injectedEvent);
        generatedTimestamps.add(injectedEvent.timestamp);
        assertTrue(listener.waitForEvent(injectedEvent.timestamp));

        long[] acquiredTimestamps = mCarDiagnosticManager.getFreezeFrameTimestamps();
        assertEquals(generatedTimestamps.size(), acquiredTimestamps.length);
        for (long acquiredTimestamp : acquiredTimestamps) {
            assertTrue(generatedTimestamps.contains(acquiredTimestamp));
        }
    }

    public void testClearFreezeFrameTimestamps() throws Exception {
        if (!isFeatureEnabled()) {
            Log.i(TAG, "skipping testClearFreezeFrameTimestamps as diagnostics API is not enabled");
            return;
        }

        Listener listener = new Listener();
        mCarDiagnosticManager.registerListener(
                listener,
                CarDiagnosticManager.FRAME_TYPE_FLAG_FREEZE,
                android.car.hardware.CarSensorManager.SENSOR_RATE_NORMAL);

        VehiclePropValue injectedEvent =
                mFreezeFrameProperties.addNewEvent(mFreezeFrameEventBuilder);
        getMockedVehicleHal().injectEvent(injectedEvent);
        assertTrue(listener.waitForEvent(injectedEvent.timestamp));

        assertNotNull(mCarDiagnosticManager.getFreezeFrame(injectedEvent.timestamp));
        mCarDiagnosticManager.clearFreezeFrames(injectedEvent.timestamp);
        assertNull(mCarDiagnosticManager.getFreezeFrame(injectedEvent.timestamp));
    }

    class Listener implements CarDiagnosticManager.OnDiagnosticEventListener {
        private final Object mSync = new Object();

        private CarDiagnosticEvent mLastEvent = null;

        CarDiagnosticEvent getLastEvent() {
            return mLastEvent;
        }

        void reset() {
            synchronized (mSync) {
                mLastEvent = null;
            }
        }

        boolean waitForEvent() throws InterruptedException {
            return waitForEvent(0);
        }

        boolean waitForEvent(long eventTimeStamp) throws InterruptedException {
            long start = SystemClock.elapsedRealtime();
            boolean matchTimeStamp = eventTimeStamp != 0;
            synchronized (mSync) {
                while ((mLastEvent == null
                                || (matchTimeStamp && mLastEvent.timestamp != eventTimeStamp))
                        && (start + SHORT_WAIT_TIMEOUT_MS > SystemClock.elapsedRealtime())) {
                    mSync.wait(10L);
                }
                return mLastEvent != null
                        && (!matchTimeStamp || mLastEvent.timestamp == eventTimeStamp);
            }
        }

        @Override
        public void onDiagnosticEvent(CarDiagnosticEvent event) {
            synchronized (mSync) {
                // We're going to hold a reference to this object
                mLastEvent = event;
                mSync.notify();
            }
        }
    }
}
