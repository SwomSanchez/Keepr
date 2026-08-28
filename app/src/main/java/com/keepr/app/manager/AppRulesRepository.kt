package com.keepr.app.manager

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import java.util.concurrent.ConcurrentHashMap

data class AppItem(
    val name: String,
    val packageName: String,
    val icon: Drawable?,
    var is120HzEnabled: Boolean
)

object AppRulesRepository {
    private const val PREFS_NAME = "keepr_120hz_rules"
    private const val KEY_GLOBAL_MODE = "pref_global_120hz_mode"

    // High-speed O(1) RAM Cache (Zero Disk I/O during app switching)
    private val memoryCache = ConcurrentHashMap.newKeySet<String>()
    @Volatile
    private var isInitialized = false
    @Volatile
    private var globalModeCache: Boolean = true

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun ensureCacheLoaded(context: Context) {
        if (!isInitialized) {
            synchronized(this) {
                if (!isInitialized) {
                    val prefs = getPrefs(context)
                    globalModeCache = prefs.getBoolean(KEY_GLOBAL_MODE, true)
                    val all = prefs.all
                    for ((pkg, value) in all) {
                        if (pkg != KEY_GLOBAL_MODE && value == true) {
                            memoryCache.add(pkg)
                        }
                    }
                    isInitialized = true
                }
            }
        }
    }

    /**
     * O(1) Global 120Hz Mode Check
     */
    fun isGlobalModeEnabled(context: Context): Boolean {
        ensureCacheLoaded(context)
        return globalModeCache
    }

    fun setGlobalModeEnabled(context: Context, enabled: Boolean) {
        ensureCacheLoaded(context)
        globalModeCache = enabled
        getPrefs(context).edit().putBoolean(KEY_GLOBAL_MODE, enabled).apply()
    }

    /**
     * O(1) Nanosecond RAM Lookup
     */
    fun isAppEnabled(context: Context, packageName: String): Boolean {
        ensureCacheLoaded(context)
        return memoryCache.contains(packageName)
    }

    fun setAppEnabled(context: Context, packageName: String, enabled: Boolean) {
        ensureCacheLoaded(context)
        if (enabled) {
            memoryCache.add(packageName)
        } else {
            memoryCache.remove(packageName)
        }
        getPrefs(context).edit().putBoolean(packageName, enabled).apply()
    }

    fun setAllAppsEnabled(context: Context, apps: List<AppItem>, enabled: Boolean) {
        ensureCacheLoaded(context)
        val editor = getPrefs(context).edit()
        for (app in apps) {
            app.is120HzEnabled = enabled
            if (enabled) {
                memoryCache.add(app.packageName)
            } else {
                memoryCache.remove(app.packageName)
            }
            editor.putBoolean(app.packageName, enabled)
        }
        editor.apply()
    }

    fun getInstalledApps(context: Context): List<AppItem> {
        ensureCacheLoaded(context)
        val pm = context.packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val list = mutableListOf<AppItem>()

        for (appInfo in packages) {
            val launchIntent = pm.getLaunchIntentForPackage(appInfo.packageName)
            val isUserApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0

            // Show launchable apps or user-installed apps
            if (launchIntent != null || isUserApp) {
                val isEnabled = isAppEnabled(context, appInfo.packageName)

                try {
                    val name = appInfo.loadLabel(pm).toString()
                    val icon = appInfo.loadIcon(pm)
                    list.add(AppItem(name, appInfo.packageName, icon, isEnabled))
                } catch (_: Exception) { }
            }
        }

        // Sort enabled apps first, then alphabetically
        return list.sortedWith(compareByDescending<AppItem> { it.is120HzEnabled }.thenBy { it.name.lowercase() })
    }
}
