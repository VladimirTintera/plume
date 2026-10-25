package eu.tintera.plume.core.video

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
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import eu.tintera.plume.client.R
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.media_pause
import eu.tintera.plume.resources.media_play
import org.jetbrains.compose.resources.stringResource

/**
 * Android: PiP je vlastnost AKTIVITY (`supportsPictureInPicture` v manifestu) — do okénka jde
 * celá, prohlížeč v ní jen schová ovládání ([PictureInPicture.active]).
 *
 * - **Automatický přechod** od Androidu 12 nese `setAutoEnterEnabled` (systém ho spustí sám při
 *   gestu domů a animuje z videa); starší verze ho spustí z `onUserLeaveHint`. Obojí jen,
 *   dokud video hraje.
 * - **Parametry vede [PictureInPictureArbiter]**: `setPictureInPictureParams` platí pro
 *   aktivitu, ne pro obrazovku, a okénko chce i videohovor. Po odchodu z kompozice se
 *   prohlížeč odhlásí; bez toho by aktivita šla do okénka i ze seznamu chatů.
 * - **Akce v okénku** jsou `RemoteAction` s broadcastem jen pro vlastní balíček
 *   (`setPackage`, přijímač `RECEIVER_NOT_EXPORTED`).
 * - Poměr stran ořízne na meze, které systém bere (1 : 2,39 až 2,39 : 1); mimo ně by
 *   `setPictureInPictureParams` vyhodilo výjimku.
 */
@Composable
actual fun rememberPictureInPicture(
    player: VideoPlayer,
    aspectRatio: Float,
    playing: Boolean,
    onTogglePlay: () -> Unit,
    onReturn: ((positionMs: Long) -> Unit)?,
): PictureInPicture {
    val context = LocalContext.current
    val activity = LocalActivity.current as? ComponentActivity
    val supported = remember(activity) {
        activity != null && activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }
    val state = remember(activity) { AndroidPictureInPicture(activity?.let(PictureInPictureArbiter::of), supported) }

    // Okénko si s hovorem dělí aktivita; parametry a automatiku vede rozhodčí.
    DisposableEffect(state) {
        state.arbiter?.register(state.owner)
        onDispose { state.arbiter?.unregister(state.owner) }
    }

    val toggle by rememberUpdatedState(onTogglePlay)
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = toggle()
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(ACTION_TOGGLE), ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }

    // Parametry se mění s poměrem stran a s tím, jestli video hraje (ikona akce, automatika:
    // do okénka se jde samo jen s hrajícím videem).
    val playLabel = stringResource(Res.string.media_play)
    val pauseLabel = stringResource(Res.string.media_pause)
    val params = remember(supported, aspectRatio, playing, playLabel, pauseLabel) {
        if (supported) pipParams(context, aspectRatio, playing, if (playing) pauseLabel else playLabel) else null
    }
    DisposableEffect(params, playing) {
        state.owner.params = params
        state.owner.wantsAutoEnter = playing
        state.arbiter?.update()
        onDispose { }
    }
    return state
}

private class AndroidPictureInPicture(
    val arbiter: PictureInPictureArbiter?,
    override val supported: Boolean,
) : PictureInPicture {
    override var active by mutableStateOf(false)
    val owner = PictureInPictureArbiter.Owner(onActive = { active = it })

    override fun enter() {
        arbiter?.enter(owner)
    }
}

private fun pipParams(context: Context, aspectRatio: Float, playing: Boolean, label: String): PictureInPictureParams {
    val ratio = aspectRatio.takeIf { it.isFinite() && it > 0f }?.coerceIn(MIN_RATIO, MAX_RATIO) ?: (16f / 9f)
    val action = RemoteAction(
        Icon.createWithResource(context, if (playing) R.drawable.ic_pip_pause else R.drawable.ic_pip_play),
        label,
        label,
        PendingIntent.getBroadcast(
            context,
            REQUEST_TOGGLE,
            Intent(ACTION_TOGGLE).setPackage(context.packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        ),
    )
    return PictureInPictureParams.Builder()
        .setAspectRatio(Rational((ratio * RATIO_BASE).toInt(), RATIO_BASE))
        .setActions(listOf(action))
        .apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setAutoEnterEnabled(playing)
                setSeamlessResizeEnabled(false)
            }
        }
        .build()
}

private const val ACTION_TOGGLE = "eu.tintera.plume.action.PIP_TOGGLE"
private const val REQUEST_TOGGLE = 7_301
private const val RATIO_BASE = 1000
private const val MIN_RATIO = 1f / 2.39f
private const val MAX_RATIO = 2.39f
