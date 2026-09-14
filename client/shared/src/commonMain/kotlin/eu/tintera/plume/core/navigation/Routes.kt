package eu.tintera.plume.core.navigation

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/**
 * Navigační klíče.
 *
 * `@Serializable` je tu kvůli `rememberNavBackStack`, který zásobník ukládá přes SavedState —
 * bez toho by se po zabití procesu uživatel vrátil na kořen místo tam, kde byl.
 */
sealed interface Route : NavKey

/** Sekce dosažitelné z railu/baru. Zásobník má vždycky právě jednu na dně. */
sealed interface TopLevelRoute : Route

@Serializable
data object ChatsRoute : TopLevelRoute

@Serializable
data object SettingsRoute : TopLevelRoute

/** Konverzace. V list-detail rozvržení je to detail panel vedle seznamu. */
@Serializable
data class ConversationRoute(val chatId: Long) : Route

/**
 * Konfigurace pro ukládání navigačního zásobníku.
 *
 * **Každá nová routa se MUSÍ zaregistrovat níže.** `NavKey` je otevřená polymorfní hierarchie
 * z cizí knihovny, takže si kotlinx.serialization podtypy sama neodvodí — `rememberNavBackStack`
 * bez registrace **spadne hned při první kompozici** hláškou „You must pass a
 * `SavedStateConfiguration.serializersModule` configured to handle `NavKey` open polymorphism".
 *
 * Pozor, je to past: pád nastane až za běhu, kompilace projde. Nová routa bez řádku níž
 * tedy projde buildem i review a shodí appku až na zařízení.
 *
 * `SerializersModule` je vložený přímo tady, ne v samostatné `private val` nad tímhle — top-level
 * hodnoty se inicializují v pořadí deklarace, takže odkaz na property deklarovanou níž by tu byl
 * `null` a konstrukce by spadla na `NullPointerException`.
 */
val NavSavedStateConfiguration: SavedStateConfiguration = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(ChatsRoute::class, ChatsRoute.serializer())
            subclass(SettingsRoute::class, SettingsRoute.serializer())
            subclass(ConversationRoute::class, ConversationRoute.serializer())
        }
    }
}
