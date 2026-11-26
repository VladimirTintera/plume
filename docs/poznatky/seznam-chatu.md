# Seznam chatů

Poznatky k jedné oblasti aplikace. Obecná pravidla projektu a rejstřík ostatních souborů jsou
v [CLAUDE.md](../../CLAUDE.md); poučení z opravy v téhle oblasti patří sem.

## Skladba obrazovky

Obrazovka je rozdělená do souborů v `features/chats`, nová logika patří do toho svého:

- `ChatListScreen.kt` — obrazovka (hlavička, lišta složek, sheety), `ChatActionsHost`
  (nabídka nad chatem, nahlášení, snackbary) a `ChatListContent` (seznam ve skupinách,
  dotahování, prázdný stav, klávesové zkratky).
- `ChatRow.kt` — řádek chatu a tah do strany (`SwipeableChat`, `ChatRow`, odznaky, odložení,
  palety barev chatů).
- `ChatListHeaderCards.kt` — karty nad seznamem (`ChatListCards` a `chatListHeaderCards`);
  jejich počet (`count`) je `headerItemCount` seznamu, takže nová karta se přidává jen tam.
- `AccountMenu.kt` — fotka účtu vedle pilulky a menu pod ní.
- `ChatListFabMenu.kt` — FAB menu Vytvořit, závoj pod ním a FAB ve slotu lišty složek.

