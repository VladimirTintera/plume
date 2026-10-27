package eu.tintera.plume.features.conversation

/*
 * Návrat po skoku na citaci.
 *
 * Ťuknutí na citaci odveze uživatele hluboko do historie a tlačítko dolů ho dřív vracelo až na
 * konec chatu — zprávu, od které vyrazil, musel hledat rolováním. Jako v Telegramu si skok
 * pamatuje, ODKUD vyšel, a tlačítko dolů vrací napřed tam. Skoky jdou řetězit (citace v citované
 * zprávě), návraty se odebírají od posledního.
 *
 * Bod návratu zmizí, jakmile zpráva, od které se skákalo, znovu je vidět — nebo cokoli novějšího:
 * pak ji uživatel míjí nebo už minul a vracet ho k ní by bylo proti směru čtení. Rozhoduje to
 * obrazovka až PO doskočení skoku (`scrollTarget` i `pendingJump` prázdné): v okamžiku ťuknutí
 * je zdrojová zpráva vidět vždycky, a bod by zmizel dřív, než se kamkoli jelo.
 */

/** Přidá bod návratu na vrchol; byl-li už v zásobníku, přesune se tam. Nejstarší nad strop vypadnou. */
internal fun List<Long>.withReturnPoint(messageId: Long): List<Long> =
    (filter { it != messageId } + messageId).takeLast(MAX_RETURN_POINTS)

/**
 * Body, ke kterým má návrat ještě smysl, když je nejnovější viditelná zpráva [newestVisibleId]:
 * všechny starší nebo stejné uživatel právě vidí nebo je minul.
 */
internal fun List<Long>.notPassed(newestVisibleId: Long): List<Long> = filter { it > newestVisibleId }

/**
 * Kolik skoků zpátky si konverzace pamatuje. Řetěz citací delší než tohle nikdo nepočítá, a bez
 * stropu by zásobník rostl s každým ťuknutím, které se nevrátilo.
 */
private const val MAX_RETURN_POINTS = 20
