package eu.tintera.plume.features.conversation.items

import eu.tintera.plume.features.conversation.glassRim
import eu.tintera.plume.resources.conversation_jump_to_date
import eu.tintera.plume.resources.cake_24dp
import eu.tintera.plume.resources.add_photo_alternate_24dp
import eu.tintera.plume.core.ui.ConfirmDialog
import eu.tintera.plume.core.ui.resolve
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.ShapeDefaults
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import eu.tintera.plume.resources.service_upgraded_open_old
import eu.tintera.plume.resources.service_upgraded_open_new
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.ServiceOpen
import eu.tintera.plume.resources.photo_suggestion_confirm_body
import eu.tintera.plume.resources.photo_suggestion_confirm_title
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.telegram.SuggestedPhoto
import eu.tintera.plume.core.telegram.Birthdate
import eu.tintera.plume.core.ui.label
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import eu.tintera.plume.resources.birthdate_suggestion_set
import eu.tintera.plume.resources.birthdate_suggestion_confirm_title
import eu.tintera.plume.resources.birthdate_privacy_hint
import eu.tintera.plume.core.ui.asDayLabel
import eu.tintera.plume.core.ui.asTimeOfDay
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.lock_open_24dp
import eu.tintera.plume.resources.protection_request_confirm_text
import eu.tintera.plume.resources.protection_request_confirm_title
import eu.tintera.plume.resources.protection_request_approve
import eu.tintera.plume.resources.protection_request_reject
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import eu.tintera.plume.core.ui.GroupActionText
import eu.tintera.plume.core.ui.GroupAction
import eu.tintera.plume.core.ui.ActionButtonGroup
import eu.tintera.plume.resources.conversation_unread_divider
import eu.tintera.plume.resources.date_with_time
import org.jetbrains.compose.resources.stringResource

/**
 * Položka konverzace s časem, který se odkryje tahem doleva.
 *
 * Obsah se posouvá vlevo a v uvolněném pruhu vpravo se ukáže čas zprávy. Předěly dne se
 * neposouvají — jsou to nadpisy nad blokem, ne zprávy, a čas u nich nedává smysl.
 *
 * S [anchor] obsah stojí (příchozí zpráva u levého okraje): čas se vysune do volného místa vpravo
 * od ní a zprávu odsune jen o tolik, o kolik by ji jinak překryl.
 *
 * [reveal] je lambda, ne hodnota: posun se čte až při kreslení vrstvy, takže gesto překresluje
 * jen vrstvu s bublinou a nepřeskládává celou konverzaci.
 */
@Composable
internal fun RevealingTime(
    timestamp: Int,
    reveal: () -> Float,
    modifier: Modifier = Modifier,
    anchor: RevealAnchor? = null,
    content: @Composable () -> Unit,
) {
    Box(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .graphicsLayer { translationX = -(anchor?.shift(reveal()) ?: reveal()) }
                // Za vrstvou: souřadnice obsahu vůči tomuhle boxu posun vrstvy nemění.
                .then(if (anchor != null) Modifier.onPlaced { anchor.container = it } else Modifier),
        ) { content() }

        /*
         * Čas čeká za pravým okrajem položky a přijíždí s posunem. Ořezává ho jeho vlastní pruh,
         * ne seznam: na široké ploše je položka jen sloupec uprostřed seznamu (okraj
         * v `contentPadding`, viz ConversationColumn), takže čas „za okrajem" ležel v okraji
         * vedle sloupce a byl vidět pořád — při okraji užším než pruh useknutý.
         */
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .width(TIME_COLUMN_WIDTH)
                .clipToBounds(),
        ) {
            Text(
                text = timestamp.asTimeOfDay(),
                // Časy stojí ve sloupci pod sebou; proporční číslice by je rozházely.
                style = MaterialTheme.typography.labelSmall.tabularNumbers(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { translationX = TIME_COLUMN_WIDTH.toPx() - reveal() },
            )
        }
    }
}

/**
 * Ukotvení příchozí zprávy při odkrývání časů ([RevealingTime]).
 *
 * Posouvala se celá plocha, takže příchozí zprávy ujížděly začátkem za levý okraj — přitom vpravo
 * od nich je místa dost (bublina má nejvýš [MESSAGE_MAX_WIDTH_FRACTION] šířky) a čas se tam
 * vejde i bez posunu. Odchozí zprávy jedou dál: stojí u pravého okraje, kam čas přijíždí.
 *
 * Měří se skutečný pravý okraj zprávy, ne podíl šířky: pod bublinou bývají tlačítka bota nebo
 * hláška o neodeslání a ty mohou být širší. Souřadnice se čtou až při kreslení, žádný stav —
 * přeměření by jinak přeskládávalo položku.
 */
