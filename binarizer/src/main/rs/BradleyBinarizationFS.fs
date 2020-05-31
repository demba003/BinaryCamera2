#pragma version(1)
#pragma rs java_package_name(pl.pk.binarizer.rs)

rs_allocation currentFrame;

uint32_t width = 1280;
uint32_t height = 720;

uchar4 __attribute__((kernel)) process(uint32_t x, uint32_t y) {
    uint sum = 0;
    uint count = 0;
    for (int yi = y-7; yi <= y+7; ++yi) {
        for (int xi = x-7; xi <= x+7; ++xi) {
            if (xi >= 0 && xi < width && yi >= 0 && yi < height) {
                sum += rsGetElementAtYuv_uchar_Y(currentFrame, xi, yi);
                ++count;
            }
        }
    }

    uchar current = rsGetElementAtYuv_uchar_Y(currentFrame, x, y);
    uchar average = (sum / count) * 78 / 100;

    return rsYuvToRGBA_uchar4(current > average ? 255 : 0, 128, 128);

    //uint sum = 0;

    //if (x < 7 || x > cols-7 || y < 7 || y > rows-7) {
    //    uchar current = rsGetElementAtYuv_uchar_Y(currentFrame, x, y);
    //    return rsYuvToRGBA_uchar4(current > 127 ? 255 : 0, 128, 128);
    //}

    //for (int yi = -7; yi < 7; yi++) {
    //    for (int xi = -7; xi < 7; xi++) {
     //       sum += rsGetElementAtYuv_uchar_Y(currentFrame, x + xi, y + yi);
     //   }
    //}

    //uint average = sum / 225;
    //uint th = average * 78 / 100;

   // uchar current = rsGetElementAtYuv_uchar_Y(currentFrame, x, y);
   // return rsYuvToRGBA_uchar4(current > average ? 255 : 0, 128, 128);
}