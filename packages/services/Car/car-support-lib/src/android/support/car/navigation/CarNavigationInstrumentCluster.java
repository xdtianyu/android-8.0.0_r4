/*
 * Copyright (C) 2016 The Android Open Source Project
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
package android.support.car.navigation;

import android.support.annotation.IntDef;
import android.support.annotation.RestrictTo;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import static android.support.annotation.RestrictTo.Scope.GROUP_ID;

/**
 * Holds options related to navigation for the car's instrument cluster.
 */
public class CarNavigationInstrumentCluster {

    /** Navigation Next Turn messages contain an image, as well as an enum. */
    public static final int CLUSTER_TYPE_CUSTOM_IMAGES_SUPPORTED = 1;
    /** Navigation Next Turn messages contain only an enum. */
    public static final int CLUSTER_TYPE_IMAGE_CODES_ONLY = 2;

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
        CLUSTER_TYPE_CUSTOM_IMAGES_SUPPORTED,
        CLUSTER_TYPE_IMAGE_CODES_ONLY
    })
    public @interface ClusterType {}

    private int mMinIntervalMillis;

    @ClusterType
    private int mType;

    private int mImageWidth;

    private int mImageHeight;

    private int mImageColorDepthBits;

    /** @hide */
    @RestrictTo(GROUP_ID)
    public static CarNavigationInstrumentCluster createCluster(int minIntervalMillis) {
        return new CarNavigationInstrumentCluster(minIntervalMillis, CLUSTER_TYPE_IMAGE_CODES_ONLY,
                0, 0, 0);
    }

    /** @hide */
    @RestrictTo(GROUP_ID)
    public static CarNavigationInstrumentCluster createCustomImageCluster(int minIntervalMs,
            int imageWidth, int imageHeight, int imageColorDepthBits) {
        return new CarNavigationInstrumentCluster(minIntervalMs,
                CLUSTER_TYPE_CUSTOM_IMAGES_SUPPORTED,
                imageWidth, imageHeight, imageColorDepthBits);
    }

    /** Minimum time between instrument cluster updates in milliseconds.*/
    public int getMinIntervalMillis() {
        return mMinIntervalMillis;
    }

    /**
     * Type of instrument cluster, can be {@link #CLUSTER_TYPE_CUSTOM_IMAGES_SUPPORTED} or
     * {@link #CLUSTER_TYPE_IMAGE_CODES_ONLY}.
     */
    @ClusterType
    public int getType() {
        return mType;
    }

    /**
     * If instrument cluster is image, width of instrument cluster in pixels.
     * @return the width of the image in pixels, 0 otherwise.
     */
    public int getImageWidth() {
        return mImageWidth;
    }

    /**
     * If instrument cluster is image, height of instrument cluster in pixels.
     * @return the width of the image in pixels, 0 otherwise.
     */
    public int getImageHeight() {
        return mImageHeight;
    }

    /**
     * @return If instrument cluster is image, number of bits of colour depth it supports (8, 16,
     * or  32), 0 otherwise.
     */
    public int getImageColorDepthBits() {
        return mImageColorDepthBits;
    }

    /** @hide */
    @RestrictTo(GROUP_ID)
    public CarNavigationInstrumentCluster(CarNavigationInstrumentCluster that) {
      this(that.mMinIntervalMillis,
          that.mType,
          that.mImageWidth,
          that.mImageHeight,
          that.mImageColorDepthBits);
    }

    /**
     * Whether cluster support custom images or not.  If not generally the cluster will provide
     * its own images.
     * @return True if supported, false otherwise.
     */
    public boolean supportsCustomImages() {
      return mType == CLUSTER_TYPE_CUSTOM_IMAGES_SUPPORTED;
    }

    /** @hide */
    @RestrictTo(GROUP_ID)
    CarNavigationInstrumentCluster(
            int minIntervalMillis,
            @ClusterType int type,
            int imageWidth,
            int imageHeight,
            int imageColorDepthBits) {
        this.mMinIntervalMillis = minIntervalMillis;
        this.mType = type;
        this.mImageWidth = imageWidth;
        this.mImageHeight = imageHeight;
        this.mImageColorDepthBits = imageColorDepthBits;
    }


    /** Converts to string for debug purpose. */
    @Override
    public String toString() {
        return CarNavigationInstrumentCluster.class.getSimpleName() + "{ " +
                "minIntervalMillis: " + mMinIntervalMillis + ", " +
                "type: " + mType + ", " +
                "imageWidth: " + mImageWidth + ", " +
                "imageHeight: " + mImageHeight + ", " +
                "imageColourDepthBits: " + mImageColorDepthBits + " }";
    }
}
