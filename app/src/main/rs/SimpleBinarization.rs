#pragma version(1)
#pragma rs java_package_name(pl.pk.binarycamera2)
#pragma rs_fp_relaxed

rs_allocation gCurrentFrame;

int gDoMerge = 0;

uchar4 __attribute__((kernel)) binarize(uint32_t x, uint32_t y) {
    // Read in pixel values from latest frame - YUV color space
    uchar3 curPixel;
    curPixel.x = rsGetElementAtYuv_uchar_Y(gCurrentFrame, x, y);
    curPixel.y = rsGetElementAtYuv_uchar_U(gCurrentFrame, x, y);
    curPixel.z = rsGetElementAtYuv_uchar_V(gCurrentFrame, x, y);

    if (gDoMerge == 1) {
        return rsYuvToRGBA_uchar4(curPixel.x > 128 ? 255 : 0, 128, 128);
    } else {
        // Straight passthrough
        return rsYuvToRGBA_uchar4(curPixel.x, 128, 128);
    }
}
