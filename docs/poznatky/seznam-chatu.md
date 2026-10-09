# Seznam chatů

Poznatky k jedné oblasti aplikace. Obecná pravidla projektu a rejstřík ostatních souborů jsou
v [CLAUDE.md](../../CLAUDE.md); poučení z opravy v téhle oblasti patří sem.

## Seznam chatů

Položky nejsou samostatné karty, ale **skupiny se společným zaoblením** — vzor, kterým M3
Expressive nahrazuje karty per položku. Připnuté mají vlastní skupinu vždycky, nepřečtené jen
když si o to uživatel řekne v Nastavení (`separateUnreadChats`, **výchozí je vypnuto**).

- **Zaoblení nese krajní položka skupiny**: velké nahoře u první, velké dole u poslední, malé
  (4 dp) mezi nimi. S rozestupem 2 dp uvnitř skupiny a 14 dp mezi skupinami z toho vznikne blok.
  Tvary a rozestupy jsou v `core/ui/GroupedList.kt` — týmž jazykem mluví i profil chatu
  a výběr cíle přeposlání, a tři kopie konstant by se rozešly.
- **Vybraná položka je zaoblená celá**, jako skupina sama (dvoupanel, v seznamu chatů i sekcí
  nastavení). Vyčlení se tím z bloku — tvarem, nejen barvou. Sousedé si svá malá nároží nechávají.
- **Zaoblení se animuje.** Skupiny se přeskládávají za běhu — přečtený chat přeskočí
  z nepřečtených mezi ostatní — a tvary jeho sousedů se tím mění. Skoková změna by při
  současném `animateItem()` působila jako chyba vykreslení.
- **Prázdné skupiny se zahazují**, jinak by po nich zbyla mezera. Bez připnutých a bez
  nepřečtených je z celého seznamu zase jeden souvislý blok.
- **Pořadí uvnitř skupiny zůstává od TDLib.** Je to pořadí, na které je uživatel zvyklý
  z ostatních klientů; přeskládávat ho by znamenalo rozhodovat za něj.
- **Důsledek, se kterým se počítá:** přečtení chatu ho přesune ze skupiny nepřečtených dolů mezi
  ostatní. Při návratu z konverzace tedy chat nezůstane tam, kde na něj uživatel klikl. Právě
  tohle je důvod, proč je vyčlenění volitelné a **výchozí vypnuté**: platí se za něj tím, že
  seznam pod prstem uskočí, a vyplatí se to až u hodně chatů.
