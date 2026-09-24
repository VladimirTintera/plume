package eu.tintera.plume.features.profile

import eu.tintera.plume.core.telegram.MAX_MEMBERS_REQUEST
import eu.tintera.plume.core.telegram.MEMBERS_PAGE_SIZE
import eu.tintera.plume.core.telegram.MemberPage
import eu.tintera.plume.core.telegram.MemberRole
import eu.tintera.plume.core.telegram.ProfileMember
import eu.tintera.plume.core.telegram.SenderInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MemberListTest {
    private fun member(id: Long, role: MemberRole = MemberRole.Member) =
        ProfileMember(SenderInfo(id = id, title = "Člen $id", avatarFileId = null), role)

    private fun page(vararg ids: Long, next: Int?) = MemberPage(ids.map { member(it) }, next)

    private fun MemberList.ids() = members?.map { it.sender.id }

    @Test
    fun dalsiStrankaSePripoji() {
        val list = MemberList().withFirstPage("", page(1, 2, next = 2))
            .withNextPage("", offset = 2, page = page(3, 4, next = 4))
        assertEquals(listOf(1L, 2L, 3L, 4L), list.ids())
        assertEquals(4, list.nextOffset)
    }

    @Test
    fun spravceZPrvniStrankyNeniPodruhe() {
        // Správci jsou na první stránce zvlášť a server je vrací i mezi nedávnými.
        val first = MemberPage(listOf(member(9, MemberRole.Owner), member(1)), nextOffset = 1)
        val list = MemberList().withFirstPage("", first)
            .withNextPage("", offset = 1, page = page(9, 2, next = null))
        assertEquals(listOf(9L, 1L, 2L), list.ids())
        // Zůstává ten první výskyt — ten s rolí.
        assertEquals(MemberRole.Owner, list.members!!.first().role)
    }

    @Test
    fun strankaKJinemuDotazuSeZahodi() {
        val list = MemberList().withFirstPage("jana", page(1, next = 1))
        assertSame(list, list.withNextPage("ja", offset = 1, page = page(2, next = null)))
    }

    @Test
    fun strankaKJinemuPosunuSeZahodi() {
        // Seznam se mezitím obnovil a čte se odjinud; stará stránka by v něm udělala díru.
        val list = MemberList().withFirstPage("", page(1, 2, 3, next = 3))
        assertSame(list, list.withNextPage("", offset = 1, page = page(4, next = null)))
    }

    @Test
    fun prvniStrankaZrusiRozdelaneDotahovani() {
        val loading = MemberList().withFirstPage("", page(1, next = 1)).copy(loadingMore = true)
        val list = loading.withFirstPage("jana", page(5, next = null))
        assertFalse(list.loadingMore)
        assertEquals("jana", list.query)
        assertEquals(listOf(5L), list.ids())
    }

    @Test
    fun dotahujeSeJenSeZbylymiStrankami() {
        assertFalse(MemberList().canLoadMore, "první stránka se ještě načítá")
        assertTrue(MemberList().withFirstPage("", page(1, next = 1)).canLoadMore)
        assertFalse(MemberList().withFirstPage("", page(1, next = null)).canLoadMore)
        assertFalse(MemberList().withFirstPage("", page(1, next = 1)).copy(loadingMore = true).canLoadMore)
    }

    @Test
    fun selhanaStrankaDotahovaniUkonci() {
        val list = MemberList().withFirstPage("", page(1, next = 1)).copy(loadingMore = true)
            .withFailedPage("", offset = 1)
        assertNull(list.nextOffset)
        assertFalse(list.loadingMore)
        assertEquals(listOf(1L), list.ids())
    }

    @Test
    fun obnovaCteTolikKolikUzBylo() {
        assertEquals(MEMBERS_PAGE_SIZE, MemberList().reloadLimit)
        assertEquals(150, MemberList().withFirstPage("", page(1, next = 150)).reloadLimit)
        // Víc server naráz nevydá.
        assertEquals(MAX_MEMBERS_REQUEST, MemberList().withFirstPage("", page(1, next = 900)).reloadLimit)
    }
}
