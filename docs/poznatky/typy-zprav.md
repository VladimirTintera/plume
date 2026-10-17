# Typy zpráv

Poznatky k jedné oblasti aplikace. Obecná pravidla projektu a rejstřík ostatních souborů jsou
v [CLAUDE.md](../../CLAUDE.md); poučení z opravy v téhle oblasti patří sem.

## Typy zpráv

TDLib má přes sto podtypů `MessageContent`. Vykreslit umíme hrstku; zbytek musí mít aspoň
pravdivý popis, protože jinak se uživateli tváří jako chyba načítání.

- **Nikdy nepoužívej jedno generické „Zpráva".** Tak to bylo a splývaly pod tím anketa, poloha,
  systémové oznámení i samotné emoji — a hlavně to vypadalo, že se zpráva nenačetla. Poslední
  záchrana je „Tenhle typ zprávy zatím neumíme zobrazit", což říká pravdu.
- **Servisní zprávy poznává `serviceText()`, která zároveň dává jejich text.** Jedno místo
  rozhoduje o textu i o způsobu vykreslení, takže se ty dva nemůžou rozejít. `null` znamená
  „tohle je běžný obsah". Vrací `UiText` a není `suspend`, takže se testuje bez prostředků
  (`ServiceTextTest`).
- **Servisní zpráva se kreslí na střed, ne do bubliny.** Bublina patří odesílateli a tyhle
  odesílatele nemají — jsou to oznámení o dění v chatu. Čas se u nich nevypisuje, reakce ani stav
  doručení k nim nepatří.
