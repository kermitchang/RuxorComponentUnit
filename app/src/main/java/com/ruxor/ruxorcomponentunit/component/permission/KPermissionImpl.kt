package com.ruxor.ruxorcomponentunit.component.permission

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import com.ruxor.ruxorcomponentunit.component.KBaseObject

class KPermissionImpl(context:Context, activity: Activity): KBaseObject() {

    private val context = context
    private val activity = activity
    private var REQUEST_PERMISSION = 100000

    fun checkPermission(permissions: Array<String>) {
        var requestPermissionList = ArrayList<String>()
        permissions.forEach { permission ->
            if (ActivityCompat.checkSelfPermission(this.context, permission) != PackageManager.PERMISSION_GRANTED)
                requestPermissionList.add(permission)
        }

        if (requestPermissionList.size > 0) {
            Log.d(TAG,"Kermit don't have permission -> $requestPermissionList")
            var requestPermissionArray = arrayOfNulls<String>(requestPermissionList.size)
            requestPermissionList.toArray(requestPermissionArray)
            this.requestPermission(requestPermissionArray)
        }
    }

    private fun requestPermission(permission: Array<String?>) {
        permission.forEach { mPermission ->
            Log.d(TAG,"Kermit requestLocationPermission -> $mPermission")
        }
        ActivityCompat.requestPermissions(this.activity, permission, REQUEST_PERMISSION)
        this.REQUEST_PERMISSION += 1
    }

    companion object {
        const val ACCESS_COARSE_LOCATION = Manifest.permission.ACCESS_COARSE_LOCATION
        const val ACCESS_FINE_LOCATION = Manifest.permission.ACCESS_FINE_LOCATION
        const val BLUETOOTH_CONNECT = Manifest.permission.BLUETOOTH_CONNECT
        const val BLUETOOTH_SCAN = Manifest.permission.BLUETOOTH_SCAN
        const val CAMERA = Manifest.permission.CAMERA
        const val POST_NOTIFICATIONS = Manifest.permission.POST_NOTIFICATIONS
        const val READ_EXTERNAL_STORAGE = Manifest.permission.READ_EXTERNAL_STORAGE
        const val SYSTEM_ALERT_WINDOW = Manifest.permission.SYSTEM_ALERT_WINDOW
        const val WRITE_EXTERNAL_STORAGE = Manifest.permission.WRITE_EXTERNAL_STORAGE
    }
}