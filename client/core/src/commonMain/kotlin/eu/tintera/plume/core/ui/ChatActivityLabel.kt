package eu.tintera.plume.core.ui

import androidx.compose.runtime.Composable
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
import eu.tintera.plume.resources.activity_watching_emoji
import org.jetbrains.compose.resources.stringResource

/**
 * Popisek činnosti v chatu („píše…").
 *
 * Krátký — v seznamu chatů konkuruje o místo s náhledem zprávy a v hlavičce konverzace
 * s dostupností. Sdílí ho obě místa, aby tatáž činnost nezněla na každém jinak.
 */
@Composable
fun ChatActivity.label(): String = when (this) {
    ChatActivity.Typing -> stringResource(Res.string.activity_typing)
    ChatActivity.RecordingVoice -> stringResource(Res.string.activity_recording_voice)
    ChatActivity.RecordingVideo -> stringResource(Res.string.activity_recording_video)
    ChatActivity.SendingPhoto -> stringResource(Res.string.activity_sending_photo)
    ChatActivity.SendingVideo -> stringResource(Res.string.activity_sending_video)
    ChatActivity.SendingFile -> stringResource(Res.string.activity_sending_file)
    ChatActivity.ChoosingSticker -> stringResource(Res.string.activity_choosing_sticker)
    // Které emoji, řekne víc než „animaci" — tak to píše i Telegram („sleduje 🎉").
    is ChatActivity.WatchingAnimation ->
        if (emoji.isBlank()) stringResource(Res.string.activity_watching_animation)
        else stringResource(Res.string.activity_watching_emoji, emoji)
    ChatActivity.Other -> stringResource(Res.string.activity_other)
}
