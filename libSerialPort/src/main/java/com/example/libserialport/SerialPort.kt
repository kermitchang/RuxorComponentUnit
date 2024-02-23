package com.example.libserialport

import java.io.FileDescriptor

class SerialPort {

    /**
     * A native method that is implemented by the 'libserialport' native library,
     * which is packaged with this application.
     */

    external fun open(path:String, baudrate:Int, flags:Int): FileDescriptor
    external fun close()

    companion object {
        // Used to load the 'libserialport' library on application startup.
        init {
            System.loadLibrary("libserialport")
        }
    }
}