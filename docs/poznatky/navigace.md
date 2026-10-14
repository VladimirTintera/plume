# Navigace

Poznatky k jedné oblasti aplikace. Obecná pravidla projektu a rejstřík ostatních souborů jsou
v [CLAUDE.md](../../CLAUDE.md); poučení z opravy v téhle oblasti patří sem.

## Navigace

Navigation3 (`rememberNavBackStack` + `NavDisplay` + `entryProvider`). Klíče jsou v
`core/navigation/Routes.kt` a jsou `@Serializable` — bez toho by se zásobník nepřežil zabití procesu.

Seznam chatů a konverzace tvoří **list-detail** dvojici přes `rememberListDetailSceneStrategy`:
`ChatsRoute` nese metadata `listPane(detailPlaceholder = …)`, `ConversationRoute` má `detailPane()`.
`directive` se počítá ze šířky **content-oblasti** (`BoxWithConstraints`), ne z okna — scaffold
má rozhodovat podle místa, které opravdu dostane.

**Boční insety NEVĚDÍ, kde prvek leží.** Compose dá `safeDrawing`/`displayCutout` každému, kdo si
o ně řekne, i když stojí daleko od kamery. Telefon naležato proto měl v konverzaci (panel VPRAVO)
mezeru o šířku výřezu vlevo — v hlavičce a seznamu zpráv, ne ve vstupním poli, které si boky
nebralo vůbec. Pravidlo: kdo inset vyřešil, ten ho **zkonzumuje** (`consumeWindowInsets`).
Seznam v dvoupanelu konzumuje pravý inset (`listPaneInsets`) — ten patří panelu vpravo. Vstupní pole bere `safeDrawing` bokem i spodkem, jinak by odeslání zajelo pod
výřez nebo boční třítlačítkovou lištu. Ověřuje se v OBOU rotacích naležato
(`adb shell cmd window user-rotation lock 1` / `3`, pak `free`) — výřez je pokaždé na jiné straně.

**`LazyColumn` ořezává jen ve směru scrollu.** Vodorovně kreslí i mimo své hranice, takže sloupec
časů schovaný „za pravým okrajem" (odkrývání tahem) byl schovaný jen dokud seznam končil
u okraje obrazovky. S insetem vpravo prosvítal; proto `clipToBounds` na ploše seznamu.

**Sekční navigace není — ani spodní lišta, ani rail.** Sekce jsou dvě a navigační lišta i rail
mají podle M3 smysl až od tří. Lišta zabírala ~80 dp jen kvůli skoku do Nastavení a seznam chatů
pod ní působil přeplácaně; rail na širší ploše přežil o něco déle, ale jen zdvojoval menu účtu
a ukusoval šířku panelům. Do Nastavení vede menu účtu v pilulce na každé šířce, spodek obrazovky
patří FAB menu „Vytvořit" (viz „Složky místo filtrů a FAB"). Důsledek pro insety:
shell žádný nekonzumuje, každá obrazovka si je bere sama (Scaffold je má ve výchozích
`contentWindowInsets`). **Kdyby sekcí přibylo na tři**, přijde rail zpátky — past s místem:
skrytí musí animovat i SLOT (`shrinkHorizontally`), ne jen posun, jinak obsah doskočí až na konci.

**Avatar a název letí ze seznamu do konverzace.** Klíče `chatAvatarKey` / `chatTitleKey`
obsahují id chatu, jinak by se při rychlém přepnutí spároval avatar jednoho chatu s druhým.
Na rozdíl od letu odeslané zprávy se tu viditelnost neřídí ručně — obě strany jsou
v `AnimatedContent` NavDisplaye, takže stačí `LocalNavAnimatedContentScope`.

