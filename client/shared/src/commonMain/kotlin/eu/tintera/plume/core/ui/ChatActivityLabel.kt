package eu.tintera.plume.core.ui

import eu.tintera.plume.core.telegram.ChatActivity

/**
 * Popisek činnosti v chatu („píše…").
 *
 * Krátký — v seznamu chatů konkuruje o místo s náhledem zprávy a v hlavičce konverzace
 * s dostupností. Sdílí ho obě místa, aby tatáž činnost nezněla na každém jinak.
 */
fun ChatActivity.label(): String = when (this) {
    ChatActivity.Typing -> "píše…"
    ChatActivity.RecordingVoice -> "nahrává hlasovku…"
    ChatActivity.RecordingVideo -> "natáčí video…"
    ChatActivity.SendingPhoto -> "posílá fotku…"
    ChatActivity.SendingVideo -> "posílá video…"
    ChatActivity.SendingFile -> "posílá soubor…"
    ChatActivity.ChoosingSticker -> "vybírá samolepku…"
    ChatActivity.Other -> "něco chystá…"
}
