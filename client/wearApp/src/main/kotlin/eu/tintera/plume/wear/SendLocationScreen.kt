package eu.tintera.plume.wear

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.Place
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.location_off_24dp
import eu.tintera.plume.resources.location_on_filled_24dp
import eu.tintera.plume.resources.wear_confirm_not_sent
import eu.tintera.plume.resources.wear_location_accuracy
import eu.tintera.plume.resources.wear_location_finding
import eu.tintera.plume.resources.wear_location_no_permission
import eu.tintera.plume.resources.wear_location_send
import eu.tintera.plume.resources.wear_location_title
import eu.tintera.plume.resources.wear_location_unavailable
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

private val logger = Logger.withTag("WearLocation")

/** Jak dlouho se čeká na čerstvou polohu, než se vezme poslední známá. GPS hodinek chytá pomaleji. */
private val FIX_TIMEOUT = 30.seconds

private sealed interface LocationStage {
    data object Asking : LocationStage
    data object Finding : LocationStage
    data class Found(val location: Location) : LocationStage
    data object Denied : LocationStage
    data object Unavailable : LocationStage
}

/**
 * Poslání aktuální polohy z hodinek, jednorázově (živá poloha by na hodinkách vybíjela baterii
 * a zůstává na telefonu). Napřed oprávnění, pak čerstvá poloha ze systému (`getCurrentLocation`,
 * Wear OS 3+) a teprve po potvrzení „Poslat polohu" odejde — kam chce člověk ukázat, kde je,
 * rozhoduje on, ne ťuknutí na tlačítko v konverzaci.
 *
 * Systémový `LocationManager`, ne Play služby: kvůli jedné poloze netřeba další knihovna
 * a hodinky mají `minSdk` 30, kde `getCurrentLocation` je. Poskytovatel `fused` (Wear OS 4+),
 * jinak GPS, jinak síť; když čerstvá poloha nepřijde do [FIX_TIMEOUT], poslední známá.
 */
@Composable
fun SendLocationScreen(chatId: Long, onDone: () -> Unit) {
    val context = LocalContext.current
    val conversations = koinInject<ConversationRepository>()
    val scope = rememberCoroutineScope()
    var stage by remember {
        mutableStateOf(if (hasPermission(context)) LocationStage.Finding else LocationStage.Asking)
    }
    var failed by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        stage = if (granted.values.any { it }) LocationStage.Finding else LocationStage.Denied
    }
    LaunchedEffect(stage) {
        when (stage) {
            LocationStage.Asking -> permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            LocationStage.Finding -> stage = currentLocation(context)?.let { LocationStage.Found(it) } ?: LocationStage.Unavailable
            else -> Unit
        }
    }

    when (val current = stage) {
        LocationStage.Asking, LocationStage.Finding -> LoadingScreen(stringResource(Res.string.wear_location_finding))
        LocationStage.Denied -> MessageScreen(stringResource(Res.string.wear_location_no_permission), Res.drawable.location_off_24dp)
        LocationStage.Unavailable -> MessageScreen(stringResource(Res.string.wear_location_unavailable), Res.drawable.location_off_24dp, error = true)
        is LocationStage.Found -> {
            val location = current.location
            val state = rememberTransformingLazyColumnState()
            ScreenScaffold(
                scrollState = state,
                edgeButton = {
                    EdgeButton(onClick = {
                        scope.launch {
                            conversations.sendLocation(chatId, Place(title = "", address = "", latitude = location.latitude, longitude = location.longitude), replyToMessageId = null)
                                .onSuccess { onDone() }
                                .onFailure {
                                    logger.w(it) { "Poloha z hodinek do chatu $chatId neodešla." }
                                    failed = true
                                }
                        }
                    }) { Text(stringResource(Res.string.wear_location_send)) }
                },
            ) { contentPadding ->
                TransformingLazyColumn(state = state, contentPadding = contentPadding) {
                    item {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                painterResource(Res.drawable.location_on_filled_24dp),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp),
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(stringResource(Res.string.wear_location_title), style = MaterialTheme.typography.titleMedium)
                            if (location.hasAccuracy()) {
                                Text(
                                    stringResource(Res.string.wear_location_accuracy, location.accuracy.roundToInt()),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    FailureConfirmation(visible = failed, text = stringResource(Res.string.wear_confirm_not_sent), onDismiss = { failed = false })
}

private fun hasPermission(context: Context): Boolean =
    listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

/**
 * Čerstvá poloha, jinak poslední známá; `null`, když není žádná. Poskytovatelé se zkoušejí po
 * řadě (`fused`, GPS, síť), dokud některý polohu nevrátí — `getCurrentLocation` vrací `null`
 * hned, když poskytovatel polohu dát neumí (na emulátoru `fused` nedostal polohu z `geo fix`,
 * ta jde jen do GPS). Na všechny dohromady nejvýš [FIX_TIMEOUT].
 */
@SuppressLint("MissingPermission") // oprávnění ověřené před voláním (stav Finding)
private suspend fun currentLocation(context: Context): Location? {
    val manager = context.getSystemService(LocationManager::class.java) ?: return null
    val enabled = manager.getProviders(true)
    val providers = listOfNotNull(
        LocationManager.FUSED_PROVIDER.takeIf { Build.VERSION.SDK_INT >= Build.VERSION_CODES.S },
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER,
    ).filter { it in enabled }
    val fresh = withTimeoutOrNull(FIX_TIMEOUT) {
        providers.firstNotNullOfOrNull { provider ->
            suspendCancellableCoroutine<Location?> { continuation ->
                val cancel = CancellationSignal()
                continuation.invokeOnCancellation { cancel.cancel() }
                manager.getCurrentLocation(provider, cancel, ContextCompat.getMainExecutor(context)) { continuation.resume(it) }
            }
        }
    }
    return fresh ?: enabled.mapNotNull { manager.getLastKnownLocation(it) }.maxByOrNull { it.time }
        .also { if (it == null) logger.w { "Poloha hodinek není k dispozici (poskytovatelé $providers)." } }
}
