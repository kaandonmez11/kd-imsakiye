package com.kdgames.imsakiye

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.core.content.ContextCompat

/**
 * Navbar pill'inin gölgesini elevation yerine referanstaki
 * `box-shadow: 0 12px 28px nav_shadow` ile birebir, gerçek blur'la çizer.
 * (Elevation gölgesi outline'a yapışıp köşelerde sert kenar bırakıyordu.)
 */
class NavBarContainer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val density = resources.displayMetrics.density
    private val radius = 26f * density
    private val rect = RectF()

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.nav_bar)
        // Android'in shadowLayer blur'u CSS'e göre daha yoğun bastığından
        // alpha düşürülüp blur genişletilerek yumuşatıldı
        val shadowColor = ContextCompat.getColor(context, R.color.nav_shadow)
        val softened = androidx.core.graphics.ColorUtils.setAlphaComponent(
            shadowColor,
            (android.graphics.Color.alpha(shadowColor) * 0.55f).toInt()
        )
        setShadowLayer(34f * density, 0f, 12f * density, softened)
    }

    init {
        setWillNotDraw(false)
        // setShadowLayer şekiller için yalnızca software layer'da çalışır
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val pill = findViewById<View>(R.id.nav_pill) ?: return
        if (pill.width == 0) return

        rect.set(
            pill.left.toFloat(),
            pill.top.toFloat(),
            pill.right.toFloat(),
            pill.bottom.toFloat()
        )
        canvas.drawRoundRect(rect, radius, radius, shadowPaint)
    }
}
