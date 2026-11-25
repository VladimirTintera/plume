package eu.tintera.plume.core.update

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Podpis aktualizace desktopu (Ed25519). SHA-256 z API GitHubu chrání jen proti poškození:
 * přichází z téže odpovědi jako odkaz, takže kdo umí přepsat přílohu vydání, přepíše i jeho
 * součet. Podpis dělá release workflow klíčem ze secretu v environmentu `release`
 * (`.github/scripts/sign-desktop-update.sh`) a aplikace ho ověří zabudovaným veřejným klíčem
 * — vydání bez platného podpisu se nenabídne vůbec.
 *
 * - **Podepisuje se text, ne soubor** ([message]): verze, jméno přílohy a SHA-256. Podpis tak
 *   nejde přenést na starší vydání (útočník by jinak vrátil uživatele ke známé chybě) ani na
 *   balíček pro jiný systém. Soubor sám se proti SHA-256 ověřuje při stažení i před instalací.
 * - **Výměna klíče = nové vydání s novým [UPDATE_PUBLIC_KEY]**, podepsané ještě STARÝM klíčem:
 *   nainstalované verze znají jen ten. Ztracený soukromý klíč znamená, že starší verze už
 *   aktualizaci nedostanou a musí se přeinstalovat ručně.
 *
 * Viz „Podpis aktualizací" v `docs/poznatky/desktop.md`.
 */
internal object UpdateSignature {

    /**
     * Text, který workflow podepisuje; musí sedět bajt po bajtu se skriptem
     * `sign-desktop-update.sh` (verze formátu na prvním řádku, za každým řádkem `\n`).
     */
    fun message(tag: String, assetName: String, sha256: String): ByteArray =
        "plume-desktop-update-v1\n$tag\n$assetName\n$sha256\n".encodeToByteArray()

    /**
     * Sedí [signature] (base64 z `<příloha>.sig`) na balíček [assetName] s [sha256] z vydání [tag]?
     * Cokoli nečitelného je `false` — aktualizace se pak nenabídne.
     */
    fun verify(signature: String, tag: String, assetName: String, sha256: String, publicKey: String = UPDATE_PUBLIC_KEY): Boolean {
        val bytes = runCatching { Base64.getDecoder().decode(signature.trim()) }.getOrNull() ?: return false
        if (bytes.size != ED25519_SIGNATURE_SIZE) return false
        return runCatching {
            val key = KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(publicKey)))
            Signature.getInstance("Ed25519").run {
                initVerify(key)
                update(message(tag, assetName, sha256))
                verify(bytes)
            }
        }.getOrDefault(false)
    }

    /**
     * Veřejný klíč aktualizací (SubjectPublicKeyInfo v base64, jak ho vypíše
     * `openssl pkey -pubout -outform DER | base64`). Skript podpisu ho odsud čte (`sed`) a s jiným
     * klíčem podepsat odmítne — tvar řádku proto neměnit.
     */
    const val UPDATE_PUBLIC_KEY = "MCowBQYDK2VwAyEAYlheyu69NGZEIyjEf2tpf1452Q97J55cDGjpRzRs+78="

    private const val ED25519_SIGNATURE_SIZE = 64

    /** Podpis je malý (base64 z 64 bajtů); větší odpověď je chyba, ne podpis. */
    const val SIGNATURE_MAX_BYTES = 1024
}
