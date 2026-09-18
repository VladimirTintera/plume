# Konverzace — co dodělat

Živý dokument. Nové nápady sem, hotové věci ven (a poučení do `CLAUDE.md`).
Pořadí je doporučení, ne závazek.

## Stav dnes (září 2026)

Konverzace umí: historii se stránkováním, skok na první nepřečtenou, předěly dne, živé zprávy,
odesílání s letem bubliny, koncept uložený v TDLib, „píše…" oběma směry, úpravu vlastní zprávy,
odpovědi (i swipem) se skokem na citaci, reakce, nabídku nad zprávou (odpovědět, upravit,
kopírovat, připnout/odepnout, smazat, oblíbené samolepky), připnuté zprávy s odepínáním,
formátování textu a náhledy odkazů, fotky s prohlížečem, samolepky (i animované `.tgs`),
animovaná emoji, výběr samolepek (sady, hledání, oblíbené, trendy) a emoji (hledání, nedávná,
odstíny pleti), profilovky ve skupinách, sponzorované zprávy, stav doručení.

Aplikace má nastavení přes DataStore (`AppSettings`) a první testy (`commonTest`,
`./gradlew :client:shared:testAndroidHostTest`).

## Doporučené pořadí dalších kroků

1. **Přílohy.** Telegram víc příloh v jedné zprávě nenese — album je skupina samostatných zpráv
   se stejným `mediaAlbumId` (viz `CLAUDE.md`, „Alba"). Po krocích:
   1. ~~**Zobrazení přijatých alb**~~ — hotovo, mřížka v jedné bublině.
   2. ~~**Posílání jedné fotky nebo souboru**~~ — hotovo (výběr, zmenšení fotky, průběh nahrávání).
   3. ~~**Posílání více příloh jako album s popiskem**~~ — hotovo (`sendMessageAlbum`, přílohy
      nad polem v M3 carouselu, popisek z textu pole).
   4. ~~**Zrušení nahrávání**~~ — hotovo (křížek v průběhu, i u dlaždice alba).
   5. ~~**Videa** ve výběru i v albu~~ — hotovo (bez překódování; přehrávání viz bod 2).
2. **Přehrávání videa a hlasovek.** ~~Hlasovky~~ — hotovo (přehrávání v bublině, waveform
   s posunem, poslechnuto). U hlasovek zbývá rychlost přehrávání, automatické pokračování další
   neposlechnutou, sluchátko u ucha a audio focus / přerušení hovorem. ~~Nahrávání~~ — hotovo
   (ťuknutím); chybí podržení se zamčením, náhled před odesláním a průběh nahrávání hlasovky.
   ~~Video~~ — hotovo v prohlížeči (Media3 / AVPlayer). Zbývá **streamování** (dnes se stahuje
   celé), rychlost, ztlumení, obraz v obraze a GIFy (`MessageAnimation`, přehrávat bez zvuku
   přímo v bublině).
3. ~~**Přeposílání zpráv**~~ — hotovo (položka v nabídce, výběr chatu, album celé). Zbývá
   hledání na serveru (`searchChats`), víc cílů najednou, výběr víc zpráv a kopie bez odesílatele.
4. ~~**Otevírání dokumentů**~~ — hotovo (stažení s průběhem, `ACTION_VIEW` / `UIDocumentInteractionController`).
   Zbývá zrušení stahování, uložení do Souborů/Downloads a otevření audia (zatím se jen popisuje).
5. **Odlehčení vzhledu.** Hotové je všechno kromě odkrývání časů tahem: ~~zaoblení na konci
   bloku bublin~~, ~~čas se stavem doručení z bubliny ven~~, ~~výška fotek~~, ~~fotky a videa na
   plochu~~ a ~~náhled odkazu na plochu~~. Poučení v `CLAUDE.md` („Tvar bubliny a seskupení",
   „Čas a stav doručení", „Médium bez bubliny", „Rámeček fotky v bublině", „Náhled odkazu bez
   bubliny"). Zbývá bod 4 níž.
6. Drobnosti ze sekcí níž (zásobník, nabídka, entity, média, emoji).

---

## Odlehčení vzhledu: co nepatří do bubliny

Inspirace je Google Messages. Tři samostatné kusy, dají se dělat nezávisle.

Hotové z tohohle seznamu: tvar bubliny na konci bloku, čas se stavem doručení, výška fotek,
médium na ploše i náhled odkazu. Zbývá poslední bod, odkrývání časů. Poučení ze všeho je
v `CLAUDE.md`.

### 1. ~~Fotky a videa na plochu, mimo bublinu~~ — HOTOVO

Fotka, video i album se kreslí přímo na plochu konverzace (`showsBareMedia` v `MessageRows.kt`,
testy v `MessageRowsTest`); popisek pod nimi dostal vlastní kontejner v barvě bubliny a se snímkem
tvoří skupinu se staženým nárožím. Poučení v `CLAUDE.md`, „Médium bez bubliny".

Co z toho zůstalo:

- **Obrázek v náhledu odkazu je pořád v bublině.** Řeší ho bod 3, kde se navíc mění celý tvar
  náhledu — sjednocovat to dřív by znamenalo dělat tu práci dvakrát.
- **Fotka s citací si bublinu drží** a nejspíš to tak zůstane: citace bez pozadí se ztratí.
  Kdyby se to mělo změnit, potřebuje citace vlastní kontejner nad snímkem, ne průhledné pozadí.
- **Přeposlaná zpráva se zatím nepozná.** Google Messages dává vedle takového snímku šipku
  (druhá předloha). Zdroj přeposlání (`forwardInfo`) doménový `ChatMessage` nenese vůbec, takže
  je to samostatný kus práce — ne důsledek téhle změny.

### 2. ~~Fotky mají respektovat výšku~~ — HOTOVO

Fotka i video v bublině se **zužují, neořezávají**: výška se spočítá z poměru stran, zastropuje
se, a když se strop uplatní, dopočítá se šířka zpátky (`photoFrame` v `MediaFrame.kt`, testy
v `MediaFrameTest`). Svislý snímek obrazovky je tím vidět celý. Poučení v `CLAUDE.md`,
„Rámeček fotky v bublině".

Co z toho zůstalo:

- ~~**Popisek táhne bublinu širší, než je fotka.**~~ Vyřešil to bod 1: popisek má vlastní
  kontejner, takže kolem zúžené fotky žádná prázdná plocha nezbývá. Zůstalo jen to, že široký
  popisek může být širší než snímek nad ním — jako dva kusy jedné skupiny to ale vypadá správně,
  stejně jako v Google Messages.
- **Album zůstává ve čtvercích.** Je to vědomé rozhodnutí (`CLAUDE.md`, „Alba" — mřížka, ne
  mozaika podle poměrů stran), takže jeho změna je změna toho rozhodnutí, ne pokračování tohohle
  bodu. Řádek alba s jednou fotkou se ale ořezává pořád.
- **Náhled odkazu se ořezává dál.** Řeší ho bod 3, který ten obrázek stejně přestěhuje na plochu.

### 3. ~~Náhled odkazu: obrázek na plochu~~ — HOTOVO

Náhled s velkým obrázkem je skupina na ploše: text zprávy, snímek a pod ním web s titulkem
a popisem ve vlastním kontejneru (`showsBareLinkPreview`, `BareLinkPreview`). Šířku určuje
obrázek, takže blok nemá roztřepený okraj. Poučení v `CLAUDE.md`, „Náhled odkazu bez bubliny".

Co z toho zůstalo:

- **Malý náhled je pořád karta s proužkem uvnitř bubliny.** Čtvereček 48 dp vedle textu je řádek,
  ne snímek; na ploše by se rozpadl na dva kusy. Že je malý, rozhoduje odesílatel.
- **Barva z obrázku se nedělá** a dělat nebude — kontejnery berou barvu bubliny. Důvody zůstávají
  platné: TDLib barvu neposílá, kvantizace by chtěla keš a vlákno navíc, a předloze by se stejně
  netrefila.
- **`displayUrl` doménový `LinkPreview` pořád nenese.** Do doplňkového textu se dnes bere
  `siteName`; bez něj (a bez titulku i popisu) se kontejner nekreslí vůbec.
- **Náhled nad textem (`showAboveText`) se dál ignoruje.**

### 4. Odkrývání časů tahem doleva

Čas z bubliny **už zmizel** (viz `CLAUDE.md`, „Čas a stav doručení"), takže se v konverzaci dá
přečíst jen z předělu dne a z plovoucího data při scrollu. Zbývá ho zpřístupnit na vyžádání.

Google Messages **posouvá celou plochu doleva** a odkryje vpravo časovou osu; po puštění se
zase schová. Tady je ta část, která si žádá rozhodnutí:

- **Je to gesto nad SEZNAMEM, ne nad položkou.** V Messages jedou všechny řádky společně;
  u nás je vodorovný `draggable` na každé bublině zvlášť (odpověď swipem).
- **Kolize je skutečná.** Bublina má `draggable(Orientation.Horizontal)` s
  `coerceIn(0f, maxDrag)`, takže tah doleva se nikam neprojeví — ale `draggable` si gesto po
  slopu vezme celé a **rodiči už ho nepustí**. Tah doleva se tedy dnes tiše zahazuje a
  seznamová varianta by se k němu nedostala.
- **Dvě cesty, ani jedna zdarma:**
  1. **Po řádcích** — povolit bublině i negativní posun a odkrývat čas u té jedné zprávy.
     Malá změna, ale není to, co dělá Messages, a u jedné bubliny to vypadá spíš jako druhá
     polovina swipu na odpověď.
  2. **Nad seznamem** — bublina nesmí tah doleva spolknout. `draggable` to neumí vyjádřit
     (po slopu je směrově agnostický), takže by to chtělo **vlastní detektor, který po slopu
     rozhodne jednou za gesto** — přesně ten vzor, který už v projektu je v prohlížeči médií
     (`CLAUDE.md`: „`detectTransformGestures` s pagerem nejde… vlastní detektor"). Doprava =
     odpověď (konzumuje), doleva = nekonzumuje a nechá to seznamu.
- **Formátovač času `asTimeOfDay()` je s odstraněním času smazaný** — byl to mrtvý kód. S tímhle
  bodem se musí vrátit (je to pět řádek nad `kotlinx.datetime`).
- **Čas musí zůstat dostupný i bez gesta.** Čtečka obrazovky se tahem nikam nedostane, takže
  patří do nabídky nad zprávou — váže se to na „Informace o zprávě", která je v seznamu
  u sekce B).

---

## A) Odpověď swipem — HOTOVO

Tažení zprávy doprava otevře odpověď; vlevo od bubliny se přitom odkrývá šipka. Poučení je
v `CLAUDE.md`, sekce „Odpověď swipem". Kód je v `MessageBubble` v `ConversationScreen.kt`.

Co zůstalo otevřené:

- **Nekontroluje se, jestli jde na zprávu vůbec odpovědět.** `canBeReplied` známe teprve
  z `getMessageProperties`, a to se ptáme až při otevření nabídky — dotazovat se na každou zprávu
  v historii kvůli gestu, které se použije jednou za čas, nedává smysl. V praxi to nevadí, protože
  chaty jen pro čtení zatím neřešíme ani ve vstupním poli.

## B) Popup nad zprávou místo bottom sheetu — HOTOVO

Dlouhý stisk otevře pruh rychlých reakcí nad bublinou a nabídku akcí pod ní, zbytek obrazovky
je pod závojem s dírou na vybrané zprávě. Poučení je v `CLAUDE.md`, sekce „Nabídka nad zprávou".
Kód: `MessageMenu.kt`, `MessageMenu`/`PendingDelete` v `ConversationViewModel.kt`,
`messageActions`/`deleteMessages` v repository, `core/ui/TextClipboard.kt`.

V nabídce zatím: **Odpovědět, Upravit, Kopírovat, Připnout/Odepnout, Smazat**. Mrtvé položky se tam nedávají, takže zbytek
přibude s tím, co bude umět:

- **Přeposlat** — potřebuje výběr cílového chatu, tedy vlastní obrazovku.
- **Upravit popisek média** (`editMessageCaption`) a **zprávu s ručním formátováním** —
  vstupní pole umí jen holý text, takže úprava se u nich zatím nenabízí.
- **Vybrat víc zpráv** — hromadné mazání a přeposílání, znamená režim výběru v celé konverzaci.
- **Informace / odkaz na zprávu** (`canGetLink`, `canGetViewers`, `canGetReadDate`).

## C) Entity v textu (odkazy) — HOTOVO

Odkazy, tučné, kurzíva, podtržení, přeškrtnutí, kód a spoiler se vykreslují a odkaz se dá
otevřít. Poučení je v `CLAUDE.md`, sekce „Formátování textu (entity)". Kód: `MessageEntity.kt`,
`captionBody()` v `MessageContent.kt`, `core/ui/RichText.kt`.

Co z toho zůstalo nedodělané:

- **Zmínky, hashtagy a příkazy botů** se nemapují. Kliknutí by nemělo kam vést — potřebují
  vyhledání uvnitř aplikace (`searchPublicChat`, `searchChatMessages`), ne prohlížeč. Až bude kam,
  přibude sem typ entity a obarvení.
- **Citace v textu** (`BlockQuote`, `ExpandableBlockQuote`) se vykreslují jako obyčejný text.
  Svislý pruh vlevo nejde udělat uvnitř jednoho `Text`; znamená to rozpad textu na bloky
  a `Column` místo jedné `Text` — samostatný kus práce.
- **Náhled odkazu nad textem** (`showAboveText`) — náhled se zatím kreslí vždycky pod ním.
- **Vlastní emoji** (`TextEntityTypeCustomEmoji`) jsou samolepky uvnitř textu; patří k médiím.

## D) Otevření média — HOTOVO pro fotky

Ťuknutí na fotku ji otevře přes celou obrazovku; štípnutí a dvojité ťuknutí přibližují, tah dolů
zavírá, do stran se listuje fotkami chatu. Poučení je v `CLAUDE.md`, sekce „Prohlížeč médií". Kód: `features/media/`,
`MediaRoute` v `Routes.kt`, `fullFileId` v `MessageMedia.Photo`.

Co zbývá:

- **Ostatní typy médií.** GIF a dokumenty se pořád jen popisují. Prohlížeč
  u nich zatím řekne, že je neumíme otevřít. Přehrávání je samostatná fáze (potřebuje platformní
  přehrávač přes `expect`/`actual`); u dokumentů stačí míň — stáhnout a předat systému.
- **Uložení a sdílení** fotky ven z aplikace.
- **Samolepky `.webm`** (VP9 s průhledností) jsou pořád jen statický náhled — potřebují
  softwarový dekodér (libvpx), iOS WebM neumí vůbec.
- **Průběh stahování originálu.** `RemoteImage` ukazuje náhled z bubliny, dokud nedorazí ostrá
  verze, ale u velké fotky na pomalé síti není poznat, že se něco děje.
- **Let z bubliny na širokém rozvržení.** `LocalTwoPane` tam sdílené prvky vypíná kvůli seznamu
  vedle konverzace; pro prohlížeč to neplatí (ten překryje všechno), ale rozlišit to znamená
  zjemnit ten příznak.

## Zásobník: co jsem našel při čtení kódu

Seřazeno podle toho, jak moc to bolí.

**Chyby** — opraveno, poučení v `CLAUDE.md` („Hlášení přečtení a indexy" a „Odesílání
a scrollování" u duplicitního klíče po potvrzení odeslání).

**Chybějící základ**

- **Skok na první nepřečtenou** — hotovo, ale s **limitem pěti stránek historie**. Kdo má
  v chatu stovky nepřečtených, skok nedostane. Dořešit by to znamenalo stránkovat oběma směry
  (kotva uprostřed přes záporný `offset` v `getChatHistory`).
- **Označení části textu.** Kopíruje se jen celá zpráva z nabídky; vybrat úryvek
  (`SelectionContainer`) zatím nejde.

---

## Výběr samolepek a emoji

Hotové: záložky Nedávné, Oblíbené, Trendy a sady se swipem; hledání samolepek i emoji; oblíbené;
sada ze samolepky v chatu; emoji vkládané do textu včetně nedávných. Zbývá:

- **Dvě různé pleti u emoji s více osobami** (👫 s různým odstínem každé osoby) — nabízí se jen
  stejný odstín pro všechny.
- **Emoji novější než Unicode 15.0** — až je budou znát systémová písma podporovaných verzí.
