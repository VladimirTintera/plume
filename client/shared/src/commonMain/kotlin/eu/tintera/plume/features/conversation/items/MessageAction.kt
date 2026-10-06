package eu.tintera.plume.features.conversation.items

import eu.tintera.plume.core.telegram.ForwardOrigin

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
import eu.tintera.plume.resources.flag_24dp
import eu.tintera.plume.resources.format_quote_24dp
import eu.tintera.plume.resources.forward_24dp
import eu.tintera.plume.resources.info_24dp
import eu.tintera.plume.resources.keep_off_24dp
import eu.tintera.plume.resources.menu_copy
import eu.tintera.plume.resources.undo_24dp
import eu.tintera.plume.resources.stop_circle_24dp
import eu.tintera.plume.resources.menu_poll_stop
import eu.tintera.plume.resources.menu_poll_retract
import eu.tintera.plume.resources.menu_delete
import eu.tintera.plume.resources.menu_edit
import eu.tintera.plume.resources.menu_favorite_add
import eu.tintera.plume.resources.menu_favorite_remove
import eu.tintera.plume.resources.menu_forward
import eu.tintera.plume.resources.menu_info
import eu.tintera.plume.resources.menu_pin
import eu.tintera.plume.resources.menu_quote
import eu.tintera.plume.resources.menu_reply
import eu.tintera.plume.resources.menu_report
import eu.tintera.plume.resources.menu_select
import eu.tintera.plume.resources.menu_select_text
import eu.tintera.plume.resources.menu_unpin
import eu.tintera.plume.resources.push_pin_24dp
import eu.tintera.plume.resources.reply_24dp
import eu.tintera.plume.resources.select_check_box_24dp
import eu.tintera.plume.resources.star_24dp
import eu.tintera.plume.resources.text_select_24dp
import eu.tintera.plume.resources.star_filled_24dp
import eu.tintera.plume.resources.translate_24dp
import eu.tintera.plume.resources.menu_translate
import eu.tintera.plume.resources.menu_summarize
import eu.tintera.plume.resources.summarize_24dp
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
    /**
     * Dlouhý stisk: otevře nabídku s [items] — kandidáty, které nabízí blok (viz [MessageMenuAction]).
     * [pressed] je zpráva pod prstem, když se liší od bloku: fotka uprostřed alba. Odpověď míří
     * na ni (viz [MessageMenu.replyTarget]), zbytek nabídky na celý blok.
     */
    data class ShowMenu(val items: List<MessageMenuAction>, val pressed: Long? = null) : MessageAction

    /** Zavřít nabídku (zpět, ťuknutí mimo). */
    data object CloseMenu : MessageAction

    /** Reakce z pruhu nebo z plného výběru v nabídce; přepíná (přidá / odebere). */
    data class React(val emoji: String) : MessageAction

    /** Ťuknutí na čip reakce — detail, kdo reagoval. */
    data object ShowReactions : MessageAction

    /** Ťuknutí na vlastní zprávu: vyžádá si odznak přečtení, druhé ho schová. */
    data object ToggleReadReceipt : MessageAction

    /** Ťuknutí (i dlouhý stisk) v režimu výběru: přidá blok do výběru, nebo ho z něj vyndá. */
    data object ToggleSelection : MessageAction

    /** Tah doprava: odpověď. Totéž jako položka nabídky [MessageMenuAction.Reply]. */
    data object SwipeReply : MessageAction

    /**
     * Odpověď jen na úryvek zprávy — „Citovat" v nabídce, když je v kopii zprávy označený
     * [text]. Vzniká z [MessageMenuAction.Reply] přes [MessageMenuAction.withSelection].
     */
    data class QuoteReply(val text: String) : MessageAction

    /** Zkopírovat jen označený úryvek; vzniká z [MessageMenuAction.Copy]. */
    data class CopyText(val text: String) : MessageAction

    data class JumpTo(val messageId: Long) : MessageAction

    /**
     * Tlačítko bota pod zprávou, určené POZICÍ — co udělá, se dohledá ve zprávě. Data zpětného
     * volání v doméně nejsou (viz `ConversationRepository.pressBotButton`).
     */
    data class PressBotButton(val row: Int, val column: Int) : MessageAction

    /**
     * Hlas v anketě. [answerIndexes] jsou indexy odpovědí v PŮVODNÍM pořadí
     * ([eu.tintera.plume.core.telegram.PollAnswer.index]), ne v tom, v jakém se ukazují.
     */
    data class Vote(val answerIndexes: List<Int>) : MessageAction

    /** Kdo v anketě jak hlasoval — sheet. */
    data object ShowPollVoters : MessageAction

    /** „Přidat odpověď" pod anketou — dialog s polem. */
    data object AddPollOption : MessageAction
    data class OpenLink(val url: String) : MessageAction

    /** `@jméno` v textu: najít veřejný chat a otevřít ho nad konverzací. */
    data class OpenMention(val username: String) : MessageAction

    /** Zmínka uživatele bez veřejného jména: soukromý chat s ním. */
    data class OpenUser(val userId: Long) : MessageAction

    /** Kanál ze soutěže: otevřít ho nad konverzací. */
    data class OpenChat(val chatId: Long) : MessageAction

    /** Soutěž: sheet se stavem účasti tohoto účtu (`getGiveawayInfo`). */
    data object ShowGiveaway : MessageAction

    /**
     * „Přeposláno od …": zdroj přeposlání. Příspěvek kanálu skočí na původní zprávu, uživatel
     * otevře soukromý chat s ním; původ z téhož chatu skočí na místě, jako citace.
     */
    data class OpenForwardOrigin(val origin: ForwardOrigin) : MessageAction

    /** Komentáře pod příspěvkem kanálu: otevře vlákno v diskusní skupině. */
    data object OpenComments : MessageAction

    /**
     * Ťuknutí na tvář odesílatele ve skupině: jeho profil. Ze zprávy se najde soukromý chat
     * (profil stojí na chatu, ne na uživateli), a tvář do profilu přiletí — viz
     * [ConversationItemScope.isProfileFlight].
     */
    data class OpenSenderProfile(val userId: Long) : MessageAction

    /**
     * `/příkaz` v textu: pošle se do chatu, jako u oficiálních klientů. Rozepsaný koncept se
     * nemaže — ťuknutí na příkaz není odeslání toho, co je v poli.
     */
    data class SendBotCommand(val command: String) : MessageAction

    /**
     * „přes @bot" u zprávy: bot se dá do pole (`@gif `), aby šel poslat další výsledek. [username]
     * je bez zavináče.
     */
    data class UseInlineBot(val username: String) : MessageAction

    /** `#tag` v textu: hledání zpráv s tagem. Navigace — vyřizuje ji obrazovka, ne ViewModel. */
    data class SearchHashtag(val tag: String) : MessageAction

    /** Fotka nebo video v prohlížeči. Navigace — vyřizuje ji obrazovka, ne ViewModel. */
    data object OpenMedia : MessageAction

    /**
     * Fotka nebo video z ankety v prohlížeči: [index] je `POLL_QUESTION_MEDIA` (otázka), nebo
     * PŮVODNÍ index odpovědi. Navigace — vyřizuje ji obrazovka.
     */
    data class OpenPollMedia(val index: Int) : MessageAction

    /** Médium článku v prohlížeči; [index] je `ArticleMedia.index`. Navigace — vyřizuje ji obrazovka. */
    data class OpenArticleMedia(val index: Int) : MessageAction

    /** Dočíst useknutý článek ([eu.tintera.plume.core.telegram.Article.isPartial]). */
    data object ReadFullArticle : MessageAction

    /** Zvuk nebo hlasovka v článku: pustit / pozastavit; [index] je `ArticleMedia.index`. */
    data class ToggleArticleAudio(val index: Int) : MessageAction

    /** Posun zvuku v článku. */
    data class SeekArticleAudio(val index: Int, val fraction: Float) : MessageAction

    /** Soubor v článku: stáhnout a otevřít (druhé ťuknutí stahování zruší). */
    data class OpenArticleDocument(val index: Int) : MessageAction

    /** Sada samolepky. Sheet nad obrazovkou — vyřizuje ji obrazovka, ne ViewModel. */
    data class OpenStickerSet(val setId: Long) : MessageAction

    data object OpenDocument : MessageAction

    /** Ťuknutí na nezaplacené placené médium: vysvětlit, že odemknout ho Plume neumí. */
    data object ExplainPaidMedia : MessageAction

    /** Ťuknutí na živé vysílání v příběhu: sledovat ho Plume neumí, tak aspoň proč. */
    data object ExplainLiveStory : MessageAction

    /**
     * Příběh podle autora a id — citace odpovědi na příběh. Na rozdíl od sdíleného příběhu ve
     * zprávě se obsah zjistí až po ťuknutí: příběh mezitím mohl vypršet.
     */
    data class OpenStory(val posterChatId: Long, val storyId: Int) : MessageAction
    data object CancelUpload : MessageAction
    data object ToggleVoice : MessageAction

    /** Ťuknutí na video kolečko: pustí ho se zvukem od začátku, podruhé zvuk vypne. */
    data object ToggleVideoNoteSound : MessageAction

    /**
     * Ťuknutí na čas v textu („1:23"): pustit médium od [seconds] — video v prohlížeči,
     * hlasovku, skladbu nebo kolečko přímo v chatu (viz [MessageEntityType.MediaTimestamp]).
     */
    data class PlayMediaAt(val seconds: Int) : MessageAction

    /** Kolečko se zvukem dohrálo nebo odjelo z obrazovky. */
    data object VideoNoteSoundEnded : MessageAction
    data class SeekVoice(val fraction: Float) : MessageAction

    /** Přepne rychlost přehrávání na další v pořadí. Platí pro celou aplikaci, ne pro tuhle zprávu. */
    data object CycleVoiceSpeed : MessageAction

    /** Požádá server o přepis hlasovky nebo kolečka do textu. */
    data object Transcribe : MessageAction
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

    /**
     * Jak položka vypadá, když je v kopii zprávy v nabídce označený text ([selection], `null` =
     * nic). Většině položek je výběr jedno.
     *
     * **Položka se mění, nepřibývá.** Výška nabídky se počítá z ukázaných položek, takže
     * položka navíc by nabídku pod prstem natáhla — „Odpovědět" se proto s výběrem přejmenuje
     * na „Citovat" (je to táž akce, jen s úryvkem).
     */
    fun presentation(menu: MessageMenu, selection: String?): MenuPresentation? = presentation(menu)

    /** Co ťuknutí odešle, když je označený text; bez výběru položka sama. */
    fun withSelection(selection: String?): MessageAction = this

    data object Reply : MessageMenuAction {
        override fun presentation(menu: MessageMenu) =
            MenuPresentation(Res.drawable.reply_24dp, Res.string.menu_reply).takeIf { menu.actions.canReply }

        override fun presentation(menu: MessageMenu, selection: String?) =
            if (selection == null) {
                presentation(menu)
            } else {
                MenuPresentation(Res.drawable.format_quote_24dp, Res.string.menu_quote).takeIf { menu.actions.canReply }
            }

        override fun withSelection(selection: String?) = selection?.let(MessageAction::QuoteReply) ?: this
    }

    /**
     * Vzít hlas v anketě zpátky. Kandidát jen tam, kde to anketa dovoluje (`ChatPoll.canRetract`)
     * — rozhoduje o tom anketa sama, ne příznaky zprávy od serveru.
     */
    data object RetractVote : MessageMenuAction {
        override fun presentation(menu: MessageMenu) = MenuPresentation(Res.drawable.undo_24dp, Res.string.menu_poll_retract)
    }

    /**
     * Ukončit anketu. Kandidát u otevřené ankety; smí to ten, komu server dovolí zprávu
     * upravit — u ankety `canBeEdited` znamená právě „jde ukončit" (dokumentace `stopPoll`).
     * Nevratné, takže s potvrzením.
     */
    data object StopPoll : MessageMenuAction {
        override fun presentation(menu: MessageMenu) =
            MenuPresentation(Res.drawable.stop_circle_24dp, Res.string.menu_poll_stop).takeIf { menu.actions.canEdit }
    }

    /** Kandidát jen tam, kde vstupní pole text nebo popisek upraví bez ztráty formátování. */
    data object Edit : MessageMenuAction {
        override fun presentation(menu: MessageMenu) =
            MenuPresentation(Res.drawable.edit_24dp, Res.string.menu_edit).takeIf { menu.actions.canEdit }
    }

    /** Kandidát jen u zprávy s textem. Kanál si obsah může chránit — pak ani do schránky. */
    data object Copy : MessageMenuAction {
        override fun presentation(menu: MessageMenu) =
            MenuPresentation(Res.drawable.content_copy_24dp, Res.string.menu_copy).takeIf { menu.actions.canCopy }

        // S označeným textem kopíruje jen ten — jako „Kopírovat" v systémové liště výběru.
        override fun withSelection(selection: String?) = selection?.let(MessageAction::CopyText) ?: this
    }

    /**
     * Vybrat úryvek textu. Kandidát za stejných podmínek jako [Copy]: bez textu není co vybírat
     * a chráněný obsah (`canBeSaved`) ven nesmí ani po kouskách.
     *
     * **Je to samostatná položka, ne výběr přímo v bublině.** Dlouhý stisk už patří nabídce nad
     * zprávou a `SelectionContainer` by si ho vzal pro sebe — z bubliny by nešlo otevřít nabídku.
     * Sheet je stejná cesta jako „Informace": jednorázová akce, která nabídku ukončí.
     */
    data object SelectText : MessageMenuAction {
        override fun presentation(menu: MessageMenu) =
            MenuPresentation(Res.drawable.text_select_24dp, Res.string.menu_select_text).takeIf { menu.actions.canCopy }
    }

    /**
     * Přeložit text zprávy do jazyka aplikace. Kandidát za stejných podmínek jako [Copy]: bez
     * textu není co překládat a chráněný obsah ven nesmí ani v jiném jazyce. Otevře sheet — je to
     * jednorázová akce jako „Informace", ne podnabídka. Zpráva, která už JE v jazyce překladu
     * ([MessageMenu.inTranslationLanguage]), ji nemá — překlad by vrátil totéž.
     */
    data object Translate : MessageMenuAction {
        override fun presentation(menu: MessageMenu) =
            MenuPresentation(Res.drawable.translate_24dp, Res.string.menu_translate)
                .takeIf { menu.actions.canCopy && !menu.inTranslationLanguage }
    }

    /**
     * Shrnutí dlouhé zprávy od serveru. Kandidát jen u zprávy, které ho server nabízí
     * (`summaryLanguage`); stojí na témže právu jako překlad — chráněný obsah ven nesmí ani
     * zkrácený.
     */
    data object Summarize : MessageMenuAction {
        override fun presentation(menu: MessageMenu) =
            MenuPresentation(Res.drawable.summarize_24dp, Res.string.menu_summarize)
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

    /**
     * Vstup do režimu výběru víc zpráv. Nabízí se VŽDY, jako [Info]: vybrat jde každá zpráva
     * a co se s výběrem dá dělat, ukáže až lišta výběru podle práv všech vybraných.
     */
    data object Select : MessageMenuAction {
        override fun presentation(menu: MessageMenu) =
            MenuPresentation(Res.drawable.select_check_box_24dp, Res.string.menu_select)
    }

    /**
     * Nahlásit zprávu moderátorům. Nabízí se VŽDY jako kandidát a rozhodne server
     * (`canReportChat`) — vlastní zprávy ani zprávy v Uložených zprávách nahlásit nejde.
     * Otevře průvodce s volbou důvodu, takže je to jednorázová akce jako „Informace".
     */
    data object Report : MessageMenuAction {
        override fun presentation(menu: MessageMenu) =
            MenuPresentation(Res.drawable.flag_24dp, Res.string.menu_report).takeIf { menu.actions.canReport }
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
