package eu.tintera.plume.features.profile

import eu.tintera.plume.core.telegram.RelatedChat
import eu.tintera.plume.core.telegram.SharedMediaCounts
import eu.tintera.plume.core.telegram.SharedMediaKind
import kotlin.test.Test
import kotlin.test.assertEquals

class ProfileTabsTest {

    private val group = RelatedChat(chatId = -1, title = "Skupina", photoFileId = null, memberCount = 3, isChannel = false)

    @Test
    fun gifyAKoleckaJsouJenKdyzVChatuJsou() {
        val state = ChatProfileState(counts = SharedMediaCounts(media = 1, files = 0, links = 0, gifs = 2, videoNotes = 0))
        assertEquals(listOf(ProfileTab.Shared(SharedMediaKind.Media), ProfileTab.Shared(SharedMediaKind.Gifs)), state.tabs)
    }

    /** Společné skupiny dorazí samostatným dotazem — patří na konec, aby neposunuly vybranou záložku. */
    @Test
    fun spolecneSkupinyJsouNaKonciAJenNeprazdne() {
        val counts = SharedMediaCounts(media = 1, files = 0, links = 0)
        assertEquals(
            listOf(ProfileTab.Shared(SharedMediaKind.Media), ProfileTab.CommonGroups),
            ChatProfileState(counts = counts, commonGroups = listOf(group)).tabs,
        )
        assertEquals(listOf(ProfileTab.Shared(SharedMediaKind.Media)), ChatProfileState(counts = counts, commonGroups = emptyList()).tabs)
    }

    @Test
    fun dokudNedorazilyPoctyNeniZadnaZalozka() {
        assertEquals(emptyList(), ChatProfileState(commonGroups = listOf(group)).tabs)
    }
}
