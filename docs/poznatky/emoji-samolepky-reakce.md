# Emoji, samolepky a reakce

Poznatky k jedné oblasti aplikace. Obecná pravidla projektu a rejstřík ostatních souborů jsou
v [CLAUDE.md](../../CLAUDE.md); poučení z opravy v téhle oblasti patří sem.

## Emoji a samolepky

- **Zpráva složená jen z emoji se kreslí velká a bez bubliny**, stejně jako samolepka. Bublina
  kolem nich nemá co ohraničovat: samolepka má vlastní tvar s průhledností a velká emoji jsou
  obrázek samy o sobě. Barva popředí pak musí patřit PLOŠE, ne bublině, která tam není —
  `onPrimaryContainer` na průhledném pozadí je nečitelný.
- **Samotné emoji NENÍ textová zpráva.** Telegram ho posílá jako `MessageAnimatedEmoji`
  s animovanou podobou navíc, takže padalo do větve `else` a v bublině z něj bylo slovo „Zpráva".
- **Animované emoji s `.tgs` je příloha** (`MessageMedia.AnimatedEmoji`), kreslí se stejnou
  komponentou jako samolepka (`AnimatedSticker`) a bez bubliny. Text je u něj prázdný, jinak by pod
  animací stál ještě jednou jako znak. O textu i příloze rozhoduje jedna funkce
  (`MessageAnimatedEmoji.animationFileId`) — dvě nezávislá rozhodnutí by mohla nechat zprávu bez
  obojího, nebo s obojím.
- **Bez animace, nebo v jiném formátu, zůstává emoji textem** (`emoji`, obyčejný unicode znak):
  vykreslí se sám a chytí se na něj vykreslení velkých emoji. Prémiová emoji můžou mít animaci
  v libovolném formátu.
- **Než se animace načte, drží místo tentýž znak velkým písmem** v pevném čtverci 112 dp. Přechod
  je výměna obrázku za obrázek a bublina se při ní nepřeskládá.
- **Kopírování animovaného emoji kopíruje jeho znak** (`copyableText`) — text je prázdný, ale
  dřív, jako textová zpráva, to šlo.
