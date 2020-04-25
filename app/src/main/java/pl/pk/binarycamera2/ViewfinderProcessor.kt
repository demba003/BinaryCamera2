package pl.pk.binarycamera2

import android.graphics.ImageFormat
import android.os.Handler
import android.os.HandlerThread
import android.renderscript.Allocation
import android.renderscript.Allocation.OnBufferAvailableListener
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.Type
import android.util.Size
import android.view.Surface
import io.reactivex.rxjava3.subjects.PublishSubject
import pl.pk.binarizer.rs.BradleyBinarizationFS
import pl.pk.binarizer.rs.YuvToMonochrome

class ViewfinderProcessor(rs: RenderScript, dimensions: Size) {
    private val yuvTypeBuilder = Type.Builder(rs, Element.YUV(rs)).apply {
        setX(dimensions.width)
        setY(dimensions.height)
        setYuvFormat(ImageFormat.YUV_420_888)
    }
    private val rgbTypeBuilder = Type.Builder(rs, Element.RGBA_8888(rs)).apply {
        setX(dimensions.width)
        setY(dimensions.height)
    }

    private val inputAllocation = Allocation.createTyped(
        rs,
        yuvTypeBuilder.create(),
        Allocation.USAGE_IO_INPUT or Allocation.USAGE_SCRIPT
    )
    private val outputAllocation = Allocation.createTyped(
        rs,
        rgbTypeBuilder.create(),
        Allocation.USAGE_IO_OUTPUT or Allocation.USAGE_SCRIPT
    )
    private val processingHandler =
        Handler(HandlerThread("ViewfinderProcessor").apply { start() }.looper)

    private val originalProcessor = YuvToMonochrome(rs)
    private val bradleyFsProcessor = BradleyBinarizationFS(rs)

    var processingMode = ProcessingMode.ORIGINAL
    val processingTime: PublishSubject<Long> = PublishSubject.create<Long>()

    val inputSurface: Surface
        get() = inputAllocation.surface

    fun setOutputSurface(output: Surface?) {
        outputAllocation.surface = output
    }

    init {
        inputAllocation.setOnBufferAvailableListener(ProcessingTask(inputAllocation))
    }

    internal inner class ProcessingTask(private val mInputAllocation: Allocation) : Runnable,
        OnBufferAvailableListener {
        private var mPendingFrames = 0

        override fun onBufferAvailable(a: Allocation) {
            synchronized(this) {
                mPendingFrames++
                processingHandler.post(this)
            }
        }
        override fun run() {
            val startTime = System.currentTimeMillis()

            // Find out how many frames have arrived
            var pendingFrames: Int
            synchronized(this) {
                pendingFrames = mPendingFrames
                mPendingFrames = 0
                // Discard extra messages in case processing is slower than frame rate
                processingHandler.removeCallbacks(this)
            }
            // Get to newest input
            for (i in 0 until pendingFrames) {
                mInputAllocation.ioReceive()
            }

            when (processingMode) {
                ProcessingMode.ORIGINAL -> {
                    originalProcessor.process(mInputAllocation, outputAllocation)
                }

                ProcessingMode.BRADLEY_RS -> {
                    bradleyFsProcessor.process(inputAllocation, outputAllocation)
                }

                else -> {
                    // TODO
                }
            }



//                val arr = ByteArray(mInputAllocation.bytesSize) // 1280*720*1.5
//                mInputAllocation.copyTo(arr)
//
//               for( i in 0 until (mInputAllocation.bytesSize/1.5).toInt()) {
//                    val th = bradleyThresholdCPU(arr, i)
//                    if(arr[i] < 0) {
//                        arr[i] = -1;
//                    } else {
//                        arr[i] = 0;
//                   }
//                }
//
//                mInputAllocation.copyFrom(arr)
//                mBinarizationScript._gDoMerge = 0

            outputAllocation.ioSend()

            val endTime = System.currentTimeMillis()
            processingTime.onNext(endTime - startTime)
        }
    }

    companion object {
        const val MODE_NORMAL = 0
        const val MODE_BINARY = 1
    }

}