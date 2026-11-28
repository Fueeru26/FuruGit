package com.furugit.app.ui.components

import android.content.Context
import android.graphics.Matrix
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.max

/** Gambar latar yang diskalakan memenuhi layar (Crop) dan diratakan ke atas-tengah (ContentScale.Crop + TopCenter). */
class TopCropImageView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    AppCompatImageView(context, attrs) {

    init {
        scaleType = ScaleType.MATRIX
    }

    override fun setFrame(l: Int, t: Int, r: Int, b: Int): Boolean {
        val changed = super.setFrame(l, t, r, b)
        val d = drawable
        val vw = (r - l) - paddingLeft - paddingRight
        val vh = (b - t) - paddingTop - paddingBottom
        if (d != null && d.intrinsicWidth > 0 && d.intrinsicHeight > 0 && vw > 0 && vh > 0) {
            val dw = d.intrinsicWidth.toFloat()
            val dh = d.intrinsicHeight.toFloat()
            val s = max(vw / dw, vh / dh)
            val m = Matrix()
            m.setScale(s, s)
            m.postTranslate((vw - dw * s) / 2f, 0f)
            imageMatrix = m
        }
        return changed
    }
}
