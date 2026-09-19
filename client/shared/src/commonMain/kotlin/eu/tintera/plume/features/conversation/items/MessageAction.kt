package eu.tintera.plume.features.conversation.items

import eu.tintera.plume.core.ui.label
import eu.tintera.plume.features.conversation.MessageMenu
import eu.tintera.plume.core.files.saveAsSupported
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.content_copy_24dp
import eu.tintera.plume.resources.delete_24dp
import eu.tintera.plume.resources.description_24dp
import eu.tintera.plume.resources.media_save_as
import eu.tintera.plume.resources.media_share
import eu.tintera.plume.resources.share_24dp
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
import eu.tintera.plume.resources.menu_pin
import eu.tintera.plume.resources.menu_reply
import eu.tintera.plume.resources.menu_unpin
import eu.tintera.plume.resources.push_pin_24dp
import eu.tintera.plume.resources.reply_24dp
import eu.tintera.plume.resources.star_24dp
import eu.tintera.plume.resources.star_filled_24dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

/**
 * Co se dá se zprávou udělat — jako data, ne jako lambda.
 *
 * Bloky zpráv i nabídka posílají akce jedinou cestou, `ConversationItemScope.onAction(id, akce)`,
 * a ViewModel je rozděluje na jednom místě (`ConversationViewModel.onAction`). Dřív to bylo
 * jedenáct lambd v `MessageMenuHandlers` a dalších patnáct parametrů bubliny, které se musely
 * protahovat obrazovkou.
 *
 * `messageId` jde vedle akce, ne v ní: u alba se ťuká na konkrétní fotku, takže je to id té
 * fotky, ne bloku.
 */
sealed interface MessageAction {
    /** Dlouhý stisk: otevře nabídku s [items] — kandidáty, které nabízí blok (viz [MessageMenuAction]). */
    data class ShowMenu(val items: List<MessageMenuAction>) : MessageAction

    /** Zavřít nabídku (zpět, ťuknutí mimo). */
    data object CloseMenu : MessageAction

    /** Reakce z pruhu nebo z plného výběru v nabídce; přepíná (přidá / odebere). */
    data class React(val emoji: String) : MessageAction

    /** Ťuknutí na čip reakce — detail, kdo reagoval. */
    data object ShowReactions : MessageAction

    /** Ťuknutí na vlastní zprávu: vyžádá si odznak přečtení, druhé ho schová. */
    data object ToggleReadReceipt : MessageAction

    /** Tah doprava: odpověď. Totéž jako položka nabídky [MessageMenuAction.Reply]. */
    data object SwipeReply : MessageAction

    data class JumpTo(val messageId: Long) : MessageAction
    data class OpenLink(val url: String) : MessageAction

    /** Fotka nebo video v prohlížeči. Navigace — vyřizuje ji obrazovka, ne ViewModel. */
    data object OpenMedia : MessageAction

    /** Sada samolepky. Sheet nad obrazovkou — vyřizuje ji obrazovka, ne ViewModel. */
    data class OpenStickerSet(val setId: Long) : MessageAction

    data object OpenDocument : MessageAction
    data object CancelUpload : MessageAction
    data object ToggleVoice : MessageAction
    data class SeekVoice(val fraction: Float) : MessageAction
}

/**
 * Položka nabídky nad zprávou. **Pořadí v seznamu je pořadí v nabídce.**
 *
 * O nabídce rozhodují dvě strany a každá ví něco jiného:
 * - **blok** (builder) ví, co zpráva JE: jen holý text jde upravit, bez textu není co kopírovat,
 *   jen samolepka jde do oblíbených, připnutá zpráva se odepíná. Z toho sestaví kandidáty.
 * - **server** ví, co se SMÍ (`getMessageProperties`). To se načítá až při otevření nabídky,
 *   protože ptát se dopředu by znamenalo dotaz na každou zprávu v historii.
 *
 * Nabídka ukáže kandidáty, pro které [presentation] něco vrátí — akce tak sama řekne, jestli
 * ji server dovolil a jak vypadá. Co server nedovolí, se nenabízí: položka, která skončí chybou,
 * je horší než položka, která tam není.
 */
sealed interface MessageMenuAction : MessageAction {
    /** Jak položka vypadá; `null`, když se v otevřené nabídce [menu] nabízet nemá. */
    fun presentation(menu: MessageMenu): MenuPresentation?

