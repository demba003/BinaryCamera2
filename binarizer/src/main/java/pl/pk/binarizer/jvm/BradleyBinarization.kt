package pl.pk.binarizer.jvm

import android.renderscript.Allocation
import android.renderscript.RenderScript
import pl.pk.binarizer.Processor
import pl.pk.binarizer.rs.ScriptC_YuvToMonochrome

class BradleyBinarization(private val rs: RenderScript) : Processor {

    private val kernel = ScriptC_YuvToMonochrome(rs)

    override fun process(input: Allocation, output: Allocation) {
        val monochromeBytesSize = (input.bytesSize / 1.5).toInt()
        val bytes = ByteArray(monochromeBytesSize)

        input.copy1DRangeTo(0, monochromeBytesSize, bytes)

        for (i in 0 until monochromeBytesSize) {
            val th = threshold(bytes, i, 1280, 720, 7, 225)
            if (bytes[i].toInt() and 0xFF > th) {
                bytes[i] = -1;
            } else {
                bytes[i] = 0;
            }
        }

        input.copy1DRangeFrom(0, monochromeBytesSize, bytes)

        kernel._currentFrame = input
        kernel.forEach_process(output)
    }


    private fun threshold(data: ByteArray, index: Int, cols: Int, rows: Int, neighbourRadius: Int, neighbourPixels: Int): Int {
        var sum = 0

        val start = index - (cols * neighbourRadius) - neighbourRadius;
        val maxOffset = neighbourRadius * 2 * cols + neighbourRadius * 2;

        if((start + maxOffset) > (cols * rows) || start < 0) return 0;

        for(y in 0 until neighbourRadius * 2) {
            for(x in 0 until neighbourRadius * 2) {
                sum += data[start + y * cols + x];
            }
        }

        val average = sum / neighbourPixels;
        return average * 78 / 100;
    }

}
