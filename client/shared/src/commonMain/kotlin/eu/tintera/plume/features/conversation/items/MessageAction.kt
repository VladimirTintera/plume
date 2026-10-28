package eu.tintera.plume.features.conversation.items

import eu.tintera.plume.resources.menu_saved_gif_remove
import eu.tintera.plume.resources.menu_saved_gif_add
import eu.tintera.plume.resources.gif_box_24dp
import eu.tintera.plume.core.ai.VOICE_SUMMARY_MIN_SECONDS
import eu.tintera.plume.resources.menu_summarize_voice
import eu.tintera.plume.resources.menu_explain
import eu.tintera.plume.features.conversation.ContextFinding
import eu.tintera.plume.resources.event_24dp
import eu.tintera.plume.resources.local_shipping_24dp
import eu.tintera.plume.resources.payments_24dp
import eu.tintera.plume.resources.menu_context_actions
import eu.tintera.plume.resources.menu_context_wifi_sheet
import eu.tintera.plume.resources.message_context_event
import eu.tintera.plume.resources.message_context_flight_calendar
import eu.tintera.plume.resources.message_context_flight_arrival_calendar
import eu.tintera.plume.resources.message_context_flight_track
import eu.tintera.plume.resources.message_context_maps
import eu.tintera.plume.resources.message_context_contact
import eu.tintera.plume.resources.message_context_wifi
import eu.tintera.plume.resources.message_context_code_copy
import eu.tintera.plume.resources.message_context_booking_copy
import eu.tintera.plume.resources.message_context_parcel
import eu.tintera.plume.resources.confirmation_number_24dp
import eu.tintera.plume.resources.menu_context_money
import eu.tintera.plume.resources.menu_context_units
import eu.tintera.plume.resources.straighten_24dp
import eu.tintera.plume.resources.currency_exchange_24dp
import eu.tintera.plume.resources.redeem_24dp
import eu.tintera.plume.resources.wifi_24dp
import eu.tintera.plume.resources.flight_24dp
import eu.tintera.plume.resources.person_add_24dp
import eu.tintera.plume.resources.map_24dp
import eu.tintera.plume.resources.menu_context_payment
import eu.tintera.plume.resources.auto_awesome_24dp
import eu.tintera.plume.core.telegram.ForwardOrigin

