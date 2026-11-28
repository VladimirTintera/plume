package eu.tintera.plume.core.video

import eu.tintera.plume.core.telegram.VideoQuality

/*
 * Výběr kvality videa je v `core`, ne ve `shared`, aby ho měla i televize (`:client:tvApp` vidí
 * jen `core`). Balíček zůstal, telefon importy neměnil. Zjistit dekodér je věc platformy:
 * telefon `videoDecoder` ve `shared`, Android (telefon i televize) nad `mediaCodecVideoDecoder`.
 */

/**
 * Čím zařízení video v dané kvalitě dekóduje (`videoDecoder` ve `shared`,
 * `mediaCodecVideoDecoder` na Androidu); pořadí je pořadí přednosti.
 */
enum class VideoDecoder { None, Software, Hardware }

/**
 * Co si uživatel v přehrávači vybral. Volba platí pro všechna videa v otevřeném prohlížeči
 * (drží ji prohlížeč, jako ztlumení), proto je to POPIS kvality, ne konkrétní soubor: jiné video
 * má jiné soubory i jiné rozměry.
 */
sealed interface VideoQualityChoice {
    /** Podle sítě a nastavení automatického stahování ([pickVideoQuality]). */
    data object Auto : VideoQualityChoice

    /** Soubor, jak ho poslal odesílatel. */
    data object Original : VideoQualityChoice

    /** Přepočítaná kvalita s kratší stranou [lines] („720p"); chybí-li, nejbližší nižší. */
    data class Lines(val lines: Int) : VideoQualityChoice
}

/**
 * Kvality, které má smysl nabídnout: původní soubor a přepočítané, které zařízení umí
 * dekódovat ([decoder]), od nejlepší.
 *
 * **Server posílá každou velikost dvakrát, v AV1 a v h264** (ověřeno na kanálu: 720p AV1
 * 720 MB, 720p h264 975 MB — h264 bývá větší i než původní soubor). V nabídce by tatáž
 * velikost byla dvakrát, zůstane proto jedna: napřed ta s hardwarovým dekodérem (softwarové
 * AV1 hodinu videa táhne z baterie), při shodě menší soubor.
 *
 * Původní soubor se nefiltruje: kodek o něm TDLib neříká a přehrával se odjakživa.
 */
fun videoQualities(
    original: VideoQuality,
    alternatives: List<VideoQuality>,
    decoder: (VideoQuality) -> VideoDecoder,
): List<VideoQuality> {
    val converted = alternatives
        .filter { it.width > 0 && it.height > 0 }
        .map { it to decoder(it) }
        .filter { (_, how) -> how != VideoDecoder.None }
        .groupBy { (quality, _) -> quality.lines }
        .map { (_, same) ->
            same.minWith(
                compareByDescending<Pair<VideoQuality, VideoDecoder>> { (_, how) -> how }
                    .thenBy { (quality, _) -> quality.sizeBytes.takeIf { it > 0 } ?: Long.MAX_VALUE },
            ).first
        }
        .sortedByDescending { it.lines }
    return listOf(original) + converted
}

/**
 * Který soubor přehrát.
 *
 * - **Automaticky** se bere nejlepší kvalita do [autoMaxLines] (strop podle úrovně automatického
 *   stahování na právě používané síti, `AutoDownloadLimits.maxVideoLines`). Když se do stropu
 *   nevejde nic, nejmenší, jaká je.
 * - **Bez stropu (Wi-Fi) přepočítaná ve STEJNÉM rozlišení jako původní**, jinak původní. Server
 *   ji dělá pro streamování a rozbíhá se hned, kdežto původní soubor je, jak ho odesílatel
 *   nahrál: 655MB video s indexem na konci a zvukem daleko od obrazu se rozbíhalo přes minutu.
 *   Rozdíl v obraze je při stejném rozlišení nepostřehnutelný; nižší rozlišení se na Wi-Fi
 *   nebere, to už by vidět bylo. Původní zůstává v nabídce. **Jen kde přepočítaná jde streamovat**
 *   ([convertedStreams]; iOS přes HLS): jinak by se na ni čekalo celou.
 * - **Už stažený původní soubor vyhrává** v automatice vždycky: nic nestojí a je nejlepší.
 * - **Při shodě rozměrů přepočítaná před původní**: na stropu se šetří data a přepočítaná bývá
 *   menší (původní bývá z telefonu s vysokým datovým tokem).
 * - **Ruční volba** výšky bere tu výšku, chybí-li, nejbližší nižší, a není-li žádná nižší,
 *   nejmenší. Tak volba „480p" zůstane smysluplná i u videa, které má jen 720p a 360p.
 *
 * [qualities] je výstup [videoQualities]: první je původní soubor.
 */
fun pickVideoQuality(
    qualities: List<VideoQuality>,
    choice: VideoQualityChoice,
    autoMaxLines: Int,
    originalDownloaded: Boolean,
    convertedStreams: Boolean = true,
): VideoQuality {
    val original = qualities.first { it.isOriginal }
    return when (choice) {
        VideoQualityChoice.Original -> original
        VideoQualityChoice.Auto -> when {
            originalDownloaded -> original
            autoMaxLines == Int.MAX_VALUE -> qualities
                .firstOrNull { convertedStreams && !it.isOriginal && it.lines == original.lines }
                ?: original
            else -> bestUpTo(qualities, autoMaxLines)
        }
        is VideoQualityChoice.Lines -> qualities
            .filter { !it.isOriginal }
            .takeIf { it.isNotEmpty() }
            ?.let { converted -> bestUpTo(converted, choice.lines) }
            ?: original
    }
}

/** Nejlepší do [maxLines], přepočítaná před původní; nic pod stropem = nejmenší. */
private fun bestUpTo(qualities: List<VideoQuality>, maxLines: Int): VideoQuality {
    // Přepočítaná vyhrává shodu: řadí se jako o kousek lepší než původní téže velikosti.
    val rank = compareBy<VideoQuality> { it.lines }.thenBy { !it.isOriginal }
    return qualities.filter { it.lines <= maxLines }.maxWithOrNull(rank)
        ?: qualities.minWith(compareBy<VideoQuality> { it.lines }.thenBy { it.isOriginal })
}
