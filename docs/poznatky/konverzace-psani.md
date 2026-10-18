# Konverzace: psaní a odesílání

Poznatky k jedné oblasti aplikace. Obecná pravidla projektu a rejstřík ostatních souborů jsou
v [CLAUDE.md](../../CLAUDE.md); poučení z opravy v téhle oblasti patří sem.

## Enter z fyzické klávesnice

Enter z fyzické klávesnice (Fold, tablet) zprávu odešle, **Shift+Enter** zalomí řádek — jako na
počítači (`onPreviewKeyEvent` na poli v `MessageInput`). Enter ze softwarové klávesnice dál
zalomí řádek; odeslání je tam tlačítko a klávesa Odeslat (`ImeAction.Send`).

- **Fyzickou klávesnici pozná `isFromHardwareKeyboard`**, ne kód klávesy: Gboard ve víceřádkovém
  poli posílá Enter taky jako klávesu. Android: bez `FLAG_SOFT_KEYBOARD` a ne z
  `VIRTUAL_KEYBOARD` (odtud posílá i `adb shell input`); iOS posílá jako klávesy jen fyzickou
  klávesnici.
- **Podmínky odeslání jsou tytéž jako u tlačítka** (text, přílohy, odebíraný popisek; ne během
  nahrávání a přípravy přílohy). **Prázdné pole Enter nepošle, ale ani nezalomí** — prázdný
  řádek navíc nikdo nechce.
- **Volba „Enter odesílá" v Nastavení zatím není**: pro fyzickou klávesnici je to obvyklé chování
  a softwarové se netýká. Kdyby o ni někdo stál, patří sem.
