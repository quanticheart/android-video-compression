package com.quanticheart.core.components.videotrimmer.interfaces

import com.quanticheart.core.components.videotrimmer.view.RangeSeekBarView

interface OnRangeSeekBarListener {
    fun onCreate(rangeSeekBarView: RangeSeekBarView?, index: Int, value: Float)
    fun onSeek(rangeSeekBarView: RangeSeekBarView?, index: Int, value: Float)
    fun onSeekStart(rangeSeekBarView: RangeSeekBarView?, index: Int, value: Float)
    fun onSeekStop(rangeSeekBarView: RangeSeekBarView?, index: Int, value: Float)
}