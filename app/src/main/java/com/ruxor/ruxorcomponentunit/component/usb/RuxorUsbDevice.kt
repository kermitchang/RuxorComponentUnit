package com.ruxor.ruxorcomponentunit.component.usb

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber

class RuxorUsbDevice(usbManager: UsbManager, usbDevice: UsbDevice) {

    private val TAG: String = this.javaClass.name
    private val READ_WAIT_MILLIS = 50
    private val RECEIVE_SIZE = 1024
    private val RECEIVE_TIME: Long = 50
    private val WRITE_WAIT_MILLIS = 2000

    private var isProcessing = true
    private var receiverDataThread:Thread ?= null
    private var ruxorUsbDeviceListener: RuxorUsbDeviceListener?= null
    private val usbDevice: UsbDevice
    private var usbDeviceConnection: UsbDeviceConnection ?= null
    private val usbManager: UsbManager
    private var usbSerialDriver: UsbSerialDriver ?= null
    private var usbSerialPort: UsbSerialPort ?= null

    private val receiveRunnableTask = Runnable {
        while (this.isProcessing) {
            Thread.sleep(this.RECEIVE_TIME)
            this.receiveData()
        }
    }

    fun closeDevice() {
        this.isProcessing = false
        this.receiverDataThread?.join()
        this.receiverDataThread = null
        this.usbSerialPort?.close()
        this.usbDeviceConnection?.close()
    }

    fun openDevice(baudRate:Int=9600, dataBits:Int=8) {
        val availableUsbSerialDriver = UsbSerialProber.getDefaultProber().findAllDrivers(this.usbManager)
        availableUsbSerialDriver.forEach() { usbSerialDriver ->
            if (usbSerialDriver.device.vendorId == this.usbDevice.vendorId && usbSerialDriver.device.productId == this.usbDevice.productId) {
                this.usbSerialDriver = usbSerialDriver
                this.usbDeviceConnection = this.usbManager.openDevice(this.usbDevice)
                this.usbSerialPort = this.usbSerialDriver?.ports?.get(0)
                this.usbSerialPort?.open(this.usbDeviceConnection)
                this.usbSerialPort?.setParameters(baudRate, dataBits, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
                this.isProcessing = true
                this.receiverDataThread = Thread(this.receiveRunnableTask).also { thread ->
                    thread.start()
                }
            }
        }
    }

    private fun receiveData() {
        var recvByteArray = ByteArray(this.RECEIVE_SIZE)
        val recvLen = this.usbSerialPort?.read(recvByteArray, this.READ_WAIT_MILLIS)
        if (recvLen != null && recvLen > 0) {
            var byteArray = recvByteArray.copyOf(recvLen)
            this.ruxorUsbDeviceListener?.receive(this.usbDevice, byteArray)
        }
    }

    fun sendData(byteArray: ByteArray) {
        this.usbSerialPort?.write(byteArray, this.WRITE_WAIT_MILLIS)
    }

    fun setReceiveListener(ruxorUsbDeviceListener: RuxorUsbDeviceListener) {
        this.ruxorUsbDeviceListener = ruxorUsbDeviceListener
    }

    init {
        this.usbManager = usbManager
        this.usbDevice = usbDevice
    }
}