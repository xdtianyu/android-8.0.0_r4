/*
 * Copyright (c) 2017 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You may
 * obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.vts.entity;

import com.android.vts.proto.VtsReportMessage.AndroidDeviceInfoMessage;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.Key;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Class describing a device used for a test run. */
public class DeviceInfoEntity implements DashboardEntity {
    protected static final Logger logger = Logger.getLogger(DeviceInfoEntity.class.getName());

    public static final String KIND = "DeviceInfo";

    // Property keys
    public static final String TEST_RUN_TIMESTAMP = "testRunTimestamp";
    public static final String BRANCH = "branch";
    public static final String PRODUCT = "product";
    public static final String BUILD_FLAVOR = "buildFlavor";
    public static final String BUILD_ID = "buildId";
    public static final String ABI_BITNESS = "abiBitness";
    public static final String ABI_NAME = "abiName";

    private final Key parentKey;

    public final String branch;
    public final String product;
    public final String buildFlavor;
    public final String buildId;
    public final String abiBitness;
    public final String abiName;

    /**
     * Create a DeviceInfoEntity object.
     *
     * @param parentKey The key for the parent TestRunEntity object in the database.
     * @param branch The build branch.
     * @param product The device product.
     * @param buildFlavor The device build flavor.
     * @param buildID The device build ID.
     * @param abiBitness The abi bitness of the device.
     * @param abiName The name of the abi.
     */
    public DeviceInfoEntity(Key parentKey, String branch, String product, String buildFlavor,
            String buildID, String abiBitness, String abiName) {
        this.parentKey = parentKey;
        this.branch = branch;
        this.product = product;
        this.buildFlavor = buildFlavor;
        this.buildId = buildID;
        this.abiBitness = abiBitness;
        this.abiName = abiName;
    }

    @Override
    public Entity toEntity() {
        Entity testCaseRunEntity = new Entity(KIND, this.parentKey);
        testCaseRunEntity.setProperty(BRANCH, this.branch.toLowerCase());
        testCaseRunEntity.setProperty(PRODUCT, this.product.toLowerCase());
        testCaseRunEntity.setProperty(BUILD_FLAVOR, this.buildFlavor.toLowerCase());
        testCaseRunEntity.setProperty(BUILD_ID, this.buildId.toLowerCase());
        testCaseRunEntity.setUnindexedProperty(ABI_BITNESS, this.abiBitness.toLowerCase());
        testCaseRunEntity.setUnindexedProperty(ABI_NAME, this.abiName.toLowerCase());

        return testCaseRunEntity;
    }

    /**
     * Convert an Entity object to a DeviceInfoEntity.
     *
     * @param e The entity to process.
     * @return DeviceInfoEntity object with the properties from e, or null if incompatible.
     */
    public static DeviceInfoEntity fromEntity(Entity e) {
        if (!e.getKind().equals(KIND) || !e.hasProperty(BRANCH) || !e.hasProperty(PRODUCT)
                || !e.hasProperty(BUILD_FLAVOR) || !e.hasProperty(BUILD_ID)
                || !e.hasProperty(ABI_BITNESS) || !e.hasProperty(ABI_NAME)) {
            logger.log(Level.WARNING, "Missing device info attributes in entity: " + e.toString());
            return null;
        }
        try {
            Key parentKey = e.getKey().getParent();
            String branch = (String) e.getProperty(BRANCH);
            String product = (String) e.getProperty(PRODUCT);
            String buildFlavor = (String) e.getProperty(BUILD_FLAVOR);
            String buildId = (String) e.getProperty(BUILD_ID);
            String abiBitness = (String) e.getProperty(ABI_BITNESS);
            String abiName = (String) e.getProperty(ABI_NAME);
            return new DeviceInfoEntity(
                    parentKey, branch, product, buildFlavor, buildId, abiBitness, abiName);
        } catch (ClassCastException exception) {
            // Invalid cast
            logger.log(Level.WARNING, "Error parsing device info entity.", exception);
        }
        return null;
    }

    /**
     * Convert a device info message to a DeviceInfoEntity.
     *
     * @param parentKey The ancestor key for the device entity.
     * @param device The device info report describing the target Android device.
     * @return The DeviceInfoEntity for the target device, or null if incompatible
     */
    public static DeviceInfoEntity fromDeviceInfoMessage(
            Key parentKey, AndroidDeviceInfoMessage device) {
        if (!device.hasBuildAlias() || !device.hasBuildFlavor() || !device.hasProductVariant()
                || !device.hasBuildId()) {
            return null;
        }
        String branch = device.getBuildAlias().toStringUtf8();
        String buildFlavor = device.getBuildFlavor().toStringUtf8();
        String product = device.getProductVariant().toStringUtf8();
        String buildId = device.getBuildId().toStringUtf8();
        String abiBitness = device.getAbiBitness().toStringUtf8();
        String abiName = device.getAbiName().toStringUtf8();
        return new DeviceInfoEntity(
                parentKey, branch, product, buildFlavor, buildId, abiBitness, abiName);
    }
}
