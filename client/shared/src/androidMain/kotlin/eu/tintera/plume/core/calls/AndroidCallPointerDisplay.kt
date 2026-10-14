package eu.tintera.plume.core.calls

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.hardware.input.InputManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
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

/**
 * Značka ukazovátka. Musí být vidět na čemkoli — světlé aplikaci, tmavé, fotce — a okno
 * má navíc průhlednost 0,8 (viz třída výš), takže samotná barva nestačí:
 * - **kroužek ve třech vrstvách**: tmavý stín, bílý lem a barevný střed. Na světlém
 *   pozadí nese obrys stín, na tmavém lem; barva akcentu jen říká, že je to Plume.
 * - **pulz**: od značky se opakovaně rozbíhá kruh, oko si pohybu všimne dřív než tvaru.
 * - **stopa**: za tahem zůstane krátká čára, která do [TRAIL_MS] zmizí — je z ní vidět,
 *   odkud a kam protistrana ukazovala, i když se člověk podívá pozdě.
 * Po zvednutí prstu značka chvíli zůstane ([LINGER_MS]) a vybledne.
 */
private class PointerView(context: Context) : View(context) {

    private val density = resources.displayMetrics.density
    private val accent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getColor(android.R.color.system_accent1_500)
    } else {
        context.getColor(android.R.color.holo_orange_dark)
    }
    private fun stroke(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = width * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val shadow = stroke(Color.BLACK, SHADOW_DP).apply { alpha = SHADOW_ALPHA }
    private val halo = stroke(Color.WHITE, HALO_DP)
    private val ring = stroke(accent, RING_DP)
    private val pulse = stroke(accent, RING_DP)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accent
        alpha = FILL_ALPHA
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }

    private var x = -1f
    private var y = -1f
    private val trail = ArrayDeque<TrailPoint>()
    private val path = Path()
    // Po vyblednutí pulz zastavit: neviditelný view se nekreslí a animátor by běžel dál.
    private val fade = Runnable {
        animate().alpha(0f).setDuration(FADE_MS).withEndAction {
            pulser.cancel()
            trail.clear()
        }
    }

    /** 0–1 v cyklu pulzu; animátor běží, dokud je značka vidět. */
    private var phase = 0f
    private val pulser = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = PULSE_MS
        repeatCount = ValueAnimator.INFINITE
        interpolator = DecelerateInterpolator()
        addUpdateListener {
            phase = it.animatedValue as Float
            invalidate()
        }
    }

    fun point(nx: Float, ny: Float, down: Boolean) {
        x = nx * width
        y = ny * height
        val now = SystemClock.uptimeMillis()
        trail.addLast(TrailPoint(x, y, now))
        removeCallbacks(fade)
        animate().cancel()
        alpha = 1f
        if (!pulser.isStarted) pulser.start()
        if (!down) postDelayed(fade, LINGER_MS)
        invalidate()
    }

    override fun onDetachedFromWindow() {
        pulser.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        if (x < 0f) return
        drawTrail(canvas)
        val radius = RADIUS_DP * density
        // Pulz: kruh se rozbíhá do dvojnásobku a mizí.
        pulse.alpha = ((1f - phase) * PULSE_ALPHA).toInt()
        canvas.drawCircle(x, y, radius * (1f + phase), pulse)
        canvas.drawCircle(x, y, radius, fill)
        canvas.drawCircle(x, y, radius, shadow)
        canvas.drawCircle(x, y, radius, halo)
        canvas.drawCircle(x, y, radius, ring)
        canvas.drawCircle(x, y, DOT_DP * density + HALO_DP * density / 2, halo)
        canvas.drawCircle(x, y, DOT_DP * density, dot)
    }

    /** Stopa za tahem: body mladší než [TRAIL_MS], stejné vrstvy jako kroužek. */
    private fun drawTrail(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        while (trail.isNotEmpty() && now - trail.first().at > TRAIL_MS) trail.removeFirst()
        if (trail.size < 2) return
        path.reset()
        trail.forEachIndexed { index, point -> if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y) }
        canvas.drawPath(path, shadow)
        canvas.drawPath(path, halo)
        canvas.drawPath(path, ring)
    }

    private class TrailPoint(val x: Float, val y: Float, val at: Long)

    private companion object {
        const val RADIUS_DP = 34f
        const val DOT_DP = 6f
        const val RING_DP = 5f
        const val HALO_DP = 9f
        const val SHADOW_DP = 13f
        const val SHADOW_ALPHA = 110
        const val FILL_ALPHA = 60
        const val PULSE_ALPHA = 220
        const val PULSE_MS = 900L
        const val TRAIL_MS = 700L
        const val LINGER_MS = 1_500L
        const val FADE_MS = 400L
    }
}

private const val MAX_OPACITY = 0.8f
