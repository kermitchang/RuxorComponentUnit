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
import android.os.Environment
import android.os.Handler
import android.os.StatFs
import android.util.Log
import android.util.Size
import com.jiangdg.usb.DeviceFilter
import com.jiangdg.usb.USBMonitor
import com.jiangdg.uvc.IButtonCallback
import com.jiangdg.uvc.IFrameCallback
import com.jiangdg.uvc.IStatusCallback
import com.jiangdg.uvc.UVCCamera
import com.ruxor.ruxorcomponentunit.R
import com.ruxor.ruxorcomponentunit.component.KBaseObject
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.Calendar

class KUVCCameraSource(private val context: Context) : KBaseObject() {

    companion object {
        public val UVC_AUTO_EXPOSURE_MODE_MANUAL = 1
        public val UVC_AUTO_EXPOSURE_MODE_AUTO = 2
        public val UVC_AUTO_EXPOSURE_MODE_SHUTTER_PRIORITY = 4
        public val UVC_AUTO_EXPOSURE_MODE_APERTURE_PRIORITY = 8
    }

    private val ACTION_USB_PERMISSION = "ACTION.USB_PERMISSION"
    private val DEFAULT_CAMERA_HEIGHT = 1080
    private val DEFAULT_CAMERA_WIDTH = 1920
    private val DEFAULT_FREE_SPACE_PERCENT = 0.2
    private val DEFAULT_RECORD_DURATION = (15 * 60 * 1000).toInt()
    private val MAX_FPS = 60
    private val MIN_FPS = 1

    private var autoRecord = false
    private var backgroundHandler: Handler? = null
    private var byteBufferStreamList =  mutableListOf<ByteArray>()
    private var cameraIndex : Int ?= null
    private var isVideoRecording = false
    private var imageReader: KCameraAutoCloseable<ImageReader>? = null
    private var kCameraCallback: KCameraCallback ?= null
    private var mediaRecorder: MediaRecorder? = null
    private var mediaRecordOrientation = KMediaRecordOrientationType.ORIENTATION_0
    private var previewSize = Size(this.DEFAULT_CAMERA_WIDTH, this.DEFAULT_CAMERA_HEIGHT)
    private var productId = this.context.resources.getInteger(R.integer.logitech_c615_product_id)
    private var recordDuration = this.DEFAULT_RECORD_DURATION
    private var storageSpacePercent = this.DEFAULT_FREE_SPACE_PERCENT
    private var usbCameraDevice:UsbDevice ?= null
    private var usbControlBlock: USBMonitor.UsbControlBlock? = null
    private var usbDevice: UsbDevice ?= null
    private val usbManager: UsbManager = this.context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val usbMonitor: USBMonitor by lazy {
        USBMonitor(this.context, this.usbMonitorListener)
    }
    private var uvcCamera: UVCCamera?= null
    private var vendorId = this.context.resources.getInteger(R.integer.logitech_c615_vendor_id)
    private var videoRecordPath = "/storage/emulated/0/FaceAI/Video"

