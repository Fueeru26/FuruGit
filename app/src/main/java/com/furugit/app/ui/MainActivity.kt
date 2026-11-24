package com.furugit.app.ui

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.furugit.app.R
import com.furugit.app.data.Prefs
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator

class MainActivity : AppCompatActivity() {

    private var busy = 0
    private lateinit var progress: LinearProgressIndicator
    private lateinit var toolbar: MaterialToolbar
    private lateinit var nav: BottomNavigationView

    private fun accentTheme(): Int = when (Accent.of(Prefs.accent)) {
        Accent.BLUE -> R.style.Theme_FuruGit_Blue
        Accent.LIGHT_BLUE -> R.style.Theme_FuruGit_LightBlue
        Accent.GREEN -> R.style.Theme_FuruGit_Green
        Accent.TEAL -> R.style.Theme_FuruGit_Teal
        Accent.PURPLE -> R.style.Theme_FuruGit_Purple
        Accent.PINK -> R.style.Theme_FuruGit_Pink
        Accent.ORANGE -> R.style.Theme_FuruGit_Orange
        Accent.RED -> R.style.Theme_FuruGit_Red
        Accent.YELLOW -> R.style.Theme_FuruGit_Yellow
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(accentTheme())
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val pal = Pal.of(this)
        val ic = WindowCompat.getInsetsController(window, window.decorView)
        ic.isAppearanceLightStatusBars = !pal.dark
        ic.isAppearanceLightNavigationBars = !pal.dark

        findViewById<View>(R.id.root).setBackgroundColor(pal.background)
        findViewById<View>(R.id.divider).setBackgroundColor(pal.cardBorder)

        toolbar = findViewById(R.id.toolbar)
        toolbar.setBackgroundColor(pal.background)
        toolbar.setTitleTextColor(pal.header)
        toolbar.setNavigationIconTint(pal.text)
        toolbar.inflateMenu(R.menu.main_menu)
        toolbar.menu.findItem(R.id.action_settings).let {
            it.iconTintList = ColorStateList.valueOf(pal.text)
            it.title = tx("Pengaturan")
        }
        toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_settings) {
                if (supportFragmentManager.backStackEntryCount == 0) push(SettingsHomeFragment(), "Pengaturan")
                true
            } else false
        }
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        supportFragmentManager.addOnBackStackChangedListener { updateHeader() }

        progress = findViewById(R.id.progress)
        progress.setIndicatorColor(pal.accent)

        nav = findViewById(R.id.nav)
        nav.setBackgroundColor(pal.card)
        val tint = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(pal.accent, pal.muted)
        )
        nav.itemIconTintList = tint
        nav.itemTextColor = tint
        nav.menu.findItem(R.id.nav_repo).title = tx("Repo")
        nav.menu.findItem(R.id.nav_changes).title = tx("Perubahan")
        nav.menu.findItem(R.id.nav_branches).title = "Branch"
        nav.menu.findItem(R.id.nav_sync).title = "Sync"
        nav.menu.findItem(R.id.nav_history).title = tx("Riwayat")
        nav.setOnItemSelectedListener { item ->
            tabId = item.itemId
            supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
            val f: Fragment = when (item.itemId) {
                R.id.nav_repo -> RepoFragment()
                R.id.nav_changes -> ChangesFragment()
                R.id.nav_branches -> BranchesFragment()
                R.id.nav_sync -> SyncFragment()
                else -> HistoryFragment()
            }
            supportFragmentManager.beginTransaction().replace(R.id.container, f).commit()
            updateHeader()
            true
        }
        if (savedInstanceState == null) nav.selectedItemId = R.id.nav_repo else tabId = nav.selectedItemId
        updateHeader()

        if (savedInstanceState == null && !StorageAccess.hasAccess(this)) askStorage()
    }

    private var tabId = R.id.nav_repo

    private fun navTitle(): String = when (tabId) {
        R.id.nav_repo -> "Repositori"
        R.id.nav_changes -> "Perubahan"
        R.id.nav_branches -> "Branch"
        R.id.nav_sync -> "Sync"
        else -> "Riwayat"
    }

    private fun updateHeader() {
        val n = supportFragmentManager.backStackEntryCount
        if (n > 0) {
            toolbar.title = tx(supportFragmentManager.getBackStackEntryAt(n - 1).name ?: "")
            toolbar.setNavigationIcon(R.drawable.ic_back)
            toolbar.menu.findItem(R.id.action_settings)?.isVisible = false
        } else {
            toolbar.title = tx(navTitle())
            toolbar.navigationIcon = null
            toolbar.menu.findItem(R.id.action_settings)?.isVisible = true
        }
    }

    /** Pindah ke tab lain lewat kode (mis. dari kartu repo aktif ke tab Perubahan). */
    fun openTab(id: Int) {
        nav.selectedItemId = id
    }

    private fun askStorage() {
        MaterialAlertDialogBuilder(this)
            .setTitle(tx("Izin akses file"))
            .setMessage(tx("FuruGit perlu izin \"Akses semua file\" agar bisa membuka dan mengubah folder repository langsung di penyimpanan HP."))
            .setPositiveButton(tx("Beri izin")) { _, _ -> StorageAccess.request(this) }
            .setNegativeButton(tx("Nanti"), null)
            .show()
    }

    fun push(f: Fragment, title: String) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, f)
            .addToBackStack(title)
            .commit()
    }

    fun setBusy(b: Boolean) {
        busy += if (b) 1 else -1
        if (busy < 0) busy = 0
        progress.visibility = if (busy > 0) View.VISIBLE else View.GONE
    }
}
