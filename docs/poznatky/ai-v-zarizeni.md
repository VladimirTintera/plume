# AI v zařízení

Poznatky k jedné oblasti aplikace. Obecná pravidla projektu a rejstřík ostatních souborů jsou
v [CLAUDE.md](../../CLAUDE.md); poučení z opravy v téhle oblasti patří sem.

## AI v zařízení (prototyp)

Experimentální „Shrnout" v hlavičce konverzace: shrnutí nepřečtených (jinak posledních 60 zpráv)
jazykovým modelem v telefonu. Zapíná se v Nastavení → Obecné → AI v zařízení (výchozí vypnuto),
tam se i stahuje model. Hranice je `core/ai` (`OnDeviceAi`).

### Dva poskytovatelé: Gemini Nano a Gemma

Zapnuté můžou být **Gemini Nano** v AICore (ML Kit, `MlKitOnDeviceAi`), **Gemma 4** a **Qwen**
přes LiteRT-LM (`LiteRtOnDeviceAi`), které si stáhne aplikace, nebo víc naráz (`AiModels.enabled`).
Každá funkce se ptá na model PRO SVOU ÚLOHU (`ai.variantFor(AiTask.Summary, settings)`,
`core/ai/AiTasks.kt`, testy `AiTasksTest`) a hotové volání pak předá `RoutingOnDeviceAi` (testy
`RoutingOnDeviceAiTest`). Nová funkce s modelem si tedy přidá řádek do `AiTask` a o
poskytovatelích nemusí vědět nic.

- **Poskytovatel je pole varianty, ne druhé rozhraní.** Funkce dostane hotovou variantu pro
  svou úlohu; kdyby řešila poskytovatele sama, jedna by na to zapomněla.
- **Router variantu NORMALIZUJE** (`forGeminiNano`, `forGemma`, `forQwen`). ML Kit drží klienta na
  variantu v mapě, takže by jinak změna modelu Gemmy vyrobila druhého klienta téhož Nano.
