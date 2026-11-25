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
Seznam v dvoupanelu konzumuje pravý inset (`listPaneInsets`) — ten patří panelu vpravo. Detail
vedle seznamu a profil konzumují levý, detail vedle profilu pravý (`consumeSide` v `PaneCard`).
Levý bok dřív konzumoval rail; s jeho odstraněním ho nikdo nebral a konverzace vedle seznamu měla
naležato vlevo prázdný pruh asi 53 dp. Každý bok je ve vlastním uzlu, ne dvě konzumace v jednom
řetězci: při změně scény by si místa vyměnily a uzel by dostal jiné insety (Gotchas v CLAUDE.md).
Ověřeno na emulátoru v obou rotacích i s profilem vedle konverzace, deset otočení mezi jedním
a dvěma panely bez pádu; Fold ověřený není. Vstupní pole bere `safeDrawing` bokem i spodkem, jinak by odeslání zajelo pod
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

Co zbývá (táhlo, kotvy, vzhled panelů), je v `docs/roadmap.md`, „Velká plocha".

- **Počet panelů určuje shell, ne knihovna** (`core/adaptive/PaneDirective.kt`): pod
  `TWO_PANE_MIN_WIDTH` (720 dp) jeden, nad ním dva, od `THREE_PANE_MIN_WIDTH` (Large,
  1200 dp) tři — ale jen s profilem navrchu. Výsledek `calculatePaneScaffoldDirective` se
  přebije `singlePane()` / `twoPane()` / `threePane()`; z knihovny zůstávají výchozí šířka
  panelu a pant.
- **Práh 720 dp není standardní, ale má základ**: seznam (`LIST_PANE_WIDTH`, 360 dp) + nejužší
  konverzace (`MIN_CONVERSATION_WIDTH`, 360 dp), a vnitřní displej Foldu (Medium) tak dostane
  dva panely. Standardní dvoupanel na Medium (od 600 dp) by konverzaci nechal 240 dp.
- **Velikostní třída se počítá z `BREAKPOINTS_V2`.** V1 zná jen Compact, Medium a Expanded,
  takže i okno přes dva monitory bylo pro knihovnu Expanded. S V2 dává XL (≥ 1600 dp) širší
  výchozí panel — 412 dp místo 360 (`DefaultPreferredWidthXL`).
- **Profil a obrazovky z něj jsou `Panes.extra()`** (úprava, pozvánky, žádosti, práva člena,
  i archivní varianty). Na Large stojí seznam | konverzace | profil, pod Large scaffold podle
  historie ukáže konverzaci a profil a seznam schová, na telefonu profil konverzaci nahradí.
- **Metadata panelů jdou přes `Panes`, ne přímo přes `ListDetailSceneStrategy`.** Knihovna má
  klíč i hodnoty rolí `internal`, takže by se z položky nedalo zjistit, do kterého panelu patří.
  `Panes` k nim přidá vlastní `PaneInfo` (role a scéna); shell podle něj srovnává zásobník
  a volí počet panelů.

Chování knihovny (`adaptive-navigation3` a `adaptive-layout` 1.3.0-beta02, přečteno ze
zdrojových jarů v gradle keši — `ThreePaneScaffoldScene.kt`, `ThreePaneScaffoldValue.kt`,
`ThreePaneScaffold.kt`):

- **Scéna kreslí do každého panelu POSLEDNÍ položku té role** (`findLast`). Víc položek
  `extraPane` nad sebou je tedy v pořádku: profil → úprava skupiny ukáže úpravu na místě profilu
  a zpět (`PopUntilContentChange`) vrátí profil.
- **Proto profil zůstane viset, když se nad něj otevře jiná konverzace** — poslední detail je
  nová konverzace, poslední extra pořád starý profil. Na široké ploše se takový „zakrytý" extra
  (`coveredExtras`, testy `CoveredExtrasTest`) i s tím, co se z něj otevřelo, ze zásobníku
  odebere a hned za novou konverzaci se vloží JEJÍ profil (`followProfile`, testy
  `FollowProfileTest`): panel zůstane otevřený a ukazuje chat, který je vedle, jako panel
  s informacemi v Telegram Desktop. Kreslí se rovnou opravený zásobník a efekt pak opraví
  i skutečný, jako u sekce Nastavení. Obrazovka bez profilu (článek, Uložené zprávy po chatech)
  panel zavře; zavřený panel (✕, hlavička) se přepnutím neotevírá. Na telefonu je to obyčejná
  historie a zpět se do profilu vrací.
  - **Napřed se zakrytý profil jen zavíral** (první verze). Přepnutí chatu tak zavřelo panel,
    který si uživatel otevřel, a u každého chatu se musel otevírat znovu.
  - **Je to tedy boční panel a ovládá ho JEDEN přepínač v hlavičce vedle, ne ✕ v panelu**
    (`ProfilePaneButton`, zrcadlo přepínače seznamu vlevo): v hlavičce konverzace a seznamu témat
    před ⋮ „Otevřít panel s profilem" (`right_panel_open`), s otevřeným panelem „Zavřít panel"
    (`right_panel_close`). Stojí pořád na jednom místě a nepřeskakuje do panelu; první verze měla
    „Zavřít" v profilu a „Otevřít" v hlavičce, takže tlačítko při každém přepnutí skočilo jinam.
    Obojí volá `toggleProfile`, ťuknutí na název v hlavičce taky.
  - **Kořen panelu proto nemá šipku zpět ani ✕** (`paneRoot` u profilu, `standsOnDetail`, testy
    `StandsOnDetailTest`; kalendář zpráv taky). Profil nad jiným profilem (člen z profilu
    skupiny) šipku zpět má — vrací v rámci panelu — a stejně tak profil nad čímkoli, co není
    konverzace ani seznam témat, aby bez přepínače vedle nezůstal bez cesty ven.
  - **Otevřít jde jen u chatu s profilem, zavřít vždycky**: kalendář stojí i vedle Uložených
    zpráv a profil chatu se přepnutím přenese i vedle vlákna komentářů.
  - **Zkratka Ctrl+Shift+I, na macOS ⌥⌘I** (inspektor ve Finderu) dělá totéž co přepínač. Ne
    Ctrl+Alt+I: na Windows je Ctrl+Alt totéž co AltGr a AltGr+I v některých rozloženích píše znak.
    Poslouchá ji hlavička, ne shell — ví, jestli chat profil má, a nemusí rozlišovat druhy
    konverzací ani archiv.
  - Ověřeno na desktopu (macOS, testovací chat) jen v první verzi (otevření z hlavičky, zavření
    v profilu, ⌥⌘I tam i zpět). Verze s jedním přepínačem, kalendář bez ✕, seznam témat, archiv,
    Windows a Fold neověřené.
- **Scaffold se třemi oddíly rozbalí třetí panel i PRÁZDNÝ** (`tertiaryPane ?: {}`;
  `calculateThreePaneScaffoldValue` prochází po historii ještě všechny tři role podle priority).
  Bez profilu by tak vpravo zůstal prázdný sloupec — třetí oddíl se proto zapíná, jen když je
  navrchu scény extra položka. Prohlížeč nad profilem roli nemá a přeskakuje se, aby se pod ním
  scéna kreslila tak, jak zůstala.
- **Zpět gestem z profilu na Large nezobrazí během tahu, jak se třetí panel zavírá**: předchozí
  stav scaffoldu se počítá s TOUTÉŽ direktivou (tři oddíly), takže v něm třetí panel pořád je;
  zavře se animací až po dokončení gesta, kdy shell přepne na dva oddíly.
