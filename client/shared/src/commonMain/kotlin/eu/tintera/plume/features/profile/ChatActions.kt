package eu.tintera.plume.features.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.telegram.ProfileKind
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.ConfirmDialog
import eu.tintera.plume.core.ui.LinkQrSheet
import eu.tintera.plume.core.ui.MenuAction
import eu.tintera.plume.core.ui.PhotoCropOverlay
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.core.ui.avatarShape
import eu.tintera.plume.core.ui.rememberAttachmentPicker
import eu.tintera.plume.features.calls.rememberVideoCallsSupported
import eu.tintera.plume.features.settings.BirthdatePickerDialog
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.add_members_title
import eu.tintera.plume.resources.add_photo_alternate_24dp
import eu.tintera.plume.resources.add_subscribers_title
import eu.tintera.plume.resources.add_to_home_screen_24dp
import eu.tintera.plume.resources.birthdate_suggest
import eu.tintera.plume.resources.birthdate_suggest_confirm
import eu.tintera.plume.resources.block_24dp
import eu.tintera.plume.resources.cake_24dp
import eu.tintera.plume.resources.call_24dp
import eu.tintera.plume.resources.call_start
import eu.tintera.plume.resources.call_start_video
import eu.tintera.plume.resources.chat_add_to_home_screen
import eu.tintera.plume.resources.contact_add
import eu.tintera.plume.resources.contact_edit
import eu.tintera.plume.resources.edit_24dp
import eu.tintera.plume.resources.link_24dp
import eu.tintera.plume.resources.link_qr_code
import eu.tintera.plume.resources.lock_24dp
import eu.tintera.plume.resources.person_add_24dp
import eu.tintera.plume.resources.person_remove_24dp
import eu.tintera.plume.resources.photo_suggest
import eu.tintera.plume.resources.profile_block
import eu.tintera.plume.resources.profile_block_body
import eu.tintera.plume.resources.profile_block_bot_body
import eu.tintera.plume.resources.profile_block_title
import eu.tintera.plume.resources.profile_copy_link
import eu.tintera.plume.resources.profile_remove_contact
import eu.tintera.plume.resources.profile_remove_contact_body
import eu.tintera.plume.resources.profile_remove_contact_title
import eu.tintera.plume.resources.profile_share_contact
import eu.tintera.plume.resources.profile_unblock
import eu.tintera.plume.resources.qr_code_24dp
import eu.tintera.plume.resources.secret_chat_start
import eu.tintera.plume.resources.share_24dp
import eu.tintera.plume.resources.videocam_24dp
import org.jetbrains.compose.resources.stringResource

/**
 * Položky akcí nad chatem pro ⋮ ([ChatActions]), ve dvou skupinách: [chat] (odkaz, kontakt,
 * plocha, tajný chat, návrhy) a [block] (zablokování — patří k nahlášení, ne mezi běžné akce).
 */
@Immutable
data class ChatActionGroups(val chat: List<MenuAction>, val block: List<MenuAction>) {
    val isEmpty: Boolean get() = chat.isEmpty() && block.isEmpty()

    companion object {
        val Empty = ChatActionGroups(emptyList(), emptyList())
    }
}

/**
 * Akce nad chatem jako položky nabídky: [content] je dostane a poskládá do svého ⋮ (konverzace
 * je přidá ke svým skupinám, profil stojící sám je má jako celou nabídku). Potvrzení, QR kód,
 * výběr data a úprava kontaktu se kreslí tady — jsou to vlastní okna, na místě v kompozici
 * nezáleží. Ořez fotky ne: ten leží přes celou obrazovku, kreslí ho [ChatActionsHost].
 *
 * Proč v konverzaci, a ne jen v profilu: všechny se týkají chatu, ne obrazovky profilu, a vedle
 * konverzace by profil s vlastním ⋮ nabízel druhou nabídku nad týmž chatem.
 */