- **Ověřeno na emulátoru** přes `uinput` (viz `navigace.md`, „Klávesové zkratky"): Enter odeslal,
  Shift+Enter zalomil, prázdné pole nic, Enter z `input keyevent` zalomil.

## Vestavěná galerie (Android)

Sponka → „Fotka nebo video" otevře na Androidu 14 se SDK Extensions 15 **galerii v sheetu nad
vstupním polem** (`androidx.photopicker`, hranice `core/ui/EmbeddedMediaPicker.kt`), ne systémovou
obrazovku výběru. Je při vybírání vidět, do jakého chatu se posílá, a vybrané fotky naskakují
rovnou do pruhu nad polem. Kde vestavěný výběr není (starší Android, iOS), zůstává systémový —
rozhoduje `embeddedMediaPickerAvailable()`.

- **Soukromí zůstává systémové.** Galerii kreslí proces výběru fotek do `SurfaceView`
  (`SurfaceControlViewHost`), aplikace dostane jen vybraná URI a nepotřebuje oprávnění ke
  knihovně fotek — tedy totéž co u systémového výběru, jen bez odchodu z chatu.
- **Volat se smí až od API 34 s extension 15**, ale knihovna má `minSdk 23`, takže překlad nic
  nehlídá a pod tou hranicí by to spadlo za běhu. Podmínka je proto i uvnitř `actual`, ne jen
  u volajícího.
- **Příprava vybrané přílohy běží v rozsahu OBRAZOVKY, ne panelu** (`EmbeddedMediaSelection`,
  `rememberEmbeddedMediaSelection` drží `CoroutineScope`). Kopie velkého videa do cache trvá
  vteřiny a „Hotovo" panel zavře dřív; dokud běžela v `rememberCoroutineScope` panelu, zavření ji
  zrušilo a video se nad polem nikdy neobjevilo — bez hlášky, protože zrušení není chyba.
  U fotky to vidět nebylo, ta je hotová dřív, než člověk ťukne. Během přípravy je nad polem
  „Připravuji soubory…" a odeslání čeká (poslalo by dávku bez videa); odznačení položky, která
  se ještě kopíruje, hotovou kopii zahodí. **Ověřuje se velkým videem a rychlým „Hotovo"** —
  malé video (screenrecord emulátoru má desítky kB) chybu neukáže. Velké video se vyrobí bez
  kodéru: k malému MP4 se připojí box `free` o stovkách MB (délka + `free` + nuly), MediaStore
  i přehrávač ho berou.
- **Přílohy chodí PRŮBĚŽNĚ, odznačení taky** (`onPicked` / `onUnpicked`), ne až na konci výběru.
  Odznačená položka se hlásí jako URI od pickeru, ale konverzace zná přílohy podle CESTY do
  cache — proto si `actual` drží mapu URI → cesta a `ConversationViewModel.removeAttachments`
  bere cesty.
- **Sbalený picker NEJDE POSOUVAT, takže sheet v konverzaci ho má vždycky rozbalený**
  (`EmbeddedMediaPicker(expanded = true)` → `state.isExpanded`). Sbalený stav je náhled pro
  panel, který hostitel teprve táhne nahoru: bez záložek Photos / Collections a se zakázaným
  scrollováním — v logu pickeru `embeddedCollapsedModeScrollingEnabled=false`, a veřejné API ho
  zapnout neumí. Náš sheet je celý rozbalený, takže sbalený picker v něm byl mřížka, se kterou
  nešlo pohnout (hlášeno z Pixelu 11 Pro Foldu jako „zaseklý sheet"). Hodnota nastavená před
  startem session se promítne do `setPickerLaunchedInExpandedState`, pozdější změna jde přes
  `notifyPhotoPickerExpanded` — obojí funguje. Sbalený stav má smysl jen tam, kde panel jde
  vytáhnout (Direct Share, viz níž).
- **Obnovené zaškrtnutí picker při startu session ohlásí ZNOVU** (`onUriPermissionGranted`
  s položkami z `initialMediaSelection`). Ty přílohy už nad polem jsou, takže by se přidaly
  podruhé — po jediném výběru a znovuotevření galerie bylo v dávce „3 z 3". Co už je
  připravené (klíč v mapě URI → cesta), se proto přeskočí.
- **Dřívější tvrzení, že rozbalený picker přejde do VLASTNÍ AKTIVITY, se nepotvrdilo.** Na
  emulátoru s API 37.2 (profil Pixel 10 Pro Fold, rozložený i složený) zůstává `mCurrentFocus`
  na `MainActivity` a picker kreslí dál do našeho sheetu — i po výběru, odznačení a opětovném
  otevření. Kdyby se to na nějakém zařízení přesto dělo, pozná se to v logu pickeru podle
  `embeddedPickerLaunchedInExpandedState=true` a `mCurrentFocus` na
  `com.google.android.photopicker`; náhradní cesta je nastavit `isExpanded` až PO startu
  session (pak jde přes `notifyPhotoPickerExpanded`).
- **Co picker dostal za konfiguraci, je v logu** (`adb logcat | grep PhotopickerConfiguration`):
  celé `EmbeddedPhotoPickerFeatureInfo` i odvozená konfigurace. Tudy se příčina našla — vzhled
  (chybějící záložky) napovídal, ale rozhodl řádek `embeddedCollapsedModeScrollingEnabled`.
- **Picker se zapíná až po DOSEDNUTÍ sheetu.** `SurfaceView` má `setZOrderOnTop(true)` a s posunem
  vrstvy se nehýbe — tatáž vlastnost, kvůli které video v prohlížeči jede do `TextureView`
  a ne přes `PlayerView`. Během vjezdu sheetu by stál na místě a doskočil až na konci.
- **Výška je podíl okna se stropem**, ne pevné číslo: nad sheetem musí zůstat kus konverzace,
  jinak není poznat, kam se posílá — a to je celý smysl vestavěného výběru.
- **Co chat nepřijme, se nenabízí** (`setMimeTypes` podle `ContentAccess`), stejné pravidlo jako
  u sponky. Omezení se posílá, jen když se fotky a videa liší; jinak zůstává výchozí obojí.
- **Motiv se předává výslovně** (`setThemeNightMode`): picker jinak jede podle systému, ale
  uživatel si v Nastavení může vynutit světlý nebo tmavý a galerie v opačném by vedle konverzace
  svítila. Akcent se nastaví jen v mezích, které API bere (jas 0,05–0,9, plné krytí).
- **Co už je vybrané, si pamatuje OBRAZOVKA, ne panel** (`EmbeddedMediaSelection`,
  `rememberEmbeddedMediaSelection` v konverzaci). Panel se zavřením zahodí, takže bez toho začíná
  každé otevření s prázdným výběrem — a tatáž fotka se dá vybrat podruhé, v albu je pak dvakrát.
- **Zaškrtnutí se obnovuje PRŮNIKEM s přílohami nad polem**, ne jen z paměti. To je celá
  synchronizace: co uživatel mezitím odebral křížkem nebo odeslal, z výběru vypadne samo a panel
  to nemusí nijak sledovat. Průnik se počítá JEDNOU při otevření — během vybírání příloh přibývá
  a přepočítávat by znamenalo měnit klíč stavu, tedy restartovat session uprostřed výběru.
- **Past: `rememberEmbeddedPhotoPickerState` je saveable, takže `initialMediaSelection` samo
  nestačí.** Při dalším otevření se obnoví ULOŽENÝ stav i s výběrem, který už neplatí — odebraná
  fotka zůstala v mřížce zaškrtnutá (ověřeno na emulátoru). Množina obnovených položek se proto
  předává i jako `inputs`, na kterých stav resetuje; mezi otevřeními se mění jen tehdy, když se
  změnily přílohy, takže resetů to nepřidá.
- **Knihovna je alpha** (`1.0.0-alpha02`) a API má `@ExperimentalPhotoPickerComposeApi`. Proto
  je za `expect`/`actual`: bez ní i bez systémové podpory funguje výběr příloh dál.
- **Ověřeno na emulátoru** (API 36, extension 20): galerie v sheetu nad konverzací, výběr → fotka
  nad polem, odznačení → fotka zmizí, zavření gestem zpět, zavření a znovuotevření → fotka je
  pořád zaškrtnutá, po odeslání → zaškrtnutí zmizí.
- **Fallback ověřený na Androidu 14 (SDK Extensions 7) a 13**: sponka otevře systémový výběr
  (`PhotoPickerActivity`), vybrané fotky naskočí nad pole, a Direct Share se ukáže bez galerie.
  Na Androidu 16 (Extensions 17) je vestavěná galerie v sheetu i v Direct Share jako na 17.
  **Android 14 sám nestačí** — rozhoduje verze SDK Extensions, a obraz emulátoru API 34
  (`google_apis_playstore` r14) má jen 7; skutečný telefon s Androidem 14 ji mívá vyšší díky
  aktualizacím Play systému, takže tam galerie být může.

## Posílání příloh a alb

Sponka ve vstupním poli nabídne Fotka nebo video / Soubor (výběr víc najednou, nejvýš 10). Vybrané přílohy
čekají v kartě nad polem, text pole je jejich popisek; odeslání pošle jednu jako zprávu, víc jako
album. Bublina ukazuje průběh nahrávání.

- **Přílohy se neposílají hned po výběru**, ale čekají ve stavu (`ConversationState.attachments`).
  Jinak by k nim nešlo napsat popisek ani některou odebrat. Fotky jsou v M3
  `HorizontalMultiBrowseCarousel` s křížkem, soubory řádky s názvem (`StagedAttachmentsStrip`).
  Při úpravě zprávy se schovají a odeslání úpravy je nepošle.
- **Album = `sendMessageAlbum`** (2–10), jedna příloha jde přes `sendMessage`. **Popisek patří první
  položce** — tak ho Telegram u alba ukazuje. Entity popisku se dohledávají jako u textu.
- **Fotky s videi a soubory se nemíchají** (TDLib skládá dokumenty do alba jen s dokumenty). Výběr jiného
  druhu připravené přílohy **nahradí**, stejný druh se přidá; nad strop se ořízne a ohlásí
  (`stageAttachments`, testy v `StagedAttachmentsTest`).
- **Připravuje se souběžně, pořadí drží `awaitAll`.** Selhání jednoho souboru ostatní nezahodí.
  iOS PHPicker má `selection = Ordered`, aby album odešlo v pořadí ťuknutí.
- **TDLib u příloh koncept sám NEMAŽE** (`clearDraft` je jen u textu), takže se po odeslání maže
  výslovně — jinak by se popisek vrátil jako koncept. Při selhání se přílohy vrátí nad pole.
- **`rememberCarouselState { size }` počet přečte znovu při každé kompozici**, takže odebrání
  a přidání fotky carousel nerozbije.

- **TDLib posílá podle CESTY** (`InputFileLocal`). Systémové URI (`content://`, iOS URL z výběru)
  neotevře, takže platforma vybraný soubor nejdřív zkopíruje do cache aplikace
  (`rememberAttachmentPicker`, `expect`/`actual`). Každá příloha má vlastní podadresář — dva
  soubory stejného jména se nepřepíšou a jméno zůstane, protože ho TDLib pošle.
- **Výběr bez oprávnění**: Android Photo Picker + `OpenDocument`, iOS PHPicker + UIDocumentPicker
  (`asCopy`). Oba běží mimo aplikaci a vydají jen vybraný soubor.
- **Fotka se zmenšuje na 2560 px a ukládá jako JPEG.** TDLib přijme fotku nejvýš 10 MB a do
  10 000 px; z foťáku bývá větší. **Orientace se musí použít, ne zahodit**: přepsaný JPEG EXIF nemá,
  takže bez otočení pixelů by fotka odešla bokem. Android: `ImageDecoder` (9+) orientaci použije
  sám, pro starší `ExifInterface`. iOS: kreslení přes `UIGraphicsImageRenderer` ji použije taky
  a HEIC se tím zároveň převede na JPEG.
- **Soubor jde beze změny s `disableContentTypeDetection = true`** — uživatel posílal „soubor",
  TDLib by jinak z videa udělal přehrávatelnou zprávu.
- **Hudba vybraná jako soubor odejde jako skladba** (`OutgoingAttachment.Audio`,
  `InputMessageAudio`): v konverzaci s přehrávačem, názvem a interpretem. Údaje čte platforma ze
  značek souboru — Android `MediaMetadataRetriever` (podle typu obsahu `audio/*`), iOS
  `AVURLAsset.commonMetadata` (podle přípony přes `UTType`); obal alba jako JPEG do 320 px.
  Soubor, který se jako skladba přečíst nedá, odejde jako soubor. Desktop zůstává u souboru (JVM
  MP3 neumí). Totéž platí pro hudbu sdílenou z jiných aplikací.
  - **Skladba patří ke SOUBORŮM**, ne k fotkám: vybírá se sponkou „Soubor" a přidává se k nim.
    Jenže do alba ji TDLib pustí jen se skladbami („Documents and audio files can be only grouped
    in an album with messages of the same type"), takže **smíšený výběr odejde celý jako soubory**
    (`sendableTogether`) — a řádek nad polem to ukazuje stejně (ikona souboru místo noty).
  - **Chat bez práva na hudbu (`canSendAudios`) dostane skladbu jako soubor**, smí-li soubory
    (`allowedByContent`). `ContentAccess.audios` proto přibyl; dřív tam hudba nebyla, protože se
    neposílala.
  - **Pás příloh nad polem rozhodoval „fotka, nebo soubor" podle `!is Document`** — skladba se
    tak nakreslila jako fotka v carouselu („Odebrat fotku"). Podmínka je teď kladná (fotka nebo
    video), aby další druh přílohy nespadl do fotek potichu.
  - **Ověřeno na emulátoru** testovací MP3 složenou v Pythonu (tiché rámce MPEG, ID3 se jménem,
    interpretem a obalem): řádek „Plume testovaci skladba – Plume", bublina s přehrávačem, délkou
    0:07 a obalem. Na Macu ffmpeg ani lame není; tichý rámec MPEG-1 Layer III s nulovými daty je
    platný a dekodér ho přehraje jako ticho.
- **iOS delegáty výběrů se drží silnou referencí** (`remember`). UIKit je drží jen slabě
  a bez toho by je uklidil garbage collector dřív, než uživatel něco vybere — výběr by tiše nic
  neudělal. Soubor z UIDocumentPickeru je v dočasném adresáři, který systém po delegátovi maže,
  proto se hned kopíruje.
- **Průběh nahrávání** chodí týmiž `fileUpdates` jako stahování (`remote.uploadedSize`),
  `FileRepository.uploadProgress`. Kreslí se jen u zprávy ve stavu `Sending`: vlnitý kruh
  (`CircularWavyProgressIndicator`) na tmavém kolečku přes fotku, u souboru místo náhledu. Dokud
  TDLib nahrávání neohlásí, točí se neurčitě. Průběh je i na každé dlaždici alba — dlaždice
  nahrávají každá sama za sebe.
- **Zrušení = smazání rozepsané zprávy** (`deleteMessages`, křížek uprostřed kolečka průběhu).
  TDLib tím zastaví i nahrávání a pošle `UpdateDeleteMessages`, takže zpráva zmizí jako každá
  smazaná. U alba se ruší jen ťuknutá položka, zbytek odejde.
- **Citace se spotřebuje** jako u textové zprávy; let ze vstupního pole se nekoná.
- **Ťuknutí na přílohu nad polem otevře náhled přes celou obrazovku** (`StagedAttachmentPreview`):
  karta mezi lištami (zpět a smazat nahoře, režim odeslání a Hotovo dole), ťuknutí lišty schová,
  štípnutí a dvojité ťuknutí přibližují, video se přehraje, tah dolů náhled zavře. **Stránky
  jsou tytéž jako v prohlížeči médií a na obrazovce „Komu: …"** (`ZoomableMediaPage`,
  `VideoPage`, krmené souborem na disku). Dřív tu byl třetí, vlastní zoom přes
  `detectTransformGestures` (bez přiblížení kolem prstů, bez dvojitého ťuknutí, posun bez mezí)
  a video se jen ukázalo náhledem; rozcházel se s prohlížečem při každém doladění. Spoiler je
  zakrytý jen s lištami, na celé obrazovce se dá zkontrolovat, co pod ním je.
- **Při úpravě zprávy sponka není.**
- **Video se nepřekódovává.** Server video nerozebírá, takže rozměry, délku a náhled (první
  snímek, JPEG do 320 px — víc TDLib nepřijme) čte platforma: Android `MediaMetadataRetriever`,
  iOS `AVURLAsset` + `AVAssetImageGenerator`. **Rozměry po otočení** — video na výšku je uložené
  naležato s příznakem; bez prohození by bublina měla poměr stran na šířku. Náhled je zároveň to,
  co se kreslí v carouselu nad polem (Coil video neotevře).
- **iOS video přebaluje do MP4** (`AVAssetExportPresetPassthrough`, bez překódování) s indexem na
  začátku (`shouldOptimizeForNetworkUse`) a posílá `supportsStreaming = true`. Android kopíruje
  soubor z foťáku, kde index bývá na konci, takže `supportsStreaming = false`. Soubor
  z `loadFileRepresentation` platí jen uvnitř callbacku — kopíruje se hned v něm.

## Úprava fotky před odesláním

V náhledu připravené fotky (`StagedAttachmentPreview`) je tužka „Upravit fotku": editor
(`PhotoMarkupEditor`) s perem v šesti barvách, žlutým zvýrazňovačem, začerněním a ořezem
s otočením. Úprava (`PhotoMarkup`) se do fotky vykreslí v plném rozlišení na platformě
(`PhotoMarkupRenderer`: Android `Canvas` a `Matrix`, iOS `UIBezierPath` a transformace
kontextu), stejně jako ořez profilové fotky (`SquarePhoto`).

- **Začernění, ne rozmazání.** Rozmazané číslo nebo text jde při troše snahy přečíst, plná černá
  ne. Je to výchozí nástroj — kvůli němu se editor otevírá nejčastěji.
- **Tahy jsou v PODÍLECH rozměrů fotky, šířka podílem kratší strany** (`MarkupStroke`,
  `MarkupTool.widthFraction`). Fotka je na displeji zmenšená a vykresluje se v plném rozlišení;
  v pixelech displeje by tah v souboru vyšel jinde a jinak silný. Ověřeno porovnáním náhledu
  v editoru a odeslané fotky.
- **Jeden tah = jedna cesta**, ne úsečky po kouskách: průsvitný zvýrazňovač by se na spojích
  překrýval a ztmavoval. Ťuknutí je tečka velká jako šířka čáry (Android `drawPoint`, iOS úsečka
  nulové délky s kulatým koncem).
- **Upravená fotka je NOVÝ soubor, originál zůstává** (`ConversationState.photoEdits`: současná
  cesta → originál, úprava a jeho rozměry). Další úprava začíná z originálu s dosavadními tahy,
  otočením i výřezem, takže jde vzít zpátky cokoli z minula; z vykreslené kopie by nešlo.
  Prázdná úprava vrátí originál. Rozměry originálu se drží zvlášť — upravená příloha je má po
  ořezu a otočení jiné.
- **Tahy jsou v souřadnicích ORIGINÁLU, otočení a výřez se přes ně jen promítají** (čisté
  funkce v `PhotoMarkupGeometry.kt`, testy `PhotoMarkupGeometryTest`). Při vykreslení platí pevné
  pořadí tahy → otočení → ořez; tahy tak drží na obsahu fotky, ať se otočí nebo ořízne jakkoli,
  a výřez jde kdykoli zvětšit zpátky. Dotyk se do originálu převádí obráceně (`resultToOriginal`).
- **Při kreslení je vidět VÝSLEDEK** (otočený a oříznutý), při ořezu celá otočená fotka s rámečkem.
  Kreslí se do toho, co odejde. Výřez se měří v otočené fotce, protože ji tak uživatel při ořezu
  vidí; „Otočit" otočí s fotkou i výřez (`CropBox.rotated`), ať ukazuje na týž obsah.
- **Rámeček výřezu má plochu přes celé okolí fotky**, ne jen přes ni. Úchyty leží na okraji fotky
  a polovina jejich dotykové plochy byla venku: tah těsně vedle rohu se do rámečku vůbec nedostal
  a výřez se nehnul (zjištěno na emulátoru stopou dotyku). Fotka má proto v editoru okraj 24 dp.
- **Galerie zná fotku pod PŮVODNÍ cestou** (`galleryPath`). Vestavěná galerie si pamatuje, co
  z ní vzešlo, podle cesty v cache (viz „Vestavěná galerie (Android)"); s novou cestou by upravenou
  fotku při dalším otevření nenašla mezi vybranými a odznačení by nic neodebralo.
- **Během vykreslování se neodesílá** (`markupBusy`): příloha má do té doby cestu k NEUPRAVENÉ
  fotce a odešlo by i to, co měl uživatel začerněné. Tlačítko odeslání je vypnuté s „Připravuji
  soubory…", `send` to hlídá i pro Enter z klávesnice, náhled ukazuje `LoadingIndicator`.
- **Řada barev drží místo i u ostatních nástrojů** (průhledná, bez dotyku a bez sémantiky).
  Vysouvaná řada zmenšovala plochu fotky a ta při přepnutí nástroje poskočila.
- **Fotka se v editoru nepřibližuje** — dva prsty by se praly s kreslením jedním; zpět editor
  zavře bez uložení. Náhled i editor jsou ve vlastní funkci (`StagedAttachmentPreviewHost`), ne
  v `ConversationScreen` (viz „Obrazovka konverzace je rozdělená do částí" v CLAUDE.md).
- **Na emulátoru Fold neodpovídají souřadnice `input motionevent` přesně snímku obrazovky**
  (`screencap -d`): tah leží zhruba o 20 px výš, než kam se podle snímku mířilo. Chyba aplikace to
  není — `settings put system pointer_location 1` ukáže stopu dotyku přesně ve středu tahu.
- **Ověřeno na emulátoru** (Fold, česky): začernění, zvýrazňovač a pero, „Hotovo" (náhled
  ukáže vykreslenou fotku), opětovná úprava z originálu se „Vzít zpět" tahu z minula, odeslání
  Notifikátorovi (odešla upravená fotka), zpět bez uložení. Ořez: otočení (tahy se otočily
  s fotkou), zmenšení výřezu tahem za roh, kreslení do otočeného a oříznutého výsledku, vykreslená
  fotka shodná s editorem, při dalším otevření obnovené otočení i výřez. **iOS jen přeložené**
  (transformace kontextu tam nikdo neviděl).
- **Táž úprava je i na obrazovce „Komu: …"** (sdílení do aplikace): tužka vlevo nahoře na kartě
  fotky otevře týž editor (`PhotoMarkupOverlay`), logika výměny fotky je společná
  (`PhotoEdits.kt`, testy `PhotoEditsTest`) — dvě kopie by se rozešly při první opravě.
  - **Vestavěnou galerii editor nepřekryje.** Je to cizí okno (`SurfaceView`) nad vším, takže
    ležela přes spodek editoru; dokud je editor otevřený, galerie se nezobrazuje vůbec (jako na
    celé obrazovce) a po zavření si výběr obnoví sama.
  - **`sources` (cesta → URI sdílené fotky) zůstávají pod PŮVODNÍ cestou**: galerie dostává cesty
    přes `galleryPath`, takže se sdílená fotka po úpravě nepřidá podruhé.
  - **Sdílení z `adb` (`am start -a SEND --eu …STREAM content://media/…`) neprojde** — shell
    nesmí propůjčit právo číst cizí fotku a obrazovka ohlásí „Některé sdílené soubory se
    nepodařilo připravit". Ověřuje se sdílením z Google Fotek (`am start -a VIEW -d
    content://media/… -p com.google.android.apps.photos`, pak „Share" → Plume).
  - Ověřeno na emulátoru: sdílení z Fotek do Uložených zpráv, tah perem, karta ukázala upravenou
    fotku, galerie se po zavření editoru vrátila, odeslala se upravená fotka.

## Přetažení z jiné aplikace (Android)

Na Foldu nebo tabletu v rozdělené obrazovce jde obrázek, GIF i soubor přetáhnout z jiné aplikace
rovnou na vstupní pole (`KeyboardContentReceiver`, `Modifier.dragAndDropTarget`). Obrázek a GIF
jdou stejnou přípravou jako vložení ze schránky (`prepare` → nad polem jako fotka z výběru, GIF
odchází hned), jiný soubor jako příloha (`KeyboardContent.File` → `addAttachments`). Pole nad
sebou drží přetahovaný obsah tónem `primary` (ne rámečkem, který by měnil rozměr).

- **Oprávnění ke čtení cizích URI dá systém až na vyžádání** (`requestDragAndDropPermissions`)
  a jen do `release()` — to proto čeká, až se zkopírují všechny přetažené položky.
- **Přijímá se podle práv chatu**: obrázky `photos`, GIFy `stickers`, soubory `documents`, při
  úpravě zprávy nic (jako vložení ze schránky).
- **Přetažený TEXT si pole bere samo** — proto se nepřijímají typy, které nese text
  (`text/plain`, `text/html`, `text/uri-list`, intent). Celou rodinu `text/…` vyřadit nejde:
  soubory `.xml` a `.csv` jsou `text/xml` a `text/csv` a po prvním pokusu se kvůli tomu
  nepřijaly.
- **Typ položky se bere z `contentResolver.getType`**, ne jen z popisu klipu — víc položek může
  mít různé typy. Víc položek naráz (víc fotek) přibude nad polem všechny.
- **Android na pole, desktop na celou konverzaci** (`ContentDropArea`, viz „Vložení ze schránky
  a přetažení souborů" v `docs/poznatky/desktop.md`). Na iOS by to chtělo `UIDropInteraction`
  nad polem; zatím nic.
- **Ověření na emulátoru (Fold rozložený):** rozdělená obrazovka jde přes přehled aplikací
  (`input keyevent 187`, ikona dlaždice → Split screen → druhá aplikace); `am start
  --windowingMode 6` ani příznak `FLAG_ACTIVITY_LAUNCH_ADJACENT` ji nevyrobily. V aplikaci
  Soubory se táhne až VYBRANÁ položka (první podržení jen vybere), pak `adb shell input
  draganddrop <x1> <y1> <x2> <y2> 3000`. Že drop dorazil, ukáže logcat `Reporting drop result:
  true`. Ověřeno: PNG (nad polem jako fotka s popiskem) i `u.xml` (příloha souboru). Displej
  rozloženého Foldu má id `4619827259835644672` (`screencap -d`).

## Práva psát

Kde se do chatu psát nesmí, není vstupní pole (`WriteAccess`, pravidlo `writeAccess`
v `core/telegram/WriteAccess.kt`, testy `WriteAccessTest`). Pole, ze kterého zpráva skončí
chybou až po odeslání, je horší než žádné. Kanál bez práva publikovat má místo něj tlačítko
Ztlumit / Zapnout zvuk, omezená skupina a chat, ve kterém uživatel není, větu proč.

- **Rozhoduje vlastní postavení ve skupině, ne typ chatu.** V kanálu publikuje vlastník
  a správce s `canPostMessages`; ve skupině výchozí práva (`Chat.permissions.canSendBasicMessages`),
  u omezeného člena jeho vlastní (`ChatMemberStatusRestricted.permissions`) — ta výchozí přebíjejí
  oběma směry. Správce a vlastník píšou do skupiny vždycky.
- **Postavení je lokální dotaz** (`getSupergroup` / `getBasicGroup`). Když ho zjistit nejde,
  psaní se NEBLOKUJE: pole navíc skončí nanejvýš chybou, chybějící pole by uživatele od chatu
  odřízlo.
- **Práva se mění za běhu** (`accessUpdates`: `UpdateChatPermissions`, `UpdateSupergroup`,
  `UpdateBasicGroup`). Update skupiny chodí i kvůli počtu členů, takže se přístup čte znovu
  a dál jde jen změna (`distinctUntilChanged`). Tudy se vrátí i pole po „Přidat se".
- **Převedená skupina** (`BasicGroup.isActive == false`) žije dál jako supergroup; do staré se
  nepíše, takže se bere jako „nejste v chatu".
- **Swipe na odpověď se v chatu jen pro čtení nekoná** (`ConversationState.canWrite`); nabídka
  nad zprávou se řídí `canBeReplied` od serveru jako dřív.
- **Video kolečka mají vlastní právo** (`ContentAccess.videoNotes` z `canSendVideoNotes`), bez
  výchozí hodnoty jako ankety. Kde smí jen hlasovky, tlačítko nepřepíná; kde nesmí ani jedno,
  je z něj neaktivní odeslání.
- **Druhy obsahu mají vlastní pravidlo** (`ContentAccess`, `contentAccess` vedle `writeAccess`,
  testy `WriteAccessTest`). Skupina smí povolit text a zakázat fotky; co se poslat nesmí, se
  nenabízí — sponka schová položku, mikrofon ustoupí odeslání, výběr zůstane na emoji.
  **Sponka ale nezmizí nikdy**, dokud jde psát: poloha je v TDLib „základní zpráva"
  (`canSendBasicMessages` jako text), takže v ní zůstane aspoň „Poloha". **Obě pravidla se musí shodovat v tom, koho omezení míjejí:** správce
  a vlastník omezení nemají, členovi platí výchozí práva skupiny, omezenému členovi jeho vlastní.
- **Neznámé postavení NEOMEZUJE nic**, stejně jako u psaní. Chybějící sponka odřízne uživatele
  od chatu víc než chyba po odeslání, která přijde jednou za čas.
- **Filtrovat se musí i to, co vydá systémový výběr** (`allowedByContent`, testy
  `StagedAttachmentsTest`). Výběr fotek vydá fotky i videa jedním tahem, takže ve skupině jen
  pro fotky se mezi vybranými objeví video — a bez filtru by skončilo chybou po odeslání.
- **Rozhoduje, JAKO CO příloha odejde, ne čím je.** Režim „Odeslat jako soubor" posílá fotku
  i video přes `InputMessageDocument` (`toInputContent`), takže se řídí právem na SOUBORY.
  Kdo to plete, nabídne přílohu, kterou server odmítne.
- **UI nestačí, pojistka patří i do ViewModelu.** Výběr samolepek se bez práva neotevře, jenže
  sada jde otevřít ťuknutím na samolepku v historii a poslat se dá z ní. Hláška je lepší než
  chyba od serveru.
- **Omezená skupina se bez druhého účtu nevyrobí.** Vlastník omezení nemá, takže vlastní
  testovací skupina pravidlo neprověří — ověřuje se podstrčeným `ContentAccess` v `chatAccess()`,
  jako vzhled karty aktualizace. Ověřeno na emulátoru: „jen fotky" nechá sponku s jedinou
  položkou, smajlík místo samolepky a odeslání místo mikrofonu; „nic" sponku schovávalo celou
  (dnes v ní zůstane „Poloha", ověřené to s podstrčeným stavem znovu není).
- **Se dvěma účty ověřeno** (12. 10. 2026, Pixel 7 vlastník, Pixel 5 člen): výchozí práva
  „jen text" → sponka jen s Polohou a Kontaktem, výběr jen emoji, místo mikrofonu odeslání;
  omezený člen (bez fotek, koleček a samolepek) → sponka bez fotek a GIFů, mikrofon nahrává
  hlasovku a na kolečko se nepřepne. **Video kolečka a ankety předtím v nastavení práv chyběly**
  — vypnuté hlasovky nechávaly kolečko, protože je to v TDLib vlastní právo; dnes je mají
  výchozí práva skupiny i omezení člena (`GroupPermissions.sendVideoNotes`, `sendPolls`).

## Psaní jménem kanálu nebo skupiny

Kde TDLib nabízí víc odesílatelů (uživatel spravuje kanál, nebo je ve skupině anonymní
správce), stojí úplně vlevo u vstupního pole avatar toho, kým se právě píše; ťuknutí otevře
sheet „Psát jako" s vlastním účtem, kanály a skupinou jako anonymním správcem
(`SendAsButton` / `SendAsSheet` v `SendAsPicker.kt`, logika `ConversationSendAs`, doména
`MessageSenders.kt`, testy `ConversationSendAsTest`).

- **Kdo je vybraný, nese chat** (`Chat.messageSenderId`, živě `UpdateChatMessageSender` →
  `ConversationRepository.messageSender`). `null` = tady odesílatele měnit nejde (soukromý chat,
  kanál, skupina bez téhle možnosti) — pak se nabídka (`getChatAvailableMessageSenders`) ani
  nečte. Vybraný, kterého nabídka nezná (změna z jiného zařízení), nabídku přečte znovu; při
  otevření sheetu se čte taky (kanál mohl přibýt).
- **Výběr s jedinou volbou se neukazuje** (`messageSenderChoice`), stejně jako vybraný, který
  v nabídce chybí: avatar u pole by tvrdil něco jiného, než kým zpráva odejde.
- **Volba se ukáže hned** (`setChatMessageSender`), server ji potvrdí updatem; když selže,
  vrátí se předchozí a přijde hláška (`send_as_failed`, ne text od TDLib).
- **Prémiová volba** (`ChatMessageSender.needsPremium`) se zamyká, jen když účet Premium
  NEMÁ (`MessageSenderOption.locked`, `getMe().isPremium` — stejně jako u reakcí a efektů).
  V sheetu je vidět s ikonou zámku a větou „Chce Telegram Premium", vybrat nejde; ViewModel
  ji odmítne i sám (pojistka) a ohlásí proč.
- **Avatar bez `IconButtonDefaults.shapes()`** — výjimka jako fotka účtu v pilulce: obsahem je
  avatar ve vlastním tvaru (kruh člověka, mnohoúhelník kanálu) a měnící se vlnka by kolem něj
  trčela. Při úpravě zprávy se neukazuje (upravená zpráva odesílatele nemění).
- **`MessageInput` dostal jediný parametr, slot** (`senderSlot`), ne data a callbacky — výběr
  i jeho sheet žijí v `SendAsPicker.kt` a pole jen drží místo. `ConversationScreen` se nemění.
- **Test části: `advanceUntilIdle` nepohne `backgroundScope`** (čeká jen na práci v popředí),
  takže trvalý odběr v testu stojí a stav zůstane prázdný. Odběr updatů v testu běží
  v `backgroundScope` (jinak by `runTest` čekal věčně) a posouvá se `runCurrent`.
- **Ověřeno jen částečně.** Testovací účet …527 žádný kanál nespravuje a ve vlastní skupině
  není anonymní — avatar se tam správně NEUKÁŽE (ověřeno na emulátoru, „Plume test
  moderace"; jestli proto, že TDLib `messageSenderId` nedá, nebo že nabídka má jedinou volbu,
  se nerozlišovalo). S podstrčeným stavem (dočasná úprava, necommitnutá)
  se avatar u pole ukázal (iniciály, vlevo před tlačítkem příkazů). **Sheet, výběr na serveru,
  zamčená volba a skutečný anonymní správce nebo kanál na zařízení ověřené NEJSOU** — chce
  to účet, který kanál spravuje, nebo zapnuté „Vystupovat anonymně" ve vlastní skupině.
  Desktop a iOS jen přeložené.

## „Píše…" v konverzaci

Činnost protějšku (ve skupině kohokoli) se kreslí **v seznamu zpráv tam, kam zpráva naskočí**:
tvář toho, kdo píše, a skákající trojtečka (`TypingItem`), jako v Google Zprávách. Protějšek
naopak vidí, že píšeme my.

- **Bez bubliny.** Je to informace o dění v chatu, ne zpráva — patří mezi předěly a servisní
  oznámení, které se kreslí holé na ploše. Bublina by tvrdila, že už něco přišlo.
- **V hlavičce není.** Tam zůstává dostupnost; „píše…" ji dřív vytlačovalo a od místa, kde
  zpráva přijde, bylo daleko.
- **Činnost se bere ze `ChatRepository`, ne z vlastního odběru.** Seznam chatů ji už sleduje
  i s odpočtem zhasnutí a odfiltrováním vlastní činnosti; druhá kopie téže logiky by se s ní
  rozešla. `ChatRepository.typing(chatId)` nese i KDO (`ChatTyping.senderId`) — `ChatSummary`
  drží jen druh, seznamu chatů stačí. Tvář dohledá `ConversationRepository.sender` z keše
  odesílatelů; v soukromém chatu je to tvář chatu.
- **Ve skupině lícuje s bublinami** (tvář 32 dp ve slotu 40 dp jako u zpráv), v soukromém chatu je
  tvář jen malá ikonka (24 dp) — zprávy tam tvář nemají. Nezjištěný odesílatel ve skupině nechá
  prázdné místo, ne tvář skupiny: ta by tvrdila, že píše skupina.
- **Stojí pod nejnovější zprávou a nad reklamou**, jen když okno sahá k přítomnosti (`reachedEnd`)
  a ne v kanálu. Klíč je jeden (`TYPING_KEY`), výměna píšícího je jen překreslení.
- **Při objevení se seznam ukotví dolů** (`requestScrollToItem(0)`), když je uživatel dole —
  seznam drží pozici podle první viditelné položky (nejnovější zprávy), takže by se indikátor
  vložil pod spodní hranu. Kdo čte historii, toho nevytrhne.
- **Trojtečka patří jen psaní.** Ostatní činnosti mají vlastní ikonku (`activityIcon`, testy
  `ConversationItemsTest`): mikrofon, kamera (natáčí i posílá video), obrázek, dokument,
  samolepka, a u „sleduje" oko. Dřív skákaly tečky vedle tváře u všeho, takže protějšek, který
  nahrával hlasovku nebo jen koukal na náš efekt animovaného emoji, vypadal, že píše. Slova
  zůstávají — ikonka napoví, text upřesní. Trojtečku má dál i neznámá činnost („něco chystá…"),
  pro tu žádný obrázek není.
- **Ikonka dýchá průhledností, oko mrká** (`BreathingIcon`, `BlinkingEye`, `eyeClosure`), obojí
  čtené až v `graphicsLayer` jako tečky. Neposkakuje, aby se nepletla s psaním. Ikonka má 16 dp
  jako řádek popisku a slot nejmíň výšku teček s výskokem, takže výměna činnosti řádkem nepohne.
- **Tečky skáčou jen v části periody** (`dotLift`), zbytek stojí — bez pauzy by vlnily neklidně.
- **„Sleduje" nese emoji** (`ChatActivity.WatchingAnimation(emoji)` z
  `chatActionWatchingAnimations.emoji`, text „sleduje 🎉…" v seznamu chatů i v konverzaci).
  Proto je `ChatActivity` sealed rozhraní, ne výčet, a `label()` je composable, která vrací
  hotový text. Prázdné emoji spadne na „sleduje animaci…".
- **Vlastní „píšu" se opakuje nejvýš jednou za 5 s** (`TYPING_REPEAT`). Server činnost zhasíná
  asi po šesti vteřinách; posílat ji na každý úhoz by byl dotaz na znak.
- **Vymazání textu psaní výslovně ukončí** (`ChatActionCancel`), odeslání ne — to ukončí server
  sám. Po odeslání se jen zapomene čas posledního ohlášení, aby další zpráva ohlásila psaní hned.
- **Hlásí se jen ruční úpravy textu.** Vyprázdnění pole po odeslání jde mimo `onValueChange`,
  takže by se o něm `onDraftChanged` stejně nedozvěděl.
- **Chyba se jen loguje.** V kanálu bez práva psát `sendChatAction` selže; snackbar kvůli
  „píše…" by uživatele jen obtěžoval.

## Naplánované zprávy a odeslání bez zvuku

Podržení odesílacího tlačítka nabídne **Odeslat bez zvuku**, **Naplánovat zprávu…** a v soukromém
chatu **Odeslat, až bude online** (`SendOptions`, `MessageSchedule`; co se nabídne, je čistá
funkce `sendMenuOffer`, testy `SendMenuTest`). Naplánované zprávy má chat v seznamu za hodinami
u pole (`ScheduledMessagesSheet`): odeslat hned, změnit čas, smazat.

- **Naplánovaná zpráva do historie NEPATŘÍ — a tři cesty ji tam vmíchají**, když se nehlídají:
  `UpdateNewMessage` (naplánovaná přijde jako nová zpráva), `UpdateMessageSendSucceeded`
  (potvrzení odeslání na server — `withSentMessage` ji do seznamu vloží) a úprava zprávy
  (přeplánování je `editMessageSchedulingState`, tedy `UpdateMessageEdited` — `merge` ji vloží
  taky). Repository proto filtruje `schedulingState != null` na všech třech. Až zpráva v čase
  odejde, přijde znovu jako běžná, s novým id.
- **Že chat naplánované zprávy má, hlásí TDLib** (`Chat.hasScheduledMessages`,
  `UpdateChatHasScheduledMessages`) — i když zpráva odejde nebo se naplánuje z jiného zařízení.
  Podle toho jsou hodiny u pole; seznam se čte až při otevření (`getChatScheduledMessages`)
  a po každé akci znovu. Prázdný se zavře sám.
- **„Odeslat hned" je přeplánování na `null`**, ne nové odeslání — zpráva odejde tak, jak byla
  naplánovaná, i s odpovědí a přílohami.
- **Tlačítko odeslání `onLongClick` nemá** (material3 1.12 ho u `IconButton` nenabízí), takže
  dlouhý stisk hlídá `longPressInstead` v `Initial` a po něm **spotřebuje zbytek gesta** —
  jinak by puštění prstu zprávu zároveň odeslalo.
- **„Až bude online" jen se ZNÁMOU dostupností** (`Presence.Offline`). TDLib: „the user's
  online status must be known". Bot dostupnost nemá, skrytou („naposledy nedávno") server
  odmítne, a online protějšek ji nepotřebuje.
- **Ve vlákně komentářů se neplánuje** (`hasScheduledMessages` tam vrací `false`): naplánované
  patří celé skupině a v komentářích by se míchaly cizí.
- **Čas: napřed den, pak hodina** (`FutureDayDialog`, `TimeOfDayDialog` — vytažené z formuláře
  ankety, sdílí je oba). Dnešek je předvybraný a čas za hodinu od teď, protože se nejčastěji
  plánuje „na později dnes". **Nejdřív za minutu, nejpozději za rok** (`scheduleValid`); čas
  vybraný na minuty by jinak mohl minout dřív, než odejde. Neplatný čas nechá text v poli.
- **Naplánovaná zpráva neletí z pole** a do seznamu se nepřidává; místo toho snackbar
  „Zpráva je naplánovaná" — jinak by po odeslání nebylo vidět nic.
- **Past s časovým výběrem přes `adb`**: po ťuknutí na hodinu se ciferník přepíná na minuty
  animací a ťuknutí během ní se ztratí — mezi ťuknutími pauza aspoň vteřinu.
- **Ověřeno na emulátoru v chatu s botem**: nabídka nad tlačítkem (bez „až bude online" — bot),
  naplánování s ikonou hodin a snackbarem, seznam („zítra 18:55"), odeslat hned (bot zprávu
  dostal, v historii jednou), **automatické odeslání v čase** (17:55 — bot dostal, hodiny
  zmizely), změna času („pondělí 18:56"), smazání, odmítnutí času, který už minul, a odeslání
  bez zvuku (bot zprávu dostal; jestli bez zvuku, Bot API neprozradí). **„Až bude online"
  ověřené se dvěma účty** (12. 10. 2026): protějšek offline (Plume i oficiální Telegram na
  jeho telefonu zastavené, v hlavičce „naposledy…"), zpráva v seznamu naplánovaných jako „When
  online"; po spuštění Plume u protějšku doručená do 10 s a ze seznamu zmizela. **Neověřeno**:
  naplánované přílohy, iOS (jen přeložené).
- **Hlasovka a anketa jdou naplánovat taky** — podržením odesílacího tlačítka během nahrávání
  a podržením „Odeslat" ve formuláři ankety (bez zvuku, naplánovat, u hlasovky i „až bude
  online"; efekt jen u textu, výběr efektu posílá obsah pole). Naplánovaná hlasovka se do
  konverzace nepřidává, jen ohlásí (`sendMedia(scheduled = true)`), jako text.
- **Nahrávání je po dobu nabídky a výběru času POZASTAVENÉ** (`VoiceRecorder.pause`), jinak by
  se do hlasovky nahrálo šustění při vybírání. Zavření bez volby nahrávání obnoví — ale **položka
  `ExpressiveMenu` volá NEJDŘÍV `onDismiss` a teprve pak svou akci**, takže obnovení v `onDismiss`
  proběhlo i při volbě „Naplánovat" (hlasovka vyšla o výběr času delší). O obnovení se proto
  rozhoduje až po zavření nabídky (`LaunchedEffect(sendMenuOpen)`), kdy už akce proběhla.
- **Anketa naplánovaná jako odpověď odpověď nad polem spotřebuje**, stejně jako odeslaná —
  ohlásí to formulář (viz „Ankety a kvízy", odpověď anketou). Ověřeno na emulátoru: odpověď
  zmizela a „Odeslat hned" z naplánovaných poslalo anketu s `reply_to_message` (Bot API).
- **Ověřeno na emulátoru v chatu s botem**: nabídka nad nahrávanou hlasovkou, zastavený čas
  během nabídky, naplánování a „Odeslat hned" (bot dostal hlasovku dlouhou jen o nahraný čas),
  zavření nabídky a nahrávání dál, bez zvuku; anketa naplánovaná z formuláře v seznamu
  naplánovaných. iOS jen přeložené.
- **Samolepka a GIF mají volby odeslání u PODRŽENÉ položky**, ne na podržení: samolepka
  v náhledu (`StickerPreviewDialog`), GIF v rohu buňky (`GifCell`, `SendOptionsButton`); nabídka
  je společná (`SendOptionsMenu`, `SendMenuChoice`). Podržení samolepce otevře náhled a druhé gesto
  nad ní by se s tím pralo. Tlačítko nabídne totéž co odesílací tlačítko bez efektu
  (bez zvuku, naplánovat, až bude online). Nabízí ho jen výběr v konverzaci — sada otevřená
  z historie a formulář ankety `sendMenu` nepředávají. Ověřeno na emulátoru v chatu s botem:
  naplánovaná samolepka v seznamu naplánovaných, „Odeslat hned" (bot ji dostal) a bez zvuku;
  naplánovaný GIF a „Odeslat hned" (bot dostal animaci).
- **Podržet jde každý GIF, ne jen uložený.** Dřív podržení znamenalo jen „odebrat z uložených",
  takže nalezený GIF podržet nešel. Teď podržený GIF (klíč položky, uložený bývá i mezi
  populárními) ukáže tlačítko voleb a jen uložený navíc křížek.
- **Pozor při ověřování přes `adb`: ťuknutí do mřížky GIFů GIF rovnou pošle.** Zpráva zavřela
  sheet a další ťuknutí skriptu šla do seznamu chatů. Pole hledání se proto hledá dumpem až
  po otevření sheetu, ne podle souřadnic z předchozího snímku.
- **`StickerPickerSheet` je `internal`** — s parametrem interního `SendMenuOffer` by veřejná
  funkce neprošla překladem (`EXPOSED_PARAMETER_TYPE`).

## Úprava zprávy

„Upravit" v nabídce nad zprávou přepne vstupní pole do úpravy: karta místo citace ukazuje původní
text, tlačítko je fajfka a potvrzení volá `editMessageText`.

- **Nenabízí se u zpráv, jejichž obsah pole neunese** (`composerEdit`, `composedFormatsOf`).
  Ruční formátování (tučné, kurzíva, podtržení, přeškrtnutí, spoiler, kód i blok kódu s jazykem,
  citace i sbalitelná) i odkaz s vlastním popiskem pole nese (viz „Formátování při psaní");
  odkazy psané v textu, `@jméno`, hashtagy, příkazy a čas v médiu se při uložení dohledají znovu.
  Zmínka jménem a datum by úprava tiše smazala — u nich „Upravit" není. Vlastní emoji pole nese,
  ale jen tam, kde by šlo i poslat (viz „Vlastní emoji v poli"). Automatický odkaz se
  pozná tak, že jeho text v adrese opravdu stojí.
- **Popisek přílohy je JINÉ volání** (`editMessageCaption` vedle `editMessageText`), takže
  pravidlo neříká jen „jde to", ale i CO se upravuje (`ComposerEdit.Text` / `Caption`). Jedno
  volání za druhé zaměnit nejde: úprava textu by popisek nezměnila a naopak.
- **Popisek jde i přidat a odebrat.** „Upravit" se proto u přílohy nabízí i bez popisku (jinak
  by ho k fotce nešlo doplnit) a prázdné pole je platná úprava, která popisek smaže — odesílací
  tlačítko proto v téhle jediné situaci zůstává aktivní i s prázdným polem. U textu zprávy ne:
  prázdnou zprávu server odmítne.
- **Samolepka ani animované emoji popisek nést nemůžou** (`carriesCaption`, `when` bez `else`),
  takže se u nich „Upravit" nenabízí — server by úpravu odmítl.
- **Hlavička nad polem musí říct, že se upravuje POPISEK.** Příloha v konverzaci zůstane, ať se
  v poli píše cokoli; bez toho by nebylo poznat, že se nepíše nová zpráva. U přílohy bez popisku
  se původní text nekreslí vůbec — prázdný řádek vypadá jako chyba vykreslení.
- **Upravená příloha si kartu drží i s prázdným popiskem** (`hasCaption = text.isNotBlank() ||
  isEdited` v `MediaMessageItem`): v pruhu pod snímkem zůstane samotné „upraveno". Dokud popisek
  nešlo odebrat, byl ten stav nedosažitelný; teď je vědomý — jinak by se u fotky nikde nedozvěděl,
  že se zpráva změnila, a čas úpravy je až v „Informace".
- **Rozepsaný koncept se po dobu úpravy odloží a pak vrátí.** Úprava je vsuvka; kdo si uprostřed
  psaní všimne překlepu, nechce přijít o rozepsanou zprávu. Při přepnutí z jedné úpravy do druhé
  se odložený koncept nepřepisuje.
- **Během úpravy se neukládá koncept ani „píše…"** (`onDraftChanged` se vrátí hned). V poli je
  text staré zprávy; koncept by přepsal ten odložený.
- **Z úpravy se odchází hned, ne po odpovědi serveru.** Výsledek dorazí `editedMessages` jako
  každá jiná úprava. Když selže, úprava se vrátí do pole i s textem.
- **Beze změny se nic neposílá** — server by odpověděl chybou.
- **Text do pole jde přes `InputFill`**, společně s obnoveným konceptem. Koncept má
  `onlyIfEmpty` (nesmí přepsat, co uživatel mezitím napsal), úprava pole přepisuje vždycky.
- **Výběr jde otevřít i při úpravě, ale jen s emoji** (`stickersAvailable = false`): přepínač
  se schová, ikona je vždycky smajlík. Samolepka do textu upravované zprávy nepatří. Uložená
  volba části výběru se tím nemění. Emoji vložené při úpravě neukládá koncept ani „píše…" —
  `onDraftChanged` se při úpravě vrací hned.
- **Přílohu jde při úpravě vyměnit** (`editMessageMedia`, tlačítko se sponkou v hlavičce úpravy).
  Nová příloha se v hlavičce ukáže („Nová fotka", „Nový soubor: …") a vymění se až uložením,
  i s popiskem z pole; šipka zpět ji zahodí a úprava popisku běží dál.
  - **Nabízí se jen tam, kde to server dovolí** (`MessageProperties.canEditMedia` →
    `MessageActions.canEditMedia`, čte se s nabídkou nad zprávou) a jen u fotky, videa, souboru,
    skladby a GIFu (`replaceableMedia`). Mizející zprávu server vyměnit nedovolí.
  - **Fotka a video se mění jen fotkou nebo videem, soubor souborem** (`ReplaceMedia.accepts`):
    v albu server dovolí právě záměnu fotky za video, a sponka podle druhu otevře správný výběr.
    Co nesedí nebo co chat nepřijme, se neuloží a ohlásí.
  - **Nová příloha je změna sama o sobě**: uložení s nezměněným popiskem ji pošle, kdežto bez ní
    by se beze změny nic neposlalo.
  - Ověřeno na emulátoru: fotka vyměněná za jinou („upraveno" u zprávy), u skladby sponka
    otevřela výběr souborů.
  - **Fotka a video se vybírají z vestavěné galerie v sheetu**, kde je (viz „Vestavěná galerie
    (Android)"), jinde ze systémového výběru jako dřív. Galerie je v režimu jedné položky
    (`EmbeddedMediaPicker(single = true)` → `setMaxSelectionLimit(1)`) a **sheet se po výběru
    zavře sám** — není co dál vybírat a „Hotovo" by byl krok navíc. Odznačení vrátí původní
    přílohu (`keepEditMedia`).
  - **Výměna má VLASTNÍ paměť výběru** (`rememberEmbeddedMediaSelection` v `ConversationDialogs`),
    ne tu od příloh nad polem: se společnou by v galerii byly zaškrtnuté přílohy rozepsané
    zprávy a vybraná náhrada by se tvářila jako příloha. Zaškrtnutí se obnovuje průnikem
    s aktuální náhradou (`editReplacement`), takže po uložení nebo zrušení úpravy zmizí samo.
  - Ověřeno na emulátoru (API 37): Upravit → sponka → galerie v sheetu nad úpravou, ťuknutí
    na fotku zavřelo sheet a v hlavičce stálo „Nová fotka", po uložení byla ve zprávě nová
    fotka s „upraveno". Soubor a skladba jdou dál systémovým výběrem.
  - **GIF se mění GIFem z výběru GIFů** (`ReplaceMedia.Gif` → sheet výběru jen s GIFy,
    `modes = listOf(Gifs)` jako ve formuláři ankety). Náhrada proto není jen soubor z telefonu:
    `MediaReplacement` je buď `File` (nahraje se), nebo `Gif` — ten na serveru už je a do
    `editMessageMedia` jde jen jeho id (`InputFileId`), stejně jako při odeslání z výběru.
    Soubor z telefonu GIF nenahradí (`ReplaceMedia.Gif.accepts` = `false`) a GIF jde jen tam,
    kde chat přijme „ostatní zprávy" (`ContentAccess.stickers`).
  - Ověřeno na emulátoru: GIF poslaný z výběru → Upravit → sponka → výběr GIFů, ťuknutí na jiný
    → „Nový GIF" v hlavičce, po uložení nový GIF s „upraveno". **GIF poslaný přes inline bota
    (`via @gif`) ověřený není** — jestli u něj server výměnu dovolí, rozhoduje `canEditMedia`.
- **Po „Upravit" se pole samo nefokusuje.** Že se něco stalo, ukazuje karta s původním textem
  a fajfka místo odeslání; klávesnici si uživatel vyvolá ťuknutím do pole. Dřív si pole fokus
  a klávesnici vyžádalo samo (uživatel si to nepřál).

## Formátování při psaní

Dokud je v poli označený text, vyjede pod ním lišta s ikonami: Tučné, Kurzíva, Podtržené,
Přeškrtnuté, Odkaz, Spoiler, Kód, Citace a Bez formátování (`FormattingBar`); použitý formát má
tlačítko zapnuté. Odejde
se zprávou, uloží se s konceptem a vrátí se do pole při úpravě zprávy. Model a pravidla jsou čisté
funkce v `core/telegram/TextFormatting.kt` (testy `TextFormattingTest`).

- **`TextFieldValue` formátování NEUNESE.** `BasicTextField(TextFieldValue)` sice bere
  `AnnotatedString`, ale `EditingBuffer` z Compose po každé úpravě vrací
  `AnnotatedString(toString())` — styly zmizí s prvním úhozem (ověřeno ve zdrojích ui-text 1.12).
  Rozsahy proto vede `ComposerDraft` zvlášť a pole je jen kreslí (`FormattingTransformation`,
  `OffsetMapping.Identity`).
- **Rozsahy se posouvají z ROZDÍLU textu** (`afterEdit`): společný začátek a konec, mezi nimi
  jeden nahrazený úsek. Nejednoznačný rozdíl („a" za „a") rozhodne kurzor po úpravě. Na konec
  rozsahu se píše dál ve formátu, na začátek ne; přepsaný formátovaný výběr formát drží.
- **Každá změna textu jde přes `ComposerDraft.update`** — obrazovka s ním pracuje jako s
  `TextFieldValue` přes delegát (`var draft by composer`), takže emoji z výběru, zmínka, návrh
  odpovědi i vyprázdnění po odeslání rozsahy posunou samy. Nové místo, které do pole píše, na
  formátování myslet nemusí; kdyby psalo mimo delegát, rozsahy by ujely.
- **Compose 1.12 má vlastní sledované styly** (`TextFieldBuffer.addStyle` → `TrackedRange`,
  `TextFieldState.textStyles`), ale jen nad `TextFieldState`, experimentálně a s významem
  schovaným ve `SpanStyle`. Přechod pole na `TextFieldState` by znamenal přepsat desítky míst
  kolem konceptu; vlastní model je menší a testovatelný bez Compose.
- **Bez výběru lištu otevře ikona „Aa" v kartě pole** (jen když je otevřená klávesnice; se
  schováním klávesnice se lišta zavře) a formát platí pro další psaní od kurzoru — „zapnout
  tučné a psát dál".
  Stav je `PendingFormats` v `ComposerDraft` (pravidla `toggledPending`, `typingAt`, `typed`,
  testy `TextFormattingTest`).
  - **Čekající formát je jednorázový** a použije se jen na text napsaný přesně na kurzor. Dál
    pokračuje sám: text psaný na konec rozsahu do něj patří (`afterEdit`), takže napsané tučné
    slovo pokračuje tučně a text napsaný s vypnutým tučným za ním zůstane bez něj. Nic se tedy
    nemusí hlídat při každém úhozu.
  - **Vypnutí uprostřed tučného slova ho rozdělí** — napsaný kus z rozsahu vypadne (`removed`)
    a další psaní už za ním pokračuje netučně.
  - **Přesun kurzoru čekající formát zahodí**; patří místu, kde se přepnul. Oprava slova
    klávesnicí nebo vložení jinam ho nepoužije (`insertedAtPending` bere jen čisté vložení
    na kurzor).
  - **Tlačítko ukazuje, jak se bude psát** (`typingAt`): zapnuté i u kurzoru na konci tučného
    slova, kde by se psalo tučně i bez ťuknutí. „Bez formátování" vypne všechno, co by pokračovalo.
  - **Citace bez výběru jde na řádek s kurzorem**, ne na další psaní — je to blok přes řádky.
    Jen na prázdném řádku čeká na psaní jako ostatní. **Odkaz bez výběru zašedne**: potřebuje
    popisek.
  - **Smajlík (výběr emoji, samolepek a GIFů) je vpravo v kartě VŽDYCKY, „Aa" si místo
    vyměňuje se sponkou vlevo** (`ComposerStartSlot`). Dřív sdílelo „Aa" místo se smajlíkem
    (s klávesnicí „Aa", bez ní smajlík) a uprostřed psaní, kdy se emoji hledají nejvíc, nešel
    výběr otevřít vůbec — nejdřív se musela schovat klávesnice. Přílohu naopak člověk přidává
    skoro vždycky na začátku, se zavřenou klávesnicí. Dokud stály „Aa" a smajlík vedle sebe,
    ukusovaly textu 96 dp a „Aa" se s fokusem objevovalo DO ŠÍŘKY — rozepsaný víceřádkový text
    se přeskládal při každém ťuknutí do pole. Výměna ikon v místě stejné velikosti šířku pole
    nemění nikdy.
    - **„Aa" naskočí až s otevřenou klávesnicí A textem v poli.** Samotná klávesnice nestačí:
      kdo otevře pole, aby přiložil fotku s popiskem, chce sponku. Zapnout tučné ještě před
      prvním písmenem jde tahem (níž).
    - **Svislý tah po levé ikoně je přepíná** jako buben: nahoru přijede další zdola, dolů
      shora, s `tick()`. Ruční volba platí, dokud se automatické pravidlo samo nezmění. Čtečce
      jde přepnutí jako vlastní akce („Ukázat formátování" / „Ukázat přílohy").
    - **Bez sponky (úprava zprávy, chat bez příloh) je „Aa" vlevo trvale** — naskakovat
      s klávesnicí by znamenalo ubrat poli šířku. Ťuknutí na „Aa" bez klávesnice ji otevře
      i s lištou (`focusRequester` pole + `keyboard.show()`).
    - **Ověřeno na emulátoru (Pixel 9, česky)** v Uložených zprávách: s klávesnicí a prázdným
      polem sponka, po prvním písmenu „Aa" (text v poli se nepohne), smajlík s otevřenou
      klávesnicí ji zavře a otevře výběr i s rozepsaným textem, tah nahoru přepne s `tick()`
      (`constant=26` v `dumpsys vibrator_manager`), tahem přepnuté „Aa" bez klávesnice ji otevře
      i s lištou, úprava popisku má „Aa" vlevo trvale. Na telefonu (jak se tah cítí pod prstem)
      ověřené není.
    - **Rozhoduje viditelná klávesnice, ne fokus** (`typing` = fokus a nenulová výška
      `WindowInsets.ime`, přes `derivedStateOf`). Po schování klávesnice gestem zpět drží pole
      fokus dál. Výška klávesnice funguje i na iOS (`isImeVisible` je jen na Androidu).
    - **S hardwarovou klávesnicí „Aa" samo nenaskočí** (softwarová se neukáže). Formátovat jde dál
      označením textu, jen „zapnout tučné a psát dál" tam chybí.
    - **Od tří řádků stojí sponka a „Aa" pod sebou** (`fieldHeight` ≥ 96 dp: dvě tlačítka 48 dp
      s překryvem 4 dp a 4 dp pod nimi; tři řádky 16/22 mají s odsazením pole 98 dp, dva 76).
      Napřed to bylo zamítnuté, jenže tehdy stály „Aa" a smajlík VEDLE sebe a složením by se
      pole rozšířilo a text přeskládal. Teď sdílejí jeden sloupec, takže šířka zůstává.
      - **Ikona, která byla vidět, zůstane dole u posledního řádku a druhá zpoza ní VYJEDE
        nahoru** (při zkrácení zajede zpátky). Ořízne se na hraně mezi nimi, takže je poznat,
        že ikony leží na sobě a u kratšího textu se dají přepínat tahem (nápad uživatele).
        Ve složeném stavu tah nic nepřepíná.
      - **Místo nesmí nahlásit víc výšky, než má pole** (`layout` s limitem `fieldHeight − 4 dp`).
        Mizející horní ikona si jinak drží místo, dokud nedoběhne: po smazání na dva řádky
        zůstala karta o 20 dp vyšší a na konci skočila (série snímků). Co se nevejde, kreslí
        se přes horní okraj — jen ta mizející ikona.
      - Ověřeno na emulátoru zpomalenými animacemi (20×, přechod vyvolaný jedním znakem — delší
        `input text` zalomí řádek dřív, než se stihne první snímek): vysunutí, zasunutí, karta
        bez skoku, horní sponka otevře nabídku příloh. Jak pohyb působí uprostřed psaní, se
        posoudí na telefonu.
- **Lišta, ne položky v kontextové nabídce výběru** (tak to bylo napřed). Ikony u vlastních
  položek nabídka výběru na telefonu NEZOBRAZÍ: na Androidu ji kreslí `ActionMode`
  (`AndroidTextContextMenuToolbarProvider`) a `leadingIcon` z `TextContextMenuItem` zahodí —
  ikony kreslí jen rozbalovací nabídka pro myš; na iOS ji kreslí systém (`UIEditMenu`), ať
  `item` bere composable ikonu, nebo ne. Položky navíc skončily v přetečení (⋮) a nešlo z nich
  poznat, co už na výběru je.
- **Lišta je POD textem, ne nad ním.** Systémová lišta výběru (Vyjmout, Kopírovat…) se kreslí
  nad výběrem a lištu nad polem přikryla — ověřeno na emulátoru, vidět zbyla jen ikona citace.
- **Zapnuté tlačítko je `primary`**, ne `secondaryContainer` jako u vybraného chatu v seznamu: karta
  pole je přitónovaná do `primaryContainer` a světlý kontejner na ní skoro nebyl vidět.
- **Na úzkém displeji se řada posouvá do strany.** Minimální dotyková plocha 48 dp zůstává
  i u menšího kontejneru (`minimumInteractiveComponentSize`), takže zmenšení tlačítek nepomůže;
  na složeném Foldu se osm tlačítek vedle odesílacího nevejde a „Bez formátování" ujíždí.
- **Citace jde po celých řádcích** (`toggled` roztáhne výběr), ostatní klienti ji kreslí jako blok.
  **Kód přes víc řádků je blok kódu** (`pre`), v řádku kód v řádku — jedna položka nabídky.
  „Kód" i „Citace" přepínají RODINU (`TextFormat.family`): blok kódu i sbalitelná citace se jimi
  vypnou, citace se nevnoří.
- **V kódu se nic nedohledává** (`detectEntities`): kód nesmí obsahovat jiné entity. Tučné
  a kurzívu uvnitř kódu TDLib při odeslání sám zahodí bez chyby (ověřeno: bot dostal jen `code`).
- **Doménový `MessageEntityType.Code` nese `block` a `language`** — kvůli úpravě: bez nich by se
  blok kódu vrátil jako kód v řádku a jazyk bloku by úprava smazala.
- **Jazyk bloku kódu nese `FormatSpan.language`** a odchází jako `preCode` (bez jazyka `pre`,
  prázdný jazyk je jako žádný). Tlačítko s jazykem („Jazyk kódu", popiskem je jazyk sám, třeba
  „kotlin") se v liště objeví hned za Kódem, jen když kurzor nebo výběr leží v bloku kódu
  (`codeBlockAt`); dialog drží obrazovka ze stejného důvodu jako dialog odkazu. Jazyk je jedno
  slovo bez mezer do 32 znaků (`codeLanguageOf`), prázdné pole ho sundá. Rozsahy se spojují jen
  se stejným jazykem (`merged`), takže sousední bloky v různých jazycích zůstanou dva. Uložený
  stav pole má páté pole pro jazyk; stav se čtyřmi poli (starší verze) se čte dál.
- **Sbalitelná citace je přepínač za Citací** (`unfold_less`, jen když kurzor nebo výběr leží
  v citaci, `quoteAt`). Mění jen druh citace (`toggledExpandable`), rozsah zůstává — obyčejnou
  a sbalitelnou TDLib stejně nedovolí vnořit.
- **Odkaz s popiskem je formát s adresou** (`TextFormat.Link`, `FormatSpan.url` →
  `TextEntityTypeTextUrl`). „Odkaz" v liště otevře dialog s adresou (`LinkDialog`,
  `linkTargetOf`: bez schématu `https://`, jinak jen `http(s)`, `tg://` a `mailto:`). Dialog
  drží obrazovka, ne lišta, a úsek se zachytí při ťuknutí: dialog vezme poli fokus a lišta
  s ním zmizí i se svým stavem. **Odkaz se psaním na konci neprodlužuje** (na rozdíl od tučného)
  a sousední odkazy na různé adresy se nespojí. V odkazu se nic nedohledává — adresa napsaná jako
  popisek by jinak byla druhým odkazem přes tentýž text. Tlačítko stojí hned za písmem: na
  složeném Foldu by jako osmé ujelo za okraj. Uložený stav pole odděluje pole znaky `\u001E`
  a `\u001F`, protože adresa obsahuje „:" i „;".
- **Koncept nese formátování** (`saveDraft(formats)`, obnova `draftFormatsOf`). Na rozdíl od
  úpravy se koncept nevzdává: z konceptu jiného klienta s vlastním emoji se vrátí aspoň tučné.
- **Změna jen formátování je změna**: uloží koncept (`onDraftChanged` bez změny textu neohlašuje
  „píše…" ani nezjišťuje náhled) a úprava zprávy, která mění jen formát, se odešle.
- **Spoiler se v poli neskrývá**, jen má podklad — pisatel musí vidět, co píše. Citace má v poli
  tónovaný podklad, ne pás přes celou šířku (ten se kreslí z layoutu zprávy).
- **Ověřeno na emulátoru** (anglicky, Fold složený): tučné v poli i v odeslané bublině, úprava
  tučné zprávy s přidanou kurzívou („edited", tučné zůstalo), koncept s tučným po zabití
  aplikace; bot přes Bot API dostal `code`, `bold` + `spoiler` a `blockquote` (roztažený na
  celý řádek) a `text_link` z dialogu; úprava zprávy s odkazem ho vrátí do pole a dialog nabídne
  změnu i odebrání. Formát bez výběru (česky, Pixel 9):
  „Aa" otevře lištu, zapnuté tučné platí pro napsané „XY", vypnuté pro „zz" za ním; odkaz je
  bez výběru zašedlý. Formátovaná zpráva psaná bez výběru se přes Bot API neověřovala — odeslání
  jde stejnou cestou rozsahů. Jazyk bloku kódu a sbalitelná citace (česky, Pixel 9): tlačítko
  jazyka se objeví za Kódem až nad blokem kódu, po uložení ukazuje „kotlin"; přepínač sbalitelné
  citace se objeví za Citací až v citaci; bot přes Bot API dostal `pre` s `language: kotlin`
  a `expandable_blockquote`. iOS jen přeložené.
- **Dva řádky na emulátoru jdou hardwarovým Enterem** (`adb shell input keyevent 66`) — vloží nový
  řádek, kdežto Enter softwarové klávesnice odesílá (`ImeAction.Send`). Rozepsaný koncept se dá
  při zkoušce odložit do schránky (`input keycombination 113 29`, pak `113 52`) a vrátit
  (`113 50`); Gboard ale při vložení do prázdného pole přidá na začátek mezeru, takže se po
  vrácení musí porovnat text (`uiautomator dump`) a mezera smazat.

### Vlastní emoji v poli

Vlastní emoji je v modelu formátování další druh rozsahu (`TextFormat.CustomEmoji`,
`FormatSpan.customEmojiId`, testy `CustomEmojiFormattingTest`): text je obyčejné emoji, za které
stojí, a odchází jako `TextEntityTypeCustomEmoji`. Je to ale OBSAH, ne formát písma:

- **Nedělitelné** (`atomicAfterEdit`): úprava, která zasáhne dovnitř (backspace smaže jen půlku
  páru UTF-16, vložení mezi znaky, přepsání), ho zruší; psaní před a za ně ho jen posune.
- **Psaním se neprodlužuje** (jako odkaz) a **dvě stejná vedle sebe se nespojí** (`merged` je
  vynechává) — jinak by z nich byl jeden rozsah přes dva znaky.
- **„Bez formátování" ho nesmaže** a nepokračuje v něm psaní (`continuingAt`).
- **Koncept ho nese** (`draftFormatsOf`, uložený stav pole má šesté pole s id) **a úprava
  zprávy jen tam, kde by šlo poslat** (`composedFormatsOf(…, customEmoji = state.customEmojiAllowed)`,
  právo z `canUseCustomEmoji`: Premium, nebo Uložené zprávy). Bez práva by ho server při úpravě
  tiše zahodil, takže „Upravit" v nabídce nad zprávou chybí.
  - **Kde se rozhoduje o právu a kde o DRUHU úpravy, je rozdělené**: nabídka a `startEdit`
    ptají `composerEdit(customEmoji)` (smí se?), kdežto karta, pole a odeslání už běžící úpravy
    berou `editKind()` (text, nebo popisek?) bez kontroly obsahu. S jedinou funkcí by karta
    úpravy zprávy s vlastním emoji zmizela, protože by nevěděla o právu z konverzace.
  - **Porovnání s původním textem při uložení pouští vlastní emoji vždy** (`customEmoji = true`):
    úprava se už rozběhla, takže právo bylo; nepovolené by vrátilo `null` a každé uložení by se
    tvářilo jako změna.
  - **Ověřeno na emulátoru** (Fold, účet bez Premium, Uložené zprávy): zpráva s vlastním emoji
    nabízí „Upravit", pole ukazuje obrázek, po připsání textu emoji zůstane a zpráva nese
    „upraveno". **Úprava s Premium a odmítnutí v cizím chatu bez Premium jsou pokryté testy,
    ne zařízením.**
- **Obrázek se kreslí PŘES pole** (`CustomEmojiOverlay`): `BasicTextField` inline obsah neumí,
  takže zástupné emoji se zprůhlední (barva `Transparent` skryje i barevné emoji — písmo emoji
  kreslí s alfou barvy) a na jeho místo z `onTextLayout` se položí `CustomEmojiImage`.
  **Jen když se text do pole vejde celý** (`customEmojiOverlayFits`): rolující pole (víc než
  6 řádků) posun nevydá a obrázky by ujely — pak je vidět obyčejné emoji.

## Náhled odkazu při psaní

Když rozepsaný text obsahuje odkaz, nad polem se po pauze v psaní ukáže náhled, jak ho připojí
server (`LinkPreviewHeader`: web, titulek, malý obrázek). Ťuknutí nebo ikona voleb otevře
`LinkPreviewOptionsSheet`: velký nebo malý obrázek, nad textem nebo pod ním, bez náhledu. ✕ náhled
vypne. Pravidla jsou čisté funkce v `ComposerLinkPreview.kt` (testy `ComposerLinkPreviewTest`).

- **`getLinkPreview` se volá, jen když text odkaz OPRAVDU má** (napřed lokální `getTextEntities`).
  TDLib u něj píše „do not call this function too often" a na text bez odkazu vrací 404. Pauza
  po posledním úhozu je 700 ms (`LINK_PREVIEW_DELAY_MS`). 404 je odpověď „náhled není", ne chyba.
- **Vynucená velikost bez adresy NEPLATÍ.** `forceSmallMedia` a `forceLargeMedia` TDLib bez
  výslovné `url` ignoruje. Adresa se proto doplní při odeslání z náhledu, který uživatel
  právě vidí (`forSending`). Ověřeno na emulátoru: YouTube, kde server volí velký obrázek,
  odešel s malým.
- **S víc odkazy v textu jde vybrat, ke kterému náhled patří** (sekce „Odkaz" v sheetu,
  `LinkPreviewOptions.url`, `selectedLink`, testy `ComposerLinkPreviewTest`). Odkazy vrací
  repository spolu s náhledem (`TextLinkPreview.links`) v podobě, jak stojí v textu — holé
  `example.com` zůstane holé a jde zpátky do `getLinkPreview` jako `url`. Označený řádek se
  porovnává s odkazem z textu, ne s `LinkPreview.url`: ta od serveru bývá upravená (`https://`,
  lomítko). **Výběr platí, jen dokud odkaz v textu stojí** (`forPreview(links)`): po jeho smazání
  by zpráva odešla s náhledem něčeho, co v ní už není. Výběr jiného odkazu se ptá hned, bez pauzy
  na psaní, a uloží se s konceptem (`toDomain` adresu přenáší). Ověřeno Bot API: druhý odkaz
  s malým obrázkem přišel jako `url` YouTube s `prefer_small_media`.
- **Text vložený sdílením je koncept jako napsaný** (`receiveShared` zapíše `draftText`). Pole
  se vyplní bez `onDraftChanged`, takže ho ViewModel neznal — a první uložení konceptu (tady
  změna volby náhledu) zapsalo prázdný koncept: sdílený text po odchodu z chatu zmizel.
- **Velikost se nabízí jen s `hasLargeMedia`** (`LinkPreview.canChangeSize`). Wikipedia ji nemá,
  YouTube ano. Volba, která nic nezmění, se nekreslí.
- **Jiný odkaz = volba znovu od začátku, kromě vypnutí** (`forPreview`). Vypnutí platí do
  vyprázdnění pole nebo odeslání. Kdo náhled zavřel, nechce, aby se vrátil s dalším písmenem.
- **Volba patří ke konceptu** (`DraftMessageContentText.linkPreviewOptions`) a po návratu do
  chatu se vrátí (`ChatInfo.draftLinkPreview`). Obnovený koncept si náhled zjistí sám —
  vyplnění pole z konceptu `onDraftChanged` nevolá. Totéž platí pro sdílený text.
- **S přílohami a při úpravě POPISKU se náhled neukazuje** (`composerLinkPreview`): text
  u příloh je popisek a `editMessageCaption` náhled nenese.
- **Úprava textu zprávy volbu náhledu PŘEBÍRÁ a jde ji měnit** (`ChatMessage.linkPreviewOptions`,
  `enterEdit`, `forEdit`, testy `ComposerLinkPreviewTest`). Nad polem je při úpravě tatáž
  hlavička náhledu a tentýž sheet jako při psaní. S výchozí volbou (`null`) by úprava zprávy
  poslané BEZ náhledu náhled vrátila — ověřeno Bot API: s `null` přišel náhled Wikipedie,
  s převzatou volbou zůstal `is_disabled`. Adresa se odešle jen tehdy, když odkaz v upraveném
  textu pořád je (i kdyby se odeslalo dřív, než dorazí náhled upraveného textu). Změna jen
  volby, bez změny textu, je taky úprava. Ze stejného důvodu úprava popisku přenáší
  `showCaptionAboveMedia` (dřív natvrdo `false`, takže popisek nad médiem nastavený jiným
  klientem úprava tiše vrátila pod médium).
- **Náhled konceptu se po dobu úpravy ODKLÁDÁ s ním** (`LinkPreviewStash`), stejně jako text
  konceptu. Stav náhledu je jeden pro pole, takže bez toho by upravená zpráva odešla s volbou
  rozepsaného konceptu a koncept se po úpravě vrátil s volbou upravené zprávy. Volba změněná
  při úpravě se do konceptu neukládá. Vymazaný text úpravy volbu nezahodí — není to nová zpráva.
- **Hlavička náhledu stála v bloku kresleném jen mimo úpravu** (spolu s citací a přílohami),
  takže při úpravě chyběla, i když ViewModel náhled měl. Teď je za ním.
- **Při ověřování úpravy přes `adb` se dlouhý stisk NESMÍ trefit do odkazu**: na přetíženém
  emulátoru dopadl jako ťuknutí a otevřel se Chrome — a další ťuknutí skriptu šla do něj.
  Stisk mířit na slovo před odkazem a po něm zkontrolovat `mCurrentFocus`.
- **Sheet je celý rozbalený** (`enabledValues = Hidden, Expanded`). Napůl rozbalený schoval
  „Poslat bez náhledu" pod okraj — tatáž past jako u ostatních sheetů s víc volbami.
- **`adb input text` s adresou se na emulátoru useká na tečce** („https://en.wikipedia"). Text pak
  odkaz neobsahuje a náhled se správně neukáže. Adresa se píše po kouscích („en", „.",
  „wikipedia"…). A s víc emulátory najednou `adb` bez `-s` odmítne všechno — v zsh se sériové
  číslo nastavuje přes `ANDROID_SERIAL`, proměnná `ADB="adb -s …"` se na slova nerozdělí.

## Koncept (rozepsaná zpráva)

Rozepsaný text se ukládá do TDLib (`setChatDraftMessage`), takže přežije odchod z chatu, je vidět
v seznamu chatů („Koncept: …") a na ostatních zařízeních.

- **Ukládá se s odstupem 1,5 s od poslední úpravy**, ne na každý úhoz — koncept se propisuje na
  server. Při odchodu z chatu se čekání přeskočí v `onCleared`.
- **Ukládání běží v `appScope`, ne ve `viewModelScope`.** Ten se při odchodu z chatu zruší dřív,
  než by poslední úprava stihla odejít. Hodnoty se proto berou při plánování na hlavním vlákně,
  úloha je jen odešle.
- **Odeslání čekající uložení ZAHODÍ.** TDLib koncept při odeslání maže sám (`clearDraft`);
  opožděné uložení by ho vrátilo se starým textem.
- **Koncept se do pole vrací jen, když je prázdné** (`InputFill.onlyIfEmpty`). Než ho TDLib
  vydá, mohl uživatel začít psát. Po vložení se `inputFill` vynuluje — jinak by se po otočení
  vrátil i smazaný.
- **Pole drží `TextFieldValue`, ne `String`.** U vráceného konceptu je potřeba postavit kurzor
  na konec; `onDraftChanged` se volá jen při změně TEXTU, ne při pohybu kurzoru.
- **Citace je součást konceptu** a obnoví se s ním (i když není v načtené historii).
- **Prázdný text koncept maže, i s citací.** Odpověď bez textu by byl koncept, který v seznamu
  nic neukáže.
- **Seznam chatů musí z `UpdateChatDraftMessage` brát i POZICE.** Chat s čerstvým konceptem se
  posune nahoru a jinudy se to nedozvíme. Čas u položky je proto novější z konceptu a poslední
  zprávy — jinak by u chatu nahoře stálo starší datum než pod ním.
- **Koncept v otevřeném chatu se z updatů NEČTE.** TDLib upozorňuje, že
  `UpdateChatDraftMessage` může pro právě otevřený chat přijít se starým obsahem; přepsat jím
  pole by uživateli smazalo, co právě napsal.
- **Ukazuje se jen textový koncept.** Rozpracovaná hlasovka je taky koncept, ale „Koncept:" bez
  obsahu by nic neřekl.

## Plovoucí napovídání nad polem

Emoji ke slovu, samolepky k emoji, příkazy bota, zmínky, hashtagy a výsledky inline bota PLAVOU nad vstupním polem
(`FloatingSuggestion`, `floatAbove`) na skle v inverzní roli jako snackbar, a místo v rozvržení
nezabírají.

- **Pole je `bottomBar` a seznam zpráv se odsazuje o jeho výšku.** Dokud napovídání stála v jeho
  sloupci, každé objevení, zmizení i změna výšky (další písmeno zúží seznam příkazů) zvedly
  a spustily celou konverzaci — oči při psaní skákaly za ní.
- **`floatAbove` nahlásí nulovou výšku a obsah posadí nad své místo.** Obsah mimo hranice rodiče
  Compose kreslí i nechá ťukat, dokud nic neořezává (ověřeno ťuknutím na emoji i příkaz).
  Vrstva stojí v `bottomBar` těsně nad polem, u textu, ke kterému patří.
- **Vyrůstá zvětšením od pole, ne rozbalením do výšky** — to by u plovoucího prvku vypadalo,
  jako by se pod ním něco odsouvalo.
- **Sklo je v INVERZNÍ roli, jako snackbar** (`inverseSurface`, popředí `inverseOnSurface`,
  akcent `inversePrimary`; `FloatingSuggestionColors`): ve světlém motivu tmavé, v tmavém světlé.
  Je to dočasný prvek nad obsahem a má od něj vystoupit v OBOU motivech — M3 na to inverzní role
  má. Dvě slepé uličky: světlé sklo v barvě plochy (jako pruh připnutých) se ve světlém motivu
  slévalo s bublinami, natvrdo tmavé (`scrim` s bílým textem) by se slilo s tmavou konverzací.
  `ListItem` popředí nedědí, barvy se mu předávají (`FloatingSuggestionColors.listItem()`).
- **Krytí 0,8, víc než u ostatních skel** (`FLOATING_SUGGESTION_TINT_ALPHA`): inverzní plocha nese
  text opačné světlosti než obsah pod ní. `inverseSurface` je světlejší než černá, takže s 0,66
  (hodnota z tmavého `scrim`) by bílé písmo nad bílou fotkou spadlo pod 4 : 1; s 0,8 vyjde v obou
  motivech nejhůř kolem 6,5 : 1 (výpočet, ne měření).
- **Tlačítko dolů a zmínky (FAB slot) na tu chvíli ustoupí** (`suggestionsFloating`): Scaffold
  je staví nad `bottomBar`, tedy přesně přes plovoucí vrstvu.
- **V rozvržení zůstávají klávesnice bota a návrhy odpovědí.** Klávesnice bota je trvalá
  a zakryla by otázku bota, na kterou se odpovídá; návrhy odpovědí naskočí po příchozí zprávě
  a plovoucí by zakryly právě ji. Uvnitř karty pole (citace, náhled odkazu, lišta formátování)
  zůstává karta jedním celkem a roste.
- **Panely s posledním stavem pro odjezd ho drží ve STAVU** (`mutableStateOf`), ne v poli —
  u příkazů zůstával po „/n" → „/ne" celý seznam (tatáž past jako u emoji).
- **Ověřeno na emulátoru** (Fold složený, chat s botem): příkazy z tlačítka i po „/s" (seznam se
  zúžil, konverzace se nepohnula), ťuknutí poslalo `/stav`, `@gif cat` a „heart" s dosazením
  emoji; inverzní sklo ve světlém i tmavém motivu (`cmd uimode night yes`). Zmínky ověřené nejsou
  (jen ve skupině), iOS jen přeložené.

## Napovídání zmínek

Po `@` ve skupině napovídá nad polem členy (`MentionSuggestionsPanel`, doména `Mentions.kt`:
`mentionQueryAt`, `mentionInsertion`, `nameMentionEntities`, testy `MentionsTest`). Ťuknutí
vloží `@jmeno`, nebo u člena bez veřejného jména jeho křestní jméno se zmínkou (entita
`TextEntityTypeMentionName`).

- **Zmínka se hledá u KURZORU, ne na začátku textu** — píše se kdekoli ve větě. Zavináč musí
  stát na začátku nebo po mezeře („jana@firma" zmínka není) a mezi ním a kurzorem jen písmena
  (i s diakritikou, hledá se i podle jména), číslice a podtržítko. Samotné `@` nabídne členy
  bez filtru. Rozepsaný dotaz na inline bota (`@gif kočka`) má přednost.
- **Kdo jde zmínit, říká server** (`searchChatMembers` s `ChatMembersFilterMention`), ne seznam
  členů — u skupiny se skrytými členy by seznam vydal jen správce. Vlastní účet se vynechá.
- **Jen ve skupině a mimo úpravu zprávy.** V soukromém chatu je koho zmínit jen jeden, v kanálu
  nikdo nepíše a úprava posílá holý text, ze kterého by zmínka jménem zmizela.
- **Zmínka jménem žije ve ViewModelu, ne v poli** (`NameMention`, `nameMentions`). Pole je holý
  text, takže entitu s id uživatele doplní až odeslání (`detectEntities(text, mentions)`):
  jméno se v textu najde jako samostatné slovo mimo entity, které text už má, v pořadí vkládání.
  Smazané nebo přepsané jméno se prostě nenajde. Vyprázdněné pole i odeslání je zapomenou;
  koncept je nenese — po návratu do chatu zbyde jen text.
- **Funguje i v popisku příloh** (`sendAttachments` bere zmínky taky).
- **Ověřeno na emulátoru sondou**: účet na emulátoru v žádné skupině, kam smí psát, není (Kotlin
  Community i Android Developers tam ukazují „Join"), takže se napovídání dočasně povolilo
  v chatu s testovacím botem a vynutila se zmínka jménem. Panel po `@`, vložení jména s mezerou
  a bot přes `getUpdates` dostal `text_mention` se svým id. **Ve skupině ověřené** (testovací
  skupina s botem): po `@` panel s botem i jeho `@jménem`, ťuknutí vloží `@NoTiBoBoBot ` a zpráva
  odejde se zmínkou. **Bot v režimu soukromí zmínku ve skupině NEDOSTANE** — dostává jen příkazy
  `/x@bot`, odpovědi na sebe a servisní zprávy —, takže to přes `getUpdates` ověřit nejde; vidět je
  to na podtržené zmínce v bublině.
- **Id veřejné skupiny zjistí Bot API** (`getChat?chat_id=@jmeno`, bot nemusí být členem)
  a otevře ji `am start … --el eu.tintera.plume.extra.CHAT_ID <id>` — bez ťukání v seznamu
  chatů se skutečnými lidmi.

## Napovídání hashtagů a nedávných inline botů

Po `#` u kurzoru nabídne nad polem dříve použité hashtagy (`HashtagSuggestionsPanel`, doména
`Hashtags.kt`: `hashtagQueryAt`, `hashtagInsertion`, testy `HashtagsTest`), po `@` na začátku
zprávy nedávné inline boty (v `MentionSuggestionsPanel`, před členy skupiny). Logika je
v `ConversationSuggestions` (`hashtagQuery`, `mentionQuery(…, atStart)`, testy
`ConversationSuggestionsTest`).

- **Hashtagy si pamatuje TDLib sám** z odeslaných zpráv (`searchHashtags`, lokálně, bez serveru):
  hashtag z čerstvě odeslané zprávy se hned nabízí. Vrací je bez `#`; pro jistotu se mřížka
  i tak odstřihne. Prefix jde taky bez `#`.
- **Pravidla jako u zmínky**: `#` na začátku nebo po mezeře („C#" hashtag není), pak jen písmena,
  číslice a podtržítko; samotné `#` nabídne poslední hashtagy. Hashtag celý napsaný v poli se
  nenabízí — ťuknutím by se nic nezměnilo. Ťuknutí nahradí úsek od `#` po kurzor a přidá mezeru.
- **Hashtagy i při úpravě zprávy** (na rozdíl od zmínek): je to holý text a nic z něj nezmizí.
- **Nedávní inline boti jen na ZAČÁTKU zprávy a bez příloh** — jen tam se z `@bot ` stane dotaz
  na bota (`inlineQueryOf`); uprostřed věty by to byla obyčejná zmínka. Nabízejí se v KAŽDÉM
  chatu, kam se smí psát, i v soukromém, kde se členové nenapovídají. Filtrují se u nás
  (`matchesMention`: uživatelské jméno nebo slovo jména začíná dotazem), server je nehledá.
- **TDLib si bota zapamatuje až po ODESLÁNÍ jeho výsledku**, ne po dotazu — na čerstvém účtu je
  seznam prázdný a po `@` se nic neukáže, i když všechno funguje (první pokus na emulátoru).
- **Ťuknutí na bota rovnou zapne inline režim**: vloží `@bot ` a s mezerou je to dotaz.
- **Ověřeno na emulátoru** v chatu s testovacím botem: odeslaná zpráva s `#plume`, pak `#pl` →
  panel s `#plume`, ťuknutí vložilo `#plume `; po odeslání GIFu přes `@gif` nabídlo samotné `@`
  „GIF Search @gif" a ťuknutí ukázalo výsledky bota. Text do pole přes sdílení (`SEND`
  s `EXTRA_SHORTCUT_ID`), `@` na začátek až do PRÁZDNÉHO pole — sdílení text připojí za
  rozepsaný. **Neověřeno**: boti před členy ve skupině, iOS (jen přeložené).
- **Hashtag jde z historie odebrat podržením** v panelu („Odebrat z historie",
  `removeRecentHashtag`, `ConversationSuggestions.removeHashtag`) — stejně jako člověk
  z nejčastějších v hledání chatů: nabídka bez potvrzení a bez „Vrátit", hashtag se do historie
  vrátí sám, jakmile ho uživatel zase pošle. Panel se pak přečte znovu (`searchHashtags`), nic
  se nepřepisuje lokálně. Odebrání běží mimo `hashtagJob`, jinak by ho další napsané písmeno
  zrušilo. TDLib hashtag bere bez `#` (tak ho i vrací); `#` se pro jistotu odstřihne.
- **Nabídka nad panelem si nebere klávesnici** (`ExpressiveMenu`), takže pole s rozepsaným `#`
  zůstane otevřené a panel jen zkrátí. Nad klávesnicí se nabídka otevře NAD řádkem (dole není
  místo) a zakryje řádek nad ním — vadí to málo, nabídka je jednopoložková.
- **Ověřeno na emulátoru** (15. 10. 2026, „Plume test moderace"): odeslaná zpráva
  s `#plumeodebrat #plumezustat`, pak `#plume` → oba, podržení `#plumeodebrat` → „Odebrat
  z historie" → v panelu zbyl `#plumezustat`, klávesnice zůstala; samotné `#` znovu napsané
  nabídlo už jen `#plumezustat` (tedy smazané v TDLib, ne jen v panelu). Desktop (pravé
  tlačítko) a iOS neověřené.

## Odpovědi (reply)

- **`ChatMessage` nese jen `replyToMessageId`, ne celou citovanou zprávu.** V konverzaci se
  odpovídá skoro vždy na něco, co už je načtené, takže náhled si poskládá `ConversationViewModel`
  z vlastního seznamu a ušetří jedno volání na každou odpověď. Zbytek (odpověď na starou zprávu
  hluboko v historii) dotahuje `resolveMissingQuotes()` do `ConversationState.quotes`.
- **Odpověď na zprávu z JINÉHO chatu nese citaci sama** (`ChatMessage.externalReply`,
  `toExternalReply`). Náhled se jinak skládá z načtené historie, která je vždy jen z jednoho
  chatu, takže dřív se taková odpověď zahazovala a zpráva vypadala jako bez citace. TDLib ale
  posílá v `MessageReplyToMessage` i `origin` a `content` citované zprávy — zástupná
  `ChatMessage` se z nich poskládá bez dotazu navíc. Text je automatický úryvek serveru
  (`quote` s `isManual == false`), bez něj popis obsahu (`MessageContent.previewTextOrNull`);
  jméno z původu, bez něj název chatu.
- **Ťuknutí na ni otevře ten druhý chat na citované zprávě** (`MessageAction.OpenForwardOrigin`,
  tatáž cesta jako značka „Přeposláno od"), ne skok v téhle konverzaci. Nulové `chatId` =
  chat, který server neprozradil; citace se pak kreslí, jen nikam nevede.
- **Bot odpověď z jiného chatu nevyrobí** (musel by mít přístup k oběma chatům), takže se
  ověřuje sondou: v `toChatMessage` dočasně sestavit `MessageReplyToMessage` (chat Uložené
  zprávy, s `origin` a `content`, nebo jen s `quote`) pro text `#ext…` od testovacího bota.
  Prochází tím převod i kreslení i navigace; necommituje se.
- **`InputMessageReplyToMessage.pollOptionId` je nenullovatelný** — prázdný řetězec, ne `null`.
- **Rozepsaná odpověď se ruší hned při odeslání**, ne až po potvrzení; jinak by při pomalé síti
  šlo odeslat druhou zprávu se stejnou citací.
- **V soukromém chatu nemá citace koho uvést.** `senderName` se vyplňuje jen ve skupinách, takže
  se jako záloha bere název chatu (`ChatInfo.title`).
- **Citace části zprávy** (`ChatMessage.replyQuote`, `ConversationState.replyQuote`) se kreslí
  místo začátku citované zprávy — nad odpovědí i v hlavičce nad polem. Bere se jen ruční citace
  (`TextQuote.isManual`); automatickou server doplňuje u odpovědí z jiného chatu, ty se zahazují.
  Citace je součást odpovědi i konceptu (`saveDraft(quote)`, `ChatInfo.draftReplyQuote`) a maže
  se s odpovědí (`clearReply`). Jak vzniká, viz „Citace z nabídky".
- **Citace nese náhled přílohy** (`quoteThumbnail` v `QuotedMessage.kt`, testy
  `QuoteThumbnailTest`): fotka, video, GIF, kolečko (kulaté), obal skladby, samolepka a soubor
  s náhledem — v bublině odpovědi i nad vstupním polem, vlevo od jména. Bere se nejmenší
  varianta; do čtverečku 36 dp se ostrá verze stahovat nemá.
- **Spoiler a citlivý obsah mají v citaci JEN rozmazaný náhled** (`BlurredThumbnail`, nic se
  nestahuje). `RemoteImage` by si ostrou verzi stáhl a po chvíli prolnul — citace by pak
  prozradila, co bublina nad ní schovává. **Mizející a tajné médium náhled nemá vůbec**: citace
  žije v konverzaci déle než médium samo.
- **Odpověď z jiného chatu náhled má taky** (`MessageContent.replyMedia`): fotka, video, GIF
  a samolepka z `MessageReplyToMessage.content`. Celý převod `messageMedia` stojí na `Message`,
  kterou tam nemáme; citlivost cizího chatu neznáme, spoiler ano.
- **Ověřeno na emulátoru** botem (`sendPhoto`, pak `sendMessage` s `reply_parameters`): náhled
  u odpovědi na fotku, rozmazaný u odpovědi na spoiler, náhled nad polem po tahu na odpověď.
  Kolečko, samolepka, obal skladby a odpověď z jiného chatu jsou pokryté testem, **na zařízení
  ověřené nejsou**; iOS jen přeložené.
- **Vynucená odpověď bota může přebít koncept.** `restoreDraftReply` i vynucená odpověď nastavují
  `replyingTo`, jen když je prázdné, a kdo doběhne dřív, vyhraje — koncept se čte asynchronně.
  Při zkoušení citací v chatu s testovacím botem pozor na starou `force_reply`.

## Odesílání a scrollování

- **Odeslaná zpráva se do seznamu přidává OKAMŽITĚ.** `sendMessage` vrací zprávu hned, s dočasným
  id a stavem `Sending`; čekat na server by znamenalo viditelnou prodlevu mezi ťuknutím a bublinou.
- **TDLib po potvrzení zprávě ZMĚNÍ id.** `UpdateMessageSendSucceeded` nese staré i nové —
  zprávu nestačí aktualizovat, původní se musí ze seznamu odebrat, jinak tam zůstane dvakrát.
- **Klíč položky v `LazyColumn` proto NENÍ id zprávy**, ale `ConversationState.keyOf()`: u odeslané
  zprávy zůstává dočasné id (`itemKeys`: nové id → dočasné). S id jako klíčem bylo potvrzení
  doručení pro seznam odebrání jedné položky a přidání jiné — `animateItem()` jednu nechal
  odprchnout, druhou naskočit a bublina problikla. Let ze vstupního pole navíc přišel o cíl,
  protože potvrzení dorazí většinou dřív, než let doběhne. Kdo z viditelných položek čte klíč
  (hlášení přečtení, plovoucí datum), musí počítat s tím, že to není id.
- **Pořadí mezi `newMessages` a `sentMessageUpdates` NENÍ ZARUČENÉ.** Jsou to dva samostatné
  odběry ve dvou korutinách, takže potvrzení odeslání může předběhnout doručení té rozepsané
  zprávy, ke které patří. Vrátit ji pak do seznamu znamená mít tam zprávu dvakrát — a protože
  si podle `itemKeys` obě drží TENTÝŽ klíč, `LazyColumn` na to spadne
  („Key … was already used"). Proto obě změny stavu dělá `ConversationState`
  (`withMessage` / `withSentMessage`) a zprávu, kterou už potvrzení nahradilo, `withMessage`
  zahodí. Kontroluje se to proti HODNOTÁM `itemKeys`, tedy dočasným id.
- **Pád z toho byl vzácný a nereprodukovatelný** — poprvé se objevil po stovkách odeslaných zpráv
  a při dvou dalších pokusech o totéž nepřišel. Sedí na něj proto **test nad stavem**, ne klikání:
  `ConversationStateTest` prožene obě pořadí a hlídá, že klíče položek zůstanou jedinečné.
  Bez opravy test selže přesně tím, čím spadla aplikace: `[382754357249, 382754357249]`.
- **Rozepsaná zpráva dorazí přes `newMessages` DŘÍV, než `sendMessage` vrátí výsledek.** Let se
  proto na ni váže už tam (`landingOn`, podle textu a stavu `Sending`). Když se vázal až
  v `send`, bublina se na snímek vykreslila na místě, pak zmizela do letu a přiletěla znovu.
- **Nová zpráva dole se ukotví přes `requestScrollToItem(0)` v kompozici**, ne efektem
  s `animateScrollToItem`. `LazyColumn` drží pozici podle klíče první viditelné položky, takže
  vložená zpráva skončila pod spodní hranou a seznam k ní dojel až o snímek později.
- **Cíl letu nemá fade-in `animateItem`.** Během letu se kreslí v překryvu, kam průhlednost
  položky nedosáhne; po přistání by skočil z plné viditelnosti do rozjetého prolnutí.
- **Ikona doručení NENÍ `Crossfade`.** Ten prolíná obě ikony naráz, takže v půlce přechodu jsou
  napůl průhledné přes sebe; fajfka a dvojitá fajfka se kryjí a jejich společná část na okamžik
  zeslábla. Teď `AnimatedContent`: nová ikona naskočí zvětšením, stará jen rychle zmizí, obojí
  přes `MotionScheme`. Barva a popis se počítají ze stavu UVNITŘ obsahu — zvenku by odcházející
  hodiny zčervenaly dřív, než zmizí.
- **Čipy reakcí jsou v `key(emoji)`.** Server pořadí reakcí přeskládává a bez klíče se stav
  animací (barva, tvar) přenesl na sousední čip.
- **„Přečteno" není ve zprávě, ale na chatu** (`lastReadOutboxMessageId`): posuvný ukazatel,
  pod který spadají všechny odchozí zprávy s menším id. Kdyby se ukládalo do zpráv, muselo by se
  při každém přečtení přemapovat celá historie.
- **Scroll po odeslání je bezpodmínečný**, u příchozí zprávy jen když je uživatel dole. Jinak by
  ho nová zpráva vytrhla ze čtení historie — místo toho se dole objeví odznak s počtem.
- **Sdílený prvek při odeslání jde přes `sharedElementWithCallerManagedVisibility`.** Běžné
  `sharedElement` chce `AnimatedVisibilityScope`, který položka `LazyColumn` ani obsah vstupního
  pole nemají; tahle varianta bere jen `visible: Boolean`. `SharedTransitionLayout` už obaluje
  celou aplikaci v `AppTheme` — **další obalení by vyrobilo vnořený scope a klíče by se
  nespárovaly**.
- **Sdílí se CELÁ bublina, ne jen text.** Se sdíleným textem doletí text sám a pozadí pod ním
  na konci skokem naskočí.
- **Startovní strana letu musí přežít vyprázdnění pole.** Text se zapamatuje do `MessageFlight`
  hned při odeslání; bez toho by mezi vyprázdněním pole a příletem zprávy nebylo co animovat.
  Let se ruší až po doběhnutí animace — dřívější zrušení sebere klíč uprostřed přechodu.
- **Pořadové číslo v klíči letu** rozliší dvě rychle po sobě odeslané zprávy; s jedním klíčem
  by se dvě animace popraly o tentýž prvek.
- **„Jsem dole" kontroluje i offset**, ne jen index 0. Bez toho se za dole považuje i stav,
  kdy je poslední bublina z půlky za okrajem.

## Neodeslaná zpráva a opakované odeslání

Pod neodeslanou bublinou je místo samotného vykřičníku věta s výzvou („Neodesláno. Ťukni
a zkus to znovu.", vzor Google Messages) a „Poslat znovu" je první položka nabídky nad zprávou
(`MessageMenuAction.Resend`). U zprávy, kterou znovu poslat nejde, ťuknutí řekne proč
(`SendFailure.reason`). Dřív byl vykřičník slepá ulička: zpráva šla jen smazat a napsat znovu.

- **Znovu poslat jde jen málo chyb** (`MessagesManager::can_resend_message` ve zdroji TDLib):
  429 (limit), `Message is too old to be re-sent automatically`, `SCHEDULE_TOO_MUCH`,
  `SEND_AS_PEER_INVALID`, `QUOTE_TEXT_INVALID`, `REPLY_MESSAGE_ID_INVALID` a placené zprávy.
  Přeposlanou zprávu TDLib znovu poslat neumí vůbec. O tom rozhoduje `canRetry` od TDLib;
  Plume k tomu přidává jen to, co samo neumí: jinou citaci (`needAnotherReplyQuote`) a hvězdy
  (`requiredPaidMessageStarCount`). Jiného odesílatele a zahození odpovědi udělá TDLib při
  opakování sám.
- **Nejběžnější skutečný případ je zpráva poslaná offline, která čekala přes den**
  (`MAX_RESEND_DELAY` = 86 400 s): TDLib ji po startu z binlogu automaticky neodešle
  a označí ji „příliš stará". Uložených zpráv se to netýká.
- **Opakování zprávu NAHRADÍ**: TDLib starou smaže (`updateDeleteMessages`, trvale) a novou
  ohlásí jako novou zprávu ve stavu odesílání, s novým id a na konci chatu. ViewModel tedy nic
  nepřepisuje, obrazovku srovnají updaty jako u každé jiné zprávy (`ConversationRepository.resend`).
- **Album se posílá celé najednou** (`failedGroup`): po kouskách by se rozpadlo na samostatné
  fotky. Znovu poslat jde, jen když jde každá jeho neodeslaná zpráva (`albumFailure`), a důvod
  je ten, kvůli kterému to nejde. Id musí jít vzestupně, jinak TDLib odmítne.
- **Před uplynutím `retryAfter` TDLib odmítne sám** („Message can't be re-sent yet",
  `error_resend_too_early`); odpočet se nedrží, `retryAfter` se v TDLib bez updatu mění.
- **Věta pod bublinou je širší než krátká bublina** — sloupec zprávy proto musí mít zarovnání
  podle strany (`MessageFrame`), jinak odchozí bublinu odsunul od pravého okraje.
- **Ověřeno na emulátoru se skutečným serverem**: letový režim, zpráva testovacímu botovi,
  zabitá aplikace, hodiny posunuté o dva dny (`settings put global auto_time 0`,
  `cmd alarm set-time <ms>`), start offline → „Neodesláno. Ťukni a zkus to znovu." a „Poslat
  znovu" v nabídce; po vrácení času a sítě ťuknutí zprávu odeslalo (stará zmizela, nová
  doručená a přečtená). **Neověřené**: zpráva, kterou znovu poslat nejde (věta s důvodem),
  album a pomalý režim ve skupině.

