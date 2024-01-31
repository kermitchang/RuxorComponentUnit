package com.ruxor.ruxorcomponentunit.component.camera

interface KCameraCallback {
    fun getFrame(frame: ByteArray?, width:Int, height:Int)
}