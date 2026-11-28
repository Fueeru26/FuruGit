package com.furugit.app.ui.manage

import androidx.fragment.app.Fragment
import com.furugit.app.MainActivity
import com.furugit.app.R
import com.furugit.app.ui.branch.BranchesFragment
import com.furugit.app.ui.components.BaseFragment
import com.furugit.app.ui.components.rowCard
import com.furugit.app.ui.sync.SyncFragment

/**
 * Tab Kelola: daftar menu (judul + keterangan, seperti MenuCard di FuruArc). Tiap menu membuka halaman dalam.
 * Menu baru ke depannya (mis. PR Manager, File Explorer) cukup ditambahkan ke [menus].
 */
class ManageFragment : BaseFragment() {
    override val tabPage: Boolean get() = true

    private class Menu(val icon: Int, val title: String, val sub: String, val open: () -> androidx.fragment.app.Fragment)

    private val menus: List<Menu> = listOf(
        Menu(
            R.drawable.ic_branch,
            "Branch",
            "Lihat, buat, pindah, merge, dan hapus branch, serta simpan perubahan sementara dengan Stash."
        ) { BranchesFragment() },
        Menu(
            R.drawable.ic_sync,
            "Sinkronisasi",
            "Fetch, Pull, dan Push dengan remote, serta kelola daftar remote."
        ) { SyncFragment() }
    )

    override fun refresh() {
        content.removeAllViews()
        val act = requireActivity() as MainActivity
        for (m in menus) {
            content.rowCard(m.icon, m.title, m.sub, onClick = { act.push(m.open(), m.title) })
        }
    }
}
