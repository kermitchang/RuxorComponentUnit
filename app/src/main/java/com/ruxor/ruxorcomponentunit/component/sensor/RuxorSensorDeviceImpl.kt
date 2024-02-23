package com.ruxor.ruxorcomponentunit.component.sensor

import android.content.Context
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class RuxorSensorDeviceImpl(private val context: Context) {

    private val TAG:String = this.javaClass.name
    private val DEAFAULT_MILLISECOND_DURATION = 1000

    private var captureValueMillDuration = this.DEAFAULT_MILLISECOND_DURATION
    private var ruxorSensorDeviceArrayList : ArrayList<RuxorSensorDevice> =  ArrayList<RuxorSensorDevice>()
    private var ruxorSensorDeviceImplListener: RuxorSensorDeviceListener?= null
    private var sensorDeviceValue = 0
    private var nowCaptureSensorNumber = 0

    private var updateDataExecutorService : ScheduledExecutorService? = null
    private val updateValue = Runnable {
        val name = this.ruxorSensorDeviceArrayList[this.nowCaptureSensorNumber].getName()
        val value = this.ruxorSensorDeviceArrayList[this.nowCaptureSensorNumber].getValue()
        this.ruxorSensorDeviceImplListener?.receive(name, value)
        this.nowCaptureSensorNumber++
        if (this.nowCaptureSensorNumber == sensorDeviceValue)
            this.nowCaptureSensorNumber = 0
    }

    public fun clear() {
        this.disconnect()
        this.updateDataExecutorService?.shutdown()
        this.updateDataExecutorService = null
    }

    public fun connect(nodePath: String, sensorDeviceName: RuxorSensorDeviceName) {
        var ruxorSensorDevice = RuxorSensorDevice(this.context, nodePath, sensorDeviceName)
        this.ruxorSensorDeviceArrayList.add(ruxorSensorDevice)
        this.sensorDeviceValue = this.ruxorSensorDeviceArrayList.size

        if (this.updateDataExecutorService == null) {
            this.updateDataExecutorService = Executors.newScheduledThreadPool(1)
            this.updateDataExecutorService?.scheduleWithFixedDelay(this.updateValue, 1000, this.captureValueMillDuration.toLong(), TimeUnit.MILLISECONDS)
        }
    }

    private fun disconnect() {
        this.ruxorSensorDeviceArrayList.clear()
    }

    public fun setReceiveDataListener(ruxorSensorDeviceListener: RuxorSensorDeviceListener) {
        this.ruxorSensorDeviceImplListener = ruxorSensorDeviceListener
    }

}