@Composable
internal fun ChatActions(
    viewModel: ChatActionsViewModel,
    onShareContact: (userId: Long) -> Unit,
    onAddMembers: () -> Unit,
    /** Hovor v nabídce; `null` = je jinde (tlačítka v liště konverzace), nebo hovory nejsou. */
    onCall: ((video: Boolean) -> Unit)?,
    content: @Composable (ChatActionGroups) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val profile = state.profile
    val videoCalls = rememberVideoCallsSupported()
    var confirmBlock by rememberSaveable { mutableStateOf(false) }
    var confirmRemoveContact by rememberSaveable { mutableStateOf(false) }
    var pickBirthdate by rememberSaveable { mutableStateOf(false) }
    var showQr by rememberSaveable { mutableStateOf(false) }
    var editContact by rememberSaveable { mutableStateOf(false) }
    val photoPicker = rememberAttachmentPicker(onPicked = viewModel::suggestPhotoFrom, onError = viewModel::reportError)
    val personalPhotoPicker = rememberAttachmentPicker(onPicked = viewModel::personalPhotoFrom, onError = viewModel::reportError)

    if (profile == null) {
        content(ChatActionGroups.Empty)
        return
    }
    // Člověk (ne bot, ne vlastní účet — to jsou `SavedMessages`): kontakt, tajný chat, návrhy.
    // Tajný chat jde začít i z jiného tajného chatu: každý je vázaný na jedno zařízení.
    val isPerson = profile.kind == ProfileKind.User && profile.userId != null
    // Kontakt se posílá s číslem; kdo ho skrývá (nebo bot, ten žádné nemá), poslat nejde.
    val canShareContact = isPerson && !profile.phone.isNullOrBlank()
    // Úpravu kontaktu nabízí tužka v liště profilu ([ProfileEditButton]); v nabídce je jen tehdy,
    // když tužka patří úpravě chatu.
    val editContactInMenu = profile.editRights.any

    val chat = listOfNotNull(
        if (profile.canAddMembers) {
            MenuAction(
                stringResource(if (profile.kind == ProfileKind.Channel) Res.string.add_subscribers_title else Res.string.add_members_title),
                Res.drawable.person_add_24dp,
                onClick = onAddMembers,
            )
        } else {
            null
        },
        profile.link?.let { MenuAction(stringResource(Res.string.profile_copy_link), Res.drawable.link_24dp, onClick = viewModel::copyLink) },
        // QR kód a sdílení odkazu: kdo stojí vedle, naskenuje; komu se posílá, dostane odkaz.
        profile.link?.let { MenuAction(stringResource(Res.string.link_qr_code), Res.drawable.qr_code_24dp, onClick = { showQr = true }) },
        if (canShareContact) {
            MenuAction(
                stringResource(Res.string.profile_share_contact),
                Res.drawable.share_24dp,
                onClick = { profile.userId?.let(onShareContact) },
            )
        } else {
            null
        },
        if (viewModel.canAddToHomeScreen) {
            MenuAction(stringResource(Res.string.chat_add_to_home_screen), Res.drawable.add_to_home_screen_24dp, onClick = viewModel::addToHomeScreen)
        } else {
            null
        },
        if (isPerson && onCall != null) {
            MenuAction(stringResource(Res.string.call_start), Res.drawable.call_24dp, onClick = { onCall(false) })
        } else {
            null
        },
        if (isPerson && onCall != null && videoCalls) {
            MenuAction(stringResource(Res.string.call_start_video), Res.drawable.videocam_24dp, onClick = { onCall(true) })
        } else {
            null
        },
        if (isPerson) {
            MenuAction(stringResource(Res.string.secret_chat_start), Res.drawable.lock_24dp, onClick = viewModel::startSecretChat)
        } else {
            null
        },
        // Návrh fotky a data narození dostane jen člověk; vlastní je v úpravě profilu.
        if (isPerson && viewModel.canCropPhoto) {
            MenuAction(stringResource(Res.string.photo_suggest), Res.drawable.add_photo_alternate_24dp, onClick = photoPicker::pickMedia)
        } else {
            null
        },
        if (isPerson && profile.birthdate == null) {
            MenuAction(stringResource(Res.string.birthdate_suggest), Res.drawable.cake_24dp, onClick = { pickBirthdate = true })
        } else {
            null
        },
        if (isPerson && profile.contact != null && editContactInMenu) {
            MenuAction(stringResource(Res.string.contact_edit), Res.drawable.edit_24dp, onClick = { editContact = true })
        } else {
            null
        },
        when {
            !isPerson -> null
            // Odebrání smaže jméno uložené v kontaktech na všech zařízeních — s potvrzením.
            profile.isContact -> MenuAction(
                stringResource(Res.string.profile_remove_contact),
                Res.drawable.person_remove_24dp,
                onClick = { confirmRemoveContact = true },
            )
            else -> MenuAction(stringResource(Res.string.contact_add), Res.drawable.person_add_24dp, onClick = { viewModel.setContact(true) })
        },
    )
    val block = listOfNotNull(
        if (profile.userId != null) {
            MenuAction(
                stringResource(if (profile.isBlocked) Res.string.profile_unblock else Res.string.profile_block),
                Res.drawable.block_24dp,
                // Odblokovat je vratné a nic neodřízne — bez potvrzení.
                onClick = { if (profile.isBlocked) viewModel.setBlocked(false) else confirmBlock = true },
            )
        } else {
            null
        },
    )
    content(ChatActionGroups(chat = chat, block = block))

    if (confirmBlock) {
        ConfirmDialog(
            title = stringResource(Res.string.profile_block_title, profile.title),
            text = stringResource(if (profile.kind == ProfileKind.Bot) Res.string.profile_block_bot_body else Res.string.profile_block_body),
            confirmLabel = stringResource(Res.string.profile_block),
            onConfirm = {
                confirmBlock = false
                viewModel.setBlocked(true)
            },
            onDismiss = { confirmBlock = false },
            icon = Res.drawable.block_24dp,
            destructive = true,
        )
    }
    val qrLink = profile.link
    if (showQr && qrLink != null) {
        LinkQrSheet(
            link = qrLink,
            title = profile.title,
            onDismiss = { showQr = false },
            avatar = {
                Avatar(
                    chatId = profile.chatId,
                    title = profile.title,
                    avatarFileId = profile.photoThumbnailFileId ?: profile.photoFileId,
                    size = QR_AVATAR_SIZE,
                    shape = profile.kind.chatKind().avatarShape(),
                )
            },
        )
    }
    if (pickBirthdate) {
        BirthdatePickerDialog(
            initial = null,
            onConfirm = { picked ->
                pickBirthdate = false
                picked?.let(viewModel::suggestBirthdate)
            },
            onDismiss = { pickBirthdate = false },
            confirmLabel = Res.string.birthdate_suggest_confirm,
        )
    }
    if (confirmRemoveContact) {
        ConfirmDialog(
            title = stringResource(Res.string.profile_remove_contact_title, profile.title),
            text = stringResource(Res.string.profile_remove_contact_body),
            confirmLabel = stringResource(Res.string.profile_remove_contact),
            onConfirm = {
                confirmRemoveContact = false
                viewModel.setContact(false)
            },
            onDismiss = { confirmRemoveContact = false },
            icon = Res.drawable.person_remove_24dp,
            destructive = true,
        )
    }
    val contact = profile.contact
    if (editContact && contact != null) {
        EditContactSheet(
            contact = contact,
            name = contact.firstName,
            onSave = viewModel::saveContact,
            onSetPhoto = if (viewModel.canCropPhoto) personalPhotoPicker::pickMedia else null,
            onRemovePhoto = viewModel::removePersonalPhoto,
            onDismiss = { editContact = false },
        )
    }
}

/**
 * Co akce nad chatem potřebují od celé obrazovky: ořez fotky přes celou plochu a hlášky
 * a otevření chatu, které obrazovka předá dál. Patří do kořenového `Box` obrazovky, ne do lišty —
 * lišta se v konverzaci mění (výběr, hledání) a ořez by s ní zmizel.
 */
@Composable
internal fun ChatActionsHost(
    viewModel: ChatActionsViewModel,
    onMessage: (UiText) -> Unit,
    onOpenChat: (chatId: Long) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnMessage by rememberUpdatedState(onMessage)
    val currentOnOpenChat by rememberUpdatedState(onOpenChat)
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        viewModel.consumeMessage()
        currentOnMessage(message)
    }
    LaunchedEffect(state.openChat) {
        val target = state.openChat ?: return@LaunchedEffect
        viewModel.consumeOpenChat()
        currentOnOpenChat(target)
    }
    PhotoCropOverlay(
        path = viewModel.cropping,
        onCancel = viewModel::cancelCrop,
        onConfirm = viewModel::confirmCrop,
        video = viewModel.croppingVideo,
    )
}

/** Avatar v QR kódu odkazu na chat. */
private val QR_AVATAR_SIZE = 72.dp
