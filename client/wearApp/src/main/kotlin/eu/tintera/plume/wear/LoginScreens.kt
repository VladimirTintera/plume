package eu.tintera.plume.wear

import android.app.RemoteInput
import androidx.activity.compose.BackHandler
import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.wear.compose.material3.Icon
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.CircularProgressIndicatorDefaults
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.input.RemoteInputIntentHelper
import co.touchlab.kermit.Logger
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import eu.tintera.plume.core.telegram.AuthStage
import eu.tintera.plume.core.telegram.TelegramSession
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.auth_qr_content_description
import eu.tintera.plume.resources.close_24dp
import eu.tintera.plume.resources.common_cancel
import eu.tintera.plume.resources.wear_confirm_failed
import eu.tintera.plume.resources.wear_login_declined
import eu.tintera.plume.resources.wear_login_qr_hint
import eu.tintera.plume.resources.smartphone_24dp
import eu.tintera.plume.resources.wear_login_phone_confirmed
import eu.tintera.plume.resources.wear_login_phone_declined
import eu.tintera.plume.resources.wear_login_phone_failed
import eu.tintera.plume.resources.wear_login_phone_not_signed_in
import eu.tintera.plume.resources.wear_login_phone_waiting
import eu.tintera.plume.resources.wear_login_with_phone
import eu.tintera.plume.core.wear.WatchLogin
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material3.ButtonDefaults
import eu.tintera.plume.resources.wear_login_qr_only
import eu.tintera.plume.resources.wear_login_show_qr
import eu.tintera.plume.resources.wear_login_title
import eu.tintera.plume.resources.wear_password_enter
import eu.tintera.plume.resources.wear_password_failed
import eu.tintera.plume.resources.wear_password_hint
import eu.tintera.plume.resources.wear_password_title
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

private val logger = Logger.withTag("WearLogin")

private const val PASSWORD_KEY = "password"

/**
 * Podíl kratší strany displeje pro QR kód. Do kulatého displeje se vejde čtverec o straně
 * 1/√2 ≈ 0,71 průměru; s rezervou na bílý okraj kódu je to 0,62. Větší kód kulatý okraj ořízl
 * i s pozičním čtvercem v rohu a čtečka ho pak nemusela najít (ověřeno na emulátoru).
 */
private const val QR_SCREEN_FRACTION = 0.62f

/**
 * QR přihlášení: kód potvrdí už přihlášený klient (telefon). Odkaz po vypršení TDLib vymění
 * sám a pošle nový stav, takže obrazovka jen kreslí, co dostane.
 *
 * Kód je na začátku svisle uprostřed displeje (horní odsazení seznamu), nápověda je pod ním
 * a dá se doscrollovat — vedle kódu se na kulatý displej nevejde.
 */
@Composable
fun QrLoginScreen(link: String) {
    val state = rememberTransformingLazyColumnState()
    val qr = remember(link) { qrBitmap(link, sizePx = 400) }
    val configuration = LocalConfiguration.current
    val screen = minOf(configuration.screenWidthDp, configuration.screenHeightDp).dp
    val qrSize = screen * QR_SCREEN_FRACTION
    val centered = PaddingValues(top = (configuration.screenHeightDp.dp - qrSize) / 2, bottom = screen / 4)
    val spec = rememberTransformationSpec()
    val phone = rememberPhoneLoginState(link)
    FailureConfirmation(
        visible = phone.failureDialog,
        text = stringResource(if (phone.result == WatchLogin.Result.Declined) Res.string.wear_login_declined else Res.string.wear_confirm_failed),
        onDismiss = { phone.failureDialog = false },
    )
    if (phone.waiting) {
        PhoneLoginWaiting(phone)
        return
    }
    ScreenScaffold(scrollState = state) {
        TransformingLazyColumn(state = state, contentPadding = centered) {
            // Kód se u okraje NEzmenšuje (ostatní položky ano): zmenšený by čtečka nemusela přečíst.
            item {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    // Bílý okraj kolem kódu: na černém pozadí hodinek by ho čtečka nenašla.
                    Image(
                        bitmap = qr,
                        contentDescription = stringResource(Res.string.auth_qr_content_description),
                        modifier = Modifier
                            .size(qrSize)
                            .background(Color.White, RoundedCornerShape(12.dp))
                            .padding(8.dp),
                    )
                }
            }
            item {
                ListHeader(
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                ) { Text(stringResource(Res.string.wear_login_title)) }
            }
            // Přihlášení telefonem hned pod nadpisem, před delším návodem ke skenování: kdo má Plume
            // na telefonu, nemusí nic skenovat.
            if (phone.shown) {
                item { PhoneLoginItem(phone, Modifier.transformedItem(this, spec)) }
            }
            item {
                Text(
                    stringResource(Res.string.wear_login_qr_hint),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().transformedItem(this, spec).padding(horizontal = 12.dp),
                )
            }
        }
    }
}

