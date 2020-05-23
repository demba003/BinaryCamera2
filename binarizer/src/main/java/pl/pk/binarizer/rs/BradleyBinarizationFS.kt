package pl.pk.binarizer.rs

import android.renderscript.Allocation
import android.renderscript.RenderScript
import pl.pk.binarizer.Processor

class BradleyBinarizationFS(rs: RenderScript) : Processor {

    private val kernel = ScriptC_BradleyBinarizationFS(rs)

    override fun process(input: Allocation, output: Allocation) {
        kernel._currentFrame = input
        kernel.forEach_process(output)
    }

}