package com.ruxor.ruxorcomponentunit.component.sensor

interface RuxorSensorDeviceListener {
    fun receive(sensorDeviceName: RuxorSensorDeviceName, message: String?)
}