/**
 * Stav přihlášení telefonem na obrazovce s QR kódem ([PhoneLogin]). Tlačítko je vidět, jen když
 * je v dosahu telefon s Plume. Po ťuknutí se čeká na potvrzení v notifikaci na telefonu a každý
 * nový odkaz (TDLib ho obnovuje zhruba po půl minutě) jde telefonu znovu — notifikace tak nese
 * platný kód, ať ho uživatel potvrdí kdykoli.
 */
private class PhoneLoginState {
    var available by mutableStateOf(false)
    var waiting by mutableStateOf(false)
    var result by mutableStateOf<WatchLogin.Result?>(null)

    /** Nezdar k ohlášení křížkem; po jeho zmizení zůstane pod nadpisem věta, proč. */
    var failureDialog by mutableStateOf(false)
    var start: () -> Unit = {}

    val shown: Boolean get() = available || waiting || result != null
}

@Composable
private fun rememberPhoneLoginState(link: String): PhoneLoginState {
    val context = LocalContext.current
    val login = remember { PhoneLogin(context.applicationContext) }
    val state = remember { PhoneLoginState() }
    state.start = {
        state.result = null
        state.waiting = true
    }
    LaunchedEffect(Unit) { state.available = login.phoneAvailable() }
    LaunchedEffect(Unit) {
        login.results().collect { result ->
            // Odpověď na zrušené čekání (zamítnuto v telefonu později) už nic neohlašuje.
            if (!state.waiting) return@collect
            state.result = result
            // Po potvrzení se čeká dál: `Ready` přijde od TDLib a obrazovka zmizí sama (fajfku
            // ukáže `WearApp`, tahle obrazovka už v tu chvíli nebude).
            if (result != WatchLogin.Result.Confirmed) {
                state.waiting = false
                state.failureDialog = true
            }
        }
    }
    LaunchedEffect(link, state.waiting) {
        if (state.waiting && !login.send(link)) {
            // Telefon mezitím zmizel z dosahu.
            state.waiting = false
            state.available = false
            state.result = WatchLogin.Result.Failed
            state.failureDialog = true
        }
    }
    return state
}

