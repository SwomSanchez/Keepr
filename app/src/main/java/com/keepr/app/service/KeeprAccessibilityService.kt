package com.keepr.app.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.keepr.app.manager.AppRulesRepository
import com.keepr.app.manager.RefreshRateManager

class KeeprAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "KeeprA11yService"
        var instance: KeeprAccessibilityService? = null
            private set

        fun isRunning(): Boolean = instance != null
    }

    @Volatile
    private var lastForegroundPackage: String? = null
    @Volatile
    private var isScreenInteractive: Boolean = true

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * ⚡ [SUB-MILLISECOND REACTIVE OBSERVER]
     * Fires immediately whenever MIUI/HyperOS system_server attempts to overwrite
     * miui_refresh_rate = 60 behind the scenes (e.g. video playback in YouTube/Maps).
     */
    private val refreshRateObserver = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            if (!isScreenInteractive) return

            val isGlobal = AppRulesRepository.isGlobalModeEnabled(this@KeeprAccessibilityService)
            val shouldEnforce = isGlobal || (lastForegroundPackage?.let { AppRulesRepository.isAppEnabled(this@KeeprAccessibilityService, it) } == true)

            if (shouldEnforce) {
                try {
                    val currentHz = Settings.Secure.getInt(contentResolver, RefreshRateManager.MIUI_REFRESH_RATE, 120)
                    if (currentHz != RefreshRateManager.TARGET_HZ) {
                        Log.d(TAG, "⚡ [MIUI 60Hz ATTEMPT DETECTED] (Global=$isGlobal, Pkg=$lastForegroundPackage, val=$currentHz) -> Forcing 120Hz immediately!")
                        RefreshRateManager.force120Hz(this@KeeprAccessibilityService)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in refreshRateObserver: ${e.message}")
                }
            }
        }
    }

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenInteractive = false
                    Log.d(TAG, "Screen OFF: Keepr entered zero-power sleeping state.")
                }
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    isScreenInteractive = true
                    Log.d(TAG, "Screen ON: Keepr active.")
                    
                    // Immediately re-evaluate active window or force global 120Hz on screen unlock
                    if (AppRulesRepository.isGlobalModeEnabled(this@KeeprAccessibilityService)) {
                        RefreshRateManager.force120Hz(this@KeeprAccessibilityService)
                    } else {
                        try {
                            val currentPkg = rootInActiveWindow?.packageName?.toString()
                            if (currentPkg != null) {
                                lastForegroundPackage = currentPkg
                                handleAppSwitch(currentPkg)
                            }
                        } catch (_: Exception) { }
                    }
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this

        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        isScreenInteractive = powerManager?.isInteractive ?: true

        // 1. Register Screen On/Off Receiver
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenStateReceiver, filter)

        // 2. Register Sub-Millisecond ContentObserver on miui_refresh_rate
        try {
            contentResolver.registerContentObserver(
                RefreshRateManager.REFRESH_RATE_URI,
                false,
                refreshRateObserver
            )
            Log.i(TAG, "⚡ High-Speed ContentObserver registered on ${RefreshRateManager.MIUI_REFRESH_RATE}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register ContentObserver: ${e.message}")
        }

        // Enforce 120Hz if global mode is active on startup
        if (AppRulesRepository.isGlobalModeEnabled(this)) {
            RefreshRateManager.force120Hz(this)
        }

        Log.i(TAG, "Keepr Accessibility Service fully connected & armed.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isScreenInteractive || event == null) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val pkgName = event.packageName?.toString() ?: return
                if (pkgName != lastForegroundPackage) {
                    lastForegroundPackage = pkgName
                    handleAppSwitch(pkgName)
                }
            }
        }
    }

    private fun handleAppSwitch(packageName: String) {
        val isGlobal = AppRulesRepository.isGlobalModeEnabled(this)
        if (isGlobal || AppRulesRepository.isAppEnabled(this, packageName)) {
            Log.d(TAG, "Foreground App: $packageName (Global=$isGlobal) -> Enforcing 120Hz")
            RefreshRateManager.force120Hz(this)
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Keepr Accessibility Service Interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(screenStateReceiver)
        } catch (_: Exception) { }

        try {
            contentResolver.unregisterContentObserver(refreshRateObserver)
        } catch (_: Exception) { }

        instance = null
        Log.i(TAG, "Keepr Accessibility Service Destroyed")
    }
}

