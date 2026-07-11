package com.kdgames.imsakiye

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

/**
 * Referanstaki `box-shadow: 0 0 10px 1.5px accent` glow'unu birebir vermek için
 * dolu kısmı gerçek blur'lu (setShadowLayer) çizen 4dp'lik progress bar.
 */
class GlowProgressBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var progress: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private val barHeight = 4f * density
    private val radius = 2f * density

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.progress_track)
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.accent)
        setShadowLayer(
            10f * density,
            0f,
            0f,
            ContextCompat.getColor(context, R.color.accent_glow)
        )
    }

    private val rect = RectF()

    init {
        // setShadowLayer şekiller için yalnızca software layer'da çalışır
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val top = (height - barHeight) / 2f
        val bottom = top + barHeight

        rect.set(0f, top, width.toFloat(), bottom)
        canvas.drawRoundRect(rect, radius, radius, trackPaint)

        if (progress > 0f) {
            rect.set(0f, top, width * progress, bottom)
            canvas.drawRoundRect(rect, radius, radius, fillPaint)
        }
    }
}
