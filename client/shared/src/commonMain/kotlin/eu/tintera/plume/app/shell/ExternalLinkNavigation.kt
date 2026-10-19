package eu.tintera.plume.app.shell

import eu.tintera.plume.core.navigation.AddBotToGroupRoute
import eu.tintera.plume.core.navigation.ChatProfileRoute
import eu.tintera.plume.core.navigation.ConversationRoute
import eu.tintera.plume.core.navigation.ForumTopicsRoute
import eu.tintera.plume.core.navigation.MediaRoute
import eu.tintera.plume.features.links.ExternalDestination

/**
 * Cíl odkazu zvenčí ([eu.tintera.plume.features.links.ExternalLinkViewModel]). Chaty se srovnají
 * na seznam a cíl jde nad něj ([AppNavigation.openOverChats]); pod téma a mini aplikaci se vloží
 * to, odkud by se k nim uživatel proklikal sám (seznam témat, chat s botem), aby zpět vedlo tam.
 * Obrazovka aplikace (`tg://settings/…`) jde cestou odkazu ze zprávy ([AppNavigation.openScreen]).
 */
internal fun AppNavigation.openExternal(destination: ExternalDestination, twoPane: Boolean) {
    when (destination) {
        is ExternalDestination.Screen -> openScreen(destination.screen, twoPane)
        is ExternalDestination.Chat -> openOverChats {
            val target = destination.target
            if (target.topicId != null) add(ForumTopicsRoute(target.chatId))
            openTarget(target, archive = false)
            if (destination.profile) add(ChatProfileRoute(target.chatId))
        }
        is ExternalDestination.Story -> openOverChats {
            add(MediaRoute(destination.posterChatId, messageId = 0, storyId = destination.storyId))
        }
        is ExternalDestination.WebApp -> openOverChats {
            val bot = destination.request.botUserId
            // Strážní bot jen schvaluje vstup do cizího chatu — jeho vlastní chat pod ním nemá co dělat.
            if (destination.request.guardQueryId == 0L) add(ConversationRoute(bot))
            add(webAppRoute(bot, destination.request, archive = false))
        }
        is ExternalDestination.AddBotToGroup -> openOverChats {
            val link = destination.link
            add(AddBotToGroupRoute(link.url, link.botChatId, link.asAdmin))
        }
    }
}
