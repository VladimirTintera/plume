package eu.tintera.plume.features.chats

import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration
import org.jetbrains.compose.resources.StringResource
import eu.tintera.plume.resources.profile_notif_mute_forever
import eu.tintera.plume.resources.profile_notif_mute_8h
import eu.tintera.plume.resources.profile_notif_mute_2d
import eu.tintera.plume.resources.profile_notif_mute_1h
import eu.tintera.plume.resources.notifications_24dp
import eu.tintera.plume.resources.expand_more_24dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import eu.tintera.plume.resources.chats_action_report
import eu.tintera.plume.resources.flag_24dp
import eu.tintera.plume.resources.logout_24dp
import androidx.compose.animation.AnimatedVisibility
import eu.tintera.plume.core.ui.SheetActionRow
import eu.tintera.plume.core.ui.chatTitle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.ChatSponsor
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.telegram.SpamActions
import eu.tintera.plume.core.telegram.isPrivate
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.chats_reorder_pinned
import eu.tintera.plume.resources.menu_select
import eu.tintera.plume.resources.select_check_box_24dp
import eu.tintera.plume.resources.drag_indicator_24dp
import eu.tintera.plume.resources.chats_sponsor_announcement_explanation
import eu.tintera.plume.resources.chats_sponsor_hide
import eu.tintera.plume.resources.chats_sponsor_open_proxy
import eu.tintera.plume.resources.chats_sponsor_proxy_explanation
import eu.tintera.plume.resources.visibility_off_24dp
import eu.tintera.plume.resources.vpn_lock_24dp
import eu.tintera.plume.resources.chat_action_folders
import eu.tintera.plume.resources.folder_24dp
import eu.tintera.plume.resources.archive_24dp
import eu.tintera.plume.resources.block_24dp
import eu.tintera.plume.resources.chats_delete_report
import eu.tintera.plume.resources.profile_block
import eu.tintera.plume.resources.profile_unblock
import eu.tintera.plume.resources.chats_action_archive
import eu.tintera.plume.resources.chats_action_unarchive
import eu.tintera.plume.resources.unarchive_24dp
import eu.tintera.plume.resources.chats_action_delete
import eu.tintera.plume.resources.chats_action_leave
import eu.tintera.plume.resources.chats_action_mark_read
import eu.tintera.plume.resources.chats_action_mark_unread
import eu.tintera.plume.resources.chats_action_mute
import eu.tintera.plume.resources.chats_action_pin
import eu.tintera.plume.resources.chats_action_unmute
import eu.tintera.plume.resources.chats_action_unpin
import eu.tintera.plume.resources.delete_24dp
import eu.tintera.plume.resources.mark_chat_unread_24dp
import eu.tintera.plume.resources.notifications_off_24dp
import eu.tintera.plume.resources.push_pin_filled_24dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Akce nad chatem po dlouhém stisku.
 *
 * Bottom sheet, ne kontextové menu u prstu: na telefonu je spodní okraj v dosahu palce a nabídka
 * má místo na popisky, ze kterých je poznat, co se stane — u nevratné akce to není kosmetika.
 */
