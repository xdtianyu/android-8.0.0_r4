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

package com.android.documentsui.files;

import static com.android.documentsui.testing.IntentAsserts.assertHasAction;
import static com.android.documentsui.testing.IntentAsserts.assertHasExtraIntent;
import static com.android.documentsui.testing.IntentAsserts.assertHasExtraList;
import static com.android.documentsui.testing.IntentAsserts.assertHasExtraUri;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Parcelable;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Path;
import android.support.test.filters.MediumTest;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.view.DragEvent;

import com.android.documentsui.AbstractActionHandler;
import com.android.documentsui.R;
import com.android.documentsui.TestActionModeAddons;
import com.android.documentsui.archives.ArchivesProvider;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.DocumentStack;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.Shared;
import com.android.documentsui.testing.ClipDatas;
import com.android.documentsui.testing.DocumentStackAsserts;
import com.android.documentsui.testing.Roots;
import com.android.documentsui.testing.TestActivityConfig;
import com.android.documentsui.testing.TestConfirmationCallback;
import com.android.documentsui.testing.TestDocumentClipper;
import com.android.documentsui.testing.TestEnv;
import com.android.documentsui.testing.TestProvidersAccess;
import com.android.documentsui.ui.TestDialogController;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(AndroidJUnit4.class)
@MediumTest
public class ActionHandlerTest {

    private TestEnv mEnv;
    private TestActivity mActivity;
    private TestActionModeAddons mActionModeAddons;
    private TestDialogController mDialogs;
    private TestConfirmationCallback mCallback;
    private ActionHandler<TestActivity> mHandler;
    private TestDocumentClipper mClipper;
    private boolean refreshAnswer = false;

    @Before
    public void setUp() {
        mEnv = TestEnv.create();
        mActivity = TestActivity.create(mEnv);
        mActionModeAddons = new TestActionModeAddons();
        mDialogs = new TestDialogController();
        mCallback = new TestConfirmationCallback();
        mEnv.roots.configurePm(mActivity.packageMgr);
        ((TestActivityConfig) mEnv.injector.config).nextDocumentEnabled = true;
        mEnv.injector.dialogs = mDialogs;
        mClipper = new TestDocumentClipper();

        mHandler = createHandler();

        mDialogs.confirmNext();

        mEnv.selectDocument(TestEnv.FILE_GIF);
    }

    @Test
    public void testOpenSelectedInNewWindow() {
        mHandler.openSelectedInNewWindow();

        DocumentStack path = new DocumentStack(Roots.create("123"), mEnv.model.getDocument("1"));

        Intent expected = LauncherActivity.createLaunchIntent(mActivity);
        expected.putExtra(Shared.EXTRA_STACK, (Parcelable) path);

        Intent actual = mActivity.startActivity.getLastValue();
        assertEquals(expected.toString(), actual.toString());
    }

    @Test
    public void testSpringOpenDirectory() {
        mHandler.springOpenDirectory(TestEnv.FOLDER_0);
        assertTrue(mActionModeAddons.finishActionModeCalled);
        assertEquals(TestEnv.FOLDER_0, mEnv.state.stack.peek());
    }

    @Test
    public void testCutSelectedDocuments_NoGivenSelection() {
        mEnv.populateStack();

        mEnv.selectionMgr.clearSelection();
        mHandler.cutToClipboard();
        mDialogs.assertDocumentsClippedNotShown();
    }

    @Test
    public void testCopySelectedDocuments_NoGivenSelection() {
        mEnv.populateStack();

        mEnv.selectionMgr.clearSelection();
        mHandler.copyToClipboard();
        mDialogs.assertDocumentsClippedNotShown();
    }

    @Test
    public void testDeleteSelectedDocuments_NoSelection() {
        mEnv.populateStack();

        mEnv.selectionMgr.clearSelection();
        mHandler.deleteSelectedDocuments();
        mDialogs.assertNoFileFailures();
        mActivity.startService.assertNotCalled();
        mActionModeAddons.finishOnConfirmed.assertNeverCalled();
    }

    @Test
    public void testDeleteSelectedDocuments_Cancelable() {
        mEnv.populateStack();

        mDialogs.rejectNext();
        mHandler.deleteSelectedDocuments();
        mDialogs.assertNoFileFailures();
        mActivity.startService.assertNotCalled();
        mActionModeAddons.finishOnConfirmed.assertRejected();
    }

