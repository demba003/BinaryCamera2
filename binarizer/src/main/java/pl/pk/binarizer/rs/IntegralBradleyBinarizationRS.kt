package pl.pk.binarizer.rs

import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.util.Size
import pl.pk.binarizer.Processor

class IntegralBradleyBinarizationRS(private val rs: RenderScript, private val dimensions: Size) : Processor {

    private val kernel = ScriptC_IntegralBradleyBinarizationRS(rs)

    override fun process(input: Allocation, output: Allocation) {
        val monochromeBytesSize = (input.bytesSize / 1.5).toInt()
        val originalBytes = ByteArray(monochromeBytesSize)
        val integral = IntArray(monochromeBytesSize)

        input.copy1DRangeTo(0, monochromeBytesSize, originalBytes)
        calculateIntegral(originalBytes, integral)

        val integralAllocation = Allocation.createSized(rs, Element.I32(rs), monochromeBytesSize,  Allocation.USAGE_SCRIPT)
        integralAllocation.copy1DRangeFrom(0, monochromeBytesSize, integral)

        kernel._currentFrame = input
        kernel.bind_integral(integralAllocation)
        kernel._width = dimensions.width.toLong()
        kernel._height = dimensions.height.toLong()
        kernel.forEach_process(output)
        rs.finish()
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

}