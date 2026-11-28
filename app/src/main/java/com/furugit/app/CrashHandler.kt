package com.furugit.app

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.io.File

/**
 * Menangkap crash yang tidak tertangani, menyimpannya ke file, lalu membuka [CrashActivity]
 * (proses terpisah ":crash") supaya pesan error tampil di layar.
 */
object CrashHandler {

    fun isCrashProcess(): Boolean {
        val name = try {
            if (Build.VERSION.SDK_INT >= 28) android.app.Application.getProcessName()
            else File("/proc/self/cmdline").readText().trim('\u0000')
        } catch (_: Throwable) {
            ""
        }
        return name != null && name.endsWith(":crash")
    }

    fun install(app: Context) {
        val crashProcess = isCrashProcess()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val info = StringBuilder()
                info.append("FuruGit crash\n")
                info.append("Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
                info.append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
                info.append("Thread: ").append(t.name).append("\n\n")
                info.append(Log.getStackTraceString(e))
                val text = info.toString()
                try {
                    File(app.filesDir, "crash.txt").writeText(text)
                } catch (_: Throwable) {
                }
                try {
                    val ext = app.getExternalFilesDir(null)
                    if (ext != null) File(ext, "crash.txt").writeText(text)
                } catch (_: Throwable) {
                }
                if (!crashProcess) {
                    val i = Intent(app, CrashActivity::class.java)
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    i.putExtra("trace", if (text.length > 60000) text.substring(0, 60000) else text)
                    app.startActivity(i)
                    Thread.sleep(400)
                }
            } catch (_: Throwable) {
            }
            android.os.Process.killProcess(android.os.Process.myPid())
            System.exit(10)
        }
    }
}
