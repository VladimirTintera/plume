package eu.tintera.plume.core.files

/**
 * Předá stažený soubor systému, ať ho otevře aplikace, která na něj je.
 *
 * Vrací `false`, když soubor nemá čím otevřít — uživateli se to musí říct, jinak to vypadá,
 * že ťuknutí nic neudělalo.
 */
expect class FileOpener {
    fun open(path: String, mimeType: String): Boolean
}
