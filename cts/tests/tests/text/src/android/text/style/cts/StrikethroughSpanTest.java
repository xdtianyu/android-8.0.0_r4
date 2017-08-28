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
import android.text.style.StrikethroughSpan;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class StrikethroughSpanTest {
    @Test
    public void testConstructor() {
        StrikethroughSpan strikethroughSpan = new StrikethroughSpan();

        Parcel p = Parcel.obtain();
        try {
            strikethroughSpan.writeToParcel(p, 0);
            p.setDataPosition(0);
            new StrikethroughSpan(p);
        } finally {
            p.recycle();
        }
    }

    @Test
    public void testUpdateDrawState() {
        StrikethroughSpan strikethroughSpan = new StrikethroughSpan();

        TextPaint tp = new TextPaint();
        tp.setStrikeThruText(false);
        assertFalse(tp.isStrikeThruText());

        strikethroughSpan.updateDrawState(tp);
        assertTrue(tp.isStrikeThruText());
    }

    @Test(expected=NullPointerException.class)
    public void testUpdateDrawStateNull() {
        StrikethroughSpan strikethroughSpan = new StrikethroughSpan();

        strikethroughSpan.updateDrawState(null);
    }

    @Test
    public void testDescribeContents() {
        StrikethroughSpan strikethroughSpan = new StrikethroughSpan();
        strikethroughSpan.describeContents();
    }

    @Test
    public void testGetSpanTypeId() {
        StrikethroughSpan strikethroughSpan = new StrikethroughSpan();
        strikethroughSpan.getSpanTypeId();
    }

    @Test
    public void testWriteToParcel() {
        Parcel p = Parcel.obtain();
        try {
            StrikethroughSpan strikethroughSpan = new StrikethroughSpan();
            strikethroughSpan.writeToParcel(p, 0);
            p.setDataPosition(0);
            new StrikethroughSpan(p);
        } finally {
            p.recycle();
        }
    }
}
