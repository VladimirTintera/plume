# Návrh: vlastní model vedle katalogu

> **Stav:** návrh, nic z toho není postavené. Vychází z kódu po #1165 (katalog modelů
> v aplikaci, ai-engine bez konkrétních modelů).

Kdo chce jiný model než ty z katalogu (`LiteRtModel` v `core/ai/ModelCatalog.kt`), si ho zadá
sám: vloží odkaz na soubor na Hugging Face a aplikace ho stáhne a počítá stejně jako modely
z katalogu. Je to **funkce pro nadšence**. Katalog zůstává výchozí cestou a jen modely z něj
jsou ověřené na našich promptech.

Komu to pomůže:

- **Porovnávání modelů.** Hřiště („Vyzkoušet model") a „Porovnání modelů" mají smysl hlavně
  tehdy, když se dá zkusit i model, který jsme do katalogu nedali.
- **Jazyky.** Katalog se ladí na češtinu. Kdo píše jiným jazykem, ví líp než my, který model ho
  umí.
- **Nové modely hned.** Dnes je každý nový model řádek v enumu a vydání aplikace.

## Co už je připravené a co chybí

**Knihovna je připravená z větší části.** ai-engine žádný model nezná a stáhne a spustí
libovolný `DownloadedModel` (datová třída: URL, jméno souboru, velikost, runtime, kontext).
llama.cpp si šablonu chatu bere ze souboru GGUF (`llama_model_chat_template`), a když ji nezná,
použije ChatML.

**Aplikace má katalog pevně v kódu:**

- `LiteRtModel` a `AiProvider` jsou enumy a rozhoduje se podle nich zhruba na patnácti místech
  přes `when`: texty v Nastavení, chybové hlášky (`OnDeviceAiFailureText`), poznámky pod
  úlohami, pořadí automatiky (`AUTOMATIC_ORDER`).
- `DownloadedModel.provider` u modelu mimo katalog spadne na `checkNotNull`. Model mimo katalog
  tam je zatím chyba v kódu.
- `ModelDownloadWorker` po zabití procesu hledá model podle id synchronně
  (`liteRtModelById`). Vlastní model uložený v DataStore by tam nenašel a úloha by skončila
  selháním.
- Soubory leží v jednom adresáři pod `DownloadedModel.fileName`. Dva modely se stejným jménem
  souboru (`model.gguf` ze dvou repozitářů, nebo tentýž soubor jako v katalogu) by si soubor
  přepisovaly a smazání jednoho by smazalo druhý.

## Jak se model zadává

Nastavení → AI v zařízení, pod blokem Modely nový blok **Vlastní modely** s řádkem
**Přidat model**. Na hodinkách ne, na iOS ne (stahované modely tam nejsou).

1. **Odkaz.** Pole přijme `org/repo`, adresu repozitáře i adresu souboru
   (`…/blob/<revize>/<soubor>`, `…/resolve/…`). Jiné adresy než Hugging Face v první fázi ne:
   o souboru by se nevědělo nic kromě velikosti z hlavičky.
2. **Výběr souboru.** Aplikace se zeptá `GET https://huggingface.co/api/models/<repo>?blobs=true`
   a ukáže soubory `.gguf` a `.litertlm` s velikostí. Pokud odkaz mířil na konkrétní soubor,
   tenhle krok se přeskočí.
   - Soubor, na který zařízení nemá paměť (viz níž), je v seznamu, ale nejde vybrat. Řádek
     říká proč.
   - Soubory pro konkrétní NPU (`*.mediatek.*.litertlm`, `*.qualcomm.*`) se skryjí. Na jiném
     čipu nepoběží a v repozitářích litert-community leží hned vedle obecných.
   - Soubor z katalogu (shodný repozitář a soubor, nebo SHA-256) nabídne místo přidání položku
     katalogu.
3. **Potvrzení.** Karta s tím, co se z API zjistilo: jméno (z repozitáře, dá se přepsat),
   velikost, runtime, licence, u GGUF architektura a kontext. Pod kartou věta, že model
   nevybírala aplikace, může psát nesmysly a licenci přijímá ten, kdo ho stahuje (stejně jako
   u Ayi, `settings_aya_license`). Tlačítko **Přidat a stáhnout** se řídí volbou „Stahovat
   jen přes Wi-Fi".

Co API vrací (ověřeno 8. 11. 2026 na `bartowski/aya-expanse-8b-GGUF` a
`litert-community/Qwen3-0.6B`):

| Pole | K čemu |
| --- | --- |
| `sha` | commit, na který se adresa připne (`huggingFaceUrl(repo, commit, file)`) |
| `siblings[].size`, `siblings[].lfs.sha256` | velikost a kontrolní součet souboru |
| `gated` | model za souhlasem odmítnout (viz níž) |
| `cardData.license` | licence na kartu |
| `gguf.architecture`, `gguf.context_length`, `gguf.chat_template` | jen u GGUF: architektura, kontext, šablona chatu |

**Model za souhlasem (`gated`) se nepřidá.** Stažení by chtělo přihlášení na Hugging Face
a token, a ten by aplikace musela uložit. Hláška poradí hledat kvantizaci od někoho jiného,
což je cesta, kterou šel katalog u EuroLLM 9B i Ayi.

## Co se ukládá

Seznam vlastních modelů v `AppSettings` jako JSON (`custom_ai_models`), položka
`CustomModel(id, displayName, repo, commit, file, sizeBytes, sha256, runtime, contextTokens,
license)`. Z položky se staví `DownloadedModel` stejně jako z `LiteRtModel.downloaded`:

- **`id = "custom-" + prvních 12 znaků SHA-256`.** Ukládá se (volby, jméno úlohy stahování),
  takže musí být stálé a nesmí kolidovat s katalogem.
- **Jméno souboru na disku je `id` s příponou**, ne jméno z repozitáře. To řeší kolize
  souborů výš. Adresa se skládá zvlášť, takže jméno na disku může být jiné než to na Hugging
  Face.
- **Kontext:** u GGUF `min(gguf.context_length, 4 096)`, jako mají v katalogu modely přes
  llama.cpp kvůli paměti. U `.litertlm` API kontext nedává, takže 4 096.
- **Paměť (`minMemoryGb`) se odhaduje z velikosti:** dvojnásobek souboru plus 2 GB, zaokrouhleno
  nahoru na stupeň 4 / 6 / 8 / 12 / 16 GB. Na katalogu to sedí u všech položek kromě Qwen3 4B,
  kde 12 GB vychází z měření na GPU (vzorec dá 8). Hranice je tvrdá jako u katalogu
  (`canRun`): systém by aplikaci při načítání zabil a to je horší než model nenabídnout.
- **`multimodal = false` vždycky.** Že soubor bere zvuk nebo obraz, se z metadat spolehlivě
  nepozná a přepis i popis fotky jsou laděné na Gemmu.

## Poskytovatel a úlohy

**Jeden nový poskytovatel `AiProvider.Custom`** a v `AiModels` volba `custom: String?`
(id vybraného vlastního modelu), stejně jako `gemma`, `qwen`, `euroLlm` a `aya` vybírají
velikost. Vlastní modely jsou tedy pro zbytek aplikace „velikosti" jednoho poskytovatele.

- **Proč ne obecný odkaz na model místo enumu:** `AiProvider` se ukládá (zapnuté modely
  `on_device_ai_models`, přiřazení úloh), takže změna typu by znamenala migraci nastavení
  a přepsání všech `when`. Jeden poskytovatel navíc je jedna větev v každém `when`.
- **Daň:** zapnutý může být jen jeden vlastní model najednou. Pro porovnávání to stačí, přepíná
  se v hřišti čipem nebo výběrem v Nastavení. Víc naráz je případná fáze 3.
- **`DownloadedModel.provider`** pozná vlastní model podle předpony `custom-`; `checkNotNull`
  zůstane jen pro id, které nemá ani předponu, ani řádek v katalogu.
- **Jen text** (`handles`): zvuk a obraz dál jen Gemma.
- **Do automatiky ne.** Vlastní model počítá jen to, co mu uživatel přiřadí v „Co čím počítat",
  a hřiště. Shrnutí, triáž nebo termíny z neznámého modelu by vypadaly jako chyba aplikace.
  K tomu malý model nebo model bez instrukčního ladění rozbije úlohy, které čtou pevný formát
  odpovědi (triáž, termíny, hledání, návrhy odpovědí, `StructuredAnswers`). Přiřazení úlohy
  vlastnímu modelu proto ukáže jednou větu, že úloha může selhávat.
- **Chybová hláška** jmenuje model jménem z položky, ne poskytovatele (`ai_failure_custom`
  s argumentem).

## Přemýšlení (thinking)

**Dnes je vypnuté u LiteRT-LM** (`enable_thinking = false` v `LiteRtEngineFactory`). Přidalo se
s Gemmou a hodilo se u Qwen3. Důvody jsou návrhové, změřené to není:

- Úlohy jsou krátké a chtějí odpověď hned: návrhy odpovědí, triáž a podvod jdou s každou
  zprávou. Přemýšlení by nejdřív napsalo desítky až stovky tokenů úvah. Na telefonu je to
  ~10–20 tokenů za vteřinu (Qwen na Galaxy S26), tedy sekundy navíc, než přijde první slovo
  odpovědi.
- Úvahy se počítají do kontextu (4 096 tokenů u Qwenu) a do stropu výstupu
  (`maxOutputTokens`), takže by se odpověď usekla dřív.
- U LiteRT-LM jdou úvahy do zvláštního kanálu. Knihovna z odpovědi bere jen text, takže
  uživatel by úvahy neviděl, jen by čekal.

**U llama.cpp vypnuté není a s katalogem to nevadí:** EuroLLM ani Aya nepřemýšlejí.
U vlastního modelu to vadit bude:

- `llama_chat_apply_template` není parser šablon Jinja, šablonu jen podle obsahu přiřadí
  ke známé rodině (komentář v `llama-chat.cpp`). Šablona Qwen3 obsahuje `<|im_start|>`,
  takže vyjde ChatML s prostým `<|im_start|>assistant\n`. To, co šablona Qwen3 dělá při
  `enable_thinking = false` (vloží prázdný blok `<think>\n\n</think>\n\n`), se tím ztratí.
  Model začne přemýšlet a úvahy přijdou v textu odpovědi.

Návrh pro llama.cpp:

1. **Vypnout předvyplněním:** obsahuje-li šablona v souboru `enable_thinking` (dá se zjistit už
   z `gguf.chat_template` v API, nebo za běhu z `llama_model_chat_template`), přidá se za výzvu
   pro asistenta prázdný blok `<think>\n\n</think>\n\n`, přesně jako by to udělala šablona.
2. **Pojistka v toku:** `<think>…</think>` na začátku odpovědi se z textu odstraní. Šetří to
   oči, ne čas: tokeny se spočítat musely.
3. **Co se vypnout nedá**, se přesto přidat dá, jen s větou na kartě, že model přemýšlí
   a odpovídá pomaleji. Patří sem modely, které přemýšlejí vždycky (destiláty DeepSeek-R1)
   nebo mají vlastní kanály (gpt-oss, `[THINK]` u Magistralu). Pojistka v bodu 2 pokryje jen
   `<think>`.

**Úvahy v hřišti** by mohly být vidět (sbalený blok „Úvahy" nad odpovědí, zapnutí u čipu
modelu). Jako měřák je to zajímavé, protože ukáže, kolik času přemýšlení stojí. Je to ale
samostatná změna knihovny (kanál úvah z LiteRT-LM, u llama.cpp rozdělení toku) pro katalog
i vlastní modely, proto až fáze 2.

## Změny v knihovně ai-engine

Malé a užitečné i pro katalog:

- **Úloha stahování nese popis modelu v `inputData`**, ne jen id. Všechna pole
  `DownloadedModel` jsou primitivní. `modelById` aplikace zůstává jako přednostní zdroj
  (katalog smí model stáhnout), popis z úlohy je záloha pro model, který aplikace synchronně
  nezná.
- **Volitelný `sha256` v `DownloadedModel`**, ověřený po stažení a před přejmenováním `.part`.
  U vlastního modelu je to ochrana proti useknutému nebo podvrženému souboru. Katalog ho
  může doplnit taky. Kolik trvá spočítat SHA-256 z 5 GB na telefonu, je potřeba změřit; podle
  toho buď vždycky, nebo jen u vlastních modelů.
- **Předvyplnění prázdných úvah u llama.cpp** (viz výš), řízené parametrem enginu, ne
  jménem modelu.

## Rizika

- **Kompatibilita až při načtení.** GGUF s architekturou, kterou přibalený llama.cpp
  (`b12170`) nezná, nebo `.litertlm` z jiné verze LiteRT-LM se stáhne, ale nenačte. Chyba
  načtení u vlastního modelu proto dostane vlastní hlášku („Tenhle model aplikace neumí
  spustit"), ne obecnou. Architekturu z API se dá porovnat se seznamem, který llama.cpp zná,
  a varovat ještě před stažením. Seznam by se ale musel udržovat s každým bumpem.
- **Šablona chatu.** GGUF bez šablony, nebo s šablonou, kterou heuristika nepozná, dostane
  ChatML. U modelu s jiným formátem pak vychází nesmysl. Karta to řekne předem
  (`gguf.chat_template` chybí).
- **Bezpečnost souboru.** Parser GGUF v llama.cpp měl v minulosti chyby, kterými šlo
  podvrženým souborem poškodit paměť. Proto jen Hugging Face, připnutý commit, SHA-256
  a llama.cpp držený aktuální. Soubor z disku (fáze 2) má totéž riziko bez kontroly původu.
- **Google Play a generativní AI.** U vlastního modelu aplikace výstup nevybírala ani
  neověřila. Před vydáním na Androidu ověřit, co to znamená pro zásady Play: „Nahlásit
  odpověď" už je na roadmapě kvůli hřišti. Kdyby to byl problém, zbývá možnost nabízet
  vlastní modely jen na desktopu.

## Úložiště a mazání

- **Úložiště** ukazuje vlastní modely mezi ostatními, jménem z položky.
- **Odebrání modelu ze seznamu** smaže i soubor a keš (`ownsCacheFile`) a zruší přiřazení úloh,
  takže ty se vrátí na automatiku.
- **Smazání souboru** položku nechá, aby šel model stáhnout znovu.

## Fáze

1. **Hugging Face, GGUF i `.litertlm`, jen text.** Ruční přiřazení úloh a hřiště, jeden
   zapnutý vlastní model, vypnuté přemýšlení u llama.cpp, SHA-256, popis modelu v úloze
   stahování. Android a desktop.
2. **Soubor z disku** (hlavně desktop: odkaz na soubor, nebo kopie do adresáře modelů) a úvahy
   v hřišti.
3. **Víc vlastních modelů zapnutých naráz** (obecný odkaz na model místo enumu
   `AiProvider`), případně zvuk a obraz u souborů, které je umí.

## Otevřené otázky

- Má vlastní model smět i do automatiky, třeba až po ručním zapnutí volby „Používat
  automaticky"?
- Tvrdá hranice paměti, nebo „Přesto přidat" s varováním? Návrh drží tvrdou hranici.
- Android i desktop, nebo napřed jen desktop, dokud není jasno se zásadami Play?
