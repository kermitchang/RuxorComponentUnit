package com.ruxor.ruxorcomponentunit.component.camera

import android.app.Activity
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.Image
import android.media.ImageReader
import android.media.ImageReader.OnImageAvailableListener
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.annotation.RequiresApi
import com.ruxor.ruxorcomponentunit.component.KBaseObject
import com.ruxor.ruxorcomponentunit.component.permission.KPermissionImpl
import java.nio.ByteBuffer
import java.util.concurrent.Semaphore

class KCameraSource(context: Context, activity: Activity) : KBaseObject() {

    private val DEFAULT_CAMERA_WIDTH = 1920
    private val DEFAULT_CAMERA_HEIGHT = 1080

    private val activity = activity
    private var backgroundHandler: Handler? = null
    private var backgroundThread: HandlerThread? = null
    private val byteBufferStreamList: ArrayList<ByteArray> = ArrayList()
    private var cameraCaptureSession: CameraCaptureSession? = null
    private var cameraDevice : CameraDevice ?= null
    private var cameraId : String ?= null
    private var cameraIndex : Int ?= null
    private val cameraManager : CameraManager by lazy {
        this.context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }
    private val context= context
    private var imageReader: KCameraAutoCloseable<ImageReader>? = null
    private var kCameraCallback : KCameraCallback ?= null
    private var mediaRecorder: MediaRecorder? = null
    private val permissionImpl: KPermissionImpl by lazy {
        KPermissionImpl(this.context, this.activity)
    }
    private var previewBuilder: CaptureRequest.Builder? = null
    private var previewSize = Size(this.DEFAULT_CAMERA_WIDTH, this.DEFAULT_CAMERA_HEIGHT)
    private val semaphore = Semaphore(1)


    private val imageReaderAvailable = OnImageAvailableListener { imageReader ->
        val image = imageReader.acquireLatestImage()
        if (image != null) {
            synchronized(byteBufferStreamList) {
                val imageData = getDataFromImage(image)
                imageData?.let {
                    this.byteBufferStreamList.add(imageData)
                    if (this.byteBufferStreamList.size > 2) {
                        this.kCameraCallback?.getFrame(this.byteBufferStreamList[1], this.previewSize.width, this.previewSize.height)
                        this.byteBufferStreamList.removeAt(0)
                    }
                }
                image.close()
            }
        }
    }

