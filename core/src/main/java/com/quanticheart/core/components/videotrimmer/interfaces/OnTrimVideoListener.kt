package com.quanticheart.core.components.videotrimmer.interfaces

import android.net.Uri

interface OnTrimVideoListener {
    fun getResult(uri: Uri?)
    fun cancelAction()
    fun trimStarted()
}