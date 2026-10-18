package eu.tintera.plume.core.telegram

/**
 * Oficiální jazykový balíček Telegramu pro jazyk aplikace [languageCode] („cs", „pt-BR"),
 * nebo prázdný řetězec = výchozí (angličtina). [packs] jsou id oficiálních balíčků
 * (`getLocalizationTargetInfo`), malými písmeny a s pomlčkou: „cs", „pt-br", „zh-hans".
 *
 * Napřed přesná shoda, pak první balíček téhož jazyka s regionem nebo písmem
 * („pt" → „pt-br", „zh" → „zh-hans") a nakonec jen základ jazyka („pt-PT" → „pt-br" ne,
 * ale „de-AT" → „de"). Norština je v Telegramu „nb", Java ji ale hlásí i jako „no".
 */
fun languagePackFor(languageCode: String, packs: List<String>): String {
    val code = languageCode.lowercase().replace('_', '-').let { if (it == "no" || it.startsWith("no-")) "nb" else it }
    val base = code.substringBefore('-')
    return packs.firstOrNull { it == code }
        ?: packs.firstOrNull { it == base }
        ?: packs.firstOrNull { code == base && it.startsWith("$base-") }
        ?: ""
}
