package eu.tintera.plume.core.share

import android.content.Context
import android.net.Uri
import android.os.Process

/**
 * URI z vlastního poskytovatele, která Plume sám poslal ven přes výběr aplikací
 * (`MediaSharer.share`). Jen ta smí přijít zpátky do sdílení — viz [sharedStreamAllowed].
 *
 * V paměti procesu: sdílení ven a příjem téhož souboru (uživatel ve výběru vybere Plume) jsou
 * jedna akce těsně po sobě. Drží se posledních pár, `MediaSharer` stejně při dalším sdílení
 * starou kopii maže.
 */
internal object OwnShares {

    private val uris = ArrayDeque<String>()

    fun remember(uri: Uri) = synchronized(uris) {
        uris.remove(uri.toString())
        uris.addLast(uri.toString())
        while (uris.size > LIMIT) uris.removeFirst()
    }

    fun current(): Set<String> = synchronized(uris) { uris.toSet() }

    private const val LIMIT = 8
}

/** Patří poskytovatel s touto autoritou Plume? Cizí, neviditelný i neexistující je „ne". */
internal fun isOwnAuthority(context: Context, authority: String): Boolean {
    val provider = runCatching { context.packageManager.resolveContentProvider(authority, 0) }.getOrNull() ?: return false
    return provider.packageName == context.packageName || provider.applicationInfo?.uid == Process.myUid()
}

/**
 * Smí Plume přečíst [uri], které mu podala jiná aplikace — sdílení, klávesnice, schránka,
 * přetažení? Všechny čtou pod identitou Plume, a tak by jimi prošly i jeho soukromé soubory.
 * Viz [sharedStreamAllowed].
 */
internal fun foreignUriAllowed(context: Context, uri: Uri): Boolean =
    sharedStreamAllowed(uri.toString(), { authority -> isOwnAuthority(context, authority) }, OwnShares.current())
