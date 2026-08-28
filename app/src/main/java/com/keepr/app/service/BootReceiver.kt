package com.keepr.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return
        Log.i("BootReceiver", "Keepr boot event received: ${intent?.action}")
        try {
            KeeprForegroundService.start(context)
        } catch (_: Exception) { }
    }
}
