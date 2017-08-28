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

package com.android.documentsui.archives;

import com.android.documentsui.archives.ArchivesProvider;
import com.android.documentsui.archives.Archive;
import com.android.documentsui.tests.R;

import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract;
import android.support.test.InstrumentationRegistry;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.MediumTest;
import android.text.TextUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;

@MediumTest
public class ArchivesProviderTest extends AndroidTestCase {
    private static final Uri ARCHIVE_URI = Uri.parse("content://i/love/strawberries");
    private static final String NOTIFICATION_URI =
            "content://com.android.documentsui.archives/notification-uri";
    private ExecutorService mExecutor = null;
    private Archive mArchive = null;
    private TestUtils mTestUtils = null;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mExecutor = Executors.newSingleThreadExecutor();
        mTestUtils = new TestUtils(InstrumentationRegistry.getTargetContext(),
                InstrumentationRegistry.getContext(), mExecutor);
    }

    @Override
    public void tearDown() throws Exception {
        mExecutor.shutdown();
        assertTrue(mExecutor.awaitTermination(3 /* timeout */, TimeUnit.SECONDS));
        super.tearDown();
    }

    public void testQueryRoots() throws InterruptedException {
        final ContentResolver resolver = getContext().getContentResolver();
        final Uri rootsUri = DocumentsContract.buildRootsUri(ArchivesProvider.AUTHORITY);
        try (final ContentProviderClient client = resolver.acquireUnstableContentProviderClient(
                rootsUri)) {
            final Cursor cursor = resolver.query(rootsUri, null, null, null, null, null);
            assertNotNull("Cursor must not be null.", cursor);
            assertEquals(0, cursor.getCount());
        }
    }

    public void testOpen_Success() throws InterruptedException {
        final Uri sourceUri = DocumentsContract.buildDocumentUri(
                ResourcesProvider.AUTHORITY, "archive.zip");
        final Uri archiveUri = ArchivesProvider.buildUriForArchive(sourceUri,
                ParcelFileDescriptor.MODE_READ_ONLY);

        final Uri childrenUri = DocumentsContract.buildChildDocumentsUri(
                ArchivesProvider.AUTHORITY, DocumentsContract.getDocumentId(archiveUri));

        final ContentResolver resolver = getContext().getContentResolver();
        final CountDownLatch latch = new CountDownLatch(1);

        final ContentProviderClient client = resolver.acquireUnstableContentProviderClient(
                archiveUri);
        ArchivesProvider.acquireArchive(client, archiveUri);

        {
            final Cursor cursor = resolver.query(childrenUri, null, null, null, null, null);
            assertNotNull("Cursor must not be null. File not found?", cursor);

            assertEquals(0, cursor.getCount());
            final Bundle extras = cursor.getExtras();
            assertEquals(true, extras.getBoolean(DocumentsContract.EXTRA_LOADING, false));
            assertNull(extras.getString(DocumentsContract.EXTRA_ERROR));

            final Uri notificationUri = cursor.getNotificationUri();
            assertNotNull(notificationUri);

            resolver.registerContentObserver(notificationUri, false, new ContentObserver(null) {
                @Override
                public void onChange(boolean selfChange, Uri uri) {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        {
            final Cursor cursor = resolver.query(childrenUri, null, null, null, null, null);
            assertNotNull("Cursor must not be null. File not found?", cursor);

            assertEquals(3, cursor.getCount());
            final Bundle extras = cursor.getExtras();
            assertEquals(false, extras.getBoolean(DocumentsContract.EXTRA_LOADING, false));
            assertNull(extras.getString(DocumentsContract.EXTRA_ERROR));
        }

        ArchivesProvider.releaseArchive(client, archiveUri);
        client.release();
    }

    public void testOpen_Failure() throws InterruptedException {
        final Uri sourceUri = DocumentsContract.buildDocumentUri(
                ResourcesProvider.AUTHORITY, "broken.zip");
        final Uri archiveUri = ArchivesProvider.buildUriForArchive(sourceUri,
                ParcelFileDescriptor.MODE_READ_ONLY);

        final Uri childrenUri = DocumentsContract.buildChildDocumentsUri(
                ArchivesProvider.AUTHORITY, DocumentsContract.getDocumentId(archiveUri));

        final ContentResolver resolver = getContext().getContentResolver();
        final CountDownLatch latch = new CountDownLatch(1);

        final ContentProviderClient client = resolver.acquireUnstableContentProviderClient(
                archiveUri);
        ArchivesProvider.acquireArchive(client, archiveUri);

        {
            // TODO: Close this and any other cursor in this file.
            final Cursor cursor = resolver.query(childrenUri, null, null, null, null, null);
            assertNotNull("Cursor must not be null. File not found?", cursor);

            assertEquals(0, cursor.getCount());
            final Bundle extras = cursor.getExtras();
            assertEquals(true, extras.getBoolean(DocumentsContract.EXTRA_LOADING, false));
            assertNull(extras.getString(DocumentsContract.EXTRA_ERROR));

            final Uri notificationUri = cursor.getNotificationUri();
            assertNotNull(notificationUri);

            resolver.registerContentObserver(notificationUri, false, new ContentObserver(null) {
                @Override
                public void onChange(boolean selfChange, Uri uri) {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        {
            final Cursor cursor = resolver.query(childrenUri, null, null, null, null, null);
            assertNotNull("Cursor must not be null. File not found?", cursor);

            assertEquals(0, cursor.getCount());
            final Bundle extras = cursor.getExtras();
            assertEquals(false, extras.getBoolean(DocumentsContract.EXTRA_LOADING, false));
            assertFalse(TextUtils.isEmpty(extras.getString(DocumentsContract.EXTRA_ERROR)));
        }

        ArchivesProvider.releaseArchive(client, archiveUri);
        client.release();
    }

    public void testOpen_ClosesOnRelease() throws InterruptedException {
        final Uri sourceUri = DocumentsContract.buildDocumentUri(
                ResourcesProvider.AUTHORITY, "broken.zip");
        final Uri archiveUri = ArchivesProvider.buildUriForArchive(sourceUri,
                ParcelFileDescriptor.MODE_READ_ONLY);

        final Uri childrenUri = DocumentsContract.buildChildDocumentsUri(
                ArchivesProvider.AUTHORITY, DocumentsContract.getDocumentId(archiveUri));

        final ContentResolver resolver = getContext().getContentResolver();
        final CountDownLatch latch = new CountDownLatch(1);

        final ContentProviderClient client = resolver.acquireUnstableContentProviderClient(
                archiveUri);

        // Acquire twice to ensure that the refcount works correctly.
        ArchivesProvider.acquireArchive(client, archiveUri);
        ArchivesProvider.acquireArchive(client, archiveUri);

        {
            final Cursor cursor = resolver.query(childrenUri, null, null, null, null, null);
            assertNotNull("Cursor must not be null. File not found?", cursor);
        }

        ArchivesProvider.releaseArchive(client, archiveUri);

        {
            final Cursor cursor = resolver.query(childrenUri, null, null, null, null, null);
            assertNotNull("Cursor must not be null. File not found?", cursor);
        }

        ArchivesProvider.releaseArchive(client, archiveUri);

        try {
            resolver.query(childrenUri, null, null, null, null, null);
            fail("The archive was expected to be invalited on the last release call.");
        } catch (IllegalStateException e) {
            // Expected.
        }

        client.release();
    }
}
