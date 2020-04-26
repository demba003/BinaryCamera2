package pl.pk.binarycamera2

import android.hardware.camera2.*
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.renderscript.RenderScript
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
    single { ViewfinderProcessor(get(), get()) }
    single { get<ViewfinderProcessor>().inputSurface }
}

val cameraModule = module {
    factory { Size(1280, 720) }
    factory { androidContext().getSystemService<CameraManager>()!! }

    factory(named("CameraHandler")) {
        val cameraThread = HandlerThread("CameraThread").apply { start() }
        Handler(cameraThread.looper)
    }

    factory(named("MainHandler")) {
        Handler(Looper.getMainLooper())
    }

    factory { (device: CameraDevice) ->
        device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).run {
            set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range.create(30, 30))
            addTarget(get())
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
