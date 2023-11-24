package com.ruxor.ruxorcomponentunit.component.bluetooth

interface RuxorBluetoothListener {
    fun receive(receiveData: ByteArray)
}