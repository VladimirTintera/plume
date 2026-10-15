package eu.tintera.plume.core.audio

import eu.tintera.plume.core.video.MIN_RESUME_AUDIO_DURATION_MS
import eu.tintera.plume.core.video.resumePosition

/**
 * Odkud zvuk pustit a jestli se tím pokračuje tam, kde uživatel minule přestal.
 *
 * [resumedFromMs] je vyplněné jen tehdy, když se začíná z PAMATOVANÉ pozice — podle něj se ukáže
 * „Pokračuje od 12:34" s nabídkou pustit to od začátku. Výslovný čas („1:23" v textu zprávy) je
 * žádost uživatele, tam se nic vysvětlovat nemusí.
 */
data class AudioStart(val startMs: Long, val resumedFromMs: Long? = null)

/**
 * Odkud pustit hlasovku nebo audio: výslovný čas ([requestedMs], odkaz „1:23") > pamatovaná pozice
 * ([rememberedMs]) > začátek. Stejné pořadí jako u videa.
 *
 * - **Čas za koncem** (překlep v „1:23" u krátké hlasovky) by přehrávač zastavil hned, takže se
 *   začne od začátku.
 * - **Pamatovaná pozice se ověřuje znovu** ([resumePosition] s prahem pro zvuk): seznam pozic je
 *   společný s videem a délka od přehrávače se od té uložené může lišit — pozice u konce nebo
 *   u krátkého souboru se nepoužije.
 */
fun audioStart(requestedMs: Long, rememberedMs: Long?, durationMs: Long): AudioStart {
    if (requestedMs > 0) return AudioStart(requestedMs.takeIf { it < durationMs } ?: 0L)
    val resume = rememberedMs?.let { resumePosition(it, durationMs, MIN_RESUME_AUDIO_DURATION_MS) }
        ?: return AudioStart(0L)
    return AudioStart(resume, resumedFromMs = resume)
}

/**
 * Jak často se za přehrávání ukládá pozice (zabitý proces by jinak vzal i poslední minuty).
 * Stejně jako u videa.
 */
const val AUDIO_POSITION_SAVE_INTERVAL_MS = 10_000L
