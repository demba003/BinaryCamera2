import kotlinx.cinterop.*
import platform.android.JNIEnvVar
import platform.android.jbyteArray
import platform.android.jbyteVar

data class WorkerArg(
    val id: Int,
    val originalBytes: CPointer<jbyteVar>,
    val processedBytes: CPointer<jbyteVar>,
    val width: Int = 1280,
    val height: Int = 720,
    val integral: CPointer<IntVar>? = null,
    val size: Int = width * height,
    val threadCount: Int = 8
)

fun CPointer<JNIEnvVar>.getByteArrayElements(array: jbyteArray) =
    this.pointed.pointed!!.GetByteArrayElements!!.invoke(this, array, null)!!

fun CPointer<JNIEnvVar>.releaseByteArrayElements(array: jbyteArray, pointer: CPointer<jbyteVar>) =
    this.pointed.pointed!!.ReleaseByteArrayElements!!.invoke(this, array, pointer, 0)
