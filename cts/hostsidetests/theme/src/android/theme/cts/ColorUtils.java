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

package android.theme.cts;

/**
 * A set of color-related utility methods, building upon those available in {@code Color}.
 */
public class ColorUtils {

    private static final double XYZ_WHITE_REFERENCE_X = 95.047;
    private static final double XYZ_WHITE_REFERENCE_Y = 100;
    private static final double XYZ_WHITE_REFERENCE_Z = 108.883;
    private static final double XYZ_EPSILON = 0.008856;
    private static final double XYZ_KAPPA = 903.3;

    private ColorUtils() {}

    /**
     * Performs alpha blending of two colors using Porter-Duff SRC_OVER.
     *
     * @param src
     * @param dst
     */
    public static int blendSrcOver(int src, int dst) {
        int x = 255 - a(src);
        int Ar = clamp(a(src) + a(dst) * x);
        int Rr = clamp(r(src) + r(dst) * x);
        int Gr = clamp(g(src) + g(dst) * x);
        int Br = clamp(b(src) + b(dst) * x);
        return argb(Ar, Rr, Gr, Br);
    }

    private static int clamp(int value) {
        return value > 255 ? 255 : value < 0 ? 0 : value;
    }

    /**
     * Return a color-int from alpha, red, green, blue components.
     * These component values should be \([0..255]\), but there is no
     * range check performed, so if they are out of range, the
     * returned color is undefined.
     *
     * @param alpha Alpha component \([0..255]\) of the color
     * @param red Red component \([0..255]\) of the color
     * @param green Green component \([0..255]\) of the color
     * @param blue Blue component \([0..255]\) of the color
     */
    public static int argb(int alpha, int red, int green, int blue) {
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    /**
     * Return the alpha component of a color int. This is the same as saying
     * color >>> 24
     */
    public static int a(int color) {
        return color >>> 24;
    }

    /**
     * Return the red component of a color int. This is the same as saying
     * (color >> 16) & 0xFF
     */
    public static int r(int color) {
        return (color >> 16) & 0xFF;
    }

    /**
     * Return the green component of a color int. This is the same as saying
     * (color >> 8) & 0xFF
     */
    public static int g(int color) {
        return (color >> 8) & 0xFF;
    }

    /**
     * Return the blue component of a color int. This is the same as saying
     * color & 0xFF
     */
    public static int b(int color) {
        return color & 0xFF;
    }

    /**
     * Convert the ARGB color to its CIE Lab representative components.
     *
     * @param color  the ARGB color to convert. The alpha component is ignored
     * @param outLab 3-element array which holds the resulting LAB components
     */
    public static void colorToLAB(int color, double[] outLab) {
        RGBToLAB(r(color), g(color), b(color), outLab);
    }

    /**
     * Convert RGB components to its CIE Lab representative components.
     *
     * <ul>
     * <li>outLab[0] is L [0 ...1)</li>
     * <li>outLab[1] is a [-128...127)</li>
     * <li>outLab[2] is b [-128...127)</li>
     * </ul>
     *
     * @param r      red component value [0..255]
     * @param g      green component value [0..255]
     * @param b      blue component value [0..255]
     * @param outLab 3-element array which holds the resulting LAB components
     */
    public static void RGBToLAB(int r, int g, int b, double[] outLab) {
        // First we convert RGB to XYZ
        RGBToXYZ(r, g, b, outLab);
        // outLab now contains XYZ
        XYZToLAB(outLab[0], outLab[1], outLab[2], outLab);
        // outLab now contains LAB representation
    }

    /**
     * Convert RGB components to its CIE XYZ representative components.
     *
     * <p>The resulting XYZ representation will use the D65 illuminant and the CIE
     * 2° Standard Observer (1931).</p>
     *
     * <ul>
     * <li>outXyz[0] is X [0 ...95.047)</li>
     * <li>outXyz[1] is Y [0...100)</li>
     * <li>outXyz[2] is Z [0...108.883)</li>
     * </ul>
     *
     * @param r      red component value [0..255]
     * @param g      green component value [0..255]
     * @param b      blue component value [0..255]
     * @param outXyz 3-element array which holds the resulting XYZ components
     */
    public static void RGBToXYZ(int r, int g, int b, double[] outXyz) {
        if (outXyz.length != 3) {
            throw new IllegalArgumentException("outXyz must have a length of 3.");
        }

        double sr = r / 255.0;
        sr = sr < 0.04045 ? sr / 12.92 : Math.pow((sr + 0.055) / 1.055, 2.4);
        double sg = g / 255.0;
        sg = sg < 0.04045 ? sg / 12.92 : Math.pow((sg + 0.055) / 1.055, 2.4);
        double sb = b / 255.0;
        sb = sb < 0.04045 ? sb / 12.92 : Math.pow((sb + 0.055) / 1.055, 2.4);

        outXyz[0] = 100 * (sr * 0.4124 + sg * 0.3576 + sb * 0.1805);
        outXyz[1] = 100 * (sr * 0.2126 + sg * 0.7152 + sb * 0.0722);
        outXyz[2] = 100 * (sr * 0.0193 + sg * 0.1192 + sb * 0.9505);
    }

    /**
     * Converts a color from CIE XYZ to CIE Lab representation.
     *
     * <p>This method expects the XYZ representation to use the D65 illuminant and the CIE
     * 2° Standard Observer (1931).</p>
     *
     * <ul>
     * <li>outLab[0] is L [0 ...1)</li>
     * <li>outLab[1] is a [-128...127)</li>
     * <li>outLab[2] is b [-128...127)</li>
     * </ul>
     *
     * @param x      X component value [0...95.047)
     * @param y      Y component value [0...100)
     * @param z      Z component value [0...108.883)
     * @param outLab 3-element array which holds the resulting Lab components
     */
    public static void XYZToLAB(double x, double y, double z, double[] outLab) {
        if (outLab.length != 3) {
            throw new IllegalArgumentException("outLab must have a length of 3.");
        }
        x = pivotXyzComponent(x / XYZ_WHITE_REFERENCE_X);
        y = pivotXyzComponent(y / XYZ_WHITE_REFERENCE_Y);
        z = pivotXyzComponent(z / XYZ_WHITE_REFERENCE_Z);
        outLab[0] = Math.max(0, 116 * y - 16);
        outLab[1] = 500 * (x - y);
        outLab[2] = 200 * (y - z);
    }

    private static double pivotXyzComponent(double component) {
        return component > XYZ_EPSILON
                ? Math.pow(component, 1 / 3.0)
                : (XYZ_KAPPA * component + 16) / 116;
    }
}
