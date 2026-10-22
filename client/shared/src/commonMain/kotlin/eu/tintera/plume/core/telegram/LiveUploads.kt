package eu.tintera.plume.core.telegram

/**
 * Nahrávání souboru do Telegramu už během jeho zápisu (`preliminaryUploadFile`) — hlasovka
 * i kolečko se nahrávají, zatímco uživatel mluví, a po puštění tlačítka odejdou skoro hned.
 */
interface LiveUploads {
    /**
     * Začne nahrávat hlasovku [path], kterou nahrávač teprve zapisuje. `null` = nejde (chybí
     * spojení s TDLib); pak se po skončení posílá jako dřív, celým souborem.
     */
    fun startVoiceNote(path: String): LiveUpload?

    /** Totéž pro video kolečko — MP4, které kamera jen připisuje (fragmentované). */
    fun startVideoNote(path: String): LiveUpload?

    /** Předem nahraný soubor [fileId] nikdo neodešle (zahozený náhled, nahrávání dál). */
    fun discard(fileId: Int)
}

/** Jedno rozběhnuté nahrávání ([LiveUploads.startVoiceNote], [LiveUploads.startVideoNote]). */
interface LiveUpload {
    /**
     * Nahrávka je hotová v [finalPath]: dopíše se zbytek a vrátí se id souboru, který se pošle
     * místo cesty. `null` = předem nahrané se použít nedá (jiný soubor, nahrávač přepsal začátek,
     * TDLib generování nezačal) — nahrávání se zruší a posílá se cesta.
     */
    suspend fun complete(finalPath: String): Int?

    /** Nahrávka se zahodila nebo bude jiná (pauza, spojování úseků) — nahrávání se zruší. */
    fun abandon()
}
