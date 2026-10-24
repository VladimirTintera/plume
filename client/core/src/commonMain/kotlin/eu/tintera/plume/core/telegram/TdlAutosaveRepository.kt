package eu.tintera.plume.core.telegram

import co.touchlab.kermit.Logger
import dev.g000sha256.tdl.dto.AutosaveSettingsScope
import dev.g000sha256.tdl.dto.AutosaveSettingsScopeChannelChats
import dev.g000sha256.tdl.dto.AutosaveSettingsScopeChat
import dev.g000sha256.tdl.dto.AutosaveSettingsScopeGroupChats
import dev.g000sha256.tdl.dto.AutosaveSettingsScopePrivateChats
import dev.g000sha256.tdl.dto.ChatTypeBasicGroup
import dev.g000sha256.tdl.dto.ChatTypePrivate
import dev.g000sha256.tdl.dto.ChatTypeSecret
import dev.g000sha256.tdl.dto.ChatTypeSupergroup
import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.MessagePhoto
import dev.g000sha256.tdl.dto.MessageVideo
import dev.g000sha256.tdl.dto.ScopeAutosaveSettings
import dev.g000sha256.tdl.toResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import dev.g000sha256.tdl.dto.AutosaveSettings as TdlAutosaveSettings

/**
 * [AutosaveRepository] nad TDLib. **Musí vznikat eagerně** (`createdAtStart`): nové zprávy
 * přicházejí updatem a update bez odběratele se ztratí — zpráva, která dorazila před složením
 * obrazovky, by se neuložila.
 */
class TdlAutosaveRepository(
    private val holder: TelegramClientHolder,
    scope: CoroutineScope,
) : AutosaveRepository {

    private val logger = Logger.withTag("Autosave")

    private val mutex = Mutex()
    private var cached: AutosaveSettings? = null

    private val _incoming = MutableSharedFlow<AutosaveCandidate>(extraBufferCapacity = 64)
    override val incoming: Flow<AutosaveCandidate> = _incoming.asSharedFlow()

    init {
        // Změna z jiného zařízení i z vlastního `set` — celé nastavení se příště načte znovu
        // (update nese jen jeden obor a „null" znamená výchozí hodnotu, kterou nezná).
        scope.launch {
            holder.updates { autosaveSettingsUpdates }.collect { mutex.withLock { cached = null } }
        }
        scope.launch {
            holder.updates { newMessageUpdates }.collect { update ->
                candidateOf(update.message)?.let { _incoming.tryEmit(it) }
            }
        }
        scope.launch {
            holder.replaced.collect { mutex.withLock { cached = null } }
        }
    }

    override suspend fun settings(): Result<AutosaveSettings> = mutex.withLock {
        cached?.let { return@withLock Result.success(it) }
        holder.client.getAutosaveSettings().toResult()
            .map { it.toDomain() }
            .onSuccess { cached = it }
            .onFailure { logger.w(it) { "Nastavení ukládání do galerie se nepodařilo načíst." } }
    }

    override suspend fun set(scope: AutosaveScope, settings: AutosaveScopeSettings?): Result<Unit> =
        holder.client.setAutosaveSettings(scope = scope.toTdl(), settings = settings?.toTdl()).toResult()
            .map { mutex.withLock { cached = null } }
            .onFailure { logger.w(it) { "Nastavení ukládání do galerie se nepodařilo uložit ($scope)." } }

    override suspend fun clearExceptions(): Result<Unit> =
        holder.client.clearAutosaveSettingsExceptions().toResult()
            .map { mutex.withLock { cached = null } }
            .onFailure { logger.w(it) { "Výjimky ukládání do galerie se nepodařilo odebrat." } }

    /**
     * Fotka nebo video, které se uložit smí. Ne: vlastní zpráva, naplánovaná, chráněný obsah
     * (`canBeSaved`), mizející médium a tajný chat — ukládat cokoli z nich do galerie by obcházelo
     * to, co odesílatel nebo chat výslovně nechce.
     */
    private suspend fun candidateOf(message: Message): AutosaveCandidate? {
        if (message.isOutgoing || message.schedulingState != null || !message.canBeSaved || message.selfDestructType != null) return null
        val (file, isVideo, name) = when (val content = message.content) {
            is MessagePhoto -> {
                if (content.isSecret) return null
                val largest = content.photo.sizes.maxByOrNull { it.width.toLong() * it.height } ?: return null
                Triple(largest.photo, false, null)
            }
            is MessageVideo -> {
                if (content.isSecret) return null
                Triple(content.video.video, true, content.video.fileName.takeIf { it.isNotBlank() })
            }
            else -> return null
        }
        val chat = holder.client.getChat(chatId = message.chatId).toResult().getOrNull() ?: return null
        val type = when (val chatType = chat.type) {
            is ChatTypePrivate -> AutosaveChatType.Private
            is ChatTypeBasicGroup -> AutosaveChatType.Group
            is ChatTypeSupergroup -> if (chatType.isChannel) AutosaveChatType.Channel else AutosaveChatType.Group
            is ChatTypeSecret -> return null
        }
        return AutosaveCandidate(
            chatId = message.chatId,
            chatType = type,
            fileId = file.id,
            isVideo = isVideo,
            size = file.size.takeIf { it > 0 } ?: file.expectedSize,
            date = message.date,
            originalName = name,
        )
    }
}

private fun TdlAutosaveSettings.toDomain(): AutosaveSettings = AutosaveSettings(
    privateChats = privateChatSettings.toDomain(),
    groups = groupSettings.toDomain(),
    channels = channelSettings.toDomain(),
    exceptions = exceptions.map { AutosaveException(chatId = it.chatId, settings = it.settings.toDomain()) },
)

private fun ScopeAutosaveSettings.toDomain(): AutosaveScopeSettings =
    AutosaveScopeSettings(photos = autosavePhotos, videos = autosaveVideos, maxVideoBytes = maxVideoFileSize)

private fun AutosaveScopeSettings.toTdl(): ScopeAutosaveSettings =
    ScopeAutosaveSettings(autosavePhotos = photos, autosaveVideos = videos, maxVideoFileSize = maxVideoBytes)

private fun AutosaveScope.toTdl(): AutosaveSettingsScope = when (this) {
    AutosaveScope.PrivateChats -> AutosaveSettingsScopePrivateChats()
    AutosaveScope.Groups -> AutosaveSettingsScopeGroupChats()
    AutosaveScope.Channels -> AutosaveSettingsScopeChannelChats()
    is AutosaveScope.Chat -> AutosaveSettingsScopeChat(chatId = chatId)
}
