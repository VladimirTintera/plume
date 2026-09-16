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
      Chybí **popisek** k příloze a **zrušení nahrávání** (smazání rozepsané zprávy).
   3. **Posílání více fotek jako album** — `sendMessageAlbum` (2–10, pravidla skládání typů).
      Vybrané fotky nad vstupním polem v **M3 `HorizontalMultiBrowseCarousel`** s možností
      některou odebrat — tady carousel sedí (v bublině alba ne, tam se pral se swipem na odpověď).
2. **Přehrávání videa a hlasovek** — dnes se jen popisují. Platformní přehrávač přes
   `expect`/`actual` (Media3 / AVPlayer); hlasovky jsou jednodušší a častější, hodí se první.
3. **Přeposílání zpráv** — položka v nabídce + obrazovka výběru cílového chatu.
4. **Otevírání dokumentů** — stáhnout a předat systému; výrazně méně práce než přehrávání.
5. Drobnosti ze sekcí níž (zásobník, nabídka, entity, média, emoji).

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

- **Ostatní typy médií.** Video, GIF, hlasovka a dokumenty se pořád jen popisují. Prohlížeč
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

**Chyby** — opraveno, poučení v `CLAUDE.md` („Hlášení přečtení a indexy").

**Chybějící základ**

- **Skok na první nepřečtenou** — hotovo, ale s **limitem pěti stránek historie**. Kdo má
  v chatu stovky nepřečtených, skok nedostane. Dořešit by to znamenalo stránkovat oběma směry
  (kotva uprostřed přes záporný `offset` v `getChatHistory`).
- **Označení části textu.** Kopíruje se jen celá zpráva z nabídky; vybrat úryvek
  (`SelectionContainer`) zatím nejde.
- **Příloha se nedá poslat.** Vstupní pole umí text, emoji a samolepky — chybí fotka, soubor,
  hlasovka. Viz „Doporučené pořadí" nahoře.

---

## Výběr samolepek a emoji

Hotové: záložky Nedávné, Oblíbené, Trendy a sady se swipem; hledání samolepek i emoji; oblíbené;
sada ze samolepky v chatu; emoji vkládané do textu včetně nedávných. Zbývá:

- **Dvě různé pleti u emoji s více osobami** (👫 s různým odstínem každé osoby) — nabízí se jen
  stejný odstín pro všechny.
- **Emoji novější než Unicode 15.0** — až je budou znát systémová písma podporovaných verzí.
