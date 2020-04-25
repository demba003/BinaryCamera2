#pragma version(1)
#pragma rs java_package_name(pl.pk.binarizer.rs)
#pragma rs_fp_relaxed

rs_allocation currentFrame;

uchar4 __attribute__((kernel)) process(uint32_t x, uint32_t y) {
    uchar curPixel = rsGetElementAtYuv_uchar_Y(currentFrame, x, y);
    return rsYuvToRGBA_uchar4(curPixel, 128, 128);
}