internal class RevealAnchor {
    /** Obsah položky; vůči němu se měří [content]. */
    var container: LayoutCoordinates? = null

    /** Zpráva sama, bez tváře odesílatele a bez pruhu výběru přes celou šířku. */
    var content: LayoutCoordinates? = null

    /** O kolik se obsah posune při odkrytí [reveal]: jen o to, co se do volného místa nevejde. */
    fun shift(reveal: Float): Float {
        if (reveal <= 0f) return 0f
        val outer = container?.takeIf { it.isAttached } ?: return reveal
        val inner = content?.takeIf { it.isAttached } ?: return reveal
        val free = outer.size.width - outer.localBoundingBoxOf(inner, clipBounds = false).right
        return (reveal - free).coerceAtLeast(0f)
    }
}

/**
 * Předěl „Nepřečtené zprávy" jako tónová pilulka se zvýrazněným písmem.
 *
 * Dřív vypadal přesně jako předěl dne (šedý text uprostřed), takže se v historii ztrácel mezi
 * časy — přitom je to jediná značka „odsud čti". Pilulka `secondaryContainer` ho odliší, aniž by
 * křičela: je to orientační bod, ne upozornění.
 */
@Composable
internal fun UnreadDivider(modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = DAY_DIVIDER_SPACING, bottom = DIVIDER_BOTTOM_SPACING),
    ) {
        Text(
            text = stringResource(Res.string.conversation_unread_divider),
            style = MaterialTheme.typography.labelMediumEmphasized,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer)
                // Odsud se čte nepřečtené: čtečka sem skočí jako na nadpis.
                .semantics { heading() }
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

/**
 * Systémové oznámení o dění v chatu.
 *
 * Čas se u něj nevypisuje: u „někdo se připojil" je minuta k ničemu a řádek by jen zdvojnásobil.
 */
@Composable
internal fun ServiceMessage(
    message: ChatMessage,
    modifier: Modifier = Modifier,
    onOpenChat: (Long) -> Unit = {},
    onJumpTo: (Long) -> Unit = {},
    onOpen: (ServiceOpen) -> Unit = {},
    onAcceptBirthdate: () -> Unit = {},
    onAcceptPhoto: () -> Unit = {},
    onAnswerProtection: (approve: Boolean) -> Unit = {},
) {
    // Text oznámení je [ChatMessage.description] a překládá se až tady — převedená zpráva
    // žije v paměti déle než jazyk aplikace.
    val text = message.description?.resolve() ?: message.text
    val link = message.upgradeLink
    val target = message.serviceTargetMessageId
    val open = message.serviceOpen
    val birthdate = message.suggestedBirthdate
    val photo = message.suggestedPhoto
    if (photo != null) {
        SuggestedPhotoNote(text, photo, message, modifier, onAcceptPhoto)
        return
    }
    if (birthdate != null) {
        SuggestedBirthdateNote(text, birthdate, modifier, onAcceptBirthdate)
        return
    }
    if (message.protectionDisableRequest) {
        ProtectionRequestNote(text, modifier, onAnswerProtection)
        return
    }
    if (link == null && target == null && open != null) {
        // Sdílený chat nebo člověk: ťuknutí ho otevře, jako zmínka v textu.
        CenteredNote(
            text = text,
            modifier = modifier
                .clip(ShapeDefaults.Medium)
                .clickable(role = Role.Button) { onOpen(open) },
        )
        return
    }
    if (link == null && target != null) {
        // Připnutí, změna ankety, konec soutěže, skóre: ťuknutí skočí na zprávu, o které
        // oznámení mluví. Odkaz pod textem by tu byl navíc — věta sama říká, o co jde.
        CenteredNote(
            text = text,
            // Odsazení dává poznámka sama, takže stojí přesně jako oznámení bez ťuknutí.
            modifier = modifier
                .clip(ShapeDefaults.Medium)
                .clickable(role = Role.Button) { onJumpTo(target) },
        )
        return
    }
    if (link == null) {
        CenteredNote(text = text, modifier = modifier)
        return
    }
    // Převod skupiny: dosavadní zprávy zůstaly ve staré skupině, a tohle je jediná cesta k nim
    // (a ze staré skupiny k nové). Ťuká se na celé oznámení, akce je pod ním jako odkaz.
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clip(ShapeDefaults.Medium)
            .clickable(role = Role.Button) { onOpenChat(link.chatId) }
            .padding(vertical = 4.dp),
    ) {
        CenteredNote(text = text)
        Text(
            text = stringResource(if (link.toNewGroup) Res.string.service_upgraded_open_new else Res.string.service_upgraded_open_old),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Navržená profilová fotka: fotka v kruhu (tak, jak by vypadala jako profilovka), věta a u návrhu
 * od protějšku „Nastavit". Nastavuje se až po potvrzení — profilovku vidí i ostatní.
 */
@Composable
private fun SuggestedPhotoNote(
    text: String,
    photo: SuggestedPhoto,
    message: ChatMessage,
    modifier: Modifier,
    onAccept: () -> Unit,
) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clip(ShapeDefaults.Medium)
            .then(if (photo.canSet) Modifier.clickable(role = Role.Button) { confirm = true } else Modifier)
            .padding(vertical = 8.dp),
    ) {
        Avatar(
            chatId = message.senderId,
            title = message.senderName.orEmpty(),
            avatarFileId = photo.fileId,
            size = SUGGESTED_PHOTO_SIZE,
        )
        CenteredNote(text = text)
        if (photo.canSet) {
            Text(
                text = stringResource(Res.string.birthdate_suggestion_set),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
        }
    }
    if (confirm) {
        ConfirmDialog(
            title = stringResource(Res.string.photo_suggestion_confirm_title),
            text = stringResource(Res.string.photo_suggestion_confirm_body),
            confirmLabel = stringResource(Res.string.birthdate_suggestion_set),
            onConfirm = {
                confirm = false
                onAccept()
            },
            onDismiss = { confirm = false },
            icon = Res.drawable.add_photo_alternate_24dp,
        )
    }
}

private val SUGGESTED_PHOTO_SIZE = 96.dp

/**
 * Návrh data narození od protějšku: věta a pod ní „Nastavit". Datum se mění až po potvrzení —
 * je to údaj profilu, který vidí i ostatní (podle soukromí), a jedno ťuknutí do konverzace
 * ho měnit nemá.
 */
@Composable
private fun SuggestedBirthdateNote(text: String, birthdate: Birthdate, modifier: Modifier, onAccept: () -> Unit) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clip(ShapeDefaults.Medium)
            .clickable(role = Role.Button) { confirm = true }
            .padding(vertical = 4.dp),
    ) {
        CenteredNote(text = text)
        Text(
            text = stringResource(Res.string.birthdate_suggestion_set),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
    }
    if (confirm) {
        ConfirmDialog(
            title = stringResource(Res.string.birthdate_suggestion_confirm_title, birthdate.label()),
            text = stringResource(Res.string.birthdate_privacy_hint),
            confirmLabel = stringResource(Res.string.birthdate_suggestion_set),
            onConfirm = {
                confirm = false
                onAccept()
            },
            onDismiss = { confirm = false },
            icon = Res.drawable.cake_24dp,
        )
    }
}

/**
 * Žádost protějšku o vypnutí ochrany obsahu: věta a pod ní „Odmítnout" a „Povolit". Povolení
 * jde přes potvrzení — protějšek pak smí zprávy z chatu ukládat a přeposílat.
 */
@Composable
private fun ProtectionRequestNote(text: String, modifier: Modifier, onAnswer: (approve: Boolean) -> Unit) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    val reject = stringResource(Res.string.protection_request_reject)
    val approve = stringResource(Res.string.protection_request_approve)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
    ) {
        CenteredNote(text = text)
        ActionButtonGroup(
            listOf(
                GroupAction(reject, { onAnswer(false) }, compressionLimit = ButtonDefaults.TextButtonContentPadding) { buttonModifier, source ->
                    TextButton(onClick = { onAnswer(false) }, modifier = buttonModifier, interactionSource = source, shapes = ButtonDefaults.shapes()) {
                        GroupActionText(reject)
                    }
                },
                GroupAction(approve, { confirm = true }, compressionLimit = ButtonDefaults.TextButtonContentPadding) { buttonModifier, source ->
                    TextButton(onClick = { confirm = true }, modifier = buttonModifier, interactionSource = source, shapes = ButtonDefaults.shapes()) {
                        GroupActionText(approve)
                    }
                },
            ),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        )
    }
    if (confirm) {
        ConfirmDialog(
            title = stringResource(Res.string.protection_request_confirm_title),
            text = stringResource(Res.string.protection_request_confirm_text),
            confirmLabel = approve,
            onConfirm = {
                confirm = false
                onAnswer(true)
            },
            onDismiss = { confirm = false },
            icon = Res.drawable.lock_open_24dp,
        )
    }
}