/** Tlačítko pod nadpisem QR obrazovky; po nezdaru nad ním věta, proč to nevyšlo. */
@Composable
private fun PhoneLoginItem(state: PhoneLoginState, modifier: Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        val problem = when (state.result) {
            WatchLogin.Result.Declined -> Res.string.wear_login_phone_declined
            WatchLogin.Result.NotSignedIn -> Res.string.wear_login_phone_not_signed_in
            WatchLogin.Result.Failed -> Res.string.wear_login_phone_failed
            else -> null
        }
        problem?.let {
            CenteredText(stringResource(it), color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(4.dp))
        }
        if (state.available) {
            Button(
                onClick = state.start,
                icon = { Icon(painterResource(Res.drawable.smartphone_24dp), contentDescription = null) },
                label = { Text(stringResource(Res.string.wear_login_with_phone)) },
                colors = ButtonDefaults.filledTonalButtonColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Čekání na potvrzení v telefonu přes celou obrazovku: kruh po obvodu displeje, uprostřed ikona
 * telefonu a co udělat. Dřív se věta jen vyměnila za tlačítko uvnitř seznamu s QR kódem a s jeho
 * zmenšováním u okraje se překreslovala přes nápovědu pod sebou. Po potvrzení v telefonu se
 * čeká dál („Přihlašování…"), až TDLib ohlásí přihlášení; zrušit jde jen do té doby.
 */
@Composable
private fun PhoneLoginWaiting(state: PhoneLoginState) {
    val confirmed = state.result == WatchLogin.Result.Confirmed
    val cancel = { state.waiting = false }
    BackHandler(enabled = !confirmed, onBack = cancel)
    ScreenScaffold {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                modifier = Modifier.fillMaxSize().padding(CircularProgressIndicatorDefaults.FullScreenPadding),
                strokeWidth = CircularProgressIndicatorDefaults.largeStrokeWidth,
            )
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    painterResource(Res.drawable.smartphone_24dp),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(if (confirmed) Res.string.wear_login_phone_confirmed else Res.string.wear_login_phone_waiting),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                if (!confirmed) {
                    Spacer(Modifier.height(8.dp))
                    FilledTonalIconButton(onClick = cancel, shapes = IconButtonDefaults.animatedShapes()) {
                        Icon(painterResource(Res.drawable.close_24dp), contentDescription = stringResource(Res.string.common_cancel))
                    }
                }
            }
        }
    }
}

/**
 * Dvoufázové ověření: heslo se zadává systémovou klávesnicí hodinek (`RemoteInput`) — Compose
 * for Wear OS textové pole nemá. Klávesnice heslo nemaskuje; na hodinkách jiná cesta není.
 */
@Composable
fun PasswordScreen(stage: AuthStage.WaitPassword) {
    val session = koinInject<TelegramSession>()
    val scope = rememberCoroutineScope()
    var failed by remember { mutableStateOf(false) }
    val label = stringResource(Res.string.wear_password_enter)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val password = result.data
            ?.let(RemoteInput::getResultsFromIntent)
            ?.getCharSequence(PASSWORD_KEY)
            ?.toString()
        if (!password.isNullOrEmpty()) {
            scope.launch {
                failed = session.submitPassword(password)
                    .onFailure { logger.w(it) { "Heslo na hodinkách neprošlo." } }
                    .isFailure
            }
        }
    }
    val state = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    ScreenScaffold(scrollState = state) { contentPadding ->
        TransformingLazyColumn(state = state, contentPadding = contentPadding) {
            item {
                ListHeader(
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                ) { Text(stringResource(Res.string.wear_password_title)) }
            }
            stage.hint?.takeIf { it.isNotBlank() }?.let { hint ->
                item { CenteredText(stringResource(Res.string.wear_password_hint, hint), Modifier.transformedItem(this, spec)) }
            }
            if (failed) {
                item {
                    CenteredText(
                        stringResource(Res.string.wear_password_failed),
                        Modifier.transformedItem(this, spec),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            item {
                Button(
                    onClick = { launcher.launch(passwordInput(label)) },
                    label = { Text(label) },
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                )
            }
        }
    }
}

/** Kód, e-mail nebo registrace: na hodinkách se nepíšou, přihlášení se vrátí k QR kódu. */
@Composable
fun QrOnlyScreen(onShowQr: () -> Unit) {
    val state = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    ScreenScaffold(scrollState = state) { contentPadding ->
        TransformingLazyColumn(state = state, contentPadding = contentPadding) {
            item { CenteredText(stringResource(Res.string.wear_login_qr_only), Modifier.transformedItem(this, spec)) }
            item {
                Button(
                    onClick = onShowQr,
                    label = { Text(stringResource(Res.string.wear_login_show_qr)) },
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                )
            }
        }
    }
}

@Composable
fun LoadingScreen(text: String) {
    ScreenScaffold {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 36.dp),
            )
        }
    }
}

/**
 * Celá obrazovka s jednou zprávou — prázdný seznam, chyba, chybějící oprávnění. Ikona nad textem
 * říká, o co jde, dřív než se text dočte; holý text uprostřed vypadal jako nedodělek. [error]
 * obarví ikonu chybovou barvou.
 */
@Composable
fun MessageScreen(text: String, icon: DrawableResource, error: Boolean = false) {
    ScreenScaffold {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                tint = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.height(8.dp))
            CenteredText(text)
        }
    }
}

@Composable
private fun CenteredText(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    Text(
        text,
        color = color,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp),
    )
}

private fun passwordInput(label: String): Intent =
    RemoteInputIntentHelper.createActionRemoteInputIntent().also { intent ->
        RemoteInputIntentHelper.putRemoteInputsExtra(intent, listOf(RemoteInput.Builder(PASSWORD_KEY).setLabel(label).build()))
    }

/** QR kód odkazu jako obrázek: černé moduly na bílém, okraj si dělá obrazovka sama. */
private fun qrBitmap(link: String, sizePx: Int): ImageBitmap {
    val matrix = QRCodeWriter().encode(link, BarcodeFormat.QR_CODE, sizePx, sizePx, mapOf(EncodeHintType.MARGIN to 0))
    val pixels = IntArray(matrix.width * matrix.height) { index ->
        if (matrix.get(index % matrix.width, index / matrix.width)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
    }
    return Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888).asImageBitmap()
}
