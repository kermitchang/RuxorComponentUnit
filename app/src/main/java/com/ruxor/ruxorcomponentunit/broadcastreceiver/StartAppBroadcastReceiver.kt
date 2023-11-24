package com.ruxor.ruxorcomponentunit.broadcastreceiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ruxor.ruxorcomponentunit.MainActivity

class StartAppBroadcastReceiver: BroadcastReceiver() {

    private val tag = "StartAppBroadcastReceiver"
    private val bootStart = false

    override fun onReceive(context: Context?, intent: Intent?) {
        intent?.let {
            if (Intent.ACTION_BOOT_COMPLETED == intent.action) {
                Log.i(tag,"Kermit ACTION_BOOT_COMPLETED")
                if (bootStart) {
                    context?.let {
                        val intentApp = Intent(context, MainActivity::class.java)
                        intentApp.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intentApp)
                    }
                }
            }
        }
    }
}