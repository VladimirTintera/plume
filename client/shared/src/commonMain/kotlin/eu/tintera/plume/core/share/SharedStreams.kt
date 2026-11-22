package eu.tintera.plume.core.share

/**
 * Smí Plume přijmout soubor sdílený z jiné aplikace ([uri] z `EXTRA_STREAM` / `ClipData`)?
 *
 * Sdílení je zmatený prostředník: Plume soubor čte pod VLASTNÍ identitou, takže mu cizí
 * aplikace (bez jakéhokoli oprávnění) mohla podstrčit cestu k jeho soukromým datům
 * a předvybrat svůj chat (`EXTRA_SHORTCUT_ID`). Uživatel by pak jedním „Odeslat" poslal
 * soubor, který sám nikdy nevybral — u `file://` navíc jen s obecným jménem „soubor".
 * Ve hře byla stažená média (i z tajných chatů), databáze TDLib, nastavení.
 *
 * - **Jen `content://`.** `file://` jiná aplikace od Androidu 7 sdílet nesmí
 *   (`FileUriExposedException`); přichází tak už jen útok na vlastní soubory.
 * - **Vlastní poskytovatel ([isOwnAuthority]) jen pro to, co Plume sám právě sdílel
 *   ([ownShares])**: „Sdílet" v prohlížeči médií pošle kopii z `cache/shared` přes výběr
 *   aplikací a uživatel si v něm může vybrat zase Plume. Cokoli jiného z vlastního
 *   poskytovatele je podstrčené.
 * - **Autorita se posuzuje bez čísla uživatele** (`10@autorita`): `ContentResolver` takovou
 *   adresu otevře u téhož poskytovatele, takže by kontrolu obešla.
 */
internal fun sharedStreamAllowed(uri: String, isOwnAuthority: (String) -> Boolean, ownShares: Set<String>): Boolean {
    val match = CONTENT_URI.find(uri) ?: return false
    val authority = match.groupValues[1].substringAfterLast('@')
    if (authority.isEmpty()) return false
    return !isOwnAuthority(authority) || uri in ownShares
}

private val CONTENT_URI = Regex("""^content://([^/?#]*)""", RegexOption.IGNORE_CASE)