    // Recents root means when deleting the srcParent will be null.
    @Test
    public void testDeleteSelectedDocuments_RecentsRoot() {
        mEnv.state.stack.changeRoot(TestProvidersAccess.RECENTS);

        mHandler.deleteSelectedDocuments();
        mDialogs.assertNoFileFailures();
        mActivity.startService.assertCalled();
        mActionModeAddons.finishOnConfirmed.assertCalled();
    }

    @Test
    public void testShareSelectedDocuments_ShowsChooser() {
        mActivity.resources.strings.put(R.string.share_via, "Sharezilla!");
        mHandler.shareSelectedDocuments();

        mActivity.assertActivityStarted(Intent.ACTION_CHOOSER);
    }

    @Test
    public void testShareSelectedDocuments_Single() {
        mActivity.resources.strings.put(R.string.share_via, "Sharezilla!");
        mHandler.shareSelectedDocuments();

        Intent intent = assertHasExtraIntent(mActivity.startActivity.getLastValue());
        assertHasAction(intent, Intent.ACTION_SEND);
        assertFalse(intent.hasCategory(Intent.CATEGORY_TYPED_OPENABLE));
        assertFalse(intent.hasCategory(Intent.CATEGORY_OPENABLE));
        assertHasExtraUri(intent, Intent.EXTRA_STREAM);
    }

    @Test
    public void testShareSelectedDocuments_ArchivedFile() {
        mEnv = TestEnv.create(ArchivesProvider.AUTHORITY);
        mHandler = createHandler();

        mActivity.resources.strings.put(R.string.share_via, "Sharezilla!");
        mEnv.selectionMgr.clearSelection();
        mEnv.selectDocument(TestEnv.FILE_PDF);
        mHandler.shareSelectedDocuments();

        Intent intent = mActivity.startActivity.getLastValue();
        assertNull(intent);
    }

    @Test
    public void testShareSelectedDocuments_Multiple() {
        mActivity.resources.strings.put(R.string.share_via, "Sharezilla!");
        mEnv.selectDocument(TestEnv.FILE_PDF);
        mHandler.shareSelectedDocuments();

        Intent intent = assertHasExtraIntent(mActivity.startActivity.getLastValue());
        assertHasAction(intent, Intent.ACTION_SEND_MULTIPLE);
        assertFalse(intent.hasCategory(Intent.CATEGORY_TYPED_OPENABLE));
        assertFalse(intent.hasCategory(Intent.CATEGORY_OPENABLE));
        assertHasExtraList(intent, Intent.EXTRA_STREAM, 2);
    }

    @Test
    public void testShareSelectedDocuments_VirtualFiles() {
        mActivity.resources.strings.put(R.string.share_via, "Sharezilla!");
        mEnv.selectionMgr.clearSelection();
        mEnv.selectDocument(TestEnv.FILE_VIRTUAL);
        mHandler.shareSelectedDocuments();

        Intent intent = assertHasExtraIntent(mActivity.startActivity.getLastValue());
        assertHasAction(intent, Intent.ACTION_SEND);
        assertTrue(intent.hasCategory(Intent.CATEGORY_TYPED_OPENABLE));
        assertFalse(intent.hasCategory(Intent.CATEGORY_OPENABLE));
        assertHasExtraUri(intent, Intent.EXTRA_STREAM);
    }

    @Test
    public void testShareSelectedDocuments_RegularAndVirtualFiles() {
        mActivity.resources.strings.put(R.string.share_via, "Sharezilla!");
        mEnv.selectDocument(TestEnv.FILE_PNG);
        mEnv.selectDocument(TestEnv.FILE_VIRTUAL);
        mHandler.shareSelectedDocuments();

        Intent intent = assertHasExtraIntent(mActivity.startActivity.getLastValue());
        assertHasAction(intent, Intent.ACTION_SEND_MULTIPLE);
        assertTrue(intent.hasCategory(Intent.CATEGORY_TYPED_OPENABLE));
        assertFalse(intent.hasCategory(Intent.CATEGORY_OPENABLE));
        assertHasExtraList(intent, Intent.EXTRA_STREAM, 3);
    }

