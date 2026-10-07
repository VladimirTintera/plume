# Výkon a paměť

Poznatky k jedné oblasti aplikace. Obecná pravidla projektu a rejstřík ostatních souborů jsou
v [CLAUDE.md](../../CLAUDE.md); poučení z opravy v téhle oblasti patří sem.

## Výkon a paměť

Nástroje pro analýzu výkonu a paměti: reporty Compose kompilátoru, LeakCanary v debug buildu
a skripty nad heap dumpy (`client/tools/heap/`). Co z první analýzy zbylo k řešení, je
v `docs/roadmap.md` („Výkon a paměť").

### Reporty Compose kompilátoru

`./gradlew :client:shared:compileAndroidMain -PcomposeReports` zapíše do
`client/shared/build/compose-reports` stabilitu tříd (`*-classes.txt`), skippability
composables (`*-composables.txt`, `.csv`) a souhrn (`android/main/*-module.json`). Bez
přepínače se reporty nevyrábějí, běžný build je nepotřebuje.

- **Strong skipping je zapnutý (Kotlin 2.x), takže „neskippable" composable v projektu
  není.** Stabilita rozhoduje o tom, JAK se parametry porovnávají: stabilní přes `equals`,
  nestabilní přes identitu (`===`). Nestabilní parametr tedy recompozici nevynutí, jen při
  každé nové instanci se stejným obsahem nepřeskočí.
- **Bloky konverzace jsou `@Immutable`** (`ConversationItem`, `MessageFrameData`…), takže se
  porovnávají přes `equals` i s nestabilními poli uvnitř. Na tom stojí to, že builder
  bloků vyrábí nové instance a nezměněné položky se přesto přeskočí. Report je u nich vede
  jako `stable`, zatímco jejich pole jako `runtime`. To je v pořádku.
- **Neměnné třídy, které kompilátor sám za stabilní nepozná, vyjmenovává
  `client/shared/compose-stability.conf`**, ne `@Immutable`: doménové typy v `core/telegram`
  nemají importovat Compose. Patří tam jen třídy, které se po vzniku opravdu nemění. Kdo do
  nich přidá měnitelné pole, musí je ze souboru vyjmout.
  - **`Minithumbnail` (`ByteArray`) dělal nestabilní celý řetěz**: `MessageMedia.Photo`,
    `Video`, `Animation`, `VideoNote`, `Audio`, `PaidPreview`, `LinkPreview` a tím
    i `ChatMessage`. Composables, které je brala přímo, je porovnávala identitou.
  - **Sealed interface vychází jako „Uncertain"**, i když jsou všechny implementace stabilní
    (`SelfDestructType`, `SpeechTranscript`). Kompilátor rozhoduje jen podle typu, ne podle
    implementací.
  - **Rekurzivní typ nejde odvodit**: `ChatMessage` → `ExternalReply` → `ChatMessage`
    vycházel nestabilní, dokud nebyl `ExternalReply` v konfiguraci.
  - Po úpravě je v `core/telegram` stabilní celý řetěz médií a `ChatMessage` vychází
    `runtime` (rozhodne `ChatPoll`, který je stabilní). Další sealed interface (`UiText`,
    `MessageMedia`, `Verification`) zůstávají „Uncertain": nejsou všechny implementace
    stabilní, a dát je do konfigurace naslepo by znamenalo slibovat neměnnost i za ně.
- **Změnu `compose-stability.conf` inkrementální překlad NEZAREGISTRUJE.** Přeloží se jen
  změněné zdrojové soubory, takže report ukáže novou stabilitu jen u nich a zbytek zůstane
  podle staré konfigurace. Po změně konfigurace se překládá s `--rerun`
  (`./gradlew :client:shared:compileAndroidMain --rerun -PcomposeReports`). Na CI to nevadí,
  tam se překládá načisto.
- **`List` a `Map` v datech jsou `runtime`**, ne `stable`. Do konfigurace stability je
  nepřidávat naslepo: s nimi by se porovnávaly přes `equals`, tedy u seznamu zpráv
  prvek po prvku při každé recompozici.

### LeakCanary

Je jen v debug buildu (`debugImplementation`); v launcheru přidá ikonu „Leaks". Nález
hlásí v logcatu (`adb logcat -s LeakCanary`) a v notifikaci.

- **Sám toho v Plume moc neuvidí.** Aktivita je jedna a `configChanges` pokrývá otočení,
  rozložení i změnu motivu, takže se nikdy nevytváří znovu. Hlídá proto jen okna, která
  zmizí (popupy, dialogy, sheety: `PopupLayout`), a služby (`PlumeMessagingService`).
- **ViewModely Navigation3 nehlídá.** Napojuje se jen na `ViewModelStore` aktivity, kdežto
  dekorátor ViewModelů Nav3 drží úložiště pro každou položku zásobníku. Únik ViewModelu se
  proto hledá počítáním instancí v heap dumpu (níž).
- **Analýzu pouští sám, až když jsou zadržené objekty**: v popředí od pěti, na pozadí od
  jednoho. Ticho po odeslání aplikace na pozadí (`input keyevent 3`, pak čtvrt minuty) znamená
  „nic nezadrženo".

### Heap dumpy a počítání instancí

`client/tools/heap/heap_dump.sh <adresář> <jméno>` vynutí GC, uloží `meminfo` a dump
a spočítá instance tříd. Postup: dump, N cyklů scénáře (otevřít a zavřít obrazovku), dump,
`heap_diff.py před.counts po.counts`. Co v paměti zůstalo, ukáže `heap_path.py dump.hprof
<třída>`: nejkratší SILNOU cestu od GC kořene (slabé reference se nepočítají).

- **GC se vynucuje `run-as eu.tintera.plume kill -10 <pid>`** (SIGUSR1 = explicitní GC
  v ART). Bez `run-as` to shell neprovede („Operation not permitted"), a jde to jen
  u debuggable buildu.
- **Únik se pozná podle RŮSTU s počtem cyklů, ne podle nenulového počtu.** Proto dvě kola
  (2 cykly na zahřátí, pak 8, pak dalších 8). `StateFlowSlot` po prvních cyklech přibyly
  o 45 a pak už ne: pool slotů se jednou zvětší, únik odběrů to není.
- **`uiautomator dump` drží v paměti poslední obrazovku.** Zapne v procesu přístupnost
  a Compose si v `AndroidComposeViewAccessibilityDelegateCompat.currentSemanticsNodes`
  nechá sémantiku posledního stavu, i s lambdami a ViewModelem v nich. Po zavření profilu
  tak v dumpu zůstal `ChatProfileViewModel`, po mini aplikaci `WebAppViewModel` i `WebView`
  (zničená, `destroy()` proběhl). Cache přetrvala i navigaci jinam. Jde o artefakt
  ověřování, ne únik; `heap_path.py` ho prozradí cestou přes `AccessibilityManager`.
- **Jedna instance ViewModelu posledního zavřeného chatu může viset v interní cache
  Compose** (`NodeChain$Differ.before` živého `LayoutNode`). Je jedna a s cykly neroste;
  kód aplikace s tím nic nezmůže.
- **Jednorázové keše nejsou únik**: `MediaCodecInfo` (seznam kodeků po prvním videu nebo
  kolečku) a stovky `BaseLocale` (ExoPlayer si poprvé staví mapu jazyků ISO).

### Delší běh (native heap)

Heap dump ukáže jen Java haldu; nativní (TDLib, kodeky, Chromium) se sleduje číslem.
`client/tools/heap/mem_sample.sh <štítek>` vynutí GC a vypíše jeden řádek: `alloc` (Heap
Alloc nativní haldy), `rss`, Java heap a PSS procesu. Scénář se pouští dokola a vzorek se bere
po každých pěti průchodech; rozhoduje trend `alloc`, ne jedno číslo.

- **Dívá se na `alloc`, ne na PSS ani RSS.** Ty kolísají podle toho, co systém zrovna odložil
  do zramu: proces s týmž `alloc` měl jednou PSS 27 MB a 26 MB `SwapPss`, jindy naopak.
- **Náběh trvá pár průchodů**, pak čísla kolísají o ±5 MB bez trendu. Porovnává se proto až
  stav po zahřátí, jako u počítání instancí.
- **`input swipe` přes otevřenou klávesnici PÍŠE.** Gboard bere tah po klávesnici jako psaní
  tahem, takže se v chatu s botem za čtyřicet průchodů napsalo „Balbau j h qk h heiqj h…"
  a uložilo jako koncept. Před tahy se kontroluje `dumpsys input_method | grep mInputShown`
  a klávesnice se schová; tahy se vedou nad ní.
- **Mini aplikace se ve smyčce zavírá gestem zpět, ne ťuknutím na ✕.** Za půl hodiny přijde
  heads-up notifikace od skutečného kontaktu a leží přesně přes hlavičku; ťuknutí podle
  souřadnic (i z dumpu těsně před ním) by otevřelo cizí chat. **Stalo se to** při ťuknutí na
  název skupiny v hlavičce konverzace: `uiautomator dump` heads-up nevidí (dumpuje okno
  aplikace), takže prvek „našel" a ťuknutí trefilo notifikaci. Před ťuknutím do horní části
  obrazovky se proto čte `dumpsys activity service com.android.systemui` a při
  `headsUpPinned=true` se čeká. Ze stejného důvodu tahy
  nezačínají v horní třetině obrazovky — tah dolů po heads-upu rozbalí lištu notifikací.
- **ANR během dlouhého scrollu na emulátoru nebyl náš.** Po ~25 minutách souvislého scrollu
  přišel jeden „Input dispatching timed out" a dialog se pak vracel i po „Wait", zatímco hlavní
  vlákno Plume spalo. Zadrhával i `system_server` a SystemUI a `IComposerClient.executeCommands`
  trval 2 s, tedy grafika emulátoru. Stack hlavního vlákna mířil do
  `GlobalSnapshot … globalWriteObservers`, a únik observerů to NENÍ: heap dump jich ukázal
  jeden (`GlobalSnapshotManager`) a 24 apply observerů (20 `snapshotFlow`), jak má být.
  Kontrola se dělá rozborem statických polí `SnapshotKt` v dumpu, ne odhadem ze stacku.

### Recompozice za běhu (composition tracing)

Debug build má `runtime-tracing` a přibalenou knihovnu Perfetto SDK (`tracing-perfetto-binary`),
takže každá composable je v systémovém trace vlastní řez se jménem a řádkem.
`client/tools/perf/trace_record.sh <adresář> <jméno> <ms> '<scénář>'` tracing v aplikaci zapne
broadcastem, nahraje trace a mezitím pustí scénář. `trace_analyze.py` pak vypíše snímky
aplikace a nejčastěji skládané composables (Python balíček `perfetto`, nejlépe ve venv).

- **Broadcast se posílá do BĚŽÍCÍHO procesu** a platí do jeho konce. Po přeinstalaci nebo
  zabití se musí poslat znovu, jinak trace nese jen systémové řezy a vypadá to, že se nic
  neskládá. `exitCode 1` = zapnuto, `2` = už bylo.
- **Nula řezů aplikace v nečinnosti je cíl, ne chyba záznamu.** Že záznam funguje, se ověří
  scénářem, ve kterém aplikace pracovat musí (scroll).
- **`Recomposer:animation` bez skládání = někdo si říká o snímky** (`withFrameNanos`,
  nekonečná animace) a jeho hodnota se čte až při kreslení. Ze jména řezu se nepozná kdo;
  hledá se v kódu (`withFrameNanos`, `rememberInfiniteTransition`, `while (true)`) a měří
  znovu.
- **Kdo se skládá v každém snímku, se pozná rozdělením řezů do časových oken**
  (`cast((ts - min) / 1e8 as int)`): šest složení za 100 ms = každý snímek, tedy animace
  čtená v kompozici; jedno za úhoz nebo za zprávu = v pořádku.

Nálezy a opravy (emulátor Plume_Galerie_Fold, debug build, říjen 2026):

- **Otevřený chat si říkal o snímek 60× za vteřinu, i když se nic nehýbalo** (průchody
  `Choreographer#doFrame` bez kreslení, viz „Start a plynulost"). Smyčka polohy
  zprávy pro nabídku (`MessageMenuLayer`, `withFrameNanos` dokola) běžela i se zavřenou
  nabídkou. Nečinné Uložené zprávy: 478 snímků za 8 s → 0. Seznam chatů měl nulu už předtím.
- **Scroll konverzace přeskládával celou obrazovku**, protože `visibleDay` (plovoucí datum)
  četl `topVisibleKey` přímo v těle `ConversationScreen`. Teď se odvozuje jen začátek dne
  (`startOfLocalDay`). Stejný scroll: našich složení 4 915 → 2 166, hlavička a pole 48× → 1×.
- **Při nahrávání se celá karta vstupního pole skládala v každém snímku** kvůli „dýchání"
  tlačítka; při psaní totéž po dobu morfu mikrofon → odeslat. Morf, dýchání a barvy tlačítka se
  čtou až v obsahu `TooltipBox` (vlastní rozsah). Šest vteřin nahrávání: karta 408× (553 ms) →
  61× (94 ms). Tlačítko se v každém snímku skládá dál (~0,8 ms), nový `MorphShape` za snímek
  jinak nejde.
- **Každá aktualizace seznamu chatů přeskládala všechny viditelné řádky.** `ChatSummary` měl
  „runtime" stabilitu, která za běhu vyšla nestabilní, takže se řádky porovnávaly identitou.
  Je v `compose-stability.conf`. Dvě příchozí zprávy: `ChatRow` 40× → 4×.
- **Příchozí zpráva přeskládala starší bublinu v každém snímku animace nároží** (`animateDpAsState`
  se četl v kompozici a z hodnoty se stavěl nový `RoundedCornerShape`). Teď je tvar jedna stálá
  instance s vlastním `CornerSize` (`AnimatedCorner` v `MessageFrame.kt`), který stav čte až
  v `toPx`, tedy při výpočtu obrysu. Tři zprávy od bota: `MessageFrame` 14× (490 ms) → 6× (72 ms).
  - **Překreslí se to bez rekompozice, protože Compose čtení stavu při počítání obrysu hlídá**
    (přečteno ve zdrojích ui 1.12): ořez a `graphicsLayer(shape)` počítají obrys uvnitř
    pozorovaného `updateLayerParameters`, `background(color, shape)` (a tím `Surface`) ho
    pozoruje v `getOutline` a překreslí se, a `drawWithContent` čte v kreslení tak jako tak.
    Vrstva obrysy porovnává hodnotou, takže stálá instance tvaru nevadí.
  - **Kdo z tvaru bere rohy, musí brát OBJEKT `CornerSize`, ne jeho hodnotu v kompozici**:
    `albumTileShape` rohy kopíruje, `flightShape` volá `toPx` ve `graphicsLayer` — obojí
    zůstává líné. Rohy z bubliny přečtené v kompozici by animaci zmrazily.
  - **Ověřeno 40× zpomalenými animacemi a sérií surových snímků**: profil spodního nároží
    předchozí bubliny prochází mezi kulatým a staženým plynule (měřením první barevné
    souřadnice v řádcích nad spodní hranou), ne skokem.
- **Každá nová zpráva přeskládala obal VŠECH viditelných zpráv konverzace** (`MessageItem.Content`,
  `RevealingTime`, `arrival`). Dvě příčiny a každá stačila sama — bez kterékoli z oprav zůstalo
  56 složení na tři zprávy:
  - **`Content` je člen rozhraní (`open`), a otevřené composables kompilátor přeskakovat
    NEUMÍ** (v reportu bez `restartable skippable`, jen `open`). Nová zpráva posune indexy všech
    položek a `LazyColumn` znovu spustí obsah každé viditelné — a s ním `Content`. Seznam proto
    volá top-level `ConversationItemContent(item, scope)`, který přeskočitelný je: blok se
    porovná přes `equals` a nezměněná položka se nesloží. Data class, která `Content` sama
    přepisuje (předěl dne), přeskočitelná je; bloky zpráv ho ale dědí z `MessageItem`.
  - **Obal četl v kompozici hranici nepřečtených** (`scope.unreadBoundary()`, kvůli efektu
    zprávy), a ta ho invalidovala s každou změnou stavu konverzace, přestože se hranice nemění
    a čte se přes `derivedStateOf` — i s `structuralEqualityPolicy()` (ověřeno pokusem, proč,
    zjištěné není). Teď ji čte až efekt (`snapshotFlow`). **Obecně: odvozený stav čtený
    v položce seznamu měřit, ne věřit, že `derivedStateOf` invalidaci zastaví.**
  - Tři zprávy od bota: obal zprávy 53× → 6× (nová zpráva a soused, kterému se změnilo nároží),
    našich složení 638 → 384. Příčina se našla sondou, která v `ConversationItemContent`
    logovala, jestli je blok `equals` s předchozím: byl, a obal se přesto skládal — tedy
    invalidace zevnitř, ne nové parametry.
- **Nestabilní argument nevadí, když nezměněné položky drží TUTÉŽ instanci.** `ForumTopic`
  je pro kompilátor „runtime" (`TopicMessagePreview` nese `UiText`, `ChatDraft`) a `TopicRow` ho
  porovnává identitou — a přesto se při změně jednoho tématu skládá jen jeho řádek: seznam témat
  mění jedno téma z updatu a ostatní instance nechává. Změřeno v testovací skupině s pěti tématy:
  tři zprávy od bota do jednoho tématu, `TopicRow` 4×. Do `compose-stability.conf` proto
  nepatří. Seznam chatů byl jiný případ: `publish` skládá při každém updatu nové `ChatSummary`
  pro všechny chaty. `InviteLink` a členové se čtou znovu jen po akci uživatele (jednou, ne
  s každým updatem) a `MemberDetail` je jediný sheet, ne seznam — měřit je nemá smysl.
- **Obecně: argument composable v seznamu, jehož třída nese pole typu sealed interface, se
  porovnává identitou**, i když je data class z `val`. Report kompilátoru ho ukáže bez
  `stable` u parametru (`chat: ChatSummary` místo `stable chat: ChatSummary`).

### Start a plynulost

Měřeno v říjnu 2026: emulátor Plume_Galerie_Fold s release buildem (R8) podepsaným debug klíčem
a Pixel 11 Pro Fold (složený) s verzí 0.3.51 z Play. Postupy:

- **Studený start**: `am force-stop`, pak `am start -W -n eu.tintera.plume/.MainActivity`.
  Pět opakování a medián; první běh po kompilaci se zahazuje.
- **Kdy je start hotový**, hlásí seznam chatů přes `ReportDrawnWhen` (`core/ui/ReportDrawn.kt`)
  ve chvíli, kdy opustí načítání. V logu je to `Fully drawn eu.tintera.plume/.MainActivity:
  +N ms`; z téhož hlášení počítají Play vitals i Macrobenchmark. Samotné `TotalTime` z `am start
  -W` je první snímek, ten ještě chaty nemá.
- **Plynulost**: `dumpsys gfxinfo eu.tintera.plume reset`, tahy přes `input swipe`, pak
  `dumpsys gfxinfo eu.tintera.plume`. Rozhoduje „Janky frames" a percentily; „legacy"
  a „High input latency" jsou u vstupů z adb nesmyslné. Na emulátoru se plynulost neměří.

Výsledky:

- **Studený start je rychlý.** Emulátor: první snímek ~130 ms, seznam chatů ~170 ms. Fold:
  první snímek 151–175 ms, TDLib připravená 110–140 ms po startu procesu.
- **Vlastní baseline profile se zatím nevyplatí.** APK nese profil knihoven (`baseline.prof`,
  15 kB) a Play instaluje s profilem z cloudu (`speed-profile`, `install-dm`). Na emulátoru
  vyšel seznam chatů v režimech `verify` / `speed-profile` / `speed` (strop toho, co dokáže
  dokonalý profil) za 207 / 171 / 157 ms. Zbývá ~15 ms, a to modul Macrobenchmark s AGP 9
  a KMP nestojí. Režim se přepíná `adb shell cmd package compile -f -m <režim> eu.tintera.plume`.
- **Scroll na Foldu (120 Hz)**: seznam chatů 0,07 % zadrhnutých snímků, 99. percentil 9 ms;
  Uložené zprávy 0,31 %, 15 ms; chat s Notifikátorem (kolečka, samolepky) 0,34 %, 15 ms.
- **`gfxinfo` počítá jen VYKRESLENÉ snímky.** Nečinný chat ve verzi 0.3.51 měl v `gfxinfo`
  nulu, a přitom Perfetto na Foldu ukázalo 361 průchodů `Choreographer#doFrame` za 6 s. Smyčka
  s `withFrameNanos` budí hlavní vlákno každý snímek, i když nic nekreslí. Takové plýtvání se
  proto hledá Perfettem (`trace.cfg` funguje i s release buildem, `Choreographer#doFrame`
  zaznamená atrace kategorie `view`), ne přes `gfxinfo`.

### Výsledky první analýzy

Říjen 2026, emulátor Plume_Galerie_Fold (Android 17), debug build, cykly v testovacích
chatech: Uložené zprávy (prohlížeč, sheet výběru, nabídka sponky, nabídka nad zprávou),
Notifikátor (kolečka, profil se všemi záložkami), Nastavení (všech pět sekcí), mini aplikace
(ukázka `cafe`). **Únik nenalezen** v žádném scénáři. Java heap se drží na 23–30 MB a mezi
koly se nemění, přehrávače (`ExoPlayerImpl`) po odchodu z chatu mizí a LeakCanary nic
nezadržel.

**Native heap se ustálí, neroste** (delší běh, `mem_sample.sh` níž). Čerstvý proces má
alokováno ~45 MB; po prvních pěti průchodech chatem s botem (kolečka, GIFy, samolepky, scroll
do historie a zpět) ~80 MB a pak 79–90 MB až do čtyřicátého průchodu, bez trendu. Třicet
otevření a zavření mini aplikace (`cafe`) přidalo 0,3 MB (69,6 → 69,9) a po zavření je
`WebViews: 0`. Dřívější „80 → 130 MB" byl tedy náběh kešů (TDLib, kodeky, Chromium), ne únik.

**Při rozložení a složení displeje aplikace padala.** Příčina byla `consumeWindowInsets(PaddingValues)`
s hodnotou, která se mění se změnou scény, viz „Gotchas". Na Foldu to byl pád
`NPE … Object.getClass()` ve verzích 0.3.47 i 0.3.50.
