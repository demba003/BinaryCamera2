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
import pl.pk.binarizer.ProcessingMode

class BinaryCameraActivity : AppCompatActivity() {

    private val outputSize: Size by inject()
    private val viewModel: BinaryCameraViewModel by viewModel()
    private val buttons: List<Button> by lazy {
        listOf(
            originalPreviewButton,
            simpleKotlinButton, simpleKtNativeButton, simpleCppButton, simpleRsButton,
            bradleyKotlinButton, bradleyKotlinNativeButton, bradleyCppButton, bradleyFsButton, bradleyRsButton,
            bradleyIntKotlinButton, bradleyIntKotlinNativeButton, bradleyIntCppButton, bradleyIntRsButton, bradleyIntFsButton
        )
    }

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
        originalPreviewButton.setOnClickListener { viewModel.switchMode(ProcessingMode.ORIGINAL) }

        simpleKotlinButton.setOnClickListener { viewModel.switchMode(ProcessingMode.SIMPLE_KT) }
        simpleKtNativeButton.setOnClickListener { viewModel.switchMode(ProcessingMode.SIMPLE_KT_NATIVE) }
        simpleCppButton.setOnClickListener { viewModel.switchMode(ProcessingMode.SIMPLE_CPP) }
        simpleRsButton.setOnClickListener { viewModel.switchMode(ProcessingMode.SIMPLE_RS) }

        bradleyKotlinButton.setOnClickListener { viewModel.switchMode(ProcessingMode.BRADLEY_KT) }
        bradleyIntKotlinButton.setOnClickListener { viewModel.switchMode(ProcessingMode.BRADLEY_INT_KT) }

        bradleyKotlinNativeButton.setOnClickListener { viewModel.switchMode(ProcessingMode.BRADLEY_KT_NATIVE) }
        bradleyIntKotlinNativeButton.setOnClickListener { viewModel.switchMode(ProcessingMode.BRADLEY_INT_KT_NATIVE) }

        bradleyCppButton.setOnClickListener { viewModel.switchMode(ProcessingMode.BRADLEY_CPP) }
        bradleyIntCppButton.setOnClickListener { viewModel.switchMode(ProcessingMode.BRADLEY_INT_CPP) }

        bradleyRsButton.setOnClickListener { viewModel.switchMode(ProcessingMode.BRADLEY_RS) }
        bradleyIntRsButton.setOnClickListener { viewModel.switchMode(ProcessingMode.BRADLEY_INT_RS) }

        bradleyFsButton.setOnClickListener { viewModel.switchMode(ProcessingMode.BRADLEY_FS) }
        bradleyIntFsButton.setOnClickListener { viewModel.switchMode(ProcessingMode.BRADLEY_INT_FS) }

        viewModel.processingTime
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                if (it != 0L)
                    processingFpsLabel.text = getString(R.string.processing_fps, 1000 / it)
            }

        viewModel.drawTime
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                if (it != 0L)
                    previewFpsLabel.text = getString(R.string.preview_fps, 1000 / it)
            }

        viewModel.processingMode
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { mode ->
                buttons.forEach { it.enable() }
                when (mode) {
                    ProcessingMode.ORIGINAL -> originalPreviewButton.disable()
                    ProcessingMode.BRADLEY_KT -> bradleyKotlinButton.disable()
                    ProcessingMode.BRADLEY_INT_KT -> bradleyIntKotlinButton.disable()
                    ProcessingMode.BRADLEY_KT_NATIVE -> bradleyKotlinNativeButton.disable()
                    ProcessingMode.BRADLEY_INT_KT_NATIVE -> bradleyIntKotlinNativeButton.disable()
                    ProcessingMode.BRADLEY_CPP -> bradleyCppButton.disable()
                    ProcessingMode.BRADLEY_INT_CPP -> bradleyIntCppButton.disable()
                    ProcessingMode.BRADLEY_RS -> bradleyRsButton.disable()
                    ProcessingMode.BRADLEY_INT_RS -> bradleyIntRsButton.disable()
                    ProcessingMode.BRADLEY_FS -> bradleyFsButton.disable()
                    ProcessingMode.BRADLEY_INT_FS -> bradleyIntFsButton.disable()
                    ProcessingMode.SIMPLE_KT -> simpleKotlinButton.disable()
                    ProcessingMode.SIMPLE_KT_NATIVE -> simpleKtNativeButton.disable()
                    ProcessingMode.SIMPLE_CPP -> simpleCppButton.disable()
                    ProcessingMode.SIMPLE_RS -> simpleRsButton.disable()
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