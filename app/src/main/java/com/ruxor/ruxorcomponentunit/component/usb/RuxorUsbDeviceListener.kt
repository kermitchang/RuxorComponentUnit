package com.ruxor.ruxorcomponentunit.component.usb

import android.hardware.usb.UsbDevice

interface RuxorUsbDeviceListener {
    fun receive(usbDevice: UsbDevice, byteArray: ByteArray)
}