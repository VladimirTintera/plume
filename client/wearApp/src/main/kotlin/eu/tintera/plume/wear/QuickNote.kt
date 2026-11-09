package eu.tintera.plume.wear

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.AuthStage
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.TelegramSession
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.lock_24dp
import eu.tintera.plume.resources.saved_messages
import eu.tintera.plume.resources.wear_confirm_not_saved
import eu.tintera.plume.resources.wear_confirm_saved
import eu.tintera.plume.resources.wear_note_label
import eu.tintera.plume.resources.wear_note_saving
import eu.tintera.plume.resources.wear_note_signed_out
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.time.Duration.Companion.seconds

private val logger = Logger.withTag("WearQuickNote")

/** Jak dlouho se po studeném startu čeká na TDLib a na id Uložených zpráv. */
private val READY_TIMEOUT = 15.seconds

/**
 * Rychlá poznámka do Uložených zpráv: z dlaždice nebo komplikace rovnou do diktování, text se
 * uloží a aktivita se zavře. Vlastní aktivita (ne cesta v aplikaci): z dlaždice se nemá otevřít
 * seznam chatů a po uložení se má uživatel vrátit tam, odkud přišel — na ciferník nebo dlaždici
 * (`taskAffinity` a `excludeFromRecents` v manifestu).
 */
class QuickNoteActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PrimaryAccountScope { WearTheme { QuickNoteScreen(onFinish = ::finish) } } }
    }

    companion object {
        fun intent(context: Context): Intent =
            Intent(context, QuickNoteActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

private enum class NoteStage { Input, Saving, Saved, Failed, SignedOut }

/**
 * Diktování se otevře hned, ještě než TDLib po studeném startu doběhne — čekání na spojení by
 * zdrželo právě tu chvíli, kdy si uživatel chce něco rychle zapsat. Na přihlášení a id Uložených
 * zpráv se čeká až s hotovým textem. Odhlášené hodinky to poznají předem, pokud už stav znají.
 * Zavření diktování bez textu aktivitu zavře.
 */
@Composable
private fun QuickNoteScreen(onFinish: () -> Unit) {
    val session = koinInject<TelegramSession>()
    val chats = koinInject<ChatRepository>()
    val conversations = koinInject<ConversationRepository>()
    val scope = rememberCoroutineScope()
    var stage by remember { mutableStateOf(NoteStage.Input) }

    val openInput = rememberReplyInput(label = Res.string.wear_note_label, quickReplies = false, onCancel = onFinish) { text ->
        stage = NoteStage.Saving
        scope.launch {
            val ready = withTimeoutOrNull(READY_TIMEOUT) { session.authStage.first { it == AuthStage.Ready } } != null
            val savedId = if (ready) withTimeoutOrNull(READY_TIMEOUT) { chats.savedMessagesChatId.filterNotNull().first() } else null
            stage = when {
                !ready -> NoteStage.SignedOut
                savedId == null -> NoteStage.Failed
                else -> conversations.send(savedId, text, replyToMessageId = null)
                    .onFailure { logger.w(it) { "Poznámka z hodinek se do Uložených neuložila." } }
                    .fold(onSuccess = { NoteStage.Saved }, onFailure = { NoteStage.Failed })
            }
        }
    }
    val title = stringResource(Res.string.saved_messages)
    LaunchedEffect(Unit) {
        val known = session.authStage.value
        if (known != AuthStage.Ready && known != AuthStage.Connecting) stage = NoteStage.SignedOut else openInput(title)
    }

    when (stage) {
        // Pod diktováním (je to jiná aktivita) nic: po zavření bez textu aktivita hned skončí.
        NoteStage.Input -> Box(Modifier.fillMaxSize())
        NoteStage.Saving, NoteStage.Saved, NoteStage.Failed -> LoadingScreen(stringResource(Res.string.wear_note_saving))
        NoteStage.SignedOut -> MessageScreen(stringResource(Res.string.wear_note_signed_out), Res.drawable.lock_24dp)
    }
    SuccessConfirmation(visible = stage == NoteStage.Saved, text = stringResource(Res.string.wear_confirm_saved), onDismiss = onFinish)
    FailureConfirmation(visible = stage == NoteStage.Failed, text = stringResource(Res.string.wear_confirm_not_saved), onDismiss = onFinish)
}
