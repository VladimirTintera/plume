# Příběhy (stories)

Prohlížení cizích příběhů: lem kolem avataru v řádku chatu, hromádka autorů v pilulce hledání,
prohlížeč přes celou obrazovku a v něm reakce a odpověď; zveřejnění vlastního příběhu z fotky
i videa (i jen pro blízké přátele), diváci (počet i seznam s reakcemi) a smazání; skrytí autora
do archivu; příběhy v profilu (lem kolem avataru a záložka „Příběhy"). Alba příběhů zatím nejsou (`docs/roadmap.md`, „Příběhy"). Sdílený příběh ve
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
- Vlastní profil (Uložené zprávy) záložku nemá; vlastní příběhy jsou v prohlížeči.

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
- **Živé vysílání a příběh, který už není**, ukážou větu a po 6 s jdou dál.

## Reakce a odpověď

Lišta pod cizím příběhem (`StoryReplyBar`): pole „Odpovědět…", „+" pro výběr reakce a srdce.

- **Srdce je rychlá reakce na příběh, ne rychlá reakce účtu.** Oficiální aplikace dává na příběh
  srdce bez ohledu na `setDefaultReactionType` (ta platí pro dvojité ťuknutí na zprávu). Klíč je
  `❤` BEZ variačního selektoru — tak ho vede Telegram v seznamu reakcí. Je-li reakce dána, místo
  srdce stojí ona a ťuknutí ji sundá; „+" otevře tentýž výběr jako prohlížeč médií
  (`ReactionPickerSheet`, emoji z `QuickReaction.choices`).
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
  se zkrátí na první minutu a obrazovka to předem řekne; vlastní výběr úseku zatím není.
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
  - Video bez zvuku jde jako animace (`isAnimation`), obálka je první snímek
    (`coverFrameTimestamp = 0`). Náhled na obrazovce je první snímek (`ProfileVideo.frame`).
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
- **Neověřené:** výběr fotky na novějším Androidu a iOS, „Celá fotka" s fotkou na šířku,
  převod na iOS a desktopu (jen přeložené), lišta u příběhu kanálu (bez pole) a u vlastního příběhu (bez lišty), video příběh, přechod mezi víc autory tahem i ťuknutím (logika v testech),
  rozložený Fold a desktop, iOS (jen přeložené).
