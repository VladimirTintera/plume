package eu.tintera.plume.core.telegram

import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.error_slowmode_wait_minutes
import eu.tintera.plume.resources.error_slowmode_wait_seconds
import kotlin.time.Clock

/**
 * Kdy (epoch ms) skončí čekání pomalého režimu, které TDLib hlásí jako zbývající sekundy
 * [expiresInSeconds] od TEĎ; `null`, když se nečeká. Čas se převádí hned, jak TDLib odpoví —
 * zbývající sekundy by uprostřed odpočtu nic neznamenaly.
 */
internal fun slowModeUntil(expiresInSeconds: Double, now: Long = Clock.System.now().toEpochMilliseconds()): Long? =
    if (expiresInSeconds > 0) now + (expiresInSeconds * 1000).toLong() else null

/**
 * „Další zprávu půjde poslat za …" — po chybě od serveru (`SLOWMODE_WAIT_`) i po ťuknutí na
 * odpočet u vstupního pole. Pod dvě minuty sekundy, pak minuty zaokrouhlené nahoru („za 0 min"
 * by lhalo). Hranice je dvě minuty, ne jedna: vedle odpočtu „1:01" četla hláška „za 2 min"
 * a vypadalo to, že si obě čísla odporují.
 */
fun slowModeWaitText(seconds: Int): UiText = if (seconds < SECONDS_SHOWN_UP_TO) {
    UiText(Res.string.error_slowmode_wait_seconds, seconds)
} else {
    UiText(Res.string.error_slowmode_wait_minutes, (seconds + 59) / 60)
}

/** Do kolika sekund se čekání píše v sekundách (viz [slowModeWaitText]). */
private const val SECONDS_SHOWN_UP_TO = 120
