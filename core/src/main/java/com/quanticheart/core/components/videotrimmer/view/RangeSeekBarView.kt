@file:Suppress("unused")

package com.quanticheart.core.components.videotrimmer.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.annotation.NonNull
import androidx.core.content.ContextCompat
import com.quanticheart.core.R
import com.quanticheart.core.components.videotrimmer.interfaces.OnRangeSeekBarListener

class RangeSeekBarView @JvmOverloads constructor(
    @NonNull context: Context?,
    attrs: AttributeSet?,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private var mHeightTimeLine = 0
    var thumbs: List<Thumb>? = null
        private set
    private var mListeners: MutableList<OnRangeSeekBarListener>? = null
    private var mMaxWidth = 0f
    private var mThumbWidth = 0f
    private var mThumbHeight = 0f
    private var mViewWidth = 0
    private var mPixelRangeMin = 0f
    private var mPixelRangeMax = 0f
    private var mScaleRangeMax = 0f
    private var mFirstRun = false
    private val mShadow = Paint()
    private val mLine = Paint()
    private fun init() {
        thumbs = Thumb.initThumbs(resources)
        mThumbWidth = Thumb.getWidthBitmap(thumbs!!).toFloat()
        mThumbHeight = Thumb.getHeightBitmap(thumbs!!).toFloat()
        mScaleRangeMax = 100f
        mHeightTimeLine = context.resources.getDimensionPixelOffset(R.dimen.frames_video_height)
        isFocusable = true
        isFocusableInTouchMode = true
        mFirstRun = true
        val shadowColor: Int = ContextCompat.getColor(context, R.color.shadow_color)
        mShadow.isAntiAlias = true
        mShadow.color = shadowColor
        mShadow.alpha = 177
        val lineColor: Int = ContextCompat.getColor(context, R.color.line_color)
        mLine.isAntiAlias = true
        mLine.color = lineColor
        mLine.alpha = 200
    }

    fun initMaxWidth() {
        mMaxWidth = thumbs!![1].pos - thumbs!![0].pos
        onSeekStop(this, 0, thumbs!![0].`val`)
        onSeekStop(this, 1, thumbs!![1].`val`)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val minW = paddingLeft + paddingRight + suggestedMinimumWidth
        mViewWidth = resolveSizeAndState(minW, widthMeasureSpec, 1)
        val minH = paddingBottom + paddingTop + mThumbHeight.toInt() + mHeightTimeLine
        val viewHeight = resolveSizeAndState(minH, heightMeasureSpec, 1)
        setMeasuredDimension(mViewWidth, viewHeight)
        mPixelRangeMin = 0f
        mPixelRangeMax = mViewWidth - mThumbWidth
        if (mFirstRun) {
            for (i in thumbs!!.indices) {
                val th = thumbs!![i]
                th.`val` = mScaleRangeMax * i
                th.pos = mPixelRangeMax * i
            }
            onCreate(this, currentThumb, getThumbValue(currentThumb))
            mFirstRun = false
        }
    }

    override fun onDraw(@NonNull canvas: Canvas) {
        super.onDraw(canvas)
        drawShadow(canvas)
        drawThumbs(canvas)
    }

    private var currentThumb = 0

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(@NonNull ev: MotionEvent): Boolean {
        val mThumb: Thumb
        val mThumb2: Thumb
        val coordinate: Float = ev.x
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {

                /*Remember where we started*/currentThumb = getClosestThumb(coordinate)
                if (currentThumb == -1) {
                    return false
                }
                mThumb = thumbs!![currentThumb]
                mThumb.lastTouchX = coordinate
                onSeekStart(this, currentThumb, mThumb.`val`)
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (currentThumb == -1) {
                    return false
                }
                mThumb = thumbs!![currentThumb]
                onSeekStop(this, currentThumb, mThumb.`val`)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                mThumb = thumbs!![currentThumb]
                mThumb2 = thumbs!![if (currentThumb == 0) 1 else 0]
                /* Calculate the distance moved*/
                val dx = coordinate - mThumb.lastTouchX
                val newX = mThumb.pos + dx
                if (currentThumb == 0) {
                    if (newX + mThumb.widthBitmap >= mThumb2.pos) {
                        mThumb.pos = mThumb2.pos - mThumb.widthBitmap
                    } else if (newX <= mPixelRangeMin) {
                        mThumb.pos = mPixelRangeMin
                    } else {
                        /*Check if thumb is not out of max width*/
                        checkPositionThumb(mThumb, mThumb2, dx, true)
                        /* Move the object*/mThumb.pos = mThumb.pos + dx

                        /* Remember this touch position for the next move event*/mThumb.lastTouchX =
                            coordinate
                    }
                } else {
                    if (newX <= mThumb2.pos + mThumb2.widthBitmap) {
                        mThumb.pos = mThumb2.pos + mThumb.widthBitmap
                    } else if (newX >= mPixelRangeMax) {
                        mThumb.pos = mPixelRangeMax
                    } else {
                        /*Check if thumb is not out of max width*/
                        checkPositionThumb(mThumb2, mThumb, dx, false)
                        /* Move the object*/mThumb.pos = mThumb.pos + dx
                        /* Remember this touch position for the next move event*/mThumb.lastTouchX =
                            coordinate
                    }
                }
                setThumbPos(currentThumb, mThumb.pos)
                invalidate()
                return true
            }
        }
        return false
    }

    private fun checkPositionThumb(
        @NonNull mThumbLeft: Thumb,
        @NonNull mThumbRight: Thumb,
        dx: Float,
        isLeftMove: Boolean
    ) {
        if (isLeftMove && dx < 0) {
            if (mThumbRight.pos - (mThumbLeft.pos + dx) > mMaxWidth) {
                mThumbRight.pos = mThumbLeft.pos + dx + mMaxWidth
                setThumbPos(1, mThumbRight.pos)
            }
        } else if (!isLeftMove && dx > 0) {
            if (mThumbRight.pos + dx - mThumbLeft.pos > mMaxWidth) {
                mThumbLeft.pos = mThumbRight.pos + dx - mMaxWidth
                setThumbPos(0, mThumbLeft.pos)
            }
        }
    }

    private fun getUnstuckFrom(index: Int): Int {
        val unstuck = 0
        val lastVal = thumbs!![index].`val`
        for (i in index - 1 downTo 0) {
            val th = thumbs!![i]
            if (th.`val` != lastVal) return i + 1
        }
        return unstuck
    }

    private fun pixelToScale(index: Int, pixelValue: Float): Float {
        val scale = pixelValue * 100 / mPixelRangeMax
        return if (index == 0) {
            val pxThumb = scale * mThumbWidth / 100
            scale + pxThumb * 100 / mPixelRangeMax
        } else {
            val pxThumb = (100 - scale) * mThumbWidth / 100
            scale - pxThumb * 100 / mPixelRangeMax
        }
    }

    private fun scaleToPixel(index: Int, scaleValue: Float): Float {
        val px = scaleValue * mPixelRangeMax / 100
        return if (index == 0) {
            val pxThumb = scaleValue * mThumbWidth / 100
            px - pxThumb
        } else {
            val pxThumb = (100 - scaleValue) * mThumbWidth / 100
            px + pxThumb
        }
    }

    private fun calculateThumbValue(index: Int) {
        if (index < thumbs!!.size && thumbs!!.isNotEmpty()) {
            val th = thumbs!![index]
            th.`val` = pixelToScale(index, th.pos)
            onSeek(this, index, th.`val`)
        }
    }

    private fun calculateThumbPos(index: Int) {
        if (index < thumbs!!.size && thumbs!!.isNotEmpty()) {
            val th = thumbs!![index]
            th.pos = scaleToPixel(index, th.`val`)
        }
    }

    private fun getThumbValue(index: Int): Float {
        return thumbs!![index].`val`
    }

    fun setThumbValue(index: Int, value: Float) {
        thumbs!![index].`val` = value
        calculateThumbPos(index)
        invalidate()
    }

    private fun setThumbPos(index: Int, pos: Float) {
        thumbs!![index].pos = pos
        calculateThumbValue(index)
        invalidate()
    }

    private fun getClosestThumb(coordinate: Float): Int {
        var closest = -1
        if (thumbs!!.isNotEmpty()) {
            for (i in thumbs!!.indices) {
                val tcoordinate = thumbs!![i].pos + mThumbWidth
                if (coordinate >= thumbs!![i].pos && coordinate <= tcoordinate) {
                    closest = thumbs!![i].index
                }
            }
        }
        return closest
    }

    private fun drawShadow(@NonNull canvas: Canvas) {
        if (thumbs!!.isNotEmpty()) {
            for (th in thumbs!!) {
                if (th.index == 0) {
                    val x = th.pos + paddingLeft
                    if (x > mPixelRangeMin) {
                        val mRect =
                            Rect(mThumbWidth.toInt(), 0, (x + mThumbWidth).toInt(), mHeightTimeLine)
                        canvas.drawRect(mRect, mShadow)
                    }
                } else {
                    val x = th.pos - paddingRight
                    if (x < mPixelRangeMax) {
                        val mRect =
                            Rect(x.toInt(), 0, (mViewWidth - mThumbWidth).toInt(), mHeightTimeLine)
                        canvas.drawRect(mRect, mShadow)
                    }
                }
            }
        }
    }

    private fun drawThumbs(@NonNull canvas: Canvas) {
        if (thumbs!!.isNotEmpty()) {
            for (th in thumbs!!) {
                if (th.index == 0) {
                    th.bitmap?.let {
                        canvas.drawBitmap(
                            it,
                            th.pos + paddingLeft,
                            (paddingTop + mHeightTimeLine).toFloat(),
                            null
                        )
                    }
                } else {
                    th.bitmap?.let {
                        canvas.drawBitmap(
                            it,
                            th.pos - paddingRight,
                            (paddingTop + mHeightTimeLine).toFloat(),
                            null
                        )
                    }
                }
            }
        }
    }

    fun addOnRangeSeekBarListener(listener: OnRangeSeekBarListener) {
        if (mListeners == null) {
            mListeners = ArrayList()
        }
        mListeners!!.add(listener)
    }

    private fun onCreate(rangeSeekBarView: RangeSeekBarView, index: Int, value: Float) {
        mListeners?.let {
            for (item in it) {
                item.onCreate(rangeSeekBarView, index, value)
            }
        }
    }

    private fun onSeek(rangeSeekBarView: RangeSeekBarView, index: Int, value: Float) {
        mListeners?.let {
            for (item in it) {
                item.onSeek(rangeSeekBarView, index, value)
            }
        }
    }

    private fun onSeekStart(rangeSeekBarView: RangeSeekBarView, index: Int, value: Float) {
        mListeners?.let {
            for (item in it) {
                item.onSeekStart(rangeSeekBarView, index, value)
            }
        }
    }

    private fun onSeekStop(rangeSeekBarView: RangeSeekBarView, index: Int, value: Float) {
        mListeners?.let {
            for (item in it) {
                item.onSeekStop(rangeSeekBarView, index, value)
            }
        }
    }

    init {
        init()
    }
}