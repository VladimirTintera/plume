package eu.tintera.plume.wear

import androidx.compose.runtime.Composable
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController

private const val CHATS = "chats"
private const val CHAT = "chat"
private const val CHAT_ID = "chatId"

/** Seznam chatů a konverzace; zpět tahem doprava jako všude na hodinkách. */
@Composable
fun WearNavigation() {
    val navController = rememberSwipeDismissableNavController()
    SwipeDismissableNavHost(navController = navController, startDestination = CHATS) {
        composable(CHATS) {
            ChatListScreen(onOpen = { chatId -> navController.navigate("$CHAT/$chatId") })
        }
        composable("$CHAT/{$CHAT_ID}") { entry ->
            val chatId = entry.arguments?.getString(CHAT_ID)?.toLongOrNull() ?: return@composable
            ConversationScreen(chatId = chatId)
        }
    }
}