    @Test
    public void testShareSelectedDocuments_OmitsPartialFiles() {
        mActivity.resources.strings.put(R.string.share_via, "Sharezilla!");
        mEnv.selectDocument(TestEnv.FILE_PARTIAL);
        mEnv.selectDocument(TestEnv.FILE_PNG);
        mHandler.shareSelectedDocuments();

        Intent intent = assertHasExtraIntent(mActivity.startActivity.getLastValue());
        assertHasAction(intent, Intent.ACTION_SEND_MULTIPLE);
        assertFalse(intent.hasCategory(Intent.CATEGORY_TYPED_OPENABLE));
        assertFalse(intent.hasCategory(Intent.CATEGORY_OPENABLE));
        assertHasExtraList(intent, Intent.EXTRA_STREAM, 2);
    }

    @Test
    public void testDocumentPicked_DefaultsToView() throws Exception {
        mActivity.currentRoot = TestProvidersAccess.HOME;

        mHandler.openDocument(TestEnv.FILE_GIF, ActionHandler.VIEW_TYPE_PREVIEW,
                ActionHandler.VIEW_TYPE_REGULAR);
        mActivity.assertActivityStarted(Intent.ACTION_VIEW);
    }

    @Test
    public void testDocumentPicked_InArchive_QuickViewable() throws Exception {
        mActivity.resources.setQuickViewerPackage("corptropolis.viewer");
        mActivity.currentRoot = TestProvidersAccess.HOME;

        mHandler.openDocument(TestEnv.FILE_IN_ARCHIVE, ActionHandler.VIEW_TYPE_PREVIEW,
                ActionHandler.VIEW_TYPE_REGULAR);
        mActivity.assertActivityStarted(Intent.ACTION_QUICK_VIEW);
    }

    @Test
    public void testDocumentPicked_InArchive_Unopenable() throws Exception {
        mActivity.currentRoot = TestProvidersAccess.HOME;

        mHandler.openDocument(TestEnv.FILE_IN_ARCHIVE, ActionHandler.VIEW_TYPE_PREVIEW,
                ActionHandler.VIEW_TYPE_REGULAR);
        mDialogs.assertViewInArchivesShownUnsupported();
    }

    @Test
    public void testDocumentPicked_PreviewsWhenResourceSet() throws Exception {
        mActivity.resources.setQuickViewerPackage("corptropolis.viewer");
        mActivity.currentRoot = TestProvidersAccess.HOME;

        mHandler.openDocument(TestEnv.FILE_GIF, ActionHandler.VIEW_TYPE_PREVIEW,
                ActionHandler.VIEW_TYPE_REGULAR);
        mActivity.assertActivityStarted(Intent.ACTION_QUICK_VIEW);
    }

    @Test
    public void testDocumentPicked_Downloads_ManagesApks() throws Exception {
        mActivity.currentRoot = TestProvidersAccess.DOWNLOADS;

        mHandler.openDocument(TestEnv.FILE_APK, ActionHandler.VIEW_TYPE_PREVIEW,
                ActionHandler.VIEW_TYPE_REGULAR);
        mActivity.assertActivityStarted(DocumentsContract.ACTION_MANAGE_DOCUMENT);
    }

    @Test
    public void testDocumentPicked_Downloads_ManagesPartialFiles() throws Exception {
        mActivity.currentRoot = TestProvidersAccess.DOWNLOADS;

        mHandler.openDocument(TestEnv.FILE_PARTIAL, ActionHandler.VIEW_TYPE_PREVIEW,
                ActionHandler.VIEW_TYPE_REGULAR);
        mActivity.assertActivityStarted(DocumentsContract.ACTION_MANAGE_DOCUMENT);
    }

    @Test
    public void testDocumentPicked_OpensArchives() throws Exception {
        mActivity.currentRoot = TestProvidersAccess.HOME;
        mEnv.docs.nextDocument = TestEnv.FILE_ARCHIVE;

        mHandler.openDocument(TestEnv.FILE_ARCHIVE, ActionHandler.VIEW_TYPE_PREVIEW,
                ActionHandler.VIEW_TYPE_REGULAR);
        assertEquals(TestEnv.FILE_ARCHIVE, mEnv.state.stack.peek());
    }

