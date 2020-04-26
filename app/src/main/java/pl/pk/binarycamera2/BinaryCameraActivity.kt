package pl.pk.binarycamera2

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Size
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import kotlinx.android.synthetic.main.main.*
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class BinaryCameraActivity : AppCompatActivity() {

    private val outputSize: Size by inject()
    private val viewModel: BinaryCameraViewModel by viewModel()
    private val buttons: List<Button> by lazy { listOf(originalPreviewButton, bradleyRsButton) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)
        initListeners()

        previewView.holder.setFixedSize(outputSize.width, outputSize.height)
        previewView.holder.addCallback(viewModel.surfaceCallback)

        if (!checkCameraPermissions()) {
            requestCameraPermissions()
        } else {
            viewModel.findAndOpenCamera()
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.closeCamera()
    }

    private fun initListeners() {
        originalPreviewButton.setOnClickListener {
            viewModel.switchMode(ProcessingMode.ORIGINAL)
        }
        bradleyRsButton.setOnClickListener {
            viewModel.switchMode(ProcessingMode.BRADLEY_RS)
        }

        viewModel.processingTime
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { processingFpsLabel.text = getString(R.string.processing_fps, 1000 / it) }

        viewModel.drawTime
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { previewFpsLabel.text = getString(R.string.preview_fps, 1000 / it) }

        viewModel.processingMode
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { mode ->
                buttons.forEach { it.enable() }
                when (mode) {
                    ProcessingMode.ORIGINAL -> originalPreviewButton.disable()
                    ProcessingMode.BRADLEY_KOTLIN -> TODO()
                    ProcessingMode.BRADLEY_CPP -> TODO()
                    ProcessingMode.BRADLEY_RS -> bradleyRsButton.disable()
                    ProcessingMode.SAUVOLA_KOTLIN -> TODO()
                    ProcessingMode.SAUVOLA_CPP -> TODO()
                    ProcessingMode.SAUVOLA_RS -> TODO()
                    else -> {
                        /* no-op */
                    }
                }
            }
    }

    private fun checkCameraPermissions(): Boolean {
        val permissionState = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        return permissionState == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermissions() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_PERMISSIONS_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == REQUEST_PERMISSIONS_REQUEST_CODE && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            viewModel.findAndOpenCamera()
        }
    }

    companion object {
        private const val REQUEST_PERMISSIONS_REQUEST_CODE = 1234
    }
}