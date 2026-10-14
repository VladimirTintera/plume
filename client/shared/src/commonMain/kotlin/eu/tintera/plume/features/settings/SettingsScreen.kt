package eu.tintera.plume.features.settings

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.ai.OnDeviceAi
import eu.tintera.plume.core.navigation.SettingsSection
import eu.tintera.plume.core.ui.GROUP_CORNER
import eu.tintera.plume.core.ui.GROUP_ITEM_SPACING
import eu.tintera.plume.core.ui.INNER_CORNER
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.auto_awesome_24dp
import eu.tintera.plume.resources.campaign_24dp
import eu.tintera.plume.resources.common_cancel
import eu.tintera.plume.resources.devices_24dp
import eu.tintera.plume.resources.forum_24dp
import eu.tintera.plume.resources.hard_drive_24dp
import eu.tintera.plume.resources.lock_24dp
import eu.tintera.plume.resources.my_qr_title
import eu.tintera.plume.resources.notifications_24dp
import eu.tintera.plume.resources.palette_24dp
import eu.tintera.plume.resources.qr_code_24dp
import eu.tintera.plume.resources.settings_edit_profile
import eu.tintera.plume.resources.settings_section_ai
import eu.tintera.plume.resources.settings_section_ai_summary
import eu.tintera.plume.resources.settings_section_appearance
import eu.tintera.plume.resources.settings_section_appearance_summary
import eu.tintera.plume.resources.settings_section_chats
import eu.tintera.plume.resources.settings_section_chats_summary
import eu.tintera.plume.resources.settings_section_devices
import eu.tintera.plume.resources.settings_section_devices_summary
import eu.tintera.plume.resources.settings_section_notifications
import eu.tintera.plume.resources.settings_section_notifications_summary
import eu.tintera.plume.resources.settings_section_privacy
import eu.tintera.plume.resources.settings_section_privacy_summary
import eu.tintera.plume.resources.settings_section_storage
import eu.tintera.plume.resources.settings_section_storage_summary
import eu.tintera.plume.resources.settings_section_whats_new
import eu.tintera.plume.resources.settings_section_whats_new_summary
import eu.tintera.plume.resources.settings_sign_out
import eu.tintera.plume.resources.settings_sign_out_body
import eu.tintera.plume.resources.settings_sign_out_confirm
import eu.tintera.plume.resources.settings_sign_out_title
import eu.tintera.plume.resources.settings_title
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Seznam Nastavení — v list-detail rozvržení levý panel. Odshora: karta účtu (úprava profilu),
 * sekce, jazyk, pomoc a odhlášení. Rozvrh a proč je takový: `docs/roadmap.md`, „Přeskupení sekcí".
 *
 * - **Karta účtu je tady, ne v sekci.** Kdo jsem a jak to změnit je první věc, kterou kdo
 *   v Nastavení hledá; dřív se našla až po otevření sběrné sekce Obecné.
 * - **Jazyk je řádek, ne sekce.** Hledá ho i ten, kdo vzhled neřeší, a sekce s jedinou volbou
 *   by byla navíc.
 * - **AI v zařízení se nenabízí tam, kde žádný model není** — stejné pravidlo jako uvnitř sekce
 *   (nabídka funkce, kterou nelze mít).
 * - **Co je nového stojí v Pomoci**: je to čtení, ne nastavení.
 * - **Odhlásit se je poslední** a v chybové barvě, s potvrzením, které říká následek.
 */
