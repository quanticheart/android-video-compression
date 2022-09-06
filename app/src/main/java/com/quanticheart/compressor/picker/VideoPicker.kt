package com.quanticheart.compressor.picker

import android.content.Context
import android.os.Bundle
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.quanticheart.core.components.videotrimmer.R
import com.quanticheart.core.components.videotrimmer.databinding.DialogVideoPickerBinding


abstract class VideoPicker(context: Context) : BottomSheetDialog(context) {

    private lateinit var binding: DialogVideoPickerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DialogVideoPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.camera.setOnClickListener {
            dismiss()
            onCameraClicked()
        }
        binding.gallery.setOnClickListener {
            dismiss()
            onGalleryClicked()
        }
    }

    protected abstract fun onCameraClicked()

    protected abstract fun onGalleryClicked()
}