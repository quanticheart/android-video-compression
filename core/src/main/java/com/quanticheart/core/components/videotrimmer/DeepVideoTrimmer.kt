@file:Suppress("unused", "DEPRECATION")

package com.quanticheart.core.components.videotrimmer

import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaPlayer.OnCompletionListener
import android.media.MediaPlayer.OnPreparedListener
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Message
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View.OnTouchListener
import android.widget.FrameLayout
import android.widget.RelativeLayout
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.Toast
import com.quanticheart.core.R
import com.quanticheart.core.components.videotrimmer.interfaces.OnProgressVideoListener
import com.quanticheart.core.components.videotrimmer.interfaces.OnRangeSeekBarListener
import com.quanticheart.core.components.videotrimmer.interfaces.OnTrimVideoListener
import com.quanticheart.core.components.videotrimmer.utils.BackgroundExecutor
import com.quanticheart.core.components.videotrimmer.utils.BackgroundExecutor.cancelAll
import com.quanticheart.core.components.videotrimmer.utils.BackgroundExecutor.execute
import com.quanticheart.core.components.videotrimmer.utils.TrimVideoUtils.startTrim
import com.quanticheart.core.components.videotrimmer.utils.UiThreadExecutor.cancelAll
import com.quanticheart.core.components.videotrimmer.view.RangeSeekBarView
import com.quanticheart.core.databinding.ViewTimeLineBinding
import java.io.File
import java.lang.ref.WeakReference
import java.text.DecimalFormat
import java.util.*
import kotlin.math.ceil

