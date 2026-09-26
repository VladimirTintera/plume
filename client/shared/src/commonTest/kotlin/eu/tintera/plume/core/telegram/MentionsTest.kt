package eu.tintera.plume.core.telegram

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Rozpoznání zmínky u kurzoru, vložení vybraného člena a zmínky jménem při odeslání. */
class MentionsTest {

    @Test
    fun zminkaUKurzoru() {
        assertEquals(MentionQuery(0, "ja"), mentionQueryAt("@ja", 3))
        assertEquals(MentionQuery(5, "ji"), mentionQueryAt("ahoj @ji", 8))
        // Samotný zavináč: nabídnou se členové bez filtru.
        assertEquals(MentionQuery(5, ""), mentionQueryAt("ahoj @", 6))
        // Diakritika a podtržítko patří do dotazu.
        assertEquals(MentionQuery(0, "Jiří_x"), mentionQueryAt("@Jiří_x", 7))
        // Kurzor uprostřed slova: dotaz je jen část před ním.
        assertEquals(MentionQuery(0, "ja"), mentionQueryAt("@jana", 3))
        // Po zalomení řádku taky.
        assertEquals(MentionQuery(3, "a"), mentionQueryAt("ok\n@a", 5))
    }

    @Test
    fun cokoliJinehoZminkaNeni() {
        // E-mail: zavináč uprostřed slova.
        assertNull(mentionQueryAt("jana@firma", 10))
        // Po mezeře se už píše věta dál.
        assertNull(mentionQueryAt("@jana ahoj", 10))
        assertNull(mentionQueryAt("ahoj", 4))
        assertNull(mentionQueryAt("", 0))
        assertNull(mentionQueryAt("@ja", 0))
        assertNull(mentionQueryAt("@" + "a".repeat(33), 34))
    }

    private val jana = MentionCandidate(1, "Jana Nováková", "Jana", "jana", null)
    private val jakub = MentionCandidate(2, "Jakub Dvořák", "Jakub", null, null)

    @Test
    fun vlozeniUzivatelskehoJmena() {
        val query = mentionQueryAt("ahoj @ja", 8)!!
        assertEquals("ahoj @jana " to 11, mentionInsertion("ahoj @ja", 8, query, jana))
    }

    @Test
    fun vlozeniJmenaBezUzivatelskehoJmena() {
        val query = mentionQueryAt("@ja díky", 3)!!
        // Mezera za kurzorem už je, druhá se nepřidá; kurzor stojí za ní.
        assertEquals("Jakub díky" to 6, mentionInsertion("@ja díky", 3, query, jakub))
    }

    @Test
    fun zminkaJmenemSeNajdeJakoSlovo() {
        val found = nameMentionEntities("Díky Jakub, Jakube!", listOf(NameMention("Jakub", 2)))
        assertEquals(listOf(NameMentionEntity(5, 5, 2)), found)
    }

    @Test
    fun viceZminekVPoradi() {
        val text = "Jakub a Jana, Jakub"
        val found = nameMentionEntities(
            text,
            listOf(NameMention("Jakub", 2), NameMention("Jana", 1), NameMention("Jakub", 2)),
        )
        assertEquals(
            listOf(NameMentionEntity(0, 5, 2), NameMentionEntity(8, 4, 1), NameMentionEntity(14, 5, 2)),
            found,
        )
    }

    @Test
    fun smazaneJmenoAObsazenyUsekSeVynechaji() {
        // Jméno z pole zmizelo.
        assertEquals(emptyList(), nameMentionEntities("ahoj", listOf(NameMention("Jakub", 2))))
        // Uvnitř odkazu (obsazený úsek) zmínka nevznikne, najde se až další výskyt.
        val found = nameMentionEntities(
            "t.me/Jakub Jakub",
            listOf(NameMention("Jakub", 2)),
            occupied = listOf(0 until 10),
        )
        assertEquals(listOf(NameMentionEntity(11, 5, 2)), found)
    }
}
