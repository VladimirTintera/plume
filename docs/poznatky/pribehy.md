# Příběhy (stories)

Prohlížení cizích příběhů: lem kolem avataru v řádku chatu, hromádka autorů v pilulce hledání,
prohlížeč přes celou obrazovku a v něm reakce a odpověď. Skrytí do archivu, příběhy v profilu
a zveřejnění vlastního příběhu zatím nejsou (`docs/roadmap.md`, „Příběhy"). Sdílený příběh ve
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
| Lišta s odpovědí a reakcemi, velké emoji, hlášení | `features/stories/StoryReplyBar.kt` |

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
- **Neověřené:** lišta u příběhu kanálu (bez pole) a u vlastního příběhu (bez lišty), video příběh, přechod mezi víc autory tahem i ťuknutím (logika v testech),
  rozložený Fold a desktop, iOS (jen přeložené).
