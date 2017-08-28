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

package com.android.documentsui.picker;

import static com.android.documentsui.base.State.ACTION_CREATE;
import static com.android.documentsui.base.State.ACTION_OPEN;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.provider.DocumentsContract.Root;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import com.android.documentsui.R;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.State;
import com.android.documentsui.testing.TestDirectoryDetails;
import com.android.documentsui.testing.TestMenu;
import com.android.documentsui.testing.TestMenuItem;
import com.android.documentsui.testing.TestSearchViewManager;
import com.android.documentsui.testing.TestSelectionDetails;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public final class MenuManagerTest {

    private TestMenu testMenu;
    private TestMenuItem open;
    private TestMenuItem openInNewWindow;
    private TestMenuItem openWith;
    private TestMenuItem share;
    private TestMenuItem delete;
    private TestMenuItem rename;
    private TestMenuItem selectAll;
    private TestMenuItem createDir;
    private TestMenuItem grid;
    private TestMenuItem list;
    private TestMenuItem cut;
    private TestMenuItem copy;
    private TestMenuItem paste;
    private TestMenuItem pasteInto;
    private TestMenuItem advanced;
    private TestMenuItem settings;
    private TestMenuItem eject;
    private TestMenuItem view;

    private TestSelectionDetails selectionDetails;
    private TestDirectoryDetails dirDetails;
    private TestSearchViewManager testSearchManager;
    private State state = new State();
    private RootInfo testRootInfo;
    private DocumentInfo testDocInfo;
    private MenuManager mgr;

    @Before
    public void setUp() {
        testMenu = TestMenu.create();
        open = testMenu.findItem(R.id.menu_open);
        openInNewWindow = testMenu.findItem(R.id.menu_open_in_new_window);
        openWith = testMenu.findItem(R.id.menu_open_with);
        share = testMenu.findItem(R.id.menu_share);
        delete = testMenu.findItem(R.id.menu_delete);
        rename =  testMenu.findItem(R.id.menu_rename);
        selectAll = testMenu.findItem(R.id.menu_select_all);
        createDir = testMenu.findItem(R.id.menu_create_dir);
        grid = testMenu.findItem(R.id.menu_grid);
        list = testMenu.findItem(R.id.menu_list);
        cut = testMenu.findItem(R.id.menu_cut_to_clipboard);
        copy = testMenu.findItem(R.id.menu_copy_to_clipboard);
        paste = testMenu.findItem(R.id.menu_paste_from_clipboard);
        pasteInto = testMenu.findItem(R.id.menu_paste_into_folder);
        view = testMenu.findItem(R.id.menu_view_in_owner);

        advanced = testMenu.findItem(R.id.menu_advanced);
        settings = testMenu.findItem(R.id.menu_settings);
        eject = testMenu.findItem(R.id.menu_eject_root);

        selectionDetails = new TestSelectionDetails();
        dirDetails = new TestDirectoryDetails();
        testSearchManager = new TestSearchViewManager();
        mgr = new MenuManager(testSearchManager, state, dirDetails);

        testRootInfo = new RootInfo();
        testDocInfo = new DocumentInfo();
        state.action = ACTION_CREATE;
        state.allowMultiple = true;
    }

    @Test
    public void testActionMenu() {
        mgr.updateActionMenu(testMenu, selectionDetails);

        open.assertInvisible();
        delete.assertInvisible();
        share.assertInvisible();
        rename.assertInvisible();
        selectAll.assertVisible();
        view.assertInvisible();
    }

    @Test
    public void testActionMenu_openAction() {
        state.action = ACTION_OPEN;
        mgr.updateActionMenu(testMenu, selectionDetails);

        open.assertVisible();
    }


    @Test
    public void testActionMenu_notAllowMultiple() {
        state.allowMultiple = false;
        mgr.updateActionMenu(testMenu, selectionDetails);

        selectAll.assertInvisible();
    }

    @Test
    public void testOptionMenu() {
        mgr.updateOptionMenu(testMenu);

        advanced.assertInvisible();
        advanced.assertTitle(R.string.menu_advanced_show);
        createDir.assertDisabled();
        assertTrue(testSearchManager.showMenuCalled());
    }

    @Test
    public void testOptionMenu_notPicking() {
        state.action = ACTION_OPEN;
        state.derivedMode = State.MODE_LIST;
        mgr.updateOptionMenu(testMenu);

        createDir.assertInvisible();
        grid.assertVisible();
        list.assertInvisible();
        assertFalse(testSearchManager.showMenuCalled());
    }

    @Test
    public void testOptionMenu_canCreateDirectory() {
        dirDetails.canCreateDirectory = true;
        mgr.updateOptionMenu(testMenu);

        createDir.assertEnabled();
    }

    @Test
    public void testOptionMenu_showAdvanced() {
        state.showAdvanced = true;
        state.showDeviceStorageOption = true;
        mgr.updateOptionMenu(testMenu);

        advanced.assertVisible();
        advanced.assertTitle(R.string.menu_advanced_hide);
    }

    @Test
    public void testOptionMenu_inRecents() {
        dirDetails.isInRecents = true;
        mgr.updateOptionMenu(testMenu);

        grid.assertInvisible();
        list.assertInvisible();
    }

    @Test
    public void testContextMenu_EmptyArea() {
        dirDetails.hasItemsToPaste = false;
        dirDetails.canCreateDoc = false;
        dirDetails.canCreateDirectory = false;

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
    public void testContextMenu_EmptyArea_canPaste() {
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
        mgr.updateContextMenuForFiles(testMenu, selectionDetails);
        // We don't want share in pickers.
        share.assertInvisible();
        // We don't want openWith in pickers.
        openWith.assertInvisible();
        cut.assertVisible();
        copy.assertVisible();
        rename.assertInvisible();
        delete.assertVisible();
    }

    @Test
    public void testContextMenu_OnDirectory() {
        selectionDetails.canPasteInto = true;
        mgr.updateContextMenuForDirs(testMenu, selectionDetails);
        // We don't want openInNewWindow in pickers
        openInNewWindow.assertInvisible();
        cut.assertVisible();
        copy.assertVisible();
        // Doesn't matter if directory is selected, we don't want pasteInto for DocsActivity
        pasteInto.assertInvisible();
        rename.assertInvisible();
        delete.assertVisible();
    }

    @Test
    public void testContextMenu_OnMixedDocs() {
        selectionDetails.containDirectories = true;
        selectionDetails.containFiles = true;
        selectionDetails.size = 2;
        selectionDetails.canDelete = true;
        mgr.updateContextMenu(testMenu, selectionDetails);
        cut.assertVisible();
        copy.assertVisible();
        delete.assertVisible();
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
        mgr.updateRootContextMenu(testMenu, testRootInfo, testDocInfo);

        eject.assertInvisible();
        openInNewWindow.assertInvisible();
        pasteInto.assertInvisible();
        settings.assertInvisible();
    }

    @Test
    public void testRootContextMenu_hasRootSettings() {
        testRootInfo.flags = Root.FLAG_HAS_SETTINGS;
        mgr.updateRootContextMenu(testMenu, testRootInfo, testDocInfo);

        settings.assertInvisible();
    }

    @Test
    public void testRootContextMenu_nonWritableRoot() {
        dirDetails.hasItemsToPaste = true;
        mgr.updateRootContextMenu(testMenu, testRootInfo, testDocInfo);

        pasteInto.assertInvisible();
    }

    @Test
    public void testRootContextMenu_nothingToPaste() {
        testRootInfo.flags = Root.FLAG_SUPPORTS_CREATE;
        dirDetails.hasItemsToPaste = false;
        mgr.updateRootContextMenu(testMenu, testRootInfo, testDocInfo);

        pasteInto.assertInvisible();
    }

    @Test
    public void testRootContextMenu_canEject() {
        testRootInfo.flags = Root.FLAG_SUPPORTS_EJECT;
        mgr.updateRootContextMenu(testMenu, testRootInfo, testDocInfo);

        eject.assertInvisible();
    }
}
