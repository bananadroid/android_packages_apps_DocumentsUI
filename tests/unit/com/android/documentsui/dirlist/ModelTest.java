/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.documentsui.dirlist;

import android.content.ContentResolver;
import android.content.ContextWrapper;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.MergeCursor;
import android.provider.DocumentsContract.Document;
import android.test.AndroidTestCase;
import android.test.mock.MockContentResolver;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.documentsui.DirectoryResult;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.Shared;
import com.android.documentsui.roots.RootCursorWrapper;
import com.android.documentsui.sorting.SortDimension;
import com.android.documentsui.sorting.SortModel;
import com.android.documentsui.testing.SortModels;
import com.android.documentsui.testing.TestEventListener;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

@SmallTest
public class ModelTest extends AndroidTestCase {

    private static final int ITEM_COUNT = 10;
    private static final String AUTHORITY = "test_authority";

    private static final String[] COLUMNS = new String[]{
        RootCursorWrapper.COLUMN_AUTHORITY,
        Document.COLUMN_DOCUMENT_ID,
        Document.COLUMN_FLAGS,
        Document.COLUMN_DISPLAY_NAME,
        Document.COLUMN_SIZE,
        Document.COLUMN_LAST_MODIFIED,
        Document.COLUMN_MIME_TYPE
    };

    private static final String[] NAMES = new String[] {
            "4",
            "foo",
            "1",
            "bar",
            "*(Ljifl;a",
            "0",
            "baz",
            "2",
            "3",
            "%$%VD"
        };

    private Cursor cursor;
    private Model model;
    private TestContentProvider provider;

    @Override
    public void setUp() {
        setupTestContext();

        Random rand = new Random();

        MatrixCursor c = new MatrixCursor(COLUMNS);
        for (int i = 0; i < ITEM_COUNT; ++i) {
            MatrixCursor.RowBuilder row = c.newRow();
            row.add(RootCursorWrapper.COLUMN_AUTHORITY, AUTHORITY);
            row.add(Document.COLUMN_DOCUMENT_ID, Integer.toString(i));
            row.add(Document.COLUMN_FLAGS, Document.FLAG_SUPPORTS_DELETE);
            // Generate random document names and sizes. This forces the model's internal sort code
            // to actually do something.
            row.add(Document.COLUMN_DISPLAY_NAME, NAMES[i]);
            row.add(Document.COLUMN_SIZE, rand.nextInt());
        }
        cursor = c;

        DirectoryResult r = new DirectoryResult();
        r.cursor = cursor;

        // Instantiate the model with a dummy view adapter and listener that (for now) do nothing.
        model = new Model();
        // not sure why we add a listener here at all.
        model.addUpdateListener(new TestEventListener<>());
        model.update(r);
    }

    // Tests that the item count is correct.
    public void testItemCount() {
        assertEquals(ITEM_COUNT, model.getItemCount());
    }

    // Tests multiple authorities with clashing document IDs.
    public void testModelIdIsUnique() {
        MatrixCursor cIn1 = new MatrixCursor(COLUMNS);
        MatrixCursor cIn2 = new MatrixCursor(COLUMNS);

        // Make two sets of items with the same IDs, under different authorities.
        final String AUTHORITY0 = "auth0";
        final String AUTHORITY1 = "auth1";

        for (int i = 0; i < ITEM_COUNT; ++i) {
            MatrixCursor.RowBuilder row0 = cIn1.newRow();
            row0.add(RootCursorWrapper.COLUMN_AUTHORITY, AUTHORITY0);
            row0.add(Document.COLUMN_DOCUMENT_ID, Integer.toString(i));

            MatrixCursor.RowBuilder row1 = cIn2.newRow();
            row1.add(RootCursorWrapper.COLUMN_AUTHORITY, AUTHORITY1);
            row1.add(Document.COLUMN_DOCUMENT_ID, Integer.toString(i));
        }

        Cursor cIn = new MergeCursor(new Cursor[] { cIn1, cIn2 });

        // Update the model, then make sure it contains all the expected items.
        DirectoryResult r = new DirectoryResult();
        r.cursor = cIn;
        model.update(r);

        assertEquals(ITEM_COUNT * 2, model.getItemCount());
        BitSet b0 = new BitSet(ITEM_COUNT);
        BitSet b1 = new BitSet(ITEM_COUNT);

        for (String id: model.getModelIds()) {
            Cursor cOut = model.getItem(id);
            String authority =
                    DocumentInfo.getCursorString(cOut, RootCursorWrapper.COLUMN_AUTHORITY);
            String docId = DocumentInfo.getCursorString(cOut, Document.COLUMN_DOCUMENT_ID);

            switch (authority) {
                case AUTHORITY0:
                    b0.set(Integer.parseInt(docId));
                    break;
                case AUTHORITY1:
                    b1.set(Integer.parseInt(docId));
                    break;
                default:
                    fail("Unrecognized authority string");
            }
        }

        assertEquals(ITEM_COUNT, b0.cardinality());
        assertEquals(ITEM_COUNT, b1.cardinality());
    }

    // Tests the base case for Model.getItem.
    public void testGetItem() {
        String[] ids = model.getModelIds();
        assertEquals(ITEM_COUNT, ids.length);
        for (int i = 0; i < ITEM_COUNT; ++i) {
            Cursor c = model.getItem(ids[i]);
            assertEquals(i, c.getPosition());
        }
    }
    private void setupTestContext() {
        final MockContentResolver resolver = new MockContentResolver();
        new ContextWrapper(getContext()) {
            @Override
            public ContentResolver getContentResolver() {
                return resolver;
            }
        };
        provider = new TestContentProvider();
        resolver.addProvider(AUTHORITY, provider);
    }
}
