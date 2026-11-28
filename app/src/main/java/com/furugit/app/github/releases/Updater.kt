package com.furugit.app.github.releases

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.io.File

/** Info dasar aplikasi / repo. */
object Updater {
    const val REPO = "Fueeru26/FuruGit"
    const val REPO_URL = "https://github.com/Fueeru26/FuruGit"

    fun current(ctx: Context): String = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
    } catch (e: Exception) {
        "?"
    }

    /** Buka installer sistem. Android meminta izin "pasang dari sumber ini" pada pemasangan pertama. */
    fun install(ctx: Context, file: File) {
        if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        ctx.startActivity(UpdateInstaller.installIntent(ctx, file))
    }
}
