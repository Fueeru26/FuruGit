package com.furugit.app.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileFilter

object StorageAccess {
    fun hasAccess(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED

    fun request(act: Activity) {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                act.startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + act.packageName))
                )
            } catch (e: Exception) {
                act.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            ActivityCompat.requestPermissions(
                act,
                arrayOf(
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                ),
                1
            )
        }
    }
}

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
