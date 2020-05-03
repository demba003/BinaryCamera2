#include <jni.h>

extern "C" JNIEXPORT void JNICALL
Java_pl_pk_binarizer_cpp_SimpleBinarization_binarize(JNIEnv *env, jobject thiz, jbyteArray input, jint size) {
    jboolean *isCopy = new jboolean;
    jbyte *bytes = env->GetByteArrayElements(input, isCopy);

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
