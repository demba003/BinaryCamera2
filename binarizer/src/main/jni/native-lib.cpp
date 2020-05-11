#include <jni.h>

extern "C" JNIEXPORT void JNICALL
Java_pl_pk_binarizer_cpp_SimpleBinarization_binarize(JNIEnv *env, jobject thiz, jbyteArray input, jint size) {
    jboolean *isCopy = new jboolean;
    jbyte *bytes = env->GetByteArrayElements(input, isCopy);

    #pragma omp parallel for num_threads(8) schedule(static) shared(bytes, size) default(none)
    for (int i = 0; i < size; ++i) {
        if (bytes[i] < 0) {
            bytes[i] = -1;
        } else {
            bytes[i] = 0;
        }
    }

    delete isCopy;
    env->ReleaseByteArrayElements(input, bytes, 0);
}

int bradleyThreshold(jbyte *data, int index, int cols, int rows, int neighbourRadius, int neighbourPixels) {
    long sum = 0;

    int start = index - (cols * neighbourRadius) - neighbourRadius;
    int maxOffset = neighbourRadius * 2 * cols + neighbourRadius * 2;

    if((start + maxOffset) > (cols * rows) || start < 0) return 127;

    for(int y = 0; y < neighbourRadius * 2; y++) {
        for(int x = 0; x < neighbourRadius * 2; x ++) {
            sum += data[start + y * cols + x] & 0xFF;
        }
    }

    int average = sum / neighbourPixels;
    return average * 78 / 100;
}

extern "C" JNIEXPORT void JNICALL
Java_pl_pk_binarizer_cpp_BradleyBinarization_binarize(JNIEnv *env, jobject thiz, jbyteArray input, jint size) {
    jboolean *isCopy = new jboolean;
    jbyte *bytes = env->GetByteArrayElements(input, isCopy);

    #pragma omp parallel for num_threads(8) schedule(static) shared(bytes, size) default(none)
    for (int i = 0; i < size; ++i) {
        int th = bradleyThreshold(bytes, i, 1280, 720, 7, 225);
        if ((bytes[i] & 0xFF) > th) {
            bytes[i] = -1;
        } else {
            bytes[i] = 0;
        }
    }

    delete isCopy;
    env->ReleaseByteArrayElements(input, bytes, 0);
}
