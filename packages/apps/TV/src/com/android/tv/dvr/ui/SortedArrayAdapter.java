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

package com.android.tv.dvr.ui;

import android.support.annotation.VisibleForTesting;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.PresenterSelector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Keeps a set of items sorted
 *
 * <p>{@code T} must have stable IDs.
 */
public abstract class SortedArrayAdapter<T> extends ArrayObjectAdapter {
    private final Comparator<T> mComparator;
    private final int mMaxItemCount;
    private int mExtraItemCount;

    SortedArrayAdapter(PresenterSelector presenterSelector, Comparator<T> comparator) {
        this(presenterSelector, comparator, Integer.MAX_VALUE);
    }

    SortedArrayAdapter(PresenterSelector presenterSelector, Comparator<T> comparator,
            int maxItemCount) {
        super(presenterSelector);
        mComparator = comparator;
        mMaxItemCount = maxItemCount;
    }

    /**
     * Sets the objects in the given collection to the adapter keeping the elements sorted.
     *
     * @param items A {@link Collection} of items to be set.
     */
    @VisibleForTesting
    final void setInitialItems(List<T> items) {
        List<T> itemsCopy = new ArrayList<>(items);
        Collections.sort(itemsCopy, mComparator);
        addAll(0, itemsCopy.subList(0, Math.min(mMaxItemCount, itemsCopy.size())));
    }

    /**
     * Adds an item in sorted order to the adapter.
     *
     * @param item The item to add in sorted order to the adapter.
     */
    @Override
    public final void add(Object item) {
        add((T) item, false);
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    /**
     * Adds an item in sorted order to the adapter.
     *
     * @param item The item to add in sorted order to the adapter.
     * @param insertToEnd If items are inserted in a more or less sorted fashion,
     *                    sets this parameter to {@code true} to search insertion position from
     *                    the end to save search time.
     */
    public final void add(T item, boolean insertToEnd) {
        int i;
        if (insertToEnd) {
            i = findInsertPosition(item);
        } else {
            i = findInsertPositionBinary(item);
        }
        super.add(i, item);
        if (size() > mMaxItemCount + mExtraItemCount) {
            removeItems(mMaxItemCount, size() - mMaxItemCount - mExtraItemCount);
        }
    }

    /**
     * Adds an extra item to the end of the adapter. The items will not be subjected to the sorted
     * order or the maximum number of items. One or more extra items can be added to the adapter.
     * They will be presented in their insertion order.
     */
    public int addExtraItem(T item) {
        super.add(item);
        return ++mExtraItemCount;
    }

    /**
     * Removes an item which has the same ID as {@code item}.
     */
    public boolean removeWithId(T item) {
        int index = indexWithTypeAndId(item);
        return index >= 0 && index < size() && remove(get(index));
    }

    /**
     * Change an item in the list.
     * @param item The item to change.
     */
    public final void change(T item) {
        int oldIndex = indexWithTypeAndId(item);
        if (oldIndex != -1) {
            T old = (T) get(oldIndex);
            if (mComparator.compare(old, item) == 0) {
                replace(oldIndex, item);
                return;
            }
            removeItems(oldIndex, 1);
        }
        add(item);
    }

    /**
     * Returns the id of the the given {@code item}, which will be used in {@link #change} to
     * decide if the given item is already existed in the adapter.
     *
     * The id must be stable.
     */
    abstract long getId(T item);

    private int indexWithTypeAndId(T item) {
        long id = getId(item);
        for (int i = 0; i < size() - mExtraItemCount; i++) {
            T r = (T) get(i);
            if (r.getClass() == item.getClass() && getId(r) == id) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Finds the position that the given item should be inserted to keep the sorted order.
     */
    public int findInsertPosition(T item) {
        for (int i = size() - mExtraItemCount - 1; i >=0; i--) {
            T r = (T) get(i);
            if (mComparator.compare(r, item) <= 0) {
                return i + 1;
            }
        }
        return 0;
    }

    private int findInsertPositionBinary(T item) {
        int lb = 0;
        int ub = size() - mExtraItemCount - 1;
        while (lb <= ub) {
            int mid = (lb + ub) / 2;
            T r = (T) get(mid);
            int compareResult = mComparator.compare(item, r);
            if (compareResult == 0) {
                return mid;
            } else if (compareResult > 0) {
                lb = mid + 1;
            } else {
                ub = mid - 1;
            }
        }
        return lb;
    }
}