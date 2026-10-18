package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.ui.rememberExpandedSheetState
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.FilledTonalButton
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ShapeDefaults
import eu.tintera.plume.core.ui.SheetTitle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.SpamBar
import eu.tintera.plume.core.telegram.CounterpartAccountLine
import eu.tintera.plume.core.telegram.flagEmoji
import eu.tintera.plume.core.telegram.localizedCountryName
import eu.tintera.plume.core.ui.asDayLabel
import eu.tintera.plume.core.ui.name
import kotlinx.datetime.Month
import eu.tintera.plume.resources.chats_action_unarchive
import eu.tintera.plume.resources.spam_bar_registered
import eu.tintera.plume.resources.spam_bar_phone_country
import eu.tintera.plume.resources.spam_bar_name_changed
import eu.tintera.plume.resources.spam_bar_photo_changed
import eu.tintera.plume.features.chats.CleanupOptions
import eu.tintera.plume.features.chats.CleanupToggle
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.block_24dp
import eu.tintera.plume.resources.chats_delete_block
import eu.tintera.plume.resources.chats_delete_report
import eu.tintera.plume.resources.close_24dp
import eu.tintera.plume.resources.common_close
import eu.tintera.plume.resources.flag_24dp
import eu.tintera.plume.resources.profile_block
import eu.tintera.plume.resources.profile_block_body
import eu.tintera.plume.resources.profile_block_title
import eu.tintera.plume.resources.spam_bar_add_contact
import eu.tintera.plume.resources.spam_bar_emoji_status
import eu.tintera.plume.resources.spam_bar_text
import eu.tintera.plume.resources.spam_report_body
import eu.tintera.plume.resources.spam_report_confirm
import eu.tintera.plume.resources.spam_report_delete
import eu.tintera.plume.resources.spam_report_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Pruh nad konverzací u chatu od někoho, kdo není v kontaktech (`ChatActionBar`).
 *
 * **Tím Telegram spam řeší primárně**: nabídne se sám, jakmile cizí napíše, místo aby ho
 * uživatel hledal v nabídce nad chatem. Dokud tu nebyl, šlo spam nahlásit jen z potvrzení
 * smazání v seznamu chatů — tedy tam, kde ho nikdo nečeká.
 *
 * Plave nad seznamem jako pruh připnutých, se stejným okrajem a zaoblením, a stojí nad ním —
 * spam je důležitější než připnutá zpráva. Zablokovat a Nahlásit jsou chybovou barvou: jsou to
 * akce PROTI protějšku a obě otevřou sheet s volbami, ne rovnou akci.
 *
 * **Chat, který server archivoval sám, nabízí místo nahlášení „Vrátit mezi chaty"**
 * ([SpamBar.canUnarchive]) — tak to předepisuje dokumentace Telegramu. Nahlásit jde dál
 * zaškrtávátkem v sheetu Zablokovat i z nabídky nad chatem.
 *
 * Pod nadpisem jsou údaje, které server o protějšku poslal ([SpamBar.account]): podle nich se
 * pozná čerstvě založený nebo ukradený účet dřív, než uživatel na zprávu odpoví. Nad nimi
 * upozornění, že emoji status u jména nic neověřuje ([SpamBar.warnsEmojiStatus]).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SpamBarCard(
    bar: SpamBar,
    onBlock: () -> Unit,
    onReport: () -> Unit,
    onAddContact: () -> Unit,
    onUnarchive: () -> Unit,
    onDismiss: () -> Unit,
    /** Mléčné sklo nad seznamem, jako u ostatních pruhů ([ChatBarSurface]). */
    glass: Modifier = Modifier,
    modifier: Modifier = Modifier,
) {
    ChatBarSurface(glass = glass, modifier = modifier) {
        /*
         * Tlačítka jsou pod textem, ne pod řádkem s křížkem: `IconButton` má 48 dp a řádek s ním
         * by mezi textem a tlačítky nechal mezeru, ve které pruh vypadal rozpadle.
         */
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 12.dp)) {
            Column(Modifier.weight(1f).padding(top = 10.dp)) {
                Text(
                    text = stringResource(Res.string.spam_bar_text),
                    style = MaterialTheme.typography.titleSmall,
                )
                if (bar.warnsEmojiStatus) {
                    Text(
                        text = stringResource(Res.string.spam_bar_emoji_status),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                bar.account?.let { account ->
                    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        account.lines.forEach { line ->
                            Text(
                                text = line.text(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                // Akce jako malá tónová tlačítka, ne červený text: akce PROTI protějšku mají
                // `errorContainer` (M3 Expressive dává akci plochu), ostatní běžnou tónovou.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    val destructive = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    if (bar.canBlock) {
                        ChatBarButton(stringResource(Res.string.profile_block), onBlock, destructive)
                    }
                    if (bar.canUnarchive) {
                        ChatBarButton(stringResource(Res.string.chats_action_unarchive), onUnarchive)
                    } else if (bar.canReportSpam) {
                        ChatBarButton(stringResource(Res.string.chats_delete_report), onReport, destructive)
                    }
                    if (bar.canAddContact) {
                        ChatBarButton(stringResource(Res.string.spam_bar_add_contact), onAddContact)
                    }
                }
            }
            // Zavření je napořád (`removeChatActionBar`) — pruh se nevrátí ani po restartu.
            IconButton(onClick = onDismiss, shapes = IconButtonDefaults.shapes()) {
                Icon(painterResource(Res.drawable.close_24dp), contentDescription = stringResource(Res.string.common_close))
            }
        }
    }
}

/**
 * Plocha pruhu nad konverzací (spam, nasdílení čísla, žádost o vstup): **mléčné sklo se stínem
 * 3 dp, jako pruh připnutých a žádostí** — plave nad posouvaným seznamem, takže stín je na místě.
 * Dřív to byla neprůhledná karta `surfaceContainerHigh` se stínem 2 dp a mezi skleněnými pruhy
 * pod ní vypadala jako cizí prvek. Zaoblení `LargeIncreased` (ne pilulka): pruh má víc řádků.
 */
@Composable
internal fun ChatBarSurface(glass: Modifier, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = ShapeDefaults.LargeIncreased,
        shadowElevation = 3.dp,
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Box(Modifier.clip(ShapeDefaults.LargeIncreased).then(glass)) { content() }
    }
}

/** Malé tónové tlačítko akce pruhu; tvar se při stisku mění jako u ostatních tlačítek. */
@Composable
internal fun ChatBarButton(label: String, onClick: () -> Unit, colors: ButtonColors = ButtonDefaults.filledTonalButtonColors()) {
    FilledTonalButton(onClick = onClick, colors = colors, shapes = ButtonDefaults.shapes()) { Text(label) }
}

/** Jeden řádek údajů o protějšku. Země a měsíc jdou přes jazyk aplikace, jako jinde. */
@Composable
private fun CounterpartAccountLine.text(): String = when (this) {
    is CounterpartAccountLine.Registered -> stringResource(
        Res.string.spam_bar_registered,
        stringResource(Month(month.month).name()),
        month.year,
    )
    is CounterpartAccountLine.PhoneCountry -> stringResource(
        Res.string.spam_bar_phone_country,
        listOfNotNull(flagEmoji(code), localizedCountryName(code) ?: code).joinToString(" "),
    )
    is CounterpartAccountLine.NameChanged -> stringResource(Res.string.spam_bar_name_changed, at.asDayLabel())
    is CounterpartAccountLine.PhotoChanged -> stringResource(Res.string.spam_bar_photo_changed, at.asDayLabel())
}

/** Co se z pruhu spouští: zablokování, nebo nahlášení spamu — a co k tomu přibalit. */
enum class SpamBarAction { Block, Report }

/** Volby akce z pruhu; [delete] = chat po akci smazat. */
data class SpamBarCleanup(val report: Boolean, val block: Boolean, val delete: Boolean)

/**
 * Sheet z pruhu: zablokovat, nebo nahlásit — a k tomu zaškrtávátka pro zbylé dvě věci. Spam se
 * řeší všemi třemi naráz, a tak to dělá i oficiální klient (dialog „Zablokovat" s volbami
 * „nahlásit spam" a „smazat chat"). Tady je to podmenu v sheetu, stejné jako mazání v nabídce
 * nad chatem, ne dialog.
 *
 * Hlavní akce je zaškrtnutá napevno (je to tlačítko), zbylé jsou výchozí vypnuté.
 */
@Composable
internal fun SpamBarSheet(
    action: SpamBarAction,
    bar: SpamBar,
    title: String,
    onConfirm: (SpamBarCleanup) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberExpandedSheetState()
    var report by rememberSaveable { mutableStateOf(false) }
    var block by rememberSaveable { mutableStateOf(false) }
    var delete by rememberSaveable { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            SheetTitle(
                text = when (action) {
                    SpamBarAction.Block -> stringResource(Res.string.profile_block_title, title)
                    SpamBarAction.Report -> stringResource(Res.string.spam_report_title)
                },
                modifier = Modifier.padding(horizontal = 8.dp).padding(bottom = 16.dp),
            )
            CleanupOptions(
                body = stringResource(
                    when (action) {
                        SpamBarAction.Block -> Res.string.profile_block_body
                        SpamBarAction.Report -> Res.string.spam_report_body
                    },
                ),
                toggles = listOfNotNull(
                    CleanupToggle(stringResource(Res.string.chats_delete_report), report) { report = it }
                        .takeIf { action == SpamBarAction.Block && bar.canReportSpam },
                    CleanupToggle(stringResource(Res.string.chats_delete_block), block) { block = it }
                        .takeIf { action == SpamBarAction.Report && bar.canBlock },
                    CleanupToggle(stringResource(Res.string.spam_report_delete), delete) { delete = it },
                ),
                confirmLabel = stringResource(
                    when (action) {
                        SpamBarAction.Block -> Res.string.profile_block
                        SpamBarAction.Report -> Res.string.spam_report_confirm
                    },
                ),
                confirmIcon = if (action == SpamBarAction.Block) Res.drawable.block_24dp else Res.drawable.flag_24dp,
                onConfirm = {
                    onConfirm(
                        SpamBarCleanup(
                            report = action == SpamBarAction.Report || (report && bar.canReportSpam),
                            block = action == SpamBarAction.Block || (block && bar.canBlock),
                            delete = delete,
                        ),
                    )
                },
                roundTop = true,
            )
        }
    }
}
