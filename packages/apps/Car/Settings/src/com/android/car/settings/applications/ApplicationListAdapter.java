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
 * limitations under the License
 */
package com.android.car.settings.applications;

import android.annotation.NonNull;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.support.car.ui.PagedListView;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import com.android.car.settings.R;
import java.util.Collections;
import java.util.List;

/**
 * Renders {@link android.content.pm.ApplicationInfo} to a view to be displayed as a row in a list.
 */
public class ApplicationListAdapter
        extends RecyclerView.Adapter<ApplicationListAdapter.ViewHolder>
        implements PagedListView.ItemCap {
    private static final String TAG = "ApplicationListAdapter";

    private final Context mContext;
    private final PackageManager mPm;

    private List<ResolveInfo> mResolveInfos;

    public ApplicationListAdapter(@NonNull Context context, PackageManager pm) {
        mContext = context;
        mPm = pm;
        Intent intent= new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        mResolveInfos = pm.queryIntentActivities(intent,
                PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
                        | PackageManager.MATCH_DISABLED_COMPONENTS);

        Collections.sort(mResolveInfos, new ResolveInfo.DisplayNameComparator(mPm));
    }

    public boolean isEmpty() {
        return mResolveInfos.isEmpty();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        private final ImageView mIcon;
        private final TextView mTitle;

        public ViewHolder(View view) {
            super(view);
            mTitle = (TextView) view.findViewById(R.id.title);
            mIcon = (ImageView) view.findViewById(R.id.icon);
        }
    }

    private class ApplicationInfoClickListener implements OnClickListener {
        private final int mPosition;

        public ApplicationInfoClickListener(int position) {
            mPosition = position;
        }

        @Override
        public void onClick(View v) {
            ResolveInfo resolveInfo = mResolveInfos.get(mPosition);
            Intent intent = new Intent(mContext, ApplicationDetailActivity.class);
            intent.putExtra(
                ApplicationDetailActivity.APPLICATION_INFO_KEY, resolveInfo);
            mContext.startActivity(intent);
        }
    };

    @Override
    public ApplicationListAdapter.ViewHolder onCreateViewHolder(ViewGroup parent,
            int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_item, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        ResolveInfo resolveInfo = mResolveInfos.get(position);
        holder.mTitle.setText(resolveInfo.loadLabel(mPm));
        holder.mIcon.setImageDrawable(resolveInfo.loadIcon(mPm));
        holder.itemView.setOnClickListener(new ApplicationInfoClickListener(position));
    }

    @Override
    public int getItemCount() {
        return mResolveInfos.size();
    }

    @Override
    public void setMaxItems(int maxItems) {
        // no limit in this list.
    }
}
