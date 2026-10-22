package com.whatsapp.util;

import javax.microedition.lcdui.Image;

/**
 * Utility class for image manipulation and scaling in J2ME MIDP 2.0.
 */
public class ImageUtil {

    private ImageUtil() {}

    /**
     * Scales an image to fit within maxW and maxH while strictly preserving aspect ratio.
     * If the source image is already smaller than maxW and maxH, it returns the original image.
     */
    public static Image scaleImageToFit(Image src, int maxW, int maxH) {
        if (src == null) return null;
        int srcW = src.getWidth();
        int srcH = src.getHeight();
        if (srcW <= 0 || srcH <= 0) return src;

        int targetW = srcW;
        int targetH = srcH;

        if (targetW > maxW && maxW > 0) {
            targetH = (targetH * maxW) / targetW;
            targetW = maxW;
        }

        if (targetH > maxH && maxH > 0) {
            targetW = (targetW * maxH) / targetH;
            targetH = maxH;
        }

        if (targetW < 1) targetW = 1;
        if (targetH < 1) targetH = 1;

        if (targetW == srcW && targetH == srcH) {
            return src;
        }

        return scaleImage(src, targetW, targetH);
    }

    /**
     * Resizes an image to exact target dimensions using native RGB buffer downsampling.
     */
    public static Image scaleImage(Image src, int targetW, int targetH) {
        if (src == null) return null;
        int srcW = src.getWidth();
        int srcH = src.getHeight();
        if (srcW <= 0 || srcH <= 0 || targetW <= 0 || targetH <= 0) return src;
        if (srcW == targetW && srcH == targetH) return src;

        int[] srcRgb = new int[srcW * srcH];
        src.getRGB(srcRgb, 0, srcW, 0, 0, srcW, srcH);

        int[] dstRgb = new int[targetW * targetH];
        for (int y = 0; y < targetH; y++) {
            int srcY = (y * srcH) / targetH;
            int srcRow = srcY * srcW;
            int dstRow = y * targetW;
            for (int x = 0; x < targetW; x++) {
                int srcX = (x * srcW) / targetW;
                dstRgb[dstRow + x] = srcRgb[srcRow + srcX];
            }
        }
        return Image.createRGBImage(dstRgb, targetW, targetH, false);
    }
}
