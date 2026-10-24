package eu.tintera.plume.wear

import androidx.compose.runtime.Composable
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController

private const val CHATS = "chats"
private const val CHAT = "chat"
private const val CHAT_ID = "chatId"
private const val REACTIONS = "reactions"
private const val MESSAGE_ID = "messageId"

/** Seznam chatů, konverzace a výběr reakce; zpět tahem doprava jako všude na hodinkách. */
@Composable
fun WearNavigation() {
    val navController = rememberSwipeDismissableNavController()
    SwipeDismissableNavHost(navController = navController, startDestination = CHATS) {
        composable(CHATS) {
            ChatListScreen(onOpen = { chatId -> navController.navigate("$CHAT/$chatId") })
        }
        composable("$CHAT/{$CHAT_ID}") { entry ->
            val chatId = entry.arguments?.getString(CHAT_ID)?.toLongOrNull() ?: return@composable
            ConversationScreen(
                chatId = chatId,
                onReact = { messageId -> navController.navigate("$REACTIONS/$chatId/$messageId") },
            )
        }
        composable("$REACTIONS/{$CHAT_ID}/{$MESSAGE_ID}") { entry ->
            val chatId = entry.arguments?.getString(CHAT_ID)?.toLongOrNull() ?: return@composable
            val messageId = entry.arguments?.getString(MESSAGE_ID)?.toLongOrNull() ?: return@composable
            ReactionPickerScreen(chatId = chatId, messageId = messageId, onDone = { navController.popBackStack() })
        }
    }
}
