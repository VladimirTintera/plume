# AI v zařízení

Poznatky k jedné oblasti aplikace. Obecná pravidla projektu a rejstřík ostatních souborů jsou
v [CLAUDE.md](../../CLAUDE.md); poučení z opravy v téhle oblasti patří sem.

## AI v zařízení (prototyp)

Experimentální „Shrnout" v hlavičce konverzace: shrnutí nepřečtených (jinak posledních 60 zpráv)
jazykovým modelem v telefonu. Zapíná se v Nastavení → Obecné → AI v zařízení (výchozí vypnuto),
tam se i stahuje model. Hranice je `core/ai` (`OnDeviceAi`).

### Dva poskytovatelé: Gemini Nano a Gemma

Uživatel volí, KDO model počítá (`ModelVariant.provider`, `AiProvider`): **Gemini Nano** v AICore
(ML Kit, `MlKitOnDeviceAi`), nebo **Gemma 4** přes LiteRT-LM (`LiteRtOnDeviceAi`), kterou si
stáhne aplikace. Volba platí pro celou aplikaci — rozhoduje `RoutingOnDeviceAi` (testy
`RoutingOnDeviceAiTest`) a každá funkce jen předá variantu z nastavení. Nová funkce s modelem
tedy o poskytovatelích nemusí vědět nic.

- **Poskytovatel je pole varianty, ne druhé rozhraní.** Funkce už variantu z nastavení braly
  (`onDeviceAiVariant`); kdyby byl poskytovatel samostatná volba, musela by každá funkce řešit
  dvě věci a jedna by na to zapomněla.
- **Router variantu NORMALIZUJE** (`forGeminiNano`, `forGemma`). ML Kit drží klienta na
  variantu v mapě, takže by jinak změna modelu Gemmy vyrobila druhého klienta téhož Nano.
- **Katalog Gemmy (`GemmaModel`) je převzatý z Google AI Edge Gallery**
  (`model_allowlists/1_0_19.json`): soubor, commit na Hugging Face, velikost a doporučená paměť.
  **Commit je součást adresy**, takže se stahuje pořád tentýž soubor; nová verze = vědomá změna
  katalogu. Verze knihovny (`litertlm-android 0.11.0`) je taky z Gallery — **tagy na GitHubu
  LiteRT-LM (v0.9.0) s verzemi na Google Maven nesedí**, a API se proto ověřovalo proti kódu
  Gallery, který se proti 0.11.0 překládá.
- **Gemma se nenabízí pod Androidem 12, bez arm64 a pod `minMemoryGb`** (8 GB pro E2B, 12 GB
  pro E4B; `totalMem` s 15% rezervou, výrobce udává víc, než jádro nechá). Pod tím by systém
  aplikaci zabil při načítání modelu. Kde model nejde, je `Unsupported` a volba se vůbec neukáže.
- **Uložená volba, kterou zařízení nesplní, se opraví** (`supportedVariant`, testy
  `SupportedVariantTest`): Nano na telefonu bez AICore → Gemma, E4B s málo pamětí → E2B.
  Chybějící uložený poskytovatel je Gemini Nano — tak se aplikace chovala dřív a kdo si ho
  zapnul, nemá po aktualizaci stahovat gigabajty.
- **Stahuje se do `.part` a přejmenovává až hotový**, takže přítomnost souboru = kompletní model.
  Přerušené stahování navazuje (`Range`). Před stahováním se kontroluje volné místo (velikost
  + 500 MB) a nedostatek má vlastní hlášku (`NotEnoughSpaceException`) — u gigabajtů je to
  nejčastější důvod selhání a jediný, se kterým uživatel může něco udělat.
- **Po výpadku sítě stahování naváže SAMO** (`LiteRtOnDeviceAi.fetch` čeká na `NetworkMonitor`).
  Stahuje se minuty a přechod z Wi-Fi na data je za tu dobu běžný; dokud výpadek končil
  „nepodařilo se stáhnout", musel se uživatel vracet do Nastavení kvůli něčemu, co se vlastně
  nepokazilo. Mezitím nastavení říká „Čekám na připojení" (`ModelDownload.waitingForNetwork`).
  Opakuje se jen chyba spojení: odpověď serveru (`HttpStatusException`) ani plný disk čekáním
  nespraví, a po pěti pokusech bez jediného nového bajtu se stahování vzdá. Po vypnutí
  letového režimu přišel na emulátoru ještě DRUHÝ výpadek, jak se síť usazovala — i ten se
  navázal, proto se čeká napřed na síť a teprve pak s prodlevou.
