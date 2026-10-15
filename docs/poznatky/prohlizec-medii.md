# Prohlížeč médií a média

Poznatky k jedné oblasti aplikace. Obecná pravidla projektu a rejstřík ostatních souborů jsou
v [CLAUDE.md](../../CLAUDE.md); poučení z opravy v téhle oblasti patří sem.

## Prohlížeč médií

Ťuknutí na fotku nebo video v bublině je otevře přes celou obrazovku: u fotky štípnutí a dvojité
ťuknutí přibližují, video se přehrává; tah dolů zavírá.

- **Vlastní routa `MediaRoute`, ne overlay v konverzaci.** Ven se tím chodí zpět včetně predictive
  back, přežije to otočení i zabití procesu a nepotřebuje to `BackHandler`, který by predictive
  back zabil. **Nezapomenout ji zaregistrovat v `NavSavedStateConfiguration`** — bez toho to spadne
  až za běhu.
- **Zpět v prohlížeči nemá vlastní handler, obsluhuje ho `NavDisplay`.** Dřív tu byl
  `NavigationBackHandler(isBackEnabled = true, onBackCompleted = …)`. Zavíral správně, ale gesto
  si bral dřív než `NavDisplay` a průběh tahu zahodil, takže predictive back v prohlížeči nebyl
  vůbec: nic se nezmenšovalo a fotka neletěla zpátky do bubliny, dokud prst nepustil. Bez
  handleru jde gestem tentýž přechod jako jinde (fade through se zmenšením). Let fotky
  (`sharedNavElement`) visí na přechodu `NavDisplay`, takže by ho měl posouvat tah. **Na zařízení
  to zatím ověřené není.** Spotřebování mizejícího média (`viewModel.onDismiss()`) hlídá
  `onDispose`, ať se z prohlížeče odchází jakkoli.
- **Routa nese jen id**, ne obsah. Zprávu si prohlížeč načte znovu (`ConversationRepository.message`);
  celá zpráva i s reakcemi a citací do serializovaného zásobníku nepatří a dotaz navíc je vedle
  stahování fotky zanedbatelný.
- **Do prohlížeče jde jiná varianta souboru než do bubliny.** `MessageMedia.Photo.fullFileId` je
  největší, co server má; `fileId` (do 1280 px) slouží jako okamžitý náhled, protože ho konverzace
  už stáhla. V bublině by originál znamenal desítky megabajtů kvůli obrázku širokému pár set bodů.
- **Prohlížeč nemá metadata list-detail scény**, takže ho scéna nepřevezme a `NavDisplay` ho
  vykreslí přes celou plochu i na širokém rozvržení.
- **Pager, sousedé, strop šířky, přisouvání karty a jezdící pozadí jsou `core/ui/MediaCarousel`**,
  vepsání karty mezi lišty je `core/ui/MediaPageLayout` — obojí sdílí prohlížeč s potvrzením
  sdílení (Direct Share). Prohlížeč si do stránky dodává zoom, video a režim celé obrazovky
  (`immersion`), nad carousel kreslí lišty a reakce. Všechno níž o sousedech, šířce a pozadí
  platí pro obě obrazovky; **nová podoba „snímků vedle sebe" patří do carouselu, ne do obrazovky.**
- **Ořez pozadí je nutný** (`clipToBounds` v pozadí `MediaCarousel`). Pozadí sousedů leží posunuté
  mimo plochu prohlížeče; bez ořezu prosvítalo vlevo (tehdy přes rail) nerozostřené — rozostření pokrývá
  jen plochu prohlížeče.
- **Gesta patří FOTCE, ne bublině.** Kdyby ťuknutí odchytávala bublina, sebralo by ho i odkazům
  v popisku. Dlouhý stisk se na fotce musí zopakovat: potomek s vlastním klikáním ho nepustí
  k rodiči, takže by nad fotkou nešla otevřít nabídka.
- **Listuje se `HorizontalPager`em, starší vlevo, novější vpravo.** Fotky se hledají
  `searchChatMessages` s `SearchMessagesFilterPhoto`, ne procházením historie — fotek je
  v chatu zlomek zpráv. Jedno volání vrátí obě strany: záporný `offset` přidá novější.
  Výchozí zpráva je ve výsledku taky, takže se při dotahování odfiltruje.
- **Pager drží stránku podle klíče** (id zprávy). Když se vlevo dotáhnou starší fotky, pod
  prstem se nic nepohne, jen se posune index — proto se stránkuje z `snapshotFlow` nad
  `currentPage`, které se po dotažení samo ozve znovu.
- **Počet stránek a klíč pageru čtou TÝŽ stav** (`rememberUpdatedState(items)`), ne seznam
  zachycený v lambdě. `rememberPagerState { … }` zapíše počet do stavu hned v rekompozici,
  kdežto pager v `BoxWithConstraints` dostane nový `key` až v subkompozici při layoutu. Mezi
  tím Compose přepočítá klíče stránek (`NearestRangeKeyIndexMap`) s novým počtem nad starým
  seznamem — po dotažení fotek to v release padalo na „Index 22 out of bounds for length 22".
  Platí pro každý pager / lazy seznam uvnitř `SubcomposeLayout`, jehož počet je mimo něj.
- **Otevřená fotka se ukáže HNED, výřez pro listování se dotáhne pod ní** (`loadInitial`).
  Dokud se čekalo na `visualMedia` (`searchChatMessages`, v chatu se čtyřiceti fotkami stovky
  ms), byl stav prázdný a kreslilo se načítání — fotka letící z bubliny neměla kam dosednout,
  zmizela, bliklo kolečko a pak se objevila. Zpráva přitom byla načtená už na začátku (kvůli
  mizejícím médiím). Pager drží stránku podle klíče, takže rozšíření výřezu kolem ní s ním
  nepohne; okraje jsou do té doby „dosažené", aby se listování nerozjelo od jediné fotky
  souběžně s výřezem. Ověřeno sérií surových snímků: fotka je vidět v každém snímku.
- **Dotažení sousedé PŘIJEDOU, nenaskočí** (`MediaCarousel(animateArrivals = true)`,
  `PageArrivals`). Otevřená fotka se zanimuje letem z bubliny a sousedé u okrajů by vedle ní
  naskočili bleskem. Stránka, která přibyla v datech, vyjede od okraje obrazovky (posun přesně
  o to, o co soused vykukuje) a prolne se. **Posun o kus šířky stránky nefungoval** — soused
  startoval hluboko za okrajem a vidět byl jen konec pohybu, takže to vypadalo jako pouhé
  prolnutí. **Pozor, pager skládá stránky i při listování** (soused dopředu): „skládá se
  poprvé" proto nestačí, jinak by přijížděla každá další fotka při švihnutí. Přijede jen stránka,
  jejíž klíč v předchozím stavu dat nebyl a která se složila hned se změnou; po dvou snímcích se
  nový stav bere jako známý. Směr se čte až při kreslení — po rozšíření dat pager přesune
  `currentPage` na otevřenou fotku až v layoutu. Sdílení (Direct Share) to nemá zapnuté, má
  vlastní animace přidávání a odebírání. Ověřeno surovými snímky: soused se při otevření vysune
  a prolne, při listování je hned celý.
- **Počítadlo „N z M" se ptá jednou**, na otevřenou fotku (`getChatMessagePosition` +
  `getChatMessageCount`; TDLib počítá od nejnovější, tak se to obrací). Ostatní stránky se
  odvozují posunem indexu, protože načtený výřez je souvislý.
- **`detectTransformGestures` s pagerem nejde.** Konzumuje pohyb v každém směru, takže by fotka
  spolkla i vodorovný tah a pager by se nepohnul. Místo něj vlastní detektor, který po slopu
  rozhodne jednou za gesto: dva prsty nebo přiblížená fotka = štípnutí/posun (konzumuje),
  svislý tah = zavírání (konzumuje), vodorovný tah nepřiblížené fotky = listování (nekonzumuje,
  pustí ho pageru). Listovat se tedy dá jen se základní velikostí; posun po přiblížené fotce
  by se s listováním pral. Konec gesta zná detektor sám, takže odpadl samostatný pozorovatel
  puštění, který byl nutný kvůli `detectTransformGestures`.
- **Odlistovaná fotka se vrací do základní velikosti.** Soused zůstává složený
  (`beyondViewportPageCount`), a s ponechaným přiblížením by se z něj po návratu nedalo listovat.
- **Přiblížení se počítá kolem prstů, ne kolem středu.** Bod pod prsty musí zůstat na místě,
  jinak fotka při štípnutí ujíždí a uživatel ji pak dohání posunem.
- **Meze posunu se počítají z poměru stran v metadatech**, ne z načteného souboru — jinak by se
  měnily ve chvíli, kdy dorazí ostrá verze.
- **Posun i přiblížení se čtou až v `graphicsLayer`**, takže gesto nic nerekomponuje; překresluje
  se jen vrstva s obrázkem. Totéž u zavíracího tahu.
- **Vzhled je po vzoru Google Zpráv.** Pozadí je **rozostřený aktuální snímek** pod tmavým
  nádechem (Haze nad malým náhledem), snímek je **karta se zaoblením**
  a sousední stránky u okrajů vykukují (`contentPadding` pageru, 24 dp soused, 8 dp mezera —
  se 14 dp souseda nebylo moc poznat, že vedle další fotky jsou). **Popisek sousední stránky
  mizí** podle posunu pageru (`captionAlpha`), jinak u okraje trčel useknutý kus textu.
- **`HorizontalPager` šířku stránky podle obsahu NEŘEŠÍ.** Stránky mají jednu šířku a karta se
  v ní centruje, takže fotka na výšku na širokém displeji (rozložený Fold) ležela uprostřed široké
  stránky a sousední karty byly celé za okrajem. Proto dvě věci: stránka má strop
  (`MAX_PAGE_WIDTH` 640 dp, zbytek jde do `contentPadding`, mezera mezi kartami je tam 24 dp místo 8) a **sousední karta se přisouvá k okraji
  bližšímu aktuální stránce** (`cardBias` = −`getOffsetDistanceInPages`, čteno až při umístění
  v `MediaPageLayout`). Plynule s tahem — karta, která dojede doprostřed, stojí na středu.
  Šířka se bere z `BoxWithConstraints`, ne z okna.
- **Pozadí jezdí s pagerem**, neprolíná se. Kreslí se pozadí aktuální stránky i sousedů vedle
  sebe, posunutá o `currentPageOffsetFraction` (čteno až v `graphicsLayer`). Dokud se pozadí
  jen prolnulo podle `currentPage`, měnilo se skokem až po přeskočení na další stránku, zatímco
  karta jela pod prstem. Šev mezi dvěma pozadími změkčí rozostření samo — Haze rozmazává
  složený obraz, ne každý snímek zvlášť. Nahoře kdo a kdy fotku poslal
  a akce, dole reakce. Dřív byl snímek přes celou obrazovku na holé černé
  a ovládání leželo přes fotku s přechodem do tmava.
- **Dva režimy: galerie a celá obrazovka.** Galerie je to výš (rozostřené pozadí, karty,
  sousedé u okrajů, lišty). Ťuknutí, dvojité ťuknutí nebo štípnutí na snímku přepne na celou
  obrazovku: černé pozadí, bez lišt a popisku, snímek bez zaoblení a stránka přes celou šířku
  (mezi fotkami při listování jen černá mezera). Zpátky vede jen ťuknutí — přiblížení vrácené
  na základní velikost zůstává na celé obrazovce a návrat do galerie přiblížení srovná.
  Zpět (gesto i tlačítko) prohlížeč zavírá z obou režimů, stejně jako tah dolů.
- **Režim je jeden průběh 0 → 1** (`immersion`), ze kterého jde všechno: odsazení a mezera
  pageru, místo pro lišty a popisek (`pageCardFrame`, testy `MediaPageLayoutTest`), zaoblení,
  průhlednost pozadí. Pružina přestřelí, takže se ořezává do 0..1 — záporné odsazení nebo
  zaoblení layout odmítne. Místo pro lišty se čte až při měření stránky, odsazení pageru
  v kompozici (bere jen hodnotu), takže přechod rekomponuje, ale jen po dobu animace.
- **Systémové lišty na celé obrazovce u FOTKY zůstávají** (čas, notifikace), jen se pod nimi
  ztmaví přechodem do průhledna. Přes vysoký nebo přiblížený snímek by jinak světlé ikony
  nebyly vidět. **Video je schovává spolu s ovládáním** (viz „Video", „Celá obrazovka jako
  v přehrávači").
- **Lišty leží NAD kartou, ne přes ni.** Stránka jde přes celou výšku (přiblížená fotka má kam
  růst), ale karta se vepisuje do místa mezi lištami. Rozvržení stránky je vlastní `Layout`
  (`MediaPageLayout`): nejdřív se změří popisek, snímek se vepíše do zbytku (`fitInto`, testy
  `MediaPageLayoutTest`). S popiskem jako samostatnou lištou by fotka poskočila při listování
  mezi stránkou s popiskem a bez.
- **Gesta sedí na stránce, přiblížení na kartě.** Štípnout jde i vedle úzké fotky; střed
  přiblížení je střed KARTY (zapamatovaný v `onPlaced` do obyčejného pole, ne do stavu), protože
  karta s popiskem pod sebou nestojí uprostřed stránky. Zaoblení nese `graphicsLayer` karty
  (`clip` + `shape`), takže se s přiblížením zvětšuje spolu s fotkou.
- **Podklad je tmavý v obou motivech**, i s rozostřením. Barva ovládání je proto natvrdo bílá —
  role z `colorScheme` by ve světlém tématu dala tmavý text na tmavé ploše — a **ikony
  systémových lišt musí být světlé** (`DarkSystemBars`).
  Kde rozostření není (Android 11 a starší), kreslí Haze skoro neprůhledný závoj
  (`AMBIENT_FALLBACK_ALPHA`) — ostrá fotka za kartou by rušila.
- **`DarkSystemBars` jen přihlašuje požadavek, o liště rozhoduje `AppTheme`** (počítadlo
  `DarkSystemBarsRequests`, ikony světlé = tmavý motiv NEBO aspoň jeden požadavek). Dřív si
  obrazovka při vstupu zapamatovala stav lišt a při odchodu ho vrátila, a ikony pak občas zůstaly
  světlé nad světlým seznamem chatů. Stačilo zavřít fotku a hned ťuknout na další: nový prohlížeč
  vznikl, než starý dojel zavírací animací, zapamatoval si „světlé“ od něj a po zavření je vrátil.
  Totéž se stalo při změně motivu během prohlížení (aktivita má `configChanges="uiMode"`, takže se
  znovu nezakládá), protože se vrátil stav ze starého motivu. **Uložit a vrátit funguje jen
  tehdy, když se obrazovky překrývají v pořadí LIFO, a přechody v Navigation3 to nezaručují.**
- **Na iOS řídí `SystemBarsAppearance` ikony stavové lišty přes rodiče Compose controlleru**
  (`StatusBarHostViewController`, `preferredStatusBarStyle`). Do té doby se ikony řídily
  systémovým vzhledem: s tmavým motivem ve světlém systému byly tmavé ikony na tmavé ploše.
  **`overrideUserInterfaceStyle` NE**: Compose čte `isSystemInDarkTheme` z `traitCollection`
  controlleru, takže by motiv „podle systému" četl sám sebe a tmavý prohlížeč by přebarvil
  aplikaci pod sebou. Rodič místo `ComposeUIViewControllerDelegate`, který je zastaralý. Plocha
  pod lištou nic nepotřebuje — obsah je přes celou obrazovku a lišta průhledná. SwiftUI
  (`UIViewControllerRepresentable`) styl od controlleru převezme; ověřeno na simulátoru iOS 26.5
  v obou kombinacích (světlý systém + tmavý motiv a naopak).
- **Uložení do galerie je v horní liště prohlížeče** (`MediaSaver`, `expect`/`actual`). Ukládá
  se KOPIE ostré verze (`fullFileId`, u souboru soubor sám) — soubor TDLib je v soukromém
  adresáři a s odhlášením zmizí. Android od verze 10 přes `MediaStore` do `Pictures/Plume`
  a `Movies/Plume` bez oprávnění, položka je během zápisu `IS_PENDING`; do verze 9 veřejný
  adresář s `WRITE_EXTERNAL_STORAGE` (`maxSdkVersion="28"`) a ohlášení skeneru médií. iOS
  `PHAssetChangeRequest` s oprávněním jen k přidávání (`NSPhotoLibraryAddUsageDescription`).
  Nabízí se podle `canBeSaved` (`MessageActions.canSave`), stejná ochrana obsahu jako
  u kopírování.
- **Lišta prohlížeče má venku jen Sdílet a Do galerie, zbytek je v nabídce ⋮** (Přeposlat,
  Uložit jako…, Smazat). Se čtyřmi ikonami by se na užším telefonu nevešel titulek; smazání
  do nabídky patří i tak — nevratná akce nemá stát vedle častých — a Přeposlat je sdílení do
  Telegramu, takže vedle Sdílet by byly dvě podobné ikony. Co právě běží, točí na místě svého
  tlačítka (`MediaViewerState.busy`), u „Uložit jako…" na místě ⋮.
- **„Uložit jako…" je jen na Androidu** (`ACTION_CREATE_DOCUMENT`, bez oprávnění, uživatel
  si vybere Stažené i cokoli jiného; `rememberSaveAs` vrací na iOS `null`). Na iOS nabízí
  dialog sdílení sám „Uložit obrázek" i „Uložit do Souborů", takže tam sdílení pokrývá všechno.
  Vestavěný `CreateDocument` má typ napevno z konstruktoru, proto vlastní kontrakt s typem
  podle souboru.
