// TourOverlayView — TapTargetView'i değiştiren custom overlay.
//
// PROBLEM (eski tasarım): TapTargetView body metnini target view'ın karşı
// tarafına yerleştirir. UniScheduler'da bu metin asıl gösterilmesi gereken
// içeriği (form alanları, accordion içeriği) kapatıyordu.
//
// YENİ TASARIM:
//   • Custom View dim + cutout: tüm ekranı yarı şeffaf siyahla boyar,
//     target view'ın bounds'una rounded rect cutout açar (PorterDuff.CLEAR)
//   • Tap forwarding: cutout içine düşen tap target'a iletilir (gerçek
//     button click). Cutout dışına düşen tap consume edilir (tour bozulmaz).
//   • Title + body alta sabit bottom card'da, bu overlay'in DIŞINDA.
//
// Bu sayede form alanları + spotlight + metin birbirini örtmez.
package com.unischeduler.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class TourOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density

    private val dimPaint = Paint().apply {
        // Alpha 0.55 — arkadaki içerik hafif görünür kalır
        color = Color.argb((0.55f * 255).toInt(), 0, 0, 0)
        isAntiAlias = true
    }

    private val clearPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        isAntiAlias = true
    }

    private val ringPaint = Paint().apply {
        color = Color.parseColor("#FF1976D2") // Material Blue 700
        style = Paint.Style.STROKE
        strokeWidth = 4f * density
        isAntiAlias = true
    }

    private val cornerRadius = 12f * density
    private val cutoutPadding = 8f * density

    private var cutoutRect: RectF? = null
    private var targetView: View? = null
    /** false ise cutout içine düşen tap target'a iletilmez, consume edilir. */
    private var forwardTaps: Boolean = true
    /** Cutout içine ACTION_UP geldiğinde tetiklenir (hem forward hem consume
     *  modunda). Tour mock-add senaryosunda kullanılır: kullanıcı butona
     *  bastı = mock veri ekle + advance. */
    var onCutoutTapped: (() -> Unit)? = null

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        isClickable = true
        isFocusable = true
    }

    fun setTarget(view: View?, forwardTaps: Boolean = true) {
        targetView = view
        this.forwardTaps = forwardTaps
        updateCutoutRect()
        invalidate()
    }

    fun refreshCutout() {
        updateCutoutRect()
        invalidate()
    }

    private fun updateCutoutRect() {
        val target = targetView
        if (target == null) {
            cutoutRect = null
            return
        }
        val targetLoc = IntArray(2)
        target.getLocationOnScreen(targetLoc)
        val thisLoc = IntArray(2)
        getLocationOnScreen(thisLoc)
        val left = (targetLoc[0] - thisLoc[0]).toFloat() - cutoutPadding
        val top = (targetLoc[1] - thisLoc[1]).toFloat() - cutoutPadding
        cutoutRect = RectF(
            left,
            top,
            left + target.width + 2 * cutoutPadding,
            top + target.height + 2 * cutoutPadding
        )
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        updateCutoutRect()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)
        cutoutRect?.let { rect ->
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, clearPaint)
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, ringPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val rect = cutoutRect ?: return true
        val inside = rect.contains(event.x, event.y)
        if (!inside) {
            // Cutout dışı: tap'i consume et (kullanıcı yanlış butona basıp
            // tour'u bozmasın)
            return true
        }
        // Cutout içi: forwardTaps false ise gerçek butona iletme; ACTION_UP'ta
        // varsa onCutoutTapped callback'i tetikle (mock-add senaryosu).
        if (!forwardTaps) {
            if (event.action == MotionEvent.ACTION_UP) {
                onCutoutTapped?.invoke()
            }
            return true
        }
        val target = targetView ?: return true
        val targetLoc = IntArray(2)
        target.getLocationOnScreen(targetLoc)
        val thisLoc = IntArray(2)
        getLocationOnScreen(thisLoc)
        val translated = MotionEvent.obtain(event)
        translated.offsetLocation(
            (thisLoc[0] - targetLoc[0]).toFloat(),
            (thisLoc[1] - targetLoc[1]).toFloat()
        )
        val handled = target.dispatchTouchEvent(translated)
        translated.recycle()
        // forwardTaps=true durumunda da ACTION_UP sonrası callback tetikle
        // (mock-add senaryosu — gerçek butona basıldığı an mock store'a yaz)
        if (event.action == MotionEvent.ACTION_UP) onCutoutTapped?.invoke()
        return handled
    }
}
