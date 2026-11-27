package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.ui.ExpressiveMenu
import eu.tintera.plume.core.ui.ExpressiveSuggestionChip
import eu.tintera.plume.core.ui.MenuAction
import eu.tintera.plume.core.ui.horizontalFadingEdges
import eu.tintera.plume.core.ui.horizontalScrollWithWheel
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.auto_awesome_24dp
import eu.tintera.plume.resources.close_24dp
import eu.tintera.plume.resources.conversation_undo
import eu.tintera.plume.resources.reply_suggestion_insert
import eu.tintera.plume.resources.reply_suggestions_hide
import eu.tintera.plume.resources.reply_suggestions_options
import eu.tintera.plume.resources.reply_suggestions_turn_off
import eu.tintera.plume.resources.reply_suggestions_turn_off_hint
import eu.tintera.plume.resources.reply_suggestions_turned_off
import eu.tintera.plume.resources.visibility_off_24dp
import org.jetbrains.compose.resources.getString
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Návrhy odpovědi nad vstupním polem.
 *
 * Čipy, ne bublina v konverzaci: bublina na místě, kde by odpověď byla, vypadá jako odeslaná
 * zpráva a jednou za čas by si někdo myslel, že už odešla. Tenhle vzor znají lidé z Gmailu
 * a Google Zpráv.
 *
 * **Ťuknutí návrh jen VLOŽÍ do pole, neodesílá ho.** Odeslání musí zůstat vědomé — text píše
 * model a uživatel se pod něj podepisuje.
 *
 * Řádek se posouvá do strany: tři návrhy se na úzký telefon vedle sebe nevejdou a lámat je
 * do dvou řádků by pod prstem posouvalo celé pole. Okraj, za kterým řada pokračuje, se
 * prolíná — jinak třetí návrh na úzkém telefonu snadno přehlédneš.
 *
 * Skladba řádku: ✨ vlevo říká, že text psal model, a otevírá nabídku (vypnout návrhy),
 * × vpravo návrhy k téhle zprávě schová. Obojí stojí mimo posun, aby bylo vidět vždycky.
 */
@Composable
fun ReplySuggestionChips(
    suggestions: List<String>,
    onPick: (String) -> Unit,
    /** Schovat návrhy k téhle zprávě; k další přijdou zase. */
    onHide: () -> Unit,
    /** Vypnout návrhy úplně (volba v Nastavení → AI v zařízení). */
    onTurnOff: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Poslední neprázdná sada se drží kvůli odchodové animaci: v okamžiku skrytí je seznam už
    // prázdný a `AnimatedVisibility` by dobíhala s prázdným obsahem (tatáž past jako u odznaku
    // doručení a pruhu připnutých).
    val shown = remember { mutableStateOf(emptyList<String>()) }
    if (suggestions.isNotEmpty()) shown.value = suggestions
    val pick by rememberUpdatedState(onPick)

    AnimatedVisibility(
        visible = suggestions.isNotEmpty(),
        enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) + expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()),
        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) + shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            // Čtečka projde řádek jako celek: napřed ✨ (co to je), pak návrhy, nakonec ×.
            modifier = Modifier
                .semantics { isTraversalGroup = true }
                .padding(horizontal = 4.dp),
        ) {
            SuggestionsMenuButton(onHide = onHide, onTurnOff = onTurnOff)
            val scroll = rememberScrollState()
            // Nová sada začíná zleva, i když řádek mezi zprávami nezmizel.
            LaunchedEffect(shown.value) { scroll.scrollTo(0) }
            // Šířka řádku pro strop čipu (`replyChipMaxWidth`); uvnitř posunu je šířka neomezená.
            BoxWithConstraints(Modifier.weight(1f)) {
                val chipMaxWidth = replyChipMaxWidth(maxWidth)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .horizontalFadingEdges(scroll)
                        .horizontalScrollWithWheel(scroll)
                        .padding(vertical = 4.dp),
                ) {
                    // Klíč podle sady: nová sada přichází znovu postupně, ne naráz.
                    key(shown.value) {
                        shown.value.forEachIndexed { index, suggestion ->
                            ReplyChip(
                                text = suggestion,
                                index = index,
                                maxWidth = chipMaxWidth,
                                onClick = { pick(suggestion) },
                            )
                        }
                    }
                }
            }
            IconButton(onClick = onHide, shapes = IconButtonDefaults.shapes()) {
                Icon(
                    painterResource(Res.drawable.close_24dp),
                    contentDescription = stringResource(Res.string.reply_suggestions_hide),
                    modifier = Modifier.size(REPLY_ICON_SIZE),
                )
            }
        }
    }
}

/**
 * Jeden návrh. Přijíždí od konce řádku se zpožděním podle pořadí, takže řada působí jako „návrhy
 * dorazily", ne jako skok rozvržení. Průhlednost jede na effects specu (bez překmitu),
 * posun na spatial.
 *
 * Plocha je karta vstupního pole ([composerCardColor]), bez rámečku: čip tak patří k poli,
 * kam se vloží, a neplete se s příchozí bublinou (ta je `surfaceContainerHigh` bez přitónování)
 * ani s odchozí. Text `onSurface` — výchozí `onSurfaceVariant` je na kartě zašedlý.
 *
 * Šířka má strop ([replyChipMaxWidth]) a text jde na jeden řádek s výpustkou: dlouhý návrh by
 * zabral celý řádek a další schoval mimo obrazovku. Celý text se stejně vloží do pole.
 */