@Composable
fun SettingsScreen(
    selectedSection: SettingsSection?,
    /** Vedle seznamu je otevřená úprava profilu — karta účtu je vybraná. */
    accountSelected: Boolean,
    onSectionClick: (SettingsSection) -> Unit,
    onEditProfile: () -> Unit,
    modifier: Modifier = Modifier,
    /** Šipka zpět do Chatů. Nastavení se otevírají z menu účtu, jiná cesta zpět není. */
    onBack: (() -> Unit)? = null,
    /** Otevřít chat (s podporou Telegramu z „Zeptat se"). */
    onOpenChat: (Long) -> Unit = {},
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val scrollBehavior = settingsScrollBehavior()
    val ai = koinInject<OnDeviceAi>()
    val aiAvailable by remember(ai) { ai.availability().map { it.providers.isNotEmpty() } }
        .collectAsStateWithLifecycle(initialValue = false)
    val sections = SettingsSection.entries.filter { section ->
        when (section) {
            SettingsSection.Ai -> aiAvailable
            SettingsSection.WhatsNew -> false
            else -> true
        }
    }
    var confirmLogOut by rememberSaveable { mutableStateOf(false) }
    if (confirmLogOut) {
        LogOutDialog(
            onConfirm = {
                confirmLogOut = false
                viewModel.logOut()
            },
            onDismiss = { confirmLogOut = false },
        )
    }

    Scaffold(
        modifier = modifier.settingsScroll(scrollBehavior),
        topBar = {
            SettingsTopBar(title = stringResource(Res.string.settings_title), onBack = onBack, scrollBehavior = scrollBehavior)
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .settingsContentPadding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = settingsContentBottom(padding, 8.dp)),
            verticalArrangement = Arrangement.spacedBy(BLOCK_SPACING),
        ) {
            AccountCard(viewModel = viewModel, selected = accountSelected, onClick = onEditProfile)

            // Sekce jsou jeden blok se společným zaoblením, stejný jazyk jako seznam chatů.
            Column(verticalArrangement = Arrangement.spacedBy(GROUP_ITEM_SPACING)) {
                sections.forEachIndexed { index, section ->
                    SettingsListRow(
                        icon = section.icon,
                        title = stringResource(section.title),
                        summary = stringResource(section.summary),
                        first = index == 0,
                        last = index == sections.lastIndex,
                        selected = section == selectedSection,
                        onClick = { onSectionClick(section) },
                    )
                }
            }

            LanguageRow()

            HelpGroup(
                onOpenChat = onOpenChat,
                whatsNewSelected = selectedSection == SettingsSection.WhatsNew,
                onOpenWhatsNew = { onSectionClick(SettingsSection.WhatsNew) },
            )

            // Destruktivní akce dostane error roli a potvrzení s konkrétním následkem.
            Button(
                onClick = { confirmLogOut = true },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Text(stringResource(Res.string.settings_sign_out))
            }
        }
    }
}

/**
 * Kdo je přihlášený. Ťuknutí vede do úpravy profilu — tam, kde je vidět, co se upravuje; QR kód
 * je vedle jména jako v oficiálních klientech (ukazuje se ten účet, který karta popisuje).
 *
 * Stejný blok jako řádky sekcí pod ní, vybraný stejnou rolí: ve dvoupanelu stojí úprava profilu
 * vedle seznamu místo sekce.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AccountCard(viewModel: SettingsViewModel, selected: Boolean, onClick: () -> Unit) {
    val account by viewModel.account.collectAsStateWithLifecycle()
    var showMyQrCode by rememberSaveable { mutableStateOf(false) }
    if (showMyQrCode) MyQrCodeSheet(onDismiss = { showMyQrCode = false })

    val container = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest
    val content = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
    val secondary = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(GROUP_CORNER),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val current = account
                if (current == null) {
                    LoadingIndicator()
                } else {
                    Text(current.displayName, style = MaterialTheme.typography.titleLarge)
                    Text(
                        current.phoneNumber.let { if (it.startsWith("+")) it else "+$it" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondary,
                    )
                    current.username?.let {
                        Text(
                            "@$it",
                            style = MaterialTheme.typography.bodyMedium,
                            color = secondary,
                        )
                    }
                    Text(
                        stringResource(Res.string.settings_edit_profile),
                        style = MaterialTheme.typography.labelLarge,
                        // Akcent jen na nevybrané kartě — na `secondaryContainer` by `primary`
                        // nemusela mít kontrast (viz „Akcent na tónované ploše" v CLAUDE.md).
                        color = if (selected) content else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            if (account != null) {
                FilledTonalIconButton(onClick = { showMyQrCode = true }, shapes = IconButtonDefaults.shapes()) {
                    Icon(painterResource(Res.drawable.qr_code_24dp), contentDescription = stringResource(Res.string.my_qr_title))
                }
            }
        }
    }
}

/**
 * Řádek seznamu Nastavení: sekce, jazyk, položka Pomoci. Řádky jdou v blocích se společným
 * zaoblením krajních položek, jako seznam chatů.
 *
 * Výběr v list-detail je navigační stav, ne zvýraznění obsahu → `secondaryContainer`, stejná role,
 * jakou nese vybraný chat v seznamu. Vybraná položka se z bloku vyčlení: zaoblená celá, jako
 * skupina sama (M3 Expressive); zaoblení se animuje, protože se blok při výběru přeskládá.
 */
