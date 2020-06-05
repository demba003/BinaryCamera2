#pragma version(1)
#pragma rs java_package_name(pl.pk.binarizer.rs)

rs_allocation currentFrame;
rs_allocation integral;

uint32_t width = 1280;
uint32_t height = 720;
const uint32_t radius = 7;

static int getIntegralAverage(uint32_t index) {
    int one = rsGetElementAt_int(integral, index + radius * width + radius);
    int two = rsGetElementAt_int(integral, index - radius * width - radius);
    int three = rsGetElementAt_int(integral, index + radius * width - radius);
    int four = rsGetElementAt_int(integral, index - radius * width + radius);
    return (one + two - three - four) / 225;
}

static int bradleyIntegralThreshold(uint32_t x, uint32_t y) {
    if (x < radius || x >= width-radius || y < radius || y >=height-radius) return 127;
    int average = getIntegralAverage(y*width + x);
    return average * 78 / 100;
}

uchar4 __attribute__((kernel)) process(uint32_t x, uint32_t y) {
    uchar current = rsGetElementAtYuv_uchar_Y(currentFrame, x, y);
    uchar average = bradleyIntegralThreshold(x, y);
    return rsYuvToRGBA_uchar4(current > average ? 255 : 0, 128, 128);
}