    @Test
    public void testDocumentPicked_OpensDirectories() throws Exception {
        mActivity.currentRoot = TestProvidersAccess.HOME;

        mHandler.openDocument(TestEnv.FOLDER_1, ActionHandler.VIEW_TYPE_PREVIEW,
                ActionHandler.VIEW_TYPE_REGULAR);
        assertEquals(TestEnv.FOLDER_1, mEnv.state.stack.peek());
    }

    @Test
    public void testShowChooser() throws Exception {
        mActivity.currentRoot = TestProvidersAccess.DOWNLOADS;

        mHandler.showChooserForDoc(TestEnv.FILE_PDF);
        mActivity.assertActivityStarted(Intent.ACTION_CHOOSER);
    }

    @Test
    public void testInitLocation_DefaultsToDownloads() throws Exception {
        mActivity.resources.bools.put(R.bool.show_documents_root, false);

        mHandler.initLocation(mActivity.getIntent());
        assertRootPicked(TestProvidersAccess.DOWNLOADS.getUri());
    }

    @Test
    public void testInitLocation_DocumentsRootEnabled() throws Exception {
        mActivity.resources.bools.put(R.bool.show_documents_root, true);

        mHandler.initLocation(mActivity.getIntent());
        assertRootPicked(TestProvidersAccess.HOME.getUri());
    }

    @Test
    public void testInitLocation_BrowseRoot() throws Exception {
        Intent intent = mActivity.getIntent();
        intent.setAction(Intent.ACTION_VIEW);
        intent.setData(TestProvidersAccess.PICKLES.getUri());

        mHandler.initLocation(intent);
        assertRootPicked(TestProvidersAccess.PICKLES.getUri());
    }

    @Test
    public void testInitLocation_LaunchToDocuments() throws Exception {
        mEnv.docs.nextIsDocumentsUri = true;
        mEnv.docs.nextPath = new Path(
                TestProvidersAccess.HOME.rootId,
                Arrays.asList(
                        TestEnv.FOLDER_0.documentId,
                        TestEnv.FOLDER_1.documentId));
        mEnv.docs.nextDocuments =
                Arrays.asList(TestEnv.FOLDER_0, TestEnv.FOLDER_1);

        mActivity.refreshCurrentRootAndDirectory.assertNotCalled();
        Intent intent = mActivity.getIntent();
        intent.setAction(Intent.ACTION_VIEW);
        intent.setData(TestEnv.FOLDER_1.derivedUri);
        mHandler.initLocation(intent);

        mEnv.beforeAsserts();

        DocumentStackAsserts.assertEqualsTo(mEnv.state.stack, TestProvidersAccess.HOME,
                Arrays.asList(TestEnv.FOLDER_0, TestEnv.FOLDER_1));
        mActivity.refreshCurrentRootAndDirectory.assertCalled();
    }

    @Test
    public void testDragAndDrop_OnReadOnlyRoot() throws Exception {
        RootInfo root = new RootInfo(); // root by default has no SUPPORT_CREATE flag
        DragEvent event = DragEvent.obtain(DragEvent.ACTION_DROP, 1, 1, null, null, null,
                null, true);
        assertFalse(mHandler.dropOn(event, root));
    }

    @Test
    public void testDragAndDrop_OnLibraryRoot() throws Exception {
        DragEvent event = DragEvent.obtain(DragEvent.ACTION_DROP, 1, 1, null, null, null,
                null, true);
        assertFalse(mHandler.dropOn(event, TestProvidersAccess.RECENTS));
    }

    @Test
    public void testClipper_suppliedCorrectClipData() throws Exception {
        // DragEvent gets recycled in Android, so it is possible that by the time the callback is
        // called, event.getLocalState() and event.getClipData() returns null. This tests to ensure
        // our Clipper is getting the original CipData passed in.
        mEnv.docs.nextRootDocument = TestEnv.FOLDER_0;
        mHandler = new ActionHandler<>(
                mActivity,
                mEnv.state,
                mEnv.roots,
                mEnv.docs,
                mEnv.searchViewManager,
                mEnv::lookupExecutor,
                mActionModeAddons,
                mClipper,
                null,
                mEnv.injector
        );
        Object localState = new Object();
        ClipData clipData = ClipDatas.createTestClipData();
        DragEvent event = DragEvent.obtain(DragEvent.ACTION_DROP, 1, 1, localState, null, clipData,
                null, true);
        assertSame(localState, event.getLocalState());
        assertSame(clipData, event.getClipData());

        mHandler.dropOn(event, TestProvidersAccess.DOWNLOADS);
        event.recycle();

        mEnv.beforeAsserts();

        mClipper.assertSameClipData(clipData);
    }

