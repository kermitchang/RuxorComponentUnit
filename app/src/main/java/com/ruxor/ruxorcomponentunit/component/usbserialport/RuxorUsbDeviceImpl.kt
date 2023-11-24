package com.ruxor.ruxorcomponentunit.component.usbserialport

import android.app.PendingIntent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log

class RuxorUsbDeviceImpl(context: Context) {

    private val TAG:String = this.javaClass.name

    private val ACTION_USB_PERMISSION = "ACTION.USB_PERMISSION"

    private val context: Context
    private var ruxorUsbDeviceHashMap : HashMap<String, RuxorUsbDevice> = HashMap<String, RuxorUsbDevice> ()
    private val ruxorUsbDeviceListener = object : RuxorUsbDeviceListener {
        override fun receive(usbDevice:UsbDevice, byteArray:ByteArray) {
            //Log.i(TAG,"Kermit receives -> byteArray:${byteArray[0]} size:${byteArray.size}")
            this@RuxorUsbDeviceImpl.ruxorUsbDeviceImplListener?.receive(usbDevice, byteArray)
        }
    }
    private var ruxorUsbDeviceImplListener: RuxorUsbDeviceListener?= null
    private val usbManager: UsbManager
    private val usbPermissionBroadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                if (this@RuxorUsbDeviceImpl.ACTION_USB_PERMISSION == intent.action) {
                    synchronized(this) {
                        val selection = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                        val usbDevice = intent.getParcelableArrayExtra(UsbManager.EXTRA_DEVICE) as UsbDevice
                        Log.d(TAG,"Kermit selection -> ${selection}, usbDevice Name:${usbDevice.manufacturerName}")
                        if (selection) {
                            this@RuxorUsbDeviceImpl.startConnect(usbDevice)
                        } else {
                            Log.e(TAG,"Kermit -> Don't have the permission for the device")
                        }
                    }
                }
            }
        }
    }

    fun connectDevice(usbDevice: UsbDevice) {
        if (this.usbManager.hasPermission(usbDevice)) {
            this.startConnect(usbDevice)
        } else {
            this.context.registerReceiver(this.usbPermissionBroadReceiver, IntentFilter(this.ACTION_USB_PERMISSION))
            val pendingIntent = PendingIntent.getBroadcast(this.context, 0, Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE)
            this.usbManager.requestPermission(usbDevice, pendingIntent)
        }
    }

    fun disconnectDevice(usbDevice: UsbDevice) {
        this.ruxorUsbDeviceHashMap.get(usbDevice.deviceName)?.closeDevice()
    }

    fun getAllUsbDevice(): HashMap<String, UsbDevice>? {
        return this.usbManager.deviceList
    }

    fun sendMessage(usbDevice: UsbDevice, byteArray: ByteArray) {
        if (this.ruxorUsbDeviceHashMap.get(usbDevice.deviceName) == null) {
            Log.e(TAG,"Not connected the device -> ${usbDevice.deviceName}")
            return
        }

        byteArray.forEachIndexed { index, byte ->
            Log.w(TAG,"Kermit ($index) -> ${byte}")
        }

        this.ruxorUsbDeviceHashMap.get(usbDevice.deviceName)?.sendData(byteArray)
    }

    fun sendMessage(usbDevice: UsbDevice, message: String) {
        this.sendMessage(usbDevice, message.toByteArray())
    }

    fun setReceiveDataListener(ruxorUsbDeviceImplListener: RuxorUsbDeviceListener) {
        this.ruxorUsbDeviceImplListener = ruxorUsbDeviceImplListener
    }

    private fun startConnect(usbDevice: UsbDevice) {
        var ruxorUsbDevice = RuxorUsbDevice(this.usbManager, usbDevice)
        ruxorUsbDevice.setReceiveListener(this.ruxorUsbDeviceListener)
        ruxorUsbDevice.openDevice()
        this.ruxorUsbDeviceHashMap.set(usbDevice.deviceName, ruxorUsbDevice)
    }

    init {
        this.context = context
        this.usbManager = this.context.getSystemService(Context.USB_SERVICE) as UsbManager
    }

}