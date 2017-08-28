/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.phone;

import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.UserManager;
import android.provider.SearchIndexableResource;
import android.provider.SearchIndexablesProvider;

import static android.provider.SearchIndexablesContract.COLUMN_INDEX_NON_INDEXABLE_KEYS_KEY_VALUE;
import static android.provider.SearchIndexablesContract.COLUMN_INDEX_XML_RES_RANK;
import static android.provider.SearchIndexablesContract.COLUMN_INDEX_XML_RES_RESID;
import static android.provider.SearchIndexablesContract.COLUMN_INDEX_XML_RES_CLASS_NAME;
import static android.provider.SearchIndexablesContract.COLUMN_INDEX_XML_RES_ICON_RESID;
import static android.provider.SearchIndexablesContract.COLUMN_INDEX_XML_RES_INTENT_ACTION;
import static android.provider.SearchIndexablesContract.COLUMN_INDEX_XML_RES_INTENT_TARGET_PACKAGE;
import static android.provider.SearchIndexablesContract.COLUMN_INDEX_XML_RES_INTENT_TARGET_CLASS;

import static android.provider.SearchIndexablesContract.INDEXABLES_RAW_COLUMNS;
import static android.provider.SearchIndexablesContract.INDEXABLES_XML_RES_COLUMNS;
import static android.provider.SearchIndexablesContract.NON_INDEXABLES_KEYS_COLUMNS;

public class PhoneSearchIndexablesProvider extends SearchIndexablesProvider {
    private static final String TAG = "PhoneSearchIndexablesProvider";

    private static SearchIndexableResource[] INDEXABLE_RES = new SearchIndexableResource[] {
            new SearchIndexableResource(1, R.xml.network_setting_fragment,
                    MobileNetworkSettings.class.getName(),
                    R.mipmap.ic_launcher_phone),
    };

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor queryXmlResources(String[] projection) {
        MatrixCursor cursor = new MatrixCursor(INDEXABLES_XML_RES_COLUMNS);
        final int count = INDEXABLE_RES.length;
        for (int n = 0; n < count; n++) {
            Object[] ref = new Object[7];
            ref[COLUMN_INDEX_XML_RES_RANK] = INDEXABLE_RES[n].rank;
            ref[COLUMN_INDEX_XML_RES_RESID] = INDEXABLE_RES[n].xmlResId;
            ref[COLUMN_INDEX_XML_RES_CLASS_NAME] = null;
            ref[COLUMN_INDEX_XML_RES_ICON_RESID] = INDEXABLE_RES[n].iconResId;
            ref[COLUMN_INDEX_XML_RES_INTENT_ACTION] = "android.intent.action.MAIN";
            ref[COLUMN_INDEX_XML_RES_INTENT_TARGET_PACKAGE] = "com.android.phone";
            ref[COLUMN_INDEX_XML_RES_INTENT_TARGET_CLASS] = INDEXABLE_RES[n].className;
            cursor.addRow(ref);
        }
        return cursor;
    }

    @Override
    public Cursor queryRawData(String[] projection) {
        MatrixCursor cursor = new MatrixCursor(INDEXABLES_RAW_COLUMNS);
        return cursor;
    }

    @Override
    public Cursor queryNonIndexableKeys(String[] projection) {
        MatrixCursor cursor = new MatrixCursor(NON_INDEXABLES_KEYS_COLUMNS);
        final UserManager userManager = (UserManager) getContext().getSystemService(
                Context.USER_SERVICE);
        if (!userManager.isAdminUser()) {
            final String[] values = new String[]{"preferred_network_mode_key", "button_roaming_key",
                    "cdma_lte_data_service_key", "enabled_networks_key", "enhanced_4g_lte",
                    "button_apn_key", "button_carrier_sel_key", "carrier_settings_key",
                    "cdma_system_select_key", "carrier_settings_euicc_key"};
            for (String nik : values) {
                final Object[] ref = new Object[NON_INDEXABLES_KEYS_COLUMNS.length];
                ref[COLUMN_INDEX_NON_INDEXABLE_KEYS_KEY_VALUE] = nik;
                cursor.addRow(ref);
            }
        }
        return cursor;
    }
}
