# Návrh: konverzace s modelem v zařízení

> **Stav:** MVP (fáze 1) je hotové — viz `docs/poznatky/ai-v-zarizeni.md`, „Vyzkoušet model
> (hřiště)". Proti návrhu: Kopírovat a Odpovědět znovu jsou ikony pod odpovědí místo nabídky
> po podržení (objevitelnější) a Nahlásit odpověď zatím chybí (roadmapa).

Volný rozhovor s modelem, který počítá telefon. Má dva účely a **pořadí mezi nimi je vědomé**:

1. **Hřiště na zkoušení modelů.** Dnes se modely porovnávají jen přes shrnutí a vysvětlení
   („Porovnání modelů" v `docs/poznatky/ai-v-zarizeni.md`): pevný prompt, jedna odpověď. Volný
   rozhovor ukáže, co tam vidět není — jestli model drží kontext přes víc kol, jestli poslouchá
   instrukce, jak mluví česky, když zrovna neshrnuje, co dělá na hraně limitu a jak je rychlý.
   Se šesti poskytovateli (Nano, Apple, Gemma, Qwen, EuroLLM, Aya) je to jediné místo, kde se
   to dá posoudit přímo.
2. **Pokec pro radost.** Malý model v telefonu s ChatGPT ani Gemini nesoupeří; jeho tahák je, že
   rozhovor neopustí zařízení a jede offline. To je dobrý příběh, ne důvod, proč by to lidi
   používali — proto hřiště napřed.

**Není to chatbot se znalostmi.** Model nic nehledá a nemá přístup k chatům. Kdo chce odpověď
z konverzace, má shrnutí a vysvětlení.

## Kde v aplikaci

- **Nastavení → AI v zařízení, řádek „Vyzkoušet model"** pod blokem Modely, jen když je
  zapnutý aspoň jeden model. Nová routa `AiPlaygroundRoute` (registrace
  v `NavSavedStateConfiguration`!) jako detail sekce, řádek i v hledání v Nastavení
  (`SettingsSearch`).
- **Do seznamu chatů ne, aspoň zatím.** Je to klient Telegramu; umělý chat mezi skutečnými by
  s nimi splýval (složky, archiv, hledání, sdílení do něj). Vrátit se k tomu dá, až se hřiště
  osvědčí — pak jako připnutý řádek à la Uložené zprávy, ne jako `ChatSummary`.
- Hodinky ne. Desktop ano (Gemma, Qwen, EuroLLM, Aya), iOS s Apple Intelligence.

## Obrazovka

- **Hlavička:** nadpis a vpravo **čip s modelem** (název + stav). Ťuknutí otevře nabídku
  (`ExpressiveMenu`) se zapnutými modely; nepřipravený je v ní se stavem („model chybí")
  a výběr vede do Nastavení ke stažení. Výchozí model je ten, kterému automatika dá úlohu
  `AiTask.Chat` (viz níž); ruční výběr platí jen pro tenhle rozhovor. Nabídka „⋮": Nový
  rozhovor, Pokyny pro model (fáze 3).
- **Bubliny:** vlastní vpravo, modelu vlevo, vzhled jako v konverzaci (barvy a tvar), ne
  `MessageFrame` celý — ten nese věci kolem zpráv z TDLib. Text odpovědi bez Markdownu
  (malé modely ho sypou nekonzistentně); `**tučné**` a odrážky až podle toho, jak to vypadá.
- **Patička odpovědi** — tady je hodnota hřiště: `Gemma 4 E4B · 1,8 s do prvního slova ·
  6,2 s · ~14 tok/s`. Tokeny přes `countTokens`, kde ho model umí, jinak
  `estimatedTokenCount` s vlnovkou. Useknutá odpověď (`Generated.truncated`) to řekne.
- **Předěl „Starší zprávy si model nepamatuje"** nad prvním kolem, které se do kontextu už
  nevešlo (viz Ořez). Je to přesně ten moment, kdy se model začne chovat divně, a uživatel
  má vědět proč.
- **Podržení odpovědi** (`contextClickable`): Kopírovat, Znovu, Zkusit jiným modelem (fáze 2),
  Nahlásit odpověď.
- **Vstupní pole:** vlastní, jednoduché (`FormTextField`-like, odeslat jako ikonové tlačítko),
  ne `MessageInput` — ten má desítky parametrů vázaných na Telegram. Během generování se
  odesílací tlačítko mění na **Zastavit** (jako streamované odpovědi botů).
- **Prázdný stav:** `IntroEmptyState` s větou „Odpovídá model v tomhle zařízení. Může se mýlit;
  nic z rozhovoru neopouští telefon." a pár čipy s náměty (vtip, vysvětli jako dítěti, přelož,
  napiš básničku). Náměty z `composeResources`, ne natvrdo.

## Architektura

- **Balíček `features/aiplayground/`**: `AiPlaygroundScreen`, `AiPlaygroundViewModel`
  (`OnDeviceAi` + nastavení modelů), čistý stav `List<Turn>` (otázka, odpověď, model, měření,
  stav generování). ViewModel s klíčem není potřeba — obrazovka je jedna.
- **Nová úloha `AiTask.Chat(AiInput.Text)`.** Výchozí model se pak vybírá stejně jako u
  ostatních funkcí (`variantFor`), řádek v „Co čím počítat" přibude sám a `gemmaServesText`
  ho započítá do prodlevy uvolnění Gemmy.
- **Každé kolo je jedno volání `OnDeviceAi.generate(variant, prompt)`, bez stavu v runtime.**
  Prompt se poskládá z pokynu (`ChatPrompt.kt` v `core/ai`, promptový soubor s obvyklou
  hlavičkou o výjimce z kontroly diakritiky), přepisu historie a nové zprávy. Proč takhle:
  - `generate` mají všichni poskytovatelé; Nano přes ML Kit stavovou session nemá, Apple
    a LiteRT-LM ano (`LanguageModelSession`, `LlmConversation`), llama.cpp neověřeno. Jedna
    cesta pro všech šest = férové porovnání a žádná změna runtime v MVP.
  - Cena: každé kolo přepočítá celý kontext znovu (prefill), takže čas do prvního slova roste
    s délkou rozhovoru. Pro hřiště je to spíš informace než vada; zrychlí to fáze 4.
  - Historie jde do jednoho uživatelského tahu jako přepis („Uživatel: … / Ty: …"), ne přes
    šablonu chatu modelu. Kvalitu to může srazit — změřit proti fázi 4.
- **Ořez historie:** rozpočet = limit vstupu modelu (`ModelInfo`) minus rezerva na odpověď
  (třeba 1 024 tokenů). Vyhazují se nejstarší kola CELÁ, nikdy půl otázky. Počítá se pro
  aktuální model — po přepnutí na Qwen (4 096) se předěl posune.
- **Nic se neukládá.** Rozhovor žije ve ViewModelu; odchod z obrazovky nebo smrt procesu ho
  zahodí. Sedí to k „nic neopouští telefon" a odpadá otázka šifrování a mazání. Kdyby se
  ukládal, patří do šifrované databáze, ne do DataStore.
- **Paměť:** dokud je obrazovka otevřená, Gemma se neuvolní; po odchodu běží obvyklá prodleva
  (`GEMMA_IDLE_TEXT_MS`). Úsporný režim a přehřátí (`DeviceLoad`) platí jako u ostatních
  funkcí — hláška místo odpovědi, ne tiché čekání.
- **Chyby** přes `OnDeviceAiFailure` a `userMessage` jako jinde; text z runtime na obrazovku
  nejde.
- **Testy** ViewModelu s falešným `OnDeviceAi` (s `Dispatchers.setMain`): skládání promptu,
  ořez kol a posun předělu při změně modelu, Zastavit, Znovu, měření.

## Fáze

1. **MVP:** obrazovka, `AiTask.Chat`, bezestavová kola s ořezem, patička s měřením, Zastavit,
   Znovu, Kopírovat, Nahlásit, nový rozhovor. Ověřit na emulátoru s Gemmou E2B, na Foldu s Nano.
2. **Porovnání:** „Zkusit jiným modelem" u odpovědi spočítá totéž kolo (se stejnou historií)
   jiným zapnutým modelem. Varianty jedné odpovědi se přepínají šipkami „‹ 2/3 ›" a **do
   kontextu dalšího kola jde ta zobrazená** — tak je jasné, na co model navazuje. Modely po
   sobě, ne souběžně (stejně jako porovnání shrnutí).
3. **Pokyny pro model:** vlastní systémová instrukce na rozhovor (persona, „odpovídej
   anglicky", „buď stručný"), u LiteRT-LM a llama.cpp i teplota. Nano ani Apple vzorkování
   nastavit nedají, volba se u nich neukáže.
4. **Stavová konverzace:** `OnDeviceAi.chat(variant)` vracející session, která drží KV keš
   (LiteRT-LM `LlmConversation` už historii umí, dnes se zakládá jedna na dotaz; Apple
   `LanguageModelSession`; llama.cpp ověřit), s návratem k bezestavovým kolům u Nano. Měřit
   přínos na čase do prvního slova v desátém kole; když je malý, fáze odpadá.

## Otevřené otázky

- **Pravidla Google Play pro obsah generovaný AI.** Aplikace s generativním chatbotem má mít
  podle všeho způsob, jak nahlásit urážlivou odpověď — proto „Nahlásit odpověď" v MVP. Znění
  a co se má s nahlášením stát (stačí e-mail / formulář?) ověřit v Policy Center před vydáním.
  Totéž se možná týká už dnešních funkcí.
- **Bezpečnostní filtry systémových modelů.** Nano (AICore) i Apple některé dotazy odmítnou;
  odmítnutí má být hláška, ne chyba („Model na tohle neodpoví"). Zjistit, jak se ohlásí.
- **Název:** „Vyzkoušet model" (hřiště) vs. „Pokec s AI". Návrh je postavený na prvním.
- **Licence Aya (CC BY-NC):** volný rozhovor je pořád nekomerční použití uživatelem, ale
  ověřit, že to nic nemění na tom, co Nastavení před stažením říká.
