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

package com.android.car;

import android.car.vms.VmsLayer;
import android.car.vms.VmsLayerDependency;
import android.car.vms.VmsLayersOffering;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@SmallTest
public class VmsLayersAvailabilityTest extends AndroidTestCase {

    private static final VmsLayer LAYER_X = new VmsLayer(1, 2);
    private static final VmsLayer LAYER_Y = new VmsLayer(3, 4);
    private static final VmsLayer LAYER_Z = new VmsLayer(5, 6);

    private static final VmsLayerDependency X_DEPENDS_ON_Y =
        new VmsLayerDependency(LAYER_X, new HashSet<VmsLayer>(Arrays.asList(LAYER_Y)));

    private static final VmsLayerDependency X_DEPENDS_ON_Z =
        new VmsLayerDependency(LAYER_X, new HashSet<VmsLayer>(Arrays.asList(LAYER_Z)));

    private static final VmsLayerDependency Y_DEPENDS_ON_Z =
        new VmsLayerDependency(LAYER_Y, new HashSet<VmsLayer>(Arrays.asList(LAYER_Z)));

    private static final VmsLayerDependency Y_DEPENDS_ON_X =
        new VmsLayerDependency(LAYER_Y, new HashSet<VmsLayer>(Arrays.asList(LAYER_X)));

    private static final VmsLayerDependency Z_DEPENDS_ON_X =
        new VmsLayerDependency(LAYER_Z, new HashSet<VmsLayer>(Arrays.asList(LAYER_X)));

    private static final VmsLayerDependency Z_DEPENDS_ON_NOTHING =
        new VmsLayerDependency(LAYER_Z);

    private static final VmsLayerDependency X_DEPENDS_ON_SELF =
        new VmsLayerDependency(LAYER_X, new HashSet<VmsLayer>(Arrays.asList(LAYER_X)));

    private Set<VmsLayersOffering> mOfferings;
    private  VmsLayersAvailability mLayersAvailability;

    @Override
    protected void setUp() throws Exception {
        mLayersAvailability = new VmsLayersAvailability();
        mOfferings = new HashSet<>();
        super.setUp();
    }

    public void testSingleLayerNoDeps() throws Exception {
        Set<VmsLayer> expectedAvailableLayers = new HashSet<>();
        expectedAvailableLayers.add(LAYER_X);

        VmsLayersOffering offering =
            new VmsLayersOffering(Arrays.asList(new VmsLayerDependency(LAYER_X)));

        mOfferings.add(offering);
        mLayersAvailability.setPublishersOffering(mOfferings);

        assertEquals(expectedAvailableLayers, mLayersAvailability.getAvailableLayers());
    }

    public void testChainOfDependenciesSatisfied() throws Exception {
        Set<VmsLayer> expectedAvailableLayers = new HashSet<>();
        expectedAvailableLayers.add(LAYER_X);
        expectedAvailableLayers.add(LAYER_Y);
        expectedAvailableLayers.add(LAYER_Z);

        VmsLayersOffering offering =
            new VmsLayersOffering(Arrays.asList(
                X_DEPENDS_ON_Y,
                Y_DEPENDS_ON_Z,
                Z_DEPENDS_ON_NOTHING));

        mOfferings.add(offering);
        mLayersAvailability.setPublishersOffering(mOfferings);

        assertEquals(expectedAvailableLayers,
            new HashSet<VmsLayer>(mLayersAvailability.getAvailableLayers()));
    }

    public void testChainOfDependenciesSatisfiedTwoOfferings() throws Exception {
        Set<VmsLayer> expectedAvailableLayers = new HashSet<>();
        expectedAvailableLayers.add(LAYER_X);
        expectedAvailableLayers.add(LAYER_Y);
        expectedAvailableLayers.add(LAYER_Z);

        VmsLayersOffering offering1 =
            new VmsLayersOffering(Arrays.asList(
                X_DEPENDS_ON_Y,
                Y_DEPENDS_ON_Z));

        VmsLayersOffering offering2 =
            new VmsLayersOffering(Arrays.asList(
                Z_DEPENDS_ON_NOTHING));

        mOfferings.add(offering1);
        mOfferings.add(offering2);
        mLayersAvailability.setPublishersOffering(mOfferings);

        assertEquals(expectedAvailableLayers,
            new HashSet<VmsLayer>(mLayersAvailability.getAvailableLayers()));
    }

