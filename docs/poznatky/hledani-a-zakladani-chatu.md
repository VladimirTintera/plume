# Hledání a zakládání chatů

Poznatky k jedné oblasti aplikace. Obecná pravidla projektu a rejstřík ostatních souborů jsou
v [CLAUDE.md](../../CLAUDE.md); poučení z opravy v téhle oblasti patří sem.

## Hledání chatů

`ChatSearch` (`features/chats`) — sdílí ho hlavička seznamu chatů, přeposílání i sdílení.

- **`searchChats`, ne filtr nad seznamem.** Filtr nad `chats()` nenašel archiv ani chaty mimo
  složky (kontakty bez konverzace, opuštěné skupiny, které TDLib zná). Nejdřív lokálně
  (`searchChats`, hned), pak server (`searchChatsOnServer`), serverové výsledky se připojí ZA
  lokální (`mergeChatResults`) — co uživatel už vidí, se pod prstem nepřeskládá.
- **`transformLatest` s krátkou pauzou (150 ms).** Rozepsaný dotaz se dalším znakem zahodí,
  takže pomalá odpověď serveru na „ja" nepřepíše výsledky pro „jana".
- **Výsledky se ukazují jen k dotazu, na který TDLib odpovídal** (`ChatSearchState.query`).
  V rozbaleném hledání se mezitím točí indikátor; ve výběru pro přeposílání zůstávají předchozí
  výsledky, jinak by výběr při každém znaku problikl načítáním (`pickerChats`, testy
  `ChatSearchTest`).
- **Pod prázdným dotazem jsou návrhy**: nejčastější lidé (`getTopChats(TopChatCategoryUsers)`)
  a historie hledání. Historii vede TDLib — `searchChats("")` ji vrací, ťuknutí na výsledek ji
  zapisuje (`addRecentlyFoundChat`), „Vymazat" ji smaže. `searchRecentlyFoundChats` prázdný
  dotaz nebere. Návrhy se načítají, jen když je hledání rozbalené (`setSuggestionsWanted`).
- **`getTopChats` vrací chybu, když si to uživatel v Telegramu vypnul** — to je prázdný řádek,
  ne selhání.
- **Uložené zprávy TDLib najde pod jménem účtu**, takže se z výsledků vyřazují a hledají podle
  přeloženého názvu. V historii a mezi nejčastějšími je kreslí `chatTitle` / `isSavedMessages`.
- **Archivovaný výsledek má podtitul „V archivu".** V hlavním seznamu není vidět, a bez podtitulu
  by chat vypadal, že se odněkud vynořil. Složku doplní repository podle pozice
  (`summariesFor`); chat, který repository nezná, si dočte (`getChat`) a uloží.
- **Po výběru z hledání dojede seznam na vybraný chat** (`scrollToChat` v `ChatListContent`),
  jinak by na široké ploše vybraný chat vlevo nebyl vidět. Index se počítá ze skupin (kotva
  + hlavička + chaty) — `LazyListState` klíč položky mimo obrazovku na index nepřevede. Chat,
  který je celý vidět, se nehýbe. Požadavek je `rememberSaveable`: na telefonu konverzace
  seznam překryje a posun proběhne, až se na seznam uživatel vrátí.
- **V hlavním hledání jsou pod chaty ještě sekce Globálně a Zprávy** (`ChatSearch(everywhere =
  true)`; přeposílání a sdílení hledají jen chaty). Globálně je `searchPublicChats` bez chatů,
  které jsou už mezi výsledky (`globalOnly` — server vynechává jen chaty ze seznamu, ne všechny
  známé). Zprávy jsou `searchMessages(chatList = null)` přes hlavní seznam i archiv, stránkované
  `nextOffset` (prázdný = konec), duplicity na hranici stránek zahazuje `mergeFoundMessages`.
- **Filtry hledání zpráv jsou nad výsledky** (`MessageSearchFilters`, `MessageSearchFilter`):
  druh chatu (Soukromé / Skupiny / Kanály), druh obsahu (Fotky, Videa, Soubory, Odkazy,
  Hlasovky, Hudba) a období (týden, měsíc, rok, vlastní rozsah). Filtruje
  **server** (`searchMessages` s `chatTypeFilter`, `minDate`/`maxDate`, převod `dateRange`, testy
  `MessageSearchFilterTest`) — filtr nad načtenou stránkou by z padesáti výsledků nechal pár
  a zbytek dotahoval naprázdno. Druh chatu zúží lokálně i sekce chatů; s obdobím se sekce chatů
  schovají, chaty datum nemají.
- **Změna jen filtru nehledá chaty znovu** — seznam by se při každém ťuknutí na čip přeskládal;
  zopakují se jen zprávy. Filtr se nuluje se sbalením hledání, jako dotaz.