    private val cameraStateCallback: CameraDevice.StateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            Log.d(TAG, "Kermit onOpen")
            this@KCameraSource.cameraDevice = camera
            this@KCameraSource.startLivePreview()
            this@KCameraSource.semaphore.release()
        }

        override fun onDisconnected(camera: CameraDevice) {
            Log.d(TAG, "Kermit onDisconnected")
            this@KCameraSource.semaphore.release()
            this@KCameraSource.cameraDevice?.close()
            this@KCameraSource.cameraDevice = null
        }

        override fun onError(camera: CameraDevice, error: Int) {
            Log.d(TAG, "Kermit onError")
            this@KCameraSource.semaphore.release()
            this@KCameraSource.cameraDevice?.close()
            this@KCameraSource.cameraDevice = null
        }
    }

    private val cameraLivePreviewCaptureSessionStateCallback: CameraCaptureSession.StateCallback = object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                Log.d(TAG, "Kermit onConfigured - Success")
                this@KCameraSource.cameraCaptureSession = session
                this@KCameraSource.updateLivePreview()
            }

            override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
                Log.e(TAG, "Kermit onConfigureFailed - Failed")
            }

            override fun onClosed(session: CameraCaptureSession) {
                super.onClosed(session)
            }
        }

    private fun byteMergerAll(vararg values: ByteArray): ByteArray {
        var lengthByte = 0
        for (i in values.indices)
            lengthByte += values[i].size
        val allByte = ByteArray(lengthByte)
        var countLength = 0
        for (i in values.indices) {
            val byteArray = values[i]
            System.arraycopy(byteArray, 0, allByte, countLength, byteArray.size)
            countLength += byteArray.size
        }
        return allByte
    }

    private fun checkCameraDevice() {
        val cameraDeviceIdList = this.cameraManager.cameraIdList
        Log.i(TAG, "Kermit camera Size: " + cameraDeviceIdList.size)
        this.cameraIndex?.let { cameraindex ->
            if (cameraDeviceIdList.isEmpty() || cameraDeviceIdList.size < cameraindex) return
            this.cameraId = cameraDeviceIdList[cameraindex].also { cameraId ->
                val cameraCharacteristics = this.cameraManager.getCameraCharacteristics(cameraId)

                cameraCharacteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)?.let { cameraInfoSupportHardwareLevel ->
                    Log.d(TAG, "Kermit info support hardware level -> $cameraInfoSupportHardwareLevel")
                }

                cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)?.let { streamConfigurationMap ->
                    streamConfigurationMap.getOutputSizes(ImageFormat.YUV_420_888).forEach { previewSize ->
                        Log.d(TAG, "Kermit camera support size -> $previewSize")
                    }
                }

                cameraCharacteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_MODES)?.let { cameraControlModes ->
                    cameraControlModes.forEach { cameraControlMode ->
                        Log.d(TAG, "Kermit control mode -> $cameraControlMode")
                    }
                }

                cameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES)?.let { cameraAeModes ->
                    cameraAeModes.forEach { cameraAeMode ->
                        Log.d(TAG, "Kermit ae mode -> $cameraAeMode")
                    }
                }

                cameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)?.let { cameraAeTargetFpsRanges ->
                    cameraAeTargetFpsRanges.forEach { cameraAeTargetFpsRange ->
                        Log.d(TAG, "Kermit ae target fps range -> Lower:" + cameraAeTargetFpsRange.lower + " Upper:" + cameraAeTargetFpsRange.upper)
                    }
                }

                cameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)?.let { cameraAeCompensationRange ->
                    Log.d(TAG,"Kermit ae compensation range -> Lower:" + cameraAeCompensationRange.lower + " Upper:" + cameraAeCompensationRange.upper)
                }

                cameraCharacteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE)?.let { cameraAeMaxRegion ->
                    Log.d(TAG,"Kermit ae max region -> $cameraAeMaxRegion")
                }

                cameraCharacteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)?.let { cameraAfModes ->
                    cameraAfModes.forEach { cameraAfMode ->
                        Log.d(TAG,"Kermit af mode -> $cameraAfMode")
                    }
                }

                cameraCharacteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_EFFECTS)?.let { cameraEffects ->
                    cameraEffects.forEach { cameraEffect ->
                        Log.d(TAG, "Kermit camera effect -> $cameraEffect")
                    }
                }
            }
        }
    }

    public fun closeCamera() {
        try {
            this.semaphore.acquire()
            this.closePreviewSession()
            this.cameraDevice?.close()
            this.cameraDevice = null
            this.imageReader?.close()
            this.imageReader = null
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera closing.")
        } finally {
            this.semaphore.release()
        }
        this.stopBackgroundThread()
        this.byteBufferStreamList.clear()
    }

    private fun closePreviewSession() {
        if (this.cameraCaptureSession != null) {
            this.cameraCaptureSession?.close()
            this.cameraCaptureSession = null
        }
    }

    private fun getDataFromImage(image: Image): ByteArray? {

        val planes = image.planes.size
        val buffers = arrayOfNulls<ByteBuffer>(3)
        val numberBuffers = IntArray(3)
        val bytesPlans = arrayOfNulls<ByteArray>(3)
        for (i in 0 until planes) {
            buffers[i] = image.planes[i].buffer
            buffers[i]?.let { buffer ->
                numberBuffers[i] = buffer.remaining()
                bytesPlans[i] = ByteArray(numberBuffers[i])
                bytesPlans[i]?.let { buffer.get(it) }
            }
        }
        image.close()
        if (planes > 1) {
            return bytesPlans[0]?.let { bytesPlans[1]?.let { it1 -> byteMergerAll(it, it1) } }
        }
        return null
    }

    public fun openCamera(cameraIndex:Int) {
        Log.d(TAG,"Kermit openCamera index: $cameraIndex")
        this.cameraIndex = cameraIndex
        this.permissionImpl.checkPermission(arrayOf(KPermissionImpl.CAMERA))
        this.checkCameraDevice()
        this.byteBufferStreamList.clear()
        this.startBackgroundThread()
        this.imageReader = KCameraAutoCloseable<ImageReader>(ImageReader.newInstance(
                this@KCameraSource.previewSize.width,
                this@KCameraSource.previewSize.height,
                ImageFormat.YUV_420_888,
                1
            )
        ).also { imagereader ->
            imagereader.get()?.setOnImageAvailableListener(this.imageReaderAvailable, this.backgroundHandler)
        }
        this.cameraId?.let { cameraId ->
            this.cameraManager.openCamera(cameraId, this.cameraStateCallback, this.backgroundHandler)
        }
    }

    public fun setKCameraCallback(kCameraCallback: KCameraCallback) {
        this.kCameraCallback = kCameraCallback
    }

    public fun setPreviewSize(width:Int, height:Int) {
        this.previewSize = Size(width, height)
    }

    private fun startBackgroundThread() {
        this.backgroundThread = HandlerThread("CameraBackground").also { handlerThread ->
            handlerThread.start()
            this.backgroundHandler = Handler(handlerThread.looper)
        }
    }

    private fun startLivePreview() {
        Log.d(TAG, "Kermit startLivePreview")
        try {
            this.closePreviewSession()
            this.cameraDevice?.let { cameraDevice ->
                this.previewBuilder = null
                this.previewBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).also { previewBuilder ->
                    this.imageReader?.get()?.surface?.let { previewBuilder.addTarget(it) }
//                    previewBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_OFF)
//                    previewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
//                    previewBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, 0.0f)
//                    previewBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, 100)
//                    previewBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, 6000000L)
//                    previewBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(30, 30))
                }
                val surfaces = listOf(this.imageReader?.get()?.surface)
                cameraDevice.createCaptureSession(surfaces, this.cameraLivePreviewCaptureSessionStateCallback, this.backgroundHandler)
            };
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Kermit startPreview: $e")
            e.printStackTrace()
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    public fun startRecord(fileName: String) {
        this.closePreviewSession()
        this.mediaRecorder = MediaRecorder(this.context).also { mediaRecorder ->
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mediaRecorder.setOutputFile(fileName)
            mediaRecorder.setVideoEncodingBitRate(10000000)
            mediaRecorder.setVideoFrameRate(30)
            mediaRecorder.setVideoSize(previewSize.width, previewSize.height)
            mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        }
        this.mediaRecorder?.prepare()
        this.startRecordPreview()
    }

    private fun startRecordPreview() {
        Log.d(TAG, "Kermit startRecordPreview")
        try {
            this.closePreviewSession()
            this.cameraDevice?.let { cameraDevice ->
                this.previewBuilder = null
                this.previewBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).also { previewBuilder ->
//                    previewBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_OFF)
//                    previewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
//                    previewBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, 0.0f)
//                    previewBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, 100)
//                    previewBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, 6000000L)
//                    previewBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(30, 30))
                }
                val surfaces: ArrayList<Surface> = ArrayList()
                this.imageReader?.get()?.surface?.let { previewSurface ->
                    surfaces.add(previewSurface)
                    this.previewBuilder?.addTarget(previewSurface)
                }
                this.mediaRecorder?.surface?.let { recorderSurface ->
                    surfaces.add(recorderSurface)
                    this.previewBuilder?.addTarget(recorderSurface)
                }
                this.cameraDevice?.createCaptureSession(surfaces, this.cameraLivePreviewCaptureSessionStateCallback, this.backgroundHandler)
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Kermit startPreview: $e")
            e.printStackTrace()
        }
    }

    private fun stopBackgroundThread() {
        if (this.backgroundThread == null) return
        this.backgroundThread?.quitSafely()
        try {
            this.backgroundThread?.join()
            this.backgroundThread = null
            this.backgroundHandler = null
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    public fun stopRecord() {
        Log.d(TAG, "Kermit stopRecording")

        try {
            this.cameraCaptureSession?.stopRepeating()
            this.cameraCaptureSession?.abortCaptures()
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }

        try {
            this.mediaRecorder?.pause()
            this.mediaRecorder?.stop()
            this.mediaRecorder?.reset()
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Kermit e: $e")
        }
        this.startLivePreview()
    }

    private fun updateLivePreview() {
        try {
            Log.d(TAG, "Kermit updateLivePreview")
            this.cameraCaptureSession?.let { cameraCaptureSession ->
                this.previewBuilder?.let { previewBuilder ->
                    cameraCaptureSession.setRepeatingRequest(previewBuilder.build(), null, this.backgroundHandler)
                }
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

}