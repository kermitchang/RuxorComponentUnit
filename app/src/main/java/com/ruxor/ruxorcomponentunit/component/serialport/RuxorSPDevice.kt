package com.ruxor.ruxorcomponentunit.component.serialport

import android.util.Log
import com.example.libserialport.SerialPort
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream

class RuxorSPDevice(private val nodePath:String,
                    private val ruxorSPDeviceName: RuxorSPDeviceName
) {

    private val TAG = this.javaClass.name
    private val RECEIVE_TIME: Long = 50

    private var fileDescriptor: FileDescriptor? = null
    private var fileInputStream: FileInputStream? = null
    private var fileOutputStream: FileOutputStream? = null
    private var isProcessing = true
    private var receiverDataThread:Thread ?= null
    private val receiveRunnableTask = Runnable {
        while (this.isProcessing) {
            Thread.sleep(this.RECEIVE_TIME)
            this.receiveData()
        }
    }
    private var ruxorSPDeviceListener: RuxorSPDeviceListener?= null
    private val serialPort by lazy {
        SerialPort()
    }

    private fun receiveData() {
        val buffer = ByteArray(1024)
        if (this.fileInputStream == null) return
        this.fileInputStream?.read(buffer)?.also { size ->
            if (size > 0) {
                val message = String(buffer)
                this.ruxorSPDeviceListener?.receive(this.ruxorSPDeviceName, message)
            }
        }
    }

    fun openDevice(baudRate: Int=9600, flags: Int = 0) {
        this.fileDescriptor = this.serialPort.open(this.nodePath, baudRate, 0)
        if ( null == this.fileDescriptor ) {
            Log.e(TAG,"Kermit ${this.nodePath} is return null")
        } else {
            Log.d(TAG,"Kermit ${this.nodePath} is exist")
            this.fileInputStream = FileInputStream(this.fileDescriptor)
            this.fileOutputStream = FileOutputStream(this.fileDescriptor)
            this.isProcessing = true
            this.receiverDataThread = Thread(this.receiveRunnableTask).also { thread ->
                thread.start()
            }
        }
    }

    fun closeDevice() {
        if ( null != this.fileDescriptor ) {
            this.isProcessing = false
            this.receiverDataThread?.join()
            this.receiverDataThread = null
            this.serialPort.close()
        }
    }

    fun setReceiveListener(ruxorSPDeviceListener: RuxorSPDeviceListener) {
        this.ruxorSPDeviceListener = ruxorSPDeviceListener
    }

    fun write(message:String) {
        this.fileOutputStream?.write(message.toByteArray())
    }

}