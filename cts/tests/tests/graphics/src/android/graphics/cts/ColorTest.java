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
package android.graphics.cts;

import static org.junit.Assert.assertEquals;

import android.graphics.Color;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class ColorTest {
    @Test
    public void testAlpha() {
        assertEquals(0xff, Color.alpha(Color.RED));
        assertEquals(0xff, Color.alpha(Color.YELLOW));
    }

    @Test
    public void testArgb() {
        assertEquals(Color.RED, Color.argb(0xff, 0xff, 0x00, 0x00));
        assertEquals(Color.YELLOW, Color.argb(0xff, 0xff, 0xff, 0x00));
        assertEquals(Color.RED, Color.argb(1.0f, 1.0f, 0.0f, 0.0f));
        assertEquals(Color.YELLOW, Color.argb(1.0f, 1.0f, 1.0f, 0.0f));
    }

    @Test
    public void testBlue() {
        assertEquals(0x00, Color.blue(Color.RED));
        assertEquals(0x00, Color.blue(Color.YELLOW));
    }

    @Test
    public void testGreen() {
        assertEquals(0x00, Color.green(Color.RED));
        assertEquals(0xff, Color.green(Color.GREEN));
    }

    @Test(expected=RuntimeException.class)
    public void testHSVToColorArrayTooShort() {
        // abnormal case: hsv length less than 3
        float[] hsv = new float[2];
        Color.HSVToColor(hsv);
    }

    @Test
    public void testHSVToColor() {
        float[] hsv = new float[3];
        Color.colorToHSV(Color.RED, hsv);
        assertEquals(Color.RED, Color.HSVToColor(hsv));
    }

    @Test
    public void testHSVToColorWithAlpha() {
        float[] hsv = new float[3];
        Color.colorToHSV(Color.RED, hsv);
        assertEquals(Color.RED, Color.HSVToColor(0xff, hsv));
    }

    @Test(expected=IllegalArgumentException.class)
    public void testParseColorStringOfInvalidLength() {
        // abnormal case: colorString starts with '#' but length is neither 7 nor 9
        Color.parseColor("#ff00ff0");
    }

    @Test
    public void testParseColor() {
        assertEquals(Color.RED, Color.parseColor("#ff0000"));
        assertEquals(Color.RED, Color.parseColor("#ffff0000"));

        assertEquals(Color.BLACK, Color.parseColor("black"));
        assertEquals(Color.DKGRAY, Color.parseColor("darkgray"));
        assertEquals(Color.GRAY, Color.parseColor("gray"));
        assertEquals(Color.LTGRAY, Color.parseColor("lightgray"));
        assertEquals(Color.WHITE, Color.parseColor("white"));
        assertEquals(Color.RED, Color.parseColor("red"));
        assertEquals(Color.GREEN, Color.parseColor("green"));
        assertEquals(Color.BLUE, Color.parseColor("blue"));
        assertEquals(Color.YELLOW, Color.parseColor("yellow"));
        assertEquals(Color.CYAN, Color.parseColor("cyan"));
        assertEquals(Color.MAGENTA, Color.parseColor("magenta"));
    }

    @Test(expected=IllegalArgumentException.class)
    public void testParseColorUnsupportedFormat() {
        // abnormal case: colorString doesn't start with '#' and is unknown color
        Color.parseColor("hello");
    }

    @Test
    public void testRed() {
        assertEquals(0xff, Color.red(Color.RED));
        assertEquals(0xff, Color.red(Color.YELLOW));
    }

    @Test
    public void testRgb() {
        assertEquals(Color.RED, Color.rgb(0xff, 0x00, 0x00));
        assertEquals(Color.YELLOW, Color.rgb(0xff, 0xff, 0x00));
        assertEquals(Color.RED, Color.rgb(1.0f, 0.0f, 0.0f));
        assertEquals(Color.YELLOW, Color.rgb(1.0f, 1.0f, 0.0f));
    }

    @Test(expected=RuntimeException.class)
    public void testRGBToHSVArrayTooShort() {
        // abnormal case: hsv length less than 3
        float[] hsv = new float[2];
        Color.RGBToHSV(0xff, 0x00, 0x00, hsv);
    }

    @Test
    public void testRGBToHSV() {
        float[] hsv = new float[3];
        Color.RGBToHSV(0xff, 0x00, 0x00, hsv);
        assertEquals(Color.RED, Color.HSVToColor(hsv));
    }

    @Test
    public void testLuminance() {
        assertEquals(0, Color.luminance(Color.BLACK), 0);
        float eps = 0.000001f;
        assertEquals(0.0722, Color.luminance(Color.BLUE), eps);
        assertEquals(0.2126, Color.luminance(Color.RED), eps);
        assertEquals(0.7152, Color.luminance(Color.GREEN), eps);
        assertEquals(1, Color.luminance(Color.WHITE), 0);
    }
}
