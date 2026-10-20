package eu.tintera.plume.core.calls

import eu.tintera.plume.core.telegram.callDebugInformation
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

/** Co jde Telegramu, když po hovoru požádá o ladicí data. */
class CallDiagnosticsTest {

    @Test
    fun bereSeJenPosledniHovorSTimtoCislem() {
        // TDLib čísluje hovory v každém spuštění od jedničky: v deníku jsou dva hovory „[1]".
        val lines = listOf(
            "10:00 [1] Pending",
            "10:01 [1] Over: Ended",
            "11:00 [1] Pending",
            "11:00 [1] Ready: version 8.0.0",
            "11:05 [1] Over: Ended",
        )
        assertEquals(lines.drop(2), CallJournal.lastCall(lines))
    }

    @Test
    fun bezKonceSeBereVsechno() {
        val lines = listOf("10:00 [2] Pending", "10:00 [2] Ready")
        assertEquals(lines, CallJournal.lastCall(lines))
        assertEquals(emptyList(), CallJournal.lastCall(emptyList()))
    }

    @Test
    fun ladiciDataNesouKlientaVerziADenik() {
        val json = Json.parseToJsonElement(callDebugInformation("1.2.3", listOf("11:00 [1] Pending"))).jsonObject
        assertEquals("Plume", json["client"]?.jsonPrimitive?.content)
        assertEquals("1.2.3", json["version"]?.jsonPrimitive?.content)
        assertEquals(listOf("11:00 [1] Pending"), json["journal"]?.jsonArray?.map { it.jsonPrimitive.content })
        assertEquals(setOf("client", "version", "device", "system", "journal"), json.keys)
    }
}
