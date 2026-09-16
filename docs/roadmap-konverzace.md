# Konverzace — co dodělat

Živý dokument. Nové nápady sem, hotové věci ven (a poučení do `CLAUDE.md`).
Pořadí je doporučení, ne závazek.

## Stav dnes

Konverzace umí: historii se stránkováním, živé zprávy, odesílání s letem bubliny, reakce
(bottom sheet), odpovědi, fotky a samolepky v bublině, servisní zprávy, sponzorované zprávy,
stav doručení, dostupnost protějšku v hlavičce.

Gesta nad zprávou jsou zatím jen dvě: dlouhý stisk (= výběr reakce) a ťuknutí, které nedělá nic.

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

V nabídce zatím: **Odpovědět, Kopírovat, Smazat**. Mrtvé položky se tam nedávají, takže zbytek
přibude s tím, co bude umět:

- **Přeposlat** — potřebuje výběr cílového chatu, tedy vlastní obrazovku.
- **Upravit** — `canBeEdited` z `MessageProperties` už máme k dispozici; chybí režim editace
  ve vstupním poli.
- **Připnout** (`canBePinned`) — a k tomu pruh s připnutou zprávou v hlavičce.
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
- **Náhledy odkazů** (`MessageText.linkPreview`: titulek, popis, obrázek). Tohle byl původní
  důvod, proč entity udělat dřív.
- **Vlastní emoji** (`TextEntityTypeCustomEmoji`) jsou samolepky uvnitř textu; patří k médiím.

## D) Otevření média — HOTOVO pro fotky

Ťuknutí na fotku ji otevře přes celou obrazovku; štípnutí a dvojité ťuknutí přibližují, tah dolů
zavírá. Poučení je v `CLAUDE.md`, sekce „Prohlížeč médií". Kód: `features/media/`,
`MediaRoute` v `Routes.kt`, `fullFileId` v `MessageMedia.Photo`.

Co zbývá:

- **Ostatní typy médií.** Video, GIF, hlasovka a dokumenty se pořád jen popisují. Prohlížeč
  u nich zatím řekne, že je neumíme otevřít. Přehrávání je samostatná fáze (potřebuje platformní
  přehrávač přes `expect`/`actual`); u dokumentů stačí míň — stáhnout a předat systému.
- **Listování mezi fotkami v chatu** (`searchChatMessages` + `SearchMessagesFilterPhoto`).
  Teď se otevře jedna a ven se musí zpět.
- **Uložení a sdílení** fotky ven z aplikace.
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
- **„Píše…" v hlavičce konverzace.** `ChatActivity` se v seznamu chatů zobrazuje, v konverzaci
  ne, i když je to přesně místo, kde na odpověď někdo čeká. (Repository konverzace ho zatím
  nemá — vede přes `ChatRepository`.)
- **Vlastní `sendChatAction` při psaní.** Protějšek o nás nevidí, že píšeme.
- **Profilovka odesílatele ve skupině.** Bubliny nesou jméno, ale žádnou tvář.
- **Označení textu / kopírování.** Bez [B] a bez `SelectionContainer` se z konverzace nedá
  dostat text ven.
- **Připnutá zpráva** nemá v hlavičce pruh.
- **Příloha se nedá poslat.** Vstupní pole umí jen text — chybí fotka, soubor, hlasovka, emoji.
- **Rozepsaná zpráva se neukládá do TDLib** (`setChatDraftMessage`), takže ji neuvidí seznam
  chatů ani druhé zařízení.
- **Editace a mazání vlastní zprávy** — obojí čeká na nabídku z [B].

---

## Doporučené pořadí

1. ~~**[C] entity a odkazy**~~ — hotovo.
2. ~~**[B] popup + [A] swipe**~~ — hotovo.
3. ~~**[D] prohlížeč médií**~~ — hotovo pro fotky.

Do každého kroku se vejde i kus zásobníku: datové předěly a profilovky ve skupině se hodí
udělat, až budeme stejně v `MessageBubble`; opravy indexů při hlášení přečtení hned, protože
jsou to tři řádky.
