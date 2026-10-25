package eu.tintera.plume.wear

import android.app.RemoteInput
import android.content.Intent
import android.view.inputmethod.EditorInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.wear.input.RemoteInputIntentHelper
import androidx.wear.input.wearableExtender
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.wear_quick_reply_1
import eu.tintera.plume.resources.wear_quick_reply_2
import eu.tintera.plume.resources.wear_quick_reply_3
import eu.tintera.plume.resources.wear_quick_reply_4
import eu.tintera.plume.resources.wear_quick_reply_5
import eu.tintera.plume.resources.wear_reply_label
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private const val REPLY_KEY = "reply"

/**
 * Systémové zadávání odpovědi na hodinkách (diktování, klávesnice, emoji a naše rychlé odpovědi
 * jako volby). Vrací funkci, která ho otevře s nadpisem [title] — v konverzaci název chatu,
 * u odpovědi na zprávu její začátek. Napsaný text (oříznutý, neprázdný) dostane [onReply],
 * zavření bez textu [onCancel]. Sdílí ho tlačítko Odpovědět pod konverzací, nabídka nad zprávou
 * a rychlá poznámka — ta bez rychlých odpovědí ([quickReplies] = `false`) a s vlastním [label].
 */
@Composable
fun rememberReplyInput(
    label: StringResource = Res.string.wear_reply_label,
    quickReplies: Boolean = true,
    onCancel: () -> Unit = {},
    onReply: (String) -> Unit,
): (title: String) -> Unit {
    val labelText = stringResource(label)
    val choices = if (!quickReplies) emptyList() else listOf(
        stringResource(Res.string.wear_quick_reply_1),
        stringResource(Res.string.wear_quick_reply_2),
        stringResource(Res.string.wear_quick_reply_3),
        stringResource(Res.string.wear_quick_reply_4),
        stringResource(Res.string.wear_quick_reply_5),
    )
    val currentOnReply by rememberUpdatedState(onReply)
    val currentOnCancel by rememberUpdatedState(onCancel)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val text = result.data
            ?.let(RemoteInput::getResultsFromIntent)
            ?.getCharSequence(REPLY_KEY)
            ?.toString()
            ?.trim()
        if (!text.isNullOrEmpty()) currentOnReply(text) else currentOnCancel()
    }
    return { title -> launcher.launch(replyInput(title, labelText, choices)) }
}

/**
 * Systémové zadávání hodinek: diktování, klávesnice a emoji, k tomu rychlé odpovědi jako volby.
 * Odeslání se potvrzuje akcí „odeslat" na klávesnici.
 */
private fun replyInput(title: String, label: String, quickReplies: List<String>): Intent =
    RemoteInputIntentHelper.createActionRemoteInputIntent().also { intent ->
        val input = RemoteInput.Builder(REPLY_KEY)
            .setLabel(label)
            .apply { if (quickReplies.isNotEmpty()) setChoices(quickReplies.toTypedArray()) }
            .wearableExtender {
                setEmojisAllowed(true)
                setInputActionType(EditorInfo.IME_ACTION_SEND)
            }
            .build()
        RemoteInputIntentHelper.putRemoteInputsExtra(intent, listOf(input))
        RemoteInputIntentHelper.putTitleExtra(intent, title)
    }
