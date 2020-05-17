package pl.pk.binarizer.cpp

import android.renderscript.Allocation
import android.renderscript.RenderScript
import pl.pk.binarizer.Processor
import pl.pk.binarizer.rs.ScriptC_YuvToMonochrome

class SimpleBinarization(rs: RenderScript) : Processor {

    private val kernel = ScriptC_YuvToMonochrome(rs)
    private external fun binarize(input: ByteArray, output: ByteArray, size: Int)

    init {
        System.loadLibrary("native-lib")
    }

    override fun process(input: Allocation, output: Allocation) {
        val monochromeBytesSize = (input.bytesSize / 1.5).toInt()
        val originalBytes = ByteArray(monochromeBytesSize)
        val processedBytes = ByteArray(monochromeBytesSize)

        input.copy1DRangeTo(0, monochromeBytesSize, originalBytes)

        binarize(originalBytes, processedBytes, monochromeBytesSize)

        input.copy1DRangeFrom(0, monochromeBytesSize, processedBytes)

        kernel._currentFrame = input
        kernel.forEach_process(output)
    }

}