- **Pravidlo je čistá funkce `chatGroups`** (`ChatGroups.kt`, testy `ChatGroupsTest`), ne
  `private fun` v obrazovce. Hraniční kombinace (samé připnuté, žádné nepřečtené, prázdný
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

Seznam chatů zužují JEN složky Telegramu v řádku pod pilulkou (viz „Složky chatů"). Dole je
FAB menu „Vytvořit" (`NewChatFabMenu`): přepínací FAB s tužkou a nad ním Nový kanál, Nová
skupina a Nová zpráva.

- **Dřív tu byl plovoucí toolbar s vlastními filtry** (Vše, Nepřečtené, Lidé, Skupiny, Kanály;
  `ChatFilter`), lokálními a kombinovatelnými se složkou. Dělaly ale totéž co doporučené složky
  Telegramu („Nepřečtené", „Osobní"…) — a s nimi přidanými stály v aplikaci dvě řady téhož nad
  sebou. Uživatel to vyhodnotil jako duplicitu a zrušily se. Co se tím ztratilo: kombinace
  „složka × filtr" (nepřečtené uvnitř Práce) a filtry pro toho, kdo složky nemá. Náhrada je
  „+" na konci řádku složek, který doporučené složky nabídne na jedno ťuknutí.
- **„+" otevře sheet** (`FolderSuggestionsSheet`, stav `ChatsViewModel.folderSuggestions`):
  doporučené složky (`getRecommendedChatFolders`, čtou se při každém otevření), pod nimi „Nová
  složka" a „Spravovat složky" do Nastavení → Obecné → Složky chatů
  (`AppNavigation.openChatFolders`, zpět po krocích jako u proxy). Přidaná doporučená složka
  se rovnou VYBERE — kdo si přidá „Nepřečtené", chce je vidět.
- **Sheet říká, že se složka synchronizuje** s ostatními klienty. U filtru, který dřív žil jen
  v Plume, by to nikdo nečekal.
- **Bez složek se řádek NEKRESLÍ** (`folderTabs` vrátí prázdný seznam). Dřív v něm stálo
  samotné „Všechny chaty" a „+", aby k doporučeným složkám vedla cesta z hlavního seznamu —
  jenže jediné tlačítko, které nejde odvybrat, není volba, jen řádek místa navíc nad chaty
  u každého, kdo složky nepoužívá. Uživatel ho chtěl pryč. Doporučené složky dál nabízí
  Nastavení → Obecné → Složky chatů; po přidání první složky se řádek objeví i s „+".
  Ověřeno na emulátoru (česky): účet bez složek bez řádku, přidaná doporučená „Unread" v Nastavení
  řádek ukáže, a když se smaže, zatímco je vybraná, řádek zmizí a seznam je zase celý.
- **„+" je mimo spojenou skupinu** (`OutlinedIconButton` s odstupem): je to akce, ne volba;
  ve skupině by se tvářil jako další složka.
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
- **Vybraná složka se v řádku vždycky ukáže** (`bringIntoViewRequester` u vybraného tlačítka):
  přidaná doporučená složka stojí na konci řádku a vybrala by se za okrajem obrazovky.
- **Server doporučené složky vracet nemusí** — účtu, který už má „Unread" i „Personal", nevrátil
  žádnou. Sheet se pak ověřuje sondou (podstrčená doporučená složka v `openFolderSuggestions`;
  necommituje se), založení jde skutečným `createChatFolder`.
- **Ověřeno na emulátoru** (anglicky): FAB menu — otevření, zavření ťuknutím do seznamu i na
  hlavičku a zpět, všechny tři položky vedou na svou obrazovku a zpět se vrací se zavřeným menu.
- **Ověřeno na emulátoru** (česky, světlý i tmavý motiv): FAB se sbalením, řádek s „+", sheet
  bez doporučených i se sondou, přidání složky (vybraná a dojetá v řádku), prázdná složka
  s ikonou, „Nová složka" do editoru a „Spravovat složky" do seznamu složek, zpět po krocích.
  Testovací složka se pak smazala. **iOS jen přeložené.**

### Složky chatů

Složky Telegramu (`ChatListFolder`) jsou řádek přepínacích tlačítek pod pilulkou hledání:
„Všechny chaty · Práce 3 · Rodina…" (`ChatFolderRow`, pravidla `folderTabs` a `selectedFolderId`
v `ChatFolderTabs.kt`, testy `ChatFolderTabsTest`). Bez složek se řádek nekreslí vůbec
(viz „Složky místo filtrů a FAB").

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
- **Řádek odjíždí spolu s pilulkou.** Scroll behavior nenese `AppBarWithSearch`, ale sloupec
  kolem pilulky i řádku (`Modifier.searchBarScrollBehavior()` je veřejný); bar sám ho nedostane,
  jinak by odjel jen on a řádek zůstal viset nahoře.
- **Jen ťuknutím, ne tahem do strany.** Vodorovný tah po seznamu patří archivaci.
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
v chatech" přes celou šířku, na jejím konci fotka účtu s menu (Uložené zprávy, Archiv,
Nastavení — vzor Gmailu a Google Messages; dřív tu bylo ⋮ vedle pilulky). Po ťuknutí se rozbalí
`ExpandedFullScreenSearchBar` s výsledky.

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
- **Výpadek spojení bar přivolá zpátky** (`scrollOffset` animovaný na 0). Jinak by odjetý bar
  schoval jediné vysvětlení, proč nic nechodí.
- **Sbalený stav barvu nemění** (`scrolledAppBarContainerColor = surface`). Tónovaný pruh přes
  celou šířku byl přesně to, co vadilo na velkém baru.
- **Plochu baru dává i `Modifier.background(surface)`.** `appBarContainerColor` bez scroll
  behavior v material3 1.5.0-alpha22 nenakreslil nic a chaty pod barem prosvítaly.
- **Stav spojení nese fotka účtu a nápověda pilulky** (`ConnectionIndicator` v `core/ui`,
  sdílený s hlavičkou konverzace), ne nadpis ani vlastní prvek. Skoro celou dobu není vidět nic; „Chaty"
  v nadpisu jen opakovalo navigaci. Dřív to byla tonální pilulka s kolečkem na konci hledání —
  ukusovala místo nápovědě a vedle fotky působila jako cizí prvek. V rozbaleném hledání se stav
  neukazuje.
  - **Fotka naroste (30 → 38 dp), zvlní se, po jejím okraji vyjede vlnitý prstenec a obojí se
    točí**; po obnovení spojení je fotka zase malý kruh. Prstenec je OBRYS fotky (tah leží napůl
    přes její okraj), ne rám kolem ní. Dvě slepé uličky: `CircularWavyProgressIndicator` kolem
    kulaté fotky (kruh v zubatém rámu nepůsobil jako celek) a zvlněná zmenšená fotka uvnitř
    prstence — mezera mezi nimi vypadala jako díra. Pilulku zvětšení nerozšíří: slot tlačítka
    má pevných 48 dp a 38 dp se vejde i pod 40dp ořez `IconButton`.
  - **Točí se obrys, fotka ne.** Obrys je ořez ve vrstvě (`graphicsLayer { shape; clip }`),
    obsah pod ním stojí; točit celou fotku by točilo i tvář. Zvlnění, zvětšení fotky i prstenec
    jdou z JEDNOHO průběhu morfu (`morphSpring`), úhel se čte až při
    kreslení — točení nic neskládá. `MorphShape` na to nestačí: otáčí jen spolu s průběhem.
  - **Prstenec a tečka archivu jsou mimo `IconButton`**, ve vrstvách nad ním: tlačítko ořezává
    obsah na 40dp kruh. Tečka je nad prstencem (jinak by ji přeškrtl) a zůstává v rohu
    NEZVĚTŠENÉ fotky, takže na prstenci sedí jako odznak; zvětšovat ji s fotkou by ji vystrčilo
    z ořezu. Zvětšuje se jen `Avatar`, ne obal s tečkou.
  - **Prstenec říká, ŽE je problém, nápověda CO.** Samotný tvar by nerozlišil „Čekání na síť"
    (uživatel může něco udělat) od „Připojování" (stačí počkat).
  - **Čekání na síť se točí POMALU (otáčka za 20 s) a prstenec je v `outline`**, připojování
    a aktualizace za 9 s v `primary`. Úplné zastavení (první návrh: „bez sítě se na nic nečeká,
    rotace by lhala") uživatel na emulátoru odmítl — stojící vlnka působila jako zamrzlá.
    Rychlost se animuje ve stupních za vteřinu a úhel přibývá po snímcích (`withFrameNanos`),
    takže přechod mezi stavy neškubne; smyčka běží jen při výpadku. Kontrast proti pilulce v tmavém motivu: `outline` 3,6 : 1,
    `primary` 9,7 : 1 (práh pro netextové prvky je 3 : 1).
  - **Nápověda se mění fade through, ne `Crossfade`.** Dva texty napůl průhledné přes sebe
    („Čekání na síť" přes „Připojování") se nedají přečíst — tatáž úvaha jako u přechodů
    obrazovek, i s jejich čísly (90 / 210 ms).
  - **Výpadek se ukáže až po 600 ms, návrat hned** (`rememberShownConnection`). Po každém návratu
    z pozadí TDLib na zlomek vteřiny hlásí `Updating` a fotka by se při každém otevření aplikace
    zavlnila. Na emulátoru se tím připojování po vypnutí letového režimu neukáže vůbec.
  - **Vzhled se ověřuje podstrčeným stavem, i bez přihlášení**: v `App.kt` dočasně
    `targetState = true` (shell se složí i nepřihlášený), v `ChatListScreen` pevný `AccountInfo`,
    `ArchiveUnread(2, 1)` (tečka) a `produceState`, který střídá čekání na síť → připojování → `null` po 4 s. Tak jde použít
    čerstvý emulátor, na kterém Plume přihlášená není. Necommituje se.
  - **Čtečce obrazovky stav říká popis tlačítka účtu** („Účet, Čekání na síť…").
- **Hledá se ve všech známých chatech**, ne v načteném seznamu — viz „Hledání chatů" níž.

### Odlehčení: blok je světlejší než pozadí

- **Vrstvení je otočené proti tomu, jak vzniklo.** Původně bylo pozadí `surface` (nejsvětlejší role)
  a řádky `surfaceContainerHighest` o tři stupně tmavší — z položek byly šedé plochy na světlém
  a seznam působil těžce. Teď mají řádky `surfaceContainerLowest`, takže je to vztah „světlý panel
  na tónované stránce". Tím dělá odstup i Google Messages: jeho řádky samy žádnou výplň nemají,
  nese ho rozdíl panelu a pozadí.
- **V tmavém režimu se to obrátí a je to správně.** `surfaceContainerLowest` je tam nejtmavší role,
  takže z panelu bude tmavší plocha na světlejším pozadí. Role drží HIERARCHII, ne konkrétní
  světlost — proto se nesahá po natvrdo zadané barvě.
- **Název jde běžným řezem, polotučně jen nepřečtený.** Když je polotučný každý chat, nevyčnívá
  žádný. Proto `bodyLarge` + `FontWeight.SemiBold` jen podle `hasUnread`, ne `titleMedium` pro vše.
- **Odznak nepřečtených není chybově červený.** M3 `Badge` je ve výchozím stavu `error`; nepřečtená
  zpráva ale není chyba a v odlehčeném seznamu byla ta červená jediná sytá barva na obrazovce.

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
- **Činnost má přednost před náhledem zprávy** a odlišuje se barvou — je to novější informace
  a obojí najednou se do řádku nevejde.
- **Telegram dostupnost záměrně rozmazává.** Kdo si to v soukromí vypne, je vidět jen jako
  „naposledy nedávno" a přesný čas nedostaneme. Proto je `Presence` několik pojmenovaných stupňů,
  ne čas — dopočítávat minutu, kterou neznáme, by bylo horší než neurčitost.

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
  Uložené zprávy archivovat nejdou, nabídka je u nich nemá.
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
  Uložené zprávy archivovat tahem nejdou, stejně jako nemají položku v nabídce.
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
- **Bez tahu se podklad nekreslí VŮBEC, ani jeho plocha.** Stejný tvar nestačí: vyhlazené hrany
  řádku jsou poloprůhledné a barevný podklad pod nimi prosvítal jako světlý lem kolem každého
  chatu, v tmavém tématu na skoro černém `surfaceContainerLowest` do očí bijící. Na emulátoru
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
- **„Archivovat chaty od neznámých" povoluje server** (`can_archive_and_mute_new_chats_from_unknown_users`);
  kde ho nepovolí, TDLib zapnutí odmítne. Přepínač se tam proto nenabízí vůbec — přepínač,
  který skončí chybou, je horší než žádný.

### Akce tahem

Nastavení → Obecné → Chaty → „Tah doprava" / „Tah doleva": archivovat, přečteno, ztlumit,
připnout, nebo nic (`ChatSwipeAction`, `AppSettings.chatSwipeActions`). Platí v hlavním seznamu,
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

### Výběr víc chatů

„Vybrat" v nabídce nad chatem přepne seznam (hlavní, složku i archiv) do výběru: hlavička s ✕
a počtem místo pilulky hledání, ťuknutí i dlouhý stisk chat přepíná a místo FAB menu je plovoucí
lišta s archivací, ztlumením a přečtením (`ChatSelectionTopBar`, `ChatSelectionToolbar`).
Pravidla jsou čisté funkce v `ChatSelection.kt` (testy `ChatSelectionTest`).

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
- **Uložené zprávy ve výběru archivaci zšednou, nezmizí** (archivovat je nejde); sponzorovaný
  chat vybrat nejde vůbec — není uživatelův a má vlastní nabídku.
- **Značka výběru je u KAŽDÉHO chatu** (prázdné kolečko / fajfka v rohu avataru, místo tečky
  „online"), ne jen barva vybraného řádku: u jediného vybraného by barva neřekla, že jdou
  vybírat i další. Ve výběru barvu řádku nese výběr, ne chat otevřený v detailu vedle.
- **Tah do archivu je ve výběru vypnutý**, zpět výběr ukončí (`NavigationBackHandler`),
  přepnutí složky taky. Chat, který ze seznamu odejde, z výběru vypadne (`within`).
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
