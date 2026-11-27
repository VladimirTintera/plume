package eu.tintera.plume.core.settings.sync

import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.WebAppRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** Úložiště klíč–hodnota na serverech Telegramu, ve kterém leží synchronizovaná data účtu. */
interface CloudStore {
    /** Hodnoty [keys]; chybějící klíč ve výsledku není. */
    suspend fun get(keys: List<String>): Result<Map<String, String>>

    suspend fun set(key: String, value: String): Result<Unit>

    suspend fun delete(keys: List<String>): Result<Unit>
}

/**
 * CloudStorage mini aplikací pro dvojici „bot @[username], uživatel" (`sendWebAppCustomRequest`,
 * MTProto `bots.invokeWebViewCustomMethod`). Bot nic neprovozuje a do úložiště nevidí ani přes
 * Bot API; server ho ale čte, šifrované mezi zařízeními není. Viz `docs/poznatky/nastaveni.md`,
 * „Synchronizace nastavení mezi zařízeními".
 *
 * - **Bota musí TDLib znát**, jinak `User not found` — přeloží se přes `searchPublicChat`
 *   ([ChatRepository.chatByUsername]) a id se pamatuje. Soukromý chat s botem má id bota.
 * - **Odpovědi jsou JSON** jako pro mini aplikaci: `getStorageValues` objekt klíč → hodnota,
 *   zápis a mazání `true`.
 */
class BotCloudStore(
    private val webApps: WebAppRepository,
    private val chats: ChatRepository,
    private val username: String = SYNC_BOT_USERNAME,
) : CloudStore {

    private val logger = Logger.withTag("CloudStore")
    private val json = Json { ignoreUnknownKeys = true }
    private var botUserId: Long? = null

    override suspend fun get(keys: List<String>): Result<Map<String, String>> {
        if (keys.isEmpty()) return Result.success(emptyMap())
        val parameters = buildJsonObject { putJsonArray("keys") { keys.forEach { add(JsonPrimitive(it)) } } }
        return call("getStorageValues", parameters).mapCatching { result ->
            val values = json.parseToJsonElement(result) as? JsonObject ?: return@mapCatching emptyMap()
            values.mapNotNull { (key, value) ->
                value.jsonPrimitive.contentOrNull?.takeIf { it.isNotEmpty() }?.let { key to it }
            }.toMap()
        }
    }

    override suspend fun set(key: String, value: String): Result<Unit> =
        call("saveStorageValue", buildJsonObject { put("key", key); put("value", value) }).map { }

    override suspend fun delete(keys: List<String>): Result<Unit> {
        if (keys.isEmpty()) return Result.success(Unit)
        return call("deleteStorageValues", buildJsonObject { putJsonArray("keys") { keys.forEach { add(JsonPrimitive(it)) } } }).map { }
    }

    private suspend fun call(method: String, parameters: JsonObject): Result<String> {
        val bot = botUserId ?: chats.chatByUsername(username).getOrElse { failure ->
            logger.w(failure) { "Bota úložiště nejde najít." }
            return Result.failure(failure)
        }.also { botUserId = it }
        return webApps.customRequest(bot, method, json.encodeToString(JsonObject.serializer(), parameters))
            .onFailure { logger.w(it) { "Úložiště bota: $method selhalo." } }
    }
}

/** Bot, v jehož úložišti leží synchronizovaná data (založený v BotFatheru, nic neprovozuje). */
const val SYNC_BOT_USERNAME = "PlumeSyncBot"

