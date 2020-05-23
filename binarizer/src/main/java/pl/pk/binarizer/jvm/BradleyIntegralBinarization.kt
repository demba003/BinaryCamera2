package pl.pk.binarizer.jvm

import android.renderscript.Allocation
import android.renderscript.RenderScript
import android.util.Size
import pl.pk.binarizer.Processor
import pl.pk.binarizer.rs.ScriptC_YuvToMonochrome
import java.util.concurrent.Executors
import java.util.concurrent.Future

class BradleyIntegralBinarization(rs: RenderScript, private val dimensions: Size) : Processor {

    private val kernel = ScriptC_YuvToMonochrome(rs)
    private val threadCount = Runtime.getRuntime().availableProcessors()
    private val pool = Executors.newFixedThreadPool(threadCount)

    override fun process(input: Allocation, output: Allocation) {
        val monochromeBytesSize = (input.bytesSize / 1.5).toInt()
        val originalBytes = ByteArray(monochromeBytesSize)
        val processedBytes = ByteArray(monochromeBytesSize)
        val integral = IntArray(monochromeBytesSize)

        input.copy1DRangeTo(0, monochromeBytesSize, originalBytes)

        calculateIntegral(originalBytes, integral)

        val tasks = mutableListOf<Future<Unit>>()

        for (threadId in 0 until threadCount) {
            tasks.add(
                pool.submit<Unit> {
                    processSegment(
                        originalBytes,
                        processedBytes,
                        integral,
                        monochromeBytesSize,
                        threadId
                    )
                }
            )
        }

        tasks.forEach { it.get() }

        input.copy1DRangeFrom(0, monochromeBytesSize, processedBytes)

        kernel._currentFrame = input
        kernel.forEach_process(output)
    }

    private fun calculateIntegral(originalBytes: ByteArray, integral: IntArray) {
        for (row in 0 until dimensions.height) {
            var sum = 0
            for (column in 0 until dimensions.width) {
                sum += originalBytes[column + dimensions.width * row].toInt() and 0xFF
                if (row == 0) {
                    integral[column + dimensions.width * row] = sum
                } else {
                    integral[column + dimensions.width * row] = integral[column + dimensions.width * (row - 1)] + sum
                }
            }
        }
    }

    private fun getIntegralAverage(index: Int, integral: IntArray): Int {
        val one = integral[index + radius * dimensions.width + radius]
        val two = integral[index - radius * dimensions.width - radius]
        val three = integral[index + radius * dimensions.width - radius]
        val four = integral[index - radius * dimensions.width + radius]
        return (one + two - three - four) / area
    }

    private fun processSegment(
        originalBytes: ByteArray,
        processedBytes: ByteArray,
        integral: IntArray,
        size: Int,
        threadId: Int
    ) {
        for (i in threadId until size step threadCount) {
            val th = threshold(integral, i)
            if ((originalBytes[i].toInt() and 0xFF) > th) {
                processedBytes[i] = -1
            } else {
                processedBytes[i] = 0
            }
        }
    }

    private fun threshold(integral: IntArray, index: Int): Int {
        if (index + radius * dimensions.width + radius >= dimensions.width * dimensions.height || index - radius * dimensions.width - radius < 0) return 127

        val average = getIntegralAverage(index, integral)
        return average * 78 / 100
    }

    companion object {
        private const val radius = 7
        private const val area = ((radius * 2 + 1) * (radius * 2 + 1))
    }
}