import eu.tintera.plume.core.ui.label
import eu.tintera.plume.features.conversation.MessageMenu
import eu.tintera.plume.core.files.saveAsSupported
import eu.tintera.plume.resources.menu_bookmark_remove
import eu.tintera.plume.resources.menu_bookmark_add
import eu.tintera.plume.resources.menu_remind
import eu.tintera.plume.resources.notifications_24dp
import eu.tintera.plume.resources.bookmark_filled_24dp
import eu.tintera.plume.resources.bookmark_24dp
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.menu_block_sender
import eu.tintera.plume.resources.block_24dp
import eu.tintera.plume.resources.menu_profile_audio_remove
import eu.tintera.plume.resources.menu_profile_audio_add
import eu.tintera.plume.resources.music_note_24dp
import eu.tintera.plume.resources.content_copy_24dp
import eu.tintera.plume.resources.delete_24dp
import eu.tintera.plume.resources.description_24dp
import eu.tintera.plume.resources.media_copy_file
import eu.tintera.plume.resources.media_copy_photo
import eu.tintera.plume.resources.media_copy_video
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
import eu.tintera.plume.resources.menu_poll_delete_option
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
import eu.tintera.plume.resources.menu_reply_elsewhere
import eu.tintera.plume.resources.menu_report
import eu.tintera.plume.resources.menu_select_messages
import eu.tintera.plume.resources.menu_select_text
import eu.tintera.plume.resources.menu_unpin
import eu.tintera.plume.resources.push_pin_24dp
import eu.tintera.plume.resources.reply_24dp
import eu.tintera.plume.resources.refresh_24dp
import eu.tintera.plume.resources.menu_resend
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
    data class ShowMenu(
        val items: List<MessageMenuAction>,
        val pressed: Long? = null,
        /** Odpověď ankety pod prstem ([eu.tintera.plume.core.telegram.PollAnswer.id]) — nabídka pak umí smazat ji. */
        val pollOptionId: String? = null,
        /** Úryvek vybraný myší v bublině (desktop) — nabídka ho Kopíruje a Cituje. */
        val selection: String? = null,
    ) : MessageAction

    /** Zavřít nabídku (zpět, ťuknutí mimo). */
    data object CloseMenu : MessageAction

    /** Schválit navrhovaný příspěvek (správce přímých zpráv kanálu); kanál ho zveřejní. */
    data object ApproveSuggestedPost : MessageAction

    /** Odmítnout navrhovaný příspěvek — napřed dialog s nepovinným komentářem pro autora. */
    data object DeclineSuggestedPost : MessageAction

    /** Vrátit zprávě původní obsah, který jí bot nahradil mizejícím jen pro uživatele. */
    data object ShowOriginalContent : MessageAction

    /**
     * Dlouhý stisk na systémovém oznámení. Nabídka nad zprávou se pro něj nehodí (kreslí kopii
     * BUBLINY a nabízí reakce a odpověď), takže oznámení má vlastní malou nabídku jen se
     * smazáním — a ukáže se, jen když ho server dovolí (viz `NoticeMenu`).
     */
    data object ShowNoticeMenu : MessageAction

    /**
     * „Smazat" z nabídky nad oznámením. Práva nese akce sama: nabídka se před akcí zavře
     * (`ExpressiveMenu` volá `onDismiss` dřív než položku), takže ve stavu už nejsou.
     */
    data class DeleteNotice(val canDeleteForSelf: Boolean, val canDeleteForAll: Boolean) : MessageAction

    /** Reakce z pruhu nebo z plného výběru v nabídce; přepíná (přidá / odebere). */
    data class React(val emoji: String) : MessageAction

    /** „Vymazat" u nedávno použitých v plném výběru reakcí (už potvrzené). */
    data object ClearRecentReactions : MessageAction

    /** Ťuknutí na čip reakce — detail, kdo reagoval. */
    data object ShowReactions : MessageAction

    /** Dvojité ťuknutí na zprávu: přepne rychlou reakci účtu (viz `QuickReaction`). */
    data object QuickReact : MessageAction

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

    /** Ťuknutí na úkol v seznamu: odškrtnout ([done]), nebo odškrtnutí zrušit. */
    data class MarkChecklistTask(val taskId: Int, val done: Boolean) : MessageAction

    /** „Přidat úkol" pod seznamem — dialog s polem. */
    data object AddChecklistTask : MessageAction
    data class OpenLink(val url: String) : MessageAction

    /** Rychlé zobrazení stránky z náhledu odkazu (Instant View). */
    data class OpenInstantView(val url: String) : MessageAction

    /** `@jméno` v textu: najít veřejný chat a otevřít ho nad konverzací. */
    data class OpenMention(val username: String) : MessageAction

    /** Zmínka uživatele bez veřejného jména: soukromý chat s ním. */
    data class OpenUser(val userId: Long) : MessageAction

    /** „Zastavit sdílení" pod vlastní živou polohou. */
    data object StopLiveLocation : MessageAction

    /** „Upozornit na blízkost" pod vlastní živou polohou — otevře volbu vzdálenosti. */
    data object SetProximityAlert : MessageAction

    /** „Přidat do kontaktů" na kartě sdíleného kontaktu. */
    data object AddContact : MessageAction

    /** „Nastavit" u návrhu data narození od protějšku (po potvrzení v dialogu). */
    data object AcceptSuggestedBirthdate : MessageAction

    /** „Nastavit" u návrhu profilové fotky od protějšku (po potvrzení v dialogu). */
    data object AcceptSuggestedPhoto : MessageAction

    /** Odpověď na žádost o vypnutí ochrany obsahu: [approve] povolit, jinak odmítnout. */
    data class AnswerProtectionRequest(val approve: Boolean) : MessageAction

    /** Ťuknutí na zprávu o hovoru: zavolat zpátky, stejně (hlas / video) jako tehdy. */
    data object CallBack : MessageAction

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

    /**
     * Příběh podle autora a id — citace odpovědi na příběh, nebo živé vysílání sdílené ve zprávě.
     * Obsah se zjistí až po ťuknutí: příběh mezitím mohl vypršet a vysílání skončit.
     */
    data class OpenStory(val posterChatId: Long, val storyId: Int) : MessageAction
    data object CancelUpload : MessageAction

    /**
     * Stáhnout video zprávy (tlačítko v rohu náhledu) — do seznamu Stahování, jako soubor. Video
     * se jinak přehrává proudem a na disku celé není; stažené jde přehrát bez sítě a vytáhnout
     * z okna ven.
     */
    data object DownloadVideo : MessageAction

    /** Zrušit stahování videa z [DownloadVideo] — totéž tlačítko s průběhem. */
    data object CancelVideoDownload : MessageAction
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

    /** Stáhne jazyk pro systémový rozpoznávač řeči a pak záznam přepíše (rada v bublině přepisu). */
    data object DownloadSpeechLanguage : MessageAction
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
 *
 * **Kam položka v nabídce patří, říká [slot]** — skupina a úroveň (nahoře, nebo pod „Další").
 * Nová akce tam musí dostat místo, jinak se nepřeloží.
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

    /**
     * Poslat znovu zprávu, která neodešla. Kandidát u každé zprávy; ukáže se jen u neodeslané,
     * kterou znovu poslat jde ([MessageMenu.resendable]). Totéž posílá i ťuknutí na „Neodesláno"
     * pod bublinou — tam i u zprávy, kterou poslat nejde, a ViewModel pak řekne proč.
     */
    data object Resend : MessageMenuAction {
        override fun presentation(menu: MessageMenu) =
            MenuPresentation(Res.drawable.refresh_24dp, Res.string.menu_resend).takeIf { menu.resendable }
    }

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

    /**
     * Upravit vlastní seznam úkolů — formulářem, ne polem (`editMessageChecklist`). Kandidát
     * u každého seznamu; smí ten, komu server zprávu dovolí upravit (`canBeEdited` u seznamu
     * znamená právě tohle).
     */
    data object EditChecklist : MessageMenuAction {
        override fun presentation(menu: MessageMenu) =
            MenuPresentation(Res.drawable.edit_24dp, Res.string.menu_edit).takeIf { menu.actions.canEdit }
    }

    /**
     * Smazat odpověď ankety, na které uživatel podržel. Kandidát u ankety, kde nějakou odpověď
     * smazat jde; ukáže se jen tehdy, když stisk byl NA té odpovědi ([MessageMenu.pressedPollOptionId]).
     * Nevratné, takže s potvrzením a barvou chyby.
     */
    data object DeletePollOption : MessageMenuAction {
        override fun presentation(menu: MessageMenu) =
            MenuPresentation(Res.drawable.delete_24dp, Res.string.menu_poll_delete_option, destructive = true)
                .takeIf { menu.pressedPollOptionId != null }
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

    /**
     * Vysvětlení dlouhé cizí zprávy modelem v zařízení („o co jde"). Kandidát jen u dlouhé
     * příchozí zprávy ([menuActions]); nabízí se se zapnutou AI v zařízení
     * ([MessageMenu.explainAvailable]). Obsah nikam neodchází, takže na rozdíl od překladu
     * nevadí ani chráněný obsah. Obsluhuje ho obrazovka (sheet shrnutí), ne ViewModel.
     */
    data object Explain : MessageMenuAction {
        override fun presentation(menu: MessageMenu) =
            MenuPresentation(Res.drawable.auto_awesome_24dp, Res.string.menu_explain).takeIf { menu.explainAvailable }
    }

    /**
     * Shrnutí PŘEPISU dlouhé hlasovky nebo kolečka modelem v zařízení. Kandidát u příchozího
     * záznamu od [VOICE_SUMMARY_MIN_SECONDS] ([menuActions]); nabízí se, jen když je přepis
     * hotový a AI v zařízení zapnutá ([MessageMenu.voiceSummaryAvailable]) — kvůli shrnutí se
     * nepřepisuje. Obsluhuje ho obrazovka (sheet shrnutí), ne ViewModel.
     */
    data object SummarizeVoice : MessageMenuAction {
        override fun presentation(menu: MessageMenu) =
            MenuPresentation(Res.drawable.auto_awesome_24dp, Res.string.menu_summarize_voice).takeIf { menu.voiceSummaryAvailable }
    }

    /**
     * Kandidát na akce z textu zprávy (termín, účet, zásilka…). ViewModel ho při otevření nabídky
     * ROZBALÍ na položky jednotlivých akcí ([ContextItem], [contextMenuItems]); sám se ukáže jen
     * jako „Akce ze zprávy…" za posledními z nich, když se všechny nevejdou. Nabízí se i s ikonkou
     * vypnutou v nastavení — nabídka se otevírá vědomě. Chráněný obsah ven nesmí ani do
     * kalendáře, takže stejné právo jako [Copy].
     */
    data object ContextActions : MessageMenuAction {
        override fun presentation(menu: MessageMenu): MenuPresentation? =
            MenuPresentation(Res.drawable.auto_awesome_24dp, Res.string.menu_context_actions)
                .takeIf { menu.contextFindings.isNotEmpty() && menu.actions.canCopy }
    }

    /**
     * Jedna akce z textu zprávy: [verb] co udělat, [index] s čím (`MessageMenu.contextFindings`),
     * [value] ukázaná hodnota, když je akcí stejného druhu víc (místo popisku, druh řekne ikona).
     * Co nic neukazuje, se provede rovnou (kalendář, mapy, kopírování); převody a QR platba
     * otevřou sheet, proto mají tři tečky.
     */
    data class ContextItem(val verb: ContextVerb, val index: Int, val value: String? = null) : MessageMenuAction {
        override fun presentation(menu: MessageMenu): MenuPresentation? {
            if (!menu.actions.canCopy || menu.contextFindings.getOrNull(index) == null) return null
            return when (verb) {
                ContextVerb.Calendar -> MenuPresentation(Res.drawable.event_24dp, Res.string.message_context_event, labelArg = value)
                ContextVerb.FlightCalendar -> MenuPresentation(Res.drawable.event_24dp, Res.string.message_context_flight_calendar, labelArg = value)
                ContextVerb.ArrivalCalendar -> MenuPresentation(Res.drawable.event_24dp, Res.string.message_context_flight_arrival_calendar, labelArg = value)
                ContextVerb.FlightStatus -> MenuPresentation(Res.drawable.flight_24dp, Res.string.message_context_flight_track, labelArg = value)
                ContextVerb.Maps -> MenuPresentation(Res.drawable.map_24dp, Res.string.message_context_maps, labelArg = value)
                ContextVerb.SaveContact -> MenuPresentation(Res.drawable.person_add_24dp, Res.string.message_context_contact, labelArg = value)
                ContextVerb.JoinWifi -> MenuPresentation(Res.drawable.wifi_24dp, Res.string.message_context_wifi, labelArg = value)
                ContextVerb.CopyCode -> MenuPresentation(Res.drawable.redeem_24dp, Res.string.message_context_code_copy, labelArg = value)
                ContextVerb.CopyBooking -> MenuPresentation(Res.drawable.confirmation_number_24dp, Res.string.message_context_booking_copy, labelArg = value)
                ContextVerb.TrackParcel -> MenuPresentation(Res.drawable.local_shipping_24dp, Res.string.message_context_parcel, labelArg = value)
                ContextVerb.Payment -> MenuPresentation(Res.drawable.payments_24dp, Res.string.menu_context_payment, labelArg = value)
                ContextVerb.Money -> MenuPresentation(Res.drawable.currency_exchange_24dp, Res.string.menu_context_money, labelArg = value)
                ContextVerb.Units -> MenuPresentation(Res.drawable.straighten_24dp, Res.string.menu_context_units, labelArg = value)
                ContextVerb.Wifi -> MenuPresentation(Res.drawable.wifi_24dp, Res.string.menu_context_wifi_sheet, labelArg = value)
            }
        }
    }

    /** Kandidát jen u samolepky. Jestli už v oblíbených je, se zjistí při otevření nabídky. */
    data object ToggleFavoriteSticker : MessageMenuAction {
        override fun presentation(menu: MessageMenu) = when (menu.favoriteSticker) {
            true -> MenuPresentation(Res.drawable.star_filled_24dp, Res.string.menu_favorite_remove)
            false -> MenuPresentation(Res.drawable.star_24dp, Res.string.menu_favorite_add)
            null -> null
        }
    }

    /**
     * GIF ze zprávy mezi uložené GIFy (`addSavedAnimation`), nebo z nich ven. Jinak se GIF od
     * někoho jiného uložit nedal — uložené plnilo jen vlastní odeslání. Kandidát jen u GIFu;
     * jestli už uložený je, se zjistí při otevření nabídky ([MessageMenu.savedGif]).
     */
    data object ToggleSavedGif : MessageMenuAction {
        override fun presentation(menu: MessageMenu) = when (menu.savedGif) {
            true -> MenuPresentation(Res.drawable.gif_box_24dp, Res.string.menu_saved_gif_remove)
            false -> MenuPresentation(Res.drawable.gif_box_24dp, Res.string.menu_saved_gif_add)
            null -> null
        }
    }

    /**
     * Skladba do hudby ve vlastním profilu, nebo z ní ven (`addProfileAudio` / `removeProfileAudio`).
     * Kandidát jen u skladby; jestli už v profilu je, se zjistí při otevření nabídky
     * ([MessageMenu.profileAudio]).
     */
    data object ToggleProfileAudio : MessageMenuAction {
        override fun presentation(menu: MessageMenu) = when (menu.profileAudio) {
            true -> MenuPresentation(Res.drawable.music_note_24dp, Res.string.menu_profile_audio_remove)
            false -> MenuPresentation(Res.drawable.music_note_24dp, Res.string.menu_profile_audio_add)
            null -> null
        }
    }

    /**
     * Záložka jen pro uživatele (`MessageBookmarks`), v zařízení. Nabízí se u každé zprávy;
     * jestli už v záložkách je, se zjistí při otevření nabídky ([MessageMenu.bookmarked]).
     */
    data object ToggleBookmark : MessageMenuAction {
        override fun presentation(menu: MessageMenu) = when (menu.bookmarked) {
            true -> MenuPresentation(Res.drawable.bookmark_filled_24dp, Res.string.menu_bookmark_remove)
            false -> MenuPresentation(Res.drawable.bookmark_24dp, Res.string.menu_bookmark_add)
            null -> null
        }
    }

    /**
     * Připomenout zprávu: ve zvolený čas ji naplánované přeposlání doručí do Uložených zpráv,
     * kde Telegram na doručení upozorní. Přeposlání proto musí server dovolit.
     */
    data object Remind : MessageMenuAction {
        override fun presentation(menu: MessageMenu) =
            MenuPresentation(Res.drawable.notifications_24dp, Res.string.menu_remind).takeIf { menu.actions.canForward }
    }

    /**
     * Odpovědět v jiném chatu (`InputMessageReplyToExternalMessage`): výběr chatu, kde se
     * odpověď napíše, citace pak vede sem. Jen kde to server dovolí (`canBeRepliedInAnotherChat`,
     * chráněný obsah ne). Pod „Další" — nahoře je obyčejná odpověď, tohle se hledá výjimečně.
     */
    data object ReplyElsewhere : MessageMenuAction {
        override fun presentation(menu: MessageMenu) =
            MenuPresentation(Res.drawable.reply_24dp, Res.string.menu_reply_elsewhere).takeIf { menu.actions.canReplyElsewhere }
    }

    data object Forward : MessageMenuAction {
        override fun presentation(menu: MessageMenu) =
            MenuPresentation(Res.drawable.forward_24dp, Res.string.menu_forward).takeIf { menu.actions.canForward }
    }

    /**
     * „Informace" se nabízí VŽDY. Nestojí na právu od serveru: čas odeslání a úpravy má konverzace
     * sama, takže sheet má co ukázat i u zprávy, u které server odkaz, zhlédnutí ani čas přečtení
     * nevydá. **Není to řádek nabídky, ale její hlavička** s časem a stavem doručení
     * ([MenuSlot.Header]) — podrobnosti o doručení se hledají právě tam.
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
     * Médium zprávy do schránky (`MediaSharer.copy`). Kandidát u fotky všude, u videa a souboru
     * jen tam, kde schránka nese soubory (`copiesAnyFile`, desktop). [kind] dává jen popisek —
     * „Kopírovat" bez předmětu by se u fotky s popiskem pletlo s kopírováním textu.
     */
    data class CopyMedia(val kind: Kind) : MessageMenuAction {
        enum class Kind(val label: StringResource) {
            Photo(Res.string.media_copy_photo),
            Video(Res.string.media_copy_video),
            File(Res.string.media_copy_file),
        }

        override fun presentation(menu: MessageMenu) =
            MenuPresentation(Res.drawable.content_copy_24dp, kind.label).takeIf { menu.actions.canSave }
    }

    /**
     * Kandidát u souboru tam, kde „Uložit jako…" platforma má ([saveAsSupported]); na iOS ho
     * nahrazuje dialog sdílení. U fotky a videa jen tam, kde sdílení není (desktop) — na
     * telefonu je cesta ven z prohlížeče a nabídka zůstává krátká. Výběr umístění spouští
     * obrazovka, ne ViewModel.
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
            MenuPresentation(Res.drawable.select_check_box_24dp, Res.string.menu_select_messages)
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

    /**
     * Zablokovat odesílatele zprávy v chatu Odpovědi (`blockMessageSenderFromReplies`). Kandidát
     * u každé zprávy; ukáže se jen tam ([MessageMenu.blockFromReplies]). Volby (smazat, spam)
     * jsou v potvrzovacím dialogu.
     */
    data object BlockFromReplies : MessageMenuAction {
        override fun presentation(menu: MessageMenu) =
            MenuPresentation(Res.drawable.block_24dp, Res.string.menu_block_sender).takeIf { menu.blockFromReplies }
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
    /**
     * Hodnota místo popisku („RR123456785CZ" s ikonou zásilky), když je akcí stejného druhu víc
     * a popisek by je nerozlišil. Místo, ne za: položka musí zůstat na jednom řádku.
     */
    val labelArg: String? = null,
)

/** Co položka akce z textu zprávy udělá (viz [MessageMenuAction.ContextItem]). */
enum class ContextVerb {
    Calendar, FlightCalendar, ArrivalCalendar, FlightStatus, Maps, SaveContact, JoinWifi, CopyCode,
    CopyBooking, TrackParcel, Payment, Money, Units, Wifi,
}

/**
 * Položky nabídky nad zprávou pro akce z jejího textu, v pořadí sheetu. Nejvýš [MAX_CONTEXT_ITEMS]
 * řádky; když jich je víc, zbytek schová „Akce ze zprávy…" ([MessageMenuAction.ContextActions]).
 * Jsou na první úrovni nabídky ve vlastní skupině, takže víc řádků by ji natáhlo pod zprávou. Dvě akce stejného druhu nesou hodnotu,
 * aby se daly rozlišit. [canJoinWifi] = telefon umí síť přidat sám (na iOS ne, tam sheet s heslem).
 */
internal fun contextMenuItems(findings: List<ContextFinding>, canJoinWifi: Boolean): List<MessageMenuAction> {
    val entries = findings.flatMapIndexed { index, finding ->
        when (finding) {
            is ContextFinding.Event -> listOf(ContextVerb.Calendar to finding.title)
            is ContextFinding.Payment -> listOf(ContextVerb.Payment to finding.account)
            is ContextFinding.Address -> listOf(ContextVerb.Maps to finding.text)
            is ContextFinding.Contact -> listOf(ContextVerb.SaveContact to (finding.name ?: finding.phone ?: finding.email.orEmpty()))
            is ContextFinding.Flight -> listOfNotNull(
                ContextVerb.FlightStatus to "${finding.airline} ${finding.number}",
                finding.at?.let { (if (finding.arrival) ContextVerb.ArrivalCalendar else ContextVerb.FlightCalendar) to "${finding.airline} ${finding.number}" },
            )
            is ContextFinding.Wifi -> listOf((if (canJoinWifi) ContextVerb.JoinWifi else ContextVerb.Wifi) to finding.ssid)
            is ContextFinding.Code -> listOf(ContextVerb.CopyCode to finding.value)
            is ContextFinding.Booking -> listOf(ContextVerb.CopyBooking to finding.value)
            is ContextFinding.Money -> listOf(ContextVerb.Money to finding.shown)
            is ContextFinding.Measure -> listOf(ContextVerb.Units to finding.shown)
            is ContextFinding.Parcel -> listOf(ContextVerb.TrackParcel to finding.number)
        }.map { (verb, value) -> Triple(verb, index, value) }
    }
    val repeated = entries.groupingBy { it.first }.eachCount().filterValues { it > 1 }.keys
    val items = entries.map { (verb, index, value) ->
        MessageMenuAction.ContextItem(verb, index, value.takeIf { verb in repeated })
    }
    return if (items.size <= MAX_CONTEXT_ITEMS) {
        items
    } else {
        items.take(MAX_CONTEXT_ITEMS - 1) + MessageMenuAction.ContextActions
    }
}

private const val MAX_CONTEXT_ITEMS = 3

/**
 * Skupina položky v nabídce nad zprávou. **Pořadí hodnot je pořadí skupin**; uvnitř skupiny platí
 * pořadí kandidátů z bloku (`menuActions`).
 *
 * Nahoře je, kvůli čemu se nabídka u zprávy otevírá: odpověď, kopírování, přeposlání a akce,
 * které má jen tenhle druh zprávy (úprava vlastní zprávy, soubor ven, samolepka do oblíbených,
 * akce ankety a nálezy v textu). Zbytek je pod „Další" ([inMore]) — dřív byl všechno jeden
 * seznam a u běžné cizí zprávy měl i sedmnáct řádků, víc, než se vešlo pod zprávu.
 * Rozvržení obou úrovní: `arrangeMenu`.
 */
enum class MenuSlot(val inMore: Boolean = false) {
    /** Hlavička s časem a stavem doručení — ťuknutím otevře Informace. Není to řádek. */
    Header,
    Primary,
    Poll,
    /** Akce z textu zprávy (termín, zásilka…), nejvýš tři řádky. */
    Context,
    /** Práce s textem: vybrat, shrnout, vysvětlit. */
    Text(inMore = true),
    /** Mimo tenhle chat: odpovědět v jiném. */
    Elsewhere(inMore = true),
    /** Na později: záložka, připomenutí, připnutí. */
    Later(inMore = true),
    Selection(inMore = true),
    /** Nahlášení zvlášť — není nevratné, ale nemá se plést s ostatním. */
    Report(inMore = true),
    /** Nevratné akce mají skupinu vlastní a zůstávají nahoře, aby se nehledaly. */
    Destructive,
}

/** Kam položka v nabídce patří (viz [MenuSlot]). */
val MessageMenuAction.slot: MenuSlot
    get() = when (this) {
        MessageMenuAction.Info -> MenuSlot.Header
        MessageMenuAction.Resend,
        MessageMenuAction.Edit,
        MessageMenuAction.EditChecklist,
        MessageMenuAction.Reply,
        MessageMenuAction.Copy,
        MessageMenuAction.Forward,
        MessageMenuAction.Translate,
        MessageMenuAction.ToggleFavoriteSticker,
        MessageMenuAction.ToggleSavedGif,
        MessageMenuAction.ToggleProfileAudio,
        MessageMenuAction.ShareFile,
        is MessageMenuAction.CopyMedia,
        MessageMenuAction.SaveFileAs,
        -> MenuSlot.Primary
        MessageMenuAction.RetractVote, MessageMenuAction.StopPoll -> MenuSlot.Poll
        MessageMenuAction.ContextActions, is MessageMenuAction.ContextItem -> MenuSlot.Context
        MessageMenuAction.SelectText, MessageMenuAction.Summarize, MessageMenuAction.Explain, MessageMenuAction.SummarizeVoice ->
            MenuSlot.Text
        MessageMenuAction.ReplyElsewhere -> MenuSlot.Elsewhere
        MessageMenuAction.ToggleBookmark, MessageMenuAction.Remind, MessageMenuAction.Pin, MessageMenuAction.Unpin -> MenuSlot.Later
        MessageMenuAction.Select -> MenuSlot.Selection
        MessageMenuAction.Report, MessageMenuAction.BlockFromReplies -> MenuSlot.Report
        MessageMenuAction.DeletePollOption, MessageMenuAction.Delete -> MenuSlot.Destructive
    }
