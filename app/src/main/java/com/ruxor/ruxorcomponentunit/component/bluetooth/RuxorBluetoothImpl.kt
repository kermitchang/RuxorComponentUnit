package com.ruxor.ruxorcomponentunit.component.bluetooth

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.util.Log
import com.ruxor.ruxorcomponentunit.component.permission.KPermissionImpl
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class RuxorBluetoothImpl(context:Context, activity: Activity) {

    private val TAG = "RuxorBluetoothImpl"
    private val REQUEST_ENABLE_BT = 10000
    private val DEVICE_UUID = "47789beb-fbd0-ae28-3393-ac359af63ee4"
    private val DEVICE_NAME = "AI Camera"
    private val DEVICE_TYPE_SERVER = "bluetooth.device.type.server"
    private val DEVICE_TYPE_CLIENT = "bluetooth.device.type.client"
    private val MESSAGE_READ = 0;
    private val MESSAGE_WRITE = 1;
    private val MESSAGE_TOAST = 2;
    private val MESSAGE_DISCONNECT = 3;

    private val activity: Activity = activity
    private val context: Context = context
    private val bluetoothManager: BluetoothManager = this.context.getSystemService(BluetoothManager::class.java)
    private val bluetoothAdapter: BluetoothAdapter = this.bluetoothManager.adapter
    private val permissionImpl: KPermissionImpl by lazy {
        KPermissionImpl(this.context, this.activity)
    }
    private var ruxorBluetoothListener: RuxorBluetoothListener?= null
    private var targetDeviceName:String? = null
    private var targetDeviceAddress:String? = null
    private var acceptThread: AcceptThread? = null
    private var connectThread: ConnectThread? = null
    private var connectedThread: ConnectedThread? = null
    private var deviceType:String = this.DEVICE_TYPE_CLIENT
    private var isExitProgram = false

    private val bluetoothHandler = object : Handler() {

        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            when(msg.what) {
                this@RuxorBluetoothImpl.MESSAGE_READ -> {
                    val size = msg.arg1
                    var getObjBuffer: ByteArray = msg.obj as ByteArray
                    var getBuffer = getObjBuffer.copyOf(size)
                    this@RuxorBluetoothImpl.ruxorBluetoothListener?.receive(getBuffer)
                }
                this@RuxorBluetoothImpl.MESSAGE_DISCONNECT -> {
                    Log.d(TAG,"Kermit Disconnect isExitProgram:$isExitProgram")
                    if (!this@RuxorBluetoothImpl.isExitProgram) {
                        if (this@RuxorBluetoothImpl.connectedThread != null) {
                            this@RuxorBluetoothImpl.connectedThread?.cancel()
                            this@RuxorBluetoothImpl.connectedThread = null
                        }

                        when(this@RuxorBluetoothImpl.deviceType) {
                            this@RuxorBluetoothImpl.DEVICE_TYPE_CLIENT -> {
                                if (this@RuxorBluetoothImpl.connectThread != null) {
                                    this@RuxorBluetoothImpl.connectThread?.cancel()
                                    this@RuxorBluetoothImpl.connectThread = null
                                }
                                this@RuxorBluetoothImpl.connectDevice(this@RuxorBluetoothImpl.targetDeviceName, this@RuxorBluetoothImpl.targetDeviceAddress)
                            }
                            this@RuxorBluetoothImpl.DEVICE_TYPE_SERVER -> {
                                if (this@RuxorBluetoothImpl.acceptThread != null) {
                                    this@RuxorBluetoothImpl.acceptThread?.cancel()
                                    this@RuxorBluetoothImpl.acceptThread = null
                                }
                                this@RuxorBluetoothImpl.listen()
                            }
                        }
                    }

                }
                else -> Log.d(TAG,"Kermit Other")
            }
        }
    }
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when(intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    //this@RuxorBluetoothImpl.permissionImpl.checkPermission(arrayOf<String>(PermissionImpl.BLUETOOTH_CONNECT))
                    val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    device?.let {
                        Log.d(this@RuxorBluetoothImpl.TAG,"Kermit onReceive -> deviceName:${device.name}, deviceHardwareAddress:${device.address}")
                        if (device.name == this@RuxorBluetoothImpl.targetDeviceName || device.address == this@RuxorBluetoothImpl.targetDeviceAddress) {
                            this@RuxorBluetoothImpl.permissionImpl.checkPermission(arrayOf<String>(
                                KPermissionImpl.BLUETOOTH_CONNECT))
                            this@RuxorBluetoothImpl.connect(device)
                        }
                    }
                }
            }
        }
    }

    private fun connect(device: BluetoothDevice) {
        this.permissionImpl.checkPermission(arrayOf(KPermissionImpl.BLUETOOTH_SCAN))
        if (this.bluetoothAdapter.isDiscovering) {
            this.bluetoothAdapter.cancelDiscovery()
        }
        this.deviceType = this.DEVICE_TYPE_CLIENT
        this.connectThread = ConnectThread(device)
        this.connectThread?.start()
    }

    public fun connectDevice (deviceName: String?, deviceAddress: String?) {
        if (deviceName == null || deviceAddress == null) return

        this.targetDeviceName = deviceName
        this.targetDeviceAddress = deviceAddress

        this.getBondedDevices().forEachIndexed { index, device ->
            Log.d(TAG,"Kermit $index ${device.name} ${device.address}")
            if (device.name == deviceName || device.address == deviceAddress) {
                this.connect(device)
            }
        }

//        this.scanBluetoothDevice()
    }

    private fun getBondedDevices(): Set<BluetoothDevice> {
        Log.d(TAG, "Kermit getBondedDevices")
        this.permissionImpl.checkPermission(arrayOf<String>(KPermissionImpl.BLUETOOTH_SCAN, KPermissionImpl.BLUETOOTH_CONNECT))
        return bluetoothAdapter.bondedDevices
    }

    public fun listen() {
        this.deviceType = this.DEVICE_TYPE_SERVER
        this.acceptThread = AcceptThread()
        this.acceptThread?.start()
    }

    public fun release() {
        this.isExitProgram = true
        this.acceptThread?.cancel()
        this.connectThread?.cancel()
        this.connectedThread?.cancel()
        this.permissionImpl.checkPermission(arrayOf<String>(KPermissionImpl.BLUETOOTH_SCAN))
        if (this.bluetoothAdapter.isDiscovering)
            this.bluetoothAdapter.cancelDiscovery()
        try {
            this.context.unregisterReceiver(this.bluetoothReceiver)
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        }
    }

    private fun scanBluetoothDevice() {

        try {
            val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
            this.context.registerReceiver(this.bluetoothReceiver, filter)
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        }

        this.permissionImpl.checkPermission(arrayOf<String>(KPermissionImpl.ACCESS_FINE_LOCATION, KPermissionImpl.ACCESS_COARSE_LOCATION, KPermissionImpl.BLUETOOTH_SCAN))
        if (this.bluetoothAdapter.isDiscovering)
            this.bluetoothAdapter.cancelDiscovery()
        this.bluetoothAdapter.startDiscovery()
    }

    public fun setReceiveListener(ruxorBluetoothListener: RuxorBluetoothListener) {
        this.ruxorBluetoothListener = ruxorBluetoothListener
    }

    public fun write(message:String) {
        this.connectedThread?.write(message.toByteArray())
    }

    init {
        if (!this.bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            this.permissionImpl.checkPermission(arrayOf<String>(KPermissionImpl.BLUETOOTH_CONNECT))
            this.activity.startActivityForResult(enableBtIntent, this.REQUEST_ENABLE_BT)
        }
    }

    private inner class AcceptThread : Thread() {

        private var serverSocket: BluetoothServerSocket? = null

        override fun run() {
            this@RuxorBluetoothImpl.permissionImpl.checkPermission(arrayOf(KPermissionImpl.BLUETOOTH_CONNECT))
            this.serverSocket = this@RuxorBluetoothImpl.bluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(this@RuxorBluetoothImpl.DEVICE_NAME, UUID.fromString(this@RuxorBluetoothImpl.DEVICE_UUID))

            var shouldLoop = true
            while (shouldLoop) {
                val socket: BluetoothSocket? = try {
                    this.serverSocket?.accept()
                } catch (e: IOException) {
                    Log.e(TAG, "Socket's accept() method failed", e)
                    shouldLoop = false
                    null
                }
                socket?.also {
                    if (this@RuxorBluetoothImpl.connectedThread != null) {
                        this@RuxorBluetoothImpl.connectedThread?.cancel()
                        this@RuxorBluetoothImpl.connectedThread = null
                    }
                    this@RuxorBluetoothImpl.connectedThread = ConnectedThread(socket, this@RuxorBluetoothImpl.bluetoothHandler)
                    this@RuxorBluetoothImpl.connectedThread?.start()
                    this.serverSocket?.close()
                    shouldLoop = false
                }
            }
        }

        // Closes the connect socket and causes the thread to finish.
        fun cancel() {
            try {
                this.serverSocket?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Could not close the connect socket", e)
            }
        }
    }

    private inner class ConnectThread(device: BluetoothDevice) : Thread() {

        private var connectSocket: BluetoothSocket? = null
        private val device: BluetoothDevice = device
        private var isLoop = true

        public override fun run() {
            this@RuxorBluetoothImpl.permissionImpl.checkPermission(arrayOf<String>(KPermissionImpl.BLUETOOTH_SCAN))
            if (this@RuxorBluetoothImpl.bluetoothAdapter.isDiscovering) {
                this@RuxorBluetoothImpl.bluetoothAdapter.cancelDiscovery()
            }

            this.connectSocket = this.device.createRfcommSocketToServiceRecord(UUID.fromString(this@RuxorBluetoothImpl.DEVICE_UUID))
            this.isLoop = true
            while (this.isLoop) {
                this.connectSocket?.let { socket ->
                    try {
                        Log.d(TAG,"Kermit connect to device: ${device.name}")
                        socket.connect()
                        if (this@RuxorBluetoothImpl.connectedThread != null) {
                            this@RuxorBluetoothImpl.connectedThread?.cancel()
                            this@RuxorBluetoothImpl.connectedThread = null
                        }
                        this@RuxorBluetoothImpl.connectedThread = ConnectedThread(socket, this@RuxorBluetoothImpl.bluetoothHandler)
                        this@RuxorBluetoothImpl.connectedThread?.start()
                    } catch (e:IOException) {
                        Log.e(TAG,"Kermit bluetooth socket -> $e")
                        e.printStackTrace()
                    }
                }
                if (this@RuxorBluetoothImpl.connectedThread != null)
                    break
                Thread.sleep(2000)
            }

        }

        // Closes the client socket and causes the thread to finish.
        fun cancel() {
            this.isLoop = false
            try {
                this.connectSocket?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Could not close the client socket", e)
            }
        }
    }

    private inner class ConnectedThread(private val socket: BluetoothSocket, handler:Handler) : Thread() {

        private val mmInStream: InputStream = socket.inputStream
        private val mmOutStream: OutputStream = socket.outputStream
        private val mmBuffer: ByteArray = ByteArray(1024) // mmBuffer store for the stream
        private val handler = handler
        private var isLoop = true

        override fun run() {
            Log.d(TAG,"Kermit ConnectedThread start")
            var numBytes: Int // bytes returned from read()
            this.isLoop = true
            // Keep listening to the InputStream until an exception occurs.
            while (this.isLoop) {
                // Read from the InputStream.
                numBytes = try {
                    this.mmInStream.read(this.mmBuffer)
                } catch (e: IOException) {
                    Log.d(this@RuxorBluetoothImpl.TAG, "Kermit Input stream was disconnected", e)
                    val readMsg = this.handler.obtainMessage(this@RuxorBluetoothImpl.MESSAGE_DISCONNECT, 0, 0, this.mmBuffer)
                    readMsg.sendToTarget()
                    break
                }

                // Send the obtained bytes to the UI activity.
                val readMsg = this.handler.obtainMessage(this@RuxorBluetoothImpl.MESSAGE_READ, numBytes, -1, this.mmBuffer)
                readMsg.sendToTarget()
            }
        }

        // Call this from the main activity to send data to the remote device.
        fun write(bytes: ByteArray) {
            try {
                this.mmOutStream.write(bytes)
            } catch (e: IOException) {
                Log.e(TAG, "Error occurred when sending data", e)

                // Send a failure message back to the activity.
                val writeErrorMsg = this.handler.obtainMessage(this@RuxorBluetoothImpl.MESSAGE_TOAST)
                val bundle = Bundle().apply {
                    putString("toast", "Couldn't send data to the other device")
                }
                writeErrorMsg.data = bundle
                this.handler.sendMessage(writeErrorMsg)
                return
            }

            // Share the sent message with the UI activity.
            val writtenMsg = this.handler.obtainMessage(this@RuxorBluetoothImpl.MESSAGE_WRITE, -1, -1, mmBuffer)
            writtenMsg.sendToTarget()
        }

        // Call this method from the main activity to shut down the connection.
        fun cancel() {
            try {
                this.isLoop = false
                this.socket.close()
            } catch (e: IOException) {
                Log.e(this@RuxorBluetoothImpl.TAG, "Could not close the connect socket", e)
            }
        }
    }
}