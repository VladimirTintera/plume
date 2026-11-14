package eu.tintera.plume.core.audio

import kotlin.time.Duration

/**
 * Kolik se skladba OPRAVDU poslouchala — pro `listenToAudio` (TDLib chce vědět, že uživatel
 * skladbu slyšel, a jak dlouho). Sčítá jen čas, kdy hrála: pauza, stahování ani hledání
 * v ní se nepočítají. Nahlásí se, když skladba skončí, uživatel pustí jinou, nebo hudbu zastaví.
 *
 * Čistá třída s vlastními hodinami ([now]) kvůli testům; [MusicQueue] jí podává stav fronty.
 */
internal class ListeningTracker<K : Any>(
    private val now: () -> Duration,
    private val report: (file: K, seconds: Int) -> Unit,
) {
    private var fileId: K? = null
    private var startedAt: Duration? = null
    private var listened = Duration.ZERO

    /** Co hraje teď: soubor skladby ([fileId], `null` = nic) a jestli zrovna zní. */
    fun update(fileId: K?, playing: Boolean) {
        if (fileId != this.fileId) {
            flush()
            this.fileId = fileId
        }
        val started = startedAt
        when {
            fileId != null && playing && started == null -> startedAt = now()
            !playing && started != null -> {
                listened += now() - started
                startedAt = null
            }
        }
    }

    private fun flush() {
        startedAt?.let { listened += now() - it }
        startedAt = null
        val id = fileId
        val seconds = listened.inWholeSeconds.toInt()
        listened = Duration.ZERO
        // Pod vteřinu to poslech nebyl (omylem ťuknutá skladba, hned přepnutá).
        if (id != null && seconds >= MIN_LISTENED_SECONDS) report(id, seconds)
    }
}

private const val MIN_LISTENED_SECONDS = 1