    private val iButtonCallback = IButtonCallback { button, state ->
        //Log.d(TAG,"ButtonCallback -> button:$button state:$state")
    }
    private val iFrameCallback = IFrameCallback { byteBuffer ->
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
    private val iStateCallback = IStatusCallback { statusClass, event, selector, statusAttribute, data ->
        //Log.d(TAG,"StateCallback -> statusClass:$statusClass, event:$event, selector:$selector, statusAttribute:$statusAttribute, data:$data")
    }
    private val mediaRecorderInfoList = MediaRecorder.OnInfoListener { mediaRecorder, what, extra ->
        Log.w(TAG,"Media Recorder Info List what:$what, extra:$extra")
        if ( what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED ) {
            Log.d(TAG,"OnInfoListener MEDIA_RECORDER_INFO_MAX_DURATION_REACHED")
            if (this@KUVCCameraSource.checkStorageSpace()) {
                this@KUVCCameraSource.waitRecording()
                Handler().postDelayed({
                    this@KUVCCameraSource.initialMediaRecord()
                    this@KUVCCameraSource.startRecording()
                }, 1000)
            }
            else
                this@KUVCCameraSource.stopRecordVideo()
        }
    }
    private val mediaRecorderErrorList = MediaRecorder.OnErrorListener { mediaRecorder, what, extra ->
        Log.w(TAG,"MediaRecorderErrorList what:$what, extra:$extra")
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

    var backlight = this.uvcCamera?.backlight
        get() = this.uvcCamera?.backlight
        set(value) {
            field = value
            value?.let {
                this.uvcCamera?.backlight = value
            }
        }

    var backlightMax = this.uvcCamera?.backlightMax
        get() = this.uvcCamera?.backlightMax

    var backlightMin = this.uvcCamera?.backlightMin
        get() = this.uvcCamera?.backlightMin

    var brightness = this.uvcCamera?.brightness
        get() = this.uvcCamera?.brightness
        set(value) {
            field = value
            value?.let {
                this.uvcCamera?.brightness = value
            }
        }

    var brightnessMax = this.uvcCamera?.brightnessMax
        get() = this.uvcCamera?.brightnessMax

    var brightnessMin = this.uvcCamera?.brightnessMin
        get() = this.uvcCamera?.brightnessMin

    var contrast = this.uvcCamera?.contrast
        get() = this.uvcCamera?.contrast
        set(value) {
            field = value
            value?.let {
                this.uvcCamera?.contrast = value
            }
        }

    var contrastMax = this.uvcCamera?.contrastMax
        get() = this.uvcCamera?.contrastMax

    var contrastMin = this.uvcCamera?.contrastMin
        get() = this.uvcCamera?.contrastMin

    var exposure = this.uvcCamera?.exposure
        get() = this.uvcCamera?.exposure
        set(value) {
            field = value
            value?.let {
                this.uvcCamera?.exposure = value
            }
        }

    var exposureMax = this.uvcCamera?.exposureMax
        get() = this.uvcCamera?.exposureMax

    var exposureMin = this.uvcCamera?.exposureMin
        get() = this.uvcCamera?.exposureMin

    var exposureMode = this.uvcCamera?.exposureMode
        get() = this.uvcCamera?.exposureMode
        set(value) {
            field = value
            value?.let {
                this.uvcCamera?.exposureMode = value
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

    var focusMax = this.uvcCamera?.focusMax
        get() = this.uvcCamera?.focusMax

    var focusMin = this.uvcCamera?.focusMin
        get() = this.uvcCamera?.focusMin

    var gain = this.uvcCamera?.gain
        get() = this.uvcCamera?.gain
        set(value) {
            field = value
            value?.let {
                this.uvcCamera?.gain = value
            }
        }

    var gainMax = this.uvcCamera?.gainMax
        get() = this.uvcCamera?.gainMax

    var gainMin = this.uvcCamera?.gainMin
        get() = this.uvcCamera?.gainMin

    var gamma = this.uvcCamera?.gamma
        get() = this.uvcCamera?.gamma
        set(value) {
            field = value
            value?.let {
                this.uvcCamera?.gamma = value
            }
        }

    var gammaMax = this.uvcCamera?.gammaMax
        get() = this.uvcCamera?.gammaMax

    var gammaMin = this.uvcCamera?.gammaMin
        get() = this.uvcCamera?.gammaMin

    var hue = this.uvcCamera?.hue
        get() = this.uvcCamera?.hue
        set(value) {
            field = value
            value?.let {
                this.uvcCamera?.hue = value
            }
        }

    var hueMax = this.uvcCamera?.hueMax
        get() = this.uvcCamera?.hueMax

    var hueMin = this.uvcCamera?.hueMin
        get() = this.uvcCamera?.hueMin

    var saturation = this.uvcCamera?.saturation
        get() = this.uvcCamera?.saturation
        set(value) {
            field = value
            value?.let {
                this.uvcCamera?.saturation = value
            }
        }

    var saturationMax = this.uvcCamera?.saturationMax
        get() = this.uvcCamera?.saturationMax

    var saturationMin = this.uvcCamera?.saturationMin
        get() = this.uvcCamera?.saturationMin

    var sharpness = this.uvcCamera?.sharpness
        get() = this.uvcCamera?.sharpness
        set(value) {
            field = value
            value?.let {
                this.uvcCamera?.sharpness = value
            }
        }

    var sharpnessMax = this.uvcCamera?.sharpnessMax
        get() = this.uvcCamera?.sharpnessMax

    var sharpnessMin = this.uvcCamera?.sharpnessMin
        get() = this.uvcCamera?.sharpnessMin

    var whiteBalance = this.uvcCamera?.whiteBlance
        get() = this.uvcCamera?.whiteBlance
        set(value) {
            field = value
            value?.let {
                this.uvcCamera?.whiteBlance = value
            }
        }

    var whiteBalanceMax = this.uvcCamera?.whiteBlanceMax
        get() = this.uvcCamera?.whiteBlanceMax

    var whiteBalanceMin = this.uvcCamera?.whiteBlanceMin
        get() = this.uvcCamera?. whiteBlanceMin

    var zoom = this.uvcCamera?.zoom
        get() = this.uvcCamera?.zoom
        set(value) {
            field = value
            value?.let {
                this.uvcCamera?.zoom = value
            }
        }

    var zoomMax = this.uvcCamera?.zoomMax
        get() = this.uvcCamera?.zoomMax

    var zoomMin = this.uvcCamera?.zoomMin
        get() = this.uvcCamera?.zoomMin

    private fun checkStorageSpace(): Boolean{
        val internalFreeSpace = this.getInternalFreeSpace()
        Log.v(TAG, "Free Space: $internalFreeSpace")

        val internalTotalSpace = this.getInternalTotalSpace()
        Log.v(TAG, "Total Space: $internalTotalSpace")

        val internalUsedSpace = this.getInternalUsedSpace()
        Log.v(TAG, "Used Space: $internalUsedSpace")

        val nowFreeSpacePercent = 1.0 * internalFreeSpace / internalTotalSpace
        Log.v(TAG, "Now Free Space percent: $nowFreeSpacePercent")

        if (nowFreeSpacePercent < this.storageSpacePercent) {
            Log.e(TAG,"Out of disk space, lower than 20%")
            return false
        }
        return true
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
                this.uvcCamera?.setButtonCallback(this.iButtonCallback)
                this.uvcCamera?.setStatusCallback(this.iStateCallback)
                this.uvcCamera?.setFrameCallback(this.iFrameCallback, UVCCamera.PIXEL_FORMAT_YUV420SP)
                this.uvcCamera?.setPreviewDisplay(this@KUVCCameraSource.imageReader?.get()?.surface)
                this.uvcCamera?.startPreview()
                this.uvcCamera?.updateCameraParams()
            } catch (e: Exception) {
                Log.e(TAG,"Open camera error $e")
            }
        }
    }

    private fun getFileName(): String {
        val videoFolder = File(this.videoRecordPath)
        if (!videoFolder.exists()) {
            Files.createDirectories(Paths.get(this.videoRecordPath));
        }

        val time = Calendar.getInstance().time
        val formatter = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss")
        val current = formatter.format(time)
        return "${this.videoRecordPath}/$current.mp4"
    }

    private fun getInternalFreeSpace(): Long {
        //Get free Bytes...
        val statFs = StatFs(Environment.getExternalStorageDirectory().path)
        return statFs.blockSizeLong * statFs.availableBlocksLong
    }

    private fun getInternalTotalSpace(): Long {
        //Get total Bytes
        val statFs = StatFs(Environment.getExternalStorageDirectory().path)
        return statFs.blockSizeLong * statFs.blockCountLong
    }

    private fun getInternalUsedSpace(): Long {
        //Get used Bytes
        return getInternalTotalSpace() - getInternalFreeSpace()
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

    private fun initialMediaRecord() {
        if (this.mediaRecorder == null) {
            this.mediaRecorder = MediaRecorder(this.context)
        }
        this.mediaRecorder?.setAudioSource(MediaRecorder.AudioSource.MIC)
        this.mediaRecorder?.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        this.mediaRecorder?.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        this.mediaRecorder?.setVideoEncodingBitRate(10000000)
        this.mediaRecorder?.setVideoFrameRate(30)
        this.mediaRecorder?.setVideoSize(this.previewSize.width, this.previewSize.height)
        this.mediaRecorder?.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        this.mediaRecorder?.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        this.mediaRecorder?.setPreviewDisplay(this.imageReader?.get()?.surface)
        when(this.mediaRecordOrientation) {
            KMediaRecordOrientationType.ORIENTATION_0 -> this.mediaRecorder?.setOrientationHint(0)
            KMediaRecordOrientationType.ORIENTATION_90 -> this.mediaRecorder?.setOrientationHint(90)
            KMediaRecordOrientationType.ORIENTATION_180 -> this.mediaRecorder?.setOrientationHint(180)
            KMediaRecordOrientationType.ORIENTATION_270 -> this.mediaRecorder?.setOrientationHint(270)
        }
        if (this.autoRecord) {
            this.mediaRecorder?.setMaxDuration(this.recordDuration.toInt())
            this.mediaRecorder?.setOnInfoListener(this.mediaRecorderInfoList)
            this.mediaRecorder?.setOnErrorListener(this.mediaRecorderErrorList)
        } else {
            this.mediaRecorder?.setMaxDuration(0)
            this.mediaRecorder?.setOnInfoListener(null)
            this.mediaRecorder?.setOnErrorListener(this.mediaRecorderErrorList)
        }
        this.mediaRecorder?.setOutputFile(this.getFileName())
        try {
            this.mediaRecorder?.prepare()
        } catch (e: IOException) {
            e.printStackTrace()
        }
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

    public fun resetBacklight() {
        this.uvcCamera?.resetBacklight()
    }

    public fun resetBrightness() {
        this.uvcCamera?.resetBrightness()
    }

    public fun resetContrast() {
        this.uvcCamera?.resetContrast()
    }

    public fun resetExposure() {
        this.uvcCamera?.resetExposure()
    }

    public fun resetFocus() {
        this.uvcCamera?.resetFocus()
    }

    public fun resetGain() {
        this.uvcCamera?.resetGain()
    }

    public fun resetGamma() {
        this.uvcCamera?.resetGamma()
    }

    public fun resetHue() {
        this.uvcCamera?.resetHue()
    }

    public fun resetSaturation() {
        this.uvcCamera?.resetSaturation()
    }

    public fun resetSharpness() {
        this.uvcCamera?.resetSharpness()
    }

    public fun resetWhiteBalance() {
        this.uvcCamera?.resetWhiteBlance()
    }

    public fun resetZoom() {
        this.uvcCamera?.resetZoom()
    }

    public fun setCameraCallback(kCameraCallback: KCameraCallback) {
        this.kCameraCallback = kCameraCallback;
    }

    public fun setCameraID(vendorId:Int, productId:Int) {
        this.vendorId = vendorId
        this.productId = productId
    }

    public fun setFreeSpacePercent( spacePercent:Double = this.DEFAULT_FREE_SPACE_PERCENT) {
        if (spacePercent > 1.0f || spacePercent < 0.0f) {
            Log.e(TAG, "The percent need range is in 0.0 - 1.0")
        } else {
            this.storageSpacePercent = spacePercent
        }
    }

    public fun setMediaRecordOrientation (orientationType: KMediaRecordOrientationType) {
        this.mediaRecordOrientation = orientationType
    }

    public fun setPreviewSize(width:Int, height:Int) {
        this.previewSize = Size(width, height)
    }

    public fun setRecordDuration(millisecond:Int) {
        this.recordDuration = millisecond
    }

    public fun setVideoPath (videoPath:String) {
        this.videoRecordPath = videoPath
    }

    public fun startRecordVideo() {
        if (this.checkStorageSpace()) {
            if (!this.isVideoRecording) {
                this.uvcCamera?.stopPreview()
                this.initialMediaRecord()
                this.startRecording()
            }
        }
    }

    private fun startRecording() {
        this.uvcCamera?.setPreviewDisplay(this.mediaRecorder?.surface)
        this.uvcCamera?.setFrameCallback(this.iFrameCallback, UVCCamera.PIXEL_FORMAT_YUV420SP)
        this.uvcCamera?.startPreview()
        this.mediaRecorder?.start()
        this.isVideoRecording = true
    }

    public fun stopRecordVideo() {
        if (this.isVideoRecording) {
            this.mediaRecorder?.pause()
            this.mediaRecorder?.stop()
            this.mediaRecorder?.reset()
            this.mediaRecorder?.release()
            this.mediaRecorder = null
            this.isVideoRecording = false
        }
        this.uvcCamera?.stopPreview()
        this.uvcCamera?.setPreviewDisplay(this.imageReader?.get()?.surface)
        this.uvcCamera?.setFrameCallback(this.iFrameCallback, UVCCamera.PIXEL_FORMAT_YUV420SP)
        this.uvcCamera?.startPreview()
    }

    private fun waitRecording() {
        if (this.isVideoRecording) {
            this.mediaRecorder?.pause()
            this.mediaRecorder?.stop()
            this.mediaRecorder?.reset()
            this.isVideoRecording = false
            this.uvcCamera?.stopPreview()
            this.uvcCamera?.setPreviewDisplay(this.imageReader?.get()?.surface)
            this.uvcCamera?.setFrameCallback(this.iFrameCallback, UVCCamera.PIXEL_FORMAT_YUV420SP)
            this.uvcCamera?.startPreview()
        }
    }
}