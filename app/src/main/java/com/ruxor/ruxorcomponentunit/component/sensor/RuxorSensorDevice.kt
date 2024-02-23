package com.ruxor.ruxorcomponentunit.component.sensor

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.FileReader
import java.io.IOException
import java.io.InputStreamReader

class RuxorSensorDevice(val context: Context, private val nodePath: String, private val sensorDeviceName: RuxorSensorDeviceName) {

    private val TAG:String = this.javaClass.name

    public fun getName(): RuxorSensorDeviceName {
        return this.sensorDeviceName
    }

    public fun getValue(): String?{
        return readFileReader(this.nodePath)
    }

    private fun readFileReader(endPointPath:String): String? {
        var bufferedReader:BufferedReader ?= null
        var value = ""
        try {
            bufferedReader = BufferedReader(FileReader(endPointPath))
            value = bufferedReader.readLine()
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            if (bufferedReader != null) {
                try {
                    bufferedReader.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
        if (value != "")
            return value
        return null
    }

    private fun readSystemPoint(endPointPath:String): String? {

        try {
            val runTime = Runtime.getRuntime()
            val process = runTime.exec("cat $endPointPath")
            val inputStream = process.inputStream
            val inputStreamReader = InputStreamReader(inputStream)
            val bufferedReader = BufferedReader(inputStreamReader)
            val line = bufferedReader.readLine()
            if (line != null)
                return line
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return null
    }

    private fun readFile(endPointPath:String): String? {
        val sysPath = File(endPointPath)
        var fileInputStream: FileInputStream?= null
        var fileOutputStream: FileOutputStream?= null

        Log.d(TAG,"Path: ${sysPath.path}")
        if (sysPath.exists()) {
            Log.d(TAG, "File(${sysPath.path}) is exist")
            try {
                fileInputStream = FileInputStream(sysPath)
                fileOutputStream = FileOutputStream(sysPath)
            } catch (e: FileNotFoundException) {
                e.printStackTrace()
            }
        }
        return null
    }
}