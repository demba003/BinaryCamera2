package pl.pk.binarizer.jvm

import android.renderscript.Allocation
import android.renderscript.RenderScript
import pl.pk.binarizer.Processor
import pl.pk.binarizer.rs.ScriptC_YuvToMonochrome
import java.util.concurrent.Executors
import java.util.concurrent.Future

class BradleyBinarization(rs: RenderScript) : Processor {

    private val kernel = ScriptC_YuvToMonochrome(rs)
    private val threadCount = Runtime.getRuntime().availableProcessors()
    private val pool = Executors.newFixedThreadPool(threadCount)

    override fun process(input: Allocation, output: Allocation) {
        val monochromeBytesSize = (input.bytesSize / 1.5).toInt()
        val originalBytes = ByteArray(monochromeBytesSize)
        val processedBytes = ByteArray(monochromeBytesSize)

        input.copy1DRangeTo(0, monochromeBytesSize, originalBytes)

        val tasks = mutableListOf<Future<Unit>>()

        for (threadId in 0 until threadCount) {
            tasks.add(
                pool.submit<Unit> { processSegment(originalBytes, processedBytes, monochromeBytesSize, threadId) }
            )
        }

        tasks.forEach { it.get() }

        input.copy1DRangeFrom(0, monochromeBytesSize, processedBytes)

        kernel._currentFrame = input
        kernel.forEach_process(output)
    }

    private fun processSegment(originalBytes: ByteArray, processedBytes: ByteArray, size: Int, threadId: Int) {
        for (i in threadId until size step threadCount) {
            val th = threshold(originalBytes, i, 1280, 720)
            if ((originalBytes[i].toInt() and 0xFF) > th) {
                processedBytes[i] = -1
            } else {
                processedBytes[i] = 0
            }
        }
    }

    private fun threshold(data: ByteArray, index: Int, cols: Int, rows: Int): Int {
        var sum: Long = 0

        val start = index - cols * 7 - 7
        val maxOffset = 7 * 2 * cols + 7 * 2

        if (start + maxOffset > cols * rows || start < 0) return 127

        for (y in 0 until 7 * 2) {
            for (x in 0 until 7 * 2) {
                sum += data[start + y * cols + x].toInt() and 0xFF
            }
        }

        val average = (sum / 225).toInt()
        return average * 78 / 100
    }

}
