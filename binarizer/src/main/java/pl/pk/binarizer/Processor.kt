package pl.pk.binarizer

import android.renderscript.Allocation

interface Processor {
    fun process(input: Allocation, output: Allocation)
}
