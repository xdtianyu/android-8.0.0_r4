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

package com.android.documentsui.testing;

import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertSame;

import android.content.ClipData;
import android.net.Uri;

import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.DocumentStack;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.clipping.DocumentClipper;
import com.android.documentsui.selection.Selection;
import com.android.documentsui.services.FileOperations.Callback;

import java.util.function.Function;

public class TestDocumentClipper implements DocumentClipper {

    private ClipData mLastClipData;

    @Override
    public boolean hasItemsToPaste() {
        return false;
    }

    @Override
    public ClipData getClipDataForDocuments(Function<String, Uri> uriBuilder, Selection selection,
            int opType) {
        return null;
    }

    @Override
    public void clipDocumentsForCopy(Function<String, Uri> uriBuilder, Selection selection) {

    }

    @Override
    public void clipDocumentsForCut(Function<String, Uri> uriBuilder, Selection selection,
            DocumentInfo parent) {
    }

    @Override
    public void copyFromClipboard(DocumentInfo destination, DocumentStack docStack,
            Callback callback) {
    }

    @Override
    public void copyFromClipboard(DocumentStack docStack, Callback callback) {
    }

    @Override
    public void copyFromClipData(RootInfo root, DocumentInfo destination, ClipData clipData,
            Callback callback) {
        mLastClipData = clipData;
    }

    @Override
    public void copyFromClipData(DocumentInfo destination, DocumentStack docStack,
            ClipData clipData, Callback callback) {
    }

    @Override
    public void copyFromClipData(DocumentStack docStack, ClipData clipData, Callback callback) {
    }

    @Override
    public int getOpType(ClipData data) {
        return 0;
    }

    public void assertNoClipData() {
        assertNull(mLastClipData);
    }

    public void assertSameClipData(ClipData expect) {
        assertSame(expect, mLastClipData);
    }
}
