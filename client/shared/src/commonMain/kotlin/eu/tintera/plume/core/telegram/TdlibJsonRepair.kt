package eu.tintera.plume.core.telegram

import co.touchlab.kermit.Logger
import kotlin.concurrent.Volatile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * Doplní do odpovědi TDLib pole, která TDLib v praxi vynechává, přestože je dokumentace vede
 * jako povinná — DŘÍV, než odpověď dostane parser `tdl-coroutines`.
 *
 * Wrapper si nullabilitu pole odvozuje jen z textu dokumentace v `td_api.tl` („may be null",
 * „pass null"). Povinné objektové pole čte `getValue`, takže chybějící klíč vyhodí
 * `NoSuchElementException` na vlákně `TDL-Receiver-Thread`. Tam ji nikdo nechytá: přijímací
 * smyčka skončí a výjimka shodí celý proces. Viz „Dárek" v CLAUDE.md.
 *
 * Na Androidu se volá z bajtkódu wrapperu (`TdlibReceiveHook`, instrumentace v
 * `client/androidApp/build.gradle.kts`), na iOS zatím nijak — Kotlin/Native knihovnu přepsat
 * nedovolí.
 */
object TdlibJsonRepair {

    /**
     * Typ objektu → chybějící pole → co doplnit. Do tabulky patří jen pole ověřená ve zdroji
     * TDLib (konstruktor dostane `nullptr`) nebo pádem — nic preventivně.
     */
    private val defaults: Map<String, Map<String, JsonObject>> = mapOf(
        // `StarGift::get_gift_object` nechá pozadí prázdné, když ho server k dárku nepošle.
        // Aplikace `Gift.background` nečte, takže na hodnotě nezáleží; jen musí být platná.
        "gift" to mapOf(
            "background" to buildJsonObject {
                put("@type", "giftBackground")
                put("center_color", 0)
                put("edge_color", 0)
                put("text_color", 0)
            },
        ),
    )

    // TDLib píše JSON bez mezer, takže typ se pozná podřetězcem a zbytek odpovědí se neparsuje.
    // Uzavírací uvozovka je součást vzoru: `"@type":"gift"` nesmí chytit `giftBackground`.
    private val markers = defaults.keys.map { "\"@type\":\"$it\"" }

    private val logger = Logger.withTag("TdlibJsonRepair")

    @Volatile
    private var reported = emptySet<String>()

    /** Jestli opravou prošla aspoň jedna odpověď — tedy jestli je zapojená. */
    @Volatile
    var isHooked: Boolean = false
        private set

    /** Nikdy nevyhodí: chyba tady by shodila přijímací smyčku stejně jako ta, které brání. */
    fun repair(json: String): String {
        isHooked = true
        if (markers.none { json.contains(it) }) return json
        return try {
            val repaired = Repairing()
            val root = repaired.visit(Json.parseToJsonElement(json))
            if (repaired.filled.isEmpty()) {
                json
            } else {
                report(repaired.filled)
                root.toString()
            }
        } catch (e: Exception) {
            logger.w(e) { "Odpověď TDLib se nepodařilo opravit." }
            json
        }
    }

    private class Repairing {
        val filled = mutableSetOf<String>()

        fun visit(element: JsonElement): JsonElement = when (element) {
            is JsonObject -> visitObject(element)
            is JsonArray -> JsonArray(element.map(::visit))
            is JsonPrimitive -> element
        }

        private fun visitObject(element: JsonObject): JsonObject {
            val children = element.mapValues { (_, value) -> visit(value) }
            val type = (element["@type"] as? JsonPrimitive)?.contentOrNull
            val missing = defaults[type]?.filterKeys { it !in children }.orEmpty()
            missing.keys.forEach { filled += "$type.$it" }
            return JsonObject(children + missing)
        }
    }

    /** Jednou za pole a běh procesu: dárků bývá v historii hodně. */
    private fun report(fields: Set<String>) {
        val fresh = fields - reported
        if (fresh.isEmpty()) return
        reported = reported + fresh
        logger.i { "TDLib vynechal povinné pole, doplněno: ${fresh.joinToString()}" }
    }
}

/**
 * Jestli na téhle platformě odpovědi TDLib [TdlibJsonRepair] opravdu procházejí. Když ano
 * a po přihlášení přesto [TdlibJsonRepair.isHooked] neplatí, instrumentace se rozpojila
 * (bump wrapperu, přejmenování) — hlásí to `TdlTelegramSession`.
 */
internal expect val tdlibJsonRepairWired: Boolean
