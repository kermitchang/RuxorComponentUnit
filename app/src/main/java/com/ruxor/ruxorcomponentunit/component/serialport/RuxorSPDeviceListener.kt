package com.ruxor.ruxorcomponentunit.component.serialport

interface RuxorSPDeviceListener {
    fun receive(ruxorSPDeviceName: RuxorSPDeviceName, message: String?)
}