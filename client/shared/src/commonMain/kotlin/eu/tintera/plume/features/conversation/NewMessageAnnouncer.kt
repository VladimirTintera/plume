package eu.tintera.plume.features.conversation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.conversation_new_message_from
import eu.tintera.plume.resources.conversation_new_message_from_untitled
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/**
 * **Ohlášení příchozí zprávy pro čtečku obrazovky.** Nová bublina se do seznamu jen vloží a čtečka
 * o ní nic neřekne; zrakově postižený by se o odpovědi dozvěděl, až by na ni náhodou narazil.
 * Tady ji ohlásí živá oblast („Nová zpráva od Jana: ahoj"), stejně jako „píše…" (`TypingItem`).
 *
 * - **Ohlašuje se jen zpráva, která opravdu PŘIŠLA**: novější než dosud nejnovější, a jen když
 *   seznam končil u přítomnosti — dotažená stránka (okno v historii) nebo první naplnění nic
 *   nepřináší. Vlastní a servisní zprávy ne. Z dávky naráz (album) jen ta nejnovější.
 * - **Uzel nese text jen chvíli** ([SHOWN_MS]) a pak zůstane prázdný. Ohlášení dělá ZMĚNA popisu
 *   živé oblasti; prázdný uzel čtečka při procházení přeskočí, kdežto trvalý by se tam s poslední
 *   zprávou pletl napořád.
 * - **Uzel nesmí být ZAKRYTÝ.** Compose dává čtečce jen nezakryté uzly (pořadí kreslení), takže
 *   uzel v rohu pod horní lištou nebo pod seznamem ve stromu přístupnosti vůbec nebyl — logika
 *   ohlášení přitom proběhla (ověřeno logem na emulátoru, `uiautomator dump` ho neukázal).
 *   Volá se proto jako POSLEDNÍ v ploše a sedí uprostřed, kam lišty nesahají; má 1 dp a nic
 *   nekreslí.
 */
@Composable
internal fun BoxScope.NewMessageAnnouncer(
    /** Zprávy konverzace, nejnovější první (jako `ConversationState.messages`). */
    messages: List<ChatMessage>,
    /** Seznam končí u přítomnosti (`reachedEnd`) — jen pak je novější zpráva příchozí. */
    live: Boolean,
    /** Název chatu: v soukromém chatu zpráva jméno odesílatele nenese. */
    chatTitle: String?,
) {
    val newest = messages.firstOrNull()
    val memory = remember { AnnouncerMemory() }
    if (newest != null && newest.id != memory.newestId) {
        val previous = memory.newestId
        if (previous != null && newest.id > previous && memory.live && newest.announces()) {
            memory.arrived = newest
        }
        memory.newestId = newest.id
    }
    memory.live = live

    var shown by remember { mutableStateOf<ChatMessage?>(null) }
    val arrived = memory.arrived
    LaunchedEffect(arrived?.id) {
        shown = arrived ?: return@LaunchedEffect
        delay(SHOWN_MS)
        shown = null
    }

    val announcement = shown?.let { message ->
        val body = message.text.ifBlank { message.description?.resolve().orEmpty() }
        val sender = message.senderName ?: chatTitle
        if (sender.isNullOrBlank()) {
            stringResource(Res.string.conversation_new_message_from_untitled, body)
        } else {
            stringResource(Res.string.conversation_new_message_from, sender, body)
        }
    }
    Box(
        Modifier
            .align(Alignment.Center)
            .size(1.dp)
            .clearAndSetSemantics {
                if (announcement != null) contentDescription = announcement
                liveRegion = LiveRegionMode.Polite
            },
    )
}

private fun ChatMessage.announces(): Boolean = !isOutgoing && !isService

/**
 * Co ohlašovač ví mezi kompozicemi. Obyčejná třída, ne stav: zapisuje se v kompozici (jako
 * `NewestItem` u ukotvení seznamu) a zápis do snapshot stavu by obrazovku jen rekomponoval.
 */
private class AnnouncerMemory {
    var newestId: Long? = null
    var live: Boolean = true
    var arrived: ChatMessage? = null
}

/** Jak dlouho uzel nese text ohlášení, než zase zůstane prázdný. */
private const val SHOWN_MS = 5_000L
