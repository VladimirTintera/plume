package eu.tintera.plume.core.ui

import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.campaign_24dp
import eu.tintera.plume.resources.celebration_24dp
import eu.tintera.plume.resources.chat_24dp
import eu.tintera.plume.resources.chat_folder_icon_airplane
import eu.tintera.plume.resources.chat_folder_icon_all
import eu.tintera.plume.resources.chat_folder_icon_book
import eu.tintera.plume.resources.chat_folder_icon_bots
import eu.tintera.plume.resources.chat_folder_icon_cat
import eu.tintera.plume.resources.chat_folder_icon_channels
import eu.tintera.plume.resources.chat_folder_icon_crown
import eu.tintera.plume.resources.chat_folder_icon_custom
import eu.tintera.plume.resources.chat_folder_icon_favorite
import eu.tintera.plume.resources.chat_folder_icon_flower
import eu.tintera.plume.resources.chat_folder_icon_game
import eu.tintera.plume.resources.chat_folder_icon_groups
import eu.tintera.plume.resources.chat_folder_icon_home
import eu.tintera.plume.resources.chat_folder_icon_light
import eu.tintera.plume.resources.chat_folder_icon_like
import eu.tintera.plume.resources.chat_folder_icon_love
import eu.tintera.plume.resources.chat_folder_icon_mask
import eu.tintera.plume.resources.chat_folder_icon_money
import eu.tintera.plume.resources.chat_folder_icon_note
import eu.tintera.plume.resources.chat_folder_icon_palette
import eu.tintera.plume.resources.chat_folder_icon_party
import eu.tintera.plume.resources.chat_folder_icon_private
import eu.tintera.plume.resources.chat_folder_icon_setup
import eu.tintera.plume.resources.chat_folder_icon_sport
import eu.tintera.plume.resources.chat_folder_icon_study
import eu.tintera.plume.resources.chat_folder_icon_trade
import eu.tintera.plume.resources.chat_folder_icon_travel
import eu.tintera.plume.resources.chat_folder_icon_unmuted
import eu.tintera.plume.resources.chat_folder_icon_unread
import eu.tintera.plume.resources.chat_folder_icon_work
import eu.tintera.plume.resources.crown_24dp
import eu.tintera.plume.resources.favorite_24dp
import eu.tintera.plume.resources.flight_24dp
import eu.tintera.plume.resources.folder_24dp
import eu.tintera.plume.resources.group_24dp
import eu.tintera.plume.resources.home_24dp
import eu.tintera.plume.resources.lightbulb_24dp
import eu.tintera.plume.resources.local_florist_24dp
import eu.tintera.plume.resources.luggage_24dp
import eu.tintera.plume.resources.mark_chat_unread_24dp
import eu.tintera.plume.resources.menu_book_24dp
import eu.tintera.plume.resources.notifications_24dp
import eu.tintera.plume.resources.palette_24dp
import eu.tintera.plume.resources.payments_24dp
import eu.tintera.plume.resources.person_24dp
import eu.tintera.plume.resources.pets_24dp
import eu.tintera.plume.resources.school_24dp
import eu.tintera.plume.resources.settings_24dp
import eu.tintera.plume.resources.smart_toy_24dp
import eu.tintera.plume.resources.sports_esports_24dp
import eu.tintera.plume.resources.sports_soccer_24dp
import eu.tintera.plume.resources.star_24dp
import eu.tintera.plume.resources.sticky_note_2_24dp
import eu.tintera.plume.resources.theater_comedy_24dp
import eu.tintera.plume.resources.thumb_up_24dp
import eu.tintera.plume.resources.trending_up_24dp
import eu.tintera.plume.resources.work_24dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

/**
 * Ikona, kterou lze složce dát: jméno, jak ho zná TDLib (`ChatFolderIcon.name`), a čím ho kreslíme.
 *
 * - **Seznam jmen je pevný a určuje ho Telegram** (dokumentace `chatFolderIcon`). Neznámé jméno
 *   (z novější verze) se kreslí obecnou složkou, nezahazuje se — přenese se při úpravě dál.
 * - **Kreslí se Material Symbols, ne ikony Telegramu**: ty jsou jejich grafika a nepatří do
 *   aplikace, která nesmí vypadat jako oficiální klient. Význam drží, tvar je náš.
 */