@Composable
private fun ReplyChip(text: String, index: Int, maxWidth: Dp, onClick: () -> Unit) {
    val motion = MaterialTheme.motionScheme
    val fadeSpec = motion.defaultEffectsSpec<Float>()
    val moveSpec = motion.defaultSpatialSpec<Float>()
    val alpha = remember { Animatable(0f) }
    val travel = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        delay(index * STAGGER_MILLIS)
        launch { alpha.animateTo(1f, fadeSpec) }
        travel.animateTo(0f, moveSpec)
    }
    val insertLabel = stringResource(Res.string.reply_suggestion_insert)
    val click by rememberUpdatedState(onClick)
    // Přijíždí od konce řádku: zprava, v RTL zleva.
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    ExpressiveSuggestionChip(
        onClick = onClick,
        label = {
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = composerCardColor(),
            labelColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = null,
        modifier = Modifier
            .widthIn(max = maxWidth)
            .graphicsLayer {
                this.alpha = alpha.value
                translationX = direction * travel.value * STAGGER_TRAVEL.toPx()
            }
            // Čtečka řekne, co ťuknutí udělá: „vložit do zprávy", ne holé „aktivovat" — jinak
            // by nevidomý nepoznal, že se nic neodešle.
            .semantics {
                onClick(label = insertLabel) {
                    click()
                    true
                }
            },
    )
}

/**
 * ✨ na začátku řádku: označuje text od modelu a nabízí návrhy vypnout. Vypnout jde i v
 * Nastavení, ale tam nikdo nehledá, když ho čipy zrovna obtěžují.
 */
@Composable
private fun SuggestionsMenuButton(onHide: () -> Unit, onTurnOff: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, shapes = IconButtonDefaults.shapes()) {
            Icon(
                painterResource(Res.drawable.auto_awesome_24dp),
                contentDescription = stringResource(Res.string.reply_suggestions_options),
                modifier = Modifier.size(REPLY_ICON_SIZE),
            )
        }
        ExpressiveMenu(
            expanded = expanded,
            onDismiss = { expanded = false },
            groups = listOf(
                listOf(
                    MenuAction(
                        label = stringResource(Res.string.reply_suggestions_hide),
                        icon = Res.drawable.close_24dp,
                        onClick = {
                            expanded = false
                            onHide()
                        },
                    ),
                    MenuAction(
                        label = stringResource(Res.string.reply_suggestions_turn_off),
                        icon = Res.drawable.visibility_off_24dp,
                        supporting = stringResource(Res.string.reply_suggestions_turn_off_hint),
                        onClick = {
                            expanded = false
                            onTurnOff()
                        },
                    ),
                ),
            ),
        )
    }
}

/**
 * Snackbar po vypnutí návrhů z nabídky: říká, kde se zapnou, a nabízí Vrátit. Bez něj by
 * vypnutí jedním ťuknutím (a omylem) nešlo poznat ani vzít zpátky jinak než v Nastavení.
 */
@Composable
internal fun ReplySuggestionsTurnedOffSnackbar(
    replies: ReplySuggestionsViewModel,
    snackbarHostState: SnackbarHostState,
) {
    val turnedOff by replies.turnedOff.collectAsStateWithLifecycle()
    // Snackbar běží mimo efekt: vynulování mění klíč a restart by ho zrušil (viz `ConversationSnackbars`).
    val scope = rememberCoroutineScope()
    LaunchedEffect(turnedOff) {
        if (!turnedOff) return@LaunchedEffect
        replies.consumeTurnedOff()
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = getString(Res.string.reply_suggestions_turned_off),
                actionLabel = getString(Res.string.conversation_undo),
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) replies.turnOn()
        }
    }
}

/**
 * Strop šířky čipu podle šířky řádku s návrhy: aspoň [REPLY_CHIP_MIN_CAP], na širokém řádku
 * [REPLY_CHIP_ROW_SHARE] z něj.
 *
 * Pevných 240 dp sedí na telefon (řádek kolem 300 dp, čip zabere čtyři pětiny a soused
 * vykoukne), ale na desktopu byl s Aya oříznutý KAŽDÝ návrh — mívají 40–60 znaků, tedy kolem
 * 400 dp, a vedle zbývalo plno místa. Návrh se tak nedal přečíst, dokud se nevložil. Podíl
 * z řádku drží původní důvod stropu: i nejdelší čip nechá vykouknout souseda, takže je vidět,
 * že řada pokračuje. Na telefonu vychází podíl pod 240 dp a platí původní strop.
 */
internal fun replyChipMaxWidth(rowWidth: Dp): Dp = maxOf(REPLY_CHIP_MIN_CAP, rowWidth * REPLY_CHIP_ROW_SHARE)

private val REPLY_CHIP_MIN_CAP = 240.dp

private const val REPLY_CHIP_ROW_SHARE = 0.6f

private val REPLY_ICON_SIZE = 20.dp

/** Rozestup příchodu sousedních čipů. */
private const val STAGGER_MILLIS = 60L

/** Jak daleko od konce řádku čip přijíždí. */
private val STAGGER_TRAVEL = 24.dp
