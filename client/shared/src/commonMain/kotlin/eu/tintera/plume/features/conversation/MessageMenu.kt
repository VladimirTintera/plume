package eu.tintera.plume.features.conversation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import eu.tintera.plume.core.ui.asDayLabel
import eu.tintera.plume.core.ui.asTimeOfDay
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.add_reaction_24dp
import eu.tintera.plume.resources.common_cancel
import eu.tintera.plume.resources.content_copy_24dp
import eu.tintera.plume.resources.conversation_delivery_failed
import eu.tintera.plume.resources.conversation_delivery_read
import eu.tintera.plume.resources.conversation_delivery_sending
import eu.tintera.plume.resources.conversation_delivery_sent
import eu.tintera.plume.resources.delete_24dp
import eu.tintera.plume.resources.delete_message_choice
import eu.tintera.plume.resources.delete_message_for_all
import eu.tintera.plume.resources.delete_message_for_partner
import eu.tintera.plume.resources.delete_message_for_partner_named
import eu.tintera.plume.resources.delete_message_for_self
import eu.tintera.plume.resources.delete_message_title
import eu.tintera.plume.resources.edit_24dp
import eu.tintera.plume.resources.forward_24dp
import eu.tintera.plume.resources.info_24dp
import eu.tintera.plume.resources.keep_off_24dp
import eu.tintera.plume.resources.menu_copy
import eu.tintera.plume.resources.menu_delete
import eu.tintera.plume.resources.menu_edit
import eu.tintera.plume.resources.menu_favorite_add
import eu.tintera.plume.resources.menu_favorite_remove
import eu.tintera.plume.resources.menu_forward
import eu.tintera.plume.resources.menu_info
import eu.tintera.plume.resources.menu_more_reactions
import eu.tintera.plume.resources.menu_pin
import eu.tintera.plume.resources.menu_reply
import eu.tintera.plume.resources.menu_sent_at
import eu.tintera.plume.resources.menu_sent_at_delivery
import eu.tintera.plume.resources.menu_unpin
import eu.tintera.plume.resources.pin_message_title
import eu.tintera.plume.resources.pin_option_notify_channel
import eu.tintera.plume.resources.pin_option_notify_group
import eu.tintera.plume.resources.pin_option_private
import eu.tintera.plume.resources.pin_option_private_named
import eu.tintera.plume.resources.pin_scope_channel
import eu.tintera.plume.resources.pin_scope_group
import eu.tintera.plume.resources.pin_scope_private
import eu.tintera.plume.resources.push_pin_24dp
import eu.tintera.plume.resources.reply_24dp
import eu.tintera.plume.resources.star_24dp
import eu.tintera.plume.resources.star_filled_24dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Nabídka nad dlouze stisknutou zprávou.
 *
 * Dva samostatné kusy, ne jeden seznam: **pruh rychlých reakcí** a pod ním **nabídka akcí**.
 * Reagovat a odpovědět nejsou varianty téhož — jedno je jedno ťuknutí na emoji, druhé otevírá
 * další krok. Stejně to dělí Google Messages a je to poznat na první pohled.
 *
 * Proti bottom sheetu, který tu byl dřív, má tohle dvě výhody: drží se **u zprávy**, které se
 * týká, a unese víc položek, než kolik se do sheetu vejde bez rolování.
 *
 * Kreslí se do [Popup], ne do obsahu obrazovky. Důvod je zpět: `Popup` s `focusable = true` si
 * tlačítko zpět obslouží sám přes `onDismissRequest`, takže není potřeba `BackHandler` — a ten
 * by v téhle aplikaci zabil predictive back (viz CLAUDE.md).
 *
 * **Deklaruje se uvnitř bubliny**, ne v obrazovce. Popup pak dostane její rozměry rovnou —
 * `calculatePosition` je volané s `anchorBounds` toho prvku, pod kterým popup v kompozici visí.
 * Odpadá tím předávání souřadnic přes celou obrazovku a hlavně se nemají jak rozejít: anchor se
 * přepočítá při každém umístění, takže sedí i když se pod nabídkou něco pohne.
 *
 * Zbývá jediná souřadnicová práce, a ta se **měří, nepočítá**: popup je na Androidu vlastní okno
 * a systém ho posadí pod stavový řádek bez ohledu na to, o jakou pozici si řekne. O kolik, ví
 * jenom platforma — proto `popupOrigin`.
 */
