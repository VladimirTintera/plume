package eu.tintera.plume.features.chats

import kotlin.math.min

/**
 * O kolik se musí chat o šířce [width] odtáhnout, aby ho puštění archivovalo. Nejvýš [cap] —
 * na široké ploše (tablet, dvoupanel) by podíl šířky znamenal tah přes půl obrazovky.
 *
 * Nulová šířka (první snímek, než je položka změřená) znamená prah, kterého nejde dosáhnout:
 * tah, o kterém nevíme, jak je dlouhý, nesmí chat archivovat.
 */
internal fun archiveSwipeThreshold(width: Float, cap: Float): Float =
    if (width <= 0f) Float.POSITIVE_INFINITY else min(width * COMMIT_FRACTION, cap)

/**
 * Podíl šířky řádku, o který se musí chat odtáhnout.
 *
 * `SwipeToDismissBox` měl 56 dp a k tomu zkratku přes rychlost: tah rychlejší než 125 dp/s
 * archivoval bez ohledu na to, jak daleko došel. Právě ta zkratka dělala z každého škubnutí
 * archivaci — proto se tu rychlost nepočítá vůbec a rozhoduje jen ušlá vzdálenost.
 */
private const val COMMIT_FRACTION = 0.4f
