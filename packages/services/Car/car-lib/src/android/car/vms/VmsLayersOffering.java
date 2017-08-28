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
 * The state of dependencies for a single publisher.
 *
 * @hide
 */
@FutureFeature
public final class VmsLayersOffering implements Parcelable {

    private final List<VmsLayerDependency> mDependencies;

    public VmsLayersOffering(List<VmsLayerDependency> dependencies) {
        mDependencies = Collections.unmodifiableList(dependencies);
    }

    /**
     * Returns the dependencies.
     */
    public List<VmsLayerDependency> getDependencies() {
        return mDependencies;
    }

    public static final Parcelable.Creator<VmsLayersOffering> CREATOR = new
        Parcelable.Creator<VmsLayersOffering>() {
            public VmsLayersOffering createFromParcel(Parcel in) {
                return new VmsLayersOffering(in);
            }
            public VmsLayersOffering[] newArray(int size) {
                return new VmsLayersOffering[size];
            }
        };

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeParcelableList(mDependencies, flags);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    private VmsLayersOffering(Parcel in) {
        List<VmsLayerDependency> dependencies = new ArrayList<>();
        in.readParcelableList(dependencies, VmsLayerDependency.class.getClassLoader());
        mDependencies = Collections.unmodifiableList(dependencies);
    }
}