- **Tečka „online" letí VLASTNÍM sdíleným prvkem** (`chatOnlineDotKey`, `OnlineDot(chatId)`).
  Sdílený byl jen avatar, tečka vedle něj zůstala v obsahu obrazovky: avatar odletěl do hlavičky
  bez ní a tečka se k němu přilepila, až dosedl — problikla. Obalit sdíleným prvkem avatar
  i s tečkou nejde, stejný klíč `chatAvatarKey` nese i profil a tvář odesílatele, které tečku
  nemají, a obal by se do nich zmenšoval s avatarem pevné velikosti uvnitř. Obě strany mají
  tutéž pružinu, takže tečka po celou dráhu drží roh avataru.
- **Tečka musí mít v překryvu vyšší `zIndexInOverlay`** (`sharedNavElement(zIndexInOverlay)`).
  Sdílené prvky se během letu kreslí v překryvu mimo pořadí rodičů; se stejným indexem by ji
  letící avatar mohl přikrýt.
- **Ověřeno na emulátoru sondou** (Notifikátor podstrčený jako online v seznamu i hlavičce,
  necommituje se) a sérií surových snímků se zpomalenými animacemi, tam i zpět. Online
  kontakt se neotevírá — přečetly by se mu zprávy.

**Na široké ploše jsou dva panely vždycky** (kromě prázdného seznamu, viz „Archiv"), i bez otevřeného chatu — vpravo je pak
`ConversationPlaceholder`. Dřív dostal seznam bez konverzace celou šířku (`singlePane`), takže
placeholder, i když byl v metadatech `listPane`, se nikdy nekreslil, řádky chatů se natáhly přes
celý rozložený Fold a hlavička s pevnou maximální šířkou v tom visela.

- **Zpět v Chatech musí být `PopUntilContentChange`.** Výchozí `PopUntilScaffoldValueChange`
  přeskakuje položky, které nemění rozložení panelů; „seznam + placeholder" a „seznam + chat"
  jsou oba dvoupanel, takže zpět z konverzace odešel rovnou z aplikace. V Nastavení zůstává
  výchozí — sekce je v dvoupanelu otevřená vždycky (`ensureSettingsSection`), a kdyby zpět
  odebral jen ji, efekt by ji hned vrátil a z Nastavení by nešlo odejít.
- **Placeholder má úvodní animaci** (`IntroShape` nad `ShapeMorphs.Intro`): tvar naroste, protočí
  se a projde kruh → čtyřlístek → sedmiúhelník → zaoblená hvězda, pak naskočí ikona a text. Běží
  při každém zobrazení, i po zavření chatu, a pak stojí. Průběh tvaru jde po úsecích jednou dráhou
  (`tween` s emphasized decelerate), otočení a zvětšení jsou pružiny — hvězda dokmitne, i když
  tvar už stojí. Tvar se čte až v `drawBehind`, animace nic nerekomponuje. **Tentýž tvar má
  i přihlášení** — viz „Přihlašovací obrazovka".
- **Hvězda má cípy zaoblené víc než žlábky** (`rounding` 0,24, `innerRounding` 0,1). Se stejným
  zaoblením obou vyšla kytička.
- **Cípů je SEDM, ne pět.** Pěticípá hvězda má jeden cíp nahoře a dva dole, takže její optický
  střed nesedí se středem obrysu, do kterého se vepisuje ikona — tvar působil nevystředěně
  (`MorphShape` centruje podle obalového obdélníku, a ten u nesymetrické hvězdy leží jinde než
  těžiště). S vyšším lichým počtem je rozdíl neznatelný a hvězda zůstane hvězdou. Patří k tomu
  mělčí žlábky (vnitřní poloměr 0,68 místo 0,58); se sedmi hlubokými cípy je z toho ježek.
- **Mnohoúhelník před hvězdou má stejný počet vrcholů jako ona**, jinak se při morfu přeskupují.
  Proto se nebere `MaterialShapes.Pentagon` — ten sedl jen k pěti cípům.
- **Tvar se dá proanimovat znovu** (`IntroShape(replayKey = …)`): opakování je kratší než úvod,
  otáčí se opačně a NEodhaluje obsah — ikona uvnitř se mění sama a dvojí naskakování by se pralo.

**Scaffold list-detail si sám bere fokus — vypnuto** (`shouldAutoFocusCurrentDestination = false`
v `PaneDirective.kt`). `ThreePaneScaffold` při každé změně aktuálního panelu zavolá na panel
`requestFocus()`; fokus propadl na první fokusovatelný prvek konverzace, tedy vstupní pole,
a na rozloženém Foldu vyjela klávesnice při každém otevření chatu i po návratu do něj.
Na compactu (a na zaklapnutém Foldu) se to nedělo, protože jeden panel scaffold nekreslí —
proto to dlouho vypadalo jako chyba konverzace. Výchozí hodnota je `true` a nastavuje ji
i konstruktor `PaneScaffoldDirective` BEZ toho parametru, takže se musí předat výslovně.

**V dvoupanelu se nesdílí nic — kromě fotky do prohlížeče** (`LocalTwoPane`, poskytuje ho
shell). Seznam vedle konverzace zůstává vidět, takže by avatar odletěl z položky, která je pořád
na obrazovce, a o jeden klíč by se přetahovaly dva prvky naráz. Prohlížeč médií ale nahradí celý
dvoupanel, takže fotka z bubliny nemá s kým se přetahovat — `sharedNavElement(inTwoPane = true)`
proto dostávají všechny klíče `photoKey` a nic jiného. **Kdo přidá `inTwoPane` jinam, musí vědět,
že druhá strana letu dvoupanel nahrazuje**, jinak se vrátí přesně ten souboj o klíč.

**Text se sdílí přes `sharedBounds` + `scaleToBounds`, ne přes `sharedElement`.** Název chatu má
v hlavičce jinou velikost písma; se `sharedElement` by se text na konci letu přeskládal skokem.

**Chat se ze seznamu otevírá container transformem** (`ChatContainer`, `rememberChatContainerSource`
v `core/ui/ChatContainerTransform.kt`): řádek se rozroste v obrazovku chatu a zpět (tlačítkem
i gestem) se do něj smrští. Dřív letěl jen avatar a název přes prolnutí obrazovek a malá ikona
s holým textem působily vytrženě; teď letí uvnitř plochy, která roste s nimi.

- **Přechod scén se pro tuhle dvojici vypíná** (`EnterTransition.None togetherWith
  ExitTransition.None`), pozná ji `NavTransitions` podle metadat položek (`ChatListMetadata`
  na seznamu a archivu, `ChatScreenMetadata` na konverzaci a seznamech témat). Seznam stojí
  pod rostoucí plochou; `ExitTransition.None` ho drží složený, protože let sdílené plochy je
  animace TÉHOŽ přechodu. `KeepUntilTransitionsFinished` je v Compose interní.
- **Obrazovka chatu se škáluje (`ScaleToBounds`), řádek přeměřuje (`RemeasureToBounds`).**
  Konverzaci přeměřovat v každém snímku by bylo drahé; řádek naopak musí plochou vyplnit celý
  kontejner, jinak by pod prolínající se konverzací prosvítal seznam. Obsah řádku drží svou
  velikost přes `skipToLookaheadSize`.
- **Plocha řádku se neprolíná, leží POD konverzací** (`zIndexInOverlay` −2 a −1, avatar
  a název 0): kdyby mizela současně s objevováním konverzace, byl by kontejner v půlce
  průhledný. **Obsah řádku ale zmizí hned na začátku** (60 ms, konverzace se začne objevovat
  až po něm) — dokud zůstával, prosvítal náhled zprávy a čas přes objevující se hlavičku
  konverzace (zjištěno sérií snímků). Mizí jen při spárovaném letu a délky drží v mezích
  přechodů scén: animace řádku patří k TÉMUŽ přechodu, takže delší by natáhla i návrat
  gestem z Nastavení.
- **Kontejner jen nad seznamem** (`liesOnChatList`, pamatuje se `remember(route)`, protože při
  návratu zmizí route ze zásobníku dřív, než přechod doběhne). Téma nad seznamem témat má tentýž
  chat a obě obrazovky by se spárovaly navzájem.
- **Vybraný řádek se na telefonu nezvýrazňuje** (`selectedChatId` jen ve dvoupanelu): otevíraný
  řádek by se uprostřed růstu přebarvil na `secondaryContainer`.
- **Bez páru (řádek odscrollovaný, chat se mezitím přesunul, chat v seznamu vůbec není) se
  konverzace jen prolne** přes seznam. Průhlednost si proto řídí `ChatContainer` sám a podle
  `isMatchFound` volí zpožděné mizení letu, nebo rychlý fade through. Spoléhat na
  `enter`/`exit` sdílené plochy nešlo: bez páru se při návratu nepoužilo, konverzace stála
  a pak zmizela skokem. Ověřeno na veřejném kanálu otevřeném z globálního hledání.
- **Z hledání v seznamu chatů to funguje taky**: hledání se zavře a konverzace vyroste z řádku
  v seznamu pod ním, ne z výsledku hledání.
- **Ověřeno na emulátoru (Pixel 9)** zpomalenými animacemi a sérií surových snímků: otevření,
  zpět tlačítkem, gesto zpět zastavené v půlce (tah smršťování posouvá) i zrušené (konverzace
  se znovu rozroste), otevření z hledání a chat bez řádku. Plynulost na telefonu ověřená není.

**Predictive back dělá `NavDisplay` sám** (přes `androidx.navigationevent`): při tažení zpět se
konverzace zmenší a odsune a pod ní se odkryje živý seznam. Nic se pro to nezapíná —
`android:enableOnBackInvokedCallback` systém při targetSdk 35+ ignoruje a je v manifestu jen
jako výslovné prohlášení.

**Přechody mezi obrazovkami jsou vlastní, ne výchozí z Nav3** (`NavTransitions.kt`, předané
do `NavDisplay`). Výchozí přechod na Androidu je 700ms prolnutí obou obrazovek NARÁZ: obě jsou
neprůhledné, takže v půlce jsou obě napůl průhledné přes sebe (nadpis „Nastavení" přes pilulku
hledání) a vypadá to jako chyba vykreslení. Výchozí zpět gestem zmenšuje obrazovku na 70 %.

- **Tlačítkem je to sdílená osa X (fade through):** odcházející zmizí za 90 ms, přicházející se
  objeví až potom, obě se posunou o 30 dp ve směru navigace. Dvojí expozice nenastane nikdy.
- **Gestem je to fade through podle specifikace Googlu** (návrhové zásady Androidu, „Predictive
  back" → „Full screen surfaces"): odcházející 100 → 90 % a zmizí do 35 % tahu, přicházející
  110 → 100 % a objevuje se od 35 %, křivka (0,1; 0,1; 0; 1). **Specifikace platí pro návrat
  UVNITŘ aplikace**; systémový návrat mezi aktivitami (pod zmenšenou obrazovkou se odkrývá ta
  předchozí) je jiný vzor a Google k němu čísla nezveřejňuje. Tím jsme začali a po švihnutí
  obrazovka stála a pak pomalu vybledla — s fade through je odchod hotový v první třetině tahu.
- **Průběh gesta je lineární a Google říká, že se tak přímo použít nemá.** Křivka proto jde na
  zmenšení; s lineárním zmenšením nebylo v půlce tahu vidět nic, protože průběh k jedničce
  skoro nedojde.
- **Délka přechodu gestem neřídí tah, jen dojezd po puštění prstu.** Tah jde po zlomcích
  (`seekTo`), zbytek `NavDisplay` dojede sám v čase přechodu a rychlost prstu nebere v úvahu
  (Nav3 1.1.1 i 1.2.0-alpha04, nastavit to nejde). Rychlé švihnutí se pouští u malého zlomku,
  takže se přehraje skoro celý přechod — proto krátkých 240 ms.
- **Při dojezdu se volá jen přechod pro gesto, ne pro tlačítko** (ověřeno dočasným logem ve
  specifikacích). Hádat ze snímků, KTERÁ vrstva se prolíná, nejde: napůl průhledná horní vrstva
  se stejně barevným pozadím vypadá jako „šednoucí" spodní.
- **Zaoblené rohy nese dekorátor scény, ne přechod** (`PredictiveBackCorners`,
  `sceneDecoratorStrategies`). `ContentTransform` tvar neumí; dekorátor obalí obsah scény
  a poloměr animuje nad TÝMŽ přechodem (`transition.animateDp` podle `EnterExitState`), takže
  ho gesto posouvá se zmenšením. Obal musí nést v klíči i třídu obalené scény: `NavDisplay`
  rozlišuje scény podle třídy a klíče a třída obalu je u všech stejná. **U fade through jsou
  rohy jen jemný detail** — zaoblená obrazovka je vidět do 35 % tahu, pod ní plocha stejné barvy.
- **Seznam chatů se při návratu skládá celý znovu, data ale ne.** `NavDisplay` kreslí jen horní
  scénu, takže kompozice seznamu pod Nastavením neexistuje; `ChatsViewModel` s načteným
  seznamem i pozice scrollu (`SaveableStateHolder`) přitom zůstávají. Kompozici ve ViewModelu
  držet nejde — kdyby vadila, cesta je nechat scénu pod Nastavením složenou (Nastavení jako
  překryvná scéna), ne cachovat data.
- **Plynulost se na emulátoru neměří.** Změřeno `gfxinfo framestats` v RELEASE buildu: návrat
  z Nastavení měl snímky kolem 60 ms, jenže obyčejný scroll seznamu chatů 200–500 ms a GPU hlásilo
  95. percentil 4950 ms. Pomalost skládání se od pomalosti emulátoru oddělit nedá; posuzuje se
  na telefonu.
- **Ověřuje se zpomalenými animacemi a sérií surových snímků** (`animator_duration_scale 20`,
  `screencap /sdcard/N.raw` ve smyčce v jednom `adb shell`): PNG snímek trvá přes 400 ms a přechod
  mine. Gesto zastavené v půlce jde přes `input motionevent DOWN/MOVE`, snímek, pak `UP`.
  Emulátor gesto seekuje stejně jako telefon, plynulost se ale posuzuje na zařízení.

**Past: obyčejný `BackHandler` predictive back zabije.** Kdo odchytí zpět bez
`PredictiveBackHandler`/`NavigationBackHandler`, dostane jen hotové gesto bez průběhu, takže
animace zmizí a nikde se neohlásí chybou. V aplikaci teď žádný `BackHandler` není a nemá
přibýt — ven z obrazovky se chodí přes `onBack` NavDisplaye. Kde je potřeba zpět zachytit uvnitř
obrazovky (nabídka nad zprávou), je to `NavigationBackHandler` z `androidx.navigationevent`.
**A ten musí mít `isBackEnabled` jen po dobu, kdy je co zachytit** (otevřená nabídka, výběr,
hledání). Zapnutý natrvalo s jediným `onBackCompleted` predictive back obrazovky zabije úplně
stejně jako `BackHandler`: gesto dostane on, ne `NavDisplay`. Tak to měl prohlížeč médií. Natrvalo
zapnutý ho smí mít jen obrazovka, kde zpět opravdu NENÍ odchod: mini aplikace (`WebAppScreen`),
kde zpět řídí stránka.

### Šířkové třídy, tři panely a kotvy

Stav k říjnu 2026; plán, co s tím, je v `docs/roadmap.md`, „Velká plocha".

- **Šířkové třídy M3 se nepoužívají.** Jediný práh je vlastní `TWO_PANE_MIN_WIDTH = 720.dp`
  (`core/adaptive/PaneDirective.kt`): pod ním jeden panel, nad ním vždycky dva, a výsledek
  `calculatePaneScaffoldDirective` se přebije `singlePane()` / `twoPane()`. Na jiné šířce
  nezávisí nic dalšího.
- **Velikostní třída se počítá z `BREAKPOINTS_V1`**, které znají jen Compact (< 600 dp),
  Medium (600–839) a Expanded (≥ 840). Large (≥ 1200) a Extra-large (≥ 1600) jsou až
  v `BREAKPOINTS_V2`; s V1 je pro knihovnu i 2000 dp široké okno jen Expanded.
- **Co dělá `calculatePaneScaffoldDirective` (adaptive 1.3.0-beta02, ověřeno v bajtkódu
  přes `javap -c`):** Compact a Medium 1 panel, Expanded 2 panely s mezerou 24 dp, od Large
  3 panely (na XL s širší výchozí šířkou panelu, `DefaultPreferredWidthXL`). Na Medium dá dva
  panely varianta `calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth`.
- **Co umí `adaptive-navigation3`, a projekt nepoužívá:** metadata
  `ListDetailSceneStrategy.extraPane()` (třetí panel), `preferredPaneSize()` (šířka panelu),
  `paneAnimation()`; `rememberListDetailSceneStrategy` bere `paneExpansionState`
  a `paneExpansionDragHandle` — táhlo mezi panely s kotvami `PaneExpansionAnchor.Offset`
  / `Proportion`. Je tam i `SupportingPaneSceneStrategy`.
- **Profil a obrazovky z něj jsou `detailPane()`**, takže na široké ploše nahradí konverzaci,
  místo aby se otevřely vedle ní.
- **Práh 720 dp má rozumný základ**, i když není standardní: seznam 360 dp (výchozí šířka
  panelu) + nejužší použitelná konverzace 360 dp, a vnitřní displej Foldu (Medium) tak dostane
  dva panely. Standardní dvoupanel na Medium (od 600 dp) by konverzaci nechal 240 dp.

### Zásobník pro každou sekci

Každá sekce aplikace (Chaty, Nastavení) má **vlastní zásobník** a všechny drží jeden stav,
`AppNavigation`, vytvořený nahoře v `AppShell` a podávaný dolů. Přepnutí sekce nic nemaže, jen
vybere jiný zásobník — návrat do Chatů vrátí otevřenou konverzaci, do Nastavení otevřenou sekci.
Dřív byl zásobník jeden pro celou aplikaci a přepnutí ho srovnalo na kořen.

- **`NavDisplay` je jeden a kreslí zásobník Chatů a nad ním zásobník vybrané sekce**
  („concatenated back stack" z Nav3). Zpět z kořene Nastavení je tím obyčejný pop do Chatů
  i s predictive back, bez vlastního handleru. Ťuknutí na už vybranou sekci ji vrátí na kořen.
- **Položky se dekorují pro KAŽDOU sekci, i tu, která není vidět** (`rememberDecoratedNavEntries`
  pro oba zásobníky pokaždé). Zásobník, který by se zrovna nedekoroval, by pro Nav3 vypadal jako
  odebraný a jeho ViewModely by se zrušily.
- **Dekorátor ViewModelů je nutný** (`rememberViewModelStoreNavEntryDecorator`, v `AppShell` nad
  `NavDisplay`). Bez něj `NavDisplay` dává jen `SaveableStateHolder` a `koinViewModel()` v obrazovce
  bral úložiště Activity — **ViewModel každé otevřené konverzace žil až do konce Activity
  a `onCleared` se při odchodu z chatu nevolal**, přestože na něm visí dopsání konceptu,
  zastavení hlasovky a zahození nahrávky. Teď se ViewModel zruší s odebráním položky ze zásobníku,
  při přepnutí sekce ne (ověřeno logem v `onCleared`). Rodičem je owner Activity: Nav3 pod ním
  drží úložiště pro každou položku, rotaci přežije a s odchodem shellu (odhlášení) je vyčistí.
  Vlastní `ViewModelStoreOwner` jako v mlogu proto není potřeba.
- **Past: `NavDisplay` volá `onBack` jednou ZA KAŽDOU odebíranou položku**
  (`repeat(entries.size - scene.previousEntries.size) { onBack() }`). U dvoupanelové scény
  (seznam + detail) tedy dvakrát. Každé volání musí odpovídat právě jedné položce toho, co se
  kreslí — cokoli jen dopočítaného pro kreslení tu počty rozhodí.
- **Obrazovka píše do zásobníku SVÉ sekce** (`chatsStack`, `settingsStack` v `shellEntryProvider`),
  ne do toho vybraného.
- **Šipka zpět obrazovky odebírá SVOU položku, ne poslední** (`pop(route)` v `AppNavigation.kt`),
  a kořen nikdy. Dřív to byl `removeLastOrNull()` a aplikace padala na „NavDisplay entries cannot
  be empty": obrazovka po odebrání ještě dobíhá odchodovou animací a její tlačítka reagují dál,
  takže druhé ťuknutí na šipku odebralo seznam chatů pod ní. Spolehlivě se to vyvolá
  klávesnicí — Tab dá fokus šipce v hlavičce a mezera ji „ťukne" (`adb shell "input keyevent 61;
  input keyevent 62 62"`); stejně tak `adb shell input text` s mezerou, když pole nemá fokus.
  Pojistka patří tam, kde se odebírá: obrazovka, která v zásobníku už není, nesmí odebrat nic.
- Vybraná sekce přežije zabití procesu (`rememberSaveable` podle názvu `TopLevelDestination`),
  zásobníky taky (`rememberNavBackStack`).

### Klávesové zkratky

Fold, tablet nebo Chromebook s fyzickou klávesnicí: **Ctrl+K** hledání chatů, **Ctrl+F** hledání
v otevřené konverzaci, **Alt+↓ / Alt+↑** a **Ctrl+Tab / Ctrl+Shift+Tab** další a předchozí chat,
**Esc** zpět (zavře hledání, konverzaci). Pravidla jsou čisté funkce (`keyboardShortcut`,
`neighbourChat`, testy `KeyboardShortcutsTest`). Enter ve vstupním poli viz
`konverzace-psani.md`, „Enter z fyzické klávesnice".

- **Zkratky se chytají v `MainActivity.dispatchKeyEvent`, DŘÍV než klávesu dostane Compose.**
  V Compose dorazí klávesa jen k zaostřenému prvku — a bez fokusu (seznam chatů po spuštění)
  nikam. Zaostřené vstupní pole by navíc Alt+šipku spolklo jako pohyb kurzoru. Zkratky jsou proto
  jen takové, které psaní nepotřebuje; s Meta (⌘, Windows) žádná, tu si bere systém.
- **Zkratka je událost** (`KeyboardShortcuts`, Koin `single`), stejně jako `ChatOpenRequests`.
  Kdo ji neposlouchá (přihlašovací obrazovka), tomu klávesa propadne dál (`trigger` vrací `false`).
- **Hledání obsluhuje shell** (`ShellShortcuts`): zná zásobník Chatů a otevřenou konverzaci.
  Ctrl+K jde cestou hashtagu z konverzace (`AppNavigation.openSearch`): v dvoupanelu zůstane
  konverzace vpravo, na telefonu se zásobník srovná. Ctrl+F jde cestou „Hledat" z profilu
  (`ConversationSearchRequests`) do konverzace navrchu, i v archivu.
- **Přepínání chatů obsluhuje seznam** (`ChatSwitchShortcuts` v `ChatListContent`): jen on zná
  pořadí, jak ho uživatel vidí, i s blokem nepřečtených. Funguje tedy, kde je seznam složený —
  na rozloženém Foldu se prochází chat za chatem a vpravo se mění konverzace; na telefonu
  s otevřenou konverzací seznam složený není a zkratka nedělá nic. Na krajích se nezacyklí,
  sponzorovaný chat přeskočí, ve výběru a v uspořádání připnutých nic.
- **Esc = Zpět je výslovně v `MainActivity`** (`handleEscape`), ne systémová záložní akce. Mapa
  kláves systému sice Esc převede na Zpět, ale jen když klávesu nikdo nezpracuje — a zaostřené
  vstupní pole ji spolkne: z konverzace s kurzorem v poli Esc nedělal nic. Zpět se volá až při
  uvolnění klávesy, stisk se jen spolkne.
- **iOS zkratky nemá** — chybí obdoba `dispatchKeyEvent` (`UIKeyCommand`); Enter ve vstupním
  poli tam funguje, protože ho řeší pole samo.
- **Fyzická klávesnice na emulátoru:** `input keyevent` / `keycombination` posílá z virtuálního
  zařízení (`VIRTUAL_KEYBOARD`) — na zkratky stačí, na Enter ne (ten rozlišuje fyzickou
  klávesnici). `adb emu event send` na emulátoru 37 klávesy nedoručí vůbec. Funguje systémový
  `uinput`: JSON s `register` (USB klávesnice, `UI_SET_EVBIT` 1, `UI_SET_KEYBIT` kódy kláves),
  `delay` 2000 ms a `inject` s trojicemi typ–kód–hodnota (`[1,28,1, 0,0,0, 1,28,0, 0,0,0]` =
  Enter), nahrát do `/data/local/tmp` a `adb shell uinput <soubor>`. Že události dorazily,
  ukáže `adb shell getevent -l`. Pozor: `adb emu event text` s `\n` taky pošle Enter z klávesnice
  emulátoru — a zprávu tím odešle.
- **Systémový přehled zkratek (Meta+/) je ukazuje** (`MainActivity.onProvideKeyboardShortcuts`,
  `KeyboardShortcutOverview`): skupina Chaty (hledání, další a předchozí chat, Esc) a Psaní zprávy
  (Enter, Shift+Enter). Klávesy se berou ze společného seznamu `SHORTCUT_BINDINGS` vedle pravidla
  `keyboardShortcut`. **Že se seznam s pravidlem nerozešel, hlídá test**, jinak by přehled
  sliboval klávesu, která nic nedělá. Texty se čtou předem (`load` v `onCreate`): přehled se
  skládá synchronně na hlavním vlákně, kdežto prostředky Compose jsou `suspend`.
  - **Nový přehled (Android 15+) stejné popisky slučuje** („Další chat: Alt+↓ nebo Ctrl+Tab").
    Na telefonu je skupina aplikace až pod systémovými kategoriemi, ne mezi nimi; najde ji
    i vyhledávání v přehledu.
  - **Esc („Zpět") nový přehled NEUKÁZAL**, ostatní položky ano (emulátor, Android 17). Proč,
    ověřené není; položka zůstává, protože starší přehled (Android 12–14) ji ukázat může.
  - Vyvolá se `adb shell input keycombination 117 76` (Meta+/); v přehledu `input text` hledá.
    Esc přehled zavře, klávesnici v něm schová Zpět (`keyevent 4`).
- **Ověřeno na emulátoru (Android 17):** Ctrl+K v jednom i dvou panelech, Ctrl+F, Alt+↓ / Alt+↑
  a Ctrl+Tab v dvoupanelu na šířku, Esc zavře hledání i konverzaci se zaostřeným polem a na
  seznamu aplikaci opustí jako Zpět. Na skutečném Foldu s klávesnicí neověřeno.

