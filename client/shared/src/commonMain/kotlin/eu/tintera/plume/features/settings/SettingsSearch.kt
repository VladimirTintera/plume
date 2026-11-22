package eu.tintera.plume.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import eu.tintera.plume.core.navigation.AccountPrivacyRoute
import eu.tintera.plume.core.navigation.AppLockRoute
import eu.tintera.plume.core.navigation.BlockedSendersRoute
import eu.tintera.plume.core.navigation.ChangePhoneRoute
import eu.tintera.plume.core.navigation.ChatFoldersRoute
import eu.tintera.plume.core.navigation.DeleteAccountRoute
import eu.tintera.plume.core.navigation.EditProfileRoute
import eu.tintera.plume.core.navigation.EmojiSetsRoute
import eu.tintera.plume.core.navigation.LoginEmailRoute
import eu.tintera.plume.core.navigation.NetworkUsageRoute
import eu.tintera.plume.core.navigation.LicensesRoute
import eu.tintera.plume.core.navigation.PasskeysRoute
import eu.tintera.plume.core.navigation.PrivacySettingRoute
import eu.tintera.plume.core.navigation.ProxySettingsRoute
import eu.tintera.plume.core.navigation.SettingsSection
import eu.tintera.plume.core.navigation.SettingsSectionRoute
import eu.tintera.plume.core.navigation.StickerSetsRoute
import eu.tintera.plume.core.navigation.TwoStepRoute
import eu.tintera.plume.core.telegram.PrivacySetting
import eu.tintera.plume.core.ui.GROUP_ITEM_SPACING
import eu.tintera.plume.core.ui.GroupEmptyRow
import eu.tintera.plume.core.ui.foldDiacritics
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.about_check_update
import eu.tintera.plume.resources.about_source
import eu.tintera.plume.resources.auto_download_title
import eu.tintera.plume.resources.autosave_group
import eu.tintera.plume.resources.birthdate_row
import eu.tintera.plume.resources.change_phone_title
import eu.tintera.plume.resources.chat_folders_title
import eu.tintera.plume.resources.chats_search_empty
import eu.tintera.plume.resources.delete_account_title
import eu.tintera.plume.resources.devices_active
import eu.tintera.plume.resources.devices_auto_terminate
import eu.tintera.plume.resources.devices_websites
import eu.tintera.plume.resources.edit_profile_bio
import eu.tintera.plume.resources.edit_profile_change_photo
import eu.tintera.plume.resources.edit_profile_first_name
import eu.tintera.plume.resources.edit_profile_title
import eu.tintera.plume.resources.edit_profile_username
import eu.tintera.plume.resources.emoji_sets_title
import eu.tintera.plume.resources.network_usage_title
import eu.tintera.plume.resources.licenses_title
import eu.tintera.plume.resources.settings_search_kw_licenses
import eu.tintera.plume.resources.person_24dp
import eu.tintera.plume.resources.personal_channel_row
import eu.tintera.plume.resources.privacy_account_ttl
import eu.tintera.plume.resources.proxy_title
import eu.tintera.plume.resources.quiet_hours_title
import eu.tintera.plume.resources.search_off_24dp
import eu.tintera.plume.resources.settings_account_privacy
import eu.tintera.plume.resources.settings_ai_tasks
import eu.tintera.plume.resources.settings_app_lock
import eu.tintera.plume.resources.settings_background_photos
import eu.tintera.plume.resources.settings_blocked
import eu.tintera.plume.resources.settings_chat_triage
import eu.tintera.plume.resources.settings_ai_wifi_only
import eu.tintera.plume.resources.ai_playground_title
import eu.tintera.plume.resources.settings_default_auto_delete
import eu.tintera.plume.resources.settings_discreet_reading
import eu.tintera.plume.resources.settings_login_email
import eu.tintera.plume.resources.settings_chat_preview_lines
import eu.tintera.plume.resources.settings_chat_row_gaps
import eu.tintera.plume.resources.settings_search_kw_chat_row_gaps
import eu.tintera.plume.resources.settings_message_text_size
import eu.tintera.plume.resources.settings_search_kw_chat_preview_lines
import eu.tintera.plume.resources.settings_search_kw_sticker_size
import eu.tintera.plume.resources.settings_search_kw_about
import eu.tintera.plume.resources.settings_sticker_size
import eu.tintera.plume.resources.settings_notif_contact_joined
import eu.tintera.plume.resources.settings_notif_mentions
import eu.tintera.plume.resources.settings_notif_pinned
import eu.tintera.plume.resources.settings_notif_preview
import eu.tintera.plume.resources.settings_notif_reset_all
import eu.tintera.plume.resources.settings_notif_stories
import eu.tintera.plume.resources.settings_open_links_in_app
import eu.tintera.plume.resources.settings_passkeys
import eu.tintera.plume.resources.settings_quick_reaction
import eu.tintera.plume.resources.settings_reply_suggestions
import eu.tintera.plume.resources.settings_scam_warnings
import eu.tintera.plume.resources.settings_search_kw_app_lock
import eu.tintera.plume.resources.settings_search_kw_blocked
import eu.tintera.plume.resources.settings_search_kw_devices
import eu.tintera.plume.resources.settings_search_kw_folders
import eu.tintera.plume.resources.settings_search_kw_last_seen
import eu.tintera.plume.resources.settings_search_kw_proxy
import eu.tintera.plume.resources.settings_search_kw_quiet_hours
import eu.tintera.plume.resources.settings_search_kw_storage
import eu.tintera.plume.resources.settings_search_kw_text_size
import eu.tintera.plume.resources.settings_search_kw_theme
import eu.tintera.plume.resources.settings_search_kw_two_step
import eu.tintera.plume.resources.settings_sensitive_content
import eu.tintera.plume.resources.settings_skip_silence
import eu.tintera.plume.resources.settings_strip_tracking_params
import eu.tintera.plume.resources.settings_swipe_left
import eu.tintera.plume.resources.settings_swipe_right
import eu.tintera.plume.resources.settings_telegram_links
import eu.tintera.plume.resources.settings_theme
import eu.tintera.plume.resources.settings_theme_color
import eu.tintera.plume.resources.settings_two_step
import eu.tintera.plume.resources.sticker_sets_title
import eu.tintera.plume.resources.storage_cache_limit
import eu.tintera.plume.resources.storage_clear
import eu.tintera.plume.resources.storage_keep_media
import eu.tintera.plume.resources.storage_used
import eu.tintera.plume.resources.usernames_section
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Jedna volba v rejstříku hledání v Nastavení. [screens] jsou obrazovky nad seznamem sekcí po
 * řadě, kudy by se k volbě uživatel proklikal sám (sekce, podstránka, …) — tak vede i zpět.
 * [keywords] jsou synonyma, která v názvu nejsou („tmavý režim" u Motivu), čárkami.
 */
internal data class SettingsSearchEntry(
    val title: StringResource,
    val screens: List<NavKey>,
    val keywords: StringResource? = null,
) {
    val section: SettingsSection?
        get() = (screens.firstOrNull() as? SettingsSectionRoute)?.section

    /**
     * Na co se otevřená obrazovka doscrolluje: na řádek s [title]. Když je volba obrazovka sama
     * (sekce, Dvoufázové ověření), není co hledat — `null`.
     */
    val highlight: StringResource?
        get() = title.takeIf { screens.last().pathTitle != it }
}

/**
 * Rejstřík voleb, ve kterém hledání v Nastavení hledá. **Názvy jsou tytéž prostředky, které nese
 * řádek v sekci** — co uživatel najde, je přesně to, co pak na obrazovce uvidí. Rejstřík je
 * ruční: volby žijí v ~40 composable funkcích s podmínkami a strom obrazovek se za běhu projít
 * nedá. Kdo přidává řádek, který lidé budou hledat, přidá ho i sem.
 *
 * Vědomě chybí řádky jen pro jednu platformu (widget, spouštění po přihlášení, synchronizace
 * kontaktů, hovory přes celou obrazovku, dynamické barvy): hledání by vedlo na volbu, která
 * na zařízení není. Řádky kořene (Jazyk, Pomoc) jsou vidět i bez hledání.
 */
internal val SETTINGS_SEARCH_INDEX: List<SettingsSearchEntry> = buildList {
    fun section(section: SettingsSection, vararg pages: NavKey) = listOf(SettingsSectionRoute(section)) + pages

    // Sekce samy: „Soukromí" najde sekci, ne každou volbu v ní.
    SettingsSection.entries.forEach { add(SettingsSearchEntry(it.title, section(it))) }

    val privacy = SettingsSection.Privacy
    add(SettingsSearchEntry(Res.string.settings_blocked, section(privacy, BlockedSendersRoute), Res.string.settings_search_kw_blocked))
    add(SettingsSearchEntry(Res.string.settings_account_privacy, section(privacy, AccountPrivacyRoute)))
    PrivacySetting.entries.forEach { setting ->
        add(
            SettingsSearchEntry(
                setting.title,
                section(privacy, AccountPrivacyRoute, PrivacySettingRoute(setting)),
                Res.string.settings_search_kw_last_seen.takeIf { setting == PrivacySetting.LastSeen },
            ),
        )
    }
    add(SettingsSearchEntry(Res.string.privacy_account_ttl, section(privacy, AccountPrivacyRoute)))
    add(SettingsSearchEntry(Res.string.delete_account_title, section(privacy, AccountPrivacyRoute, DeleteAccountRoute)))
    add(SettingsSearchEntry(Res.string.settings_two_step, section(privacy, TwoStepRoute), Res.string.settings_search_kw_two_step))
    add(SettingsSearchEntry(Res.string.settings_app_lock, section(privacy, AppLockRoute), Res.string.settings_search_kw_app_lock))
    add(SettingsSearchEntry(Res.string.settings_login_email, section(privacy, LoginEmailRoute)))
    add(SettingsSearchEntry(Res.string.settings_passkeys, section(privacy, PasskeysRoute)))
    listOf(
        Res.string.settings_discreet_reading,
        Res.string.settings_sensitive_content,
        Res.string.settings_default_auto_delete,
        Res.string.settings_strip_tracking_params,
    ).forEach { add(SettingsSearchEntry(it, section(privacy))) }

    val notifications = SettingsSection.Notifications
    add(SettingsSearchEntry(Res.string.quiet_hours_title, section(notifications), Res.string.settings_search_kw_quiet_hours))
    listOf(
        Res.string.settings_notif_mentions,
        Res.string.settings_notif_pinned,
        Res.string.settings_notif_preview,
        Res.string.settings_notif_stories,
        Res.string.settings_notif_contact_joined,
        Res.string.settings_notif_reset_all,
    ).forEach { add(SettingsSearchEntry(it, section(notifications))) }

    val chats = SettingsSection.Chats
    add(SettingsSearchEntry(Res.string.chat_folders_title, section(chats, ChatFoldersRoute), Res.string.settings_search_kw_folders))
    add(SettingsSearchEntry(Res.string.sticker_sets_title, section(chats, StickerSetsRoute)))
    add(SettingsSearchEntry(Res.string.emoji_sets_title, section(chats, EmojiSetsRoute)))
    listOf(
        Res.string.settings_swipe_left,
        Res.string.settings_swipe_right,
        Res.string.settings_quick_reaction,
        Res.string.settings_open_links_in_app,
        Res.string.settings_telegram_links,
        Res.string.settings_skip_silence,
    ).forEach { add(SettingsSearchEntry(it, section(chats))) }

    val appearance = SettingsSection.Appearance
    add(SettingsSearchEntry(Res.string.settings_theme, section(appearance), Res.string.settings_search_kw_theme))
    add(SettingsSearchEntry(Res.string.settings_theme_color, section(appearance)))
    add(SettingsSearchEntry(Res.string.settings_message_text_size, section(appearance), Res.string.settings_search_kw_text_size))
    add(SettingsSearchEntry(Res.string.settings_sticker_size, section(appearance), Res.string.settings_search_kw_sticker_size))
    add(SettingsSearchEntry(Res.string.settings_chat_preview_lines, section(appearance), Res.string.settings_search_kw_chat_preview_lines))
    add(SettingsSearchEntry(Res.string.settings_chat_row_gaps, section(appearance), Res.string.settings_search_kw_chat_row_gaps))

    val storage = SettingsSection.Storage
    add(SettingsSearchEntry(Res.string.storage_used, section(storage), Res.string.settings_search_kw_storage))
    add(SettingsSearchEntry(Res.string.storage_clear, section(storage)))
    add(SettingsSearchEntry(Res.string.storage_keep_media, section(storage)))
    add(SettingsSearchEntry(Res.string.storage_cache_limit, section(storage)))
    add(SettingsSearchEntry(Res.string.auto_download_title, section(storage)))
    add(SettingsSearchEntry(Res.string.autosave_group, section(storage)))
    add(SettingsSearchEntry(Res.string.network_usage_title, section(storage, NetworkUsageRoute)))
    add(SettingsSearchEntry(Res.string.proxy_title, section(storage, ProxySettingsRoute), Res.string.settings_search_kw_proxy))

    val devices = SettingsSection.Devices
    add(SettingsSearchEntry(Res.string.devices_active, section(devices), Res.string.settings_search_kw_devices))
    add(SettingsSearchEntry(Res.string.devices_auto_terminate, section(devices)))
    add(SettingsSearchEntry(Res.string.devices_websites, section(devices)))

    val ai = SettingsSection.Ai
    listOf(
        Res.string.settings_reply_suggestions,
        Res.string.settings_scam_warnings,
        Res.string.settings_chat_triage,
        Res.string.settings_background_photos,
        Res.string.settings_ai_tasks,
        Res.string.settings_ai_wifi_only,
        // Rovnou do hřiště ne: řádek je v sekci jen se zapnutou AI a sekce sama řekne proč.
        Res.string.ai_playground_title,
    ).forEach { add(SettingsSearchEntry(it, section(ai))) }

    val about = SettingsSection.About
    add(SettingsSearchEntry(Res.string.about_check_update, section(about), Res.string.settings_search_kw_about))
    add(SettingsSearchEntry(Res.string.about_source, section(about), Res.string.settings_search_kw_about))
    add(SettingsSearchEntry(Res.string.licenses_title, section(about, LicensesRoute), Res.string.settings_search_kw_licenses))

    // Úprava profilu stojí nad seznamem místo sekce (karta účtu).
    listOf(
        Res.string.edit_profile_first_name,
        Res.string.edit_profile_username,
        Res.string.edit_profile_bio,
        Res.string.birthdate_row,
        Res.string.edit_profile_change_photo,
        Res.string.personal_channel_row,
        Res.string.usernames_section,
    ).forEach { add(SettingsSearchEntry(it, listOf(EditProfileRoute))) }
    add(SettingsSearchEntry(Res.string.change_phone_title, listOf(EditProfileRoute, ChangePhoneRoute)))
}

/** Volba s textem v jazyce aplikace — co se porovnává a co se ukazuje. */
internal data class ResolvedSettingsEntry(
    val entry: SettingsSearchEntry,
    val title: String,
    val keywords: String,
    /** Cesta k volbě („Soukromí › Soukromí účtu"); prázdná u sekce samotné. */
    val path: String,
)

/**
 * Výsledky pro [query], od nejlepších. Každé slovo dotazu musí být začátkem některého slova
 * názvu nebo synonym — „vid" najde „Naposledy viděn", „zam apl" „Zámek aplikace". Bez ohledu na
 * velikost písmen a diakritiku ([foldDiacritics]), jako hledání zpráv.
 *
 * Pořadí: název začínající dotazem, pak shoda ve slovech názvu, nakonec shoda jen v synonymech;
 * uvnitř skupiny pořadí rejstříku (sekce, pak volby, jak jdou v Nastavení).
 */
internal fun searchSettings(query: String, entries: List<ResolvedSettingsEntry>): List<ResolvedSettingsEntry> {
    val needle = normalize(query)
    val words = needle.split(' ').filter { it.isNotEmpty() }
    if (words.isEmpty()) return emptyList()
    return entries
        .mapNotNull { entry ->
            val title = normalize(entry.title)
            val titleWords = title.split(' ')
            val keywordWords = normalize(entry.keywords).split(' ', ',').filter { it.isNotEmpty() }
            val inTitle = words.all { word -> titleWords.any { it.startsWith(word) } }
            val score = when {
                title.startsWith(needle) -> 0
                inTitle -> 1
                words.all { word -> (titleWords + keywordWords).any { it.startsWith(word) } } -> 2
                else -> return@mapNotNull null
            }
            score to entry
        }
        .sortedBy { it.first }
        .map { it.second }
        // Táž volba dvakrát (sekce i řádek se stejným názvem) by byla jen šum.
        .distinctBy { it.title to it.path }
}

private fun normalize(text: String): String =
    foldDiacritics(text).lowercase().replace(Regex("[^\\p{L}\\p{N},]+"), " ").trim()

/** Název obrazovky v cestě k volbě. */
internal val NavKey.pathTitle: StringResource?
    get() = when (this) {
        is SettingsSectionRoute -> section.title
        is PrivacySettingRoute -> setting.title
        AccountPrivacyRoute -> Res.string.settings_account_privacy
        BlockedSendersRoute -> Res.string.settings_blocked
        DeleteAccountRoute -> Res.string.delete_account_title
        TwoStepRoute -> Res.string.settings_two_step
        AppLockRoute -> Res.string.settings_app_lock
        LoginEmailRoute -> Res.string.settings_login_email
        PasskeysRoute -> Res.string.settings_passkeys
        ChatFoldersRoute -> Res.string.chat_folders_title
        StickerSetsRoute -> Res.string.sticker_sets_title
        EmojiSetsRoute -> Res.string.emoji_sets_title
        NetworkUsageRoute -> Res.string.network_usage_title
        LicensesRoute -> Res.string.licenses_title
        ProxySettingsRoute -> Res.string.proxy_title
        EditProfileRoute -> Res.string.edit_profile_title
        ChangePhoneRoute -> Res.string.change_phone_title
        else -> null
    }

/** Rejstřík s texty v jazyce aplikace; [sections] jsou sekce, které seznam právě nabízí. */
@Composable
internal fun rememberResolvedSettingsIndex(sections: List<SettingsSection>): List<ResolvedSettingsEntry> =
    SETTINGS_SEARCH_INDEX
        .filter { entry -> entry.section.let { it == null || it in sections || it == SettingsSection.WhatsNew || it == SettingsSection.About } }
        .map { entry ->
            val title = stringResource(entry.title)
            // Cesta bez obrazovky, kterou volba sama je (Dvoufázové ověření → jen „Soukromí").
            val path = entry.screens.mapNotNull { it.pathTitle }.map { stringResource(it) }.filter { it != title }
            ResolvedSettingsEntry(
                entry = entry,
                title = title,
                keywords = entry.keywords?.let { stringResource(it) }.orEmpty(),
                path = path.joinToString(PATH_SEPARATOR),
            )
        }

/**
 * Výsledky hledání místo obsahu seznamu: jeden blok řádků s ikonou sekce, názvem volby a cestou
 * k ní pod ním. Ťuknutí otevře obrazovku, kde volba je, i s podstránkami po cestě.
 */
@Composable
internal fun SettingsSearchResults(
    results: List<ResolvedSettingsEntry>,
    onOpen: (screens: List<NavKey>, highlight: StringResource?) -> Unit,
) {
    if (results.isEmpty()) {
        GroupEmptyRow(stringResource(Res.string.chats_search_empty), icon = Res.drawable.search_off_24dp)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(GROUP_ITEM_SPACING)) {
        results.forEachIndexed { index, result ->
            SettingsListRow(
                // Úprava profilu sekci nemá: postava jako u karty účtu.
                icon = result.entry.section?.icon ?: Res.drawable.person_24dp,
                title = result.title,
                summary = result.path.ifEmpty { null },
                first = index == 0,
                last = index == results.lastIndex,
                onClick = { onOpen(result.entry.screens, result.entry.highlight) },
            )
        }
    }
}

private const val PATH_SEPARATOR = " › "
