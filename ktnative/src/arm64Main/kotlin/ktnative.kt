import kotlinx.cinterop.*
import platform.android.*

//////////// CONSTANTS ////////////

private const val radius = 7
private const val area = ((radius * 2 + 1) * (radius * 2 + 1))

//////////// SIMPLE BINARIZATION ////////////

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

//////////// BRADLEY BINARIZATION ////////////

@CName("Java_pl_pk_binarizer_ktnative_BradleyBinarization_binarize")
fun bradleyBinarize(
    env: CPointer<JNIEnvVar>,
    thiz: jclass,
    input: jbyteArray,
    output: jbyteArray,
    size: jint,
    width: jint,
    height: jint
) {
    val originalBytes: CPointer<ByteVarOf<jbyte>> = env.pointed.pointed!!.GetByteArrayElements!!.invoke(env, input, null)!!
    val processedBytes = env.pointed.pointed!!.GetByteArrayElements!!.invoke(env, output, null)!!

    for (i in 0 until size) {
        val th = threshold(originalBytes, i, width, height)
        if ((originalBytes[i].toInt() and 0xFF) > th) {
            processedBytes[i] = -1
        } else {
            processedBytes[i] = 0
        }
    }

    env.pointed.pointed!!.ReleaseByteArrayElements!!.invoke(env, input, originalBytes, 0)
    env.pointed.pointed!!.ReleaseByteArrayElements!!.invoke(env, output, processedBytes, 0)
}

private fun threshold(data: CPointer<ByteVarOf<jbyte>>, index: Int, width: Int, height: Int): Int {
    var sum: Long = 0

    val start = index - width * radius - radius
    val maxOffset = radius * 2 * width + radius * 2

    if (start + maxOffset > width * height || start < 0) return 127

    for (y in 0 until radius * 2) {
        for (x in 0 until radius * 2) {
            sum += data[start + y * width + x].toInt() and 0xFF
        }
    }

    val average = (sum / area).toInt()
    return average * 78 / 100
}

//////////// BRADLEY INTEGRAL BINARIZATION ////////////

private fun calculateIntegral(originalBytes: CPointer<ByteVarOf<jbyte>>, integral: IntArray, width: Int, height: Int) {
    for (row in 0 until height) {
        var sum = 0
        for (column in 0 until width) {
            sum += originalBytes[column + width * row].toInt() and 0xFF
            if (row == 0) {
                integral[column + width * row] = sum
            } else {
                integral[column + width * row] = integral[column + width * (row - 1)] + sum
            }
        }
    }
}

private fun getIntegralAverage(index: Int, integral: IntArray, width: Int): Int {
    val one = integral[index + radius * width + radius]
    val two = integral[index - radius * width - radius]
    val three = integral[index + radius * width - radius]
    val four = integral[index - radius * width + radius]
    return (one + two - three - four) / area
}

private fun integralThreshold(integral: IntArray, index: Int, width: Int, height: Int): Int {
    if (index + radius * width + radius >= width * height || index - radius * width - radius < 0) return 127

    val average = getIntegralAverage(index, integral, width)
    return average * 78 / 100
}

@CName("Java_pl_pk_binarizer_ktnative_BradleyIntegralBinarization_binarize")
fun bradleyIntegralBinarize(
    env: CPointer<JNIEnvVar>,
    thiz: jclass,
    input: jbyteArray,
    output: jbyteArray,
    size: jint,
    width: jint,
    height: jint
) {
    val originalBytes: CPointer<ByteVarOf<jbyte>> = env.pointed.pointed!!.GetByteArrayElements!!.invoke(env, input, null)!!
    val processedBytes = env.pointed.pointed!!.GetByteArrayElements!!.invoke(env, output, null)!!
    val integral = IntArray(size)
    calculateIntegral(originalBytes, integral, width, height)

    for (i in 0 until size) {
        val th = integralThreshold(integral, i, width, height)
        if ((originalBytes[i].toInt() and 0xFF) > th) {
            processedBytes[i] = -1
        } else {
            processedBytes[i] = 0
        }
    }

    env.pointed.pointed!!.ReleaseByteArrayElements!!.invoke(env, input, originalBytes, 0)
    env.pointed.pointed!!.ReleaseByteArrayElements!!.invoke(env, output, processedBytes, 0)
}
