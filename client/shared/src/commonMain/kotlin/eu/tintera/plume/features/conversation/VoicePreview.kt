package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.audio.RecordedVoice
import eu.tintera.plume.core.audio.VoiceKey
import eu.tintera.plume.core.audio.decodeWaveform
import eu.tintera.plume.core.telegram.OutgoingVideoNote
import eu.tintera.plume.core.audio.resampleWaveform

/**
 * Hlasovka zastavená k poslechu před odesláním (zamčené nahrávání → ■).
 *
 * [voice] je `null`, dokud se nahrávka dokončuje: na iOS se po zastavení teprve kóduje do Opusu
 * a bez tohohle mezistavu by karta na tu chvíli ukázala textové pole — jako by nahrávka zmizela.
 */
class VoicePreview(val voice: RecordedVoice?) {
    /** Sloupce waveformu pro pruh náhledu; počítá se jednou, ne při každém snímku přehrávání. */
    val bars: List<Float> = voice?.let { resampleWaveform(decodeWaveform(it.waveform), VOICE_PREVIEW_BARS) }.orEmpty()
}

/**
 * Klíč, pod kterým [eu.tintera.plume.core.audio.VoicePlayer] hraje náhled. Zpráva to není,
 * takže id je záporné — skutečné id zprávy je vždycky kladné. **Po dohrání se od něj nesmí
 * řetězit další neposlechnutá** ([isVoicePreview]): „další po −1" by byla nejstarší hlasovka v chatu.
 */
internal fun voicePreviewKey(chatId: Long) = VoiceKey(chatId, VOICE_PREVIEW_MESSAGE_ID)

internal val VoiceKey.isVoicePreview: Boolean get() = messageId == VOICE_PREVIEW_MESSAGE_ID

private const val VOICE_PREVIEW_MESSAGE_ID = -1L

internal const val VOICE_PREVIEW_BARS = 48

/**
 * Kolečko zastavené k přehrání před odesláním (zamčené nahrávání → ■).
 *
 * [note] je `null`, dokud se nahrávka dokončuje a připravuje: zmenšení na Androidu i ořez na iOS
 * trvá vteřiny a kruh by jinak na tu chvíli zmizel, jako by nahrávka propadla.
 */
class VideoNotePreview(val note: OutgoingVideoNote?)
