package pl.pk.binarycamera2

import android.renderscript.Allocation
import android.renderscript.RenderScript
import android.util.Log
import android.view.Surface
import io.reactivex.rxjava3.subjects.PublishSubject
import org.koin.core.KoinComponent
import org.koin.core.inject
import org.koin.core.qualifier.named
import pl.pk.binarizer.Benchmarkable
import pl.pk.binarizer.ProcessingMode
import pl.pk.binarizer.Processor
import pl.pk.binarizer.rs.YuvToMonochrome
import kotlin.concurrent.thread

class ProcessorProxy(rs: RenderScript) : Processor, Benchmarkable, KoinComponent {
    private val inputAllocation: Allocation by inject(named("Input"))
    private val outputAllocation: Allocation by inject(named("Output"))
    private var currentMode = ProcessingMode.ORIGINAL

    val processingTime: PublishSubject<Long> = PublishSubject.create()
    val processingMode: PublishSubject<ProcessingMode> = PublishSubject.create()

    override var processedFrames: Int = 0
    override var totalProcessingTime: Long = 0
    override var lastFrameTime: Long = 0

    private val modes = mapOf(
        ProcessingMode.ORIGINAL to YuvToMonochrome(rs),

        ProcessingMode.SIMPLE_KT to pl.pk.binarizer.jvm.SimpleBinarization(rs),
        ProcessingMode.SIMPLE_CPP to pl.pk.binarizer.cpp.SimpleBinarization(rs),
        ProcessingMode.SIMPLE_RS to pl.pk.binarizer.rs.SimpleBinarization(rs),

        ProcessingMode.BRADLEY_KT to pl.pk.binarizer.jvm.BradleyBinarization(rs),
        ProcessingMode.BRADLEY_INT_KT to pl.pk.binarizer.jvm.BradleyIntegralBinarization(rs),
        ProcessingMode.BRADLEY_CPP to pl.pk.binarizer.cpp.BradleyBinarization(rs),
        ProcessingMode.BRADLEY_INT_CPP to pl.pk.binarizer.cpp.BradleyIntegralBinarization(rs),
        ProcessingMode.BRADLEY_RS to pl.pk.binarizer.rs.BradleyBinarizationFS(rs)
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
    }

    override fun benchmark() {
        thread {
            ProcessingMode.values().forEach {
                setProcessingMode(it)

                while (processedFrames < 100) { }

                Log.i("BENCHMARK", "$it | $processedFrames | ${getAverageTime()} | ${getAverageFPS()}")
                totalProcessingTime = 0
                processedFrames = 0
            }
        }
    }
}
