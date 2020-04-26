package pl.pk.binarycamera2

import android.annotation.SuppressLint
import android.hardware.camera2.*
import android.os.Handler
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import androidx.lifecycle.ViewModel
import io.reactivex.rxjava3.subjects.PublishSubject
import org.koin.core.KoinComponent
import org.koin.core.inject
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named

class BinaryCameraViewModel : ViewModel(), KoinComponent {

    private val uiHandler: Handler by inject(named("MainHandler"))
    private val mCameraHandler: Handler by inject(named("CameraHandler"))
    private val processingSurface: Surface by inject()
    private val previewRequest: CaptureRequest by inject { parametersOf(cameraDevice) }
    private val processor: ViewfinderProcessor by inject()
    private val cameraManager: CameraManager by inject()

    private var cameraInfo: CameraCharacteristics? = null
    private var previewSurface: Surface? = null
    private var cameraDevice: CameraDevice? = null
    private var cameraSession: CameraCaptureSession? = null
    private var surfaces: List<Surface>? = null

    val processingMode: PublishSubject<ProcessingMode> = PublishSubject.create<ProcessingMode>()
    val processingTime: PublishSubject<Long> by lazy { processor.processingTime }
    val drawTime: PublishSubject<Long> = PublishSubject.create()

    private val captureCallback: CameraCaptureSession.CaptureCallback by inject { parametersOf(drawTime) }

    private val sessionListener: CameraCaptureSession.StateCallback = object : CameraCaptureSession.StateCallback() {
        override fun onConfigured(session: CameraCaptureSession) {
            cameraSession = session
            uiHandler.post {
                if (cameraDevice != null) {
                    setRepeatingRequest()
                }
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
        processor.processingMode = mode
        processingMode.onNext(mode)
    }

    fun findAndOpenCamera() {
        val cameraIds = cameraManager.cameraIdList

        for (id in cameraIds) {
            val info = cameraManager.getCameraCharacteristics(id)
            val facing = info.get(CameraCharacteristics.LENS_FACING)

            if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                cameraInfo = info
                openCamera(id)
                setupProcessor()
                break
            }
        }
    }

    private fun setupProcessor() {
        if (previewSurface == null) return
        processor.setOutputSurface(previewSurface)
        setSurfaces(listOf(processingSurface))
    }

    @SuppressLint("MissingPermission")
    private fun openCamera(cameraId: String?) {
        mCameraHandler
        mCameraHandler.post {
            check(cameraDevice == null) { "Camera already open" }
            try {
                cameraManager.openCamera(cameraId!!, deviceListener, mCameraHandler)
            } catch (e: CameraAccessException) {
                Log.e(TAG, "", e)
            }
        }
    }

    fun closeCamera() {
        cameraDevice?.close()
        cameraDevice = null
        cameraSession = null
        surfaces = null
    }

    private fun setSurfaces(surfaces: List<Surface>) {
        mCameraHandler.post {
            this.surfaces = surfaces
            startCameraSession()
        }
    }

    private fun setRepeatingRequest() {
        mCameraHandler.post {
            try {
                cameraSession!!.setRepeatingRequest(previewRequest, captureCallback, uiHandler)
            } catch (e: CameraAccessException) {
                Log.e(TAG, "", e)
            }
        }
    }

    private fun startCameraSession() {
        if (cameraDevice == null || surfaces == null) return
        try {
            cameraDevice!!.createCaptureSession(surfaces!!, sessionListener, mCameraHandler)
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
