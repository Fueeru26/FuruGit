package com.furugit.app.ui.settings

import android.view.View
import com.furugit.app.MainActivity
import com.furugit.app.R
import com.furugit.app.core.common.tr
import com.furugit.app.github.releases.UpdateChecker
import com.furugit.app.ui.components.BaseFragment
import com.furugit.app.ui.components.MenuTile
import com.furugit.app.ui.components.tileGrid

// ───────────────────────── Menu utama Pengaturan ─────────────────────────

class SettingsHomeFragment : BaseFragment() {
    private var updateTile: MenuTile? = null

    /** Titik merah pada kotak Periksa Pembaruan mengikuti ada / tidaknya versi baru. */
    private val upListener: () -> Unit = {
        updateTile?.dot?.visibility = if (UpdateChecker.available) View.VISIBLE else View.GONE
    }

    override fun onStart() {
        super.onStart()
        UpdateChecker.addListener(upListener)
        upListener()
    }

    override fun onStop() {
        UpdateChecker.removeListener(upListener)
        super.onStop()
    }

    override fun refresh() {
        content.removeAllViews()
        val act = requireActivity() as MainActivity
        val upd = MenuTile(R.drawable.ic_download, tr("Periksa Pembaruan", "Check for Updates")) {
            UpdateChecker.check(act)
            UpdateDialogs.showCheck(act)
        }
        updateTile = upd
        content.tileGrid(
            listOf(
                MenuTile(R.drawable.ic_shield, tr("Pengaturan Github", "GitHub Settings")) { act.push(GithubSettingsFragment(), "Pengaturan Github") },
                MenuTile(R.drawable.ic_tune, tr("Pengaturan Aplikasi", "App Settings")) { act.push(AppSettingsFragment(), "Pengaturan Aplikasi") },
                MenuTile(R.drawable.ic_log, tr("Log Aktivitas", "Activity Log")) { act.push(LogFragment(), "Log Aktivitas") },
                MenuTile(R.drawable.ic_guide, tr("Panduan", "Guide")) { act.push(GuideFragment(), "Panduan") },
                upd,
                MenuTile(R.drawable.ic_info, tr("Tentang", "About")) { act.push(AboutFragment(), "Tentang") }
            )
        )
        upListener()
    }
}