    public void testChainOfDependencieNotSatisfied() throws Exception {
        Set<VmsLayer> expectedAvailableLayers = new HashSet<>();
        VmsLayersOffering offering =new VmsLayersOffering(Arrays.asList(
            X_DEPENDS_ON_Y,
            Y_DEPENDS_ON_Z));

        mOfferings.add(offering);
        mLayersAvailability.setPublishersOffering(mOfferings);

        assertEquals(expectedAvailableLayers,
            new HashSet<VmsLayer>(mLayersAvailability.getAvailableLayers()));

        Set<VmsLayer> expectedUnavailableLayers = new HashSet<>();
        expectedUnavailableLayers.add(LAYER_X);
        expectedUnavailableLayers.add(LAYER_Y);

        assertEquals(expectedUnavailableLayers ,
            new HashSet<VmsLayer>(mLayersAvailability.getUnavailableLayers()));
    }

    public void testOneOfMultipleDependencySatisfied() throws Exception {
        Set<VmsLayer> expectedAvailableLayers = new HashSet<>();
        expectedAvailableLayers.add(LAYER_X);
        expectedAvailableLayers.add(LAYER_Z);

        VmsLayersOffering offering =
            new VmsLayersOffering(Arrays.asList(
                X_DEPENDS_ON_Y,
                X_DEPENDS_ON_Z,
                Z_DEPENDS_ON_NOTHING));

        mOfferings.add(offering);
        mLayersAvailability.setPublishersOffering(mOfferings);

        assertEquals(expectedAvailableLayers,
            new HashSet<VmsLayer>(mLayersAvailability.getAvailableLayers()));
    }

    public void testCyclicDependency() throws Exception {
        Set<VmsLayer> expectedAvailableLayers = new HashSet<>();

        VmsLayersOffering offering =
            new VmsLayersOffering(Arrays.asList(
                X_DEPENDS_ON_Y,
                Y_DEPENDS_ON_Z,
                Z_DEPENDS_ON_X));

        mOfferings.add(offering);
        mLayersAvailability.setPublishersOffering(mOfferings);

        assertEquals(expectedAvailableLayers,
            new HashSet<VmsLayer>(mLayersAvailability.getAvailableLayers()));
    }

    public void testAlmostCyclicDependency() throws Exception {
        Set<VmsLayer> expectedAvailableLayers = new HashSet<>();
        expectedAvailableLayers.add(LAYER_X);
        expectedAvailableLayers.add(LAYER_Y);
        expectedAvailableLayers.add(LAYER_Z);

        VmsLayersOffering offering1 =
            new VmsLayersOffering(Arrays.asList(
                X_DEPENDS_ON_Y,
                Z_DEPENDS_ON_NOTHING));

        VmsLayersOffering offering2 =
            new VmsLayersOffering(Arrays.asList(
                Y_DEPENDS_ON_Z,
                Z_DEPENDS_ON_X));

        mOfferings.add(offering1);
        mOfferings.add(offering2);
        mLayersAvailability.setPublishersOffering(mOfferings);

        assertEquals(expectedAvailableLayers,
            new HashSet<VmsLayer>(mLayersAvailability.getAvailableLayers()));
    }

    public void testCyclicDependencyAndLayerWithoutDependency() throws Exception {
        Set<VmsLayer> expectedAvailableLayers = new HashSet<>();
        expectedAvailableLayers.add(LAYER_Z);

        VmsLayersOffering offering1 =
            new VmsLayersOffering(Arrays.asList(
                X_DEPENDS_ON_Y,
                Z_DEPENDS_ON_NOTHING));

        VmsLayersOffering offering2 =
            new VmsLayersOffering(Arrays.asList(
                Y_DEPENDS_ON_X));

        mOfferings.add(offering1);
        mOfferings.add(offering2);
        mLayersAvailability.setPublishersOffering(mOfferings);

        assertEquals(expectedAvailableLayers,
            new HashSet<VmsLayer>(mLayersAvailability.getAvailableLayers()));


        Set<VmsLayer> expectedUnavailableLayers = new HashSet<>();
        expectedUnavailableLayers.add(LAYER_Y);
        expectedUnavailableLayers.add(LAYER_X);

        assertEquals(expectedUnavailableLayers,
            new HashSet<VmsLayer>(mLayersAvailability.getUnavailableLayers()));
    }

    public void testSelfDependency() throws Exception {
        Set<VmsLayer> expectedAvailableLayers = new HashSet<>();

        VmsLayersOffering offering =
            new VmsLayersOffering(Arrays.asList(
                X_DEPENDS_ON_SELF));

        mOfferings.add(offering);
        mLayersAvailability.setPublishersOffering(mOfferings);

        assertEquals(expectedAvailableLayers,
            new HashSet<VmsLayer>(mLayersAvailability.getAvailableLayers()));

        Set<VmsLayer> expectedUnavailableLayers = new HashSet<>();
        expectedUnavailableLayers.add(LAYER_X);

        assertEquals(expectedUnavailableLayers,
            new HashSet<VmsLayer>(mLayersAvailability.getUnavailableLayers()));
    }
}