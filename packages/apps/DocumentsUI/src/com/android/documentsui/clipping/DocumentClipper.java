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

package com.android.documentsui.clipping;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.net.Uri;
import android.support.annotation.Nullable;

import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.DocumentStack;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.selection.Selection;
import com.android.documentsui.services.FileOperationService.OpType;
import com.android.documentsui.services.FileOperations;

import java.util.function.Function;

public interface DocumentClipper {

    static final String OP_JUMBO_SELECTION_SIZE = "jumboSelection-size";
    static final String OP_JUMBO_SELECTION_TAG = "jumboSelection-tag";

    static public DocumentClipper create(Context context, ClipStore clipStore) {
        return new RuntimeDocumentClipper(context, clipStore);
    }

    boolean hasItemsToPaste();
    @OpType int getOpType(ClipData data);

    /**
     * Returns {@link ClipData} representing the selection, or null if selection is empty,
     * or cannot be converted.
     */
    ClipData getClipDataForDocuments(
        Function<String, Uri> uriBuilder, Selection selection, @OpType int opType);

    /**
     * Puts {@code ClipData} in a primary clipboard, describing a copy operation
     */
    void clipDocumentsForCopy(Function<String, Uri> uriBuilder, Selection selection);

    /**
     *  Puts {@Code ClipData} in a primary clipboard, describing a cut operation
     */
    void clipDocumentsForCut(
            Function<String, Uri> uriBuilder, Selection selection, DocumentInfo parent);

    /**
     * Copies documents from clipboard. It's the same as {@link #copyFromClipData} with clipData
     * returned from {@link ClipboardManager#getPrimaryClip()}.
     *
     * @param destination destination document.
     * @param docStack the document stack to the destination folder (not including the destination
     *                 folder)
     * @param callback callback to notify when operation finishes.
     */
    void copyFromClipboard(
            DocumentInfo destination,
            DocumentStack docStack,
            FileOperations.Callback callback);

    /**
     * Copies documents from clipboard. It's the same as {@link #copyFromClipData} with clipData
     * returned from {@link ClipboardManager#getPrimaryClip()}.
     *
     * @param docStack the document stack to the destination folder,
     * @param callback callback to notify when operation finishes.
     */
    void copyFromClipboard(
            DocumentStack docStack,
            FileOperations.Callback callback);

    /**
     * Copied documents from given clip data to a root directory.
     * @param root the root which root directory to copy to
     * @param destination the root directory
     * @param clipData the clipData to copy from
     * @param callback callback to notify when operation finishes
     */
    void copyFromClipData(
            final RootInfo root,
            final DocumentInfo destination,
            final ClipData clipData,
            final FileOperations.Callback callback);

    /**
     * Copies documents from given clip data to a folder.
     *
     * @param destination destination folder
     * @param docStack the document stack to the destination folder (not including the destination
     *                 folder)
     * @param clipData the clipData to copy from
     * @param callback callback to notify when operation finishes
     */
    void copyFromClipData(
            final DocumentInfo destination,
            final DocumentStack docStack,
            final ClipData clipData,
            final FileOperations.Callback callback);

    /**
     * Copies documents from given clip data to a folder.
     *
     * @param dstStack the document stack to the destination folder, including the destination
     *            folder.
     * @param clipData the clipData to copy from
     * @param callback callback to notify when operation finishes
     */
    void copyFromClipData(
            final DocumentStack dstStack,
            final ClipData clipData,
            final FileOperations.Callback callback);
}
