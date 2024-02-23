package com.ruxor.ruxorcomponentunit.component.serialport

class RuxorSPDeviceImpl {

    private val TAG = this.javaClass.name

    private val ruxorSPDeviceListener = object : RuxorSPDeviceListener {
        override fun receive(ruxorSPDeviceName: RuxorSPDeviceName, message: String?) {
            this@RuxorSPDeviceImpl.ruxorSPDeviceImplListener?.receive(ruxorSPDeviceName, message)
        }
    }
    private var ruxorSPDeviceImplListener: RuxorSPDeviceListener?= null
    private var ruxorSPDeviceHashMap : HashMap<RuxorSPDeviceName, RuxorSPDevice> = HashMap<RuxorSPDeviceName, RuxorSPDevice> ()

    public fun connect(nodePath: String, ruxorSPDeviceName: RuxorSPDeviceName) {
        var ruxorSPDevice = RuxorSPDevice(nodePath, ruxorSPDeviceName)
        ruxorSPDevice.setReceiveListener(this.ruxorSPDeviceListener)
        ruxorSPDevice.openDevice()
        this.ruxorSPDeviceHashMap[ruxorSPDeviceName] = ruxorSPDevice
    }

    public fun clear() {
        this.disconnect()
        this.ruxorSPDeviceHashMap.clear()
    }

    private fun disconnect() {
        this.ruxorSPDeviceHashMap.values.forEach {
            it.closeDevice()
        }
    }

    public fun setReceiveDataListener(ruxorSPDeviceListener: RuxorSPDeviceListener) {
        this.ruxorSPDeviceImplListener = ruxorSPDeviceListener
    }

    public fun write(ruxorSPDeviceName: RuxorSPDeviceName, message:String) {
        this.ruxorSPDeviceHashMap[ruxorSPDeviceName]?.write(message)
    }

}