- **Nastavení u Gemmy říká, CO a KOLIK se stáhne** („Gemma 4 E2B je potřeba nejdřív stáhnout
  (2,4 GB)") a během stahování „164 MB z 2,4 GB". Samotné „zabere gigabajty" stačí u Nano, jehož
  velikost nikdo neřekne; tady ji víme. Velikost jde přes `fileSizeText` jako u souborů
  v chatu, takže je v GiB — katalog Gallery uvádí 2,6 GB v desítkových. Po selhání tlačítko
  slibuje „Pokračovat ve stahování": stažený kus se nezahazuje.
- **Stahovaný model jde smazat, systémový ne** (`ModelVariant.appManaged`, `OnDeviceAi.delete`).
- **Gemmu stahuje úloha WorkManageru, ne korutina v procesu** (`ModelDownloadWorker` v `core/ai`,
  vedle `LiteRtOnDeviceAi`). Dokud stahovala korutina, skončilo stahování se zabitým procesem
  a navázalo až dalším ťuknutím v Nastavení. Stahuje pořád `LiteRtOnDeviceAi` (i s čekáním na
  síť), úloha jen drží proces a ukazuje notifikaci průběhu se „Zastavit"; stav pro obrazovku je
  v téže instanci (proto je `LiteRtOnDeviceAi` v Koinu samostatný `single`). WorkManager je kvůli
  tomu i v `:client:shared` — v aplikaci byl už dřív, APK se nezměnilo.
  - **Služba v popředí `dataSync`, jen když ji systém pustí.** Po zabití procesu spustí
    WorkManager úlohu z pozadí a Android 12+ tam službu v popředí odmítne
    (`ForegroundServiceStartNotAllowedException`). Úloha pak stahuje jako běžná a notifikaci
    ukazuje sama; po limitu běžné úlohy ji systém zastaví, WorkManager spustí znovu a `.part`
    naváže. Ověřeno: po `kill -9` do 5 s nový proces bez služby v popředí, stahování pokračovalo
    od 751 MB. Když se aplikace mezitím otevře, rozběhne se úloha i se službou.
  - **`JobCancellationException` JE `IllegalStateException`.** `catch (IllegalStateException)`
    kolem `setForeground` chytal i zrušení úlohy a dokončené stahování vypadalo jako odepřená
    služba v popředí. Zrušení se proto chytá a posílá dál dřív.
  - **Podmínka sítě (`NetworkType.CONNECTED`), ne jen smyčka v `fetch`.** Bez sítě by úloha
    držela službu v popředí hodiny naprázdno. Při ztrátě sítě ji WorkManager zastaví
    (`STOP_REASON_CONSTRAINT_CONNECTIVITY` → obrazovka „Čekám na připojení") a s návratem
    spustí znovu. Krátký výpadek, o kterém systém ještě neví, přečká smyčka jako dřív.
  - **Důvod zastavení rozhoduje, co ukázat** (`getStopReason`): `CANCELLED_BY_APP` = uživatel
    zastavil („Zastavit" v notifikaci je `createCancelPendingIntent`), cokoli jiného = systém
    a úloha se vrátí, takže pro obrazovku se pořád stahuje.
  - **Spuštěná úloha zapíše stav NAČISTO**, i když už nějaký byl: jinak by po návratu sítě visel
    starý příznak čekání, než přijdou první 4 MB.
  - **Zastavit jde z notifikace i z Nastavení** a zčásti stažený model má vlastní stav
    (`OnDeviceAiStatus.Paused`: `.part` je, úloha není) s „Pokračovat ve stahování" a „Zahodit
    staženou část". Stahování, které přežije zabití aplikace, by jinak nešlo zastavit vůbec,
    a gigabajt rozstahovaného modelu by nešel smazat.
  - **Play chce typ `dataSync` vysvětlit** v deklaraci služeb v popředí (Play Console → Obsah
    aplikace), stejně jako `mediaPlayback` u hudby. Bez ní neprojde další release.
  - **Ověřeno na emulátoru sondou** (Pixel_9 se 4 GB, kontrola paměti v `deviceCanRun` dočasně
    vypnutá; necommituje se — stahování na paměti nezávisí): stažení celého E2B (velikost sedí
    na bajt) s notifikací „Model … je stažený", zabití procesu za běhu, „Zastavit" z notifikace
    i z Nastavení, letový režim (čekání, zastavení úlohy, navázání), zahození části a smazání.
    **Release build (R8) s úlohou ověřený není** — workery drží keep pravidla WorkManageru,
    na kterých stojí i notifikační úlohy.
- **Engine je v paměti nejvýš jeden a po dvou minutách nečinnosti se uvolní** — drží gigabajty
  RAM. Načtení trvá až deset sekund, proto ne hned po odpovědi (návrhy chodí po sobě).
  **Generování je jedno po druhém** (zámek): dvě konverzace nad jedním enginem LiteRT-LM neslibuje.
- **GPU, a když nejde, CPU.** GPU backend potřebuje `libOpenCL.so` — `uses-native-library`
  v manifestu aplikace (`required=false`). Na emulátoru GPU selže na OpenGL (`GL_INVALID_ENUM`
  v `delegate_opengl.cc`) a CPU naskočí; na zařízení ověřené není, jestli se GPU chytí.
- **LiteRT-LM v Kotlinu neumí počítat tokeny** (`countTokens` = `null`), takže se přepis jen
  odhaduje z limitu. Limit je velikost KV keše (8 192, stejně jako Gemini Nano) minus místo na
  odpověď — vstup i výstup se do keše musí vejít SPOLU. **Strop výstupu hlídá aplikace sama**
  (`MAX_OUTPUT_CHARS`, pak `Generated.truncated`); konfigurace konverzace žádný nemá.
- **Hugging Face model vydá bez přihlášení** (ověřeno `curl -IL`: 302 na CDN, 200, velikost
  sedí na bajt, `accept-ranges: bytes`). Licenční brána by se projevila 401/403 a tu prototyp
  hlásí jako selhání stahování.
- **Nativní knihovny přidají ~25 MB na ABI** (debug arm64 49,6 → 74,3 MB) a dostane je každý,
  i na telefonu, kde se Gemma nikdy nenabídne. Pro prototyp únosné; před vydáním Play Feature
  Delivery. Knihovna má minSdk 23 (projekt 26), `tools:overrideLibrary` potřeba není.
- **Na emulátoru se Gemma vyzkoušet DÁ — na rozdíl od Gemini Nano.** Stačí AVD s 8 GB RAM
  (`hw.ramSize=8192` v `config.ini`, jinak se E2B kvůli `minMemoryGb` nenabídne); hostitel
  paměť bere až podle skutečné spotřeby, na Macu s 18 GB to po zastavení Gradle daemonů šlo.
  Běží na CPU: shrnutí 32 zpráv zhruba za 40 s, proces drží ~3 GB a po dvou minutách
  nečinnosti spadne na ~300 MB. E4B (12 GB) se na takový Mac nevejde. **Čeština proti Nano
  a GPU se ověřují až na telefonu.**
- **Kvalita E2B je podobná Nano, ne lepší**: fakta drží (data, ceny, číslo účtu), ale
  propsala „v češtině" z instrukce, „Notifikator změnily" a v návrzích se objevil nesmysl
  („Ne, chyba, nebudu přijímat."). Kvůli kvalitě je tu E4B.

- **`core/ai` je hranice** jako `core/telegram`: `com.google.mlkit.genai.*`
  a `com.google.ai.edge.litertlm.*` smí být jen v `androidMain/core/ai`. Poskytovatelé jdou
  přidávat (Apple Foundation Models ze Swiftu) bez zásahu do obrazovek. iOS je zatím `NoopOnDeviceAi`.
- **AICore není API, je to systémová služba** s Gemini Nano; aplikace na ni sahá přes ML Kit
  GenAI Prompt API (`com.google.mlkit:genai-prompt`, beta). Verze se bere z oficiální ukázky
  `googlesamples/mlkit` (`android/genai`) — Google Maven se z cloudového sandboxu stáhnout nedá,
  ukázka na GitHubu ano, a je to jediný spolehlivý zdroj přesného API.
- **Hotová API ML Kitu (Summarization, Proofreading, Rewriting) češtinu NEUMÍ**, jazyk se u nich
  vybírá ze seznamu. Prompt API jazyk nevolí, proto prototyp stojí na něm.
- **Instrukce promptu jsou anglicky, jazyk odpovědi výslovně podle aplikace** (`summaryPrompt`,
  testy `SummaryPromptTest`). Přepis se ořezává ZEZAČÁTKU — shrnutí má říct, co se dělo
  naposledy.
- **Stream Prompt API vydává PŘÍRŮSTKY**, `OnDeviceAi.generate` celý dosavadní text.
- **Varianta modelu je `ModelConfig`** (`ModelReleaseStage` STABLE / PREVIEW, `ModelPreference`
  FULL / FAST; dokumentace „Select a model"). Pro AICore je to JINÝ model: vlastní stav, vlastní
  stažení, vlastní limit. Proto každé volání `OnDeviceAi` bere `ModelVariant` a klient je jeden
  na variantu. Volba je přání — co opravdu běží, říká `getBaseModelName()`.
- **Aplikace nevidí seznam modelů v telefonu ani je nespravuje** — to AICore nedovoluje. Kdo chce
  vybírat konkrétní soubor modelu, potřebuje vlastní runtime (LiteRT-LM).
- **Přepis se přizpůsobuje skutečnému limitu** (`getTokenLimit`, `countTokens`): napřed odhad
  podle limitu (`transcriptCharsFor`, tři znaky na token), pak nejvýš třikrát zkrátit úměrně
  přesahu (`shrunkTranscriptChars`). Pod shrnutím je model, tokeny a kolik zpráv se vešlo.
- **Inferenci AICore pustí jen aplikaci v popředí** — z notifikace nebo úlohy to nepůjde.
- **Sledování stahování běží v `appScope`**, odchod z Nastavení ho neruší; nová obrazovka se ve
  stavu `Downloading` k němu připojí voláním `download()`. Samotné stahování Nano řídí AICore,
  Gemmu úloha WorkManageru (viz výš).
- **Zprávy ke shrnutí dává obrazovka** (`summarySource` nad `state.messages`, testy
  `ChatSummaryTest`), ale historie se kvůli němu nejdřív DOTÁHNE
  (`ConversationViewModel.loadForSummary`). Hned po otevření chatu je načtená jediná stránka,
  takže „posledních 60 zpráv" bralo 30 — a kolik jich bude, záviselo na tom, kolik uživatel
  odscrolloval. Kdy přestat dotahovat, je čistá funkce `summaryHasEnough`.
- **Useknutý výstup se POZNÁ, neodhaduje se z délky** (`Candidate.FinishReason.MAX_TOKENS` →
  `Generated.truncated`). Model přestane psát uprostřed a text vypadá jako hotový; patička
  přitom hlásí VSTUP („583 z 8 192"), takže uživatele naopak uklidní, že se všechno vešlo.
  Ověřeno na Pixelu 11 Pro Foldu: shrnutí dvanácti zpráv skončilo u šesté a podruhé u sedmé —
  strop je v TOKENECH, takže kam text dosáhne, je pokaždé jinde. Kus streamu bez textu nese jen
  `finishReason`, takže se nesmí přeskočit — jinak se useknutí ztratí právě na té zprávě, která
  ho hlásí.
- **Čeština spotřebuje víc tokenů než angličtina**, na kterou jsou výchozí hodnoty laděné; proto
  `MAX_OUTPUT_TOKENS = 800`, ne 400. Není to „čím víc, tím líp" — platí se delším generováním
  (na Foldu 12–35 s).
- **Ověřeno na zařízení** (Pixel 11 Pro Fold, Android 17, AICore `thirdpartyexperimental`):
  `nano-v4-full`, limit vstupu 8 192 tokenů, rychlejší varianta je opravdu jiný model
  (`nano-v4-fast`) a je připravená hned. **Shrnutí funguje i v letovém režimu** — model počítá
  v telefonu, nic se neposílá.
- **Preview varianta se tiše chová jako stabilní.** `checkStatus` vrátí `AVAILABLE` (ne
  `DOWNLOADABLE`) a `getBaseModelName()` dá totéž jméno co stabilní verze, takže přepínač
  nedělá nic. Porovnáno na zařízení všemi čtyřmi kombinacemi: jméno určuje VÝHRADNĚ `fast`
  (`nano-v4-full` / `nano-v4-fast`), `preview` na ně nemá vliv a limit je pořád 8 192.
  Proto `previewSameAsStable` — jména se porovnají a pod přepínač se připíše, že se nepoužije.
- **Preview model v zařízení JE a přes ML Kit se k němu stejně nedostaneme — a je vidět
  proč.** V AICore Labs (`aicore.demo.labs.thirdparty.ThirdPartyLabsActivity`) jde vybrat
  „Gemini Nano 4 Full [Preview, TPU]" a odpoví na prompt; `getBaseModelName()` se zapnutým
  `preview` přesto vrací dál `nano-v4-full`, i po `force-stop`. **V logcatu je příčina:**
  ```
  GenAiProcessorKt: AICore failed with error type 3-PREPARATION_ERROR
    and error code 606-FEATURE_NOT_FOUND: Feature -1 is not available.
  GenAiProcessorKt: checkFeatureStatus failed
  ```
  ML Kit si konfiguraci překládá na číslo AICore „feature" a pro preview mu vyjde **-1**,
  tedy žádné. Chybu spolkne a tiše použije stabilní model. Není to tedy chybějícím modelem
  ani naším voláním — je to mezera v `genai-prompt` (1.0.0-beta4 je nejnovější verze, další
  alfa ani beta na Google Maven není). Hypotéza „stačí preview stáhnout" je vyvrácená
  pokusem. **Má to i kontrolní měření:** totéž shrnutí s VYPNUTÝM preview nechá v logu
  nula výskytů `FEATURE_NOT_FOUND`, model se načte normálně. Bez té kontroly by to bylo
  jen pozorování vedle sebe, ne příčina.
- **Labs nabízí čtyři modely, „Správa AI modelů" ukazuje jediný balík „Fast + Full [TPU]".**
  Proto je rychlá varianta připravená okamžitě a nic se nestahuje: full i fast jsou TENTÝŽ
  balík.
- **Preview Full je postavený na Gemma 4 E4B**, zatímco stabilní na E2B — tedy větší model
  a nejnadějnější směr pro kvalitu češtiny. **Cesta k němu přes ML Kit ale nevede** (viz výš)
  a seznam modelů API nemá: `GenerativeModel` zná jen `getBaseModelName()` pro právě
  nakonfigurovanou variantu. Kdo na E4B chce, potřebuje vlastní runtime (LiteRT-LM).
- **Na emulátoru se to vyzkoušet NEDÁ, ani v nejnovějším obrazu.** Android 17 (API 37,
  `google_apis_playstore_ps16k`) balíček `com.google.android.aicore` nemá; starší obrazy taky ne.
  **Řetězce „aicore" v `system.img` k ničemu neslouží jako důkaz** — `grep` jich najde desítky,
  ale jsou to klientská rozhraní `com.google.android.apps.aicore.aidl.*`, která si nese Dialer,
  autofill a detekce spamu. Rozhodne až `pm list packages` na běžícím systému.
- **Doinstalovat AICore z telefonu JDE a stejně to nepomůže.** Je v `/data/app` a není privileged
  (`flags=0x0`), takže `adb pull` tří split APK z Pixelu (128 MB) a `install-multiple` na emulátor
  projde, **služba nastartuje a ML Kit se k ní připojí** (v logu `Start proc … for bound-service
  AiCoreMultiUserService`). Pak ale přijde `606-FEATURE_NOT_FOUND: Feature 636 is not available`
  a v logu `Failed to fetch manifest config from source`: AICore v emulátoru nemá model ani
  hardware, na kterém by počítal. Sonda vrátila `status=Unsupported`, `countTokens=null`,
  `generate` selhalo — tedy **aplikace se zachová správně a nic nespadne**, což je to jediné, co
  se tím ověří.
- **`Feature 636` versus `Feature -1` je rozdíl, který něco říká.** Tady si ML Kit číslo featury
  odvodil správně a AICore ji nemá; u preview varianty na zařízení vyjde `-1`, tedy žádná. Je to
  nezávislé potvrzení, že `-1` je mezera v `genai-prompt`, ne chybějící model.
- **Detail k `info()`:** s doinstalovaným AICore vrátila `ModelInfo(name=null, tokenLimit=8192)`,
  tedy NENULOVÝ objekt bez jména. Na UI se to neprojeví (rozhoduje `status`), ale kdo by se ptal
  jen na `info() != null`, dostal by na nepodporovaném zařízení „model je".
- **Co na emulátoru ověřit JDE:** chování bez modelu (že se sekce v Nastavení nenabídne a nic
  nespadne — viz „Návrhy odpovědí"), vzhled podstrčeným stavem a čisté funkce testy. Na zařízení
  zbývá kvalita výstupu, rychlost, limit tokenů a useknutí.

### Termín připomínky

Třetí funkce s modelem: termín pro „Připomenout…" nad zprávou, když ho nenajdou pravidla
(podrobně v `konverzace-akce.md`, „Připomenutí zprávy"). Stejná hranice a varianta z nastavení
jako návrhy odpovědí, vlastní vypínač nemá — běží jen s modelem `Ready` a jen na vyžádání
(otevření sheetu), takže nic nevyskakuje samo.

- **Krátký výstup = rychlé.** Na emulátoru s Gemmou E2B na CPU 8–9 s, s prvním načtením enginu
  13 s (shrnutí 32 zpráv přes 40 s). Na telefonu s Nano se dá čekat zlomek vteřiny jako u návrhů.
- **Datum a den odeslání musí být v promptu výslovně** — model kalendář nezná a „za týden"
  počítá od nich.
- **Emulátor na zkoušení modelu:** `Plume_Galerie_Fold` s 8 GB RAM a staženou E2B (stahování
  2,4 GB trvalo ~13 min, ~3 MB/s).

### Přepis řeči (zvukový vstup Gemmy)

Gemma 4 umí zvuk (`OnDeviceAi.transcribe`); podrobně v `prohlizec-medii.md`, „Přepis v telefonu".
Pro tuhle oblast podstatné: **engine se zvukem je jiný engine** (`EngineConfig.audioBackend`),
`withEngine(audio = true)` ho načte a pak slouží všemu; bez zvuku se kvůli přepisu přenačte.
Na emulátoru (CPU) 7,5 s řeči za 11 s i s načtením.

### Kontrola tónu

Sheet „Zkontrolovat a přepsat…" (viz „Přepis před odesláním") se po otevření sám zeptá, jak
koncept vyzní (`DraftRewriteViewModel.checkTone`, prompt `TonePrompt.kt`): „Zpráva zní
v pořádku", nebo chybovou barvou „Může vyznít podrážděně / příkře". Nic se nepřepisuje —
přepis „Zdvořileji" je hned pod tím, na ťuknutí.

- **Spouští se na vyžádání** (otevřením sheetu), ne na každý úhoz ani před odesláním: roadmapa
  chtěla kontrolu, která neotravuje. Menu proto zní „Zkontrolovat a přepsat…", ne „Přepsat…".
- **Pasivní agrese vědomě NENÍ.** Gemma 4 E2B jí označila věcné „Pošli mi to do pátku, jinak to
  nestihnu" i s výčtem toho, co je v pořádku; bez té kategorie stejná zpráva vyšla `FINE`
  a podrážděná („Už potřetí ti píšu, kde jsou ty peníze?!") dál `IRRITATED`. Nejjemnější tón
  malý model nerozezná a zbytečné varování je horší než žádné.
- **Nejistota = mlčet**: `UNSURE`, víc štítků naráz nebo chyba modelu řádek vůbec neukáže
  (`ToneState.Unknown`).
- **Přepis kontrolu ruší**, dokud běží — dva dotazy na model by se jen předbíhaly.
- **Ověřeno na emulátoru** (Gemma E2B, CPU, ~10 s): podrážděný koncept → „Může vyznít
  podrážděně", „V kolik se zítra sejdeme?" i věcná prosba s termínem → „v pořádku".
  Koncepty psané bez diakritiky (adb neumí psát háčky). **Gemini Nano neověřené.**

### Vysvětlení dlouhé zprávy

„Vysvětlit" v nabídce nad DLOUHOU PŘÍCHOZÍ zprávou (od `EXPLAIN_MIN_CHARS` = 280 znaků,
`MessageMenuAction.Explain`, prompt `ExplainPrompt.kt`) pustí model nad jednou zprávou a výsledek
ukáže v sheetu shrnutí konverzace (`ChatSummaryViewModel.explain`, zdroj s `explain = true`
mění nadpis, „Čtu zprávu…" a hlášku o chybě). Vedle serverového „Shrnout" (jen s Premium,
viz `konverzace-akce.md`, „Shrnutí od serveru") je to cesta bez Premium a bez serveru.

- **Odpověď v jazyce aplikace, i u cizojazyčné zprávy** — vysvětlení anglického oznámení česky
  je půlka užitku.
- **Nabízí se se zapnutou AI a modelem, který telefon UMÍ** (`explainAvailable`, stav jiný než
  `Unsupported`); nestažený model ohlásí sheet, jako u shrnutí. Na chráněném obsahu taky: nic
  neodchází z telefonu.
- **Obsluhuje ho obrazovka, ne ViewModel konverzace** (`ScreenItemScope.onAction` dostal ViewModel
  shrnutí), ViewModel jen zavře nabídku. Sheet i ViewModel shrnutí už v `ConversationScreen`
  byly, takže funkce ho nezvětšila (271 registrů, stejně jako `main`).
- **„Zkusit znovu" po vysvětlení zopakuje vysvětlení** (`ChatSummaryViewModel.retry`), ne
  shrnutí celé konverzace.
- **Gemma 4 E2B na češtině chybuje i ve faktech** (emulátor, CPU, ~40 s na dlouhou zprávu):
  „by Wednesday evening" přepsala na „do čtvrtka večer"; se zněním „opiš přesně" zase nechala
  anglické úseky v uvozovkách; s „přelož, ale neměň" sedělo datum údržby, ale termín kontaktu
  si vymyslela („sobotu 13. října"). Každý ze tří běhů měl jinou chybu, takže se prompt dál
  neladil — pod výsledkem stojí „Může být nepřesné". **Gemini Nano na skutečném telefonu
  neověřené.**

### Přepis před odesláním

Podržení odesílacího tlačítka → „Zkontrolovat a přepsat…" (dřív „Přepsat…") otevře sheet (`DraftRewriteSheet`, prompt
`RewritePrompt.kt`): původní text, volba Kratší / Zdvořileji / Opravit překlepy, výsledek
a „Použít". Nic se neodesílá a pole se nemění, dokud uživatel neťukne „Použít" — tím se tahle
funkce liší od návrhů odpovědí: sloh drhne, ale chybu uživatel VIDÍ dřív, než ji podepíše.

- **Přepisuje se vždy PŮVODNÍ text**, ne předchozí přepis; přepnutí stylu tak nekupí chyby.
- **Jazyk zprávy, ne aplikace, a tykání zůstává tykáním** — obojí musí v promptu stát výslovně
  (stejná past jako u návrhů odpovědí, viz `formalityInstruction`). Ověřeno: „Zdvořileji"
  kamarádovi nevykalo.
- **Použitý text nahradí koncept i s formátováním** (`composer.fill(text, emptyList())`): model
  vrací holý text a rozsahy tučného by na něm ujely.
- **Žije ve vstupním poli (`MessageInput`), ne v `ConversationScreen`** — ten je v dexu na hraně
  (271 registrů už na `main`, pád byl při 275). `MessageInput` má kolem 180. Kdo do konverzace
  přidává funkci s vlastním stavem, ať ji pověsí tam, kde data už jsou.
- **Kvalita Gemmy E2B na češtině je slabá** (emulátor, CPU, ~10 s): „Opravit překlepy" doplní
  většinu háčků, ale „pozdejc" přepsalo na „pozdejší" a „pockej" nechalo; „Kratší" je dobré.
  Proto výsledek vždy jen nabízí. **Gemini Nano na skutečném telefonu neověřené.**
- **Barva výsledku výslovně `onSurface`**: zděděná vyšla v sheetu šedá jako původní text nad
  kartou a výsledek vypadal potlačeně.

### Varování před podvodem

Karta „Možný podvod" pod příchozí zprávou (`ScamWarnings`, prompt `ScamPrompt.kt`, karta
`items/ScamWarning.kt`). Vlastní vypínač v Nastavení → Obecné → AI v zařízení, výchozí vypnuto —
ze stejného důvodu jako návrhy odpovědí: model běží sám, bez ťuknutí.

- **Model se ptá jen u kandidátů z předfiltru** (`isScamCandidate`): odkaz a jedno typické slovo,
  nebo dvě typická slova (česky bez diakritiky a anglicky; krátká slova celá, jinak by „won"
  chytilo „wonderful"). Běžná zpráva se k modelu nedostane, takže nestojí baterii a nemůže
  se u ní zmýlit. Neposuzují se odchozí zprávy, Uložené, kanály, ověřené chaty ani ověření
  odesílatelé — „Kód pro přihlášení" od služby Telegramu je přesně zpráva, u které by
  varování bylo nejhorší.
- **Jen nejnovějších 20 zpráv konverzace**, ne historie, kterou uživatel proscrolluje: model by
  se pustil do všeho a varování u zprávy z loňska nikomu nepomůže.
- **Odpověď je jedno slovo** (`PHISHING`, `PRIZE`, `MONEY`, `SAFE`, `UNSURE`) a uživatel dostane
  NAŠI větu podle druhu, ne text od modelu. `UNSURE` je výslovná možnost a parser varuje jen
  při jednoznačné odpovědi (dva druhy naráz nebo druh s pochybností = mlčet).
- **Fronta po jedné zprávě v `appScope`** a výsledek v paměti procesu podle (chat, zpráva):
  rozjeté posouzení doběhne i po zavření chatu, po restartu se kandidáti posoudí znovu. Model,
  který ještě není `Ready`, zprávu nevyřadí — posoudí se při dalším otevření.
- **Tok čte až položka** (`ConversationItemScope.scamWarning` je `@Composable` a sbírá
  `scamVerdicts` sama), ne `ConversationScreen` — ten je v dexu na hraně (viz „Gotchas").
- **Ověřeno na emulátoru** (Gemma E2B na CPU, ~3 s na zprávu po načtení enginu): falešné
  zablokování účtu s odkazem → `PHISHING`, výhra s poštovným → `PRIZE`, kamarád posílá článek
  o bankovních poplatcích s odkazem (kandidát z předfiltru) → `SAFE`, bez varování. „Skrýt"
  kartu schová, tmavý motiv čitelný (akcent ustoupí `onErrorContainer`, viz `accentOn`).
  **Gemini Nano na skutečném zařízení neověřené.**

### Akce ze zpráv

Ikonka se sparkle (`auto_awesome`) na volné straně zprávy, ve které je termín, adresa, telefon
nebo e-mail, číslo účtu, letu nebo zásilky, Wi-Fi s heslem nebo kód; ťuknutí otevře sheet
s akcemi (`MessageContextSheet`): kalendář a „Připomenout…", mapy, uložení do kontaktů, QR
platba s kopírováním účtu, částky a VS, stav letu, připojení k Wi-Fi, kopírování kódu,
převod cizí měny a jednotek, kopírování kódu rezervace, sledování zásilky. Rozpoznávají PRAVIDLA
(`findMessageContext`, testy `MessageContextTest`), model jen posuzuje termín (`MessageContexts`,
prompt `EventPrompt.kt`). Vypínač v Nastavení → Obecné → Chaty, **výchozí zapnuto** — na rozdíl
od podvodu se kvůli tomu nic nestahuje a model se přidá, jen když si AI v zařízení uživatel zapnul.

- **Pravidla napřed, protože jdou ověřit kontrolním součtem.** Český účet mod 11 a kód banky
  ze seznamu ČNB (bez seznamu by „č. j. 1234/2025" prošlo jednou z jedenácti), IBAN mod 97,
  zásilka S10 kontrolní číslicí. Model by tu jen hádal, co už víme jistě. Holá čísla PPL a DPD
  od telefonu nerozeznáš, proto zatím nejsou.
- **Adresa jen s jasnou stavbou**: ulice, číslo, čárka, město — a k tomu PSČ, nebo ulice, která
  jako ulice vypadá (koncovka „-ská", „-ova", „-ního"…, klíčové slovo „nám.", předložka „Na").
  Ulice je JEDNO slovo: víc slov by spolklo i slovo před ní („Sraz Vinohradská 12"), takže
  „Karla Engliše 3" se nenajde. „Kapitola 12, Praha" adresou není. Anglicky číslo, název a druh
  ulice („221B Baker Street"). Hledá se v textu bez diakritiky (`foldDiacritics` nemění délku
  ani velikost písmen), ven jde úsek původního textu. Vložené příznaky regexů (`(?i:…)`) nejsou
  na všech platformách stejné, velikost písmen je proto ručně (`[Nn]a`).
- **Mapy hledají text** (`mapsSearchUrl`: Google Maps přes https na Androidu, Apple Maps na
  iOS — táž volba jako u polohy, `mapsUrl`). Místo, které k termínu doplnil model, má
  „Otevřít v mapách" taky; je ve tvaru ze zprávy („Černém Mostě u kina") a mapy si s ním poradí.
- **Kontakt bere telefony a e-maily, které vyznačil Telegram** (`TextEntityTypePhoneNumber` /
  `EmailAddress` → `Link("tel:…")` / `Link("mailto:…")`), a kde značka chybí, regulár
  (mezinárodní číslo, české devítimístné od 2). Číslo, které je kusem účtu nebo variabilním
  symbolem, kontaktem není; písmeno těsně u čísla znamená kód zásilky. Jeden telefon a jeden
  e-mail jsou jeden kontakt (vizitka). **Jméno jen z jasné stavby**: dvě slova velkým písmenem
  a za nimi oddělovač nebo „tel." („Jan Novák, tel. …"); bez oddělovače by jménem bylo
  „Zavolej Petrovi". Editor kontaktu ho dovolí doplnit.
- **Uložení kontaktu otevře systémový editor** (Android `ContactsContract.Intents.Insert`, bez
  oprávnění; iOS `CNContactViewController(forNewContact:)` v navigačním kontroleru, s popisem
  `NSContactsUsageDescription`). V Kotlin/Native má `CNMutableContact` vlastnosti jen ke čtení
  (dědí je z `CNContact`), zapisuje se settery (`setGivenName`…).
- **Číslo letu jen se dvěma signály**: kód ze seznamu leteckých společností (`AIRLINES`, IATA)
  a slovo o letu ve zprávě („let", „odlet", „letiště", „flight", „gate"…). Samotné „OK 123"
  nebo „LO 5" je spíš cokoli jiného. `VS` (Virgin Atlantic) v seznamu schválně není —
  v češtině je to variabilní symbol. Stav letu na FlightAware, který kód IATA převede sám
  („BA117" → „BAW117", ověřeno curlem); Flightradar24 robotům vrací 403 a ověřit se nedal.
- **Wi-Fi jen s názvem I heslem o 8–63 znacích** (rozsah WPA) — „wifi heslo je…" bez názvu
  sítě nic nenabídne. Textem („Wi-Fi na chatě: Chata_Lesni, heslo: …", s uvozovkami i víc
  slov) a ze zápisu QR kódu `WIFI:S:…;P:…;;`. **Pořadí alternativ v reguláru rozhoduje**:
  „klíčové slovo, pár slov a dvojtečka" musí být PRVNÍ — kratší varianta bez dvojtečky by
  uspěla s hodnotou „na" (z „Wi-Fi na chatě:"), filtr by ji zahodil až po nálezu a regulár
  by se k delší variantě na tomtéž místě už nevrátil.
- **Připojení k Wi-Fi rozhoduje systém**: Android 11+ `Settings.ACTION_WIFI_ADD_NETWORKS`
  s `WifiNetworkSuggestion` (WPA2) ukáže dialog „Uložit tuto síť?" — **oprávnění k Wi-Fi
  potřeba není** (ověřeno na emulátoru, Android 17). Starší Android otevře nastavení Wi-Fi
  a heslo dá do schránky. iOS připojení z aplikace bez entitlementu Hotspot Configuration
  nedovolí, zbývá kopírování a QR kód sítě (`WIFI:T:WPA;S:…;P:…;;`, escapuje se `\ ; , : "`),
  který naskenuje fotoaparát jiného telefonu.
- **Kód ke zkopírování jen za klíčovým slovem** („kód", „code", „voucher", „promo", „kupón",
  „PIN", „OTP"; mezi nimi nejvýš tři slova) a jen z VELKÝCH písmen a číslic: číselný 3–8
  číslic (devět je spíš telefon), smíšený 4–16 znaků. Velikost písmen hlídá třída znaků, ne
  `IGNORE_CASE` — s ním by kódem bylo „na" a regulár by se k delší variantě nevrátil (táž past
  jako u Wi-Fi). **Přihlašovací kód Telegramu se nenabídne nikdy**: zprávy služby Telegramu
  (777000) akce nemají vůbec (`offersContext`) a kód ve zprávě, která Telegram zmiňuje, taky
  ne — ověřovací kód je přesně to, co podvodník chce. Kód z banky nebo e-shopu kopírovat jde,
  je to totéž, co nabízí Android u SMS.
- **Cizí měna jen se značkou nebo kódem u čísla** („120 €", „$45", „300 PLN", „2 500 Ft",
  „15 eur") a jen jiná než měna uživatele (`HomeCurrency`). Číslo v zápisech „1 250,50",
  „1.250,50" i „1,250.50" (`parseAmount`: poslední oddělovač s jednou nebo dvěma číslicemi je
  desetinný). Bez `IGNORE_CASE`: „8 ft" jsou stopy, ne forinty. Tři velká písmena jen ze
  seznamu kódů — „10 SMS" měna není.
- **Měna slovy**: číslo a nejvýš tři slova za ním — řád („1,5 milionu", „2 tisíce"), přívlastek
  („švédských korun" = SEK, „kanadských dolarů" = CAD) a měna podle začátku slova kvůli
  skloňování („dolarů", „liber", „zlotých", „forintů", „franků", „juanů", „rupií", anglicky
  „dollars", „bucks", „yen"). **Řád se musí počítat** — bez něj by „1,5 milionu dolarů" bylo
  1,50 USD. Vynechané je dvojznačné: samotné „jen" (= pouze; jen celými tvary „jenů", „jeny")
  a anglické „pounds" (spíš váha, převádí se jako jednotka). Hledá se v textu bez diakritiky,
  ven jde úsek původního textu („1,5 milionu liber").
- **Kurz z denního lístku ČNB** (anglická verze: desetinná tečka, ASCII; zdarma, bez klíče).
  Kurzy jsou vůči koruně, takže jde převádět i mezi dvěma cizími měnami (EUR → USD přes CZK).
  Stahuje se až s otevřením sheetu („Načítám kurz ČNB…") a drží se 6 hodin. Obsah zprávy
  nikam neodchází, stahuje se jen celý lístek. Stahování je platformní (`TextDownloader`:
  `HttpURLConnection`, `NSURLSession`) — Ktor v projektu není a kvůli jednomu souboru nestojí.
- **Měna uživatele je podle REGIONU systému, ne jazyka aplikace — a `Resources.getSystem()`
  na to nestačí**: v procesu aplikace do něj Android promítne i jazyk nastavený jen pro
  aplikaci, takže s Plume v češtině a systémem `en_US` vyšla koruna místo dolaru (ověřeno na
  emulátoru). Od Androidu 13 proto `LocaleManager.getSystemLocales()`.
- **Jednotky jen té soustavy, kterou uživatel NEpoužívá** (`HomeRegion.metric`: imperiální
  jsou jen USA, Libérie a Myanmar — CLDR). Metrickému míle, mph, stopy, palce („65 palců"),
  yardy, libry, unce, galony a °F; americkému km, km/h, m, cm, kg, g, litry a °C. Počítá se na
  místě (`MeasureUnit`), síť ani model nejsou potřeba. Anglické „in" schválně chybí („2 in 1"),
  palce jen celým slovem; bez `IGNORE_CASE` („5G" není gram). Výsledek od stovky celým číslem,
  pod ní s jedním desetinným místem, a to jen když není nula.
- **Kód rezervace za klíčovým slovem** („rezervace" ve všech tvarech, „booking", „reservation",
  „confirmation", „PNR", „locator"; mezi nimi nejvýš tři slova): 5–16 znaků z velkých písmen
  a číslic, i s tečkami (Booking „4123.456.789"), čistě číselný 6–12 číslic. Hodnota, která je
  rezervací, se už nenabídne jako obecný kód („kód rezervace: ABC123" chytí oboje).
- **Let do kalendáře s časem ze zprávy** („Let OK 123" / „Přílet BA 117"): čas letu se odjinud
  nezjistí, bere se termín, který ve zprávě našla pravidla — i NEJISTÝ („let OK 123 v 9:40"),
  protože že jde o let, říká číslo letu a model to potvrzovat nemusí. Přílet jen tehdy, když
  zpráva mluví o příletu a ne o odletu („odlet v 9:40, přílet ve 12:00" je odlet). Termín, který
  nese let, se nenabízí ještě jako obecná událost — model by z „přistání na letišti" udělal
  název „přistání na letišti" s místem „letišti". V popisu je celá zpráva, takže i kód
  rezervace.
- **Trasa a délka letu ze zprávy a tabulky letišť** (`AIRPORTS`: kód IATA, město, souřadnice,
  pásmo IANA; ~110 nejběžnějších). Trasa z dvojice kódů („PRG → LHR", „PRG-LHR", „z PRG do
  LHR"), jednoho kódu s předložkou, nebo „z Ruzyně". Tři velká písmena mimo tabulku letištěm
  nejsou („MAN" a „SIN" v ní jsou, ale jen v trase). **Časy ve zprávě jsou MÍSTNÍ časy letišť**:
  odlet v pásmu letiště odletu, přistání v pásmu příletu — Praha 9:40 → Londýn 10:55 jsou
  2 h 15 min, ne 1 h 15 min; přistání „dřív" než odlet je druhý den (noční let). S dvěma časy
  je délka přesná, s trasou bez druhého času ODHAD ze vzdálenosti (780 km/h + 35 min, nahoru
  na 5 min; Praha–Londýn 2 h) a sheet to říká. Událost dostane začátek a konec jako okamžiky
  (`CalendarEvent.begin` / `end`), místo letiště odletu („Prague (PRG)") a trasu do popisu.
  Kalendář pak ukáže odlet v pásmu telefonu, tak jak ho uživatel zažije.
- **Města místo kódů**: česky za „z / ze / do / na" („z Prahy do Londýna", „do Říma", „na
  Krétu") podle začátku slova bez diakritiky kvůli skloňování (`CZECH_CITY_STEMS`, krátké
  kořeny jako Řím, Oslo a Riga jen celými tvary), anglicky za „from / to"
  (`ENGLISH_CITIES`). Jen ve zprávě o letu, takže „to split the bill" Split není. Město s víc
  letišti dostane hlavní (Londýn → Heathrow, Paříž → CDG); kód ve zprávě má přednost.
  Vynechaná jsou jména, která jsou i běžným slovem nebo jménem (Nice, Sofie, Porto). **Hledá se
  po slovech, ne regulárem**: „předložka a dvě slova" by u „z Prahy do Londýna" spolkla „do"
  a přílet by se nenašel (regulár překrývající se nálezy nevrací). Mezi slovy o letu jsou
  i tvary „letím / letíme" — bez nich by „Letím zítra OK 123…" let nenašlo.
- **Termín je jistý, nebo nejistý** (`EventCandidate.certain`). Jistý = den a čas, datum číslem,
  nebo termín vyznačený Telegramem (`MessageEntityType.DateTime`); ten se nabídne i bez modelu.
  Jen čas („v 15:00") je nejistý a nabídne se, jen když ho model potvrdí. Jen den („v pátek")
  není termín vůbec.
- **Minulost odfiltrují pravidla, model na ni nestačí.** Gemma 4 E2B odpověděla „schůzka" na
  „Včera v 15:00 jsem byl u doktora" i na „Volal jsem ti v 9:30, ale nebral jsi to" — u té
  druhé i s téměř stejným příkladem v promptu. Pravidla proto zahodí zprávu se „včera",
  „minulý", „yesterday", „last week" a u termínu bez dne i český minulý čas (příčestí na -l
  s „jsem/jsi/jsme/jste"). U termínu se dnem se minulý čas nekontroluje: „domluvili jsme se na
  zítra v 15" je schůzka.
- **Model se ptá napřed na čas (`WHEN: PAST/FUTURE`), pak na schůzku.** Samotné „je to
  schůzka?" bralo Gemmu k souhlasu; s klasifikací minulosti na prvním řádku odpověděla u staré
  zprávy o odstávce `PAST` a u schůzek `FUTURE`. Potvrzení chce obojí výslovně.
- **Prompt termín NEtvrdí, jen nabízí výklad** („If it plans something, the time would be…").
  S větou „It mentions <datum>" model termín přebíral jako fakt — pravidla „v 15:00" u minulé
  zprávy posunou na příští den a model pak „věděl", že je v budoucnu.
- **Místo jen takové, které ve zprávě stojí** (`placeInText`, začátek slova bez dvou posledních
  písmen kvůli skloňování: „kino" ↔ „u kina"). Název od modelu se bere, jak je — uživatel ho
  vidí a opraví v editoru kalendáře. Sheet říká, že název a místo navrhl model.
- **Během posuzování se jistý termín ukazuje** (ikonka nečeká vteřiny na model), nejistý ne —
  ten by naskočil a po odmítnutí zmizel. Posudky jsou v paměti procesu podle (chat, zpráva,
  otisk textu), takže upravená zpráva se posoudí znovu; nepovedené (`Unavailable`) dostanou
  novou šanci s dalším otevřením chatu (`reopen`).
- **Nic nevynáší chráněný obsah**: chat s `has_protected_content` (`ChatInfo.protectsContent`),
  mizející zprávy a zprávy s varováním před podvodem ikonku nemají — QR platba k podvodu je
  přesně to, co podvodník chce.
- **Kalendář nic neukládá sám**: Android `ACTION_INSERT` do `CalendarContract.Events` (bez
  oprávnění), bez kalendáře soubor `.ics` (`toIcs`, testy `CalendarEventIcsTest`: čas v UTC,
  zalamování po 75 BAJTECH, ne znacích). iOS `EKEventEditViewController` — od iOS 17 bez
  přístupu ke kalendáři; projekt cílí na 18.2.
- **Tytéž akce jsou v nabídce nad zprávou** (`MessageMenuAction.ContextActions`), s popiskem podle
  nálezu („QR platba…", „Přidat do kalendáře…", u kombinace „Akce ze zprávy…"; tři tečky, protože
  se otevře sheet). **I s vypnutou ikonkou**: vypínač brání tomu, co vyskakuje samo, nabídka se
  otevírá vědomě — věta pod vypínačem to říká. Nálezy počítá ViewModel až při otevření nabídky
  (`contextFindingsOf`), ne `menuActions` u každé zprávy; kandidát je proto u každé zprávy
  s textem a bez nálezu se položka neukáže. Právo jako u kopírování (`canCopy`).
- **Ikonka i sheet jsou mimo `ConversationScreen`** (v dexu na hraně): posudky sbírá položka
  (`ConversationItemScope.messageContext`), sheet si stav čte sám (`MessageContextHost`, jediné
  volání). `ConversationScreen` zůstal na 271 registrech.
- **„Kč" v regulárním výrazu je escapované** (`\u010d`): `verifyNoHardcodedText` diakritiku
  v kódu mimo komentáře nepustí a tohle jsou data, ne text pro uživatele.
- **Ověřeno na emulátoru** (`Plume_Galerie_Fold`, Gemma 4 E2B na CPU, ~30–40 s na posudek,
  zprávy od bota Notifikátor): ikonky u schůzky, platby a zásilek, žádná u „včera…" a „volal
  jsem…"; sheet s názvem a místem od modelu; „Přidat do kalendáře" předá `ACTION_INSERT`
  Kalendáři Google (editor se bez účtu Google neukázal); kopírování účtu; sledování otevře
  `tracking.packeta.com/cs/Z1234567890`; adresa „Vinohradská 1511/230, 100 00 Praha 10" otevře
  Mapy Google s `?q=Vinohradsk%C3%A1%201511%2F230…`; vizitka „Jan Novák, tel. +420 602 123 456,
  jan.novak@example.com" otevře editor Kontaktů Google se jménem, příjmením, telefonem
  i e-mailem; „Přiletím letem BA 117, přistání na letišti kolem 14:30" nabídne termín od modelu
  i let a „Stav letu" otevře FlightAware; „Wi-Fi na chatě: Chata_Lesni, heslo: medved2026"
  ukáže síť, heslo, QR kód a „Připojit k Wi-Fi" otevře systémový dialog uložení sítě
  (zrušený, síť se neuložila); „Slevový kód na první nákup: LETO20" zkopíruje „LETO20";
  „120 € na noc" se systémem `en_US` převede na „≈ 134,46 USD" s kurzem ČNB z 9. října 2026
  a „1,5 milionu liber" na „≈ 1 982 869,63 USD";
  „uběhl 10 km, venku bylo 20 °C" se systémem `en_US` ukáže „≈ 6,2 mi" a „≈ 68 °F";
  „kód rezervace: XKQ7ZT" nabídne kód rezervace ke zkopírování; „Odlet OK 123 zítra v 9:40
  z Ruzyně, kód rezervace XKQ7ZT" nabídne let se „Stav letu", „Odlet do kalendáře — zítra
  09:40" (bez obecné karty události) a kód rezervace; „Let OK 123 PRG → LHR zítra, odlet 9:40,
  přílet 10:55" ukáže pod číslem letu „PRG → LHR · 2 h 15 min"; „Letím zítra OK 123 z Prahy do
  Paříže, odlet 9:40, přílet 11:25" ukáže „PRG → CDG · 1 h 45 min".
  **Neověřené**: načtení QR bankovní aplikací, editor
  události, Gemini Nano, iOS.

### Návrhy odpovědí

Přijde zpráva, pole je prázdné → nad polem se ukážou až tři čipy s krátkou odpovědí. Ťuknutí ji
**VLOŽÍ do pole, neodešle**: text píše model a uživatel se pod něj podepisuje. Vlastní vypínač
v Nastavení (shrnutí ho nemá — otevírá se vědomě ťuknutím, kdežto návrhy vyskočí samy).

- **Čipy, ne bublina v konverzaci.** Průsvitná „duchová" bublina na místě, kde by odpověď byla,
  vypadá jako odeslaná zpráva a jednou za čas by si někdo myslel, že už odešla. Tenhle vzor znají
  lidé z Gmailu a Google Zpráv.
- **Krátká odpověď je RYCHLÁ.** Inference 0,2–0,4 s (měřeno v logu AICore mezi
  `CreateSessionWithSSV1` a `nativeUnload`), zatímco shrnutí 12–35 s. Rozdíl nedělá vstup, ale
  délka VÝSTUPU — našeptávání je tedy použitelné, i když shrnutí působí pomalu.
- **Poslední zpráva jde do promptu ZVLÁŠŤ a až za kontextem.** V jednom slepeném přepisu zanikne
  a model odpovídá na rozhovor jako celek: do chatu plného zpráv o výletu přišlo „Dáme v sobotu
  kafe?" a návrhy zněly „Jsem připraven." a „Dobré, pak se." Se zdůrazněnou poslední zprávou
  („Reply to THIS message") a kontextem zkráceným z 10 na 6 zpráv přišlo „Neděle mi vyhovuje."
- **Tykání/vykání se musí říct výslovně.** Bez instrukce model kamarádovi VYKAL („Co byste
  předpokládali?"). Instrukce jmenuje „tykání" i „vykání" česky — samotné „be informal" model do
  správného tvaru slovesa nepřeloží. Když z konverzace poznat nejde, tyká se: v Telegramu si lidé
  tykají mnohem častěji a tykání cizímu je menší prohřešek než vykání kamarádovi.
- **Jazyk se volí jinak než u shrnutí.** Shrnutí čte uživatel → jazyk aplikace. Návrh se ODEŠLE
  protějšku → jazyk konverzace; jazyk aplikace je jen nápověda, protože jinak model sklouzne
  (na nejednoznačný vstup odpověděl polsky).
- **Návrh se dělá JEDNOU na zprávu** (`ReplySuggestionsViewModel` si drží id). Bez toho by se
  inference pouštěla při každé změně stavu konverzace — přečtení, reakce, scroll.
- **Kdy nabídnout, je čistá funkce** `replySuggestionTarget` (testy `ReplySuggestionsTest`):
  poslední zpráva příchozí a neservisní, nic rozepsaného, neupravuje se, smí se psát. Vrací id
  zprávy, ne `Boolean` — obrazovka podle něj pozná, že jde o jinou zprávu, a nemusí si pamatovat nic.
- **Výstup modelu se parsuje tolerantně** (`parseReplySuggestions`, testy `ReplyPromptTest`):
  model odrážky nedrží, očísluje je, obalí uvozovkami nebo přidá úvodní větu s dvojtečkou.
  Nikdy nevrátí prázdno kvůli formátu — když řádky rozpoznat nejdou, je návrhem celý text.
- **Na zařízení bez AICore se nesmí nic stát a nestane.** Ověřeno na TŘECH systémech —
  Android 16, Android 9 (API 28, Play služby 21.45.15) a **Android 8 (API 26, tedy `minSdk`
  projektu)** —, všude bez balíčku `com.google.android.aicore`. Pokaždé stejně: `status` vrátí
  `Unsupported` (takže se ikona Shrnout ani čipy nenabídnou), `countTokens` `null` a `generate`
  skončí VÝJIMKOU `GenAiException [ErrorCode -101] AICore is either not installed or the
  installed version is too low`. Žádný pád, v logu aplikace čisto. Stáří systému ani verze Play
  služeb na tom nic nemění.
- **Na nepodporovaném zařízení se sekce v Nastavení VŮBEC NENABÍZÍ.** Dřív se vykreslila vždycky
  a uživatel zapnul přepínač, aby se teprve pak dozvěděl „tohle zařízení model nemá" — nabídka
  funkce, kterou nelze mít. Stejné pravidlo jako u archivace od neznámých (viz „Archiv“). Ptá se na VÝCHOZÍ
  variantu, ne na zvolenou: preview nebo rychlá může chybět zvlášť a kvůli tomu by sekce zmizela
  i tomu, kdo model má. Dokud stav nedorazí, taky se nekreslí — objevení ruší míň než zmizení.
- **Ověřuje se to sondou, ne proklikáním.** Do Nastavení se bez přihlášení nedostaneš, takže se
  do `startKoin` dočasně přidá volání `status`/`info`/`countTokens`/`generate` v `runCatching`
  a výsledek se zaloguje. Sonda se NECOMMITUJE — postaví se z ní APK a zdroják se hned vrátí.
- **Staré arm64 obrazy na Apple Silicon zamrzají.** Android 9 i 8 spadly na
  „detected a hanging thread QEMU2 main loop", dokud emulátor nedostal
  `-gpu swiftshader_indirect -cores 2`. Není to chyba aplikace a stojí to deset minut hledání.
- **Chyba ale nemusí přijít jako výjimka.** Kde chybí systémová část (staré Play služby, ořezaný
  ROM), vyletí `Error` — `NoClassDefFoundError`, `UnsatisfiedLinkError`, `VerifyError` —, a ten
  `catch (Exception)` NECHYTÍ. `MlKitOnDeviceAi` proto chytá i `LinkageError`: aplikace nesmí
  spadnout jen proto, že si někdo zapnul volitelnou funkci na starším telefonu.
- **Zjišťování dostupnosti má `catch`, i když „nemůže" selhat.** `available` je `flow { … }`
  v `stateIn(viewModelScope)`; cokoli, co by z něj vyletělo, shodí scope, tedy ViewModel celé
  obrazovky. Nepodařilo se zjistit = nenabízet.
- **AICore umí model rozbít a `checkStatus` pořád hlásí `AVAILABLE`.** Na Pixelu 11 Pro Foldu
  se po aktualizaci AICore (a restartu) Gemini Nano přestal nacházet ve vlastních souborech
  (`NOT_FOUND: …/aicore-models/symlinks/…/main/config.binarypb`) a každé generování skončilo
  `GenAiException` s kódem 5 (`COMPUTE_ERROR`, ML Kit ho mezi `ErrorCode` nemá). Featura byla
  v AICore `ACTIVE`, Plume model nabízela — a uživatel viděl jen „Shrnutí se nepovedlo", den
  a půl. **Spravilo to vymazání úložiště AICore** (`adb shell pm clear com.google.android.aicore`
  nebo Nastavení → Aplikace → AICore), pak si model stáhl znovu.
- **Kde to hledat:** `adb shell dumpsys activity service com.google.android.aicore` má historii
  posledních inferencí (`caller_package_names=eu.tintera.plume … status=N`; 1 = úspěch) a stav
  každé featury (`feature_647: ACTIVE` / `PENDING (DOWNLOADING)`). Plume volá featuru 647
  (`nano-v4-full`). Chyba samotná je v logu procesu AICore (`AiCoreInferenceHelper`).
- **Selhání generování nese důvod** (`OnDeviceAiException` s `AiFailureReason`, převod z kódu
  ML Kitu v `MlKitOnDeviceAi.toAiFailure`). Sheet shrnutí ho říká větou, u rozbitého Nano
  i s kódem a radou vymazat úložiště AICore nebo přepnout na Gemmu. Do Crashlytics jako
  problém (`e` s výjimkou) jde jen rozbitý / nedostupný model (`isReportable`), vytížený,
  dlouhý vstup a plný disk zůstávají `w`. Návrhy odpovědí se pouštějí samy u každé zprávy,
  takže hlásí jen první selhání za běh procesu.
- **Sloh je pořád slabina.** Tykání i věcnost sedí, ale mezi třemi návrhy bývá jeden nesmysl
  („Ne, zítra mi je zajímavé."). To je limit modelu, ne promptu.

- **Fakta model drží, sloh ne.** Na připraveném testu (ceny, číslo účtu, datum, jména) nevymyslel
  nic — ale na skutečné konverzaci komolí tvary („telefon nebudou mít vibrace“), plete si, kdo co
  řekl, občas propíše do textu instrukci („shrnutí konzultace **v češtině**“) a u pár zpráv nafoukne
  zdvořilosti na plnohodnotné body. Když to nebude stačit, další krok je Gemma 4 přes LiteRT-LM
  za stejným rozhraním.
