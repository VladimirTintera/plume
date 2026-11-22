package eu.tintera.plume.features.media

import eu.tintera.plume.core.ui.rememberRestoringScrollState
import eu.tintera.plume.core.ui.FormTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.PhotoReportReason
import eu.tintera.plume.core.ui.GROUP_ITEM_SPACING
import eu.tintera.plume.core.ui.MediumButton
import eu.tintera.plume.core.ui.SheetActionRow
import eu.tintera.plume.core.ui.SheetTitle
import eu.tintera.plume.core.ui.rememberExpandedSheetState
import eu.tintera.plume.core.ui.sheetActionShape
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.report_photo_child_abuse
import eu.tintera.plume.resources.report_photo_copyright
import eu.tintera.plume.resources.report_photo_drugs
import eu.tintera.plume.resources.report_photo_fake
import eu.tintera.plume.resources.report_photo_hint
import eu.tintera.plume.resources.report_photo_other
import eu.tintera.plume.resources.report_photo_other_hint
import eu.tintera.plume.resources.report_photo_personal
import eu.tintera.plume.resources.report_photo_pornography
import eu.tintera.plume.resources.report_photo_send
import eu.tintera.plume.resources.report_photo_spam
import eu.tintera.plume.resources.report_photo_title
import eu.tintera.plume.resources.report_photo_violence
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Důvod nahlášení profilové fotky. Pevný výčet z TDLib (`ReportReason`) — server se na rozdíl
 * od nahlášení chatu na nic neptá, takže průvodce (`ReportFlow`) tu nepomůže. Ťuknutí na důvod
 * rovnou nahlásí, jako výběr důvodu v průvodci; „Jiný důvod" napřed chce popis.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReportPhotoSheet(
    onReport: (reason: PhotoReportReason, text: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var other by rememberSaveable { mutableStateOf(false) }
    var text by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberExpandedSheetState()) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberRestoringScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SheetTitle(stringResource(Res.string.report_photo_title))
                Text(
                    stringResource(Res.string.report_photo_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!other) {
                Column(verticalArrangement = Arrangement.spacedBy(GROUP_ITEM_SPACING)) {
                    REASONS.forEachIndexed { index, (reason, label) ->
                        SheetActionRow(
                            icon = null,
                            label = stringResource(label),
                            shape = sheetActionShape(index, REASONS.size),
                            reserveIconSpace = false,
                            onClick = {
                                if (reason == PhotoReportReason.Other) {
                                    other = true
                                } else {
                                    onReport(reason, "")
                                }
                            },
                        )
                    }
                }
            } else {
                FormTextField(
                    value = text,
                    onValueChange = { text = it.take(TEXT_MAX) },
                    label = { Text(stringResource(Res.string.report_photo_other_hint)) },
                    minLines = 3,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                MediumButton(
                    onClick = { onReport(PhotoReportReason.Other, text) },
                    enabled = text.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(Res.string.report_photo_send)) }
            }
        }
    }
}

private val REASONS: List<Pair<PhotoReportReason, StringResource>> = listOf(
    PhotoReportReason.Spam to Res.string.report_photo_spam,
    PhotoReportReason.Fake to Res.string.report_photo_fake,
    PhotoReportReason.Pornography to Res.string.report_photo_pornography,
    PhotoReportReason.Violence to Res.string.report_photo_violence,
    PhotoReportReason.ChildAbuse to Res.string.report_photo_child_abuse,
    PhotoReportReason.IllegalDrugs to Res.string.report_photo_drugs,
    PhotoReportReason.PersonalDetails to Res.string.report_photo_personal,
    PhotoReportReason.Copyright to Res.string.report_photo_copyright,
    PhotoReportReason.Other to Res.string.report_photo_other,
)

/** `reportChatPhoto` bere popis nejvýš 1024 znaků. */
private const val TEXT_MAX = 1024
