package eu.tintera.plume.core.telegram

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class TdlibJsonRepairTest {

    // Tvar odpovědi na getAvailableGifts, se kterým tdl-coroutines 15.0.0 shodil aplikaci.
    private val availableGifts =
        """{"@type":"availableGifts","gifts":[""" +
            """{"@type":"availableGift","gift":{"@type":"gift","id":"5170145012310081615",""" +
            """"star_count":15,"has_colors":false},"resale_count":0},""" +
            """{"@type":"availableGift","gift":{"@type":"gift","id":"5170233102089322756",""" +
            """"star_count":25,"background":{"@type":"giftBackground","center_color":1,""" +
            """"edge_color":2,"text_color":3}},"resale_count":0}],"@client_id":1,"@extra":7}"""

    @Test
    fun darekBezPozadiDostaneVychoziPozadi() {
        val gifts = Json.parseToJsonElement(TdlibJsonRepair.repair(availableGifts))
            .jsonObject.getValue("gifts").jsonArray
            .map { it.jsonObject.getValue("gift").jsonObject }

        val doplnene = gifts[0].getValue("background").jsonObject
        assertEquals("giftBackground", doplnene.getValue("@type").jsonPrimitive.content)
        assertEquals("0", doplnene.getValue("center_color").jsonPrimitive.content)
        // Pozadí, které TDLib poslal, zůstane, jak bylo.
        assertEquals("1", gifts[1].getValue("background").jsonObject.getValue("center_color").jsonPrimitive.content)
    }

    @Test
    fun zbytekOdpovediZustaneBezeZmeny() {
        val repaired = Json.parseToJsonElement(TdlibJsonRepair.repair(availableGifts)).jsonObject
        // int64 chodí jako řetězec a nesmí se cestou převést na číslo.
        assertEquals(
            "\"5170145012310081615\"",
            repaired.getValue("gifts").jsonArray[0].jsonObject.getValue("gift").jsonObject.getValue("id").toString(),
        )
        assertEquals("7", repaired.getValue("@extra").jsonPrimitive.content)
        assertEquals(
            Json.parseToJsonElement(availableGifts).jsonObject.keys,
            repaired.keys,
        )
    }

    /** Běžná odpověď se neparsuje: projde ta samá instance. */
    @Test
    fun odpovedBezDarkuProjdeNetknuta() {
        val json = """{"@type":"updateChatTitle","chat_id":1,"title":"gift"}"""
        assertSame(json, TdlibJsonRepair.repair(json))
    }

    /** `giftBackground` samotný není dárek. */
    @Test
    fun jinyTypSeStejnymZacatkemSeNeopravuje() {
        val json = """{"@type":"giftBackground","center_color":1,"edge_color":2,"text_color":3}"""
        assertSame(json, TdlibJsonRepair.repair(json))
    }

    @Test
    fun uplnyDarekSeNeprepisuje() {
        val json = """{"@type":"gift","background":{"@type":"giftBackground","center_color":5}}"""
        assertSame(json, TdlibJsonRepair.repair(json))
    }

    /** Chyba opravy nesmí vyletět: na přijímacím vlákně by shodila aplikaci. */
    @Test
    fun rozbityJsonProjdeBezVyjimky() {
        val json = """{"@type":"gift","id":"""
        assertSame(json, TdlibJsonRepair.repair(json))
    }

    /** Dárek uvnitř zprávy, tedy cesta, kterou by spadl skutečný chat s dárkem. */
    @Test
    fun darekVeZpraveSeOpravi() {
        val message =
            """{"@type":"message","id":1048576,"content":{"@type":"messageGift",""" +
                """"gift":{"@type":"gift","id":"1","star_count":50},"text":{"@type":"formattedText","text":""}}}"""
        val gift = Json.parseToJsonElement(TdlibJsonRepair.repair(message))
            .jsonObject.getValue("content").jsonObject.getValue("gift").jsonObject
        assertEquals("giftBackground", gift.getValue("background").jsonObject.getValue("@type").jsonPrimitive.content)
    }
}
