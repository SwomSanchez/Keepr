package com.keepr.app.utils

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.widget.Toast
import com.keepr.app.R

object XiaomiHelper {

    private const val PREFS_NAME = "keepr_settings"
    private const val KEY_AUTOSTART_CONFIGURED = "pref_autostart_configured"

    fun isXiaomiDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        val fingerprint = Build.FINGERPRINT.lowercase()
        return manufacturer.contains("xiaomi") ||
                manufacturer.contains("redmi") ||
                brand.contains("xiaomi") ||
                brand.contains("poco") ||
                brand.contains("redmi") ||
                fingerprint.contains("xiaomi") ||
                fingerprint.contains("miui")
    }

    /**
     * Checks if MIUI / HyperOS Autostart is allowed.
     * 1. Checks MIUI AppOps (OP_AUTO_START = 10008) via reflection.
     * 2. Checks local user preference fallback if reflection is restricted.
     */
    fun isAutostartEnabled(context: Context): Boolean {
        try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            if (appOps != null) {
                val method = appOps.javaClass.getMethod(
                    "checkOpNoThrow",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    String::class.java
                )
                // MIUI OP_AUTO_START is 10008, OP_BOOT_COMPLETED is 10007
                val opAutoStart = 10008
                val result = method.invoke(appOps, opAutoStart, Process.myUid(), context.packageName) as? Int
                if (result == AppOpsManager.MODE_ALLOWED) {
                    return true
                }
            }
        } catch (_: Exception) { }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_AUTOSTART_CONFIGURED, false)
    }

    fun setAutostartConfigured(context: Context, configured: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_AUTOSTART_CONFIGURED, configured).apply()
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    }

    @SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimizations(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!isIgnoringBatteryOptimizations(context)) {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    return
                }
            }
            openAppDetailsSettings(context)
        } catch (_: Exception) {
            openAppDetailsSettings(context)
        }
    }

    fun openAutostartSettings(context: Context): Boolean {
        setAutostartConfigured(context, true)

        val autostartIntents = listOf(
            Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
            Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartMainActivity")),
            Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")).putExtra("extra_pkgname", context.packageName),
            Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.securityscan.MainActivity"))
        )

        for (intent in autostartIntents) {
            try {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                return true
            } catch (_: Exception) { }
        }

        openAppDetailsSettings(context)
        return false
    }

    fun openMiuiBatterySettings(context: Context): Boolean {
        val batteryIntents = listOf(
            Intent().setComponent(ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"))
                .putExtra("package_name", context.packageName)
                .putExtra("package_label", "Keepr"),
            Intent().setComponent(ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.PowerHideManageActivity")),
            Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.powercenter.PowerSettings"))
        )

        for (intent in batteryIntents) {
            try {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                return true
            } catch (_: Exception) { }
        }

        requestIgnoreBatteryOptimizations(context)
        return false
    }

    fun openAppDetailsSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, context.getString(R.string.toast_open_settings_error), Toast.LENGTH_SHORT).show()
        }
    }
}