@Composable
fun ChatActionsSheet(
    chat: ChatSummary,
    /** Co nabídnout proti spamu; `null`, dokud to nedorazí ze serveru — pak se nenabízí nic. */
    spam: SpamActions?,
    onTogglePinned: () -> Unit,
    /**
     * Režim uspořádání připnutých; `null` = nenabízet (chat není připnutý, nebo není s čím ho
     * přeskládat).
     */
    onReorderPinned: (() -> Unit)?,
    /** Výběr víc chatů, začne tímhle chatem. */
    onSelect: () -> Unit,
    /** Ztlumení na dobu; `null` = navždy. */
    onMute: (Duration?) -> Unit,
    onUnmute: () -> Unit,
    onToggleUnread: () -> Unit,
    /** `null` = chat archivovat nejde (Uložené zprávy), položka se nenabídne. */
    onToggleArchived: (() -> Unit)?,
    /** Složky, do kterých chat jde přidat nebo ve kterých je; prázdné = „Přidat do složky" není. */
    folders: List<FolderChoice>,
    onToggleFolder: (Int) -> Unit,
    onSetBlocked: (Boolean) -> Unit,
    /** Samostatné nahlášení (průvodce), bez mazání. */
    onReport: () -> Unit,
    /** Smazání potvrzené v podmenu, s volbami proti spamu. */
    onDelete: (SpamCleanup) -> Unit,
    onDismiss: () -> Unit,
    /** Nastavení proxy; nabízí se jen u chatu od provozovatele proxy ([ChatSponsor.Proxy]). */
    onOpenProxySettings: () -> Unit = {},
) {
    // Sponzorovaný chat není uživatelův: připnout, archivovat, přidat do složky ani opustit ho
    // nejde (TDLib to odmítne), takže má vlastní, kratší nabídku s vysvětlením.
    chat.sponsor?.let { sponsor ->
        SponsoredChatSheet(
            chat = chat,
            sponsor = sponsor,
            onMute = { onMute(null) },
            onUnmute = onUnmute,
            onToggleUnread = onToggleUnread,
            onOpenProxySettings = {
                onDismiss()
                onOpenProxySettings()
            },
            onHide = { onDelete(SpamCleanup()) },
            onDismiss = onDismiss,
        )
        return
    }

    /*
     * Celý, bez napůl rozbaleného stavu, a obsah scrollovatelný. Podmenu smazání se rozbaluje
     * na KONCI nabídky; u polovičního sheetu by jeho tlačítko skončilo pod okrajem obrazovky
     * (tatáž past jako u správy členů). Náhrada za `rememberModalBottomSheetState`, které je od
     * Compose 1.12 zastaralé.
     */
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val scroll = rememberScrollState()
    var deleteExpanded by rememberSaveable { mutableStateOf(false) }

    /*
     * Rozbalené podmenu smazání se dotáhne do pohledu. Sleduje se `maxValue`, protože podmenu
     * roste animací — jednorázový posun na začátku by dojel jen kousek.
     */
    LaunchedEffect(deleteExpanded) {
        if (deleteExpanded) snapshotFlow { scroll.maxValue }.collect { scroll.animateScrollTo(it) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.verticalScroll(scroll)) {
            Text(
                text = chatTitle(chat.id, chat.title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            )

            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                SheetActionRow(
                    icon = Res.drawable.push_pin_filled_24dp,
                    // Popisek říká, co se STANE, ne jaký je stav — jinak si uživatel musí domýšlet,
                    // jestli „Připnuto" je tvrzení, nebo tlačítko.
                    label = stringResource(if (chat.isPinned) Res.string.chats_action_unpin else Res.string.chats_action_pin),
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
                    onClick = onTogglePinned,
                )

                // Pod „Odepnout": pořadí připnutých je vlastnost téže věci.
                if (chat.isPinned && onReorderPinned != null) {
                    SheetActionRow(
                        icon = Res.drawable.drag_indicator_24dp,
                        label = stringResource(Res.string.chats_reorder_pinned),
                        shape = RoundedCornerShape(4.dp),
                        onClick = onReorderPinned,
                    )
                }

                // Vstup do výběru víc chatů: dlouhý stisk patří téhle nabídce, takže výběr začíná
                // odsud — stejně jako výběr zpráv z nabídky nad zprávou.
                SheetActionRow(
                    icon = Res.drawable.select_check_box_24dp,
                    label = stringResource(Res.string.menu_select),
                    shape = RoundedCornerShape(4.dp),
                    onClick = onSelect,
                )

                if (chat.isMuted) {
                    SheetActionRow(
                        icon = Res.drawable.notifications_24dp,
                        label = stringResource(Res.string.chats_action_unmute),
                        shape = RoundedCornerShape(4.dp),
                        onClick = onUnmute,
                    )
                } else {
                    /*
                     * Doby ztlumení se rozbalí na místě, ne v dalším sheetu — jsou to varianty jedné
                     * položky a nabídka pod prstem zůstane stejná. Dřív „Ztlumit" znamenalo navždy,
                     * a na hodinu šlo chat ztlumit jen z notifikace.
                     */
                    var expanded by rememberSaveable { mutableStateOf(false) }
                    val rotation by animateFloatAsState(
                        targetValue = if (expanded) 180f else 0f,
                        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                        label = "mute-expand",
                    )
                    SheetActionRow(
                        icon = Res.drawable.notifications_off_24dp,
                        label = stringResource(Res.string.chats_action_mute),
                        shape = RoundedCornerShape(4.dp),
                        onClick = { expanded = !expanded },
                        trailing = {
                            Icon(
                                painterResource(Res.drawable.expand_more_24dp),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = rotation },
                            )
                        },
                    )
                    AnimatedVisibility(
                        visible = expanded,
                        enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                            fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                        exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) +
                            fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            MUTE_OPTIONS.forEach { (label, duration) ->
                                SheetActionRow(
                                    icon = null,
                                    label = stringResource(label),
                                    shape = RoundedCornerShape(4.dp),
                                    // Tónovaná plocha o stupeň níž: varianty patří k položce nad nimi.
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    onClick = { onMute(duration) },
                                )
                            }
                        }
                    }
                }

                SheetActionRow(
                    icon = Res.drawable.mark_chat_unread_24dp,
                    label = stringResource(if (chat.hasUnread) Res.string.chats_action_mark_read else Res.string.chats_action_mark_unread),
                    shape = RoundedCornerShape(4.dp),
                    onClick = onToggleUnread,
                )

                onToggleArchived?.let { onClick ->
                    val archived = chat.isArchived
                    SheetActionRow(
                        icon = if (archived) Res.drawable.unarchive_24dp else Res.drawable.archive_24dp,
                        label = stringResource(if (archived) Res.string.chats_action_unarchive else Res.string.chats_action_archive),
                        shape = RoundedCornerShape(4.dp),
                        onClick = onClick,
                    )
                }

                if (folders.isNotEmpty()) FolderChoices(folders, onToggleFolder)

                /*
                 * Zablokovat, když má chat koho. Zjišťuje se to na dotaz, takže položka naskočí o kousek
                 * později než zbytek nabídky — objevení ruší míň než zmizení a chybějící stav nesmí
                 * nabídnout akci, kterou server odmítne.
                 *
                 * Bez potvrzovacího dialogu: blokování je vratné a „Vrátit" ve snackbaru je lepší než
                 * dialog, který před vratnou akcí jen zdržuje. (V profilu chatu dialog zůstává — tam se
                 * do blokování chodí vědomě a snackbar s „Vrátit" tam není odkud ukázat.)
                 */
                if (spam?.canBlock == true) {
                    SheetActionRow(
                        icon = Res.drawable.block_24dp,
                        label = stringResource(if (spam.isBlocked) Res.string.profile_unblock else Res.string.profile_block),
                        shape = RoundedCornerShape(4.dp),
                        onClick = { onSetBlocked(!spam.isBlocked) },
                    )
                }

                /*
                 * Nahlásit samostatně, bez mazání — hlavně pro skupiny a kanály, ze kterých se odejít
                 * nechce. Popisek říká dopředu, jestli se průvodce zeptá na důvod.
                 */
                if (spam?.canReport == true) {
                    SheetActionRow(
                        icon = Res.drawable.flag_24dp,
                        label = stringResource(
                            if (spam.reportsSpamDirectly) Res.string.chats_delete_report else Res.string.chats_action_report,
                        ),
                        shape = RoundedCornerShape(4.dp),
                        onClick = onReport,
                    )
                }

                /*
                 * Smazání se rozbalí na místě do podmenu s následkem, volbami proti spamu a tlačítkem,
                 * které chat opravdu smaže. Dřív to byl dialog nad sheetem: zavřel nabídku, ze které
                 * vyšel, a volby v něm stály mimo to, čeho se týkaly.
                 */
                val deleteRotation by animateFloatAsState(
                    targetValue = if (deleteExpanded) 180f else 0f,
                    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                    label = "delete-expand",
                )
                val deleteBottom by animateDpAsState(
                    targetValue = if (deleteExpanded) 4.dp else 20.dp,
                    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                    label = "delete-corner",
                )
                SheetActionRow(
                    icon = if (chat.isPrivate) Res.drawable.delete_24dp else Res.drawable.logout_24dp,
                    // Ze skupiny se odchází, soukromý chat se maže. Jeden popisek pro obojí by lhal.
                    label = stringResource(if (chat.isPrivate) Res.string.chats_action_delete else Res.string.chats_action_leave),
                    // Pružina přestřelí; záporné zaoblení `RoundedCornerShape` odmítne.
                    shape = RoundedCornerShape(
                        topStart = 4.dp,
                        topEnd = 4.dp,
                        bottomStart = deleteBottom.coerceAtLeast(0.dp),
                        bottomEnd = deleteBottom.coerceAtLeast(0.dp),
                    ),
                    // Jediná nevratná akce v nabídce — dostane chybovou barvu, aby se nespletla
                    // s ostatními.
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    onClick = { deleteExpanded = !deleteExpanded },
                    trailing = {
                        Icon(
                            painterResource(Res.drawable.expand_more_24dp),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = deleteRotation },
                        )
                    },
                )
                AnimatedVisibility(
                    visible = deleteExpanded,
                    enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                        fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                    exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) +
                        fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                ) {
                    DeleteChatOptions(
                        isPrivate = chat.isPrivate,
                        title = chatTitle(chat.id, chat.title),
                        spam = spam,
                        onConfirm = onDelete,
                    )
                }
            }
        }
    }
}

