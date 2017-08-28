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

import android.car.annotation.FutureFeature;
import android.car.vms.VmsLayer;
import android.car.vms.VmsLayerDependency;
import android.car.vms.VmsLayersOffering;
import android.util.Log;
import com.android.internal.annotations.GuardedBy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Manages VMS availability for layers.
 *
 * Each VMS publisher sets its layers offering which are a list of layers the publisher claims
 * it might publish. VmsLayersAvailability calculates from all the offering what are the
 * available layers.
 */

@FutureFeature
public class VmsLayersAvailability {

    private static final boolean DBG = true;
    private static final String TAG = "VmsLayersAvailability";

    private final Object mLock = new Object();
    @GuardedBy("mLock")
    private final Map<VmsLayer, Set<Set<VmsLayer>>> mPotentialLayersAndDependencies =
        new HashMap<>();
    @GuardedBy("mLock")
    private final Set<VmsLayer> mCyclicAvoidanceSet = new HashSet<>();
    @GuardedBy("mLock")
    private Set<VmsLayer> mAvailableLayers = Collections.EMPTY_SET;
    @GuardedBy("mLock")
    private Set<VmsLayer> mUnavailableLayers = Collections.EMPTY_SET;

    /**
     * Setting the current layers offerings as reported by publishers.
     */
    public void setPublishersOffering(Collection<VmsLayersOffering> publishersLayersOfferings) {
        synchronized (mLock) {
            reset();

            for (VmsLayersOffering offering : publishersLayersOfferings) {
                for (VmsLayerDependency dependency : offering.getDependencies()) {
                    VmsLayer layer = dependency.getLayer();
                    Set<Set<VmsLayer>> curDependencies =
                        mPotentialLayersAndDependencies.get(layer);
                    if (curDependencies == null) {
                        curDependencies = new HashSet<>();
                        mPotentialLayersAndDependencies.put(layer, curDependencies);
                    }
                    curDependencies.add(dependency.getDependencies());
                }
            }
            calculateLayers();
        }
    }

    /**
     * Returns a collection of all the layers which may be published.
     */
    public Collection<VmsLayer> getAvailableLayers() {
        synchronized (mLock) {
            return mAvailableLayers;
        }
    }

    /**
     * Returns a collection of all the layers which publishers could have published if the
     * dependencies were satisfied.
     */
    public Collection<VmsLayer> getUnavailableLayers() {
        synchronized (mLock) {
            return mUnavailableLayers;
        }
    }

    private void reset() {
        synchronized (mLock) {
            mCyclicAvoidanceSet.clear();
            mPotentialLayersAndDependencies.clear();
            mAvailableLayers = Collections.EMPTY_SET;
            mUnavailableLayers = Collections.EMPTY_SET;
        }
    }

    private void calculateLayers() {
        synchronized (mLock) {
            final Set<VmsLayer> availableLayers = new HashSet<>();

            availableLayers.addAll(
                mPotentialLayersAndDependencies.keySet()
                    .stream()
                    .filter(layer -> isLayerSupportedLocked(layer, availableLayers))
                    .collect(Collectors.toSet()));

            mAvailableLayers = Collections.unmodifiableSet(availableLayers);
            mUnavailableLayers = Collections.unmodifiableSet(
                mPotentialLayersAndDependencies.keySet()
                    .stream()
                    .filter(layer -> !availableLayers.contains(layer))
                    .collect(Collectors.toSet()));
        }
    }

    private boolean isLayerSupportedLocked(VmsLayer layer, Set<VmsLayer> currentAvailableLayers) {
        if (DBG) {
            Log.d(TAG, "isLayerSupported: checking layer: " + layer);
        }
        // If we already know that this layer is supported then we are done.
        if (currentAvailableLayers.contains(layer)) {
            return true;
        }
        // If there is no offering for this layer we're done.
        if (!mPotentialLayersAndDependencies.containsKey(layer)) {
            return false;
        }
        // Avoid cyclic dependency.
        if (mCyclicAvoidanceSet.contains(layer)) {
            Log.e(TAG, "Detected a cyclic dependency: " + mCyclicAvoidanceSet + " -> " + layer);
            return false;
        }
        for (Set<VmsLayer> dependencies : mPotentialLayersAndDependencies.get(layer)) {
            // If layer does not have any dependencies then add to supported.
            if (dependencies == null || dependencies.isEmpty()) {
                currentAvailableLayers.add(layer);
                return true;
            }
            // Add the layer to cyclic avoidance set
            mCyclicAvoidanceSet.add(layer);

            boolean isSupported = true;
            for (VmsLayer dependency : dependencies) {
                if (!isLayerSupportedLocked(dependency, currentAvailableLayers)) {
                    isSupported = false;
                    break;
                }
            }
            mCyclicAvoidanceSet.remove(layer);

            if (isSupported) {
                currentAvailableLayers.add(layer);
                return true;
            }
        }
        return false;
    }
}
