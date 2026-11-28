package com.furugit.app.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.furugit.app.core.common.ActivityLog
import com.furugit.app.core.common.tx
import com.furugit.app.ui.components.BaseFragment
import com.furugit.app.ui.components.Dialogs
import com.furugit.app.ui.components.button
import com.furugit.app.ui.components.hint
import com.furugit.app.ui.components.hrow
import com.furugit.app.ui.components.label
import com.furugit.app.ui.components.space

class LogFragment : BaseFragment() {
    override fun refresh() {
        content.removeAllViews()
        content.hrow {
            button("Salin log", true) {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("log", ActivityLog.all().joinToString("\n")))
                Dialogs.toast(ctx, "Log disalin")
            }
            button("Bersihkan", true) {
                sure("Bersihkan log?", "Semua catatan aktivitas dihapus.", "Bersihkan") {
                    ActivityLog.clear()
                    refresh()
                }
            }
        }
        content.space(8)
        val lines = ActivityLog.all()
        if (lines.isEmpty()) content.hint("Belum ada aktivitas.")
        else content.label(lines.reversed().joinToString("\n") { tx(it) }, 12f, mono = true)
    }
}
