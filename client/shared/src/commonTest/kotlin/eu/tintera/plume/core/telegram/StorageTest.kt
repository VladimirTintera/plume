package eu.tintera.plume.core.telegram

import kotlin.test.Test
import kotlin.test.assertEquals

class StorageTest {

    private fun chat(id: Long, bytes: Long, vararg parts: CategoryUsage) =
        ChatStorage(chatId = id, title = "", avatarFileId = null, kind = ChatKind.Private, bytes = bytes, categories = parts.toList())

    @Test
    fun kategorieSeSectouAPrazdnePryc() {
        val merged = listOf(
            CategoryUsage(StorageCategory.Videos, 100, 1),
            CategoryUsage(StorageCategory.Photos, 30, 2),
            CategoryUsage(StorageCategory.Photos, 20, 3),
            CategoryUsage(StorageCategory.Stickers, 0, 0),
        ).merged()

        assertEquals(
            listOf(
                CategoryUsage(StorageCategory.Videos, 100, 1),
                CategoryUsage(StorageCategory.Photos, 50, 5),
            ),
            merged,
        )
    }

    /** Stejná velikost: rozhoduje pořadí kategorií, aby se řádky nepřehazovaly. */
    @Test
    fun shodnaVelikostDrziPoradiKategorii() {
        val merged = listOf(
            CategoryUsage(StorageCategory.Files, 10, 1),
            CategoryUsage(StorageCategory.Photos, 10, 1),
        ).merged()

        assertEquals(listOf(StorageCategory.Photos, StorageCategory.Files), merged.map { it.category })
    }

    @Test
    fun zbytekJeJedenRadekNaKonci() {
        val ordered = listOf(
            chat(OTHER_CHATS, 500, CategoryUsage(StorageCategory.Photos, 500, 1)),
            chat(1, 100),
            chat(2, 300),
            chat(OTHER_CHATS, 200, CategoryUsage(StorageCategory.Photos, 150, 1), CategoryUsage(StorageCategory.Other, 50, 1)),
            chat(3, 0),
        ).ordered()

        assertEquals(listOf(2L, 1L, OTHER_CHATS), ordered.map { it.chatId })
        val rest = ordered.last()
        assertEquals(700, rest.bytes)
        assertEquals(
            listOf(CategoryUsage(StorageCategory.Photos, 650, 2), CategoryUsage(StorageCategory.Other, 50, 1)),
            rest.categories,
        )
    }

    @Test
    fun vychoziMazaniNechaSamolepkyAOstatni() {
        assertEquals(
            setOf(
                StorageCategory.Photos, StorageCategory.Videos, StorageCategory.VoiceMessages,
                StorageCategory.Music, StorageCategory.Files, StorageCategory.Gifs,
            ),
            DEFAULT_CLEAR_CATEGORIES,
        )
    }
}
