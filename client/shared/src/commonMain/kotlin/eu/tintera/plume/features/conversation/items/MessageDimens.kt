package eu.tintera.plume.features.conversation.items

import androidx.compose.ui.unit.dp

/** Mezera navíc tam, kde začíná nová skupina — jiný odesílatel, jiný blok. */
internal val GROUP_SPACING = 8.dp

/** Zaoblení bubliny. Sdílí ho i startovní strana letu, aby se tvar po cestě neměnil. */
internal val BUBBLE_CORNER = 18.dp

/** Stažené nároží MEZI navazujícími bublinami téhož odesílatele. */
internal val BUBBLE_TIGHT = 4.dp

/**
 * Vnitřní odsazení dílů zprávy: 14 dp do stran, 8 dp nahoru a dolů.
 *
 * Jedna anatomie pro všechny karty konverzace — bublinu, citaci nad odpovědí, popis webu
 * u náhledu odkazu i pás citace v textu. Bylo to opsané v pěti souborech a rozcházelo se to;
 * u citace v textu navíc obě čísla musí sedět PŘESNĚ, protože pás dosedá na okraj bubliny
 * (do stran) a jeho vnitřní odsazení má vypadat stejně jako u dílu nad ním a pod ním.
 */
internal val MESSAGE_PADDING_H = 14.dp

internal val MESSAGE_PADDING_V = 8.dp

/**
 * Šířka sloupce s časy, který se odkrývá tahem doleva. Vejde se do něj „00:00" s rezervou.
 */
internal val TIME_COLUMN_WIDTH = 56.dp

/** Nad tímhle počtem už emoji netvoří gesto, ale odstavec, a kreslí se normálně velké. */
internal const val MAX_LARGE_EMOJI = 3

/** Rozestup mezi zprávami téhož odesílatele. Patří k sobě, tak se drží u sebe. */
internal val MESSAGE_SPACING = 4.dp

/**
 * Nejvyšší šířka zprávy jako podíl řádku — pro KAŽDÝ druh bloku, ne jen pro text.
 *
 * Zpráva přes celou šířku nechá na obou stranách stejný okraj, takže se strany konverzace poznají
 * jen podle barvy. U textu to bylo nepříjemné, u fotky je to slepá ulička: holý snímek se kreslí
 * na plochu bez bubliny (viz `showsBareMedia`), takže **žádnou barvu, podle které by se strana
 * poznala, nemá** — u obrázku přes celý řádek nešlo na první pohled říct, kdo ho poslal.
 *
 * Volný pruh na protější straně je proto pravidlo rámu, ne vlastnost jednoho bloku: nese ho
 * výchozí `MessageItem.maxWidthFraction`, takže nový druh zprávy ho dostane sám od sebe a šířka
 * přes celý řádek je vědomá výjimka. Google Zprávy i Telegram drží podobný okraj.
 */
internal const val MESSAGE_MAX_WIDTH_FRACTION = 0.82f
