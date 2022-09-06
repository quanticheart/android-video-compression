@file:Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")

package com.quanticheart.compressor

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.snackbar.Snackbar
import com.quanticheart.core.components.videotrimmer.R
import com.quanticheart.core.components.videotrimmer.databinding.ActivityMainBinding
import com.quanticheart.compressor.Constants.EXTRA_VIDEO_PATH
import com.quanticheart.compressor.picker.VideoPicker
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.*

class MainActivity : BaseActivity(), View.OnClickListener {

    private val requestVideoTrimmerResult = 342
    private val requestVideoTrimmer = 0x12
    private var thumbFile: File? = null
    private var selectedVideoName: String? = null
    private lateinit var simpleOptions: RequestOptions

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setUpToolbar("Video Trimmer Example")
        binding.btnSelectVideo.setOnClickListener(this)
        simpleOptions = RequestOptions().centerCrop().placeholder(R.color.blackOverlay)
            .error(R.color.blackOverlay).diskCacheStrategy(DiskCacheStrategy.RESOURCE)
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.btnSelectVideo -> checkForPermission()
        }
    }

    private fun checkForPermission() {
        requestAppPermissions(
            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
            PERMISSION_STORAGE,
            object : SetPermissionListener {
                override fun onPermissionGranted(requestCode: Int) {
                    selectVideoDialog()
                }

                override fun onPermissionDenied(requestCode: Int) {
                    showSnackbar(binding.root,
                        getString(R.string.critical_permission_denied),
                        Snackbar.LENGTH_INDEFINITE,
                        getString(
                            R.string.allow
                        ),
                        object : OnSnackbarActionListener {
                            override fun onAction() {
                                checkForPermission()
                            }
                        })
                }

                override fun onPermissionNeverAsk(requestCode: Int) {
                    showPermissionSettingDialog(getString(R.string.permission_gallery_camera))
                }
            })
    }

    private fun selectVideoDialog() {
        object : VideoPicker(this) {
            override fun onCameraClicked() {
                openVideoCapture()
            }

            override fun onGalleryClicked() {
                val intent = Intent()
                intent.setTypeAndNormalize("video/*")
                intent.action = Intent.ACTION_GET_CONTENT
                intent.addCategory(Intent.CATEGORY_OPENABLE)
                startActivityForResult(
                    Intent.createChooser(
                        intent,
                        getString(R.string.select_video)
                    ), requestVideoTrimmer
                )
            }
        }.show()
    }

    private fun openVideoCapture() {
        val videoCapture = Intent(MediaStore.ACTION_VIDEO_CAPTURE)
        startActivityForResult(videoCapture, requestVideoTrimmer)
    }

    private fun startTrimActivity(uri: Uri) {
        val intent = Intent(this, VideoTrimmerActivity::class.java)
        intent.putExtra(
            EXTRA_VIDEO_PATH,
            com.quanticheart.core.compression.FileUtils.getPath(this, uri)
        )
        startActivityForResult(intent, requestVideoTrimmerResult)
    }

    private fun getFileFromBitmap(bmp: Bitmap): File {
        /*//create a file to write bitmap data*/
        thumbFile = File(this.cacheDir, "thumb_$selectedVideoName.png")
        try {
            thumbFile!!.createNewFile()
        } catch (e: IOException) {
            e.printStackTrace()
        }

        /*//Convert bitmap to byte array*/
        val bos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 0 /*ignored for PNG*/, bos)
        val bitmapdata = bos.toByteArray()
        /*//write the bytes in file*/
        try {
            val fos = FileOutputStream(thumbFile!!)
            fos.write(bitmapdata)
            fos.flush()
            fos.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return thumbFile!!
    }

    @Deprecated("Deprecated in Java")
    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                requestVideoTrimmer -> {
                    val selectedUri = data!!.data
                    if (selectedUri != null) {
                        startTrimActivity(selectedUri)
                    } else {
                        showToastShort(getString(R.string.toast_cannot_retrieve_selected_video))
                    }
                }
                requestVideoTrimmerResult -> {
                    val selectedVideoUri = data!!.data

                    if (selectedVideoUri != null) {
                        selectedVideoName = data.data!!.lastPathSegment

                        val thumb = ThumbnailUtils.createVideoThumbnail(
                            selectedVideoUri.path ?: "",
                            MediaStore.Images.Thumbnails.FULL_SCREEN_KIND
                        )
                        Glide.with(this).load(thumb?.let { getFileFromBitmap(it) })
                            .apply(simpleOptions)
                            .into(binding.selectedVideoThumb)

                        //create destination directory
                        val f = File(
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                                .toString() + "/Silicompressor/videos"
                        )
                        //  String path = FileUtils.getRealPath(this, selectedVideoUri);
                        val videoFilePath = selectedVideoUri.path
                        Log.d(TAG, "onActivityResult: " + videoFilePath!!)
                        //compress and output new video specs
                        if (f.mkdirs() || f.isDirectory) {
                            VideoCompressAsyncTask(this).execute(videoFilePath, f.path)
                        }
                    } else {
                        showToastShort(getString(R.string.toast_cannot_retrieve_selected_video))
                    }
                }
            }
        }
    }

    @SuppressLint("StaticFieldLeak")
    internal inner class VideoCompressAsyncTask(private var mContext: Context) :
        AsyncTask<String, String, String>() {

        override fun onPreExecute() {
            super.onPreExecute()
            binding.compressionMsg.visibility = View.VISIBLE
            binding.picDescription.visibility = View.GONE
        }

        override fun doInBackground(vararg paths: String): String? {
            var filePath: String? = null
            try {

                filePath = com.quanticheart.core.compression.SiliCompressor.with(mContext)
                    .compressVideo(paths[0], paths[1])
            } catch (e: Exception) {
                e.printStackTrace()
            }

            return filePath
        }

        override fun onPostExecute(compressedFilePath: String) {
            super.onPostExecute(compressedFilePath)
            val imageFile = File(compressedFilePath)
            val length = imageFile.length() / 1024f // Size in KB
            val value = if (length >= 1024) {
                (length / 1024f).toString() + " MB"
            } else {
                "$length KB"
            }
            val text = String.format(
                Locale.US,
                "%s\nName: %s\nSize: %s",
                getString(R.string.video_compression_complete),
                imageFile.name,
                value
            )
            binding.compressionMsg.visibility = View.GONE
            binding.picDescription.visibility = View.VISIBLE
            binding.picDescription.text = text
            Log.i("Silicompressor", "Path: $compressedFilePath")
        }
    }

    companion object {
        const val TAG = "VideoCompressor"
        const val PERMISSION_STORAGE = 100
    }
}