/** Doby ztlumení v nabídce; `null` = navždy. Stejné jako v profilu chatu. */
private val MUTE_OPTIONS: List<Pair<StringResource, Duration?>> = listOf(
    Res.string.profile_notif_mute_1h to 1.hours,
    Res.string.profile_notif_mute_8h to 8.hours,
    Res.string.profile_notif_mute_2d to 2.days,
    Res.string.profile_notif_mute_forever to null,
)

/** Složka v nabídce nad chatem: název a jestli v ní chat je. */
data class FolderChoice(val id: Int, val title: String, val member: Boolean)

/**
 * „Přidat do složky" se rozbalí na místě na seznam složek, jako doby ztlumení. Zaškrtnutá složka
 * chat obsahuje; ťuknutí ho přidá, nebo odebere. Nabídka se nezavírá — do víc složek se přidává
 * po sobě.
 */
@Composable
private fun FolderChoices(folders: List<FolderChoice>, onToggle: (Int) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "folders-expand",
    )
    SheetActionRow(
        icon = Res.drawable.folder_24dp,
        label = stringResource(Res.string.chat_action_folders),
        shape = RoundedCornerShape(4.dp),
        onClick = { expanded = !expanded },
        trailing = {
            Icon(
                painterResource(Res.drawable.expand_more_24dp),
                contentDescription = null,
                modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = rotation },
            )
        },
    )
    AnimatedVisibility(
        visible = expanded,
        enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
            fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
        exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) +
            fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            folders.forEach { folder ->
                SheetActionRow(
                    icon = null,
                    label = folder.title,
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    onClick = { onToggle(folder.id) },
                    trailing = {
                        Checkbox(checked = folder.member, onCheckedChange = null)
                    },
                )
            }
        }
    }
}

