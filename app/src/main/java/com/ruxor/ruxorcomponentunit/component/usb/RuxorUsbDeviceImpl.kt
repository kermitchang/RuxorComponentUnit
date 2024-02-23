package com.ruxor.ruxorcomponentunit.component.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log

class RuxorUsbDeviceImpl(private val context: Context) {

    private val TAG:String = this.javaClass.name

    private val ACTION_USB_PERMISSION = "ACTION.USB_PERMISSION"

    private var connectBaudRate:Int ?= null
    private var ruxorUsbDeviceHashMap : HashMap<String, RuxorUsbDevice> = HashMap<String, RuxorUsbDevice> ()
    private val ruxorUsbDeviceListener = object : RuxorUsbDeviceListener {
        override fun receive(usbDevice:UsbDevice, byteArray:ByteArray) {
            this@RuxorUsbDeviceImpl.ruxorUsbDeviceImplListener?.receive(usbDevice, byteArray)
        }
    }
    private var ruxorUsbDeviceImplListener: RuxorUsbDeviceListener?= null
    private var connectUsbDevice: UsbDevice ?= null
    private val usbManager: UsbManager
    private val usbPermissionBroadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                Log.i(TAG,"Kermit usbPermissionBroadReceiver onReceive")
                if (this@RuxorUsbDeviceImpl.ACTION_USB_PERMISSION == intent.action) {
                    synchronized(this) {
                        val selection = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                        Log.d(TAG,"Kermit selection -> $selection")
                        if (selection) {
                            this@RuxorUsbDeviceImpl.connectUsbDevice?.let { device ->
                                this@RuxorUsbDeviceImpl.connectBaudRate?.let { baudRate ->
                                    this@RuxorUsbDeviceImpl.connectDevice(device, baudRate)
                                }
                            }
                        } else {
                            Log.e(TAG,"Kermit -> Don't have the permission for the device")
                        }
                    }
                }
            }
        }
    }

    fun connectDevice(usbDevice: UsbDevice, baudRate:Int = 9600) {
        if (this.usbManager.hasPermission(usbDevice)) {
            Log.i(TAG,"Kermit device has permission")
            this.startConnect(usbDevice, baudRate)
        } else {
            Log.i(TAG,"Kermit device don't has permission")
            this.connectUsbDevice = usbDevice
            this.connectBaudRate = baudRate
            this.context.registerReceiver(this.usbPermissionBroadReceiver, IntentFilter(this.ACTION_USB_PERMISSION))
            val pendingIntent = PendingIntent.getBroadcast(this.context, 0, Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_MUTABLE)
            this.usbManager.requestPermission(usbDevice, pendingIntent)
        }
    }

    fun disconnectDevice(usbDevice: UsbDevice) {
        this.ruxorUsbDeviceHashMap[usbDevice.deviceName]?.closeDevice()
    }

    fun getAllUsbDevice(): HashMap<String, UsbDevice>? {
        return this.usbManager.deviceList
    }

    fun sendMessage(usbDevice: UsbDevice, byteArray: ByteArray) {
        if (this.ruxorUsbDeviceHashMap[usbDevice.deviceName] == null) {
            Log.e(TAG,"Not connected the device -> ${usbDevice.deviceName}")
            return
        }

        this.ruxorUsbDeviceHashMap.get(usbDevice.deviceName)?.sendData(byteArray)
    }

    fun sendMessage(usbDevice: UsbDevice, message: String) {
        this.sendMessage(usbDevice, message.toByteArray())
    }

    fun setReceiveDataListener(ruxorUsbDeviceImplListener: RuxorUsbDeviceListener) {
        this.ruxorUsbDeviceImplListener = ruxorUsbDeviceImplListener
    }

    private fun startConnect(usbDevice: UsbDevice, baudRate:Int) {
        var ruxorUsbDevice = RuxorUsbDevice(this.usbManager, usbDevice)
        ruxorUsbDevice.setReceiveListener(this.ruxorUsbDeviceListener)
        ruxorUsbDevice.openDevice(baudRate)
        this.ruxorUsbDeviceHashMap[usbDevice.deviceName] = ruxorUsbDevice
    }

    init {
        this.usbManager = this.context.getSystemService(Context.USB_SERVICE) as UsbManager
    }

}