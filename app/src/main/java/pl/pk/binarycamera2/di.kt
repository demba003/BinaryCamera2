package pl.pk.binarycamera2

import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.Type
import android.util.Range
import android.util.Size
import androidx.core.content.getSystemService
import io.reactivex.rxjava3.subjects.PublishSubject
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

val mainModule = module {
    viewModel { BinaryCameraViewModel() }

    factory { RenderScript.create(androidContext()) }
    single { ProcessorProxy(get()) }

    factory(named("YuvTypeBuilder")) {
        Type.Builder(get(), Element.YUV(get())).run {
            setX(get<Size>().width)
            setY(get<Size>().height)
            setYuvFormat(ImageFormat.YUV_420_888)
            create()
        }
    }

    factory(named("RGBATypeBuilder")) {
        Type.Builder(get(), Element.RGBA_8888(get())).run {
            setX(get<Size>().width)
            setY(get<Size>().height)
            create()
        }
    }

    factory(named("Input")) {
        Allocation.createTyped(
            get(),
            get(named("YuvTypeBuilder")),
            Allocation.USAGE_IO_INPUT or Allocation.USAGE_SCRIPT
        )
    }

    factory(named("Output")) {
        Allocation.createTyped(
            get(),
            get(named("RGBATypeBuilder")),
            Allocation.USAGE_IO_OUTPUT or Allocation.USAGE_SCRIPT
        )
    }
}

val cameraModule = module {
    factory { Size(1280, 720) }
    factory { androidContext().getSystemService<CameraManager>()!! }

    factory { (device: CameraDevice) ->
        device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).run {
            set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range.create(30, 30))
            addTarget(get<ProcessorProxy>().getInputSurface())
            build()
        }
    }

    factory<CameraCaptureSession.CaptureCallback> { (subject: PublishSubject<Long>) ->
        object : CameraCaptureSession.CaptureCallback() {
            var timestamp: Long = 0

            override fun onCaptureCompleted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                result: TotalCaptureResult
            ) {
                subject.onNext(System.currentTimeMillis() - timestamp)
                timestamp = System.currentTimeMillis()
            }
        }
    }

}