- **Kostka (`MessageDice`) padala do téhož `else`.** Dnes je to animace (viz „Hod kostkou");
  text „🎲 6" zůstává v náhledu, citaci, schránce a tam, kde animace není `.tgs`.

### Hod kostkou

Kostka, šipky, míč, koš, kuželky i automat (`MessageDice`) jsou příloha `MessageMedia.Dice`
a kreslí se bez bubliny jako samolepka (`DiceImage` v `Attachments.kt`, převod `toDiceMedia`,
testy `DiceMediaTest`).

- **Animaci posílá Telegram, výsledek je v ní NAKRESLENÝ.** `finalState` končí snímkem s tím,
  co padlo; `value` je jen číslo pro text. Proto se nekreslí žádné vlastní číslo navrch.
- **Hraje jednou, ne ve smyčce** (`StickerPlayback.Once`). Ve smyčce by se házelo pořád dokola
  a výsledek by byl vidět jen na okamžik. Hod z historie se ukáže rovnou posledním snímkem
  (`LastFrame`); přehraje se jen ten, který přišel po otevření konverzace
  (`claimDiceThrow`, stejná tolerance jako pružný příchod zprávy), a jen jednou — po
  odscrollování a návratu je vidět výsledek. Kopie v nabídce nad zprávou nehází.
- **Konec hodu se hlídá mimo kompozici** (`snapshotFlow { state.isAtEnd }`): průběh se mění
  každý snímek a čtený v kompozici by ji skládal znovu.
- **Automat je pět vrstev přes sebe** (pozadí, páka, tři válce) a hod skončil, až doběhnou
  VŠECHNY — válce nekončí naráz. Když kterákoli vrstva není `.tgs`, zůstane text: chybějící
  válec by ukázal jiný výsledek, než jaký padl.
- **Bez výsledku (`value == 0`) se točí úvodní animace** (`initialState`) ve smyčce; na druhý
  stav se nepadá. Nastane u vlastního hodu, dokud server výsledek nepošle (ověřeno: odeslaná
  kostka se chvíli točí, pak dopadne).
- **Ověřuje se botem**: `sendDice` s `emoji=🎲` a `🎰` do otevřeného chatu (hod proběhne), pak
  odejít a vrátit se (výsledek hned). Ověřeno na emulátoru.

- **Výhru oslaví konfety** (`DiceConfetti`, `MessageMedia.Dice.successFrame` ze
  `successAnimationFrameNumber`). Vyletí na snímku, který poslal server — když míč propadne
  sítí, šipka zasáhne střed — ne až po dohrání: `AnimatedSticker` umí ohlásit snímek
  (`markerFrame`, snímek = `lerp(startFrame, endFrame, progress)`), u automatu poslední
  vrstva (pravý válec dotočí poslední). **Jen u VLASTNÍHO hodu, který proběhl před očima** —
  server snímek výhry posílá kvůli odeslanému hodu (TDLib: „on updateMessageSendSucceeded"),
  oficiální klienti cizí výhru neslaví. Konfety se kreslí v Compose (barvy z tématu, náhoda
  se semínkem id zprávy), bez velikosti v layoutu; položka se po dobu oslavy zvedne
  (`LocalRaiseItem`, `zIndex` jako u efektu zprávy).
- **Ověřuje se hodem ⚽ v chatu s botem** (gól je hodnota 3–5, tedy asi tři hody z pěti; text
  do pole přes sdílení, `SEND` s `EXTRA_SHORTCUT_ID chat_<id bota>`) a sérií surových snímků
  (`screencap` do `/data/local/tmp` ve smyčce v jednom `adb shell`, aspoň 70 — hod trvá přes
  tři vteřiny). Hodnotu hodu řekne `getUpdates`.

#### Poslat hod

- **Samotné emoji ze seznamu serveru odejde jako hod**, ne jako text (`diceEmojiFor`, testy
  `DiceEmojiTest`; `InputMessageDice`). Tak to dělají oficiální klienti — žádné tlačítko navíc,
  kdo pošle 🎲, hází. Seznam (🎲 🎯 🏀 ⚽ 🎳 🎰) posílá server v `UpdateDiceEmojis`.
- **`UpdateDiceEmojis` chodí JEDNOU po startu klienta**, takže ho drží eager `ChatRepository`
  (`diceEmojis`), ne konverzace — ta vzniká až s obrazovkou a update by minula. Prázdný seznam
  (nic nedorazilo) = emoji odejde jako text, nic se nerozbije.
- **Porovnává se bez U+FE0F a posílá se podoba od serveru.** Klávesnice dává „🎰️" i „🎰"
  podle toho, odkud emoji vzala; ověřeno, že „🎰️" z pole dorazilo botovi jako hod.
- **Hod je v právech „ostatní zprávy"** (`ContentAccess.stickers`). Kde se smí jen text, emoji
  odejde jako text — hláška by tu byla horší, uživatel chtěl poslat zprávu.
- **Neletí z pole** (`sendMedia`, jako samolepka): bublina by přiletěla jako text a na konci
  se proměnila v kostku. Pole i koncept se vyprázdní jako u textu (`clearDraft = true`),
  citace se spotřebuje.
- **Ověřeno na emulátoru** v chatu s botem: 🎲 z pole (točí se, pak výsledek; bot dostal
  `dice.value`), 🎰️ s výběrem varianty jako odpověď na hod (bot dostal `reply_to_message`).
  Text do pole jde přes sdílení (`SEND` s `EXTRA_SHORTCUT_ID chat_<id bota>`) — emoji
  `adb input text` nenapíše.
- **Strop jsou 3 emoji** (`MAX_LARGE_EMOJI`). Nad ním už nejde o gesto, ale o odstavec, a ve
  velké sazbě by zabral půl obrazovky. Míň emoji = větší: `displayLarge` / `Medium` / `Small`.
- **Odpověď si bublinu drží**, i když je jen z emoji — citace bez pozadí se ztratí.
- **Detekce emoji je pragmatický výběr rozsahů, ne unicode tabulka.** Kotlin v commonMain nemá
  ani `codePointAt`, ani `\p{...}` v regulárních výrazech, takže úplná data by znamenala vlastní
  tabulku a její údržbu při každé revizi Unicode. Chyba je na obě strany levná: nepoznané emoji
  se vykreslí normálně velké, symbol omylem považovaný za emoji se ukáže velký.
- **Počítají se shluky, ne kódové body.** Rodina (👨‍👩‍👧) je pět kódových bodů spojených ZWJ
  a vlajka dva regionální indikátory — pro uživatele je to jedno emoji. ASCII se za emoji nepovažuje,
  takže „:-P" zůstává normální zpráva (a klávesnicová emoji `1️⃣` se tím minou).
- **Samolepky: Coil umí jen `.webp`.** `.tgs` je gzipovaná Lottie animace, `.webm` video — ani
  jedno není obrázek. U nich se bere statický náhled, který TDLib k samolepce dává, a teprve když
  není ani ten, emoji. Náhled jen v rastrovém formátu: `ThumbnailFormatTgs` a `Mpeg4` mají tentýž
  problém, jen o úroveň níž.
- **`.tgs` přehrává Compottie** (`AnimatedSticker`). Gzip Compottie neumí, takže se soubor
  rozbalí přes Okio (`gzip()` funguje v commonMain, protože Okio má zlib pro JVM i native) a do
  Compottie jde až JSON. Do té doby i při chybě se kreslí statický náhled — odpovídá prvnímu
  snímku, takže přechod na animaci není skok z prázdna.
- **Compottie je `lite`.** Lite nemá JavaScriptový engine pro výrazy; samolepky Telegramu výrazy
  nepoužívají (oficiální rlottie je ani neumí). Compottie táhne Compose `foundation` tranzitivně,
  takže novější verze než náš Compose by ho tiše povýšila — před bumpem zkontrolovat její metadata.
- **Rozparsované animace mají vlastní cache (50), ne `LocalLottieCache` (10 na celou aplikaci).**
  V konverzaci se jedna samolepka opakuje a při scrollu by se jinak rozbalovala a parsovala
  znovu. Klíčem je cesta, ne MD5 obsahu jako u `JsonString` — hash rozbaleného JSONu by stál víc,
  než cache ušetří. Kvůli cestě jako klíči se cache po odhlášení zahazuje (`forgetStickerAnimations`).
- **Samolepky `.tgs` se kreslí MIMO hlavní vlákno** (`RenderedSticker` v `core/ui`, platí pro
  všechna `AnimatedSticker`: bublina, výběr, velké emoji, vlastní emoji, kostka, efekty).
  Compottie kreslí Lottie vektorově a celý výpočet snímku (klíčové snímky, cesty, matice) je
  Kotlin. Kreslený v kompozici přes `Image(rememberLottiePainter(…))` ho platilo hlavní vlákno:
  mřížka výběru měla medián snímku 18 ms a `Record View#draw` 12,9 s za 30 s scénáře. Teď se painter
  kreslí do bitmapy (`CanvasDrawScope` nad `Canvas(ImageBitmap)`) a hlavní vlákno bitmapu jen
  položí (`drawWithContent` + vlastní `graphicsLayer`, nový snímek tedy nesloží znovu kompozici).
  - **JEDNO vlákno pro všechny samolepky** (`Dispatchers.Default.limitedParallelism(1)`).
    Compottie 2.3.1 kreslení z víc vláken nesnese: ořez cesty (`CompoundTrimPath.kt`) sdílí
    globální `pathMeasure`, `tempPath` a `tempPath2`, na iOS a desktopu přechody sdílí `tmpMatrix`
    (`PlatformShader.skiko.kt`) a hlavně **vrstvy animace jsou objekty kompozice**
    (`CompositionLayer.compose` vrací `composition.animation.layers`), takže je sdílí každý painter
    téže samolepky z cache. Se třemi vlákny aplikace během vteřiny spadla nativně (SIGSEGV
    v `SkContourMeasureIter` pod `PathMeasure.setPath` z `applyTrimPath`). Proto jde přes to
    vlákno i jednorázové přehrání a poslední snímek: painter kreslený zároveň na hlavním vlákně
    by se se smyčkou potkal ve stejných globálech. Víc vláken by šlo jen s kompozicemi bez ořezu
    cesty (`"ty":"tm"` v JSONu) a s pevným vláknem pro každou kompozici.
  - **Další snímek až po vykreslení předchozího** (`StickerFrames.awaitDrawn`: kreslení zapíše
    vykreslený `tick`, smyčka na něj čeká). Samolepka, která se nekreslí — položka přednačtená
    mřížkou mimo viewport, aplikace na pozadí — po jednom snímku stojí a nepočítá nic. Hlídat
    viewport nebo lifecycle zvlášť proto není potřeba; položka mimo viewport navíc vypadne
    z kompozice i se smyčkou.
  - **Tři bitmapy na střídačku**, jako u videosamolepek: do jedné se kreslí, druhou kreslí UI,
    třetí může ještě nahrávat `RenderThread`. Vznikají líně — poslední snímek (kostka z historie)
    potřebuje jednu.
  - **Průběh se zapisuje uvnitř vlastního snímku stavu, který se zahodí** (`takeMutableSnapshot`
    + `dispose`). Painter čte průběh přes `derivedStateOf` (`LottiePainterImpl.frame`), takže to
    musí být stav — obyčejnou proměnnou by si odvozený stav zapamatoval napořád. Zápis do
    globálního snímku z pozadí by po každém snímku plánoval rozesílání změn na hlavním vlákně.
  - **Painter vzniká v kompozici** (`rememberLottiePainter` je jediná veřejná cesta, třída
    `LottiePainterImpl` je `internal`) a chvíli po složení je prázdný: nepřipravený hlásí
    `intrinsicSize` 1 × 1 a smyčka čeká, až bude skutečný. Jinak by místo náhledu na okamžik
    probleskla prázdná bitmapa.
  - **Čas běží podle hodin, ne podle snímků** (`StickerClip`): nestíhá-li vlákno, snímky se
    přeskočí a animace se nezpomalí. Po čekání na vykreslení se už nepřidává pevná pauza: dřív
    tam byly 4 ms a samolepka s 60 fps jela na 30, protože cyklus přesáhl jeden vsync.
  - **Snímková frekvence nejvýš 60, v mřížce výběru, řádku návrhů a u emoji v textu 30**
    (`REDUCED_STICKER_FRAME_RATE`), nikdy víc, než má animace sama — část `.tgs` má jen 30.
  - **Nanejvýš dvě samolepky se parsují naráz** (`Semaphore` v `TgsSpec.load`). Nová mřížka
    (přepnutá kategorie) jich chce desítky v jednom okamžiku a parsování JSONu alokuje megabajty:
    GC uvolňoval po 100 MB a vlákna čekala na blokující GC (`WaitForGcToComplete blocked Alloc`
    ~120 ms), hlavní vlákno taky. S omezením klesl nejdelší snímek z 400–600 ms na 100–170 ms.
  - **Vlákno stíhá ~850 snímků/s** (emulátor Pixel 7 API 36, ne-debuggable build, buňka
    168 × 168 px, ~0,9 ms na snímek): mřížka 25 samolepek po 30 fps ho vytíží na ~80 %. Na
    slabším telefonu snímky ubydou (samolepky se hýbou trhaněji), UI zůstane plynulé.
    V debug buildu je snímek ~2,5 ms — viz „Debug build zkresluje Kotlin" ve `vykon.md`.
  - **V ustáleném stavu Compottie alokuje ~20 KB na snímek** (GC po 24 MB zhruba co 2 s při 600
    snímcích za vteřinu). Pauzy jsou pod milisekundu; víc s tím bez zásahu do knihovny udělat nejde.
  - **Změřeno Perfettem** (scénář: otevřít výběr v Notifikátoru, dvě kategorie, scroll, dvakrát;
    jen tenhle emulátor, ne-debuggable build, `cmd package compile -m speed`): medián snímku hlavního
    vlákna 18,2 → 3,7–4,1 ms, p90 27 → 5,5–6,0 ms, snímků nad 16 ms 816–861 → 30–40, nad 33 ms
    36–40 → 4, `Record View#draw` 12,9 s → 0,5–0,7 s, čekání na blokující GC 400–613 → 152–230.
- **`.webm` (videosamolepky) hrají na Androidu** (`VideoSticker` → `WebmSticker`), na iOS
  a desktopu zůstávají náhledem (`webmStickersSupported`; soubor se tam ani nestahuje).
  - **Průhlednost NENÍ v VP9, ale druhý VP9 stream v kontejneru**: stopa má `AlphaMode = 1`
    a každý snímek je `BlockGroup`, jehož `BlockAdditional` (`BlockAddID = 1`) je VP9 snímek
    s průhledností v jasu. Dekóduje se proto DVĚMA obyčejnými dekodéry a výsledek se složí —
    libvpx přibalovat netřeba. Neprůhledné samolepky mají jen `SimpleBlock`. Ověřeno rozborem
    skutečných samolepek (PepeVideo bez průhlednosti, GIFdotWEBM, PandaVideo s ní).
  - **Kontejner čte vlastní `WebmDemuxer`** (commonMain, testy `WebmDemuxerTest`): systémový
    `MediaExtractor` `BlockAdditional` nevydá. Čte jen první video stopu VP8/VP9, bloky
    s lacingem přeskočí, poškozený soubor je `null`. Barevný prostor bere z `Colour`
    (`MatrixCoefficients = 1` → BT.709, `Range = 2` → plný rozsah); samolepky mají obojí.
  - **Dekóduje softwarový `MediaCodec`** (`c2.android.vp9.decoder`, starší `OMX.google`):
    hardwarových instancí je pár a patří videu. Dekodér běží v procesu `media.swcodec`, ne
    v aplikaci. Výstup jako `Image` (YUV 4:2:0, `COLOR_FormatYUV420Flexible`), převod na ARGB
    rovnou do velikosti, ve které se samolepka kreslí; průhlednost je jas druhého proudu bez
    převodu rozsahu. Posuny sloupců se počítají jednou za snímek — dělení v nejvnitřnější
    smyčce bralo znatelnou část procesoru.
  - **Smyčka**: po posledním snímku `END_OF_STREAM`, `flush` a znovu od prvního (jediného
    klíčového). `flush` až při DALŠÍM snímku — zneplatnil by obrázek, který se ještě převádí.
  - **Hraje nejvýš 10 naráz** (`Semaphore`, každá drží 1–2 instance kodeku). Další ČEKÁ ve
    frontě na náhledu a rozběhne se, až jiná odjede z obrazovky. Dřív `tryAcquire`: zamítnutá
    samolepka už nezačala nikdy, ani když se místo uvolnilo. Do limitu se počítají i bubliny
    v chatu pod otevřeným sheetem.
  - **Dekodér se zakládá jednou, až má plocha velikost** — klíčem efektu je „změřeno", ne
    velikost: buňky mřížky se o pixel přeměřují (171 ↔ 172) a každá změna dekodér zahodila.
  - **Kreslí se z trojice bitmap a nový snímek jen překreslí** (`drawWithContent` čte čítač
    snímků), kompozice se neskládá znovu. V pozadí (`repeatOnLifecycle(STARTED)`) se nedekóduje.
  - **Zátěž na emulátoru**: pár samolepek v chatu ~30 % aplikace (převod ~13 %, kreslení ~9 %)
    a ~10 % `media.swcodec`. Na skutečném telefonu změřené NENÍ.
  - **Vlastní emoji a efekty zpráv ve `.webm` zůstávají náhledem** — v textu jich bývá mnoho
    naráz a limit by je vyčerpal; patří k nim vlastní úvaha.
- **`RemoteImage` má parametr `background`.** Výchozí podložka `surfaceContainerHighest` je správně
  u fotky, ale u samolepky by z průhledného tvaru udělala obrázek v rámečku.
- **`captionText()` vrací u samolepky prázdno.** Její emoji se dřív vypisovalo jako text zprávy;
  vedle vykreslené samolepky je to zdvojení. V náhledu seznamu chatů a v citaci ale zůstává —
  tam není kam kreslit obrázek.
- **Emoji v čipu reakce je záměrně mimo typografickou škálu** (18 sp natvrdo). Role jako
  `labelLarge` míří na výšku písmen, ale u emoji je nosná celá kresba, takže ve stejné velikosti
  působí menší než číslo vedle ní.

### Efekty zprávy

Zpráva poslaná s efektem (`Message.effectId`: oheň, konfety, srdce…) má na volné straně vedle
spodku bubliny kolečko s emoji efektu (`EffectMark` v `items/MessageEffect.kt`) a animace se nad
ní přehraje: sama jednou u zprávy, která přišla po otevření chatu (`claimEffect`, stejná
tolerance jako hod kostkou) nebo je NEPŘEČTENÁ, a znovu po ťuknutí na značku. Převod
`toMessageEffect`, doména
`MessageEffect` v `ChatMessage.effect`.

- **Nepřečtená příchozí zpráva efekt přehraje taky** (`effectAutoplays`, testy
  `EffectAutoplayTest`): kdo ji ještě neviděl, má vidět i efekt, se kterým přišla — tak to dělají
  oficiální klienti. Rozhoduje hranice nepřečtených zachycená při otevření (`unreadBoundaryId`),
  vlastní zpráva se nepočítá. **Hranice dorazí až po prvních zprávách**, proto na ni efekt
  položky čeká (`snapshotFlow { scope.unreadBoundary() }`). **V kompozici se nečte** — viz
  „Recompozice za běhu" (Výkon a paměť): i přes `derivedStateOf` tím obal každé viditelné
  zprávy přeskládala každá změna stavu konverzace. Ověřeno sondou hranice
  (viz „Skok na první nepřečtenou"): obě nepřečtené zprávy s 🔥 zahořely po otevření chatu.
  Ověření bez sondy v #325 nic nedokazovalo — chat otevřený do pěti vteřin po zprávě spadl pod
  toleranci příchodu, ne pod nepřečtené. Efekt u nejnovější zprávy
  zčásti zajede pod vstupní pole — to leží nad seznamem.
- **Efekt se dočítá při převodu zprávy** (`getMessageEffect`, pamatuje se pro instanci klienta),
  jako jméno inline bota. Efektů je pár desítek a opakují se; `fileId` animace platí jen v jedné
  databázi.
- **Druhy jsou dva a animaci nese každý jinde**: efekt reakce `effectAnimation`, prémiová
  samolepka `premiumAnimation` své samolepky. `when` bez `else`.
- **Značka vedle zprávy, ne pod ní**: zpráva s efektem není vyšší a animace vybuchne z volného
  pruhu. Animace je dítě značky, které hlásí nulovou velikost, takže nic nepřeskládá.
- **Plátno animace je navržené pro ODCHOZÍ zprávu**: zdroj výbuchu leží asi ve ¾ šířky
  (`EFFECT_SOURCE_X`) a výbuch letí doleva. Na značku se kotví ten bod, ne střed plátna —
  vystředěný oheň stál o ~50 dp vedle značky. U příchozí zprávy se animace zrcadlí; bez zrcadlení
  letěla srdce doleva přes bubliny (ověřeno obojí na emulátoru).
- **Dokud efekt hraje, má položka `zIndex(1f)`.** Animace přesahuje zprávu a starší zprávy se
  v `LazyColumn` kreslí po ní — přikryly by ji. `zIndex` na kořeni položky pořadí kreslení mezi
  položkami mění.
- **Animace je krátká (kolem vteřiny)**: snímek o půl vteřiny po ťuknutí už nic nezachytí
  a vypadá to, že ťuknutí nefunguje. Ověřuje se sérií `screencap` v jednom `adb shell`.
- **Poslat s efektem jde z podržení odesílacího tlačítka** („Odeslat s efektem…" →
  `EffectPickerSheet`, `SendOptions.effectId` → `MessageSendOptions.effectId`). Ťuknutí na efekt
  zprávu rovnou pošle; v mřížce je jen emoji efektu, animace se ukáže nad odeslanou zprávou.
- **Podržení efektu ve výběru ho přehraje** (náhled), ťuknutí dál rovnou posílá. Animace je
  tatáž `EffectAnimation` jako u značky zprávy (kotvená zdrojem výbuchu, jako u ODCHOZÍ zprávy)
  a leží nad CELÝM sheetem, ne v políčku: mřížka ořezává ve směru scrollu a výbuch nad políčkem
  by uřízla. Kotví se ve středu podrženého políčka (`localPositionOf` vůči sheetu). Sheet sám
  ořezává svou plochu, takže co vyletí nad jeho horní hranu, vidět není — konfety u krátkého
  sheetu dosáhnou jen k nadpisu, a to na náhled stačí.
- **Jen v soukromém chatu, i s botem** (`SendMenuOffer.effects`, testy `SendMenuTest`) — jinde
  TDLib efekt nepřijme; v Uložených zprávách a ve vlákně komentářů se nenabízí.
- **Seznam posílá server JEDNOU po startu** (`UpdateAvailableMessageEffects`: efekty reakcí,
  pak prémiových samolepek), takže ho drží eager `ChatRepository.availableEffectIds`, jako
  emoji kostek. Efekty se dočítají až při otevření výběru (`sendableEffects`, tentýž keš jako
  u zpráv) — většina odeslání efekt nechce.
- **Prémiové efekty se bez Premium nenabízejí** (`MessageEffect.needsPremium`), stejně jako
  prémiové samolepky. Testovací účet jich dostane šest (👍 👎 ❤️ 🔥 🎉 💩).
- **Ověřuje se botem**: `sendMessage` s `message_effect_id` (🔥 5104841245755180586,
  🎉 5046509860389126442, ❤️ 5159385139981059251) do chatu s testovacím botem. Ověřeno na
  emulátoru: značky, samočinné přehrání po příchodu, přehrání ťuknutím, kresba nad staršími
  zprávami. Odeslání s efektem: bot přes `getUpdates` dostal `effect_id`, u odchozí zprávy
  vylétají konfety doleva do volného pruhu (zrcadlení sedí). **Neověřeno**: efekt prémiové
  samolepky (testovací účet Premium nemá) a iOS (jen přeložené).

### Efekt animovaného emoji pro oba

Ťuknutí na animované emoji (`MessageMedia.AnimatedEmoji`) přehraje kolem něj velký efekt
a v soukromém chatu ho uvidí i protějšek; jeho ťuknutí se zase přehraje u nás. Část ViewModelu
`ConversationEmojiBursts` (testy `ConversationEmojiBurstsTest`), kreslení `AnimatedEmojiImage`
v `items/AnimatedEmoji.kt`.

- **Posílá TDLib sám**: `clickAnimatedEmojiMessage` interakci odešle protějšku a vrátí samolepku
  efektu; 404 znamená, že emoji efekt nemá (pak jen poskočí). Protějškovo ťuknutí chodí jako
  `updateAnimatedEmojiMessageClicked` a má se přehrát, JEN když je zpráva vidět — a pak poslat
  `chatActionWatchingAnimations` s emoji. Protějšek to vidí jako „sleduje 🎉…" s okem
  (`ChatActivity.WatchingAnimation`, viz „„Píše…" v konverzaci" v `konverzace-psani.md`), u nás
  totéž, když sleduje on.
- **Efekt čeká na zprávu jen chvíli** (`FRESH_MILLIS`, 1,5 s): vyzvedne si ho položka na
  obrazovce (`EmojiBursts.shown`) a teprve tím se hlásí „sleduje". Zpráva mimo obrazovku ho
  nevyzvedne, a na kterou se odscrolluje až potom, ta už nevybuchne — efekt patří k okamžiku
  ťuknutí. Nové ťuknutí nahradí starší (`run` složí animaci znovu).
- **Odezva gesta je hned, efekt až s odpovědí TDLib**: emoji při ťuknutí poskočí a ozve se tep
  (`pulse()`, viz „Haptika" v CLAUDE.md); samolepka efektu se napoprvé stahuje, takže první
  efekt může přijít se zpožděním (na emulátoru se první ťuknutí nestihlo zachytit vůbec).
- **Efekt od protějšku tepe taky** — vědomá výjimka z pravidla, že cizí akce nevibruje: je to
  gesto člověka mířené na nás, přehrané před očima, a hmatová ozvěna je jeho půlka.
- **Plátno je 3× emoji, emoji leží u kraje** (jako v Telegram Desktop): u odchozí zprávy pravým
  krajem, výbuch letí doleva; u příchozí levým a zrcadlí se. Ověřeno: srdce z odchozí zprávy
  rozletí srdíčka doleva přes starší zprávy.
- **Obsah zprávy je ořezaný obrysem i bez bubliny** (`graphicsLayer { clip = true }` v `MessageBox`)
  a efekt nakreslený na místě se uřízl na hranici zprávy (první pokus na emulátoru). Kreslí se
  proto do `MessageOverlay` — vrstvy rámu mimo ořez, kam obsah předá kresbu a kotvu (střed emoji
  v souřadnicích rámu, `localPositionOf`). Kotva se počítá až při přehrání: pořadí `onPlaced`
  rámu a emoji zaručené není. Položka se po dobu efektu zvedne (`LocalRaiseItem`).
- **Kopie v nabídce na ťuknutí nereaguje** (`InertItemScope.emojiBursts() = null`).
- **Ověřeno na emulátoru** v chatu s testovacím botem (❤️ přes sdílení): poskočení, efekt mimo
  ořez, tep (`dumpsys vibrator_manager`: dva `constant=0` 142 ms po sobě). **Neověřeno**: efekt
  od protějšku a „sleduje animaci…" — bot interakci poslat neumí, chce to druhý účet. iOS jen
  přeložené.

### Vlastní emoji v textu

Vlastní emoji (`TextEntityTypeCustomEmoji`) je samolepka, která v textu stojí místo náhradního
emoji. Kreslí se jako inline obsah `RichText` (`CustomEmojiImage`, `customEmojiRanges`, testy
`CustomEmojiRangesTest`), data dává `CustomEmojiRepository` nad `getCustomEmojiStickers`.

- **Náhradní znak v řetězci ZŮSTÁVÁ**, jen se místo něj vykreslí obrázek. Inline obsah se
  připojuje anotací nad UŽ PŘIPOJENÝM úsekem (`addStringAnnotation` se značkou
  `INLINE_CONTENT_TAG`), ne `appendInlineContent` — ten by text přestavěl a posunul offsety
  všech entit, zvýraznění hledání i spoilerů. Značka je v Compose `internal`, a proto opsaná;
  `appendInlineContent` ji přidává přesně takhle.
- **Placeholder je čtverec 1,2 em** na střed řádku; než obrázek dorazí (a když ho server
  nevydá), kreslí se v něm náhradní emoji, takže se výměnou nic nepřeskládá.
- **V zakrytém spoileru se nenahrazuje** — obrázek by prozradil, co je skryté.
- **Dotazy se slučují do dávky** (16 ms, nejvýš 200 id na volání) a výsledek se pamatuje po
  dobu života klienta; `fileId` platí v jedné databázi, takže se po odhlášení zahazuje.
  Nevydané emoji se zapomene, ať se o něj příště řekne znovu.
- **`needsRepainting` = obarvit barvou textu** (`tinted`, `SrcIn` nad vlastní vrstvou) — na
  odchozí bublině by jednobarevné emoji jinak splynulo.
- **Formáty jako u samolepky**: `.tgs` hraje (Compottie), `.webp` a rastrový náhled jsou obrázek,
  `.webm` zůstane náhledem (videosamolepky hrají, vlastní emoji zatím ne), bez náhledu
  náhradním znakem.
- **Zpráva s vlastním emoji jde upravit jen tam, kde by šla poslat** (`customEmojiAllowed`
  ve stavu konverzace = `canUseCustomEmoji`: Premium, nebo Uložené zprávy). Jinde „Upravit"
  chybí: bez Premium by server vlastní emoji při úpravě tiše zahodil, tedy by ho úprava smazala.
  Viz „Vlastní emoji v poli" v `konverzace-psani.md`.
- **Bot vlastní emoji poslat nesmí** (Bot API je bez placeného uživatelského jména na Fragmentu
  potichu zahodí, v odpovědi pak `entities` chybí). Ověřuje se proto sondou: skutečná id vydá
  Bot API `getStickerSet` (sady `RestrictedEmoji`, `StatusEmoji`) a dočasně se přidají jako
  entity textu od testovacího bota v `FormattedText.toBody`. Necommituje se.
- **Ověřeno na emulátoru sondou**: animovaný `.tgs` uprostřed textu, emoji na konci řádku,
  zpráva jen z vlastních emoji ve velké sazbě, obarvení v tmavém motivu (podstrčené `tinted`).
  **Skutečná zpráva od člověka ověřená není.**

### Vlastní emoji mimo text

Reakce, emoji status za jménem, náhled v seznamu chatů, citace a ikona tlačítka bota. Všechno
jde přes tentýž `CustomEmojiImage`; samostatně stojící emoji je `CustomEmojiIcon`, emoji
v obyčejném `Text` (náhled, citace) `appendWithCustomEmoji` + `customEmojiInlineContent`.

- **Reakce vlastním emoji jsou řetězcový KLÍČ, ne nový typ** (`customEmojiReaction`,
  `customEmojiIdOf`, testy `ReactionKeyTest`). Reakce jdou jako `String` přes celou cestu —
  nabídka, optimistické přepnutí, prohlížeč médií, klíče v mřížce výběru — a všude jsou jen
  porovnávaným klíčem. Vědět o nich musí jen hranice TDLib (`toReactionKey` / `toReactionType`,
  `when` bez `else`) a kreslení; typ by měnil celou cestu kvůli dvěma koncům.
- **Nabídka reakcí vynechá `needsPremium`**, když účet Premium nemá — ťuknutí by skončilo chybou.
  `getMe` se volá, jen když taková reakce v nabídce je.
- **Náhradní znak reakce ani ikona tlačítka nenesou** (jen id), takže než obrázek dorazí, je
  místo prázdné. V textu drží místo náhradní znak.
- **Emoji status nese `Chat.emojiStatus`** a mění ho `UpdateChatEmojiStatus` — na rozdíl od
  ověření se na uživatele ani supergroupu ptát nemusí (`statusEmojiId`, testy
  `EmojiStatusTest`). Prošlý status se nekreslí i bez updatu: chat z databáze ho nese dávno
  prošlý. Vylepšený dárek se kreslí svým modelem (`modelCustomEmojiId`). Jednobarevný status má
  barvu akcentu, ne textu (`EmojiStatusBadge`), na vybraném chatu přes `accentOn`.
- **Kdo kreslí emoji statusy, musí v pruhu spamu upozornit, že status nic neověřuje**
  (`SpamBar.warnsEmojiStatus`). Dokumentace `chatActionBarReportSpam` / `ReportAddBlock`:
  „a notice about emoji status usage must be shown" — status jde koupit s Premium a u jména
  vypadá jako odznak. Proto přišlo upozornění v TÉMŽE PR jako statusy, ne později.
- **Jméno v profilu je v řádku se statusem, a kde hlavička končí, hlásí ŘÁDEK.** `onPlaced`
  na textu uvnitř řádku by dal pozici vůči řádku, ne vůči hlavičce — a lišta by ukázala název
  v nesprávnou chvíli.
- **Náhled v seznamu nese jen vlastní emoji a spoilery** (`previewEntities`,
  `ChatSummary.lastMessagePreviewEntities`, `ChatDraft.entities`). Rozsahy sedí, protože náhled
  bere text zprávy nebo popisek doslova; prázdný popisek („Fotka") entity nemá. Offsety se za
  prefixem „Koncept: " posunou samy (`appendWithCustomEmoji`, testy `AppendWithCustomEmojiTest`).
- **Citace kreslí vlastní emoji jen u celé zprávy.** Ruční úryvek entity nenese a offsety
  zprávy by do něj ukazovaly jinam.
- **V notifikaci zůstává náhradní znak** — notifikaci kreslí systém a vlastní obrázek do textu
  nevloží. Není to chyba, náhradní emoji je přesně to, co odesílatel k obrázku zvolil.
- **Bot nic z toho nevyrobí**: vlastní emoji v textu zahodí (viz výš), reakci vlastním emoji
  odmítne (`REACTION_INVALID`) a `icon_custom_emoji_id` Bot API sice vrátí v odpovědi, ale do
  zprávy se nedostane (TDLib hlásí `iconCustomEmojiId = 0`) — smí ho jen bot s Fragment jménem
  nebo majitelem s Premium. Ověřuje se proto sondou: vlastní emoji v `FormattedText.toBody`,
  reakce v `toReactions` a `availableReactions`, status v `toSummary` / `chatInfo` / `profile`
  a ikona v `toBotKeyboard`, vše pro chat s testovacím botem. Necommituje se.
- **Ověřeno na emulátoru sondou**: status v seznamu, hlavičce a profilu; vlastní emoji
  v náhledu, v citaci nad polem, v čipu reakce (i vybrané s počtem), v pruhu rychlých reakcí
  a ve výběru; ikona na tlačítku bota; upozornění na status v pruhu spamu (česky, podstrčeným
  pruhem). **Skutečná data od člověka ověřená nejsou**, stejně jako
  přidání reakce vlastním emoji (bez Premium ji server nabídne jen tam, kde ji chat povolil)
  a detail reakcí s vlastním emoji (kreslí ho tentýž `CustomEmojiIcon` jako čip).

### Emoji podle slov

Napsané slovo, ke kterému má TDLib klíčové slovo („srdce", „heart"), nabídne nad polem pilulku
s emoji (`EmojiSuggestionsPanel`, `EmojiSuggestionsViewModel`, pravidla `emojiWordAt`
a `emojiReplacement`, testy `EmojiSuggestionsTest`). Ťuknutí dosadí emoji MÍSTO slova a zařadí
ho mezi nedávná.

- **Přesná shoda, ne začátek** (`getKeywordEmojis`, `EmojiRepository.keywordEmojis`).
  `searchEmojis` hledá podle začátků a panel by vyskakoval u každého druhého slova.
- **Slovo u KURZORU, jen písmena, aspoň dva znaky, a celé** — kurzor uprostřed slova nic
  nenabídne. Za `@`, `#`, `/` a v adrese (`.`, `:`) to slovo není. S dotazem na inline bota,
  zmínkou nebo příkazem se nenabízí: to napovídání má nad polem přednost.
- **Jazyk zařízení a angličtina**, jako hledání ve výběru emoji; výsledky se zúží na katalog
  a dostanou zvolený odstín pleti.
- **Panel nad polem drží poslední sadu ve STAVU, ne v obyčejném poli.** Napovídání zmínek mělo
  `remember { arrayOf(…) }` a zápis do pole kompozici nevyvolá: obsah uvnitř
  `AnimatedVisibility` zůstal u první sady — po „hear" → „heart" dál 👂 🙉, přestože
  ViewModel už vracel srdce (ověřeno sondou v logu). Opraveno v obou panelech.
- **Pilulka PLAVE nad polem a místo v rozvržení nezabírá** — viz „Plovoucí napovídání nad
  polem" (Konverzace).
- **Ověřeno na emulátoru** v Uložených zprávách jen psaním: „heart" anglicky i „srdce" česky,
  přechod „hear" → „heart", ťuknutí dosadí emoji. Nic se neodeslalo.

## Výběr samolepek

Tlačítko se samolepkou ve vstupním poli otevře bottom sheet: záložky Nedávné, Oblíbené
a nainstalované sady, pod nimi mřížka. Ťuknutí samolepku pošle (i jako odpověď).

- **Než se samolepka načte, kreslí se její obrys** (`getStickerOutlineSvgPath`, `StickerOutline`
  ve `StickerPreview`, slot `placeholderContent` v `RemoteImage`) — tvar v `surfaceContainerHighest`
  místo prázdného místa, jako u oficiálních klientů. Platí všude, kde se samolepka kreslí přes
  `StickerPreview` (výběr, sada, bublina, karta dárku).
  - **Souřadnice jsou v rozměrech samolepky** (TDLib `get_sticker_outline_svg_path` se zoomem 1
    u běžných samolepek), takže se obrys zmenší stejně jako obrázek (Fit, na střed). Path parsuje
    Compose (`PathParser`); nečitelný nebo prázdný obrys = nic.
  - **Obrysy se pamatují v repository** (`TdlStickerRepository.outlines`): je to offline dotaz,
    ale mřížka se na stovky samolepek ptá při každém posunu znovu.
  - **Ověřeno na emulátoru** bez sítě (`svc wifi/data disable`): nedávné samolepky jako obrysy,
    po zapnutí sítě je samolepky nahradily přesně na jejich místě.

- **Bottom sheet, ne panel místo klávesnice.** Výměna klávesnice za panel tak, aby vstupní pole
  neposkočilo, je v Compose nejkřehčí část — sheet ji nepotřebuje. Klávesnice se před otevřením
  zavře, jinak by se se sheetem přetahovaly o spodek obrazovky.
- **Hranice je `StickerRepository`** (vlastní, ne kus konverzace): seznamy jsou živé toky, které
  se na update **čtou znovu** — updaty nesou jen id sad a souborů. Odběr stojí před prvním čtením.
- **Záložky jsou stránky `HorizontalPager`u** — mezi sadami se swipuje do stran. Řádek záložek
  a pager sdílejí `PagerState`: swipe přepne záložku (`currentPage` už v půlce tahu), ťuknutí
  na záložku posune pager. Počet stránek je lambda, protože sady dorazí až po nedávných
  a oblíbených; klíč stránky je záložka, ne index, aby se stránkám nerozsypal scroll.
- **Sada skupiny je záložka hned za Oblíbenými** (`StickerTab.Group`, `stickerTabs`,
  `StickerRepository.groupStickerSet`: `SupergroupFullInfo.stickerSetId`), jen v té supergroupě
  a i nenainstalovaná. Ikona je **fotka skupiny ve tvaru avataru skupiny**, ne obálka sady:
  obálka by vypadala jako další nainstalovaná sada. Má-li ji uživatel i nainstalovanou, mezi
  sadami se znovu neukáže; nainstalovanost pro výsledky hledání proto nese stav zvlášť
  (`StickerPickerState.installedSetIds`), ze záložek se vyčíst nedá.
  - **Výběr na sadu skupiny čeká** (`loaded` až po dotazu, strop 1,5 s): záložka přibývající
    před Trendy by posunula indexy a obnovení poslední záložky by trefilo vedle. Dotaz bývá
    lokální, plné údaje skupiny už načetla konverzace.
  - Nastavuje se v úpravě skupiny (viz „Sada samolepek skupiny" v profil-chatu.md).
  - Klíč poslední záložky je `group:<id>`, ne `set:<id>` — v jiném chatu se tatáž sada ukáže
    jako obyčejná, nebo vůbec, a výběr pak začne Nedávnými.
  - **Ověřovací skupina:** z testovacích skupin sadu má jen Android Developers (@android_ru,
    `-1001055466536`), účet v ní ale NENÍ — ověřovalo se dočasnou náhradou chatu ve výběru,
    ne odesláním. Malá skupina sadu nastavit nemůže (`canSetStickerSet = false`).
- **Výběr se otevře na záložce, na které uživatel naposledy dosedl** (`AppSettings
  .lastStickerTab`, klíč `StickerTab.storageKey()`: `recent`, `favorites`, `trending`,
  `set:<id>`), i po restartu. Kdo posílá z jedné sady, nemusí k ní pokaždé dojíždět.
  - **`PagerState` vzniká až s CELÝM seznamem záložek** (`StickerPickerState.loaded`,
    `StickerTabsPager`) a rovnou s `initialPage`. První stav má jen Nedávné, Oblíbené a Trendy;
    pager založený na něm by se otevřel na Nedávných a na sadu pak přeskočil. Čekání je dotaz
    do lokální databáze TDLib a místo drží indikátor ve stejně vysoké ploše. Na emulátoru byl
    výběr na sadě už v prvním snímku vyjíždějícího sheetu, řádek záložek posunutý k ní.
  - **Ukládá se USAZENÁ stránka** (`settledPage`), ne každá, přes kterou prst přejel, a sada
    se ukládá podle id — pořadí sad se mění. Odebraná sada se nenajde a výběr začne Nedávnými.
  - Klíč stránky pageru je tentýž `storageKey()` — jeden klíč záložky, ne dva skoro stejné.
- **ViewModel drží obsah VŠECH záložek**, ne jen vybrané — při swipu je vidět i sousední stránka.
- **Obsah sady se načítá až když se její stránka poprvé ukáže** (`load` v `LaunchedEffect` stránky;
  pager skládá jen viditelné). Sad bývá desítky.
- **Mřížka JEDNÉ sady hraje celá** (`StickerCells.playing`): stránka záložky (sada, nedávné,
  oblíbené), výsledky hledání i sheet sady. Na obrazovce je tak nejvýš pár desítek samolepek
  (buňka 76 dp, mřížka 320 dp: kolem pěti řádků po pěti) a `LazyVerticalGrid` skládá jen
  viditelné buňky. Dřív mřížka nehrála vůbec a hýbala se jen podržená samolepka.
- **Mřížka hraje hned, i během vyjíždění sheetu a swipu mezi sadami.** Dokud se samolepky
  kreslily na hlavním vlákně, mřížka se rozhýbala až ve vysunutém sheetu, při zasouvání zamrzla
  (`SheetStickerMotion`) a v pageru hrála jen usazená stránka. S kreslením mimo hlavní vlákno
  (viz výš) to nic měřitelného nepřináší, a tak to zmizelo i s parametry `held`, `paused`
  a `load`. Změřeno Perfettem (ne-debuggable build): třikrát otevřít a zavřít výběr — snímků nad
  16 ms 61 se zadržením, 29 bez něj, nejdelší 75 → 33 ms; pět swipů mezi sadami — nad 33 ms
  12 se zadržením, 7 a 13 bez něj. Zbylé drhnutí swipu je skládání nové stránky, ne Lottie.
  - **Placeholder (statický náhled) zůstává na STEJNÉM místě stromu**, dokud není první snímek
    animace (`RenderedSticker` ho kreslí jako obsah svého `Box`u), takže se po načtení animace
    neskládá znovu jinde.
  - **`gfxinfo` na plynulost nestačí**: procenta zaseknutých snímků se počítají z vykreslených
    snímků a verze, která jich kvůli práci na hlavním vlákně vykreslí zlomek, vypadá lépe.
    Porovnává se Perfettem.
- **Řádek záložek je líný** (`StickerTabRow`: M3 `Tab`y v `LazyRow`), ne `PrimaryScrollableTabRow`.
  Ten skládá a měří záložky VŠECH sad naráz a záložka sady nese obálku (`RemoteImage`): s 32
  sadami to bylo ~106 ms jen skládání v prvním snímku sheetu, tedy zdržení, než se sheet pohne.
  Zbytek řádku je napodobený podle M3 (zdroje material3 1.12): záložka aspoň
  `ScrollableTabRowMinTabWidth` (90 dp), oddělovač, indikátor 24 dp × 3 dp v `primary` (M3 bere
  šířku obsahu bez 2 × 16 dp odsazení textu a nejméně 24 dp) a vybraná záložka posunutá doprostřed;
  posun i jízda indikátoru `defaultSpatialSpec`, jako v M3.
  - **Indikátor se kreslí přes řádek a polohu záložek čte z `layoutInfo` až při kreslení**, jako
    index se zlomkem (`Animatable`) — mezi záložkami jede plynule a s posunem řádku jde bez
    rekompozice. Záložka mimo obrazovku se dopočítá od nejbližší viditelné (záložky jsou stejně
    široké). `LazyListItemInfo.offset` se počítá od začátku obsahu, ne kontejneru: poloha
    v řádku je `beforeContentPadding + offset`.
  - **Vystředění čeká na první rozměření** (`snapshotFlow { visibleItemsInfo.isNotEmpty() }`):
    v prvním snímku `layoutInfo` nic nezná a výběr otevřený na poslední sadě by ji nechal u kraje.
    Napoprvé skokem, potom animací.
  - Změřeno Perfettem (emulátor, debug build, 32 sad): první snímek sheetu 456–642 ms → 388–439 ms,
    skladba obsahu sheetu ~127 → ~43 ms. Zbytek je obsah, který vidět JE (~20 buněk mřížky
    ~41 ms, viditelné záložky a kategorie), a měření a kreslení nového okna; odložit ho by znamenalo
    skládat během pohybu, tedy zpátky to, čemu se tu předešlo.
- **Kde je vedle sebe víc sad, samolepky dál NEHRAJÍ** (`StickerPreview(playing = false)`):
  obálky v řádku záložek a ukázky v trendech. Bere se nejlevnější podoba: rastrový náhled,
  statická `.webp`, a teprve bez nich první snímek `.tgs`.
- **Snímkovou frekvenci řídí vlastní hodiny (`StickerClip`), ne Compottie.** Animační API
  Compottie se nepoužívá vůbec; kdyby se k němu někdo vracel: **`useCompositionFrameRate`
  NEPOUŽÍVAT** jako úsporu pro mřížku. Vypadá jako
  „hraj v rytmu samolepky, ne 120Hz displeje", ale Compottie 2.3.1
  (`LottieAnimatable.roundToCompositionFrameRate`) zaokrouhluje PRŮBĚH 0..1 na krok `1/fps` —
  tedy na šedesátiny celé animace, ne snímky za vteřinu. Třívteřinová samolepka by jela
  dvaceti snímky za vteřinu.
- **Podržení otevře velký náhled** (`StickerPreviewDialog`, `StickerPreviewHost`): samolepka
  240 dp a pod ní Poslat, hvězdička oblíbených a volby odeslání. Buňka má 76 dp a samolepka se
  v ní dá jen tušit; dřív podržení samolepku jen podbarvilo a v rozích buňky vyskočila 28dp
  tlačítka, na která se špatně trefovalo a která samolepku zakrývala. Podržení nic neposílá
  (`combinedClickable` po dlouhém stisku klik nevyvolá). Buňka pod náhledem zůstává
  podbarvená (`activeStickerId`), ať je vidět, odkud samolepka přišla.
  - **`Dialog`, ne vrstva v sheetu** — náhled má být větší než mřížka a sheet je vysoký jen
    kolem 370 dp. Dialog leží nad sheetem a ztmavení mu dává okno.
  - **Odchod se dohraje, než dialog zmizí** (`MutableTransitionState`, `onDismiss` až po
    `isIdle`). Poslat ho zavře bez animace — zavírá se i sheet a náhled odjede s ním.
  - **Hvězdička náhled nezavírá**; stav se přepíše, až ho potvrdí `favoriteStickersUpdates`.
  - **Hlavní tlačítko má popisek od volajícího** (`pickLabel`): ve formuláři ankety se
    samolepka nevysílá, ale vybírá („Vybrat").
  - **Prstem po mřížce se mezi náhledy nepřejíždí** (jako v Telegramu). Náhled zůstává otevřený
    i po puštění, protože se v něm ťuká na tlačítka.
- **Sheety výběru a sady nemají napůl rozbalený stav** (`enabledValues = Hidden, Expanded`).
  Jejich obsah je vyšší než polovina obrazovky a napůl rozbalený sheet schoval spodek pod okraj —
  tlačítko „Odebrat sadu" skončilo pod navigační lištou. V sheetu SADY řeší spodní inset
  výchozí `BottomSheetDefaults.modalWindowInsets` — je tam tlačítko a to nad lištou stát musí.
- **Sheet výběru (emoji, samolepky, GIFy) si z insetů bere jen stavovou lištu a klávesnici**
  (`expressionSheetInsets`), navigační lištu dostanou seznamy do `contentPadding`
  (`sheetListPadding`). S výchozími insety byla lišta odsazením celého obsahu: mřížka končila
  ostrou hranou nad ní a pod ní zůstal prázdný pruh sheetu. Plochy s pevnou výškou jsou o lištu
  vyšší (`sheetBottomInset`), aby viditelná část zůstala stejná. **Klávesnici seznamům nechat
  nejde** — mřížka má pevnou výšku a skoro celá by zmizela pod ní; proto `navigationBars
  .exclude(ime)`: s otevřenou klávesnicí lištu kryje ona a její místo drží sheet.
- **Mřížka má pevnou výšku**, jinak by se sheet při přepnutí záložky natahoval a smršťoval.
- **Hledání má dva zdroje.** `getStickers` prochází nainstalované sady podle emoji i podle
  klíčových slov samolepek; `searchStickers` veřejné sady, ale jen podle emoji. Slovo se proto
  převádí na emoji přes `getKeywordEmojis` (potřebuje `useFileDatabase = true`) v jazyce zařízení
  a angličtině — klíčová slova samolepek bývají anglicky. Bez převedených emoji se veřejné hledání
  vynechá, vrátilo by nahodilé samolepky. Jestli je dotaz emoji, nebo slovo, rozhoduje ViewModel
  (`emojiClusterCount`), repository to dostane jako `StickerQuery`.
- **Hledá se po 300 ms pauze v psaní, přes `transformLatest`**: rozepsané hledání se při dalším
  znaku zahodí, takže pomalá odpověď na „ko" nepřepíše výsledky pro „kočka".
- **Zdroj, který selže, výsledek nezahodí** — bez sítě se aspoň najde, co je ve vlastních sadách.
- **Výsledky hledání nahradí záložky ve stejně vysoké ploše** (řádek záložek má proto pevnou
  výšku), jinak by sheet při prvním písmenu poskočil. Co se ukazuje, rozhoduje text v poli, ne
  stav hledání — po smazání textu se záložky vrátí hned, bez čekání na debounce.
- **Slovo hledá i sady podle názvu** (`searchInstalledStickerSets` + `searchStickerSets`; ta
  druhá nainstalované sama vynechá), souběžně se samolepkami. Emoji a kategorie sady nehledají —
  názvy sad emoji nemají. Sady jsou ve výsledcích **nahoře, ale jen dvě** a zbytek za tlačítkem
  „Všechny sady": samolepek z vlastních sad bývá ke slovu přes stovku a sada pod nimi by byla
  k nenalezení (napřed stály mezi samolepkami a „duck" je na emulátoru zahrabal o mnoho obrazovek
  níž). Řádek sady je tentýž jako v Trendech (`StickerSetRow`, data `StickerSetSample`),
  ťuknutí otevře detail nad výběrem. **Nainstalovanost se čte ze záložek** (živý seznam), ne
  z výsledku hledání — ten je jednorázový a přidaná sada by dál nabízela Přidat.
- **Kategorie pod polem hledání** (`getEmojiCategories(EmojiCategoryTypeRegularStickers)`):
  každá je jen seznam emoji, ťuknutí hledá jako `StickerQuery.Emoji` bez debounce a bez
  klávesnice, druhé ťuknutí hledání zruší, psaní kategorii zruší. Prémiová kategorie
  (`EmojiCategorySourcePremium`) se vynechává.
  - **Čip nese JEN ikonu.** Názvy server lokalizuje podle jazykového balíčku Telegramu
    (`language_pack_id`), který aplikace nenastavuje, a v české aplikaci přišlo „Love",
    „Approval". Název zůstává popisem pro čtečku.
  - **Ikona je jednobarevné vlastní emoji (`needsRepainting`)** — Telegram ho kreslí bíle,
    takže se obarví barvou obsahu čipu (`Modifier.tinted` z `CustomEmoji.kt`).
  - **Náhled ikony je PRÁZDNÝ** (průhledná webp, 126 B, ověřeno vytažením souboru ze zařízení)
    a animace se kreslí z prázdna, takže i první snímek `.tgs` je prázdný. Ikona proto stojí na
    POSLEDNÍM snímku (`StickerPlayback.LastFrame`) a po výběru se jednou přehraje (`Once`).
    S náhledem nebo prvním snímkem byly čipy prázdné a nic nehlásilo chybu.
- **Oblíbené se přepínají hvězdičkou v NÁHLEDU podržené samolepky**, ne u každé buňky. Hvězdička
  všude by zaplevelila mřížku a ťuknutí vedle ní by samolepku omylem poslalo; podržení je zároveň
  „prohlížím si tuhle". Stav se lokálně nepřepisuje, potvrzení přijde `favoriteStickersUpdates`.
- **Z Nedávných se odebírá v NÁHLEDU podržené samolepky** (`removeRecentSticker`, ikona
  `history_toggle_off`), stejně jako se tam přepínají oblíbené. Tlačítko je u každé samolepky,
  která v nedávných je (`StickerCells.recentFileIds`), i když byla podržená ve výsledcích
  hledání nebo v sadě; v detailu sady otevřeném z konverzace ne, tam se nedávné nesledují.
  **Odebrání náhled zavře** (hvězdička ne): samolepka pod ním z mřížky odejde a náhled by visel
  nad ničím. Bez potvrzení — příští odeslání ji vrátí. Stav se lokálně nepřepisuje, potvrzení
  přijde `recentStickersUpdates`.
- **„Vymazat" je nahoře v Nedávných** (položka přes celou šířku mřížky, jen u neprázdného
  seznamu) a ptá se: seznam je v cloudu, zmizí na všech zařízeních účtu a vrátit nejde.
- **Buňky mřížky mají `animateItem`** — odebraná samolepka plynule zmizí a ostatní se dosunou.
  Průhlednost přes effects spec, posun přes spatial. **Mřížka zůstává i prázdná** a prázdný text
  se nad ní jen objeví: kdyby ji prázdný stav nahradil, zmizela by i s poslední samolepkou dřív,
  než ta stihne odejít animací.
- **V konverzaci jsou oblíbené v nabídce nad zprávou se samolepkou.** Jestli je samolepka
  oblíbená, se zjistí při otevření nabídky (souběžně s ostatními příznaky). Výsledek hlásí
  snackbar (`notice`) — v konverzaci se jinak nic viditelného nezmění.
- **Samolepka se do oblíbených dává podle souboru** (`stickerFileId`), ne podle id samolepky —
  tak to TDLib chce (`addFavoriteSticker(InputFileId)`). `MessageMedia.Sticker` ho proto nese
  vždycky, bez ohledu na formát. Oblíbených je omezený počet; přidání nad limit vytlačí nejstarší.
- **Ťuknutí na samolepku v konverzaci otevře její sadu** (`StickerSetSheet`): celá sada, přidat /
  odebrat (`changeStickerSet`), mřížka se chová jako ve výběru. Klikání na samolepce je **bez vlnky**
  (průhledný tvar bez bubliny) a opakuje dlouhý stisk kvůli nabídce. Sada se nainstalovanost
  dozví z `installedStickerSetsUpdates` — jiný update o sadě TDLib nepošle; tlačítko je mezitím
  neaktivní, aby nešlo zmáčknout dvakrát. Oficiální sady odebrat nejdou. ViewModel sady má klíč
  s id sady, stejně jako konverzace.
- **Trendy jsou záložka hned za Oblíbenými** — na konci za desítkami sad by je nikdo nenašel.
  Místo mřížky mají seznam sad s ukázkou a tlačítkem Přidat; ťuknutí na sadu otevře její detail
  jako **sheet nad výběrem** (samolepka poslaná z detailu zavře oba). Trendy jdou na server, proto
  mají vlastní tok s `WhileSubscribed` mimo stav výběru a načtou se až při zobrazení stránky.
  Přepočítávají se i na `installedStickerSetsUpdates` — přidání sady trendy samy nehlásí. Po
  zobrazení se označí za zhlédnuté (`viewTrendingStickerSets`).
- **Prémiové samolepky se bez Premium nenabízejí** (`StickerFullTypeRegular.premiumAnimation`).
  Odeslat je nejde a samolepka, po které přijde chyba, je horší než žádná. Na Premium se ptá
  `getMe` při každém čtení — předplatné se mění.
- **Posílá se soubor samolepky** (`InputFileId`), ne náhled; náhled se nenahrává, samolepka už
  na serveru je. Po odeslání se výslovně přidá mezi nedávné, aby byla hned nahoře.
- **Sheet se nejdřív schová, teprve pak se pošle.** Zmizení bez animace vypadá jako pád.
- **Samolepka nezakládá let ze vstupního pole** a nemaže rozepsaný text — z pole nevyletěla.
- **Změnu sady hlásí `UpdateStickerSet`** (`StickerRepository.changedSets`): obsah sady
  načtený ve výběru se přečte znovu, detail sady a záložky (název, obálka) taky. Bez toho zůstala
  sada, do které autor přidal samolepku, stará až do odchodu z chatu (výběr žije s konverzací).
  - **Chodí jen u sady, kterou TDLib načetl CELOU a klientovi už poslal, a jen při skutečné
    změně** (`StickersManager::update_sticker_set`: `is_changed_ && was_loaded_ &&
    was_update_sent_`). Načtení sady ani přidání sady z trendů (zná jen ukázku) ho nevyvolá —
    obnova po updatu se tedy nezacyklí. Ověřeno na emulátoru dočasným výpisem: otevření výběru,
    záložek sad i trendů nic, odebrání celé načtené sady v jejím detailu update poslalo a dorazil
    do repository i do ViewModelu výběru.
  - **Neověřené:** sada, do které autor opravdu přidal samolepku (testovací účet žádnou vlastní
    nemá; založit ji přes bota by byla veřejná sada).
- **Ikony Material Symbols ze SVG mají souřadnice y v -960..0.** Ve vector drawable se to řeší
  `group` s `translateY="960"`, cesta zůstává, jak je.

## Sdílení sady

V detailu sady (`StickerSetSheet`) je vedle názvu tlačítko sdílení s nabídkou: Poslat do chatu,
Kopírovat odkaz, Sdílet jinam. Odkaz je `t.me/addstickers/<name>` (`stickerSetLink`,
`StickerSetDetail.name`) a aplikace ho sama otevírá, takže kruh se uzavře i u příjemce
s Plume (ověřeno: odkaz poslaný do Uložených zpráv po ťuknutí otevřel tutéž sadu).

- **Poslat do chatu jde přes `ShareRequests`** — tentýž výběr „Komu: …" jako sdílení z jiné
  aplikace. Sheet se před tím zavře, výběr chatu se otevře nad aplikací. Odkaz u příjemce
  dostane náhled sady.
- **Sdílet jinam** = `MediaSharer.shareText` (systémový dialog), jako odkaz pozvánky.
- **Hláška o zkopírování jen tam, kde ji systém nedá sám** (`TextClipboard.confirmsCopy`):
  Android 13+ ukáže vlastní okénko s obsahem schránky vlevo dole a snackbar „Odkaz
  zkopírován" se s ním překrýval (ověřeno na emulátoru). Na iOS a starším Androidu hláška
  zůstává.
- **Snackbar je UVNITŘ sheetu** (`SnackbarHost` v `Box` kolem obsahu): sheet má vlastní
  okno a snackbar obrazovky pod ním by zůstal schovaný.

## Správa sad v Nastavení

Nastavení → Obecné → Chaty → Sady samolepek (`StickerSetsRoute`, `StickerSetsScreen`,
`StickerSetsViewModel`, testy `StickerSetsViewModelTest`). Vrší se nad sekci Obecné jako složky
chatů a stejně se v ní přeřazuje.

- **Pořadí sad je pořadí záložek ve výběru** a platí na všech zařízeních účtu
  (`reorderInstalledStickerSets`). Přeřazuje se jen úchytem (`rememberDragReorderState`, jako
  složky), ťuknutí na řádek otevře detail sady. Pořadí odchází až po puštění, jedním voláním.
- **Lokální pořadí vydrží do potvrzení serverem**, ne jen do návratu volání: po
  `reorderInstalledStickerSets` přijde `updateInstalledStickerSets` o chvilku později a seznam by
  mezitím skočil do starého pořadí. Zahodí se, až server pošle totéž pořadí (pojistka 3 s).
- **Archivovat a Odebrat jsou v nabídce řádku** (Odebrat ve vlastní skupině chybovou barvou,
  u oficiálních sad chybí) a neptají se — nic se nesmaže, snackbar nabídne „Vrátit".
- **Vrácená sada se zařadí NA ZAČÁTEK** (ověřeno na emulátoru: „Přidat" u archivované sady ji
  dal první). „Vrátit" proto po vrácení pošle i pořadí z doby před změnou
  (`StickerSetNotice.orderBefore`); bez toho by „Vrátit" rozbilo pořadí. „Přidat" v sekci
  Archivované pořadí neobnovuje — sada tam nemá „své" místo.
- **Archiv = `changeStickerSet(isInstalled = false, isArchived = true)`**, nainstalovaná
  a archivovaná zároveň sada být nemůže. Archivované se čtou po stránkách po 100
  (`getArchivedStickerSets`, víc TDLib nevrátí) a první stránka znovu po každé změně
  nainstalovaných. Počet od serveru je přibližný, prázdná stránka znamená konec.
- **Detail sady z Nastavení je jen na prohlížení** (`StickerSetSheet(onPick = null)`): ťuknutí
  otevře velký náhled a ten nemá Poslat (`pickLabel = null`) — v Nastavení není kam.
- **Ověřeno na emulátoru**: přetažení sady o řádek (pořadí drželo i po odchodu a návratu)
  a zpět, Archivovat + „Vrátit" (sada zpět na třetím místě), detail sady bez Poslat,
  „Přidat" v Archivovaných. Odebrat jen přes testy ViewModelu.

### Sady emoji v Nastavení

Nastavení → Obecné → Chaty → Sady emoji (`EmojiSetsRoute`) je TÁŽ obrazovka jako sady samolepek
(`StickerSetsScreen(kind = StickerSetKind.Emoji)`), jen nad jiným seznamem: Telegram drží pořadí
i archiv každého druhu zvlášť, takže repository bere druh (`installedSets(kind)`,
`reorderSets(…, kind)`, `archivedSets(…, kind)`) a ViewModel ho dostává parametrem (klíč
`emoji-sets` / `sticker-sets`, ať se dva ViewModely nepletou).

- **Vlastní emoji se kreslí přes `CustomEmojiImage`, ne jako samolepka** (obálka řádku
  `StickerSetSummary.coverCustomEmojiId`, buňka detailu `StickerItem.customEmojiId`):
  jednobarevná emoji jsou bílá a obarvit je umí jen ono — jako samolepka by na světlém pozadí
  zmizela.
- **Detail sady emoji nemá náhled podržením**: jeho hvězdička patří oblíbeným SAMOLEPKÁM.
  Počet je „N emoji" (`StickerSetDetail.isCustomEmoji`, plurál `emoji_count`).
- **`stickerSetDetail` se přepočítá po změně nainstalovaných sad JAKÉHOKOLI druhu** — filtr na
  běžné samolepky by u sady emoji tlačítko Přidat/Odebrat nikdy neodemkl.
- **Ověřeno na emulátoru**: dvě sady (Duck a dočasně přidaná Crayons), přeřazení drželo po
  odchodu z obrazovky, detail sady s obarvenými emoji a „104 emoji", odebrání Crayons.

## Tvorba samolepky z fotky

Z fotky jde udělat samolepku a přidat ji do vlastní sady, nebo sadu založit
(`StickerComposerScreen`, `StickerComposerViewModel`, `StickerMaker`). Vstupy jsou dva:
⋮ v prohlížeči fotky („Vytvořit samolepku") a Nastavení → Chaty → Sady samolepek →
„Vytvořit samolepku z fotky" (systémový výběr fotek). **Všechny tři platformy**
(`AndroidStickerMaker`, `IosStickerMaker`, `DesktopStickerMaker`), odstranění pozadí jen Android
a iOS. Rozměry, obrys, práh alfy a obálka jsou pro všechny v `StickerGeometry.kt` (commonMain),
ať se nerozejdou; kreslení a WebP ve Skie mají iOS a desktop společné v `SkiaStickers.kt`
(source set `skiaMain`).

- **Telegram chce delší stranu přesně 512 px a kratší nejvýš 512** (`stickerSize`),
  statickou samolepku do 512 kB. `uploadStickerFile` / `NewSticker` bere WebP i PNG, z PNG si
  WebP udělá server. Ukládá se **ztrátové WebP s průhledností** (kvalita 90): PNG fotky
  v 512 px tu hranici snadno přeleze.
- **Pozadí odstraňuje ML Kit Subject Segmentation z Play služeb** (`AndroidStickerMaker`,
  `enableForegroundBitmap`): popředí přijde jako bitmapa s průhledností a ořízne se na
  neprůhlednou část (práh alfy 16, vyhlazené okraje masky se nepočítají). Bez Play služeb
  se přepínač nenabízí a samolepka zůstane s pozadím.
  - **Model se stahuje** jako u čtení textu: meta-data `subject_segment` vedle `ocr`
    v manifestu (Play služby ho stáhnou po instalaci). Kdo zkusí samolepku dřív, dostane
    `MlKitException.UNAVAILABLE` → `StickerModelNotReadyException`, věta „ještě se stahuje"
    a „Zkusit znovu"; zároveň se model vyžádá hned (`ModuleInstallClient.installModules`).
    Ověřeno: po čerstvé instalaci na emulátoru první pokus hlášku dal, za minutu prošel.
  - **Beta** (`play-services-mlkit-subject-segmentation:16.0.0-beta1`) — jiná verze není.
- **Bílý obrys** (přepínač „Bílý obrys" pod odstraněním pozadí, výchozí zapnutý; `outline`
  v `StickerMaker.make`) jako u samolepek z oficiálního klienta. Nabízí se jen s odstraněným
  pozadím — kolem celé fotky by to byl jen rámeček.
  - **Objekt se zmenší tak, aby se i s obrysem vešel do 512 px**, a obrys se kreslí až
    v hotové velikosti: má tak vždycky 16 px z 512 (`OUTLINE_WIDTH`), ať byla fotka jakkoli
    velká. 12 px na emulátoru vypadalo vedle oficiálního klienta tence.
  - **Obrys je rozšířená maska objektu**: maska (`extractAlpha`) se nakreslí bíle mnohokrát
    posunutá po třech soustředných kružnicích a objekt přijde navrch. Jen jedna kružnice by
    kolem tenkých výběžků (špička zobáku) nechala mezeru mezi objektem a obrysem.
  - **Do masky jdou jen pixely nad prahem alfy** (jako u ořezu) — téměř průhledné zbytky po
    segmentaci by jinak dostaly vlastní bílé skvrny.
  - **ALPHA_8 se neplní z bufferu**: řádek takové bitmapy může být zarovnaný na víc bajtů než
    šířka; maska se proto staví v ARGB a převede `extractAlpha`.
- **Náhled je hotový soubor**, ne odhad: s každou změnou (úprava, pozadí) se samolepka vyrobí
  znovu a nahraje se přesně ta, kterou uživatel vidí. Šachovnice pod ní ukazuje průhlednost.
- **Úprava fotky je editor příloh** (`PhotoMarkupEditor` + `PhotoMarkupRenderer`): ořez,
  otočení, kresba. Úprava se drží k originálu, jako u přílohy, takže jde vzít zpátky.
- **Jméno sady (odkaz `t.me/addstickers/…`) navrhne Telegram** (`getSuggestedStickerSetName`
  z názvu) — ruční by mohlo kolidovat. Název zadává uživatel, výchozí žádný: text složený ve
  ViewModelu by po změně jazyka zůstal ve starém.
- **Nová sada se sama nenainstaluje.** Po `createNewStickerSet` se do výběru dostala jen do
  restartu aplikace (update), pak v seznamu nainstalovaných chyběla — ověřeno na emulátoru.
  Repository ji proto hned nainstaluje (`changeStickerSet(isInstalled = true)`), a totéž při
  každém přidání do vlastní sady (mohla být odinstalovaná).
- **Výchozí cíl je první vlastní sada, kam se vejde** (`getOwnedStickerSets`, jen běžné
  samolepky, ne emoji); plná (120 samolepek) se nabízí s „Plná" a vybrat nejde. Bez vlastních
  sad „Nová sada".
- **Emoji**: řada šestnácti nejčastějších a pole „Jiné emoji" pro klávesnici; bez emoji
  přidat nejde (`NewSticker.emojis` je povinné, 1–20 emoji).
- **Fotka z chatu se zákazem ukládání samolepkou být nesmí** — nabídka se řídí stejným
  `canSave` jako „Text z fotky" a popis fotky.
- **Esc (klávesnice emulátoru) je zpět** a obrazovku zavře i s rozepsaným formulářem;
  klávesnici zavírá „Hotovo" (`ImeAction.Done`).
- **Ověřeno na emulátoru 24. 10. 2026** (`Plume_Barvy`, …527, česky; testovací fotka papouška
  v Uložených): z prohlížeče s odstraněním pozadí do nové sady „Plume test" (sada se objevila
  ve výběru samolepek), znovu bez odstranění pozadí do existující sady, z Nastavení s fotkou
  z galerie (sada se objevila v Moje sady). **Neověřené**: úprava ořezem a kresbou před
  vytvořením, plná sada, odmítnutí serverem (`STICKER_PNG_DIMENSIONS` a spol.), iOS a desktop
  (tam se nenabízí). Obrys tamtéž: náhled s obrysem (hladký okraj, bez mezery u špičky zobáku),
  vypnutí a zapnutí přepínače, samolepka s obrysem přidaná do sady „Plume obalka" (server ji
  vzal). Jak obrys vypadá na tmavém pozadí konverzace, ověřené není.

### Tvorba samolepky na iOS

`IosStickerMaker`: pozadí odstraní Vision, zbytek (zmenšení, obrys, WebP) dělá Skia, kterou na
iOS kreslí Compose.

- **WebP umí zakódovat jen Skia** (`Image.encodeToData(EncodedImageFormat.WEBP, …)`). ImageIO
  WebP jen čte: `CGImageDestinationCopyTypeIdentifiers` nabízí z obrázků jen PNG a HEIC. PNG
  fotky v 512 px snadno přeleze 512 kB, které Telegram u statické samolepky snese.
- **Odstranění pozadí je `VNGenerateForegroundInstanceMaskRequest`** (iOS 17; aplikace cílí na
  18.2, takže je vždycky), výřez `generateMaskedImageOfInstances(allInstances, …,
  croppedToInstancesExtent = true)` — vrací pixel buffer s průhledností oříznutý na objekty, přes
  `CIContext` z něj je `CGImage` a z toho přes bitmapový kontext RGBA obrázek pro Skiu. Bez
  objektu Vision nevrátí žádné pozorování → `NoStickerSubjectException`.
- **V simulátoru maska nejde**: Vision skončí chybou „does not support
  VNGenerateForegroundInstanceMaskRequestRevision1" (potřebuje Neural Engine). V simulátoru se
  proto přepínač „Odstranit pozadí" nenabízí (`SIMULATOR_UDID` v prostředí). Každé zařízení
  s iOS 18 Neural Engine má (A12 a novější).
- **Fotka se napřed překreslí přes UIKit** (`UIGraphicsImageRenderer`, zároveň zmenšení na
  1024 px): holý `CGImage` z fotky z fotoaparátu by ignoroval otočení podle EXIF.
- **Ověřeno**: testy v simulátoru (`IosStickerMakerTest`: WebP 512 px, obálka 100 × 100 do
  32 kB, obrys kolem kruhu); táž volání Vision Swift skriptem na Macu nad
  `/Library/User Pictures/Animals/Parrot.heic` a výřez přes iOS cestu obrysu — vypadá stejně
  jako na Androidu. V aplikaci v simulátoru: vstup z Nastavení, výběr fotky, náhled, úprava
  fotky (tah perem se propsal do samolepky), výběr emoji a sady, přidání do nové sady (server
  WebP ze Skii vzal), druhá samolepka z úpravy sady s předvybranou sadou a obálka (sada po ní
  v Moje sady zůstala). **Neověřené**: odstranění pozadí na skutečném iPhonu a vstup
  z prohlížeče fotky.

### Tvorba samolepky na desktopu

`DesktopStickerMaker`: celá fotka zmenšená na 512 px, WebP ze Skii (`SkiaStickers.kt`, totéž
co iOS). **Bez odstranění pozadí** — na desktopu není model segmentace (ML Kit je v Play
službách, Vision na Apple platformách) a vlastní by aplikaci zvětšil o desítky MB. Bez pozadí
odpadá i obrys (kolem celé fotky by byl rámeček).

- **Skia je na desktopu jediná cesta k WebP**: `javax.imageio` WebP nezná vůbec. Kreslení
  a kódování proto leží v source setu `skiaMain` (iOS + JVM), ne v `nonDynamicThemeMain` —
  ten je o motivu, ne o obrázcích.
- **Otočení podle EXIF dělá dekodér** (`Codec.encodedOrigin` + `EncodedOrigin.toMatrix`):
  výběr souborů na desktopu fotku nepřevádí (soubor se nekopíruje), takže snímek
  z fotoaparátu by bez toho skončil na boku. Test vkládá do JPEGu ručně segment EXIF
  s orientací 6.
- **„Upravit fotku" se na desktopu nenabízí** — editor fotky tam zatím není
  (`PhotoMarkupRenderer.isAvailable = false`), viz `docs/poznatky/desktop.md`.
- **Ověřeno v ladicí desktopové aplikaci 24. 10. 2026** (macOS, skutečný účet, sada „Plume test
  iOS"): výběr JPEGu přes `FileDialog`, náhled, přidání do sady (server WebP vzal), obálka
  z úpravy sady. Okno JVM jde ovládat jen přes celou obrazovku (`computer_batch`), na pozadí
  klik skončí „Could not read which file this window is editing". **Neověřené**: Windows
  a Linux, fotka s EXIF orientací v aplikaci (jen testem).

## Úprava vlastní sady samolepek

Vlastní sada (`StickerSetDetail.isOwned`, z `StickerSet.isOwned`) má v Nastavení → Chaty →
Sady samolepek → ⋮ položku „Upravit sadu" (`OwnedStickerSetScreen`,
`OwnedStickerSetViewModel`): název, pořadí tažením za úchyt, změna emoji a odebrání samolepky
(⋮ u řádku), „Přidat samolepku z fotky" s předvybranou touto sadou
(`StickerComposerRoute.targetSetId`) a smazání celé sady. Android a iOS, stejně jako tvorba.

- **Samolepka se v sadě adresuje souborem**, ne id: `removeStickerFromSet`,
  `setStickerPositionInSet` i `setStickerEmojis` berou `InputFile`, tedy
  `InputFileId(sticker.sticker.id)` (`StickerItem.sendFileId`).
- **Co server potvrdí, přijde updatem sady** (`stickerSetDetail`) — obrazovka si po změně nic
  nepřepisuje sama. Výjimka je pořadí během tahu: drží ho ViewModel a serveru odejde až při
  puštění úchytu, **jedním voláním za taženou samolepku** (`setStickerPositionInSet` bere jednu
  samolepku a cílovou pozici, ne celé pořadí). Tah zpátky na původní místo server nevolá.
- **Akce ve ViewModelu čtou zdroje, ne odvozený `state`.** `state` je `combine(…).stateIn`
  a přepočítá se až po dispatchi; `saveTitle` hned po `setTitleDraft` by jinak četl starý
  koncept (přišlo se na to testem, v UI mezi tím leží snímek).
- **`deleteStickerSet` sadu ze seznamu nainstalovaných NEODEBERE.** Na serveru je pryč, ale
  TDLib ji v paměti drží a v Moje sady visela do restartu aplikace (ověřeno na emulátoru).
  `deleteSet` ji proto napřed odinstaluje (`changeStickerSet(isInstalled = false)`) a když
  smazání selže, nainstaluje ji zpátky.
- **Odkaz sady se změnit nedá** — přejmenování (`setStickerSetTitle`) mění jen název, pod
  polem je vidět `t.me/addstickers/<name>`. Název má nejvýš 64 znaků.
- **Ověřeno na emulátoru 24. 10. 2026** (`Plume_Barvy`, …527, česky): přejmenování, přesun
  tažením (vydržel po návratu na obrazovku), dialog emoji s předvyplněným emoji, odebrání
  samolepky, smazání sady (s opravou zmizí ze seznamu hned). **Neověřené**: samotná změna
  emoji (emoji nejde napsat přes `adb input text`), předvýběr sady při přidání z úpravy, když
  je vlastních sad víc (byla jedna), plná sada.

### Obálka sady

Obálka je řádek pod názvem (náhled, „První samolepka v sadě" / „Vybraná samolepka", šipka
zpět k první samolepce) a v nabídce ⋮ u samolepky „Nastavit jako obálku"
(`OwnedStickerSetViewModel.setCover`, `StickerRepository.setSetCover`).

- **Obálka je samostatný soubor, ne odkaz na samolepku.** `setStickerSetThumbnail` chce
  WebP (nebo PNG) přesně 100 × 100 a nejvýš 32 kB. Samolepka se proto stáhne
  (`FileRepository.file`), vepíše doprostřed průhledného čtverce a uloží
  (`StickerMaker.makeCover`, Android a iOS); nad 32 kB se ubírá kvalita. Bez výrobce
  (desktop) řádek obálku jen ukazuje.
- **`null` místo souboru obálku zruší** — obálkou je zase první samolepka (`thumbnail`
  u sady je pak `null`). `format` se v tom případě předává taky `null`.
- **Změna obálky sadu vyřadí z nainstalovaných.** Po `setStickerSetThumbnail` sada zmizela
  z Moje sady (i výběru samolepek), u nové obálky i u vrácení první samolepky; mezi vlastními
  (`getOwnedStickerSets`) zůstala. Ověřeno na emulátoru dvakrát po sobě. Repository ji proto
  hned nainstaluje znovu, stejně jako po založení sady a přidání samolepky.
- **Aplikace obálku dřív vůbec nečetla** — jako obálka sady se všude kreslila první samolepka
  (`covers`). Teď se bere `thumbnail` (`toSetCover`: WebP a PNG jako obrázek, TGS jako Lottie,
  WEBM jako videosamolepka) a teprve bez něj první samolepka: seznam sad, záložky výběru
  samolepek, sada skupiny, výběr sady při tvorbě. Platí i pro cizí sady, takže záložky teď
  ukazují tytéž obálky jako oficiální klient. Sady emoji to nemění (obálka je tam vlastní emoji).
- **Ověřeno na emulátoru 24. 10. 2026** (`Plume_Barvy`, …527, česky; testovací sada „Plume
  obalka" zůstala na účtu): nastavení druhé samolepky jako obálky (řádek i seznam sad ji ukázaly
  hned), vrácení první samolepky, sada po obojím zůstává v Moje sady. **Neověřené**: záložky ve
  výběru samolepek v konverzaci, cizí sady s obálkou TGS nebo WEBM, obálka přes 32 kB.

## Samolepky k emoji v poli

Když je v poli jediné emoji a nic jiného, nad polem vyjede řádek samolepek k němu
(`StickerSuggestionsPanel`, `StickerSuggestionsViewModel`, pravidlo `stickerSuggestionEmoji`,
testy `StickerSuggestionsTest`). Ťuknutí pošle samolepku MÍSTO emoji a pole vyprázdní.

- **Jen samotné emoji** (`emojiClusterCount(max = 1) == 1`, mezery kolem nevadí). Emoji
  uprostřed věty je součást textu; složené emoji (rodina, vlajka, odstín pleti) je jedno.
- **Jen z toho, co uživatel zná** (`StickerRepository.stickersForEmoji` = `getStickers`
  s emoji jako dotazem): vlastní sady, nedávné a oblíbené, ne veřejné sady. Nabízet cizí
  samolepky ke každému napsanému emoji by bylo dotěrné a stálo by to dotaz na server.
- **Ne při úpravě, s přílohou ani bez práva posílat samolepky** — emoji je tam text nebo popisek
  a samolepka by nešla poslat. Dotaz na inline bota má přednost; se zmínkou ani emoji ke slovu
  se nepotká, ty chtějí písmena.
- **Samolepky v řádku hrají** — je jich vidět pár a řádek je jediný, jako mřížka jedné sady.
- **Plave nad polem** jako ostatní napovídání (`FloatingSuggestion`), tlačítko dolů mezitím
  ustoupí. Kdo chtěl poslat emoji, odešle ho jako vždycky.
- **Logika je ve vlastních funkcích** (`rememberStickerSuggestions`), ne v `ConversationScreen`:
  viz „Obrazovka konverzace je rozdělená do částí" v CLAUDE.md.
- **Podržení navržené samolepky otevře velký náhled** (`SuggestedStickerPreview`) s Poslat
  a hvězdičkou, bez voleb odeslání — ty by se musely protáhnout přes `ConversationScreen`.
  Oblíbené si náhled čte sám (`koinInject<StickerRepository>`) a jen po dobu, co je otevřený;
  panel vlastní ViewModel na oblíbené nemá a `ConversationScreen` se kvůli tomu neměnil.
- **Výsledky inline bota mají náhled taky** (GIF a samolepka, `InlineResultPreview`), ale JEN
  S POSLAT: posílá se výsledek bota (`sendInlineResultMessage`), ne soubor, takže uložení GIFu
  ani hvězdička by se k tomu, co odejde, nevztahovaly. Náhledy proto berou
  `onToggleFavorite` / `onToggleSaved` jako nepovinné (`null` = tlačítko není).
- **Náhled nad polem zavře klávesnici** (dialog si vezme fokus). Text v poli i panel výsledků
  zůstanou, po zavření náhledu je výběr dál na místě — ověřeno na emulátoru s `@gif cat`.
  Samolepka z inline bota ověřená není (testovací účet nemá bota, který samolepky vrací); jde
  týmž `StickerPreviewDialog` jako návrhy.

## Výběr GIFů

Třetí část sheetu výběru (Emoji / Samolepky / GIFy, `ExpressionMode.Gifs`): pole hledání, pod
ním mřížka. Bez dotazu nahoře uložené GIFy, pod nimi populární. Ťuknutí GIF hned pošle
(`GifPanel`, `GifPickerViewModel`, hranice `GifRepository` v `core/telegram`).

- **Hledání GIFů API nemá — hledá inline bot.** Jméno posílá server (`animation_search_bot_username`,
  dnes `gif`), `searchPublicChat` z něj udělá id a `getInlineQueryResults` vrací
  `InlineQueryResultAnimation`. Prázdný dotaz = populární. Stránkuje `nextOffset` (prázdný =
  konec). Id bota se pamatuje pro INSTANCI klienta — po odhlášení je jiný.
- **Posílá se `InputFileId`, ne `sendInlineQueryResultMessage`.** GIF z výsledku je soubor, který
  na serveru už je; přes bota by u zprávy stálo „přes @gif" a byly by to dvě různé cesty pro
  uložený a nalezený GIF. Po odeslání `addSavedAnimation`, ať je nahoře (jako `addRecentSticker`).
- **Uložené jsou živý tok** (`UpdateSavedAnimations` → znovu `getSavedAnimations`). GIF poslaný
  z klávesnice se mezi ně dostane sám.
- **Podržení otevře velký náhled** (`GifPreviewDialog`), jako u samolepek: GIF v plném poměru
  stran (extrémní poměry oříznuté na 0,75–2,5, jinak by náhled přerostl obrazovku) a pod ním
  Poslat, záložka uložení a volby odeslání. Dřív podržení ukázalo v rozích buňky 28dp tlačítka
  (volby odeslání, u uloženého křížek), na která se špatně trefovalo a která GIF zakrývala.
  - **Uložit jde i GIF z populárních a hledání** (`addSavedAnimation`), dřív šlo jen odebrat
    uložený. Záložka náhled nezavírá, stav potvrdí update uložených (jako hvězdička samolepky);
    uložený GIF přibude nahoře, takže se mřížka pod náhledem posune.
  - **V náhledu se GIF stahuje vždycky**, i velký, který mřížka sama nestahuje (`autoPlays`) —
    podržením si ho uživatel výslovně otevřel.
  - **Buňka pod náhledem má obrys** v `primary` — GIF je obdélník, sušenka jako u samolepky by
    k němu neseděla. Klíč nese sekci: uložený GIF bývá i mezi populárními a obrys má mít jen
    buňka, odkud přišel.
  - **Hlavní tlačítko má popisek od volajícího** (`pickLabel`), ve formuláři ankety „Vybrat".
  - Výsledky inline bota (`InlineResultsPanel`) náhled nemají — `GifCell(onPreview = null)`.
- **Stránka patří dotazu** (`GifResults.withPage`, testy `GifResultsTest`) — pomalá odpověď na
  „ko" nesmí přepsat „kočka". Bot vrací tentýž GIF na dvou stránkách; duplicitní klíč by mřížku
  shodil, proto `distinctBy`.
- **GIFy v mřížce hrají**, jako v konverzaci: GIF se vybírá podle toho, co dělá. Hrají jen
  složené buňky a velký se sám nestahuje (`autoPlays`).
- **Obraz přehrávače se do buňky ROZTAHUJE, ne vepisuje** (`coverAspect`). `VideoSurface` obraz
  vepisuje, takže v buňkách pevné výšky zůstávaly pruhy a každý GIF měl jinou velikost.
- **Právo je totéž jako u samolepek** (`canSendOtherMessages`): bez něj ani při úpravě se část
  GIFy nenabízí. `ViewModel` klíčovaný chatem (`gifs-$chatId`) — inline bot chce vědět, kam se hledá.
- **Zdrojový soubor nesmí obsahovat znak NUL.** Konstanta `"\u0000"` zapsaná nástrojem jako
  skutečný bajt udělala z `.kt` pro `grep` binární soubor a hledání v něm tiše nic nenašlo.

## Vlastní emoji ve výběru emoji

Za skupinami obyčejných emoji jsou záložky nainstalovaných sad vlastních emoji a na konci „+"
s populárními sadami k přidání (`CustomEmojiPicker.kt`, `StickerRepository.customEmojiSets` /
`trendingCustomEmojiSets`). Ťuknutí vloží emoji do textu jako `TextFormat.CustomEmoji` (model
a pole viz „Formátování při psaní" v konverzace-psani.md).

- **Bez Premium jen v Uložených zprávách** (`canUseCustomEmoji`: `getMe().isPremium` nebo chat
  se sebou samým). Ověřeno pokusem: zpráva s vlastním emoji (Duck Emoji, testovací účet bez
  Premium) poslaná do Uložených zpráv má entitu `TextEntityTypeCustomEmoji` i po potvrzení
  serverem; jinde ho TDLib bez Premium nepovolí („Only premium users can use premium custom
  emoji"). Jinde výběr sady vůbec nenabídne — žádný zámek, žádná propagace Premium.
- **„+" s populárními sadami je tu kvůli tomu, že sady emoji jinak přidat nejde** — testovací
  účet neměl ani jednu. Přidaná sada přibude jako záložka před „+".
- **Ikona záložky je první emoji sady** (`CustomEmojiImage`), popis pro čtečku název sady.
- **Náhled rozepsaného textu nad výběrem kreslí vlastní emoji obrázkem** (`appendWithCustomEmoji`),
  stejně jako bublina. Proto `StickerPickerSheet` dostává `draftFormats`.
- **Povolení řeší výběr sám**, ne `ConversationScreen` (v dexu na hraně): ten předává jen
  `draftFormats` a `onCustomEmoji`, výběr zjistí z chatu, jestli je smí nabídnout.
- **Ověřeno na emulátoru** (anglicky, účet bez Premium): „+" v Uložených zprávách, přidání
  Duck Emoji, vložení dvou emoji (náhled i pole s obrázky), odeslání (bublina s obrázky),
  koncept s vlastním emoji po ukončení aplikace; v Notifikátoru se sady nenabízejí.
  S Premium ověřené NENÍ.
- **Hledání najde i vlastní emoji** (`searchCustomEmoji`, `EmojiSearchState.Results.custom`):
  `getStickers` s typem vlastních emoji a chatem, dvakrát jako u samolepek — jednou hledaným
  textem (klíčová slova samolepek), jednou emoji, na která se text převedl. Jen nainstalované
  sady, veřejné `searchStickers` ne: vlastní emoji z cizí sady by bez Premium poslat nešlo.
  Ve výsledcích jsou za obyčejnými emoji, v jedné mřížce (`MixedEmojiGrid`), a jen kde je
  uživatel smí poslat.
- **Nedávná vlastní emoji jsou v TÉMŽE seznamu jako obyčejná** (`EmojiEntry`, `recentKey`,
  `parseRecentEmoji`, testy `EmojiEntryTest`): klíč `custom:<id>:<náhradní znak>`. Dva seznamy
  by šly jen za sebou a pořadí použití by se ztratilo. Náhradní znak se ukládá s id, protože
  bez něj by se emoji nedalo vložit do textu (entita leží nad ním) ani nakreslit, než dorazí
  obrázek. Do nedávných jde vlastní emoji ze sady, z hledání i z nedávných samotných; tam, kde
  ho uživatel poslat nesmí (bez Premium mimo Uložené zprávy), se v nedávných vynechá. TDLib
  vlastní seznam nedávných vlastních emoji nemá (`getRecentStickers` je jen pro samolepky).
- **Ověřeno na emulátoru** (česky, účet bez Premium, Duck Emoji): v Uložených zprávách „duck"
  najde 🦆 a za ním kachny ze sady, ťuknutí je vloží do náhledu; po novém otevření výběru jsou
  v Nedávných před obyčejnými a ťuknutí z nich vloží emoji znovu. V Notifikátoru nedávná ani
  hledání vlastní emoji neukážou. Koncept smazán, nic se neodeslalo. S Premium ověřené NENÍ.

## Výběr emoji

Týž sheet jako samolepky, přepínaný segmentovými tlačítky Emoji / Samolepky (a tlačítko ve vstupním
poli má proto ikonu smajlíka). Emoji se **vkládá do rozepsaného textu** na pozici kurzoru.

- **Katalog je vygenerovaný z Unicode `emoji-test.txt`** (`EmojiCatalog.kt`, neupravovat ručně).
  TDLib žádný katalog nemá, jen slovník klíčových slov. Jen fully-qualified emoji **do verze 15.0**
  — novější by na starších systémech, jejichž písmo je nezná, byly prázdné čtverečky — a bez
  variant odstínu pleti. Emoji skupiny jsou jeden řetězec oddělený mezerou, ne `listOf` s tisíci
  literály: inicializace by na JVM narážela na limit velikosti metody.
- **Rozepsaný text drží obrazovka, ne vstupní pole** (`draft: TextFieldValue`). Pole je pod sheetem
  a výběr do textu vkládá i z něj maže. Úprava z výběru volá `onDraftChanged` jako psaní —
  ukládá koncept i hlásí „píše…".
- **Sheet se po vložení nezavírá** — emoji se píšou po několika. Nahoře je proto náhled textu
  (`StartEllipsis`, protože vkládá se na konec) s vlastním mazáním.
- **Mazání bere celý znak, jak ho vidí uživatel** (`previousClusterStart`): surrogate páry,
  modifikátory (odstín, výběr varianty, rámeček klávesy, tagy vlajek regionů), ZWJ složeniny
  a vlajky jako dvojice indikátorů. Smazat jednu jednotku UTF-16 by nechalo v textu půlku emoji.
  Pokryté testy v `commonTest`.
- **Hledání přes `searchEmojis`** v jazyce zařízení a angličtině, výsledky zúžené na katalog.
  TDLib vrací emoji občas bez U+FE0F; porovnává se bez něj a vkládá se podoba z katalogu, jinak
  by se na části systémů nakreslila jako černobílý textový symbol.
- **Nedávná emoji jsou první záložka** (ukládá se ťuknutí ve výběru, i ve výsledcích hledání;
  emoji ze systémové klávesnice zachytit nejde). **Ve sheetu jsou zmražená** na stav při otevření —
  živý seznam by se po každém ťuknutí přeskládal pod prstem. Bez nedávných se výběr otevře
  rovnou na první skupině. Při čtení se zúží na katalog.
- **Odstíny pleti se volí podržením** emoji, které je má (trojúhelník v rohu buňky): nabídka nad
  buňkou s výchozí podobou a pěti odstíny. Volba se **pamatuje po emoji** a mřížka pak kreslí
  i obyčejným ťuknutím vkládá zvolenou variantu — v kategoriích, hledání i nedávných. Varianty
  jsou vygenerované z téhož `emoji-test.txt` (`EmojiSkinTones.kt`), jen se stejným odstínem pro
  všechny osoby: kombinace dvou odstínů by znamenaly 25 variant. Nabídka je `Popup` s vlastním
  `PopupPositionProvider` (nad buňku, vodorovně uvnitř okna), zpět obsluhuje sám.
- **Emoji se dvěma osobami (👫, 🤝, 💑, 💏 a jejich varianty, 13 emoji) mají místo řady mřížku
  5 × 5** (`mixedSkinToneGrid`, `EMOJI_MIXED_SKIN_TONES_PACKED`): řádek je odstín první osoby,
  sloupec druhé, na úhlopříčce stejný. Zvolený pár se pamatuje jako každá varianta (podle
  základu), takže se pak kreslí i vkládá obyčejným ťuknutím. Smíšené varianty jsou v Unicode do
  15.0 (handshake 14.0, páry 12.0–13.1), takže mez katalogu platí i tu.
- **Odstíny generuje `client/tools/emoji_skin_tones.py`** z `emoji-test.txt` (návod v hlavičce
  skriptu). Původní generátor v repu nebyl; nový vyrobil stejnorodé varianty znak po znaku
  shodně se starým souborem, než se přidaly smíšené. **Základ se hledá podle JMÉNA, ne kódových
  bodů**: smíšené varianty páru se píšou rozloženě (👩🏻‍🤝‍👨🏼), kdežto základ je jeden znak (👫);
  „kiss: person, person" jako emoji neexistuje, jeho základ je 💏 (jméno před dvojtečkou).
- **`catalogEmoji` pozná i varianty** — nedávná je ukládají, a bez toho by při čtení vypadla.
- **Výšky obou částí sedí**: náhled textu si ukousne z výšky mřížky, takže přepnutí Emoji /
  Samolepky sheet nenatáhne.

## Reakce

- **TDLib nezná „přepnutí".** Má `addMessageReaction` a `removeMessageReaction`; jestli reakce
  už je, si musí pamatovat klient (`MessageReaction.isChosen`).
- **Stav se mění lokálně hned**, ještě před odpovědí serveru — jinak by mezi ťuknutím a potvrzením
  byla viditelná prodleva. Při selhání se změna vrátí; autoritativní stav stejně dorazí přes
  `reactionUpdates` (`UpdateMessageInteractionInfo`).
- **Reakce, která klesne na nulu, ze seznamu zmizí.** Čip s nulou vypadá jako chyba.
- **Obyčejná i vlastní emoji, placená (hvězdičky) ne.** Vlastní emoji se kreslí jako obrázek
  (`CustomEmojiIcon`), viz „Vlastní emoji mimo text". Placená reakce se přeskočí, aby u zprávy
  nezmizely ty ostatní.
- **Prázdná nabídka není chyba.** V Uložených zprávách Telegram místo reakcí používá tagy a
  `getMessageAvailableReactions` vrátí prázdný seznam. Testovat reakce tam tedy nejde.
- **Pořadí rychlých reakcí podle zvyku uživatele dodává Telegram, vlastní počítadlo NEPSAT.**
  `getMessageAvailableReactions` vrací `topReactions` (nejčastěji používané, řadí server a drží
  je napříč zařízeními), `recentReactions` a `popularReactions`; skládají se v tomhle pořadí bez
  duplicit a pruh bere prvních šest (`MAX_QUICK_REACTIONS`). Přidání reakce posílá
  `updateRecentReactions = true`, takže se i reakce z Plume propíše do „nedávných" všude.
  Počítadlo v aplikaci by vidělo jen reakce z tohohle zařízení a s pořadím serveru by se přelo.
- **„Vymazat nedávné" je nahoře v plném výběru reakcí** (`clearRecentReactions`, s potvrzením —
  seznam je v cloudu), jen v nabídce nad zprávou. **Oddíl „Nedávno použité" NENÍ, protože TDLib
  neřekne, které reakce jsou nedávné** (`ReactionManager::get_sorted_available_reactions`):
  - **Bez Premium nedávné při řazení vůbec nepoužije** a všechno dá do `topReactions` —
    testovací účet dostal všech 73 reakcí v top, `recentReactions` i `popularReactions` prázdné,
    i hned po reakci neobvyklým 🎃. Vymazání by tedy nic viditelného neudělalo a tlačítko se
    bez Premium neukáže.
  - **S Premium** je top nejvýš dva řádky (`2 × rowSize`) a za skutečně nedávné připojí do
    `recentReactions` rovnou VŠECHNY ostatní; `popularReactions` je pak prázdné. Hranice
    nedávné / ostatní se tedy poznat nedá — jediný spolehlivý znak, že seznam nedávných
    existuje, je neprázdné `recentReactions` vedle prázdného `popularReactions`
    (`ReactionChoices.hasRecent`). Podle něj se tlačítko ukazuje.
  - Po vymazání tlačítko zmizí hned, pořadí se srovná při příštím otevření (TDLib řadí znovu).
  - **Ověřeno na emulátoru sondou** (tlačítko vynucené): vzhled, potvrzení a volání
    (`Success`), tlačítko po něm zmizí. Testy `ConversationReactionsTest`. **Neověřené:** účet
    s Premium, kde se tlačítko ukáže samo a pořadí po vymazání změní.
- **Pruh reakcí bere jen ÚMYSLNÉ ťuknutí** (`strayTapGuard` v `core/ui`, testy
  `StrayTapGuardTest`) — v prohlížeči médií i nad zprávou. Srdíčko se dvakrát samo přidalo
  fotce, aniž by uživatel o nabídce věděl. **`clickable` tah nezruší, dokud prst neopustí
  tlačítko** (`waitForUpOrCancellation` ruší jen spotřebovanou událost nebo pohyb MIMO hranice,
  rozšířené na 48 dp); nad seznamem pohyb spotřebuje scroll, jenže pruh reakcí nad sebou nic
  scrollovatelného nemá — nedbalý švih začínající na srdíčku byl ťuknutí. Pojistka v
  `PointerEventPass.Initial` spotřebuje dotyk, který ujel přes touch slop, šel víc prsty, nebo
  začal do 500 ms od objevení pruhu (pruh vyskočil pod prstem: druhé ťuknutí na fotku, scroll po
  omylem otevřené nabídce). **Který z případů to u uživatele byl, ověřené není** — pojistka
  kryje všechny tři; ověřuje se na telefonu švihem přes pruh v prohlížeči.
- **Blok `pointerInput` se NESPOUŠTÍ při složení, ale až s PRVNÍM dotykem**
  (`SuspendingPointerInputModifierNodeImpl`: „Coroutine lazily launches when first event comes
  in"). První verze pojistky si v něm zapisovala čas objevení pruhu — ve skutečnosti tedy čas
  prvního dotyku, takže lhůta 500 ms spolkla KAŽDÉ první ťuknutí, i to po deseti vteřinách:
  reakce z nabídky nad zprávou zabírala až na druhé ťuknutí. Test čisté funkce `isStrayTap` to
  nechytil, modifier sám nezkoušel. Čas proto zapisuje `Modifier.Node` v `onAttach` a při změně
  klíče (`update`). **Obecně: co se má měřit od objevení prvku, nepatří do `pointerInput`.**
- **V prohlížeči pruh reakcí vyjede zdola, až prohlížeč dosedne** (přechod navigace i let fotky
  hotové, `LocalNavAnimatedContentScope.transition` ve stavu `Visible`) a reakce jsou načtené.
  Během letu by vyskočil tam, kam fotka teprve míří — a tam dopadá druhé ťuknutí dvojitého
  ťuknutí. Vyjíždí jen poprvé; po celé obrazovce a mezi stránkami se jen prolne jako horní lišta.
  Změřeno na emulátoru: fotka dosedne ~0,86 s po ťuknutí, přechod se ohlásí hotový až ~1,24 s
  (dozní pružina), pruh vyjede s tím odstupem.
- **`isBig = false`** — `true` je ta celoobrazovková animace; patří k vědomému gestu, ne ke
  každému ťuknutí na čip.
- **Avatary v čipech NEJSOU.** Byly, a byla to slepá ulička: čip s profilovkami je široký, takže
  se jich vedle sebe vešlo pár, a stejně ukazoval jen několik posledních (`recentSenderIds` není
  úplný seznam — celkový počet je v `totalCount`). Čip proto nese jen emoji a počet, a KDO
  reagoval, říká detail. Odpadly s tím i stropy `MAX_AVATARS_PER_REACTION`,
  `MAX_REACTIONS_WITH_AVATARS` a `MAX_SENDERS_SHOWN` a dotazy na odesílatele u každé zprávy
  s reakcemi.
- **Čip je kolečko, počet je odznak přes jeho roh** — ne kapsle s číslem vedle emoji. Čip leží
  PŘES nároží bubliny, takže musí být co nejmenší; kulatý tvar na to sedí líp a číslo vedle emoji
  by ho roztáhlo. **Počet se vypisuje až od dvou**: „1" je šum, že tam někdo je, říká samotný čip.
- **Místo pro odznak si vyhradí obálka čipu, odznak z kolečka NEČOUHÁ.** Řádek reakcí se posouvá
  do strany a posouvatelný kontejner si obsah ořezává na svoje hranice — zavěšený odznak by se
  uřízl.
- **Čip je odznak bubliny a bublina se kvůli němu dole PLYNULE rozšíří.** Čip leží přes spodní
  nároží a na bublinu zasahuje kolečkem o 8 dp (`REACTION_CHIP_ON_BUBBLE`), odznak počtu ještě
  o 7 dp výš (`REACTION_BADGE_ROOM`). Středem na okraji (zkoušeno) zakrýval začátek posledního
  řádku; úplně pod zprávou (zkoušeno) přestal působit jako její odznak.
  - **Pevné odsazení 8 dp nestačilo**: kolečko se dotýkalo dotahů posledního řádku („you")
    a odznak počtu do něj lezl (snímky od uživatele, kanál i příchozí zpráva). Zpráva s reakcemi
    má proto dole `REACTION_CONTENT_BOTTOM` (čip na bublině + odznak + 4 dp mezera, celkem
    19 dp) místo 8 dp. Počítá s odznakem i u reakcí bez počtu — jinak by se zpráva při druhé
    reakci téhož emoji zvětšila podruhé.
  - **Rozšíření se animuje** stejnou pružinou jako místo pod zprávou (`animateDpAsState`
    v `rememberMessageBodyScope`). Úplně první řešení (`REACTION_STRIP_SPACE`) si dělalo místo
    skokem a zpráva při první reakci poskočila — proto se pak místo nedělalo vůbec.
  - **`MessageBodyScope` drží STAV odsazení, ne hodnotu** (`contentBottom` je getter nad
    `State<Dp>`). S hodnotou v klíči `remember` by vznikal nový scope v každém snímku animace
    a překreslovalo by se celé tělo zprávy; takhle rekomponuje jen to, co odsazení čte.
  - **Řídí se reakcemi, které kreslí `MessageBox`** (`heldReactions`), ne `message.reactions`:
    dokud zprávu kreslí nabídka, nesmí se bublina rozšířit dřív, než naskočí čip.
  - Ověřeno na emulátoru (Pixel 7, API 36): čip i odznak „2" pod posledním řádkem s mezerou,
    zpomalenými animacemi (`animator_duration_scale 10`) plynulé splasknutí po odebrání reakce.
- **Kolečko lícuje s hranou bubliny**, jako v Google Zprávách — ne 10 dp uvnitř. Řádek se vysune
  o místo na odznak počtu (vpravo od kolečka) a o místo na stín, takže samotné kolečko končí
  přesně na okraji.
- **Čip má lem v barvě pozadí A drobný stín** (`REACTION_RING` v `surface`, `REACTION_ELEVATION`
  1 dp). Lem odděluje čip od bubliny, jako by z ní byl vykrojený (Google Zprávy). Jenže pozadí
  konverzace je `background`, v M3 totéž co `surface`, takže mimo bublinu lem zmizí a bílý čip
  na skoro bílé ploše splýval — hlavně ve sloupci pod dlouhými příspěvky v kanálu. Stín s
  elevací 1 dp se vejde do rezervy řádku (`REACTION_SHADOW_ROOM`, 3 dp), kterou jinak ořezává
  `horizontalScroll`; vyšší elevace by se tam uřízla, a právě proto dřív stín nebyl vůbec.
- **Emoji v čipu má výšku řádku rovnou písmu, na střed a oříznutou** (`LineHeightStyle` s `Trim.Both`).
  Holý `Text` nese nad kresbou rezervu řádku a emoji sedělo v kolečku viditelně níž.
- **Výplň čipu je neutrální i u vlastní reakce** (`surfaceContainerLowest`). Dřív měla vlastní
  `primaryContainer` a emoji od Telegramu — kreslená se stíny a přechody, žádné ploché symboly —
  na tónované ploše splývala: žlutý palec na světle modré nebyl skoro k poznání. Vlastní reakci
  nese **kroužek v `primary` UVNITŘ lemu v barvě pozadí** (na odchozí `primary` bublině ho od
  ní lem odděluje) a plný `primary` odznak počtu.
- **Místo pod zprávou se otevře animovaně** (`REACTION_OVERHANG` přes spatial spec), takže zpráva
  plynule popojede. Místo je uvnitř `MessageBox`, který kreslí i nabídka nad zprávou — čip patří
  ke zprávě a do nabídky jede s ní.
- **Čipy naběhnou z nuly s pružným přeskokem** (tlumení 0,45 — víc než běžný spatial spec; náběh
  reakce je drobná radostná událost, ne přesun obsahu). Při odebrání poslední reakce se dál
  kreslí ta poslední, jinak by odchod dobíhal s prázdným řádkem. Čipy, které tu byly při prvním
  složení, se nezvětšují — jinak by pulzoval scroll.
- **Dokud zprávu kreslí nabídka, drží se její reakce z doby před otevřením** (`heldReactions`,
  v seznamu i v kopii). Reakce se přidává v nabídce a ta se tím zavře; bez podržení by náběh čipu
  doběhl na skryté zprávě a uživatel by viděl čip už hotový. Takhle naskočí, až zpráva dosedne
  zpátky na místo — jako v Google Zprávách. Ověřeno sérií surových snímků: prvních sedm snímků
  (návrat zprávy) bez čipu, pak přeskok a ustálení.
- **Jedou s bublinou i při tahu na odpověď**, protože posun sedí na sloupci kolem toho `Box`u.
- **Jeden řádek, který se posouvá do strany**, ne lámání do víc řádků: čipy leží na bublině
  a druhý řádek by ji přerostl. U příspěvku s deseti různými emoji je to jediné, co se vejde.
- **Přidaná reakce se ohlásí výbuchem**: za emoji se na 600 ms otevře `SoftBurst` v `primary`
  (naroste, pootočí se a vybledne) a čip pružně povyskočí (`ReactionChip`, `burst`). Ne konfety,
  ty patří výhře v kostce. **Jen při přechodu na vybranou reakci před očima**: čip, který byl
  vybraný už při složení, nebo cizí reakce nic nedělají — jinak by bouchal každý scroll.
  - **Co tu bylo při složení, si pamatuje `ReactionRow`, ne `ReactionChips`.** Čipy se skládají
    až s první reakcí (`AnimatedVisibility`), takže jejich „počáteční" množina už novou reakci
    obsahovala a výbuch se u první reakce zprávy nekonal (ověřeno na emulátoru, první pokus).
  - **Výbuch se kreslí UVNITŘ kolečka čipu** (`drawBehind` pod emoji), ne přes jeho okraj:
    řádek reakcí je `horizontalScroll` a ořezává na svoje hranice, přetékající tvar by se uřízl.
  - Průběh se čte až při kreslení, animace nic nerekomponuje. Ověřuje se zpomalenými animacemi
    a sérií surových snímků — v normální rychlosti ho snímek mine.
- **Dvojité ťuknutí na zprávu přepne rychlou reakci účtu** (`MessageAction.QuickReact`,
  `QuickReaction`; vypnout jde v Nastavení → Obecné → Chaty). Bere ho jen rám bubliny — fotka,
  odkaz nebo citace si ťuknutí berou samy, jako u odznaku přečtení. Cenou je, že jednoduché
  ťuknutí na vlastní zprávu (odznak přečtení) čeká na případné druhé.
  - **Dostupnost se ověří dřív, než se cokoli změní** (`availableReactions`): kanál si sadu
    omezuje, v Uložených reakce nejsou. Optimisticky přidaná reakce, kterou server odmítne, by
    problikla a skončila chybou u gesta, o kterém uživatel nemusí vědět. Nedostupná reakce
    neudělá nic; odebrání vlastní se neověřuje.
  - **Haptika u dvojitého ťuknutí není**: jestli reakce jde, se ví až po dotazu, a potvrzení
    u ťuknutí, po kterém se nic nestane, by lhalo. Odezvu nese výbuch za přidanou reakcí.
  - **Ověřeno na emulátoru** v testovací skupině: dvojité ťuknutí přidalo ❤️, druhé ji odebralo
    (`adb shell "input tap X Y & sleep 0.12; input tap X Y"` — dvě samostatná volání `input`
    jsou na dvojité ťuknutí moc pomalá).
- **Ťuknutí na čip otevírá DETAIL, nepřepíná reakci.** Přidat i odebrat jde z nabídky nad zprávou,
  kde je vybraná reakce zvýrazněná a ťuknutí na ni ji sundá. Dvě věci na jedno ťuknutí nejdou
  a detail je to, co se jinde nedozvíme.
- **V kanálu detail není** — `getMessageAddedReactions` tam vrátí `BROADCAST_FORBIDDEN`. Čip se
  proto v kanálu nedá ťuknout a nemá ani vlnku: čip, po kterém přijde chyba, je horší než čip,
  který nic nedělá. Rozhoduje o tom `ChatInfo.isChannel`, ne odpověď serveru — ta by přišla až po
  ťuknutí.
- **Detail jde filtrovat podle reakce** — nad seznamem spojená skupina přepínacích tlačítek
  „Vše N" a každá reakce s počtem (`ReactionFilter`, tentýž prvek jako druh chatu ve filtrech
  hledání). Jen s víc druhy reakcí; vybrané je vždycky právě jedno. **Filtruje server**
  (`reactionType` v `getMessageAddedReactions`): filtr nad jedinou stránkou by u příspěvku
  s tisíci reakcemi ukázal zlomek. Počty jsou ze zprávy v okamžiku otevření. Při přepnutí
  zůstává starý seznam, dokud nedorazí nový — indikátor by sheet smrštil a zase natáhl.
- **Detail je jedna stránka** (`REACTION_DETAIL_LIMIT`, 50 řádků). U příspěvku s tisíci reakcemi
  ho nikdo neprojde a stránkování by znamenalo držet offset ve stavu sheetu.
- **Správce smí v detailu smazat nebo nahlásit reakce člena** (`deleteMessageReactionsFromSender`,
  `reportMessageReactions`). Ťuknout jde jen CIZÍ řádek a jen když `getMessageProperties` vrátí
  `canDeleteReactions` / `canReportReactions` — vlastnosti se čtou jednou při otevření detailu
  (`ReactionModeration`), řádek bez práva se při stisku nezaobluje. Nabídka u řádku má „Nahlásit
  reakce" a ve vlastní chybové skupině „Smazat jeho reakce". Nahlášení nemá průvodce: server
  k němu důvod nechce. Smaže se VŠECHNO, co člen u zprávy dal (jedna reakce v detailu je jen
  řádek, server maže podle odesílatele).
  - **Po smazání posledního reagujícího se detail zavře**, prázdný sheet s nadpisem „Reakce"
    vypadal rozbitě. Pod filtrem se místo toho filtr zruší — jiné reakce zbýt můžou. Čipy pod
    zprávou srovná update reakcí, ne detail.
  - **Ověřeno na emulátoru jen napůl**: smazání pokusným buildem, který pustil nabídku i na
    vlastní řádek (v testovací skupině vlastníka — `canDeleteReactions` přišlo `true`, nahlásit
    `false`), hláška „Reakce smazány" a zmizelý čip. Cizí reakci na zařízení nebylo kde vzít;
    nahlášení se neodeslalo vůbec. Rozhodování o zavření detailu hlídá `ConversationReactionsTest`.
- **Nepřečtená reakce na vlastní zprávu se zvýrazní, jakmile je zpráva vidět** (`NewReaction`,
  `ChatMessage.newReactions` z `Message.unreadReactions`, živě přes `unreadMarkChanges`): čip
  dvakrát povyskočí (`NEW_REACTION_PULSE_SCALE`) a ve skupině vedle čipů na pár vteřin vyjede
  pilulka s tvářemi a jmény těch, kdo reagovali (`ReactorsLabel`, „Jana, Petr a 2 další").
  V soukromém chatu jméno nic nepřidá — reagovat mohl jen protějšek.
- **Zvýraznění se musí PODRŽET** (`rememberReactionHighlight`, 4 s). Zobrazená zpráva se hlásí
  jako zhlédnutá po 400 ms, TDLib reakce přečte a `newReactions` zmizí — bez podržení by
  zvýraznění zhaslo dřív, než si ho kdo všimne. Stav patří složené položce, takže po
  odscrollování a návratu je reakce přečtená a nic nepulzuje.
- **Pilulka místo v rozvržení nezabírá** (`layout` s nulovou velikostí): sloupec zprávy se
  roztahuje podle řádku reakcí, a popisek v něm by bublinu na pár vteřin rozšířil a posunul.
  Leží přes spodní okraj zprávy vedle čipů, stejně jako čip.
- **Ověřuje se botem**: vlastní zpráva do chatu s botem, `setMessageReaction` botem (změna emoji
  je nová nepřečtená), pak chat otevřít. Pulz jde vidět jen zpomaleně
  (`animator_duration_scale 5`) a změřením plochy emoji ve snímcích. Jména se kreslí jen ve
  skupině, takže se v chatu s botem ukážou jen sondou (`namesReactors = true`, necommituje se).
  **Ve skupině ověřená** (testovací skupina s botem, `setMessageReaction` na vlastní zprávu,
  id zprávy v supergroupě = id u serveru): pilulka „Notifikator" vedle čipu.
