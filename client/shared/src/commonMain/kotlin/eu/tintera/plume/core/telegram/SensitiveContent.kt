package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.OptionValueBoolean
import dev.g000sha256.tdl.toResult

/**
 * Citlivý obsah: médium, které server označil jako nevhodné bez varování
 * (`Message.restrictionInfo.hasSensitiveContent`, důvod omezení „sensitive").
 *
 * **Skrývá se stejně jako spoiler** — rozostřené, odkryje se ťuknutím —, jen štítek říká
 * „Citlivý obsah". Je to táž věc („podívej se, až budeš chtít"), takže dvě různá skrývání by
 * znamenala dvakrát totéž rozostření, odkrývání i let do prohlížeče. Rozdíl nese jen štítek
 * (`MessageMedia.*.isSensitive`).
 *
 * **Volbu uživatele musí hlídat aplikace, ne TDLib.** Kdo si v Telegramu citlivý obsah povolil,
 * má `ignore_sensitive_content_restrictions`, ale TDLib příznak `hasSensitiveContent` hlásí dál
 * (`get_restriction_info_object` v `RestrictionReason.cpp` na volbu nesahá). Bez kontroly by se
 * mu rozostřovalo, co si výslovně přál vidět.
 */
internal object SensitiveContent {
    const val IGNORE_OPTION = "ignore_sensitive_content_restrictions"
}

/** Pravidlo; čistá funkce kvůli testům (`SensitiveContentTest`). */
internal fun hidesSensitive(hasSensitiveContent: Boolean, ignoresRestrictions: Boolean): Boolean =
    hasSensitiveContent && !ignoresRestrictions

/**
 * Jestli se má médium zprávy skrýt. Na volbu se ptá jen u zprávy, která citlivý obsah má —
 * u historie o padesáti zprávách by to jinak byl dotaz za každou. Nezjištěná volba = skrýt.
 */
internal suspend fun TdlClient.hidesSensitive(message: Message): Boolean {
    if (message.restrictionInfo?.hasSensitiveContent != true) return false
    val ignores = (getOption(name = SensitiveContent.IGNORE_OPTION).toResult().getOrNull() as? OptionValueBoolean)
        ?.value == true
    return hidesSensitive(hasSensitiveContent = true, ignoresRestrictions = ignores)
}
