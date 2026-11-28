package com.furugit.app

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.net.ConnectivityManager
import android.net.Network
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.furugit.app.core.common.L
import com.furugit.app.core.common.tr
import com.furugit.app.core.common.tx
import com.furugit.app.core.storage.StorageAccess
import com.furugit.app.data.preferences.RepoStore
import com.furugit.app.github.artifacts.Downloads
import com.furugit.app.github.releases.UpdateChecker
import com.furugit.app.ui.actions.ActionsFragment
import com.furugit.app.ui.actions.RunLogFragment
import com.furugit.app.ui.changes.ChangesFragment
import com.furugit.app.ui.components.BaseFragment
import com.furugit.app.ui.components.DBtn
import com.furugit.app.ui.components.Dialogs
import com.furugit.app.ui.components.EntranceClock
import com.furugit.app.ui.components.Fonts
import com.furugit.app.ui.components.GlassCard
import com.furugit.app.ui.components.GlassNav
import com.furugit.app.ui.components.MATCH
import com.furugit.app.ui.components.PageTransitions
import com.furugit.app.ui.components.Pal
import com.furugit.app.ui.components.Palette
import com.furugit.app.ui.components.TopCropImageView
import com.furugit.app.ui.components.WRAP
import com.furugit.app.ui.components.dp
import com.furugit.app.ui.components.pressScale
import com.furugit.app.ui.components.redDot
import com.furugit.app.ui.history.HistoryFragment
import com.furugit.app.ui.manage.ManageFragment
import com.furugit.app.ui.repository.RepoFragment
import com.furugit.app.ui.settings.SettingsHomeFragment
import com.furugit.app.ui.settings.UpdateDialogs
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.lang.ref.WeakReference
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private class Tab(val id: Int, val icon: Int, val title: String, val label: String)

    private var busy = 0
    private lateinit var root: FrameLayout
    private lateinit var page: LinearLayout
    private lateinit var header: FrameLayout
    private lateinit var progress: LinearProgressIndicator
    private lateinit var nav: GlassNav
    private lateinit var tabHeader: LinearLayout
    private lateinit var tabTitle: TextView
    private lateinit var subHeader: LinearLayout
    private lateinit var subTitle: TextView
    private lateinit var tabs: List<Tab>
    private var tabIndex = 0
    private var imeVisible = false
    private var online = true
    private var storageOk = false
    private val handler = Handler(Looper.getMainLooper())
    private val staleRunnable = Runnable { markOthersDirty(tabFragment(0)) }

    /** Memantau koneksi internet: saat tersambung lagi setelah putus, semua tab dimuat ulang. */
    private val netCb = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            runOnUiThread {
                if (!online) {
                    online = true
                    reloadAll()
                }
            }
        }

        override fun onLost(network: Network) {
            runOnUiThread { online = false }
        }
    }
    private lateinit var backCb: OnBackPressedCallback

    // pembaruan versi (seperti FuruArc): titik merah di ikon gear + jendela pengumuman sekali per versi
    private lateinit var gearDot: View
    private var announceDlg: androidx.appcompat.app.AlertDialog? = null
    private var destroying = false
    private val upListener: () -> Unit = {
        gearDot.visibility = if (UpdateChecker.available) View.VISIBLE else View.GONE
        maybeAnnounce()
    }

    // kepala halaman dalam menyusut saat isi digulir ke bawah (HeaderState di FuruArc)
    private var collapsed = false
    private var shrink = 0f
    private var scrollAcc = 0f
    private var shrinkAnim: ValueAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val pal = Pal.of(this)

        // bilah status dan bilah sistem mengikuti warna latar tema
        window.statusBarColor = pal.background
        window.navigationBarColor = pal.background
        val ic = WindowCompat.getInsetsController(window, window.decorView)
        ic.isAppearanceLightStatusBars = !pal.dark
        ic.isAppearanceLightNavigationBars = !pal.dark

        root = findViewById(R.id.root)
        page = findViewById(R.id.page)
        header = findViewById(R.id.header)
        progress = findViewById(R.id.progress)
        progress.setIndicatorColor(pal.accent)
        progress.trackColor = pal.disabledBg
        // bilah loading digeser ke atas agar pas di tengah antara judul tab dan isi
        progress.translationY = -dp(10).toFloat()

        val bg = findViewById<TopCropImageView>(R.id.bg)
        bg.setImageResource(if (pal.dark) R.drawable.bg_blossom_dark else R.drawable.bg_blossom_light)

        tabs = listOf(
            Tab(R.id.nav_repo, R.drawable.ic_folder, "Repositori", tx("Repositori")),
            Tab(R.id.nav_changes, R.drawable.ic_edit, "Perubahan", tx("Perubahan")),
            Tab(R.id.nav_manage, R.drawable.ic_manage, "Kelola", tr("Kelola", "Manage")),
            Tab(R.id.nav_actions, R.drawable.ic_actions, "Aksi", tr("Aksi", "Actions"))
        )
        buildHeader(pal)

        nav = GlassNav(this, tabs.map { GlassNav.Item(it.icon, it.label) }) { selectTab(it, true) }
        findViewById<FrameLayout>(R.id.navHolder).addView(nav, FrameLayout.LayoutParams(MATCH, WRAP))

        backCb = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                goBack()
            }
        }
        onBackPressedDispatcher.addCallback(this, backCb)
        supportFragmentManager.addOnBackStackChangedListener { updateChrome() }

        if (savedInstanceState == null) {
            selectTab(0, false)
        } else {
            tabIndex = savedInstanceState.getInt("tab", 0).coerceIn(0, tabs.size - 1)
            nav.select(tabIndex, false)
        }
        updateChrome()
        root.viewTreeObserver.addOnGlobalLayoutListener {
            val v = ViewCompat.getRootWindowInsets(root)?.isVisible(WindowInsetsCompat.Type.ime()) == true
            if (v != imeVisible) {
                imeVisible = v
                updateNav()
            }
        }

        val cm = getSystemService(ConnectivityManager::class.java)
        online = cm.activeNetwork != null
        try {
            cm.registerDefaultNetworkCallback(netCb)
        } catch (e: Exception) {
        }
        // daftar repo / repo aktif berubah: tab selain Repo ditandai usang (ditunda sebentar agar perubahan beruntun jadi satu)
        RepoStore.onChanged = {
            handler.removeCallbacks(staleRunnable)
            handler.postDelayed(staleRunnable, 150L)
        }
        storageOk = StorageAccess.hasAccess(this)

        // cek pembaruan senyap saat aplikasi dibuka (paling sering tiap 6 jam)
        UpdateChecker.autoCheck(this)

        if (savedInstanceState == null && !StorageAccess.hasAccess(this)) askStorage()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("tab", tabIndex)
    }

    // ───────────── kepala halaman ─────────────

    private fun buildHeader(pal: Palette) {
        // judul tab: ikon bunga biru + judul 24sp tebal (putih di mode gelap, biru di mode terang) + ikon gear di kanan
        tabHeader = LinearLayout(this)
        tabHeader.orientation = LinearLayout.HORIZONTAL
        tabHeader.gravity = Gravity.CENTER_VERTICAL
        tabHeader.setPadding(dp(16), 0, dp(16), 0)
        val icon = ImageView(this)
        icon.setImageResource(if (pal.dark) R.drawable.blossom_icon_dark else R.drawable.blossom_icon)
        tabHeader.addView(icon, LinearLayout.LayoutParams(dp(28), dp(28)))
        tabTitle = TextView(this)
        tabTitle.textSize = 24f
        tabTitle.typeface = Fonts.bold(this)
        tabTitle.setTextColor(pal.header)
        tabTitle.maxLines = 1
        tabTitle.ellipsize = TextUtils.TruncateAt.END
        tabHeader.addView(tabTitle, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = dp(10) })
        val gear = ImageView(this)
        gear.setImageResource(R.drawable.ic_settings)
        gear.imageTintList = ColorStateList.valueOf(pal.text)
        gear.scaleType = ImageView.ScaleType.CENTER
        val gearBox = FrameLayout(this)
        gearBox.clipChildren = false
        gearBox.contentDescription = tx("Pengaturan")
        gearBox.isClickable = true
        gearBox.setOnClickListener {
            if (supportFragmentManager.backStackEntryCount == 0) push(SettingsHomeFragment(), "Pengaturan")
        }
        gearBox.pressScale(0.9f)
        gearBox.addView(gear, FrameLayout.LayoutParams(MATCH, MATCH))
        // titik merah di pojok kanan atas ikon gear bila ada versi baru
        gearDot = redDot(9)
        (gearDot.layoutParams as FrameLayout.LayoutParams).apply {
            gravity = Gravity.TOP or Gravity.END
            topMargin = dp(10)
            marginEnd = dp(8)
        }
        gearDot.visibility = View.GONE
        gearBox.addView(gearDot)
        // area sentuh 48dp, tetapi ikon 24dp sejajar dengan tepi isi
        tabHeader.addView(gearBox, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = -dp(12) })
        header.addView(tabHeader, FrameLayout.LayoutParams(MATCH, MATCH))

        // judul halaman dalam: tombol kembali + judul 22sp semi-tebal (ScreenHeader)
        subHeader = LinearLayout(this)
        subHeader.orientation = LinearLayout.HORIZONTAL
        subHeader.gravity = Gravity.CENTER_VERTICAL
        subHeader.setPadding(dp(16), 0, dp(16), 0)
        val back = ImageView(this)
        back.setImageResource(R.drawable.ic_back)
        back.imageTintList = ColorStateList.valueOf(pal.text)
        back.scaleType = ImageView.ScaleType.CENTER
        back.contentDescription = tx("Kembali")
        back.isClickable = true
        back.setOnClickListener { goBack() }
        back.pressScale(0.9f)
        subHeader.addView(back, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginStart = -dp(12) })
        subTitle = TextView(this)
        subTitle.textSize = 22f
        subTitle.typeface = Fonts.semibold(this)
        subTitle.setTextColor(pal.text)
        subTitle.maxLines = 2
        subTitle.ellipsize = TextUtils.TruncateAt.END
        subHeader.addView(subTitle, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = dp(16) })
        header.addView(subHeader, FrameLayout.LayoutParams(MATCH, MATCH))
    }

    private fun applyShrink(f: Float) {
        shrink = f
        header.layoutParams.height = dp(64) - (dp(18) * f).toInt()
        header.requestLayout()
        subTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f - 4f * f)
    }

    private fun setCollapsed(c: Boolean) {
        if (collapsed == c) return
        collapsed = c
        if (supportFragmentManager.backStackEntryCount == 0) return
        shrinkAnim?.cancel()
        val a = ValueAnimator.ofFloat(shrink, if (c) 1f else 0f)
        a.duration = 250L
        a.addUpdateListener { applyShrink(it.animatedValue as Float) }
        shrinkAnim = a
        a.start()
    }

    /** Dipanggil halaman saat isi digulir: menggulir ke bawah menyusutkan kepala, ke atas mengembalikannya. */
    fun onPageScroll(dy: Int) {
        val d = -dy.toFloat()
        if (abs(d) < 1f) return
        if ((d < 0f) != (scrollAcc < 0f)) scrollAcc = 0f
        scrollAcc += d
        if (scrollAcc < -60f) setCollapsed(true) else if (scrollAcc > 30f) setCollapsed(false)
    }

    /** Menyesuaikan kepala, bilah navigasi, dan tombol kembali dengan halaman yang tampil. */
    private fun updateChrome() {
        val n = supportFragmentManager.backStackEntryCount
        val sub = n > 0
        tabHeader.visibility = if (sub) View.GONE else View.VISIBLE
        subHeader.visibility = if (sub) View.VISIBLE else View.GONE
        if (sub) subTitle.text = tx(supportFragmentManager.getBackStackEntryAt(n - 1).name ?: "")
        else tabTitle.text = tx(tabs[tabIndex].title)
        updateNav()
        backCb.isEnabled = sub
        shrinkAnim?.cancel()
        collapsed = false
        scrollAcc = 0f
        applyShrink(0f)
    }

    /** Bilah navigasi hanya tampil di halaman tab dan disembunyikan saat keyboard terbuka (agar tidak menutupi isian). */
    private fun updateNav() {
        nav.visibility = if (supportFragmentManager.backStackEntryCount > 0 || imeVisible) View.GONE else View.VISIBLE
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

    /** Versi baru ditemukan oleh pemeriksaan otomatis: jendela pembaruan langsung muncul (sekali per versi). */
    private fun maybeAnnounce() {
        val info = UpdateChecker.announce ?: return
        if (announceDlg != null || isFinishing || isDestroyed) return
        announceDlg = UpdateDialogs.showAnnounce(this, info, onDownload = { UpdateDialogs.showCheck(this) }) {
            announceDlg = null
            if (!destroying) UpdateChecker.announce = null
        }
    }

    override fun onResume() {
        super.onResume()
        com.furugit.app.github.artifacts.Downloads.host = java.lang.ref.WeakReference(this)
        // izin akses file baru diberikan (dari layar pengaturan sistem): muat ulang semua tab
        val ok = StorageAccess.hasAccess(this)
        if (ok && !storageOk) reloadAll()
        storageOk = ok
    }

    override fun onDestroy() {
        destroying = true
        try {
            announceDlg?.dismiss()
        } catch (e: Exception) {
        }
        super.onDestroy()
        try {
            getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(netCb)
        } catch (e: Exception) {
        }
        RepoStore.onChanged = null
        handler.removeCallbacks(staleRunnable)
    }

    override fun onPause() {
        super.onPause()
        if (com.furugit.app.github.artifacts.Downloads.host?.get() === this) com.furugit.app.github.artifacts.Downloads.host = null
    }

    // ───────────── navigasi ─────────────

    private fun newTab(i: Int): Fragment = when (tabs[i].id) {
        R.id.nav_repo -> RepoFragment()
        R.id.nav_changes -> ChangesFragment()
        R.id.nav_manage -> ManageFragment()
        else -> ActionsFragment()
    }

    private fun tabTag(i: Int) = "tab$i"

    private fun tabFragment(i: Int): Fragment? = supportFragmentManager.findFragmentByTag(tabTag(i))

    /** Fragmen yang sedang tampil (tab aktif atau halaman dalam teratas). */
    private fun visibleFragment(): Fragment? =
        supportFragmentManager.fragments.lastOrNull { it.isAdded && !it.isHidden }

    /**
     * Keempat tab dibuat sekali dan dipertahankan (hanya ditampilkan / disembunyikan), sehingga berpindah tab tidak
     * memuat ulang. Tab dimuat saat aplikasi dibuka, lalu hanya ketika ditandai usang atau internet tersambung lagi.
     */
    private fun selectTab(index: Int, animate: Boolean) {
        val old = tabIndex
        if (animate && index == old && supportFragmentManager.backStackEntryCount == 0) {
            // ketuk ulang tab yang sedang aktif = muat ulang
            (tabFragment(index) as? BaseFragment)?.reloadNow()
            return
        }
        val swap = {
            val fm = supportFragmentManager
            if (fm.backStackEntryCount > 0) fm.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
            tabIndex = index
            val existing = tabFragment(index)
            val tx = fm.beginTransaction()
            for (i in tabs.indices) {
                val f = tabFragment(i)
                if (f == null) {
                    val nf = newTab(i)
                    tx.add(R.id.container, nf, tabTag(i))
                    if (i != index) tx.hide(nf)
                } else if (i == index) {
                    tx.show(f)
                } else {
                    tx.hide(f)
                }
            }
            tx.commit()
            if (animate) fm.executePendingTransactions()
            nav.select(index, animate)
            updateChrome()
            // tab yang sudah termuat: kartunya muncul bertahap lagi seperti pada pindah layar FuruArc
            if (animate && existing != null) existing.view?.let { replayEntrance(it) }
        }
        if (animate) {
            EntranceClock.restart()
            PageTransitions.run(root, page, PageTransitions.TAB, if (index > old) 1 else -1, swap)
        } else swap()
    }

    private fun replayEntrance(v: View) {
        if (v is GlassCard) {
            v.replayEntrance()
        } else if (v is ViewGroup) {
            for (i in 0 until v.childCount) replayEntrance(v.getChildAt(i))
        }
    }

    /** Menandai semua tab (selain [except]) usang: yang tampil dimuat ulang, yang tersembunyi dimuat saat ditampilkan. */
    fun markOthersDirty(except: Fragment?) {
        for (f in supportFragmentManager.fragments) {
            if (f is BaseFragment && f !== except) f.markStale()
        }
    }

    /** Memuat ulang semua tab sekarang juga (internet tersambung lagi, izin akses file baru diberikan). */
    private fun reloadAll() {
        for (f in supportFragmentManager.fragments) {
            if (f is BaseFragment) f.reloadNow()
        }
    }

    /** Pindah ke tab lain lewat kode (mis. dari kartu repo aktif ke tab Perubahan). */
    fun openTab(id: Int) {
        val i = tabs.indexOfFirst { it.id == id }
        if (i >= 0) selectTab(i, true)
    }

    /** Membuka halaman dalam (mis. Pengaturan): naik dari bawah. Halaman di bawahnya hanya disembunyikan. */
    fun push(f: Fragment, title: String) {
        EntranceClock.restart()
        PageTransitions.run(root, page, PageTransitions.PUSH, 0) {
            val fm = supportFragmentManager
            val cur = visibleFragment()
            val tx = fm.beginTransaction()
            if (cur != null) tx.hide(cur)
            tx.add(R.id.container, f).addToBackStack(title).commit()
            fm.executePendingTransactions()
            updateChrome()
        }
    }

    /** Kembali ke halaman sebelumnya: halaman atas turun ke bawah. */
    private fun goBack() {
        if (supportFragmentManager.backStackEntryCount == 0) return
        EntranceClock.restart()
        // setelah kembali dari Pengaturan dll. isi tab dimuat ulang; dari log / riwayat commit tidak perlu
        val leaving = visibleFragment()
        val refreshAfter = leaving !is HistoryFragment && leaving !is RunLogFragment
        PageTransitions.run(root, page, PageTransitions.POP, 0) {
            supportFragmentManager.popBackStackImmediate()
            updateChrome()
            if (refreshAfter) markOthersDirty(null)
        }
    }

    private fun dp(v: Int) = this.dp_(v)

    private fun askStorage() {
        Dialogs.show(
            this,
            tx("Izin akses file"),
            tx("FuruGit perlu izin \"Akses semua file\" agar bisa membuka dan mengubah folder repository langsung di penyimpanan HP."),
            null,
            listOf(
                DBtn(tx("Nanti")),
                DBtn(tx("Beri izin"), true) { StorageAccess.request(this) }
            )
        )
    }

    fun setBusy(b: Boolean) {
        busy += if (b) 1 else -1
        if (busy < 0) busy = 0
        progress.visibility = if (busy > 0) View.VISIBLE else View.INVISIBLE
    }
}

private fun android.content.Context.dp_(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
