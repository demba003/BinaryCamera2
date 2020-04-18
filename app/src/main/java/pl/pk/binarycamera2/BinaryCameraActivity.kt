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

class BinaryCameraActivity : AppCompatActivity(), SurfaceHolder.Callback, CameraOps.CameraReadyListener {
    private val mUiHandler: Handler = Handler(Looper.getMainLooper())
    private val outputSize = Size(1280, 720)
    private var mCameraInfo: CameraCharacteristics? = null
    private var mPreviewSurface: Surface? = null
    private val mProcessingNormalSurface: Surface by lazy { mProcessor.inputSurface }
    private val mPreviewRequest: CaptureRequest by lazy {
        mCameraOps.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).run {
            set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range.create(30, 30))
            addTarget(mProcessingNormalSurface)
            build()
        }
    }
    private val mRS: RenderScript by lazy { RenderScript.create(this) }
    private val mProcessor: ViewfinderProcessor by lazy { ViewfinderProcessor(mRS, outputSize) }
    private val mCameraManager: CameraManager by lazy { getSystemService<CameraManager>()!! }
    private val mCameraOps: CameraOps by lazy { CameraOps(mCameraManager, this, mUiHandler) }

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
        mCameraOps.closeCameraAndWait()
    }

    private fun initListeners() {
        originalPreviewButton.setOnClickListener { switchRenderMode(ViewfinderProcessor.MODE_NORMAL) }
        bradleyKotlinButton.setOnClickListener { switchRenderMode(ViewfinderProcessor.MODE_BINARY) }

        mProcessor.processingTime
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
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_PERMISSIONS_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
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

        val cameraIds = mCameraManager.cameraIdList

        for (id in cameraIds) {
            val info = mCameraManager.getCameraCharacteristics(id)
            val facing = info.get(CameraCharacteristics.LENS_FACING)

            if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                // Found suitable camera - get info, open, and set up outputs
                mCameraInfo = info
                mCameraOps.openCamera(id)
                configureSurfaces()
                break
            }
        }
    }

    private fun switchRenderMode(mode: Int) {
        mProcessor.setRenderMode(mode)
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
        if (mPreviewSurface == null) return
        mProcessor.setOutputSurface(mPreviewSurface)
        mCameraOps.setSurfaces(listOf(mProcessingNormalSurface))
    }

    /**
     * Listener for completed captures
     * Invoked on UI thread
     */
    private val mCaptureCallback: CaptureCallback = object : CaptureCallback() {
        var timestamp: Long = 0
        override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
            val time = System.currentTimeMillis() - timestamp
            Log.d("DUPA", (1000 / time).toString())
            timestamp = System.currentTimeMillis()
        }
    }

    /**
     * Callbacks for the FixedAspectSurfaceView
     */
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        mPreviewSurface = holder.surface
        setupProcessor()
    }

    override fun surfaceCreated(holder: SurfaceHolder) { // ignored
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        mPreviewSurface = null
    }

    /**
     * Callbacks for CameraOps
     */
    override fun onCameraReady() {
        switchRenderMode(0)
        mCameraOps.setRepeatingRequest(mPreviewRequest, mCaptureCallback, mUiHandler)
    }

    companion object {
        private val TAG = this::class.java.simpleName
        private const val REQUEST_PERMISSIONS_REQUEST_CODE = 34
    }
}