- **Katalog Gemmy (`LiteRtModel`) je převzatý z Google AI Edge Gallery**
  (`model_allowlists/1_0_19.json`): soubor, commit na Hugging Face, velikost a doporučená paměť.
  **Commit je součást adresy**, takže se stahuje pořád tentýž soubor; nová verze = vědomá změna
  katalogu. **Tagy na GitHubu LiteRT-LM s verzemi na Google Maven nesedí**; platné verze jsou
  v `maven-metadata.xml` artefaktu. Knihovna byla napřed 0.11.0 z Gallery, kvůli Qwenu je 0.19.1
  (viz „Qwen a další modely přes LiteRT-LM").
- **Gemma se nenabízí pod Androidem 12, bez arm64 a pod `minMemoryGb`** (8 GB pro E2B, 12 GB
  pro E4B; `totalMem` s 15% rezervou, výrobce udává víc, než jádro nechá). Pod tím by systém
  aplikaci zabil při načítání modelu. Kde model nejde, je `Unsupported` a volba se vůbec neukáže.
- **Uložená volba, kterou zařízení nesplní, se opraví** (`supportedModels`, testy
  `SupportedModelsTest`): Nano na telefonu bez AICore → Gemma, výchozí Nano na iOS → Apple,
  E4B s málo pamětí → E2B. Z víc zapnutých se jen vypustí ten, který tu není.
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
  **Se souborem modelu jde i jeho zkompilovaná keš** (`LiteRtModel.ownsCacheFile`, testy
  `ModelCacheFilesTest`, `LiteRtDeleteTest`). LiteRT-LM si při prvním načtení uloží model
  přeložený pro procesor do `cacheDir` enginu (`<soubor>_<čas>_<velikost>.xnnpack_cache`,
  u Gemmy i zvukový kodér a adaptér) — u Gemmy 4 E4B 2,3 GB, u Qwen3 4B taky. Dokud se mazal
  jen model, zůstávala: na desktopu po smazání E4B a Qwenu 4,5 GB v `ai-cache`, které nikdo
  neuklidí; na Androidu ji časem smaže systém, do té doby se počítá do místa aplikace.
  Engine bez `cacheDir` (sondy) ji ukládá vedle modelu, proto se hledá v obou adresářích.
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
  - **„Stahovat modely jen přes Wi-Fi"** (Nastavení → AI v zařízení, pod modely; výchozí
    zapnuté, `AppSettings.modelDownloadWifiOnly`) je táž podmínka, jen `NetworkType.UNMETERED`.
    Dřív stačilo jedno ťuknutí na mobilních datech a stahovaly se gigabajty.
    - **Placenost, ne druh sítě** (`MeteredNetwork`, na Androidu `isActiveNetworkMetered`).
      `NetworkMonitor` by Wi-Fi z hotspotu telefonu vedl jako Wi-Fi, systém i podmínka
      `UNMETERED` ji vedou jako placenou. Dotaz a skutečné chování úlohy se tak shodnou. Bez
      sítě vrací `false` (systém `true`): v letovém režimu se nemá na co ptát.
    - **Mimo Wi-Fi se Stáhnout zeptá** (`ConfirmDialog`: „Stáhnout přes data" / „Počkat na
      Wi-Fi", s velikostí modelu). Zavření dialogu mimo tlačítka = počkat: o model uživatel řekl
      a čekání nic nestojí. „Přes data" platí jen pro to jedno stažení, Nastavení se nemění.
      Připojení obrazovky k běžícímu stahování (`refresh` → `startDownload`) se neptá.
    - **Stav „Čekám na Wi-Fi"** (`ModelDownload.wifiOnly` s `waitingForNetwork`). Úloha se na
      datech vůbec nespustí, takže stav musí čekání říct sám hned při plánování
      (`download`), jinak by tvrdil, že se stahuje.
    - **Přepnutí volby za běhu úlohu neruší** (`setDownloadNetwork` → `WorkManager.updateWork`).
      Zrušení by se ohlásilo jako zastavení uživatelem (`CANCELLED_BY_APP`) a obrazovka by
      ukázala „Pokračovat". Čekající úloha se po povolení dat rozběhne. **Běžící úlohu nová
      podmínka nezastaví** (platí až pro příští běh), proto se při zákazu dat na placené síti
      zruší jen korutina stahování v `runDownload` a úloha skončí s `Result.retry()`
      (`WifiRequiredException`). Opakování má lineární prodlevu 10 s: výchozí exponenciální
      by návrat na Wi-Fi zdržela.
    - **Runtime z Play volba neřídí.** Je to desítka MB a velké stahování přes data si Play
      potvrdí sám (`SplitInstallConfirmations`). Gemini Nano stahuje AICore po svém.
    - **Na desktopu se volba nenabízí** (`NoMeteredNetwork`): JVM druh sítě nehlásí
      (`NetworkMonitor` tam vrací vždycky `Other`) a volba by slibovala něco, co se nestane.
    - **Zrušení stahování zavře spojení** (`invokeOnCompletion` v `fetchFile`). Blokující `read`
      zrušení korutiny nepozná: na síti, která po změně přestala posílat data, čekal do
      `readTimeout` a zákaz dat za běhu se na emulátoru projevil až po 23 s. Se zavřením
      spojení za 0,1 s. `IOException` ze zavřeného spojení se hlásí jako zrušení
      (`ensureActive`), ne jako výpadek sítě. Totéž zrychlilo „Zastavit".
    - **Ověřeno na emulátoru** (Android 17, Qwen3 0,6B, síť zpomalená `adb emu network speed
      hsdpa`, ať stahování nedoběhne hned):
      - Na LTE dialog a „Počkat na Wi-Fi": úloha čeká v plánovači s `NOT_METERED`
        (`dumpsys jobscheduler`), stav „Čekám na Wi-Fi".
      - Zapnutí Wi-Fi úlohu rozběhne, ztráta Wi-Fi uprostřed ji zastaví (`.part` dál neroste).
      - Vypnutí volby za čekání úlohu pustí bez „Pokračovat" (`updateWork`).
      - Zapnutí volby za stahování na placené síti dá `RETRY` a čekání.
      - Po zabití procesu (reinstalace) se stav čekání obnoví.
      - „Pokračovat" na placené síti se zeptá a „Stáhnout přes data" model stáhne celý (velikost
        sedí na bajt).
    - **LTE emulátoru ven nevede**, i když ho systém hlásí jako ověřené (`ping` 100 % ztrát, se
      zapnutou Wi-Fi i bez ní). Stahování „přes data" tam skončí na timeoutu. Placená síť se
      proto simuluje Wi-Fi: `adb shell cmd netpolicy set metered-network '"AndroidWifi"' true`
      (zpátky `undefined`, výchozí stav emulátoru). Změna placenosti Wi-Fi na chvíli shodí
      (`onLost`) a WorkManager běžící úlohu zastaví a hned pustí znovu.
    - **Snímek obrazovky Plume je na emulátoru bílý** (`screencap`), stav se čte z `uiautomator
      dump`. Fyzický telefon ověřený není. Testy `LiteRtDownloadNetworkTest`.
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
- **Engine je v paměti nejvýš jeden a po chvíli nečinnosti se uvolní** — drží gigabajty RAM.
  Načtení trvá až deset sekund, proto ne hned po odpovědi (návrhy chodí po sobě): **dvě minuty,
  když Gemma počítá nějaký text, 30 s, když dělá jen zvuk a obraz** a text má Nano
  (`gemmaIdleUnloadMs`, testy `GemmaIdleTest`). Přepisy a popisy fotek se pouštějí ťuknutím,
  ne s každou zprávou; 30 s pokryje pár hlasovek za sebou. Prodleva se čte po každém použití
  (zapnutí Nano nebo jiné přiřazení ji mění za běhu) a rozhoduje stejně jako funkce samy
  (`variantFor`): Gemma, která převzala text, protože se Nano stahuje, drží dvě minuty.
  **Kolik stojí opětovné načtení na GPU, změřené není** — na emulátoru (CPU, 8 GB) trvalo
  načtení s přepisem ~11 s; jestli je 30 s na telefonu málo, ukáže až Fold.
  **Generování je jedno po druhém** (zámek): dvě konverzace nad jedním enginem LiteRT-LM neslibuje.
- **GPU, a když nejde, CPU.** GPU backend potřebuje `libOpenCL.so` — `uses-native-library`
  v manifestu aplikace (`required=false`). Na zařízení ověřené není, jestli se GPU chytí.
- **Bez OpenCL se GPU engine NAČTE BEZ CHYBY a selže až u první zprávy.** Na emulátoru x86_64
  s LiteRT-LM 0.19.1 prošlo `Engine.initialize()` s `Backend.GPU()` a teprve generování skončilo
  `LiteRtLmJniException: … UNKNOWN: Can not find OpenCL library on this device` z
  `Conversation$JniMessageCallbackImpl.onError`. Záloha jen kolem `initialize()` (tak to bylo do
  té doby) tedy CPU nikdy nezkusila a uživatel viděl „Model Qwen selhal". (Se starší verzí
  selhala už inicializace, na OpenGL v `delegate_opengl.cc`, a CPU naskočilo.) Týká se nejspíš
  i telefonů bez OpenCL.
  - **Selže-li generování na GPU dřív, než přišel první token, engine se zavře, načte na CPU
    a dotaz se zopakuje jednou** (`withEngine` v `LiteRtOnDeviceAi`, testy
    `LiteRtGpuFallbackTest` s falešným `LlmEngineFactory`). Platí pro shrnutí, přepis i popis
    fotky; zvuk a obraz si engine přenese.
  - **Po prvním tokenu ani po zrušení se neopakuje**: obrazovka už text ukazuje a odpověď by
    začala znovu.
  - **GPU se pak do konce procesu nezkouší** (`gpuFailed`, jen v paměti): každé další načtení
    by jinak trvalo o neúspěšné načtení na GPU déle. Pamatuje se, jen když CPU uspělo (vydalo
    token nebo doběhlo) — selže-li CPU stejně brzy, chyba na backendu nejspíš nezávisí a GPU
    dostane příště šanci znovu. Totéž platí pro GPU, které selže už při načtení. **Pamatuje se
    pro každý runtime zvlášť**: Metal v llama.cpp a GPU v LiteRT-LM jsou různé backendy
    a selhání jednoho nesmí vzít GPU druhému (`LiteRtGpuFallbackTest`).
  - **Test jede nad rozhraním enginu z `:client:ai-engine`** (`LlmEngineFactory`), falešný
    engine dodá `FakeLiteRtRuntime(engines = …)`.
  - **Ověřeno na emulátoru x86_64** (Qwen3 0.6B, sonda v `AndroidLiteRtPlatform.canRun`, jinak
    se tam model nenabízí): shrnutí chatu napoprvé hlásí v logu „GPU selhalo při generování,
    zkouším CPU" a doběhne na CPU. **Nezdařený pokus na GPU ale stojí ~24 s** — LiteRT-LM bez
    OpenCL nejdřív sáhne po WebGPU („OpenCL not supported on this platform. Using WebGPU
    instead.") a chybu ohlásí až po přípravě. Platí se to jednou za proces, právě proto
    `gpuFailed`. Na telefonu neověřené.
- **LiteRT-LM v Kotlinu neumí tokeny spočítat předem** (`Conversation.getTokenCount` zná až
  rozběhnutou konverzaci), proto `countTokens` vrací HORNÍ odhad z bajtů UTF-8
  (`estimatedTokenCount`, 1,8 bajtu na token; testy `TokenEstimateTest`) a shrnutí podle něj
  přepis zkrátí nebo rozdělí na části. **Dřív vracel `null` a jelo se s odhadem tří znaků na
  token — a ten u češtiny neplatí**: změřeno na desktopu `getTokenCount` nad českým chatem
  s odkazy Gemma 2,2 znaku na token, Qwen 2,0. Přepis, který aplikace pro Gemmu posílala
  (16 330 znaků), měl 7 547 tokenů z keše 8 192, a na odpověď místo 1 536 tokenů zbylo ~650.
  Platí i pro Android; na Foldu se to neukázalo, protože se zkoušely kratší chaty. Limit je
  velikost KV keše (8 192 u Gemmy, 4 096 u Qwenu) minus místo na odpověď — vstup i výstup se do
  keše musí vejít SPOLU. **Strop výstupu je dvojí**: `maxOutputToken` pro knihovnu (od 0.19)
  a `MAX_OUTPUT_CHARS` pro obrazovku (pak `Generated.truncated`).
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
- **Shrnutí po částech** (`summaryParts`, `partSummaryPrompt`, `combinedSummaryPrompt`, testy
  `SummaryPromptTest`). Když se přepis nevejde do jednoho promptu (`keptLineCount` < počet
  zpráv), rozdělí se na části o délce, která se do limitu vešla, plněné od nejnovějších zpráv;
  každá dostane krátké poznámky a z nich se složí shrnutí celku. Dřív se u chatu s tisíci
  nepřečtenými shrnul jen konec a zbytek tiše odpadl — patička sice napsala „vešlo se 80
  z 2 000", ale text se tvářil jako shrnutí všeho.
  - **Nejvýš tři části** (`MAX_SUMMARY_PARTS`): každá je celé generování navíc. Co se nevejde
    ani do nich, odpadne jako dřív (nejstarší) a patička to řekne.
  - **Části se znovu nepočítají na tokeny** — mají délku přepisu, který se do limitu ověřeně
    vešel, a instrukce jsou podobně dlouhé. Poznámky jsou krátké, složený prompt je menší než
    kterákoli část.
  - **Poznámky jsou už v jazyce odpovědi**, jména a fakta se tak nepřekládají dvakrát.
  - **Sheet ukazuje průběh** („Čtu část 1 z 2…", `ChatSummaryState.ReadingParts`) a patička
    „Shrnuto po 2 částech" — jinak by dlouho jen „připravoval".
  - **U Gemmy na emulátoru nastanou části až nad ~24 000 znaků** (strop přepisu
    `MAX_TRANSCRIPT_CHARS_CAP`, Gemma hlásí velký limit a tokeny nepočítá). **Platilo před
    odhadem tokenů** (`estimatedTokenCount`): teď se Gemma rozdělí zhruba nad 11 000 znaků
    českého přepisu. 48 zpráv
    (~12 000 znaků) se shrnulo jedním promptem, 108 zpráv (~27 000) ve dvou částech: „Čtu
    část 1 z 2…" ~25 s, část 2 ~55 s, složení ~30 s, pět celých odrážek.
  - **Mechanika funguje, kvalita E2B ne zcela** (ověřeno na emulátoru Fold, zkušební plánování
    výletu od testovacího bota): fakta ze starší části drží, ale spletla si den („posunuli na
    pátek") a novější změny (jiná chata, jiný sraz) skoro vynechala, přestože instrukce říká,
    že novější události mají přednost. Části se plní od nejnovějších, takže starší část bývá
    malá a nová velká — model se v ní ztrácí stejně jako v jednom dlouhém promptu. Jestli to
    E4B nebo Nano zvládnou líp, se ukáže až na telefonu.
- **Shrnutí bez úvodu a o uživateli ve druhé osobě** (`NO_INTRO_INSTRUCTION`,
  `readerInstruction`, `withoutIntro`; testy `SummaryPromptTest`). Porovnání na Foldu
  (Nano fast i Gemma 4 E4B) ukázalo dvě chyby, které měly OBA modely: úvodní „Zde je shrnutí
  konverzace:" a podpis uživatelových zpráv „Já" opsaný jako jméno v uvozovkách („Martin
  Glasnák a "Já" se…"). Prompt shrnutí na rozdíl od vysvětlení pokyn „začni rovnou odrážkou"
  neměl; přibyl do celku, částí i složení. O „Já" teď model ví, že je to čtenář, a píše
  o něm ve druhé osobě, česky výslovně tykáním (aplikace tyká). **Úvod navíc odstraní kód**
  (první řádek, který není odrážka a končí dvojtečkou, když po něm odrážky jsou) — i během
  psaní a i u poznámek k částem. Vysvětlení zprávy pokyn mělo a podpis „Já" v něm není.
  **Na Foldu po opravě neověřené.**
- **Odrážka má nejvýš 20 slov a useknutý text končí poslední celou odrážkou**
  (`completeLines`). Bez délky v instrukci psal model u delšího přepisu odrážku skoro ke každé
  zprávě a narazil na strop uprostřed věty; useknutá věta vypadala jako chyba modelu. Že je
  shrnutí neúplné, říká sheet dál zvlášť (`truncated`).
- **Čeština spotřebuje víc tokenů než angličtina**, na kterou jsou výchozí hodnoty laděné; proto
  `MAX_OUTPUT_TOKENS = 800`, ne 400. Není to „čím víc, tím líp" — platí se delším generováním
  (na Foldu 12–35 s).
- **Ověřeno na zařízení** (Pixel 11 Pro Fold, Android 17, AICore `thirdpartyexperimental`):
  `nano-v4-full`, limit vstupu 8 192 tokenů (6. 11. už 32 768), rychlejší varianta je opravdu jiný model
  (`nano-v4-fast`) a je připravená hned. **Shrnutí funguje i v letovém režimu** — model počítá
  v telefonu, nic se neposílá.
- **Preview varianta JEDE preview modelem — `getBaseModelName()` to jen neřekne.** S `preview`
  vrací jméno dál `nano-v4-full` / `nano-v4-fast` (jméno určuje jen `fast`), `checkStatus` dá
  `AVAILABLE` a limit je stejný jako u stabilní. Podle jména se proto preview poznat NEDÁ.
  Rozhoduje plánovač úloh AICore, který do logcatu píše číslo i název featury každého požadavku:
  ```
  InferenceTaskScheduler: submit (… 636/1 - SAPI Open Prompt (Regenerative Mode))
  InferenceTaskScheduler: submit (… 645/1 - SAPI Open Prompt Stable Fast Mode)
  InferenceTaskScheduler: submit (… 647/1 - SAPI Open Prompt Preview Full Mode)
  InferenceTaskScheduler: submit (… 646/1 - SAPI Open Prompt Preview Fast Mode)
  ```
  U 647 AICore načte jiný checkpoint (`ulm1b_ggml.cc: CreateModelFromCheckpoint`, delegát
  EdgeTPU) a paměť modelů (`dmabuf_rss_kb_at_inference_start`) vyskočí z 1,30 na 2,44 GB;
  první odpověď trvá ~2,4 s místo ~0,3 s. Ověřeno sondou 6. 11. 2026 (Pixel 11 Pro Fold,
  AICore `26.10.15…thirdpartyexperimental`): samostatná aplikace s jiným balíčkem, která projde
  čtyři varianty (`checkStatus`, `getBaseModelName`, `getTokenLimit`, jedna odpověď) — vedle
  nainstalovaného Plume z Play, bez odinstalace. Limit vstupu tentokrát 32 768 u všech variant
  (15. 10. bylo 8 192), mezitím se tedy změnil AICore nebo jeho model.
- **Dřívější závěr „preview tiše jede stabilním modelem" byl MYLNÝ.** Opíral se o shodné jméno
  a o tuhle chybu v logu s `genai-prompt` 1.0.0-beta4:
  ```
  GenAiProcessorKt: AICore failed with error type 3-PREPARATION_ERROR
    and error code 606-FEATURE_NOT_FOUND: Feature -1 is not available.
  ```
  Kontrolní běh sondy s beta4 ale ukázal, že generování i tak šlo na 647 (Preview Full Mode).
  `-1` patří jiným typům požadavků: ML Kit si překládá (varianta, typ požadavku) na číslo
  featury statickou funkcí (v beta4 `zzyt.zzb`, v beta5 `zzaaq.zzb`; `javap -c` ji najde jako
  jedinou s konstantami `sipush 636…655`). Beta4 znala pět typů (bez mezipaměti, vytvoření
  mezipaměti, s mezipamětí, strukturovaný bez a s mezipamětí) a preview měla číslo jen pro dva
  bez mezipaměti (plná 647 / 652, rychlá 646 / 653), pro ostatní `-1` — to jsou kontroly, které
  ML Kit spolkne. Beta5 má typy jen dva (`NORMAL_INFERENCE`, `STRUCTURED_OUTPUT_INFERENCE`)
  a číslo pro všechny kombinace, takže chyba z logu zmizela. **Poučení:** jméno modelu ani
  spolknutá chyba neříkají, čím AICore počítal — říká to `InferenceTaskScheduler`.
- **Proto zmizelo `previewSameAsStable`** (porovnání jmen preview a stabilní varianty) i hláška
  „Preview verze se tu nepoužije" pod přepínačem. Tvrdila opak pravdy přesně na zařízení, kde
  preview je. Spolehlivý příznak, že zařízení preview nemá, API nedává; přepínač to říká
  v popisku („pro tohle zařízení nemusí být").
- **Bump na `genai-prompt` 1.0.0-beta5 a `genai-schema` 1.0.0-alpha2:** veřejné API se jen
  rozšířilo (volání funkcí, role v `Content`, `sessionId`, `getAvailableFeatures()`
  s `Feature.THINKING_MODE` aj.), `isStructuredOutputFeatureAvailable` je zastaralé ve prospěch
  `getAvailableFeatures()`, `Candidate.text` už není nullable. `genai-schema` alpha2 přidal
  `GuideDetail.pattern` s výchozí hodnotou a `GenerableProvider` se hledá dál přes
  `ServiceLoader`, ruční providery se měnit nemusely. `ModelReleaseStage` (STABLE 0, PREVIEW 1)
  a `ModelPreference` (FAST 1, FULL 2) beze změny.
- **Labs nabízí čtyři modely, „Správa AI modelů" ukazuje jediný balík „Fast + Full [TPU]".**
  Proto je rychlá varianta připravená okamžitě a nic se nestahuje: full i fast jsou TENTÝŽ
  balík.
- **Preview Full je postavený na Gemma 4 E4B**, zatímco stabilní na E2B — tedy větší model
  a nejnadějnější směr pro kvalitu češtiny. **Přes ML Kit k němu cesta vede** (přepínač preview,
  viz výš); seznam modelů ale API nemá: `GenerativeModel` zná jen `getBaseModelName()` pro právě
  nakonfigurovanou variantu, a to jméno preview neodliší.
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
  odvodil správně a AICore ji nemá; `-1` u preview v beta4 je naopak mezera v převodu
  v `genai-prompt` (jen u typů požadavků s mezipamětí, generování samo šlo na 647, viz výš).
- **Detail k `info()`:** s doinstalovaným AICore vrátila `ModelInfo(name=null, tokenLimit=8192)`,
  tedy NENULOVÝ objekt bez jména. Na UI se to neprojeví (rozhoduje `status`), ale kdo by se ptal
  jen na `info() != null`, dostal by na nepodporovaném zařízení „model je".
- **Co na emulátoru ověřit JDE:** chování bez modelu (že se sekce v Nastavení nenabídne a nic
  nespadne — viz „Návrhy odpovědí"), vzhled podstrčeným stavem a čisté funkce testy. Na zařízení
  zbývá kvalita výstupu, rychlost, limit tokenů a useknutí.


### Qwen a další modely přes LiteRT-LM

LiteRT-LM spustí jakýkoli soubor `.litertlm`, ne jen Gemmu. Druhý stahovaný model je **Qwen**
(`AiProvider.Qwen`), jen text, Apache 2.0, ve třech velikostech jako volba u modelu (jako E2B / E4B
u Gemmy): **Qwen3 4B** (2,1 GB, 12 GB paměti) kvůli češtině — jestli ji umí líp než Gemma, má
ukázat „Porovnat s dalšími modely" na skutečné konverzaci —, **Qwen2.5 1,5B** (1,5 GB, 6 GB)
a **Qwen3 0,6B** (328 MB, 4 GB) pro telefony, na které Gemma nestačí.

- **Slabší telefon dostane sekci AI, i když se nevejde výchozí velikost.** Poskytovatel je
  dostupný, když zařízení unese aspoň JEDNU jeho velikost (`availability`); dřív se ptalo na
  výchozí variantu, a ta by Qwen na telefonu s 6 GB schovala celý. Uloženou velikost, na kterou
  paměť nestačí, `supportedModels` vymění za první dostupnou téhož modelu — Qweny jsou v katalogu
  od největšího, takže telefon dostane největší, který unese (test `SupportedModelsTest`).
- **Kontext je vlastnost souboru** (`LiteRtModel.contextTokens`): Qweny mají 4 096 tokenů, Gemma
  8 192. Engine dostane menší z obou (`maxNumTokens`) a podle toho i limit vstupu (`info`), takže
  shrnutí u Qwenu dřív přejde na části. Soubor Qwen2.5 má KV keš pevně na 4 096 (`ekv4096`).
- **Proč tyhle tři a ne jiné.** Qwen2.5 1,5B je z katalogu Gallery (`1_0_23.json`, 6 GB paměti),
  Qwen3 0,6B týž export jako Qwen3 4B a v oficiální tabulce LiteRT-LM. **Gemma 3 1B** (z Gallery,
  6 GB) je na Hugging Face za licenčním souhlasem — bez přihlášení vrací 401 a stahování ho neumí.
  **Qwen3.5 0,8B** (litert-community) má vrstvy s rekurentním stavem a podle karty modelu chce
  NOVÝ engine pro každou konverzaci, jinak navazuje na stav předchozí (LiteRT-LM#3165); aplikace
  engine mezi dotazy drží, takže by odpovídal nesmysly.

- **Katalog je jeden pro všechny stahované modely** (`LiteRtModel`, dřív `GemmaModel`): soubor,
  commit, velikost, paměť, komu patří (`provider`) a jestli bere zvuk a obraz (`multimodal`).
  Runtime pracuje se souborem (`ModelVariant.model`), poskytovatel jen vybírá, který to je.
  **Jména položek se ukládají** (volba velikosti Gemmy, jméno úlohy stahování), proto zůstalo
  `E2B` / `E4B`.
- **Engine je pořád jeden.** Gemma a Qwen se v paměti střídají; přechod znamená zavřít jeden
  a načíst druhý (až desítky sekund na CPU). Proto Qwen v automatice stojí PŘED Gemmou: kdo si
  ho vedle Gemmy zapne, chce ho na text, a Gemma zůstane hlasovkám a fotkám, které se pouštějí
  ťuknutím. Systémový model (Nano, Apple) má přednost před oběma.
- **Qwen3 4B potřebuje LiteRT-LM 0.19.** Soubor `qwen3_4b_mixed_int4.litertlm` je od 31. 10. 2026
  nový export (dynamický INT4, GPU kompozity, váhy v oddílu na konci souboru); GPU engine verzí
  0.18.x na něm na Androidu spadne (SIGSEGV, LiteRT-LM#3986), CPU chce aspoň 0.18. Dřívější soubor
  téhož jména (revize `a45912eb`) měl kontext jen 2 048 tokenů, na shrnutí málo. Všechno podle
  karty modelu na Hugging Face; commit v katalogu je ten po výměně souboru.
- **Bump 0.11 → 0.19.1 prošel bez jediné změny v kódu** (API `Engine`, `ConversationConfig`,
  `sendMessageAsync` s `extraContext` zůstalo). Od 0.18 je v AAR jen `liblitertlm_jni.so`
  (22,7 MB na arm64) bez samostatného `libLiteRtClGlAccelerator.so`; knihovna jména akcelerátorů
  zná a OpenCL kód nese, Gallery (0.18.1) přibaluje taky jen ji. **Jestli se na telefonu chytí
  GPU, ověřené není** — bez něj jede CPU (Qwen na Galaxy S26 ~10 tok/s místo ~20).
  AAR nově nese i `x86_64`; model se přesto mimo arm64 nenabízí (`deviceCanRun`).
- **Přemýšlení je vypnuté** (`enable_thinking = false` v `extraContext`, posílalo se už kvůli
  Gemmě). Qwen3 by jinak psal úvahy do zvláštního kanálu a odpověď by přišla mnohem později.
- **Paměť 12 GB jako E4B.** Na 8GB Pixelu 8a podle karty modelu při prvním výpočtu na GPU
  zbylo 243 MB volné paměti a běh se zastavil.
- **Pod zátěží čeká jako Gemma** (`avoidUnderLoad` podle `appManaged`, ne podle poskytovatele)
  a selhání jmenuje Qwen (`ai_failure_qwen`) — výjimka nese poskytovatele z varianty.
- **Úložiště a mazání jsou společné** (`LiteRtModel.variant`), Qwen se tam ukáže jako Gemma.
- **Co dalšího jde** (stav 11/2026): oficiálně testované Gemma 3n, Gemma 3 1B, Phi-4-mini,
  Qwen2.5 0,5B a 1,5B, Qwen3 0,6B; na litert-community Qwen3.5 0,8B a LFM3 od Liquid AI (vlastní
  licence s omezením podle obratu). Malé modely česky slabě, Phi-4-mini pomalý. Llama ani Mistral
  ve formátu LiteRT-LM nejsou — na ně je llama.cpp (viz „EuroLLM přes llama.cpp"). Nový model = řádek
  v `LiteRtModel`, a má-li vlastního poskytovatele, i v `AiProvider` (texty, pořadí automatiky).
- **Ověřeno**: testy a vzhled Nastavení na emulátoru se sondou (x86_64 emulátor model nespustí,
  kontrola zařízení dočasně podstrčená): Qwen v bloku Modely, „Automaticky (Qwen)" u textových
  úloh vedle Gemmy, věta o Qwenu pod úlohami; jako telefon se 6 GB jen Qwen bez přepínače,
  velikost sama na 1,5B, přepnutí na 0,6B („328 MB"). **Na telefonu neověřené nic: stažení
  Qwenu, jeho výstup a čeština, ani Gemma na nové verzi knihovny.**

### Desktop

Gemma a Qwen běží i na desktopu (Windows x64, macOS arm64, Linux), stejným runtime jako na
Androidu (`liteRtMain`, `LiteRtPlatform`); co je jinak (Java 21, nativní knihovna jen pro systém
balíčku, modely mimo Roaming, stahování v procesu), je v `docs/poznatky/desktop.md`, „Model
v zařízení (LiteRT-LM)". Gemini Nano a Apple tam nejsou.

### Runtime jako součást z Google Play

Knihovna LiteRT-LM (nativní `liblitertlm_jni.so`, 23 MB na arm64, k tomu Gson a kotlin-reflect)
není v základu aplikace. Na Androidu je to **součást z Google Play stahovaná až na požádání**
(Play Feature Delivery, modul :client:litertFeature), na desktopu je přibalená. Model v zařízení
si zapne málokdo, a každý další runtime (llama.cpp a spol.) by jinak zvětšoval aplikaci všem.
Naměřeno `bundletool get-size` nad debug AAB pro arm64: základ 63,5 MB, se součástí 74,8 MB ke
stažení.

- **Tři moduly**: :client:ai-engine (rozhraní `LlmEngineFactory` / `LlmEngine` / `LlmConversation`,
  bez závislostí), :client:ai-litert (implementace nad LiteRT-LM, jediné místo s
  `com.google.ai.edge.litertlm`) a :client:litertFeature (dynamic-feature bez kódu, jen balí
  ai-litert). `shared` vidí jen ai-engine: stahování modelu, stav a výběr modelu
  (`LiteRtOnDeviceAi`) zůstaly, kde byly, vyčleněný je jen engine.
- **Implementace se hledá REFLEXÍ podle jména** (`LITERT_ENGINE_FACTORY`, `loadEngineFactory`).
  Základ na třídy součásti nevidí ani při překladu; na desktopu by ai-litert nad `shared` dělal
  kruh. Keep pravidlo je v `androidApp/proguard-rules.pro` — R8 zpracovává základ i součást
  dohromady. Bez něj by model po stažení hlásil chybu jen v release buildu.
- **Runtime se stahuje SPOLU s modelem a NAPŘED** (`LiteRtOnDeviceAi.download`,
  `installRuntimeThenDownload`, testy `LiteRtRuntimeTest`). Desítky MB proti gigabajtům: když
  instalace selže (aplikace není z Play), uživatel to má vědět hned. Stav pro obrazovku je pořád
  `Downloading`, průběh napřed v bajtech runtime, pak modelu. Instalace běží v `scope` runtime,
  ne v obrazovce, a „Zastavit" ji zruší i v Play (`cancelInstall`).
- **`Ready` = soubor modelu A runtime.** Stažený model bez runtime (aktualizace z verze, která
  runtime nesla v sobě, nebo součást odinstalovaná) si runtime **doinstaluje sám**, jakmile se na
  model zeptá funkce nebo Nastavení (`restoreRuntime` ve `status`): uživatel si model už vybral
  a stáhl, ťukat kvůli aktualizaci aplikace by nedávalo smysl. Stav je mezitím `Downloading`.
  Kdo má AI vypnutou, na model se nikdo neptá a nic se nestahuje. **Jen jednou za proces** —
  selhávající instalace (aplikace mimo Play) by se jinak pouštěla s každým dotazem na stav; pak
  zůstává `Paused` a „Pokračovat ve stahování" doinstaluje jen runtime.
- **Instalaci dělá Play, aplikace poslouchá** (`PlayLiteRtRuntime`). Na instalaci, která běží
  z minulého ťuknutí, se napojí přes `sessionStates` — druhou by Play odmítl. Po instalaci za
  běhu je součást vidět až po `SplitCompat.install` (volá ho i `MainApplication`
  v `attachBaseContext`) a nativní knihovnu napřed načte `SplitInstallHelper.loadLibrary`;
  LiteRT-LM si ji bere `System.loadLibrary`, který v čerstvém splitu cestu najít nemusí, a už
  načtenou knihovnu linker najde podle jména. Po restartu procesu je součást běžný split APK.
- **Souhlas Play není chyba.** Play si před instalací součásti může říct o souhlas uživatele
  (`REQUIRES_USER_CONFIRMATION`; dokumentace jmenuje aplikaci, kterou nenainstaloval Play,
  a větší stahování přes mobilní data). Aplikace to brala jako selhání, a „Stáhnout model" tak
  hned ukázalo „Model se nepodařilo stáhnout" — uživatel to hlásil z telefonu na mobilních
  datech (log z telefonu ověřený není, kód chyby tedy taky ne). Teď runtime žádost předá
  `SplitInstallConfirmations` a dialog Play ukáže `SplitInstallConfirmationHost` v kořeni
  `MainActivity` (`startConfirmationDialogForResult` s launcherem, jako nabídka aktualizace).
  Souhlas instalaci rozběhne, odmítnutí stahování zastaví (`Paused`), dialog, který nejde
  ukázat, je chyba. Hostitel sedí v kořeni, ne v Nastavení: instalaci spouští i doinstalace
  u už staženého modelu (`restoreRuntime`) a dialog má naskočit, kde uživatel zrovna je; ukáže
  se, až je aktivita na obrazovce (`collectAsStateWithLifecycle`).
- **Jméno modulu je jméno součásti** (`PlayLiteRtRuntime.MODULE` = `litertFeature`).
  Přejmenování projektu by instalaci tiše rozbilo.
- **bundletool chce stejnou sadu ABI ve všech modulech s nativními knihovnami** („All modules
  with native libraries must support the same set of ABIs"). Základ má kvůli TDLib čtyři,
  LiteRT-LM jen arm64-v8a a x86_64. Součást proto nese pro armeabi-v7a a x86 prázdný
  `libplume_no_<součást>.so` (plugin `plume.feature-abi-placeholders` v build-logic, sdílí ho
  obě součásti); tam se model nenabízí (`canRun` chce
  arm64), takže se součást nikdy nestáhne. AGP při buildu hlásí, že ho nejde ořezat — to je
  v pořádku. Jestli prázdný soubor projde kontrolami Play Console, **ověřené není**.
- **Rozdělení APK podle ABI (`splits.abi`) muselo pryč**: s `dynamicFeatures` AGP součást
  nesestaví („Cannot find PROCESSED_RES output"). Debug APK je teď jedno univerzální,
  `androidApp-debug.apk`.
- **Mimo Play se součást nestáhne.** `assembleDebug` a `adb install` dají aplikaci bez runtime
  a stažení modelu skončí chybou — Play vrátí `-15` (`APP_NOT_OWNED`, aplikaci nenainstaloval
  on), v logcatu `SplitInstallService : onError(-15)`. Ověřeno na emulátoru s EuroLLM: hned
  „Model se nepodařilo stáhnout", se součástí nainstalovanou spolu s aplikací
  (`adb install-multiple`) se model začal stahovat. Pro vývoj buď obojí naráz: `./gradlew
  :client:androidApp:assembleDebug :client:litertFeature:assembleDebug` a `adb install-multiple
  androidApp-debug.apk litertFeature-debug.apk` (součást je pak nainstalovaná rovnou), nebo
  instalaci z Play napodobit: `bundleDebug`, `bundletool build-apks --local-testing` a
  `install-apks` — aplikace dostane jen základ a součást „stáhne" `FakeSplitInstallManager`
  z `/sdcard/Android/data/eu.tintera.plume/files/local_testing`. bundletool chce `--aapt2`
  z build-tools a na classpath i kotlin-stdlib (jar z Maven není spustitelný).
- **Ověřeno**: testy, obsah APK (`dexdump`: v základu žádné `com.google.ai.edge`, Gson ani
  kotlin-reflect, v součásti všechno), R8 release (`LiteRtEngineFactory` pod svým jménem, jen
  v dexu součásti), AAB a bundletool `--local-testing` na emulátoru se sondou (x86_64 v `canRun`):
  aplikace se základem bez součásti nastartuje, „Stáhnout model" napřed doinstaluje součást
  (`SplitCompat: Splits installed`, nativní knihovna přidaná do classloaderu), pak stáhne
  Qwen3 0,6B, a shrnutí v témže procesu načte `liblitertlm_jni.so` ze splitu (`nativeloader …
  ok`), otevře engine a pošle zprávu. Výstup modelu na emulátoru nevyšel kvůli GPU backendu bez
  OpenCL, ne kvůli součásti — to od té doby řeší náhrada CPU při generování (viz „Bez OpenCL
  se GPU engine NAČTE BEZ CHYBY"). **Neověřené: skutečná instalace z Play (interní testování) a jestli
  Play Console vezme prázdné knihovny pro armeabi-v7a a x86.**

### EuroLLM přes llama.cpp

Kvůli češtině je třetí stahovaný model **EuroLLM** (`AiProvider.EuroLlm`) od utter-project,
trénovaný na všech jazycích EU, Apache 2.0. Ve formátu LiteRT-LM není, takže běží přes
**llama.cpp** (soubory GGUF) — druhý runtime vedle LiteRT-LM, na Androidu i desktopu; GPU jen
přes Metal na Macu s Apple Silicon.
Velikosti: **EuroLLM 9B** (verze 2512, Q4_K_M, 5,2 GiB, 16 GB paměti) a **EuroMoE 2,6B**
(směs expertů, aktivních 0,6B, Q4_K_M, 1,5 GiB, 6 GB). Kvantizace od mradermacher — originál 9B
je na Hugging Face za souhlasem, GGUF kopie ne.

- **Runtime je vlastnost souboru** (`LiteRtModel.runtime`, `ModelRuntime`), ne poskytovatele.
  `LiteRtOnDeviceAi` dostává runtime pro každý druh (`runtimes`), stahování, stav, uvolňování
  i porovnání jsou společné; jméno třídy zůstalo z doby, kdy byl jen LiteRT-LM.
- **llama.cpp je git submodul** (`client/ai-llamacpp/llama.cpp`, připnutý na vydání `b12170`,
  mělký). Oba release workflow proto dělají checkout se `submodules: true`. Hotové wrappery
  z Maven jsou od jednotlivců — stejné riziko jako wrapper TDLib, jen bez hranice kolem.
  Bump = `git -C client/ai-llamacpp/llama.cpp fetch --depth 1 origin tag bNNNNN`, checkout
  a commit ukazatele submodulu.
- **Tři moduly: kód, knihovna pro Android, knihovna pro desktop.** :client:ai-llamacpp je čistě
  JVM modul s kódem v Kotlinu (`LlamaCppEngineFactory`), JNI mostem a CMakeLists; knihovnu
  z téhož CMakeLists staví :client:ai-llamacpp-android (NDK r29 kvůli 16KB stránkám, do součásti
  z Play) a :client:ai-llamacpp-desktop (CMake systému, do jaru), viz „EuroLLM na desktopu"
  v `docs/poznatky/desktop.md`. Kód nesmí stavět nic nativního sám: knihovna pro Mac by skončila
  i v součásti pro Android. KMP to není, protože KMP plugin pro Android nativní build z CMake
  neumí. Kód rozezná platformu jen podle toho, jestli knihovna leží v jaru
  (`LlamaNative.loadLibrary`), a podle VM (počet vláken).
- **Jedna knihovna `libplume_llama.so` (6,6 MB na arm64)**: llama.cpp a ggml staticky, k tomu
  vlastní JNI (`plume_llama.cpp`) jen nad C API z `llama.h`. Příklad `examples/llama.android`
  staví backendy zvlášť (`GGML_BACKEND_DL`) a CPU variantu načítá z ADRESÁŘE knihoven — split
  APK z Play je má uvnitř APK, takže by nebylo odkud. Knihovna `common` z příkladů se mění
  s každým vydáním, proto jen `llama.h` (šablona chatu `llama_chat_apply_template`, vzorkování
  řetězem `llama_sampler_*`).
- **Bez OpenMP a KleidiAI.** OpenMP přidá sdílenou `libomp.so`, kterou by linker v součásti
  doinstalované za běhu hledat nemusel; KleidiAI si zdrojáky stahuje z GitHubu při konfiguraci.
  arm64 se staví pro `armv8.2-a+dotprod+fp16` (všechny telefony, na kterých se model nabízí);
  i8mm a SVE by na starších jádrech skončily SIGILL.
- **Text přes JNI jako bajty UTF-8**, ne `jstring`: `NewStringUTF` a `GetStringUTFChars` jedou
  v „modified UTF-8", ve kterém emoji vypadají jinak a CheckJNI na ně aplikaci shodí. Token, který
  nedotvoří znak, se drží, dokud znak nedokončí další (`completeUtf8Prefix`). Proto na desktopu
  NEPLATÍ obcházka LiteRT-LM, která emoji ze vstupu vypouští (`DesktopLiteRtPlatform.modelText`):
  llama.cpp dostane standardní UTF-8 a „Ahoj 😊" projde. **Totéž cesta k modelu**: na Windows ji
  ggml otevírá jako UTF-8 (`ggml_fopen` → `_wfopen`) a jméno uživatele v `%LOCALAPPDATA%`
  bývá s diakritikou.
- **GPU jen Metal na Macu, jinde CPU.** Jestli runtime GPU má, říká platforma
  (`LiteRtPlatform.gpu`, výchozí jen LiteRT-LM; desktop přidá llama.cpp na macOS arm64), ne
  `ModelRuntime`. Na Androidu se o GPU nežádá: Vulkan ani OpenCL backend llama.cpp se nepřekládá.
  Přijde-li přesto `LlmBackend.Gpu` a build GPU nemá (`LlamaNative.hasGpu`), factory vyhodí dřív,
  než začne načítat. Podrobnosti v `docs/poznatky/desktop.md`, „EuroLLM na desktopu".
- **Ořez knihovny dělá modul, který ji balí** (:client:llamacppFeature), a jeho NDK: bez
  `ndkVersion` v součásti AGP ořez přeskočil („Unable to strip") a do AAB šlo 55 MB místo 6,6 MB.
- **Na desktopu EuroLLM je taky**, podrobnosti v `docs/poznatky/desktop.md`,
  „EuroLLM na desktopu (llama.cpp)".
- **V automatice stojí EuroLLM před Qwenem** (`AUTOMATIC_ORDER`): kdo si ho zapne, chce ho na
  text kvůli češtině. Systémové modely mají přednost dál.
- **Ověřeno na emulátoru x86_64** (8 GB RAM, `bundletool --local-testing`, sonda v `canRun`
  pouštějící x86_64 a EuroMoE bez kontroly paměti, necommitovaná): zapnutí EuroLLM nabídne
  EuroMoE 2,6B, „Stáhnout model" doinstaluje součást `llamacppFeature` a stáhne 1,5 GiB, shrnutí
  testovacího chatu načte `libplume_llama.so` ze splitu a odpoví česky za 15 s; „Porovnat
  s dalšími modely" pak v témže procesu přepne na Qwen přes LiteRT-LM. **Se 4 GB RAM emulátor
  model jen stránkoval do swapu a nedopočítal** — pod `minMemoryGb` se model nenabízí právě
  proto. **Na telefonu neověřené nic**: rychlost na arm64, EuroLLM 9B (emulátor na něj nemá
  paměť) ani jestli je čeština opravdu lepší než u Gemmy a Qwenu — to má ukázat porovnání na
  skutečné konverzaci.

### Víc modelů naráz a co čím počítat

Nano zvuk ani obraz nebere, Gemma je umí, jenže drží gigabajty RAM. Kdo měl na všechno Gemmu
kvůli přepisu hlasovek, platil pamětí i za každé shrnutí. Proto se modely ZAPÍNAJÍ (i oba)
a úlohy se jim přiřazují.

- **Úloha nese, co modelu dává** (`AiTask.input`: text, zvuk, obraz) a kdo ji zvládne, je
  statické (`AiProvider.handles`): zvuk a obraz jen Gemma. Co runtime UMÍ, se neptá runtime —
  jestli je připravený, říká `status`.
- **„Automaticky" (výchozí) bere první PŘIPRAVENÝ model v pořadí Nano → Apple → Gemma**, jinak
  první, který zařízení umí (funkce pak nabídne stažení). Systémový model nic nestojí, Gemma se
  načte až kvůli úloze, kterou dostala, a s Nano na textu se uvolní už po 30 s — gigabajty
  v paměti jen chvíli po přepisu hlasovky nebo popisu fotky.
- **Záloha jen podle stavu modelu, ne podle chyby jednoho volání.** `Busy` u Nano nemá kvůli
  jedné odpovědi načíst Gemmu; stahující se Nano naopak text Gemmě předá (je připravená).
- **Ruční přiřazení platí, i když model není připravený**: funkce řekne, že model chybí,
  a řádek v Nastavení „Gemma · model zatím není připravený". Tiše vzít jiný model by udělalo
  z volby dekoraci. **Přiřazení k VYPNUTÉMU modelu se nepočítá** (`AiModels.assignment`) — úloha
  jede automaticky, ne nikam — a nemaže se, takže po opětovném zapnutí platí zase.
- **Úloha bez kandidáta** (zvuk, Gemma vypnutá) dostane první zapnutý model. Ten úlohu neumí
  (`canTranscribe` = `false`), takže se funkce nenabídne; volající nemusí řešit `null`.
- **„Automaticky" se neukládá** (`on_device_ai_tasks` nese jen ruční výjimky jako
  `Summary=Gemma`), aby šlo pravidlo zlepšit bez migrace.
- **Dřívější jediný poskytovatel je výchozí seznam**: bez `on_device_ai_models` platí
  `on_device_ai_provider`, bez něj Nano. Kdo měl Gemmu, nepřijde o ni; kdo měl Nano, nezačne
  stahovat gigabajty. Druhý model se sám nezapne nikdy.
- **Nastavení**: blok Modely má u každého modelu přepínač (jen když jich zařízení umí víc;
  poslední zapnutý vypnout nejde, na to je hlavní vypínač), volby modelu a stav stažení. Blok
  „Co čím počítat" je jen s víc zapnutými modely a má řádek jen pro úlohu, kterou zvládne víc
  z nich — přepis a popis fotek umí jen Gemma, to říká věta pod řádky. „Automaticky (Gemini
  Nano)" ukazuje, kam automatika úlohu poslala (`OnDeviceAiSettingsViewModel.automatic`).
- **Stav se vede pro každý model zvlášť a i pro vypnutý**: stažená Gemma zabírá místo, i když
  ji nic nepoužívá, takže vypnutá stažená (nebo zčásti stažená) má dál „Smazat model". Vypnutí
  Gemmy během stahování stahování zastaví — úloha WorkManageru by jinak stahovala model, o který
  uživatel právě řekl, že nestojí.
- **Rada u hlasovky „přepni na Gemmu" je teď „zapni si k němu Gemmu"** (`OnDeviceFix.SwitchToGemma`
  se nepřejmenoval: posílá se hodinkám jako `switch_to_gemma`).
- **Na zařízení neověřené**: Nano a Gemma vedle sebe jdou jen na telefonu s AICore (Fold),
  emulátor Nano nemá. Na emulátoru se ověří Gemma sama a vzhled Nastavení.

### Porovnání modelů

V sheetu shrnutí i vysvětlení je s víc zapnutými modely „Porovnat s dalšími modely"
(`ChatSummaryViewModel.compare`, testy `ChatSummaryCompareTest`): totéž spočítají ostatní
zapnuté modely a výsledky jsou pod hlavním, každý s názvem modelu a časem v patičce. Kvůli tomu,
že otázka „je E4B česky lepší než Nano" se dojmem z jednotlivých chatů rozhodnout nedá — každé
shrnutí počítal jiný model nad jinou konverzací.

- **V sheetu, ne na zvláštní obrazovce se vzorovým textem.** Porovnává se na SKUTEČNÉ
  konverzaci, a to je jediné porovnání, které o češtině něco řekne. Nepotřebuje to ani novou
  routu.
- **Táž cesta jako hlavní výsledek** (`run` se stavem do `out`): přepis, zkrácení na limit
  i shrnutí po částech. Kdyby porovnání mělo vlastní zkratku, porovnával by se jiný výpočet.
  Pozor, že limit se počítá ZVLÁŠŤ pro každý model — Nano (8 192 tokenů) může shrnovat po
  částech, kde Gemma vezme vše jedním promptem; patička to řekne („Shrnuto po 2 částech").
- **Po sobě, ne souběžně, a až po hotovém hlavním výsledku.** Gemma drží gigabajty a dva
  modely naráz by si braly paměť i procesor; naměřený čas by pak neznamenal nic.
- **Čas je od dotazu na stav po poslední slovo** (`ChatSummaryState.Text.elapsedMs`), tedy
  i s načtením enginu Gemmy. První dotaz po delší pauze je proto pomalejší než druhý —
  porovnávat aspoň dvakrát.
- **Nepřipravený model** (Gemma nestažená) řekne jen „model chybí" a nic nepočítá.
- **Ověřeno testy a na emulátoru se sondou** (falešný model místo obou runtime; emulátor nemá
  AICore a skutečný model na něm kvůli paměti nepočítá): tlačítko až po hotovém výsledku,
  Gemma pod Nano s vlastním časem. Nadpis nad hlavním výsledkem při porovnání přibyl až potom
  a na emulátoru vidět nebyl (nevešel se další APK). Češtinu porovnat zbývá na Foldu.

### Shrnutí hlasovky

„Shrnout přepis" v nabídce nad dlouhou příchozí hlasovkou nebo kolečkem
(`MessageMenuAction.SummarizeVoice`, `ChatSummaryViewModel.summarizeVoice`, prompt
`VoiceSummaryPrompt.kt`): hotový přepis shrne do pár odrážek tentýž sheet jako shrnutí chatu.
První úloha, kde se potkají dva modely — s Nano + Gemmou přepisuje Gemma a shrnuje Nano.

- **Úloha je `AiTask.Summary`, ne přepis.** Přepis už je text; model se zvukem by tu jen
  zbytečně načítal gigabajty. Kdo si v „Co čím počítat" dal shrnutí Gemmě, dostane ji i tady.
- **Jen s HOTOVÝM přepisem** (`doneTranscript`): ze serveru ve zprávě (`media.transcript`),
  jinak z telefonu (`LocalTranscripts` — zpráva ho nenese, nabídka ho dostává od
  `ConversationPlayback`). Kvůli shrnutí se nepřepisuje: přepis je tlačítko v bublině
  a uživatel ho tak vidí dřív, než ho model shrne.
- **Od minuty a jen příchozí** (`VOICE_SUMMARY_MIN_SECONDS`), stejná úvaha jako u „Vysvětlit":
  krátký přepis se přečte rychleji, než model odpoví, a vlastní hlasovku člověk zná.
- **„Shrnout přepis", ne „Shrnout"** — tak se jmenuje shrnutí od serveru a obě by se mohly
  potkat v jedné nabídce.
- **Prompt počítá s chybami rozpoznávání**: nejasné úseky vynechat, ne domýšlet, a mluvčí
  (jméno odesílatele, v soukromém chatu název chatu) je v instrukci jménem.
- **Porovnání modelů i „Zkusit znovu" fungují stejně** jako u shrnutí — jdou se stejným
  přepisem (testy `ChatSummaryCompareTest`).
- **Položka je v nabídce pod „Další"** (skupina textu, `MenuSlot.Text`), vedle „Vysvětlit".
- **Ověřeno na emulátoru se sondou** (falešný model místo obou runtime a dočasně povolený krátký
  vlastní záznam): přepis kolečka falešnou Gemmou, „Shrnout přepis" pod „Další", sheet „Shrnutí
  kolečka" s promptem nad přepisem a porovnání s Gemmou. **Na zařízení neověřené** — ani
  kvalita shrnutí skutečného přepisu, ani dvě úlohy na dvou modelech za sebou.

### Úsporný režim a přehřátí

Pod úsporným režimem nebo od `THERMAL_STATUS_MODERATE` Gemma **sama od sebe** nic nepočítá
(`proactiveVariant`, `DeviceLoad`, `AndroidDeviceLoad`; testy `DeviceLoadTest`). Týká se úloh,
které běží bez ťuknutí: návrhy odpovědí, varování před podvodem, triáž, termíny ve zprávách
a popisy fotek na pozadí.

- **Úlohy na ťuknutí jedou dál** (shrnutí, vysvětlení, přepis hlasovky, přepis konceptu,
  hledání, popis fotky v prohlížeči): o výsledek si uživatel právě řekl.
- **Nano a Apple pod zátěží jedou** — počítá je systém, který si úsporu řídí sám, a aplikace
  kvůli nim nic nenačítá. S oběma modely tak pod zátěží běží dál všechno, co dostalo Nano.
- **I ručně přiřazená Gemma počká.** Úsporný režim je taky volba uživatele; úloha, kterou si
  nevyžádal ťuknutím, se projeví s další změnou (nová zpráva, otevřený chat), až zátěž pomine.
- **Proč ne ve `chooseProvider`**: ten vrací poskytovatele vždycky, a „pod zátěží jen Nano"
  by u uživatele, který Nano nezapnul, Nano potichu pustilo. `null` z `proactiveVariant` znamená
  „teď ne" a volající to obslouží stejnou větví jako nepřipravený model.
- **Čte se při volání** (`isPowerSaveMode`, `currentThermalStatus`), bez naslouchání — úloha se
  ptá těsně před tím, než by model pustila.
- **Nastavení u Gemmy to říká větou** (`settings_gemma_under_load`): uživatel s jedinou Gemmou by
  jinak nepoznal, proč návrhy a varování zmizely.
- **Emulátor**: úsporný režim zapne `adb shell cmd power set-mode 1` (zpět `0`;
  `settings put global low_power 1` na Androidu 17 nezabral), teplotu podstrčí
  `adb shell cmd thermalservice override-status 2` (zpět `reset`). Ověřeno sondou, která
  logovala `strained`: úspora i `MODERATE` → `true`, vypnuto i `LIGHT` (1) → `false`.
  Samotné vynechání Gemmy v triáži na emulátoru vidět nebylo (žádný kandidát); pravidlo mají
  testy (`DeviceLoadTest`, `ReplySuggestionsViewModelTest`).

### Návrh popisku fotky

Gemma navrhne popisek k fotce, která čeká nad polem na odeslání; podrobně v
`konverzace-psani.md`, „Návrh popisku fotky". Pro tuhle oblast podstatné: `describeImage` bere
volitelný `prompt` (výchozí `imageDescriptionPrompt`), takže popis pro nevidomé i návrh popisku
jedou stejným enginem s obrazem — žádné druhé načtení.

### Návrh složek

Model rozřadí nejnovější chaty do pevných kategorií a navrhne složky; podrobně v
`seznam-chatu.md`, „Návrh složek modelem". Pro tuhle oblast podstatné: je to klasifikace
s výstupem „číslo: KATEGORIE" za chat a vlastní úloha `AiTask.Folders`.

### Apple Intelligence na iOS (Foundation Models)

Na iOS 26+ počítá model v zařízení **Apple Intelligence** (`AiProvider.Apple`,
`AppleOnDeviceAi` v `iosMain`). Je to systémový model jako Gemini Nano: stahuje a aktualizuje ho
iOS, aplikace ho jen používá.

- **Kotlin/Native na `FoundationModels` nedosáhne** (čistě Swiftové API bez Objective-C). Kotlin
  proto vystavuje rozhraní `OnDeviceTextModel` (do frameworku jde jako Objective-C protokol),
  Swift ho implementuje (`iosApp/iosApp/AppleTextModel.swift`, `LanguageModelSession.streamResponse`)
  a v `iOSApp.init` ho předá `IosOnDeviceModel.model` **před** `initKoin` — Koin podle něj
  vybere `AppleOnDeviceAi`, jinak `NoopOnDeviceAi`. Stejný vzor má i hlášení pádů.
- **Deployment target je iOS 18.2**, takže `@available(iOS 26, *)` a `#if canImport` a framework
  **slabě linkovaný** — Swift to udělá sám (`otool -l` nad `Plume.debug.dylib`: `LC_LOAD_WEAK_DYLIB`
  pro FoundationModels). Starší iOS aplikaci spustí, jen bez AI.
- **Snímek, ne přírůstek**: stream Foundation Models dává celý dosavadní text (`snapshot.content`),
  přesně jak `OnDeviceAi.generate` slibuje; Kotlin ho bere `callbackFlow` a `conflate`.
  Zrušení sběru zruší Swiftový `Task`.
- **Chyby podle JMÉNA případu** (`String(describing:)`): iOS 27 je přejmenoval
  (`GenerationError` → `LanguageModelError` a spol.), takže `switch` nad typem by na jedné
  verzi nepoznal nic. Kontext → `TooLarge`, jazyk → `UnsupportedLanguage`, ochranné filtry →
  `Refused` (oba nové důvody nejsou „reportable"), vytížení → `Busy`, chybějící soubory modelu
  → `Unavailable`.
- **Kontext 4 096 tokenů** (dotaz na něj je až od iOS 27), počítání tokenů až od 26.4 a jen
  asynchronně — délka přepisu se odhaduje z limitu jako u Gemmy.
- **Vypnutá Apple Intelligence nebo model, který systém teprve stahuje, = `Unsupported`**:
  sekce AI v Nastavení se pak nenabídne. Stav „zapni si Apple Intelligence v Nastavení" zatím
  nastavení neumí.
- **Strukturovaný výstup, přepis řeči a popis obrázků na iOS zatím ne** — Foundation Models
  je umí (`@Generable`, obrázky), ale každý chce vlastní Swift most.
- **Simulátor používá model HOSTITELE**: na Macu s vypnutou Apple Intelligence hlásí
  simulátor model jako dostupný, ale generování spadne na `ModelManagerError` 1026 (chybí
  soubory modelu) — proto ji `AppleTextModel` řadí jako `Unavailable`. Ověřit generování jde
  jen na Macu se zapnutou Apple Intelligence, nebo na iPhonu s iOS 26+.
- **Ověřeno v simulátoru (iOS 26.2)**: Nastavení → On-device AI ukáže „Model ready: Apple
  Intelligence, Input limit: 4 096 tokens", bez volby poskytovatele (jediný dostupný).
  Generování ověřené samostatným Swift programem spuštěným v simulátoru (`simctl spawn`):
  `availability: available`, ale `ModelManagerError` 1026, protože Apple Intelligence je na
  Macu vypnutá. **Skutečné generování (a čeština) neověřené** — Apple Intelligence češtinu
  v seznamu jazyků nemá a česká konverzace může skončit `UnsupportedLanguage`.
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

### Popis fotky (obrazový vstup Gemmy)

Gemma 4 umí i obraz (`OnDeviceAi.describeImage`, prompt `ImagePrompt.kt`); v aplikaci ho
používá prohlížeč (`prohlizec-medii.md`, „Popis fotky"). LiteRT-LM 0.11.0 bere
`Content.ImageBytes` / `Content.ImageFile` vedle textu v jedné zprávě, stejně jako zvuk.

- **Engine s obrazem je zase jiný engine** (`EngineConfig.visionBackend`, `maxNumImages = 1`).
  Obraz počítá týž backend jako text (Gallery: GPU, když jde) a s CPU zálohou spadne spolu
  s ním. **Přenačtený engine si nechá, co uměl** (`withEngine`: zvuk i obraz se sčítají), jinak
  by se přepis hlasovky a popis fotky střídaly přenačítáním po deseti vteřinách.
- **Obrázek dekóduje systém a zmenší na 1024 px** (`ImageInput`, PNG jako v Gallery). Kodér
  si ho zmenší sám (v logu `vision_280` — signatura kodéru), plný originál by jen držel desítky MB.
- **E2B píše česky s chybami** („malownicý kraj", „limevé barvy") — obsah sedí, sloh ne.
  U snímku obrazovky s pokynem „řekni, o čem text je, neopisuj ho celý" vyjmenovala všechny
  popisky v uvozovkách; „jen druh textu (účtenka, cedule, obrazovka aplikace), necituj" výčet
  zkrátila na pár položek. Úplně se ho nezbaví.
- **Čas na emulátoru (CPU)**: první popis i s přenačtením enginu ~13 s.

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

- **„Opravit překlepy" ukazuje změny jako oprava od serveru** (`draftDiff`, testy
  `DraftDiffingTest`): model rozdíl nevrací, spočítá ho aplikace a vyrobí z něj tentýž
  `DraftDiff`, takže se vykreslí stejně (vložené na `primaryContainer`, smazané přeškrtnuté).
  - **Po slovech** (nejdelší společná podposloupnost slov, mezer a interpunkce): oprava
    diakritiky je změna slova, po znacích by se zvýraznila písmena uprostřed. Pár UTF-16
    (emoji) se nedělí.
  - **Náhrada = smazané a hned vložené** („~~mas~~máš"), ne jen nový text jako u serveru —
    smyslem je ukázat, co model změnil. Ověřeno na Foldu s Gemmou: „Ahoj jak se mas prijdu
    zitra v osm" → „Ahoj**,** jak se ~~mas~~**máš** prijdu ~~zitra~~**zítra** v osm**?**" —
    model sám připsal otazník a „prijdu" neopravil, a obojí je teď vidět.
  - Jen u opravy; u „Kratší" a „Zdvořileji" se mění celé věty a zvýraznění by bylo šum.
- **„Přidat emoji" v telefonu má pojistku** (`RewriteStyle.Emoji`, `onlyEmojiAdded`, testy
  `EmojiGuardTest`): po odebrání emoji (obrázkové znaky, ZWJ, voliče variant, odstíny pleti)
  a všech mezer musí zbýt přesně původní text. Jinak se výsledek zahodí s větou, že model změnil
  i text — v záplavě emoji by „mimochodem" opravené slovo nikdo nepoznal. Mezery se nepočítají,
  protože model kolem emoji mezeru rád přidá nebo ubere („kufr 🧳."). Vložená emoji ukáže
  zvýraznění změn (`draftDiff`), takže je vidět i to, že se nic jiného nezměnilo.
  - **Ověřeno na Foldu s Gemmou** (koncept v Uložených): „Tomorrow we go hiking in the
    mountains" → „… ⛰️🌲😊", „zitra jedeme na hory uz se tesim" → „… ⛰️😊" a i „happy birthay my
    friend i love you" → „… ❤️🎂🥳" — Gemma text pokaždé nechala, i s překlepem. Pojistka tedy
    v praxi zatím nezasáhla; zarazí to, co testy (změněná slova, doplněná interpunkce).
    **Gemini Nano neověřené.**

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

### Úpravy textu od Telegramu (server)

Týž sheet „Zkontrolovat a přepsat…" má vedle modelu v zařízení řadu **Telegram AI**: „Opravit
chyby" (`fixTextWithAi`) a styly psaní od serveru (`composeTextWithAi`; `AiTextRepository`,
`TdlAiTextRepository`). Text tu na rozdíl od zbytku téhle oblasti ODCHÁZÍ na server.

- **Dvě řady s nadpisy „V telefonu" / „Telegram AI"** — liší se tím podstatným, kam text jde.
  Nadpisy jen když jsou řady dvě. Poznámka pod výsledkem se řídí volbou: „Text upravuje
  Telegram na svých serverech…", nebo dosavadní „Přepisuje model v telefonu…".
- **Sheet se teď nabízí i bez AI v zařízení** (jen serverová řada), a **v tajném chatu jen
  s modelem v zařízení** — dokumentace obou metod tajný chat zakazuje (`serverAiAllowed`
  z `ConversationBottomBar` do `MessageInput`).
- **Styly chodí jen updatem** (`updateTextCompositionStyles`), dotaz na ně TDLib nemá —
  repository je proto eager (`createdAtStart`). Názvy jsou od serveru a **anglicky** (Formal,
  Short, Tribal, Corp, Biblical, Viking, Zen). Jazykový balíček už Plume nastavuje podle jazyka
  aplikace (`syncLanguagePack`, viz „Jazykový balíček Telegramu" v `nastaveni.md`), ale styly
  přišly anglicky i s českým balíčkem — buď je server česky nemá, nebo TDLib drží starý seznam
  z keše a nový update nepošle. Rozhodnout to šlo by až s čerstvou instalací.
- **Oprava vrací i rozdíl** (`DiffText`: vložené, smazané, nahrazené; rozsahy v UTF-16).
  Výsledek ukazuje text rozdílu se změnami na podkladu `primaryContainer` a smazanými kusy
  přeškrtnutými chybovou barvou; „Použít" vloží čistý opravený text.
- **Bez Premium je počet dotazů omezený**; po vyčerpání `AICOMPOSE_FLOOD_PREMIUM` → věta
  o Premium (`errorText`, test `TelegramErrorsTest`).
- **Překlad a emoji jsou tentýž `composeTextWithAi`** jako styly, jen s `translateToLanguageCode`
  a `addEmojis` (`RewriteChoice.ServerTranslate` / `ServerEmoji`). Jazyk se vybírá stejným sheetem
  jako u překladu zprávy (`TranslationLanguageSheet`, sheet nad sheetem — zpět zavře jen výběr);
  čip pak nese vybraný jazyk („Přeložit: Angličtina") a další ťuknutí výběr otevře znovu.
  Volby by šlo kombinovat (styl + překlad + emoji jedním dotazem), sheet je ale nabízí zvlášť
  jako ostatní čipy: každý čip = jeden přepis původního textu. Ověřeno na emulátoru `Plume_Barvy`
  (…527): „Uvidime se zitra na nadrazi v osm" → s emoji 🚆, do angličtiny „Let's meet at the
  station tomorrow at eight", „Použít" vložilo překlad do pole.
- **Text bez diakritiky server čte jako slovenštinu**: „ahoj prijdu pozdejc, protoze mam hodne
  prace" opravil na „prídem neskôr, pretože mám veľa práce" a styl Viking psal slovensky.
  Jazyk se serveru předat nedá (parametr je jen pro překlad), takže to platí, dokud v textu
  nejsou háčky; výsledek se proto vždy jen nabízí.
- **Ověřeno na emulátoru `Plume_Barvy`** (testovací účet …527, bez AI v zařízení): sheet jen se
  serverovou řadou, styly dorazily, oprava se zvýrazněním, styl, „Použít" nahradil koncept.
  Na Foldu (s Gemmou) obě řady a kontrola tónu; serverové volby tam nezkoušené (skutečný účet).
  **Neověřeno:** limit bez Premium, tajný chat, iOS.

### Překlad konceptu v telefonu

Řada „V telefonu" v sheetu „Zkontrolovat a přepsat…" má **Přeložit…** — koncept přeloží ML Kit
Translation v telefonu, stejný překladač jako „Překládat v telefonu" u zprávy
(`RewriteChoice.LocalTranslate`, `DraftRewriteViewModel.translateLocally`). Text neodchází
nikam, takže se nabízí i v tajném chatu, kde serverová řada není.

- **Jazykový model na to není**: překlad je slabé místo Nano i Gemmy a ML Kit je stavěný přímo
  na něj. Proto se nabízí i bez zapnuté AI v zařízení — řada „V telefonu" se kreslí, když je
  přepis modelem NEBO překladač (`translateAvailable`; dnes jen Android).
- **Výběr jazyka je tentýž sheet jako u serveru** (`TranslationLanguageSheet`), jen zúžený na
  jazyky, které ML Kit umí (`available = translatesTo`). Který překlad se vybírá, drží sheet
  přepisu (`LanguagePick`).
- **Balíčky se stahují jen na „Stáhnout"** (`RewriteState.NeedsDownload` → `Downloading`), jako
  u zprávy.
- **Nerozpoznaný jazyk konceptu = jazyk aplikace.** Koncept bývá krátký a rozpoznání pod
  prahem jistoty vrátí `null`; u cizí zprávy by se překlad odmítl, ale koncept píše uživatel,
  nejspíš v jazyce aplikace. Text bez diakritiky ML Kit ale přeloží špatně: „Ahoj jak se mas"
  → „Hello both with meat" (z „mas" bylo maso) — výsledek se proto jen nabízí jako vždy.
- **Ověřeno na emulátoru `Plume_Barvy`** (…527, bez AI v zařízení): řada „V telefonu" jen
  s „Přeložit…", výběr bez jazyků, které ML Kit neumí, stažení balíčků cs + en (pod minutu),
  „See you tomorrow at the train station at eight" → „Uvidíme se zítra na vlakovém nádraží
  v osm". Koncept pak smazaný. **Neověřené:** tajný chat.

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

### Triáž chatů

Odznak „čeká na odpověď" v seznamu chatů (`ChatTriage`, prompt `TriagePrompt.kt`, odznak
v `UnreadBadges`, `seznam-chatu.md`, „Čeká na odpověď"). Vlastní vypínač v Nastavení → AI
v zařízení, výchozí vypnuto — model běží sám, bez ťuknutí, jako u varování před podvodem.

- **Nepřečtené soukromé chaty, kde poslední zprávu poslal protějšek** (`isTriageCandidate`; ne
  ztlumené), **a skupiny s nepřečtenou zmínkou nebo odpovědí na vlastní zprávu**
  (`unreadMentionCount`, i ztlumené — zmínky Telegram hlásí i z nich). Ne Uložené, kanály,
  ověřené účty ani sponzorované chaty. Boti ano — model jejich oznámení pozná jako `INFO`.
- **Ve skupině model vidí jen zmínky** (`ConversationRepository.unreadMentions`,
  `SearchMessagesFilterUnreadMention`, nejvýš šest) se jménem odesílatele („Jana: …"), ne
  ostatní provoz. **Posudek patří nejnovější zmínce**, ne poslední zprávě: běžná zpráva ve
  skupině model znovu nepustí (uložený posudek té zmínky se jen přenese na novou poslední
  zprávu), nová zmínka ano.
- **Posudky se ukládají** (`StoredTriageVerdict` v `AppSettings`, po účtech podle id Uložených,
  nejvýš 300, jeden na chat): po restartu se použijí bez modelu. Chyba modelu se neukládá —
  zkusí se znovu.
- **Model dostane nejvýš šest posledních nepřečtených příchozích zpráv** (`history` od nejnovější,
  do první vlastní), od nejstarší; fotka bez popisku jako „Fotka". Rozhoduje konec rozhovoru,
  delší vstup se ořízne od začátku.
- **Výsledek patří poslední zprávě**: chat → id zprávy, nad kterou to model řekl. Klíč (chat,
  zpráva) se posuzuje jednou za běh procesu, frontou po jednom v `appScope`.
- **Seznam předbíhá historii**: počet nepřečtených (`UpdateChatReadInbox`) dorazí dřív než nová
  poslední zpráva, takže první kandidát nese STAROU poslední zprávu a historie vrátí novou.
  Takový dotaz se zahodí a vyřadí ze „viděných" — správný přijde s další změnou seznamu.
- **Po startu triáž čeká i na volbu modelu**: z DataStore se načte až po prvním seznamu a model
  do té doby „není připravený" (výchozí varianta je Gemini Nano). ViewModel proto spouští
  `check` i při změně volby, ne jen seznamu — jinak se po restartu neposoudilo nic.
- **„Jak se máš?" Gemma 4 E2B brala jako pozdrav** (`INFO`), dokud pokyn zněl „otázka… nebo
  samotný pozdrav = INFO", i s příkladem „how are you" u `REPLY`. Pomohlo obrátit důraz:
  `REPLY` = „poslední zprávy obsahují otázku na uživatele, i small talk", `INFO` = „žádná otázka
  ani prosba", a věta „otázka na uživatele odpověď potřebuje vždy".
- **Skupina ověřena na emulátoru Fold** (testovací skupina „Plume test triaze" s botem, zmínka
  přes `text_mention`): „Vladimíre, můžeš prosím zajistit jízdenky?" → odznak „@" i šipka
  a karta; další zpráva bez zmínky model nepustila a odznak zůstal; po `force-stop` a startu se
  karta i odznaky vrátily bez jediného dotazu na model; po přečtení „Díky, Vladimíre, jízdenky
  už mám" → `INFO`, z karty zmizela.
- **Ověřeno na emulátoru Fold** (Gemma 4 E2B, CPU, ~10 s na chat; ve frontě za rozpoznáváním
  termínů otevřené konverzace): žádost o prezentaci a „Ahoj, jak se máš?" od bota → odznak,
  poděkování a oznámení o vodě → bez odznaku, skutečné chaty („Nehoda pri teste hahaha") bez
  odznaku. Přečtení chatu odznak schová. **Gemini Nano neověřené.**

### Akce ze zpráv

Ikonka se sparkle (`auto_awesome`) na volné straně zprávy, ve které je termín, adresa, telefon
nebo e-mail, číslo účtu, letu nebo zásilky, Wi-Fi s heslem nebo kód; ťuknutí otevře sheet
s akcemi (`MessageContextSheet`): kalendář a „Připomenout…", mapy, uložení do kontaktů, QR
platba s kopírováním účtu, částky a VS, stav letu, připojení k Wi-Fi, kopírování kódu,
převod cizí měny a jednotek, kopírování kódu rezervace, sledování zásilky. Rozpoznávají PRAVIDLA
(`findMessageContext`, testy `MessageContextTest`), model jen posuzuje termín (`MessageContexts`,
prompt `EventPrompt.kt`). Vypínač v Nastavení → Obecné → Chaty, **výchozí zapnuto** — na rozdíl
od podvodu se kvůli tomu nic nestahuje a model se přidá, jen když si AI v zařízení uživatel zapnul.

- **Rozbor pravidly je DRAHÝ a na hlavní vlákno nepatří.** Desítky regulárních výrazů,
  `foldDiacritics` a hledání letišť, měn a jednotek dělají ~0,1 ms na zprávu na Macu (JVM
  po zahřátí), na telefonu násobně víc. Dřív běžel dvakrát na hlavním vlákně: ViewModel
  rozebíral posledních 20 zpráv při každé změně seznamu (jen kvůli předání termínu modelu,
  a to i bez zapnuté AI, kdy `MessageContexts.check` výsledek zahodí), a každá položka
  seznamu si ho počítala v kompozici. Odeslání mění seznam několikrát (rozepsaná zpráva,
  potvrzení s novým id) a let odeslané zprávy z pole drhnul. Změřeno dočasným logem na
  emulátoru (debug build): pět odeslání = 13 průchodů po 20 zprávách, každý 7–60 ms na hlavním
  vlákně, tedy několik ztracených snímků na každý let. Teď ViewModel nedělá nic bez
  zapnuté AI (`MessageContexts.enabled`), každou podobu zprávy (id, text) rozebere jednou
  a na `Dispatchers.Default`; položka si výsledek bere z mezipaměti obrazovky, jinak ho
  spočítá na pozadí (`produceState`). Ikonka stojí mimo rozvržení (`offset`), takže když
  naskočí o snímek později, nic se neposune.
- **Pravidla napřed, protože jdou ověřit kontrolním součtem.** Český účet mod 11 a kód banky
  ze seznamu ČNB (bez seznamu by „č. j. 1234/2025" prošlo jednou z jedenácti), IBAN mod 97,
  zásilka S10 kontrolní číslicí. Model by tu jen hádal, co už víme jistě.
- **PPL a DPD jen se jménem dopravce do 80 znaků od čísla** (`CARRIER_NUMBER`, `CARRIER_WINDOW`).
  Jejich čísla jsou holé číslice bez kontrolního součtu — PPL 11 („11-digit shipment number",
  ppl.cz → Customer service), DPD 14 („14místné číslo zásilky", dpd.com/cz → Nejčastější
  dotazy) — a samotné se od čísla objednávky nebo telefonu bez předvolby nerozeznají. Délka
  musí sedět k dopravci (11 s „DPD" nic není), jméno jen VELKÝMI písmeny: „ppl" je
  v angličtině „people" a „ppl can text me at 12025550123" je americký telefon o 11 číslicích.
  Číslo nesmí být kusem delšího čísla, účtu („19-…/0800") ani desetinného čísla. **Adresy
  sledování jsou z webů dopravců**, ne z agregátorů: PPL `ppl.cz/vyhledat-zasilku?shipmentId=`
  (anglicky `/en/track-a-shipment`; cesty z `tntSearchUrlCs/En` ve zdroji ppl.cz), DPD
  `dpdgroup.com/cz/mydpd/my-parcels/search?lang=cz&parcelNumber=` z vyhledávacího pole na
  dpd.com/cz — čeština je tam `lang=cz`, ne `cs`. curl na myDPD dostane 403 (ochrana proti
  robotům), v prohlížeči se otevře. Ověřeno na emulátoru (zprávy od bota Notifikátor): „PPL:
  Vaše zásilka 40012345678…" i „Číslo zásilky DPD: 01234567890123…" mají ikonku a sheet
  s dopravcem, „Objednávka 40012345678…" ikonku nemá; „Sledovat zásilku" u PPL otevře ppl.cz.
- **Adresa jen s jasnou stavbou**: ulice, číslo, čárka, město — a k tomu PSČ, nebo ulice, která
  jako ulice vypadá (koncovka „-ská", „-ova", „-ního"…, klíčové slovo „nám.", předložka „Na").
  Ulice je JEDNO slovo: víc slov by spolklo i slovo před ní („Sraz Vinohradská 12").
  **Dvouslovná ulice jen s PSČ** (`CZECH_ADDRESS_TWO_WORDS`): „Karla Engliše 3, 150 00 Praha 5",
  „Kpt. Jaroše 7, 602 00 Brno" — první slovo musí vypadat jako jméno ve 2. pádě (končí na
  -a, -y, -e, -i) nebo zkratka s tečkou. Jednoslovná ulice se znakem ulice (koncovka,
  „nám.", předložka) má přednost („Adresa Vinohradská 12, 120 00 Praha" zůstane bez
  „Adresa"); jednoslovnou, kterou drží jen PSČ („[Karla] Engliše 3, …"), dvouslovná přebije.
  Bez PSČ („Karla Engliše 3, Praha") se víceslovná ulice nenajde dál a tři slova („Dr. Zikmunda
  Wintra") taky ne. Ověřeno na emulátoru: zpráva od bota → adresa celá, s kopírováním
  a mapami. „Kapitola 12, Praha" adresou není. Anglicky číslo, název a druh
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
- **Dvojznačné značky rozhoduje okolí zprávy, a když nerozhodne, nabídnou se obě možnosti**
  (`Money.assumed`, karta pak měnu řekne: „¥800 (JPY)", „¥800 (CNY)"). `¥` je jen, nebo jüan
  — podle zmínky o Japonsku / Tokiu / jenu, nebo o Číně / Pekingu / jüanu / RMB; `円`, `元`,
  `JP¥`, `CN¥` a `RMB` jsou jednoznačné. Anglické „pounds" jsou libry, nebo váha — podle slov
  o penězích („costs", „paid", „ticket", „rent", „stojí"…) a o váze („weighs", „flour",
  „lost"…); bez nich převod váhy i měny. „Quid" a „pounds sterling" jsou peníze vždy,
  zkratka „lb" váha vždy. Kořeny měst v kontextu se skloňují („v Tokiu") — proto „toki".
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
- **Tytéž akce jsou v nabídce nad zprávou, každá jako vlastní položka** (`MessageMenuAction.ContextItem`,
  `ContextVerb`, `contextMenuItems`, testy v `MessageActionTest`). Kandidát `ContextActions` se
  při otevření nabídky rozvine na položky („Stav letu", „Odlet do kalendáře", „Kopírovat
  rezervaci"). **Přímé akce se provedou hned** (kalendář, mapy, kontakt, připojení k Wi-Fi,
  kopírování, sledování) — kvůli nim položka v nabídce je; sheet by byl ťuknutí navíc. **Co se
  musí ukázat, otevře sheet** a má tři tečky: převod měny a jednotek, QR platba a Wi-Fi tam, kde
  se připojit nedá (heslo a QR kód).
  - **Nejvýš čtyři položky**, jinak tři a „Akce ze zprávy…" (sheet se vším). Zpráva s pěti
    adresami by jinak nabídku protáhla přes obrazovku a vlastní položky zprávy (Přeposlat,
    Připnout) odsunula dolů.
  - **Více akcí stejného druhu nese hodnotu MÍSTO popisku** („RR123456785CZ" s ikonou zásilky),
    druh řekne ikona. Hodnota ZA popiskem („Sledovat zásilku · RR…") se zalomila a výška
    nabídky se počítá na jeden řádek na položku — konec nabídky se usekl. Ze stejného důvodu
    je „Kopírovat rezervaci", ne „Kopírovat kód rezervace".
  - **I s vypnutou ikonkou**: vypínač brání tomu, co vyskakuje samo, nabídka se otevírá
    vědomě — věta pod vypínačem to říká. Nálezy počítá ViewModel až při otevření nabídky
    (`contextFindingsOf`), ne `menuActions` u každé zprávy; kandidát je proto u každé zprávy
    s textem a bez nálezu se rozvine na nic. Právo jako u kopírování (`canCopy`).
  - Ověřeno na emulátoru: zpráva s letem a rezervací ukáže tři položky, „Kopírovat rezervaci"
    zkopíruje kód a zavře nabídku.
- **Zkopírování v sheetu potvrzuje řádek, ne snackbar** (`copyInSheet`, `LocalJustCopied`
  v `MessageContextSheet`): ikona kopírování se na dvě vteřiny prolne do fajfky v barvě
  `primary` a čtečce ji oznámí živá oblast. Snackbar obrazovky leží POD modálním sheetem
  a vidět nebyl — na iOS a Androidu do 12 tak kopírování nepotvrdilo nic. Kopírování z NABÍDKY
  nad zprávou („Kopírovat rezervaci") snackbar dál má: nabídka se zavře a hláška je vidět.
  Na Androidu 13+ se fajfka potká se systémovým náhledem schránky; je to stav ovládacího
  prvku, ne druhé oznámení (viz „Hláška Zkopírováno" v `CLAUDE.md`). Ověřeno na emulátoru
  (Android 17): fajfka naskočí, po dvou vteřinách se vrátí ikona kopírování.
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
  a „1,5 milionu liber" na „≈ 1 982 869,63 USD"; „Ta mikina stála ¥800" ukáže dvě karty,
  „¥800 (JPY) ≈ 5,06 USD" a „¥800 (CNY) ≈ 119,53 USD";
  „uběhl 10 km, venku bylo 20 °C" se systémem `en_US` ukáže „≈ 6,2 mi" a „≈ 68 °F";
  „kód rezervace: XKQ7ZT" nabídne kód rezervace ke zkopírování; „Odlet OK 123 zítra v 9:40
  z Ruzyně, kód rezervace XKQ7ZT" nabídne let se „Stav letu", „Odlet do kalendáře — zítra
  09:40" (bez obecné karty události) a kód rezervace; „Let OK 123 PRG → LHR zítra, odlet 9:40,
  přílet 10:55" ukáže pod číslem letu „PRG → LHR · 2 h 15 min"; „Letím zítra OK 123 z Prahy do
  Paříže, odlet 9:40, přílet 11:25" ukáže „PRG → CDG · 1 h 45 min".
  **Neověřené**: načtení QR bankovní aplikací, editor
  události, Gemini Nano, iOS.
- **Mapy jen u místa, se kterým si poradí** (`ContextFinding.Event.mapsQuery`, testy
  `MessageContextTest`). Model bere místo ze zprávy, jak stálo, a bývá vágní („letišti",
  „u kina", samotné „Kotvy") — mapy pak ukázaly něco náhodného v okolí. „Otevřít v mapách"
  se nabízí jen u místa s číslem („Vinohradské 12", PSČ) nebo s vlastním jménem dál než na
  začátku („na Černém Mostě u kina", „U Fleků"); velké první slovo nestačí, model ho píše
  i u „Na letišti". Místo samo se v kartě ukazuje dál. Ověřeno s Gemmou na emulátoru Fold:
  „Sraz zítra v 18:00 na letišti" → místo „letišti" bez map, „… na Vinohradské 12" → mapy.
- **QR platbu jde sdílet jako obrázek** („Sdílet QR kód" pod kódem, `sharePaymentQr`, PNG
  `qrPng`, testy `QrPngTest`). QR na obrazovce telefonu týž telefon nenaskenuje; bankovní
  aplikace ho přečte ze sdílení nebo z galerie. PNG se zapisuje vlastním kódem bez komprese
  (černobílý obrázek 1 bit na pixel, uložené bloky deflate): ve společném kódu kodér obrázků
  ani deflate na všech platformách není. Že je obrázek platný, ověřuje test čtením přes
  `ImageIO` a celou cestu (text platby → QR → PNG → čtečka zxing) taky. Ověřeno na emulátoru
  Fold: zpráva s účtem 2000145399/0800 od testovacího bota → „Sdílet QR kód" → systémový dialog
  „Sharing image" s náhledem QR. **Pozor na číslo účtu ve zkouškách**: neplatné (nesplní
  kontrolu modulo 11, třeba 123456789/0800) se za účet nepovažuje a nabídne se jako telefon.

### Strukturovaný výstup

Gemini Nano umí vrátit odpověď rovnou jako typ (`OnDeviceAi.generateStructured`, ML Kit
`GenerateTypedContentRequest`): tvar drží model, ne náš parser. Dnes ho používají **návrhy
odpovědí** (`ReplySuggestionsOutput`); kde ho model nemá, jde všechno dál textem.

- **Jen Gemini Nano.** Gemma přes LiteRT-LM 0.11.0 typovaný výstup nemá
  (`supportsStructuredOutput` = `false`) a AICore ho hlásí zvlášť
  (`getAvailableFeatures()` s `Feature.STRUCTURED_OUTPUT`, do beta4 `isStructuredOutputFeatureAvailable`) — ani každé Nano ho mít nemusí. **Textová cesta proto
  zůstává plnohodnotná**, ne jako náhradní řešení na odchodu.
- **Popis tvaru je `GenerableProvider` a ML Kit ho hledá přes `ServiceLoader`** — ověřeno
  v bajtkódu `genai-prompt` 1.0.0-beta4 (`ServiceLoader.load(GenerableProvider…)` → mapa
  `targetClass` → provider). Anotace a rozhraní jsou ve zvláštním artefaktu `genai-schema`
  (1.0.0-alpha1); KSP procesor `genai-schema-compiler` by je generoval z `@Generable`/`@Guide`.
- **Providery jsou psané RUČNĚ, KSP v projektu není** (`StructuredOutputProviders.kt`
  v `androidMain`). Typy odpovědí leží v `commonMain`, protože je čte ViewModel, a anotace ML Kitu
  tam být nemůžou. Provider je pár řádků podle vzoru, který procesor generuje (šablona je čitelná
  přímo v jeho jaru); seznam na `List::class` s `isList = true` a `listItemType`.
- **Nový typ = tři místa**: provider, řádek v `src/androidMain/resources/META-INF/services/
  com.google.mlkit.genai.schema.guided.GenerableProvider` a keep pravidlo na pole. Bez řádku ve
  services ML Kit typ nezná a generování selže; soubor se do APK dostane (ověřeno `unzip -l`).
- **Objekt staví Gson (přibalený v ML Kitu) reflexí podle JMEN polí**, bez konstruktoru. Pole,
  které model vynechá, je proto `null` i v nenullable typu — `ReplySuggestionsOutput.replies` je
  vědomě nullable. **Pole drží keep pravidlo s konstruktorem**
  (`proguard-rules.pro`): s pouhým `-keepclassmembers … { <fields>; }` R8 v plném režimu pole
  ZAHODIL (ověřeno v dexu release buildu a v `usage.txt`) — instanci staví jen Gson, takže třídu
  bral jako nikdy nevytvořenou a getter z ní udělal abstraktní. **Provider pravidlo nepotřebuje:**
  R8 sloučil rozhraní `GenerableProvider` s jedinou implementací a soubor ve `services`
  přejmenoval souhlasně (`META-INF/services/pkb` → `pkb`), takže ho `ServiceLoader` najde.
- **Gson v aplikaci musí zůstat pod 2.14, dokud ML Kit nevyjde opravený.** Release 0.3.122
  padal při prvním strukturovaném dotazu (`ExceptionInInitializerError` ← `ClassCastException`
  v `com.google.android.gms.internal.mlkit_genai_prompt.zzvk.zzc`). Gson přibalený v `genai-prompt`
  1.0.0-beta4 je přejmenovaný, ale v metodě, která přidává adaptéry `java.time`, zůstal řetězec
  `Class.forName("com.google.gson.internal.bind.JavaTimeTypeAdapters")` s PŮVODNÍM jménem.
  Chytá jen `ReflectiveOperationException` a `LinkageError` — počítá s tím, že třída není.
  Jenže LiteRT-LM 0.19.1 přitáhl skutečný Gson 2.14.0, který ji má: najde se cizí třída
  a přetypování na kopii z ML Kitu vyhodí `ClassCastException`. Zjištěno `javap -c` nad
  `classes.jar` z `genai-prompt` v keši Gradlu. Gson je proto připnutý `strictly` na 2.13.2
  (tu měl LiteRT-LM 0.11.0; LiteRT-LM bere jen stromové API) a hlídá to `GsonForMlKitTest`
  v `androidHostTest` modulu :client:ai-litert — s 2.14.0 padá. Připnutí i test jsou tam, kde
  je LiteRT-LM: Gson přichází se součástí z Google Play (viz „Runtime jako součást z Google
  Play"), základ aplikace žádný nemá. Kdo bumpuje ML Kit, ověří tím, že v `zzvk`/nástupci
  řetězec zmizel nebo je přejmenovaný, a připnutí smaže.
- **`generateStructured` chytá i `LinkageError`.** Rozbitá inicializace třídy v ML Kitu je
  `Error`, `catch (Exception)` ji minul a padla celá aplikace. Teď je to `StructuredOutputException`
  (volající jde textem, ten tuhle část ML Kitu nepotřebuje), `logger.e` pro Crashlytics
  a strukturovaný výstup se do konce procesu přestane nabízet — třída zůstane rozbitá
  a každý další pokus by skončil `NoClassDefFoundError`.
- **Špatný tvar není rozbitý model.** ML Kit ho hlásí kódy `STRUCTURED_OUTPUT_REQUEST_ERROR`,
  `…_RESPONSE_ERROR` a `…_MAX_TOKENS_ERROR`; ty se převádějí na `StructuredOutputException`
  a návrhy pak zkusí text. Do Crashlytics jako problém nejdou — tam patří jen `OnDeviceAiException`
  rozbitého modelu, a ten se textem nezkouší podruhé (selhal by stejně). Na text se jde i tehdy,
  když po úklidu (`cleanReplySuggestions`: délka, prázdné, zdvojené) nezbyde nic.
- **Prompt o formátu mlčí** (`replyPrompt(structured = true)`): odrážky a závěrečné „Replies:"
  by se praly se schématem, které ML Kit do promptu přidá sám (`includeSchemaInPrompt`).
  Obsahové pokyny (celá věta, jazyk konverzace, tykání) zůstávají — schéma říká, jak odpověď
  vypadá, ne co v ní má být.
- **Klasifikace a výtahy jdou přes `generateAnswer`** (`StructuredAnswers.kt`): podvod
  (`ScamAnswer`), tón (`ToneAnswer`), termín připomínky (`ReminderAnswer`), termín do kalendáře
  (`EventAnswer`) a hledání přirozeným jazykem (`SearchAnswer`). **Typ zrcadlí řádky textového
  promptu a typovaná odpověď jde přes TENTÝŽ parser jako text** (`scamVerdictOf` →
  `parseScamVerdict`, `eventDetailsOf` → společné jádro `eventDetailsFrom` …). Ověření obsahu
  (místo musí být ve zprávě, termín v budoucnu, verdikt jen jednoznačný) tak zůstává na jednom
  místě a strukturovaný výstup jistí jen tvar. Výčty (PHISHING/SAFE/UNSURE, PAST/FUTURE…) dostane
  model jako `enumValues` ve schématu.
- **„Nevím" není důvod zkoušet text.** U klasifikace je `null` platná odpověď; na text se jde
  jen při `StructuredOutputException`. Druhý pokus by stál dvojnásob a přebil by opatrnost, kvůli
  které klasifikace raději mlčí. (U návrhů odpovědí je to jinak: tam prázdno po úklidu text zkusí,
  protože prázdný seznam nic neznamená.)
- **Ověřeno na Foldu všech pět** (15. 10. 2026, featura 648 „Fast Mode (Structured Output)",
  bez jediného pádu na text): podvodná zpráva `ScamAnswer(verdict=PHISHING)` → karta „Možný
  podvod"; hrubý koncept `ToneAnswer(verdict=RUDE)` → „Může vyznít příkře."; „dáš si zítra kafe?"
  `EventAnswer(when=FUTURE, event=YES, title=kafe zítra odpoledne, place=NONE)`; „odkazy z kanalu
  za posledni tyden" `SearchAnswer(text=odkazy, chats=CHANNELS, content=LINKS, from=…, to=…)` →
  čipy Kanály a Odkazy (filtrové slovo v `text` odstraní `refineNaturalSearch`, jako u textu).
  **Obsah zůstává strop modelu**: „den před Silvestrem" vyšel `ReminderAnswer(moment=2026-12-24
  08:00)` místo 30. 12. — tvar je správně, datum ne; uživatel ho v sheetu vidí a může zvolit jiné.
  Svátky s pevným datem teď poznají pravidla (`holidayDate`, `konverzace-akce.md`, „Připomenutí
  zprávy"), takže k modelu se tenhle případ už nedostane.
- **Rozhraní `GenerableProvider` se s víc implementacemi nesloučí**, takže v release buildu je
  soubor `META-INF/services/<rozhraní>` se všemi šesti providery (ověřeno `unzip -p` nad APK),
  a pole všech typů drží jedno keep pravidlo se seznamem tříd (ověřeno `dexdump`).
- **Ověřit jde jen na telefonu s Gemini Nano** (emulátor AICore nemá a Gemma typy neumí). Testy
  (`ReplySuggestionsViewModelTest`) hlídají volbu cesty a náhradu textem s podstrčeným modelem.
- **Ověřeno na Pixelu 11 Pro Foldu** (Android 17, AICore `thirdpartyexperimental`, 15. 10. 2026):
  AICore strukturovaný výstup hlásí a návrhy chodí z něj, bez pádu na text, za ~2 s.
- **Strukturovaný výstup počítá JINÁ featura AICore než text.** V logu AICore
  (`InferenceTask: run`) je typovaný dotaz `648 - SAPI Open Prompt Stable Fast Mode (Structured
  Output)`, textový `636 - SAPI Open Prompt (Regenerative Mode)` — obojí při tomtéž `ModelConfig`
  (STABLE, FULL). Strukturovaný výstup tedy jede na RYCHLÉ variantě a volba „Rychlejší varianta"
  ho nijak neovlivní.
- **Kvalita přesto nevyšla horší, spíš naopak.** Na stejných dvou zprávách (dočasný build se
  strukturovaným výstupem vypnutým, necommitnutý): u „dáš si zítra kafe?" typy „Zítra odpoledne mi
  vyhovuje." / „Kde bys chtěl si sednout?", text „Jsem rád, pak se dojde." / „Žádně, zítra mi
  nebudí."; u faktury typy tři návrhy (jeden zkomolený „Jasně, poslať ti dam."), text jen dva. Vzorek
  je malý a strop modelu (sloh) platí dál — ale tvar je jistý a návrhy jsou vždy tři.

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
- **„Poslední zpráva" je celý BLOK posledních zpráv od téhož autora** (`replyBlockSize`, nejvýš
  `MAX_REPLY_BLOCK_MESSAGES` = 4, testy `ReplyPromptTest`). Lidé píšou po kouscích („Ahoj" /
  „půjdeš v sobotu?" / „?") a otázka pak není v poslední zprávě; model odpovídal na „?" nebo
  smajlík. Blok jde pod „Reply to THESE messages" s pokynem odpovědět na něj jako celek. Kontext
  PŘED blokem zůstává 5 zpráv (`REPLY_EARLIER_MESSAGES`), takže blok kontext neprodlouží
  a nevrátí past s deseti zprávami; z konverzace se proto bere 5 + 4 a rozdělí je až prompt.
  S jedinou zprávou jsou pokyny tytéž jako dřív. Autor se pozná podle jména v přepisu.
  Ověřeno s Gemmou 4 E2B na emulátoru, tatáž konverzace („Ahoj 👋" / „Půjdeš v sobotu s námi
  na oběd?" / „?"): s jedinou zprávou model nabídl „Co máš na mysli?", „Co ti potřebuji říct?",
  „Co je to?", s blokem „Nemůžu, mám jiný plán." a „Jsem v sobotu, rád přijdeš." (sloh viz níž).
- **Tykání/vykání se musí říct výslovně.** Bez instrukce model kamarádovi VYKAL („Co byste
  předpokládali?"). Instrukce jmenuje „tykání" i „vykání" česky — samotné „be informal" model do
  správného tvaru slovesa nepřeloží. Když z konverzace poznat nejde, tyká se: v Telegramu si lidé
  tykají mnohem častěji a tykání cizímu je menší prohřešek než vykání kamarádovi.
- **Jazyk se volí jinak než u shrnutí.** Shrnutí čte uživatel → jazyk aplikace. Návrh se ODEŠLE
  protějšku → jazyk konverzace; jazyk aplikace je jen nápověda, protože jinak model sklouzne
  (na nejednoznačný vstup odpověděl polsky).
- **Návrh se dělá JEDNOU na zprávu** (`ReplySuggestionsViewModel` si drží id). Bez toho by se
  inference pouštěla při každé změně stavu konverzace — přečtení, reakce, scroll.
- **Během letu odeslané zprávy se návrh nepouští** (`state.flight` v `ConversationScreen`).
  Pole se vyprázdní dřív, než odeslaná zpráva dorazí do seznamu, takže poslední zprávou byla na
  okamžik zase ta příchozí: `clear()` při psaní zapomněl id, a model se tak rozjel znovu přesně
  uprostřed animace — a hned nato se zrušil.
- **Kdy nabídnout, je čistá funkce** `replySuggestionTarget` (testy `ReplySuggestionsTest`):
  poslední zpráva příchozí a neservisní, nic rozepsaného, neupravuje se, smí se psát. Vrací id
  zprávy, ne `Boolean` — obrazovka podle něj pozná, že jde o jinou zprávu, a nemusí si pamatovat nic.
- **Výstup modelu se parsuje tolerantně** (`parseReplySuggestions`, testy `ReplyPromptTest`):
  model odrážky nedrží, očísluje je, obalí uvozovkami nebo přidá úvodní větu s dvojtečkou.
  Nikdy nevrátí prázdno kvůli formátu — když řádky rozpoznat nejdou, je návrhem celý text.
  Kde Gemini Nano umí strukturovaný výstup, parser se nepouští vůbec (viz „Strukturovaný výstup").
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
- **Sloh návrhů: co prompt umí a co ne** (17. 10. 2026, Gemma 4 E2B na emulátoru, devět
  konverzací přehraných přes testovacího bota, každá varianta promptu zvlášť):
  - **Pomohlo:** výslovně „zprávu DOSTAL uživatel" s ukázkou obrácení („Will you come?" →
    „Yes, I'll come.", nikdy „You'll come."); u smutné zprávy „odpověz s účastí, neodmítej"
    (dřív na „Babička je v nemocnici" přišlo „Je nebezpečné, nebudu.", teď „Je mi tak líto,
    držím palce."); TŘI české ukázky na různá témata — s jedinou model opisoval obsah („Nemůžu
    zítra, co v sobotu?" i u poděkování za stěhování).
  - **Jazyk zprávy pozná kód** (`looksEnglish`): nápověda „most likely Czech" podle jazyka
    aplikace přebila anglickou zprávu a „Hi, are you coming…?" dostalo české odpovědi. Obecný
    pokyn „anglická zpráva → anglické odpovědi" nešel — samotná zmínka angličtiny stáhla do
    angličtiny i zprávu o babičce. Modelu se proto říká jen výsledek.
  - **Nepomohlo, nezkoušet znovu:** pokyny celé ČESKY (horší — „Jsem zajímavý, co bys řekl?",
    míň návrhů, angličtina zase česky); odpovědi 2–6 slov místo 8 (kratší, ne správnější —
    „Vracím se v délce."); vypuštění „in first person".
  - **Strop modelu zůstává:** zlozvyk začínat „Jsem…" (doslovný překlad „I'm…": „Jsem tam,
    přijdu.", „Jsem v sobotu s námi.") je ve všech variantách, i bez „first person"; tvary
    („Poslím") a rod („Jsem rád, že jsem pomohl") pokyn jen ubere. Další krok by byl větší model
    (E4B, Gemini Nano), ne další ladění promptu. **Gemini Nano neověřené.**

- **Fakta model drží, sloh ne.** Na připraveném testu (ceny, číslo účtu, datum, jména) nevymyslel
  nic — ale na skutečné konverzaci komolí tvary („telefon nebudou mít vibrace“), plete si, kdo co
  řekl, občas propíše do textu instrukci („shrnutí konzultace **v češtině**“) a u pár zpráv nafoukne
  zdvořilosti na plnohodnotné body. Když to nebude stačit, další krok je Gemma 4 přes LiteRT-LM
  za stejným rozhraním.
