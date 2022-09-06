package com.quanticheart.core.components.videotrimmer.view

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.quanticheart.core.R
import java.util.*

class Thumb private constructor() {
    var index = 0
        private set
    var `val` = 0f
    var pos = 0f
    var bitmap: Bitmap? = null
        private set
    var widthBitmap = 0
        private set
    private var heightBitmap = 0
    var lastTouchX = 0f
    private fun setBitmap(bitmap: Bitmap) {
        this.bitmap = bitmap
        widthBitmap = bitmap.width
        heightBitmap = bitmap.height
    }

    companion object {
        fun initThumbs(resources: Resources?): List<Thumb> {
            val thumbs: MutableList<Thumb> = Vector()
            for (i in 0..1) {
                val th = Thumb()
                th.index = i
                if (i == 0) {
                    val resImageLeft = R.drawable.text_select_handle_left
                    th.setBitmap(BitmapFactory.decodeResource(resources, resImageLeft))
                } else {
                    val resImageRight = R.drawable.select_handle_right
                    th.setBitmap(BitmapFactory.decodeResource(resources, resImageRight))
                }
                thumbs.add(th)
            }
            return thumbs
        }

        fun getWidthBitmap(thumbs: List<Thumb>): Int {
            return thumbs[0].widthBitmap
        }

        fun getHeightBitmap(thumbs: List<Thumb>): Int {
            return thumbs[0].heightBitmap
        }
    }
}