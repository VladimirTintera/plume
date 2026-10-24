package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.toResult
import dev.g000sha256.tdl.dto.DiffEntityTypeDelete
import dev.g000sha256.tdl.dto.DiffEntityTypeInsert
import dev.g000sha256.tdl.dto.DiffEntityTypeReplace
import dev.g000sha256.tdl.dto.FormattedText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * [AiTextRepository] nad TDLib.
 *
 * **Musí vznikat eagerně** (`createdAtStart`): styly chodí jen updatem po přihlášení a dotaz
 * na ně TDLib nemá — líná repository by první (a často jediný) update minula, stejně jako
 * seznam chatů (viz „Gotchas" v CLAUDE.md).
 */
class TdlAiTextRepository(
    private val holder: TelegramClientHolder,
    scope: CoroutineScope,
) : AiTextRepository {

    private val client: TdlClient get() = holder.client

    private val _styles = MutableStateFlow<List<TextStyleOption>>(emptyList())
    override val styles: StateFlow<List<TextStyleOption>> = _styles.asStateFlow()

    init {
        scope.launch {
            holder.updates { textCompositionStylesUpdates }.collect { update ->
                _styles.value = update.styles.map { TextStyleOption(name = it.name, title = it.title) }
            }
        }
    }

    override suspend fun fix(text: String): Result<FixedDraft> =
        client.fixTextWithAi(text = FormattedText(text = text, entities = emptyArray()))
            .toResult()
            .map { fixed ->
                FixedDraft(
                    text = fixed.text.text,
                    diff = DraftDiff(
                        text = fixed.diffText.text,
                        changes = fixed.diffText.entities.map { entity ->
                            when (val type = entity.type) {
                                is DiffEntityTypeInsert -> DraftChange(entity.offset, entity.length, DraftChange.Kind.Insert)
                                is DiffEntityTypeDelete -> DraftChange(entity.offset, entity.length, DraftChange.Kind.Delete)
                                is DiffEntityTypeReplace ->
                                    DraftChange(entity.offset, entity.length, DraftChange.Kind.Replace, oldText = type.oldText)
                            }
                        },
                    ),
                )
            }

    override suspend fun compose(text: String, styleName: String, translateTo: String, addEmojis: Boolean): Result<String> =
        client.composeTextWithAi(
            text = FormattedText(text = text, entities = emptyArray()),
            translateToLanguageCode = translateTo,
            styleName = styleName,
            addEmojis = addEmojis,
        ).toResult().map { it.text }
}