Dřív to byl jeden soubor o 2 457 řádcích (funkce `ChatListScreen` sama ~680 řádků). Rozdělilo se
dřív, než narazí na limit registrů jako kdysi `ConversationScreen` (viz CLAUDE.md, „Gotchas").

## Seznam chatů

Položky nejsou samostatné karty, ale **skupiny se společným zaoblením** — vzor, kterým M3
Expressive nahrazuje karty per položku. Připnuté mají vlastní skupinu vždycky, nad nimi
sponzorovaný chat a vynořené odložené chaty. **Nepřečtené vlastní skupinu nemají** (viz níž,
„Blok nepřečtených zmizel").

- **Zaoblení nese krajní položka skupiny**: velké nahoře u první, velké dole u poslední, malé
  (4 dp) mezi nimi. S rozestupem 2 dp uvnitř skupiny a 14 dp mezi skupinami z toho vznikne blok.
  Tvary a rozestupy jsou v `core/ui/GroupedList.kt` — týmž jazykem mluví i profil chatu
  a výběr cíle přeposlání, a tři kopie konstant by se rozešly.
- **Vybraná položka je zaoblená celá**, jako skupina sama (dvoupanel, v seznamu chatů i sekcí
  nastavení). Vyčlení se tím z bloku — tvarem, nejen barvou. Sousedé si svá malá nároží nechávají.
- **Zaoblení se animuje.** Skupiny se přeskládávají za běhu — chat se připne nebo odepne,
  odložený se vynoří — a tvary jeho sousedů se tím mění. Skoková změna by při současném
  `animateItem()` působila jako chyba vykreslení.
- **Prázdné skupiny se zahazují**, jinak by po nich zbyla mezera. Bez připnutých je z celého
  seznamu zase jeden souvislý blok.
- **Pořadí uvnitř skupiny zůstává od TDLib.** Je to pořadí, na které je uživatel zvyklý
  z ostatních klientů; přeskládávat ho by znamenalo rozhodovat za něj.
- **Blok nepřečtených zmizel** (11/2026). Byla to volba „Nepřečtené ve vlastním bloku"
  v Nastavení → Chaty (`separateUnreadChats`, výchozí vypnutá): nepřečtené chaty se přesunuly
  nad ostatní. Odstranila se jako nadstandard proti oficiálním klientům:
  - přečtení chat přesunulo dolů, takže při návratu z konverzace nezůstal tam, kde na něj
    uživatel ťukl, a seznam pod prstem uskočil,
  - ve společném seznamu přes víc účtů (`docs/navrh-vice-uctu.md`) by byl další blok, který
    se musí slučovat.
  
  Uložená hodnota `separate_unread_chats` v DataStore zůstává nepoužitá; migrace ji mazat
  nemusí.
- **Pravidlo je čistá funkce `chatGroups`** (`ChatGroups.kt`, testy `ChatGroupsTest`), ne
  `private fun` v obrazovce. Hraniční kombinace (samé připnuté, samé odložené, prázdný
  seznam) se uvnitř `@Composable` neověří a v seznamu se projeví mezerou po prázdné skupině.
- **Mezera mezi skupinami se přidává u první položky skupiny**, ne ve `verticalArrangement` —
  `LazyColumn` o sousedech položky nic neví. Stejný postup jako u rozestupů zpráv v konverzaci.
- **Nad prvním chatem je neviditelná kotva** (`TOP_ANCHOR_KEY`, 1 dp). `LazyColumn` drží pozici
  podle klíče první viditelné položky: bez kotvy to byl první chat, a když nad něj vyskočil jiný
  (nová zpráva), seznam šel za tím původním a nový chat skončil nad horní hranou — ve vršku
  seznamu trčel jen useknutý kus karty. Kotva je první viditelná položka, takže seznam u vršku
  u vršku zůstane. **Nulovou výšku mít nesmí**, to by ji seznam za viditelnou nepovažoval; její
  výška i rozestup za ní se odečítají z horního odsazení, aby první chat neposkočil.

### Složky místo filtrů a FAB

Seznam chatů zužují JEN složky Telegramu v plovoucí liště dole vedle FAB (viz „Složky chatů").
Vpravo dole je FAB menu „Vytvořit" (`NewChatFabMenu`): přepínací FAB s tužkou a nad ním Nový kanál, Nová
skupina a Nová zpráva.

- **Dřív tu byl plovoucí toolbar s vlastními filtry** (Vše, Nepřečtené, Lidé, Skupiny, Kanály;
  `ChatFilter`), lokálními a kombinovatelnými se složkou. Dělaly ale totéž co doporučené složky
  Telegramu („Nepřečtené", „Osobní"…) — a s nimi přidanými stály v aplikaci dvě řady téhož nad
  sebou. Uživatel to vyhodnotil jako duplicitu a zrušily se. Co se tím ztratilo: kombinace
  „složka × filtr" (nepřečtené uvnitř Práce) a filtry pro toho, kdo složky nemá. Náhrada je
  „+" na konci lišty složek, který doporučené složky nabídne na jedno ťuknutí.
- **„+" otevře sheet** (`FolderSuggestionsSheet`, stav `ChatsViewModel.folderSuggestions`):
  doporučené složky (`getRecommendedChatFolders`, čtou se při každém otevření), pod nimi „Nová
  složka" a „Spravovat složky" do Nastavení → Obecné → Složky chatů
  (`AppNavigation.openChatFolders`, zpět po krocích jako u proxy). Přidaná doporučená složka
  se rovnou VYBERE — kdo si přidá „Nepřečtené", chce je vidět.
- **Sheet říká, že se složka synchronizuje** s ostatními klienty. U filtru, který dřív žil jen
  v Plume, by to nikdo nečekal.
- **Bez složek se lišta NEKRESLÍ** (`folderTabs` vrátí prázdný seznam). Dřív v něm stálo
  samotné „Všechny chaty" a „+", aby k doporučeným složkám vedla cesta z hlavního seznamu —
  jenže jediné tlačítko, které nejde odvybrat, není volba, jen řádek místa navíc nad chaty
  u každého, kdo složky nepoužívá. Uživatel ho chtěl pryč. Doporučené složky dál nabízí
  Nastavení → Obecné → Složky chatů; po přidání první složky se lišta objeví i s „+".
  Ověřeno na emulátoru (česky): účet bez složek bez řádku, přidaná doporučená „Unread" v Nastavení
  řádek ukáže, a když se smaže, zatímco je vybraná, řádek zmizí a seznam je zase celý.
- **Prázdná složka má `IntroEmptyState` s ikonou složky** („Nic tu není") — chaty jsou, jen
  ne tyhle (typicky „Nepřečtené" se vším přečteným). Prázdný účet má dál holý text.
- **FAB a snackbar nejsou ve slotech Scaffoldu** a stojí nad klávesnicí (`imePadding` za
  `consumeWindowInsets` spodního paddingu Scaffoldu): Scaffold dává do paddingu jen systémové
  lišty, takže by je vysunutá klávesnice zakryla. Seznam má dole `FAB_CLEARANCE`.
- **FAB menu M3 Expressive, ne rozšířený FAB** (`FloatingActionButtonMenu`
  + `ToggleFloatingActionButton`). Dřív byl FAB „Nová zpráva", který se při scrollu sbalil na
  ikonu; Nová skupina a Nový kanál byly jen řádky na obrazovce Nové zprávy (zůstaly tam). Popisky
  nesou položky, přepínač je jen ikona (tužka → ✕, zaoblený čtverec → kruh) — tak to M3 u FAB
  menu chce, takže sbalování při scrollu odpadlo. Nová zpráva je poslední, u palce.
- **Pod otevřeným menu je závoj** (`FabMenuScrim`, `surface` s průhledností): ťuknutí vedle
  menu zavře a nepropadne do seznamu (otevřelo by chat). Kreslí se DVAKRÁT — přes seznam a přes
  hlavičku, protože Scaffold kreslí `topBar` nad obsahem a ťuknutí na pilulku by jinak otevřelo
  hledání s menu otevřeným pod ním. Zpět menu zavře (`NavigationBackHandler`), teprve další zpět
  odchází z aplikace. Položka napřed menu zavře, pak naviguje — po návratu má být zavřené.
- **Popisek přepínače se mění se stavem** („Vytvořit" / „Zavřít") a čtečka ho čte PŘED
  položkami (`traversalIndex = -1`), jako v ukázce M3; z poslední položky menu zavře vlastní akce.
- **Vybraná složka se v liště vždycky ukáže** (`bringIntoViewRequester` u vybrané položky):
  přidaná doporučená složka stojí na konci lišty a vybrala by se za okrajem.
- **Server doporučené složky vracet nemusí** — účtu, který už má „Unread" i „Personal", nevrátil
  žádnou. Sheet se pak ověřuje sondou (podstrčená doporučená složka v `loadFolderSuggestions`;
  necommituje se), založení jde skutečným `createChatFolder`.
- **Ověřeno na emulátoru** (anglicky): FAB menu — otevření, zavření ťuknutím do seznamu i na
  hlavičku a zpět, všechny tři položky vedou na svou obrazovku a zpět se vrací se zavřeným menu.
- **Ověřeno na emulátoru** (česky, světlý i tmavý motiv): FAB se sbalením, řádek s „+", sheet
  bez doporučených i se sondou, přidání složky (vybraná a dojetá v řádku), prázdná složka
  s ikonou, „Nová složka" do editoru a „Spravovat složky" do seznamu složek, zpět po krocích.
  Testovací složka se pak smazala. **iOS jen přeložené.**

### Složky chatů

Složky Telegramu (`ChatListFolder`) jsou plovoucí lišta dole s FAB na konci (`ChatFolderToolbar`,
`HorizontalFloatingToolbar` ve variantě s FAB; pravidla `folderTabs` a `selectedFolderId` v `ChatFolderTabs.kt`,
testy `ChatFolderTabsTest`). Bez složek se lišta nekreslí vůbec (viz „Složky místo filtrů a FAB").

- **Dřív to byl řádek přepínacích tlačítek pod pilulkou** (connected button group s fajfkou
  a „+" v obrysovém kruhu). Uživatel ho měl za ošklivý: vybraná složka byla nejvýraznější věc
  na obrazovce, nevybraná vypadala jako holý text a „+" byl vyšší než zbytek řádku. Lišta dole
  je u palce a nahoře zbyla jen pilulka.
- **Popisky, když se vejdou; jinak ikony a popisek jen u vybrané.** Rozhoduje ZMĚŘENÁ šířka
  (složené jsou dvě řady, se všemi popisky a kompaktní, a postaví se ta, která se vejde), ne počet složek —
  tři dlouhé názvy se na telefon nevejdou, pět krátkých na desktop ano. Co se nevejde ani jako
  ikony, posouvá se do strany. Ikona je ta od TDLib (zvolená, jinak výchozí podle obsahu),
  „Všechny chaty" mají ikonu „All" a v liště popisek „Vše" (`chat_folders_all_short`): celý
  název zabral v kompaktní liště skoro polovinu místa a „+" se kvůli němu posunul za okraj.
  Čtečka obrazovky a bublina dostávají celý název, Nastavení ho mají taky celý. Bez popisku nese název bublina (podržení / najetí myší)
  a čtečka obrazovky, počet nepřečtených je v odznaku na ikoně.
- **Bublina se u položky s popiskem VYPÍNÁ, neodpojuje** (`TooltipBox(enableUserInput =
  false)`). S obalem přidávaným jen bez popisku se položka při změně výběru skládala znovu
  a popisek naskočil bez animace.
- **Položky nejsou `IconButton`, ani „+".** `IconButton` hlásí zarovnávací čáry minimální
  dotykové plochy (`MinimumInteractiveLeftAlignmentLine`) a `HorizontalFloatingToolbar` podle
  nich vyvažuje svislé odsazení (`minimumInteractiveBalancedPadding`). V posouvaném řádku se
  čára „+" počítá od jeho polohy v řádku — stovky pixelů — a lišta narostla přes půl obrazovky.
  Položky jsou proto vlastní 48dp plochy (`selectable` s rolí Tab, „+" `clickable`).
- **Lišta M3 s FAB, ne lišta vedle FAB.** Dřív stála samotná lišta (bez FAB) v `Box` vedle FAB
  menu a schovávala se vlastním `AnimatedVisibility` (zúžení a prolnutí) — chyběla tak animace,
  kterou má M3 Expressive: sbalená lišta zajede DO FAB a FAB se nafoukne z 56 na 80 dp (střední
  FAB), rozbalená vyjede a FAB splaskne. Tu dělá jen `HorizontalFloatingToolbar(expanded,
  floatingActionButton)`, takže lišta a FAB jsou teď jedna komponenta. Uživatel si toho všiml
  podle chybějící animace při scrollu.
- **Celá lišta je `button` FAB menu, přepínací FAB je v jejím slotu** (`NewChatFabMenu(toolbar
  = …)`, `ToolbarSlotFab`). Položky menu tak vyjedou nad lištou a FAB je pořád jeden. Bez složek
  je to holé FAB menu jako dřív.
  - **Velikost FAB určuje lišta** (pevné omezení 56–80 dp), ale `ToggleFloatingActionButton` si
    velikost kreslí sám (`containerSize(progress)`, vnitřní `layout`) — výchozí přepínač by
    zůstal 56 dp a nenafoukl se. `ToolbarSlotFab` proto čte velikost slotu z omezení při měření
    a dává přepínači `containerSize = lerp(slot, 56 dp, progress)`: zavřený vyplní slot, otevřený
    se morfuje do zavíracího kruhu. Obyčejná proměnná, ne stav — slot se mění přeměřením
    a přepínač velikost čte v měření, takže změna dojde i tak.
  - **Při otevřeném menu se lišta sbalí** (`expanded = false`): obsah zajede do FAB a přepínač se
    z nafouknutého slotu morfuje do kruhu „Zavřít" v jeho horním rohu (zarovnání přepínače
    `TopEnd`, výchozí M3, stejně jako u středního FAB menu).
  - **Lišta s FAB je vysoká 80 dp** (`defaultMinSize` na velikost sbaleného FAB uvnitř M3,
    `HorizontalFloatingToolbarWithFabLayout`), aby při nafouknutí neposkočila; 56dp FAB v ní
    M3 svisle centruje, takže jeho spodek je 28 dp nad okrajem, ne 16 dp jako u holého FAB menu.
    **Holý FAB (účet bez složek, archiv) proto stojí ve stejném 80dp pásu** (`NewChatFabMenu`)
    a seznam má jednu rezervu (`FAB_CLEARANCE`). Dřív byl holý FAB o 12 dp níž a při přepnutí
    na účet se složkami (i při návratu z archivu) poskočil. Ověřeno na Pixelu 5 (bez složek)
    a Pixelu 7 Pro (se složkami): spodek FAB v obou 28 dp nad navigační lištou, zprava 16 dp.
  - S čtečkou obrazovky se lišta nesbaluje vůbec (M3 to dělá sama), ani při otevřeném menu.
- **Ikona FAB po rozložení Foldu**: přepínač kreslil po rozložení jen plochu bez tužky
  a ikona se vrátila až po otevření a zavření menu. Za to může `animateIcon` z M3: ikonu
  nahraje do vrstvy v `drawWithCache`, kterou Compose při přesunu seznamu do jiné scény
  uvolní a mezipaměť nechá platnou (mechanismus v „Obsah přesouvaný mezi scénami"
  v `navigace.md`). Scroll ikonu nevrátí: velikost mění FAB, ne ikona (pořád 24 dp), takže
  se mezipaměť ikony nezneplatní. Otevření menu ano, protože se mění barva čtená v bloku.
  Proto má ikona vlastní `toggleFabIcon` (velikost v `layout`, barva přes `colorFilter`
  v `graphicsLayer`) se stejnými výchozími hodnotami M3. Ověřeno na emulátoru Fold (česky,
  světlý motiv): bez opravy ikona chyběla hned po prvním `adb emu unfold`, s opravou zůstala
  ve čtyřech rozloženích a třech složeních po sobě a ✕ v otevřeném menu má dál barvu
  `onPrimary`.
- **`SubcomposeLayout` v obsahu lišty s FAB SPADNE.** Lišta se ptá obsahu na intrinsickou šířku
  (`maxIntrinsicWidth`, z ní počítá, kam zajede) a `SubcomposeLayout` intrinsické míry neumí.
  Proto se o popiscích nerozhoduje měřením nanečisto v subkompozici, ale `FolderItems` složí
  obě řady (s popisky i kompaktní) do `Layout` s vlastní `MultiContentMeasurePolicy`: změří
  řadu s popisky, a když se nevejde, postaví kompaktní. Nepostavená řada nekreslí ani nebere
  dotyk; čtečce, fokusu a dojetí na vybranou složku se to říká stavem (`placed`), který měření
  zapíše.
  - **Řádek lišty se posouvá do strany (`horizontalScroll` uvnitř M3), takže obsah horní mez
    šířky nedostane.** Kolik místa zbývá vedle FAB, se měří `BoxWithConstraints` nad lištou
    (minus FAB 56 dp, mezera 8 dp a vnitřní odsazení lišty) a intrinsická šířka obsahu se na ni
    ořízne — jinak by lišta i s FAB přetekla za okraj a FAB by zmizel z obrazovky. Na tu šířku
    se položky i ořezávají (`widthIn`) a posouvají se VLASTNÍM `horizontalScroll`, ne tím
    v M3 — viz kolečko níž.
- **Okraj, kde lišta pokračuje, vybledne** (`horizontalFadingEdges`, `core/ui/FadingEdges.kt`).
  Naležato se lišta nevejde a „+" na konci byl useknutý přesně uprostřed, četl se jako „−".
  Vyblednutí říká, že řada pokračuje; zbytek „+" je ale vidět dál jako slabá čárka.
- **Svislé kolečko myši nad lištou ji posouvá do strany** (`wheelScrollsHorizontally`, sdílený
  v `core/ui/WheelScroll.kt`, viz „Kolečko u vodorovných řad" v `desktop.md`). Na
  desktopu je seznam chatů úzký (panel 360 dp, na složky vedle FAB zbývá ~250 dp), takže
  složky za okraj přetékají běžně — a myší se k nim nedalo dostat:
  - **Compose u vodorovného posunu svislé kolečko ZAHODÍ** (`toSingleAxisDeltaFromAngle` vrátí
    pro převážně svislý pohyb 0). Vodorovně posouvá jen Shift + kolečko (desktop ho převede na
    vodorovný pohyb, `ComposeSceneMediator`), kolečko s náklonem a touchpad do strany.
  - **Tah myší posun nebere vůbec** (`CanDragCalculation` u `scrollable` vynechává
    `PointerType.Mouse`), jen dotyk.
  - **Posun řádku lišty je uvnitř M3 a zvenku na něj nejde sáhnout**, proto má obsah lišty
    vlastní `horizontalScroll` a řádek M3 už nikdy nepřeteče (posouvá se jen při sbalování).
  - Převádí se jen převážně svislý pohyb a jen když je kam posouvat; událost se pak spotřebuje,
    jinak propadne dál. `scrollDelta` je v zubech kolečka (desktop i Android), krok je 64 dp
    na zub a posun dojede animací k cíli, který se při rychlém točení sčítá.
  - Totéž platí na Androidu s myší (Chromebook, DeX). Ověřeno na emulátoru
    `adb shell input mouse scroll X Y --axis VSCROLL,-1` (záporné = dolů = doprava): zub dolů
    dojede na konec s „+", další zuby lištu nepřetočí, nahoru zpět na začátek, kolečko nad
    seznamem dál posouvá seznam a lištu sbalí. Na desktopu (Windows 11, ladicí běh) kolečko
    skutečnou myší vyzkoušel a potvrdil uživatel. macOS a Linux neověřené.
- **Při scrollu dolů se lišta sbalí do FAB, při scrollu nahoru (a na vršku) vyjede** — jako
  pilulka nahoře, aby nezakrývala čtené chaty. Směr se dál bere z polohy seznamu
  (`snapshotFlow`), ne z `floatingToolbarVerticalNestedScroll`: ten slyší jen scroll prstem,
  ne programový posun, a vršek seznamu nepozná.
- **Lišta s FAB ověřená na emulátoru** (15. 10. 2026, anglicky, světlý motiv, účet se šesti
  složkami, tedy kompaktní režim) sérií snímků se zpomalenými animacemi
  (`animator_duration_scale 10`): sbalení při scrollu dolů (FAB 80 dp), vyjetí s překmitem
  pružiny a splasknutí FAB na 56 dp, otevření FAB menu (lišta zajede, FAB se morfne do kruhu,
  položky nad ním) a zavření, výběr složky s popiskem, tah lištou k „+", ve výpisu
  `uiautomator` každá položka jednou. **Neověřené:** účet bez složek (holé FAB menu), režim se
  všemi popisky, tmavý motiv, TalkBack, desktop (jen přeložený) a iOS.

- **Nabídka nad záložkou nese i úpravu, správu a smazání** (`folderMenuGroups`, akce v jedné
  `FolderTabActions` na ViewModel — jinak by každá protékala čtyřmi úrovněmi lišty). Vlastní
  složka: přečíst vše; Upravit složku (editor v Nastavení, `AppNavigation.openChatFolder`)
  a Spravovat složky (pořadí, doporučené, sdílení); ve vlastní skupině Smazat složku s potvrzením
  (`ChatsViewModel.deleteFolder`, vybraná smazaná složka spadne na celý seznam sama).
  „Všechny chaty": přečíst vše a Spravovat složky.
  - **Sdílenou složku z nabídky smazat nejde** (`FolderTab.shareable`): s ní se rozhoduje
    i o odchodu z jejích chatů (`chatsToLeave`) a ten dialog má jen Správa složek.
  - **„Ztlumit vše" vědomě není**: TDLib ztlumení složky nemá, šlo by jen chat po chatu přes
    načtené chaty — a ty, které se dotáhnou později, by zůstaly neztlumené.
- **Prázdná vlastní složka má tlačítko „Přidat chaty"** (tónové, v `IntroEmptyState`), které
  otevře editor složky — ten začíná výběrem chatů. Bez něj byla prázdná složka slepá ulička.
  „Nepřečtené" se vším přečteným ho má taky: přidané chaty se pak ukážou i přečtené.
- **Podržení složky otevře nabídku „Označit vše jako přečtené"** (`readChatList`, `ChatRepository.readAll`,
  `ChatsViewModel.readFolder`). U „Vše" to vezme hlavní seznam. Odznaky se přepočítají samy
  updaty, hláška jde jen při selhání. Potvrzení není: nic se nemaže a oficiální klienti se taky
  neptají.
  - **Položka nese název složky** („Označit vše v „Práce“ jako přečtené"). Ikona bez popisku
    dřív na podržení ukazovala bublinu s názvem; teď by se pustila zároveň s nabídkou, a tak
    u složek bublina není a název je v nabídce. Čtečka obrazovky dostává název i akci podržení
    (`clearAndSetSemantics` s `onLongClick`).
  - **Odezvu na podržení dělá `combinedClickable` sám** (viz „Haptika" v CLAUDE.md).
  - Ověřeno na emulátoru (14. 10. 2026, účet …527): složka „Skupiny" (druh Skupiny) s odznakem 1
    po dvou zprávách bota; po „Označit vše…" skupina i odznak přečtené, chat Telegramu mimo
    složku zůstal nepřečtený. Testovací složka se pak smazala. Na „Vše" se to nezkoušelo —
    přečetlo by to i chaty skutečných lidí a poslalo jim potvrzení o přečtení.
  - **Archiv lištu složek nemá**, takže „Označit archiv jako přečtený" je ikona v jeho liště
    vedle nastavení (`ChatsViewModel.readArchive`). Jen když je v archivu něco nepřečteného
    (`archiveUnread.chats`), jinak by slibovala akci, která nic neudělá. Ověřeno: ztlumená
    a archivovaná testovací skupina se dvěma zprávami bota, po ťuknutí přečtená a ikona zmizela.
    Neztlumený chat by se novou zprávou z archivu vrátil sám — proto napřed ztlumit.
- **Složka je další `ChatFolder` (`Custom(id)`), ne nový druh seznamu.** Repository drží pozice
  chatu ve VŠECH seznamech, které TDLib hlásí (`positions`), takže seznam složky se skládá
  stejným `publish` jako hlavní a archiv. Seznamy se ale publikují jen pro složky, o které si
  někdo řekl (`lists` roste s `chats(folder)`) — skládat seznam každé složky při každém updatu
  by byla práce pro nic.
- **Složka se načítá až při výběru** (`loadMore(Custom)`), jako archiv. Do té doby je `Loading`;
  pozice chatů ve složce TDLib pošle teprve po `loadChats` nad ní.
- **Archivovaný chat může být ve složce** (`excludeArchived = false`), takže archivace se už
  nerozhoduje podle složky, ze které položka přišla, ale podle `ChatSummary.isArchived`
  (pozice v archivu). `folder` zůstává jen pro připínání — připnutí patří složce.
- **Uložené zprávy ve složce JSOU vidět i nepřipnuté** (`hiddenSavedMessagesChatId`). Do složky
  je uživatel dal sám; v hlavním seznamu zůstávají schované jako dřív.
- **Vybraná složka žije jako filtr**: `SavedStateHandle`, ne nastavení. Návrat z konverzace
  i zabití procesu ji zachovají, nové spuštění začne na všech chatech. Smazaná složka (třeba
  na jiném zařízení) spadne na celý seznam, ale výběr se NEPŘEPISUJE — po startu přichází
  `UpdateChatFolders` až po prvním složení a uložená volba by se jinak ztratila.
- **Jen ťuknutím, ne tahem do strany** (žádný pager mezi složkami). Vodorovný tah po seznamu
  patří archivaci.
- **„Všechny chaty" stojí, kam ho uživatel v Telegramu přesunul** (`mainChatListPosition`,
  bez Premium vždycky první); pozice za koncem (složka mezitím zmizela) jde na konec.
- **Číslo u složky je počet nepřečtených NEZTLUMENÝCH chatů** (`UpdateUnreadChatCount` pro
  `ChatListFolder`), jako odznak archivu. Složku plnou ztlumených kanálů by jinak číslo
  zdobilo napořád. Chodí jen se zapnutou databází zpráv.
- **Název složky může nést vlastní emoji** (`ChatFolderName.text` je `FormattedText`) — kreslí
  se jako v náhledu seznamu chatů (`appendWithCustomEmoji`).
- **Ověřeno na emulátoru** dvěma složkami založenými sondou (`createChatFolder`; jedna s vybranými
  chaty včetně Uložených zpráv, druhá „všichni boti"): řádek, přepnutí, číslo u složky, obnova
  po `am kill`, odjetí s pilulkou. **iOS jen přeložené.**
- **Lišta ověřená na emulátoru** (česky, světlý i tmavý motiv) se třemi podstrčenými složkami
  (sonda ve `folderTabs`, necommituje se — účet na emulátoru složky nemá): kompaktní režim
  s popiskem u vybrané, přepnutí s přejetím popisku, odznak, „+" na konci posunu, schování
  při otevřeném FAB menu a při scrollu dolů, návrat nahoru. Režim se všemi popisky
  (široké okno) a desktop jen přeložené; **iOS jen přeložené.**

#### Správa složek

Nastavení → Obecné → Složky chatů (`ChatFoldersScreen`, `ChatFolderEditScreen`, hranice
`ChatFolderRepository` / `TdlChatFolderRepository`, pravidla konceptu `ChatFolderDraft`, testy
`ChatFolderDraftTest`) a „Přidat do složky" v nabídce nad chatem (`folderChoices`).

- **Seznam a pořadí složek jsou živé z `ChatRepository.chatFolders`**, správa jen zapisuje.
  Po založení, úpravě i smazání přijde `UpdateChatFolders` sám; nic se nepřepisuje lokálně.
- **Složka se zapisuje CELÁ** (`editChatFolder` bere celý `ChatFolder`), takže co aplikace
  nenabízí — ikona, barva, sdílení, připnuté chaty, vlastní emoji v názvu — se přenáší
  z načtené složky (`ChatFolderDraft`). Vlastní emoji patří textu, ve kterém přišly; změněný
  název je zahodí (`withTitle`), jinak by ukazovaly do jiných znaků.
- **Prázdnou složku server odmítne**: musí mít aspoň jeden vybraný chat nebo druh chatů,
  a název 1–12 znaků. Hlídá to `problem()` a Uložit ukáže důvod hned, ne chybu od serveru.
- **Vybraný chat nemůže být zároveň vynechaný** — `withChosen` / `withExcluded` ho z druhého
  seznamu vyřadí; server by jinak celou úpravu odmítl.
- **Odebrat chat ze složky nestačí z vybraných.** Složka „všichni boti" drží bota DRUHEM, takže
  `removeChat` ho navíc vynechá (`withoutChat(coveredByKind)`); jinak by zaškrtávátko nad chatem
  zhaslo a chat ve složce zůstal. Přidání (`addChatToList(ChatListFolder)`) vynechání zruší
  samo — ověřeno: Notifikátor ze „Boti" ven a zpátky.
- **„Přidat do složky" nabízí složky, ve kterých chat je, a ty, kam jde přidat**
  (`getChatListsToAddChat`), zaškrtávátkem a přímo v nabídce (rozbalení jako doby ztlumení).
  Členství se čte až při otevření nabídky, přepnutí se ukáže hned a při chybě vrátí.
- **Pořadí se mění tažením za úchyt** (`DragReorder`) a server dostane nové pořadí až po
  PUŠTĚNÍ (`snapshotFlow { draggedKey }` → `reorderChatFolders`), ne po každé výměně. „Všechny
  chaty" úchyt nemají — přesunout je jde jen s Premium.
- **Doporučené složky** (`getRecommendedChatFolders`) se čtou znovu po každé změně složek;
  přidaná doporučená z nabídky zmizí. Server je nevrací vždycky: při prvním otevření na emulátoru
  přišel prázdný seznam bez chyby, po smazání jiné složky „Nepřečtené" a „Osobní".
- **Výběr chatů do složky je `ChatPicker` přes celou obrazovku nad editorem**, ne routa: výběr
  patří rozdělanému konceptu, který by navigace musela přenášet. „Hotovo (0)" jde potvrdit
  i prázdné (`confirmWhenEmpty`) — tak se vybrané chaty odebírají všechny naráz.
- **Ikona složky je jméno od TDLib** (`ChatFolderIcon.name`: „Bots", „Cat", „Work"… — pevný seznam
  z dokumentace) a kreslí se Material Symbols (`CHAT_FOLDER_ICONS`, `folderIcon`), ne grafikou
  Telegramu. Neznámé jméno z novější verze je obecná složka a při úpravě se přenese dál.
- **Bez vybrané ikony ji volí Telegram podle obsahu** (`getChatFolderDefaultIconName`, lokální)
  a editor ukazuje tu, kterou by zvolil, přepočítanou s každou změnou konceptu: zapnutí „Boti" ji
  samo změní na robota. **S vybranými chaty je výchozí „Custom"**, tedy obecná složka — i u
  složky „všichni boti" s jedním chatem navíc. Není to chyba, tak to TDLib počítá.
- **`ChatFolderInfo.icon` v `UpdateChatFolders` je už ta výsledná** (zvolená, jinak výchozí),
  takže seznam složek si nic nedopočítává. Doporučené složky ikonu nemají a dopočítá se jim jen
  pro zobrazení (`RecommendedChatFolder.iconName`) — do konceptu nejde, přidaná složka si ji
  dál volí podle obsahu.
- **Výběr je sheet s „Automaticky" a mřížkou**, ikona stojí v poli s názvem jako v oficiálních
  klientech. Řádek složek nad seznamem chatů ikony nekreslí — je to text, jako u oficiálního
  klienta na Androidu, a ikona vedle názvu by z tlačítek udělala dvojnásobnou šířku.
- **Ověřeno na emulátoru** (anglicky, složený Fold): založení složky se dvěma chaty, přeřazení
  tažením (server pořadí převzal, řádek nad seznamem ho ukázal), úprava (druh Boti, odebraný
  chat) a smazání s potvrzením, doporučená „Unread" přidaná a zase smazaná, „Přidat do složky"
  oběma směry. Ikona: výběr tlapky, uložení (server ji převzal, seznam ji ukázal), návrat na
  „Automaticky", robot u nové složky s Boty a ikony doporučených. „Chaty ze složek" v nastavení
  archivu se ukazuje se skutečnou hodnotou účtu; **zápis ověřený není** (mění nastavení účtu).
  **iOS jen přeložené.**

#### Návrh složek modelem

Pod doporučenými složkami je se zapnutou AI v zařízení blok „Návrh od AI v zařízení"
(`FolderSuggestions`, `ChatFoldersViewModel.suggestWithAi`, prompt `FolderPrompt.kt`; testy
`FolderSuggestionsTest`, `FolderPromptTest`). Model rozřadí 40 nejnovějších chatů hlavního
seznamu do pevných kategorií a kategorie aspoň se dvěma chaty nabídne jako složky s „Přidat".

- **Pevná sada kategorií** (Práce, Rodina, Přátelé, Škola, Nákupy, Zprávy, Koníčky; `OTHER` =
  žádná), ne názvy od modelu: klasifikace je jeho silná stránka, psaní ne, a s pevnou sadou se
  dá hlídat délka názvu (12 znaků) i duplicita. Odpověď je řádek „číslo: KATEGORIE" za chat
  (`parseFolderCategories`, tolerantní k obalu; chat jmenovaný dvakrát má první odpověď).
- **`OTHER` je výchozí při nejistotě**: chat ve špatné složce je horší než chat bez složky.
- **Model se pouští až na ťuknutí** („Navrhnout složky") a nic se nezakládá samo. Pod navrženou
  složkou jsou jména chatů (tři a „a další N"), takže uživatel vidí, co přidává. Přidaná zmizí,
  upravit ji jde jako každou jinou.
- **Kategorie, pro kterou už složka stejného jména je, se nenabízí** (bez ohledu na velikost
  písmen); Uložené zprávy a sponzorovaný chat model nedostane.
- **Chaty z paměti, nic se nedotahuje**: po startu jsou v hlavním seznamu první desítky, a to
  na návrh stačí.
- **Název se zkracuje na 12 znaků** (`CHAT_FOLDER_TITLE_MAX`) — komunitní překlad kategorie
  by mohl být delší a server by složku odmítl.
- **Model zvlášť v „Co čím počítat"** (`AiTask.Folders`), text, takže automaticky Nano.
- **Ověřeno na emulátoru se sondou** (falešný model s pevnou odpovědí): blok pod doporučenými,
  prompt nad skutečným seznamem (druhy chatů, náhledy bez nových řádků, bez Uložených zpráv),
  složky se jmény a „a 1 další". „Přidat" ověřené není — založilo by složku na skutečném účtu;
  zakládání je ale táž cesta jako u doporučených. **Na zařízení zbývá kvalita rozřazení
  skutečných chatů v češtině.**

#### Sdílené složky

Odkaz `t.me/addlist/…` ve zprávě otevře náhled složky (`FolderInviteSheet`, `InternalLink.FolderInvite`
z `checkChatFolderInviteLink`), v editoru vlastní složky je sekce Sdílení s odkazy
(`ChatFolderRepository.links` / `createLink` / `deleteLink`).

- **Nic se nepřidá bez potvrzení**, stejně jako u pozvánky do chatu: přidání složky znamená
  připojit se k jejím chatům. Chaty k připojení jsou zaškrtnuté všechny a jdou odškrtnout.
  Novou složku jde přidat i bez nich, když v některých chatech uživatel už je; u složky, kterou
  už má, se přidávají jen vybrané nové chaty, a když žádné nejsou, tlačítko není.
- **Sdílet jde jen složku z vybraných chatů** (`ChatFolderDraft.canBeShared`): TDLib odkaz na
  složku s druhy chatů nebo vynechanými odmítne. Editor to řekne pod tlačítkem, místo aby
  odkaz skončil chybou, a **sdílená složka druhy ani vynechané nenabízí** — uložení by server
  odmítl.
- **Odkaz nese chaty z `getChatsForChatFolderInviteLink`** — jen skupiny a kanály, kam smí
  uživatel zvát; soukromé chaty a boti v odkazu nejsou („1 chat" u složky se třemi). Bez nich
  odkaz vytvořit nejde (`NothingToShareException`, vlastní věta).
- **Odkaz se dělá z ULOŽENÉ složky**, takže s rozepsanými změnami tlačítko zašedne a řekne
  proč. Nový odkaz se rovnou zkopíruje, jako pozvánka do chatu.
- **Sdílenou se složka stane prvním odkazem a přestane být smazáním posledního** — a to
  potvrdí až `UpdateChatFolders`, ne odpověď na smazání. Proto nese `ChatFolderTab` příznak
  `isShareable`: bez něj `chatFolders` (StateFlow) update odfiltroval jako beze změny a editor
  dál schovával druhy chatů u složky, která sdílená už nebyla.
- **Po smazání posledního odkazu vrátí `getChatFolderInviteLinks` chvíli `FILTER_ID_INVALID`**,
  proto smazaný odkaz zmizí ze seznamu hned, ať čtení dopadne jakkoli.
- **Nové chaty v přidané složce** (autor do ní doplnil chaty) jsou karta nad seznamem chatů
  vybrané složky (`NewFolderChatsCard`, `ChatsViewModel.newFolderChats`), ne dialog: sheet se
  chaty k připojení se otevře až „Zobrazit". „Skrýt" pošle `processChatFolderNewChats`
  s prázdným seznamem — server tím nabídku zapomene. Ptá se jen u složky, která je sdílená
  a NENÍ moje (`isShareable && !hasMyInviteLinks`; `ChatFolderTab.hasMyInviteLinks` kvůli tomu).
- **`getChatFolderNewChats` se nesmí volat při každém výběru složky.** Dokumentace dovoluje
  nejvýš jednou za `chat_folder_new_chats_update_period` sekund (výchozí hodina), proto má
  `TdlChatFolderRepository` vlastní keš na složku; po vyřízení je prázdná.
- **Jeden sheet a jeden blok řádků pro „které z těchhle chatů"** (`FolderChatsSheet`,
  `CheckableChatRows` v `core/ui`): náhled z odkazu, nové chaty, chaty v odkazu i chaty
  k opuštění. Dřív byl sheet jen v `features/conversation` a seznam chatů by na konverzaci
  závisel.
- **Smazání přidané složky se ptá, které chaty opustit** (`getChatFolderChatsToLeave`, pak
  `deleteChatFolder(leaveChatIds)`), zaškrtnuté všechny, jako oficiální klienti. Dialog se
  otevře až s odpovědí — kdyby během čtení povyrostl o seznam, vypadalo by to jako chyba.
  U vlastní složky je seznam prázdný a dialog je stejný jako dřív.
- **Odkaz jde upravit: název a chaty** (`FolderLinkSheet`, `editChatFolderInviteLink`).
  Nabízí se chaty z `getChatsForChatFolderInviteLink` PLUS ty, které odkaz už nese — jinak by
  chat, který sdílet přestal jít, nešlo z odkazu odebrat. Bez chatů server odkaz odmítne, Uložit
  zašedne s větou. Ťuknutí na odkaz otevře sheet, sdílení zůstalo ikonou v řádku (nejčastější
  akce bez kroku navíc).
- **Potvrzení v sheetu hlásí sheet sám** (kopírování fajfkou, chyba uložení větou nad Uložit):
  snackbar obrazovky je pod modálním sheetem schovaný.
- **Načtení náhledu trvá pár vteřin** (dotaz na server). Kdo ťukne podruhé, trefí závoj sheetu
  a zavře ho — při ověřování přes `adb` se po ťuknutí čeká.
- **Ověřeno na emulátoru** (anglicky i česky): odkaz na složku „Plume test" (s testovací
  skupinou), sdílení schovalo druhy a vynechané, odkaz poslaný botem otevřel náhled „složku už
  máš"; smazání odkazu vrátilo složku na nesdílenou i v otevřeném editoru; u složky s druhem Boti
  vysvětlení. Náhled nové složky s chatem k připojení **jen sondou** (podstrčené `folderId = 0`
  a chaty), odškrtnutí chatu. **Skutečné přidání složky z cizího odkazu ověřené NENÍ** — chtělo
  by to druhý účet, nebo se připojit ke skutečným cizím chatům. Úprava odkazu ověřená naostro
  (přejmenování, odškrtnutí jediného chatu s větou, uložení, smazání ze sheetu). Dialog
  s chaty k opuštění a karta nových chatů **jen sondou** (podstrčené chaty u vlastní složky,
  nic se nepotvrdilo) — přidaná cizí složka na testovacím účtu není. **iOS jen přeložené.**
- **Dvěma účty ověřené 12. 10. 2026** (Pixel 7 autor, Pixel 5 příjemce): odkaz na složku
  s jednou skupinou poslaný zprávou, náhled „Chaty k připojení", přidání (záložka složky
  a skupina v ní), autor přidal do složky i odkazu druhou skupinu → karta „Nové chaty ve
  složce" → sheet → připojení, smazání složky s dialogem chatů k opuštění (jedna odškrtnutá
  zůstala, druhá se opustila).
- **Karta nových chatů naskočí až po restartu, nebo do hodiny.** Keš `getChatFolderNewChats`
  v `TdlChatFolderRepository` drží prázdnou odpověď z prvního otevření složky po celou periodu
  (`chat_folder_new_chats_update_period`, hodina) a server o nových chatech update nepošle.
  Při ověřování se Plume na straně příjemce proto zastavil (`am force-stop`). Je to cena za
  limit z dokumentace, ne chyba; oficiální klienti se ptají se stejnou periodou.
- **Chat, ve kterém příjemce po smazání složky zůstal, skončil v archivu** (Pixel 5, „Plume
  test zadosti"). Plume ho tam nepřesouvá; příčina (server, nebo nastavení archivu účtu)
  ověřená není.

### Ověření a štítek podvodu

Za jménem chatu je fajfka ověřeného účtu, nebo štítek „PODVOD" / „FALEŠNÝ" (`VerificationBadge`
v `core/ui`, doména `Verification`, pravidlo `verificationOf`, testy `VerificationTest`) —
v seznamu chatů, v hlavičce konverzace a v profilu, kde u podvodu a falešného stojí i věta, co
to znamená.

- **Podvod a falešný mají přednost před ověřením.** Ověření by uklidnilo přesně tam, kde má
  uživatel zbystřit.
- **Podvod je slovo, ne ikona**, v chybové barvě a s obrysem místo plné plochy. Vykřičník by se
  četl jako „nepovedlo se"; plná chybová plocha by v řádku přebila všechno ostatní.
- **`Chat` ověření nenese** — je na `User` (soukromý a tajný chat) a `Supergroup` (skupina
  i kanál), základní skupina ho nemá. Dočítá se lokálním `getUser` / `getSupergroup`
  (`TdlClient.verificationOf`, jedno místo pro seznam i hlavičku) a živě ho mění `UpdateUser`
  a `UpdateSupergroup`. Supergroupa se na chat převádí konvencí TDLib (`supergroupChatId`).
- **Přežívá přepsání chatu** (`keepingLiveState`), stejně jako dostupnost — `UpdateNewChat` by
  ho jinak smazal.
- **Jméno v seznamu je ve vlastním řádku s váhou, uvnitř s váhou BEZ vyplnění.** První verze
  dala jménu `weight(1f, fill = false)` a za štítek mezeru s `weight(1f)` — obě váhy si místo
  rozdělily napůl, takže se každé delší jméno zkrátilo v půlce řádku a čas nesl skok. Totéž
  čeká každý prvek, který má stát HNED za textem proměnlivé délky a přitom nechat zbytek
  řádku jiným.
- **Značka je i u jména odesílatele ve skupině** (`ChatMessage.senderVerification`), hned za
  jménem, před bleskem boostů a štítkem člena. Podvodník ve skupině by jinak vypadal jako
  kdokoli jiný. Nese ji `SenderInfo.verification` z keše odesílatelů (`resolveSender`:
  `verificationStatus` uživatele, u kanálu jako odesílatele `verificationOf(chat)`), takže
  dotaz navíc nestojí. Fajfka má akcent ověřený proti ploše (`accentOn`), menší než v seznamu
  (14 dp, jméno je `labelLarge`). Ověřeno sondou (zprávy od bota s `#over1–3` dostaly jméno
  a značku; necommituje se) — skutečný podvodník ve skupině na testovacím účtu není.
- **Ověřit účet může i bot** (`VerificationStatus.botVerificationIconCustomEmojiId`): značka
  organizace jako vlastní emoji, v seznamu, hlavičce, u jména odesílatele i v profilu, VEDLE
  fajfky Telegramu (`Verification.Verified(official, botIconEmojiId)` — z výčtu je proto
  uzavřená hierarchie). U podvodu a falešného účtu se nekreslí, ze stejného důvodu jako fajfka.
- **V profilu jsou to DVA řádky**: fajfka s „Ověřeno Telegramem" a pod ní značka od bota
  s důvodem ověření (`UserFullInfo` / `SupergroupFullInfo.botVerification.customDescription`),
  bez důvodu „Ověřil @bot". Na jednom řádku stála značka od bota vedle „Ověřeno Telegramem",
  jako by ji udělil Telegram.
- **Ověřovací bot musí být schválený Telegramem** — testovací bot značku přidělit nemůže, takže
  se ověřuje sondou (`verificationOf(chat)` a profil pro chat s testovacím botem, skutečné id
  vlastního emoji z `getStickerSet` sady `StatusEmoji`). Necommituje se.
- **Ověřeno na emulátoru**: fajfka u chatu Telegram v seznamu; štítek podvodu v seznamu,
  hlavičce i profilu **jen podstrčeným stavem** (sonda v `verificationOf`, `UpdateUser`
  a profilu pro testovacího bota — chat s botem má id BOTA, číslo před dvojtečkou v tokenu,
  ne `chat_id` z Bot API). Skutečný účet označený jako podvod testovací účet nezná.

### Tvar avataru podle druhu chatu

Člověk kruh, skupina čtyřlaločná sušenka (`MaterialShapes.Cookie4Sided`), kanál zaoblený čtverec
(`MaterialShapes.Square`) — `ChatKind.avatarShape()` v `core/ui/Avatar.kt`. Druh se pozná dřív,
než se dočte název.

- **Statický tvar nese DRUH, morf nese STAV** (`ShapeMorphs`). Avatary se nehýbou a neberou
  tvary, které už něco znamenají (šesti- a dvanáctistranná sušenka, SoftBurst).
- **Tvar musí sedět všude, kam avatar letí sdíleným prvkem**: seznam → hlavička konverzace →
  profil. Jinak by uprostřed letu přeskočil. Hlavička ho odvozuje z `isChannel`/`isGroup`,
  profil z `ProfileKind`. Výchozí `Avatar(shape = CircleShape)` je pro odesílatele, reakce apod.
  — tam jde vždycky o člověka.

### Hlavička: vyhledávací pilulka

Seznam chatů nemá nadpis, ale `AppBarWithSearch` (`ChatSearchBar.kt`): pilulka „Hledat
v chatech", na jejím konci hromádka příběhů a VEDLE ní fotka účtu s menu (Uložené zprávy,
Archiv, Nastavení; dřív tu bylo ⋮ vedle pilulky). Po ťuknutí na pilulku se rozbalí
`ExpandedFullScreenSearchBar` s výsledky.

- **Fotka účtu je ve slotu `actions` baru, ne v `trailingIcon` pilulky.** Uvnitř pilulky (vzor
  Gmailu) měla 30 dp v 40dp tlačítku a všechno kolem patřilo pilulce: ťuknutí těsně vedle fotky
  místo menu rozbalilo hledání, a stávalo se to často — hranice tlačítka v tónové ploše není
  vidět. Mimo pilulku ji od hledání dělí 4dp mezera (`AppBarWithSearch` ji dává sám, k tomu 8 dp
  okraje pilulky) a fotka vyrostla na 40 dp v plném 48dp terči. Varianta Google Messages (jen
  lupa, bez pilulky) se vědomě nedělá: chtěla by nadpis, který jsme zrušili, stav spojení by
  ztratil místo a hledání by se schovalo o ťuknutí hlouběji.
- **Pilulka a fotka účtu lícují s bloky seznamu (16 dp od kraje)**, jako lišta složek a FAB
  (`HEADER_EDGE` v `ChatSearchBar.kt`, `contentPadding` baru). Výchozí `AppBarWithSearch` dává
  pilulce jen 8 dp (`SearchBarAsTopBarPadding`) a slotu akcí 4 dp, takže hlavička přečnívala seznam
  na obou stranách o 8 dp; výchozí hodnota počítá se seznamem přes celou šířku, ne s odsazenými
  bloky. Okraj navíc není před přepínačem seznamu (holá ikona má odsazení svoje) ani vpravo pod
  tlačítky Windows; za semafory Macu ano, jinak by pilulka k semaforům přilehla (viz „Šířka na
  Macu“ v `desktop.md`). Pole rozbaleného hledání (`ChatSearchScreen`) stojí taky 16 dp od boků,
  na hraně bloků s výsledky. Platí i pro úzkou hlavičku s lupou a počítá se do prahu, kdy
  se z pilulky stane lupa. Ověřeno na emulátoru Foldu: složený (pilulka) i rozložený (lupa vedle
  prázdné konverzace), světlý motiv. Desktop (macOS, tmavý motiv): fotka účtu končí na hraně karet (396 b), vlevo se semafory se nic nepřidává. iOS neověřené.
- **Obroučky na fotce (tečka archivu, složené fotky Všech účtů) mají barvu `surface`**, ne
  pilulky (`surfaceContainerHigh`): fotka teď stojí na ploše stránky.
- **Hromádka příběhů v pilulce zůstala** — je to vstup do obsahu, ne identita, a do terče fotky
  nezasahuje.
- **Ověřeno na emulátoru** (Pixel 7, česky, světlý motiv): ťuknutí 1 px vlevo od terče fotky
  nedělá nic, ťuknutí na fotku otevře menu. Tmavý motiv, Všechny účty a tečka archivu neověřené.

- **Fotka účtu je vidět hned, i než se účet načte** (ikona člověka). Tlačítko, které se objeví
  až později, by do té doby nešlo otevřít — a na telefonu je to jediná cesta do Nastavení.
  Fotka je `AccountInfo.photoFileId` (`getMe`), načítá ji `ChatsViewModel` jen pro hlavní seznam.

- **Nahradila `LargeFlexibleTopAppBar`, protože ten byl hezký jen rozbalený.** Sbalený přešel na
  `scrolledContainerColor` (`surfaceContainer`), tedy pruh přes celou šířku v roli, která jinde
  v seznamu není. Na rozloženém Foldu navíc stál vedle 64dp hlavičky konverzace bar jiné výšky
  i barvy. Pilulka je zaoblený blok jako skupiny chatů a bar je vysoký přesně 64 dp —
  a nemá mezistav, jen je, nebo není.
- **Při scrollu dolů bar odjede** (`enterAlwaysSearchBarScrollBehavior`), nahoru se vrátí.
  Odjetý bar odkryje stavovou lištu, pod kterou by chaty jezdily bez pozadí — proto seznam
  kreslí pod lištou závoj v barvě stránky (`windowInsetsTopHeight(statusBars)`), nad obsahem,
  ale pod `topBar`. **Závoj je přechod do průhledna, ne plná barva:** s plným pásem končily
  chaty o výšku lišty pod okrajem ostrou hranou, jako useknuté.
- **Kde na pilulku nezbývá místo, je z ní lupa** (`CompactChatListHeader`, práh `PILL_MIN_WIDTH`
  220 dp z šířky řádku bez tlačítek okna, přepínače seznamu, fotky účtu a okrajů baru). Na Macu
  vedle konverzace sebraly semafory, přepínač a fotka účtu přes polovinu výchozí šířky a pilulce
  zbylo kolem 150 dp: „Hledat v chatech" se vešlo tak tak a hláška o spojení se uřízla. Na
  telefonu, tabletu a v širším seznamu (táhlem na polovinu) pilulka zůstává; mezi podobami se
  prolíná (`AnimatedContent`), i za tahu táhlem.
  - **Co pilulka nesla, stojí samostatně:** stav spojení jako znak a věta uprostřed řádku (jen
    při výpadku, jinak je střed prázdný — nadpis „Chaty" by jen opakoval navigaci), hromádka
    příběhů před lupou s lemem v barvě stránky, ne pilulky.
  - **Pole hledání vyroste z lupy** stejně jako z pilulky (lupa zapisuje svou polohu do
    `SearchMorph.pill`) a při zavření se do ní zase stáhne. Do sbaleného sloupce lupa přeletí
    (`collapseFlight("search")`), pilulka ne — ta se do 48 dp nevejde.
  - **Přepínač seznamu stojí v obou podobách na témže místě**, takže při přepnutí neposkočí.
  - Ověřeno na desktopu (macOS): lupa při výchozí šířce, hledání z ní a zpět do ní, pilulka po
    roztažení na polovinu. Stav spojení v úzké hlavičce a příběhy v ní neověřené (spojení bylo
    v pořádku, příběhy žádné), Windows a Fold taky ne.
- **Výpadek spojení bar přivolá zpátky** (`scrollOffset` animovaný na 0). Jinak by odjetý bar
  schoval jediné vysvětlení, proč nic nechodí.
- **Sbalený stav barvu nemění** (`scrolledAppBarContainerColor = surface`). Tónovaný pruh přes
  celou šířku byl přesně to, co vadilo na velkém baru.
- **Plochu baru dává i `Modifier.background(surface)`.** `appBarContainerColor` bez scroll
  behavior v material3 1.5.0-alpha22 nenakreslil nic a chaty pod barem prosvítaly.
- **Stav spojení nese pilulka: znak místo lupy a věta místo nápovědy** (`ConnectionStatusIcon`
  v `core/ui`, sdílený s hlavičkou konverzace), ne nadpis, vlastní prvek ani fotka účtu. Skoro
  celou dobu není vidět nic; „Chaty" v nadpisu jen opakovalo navigaci. Dřív to byla tonální
  pilulka s kolečkem na konci hledání — ukusovala místo nápovědě a vedle fotky působila jako
  cizí prvek. V rozbaleném hledání se stav neukazuje.
  - **Připojování: `LoadingIndicator` v `primary`, čekání na síť: ikona `cloud_off` v barvě
    obsahu, bez pohybu.** Znak říká, ŽE je problém, a klid proti pohybu i to, jestli stačí
    počkat; věta říká CO. Lupa a znak se prolínají (`contentKey` jen „je výpadek", takže mezi
    stavy výpadku se přechod nespouští a odcházející znak si nechá poslední stav).
  - **Fotka účtu stav NENESE.** Do října 2026 narostla (30 → 38 dp), zvlnila se a po jejím okraji
    obíhal vlnitý prstenec (`ConnectionIndicator`). Uživatel to odmítl — fotka je identita, ne
    kontrolka — a v hlavičce konverzace to bylo ještě horší: roztočený avatar protějšku
    vypadal, že se něco děje s ním. Před tím neobstály ani `CircularWavyProgressIndicator`
    kolem kulaté fotky (kruh v zubatém rámu nepůsobil jako celek) a zvlněná zmenšená fotka
    uvnitř prstence (mezera vypadala jako díra). Viz „Stav spojení a obnova po pozadí"
    v `databaze-spojeni-proxy.md`.
  - **Nápověda se mění fade through, ne `Crossfade`.** Dva texty napůl průhledné přes sebe
    („Čekání na síť" přes „Připojování") se nedají přečíst — tatáž úvaha jako u přechodů
    obrazovek, i s jejich čísly (90 / 210 ms).
  - **Výpadek se ukáže až po 600 ms, návrat hned** (`rememberShownConnection`). Po každém návratu
    z pozadí TDLib na zlomek vteřiny hlásí `Updating` a stav by při každém otevření aplikace
    probliknul. Na emulátoru se tím připojování po vypnutí letového režimu neukáže vůbec.
  - **Vzhled se ověřuje podstrčeným stavem, i bez přihlášení**: v `App.kt` dočasně
    `targetState = true` (shell se složí i nepřihlášený), v `ChatListScreen` pevný `AccountInfo`,
    `ArchiveUnread(2, 1)` (tečka) a `produceState`, který střídá čekání na síť → připojování → `null` po 4 s. Tak jde použít
    čerstvý emulátor, na kterém Plume přihlášená není. Necommituje se.
  - **Čtečce obrazovky stav říká nápověda pole**; tlačítko účtu je jen „Účet" — dokud stav
    nesla fotka, měl ho i popis tlačítka.
- **Hledá se ve všech známých chatech**, ne v načteném seznamu — viz „Hledání chatů" níž.

### Plocha stránky a řádku

- **Stránka `surfaceContainer`, řádek `surface`** (`groupRow`, `asListPage` v `core/ui/GroupedList.kt`),
  jako seznam v Google Chatu: ve světlém motivu světlejší řádek na tónované stránce, v tmavém tmavší.
  Změřeno ze snímků Chatu: světlý stránka #E6EDFF / řádek #F9F8FE, tmavý #0E192B / #060E1B
  (kontrast 1,10). Uživatelům tmavý seznam se světlejšími řádky (`surfaceBright`, jako systémová
  Nastavení: #2A2C34 na #181920) působil šedivě a rozsvíceně; ve světlém motivu se nezměnilo nic,
  `surfaceBright` je tam totéž co `surface`. **Na desktopu se třemi panely** mají teď řádky
  seznamu, karta konverzace (`surface` odjakživa) i bloky profilu v obou motivech tentýž tón
  a plocha okna je rámuje (paleta z výchozí barvy: tmavý okno #172026 / obsah #0D131B, světlý
  #E6EFF7 / #F6FAFF). Dřív byly v tmavém řádky světlejší než karta konverzace vedle nich.
- **`groupRow` čte `surface` přes `surfaceDim` / `surfaceBright`.** Pod stránkou je `surface`
  přemapovaný na stránku a jiná cesta k původní barvě přes `ColorScheme` nevede (`groupRow` se čte
  i mimo kompozici, v kreslení grafů). M3 definuje `surfaceDim` a `surfaceBright` jako nejtmavší
  a nejsvětlejší plochu motivu; ve světlém je `surface` totéž co `surfaceBright`, v tmavém totéž co
  `surfaceDim`, a přemapování je nechává. `background` to být nemůže, i když má tentýž tón: `Scaffold`
  z něj bere výchozí `containerColor`, takže ho přemapování stránky nést musí.
- **Řádky `surfaceContainerLowest` na `surface` se vyměnily, protože v tmavém motivu nesedly.** Do
  listopadu 2026 to tu tvrdilo, že se to „obrátí a je to správně", role prý drží hierarchii. Nedrží:
  `surfaceContainerLowest` je role nejnižšího důrazu a na Androidu 16 čistá černá, takže byly řádky
  #000000 na stránce #0D0E12 (kontrast 1,09), díry pod pilulkou hledání, která je světlejší.
  Vzniklo to laděním světlého motivu, předtím byly řádky `surfaceContainerHighest` a seznam působil
  těžce.
  Proti řádkům `surface` na stránce `surfaceContainer` je rozdíl ve stránce: tónovaná
  `surfaceContainer` je od řádku dál než skoro černý `surface` od `surfaceContainerLowest`.
- **Holý `surface` jako stránka pod řádky nejde:** řádek je sám `surface` a blok by zmizel.
  Proto stránku dává shell (`PaneCard`, `rememberPaneCardDecorator`)
  přemapováním `surface` a `background` VŠEM položkám zásobníku; výjimky jsou `Panes.ownSurface`
  (konverzace, článek, prohlížeč, příběhy, hovor, mini aplikace). Nová obrazovka je tím stránka sama.
- **Kdo schéma pod shellem vyměňuje, musí přemapování zopakovat.** `ChatColorTheme` staví schéma
  z `LocalAppColorScheme`, takže profil a seznam témat o stránku přišly, a stálo to za pohled až na
  snímku (profil měl #0D0E12). Proto `LocalListPage`.
- **Co na `surfaceBright` nebo na nové stránce splývá:** tichý odznak na `surfaceContainerHighest`
  (#23252E na #2A2C34), proto `outlineVariant`; plovoucí lišta složek ve výchozím `surfaceContainer`,
  proto `surfaceContainerHigh` jako pilulka; pole formuláře na `surfaceContainerLow`, v tmavém
  motivu tmavší než stránka, proto `groupRow`. **Náhled konverzace v Nastavení** má plochu
  z `LocalAppColorScheme`, ne `surface`, ten je tam už přemapovaný a bublina by na něm vypadala jinak
  než v konverzaci.
- **Mezery mezi chaty jsou volba** (`LocalChatRowGaps`, Nastavení → Vzhled, výchozí zapnuté). Se
  spárou 2 dp je to segmentovaný seznam M3 (`SegmentedGap`), bez ní jeden souvislý panel na skupinu
  jako v Google Zprávách (změřeno ze snímku: stránka #E7EEFF, panel #F9F9FF, mezi řádky nic). Bez
  mezer dává podklad panel pod řádkem (`background` na obalu tahu), jinak by v rozích stisknutého
  a vybraného řádku prosvítala stránka. Uspořádání připnutých mezery nechává vždycky. Platí jen pro
  seznam chatů a archiv; bloky Nastavení a profilu mezery mají dál.
- **`surfaceContainerLowest` zůstává jen tam, kde má ustoupit:** plocha konverzace při hledání a čip
  reakce (`docs/poznatky/emoji-samolepky-reakce.md`).
- **Název jde běžným řezem, polotučně jen nepřečtený.** Když je polotučný každý chat, nevyčnívá
  žádný. Proto `bodyLarge` + `FontWeight.SemiBold` jen podle `hasUnread`, ne `titleMedium` pro vše.
- **Odznaky stojí na řádku náhledu, pod časem.** Dokud měly vlastní sloupec vedle obou řádků,
  posunuly čas nepřečteného chatu doleva a časy v seznamu nestály pod sebou (18:25 vedle 18:00).
  Odznak (16 dp) sedí na středu prvního řádku náhledu (20 sp) i při víc řádcích.
- **Odznak nepřečtených není chybově červený.** M3 `Badge` je ve výchozím stavu `error`; nepřečtená
  zpráva ale není chyba a v odlehčeném seznamu byla ta červená jediná sytá barva na obrazovce.
- **Ztlumený chat má počet i čas neutrálně** (`quietCount` v `UnreadBadges`, `outlineVariant`
  s `onSurfaceVariant`), zmínka a reakce zůstávají v akcentu: míří na uživatele a TDLib na ně
  upozorní i ve ztlumeném chatu. Do listopadu 2026 měl ztlumený kanál stejně sytý odznak jako
  člověk, ačkoli přepínač chatů v konverzaci (`ChatSwitcherButton`) ho kreslil neutrálně
  s komentářem „jako v seznamu chatů". Ověřeno na emulátoru v Archivu: ztlumené kanály šedě,
  neztlumený chat v akcentu. Zmínka ve ztlumené skupině ověřená není.

### Karta narozenin

Nad doporučenou akcí, jen v hlavním seznamu (`BirthdaysCard`, `ChatsViewModel.birthdays`).
Poučení (dlaždice kvůli barvě avatarů, řazení, schování na 24 hodin) je v
`docs/poznatky/nastaveni.md`, „Narozeniny".

### Uložené zprávy

Uložené zprávy jsou v Telegramu **obyčejný soukromý chat s vlastním účtem** — id chatu je id
uživatele z `getMe()`. Všichni klienti je ale kreslí jinak a my taky:

- **Odchozí je tu VŠECHNO.** TDLib hlásí u přeposlané zprávy `isOutgoing = false` (napsal ji
  někdo jiný), takže se kreslila jako příchozí — šedá bublina vlevo a bez odznaku doručení —,
  zatímco vlastní fotky v témž chatu stály vpravo. Chat sám se sebou ale druhou stranu nemá;
  `TdlConversationRepository.toChatMessage` proto bere `isOutgoing || chatId == myId()`
  a odkud zpráva je, říká značka „Přeposláno od …". **Musí to sedět i se `sendState`** — ten se
  plní „jen u odchozích", takže dokud se opravil jen `isOutgoing`, zpráva se přesunula doprava,
  ale odznak doručení pod ní chyběl. Id vlastního účtu se drží per klient, jako keš odesílatelů:
  po přihlášení jiného účtu je jiné.
- **Vlastní název a záložka místo jména a fotky účtu** (`chatTitle`, `Avatar(isSavedMessages =
  true)`). Rozpoznává se podle `ChatRepository.savedMessagesChatId`, do UI jde přes
  `LocalSavedMessagesChatId` (poskytuje shell). **`Avatar` si to NEzjišťuje sám** — kreslí
  i odesílatele (reakce, zhlédnutí, profilovky ve skupině) a tam má být vlastní tvář.
- **V seznamu chatů nejsou, pokud nejsou připnuté** (`chatGroups`, test
  `ulozeneZpravyJenPripnute`). Vstup je menu účtu v pilulce (vedle archivu). Připnutí
  je výslovné přání uživatele, takže připnuté zůstávají — schovat je by působilo jako chyba.
- **Ve výběrech (přeposlání, sdílení, iOS rozšíření, Direct Share) jsou vždy první**, i když
  v seznamu chatů nejsou. V `ChatPicker` jako samostatná karta v `primaryContainer` nad
  seznamem, ne jeho první řádek — v řadě chatů by nejčastější cíl splynul s ostatními. „Pošlu si to" je nejčastější cíl. Hledání je najde podle přeloženého
  názvu.
- **Chat se zakládá hned po přihlášení** (`createPrivateChat`, lokální). U účtu, který si nic
  neuložil, ho TDLib v databázi nemá a nešlo by ho otevřít ani do něj přeposlat.
- **Záložka v avataru je `primary`, na kartě ve výběru proto dostává `backdrop`.** V paletě
  se zvýšeným kontrastem je `primaryContainer` světlý jako `primary` a kolečko s kartou
  splynulo; `Avatar` pak přes `accentOn` sáhne po barvě popředí té plochy.
- **V hlavičce není „online"** — vlastní dostupnost v Uložených zprávách nic neříká.
- **Zkratka konverzace a notifikace** (připomínka v Uložených zprávách) mají název
  „Uložené zprávy" a záložku (`bookmarkIcon`), ne jméno a fotku účtu.
- **Štítky Uložených zpráv (`SavedMessagesTag`) zatím nejsou.** Témata ano, viz níž.

#### Témata Uložených zpráv

„Zobrazit jako chaty" v hlavičce Uložených zpráv přepne na seznam témat (`SavedTopicsRoute`,
`SavedTopicsScreen`): Moje poznámky, pak chaty, ze kterých se zprávy uložily, s poslední uloženou
zprávou. Téma se otevře jako konverzace (`ConversationRoute.savedTopicId`), „Zobrazit jako zprávy"
v ⋮ vrátí jednu konverzaci. Hranice je `SavedTopicsRepository` (`TdlSavedTopicsRepository`).

- **Témata chodí JEN updaty** (`UpdateSavedMessagesTopic`). Dotaz na seznam ani na jedno téma
  TDLib nemá; `loadSavedMessagesTopics` nic nevrací, jen řekne „pošli další". Repository je proto
  eager a drží mapu podle id, a `load()` se volá při každém otevření seznamu (po startu je TDLib
  sám nepošle). **Konec načítání je chyba 404**, ne prázdná odpověď. `order == 0` = téma v seznamu
  není, jako u chatů.
- **Do tématu jiného než Moje poznámky psát NEJDE.** TDLib odesílání s `MessageTopicSavedMessages`
  odmítne („Messages can't be explicitly sent to a Saved Messages topic", ověřeno sondou) —
  zpráva bez tématu skončí v Mých poznámkách (id tématu = id vlastního účtu). Konverzace tématu
  proto rozlišuje dvě pole: `readTopic` (historie, hledání, připnuté, nepřečtené) a `topic`
  (odeslání, koncept, „píše…"), které u Uložených zpráv zůstává `null`. Cizí téma je jen pro čtení
  (`WriteAccess.SavedTopic`) s tlačítkem „Otevřít chat" do zdrojového chatu.
- **Volba „jako chaty" se pamatuje** (`AppSettings.savedMessagesAsChats`, výchozí ne) a řídí jen
  ťuknutí ze seznamu chatů a z menu účtu (obojí jde přes `onChatClick`). Notifikace, hledání
  a výběry otevírají dál celou konverzaci — míří na konkrétní zprávu.
- **Smazání tématu je `deleteSavedMessagesTopicHistory`**: zmizí uložené zprávy, zdrojový chat ne.
  U Mých poznámek se nenabízí — smazalo by všechny vlastní poznámky jedním ťuknutím.
- **Pořadí připnutých témat mění režim uspořádání** („Změnit pořadí připnutých" v nabídce
  připnutého tématu, jen když jsou připnutá aspoň dvě; `setPinnedSavedMessagesTopics`). Tentýž
  `ReorderRow` a `ReorderTopBar` jako u chatů a témat skupiny.
  - **Na rozdíl od témat skupiny se seznam přeskládá SÁM**: TDLib po uložení pošle updaty
    s novým `order`, kdežto u témat skupiny `order` pořadí připnutých nenese a ViewModel si ho
    drží zvlášť. Ověřeno na emulátoru, nic se lokálně nepřepisuje.
  - **Seznam k uložení je NOVÝ STAV připnutých** jako u `setPinnedChats`: co v něm chybí, TDLib
    odepne. Skládané pořadí se proto srovná s tím, co je připnuté teď (`savedPinnedOrderToSave`):
    téma odepnuté jinde vypadne, nově připnuté zůstane nahoře.
  - **Ověřeno na emulátoru**: připnutí dvou témat, přetažení za úchyt, Hotovo, nové pořadí
    v seznamu; s jedním připnutým se volba nenabízí. Pak vráceno do původního stavu.
- **Ověřeno na emulátoru** (česky): seznam čtyř témat, Moje poznámky s polem (zpráva odešla
  a objevila se v tématu i v jeho náhledu), téma Notifikator jen pro čtení a „Otevřít chat",
  připnutí a odepnutí (seznam se přeskládá sám updatem), dialog smazání (zrušený), volba přežije
  restart. **Smazání tématu ověřené NENÍ** — mazalo by skutečné uložené zprávy. iOS jen přeložené.

### Co položka ukazuje

Čas poslední zprávy, ztlumení, dostupnost protějšku a jeho právě probíhající činnost.

- **Čas se s věkem zjednodušuje** (`asRelativeTime`): dnes hodiny, včera slovem, do týdne zkratka
  dne, pak datum. V seznamu jde o STÁŘÍ zprávy, ne o pořadí během dne — „14:32" u zprávy z loňska
  nic neřekne. V konverzaci se naopak vypisují jen hodiny, protože tam jdou zprávy chronologicky
  za sebou.
- **„Naposledy viděn" má VLASTNÍ formát** (`asLastSeen`), ne tenhle. `asRelativeTime` je dělaný
  na SLOUPEC, kde čas stojí sám a zkratka dne se přečte jako zkratka; uvnitř věty z ní byla
  hláška **„naposledy ne"**, u které to vypadá, že se text usekl. `asLastSeen` proto skládá celou
  větu: „naposledy dnes, 7:41", „naposledy v neděli, 11:45", „naposledy 18. listopadu 2025".
- **Den nese předložku, protože v češtině není jedna** (`day_on_*`): „v pondělí", ale „ve středu".
  Lepit ji v kódu by znamenalo, že si ji jazyk neuhlídá — tentýž důvod, proč je pořadí dne
  a měsíce formátovací řetězec, ne slepenec.
- **Starší než týden dostane datum bez času.** Hodina u něčeho, co bylo před měsícem, nic neříká;
  rok se dopisuje, jen když nesedí s letoškem.
- **Dostupnost se musí jednou přečíst, ne jen odebírat.** `updateUserStatus` chodí jen na ZMĚNU,
  takže bez počátečního `getUser` by zelená tečka naskočila až ve chvíli, kdy se protějšek odpojí
  a zase připojí. V soukromém chatu je `chatId` shodné s `userId`, takže není co dohledávat.
- **Dostupnost a činnost PŘEŽÍVAJÍ přepsání chatu** (`keepingLiveState`). `Chat` od TDLib je
  nenese — chodí samostatnými updaty. Bez toho by každý `UpdateNewChat` nebo seed z databáze
  zhasl zelenou tečku i „píše…", protože obojí by se vrátilo na `null`.
- **„Píše…" se musí umět zhasnout samo.** TDLib činnost ukončuje samostatným `ChatActionCancel`,
  jenže kdyby se ten ztratil, zůstalo by „píše…" viset napořád. Každá činnost si proto nese
  odpočet, který nová zpráva o psaní posouvá — při souvislém psaní nikdy nedoběhne.
- **Vlastní činnost se zahazuje.** Porovnává se s id přihlášeného účtu, zjištěným jednou při
  přihlášení; v seznamu by vypadalo divně, že chat hlásí psaní, když píšeme my.
- **Fotka chatu se mění updatem `updateChatPhoto`, ne novým `Chat`.** Dlouho ho nikdo neodebíral
  a `avatarFileId` se bral jen z objektu chatu při prvním `upsert`: nová (i odebraná) fotka
  skupiny nebo kontaktu se v seznamu ukázala až po restartu. Totéž hlavička otevřené konverzace,
  která `chatInfo` čte jednou při otevření — ta teď bere název a fotku živě z repository
  (`ChatRepository.appearance`, stejný vzor jako `muted`). **Ověřeno na emulátoru proti
  starému buildu**: testovací skupina, fotka změněná z úpravy skupiny — se starým buildem
  zůstaly v seznamu i hlavičce iniciály, s opravou se fotka objevila hned, a po odebrání se
  iniciály vrátily. Kdo přidává další vlastnost chatu, ať zkontroluje, jestli k ní nechodí
  vlastní update (`*Updates` v `TdlClient`): `Chat` od TDLib přijde jen jednou.
- **Činnost má přednost před náhledem zprávy** a odlišuje se barvou — je to novější informace
  a obojí najednou se do řádku nevejde.
- **Telegram dostupnost záměrně rozmazává.** Kdo si to v soukromí vypne, je vidět jen jako
  „naposledy nedávno" a přesný čas nedostaneme. Proto je `Presence` několik pojmenovaných stupňů,
  ne čas — dopočítávat minutu, kterou neznáme, by bylo horší než neurčitost.

#### Řádky náhledu (`LocalChatPreviewLines`)

Nastavení → Vzhled → Řádky náhledu zprávy: 1 (výchozí), 2, nebo 3. Skladba volby je v
`docs/poznatky/nastaveni.md`, „Velikost samolepek a řádky náhledu".

- **Výška řádku jde podle obsahu, ne pevně** (`smoothPreviewHeight`). Se stejnou výškou zůstávalo
  u většiny skutečných chatů pod krátkou zprávou prázdno: na obrazovku se vešlo jen sedm chatů
  při třech řádcích. Krátká zpráva tak zabere stejně jako při jednom řádku.
- **Změna výšky je `animateContentSize` s effects specem.** Spatial spec má překmit a ten by
  rozkýval všechny řádky pod chatem. Při jednom řádku se modifikátor nepřidává vůbec: výšku drží
  avatar, takže výchozí nastavení nic neplatí.
- **„Píše…" drží poslední výšku.** Činnost nahradí náhled jedním krátkým řádkem a dlouhý náhled by
  se kvůli ní smrskl a po pár vteřinách zase roztáhl.
- **Ověřeno na emulátoru** (`wm density 240` kvůli seznamu vedle konverzace, `animator_duration_scale
  20`, polohy řádků z `uiautomator`): řádek s konceptem rostl přes 153 na 159 px, po smazání konceptu
  řádek pod ním jel přes 553 na 547, při `sendChatAction` od bota zůstalo 159 px. Výpis stihne jeden
  snímek za ~3 s, takže plynulost pod prstem je potřeba posoudit rukou.
- **Avatar s víc řádky stojí nahoře u jména.** Uprostřed od něj u dlouhého náhledu utekl. Odznaky
  zůstávají na středu, patří k pravému sloupci.
- **Zalomení v náhledu je mezera, stejně dlouhá kvůli offsetům emoji.** Platí i pro jeden řádek:
  dřív se tam víceřádková zpráva usekla po prvním zalomení a teď je vidět víc textu.
- **Řádky karty „Čeká na odpověď" volba nemění** (`AwaitingChatRow`), mají vlastní kompaktní tvar.

#### Kdo psal, stav doručení a obrázek v náhledu

Náhled poslední zprávy říká, kdo ji napsal („Jana: …", „Vy: …"), u vlastní zprávy je před
časem stav doručení a fotka, video, GIF nebo kolečko mají v textu čtvereček s obrázkem
(`ChatRowPreview.kt`, v core `ChatListPreview.kt`, testy `ChatListPreviewTest`).

- **Pravidlo je totéž jako v hledání zpráv a u témat**, jinak by se tři řádky téhož druhu
  rozešly: ve skupině křestní jméno (`previewSenderName`, sdílený s `TdlForumRepository`), u vlastní
  zprávy „Vy" kromě kanálu a Uložených zpráv, oznámení se jménem ve větě („Jana se přidala")
  předponu nedostane. „Vy" je i v soukromém chatu (jako v Google Messages), přestože tam stav
  doručení vlastní zprávu prozradí taky.
- **„Přečteno" se odvozuje, nenese ho zpráva**: `Chat.lastReadOutboxMessageId` chodí vlastním
  updatem (`UpdateChatReadOutbox`), a dvojitá fajfka je „id poslední zprávy ≤ přečteno". Kanál
  ukazuje jen hodiny a vykřičník — čtenářů je víc a „přečteno" tam nic neříká. U konceptu stav
  není: čas v řádku pak patří konceptu.
- **Potvrzení odeslání se odebírá zvlášť** (`messageSendSucceededUpdates` / `…Failed`): zpráva
  dostane nové id a stav, ale poslední zprávou chatu zůstává, takže na `UpdateChatLastMessage` se
  spoléhat nedá (témata to mají stejně). Mění se jen položka, jejíž poslední zpráva je ta odeslaná.
- **Jméno se dohledává před zápisem**, ne v `edit` — ten běží pod zámkem a dotaz na klienta tam
  nepatří. U chatu, který repository ještě nezná, se jméno nedohledává; `edit(fetchUnknown)` si chat
  dočte celý a `fullSummary` ho doplní.
- **Obrázek je inline obsah textu, ne prvek vedle něj** — stojí za jménem a zkracuje se s textem.
  Bere se nejmenší varianta fotky, jejíž KRATŠÍ strana zaplní čtvereček (`listThumbnailSize`):
  oříznutí na čtverec bere kratší stranu a varianta `s` má u fotky 16 : 9 jen 56 px.
- **Mizející, tajné a omezené médium obrázek nemá**, spoiler a citlivý obsah jen rozmazaný
  `minithumbnail` a nic se nestahuje — seznam je vidět bez otevření chatu. Stejné pravidlo jako
  citace v konverzaci (`quoteThumbnail`). GIF, jehož náhled je MPEG-4 (Coil ho neotevře, viz
  `picture()`), má proto jen rozmazaný čtvereček — bublina i citace to mají stejně.
- **Ověřeno na emulátoru (česky, světlý motiv)**: „Vy:" s fajfkou v soukromém chatu i u emoji,
  miniatury fotek v kanálech, „Plume test kanal: ▣ GIF" ve skupině, ztlumené kanály bez fajfky.
  Dvojitou fajfku po přečtení protějškem, hodiny při odesílání a vykřičník ověřené nejsou
  (pokrývá je jen test odvození `lastMessageRead`); tmavý motiv a iOS taky ne.

### Čeká na odpověď

Nepřečtený soukromý chat (nebo skupina se zmínkou), ve kterém na uživatele čeká otázka nebo prosba, má vedle počtu
nepřečtených kulatý odznak se šipkou odpovědi („Čeká na tvou odpověď" pro čtečku). Posuzuje
model v telefonu (`ChatTriage`), jen se zapnutou volbou v Nastavení → AI v zařízení; jádro
v `ai-v-zarizeni.md`, „Triáž chatů".

- **Ikonový odznak, ne štítek s textem** — stejného druhu jako „@" a reakce a stojí hned u počtu,
  protože se týká právě těch nepřečtených zpráv. Řádek nemá kam růst a slovní štítek by se pral
  s časem i náhledem.
- **Odznak patří poslední zprávě** (`ChatSummary.lastMessageId`): nová zpráva ho schová, než se
  posoudí znovu, přečtení chatu ho schová hned. Vypnutí volby schová všechny naráz.

**Karta „Čeká na tvou odpověď"** („Co jsem zmeškal", `AwaitingReplyCard`) nahoře v hlavním
seznamu, za narozeninami: tytéž chaty pohromadě, nejvýš čtyři a „a další N v seznamu níž".
Mezi desítkami nepřečtených se odznak ztratí; karta je ukáže hned po otevření aplikace.

- **Jen v hlavním seznamu bez vybrané složky** — ve složce a v archivu si uživatel vybral, co
  chce vidět, a karta by to přebíjela.
- **Křížek schová DVOJICE (chat, zpráva), ne kartu** (`awaitingReplyChats`, testy
  `ChatTriageTest`): přibude-li chat, který na kartě nebyl, vrátí se karta celá, i se schovanými.
  Chat, který mezitím uživatel přečetl, z ní zmizí. Schované drží proces; natrvalo se karta
  vypíná volbou v AI v zařízení.
- **Ťuknutí otevře chat** stejně jako řádek v seznamu (`opened` + fórum jako témata).
- **Ověřeno na emulátoru Fold**: karta s chatem od skutečného kontaktu a s otázkou od bota,
  křížek ji schová, další otázka ji vrátí, otevření chatu z karty ho přečte a z karty zmizí.

### Přehled konceptů

Menu účtu → Koncepty (`DraftsSheet`): chaty s rozepsanou zprávou napříč složkami, nejčerstvější
první, ťuknutí otevře chat s konceptem ve vstupním poli. Zdroj je `ChatRepository.drafts` nad
všemi známými chaty (`all` v `TdlChatRepository`), ne nad jednou složkou.

- **Sheet, ne obrazovka jako archiv.** Archiv je seznam, ve kterém se žije (vlastní rodina rout,
  konverzace vedle v dvoupanelu). Koncepty jsou rozcestník; konverzace se otevře v hlavním seznamu
  přes `onChatClick` / `onForumClick`, sheet napřed odjede a teprve pak se chat otevře.
- **Položka v menu je jen s koncepty** a nese jejich počet v NEUTRÁLNÍ barvě — koncept je náš
  rozdělaný text, nic, co by volalo o pozornost jako nepřečtené v archivu.
- **Text konceptu na tři řádky**, ne jeden jako v řádku seznamu: tady je to jediné, co řádek říká.
  Uložené zprávy se jmenují `chatTitle` (v TDLib nesou jméno účtu).
- **`drafts` ve ViewModelu je `null`, dokud se nenačte.** S počáteční prázdnou hodnotou se sheet
  obnovený po zániku procesu (`rememberSaveable`) hned zavřel — prázdný seznam pro něj znamená
  „všechno odesláno" a zavírá se, aby nezůstal slepou uličkou. Ověřeno přeinstalací s otevřeným
  sheetem.
- **Jen chaty, které TDLib zná.** Koncept v chatu, který se v relaci ještě nenačetl (hluboko
  v archivu), v přehledu není, dokud ho seznam nedotáhne.
- **Koncepty ve vláknech komentářů jsou v přehledu z EVIDENCE, ne z updatů** (`CommentDrafts`,
  `ThreadDrafts`/`TdlThreadDrafts`, testy `CommentDraftsTest`). TDLib koncept vlákna nehlásí
  ničím (`UpdateChatDraftMessage` nese jen koncept celého chatu, `UpdateForumTopic` témata);
  je jen v odpovědi `getMessageThread`. Plume si proto pamatuje vlákna, ve kterých koncept
  psalo (`AppSettings.commentDraftThreads`, po účtech), a při čtení přehledu je ověří.
  - **Zapisuje obal repository vlákna** (`CommentDraftTracking` kolem
    `ThreadConversationRepository` v Koinu), ne ViewModel konverzace: koncept ukládá víc míst
    (psaní, odeslání příloh) a všechna jdou přes `saveDraft`. Prázdný koncept vlákno zapomene.
  - **Ověřuje se při změně evidence a při otevření přehledu** (`DraftsViewModel.refresh`).
    Vlákno bez konceptu (odeslaný, smazaný jinde) z evidence vypadne, chyba čtení (bez sítě)
    ho tam nechá na příště.
  - **Koncept psaný jen na jiném zařízení ve vlákně, které Plume neotevřelo, v přehledu není** —
    zjistit to jde jen projitím všech vláken všech kanálů.
  - Řádek nese pod diskusní skupinou „Komentář k: <příspěvek>" (kořen vlákna z
    `MessageThreadInfo.messages`, nejstarší zpráva). Ťuknutí otevře vlákno místo konverzace
    (`openCommentThread`, kanál pod ním není); mazání a „Vrátit" jako u témat, hromadné smazání
    dočistí vlákna po jednom.
- **Koncepty v tématech skupin jsou v přehledu taky** (`TopicDrafts`, `TdlTopicDrafts`), pod
  názvem skupiny s řádkem „# Téma" a ťuknutí otevře přímo téma (pod ním seznam témat, jako
  u nalezené zprávy). `Chat.draftMessage` nese jen koncept celého chatu; koncept tématu je
  v `ForumTopic.draftMessage` a mění ho `UpdateForumTopic` — **jen u témat, která TDLib zná**.
  - Proto se skupina s tématy jednou za přihlášení projde (`getForumTopics`, prvních 100 témat
    podle aktivity) a pak už stav drží updaty. Koncept hlouběji než stovka témat chybí, dokud
    se nezmění.
  - **Název tématu pro téma, které přišlo jen updatem, se dočte `getForumTopic` — jen když ho
    neznáme.** `getForumTopic` po odpovědi pošle `UpdateForumTopic` i beze změny a čtení
    s updatem by se honily dokola (stejná past jako v `TdlForumRepository.topicChanges`).
  - Třída je eager (`createdAtStart`): koncept psaný na jiném zařízení chodí updatem i bez
    otevřeného přehledu. Po odhlášení se stav i seznam prošlých skupin zahodí.
  - Klíč položky přehledu je chat i téma (`DraftItem.key`) — jeden chat může mít koncept
    v celém chatu i ve víc tématech.
  - Počet v menu účtu zahrnuje i témata.
  - Ověřeno na emulátoru (Fold) v testovací skupině: koncept v tématu General v přehledu
    („# General"), počet v menu, ťuknutí otevře téma s konceptem v poli a zpět vede na
    seznam témat, smazání a „Vrátit".
- **Ověřeno na emulátoru**: koncept v Uložených se objevil nahoře, ťuknutí otevřelo konverzaci
  s textem v poli, po smazání textu počet v menu živě klesl.
- **Mazání z přehledu: koš u konceptu, bez potvrzení, se snackbarem „Vrátit"** — jako archivace.
  `ChatRepository.deleteDraft` si koncept před smazáním přečte z TDLib (`getChat().draftMessage`)
  a vrací `DraftRestore`, které ho vrátí CELÝ: `ChatSummary.draft` nese jen text a entity, ne
  odpověď ani náhled odkazu. **Původní datum nezůstane**: i když se vrací původní `DraftMessage`
  s jeho `date`, koncept má po vrácení čas vrácení (14:43 → 14:45 na emulátoru) a zařadí se
  v přehledu nahoru.
- **Smazaný řádek odjíždí animací a sheet mění výšku plynule.** `drafts` ho ztratí v jednom
  snímku, takže si přehled odjíždějící řádky drží sám (`LeavingDrafts`) na jejich původním
  místě a sbalí je `AnimatedVisibility`; vrácený koncept se rozbalí. Řádky se počítají
  v `remember` přímo při kompozici, ne efektem — s efektem by první snímek bez konceptu řádek
  ukázal bez něj a bliknul by. Zbytek skoků (snackbar) vyrovná `animateContentSize` kolem
  seznamu. `animateItem` na to nestačí: seznam je vysoký jen na obsah, takže se zmenší hned
  a mizící řádek ořízne. Ověřeno zpomalenými animacemi (`animator_duration_scale 8`).
- **Snackbar je uvnitř sheetu a POD seznamem, ne přes něj.** Snackbar seznamu chatů by ležel pod
  sheetem. Překrytý (`Box`) zase zakryl zbylý koncept: sheet je vysoký jen na obsah, takže po
  smazání jednoho ze dvou konceptů zbyl řádek přesně pod snackbarem.
- **Prázdný přehled se zavře až po doběhnutém snackbaru**, jinak by „Vrátit" posledního
  konceptu odjelo se sheetem. Rozpracovaná smazání se počítají OD ŤUKNUTÍ: update, že koncept
  zmizel, může přijít dřív než výsledek smazání. ViewModel proto výsledek ohlásí vždycky
  (`DraftDeleted.restore` `null` = selhalo nebo nebylo co vracet), aby počítadlo kleslo. Po
  „Vrátit" se ještě čeká (nejvýš 2 s), než vrácený koncept přijde updatem.
- **Ověřeno na emulátoru** (Fold, česky, koncepty v Uložených a u testovacího bota): smazání
  a „Vrátit" (koncept zpět v přehledu), smazání posledního (přehled otevřený se snackbarem, po
  něm se zavřel). **Pozor při ověřování:** snackbar zmizí po 4 s a koš dalšího konceptu leží
  přesně pod „Vrátit" — ťuknutí se zpožděním smaže další koncept.
- **„Smazat vše" (od dvou konceptů) jde přes potvrzovací dialog, ne přes „Vrátit"** jako
  jednotlivé smazání: hromadné smazání (`clearAllDraftMessages`) server rozešle na všechna
  zařízení a vracet desítky konceptů po jednom by byla nová past. Mažou se i koncepty
  v tajných chatech. **Koncepty v tématech se po něm dočistí po jednom** (`deleteAllDrafts`):
  TDLib je vede zvlášť a dokumentace hromadného smazání mluví jen o chatech. Přehled se zavře
  sám, až zmizí poslední koncept. Ověřeno na emulátoru (…527): tři koncepty (dva chaty a téma
  skupiny), po „Smazat vše" přehled zmizel a ani po restartu aplikace žádný koncept nezůstal.

### Archiv

Archivované chaty jsou vlastní seznam (`ArchiveRoute`, `ArchiveScreen`), vstup je menu účtu
v pilulce hledání. Do archivu a zpátky se chat dostane z nabídky po dlouhém stisku; místo potvrzení je
snackbar s „Vrátit".

- **Repository drží VŠECHNY chaty, které TDLib ohlásí, a složka je jen pozice**
  (`TdlChatRepository.positions`, `ChatFolder`). Dřív se chat mimo hlavní seznam vůbec neukládal,
  takže se zahazovaly i jeho updaty — archivovaný chat neukázal v konverzaci „píše…". Seznamy
  složek se skládají až při publikaci; `ChatSummary.order` / `isPinned` / `folder` platí pro tu
  složku, ze které položka přišla.
- **Počet pro `getChats` se bere ze složky, ne ze všech známých chatů.** Těch je víc (i chaty
  mimo složky, z notifikací a odkazů) a limit by se nafukoval.
- **Archivace je `addChatToList(ChatListArchive)`, vrácení `addChatToList(ChatListMain)`.**
  Složky se vylučují, odebrání z té druhé pošle TDLib samo updatem pozice s nulovým `order`.
  **Do archivu nejdou dva chaty: Uložené zprávy a chat služby Telegramu** (id 777000 —
  přihlašovací kódy, upozornění na přihlášení). Pravidlo je napevno v TDLib
  (`MessagesManager::add_dialog_to_list`, „Chat can't be archived"), příznak v API na to není
  (`canBeArchived` mají jen příběhy), takže ho drží `ChatSummary.canBeArchived` v `core/telegram`
  a ptají se ho nabídka, tah i výběr víc chatů. Dřív se hlídaly jen Uložené zprávy: výběr
  s chatem Telegram archivaci nabídl a skončil obecnou hláškou „Akci se nepodařilo provést".
  **Vrácení z archivu TDLib nebrání**, takže chat Telegramu archivovaný dřív (jiným klientem)
  jde vrátit — pravidlo se ptá jen u přesunu DO archivu.
- **Připnutí patří pozici ve složce.** Archiv má vlastní připnuté (a vlastní limit
  `pinned_archived_chat_count_max`), proto `setPinned` bere složku.
- **Archiv se začne načítat až při otevření** (`ChatsViewModel` s parametrem `ChatFolder.Archive`).
  Do té doby je `Loading`, ne prázdný — složka je hotová, až se jednou přečte z databáze, nebo
  když v ní už něco je.
- **Jeden ViewModel i obsah pro oba seznamy.** `ChatsViewModel` dostane složku parametrem
  (Koin, klíč `chats-archive`), seznam je `ChatListContent`, akce `ChatActionsHost`. Archiv
  nemá hledání ani kartu aktualizace, jinak je to tentýž kód.
- **Na široké ploše je archiv samostatná list-detail scéna** (`ARCHIVE_SCENE` v `AppShell`).
  V dvoupanelu má vpravo vlastní detail panel `ArchiveConversationRoute` (nebo placeholder),
  takže neukazuje cizí konverzaci otevřenou z hlavního seznamu. Šipka zpět v hlavičce archivu
  odebere celou archivní větev a vrátí hlavní seznam v jeho původním stavu (včetně původně
  otevřené konverzace). Chat otevřený z archivu má vlastní routu `ArchiveConversationRoute`
  a při jeho obnovení z archivu („Vrátit mezi chaty“) se jeho konverzace automaticky zavře.
  Symetricky při archivaci chatu z hlavního seznamu se zavře jeho otevřená `ConversationRoute`.
  Šipku zpět má archiv i v dvoupanelu — na rozdíl od sekce nastavení je to seznam, ne detail.
- **Prázdný archiv má tentýž animovaný prázdný stav jako placeholder detailu**
  (`IntroEmptyState` v `core/ui`, vytažený z `ConversationPlaceholder`). Na široké ploše stojí
  oba vedle sebe a dva různé vzhledy pro „tady nic není" by se přely. Prázdný stav si
  `ChatListContent` bere jako slot (`empty`), hlavní seznam má dál holý text.
- **Prázdný seznam na široké ploše dostane celou šířku** (`nothingToPick` v `ShellContent`):
  bez otevřené konverzace a s prázdným zobrazeným seznamem (archiv i hlavní) přejde scéna na
  jeden panel. Placeholder „vyber chat vlevo" by vedle prázdného stavu radil něco, co udělat
  nejde. Rozhoduje se až nad `Ready` — `Loading` za prázdný nepočítat, jinak se rozvržení
  přepne tam a zpátky, než seznam dorazí. Výjimka z pravidla „dva panely vždycky" (viz „Navigace").
- **„Otevřená konverzace" se od rozdělení scén musí ptát NA VĚTEV, ne na zásobník jako celek**
  (`hasOpenConversation`). Dokud podmínka brala `ConversationRoute` i `ArchiveConversationRoute`
  najednou, držela otevřená konverzace hlavního seznamu dvoupanel i nad PRÁZDNÝM archivem —
  vlevo prázdný stav, vpravo placeholder archivní scény, tedy přesně ty dva prázdné stavy vedle
  sebe, kterým má `nothingToPick` bránit. Dřív to nevadilo, protože archiv sdílel scénu
  s hlavním seznamem a vpravo stála skutečná konverzace. **Obecně: co se ptá na zásobník Chatů,
  musí od rozdělení scén vědět, kterou větev se ptá.**
- **Pravidla nad zásobníkem Chatů jsou čisté funkce** (`ChatsStack.kt`, testy `ChatsStackTest`),
  ne lambdy v `entryProvider`. Zásobník nese dvě list-detail větve naráz, takže skoro každá
  operace musí vědět, do které patří — a uvnitř lambdy se to neotestuje. První verze testů to
  obešla tak, že si těla lambd přepsala do testu; takový test projde i nad rozbitým kódem,
  protože produkční cestu vůbec nezavolá.
- **Hlavička je obyčejný `TopAppBar` bez scroll behavior**, takže nemění barvu — tónovaný pruh
  přes celou šířku byl důvod, proč hlavní seznam opustil `LargeFlexibleTopAppBar`.
- **Počet nepřečtených v archivu počítá TDLib** (`UpdateUnreadChatCount` pro `ChatListArchive`),
  ne my nad načteným seznamem — archiv se načítá až při otevření, takže do té doby by nebylo
  z čeho počítat, a právě tehdy je odznak nejpotřebnější. Update chodí **jen se zapnutou databází
  zpráv** (`useMessageDatabase`); bez ní se nic nerozbije, jen se nikdy neobjeví.
- **`unreadCount` v tom updatu započítává i ručně označené chaty** (ověřeno na emulátoru:
  archivovaný chat bez nepřečtených zpráv, jen s `isMarkedAsUnread`, odznak vyrobil). Sedí to
  tedy s `ChatSummary.hasUnread` a dopočítávat `markedAsUnreadCount` netřeba.
- **Odznak je na dvou místech, protože menu účtu je zavřené**: číslo u položky Archiv a tečka na
  fotce účtu. **Tečka je jen od NEZTLUMENÝCH chatů** (`unreadUnmutedCount`) — do archivu se
  ztlumené chaty ukládají, takže tečka od nich by byla signál, který nikdy nezhasne. Číslo
  u položky počítá všechny, ale v neutrální barvě, dokud není co ztlumeného; sytý odznak nad
  samými ztlumenými chaty je falešné volání.
- **Ikona na podkladu patří k hraně, ze které se tah odkrývá** (doleva tedy vpravo od popisku,
  doprava vlevo). S pevným pořadím se při tahu doleva odkryl jako první konec věty, ze kterého
  není poznat, co se stane.
- **Co tah udělá, volí uživatel zvlášť pro každou stranu** — viz „Akce tahem" níž. Výchozí je
  archivace na obou stranách: dokud jiná akce nebyla, musel by si uživatel pamatovat směr pro
  nic. Archivace se nepotvrzuje — snackbar „Vrátit" je tentýž jako u archivace z nabídky.
  Uložené zprávy a chat Telegramu archivovat tahem nejdou, stejně jako nemají položku v nabídce
  (viz „Archiv", `canBeArchived`).
- **`SwipeToDismissBox` se na tohle NEHODÍ a je pryč** (prah v `ChatSwipeGesture.kt`,
  testy `ChatSwipeGestureTest`). Byl „hodně moc citlivý": při scrollování seznamu odlétaly
  chaty do archivu. Dvě příčiny, ani jedna se zvenku vypnout nedá:
  - **Stojí na `draggable`, které si tah vezme po obyčejném touch slopu a je směrově
    agnostické.** Položka je potomek seznamu, takže gesto dostane dřív — a palec jde na začátku
    scrollu obloukem do strany, takže osm bodů do strany stačilo, aby seznam o scroll přišel.
  - **Archivuje ZKRATKOU PŘES RYCHLOST.** `AnchoredDraggableDefaults.flingBehavior` bere tah
    rychlejší než 125 dp/s jako dokončený BEZ OHLEDU NA TO, kam došel; `positionalThreshold`
    (výchozích 56 dp) se v tu chvíli vůbec nepočítá. Zvedat práh je proti tomuhle k ničemu —
    škubnutí prstem archivovalo, ať byl jakkoli vysoko.
- **Vlastní detektor rozhodne jednou za gesto** (`detectHorizontalSwipe`, sdílený s odkrýváním
  časů a odpovědí tahem v konverzaci, viz „Jeden detektor tahu do strany"): rozhoduje se až po DVOJNÁSOBKU touch slopu a jen když je
  vodorovná složka aspoň dvakrát větší než svislá; do té doby nic nespotřebovává, takže si
  šikmý tah vezme seznam. Akce se provede při PUŠTĚNÍ po `chatSwipeThreshold` (40 % šířky
  řádku, nejvýš 160 dp), rychlost se nepočítá vůbec. Prah ohlásí haptika, jako u odpovědi
  tahem v konverzaci.
- **S vlastním detektorem odpadl hlídač dvojího odeslání.** `SwipeToDismissBox` hlásil dokončený
  tah z `LaunchedEffect`, jehož klíčem byla i lambda `onDismiss` — ta se při rekompozici položky
  mohla změnit, efekt se rozběhl ZNOVU a chat se archivoval podruhé (a po „Vrátit" zase zpátky).
  Tady akce odchází přímo z konce gesta, takže jednou. **Past platí pro každé použití téhle
  komponenty**, kdyby se někde vrátila.
- **Archivace odchází PŘED animací odjezdu.** Odscrollovaná položka se uklidí z kompozice
  i s rozsahem korutin, takže po odsunutí a rychlém scrollu by se akce na konci animace už
  neměla kde odeslat.
- **Tvar bloku se počítá mimo řádek** (`groupItemShape`): tentýž tvar musí mít i podklad
  odkrývaný tahem, jinak pod zaoblenou položkou vykukují jeho hranaté rohy. `animateItem` zůstává
  na kořeni položky, tedy na tahu, ne na řádku uvnitř.
  - **Stisk mění tvar i podkladu tahu** (`animatedGroupItemShape(…, pressed)`, říjen 2026):
    stisknutý chat se zaoblí na 16 dp. Stisk se čte z `interactionSource`, který položka předá
    `contextClickable` v řádku, a tvar se spočítá jednou nad oběma — se dvěma by se podklad
    zaobloval jinak než řádek nad ním.
- **Bez tahu se podklad nekreslí VŮBEC, ani jeho plocha.** Stejný tvar nestačí: vyhlazené hrany
  řádku jsou poloprůhledné a barevný podklad pod nimi prosvítal jako světlý lem kolem každého
  chatu, v tmavém tématu na tmavém řádku do očí bijící. Na emulátoru
  (hustota 3,0) leží rovné hrany na celých pixelech a lem je vidět jen v zaoblených rozích; na
  telefonu s necelou hustotou jde kolem celé karty. **Obecně: pod plochou, která má být krycí,
  nesmí trvale ležet jiná barva stejného tvaru.** Ukáže se na každé vyhlazené hraně.
- **Nastavení archivu je sheet z hlavičky archivu, ne pátá sekce Nastavení.** Jsou to dvě volby
  o tom, co se s archivem děje samo, a kdo je hledá, stojí zrovna v archivu.
- **TDLib na změnu `ArchiveChatListSettings` NEPOSÍLÁ update**, takže sheet je jediný zdroj pravdy:
  přepínač se pohne hned a při selhání se vrátí. Čekat na potvrzení by znamenalo přepínač, který
  se po ťuknutí nehýbe.
- **Zapisuje se celým objektem**, jako ztlumení chatu — všechny tři volby nese `ArchiveSettings`.
- **„Nechávat v archivu chaty ze složek" (`keepChatsFromFoldersArchived`) jen se složkami a jen
  bez „Nechávat chaty v archivu"** (`AnimatedVisibility` v sheetu). Týká se neztlumených chatů,
  které jsou ve složce vybrané nebo připnuté, a TDLib ho ignoruje, když platí volba nad ním —
  ta drží v archivu všechny neztlumené. Přepínač bez následku by sliboval něco, co se nestane.
  „Nechávat chaty v archivu" ověřené se dvěma účty (12. 10. 2026): zapnuté — archivovaná
  skupina po zprávě od druhého účtu zůstala v archivu; vypnuté — vrátila se do hlavního seznamu.
- **„Archivovat chaty od neznámých" povoluje server** (`can_archive_and_mute_new_chats_from_unknown_users`);
  kde ho nepovolí, TDLib zapnutí odmítne. Přepínač se tam proto nenabízí vůbec — přepínač,
  který skončí chybou, je horší než žádný.

### Akce tahem

Nastavení → Obecné → Chaty → „Tah doprava" / „Tah doleva": archivovat, přečteno, ztlumit,
připnout, odložit, nebo nic (`ChatSwipeAction`, `AppSettings.chatSwipeActions`). Platí v hlavním seznamu,
v archivu i ve složkách — všechny jsou `ChatListContent`.

- **Akce se u chatu, kde už platí, otočí** (`chatSwipeEffect`, testy `ChatSwipeGestureTest`):
  archivovaný se vrátí, ztlumený odtlumí, připnutý odepne, přečtený označí jako nepřečtený.
  Tah tak jde vždycky vrátit týmž tahem a volba v Nastavení je sloveso, ne dvojice; že se
  otáčí, říká věta nad výběrem. Ztlumení tahem je natrvalo, jako z lišty výběru.
- **Směr je FYZICKÝ, ne začátek a konec řádku.** Tak o něm uživatel mluví; aplikace jazyk
  psaný zprava doleva nemá.
- **`pointerInput(Unit)` drží lambdy z PRVNÍHO snímku — i s chatem, který zachytily.** Druhý
  tah po připnutí poslal `togglePinned` se starým `chat.isPinned = false`, takže chat
  „připnul" znovu a podklad přitom správně hlásil „Odepnout" (ten se kreslí z čerstvého stavu).
  Stejná past ležela i v původní archivaci tahem; nevadila jen proto, že archivovaný chat ze
  seznamu zmizí. Proto `rememberUpdatedState` pro akce i pro `onSwipe` a ViewModel provádí
  přesně efekt spočítaný při tahu (`swipe(chat, effect)`), ne přepnutí podle předaného chatu.
  Klíčem `pointerInput` je jen SMĚR (kam táhnout jde), ne akce — ta se mění se stavem chatu.
- **Ze seznamu odjíždí jen archivace** (`ChatSwipeEffect.leavesList`); ostatní akce chat
  nechají na místě a řádek se po puštění vrátí. I odjetý řádek se po vteřině vrátí, pokud
  ještě existuje (složka s archivovanými chaty, chyba od TDLib) — jinak v seznamu visel
  neviditelný řádek, na který šlo ťuknout.
- **Popisky na podkladu jsou krátké** („Přečteno", „Odtlumit", „Vrátit"), ne věty z nabídky.
  Podklad je u prahu odkrytý jen ze 40 %, a „Označit jako nepřečtené" tam zůstalo z půlky pod
  řádkem (ověřeno na emulátoru).
- **Čtečka obrazovky tah neudělá, proto jsou obě akce i vlastními akcemi řádku** (`customActions`,
  v TalkBacku „Akce" / tah nahoru a doprava). Popisky jsou věty z nabídky nad chatem („Označit
  jako přečtené", „Vrátit mezi chaty") — krátký popisek z podkladu bez taženého řádku nic neřekne.
  - **Akce musí sedět na uzlu s ťuknutím** (`ChatRow`, jeho `contextClickable`), ne na obalu
    tahu: obal je pro čtečku prázdný uzel, na který se nedá zaměřit, takže by akce nikdo
    nenabídl. `SwipeableChat` proto předá obsahu modifikátor (`content(swipeActions)`)
    a `ChatListContent` ho dá řádku. Ve výběru víc chatů akce nejsou, stejně jako tah.
  - Ověřeno headless testem (`SwipeActionsSemanticsTest`: popisky na uzlu řádku, akce volá
    tah); se skutečným TalkBackem neověřené (viz „Zpráva pro čtečku obrazovky"
    v `konverzace-zobrazeni.md`, proč na emulátoru nejde).
- **Strana s „Nic" tah nebere** (`SwipeDirection.Left` / `Right`), takže si šikmý tah vezme
  seznam a dlouhý stisk zůstane nabídce.
- **Ověřeno na emulátoru (česky):** nastavení obou stran, připnutí a odepnutí tahem, „Nic"
  doleva, nepřečteno a přečteno tahem, archivace doleva i „Vrátit" z archivu. Ztlumení tahem
  ověřené není (zapisuje nastavení upozornění chatu účtu), jde ale stejnou cestou jako
  „Ztlumit" z lišty výběru. **iOS jen přeložené.**

### Akce nad chatem

Dlouhý stisk otevře bottom sheet: připnout, ztlumit, označit jako nepřečtené, smazat/opustit.

- **Smazat NENÍ `deleteChat`.** Ten maže obsah VŠEM členům — z položky v seznamu to nikdo nečeká.
  U soukromého chatu se volá `deleteChatHistory(removeFromChatList = true, revoke = false)`, tedy
  smazání jen u nás, u skupiny a kanálu `leaveChat`. Jsou to dvě různé věci, takže má každá
  vlastní popisek i vlastní text potvrzení; jeden společný by lhal.
- **Nevratná akce má potvrzení a pojmenovává následek**, ne „opravdu?". `ChatActionsState` proto
  drží `target` (sheet) a `pendingDelete` (dialog) zvlášť — mezi zavřením nabídky a potvrzením
  nesmí zůstat viset obojí.
- **„Přidat na plochu" (nabídka nad chatem i ⋮ v profilu) připne TUTÉŽ zkratku konverzace**,
  kterou zakládají notifikace a Direct Share (`conversationShortcut`, id `chat_<id>`;
  `ShareTargets.pinToHomeScreen`, `ShortcutManagerCompat.requestPinShortcut`). Má tak fotku chatu
  (nebo iniciálu, u Uložených zpráv záložku) a otevře chat stejným intentem jako notifikace;
  launcher připnutou zkratku aktualizuje spolu s tou ve sdílení.
  - **Jen Android s launcherem, který připnutí dovolí** (`isRequestPinShortcutSupported`);
    jinde (iOS, desktop) se položka nenabízí (`ShareTargetPublisher.canPin`).
  - **Potvrzení je dialog launcheru**, aplikace se nedozví, jestli ho uživatel přijal — proto po
    ťuknutí žádná hláška. Na fotku se čeká až 5 s (u zkratky ke sdílení 2 s): na ploše zůstane
    napořád a uživatel o ni výslovně požádal.
  - Ověřeno na emulátoru (Pixel Launcher, chat s botem): dialog s fotkou bota, zástupce na
    ploše s odznakem Plume (`dumpsys shortcut` → `Pinned: chat_…`), ťuknutí otevřelo chat.
    Položka v ⋮ profilu vidět je.
- **„Vymazat historii…" je v nabídce taky, ne jen v ⋮ konverzace** — chat se vyprázdní a zůstane
  v seznamu. Otevře TENTÝŽ `ClearHistorySheet` (celá historie nebo vybrané dny, „i pro
  protějšek"), dvě podoby téhož kroku by se rozešly; pravidla jsou v `konverzace-akce.md`,
  „Vymazání historie".
  - **Práva se zjišťují až při otevření nabídky** (`ConversationRepository.clearHistoryRights`,
    `getChat` → `Chat.canBeDeleted…`), jako blokování a složky: `ChatSummary` je nenese a ptát
    se dopředu by znamenalo dotaz za každý chat. Dokud nedorazí, položka není.
  - **Uložené zprávy ji nemají** — stejně jako v konverzaci se tam maže po tématech.
  - Stojí hned nad „Smazat chat" a má jeho chybovou barvu: obojí je nevratné a liší se jen tím,
    jestli chat v seznamu zůstane. Bez „Vrátit": historii odložit nejde tak, aby ji konverzace
    mezitím neukazovala (zprávy by musela schovávat i otevřená konverzace).
  - Ověřeno na emulátoru (chat s botem): položka v nabídce, sheet s volbami. Samotné vymazání
    z nabídky na emulátoru NEPROBĚHLO (skutečný účet); volání je totéž jako v konverzaci.
- **Smazání a opuštění mají „Vrátit" — smazání se proto ODKLÁDÁ** (`PendingChatDeletes`, testy
  `PendingChatDeletesTest`). Zpětně se vrátit nedá nic: z opuštěné skupiny se zpátky dostane
  jen pozvánkou a smazanou historii nic neobnoví. Chat po potvrzení hned zmizí ze seznamu
  (`ChatListState.without`), snackbar „Chat smazán" / „Chat opuštěn" (a plurály u výběru víc
  chatů) nabídne „Vrátit" a `deleteOrLeave` se zavolá až po 8 s — o půl vteřiny déle, než
  běží odpočet ve snackbaru, aby „Vrátit" na poslední chvíli ještě stihlo.
  - **Snackbar ukazuje ubíhající odpočet** (`CountdownSnackbarVisuals`, `CountdownSnackbarHost`
    v `core/ui`): kroužek, který se plynule vyprázdní, a v něm zbývající vteřiny. Původních
    5 s s krátkým snackbarem (4 s) bylo málo — při ověřování na emulátoru snackbar zmizel dřív,
    než se na „Vrátit" ťuklo, a chat se opravdu smazal. Odpočet běží od chvíle, kdy je snackbar
    VIDĚT, a zavírá ho sám (délka `Indefinite`); předchozí snackbar se proto zavře, jinak by
    smazání doběhlo, zatímco „Vrátit" čeká ve frontě. Každý `SnackbarHost`, kam může přijít,
    kreslí přes `CountdownSnackbarHost` (seznam chatů, archiv, sbalený seznam).
  - **Vlastní obsah snackbaru si okraj 12 dp přidává sám.** `Snackbar(data)` ho dává
    v sobě (`modifier.padding(12.dp)`), přetížení s obsahem ne — první verze odpočtu proto ležela
    přes celou šířku displeje. Písmo zprávy i akce je výchozí M3 (`bodyMedium`, `labelLarge`);
    číslice v kroužku má řádek oříznutý na výšku písma (`LineHeightStyle.Trim.Both`), jinak sedí
    níž, a kroužek je bez mezery mezi průběhem a stopou (`gapSize = 0.dp`).
  - **Odkládač je jeden na aplikaci a běží ve scope aplikace**: hlavní seznam i archiv schovávají
    tytéž chaty a smazání doběhne, i když se archiv mezitím zavře (jeho ViewModel zaniká).
  - **Zánik procesu ve lhůtě smazání zruší** — chat zůstane. Opačná chyba by byla horší.
  - **Smazaný chat zůstává schovaný ještě 3 s po odpovědi**: update, který ho ze seznamu odebere,
    může dorazit až po ní a chat by jinak na okamžik vyskočil.
  - **Chyba chat vrátí a ohlásí** přes seznam, který zrovna žije (`PendingChatDeletes.failures`).
  - **Se spamem (nahlásit, zablokovat) se maže hned, bez „Vrátit"**: nahlášení i blokování
    proběhnou předem a vrátit nejdou, takže odložené smazání by slibovalo víc, než umí.
  - Potvrzovací dialog zůstává: „Vrátit" je pojistka proti omylu, ne náhrada věty o následku.
- **Ztlumení se nastavuje celým objektem `ChatNotificationSettings`**, ne jedním polem. Ostatní
  hodnoty se musí přenést z aktuálního stavu chatu, jinak by se uživateli tiše přepsal třeba
  vlastní zvuk. `useDefaultMuteFor = false` je nutné: dokud je `true`, jede chat podle globálního
  nastavení a `muteFor` se ignoruje. Ztlumení „navždy" je `Int.MAX_VALUE` sekund.
- **„Ztlumit" se v nabídce rozbalí na doby** (hodina, 8 hodin, 2 dny, navždy), přímo v sheetu,
  ne v dalším — jsou to varianty jedné položky. Stejné doby jako v profilu chatu. Ztlumený chat
  má jen „Zrušit ztlumení"; zapisuje výslovnou nulu, ne návrat k výchozímu (ten je v profilu),
  takže projde i ztlumeným rozsahem.
- **Stav se po akci nepřepisuje lokálně.** U reakcí v konverzaci to smysl dávalo (mezi ťuknutím
  a potvrzením je vidět prodleva), tady se položka jen přeskládá — a předběhnout server by
  znamenalo riskovat, že se seznam rozejde se skutečností. Potvrzení chodí updaty
  (`chatNotificationSettingsUpdates`, `chatIsMarkedAsUnreadUpdates`, `chatPositionUpdates`).
- **Limit připnutých chatů je serverový** (`pinned_chat_count_max`, Premium ho zvedá). Při
  překročení vrátí TDLib chybu, která MUSÍ jít vidět — jinak to vypadá, že se nic nestalo.
  Proto má seznam chatů `SnackbarHost`.
- **Pořadí připnutých chatů se mění režimem uspořádání** („Změnit pořadí připnutých" pod
  „Odepnout" v nabídce nad připnutým chatem, od dvou připnutých; `PinnedReorder`, pravidla
  v `PinnedReorder.kt`, testy `PinnedReorderTest`). Tentýž režim jako u témat: hlavička
  s ✕ a „Hotovo", jen připnuté chaty s úchytem, zpět ho zruší bez uložení. Platí pro složku,
  ve které se skládá — hlavní seznam, archiv i složku Telegramu (`setPinnedChats` s jejím
  `ChatList`).
  - **`setPinnedChats` bere seznam jako NOVÝ STAV připnutých, ne jako přeskládání** — chat,
    který v něm chybí, odepne, chat navíc připne. Odesílá se proto vždycky CELÝ seznam
    připnutých a srovnaný s tím, co je připnuté teď (`pinnedOrderToSave`): mezitím odepnutý
    chat (jiné zařízení) se znovu nepřipne a nově připnutý zůstane nahoře.
  - **Na rozdíl od témat nese pořadí `order`**: nové pořadí přijde zpátky updaty pozic a seznam
    se přeskládá sám, nic se nedrží lokálně. Ověřeno i po `force-stop`.
  - **Ověřeno na emulátoru** ve složce „Plume test" (hlavní seznam testovacího účtu připnuté
    nemá): připnutí bota a testovací skupiny, přetažení, „Hotovo", pořadí po restartu, zpět
    bez uložení, položka se u jediného připnutého nenabízí; obojí se pak odepnulo. Archiv
    a hlavní seznam jdou toutéž cestou, ověřené zvlášť nejsou. **iOS jen přeložené.**
- **`Card(onClick = …)` umí jen krátké ťuknutí.** Dlouhý stisk potřebuje `combinedClickable`,
  takže se z karty stal `Surface` s vlastním klikáním.
- **Otevřený sheet je stav obrazovky, ne ViewModelu: `remember` v composable, které ho kreslí**
  (`ChatSheets` pro nabídku nad chatem, odložení a vymazání historie; příznaky v
  `ChatListScreen` a `ArchiveScreen` pro ostatní). ViewModel dává data (`actionDetails`,
  `folderSuggestions`) a akce, a jednorázové výsledky akcí pro snackbar (`ChatActionsState`) —
  akce doběhne, až je sheet zavřený, a výsledek na něm viset nesmí.
  - **Proč:** seznam je kořen zásobníku Chatů, takže jeho ViewModel ani uložený stav
    (`rememberSaveable`) nezanikají nikdy. Otevřená nabídka nad chatem, přes kterou přijela
    konverzace zvenčí (sdílení screenshotu do Uložených zpráv, notifikace, odkaz — přijde
    i s aplikací na pozadí), zůstala „otevřená" a vrátila se s prvním složením seznamu: **už
    v náhledu gesta zpět**. `ModalBottomSheet` je samostatné okno nad aktivitou, gesto mu
    propadlo, náhled se zrušil, seznam zmizel i se sheetem a znovu dokola — z konverzace nešlo
    odejít, jen to problikávalo. `remember` zanikne s odchodem seznamu z kompozice sám.
  - **`rememberSaveable` ne, ani kvůli otočení:** otočení aktivitu znovu nevytváří
    (`configChanges` v manifestu) a rozložení Foldu obsah položky jen přesune, takže `remember`
    obojí přežije. Navíc by `rememberSaveable` přidal jen obnovu po zabití procesu a změně
    jazyka — a právě i ten nechtěný návrat po odchodu ze zásobníku. Vynulovat ho při odchodu
    nejde: `SaveableStateHolder` ukládá v `onDispose` vlastního efektu, který leží ZA obsahem,
    takže doběhne dřív než efekty obsahu (zapomínají se v obráceném pořadí) a uloží ještě `true`.
  - **Výjimka je průvodce nahlášením** (`ReportFlow`): kroky určuje server a čeká se na odpověď,
    takže žije ve ViewModelu a s odchodem seznamu se ruší ručně (`cancelReportOnLeave`
    z `DisposableEffect` v `ChatActionsHost`).
  - **Sheet, který je místem sám o sobě, je navigační destinace** (otevírá se z víc míst, obsah
    popíše klíč, výsledek nevrací): Můj QR kód, Záložky a Přehled konceptů jsou `SheetRoute`,
    viz „Sheety jako navigační destinace" v `navigace.md`. Kontextová nabídka a kroky akce
    (odložení, vymazání historie) destinacemi nejsou — vracejí výsledek a nesou dočasný stav
    obrazovky.
  - Hlídání životního cyklu položky (`RESUMED`, jako u příběhů) by tenhle případ nechytilo:
    sdílení přijde, když je aplikace na pozadí, takže položka i aktivita jsou zastavené obě.
  - **Na zařízení zatím neověřeno.** Reprodukce: podržet chat (nabídka otevřená), screenshot,
    z náhledu screenshotu sdílet do Uložených zpráv přes Plume, odeslat, gesto zpět.

### Odložení chatu

„Odložit" v nabídce nad chatem (za hodinu, dnes večer, ráno / zítra ráno) chat do zvolené doby
ztiší a pak ho vynoří nahoře v seznamu jako připomínku (`ChatSnoozes` v `core/snooze`, uložené
v `AppSettings`; doby jsou čisté funkce v `SnoozeOption.kt`, testy `SnoozeTest`, `ChatSnoozesTest`).

- **Jen v zařízení.** Telegram nic jako odložení chatu nemá; ztlumení je něco jiného (vidí ho
  všechna zařízení a nevynoří chat zpátky). Účet je id Uložených zpráv, jako u záložek zpráv
  (viz „Záložky zpráv" v `konverzace-akce.md`), takže se odložení mezi účty nepromíchají.
- **Spící chat v seznamu ZŮSTÁVÁ, jen ztichne**: jméno bez tučnosti, čas bez akcentu, odznaky
  v neutrální barvě (`outlineVariant`) a místo náhledu „Odloženo do 20:00". Kdyby
  zmizel, uživatel by ho nenašel ani pohledem, a to je víc, než si řekl.
- **Notifikace spícího chatu přijdou potichu**, ne vůbec — stejné pravidlo jako u tichých hodin
  (viz „Tiché hodiny" v `notifikace.md`): zprávu, kterou TDLib považuje za doručenou, znovu nevydá.
  Jen Android; na iOS notifikace kreslí systém.
- **Vynořený chat má vlastní blok nad připnutými** (`chatGroups(…, due)`), s ikonou odložení
  v akcentu na místě připínáčku. Připomínka, která by zůstala tam, kde chat stál, by se ztratila.
  Blok zmizí **otevřením chatu** — ze seznamu i odjinud (notifikace, hledání), proto to hlídá
  i `ConversationViewModel`. Nahlédnutí do chatu, který ještě spí, odložení neruší.
- **Doby jsou v místním čase a nabídka se mění s denní dobou**: „Dnes večer" (20:00) jen do 18:00,
  po půlnoci do 6:00 je „Ráno" dnešní (8:00), jinak „Zítra ráno". U každé volby je vidět, kdy se
  chat vrátí — „Za hodinu" záleží na tom, kolik je.
- **Vynoření se přepočítává po nejvýš minutě**, ne jedním `delay` do konce odložení: monotónní
  hodiny korutin na Androidu během hlubokého spánku zařízení stojí a odložení do rána by se
  vynořilo o noc později.
- **Ověřeno na emulátoru** (Fold, česky) v chatu Notifikátor: odložení na hodinu a do večera,
  ztišený řádek, tichá notifikace (`flags=…|SILENT`, `notification_alert` bez zvuku) a po zrušení
  odložení zase se zvukem, „Zrušit odložení" s časem. Vynoření se ověřilo přepsáním konce
  odložení v souboru nastavení na minulost (`run-as`, číslo stejné délky, aby protobuf zůstal
  platný) — systémový čas emulátoru `shell` posunout nesmí. **iOS jen přeložené.**
- **V čase vynoření přijde upozornění** (`SnoozeReminders`): bez něj by se připomínka ukázala, až
  když uživatel aplikaci sám otevře. Android plánuje úlohu WorkManageru (`SnoozeReminderWorker`,
  jedna na chat, `REPLACE`) s vlastním kanálem „Odložené chaty" — připomínku si uživatel
  naplánoval sám a může ji chtít jinak než zprávy. iOS lokální notifikaci se stejným `userInfo`
  jako push, takže ťuknutí otevře chat touž cestou. Zrušení odložení i otevření vynořeného chatu
  upozornění zruší, i když už visí v liště; nahlédnutí do spícího chatu ne.
  - **Název chatu se ukládá při odložení**: úloha může proces spustit sama a TDLib se v tu chvíli
    ptát nedá.
  - **WorkManager čas nedodrží na minutu** (Doze). Přesný alarm by chtěl oprávnění
    `SCHEDULE_EXACT_ALARM`; chat se v seznamu vynoří přesně a upozornění pár minut po něm vadí míň.
  - **Na iOS úklid notifikací přečtených chatů připomínku nesmí sebrat** — přichází k chatu,
    který bývá přečtený. Pozná se podle identifikátoru (`SNOOZE_REMINDER_PREFIX`).
  - **Intent upozornění nese chatId i v datech** (`plume-snooze://chat/<id>`): s pevným
    request code by se intenty dvou chatů podle `filterEquals` slily a otevíral by se jen jeden.
  - Ověřeno na emulátoru: úloha v `dumpsys jobscheduler`, předčasné spuštění `cmd jobscheduler
    run -f -n androidx.work.systemjobscheduler eu.tintera.plume <id>` ukázalo upozornění
    v kanálu `snooze-reminders`, ťuknutí otevřelo chat; zrušení odložení úlohu odebralo.
- **Odložit jde i tahem** (Nastavení → Chaty → Tah doleva / doprava → Odložit). Doba se tahem
  zvolit nedá, takže tah otevře malý sheet s dobami (`SnoozeSheet`), ne celou nabídku nad
  chatem. Spící chat tah probudí, jako se ostatní akce tahem otáčejí. Ověřeno na emulátoru
  (odložení i probuzení tahem); nastavení se pak vrátilo na archivaci.

### Výběr víc chatů

„Vybrat" v nabídce nad chatem přepne seznam (hlavní, složku i archiv) do výběru: hlavička s ✕,
počtem a „Vybrat vše" místo pilulky hledání, ťuknutí i dlouhý stisk chat přepíná a místo FAB menu
je plovoucí lišta s archivací, připnutím, ztlumením, přečtením, přidáním do složky a smazáním /
opuštěním (`ChatSelectionTopBar`, `ChatSelectionToolbar`). Pravidla jsou čisté funkce
v `ChatSelection.kt` (testy `ChatSelectionTest`).

- **Každé tlačítko lišty i hlavičky má bublinu s popisem** (`WithTooltip`: na dotyku po
  podržení, myší po najetí; u svislé lišty sbaleného sloupce vpravo, jinak nad tlačítkem).
  Šest ikon vedle sebe se poznat dá, jistotu dá text — i u zšedlé akce, kde se uživatel ptá
  nejvíc.
- **Callbacky lišty jsou jedna `ChatSelectionCallbacks` na ViewModel**: lišta stojí na třech
  místech (seznam, archiv, sbalený sloupec) a každá nová akce by jinak přidávala parametr na
  všech třech.
- **Připnutí je přepínač jako ostatní** (připnout, dokud nejsou připnuté všechny); připíná se
  v pořadí výběru. Strop připnutých hlídá server: chat nad strop selže s vlastní hláškou a ty
  před ním zůstanou připnuté.
- **„Do složky" je nabídka složek** (nad tlačítkem, lišta je u spodku); ťuknutí přidá do složky
  celý výběr. Jen kde složky jsou a jdou upravovat (`folderRepository`).
- **Smazání / opuštění je jediná nevratná akce lišty — chce potvrzení** (`ConfirmDialog`,
  destruktivní), s větou podle obsahu výběru: soukromé chaty se smažou jen u nás, ze skupin
  a kanálů se odejde, nebo obojí. Operace je tatáž jako u jednoho chatu (`deleteOrLeave`). Jen
  skupiny ve výběru → ikona, nadpis i tlačítko „Opustit". **Uložené zprávy ve výběru mazání zakážou**
  (zšedne, jako archivace): jejich smazání by vymazalo všechno uložené, a to hromadně, mimo
  nabídku nad nimi samými. Volby proti spamu (nahlásit, zablokovat) má jen nabídka nad jedním
  chatem — hromadně by šly mimo kontext.
- **„Vybrat vše" vybere jen načtené chaty** (`withAll`, bez sponzorovaných) — seznam se dotahuje
  po stránkách a vybrat chaty, které uživatel neviděl, by bylo překvapení; když jsou vybrané
  všechny, tlačítko zšedne.

- **Vstup je položka nabídky, ne dlouhý stisk** — ten patří nabídce nad chatem, stejně jako
  u výběru zpráv. Uvnitř výběru dlouhý stisk jen přepíná: nabídka nad jedním chatem by z výběru
  vytrhla.
- **Každá akce je přepínač podle VŠECH vybraných** (jako v Gmailu): archivovat, dokud nejsou
  všechny v archivu, ztlumit, dokud nejsou všechny ztlumené, přečíst, když je aspoň jeden
  nepřečtený. Posílá se jen chatům, kterým se něco změní (`archiveTargets`…) — jinak by „Vrátit"
  po archivaci vracel i to, co se nehnulo.
- **Po akci výběr končí** a archivace má tentýž snackbar s „Vrátit" jako u jednoho chatu
  (`ArchiveChange` nese seznam chatů, plurál „2 chaty jsou v archivu") a zavře jejich otevřené
  konverzace. Chaty jdou postupně a selhání jednoho ostatní nezastaví.
- **Uložené zprávy a chat Telegramu ve výběru archivaci zšednou, nezmizí** (archivovat je nejde,
  `canBeArchived`; vracení z archivu zůstává); sponzorovaný
  chat vybrat nejde vůbec — není uživatelův a má vlastní nabídku.
- **Značka výběru je u KAŽDÉHO chatu** (prázdné kolečko / fajfka v rohu avataru, místo tečky
  „online"), ne jen barva vybraného řádku: u jediného vybraného by barva neřekla, že jdou
  vybírat i další. Ve výběru barvu řádku nese výběr, ne chat otevřený v detailu vedle.
- **Tah do archivu je ve výběru vypnutý**, zpět výběr ukončí (`NavigationBackHandler`),
  přepnutí složky taky. Chat, který ze seznamu odejde, z výběru vypadne (`within`).
- **Ve sbaleném sloupci vedle konverzace výběr jde taky** — svislou lištou místo vodorovné;
  viz „Táhlo, kotvy a sbalený seznam" v `docs/poznatky/navigace.md`.
- **„Označit jako přečtené" shazovalo jen ruční příznak** (`toggleChatIsMarkedAsUnread(false)`)
  a chat s nepřečtenými zprávami zůstal nepřečtený — i z nabídky nad jedním chatem. Teď jde přes
  `ChatRepository.readChat`: nejnovější zpráva `viewMessages` se zdrojem `MessageSourceChatList`
  a `forceRead` (přečte i všechno před ní), zmínky, reakce a příznak zvlášť.
- **Odstup lišty patří DOVNITŘ `AnimatedVisibility`**, ne na její modifikátor: skrytá lišta by
  jinak nechala nad FAB (a v archivu nad spodkem seznamu) 24 dp prázdna.
- **Ověřeno na emulátoru** ve složce „Plume test" a v archivu (česky): výběr dvou chatů, zšedlá
  archivace s Uloženými zprávami, ztlumení a zrušení ztlumení, archivace se snackbarem
  „2 chaty jsou v archivu", vrácení výběrem z hlavního seznamu i z archivu, označení jako
  nepřečtené a přečtení dvou skutečných zpráv od bota (odznak zmizel, notifikace taky), zpět
  ukončí výběr. **Nejisté je čtení skupiny s tématy** (nepřečtené se tam vedou po tématech
  a nejnovější zpráva patří jen jednomu) **— neověřené, stejně jako iOS.**

### Společný seznam víc účtů

Režim „Všechny účty" (zapíná se v menu účtu, fotka v pilulce je pak složená z fotek účtů); celé
rozhodnutí je v `docs/navrh-vice-uctu.md`
(„Společný seznam chatů", krok 4 v „Postup"). Co se tu dá snadno rozbít:

- **Id chatu je číslo jednoho účtu.** Soukromý chat má id protějšku a společná skupina totéž id
  v obou účtech, takže klíč položky (`MergedChat.key`), vybraný řádek (`selectedChatSlot`),
  klíče letu avataru a názvu i hledání v zásobníku (`belongsTo`) nesou účet. Kdo přidává další
  porovnání podle id chatu, přidá i slot.
- **Uložené zprávy jsou id vlastního účtu**, a chat druhého účtu s ním má právě tohle id. Proto
  `LocalSavedMessagesChatId` podává `AccountScope` za svůj účet, ne shell; jinak se chat VT s VT2
  ukázal jako „Uložené zprávy".
- **Řádek patří ViewModelu svého účtu** (`ChatRowsContext`): akce nad chatem, tah, odložení,
  barvy a příběhy. Sdílený řádek je `ChatListItem`, ať seznam jednoho účtu a společný seznam
  nekreslí řádek každý jinak.
- **Výběr víc chatů a uspořádání připnutých jen v jednom účtu**: po dobu výběru seznam ukazuje
  jen chaty toho účtu.
- **Výsledek hledání nese účet v `ChatSummary.slot`** (`mergedSearchState`, krok 8 návrhu).
  Řádky výsledků, které nemají slot (návrhy, reklamy), patří účtu obrazovky. Kdo přidává do
  výsledků další druh řádku, předá slot do ťuknutí i do klíče (`resultKey`) — veřejný chat
  i zprávy ze společné skupiny se jinak srazí. **Chat, který má víc účtů, je mezi vlastními
  výsledky jednou** (týž `mergeKey` jako seznam, krok 29 návrhu); jeho účty nese
  `ChatSearchState.resultAccounts` a odznaky kreslí `ResultAvatar` každý v kontejneru svého účtu.
- **Sbalený sloupec má režim Vše taky** (`CollapsedAccounts`): bez něj by po sbalení táhlem
  zmizely chaty ostatních účtů, i když je pilulka ukazuje jako „Všechny účty".
- **Klíče sdílených prvků nesou účet i instanci shellu** (`chatAvatarKey`, `chatTitleKey`,
  `chatContainerKey`, `sharedKey`): výměna výchozího účtu skládá shell znovu a starý i nový
  seznam se ve snímku výměny potkají v jednom `SharedTransitionLayout`. Se stejnými klíči
  aplikace padala (`docs/navrh-vice-uctu.md`, krok 15). Kdo přidává sdílený prvek, bere klíč
  přes `sharedKey`.
- **Chat, který má víc účtů, je jeden řádek** (`MergedChat.others`, klíč `shared:<mergeKey>`,
  kroky 24 a 25 návrhu): společná skupina nebo kanál, soukromý chat a Uložené zprávy. Kdo
  porovnává řádek s otevřeným chatem, bere všechny jeho výskyty (`MergedChat.isOpen`
  přes `instances`) i s id chatu — Uložené zprávy ho mají v každém účtu jiné a po přepnutí účtu
  v konverzaci by zvýraznění v seznamu zhaslo. Odznaky dává `MergedChat.accounts`, akce dál
  ViewModel účtu, kterým se řádek otevírá.
- **Archiv má ViewModel za účet A složku** (klíč `chats-all-archive-<slot>`): sdílet ho
  s hlavním seznamem by přepínalo složku pod rukama.

### Widget na ploše (Android)

Widget s chaty (`ChatsWidget`, Glance, `core/widget` v `androidMain`): připnuté a nepřečtené
chaty napřed, pak zbytek seznamu do dvanácti (`widgetChats`, testy `WidgetChatsTest`), počet
nepřečtených bez ztlumených v záhlaví. Ťuknutí na řádek otevře chat, záhlaví aplikaci. Přidat jde
z Nastavení → Vzhled → „Přidat widget na plochu" (`requestPinAppWidget`, dialog launcheru) i z
nabídky widgetů launcheru.

- **Podklady jsou v souboru a ve stavu widgetu, ne v dotazu při kreslení.** Launcher widget
  kreslí i bez běžícího procesu a TDLib se ptát nedá. Zapisuje je `ChatsWidgetUpdater` (eager
  v Koinu, od startu procesu) po vteřině klidu ze živého seznamu chatů.
- **Obnova přes `currentState`, ne čtením souboru v `provideGlance`.** Glance drží kompozici
  widgetu po obnově ještě ~45 s otevřenou a další `update` v tu dobu jen překreslí tutéž
  kompozici; podklady přečtené jednou před `provideContent` zůstaly staré (zjištěno na emulátoru:
  zpráva od bota byla v souboru, widget ji neukázal). Updater proto zapisuje do
  `PreferencesGlanceStateDefinition` každého widgetu a kompozice si stav přečte znovu; soubor
  slouží jen prvnímu kreslení nového widgetu.
- **Se zámkem aplikace bez náhledů zpráv** — jen jména a počty. Widget vidí každý, kdo drží
  telefon, a zámek, který chrání obsah chatů, by měl díru hned vedle ikony aplikace.
- **Ťuknutí na řádek ukazovalo hlášku „odkaz nejde otevřít"**: intent řádku nese data
  `plume-widget://chat/…` (jinak by se `PendingIntent` řádků slily) a `MainActivity.receiveLink`
  bral jakákoli data z `ACTION_VIEW` jako odkaz na Telegram. Bere teď jen `isTelegramLink`.
- **S víc účty má řádek odznak účtu** (fotka účtu nebo iniciála v kroužku barvy pozadí)
  a klíč řádku nese účet (`WidgetChat.itemId`) — týž chat ve dvou účtech má stejné id.
- **Updater je jeden přes všechny účty** a ukazuje chaty podle filtru seznamu (režim Všechny
  účty → `mergedWidgetChats`). Updater za každý účet si soubor, stav widgetu i miniatury
  přepisoval (`docs/navrh-vice-uctu.md`, krok 16).
- **Odhlášení widget vyprázdní** („Přihlas se v aplikaci Plume"). Zapisuje se jen v jasných
  stavech (přihlášeno a seznam načtený, nebo odhlášeno) — při startu, než se TDLib rozběhne,
  zůstává poslední známý stav, jinak by widget při každém studeném startu problikl prázdnem.
- **Texty se překládají před `provideContent`** (`getString`): Glance skládá vlastní kompozici
  bez prostředí Compose Resources a `stringResource` v ní nefunguje. Čas poslední zprávy proto
  formátuje systém (`DateFormat.getBestDateTimePattern`), počítá se v kompozici.
- **Intent řádku nese chatId i v datech** (`plume-widget://chat/<id>`), jinak by se intenty řádků
  podle `filterEquals` slily a všechny by otevřely týž chat.
- **Fotky chatů jsou kulaté miniatury připravené při zápisu podkladů** (`WidgetAvatars`,
  `filesDir/widget/avatars/<fileId>.png`, 128 px). Launcher widget překresluje často a dvanáct
  dekódování plné fotky by ho brzdilo; Glance navíc obrázek do kruhu oříznout neumí, takže je
  ořez hotový v souboru. Fotku stáhne TDLib, pokud ještě není (je malá), čeká se na ni nejvýš
  5 s — widget s iniciálou je lepší než widget, který se neobnoví. Jméno podle `fileId`: nová
  fotka chatu má nové id, stará miniatura se nepoužije omylem; nepoužívané se po zápisu smažou.
  Chat bez fotky má iniciálu na `primaryContainer`. Barvy z `GlanceTheme` (Android 12+ z tapety).
- **Uložené zprávy se záložkou a svým jménem**, ne fotkou a jménem účtu — jako v seznamu chatů.
  Objeví se jen připnuté (`widgetChats`); na zařízení to ověřené není (testovací účet je
  nepřipíná a připnutí by se propsalo na všechna zařízení).
- **Náhled ve výběru widgetů** (`previewLayout`, Android 12+): kostra se záhlavím a řádky bez
  skutečných chatů, barvy ze systémové palety (světlá i tmavá varianta). Náhled vidí kdokoli
  u telefonu, nic z účtu do něj nepatří. Jen prvky povolené v RemoteViews — holý `View` mezi
  nimi není, bloky jsou `FrameLayout` s pozadím.
- **Ověřeno na emulátoru** (Fold, česky): přidání z Nastavení přes dialog launcheru, widget
  s chaty a odznaky (ztlumený šedě), zpráva od testovacího bota posunula Notifikátor nahoru
  s počtem 2 během pár vteřin, ťuknutí otevřelo chat, se zapnutým zámkem zmizely náhledy a po
  vypnutí se vrátily; widget pak odebrán. **iOS widget nemá.**

