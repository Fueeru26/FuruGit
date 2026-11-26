package com.furugit.app.ui

import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.furugit.app.R
import com.google.android.material.progressindicator.LinearProgressIndicator
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
    private lateinit var backCb: OnBackPressedCallback

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
            Tab(R.id.nav_repo, R.drawable.ic_folder, "Repositori", tx("Repo")),
            Tab(R.id.nav_changes, R.drawable.ic_edit, "Perubahan", tx("Perubahan")),
            Tab(R.id.nav_branches, R.drawable.ic_branch, "Branch", "Branch"),
            Tab(R.id.nav_sync, R.drawable.ic_sync, "Sync", "Sync"),
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
        gear.contentDescription = tx("Pengaturan")
        gear.isClickable = true
        gear.setOnClickListener {
            if (supportFragmentManager.backStackEntryCount == 0) push(SettingsHomeFragment(), "Pengaturan")
        }
        gear.pressScale(0.9f)
        // area sentuh 48dp, tetapi ikon 24dp sejajar dengan tepi isi
        tabHeader.addView(gear, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = -dp(12) })
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
        nav.visibility = if (sub) View.GONE else View.VISIBLE
        backCb.isEnabled = sub
        shrinkAnim?.cancel()
        collapsed = false
        scrollAcc = 0f
        applyShrink(0f)
    }

    // ───────────── navigasi ─────────────

    private fun newTab(i: Int): Fragment = when (tabs[i].id) {
        R.id.nav_repo -> RepoFragment()
        R.id.nav_changes -> ChangesFragment()
        R.id.nav_branches -> BranchesFragment()
        R.id.nav_sync -> SyncFragment()
        else -> ActionsFragment()
    }

    private fun selectTab(index: Int, animate: Boolean) {
        val old = tabIndex
        if (animate && index == old && supportFragmentManager.backStackEntryCount == 0) return
        val swap = {
            if (supportFragmentManager.backStackEntryCount > 0) {
                supportFragmentManager.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
            }
            tabIndex = index
            supportFragmentManager.beginTransaction().replace(R.id.container, newTab(index)).commit()
            if (animate) supportFragmentManager.executePendingTransactions()
            nav.select(index, animate)
            updateChrome()
        }
        if (animate) {
            EntranceClock.restart()
            PageTransitions.run(root, page, PageTransitions.TAB, if (index > old) 1 else -1, swap)
        } else swap()
    }

    /** Pindah ke tab lain lewat kode (mis. dari kartu repo aktif ke tab Perubahan). */
    fun openTab(id: Int) {
        val i = tabs.indexOfFirst { it.id == id }
        if (i >= 0) selectTab(i, true)
    }

    /** Membuka halaman dalam (mis. Pengaturan): naik dari bawah. */
    fun push(f: Fragment, title: String) {
        EntranceClock.restart()
        PageTransitions.run(root, page, PageTransitions.PUSH, 0) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.container, f)
                .addToBackStack(title)
                .commit()
            supportFragmentManager.executePendingTransactions()
            updateChrome()
        }
    }

    /** Kembali ke halaman sebelumnya: halaman atas turun ke bawah. */
    private fun goBack() {
        if (supportFragmentManager.backStackEntryCount == 0) return
        EntranceClock.restart()
        PageTransitions.run(root, page, PageTransitions.POP, 0) {
            supportFragmentManager.popBackStackImmediate()
            updateChrome()
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
