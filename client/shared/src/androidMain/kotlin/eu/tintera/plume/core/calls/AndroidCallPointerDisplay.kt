package eu.tintera.plume.core.calls

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.hardware.input.InputManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import co.touchlab.kermit.Logger

/**
 * Ukazovátko protistrany přes všechny aplikace, když sdílíme obrazovku: okno
 * `TYPE_APPLICATION_OVERLAY` přes celý displej, které dotyky nebere.
 *
 * - **Chce oprávnění „Zobrazení přes jiné aplikace"** (`Settings.canDrawOverlays`); bez něj
 *   [available] je `false` a protistrana se o ukazovátku nedozví (`Hello`).
 * - **Průhlednost okna nejvýš `maximumObscuringOpacityForTouch` (0,8).** Od Androidu 12 okno
 *   jiné aplikace přes dotyky, které je neprůhlednější, aplikaci pod sebou dotyky NEPUSTÍ
 *   (ochrana proti tapjackingu), i když samo dotyky nebere — sdílející by pak nemohl na
 *   svůj telefon ťuknout.
 * - **Přes celý displej včetně výřezu a lišt** (`LAYOUT_IN_SCREEN`, `NO_LIMITS`, výřez
 *   `ALWAYS`), ať poloha v poměru odpovídá snímané obrazovce.
 * - Značka po zvednutí prstu protistrany vybledne; okno zmizí s koncem sdílení ([hide]).
 */
internal class AndroidCallPointerDisplay(context: Context) : CallPointerDisplay {

    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val logger = Logger.withTag("CallPointer")
    private var view: PointerView? = null

    override val available: Boolean get() = Settings.canDrawOverlays(app)

    override fun show(x: Float, y: Float, down: Boolean) {
        main.post { (view ?: attach())?.point(x, y, down) }
    }

    override fun hide() {
        main.post {
            val current = view ?: return@post
            view = null
            runCatching { app.getSystemService(WindowManager::class.java).removeView(current) }
        }
    }

    private fun attach(): PointerView? {
        if (!available) return null
        val created = PointerView(app)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            alpha = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                app.getSystemService(InputManager::class.java).maximumObscuringOpacityForTouch
            } else {
                MAX_OPACITY
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }
        return runCatching {
            app.getSystemService(WindowManager::class.java).addView(created, params)
            created.also { view = it }
        }.onFailure { logger.w(it) { "Ukazovátko nejde ukázat přes jiné aplikace." } }.getOrNull()
    }
}

/** Značka: kroužek s tečkou uprostřed, po zvednutí prstu vybledne. */
private class PointerView(context: Context) : View(context) {

    private val density = resources.displayMetrics.density
    private val accent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getColor(android.R.color.system_accent1_400)
    } else {
        context.getColor(android.R.color.holo_orange_light)
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accent
        alpha = FILL_ALPHA
    }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accent
        style = Paint.Style.STROKE
        strokeWidth = RING_WIDTH_DP * density
    }
    private var x = -1f
    private var y = -1f
    private val fade = Runnable { animate().alpha(0f).setDuration(FADE_MS) }

    fun point(nx: Float, ny: Float, down: Boolean) {
        x = nx * width
        y = ny * height
        removeCallbacks(fade)
        animate().cancel()
        alpha = 1f
        if (!down) postDelayed(fade, LINGER_MS)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (x < 0f) return
        val radius = RADIUS_DP * density
        canvas.drawCircle(x, y, radius, fill)
        canvas.drawCircle(x, y, radius, ring)
        canvas.drawCircle(x, y, DOT_DP * density, ring)
    }

    private companion object {
        const val RADIUS_DP = 28f
        const val DOT_DP = 3f
        const val RING_WIDTH_DP = 3f
        const val FILL_ALPHA = 70
        const val LINGER_MS = 1_200L
        const val FADE_MS = 400L
    }
}

private const val MAX_OPACITY = 0.8f
