package eu.tintera.plume

import androidx.core.content.FileProvider


/**
 * Provider pro soubory z chatu, které otevírá jiná aplikace.
 *
 * **Musí to být vlastní třída, ne druhý `<provider>` nad `androidx.core.content.FileProvider`.**
 * Providery se v procesu drží podle jména třídy, takže dvě deklarace téže třídy sdílejí jednu
 * instanci — a ta zná jen autoritu, se kterou vznikla. Požadavek na `…documents` pak spadl
 * uvnitř naší aplikace na `SecurityException: The authority eu.tintera.plume.documents does not
 * match the one of the contentProvider: eu.tintera.plume.notificationimages` a volající
 * (Fotky) ukázal jen „media not found".
 */
class DocumentFileProvider : FileProvider()
