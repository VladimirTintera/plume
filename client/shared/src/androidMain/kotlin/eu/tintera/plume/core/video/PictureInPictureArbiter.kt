package eu.tintera.plume.core.video

import android.app.PictureInPictureParams
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import co.touchlab.kermit.Logger
import java.util.WeakHashMap

/**
 * Jeden rozhodčí obrazu v obraze na aktivitu. Okénko i jeho parametry
 * (`setPictureInPictureParams`) má na Androidu AKTIVITA, ne obrazovka — a chtějí ho dva:
 * video v prohlížeči médií a videohovor. Dokud si je každý nastavoval sám, vypnul ten, kdo
 * skončil, automatiku i tomu druhému (zavřený prohlížeč během videohovoru → hovor po odchodu
 * z aplikace do okénka nešel).
 *
 * - **Platí poslední přihlášený, který okénko právě chce** ([Owner.wantsAutoEnter]: hrající
 *   video, hovor s obrazem). Když ho nechce nikdo, automatika je vypnutá; odhlášení nic
 *   nevypíná, jen se znovu rozhodne.
 * - **„Jsem v okénku" dostane jen ten, kvůli komu se do okénka šlo** ([Owner.onActive]) —
 *   v okénku je celá aktivita a kreslit se v něm má jedna věc, ne dvě přes sebe.
 * - Pod Androidem 12 automatika v parametrech není; do okénka se jde z `onUserLeaveHint`.
 */
internal class PictureInPictureArbiter private constructor(private val activity: ComponentActivity) {
    private val logger = Logger.withTag("PictureInPicture")

    /**
     * Kdo okénko chce a s jakými parametry. Parametry jsou kompletní, včetně automatiky.
     * [onActive]: okénko je kvůli němu; [onOther]: aplikace je v okénku kvůli někomu jinému —
     * pak se nemá kreslit vůbec (pruh hovoru by ležel přes video).
     */
    class Owner(val onActive: (Boolean) -> Unit, val onOther: (Boolean) -> Unit = {}) {
        var params: PictureInPictureParams? = null
        var wantsAutoEnter = false
    }

    private val owners = mutableListOf<Owner>()

    /** Kvůli komu se do okénka šlo; `null` mimo okénko. */
    private var entered: Owner? = null

    init {
        activity.addOnPictureInPictureModeChangedListener(Consumer<PictureInPictureModeChangedInfo> { info ->
            entered = if (info.isInPictureInPictureMode) entered ?: current() ?: owners.lastOrNull() else null
            notifyOwners()
        })
        activity.addOnUserLeaveHintListener {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) current()?.let(::enter)
        }
    }

    fun register(owner: Owner) {
        owners += owner
        notifyOwners()
        apply()
    }

    fun unregister(owner: Owner) {
        owners -= owner
        if (entered === owner) entered = null
        apply()
    }

    /** Vlastník změnil parametry nebo to, jestli okénko chce. */
    fun update() = apply()

    /** Ručně do okénka (tlačítko v liště videa). */
    fun enter(owner: Owner) {
        val params = owner.params ?: return
        entered = owner
        runCatching { activity.enterPictureInPictureMode(params) }
            .onFailure {
                entered = null
                logger.w(it) { "Obraz v obraze se nepodařilo otevřít." }
            }
    }

    private fun notifyOwners() {
        val inWindow = activity.isInPictureInPictureMode
        owners.forEach { owner ->
            owner.onActive(inWindow && owner === entered)
            owner.onOther(inWindow && owner !== entered)
        }
    }

    /** Poslední přihlášený, který okénko teď chce. */
    private fun current(): Owner? = owners.lastOrNull { it.wantsAutoEnter && it.params != null }

    private fun apply() {
        val params = current()?.params
            ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PictureInPictureParams.Builder().setAutoEnterEnabled(false).build() else null
            ?: return
        runCatching { activity.setPictureInPictureParams(params) }
            .onFailure { logger.w(it) { "Obraz v obraze se nepodařilo nastavit." } }
    }

    companion object {
        private val arbiters = WeakHashMap<ComponentActivity, PictureInPictureArbiter>()

        fun of(activity: ComponentActivity): PictureInPictureArbiter =
            arbiters.getOrPut(activity) { PictureInPictureArbiter(activity) }
    }
}
