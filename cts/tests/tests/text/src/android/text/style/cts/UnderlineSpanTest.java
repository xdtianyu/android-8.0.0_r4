/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.text.style.cts;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.Parcel;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.text.TextPaint;
import android.text.style.UnderlineSpan;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class UnderlineSpanTest {
    @Test
    public void testConstructor() {
        new UnderlineSpan();

        final Parcel p = Parcel.obtain();
        try {
            new UnderlineSpan(p);
        } finally {
            p.recycle();
        }
    }

    @Test
    public void testUpdateDrawState() {
        UnderlineSpan underlineSpan = new UnderlineSpan();

        TextPaint tp = new TextPaint();
        tp.setUnderlineText(false);
        assertFalse(tp.isUnderlineText());

        underlineSpan.updateDrawState(tp);
        assertTrue(tp.isUnderlineText());
    }

    @Test(expected=NullPointerException.class)
    public void testUpdateDrawStateNull() {
        UnderlineSpan underlineSpan = new UnderlineSpan();

        underlineSpan.updateDrawState(null);
    }

    @Test
    public void testDescribeContents() {
        UnderlineSpan underlineSpan = new UnderlineSpan();
        underlineSpan.describeContents();
    }

    @Test
    public void testGetSpanTypeId() {
        UnderlineSpan underlineSpan = new UnderlineSpan();
        underlineSpan.getSpanTypeId();
    }

    @Test
    public void testWriteToParcel() {
        Parcel p = Parcel.obtain();
        try {
            UnderlineSpan underlineSpan = new UnderlineSpan();
            underlineSpan.writeToParcel(p, 0);
            new UnderlineSpan(p);
        } finally {
            p.recycle();
        }
    }
}