/**
 * Předěl dne.
 *
 * Bez něj se v delší historii nedá poznat, kde končí včerejšek — u zprávy se vypisují jen hodiny,
 * protože datum má nést právě tohle.
 */
@Composable
internal fun DayDivider(
    timestamp: Int,
    modifier: Modifier = Modifier,
    /** Ťuknutí otevře kalendář na tom dni; `null` = předěl je jen nápis. */
    onClick: ((timestamp: Int) -> Unit)? = null,
) {
    CenteredNote(
        text = stringResource(Res.string.date_with_time, timestamp.asDayLabel(), timestamp.asTimeOfDay()),
        modifier = modifier.padding(top = DAY_DIVIDER_SPACING, bottom = DIVIDER_BOTTOM_SPACING),
        onClick = onClick?.let { open -> { open(timestamp) } },
        onClickLabel = stringResource(Res.string.conversation_jump_to_date),
        heading = true,
    )
}

/**
 * Časový předěl uvnitř dne: nad zprávou, od které uplynula od předchozí aspoň
 * [TIME_DIVIDER_GAP_SECONDS] (viz `conversationItems`).
 *
 * V bublině čas není, takže bez tohohle se čas dal zjistit jen tahem doleva nebo v nabídce.
 * Google Zprávy to řeší stejně: souvislý rozhovor čas nepřerušuje, nová dávka zpráv po pauze
 * ho dostane. Vypadá jako předěl dne, jen nese samotné hodiny.
 */
