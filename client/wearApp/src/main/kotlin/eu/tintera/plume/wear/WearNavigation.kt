package eu.tintera.plume.wear

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController

private const val CHATS = "chats"
private const val CHAT = "chat"
private const val CHAT_ID = "chatId"
private const val REACTIONS = "reactions"
private const val MESSAGE_ID = "messageId"
private const val RECORD = "record"
private const val PHOTO = "photo"
private const val FILE_ID = "fileId"

/**
 * Seznam chatů, konverzace, výběr reakce, nahrání hlasovky a fotka přes celý displej; zpět tahem doprava jako všude na hodinkách.
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
            ChatListScreen(onOpen = { chatId -> navController.navigate("$CHAT/$chatId") })
        }
        composable("$CHAT/{$CHAT_ID}") { entry ->
            val chatId = entry.arguments?.getString(CHAT_ID)?.toLongOrNull() ?: return@composable
            ConversationScreen(
                chatId = chatId,
                onReact = { messageId -> navController.navigate("$REACTIONS/$chatId/$messageId") },
                onRecord = { navController.navigate("$RECORD/$chatId") },
                onOpenPhoto = { fileId -> navController.navigate("$PHOTO/$fileId") },
            )
        }
        composable("$PHOTO/{$FILE_ID}") { entry ->
            val fileId = entry.arguments?.getString(FILE_ID)?.toIntOrNull() ?: return@composable
            PhotoScreen(fileId = fileId)
        }
        composable("$RECORD/{$CHAT_ID}") { entry ->
            val chatId = entry.arguments?.getString(CHAT_ID)?.toLongOrNull() ?: return@composable
            RecordVoiceScreen(chatId = chatId, onDone = { navController.popBackStack() })
        }
        composable("$REACTIONS/{$CHAT_ID}/{$MESSAGE_ID}") { entry ->
            val chatId = entry.arguments?.getString(CHAT_ID)?.toLongOrNull() ?: return@composable
            val messageId = entry.arguments?.getString(MESSAGE_ID)?.toLongOrNull() ?: return@composable
            ReactionPickerScreen(chatId = chatId, messageId = messageId, onDone = { navController.popBackStack() })
        }
    }
}
