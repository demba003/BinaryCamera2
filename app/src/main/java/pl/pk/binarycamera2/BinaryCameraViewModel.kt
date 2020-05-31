package pl.pk.binarycamera2

import android.annotation.SuppressLint
import android.hardware.camera2.*
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import androidx.lifecycle.ViewModel
import io.reactivex.rxjava3.subjects.PublishSubject
import org.koin.core.KoinComponent
import org.koin.core.inject
import org.koin.core.parameter.parametersOf
import pl.pk.binarizer.ProcessingMode

class BinaryCameraViewModel : ViewModel(), KoinComponent {
    private val previewRequest: CaptureRequest by inject { parametersOf(cameraDevice) }
    private val processor: ProcessorProxy by inject()
    private val cameraManager: CameraManager by inject()

    private var previewSurface: Surface? = null
    private var cameraDevice: CameraDevice? = null
    private var surfaces: List<Surface>? = null

    val processingMode: PublishSubject<ProcessingMode> by lazy { processor.processingMode }
    val processingTime: PublishSubject<Long> by lazy { processor.processingTime }
    val drawTime: PublishSubject<Long> = PublishSubject.create()

    private val captureCallback: CameraCaptureSession.CaptureCallback by inject { parametersOf(drawTime) }

    private val sessionListener: CameraCaptureSession.StateCallback = object : CameraCaptureSession.StateCallback() {
        override fun onConfigured(session: CameraCaptureSession) {
            if (cameraDevice != null) {
                session.setRepeatingRequest(previewRequest, captureCallback, null)
            }
        }

        override fun onConfigureFailed(session: CameraCaptureSession) {
            Log.e(TAG, "Unable to configure the capture session")
            cameraDevice?.close()
            cameraDevice = null
        }
    }

    private val deviceListener: CameraDevice.StateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraDevice = camera
            startCameraSession()
        }

        override fun onClosed(camera: CameraDevice) {}

        override fun onDisconnected(camera: CameraDevice) {
            camera.close()
            cameraDevice = null
        }

        override fun onError(camera: CameraDevice, error: Int) {
            camera.close()
            cameraDevice = null
        }
    }

    val surfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            previewSurface = holder.surface
            setupProcessor()
        }

        override fun surfaceCreated(holder: SurfaceHolder) {}

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            previewSurface = null
        }
    }

    fun switchMode(mode: ProcessingMode) {
        processor.setProcessingMode(mode)
    }

    @SuppressLint("MissingPermission")
    fun findAndOpenCamera() {
        val cameraIds = cameraManager.cameraIdList

        for (id in cameraIds) {
            val info = cameraManager.getCameraCharacteristics(id)
            val facing = info.get(CameraCharacteristics.LENS_FACING)

            if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                cameraManager.openCamera(id, deviceListener, null)
                setupProcessor()
                break
            }
        }
    }

    private fun setupProcessor() {
        if (previewSurface == null) return

        processor.setOutputSurface(previewSurface)
        surfaces = listOf(processor.getInputSurface())

        startCameraSession()
    }

    fun closeCamera() {
        cameraDevice?.close()
        cameraDevice = null
        surfaces = null
    }

    private fun startCameraSession() {
        if (cameraDevice == null || surfaces == null) return
        try {
            cameraDevice!!.createCaptureSession(surfaces!!, sessionListener, null)
        } catch (e: CameraAccessException) {
            Log.e(TAG, "", e)
            cameraDevice?.close()
            cameraDevice = null
        }
    }

    companion object {
        private val TAG = this::class.java.simpleName
    }

}