- **Sdílí se KOPIE pod čitelným jménem** (`cache/shared`, vydává ji provider `…documents`),
  ne soubor TDLib — příjemce jméno ukazuje. Mazat se může až při dalším sdílení, protože
  příjemce soubor čte až po odeslání. Oprávnění jde v `ClipData` (výběr aplikací ho předá dál)
  i výslovným `grantUriPermission` všem, kdo `ACTION_SEND` přijme — tatáž past jako
  u otevírání souborů. Na iPadu potřebuje `UIActivityViewController` kotvu popoveru, jinak
  aplikace spadne.
- **Fotka a video se ukládají pod jménem podle času zprávy** (`savedFileName`,
  `Plume_20260918_233025.jpg`). TDLib je drží jako `5868681087951770588_121.jpg`, což v galerii
  nic neřekne. Soubor si nechává jméno, které mu dal odesílatel.
- **Ověřuje se dotazem na `MediaStore`**, ne pohledem do galerie: `adb shell "content query
  --uri content://media/external/images/media --projection _display_name:_size --where
  \"relative_path LIKE 'Pictures/Plume%'\""`. Velikost prozradí, jestli se uložila ostrá verze.
- **Odpovědět jde přímo z prohlížeče** — šipka na začátku dolní lišty, jako v Google Zprávách.
  Prohlížeč se zavře a v konverzaci pod ním je nad polem citace. Požadavek jde přes
  `ConversationReplyRequests` — STAV, ne událost, jako hledání z profilu: konverzace se po
  zavření prohlížeče teprve znovu skládá. **Nabízí se jen nad konverzací téhož chatu**
  (`conversationBelow` v `AppShell`): z mřížky médií v profilu nebo z ankety by nebylo kam se
  vrátit. Podle práva `canReply` stránky; lišta se kvůli odpovědi ukáže i bez reakcí
  (Uložené zprávy je nemají).
- **Fotka z prohlížeče nemusí být v načtené historii** — prohlížeč listuje médii celého chatu.
  Odpověď si ji proto dočte (`repository.message`, lokálně); citace ji v seznamu mít nemusí.
  Ověřeno na emulátoru odpovědí na fotku 31 ze 43: bot ji dostal s `reply_to_message` na ni.
- **Reakce, přeposlání a smazání jsou přímo v prohlížeči.** Nabízené reakce a práva
  (`messageActions`) se načítají **pro stránku, na které uživatel stojí**, jednou za zprávu —
  stejný důvod jako u nabídky v konverzaci. Dokud nedorazí, akce se nekreslí. Smaže se jen
  zobrazená fotka, i z alba; ze seznamu ji odebere až `deletedMessages` a po poslední fotce se
  prohlížeč zavře.