- **Zprávy o členech mají jména** („Pixel 5 opustil(a) skupinu", „Přidal(a) jsi Alici a Boba",
  „Tvoje žádost o vstup byla schválena"; `memberServiceText`, testy `MemberServiceTextTest`).
  Dřív byly všechny servisní zprávy bezejmenné, aby se nedohledávala jména — a se dvěma
  skutečnými účty to vypadalo jako chyba: ve dvoučlenné skupině stálo „Ze skupiny někdo odešel".
  - **Aktér je odesílatel zprávy** (kdo přidal, odešel, připojil se); TDLib k tomu dává jen
    id dalších lidí (`memberUserIds`, `userId`). Kdo je „ty", rozhoduje vlastní id (u aktéra
    i `isOutgoing`).
  - **Jména se dohledávají jen u zpráv o členech a jen ta, která věta použije** — u hromadného
    přidání první dvě, zbytek je „a 4 další". Ostatní oznámení mají jméno jen
    aktéra (viz níž), ostatní lidi v nich nejsou.
  - **Bez jména zůstane bezejmenná věta** (`serviceText`): kdo se ze skupiny dávno ztratil
    a TDLib ho nezná, nedostane prázdné místo ani „null".
  - **Čeština rod nezná, proto „(a)"**, stejně jako oficiální klient. Předmět („přidal(a)
    Pixel 5") se neskloňuje — jméno je jen řetězec.
  - **Konverzace, seznam chatů i seznam témat** (`TdlConversationRepository` přes
    `resolveSender`, seznamy přes společné `namedServicePreview` nad `getUser` / `getChat`)
    i nalezené zprávy. **Řádek tématu u oznámení se jménem předponu „Jana:" nemá**
    (`TopicMessagePreview.showsSender`): „Jana: Jana uzavřel(a) téma" by jméno zdvojilo; bez
    dohledaného jména zůstane předpona s bezejmennou větou. Ověřeno na emulátoru (téma General
    testovací skupiny: „Notifikator změnil(a) název na …"). Notifikace mají věty bez podmětu,
    jméno nese řádek (`docs/poznatky/notifikace.md`).
  - **Ověřeno dvěma účty** (česky i anglicky): přijatá žádost, odchod, přidání a vyhození
    z pohledu správce („Přidal(a) jsi Pixel 5") i člena („Your request to join was approved",
    „You left the group"). „X tě odebral(a)" jen testem — vyhozený člen supergroup chat
    nevidí vůbec.
- **Připnutí má jméno a text připnuté zprávy** („Jana připnul(a) „Sraz v 8"", `pinServiceText`,
  testy `PinServiceTextTest`) v konverzaci i v seznamu chatů. Text se bere z lokální databáze
  (`getMessageLocally`, jako texty úkolů) — kvůli větě se na server nečeká; zpráva, která
  lokálně není, nebo médium bez popisku dostane „připnul(a) zprávu". Delší text se zkrátí
  na 40 znaků (`quotedTask`). Připnul-li zprávu chat (anonymní správce, kanál), jméno se
  nevypisuje: byl by to název téhož chatu.
- **Jméno aktéra mají i ostatní oznámení, která někdo udělal** (`actorServiceText`, testy
  `ActorServiceTextTest`): změna názvu a fotky, snímek obrazovky, témata (vytvoření,
  přejmenování, ikona, uzavření, skrytí), boost, skóre ve hře, „Jana je teď na Telegramu"
  a připojení z komunity. Aktér je odesílatel oznámení, takže se dohledává jediné jméno,
  stejně jako u připnutí. Oznámení od chatu (kanál, anonymní správce) jméno nedostane
  a zůstane u bezejmenné věty — u kanálu je to tak správně, oznámení posílá kanál sám.
  „Ty ses připojil(a) k Telegramu" nemá kdy přijít, vlastní zpráva o registraci zůstane
  bezejmenná. Ověřeno na emulátoru: bot přejmenoval testovací skupinu tam a zpět („Notifikator
  změnil(a) název na …") a starší oznámení o tématu se ukázala jako „Uzavřel(a) jsi téma".
- **Oznámení, které mluví o jiné zprávě, na ni ťuknutím skočí** (`serviceTargetMessageId`:
  připnutí, změna možnosti ankety, konec soutěže, skóre ve hře). Skok je tentýž jako u citace
  (`MessageAction.JumpTo`), takže cílová zpráva se zvlněně zvýrazní. Odkaz pod textem tu
  není, na rozdíl od převodu skupiny: věta sama říká, o co jde.
- **Údaje, které zpráva nese, patří do věty** (`ServiceDetailsTest`): délka skončeného
  videochatu v závorce (1. pád, „(90 minut)" — „trval 1 hodina" by se skloňovat muselo),
  nový název tématu, text změněné možnosti ankety, počet výherců a nerozdaných cen, počet
  boostů a důvod, proč bot smí psát. Dřív stálo „Téma upraveno" i „Soutěž skončila" bez
  jediného údaje, ačkoli je TDLib posílá.
- **Sdílení s botem je oznámení, ne bublina** (`MessageChatShared`, `MessageUsersShared`;
  `SharedWithBotText.kt`, testy `SharedWithBotTextTest`): „Sdílel(a) jsi s botem „Plume test
  pozvánek"", „… Notifikator". **Jména a název ve zprávě TDLib vyplňuje jen botům** (`SharedUser`
  i `SharedChat` „for bots only") — uživatelský účet dostane prázdné řetězce a jen id, takže se
  jména dohledávají (`resolveSender` v konverzaci, `getUser` / `getChat` v seznamu chatů).
  Bez dohledání stálo „Sdílení uživatelé" (ověřeno na emulátoru, než se doplnilo). Ťuknutí
  otevře sdílený chat (`ServiceOpen.Chat`) nebo jediného sdíleného člověka (`ServiceOpen.User`);
  u víc lidí nevede nikam. Ověřeno na emulátoru tlačítky od testovacího bota (bot, vlastní
  skupina; ťuknutí otevřelo skupinu).
- **Datum a částka ve větě jsou taky `UiText`** (`dateUiText`, `Price.asUiText`), ne hotový
  řetězec: naplánovaný videochat („Videochat naplánován na 5. října v 15:22"), platba
  („Platba 12,50 EUR za „Káva" proběhla", hvězdy plurálem) a vrácená platba. Měsíc i desetinná
  čárka patří jazyku a převedená zpráva přežije změnu jazyka. Výjimkou je čas („15:22") —
  ve 24hodinovém zápisu se mezi jazyky nemění. Platba se píše neosobně: oznámení dostává
  plátce, ale u firemních účtů to nemusí být on. Testy `ServiceVideoChatPaymentTest`.
- **Pozvaní do videochatu jsou zpráva o členech** (`memberServiceUserIds`): „Alice pozval(a)
  do videochatu Boba a Cyrila", „Alice tě pozval(a) do videochatu". Jména se dohledávají
  stejně jako u přidaných členů (první dva, zbytek „a N další").
- **Ověřeno na emulátoru Fold** (česky): bot Notifikátor připnul zprávu přes Bot API —
  v konverzaci „Notifikator připnul(a) „Sraz v 8 u kašny…"", ťuknutí doskočilo na zprávu
  a zvýraznilo ji, náhled v seznamu chatů nese tutéž větu. Téma, anketa, soutěž, boost
  a bot jen testy — testovací účet je na zařízení vyvolat neumí. Naplánovaný videochat ověřený
  na starším oznámení v testovací skupině; platba a pozvánka do videochatu jen testy.
- **`MessageSupergroupChatCreate` chodí pro skupinu I kanál** („a newly created supergroup or
  channel") a obsah druh nenese — dokud tam natvrdo stál kanál, hlásila každá nová skupina
  „Kanál „X“ vytvořen". Rozlišuje `Message.isChannelPost` (`supergroupCreatedText`, testy
  `SupergroupCreatedTest`), a proto ho `serviceText`, `describe` i `previewTextOrNull` berou
  parametrem BEZ výchozí hodnoty. Citace z jiného chatu zprávu nemá, bere ho z `origin`
  (`MessageOriginChannel`). Ověřeno sondou nad první zprávou (id `1 shl 20`) třinácti supergroup
  na emulátoru: `isChannelPost` sedělo s `ChatTypeSupergroup.isChannel` pokaždé.
- **Kanál potřebuje vlastní větu i u fotky a videochatu**, nejen u založení: „Změněna fotka
  skupiny" a „Videohovor začal" stály dřív i v kanálu. Videochat kanálu je živé vysílání (tak
  ho jmenuje i pruh nad konverzací) a ve skupině je to videochat, ne videohovor — hovor je věc
  dvou lidí. Rozhoduje totéž `isChannelPost` (testy `ServiceTextTest`).
- **`MessageChatOwnerLeft` neříká, že chat má nového vlastníka.** Vlastník jen odešel; nástupce
  (`newOwnerUserId`) chat převezme, teprve když se nevrátí, a nula znamená, že žádný není.
  Sdílená věta s `MessageChatOwnerChanged` tvrdila něco, co se možná nikdy nestane.
- **`MessageChatSetTheme` s `theme = null` je vrácení na výchozí motiv**, ne jeho změna.
- **`MessageUnsupported` znamená, že klient je starší než server.** Doslovný název typu by
  uživateli neřekl nic, takže dostane stejnou větu jako ostatní neznámé typy.
- **Notifikace „neumíme zobrazit" neříká nikdy** (`notificationText`): typ, který neumíme
  popsat, je na liště „Nová zpráva". V aplikaci ta věta stojí na místě zprávy, na liště vypadala
  jako chybová hláška místo zprávy.
- **Nové typy TDLib se objevují tiše**: TDLib 15 přidal dárky, soutěže, boosty, skupinové
  hovory i články (`MessageRichMessage`, text se bere z bloků) a všechno padalo do „neumíme
  zobrazit". Při bumpu TDLib projít seznam podtypů `MessageContent` a `PushMessageContent`
  proti `describe()` a `serviceText()`.
- **Podtypy se vyjmenují podle PŘEDKA, ne podle názvu.** `grep dto/Message` v seznamu tříd jich
  najde 180 a většina `MessageContent` vůbec není (`MessageSender`, `MessageReplyTo`,
  `MessageOrigin`…), takže se v tom skutečné mezery ztratí. Třídy se rozbalí z
  `tdl-coroutines-api.jar` a proženou `javap`; `class … extends …MessageContent` jich nechá
  105 a rozdíl proti tomu, co je vyjmenované v kódu, je pak přesný seznam nepokrytých typů:

  ```bash
  javap -cp . $(ls dev/g000sha256/tdl/dto/Message*.class | sed 's#.*/##;s#\.class##;s#^#dev.g000sha256.tdl.dto.#') \
    | grep -E "class .* extends dev\.g000sha256\.tdl\.dto\.MessageContent"
  ```
- **Neznámé SERVISNÍ oznámení vypadá hůř než neznámý obsah.** `serviceText()` o něm neví, takže
  se nekreslí jako poznámka na střed, ale jako bublina se jménem odesílatele a větou „neumíme
  zobrazit" — v kanálu to mezi dvěma videi vypadá jako rozbitý příspěvek. Přesně takhle se
  projevilo `MessageDirectMessagePriceChanged` (cena přímých zpráv kanálu, TDLib 15).
- **Co je „for bots only", se schválně nepopisuje** (`MessagePassportDataReceived`,
  `MessageWebAppDataReceived`, `MessagePaymentSuccessfulBot`). K uživatelskému účtu se to
  nedostane, takže by to byla věta psaná naslepo; kdyby přece jen dorazilo, zapíše se to do logu
  a podle logu tabulka roste.
- **Neznámý typ se zapíše do logu** (`reportUnknownContent`, `w`, jednou za typ a proces), a tím
  do kontextu hlášení v Crashlytics. Bez toho se typ ze snímku „neumíme zobrazit" nedal zjistit.
  Název se bere z `toString()` (TDLib ho má jako řetězcový literál, R8 ho nepřejmenuje), obsah
  zprávy do logu nejde. `MessageUnsupported` jde toutéž cestou — v notifikaci „Nová zpráva".
- **Když typ dostane přílohu s popiskem, `captionText()` pro něj musí vracet prázdno** — jinak
  se popisek v bublině objeví dvakrát. Platí pro video kolečko, hru a platbu.

### Článek

`MessageRichMessage` (TDLib 15) je článek ze stromu bloků — nadpisy, odstavce, seznamy,
citace, kód, tabulky, média. V bublině je to **jeden text s entitami**, které bublina už umí
(`toRichBody` v `core/telegram/RichMessageBody.kt`, testy `RichMessageBodyTest`): nadpis tučně
s volným řádkem nad sebou, odrážky, čísla a zaškrtávátka (☑/☐), citace jako pás, kód, odkazy,
zmínky, hashtagy, příkazy, spoiler, vlastní emoji, datum.

- **Text, ne vlastní sazba bloků.** Druhá sazba vedle bubliny by znamenala zdvojit, co text
  umí: hledání se zvýrazněním, kopírování, výběr textu a citace z nabídky, spoilery. Rozložený
  do textu to všechno funguje beze změny.
- **Offsety se počítají z délky `StringBuilder`u** (UTF-16, jako všude) a entita se zapisuje
  až po napsání obsahu. Proto se nic nesmí z textu MAZAT zpětně: blok za odrážkou seznamu první
  verze přitahovala smazáním `\n` a posunula tím všechny entity za ním. Teď `continueLine`
  zakáže nový řádek předem.
- **Média stojí v textu jako slovo, a bublina místo něj nakreslí médium.** Text zůstává
  „[Fotka] popisek" (kopírování, náhled, hledání, přepis pro AI), a TENTÝŽ průchod stromem zapíše
  kotvu (`ArticleMedia`: úsek slova, pořadí, `MessageMedia`). Bublina článek s médii kreslí po
  úsecích (`articleSegments`, testy `ArticleSegmentsTest`): text, fotka / video / GIF, text…
  Entity se do úseků ořežou a posunou, zvýraznění hledání se počítá nad úsekem. Popisek média je
  kurzívou — pod fotkou by jinak splynul s textem.
- **Koláž a prezentace jsou mřížka alba** (`ArticleMedia.group`, `AlbumGrid` s `AlbumCell`).
  Mřížka alba dřív brala jen zprávy (`ChatMessage` na dlaždici); teď bere dlaždice s id zprávy,
  médiem a pořadím v článku, které jde do klíče letu (`LocalArticleMediaFlight` u každé
  dlaždice). Média koláže jdou do jedné mřížky, jen když mezi nimi v textu není nic než konec
  řádku — médium koláže s vlastním popiskem mřížku přeruší, popisek by v ní neměl kde být.
  Prezentace (listování) se kreslí stejně, jako mřížka.
- **Fotky a videa článku jsou i v záložce Média v profilu** (`sharedMediaTiles`, testy
  `SharedMediaTilesTest`): článek dá dlaždici za každé médium. TDLib článek v hledání
  s `SearchMessagesFilterPhotoAndVideo` vrací (ověřeno sondou), Plume ho dřív zahodil, protože
  článek nemá `media`. Klíč dlaždice nese id i pořadí média, dlaždice otevře prohlížeč
  s `MediaRoute(fromSharedMedia = true, articleMedia = …)`.
- **Zvuk, hlasovka a soubor v článku jsou řádek jako ve zprávě** (přehrát, posunout,
  otevřít). Přehrávač i stahování souborů dřív počítaly s jedním médiem na zprávu: klíč
  přehrávače proto nese pořadí zvuku ve zprávě (`VoiceKey.part`), akce článku nesou pořadí média
  (`ToggleArticleAudio`, `SeekArticleAudio`, `OpenArticleDocument`) a stahování souboru jede
  přes `withDocument(document)` — průběh je podle `fileId`, takže na zprávě nezáleží.
  - **Skladba z článku hraje jako hlasovka, ne jako hudba na pozadí**: fronta hudby
    (`MusicQueue`) hledá sousední skladby mezi zprávami a médium článku zprávou není. Po dohrání
    zvuku z článku se nic neřetězí (`part != NO_PART`).
  - **Název souboru není popisek.** Řádek souboru ho ukazuje sám; dřív, když byl soubor jen
    slovem, se název psal jako popisek.
- **Médium článku se otevírá v prohlížeči jako příloha ankety** (`MediaRoute.articleMedia`,
  `LocalArticleMediaFlight` v klíči letu): jediná stránka bez akcí. Pod jedním id zprávy je médií
  víc, takže bez indexu v klíči by se spárovala špatná fotka.
- **Dlouhý článek posílá TDLib USEKNUTÝ** (`RichMessage.isFull = false`) a zbytek vydá až
  `getFullRichMessage`. Dokud se to neřešilo, článek o 12 700 znacích končil po 8 200 a nebylo
  poznat proč. Pod useknutým článkem je „Číst celý článek" (`MessageAction.ReadFullArticle`,
  `ConversationRepository.fullArticle`); dočtený se v repository pamatuje podle zprávy
  a `editDate`, takže další převody téže zprávy (přečtení, reakce, návrat do chatu) nevrátí
  začátek. Prohlížeč bere médium z uložené zprávy a celý článek čte, jen když v ní není.
- **Stav zaškrtávátka nese `isChecked`** — Kotlin getter booleanu, takže `javap | grep get` ho
  mine a vypadá to, že wrapper stav nemá.
- **Náhled v seznamu chatů zůstává holý text** (`plainText`, spoiler vynechá).
- **„Upravit" u vlastního článku se nabídnout může** (`composerEdit` článek od textu nerozezná)
  a server `editMessageText` odmítne; poslat článek jde jen s Premium, takže se to zatím netýká.
- **Ověřuje se botem**: `sendRichMessage` s `rich_message.markdown` (Bot API 10). Ověřeno na
  emulátoru anglicky: nadpisy, formátování, spoiler, seznamy i zaškrtávátka, citace s podpisem,
  kód, oddělovač, tabulka a klikatelné `/help`, `@BotFather`, `#vylet`. **Neověřeno**: sbalitelná
  citace, iOS (jen přeložené).
  - **Média v markdownu jsou odkaz `tg://photo?id=p1`** a soubor jde v poli `rich_message.media`
    (`{"id":"p1","media":{"type":"photo","media":"attach://f1"}}`, multipart s `f1=@…`). Obrázek
    adresou (`![](https://…)`) server odmítne (`RICH_MESSAGE_PHOTO_NO_MEDIA_FOUND`) a stažení
    z Wikimedie selhalo (`failed to get HTTP URL content`); obrázky se proto vyrábějí lokálně.
  - **Zvuk a soubory článků v záložkách Soubory a Hudba profilu nejsou — TDLib je tam nevrací**
    (ověřeno sondou: hledání s `SearchMessagesFilterDocument` / `Audio` vrátilo jen starší
    zprávy, žádný článek). U Médií je vrací, proto tam dlaždice z článků jsou.
  - **Useknutý článek vyrobí dlouhý text**: 39 odstavců po ~320 znacích (~12 700) TDLib poslal
    s `isFull = false` a 8 224 znaky.
  - **Hlasovka má v `rich_message.media` typ `voice_note`** (jako `InputMediaVoiceNote`);
    s `voice` server odpoví „type "voice" is unsupported". Hlasovka i skladba se odkazují
    `tg://audio?id=…`. M4A od `say` / `afconvert` jako hlasovka nemá délku ani waveform (0:00).
  - Ověřeno na emulátoru česky: fotka s popiskem a koláž dvou fotek na svém místě, otevření
    fotky z článku v prohlížeči (svislá fotka celá), „Číst celý článek" s kolečkem (pak 12 665
    znaků a konec článku) a dočtený článek i po odchodu z chatu, zvýraznění hledaného slova
    v úseku za fotkami. **Video a GIF v článku ověřené nejsou** (jdou toutéž cestou jako fotka).
- **`getUpdates` testovacího bota nic nevrací, když `allowed_updates` zúžilo dřívější volání**
  (`getWebhookInfo` pak ukáže `"allowed_updates":["my_chat_member"]`). Volba platí, dokud ji
  další `getUpdates` nezmění, a zprávy mezitím server zahodí — volat s
  `allowed_updates=["message","my_chat_member","callback_query"]`.

### Instant View

Odkaz, ke kterému Telegram umí Instant View, se otevře ve čtečce v aplikaci, ne v prohlížeči.
Náhled odkazu to ohlásí řádkem „⚡ Rychlé zobrazení" (`LinkPreview.instantView` =
`instantViewVersion > 0`), ťuknutí na náhled pošle `MessageAction.OpenInstantView` místo
`OpenLink`. Stránku skládá server (`getWebPageInstantView`, `InstantViewRepository`) ze stejných
bloků jako článek ve zprávě, takže se převádí týmž `toRichBody` a kreslí po úsecích
(`articleSegments`) — čtečka je `features/instantview`.

- **Čtečka se otevírá přes `InstantViewRequests`, ne parametrem konverzace.** `ConversationScreen`
  je na hraně počtu registrů (viz CLAUDE.md), takže žádost vyřídí shell (`InstantViewRoute`
  do zásobníku chatů, detailní panel), stejně jako odkazy zvenčí (`LinkOpenRequests`).
- **Vlastní sazba, ne bublina.** Bublina kreslí článek komponentami navázanými na zprávu (let
  médií podle id zprávy, rámeček); stránka žádnou zprávou není. Čtečka kreslí text jako
  `RichText`, fotky přes celou šířku čtecího sloupce (nejvýš 680 dp), video a GIF náhledem.
  **Přehrát je nejde** — prohlížeč médií stojí na zprávě; celou stránku otevře tlačítko vpravo
  nahoře v prohlížeči.
- **Ve čtečce jsou mezi bloky volné řádky** (`toRichBody(spacious = true)`). Bez nich odstavce
  splývaly (v bublině je dělí jen konec řádku, a to tam stačí). Položky seznamu, řádky tabulky
  a obsah citace zůstávají u sebe a pod mezinadpisem mezera není — nadpis patří k textu pod ním.
- **Název článku (`PageBlockTitle`) se z textu vyjme a je nadpisem čtečky** (`headlineMedium`).
  V textu byl jen tučným řádkem stejné velikosti jako odstavec.
- **Odkaz uvnitř stránky** na Telegram jde do Plume (`LinkOpenRequests`), jinam do prohlížeče.
- **404 není chyba, ale „Instant View nemá"** — náhled mohl být starší než změna stránky.
  Čtečka pak stránku otevře v prohlížeči a sama se zavře. Jiná chyba ukáže „Zkusit znovu"
  a „Otevřít v prohlížeči".
- **Useknutá stránka** (`isFull = false`) má na konci „Zbytek dočíst v prohlížeči"; plnou verzi
  TDLib na rozdíl od článku ve zprávě nevydá.
- **Zprava doleva psaná stránka** (`isRtl`) se tak i sází (`LocalLayoutDirection`).
- **Ověřuje se botem**: zpráva s odkazem na blog telegram.org (`/blog/700-million-and-premium`
  Instant View má; `/blog/telegram-10-years` přesměruje na úvodní stránku a nemá). Ověřeno na
  emulátoru česky: značka v náhledu, čtečka s nadpisem, počtem zobrazení, odstavci, citacemi,
  fotkami a náhledy videí. **Neověřeno**: RTL stránka, useknutá stránka, iOS (jen přeložené).

### Počty pod příspěvkem

Pod příspěvkem kanálu je řádek se zhlédnutími a přeposláními (`PostStatsRow`) a u příspěvku
s diskusí před nimi čip komentářů (`CommentsChip`, obojí v `PostFooter`; doména `PostStats`
v `ChatMessage.postStats`). Čísla jsou zkrácená („12 tis.", „1.2K";
`compactCount`, testy `CompactCountTest`), čtečka obrazovky dostane přesná.

- **Reakce a počty chodí JEDNÍM updatem** (`UpdateMessageInteractionInfo`), proto je z dřívějšího
  `reactionUpdates` `interactionUpdates` a nese obojí (`MessageInteractions`). Dva odběry téhož
  updatu by jen čekaly, až se rozejdou.
- **Pod zprávou, ne v ní, a na protější straně než čipy reakcí.** Ty visí přes dolní nároží na
  straně odesílatele, takže u příchozího příspěvku jsou počty vpravo. Barva patří ploše
  konverzace, jako u odznaku doručení. Když řádek reakcí přesahuje bublinu, zarovnají se počty
  k jeho konci — sloupec zprávy je široký podle něj.
- **Komentáře jen v kanálu** (`PostStats.shownIn`, test `komentareJenVKanalu`). `replyInfo` má
  i zpráva v diskusní skupině (odpovědi ve vláknu) a pod každou by stálo „0".
- **Čip a počty jsou JEDEN řádek.** Dva řádky pod čipy reakcí (počty, pod nimi čip) příspěvek
  natahovaly a mezi příspěvky vznikala díra. S čipem stojí řádek na straně zprávy, bez něj na
  protější.
- **Zaokrouhluje se DOLŮ.** 999 999 zhlédnutí není „1 mil."; příspěvek nemá ukazovat víc, než
  kolik jich má.
- **Mezera v „12 tis." je nezlomitelná** (`&#160;` v `strings.xml`), jinak by se jednotka mohla
  odtrhnout na další řádek.

### Komentáře (vlákno diskuse)

Ťuknutí na čip komentářů otevře vlákno: `getMessageThread` řekne, kde je (diskusní skupina
a kořenová zpráva), a otevře se `ConversationRoute(chatId = skupina, threadId = kořen)`.

- **Komentáře NEJSOU v kanálu.** Jsou to zprávy v připojené diskusní skupině, které odpovídají
  na kopii příspěvku (server ji tam při zveřejnění sám přepošle), nebo na jiný komentář.
- **Vlákno je obyčejná konverzace skupiny za obalem** (`ThreadConversationRepository`,
  delegace `by`). ViewModel konverzace o vláknu skoro neví: historie jde přes
  `getMessageThreadHistory`, nové zprávy se filtrují podle `ChatMessage.threadId`
  (`MessageTopicThread`), ostatní jde rovnou do skutečného repository. Druhá obrazovka pro
  komentáře by znamenala zdvojit celou konverzaci.
- **Odeslání bez citace odpovídá KOŘENI.** Zpráva bez odpovědi by skončila v hlavním chatu
  skupiny, ne pod příspěvkem. Obal to doplní u všech `send*`.
- **Kořen je nejstarší zpráva vlákna** a `getMessageThreadHistory` ho nevrací; obal ho přidá,
  když přijde prázdná stránka (začátek vlákna), a stránka „od kořene" je vždycky prázdná.
- **Citace kořene se nekreslí** (`state.threadRootId` v builderu, test
  `veVlaknuSeKorenNecituje`) — odpovídá mu skoro každý komentář.
- **Ve vlákně se vypíná, co patří chatu skupiny:** „píše…" (viděla by ho celá skupina),
  připnuté zprávy, reklamy, počty zmínek a reakcí (jsou za skupinu), shrnutí, profil
  z hlavičky a ankety (zakládá je obrazovka, která o vláknu neví).
- **Hledání a koncept patří VLÁKNU** (`searchInThread`, `saveThreadDraft` s `MessageTopicThread`;
  koncept se čte z `MessageThreadInfo.draftMessage`, `CommentThread.draft`). Bez tématu by
  hledání prošlo celou diskusní skupinu a koncept skončil v jejím hlavním chatu i v seznamu
  chatů. Koncept skupiny zůstává nedotčený; odpověď kořeni se z obnoveného konceptu zahazuje —
  je to výchozí cíl komentáře, ne citace.
- **Ověřeno na emulátoru** (FilipHorky – zprávy, bez členství, diskusní skupina psaní bez
  vstupu dovoluje): „binance" našlo ve vlákně 6 výsledků a všechny se otevřou ve vlákně; pole
  místo „Přidat se"; rozepsaný komentář se po odchodu a návratu vrátil do pole a po smazání
  zmizel. Koncept skupiny zůstal prázdný (ověřeno sondou). **Nic se neodeslalo.**
- **Nepřečtené komentáře jsou vlákna, ne skupiny**: otevření skočí na první nepřečtený
  s předělem nad ním, jako v chatu. Počet dá `getMessageThread` (`unreadMessageCount`),
  vlákno ho podstrčí do `chatInfo` místo počtu skupiny (`ThreadConversationRepository`).
- **Hranici přečtení vlákna server NEPOSÍLÁ, i když pole na ni má.**
  `MessageReplyInfo.lastReadInboxMessageId` vrátil po přečtení šesti ze třinácti komentářů
  NEJNOVĚJŠÍ komentář, zatímco `unreadMessageCount` správně klesl na sedm (ověřeno na
  emulátoru). Hranice se proto dopočítá z počtu: N-tý nejnovější cizí komentář je první
  nepřečtený (`threadReadBoundary`, testy `ThreadReadBoundaryTest`). Nula znamená nikdy
  neotevřené vlákno — nepřečtené je všechno pod kořenem a historie se kvůli tomu nečte.
- **Přečtení ve vlákně jde se zdrojem `MessageSourceMessageThreadHistory`**
  (`markThreadRead`). S `MessageSourceChatHistory` se čte chat skupiny a přečtení vlákna na
  serveru stojí. **Server ho vede jako nejvyšší přečtené id**: nahlášení nejnovějšího komentáře
  přečte celé vlákno.
- **Přečtení se hlásí až po skoku na nepřečtené** (`heldReads` v `ConversationViewModel`,
  platí pro každý chat). Konverzace se otevírá u nejnovějších zpráv a ty byly vidět déle než
  odpočet hlášení (400 ms), než se historie dotáhla — ve vlákně, kde se tahá ze serveru,
  pokaždé, takže se vlákno přečetlo celé dřív, než na něj uživatel pohlédl. Podržené hlášení
  po skoku propadne; když se neskáče, odejde.
- **Hlavička: „Komentáře" a pod tím název skupiny**, bez sdílených prvků — vlákno je jiná
  obrazovka než chat skupiny a klíč by se přetahoval s položkou v seznamu.
- **Komentovat jde i bez vstupu do skupiny, když to skupina dovolí**
  (`Supergroup.joinToSendMessages == false` → `writeAccess(nonMembersCanSend)`, testy
  `WriteAccessTest`). Nečlen pak má pole, ne „Přidat se" (`canJoin` je `false`), a platí pro něj
  výchozí práva skupiny; vyhozenému to nepomůže. Jinak vidí „Přidat se". **Odeslání ověřené se
  dvěma účty** (12. 10. 2026): odběratel kanálu, který v diskusní skupině není, komentoval
  z vlákna, komentář dorazil a skupina má dál jen vlastníka. Bot API pole
  `join_to_send_messages` u skupiny, která psaní bez vstupu dovoluje, vůbec neposílá — tak se
  pozná diskusní skupina k ověření (`getChat` na `linked_chat_id` kanálu).
- **Čip nese tváře posledních komentujících místo ikony** (`MessageReplyInfo.recentReplierIds` →
  `PostStats.commenters`, nejvýš `MAX_RECENT_COMMENTERS` = 3, jména z keše odesílatelů). Kreslí je
  tentýž `FaceStack` (`core/ui`) jako tváře hlasujících v anketě — dvě kopie hromádky by se
  rozešly hned při prvním doladění. Lem má barvu plochy konverzace (`surface`): čip je bez
  výplně, leží přímo na ní. Bez komentujících (nový příspěvek) zůstává ikona. Ověřeno na
  kanálu FilipHorky – zprávy jen prohlížením.
- **Ověřeno na emulátoru jen čtení** (kanál FilipHorky – zprávy, bez přidání se): čip, vlákno
  s kořenem nahoře, zpět do kanálu. **Odeslání komentáře ověřené NENÍ** — veřejně by ho viděli
  všichni čtenáři kanálu.

### Témata ve skupinách

Skupina s tématy (fórum, `Supergroup.isForum`) se z seznamu chatů, archivu i výsledků hledání
otevírá **seznamem témat** (`ForumTopicsRoute` / `ArchiveForumTopicsRoute`, `ForumTopicsScreen`),
téma pak jako konverzace (`ConversationRoute(topicId)`). Hranice: `ForumRepository`
(`TdlForumRepository`) a `TdlConversationRepository(forumTopicId)`; pravidla seznamu jsou čisté
funkce v `ForumTopics.kt` (testy `ForumTopicsTest`).

- **„Zobrazit jako zprávy / jako témata" je volba ÚČTU, ne zařízení** (`toggleChatViewAsTopics`,
  `Chat.viewAsTopics`, `UpdateChatViewAsTopics` → `ChatSummary.viewAsTopics`). Dřív to byla jen
  navigace (konverzace NAD seznamem témat) a příště se skupina otevřela zase tématy; teď si ji
  server pamatuje a přijde updatem i z jiného klienta. Kam ťuknutí vede, říká
  `ChatSummary.opensAsTopics` (`isForum && viewAsTopics`) — seznam chatů, archiv, hledání,
  rychlý přepínač i odkazy (`ChatRepository.opensAsTopics`). Seznam témat nabízí „Zobrazit jako
  zprávy", celá skupina otevřená jako zprávy „Zobrazit jako témata" (`ChatInfo.isForum`); obojí
  obrazovku NAHRADÍ, jako u Uložených zpráv, takže zpět vede do seznamu chatů.
- **`viewAsTopics` už v sobě fórum NESE**: TDLib ho skládá jako `!view_as_messages && is_forum
  && !is_forum_tabs` (`MessagesManager::get_dialog_view_as_topics`) a update pošle i po zapnutí
  či vypnutí témat. Fórum se záložkami (`Supergroup.hasForumTabs`, oficiální klienti ho kreslí
  jako proud zpráv se záložkami témat) má proto `viewAsTopics = false` vždycky a otevře se
  zprávami; „Zobrazit jako témata" ho převede na seznam jen pro tu chvíli. U Uložených zpráv
  TDLib touž hodnotu na server NEPOSÍLÁ (jen lokálně), takže Plume tam drží vlastní
  `AppSettings.savedMessagesAsChats` a `opensAsTopics` je jen pro fórum.
- **Navigace až po odpovědi TDLib** (`viewAsMessages(onDone)`, `viewAsTopics(onDone)`): s výměnou
  obrazovky zmizí ViewModel a jeho korutiny, a volání zrušené uprostřed by volbu nemuselo uložit.
  Čeká se jen na lokální zápis — na server ji TDLib pošle sám (`toggle_dialog_view_as_messages_on_server`).
- **Ověřeno na emulátoru** (15. 10. 2026, „Plume test soukromi" s tématy): z hledání otevřený
  seznam témat → „Zobrazit jako zprávy" → zpět a znovu z hledání otevře konverzaci → „Zobrazit
  jako témata" vrátí seznam témat. **Synchronizace s jiným zařízením ověřená NENÍ** — opírá se
  o zdroj TDLib (server ukládá `view_forum_as_messages`, druhé zařízení dostane update).

- **Zúžení na téma je PARAMETR repository, ne obal** jako u vlákna komentářů
  (`ConversationRepository.inForumTopic`). Téma se TDLib předává v každém odeslání, konceptu,
  „píše…", hledání i připnutých (`topicId = MessageTopicForum`); obal by musel přepsat přes dvacet
  metod kvůli jednomu argumentu. Historie jde přes `getForumTopicHistory`, nové zprávy se filtrují
  podle `Message.topicId`, přečtení se hlásí se zdrojem `MessageSourceForumTopicHistory` (jinak se
  přečtení tématu nepohne — tatáž past jako u vlákna), počty nepřečtených, hranice přečtení,
  koncept a „Označit vše" (`readAllForumTopic*`) jsou tématu (`getForumTopic`).
- **O tom, že je chat fórum, ví položka seznamu** (`ChatSummary.isForum`, dočítá se lokálním
  `getSupergroup` spolu s ověřením a mění ho `UpdateSupergroup`). Kdo položku seznamu nemá
  (zmínka, odkaz, globální výsledek hledání), ptá se `ChatRepository.isForum` — lokálně, TDLib
  supergroupu zná, když zná chat.
- **Seznam témat je detailový panel, ne druhý list panel.** Stojí vpravo na místě konverzace
  a téma se vrší nad něj; list panel by chtěl vlastní scénu i vlastní profil a úpravy (jako archiv).
  `openConversation` / `openForum` nahrazují konverzaci i seznam témat — výběr jiného chatu je
  výměna obojího.
- **Změna tématu nese jen id** (`topicChanges` z `UpdateForumTopic`, `UpdateForumTopicInfo`
  a nových zpráv s `MessageTopicForum`) a téma se čte znovu — stejný vzor jako připnuté zprávy.
  Téma, které ještě není načtené, se tím PŘIDÁ (nové téma, zpráva hluboko v seznamu), a dočtená
  stránka pak NEPŘEPÍŠE novější verzi z updatu (`withPage`). Téma, které se dočíst nedá, bylo
  smazané; skryté obecné téma se nekreslí (`isHidden`).
- **Čtení tématu vyrobí update, na který se téma čte — past nekonečné smyčky.** `getForumTopic`
  se ptá SERVERU a TDLib po odpovědi pošle `UpdateForumTopic`, i když se nic nezměnilo. Seznam
  na ten update téma čte znovu, takže se čtení a update honily dokola: po každém otevření
  skupiny s tématy asi 40 s, 638 dotazů na server u dvou témat (změřeno dočasným logem
  v `refresh`), pak to samo ustalo. Nebylo to vidět nijak jinak než v logu. Proto
  `changedPerTopic` (testy `ChangedPerTopicTest`): update téhož tématu jde dál, jen když se
  od posledního LIŠÍ (DTO z TDLib mají `equals`). S ním proběhnou po otevření čtyři čtení.
  **Obecně: kdo na update odpovídá dotazem, musí vědět, jestli ten dotaz sám update nevyrobí.**
- **Pořadí je `ForumTopic.order` sestupně**, připnutá témata ho mají vyšší — kromě pořadí
  připnutých mezi sebou, to `order` NENESE (viz „Správa témat").
- **Uzavřené téma = `WriteAccess.TopicClosed`** (`topicWriteAccess`, testy `TopicWriteAccessTest`):
  psát smí vlastník a správce s `canManageTopics`; důvod ze skupiny (není členem, omezený) má
  přednost. Mění se živě (`accessUpdates` odebírá i `UpdateForumTopicInfo`).
- **V tématu se neplánuje a nezakládá anketa** — naplánované zprávy patří skupině a formulář
  ankety o tématu neví (skončila by v obecném tématu). Reklamy se v tématu nekreslí.
- **Ikona tématu** (`ForumTopicIcon` v `core/ui`): vlastní emoji, jinak bublina v barvě od serveru
  s písmenem (barva písma podle jasu — na žluté by bílé zmizelo), obecné téma `#`. Tvar je
  bublina, ne kruh ani sušenka: tvary avatarů nesou druh chatu a téma není ani jeden.
- **V hlavičce tématu nic neletí** (`ConversationState.narrowed`): ikona tématu v řádku seznamu
  chatů není. Letí fotka a název skupiny ze seznamu chatů do hlavičky seznamu témat.
- **Past s vahami v řádku podruhé**: název tématu s `weight(1f, fill = false)` a `Spacer`
  s `weight(1f)` si řádek rozdělily napůl a čas visel uprostřed (viz „Ověření a štítek podvodu").
- **Ověřeno na emulátoru jen čtením** na veřejné Kotlin Community (@kotlin_lang, účet v ní není —
  místo pole „Join", na který se neťuká): seznam témat s ikonami z vlastních emoji, odesílatel
  v náhledu, stránkování, konverzace tématu (hlavička, připnuté zprávy tématu 6 proti 86 ve
  skupině), „Zobrazit jako zprávy" a zpět. **Psaní do tématu, uzavřené téma, přečtení
  a dvoupanel ověřené** v testovací skupině „Plume test pozvánek" (vlastník píše i do uzavřeného
  General). Dvoupanel na emulátoru zvětšeném jako rozložený Fold (`adb shell wm size 2208x1840`,
  pak `wm size reset`): seznam témat vpravo, téma ho nahradí, zpět vrátí seznam témat a další
  zpět placeholder.
- **Vlastní zprávy nad hranicí nepřečtených se hlásí jako zhlédnuté** (`messagesToView`, testy
  `UnreadMarksTest`). Server v tématu počítal jako nepřečtené i VLASTNÍ zprávy nad hranicí
  (servisní „přibyli členové", zpráva z jiného zařízení); cizí nad ní nebyla, nehlásilo se nic
  a odznak „2" u tématu zůstal, i když v něm uživatel četl a psal. Ověřeno dočasným logem:
  před opravou `getForumTopic` `unreadCount = 2` s hranicí pod oběma vlastními zprávami, po ní
  `unreadCount = 0` a hranice na nejnovější zprávě. Vlastní zprávu bez hranice (nic
  nepřečteného) se hlásit nemusí.
- **Domněnka, která se nepotvrdila:** že krátká konverzace po skoku na první nepřečtenou
  přečtení nenahlásí, protože se viditelné položky nezmění. Skok vloží předěl nepřečtených,
  viditelné položky se tím změní a hlášení odejde — ověřeno pokusem v krátké skupině s botem.

#### Správa témat

Seznam témat má nahoře hledání podle názvu, dole „Nové téma" a podržení řádku otevře nabídku
(připnout, ztlumit, přejmenovat, uzavřít / znovu otevřít, skrýt obecné téma, smazat). Témata ve
skupině zapíná přepínač v úpravě skupiny. Kdo co smí, rozhodují čisté funkce `topicRights`
a `topicActions` (`ForumTopics.kt`, testy `TopicManagementTest`).

- **Vlastní téma smí autor upravit, uzavřít i smazat bez práv správce** (`ForumTopicInfo.isOutgoing`
  → `ForumTopic.isOwn`); cizí jen správce s `canManageTopics`, smazat s `canDeleteMessages`.
  Připnout smí jen správce — pořadí témat patří celé skupině. Zakládá člen, když to skupina
  dovolí (`ChatPermissions.canCreateTopics`), správce s `canManageTopics` vždy.
- **Obecné téma smazat nejde, jen skrýt** — a skryté se v seznamu nekreslí (`sortedTopics`).
  Proto stránka nese obecné téma zvlášť (`ForumTopicsPage.general`, i skryté) a vrací se z ⋮
  („Zobrazit obecné téma"). **Server skryté obecné téma zároveň UZAVŘE a při vrácení ho neotevře**
  — v seznamu pak má zámek; znovu otevřít jde z nabídky.
- **Barvy nového tématu jsou jen ty, které TDLib přijme** (`TOPIC_COLORS`, šest). Přejmenování
  barvu nemění — `editForumTopic` ji měnit neumí (jen vlastní emoji, a to až s Premium).
- **Nové téma se po vytvoření rovnou otevře** (`createdTopicId`), jako u oficiálních klientů.
- **Ztlumení tématu se zapisuje celým objektem** jako ztlumení chatu (`withMuteFor`,
  `useDefaultMuteFor = false`), jinak by se přepsal zvuk a téma by jelo podle skupiny.
- **Upozornění tématu jsou tentýž sheet jako upozornění chatu** (`ChatNotificationSheet`,
  „Upozornění" v nabídce nad tématem vedle rychlého Ztlumit): doby ztlumení, náhled, připnuté,
  zmínky a „Vrátit nastavení skupiny". Zápis jde přes `NotificationSettingsRepository`
  (`setTopicMute`, `setTopicOption`, `resetTopic`), ne přes `ForumRepository` — cesta
  k `setForumTopicNotificationSettings` je jedna, i pro rychlé Ztlumit.
- **Téma má TŘI vrstvy, ne dvě**: vlastní nastavení, pak skupina, pak výchozí nastavení skupin.
  `useDefault…` u tématu znamená „podle skupiny", ne „podle rozsahu"; téma ve ztlumené skupině je
  tedy ztlumené, i když samo nic nemá. Každá volba hledá svou vrstvu ZVLÁŠŤ (`notificationPrefs`,
  testy `NotificationPrefsTest`) — sdílí ji i čtení upozornění chatu, jen bez prostřední vrstvy.
  Odběr hlídá všechny tři (`UpdateForumTopic`, `UpdateChatNotificationSettings`, rozsah).
- **Zvuk tématu v sheetu není.** Kanál konverzace je jeden na CHAT a notifikace všech témat
  skupiny jdou jím (skupina notifikací TDLib je taky jedna na chat).
- **Seznam témat ukazuje ztlumení jen VLASTNÍ** (`isTopicMuted`), ne zděděné ze skupiny — to
  seznam ukazovat nemá, ztlumená je celá skupina. Sheet naopak ukazuje ÚČINNÝ stav („Ztlumeno ·
  jako ve skupině").
- **Pořadí připnutých se mění režimem uspořádání** („Změnit pořadí připnutých" v nabídce nad
  připnutým tématem; jen s právem připínat a od dvou připnutých, `canReorderPinned`). Připnutá
  témata dostanou úchyt, tah je jen koncept a „Hotovo" ho pošle jedním voláním
  (`setPinnedForumTopics`); ✕ i zpět ho zahodí. Čtečka obrazovky má u řádku akce „Posunout
  nahoru / dolů".
  - **`order` od TDLib pořadí připnutých NENESE.** Po `setPinnedForumTopics` zůstalo u obou
    témat totéž `order` (ověřeno sondou i po čtyřech vteřinách), zatímco `getForumTopics` vracel
    témata už v novém pořadí. Seznam proto řadí připnutá podle pořadí z PRVNÍ stránky od serveru
    (`ForumTopicsPage.pinnedOrder`, `sortedTopics(pinnedOrder)`, testy `PinnedTopicsOrderTest`)
    a po uložení převezme nové pořadí sám. Nově připnuté téma (v pořadí ještě není) jde nahoru,
    kam ho dává i server. **Změnu pořadí z jiného zařízení** seznam uvidí až při dalším otevření.
  - **Tažení v `LazyColumn` je obecné** (`core/ui/DragReorder.kt`: `rememberDragReorderState`,
    `Modifier.dragHandle`, `Modifier.draggedItem`), používají ho i připnuté chaty. Tažená
    položka jede jen kreslením (`graphicsLayer`) a NEMÁ `animateItem`: animace umístění by se
    s posunem pod prstem prala. Výměnu ohlásí `tick`. Úchyt táhne hned, bez podržení — podržení
    řádku patří nabídce. Hlavička režimu a řádek s úchytem jsou taky sdílené
    (`core/ui/ReorderRow.kt`: `ReorderTopBar`, `ReorderRow`), aby témata a chaty vypadaly
    i ovládaly se stejně.
- **Hledání hledá server** (`getForumTopics` s dotazem, první stránka), takže najde i téma, které
  ještě není načtené. Rozepsaný dotaz další znak zahodí (`transformLatest`), jako hledání chatů.
- **Zapnout témata smí jen vlastník** (`EditableChat.canToggleForum`) a diskusní skupina kanálu
  je mít nemůže. Základní skupině se přepínač nabízí, zapnutí ji ale napřed převede na
  supergroupu (viz „Převod základní skupiny"). `hasForumTabs` (záložky místo seznamu) posíláme
  `false`.
- **Ověřeno na emulátoru** v testovací skupině „Plume test pozvánek" (zapnutím témat): zapnutí,
  založení tématu se zelenou ikonou a jeho otevření, připnutí, uzavření, ztlumení, přejmenování,
  hledání, skrytí a vrácení obecného tématu, smazání s potvrzením, anglicky i česky.
  **Člen ověřený se dvěma účty** (12. 10. 2026): „Nové téma" jen s právem skupiny
  „Create topics", cizí téma nabízí jen ztlumení a upozornění. **Práva k tématům jsou živá**
  (`ForumRepository.rightsUpdates` nad `chatPermissionsUpdates` a `supergroupUpdates`): dřív se
  přečetla jednou při otevření a vypnutí „Create topics" ve skupině nebo zrušení omezení se
  projevilo až po restartu aplikace — dnes do 2 s oběma směry. **Omezený člen zakládá podle
  SVÝCH práv** (`OwnMemberStatus.Restricted.canCreateTopics`), dřív nesměl nic, ani když mu
  omezení zakládání nechávalo. **Správce bez vlastnictví ověřený** (s „Spravovat témata", bez
  mazání: Připnout / Přejmenovat / Uzavřít, Smazat ne; přejmenování prošlo). **Neověřeno**:
  vypnutí témat, iOS.

#### Cesty do tématu

Notifikace, odkaz na zprávu, výsledek hledání i zmínka vedou do tématu, ne do celé skupiny.
Kam cíl míří, nese `ChatTarget` (`topicId`, `forum`) a kam ho zásobník uloží, rozhoduje čistá
funkce `openTarget` (`ChatsStack.kt`, testy `ChatsStackTest`).

- **Téma zprávy nese `Message.topicId`** (`MessageTopicForum`), odkaz `MessageLinkInfo.topicId`.
  **Holý odkaz `t.me/skupina/123` téma NENESE** — takový vydá „Informace" u zprávy
  (`getMessageLink` bez `inMessageThread`). Proto se bere i `topicId` zprávy, kterou odkaz vydá.
- **Odkaz a zmínka otevřou cíl NAD konverzací, bez seznamu témat pod ním**: zpět vede tam, kde
  uživatel na odkaz ťukl, ne do cizí skupiny. Skupina s tématy bez tématu (zmínka, `t.me/skupina`)
  otevře seznam témat — celá skupina jako zprávy je jen volba v něm.
- **Notifikace a výsledek hledání naopak seznam témat POD téma vloží** (`AppNavigation.openChat`,
  `openFoundMessage`): zásobník se tu skládá od seznamu chatů a zpět má vést tam, kam by vedl
  z tématu otevřeného ručně.
- **Téma téže skupiny není „otevřený chat"** — dřív stačila shoda `chatId` a odkaz na zprávu
  mimo téma z tématu téže skupiny nedělal nic. Otevřený je jen celý chat (bez tématu a vlákna);
  skok na místě jen tam, kde zpráva patří do právě otevřeného tématu.
- **Notifikace nese téma NEJNOVĚJŠÍ zprávy** (`ChatNotification.topicId` → `EXTRA_TOPIC_ID`).
  Skupina notifikací TDLib je jedna na chat, ne na téma, takže víc témat v jedné notifikaci otevře
  to poslední. **Z pushe téma nepřijde** (`NotificationTypeNewPushMessage` ho nemá) — dočasná
  notifikace otevře celou skupinu, dokud ji nenahradí skutečná. Staré téma se nedrží: zpráva
  z jiného tématu by jinak otevřela to předchozí.
- **Odpověď z notifikace jde do tématu** (`NotificationActions.send(topicId)` přes
  `inForumTopic`) — bez něj by skončila v obecném tématu.
- **`ChatOpenRequests` nese dvojici chat + téma** (`ChatOpenRequest`), stále jako stav.
- **„Píše…" v tématu jen z tématu** (`ChatTyping.topicId` z `UpdateChatAction.topicId`).
  Ověřené NENÍ — v tématu, kde by někdo psal, účet není.
- **iOS téma z notifikace nemá**: APNs push od Telegramu téma v `custom` nepopisuje a skutečný
  push ověřený není; ťuknutí otevře skupinu.
- **Ověřeno na emulátoru** (Kotlin Community jen čtením): holý odkaz na zprávu v tématu poslaný
  botem otevřel „Kotlin Releases" se skokem na zprávu a zpět vedl do chatu s botem; zmínka
  `@kotlin_lang` otevřela seznam témat; intent notifikace s `EXTRA_TOPIC_ID` otevřel téma,
  zpět seznam témat, zpět seznam chatů. **Skutečná notifikace z tématu a odpověď z ní ověřené
  NEJSOU** — chce to zprávu v tématu skupiny, kde účet je.

### Placená média

`MessagePaidMedia` (fotka nebo video za hvězdy, častá v kanálech) je `MessageMedia.PaidMedia`:
holé médium s rozmazaným náhledem, cenou s hvězdou uprostřed a u videa délkou v rohu
(`PaidMediaPreview` v `Attachments.kt`, převod `toPaidMedia`, testy `PaidMediaTest`).

- **Server před zaplacením vydá jen náhled** (`PaidMediaPreview`: rozměry, délka, minithumbnail),
  ne soubor. Rozmazání je tedy všechno, co je; nestahuje se nic a nic se neodkrývá.
- **Zaplacené médium je obyčejná fotka nebo video** (`PaidMediaPhoto` / `PaidMediaVideo` →
  `Photo` / `Video`), i s prohlížečem a reakcemi — týká se i prodejce, který vidí vlastní
  příspěvek odemčený. Ze ZAPLACENÝCH víc položek se ukazuje PRVNÍ: prohlížeč i reakce stojí
  na tom, že zpráva je jedna fotka (album je jinak víc zpráv), a ověřit by to nešlo.
- **Nezaplacené víc položek je mřížka** (`PaidPreviewGrid`) se stejnými řádky a výškami jako
  album (`albumRowSizes`, `ALBUM_GAP`, `ALBUM_TILE_MAX_HEIGHT`) a s JEDNOU cenou přes celou
  mřížku — kupuje se všechno naráz, cena u každé dlaždice by tvrdila, že jde koupit kus. Model
  proto nese náhledy všech položek (`PaidMedia.previews`), ne jen první s počtem; „N položek"
  zbyl jen v popisu pro čtečku. Položka, kterou TDLib nezná, je prázdná dlaždice.
- **Odemknout (koupit za hvězdy) Plume neumí**, ťuknutí to řekne snackbarem
  (`MessageAction.ExplainPaidMedia`) místo prohlížeče, který by neměl co ukázat.
- **Bot placené médium do soukromého chatu pošle** (`sendPaidMedia` se `star_count`), takže se
  to ověřuje bez kanálu. Ověřeno na emulátoru česky: fotka s popiskem (25 hvězd), mřížka tří
  fotek a mřížka 2 × 2 s videem (délka na dlaždici) a popiskem, ťuknutí s vysvětlením, popis
  pro čtečku („4 položky, Placený obsah za 40 hvězd"). **Neověřeno**: zaplacené médium
  (zaplatit nejde), iOS (jen přeložené).

### Faktura a hra

`MessageInvoice` a `MessageGame` jsou příloha `MessageMedia.Product` a v bublině karta
(`ProductCard`): obrázek přes celou šířku bubliny, název, popis a u faktury cena — hvězdy ikonou,
jiné měny částkou s kódem („12,50 EUR"), u zaplacené „Zaplaceno", u testovací „Testovací platba".
Dřív to byl řádek „Platba: …" s malým náhledem, ze kterého nebylo poznat, co a za kolik se prodává.
Seznam chatů, citace a notifikace dál píšou „Platba: …" / „Hra: …".

- **Karta na ťuknutí nereaguje.** Zaplatit a hrát jde jen tlačítkem bota pod zprávou („Pay",
  „Play"), které Telegram k faktuře i hře přikládá vždycky — a to už říká, že to Plume neumí
  (`bot_unsupported_buy` / `_game`). Obrázek do prohlížeče nevede: ten otevírá fotku ZPRÁVY.
- **Kolik desetinných míst měna má, TDLib neříká** — částka chodí v nejmenších jednotkách
  (`totalAmount`). Rozkládá ji `Price.parts` s tabulkou výjimek (`currencyExponent`: hvězdy
  `XTR` a JPY bez desetin, KWD tři; testy `PriceTest`), neznámá měna dostane dvě. Oddělovač
  patří jazyku (`decimal_number`), stejně jako u velikosti souboru.
- **Hra ukazuje fotku, ne animaci** (`Game.animation` se zatím zahazuje), a popis `description`;
  `text` hry (výsledková tabulka) ne.
- **Ověřuje se botem** fakturou v hvězdách: `sendInvoice` s `currency=XTR` nepotřebuje
  `provider_token` (jiné měny ano, takže je testovací bot poslat nemůže). Ověřeno na emulátoru
  česky: karta s obrázkem z `photo_url` i bez něj, cena „★ 1 500", tlačítko Pay vysvětlí, že
  platby Plume neumí. **Neověřeno**: jiná měna, „Zaplaceno", testovací faktura, hra (chce hru
  založenou v BotFatheru) a iOS (jen přeložené). Obrázek z `photo_url` vyšel měkký — jakou
  velikost server vydá, zjištěné není.

### Soutěž

Vyhlášení soutěže (`MessageGiveaway`) a její výsledky (`MessageGiveawayWinners`) jsou příloha
`MessageMedia.Giveaway` a v bublině karta (`GiveawayCard`): samolepka dárku (u výsledků ikona
oslavy), výhra („3 × Telegram Premium na 6 měsíců", „1 500 hvězd rozdělí se mezi 2 výherce"),
popis navíc od pořadatele, čipy kanálů, podmínky (jen noví odběratelé, země s vlajkou, kdy se
losuje) a u výsledků čipy výherců a nevyzvednuté výhry. „Podrobnosti" otevře sheet se stavem
účasti tohoto účtu (`GiveawayStatusSheet`), u výsledků „Zobrazit soutěž" skočí na vyhlášení.
Doména a převod v `core/telegram/Giveaway.kt`, testy `GiveawayTest`.

- **Stav účasti NENÍ ve zprávě** — je to dotaz na server (`getGiveawayInfo`), a proto se ptá až
  sheet, ne každá karta. Hotová soutěž má přednost před stavem účastníka: zrušená (`wasRefunded`)
  před výhrou, výhra = neprázdný `giftCode` nebo `wonStarCount > 0`.
- **Výhru Premium nese dárkový kód** a ten jde zkopírovat (potvrzení fajfkou v tlačítku — snackbar
  by zůstal pod modálním sheetem). Uplatnit ho Plume neumí, věta posílá do oficiální aplikace.
- **Jména kanálů a výherců dohledává `resolveSender`** při převodu zprávy (záporné id = chat).
  Kanál, který TDLib nezná, z karty vypadne — čip bez jména by nikam nevedl; výherce bez jména
  zůstane v datech, ale nekreslí se. Výsledky nesou jen pořádající kanál, ostatní počtem
  („a 2 další kanály").
- **Čip vede do chatu nad konverzací** (`MessageAction.OpenChat`, cesta zmínky). U výsledků
  `giveawayMessageId` míří do pořádajícího chatu; skok funguje, jen když výsledky v tomtéž chatu
  jsou — jinak by vedl na id z cizího chatu.
- **Tlačítka jsou pod sebou přes celou šířku karty.** Vedle sebe se „Zobrazit soutěž" do půlky
  karty nevešlo a zkrátilo se.
- **Bot soutěž nevyrobí** (pořádá ji jen kanál s boosty a Premium), takže se ověřuje sondou:
  v `toChatMessage` text `#soutez` / `#vysledky` od testovacího bota převést na podstrčené
  `MessageGiveaway` / `MessageGiveawayWinners` a v `giveawayStatus` vrátit stav podle textu.
  Necommituje se. Ověřeno na emulátoru česky i anglicky, světle i tmavě: obě karty, sheet
  „Soutěžíš" i výhra s kódem a kopírováním, skok se zvýrazněním, čip otevře chat. **Skutečná
  soutěž a skutečné `getGiveawayInfo` ověřené NEJSOU**, iOS jen přeložené.

### Dárek

Dárky (`MessageGift`, `MessageUpgradedGift`, `MessageRefundedUpgradedGift`, `MessageGiftedPremium`,
`MessagePremiumGiftCode`, `MessageGiftedStars`, `MessageGiveawayPrizeStars`, `MessageGiftedGrams`)
jsou příloha `MessageMedia.Gift` (doména `GiftKind` v `core/telegram/Gift.kt`, testy `GiftTest`)
a kreslí se jako karta uprostřed konverzace (`GiftCard`): samolepka dárku, co se darovalo, od koho
a komu, vzkaz a stav. Dřív z nich zbylo jen „Byl poslán dárek".

- **Zůstávají servisním oznámením**, jako u oficiálních klientů — karta je uvnitř
  `ServiceMessageItem`, ne bublina. Nemají stranu ani odesílatele, a proto nemají ani nabídku.
- **Věta oznámení říká, CO se darovalo** („Dárek: 1 500 hvězd", „Dárek: Plush Pepe #42",
  „Dárek: Telegram Premium na 3 měsíce"). Jde do seznamu chatů, citace a notifikace, kde karta
  není. Běžný dárek zůstává „Byl poslán dárek" — hvězdy jsou jeho cena, ne obsah.
- **„Od: / Pro:" s dvojtečkou**, ne „od Jany": jméno je pak v 1. pádu a nemusí se skloňovat.
  Jména dohledává tentýž `senderName` jako u soutěže; neznámé jméno řádek vynechá.
- **Sběratelský dárek má pod samolepkou kruhový přechod** z barev pozadí (`centerColor` →
  `edgeColor`, RGB bez alfy, takže `OPAQUE or`). Samolepka je MODEL (jak dárek vypadá), ne
  symbol — ten je jen vzor na pozadí. Pořadové číslo bez oddělovače tisíců („#1042").
- **Proměnit, vylepšit ani předat dárek Plume neumí** (hvězdy nemáme), karta to nenabízí. Jediná
  akce je zkopírovat dárkový kód Premium; potvrzuje se fajfkou v tlačítku a věta pod ním říká,
  že se uplatňuje v oficiální aplikaci.
- **TON chodí v nanoTON** (10⁹ na TON); `tonParts` dá celou část a nejvýš čtyři desetinná místa
  bez koncových nul, oddělovač dodá jazyk (`decimal_number`).
- **Karty mají stejnou šířku** (do 280 dp přes celou plochu), ne podle obsahu: karta „Dárek" vedle
  „Telegram Premium na 3 měsíce" vypadala jako jiný prvek.
- **`tdl-coroutines` 15.0.0 padá na dárku bez `background` — na Androidu to obchází
  instrumentace, na iOS NE.** Viz „Odpovědi TDLib, které wrapper nerozebere" (Hranice kolem
  TDLib). Ověřeno sondou přes `getAvailableGifts`: bez obcházky pád, s ní 151 dárků.
- **Bot dárek poslat neumí**, takže se ověřuje sondou: text `#darek1–8` od testovacího bota
  `toChatMessage` dočasně nahradí obsahem dárku (objekty skládané ručně, samolepky
  z `getAnimatedEmoji` / `getStickers`). Necommituje se. Animované samolepky drží Compose
  v pohybu a `uiautomator dump` pak selhává („null root node") — ověřuje se snímky.
- **Ověřeno na emulátoru sondou** (česky, světlý i tmavý motiv): všech osm druhů, kopírování kódu
  (fajfka a náhled schránky). **Skutečný dárek ověřený NENÍ**; iOS jen přeložené.

### Sdílený příběh

Příběh poslaný do chatu (`MessageStory`) je snímek se značkou „Příběh" v rohu a pod ním karta
„Příběh od …", případně „V příběhu tě zmínili" a popisek příběhu (`MessageMedia.Story`,
`StoryPreview` a `StoryDetails` v `items/StoryCard.kt`, převod `core/telegram/Story.kt`, testy
`StoryTest`). Ťuknutí otevře fotku nebo video v prohlížeči médií. Příběhy jako takové (kroužky,
přehrávání po sobě, odpovědi) aplikace nemá.

- **Zpráva nese jen odkaz** (autor a id), obsah se dočítá `getStory` při převodu zprávy:
  napřed lokálně, pak ze serveru (`TdlConversationRepository.sharedStory`). Pamatuje se pro
  instanci klienta jako efekt zprávy, i „není" — ale jen když to řekl server chybou 4xx:
  vypršelý příběh je 404, autor, který příběhy nikdy neměl, **400 `STORIES_NEVER_CREATED`**
  (ověřeno na kanálu @telegram). Výpadek spojení se nepamatuje.
- **Dotaz na server má strop 3 s.** Převod zprávy je součást načítání historie a TDLib bez
  spojení na síť čeká; jeden sdílený příběh by jinak zdržel celou konverzaci.
- **Obsah kreslí táž cesta jako příloha zprávy** (`MessageAttachment` nad vnitřní fotkou nebo
  videem): rámeček, stahování, let do prohlížeče pod klíčem zprávy. Šířka je stálá
  (`fillWidth = false`) — příběh je na výšku a přes celou šířku by z něj zbyl ořezaný pruh.
- **Prohlížeč příběh rozbalí** (`toViewerItem`): jediná stránka bez „N z M" (mezi fotkami chatu
  příběh není), popiskem je text příběhu, akce jsou akce zprávy.
- **Živé vysílání a příběh, který už není, jsou plocha na výšku s ikonou a větou**; ťuknutí na
  živé vysílání vysvětlí, že ho jde sledovat jen v oficiální aplikaci.
- **Bot příběh poslat neumí** (Bot API příběhy jen pro firemní účty), takže se ověřuje sondou:
  zprávy od testovacího bota s `#pribeh…` převod dočasně vydává za `MessageStory` — fotka
  z téže zprávy jako obsah, skutečný dotaz na kanál @telegram (bez příběhů, tedy „není")
  a podstrčené živé vysílání. Necommituje se. **Skutečný sdílený příběh ověřený NENÍ**, iOS
  jen přeložené. **Dvěma testovacími účty ho vyrobit nejde**: příběh zveřejněný jen pro vybraný
  kontakt (testovací účty mají v adresáři skutečné lidi, viditelnost se proto omezuje) oficiální
  aplikace přeposlat nenabídne — sdílet jde jen veřejný příběh.

#### Odpověď na příběh a odkaz na příběh

Zpráva, která odpovídá na příběh (`MessageReplyToStory`), má nad sebou citaci jako odpověď
z jiného chatu: náhled příběhu, jméno autora a „Příběh" (`ExternalReply` s `quote.media =
MessageMedia.Story`, `toExternalReply` v `TdlConversationRepository`). Ťuknutí na ni a odkaz
`t.me/jméno/s/N` (`InternalLink.Story`) otevřou příběh v prohlížeči médií.

- **Kreslí ji TÁŽ citace jako odpověď z jiného chatu** — `QuotedMessage` příběh uměl už kvůli
  sdílenému příběhu (náhled, „Příběh"). Nový je jen převod a cíl ťuknutí.
- **Prohlížeč otevírá příběh podle autora a id, ne podle zprávy** (`MediaRoute.storyId`,
  `MediaViewerViewModel.loadStory`, `ConversationRepository.story`): odpověď na příběh ani odkaz
  zprávu s příběhem nemají. Jediná stránka bez akcí, jako příloha ankety; popiskem je text
  příběhu, v liště autor a čas zveřejnění (`MessageMedia.Story.postedAt`).
- **Příběh, který už není, a živé vysílání prohlížeč neotevírají** — hláška
  „Příběh už není k dispozici" / vysvětlení živého vysílání. Rozhoduje se ve ViewModelu
  konverzace dřív, než se naviguje (`openStory`), prázdný prohlížeč by nic neřekl.
- **Odkaz na příběh dřív šel do prohlížeče** (TDLib ho rozpozná jako `InternalLinkTypeStory`,
  Plume ho neuměl). Autor se z odkazu najde `searchPublicChat`.
- **Past: další `when` se suspend voláním přímo v `toChatMessage` shodil aplikaci při startu.**
  Ta funkce je obří suspend funkce a ART ji po přidání větve odmítl při ověření třídy
  (`VerifyError … copy-reference … type=Conflict`) — pád hned po spuštění, ne při té zprávě.
  Výběr citace je proto samostatná funkce (`externalReply`). **Kdo do `toChatMessage` přidává
  suspend volání, ať ho vytáhne do vlastní funkce** a aplikaci po změně spustí.
- **Ověřeno sondou** (zpráva od testovacího bota s `#pribeh` převod vydával za odpověď na
  příběh kanálu nalezený `searchPublicStoriesByTag`, `#pribehpryc` za odpověď na neexistující
  příběh; necommituje se): citace s náhledem a jménem kanálu, ťuknutí otevře prohlížeč
  s fotkou příběhu, u neexistujícího hláška. **`searchPublicStoriesByTag` vrací hlavně příběhy
  LIDÍ** — sonda proto brala jen kanály (`posterChatId < 0`). Odkaz `t.me/telegram/s/1` od bota
  zůstal v aplikaci a ohlásil nedostupný příběh. **Skutečná odpověď na příběh ověřená se dvěma
  účty** (12. 10. 2026): Pixel 5 zveřejnil v oficiální aplikaci příběh jen pro „Pixel 7 Pro",
  ten na něj odpověděl z oficiální aplikace. Plume u příjemce odpovědi (autora příběhu) i u
  odesílatele kreslí citaci s náhledem a „Příběh", ťuknutí otevře prohlížeč s fotkou, autorem
  a časem zveřejnění. **U vlastního příběhu stojí v citaci jméno vlastního účtu** („VT"), ne
  „Vy" — funkční to je, jen to čte jako cizí jméno. iOS jen přeložené.

### Kontakt

`MessageContact` je příloha `MessageMedia.Contact` a v bublině karta jako řádek souboru
a hovoru: tvář, jméno, číslo a u kontaktu na Telegramu „Napsat zprávu" (soukromý chat přes
`MessageAction.OpenUser`, `ContactCard`). Seznam chatů, citace i notifikace dál píšou
„Kontakt: Jana".

- **„Napsat zprávu" jen u kontaktu, který vede JINAM** (`MessageMedia.Contact.isThisChat`).
  Vlastní kontakt v Uložených zprávách i kontakt protějšku v chatu s ním vedou do chatu, ve
  kterém karta leží, a konverzace sama sebe znovu neotevře — tlačítko svítilo a nedělalo nic.
  Pozná se to při převodu zprávy: soukromý chat má id uživatele (Uložené zprávy id vlastního účtu).
- **Tvář se dočítá lokálním `getUser`** při převodu zprávy (`contactPhotoFileId`) — TDLib
  uživatele zná, když zná zprávu, která ho sdílí. Kontakt bez Telegramu (`userId` 0) má
  iniciály ze jména.
- **Číslo dostane „+", jen když je ze samých číslic** (`contactPhone`, testy
  `ContactPhoneTest`). Místní číslo s mezerami zůstane, jak přišlo — plus by z něj udělal cizí.
- **„Kopírovat" v nabídce kopíruje jméno a číslo** (`copyableText`); text zprávy je prázdný.
- **„Přidat do kontaktů" je na kartě i v ⋮ profilu** (`MessageMedia.Contact.canAddToContacts`,
  `addSharedContact`; profil `ChatProfile.isContact`, `ChatProfileViewModel.setContact`).
  Karta ho nabízí jen u člověka na Telegramu, který v adresáři ještě není — `getUser` při převodu
  zprávy (`sharedContactUser`, vedle tváře): `isContact`, `UserTypeRegular`, ne vlastní účet.
  - **Z karty se ukládá JMÉNO A ČÍSLO Z KARTY**, ne z účtu: odesílatel ho tak pojmenoval a číslo
    může být jediné, co uživatel o tom člověku ví. Profil (stejně jako pruh proti spamu) bere
    jméno z účtu (`addToContacts`). Vlastní číslo se nesdílí (`sharePhoneNumber = false`).
  - **Přidání z karty má snackbar s „Vrátit"**, ne potvrzení — je vratné (`removeContacts`).
    Po přidání zhasnou VŠECHNY karty téhož člověka v načtené historii (`withContactAddable`,
    testy `AddContactTest`), po „Vrátit" se rozsvítí.
  - **Odebrání z kontaktů je jen v profilu a s potvrzením**: smaže jméno uložené v adresáři na
    všech zařízeních a vrátit ho jde jen ručně. Ikona `person_remove` je odvozená z
    `person_add` (plus nahrazený minusem).
  - **Server do kontaktů vezme i BOTA** (`addContact` na testovacího bota prošlo). Karta
    a profil ho přesto nenabízejí — oficiální klienti taky ne. Tudy se ale dá ověřovat bez
    zásahu do adresáře se skutečnými lidmi: sonda vydá kartu od testovacího bota za kontakt
    s id bota a profil bota za člověka; necommituje se, bota pak „Vrátit" / „Odebrat" smaže.
  - **Ověřeno na emulátoru sondou** (česky): tlačítko na kartě, chyba u kontaktu bez Telegramu,
    přidání se snackbarem a „Vrátit" (tlačítko se vrátí), v profilu přidání a odebrání
    s potvrzovacím dialogem. **S člověkem ověřené NENÍ** — mění skutečný adresář. iOS jen
    přeložené. **Snackbar je krátký** a snímek po 1,5 s ho minul; „Vrátit" se hledá dumpem.
- **Poslat kontakt jde ze sponky** („Kontakt" → `ContactPickerSheet` → `ContactConfirmDialog`
  → `sendContact`). Seznam je `ChatRepository.contacts()`, filtruje se lokálně podle jména
  (`ContactPicker.shown`, testy `ContactPickerTest`). **Odeslání se potvrzuje dialogem se jménem
  a ČÍSLEM** (`contactCard`, lokální `getUser`): odchází cizí telefonní číslo a ťuknutí vedle
  v seznamu by poslalo jiné. Kontakt, který číslo skrývá (prázdné `phoneNumber`), poslat nejde
  a řekne to hláška. Posílá se i `userId`, takže u příjemce funguje „Napsat zprávu".
  Nabízí se tam, kde poloha — TDLib kontakt řadí k „základním zprávám" jako text.
- **Nad kontakty je karta „Můj kontakt"** (`ContactPicker.me`, `primaryContainer` jako Uložené
  zprávy ve výběru chatu): vlastní číslo je to, co se posílá nejčastěji. Vlastní účet se bere
  z `savedMessagesChatId` (id Uložených zpráv je id uživatele) a jde toutéž cestou, i s dialogem
  s číslem. Při hledání se karta schová (`showsMe`) — hledá se v kontaktech, ne v sobě.
- **Sheet je jen rozbalený** (`enabledValues = Hidden, Expanded`); napůl rozbalený nechal seznam
  kontaktů pod spodním okrajem. Ověřeno odesláním do Uložených zpráv — ne botovi, cizí číslo
  nemá odcházet nikam mimo vlastní účet.
- **Ověřuje se botem** (`sendContact`): fiktivní číslo dá kartu bez tlačítka; s `user_id`
  vlastního účtu karta s fotkou a „Napsat zprávu" otevře Uložené zprávy. Zprávu s vlastním
  číslem po ověření smazat (`deleteMessage`).

### Zpráva o hovoru

`MessageCall` je příloha `MessageMedia.Call` a v bublině řádek jako u souboru: kolečko se
šipkou směru, název („Zmeškaný hovor", „Odchozí videohovor") a délka (`CallRow`). Tentýž název
nese náhled v seznamu chatů, citace i notifikace. Pravidlo je čistá funkce `callOutcome`
(`CallRecord.kt`, testy `CallOutcomeTest`).

- **Odchozí nevzatý i odmítnutý hovor je „zrušený"**, jako u oficiálních klientů; u příchozího
  se zmeškaný a odmítnutý rozlišují. Spadlé spojení je normální hovor — mluvilo se.
- **Směr zná jen zpráva, ne obsah** (`Message.isOutgoing`), proto je název v
  `previewTextOrNull`, ne v `describe()` nad `MessageContent`.
- **Chybová barva jen u zmeškaného a odmítnutého** — to jediné po uživateli něco chce. Obojí je
  příchozí, tedy na světlé bublině; kontrast se přesto hlídá `accentOn`.
- **Ťuknutí zavolá zpátky** (`MessageAction.CallBack`), stejně jako tehdy — videohovor zase
  videem, když ho protějšek přijímá; dlouhý stisk otevře nabídku nad zprávou. Kde hovory nejsou
  (iOS, desktop), ťuknutí i dlouhý stisk dostane bublina. Viz `docs/poznatky/hovory.md`,
  „Hovor z konverzace".
- **Skupinový hovor (`MessageGroupCall`, TDLib 15) je týž řádek** s vlastními názvy
  („Zmeškaný skupinový hovor", „Probíhá skupinový hovor"). Dřív bublina se slovem „Skupinový
  hovor" bez směru, zmeškání a délky. TDLib nerozlišuje zmeškaný a odmítnutý (`wasMissed` je
  obojí), takže odchozí je „zrušený" a příchozí „zmeškaný" — stejné pravidlo `callOutcome`.
  **Ťuknutí nevolá zpátky**: vytočil by se hovor ve dvou a skupinové hovory Plume neumí.
  Ověřené jen testem (`CallOutcomeTest`); skutečný skupinový hovor nikdo neviděl.
- **Ověřuje se sondou**: skutečné hovory jsou jen v chatech se skutečnými lidmi (otevření by
  jim označilo zprávy jako přečtené) a bot volat neumí. Dočasně v `messageMedia` převést text
  `#call:<CallOutcome>:video:<sekundy>` na `MessageMedia.Call` (a v `captionBody` na prázdný
  text), zprávy poslat botem do Notifikátoru. Necommituje se.
- **Skutečné hovory ověřené mezi dvěma testovacími účty** (12. 10. 2026): příjemce má
  „Odmítnutý hovor" (odmítnutý z notifikace) a „Zmeškaný hovor" (volající zavěsil), volající
  u obou „Cancelled call" — tak to pravidlo `callOutcome` chce, odchozí nedovolaný je vždycky
  zrušený.
