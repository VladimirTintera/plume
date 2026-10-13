package eu.tintera.plume.core.calls

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import co.touchlab.kermit.Logger
import eu.tintera.plume.client.R
import eu.tintera.plume.core.video.PictureInPictureArbiter
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.call_hang_up
import eu.tintera.plume.resources.call_mute
import eu.tintera.plume.resources.call_unmute
import org.jetbrains.compose.resources.stringResource

/**
 * Android: okénko je vlastnost aktivity (`supportsPictureInPicture`), stejně jako u videa
 * v prohlížeči (`rememberPictureInPicture`, viz `docs/poznatky/prohlizec-medii.md`) — tytéž
 * zásady: automatika od Androidu 12 `setAutoEnterEnabled`, starší přes `onUserLeaveHint`,
 * akce jako `RemoteAction` s broadcastem jen pro vlastní balíček, poměr stran v mezích, které
 * systém bere. Parametry i automatiku vede [PictureInPictureArbiter], sdílený s prohlížečem.
 *
 * - **Okénko hovor chce, jen dokud je v něm obraz** (i s koncem hovoru přestane) — jinak by
 *   aplikace šla do okénka i bez videohovoru.
 * - **Konec hovoru v okénku okénko zavře** (`moveTaskToBack`): zbylo by v něm „Hovor skončil"
 *   nad zmenšenou aplikací. Jiná cesta, jak okénko zavřít z aplikace, není.
 */
@Composable
actual fun rememberCallPictureInPicture(
    enabled: Boolean,
    aspectRatio: Float,
    muted: Boolean,
    onToggleMute: () -> Unit,
    onHangUp: () -> Unit,
): CallPictureInPicture {
    val activity = LocalActivity.current as? ComponentActivity
    val supported = remember(activity) {
        activity != null && activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }
    val arbiter = remember(activity) { activity?.let(PictureInPictureArbiter::of) }
    val state = remember(activity) { AndroidCallPictureInPicture() }

    // Okénko si s videem v prohlížeči dělí aktivita; parametry a automatiku vede rozhodčí.
    DisposableEffect(arbiter) {
        arbiter?.register(state.owner)
        onDispose { arbiter?.unregister(state.owner) }
    }

    val toggleMute by rememberUpdatedState(onToggleMute)
    val hangUp by rememberUpdatedState(onHangUp)
    DisposableEffect(activity) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    ACTION_MUTE -> toggleMute()
                    ACTION_HANG_UP -> hangUp()
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(ACTION_MUTE)
            addAction(ACTION_HANG_UP)
        }
        activity?.let { ContextCompat.registerReceiver(it, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED) }
        onDispose { activity?.unregisterReceiver(receiver) }
    }

    val muteLabel = stringResource(if (muted) Res.string.call_unmute else Res.string.call_mute)
    val hangUpLabel = stringResource(Res.string.call_hang_up)
    val params = remember(supported, enabled, aspectRatio, muted, muteLabel, hangUpLabel) {
        if (supported && activity != null) callPipParams(activity, enabled, aspectRatio, muted, muteLabel, hangUpLabel) else null
    }
    DisposableEffect(params, enabled) {
        state.owner.params = params
        state.owner.wantsAutoEnter = enabled
        arbiter?.update()
        onDispose { }
    }

    // Obraz z hovoru zmizel (konec, obě kamery vypnuté) a aplikace je v okénku: pryč s ním.
    DisposableEffect(enabled, state.active) {
        if (!enabled && state.active) runCatching { activity?.moveTaskToBack(false) }.onFailure(::logFailure)
        onDispose { }
    }
    return state
}

private class AndroidCallPictureInPicture : CallPictureInPicture {
    override var active by mutableStateOf(false)
    override var elsewhere by mutableStateOf(false)
    val owner = PictureInPictureArbiter.Owner(onActive = { active = it }, onOther = { elsewhere = it })
}

private fun callPipParams(
    context: Context,
    enabled: Boolean,
    aspectRatio: Float,
    muted: Boolean,
    muteLabel: String,
    hangUpLabel: String,
): PictureInPictureParams {
    fun action(icon: Int, label: String, intentAction: String, request: Int) = RemoteAction(
        Icon.createWithResource(context, icon),
        label,
        label,
        PendingIntent.getBroadcast(
            context,
            request,
            Intent(intentAction).setPackage(context.packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        ),
    )
    val ratio = aspectRatio.takeIf { it.isFinite() && it > 0f }?.coerceIn(MIN_RATIO, MAX_RATIO) ?: DEFAULT_RATIO
    return PictureInPictureParams.Builder()
        .setAspectRatio(Rational((ratio * RATIO_BASE).toInt(), RATIO_BASE))
        .setActions(
            listOf(
                action(if (muted) R.drawable.ic_pip_mic_off else R.drawable.ic_pip_mic, muteLabel, ACTION_MUTE, REQUEST_MUTE),
                action(R.drawable.ic_pip_hang_up, hangUpLabel, ACTION_HANG_UP, REQUEST_HANG_UP),
            ),
        )
        .apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setAutoEnterEnabled(enabled)
                setSeamlessResizeEnabled(false)
            }
        }
        .build()
}

private fun logFailure(failure: Throwable) {
    Logger.withTag("CallPictureInPicture").w(failure) { "Okénko hovoru se nepodařilo nastavit." }
}

private const val ACTION_MUTE = "eu.tintera.plume.action.CALL_PIP_MUTE"
private const val ACTION_HANG_UP = "eu.tintera.plume.action.CALL_PIP_HANG_UP"
private const val REQUEST_MUTE = 7_303
private const val REQUEST_HANG_UP = 7_304
private const val RATIO_BASE = 1000
private const val MIN_RATIO = 1f / 2.39f
private const val MAX_RATIO = 2.39f

/** Telefon na výšku — obraz protistrany, dokud nepřijde první snímek s rozměrem. */
private const val DEFAULT_RATIO = 9f / 16f
