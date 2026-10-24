package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.OptionValueBoolean
import dev.g000sha256.tdl.dto.RestrictionInfo
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
internal suspend fun TdlClient.hidesSensitive(message: Message): Boolean =
    hidesSensitive(message.restrictionInfo)

/**
 * Jestli se má před citlivým CHATEM (kanál, účet) napřed varovat — oficiální klient to dělá už
 * při otevření. Stejná volba jako u zpráv: kdo si citlivý obsah povolil, varování nedostane.
 */
internal suspend fun TdlClient.hidesSensitive(restriction: RestrictionInfo?): Boolean =
    // Na volbu se ptá jen u citlivého obsahu — u historie o padesáti zprávách by to jinak byl
    // dotaz za každou.
    restriction?.hasSensitiveContent == true &&
        hidesSensitive(hasSensitiveContent = true, ignoresRestrictions = ignoresSensitive())

/** Povolil si uživatel citlivý obsah? Nezjištěná volba = nepovolil (skrýt). */
private suspend fun TdlClient.ignoresSensitive(): Boolean =
    (getOption(name = SensitiveContent.IGNORE_OPTION).toResult().getOrNull() as? OptionValueBoolean)?.value == true
