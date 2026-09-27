package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.toResult
import dev.g000sha256.tdl.dto.Chat
import dev.g000sha256.tdl.dto.ChatTypeBasicGroup
import dev.g000sha256.tdl.dto.ChatTypePrivate
import dev.g000sha256.tdl.dto.ChatTypeSecret
import dev.g000sha256.tdl.dto.ChatTypeSupergroup
import dev.g000sha256.tdl.dto.VerificationStatus

/**
 * Co o účtu nebo chatu říká Telegram: ověřený, nebo označený jako podvod či falešný.
 *
 * **Podvod a falešný jsou bezpečnostní údaj, ne ozdoba.** Telegram je přiděluje účtům, které se
 * vydávají za někoho jiného nebo lákají peníze; bez štítku u jména je uživatel pozná až poté,
 * co mu ublíží. Proto mají přednost před ověřením — kdyby měl účet obojí, ověření by uklidnilo
 * přesně tam, kde má uživatel zbystřit.
 *
 * **Ověřit může i bot** (`botVerificationIconCustomEmojiId`): organizace s vlastním ověřovacím
 * botem dá účtu svou značku — vlastní emoji. Je to ověření jako každé jiné a stojí vedle
 * fajfky Telegramu, ne místo ní; u podvodu a falešného účtu se nekreslí, ze stejného důvodu
 * jako fajfka.
 */
sealed interface Verification {
    data object Scam : Verification

    data object Fake : Verification

    /**
     * Ověřený účet: [official] fajfkou Telegramu, [botIconEmojiId] značkou od ověřovacího bota.
     * Aspoň jedno z toho platí vždycky — neověřený účet je `null`, ne `Verified`.
     */
    data class Verified(val official: Boolean = true, val botIconEmojiId: Long? = null) : Verification
}

/** Pravidlo přednosti; čistá funkce kvůli testům (`VerificationTest`). */
fun verificationOf(
    isVerified: Boolean,
    isScam: Boolean,
    isFake: Boolean,
    botIconEmojiId: Long = 0L,
): Verification? = when {
    isScam -> Verification.Scam
    isFake -> Verification.Fake
    isVerified || botIconEmojiId != 0L ->
        Verification.Verified(official = isVerified, botIconEmojiId = botIconEmojiId.takeIf { it != 0L })
    else -> null
}

internal fun VerificationStatus?.toVerification(): Verification? = this?.let {
    verificationOf(
        isVerified = it.isVerified,
        isScam = it.isScam,
        isFake = it.isFake,
        botIconEmojiId = it.botVerificationIconCustomEmojiId,
    )
}

/**
 * Id chatu supergroupy (skupiny i kanálu) z id supergroupy — konvence TDLib, kterou nese
 * i push na iOS (viz „Notifikace na iOS"). Update supergroupy chat nejmenuje.
 */
internal fun supergroupChatId(supergroupId: Long): Long = -1_000_000_000_000L - supergroupId

/**
 * Ověření chatu: `Chat` ho nenese, je na uživateli (soukromý a tajný chat) a supergroupě
 * (skupina i kanál). Lokální dotaz — TDLib je zná, když zná chat. Základní skupina ověření nemá.
 */
internal suspend fun TdlClient.verificationOf(chat: Chat): Verification? = when (val type = chat.type) {
    is ChatTypePrivate -> getUser(userId = type.userId).toResult().getOrNull()?.verificationStatus.toVerification()
    is ChatTypeSecret -> getUser(userId = type.userId).toResult().getOrNull()?.verificationStatus.toVerification()
    is ChatTypeSupergroup ->
        getSupergroup(supergroupId = type.supergroupId).toResult().getOrNull()?.verificationStatus.toVerification()
    is ChatTypeBasicGroup -> null
}
