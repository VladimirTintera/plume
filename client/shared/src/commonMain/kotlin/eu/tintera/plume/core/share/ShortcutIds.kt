package eu.tintera.plume.core.share

import eu.tintera.plume.core.accounts.AccountSlot

/**
 * Id zkratky konverzace: `chat_<chatId>`, u dalších účtů `chat_<slot>_<chatId>`. Primární účet
 * zůstává beze změny, ať nezmizí připnutí na ploše a konverzační kanály, které si uživatel nastavil.
 */
internal fun shortcutId(slot: AccountSlot, chatId: Long): String =
    if (slot.isPrimary) "$SHORTCUT_PREFIX$chatId" else "$SHORTCUT_PREFIX${slot.id}_$chatId"

/** Chat ze zkratky, přes kterou uživatel sdílel; `null` u cizího nebo nečitelného id. */
internal fun shortcutChat(id: String?): ShortcutTarget? {
    val rest = id?.removePrefix(SHORTCUT_PREFIX)?.takeIf { it != id } ?: return null
    val separator = rest.indexOf('_')
    val slot = AccountSlot.parse(if (separator < 0) null else rest.substring(0, separator)) ?: return null
    val chatId = rest.substring(separator + 1).toLongOrNull() ?: return null
    return ShortcutTarget(slot, chatId)
}

internal data class ShortcutTarget(val slot: AccountSlot, val chatId: Long)

private const val SHORTCUT_PREFIX = "chat_"

/**
 * Název zkratky konverzace: název chatu, s víc účty i jméno účtu („Notifikator · VT2"). Tutéž
 * zkratku sdílí notifikace (Android 11+ z ní bere jméno konverzace, `conversationTitle`
 * ignoruje), nabídka Sdílet a zástupce na ploše, takže všichni musí skládat stejný název —
 * jinak by se přepisoval tam a zpátky.
 */
internal fun shortcutLabel(title: String, account: String?): String = if (account == null) title else "$title · $account"
