import kotlinx.cinterop.*
import platform.android.JNIEnvVar
import platform.android.jbyteArray
import platform.android.jclass
import platform.android.jint


@CName("Java_pl_pk_binarizer_ktnative_SimpleBinarization_binarize")
fun simpleBinarize(
    env: CPointer<JNIEnvVar>,
    thiz: jclass,
    input: jbyteArray,
    output: jbyteArray,
    size: jint
) {
    val originalBytes = env.pointed.pointed!!.GetByteArrayElements!!.invoke(env, input, null)!!
    val processedBytes = env.pointed.pointed!!.GetByteArrayElements!!.invoke(env, output, null)!!

    for (i in 0 until size) {
        if (originalBytes[i] < 0) {
            processedBytes[i] = -1
        } else {
            processedBytes[i] = 0
        }
    }

    env.pointed.pointed!!.ReleaseByteArrayElements!!.invoke(env, input, originalBytes, 0)
    env.pointed.pointed!!.ReleaseByteArrayElements!!.invoke(env, output, processedBytes, 0)
}
