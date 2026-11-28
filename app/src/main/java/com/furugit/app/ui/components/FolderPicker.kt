package com.furugit.app.ui.components

import android.content.Context
import com.furugit.app.core.common.tx
import java.io.File
import java.io.FileFilter

/** Penjelajah folder sederhana berbasis path asli (bukan SAF), agar bisa dipakai JGit. */
object FolderPicker {
    private const val ROOT = "/storage/emulated/0"

    fun pick(ctx: Context, start: File, title: String, allowCreate: Boolean, onPick: (File) -> Unit) {
        var dir = start
        if (!dir.isDirectory) dir = File(ROOT)
        val here = dir
        val subs = (here.listFiles(FileFilter { it.isDirectory && !it.name.startsWith(".") }) ?: emptyArray())
            .sortedBy { it.name.lowercase() }
        val hasUp = here.parentFile != null && here.absolutePath != "/"
        val items = ArrayList<String>()
        if (hasUp) items.add("⬆  ..")
        for (s in subs) items.add("📁  " + s.name + (if (File(s, ".git").exists()) "   [git]" else ""))

        val buttons = ArrayList<DBtn>()
        if (allowCreate) {
            buttons.add(DBtn(tx("Folder baru")) {
                Dialogs.prompt(ctx, "Folder baru", "Nama folder") { name ->
                    if (name.isNotBlank() && !name.contains('/')) {
                        val f = File(here, name)
                        f.mkdirs()
                        pick(ctx, if (f.isDirectory) f else here, title, allowCreate, onPick)
                    }
                }
            })
        }
        buttons.add(DBtn(tx("Batal")))
        buttons.add(DBtn(tx("Pilih folder ini"), true) { onPick(here) })
        Dialogs.chooseWith(ctx, tx(title) + "\n" + here.absolutePath, items, buttons) { i ->
            if (hasUp && i == 0) pick(ctx, here.parentFile!!, title, allowCreate, onPick)
            else pick(ctx, subs[if (hasUp) i - 1 else i], title, allowCreate, onPick)
        }
    }
}
