import kotlinx.cinterop.*
import platform.android.*
import kotlin.native.concurrent.*

//////////// CONSTANTS ////////////

private const val radius = 7
private const val area = ((radius * 2 + 1) * (radius * 2 + 1))

private val workers = generateSequence { Worker.start() }
    .take(8)
    .toList()

//////////// SIMPLE BINARIZATION ////////////

@CName("Java_pl_pk_binarizer_ktnative_SimpleBinarization_binarize")
fun simpleBinarize(
    env: CPointer<JNIEnvVar>,
    thiz: jclass,
    input: jbyteArray,
    output: jbyteArray,
    size: jint
) {
    val originalBytes = env.getByteArrayElements(input)
    val processedBytes = env.getByteArrayElements(output)

    val futures = ArrayList<Future<Unit>>(workers.size)

    workers.forEachIndexed { index, worker ->
        futures += worker.execute(TransferMode.SAFE, { WorkerArg(index, originalBytes, processedBytes) }) {
            for (i in it.id until it.size step it.threadCount) {
                if (it.originalBytes[i] < 0) {
                    it.processedBytes[i] = -1
                } else {
                    it.processedBytes[i] = 0
                }
            }
        }
    }

    futures.forEach { it.result }

    env.releaseByteArrayElements(input, originalBytes)
    env.releaseByteArrayElements(output, processedBytes)
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
    val originalBytes = env.getByteArrayElements(input)
    val processedBytes = env.getByteArrayElements(output)

    val futures = ArrayList<Future<Unit>>(workers.size)

    workers.forEachIndexed { index, worker ->
        futures += worker.execute(TransferMode.SAFE, { WorkerArg(index, originalBytes, processedBytes, width, height) }) {
            for (i in it.id until it.size step it.threadCount) {
                val th = threshold(it.originalBytes, i, it.width, it.height)
                if ((it.originalBytes[i].toInt() and 0xFF) > th) {
                    it.processedBytes[i] = -1
                } else {
                    it.processedBytes[i] = 0
                }
            }
        }
    }

    futures.forEach { it.result }

    env.releaseByteArrayElements(input, originalBytes)
    env.releaseByteArrayElements(output, processedBytes)
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

private fun getIntegralAverage(index: Int, integral: CPointer<IntVar>, width: Int): Int {
    val one = integral[index + radius * width + radius]
    val two = integral[index - radius * width - radius]
    val three = integral[index + radius * width - radius]
    val four = integral[index - radius * width + radius]
    return (one + two - three - four) / area
}

private fun integralThreshold(integral: CPointer<IntVar>, index: Int, width: Int, height: Int): Int {
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
    val originalBytes = env.getByteArrayElements(input)
    val processedBytes = env.getByteArrayElements(output)

    val integral = IntArray(size)
    calculateIntegral(originalBytes, integral, width, height)

    val futures = ArrayList<Future<Unit>>(workers.size)

    integral.usePinned {
        workers.forEachIndexed { index, worker ->
            futures += worker.execute(TransferMode.SAFE, { WorkerArg(index, originalBytes, processedBytes, width, height, it.addressOf(0)) }) {
                for (i in it.id until it.size step it.threadCount) {
                    val th = integralThreshold(it.integral!!, i, it.width, it.height)
                    if ((it.originalBytes[i].toInt() and 0xFF) > th) {
                        it.processedBytes[i] = -1
                    } else {
                        it.processedBytes[i] = 0
                    }
                }
            }
        }
   }

    futures.forEach { it.result }

    env.releaseByteArrayElements(input, originalBytes)
    env.releaseByteArrayElements(output, processedBytes)
}