class DeepVideoTrimmer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet?,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), MediaPlayer.OnErrorListener, OnPreparedListener,
    OnCompletionListener, OnSeekBarChangeListener, OnRangeSeekBarListener, OnProgressVideoListener {

    private var mSrc: Uri? = null
    private var mFinalPath: String? = null
    private var mListeners: MutableList<OnProgressVideoListener>? = null
    private var mOnTrimVideoListener: OnTrimVideoListener? = null
    private var compressionRatio = (20 * 1024).toLong().toFloat() // 20 MB
    private var mDuration = 0
    private var maxFileSize = 15 * 1024 // 15 MB
    private var mTimeVideo = 0
    private var mStartPosition = 0
    private var mEndPosition = 0
    private var mOriginSizeFile: Long = 0
    private var mResetSeekBar = true
    private val mMessageHandler = MessageHandler(this)
    private var letUserProceed = false
    private var mGestureDetector: GestureDetector? = null
    private var initialLength = 0
    private val mGestureListener: SimpleOnGestureListener = object : SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            if (binding.videoLoader.isPlaying) {
                binding.iconVideoPlay.visibility = VISIBLE
                mMessageHandler.removeMessages(SHOW_PROGRESS)
                binding.videoLoader.pause()
            } else {
                binding.iconVideoPlay.visibility = GONE
                if (mResetSeekBar) {
                    mResetSeekBar = false
                    binding.videoLoader.seekTo(mStartPosition)
                }
                mMessageHandler.sendEmptyMessage(SHOW_PROGRESS)
                binding.videoLoader.start()
            }
            return true
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private val mTouchListener = OnTouchListener { _, event ->
        mGestureDetector!!.onTouchEvent(event)
        true
    }

    private lateinit var binding: ViewTimeLineBinding

    @SuppressLint("ClickableViewAccessibility")
    private fun init(context: Context) {
        binding = ViewTimeLineBinding.inflate(LayoutInflater.from(context))

        binding.sendVideo.setOnClickListener { startTrimming() }
        mListeners = ArrayList<OnProgressVideoListener>()
        mListeners?.add(this)
        mListeners?.add(binding.timeVideoView)
        binding.handlerTop.max = 1000
        binding.handlerTop.secondaryProgress = 0
        binding.timeLineBar.addOnRangeSeekBarListener(this)
        binding.timeLineBar.addOnRangeSeekBarListener(binding.timeVideoView)
        val marge = binding.timeLineBar.thumbs!![0].widthBitmap
        val widthSeek = binding.handlerTop.thumb.minimumWidth / 2
        var lp = binding.handlerTop.layoutParams as RelativeLayout.LayoutParams
        lp.setMargins(marge - widthSeek, 0, marge - widthSeek, 0)
        binding.handlerTop.layoutParams = lp
        lp = binding.timeLineView.layoutParams as RelativeLayout.LayoutParams
        lp.setMargins(marge, 0, marge, 0)
        binding.timeLineView.layoutParams = lp
        lp = binding.timeVideoView.layoutParams as RelativeLayout.LayoutParams
        lp.setMargins(marge, 0, marge, 0)
        binding.timeVideoView.layoutParams = lp
        binding.handlerTop.setOnSeekBarChangeListener(this)
        binding.videoLoader.setOnPreparedListener(this)
        binding.videoLoader.setOnCompletionListener(this)
        binding.videoLoader.setOnErrorListener(this)
        mGestureDetector = GestureDetector(getContext(), mGestureListener)
        binding.videoLoader.setOnTouchListener(mTouchListener)
        setDefaultDestinationPath()
        addView(binding.root)
    }

    private fun startTrimming() {
        if (letUserProceed) {
            if (mStartPosition <= 0 && mEndPosition >= mDuration) {
                mOnTrimVideoListener!!.getResult(mSrc)
            } else {
                binding.iconVideoPlay.visibility = VISIBLE
                binding.videoLoader.pause()
                val mediaMetadataRetriever = MediaMetadataRetriever()
                mediaMetadataRetriever.setDataSource(context, mSrc)
                val metadataKeyDuration =
                    mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!
                        .toLong()
                if (mSrc!!.path == null) {
                    return
                }
                val file = mSrc!!.path?.let { File(it) }
                if (mTimeVideo < MIN_TIME_FRAME) {
                    if (metadataKeyDuration - mEndPosition > MIN_TIME_FRAME - mTimeVideo) {
                        mEndPosition += MIN_TIME_FRAME - mTimeVideo
                    } else if (mStartPosition > MIN_TIME_FRAME - mTimeVideo) {
                        mStartPosition -= MIN_TIME_FRAME - mTimeVideo
                    }
                }
                mOnTrimVideoListener!!.trimStarted()
                file?.let {
                    startTrimVideo(
                        it,
                        mFinalPath!!,
                        mStartPosition,
                        mEndPosition,
                        mOnTrimVideoListener!!
                    )
                }
            }
        } else {
            Toast.makeText(
                context,
                "Please trim your video less than 15 MB of size",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun setVideoURI(videoURI: Uri?) {
        mSrc = videoURI
        initMediaData()
        binding.videoLoader.setVideoURI(mSrc)
        binding.videoLoader.requestFocus()
        binding.timeLineView.setVideo(mSrc!!)
    }

    fun setDestinationPath(finalPath: String) {
        mFinalPath = finalPath + File.separator
        Log.d(TAG, "Setting custom path $mFinalPath")
    }

    private fun initMediaData() {
        if (mSrc!!.path == null) {
            return
        }
        val file = mSrc!!.path?.let { File(it) }
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(file?.path)
        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
        val originalWidth = width?.let { Integer.valueOf(it) }
        val originalHeight = height?.let { Integer.valueOf(it) }
        Log.i(
            TAG,
            "checkCompressionRatio: originalWidth = $originalWidth originalHeight = $originalHeight"
        )
        if (originalWidth != null && originalHeight != null) {
            if (originalWidth <= DEFAULT_VIDEO_WIDTH || originalHeight <= DEFAULT_VIDEO_HEIGHT) {
                compressionRatio = 1024.toLong().toFloat() // 1 MB - no compression
            }
        }
        val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        val totalDuration = duration?.let { Integer.valueOf(it) }?.div(1000) ?: 0
        val originalLength = file?.length() ?: 0
        val totalFileSizeInKB = originalLength / 1024
        val compressedTotalSize = getCompressedSize(totalFileSizeInKB).toInt()
        initialLength = totalDuration
        if (compressedTotalSize <= maxFileSize) {
            mStartPosition = 0
            mEndPosition = totalDuration * 1000
            getSizeFile(false)
        } else {
            val newDuration =
                ceil((maxFileSize.toFloat() * totalDuration / compressedTotalSize).toDouble())
                    .toInt()
            val maxDuration = if (newDuration > 0) newDuration else totalDuration
            mStartPosition = 0
            mEndPosition = maxDuration * 1000
            // set size for updated duration
            var newSize = (compressedTotalSize / totalDuration * maxDuration).toLong()
            newSize = (newSize * compressionRatio).toLong()
            setVideoSize(newSize / 1024)
        }
        Log.i(TAG, "checkCompressionRatio: compressionRatio = $compressionRatio")
    }

    private fun setDefaultDestinationPath() {
        val folder = Environment.getExternalStorageDirectory()
        mFinalPath = folder.path + File.separator
        Log.d(TAG, "Setting default path $mFinalPath")
    }

    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
        var duration = (mDuration * progress / 1000L).toInt()
        if (fromUser) {
            if (duration < mStartPosition) {
                setProgressBarPosition(mStartPosition)
                duration = mStartPosition
            } else if (duration > mEndPosition) {
                setProgressBarPosition(mEndPosition)
                duration = mEndPosition
            }
            setTimeVideo(duration)
        }
    }

    override fun onStartTrackingTouch(seekBar: SeekBar) {
        mMessageHandler.removeMessages(SHOW_PROGRESS)
        binding.videoLoader.pause()
        binding.iconVideoPlay.visibility = VISIBLE
        updateProgress(false)
    }

    override fun onStopTrackingTouch(seekBar: SeekBar) {
        mMessageHandler.removeMessages(SHOW_PROGRESS)
        binding.videoLoader.pause()
        binding.iconVideoPlay.visibility = VISIBLE
        val duration = (mDuration * seekBar.progress / 1000L).toInt()
        binding.videoLoader.seekTo(duration)
        setTimeVideo(duration)
        updateProgress(false)
    }

    override fun onPrepared(mp: MediaPlayer) {
        /*        Adjust the size of the video
         so it fits on the screen*/
        val videoWidth = mp.videoWidth
        val videoHeight = mp.videoHeight
        val videoProportion = videoWidth.toFloat() / videoHeight.toFloat()
        val screenWidth = binding.layoutSurfaceView.width
        val screenHeight = binding.layoutSurfaceView.height
        val screenProportion = screenWidth.toFloat() / screenHeight.toFloat()
        val lp = binding.videoLoader.layoutParams
        if (videoProportion > screenProportion) {
            lp.width = screenWidth
            lp.height = (screenWidth.toFloat() / videoProportion).toInt()
        } else {
            lp.width = (videoProportion * screenHeight.toFloat()).toInt()
            lp.height = screenHeight
        }
        binding.videoLoader.layoutParams = lp
        binding.iconVideoPlay.visibility = VISIBLE
        mDuration = binding.videoLoader.duration
        setSeekBarPosition()
        setTimeFrames()
        setTimeVideo(0)
        letUserProceed = croppedFileSize < maxFileSize
    }

    private fun setSeekBarPosition() {
        binding.timeLineBar.setThumbValue(0, 0f)
        binding.timeLineBar.setThumbValue(1, (mEndPosition * 100).toFloat() / mDuration)
        setProgressBarPosition(mStartPosition)
        binding.videoLoader.seekTo(mStartPosition)
        mTimeVideo = mDuration
        binding.timeLineBar.initMaxWidth()
    }

    private fun startTrimVideo(
        file: File,
        dst: String,
        startVideo: Int,
        endVideo: Int,
        callback: OnTrimVideoListener
    ) {
        execute(object : BackgroundExecutor.Task("", 0L, "") {
            override fun execute() {
                try {
                    startTrim(file, dst, startVideo.toLong(), endVideo.toLong(), callback)
                } catch (e: Throwable) {
                    Thread.getDefaultUncaughtExceptionHandler()
                        ?.uncaughtException(Thread.currentThread(), e)
                }
            }
        })
    }

    private fun setTimeFrames() {
        binding.textTimeSelection.text =
            String.format("%s - %s", stringForTime(mStartPosition), stringForTime(mEndPosition))
    }

    private fun setTimeVideo(position: Int) {
        val seconds = context.getString(R.string.short_seconds)
        binding.textTime.text = String.format("%s %s", stringForTime(position), seconds)
    }

    override fun onCreate(rangeSeekBarView: RangeSeekBarView?, index: Int, value: Float) {}
    override fun onSeek(rangeSeekBarView: RangeSeekBarView?, index: Int, value: Float) {
        /*        0 is Left selector
         1 is right selector*/
        when (index) {
            0 -> {
                mStartPosition = (mDuration * value / 100L).toInt()
                binding.videoLoader.seekTo(mStartPosition)
            }
            1 -> {
                mEndPosition = (mDuration * value / 100L).toInt()
            }
        }
        setProgressBarPosition(mStartPosition)
        setTimeFrames()
        getSizeFile(true)
        mTimeVideo = mEndPosition - mStartPosition
        letUserProceed = croppedFileSize < maxFileSize
    }

    override fun onSeekStart(rangeSeekBarView: RangeSeekBarView?, index: Int, value: Float) {}
    override fun onSeekStop(rangeSeekBarView: RangeSeekBarView?, index: Int, value: Float) {
        mMessageHandler.removeMessages(SHOW_PROGRESS)
        binding.videoLoader.pause()
        binding.iconVideoPlay.visibility = VISIBLE
    }

    private fun stringForTime(timeMs: Int): String {
        val totalSeconds = timeMs / 1000
        val seconds = totalSeconds % 60
        val minutes = totalSeconds / 60 % 60
        val hours = totalSeconds / 3600
        val mFormatter = Formatter()
        return if (hours > 0) {
            mFormatter.format("%d:%02d:%02d", hours, minutes, seconds).toString()
        } else {
            mFormatter.format("%02d:%02d", minutes, seconds).toString()
        }
    }

    private fun getSizeFile(isChanged: Boolean) {
        if (isChanged) {
            val initSize = fileSize
            val newSize: Long = initSize / initialLength * (mEndPosition - mStartPosition)
            setVideoSize(newSize / 1024)
        } else {
            if (mOriginSizeFile == 0L && mSrc!!.path != null) {
                val file = mSrc!!.path?.let { File(it) }
                mOriginSizeFile = file?.length() ?: 0
                val fileSizeInKB = mOriginSizeFile / 1024
                setVideoSize(fileSizeInKB)
            }
        }
    }

    private fun setVideoSize(fileSizeInKB: Long) {
        val compressedSize = getCompressedSize(fileSizeInKB)
        if (compressedSize > 1000) {
            val fileSizeInMB = compressedSize / 1024f
            val df = DecimalFormat("###.#")
            binding.textSize.text = String.format(
                "%s %s",
                df.format(fileSizeInMB.toDouble()),
                context.getString(R.string.megabyte)
            )
        } else {
            binding.textSize.text =
                String.format("%s %s", compressedSize.toInt(), context.getString(R.string.kilobyte))
        }
    }

    private fun getCompressedSize(fileSizeInKB: Long): Float {
        if (compressionRatio.toDouble() == 1024.0) {
            return fileSizeInKB.toFloat()
        }
        val estimatedCompressedFileSize: Float = if (fileSizeInKB > compressionRatio) {
            fileSizeInKB / compressionRatio * 1024
        } else if (fileSizeInKB > 30) {
            fileSizeInKB / 40f
        } else {
            1f
        }
        return estimatedCompressedFileSize // in KB
    }

    private val fileSize: Long
        get() {
            if (mSrc!!.path == null) {
                return 0
            }
            val file = mSrc!!.path?.let { File(it) }
            mOriginSizeFile = file?.length() ?: 0
            return mOriginSizeFile / 1024
        }
    private val croppedFileSize: Long
        get() {
            val initSize = fileSize
            val newSize: Long = initSize / initialLength * (mEndPosition - mStartPosition)
            val compressedSize = getCompressedSize(newSize).toLong()
            return compressedSize / 1024
        }

    fun setOnTrimVideoListener(onTrimVideoListener: OnTrimVideoListener?) {
        mOnTrimVideoListener = onTrimVideoListener
    }

    override fun onCompletion(mediaPlayer: MediaPlayer) {
        binding.videoLoader.seekTo(0)
    }

    override fun onError(mediaPlayer: MediaPlayer, i: Int, i1: Int): Boolean {
        return false
    }

    private class MessageHandler(view: DeepVideoTrimmer) : Handler() {
        private val mView: WeakReference<DeepVideoTrimmer>
        override fun handleMessage(msg: Message) {
            val view = mView.get()
            if (view?.binding?.videoLoader == null) {
                return
            }
            view.updateProgress(true)
            if (view.binding.videoLoader.isPlaying) {
                sendEmptyMessageDelayed(0, 10)
            }
        }

        init {
            mView = WeakReference(view)
        }
    }

    private fun updateProgress(all: Boolean) {
        if (mDuration == 0) return
        val position = binding.videoLoader.currentPosition
        if (all) {
            for (item in mListeners!!) {
                item.updateProgress(position, mDuration, (position * 100 / mDuration).toFloat())
            }
        } else {
            mListeners!![1].updateProgress(
                position,
                mDuration,
                (position * 100 / mDuration).toFloat()
            )
        }
    }

    override fun updateProgress(time: Int, max: Int, scale: Float) {

        if (time >= mEndPosition) {
            mMessageHandler.removeMessages(SHOW_PROGRESS)
            binding.videoLoader.pause()
            binding.iconVideoPlay.visibility = VISIBLE
            mResetSeekBar = true
            return
        }
        setProgressBarPosition(time)
        setTimeVideo(time)
    }

    private fun setProgressBarPosition(position: Int) {
        if (mDuration > 0) {
            val pos = 1000L * position / mDuration
            binding.handlerTop.progress = pos.toInt()
        }
    }

    fun destroy() {
        cancelAll("", true)
        cancelAll("")
    }

    companion object {
        private val TAG = DeepVideoTrimmer::class.java.simpleName
        private const val MIN_TIME_FRAME = 1000
        private const val DEFAULT_VIDEO_WIDTH = 640
        private const val DEFAULT_VIDEO_HEIGHT = 360
        private const val SHOW_PROGRESS = 2
    }

    init {
        init(context)
    }
}