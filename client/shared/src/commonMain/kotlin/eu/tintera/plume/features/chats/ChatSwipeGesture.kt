package eu.tintera.plume.features.chats

import eu.tintera.plume.core.settings.ChatSwipeAction
import eu.tintera.plume.core.telegram.ChatSummary
import kotlin.math.min

/**
 * O kolik se musí chat o šířce [width] odtáhnout, aby ho puštění provedlo akci tahu. Nejvýš
 * [cap] — na široké ploše (tablet, dvoupanel) by podíl šířky znamenal tah přes půl obrazovky.
 *
 * Nulová šířka (první snímek, než je položka změřená) znamená prah, kterého nejde dosáhnout:
 * tah, o kterém nevíme, jak je dlouhý, nesmí s chatem nic udělat.
 */
internal fun chatSwipeThreshold(width: Float, cap: Float): Float =
    if (width <= 0f) Float.POSITIVE_INFINITY else min(width * COMMIT_FRACTION, cap)

/**
 * Podíl šířky řádku, o který se musí chat odtáhnout.
 *
 * `SwipeToDismissBox` měl 56 dp a k tomu zkratku přes rychlost: tah rychlejší než 125 dp/s
 * archivoval bez ohledu na to, jak daleko došel. Právě ta zkratka dělala z každého škubnutí
 * archivaci — proto se tu rychlost nepočítá vůbec a rozhoduje jen ušlá vzdálenost.
 */
private const val COMMIT_FRACTION = 0.4f

/** Co tah s konkrétním chatem udělá — zvolená akce, otočená podle jeho stavu. */
internal enum class ChatSwipeEffect(
    /**
     * Chat po akci ze zobrazeného seznamu odejde (archiv ↔ hlavní seznam), takže řádek po
     * puštění odjede z obrazovky. Ostatní akce chat nechají na místě a řádek se vrátí.
     */
    val leavesList: Boolean = false,
) {
    Archive(leavesList = true),
    Unarchive(leavesList = true),
    MarkRead,
    MarkUnread,
    Mute,
    Unmute,
    Pin,
    Unpin,

    /** Nabídne doby odložení — doba se tahem zvolit nedá, na rozdíl od ztlumení „navždy". */
    Snooze,
    Unsnooze,
}

/**
 * Co udělá tah s akcí [action] s chatem [chat]; `null` = na té straně se tah nechytne.
 *
 * Uložené zprávy archivovat nejdou (nabídka nad chatem archivaci u nich taky nemá) — tah, po
 * kterém přijde chyba, je horší než tah, který se nechytne. Sponzorovaný chat není uživatelův
 * a TDLib ho v seznamu drží sám, takže s ním tah nedělá nic.
 */
internal fun chatSwipeEffect(
    action: ChatSwipeAction,
    chat: ChatSummary,
    savedMessagesChatId: Long?,
    /** Chat právě spí (odložený, ještě se nevynořil). */
    snoozed: Boolean = false,
): ChatSwipeEffect? {
    if (chat.sponsor != null) return null
    return when (action) {
        ChatSwipeAction.None -> null
        ChatSwipeAction.Archive -> when {
            chat.id == savedMessagesChatId -> null
            chat.isArchived -> ChatSwipeEffect.Unarchive
            else -> ChatSwipeEffect.Archive
        }
        ChatSwipeAction.Read -> if (chat.hasUnread) ChatSwipeEffect.MarkRead else ChatSwipeEffect.MarkUnread
        ChatSwipeAction.Mute -> if (chat.isMuted) ChatSwipeEffect.Unmute else ChatSwipeEffect.Mute
        ChatSwipeAction.Pin -> if (chat.isPinned) ChatSwipeEffect.Unpin else ChatSwipeEffect.Pin
        ChatSwipeAction.Snooze -> if (snoozed) ChatSwipeEffect.Unsnooze else ChatSwipeEffect.Snooze
    }
}