internal class FolderIconChoice(val name: String, val icon: DrawableResource, val label: StringResource)

internal val CHAT_FOLDER_ICONS: List<FolderIconChoice> = listOf(
    FolderIconChoice("All", Res.drawable.chat_24dp, Res.string.chat_folder_icon_all),
    FolderIconChoice("Unread", Res.drawable.mark_chat_unread_24dp, Res.string.chat_folder_icon_unread),
    FolderIconChoice("Unmuted", Res.drawable.notifications_24dp, Res.string.chat_folder_icon_unmuted),
    FolderIconChoice("Bots", Res.drawable.smart_toy_24dp, Res.string.chat_folder_icon_bots),
    FolderIconChoice("Channels", Res.drawable.campaign_24dp, Res.string.chat_folder_icon_channels),
    FolderIconChoice("Groups", Res.drawable.group_24dp, Res.string.chat_folder_icon_groups),
    FolderIconChoice("Private", Res.drawable.person_24dp, Res.string.chat_folder_icon_private),
    FolderIconChoice("Custom", Res.drawable.folder_24dp, Res.string.chat_folder_icon_custom),
    FolderIconChoice("Setup", Res.drawable.settings_24dp, Res.string.chat_folder_icon_setup),
    FolderIconChoice("Cat", Res.drawable.pets_24dp, Res.string.chat_folder_icon_cat),
    FolderIconChoice("Crown", Res.drawable.crown_24dp, Res.string.chat_folder_icon_crown),
    FolderIconChoice("Favorite", Res.drawable.star_24dp, Res.string.chat_folder_icon_favorite),
    FolderIconChoice("Flower", Res.drawable.local_florist_24dp, Res.string.chat_folder_icon_flower),
    FolderIconChoice("Game", Res.drawable.sports_esports_24dp, Res.string.chat_folder_icon_game),
    FolderIconChoice("Home", Res.drawable.home_24dp, Res.string.chat_folder_icon_home),
    FolderIconChoice("Love", Res.drawable.favorite_24dp, Res.string.chat_folder_icon_love),
    FolderIconChoice("Mask", Res.drawable.theater_comedy_24dp, Res.string.chat_folder_icon_mask),
    FolderIconChoice("Party", Res.drawable.celebration_24dp, Res.string.chat_folder_icon_party),
    FolderIconChoice("Sport", Res.drawable.sports_soccer_24dp, Res.string.chat_folder_icon_sport),
    FolderIconChoice("Study", Res.drawable.school_24dp, Res.string.chat_folder_icon_study),
    FolderIconChoice("Trade", Res.drawable.trending_up_24dp, Res.string.chat_folder_icon_trade),
    FolderIconChoice("Travel", Res.drawable.luggage_24dp, Res.string.chat_folder_icon_travel),
    FolderIconChoice("Work", Res.drawable.work_24dp, Res.string.chat_folder_icon_work),
    FolderIconChoice("Airplane", Res.drawable.flight_24dp, Res.string.chat_folder_icon_airplane),
    FolderIconChoice("Book", Res.drawable.menu_book_24dp, Res.string.chat_folder_icon_book),
    FolderIconChoice("Light", Res.drawable.lightbulb_24dp, Res.string.chat_folder_icon_light),
    FolderIconChoice("Like", Res.drawable.thumb_up_24dp, Res.string.chat_folder_icon_like),
    FolderIconChoice("Money", Res.drawable.payments_24dp, Res.string.chat_folder_icon_money),
    FolderIconChoice("Note", Res.drawable.sticky_note_2_24dp, Res.string.chat_folder_icon_note),
    FolderIconChoice("Palette", Res.drawable.palette_24dp, Res.string.chat_folder_icon_palette),
)

/** Ikona ke jménu od TDLib; bez jména nebo s neznámým obecná složka. */
internal fun folderIcon(name: String?): DrawableResource =
    CHAT_FOLDER_ICONS.firstOrNull { it.name == name }?.icon ?: Res.drawable.folder_24dp