- **Druh chatu je connected button group** (`ToggleButton` se spojenými tvary, vybraný plný
  s fajfkou, jako záložky profilu); druhé ťuknutí výběr zruší — filtr smí být prázdný.
  **Období je split button**: levá část období zapne a vypne a ukazuje které (poslední vybrané
  si pamatuje), šipka otevře Expressive menu se zaškrtnutou volbou a vlastním rozsahem. „Kdykoli"
  v nabídce není — to je vypnutí levé části. Nejdřív to byly `FilterChip`y; uživatel chtěl
  Expressive komponenty.
- **Na tlačítku krátký popisek** („Týden"), plný je v nabídce, a tlačítka mají užší odsazení
  (12 dp). S „Poslední týden" a výchozím odsazením se řádek v češtině nevešel a šipka nabídky
  skončila za okrajem — ověřuje se v češtině s vybraným druhem i obdobím, to je nejširší stav.
- **Výběr dat vrací půlnoc v UTC** — den se z ní vezme v UTC a začátek hledání je jeho půlnoc
  v MÍSTNÍM pásmu; konec rozsahu je konec dne. Budoucí dny se nenabízejí.
- **Ověřovat přes `adb` opatrně**: klávesa Esc (`keyevent 111`) hledání zavře a další ťuknutí
  trefí seznam chatů — otevřený chat se tím označí jako přečtený. Klávesnice se schovává gestem
  zpět, jen když je opravdu vidět (`dumpsys input_method | grep mInputShown`).
- **Chaty a Globálně jsou sbalené na čtyři řádky** se „Zobrazit vše" — u krátkého dotazu jich
  jsou desítky a zprávy by skončily daleko dole.
- **Nadpis sekce chatů je vždycky, když jsou chaty.** Dokud se ukazoval až s dalšími sekcemi,
  vložil se nad už vykreslené chaty a `LazyColumn`, který drží pozici podle první viditelné
  položky, ho odsunul nad horní hranu — nebyl vidět nikdy. Totéž čeká každou položku vkládanou
  nad obsah, na který se uživatel už dívá.
- **TDLib hledá bez ohledu na diakritiku, zvýraznění musí taky** (`highlightRanges`
  s `foldDiacritics`, znak za znak, takže pozice sedí na původní text). „obslehl" najde
  „Obšlehl"; se zvýrazněním jen přes `ignoreCase` by nalezená zpráva neměla zvýrazněné nic.
  Tabulka diakritiky je v escape sekvencích — `verifyNoHardcodedText` hlídá diakritiku
  v literálech a oslabovat ho kvůli datům nestojí za to.
- **U nalezené zprávy je, kdo ji napsal** („Jana: …", u vlastní „Vy: …";
  `FoundMessage.senderName`). Jméno jen ve SKUPINĚ — v soukromém chatu a kanálu ho říká název
  chatu; anonymní správce (odesílatel je skupina sama) jméno nemá. Dohledává se lokálně
  (`getUser` / `getChat`) při převodu stránky. Zvýraznění dotazu se posouvá o délku prefixu.
- **`adb input text` do pilulky hledání na zatíženém emulátoru ZTRÁCÍ první znaky**
  („kotlin" → „tlin"). Psát po znaku s pauzou, a před ťuknutím na výsledek ověřit dotaz dumpem.
- **Ukázka zprávy začíná kousek před nalezeným slovem** (`snippetAround`, „…" na začátku).
  Ve dvou řádcích by slovo z konce dlouhé zprávy bylo za výpustkou a řádek by nevysvětlil,
  proč se našel. Slovo jen v adrese skrytého odkazu vidět nebude — text ji nenese.
- **Vlastní emoji jsou v ukázce obrázkem**, jako v náhledu seznamu chatů (`FoundMessage.entities`
  z `previewEntities`, `snippetOf`, testy `ChatSearchTest`). Offsety se posouvají o uříznutý
  začátek a výpustku; spoiler přes začátek ukázky se zkrátí (emoji v něm zůstane skryté),
  useknuté vlastní emoji vypadne. Ověřeno sondou — bot vlastní emoji poslat nesmí (viz
  „Vlastní emoji v textu").
- **Ťuknutí na zprávu otevře chat se skokem na ni**: `ConversationRoute(chatId, messageId)`,
  ViewModel dostane `initialMessageId` a místo skoku na nepřečtené zavolá `jumpToMessage`
  (okno kolem zprávy a zvýraznění jako u citace, viz „Okno v historii"). Druhý parametr
  Koinu se čte INDEXEM (`parameters.values.getOrNull(1)`): bývá `null` a podle typu by se spletl
  s `chatId`. **Konverzace na vrcholu zásobníku se porovnává podle `chatId`**
  (`isOpenConversation`), ne rovností routy — s `messageId` by přeposlání otevřelo tentýž chat
  podruhé.
- **Globální výsledek otevře chat, ve kterém uživatel není**: `ChatInfo.canJoin` (supergroup
  se stavem `ChatMemberStatusLeft`, `getSupergroup` je lokální) nahradí vstupní pole tlačítkem
  „Přidat se" (`joinChat`). Psát do cizí skupiny nejde a nabízet pole by znamenalo chybu po
  odeslání.
- **Na široké ploše je hledání panel pod pilulkou, ne celá obrazovka** (`ExpandedDockedSearchBarWithGap`
  podle `LocalTwoPane`). Material doporučuje docked od střední šířky; celoobrazovkové hledání by
  na rozloženém Foldu přikrylo i otevřenou konverzaci vpravo. Panel je široký jako pilulka (tedy
  levý panel), zbytek obrazovky pod závojem; výšku i místo nad klávesnicí (`imePadding`) si řeší
  komponenta sama, takže výsledky v panelu spodní inset NEBEROU (`LocalDockedResults`) — jinak by
  pod posledním výsledkem zůstala mezera o výšku klávesnice. Obsah (`SearchResults`) je pro obě
  podoby jeden.
- **Zpět v panelu napřed schová klávesnici, teprve další zpět zavře hledání** (`PopupBackAfterKeyboard`
  v `core/ui/PopupBack.kt`), jako na telefonu. Panel je popup a dvě věci to rozbíjely:
  - **Popup si na Androidu 13+ registruje zpět s `PRIORITY_OVERLAY`**, takže předběhne
    klávesnici, a registruje ho JEN při připojení okna — změna `dismissOnBackPress` za běhu se už
    nepromítne (přečteno ve zdroji `AndroidPopup.android.kt`). Proto `dismissOnBackPress = false`
    (`popupDismissesOnBack`) a vlastní callback přímo v okně popupu (`PopupBackHandler`,
    `view.findOnBackInvokedDispatcher()`). Vnitřní `BackHandler` komponenty i `NavigationBackHandler`
    jdou přes dispatcher aktivity a zpět z okna popupu k nim nedojde — bez vlastního callbacku
    druhé zpět nedělalo nic.
  - **Callback je zapnutý pořád a rozhoduje až při vyvolání** (klávesnice vidět → schovat, jinak
    zavřít). Nezáleží pak na tom, do kterého okna si klávesnice registruje vlastní zpět, ani kdy
    během gesta zhasne `isImeVisible`. Klávesa zpět jde do klávesnice sama.
  - **Ověřuje se gestem v horní půlce obrazovky.** `input swipe` od levého okraje ve výšce těsně
    nad klávesnicí (y = 1100 na rozloženém Foldu) systém jako gesto zpět nevzal — v logu nebyl
    `startBackNavigation` — a tah dopadl do závoje popupu jako ťuknutí, takže zavřel hledání
    i s klávesnicí; vypadalo to, že oprava nefunguje. Proč zrovna tam, zjištěné není. Ve výšce
    700 gesto prošlo. Jestli zpět opravdu proběhlo, říká `adb logcat | grep
    startBackNavigation` (`CoreBackPreview`).
- **Spodní inset si berou seznamy výsledků, ne `ExpandedFullScreenSearchBar`.** Jeho výchozí
  `windowInsets` je `safeDrawing` a spodek dává obsahu jako padding, takže výsledky končily
  useknuté nad navigační lištou na holé ploše. Hledání dostane jen `Top + Horizontal`, seznamy
  spodek (lišta i klávesnice) v `contentPadding` (`resultsPadding`) — obsah jede pod lištu
  a poslední položka se nad ni doroluje. Tentýž vzor jako u seznamu chatů a konverzace.
- **Prázdný výsledek je `IntroEmptyState`** („Nic nenalezeno", ikona `search_off`), stejný
  jako placeholder detailu a prázdný archiv. Plochu nekreslí (`containerColor = Transparent`,
  hledání má vlastní barvu) a spodek má nad klávesnicí (`resultsPadding`), jinak by se
  centroval pod ni. **Jednou ukázaný vydrží i během hledání dalšího znaku** (`emptyShown`) —
  kdyby ho mezitím vystřídal indikátor, pouštěla by se úvodní animace při každém znaku.
- **Selhání hledání se nehlásí.** Prázdný výsledek je u hledání normální stav a lokální výsledky
  zůstanou, i když server neodpoví.
- **Dotaz se maže při KAŽDÉM sbalení** (efekt nad `expanded`), ne v obsluze šipky. Gesto zpět
  zavírá dialog hledání samo a obsluhu šipky obchází.
- **Ověřování snímky: `sips -c` ořezává ze STŘEDU**, `--cropOffset` ignoruje. Výřez „horní
  části" byl ve skutečnosti prostředek obrazovky a vypadalo to, že bar chybí.

### Hledání přirozeným jazykem

V rozbaleném hledání je u dotazu o aspoň třech slovech ikona „Hledat s AI" (jen se zapnutou
AI v zařízení a PŘIPRAVENÝM modelem, `NaturalSearchModel`). Ťuknutí převede větu („fotky
z kanálu za poslední měsíc", „smlouva v souborech z loňského roku") na slova k hledání
a filtry nad výsledky — chybu je vidět hned v čipech a opraví se ťuknutím.

- **Pravidla tam, kde jsou si jistá, model na zbytek** (`refineNaturalSearch`, testy
  `NaturalSearchTest` na skutečných odpovědích z emulátoru). Gemma 4 E2B sama nestačila:
  do slov k hledání dala celou větu, nebo jen slovo filtru („souborech"), soubory ani
  „smlouvu" nepoznala a „poslední měsíc" spočítala jako rok — i se slovníkem v promptu.
  Druh obsahu a chatu i běžná období („poslední týden / měsíc / rok", „loni", „letos",
  „včera", „dnes") proto rozhodují slova věty; volnější formulace („loni v létě") zůstávají
  na datech od modelu.
- **Slova k hledání jen z věty.** TDLib hledá podle začátku slova, takže model, který
  „dovolené" převede na „dovolená", by nenašel nic. Od modelu se berou jen slova, která ve
  větě stojí; když žádná nedal, vezmou se slova věty bez slov filtru a období a bez předložek
  na krajích („recept na guláš ze [skupin]").
- **Filtr druhu obsahu** (`MessageSearchFilter.content`, `SearchMessagesFilterPhoto` …
  v `searchMessages`) je split button jako období (`ContentFilterButton`): levá část zapne
  a vypne poslední vybraný (výchozí Fotky), šipka otevře nabídku. Hledání větou rozsvítí
  tentýž prvek. Stojí mezi druhem chatu a obdobím — oba první zužují „co", období „kdy".
  S filtrem obsahu se sekce chatů schovají, jako s obdobím. Ověřeno na emulátoru: „drama"
  + Videa ukáže jen videa z kanálů, vypnutí vrátí chaty.
- **Prázdný dotaz s druhem obsahu je hledání zpráv, ne návrhy** („fotky z kanálu za poslední
  měsíc" nemá téma). TDLib `searchMessages` s prázdným dotazem a filtrem obsahu vrací zprávy
  (ověřeno: fotky z kanálu). Chaty se v tom případě nehledají — bez slova není co najít.
- **Výsledek modelu se zahodí, když se dotaz mezitím změnil**, a nic nevyčtené (`null`)
  hlásí jen haptika `reject()`.
- **Ověřeno na emulátoru** (Gemma 4 E2B, CPU, ~10 s): „fotky z kanalu za posledni mesic" →
  prázdný dotaz, Fotky, Kanály, Měsíc, výsledky fotky z kanálů; „recept na gulas ze skupin" →
  „recept na gulas", Skupiny; „smlouva v souborech z lonskeho roku" → „smlouva", Soubory,
  loňský rok. Dotazy bez diakritiky (adb háčky neumí). **Gemini Nano neověřené.**

## Nová konverzace a kontakty

`NewChatScreen` (`features/chats`), `NewChatRoute`, vstup FAB s tužkou v seznamu chatů.

- **Je to tentýž `ChatPicker` jako přeposílání a sdílení** (viz „Jeden výběr chatu"). Vlastní
  seznam tu byl a **při přestavbě výběru se na něj zapomnělo**: zůstal u `TextField`
  s podtržením, holých `ListItem`ů na pozadí, `shapes.large` u karty Uložených zpráv, holého
  textu místo prázdného stavu a bez spodního insetu, zatímco přeposílání už mluvilo jazykem
  seznamu chatů. Dva vzhledy pro „seznam chatů k výběru" znamenaly, že uživatel poznává výběr
  na každé obrazovce znovu.
- **ViewModely zůstávají dva.** `NewChatViewModel` načítá kontakty, `ForwardViewModel` řeší
  volby a odesílání — sdílí se vzhled, ne obsah.
- **Vstup je FAB menu** v seznamu chatů (viz „Složky místo filtrů a FAB"): Nová zpráva, Nová
  skupina a Nový kanál. Skupina a kanál jsou i řádky nahoře na téhle obrazovce.
- **Kontakty jdou z `getContacts`** a pro každý se lokálně zavolá `createPrivateChat(userId, force = false)`
  — TDLib tak pro kontakt založí chat objekt (lokálně v paměti/databázi, bez odesílání zpráv)
  a do seznamu se dostane kompletní `ChatSummary` s avatarem i dostupností (`withPresence()`).
- **Uložené zprávy jsou i tady nahoře**, jako samostatná karta v `primaryContainer`.
- **Vyhledávání kombinuje tři vrstvy:** lokální kontakty (filtrace podle jména), existující známé chaty
  z `searchChats` a veřejné Telegram uživatele / skupiny z `searchPublicChats` (podle `@username`).
- **Prázdno má dvě podoby.** Bez dotazu „Žádné kontakty" s radou hledat podle `@jména`,
  s dotazem „Nic nenalezeno" — dvě různé situace a rada dává smysl jen u té první.
- **Načítání se hlásí, jen dokud nejsou kontakty.** Jinak by hledání pod prstem vyměnilo seznam
  za kolečko při každém znaku.
- **List panel na dvoupanelovém rozvržení:** Na tabletu / TwoPane nahradí levý panel stejně jako
  Archiv, po výběru kontaktu se zavře a v pravém panelu se otevře konverzace.

## Nový kontakt podle čísla

Třetí řádek nad kontakty v „Nové zprávě" (`NewContactRoute`, `NewContactScreen`,
`NewContactViewModel`): jméno, nepovinné příjmení a číslo s výběrem země. Po přidání se otevře
konverzace a z Chatů se odebere formulář i „Nová zpráva" — zpět vede do seznamu.

- **`importContacts`, ne `searchUserByPhoneNumber` + `addContact`** (`ChatCreation.addContact`).
  Jedno volání a číslo bez účtu je ODPOVĚĎ (id 0 v `userIds`), ne chyba, kterou by šlo splést
  se spadlým spojením. Svoje číslo protějšku tím nesdílíme — to umí jen
  `addContact(sharePhoneNumber = true)`. Daň: číslo bez účtu zůstane na serveru mezi
  importovanými kontakty (jako u oficiálních klientů; `getContacts` ho nevrací).
- **„Nikoho najít nejde" je věta pod polem, ne chyba.** Server nerozliší „účet tu není"
  a „soukromí zakazuje hledání podle čísla" a my to rozlišovat nesmíme — věta jmenuje obojí.
  Haptika `reject()`, protože přišla po ťuknutí na Přidat.
- **Zadání čísla sdílí s přihlášením** (`PhoneInput` a `PhoneField` v `core/ui`, dřív
  v `features/auth`): seznam zemí a odhad podle IP, formátování od TDLib, vložené `+420…`
  rozdělené na zemi a zbytek. Dvě kopie by se rozešly; v `core/ui` je ze stejného důvodu jako
  `ChatPicker` — `features/chats` nemá záviset na `features/auth`.
- **Pod polem je, kam kontakt jde**: do účtu Telegramu, ne do telefonu, a protějšek naše číslo
  neuvidí. U „přidat kontakt" se to jinak čeká od telefonu. Synchronizace adresáře z telefonu
  (jako oficiální aplikace) vědomě NENÍ — oprávnění ke kontaktům a nahrávání adresáře na server
  je samostatné rozhodnutí; prázdný seznam proto radí „přidej nový kontakt podle čísla", ne
  „přidej kontakt v telefonu".
- **Ověřeno na Pixelu 7 Pro** (česky, testovací účet …029): servisní číslo +420 111 111 111
  ukázalo větu o nenalezeném čísle (a `performHapticFeedback(constant=17)`), druhé testovací
  číslo …527 se přidalo jako „Test", otevřel se prázdný chat, zpět vedlo do seznamu a kontakt
  je v Kontaktech „Nové zprávy". **Anglicky ověřeno na Pixelu 5** (systém v angličtině,
  12. 10. 2026): formulář, nápověda pod polem i věta o nenalezeném čísle se vejdou a čtou se
  dobře; tamtéž po přesunu `PhoneInput` do `core/ui` přihlašovací obrazovka — země z odhadu
  podle IP, formátování čísla, odeslání a přihlášení kódem. **Neověřeno**: iOS (jen
  přeložené), číslo se zakázaným hledáním (chce cizí účet).

## Můj QR kód a odkazy zvenčí

Rychlá výměna kontaktu mezi dvěma lidmi vedle sebe: jeden ukáže „Můj QR kód", druhý ho
naskenuje a otevře se mu chat. Sheet `MyQrCodeSheet` (`MyQrCodeViewModel`, `features/settings`)
se otevírá z menu účtu v seznamu chatů a z ikony na kartě účtu v Nastavení → Obecné; čtečku
nabízí sheet („Naskenovat kód druhého") a řádek „Naskenovat QR kód" v „Nové zprávě".

- **Odkaz vyrábí TDLib (`getUserLink`, `AccountRepository.contactLink`).** S veřejným jménem
  vrátí `t.me/jméno` s `expires_in = 0`, bez něj dočasný `t.me/contact/<token>`. Token najde
  účet i se skrytým číslem, proto platí jen chvíli. **Kód se obnovuje sám minutu před koncem,
  ale jen dokud je sheet otevřený** (`keepFresh` v efektu sheetu, ne v `init`): ViewModel žije
  s obrazovkou pod sheetem a nový token na pozadí by nikomu neposloužil.
- **Kód je `https://t.me/…`, ne `tg://`.** Přečte ho fotoaparát, oficiální klient i prohlížeč
  (stránka `t.me` nabídne „otevřít v Telegramu" přes `tg://`) — tedy i člověk bez Plume.
- **Prošlý token má vlastní větu** (`ContactLinkExpiredException`, „kód už neplatí, požádejte
  o nový"). Dřív padal do `LinkUserNotFoundException`, jejíž věta mluví o skrytém čísle —
  u kódu, který někdo ukázal před hodinou, by mátla.
- **Čtečka je Google code scanner** (`rememberQrScanner`, bez oprávnění ke kameře). Přečtený text
  projde hrubým sítem `isTelegramLink` (testy `TelegramLinkTest`): QR kód Wi-Fi, vizitka nebo
  cizí web dostanou „Tohle není kód Telegramu", ne obecné „odkaz nejde otevřít". Co odkaz
  znamená, rozhoduje až TDLib. Na desktopu kamera není (`qrScannerSupported = false`), tlačítko
  ani řádek tam nejsou.
- **„Zkopírováno" nese tlačítko** (fajfka na dvě vteřiny), ne snackbar — stav ovládacího prvku
  uvnitř sheetu, viz „Hláška Zkopírováno" v CLAUDE.md.

### Odkazy z jiných aplikací (`LinkOpenRequests`)

Naskenovaný kód i `t.me/…` / `tg://…` z jiné aplikace jde do `LinkOpenRequests` (stav, ne
událost — jako `ChatOpenRequests`) a otevírá ho shell: `ExternalLinkHost` nad `NavDisplay`
s `ExternalLinkViewModel`. Je to `ConversationViewModel.openLink` bez konverzace kolem; sheety,
které jinak kreslí konverzace (pozvánka, sdílená složka, sada samolepek, proxy, varování před
přihlašovacím odkazem), kreslí host sám, i s vlastním snackbarem.

- **Chat se otevře nad srovnaným zásobníkem** (`AppNavigation.openOverChats`), jako
  z notifikace: odkaz zvenčí s tím, co bylo otevřené, nesouvisí. Pod téma a mini aplikaci se
  vloží seznam témat / chat s botem, aby zpět vedlo tam (`openExternal`).
- **Bot se z odkazu zvenčí nespouští sám**, i když odkaz nese `autostart` — chat nabídne Start
  (`BotStartRequests`). Odkaz mohl poslat kdokoli a uživatel ho v Plume ještě neviděl.
- **Neznámý webový odkaz jde VÝSLOVNĚ do prohlížeče** (`UrlOpener.openInBrowser`, na Androidu
  selektor `CATEGORY_APP_BROWSER`). Obyčejný `ACTION_VIEW` i karta Custom Tabs by adresu `t.me`
  mohly vrátit zase Plume.
- **Manifest přijímá `tg://` a `http(s)://t.me` (i `*.t.me`, `telegram.me`, `telegram.dog`)
  BEZ `autoVerify`.** Doménu `t.me` ověřit nejde — `assetlinks.json` na ní vydává Telegram.
  Od Androidu 12 proto neověřený webový odkaz otevírá prohlížeč, dokud uživatel Plume odkazy
  `t.me` nepovolí v systému („Otevírat ve výchozím nastavení"). Řádek „Otevírat odkazy Telegramu
  v Plume" v Nastavení → Obecné (`rememberTelegramLinkHandling`) říká stav
  (`DomainVerificationManager`, čte se při každém návratu) a vede tam. Do Androidu 11 systém
  nabídne výběr aplikací sám, `tg://` nepotřebuje nic. S nainstalovaným oficiálním klientem,
  který doménu ověřenou má, jde `t.me` z fotoaparátu do něj — Plume dostane `tg://` z jeho
  výběru, nebo kód naskenovaný ve vlastní čtečce.
- **Odkaz se z intentu po předání maže** (`MainActivity.receiveLink`), jinak by ho otočení
  displeje otevřelo podruhé; studený start ho čte jen bez `savedInstanceState`.
- **Kontaktní odkaz otevře CHAT, ne profil** (beze změny proti odkazu ve zprávě). Přidat do
  kontaktů jde z pruhu nad konverzací, pokud ho server pošle (`ChatActionBarAddContact`), nebo
  z ⋮ profilu.
- **iOS odkazy zvenčí nepřijímá** (chybí `CFBundleURLTypes` pro `tg` a asociované domény);
  QR kód, sdílení i čtečka tam fungují, jsou ve společném kódu.
- **NEOVĚŘENO na zařízení ani emulátoru** — přeložené a otestované jen jednotkovými testy.
  Ověřit: kód z menu účtu a z Nastavení (světlý i tmavý motiv), obnova prošlého tokenu,
  naskenování kódu druhým telefonem (čtečkou v Plume, fotoaparátem s povolenými odkazy i bez nich
  přes stránku `t.me`), řádek v Nastavení po přepnutí v systému, prošlý kód (věta), Wi-Fi kód
  (věta), `adb shell am start -a android.intent.action.VIEW -d "tg://resolve?domain=…"`
  a `-d "https://t.me/…"` za běhu i za studena, otočení displeje po otevření odkazu.

## Založení skupiny a kanálu

„Nová skupina" a „Nový kanál" jsou dva řádky nad kontakty v „Nové zprávě" (schované při
hledání). Skupina: výběr členů (`NewGroupMembersRoute`, `NewGroupMembersScreen`) → název
a fotka (`CreateChatRoute`, `CreateChatScreen`); kanál rovnou formulář s popisem. Hranice je
`ChatCreation` v `core/telegram`, po založení se otevře nový chat.

- **Vždycky supergroupa** (`createNewSupergroupChat`), i pro malou skupinu. Základní skupina
  nemá témata, část práv ani veřejný odkaz a při prvním takovém kroku se převádí — na chat
  s JINÝM id, které by rozsypalo zásobník i koncepty. Skupina a kanál tak jdou jedním voláním.
- **`ChatCreation` je samostatné rozhraní, ne metoda `ChatRepository`** — ten má v testech
  desítky podvrhů a založení s jeho seznamem nesouvisí.
- **Výchozí dobu automatického mazání posílá klient** (`messageAutoDeleteTime`); TDLib ji
  u supergroupy sám nepoužije. Bere jen celé dny do roku a jinou hodnotu by odmítl i se
  založením (`newChatAutoDelete`, testy `CreateChatTest`).
- **Členové se přidávají až do STOJÍCÍ skupiny** (`addChatMembers`). Koho server nepustí
  (soukromí, `FailedToAddMembers`), ohlásí dialog jménem — skupina se kvůli tomu nezahazuje
  a otevře se po zavření dialogu. Totéž selhání fotky: chat už stojí, zakládat ho znovu by
  vyrobilo druhý.
- **Výběr dává chaty, do skupiny jdou uživatelé.** Id uživatele se bere z typu chatu
  (`ChatTypePrivate`), ne z id chatu — tajný chat má id jiné. Nabízejí se jen soukromé chaty
  bez Uložených zpráv (`groupMemberCandidates`).
- **Pokračovat jde i bez členů** (`ChatPicker(confirmWhenEmpty = true)`): skupina smí vzniknout
  prázdná a pozvat se dá odkazem.
- **Soukromý, nebo veřejný** (spojená přepínací tlačítka pod názvem, výchozí soukromý — veřejný
  chat najde kdokoli a to má být vědomá volba). Veřejný chce odkaz `t.me/…`, kontrolovaný jako
  vlastní uživatelské jméno: místní pravidla hned, dostupnost po pauze (`UsernameStatus`
  a `usernameMessage` se proto přestěhovaly z úpravy profilu do `core/ui/UsernameMessage.kt`).
  **Zakládaný chat se ptá `checkChatUsername(chatId = 0)`** — tak to TDLib pro chat, který ještě
  neexistuje, chce. Strop veřejných chatů (`PublicChatsTooMany`) má vlastní větu.
- **Veřejné jméno se nastavuje až stojící supergroupě** (`setSupergroupUsername`),
  `createNewSupergroupChat` ho nebere. Když to selže (jméno mezitím někdo vzal), chat zůstane
  soukromý a řekne to dialog po založení — zakládat znovu by vyrobilo druhý chat.
- **Komu server nedovolil přidání, tomu jde po založení poslat pozvánku** — tytéž věty a tatáž
  cesta (`ProfileRepository.sendInviteLink`) jako u přidávání členů. Pozvánku vlastníkovi vydá
  `profile(chatId).inviteLink`; když ji zjistit nejde, dialog jen vyjmenuje, koho přidat nešlo.
- **Fotka se ořízne hned po výběru** (`SquarePhoto`) a nahraje až s id chatu — náhled ukazuje
  přesně to, co odejde.
- **Po založení se z Chatů odebere celá větev** (nová zpráva, členové, formulář) a zpět z nového
  chatu vede do seznamu, ne do formuláře, ze kterého by šlo založit druhý.
- **Ověřeno na emulátoru se souhlasem** (česky): skupina s testovacím botem jako jediným
  členem (servisní zprávy „vytvořena" a „přibyli členové", tlačítko příkazů bota) a kanál
  s popisem (čtvercový avatar, popis v profilu); po založení se otevře chat a zpět vede do
  seznamu. Bot jde do výběru členů přes hledání — v kontaktech není. **Neověřeno**: fotka při
  založení, dialog s lidmi, které server nepustil (chce cizí účet se zakázaným přidáváním),
  a předání výchozí doby mazání. Testovací skupina a kanál na účtu zůstaly.
  Veřejný kanál ověřený na emulátoru: obsazené jméno („durov") i chybný formát hlásí pole,
  volné potvrdí, a založený kanál má `@plume_test_1005` v profilu i podle Bot API (`getChat`).
  **Dialog s pozvánkou jen sondou** (založení podstrčené výsledkem s testovacím botem mezi
  odmítnutými a selhaným odkazem, necommituje se): obě věty, „Poslat pozvánku" a bot dostal
  pozvánku jako odkaz s entitou `url`. Skutečný člověk, který přidávání zakazuje, ověřený není.
  Pozvánka je i u veřejného chatu odkaz `t.me/+…` (`profile.inviteLink`), ne `t.me/jméno` —
  funguje stejně.

## Jeden výběr chatu: `ChatPicker`

Přeposílání, sdílení ze systému i zahájení nové konverzace kreslí tentýž seznam
(`core/ui/ChatPicker.kt`): `SearchPill`, karta Uložených zpráv a bloky zaoblených řádků.

- **Bydlí v `core/ui`, ne v `features/forward`.** Tam vznikl a sdílení si ho odtud importovalo;
  nová konverzace (`features/chats`) by tím na balíčku přeposílání závisela. Stejná úvaha jako
  u `GroupedList.kt` — mluví jím víc obrazovek než jedna.
- **Obsah dávají SEKCE** (`PickerSection`): volitelný nadpis a chaty pod ním jako jeden blok.
  Přeposílání a sdílení dávají jednu sekci bez nadpisu, nová konverzace tři (Kontakty / Chaty /
  Globálně). Prázdné sekce se zahazují, jinak by po nich zbyl nadpis nad ničím — totéž pravidlo
  jako u skupin v seznamu chatů.
- **Klíč položky nese i klíč sekce.** Tentýž chat může být ve dvou sekcích a dva stejné klíče
  `LazyColumn` shodí.
- **Mezeru před blokem nese jeho první položka** (nadpis, nebo první řádek), ne
  `verticalArrangement` — seznam o sousedech položky nic neví. Nad prvním blokem se nepřidává,
  odstup od hlavičky dává `contentPadding`.
- **Dostupnost pod jménem je volba obrazovky** (`showPresence`), ne vlastnost seznamu. Patří
  tam, kde se vybírá ČLOVĚK; u přeposílání by „naposledy před hodinou" o cíli nic neříkalo
  a řádky by kvůli tomu byly vyšší.
- **Podtitul má vlastní barvu popředí** (`mutedContentColor`). `onSurfaceVariant` je počítaná na
  `surface` role, takže na vybraném `secondaryContainer` i na kartě Uložených zpráv patří jejich
  vlastní `on*` — tatáž past jako u času a ikon u vybraného chatu v seznamu. Zelená u „online"
  navíc projde přes `accentOn`: `primary` na tónované kartě je v paletě se zvýšeným kontrastem
  k nerozeznání.
- **Titulek se šipkou při scrollu odjede** (`enterAlwaysScrollBehavior`), jako hlavička seznamu
  chatů. Přečte se jednou a pak jen ukusuje ze seznamu; nahoru se vrátí na první pohyb.
- **Pilulka hledání s ním NEODJÍŽDÍ**, i když v seznamu chatů ano. Tam je pilulka sám ten bar
  a hlavní činnost je čtení seznamu; tady je hledání to hlavní — chatů jsou stovky a ten
  hledaný v první obrazovce většinou není. Zmizelé pole přesně ve chvíli, kdy ho uživatel
  potřebuje, je gesto navíc za nic.
- **Obsah hlavičky (`header`) je první POLOŽKA seznamu**, ne sourozenec nad ním. Volby
  přeposlání a karta „co se sdílí" jsou obsah úlohy, ne chrome, takže mají se scrollem odjet —
  a jako položka seznamu to dělají samy, bez další mechaniky. V načítání a v prázdném stavu
  žádný seznam není, takže se kreslí nad ním; vypadá to stejně.
- **Mezeru pod sebou nese hlavička, ne seznam.** Přeposlání bez voleb (server zakázal kopii)
  a sdílení bez textu nekreslí nic, a mezera po ničem by vypadala jako chyba.
- **Obsah se drží pod stavovou lištou** (`max(padding.top, statusBars.top)`). Odjíždějící bar
  smrskne horní padding od Scaffoldu až na nulu, takže by pilulka skončila pod hodinami.
  Seznam začíná až pod pilulkou, takže pod lištu nic nejede a **závoj jako v seznamu chatů tu
  potřeba není** — tam pod ni chaty jezdí, protože tam odjíždí i pilulka.
- **Sbalený bar NEMĚNÍ barvu** (`scrolledContainerColor = surface`). Výchozí `surfaceContainer`
  nechal pod stavovou lištou tónovaný pruh přes celou šířku — přesně to, kvůli čemu z hlavičky
  seznamu chatů zmizel velký app bar.
