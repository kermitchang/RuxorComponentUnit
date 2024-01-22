package com.ruxor.ruxorcomponentunit.component.camera

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.ImageFormat
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.media.ImageReader
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Message
import android.util.Log
import android.util.Size
import androidx.annotation.RequiresApi
import com.jiangdg.usb.DeviceFilter
import com.jiangdg.usb.USBMonitor
import com.jiangdg.uvc.IFrameCallback
import com.jiangdg.uvc.UVCCamera
import com.ruxor.ruxorcomponentunit.R
import com.ruxor.ruxorcomponentunit.component.KBaseObject
import java.io.IOException

class KUVCCameraSource(private val context: Context) : KBaseObject() {

    private val ACTION_USB_PERMISSION = "ACTION.USB_PERMISSION"
    private val DEFAULT_CAMERA_HEIGHT = 1080
    private val DEFAULT_CAMERA_WIDTH = 1920
    private val MAX_FPS = 60
    private val MIN_FPS = 1

    private var backgroundHandler: Handler? = null
    private var byteBufferStreamList =  mutableListOf<ByteArray>()
    private var kCameraCallback: KCameraCallback ?= null
    private var cameraIndex : Int ?= null
    private var imageReader: KCameraAutoCloseable<ImageReader>? = null
    private var mediaRecorder: MediaRecorder? = null
    private var previewSize = Size(this.DEFAULT_CAMERA_WIDTH, this.DEFAULT_CAMERA_HEIGHT)
    private var usbCameraDevice:UsbDevice ?= null
    private var usbControlBlock: USBMonitor.UsbControlBlock? = null
    private val usbManager: UsbManager = this.context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val usbMonitor: USBMonitor by lazy {
        USBMonitor(this.context, this.usbMonitorListener)
    }
    private var uvcCamera: UVCCamera?= null
    private var vendorId = this.context.resources.getInteger(R.integer.logitech_c615_vendor_id)
    private var productId = this.context.resources.getInteger(R.integer.logitech_c615_product_id)

