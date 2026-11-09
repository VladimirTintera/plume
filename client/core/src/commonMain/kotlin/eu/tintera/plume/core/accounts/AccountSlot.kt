package eu.tintera.plume.core.accounts

import kotlinx.serialization.Serializable

/**
 * Místní id účtu v aplikaci (návrh `docs/navrh-vice-uctu.md`, „Identita účtu: slot").
 *
 * Vzniká při přidání účtu, tedy dřív, než je známé user id, a podle něj se jmenuje všechno, co
 * účtu patří na disku a v úložišti tajemství: adresáře TDLib ([directory]) a klíč databáze
 * ([secretName]). User id se ke slotu doplní až po přihlášení.
 *
 * **Dnešní instalace je [Primary] na dnešních cestách.** Primární slot nemá v názvech nic navíc
 * (`tdlib`, `tdlib-files`, alias `plume_tdlib_database`), takže se kvůli víc účtům nic
 * nepřesouvá ani nepřejmenovává: gigabajty databáze zůstanou, kde jsou, a klíč k nim taky.
 * Další sloty dostanou vlastní podadresář a příponu.
 *
 * Id smí obsahovat jen malá písmena a číslice: skládá se do cest, aliasů v Keystore a jmen
 * v Klíčence, kde by lomítko nebo tečka udělaly něco jiného, než se čeká.
 */
@Serializable
data class AccountSlot(val id: String) {

    init {
        require(id.isNotEmpty() && id.all { it in 'a'..'z' || it in '0'..'9' }) { "Neplatné id slotu účtu: \"$id\"" }
    }

    val isPrimary: Boolean get() = this == Primary

    /**
     * Cesta k adresáři [name] tohoto účtu, relativně k datům aplikace: u primárního slotu
     * beze změny (`tdlib`), u ostatních `accounts/<id>/tdlib`.
     */
    fun directory(name: String): String = if (isPrimary) name else "$ACCOUNTS_DIRECTORY/$id/$name"

    /**
     * Jméno tajemství (alias v Keystore, soubor s obaleným klíčem, účet v Klíčence) tohoto účtu:
     * u primárního slotu [base] beze změny, u ostatních s příponou `<separator><id>`.
     */
    fun secretName(base: String, separator: String = "_"): String = if (isPrimary) base else "$base$separator$id"

    companion object {
        /** Účet, se kterým aplikace pracovala odjakživa — dnes jediný. */
        val Primary = AccountSlot("0")
    }
}

/** Adresář s daty dalších účtů v datech aplikace (primární účet v něm není, viz [AccountSlot]). */
const val ACCOUNTS_DIRECTORY = "accounts"