@Composable
internal fun MessageMenuOverlay(
    menu: MessageMenu,
    /** Tvar bubliny. Díra v závoji ho kopíruje, včetně staženého nároží u navazující zprávy. */
    bubbleShape: Shape,
    handlers: MessageMenuHandlers,
    /** Stav doručení vlastní zprávy; `null` u cizí. Vypíše se v hlavičce vedle času. */
    delivery: Delivery?,
) {
    val id = menu.messageId
    val items = buildList {
        if (menu.actions.canReply) {
            add(MenuItem(Res.drawable.reply_24dp, stringResource(Res.string.menu_reply)) { handlers.onReply(id) })
        }
        if (menu.canEdit) {
            add(MenuItem(Res.drawable.edit_24dp, stringResource(Res.string.menu_edit)) { handlers.onEdit(id) })
        }
        // Bez textu není co kopírovat, i kdyby to server dovolil.
        if (menu.actions.canCopy && menu.hasText) {
            add(MenuItem(Res.drawable.content_copy_24dp, stringResource(Res.string.menu_copy)) { handlers.onCopy(id) })
        }
        menu.favoriteSticker?.let { favorite ->
            add(
                if (favorite) {
                    MenuItem(Res.drawable.star_filled_24dp, stringResource(Res.string.menu_favorite_remove)) { handlers.onToggleFavorite(id) }
                } else {
                    MenuItem(Res.drawable.star_24dp, stringResource(Res.string.menu_favorite_add)) { handlers.onToggleFavorite(id) }
                },
            )
        }
        if (menu.actions.canForward) {
            add(MenuItem(Res.drawable.forward_24dp, stringResource(Res.string.menu_forward)) { handlers.onForward(id) })
        }
        /*
         * „Informace" se nabízí VŽDY.
         *
         * Na rozdíl od ostatních položek nestojí na právu od serveru: čas odeslání a úpravy má
         * konverzace sama, takže sheet má co ukázat i u zprávy, u které server odkaz, zhlédnutí
         * ani čas přečtení nevydá.
         */
        add(MenuItem(Res.drawable.info_24dp, stringResource(Res.string.menu_info)) { handlers.onInfo(id) })
        run {
        }

        if (menu.actions.canPin) {
            add(
                if (menu.isPinned) {
                    MenuItem(Res.drawable.keep_off_24dp, stringResource(Res.string.menu_unpin)) { handlers.onUnpin(id) }
                } else {
                    MenuItem(Res.drawable.push_pin_24dp, stringResource(Res.string.menu_pin)) { handlers.onPin(id) }
                },
            )
        }
        if (menu.actions.canDeleteForSelf || menu.actions.canDeleteForAll) {
            add(
                MenuItem(Res.drawable.delete_24dp, stringResource(Res.string.menu_delete), destructive = true) {
                    handlers.onDelete(id)
                },
            )
        }
    }

    val showQuickRow = menu.quickEmoji.isNotEmpty()

    /*
     * Rozbalený výběr reakcí je sheet UVNITŘ tohohle popupu, ne `ModalBottomSheet`.
     *
     * Modální sheet otevřený z nabídky je mrtvý: vyjede, ale ťuknutí do něj nikam nedojde
     * (ověřeno na zařízení, stejně u výběru reakcí i u „Informace"). Je to okno navíc, otevřené
     * ve chvíli, kdy se okno popupu ruší — a dokud se to nevysvětlí, nesmí z nabídky žádný další
     * `ModalBottomSheet` vést. Tady je výběr obyčejný obsah popupu: kreslí ho totéž okno, které
     * ukazuje sbalený pruh, a ťuknutí jde touž cestou jako u rychlé reakce.
     *
     * Že se v tom listuje, popupu nevadí — `LazyVerticalGrid` v něm scrolluje jako kdekoli jinde.
     */
    var expanded by rememberSaveable { mutableStateOf(false) }

    /** Zpět a ťuknutí mimo: rozbalený výběr se nejdřív sbalí na nabídku, teprve pak se zavírá. */
    val dismiss: () -> Unit = {
        if (expanded) {
            expanded = false
        } else {
            handlers.onDismiss()
        }
    }

    // Výšky se počítají, ne měří — kreslíme si nabídku sami, takže je známe předem a ušetří
    // se snímek, ve kterém by nabídka poskočila na správné místo. Hlavička s časem se
    // proto musí připočíst.
    val menuHeight = MENU_PADDING * 2 + MENU_HEADER_HEIGHT + MENU_ITEM_HEIGHT * items.size

    // Náběh se spouští až po prvním složení, jinak by animace neměla z čeho vyjet.
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    val scale by animateFloatAsState(
        targetValue = if (shown) 1f else 0.9f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "menu-scale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        // Bez bounce — overshoot u alfy se ořízne do [0,1] a působí jako bliknutí.
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "menu-alpha",
    )

    /*
     * Kde má popup svůj počátek na obrazovce.
     *
     * Zjišťuje se měřením, ne výpočtem ze systémových vsazení: popup je vlastní okno a o kolik je
     * posazené níž, ví jenom platforma. Dokud to není změřené, nekreslí se nic — je to jeden
     * snímek a náběh stejně startuje na nulové průhlednosti.
     */
    var popupOrigin by remember { mutableStateOf<Offset?>(null) }

    /** Obdélník bubliny na obrazovce, jak ho popupu nabídne platforma při umísťování. */
    var anchor by remember { mutableStateOf<IntRect?>(null) }
    val positionProvider = remember {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset {
                // Jediné místo, kde se o bublině dozvíme její pozici v soustavě popupů.
                anchor = anchorBounds
                // Nabídka si obsah rozmístí sama, takže od okna chce jen plátno od počátku.
                return IntOffset.Zero
            }
        }
    }

    /** Bublina přepočtená do soustavy popupu. */
    val local = popupOrigin?.let { origin ->
        anchor?.let { bounds ->
            Rect(
                left = bounds.left - origin.x,
                top = bounds.top - origin.y,
                right = bounds.right - origin.x,
                bottom = bounds.bottom - origin.y,
            )
        }
    }

    /*
     * Systémové vsazení se čte TADY, ne uvnitř popupu.
     *
     * Tenhle kód běží v hlavním okně, kde jsou vsazení známá; popup je vlastní okno a s vypnutým
     * ořezem (viz níž) o nich nic neví. Závoj má jít pod lišty, ale nabídka se pod ně schovat
     * nesmí, takže z nich vzniknou okraje, do kterých se nesmí umístit.
     */
    val insets = WindowInsets.systemBars
    val density = LocalDensity.current
    val topInset = with(density) { insets.getTop(density).toDp() }
    val bottomInset = with(density) { insets.getBottom(density).toDp() }

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = dismiss,
        /*
         * `clippingEnabled = false` pustí okno popupu **za hranice obsahu okna**, tedy pod stavovou
         * a navigační lištu. Bez toho systém popup vsadí dovnitř a závoj skončí ostrým pruhem
         * u obou lišt — vypadá to jako nedotažené vsazení, protože to jím doslova je.
         */
        properties = PopupProperties(focusable = true, clippingEnabled = false),
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { popupOrigin = it.positionOnScreen() }
                // Ťuknutí mimo nabídku zavírá. Bez `indication` — vlnka přes celou obrazovku
                // by vypadala jako chyba.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = dismiss,
                )
                // Vlastní vrstva je pro vykrojení závoje POVINNÁ: `BlendMode.Clear` maže z vrstvy,
                // a bez ní by vymazalo i všechno, co je pod popupem.
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawBehind {
                    val hole = local ?: return@drawBehind
                    drawScrimWithHole(
                        anchor = hole,
                        shape = bubbleShape,
                        color = scrimColor.copy(alpha = scrimColor.alpha * alpha),
                    )
                },
        ) {
            val bubble = local ?: return@BoxWithConstraints

            val anchorTop = with(density) { bubble.top.toDp() }
            val anchorBottom = with(density) { bubble.bottom.toDp() }
            val anchorLeft = with(density) { bubble.left.toDp() }
            val anchorRight = with(density) { bubble.right.toDp() }

            val layout = placeMenu(
                anchorTop = anchorTop,
                anchorBottom = anchorBottom,
                quickRowHeight = if (showQuickRow) QUICK_ROW_HEIGHT else 0.dp,
                menuHeight = menuHeight,
                screenHeight = maxHeight,
                topMargin = topInset + SCREEN_MARGIN,
                bottomMargin = bottomInset + SCREEN_MARGIN,
            )

            /*
             * Rozbalený výběr nechává na obrazovce pruh reakcí a vybranou zprávu, nabídku akcí ne.
             *
             * Tak to dělá i Google Messages: pruh zůstane nad sheetem, takže je pořád vidět, čeho
             * se výběr týká a která reakce je už použitá. Nabídka akcí je druhý krok, ve kterém
             * uživatel zrovna není, a vedle mřížky by se na telefon nevešla.
             *
             * Rozvržení se počítá i sbalené: při odjezdu sheetu `expanded` už neplatí a výška by
             * mu pod rukama skočila na podlahu.
             */
            val sheet = reactionSheetLayout(
                anchorTop = anchorTop,
                screenHeight = maxHeight,
                topMargin = topInset + SCREEN_MARGIN,
            )

            if (showQuickRow) {
                // Pruh se nad vyjetý sheet posouvá plynule — skok by vypadal jako přeskládání.
                val quickY by animateDpAsState(
                    targetValue = if (expanded) sheet.quickY else layout.quickY,
                    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                    label = "quick-row-y",
                )

                QuickReactions(
                    menu = menu,
                    onPick = { emoji -> handlers.onPick(id, emoji) },
                    onMore = { expanded = true },
                    modifier = Modifier
                        // Pruh je široký, takže se zarovnává ke KRAJI obrazovky podle strany
                        // bubliny — na zprávu samotnou by se stejně nevešel.
                        .align(if (menu.isOutgoing) Alignment.TopEnd else Alignment.TopStart)
                        .offset(y = quickY)
                        .padding(horizontal = SCREEN_MARGIN)
                        .scale(scale)
                        .alpha(alpha),
                )
            }

            if (!expanded) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .offset(
                            // Nabídka má pevnou šířku, takže se dá zarovnat k hraně BUBLINY —
                            // vyrůstá od zprávy, ne od kraje obrazovky.
                            x = menuX(
                                anchorLeft = anchorLeft,
                                anchorRight = anchorRight,
                                isOutgoing = menu.isOutgoing,
                                screenWidth = maxWidth,
                            ),
                            y = layout.menuY,
                        )
                        .scale(scale)
                        .alpha(alpha),
                ) {
                    ActionMenu(items, menu.timestamp, delivery)
                }
            }

            AnimatedVisibility(
                visible = expanded,
                modifier = Modifier.align(Alignment.BottomCenter),
                // Vyjíždí zdola jako sheet. Posun stačí: leží v překryvu, takže nemá layout,
                // kterému by se měnilo místo — past se `slideOutVertically` je jinde (spodní bar).
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                label = "reaction-sheet",
            ) {
                ReactionSheet(
                    emoji = menu.emoji,
                    chosen = menu.chosenEmoji,
                    onPick = { emoji -> handlers.onPick(id, emoji) },
                    onCollapse = { expanded = false },
                    height = sheet.height,
                    bottomInset = bottomInset,
                )
            }
        }
    }
}

