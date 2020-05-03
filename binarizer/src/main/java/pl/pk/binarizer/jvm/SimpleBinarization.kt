package pl.pk.binarizer.jvm

import android.renderscript.Allocation
import android.renderscript.RenderScript
import pl.pk.binarizer.Processor
import pl.pk.binarizer.rs.ScriptC_YuvToMonochrome

class SimpleBinarization(private val rs: RenderScript) : Processor {

    private val kernel = ScriptC_YuvToMonochrome(rs)

    override fun process(input: Allocation, output: Allocation) {
        val monochromeBytesSize = (input.bytesSize / 1.5).toInt()
        val bytes = ByteArray(monochromeBytesSize)

        input.copy1DRangeTo(0, monochromeBytesSize, bytes)

        for (i in 0 until monochromeBytesSize) {
            if (bytes[i] < 0) {
                bytes[i] = -1;
            } else {
                bytes[i] = 0;
            }
        }

        input.copy1DRangeFrom(0, monochromeBytesSize, bytes)

        kernel._currentFrame = input
        kernel.forEach_process(output)
    }

}