- **Zavřený třetí panel by zmizel problikem, ne odjel.** Scéna kreslí do třetího panelu
  POSLEDNÍ extra položku (`lastExtra?.let { … }`); jakmile ze zásobníku zmizí, dostane scaffold
  místo obsahu `null` a panel zmizí v jednom snímku, i když se rozvržení animuje.
  `rememberWithLeavingExtras` (`core/adaptive/LeavingExtras.kt`) proto odcházející položky
  ještě 600 ms drží ve scéně — NA ZAČÁTKU zásobníku: scaffold přiděluje oddíly podle historie
  odzadu, takže položka vepředu zůstane `Hidden` a `AnimatedPane` ji nechá odjet (navrchu by
  se panel neskryl vůbec). Jen pro kreslení a jen s panely vedle sebe, skutečný zásobník ji už
  nemá; znovu otevřený panel odcházející hned zahodí. Platí pro profil v Chatech i podstránky
  Nastavení. Ověřeno na desktopu uživatelem.
- **Táhlo (`paneExpansionDragHandle`) funguje jen mezi DVĚMA rozbalenými panely**
  (`expandedPanes.size == 2` v měření; `paneExpansionStateKey` je pro tři panely `Default`).
  Ve třech panelech rozhodují šířky z `preferredPaneSize`: zbytek dostane panel s nejvyšší
  prioritou (detail, tedy konverzace), a když se nevejdou, zmenší se poměrně všechny.
- **Sdílené prvky a container transform** vypíná `LocalPanesSideBySide` (dřív `LocalTwoPane`)
  i pro třetí panel — avatar by letěl z hlavičky konverzace, která zůstává vidět.

### Táhlo, kotvy a sbalený seznam

