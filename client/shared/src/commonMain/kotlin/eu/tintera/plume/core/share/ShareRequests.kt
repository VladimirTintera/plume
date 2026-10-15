package eu.tintera.plume.core.share

import eu.tintera.plume.core.telegram.OutgoingAttachment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * Obsah sdílený do aplikace (nabídka „Sdílet" v jiné aplikaci, odkaz `t.me/share`, sdílení sady
 * samolepek) a kam s ním.
 *
 * **Stav, ne událost**, ze stejného důvodu jako `ChatOpenRequests`: sdílet jde i do aplikace,
 * která neběží nebo visí na přihlášení. Požadavek si počká, než vznikne shell.
 *
 * Dvě cesty:
 * - **Bez chatu** ([SharedContent.carried]): obsah nese čip v shellu („Sdílíte: …"), uživatel
 *   se volně pohybuje po aplikaci a do konverzace ho vloží až ťuknutím ([choose]). Takové
 *   sdílení přežije zabití procesu ([store]), nejdéle [SHARE_LIFETIME_MS].
 * - **S chatem vybraným předem** ([start] s `target`): Direct Share a iOS rozšíření. Čip nemá,
 *   neukládá se; čeká na obrazovce „Komu: …" ([confirm]), nebo rovnou v konverzaci.
 *
 * Průběh: platforma ohlásí příchod ([start]) hned, soubory připravuje dál ([prepared]) — kopie
 * videa může trvat a čip se má ukázat okamžitě. Konverzace si obsah vyzvedne ([deliveries]),
 * až je chat vybraný a obsah připravený.
 */
class ShareRequests(
    /** Kam se ukládá sdílení na čipu; `null` = jen v paměti (iOS, desktop, testy). */
    private val store: ShareStore? = null,
    /** Kde se zapisuje na disk; `null` = hned ve volajícím vlákně (testy). */
    private val scope: CoroutineScope? = null,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {

    private var nextId = 1L

    private val current = MutableStateFlow(restore())

    /** Sdílený obsah, se kterým se ještě nic nestalo; `null`, když žádný není. */
    val incoming: StateFlow<SharedContent?> = current.asStateFlow()

    private val arrived = MutableStateFlow<Long?>(null)

    /**
     * Id čerstvě přišlého sdílení na čip, kvůli kterému má shell ukázat seznam chatů. Jen
     * jednou ([arrivalShown]) — obnova aktivity (otočení) by jinak zavřela, co mezitím uživatel
     * otevřel. Sdílení obnovené po zabití procesu sem nepřijde: zásobník se obnovil s ním.
     */
    val arrival: StateFlow<Long?> = arrived.asStateFlow()

    /** Zápisy na disk jdou po jednom, v pořadí změn — ať pozdější stav nepřepíše starší. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val writer = Dispatchers.Default.limitedParallelism(1)

    /**
     * Přišlo sdílení. [hasFiles] = soubory se teprve připravují a přijdou v [prepared]; bez nich
     * je obsah hned hotový. Nové sdílení nahradí to předchozí, nevyřízené — i na čipu.
     *
     * **Cíl se zakládá SPOLU s obsahem** ([target]), ne dalším voláním. Mezi `start` a
     * [confirm] by na okamžik existoval obsah bez chatu: otevřená obrazovka „Komu: …"
     * ho brala jako „obsah zmizel" a odešla do předchozí aplikace. Opakované sdílení tak zavřelo
     * obrazovku a nové se neukázalo vůbec.
     */
    fun start(text: String?, hasFiles: Boolean, target: ShareTarget? = null): Long {
        val id = nextId++
        val replaced = current.getAndUpdate {
            SharedContent(
                id = id,
                text = text?.takeIf { it.isNotBlank() },
                attachments = if (hasFiles) null else emptyList(),
                targetChatId = target?.chatId,
                awaitingConfirmation = target?.confirm ?: false,
                carried = target == null,
                createdAt = now(),
            )
        }
        // Nahrazený obsah z čipu už nikdo nepošle; jeho kopie by v cache jen ležely.
        replaced?.takeIf { it.carried }?.let(::deleteFiles)
        if (target == null) arrived.value = id
        persist()
        return id
    }

    /**
     * Soubory jsou v cache; [failed] se nepodařilo připravit a konverzace to ohlásí.
     *
     * [denied] z nich selhalo na chybějícím OPRÁVNĚNÍ — sdílející aplikace Plume ke souboru
     * nepustila. Tak chodí snímek obrazovky sdílený z jeho náhledu přímo na kontakt: intent
     * spouští Android System Intelligence, a protože sám ke snímku přístup nemá, nemá co předat
     * (`flg` bez `FLAG_GRANT_READ_URI_PERMISSION`). Obejít to nejde; obrazovka „Komu: …" proto
     * místo obecné chyby řekne, ať snímek uživatel vybere v galerii.
     */
    fun prepared(
        id: Long,
        attachments: List<OutgoingAttachment>,
        failed: Int,
        sources: Map<String, String> = emptyMap(),
        denied: Int = 0,
    ) {
        var late = false
        current.update { content ->
            if (content?.id == id) {
                content.copy(attachments = attachments, failed = failed, denied = denied, sources = sources)
            } else {
                late = true
                content
            }
        }
        // Příprava doběhla po zahození nebo nahrazení — kopie už nikomu nepatří.
        if (late) deleteFiles(attachments) else persist()
    }

    /**
     * Uživatel vybral chat — u sdílení na čipu ťuknutím na „Vložit sem" v otevřené konverzaci.
     * Obsah si vyzvedne konverzace toho chatu, až bude připravený.
     */
    fun choose(chatId: Long) {
        current.update { it?.copy(targetChatId = chatId, awaitingConfirmation = false) }
    }

    /**
     * Chat je vybraný zvenčí (Direct Share), ale obsah ještě nikam nejde — čeká na obrazovce
     * „Komu: …", kde uživatel vidí, co posílá, a teprve odtud ho odešle.
     *
     * Od [choose] se liší právě tím: vybraný chat ho z konverzace nesmí vyzvednout
     * ([deliveries]), protože odesílá ta obrazovka sama.
     */
    fun confirm(chatId: Long) {
        current.update { it?.copy(targetChatId = chatId, awaitingConfirmation = true) }
    }

    /**
     * Obrazovka „Komu: …" odešla, aniž by obsah [id] poslala — sdílení se zahodí.
     *
     * **Jen to, které obrazovka ukazovala.** Když mezitím přišlo nové sdílení (a kvůli němu
     * obrazovka odchází — shell ji nahrazuje), bez [id] by její odchod zahodil právě to nové.
     * Zůstává jen to, co už je na cestě do konverzace.
     *
     * Obsah čekající na potvrzení ([confirm]) se zahazuje, i když chat vybraný má — odejít z té
     * obrazovky znamená „nechci to poslat", a bez zahození by se sdílení vložilo do konverzace,
     * kterou uživatel neotevřel.
     */
    fun abandon(id: Long) {
        current.update { content ->
            val keep = content == null || content.id != id ||
                (content.targetChatId != null && !content.awaitingConfirmation)
            content.takeIf { keep }
        }
        persist()
    }

    /**
     * ✕ na čipu: sdílení [id] se zahodí i s kopiemi souborů. Jen to, které čip ukazoval — nové
     * sdílení, které mezitím přišlo, zůstává.
     */
    fun discard(id: Long) {
        val discarded = current.getAndUpdate { content -> content?.takeIf { it.id != id } }
        discarded?.takeIf { it.id == id }?.let(::deleteFiles)
        persist()
    }

    /**
     * Sdílení na čipu po [SHARE_LIFETIME_MS] zmizí i s kopiemi. Při startu se to hlídá samo
     * (obnova), za běhu to volá shell při návratu aplikace do popředí.
     */
    fun dropExpired() {
        val content = current.value ?: return
        if (content.carried && expired(content.createdAt)) discard(content.id)
    }

    /**
     * Obsah pro chat [chatId], jakmile je vybraný a připravený. Kdo ho dostane, musí zavolat
     * [consume] — jinak by ho konverzace po otočení displeje vložila podruhé.
     */
    fun deliveries(chatId: Long): Flow<SharedContent> = current
        .filterNotNull()
        .filter { it.targetChatId == chatId && it.attachments != null && !it.awaitingConfirmation }

    /**
     * Vyřízeno — soubory si převzal ten, kdo je posílá, takže se NEmažou. Porovnává se id:
     * mezi vyzvednutím a vyřízením mohlo přijít nové sdílení a to by se zahodilo nepřečtené.
     *
     * Vrací `true` jen prvnímu: odebírat [deliveries] téhož chatu můžou dvě konverzace naráz
     * (tentýž chat dvakrát v zásobníku) a obsah se má vložit jen jednou.
     */
    fun consume(id: Long): Boolean {
        val before = current.getAndUpdate { it?.takeIf { content -> content.id != id } }
        val consumed = before?.id == id
        if (consumed) persist()
        return consumed
    }

    /** Shell ukázal seznam chatů kvůli sdílení [id]. */
    fun arrivalShown(id: Long) {
        arrived.compareAndSet(id, null)
    }

    private fun expired(createdAt: Long) = now() - createdAt >= SHARE_LIFETIME_MS

    /** Sdílení z minulého běhu procesu, pokud ještě platí. */
    private fun restore(): SharedContent? {
        val stored = store?.load() ?: return null
        val attachments = stored.attachments.map { it.toOutgoing() }
        if (expired(stored.createdAt) || (attachments.isEmpty() && stored.text.isNullOrBlank())) {
            store.deleteFiles(attachments.flatMap { it.files() })
            store.write(null)
            return null
        }
        return SharedContent(
            id = nextId++,
            text = stored.text,
            attachments = attachments,
            failed = stored.failed,
            denied = stored.denied,
            sources = stored.sources,
            carried = true,
            createdAt = stored.createdAt,
        )
    }

    /**
     * Zapíše, co platí TEĎ — stav se čte až při zápisu, takže pozdější zápis vždycky nese
     * poslední stav, i když se změny potkaly ve dvou vláknech (příprava souborů běží na pozadí).
     *
     * Ukládá se jen sdílení na čipu s připravenými soubory. Rozpracovaná příprava se uložit
     * nedá — oprávnění ke zdrojovým URI zabití procesu nepřežije — a sdílení s chatem vybraným
     * předem čeká na obrazovce, kterou obnova stejně zavře.
     */
    private fun persist() {
        val store = store ?: return
        val write = { store.write(current.value?.toStored()) }
        if (scope == null) write() else scope.launch(writer) { write() }
    }

    private fun deleteFiles(content: SharedContent) = deleteFiles(content.attachments.orEmpty())

    private fun deleteFiles(attachments: List<OutgoingAttachment>) {
        val store = store ?: return
        if (attachments.isEmpty()) return
        val paths = attachments.flatMap { it.files() }
        if (scope == null) store.deleteFiles(paths) else scope.launch(writer) { store.deleteFiles(paths) }
    }
}

private fun SharedContent.toStored(): StoredShare? {
    if (!carried) return null
    val ready = attachments ?: return null
    return StoredShare(
        createdAt = createdAt,
        text = text,
        attachments = ready.map { it.toStored() },
        failed = failed,
        denied = denied,
        sources = sources,
    )
}

/** Kam sdílení míří už při příchodu: chat z Direct Share, nebo z iOS rozšíření. */
data class ShareTarget(
    val chatId: Long,
    /** Obsah čeká na obrazovce „Komu: …" ([ShareRequests.confirm]), ne v konverzaci. */
    val confirm: Boolean,
)

data class SharedContent(
    val id: Long,
    /** Sdílený text nebo odkaz; u souborů je to popisek. */
    val text: String?,
    /** `null`, dokud se soubory připravují. */
    val attachments: List<OutgoingAttachment>?,
    /** Kolik souborů se nepodařilo připravit. */
    val failed: Int = 0,
    /** Kolik z [failed] sdílející aplikace nedovolila přečíst (viz [ShareRequests.prepared]). */
    val denied: Int = 0,
    /**
     * Odkud která příloha přišla: cesta v cache → URI od sdílející aplikace. Galerie podle něj
     * pozná sdílenou fotku mezi svými, takže ji po ťuknutí nepřidá podruhé (`EmbeddedMediaPicker`).
     */
    val sources: Map<String, String> = emptyMap(),
    /** Vybraný chat; `null`, dokud se vybírá. */
    val targetChatId: Long? = null,
    /**
     * Chat je vybraný, ale obsah čeká na obrazovce „Komu: …" ([ShareRequests.confirm]), ne
     * v konverzaci. Odesílá ta obrazovka, takže konverzace si ho vyzvednout nesmí.
     */
    val awaitingConfirmation: Boolean = false,
    /**
     * Sdílení bez chatu vybraného předem: nese ho čip v shellu, dokud ho uživatel nevloží do
     * konverzace nebo nezahodí. Zůstává `true` i po [ShareRequests.choose] — než si ho
     * konverzace vyzvedne, čip nemá zmizet.
     */
    val carried: Boolean = false,
    /** Kdy sdílení přišlo (epoch ms) — podle toho vyprší ([SHARE_LIFETIME_MS]). */
    val createdAt: Long = 0,
)
