package eu.tintera.plume.features.webapp

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Gesto uživatele v mini aplikaci: dotyk ve WebView, hlavní nebo druhé tlačítko, tlačítko popupu.
 *
 * Odkazy, stažení, sdílení zprávy, zástupce na ploše a dialog polohy smí stránka vyvolat jen
 * krátce po gestu ([GESTURE_WINDOW]) a jedním gestem jen jednou ([consume]). Bez toho mohla
 * stránka ve smyčce otevírat prohlížeč, nebo podstrčit dialog přesně pod opakované ťuknutí.
 * Okno i výčet akcí jsou podle oficiálního klienta pro Android (`BotWebViewContainer.lastClickMs`).
 */
internal class WebAppGestureGate(private val timeSource: TimeSource = TimeSource.Monotonic) {
    private var lastGesture: TimeMark? = null

    fun gesture() {
        lastGesture = timeSource.markNow()
    }

    /** Bylo gesto nejvýš před [GESTURE_WINDOW]? Gesto se tím spotřebuje: jedno ťuknutí = jedna akce. */
    fun consume(): Boolean {
        val mark = lastGesture ?: return false
        lastGesture = null
        return mark.elapsedNow() <= GESTURE_WINDOW
    }
}

/**
 * Brzda dialogů, které vyvolává stránka (popup, zápis, poloha, stažení). Stránka, která dialog
 * po zavření hned otevře znovu, by uživatele zavřela ve smyčce: třetí otevření do
 * [QUICK_REOPEN] od zavření předchozího spustí pauzu [DIALOG_COOLDOWN], po kterou se žádný
 * další dialog neukáže. Pravidlo je z oficiálního klienta pro Android (`dialogSequentialOpenTimes`).
 */
internal class WebAppDialogThrottle(private val timeSource: TimeSource = TimeSource.Monotonic) {
    private var lastClosed: TimeMark? = null
    private var quickReopens = 0
    private var cooldownEnd: TimeMark? = null

    fun closed() {
        lastClosed = timeSource.markNow()
    }

    /**
     * Smí se ukázat další dialog? `Duration.ZERO` = ano, jinak jak dlouho ještě trvá pauza.
     * Volá se, jen když žádný dialog otevřený není.
     */
    fun wait(): Duration {
        cooldownEnd?.let { end ->
            if (end.hasNotPassedNow()) return -end.elapsedNow()
            cooldownEnd = null
        }
        val closed = lastClosed
        if (closed != null && closed.elapsedNow() <= QUICK_REOPEN) {
            quickReopens++
            if (quickReopens >= QUICK_REOPEN_LIMIT) {
                quickReopens = 0
                val end = timeSource.markNow() + DIALOG_COOLDOWN
                cooldownEnd = end
                return DIALOG_COOLDOWN
            }
        } else {
            quickReopens = 0
        }
        return Duration.ZERO
    }
}

/** Jak dlouho po gestu smí stránka otevřít odkaz nebo dialog; oficiální klient má 10 s. */
internal val GESTURE_WINDOW = 10.seconds

private val QUICK_REOPEN = 150.milliseconds
private const val QUICK_REOPEN_LIMIT = 3
internal val DIALOG_COOLDOWN = 3.seconds

/**
 * Tlačítka dialogu od stránky nereagují hned po jeho ukázání: stránka ho může vyvolat přesně
 * pod opakované ťuknutí („ťukej co nejrychleji") a uživatel by souhlasil s něčím, co neviděl.
 */
internal val DIALOG_ARM_DELAY = 500.milliseconds
