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

package android.car.vms;

import android.car.annotation.FutureFeature;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

/**
 * A VMS Layer which can be subscribed to by VMS clients.
 * Consists of the layer ID and the layer version.
 *
 * @hide
 */
@FutureFeature
public final class VmsLayer implements Parcelable {

    // The layer ID.
    private int mId;

    // The layer version.
    private int mVersion;

    public VmsLayer(int id, int version) {
        mId = id;
        mVersion = version;
    }

    public int getId() {
        return mId;
    }

    public int getVersion() {
        return mVersion;
    }

    /**
     * Checks the two objects for equality by comparing their IDs and Versions.
     *
     * @param o the {@link VmsLayer} to which this one is to be checked for equality
     * @return true if the underlying objects of the VmsLayer are both considered equal
     */
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof VmsLayer)) {
            return false;
        }
        VmsLayer p = (VmsLayer) o;
        return Objects.equals(p.mId, mId) && Objects.equals(p.mVersion, mVersion);
    }

    /**
     * Compute a hash code similarly tp {@link android.util.Pair}
     *
     * @return a hashcode of the Pair
     */
    @Override
    public int hashCode() {
        return Objects.hash(mId, mVersion);
    }

    @Override
    public String toString() {
        return "VmsLayer{" + mId + " " + mVersion + "}";
    }


    // Parcelable related methods.
    public static final Parcelable.Creator<VmsLayer> CREATOR = new
            Parcelable.Creator<VmsLayer>() {
                public VmsLayer createFromParcel(Parcel in) {
                    return new VmsLayer(in);
                }

                public VmsLayer[] newArray(int size) {
                    return new VmsLayer[size];
                }
            };

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(mId);
        out.writeInt(mVersion);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    private VmsLayer(Parcel in) {
        readFromParcel(in);
    }

    private void readFromParcel(Parcel in) {
        mId = in.readInt();
        mVersion = in.readInt();
    }
}