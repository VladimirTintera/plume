package eu.tintera.plume.core.share

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedStreamsTest {

    private val own = { authority: String -> authority.startsWith("eu.tintera.plume.") }

    @Test
    fun foreignContentIsAllowed() {
        assertTrue(sharedStreamAllowed("content://media/external/images/media/42", own, emptySet()))
        assertTrue(sharedStreamAllowed("content://com.android.providers.downloads.documents/document/7", own, emptySet()))
        assertTrue(sharedStreamAllowed("content://10@media/external/images/media/42", own, emptySet()), "pracovní profil")
    }

    @Test
    fun fileUrisAreRejected() {
        assertFalse(sharedStreamAllowed("file:///data/user/0/eu.tintera.plume/files/tdlib/td.binlog", own, emptySet()))
        assertFalse(sharedStreamAllowed("file:///sdcard/Download/a.pdf", own, emptySet()))
        assertFalse(sharedStreamAllowed("/data/user/0/eu.tintera.plume/files/x", own, emptySet()))
        assertFalse(sharedStreamAllowed("content:///no-authority", own, emptySet()))
    }

    @Test
    fun ownProviderOnlyForWhatPlumeShared() {
        val shared = "content://eu.tintera.plume.documents/shared/photo.jpg"
        assertTrue(sharedStreamAllowed(shared, own, setOf(shared)))
        assertFalse(sharedStreamAllowed("content://eu.tintera.plume.documents/tdlib_files/photos/1.jpg", own, setOf(shared)))
        assertFalse(sharedStreamAllowed("content://0@eu.tintera.plume.documents/tdlib_files/photos/1.jpg", own, setOf(shared)), "číslo uživatele kontrolu neobejde")
        assertFalse(sharedStreamAllowed("CONTENT://eu.tintera.plume.documents/tdlib_files/x", own, emptySet()))
    }
}
