@file:Suppress("DEPRECATION")

package com.quanticheart.compressor

import android.app.Dialog
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Window
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.core.content.ContextCompat
import com.quanticheart.core.components.videotrimmer.R

class CustomProgressDialog : AppCompatDialogFragment() {

    companion object {

        const val TAG = "CustomProgressDialog"
        private const val KEY_PROGRESS_TEXT = "progress_text"

        fun newInstance(title: String): CustomProgressDialog {
            val dialog = CustomProgressDialog()
            val bundle = Bundle()
            bundle.putString(KEY_PROGRESS_TEXT, title)
            dialog.arguments = bundle
            return dialog
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // Use the Builder class for convenient dialog construction

        val builder = AlertDialog.Builder(requireActivity(), R.style.dialogTheme)
        val view = LayoutInflater.from(activity).inflate(R.layout.progress_dialog, null, false)
        builder.setView(view)

        val progressBar = view.findViewById<ProgressBar>(R.id.progress)
        val message = view.findViewById<TextView>(R.id.message)

        val arguments = requireArguments()
        val progressText = arguments.getString(KEY_PROGRESS_TEXT)
        message.text = progressText
        progressBar.indeterminateDrawable.setColorFilter(
            ContextCompat.getColor(
                requireContext(),
                R.color.colorAccent
            ), PorterDuff.Mode.MULTIPLY
        )
        val dialog = builder.create()
        isCancelable = false
        dialog.setCanceledOnTouchOutside(false)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            requestFeature(Window.FEATURE_NO_TITLE)
        }
        return dialog
    }
}