    private val frameCallback = IFrameCallback { byteBuffer ->
//        Log.d(TAG,"Buffer size: ${byteBuffer?.capacity()}")

        byteBuffer?.capacity()?.let { bufSize ->
            val byteArray = ByteArray(bufSize)
            byteBuffer.get(byteArray)

            synchronized (this.byteBufferStreamList) {
                this.byteBufferStreamList.add(byteArray);
                if(this.byteBufferStreamList.size > 2) {
                    this.kCameraCallback?.getFrame(this.byteBufferStreamList[1], this.previewSize.width, this.previewSize.height)
                    this.byteBufferStreamList.removeAt(0)
                }
            }
        }
    }
    private val imageReaderAvailable = ImageReader.OnImageAvailableListener { imageReader ->
        val image = imageReader.acquireLatestImage()
        image?.close()
    }
    private val usbMonitorListener = object : USBMonitor.OnDeviceConnectListener {
        override fun onAttach(device: UsbDevice?) {
            Log.w(TAG,"USBMonitor OnDeviceConnectListener onAttach")
        }

        override fun onDetach(device: UsbDevice?) {
            Log.w(TAG,"USBMonitor OnDeviceConnectListener onDetach")
        }

        override fun onConnect(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?, createNew: Boolean) {
            Log.w(TAG,"USBMonitor OnDeviceConnectListener onConnect")
        }

        override fun onDisconnect(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {
            Log.w(TAG,"USBMonitor OnDeviceConnectListener onDisconnect")
        }

        override fun onCancel(device: UsbDevice?) {
            Log.w(TAG,"USBMonitor OnDeviceConnectListener onCancel")
        }
    }
    private val usbPermissionBroadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                if (this@KUVCCameraSource.ACTION_USB_PERMISSION == intent.action) {
                    synchronized(this) {
                        val selection = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                        if (selection) {
                            Log.d(TAG,"Get the permission for the device")
                            this@KUVCCameraSource.cameraIndex?.let { cameraIndex ->
                                this@KUVCCameraSource.openUVCCamera(cameraIndex)
                            }
                        } else {
                            Log.e(TAG,"Don't get the permission for the device")
                        }
                    }
                }
            }
        }
    }

    private fun checkUVCCameraPermission() {
        if (this.usbCameraDevice == null) {
            Log.e(TAG,"Can't not find UVCCamera on usb device list")
            return
        }

        this.context.registerReceiver(this.usbPermissionBroadReceiver, IntentFilter(this.ACTION_USB_PERMISSION))
        val pendingIntent = PendingIntent.getBroadcast(this.context, 0, Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_MUTABLE)
        this.usbManager.requestPermission(this.usbCameraDevice, pendingIntent)
    }

    public fun closeUVCCamera() {
        this.byteBufferStreamList.clear()
        this.uvcCamera?.close()
    }

    private fun connectUVCCamera() {
        Log.d(TAG,"Connect to the UVC Camera")

        this.usbMonitor.setDeviceFilter(DeviceFilter(this.usbCameraDevice))

        this.usbMonitor.deviceList.forEachIndexed { index, usbDevice ->
            Log.d(TAG,"UsbMonitor device $index -> ${usbDevice.deviceName}")
        }

        if (!this.usbMonitor.hasPermission(this.usbCameraDevice)) {
            Log.e(TAG,"UsbMonitor don't have permission")
            this.usbMonitor.requestPermission(this.usbCameraDevice)
        } else {
            Log.d(TAG,"UsbMonitor has permission")
            Log.d(TAG, "Usb DeviceId: ${this.usbCameraDevice?.deviceId}")

            try {
                this.byteBufferStreamList.clear()
                this.imageReader = KCameraAutoCloseable<ImageReader>(
                    ImageReader.newInstance(
                        this.previewSize.width,
                        this.previewSize.height,
                        ImageFormat.JPEG,
                        2
                    )
                )
                if (this.imageReader != null) {
                    this.imageReader?.get()?.setOnImageAvailableListener(this.imageReaderAvailable, this.backgroundHandler)
                }
                this.usbControlBlock = this.usbMonitor.openDevice(this.usbCameraDevice)
                this.uvcCamera = UVCCamera().apply {
                    open(this@KUVCCameraSource.usbControlBlock)
                }
                Log.d(TAG,"The UVCCamera support size ${this.uvcCamera?.supportedSize}")
                this.uvcCamera?.setPreviewSize(this.previewSize.width, this.previewSize.height, this.MIN_FPS, this.MAX_FPS, UVCCamera.FRAME_FORMAT_MJPEG, UVCCamera.DEFAULT_BANDWIDTH)
                this.uvcCamera?.setFrameCallback(this.frameCallback, UVCCamera.PIXEL_FORMAT_YUV420SP)
                this.uvcCamera?.setPreviewDisplay(this@KUVCCameraSource.imageReader?.get()?.surface)
                this.uvcCamera?.startPreview()
                this.uvcCamera?.updateCameraParams()
                this.autoFocus = false
                this.autoWhiteBalance = true
            } catch (e: Exception) {
                Log.e(TAG,"Open camera error $e")
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun connectRecorderUVCCamera(fileName: String) {
        Log.d(TAG,"Connect to the UVC Camera")

        this.usbMonitor.setDeviceFilter(DeviceFilter(this.usbCameraDevice))

        this.usbMonitor.deviceList.forEachIndexed { index, usbDevice ->
            Log.d(TAG,"UsbMonitor device $index -> ${usbDevice.deviceName}")
        }

        if (!this.usbMonitor.hasPermission(this.usbCameraDevice)) {
            Log.e(TAG,"UsbMonitor don't have permission")
            this.usbMonitor.requestPermission(this.usbCameraDevice)
        } else {
            Log.d(TAG,"UsbMonitor has permission")
            Log.d(TAG, "Usb DeviceId: ${this.usbCameraDevice?.deviceId}")

            try {
                this.byteBufferStreamList.clear()
                this.imageReader = KCameraAutoCloseable<ImageReader>(
                    ImageReader.newInstance(
                        this.previewSize.width,
                        this.previewSize.height,
                        ImageFormat.JPEG,
                        2
                    )
                )

                if (this.imageReader != null) {
                    this.imageReader?.get()?.setOnImageAvailableListener(this.imageReaderAvailable, this.backgroundHandler)
                }

                this.mediaRecorder = MediaRecorder(this.context).also { mediaRecorder ->
                    mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
                    mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
                    mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    mediaRecorder.setOutputFile(fileName)
                    mediaRecorder.setVideoEncodingBitRate(10000000)
                    mediaRecorder.setVideoFrameRate(30)
                    mediaRecorder.setVideoSize(this.previewSize.width, this.previewSize.height)
                    mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                    mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    mediaRecorder.setPreviewDisplay(this@KUVCCameraSource.imageReader?.get()?.surface);
                }

                try {
                    this.mediaRecorder?.prepare()
                } catch (e: IOException) {
                    e.printStackTrace()
                }

                this.usbControlBlock = this.usbMonitor.openDevice(this.usbCameraDevice)
                this.uvcCamera = UVCCamera().apply {
                    open(this@KUVCCameraSource.usbControlBlock)
                }
                Log.d(TAG,"The UVCCamera support size ${this.uvcCamera?.supportedSize}")
                this.uvcCamera?.setPreviewSize(this.previewSize.width, this.previewSize.height, this.MIN_FPS, this.MAX_FPS, UVCCamera.FRAME_FORMAT_MJPEG, UVCCamera.DEFAULT_BANDWIDTH)
                this.uvcCamera?.setFrameCallback(this.frameCallback, UVCCamera.PIXEL_FORMAT_YUV420SP)
                this.uvcCamera?.setPreviewDisplay(this.mediaRecorder?.surface)
                this.uvcCamera?.startPreview()
                this.uvcCamera?.updateCameraParams()
                this.autoFocus = false
                this.autoWhiteBalance = true

                this.mediaRecorder?.start()
            } catch (e: Exception) {
                Log.e(TAG,"Open camera error $e")
            }
        }
    }

    var autoFocus = this.uvcCamera?.autoFocus
        get() = this.uvcCamera?.autoFocus
        set(value) {
            field = value
            value?.let {
                this.uvcCamera?.autoFocus = value
            }
        }

    var autoWhiteBalance = this.uvcCamera?.autoWhiteBlance
        get() = this.uvcCamera?.autoWhiteBlance
        set(value) {
            field = value
            value?.let {
                this.uvcCamera?.autoWhiteBlance = value
            }
        }

    var brightness = this.uvcCamera?.brightness
        get() = this.uvcCamera?.brightness
        set(value) {
            field = value
            value?.let {
                this.uvcCamera?.brightness = value
            }
        }

    var focus = this.uvcCamera?.focus
        get() = this.uvcCamera?.focus
        set(value) {
            field = value
            value?.let {
                this.uvcCamera?.focus = value
            }
        }

    var gain = this.uvcCamera?.gain
        get() = this.uvcCamera?.gain
        set(value) {
            field = value
            value?.let {
                this.uvcCamera?.gain = value
            }
        }

    var gamma = this.uvcCamera?.gamma
        get() = this.uvcCamera?.gamma
        set(value) {
            field = value
            value?.let {
                this.uvcCamera?.gamma = value
            }
        }

    var hue = this.uvcCamera?.hue
        get() = this.uvcCamera?.hue
        set(value) {
            field = value
            value?.let {
                this.uvcCamera?.hue = value
            }
        }

    var saturation = this.uvcCamera?.saturation
        get() = this.uvcCamera?.saturation
        set(value) {
            field = value
            value?.let {
                this.uvcCamera?.saturation = value
            }
        }

    var whiteBlance = this.uvcCamera?.whiteBlance
        get() = this.uvcCamera?.whiteBlance
        set(value) {
            field = value
            value?.let {
                this.uvcCamera?.whiteBlance = value
            }
        }

    var zoom = this.uvcCamera?.zoom
        get() = this.uvcCamera?.zoom
        set(value) {
            field = value
            value?.let {
                this.uvcCamera?.zoom = value
            }
        }

    public fun getImageBuffer(): ByteArray? {
        synchronized(this.byteBufferStreamList) {
            val listSize: Int = this.byteBufferStreamList.size
            return if (listSize == 0) null else this.byteBufferStreamList[listSize - 1]
        }
    }

    private fun getUVCDevice(): UsbDevice? {
        this.usbManager.deviceList.values.forEach { usbDevice ->
            Log.d(TAG,"Kermit UsbDevice -> " +
                    "Name:${usbDevice.deviceName}" +
                    " ID:${usbDevice.deviceId}" +
                    " VID:${usbDevice.vendorId}" +
                    " PID:${usbDevice.productId}" +
                    " CL:${usbDevice.deviceClass}" +
                    " SC:${usbDevice.deviceSubclass}" +
                    " PO:${usbDevice.deviceProtocol}" +
                    " MN:${usbDevice.manufacturerName}" +
                    " PN:${usbDevice.productName}")
            if ( this.vendorId == usbDevice.vendorId && this.productId == usbDevice.productId ) {
                return usbDevice
            }
        }
        return null
    }

    public fun openUVCCamera(cameraIndex:Int) {
        this.cameraIndex = cameraIndex
        this.byteBufferStreamList.clear()
        this.usbCameraDevice = this.getUVCDevice()

        if(!this.usbManager.hasPermission(this.usbCameraDevice)) {
            this.checkUVCCameraPermission()
        }
        else {
            this.connectUVCCamera()
        }
    }

    public fun setPreviewSize(width:Int, height:Int) {
        this.previewSize = Size(width, height)
    }

    public fun setCameraID (vendorId:Int, productId:Int) {
        this.vendorId = vendorId
        this.productId = productId
    }

    public fun setCameraCallback(kCameraCallback: KCameraCallback) {
        this.kCameraCallback = kCameraCallback;
    }

    @RequiresApi(Build.VERSION_CODES.S)
    public fun startRecordingVideo(fileName: String) {
        Log.d(TAG, "Kermit startRecordingVideo")
        this.closeUVCCamera()
        this.connectRecorderUVCCamera(fileName)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun stopRecordingVideo() {
        Log.d(TAG, "Kermit stopRecordingVideo")
        try {
            this.mediaRecorder?.pause()
            this.mediaRecorder?.stop()
            this.mediaRecorder?.reset()
            this.mediaRecorder?.release()
            this.mediaRecorder = null
            this.cameraIndex?.let { cameraIndex ->
                this.openUVCCamera(cameraIndex)
            }
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Kermit e: $e")
        }
    }
}