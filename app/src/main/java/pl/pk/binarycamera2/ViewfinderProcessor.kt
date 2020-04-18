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

class ViewfinderProcessor(rs: RenderScript?, dimensions: Size) {
    private val yuvTypeBuilder = Type.Builder(rs, Element.YUV(rs)).apply {
        setX(dimensions.width)
        setY(dimensions.height)
        setYuvFormat(ImageFormat.YUV_420_888)
    }
    private val rgbTypeBuilder = Type.Builder(rs, Element.RGBA_8888(rs)).apply {
        setX(dimensions.width)
        setY(dimensions.height)
    }

    private val mInputAllocation = Allocation.createTyped(
        rs,
        yuvTypeBuilder.create(),
        Allocation.USAGE_IO_INPUT or Allocation.USAGE_SCRIPT
    )
    private val mOutputAllocation = Allocation.createTyped(
        rs,
        rgbTypeBuilder.create(),
        Allocation.USAGE_IO_OUTPUT or Allocation.USAGE_SCRIPT
    )
    private val mProcessingHandler =
        Handler(HandlerThread("ViewfinderProcessor").apply { start() }.looper)
    private val mBinarizationScript = ScriptC_SimpleBinarization(rs)
    private var mMode = 0

    val processingTime = PublishSubject.create<Long>()

    val inputSurface: Surface
        get() = mInputAllocation.surface

    fun setOutputSurface(output: Surface?) {
        mOutputAllocation.surface = output
    }

    fun setRenderMode(mode: Int) {
        mMode = mode
    }

    init {
        mInputAllocation.setOnBufferAvailableListener(ProcessingTask(mInputAllocation))
        setRenderMode(MODE_NORMAL)
    }

    internal inner class ProcessingTask(private val mInputAllocation: Allocation) : Runnable,
        OnBufferAvailableListener {
        private var mPendingFrames = 0

        override fun onBufferAvailable(a: Allocation) {
            synchronized(this) {
                mPendingFrames++
                mProcessingHandler.post(this)
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
                mProcessingHandler.removeCallbacks(this)
            }
            // Get to newest input
            for (i in 0 until pendingFrames) {
                mInputAllocation.ioReceive()
            }

            if (mMode == MODE_BINARY) {
                mBinarizationScript._gDoMerge = 1
            } else {
                val arr = ByteArray(mInputAllocation.bytesSize) // 1280*720*1.5
                mInputAllocation.copyTo(arr)

//                for( i in 0 until (mInputAllocation.bytesSize/1.5).toInt()) {
//                    if(arr[i] < 0) {
//                        arr[i] = -1;
//                    } else {
//                        arr[i] = 0;
//                    }
//                }

                mInputAllocation.copyFrom(arr)
                mBinarizationScript._gDoMerge = 0
            }
            mBinarizationScript._gCurrentFrame = mInputAllocation
            // Run processing pass
            mBinarizationScript.forEach_binarize(mOutputAllocation)
            mOutputAllocation.ioSend()

            val endTime = System.currentTimeMillis()
            processingTime.onNext(endTime - startTime)
        }
    }

    companion object {
        const val MODE_NORMAL = 0
        const val MODE_BINARY = 1
    }

}