package com.furugit.app

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Layar sederhana (tanpa AppCompat/Material) yang menampilkan error crash. Jalan di proses ":crash". */
class CrashActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val trace = intent.getStringExtra("trace") ?: "(tidak ada data crash)"
        val d = resources.displayMetrics.density
        val pad = (16 * d).toInt()

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(pad, pad * 2, pad, pad)

        val title = TextView(this)
        title.text = "FuruGit berhenti karena error"
        title.textSize = 18f
        title.setTypeface(null, Typeface.BOLD)
        root.addView(title, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val body = TextView(this)
        body.text = trace
        body.textSize = 11f
        body.typeface = Typeface.MONOSPACE
        body.setTextIsSelectable(true)
        val sv = ScrollView(this)
        sv.addView(body)
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        lp.topMargin = pad / 2
        root.addView(sv, lp)

        val copy = Button(this)
        copy.text = "Salin error"
        copy.setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("FuruGit crash", trace))
            Toast.makeText(this, "Tersalin", Toast.LENGTH_SHORT).show()
        }
        root.addView(copy, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val close = Button(this)
        close.text = "Tutup"
        close.setOnClickListener { finishAndRemoveTask() }
        root.addView(close, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        setContentView(root)
    }
}
