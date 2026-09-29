package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.settings.RecordMode

/**
 * Co tlačítko v prázdném poli nahraje. Uložená volba platí, jen když ji chat dovolí; jinak
 * zbyde druhý druh, a bez obou `null` — tlačítko je pak odeslání (neaktivní), ne mikrofon.
 *
 * [videoNotes] musí zahrnovat i to, že zařízení kameru MÁ: bez ní se kolečko nenabízí, ani
 * přepnutím.
 */
internal fun recordModeFor(preferred: RecordMode, voiceNotes: Boolean, videoNotes: Boolean): RecordMode? = when {
    preferred == RecordMode.VideoNote && videoNotes -> RecordMode.VideoNote
    voiceNotes -> RecordMode.Voice
    videoNotes -> RecordMode.VideoNote
    else -> null
}

/** Ťuknutí přepíná druh, jen když chat dovolí oba — jinak by přepnulo na nic. */
internal fun canSwitchRecordMode(voiceNotes: Boolean, videoNotes: Boolean): Boolean = voiceNotes && videoNotes

/** Kam došel tah při podrženém nahrávání. */
internal enum class RecordDrag { None, Cancel, Lock }

/**
 * Tah prstu od místa, kde nahrávání začalo ([dx] doleva a [dy] nahoru jsou záporné): doleva
 * přes [cancelDistance] nahrávku zahodí, nahoru přes [lockDistance] ji zamkne (nahrává se dál
 * bez prstu). Když prst přejel oba prahy, rozhodne ten, ke kterému má poměrně blíž — tah
 * šikmo doleva nahoru nesmí zahodit nahrávku, kterou chtěl uživatel zamknout.
 *
 * Opačné směry (doprava, dolů) nedělají nic: prst po tlačítku přirozeně trochu ujíždí.
 */
internal fun recordDragOutcome(dx: Float, dy: Float, cancelDistance: Float, lockDistance: Float): RecordDrag {
    val cancel = cancelProgress(dx, cancelDistance)
    val lock = lockProgress(dy, lockDistance)
    return when {
        cancel >= 1f && cancel >= lock -> RecordDrag.Cancel
        lock >= 1f -> RecordDrag.Lock
        else -> RecordDrag.None
    }
}

/** Jak daleko je tah ke zrušení, 0..1 a víc; kreslí se jím nápověda „Posunutím zrušíte". */
internal fun cancelProgress(dx: Float, cancelDistance: Float): Float =
    if (cancelDistance <= 0f) 0f else (-dx / cancelDistance).coerceAtLeast(0f)

/** Jak daleko je tah k zamčení, 0..1 a víc; kreslí se jím zámek nad tlačítkem. */
internal fun lockProgress(dy: Float, lockDistance: Float): Float =
    if (lockDistance <= 0f) 0f else (-dy / lockDistance).coerceAtLeast(0f)
