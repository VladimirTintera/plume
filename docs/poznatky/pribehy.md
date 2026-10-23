# Příběhy (stories)

Prohlížení cizích příběhů: lem kolem avataru v řádku chatu, hromádka autorů v pilulce hledání,
prohlížeč přes celou obrazovku a v něm reakce a odpověď; zveřejnění vlastního příběhu z fotky
i videa (i jen pro blízké přátele), diváci (počet i seznam s reakcemi) a smazání; skrytí autora
do archivu; příběhy v profilu (lem kolem avataru a záložka „Příběhy") i s alby; správa alb
u vlastního příběhu (`docs/roadmap.md`, „Příběhy"). Sdílený příběh ve
zprávě a odpověď na příběh jsou v `typy-zprav.md`.

Skladba:

| Část | Soubor |
| --- | --- |
| Doménové rozhraní, `ActiveStories`, `StoryRef` | `core/telegram/StoryRepository.kt` |
| Nad TDLib (updaty, načtení seznamů, `openStory` / `closeStory`) | `core/telegram/TdlStoryRepository.kt` |
| Lem po tvaru avataru s dílky | `core/ui/StoryRing.kt` (`Modifier.storyRing`) |
| Pořadí autorů a pohyb mezi příběhy (čisté funkce, testy `StoryCursorTest`) | `features/stories/StoryCursor.kt` |
| ViewModel prohlížeče | `features/stories/StoriesViewModel.kt` |
| Prohlížeč | `features/stories/StoriesScreen.kt`, routa `StoriesRoute` |
| Hromádka v pilulce | `features/stories/StoryStack.kt` |
| Lišta s odpovědí a reakcemi, lišta vlastního příběhu, velké emoji, hlášení | `features/stories/StoryReplyBar.kt` |
| Nový příběh (obrazovka, výběr lidí) | `features/stories/StoryComposerScreen.kt`, `StoryComposerViewModel.kt`, routa `StoryComposerRoute` |
| Plátno 1080 × 1920 | `core/ui/StoryPhoto.kt` + `AndroidStoryPhoto`, `IosStoryPhoto`, `DesktopStoryPhoto` |
| Příběhy v profilu (mřížka) | `features/profile/ProfileStories.kt`, záložka `ProfileTab.Stories`, stav `ProfileStories` v `ChatProfileViewModel` |
| Seznam diváků | `features/stories/StoryViewersSheet.kt`, stav `StoryViewersState` ve `StoriesViewModel` |
| Video 720 × 1280 H.265 | `core/ui/StoryVideo.kt` + `AndroidStoryVideo` (Media3 Transformer), `IosStoryVideo` (AVFoundation), desktop `UnsupportedStoryVideo` |
| Správa vlastního příběhu (popisek, viditelnost, profil, připnutí) | `StoriesViewModel` (`setOnProfile`, `setPinned`, `setAudience`, `editCaption`), `features/stories/StoryManageSheets.kt`, `OwnStoryMenu` ve `StoriesScreen.kt` |
| Moje příběhy (v profilu, archiv) | `features/stories/MyStoriesScreen.kt`, routa `MyStoriesRoute`, mřížka `ProfileStoriesGrid` |

## Odkud se ví, kdo má příběh

- **Jen z updatů `updateChatActiveStories`**, proto `TdlStoryRepository` vzniká eagerně
  (`createdAtStart`) — stejný důvod jako u seznamu chatů. TDLib je posílá spolu s načítáním
  chatů; `loadActiveStories` (hlavní seznam i archiv) je jen spouští a 404 znamená „všechno
  načtené". Prázdné `stories` = autor už nic nemá, z mapy pryč.
- **Jméno a fotka autora jsou v `ActiveStories`, ne vypůjčené ze seznamu chatů.** Kontakt, se
  kterým jsme si nikdy nepsali, v seznamu chatů není, ale příběhy má — a hromádka ho ukázat
  musí. Dočítá se jednou `getChat` (TDLib chat zná dřív, než pošle jeho příběhy), pak se drží
  `chatTitleUpdates` / `chatPhotoUpdates`.
- **`list` je `null`** u autora, jehož příběhy nejsou v žádném seznamu (vidět jsou jen u něj).
  Lem v řádku chatu má, do hromádky nepatří a prohlížeč projde jen jeho.

## Lem kolem avataru

- **Jde po TVARU avataru** (kruh, sušenka, čtverec), ne po kruhu: tvar nese druh chatu
  („Tvar avataru podle druhu chatu" v `seznam-chatu.md`) a kruhový lem kolem čtverce by ho
  přebil. Kreslí se obrys téhož `Shape` zvětšený o mezeru a čáru, dílky přes `PathMeasure`.
  Avatar se kvůli příběhu nemorfuje: jeho tvar nese druh chatu a měnit se nesmí.
- **Kreslí se VEN z rozměru avataru** (`drawWithCache`, 5 dp), takže řádek se nezvětší
  a nepohne. Řádek má 12 dp odsazení nahoře i dole, víc než dost. Kdo dá lem jinam, musí mít
  kolem místo, jinak ho ořízne `clip` rodiče.
- **Dílky od horního středu po směru hodinových ručiček, nejstarší první.** Zhlédnuté jsou
  vždycky nejstarší (TDLib drží `maxReadStoryId`), takže šedé tenké dílky jsou na začátku
  a barevné (`primary`) na konci. Začátek obrysu se u každého tvaru liší (`RoundRect` začíná
  vlevo nahoře, polygon jinde) — horní střed se proto hledá vzorkováním cesty a směr podle
  toho, kam cesta z něj vede.
- **Mezera mezi dílky má strop** (45 % dílku): u desítek příběhů by z lemu zbyly tečky.
- **Ťuknutí na avatar otevře příběhy, zbytek řádku chat** (jako oficiální aplikace). Podržení
  avataru dělá totéž co podržení řádku; ve výběru víc chatů avatar přepíná výběr jako zbytek
  řádku. Ripple je neomezený s poloměrem přes lem.
- **Archiv má lem taky** (`ChatListContent` je společný), hromádku ne.

## Hromádka v pilulce

- **Jediný prvek „nahoře"** — řada avatarů přes celou šířku, jak ji mají jiné aplikace, se
  vědomě nedělá (uživatel nechtěl, aby příběhy trčely nad seznamem). Hromádka je až tři
  avatary vedle fotky účtu, bez příběhů zmizí. Slouží hlavně kontaktům bez chatu.
- **Pořadí je pořadí TDLib** (`order`, pak id chatu sestupně) — nezhlédnuté a vlastní příběh
  posouvá dopředu sám server. Ťuknutí otevře prvního autora s nezhlédnutým.
- **Jen v hlavním seznamu chatů** (`ChatsViewModel.storyStack`, `folder == Main`), ne ve složce
  archivu.

## Úsek a obálka videa

Pod náhledem videa je **tentýž pás jako u videa profilovky** (`VideoTrimBar` nad `VideoTrim`,
`core/ui`): rámeček úseku se posouvá tahem, čárka obálky tahem za ni nebo ťuknutím do pásu.

- **`VideoTrim.maxSegmentMs`**: profilovka 10 s (výchozí, beze změny), příběh minuta. Kratší
  video je úsekem celé a posouvat ho nejde — pás pak vybírá jen obálku.
- **Popisky pásu podle použití** (`VideoTrimLabels`): u profilovky je vybraný snímek „fotka"
  (statická podoba), u příběhu „obálka". Pás je bílý na tmavém (dělaný pro ořez), na obrazovce
  nového příběhu proto leží na černé ploše jako plátno příběhu.
- **Náhled nahoře ukazuje vybranou obálku** — tak bude příběh vypadat v profilu a jako náhled.
  Snímek se dočte až po krátké pauze po tahu (150 ms), ne po každém pixelu.
- **Převod začíná na začátku úseku** (`StoryVideo.render(startMs)`: Android
  `ClippingConfiguration.setStartPositionMs`, iOS `insertTimeRange` od `startMs`) a obálka jde
  do `coverFrameTimestamp` v čase OD ZAČÁTKU PŘEVEDENÉHO videa (`VideoTrim.mainFrameSeconds`),
  ne zdroje.

## Správa alb

⋮ u vlastního příběhu v prohlížeči → „Alba…" (jen když `canBeAddedToAlbum`) → panel: alba
autora se zaškrtávátky (příběh v albu ano/ne, `addStoryAlbumStories` / `removeStoryAlbumStories`),
nahoře „Nové album" (`createStoryAlbum` rovnou s tímhle příběhem), u alba ⋮ přejmenovat
(`setStoryAlbumName`) a smazat (`deleteStoryAlbum` po `ConfirmDialog`; příběhy zůstanou).

- **Vlastní profil s příběhy Plume nemá**, proto je správa u příběhu samotného.
- **Pořadí alb** (`reorderStoryAlbums`): od dvou alb řádek „Seřadit alba", který panel přepne
  do řazení — tytéž řádky s úchytem jako u hudby v profilu (`ReorderRow`, `DragReorder`;
  čtečka má „Posunout nahoru / dolů"). Pošle se až „Hotovo", ne po každém přesunu; přeskládá
  se hned a při chybě vrátí.
- **Pořadí příběhů v albu** (`reorderStoryAlbumStories`): ⋮ u alba → „Seřadit příběhy" přepne
  panel na seznam příběhů alba (náhled 9 : 16, popisek, a když není, čas zveřejnění), šipka zpět
  vrátí k albům bez uložení. **Načtou se VŠECHNY stránky alba** (`getStoryAlbumStories` po
  stránkách), ne jen první: TDLib dané příběhy posune na začátek a zbytek nechá za nimi, takže
  řazení z neúplného seznamu by tiše přeházelo konec alba. Posílá se celé pořadí a jen když se
  změnilo; chyba načtení i uložení jde do panelu. Ověřeno na emulátoru se sondou (falešné album
  ze tří kopií aktivního příběhu): přetažení, „Hotovo" poslalo `[23, 21, 22]`, zpět nic.
- **Název má 1–12 znaků** (`STORY_ALBUM_NAME_MAX`): pole víc nepustí a ukazuje počítadlo,
  ViewModel ořízne mezery a zkrátí pro jistotu taky.
- **Zaškrtnutí se přepne hned a při chybě vrátí**; zapisuje se i do `MessageMedia.Story.albumIds`
  stránky prohlížeče, ať po zavření a otevření panelu nesvítí staré.
- **Chyba se ukazuje V PANELU, ne v pilulce prohlížeče.** Panel je samostatné okno
  (`ModalBottomSheet`) a pilulku pod sebou zakryje — v první verzi chyba nebyla vidět vůbec.

## Správa vlastního příběhu

⋮ u vlastního příběhu v prohlížeči: **Upravit popisek…**, **Kdo ho uvidí…**, **Ponechat
v profilu / Odebrat z profilu**, **Připnout v profilu / Odepnout z profilu** a Alba… (testy
ve `StoriesViewModelTest`, `StoryManageTest`). Nabízí se jen to, co TDLib u příběhu dovolí
(`canBeEdited`, `canSetPrivacySettings`, `canToggleIsPostedToChatPage`); připnout jde jen
příběh, který v profilu je.

- **Všechno se přepne hned a při chybě vrátí** (jako reakce), výsledek hlásí pilulka nad lištou.
- **Popisek** (`editStory` jen s `caption`, obsah zůstává): dialog s polem, prázdný popisek
  popisek odebere.
- **Vyměnit fotku nebo video** (`editStory` s obsahem, `StoryRepository.replaceMedia`): výběr
  přílohy a pak TENTÝŽ editor jako u nového příběhu v režimu úprav (`StoryComposerRoute`
  s `editStoryId`, `StoryEditTarget`) — fotka i video se převedou stejně (plátno 1080 × 1920,
  video 720 × 1280), popisek je předvyplněný a ukládá se s médiem. Viditelnost a ostatní volby
  editor v režimu úprav neukazuje, příběh je má svoje. Po návratu prohlížeč příběh dočte znovu
  (`reloadReplaced` v `LifecycleResumeEffect`), jinak by ukazoval staré soubory.
- **Změnit obálku** videa (`editStoryCover`, `StoryRepository.editCover`): TENTÝŽ editor
  v režimu „jen obálka" (`StoryEditTarget.coverOnly`) nad staženým videem příběhu — pás snímků
  na výběr obálky, bez ořezu, výplně a popisku; nic se nepřevádí ani nenahrává, jde jen čas
  snímku od začátku videa. Položka v ⋮ je jen u videa a až je stažené (editor čte snímky
  ze souboru); dokud se stahuje, chybí. **Ověřeno na emulátoru** na video příběhu viditelném
  jen testovacímu botovi (se souhlasem uživatele, pak smazaný): obálka 0:00 → 0:02, v „Moje
  příběhy → Archiv" pak náhled s novým snímkem; prohlížeč pod editorem zůstal otevřený.
- **Pod editorem prohlížeč stojí** (`covered` z životního cyklu položky: pod RESUMED = jiná
  obrazovka nahoře). Bez toho příběh dojel, prohlížeč se zavřel a po uložení nebylo kam se
  vrátit. Čte se `collectAsState`, NE `…WithLifecycle` — to by sběr při zastavení vypnulo
  a přechod by nepoznalo.
  - **Během výběru fotky příběh stojí** (`replaceKey` v `paused`). Výběr fotek na Androidu je
    panel přes aplikaci, ne celá obrazovka, a prohlížeč pod ním dál běžel: příběh dojel,
    prohlížeč se zavřel a vybraná fotka neměla kam dojít — editor se vůbec neotevřel (ověřeno
    na emulátoru). Zrušený výběr nic nevrátí, proto se pauza po návratu uvolní sama za 3 s.
- **Viditelnost** (`setStoryPrivacySettings`): tytéž volby jako u nového příběhu, `ChoiceListItem`
  v panelu. **Výjimky („všichni kromě…") Plume nenastavuje ani neukazuje**, změna je zahodí.
  **Blízcí přátelé jdou vybrat jen s neprázdným seznamem** (`closeFriends`) — jinak by příběh
  neviděl nikdo; pod volbami je pak věta, že se seznam nastaví u nového příběhu. **„Vybraní
  lidé" otevřou výběr lidí** (`StoryAudiencePicker` z editoru, `pickPeople` / `confirmPeople`)
  místo příběhu přes celou plochu, se současným výběrem předvybraným; prázdný výběr nic nezmění
  (příběh by nikdo neviděl). Stejná volba nic neposílá.
- **Ponechat v profilu** (`toggleStoryIsPostedToChatPage`). Příběh vzatý z profilu přestane být
  připnutý (server ho z připnutých vyřadí); prohlížeč to promítne hned i do svého seznamu.
- **Připnutí** (`setChatPinnedStories` s CELÝM seznamem): repository si napřed přečte připnuté
  (vydá je jen první stránka `getChatPostedToChatPageStories`), nový jde na začátek a seznam se
  zastropí `pinned_story_count_max` (`pinnedAfter`). Prohlížeč připnuté načte, až je vlastní
  příběh vidět — podle nich nabídka píše „Připnout" / „Odepnout".
- **Ověřeno na emulátoru** se sondou (necommitovaná): příběh viditelný jen mně
  (`StoryPrivacySettingsSelectedUsers` bez lidí — TDLib ho vezme a příběh nikdo jiný nevidí).
  Popisek „Sonda" → „Sonda upravena" (po novém otevření ze serveru), Ponechat v profilu →
  v „Moje příběhy → V profilu" se špendlíkem po připnutí, odepnutí, odebrání z profilu, smazání.
  Panel viditelnosti ukázal Všichni / Moje kontakty / Vybraní lidé (0 lidí) a větu o prázdném
  seznamu blízkých přátel. **Skutečná změna viditelnosti ověřená NENÍ**: každá jiná volba by
  příběh ukázala kontaktům; testy ji pokrývají. **Výměna média a výběr lidí z prohlížeče
  ověřené na emulátoru** na příběhu viditelném jen testovacímu botovi (se souhlasem uživatele,
  pak smazaný): přechod → pruhy → přechod, prohlížeč po uložení hned ukázal nové médium;
  „Vybraní lidé" otevřel výběr s předvybraným botem, přidání druhého → „Viditelnost změněna"
  a po novém otevření „2 lidé" ze serveru. Přidat tak šli jen boti, kteří příběhy nevidí.

### Příběh za kanál nebo skupinu

Editor nového příběhu má nad viditelností **Zveřejnit jako**: Já a chaty z
`getChatsToPostStories` (`StoryRepository.postTargets`). Vybraný chat se hned ověří
(`canPostStory` → `canPostAs`) a důvod odmítnutí je pod ním; zveřejnit za něj pak nejde.

- **Za chat viditelnost není** (TDLib ji u příběhu za chat ignoruje, vidí ho odběratelé),
  editor ji schová. Ponechat v profilu i snímky obrazovky zůstávají.
- **Věty odmítnutí jsou pro chat vlastní**: obecná „jen s Premium" mluvila o „tomto účtu",
  u skupiny to nedávalo smysl (`story_chat_premium`, `story_chat_boost`).
- **Ověřeno na emulátoru**: seznam ukázal testovací skupiny a kanály účtu, „Plume test fotka"
  hned s větou o Premium (účet ho nemá). Skutečné zveřejnění za chat ověřené není — chce
  Premium nebo boosty.

## Moje příběhy

Menu účtu → **Moje příběhy** (`MyStoriesRoute`, `MyStoriesScreen`): přepínač **V profilu**
(`getChatPostedToChatPageStories`) / **Archiv** (`getChatArchivedStories`, všechny vlastní
i vypršelé) a tatáž mřížka jako záložka „Příběhy" v profilu (`ProfileStoriesGrid`, připnuté se
špendlíkem). Ťuknutí otevře prohlížeč nad mřížkou a příběh se spravuje tam.

- **Jediné místo, kde se k vypršelým vlastním příběhům dostat** — vlastní profil s příběhy
  Plume nemá. Proto menu účtu (vedle Stahování), ne nabídka nového příběhu.
- **Po návratu z prohlížeče se mřížka načte znovu** (`LifecycleResumeEffect`): odebraný z profilu
  nebo nově připnutý příběh se má hned projevit.
- **Přepínač je connected button group** (dvě vzájemně se vylučující volby), jako výběr sítě
  u přenesených dat.

## Skrytí autora do archivu

⋮ v hlavičce prohlížeče → „Skrýt příběhy do archivu" / „Vrátit příběhy mezi ostatní"
(`setChatActiveStoriesList` s `StoryListArchive` / `StoryListMain`).

- **`canBeArchived` platí jen v hlavním seznamu.** U skrytého autora TDLib vrací `false`
  (dokumentace: „True, if the stories are shown in the main story list and can be archived").
  Nabídka se proto ukazuje pro `canBeArchived || isHidden` — bez toho šel autor skrýt, ale
  už ne vrátit (ověřeno: ⋮ u skrytého chybělo). A protože jsou autoři v prohlížeči zmražení,
  vrácený autor si `canBeArchived = false` ze skrytého stavu nesl dál a ⋮ zmizela — ViewModel
  ho po vrácení nastaví zpátky. Vlastní příběhy nejdou skrýt ani vrátit.
- **Skrytí autoři jsou v liště archivu** — hromádka (`StoryStack`) vedle nastavení archivu,
  z `ChatsViewModel.storyStack` složky archivu (`StoryListKind.Archive`). Jinde se neobjeví.
- **V hlavním seznamu chatů má skrytý autor jen tenký šedý lem** (jako zhlédnutý), i když má
  nezhlédnuté: příběhy jdou z řádku otevřít, ale nevolají. Ve složce archivu se lem kreslí
  normálně. Prohlížeč otevřený z řádku skrytého autora prochází skryté autory (jeho seznam).
- Prohlížeč po přesunu zůstane, kde je, a ohlásí to pilulkou („Příběhy od … jsou skryté
  v archivu"); hromádky a lemy se přeskládají updatem `chatActiveStories`.

## Nahlášení

⋮ v hlavičce prohlížeče → „Nahlásit příběh" (vlastní skupina, chybová barva; jen cizí
a dostupný příběh) → **tentýž průvodce jako u chatu a zpráv** (`ReportFlow`, `ReportSheet`,
`docs/poznatky/spam-a-blokovani.md`). `reportStory` je průvodce stejně jako `reportChat`:
prázdný požadavek se zeptá na důvod, pak případně na text; otázky i důvody píše server.

- `ReportFlow` volá funkci (chatId, krok); ViewModel si pamatuje, KTERÝ příběh se hlásí
  (`reporting`), a doplní ho — průvodce sám o příbězích neví.
- Výsledek jde do pilulky nad lištou, ne do snackbaru (prohlížeč `Scaffold` nemá) —
  ViewModel přeposílá `ReportFlow.outcome` do `notice`.
- Pod průvodcem příběh stojí.

## Upozornění na příběhy

V `notifikace.md`, „Upozornění na příběhy" — včetně chyby TDLib, kvůli které se rozsah
zapisuje dvakrát.

## Příběhy v profilu

- **Avatar v hlavičce profilu má lem**, když má chat aktivní příběhy (`ChatProfileViewModel`
  sleduje `StoryRepository.active`), a **ťuknutí pak pustí příběhy, ne historii fotek** —
  lem slibuje totéž co v seznamu chatů. Bez příběhů ťuknutí dál otevírá fotky. Lem je o kus
  silnější (velký avatar) a kreslí se PŘED ořezem avataru, jinak by ho ořez uřízl.
- **Záložka „Příběhy"** (`getChatPostedToChatPageStories`): příběhy, které autor nechal
  v profilu, i vypršelé. Mřížka dlaždic 9 : 16, připnuté první se špendlíkem (vrací je jen
  první stránka, `fromStoryId = 0`), video s trojúhelníkem. Další stránka od id posledního
  příběhu; konec, když stránka nepřinese nic nového nebo je načteno `totalCount`. Záložka je
  na konci řady (dorazí samostatným dotazem, jako dárky) a jen když něco je. Ikona je
  přerušovaný kruh (`history_toggle_off`) — tvar, kterým Telegram příběhy značí.
- **Prohlížeč umí konkrétní seznam jednoho autora** (`StoriesRoute.storyIds`,
  `profileStoriesState`): projde přesně mřížku od ťuknuté dlaždice a za poslední se zavře —
  žádní další autoři. Nic z toho není „nezhlédnuté" a ⋮ se skrytím do archivu tu není
  (autor není v žádném seznamu). Jméno a fotku autora dočte `StoryRepository.author`, protože
  autor teď nemusí mít aktivní příběh; do té doby je prohlížeč prázdný, ne zavřený.
- Vlastní profil (Uložené zprávy) záložku nemá; vlastní příběhy jsou v „Moje příběhy" (menu účtu, viz níž).
- **Alba** (`getChatStoryAlbums`, `getStoryAlbumStories`): čipy „Vše" a názvy alb přes celou
  šířku nad mřížkou, jen když autor nějaké má. Výběr alba mřížku vyprázdní a načte jeho
  příběhy po stránkách (`offset` = počet načtených); odpověď na album, které uživatel mezitím
  přepnul, se zahodí. Připnuté má jen „Vše". Prohlížeč z alba projde to, co je v mřížce vidět.
  **Záložka se řídí `ProfileStories.available`, ne obsahem mřížky** — prázdné album by ji jinak
  schovalo i s čipy, na kterých uživatel zrovna stojí. Prázdné (nebo nenačtené) album ukáže
  „Album je prázdné".

## Prohlížeč

- **Autoři i jejich příběhy se při otevření ZMRAZÍ** (`StoriesState`). Zhlédnutím TDLib mění
  pořadí seznamu i `maxReadStoryId`; živý seznam by uživateli ujížděl pod rukama. Otevírá se
  na prvním nezhlédnutém příběhu autora, u celého zhlédnutého od začátku.
- **Tahem do strany jde na autory TÉHOŽ seznamu** (`viewerPosters`) — i z řádku chatu, ne jen
  z hromádky. Zpět z prvního příběhu autora skočí na předchozího tam, kde se u něj skončilo;
  zpět u úplně prvního příběh pustí znovu (`restart`).
- **`openStory` / `closeStory` hlídá ViewModel** podle aktuálního příběhu (`distinctUntilChanged`
  nad `state.current`). Bez nich se zhlédnutí nepočítá: lem zůstane barevný a autor zhlédnutí
  nevidí. Poslední `closeStory` jde přes `appScope` — `viewModelScope` je v `onCleared` zrušený.
- **Fotka stojí 6 s od chvíle, kdy je stažená**, ne od otevření — na pomalé síti by jinak
  doběhla dřív, než se ukázala. Video běží, jak dlouho hraje (průběh z přehrávače každý snímek).
- **Desktop video nepřehraje** (`VideoPlayer.isFailed`) — stojí náhled po délku videa a jde se
  dál, ať se prohlížení nezasekne.
- **Podržení = pauza, a `onLongPress = {}` je NUTNÉ.** `detectTapGestures` bez `onLongPress`
  čeká jen na puštění a zavolá `onTap` — puštění po pauze by přeskočilo na další příběh.
- **Levá třetina vrací, zbytek jde dál** (dál se ťuká častěji). Pro čtečku obrazovky jsou
  „Další / Předchozí příběh" vlastní akce.
- **Tah dolů zavře** (práh 18 % výšky, jako prohlížeč médií), během tahu příběh stojí.
- **Bez metadat list-detail scény**, takže ho `NavDisplay` vykreslí přes celou plochu i na
  rozloženém Foldu a desktopu; příběh je tam uprostřed v poměru 9 : 16. Lišty mají světlé ikony
  (`DarkSystemBars`).
- **Příběh, který už není**, ukáže větu a po 6 s jde dál. Živé vysílání má vlastní
  kapitolu („Živé příběhy"); kde ho neumíme (desktop, iOS), ukáže větu taky.

## Reakce a odpověď

Lišta pod cizím příběhem (`StoryReplyBar`): pole „Odpovědět…", „+" pro výběr reakce a srdce.

- **Srdce je rychlá reakce na příběh, ne rychlá reakce účtu.** Oficiální aplikace dává na příběh
  srdce bez ohledu na `setDefaultReactionType` (ta platí pro dvojité ťuknutí na zprávu). Klíč je
  `❤` BEZ variačního selektoru — tak ho vede Telegram v seznamu reakcí. Je-li reakce dána, místo
  srdce stojí ona a ťuknutí ji sundá; „+" otevře tentýž výběr jako prohlížeč médií
  (`ReactionPickerSheet`).
- **Nabídka „+" je od serveru pro příběhy** (`getStoryAvailableReactions`,
  `StoryRepository.availableReactions`, jednou při otevření prohlížeče): v jeho pořadí, i vlastní
  emoji; reakce, které chtějí Premium, se bez něj vynechají (jako u zprávy). Dokud nedorazí nebo
  když selže, je tam výběr rychlé reakce jako dřív (`QuickReaction.choices`). Ověřeno na
  příběhu kanálu: nabídka ❤ 👍 🔥 … v pořadí od serveru, jiném než rychlá reakce (bez reakce).
- **Reakce se přepne hned a při chybě vrátí** (jako u zprávy) — `setStoryReaction` s `null`
  sundává. Velké emoji přes příběh potvrdí reakci i bez pohledu na lištu.
- **Odpověď je zpráva do soukromého chatu s autorem** (`sendMessage` s
  `InputMessageReplyToStory`; id soukromého chatu = id autora). V konverzaci ji kreslí citace
  příběhu, kterou aplikace měla už dřív (`typy-zprav.md`, „Odpověď na příběh a odkaz na
  příběh"). Pole je jen u `canBeReplied` — kanál odpověď nepovoluje, tam zbudou reakce.
- **Vlastní příběh lištu nemá** (`MessageMedia.Story.isOwn`). TDLib „vlastní" přímo neříká;
  pozná se podle `canBeDeleted` — smazat jde jen příběh, který jsme zveřejnili my (nebo kanál,
  který spravujeme), a reagovat na něj stejně nejde.
- **Psaní a výběr reakce příběh zastaví** — jinak by uživateli odjel uprostřed věty. Kořen
  prohlížeče proto bere insety lišt a výřezu, NE klávesnice: se `safeDrawing` by se při psaní
  zmenšil celý příběh. Nad klávesnici jede jen lišta (`imePadding`), zbytek ztmavne a ťuknutí
  do ztmavnutí psaní ukončí.
- **Příběh si nechává místo pro lištu** (`STORY_BAR_HEIGHT` pod kartou), lišta ho nepřekrývá.
- **Výsledek hlásí pilulka nad lištou** („Odpověď odeslána", chyba přes `userMessage`), ne
  snackbar — prohlížeč žádný `Scaffold` nemá.

## Vlastní příběh

„+" v seznamu chatů → Nový příběh (nahoře, nejdál od palce; ve sbaleném sloupci vlastní
skupina nabídky) → kontrola limitu → výběr fotky → obrazovka „Nový příběh" → Zveřejnit.

- **Limit se kontroluje PŘED výběrem fotky** (`canPostStory`), ne až po něm. Free účet smí
  mít jeden aktivní příběh; jinak by si uživatel příběh připravil a pak se dozvěděl, že nejde.
  Odmítnutí jde větou do snackbaru — **nákup Premium se nenabízí**. Oficiální aplikace má
  u limitu „Increase Limit", které vede rovnou do platby přes Google Play; Plume platby nedělá
  (roadmapa, „Hvězdy, platby") a takové tlačítko by byla past.
- **TDLib chce fotku PŘESNĚ 1080 × 1920** (`InputStoryContentPhoto`), ne „nejvýš". Vybraná
  fotka se proto kreslí na plátno 9 : 16 (`StoryPhoto`): „Vyplnit" ořízne přečnívající kraje,
  „Celá fotka" ji vloží celou na černou. Geometrie je společná (`storyPhotoPlacement`, testy
  `StoryPhotoTest`), kreslí platforma: Android `Canvas`, iOS `UIGraphicsImageRenderer`,
  desktop `BufferedImage`. Převádí se až při zveřejnění — náhled jen kreslí `AsyncImage`
  s `Crop` / `Fit`, takže vypadá stejně.
- **Video se převádí VŽDYCKY** (`StoryVideo`): TDLib chce MP4 720 × 1280, H.265 a klíčový
  snímek každou sekundu, nejvýš 60 s — video z galerie tomu nevyhoví skoro nikdy. Delší video
  se zkrátit musí — úsek si uživatel vybere sám (viz „Úsek a obálka videa" níž).
  - **Android: Media3 Transformer** jako u profilového videa (`exportVideo` dostal kodek
    a interval klíčových snímků): `Presentation` 720 × 1280 (`LAYOUT_SCALE_TO_FIT_WITH_CROP`
    pro Vyplnit, `LAYOUT_SCALE_TO_FIT` pro Celý záběr), `setiFrameIntervalSeconds(1)`,
    2,5 Mbit/s, zvuk AAC, když ho zdroj má. **H.265 jen když ho zařízení zakóduje** —
    jinak Transformer sám spadne na H.264; jestli ho server vezme, ověřené NENÍ.
  - **iOS: `AVAssetExportSession` s předvolbou H.265** nad kompozicí obrazu i zvuku
    a `AVMutableVideoComposition` 720 × 1280. **Interval klíčových snímků export nastavit
    neumí** (jen `AVAssetWriter`) — iOS je jen přeložené.
  - **Desktop video nepřevede** (`UnsupportedStoryVideo`); video z výběru tam skončí větou
    „tady jen z fotky".
  - Video bez zvuku jde jako animace (`isAnimation`).
  - Převod trvá pár vteřin; obrazovka během něj říká „Připravuji video…" a zavře se až
    po něm (a po přijetí `postStory`).
- **Volba „Celý záběr", ne „Celá fotka"** — platí pro fotku i video.
- **Zveřejňuje se „za" Uložené zprávy** (`chatId` = id účtu, dokumentace `postStory`).
- **Výchozí viditelnost jsou kontakty**, ne všichni: příběh je osobní a zveřejnit ho celému
  světu má být rozhodnutí. Vybraní lidé jdou přes `ChatPicker` jako členové nové skupiny
  (`groupMemberCandidates`, id soukromého chatu = id uživatele); volba „Vybraní" bez výběru
  rovnou otevře výběr.
- **Blízcí přátelé** jsou čtvrtá volba. **Seznam je nastavení ÚČTU** (`getCloseFriends` /
  `setCloseFriends`), ne příběhu — upravuje se ve výběru přímo z volby (prázdný seznam ho otevře
  rovnou, vybraná volba podruhé k úpravě) a platí i v oficiálních klientech. Smí na něm být
  **jen kontakty**, výběr proto ukazuje jen kontakty (žádné globální výsledky) a potvrdit jde
  i prázdný (vyprázdnění seznamu). Seznam se v obrazovce přepne hned a při chybě vrátí;
  zveřejnit s prázdným seznamem (nebo dokud se ukládá) nejde — příběh by nikdo neviděl.
- **Nezhlédnutý příběh pro blízké přátele má lem v `tertiary`** (řádek chatu i hromádka;
  `ActiveStories.hasUnreadForCloseFriends`, `StoryRingState.closeFriends`) — oficiální aplikace
  ho má zelený. Barva se bere z palety, ne zelená natvrdo; odlišení nese i to, že lem je jiný
  než u ostatních. Zhlédnutý příběh pro blízké barvu nemění.
- **Délka je vždycky 24 h** (`activePeriod = 86400`); 6, 12 a 48 h jsou jen pro Premium.
- **Obrazovka se zavře, jakmile TDLib `postStory` přijme**; nahrávání běží dál a výsledek
  (`updateStoryPostSucceeded` / `updateStoryPostFailed`) hlásí snackbar seznamu chatů —
  odebírá ho `ChatsViewModel` hlavního seznamu.
- **Vlastní příběh v prohlížeči** má místo odpovědi lištu s počtem zhlédnutí
  (`interactionInfo.viewCount`) a košem (`deleteStory` po `ConfirmDialog`). Smazaný příběh
  z prohlížeče zmizí a jde se na další (`withoutStory`).
- **Seznam diváků** (`getStoryInteractions`) otevře ťuknutí na počet zhlédnutí: bottom sheet
  s fotkou, jménem, časem a reakcí vpravo; přeposlání a sdílení do vlastního příběhu mají větu
  místo času. Řadí se **nejdřív s reakcí** (`preferWithReaction`), dočítá se po 50, když
  seznam dojede na konec. Téhož diváka server může vrátit na dvou stránkách — slučuje se.
  Divák je `MessageSender`: člověk se dočte `getUser`, chat `getChat`. **Příběh pod sheetem
  stojí.** Po vypršení příběhu server seznam po čase přestane dávat (`hasExpiredViewers`) —
  sheet pak řekne, že už není, místo prázdna.

## Živé příběhy

Živý příběh je skupinový hovor (`StoryContentLive.groupCallId`, `MessageMedia.Story.live`).
Divák se k němu připojí jako ztlumený účastník bez kamery a obraz se zvukem přijímá přes
ntgcalls — tutéž knihovnu jako hovory ve dvou (`docs/poznatky/hovory.md`), ale ve vlastní
instanci a na vlastním vlákně (`NtgLiveStreamMedia`). **Jen Android**: na desktopu a iOS se
`LiveStreamMedia` nezaregistruje a prohlížeč i konverzace řeknou větou, že vysílání neumíme.

- **Pořadí drží knihovna**: `createCall` vrátí přihlašovací JSON (v něm `ssrc` = `audioSourceId`
  pro TDLib) → zdroje přehrávání (zvuk ostatních ve slotu `microphone`, obraz jako EXTERNAL,
  stejně jako u hovoru ve dvou) → `joinLiveStory` → `connect` s odpovědí serveru → u vysílání
  z kamery obraz vysílajícího (`getLiveStoryStreamer` → `addIncomingVideo`, změny přes
  `updateGroupCallParticipant`).
- **Podle odpovědi serveru jede hovor dvěma způsoby.** WebRTC (`RTC`), nebo po segmentech
  (`STREAM`, `RTMP`): knihovna si pak sama řekne o čas streamu (`onRequestBroadcastTimestamp`
  → `getGroupCallStreams`, největší `timeOffset`) a o segmenty (`onRequestBroadcastPart` →
  `getGroupCallStreamSegment`, měřítko 0 = vteřina, kanál a kvalitu z požadavku). Tak to dělá
  pytgcalls. Segment, který TDLib nedá, jde knihovně jako `NOT_READY` a zeptá se znovu.
- **Stream po segmentech spojení nehlásí jako WebRTC**, proto se za „běží" bere i první snímek.
- **Zvuk WebRTC přehrává jako hovor** (`USAGE_VOICE_COMMUNICATION`, AAudio), takže výstup vede
  `CallAudioRouter` jako u hovoru — jen bez soupravy a sluchátek hraje reproduktor, ne
  sluchátko u ucha (`fallback`). Telefon je po dobu sledování v režimu hovoru (hlasitost
  tlačítky řídí hlasitost hovoru); po zavření se vrátí `MODE_NORMAL`.
- **Sleduje se jen příběh, na kterém prohlížeč stojí** (`LiveStoryWatch` ve `StoriesViewModel`
  hlídá aktuální klíč i načtenou stránku). Přechod jinam nebo zavření zastaví knihovnu
  a pošle `leaveGroupCall` — přes `appScope`, protože `viewModelScope` je při zavření pryč.
  Odchází se jen po úspěšném přihlášení, jinak TDLib vrátí `GROUPCALL_JOIN_MISSING`.
- **Konec vysílání** hlásí `updateGroupCall` s `isActive = false`; když skončí dřív, než se
  divák stihne přihlásit, odpoví server na `joinLiveStory` `GROUPCALL_FORBIDDEN`. Proto se po
  nezdaru přihlášení stav hovoru dočte znovu a skončené vysílání řekne „Vysílání skončilo",
  ne „nepodařilo se připojit" (ověřeno: smazání příběhu za připojování).
- **Průběh je celý a dál se nejde samo** — vysílání nemá délku. Po konci nebo nezdaru stojí
  věta 6 s, jako fotka. V hlavičce místo času štítek „ŽIVĚ" a kolik lidí se dívá
  (`participantCount`).
- **Ze zprávy a z odkazu** se živý příběh otevře v prohlížeči (`MessageAction.OpenStory`
  → `ConversationLinks.openStory`), kde ho umíme; jinak věta jako dřív.
- **Na zkoušku vysílání bez druhého telefonu**: `startLiveStory(isRtmpStream = true)` na
  testovacím účtu, `getLiveStoryRtmpUrl` dá adresu a klíč, a z Macu
  `ffmpeg -re -f lavfi -i testsrc2=size=720x1280:rate=30 -f lavfi -i sine=frequency=440 …
  -c:v libx264 -c:a aac -f flv rtmps://…/s/<klíč>`. **Živý příběh se počítá do limitu
  příběhů** — free účet pak `CanPostStoryResultPremiumNeeded` / `ActiveStoryLimitExceeded`,
  stejně jako u fotky.
- **Ověřeno na emulátoru (Android 11)** s RTMP vysíláním z …527 a divákem …029: obraz běží
  plynule, „1 sleduje", zvuk teče (AAudio, reproduktor přes `setSpeakerphoneOn`), zavření
  uvolní přehrávač a vrátí normální režim zvuku. **Neověřené**: vysílání z kamery
  (WebRTC i velké publikum po segmentech), konec vysílání během sledování (jen testy
  `LiveStoryWatchTest` — oba účty měly vyčerpaný denní limit), zvuk ušima na skutečném
  telefonu, souběh s hovorem ve dvou.
- **Co ještě není**: vlastní vysílání (`startLiveStory` z kamery i RTMP), placené zprávy
  a reakce z Plume (hvězdy) a sledování na desktopu.

### Chat vysílání

Zprávy diváků se kreslí přes spodek obrazu (`LiveChatMessages` v `LiveStoryChat.kt`),
pole pro komentář je lišta pod ním (`LiveMessageBar`). Stav drží `LiveStoryWatch` spolu se
sledováním (`LiveStoryState.messages`), takže chat žije přesně tak dlouho jako připojení.

- **Historie neexistuje.** TDLib nemá funkci, která by zprávy vysílání vydala; chodí jen
  updaty (`updateNewGroupCallMessage`, `…MessagesDeleted`, `…MessageSendFailed`,
  `updateNewGroupCallPaidReaction`) od chvíle, kdy se odebírají. Proto se odběr spouští
  v `watch` dřív než připojení, a proto se drží jen posledních 100 zpráv (`MAX_LIVE_MESSAGES`).
- **Vlastní zpráva přijde taky updatem** — `sendGroupCallMessage` vrací jen `Ok`. Když ji
  server odmítne až potom, přijde `updateGroupCallMessageSendFailed` s jejím id: zpráva
  z chatu zmizí a ukáže se hláška (`onSendFailed`). Táž zpráva může přijít znovu, nahradí
  dřívější podle id.
- **Hvězdami Plume neplatí, ani tady.** Zpráva jde vždy s `paidMessageStarCount = 0`. Když
  vysílající za zprávy hvězdy chce (`groupCall.paidMessageStarCount > 0`), místo pole stojí
  věta (`paidOnly`). Placené reakce (`addPendingLiveStoryReaction`) Plume neposílá vůbec;
  cizí jen ukazuje — pilulka s počtem hvězd a jménem proletí přes obraz (`LiveReactionBurst`).
  Zpráva s prázdným textem je placená reakce, která se v chatu drží jako řádek.
- **Psát smí divák jen s `canSendMessages` A `areMessagesAllowed`** — vysílající může
  komentáře vypnout všem a první příznak to neodráží.
- **Barvy úrovní od serveru se neberou.** `updateGroupCallMessageLevels` přijde jednou po
  startu (repository by musela vznikat eagerně) a barvy nesedí k paletě. Placená zpráva má
  plochu `tertiaryContainer`, zpráva vysílajícího (`isFromOwner`) `primaryContainer`.
- **Do živého příběhu nejde zalomení řádku** (dokumentace `sendGroupCallMessage`), pole je
  jednořádkové a repository zalomení pro jistotu nahradí mezerou. Entity se neposílají.
- **Smazání** podržením zprávy s `canBeDeleted`; kdo smí mazat cizí (`canDeleteMessages`),
  dostane i „Smazat a nahlásit spam" (`deleteGroupCallMessages(reportSpam = true)`).
- **Chat dojede na novou zprávu jen tomu, kdo nečte starší.** Seznam (`reverseLayout`) drží
  pozici podle klíče, takže index první viditelné položky po příchodu zprávy poskočí
  o počet nových — porovnává se s ním, ne s nulou.
- **S klávesnicí jede chat nad pole**: `imePadding` PŘED `fillMaxHeight(0.4f)`, jinak by se
  odsazení vešlo do výšky chatu a chat by se smrštil na pár řádků. Odečítat výšku lišty
  netřeba — místo pro ni pod obrazem už je (`Spacer` v `StoryPage`), takže `exclude`
  posadil chat přesně za pole (ověřeno na emulátoru, první pokus).
- **Ověřeno na emulátoru jen vzhled, sondou** (23. 10. 2026, Plume_Barvy …527, česky):
  podstrčené zprávy v prohlížeči archivovaného příběhu — běžné, od vysílajícího, placené
  s hvězdami, placená reakce bez textu, proletující pilulka, nabídka Smazat / Smazat
  a nahlásit spam, psaní s klávesnicí. **Se skutečným vysíláním NEOVĚŘENO**: oba testovací
  účty měly vyčerpaný denní limit příběhů (`CanPostStoryResultPremiumNeeded` bez aktivního
  příběhu — dopoledne se zveřejňovalo při ladění obálky). Zbývá: odeslání a příchod zpráv
  mezi …527 a …029, odmítnutá zpráva, smazání, zpráva vysílajícího.

## Ověření

- **Free účet smí zveřejnit JEDEN příběh za 24 hodin.** Další pokus v oficiální aplikaci
  ukáže „Limit Reached" s tlačítkem „Increase Limit", které vede rovnou na nákup Premium přes
  Google Play — ťukat naslepo se tam nesmí. Dílky lemu se proto dvěma testovacími účty vyrobit
  nedají; ověřily se dočasnou sondou (podstrčený `StoryRingState` u několika řádků,
  necommituje se).
- **Ověřeno na emulátoru `Plume_A11` (…029) proti příběhu z Pixelu 5 (…527)** zveřejněnému
  jen pro „Pixel 7 Pro" (20. 10. 2026): lem v řádku i hromádka, otevření z avataru i z hromádky,
  průběh, popisek, pauza podržením bez přeskočení, zavření tahem dolů, samo zavření po
  posledním příběhu, zešednutí lemu po zhlédnutí a „1 view" u autora v oficiální aplikaci.
  Sondou lem po sušence a čtverci a dílky (3, 5, 12 i všechny zhlédnuté).
- **Reakce a odpověď ověřené tamtéž** (20. 10. 2026): srdce (velké emoji, autor v oficiální
  aplikaci vidí „1 view ❤ 1"), výměna přes „+" za 🔥, sundání ťuknutím, reakce po restartu
  aplikace načtená ze serveru; odpověď s pauzou příběhu při psaní a ztmavnutím, pilulka
  „Reply sent", zpráva v chatu s citací příběhu. **Fotka stojí 6 s a v testu to bylo málo** —
  ťuknutí po snímku obrazovky už mířilo na zavřený prohlížeč; uživateli to nevadí, při psaní
  příběh stojí.
- **Zveřejnění ověřené tamtéž** (20. 10. 2026, účet …029): „+" → Nový příběh, obrazovka
  s náhledem, popiskem a výběrem „Vybraní lidé" (Pixel 5) → Zveřejnit → snackbar „Story
  posted", vlastní avatar v hromádce, u Pixelu 5 v oficiální aplikaci zelený kroužek. Vlastní
  příběh v prohlížeči: plátno 9 : 16 s popiskem, „0 views", koš → dialog → smazání a přechod
  na dalšího autora, avatar z hromádky zmizel. Druhý pokus o příběh ukázal hlášku o limitu.
  **Výběr fotky na emulátoru s Androidem 11 nešel** — systémový výběr souborů ťuknutí na
  soubor nebral (ani v hledání); obrazovka se proto otevřela dočasnou sondou s fotkou v cache
  (necommituje se). Výběr je tentýž jako u příloh.
- **Video ověřené tamtéž** (20. 10. 2026): záznam obrazovky emulátoru (1080 × 2340, bez
  zvuku, 8 s) přes sondu → náhled prvním snímkem, Celý záběr s černými pruhy → Zveřejnit →
  převod kodérem `c2.android.hevc.encoder` asi 6 s → „Story posted" → vlastní příběh
  v prohlížeči se přehrává. **Server tedy H.265 z Transformeru vzal.** Neověřené: video se
  zvukem, video delší než minuta (zkrácení jen v testu), spadnutí na H.264, iOS.
- **Diváci ověření** (20. 10. 2026): Pixel 5 si v oficiální aplikaci pustil video příběh
  účtu …029 (H.265 z Plume přehrál) a dal srdce; v Plume „1 view" → sheet s „Pixel 5 · 19:22"
  a ❤, příběh pod sheetem stál. Neověřené: stránkování (víc než 50 diváků — jen v testu),
  přeposlání a sdílení, vypršelý seznam.
- **Blízcí přátelé ověření** (20. 10. 2026, …029 přes sondu s fotkou): volba „Close friends ·
  Choose close friends" (prázdný seznam), výběr jen z kontaktů → Pixel 5 → „Close friends ·
  1 person" → Zveřejnit → u Pixelu 5 v oficiální aplikaci **zelený** kroužek. Neověřené:
  barva lemu v Plume u příjemce (Plume na Pixelu 5 je verze z Play), chyba uložení.
- **Skrytí ověřené** (20. 10. 2026, …029, příběh Pixelu 5): ⋮ → „Hide stories in archive"
  → pilulka, Pixel 5 zmizel z hromádky v pilulce; v archivu hromádka s Pixelem 5 →
  prohlížeč → ⋮ „Show stories again" → pilulka, nabídka zase nabízí skrytí, Pixel 5 zpátky
  v hromádce. Obě chyby s `canBeArchived` výš se našly tady. Neověřené: lem skrytého autora
  s NEzhlédnutým příběhem (příběh byl zhlédnutý), iOS.
- **Profil ověřený** (20. 10. 2026): Pixel 5 dal v oficiální aplikaci svůj příběh do
  profilu (⋮ → Post to Profile); v Plume (…029) profil Pixelu 5 → avatar s lemem → záložka
  „Stories" s dlaždicí → prohlížeč bez ⋮, po dohrání zpátky v profilu; ťuknutí na avatar
  pustí aktivní příběhy. Neověřené: stránkování, připnuté, video dlaždice, iOS.
- **Nahlášení ověřené po seznam důvodů** (20. 10. 2026, příběh Pixelu 5): ⋮ → dvě skupiny
  (skrýt / červené „Report story") → sheet „What is wrong with this story?" s důvody od
  serveru → zavřeno bez odeslání, příběh pod sheetem stál. **Skutečné nahlášení odeslané
  NENÍ** — šlo by Telegramu. Krok s textem a hláška po nahlášení jen v testu.
- **Alba ověřená jen sondou** (20. 10. 2026): podstrčená alba „Léto 2026" a „Výlety"
  v profilu Pixelu 5 → čipy nad mřížkou, výběr alba (server ho nezná) → „This album is empty."
  a záložka zůstala, „All" vrátí dlaždici. Profil bez alb beze změny. **Skutečné album ověřené
  NENÍ** — vyrobit ho jde jen v oficiální aplikaci (⋮ u vlastního příběhu → Add to Album)
  a oba testovací telefony byly zamčené.
- **Úsek a obálka ověřené na obrazovce** (20. 10. 2026, sonda s 70s záznamem obrazovky, bez
  kontroly limitu): pás „Clip 0:00–1:00 · cover 0:00", tah posunul úsek na 0:05–1:05, ťuknutí
  dalo obálku 0:48 a náhled se přepnul na ten snímek. **Zveřejnění s úsekem a obálkou ověřené
  NENÍ** — účet …029 měl ten den vyčerpaný limit (server vrátil `canPostStory` „potřeba
  Premium"); začátek úseku a čas obálky hlídá jen test.
- **Správa alb ověřená sondou** (20. 10. 2026; příběh Pixelu 5 vydávaný za vlastní, protože
  …029 vlastní příběh neměl a limit byl vyčerpaný): ⋮ → „Albums…" → panel s „New album" →
  dialog s počítadlem (víc než 12 znaků pole nepustí) → server založení cizího alba odmítl
  a chyba se ukázala v panelu. **Skutečné album ani přepínání příběhu v něm ověřené NENÍ.**
  Řazení tamtéž se třemi podstrčenými alby: tah úchytem přesunul „Město" nahoru, „Done" poslal
  `reorderStoryAlbums`, server cizí alba odmítl, pořadí se vrátilo a chyba se ukázala v panelu.
- **Neověřené:** výběr fotky na novějším Androidu a iOS, „Celá fotka" s fotkou na šířku,
  převod na iOS a desktopu (jen přeložené), lišta u příběhu kanálu (bez pole) a u vlastního příběhu (bez lišty), video příběh, přechod mezi víc autory tahem i ťuknutím (logika v testech),
  rozložený Fold a desktop, iOS (jen přeložené).
