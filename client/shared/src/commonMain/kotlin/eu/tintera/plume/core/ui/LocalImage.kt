package eu.tintera.plume.core.ui

import okio.Path
import okio.Path.Companion.toPath

/**
 * Model pro Coil (`AsyncImage`, `ImageRequest.data`) ze souboru na disku.
 *
 * **Cesta jde jako `okio.Path`, ne jako `"file://$path"`.** Řetězec Coil rozebírá jako URI a na
 * Windows z `file://C:\Users\…\a.jpg` udělá autoritu `C:` a cestu `\Users\…\a.jpg` — písmeno
 * disku se ztratí, Coil hledá soubor relativně k pracovnímu adresáři a nenajde ho. Na desktopu
 * pod Windows tak nebyl vidět jediný obrázek (fotky, avatary, náhledy příloh), na Androidu,
 * iOS ani macOS se to neprojeví. `Path` mapuje Coil (`PathMapper`) na URI rovnou z částí, bez
 * rozboru, takže cesta projde celá.
 */
fun localImage(path: String): Path = path.toPath()