@Composable
internal fun SettingsListRow(
    icon: DrawableResource,
    title: String,
    summary: String?,
    first: Boolean,
    last: Boolean,
    onClick: () -> Unit,
    selected: Boolean = false,
) {
    val containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest
    val contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
    // Popis je doplněk k názvu, takže je tlumený — na vybraném řádku ale nesmí zůstat
    // `onSurfaceVariant`, ten je počítaný na jinou plochu.
    val supportingColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

    val topCorner by animateDpAsState(
        targetValue = if (first || selected) GROUP_CORNER else INNER_CORNER,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "settings-corner-top",
    )
    val bottomCorner by animateDpAsState(
        targetValue = if (last || selected) GROUP_CORNER else INNER_CORNER,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "settings-corner-bottom",
    )
    val shape = RoundedCornerShape(topStart = topCorner, topEnd = topCorner, bottomStart = bottomCorner, bottomEnd = bottomCorner)

    ListItem(
        colors = ListItemDefaults.colors(
            containerColor = containerColor,
            contentColor = contentColor,
            supportingContentColor = supportingColor,
            leadingContentColor = supportingColor,
        ),
        // Ikona je dekorace: co řádek je, říká název vedle ní, takže čtečce nic nepřidá.
        leadingContent = { Icon(painterResource(icon), contentDescription = null) },
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it) } },
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick),
    )
}

/**
 * Potvrzení odhlášení. Pojmenovává následek: odhlášení maže lokální data TDLib, takže se po
 * novém přihlášení všechno stahuje znovu. Účtu samotnému ani ostatním zařízením se nic nestane.
 */
@Composable
private fun LogOutDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.settings_sign_out_title)) },
        text = { Text(stringResource(Res.string.settings_sign_out_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(Res.string.settings_sign_out_confirm), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) }
        },
    )
}

/** Název sekce. Nese ho jedno místo, protože stojí i v hlavičce otevřené sekce. */
internal val SettingsSection.title: StringResource
    get() = when (this) {
        SettingsSection.Privacy -> Res.string.settings_section_privacy
        SettingsSection.Notifications -> Res.string.settings_section_notifications
        SettingsSection.Chats -> Res.string.settings_section_chats
        SettingsSection.Appearance -> Res.string.settings_section_appearance
        SettingsSection.Storage -> Res.string.settings_section_storage
        SettingsSection.Devices -> Res.string.settings_section_devices
        SettingsSection.Ai -> Res.string.settings_section_ai
        SettingsSection.WhatsNew -> Res.string.settings_section_whats_new
    }

/**
 * Ikona sekce — v delším seznamu se řádek najde koutkem oka, ne podle slov.
 * „Co je nového" má megafon, ne raketu: raketa uvnitř sekce značí novinku u jedné poznámky.
 */
internal val SettingsSection.icon: DrawableResource
    get() = when (this) {
        SettingsSection.Privacy -> Res.drawable.lock_24dp
        SettingsSection.Notifications -> Res.drawable.notifications_24dp
        SettingsSection.Chats -> Res.drawable.forum_24dp
        SettingsSection.Appearance -> Res.drawable.palette_24dp
        SettingsSection.Storage -> Res.drawable.hard_drive_24dp
        SettingsSection.Devices -> Res.drawable.devices_24dp
        SettingsSection.Ai -> Res.drawable.auto_awesome_24dp
        SettingsSection.WhatsNew -> Res.drawable.campaign_24dp
    }

/** Co v sekci je. Bez toho by ze seznamu slov nebylo poznat, kde co hledat. */
internal val SettingsSection.summary: StringResource
    get() = when (this) {
        SettingsSection.Privacy -> Res.string.settings_section_privacy_summary
        SettingsSection.Notifications -> Res.string.settings_section_notifications_summary
        SettingsSection.Chats -> Res.string.settings_section_chats_summary
        SettingsSection.Appearance -> Res.string.settings_section_appearance_summary
        SettingsSection.Storage -> Res.string.settings_section_storage_summary
        SettingsSection.Devices -> Res.string.settings_section_devices_summary
        SettingsSection.Ai -> Res.string.settings_section_ai_summary
        SettingsSection.WhatsNew -> Res.string.settings_section_whats_new_summary
    }

private val BLOCK_SPACING = 16.dp
