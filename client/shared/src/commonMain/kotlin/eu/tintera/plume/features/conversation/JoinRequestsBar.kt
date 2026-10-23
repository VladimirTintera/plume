package eu.tintera.plume.features.conversation

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.PendingJoinRequests
import eu.tintera.plume.core.ui.FaceStack
import eu.tintera.plume.core.ui.grouped
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.close_24dp
import eu.tintera.plume.resources.join_requests_bar
import eu.tintera.plume.resources.join_requests_bar_hide
import eu.tintera.plume.resources.join_requests_bar_more
import eu.tintera.plume.resources.person_add_24dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Čekající žádosti o vstup nad konverzací skupiny nebo kanálu — tvarem i sklem jako pruh
 * videochatu a připnutých. Ťuknutí otevře žádosti ([onClick]), ✕ pruh schová ([onHide]).
 *
 * - **Tváře posledních žadatelů místo ikony**, jako čip komentářů: kdo chce dovnitř, je to
 *   hlavní, co správce potřebuje vědět, a ze jmen pod nadpisem se to čte pomaleji.
 * - **Jména jen tři, zbytek „a další"** — počet nese nadpis.
 */
@Composable
internal fun JoinRequestsBar(
    requests: PendingJoinRequests,
    onClick: () -> Unit,
    onHide: () -> Unit,
    /** Mléčné sklo nad seznamem: plocha je průhledná, barvu i rozostření nese sklo. */
    glass: Modifier = Modifier,
    modifier: Modifier = Modifier,
    /**
     * Plovoucí pruh nad konverzací má odsazení od okrajů a stín; jako blok seznamu (seznam
     * témat) ne — tam ho drží odsazení seznamu jako každý jiný řádek.
     */
    floating: Boolean = true,
) {
    val title = pluralStringResource(Res.plurals.join_requests_bar, requests.count, requests.count.grouped())
    val names = requests.recent.joinToString(", ") { it.title }
    val subtitle = when {
        names.isEmpty() -> null
        requests.count > requests.recent.size -> stringResource(Res.string.join_requests_bar_more, names)
        else -> names
    }

    BarPill(
        title = title,
        subtitle = subtitle,
        onClick = onClick,
        glass = glass,
        modifier = modifier,
        floating = floating,
        leading = {
            if (requests.recent.isNotEmpty()) {
                FaceStack(
                    faces = requests.recent,
                    // Lem v barvě skla, jinak by se tváře přes sebe slily.
                    ringColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    size = FACE_SIZE,
                )
            } else {
                BarBadge(Res.drawable.person_add_24dp)
            }
        },
        trailing = {
            IconButton(onClick = onHide, shapes = IconButtonDefaults.shapes()) {
                Icon(painterResource(Res.drawable.close_24dp), contentDescription = stringResource(Res.string.join_requests_bar_hide))
            }
        },
    )
}

private val FACE_SIZE = 32.dp
