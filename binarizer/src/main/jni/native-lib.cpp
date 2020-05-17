#include <jni.h>

extern "C" JNIEXPORT void JNICALL
Java_pl_pk_binarizer_cpp_SimpleBinarization_binarize(JNIEnv *env, jobject thiz, jbyteArray input, jbyteArray output, jint size) {
    jboolean *isCopy = new jboolean;
    jbyte *originalBytes = env->GetByteArrayElements(input, isCopy);
    jbyte *processedBytes = env->GetByteArrayElements(output, nullptr);

    #pragma omp parallel for num_threads(8) schedule(static) shared(originalBytes, processedBytes, size) default(none)
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

int bradleyThreshold(jbyte *data, int index, int cols, int rows) {
    long sum = 0;

    int start = index - (cols * 7) - 7;
    int maxOffset = 7 * 2 * cols + 7 * 2;

    if ((start + maxOffset) > (cols * rows) || start < 0) return 127;

    for (int y = 0; y < 7 * 2; y++) {
        for (int x = 0; x < 7 * 2; x++) {
            sum += data[start + y * cols + x] & 0xFF;
        }
    }

    int average = sum / 225;
    return average * 78 / 100;
}

extern "C" JNIEXPORT void JNICALL
Java_pl_pk_binarizer_cpp_BradleyBinarization_binarize(JNIEnv *env, jobject thiz, jbyteArray input, jbyteArray output, jint size) {
    jboolean *isCopy = new jboolean;
    jbyte *originalBytes = env->GetByteArrayElements(input, isCopy);
    jbyte *processedBytes = env->GetByteArrayElements(output, nullptr);

    #pragma omp parallel for num_threads(8) schedule(static) shared(originalBytes, processedBytes, size) default(none)
    for (int i = 0; i < size; ++i) {
        int th = bradleyThreshold(originalBytes, i, 1280, 720);
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
