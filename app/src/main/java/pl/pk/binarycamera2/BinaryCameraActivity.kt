package pl.pk.binarycamera2

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.camera2.*
import android.hardware.camera2.CameraCaptureSession.CaptureCallback
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.renderscript.RenderScript
import android.util.Log
import android.util.Range
import android.util.Size
import android.view.Surface
import android.view.SurfaceHolder
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.getSystemService
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import kotlinx.android.synthetic.main.main.*

class BinaryCameraActivity : AppCompatActivity(), SurfaceHolder.Callback,
    CameraOps.CameraReadyListener {
    private val uiHandler: Handler = Handler(Looper.getMainLooper())
    private val outputSize = Size(1280, 720)
    private var cameraInfo: CameraCharacteristics? = null
    private var previewSurface: Surface? = null
    private val processingSurface: Surface by lazy { processor.inputSurface }
    private val previewRequest: CaptureRequest by lazy {
        cameraOps.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).run {
            set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range.create(30, 30))
            addTarget(processingSurface)
            build()
        }
    }
    private val renderScript: RenderScript by lazy { RenderScript.create(this) }
    private val processor: ViewfinderProcessor by lazy { ViewfinderProcessor(renderScript, outputSize) }
    private val cameraManager: CameraManager by lazy { getSystemService<CameraManager>()!! }
    private val cameraOps: CameraOps by lazy { CameraOps(cameraManager, this, uiHandler) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)
        previewView.holder.addCallback(this)
        initListeners()

        if (!checkCameraPermissions()) {
            requestCameraPermissions()
        } else {
            findAndOpenCamera()
        }
    }

    override fun onPause() {
        super.onPause()
        // Wait until camera is closed to ensure the next application can open it
        cameraOps.closeCameraAndWait()
    }

    private fun initListeners() {
        originalPreviewButton.setOnClickListener {
            processor.processingMode = ProcessingMode.ORIGINAL
        }
        bradleyRsButton.setOnClickListener {
            processor.processingMode = ProcessingMode.BRADLEY_RS
        }

        processor.processingTime
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { processingFpsLabel.text = getString(R.string.processing_fps, 1000 / it) }
    }

    private fun checkCameraPermissions(): Boolean {
        val permissionState = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        return if (permissionState != PackageManager.PERMISSION_GRANTED) { // Camera permission has not been granted.
            Log.i(TAG, "CAMERA permission has NOT been granted.")
            false
        } else {
            Log.i(TAG, "CAMERA permission has already been granted.")
            true
        }
    }

    private fun requestCameraPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            REQUEST_PERMISSIONS_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_PERMISSIONS_REQUEST_CODE) {
            when {
                grantResults.isEmpty() -> {
                    Log.i(TAG, "User interaction was cancelled.")
                }
                grantResults[0] == PackageManager.PERMISSION_GRANTED -> {
                    findAndOpenCamera()
                }
                else -> {
                    // Permission denied.
                }
            }
        }
    }

    private fun findAndOpenCamera() {
        val cameraPermissions = checkCameraPermissions()
        if (!cameraPermissions) {
            return
        }

        val cameraIds = cameraManager.cameraIdList

        for (id in cameraIds) {
            val info = cameraManager.getCameraCharacteristics(id)
            val facing = info.get(CameraCharacteristics.LENS_FACING)

            if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                // Found suitable camera - get info, open, and set up outputs
                cameraInfo = info
                cameraOps.openCamera(id)
                configureSurfaces()
                break
            }
        }
    }

    /**
     * Configure the surfaceview and RS processing.
     */
    private fun configureSurfaces() {
        Log.i(TAG, "Resolution chosen: $outputSize")

        setupProcessor()
        previewView.holder.setFixedSize(outputSize.width, outputSize.height)
    }

    /**
     * Once camera is open and output surfaces are ready, configure the RS processing
     * and the camera device inputs/outputs.
     */
    private fun setupProcessor() {
        if (previewSurface == null) return
        processor.setOutputSurface(previewSurface)
        cameraOps.setSurfaces(listOf(processingSurface))
    }

    /**
     * Listener for completed captures
     * Invoked on UI thread
     */
    private val mCaptureCallback: CaptureCallback = object : CaptureCallback() {
        var timestamp: Long = 0
        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {
            val time = System.currentTimeMillis() - timestamp
            Log.d("DUPA", (1000 / time).toString())
            timestamp = System.currentTimeMillis()
        }
    }

    /**
     * Callbacks for the FixedAspectSurfaceView
     */
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        previewSurface = holder.surface
        setupProcessor()
    }

    override fun surfaceCreated(holder: SurfaceHolder) { // ignored
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        previewSurface = null
    }

    /**
     * Callbacks for CameraOps
     */
    override fun onCameraReady() {
        cameraOps.setRepeatingRequest(previewRequest, mCaptureCallback, uiHandler)
    }

    companion object {
        private val TAG = this::class.java.simpleName
        private const val REQUEST_PERMISSIONS_REQUEST_CODE = 34
    }
}