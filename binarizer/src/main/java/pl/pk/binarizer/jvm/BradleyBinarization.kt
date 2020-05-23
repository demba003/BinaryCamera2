package pl.pk.binarizer.jvm

import android.renderscript.Allocation
import android.renderscript.RenderScript
import android.util.Size
import pl.pk.binarizer.Processor
import pl.pk.binarizer.rs.ScriptC_YuvToMonochrome
import java.util.concurrent.Executors
import java.util.concurrent.Future

class BradleyBinarization(rs: RenderScript, private val dimensions: Size) : Processor {

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
            val th = threshold(originalBytes, i)
            if ((originalBytes[i].toInt() and 0xFF) > th) {
                processedBytes[i] = -1
            } else {
                processedBytes[i] = 0
            }
        }
    }

    private fun threshold(data: ByteArray, index: Int): Int {
        var sum: Long = 0

        val start = index - dimensions.width * radius - radius
        val maxOffset = radius * 2 * dimensions.width + radius * 2

        if (start + maxOffset > dimensions.width * dimensions.height || start < 0) return 127

        for (y in 0 until radius * 2) {
            for (x in 0 until radius * 2) {
                sum += data[start + y * dimensions.width + x].toInt() and 0xFF
            }
        }

        val average = (sum / area).toInt()
        return average * 78 / 100
    }

    companion object {
        private const val radius = 7
        private const val area = ((radius * 2 + 1) * (radius * 2 + 1))
    }

}
