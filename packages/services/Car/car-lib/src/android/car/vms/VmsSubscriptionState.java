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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The list of layers with subscribers.
 *
 * @hide
 */
@FutureFeature
public final class VmsSubscriptionState implements Parcelable {
    private final int mSequenceNumber;
    private final List<VmsLayer> mLayers;

    /**
     * Construct a dependency for layer on other layers.
     */
    public VmsSubscriptionState(int sequenceNumber, List<VmsLayer> dependencies) {
        mSequenceNumber = sequenceNumber;
        mLayers = Collections.unmodifiableList(dependencies);
    }

    public int getSequenceNumber() {
        return mSequenceNumber;
    }

    public List<VmsLayer> getLayers() {
        return mLayers;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("sequence number=").append(mSequenceNumber);
        sb.append("; layers={");
        for(VmsLayer layer : mLayers) {
            sb.append(layer).append(",");
        }
        sb.append("}");
        return sb.toString();
    }

    public static final Parcelable.Creator<VmsSubscriptionState> CREATOR = new
        Parcelable.Creator<VmsSubscriptionState>() {
            public VmsSubscriptionState createFromParcel(Parcel in) {
                return new VmsSubscriptionState(in);
            }
            public VmsSubscriptionState[] newArray(int size) {
                return new VmsSubscriptionState[size];
            }
        };

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(mSequenceNumber);
        out.writeParcelableList(mLayers, flags);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    private VmsSubscriptionState(Parcel in) {
        mSequenceNumber = in.readInt();
        List<VmsLayer> layers = new ArrayList<>();
        in.readParcelableList(layers, VmsLayer.class.getClassLoader());
        mLayers = Collections.unmodifiableList(layers);
    }
}