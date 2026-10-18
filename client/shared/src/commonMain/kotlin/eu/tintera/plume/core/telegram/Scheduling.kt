package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.dto.MessageSchedulingState
import dev.g000sha256.tdl.dto.MessageSchedulingStateSendAtDate
import dev.g000sha256.tdl.dto.MessageSchedulingStateSendWhenOnline
import dev.g000sha256.tdl.dto.MessageSchedulingStateSendWhenVideoProcessed
import dev.g000sha256.tdl.dto.MessageSendOptions
import dev.g000sha256.tdl.dto.InputSuggestedPostInfo

/** `null` = výchozí odeslání; TDLib pak nic nenastavuje. */
internal fun SendOptions.toTdl(): MessageSendOptions? {
    if (!silent && schedule == null && effectId == 0L && !suggestPost) return null
    return MessageSendOptions(
        suggestedPostInfo = if (suggestPost) InputSuggestedPostInfo(price = null, sendDate = 0) else null,
        disableNotification = silent,
        fromBackground = false,
        protectContent = false,
        allowPaidBroadcast = false,
        paidMessageStarCount = 0,
        updateOrderOfInstalledStickerSets = false,
        schedulingState = schedule?.toTdl(),
        effectId = effectId,
        sendingId = 0,
        onlyPreview = false,
    )
}

internal fun MessageSchedule.toTdl(): MessageSchedulingState = when (this) {
    is MessageSchedule.At -> MessageSchedulingStateSendAtDate(sendDate = epochSeconds.toInt(), repeatPeriod = 0)
    MessageSchedule.WhenOnline -> MessageSchedulingStateSendWhenOnline()
}

/**
 * `when` bez `else`: nový druh plánování v novější TDLib rozbije překlad, místo aby zpráva
 * tiše vypadala jako běžná — a vmíchala se do historie.
 */
internal fun MessageSchedulingState.toSchedule(): MessageSchedule = when (this) {
    is MessageSchedulingStateSendAtDate -> MessageSchedule.At(sendDate.toLong())
    is MessageSchedulingStateSendWhenOnline -> MessageSchedule.WhenOnline
    // Video, které server ještě zpracovává; odejde, až bude hotové — nejbližší je „v čase".
    is MessageSchedulingStateSendWhenVideoProcessed -> MessageSchedule.At(sendDate.toLong())
}
