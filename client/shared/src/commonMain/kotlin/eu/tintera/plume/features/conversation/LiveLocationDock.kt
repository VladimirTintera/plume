package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.features.conversation.items.isLiveLocation

/*
 * Dock živé polohy: řádek pod pruhy nad konverzací, do kterého se zpráva se živou polohou
 * „přestěhuje", když odjede z obrazovky. Tady jsou pravidla (které polohy v docku jsou a kolik
 * ze zprávy je schované); kreslí `LiveLocationDockRow`.
 */

/** Konec sdílení, nebo `null`, když zpráva živou polohou není. */
private val ChatMessage.liveUntil: Long?
    get() = (media as? MessageMedia.Location)?.takeIf { it.isLive }?.liveUntil

/** Sdílí se poloha ve zprávě ještě v okamžiku [now]? */
internal fun ChatMessage.sharesLocationAt(now: Long): Boolean = (liveUntil ?: 0) > now

/**
 * Seznam živých poloh v docku po příchodu nebo úpravě [message]: nahradí ji, přidá, nebo —
 * když se už nesdílí (zastavená, prošlá) — odebere. Zpráva, která živou polohou není, seznam
 * nemění. Pořadí: vlastní napřed (tu jde ovládat), pak od nejnovější.
 */
internal fun List<ChatMessage>.withLiveLocation(message: ChatMessage, now: Long): List<ChatMessage> {
    if (!message.isLiveLocation) return this
    val rest = filterNot { it.id == message.id }
    val next = if (message.sharesLocationAt(now)) rest + message else rest
    return next.sortedWith(compareByDescending<ChatMessage> { it.isOutgoing }.thenByDescending { it.id })
}

/** Bez poloh, které v okamžiku [now] už skončily. */
internal fun List<ChatMessage>.withoutEnded(now: Long): List<ChatMessage> = filter { it.sharesLocationAt(now) }

/** Nejbližší konec sdílení — tehdy se má poloha z docku odebrat; `null`, když nic nekončí. */
internal fun List<ChatMessage>.nextLiveEnd(): Long? =
    mapNotNull { it.liveUntil }.filter { it != Long.MAX_VALUE }.minOrNull()

/**
 * Jak moc je zpráva schovaná — a tedy jak moc je „v docku": 0 = celá vidět, 1 = celá pod
 * okrajem nebo mimo seznam.
 *
 * Počítá se v souřadnicích obráceného seznamu (offset roste odspodu, viz `topVisibleKey`).
 * Zpráva zajíždí pod HORNÍ okraj ([top] = spodní hrana pruhů BEZ řádku docku) nebo pod SPODNÍ
 * ([bottom] = horní hrana vstupního pole). Bere se podíl, který je za kterýmkoli z nich —
 * tah prstem tak dock vysouvá a zasouvá plynule, oběma směry.
 *
 * **Horní okraj se počítá bez řádku docku.** Řádek se vysouvá s tímhle podílem a s ním i odsazení
 * seznamu; kdyby okraj šel s ním, zakryl by řádek další kus zprávy, ta by se schovala víc, řádek
 * by se vysunul dál… a z plynulého přechodu by byl skok.
 */
internal fun dockProgress(offset: Int, size: Int, top: Float, bottom: Float): Float {
    if (size <= 0) return 1f
    val start = offset.toFloat()
    val end = start + size
    val hiddenTop = (end - top).coerceIn(0f, size.toFloat())
    val hiddenBottom = (bottom - start).coerceIn(0f, size.toFloat())
    return (maxOf(hiddenTop, hiddenBottom) / size).coerceIn(0f, 1f)
}
