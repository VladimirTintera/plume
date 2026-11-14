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
    by seznamu vzalo 8 dp výšky. Po bumpu material3 hodnotu porovnat.
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
  nedotkl jinde. Avatar proto má `RootTooltipBox` s vlastním stavem a podržení bublinu po
  100 ms zavře (pořadí obou obsluh dlouhého stisku není dané).
- Ověřeno na emulátoru Fold (česky): FAB s nabídkou a „Nová zpráva", podržení → „Vybrat",
  výběr dvou chatů s přetočeným počtem a lištou, archivace se snackbarem a „Vrátit" (chat se
  vrátil), zpět ukončí výběr, výběr přežije čtyři cykly `adb emu fold` / `unfold` bez pádu.
  **Desktop jen přeložený**, změna šířky okna se sbaleným sloupcem a snackbarem neověřená.
- **Bublina se jménem u avataru přes `WithTooltip`, ne holý `TooltipBox`.** Najetí na avatar
  a hned tah táhlem shodilo desktop na `RootNodeOwner is already disposed`: `BoxWithConstraints`
  přepnul sloupec na plný seznam uprostřed měření, kotva viditelné bubliny zmizela a skiko pak
  změřilo už zrušenou vrstvu popupu. Bublina se proto kreslí z kořene kompozice a ruší ji až
  běžná rekompozice — mechanismus a pravidlo pro celý projekt jsou v `docs/poznatky/desktop.md`,
  „Bubliny (tooltip) z kořene kompozice".
- **Šipka zpět v konverzaci vedle seznamu konverzaci ZAVŘE** (`closeConversation`, testy
  `CloseConversationTest`): odebere celou větev nad seznamem — konverzace otevřené zmínkami jedna
  z druhé i profil — a zůstane seznam s placeholderem. Procházet zpátky řetězem zmínek šipkou
  nedávalo smysl, když je seznam vedle. Systémové zpět a Esc jdou dál po jedné položce, na
  telefonu je šipka obyčejné zpět. Téma nad seznamem témat se vrací do něj (rodič, ne historie).
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
- **Přihlášení** má dva sloupce od dřív (`docs/poznatky/prihlaseni.md`, „Naležato a na širokém
  okně").

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

### Klávesové zkratky

Desktop, Fold, tablet nebo Chromebook s fyzickou klávesnicí: **Ctrl+K** hledání chatů, **Ctrl+F** hledání
v otevřené konverzaci, **Alt+↓ / Alt+↑** a **Ctrl+Tab / Ctrl+Shift+Tab** další a předchozí chat,
**Esc** zpět (zavře hledání, konverzaci; rozepsanou úpravu nebo odpověď napřed zruší, viz
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
    bere systém.
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