/**
 * Nabídka nad chatem, který do seznamu dal někdo jiný ([ChatSponsor]). Napřed věta, PROČ tu je —
 * uživatel do něj nevstoupil a bez vysvětlení vypadá jako cizí kanál, který se objevil sám.
 *
 * - **Od proxy** smazat ani opustit nejde (TDLib „Can't delete the chat"); jediná cesta ven je
 *   proxy vypnout, a proto vede nabídka do jejího nastavení.
 * - **Oznámení** jde skrýt (`deleteChatHistory` s odebráním ze seznamu, [ChatRepository.deleteOrLeave]).
 *   Bez potvrzení: nic se nemaže, jen oznámení zmizí ze seznamu.
 */
@Composable
private fun SponsoredChatSheet(
    chat: ChatSummary,
    sponsor: ChatSponsor,
    onMute: () -> Unit,
    onUnmute: () -> Unit,
    onToggleUnread: () -> Unit,
    onOpenProxySettings: () -> Unit,
    onHide: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(
                text = chatTitle(chat.id, chat.title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            )
            Text(
                text = when (sponsor) {
                    ChatSponsor.Proxy -> stringResource(Res.string.chats_sponsor_proxy_explanation)
                    is ChatSponsor.Announcement ->
                        sponsor.text.ifBlank { stringResource(Res.string.chats_sponsor_announcement_explanation) }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            )

            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                SheetActionRow(
                    icon = if (chat.isMuted) Res.drawable.notifications_24dp else Res.drawable.notifications_off_24dp,
                    label = stringResource(if (chat.isMuted) Res.string.chats_action_unmute else Res.string.chats_action_mute),
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
                    onClick = if (chat.isMuted) onUnmute else onMute,
                )
                SheetActionRow(
                    icon = Res.drawable.mark_chat_unread_24dp,
                    label = stringResource(if (chat.hasUnread) Res.string.chats_action_mark_read else Res.string.chats_action_mark_unread),
                    shape = RoundedCornerShape(4.dp),
                    onClick = onToggleUnread,
                )
                when (sponsor) {
                    ChatSponsor.Proxy -> SheetActionRow(
                        icon = Res.drawable.vpn_lock_24dp,
                        label = stringResource(Res.string.chats_sponsor_open_proxy),
                        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 20.dp, bottomEnd = 20.dp),
                        onClick = onOpenProxySettings,
                    )
                    is ChatSponsor.Announcement -> SheetActionRow(
                        icon = Res.drawable.visibility_off_24dp,
                        label = stringResource(Res.string.chats_sponsor_hide),
                        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 20.dp, bottomEnd = 20.dp),
                        onClick = onHide,
                    )
                }
            }
        }
    }
}