- **Stránky leží na ploše okna, karty jsou jen konverzace a profil vedle ní** (`PaneCard`, styl
  Gmailu / Google Chatu). Dřív byl kartou každý panel zvlášť s mezerami 24 dp a uvnitř seznamu
  i profilu byly další karty (řádky, bloky informací) — tři vrstvy zaoblených ploch do sebe, což na
  desktopu působilo nepořádně. Plochu okna (`surfaceContainer`) kreslí shell pod scénou.
  - **Stránkám se `surface` a `background` přemapují na `surfaceContainer`** (`asListPage`, téma
    v `PaneCard`, viz „Plocha stránky a řádku" v `seznam-chatu.md`), a to i na telefonu. Obrazovky
    si pozadí kreslí samy přes `Scaffold`; bez přemapování by na ploše okna stál obdélník o tón
    jiný — karta bez rohů.
  - **Karta konverzace (`Panes.ownSurface`: konverzace, článek, placeholder) je `surface` se
    `shapes.large`** a okrajem `PANE_SPACING` (12 dp) ze stran a zdola, i od kraje okna (nahoře
    viz níž). S okrajem jen od seznamu narážela nahoru a dolů a zaoblený roh u spodního kraje
    okna, bez jediného bodu mezery, vypadal jako chyba.
  - **Nahoře je okraj karty jen 4 dp** (`PANE_TOP_SPACING`), ať horní hrana lícuje s pilulkou
    hledání v seznamu vedle: `AppBarWithSearch` ji odsadí pod stavovou lištou o interní
    `AppBarWithSearchVerticalPadding` (4 dp v material3 1.5.0-alpha22). S 12 dp i nahoře
    začínala karta o 8 dp níž a na Foldu to bylo vidět na první pohled. Posunout naopak pilulku
    by seznamu vzalo 8 dp výšky. Po bumpu material3 hodnotu porovnat. Dnes to platí jen pro článek
    a další karty bez hlavičky venku, konverzace a placeholder mají geometrii z dalšího bodu.
  - **Hlavička konverzace leží NAD kartou, na ploše okna** (`Panes.headerOutside`, `PaneHeader`
    v `core/adaptive/PaneHeader.kt`; styl Google Zpráv). Vlevo stojí na ploše pilulka hledání
    a pod ní řádky, vpravo byla jedna karta i s hlavičkou: každá polovina mluvila jiným jazykem
    a hlavičku od zpráv dělila jen změna barvy při odrolování. Teď je nahoře jedna řada
    „pilulka | hlavička“, pod ní obsah, a zprávy se ořežou o hranu karty.
    - **Karta ořezává tvarem „pruh hlavičky + zaoblený obdélník pod ní“** (`HeaderOutsideShape`),
      takže hlavička zůstává uvnitř karty (přechody, predictive back, táhla) a jen nemá plochu.
      Výšku hlavičky hlásí `PaneHeader` přes `onSizeChanged` do `PaneHeaderHost`.
    - **Držák výšky musí být stabilní** (`remember {}`), barvy plochy okna jdou zvlášť
      (`LocalPanePlaneScheme`). S `remember(scheme)` vznikal při přechodu motivu v každém snímku
      nový držák s výškou 0, `onSizeChanged` se při stejné velikosti znovu neozval a karta na
      dobu přechodu (a po rozložení Foldu) překryla hlavičku. Ze stejného důvodu hlásí hlavička
      výšku i na telefonu, kde leží uvnitř — po rozložení se její velikost nezmění.
    - **Hlavička je v barvách aplikace, ne chatu** (`ChatColorTheme` je až uvnitř položky, plocha
      okna ho nezná). Barvu chatu nese karta; neutrální hlavička nad tónovanou kartou je vědomá
      cena (ověřeno na zelené ve světlém i tmavém motivu).
    - **Karta začíná ve výšce prvního řádku seznamu**: hlavička i lišta hledání mají 64 dp,
      seznam je pod lištou odsazený o 8 dp, takže hlavička nese mezeru `PANE_HEADER_GAP` 8 dp pod
      sebou. Mezera patří hlavičce, ne kartě, aby obsah Scaffoldu začínal až pod ní.
    - **Karta jde až pod spodní kraj okna** (bez spodního okraje, insetu a dolních rohů), jako
      seznam vedle; spodní inset si bere vstupní pole samo, jako na telefonu. Horní a spodní inset
      jsou proto ve dvou uzlech (pravidlo z Gotchas o insetech při změně scény).
    - **Placeholder má stejnou geometrii** (prázdný `PaneHeader` výšky `TopAppBarExpandedHeight`),
      jinak by karta při otevření chatu poskočila.
    - **Profil, jeho podstránky a kalendář zpráv ve třetím panelu taky** (`headerOutside` na všech
      položkách s `profilePaneWidth`). Jsou to stránky s bloky řádků, plochu si kreslí Scaffold
      přes celou výšku — proto `paneScaffoldColor()` (s hlavičkou venku průhledná) a obsah
      v `PaneBody`, který začíná pod hlavičkou a nese plochu stránky. Hlavičky jsou
      `ProfileSubpageTopBar` (jedno místo pro osm podstránek), lišta profilu a lišta kalendáře.
      Kalendář míval vlastní řádek s nadpisem sheetu (`titleLargeEmphasized`, nadpis o 4 dp
      jinde); teď je to týž `TopAppBar` bez insetů (bere si je sloupec kolem). V barvách
      aplikace je karta profilu dál neviditelná (stránka = plocha okna).
    - **Obsah pod hlavičkou potřebuje vlastní horní odstup**: dřív ho dělala lišta nad ním.
      Fotka profilu (16 dp) a čipy kalendáře (12 dp) jinak narážely na hranu karty; seznamy
      podstránek si 8 dp nad prvním blokem přidávají samy.
    - **Konverzace a boční panel mají jeden pás hlavičky** (`PaneBand` v `core/adaptive`). Vlevo
      je konverzace (šipka, avatar, název přes celou šířku jejího panelu), její akce (hovor,
      přepínač panelu, ⋮) jsou nad kartou bočního panelu u pravého okraje okna. Kreslí je
      `PaneCard` bočního panelu, konverzace je tam pošle (`publishToPaneBand`) a ve své liště
      vynechá.
      - **Proč akce u okraje okna:** se zavřeným panelem tam stojí taky (konverzace je poslední
        panel), takže s otevřeným panelem zůstanou na místě. Dřív uskočily doprostřed okna, i to
        tlačítko, kterým se panel právě otevřel. Dvě lišty vedle sebe navíc opakovaly jméno chatu
        a profil měl druhé ⋮ nad týmž chatem.
      - **Boční panel do pásu nepíše, jeho ovládání je v kartě**: s pásem je hlavička obrazovky
        panelu uvnitř karty (`LocalPaneHeaderOutside` = `false`), ne nad ní — šipka zpět
        a nadpis podstránky, přepínač roku v kalendáři, ⋮ profilu člena. V panelu širokém
        360 dp by se vedle čtyř akcí chatu nevešly a nadpis mimo svou kartu by přestal říkat,
        ke které kartě patří.
      - **Kořen profilu vedle vlastní konverzace lištu nemá vůbec** (`besideOwnConversation`):
        jméno je v hlavičce konverzace, akce nad chatem i Upravit jsou v jejím ⋮ (`ChatActions`).
        Profil člena nad konverzací skupiny je jiný chat a lištu (v kartě) si nechává.
      - **Akce se skládají v kompozici bočního panelu**, takže co v nich bere ViewModel, dostane
        ho hotový od konverzace: `koinViewModel` uvnitř by sáhl do úložiště ViewModelů panelu
        a vrátil jinou instanci (ořez navrhované fotky by se pak neukázal).
      - **Karta panelu začíná ve výšce karty konverzace**, změřené v okně
        (`PaneBand.detailCardTop`), ne pevným číslem: hlavička konverzace nese podle platformy
        místo pro tlačítka okna nebo stavovou lištu. Kreslí jen karta zobrazené položky
        (`LocalPaneResizeOwners`), odjíždějící panel by akce zdvojil i s jejich efekty
        (zkratka přepínače panelu by se přepnula dvakrát).
      - **`verifyWindowControls` čte TEXT volání lišty**: sdílená lambda s `WindowControlsSpace`
        předaná do `navigationIcon` neprojde, místa pro tlačítka okna musí stát přímo v argumentech
        každého `…TopAppBar(`. Pás dává `WindowControlsEndSpace` za akce sám.
      - **Podstránky profilu mají vedle konverzace malý `TopAppBar` s podtitulkem**, na jednom
        panelu dál střední flexibilní bar (přes 100 dp by kartu natáhl zbytečně dolů).
    - Ověřeno na emulátoru Fold: světlý i tmavý motiv a přechod mezi nimi, hledání v chatu,
      barva chatu, placeholder, profil a kalendář vedle konverzace (i v barvě chatu), šest cyklů
      složit/rozložit bez pádu s konverzací i s profilem; složený telefon beze změny. Podstránky
      profilu (statistiky, pozvánky…) naživo neověřené — testovací chat (bot) je nemá.
  - **Profil a co se z něj otevírá je v Chatech karta taky** (`PaneNeighbours.extraCard`), jen bez
    vlastní plochy: ořez a okraj stejný jako konverzace. V barvách aplikace je jeho stránka táž
    jako plocha okna, takže karta není vidět a profil zůstává stránkou na ploše. V barvě chatu
    (`ChatColorTheme` přebarví i `surfaceContainer`) je z něj zaoblená karta v odstínu chatu
    a s konverzací tvoří dvojici; dřív stál přes celou výšku jako obdélník u kraje okna.
    Podstránky Nastavení ve třetím panelu kartou nejsou — sekce vedle nich je stránka.
    `extraCard` hlídá sekci, ne otevřený profil: zavíraný profil ještě odjíždí a s okraji by
    uprostřed animace poskočil.
  - **Do kotev se okraje profilu nepočítají** (`PROFILE_CARD_MARGINS`): šířka kotvy platí pro
    kartu, panel je o mezeru od konverzace a okraj od kraje okna širší. Jinak by se na výchozí
    šířce nevešly dlaždice akcí. `profileWidthBeside` a šířka seznamu ve třech panelech
    (`GAPS_BESIDE_PROFILE`) s nimi počítají, aby kartě konverzace zbylo `MIN_CONVERSATION_WIDTH`.
  - **Karta u kraje okna se odsadí o systémové lišty a výřez, ne o klávesnici** (`PaneEdgeInsets.Pad`).
    Pod stavovou lištou by ležely její horní rohy; klávesnici dál řeší konverzace sama a karta za
    ni zajede. Boky, které u kraje nestojí, inset jen zkonzumují. Každý bok je ve vlastním uzlu
    a podmínka mění celý modifikátor, ne jeho hodnotu (Gotchas v CLAUDE.md, rozložení Foldu).
  - **Mezeru mezi panely nedělá scaffold** (`horizontalPartitionSpacerSize = 0`): jeho mezera je
    všude stejná a přes ni kreslit nejde — panely mají `clipToBounds` (`AnimatedPane`). Okraj si
    dělá karta; mezi konverzací a profilem je jeden (dělá ho profil). Táhla stojí uprostřed mezer
    (`PaneEdge`). Práh dvou panelů: 360 + 12 + 360 + 12 = 744 dp.
  - Kartu dává dekorátor položek (`rememberPaneCardDecorator`) všemu, co má roli panelu;
    placeholder ji má zvlášť (`PaneCard(PaneRole.Detail)`), protože položkou není. Na telefonu
    je struktura táž (téma i `Box`y), jen bez ořezu, okrajů a přemapování — větvení by obsahu
    při změně šířky okna vzalo stav.
  - Ověřeno na desktopu (Windows, světlý motiv): seznam + placeholder, seznam | konverzace,
    seznam | konverzace | profil v barvách aplikace i v barvě chatu (růžová) a konverzace | profil
    v užším okně; táhlo profilu stojí uprostřed mezery. Původní rozvržení i v tmavém motivu.
    **Tmavý motiv s okraji, tablet a Fold (insety lišt, rozložení) ověřené nejsou** — emulátor
    při ověřování kreslil jen bílou plochu.
- **Táhlo jen mezi HLAVNÍM seznamem chatů a konverzací** (`ListPaneExpansion.kt`). Stav táhla
  (`PaneExpansionState`) počítá šířku PRVNÍHO rozbaleného panelu, takže mezi konverzací
  a profilem (pod Large) by konverzaci zmáčkl na šířku sbaleného seznamu. Archiv, nová zpráva
  a Nastavení mají výchozí šířku. Kde táhlo není, dostane scaffold `paneExpansionState = null`
  a vytvoří si vlastní výchozí.
- **Kotvy** (`ListPaneAnchor`): sbalený sloupec 96 dp (šířka railu M3 Expressive), výchozí šířka
  z direktivy (360, na XL 412) a polovina plochy. Po puštění se seznam dorovná na nejbližší.
- **Zvolená kotva se ukládá jen po akci uživatele.** Stav začíná na uložené kotvě; dokud se
  `AppSettings` nenačte, je to výchozí, a ta se neukládá (shoduje se s `null`), takže uloženou
  nepřepíše. `animateTo` kotvy, kterou stav ještě nezná, vyhodí `IllegalArgumentException`
  (`require` v knihovně) — chytá se jen ta, `runCatching` by spolkl i zrušení korutiny.
- **Ve třech panelech je táhlo seznamu vlastní** (`rememberListPaneResize`, viz „Táhlo profilu"; knihovna tam žádné nekreslí) a šířka jde metadaty seznamu
  (`listWidthBesideProfile`, testy `ListWidthBesideProfileTest`): sbalený zůstane sbalený, širší
  se zúží, aby konverzaci vedle profilu zbylo aspoň 360 dp. **Metadata položky se ale počítají
  jen při změně zásobníku** (`rememberDecoratedNavEntries(zásobník, …)` si položky pamatuje podle
  klíčů), takže si položky Chatů shell staví sám s šířkou v klíči. Ze stejného důvodu se
  v obsahu položek nesmí spoléhat na hodnoty zachycené při stavbě poskytovatele (`wide`):
  po změně šířky okna bez navigace zůstanou staré. Co závisí na šířce, čte
  `LocalPanesSideBySide` uvnitř obsahu (✕ v profilu, chování šipky zpět v konverzaci).
- **Sbalený seznam (`CollapsedChatList`) se zapíná podle ŠÍŘKY, ne kotvy** (pod 200 dp): během
  tahu se prochází všemi šířkami a řádek s názvem by byl nečitelný dávno před sbalenou kotvou.
  Hledání ze sloupce seznam napřed rozbalí (`LocalExpandListPane`) a požadavek na hledání počká,
  až se pilulka objeví.
  Karty nad seznamem (aktualizace, nové přihlášení, narozeniny…) se do sloupce nevejdou; že
  nějaká čeká, ukazuje tlačítko s tečkou pod lupou (`PendingCard`, ikona a bublina podle
  nejdůležitější, zmrazený účet a cizí přihlášení v chybové barvě). Ťuknutí seznam rozbalí
  a `requestScrollToItem(0)` ho pošle na vršek — `scrollToItem` by čekal na rozložení seznamu,
  který se v tu chvíli teprve skládá.
- **Mezi plným seznamem a sbaleným sloupcem se přelétá** (`ListCollapseTransition.kt`). Obě
  podoby jsou v jednom `AnimatedContent` podle šířky a prvky, které mají obě, přeletí na nové
  místo (`collapseFlight`): avatar účtu (konec řádku s pilulkou ↔ vršek sloupce), lupa (pilulka
  ↔ tlačítko hledání), Vytvořit (plovoucí lišta dole ↔ FAB pod účtem), přepínač sbalení (za
  semafory ↔ vršek sloupce) a avatary chatů. Zbytek se
  prolne. Dřív prvky v jednom snímku zmizely a jinde se objevily.
  - **Let je ruční, ne `sharedElement`.** Sdílený prvek bere cíl z rozložení „napřed"
    (lookahead) a v něm má panel seznamu jinou šířku než ve skutečnosti: šířku po puštění táhla
    scaffold teprve dotahuje na kotvu. Avatar účtu a lupa proto stály po celý přechod vpravo od
    osy sloupce a na konci přeskočily, avatary chatů v líném seznamu stály úplně (`isMatchFound`
    přitom true; beze změny i bez `animateItem`) — zachyceno sériemi snímků během tahu.
  - **Ruční let:** každý prvek si v klidu zapisuje polohu v okně (`CollapseFlights`, zvlášť plný
    seznam a sloupec, při odchodu ze seznamu se maže), přicházející se při prvním umístění posune
    a zvětší na místo protějšku (`graphicsLayer`) a dojede domů.
  - **Průběh letu je průběh přechodu** (`transition.animateFloat`), ne vlastní `Animatable`:
    s vlastní pružinou doletěly avatary dřív než ostatní. Během letu se prvek kreslí v překryvu
    (`renderInSharedTransitionScopeOverlay`), jinak by ho ořízl seznam — do sloupce se letí zpod
    hlavičky. **Pružina přestřelí přes 1**, transformace se proto vypíná až na přesné 1; s `>= 1`
    prvek na konci poskočil.
  - **Každá podoba se rozkládá ve své šířce, ne v šířce panelu**: sloupec nejvýš na prahu
    (`widthIn(max = COLLAPSED_LIST_MAX_WIDTH)`) u levého kraje, plný seznam aspoň na prahu
    (`wrapContentWidth(Start, unbounded)` + `requiredWidth`, přesah ořízne panel). Odcházející
    podoba se během přechodu rozkládá v NOVÉ šířce panelu: sloupec se v širokém panelu vystředil
    a avatary, lupa i Vytvořit letěly ze středu panelu (zachyceno snímky během tahu).
  - **Pozice v seznamu jde přes kotvu** (`firstVisibleChatKey`): klíč prvního celého chatu
    v odcházejícím seznamu se spočítá v okamžiku přepnutí, dokud je ještě rozložený. Sloupec na
    něj skočí (`requestScrollToItem`), plný seznam po rozbalení použije dojetí z hledání
    (`scrollToChat`, jen jeden účet). Bez toho sloupec začínal nahoře a z odrolovaného seznamu
    neměl kam doletět žádný avatar. Obě podoby mají tytéž klíče položek (`chat.id`,
    ve společném seznamu `MergedChat.key`), avatary pro let nesou i účet (`collapseAvatarKey`).
  - **S letem avataru do hlavičky konverzace se nepere**: sbalený sloupec je jen vedle panelů
    a tam `sharedNavElement` nesdílí.
  - **Během přechodu jsou složené oba seznamy a snímky jsou delší** (v logu průběhu letu
    0 → 0,49 → 0,76 → 1,05 → 1, tedy jen pár snímků). Na desktopu v ladicím běhu; výkon release
    buildu a telefonu neměřený.
  - Ověřeno na desktopu (macOS) tahem táhla tam a zpět a sérií snímků uprostřed přechodu, i při
    rychlém přetahování přes práh. Společný seznam víc účtů a Fold neověřené.
- **Tlačítko sbalení seznamu** (`ListPaneToggleButton`, stav `ListPaneToggle` přes
  `LocalListPaneToggle`): v plném seznamu na začátku řádku s pilulkou (na Macu hned za semafory,
  ve slotu `WindowControlsSpace`), ve sloupci nahoře nad účtem jako tlačítko menu v railu M3.
  Ikona mezi oběma místy přelétá (`collapseFlight`) jako účet a lupa; bez letu jen přeskočila.
  Táhlo samo se najít nedalo a z klávesnice ani čtečkou s ním pohnout nejde (WCAG 2.5.7 chce
  ke každému tahu alternativu na jedno kliknutí); totéž dělá zkratka Ctrl+Shift+S / ⌃⌘S.
  - **Jen u hlavního seznamu vedle konverzace**, kde je táhlo: ne na telefonu, v Archivu,
    v Nastavení ani v hledání (to seznam rozbaluje samo).
  - **Rozbalí se na poslední nesbalenou kotvu** (`ListPaneExpansion.expandedAnchor`, i pro
    hledání a karty), ne vždy na výchozí šířku. Drží se jen v paměti: po spuštění se sbaleným
    seznamem je to výchozí šířka.
  - **Směr přepínače je zvolená kotva, podoba tlačítka je místo, kde stojí**: plný seznam má
    vždy „Sbalit", sloupec „Rozbalit". Za tahu táhlem se podoba přepne podle šířky dřív, než
    seznam dosedne na kotvu, a tlačítko podle kotvy by slibovalo opak.
  - **Ve dvou panelech dojede táhlo knihovny** (`PaneExpansionState.animateTo`, kotvu zapíše hned
    a uloží ji tentýž `snapshotFlow` jako po tahu), **ve třech vlastní** (`ListPaneWidth.settleTo`,
    stejný dojezd jako po puštění táhla).
  - Ověřeno na desktopu (macOS, ladicí běh): tlačítko za semafory, sbalení kliknutím, rozbalení
    a sbalení ⌃⌘S i položkou nabídky „Chaty", po restartu se sbaleným seznamem; let ikony
    ověřil uživatel. Tři panely,
    Windows, Android (Fold, Ctrl+Shift+S) a čtečka obrazovky neověřené. Při prvním běhu jednou
    rozbalení nezabralo ani z nabídky a znovu se to vyvolat nepodařilo.
- **„Vytvořit" a výběr víc chatů má i sbalený sloupec.** FAB je nahoře pod účtem jako FAB
  v navigačním railu (sloupec má jeho šířku); FAB menu plného seznamu vyjíždí s popisky nad
  FAB a do 96 dp by se nevešlo, takže tytéž tři položky nabídne `ExpressiveMenu` vpravo od
  tlačítka (okno, ořez panelu ho neusekne). Výběr se zapíná podržením avataru (nabídka nad
  chatem s „Vybrat", táž jako v plném seznamu); ve výběru je nahoře ✕ s přetáčeným počtem,
  u avatarů `SelectionMark` a dole svislá plovoucí lišta (`ChatSelectionToolbar(vertical = true)`
  nad `VerticalFloatingToolbar`). Výběr je stav ViewModelu, takže přežije rozbalení táhlem
  i složení Foldu; zpět ho ukončí i ve sloupci (vlastní `NavigationBackHandler` — ten
  v `ChatListContent` se ve sloupci neskládá).
- **Snackbar sbaleného sloupce stojí z kořene okna** (`CollapsedSnackbar`: `RootOverlay`
  + `Popup` vlevo dole, jen když nějaká hláška je). `SnackbarHost` plného seznamu se ve
  sloupci neskládá a do 96 dp by se nevešel, takže archivace výběrem byla bez „Vrátit".
  Okno z kořene, ne ukotvené ve sloupci, ze stejného důvodu jako bubliny: sloupec mizí
  uprostřed měření. Okno nezavírá zpět ani ťuknutí vedle (`dismissOnBackPress = false`) —
  jinak by na čtyři vteřiny spolklo systémové zpět.
- **Podržení avataru na dotyku ukazovalo i bublinu se jménem** (kotva `TooltipBox` reaguje na
  týž dlouhý stisk) a ta po otevření nabídky nad chatem visela nad sloupcem, dokud se uživatel
  nedotkl jinde. Avatar proto má `TooltipBox` s vlastním stavem a podržení bublinu po
  100 ms zavře (pořadí obou obsluh dlouhého stisku není dané).
- Ověřeno na emulátoru Fold (česky): FAB s nabídkou a „Nová zpráva", podržení → „Vybrat",
  výběr dvou chatů s přetočeným počtem a lištou, archivace se snackbarem a „Vrátit" (chat se
  vrátil), zpět ukončí výběr, výběr přežije čtyři cykly `adb emu fold` / `unfold` bez pádu.
  **Desktop jen přeložený**, změna šířky okna se sbaleným sloupcem a snackbarem neověřená.
- **Najetí na avatar a hned tah táhlem shazovalo desktop** na `RootNodeOwner is already disposed`
  (Compose 1.12): `BoxWithConstraints` přepnul sloupec na plný seznam uprostřed měření, kotva
  viditelné bubliny zmizela a skiko změřilo už zrušenou vrstvu popupu. Bubliny se proto kreslily
  z kořene kompozice; Compose 1.13 to opravuje a bublina je zase obyčejný `TooltipBox` — viz
  `docs/poznatky/desktop.md`, „Bubliny (tooltip)".
- **Šipka zpět v konverzaci vedle seznamu konverzaci ZAVŘE** (`closeConversation`, testy
  `CloseConversationTest`): odebere celou větev nad seznamem — konverzace otevřené zmínkami jedna
  z druhé i profil — a zůstane seznam s placeholderem. Procházet zpátky řetězem zmínek šipkou
  nedávalo smysl, když je seznam vedle. Systémové zpět a Esc jdou dál po jedné položce, na
  telefonu je šipka obyčejné zpět. Téma nad seznamem témat se vrací do něj (rodič, ne historie).
  **Vlákno komentářů nad konverzací se vrací do ní** (testy `vlaknoKomentaru…`): čip pod
  příspěvkem ho otevřel nad kanálem, takže kanál je jeho rodič. Dokud se bralo jako zmínka,
  zavřela šipka na desktopu i kanál a na telefonu ne. Vlákno přímo na seznamu (z přehledu
  konceptů) se zavře celé. Třetí panel pro komentáře se zvažoval a zatím ne: třetí panel nese
  profil (`followProfile`, přepínač, táhlo) a pod Large by se tři panely stejně nevešly.
  **Šipku neschovávat**: bez ní nešla otevřená konverzace zavřít vůbec (zkusilo se, vrátilo se).

### Táhlo profilu

Šířka profilu vedle konverzace se mění táhlem na předělu s konverzací (stav
`rememberProfilePaneResize`), kotvy výchozí (360/412) / 480 / 600 dp, zvolená
v `AppSettings.profilePaneAnchor`. Stejným mechanismem má táhlo i seznam ve třech panelech
(`rememberListPaneResize`, kotvy seznamu, ukládá se tatáž `listPaneAnchor` jako z táhla
knihovny ve dvou panelech).

- **Táhlo knihovny na to nejde.** Ve třech panelech ho `ThreePaneScaffold` nekreslí vůbec
  a ve dvou (konverzace | profil) nastavuje šířku PRVNÍHO panelu, tedy konverzace. Šířka
  proto jde metadaty (`Panes.width` u profilu a obrazovek z něj, v Chatech i archivu, a u
  seznamu) a táhla jsou vlastní (`PaneResize`).
- **Táhla se kreslí NAD panely** (`PaneHandleOverlay` přes celou scénu), ne v panelu. Obsah
  panelu se ořezává na jeho okraj: táhlo na předělu by bylo napůl uříznuté a první pokus ho
  proto dal dovnitř profilu — leželo přes dlaždici „Ztlumit" (snímek od uživatele). Panel jen
  hlásí, kde leží (`PaneCard` → `paneBounds`), a vrstva posadí táhlo na jeho okraj: profil na
  levý, seznam doprostřed mezery za pravým. Uzel táhla zabírá celou vrstvu, jinak by posunuté
  táhlo nebralo dotyk; mimo táhlo jde dotyk do panelů pod ním.
- **Za tahu se šířka omezuje hned** (profil: konverzaci zbude aspoň 360 dp a sám nejužší na
  výchozí šířce; seznam: od sbaleného sloupce po místo vedle profilu), po puštění dosedne na
  nejbližší kotvu. Kotva se zapíše do stavu hned, ne až z toku nastavení — ten přijde o snímek
  později a panel by skočil zpátky. Seznam má přednost: širší profil se zúží on.
- **Položky Chatů se za tahu staví znovu** (šířky jsou v klíči `chatsEntries`), obsah ne: drží
  ho `contentKey`.
- **Za tahu a dojezdu panely NEanimuje knihovna** (`DraggedWidth`, `Panes.instantBounds`).
  Napřed šířka za tahu skákala po krocích a knihovní pružina (`AnimatedPane`) každý krok
  animovala znovu: panely prst dotahovaly a cukaly, a po puštění šířka skočila na kotvu bez
  animace — vedle hladkého táhla knihovny ve dvou panelech to působilo trhaně. Teď šířka jde
  přímo za prstem, po puštění dojede na kotvu jednou pružinou (`MotionScheme`, prostorová)
  a po tu dobu mají panely rozměry bez animace (`snap()`). **Animaci rozměrů DETAILU scéna
  bere z položky SEZNAMU** (`lastList.boundsAnimationSpec()` v `ThreePaneScaffoldScene`),
  proto ji nese i seznam.
- Jen Chaty; podstránky Nastavení ve třetím panelu mají výchozí šířku.
- **Polohu táhlu hlásí jen karta položky, která je v Chatech opravdu vidět**
  (`LocalPaneResizeOwners`: profil navrchu zobrazeného zásobníku a hlavní seznam), a smaže ji
  jen ta karta, která ji zapsala (`PaneResize.report` / `release`). Lokály táhel dosáhnou na
  všechno v `NavDisplay`, tedy i na scénu, která po změně scény ještě odjíždí. Po návratu
  z Nastavení s otevřenou podstránkou do Chatů s profilem dostala táhla i karta podstránky
  a karta seznamu Nastavení: zapsaly do slotu svou polohu a po dojetí přechodu ho vynulovaly.
  Nová karta se znovu nepřihlásila (`onGloballyPositioned` se ozve až při pohybu), takže obě
  táhla zmizela, dokud se nezměnilo rozložení. Reprodukováno na desktopu bez opravy (obě táhla
  pryč) a s opravou ověřeno, že zůstanou. Týž mechanismus hrozil u skoku mezi archivem
  (`ARCHIVE_SCENE`) a hlavními Chaty, ten ověřený není.

### Obsah přesouvaný mezi scénami

Při rozložení a složení Foldu (jeden panel ↔ list-detail) NavDisplay obsah položky do jiné
scény PŘESOUVÁ: uzly se odpojí a znovu připojí, nevznikají znovu. Co si nese stav navázaný
na připojení, se tím může rozbít, a to bez chyby v logu.

- **`drawWithCache` s `obtainGraphicsLayer` po přesunu kreslí prázdnou vrstvu.** Compose UI
  1.12 (`CacheDrawModifierNodeImpl`) vrstvy při `onDetach` uvolní, ale mezipaměť nechá platnou
  (zneplatní ji jen `onReset`, změna velikosti uzlu nebo změna stavu čteného v bloku). Kreslí
  se pak nahraná, už uvolněná vrstva. Tak mizela ikona z FAB „Vytvořit" (`animateIcon` z M3,
  bod „Ikona FAB po rozložení Foldu" v `seznam-chatu.md`). Vrstva z modifikátoru `graphicsLayer`
  tímhle netrpí, vlastní ji uzel a po připojení ji nahraje znovu.
- Viz také „Modifikátor insetů … při rozložení nebo složení Foldu" v Gotchas v CLAUDE.md:
  tamtéž přesunutí, jen u insetů padá aplikace.
- **Ověřuje se na emulátoru Fold**: aplikace spuštěná složená, pak `adb emu unfold`. Ikona
  chyběla hned při prvním rozložení.

### Standardní šířky

Na okně přes dva monitory (snímek ~2000 dp) se konverzace roztáhla přes celou šířku: přijaté zprávy
u levého okraje, odeslané u pravého, vstupní pole přes 1600 dp. Šířky se proto berou ze
škály `ContentWidth` (`core/adaptive/ContentWidth.kt`) opřené o M3 a dřívější konstanty po
obrazovkách (420 přihlášení, 560 bublina, 680 článek) jsou z ní.

- **Konverzace je sloupec 840 dp uprostřed** (`ContentWidth.Conversation`, hranice Expanded),
  hlavička zůstává přes celý panel — šipka a akce u okrajů, kde je hledá myš. Pozadí konverzace
  jde přes celou šířku.
- **Seznam zpráv se do sloupce stahuje okrajem v `contentPadding`** (`ConversationColumn`,
  `columnInset`), ne zúžením seznamu: scroll kolečkem i tahem tak funguje kdekoli v panelu.
  Vstupní pole má `Modifier.centeredColumn` přímo na obsahu slotu `bottomBar`.
- **Pruhy nad seznamem jsou ve sloupci taky** (`ConversationBars`: připnuté, spam, videochat,
  žádosti o vstup, reklama u bota, dock polohy a přehrávače). Přes celý panel se pilulka
  připnutých táhla od okraje k okraji daleko za bubliny i vstupní pole. Sloupec dostává celý
  sloupec pruhů naráz, ne každý pruh zvlášť — nový pruh do něj spadne sám.
- **Nastavení má strop 720 dp na úrovni panelu** (`Panes.contentWidth`, uplatní ho
  `PaneCard`), ne v obrazovkách — sekcí je přes deset a každá má vlastní seznam. Zúží se
  i velký nadpis, takže lícuje s obsahem; scroll je jen nad sloupcem.
- **Profil strop nepotřebuje**: na široké ploše je vždycky panel o výchozí šířce.
- **`ConversationScreen` a registry**: logika sloupce je ve vlastní funkci, změna počet
  registrů v dexu nezvedla. Main ale stál na 274 (z 268 ho zvedly tooltipy v #684), viz
  Gotchas v `CLAUDE.md`.

### Nízké okno (telefon naležato)

Telefon naležato má po odečtení lišt kolem 360 dp výšky (emulátor Pixel 7: 862 × 359 dp)
a panely vedle sebe. Obrazovky stavěné na výšku se do toho nevešly a nerolovaly, takže kus
z nich nebyl vidět vůbec. Prošlo se to na emulátoru naležato (11/2026) a opravilo:

- **Nabídky nerolovaly** — `DropdownMenuPopup` z material3 Expressive (1.12) obsah neroluje,
  na rozdíl od starého `DropdownMenu`. Z menu účtu tak naležato zmizelo **Nastavení** (poslední
  položka) a jinudy se k němu nejde. Každé volání dostane `modifier = Modifier.menuScroll()`
  (`core/ui/ExpressiveMenu.kt`), hlídá to `verifyMenuScroll`. Nabídka nad zprávou si výšku
  počítá sama a rolovala odjakživa.
- **Nastavení má na nízkém okně malou lištu** (`SettingsTopBar`, `isWindowHeightCompact`
  v `core/adaptive/WindowHeight.kt`). V panelech vedle sebe se velký nadpis nesbaluje a bral
  přes 150 dp ze 360.
- **PIN: hlavička vedle klávesnice** (`PinLayout` v `core/ui/PinPad.kt`, zamykací obrazovka
  i zadávání v Nastavení). Dřív z klávesnice zbyla první řada a tečky ležely přes ni, PIN nešel
  nastavit. Vedle sebe jen tam, kde je místo do šířky; v úzkém panelu Nastavení (asi 330 dp)
  zůstává pod sebou se zmenšenými tlačítky a roluje — poslední řada se tam dorolovává.
  Zamykací obrazovka se na snímku ukáže černá (zámek zakazuje snímky), ověřuje se výpisem
  `uiautomator dump`.
- **Prázdný stav se zmenší a roluje** (`IntroEmptyState` pod 400 dp výšky plochy). Prázdné
  Stahování pod horní lištou ukázalo jen tvar, nadpis a text byly pod okrajem.
- **Můj QR kód: kód vlevo, zbytek vpravo.** Na výšku nad kódem stojí profil, takže naležato
  byla vidět jen horní půlka kódu a naskenovat nešel.
- **FAB menu seznamu chatů schovalo horní položku pod hledání** (Nový příběh). Obsah Scaffoldu
  sahá až pod hlavičku, takže menu dostalo celou výšku a nevědělo, že se nevejde. Sloupec
  s FAB má shora `padding` o výšku hlavičky a menu se pak rozroluje samo — `FloatingActionButtonMenu`
  z material3 roluje, jen když mu výška nestačí.
- **Náhledy v tvorbě příběhu (360 dp) a samolepky (240 dp) jsou na nízkém okně menší**; obě
  obrazovky rolovaly i předtím, náhled ale zabral celé okno.
- **V pořádku bez změny:** prohlížeč fotek (s ovládáním je fotka mezi lištami), nahrávání
  kolečka (průměr počítá i z výšky), úprava fotky, nabídka nad zprávou, konverzace.
- **Přihlášení** má dva sloupce od dřív (`docs/poznatky/prihlaseni.md`, „Naležato a na širokém
  okně").
- **Neověřené naživo:** prohlížeč příběhů (testovací účet žádné nevidí; kód drží poměr 9 : 16
  podle výšky), tvorba příběhu (účet bez Premium ji nepustí), videochat (chce běžící hovor ve
  skupině; mřížka roluje), hřiště AI (emulátor nemá sekci AI v zařízení), hovor (vlastní větev).

**Rozhoduje místo, které obrazovka dostane, ne natočení** (`BoxWithConstraints`), s hranicemi
tříd okna M3: nízké okno pod 480 dp, Medium od 600 dp. Vedle sebe jen tam, kde je široko
i do šířky — úzký panel naležato je pořád „širší než vysoký". Celé okno (`isWindowHeightCompact`)
jen tam, kde má rozhodnutí platit pro všechny panely naráz (výška hlavičky Nastavení).

**Jak hledat další:** obrazovka s pevným rozvržením (`Arrangement.SpaceBetween` / `Center` přes
celou výšku) bez rolování, nebo obsah, který na výšku zabere přes ~350 dp. Lišta systému zamyká
natočení: `adb shell settings put system accelerometer_rotation 0` a `wm user-rotation lock 1`;
spouštěč umí jen na výšku a po návratu do něj se natočení vrátí, zamyká se proto znovu.

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
  Vlastní `ViewModelStoreOwner` proto není potřeba.
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

### Pozice scrollu po návratu

Odrolovaný seznam po návratu z podstránky uskočil (Nastavení dolů → O aplikaci → zpět: o 74 dp,
Soukromí → Dvoufázové ověření → zpět: o 16 dp). Insety v tom nebyly.

- **Obrazovka pod jinou obrazovkou není složená** (viz výš u návratu do seznamu chatů), po
  návratu se složí znovu a `rememberScrollState()` obnoví uloženou pozici v PIXELECH.
- **`ScrollState` ji při prvním měření TRVALE ořízne** na `maxValue` (setter `maxValue` srazí
  `value`). Když je obsah v prvním snímku kratší než při odchodu a doroste až potom, pozice
  zůstane ořezaná a seznam ukazuje o chybějící kus míň ze spodku.
- **Kratší první snímek má pokaždé jiný zdroj.** V Nastavení řádek „AI v zařízení": dostupnost
  se četla v obrazovce přes `collectAsStateWithLifecycle(initialValue = false)`, takže v prvním
  snímku chyběl (posun přesně o řádek 72 dp + mezera). V Soukromí něco 16 dp vysokého. Honit
  zdroj obrazovku po obrazovce by nikdy neskončilo.
- **Oprava je obecná: `rememberRestoringScrollState()`** (`core/ui/RestoringScrollState.kt`)
  místo `rememberScrollState()` u svislého obsahu (68 souborů naráz, hlídá `verifyRestoringScroll`).
  Uloženou pozici si pamatuje jako cíl a s každým dorůstáním obsahu ji dorovná
  (`dispatchRawDelta`, ten nenastaví `isScrollInProgress`). Skončí po dosažení, při jakémkoli
  jiném posunu (tah, fling, kolečko, `animateScrollTo` obrazovky, doscrollování na výsledek
  hledání) nebo po 3 s. Neukládá ořezanou hodnotu, dokud dorovnává.
- **Kde to jde, má obrazovka znát obsah hned** — dostupnost AI je teď `StateFlow`
  v `SettingsViewModel`, který návrat přežije a poslední hodnotu má. Obecná oprava je záchranná
  síť, ne důvod číst stav s výchozí hodnotou v obrazovce.
- **`LazyColumn` tohle nemá v téže podobě** (`LazyListState` ukládá index a posun položky), na
  líné seznamy se oprava nevztahuje.
- **Ověřeno na emulátoru Fold (složený)**: Nastavení i Soukromí po návratu na stejném pixelu
  (`uiautomator dump`, `bounds` řádku před a po), tah hned po návratu má přednost a pozice
  pak zůstane. Na telefonu s Gemini Nano ověřené není.

### Routy s účtem

- **Routa s id chatu, zprávy nebo souboru je `AccountRoute` s povinným `slot`** na prvním místě.
  Id platí jen v klientovi jednoho účtu. Shell její obsah skládá v kontejneru účtu
  (`inRouteAccount`), takže obrazovka nic dalšího řešit nemusí; `koinInject` a `koinViewModel` míří
  do správného kontejneru samy.
- **Staví-li obrazovka další routu, bere slot z té svojí** (`route.slot`, `LocalAccount.current.slot`).
  Pomocníci nad zásobníkem (`openTarget`, `openFoundMessage`…) ho dostávají parametrem.
- **Změna tvaru routy, kterou stará data nepřečtou, zvedá `SAVED_ROUTES_VERSION`.** Je to klíč
  kolem `rememberNavBackStack`: uložený stav se s jinou verzí nenajde a začne se od kořene. Bez
  toho by obnova po aktualizaci skončila výjimkou (chybějící povinné pole).

### Sheety jako navigační destinace

Můj QR kód, Záložky a Přehled konceptů jsou routy (`SheetRoute`: `MyQrCodeRoute`,
`BookmarksRoute`, `DraftsRoute`), ne příznak v obrazovce. Kreslí je `SheetSceneStrategy`
(`app/shell/SheetScene.kt`) jako `OverlayScene` — tentýž mechanismus jako `DialogSceneStrategy`
z Navigation3: položka se značkou `sheet()` navrchu zásobníku se kreslí nad scénou, kterou
z položek pod ní spočítá list-detail strategie.

- **Kdy destinace a kdy `remember` v obrazovce.** Destinace je sheet, který je místem sám o sobě:
  otevírá se z víc míst (QR kód ze seznamu i z Nastavení, záložky z menu i z profilu), jeho obsah
  popíše klíč a výsledek nevrací. Kontextová nabídka a kroky akce (nabídka nad chatem, odložení,
  vymazání historie) destinacemi nejsou — viz „Otevřený sheet je stav obrazovky"
  v `seznam-chatu.md`. Destinace má navíc obnovu po zabití procesu a otevření chatu zvenčí ji
  odebere spolu se vším ostatním (`popToRoot`).
- **Sheet je ve zásobníku sekce, ze které se otevřel** (`AppNavigation.openSheet`), a zavírá se
  podle routy, ať stojí kdekoli (`closeSheet`) — QR kód neví, jestli je v Chatech, nebo
  v Nastavení. Přepnutí sekce sheety jinde zavře (`closeSheetsOutside`): ve skrytém zásobníku
  by čekaly a po návratu vyskočily, a pod Nastavením by stály uprostřed kreslených položek, kde
  je strategie (dívá se jen na poslední) nepozná.
- **Pravidla nad zásobníkem sheety přeskakují** (`withoutSheets`): panely, sekce
  Nastavení v dvoupanelu, profil sledující konverzaci, čip sdílení i „je vedle otevřená
  konverzace" se počítají ze scény POD sheetem. Routa sheetu nemá roli panelu, takže by se
  jinak brala jako obrazovka přes celou plochu: čip by zmizel a scéna by spadla na jeden panel.
  Ke kreslení se sheety přidají zpátky navrch.
- **Vede-li sheet jinam, routa se odebere NAPŘED** (koncept, záložka). Pomocníci nad zásobníkem
  (`openTarget`, `addOnce`) se rozhodují podle vrcholu, a ten by byl sheet.
- **Nad sheetem se nic neotevírá — a když přece, sheet zmizí.** Vstupy zvenčí srovnávají
  zásobník (`popToRoot`), jenže odkaz `tg://new`, `tg://new/group` a `tg://new/channel` (`openScreen`
  → `openInChats`) obrazovku jen přidával, takže nad otevřeným přehledem konceptů vzniklo
  `[Chats, DraftsRoute, NewChatRoute]`. Zpět by vrátil koncepty, a hlavně by se v náhledu gesta
  zpět složila předchozí scéna se sheetem: `ModalBottomSheet` je samostatné okno, gesto by
  sebral a z obrazovky by nešlo odejít — tatáž past, jakou řeší „Otevřený sheet je stav
  obrazovky" v `seznam-chatu.md`. `openInChats` proto sheety zavře a shell to jistí obecně:
  sheet uprostřed se nekreslí (`withoutSheets` vynechá i ten) a efekt ho ze zásobníku odklidí
  (`dropBuriedSheets`, s varováním v logu, ať se další taková cesta najde). Že sheet stojí
  navrchu, tedy **vynucuje shell**, ne disciplína volajících; nová cesta, která přidává
  obrazovku, na to myslet nemusí, ale má sheet zavřít sama, ať zmizí s animací a bez varování.
  **Ověřeno na emulátoru** (Android 17): Můj QR kód z menu účtu, pak zvenčí
  `adb shell am start -a android.intent.action.VIEW -d tg://new/group eu.tintera.plume` —
  otevře se Přidat členy, okno sheetu zmizí (`dumpsys window windows`: jediné okno Plume),
  varování pojistky v logu není (zabralo `openInChats`) a gesto zpět od kraje vede na seznam
  chatů bez sheetu. **Tvar odkazu ověřovat v knihovně**, ne z hlavy: `tg://newgroup` TDLib
  nezná a odkaz neudělá nic; tvary najde `strings libtdjsonjava.so | grep 'tg://new'`.
- **Vlastní ViewModel.** Destinace má vlastní úložiště ViewModelů, ten seznamu ani profilu nevidí;
  koncepty a záložky proto mají `DraftsViewModel` a `BookmarksViewModel`. Seznam koncepty jen
  počítá (menu účtu) ze stejné funkce (`draftItems`), ať se počet a přehled nerozejdou.
- **Zpět a zavření tahem řeší sheet sám**: `ModalBottomSheet` je samostatné okno, zpět dostane
  ono a `onDismissRequest` routu odebere. Scéna pod sheetem se nepřekresluje, jen přestane být
  `RESUMED` (NavDisplay drží `RESUMED` jen nejvyšší překryvnou scénu).
- **Odebraný sheet zmizí bez animace**, když ho zavře něco jiného než on sám (otevření chatu
  zvenčí). `OverlayScene.onRemove` by animaci zvládlo, ale potřebovalo by stav sheetu mimo položku.
- `Panes.ownSurface()`: položka sheetu nemá roli panelu a dekorátor karty by jí dal barvy stránky
  s bloky řádků; sheet si je bere z tématu.
- **Na zařízení ověřené jen zčásti**: Můj QR kód z menu účtu se otevře jako destinace a zpět
  ho zavře, odkaz zvenčí ho odklidí (viz bod výš). Zbývá projít: koncepty a záložky (otevření,
  tah, ťuknutí vedle), dvoupanel a QR kód z Nastavení v něm, koncept a záložka vedou do chatu,
  záložky z profilu (i v archivu) skočí na zprávu, zabití procesu s otevřeným sheetem,
  notifikace nad otevřeným sheetem.

### Klávesové zkratky

Desktop, Fold, tablet nebo Chromebook s fyzickou klávesnicí: **Ctrl+K** hledání chatů, **Ctrl+F** hledání
v otevřené konverzaci, **Alt+↓ / Alt+↑** a **Ctrl+Tab / Ctrl+Shift+Tab** další a předchozí chat,
**Ctrl+Shift+S** (macOS ⌃⌘S) sbalí nebo rozbalí seznam chatů vedle konverzace (viz „Tlačítko
sbalení seznamu"), **Ctrl+Shift+I** (macOS ⌥⌘I) otevře nebo zavře panel s profilem, **Esc** zpět (zavře hledání, konverzaci; rozepsanou úpravu nebo odpověď napřed zruší, viz
`konverzace-psani.md`, „Esc, šipka nahoru a fokus pole"). Pravidla jsou čisté funkce (`keyboardShortcut`,
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
  Ve společném seznamu víc účtů obsluhuje zkratky `MergedChatListContent` a sousedy hledá
  podle id S ÚČTEM: totéž id má chat ve dvou účtech a podle samotného id by zkratka mezi nimi
  nepřešla (`docs/navrh-vice-uctu.md`, krok 9).
- **Esc = Zpět je výslovně v `MainActivity`** (`handleEscape`), ne systémová záložní akce. Mapa
  kláves systému sice Esc převede na Zpět, ale jen když klávesu nikdo nezpracuje — a zaostřené
  vstupní pole ji spolkne: z konverzace s kurzorem v poli Esc nedělal nic. Zpět se volá až při
  uvolnění klávesy, stisk se jen spolkne.
- **Desktop je chytá v `onPreviewKeyEvent` okna** (`rememberDesktopShortcutHandler` v jvmMain,
  předává ho `Main.kt`). Dřív na desktopu nefungovala ŽÁDNÁ zkratka: `KeyboardShortcuts`
  spouštěla jen activity a okno obsluhu kláves nemělo. `onPreviewKeyEvent` okna jde před scénou
  (`ComposeSceneMediator.onKeyEvent`: náhled okna → scéna se zaostřeným prvkem → `onKeyEvent`
  okna → Esc jako Zpět), takže chytá i nad zaostřeným polem a bez fokusu.
  - **Na macOS je příkazová klávesa ⌘** (`commandIsMeta`): ⌘K a ⌘F, Ctrl+K a Ctrl+F nic
    (Ctrl+K maže v poli do konce řádku). Přepínání chatů zůstává Ctrl+Tab a ⌥+šipka — ⌘Tab si
    bere systém. Seznam chatů je ⌃⌘S jako „Zobrazit / skrýt boční panel" ve Finderu a Mailu;
    `menuKeys` ho mapuje zvlášť, pravidlem „Ctrl → ⌘" by vyšlo ⌘⇧S.
  - **Esc = Zpět dělá na desktopu Compose sám** (`BackNavigationEventInput`), ale až když
    klávesu nikdo nespotřebuje — tatáž past jako na Androidu.
  - **Na macOS je nabídka „Chaty" v liště** (`DesktopMenuBar`): Hledat v chatech ⌘K, Hledat
    v konverzaci ⌘F, Další / Předchozí chat ⌥↓ / ⌥↑ — na Macu se zkratky hledají v nabídkách.
    Popisky i klávesy jdou ze `SHORTCUT_BINDINGS` (`menuKeys` mění Ctrl hledání na ⌘, Ctrl+Tab
    nechá) a `KeyboardShortcutsTest` hlídá, že nabídka slibuje klávesy, které pravidlo opravdu
    rozpozná. Na Windows a Linuxu nabídka není — `MenuBar` by tam přidal pruh dovnitř okna.
  - **Akce se nespustí dvakrát, i když klávesu má okno i nabídka.** Okno ji chytá první
    (`onPreviewKeyEvent`) a spotřebovaná klávesa už do nabídky nedojde; nabídka ji dostane, jen
    když okno nevyřídí (zkratku nikdo neposlouchá — přihlašovací obrazovka) a pak nic nespustí
    ani ona. **Pozor na falešnou stopu**: na přihlašovací obrazovce sonda ukázala volání z okna
    i z nabídky, jenže to bylo právě tím, že okno klávesu nespotřebovalo. Spolknout ji v okně
    „aby to nespustila nabídka" klávesu zabije úplně (vyzkoušeno: ⌘K pak nedělal nic).
  - **Ověřeno na macOS** (ladicí běh, sonda v logu): v liště „Plume" a „Chaty" se čtyřmi
    položkami; ⌘K nad seznamem chatů otevřel hledání jednou (jen z okna), položka nabídky
    taky jednou. Ostatní zkratky z nabídky jen testy — ⌥↓ by otevřel chat skutečného člověka.
  - **Ověřeno na Windows 11** (ladicí běh, uživatel): Ctrl+K, Ctrl+F, Alt+šipky, Ctrl+Tab
    a Esc i s kurzorem v poli zprávy. macOS (⌘) jen testy, za běhu neověřené.
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

