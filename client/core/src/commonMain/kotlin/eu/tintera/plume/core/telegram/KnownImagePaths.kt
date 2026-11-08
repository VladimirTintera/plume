package eu.tintera.plume.core.telegram

/**
 * Poslední známé cesty k obrázkům jednoho účtu.
 *
 * `RemoteImage` startuje stav souboru vždycky na [FileState.Pending] — flow odpovídá až
 * z korutiny, takže každý nově složený obrázek proletí aspoň jedním snímkem bez obrázku. U
 * sdíleného prvku ze seznamu do konverzace je to vidět: avatar odletí jako prázdný kruh a obrázek
 * naskočí až na místě. Odtud si vezme cestu, kterou už jednou viděl.
 *
 * **Patří účtu** (kontejner účtu, `accountModule`): `fileId` je číslo jednoho klienta TDLib, dva
 * účty by si pod stejným id podstrčily cizí obrázek.
 *
 * Čte se i zapisuje výhradně z hlavního vlákna (kompozice), takže nepotřebuje zámek. Cesty
 * v TDLib jednou hotovému souboru nemizí, takže se za běhu nic nezneplatňuje; roste to o jeden
 * krátký řetězec na zobrazený soubor. Výjimky jsou odhlášení a úklid úložiště, viz [forget].
 */
class KnownImagePaths {

    private val paths = mutableMapOf<Int, String>()

    operator fun get(fileId: Int): String? = paths[fileId]

    operator fun set(fileId: Int, path: String) {
        paths[fileId] = path
    }

    /**
     * Zahodí známé cesty: po odhlášení dostane další přihlášený účet tytéž `fileId` pro jiné
     * soubory, po úklidu úložiště by cesty vedly na smazané soubory. Volat z hlavního vlákna.
     */
    fun forget() = paths.clear()
}