    @Test
    public void testClipper_notCalledIfDestInSelection() throws Exception {
        mHandler = new ActionHandler<>(
                mActivity,
                mEnv.state,
                mEnv.roots,
                mEnv.docs,
                mEnv.searchViewManager,
                mEnv::lookupExecutor,
                mActionModeAddons,
                mClipper,
                null,
                mEnv.injector
        );
        List<DocumentInfo> localState = new ArrayList<>();
        localState.add(mEnv.docs.getRootDocument(TestProvidersAccess.DOWNLOADS));
        ClipData clipData = ClipDatas.createTestClipData();
        DragEvent event = DragEvent.obtain(DragEvent.ACTION_DROP, 1, 1, localState, null, clipData,
                null, true);

        mHandler.dropOn(event, TestProvidersAccess.DOWNLOADS);

        mEnv.beforeAsserts();

        mClipper.assertNoClipData();
    }

    @Test
    public void testRefresh_nullUri() throws Exception {
        refreshAnswer = true;
        mHandler.refreshDocument(null, (boolean answer) -> {
            refreshAnswer = answer;
        });

        mEnv.beforeAsserts();
        assertFalse(refreshAnswer);
    }

    @Test
    public void testRefresh_emptyStack() throws Exception {
        refreshAnswer = true;
        assertTrue(mEnv.state.stack.isEmpty());
        mHandler.refreshDocument(new DocumentInfo(), (boolean answer) -> {
            refreshAnswer = answer;
        });

        mEnv.beforeAsserts();
        assertFalse(refreshAnswer);
    }

    @Test
    public void testRefresh() throws Exception {
        refreshAnswer = false;
        mEnv.populateStack();
        mHandler.refreshDocument(mEnv.model.getDocument("1"), (boolean answer) -> {
            refreshAnswer = answer;
        });

        mEnv.beforeAsserts();
        assertTrue(refreshAnswer);
    }

    @Test
    public void testAuthentication() throws Exception {
        PendingIntent intent = PendingIntent.getActivity(
                InstrumentationRegistry.getInstrumentation().getTargetContext(), 0, new Intent(),
                0);

        mHandler.startAuthentication(intent);
        assertEquals(intent.getIntentSender(), mActivity.startIntentSender.getLastValue().first);
        assertEquals(AbstractActionHandler.CODE_AUTHENTICATION,
                mActivity.startIntentSender.getLastValue().second.intValue());
    }

    @Test
    public void testOnActivityResult_onOK() throws Exception {
        mHandler.onActivityResult(AbstractActionHandler.CODE_AUTHENTICATION, Activity.RESULT_OK,
                null);
        mActivity.refreshCurrentRootAndDirectory.assertCalled();
    }

    @Test
    public void testOnActivityResult_onNotOK() throws Exception {
        mHandler.onActivityResult(0, Activity.RESULT_OK, null);
        mActivity.refreshCurrentRootAndDirectory.assertNotCalled();

        mHandler.onActivityResult(AbstractActionHandler.CODE_AUTHENTICATION,
                Activity.RESULT_CANCELED, null);
        mActivity.refreshCurrentRootAndDirectory.assertNotCalled();
    }

    private void assertRootPicked(Uri expectedUri) throws Exception {
        mEnv.beforeAsserts();

        mActivity.rootPicked.assertCalled();
        RootInfo root = mActivity.rootPicked.getLastValue();
        assertNotNull(root);
        assertEquals(expectedUri, root.getUri());
    }

    private ActionHandler<TestActivity> createHandler() {
        return new ActionHandler<>(
                mActivity,
                mEnv.state,
                mEnv.roots,
                mEnv.docs,
                mEnv.searchViewManager,
                mEnv::lookupExecutor,
                mActionModeAddons,
                new TestDocumentClipper(),
                null,  // clip storage, not utilized unless we venture into *jumbo* clip territory.
                mEnv.injector
        );
    }
}
