package pl.pk.binarizer.rs

import android.renderscript.Allocation
import android.renderscript.RenderScript
import android.util.Size
import pl.pk.binarizer.Processor

class BradleyBinarizationRS(private val rs: RenderScript, private val dimensions: Size) : Processor {

    private val kernel = ScriptC_BradleyBinarizationRS(rs)

    override fun process(input: Allocation, output: Allocation) {
        kernel._currentFrame = input
        kernel._width = dimensions.width.toLong()
        kernel._height = dimensions.height.toLong()
        kernel.forEach_process(output)
        rs.finish()
    }

}