@Composable
internal fun TimeDivider(timestamp: Int, modifier: Modifier = Modifier) {
    CenteredNote(
        text = timestamp.asTimeOfDay(),
        modifier = modifier.padding(top = DAY_DIVIDER_SPACING, bottom = DIVIDER_BOTTOM_SPACING),
    )
}

/**
 * Plovoucí odznak dne při scrollování nad zprávami.
 *
 * Má zaoblenou pilulku a stín, aby byl čitelný nad projíždějícím obsahem.
 */
@Composable
internal fun FloatingDayBadge(
    text: String,
    /** Mléčné sklo nad seznamem: plocha je průhledná, barvu i rozostření nese sklo. */
    glass: Modifier = Modifier,
    modifier: Modifier = Modifier,
    /** Ťuknutí otevře kalendář; `null` = jen nápis. */
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(BUBBLE_CORNER)
    val label = stringResource(Res.string.conversation_jump_to_date)
    Surface(
        shape = shape,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shadowElevation = 3.dp,
        modifier = modifier.then(
            if (onClick == null) Modifier else Modifier.clip(shape).clickable(onClickLabel = label, role = Role.Button, onClick = onClick),
        ),
    ) {
        Box(Modifier.clip(shape).then(glass).glassRim(shape)) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

/**
 * Informativní systémová poznámka na střed konverzace (předěly času, data, oznámení apod.).
 *
 * Zobrazuje se čistě textově bez pilulky v decentní barvě [color].
 */
@Composable
internal fun CenteredNote(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    /**
     * Ťuknutí na nápis (předěl dne → kalendář). Klikatelný je jen nápis s malým okrajem, ne celá
     * šířka řádku: vlnka přes celou konverzaci by vypadala jako ťuknutí na zprávu.
     */
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    /** Nadpis pro čtečku obrazovky (předěl dne): mezi nadpisy se dá skákat po dnech. */
    heading: Boolean = false,
) {
    // Na nápisu samém, ne na obalu: obal ťukatelného nápisu je pro čtečku prázdný uzel.
    val headingSemantics = if (heading) Modifier.semantics { heading() } else Modifier
    if (onClick != null) {
        Box(modifier.fillMaxWidth().padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium.tabularNumbers(),
                color = color,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
                    .then(headingSemantics)
                    .padding(horizontal = NOTE_TOUCH_H, vertical = 4.dp),
            )
        }
        return
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.tabularNumbers(),
        color = color,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .then(headingSemantics)
            .padding(horizontal = 24.dp, vertical = 4.dp),
    )
}

/** Vzduch kolem předělu dne. Odděluje bloky, takže potřebuje víc než rozestup mezi zprávami. */
private val DAY_DIVIDER_SPACING = 6.dp

/**
 * Spodní vzduch předělu. Předěl je samostatná položka seznamu, takže mezi ním a zprávou pod ním
 * leží ještě [MESSAGE_SPACING] z `verticalArrangement` — o ten se tady ubírá, aby zpráva seděla
 * pod předělem stejně těsně, jako když byl předěl kreslený uvnitř její položky.
 */
private val DIVIDER_BOTTOM_SPACING = DAY_DIVIDER_SPACING - MESSAGE_SPACING

/** Vodorovný okraj ťukatelného nápisu předělu — kolem textu, aby se dal trefit. */
private val NOTE_TOUCH_H = 8.dp
