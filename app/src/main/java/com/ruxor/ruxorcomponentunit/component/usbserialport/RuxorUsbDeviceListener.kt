package com.ruxor.ruxorcomponentunit.component.usbserialport

import android.hardware.usb.UsbDevice

interface RuxorUsbDeviceListener {
    fun receive(usbDevice: UsbDevice, byteArray: ByteArray)
}