    data object Reply : MessageMenuAction {
        override fun presentation(menu: MessageMenu) =
            MenuPresentation(Res.drawable.reply_24dp, Res.string.menu_reply).takeIf { menu.actions.canReply }
    }

    /** Kandidát jen u zprávy, kterou jde upravit bez ztráty formátování ([isEditableAsPlainText]). */
    data object Edit : MessageMenuAction {
        override fun presentation(menu: MessageMenu) =
            MenuPresentation(Res.drawable.edit_24dp, Res.string.menu_edit).takeIf { menu.actions.canEdit }
    }

    /** Kandidát jen u zprávy s textem. Kanál si obsah může chránit — pak ani do schránky. */
    data object Copy : MessageMenuAction {
        override fun presentation(menu: MessageMenu) =
            MenuPresentation(Res.drawable.content_copy_24dp, Res.string.menu_copy).takeIf { menu.actions.canCopy }
    }

    /** Kandidát jen u samolepky. Jestli už v oblíbených je, se zjistí při otevření nabídky. */
    data object ToggleFavoriteSticker : MessageMenuAction {
        override fun presentation(menu: MessageMenu) = when (menu.favoriteSticker) {
            true -> MenuPresentation(Res.drawable.star_filled_24dp, Res.string.menu_favorite_remove)
            false -> MenuPresentation(Res.drawable.star_24dp, Res.string.menu_favorite_add)
            null -> null
        }
    }

    data object Forward : MessageMenuAction {
        override fun presentation(menu: MessageMenu) =
            MenuPresentation(Res.drawable.forward_24dp, Res.string.menu_forward).takeIf { menu.actions.canForward }
    }

    /**
     * „Informace" se nabízí VŽDY. Nestojí na právu od serveru: čas odeslání a úpravy má konverzace
     * sama, takže sheet má co ukázat i u zprávy, u které server odkaz, zhlédnutí ani čas přečtení
     * nevydá.
     */
    data object Info : MessageMenuAction {
        override fun presentation(menu: MessageMenu) = MenuPresentation(Res.drawable.info_24dp, Res.string.menu_info)
    }

    data object Pin : MessageMenuAction {
        override fun presentation(menu: MessageMenu) =
            MenuPresentation(Res.drawable.push_pin_24dp, Res.string.menu_pin).takeIf { menu.actions.canPin }
    }

    data object Unpin : MessageMenuAction {
        override fun presentation(menu: MessageMenu) =
            MenuPresentation(Res.drawable.keep_off_24dp, Res.string.menu_unpin).takeIf { menu.actions.canPin }
    }

    /**
     * Kandidát jen u souboru. Do prohlížeče médií se soubor, který obrázkem ani videem není,
     * nedostane, takže sdílení a ukládání ven patří sem. Chráněný obsah (`canBeSaved`) ven nesmí.
     */
    data object ShareFile : MessageMenuAction {
        override fun presentation(menu: MessageMenu) =
            MenuPresentation(Res.drawable.share_24dp, Res.string.media_share).takeIf { menu.actions.canSave }
    }

    /**
     * Kandidát jen u souboru a jen tam, kde „Uložit jako…" platforma má ([saveAsSupported]);
     * na iOS ho nahrazuje dialog sdílení. Výběr umístění spouští obrazovka, ne ViewModel.
     */
    data object SaveFileAs : MessageMenuAction {
        override fun presentation(menu: MessageMenu) =
            MenuPresentation(Res.drawable.description_24dp, Res.string.media_save_as).takeIf { menu.actions.canSave }
    }

    /** Pro sebe, nebo pro všechny — rozhodne se až v potvrzovacím dialogu. */
    data object Delete : MessageMenuAction {
        override fun presentation(menu: MessageMenu) =
            MenuPresentation(Res.drawable.delete_24dp, Res.string.menu_delete, destructive = true)
                .takeIf { menu.actions.canDeleteForSelf || menu.actions.canDeleteForAll }
    }
}

/** Ikona a popisek položky nabídky. [destructive] se kreslí barvou chyby. */
data class MenuPresentation(
    val icon: DrawableResource,
    val label: StringResource,
    val destructive: Boolean = false,
)
