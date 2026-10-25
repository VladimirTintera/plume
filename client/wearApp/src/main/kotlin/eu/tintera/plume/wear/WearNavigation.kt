package eu.tintera.plume.wear

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController

private const val CHATS = "chats"
private const val CHAT = "chat"
private const val CHAT_ID = "chatId"
private const val MESSAGE = "message"
private const val MESSAGE_ID = "messageId"
private const val RECORD = "record"
private const val SETTINGS = "settings"
private const val CHAT_ACTIONS = "chatActions"

/**
 * Seznam chatů, konverzace, nabídka nad zprávou, nahrání hlasovky a nastavení; zpět tahem doprava jako všude na hodinkách.
 * Fotka přes celý displej není cesta, ale vrstva nad konverzací ([PhotoViewerHost]) — kvůli sdílenému přechodu.
 * [openChat] (ťuknutí na chat v dlaždici nebo na notifikaci) otevře konverzaci nad seznamem chatů, takže tah zpět
 * vede do seznamu, ne pryč z aplikace.
 */
@Composable
fun WearNavigation(openChat: Long? = null, onChatOpened: () -> Unit = {}) {
    val navController = rememberSwipeDismissableNavController()
    LaunchedEffect(openChat) {
        if (openChat == null) return@LaunchedEffect
        navController.navigate("$CHAT/$openChat") { popUpTo(CHATS) }
        onChatOpened()
    }
    SwipeDismissableNavHost(navController = navController, startDestination = CHATS) {
        composable(CHATS) {
            ChatListScreen(
                onOpen = { chatId -> navController.navigate("$CHAT/$chatId") },
                onActions = { chatId -> navController.navigate("$CHAT_ACTIONS/$chatId") },
                onSettings = { navController.navigate(SETTINGS) },
            )
        }
        composable(SETTINGS) { SettingsScreen() }
        composable("$CHAT_ACTIONS/{$CHAT_ID}") { entry ->
            val chatId = entry.arguments?.getString(CHAT_ID)?.toLongOrNull() ?: return@composable
            ChatActionsScreen(chatId = chatId, onDone = { navController.popBackStack() })
        }
        composable("$CHAT/{$CHAT_ID}") { entry ->
            val chatId = entry.arguments?.getString(CHAT_ID)?.toLongOrNull() ?: return@composable
            PhotoViewerHost {
                ConversationScreen(
                    chatId = chatId,
                    onReact = { messageId -> navController.navigate("$MESSAGE/$chatId/$messageId") },
                    onRecord = { navController.navigate("$RECORD/$chatId") },
                )
            }
        }
        composable("$RECORD/{$CHAT_ID}") { entry ->
            val chatId = entry.arguments?.getString(CHAT_ID)?.toLongOrNull() ?: return@composable
            RecordVoiceScreen(chatId = chatId, onDone = { navController.popBackStack() })
        }
        composable("$MESSAGE/{$CHAT_ID}/{$MESSAGE_ID}") { entry ->
            val chatId = entry.arguments?.getString(CHAT_ID)?.toLongOrNull() ?: return@composable
            val messageId = entry.arguments?.getString(MESSAGE_ID)?.toLongOrNull() ?: return@composable
            MessageMenuScreen(chatId = chatId, messageId = messageId, onDone = { navController.popBackStack() })
        }
    }
}
