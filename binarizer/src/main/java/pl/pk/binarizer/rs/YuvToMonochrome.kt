package pl.pk.binarizer.rs

import android.renderscript.Allocation
import android.renderscript.RenderScript
import pl.pk.binarizer.Processor

class YuvToMonochrome(private val rs: RenderScript) : Processor {

    private val kernel = ScriptC_YuvToMonochrome(rs)

    override fun process(input: Allocation, output: Allocation) {
        kernel._currentFrame = input
        kernel.forEach_process(output)
        rs.finish()
    }

}
