package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.AppScreen
import eu.tintera.plume.core.telegram.InternalLink
import eu.tintera.plume.core.telegram.PollComposerMode

/**
 * Kam konverzace odchází — ViewModel řekne „otevři chat", navigace je věc shellu
 * ([ConversationNavigation]). Jednorázové UDÁLOSTI, ne stav.
 *
 * Dřív to bylo třináct polí `ConversationState` (`openChat`, `openScreen`, `openStory`…), každé
 * s vlastní funkcí `consume…`, kterou obrazovka po vyzvednutí pole vynulovala. Stav ale
 * vyjadřuje, co JE, ne co se má jednou stát: zapomenuté vynulování by po otočení obrazovky
 * otevřelo chat znovu, a každá kopie stavu nesla třináct polí, která byla skoro vždycky `null`.
 *
 * - **Kanál, ne `SharedFlow`** (`ConversationViewModel.navigation`): událost, která přijde, když
 *   obrazovka zrovna neodebírá (přechod, otočení), počká ve frontě a doručí se jednou.
 * - **Části ViewModelu je posílají přes `navigate`**, povinný parametr konstruktoru — výchozí
 *   „nic" by v produkci navigaci tiše zahodilo. Testy si místo něj dají seznam.
 */
sealed interface ConversationNavEvent {

    /** Přeposlat zprávy — obrazovka otevře výběr cílového chatu. */
    data class Forward(val messageIds: List<Long>) : ConversationNavEvent

    /** Odpovědět na zprávu v jiném chatu — výběr chatu, kde se odpověď napíše. */
    data class ReplyElsewhere(val messageId: Long) : ConversationNavEvent

    /** Video v prohlížeči od daného místa (čas v médiu). */
    data class OpenMediaAt(val target: MediaAt) : ConversationNavEvent

    /** Chat ze zmínky nebo odkazu, případně se skokem na zprávu. */
    data class OpenChat(val target: ChatTarget) : ConversationNavEvent

    /** Obrazovka aplikace z odkazu (`tg://settings/…`, `tg://newgroup`). */
    data class OpenScreen(val screen: AppScreen) : ConversationNavEvent

    /** Příběh v prohlížeči (citace odpovědi, odkaz na příběh). */
    data class OpenStory(val target: StoryTarget) : ConversationNavEvent

    /** Odkaz `t.me/bot?startgroup=…` — výběr skupiny. */
    data class AddBotToGroup(val link: InternalLink.BotStartInGroup) : ConversationNavEvent

    /** Tlačítko bota s inline dotazem do jiného chatu — výběr chatu. */
    data class SwitchInline(val pick: SwitchInlinePick) : ConversationNavEvent

    /** Profil chatu (odesílatele ze skupiny). */
    data class OpenProfile(val chatId: Long) : ConversationNavEvent

    /** Statistika příspěvku z „Informace o zprávě". */
    data class OpenPostStatistics(val messageId: Long) : ConversationNavEvent

    /** Tlačítko bota chce formulář nové ankety (v tomhle režimu). */
    data class CreatePoll(val mode: PollComposerMode) : ConversationNavEvent

    /** „Upravit" nad seznamem úkolů v téhle zprávě. */
    data class EditChecklist(val messageId: Long) : ConversationNavEvent

    /** Tlačítko bota chce výběr lidí nebo chatu. */
    data class OpenBotShare(val request: BotShareRequest) : ConversationNavEvent

    /** Mini aplikace bota přes celou plochu. */
    data class OpenWebApp(val request: WebAppRequest) : ConversationNavEvent
}
