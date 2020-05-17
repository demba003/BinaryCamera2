package pl.pk.binarizer.jvm

import android.renderscript.Allocation
import android.renderscript.RenderScript
import pl.pk.binarizer.Processor
import pl.pk.binarizer.rs.ScriptC_YuvToMonochrome
import java.util.concurrent.Executors
import java.util.concurrent.Future

class SimpleBinarization(rs: RenderScript) : Processor {

    private val kernel = ScriptC_YuvToMonochrome(rs)
    private val threadCount = Runtime.getRuntime().availableProcessors()
    private val pool = Executors.newFixedThreadPool(threadCount)

    override fun process(input: Allocation, output: Allocation) {
        val monochromeBytesSize = (input.bytesSize / 1.5).toInt()
        val bytes = ByteArray(monochromeBytesSize)

        input.copy1DRangeTo(0, monochromeBytesSize, bytes)

        val tasks = mutableListOf<Future<Unit>>()

        for (threadId in 0 until threadCount) {
            tasks.add(
                pool.submit<Unit> { processSegment(bytes, monochromeBytesSize, threadId) }
            )
        }

        tasks.forEach { it.get() }

        input.copy1DRangeFrom(0, monochromeBytesSize, bytes)

        kernel._currentFrame = input
        kernel.forEach_process(output)
    }


    private fun processSegment(bytes: ByteArray, size: Int, threadId: Int) {
        for (i in threadId until size step threadCount) {
            if (bytes[i] < 0) {
                bytes[i] = -1
            } else {
                bytes[i] = 0
            }
        }
    }

}