- **Let z bubliny jede přes sdílený prvek** (`photoKey`), stejně jako avatar a název chatu.
  Na širokém rozvržení taky (`inTwoPane`, viz „Navigace"): prohlížeč jde přes celou plochu,
  takže fotka z pravého panelu vyletí na celou obrazovku a při zavření se do bubliny vrátí.
  Ověřeno na rozloženém Foldu sérií surových snímků se zpomalenými animacemi, tam i zpět.
- **Sdílený prvek se během letu kreslí v PŘEKRYVU nad celou aplikací, mimo ořezy rodičů** — tedy
  i přes hlavičku obrazovky. Fotka, která v konverzaci zpola mizí pod hlavičkou, tak po návratu
  z prohlížeče dosedla na místo, ale kreslila se PŘES hlavičku, a teprve na konci přechodu
  (kdy kreslení převezme seznam) hlavička blikla přes ni — vypadalo to jako chyba vykreslení
  a trvalo to víc než vteřinu, protože přechod běží dál i po dosednutí. Obrazovka proto řekne,
  kde ji ořezává vlastní seznam (`SharedElementClip`, `sharedElementClipArea` na TÉŽE ploše,
  která má `clipToBounds`), a `sharedNavElement` ten ořez použije i v překryvu
  (`clipInOverlayDuringTransition`).
- **Tvar fotky se musí předat i SDÍLENÉMU PRVKU, ne jen rodiči** (`sharedNavElement(shape = …)`,
  `flightShape`, testy `FlightShapeTest`). Ze stejného důvodu jako ořez výš: v překryvu se
  neuplatní nic, co leží nad prvkem. Bublina, náhled videa, dlaždice alba, odpověď ankety i mřížka
  v profilu měly `.clip(tvar)` PŘED `sharedNavElement`, takže se fotka z prohlížeče vracela
  s hranatými rohy a zaoblila se až po dosednutí; prohlížeč má zaoblení uvnitř, a proto bylo
  otevírání v pořádku. Rohy se během letu přelévají z karty prohlížeče (`flightFromCorner`) do
  tvaru cíle — jen při spárovaném přechodu, jinak platí cílový tvar. Průběh se čte ve vrstvě,
  let nic nerekomponuje.
  - **Dlaždice alba zaobluje mřížka, ne dlaždice** — tvar pro let jí proto počítá
    `albumTileShape` (testy `AlbumTileShapeTest`): rohy mřížky jen tam, kam dlaždice sahá.
  - Ověřeno sérií surových snímků se zpomalenými animacemi: samostatná fotka tam i zpátky
    a pravá dlaždice alba (zaoblená jen vpravo).
- **Ořezová plocha je VIDITELNÁ část seznamu, ne jeho hranice** (`SharedElementClip.obscuredTop`
  / `obscuredBottom`, `visibleArea`, testy `VisibleAreaTest`). Seznam zajíždí pod vstupní pole
  i pod pruh připnutých zpráv, takže jeho hranice sahají až ke spodku obrazovky: fotka ležící
  zpola pod polem se při návratu z prohlížeče kreslila PŘES pole a na konci přechodu skočila za
  něj — tatáž chyba jako u hlavičky, jen dole. Obrazovka proto po každé kompozici zapíše výšku
  obou pásů (výška pole se mění s klávesnicí). Pod polem je sklo, takže po dosednutí je dole
  vidět rozostřený kus fotky; to je nepatrný rozdíl proti skoku přes celé pole. Ověřeno pixelem
  uprostřed karty pole během zpomaleného návratu: před opravou ho fotka od půlky letu přebarvila
  až do konce přechodu, po opravě ani jednou.
- **Dole se ale neořezává rovnou čarou, fotka zajede POD kartu pole** (`SharedElementClip.band`,
  `occludesSharedElements`, `openBand`). Čára u horní hrany pásu useknula fotku v půlce letu nad
  kartou a vypadalo to, že do pole zalézá (hlášeno videem). Pás pod polem je průhledné sklo,
  takže se ořez rozšíří až na jeho spodek a vyřízne se z něj jen to, co fotku opravdu přikrývá:
  tvar karty, tvar tlačítka (i sušenka a vlnka při nahrávání — ořez si tvar čte při kreslení)
  a všechno pod nimi. Výřezy rostou s letem od spodku, jinak by na začátku, dokud fotka ještě
  stojí v prohlížeči, byla v ní vidět díra ve tvaru pole.
  - **Jen když pás leží hned pod seznamem.** Nad polem můžou být panely (návrhy odpovědí,
    klávesnice bota) a přes ty se letět nesmí; pak platí čára jako dřív. Totéž, když místo pole
    stojí jiná lišta (výběr, hledání, „Přidat se") — ta tvary nehlásí.
  - **Odscrollovat konverzaci, aby fotka dosedla celá, NENÍ řešení** (zkoušeno, uživatel
    odmítl): návrat z prohlížeče nemá měnit místo, kde uživatel čte.
  - V proužcích skla kolem karty letí fotka ostrá a po dosednutí ji převezme rozostřený seznam.
    Je to pár dp a pod kartou mizí tak jako tak, skok vidět není. Ověřeno sérií surových snímků
    se zpomalenými animacemi.
- **Ořez se ale nesmí zapnout skokem**, jinak hned v prvním snímku ukrojí kus celoobrazovkové
  fotky a odkryje pod ní hlavičku, která tam ještě nepatří. Otevřený ořez se proto nebere jako
  nějaké velké číslo, ale jako **obal plochy a samotného prvku** — ten prvek z definice obsahuje
  celý, takže na začátku neukrojí nic — a stahuje se na plochu toutéž pružinou jako let
  (`sharedElementClipRect`, testy `SharedElementClipTest`). Fotka tím pod hlavičku podjede.
- **Ořezává se jen strana, která PŘICHÁZÍ**, a to stačí: `sharedElement` kreslí v překryvu právě
  prvek, který se stává viditelným. Při otevírání prohlížeče je to fotka v prohlížeči, a ta
  žádnou plochu nedostává — skrytý proužek nad hlavičkou tam na okamžik probleskne, jenže fotka
  se v té chvíli tak jako tak zvětšuje přes celou obrazovku, takže to není vidět. Vadí to jen
  při návratu, kde prvek na svém místě ZŮSTANE stát.
- **Průběh ořezu jde z `AnimatedVisibilityScope.transition`** (`animateFloat` nad
  `EnterExitState`), ne z bounds prvku: `OverlayClip.getClipPath` dostane jen AKTUÁLNÍ hranice,
  ne cílové, takže z nich není jak poznat, jak daleko let došel.

### Poloha a mapy

Zpráva s polohou, místem (`MessageVenue`) a živou polohou je `MessageMedia.Location` a kreslí se
**statickou mapou** se špendlíkem uprostřed (`StaticMap` v `core/ui`, `LocationMap`); tentýž
kus kreslí místo v anketě i jeho náhled ve formuláři. Ťuknutí otevře mapy platformy.

- **Mapu kreslí Telegram** (`getMapThumbnailFile` → `FileRepository.mapThumbnail`): PNG soubor
  jako každý jiný, bez klíče k mapovému API a bez dotazu na cizí službu. Je to obrázek — neposouvá
  se ani nepřibližuje; interaktivní mapa by chtěla mapové SDK (a klíč), které aplikace nemá.
- **Mapa se žádá přesně na změřenou plochu** (`BoxWithConstraints`), v bodech zaokrouhlených na
  8 a v hustotě displeje (`scale` 1–3). Meze TDLib (zoom 13–20, strana 16–1024) se hlídají
  v `StaticMap` — mimo ně TDLib vrátí chybu, ne oříznutí. `fileId` výřezu si repository pamatuje
  (mapa se skládá při každém návratu položky do seznamu) a zahazuje s výměnou klienta.
- **Mapa jde na plochu bez bubliny jako fotka** (`showsBareMedia`), název a adresa místa pod ní
  jsou karta v barvě bubliny (`LocationDetails`), jako popisek fotky. Šířka je STÁLÁ (280 dp,
  poměr 0,6) — s názvem i bez něj, jinak by karta s popiskem vyšla širší než holá mapa vedle ní.
  Text zprávy je prázdný (`captionBody`): „Poloha" pod mapou by ji jen popisovalo podruhé;
  seznam chatů a citace dál píšou „Poloha" / název místa.
- **Mapa je vždycky světlá**, i v tmavém motivu (tmavé dlaždice Telegram nemá). Špendlík proto
  nebere `primary` natvrdo — v tmavém motivu je světlá — ale tmavší z `primary` / `inversePrimary`.
- **Živá poloha je taky jen obrázek.** Posun chodí jako úprava zprávy, takže se mapa přestaví
  sama; pod ní „Sdílí se do 14:21" / „Sdílení skončilo". Konec je `datum zprávy + livePeriod`
  (`liveLocationUntil`, testy `LiveLocationTest`), ne `expiresIn` — ten TDLib změnou neposílá,
  takže by zastaral. „Upraveno" se u živé polohy neukazuje, svítilo by pořád.
- **Známý směr pohybu (`LiveLocation.heading`, 1–360 po směru hodin od severu) je šipka místo
  špendlíku** (`StaticMap(heading)`, ikona `navigation`), na bílém kolečku — mapa je vždycky
  světlá, ale park nebo voda pod šipkou ne. Špendlík ukazuje místo hrotem, šipka středem, takže
  se neposouvá. Jen dokud se sdílí: u skončené polohy by směr tvrdil, že se někdo pořád hýbe.
- **Jestli se ještě sdílí, rozhoduje `rememberLiveSharing`, ne porovnání při složení.**
  Zastavené sdílení (`stopMessageLiveLocation`) přijde úpravou s `livePeriod` zkráceným na
  okamžik zastavení, takže konec padne do téže vteřiny jako složení a „už skončilo" vyšlo
  naprázdno — pod mapou zůstalo „Sdílí se do 14:08". Teď se jednou počká (`delay`) na konec
  a mapa i text se přepnou samy, i u polohy, která vyprší před očima. **Hodiny emulátoru jdou
  proti Macu klidně o 40 s pozadu** — na konec se čeká podle `adb shell date`.
- **Adresa map je platformní** (`mapsUrl` v `core/location`): Android Google Mapy přes https
  (app link do aplikace Map), iOS `maps.apple.com`. `geo:` iOS nezná a na Androidu by neprošel
  cestou odkazu z konverzace — ta umí jen adresy s `://`.
- **Mapa má vlastní klikání, takže opakuje dlouhý stisk** — jinak by nad ní nešla otevřít
  nabídka zprávy (tatáž past jako u fotky). Upravit polohu nejde (`carriesCaption` = false).
- **Ověřuje se botem**: `sendLocation`, `sendVenue`, `sendLocation` s `live_period`, a anketa
  s `media` typu `venue` / `location`. Ověřeno na emulátoru: všechny tři druhy zpráv, mapa
  u otázky i odpovědi ankety, náhled ve formuláři, ťuknutí do Google Map (i bez hlasu u
  odpovědi), dlouhý stisk s nabídkou. **Posun živé polohy ověřený není** (`editMessageLiveLocation`
  botem by šel) a **iOS jen přeložené** — Apple Mapy se na simulátoru neotevíraly. Odeslání ze
  sponky ověřené na emulátoru: místo i „Moje poloha" odešly a bot přes `getUpdates` dostal
  `venue` (s `foursquare_id`) a holou `location`.
- **Poslat polohu jde ze sponky** (`AttachButton` → „Poloha", `ConversationViewModel.sendLocation`).
  Je to tentýž sheet jako místo v anketě (`PlacePickerHost` nad `PlaceSearcher` — hledání
  kolem polohy zařízení, sdílí ho formulář ankety i konverzace): „Moje poloha" odejde jako
  `InputMessageLocation`, místo z hledání jako `InputMessageVenue` i s id a typem od
  poskytovatele (server ho pak vydá dál, Bot API ukázalo `foursquare_id`). Odchází hned, jako
  samolepka: citaci spotřebuje, rozepsaný text nechá. **V konverzaci si pole hledání fokus
  nebere** (`autoFocusSearch = false`) — hlavní akce je „Moje poloha" a klávesnice by schovala
  místa v okolí; v anketě se místo hledá podle názvu, tam ano.
- **Sdílení ŽIVÉ polohy** je „Sdílet živou polohu" pod „Moje poloha" ve výběru polohy
  (jen Android a ne v kanálu), pak doba (`LiveLocationPeriodDialog`: 15 min, 1 h, 8 h, „dokud ji
  nevypnu" = `LIVE_FOREVER`) a přesná poloha zařízení. Odchází `InputMessageLiveLocation`
  (`sendLiveLocation`), viz „Sdílení živé polohy" níž.

#### Sdílení živé polohy

Polohu do zpráv posílá služba v popředí `LiveLocationService` (`core/location`, typ `location`),
seznam zpráv drží `LiveLocationRepository` (`core/telegram/LiveLocation.kt`, testy
`LiveLocationSharingTest`). Zastavit jde z notifikace služby (všechno) a tlačítkem „Zastavit
sdílení" pod vlastní živou polohou (`MessageAction.StopLiveLocation`).

- **Které zprávy se mají posouvat, drží TDLib** (`UpdateActiveLiveLocationMessages`, s databází
  zpráv přežije restart). Dotaz na ten seznam v TDLib 15 NENÍ — jen update, proto je repository
  eager. Služba ho odebírá a s prázdným seznamem skončí sama (vypršelo, zastaveno v bublině nebo
  na jiném zařízení). Zastavení je `editMessageLiveLocation(location = null)`.
- **Při posunu se `livePeriod` posílá zpátky beze změny** — jinak by úprava dobu sdílení měnila.
- **Oprávnění k poloze na pozadí potřeba NENÍ.** Služba typu `location` spuštěná z popředí si
  nese přístup „při používání"; proto ji `LiveLocationControls` spouští, jen když je aplikace
  v popředí a je co sdílet (i po restartu aplikace — služba po `force-stop` naskočí s prvním
  otevřením). Z pozadí by ji Android 14 nepustil. Když spuštění selže (oprávnění vzaté zpátky),
  sdílení se zastaví — stojící „živá" poloha by klamala.
- **Posílá se jen to, co je znát** (`shouldSendLiveFix`): poloha se čte po 5 s, ale úprava zprávy
  odchází až při pohybu o 30 m (nejdřív po 10 s), bez pohybu jednou za 2 min, a hned po
  `UpdateMessageLiveLocationViewed` (TDLib to tak chce). Směr (`headingOf`) jen při pohybu nad
  0,5 m/s; sever je 360, nula je „neznámý".
- **Notifikace služby má kanál `live-location` s `IMPORTANCE_DEFAULT`** a je `setSilent`. S LOW
  skončila ve sbalené skupině „Tiché", a o sdílené poloze má uživatel vědět.
- **Úprava zprávy trvá i přes deset vteřin** (log: posláno 14:25:07, potvrzeno 14:25:21), takže
  se ověřuje se zpožděním — Bot API `getUpdates` hned po posunu nic neukáže.
- **iOS sdílení nenabízí** (`liveLocationSharingSupported = false`): bez
  `allowsBackgroundLocationUpdates` a režimu `location` v `UIBackgroundModes` by se poloha
  zastavila s odchodem z aplikace. Cizí živou polohu iOS kreslí dál.
- **Ověřeno na emulátoru** (Android 17) testovacím poskytovatelem polohy (viz „Vlastní klávesnice
  bota") v chatu s testovacím botem: 15 min i „dokud ji nevypnu", posuny s aplikací v popředí
  i na ploše (proces nezamrzl, bot dostal `edited_message` s novou polohou), obnova služby po
  `force-stop`, „Zastavit" z notifikace (konečná poloha bez `live_period`) a z bubliny.
  **Ťukat po spuštění aplikace podle
  souřadnic se nevyplatí:** notifikace služby otevřená z lišty vrátila seznam chatů a ťuknutí
  mířené na sponku otevřelo chat se skutečným člověkem. Nabídka sponky je popup, který
  `uiautomator dump` nevidí — ověřit snímkem, že je otevřený správný chat, a teprve pak ťukat.
- **Na přetíženém emulátoru se první dlouhý stisk ztratí** („spent 2442ms processing
  MotionEvent" v logu) a vypadá to jako chybějící nabídka. Zopakovat, než se hledá chyba.

#### Upozornění na blízkost

Pod vlastní živou polohou je vedle „Zastavit sdílení" „Upozornit na blízkost" (se zapnutým
„Upozorním do 500 m") a dialog se vzdálenostmi 100 m – 5 km a „Vypnout upozornění"
(`ProximityAlertDialog`, `MessageAction.SetProximityAlert`, `LiveLocationRepository
.setProximityAlert`). Servisní zpráva o spuštění říká vzdálenost („Upozornění na blízkost: 48 m",
`distanceUiText` v `core/ui/Distances.kt`, testy `DistanceTest`), jména dál ne.

- **Poloměr je součást živé polohy** (`LiveLocation.proximityAlertRadius`, 0 = vypnuto,
  nejvýš 100 km) a nastavuje se úpravou zprávy se STEJNOU polohou, dobou a směrem (bere se ze
  zprávy, ne ze zařízení). **Každý posun polohy ho musí poslat zpátky**, jinak ho vypne.
- **Server upozornění po spuštění SÁM VYPNE** (zpráva přijde s poloměrem 0)
  a `UpdateActiveLiveLocationMessages` kvůli tomu NEPŘIJDE. Dokud posun bral poloměr ze seznamu
  aktivních poloh, zapnul ho dalším posunem znovu — na emulátoru po prvním upozornění svítilo
  „Upozorním do 500 m" dál. Posun proto čte poloměr i dobu z aktuální zprávy (`getMessage`,
  lokální), seznam je jen záloha.
- **Kdy upozornění jde, ví zpráva** (`MessageMedia.Location.proximityAlertRadius`, `null` = nejde,
  `canSetProximityAlert`): jen vlastní poloha, ne v kanálu a ne v Uložených zprávách. „Direct
  messages chats" z dokumentace TDLib jsou přímé zprávy KANÁLU, ne soukromé chaty — chat
  s botem i s člověkem upozornění bere.
- **Spouští ho druhý účastník, který se PŘIBLÍŽÍ** (jeho posun do poloměru). Zapnutí poloměru
  ve chvíli, kdy je druhý už blízko, nespustí nic, a tatáž dvojice zpráv po vypnutí a novém
  zapnutí podruhé nespustila — zkouší se čerstvou dvojicí zpráv.
- **Ověřeno na emulátoru** testovacím poskytovatelem polohy a botem (`sendLocation`
  s `live_period` daleko, pak `editMessageLiveLocation` k nám): servisní zpráva „31 m",
  poloměr přežije posun vlastní polohy, po spuštění zůstane vypnutý, „Vypnout upozornění".
  Notifikace o spuštění jde cestou běžných zpráv (TDLib), zvlášť ověřená není. **iOS jen
  přeložené.** Bot v chatu s ním smaže i NAŠE zprávy (`deleteMessage` vrátil `true`),
  servisní zprávy o blízkosti ne — v chatu s testovacím botem zůstaly.
- **Ťuknutí na vzdálenost v dialogu podle souřadnic se může trefit do mapy pod ním**, když se
  dialog ještě neotevřel — otevřely se Google Mapy. Před ťuknutím ověřit dumpem text dialogu.

#### Dock živé polohy

Zpráva se živou polohou, která odjede z viditelné části konverzace, se plynule přemění v řádek
pod pruhy nad konverzací (pod připnutými): špendlík, kdo polohu sdílí a do kdy
(`LiveLocationDockBar`, pravidla v `LiveLocationDock.kt`, testy `LiveLocationDockTest`). Řádek
se rozbalí na mapu s „Přejít na zprávu" a u vlastní polohy „Zastavit sdílení" a „Upozornit na
blízkost". Když se zpráva scrollem vrátí do pohledu, řádek plynule zmizí.

- **Které polohy, drží ViewModel, ne načtená historie** (`ConversationState.liveLocations`).
  Zpráva s polohou bývá dávno odscrollovaná nebo mimo okno. Při otevření je dá
  `searchChatRecentLocationMessages` (`recentLiveLocations`, vrací i SKONČENÉ — filtruje
  `withLiveLocation` podle `liveUntil`), pak nové a upravené zprávy a vlastní sdílení
  z `LiveLocationRepository.active`. Zastavená poloha přijde úpravou se zkrácenou dobou a odejde
  sama, prošlou odebere odpočet na nejbližší konec (`nextLiveEnd`). Ve vlákně komentářů dock
  není, v tématu jen polohy tématu.
- **Kolik ze zprávy je schované, je průběh docku** (`dockProgress`): podíl zprávy za horní
  hranou (spodek pruhů) nebo za spodní (pole). Seznam je obrácený, offset roste odspodu.
- **Horní hrana se měří BEZ řádku docku** (`barsHeight` je vnitřní sloupec pruhů). Řádek se
  vysouvá s průběhem a s ním i odsazení seznamu; kdyby hrana šla s ním, zakryl by další kus
  zprávy, ta by se schovala víc… a z plynulého přechodu by byl skok.
- **Kompozice čte jen MNOŽINU id v docku** (`derivedStateOf` se `structuralEqualityPolicy`),
  průběh čte až layout (`reveal` jako lambda, `placeRelativeWithLayer`). Jinak by každý snímek
  scrollu rekomponoval sloupec pruhů. Velký skok průběhu (otevření chatu, skok na zprávu,
  `DOCK_JUMP`) se dojede pružinou, scroll jde s prstem.
- **Žádný závoj přes konverzaci.** První verze rozbalený dock podložila scrimem jako sheet;
  uživatel ho odmítl — kouzlo je v tom, že mapa je vidět a chatuje se dál. Rozbalený dock
  zavře šipka, zpět, „Přejít na zprávu" a návrat zprávy do pohledu (`dockedIds` prázdné).
- **Stín pod průsvitným sklem prosvítá DOVNITŘ plochy.** Rozbalená vysoká karta se 3dp stínem
  měla vpravo a dole ztmavlý pruh, posunutý vůči kartě — vypadalo to jako špatně umístěná
  vrstva (`placeRelativeWithLayer`), a nebyla to ona. Stín má proto jen sbalený řádek.
- **Ověřeno na emulátoru** (anglicky) živou polohou od testovacího bota (`sendLocation`
  s `live_period`) a šestnácti zprávami za ní: vysunutí při scrollu i po znovuotevření chatu,
  rozbalení s mapou, scroll pod rozbaleným dockem, sbalení a zmizení při návratu zprávy,
  „Přejít na zprávu" se zvýrazněním. **Vlastní poloha v docku (zastavit, upozornit) ověřená
  není** — tlačítka jsou tatáž cesta jako pod zprávou (`StopLiveLocation`,
  `SetProximityAlert`). iOS jen přeložené.

#### Mini přehrávač v docku

Hrající hudba nebo hlasovka, jejíž zpráva odjede z obrazovky, je ve sloupci pruhů pod dockem
polohy jako řádek se stejným sklem (`PlayerDockBar`): tlačítko s průběhem po obvodu (u skladby
přes obal), název a „interpret · 0:04 / 0:16", u skladby „další" a vždycky ✕. Ťuknutí na text
skočí na zprávu. Kdo do docku patří, je `VoicePlayback.docksIn` (testy `PlayerDockTest`).

- **Vysouvání a odsazení jsou společné s dockem polohy** (`Modifier.dockReveal`, rozměry
  `DOCK_*` v `DockRow.kt`). Průběh je `dockProgress` nad položkou s klíčem BLOKU
  (`blockKeyOf`) — skladba z alba je položka nejstarší zprávy alba.
- **Skladba z jiného chatu je v docku celá** (průběh 1), protože hudba hraje dál i po odchodu
  z konverzace a jinde než tady (a v notifikaci) by nešla zastavit. Ťuknutí na ni otevře její
  chat se skokem na zprávu. Hlasovka z jiného chatu ne — odchodem z chatu dohraje.
- **Náhled nahrávky (zpráva −1) do docku nepatří**, má vlastní pruh nad polem.
- **Kompozice čte jen KLÍČ hrajícího zvuku** (`derivedStateOf` se `structuralEqualityPolicy`),
  průběh a čas čte až řádek přes lambdu — pozice se mění dvacetkrát za vteřinu.
- **Název skladby z jiného chatu bere `MusicQueue.nowPlaying`**, ne načtenou historii: zpráva
  z jiného chatu v ní není.
- **✕ u hudby jde přes `MusicQueue.stop`**, u hlasovky přes `VoicePlayer.stop` — fronta jinak
  po zastavení navázala další skladbou.
- **Kolečko se zvukem hraje v docku dál, i obrazem** (`SoundingVideoNote`,
  `rememberSoundingVideoNote` v `items/SoundingVideoNote.kt`): v kolečku na místě tlačítka běží
  živé video s průběhem po obvodu, „Notifikator · Video zpráva · 0:23 / 0:39".
  - **Přehrávač kolečka se zvukem patří OBRAZOVCE, ne položce.** Dokud ho držela položka,
    kolečko s odscrollováním zmlklo (přehrávač odešel s kompozicí) a dock neměl co ukázat.
    Bublina si ho bere přes `ConversationItemScope.soundingVideoNote`, tichou smyčku dál hraje
    vlastním přehrávačem. Kopie v nabídce nad zprávou dostává `null` — hraje potichu.
  - **Obraz kreslí VŽDYCKY JEN JEDNA plocha** (`SoundingVideoNote.docked`): video jde na Androidu
    do `TextureView` a přehrávač má jediný výstup — plocha, která by o něj přišla, zůstala viset
    na posledním snímku. Předává se v polovině vysunutí docku (`VIDEO_NOTE_HANDOFF`), takže
    v bublině odjíždějící z obrazovky i v kolečku docku je obraz vždycky jen tam, kde je víc vidět.
  - **Zvuk vrací jen odchod z obrazovky, dohrání a ✕** (`VideoNoteSoundEnded`), ne odscrollování.
    Pozastavené kolečko se v bublině i v docku pustí dál ťuknutím, nezačíná znovu.
  - **Ověřeno na emulátoru** čtyřicetivteřinovým kolečkem od testovacího bota: dock s živým
    obrazem a časem, pauza a pokračování z docku, ✕ (bublina se vrátí do tiché smyčky), dohrání
    (dock zmizí), návrat scrollem (bublina hraje dál, čas běží), skok na zprávu ťuknutím. iOS jen
    přeložené.
- **Ověřeno na emulátoru** (anglicky) dvěma skladbami a hlasovkou od testovacího bota: vysunutí
  při scrollu, pauza, „další" (u poslední skladby není), skok na zprávu ťuknutím (dock zmizí,
  zpráva zvýrazněná), ✕, skladba z Notifikátora v docku Uložených zpráv. iOS jen přeložené.

#### Přehrávač nad seznamem chatů

Hudba, která hraje dál po odchodu z konverzace, má nad seznamem chatů tentýž řádek
(`PlayerDockBar`, `NowPlayingViewModel`): dole pod FAB menu, sklo nad seznamem.
Ťuknutí na text otevře chat se skokem na skladbu.

- **Jen hudba.** Hlasovka odchodem z chatu dohraje (`stop(chatId, keepMusic = true)`), takže
  nad seznamem by nebylo co ukázat. Zdrojem je `MusicQueue.nowPlaying`, ne `VoicePlayer`.
- **Dole, ne v hlavičce.** Pilulka hledání při scrollu odjíždí a řádek by s ní zmizel; dole je
  navíc pod palcem. FAB se nad něj posune (je ve stejném sloupci), seznam má dole místo navíc
  o změřenou výšku řádku, aby poslední chat nezůstal schovaný.
- **S otevřenou konverzací vedle (dvoupanel) se neukazuje** (`conversationOpen` z `AppShell`):
  konverzace má vlastní dock a dva přehrávače vedle sebe by říkaly totéž dvakrát.
- **Sklo rozostřuje jen seznam chatů** (vlastní `HazeState`), stejně jako v konverzaci.
- **Obal skladby se předává jako soubor** (`TrackInfo.coverFileId`). Fronta měla jen bitmapu pro
  systémové ovládání; řádek si obal stáhne sám přes `RemoteImage`.
- **Řádek mizí i se svým místem**, takže FAB po dohrání sjede dolů na jeho místo. Ťuknutí na ✕
  ve chvíli, kdy skladba právě dohrála, proto trefí FAB a otevře menu „Vytvořit" — neškodné,
  jen se na to při ověřování přes `adb` nesmí zapomenout.
- **Ověřeno na emulátoru** (anglicky) skladbami od testovacího bota: řádek po odchodu z chatu,
  pauza, „další" (u poslední není), ✕ za hraní, zmizení po dohrání, ťuknutí na text otevře
  chat se skladbou. Dvoupanel a iOS ověřené nejsou (iOS jen přeložené).

### GIFy

GIF je v Telegramu **MP4 bez zvuku** (`MessageAnimation`), ne skutečný GIF — přehrává se proto
týmž přehrávačem jako video (`MessageMedia.Animation`, `AnimationBubble`).

- **Hraje sám, dokud je vidět, ve smyčce a ztlumeně.** GIF, který se nehýbe, není GIF. Přehrávač
  žije s kompozicí bloku, takže odscrollovaný GIF dekodér vrátí. `LazyColumn` skládá i kousek za
  viewportem, takže pár GIFů hraje mimo obrazovku — proti sledování skutečné viditelnosti je to
  levnější a rozdíl je neznatelný.
- **Ztlumený obsah NEŽÁDÁ o audio focus** (Android `handleAudioFocus = !muted`) a nepřepíná
  kategorii `AVAudioSession` (iOS). Ztlumený GIF nemá co ztišit hudbu, která uživateli hraje —
  a ExoPlayer si o fokus řekne i při nulové hlasitosti.
- **Smyčku `AVPlayer` neumí sám.** Řeší ji pozorovatel `AVPlayerItemDidPlayToEndTimeNotification`,
  který se drží silnou referencí a odhlašuje při uvolnění přehrávače — `NSNotificationCenter` si
  pozorovatele nedrží. `AVPlayerLooper` by znamenal `AVQueuePlayer`.
- **Velký GIF se sám nestahuje** (`autoPlaysWithin` se stropem podle sítě, viz „Automatické
  stahování"; testy `MessageRowsTest`). Autoplay by bez ptaní snědl data, takže nad stropem —
  a u neznámé velikosti — zůstane náhled a čeká na ťuknutí. GIF, který už na disku je, hraje
  i nad stropem. Výběr GIFů má pevný strop 5 MB (`autoPlays`), tam jde o náhled před odesláním. Ťuknutí na hrající GIF ho pozastaví, jinak by nešel zastavit vůbec.
- **GIF jde na plochu bez bubliny** jako fotka a video (`showsBareMedia`): je to obrázek sám
  o sobě. Od videa ho odliší značka „GIF" v rohu a to, že nemá délku.
- **Přehrávání se na emulátoru ověřit NEDÁ**, když je stroj zatížený: `c2.goldfish.h264.decoder`
  selže na `queueInputBuffer error: 14` a ExoPlayer uvízne ve `STATE_BUFFERING` (délka souboru
  je přitom známá). Pozná se to podle toho, že **stejně selže i video v prohlížeči médií** —
  tedy cesta, která s GIFy nesouvisí. Ověřit jde vzhled, značka a přepínání pauzy.
  **Obejít to jde emulátorem bez hardwarového dekodéru**: `emulator -avd … -feature
  -HardwareDecoder`. Systém pak nabídne softwarový `c2.android.avc.decoder` a video hraje
  (ověřeno 15. 10. 2026 na hodinovém videu 720p v prohlížeči).

### Video kolečka

`MessageVideoNote` je `MessageMedia.VideoNote` a kreslí se bez bubliny jako kolečko 200 dp
(`VideoNoteBubble`, cesta přes `StickerMessageItem` — s citací nebo přeposláním v bublině).

- **Samo hraje ve smyčce a potichu, jako GIF** — stejný přehrávač, stejný strop velikosti
  (`autoPlaysWithin` podle sítě), nad ním se nestahuje. Dole je délka s přeškrtnutým reproduktorem, jinak by
  nebylo poznat, že ťuknutí pustí zvuk.
- **Ťuknutí = zvuk od začátku, bez smyčky, s průběhem po obvodu**; další ťuknutí nebo dohrání
  vrátí tichou smyčku. Přepnutí je nové `load` nad týmž přehrávačem (Media3 i AVPlayer to
  snesou), takže video začne znovu od nuly.
- **Se zvukem hraje jedno kolečko na konverzaci a rozhoduje ViewModel**
  (`ConversationState.soundingVideoNote`), ne kolečko samo: ví i o hlasovkách. Kolečko se
  zvukem zastaví hlasovku, hlasovka a nahrávání umlčí kolečko.
- **Kolečko se zvukem hraje dál i odscrollované** — přehrávač má obrazovka a kolečko přejde do
  docku (viz „Mini přehrávač v docku"). Zvuk vrací (`VideoNoteSoundEnded`) až odchod
  z obrazovky, dohrání nebo ✕; jinak by ViewModel dál věřil, že hraje, a po návratu by se
  kolečko samo rozeznělo.
- **Audio focus drží jen kolečko se zvukem** — ztlumený přehrávač o něj nežádá (viz „GIFy"),
  takže tichá smyčka nezastaví hudbu. Ověřeno `dumpsys audio`: focus stack je prázdný při
  smyčce, `GAIN` od Plume jen se zvukem a po dohrání zase prázdný.
- **Zhlédnutí se hlásí jako poslech hlasovky** (`openMessageContent` při prvním puštění se
  zvukem, `isViewed`); potvrzení chodí týmž `contentOpenedUpdates`.
- **Ověřuje se botem**: `sendVideoNote` s MP4. Bez ffmpeg se dá vyrobit na Macu: `adb shell
  screenrecord --size 480x480` **při pohybu na obrazovce** (statickou obrazovku screenrecord
  nezapíše — vyšlo video o 0,4 s) a zvuk z `say -o x.aiff`, spojené přes `AVMutableComposition`
  + `AVAssetExportSession` v krátkém Swift skriptu. Nahrává se obrazovka emulátoru, takže jen
  plocha nebo chat s botem, nic s kontakty.
- **Ověřeno na emulátoru**: tichá smyčka, zvuk s průběhem a uplynulým časem, návrat po dohrání
  i druhým ťuknutím, nabídka dlouhým stiskem. **Zhlédnutí u odesílatele ověřené se dvěma
  účty** (12. 10. 2026): tichá smyčka u příjemce se nepočítá, puštění se zvukem tečku zhasne
  u příjemce i odesílatele. **Tečka „nezhlédnuto" je v odznaku délky** („0:04 • 🔇"), jako
  u hlasovky; dřív ji kolečko nemělo vůbec, takže odesílatel nepoznal, jestli ho protějšek
  pustil. **Neověřeno**: souhra s hrající hlasovkou, přepis řeči (`speechRecognitionResult`) a iOS
  (jen přeložené).

### Nahrávání koleček

Ťuknutí na mikrofon v prázdném poli přepne na kameru (a zpátky), podržení nahraje kolečko:
nad konverzací je náhled z kamery v kruhu s průběhem po obvodu, ovládá se stejně jako hlasovka
(puštění, tah doleva, tah nahoru — viz „Nahrávání"). Volba se pamatuje
(`AppSettings.recordMode`). Hranice je `core/camera` (`VideoNoteCamera`): CameraX na Androidu,
AVFoundation na iOS.

- **Hlasovka i kolečko jdou tímtéž `VoiceRecorder`** nad rozhraním `MediaCapture` (čas, pauzy,
  zahození krátkého). Kamera ale žije s KOMPOZICÍ (potřebuje vlastníka životního cyklu a plochu
  pro náhled), nahrávání s ViewModelem — proto `CaptureSlot`: obrazovka kameru připojí, a když se
  kompozice rozloží, rozpracované kolečko se zahodí. Otočení Foldu do dvoupanelu tak kolečko
  zahodí (hlasovka naopak běží dál zamčená).
- **Kamera se otevírá až se `start`**, ne s náhledem nebo konverzací — indikátor soukromí by
  jinak svítil pořád. `ProcessCameraProvider.getInstance` při otevření chatu kameru neotevírá,
  jen inicializuje CameraX, takže je do podržení hotová.
- **Čtverec dělá `ViewPort` 1 : 1** nad náhledem i `VideoCapture` — kolečko obsahuje přesně to,
  co bylo v kruhu vidět (ověřeno: výstup 720 × 720). Přední kamera se zrcadlí i ve videu
  (`MIRROR_MODE_ON_FRONT_ONLY`), nahrávání je persistent a přežije přepnutí kamery.
- **Náhled je `PreviewView` v režimu `COMPATIBLE` (`TextureView`)**, jinak by ho ořez do kruhu
  v Compose neořízl — `SurfaceView` je samostatné okno.
- **Telegram kolečko větší než 640 px PŘEVEDE NA OBYČEJNÉ VIDEO.** TDLib stranu od 640 výš
  odmítne rovnou („Wrong video note length"); a když se `length` zastropoval, ale soubor zůstal
  720 × 720, server zprávu přijal — jako `video`, ne `video_note` (ověřeno Bot API), takže
  u všech byla obdélníkem s tlačítkem přehrání. Soubor proto musí být opravdu malý.
- **CameraX výstup zmenšit neumí, jen oříznout.** Kvalita je SD (480p → 480 × 480), s fallbackem
  radši nižším; kamera bez SD (emulátor, část telefonů) ale jinak než 720p nenatočí. Takové
  kolečko se po nahrání přepočítá na 480 × 480 přes Media3 Transformer (`shrinkIfNeeded`) —
  jen když je potřeba. Minuta na emulátoru: do deseti vteřin od konce nahrávání i s nahráním
  na server.
- **Po minutě kolečko odejde samo** (`VIDEO_NOTE_MAX_DURATION_MS`, víc Telegram nepřijme),
  pozastavený čas (nabídka, výběr času) se nepočítá. Ověřeno: 60 s, 480 px, 1,9 MB.
- **Délka, strana a náhled se čtou až z hotového souboru** (`prepareVideoNote`), ne z časovače.
- **iOS: `AVCaptureMovieFileOutput` do `.mov` a export do čtvercového MP4** se středovým ořezem
  (`AVMutableVideoComposition` po `preferredTransform`, strana nejvýš 480). **Přepnout kameru
  během nahrávání tam nejde** — výstup do souboru při výměně vstupu nahrávání ukončí; umělo by
  to jen `AVAssetWriter`. **K/N interop:** vlastnosti `AVMutable…` jsou pro Kotlin `val` ze
  základní třídy a zapisují se settery (`setRenderSize`, `setInstructions`…), `videoComposition`
  u exportu je z kategorie (`setVideoComposition` s vlastním importem) a `kCMTimeZero` je
  struktura bez `readValue` — nahrazuje ho `CMTimeMake(0, 1)`.
- **iOS přidává vstupy (`AVCaptureDeviceInput`) až při prvním nahrávání**, ne s konverzací:
  bez uděleného oprávnění vstup selže, nebo si o oprávnění systém řekne sám — a chat se otevírá
  mnohem častěji, než se nahrává.
- **Simulátor kameru nemá**, takže se tam kolečko nenabízí vůbec (`rememberVideoNoteCamera`
  vrátí `null`) a iOS je jen přeložené a slinkované, **na zařízení neověřené**.
- **Oprávnění ke kameře musí být v manifestu i s `uses-feature … camera required=false`**,
  jinak by z kamery udělalo povinný hardware a aplikace by zmizela z Play pro zařízení bez ní.
  Pozor při ověřování: APK sestavené PŘED úpravou manifestu oprávnění nemá a systém se na kameru
  vůbec nezeptá — výsledek pak vypadá jako odmítnuté oprávnění.
- **Zamčené kolečko jde zastavit a přehrát před odesláním** (■ v pruhu, `stopRecordingForPreview`
  → `VideoNotePreview`, `VideoNotePreviewBar`, kruh v `VideoNoteRecordingOverlay`). Místo kamery
  hraje v kruhu nahrané kolečko, a to **jako v bublině**: potichu ve smyčce, ťuknutí na kruh nebo
  na ▶ v kartě ho pustí od začátku se zvukem a s průběhem po obvodu, po dohrání zase potichu.
  Uživatel tak vidí přesně to, co protějšek dostane.
  - **Připravuje se hned po ■** (`prepareVideoNote`), ne až při odeslání: náhled přehrává hotový
    soubor. Mezistav (`note == null`) točí indikátor v kruhu i v kartě a odeslat v něm nejde —
    zmenšení na Androidu i ořez na iOS trvá vteřiny. Koš během dokončování soubory smaže, když
    doběhnou (porovnání instance mezistavu, jako u hlasovky).
  - **Stav přehrávání drží obrazovka** (`VideoNotePreviewPlayback`), ne ViewModel: čtou ho dvě
    místa (kruh s přehrávačem a karta s tlačítkem a časem) a přehrávač žije v kompozici.
  - **Po ■ je `recordingKind` pořád kolečko** (i bez běžícího nahrávání), jinak se odesílací
    tlačítko čtečce hlásilo jako „odeslat hlasovou zprávu".
  - **Odeslání i nabídka podržení jdou přes `sendRecording`**, takže naplánovat nebo poslat bez
    zvuku jde i zastavené kolečko. Koš a odchod z chatu smažou video i náhledový snímek.
  - **Ověřeno na emulátoru** (Pixel_10_Pro_XL, emulovaná kamera) v chatu s botem: zamknout, ■,
    tichá smyčka, zvuk s průběhem a časem (audio focus drží jen po dobu zvuku), návrat po dohrání,
    odeslání (bot dostal `video_note`, 480 px, 19 s) a koš (soubory pryč). iOS jen přeložené.
  - **Po přehrání jde nahrávat dál** (kamera na konci pruhu náhledu, `continueVideoNotePreview`)
    — totéž co u hlasovky, jen spojuje kamera (`VideoNoteCamera.join`) do nového souboru vedle
    nového úseku a vstupy maže až po úspěchu. Náhledový snímek starého kolečka jde pryč hned,
    nový se přečte z výsledku. Kolečko pod minutovým stropem (méně než vteřina místa) tlačítko
    nemá — dovětek by po zlomku vteřiny odešel sám.
    - **Android: Transformer s JEDNOU sekvencí dvou položek**, obě přes `Presentation` na 480 px.
      **Zvuk se musí vynutit do AAC** (`setAudioMimeType`): sekvenci Transformer zvukem vždycky
      překódovává a kodér volí podle vstupu — emulátor nahrává AMR-NB (8 kHz) a s kodérem AMR
      se export zasekl, hlídač ho po 25 s ukončil („Abort: no output sample written in the last
      25000 milliseconds", nahoře „Muxer error"). Zmenšení jednoho úseku (`shrinkIfNeeded`)
      zvuk jen přenáší, proto tam to vidět nebylo.
    - **iOS: `AVMutableComposition`** se stopou obrazu a zvuku a export do MP4. Obě kolečka už
      prošla čtvercovým exportem, takže `AVMutableVideoComposition` netřeba. Jen přeložené —
      simulátor kameru nemá.
    - **Ověřeno na emulátoru** v chatu s botem: 4 s + 4 s, náhled ukázal 0:08, výsledek AAC
      mono 7,9 s, bot dostal `video_note` (8 s, 480 px); koš během navazování smazal původní
      kolečko i rozpracovaný úsek. Po prvním (zaseknutém) pokusu zůstalo původní kolečko
      k odeslání — záchrana funguje.
- **Ověřeno na emulátoru** (Plume_Galerie_Fold, emulovaná přední kamera) v chatu s botem:
  přepnutí ťuknutím s nápovědou, dotazy na mikrofon a kameru, podržení s náhledem v kruhu,
  průběhem a zámkem, puštění (bot dostal `video_note`, 480 px), zrušení tahem doleva, zamčení
  a odeslání ťuknutím, minutový strop, hlasovka podržením, příliš krátký stisk, otočení během
  kolečka i hlasovky, pauza v nabídce odeslání (kolečko mělo 6 s, pět vteřin s otevřenou
  nabídkou se nezapočítalo). **Release build (R8)** nahrál hlasovku i kolečko se zmenšením bez
  pádu — CameraX i Media3 si keep pravidla nesou samy, do `proguard-rules.pro` nic nepřibylo.
  **Neověřeno**: přepnutí na zadní kameru během nahrávání, akce pro
  čtečku obrazovky, skutečný telefon a iOS.

### Přepis řeči

Hlasovka i kolečko mají vedle sebe malé tlačítko „Přepsat do textu"; přepis se ukáže pod
záznamem (u kolečka v kartě v barvě bubliny) a ťuknutím se schová. Doména `SpeechTranscript`
a `TranscribeOffer` (`core/telegram/SpeechRecognition.kt`, testy `TranscribeOfferTest`), UI
`items/Transcript.kt`.

- **Přepisuje server (`recognizeSpeech`) a výsledek NEVRACÍ** — přijde úpravou obsahu zprávy
  (`speechRecognitionResult` u `VoiceNote` i `VideoNote`): napřed `Pending` s částečným textem,
  pak text, nebo chyba. Jde tedy existující cestou `editedMessages` a ViewModel nic nedrží.
  Přepis zůstane ve zprávě i po zavření chatu.
- **I chyba přijde ve zprávě, ne z volání.** Účet bez Premium dostane od `recognizeSpeech` `Ok`
  a pak `SpeechRecognitionResultError` s `403 PREMIUM_ACCOUNT_REQUIRED` — ověřeno na emulátoru.
  Text chyby se uživateli neukazuje; `MSG_VOICE_TOO_LONG` a `PREMIUM_ACCOUNT_REQUIRED` mají
  vlastní větu a znovu se nezkoušejí, cokoli jiného jde zkusit znovu ťuknutím.
- **Kdy nabídnout, rozhoduje Premium a zkušební přepisy** (`UpdateSpeechRecognitionTrial`,
  chodí po startu jednou, proto ho drží eager `ChatRepository` jako emoji kostek). Bez
  zkušebních přepisů (`weeklyCount` 0) se tlačítko nenabízí; došlé přepisy a záznam delší než
  `maxMediaDuration` tlačítko nechají a ťuknutí místo přepisu vysvětlí proč, přímo pod
  záznamem. **Testovací účet má 0 z 0** — bez Premium tedy tlačítko správně nevidí; Telegram
  bezplatné přepisy tomuhle účtu nedává.
- **Ve skupině s dost boosty smí přepisovat každý** (`group_transcribe_level_min`), to zatím
  nepoznáme — tlačítko se tam bez Premium nenabídne.
- **Viditelnost přepisu je stav obrazovky** (`rememberSaveable` podle zprávy), výsledek stav
  zprávy. Přepis, který už ve zprávě je, je vidět hned.
- **Přepis je pod záznamem zalomený na jeho šířku** (`BelowWithSameWidth`, vlastní `Layout`):
  dlouhý text nemá bublinu rozšířit.
- **Hotový přepis jde kopírovat a vybrat** (`copyableText`), **přeložit ne** — server
  překládá text zprávy (`translateMessageText`) a přepis jím není.
- **Ověřeno na emulátoru sondou**: vynucené nabídnutí (server vrátil Premium-chybu a její věta
  se ukázala u hlasovky i kolečka) a podstrčený hotový a rozpracovaný přepis (vzhled, schování).
  **Skutečný přepis ověřený NENÍ** — chce účet s Premium. Hlasovku i kolečko posílá testovací
  bot (`sendVoice` s m4a z `say -v Zuzana`, `sendVideoNote` se záznamem obrazovky emulátoru).

### Přepis v telefonu

Bez Premium (a bez zkušebních přepisů) přepíše hlasovku nebo kolečko **model v telefonu** —
Gemma 4 se zvukovým vstupem (`OnDeviceAi.transcribe`, `LiteRtOnDeviceAi`). Tlačítko je totéž
„Přepsat do textu", nabídka `TranscribeOffer.OnDevice` (testy `TranscribeOfferTest`).

- **Server má přednost**: přepisuje líp a rychleji. Telefon jen tam, kde server odmítne
  (Premium chybí, zkušební přepisy došly nebo žádné nejsou), se zapnutou AI v zařízení
  a připraveným modelem, a jen do 5 minut záznamu (`MAX_ON_DEVICE_SECONDS`).
- **Gemma má přednost, systém je záloha.** Gemma 4 E2B/E4B zvuk umí (katalog Gallery
  `llmSupportAudio`, úloha `llm_ask_audio`) a přepisuje i interpunkci. Gemini Nano přes ML Kit
  zvuk nebere (`canTranscribe` = `false`), takže telefon bez Gemmy přepíše systémem
  (`SpeechToText`, Android `SystemSpeechToText`) — i bez zapnuté AI v zařízení: není to jazykový
  model, nic se nestahuje a zvuk telefon neopouští (stejně jako text z fotky).
- **Systémový přepis: `SpeechRecognizer.createOnDeviceSpeechRecognizer`** se zvukem z roury
  (`EXTRA_AUDIO_SOURCE`, PCM 16 kHz mono z `AudioClips.pcm16k`) a segmentovanou relací
  (`EXTRA_SEGMENTED_SESSION` = zdroj zvuku), aby nepřestal u první pauzy. Obojí i dotaz na
  jazyky (`checkRecognitionSupport` → `installedOnDeviceLanguages`) je až od **Androidu 13**;
  nabízí se jen NAINSTALOVANÝ jazyk aplikace — kvůli přepisu se nic nestahuje. Rozpoznávač se
  smí volat jen z hlavního vlákna (`flowOn(Main)`).
- **Na emulátoru systémový přepis NEJDE**: obraz API 37 má službu
  `AiAiSpeechRecognitionService` (Android System Intelligence), ale
  `isOnDeviceRecognitionAvailable` vrací `false`. Ověřeno jen, že se bez podpory přepis
  nenabídne a nic nespadne, a že přepis Gemmou po změně běží dál. **Na skutečném telefonu
  bez Gemmy neověřené** (roura, segmenty, čeština).
- **Zvuk modelu jako v Gallery**: WAV 16 kHz mono 16 bit, kusy po 30 s (`AudioClips`), každý kus
  vlastní konverzací, text se skládá za sebe. Dekóduje systém (`MediaExtractor` + `MediaCodec`):
  hlasovka OGG/Opus, kolečko MP4/AAC; převzorkování lineárně, kanály průměrem.
- **Engine musí mít zvukový backend** (`audioBackend = Backend.CPU()`, „must be CPU" i v Gallery).
  Načítá se se zvukem až při prvním přepisu; pak poslouží i textovým funkcím, aby se engine
  nepřenačítal. Při načtení si LiteRT-LM zapíše vlastní keš zvukové části do `cacheDir`.
- **Přepis nejde do zprávy** (na serveru ho měnit nejde), ale do `LocalTranscripts` — jeden na
  proces, takže odchod z chatu přežije. **Hotový se ukládá** (`AppSettings.storedTranscripts`,
  po účtech jako záložky, nejvýš 300 nejnovějších, testy `LocalTranscriptsTest`) a přežije
  i restart; rozpracovaný a nepovedený zůstávají jen v paměti. Hotový jde do paměti i do
  úložiště zároveň — zápis do DataStore je asynchronní a jinak by přepis na okamžik zmizel.
  Ověřeno na emulátoru: přepis, `am force-stop`, nový start → přepis u hlasovky hned vidět.
  Pro obrazovku jde spolu s Premium
  a zkušebními přepisy v jednom toku (`transcribeContext`) — `ConversationScreen` je v dexu na
  hraně a dvě `collectAsState` se tak změnila v jedno.
- **Běží v `appScope`**, ne ve `viewModelScope`: odchod z chatu přepis nezruší.
- **Ověřeno na emulátoru s Gemmou 4 E2B na CPU** (Fold s 8 GB RAM): hlasovka od testovacího bota
  (`say -v Zuzana`, m4a, 7,5 s) přepsaná za 11 s včetně načtení zvukové části —
  „Ahoj, posílám ti hlasovku na zkoušku. Seideme se zítra v 10. hlavního nádraží a pak pujdeme
  na Obchod." (originál „…Sejdeme se zítra v deset u hlavního nádraží a pak půjdeme na oběd.").
  Smysl sedí, drobnosti ne — syntetický hlas a nejmenší model. Přepis zůstal po odchodu
  z chatu a návratu. **Neověřeno**: kolečko, skutečný lidský hlas, OGG/Opus z Telegramu (bot
  poslal m4a), Gemma na GPU telefonu.

### GIFy a samolepky z klávesnice

GIF vybraný v Gboardu **odchází hned** jako animace (`InputMessageAnimation`), jako samolepka
z výběru; statický obrázek a samolepka z klávesnice čekají nad polem jako fotka z výběru
(`KeyboardContentReceiver` v `core/ui`, `ConversationViewModel.sendAnimation`).

- **Klávesnice nabízí GIFy jen poli, které řekne, že je přijme** (`EditorInfo.contentMimeTypes`).
  Bez toho Gboard hlásí „animované GIFy nejsou podporované" — a nic z toho není chyba, jen
  chybějící deklarace.
- **`Modifier.contentReceiver` z Compose tu NEJDE.** Funguje jen s `BasicTextField` nad
  `TextFieldState` a naše pole stojí na `TextFieldValue` (viz „Koncept"). Android proto obaluje
  vstupní spojení přes `InterceptPlatformTextInput`: typy se do `EditorInfo` doplní až PO
  původním `createInputConnection` (ten ho vyplňuje a přepsal by je), `commitContent` převezme
  `InputConnectionCompat.createWrapper`.
- **Obal stojí kolem pole vždycky**, i když chat nic nepřijme (pak jen propouští). Větev
  „bez obalu" by pole při přepnutí do úpravy složila znovu a vzala mu fokus.
- **Nabízí se jen to, co chat přijme**: GIF podle práva na samolepky (`canSendOtherMessages`),
  obrázek podle práva na fotky; při úpravě zprávy nic, jako u sponky. Pojistka je i ve ViewModelu.
- **Obsah se kopíruje HNED a oprávnění se pak vrací** (`requestPermission` /
  `releasePermission`). Oprávnění k URI od klávesnice je dočasné, stejně jako u sdílení.
- **Přípona souboru se doplňuje podle typu** (`prepareAnimation`) — jméno od klávesnice bývá
  bez ní a TDLib podle ní pozná, co nahrává. GIF se nepřevádí, na MP4 ho převede server.
- **Průhledný obrázek se před JPEG skládá na bílou** (`opaque()` v `preparePhoto`). JPEG alfu
  nemá a průhledné pixely z něj vyšly černé — samolepka z Gboardu odcházela na černém čtverci.
  Týkalo se to i PNG z výběru příloh.
- **iOS klávesnice GIFy do pole nevkládají** (`commitContent` tam nemá obdobu) — GIF klávesnice
  i samolepky jdou přes SCHRÁNKU. Na iOS proto `KeyboardContentReceiver` dělá jen vložení ze
  schránky (viz níž).
- **Obrázek a GIF ze schránky vkládá vlastní položka kontextové nabídky pole** („Vložit GIF" /
  „Vložit obrázek", `appendTextContextMenuComponents`). Systémové „Vložit" v poli umí jen text
  a s obrázkem ve schránce se ani neukáže. Položka je jen tehdy, když schránka nese typ, který chat
  přijme; rozhoduje `primaryClipDescription`, který se čte při otevření nabídky — popis systém
  jako čtení schránky nehlásí, obsah (`primaryClip`) se bere až po ťuknutí. Dál stejná cesta
  jako obsah z klávesnice (`prepare`): GIF odchází hned, obrázek čeká nad polem.
- **Funguje to jen s NOVOU kontextovou nabídkou** (`ComposeFoundationFlags.isNewContextMenuEnabled`).
  Staré `TextToolbar` vlastní položky nebere. **Na Androidu je v 1.12 výchozí, na iOS NE**
  (`isNewContextMenuInitiallyEnabled = false` ve skiko) — zapíná se v `MainViewController`,
  dřív než se cokoli složí. Nabídku na iOS kreslí dál systém (`UIEditMenuInteraction`), vlastní
  položky jdou do ní jako nativní akce (`CMPEditMenuCustomAction`); systémové „Vložit" textu
  funguje dál (ověřeno na poli s číslem při přihlášení).
- **iOS: typ ve schránce se zjišťuje `containsPasteboardTypes`**, které systém nehlásí jako
  čtení. Data (`dataForPasteboardType`) až po ťuknutí — a protože to není systémové „Vložit",
  iOS se zeptá „Povolit vložení?". Obejít to jde jen `UIPasteControl` (tlačítko kreslí systém).
  Typy jsou UTI: `com.compuserve.gif`, `public.mpeg-4`, `public.png`/`jpeg`/`heic`,
  `org.webmproject.webp`. GIF má přednost — z téže položky by obrázek byl jen první snímek.
- **Na simulátoru se to ověří bez podstrkávání**: GIF do schránky Macu (`osascript -e 'set the
  clipboard to (read (POSIX file "…/x.gif") as «class GIFf»)'`), pak `xcrun simctl pbsync host
  <zařízení>`. Dotaz „Povolit vložení?" se ukáže s původem „CoreSimulatorBridge". Ověřeno:
  „Paste GIF" v nabídce, po povolení GIF odešel a hraje.
- **Bez dotazu „Povolit vložení?" vkládá jen `UIPasteControl`** — systémové tlačítko
  (`ClipboardPasteSuggestion`). Když má pole fokus a schránka nese GIF / obrázek, nad polem se
  ukáže pruh „GIF ve schránce" s tímto tlačítkem. Položka v kontextové nabídce zůstává, ale ptá se.
  - **Interop view musí být `placedAsOverlay = true`.** Výchozí interop leží POD plátnem Metalu
    a prosvítá průhledným otvorem; iOS pak tlačítko považuje za zakryté, ťuknutí neprojde
    ověřením a dotaz na povolení přijde stejně. Nic se nehlásí — pozná se to jen podle dotazu.
    Průhledné pozadí (`clearColor`) k tomu, jinak má kapsle bílé rohy.
  - **`intrinsicContentSize` u `UIPasteControl` rozměr NEHLÁSÍ** (−1), Compose mu dal nulovou
    plochu a tlačítko nebylo vidět. Měří se `systemLayoutSizeFittingSize`, s minimem.
  - **Cíl je `UIResponder` s `pasteConfiguration`** (přijímané UTI) a `pasteItemProviders`;
    data z `NSItemProvider.loadDataRepresentation` chodí na vlákně na pozadí.
  - **Vložený obsah se podruhé nenabízí**, pamatuje se `changeCount` schránky pro celý proces.
    Při vložení se čte ŽIVÝ `changeCount` — mění se i bez notifikace (jiná aplikace, na
    simulátoru synchronizace se schránkou Macu), a s číslem z doby zobrazení pruh zůstal viset.
  - Android návrh nekreslí — tam se na vložení nikdo neptá.
- **`preparePhotoData` na iOS skládá na bílou** (`opaque = true` + `UIRectFill`), ze stejného
  důvodu jako `opaque()` na Androidu.
- **Obrázek do schránky emulátoru z `adb` nedostaneš** — `cmd clipboard` neexistuje a Chrome
  i Fotky chtějí nejdřív projít úvodním souhlasem. Ověřuje se podstrčením: dočasně v debug buildu
  `setPrimaryClip(ClipData.newUri(…))` s GIFem v `cache/shared` (vydává ho provider `…documents`;
  soubor se tam dostane `adb push` do `/data/local/tmp` a `run-as … cp`). Pozor: po zápisu ukáže
  Android vlevo dole náhled schránky a dlouhý stisk v tom místě otevře systémové sdílení, ne pole.

### Video

Video je vlastní typ přílohy (`MessageMedia.Video`: soubor, náhled, rozměry, délka). V bublině
a v albu je náhled s tlačítkem přehrávání a délkou; přehrává se v prohlížeči, listuje se fotkami
i videi spolu (`SearchMessagesFilterPhotoAndVideo`).

- **Přehrává se až v prohlížeči na celé obrazovce, ne v bublině ani v galerii.** Video v seznamu
  by se pralo se scrollem a swipem na odpověď a každá viditelná bublina by držela dekodér.
  V galerii je náhled s tlačítkem přehrání; ťuknutí přepne na celou obrazovku a teprve tam se
  video stahuje a hraje. Návrat do galerie ho pozastaví.
- **Ťuknutí na celé obrazovce u videa napřed ukáže schované ovládání**, teprve ťuknutí
  s ovládáním na očích vede do galerie. Jinak by se u hrajícího videa k posuvníku nedalo dostat.
- **Video se streamuje z rozestavěného souboru** (Android; `StreamingSource`,
  `PartialFileDataSource`). ExoPlayer dostane vlastní `DataSource`, který čte soubor TDLib
  a místo konce vstupu POČKÁ, až data dorazí — `FileDataSource` by na konci rozestavěného
  souboru ohlásil konec a přehrávání by po pár vteřinách skončilo. Stahovat začne až vybraná
  stránka; soused složený dopředu ne.
- **Bez přesouvání stahování by to nefungovalo.** Přehrávač si hned na začátku řekne o `moov`
  atom, a ten u většiny videí leží na KONCI souboru; TDLib přitom stahuje od začátku, takže by
  se na něj čekalo marně. `StreamingSource.requestFrom` proto volá `downloadFile` s offsetem.
- **`downloadedPrefixSize` se počítá od `downloadOffset`, ne od začátku souboru.** Po skoku na
  konec je dostupný úsek `downloadOffset ..< downloadOffset + prefix` — prvních N bajtů
  dostupných NENÍ, i když se předtím stáhly (testy `FileStateTest`). Kdo to plete, čte díry.
- **`supportsStreaming` od serveru říká, co tvrdí odesílatel, ne kde index doopravdy je.**
  Ověřeno: video poslané botem s `supports_streaming=true` mělo `moov` na konci. Nedá se z něj
  tedy usuzovat, že skok nebude potřeba — jen se podle něj streamování vůbec zkusí.
- **Čekání na data blokuje vlákno ExoPlayeru, a tak to má být** — je to jeho vlastní vlákno pro
  načítání. Po 30 s bez nových dat se čtení vzdá: stahování mohlo spadnout a věčné čekání by
  vypadalo jako zamrzlý přehrávač.
- **Dostažený soubor TDLib PŘESUNE** z `files/tdlib-files/temp/<n>` na konečnou cestu
  (`videos/…`) a `FileState.Downloading` vystřídá `Ready` — bez `downloaded`. Zdroj streamu proto
  bere cestu jako FUNKCI (`StreamingSource.path`) a `VideoPage` mu po dokončení podá konečnou
  cestu a celý soubor jako stažený; přehrávač se nepřenačítá. Dokud si zdroj držel dočasnou
  cestu, šlo video přehrát jen jednou: přehrání od začátku (i skok) soubor OTEVÍRÁ ZNOVU
  a skončilo `ExoPlaybackException: Source error` / `FileNotFoundException … temp/70: ENOENT`.
  Hrající video přesun nepozná — už otevřený soubor přejmenování přežije. Ověřeno dočasným
  logem v `PartialFileDataSource.open`: první přehrání čte `temp/175`, opakované
  `videos/out2.mp4`.
- **Chyba přehrávače se čte jako stav** (`VideoPlayer.isFailed`: `playerError`, na iOS
  `AVPlayerItemStatusFailed`). Bez ní zůstalo optimistické `playing = true` z tlačítka a UI
  tvrdilo, že hraje. Po chybě je vidět tlačítko přehrát s hláškou a `play()` přehrávač
  připraví znovu (`prepare()` pokračuje z místa chyby). Ověřeno sondou s neexistující cestou.
- **Streamovací cestu ověří jen video, které TDLib ještě NEMÁ**: stažené jde přes `load(path)`
  a chyba se neukáže. Každý pokus proto chce nový soubor — k MP4 stačí připojit box `free`
  s náhodnými bajty (jinak TDLib soubory se stejným obsahem sloučí).
- **iOS zatím streamovat neumí** (`videoStreamingSupported = false`). `AVPlayer` by potřeboval
  `AVAssetResourceLoaderDelegate` — vlastní zdroj odpovídající na požadavky na rozsahy bajtů;
  do té doby se tam čeká na celý soubor.
- **Velký průběh uprostřed jen do ROZBĚHNUTÍ videa** (`hasPlayed` = přehrávač poprvé hlásí
  `isPlaying`), ne dokud se stahuje. U streamovaného videa je soubor „načtený" hned, ale
  přehrávač ještě čeká na data — procento staženého souboru by tam lhalo (hraje se dávno před
  100 %), proto se po načtení točí neurčitě. Tlačítko play/pauza se do té doby neukazuje.
- **Stahování pod hrajícím videem je kroužek v pravém horním rohu** (`VideoDownloadIndicator`);
  ťuknutí ho roztáhne do čáry přes horní okraj karty, další ho svine. Nahoře, protože kroužek tam
  už je a jen se roztáhne do šířky; dole by se čára přetahovala o místo s posuvníkem.
- **Jsou to běžné `CircularWavyProgressIndicator` / `LinearWavyProgressIndicator`** a přechod je
  obyčejné `AnimatedContent` se `SizeTransform`, rostoucí z rohu (`contentAlignment = TopEnd`).
  První verze kreslila na `Canvas` oblouk, který se rozvine do čáry — vymyšlený prvek, který
  nikde jinde není; uživatel chtěl standardní vzhled. Ukazuje se proto celkový `progress`, ne
  souvislý stažený úsek: obě komponenty kreslí průběh od nuly.
- **Podklad pilulky nese kontejner `AnimatedContent`, ne obsah.** Velikost se animuje na
  kontejneru; podklad na obsahu `SizeTransform` ořízne a pilulka roste s hranatým okrajem.
- **Na emulátoru se stahování zpomalit NEDÁ**: `adb emu network speed edge`/`gprs` odpoví OK,
  a 2,5MB video se přesto stáhne za vteřinu. Vzhled indikátoru se proto ověřuje podstrčeným
  stavem (`downloadVisible` bez podmínky na `Downloading`, pevný `progress`), jako karta aktualizace.
  **Přechod jde prohlédnout jen zpomaleně**: `adb shell settings put global
  animator_duration_scale 10` (Compose ho respektuje) a série `screencap` — v normální
  rychlosti ho snímky minou.
- **Obraz v obraze** (`rememberPictureInPicture` v `core/video`): tlačítko v liště videa
  a samočinný přechod při odchodu z aplikace, **jen dokud video hraje** — pozastavené okénko
  nepotřebuje. Jen v prohlížeči médií a jen pro stránku, která hraje na celé obrazovce;
  mizející video ne (okénko nad jinými aplikacemi by obešlo `FLAG_SECURE` i odpočet).
  - **Android posílá do okénka CELOU aktivitu** (`supportsPictureInPicture` na `MainActivity`),
    takže `VideoPage` během PiP schová ovládání a nechá jen obraz. Parametry
    (`setPictureInPictureParams`) platí pro aktivitu, ne pro obrazovku, a okénko chce
    i videohovor — **vede je jeden rozhodčí na aktivitu** (`PictureInPictureArbiter`, viz
    `docs/poznatky/hovory.md`, „Obraz v obraze"). Prohlížeč se při odchodu z kompozice
    odhlásí, jinak by aplikace šla do okénka i ze seznamu chatů (ověřeno: po zavření
    prohlížeče Home okénko neotevře — leda probíhá videohovor, pak jde do okénka on).
  - **`smallestScreenSize` musí být v `configChanges`.** Přechod do okénka mění velikost okna;
    bez něj by se aktivita vytvořila znovu a přehrávač s ní zanikl.
  - **Automatika:** od Androidu 12 `setAutoEnterEnabled(playing)`, starší přes
    `addOnUserLeaveHintListener`. Akce v okénku je `RemoteAction` (přehrát / pozastavit)
    s broadcastem jen pro vlastní balíček.
  - **Zavřené okénko i odchod na pozadí video pozastaví** (`LifecycleEventEffect(ON_STOP)`).
    Dřív video bez okénka po Home hrálo dál na pozadí, dokud proces žil.
  - **iOS** jde přes `AVPictureInPictureController` nad vrstvou přehrávače (`VideoPlayer.layer`,
    stav — povrch se skládá až po přehrávači) a `canStartPictureInPictureAutomaticallyFromInline`;
    na pozadí hraje díky `UIBackgroundModes: audio`. Jen přeložené.
  - **Při ověřování okénko odkryje aplikaci pod emulátorem** (v Androidu cokoli, co bylo v popředí
    předtím) — snímky obrazovky s PiP zachytí i cizí obsah. Stav se čte z `dumpsys activity
    activities` (`mode=pinned`), a na okénko se ťuká jen tam, kde okénko opravdu leží
    (`mBounds` úlohy), jinak ťuknutí dopadne do aplikace pod ním.
  - **Ověřeno na emulátoru:** tlačítko, automatický přechod při Home s hrajícím videem (bez hraní
    se nic neděje — správně), video v okénku dohraje, návrat z okénka bez nového vytvoření aktivity,
    zavření okénka tahem video pozastaví. **Akce přehrát / pozastavit v okénku ověřená NENÍ**
    (nabídka okénka se na emulátoru nedala spolehlivě trefit).
- **Přehrávač je v kompozici** (`rememberVideoPlayer`) a s ní se uvolní. Hraje jen vybraná
  stránka; stav (pozice, délka, dohráno) se čte po 200 ms, přehrávače ho samy nehlásí.
  Spuštění videa zastaví hrající hlasovku.
- **Android: Media3 ExoPlayer do `TextureView`**, ne `PlayerView`. Ten kreslí do `SurfaceView`,
  samostatného okna, které se s posunem vrstvy nehýbe — video by při tahu na zavření stálo.
  Jen `media3-exoplayer` + `media3-ui` (kvůli `AspectRatioFrameLayout`): `media3-ui-compose`
  táhne androidx Compose novější, než drží Compose Multiplatform. Audio focus řeší ExoPlayer.
- **iOS: `AVPlayer` + `AVPlayerLayer` v UIKit view** (`UIKitView`, `interactionMode = null`, aby
  dotyky šly do Compose). Vrstva velikost s view nemění sama — nastavuje se v `layoutSubviews`
  bez implicitní animace. „Dohráno" AVPlayer stavem nehlásí, pozná se podle pozice u konce.
- **Tah na zavření posouvá video `offset`em, ne `graphicsLayer`** — nativní view (UIKit) dostane
  jen pozici z layoutu, transformaci vrstvy ne.
- **Náhled MPEG-4 se zahazuje** (`ThumbnailFormatMpeg4`) — není to obrázek a Coil ho neotevře.
- **Dráha posuvníku se vlní, dokud video hraje** (`LinearWavyProgressIndicator` ve slotu
  `track` u `Slider`), po pozastavení, dohrání i během tahu je rovná. Tentýž význam jako vlnka
  hrající hlasovky („teď zní") a jako systémový přehrávač médií. Přechod amplitudy animuje
  indikátor sám, stačí mu podat 0 nebo 1. Vlnky se posouvají jen s viditelným ovládáním —
  schované ovládání odchází z kompozice i s animací. Ověřeno na emulátoru (hraje / pozastaveno),
  iOS jen přeložené.
- **Rychlost a ztlumení jsou v liště pod videem**, za časem: čip rychlosti (tentýž `SpeedChip`
  a stupně 1× / 1,5× / 2× jako u hlasovky) a reproduktor. Na přehrávači `setSpeed` a `setMuted`.
  - **Rychlost si aplikace pamatuje zvlášť od hlasovek** (`AppSettings.videoSpeed`): kdo poslouchá
    hlasovky na 2×, nemusí se tak dívat na video. Platí i v náhledu při sdílení a nad polem.
  - **Ztlumení drží prohlížeč, ne stránka, a neukládá se.** Na stránce by se po odlistování další
    video zase rozeznělo; uložené by příště pouštělo video potichu bez vysvětlení.
  - **Obojí se zapisuje až PO načtení** (efekty klíčované `loaded`): `load` hlasitost přenastaví.
  - **Ztlumení za běhu audio focus NEPUSTÍ** — video pořád běží a hudba se pod ním rozeznít nemá.
    Jinak než `muted` v `load` (GIF), který o focus vůbec nežádá.
  - **iOS `play()` vrací rychlost na 1×**, takže se drží v přehrávači a zapisuje po každém
    spuštění; do pozastaveného `rate` nepsat, rozjelo by ho.
  - **Na emulátoru ověřené jen ovládání**: čip přepíná a rychlost vydrží zavření prohlížeče,
    ztlumení ne. Přehrávání samo emulátor nepustil (`queueInputBuffer error: 14` u všech videí,
    viz „GIFy") — **že video opravdu hraje rychleji a potichu, ověřené NENÍ**.

- **Displej při přehrávání nezhasíná** (`KeepScreenOn` v `core/ui`, jen dokud video hraje,
  i v okénku PiP). Dřív ho nedržel nikdo: `PlayerView` z Media3 to dělá sám, jenže ten kreslí do
  `SurfaceView` (viz výš) a my máme `TextureView`. Na samotný povrch to dát nejde — `VideoSurface`
  kreslí i GIFy v bublinách, které hrají pořád, a displej by nezhasl nad otevřeným chatem.
  Požadavky se sčítají (ne uložit a vrátit), stejně jako u `DarkSystemBars`. iOS:
  `idleTimerDisabled` (`AVPlayerLayer` ho na rozdíl od `AVPlayerViewController` nenastaví).
- **Vytažená sluchátka video pozastaví** (`setHandleAudioBecomingNoisy` v `load`, Android).
  Jen se zvukem: ztlumený GIF v bublině by se jinak po vytažení sluchátek zastavil. Na iOS to
  `AVPlayer` dělá sám.
- **Celá obrazovka jako v přehrávači** (`VideoFullscreen`, drží ho prohlížeč, ne stránka):
  - **Systémové lišty jdou pryč spolu s ovládáním** a vracejí se s ním (`HideSystemBars`), jako
    YouTube; pozastavené video má ovládání na očích, a tedy i lišty. Tah od okraje lištu ukáže
    jen na chvíli. Horní roh se odsazuje podle místa lišty i schované (`statusBarArea`): lišta
    se vrací spolu s ovládáním a tlačítka by během jejího vyjetí poskočila. iOS schová stavovou
    lištu přes hostitele controlleru (`prefersStatusBarHidden`); ukazatel plochy NE —
    `prefersHomeIndicatorAutoHidden` je v kategorii UIKit a Kotlin/Native ji přepsat nedovolí
    („overrides nothing").
  - **Zámek proti dotykům** (zámek vlevo nahoře): tah nezavírá, ťuknutí nepřepíná, ovládání
    se neukáže a listování je vypnuté (`MediaCarousel(userScrollEnabled)`). Ťuknutí kamkoli
    vytáhne vyplněný zámek, kterým se odemyká; po zamčení se na chvíli ukáže sám. **Zpět
    za zamčení nezavírá**, jen ukáže zámek — tah od okraje je ten nejčastější nechtěný dotyk.
    Jde to přes `NavigationBackHandler` zapnutý jen za zamčení; `BackHandler` by zabil predictive
    back i odemčenému prohlížeči.
  - **Ruční otočení** (vedle zámku): do té druhé orientace, než v jaké okno právě je, i se
    systémovým automatickým otáčením vypnutým (`SENSOR_LANDSCAPE` / `PORTRAIT`). Platí jen na
    celé obrazovce a pro další videa taky; návrat do galerie orientaci pustí. Požadavky jsou
    zásobník (`LockScreenOrientation`), ne uložit a vrátit — past z `DarkSystemBars`.
    **Na velkém displeji (nejmenší šířka od 600 dp) se tlačítko neukazuje**: Android 16+
    s `targetSdk` 37 tam orientaci aplikace ignoruje. **iOS ho nemá**: kořen je SwiftUI
    `UIHostingController`, který podporuje všechny orientace z Info.plist, takže
    `requestGeometryUpdate` by telefon otočil jen do prvního pohybu; zamknout by to šlo jen
    přes `supportedInterfaceOrientationsFor` v delegátu aplikace.
  - **Dole místo není**, proto zámek a otočení leží nahoře: s dalšími dvěma tlačítky by
    posuvníku na telefonu na výšku zbyl palec.
  - **Na úzké kartě má posuvník vlastní řádek** (pod 480 dp, `VIDEO_CONTROLS_ONE_LINE_WIDTH`),
    tlačítka jdou pod něj doprava. Video na výšku otočené na šířku má kartu široká asi 400 dp
    a v jednom řádku s časy a tlačítky posuvník stlačilo na nulu; na telefonu na výšku mu
    zbývalo kolem 100 dp. Rozhoduje šířka KARTY (`BoxWithConstraints`), ne okna.
  - **Ověřeno na emulátoru** (Pixel 9, Android 17, software dekodér, viz „GIFy"): příznak
    `KEEP_SCREEN_ON` na okně jen během přehrávání (`dumpsys window windows`), lišty schované
    s ovládáním (`statusBars visible=false`), zámek vydrží tah dolů, do strany, klávesu i gesto
    zpět (první tah od okraje jen vytáhne systémové lišty, druhý ukáže zámek), haptika 21 / 22,
    otočení `SENSOR_LANDSCAPE` ↔ `PORTRAIT` a návrat do galerie vrátí `UNSPECIFIED`. **Pauza po
    vytažení sluchátek ověřená NENÍ** (emulátor sluchátka nemá), iOS jen přeložené.

- **Pozice přehrávání se pamatuje** (`VideoPositions` v `core/video`, uložené v `AppSettings`
  jako seznam, testy `VideoPositionsTest`). Klíč je `RemoteFile.uniqueId`, ne id zprávy:
  přeposlané video je týž soubor a pokračuje se v něm odkudkoli.
  - **Jen delší videa** (od 2 min), ne prvních 10 s a ne posledních 15 s; dohrané se zapomene.
    Nejvýš 200 záznamů, nejstarší odpadají.
  - **Zapisuje se při odlistování, odchodu z kompozice, `ON_STOP`, dohrání a každých 10 s**
    během hraní (zabitý proces). Jen u videa, které opravdu hrálo (`hasPlayed`): otevření
    a zavření bez puštění by zapsalo nulu, a tím pamatovanou pozici smazalo. Pozice se bere
    z posledního tiknutí, ne z přehrávače — při odchodu z kompozice už může být uvolněný.
  - **Odkud začít**: čas z odkazu ve zprávě > pamatovaná pozice > `MessageVideo.startTimestamp`
    (přeposlání „od 1:23", dřív se nečetl). Pamatovaná pozice se čte jednou, při složení
    stránky — během sledování se přepisuje.
  - **Po rozběhnutí z pamatované pozice** se na 6 s ukáže „Pokračuje od 12:34" s tlačítkem Od
    začátku. Bez toho by video hrálo uprostřed bez vysvětlení.
  - **Bublina má proužek zhlédnutého** přes spodek náhledu (`WatchedBar`), bílý na tmavé dráze
    jako posuvník — leží na libovolném obrázku.
  - **Mizející médium a tajný chat se nepamatují**: první se podruhé neotevře, po druhém nemá
    v zařízení zůstat stopa.
- **Dvojí ťuknutí do levé / pravé půlky skočí o 10 s** (víc ťuknutí rychle za sebou se sčítá,
  u okraje se ukáže „−20 s"), **podržení pustí video 2×**, dokud prst leží (pilulka „Rychlost 2×"
  nahoře, uložená rychlost se po puštění vrátí). Jen u hrajícího videa na celé obrazovce
  a ne zamčeného. Detektor s dvojím ťuknutím čeká s jednoduchým, jestli nepřijde druhé — proto
  se zapíná jen tam, v galerii by zdržel přepnutí na celou obrazovku.
- **Náhled snímku nad posuvníkem** při tahu (`StoryboardThumb`, testy `StoryboardTest`) ze
  storyboardu, který server sám vyrábí k delším videím (`MessageVideo.storyboards`: JPEG
  s dlaždicemi a mapa). **Formát mapy není nikde popsaný** — převzatý z oficiálního klienta pro
  Android (`VideoSeekPreviewImage.parseStoryBoardMap`): řádky `file=mtproto:…`,
  `frame_width=`, `frame_height=` a pak `čas,x,y` (čas v sekundách s desetinnou částí).
  Stahuje se, až video na celé obrazovce hraje. Kreslí se celý obrázek posunutý a ořezaný na
  dlaždici (Coil v původní velikosti — zmenšený by posunul souřadnice z mapy).
- **Ověřeno na emulátoru** (Pixel 9, Android 17, software dekodér, hodinové video v kanálu):
  „Pokračuje od 1:38 · Od začátku" po znovuotevření, zápis každých 10 s i při zavření (pozice
  ~29 min po skoku posuvníkem, `app_settings.preferences_pb` přes `run-as`), proužek v bublině
  na polovině, náhled dlaždice nad posuvníkem jede s prstem, dvojí ťuknutí vpravo „+10 s"
  (haptika 26), podržení „Rychlost 2×" (haptika 0). Krátké video (89 s) se nezapsalo, jak má.
  **Jednou se ale pozice po skoku posuvníkem neuložila** (zůstala ta před skokem) a znovu se
  to vyvolat nepodařilo; byl to přetížený emulátor uprostřed sledu gest. Kdyby se to ukázalo
  na telefonu, začít logem v `saveProgress`.
  Že podržení opravdu zrychlí přehrávání, je vidět jen okem (ExoPlayer rychlost nehlásí).

### Text z fotky

⋮ → „Text z fotky" rozpozná text v otevřené fotce, celé v telefonu: ML Kit z Play služeb na
Androidu, Vision na iOS (`core/ocr`, `TextRecognizer`). Bloky se zvýrazní přímo na snímku
(`PhotoTextHighlights`) a na místě pruhu reakcí vyjede panel s textem (`PhotoTextPanel`) — úsek
jde vybrat, „Kopírovat vše" zkopíruje celý. Ťuknutím na řádky ve fotce jde vybrat jen některé:
panel pak ukazuje a „Kopírovat výběr" kopíruje jen je (`PhotoText.selected`).

- **Knihovna jen v `core/ocr`**, zbytek aplikace vidí `RecognizedText` — jako u `core/ai`.
- **ML Kit z Play služeb (`play-services-mlkit-text-recognition`), ne přibalený model.** Přibalený
  (`com.google.mlkit:text-recognition`) by APK zvětšil o ~4 MB na ABI. Model si Play služby
  stáhnou hned po instalaci, protože to manifest aplikace žádá (meta-data
  `com.google.mlkit.vision.DEPENDENCIES` = `ocr`); dokud stahování neskončí, rozpoznání selže
  a uživatel dostane „zkus to za chvíli" (`null` z `recognize`), ne „na fotce není text".
- **Latinka stačí na češtinu** — ověřeno na emulátoru: „Káva s mlékem 89 Kč", „Děkujeme za
  návštěvu!" i s háčky a čárkami.
- **Rozpoznává se OSTRÁ verze** (`fullFileId`), stažená tímtéž `prepare` jako uložení; po dobu
  stahování a rozpoznání točí ⋮ (`MediaTask.Text`). Ve zmenšenině z bubliny drobné písmo
  nepřečte ani model. Obrázek se dekóduje zmenšený na 2048 bodů — víc rozpoznání nepomůže.
- **Rozpoznává se po ŘÁDCÍCH, ne po blocích** (`RecognizedLine`). Blok z ML Kitu je odstavec —
  na účtence by jedno ťuknutí vybralo pět položek i s cenami; Vision vrací řádky sám, takže
  `block.lines` z ML Kitu dává obě platformy na stejnou úroveň. Daň: text ve dvou sloupcích
  se v celém textu prokládá řádek po řádku (řádky na stejné výšce spojí `readingRows`). Na
  účtence je to naopak správně („Káva 89 Kč"), a sloupec si uživatel teď vybere ťukáním.
- **Výběr řádku se pozná až při zvednutí prstu a spotřebuje se jen to zvednutí**, dotyk sám
  zůstává volný. Gesta stránky (`ZoomableMediaPage`, rodič) dostávají pohyb dál: štípnutí,
  posun přiblížené fotky, zavírací tah i listování začaté na řádku fungují a ťuknutí na řádek
  se tím zruší (pohyb spotřebují ony). Spotřebované zvednutí zruší ťuknutí stránky, takže
  výběr nepřepne celou obrazovku. Daň: dvojité ťuknutí NA ŘÁDKU nepřibližuje, jen řádek dvakrát
  přepne — vedle řádků přibližuje dál. Ťuknutí na řádek se trefuje s přesahem 12 dp a vyhrává
  nejbližší řádek, drobné písmo by jinak přesně trefit nešlo.
- **Barva vybraného řádku je barva výběru textu** (`LocalTextSelectionColors`), stejná jako při
  výběru úseku v panelu — oboje je „vybraný text".
- **Čtečka obrazovky** dostane každý řádek jako zaškrtávací prvek s jeho textem (neviditelné
  boxy na místě řádků nad `Canvas`). `uiautomator` je ukáže jako `checkable` s `checked`.
- **Souřadnice bloků jsou podíly rozměrů obrázku** (0–1, počátek vlevo nahoře). Zvýraznění leží
  UVNITŘ karty se snímkem, která má poměr stran fotky, takže se s ní přibližuje a posouvá bez
  přepočtu. Vision má počátek VLEVO DOLE — převrací se při převodu.
- **Výřezy do závoje kreslí `BlendMode.Clear` a ten potřebuje `CompositingStrategy.Offscreen`**,
  jinak by vymazal i fotku pod závojem.
- **Pořadí čtení si dělá aplikace** (`readingRows`, testy `RecognizedTextTest`): Vision vrací
  řádky v pořadí, jak je najde. Bloky na jednom řádku spojí mezera, řádky zalomení — napoprvé
  byl mezi každým blokem prázdný řádek a z účtenky „Káva s mlékem / 89 Kč" vyšly dva odstavce.
- **Panel, ne modální sheet**: sheet by fotku zakryl závojem a zvýraznění, podle kterého
  uživatel pozná, co se přečetlo, by nebylo vidět.
- **Jen když jde fotku uložit** (`canSave`): v chatu se zakázaným ukládáním by text z fotky
  obešel tutéž ochranu. Video ani mizející médium položku nemají.
- **Odlistování text zahodí** (`onPageChanged`): zvýraznění patří fotce, a po návratu by
  překvapilo.
- **Ověřeno na emulátoru Fold** (Android 17, Play služby): fotka od testovacího bota, zvýraznění,
  panel, kopírování (snackbar i systémový náhled schránky), zavření křížkem, odlistování.
  **iOS jen přeložené; release s R8 neověřený** (ML Kit nese vlastní keep pravidla).
- **Výběr řádků ověřený na emulátoru Pixel 7** (Android 16, Play služby) na účtence od
  Notifikátoru: řádky položek a cen zvlášť, výběr a zrušení ťuknutím (haptika 21 / 22),
  „Kopírovat výběr" (systémový náhled schránky „89 Kč"), Zrušit výběr, ťuknutí vedle řádku
  přepne celou obrazovku a výběr jde i v ní, vodorovný tah začatý na řádku odlistuje bez
  výběru, uzly pro čtečku. **Štípnutí začaté na řádku se přes adb ověřit nedá; iOS jen
  přeložené.** Rámeček řádku od ML Kitu občas ukrojí první znak („K", „4") — přepočet
  souřadnic to podle všeho není (obrázek se nezmenšoval), ale ověřené to není.

## Spoilery a mizející média

Dva různé mechanismy TDLib. **Spoiler** (`hasSpoiler`) je jen rozmazání, fotka v historii zůstává
a ťuknutí ji odkryje. **Mizející médium** (`selfDestructType`: jednorázové zhlédnutí nebo časovač)
se otevírá rovnou na celou obrazovku bez listování a po zhlédnutí nebo odpočtu ho TDLib vymění
za `MessageExpiredPhoto` / `MessageExpiredVideo`.

- **Mizení se nabízí jen jedné příloze v soukromém chatu** (`selfDestructAllowed`
  v `StagedAttachments.kt`, testy `StagedAttachmentsTest`). Jinde TDLib odpoví „Messages can
  self-destruct only in private chats" (ověřeno `libtdjsonjava.so`) — a to až po odeslání.
  Dokud to hlídané nebylo, výběr režimu nabízel časovač i ve skupině a v kanálu.
- **Z alba mizení VIDITELNĚ zmizí** (`dropForbiddenSelfDestruct`, snackbar). Kdo k mizející
  fotce přidá druhou, dostane album; tiché přepnutí až při odeslání by poslalo trvale fotku,
  o které si odesílatel myslel, že zmizí.
- **Zbývající čas je od TDLib, ne délka časovače** (`ChatMessage.selfDestructIn`, čte se po
  `openMessageContent`). Odpočet běží od prvního otevření — i na jiném zařízení nebo před
  restartem — a `SelfDestructTimers` drží jen paměť procesu. Začít znovu od plné délky by obsah
  ukázalo déle, než odesílatel dovolil; proto `startTimer` odpočet umí zkrátit, prodloužit ne.
- **Vlastní odeslané mizející médium se u odesílatele nespotřebovává.** Odpočet patří příjemci;
  dokud se spouštěl i odesílateli, viděl u své zprávy „vypršelo", i když ji příjemce ještě
  neotevřel — a po restartu zase fotku, protože se to pamatovalo jen v paměti.
- **Prohlížeč s mizejícím médiem má `FLAG_SECURE`** (`SecureScreen`): snímek obrazovky vyjde
  černý a obsah se neukáže ani v přehledu úloh. iOS snímky zakázat neumí, tam je `SecureScreen`
  prázdné.
- **Mizející média nejsou v listování prohlížeče** (`visualMedia` je vyřazuje), nemají v něm
  reakce, sdílení ani uložení, a notifikace k nim nepřikládá obrázek — stejně jako ke spoileru.
- **Časovač u videa NENÍ to, co nastavil odesílatel.** TDLib ho při přijetí prodlouží aspoň na
  délku videa + 1 s (`ttl.ensure_at_least(get_message_content_duration(…) + 1)`
  v `MessagesManager.cpp`), a `MessageSelfDestructTypeTimer` už nese prodlouženou hodnotu.
  Video délky 6 s se 3s časovačem má v odznaku 7 s — to je správně, chyba to není. Délku
  videa zapisuje do zprávy odesílatel.
- **A délku TDLib zaokrouhluje NAHORU** (`std::ceil(video->duration_)` v
  `DocumentsManager.cpp`; server ji posílá s desetinami). Oficiální klient ji ukazuje
  zaokrouhlenou dolů, takže u videa, které má u odesílatele „▶ 3s" (ve skutečnosti 3,x s),
  vyjde u příjemce časovač 5 s: 4 + 1. Obě „sekundy navíc" jsou tedy od TDLib, ne od nás.
- **Odpočet začíná `openMessageContent`, a proto se volá až nad STAŽENÝM médiem**
  (`awaitSelfDestructMedia`). Dřív se volal hned při otevření prohlížeče: stahování ukrojilo
  z časovače (video 11 s, v prohlížeči se objevilo 7 s) a prohlížeč se zavřel dřív, než
  video dohrálo — časovač má na délku videa rezervu jedné vteřiny. Mizející video se ze
  stejného důvodu nestreamuje (`VideoPage(streaming = false)`): přehrávání a odpočet začnou
  naráz a nic se neukáže dřív, než odpočet běží.
- **Vypršelé médium je OZNÁMENÍ na střed, ne bublina** (`MessageExpired*` v `serviceText()`,
  test `ServiceTextTest`). Dokud padalo do `describe()`, kreslila se „Fotka vypršela" jako
  obyčejný text v bublině protistrany — četlo se to, jako by to odesílatel napsal. Tak to mají
  i oficiální klienti.
- **Spotřebované médium je oznámením HNED, ne až po TDLib** (`withExpiredNotices`
  v `ConversationItems.kt`, obrazovka podává `SelfDestructTimers.consumedIds`, test
  `spotrebovaneMiziciMediumJeHnedOznameni`). Update obsahu (`MessageExpiredPhoto`) přijde až
  chvíli po zavření prohlížeče nebo po doběhnutí odpočtu a do té doby stála v seznamu bublina
  „vypršelo" (`ExpiredMediaRow`), která pak skočila na oznámení. Převod napodobí, co TDLib
  pošle: bez média a bez popisku. `ExpiredMediaRow` v `Attachments.kt` zůstává jen jako
  pojistka pro bubliny kreslené mimo seznam konverzace.
- **Oznámení má vlastní nabídku jen se smazáním** (`MessageAction.ShowNoticeMenu`,
  `NoticeMenu`, `ServiceMessageItem`). Nabídka nad zprávou se nehodí — kreslí kopii BUBLINY
  a nabízí reakce a odpověď. Práva se zjistí napřed (`messageActions`) a bez práva smazat se
  nabídka neotevře vůbec. **Práva nese položka „Smazat" v akci** (`DeleteNotice`), ne stav:
  `ExpressiveMenu` volá `onDismiss` DŘÍV než položku, takže stav nabídky už je pryč. Nabídka
  je ukotvená k šířce textu (`IntrinsicSize.Max`), jinak vyjela u levého okraje seznamu.
- **Ověřeno na emulátoru sondou** (zpráva `#vyprselo` od testovacího bota převod dočasně
  vydával za vypršelou fotku; necommituje se): oznámení na střed, dlouhý stisk → „Smazat" →
  dialog → smazáno. Převod spotřebované sondou v prohlížeči (`ownSelfDestruct = false`, takže
  se spotřebovala i VLASTNÍ odeslaná fotka s 10s časovačem): první snímek chatu po zavření
  prohlížeče už nesl oznámení, bublina neprobleskla.
- **Příjem ověřený se dvěma účty na dvou telefonech** (12. 10. 2026, Plume → Plume): 10s
  časovač — rozmazaná bublina s odznakem „10s", v prohlížeči odpočet 8 s → 4 s, `SECURE`
  v `dumpsys window`, po doběhnutí se prohlížeč zavřel a obě strany ukázaly oznámení
  („Fotka vypršela" / „The photo expired"). Jednorázové zhlédnutí vydrželo otevřené 7 s
  a vypršelo až po zavření. Notifikace obrázek nepřiložila.
- **V náhledu a notifikaci je „Mizející fotka / video", ne „Fotka"** (`previewTextOrNull(selfDestructs)`,
  push `isSecret`). Dřív se mizející médium v seznamu ani na liště nedalo poznat — oficiální
  klient píše „Self-Destructing Photo". Popisek se do náhledu nedává (patří k mizejícímu
  obsahu) a s ním ani jeho entity (`previewEntities`). `selfDestructType` nese ZPRÁVA, ne obsah,
  proto příznak předává `Message.previewTextOrNull`; citace ho nezná.
- **Jednorázové zhlédnutí se hlásí až při ODCHODU z prohlížeče** (`onCleared`, `appScope`).
  U `MessageSelfDestructTypeImmediately` TDLib po `openMessageContent` obsah vyřídí okamžitě
  (`ttl_on_open` → `on_message_ttl_expired`): smaže soubory a pošle vypršelý obsah, na který
  se prohlížeč zavírá. Nahlášené při otevření by médium zmizelo dřív, než se stáhlo. Co se
  nestáhlo, uživatel neviděl — nespotřebuje se.

### Citlivý obsah

Médium, které server označil jako citlivé (`Message.restrictionInfo.hasSensitiveContent`), je
rozostřené se štítkem „Citlivý obsah" a odkryje se ťuknutím (`SensitiveContent`, testy
`SensitiveContentTest`).

- **Je to spoiler s jiným štítkem.** Převod nastaví `hasSpoiler` a navíc `isSensitive`, takže
  rozostření, odkrývání (`RevealedSpoilers`), prohlížeč i notifikace jdou toutéž cestou;
  `isSensitive` mění jen text štítku. Dvě samostatná skrývání by se rozešla při prvním doladění.
- **Volbu uživatele hlídá aplikace, TDLib ne.** Kdo si citlivý obsah v Telegramu povolil, má
  `ignore_sensitive_content_restrictions = true`, ale `hasSensitiveContent` přichází dál
  (`get_restriction_info_object` v `RestrictionReason.cpp` na volbu nesahá — přečteno ve zdroji
  TDLib). Na volbu se ptá jen zpráva, která příznak má (`TdlClient.hidesSensitive`), ne každá
  zpráva historie.
- **Do notifikace citlivá fotka nejde nikdy**, ani s povolenou volbou: zamykací obrazovku
  vidí i ostatní.
- **Přepínač „Zobrazovat citlivý obsah" je v Obecných → Soukromí** (`PrivacySettings`,
  `SensitiveContentSettings`, testy `SensitiveContentViewModelTest`). Je to nastavení ÚČTU —
  TDLib ho zapíše na server a platí na všech zařízeních, proto to podtitul říká. Nabízí se jen
  s `can_ignore_sensitive_content_restrictions` (jinak TDLib zápis odmítne), přepínač se pohne
  hned a po selhání se vrátí s větou pod sebou. Už převedené zprávy se nepřekreslí: příznak se
  čte při převodu zprávy, projeví se po návratu do chatu.
- **Přepínač se na testovacím účtu NEPŘEPÍNÁ bez souhlasu** — mění skutečný účet všude. Ověřeno
  jen čtení: na emulátoru se ukázal zapnutý, protože ho účet má povolený z jiného klienta.
- **GIF spoiler dřív NEUMĚL VŮBEC** (`MessageAnimation.hasSpoiler` se zahazoval) a hrál sám
  i schovaný. Skrytý GIF se teď nestahuje ani nepřehrává, dokud se neodkryje — pod rozostřením
  by se hýbal a prozradil, co skrývá.
- **Mřížka médií v profilu spoiler taky neznala** a ukazovala ho ostře. Skrytá dlaždice je
  rozostřená s přeškrtnutým okem a **první ťuknutí jen odkryje**, jako v konverzaci. Otevřít
  rovnou nejde: prohlížeč médium, na které se ťuklo, odkrývá sám (z konverzace je to druhé
  ťuknutí), takže by ho z mřížky ukázal bez varování.
- **Skrývá se VŠECHNO, co citlivá zpráva ukazuje jako obrázek**, ne jen fotka, video a GIF:
  samolepka, náhled souboru a obrázek náhledu odkazu. Jde to jednou cestou (`SensitiveBox`
  v `SpoilerOverlay.kt`): rozostření, štítek (u malých náhledů bez něj) a odkrytí prvním
  ťuknutím přes `RevealedSpoilers`, tedy pro celou zprávu naráz. Obrázek náhledu odkazu je
  `MessageMedia.Photo` se `hasSpoiler`/`isSensitive` (`linkPreview(hideSensitive)`),
  samolepka a soubor mají vlastní `isSensitive`.
- **Ťuknutí bere VRSTVA NAD obsahem, ne obal kolem něj.** Samolepka otevírá sadu, obrázek
  náhledu odkaz a potomek dostává dotyk dřív než rodič — skrytá samolepka by se místo odkrytí
  otevřela. Poslední potomek v `Box` je na řadě první, proto průhledná vrstva nad obsahem, jen
  dokud je skrytý. Dlouhý stisk se opakuje kvůli nabídce nad zprávou.
- **Rozostřená samolepka se ořezává do zaoblení** (`coveredShape`): je průhledná a závoj
  rozostření pokryl celý její obdélník — hranatý šedý čtverec. Odkrytá se neořezává.
- **Náhled v citaci i v pruhu připnutých respektuje skrytí taky** (`quoteThumbnail`,
  `PinnedMessagesBar.thumbnailFileId`). Pruh připnutých dřív ukazoval ostrý náhled i u fotky se
  spoilerem — visí nad konverzací pořád, takže to byl nejviditelnější únik.
- **Sonda „všechny zprávy od čísla N" v soukromém chatu s botem NEFUNGUJE podle čísel z Bot
  API.** Bot má v soukromém chatu vlastní číslování zpráv, jiné než náš účet, takže podmínka
  `id > (N shl 20)` chytila i staré zprávy. Spolehlivé je `date` (čas odeslání).
- **Citlivý může být i CELÝ chat** (`User` / `Supergroup.restrictionInfo.hasSensitiveContent`,
  `ChatInfo.sensitive`): konverzace napřed ukáže varování („Citlivý obsah" s tlačítkem „Přesto
  zobrazit") a obsah až po potvrzení, jako oficiální klient. Rozhoduje tatáž volba účtu jako
  u zpráv (`hidesSensitive(restriction)`) — kdo si citlivý obsah povolil, varování nedostane.
- **Potvrzení platí pro jedno otevření chatu** (`rememberSaveable` podle chatu, přežije otočení),
  ne napořád: „napořád" je volba účtu v Obecných → Soukromí, a druhá, místní verze téže volby by
  se s ní rozešla.
- **Varování a omezený chat se řídí JEDNOU podmínkou** (`chatHidden` v `ConversationScreen`):
  obsah, pole, lupa a shrnutí v hlavičce i pruh připnutých. Dokud to byly podmínky zvlášť,
  pruh připnutých u omezeného chatu zapomněl a obsah prozradil. Seznam se pod varováním
  neskládá, takže se nic nehlásí jako přečtené.
- **Testovací účet má citlivý obsah povolený**, takže skutečný citlivý kanál by varování
  neukázal — ověřuje se sondou, která `ChatInfo.sensitive` nastaví chatu s testovacím botem.
- **Ověřeno na emulátoru podstrčeným příznakem** (sonda: popisek `#citlive` od testovacího bota,
  necommituje se): fotka, video i GIF v konverzaci, odkrytí GIFu s přehráním, mřížka v profilu.
  Skutečná citlivá zpráva ověřená není. **Ruční GIF z Pythonu Telegram nepoznal** a poslal ho
  jako soubor; animaci vyrobí `sendAnimation` s němým MP4 (`screenrecord` z emulátoru). Ten ale
  nahrává obrazovku i se jmény kontaktů — testovací zprávy se proto po ověření mažou
  (`deleteMessage` botem).

### Omezený obsah

Zpráva, kterou server v téhle zemi nebo na téhle platformě nedovolí ukázat
(`Message.restrictionInfo.restrictionReason`), má v bublině místo obsahu důvod od serveru
s ikonou zákazu (`restrictionText`, `ChatMessage.restricted`, `RestrictedNote`, testy
`RestrictedContentTest`).

- **Je to jiná věc než citlivý obsah.** TDLib (`get_restriction_info_object`) plní
  `restrictionReason` JEN důvodem, který citlivý není; citlivost hlásí zvlášť
  `hasSensitiveContent` a ten se odkrývá ťuknutím. Omezený obsah odkrýt nejde — ani v prohlížeči,
  ani v náhledu, ani v citaci.
- **Důvod je věta od serveru a vypisuje se.** Pravidlo „text z TDLib se uživateli nevypisuje"
  platí pro chybové hlášky; tohle je obsah, který dokumentace popisuje jako „human-readable
  description", a oficiální klienti ho ukazují taky.
- **Zahazuje se všechno, co by obsah prozradilo nebo nabízelo akci nad ním**: příloha, entity,
  náhled odkazu, anketa, tlačítka bota, kontrola faktů. Odesílatel, čas a reakce zůstávají.
- **Náhled v seznamu chatů a notifikace nesou důvod taky** (`previewTextOrNull`,
  `Message.previewEntities`), jinak by obsah prosákl tam; fotka se do notifikace nepřikládá.
- **Ikona a ztlumená barva jsou nutné**: holý text v bublině by se četl jako zpráva odesílatele.
- **Omezený může být i CELÝ chat** (`ChatInfo.restriction`, `restrictionOf`): kanál nebo účet
  zablokovaný v zemi. `Chat` omezení nenese — je na `User` a `Supergroup`, stejně jako ověření;
  základní skupina ho nemá. Konverzace pak místo zpráv ukáže prázdný stav s ikonou zákazu
  a důvodem, bez vstupního pole, bez hledání a shrnutí v hlavičce.
- **Pruh připnutých zpráv se u omezeného chatu schovává taky** — první verze ho nechala a nad
  prázdným stavem visela připnutá zpráva i s obsahem, tedy přesně to, co omezení skrývá.
  Pruh proti spamu zůstává: zablokovat a nahlásit dává smysl i u omezeného účtu.
- **Prázdný stav stojí PŘED načítáním.** Historie se načte tak jako tak, jen se neukáže;
  točící se indikátor by sliboval, že zprávy přijdou.
- **Ověřeno na emulátoru sondou** (text `#omezeno` od testovacího bota převod dočasně vydával
  za omezený; necommituje se): fotka s popiskem i text s odkazem ukázaly jen důvod. Omezený
  chat sondou v `chatInfo` (chat s testovacím botem): prázdný stav s důvodem, bez pole
  a připnutých. Skutečně
  omezenou zprávu testovací účet nezná, náhled v seznamu a notifikace ověřené nejsou.

### Jak to ověřit

**Přes testovacího bota jde ověřit jen ODESÍLACÍ strana.** Bot API mizející média posílat neumí
— `sendPhoto` nemá `ttl_seconds` a `ephemeral_message_parameters` (Bot API 10.3) je něco jiného:
viditelnost zprávy ve skupině, ne mizení. Kdo čeká, že si mizející fotku pošle botem, hledá
parametr, který neexistuje.

- **Chat s botem je platný cíl a server ho přijme.** `selfDestructAllowed` se ptá na soukromý
  chat, a chat s botem jím je — ověřeno na emulátoru odesláním obou režimů. Testuje se tedy
  v něm, ne ve skupině.
- **Příjmová strana potřebuje druhý uživatelský účet.** Tikající odpočet ze `selfDestructIn`,
  spotřebování po zhlédnutí, výměna za `MessageExpiredPhoto` a vyřazení z listování prohlížeče
  se u vlastní odeslané zprávy z principu neprojeví — u odesílatele se nespotřebovává.
- **Že se vlastní zpráva nespotřebovává, se pozná podle STOJÍCÍHO odpočtu**: v prohlížeči zůstane
  „10s" i po patnácti vteřinách. U jednorázového zhlédnutí to samé přežije otevření i zavření.
- **`FLAG_SECURE` se ověřuje dvakrát, ne snímkem.** Černý `screencap` sám o sobě nic nedokazuje —
  stejně vyjde i obrazovka, která se nestihla vykreslit. Druhá půlka důkazu je
  `adb shell dumpsys window windows | grep -A3 plume`, kde musí být ve `fl=` příznak `SECURE`.
- **Se zapnutým `FLAG_SECURE` se prohlížeč nedá ověřovat snímky** a musí se číst strom
  zpřístupnění (`adb shell uiautomator dump`). Ten čte hierarchii, ne pixely, takže ho ochrana
  proti snímkům neomezuje.

## Soubory a média

TDLib **nedává URL**. Soubory drží ve vlastním adresáři a identifikuje je celočíselným `fileId`;
cesta je známá až po stažení a průběh chodí updaty. Překlad do jednoho reaktivního stavu je
[FileRepository] / `TdlFileRepository`.

- **Odběr `file(fileId)` sám spustí stahování.** UI nic neinicializuje — `RemoteImage` jen vykreslí,
  co dostane. Díky tomu nemůže vzniknout obrázek, který se nikdy nezačne stahovat.
- **Jeden stav na `fileId`.** Když tentýž obrázek visí v seznamu i v konverzaci, sdílí jedno
  stahování.
- **`downloadFile(synchronous = false)`** — s `true` by volání čekalo na dokončení a UI by mezitím
  nemělo co ukázat.
- **`local.path` je vyplněná i u rozestavěného souboru**, takže hotovost se pozná jen podle
  `isDownloadingCompleted`.
- **Coil potřebuje schéma** — `model = "file://$path"`; holá cesta se vyhodnotí jako relativní URI.
- **Progresivní načtení:** TDLib drží fotku v několika velikostech. Do bubliny jde největší varianta
  do 1280 px, nejmenší slouží jako okamžitý náhled (`thumbnailFileId`).
- **`RemoteImage` si pamatuje poslední známou cestu** (`knownPaths`). Odběr souboru startuje vždycky
  na `Pending` a odpovídá až z korutiny, takže každý nově složený `RemoteImage` proletí aspoň jedním
  snímkem bez obrázku. U sdíleného prvku ze seznamu do konverzace to bylo vidět: avatar odletěl jako
  prázdný kruh a obrázek naskočil až na místě. Mapa se čte i zapisuje jen z kompozice, takže
  nepotřebuje zámek.
- **Stahování obrázku ukazuje lesk přes náhled, ne indikátor** (`loadingShimmer` v `RemoteImage`):
  šikmý světlý pruh přejíždí přes rozmazaný náhled (nad fotkou bílý, na holé podložce v barvě
  popředí), jen dokud se stahuje — stojící rozmazání tedy znamená, že se nic neděje. Dřív to
  byl `ContainedLoadingIndicator` uprostřed: nad rozmazanou fotkou barevný disk, který k obrázku
  nepatřil a vypadal jako nálepka. Na průhledné podložce bez náhledu (samolepka) se nekreslí
  nic, pruh by přejížděl přes prázdno; pod 24 dp (malé avatary) taky ne.
- **Lesk běží i přes ZMENŠENINU, dokud se stahuje ostrá verze** (`sharpening` v `RemoteImage`).
  V prohlížeči médií stojí za ostrou fotkou zmenšenina z bubliny (`thumbnailFileId`) a dřív
  vypadala jako hotový obrázek — u velké fotky na pomalé síti nebylo poznat, že se něco děje.
  Náhled (`thumbnailFileId`) předávají jen fotky, takže pruh je vždycky ten nad fotkou.
  **Ověřuje se letovým režimem**: zmenšenina v konverzaci se načte, pak `adb shell cmd
  connectivity airplane-mode enable` a otevřít prohlížeč — ostrá verze zůstane ve stahování
  a lesk běží; po vypnutí letového režimu se dotáhne a obraz stojí. Zpomalení sítě na
  emulátoru nefunguje (viz „Video").
- **Lesk se ověřuje sondou a zpomalenými animacemi**: v `RemoteImage` dočasně `if (true)` místo
  `current == null` a `shimmer` bez podmínky na `Downloading`, pak `animator_duration_scale 5`
  a série `screencap`. V normální rychlosti snímek trefí pauzu mezi průjezdy a pruh „chybí".
  Při krytí 0,28 byl na snímku sotva znát — slabý lesk na telefonu splyne s rozmazáním.
- **Než dorazí jakýkoli soubor, kreslí se rozmazaný náhled ze zprávy** (`Minithumbnail`,
  parametr `placeholder` u `RemoteImage`). TDLib ho posílá přímo v datech fotky, videa, GIFu
  a dokumentu (JPEG kolem 40 px), takže je vidět v prvním snímku, bez stahování.
- **`Minithumbnail` není data class.** `ByteArray` se v data class porovnává podle reference;
  každé nové namapování téže zprávy by vypadalo jako změna a seznam by přestal přeskakovat
  nezměněné položky. `equals` proto porovnává obsah.
- **Rozmazává `Modifier.blur`, ne Haze.** Obojí umí vlastní obsah na Androidu 12+ i na iOS, ale
  na starším Androidu Haze kreslí místo rozostření tónovanou plochu, kdežto `blur` se jen vypne
  a zbude bilineárně zvětšený 40px náhled, který rozmazaně vypadá sám.
- **Poslední `updateFile` stahovaného souboru může lhát.** TDLib po stažení pozná, že tentýž
  soubor má už pod jinými id (bot poslal skladbu, která v chatu byla), a soubory SLOUČÍ: „hotovo"
  pošle jen pro ta starší id a stahované id dostane jako poslední update „stahuje, 73531 ze
  73531" — a dál nic. `getFile` o chvíli později hlásí hotovo. Přehrávač skladby na to čekal
  navždy (`loading`, MediaSession `BUFFERING`). Proto `confirmFinished`: soubor stažený celý,
  a přesto „stahující" (`looksFinished`, testy `LooksFinishedTest`), se ověří dotazem po 250 ms,
  1, 3 a 10 s. Ověřeno na emulátoru: `sendAudio` botem se souborem, který v chatu už byl,
  a ťuknutí do pár vteřin; v logu `Files` pak „Soubor N po stažení: hotovo=true" a skladba hraje.
  **Obecně: na stav souboru z updatů se nespoléhat, když na něm něco čeká bez časového limitu.**
- **`FileRepository` je eager** (`createdAtStart`) ze stejného důvodu jako seznam chatů — v
  konstruktoru se přihlašuje k `fileUpdates`.

### Automatické stahování

Nastavení → Úložiště → Automatické stahování: pro Wi-Fi, mobilní data a roaming jedna úroveň
(`AutoDownloadLevel`: Vypnuto, Úsporně, Běžně, Naplno; `AppSettings.autoDownload`). Úroveň
říká, jestli se samy stahují fotky ve zprávách a do jaké velikosti GIFy, kolečka a obrázky
poslané jako soubor (`AutoDownloadLimits`, testy `AutoDownloadTest`). „Běžně" je to, co aplikace
dělala na všech sítích dřív (5 MB); výchozí je Wi-Fi Naplno, mobilní data Běžně, roaming Úsporně.

- **Úrovně, ne přepínače po druzích.** Plume samo stahuje jen tři věci, takže šest voleb na síť
  by nastavovalo rozdíly, které uživatel nepozná. Video samo se nepřednačítá vůbec (proud až
  v prohlížeči), hlasovky se stahují až přehráním — proto v nabídce nejsou.
- **Co platí teď, počítá `AutoDownloadPolicy`** (nastavení + `NetworkMonitor.status` +
  `saveData`) a dává to UI přes `LocalAutoDownload` z kořene přihlášené části. Bubliny
  nesahají do Koinu ani nesledují síť samy.
- **Spořič dat srazí mobilní síť nejvýš na Úsporně** (Android `restrictBackgroundStatus`,
  iOS `NWPath.isConstrained`). Spořič se síťovým callbackem neohlásí, chodí jen vysílání
  `ACTION_RESTRICT_BACKGROUND_CHANGED`, a proto je to vlastní proud vedle `status` — v něm by
  změna znamenala nové hlášení sítě TDLib a znovuotevření všech spojení.
- **Co se samo stahovat nemá, se jen SLEDUJE** (`FileRepository.observe`, `rememberFileState(…,
  download = false)`): už stažená fotka i GIF se ukážou, stahuje-li soubor někdo jiný
  (prohlížeč), běží lesk. Dřív se stav souboru nedal zjistit bez spuštění stahování —
  `file()` ho spouští odběrem.
- **Fotka bez stažení = rozmazaný náhled ze zprávy a tlačítko stažení** (`RemoteImage(autoDownload
  = false)`). Ťuknutí stáhne fotku i zmenšeninu a otevře se až dalším ťuknutím; tlačítko je tmavý
  kruh jako „přehrát" u videa, protože leží přes fotku. Zmenšeniny videí a souborů se stahují
  dál — jsou malé a bez nich by nebylo poznat, co ve zprávě je.
- **Roaming nastavení nabízí jen tam, kde ho platforma pozná** (`detectsRoaming`): iOS ho
  nehlásí a volba, která se nikdy neuplatní, by lhala.
- **Emulátor jede pro aplikaci přes Wi-Fi, i když `dumpsys connectivity` hlásí jako výchozí
  síť mobilní data** (výchozí síť aplikace měla transport Wi-Fi). Která úroveň platí, říká log
  `AutoDownload` („Automatické stahování: High (síť WiFi…)"), píše se jen při změně úrovně.
- **Ověřeno na emulátoru (česky):** nastavení všech tří sítí, Wi-Fi na Vypnuto → fotka v kanálu
  rozmazaná s tlačítkem a v logu `Files` ani jedna žádost o soubor, ťuknutí stáhne fotku
  i zmenšeninu. **Neověřeno:** spořič dat a režim nízkých dat (logika jen testy), skutečný
  roaming a iOS (jen přeložené).


### Automatické ukládání do galerie

Úložiště → Ukládání do galerie: výchozí pravidla pro soukromé chaty, skupiny a kanály (fotky,
videa, největší video) a výjimky pro jednotlivé chaty (`AutosaveGroup`, `AutosaveViewModel`).
Ukládá `AutosaveWorker` (`core/files`) přes `MediaSaver` — tentýž, který ukládá ručně
z prohlížeče.

- **Nastavení drží Telegram na serveru** (`getAutosaveSettings` / `setAutosaveSettings`,
  `updateAutosaveSettings`) — je společné s oficiálními aplikacemi na všech zařízeních účtu.
  **Ukládat ale musí každá aplikace sama**, TDLib jen řekne co. Plume dřív nastavení
  ignorovalo: kdo si ukládání zapnul v oficiální aplikaci, v Plume ho neměl.
- **Ukládají se fotky a videa z NOVĚ příchozích zpráv** (`updateNewMessage`, i ty dohnané po
  startu). Nikdy: vlastní a naplánované zprávy, chráněný obsah (`canBeSaved`), mizející média,
  média „jen na ťuknutí" (`isSecret`) a tajné chaty — uložit je do galerie by obcházelo to, co
  odesílatel nebo chat výslovně nechce.
- **Ukládání nestahuje víc, než by stáhlo automatické stahování** (`autosaveAction`): fotka se
  stáhne, když se fotky stahují samy, video, když se vejde do limitu souborů dané sítě
  (Naplno 20 MB). Jinak se jen čeká (`FileRepository.observe`), až soubor stáhne otevřený chat
  nebo prohlížeč — nejvýš 6 hodin a jen v běžícím procesu. Kdo na mobilních datech stahování
  vypnul, nečeká, že mu je sní galerie. Najednou se kvůli ukládání stahují nejvýš dva soubory.
- **Repository vzniká eagerně** — nové zprávy chodí updatem a ten bez odběratele zmizí.
  Nastavení se čte až s první zprávou (a s obrazovkou) a drží se v paměti; update ho zneplatní.
- **Server u vypnutých videí vrací nejmenší limit (512 kB).** Zapnout videa s ním by ukládalo jen
  půlmegová videa, proto se při zapnutí předvybere 100 MB a 512 kB se v nabídce ukáže, jen když
  videa zapnutá byla (ověřeno na emulátoru, účet měl všude „Vypnuto").
- **Změna jde na server až „Hotovo"** v dialogu, ne po každém přepínači — tři volání za sebou
  by se předbíhala.
- **Zapnutí si řekne o oprávnění zápisu** (`rememberSavePermission`): iOS (přidávání do knihovny
  fotek) a Android do verze 9. Nastavení se změní i při odmítnutí — patří i jiným zařízením;
  ukládání pak v logu hlásí „NEULOŽENO".
- **Desktop automatické ukládání nemá** (`autosavesToGallery = false`): galerii nemá a ruční
  „Do galerie" tam ukládá do Stažených, které by automatika zahltila.
- **Výjimky se zatím jen upravují a odebírají**, zakládá se v oficiálních aplikacích (Plume
  nemá v profilu chatu kam je dát) — viz `docs/roadmap.md`.
- **Ověřeno**: `AutosaveTest` (výjimka přebije druh, kdy stahovat fotku a video), `AutosaveWorkerTest`
  (stáhne a uloží, skupina se neukládá, bez automatického stahování se čeká na cizí stažení,
  nestažené se neuloží); na emulátoru skutečné nastavení účtu a dialog (zavřený přes Zrušit),
  a **se sondou** (v paměti podstrčené „fotky ze soukromých chatů", na server nic) uložení
  existující fotky do `Pictures/Plume` i skutečné nově příchozí fotky ze soukromého chatu.
  **Změna nastavení na serveru a iOS vyzkoušené NEJSOU** (iOS jen přeložené) — nastavení platí
  pro celý účet včetně oficiálních aplikací.