package eu.tintera.plume.features.settings

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.resources.delete_24dp
import eu.tintera.plume.core.ui.ConfirmDialog
import eu.tintera.plume.resources.change_phone_row
import eu.tintera.plume.resources.call_24dp
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.runtime.produceState
import eu.tintera.plume.core.ui.GroupedColumn
import eu.tintera.plume.core.ui.displayPhone
import eu.tintera.plume.core.telegram.TelegramSession
import eu.tintera.plume.core.telegram.AccountRepository
import org.koin.compose.koinInject
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import eu.tintera.plume.resources.profile_photos_open
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.NAME_MAX_LENGTH
import eu.tintera.plume.core.telegram.USERNAME_MAX_LENGTH
import eu.tintera.plume.core.ui.usernameMessage
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.PhotoCropOverlay
import eu.tintera.plume.core.ui.rememberAttachmentPicker
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.edit_profile_bio
import eu.tintera.plume.resources.edit_profile_bio_help
import eu.tintera.plume.resources.edit_profile_bio_count
import eu.tintera.plume.resources.edit_profile_change_photo
import eu.tintera.plume.resources.edit_profile_delete_photo
import eu.tintera.plume.resources.edit_profile_delete_photo_body
import eu.tintera.plume.resources.edit_profile_delete_photo_confirm
import eu.tintera.plume.resources.edit_profile_first_name
import eu.tintera.plume.resources.edit_profile_first_name_required
import eu.tintera.plume.resources.edit_profile_last_name
import eu.tintera.plume.resources.edit_profile_save
import eu.tintera.plume.resources.edit_profile_title
import eu.tintera.plume.resources.edit_profile_username
import eu.tintera.plume.resources.edit_profile_username_help
import eu.tintera.plume.resources.edit_profile_username_prefix
import eu.tintera.plume.resources.photo_camera_24dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Úprava vlastního profilu. Otevírá se kartou účtu nahoře v seznamu Nastavení.
 *
 * - **Uložit je v liště a svítí, jen když je co uložit** a všechno projde (jméno neprázdné,
 *   uživatelské jméno volné, bio pod stropem). Chybu pole říká pod ním, ne až po ťuknutí.
 * - **Fotka se mění hned**, bez Uložit — viz [EditProfileViewModel].
 * - **Šipka zpět jen na telefonu**: v dvoupanelu stojí úprava místo sekce vedle seznamu, a po uložení tam zůstane.
 *   Neuložené změny zahodí — jako v kontaktech; potvrzovací dialog před odchodem by z běžného
 *   „rozmyslel jsem si to" dělal překážku.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EditProfileScreen(
    viewModel: EditProfileViewModel,
    /** Zpět a zavření po uložení. `null` v dvoupanelu: profil tam stojí místo sekce vedle seznamu, nemá kam vést. */
    onBack: (() -> Unit)?,
    /** Ťuknutí na fotku: historie vlastních profilových fotek v prohlížeči (id účtu = id chatu). */
    onOpenPhotos: (userId: Long) -> Unit,
    /** „Telefonní číslo": změna čísla. */
    onChangePhone: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val currentOnBack by rememberUpdatedState(onBack)
    var confirmDeletePhoto by rememberSaveable { mutableStateOf(false) }

    val notice = viewModel.notice
    val noticeText = notice?.resolve()
    // Snackbar mimo tělo efektu: vynulování hlášky mění klíč a zrušilo by ho (viz „Vrátit").
    LaunchedEffect(notice) {
        if (noticeText != null) {
            viewModel.noticeShown()
            scope.launch { snackbarHostState.showSnackbar(noticeText) }
        }
    }
    LaunchedEffect(viewModel.saved) {
        if (viewModel.saved) currentOnBack?.invoke()
    }

    val picker = rememberAttachmentPicker(onPicked = viewModel::setPhoto, onError = viewModel::reportError)

    if (confirmDeletePhoto) {
        ConfirmDialog(
            title = stringResource(Res.string.edit_profile_delete_photo),
            text = stringResource(Res.string.edit_profile_delete_photo_body),
            confirmLabel = stringResource(Res.string.edit_profile_delete_photo_confirm),
            onConfirm = {
                confirmDeletePhoto = false
                viewModel.deletePhoto()
            },
            onDismiss = { confirmDeletePhoto = false },
            icon = Res.drawable.delete_24dp,
            destructive = true,
        )
    }

    // Ořez fotky leží přes celou obrazovku, nad Scaffoldem.
    Box(Modifier.fillMaxSize()) {
        val scrollBehavior = settingsScrollBehavior()
        Scaffold(
            modifier = modifier.settingsScroll(scrollBehavior),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                SettingsTopBar(
                    title = stringResource(Res.string.edit_profile_title),
                    onBack = onBack,
                    scrollBehavior = scrollBehavior,
                    actions = {
                        SaveActionButton(onClick = viewModel::save, enabled = viewModel.canSave) {
                            Text(stringResource(Res.string.edit_profile_save))
                        }
                    },
                )
            },
        ) { padding ->
            val profile = viewModel.original
            if (profile == null) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    if (!viewModel.loadFailed) LoadingIndicator()
                }
                return@Scaffold
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Avatar(
                        chatId = profile.id,
                        title = listOf(viewModel.firstName, viewModel.lastName).joinToString(" ").trim(),
                        avatarFileId = profile.photoFileId ?: profile.photoThumbnailFileId,
                        size = AVATAR_SIZE,
                        modifier = Modifier
                            // Ořez před klikáním, jinak by vlnka přetekla z kruhu do čtverce.
                            .clip(CircleShape)
                            .clickable(
                                enabled = profile.photoFileId != null && !viewModel.photoBusy,
                                onClickLabel = stringResource(Res.string.profile_photos_open),
                                onClick = { onOpenPhotos(profile.id) },
                            ),
                    )
                    // Nahrávání fotky trvá: kolečko přes avatar, jako průběh nahrávání přílohy.
                    if (viewModel.photoBusy) CircularWavyProgressIndicator(Modifier.size(AVATAR_SIZE))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = picker::pickMedia, enabled = !viewModel.photoBusy, shapes = ButtonDefaults.shapes()) {
                        Icon(painterResource(Res.drawable.photo_camera_24dp), contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text(stringResource(Res.string.edit_profile_change_photo))
                    }
                    if (profile.photoFileId != null) {
                        TextButton(onClick = { confirmDeletePhoto = true }, enabled = !viewModel.photoBusy, shapes = ButtonDefaults.shapes()) {
                            Text(stringResource(Res.string.edit_profile_delete_photo))
                        }
                    }
                }

                OutlinedTextField(
                    value = viewModel.firstName,
                    onValueChange = { viewModel.firstName = it.take(NAME_MAX_LENGTH) },
                    label = { Text(stringResource(Res.string.edit_profile_first_name)) },
                    singleLine = true,
                    isError = viewModel.firstName.isBlank(),
                    supportingText = if (viewModel.firstName.isBlank()) {
                        { Text(stringResource(Res.string.edit_profile_first_name_required)) }
                    } else null,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = viewModel.lastName,
                    onValueChange = { viewModel.lastName = it.take(NAME_MAX_LENGTH) },
                    label = { Text(stringResource(Res.string.edit_profile_last_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )

                val usernameMessage = usernameMessage(viewModel.username.trim(), profile.username, viewModel.usernameStatus)
                OutlinedTextField(
                    value = viewModel.username,
                    // Zavináč napsaný ze zvyku se zahodí — je to prefix pole, ne součást jména.
                    onValueChange = { viewModel.username = it.removePrefix("@").take(USERNAME_MAX_LENGTH) },
                    label = { Text(stringResource(Res.string.edit_profile_username)) },
                    prefix = { Text(stringResource(Res.string.edit_profile_username_prefix)) },
                    singleLine = true,
                    isError = usernameMessage?.second == true,
                    supportingText = { Text(usernameMessage?.first ?: stringResource(Res.string.edit_profile_username_help)) },
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )

                if (viewModel.usernames.hasMore) {
                    UsernamesSection(
                        usernames = viewModel.usernames,
                        onSetActive = viewModel::setUsernameActive,
                        onMove = viewModel::moveUsername,
                    )
                }

                val bioLength = viewModel.bio.trim().length
                OutlinedTextField(
                    value = viewModel.bio,
                    onValueChange = { viewModel.bio = it },
                    label = { Text(stringResource(Res.string.edit_profile_bio)) },
                    minLines = 2,
                    isError = bioLength > profile.bioMaxLength,
                    supportingText = {
                        Row(Modifier.fillMaxWidth()) {
                            Text(stringResource(Res.string.edit_profile_bio_help), modifier = Modifier.weight(1f))
                            // Počítadlo se ukazuje vždycky: strop je krátký (70 znaků) a zjistit ho až
                            // chybou by bylo pozdě.
                            Text(stringResource(Res.string.edit_profile_bio_count, bioLength, profile.bioMaxLength))
                        }
                    },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )

                // Údaje pod poli jsou blok zaoblených řádků jako skupiny sekcí Nastavení.
                GroupedColumn(Modifier.fillMaxWidth()) {
                    BirthdateRows(
                        value = viewModel.birthdate,
                        shown = viewModel.birthdateToSave,
                        showYear = viewModel.showBirthYear,
                        onChange = { viewModel.birthdate = it },
                        onShowYearChange = { viewModel.showBirthYear = it },
                    )
                    PersonalChannelRow(
                        value = viewModel.personalChannel,
                        channels = viewModel.suitableChannels,
                        failed = viewModel.suitableChannelsFailed,
                        onOpen = viewModel::loadSuitableChannels,
                        onChange = { viewModel.personalChannel = it },
                    )
                    PhoneNumberRow(onClick = onChangePhone)
                }
                OwnProfileMusicSection(onNotice = { viewModel.notice = it })
            }
        }
        PhotoCropOverlay(
            path = viewModel.cropping,
            onCancel = viewModel::cancelCrop,
            onConfirm = viewModel::cropPhoto,
            video = viewModel.croppingVideo,
        )
    }
}

private val AVATAR_SIZE = 112.dp

/**
 * Číslo účtu pod polem bia, ťuknutí vede na změnu. Do úpravy profilu patří stejně jako jméno —
 * je to údaj, podle kterého účet najdou kontakty. Formát dává TDLib ([displayPhone]).
 */
@Composable
private fun PhoneNumberRow(onClick: () -> Unit) {
    val accounts = koinInject<AccountRepository>()
    val session = koinInject<TelegramSession>()
    val account by accounts.account.collectAsStateWithLifecycle(initialValue = null)
    val raw = account?.phoneNumber?.takeIf { it.isNotBlank() }
    val shown by produceState<String?>(initialValue = null, raw) {
        value = raw?.let { session.displayPhone(it) }
    }
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Icon(painterResource(Res.drawable.call_24dp), contentDescription = null) },
        headlineContent = { Text(stringResource(Res.string.change_phone_row)) },
        supportingContent = { shown?.let { Text(it) } },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
