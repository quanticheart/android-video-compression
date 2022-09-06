package com.quanticheart.compressor

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import com.quanticheart.core.components.videotrimmer.R
import com.quanticheart.core.components.videotrimmer.databinding.ActivityVideoTrimmerBinding
import com.quanticheart.core.components.videotrimmer.interfaces.OnTrimVideoListener
import com.quanticheart.compressor.Constants.EXTRA_VIDEO_PATH

class VideoTrimmerActivity : BaseActivity(), OnTrimVideoListener {

    private var isActive = false
    private var dialog: CustomProgressDialog? = null

    private lateinit var binding: ActivityVideoTrimmerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoTrimmerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initProgressDialog()
        trimStarted()

        val path: String? = intent.getStringExtra(EXTRA_VIDEO_PATH)
        if (path != null) {
            binding.timeLine.setOnTrimVideoListener(this)
            binding.timeLine.setVideoURI(Uri.parse(path))
        } else {
            showToastLong(getString(R.string.toast_cannot_retrieve_selected_video))
            finish()
        }
        dialog?.dismiss()
    }

    private fun initProgressDialog() {
        dialog = CustomProgressDialog.newInstance("Processing, Please wait...")
    }

    override fun onResume() {
        super.onResume()
        isActive = true
    }

    override fun getResult(uri: Uri?) {
        runOnUiThread {
            binding.tvCroppingMessage.visibility = View.GONE
            if (isActive && dialog != null && dialog!!.isVisible) {
                dialog?.dismiss()
            }
        }
        Constants.croppedVideoURI = uri.toString()
        val intent = Intent()
        intent.data = uri
        setResult(Activity.RESULT_OK, intent)
        finish()
    }

    override fun trimStarted() {
        dialog?.show(supportFragmentManager, CustomProgressDialog.TAG)
    }

    override fun onBackPressed() {
        cancelAction()
    }

    override fun cancelAction() {
        binding.timeLine.destroy()
        runOnUiThread {
            binding.tvCroppingMessage.visibility = View.GONE
            if (dialog != null && dialog!!.isVisible)
                dialog!!.dismiss()
        }
        finish()
    }

    override fun onStop() {
        super.onStop()
        isActive = false
        if (dialog != null && dialog!!.isVisible) {
            dialog?.dismiss()
        }
    }
}
