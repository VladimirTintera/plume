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
 * Značka ověření od bota (`botVerificationIconCustomEmojiId`) sem nepatří: je to vlastní emoji
 * a to aplikace zatím nekreslí.
 */
enum class Verification { Verified, Scam, Fake }

/** Pravidlo přednosti; čistá funkce kvůli testům (`VerificationTest`). */
fun verificationOf(isVerified: Boolean, isScam: Boolean, isFake: Boolean): Verification? = when {
    isScam -> Verification.Scam
    isFake -> Verification.Fake
    isVerified -> Verification.Verified
    else -> null
}

internal fun VerificationStatus?.toVerification(): Verification? =
    this?.let { verificationOf(isVerified = it.isVerified, isScam = it.isScam, isFake = it.isFake) }

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
