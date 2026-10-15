package eu.tintera.plume.core.ui

import eu.tintera.plume.core.telegram.ChatActivity
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.activity_choosing_sticker
import eu.tintera.plume.resources.activity_other
import eu.tintera.plume.resources.activity_recording_video
import eu.tintera.plume.resources.activity_recording_voice
import eu.tintera.plume.resources.activity_sending_file
import eu.tintera.plume.resources.activity_sending_photo
import eu.tintera.plume.resources.activity_sending_video
import eu.tintera.plume.resources.activity_typing
import eu.tintera.plume.resources.activity_watching_animation
import org.jetbrains.compose.resources.StringResource

/**
 * Popisek činnosti v chatu („píše…").
 *
 * Krátký — v seznamu chatů konkuruje o místo s náhledem zprávy a v hlavičce konverzace
 * s dostupností. Sdílí ho obě místa, aby tatáž činnost nezněla na každém jinak.
 */
fun ChatActivity.label(): StringResource = when (this) {
    ChatActivity.Typing -> Res.string.activity_typing
    ChatActivity.RecordingVoice -> Res.string.activity_recording_voice
    ChatActivity.RecordingVideo -> Res.string.activity_recording_video
    ChatActivity.SendingPhoto -> Res.string.activity_sending_photo
    ChatActivity.SendingVideo -> Res.string.activity_sending_video
    ChatActivity.SendingFile -> Res.string.activity_sending_file
    ChatActivity.ChoosingSticker -> Res.string.activity_choosing_sticker
    ChatActivity.WatchingAnimation -> Res.string.activity_watching_animation
    ChatActivity.Other -> Res.string.activity_other
}
