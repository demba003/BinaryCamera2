package pl.pk.binarycamera2

import android.renderscript.Allocation
import android.renderscript.RenderScript
import android.util.Log
import android.util.Size
import android.view.Surface
import io.reactivex.rxjava3.subjects.PublishSubject
import org.koin.core.KoinComponent
import org.koin.core.inject
import org.koin.core.qualifier.named
import pl.pk.binarizer.Benchmarkable
import pl.pk.binarizer.ProcessingMode
import pl.pk.binarizer.Processor
import pl.pk.binarizer.rs.YuvToMonochrome

class ProcessorProxy(rs: RenderScript) : Processor, Benchmarkable, KoinComponent {
    private val inputAllocation: Allocation by inject(named("Input"))
    private val outputAllocation: Allocation by inject(named("Output"))
    private val previewSize: Size by inject()
    private var currentMode = ProcessingMode.ORIGINAL

    val processingTime: PublishSubject<Long> = PublishSubject.create()
    val processingMode: PublishSubject<ProcessingMode> = PublishSubject.create()

    override var processedFrames: Int = 0
    override var totalProcessingTime: Long = 0
    override var lastFrameTime: Long = 0

    private val modes = mapOf(
        ProcessingMode.ORIGINAL to YuvToMonochrome(rs),

        ProcessingMode.SIMPLE_KT to pl.pk.binarizer.jvm.SimpleBinarization(rs),
        ProcessingMode.SIMPLE_KT_NATIVE to pl.pk.binarizer.ktnative.SimpleBinarization(rs),
        ProcessingMode.SIMPLE_CPP to pl.pk.binarizer.cpp.SimpleBinarization(rs),
        ProcessingMode.SIMPLE_RS to pl.pk.binarizer.rs.SimpleBinarization(rs),

        ProcessingMode.BRADLEY_KT to pl.pk.binarizer.jvm.BradleyBinarization(rs, previewSize),
        ProcessingMode.BRADLEY_INT_KT to pl.pk.binarizer.jvm.BradleyIntegralBinarization(rs, previewSize),

        ProcessingMode.BRADLEY_KT_NATIVE to pl.pk.binarizer.ktnative.BradleyBinarization(rs, previewSize),
        ProcessingMode.BRADLEY_INT_KT_NATIVE to pl.pk.binarizer.ktnative.BradleyIntegralBinarization(rs, previewSize),

        ProcessingMode.BRADLEY_CPP to pl.pk.binarizer.cpp.BradleyBinarization(rs, previewSize),
        ProcessingMode.BRADLEY_INT_CPP to pl.pk.binarizer.cpp.BradleyIntegralBinarization(rs, previewSize),

        ProcessingMode.BRADLEY_RS to pl.pk.binarizer.rs.BradleyBinarizationRS(rs, previewSize),
        ProcessingMode.BRADLEY_INT_RS to pl.pk.binarizer.rs.IntegralBradleyBinarizationRS(rs, previewSize),

        ProcessingMode.BRADLEY_FS to pl.pk.binarizer.rs.BradleyBinarizationFS(rs, previewSize)
    )

    init {
        inputAllocation.setOnBufferAvailableListener {
            inputAllocation.ioReceive()
            process(inputAllocation, outputAllocation)
            outputAllocation.ioSend()
            processingTime.onNext(lastFrameTime)
        }
    }

    fun setProcessingMode(mode: ProcessingMode) {
        currentMode = mode
        processingMode.onNext(mode)
        totalProcessingTime = 0
        processedFrames = 0
    }

    fun setOutputSurface(output: Surface?) {
        outputAllocation.surface = output
    }
    fun getInputSurface(): Surface = inputAllocation.surface

    override fun process(input: Allocation, output: Allocation) {
        val start = System.currentTimeMillis()

        modes[currentMode]?.process(input, output)

        val end = System.currentTimeMillis()
        lastFrameTime = end - start
        processedFrames++
        totalProcessingTime += lastFrameTime

        if (processedFrames == 100) {
            Log.i("BENCHMARK", "$currentMode | $processedFrames | $totalProcessingTime | ${getAverageTime()} | ${getAverageFPS()}")
            totalProcessingTime = 0
            processedFrames = 0
        }
    }

}
