package eu.tintera.plume.core.language

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

/**
 * Jazyk, ve kterém aplikace zrovna čte své prostředky, a hlášení o jeho změně.
 *
 * Text, který se skládá **mimo kompozici** (`getString` při převodu zpráv v `core/telegram`:
 * servisní oznámení, náhled poslední zprávy, „Fotka"), vzniká v jazyce platném v okamžiku
 * převodu a drží se v paměti jako hotový řetězec. Po přepnutí jazyka by zůstal ve starém, dokud
 * se zprávy nepřevedou znovu — a přesně to na tenhle signál dělají ti, kdo je drží.
 *
 * Jazyk hlásí kořen kompozice ([report]): na Androidu se po změně jazyka Activity skládá znovu
 * a jazyk se čte z téhož zdroje jako `getString` (`Locale.getDefault()`). iOS aplikaci po změně
 * jazyka v Nastavení ukončí, takže tam změna nenastane nikdy.
 */
class AppLanguage {

    private val current = MutableStateFlow<String?>(null)

    /**
     * Ozve se při každé ZMĚNĚ jazyka, ne při prvním hlášení — to je jazyk, ve kterém se všechno
     * převádělo od začátku. Nový odběratel dostane jen změny, které přijdou po něm.
     */
    val changes: Flow<Unit> = current.filterNotNull().drop(1).map { }

    /** Nahlásí jazyk (tag BCP 47), ve kterém se zrovna kreslí. Tentýž jazyk podruhé nic nespustí. */
    fun report(languageTag: String) {
        current.value = languageTag
    }
}
