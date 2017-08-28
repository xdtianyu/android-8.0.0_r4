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

import static junit.framework.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.net.Uri;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Root;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.test.AndroidTestCase;

import com.android.documentsui.R;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.State;
import com.android.documentsui.dirlist.TestContext;
import com.android.documentsui.dirlist.TestData;
import com.android.documentsui.selection.SelectionManager;
import com.android.documentsui.testing.SelectionManagers;
import com.android.documentsui.testing.TestDirectoryDetails;
import com.android.documentsui.testing.TestEnv;
import com.android.documentsui.testing.TestFeatures;
import com.android.documentsui.testing.TestMenu;
import com.android.documentsui.testing.TestMenuInflater;
import com.android.documentsui.testing.TestMenuItem;
import com.android.documentsui.testing.TestScopedPreferences;
import com.android.documentsui.testing.TestSearchViewManager;
import com.android.documentsui.testing.TestSelectionDetails;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public final class MenuManagerTest {

    private TestMenu testMenu;
    private TestMenuItem rename;
    private TestMenuItem selectAll;
    private TestMenuItem moveTo;
    private TestMenuItem copyTo;
    private TestMenuItem compress;
    private TestMenuItem extractTo;
    private TestMenuItem share;
    private TestMenuItem delete;
    private TestMenuItem createDir;
    private TestMenuItem settings;
    private TestMenuItem newWindow;
    private TestMenuItem open;
    private TestMenuItem openWith;
    private TestMenuItem openInNewWindow;
    private TestMenuItem cut;
    private TestMenuItem copy;
    private TestMenuItem paste;
    private TestMenuItem pasteInto;
    private TestMenuItem advanced;
    private TestMenuItem eject;
    private TestMenuItem view;
    private TestMenuItem debug;

    private TestFeatures features;
    private TestSelectionDetails selectionDetails;
    private TestDirectoryDetails dirDetails;
    private TestSearchViewManager testSearchManager;
    private TestScopedPreferences preferences;
    private RootInfo testRootInfo;
    private DocumentInfo testDocInfo;
    private State state = new State();
    private MenuManager mgr;
    private TestActivity activity = TestActivity.create(TestEnv.create());
    private SelectionManager selectionManager;

    @Before
    public void setUp() {
        testMenu = TestMenu.create();
        rename = testMenu.findItem(R.id.menu_rename);
        selectAll = testMenu.findItem(R.id.menu_select_all);
        moveTo = testMenu.findItem(R.id.menu_move_to);
        copyTo = testMenu.findItem(R.id.menu_copy_to);
        compress = testMenu.findItem(R.id.menu_compress);
        extractTo = testMenu.findItem(R.id.menu_extract_to);
        share = testMenu.findItem(R.id.menu_share);
        delete = testMenu.findItem(R.id.menu_delete);
        createDir = testMenu.findItem(R.id.menu_create_dir);
        settings = testMenu.findItem(R.id.menu_settings);
        newWindow = testMenu.findItem(R.id.menu_new_window);
        open = testMenu.findItem(R.id.menu_open);
        openWith = testMenu.findItem(R.id.menu_open_with);
        openInNewWindow = testMenu.findItem(R.id.menu_open_in_new_window);
        cut = testMenu.findItem(R.id.menu_cut_to_clipboard);
        copy = testMenu.findItem(R.id.menu_copy_to_clipboard);
        paste = testMenu.findItem(R.id.menu_paste_from_clipboard);
        pasteInto = testMenu.findItem(R.id.menu_paste_into_folder);
        advanced = testMenu.findItem(R.id.menu_advanced);
        eject = testMenu.findItem(R.id.menu_eject_root);
        view = testMenu.findItem(R.id.menu_view_in_owner);
        debug = testMenu.findItem(R.id.menu_debug);

        features = new TestFeatures();

        // These items by default are visible
        testMenu.findItem(R.id.menu_select_all).setVisible(true);
        testMenu.findItem(R.id.menu_list).setVisible(true);

        selectionDetails = new TestSelectionDetails();
        dirDetails = new TestDirectoryDetails();
        testSearchManager = new TestSearchViewManager();
        preferences = new TestScopedPreferences();
        selectionManager = SelectionManagers.createTestInstance(TestData.create(1));
        selectionManager.toggleSelection("0");

        mgr = new MenuManager(
                features,
                testSearchManager,
                state,
                dirDetails,
                activity,
                selectionManager,
                this::getApplicationNameFromAuthority,
                this::getUriFromModelId);

        testRootInfo = new RootInfo();
        testDocInfo = new DocumentInfo();
    }

    private Uri getUriFromModelId(String id) {
        return Uri.EMPTY;
    }
    private String getApplicationNameFromAuthority(String authority) {
        return "TestApp";
    }

    @Test
    public void testActionMenu() {
        selectionDetails.canDelete = true;
        selectionDetails.canRename = true;
        dirDetails.canCreateDoc = true;

        mgr.updateActionMenu(testMenu, selectionDetails);

        rename.assertEnabled();
        delete.assertVisible();
        share.assertVisible();
        copyTo.assertEnabled();
        compress.assertEnabled();
        extractTo.assertInvisible();
        moveTo.assertEnabled();
        view.assertInvisible();
    }

    @Test
    public void testActionMenu_ContainsPartial() {
        selectionDetails.containPartial = true;
        dirDetails.canCreateDoc = true;
        mgr.updateActionMenu(testMenu, selectionDetails);

        rename.assertDisabled();
        share.assertInvisible();
        copyTo.assertDisabled();
        compress.assertDisabled();
        extractTo.assertDisabled();
        moveTo.assertDisabled();
        view.assertInvisible();
    }

    @Test
    public void testActionMenu_CreateArchives_ReflectsFeatureState() {
        features.archiveCreation = false;
        dirDetails.canCreateDoc = true;
        mgr.updateActionMenu(testMenu, selectionDetails);

        compress.assertInvisible();
        compress.assertDisabled();
    }

    @Test
    public void testActionMenu_CreateArchive() {
        dirDetails.canCreateDoc = true;
        mgr.updateActionMenu(testMenu, selectionDetails);

        compress.assertEnabled();
    }

    @Test
    public void testActionMenu_NoCreateArchive() {
        dirDetails.canCreateDoc = false;
        mgr.updateActionMenu(testMenu, selectionDetails);

        compress.assertDisabled();
    }

    @Test
    public void testActionMenu_cantRename() {
        selectionDetails.canRename = false;
        mgr.updateActionMenu(testMenu, selectionDetails);

        rename.assertDisabled();
    }

    @Test
    public void testActionMenu_cantDelete() {
        selectionDetails.canDelete = false;
        mgr.updateActionMenu(testMenu, selectionDetails);

        delete.assertInvisible();
        // We shouldn't be able to move files if we can't delete them
        moveTo.assertDisabled();
    }

    @Test
    public void testActionsMenu_canViewInOwner() {
        selectionDetails.canViewInOwner = true;
        mgr.updateActionMenu(testMenu, selectionDetails);

        view.assertVisible();
    }

    @Test
    public void testActionMenu_changeToCanDelete() {
        selectionDetails.canDelete = false;
        mgr.updateActionMenu(testMenu, selectionDetails);

        selectionDetails.canDelete = true;
        mgr.updateActionMenu(testMenu, selectionDetails);

        delete.assertVisible();
        delete.assertEnabled();
        moveTo.assertVisible();
        moveTo.assertEnabled();
    }

    @Test
    public void testActionMenu_ContainsDirectory() {
        selectionDetails.containDirectories = true;
        mgr.updateActionMenu(testMenu, selectionDetails);

        // We can't share directories
        share.assertInvisible();
    }

    @Test
    public void testActionMenu_RemovesDirectory() {
        selectionDetails.containDirectories = true;
        mgr.updateActionMenu(testMenu, selectionDetails);

        selectionDetails.containDirectories = false;
        mgr.updateActionMenu(testMenu, selectionDetails);

        share.assertVisible();
        share.assertEnabled();
    }

    @Test
    public void testActionMenu_CantExtract() {
        selectionDetails.canExtract = false;
        mgr.updateActionMenu(testMenu, selectionDetails);

        extractTo.assertInvisible();
    }

    @Test
    public void testActionMenu_CanExtract_hidesCopyToAndCompressAndShare() {
        features.archiveCreation = true;
        selectionDetails.canExtract = true;
        dirDetails.canCreateDoc = true;
        mgr.updateActionMenu(testMenu, selectionDetails);

        extractTo.assertEnabled();
        copyTo.assertDisabled();
        compress.assertDisabled();
    }

    @Test
    public void testActionMenu_CanOpenWith() {
        selectionDetails.canOpenWith = true;
        mgr.updateActionMenu(testMenu, selectionDetails);

        openWith.assertVisible();
        openWith.assertEnabled();
    }

    @Test
    public void testActionMenu_NoOpenWith() {
        selectionDetails.canOpenWith = false;
        mgr.updateActionMenu(testMenu, selectionDetails);

        openWith.assertVisible();
        openWith.assertDisabled();
    }

    @Test
    public void testOptionMenu() {
        mgr.updateOptionMenu(testMenu);

        advanced.assertInvisible();
        advanced.assertTitle(R.string.menu_advanced_show);
        createDir.assertDisabled();
        debug.assertInvisible();
        assertTrue(testSearchManager.updateMenuCalled());
    }

    @Test
    public void testOptionMenu_ShowAdvanced() {
        state.showAdvanced = true;
        state.showDeviceStorageOption = true;
        mgr.updateOptionMenu(testMenu);

        advanced.assertVisible();
        advanced.assertTitle(R.string.menu_advanced_hide);
    }

    @Test
    public void testOptionMenu_CanCreateDirectory() {
        dirDetails.canCreateDirectory = true;
        mgr.updateOptionMenu(testMenu);

        createDir.assertEnabled();
    }

    @Test
    public void testOptionMenu_HasRootSettings() {
        dirDetails.hasRootSettings = true;
        mgr.updateOptionMenu(testMenu);

        settings.assertVisible();
    }

    @Test
    public void testInflateContextMenu_Files() {
        TestMenuInflater inflater = new TestMenuInflater();

        selectionDetails.containFiles = true;
        selectionDetails.containDirectories = false;
        mgr.inflateContextMenuForDocs(testMenu, inflater, selectionDetails);

        assertEquals(R.menu.file_context_menu, inflater.lastInflatedMenuId);
    }

    @Test
    public void testInflateContextMenu_Dirs() {
        TestMenuInflater inflater = new TestMenuInflater();

        selectionDetails.containFiles = false;
        selectionDetails.containDirectories = true;
        mgr.inflateContextMenuForDocs(testMenu, inflater, selectionDetails);

        assertEquals(R.menu.dir_context_menu, inflater.lastInflatedMenuId);
    }

    @Test
    public void testInflateContextMenu_Mixed() {
        TestMenuInflater inflater = new TestMenuInflater();

        selectionDetails.containFiles = true;
        selectionDetails.containDirectories = true;
        mgr.inflateContextMenuForDocs(testMenu, inflater, selectionDetails);

        assertEquals(R.menu.mixed_context_menu, inflater.lastInflatedMenuId);
    }

    @Test
    public void testContextMenu_EmptyArea() {
        mgr.updateContextMenuForContainer(testMenu);

        selectAll.assertVisible();
        selectAll.assertEnabled();
        paste.assertVisible();
        paste.assertDisabled();
        createDir.assertVisible();
        createDir.assertDisabled();
    }

    @Test
    public void testContextMenu_EmptyArea_NoItemToPaste() {
        dirDetails.hasItemsToPaste = false;
        dirDetails.canCreateDoc = true;

        mgr.updateContextMenuForContainer(testMenu);

        selectAll.assertVisible();
        selectAll.assertEnabled();
        paste.assertVisible();
        paste.assertDisabled();
        createDir.assertVisible();
        createDir.assertDisabled();
    }

    @Test
    public void testContextMenu_EmptyArea_CantCreateDoc() {
        dirDetails.hasItemsToPaste = true;
        dirDetails.canCreateDoc = false;

        mgr.updateContextMenuForContainer(testMenu);

        selectAll.assertVisible();
        selectAll.assertEnabled();
        paste.assertVisible();
        paste.assertDisabled();
        createDir.assertVisible();
        createDir.assertDisabled();
    }

    @Test
    public void testContextMenu_EmptyArea_CanPaste() {
        dirDetails.hasItemsToPaste = true;
        dirDetails.canCreateDoc = true;

        mgr.updateContextMenuForContainer(testMenu);

        selectAll.assertVisible();
        selectAll.assertEnabled();
        paste.assertVisible();
        paste.assertEnabled();
        createDir.assertVisible();
        createDir.assertDisabled();
    }

    @Test
    public void testContextMenu_EmptyArea_CanCreateDirectory() {
        dirDetails.canCreateDirectory = true;

        mgr.updateContextMenuForContainer(testMenu);

        selectAll.assertVisible();
        selectAll.assertEnabled();
        paste.assertVisible();
        paste.assertDisabled();
        createDir.assertVisible();
        createDir.assertEnabled();
    }

    @Test
    public void testContextMenu_OnFile() {
        selectionDetails.size = 1;
        mgr.updateContextMenuForFiles(testMenu, selectionDetails);
        open.assertVisible();
        open.assertEnabled();
        cut.assertVisible();
        copy.assertVisible();
        rename.assertVisible();
        createDir.assertVisible();
        delete.assertVisible();
    }

    @Test
    public void testContextMenu_OnFile_CanOpenWith() {
        selectionDetails.canOpenWith = true;
        mgr.updateContextMenuForFiles(testMenu, selectionDetails);
        openWith.assertVisible();
        openWith.assertEnabled();
    }

    @Test
    public void testContextMenu_OnFile_NoOpenWith() {
        selectionDetails.canOpenWith = false;
        mgr.updateContextMenuForFiles(testMenu, selectionDetails);
        openWith.assertVisible();
        openWith.assertDisabled();
    }

    @Test
    public void testContextMenu_OnMultipleFiles() {
        selectionDetails.size = 3;
        mgr.updateContextMenuForFiles(testMenu, selectionDetails);
        open.assertVisible();
        open.assertDisabled();
    }

    @Test
    public void testContextMenu_OnWritableDirectory() {
        selectionDetails.size = 1;
        selectionDetails.canPasteInto = true;
        dirDetails.hasItemsToPaste = true;
        mgr.updateContextMenuForDirs(testMenu, selectionDetails);
        openInNewWindow.assertVisible();
        openInNewWindow.assertEnabled();
        cut.assertVisible();
        copy.assertVisible();
        pasteInto.assertVisible();
        pasteInto.assertEnabled();
        rename.assertVisible();
        delete.assertVisible();
    }

    @Test
    public void testContextMenu_OnNonWritableDirectory() {
        selectionDetails.size = 1;
        selectionDetails.canPasteInto = false;
        mgr.updateContextMenuForDirs(testMenu, selectionDetails);
        openInNewWindow.assertVisible();
        openInNewWindow.assertEnabled();
        cut.assertVisible();
        copy.assertVisible();
        pasteInto.assertVisible();
        pasteInto.assertDisabled();
        rename.assertVisible();
        delete.assertVisible();
    }

    @Test
    public void testContextMenu_OnWritableDirectory_NothingToPaste() {
        selectionDetails.canPasteInto = true;
        selectionDetails.size = 1;
        dirDetails.hasItemsToPaste = false;
        mgr.updateContextMenuForDirs(testMenu, selectionDetails);
        pasteInto.assertVisible();
        pasteInto.assertDisabled();
    }

    @Test
    public void testContextMenu_OnMultipleDirectories() {
        selectionDetails.size = 3;
        mgr.updateContextMenuForDirs(testMenu, selectionDetails);
        openInNewWindow.assertVisible();
        openInNewWindow.assertDisabled();
    }

    @Test
    public void testContextMenu_OnMixedDocs() {
        selectionDetails.containDirectories = true;
        selectionDetails.containFiles = true;
        selectionDetails.size = 2;
        selectionDetails.canDelete = true;
        mgr.updateContextMenu(testMenu, selectionDetails);
        cut.assertVisible();
        cut.assertEnabled();
        copy.assertVisible();
        copy.assertEnabled();
        delete.assertVisible();
        delete.assertEnabled();
    }

    @Test
    public void testContextMenu_OnMixedDocs_hasPartialFile() {
        selectionDetails.containDirectories = true;
        selectionDetails.containFiles = true;
        selectionDetails.size = 2;
        selectionDetails.containPartial = true;
        selectionDetails.canDelete = true;
        mgr.updateContextMenu(testMenu, selectionDetails);
        cut.assertVisible();
        cut.assertDisabled();
        copy.assertVisible();
        copy.assertDisabled();
        delete.assertVisible();
        delete.assertEnabled();
    }

    @Test
    public void testContextMenu_OnMixedDocs_hasUndeletableFile() {
        selectionDetails.containDirectories = true;
        selectionDetails.containFiles = true;
        selectionDetails.size = 2;
        selectionDetails.canDelete = false;
        mgr.updateContextMenu(testMenu, selectionDetails);
        cut.assertVisible();
        cut.assertDisabled();
        copy.assertVisible();
        copy.assertEnabled();
        delete.assertVisible();
        delete.assertDisabled();
    }

    @Test
    public void testRootContextMenu() {
        testRootInfo.flags = Root.FLAG_SUPPORTS_CREATE;

        mgr.updateRootContextMenu(testMenu, testRootInfo, testDocInfo);

        eject.assertInvisible();

        openInNewWindow.assertVisible();
        openInNewWindow.assertEnabled();

        pasteInto.assertVisible();
        pasteInto.assertDisabled();

        settings.assertVisible();
        settings.assertDisabled();
    }

    @Test
    public void testRootContextMenu_HasRootSettings() {
        testRootInfo.flags = Root.FLAG_HAS_SETTINGS;
        mgr.updateRootContextMenu(testMenu, testRootInfo, testDocInfo);

        settings.assertEnabled();
    }

    @Test
    public void testRootContextMenu_NonWritableRoot() {
        dirDetails.hasItemsToPaste = true;
        mgr.updateRootContextMenu(testMenu, testRootInfo, testDocInfo);

        pasteInto.assertVisible();
        pasteInto.assertDisabled();
    }

    @Test
    public void testRootContextMenu_NothingToPaste() {
        testRootInfo.flags = Root.FLAG_SUPPORTS_CREATE;
        testDocInfo.flags = Document.FLAG_DIR_SUPPORTS_CREATE;
        dirDetails.hasItemsToPaste = false;
        mgr.updateRootContextMenu(testMenu, testRootInfo, testDocInfo);

        pasteInto.assertVisible();
        pasteInto.assertDisabled();
    }

    @Test
    public void testRootContextMenu_PasteIntoWritableRoot() {
        testRootInfo.flags = Root.FLAG_SUPPORTS_CREATE;
        testDocInfo.flags = Document.FLAG_DIR_SUPPORTS_CREATE;
        dirDetails.hasItemsToPaste = true;
        mgr.updateRootContextMenu(testMenu, testRootInfo, testDocInfo);

        pasteInto.assertVisible();
        pasteInto.assertEnabled();
    }

    @Test
    public void testRootContextMenu_Eject() {
        testRootInfo.flags = Root.FLAG_SUPPORTS_EJECT;
        mgr.updateRootContextMenu(testMenu, testRootInfo, testDocInfo);

        eject.assertEnabled();
    }

    @Test
    public void testRootContextMenu_EjectInProcess() {
        testRootInfo.flags = Root.FLAG_SUPPORTS_EJECT;
        testRootInfo.ejecting = true;
        mgr.updateRootContextMenu(testMenu, testRootInfo, testDocInfo);

        eject.assertDisabled();
    }
}
