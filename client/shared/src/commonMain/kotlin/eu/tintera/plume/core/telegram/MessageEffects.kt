package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.dto.MessageEffectTypeEmojiReaction
import dev.g000sha256.tdl.dto.MessageEffectTypePremiumSticker
import dev.g000sha256.tdl.dto.StickerFormatTgs
import dev.g000sha256.tdl.dto.StickerFullTypeRegular
import dev.g000sha256.tdl.dto.MessageEffect as TdMessageEffect

/**
 * Efekt zprávy z TDLib. Druhy jsou dva a animaci nese každý jinde:
 * - **efekt reakce** (konfety, oheň, srdce) má vlastní `effectAnimation` — tu, která se hraje
 *   kolem reakce vybrané na zprávě;
 * - **prémiová samolepka** má animaci v `premiumAnimation` své samolepky.
 *
 * `when` je bez `else`: nový druh efektu v novější TDLib rozbije překlad, místo aby se tiše
 * ukázal bez animace.
 */
internal fun TdMessageEffect.toMessageEffect(): MessageEffect = when (val type = type) {
    is MessageEffectTypeEmojiReaction -> MessageEffect(
        id = id,
        emoji = emoji,
        animationFileId = type.effectAnimation.takeIf { it.format is StickerFormatTgs }?.sticker?.id,
        premium = false,
    )
    is MessageEffectTypePremiumSticker -> MessageEffect(
        id = id,
        emoji = emoji,
        // Prémiová animace samolepky je vždycky Lottie; formát si nese samolepka, ne ona.
        animationFileId = (type.sticker.fullType as? StickerFullTypeRegular)?.premiumAnimation?.id,
        premium = true,
    )
}
