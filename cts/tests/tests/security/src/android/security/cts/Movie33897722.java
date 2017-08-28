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

package android.security.cts;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Movie;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.platform.test.annotations.SecurityTest;
import android.test.AndroidTestCase;

import java.io.InputStream;

import android.security.cts.R;

@SecurityTest
public class Movie33897722 extends AndroidTestCase {
    /**
     * Verifies that decoding a particular GIF file does not read out out of bounds.
     *
     * The image has a color map of size 2, but states that pixels should come from values
     * larger than 2. Ensure that we do not attempt to read colors from beyond the end of the
     * color map, which would be reading memory that we do not control, and may be uninitialized.
     */
    public void test_android_bug_33897722() {
        InputStream exploitImage = mContext.getResources().openRawResource(R.raw.bug_33897722);
        Movie movie = Movie.decodeStream(exploitImage);
        assertNotNull(movie);
        assertEquals(movie.width(), 600);
        assertEquals(movie.height(), 752);

        // The image has a 10 x 10 frame on top of a transparent background. Only test the
        // 10 x 10 frame, since the original bug would never have used uninitialized memory
        // outside of it.
        Bitmap bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        // Use Src PorterDuff mode, to see exactly what the Movie creates.
        Paint paint = new Paint();
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC));

        movie.draw(canvas, 0, 0, paint);

        for (int x = 0; x < 10; x++) {
            for (int y = 0; y < 10; y++) {
                assertEquals(bitmap.getPixel(x, y), Color.TRANSPARENT);
            }
        }
    }
}
