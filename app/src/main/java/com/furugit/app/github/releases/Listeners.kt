package com.furugit.app.github.releases

import android.os.Handler
import android.os.Looper

/** Pengamat perubahan status: dipanggil di thread utama. */
internal class Listeners {
    private val list = ArrayList<() -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    fun add(l: () -> Unit) {
        if (!list.contains(l)) list.add(l)
    }

    fun remove(l: () -> Unit) {
        list.remove(l)
    }

    fun fire() {
        val run = { for (l in ArrayList(list)) l() }
        if (Looper.myLooper() === Looper.getMainLooper()) run() else main.post(run)
    }
}
