#include <jni.h>

//////////// CONSTANTS ////////////

constexpr int radius = 7;
constexpr int area = ((radius * 2 + 1) * (radius * 2 + 1));

//////////// SIMPLE BINARIZATION ////////////

extern "C" JNIEXPORT void JNICALL
Java_pl_pk_binarizer_cpp_SimpleBinarization_binarize(
        JNIEnv *env,
        jobject thiz,
        jbyteArray input,
        jbyteArray output,
        jint size) {
    jboolean *isCopy = new jboolean;
    jbyte *originalBytes = env->GetByteArrayElements(input, isCopy);
    jbyte *processedBytes = env->GetByteArrayElements(output, nullptr);

    #pragma omp parallel for schedule(static) shared(originalBytes, processedBytes, size) default(none)
    for (int i = 0; i < size; ++i) {
        if (originalBytes[i] < 0) {
            processedBytes[i] = -1;
        } else {
            processedBytes[i] = 0;
        }
    }

    delete isCopy;
    env->ReleaseByteArrayElements(input, originalBytes, 0);
    env->ReleaseByteArrayElements(output, processedBytes, 0);
}

//////////// BRADLEY BINARIZATION ////////////

int bradleyThreshold(jbyte *data, int index, int cols, int rows) {
    long sum = 0;

    int start = index - (cols * radius) - radius;
    int maxOffset = radius * 2 * cols + radius * 2;

    if ((start + maxOffset) > (cols * rows) || start < 0) return 127;

    for (int y = 0; y < radius * 2; y++) {
        for (int x = 0; x < radius * 2; x++) {
            sum += data[start + y * cols + x] & 0xFF;
        }
    }

    int average = sum / area;
    return average * 78 / 100;
}

extern "C" JNIEXPORT void JNICALL
Java_pl_pk_binarizer_cpp_BradleyBinarization_binarize(
        JNIEnv *env,
        jobject thiz,
        jbyteArray input,
        jbyteArray output,
        jint size,
        jint width,
        jint height) {
    jboolean *isCopy = new jboolean;
    jbyte *originalBytes = env->GetByteArrayElements(input, isCopy);
    jbyte *processedBytes = env->GetByteArrayElements(output, nullptr);

    #pragma omp parallel for schedule(static) shared(originalBytes, processedBytes, size, width, height) default(none)
    for (int i = 0; i < size; ++i) {
        int th = bradleyThreshold(originalBytes, i, width, height);
        if ((originalBytes[i] & 0xFF) > th) {
            processedBytes[i] = -1;
        } else {
            processedBytes[i] = 0;
        }
    }

    delete isCopy;
    env->ReleaseByteArrayElements(input, originalBytes, 0);
    env->ReleaseByteArrayElements(output, processedBytes, 0);
}

//////////// BRADLEY INTEGRAL BINARIZATION ////////////

void calculateIntegral(jbyte *originalBytes, int *integral, int width, int height) {
    for (int row = 0; row < height; row++) {
        int sum = 0;
        for (int column = 0; column < width; column++) {
            sum += originalBytes[column + width * row] & 0xFF;
            if (row == 0) {
                integral[column + width * row] = sum;
            } else {
                integral[column + width * row] = integral[column + width * (row - 1)] + sum;
            }
        }
    }
}

int getIntegralAverage(int index, int *integral, int width) {
    int one = integral[index + radius * width + radius];
    int two = integral[index - radius * width - radius];
    int three = integral[index + radius * width - radius];
    int four = integral[index - radius * width + radius];
    return (one + two - three - four) / area;
}

int bradleyIntegralThreshold(int *integral, int index, int width, int height) {
    if (index + radius * width + radius >= width * height || index - radius * width - radius < 0) return 127;

    int average = getIntegralAverage(index, integral, width);
    return average * 78 / 100;
}

extern "C" JNIEXPORT void JNICALL
Java_pl_pk_binarizer_cpp_BradleyIntegralBinarization_binarize(
        JNIEnv *env,
        jobject thiz,
        jbyteArray input,
        jbyteArray output,
        jint size,
        jint width,
        jint height) {
    jboolean *isCopy = new jboolean;
    jbyte *originalBytes = env->GetByteArrayElements(input, isCopy);
    jbyte *processedBytes = env->GetByteArrayElements(output, nullptr);

    int *integral = new int[size];
    calculateIntegral(originalBytes, integral, width, height);

    #pragma omp parallel for schedule(static) shared(originalBytes, processedBytes, integral, size,  width, height) default(none)
    for (int i = 0; i < size; ++i) {
        int th = bradleyIntegralThreshold(integral, i, width, height);
        if ((originalBytes[i] & 0xFF) > th) {
            processedBytes[i] = -1;
        } else {
            processedBytes[i] = 0;
        }
    }

    delete isCopy;
    delete[] integral;
    env->ReleaseByteArrayElements(input, originalBytes, 0);
    env->ReleaseByteArrayElements(output, processedBytes, 0);
}