/** Pruh rychlých reakcí. */
@Composable
private fun QuickReactions(
    menu: MessageMenu,
    onPick: (String) -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(QUICK_ROW_HEIGHT / 2),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 3.dp,
        modifier = modifier.height(QUICK_ROW_HEIGHT),
    ) {
        Row(
            Modifier.padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            menu.quickEmoji.forEach { emoji ->
                val chosen = emoji in menu.chosenEmoji

                Surface(
                    onClick = { onPick(emoji) },
                    shape = RoundedCornerShape(50),
                    // Už použitá reakce se zvýrazní, aby bylo poznat, že ťuknutí ji zase sundá.
                    color = if (chosen) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        Color.Transparent
                    },
                    modifier = Modifier.size(QUICK_EMOJI_SIZE),
                ) {
                    Text(
                        text = emoji,
                        // Mimo typografickou škálu schválně: u emoji je nosná celá kresba,
                        // ne výška písmen, takže ve velikosti role působí menší.
                        fontSize = 24.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
                    )
                }
            }

            // „+" má smysl jen tehdy, když je za ním něco navíc.
            if (menu.hasMoreEmoji) {
                Surface(
                    onClick = onMore,
                    shape = RoundedCornerShape(50),
                    color = Color.Transparent,
                    modifier = Modifier.size(QUICK_EMOJI_SIZE),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            painterResource(Res.drawable.add_reaction_24dp),
                            contentDescription = stringResource(Res.string.menu_more_reactions),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Rozbalený výběr reakcí: sheet u spodní hrany s mřížkou VŠECH nabídnutých emoji.
 *
 * **Není to `ModalBottomSheet`.** Modální sheet otevřený z nabídky nedostane ťuknutí (viz
 * `MessageMenuOverlay`), takže je tohle obyčejný obsah popupu, jen sheet připomíná: celá šířka
 * u spodní hrany, zaoblený vršek, úchyt a tažením dolů se zavírá. Ťuknutí jde touž cestou jako
 * u rychlé reakce ([MessageMenuHandlers.onPick]) — není tu druhá obsluha, která by se s ní
 * mohla rozejít.
 *
 * Mřížka se posouvá svisle; nabídnutých emoji bývá přes třicet. Až přibudou vlastní (placené)
 * emoji, přibude sem řádek záložek a hledání — proto sheet, a ne pruh navíc.
 */
@Composable
private fun ReactionSheet(
    emoji: List<String>,
    chosen: Set<String>,
    onPick: (String) -> Unit,
    onCollapse: () -> Unit,
    height: Dp,
    /** Výška navigační lišty. Popup kreslí i pod ni (`clippingEnabled = false`), sheet tedy jde
     *  až k hraně displeje a lišta se odsadí až obsahu mřížky. */
    bottomInset: Dp,
) {
    val scope = rememberCoroutineScope()
    val dragY = remember { Animatable(0f) }
    val settle = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val dismissPx = with(LocalDensity.current) { REACTION_SHEET_DISMISS.toPx() }

    Surface(
        shape = RoundedCornerShape(topStart = REACTION_SHEET_CORNER, topEnd = REACTION_SHEET_CORNER),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 3.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .graphicsLayer { translationY = dragY.value }
            // Sheet musí ťuknutí SPOLKNOUT. Bez toho propadne na závoj pod ním a ten výběr
            // zavře — ťuknutí vedle buňky by mřížku sestřelilo.
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Column {
            /*
             * Úchyt, a zároveň jediné místo, které bere tažení.
             *
             * Na celém sheetu by se tah pral se svislým posunem mřížky; nahoře je jasné, že se
             * táhne se sheetem. Zavírá se při PUŠTĚNÍ za prahem, stejně jako odpověď swipem —
             * kdo si to rozmyslí, vrátí prst zpátky.
             */
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(DRAG_HANDLE_ROW_HEIGHT)
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta ->
                            scope.launch { dragY.snapTo((dragY.value + delta).coerceAtLeast(0f)) }
                        },
                        onDragStopped = {
                            // Po zavření se posun nevrací na nulu: sheet odjíždí z místa, kam ho
                            // prst dotáhl, a obsah se stejně zahodí, až animace doběhne.
                            if (dragY.value > dismissPx) {
                                onCollapse()
                            } else {
                                dragY.animateTo(0f, settle)
                            }
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(width = DRAG_HANDLE_WIDTH, height = DRAG_HANDLE_HEIGHT)
                        .background(
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            RoundedCornerShape(50),
                        ),
                )
            }

            LazyVerticalGrid(
                // Adaptivně, ne pevný počet sloupců: sheet je přes celou šířku okna a to se mezi
                // telefonem a širokým rozvržením liší.
                columns = GridCells.Adaptive(QUICK_EMOJI_SIZE + REACTION_SHEET_PADDING),
                // Zbytek sheetu pod úchytem, ať se mřížka nesmršťuje podle počtu emoji.
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = REACTION_SHEET_PADDING,
                    end = REACTION_SHEET_PADDING,
                    top = REACTION_SHEET_PADDING,
                    bottom = REACTION_SHEET_PADDING + bottomInset,
                ),
            ) {
                // Emoji jako klíč: `availableReactions` je vrací bez duplicit, takže se dvě buňky
                // o tentýž klíč nepoperou.
                items(emoji, key = { it }) { item ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Surface(
                            onClick = { onPick(item) },
                            shape = RoundedCornerShape(50),
                            // Už použitá reakce se zvýrazní, aby bylo poznat, že ťuknutí ji sundá.
                            color = if (item in chosen) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                Color.Transparent
                            },
                            modifier = Modifier.size(QUICK_EMOJI_SIZE),
                        ) {
                            Text(
                                text = item,
                                // Mimo typografickou škálu schválně: u emoji je nosná celá kresba,
                                // ne výška písmen, takže ve velikosti role působí menší.
                                fontSize = 24.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Nabídka akcí. Položky přibývají, jak je postupně umíme — mrtvé se sem nedávají. */
@Composable
private fun ActionMenu(items: List<MenuItem>, timestamp: Int, delivery: Delivery?) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 3.dp,
        modifier = Modifier.width(MENU_WIDTH),
    ) {
        Column(Modifier.padding(vertical = MENU_PADDING)) {
            /*
             * Kdy zpráva přišla.
             *
             * V bublině čas není a tahem doleva se k němu čtečka obrazovky nedostane, takže
             * tohle je jediná cesta, která funguje pro každého. Není to položka nabídky —
             * nedá se na ni ťuknout a nic nedělá.
             */
            /*
             * Kdy zpráva přišla a jak dopadla.
             *
             * Odznak doručení je pod bublinou jen u některých zpráv (viz `readBadgeRows`)
             * a odkrýt ho ťuknutím čtečka obrazovky neumí — tady ho tedy má vždycky.
             */
            val deliveryLabel = when (delivery) {
                Delivery.Sending -> stringResource(Res.string.conversation_delivery_sending)
                Delivery.Sent -> stringResource(Res.string.conversation_delivery_sent)
                Delivery.Read -> stringResource(Res.string.conversation_delivery_read)
                Delivery.Failed -> stringResource(Res.string.conversation_delivery_failed)
                null -> null
            }

            Text(
                text = if (deliveryLabel == null) {
                    stringResource(Res.string.menu_sent_at, timestamp.asDayLabel(), timestamp.asTimeOfDay())
                } else {
                    stringResource(
                        Res.string.menu_sent_at_delivery,
                        timestamp.asDayLabel(),
                        timestamp.asTimeOfDay(),
                        deliveryLabel,
                    )
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .height(MENU_HEADER_HEIGHT)
                    .padding(horizontal = 20.dp)
                    .wrapContentHeight(),
            )

            items.forEach { item ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(MENU_ITEM_HEIGHT)
                        .clickable(onClick = item.onClick)
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Jediná nevratná položka dostane chybovou barvu, aby se nespletla
                    // s ostatními — stejně jako v nabídce nad chatem.
                    val color = if (item.destructive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }

                    Icon(
                        painterResource(item.icon),
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = color,
                    )
                }
            }
        }
    }
}

/**
 * Potvrzení smazání zprávy.
 *
 * Text pojmenovává **konkrétní následek**, ne obecné „opravdu?". Smazat u sebe a smazat u všech
 * jsou dvě různé věci a uživatel musí vědět, kterou zrovna dělá — proto je volba zaškrtávátkem
 * a ne dvěma tlačítky vedle sebe, u kterých se dá přehlédnout, na které se ťuklo.
 *
 * Když server dovoluje jen jednu z nich, volba se nenabízí a text rovnou říká, co se stane.
 */
@Composable
fun MessageDeleteDialog(
    pending: PendingDelete,
    partnerName: String?,
    onConfirm: (revoke: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    // Ve skupinách a kanálech je smazání u všech jediná možnost, takže se zaškrtávátko
    // nenabízí a `revoke` je dané.
    val choice = pending.canDeleteForAll && pending.canDeleteForSelf
    var forEveryone by rememberSaveable { mutableStateOf(false) }
    val revoke = if (choice) forEveryone else pending.canDeleteForAll

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.delete_message_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(
                        when {
                            choice -> Res.string.delete_message_choice
                            pending.canDeleteForAll -> Res.string.delete_message_for_all
                            else -> Res.string.delete_message_for_self
                        },
                    ),
                )

                if (choice) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { forEveryone = !forEveryone },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Checkbox(checked = forEveryone, onCheckedChange = { forEveryone = it })
                        Text(
                            text = partnerName?.let { stringResource(Res.string.delete_message_for_partner_named, it) }
                                ?: stringResource(Res.string.delete_message_for_partner),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(revoke) }) {
                Text(text = stringResource(Res.string.menu_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) }
        },
    )
}

private data class MenuItem(
    val icon: DrawableResource,
    val label: String,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * Obsluha nabídky, pohromadě.
 *
 * Nabídka se deklaruje uvnitř bubliny, takže by ji bublina jinak musela nést jako šest
 * samostatných parametrů, které sama k ničemu nepoužije.
 */
data class MessageMenuHandlers(
    val onPick: (messageId: Long, emoji: String) -> Unit,
    val onReply: (messageId: Long) -> Unit,
    val onEdit: (messageId: Long) -> Unit,
    val onCopy: (messageId: Long) -> Unit,
    val onDelete: (messageId: Long) -> Unit,
    val onForward: (messageId: Long) -> Unit,
    val onPin: (messageId: Long) -> Unit,
    val onUnpin: (messageId: Long) -> Unit,
    val onToggleFavorite: (messageId: Long) -> Unit,
    val onInfo: (messageId: Long) -> Unit,
    val onDismiss: () -> Unit,
)

/**
 * Závoj s dírou přesně na bublině.
 *
 * Vybraná zpráva musí zůstat čitelná — je to kontext, ke kterému se nabídka vztahuje; ztmavit
 * i ji by znamenalo ptát se na něco, co není vidět. Vykrojení jde přes [BlendMode.Clear], takže
 * se kreslí NAD bublinou a přesto ji neschová.
 *
 * Tvar se **bere ze samotné bubliny**, nekreslí se podle konstanty. Navazující zpráva má jedno
 * nároží stažené na 4 dp a jednotné zaoblení by u ní z díry vyčnívalo; navíc by se konstanta
 * musela ručně držet v souladu s `BUBBLE_CORNER`.
 *
 * Nutná je [CompositingStrategy.Offscreen]: `Clear` maže z vrstvy, a bez vlastní vrstvy by
 * vymazalo i všechno pod popupem.
 */
private fun DrawScope.drawScrimWithHole(anchor: Rect, shape: Shape, color: Color) {
    drawRect(color)

    val outline = shape.createOutline(anchor.size, layoutDirection, this)
    translate(left = anchor.left, top = anchor.top) {
        drawOutline(outline, color = Color.Transparent, blendMode = BlendMode.Clear)
    }
}

/** Svislé umístění obou kusů. */
private data class MenuLayout(val quickY: Dp, val menuY: Dp)

/**
 * Kam přijde pruh reakcí a kam nabídka.
 *
 * Výchozí stav je **reakce nad zprávou, nabídka pod ní** — mezi nimi zůstane zpráva vidět.
 * Když se to nevejde, oba kusy se přesunou na tu stranu, kde je místo. Výšky se nepočítají
 * z měření, ale z konstant: obojí si kreslíme sami, takže je známe předem a ušetří se tím
 * snímek, ve kterém by nabídka poskočila na správné místo.
 */
private fun placeMenu(
    anchorTop: Dp,
    anchorBottom: Dp,
    quickRowHeight: Dp,
    menuHeight: Dp,
    screenHeight: Dp,
    /** Kam nahoře nesmí — stavová lišta plus odstup od kraje. */
    topMargin: Dp,
    /** Totéž dole, kvůli navigační liště. */
    bottomMargin: Dp,
): MenuLayout {
    val gap = MENU_GAP
    var quickY = anchorTop - gap - quickRowHeight
    var menuY = anchorBottom + gap

    // Nevejde se nad zprávu: oba kusy jdou pod ni.
    if (quickY < topMargin) {
        quickY = anchorBottom + gap
        menuY = quickY + quickRowHeight + gap
    }

    // Nevejde se pod zprávu: nabídka jde nad ni, reakce ještě nad nabídku.
    if (menuY + menuHeight > screenHeight - bottomMargin) {
        menuY = anchorTop - gap - menuHeight
        if (quickY + quickRowHeight > menuY) {
            quickY = menuY - gap - quickRowHeight
        }
    }

    fun Dp.clampTo(height: Dp): Dp =
        coerceIn(topMargin, (screenHeight - bottomMargin - height).coerceAtLeast(topMargin))

    return MenuLayout(
        quickY = quickY.clampTo(quickRowHeight),
        menuY = menuY.clampTo(menuHeight),
    )
}

/** Výška vyjetého výběru reakcí a kam se nad něj uklidí pruh rychlých reakcí. */
internal data class ReactionSheetLayout(val height: Dp, val quickY: Dp)

/**
 * Jak vysoký bude vyjetý výběr reakcí a kam se nad něj posune pruh rychlých reakcí.
 *
 * Sheet sedí u spodní hrany a bere podíl výšky okna — ne tolik, aby z výběru byla samostatná
 * obrazovka, a ne tak málo, aby v mřížce byl jeden řádek. Mřížka se posouvá, takže nižší sheet
 * nic neschová, jen se v něm listuje dřív.
 *
 * Pruh reakcí zůstává u zprávy, dokud se nad sheet vejde; jinak se posadí těsně nad něj. Musí
 * zůstat vidět — je to jediné místo, kde je poznat, čeho se výběr týká a co je už použité.
 */
internal fun reactionSheetLayout(
    anchorTop: Dp,
    screenHeight: Dp,
    /** Kam nahoře nesmí — stavová lišta plus odstup od kraje. */
    topMargin: Dp,
): ReactionSheetLayout {
    val height = (screenHeight * REACTION_SHEET_FRACTION)
        .coerceIn(REACTION_SHEET_MIN_HEIGHT, REACTION_SHEET_MAX_HEIGHT)
        // Na nízkém okně se ustoupí, aby nad sheetem zbylo aspoň na pruh reakcí.
        .coerceAtMost((screenHeight - topMargin - MENU_GAP - QUICK_ROW_HEIGHT).coerceAtLeast(0.dp))

    val sheetTop = screenHeight - height
    val highest = (sheetTop - MENU_GAP - QUICK_ROW_HEIGHT).coerceAtLeast(topMargin)

    return ReactionSheetLayout(
        height = height,
        quickY = (anchorTop - MENU_GAP - QUICK_ROW_HEIGHT).coerceIn(topMargin, highest),
    )
}

/** Vodorovné umístění nabídky: k té hraně bubliny, u které zpráva stojí. */
private fun menuX(anchorLeft: Dp, anchorRight: Dp, isOutgoing: Boolean, screenWidth: Dp): Dp {
    val raw = if (isOutgoing) anchorRight - MENU_WIDTH else anchorLeft
    return raw.coerceIn(
        SCREEN_MARGIN,
        (screenWidth - SCREEN_MARGIN - MENU_WIDTH).coerceAtLeast(SCREEN_MARGIN),
    )
}

/** Jak moc ztmavne zbytek obrazovky. Dost na odsunutí do pozadí, ne na zakrytí. */
private val scrimColor = Color.Black.copy(alpha = 0.45f)

private val MENU_GAP = 8.dp
private val SCREEN_MARGIN = 12.dp
private val MENU_WIDTH = 220.dp
private val MENU_ITEM_HEIGHT = 48.dp

/** Řádek s časem odeslání nad položkami. Nižší než položka — nedá se na něj ťuknout. */
private val MENU_HEADER_HEIGHT = 32.dp
private val MENU_PADDING = 8.dp
private val QUICK_ROW_HEIGHT = 52.dp
private val QUICK_EMOJI_SIZE = 40.dp

/** Rozbalený výběr: podíl výšky okna, podlaha a strop. */
private const val REACTION_SHEET_FRACTION = 0.45f
internal val REACTION_SHEET_MIN_HEIGHT = 220.dp
internal val REACTION_SHEET_MAX_HEIGHT = 400.dp
private val REACTION_SHEET_CORNER = 28.dp
private val REACTION_SHEET_PADDING = 8.dp

/** Úchyt nahoře: proužek a pruh, ve kterém se dá za sheet táhnout. */
private val DRAG_HANDLE_WIDTH = 32.dp
private val DRAG_HANDLE_HEIGHT = 4.dp
private val DRAG_HANDLE_ROW_HEIGHT = 22.dp

/** Za tuhle vzdálenost stažený sheet se při puštění zavře. */
private val REACTION_SHEET_DISMISS = 80.dp

/**
 * Potvrzení připnutí.
 *
 * Nejde o nevratnou akci (odepnout jde kdykoli), ale připnutí má jednu volbu, která mění,
 * koho se dotkne — a ta se musí dát najevo DŘÍV, než se stane: upozornění členům už nikdo
 * nevezme zpátky. Výchozí stavy odpovídají Telegramu: upozornit ano, připnout i protějšku ano.
 */
@Composable
fun PinMessageDialog(
    pending: PendingPin,
    partnerName: String?,
    onConfirm: (option: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var option by rememberSaveable { mutableStateOf(true) }

    val (description, optionLabel) = when (pending.scope) {
        PinScope.Private ->
            // Jméno v 1. pádu: skloňovat cizí jména neumíme a „i u Petr Novák" zní rozbitě.
            stringResource(Res.string.pin_scope_private) to (
                partnerName?.let { stringResource(Res.string.pin_option_private_named, it) }
                    ?: stringResource(Res.string.pin_option_private)
                )
        PinScope.Group ->
            stringResource(Res.string.pin_scope_group) to stringResource(Res.string.pin_option_notify_group)
        PinScope.Channel ->
            stringResource(Res.string.pin_scope_channel) to stringResource(Res.string.pin_option_notify_channel)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.pin_message_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(description)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { option = !option },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Checkbox(checked = option, onCheckedChange = { option = it })
                    Text(text = optionLabel, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(option) }) { Text(stringResource(Res.string.menu_pin)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) }
        },
    )
}
