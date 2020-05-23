package pl.pk.binarizer.jvm

import android.renderscript.Allocation
import android.renderscript.RenderScript
import pl.pk.binarizer.Processor
import pl.pk.binarizer.rs.ScriptC_YuvToMonochrome
import java.util.concurrent.Executors
import java.util.concurrent.Future

class BradleyIntegralBinarization(rs: RenderScript) : Processor {

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
        val height = 720
        val width = 1280
        for (row in 0 until height) {
            var sum: Int = 0
            for (column in 0 until width) {
                sum += originalBytes[column + width * row]
                if (row == 0) {
                    integral[column + width * row] = sum
                } else {
                    integral[column + width * row] = integral[column + width * (row - 1)] + sum
                }
            }
        }
    }

    private fun getIntegralAverage(index: Int, integral: IntArray): Int {
        val one = integral[index + 7 * 1280 + 7]
        val two = integral[index - 7 * 1280 - 7]
        val three = integral[index + 7 * 1280 - 7]
        val four = integral[index - 7 * 1280 + 7]
        return (one + two - three - four) / 225
    }

    private fun processSegment(
        originalBytes: ByteArray,
        processedBytes: ByteArray,
        integral: IntArray,
        size: Int,
        threadId: Int
    ) {
        for (i in threadId until size step threadCount) {
            val th = threshold(integral, i, 1280, 720)
            if ((originalBytes[i].toInt() and 0xFF) > th) {
                processedBytes[i] = -1
            } else {
                processedBytes[i] = 0
            }
        }
    }

    private fun threshold(integral: IntArray, index: Int, cols: Int, rows: Int): Int {
        if (index + 7 * 1280 + 7 >= cols * rows || index - 7 * 1280 - 7 < 0) return 127

        val average = getIntegralAverage(index, integral)
        return average * 78 / 100
    }

}
