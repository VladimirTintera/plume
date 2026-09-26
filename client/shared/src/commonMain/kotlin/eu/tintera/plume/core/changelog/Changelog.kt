package eu.tintera.plume.core.changelog

/** Novinka, nebo oprava. Poznámky typu `internal` se do aplikace nedostanou vůbec. */
enum class ChangelogNoteType { FEAT, FIX }

/** Jedna poznámka k vydání, oba jazyky najednou (zdroj je `release-notes/<větev>.md`). */
data class ChangelogNote(val type: ChangelogNoteType, val cs: String, val en: String)

data class ChangelogRelease(
    /** `versionName` z tagu (`android-0.3.37-73` → `0.3.37`). */
    val version: String,
    /** Čas commitu, na kterém tag leží, v sekundách — jako časy zpráv, ať jde přes tentýž formát. */
    val date: Int,
    /** Novinky před opravami, v pořadí, v jakém přibyly. */
    val notes: List<ChangelogNote>,
)

/**
 * Poznámky k vydání **zapečené při buildu** (gradle task `generateChangelog`, viz „Co je nového"
 * v CLAUDE.md), od nejnovějšího vydání.
 *
 * Z buildu, ne ze serveru ani z Google Play: poznámky patří k verzi, kterou uživatel právě má,
 * a jdou číst i offline. Seznam končí vydáním, ze kterého je build — novější verze by lhaly.
 * Prázdný je u sestavy bez pythonu nebo gitu (cizí stroj, mělký klon).
 */
object Changelog {
    val releases: List<ChangelogRelease> get() = GENERATED_CHANGELOG
}
