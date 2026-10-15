package eu.tintera.plume.features.conversation

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.tintera.plume.features.conversation.items.MenuSlot

/**
 * Nabídka nad zprávou rozdělená do dvou úrovní (viz [MenuSlot]).
 *
 * [header] je položka hlavičky (Informace), [top] skupiny první úrovně bez nevratných akcí,
 * [destructive] skupina nevratných akcí (kreslí se nahoře úplně dole), [more] skupiny pod „Další".
 * Prázdná [more] = řádek „Další" se nekreslí.
 */
internal data class MenuLevels<T>(
    val header: T?,
    val top: List<List<T>>,
    val destructive: List<T>,
    val more: List<List<T>>,
) {
    /**
     * Počty řádků ve skupinách první úrovně, jak se kreslí: hlavička leží v první skupině (její
     * výška se počítá zvlášť, viz [menuLevelHeight]), „Další" má skupinu vlastní.
     */
    val topGroupSizes: List<Int>
        get() = buildList {
            if (top.isEmpty()) add(0) else top.forEach { add(it.size) }
            if (more.isNotEmpty()) add(1)
            if (destructive.isNotEmpty()) add(destructive.size)
        }

    /** Totéž pro „Další": řádek zpět leží v první skupině a počítá se jako položka. */
    val moreGroupSizes: List<Int>
        get() = more.mapIndexed { index, group -> group.size + if (index == 0) 1 else 0 }
}

/**
 * Rozdělí ukázané položky do skupin a úrovní podle [slot]. Pořadí skupin dává pořadí [MenuSlot],
 * uvnitř skupiny zůstává pořadí [entries].
 *
 * **Jediná položka pod „Další" jde nahoru.** Řádek „Další" a pak řádek zpět kvůli jedné položce
 * by byly dva kroky navíc, a nahoře zabere tentýž řádek jako „Další" sám (Uložené zprávy,
 * zprávy, u kterých server skoro nic nedovolí).
 */
internal fun <T> arrangeMenu(entries: List<T>, slot: (T) -> MenuSlot): MenuLevels<T> {
    val bySlot = entries.groupBy(slot)
    fun groups(predicate: (MenuSlot) -> Boolean) =
        MenuSlot.entries.filter(predicate).mapNotNull { bySlot[it]?.takeIf(List<T>::isNotEmpty) }

    val top = groups { it != MenuSlot.Header && it != MenuSlot.Destructive && !it.inMore }
    val more = groups { it.inMore }
    val promote = more.sumOf { it.size } <= 1
    return MenuLevels(
        header = bySlot[MenuSlot.Header]?.firstOrNull(),
        top = if (promote) top + more else top,
        destructive = bySlot[MenuSlot.Destructive].orEmpty(),
        more = if (promote) emptyList() else more,
    )
}

/**
 * Výška jedné úrovně nabídky ze skupin a jejich řádků. **Konstanty, ne měření** (viz
 * „Výšky obou kusů jsou konstanty" v `docs/poznatky/konverzace-akce.md`): nabídka se rozmístí
 * dřív, než se poprvé složí, takže nepřeskočí na správné místo o snímek později.
 *
 * Hodnoty jsou z M3 (`DropdownMenuGroup`: svislé odsazení 2 dp, mezera mezi skupinami 2 dp);
 * položka má pevnou výšku [MENU_ITEM_HEIGHT], aby výška nezávisela na velikosti písma.
 */
internal fun menuLevelHeight(groupSizes: List<Int>, header: Boolean): Dp {
    if (groupSizes.isEmpty()) return if (header) MENU_HEADER_HEIGHT + MENU_GROUP_PADDING * 2 else 0.dp
    val groups = groupSizes.fold(0.dp) { sum, rows -> sum + MENU_GROUP_PADDING * 2 + MENU_ITEM_HEIGHT * rows }
    return groups + MENU_GROUP_GAP * (groupSizes.size - 1) + if (header) MENU_HEADER_HEIGHT else 0.dp
}

/** Výška položky. M3 dává 44 dp (`SegmentedMenuTokens.Item`). */
internal val MENU_ITEM_HEIGHT = 44.dp

/** Hlavička s časem a doručením. Nižší než položka, ať se nemyslí, že je to další akce. */
internal val MENU_HEADER_HEIGHT = 36.dp

/** Svislé odsazení uvnitř skupiny (`DropdownMenuGroupVerticalPadding`, interní v material3). */
internal val MENU_GROUP_PADDING = 2.dp

/** Mezera mezi skupinami (`MenuDefaults.GroupSpacing`). */
internal val MENU_GROUP_GAP = 2.dp
