package com.keepr.app

import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.keepr.app.manager.AppRulesRepository
import com.keepr.app.manager.RefreshRateManager
import com.keepr.app.service.KeeprAccessibilityService
import com.keepr.app.service.KeeprForegroundService
import com.keepr.app.ui.AppsAdapter
import com.keepr.app.utils.XiaomiHelper
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var layoutTabApps: LinearLayout
    private lateinit var layoutTabPerms: View
    private lateinit var btnNavApps: LinearLayout
    private lateinit var btnNavPerms: LinearLayout
    private lateinit var iconNavApps: ImageView
    private lateinit var iconNavPerms: ImageView
    private lateinit var tvNavApps: TextView
    private lateinit var tvNavPerms: TextView

    private lateinit var etSearchApps: EditText
    private lateinit var btnClearSearch: ImageView
    private lateinit var btnFilterModal: ImageView
    private lateinit var tvAppCount: TextView
    private lateinit var tvFilterStatus: TextView
    private lateinit var rvApps: RecyclerView
    private lateinit var pbLoadingApps: ProgressBar
    private var appsAdapter: AppsAdapter? = null

    // Global Mode UI
    private lateinit var cardGlobalMode: LinearLayout
    private lateinit var switchGlobalMode: androidx.appcompat.widget.SwitchCompat
    private lateinit var layoutAppsSection: LinearLayout

    private var isOnlyActiveFilter = false

    // Permission status icon views
    private lateinit var ivAutostartStatus: ImageView
    private lateinit var ivA11yStatus: ImageView
    private lateinit var ivWriteSettingsStatus: ImageView

    // Language setting views
    private lateinit var rowAppLanguage: View
    private lateinit var tvSelectedLanguage: TextView

    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        try {
            KeeprForegroundService.start(this)
        } catch (_: Exception) { }

        initViews()
        setupBottomNav()
        setupGlobalModeToggle()
        setupSearchAndFilter()
        setupLanguageSection()
        setupPermissionClicks()
        loadInstalledApps()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatusIcons()
        updateCurrentLanguageSubtitle()
    }

    private fun initViews() {
        layoutTabApps = findViewById(R.id.layoutTabApps)
        layoutTabPerms = findViewById(R.id.layoutTabPerms)
        btnNavApps = findViewById(R.id.btnNavApps)
        btnNavPerms = findViewById(R.id.btnNavPerms)
        iconNavApps = findViewById(R.id.iconNavApps)
        iconNavPerms = findViewById(R.id.iconNavPerms)
        tvNavApps = findViewById(R.id.tvNavApps)
        tvNavPerms = findViewById(R.id.tvNavPerms)

        cardGlobalMode = findViewById(R.id.cardGlobalMode)
        switchGlobalMode = findViewById(R.id.switchGlobalMode)
        layoutAppsSection = findViewById(R.id.layoutAppsSection)

        etSearchApps = findViewById(R.id.etSearchApps)
        btnClearSearch = findViewById(R.id.btnClearSearch)
        btnFilterModal = findViewById(R.id.btnFilterModal)
        tvAppCount = findViewById(R.id.tvAppCount)
        tvFilterStatus = findViewById(R.id.tvFilterStatus)
        rvApps = findViewById(R.id.rvApps)
        pbLoadingApps = findViewById(R.id.pbLoadingApps)

        ivAutostartStatus = findViewById(R.id.ivAutostartStatus)
        ivA11yStatus = findViewById(R.id.ivA11yStatus)
        ivWriteSettingsStatus = findViewById(R.id.ivWriteSettingsStatus)

        rowAppLanguage = findViewById(R.id.rowAppLanguage)
        tvSelectedLanguage = findViewById(R.id.tvSelectedLanguage)

        rvApps.layoutManager = LinearLayoutManager(this)
        rvApps.setHasFixedSize(true)
        rvApps.setItemViewCacheSize(30)
    }

    private fun setupGlobalModeToggle() {
        val isGlobal = AppRulesRepository.isGlobalModeEnabled(this)
        switchGlobalMode.isChecked = isGlobal
        updateAppsSectionState(isGlobal)

        switchGlobalMode.setOnCheckedChangeListener { _, isChecked ->
            AppRulesRepository.setGlobalModeEnabled(this, isChecked)
            if (isChecked) {
                // Global mod açıldığında daha önce açık kalmış olan tüm uygulama anahtarlarını kapat / sıfırla
                val apps = appsAdapter?.getAppsList() ?: emptyList()
                AppRulesRepository.setAllAppsEnabled(this, apps, false)
                appsAdapter?.setAllEnabled(false)
                RefreshRateManager.force120Hz(this)
            }
            updateAppsSectionState(isChecked)
        }

        cardGlobalMode.setOnClickListener {
            switchGlobalMode.toggle()
        }
    }

    private fun updateAppsSectionState(isGlobal: Boolean) {
        layoutAppsSection.animate()
            .alpha(if (isGlobal) 0.35f else 1.0f)
            .setDuration(200)
            .start()

        etSearchApps.isEnabled = !isGlobal
        btnFilterModal.isEnabled = !isGlobal
        appsAdapter?.setInteractive(!isGlobal)
    }

    private fun setupBottomNav() {
        btnNavApps.setOnClickListener { switchTab(isApps = true) }
        btnNavPerms.setOnClickListener { switchTab(isApps = false) }
    }

    private fun switchTab(isApps: Boolean) {
        val colorActive = ContextCompat.getColor(this, R.color.text_primary)
        val colorMuted = ContextCompat.getColor(this, R.color.text_muted)

        if (isApps) {
            layoutTabApps.visibility = View.VISIBLE
            layoutTabPerms.visibility = View.GONE
            btnNavApps.setBackgroundResource(R.drawable.bg_nav_item_selected)
            btnNavPerms.background = null
            iconNavApps.setColorFilter(colorActive)
            tvNavApps.setTextColor(colorActive)
            iconNavPerms.setColorFilter(colorMuted)
            tvNavPerms.setTextColor(colorMuted)
        } else {
            layoutTabApps.visibility = View.GONE
            layoutTabPerms.visibility = View.VISIBLE
            btnNavApps.background = null
            btnNavPerms.setBackgroundResource(R.drawable.bg_nav_item_selected)
            iconNavApps.setColorFilter(colorMuted)
            tvNavApps.setTextColor(colorMuted)
            iconNavPerms.setColorFilter(colorActive)
            tvNavPerms.setTextColor(colorActive)
            updatePermissionStatusIcons()
        }
    }

    private val searchHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var searchRunnable: Runnable? = null

    private fun setupSearchAndFilter() {
        etSearchApps.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                btnClearSearch.visibility = if (!s.isNullOrEmpty()) View.VISIBLE else View.GONE
                searchRunnable?.let { searchHandler.removeCallbacks(it) }
                val query = s?.toString() ?: ""
                val runnable = Runnable {
                    appsAdapter?.setFilterOptions(query = query)
                    updateCountHeader()
                }
                searchRunnable = runnable
                searchHandler.postDelayed(runnable, 150)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnClearSearch.setOnClickListener {
            etSearchApps.text.clear()
        }

        btnFilterModal.setOnClickListener {
            showFilterBottomSheet()
        }
    }

    private fun showFilterBottomSheet() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_filter_bottom_sheet, null)
        dialog.setContentView(view)

        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.background = null
        }

        val checkAll = view.findViewById<TextView>(R.id.checkFilterAll)
        val checkActive = view.findViewById<TextView>(R.id.checkFilterActive)
        val optAll = view.findViewById<View>(R.id.optFilterAll)
        val optActive = view.findViewById<View>(R.id.optFilterActive)

        checkAll.visibility = if (!isOnlyActiveFilter) View.VISIBLE else View.INVISIBLE
        checkActive.visibility = if (isOnlyActiveFilter) View.VISIBLE else View.INVISIBLE

        optAll.setOnClickListener {
            isOnlyActiveFilter = false
            tvFilterStatus.text = getString(R.string.filter_all)
            btnFilterModal.setColorFilter(ContextCompat.getColor(this, R.color.text_secondary))
            appsAdapter?.setFilterOptions(onlyActive = false)
            updateCountHeader()
            dialog.dismiss()
        }

        optActive.setOnClickListener {
            isOnlyActiveFilter = true
            tvFilterStatus.text = getString(R.string.filter_active)
            btnFilterModal.setColorFilter(ContextCompat.getColor(this, R.color.text_primary))
            appsAdapter?.setFilterOptions(onlyActive = true)
            updateCountHeader()
            dialog.dismiss()
        }

        val btnSelectAll = view.findViewById<View>(R.id.btnSelectAllApps)
        val btnDeselectAll = view.findViewById<View>(R.id.btnDeselectAllApps)

        btnSelectAll.setOnClickListener {
            val apps = appsAdapter?.getAppsList() ?: emptyList()
            AppRulesRepository.setAllAppsEnabled(this, apps, true)
            appsAdapter?.setAllEnabled(true)
            updateCountHeader()
            dialog.dismiss()
        }

        btnDeselectAll.setOnClickListener {
            val apps = appsAdapter?.getAppsList() ?: emptyList()
            AppRulesRepository.setAllAppsEnabled(this, apps, false)
            appsAdapter?.setAllEnabled(false)
            updateCountHeader()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun updateCountHeader() {
        val count = appsAdapter?.itemCount ?: 0
        tvAppCount.text = getString(R.string.app_count_format, count)
    }

    private fun loadInstalledApps() {
        pbLoadingApps.visibility = View.VISIBLE
        executor.execute {
            val apps = AppRulesRepository.getInstalledApps(this)
            runOnUiThread {
                pbLoadingApps.visibility = View.GONE
                tvAppCount.text = getString(R.string.app_count_format, apps.size)
                val isGlobal = AppRulesRepository.isGlobalModeEnabled(this)
                appsAdapter = AppsAdapter(apps, isInteractive = !isGlobal) { item, isChecked ->
                    AppRulesRepository.setAppEnabled(this, item.packageName, isChecked)
                    updateCountHeader()
                }
                rvApps.adapter = appsAdapter
            }
        }
    }

    private fun setupLanguageSection() {
        updateCurrentLanguageSubtitle()
        rowAppLanguage.setOnClickListener {
            showLanguageBottomSheet()
        }
    }

    private fun updateCurrentLanguageSubtitle() {
        val appLocales = AppCompatDelegate.getApplicationLocales()
        if (appLocales.isEmpty) {
            tvSelectedLanguage.text = getString(R.string.lang_system_default)
        } else {
            val primaryTag = appLocales.get(0)?.language?.lowercase() ?: ""
            if (primaryTag.startsWith("tr")) {
                tvSelectedLanguage.text = getString(R.string.lang_turkish)
            } else {
                tvSelectedLanguage.text = getString(R.string.lang_english)
            }
        }
    }

    private fun showLanguageBottomSheet() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_language_bottom_sheet, null)
        dialog.setContentView(view)

        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.background = null
        }

        val checkEN = view.findViewById<View>(R.id.checkLangEN)
        val checkTR = view.findViewById<View>(R.id.checkLangTR)
        val checkSystem = view.findViewById<View>(R.id.checkLangSystem)

        val appLocales = AppCompatDelegate.getApplicationLocales()
        val currentTag = if (appLocales.isEmpty) "" else (appLocales.get(0)?.language?.lowercase() ?: "")

        checkEN.visibility = if (currentTag.startsWith("en")) View.VISIBLE else View.GONE
        checkTR.visibility = if (currentTag.startsWith("tr")) View.VISIBLE else View.GONE
        checkSystem.visibility = if (currentTag.isEmpty()) View.VISIBLE else View.GONE

        view.findViewById<View>(R.id.optLangEN).setOnClickListener {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
            dialog.dismiss()
        }

        view.findViewById<View>(R.id.optLangTR).setOnClickListener {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("tr"))
            dialog.dismiss()
        }

        view.findViewById<View>(R.id.optLangSystem).setOnClickListener {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun setupPermissionClicks() {
        findViewById<View>(R.id.rowAutostart).setOnClickListener {
            XiaomiHelper.openAutostartSettings(this)
        }

        findViewById<View>(R.id.rowAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<View>(R.id.rowWriteSettings).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!Settings.System.canWrite(this)) {
                    val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                }
            }
        }
    }

    private fun updatePermissionStatusIcons() {
        val colorActive = ContextCompat.getColor(this, R.color.status_active)
        val colorMuted = ContextCompat.getColor(this, R.color.text_muted)

        // Autostart
        val isAutostart = XiaomiHelper.isAutostartEnabled(this)
        ivAutostartStatus.setImageResource(if (isAutostart) R.drawable.ic_check_small else R.drawable.ic_chevron_right)
        ivAutostartStatus.imageTintList = ColorStateList.valueOf(if (isAutostart) colorActive else colorMuted)

        // Accessibility
        val isA11y = KeeprAccessibilityService.isRunning()
        ivA11yStatus.setImageResource(if (isA11y) R.drawable.ic_check_small else R.drawable.ic_chevron_right)
        ivA11yStatus.imageTintList = ColorStateList.valueOf(if (isA11y) colorActive else colorMuted)

        // Write Settings
        val canWrite = RefreshRateManager.hasPermission(this)
        ivWriteSettingsStatus.setImageResource(if (canWrite) R.drawable.ic_check_small else R.drawable.ic_chevron_right)
        ivWriteSettingsStatus.imageTintList = ColorStateList.valueOf(if (canWrite) colorActive else colorMuted)
    }

    override fun onDestroy() {
        super.onDestroy()
        searchRunnable?.let { searchHandler.removeCallbacks(it) }
    }
}
