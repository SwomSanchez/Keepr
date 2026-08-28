package com.keepr.app.manager

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log

object RefreshRateManager {
    private const val TAG = "RefreshRateManager"
    const val MIUI_REFRESH_RATE = "miui_refresh_rate"
    const val TARGET_HZ = 120

    val REFRESH_RATE_URI: Uri by lazy {
        Settings.Secure.getUriFor(MIUI_REFRESH_RATE)
    }

    fun hasPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun getCurrentRefreshRate(context: Context): Int {
        return try {
            Settings.Secure.getInt(context.contentResolver, MIUI_REFRESH_RATE, TARGET_HZ)
        } catch (_: Exception) {
            TARGET_HZ
        }
    }

    /**
     * Sub-millisecond direct write with fast check.
     * Prevents loops if already 120Hz.
     */
    fun force120Hz(context: Context): Boolean {
        return try {
            val resolver = context.contentResolver
            val current = Settings.Secure.getInt(resolver, MIUI_REFRESH_RATE, 60)
            if (current != TARGET_HZ) {
                Settings.Secure.putInt(resolver, MIUI_REFRESH_RATE, TARGET_HZ)
                Log.i(TAG, "⚡ [SUB-MS ENFORCED] $MIUI_REFRESH_RATE locked to $TARGET_HZ")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed setting $MIUI_REFRESH_RATE: ${e.message}")
            false
        }
    }
}

