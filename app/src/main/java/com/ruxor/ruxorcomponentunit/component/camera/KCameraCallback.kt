package com.ruxor.ruxorcomponentunit.component.camera

import androidx.annotation.Nullable

interface KCameraCallback {
    fun getFrame(frame: ByteArray?, width:Int, height:Int)
}