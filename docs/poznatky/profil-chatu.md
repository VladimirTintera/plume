# Profil chatu

Poznatky k jedné oblasti aplikace. Obecná pravidla projektu a rejstřík ostatních souborů jsou
v [CLAUDE.md](../../CLAUDE.md); poučení z opravy v téhle oblasti patří sem.

## Profil chatu

Ťuknutí na hlavičku konverzace otevře profil (`ChatProfileRoute`, `features/profile`): velká fotka
a jméno, podtitul (dostupnost / počet členů / „Bot"), dlaždice Ztlumit a Hledat, údaje
(uživatelské jméno, telefon, bio nebo popis) a u skupiny členové s rolí.

- **Údaj má hodnotu nahoře, nastavení název nahoře** (`InfoRow` / `SettingRow`). U jména, telefonu
  nebo bia hledá člověk hodnotu a popisek pod ní říká, co to je, jako v kontaktech. U automatického
  mazání, upozornění, ukládání do galerie, barvy chatu, záložek a šifrovacího klíče je to naopak,
  jako v Nastavení; dokud šla přes `InfoRow`, četl se profil „Off / Auto-delete messages".
- **`ProfileRepository` v `core/telegram`** nad `getUserFullInfo` / `getBasicGroupFullInfo` /
  `getSupergroupFullInfo` a `searchChatMembers`. Data se čtou při otevření a **znovu po každé
  změně** (`profileUpdates`): název, fotka, emoji status a automatické mazání chatu, `User`
  a `UserFullInfo` člověka (jméno, uživatelské jméno, telefon, bio, blokování), `BasicGroup` /
  `Supergroup` (veřejný odkaz, počet členů) a stav tajného chatu. Plné info skupin hlídá
  `memberUpdates`. Zvlášť živé je ztlumení (`ChatRepository.muted`) a dostupnost (`presenceUpdates`).
- **Profil se na update čte CELÝ, neskládá se z updatu.** Čtení je lokální a co se změnilo, nese
  celý objekt od TDLib; poskládat profil z jednotlivých updatů by znamenalo udržovat druhou kopii
  převodu. Dřív se profil četl jen při otevření a po návratu z úpravy (`refreshOnReturn`, zůstala
  jako pojistka), takže přejmenování nebo nová fotka odjinud zůstaly staré, dokud se profil
  neotevřel znovu. **Ověřeno na emulátoru sondou** (necommituje se): ViewModel profilu testovací
  skupiny ji po deseti sekundách sám přejmenoval přes `setTitle` bez lokálního přepisu —
  otevřený profil ukázal nový název. Cesta přes `User` / `UserFullInfo` je tatáž, jen ji
  na emulátoru nebylo čím vyvolat.
- **Profil je detailový panel** (`detailPane()`): na telefonu překryje konverzaci, na širokém
  rozvržení ji v pravém panelu nahradí a zpět vede do ní. Spodní bar se skrývá jako u konverzace
  (`inDetail`). Routa je v `NavSavedStateConfiguration`.
- **Avatar a název přiletí z hlavičky konverzace** — tytéž sdílené klíče jako ze seznamu chatů.
- **Do profilu vede i tvář odesílatele ve skupině** (`MessageAction.OpenSenderProfile`). Profil
  stojí na chatu, takže se napřed najde soukromý chat (`privateChat`, lokálně). **Sdílený klíč
  nese jen ŤUKNUTÁ tvář** (`ConversationItemScope.isProfileFlight`, `rememberSaveable` podle id
  zprávy): tentýž člověk má tvář u každého bloku zpráv a o jeden klíč by se přetahovaly všechny.
  Klíč se nastaví při ťuknutí, dřív než se asynchronně najde chat — navigace ho pak už vidí.
  Záporné id odesílatele je chat (anonymní správce), ten profil neotevírá.
- **Hledat v profilu otevřeném ze skupiny otevře konverzaci S NÍM**, ne skupinu pod profilem:
  požadavek hledání je stav podle chatu a čekal by na chat, který není vidět.
- **Člen ze seznamu letí do hlavičky konverzace** (avatar i jméno, klíče jako ze seznamu chatů),
  kromě vlastního účtu — ten vede do Uložených zpráv se záložkou místo fotky.
- **Podstránky profilu mají střední flexibilní hlavičku s názvem chatu v podtitulku**
  (`ProfileSubpageTopBar`: pozvánky, žádosti o vstup, poslední akce, omezení a vyhození, práva
  člena; úprava skupiny bez podtitulku — název je hned pod ní v poli). Malý bar jen s názvem
  obrazovky neřekl, čeho se týká. Název se čte z místního přehledu (`rememberChatTitle`);
  **dokud nedorazí, je to prázdný řetězec, ne `null`** — bar s podtitulkem je vyšší a po načtení
  by poskočil. Sbaluje se jako Nastavení (na jednom panelu ano, v dvoupanelu ne). „Uložit" je
  plné tlačítko (`SaveActionButton`, z Nastavení přesunuté do `core/ui`).
- **Úprava skupiny a práva člena jsou bloky zaoblených řádků taky** (`GroupedColumn`, říjen 2026):
  práva členů, nastavení skupiny (`GroupSettingsCard`), pozvánky, omezení a vyhození, poslední
  akce, smazání (blok vlastní), práva správce, omezení člena a délka omezení. Dřív to byly karty
  `Card { Column { … } }` jako stará podoba Nastavení. **Přepínač Témata a nápověda pod ním
  zůstaly jedním řádkem bloku** (`Column` uvnitř), nápověda mluví o přepínači nad ní.
- **Údaje i obsah záložek jsou bloky zaoblených řádků** (`groupItemShape`, `groupRow`,
  spára 2 dp), stejný jazyk jako seznam chatů. Dřív byly údaje karta `SettingsGroup` (dnes `TitledGroup`)
  (`surfaceContainerHighest`) a záložky holé řádky — v jednom profilu dva vzhledy pro „seznam věcí".
  Mřížka médií nese velké zaoblení jen v rozích bloku (`mediaTileShape`, i schod neúplného
  posledního řádku). Role člena je tonální štítek, ne barevné slovo;
  vlastník terciární s hvězdou, správce sekundární se štítem — ikona proto, že barva sama
  rozdíl nenese (barvoslepost, paleta se zvýšeným kontrastem). Vlastní štítek člena nahrazuje
  slovo role, ikona a barva role zůstávají; běžný člen se štítkem má neutrální štítek. Bot je
  pod jménem, ne ve štítku — bývá i správce a dva štítky vedle sebe by se praly.
- **„Custom title" správce v TDLib 15 už NENÍ.** `ChatMemberStatusCreator/Administrator` ho
  nenesou; nahradil ho `ChatMember.tag`, který smí mít kterýkoli člen. Kdo hledá titul u stavu
  správce, nenajde nic. Akce jsou tónované dlaždice
  s ikonou nad popiskem; popisek říká, co se stane (Ztlumit / Zapnout zvuk), ne stav.
- **Záložky jsou connected button group** (`ToggleButton` s `ButtonGroupDefaults.connected*Shapes`),
  ne `PrimaryTabRow` s podtržením — to je vzhled M2 a mezi zaoblenými bloky působil cize.
  Vybraná je plná `primary` a zakulatí se celá. Semantika `Role.Tab`.
- **Vybraná záložka si přianimuje ikonu a o ni se rozšíří** (váha 1 → 1,4, jednou pružinou
  s ikonou). Se stejnou šířkou všech čtyř by ikona ukrojila popisek („Čle…"); ověřuje se
  v češtině, má nejdelší popisky.
- **Avatar se při sbalování zmenšuje do jména a mizí** (`graphicsLayer`, průběh = podíl výšky
  avataru, o který profil odjel). Zmenšuje se ke spodku a PŘESNĚ o tolik, o kolik odjel: vršek
  tím stojí těsně pod lištou. S poloviční mírou (první pokus) vršek dál utíkal pod lištu a byl
  useknutý. Scroll se čte až při kreslení, nic nerekomponuje.
- **Dlaždice akcí mění tvar**: stiskem zhranatí (20 → 12 dp) jako tlačítka M3 Expressive a ztlumený
  chat má dlaždici celou kulatou v `tertiaryContainer` — stejný jazyk jako vybraná záložka.
  Hledat stav nemá, takže tvarem nic nenese. Ikona s popiskem se mění přechodem: změna přijde až
  s potvrzením serveru a skok se dal přehlédnout.
- **Název se po odjetí velkého jména ukáže v liště** (`titleInBar`: scroll za spodkem jména,
  změřeným `onPlaced`). Bez toho nebylo u sbalené hlavičky poznat, čí profil je otevřený.
- **Hledat zavře profil a otevře hledání v konverzaci** přes `ConversationSearchRequests` — STAV,
  ne událost (jako `ChatOpenRequests`): konverzace pod profilem si požadavek vyzvedne, až ho
  odebírá. Hledání patří konverzaci, profil by ho jen zdvojil.
- **Popis skupiny chodí jako holý text** — odkazy a zmínky v něm dohledá `getTextEntities`
  (lokálně), aby byly klikatelné jako v bio (to je `FormattedText` rovnou). Bot bio nemá, bere se
  `botInfo.shortDescription`.
- **Členové jen tam, kde je server vydá** (`canListMembers`: malá skupina vždy, supergroup podle
  `canGetMembers`, kanál ne). Smazané účty (bez jména) a kdo odešel se vynechají. Ťuknutí na
  člena otevře soukromý chat.
- **Ukládání do galerie pro tenhle chat** je řádek mezi upozorněními a barvou konverzace, s vlastním
  ViewModelem (`ChatAutosaveViewModel`), ať profil nenese další závislost. Pravidla v
  `docs/poznatky/prohlizec-medii.md`, „Automatické ukládání do galerie".

### Osobní kanál a datum narození

Řádky v údajích o uživateli; poučení je v `docs/poznatky/nastaveni.md`, „Osobní kanál"
a „Narozeniny" (tam se obojí i nastavuje).

### Členové velké skupiny

Záložka Členové se stránkuje po 50 (`MEMBERS_PAGE_SIZE`) a u skupiny, která se na stránku
nevejde, má nad seznamem pole hledání (`MemberList`, testy `MemberListTest`).

- **`searchChatMembers` stránkovat NEUMÍ** — nemá `offset`, jen `limit` do 200. Dřív se proto
  ukazovalo prvních 200 a dál nic. Supergroup se čte `getSupergroupMembers` s filtrem
  (`Recent`, s dotazem `Search`) a posunem; základní skupina má nejvýš pár set členů a vydá je
  `searchChatMembers` naráz.
- **Vlastník a správci napřed jen na první stránce bez dotazu** (samostatný dotaz s filtrem
  `Administrators`). Setřídit celý seznam podle role nejde, další stránky přicházejí až potom.
  Server správce vrací i mezi nedávnými, takže se **tentýž člověk na dvou stránkách drží jednou**
  (`distinctBy`) — dva stejné klíče by `LazyColumn` shodily. Totéž hrozí, když se mezitím někdo
  přidá a celá řada se posune o místo.
- **Posun další stránky se počítá ze SUROVÉ odpovědi**, ne z vyfiltrovaného seznamu, a konec
  z `totalCount`. Stránka, ze které po odfiltrování nezbyl nikdo nový, seznam neprodlouží —
  a seznam, který se neprodloužil, si o další stránku sám neřekne; ViewModel ji proto dotáhne hned.
- **Repository chytá do `Result` i zrušení korutiny** (`runCatching` chytí
  `CancellationException`). Zrušené hledání by tím prošlo jako selhání a přepsalo výsledky
  prázdnem — po návratu z repository proto `ensureActive()`.
- **Skupina se skrytými členy vydá běžnému členovi JEN SPRÁVCE** (`hasHiddenMembers`), i při
  hledání a i s `canGetMembers == true`. Velké veřejné skupiny to mívají zapnuté skoro vždycky
  (Android Developers: 16 z 11 168, Kotlin Community: 22 z 6 840). Záložka to říká větou nad
  seznamem (`ChatProfile.membersHidden`) a hledání se nenabízí; bez vysvětlení to vypadalo jako
  nedotažený seznam. **Nečlenovi veřejné skupiny server taky vydá jen správce.**
- **Ověřit to jde jen ve skupině, kde je účet ČLENEM a členy nevidí skryté** — a takovou
  testovací účet nemá. Stránkování se proto ověřilo sondou (stránka po 3) na Android Developers:
  šest stránek po sobě, konec podle `totalCount`, s i bez správců napřed. **Pozor na kontrolu
  proti botům:** skupiny pouštějí nové členy až po ťuknutí na tlačítko pod zprávou od svého bota,
  a Plume tlačítka botů zatím nekreslí — napsaná odpověď se nepočítá.
- **Uložené zprávy profil nemají** — hlavička v nich není klikatelná.
- **Počty se oddělují po tisících úzkou nezlomitelnou mezerou** (`Int.grouped()` v `core/ui`) —
  „10704668 odběratelů" se nedalo přečíst. Commonmain `NumberFormat` nemá; mezera je čitelná
  v češtině i angličtině. Plurály proto berou číslo jako `%1$s`.

- **Méně časté akce jsou akce nad chatem** (`ChatActions`, `ChatActionsViewModel`): přidat
  členy, kopírovat odkaz (veřejné jméno, jinak pozvánka — tu server vydá jen správci s právem
  zvát), QR kód, sdílet kontakt, plocha, tajný chat, návrh fotky a data narození, kontakty
  a zablokovat / odblokovat uživatele či bota. Dlaždice nesou to, kvůli čemu se do profilu chodí;
  tohle se dělá jednou za čas. Zablokovat má potvrzení, odblokovat ne.
  - **Nabízí je ⋮ konverzace i ⋮ profilu, jedna implementace.** Dřív byly jen v profilu, ačkoli
    se týkají chatu, ne obrazovky profilu — a ztlumit bylo v konverzaci, blokovat v profilu, takže
    člověk musel vědět, kde hledat. V konverzaci jen u celého chatu (ne ve vlákně, tématu ani
    v Uložených), hovor tam v nabídce není (tlačítka v liště); zablokovat stojí u nahlášení.
  - **Profil vedle konverzace s TÝMŽ celým chatem nemá lištu vůbec** (`besideOwnConversation`,
    `standsOnOwnConversation` v `ChatsStack.kt`): jméno je v hlavičce konverzace, akce jsou v pásu
    nad profilem (viz „Konverzace a boční panel mají jeden pás hlavičky" v `navigace.md`)
    a Upravit (chat, jinak kontakt) je v ⋮ konverzace jako první akce nad chatem. Profil člena nad
    konverzací skupiny je jiný chat a lištu s tužkou a ⋮ si nechává, stejně jako profil na telefonu
    nebo nad seznamem témat.
  - **Vlastní malý ViewModel, ne `ChatProfileViewModel`**: ten načítá členy, média, dárky
    a příběhy, což konverzace nepotřebuje. `ChatActionsViewModel` čte jen profil (TDLib ho má
    v paměti) a po akci se obrazovka profilu obnoví sama z `profileUpdates` (`updateUser`
    nese kontakt, `updateChatBlockList` blokování).
  - **Ořez navrhované fotky leží přes celou obrazovku** (`ChatActionsHost`), proto ho nekreslí
    nabídka v liště: lišta konverzace se mění (výběr, hledání) a ořez by s ní zmizel. Nabídka
    i hostitel berou tutéž instanci ViewModelu přes klíč s id (`chatActionsViewModel`).
- **Nahlásit, smazat chat / opustit skupinu nebo kanál a ukončit tajný chat jsou v ⋮
  konverzace** (`ConversationMenu`), ne v profilu. Týkají se konverzace, ne člověka: profil člena
  otevřený z jeho zprávy ve skupině nabízel smazat soukromý chat, který s ním uživatel ani nemá.
  Smazání je tentýž sheet s volbami proti spamu (`ChatDeleteSheet`) jako dřív v profilu.
- **Po smazání nebo odchodu se zavře všechno, co chat ukazuje** (`closeChat` v `ChatsStack.kt`,
  test v `ChatsStackTest`): konverzace, profil a prohlížeč médií, v hlavním seznamu i archivu.
  Konverzace to dostává jako `onChatGone` (⋮ v hlavičce i pruh proti spamu); dřív se zavřela
  jen ona sama, takže po odchodu ze skupiny s tématy by pod ní zůstal seznam témat.
- **Blokuje se hlavní seznam** (`BlockListMain`) — ten brání psát i volat. Stav se čte
  z `UserFullInfo.blockList` a po akci přepíše lokálně: profil `UpdateUserFullInfo` neodebírá.
- **Opustit se nabízí jen tam, kde uživatel je** (v konverzaci `!canJoin` a `writeAccess` jiné
  než `NotMember`) — z chatu otevřeného z globálního hledání není odkud odejít.
- **Snackbar profilu dřív neukázal nikdy nic**, ani chybu: efekt klíčovaný `state.error` hlášku
  vynuloval a tím se sám zrušil (past „Vrátit"). Teď běží v `rememberCoroutineScope`.
- **⋮ → Sdílet kontakt** (`ShareContactRoute`, `ShareContactScreen`): tentýž `ChatPicker` jako
  přeposílání, víc cílů naráz, odesílá se tlačítkem a otevře se první vybraný chat. Nabízí se
  jen u člověka se ZNÁMÝM číslem (`ProfileKind.User`, `phone`) — bot číslo nemá a kontakt se
  bez čísla poslat nedá. **Potvrzovací dialog tu není**, na rozdíl od kontaktu ze sponky: tam
  odchází ťuknutím na řádek, tady až tlačítkem pod kartou, která jméno i číslo ukazuje.
  - **Tlačítko výběru říká „Poslat", ne „Přeposlat"** (`ChatPicker.confirmLabel`,
    `confirmIcon`). Dokud bylo napevno z přeposílání, lhalo o tom, co se stane.
  - Ověřeno na emulátoru sondou (položka v profilu testovacího bota, vlastní kontakt do
    Uložených zpráv — karta dorazila i s „Napsat zprávu"); bez sondy se u bota nenabízí.

### Úprava skupiny

„Upravit" v ⋮ profilu otevře `EditChatRoute` (`EditChatScreen`, `EditChatViewModel`): fotka,
název, popis a výchozí práva členů.

- **Dvě práva, ne jedno** (`ChatEditRights`, pravidlo `chatEditRights` ve `WriteAccess.kt`, testy
  `WriteAccessTest`). Název, popis a fotka stojí na `canChangeInfo`; výchozí práva členů na
  `canRestrictMembers`, protože to vyžaduje `setChatPermissions`. Správce může mít jen jedno
  z nich, takže se o každém bloku obrazovky rozhoduje zvlášť — a položka v menu se nabídne, když
  je aspoň jedno.
- **Rozhoduje vlastní postavení, ne typ chatu** — stejně jako u psaní (`writeAccess`). Vlastník
  smí všechno; správce to, co má v právech; běžný člen mění informace jen tam, kde to dovolují
  výchozí práva skupiny (`Chat.permissions.canChangeInfo`), a práva členů nikdy; omezený člen se
  řídí svými vlastními právy, která ta výchozí přebíjejí.
- **V kanálu se práva členů nenabízejí.** Nikdo do něj nepíše, takže není co omezovat — a TDLib
  by `setChatPermissions` stejně odmítl.
- **Fotka a práva se ukládají HNED, texty až tlačítkem Uložit.** Fotka má vlastní nahrávání
  a přepínač se musí pohnout na ťuknutí; posílat text po písmenech nemá smysl. Stejné dělení
  jako u úpravy vlastního profilu.
- **TDLib na změnu `ChatPermissions` update NEPOSÍLÁ**, takže je obrazovka jediný zdroj pravdy:
  přepínač se pohne hned a při selhání se vrátí — jako v nastavení archivu.
- **Práva se zapisují CELÝM objektem**, takže co aplikace nenabízí (hudba, video kolečka, ankety,
  náhledy odkazů, štítky, témata), se přenese z aktuálního stavu. Nastavit to mohl jiný klient
  a přepsat mu to tiše by bylo horší než to nenabízet.
- **Nabízí se jen ty druhy obsahu, které aplikace umí poslat** — tytéž jako `ContentAccess`.
  Přepínač u něčeho, co se z aplikace stejně poslat nedá, by sliboval vliv, který nemá.
  Video kolečka a ankety mezi nimi chyběly, i když je aplikace posílá — doplněné (ověřeno se
  dvěma účty: vypnutá kolečka u člena schovají přepínání na kolečko).
- **Omezení se všemi přepínači zapnutými NENÍ zrušení omezení.** Člen zůstane „Omezeno bez
  konce" s vlastními právy a výchozí práva skupiny (třeba nově povolená témata) pro něj
  přestanou platit. Zpět mezi běžné členy vede jen „Zrušit omezení" (`unrestrictMember`).
- **Název a popis jsou dvě volání.** Když první selže, druhé se neposílá a co prošlo, se zapíše
  jako výchozí stav — další pokus to neposílá znovu. Přesně jako u vlastního profilu.
- **Fotka jde přes CESTU** (`InputChatPhotoStatic` + `InputFileLocal`), ne přes systémové URI —
  TDLib jiné nepřijme, stejně jako u příloh. Ořezává se na čtverec podle výřezu z ořezu
  (`SquarePhoto`, viz „Ořez profilové fotky"), protože se kreslí v kruhu. Odebrání je `setChatPhoto(photo = null)`; jiné volání na to není.
- **Úprava má vlastní routu v OBOU větvích** (`EditChatRoute`, `ArchiveEditChatRoute`). Scéna se
  určuje metadaty, takže jedna routa nemůže patřit hlavnímu seznamu i archivu — stejně jako
  u konverzace a profilu.
- **Zpět z úpravy vrací profil, ne chat.** Chaty mají `PopUntilContentChange` (viz „Navigace"),
  takže se dvoupanelová scéna nepřeskočí celá; kdyby tam bylo výchozí chování, zpět by odešlo
  rovnou z profilu i z konverzace.
- **Ověřeno bez zásahu do skupiny**: položka v menu u vlastníka, její nepřítomnost v soukromém
  chatu, načtení názvu, popisu, fotky a všech práv a neaktivní Uložit, dokud se nic nezmění.
- **Uložení ověřené v testovací skupině** („Plume test zakladni", Bot API `getChat`): popis
  i výchozí práva členů (fotky vypnout a zapnout). **Bot API práva skupiny vrací se zpožděním
  několika vteřin** — hned po přepnutí ukazuje starou hodnotu a vypadá to, že se nic neuložilo.
  Stejně tak `adb input text` s `%s` na pomalém emulátoru useklo konec popisu („Testovaci
  skupina p"): obsah pole se proto čte dumpem PŘED uložením, ne až ze serveru.

### Fotka z emoji

„Z emoji" vedle „Změnit fotku" v úpravě skupiny / kanálu i v úpravě vlastního profilu otevře
`EmojiPhotoSheet`: vlastní emoji z nabídky Telegramu na barevném přechodu, náhled v tvaru
avataru (skupina sušenka, kanál čtverec, účet kruh). Nastaví se hned, bez ořezu a bez Uložit,
stejně jako fotka z galerie (`ProfilePhotoFile.Emoji` → `InputChatPhotoSticker`; testy
`EmojiPhotoTest`).

- **Obrázek i animaci vyrobí server.** Aplikace posílá jen id emoji a přechod pozadí
  (`ChatPhotoStickerTypeCustomEmoji`, `BackgroundFillGradient`); hotovou fotku pak dostane jako
  každou jinou. Náhled v sheetu je jen přiblížení — na emulátoru vyšla serverová fotka stejně.
- **Nabídka emoji se liší pro účet a pro chat** (`getDefaultProfilePhotoCustomEmojiStickers`
  / `getDefaultChatPhotoCustomEmojiStickers`, `StickerRepository.defaultPhotoEmoji`) a jde
  použít bez Premium.
- **Mimo nabídku: nainstalované sady vlastních emoji i samolepek.** Přepínač Emoji /
  Samolepky, pod ním sady jako čipy („Nabídka" a sady emoji, resp. sady samolepek). Samolepka
  jde jako `ChatPhotoStickerTypeRegularOrMask` (id sady a samolepky, `ProfilePhotoFile.Sticker`).
  **Emoji z nainstalované sady server přijal i bez Premium** (ověřeno: „Duck Emoji" na účtu
  bez Premium). Hledat ve všech sadách sheet neumí — sady se přidávají z výběru samolepek
  v konverzaci.
- **I při zakládání skupiny a kanálu** (`CreateChatScreen`, „Z emoji" vedle „Přidat fotku").
  Volba se drží v `CreateChatViewModel.emojiPhoto` a na server jde až po založení, stejně jako
  fotka ze souboru; jedna vylučuje druhou. Náhled kreslí `EmojiPhotoPreview` — samolepka si
  proto nese i `preview` (jen pro aplikaci, na server jdou id).
- **Barvy pozadí jsou pevné RGB bez alfy** (`EMOJI_PHOTO_BACKGROUNDS`): jsou to data fotky,
  kterou uvidí všichni v každém klientovi, ne barvy rozhraní, takže z motivu být nemůžou.
  `BackgroundFill` alfu nebere.
- **Jednobarevné emoji se v náhledu kreslí bíle**, jak ho na fotce vykreslí server; barva textu
  z motivu by na sytém pozadí zmizela.
- **Tři tlačítka pod avatarem jsou `FlowRow`**: „Odebrat fotku" na telefonu přeteče na druhý
  řádek, vedle sebe by se nevešla.
- **Ověřeno na emulátoru v testovací skupině** („Plume test fotka"): sheet načetl nabídku,
  přechod i emoji šly vybrat a fotka ze serveru odpovídala náhledu. Pak emoji ze sady „Duck
  Emoji" a samolepka ze sady „Funky Goose" — obojí server vykreslil. Založení skupiny „Plume
  test emoji" s fotkou z emoji: skupina vznikla rovnou s ní (servisní zpráva „Změnil(a) jsi
  fotku skupiny"), pak smazaná pro všechny. Vlastní fotku účtu jsem
  neměnil (uviděly by ji kontakty) — jde touž cestou přes `setProfilePhoto`.

### Správa členů

Dlouhý stisk na členovi v záložce Členové otevře nabídku (`MemberActionsSheet`): napsat zprávu,
štítek, povýšit na správce / upravit jeho práva, sesadit, omezit / upravit omezení, zrušit
omezení, předat vlastnictví, vyhodit. Práva správce a omezení mají vlastní obrazovku
(`MemberRightsScreen`, routy `MemberRightsRoute` / `ArchiveMemberRightsRoute`). Pravidlo, co
uživatel s kterým členem smí, je čistá funkce `memberActions` (`core/telegram/MemberManagement.kt`,
testy `MemberManagementTest`).

- **Ťuknutí otevírá konverzaci dál, správa je dlouhý stisk** — jako akce nad chatem v seznamu.
  Kdyby ťuknutí otevíralo nabídku, zmizela by nejčastější cesta „napsat tomuhle člověku".
- **Nabídka se neotevře, když nemá co ukázat** (`MemberActions.any`). Běžnému členovi by se
  jinak otevřel prázdný sheet a vypadalo by to jako chyba.
- **Meze jsou ověřené v knihovně, ne odhadnuté** (`strings libtdjsonjava.so`): „Can't restrict
  users in basic group chats" (omezovat jde jen v supergroupě; v kanálu nikdo nepíše, takže tam
  není co omezovat), „Can't change owner in basic group chats", „Can't promote or demote self",
  „Can't restrict self" a **„Can't edit chat administrators"** — cizího správce, kterého jsem
  sám nepovýšil, měnit nesmím. To poslední se pozná z `ChatMemberStatusAdministrator.canBeEdited`;
  vlastníka ho neomezuje.
- **Sesazení není vlastní volání, jsou to prázdná práva** (`AdminRights.any == false` →
  `ChatMemberStatusMember`). Tak to bere TDLib, takže „Sesadit" v nabídce je zkratka k témuž
  uložení.
- **Vyhození jde přes `banChatMember`, ne `setChatMemberStatus`** — jen ono umí dobu a rozhodnutí
  o zprávách. `revokeMessages = false` vědomě: mazání celé historie je jiná věc a v nabídce hned
  vedle vyhození by se na něj dalo ťuknout omylem.
- **V základní skupině je vyhození jen odebrání**, vyhozeného může kdokoli přidat zpátky; ze
  supergroupy a kanálu se sám nevrátí. Text potvrzení je proto pro každý případ jiný — jeden
  společný by lhal.
- **Práva se ukládají TLAČÍTKEM, ne po jednom přepínači** — obráceně než výchozí práva skupiny
  v `EditChatScreen`. Každý přepínač by byl vlastní `setChatMemberStatus`, tedy série změn
  postavení na serveru, a u omezení se zároveň vybírá doba, která bez přepínačů nedává smysl.
- **U už omezeného člena je první volba doby „ponechat"** se zbytkem běžícího omezení. Bez ní by
  změna jediného přepínače u člověka omezeného na týden přepsala konec na „navždy" a nebylo by
  z čeho to poznat. Je to **vlastní příznak `keepsCurrentEnd`, ne porovnání `duration` s tím, co
  zbývá**: zbytek může vyjít přesně na některou z nabízených dob a obě volby by se pak tvářily
  jako vybrané.
- **„Ponechat" posílá PŘESNĚ původní konec** (`restrictMember(keepUntil)`, `keptUntilDate`,
  testy `MemberManagementTest`), ne „teď + zbytek". Přepočet posunul konec o čas strávený na
  obrazovce (ověřeno Bot API: +19 s) a omezení, kterému zbývalo pod 30 s, by udělal trvalým —
  tak krátkou dobu bere TDLib jako navždy. Takové se posune těsně za tu mez.
- **Doby se drží pod 366 dny.** Všechno delší (a kratší než 30 sekund) bere TDLib jako navždy,
  takže by delší volba slibovala konec, který nenastane. Navždy je `untilDate = 0`.
- **Práva správce jsou zúžená sada a zapisují se celým objektem** (jako `GroupPermissions`): co
  aplikace nenabízí (videohovory, stories, přímé zprávy kanálu), se u stávajícího správce
  přenese z jeho současných práv — nastavit mu to mohl jiný klient. `canManageChat` drží podle
  toho, jestli správcem vůbec zůstává; v Telegramu je to implicitní právo každého správce.
- **Skupina a kanál ukazují každý jinou podmnožinu.** V kanálu se publikuje a upravují cizí
  příspěvky, ve skupině se připíná a omezuje; anonymita je jen v supergroupě.
- **„Spravovat témata" jen ve skupině s tématy** (`AdminRights.manageTopics`,
  `MemberDetail.isForum`). Dřív chybělo úplně, takže správce povýšený v Plume témata spravovat
  nesměl a nikde se to nedalo zapnout. Nový správce ho ve skupině s tématy dostane ve výchozí
  sadě, jinde se právo nenabízí ani neposílá. Ověřeno se dvěma účty (12. 10. 2026): správce
  s právem na témata a bez mazání cizích zpráv má u cizího tématu Připnout, Přejmenovat
  a Uzavřít, Smazat ne; přejmenování server přijal („Téma upraveno" u vlastníka).
- **Seznam členů se po změně ČTE ZNOVU, neodvozuje se z updatu** (`ProfileRepository.memberUpdates`
  nad `UpdateChatMember`, `conflate`). Jeden update nese jednoho člena a co změna doopravdy
  udělala, ví jen server — tentýž vzor jako připnuté zprávy a relace v Nastavení. **`UpdateChatMember`
  chodí jen správcům**, tedy právě těm, kdo členy mění; komu by k ničemu nebyl, ten ho nedostane.
- **Na změnu člena se čte znovu celý profil, ne jen seznam** — jinak hlavička po vyhození dál
  hlásila „2 členové". Samotné čtení ale nestačí: počet nese `SupergroupFullInfo`, který má
  TDLib v keši až minutu, takže hned po vyhození vrátí starý. Opraví ho až
  `UpdateSupergroupFullInfo` (u základní skupiny `UpdateBasicGroupFullInfo`), a proto ho
  `memberUpdates` odebírá taky. Ověřeno: „1 člen" do osmi vteřin po vyhození, bez odchodu
  z profilu.
- **Jestli jde předat vlastnictví, se ptáme PŘED otevřením dialogu** (`canTransferOwnership`).
  Bez zapnutého dvoufázového ověření (`PasswordNeeded`) nebo s čerstvým heslem či relací
  (`PasswordTooFresh` / `SessionTooFresh`) to Telegram nedovolí a uživatel by psal heslo zbytečně.
  Vlastníkem se navíc nestane bot.
- **Heslo NENÍ `rememberSaveable`.** Uložený stav se serializuje a přežil by i zabití procesu;
  heslo žije jen v dialogu a odchází rovnou do TDLib.
- **Po převodu se profil čte znovu** — vlastníkem je někdo jiný, takže se změnila i naše vlastní
  práva a s nimi celá nabídka.
- **Koin čte parametry INDEXEM** (`parameters.values[0]`), protože `chatId` i `userId` jsou `Long`
  a `get()` bere parametr podle typu — zaměnilo by je. Tatáž past jako u `messageId` konverzace.
- **`var duration` a `fun setDuration(…)` se v JVM srazí** („Platform declaration clash"): property
  si generuje setter téhož jména. Funkce se proto jmenuje `chooseDuration`.
- **Nabídka je CELÁ rozbalená a ještě scrollovatelná** (`enabledValues = Hidden, Expanded`).
  S výchozím sheetem skončilo „Vyhodit" pod spodní hranou obrazovky — u položky, která je
  poslední a nejzávažnější, to vypadá, že vyhodit člena vůbec nejde. Tatáž past jako u výběru
  samolepek a nastavení archivu; osm položek se do napůl rozbaleného sheetu nevejde.
- **Ověřit to jde botem ve vlastní testovací skupině.** Vlastník sám sebe spravovat nemůže
  („Can't promote or demote self"), takže se vlastní účet k ničemu nehodí; bot přidaný do skupiny
  je plnohodnotný druhý člen, na kterém jde povýšení, omezení i vyhození vyzkoušet bez druhého
  uživatelského účtu. **Omezit bota ale nejde** — omezení je jen v supergroupě, takže skupina se
  musí nejdřív převést. Převod vlastnictví se takhle ověřit nedá vůbec: bot vlastníkem být nemůže.
- **Ověřeno na emulátoru (bez zásahu do skutečné skupiny):** nabídka se na vlastním účtu
  neotevře (`isSelf`), na správci ukáže právě těch sedm položek, které vlastník v supergroupě
  má; práva správce se načtou se skutečnými hodnotami a „Uložit" je zašedlé, dokud se něco
  nezmění; zpět bez uložení nic neposílá; dialog vyhození má předvybrané „Navždy" a text podle
  druhu skupiny; „Předat vlastnictví" na účtu bez dvoufázového ověření skončí vysvětlením,
  ještě než se zeptá na heslo.
- **Zápisy ověřené v testovací supergroupě s testovacím botem** („Plume test zakladni",
  kontrola přes Bot API `getChatMember`): omezení na den (zakázané fotky, `until_date` +24 h),
  úprava omezení s „ponechat" (konec beze změny), zrušení omezení, štítek i jeho smazání
  (`tag`), povýšení s výchozími právy a sesazení, vyhození na hodinu i navždy a návrat bota
  přes „Přidat členy". **Dokončený převod vlastnictví ověřený NENÍ** — bot vlastníkem být nemůže.
  Se dvěma účty (12. 10. 2026, skupina „Plume test predani", vlastník Pixel 7, člen Pixel 5):
  s heslem nastaveným týž den Plume převod zastavil PŘED zadáním hesla, server vrátil
  `PasswordTooFresh` s `retryAfter` = 7 dní. **Hláška teď čekací dobu říká** („Převést
  vlastnictví půjde za 7 dní", `waitUiText` zaokrouhluje nahoru, plurály „za …" kvůli 4. pádu);
  dřív jen „až s odstupem" a uživatel zkoušel naslepo. Dokončit převod jde od 19. 10. 2026.
- **Dlouhý stisk přes `adb` chce 1,5 s.** Se `swipe … 900` se gesto vyhodnotilo jako ťuknutí
  a místo nabídky se otevřela konverzace s tím členem — vypadalo to jako chyba `combinedClickable`.
  A `uiautomator dump` při běžící animaci selže („null root node returned"), **jenže nechá na
  místě STARÝ soubor**, takže další `cat` vypíše předchozí obrazovku a nová verze aplikace se
  tváří, že se nezměnila. Před každým dumpem proto `rm -f /sdcard/ui.xml`.

### Nastavení skupiny a kanálu

Úprava skupiny má pod právy členů kartu **Nastavení skupiny** / **Nastavení kanálu**
(`GroupSettingsCard`): pomalý režim a reakce jako řádky s výběrem, pod nimi přepínače —
historie pro nové členy, psát jen členové, schvalovat nové členy, skrýt členy, agresivní
antispam, podpisy a profily autorů v kanálu, zakázat ukládání obsahu. Na konci úpravy je
**Smazat skupinu / kanál** pro všechny.

- **Co se nabídne, je čistá funkce** `groupSettingsOffer` (`core/telegram/GroupSettings.kt`, testy
  `GroupSettingsTest`). Meze jsou z dokumentace TDLib: pomalý režim, schvalování a „psát jen
  členové" chtějí právo omezovat, historie, reakce a podpisy právo měnit údaje, ochrana obsahu
  vlastníka. Přepínače supergroupy se základní skupině nenabízejí (musela by se převést), reakce
  a ochrana obsahu ano.
- **Historie jen v soukromé skupině bez kanálu, „psát jen členové" jen v diskusi kanálu,
  schvalování ve veřejné skupině nebo v diskusi s „psát jen členové".** Jinde je volba buď
  pevná, nebo nemá smysl — oficiální klienti ji tam taky neukazují. **Skrýt členy a antispam
  rozhoduje server** (`canHideMembers`, `canToggleAggressiveAntiSpam`): malá skupina je nemá.
- **Ukládá se hned a při selhání se přepínač vrátí**, jako práva členů. Reakce jsou výjimka:
  výběr „Jen vybrané" se ukládá tlačítkem, po jednom ťuknutí na každé emoji by šla na server
  řada změn.
- **Výběr reakcí jsou `updateActiveEmojiReactions`** (`QuickReaction.choices`, tytéž emoji jako
  rychlá reakce). Vlastní emoji reakce kanálu aplikace vybrat neumí, a proto je při uložení
  přenese beze změny — stejně jako strop počtu reakcí na zprávu.
- **Tlačítko Uložit u reakcí stojí MIMO posouvaný obsah.** Emoji je přes sedmdesát a tlačítko
  na konci seznamu šlo najít jen dlouhým rolováním. Oba listy (reakce, pomalý režim) jsou celé
  rozbalené: napůl otevřený list schoval delší doby pomalého režimu pod spodní hranu.
- **Podpisy a profily autorů jsou v TDLib jedno volání** (`toggleSupergroupSignMessages`);
  repository druhou hodnotu dočte a vypnuté podpisy vypnou i profily. Profily se bez podpisů
  ani neukazují.
- **Smazání pro všechny (`deleteChat`) jen s `Chat.canBeDeletedForAllUsers`** (vlastník). Po
  smazání zavře `closeChat` i úpravu a všechno nad ní (práva člena, pozvánky, žádosti, omezení),
  ne jen konverzaci a profil — maže se právě z úpravy (test v `ChatsStackTest`).
- **Ověřeno na emulátoru** (14. 10. 2026, „Plume test moderace", vlastník …527): pomalý režim
  10 s, reakce „Jen vybrané" bez tří emoji, ochrana obsahu i historie tam a zpět — po novém
  otevření úpravy drželo všechno server; pak vráceno. Smazání pro všechny na jednorázové skupině
  „Plume test smazani": aplikace se vrátila do seznamu a skupina z něj zmizela. Kanál, veřejná
  skupina, diskuse kanálu, skrytí členů a antispam ověřené NEJSOU (testovací účet takové nemá).

#### Správa za boosty a hlavní záložka

Karta má navíc tři volby (stejná `groupSettingsOffer`, testy `ChannelBoostSettingsTest`):

- **Boosty pro obejití omezení** (supergroupa, právo omezovat; `setSupergroupUnrestrictBoostCount`,
  `SupergroupFullInfo.unrestrictBoostCount`): řádek s nabídkou Vypnuto / po 1–8 boostech. Kdo
  skupinu boostne aspoň tolikrát, nemusí dodržovat pomalý režim ani omezení členů.
- **Sponzorované zprávy** (jen vlastník kanálu; `toggleSupergroupCanHaveSponsoredMessages`,
  `canHaveSponsoredMessages` zná jen vlastník). **Zapnout jdou vždycky, vypnout až od úrovně
  boostů** `chatBoostFeatures.minSponsoredMessageDisableBoostLevel` (pro kanál dnes 50).
  Úroveň se čte `getChatBoostStatus`, mez `getChatBoostFeatures(isChannel = true)` — dva dotazy
  navíc, proto jen když se volba nabízí. Pod mezí je přepínač zamčený a věta říká, od které
  úrovně to jde a jakou kanál má; když mez nepřišla, vypnout se nenabídne (`Int.MAX_VALUE`).
- **Hlavní záložka profilu** (kanál, právo měnit údaje; `setSupergroupMainProfileTab`) — na ní
  se profil otevře v každé aplikaci. Totéž má vlastní profil v úpravě profilu
  (`setMainProfileTab`, `UserFullInfo.mainProfileTab`) a ukládá se hned.
  - **„Automaticky" se nabízí, jen dokud nic vybrané není**: zrušit volbu TDLib neumí,
    `setMainProfileTab` bere jen konkrétní záložku. Kdo ji jednou nastaví, může ji jen měnit.
  - **Plume volbu i respektuje**: profil (`ChatProfile.mainTab`) přeskočí na odpovídající záložku,
    jakmile se objeví — záložky přibývají, jak dorážejí počty, dárky a příběhy. Jen jednou a jen
    dokud uživatel sám nepřepnul (`mainTabDone`), jinak by záložka uskočila pod rukou.
    Hlavní záložka, kterou chat nemá (bez GIFů, bez příběhů), se nepoužije.
  - „Příspěvky" (`profileTabPosts`) jsou v Plume záložka Příběhy.
- **Záložka vyžádaná zvenčí** (`ProfileTabRequests`, „Všechna média" v prohlížeči médií):
  profil na ni přeskočí, jakmile ji má, a sbalí hlavičku, ať je obsah záložky vidět hned. Má
  přednost před hlavní záložkou. **Je to stav, ne pole routy**: `ChatProfileRoute` se porovnává
  celá (otevřený profil, profil vedle konverzace, `followProfile`) a pole navíc by ty rovnosti
  rozbilo. Nevyřízený požadavek (chat záložku nemá) se zahodí s profilem, jinak by přeskočil
  až při některém dalším otevření.
- **Ověřeno na emulátoru** („Plume test kanal", vlastník; „Plume test zalozeni", diskuse kanálu):
  sponzorované zprávy zamčené s větou „od úrovně 50, kanál má úroveň 0"; hlavní záložka Příběhy
  a pak GIFy drží server (po novém otevření úpravy), „Automaticky" po první volbě zmizelo a profil
  kanálu s odkazem a GIFem se otevřel rovnou na GIFech (druhá záložka). Boosty „Po 2 boostech"
  po novém otevření drží, pak vráceno na Vypnuto. Vlastní hlavní záložka jen zobrazená, neměněná
  (uvidí ji kontakty); skutečné vypnutí reklam nešlo ověřit (kanál nemá úroveň 50).

### Poslední akce

Úprava skupiny nebo kanálu má pro správce řádek **Poslední akce** (`EventLogRoute`,
`EventLogScreen`, `EventLogViewModel`): co kdo udělal za posledních 48 hodin
(`getChatEventLog`), po dnech, u každé události autor, popis, podrobnost a čas.

- **Popis je podstatné jméno, ne věta se slovesem** („Nový název: …", „Vyhození: Jan",
  „Témata: zapnuto"). Čeština by u slovesa potřebovala rod autora („změnil / změnila"), a ten
  Telegram neposílá — hádat ho ze jména nejde. Přepínače mají hodnotu příslovcem
  („zapnuto / vypnuto"), které sedí k čemukoli bez ohledu na rod.
- **Převod je v `core/telegram/ChatEventLog.kt`** (`toEventText`, `when` bez `else`): nový druh
  události v novější TDLib rozbije překlad, neprojde tiše. Co se podrobně nepopisuje (barvy,
  pozadí, status), má obecnou větu svého druhu („Změna vzhledu").
- **Změny členů se rozlišují podle starého a nového postavení**: `ChatEventMemberPromoted`
  je povýšení, úprava práv, sesazení i předání vlastnictví; `ChatEventMemberRestricted`
  vyhození, omezení i jejich zrušení.
- **Podrobnost je náhled zprávy** (`Message.previewText`, i u smazané a upravené), nový popis
  nebo odkaz pozvánky — na dva řádky. Jména autorů se čtou jednou za stránku.
- **Jen supergroup a kanál, jen vlastník a správce** (`EditableChat.canViewEventLog`); základní
  skupina záznam nemá. Živě se neobnovuje, TDLib na něj update nemá.
- Ověřeno na emulátoru (14. 10. 2026, „Plume test moderace"): celý den testů — přidání členů,
  smazané zprávy s textem, vyhození, povolení návratu, omezení a jeho zrušení, přepínače
  nastavení, změna reakcí, připnutí a odepnutí. Kanál ověřený není.

#### Hledání a filtry záznamu

Nad záznamem je vyhledávací pilulka a řádek čipů: **kdo** (čip s nabídkou správců) a **druh
akce** (Zprávy, Členové, Správci, Omezení, Informace o skupině, Pozvánky, Videochaty, Témata).

- **Filtruje server, ne načtené stránky** (`getChatEventLog` s `query`, `filters`, `userIds`;
  `ChatLogFilter` → `toTdlFilters`, testy `ChatLogFilterTest`). Záznam se stránkuje po stovce
  a filtr nad první stránkou by tvrdil „nic", i když starší akce odpovídá.
- **Žádný vybraný druh = `filters = null`**, ne objekt se samými `false` — ten by ze serveru
  nevrátil nic. Čipy jsou hrubší než 15 příznaků TDLib (Zprávy = úpravy, smazání i připnutí;
  Informace o skupině = `infoChanges` + `settingChanges`; Pozvánky nesou i prodloužení
  předplatného), rozdělení podle oficiálních klientů.
- **Druhy jsou `FilterChip`, ne connected button group** jako druh chatu v hledání zpráv: tam
  se volí nejvýš jedna ze tří, tady libovolně mnoho z osmi, a to je úloha filtračního čipu.
  Vybraný má fajfku, stav nese i tvar.
- **Kdo = správci z `SupergroupMembersFilterAdministrators`** (vlastník první), jen lidé — autor
  akce v záznamu je vždycky vlastník nebo správce (člen je autorem jen svého příchodu
  a odchodu). Nabídka se po výběru NEZAVÍRÁ, vybírá se víc lidí; „Kdokoli" výběr zruší a zavře
  ji. Když se správci nenačtou, čip se neukáže.
- **Změna filtru zruší rozběhnuté načítání i další stránku** (`loadJob`): jejich výsledek patří
  ke starému filtru a vložil by do seznamu akce, které novému neodpovídají. Hledání čeká 400 ms
  po posledním písmenu.
- **Prázdný výsledek s filtrem má vlastní stav** („Nic neodpovídá" + Zrušit filtry), jinak by
  tvrdil „za 48 hodin nic". Zrušení vymaže i text v pilulce — ten drží pole samo, proto se
  založí znovu (`key(searchGeneration)`).
- Ověřeno na emulátoru (15. 10. 2026, „Plume test moderace"): hledání „Neprectena" najde
  připnutí a odepnutí, s čipem Členové nic a „Zrušit filtry" vrátí celý záznam; čip Zprávy
  nechá jen zprávy; nabídka Kdo s vlastníkem (jediný správce — že filtr podle autora
  vyřadí cizí akce, ověřit nešlo, všechny akce jsou jeho).

#### Omyl antispamu

Zpráva, kterou smazal agresivní antispam skupiny, má v Posledních akcích pod náhledem tlačítko
**Není to spam** (`reportSupergroupAntiSpamFalsePositive`; `ChatLogEvent.antiSpamMessageId`,
`EventLogViewModel.reportAntiSpamMistake`, testy `AntiSpamReportTest`).

- **Nabízí se jen podle příznaku od TDLib** (`ChatEventMessageDeleted.canReportAntiSpamFalsePositive`):
  zprávu smazanou správcem nahlásit nejde a volání by skončilo chybou. Id pro volání je id
  smazané zprávy, ne události, a supergroupa se dočte z chatu.
- **Tlačítko v řádku, ne nabídka nad událostí**: jiná akce nad událostí není a nabídka s jedinou
  položkou by ji jen schovala. Malé obrysové tlačítko (`ExtraSmallContainerHeight`) pod
  podrobností, ať řádek neroste víc, než musí.
- **Po nahlášení tlačítko zmizí** (`withAntiSpamReported`), během čekání je vypnuté a druhé
  ťuknutí nic neodešle. Po chybě zůstává a hláška jde přes `userMessage`. Zprávu to NEVRÁTÍ —
  Telegram tím ladí antispam skupiny; hláška o úspěchu proto neslibuje obnovení.
- **Na zařízení NEOVĚŘENÉ.** Událost vzniká jen tam, kde agresivní antispam (`canToggleAggressiveAntiSpam`,
  server ho dovolí až větší skupině) zprávu opravdu smazal; testovací účet takovou skupinu nemá.
  Ověřené jsou testy ViewModelu nad `FakeProfileRepository` (testovací náhrada repository, ze které
  si test přepíše jen volané metody).

### Omezení a vyhození

Úprava skupiny má kartu **Členové** s řádky Omezení a Vyhození (v kanálu jen Vyhození), každý
s počtem v podtitulu. Řádek otevře seznam (`RemovedMembersScreen`, `RemovedMembersViewModel`,
routy `RemovedMembersRoute` / `ArchiveRemovedMembersRoute` s příznakem `banned`): kdo, do kdy,
a tlačítko Zrušit omezení / Povolit návrat. Ťuknutí na omezeného otevře jeho omezení
(`MemberRightsRoute`), aby šlo změnit, co smí, ne jen omezení zrušit.

- **Dřív šlo zrušit vyhození jen tomu, koho měl správce zrovna před sebou** — vyhozený
  v seznamu členů není, takže na něj nebylo kde dlouze stisknout.
- **Seznamy jsou `getSupergroupMembers` s filtrem `Banned` / `Restricted`** (`ProfileRepository.members`
  s `MemberFilter`), stránkované a s hledáním jako záložka Členové — filtr bere dotaz, takže se
  hledá jen mezi omezenými / vyhozenými. Hledání se ukáže, až se seznam nevejde na stránku.
- **Jen supergroup a kanál, jen vlastník a správce s právem omezovat** (`EditableChat.moderation`).
  Základní skupina vyhozené nevede: vyhození je v ní jen odebrání.
- **Počty se čtou TÍMŽ dotazem (`totalCount` s `limit = 1`), ne z `SupergroupFullInfo`.**
  `restrictedCount` z plných údajů TDLib drží v keši: po omezení člena ukazoval „Nikdo", zatímco
  seznam už člověka měl. A **update o změně člena na obnovení počtu nestačí**: přijde dřív, než
  server nový stav vydá, a čtení v tu chvíli vrátilo starý počet (po zrušení omezení zůstalo
  „1 člověk"). Úprava proto počty čte znovu i po návratu ze seznamu (`refreshOnReturn`, jako profil);
  odběr `memberUpdates` zůstává na změny od jiného správce.
- **Zrušit vyhození = `setChatMemberStatus(ChatMemberStatusLeft)`.** Do skupiny se tím nikdo
  nepřidá, jen se smí vrátit sám nebo ho někdo přidá — hláška to říká („… se může znovu přidat").
- **Zrušení se nepotvrzuje**: obojí jde vzít zpátky (omezit a vyhodit znovu) a nikomu nic nezmizí.
- **Ověřeno na emulátoru** (14. 10. 2026, účet …527, skupina „Plume test moderace" s „Pixel 7 Pro"
  a botem Notifikátor): omezení na den v seznamu („Omezeno do zítra 19:28"), zrušení ze seznamu,
  vyhozený bot s „Povolit návrat", počty v úpravě po návratu. Kanál ověřený NENÍ.

### Historie profilových fotek

Ťuknutí na velkou fotku v profilu chatu otevře prohlížeč médií se všemi profilovkami chatu
(`MediaRoute(profilePhotos = true)`, `MediaViewerViewModel.loadProfilePhotos`,
`ProfileRepository.profilePhotos`). Otevírá se vpravo na aktuální fotce, doleva starší.

- **Je to zdroj prohlížeče, ne nová obrazovka.** Přiblížení, video, galerie / celá obrazovka,
  uložení a sdílení prohlížeč umí; stačilo mu podat položky. Stejně jako u přílohy ankety
  (`notMessages`): stránky nejsou zprávy, takže se nenačítají reakce ani práva, neodebírají
  se úpravy a mazání a z akcí zbývá uložit a sdílet (`PROFILE_PHOTO_ACTIONS`). Klíčem stránky
  je id FOTKY; `messageId` v routě je 0.
- **Člověk a bot: `getUserProfilePhotos`**, po stránkách do `totalCount` (strop 300). Osobní
  fotka, kterou si uživatel kontaktu nastavil sám, ani veřejná fotka v něm podle TDLib nejsou.
- **Skupina a kanál historii fotek v TDLib NEMAJÍ.** Skládá se z aktuální fotky (`photo`
  z plných údajů skupiny) a ze servisních zpráv o změně fotky (`SearchMessagesFilterChatPhoto`
  → `MessageChatChangePhoto`), bez duplicit podle id. Fotka, jejíž servisní zprávu někdo smazal,
  proto chybí. Smyčka hledání končí i na prázdné stránce a na neměnném pokračování — jinak by se
  při chybné odpovědi dotazy točily donekonečna.
- **Velikosti se vybírají stejně jako u fotky ve zprávě** (`photoMedia`, sdílené s
  `Photo.toMedia`): `ChatPhoto` nese tytéž `PhotoSize`, jen v jiném obalu. Animovaná profilovka
  je video (`AnimatedChatPhoto`, čtvercové podle `length`, délku přehrávač zjistí sám) s fotkou
  jako náhledem.
- **Datum v liště je, kdy si chat fotku nastavil** (`addedDate`), a podle něj se jmenuje i
  uložený soubor.
- **Fotky uživatele se NEŘADÍ podle data, drží pořadí od serveru** (první = aktuální). Starší
  fotka nastavená znovu jako hlavní si nechává původní `addedDate`: dokud se řadilo podle data,
  zůstala po nastavení uprostřed a jako aktuální se dál tvářila ta předchozí. Podle data se
  řadí jen skupiny, kde se historie skládá ze zpráv o změně fotky.
- **Let z avataru do prohlížeče není.** Avatar v profilu už nese sdílený klíč letu z hlavičky
  konverzace (`chatAvatarKey`); druhý klíč na tomtéž prvku by se s ním pral. Prohlížeč se
  otevře běžným přechodem.
- **Bez fotky (iniciály) se na avatar ťuknout nedá** a Uložené zprávy profil nemají.
- **Vlastní profilovky se otevírají z úpravy profilu** (Nastavení → Obecné → Upravit profil,
  ťuknutí na fotku). Id účtu je id chatu Uložených zpráv, takže `profilePhotos` nic navíc
  nepotřebuje; kladné id bez známého chatu vezme rovnou jako uživatele.
- **Prohlížeč pak leží v zásobníku NASTAVENÍ, ne Chatů** — a zavírá se z toho, ve kterém leží
  (`chatsStack.pop(route) || settingsStack.pop(route)`). Dokud šlo zavírání jen přes zásobník
  Chatů, ✕ by v Nastavení nedělalo nic; systémové zpět to nepotřebuje, bere vybranou sekci.
- **Vlastní profilovky jde v ⋮ nastavit jako hlavní a smazat** (`setProfilePhoto`
  s `InputChatPhotoPrevious`, `deleteProfilePhoto`; `MediaViewerState.ownProfilePhotos` podle
  `getMe`). Jen u vlastního účtu — starší fotku skupiny znovu použít ani jednotlivě smazat TDLib
  neumí. „Nastavit jako profilovou" se u aktuální fotky nenabízí, smazání se potvrzuje dialogem
  a po smazání poslední fotky se prohlížeč zavře. Nastavená fotka se přesune na konec, takže
  pager na ni dojede sám (`focusMessageId`) — jinak by zůstal na indexu, tedy na jiné fotce.
- **Cizí profilovku jde v ⋮ nahlásit** („Nahlásit fotku", `reportChatPhoto`), jen kde to TDLib
  dovolí (`Chat.canBeReported`; `MediaViewerState.reportablePhotos`). Nahlásit jde jen PLNÁ
  fotka, ne náhled ani animovaná podoba — `ProfilePhoto.reportFileId` je největší ze `sizes`.
  Důvod je pevný výčet z TDLib (`ReportReason`, doména `PhotoReportReason`), server se na nic
  neptá, takže průvodce nahlášením chatu (`ReportFlow`) se nehodí: vlastní `ReportPhotoSheet`,
  ťuknutí na důvod rovnou nahlásí, „Něco jiného" chce popis. Ověřeno na emulátoru (kanál
  s fotkou) po výběr důvodu a pole popisu; **samotné nahlášení odeslané NENÍ** — šlo by
  skutečným moderátorům.
- **Znovu nastavená fotka dostane NOVÉ id** (ověřeno sondou: …131 → …133). Hledat ji po
  nastavení podle id, se kterým se nastavovala, nejde; pager se proto posílá na aktuální
  (poslední) fotku.
- **Ověřeno na emulátoru** (česky, se souhlasem, na skutečném účtu): starší fotka nastavená
  jako hlavní (snackbar, „3 z 3" a pager na ní) a hned vrácená původní, nabídka bez
  „Nastavit…" u aktuální fotky. **Smazání ověřené jen po potvrzovací dialog** — mazalo by
  skutečnou fotku. **Že u cizího profilu nabídka chybí, ověřené na zařízení NENÍ** (otevřel by
  se profil skutečného člověka).
- **Ověřeno na emulátoru** (česky): člověk se třemi fotkami („3 z 3", listování ke staršímu
  datu), kanál s jednou fotkou („1 z 1"), uložení do galerie (`Pictures/Plume`, jméno podle
  data přidání), vlastní profilovky z úpravy profilu („3 z 3", ✕ i zpět vrací do úpravy
  profilu). **Animovaná profilovka ani skupina s několika změnami fotky ověřené nejsou.**

### Přidání členů

Pruh „Pozvat členy" nad čerstvou skupinou (`InviteMembersBar` z `ChatActionBarInviteMembers`)
a ⋮ → „Přidat členy" / „Přidat odběratele" v profilu otevřou `AddMembersRoute`
(`AddMembersScreen`, `AddMembersViewModel`): tentýž `ChatPicker` jako kontakty, víc lidí naráz,
přidává se tlačítkem s počtem. Kdo smí, je čistá funkce `canInviteMembers` (testy
`InviteMembersTest`), kdo se nabídne, `addMemberCandidates` (testy `AddMemberCandidatesTest`).

- **Pravidlo „kdo smí" je totéž jako u bota bez práv správce** (`canAddBot`): vlastník, správce
  s právem zvát, člen podle výchozích práv skupiny, omezený podle svých. **V kanálu členové
  nezvou nikdy** — `chat.permissions.canInviteUsers` tam nic neznamená.
- **Supergroup a kanál berou všechny naráz (`addChatMembers`), základní skupina jen po jednom**
  (`addChatMember`) a novému členovi rovnou přepošle posledních 100 zpráv (`forwardLimit`,
  `BASIC_GROUP_HISTORY`) — jinak by vstoupil do skupiny, ve které nic nevidí.
- **Odmítnutí kvůli soukromí NENÍ chyba.** Obě volání vrací `FailedToAddMembers` a přidání
  ostatních proběhne; obrazovka ty odmítnuté vyjmenuje a nabídne poslat jim pozvánku
  (`sendInviteLink`, s entitou odkazu z `getTextEntities` — server ji v textu sám neoznačí).
  Bez pozvánky (vidí ji jen správce s právem zvát) dialog jen řekne, koho přidat nešlo.
- **Kdo už členem je, se nenabízí** — ale jen z první stránky členů (200). Číst kvůli výběru
  tisíce členů nestojí za to; server už přidaného podruhé nepřidá.
- **Bot do KANÁLU nejde přidat jako odběratel** — `addChatMembers` celé volání odmítne s
  `USER_BOT` (ne jako odmítnutého v `FailedToAddMembers`), takže dřív selhalo přidání úplně a
  ukázala se jen obecná hláška. V kanálu se proto boti z výběru oddělí (`botsAmong`, `getUser`
  → `UserTypeBot`), lidé se přidají jako dřív a na boty se obrazovka zeptá: „Přidat bota jako
  správce?" s právy zveřejňovat, upravovat a mazat příspěvky (`setAdminRights` na nečlena bota
  rovnou přidá). Ve skupině bot jako člen jde, tam se nic nemění. Ověřeno na emulátoru: bot
  v testovacím kanálu jako `administrator` s `can_post_messages` (Bot API `my_chat_member`).
- **Ověřeno na emulátoru** v testovací skupině: pruh podstrčenou sondou, přidání testovacího bota
  (Bot API `getChatMember` → `member`, v konverzaci „Members joined the group"), bot se pak
  podruhé nenabídne, položka v ⋮ profilu. Dialog odmítnutých sondou (repository vrátil všechny
  jako odmítnuté) anglicky i česky a „Poslat pozvánku" — bot dostal odkaz s entitou `url`.
  **Skutečné odmítnutí kvůli soukromí a základní skupina ověřené NEJSOU** — chtělo by to
  přidávat skutečné lidi. Bota z testovací skupiny jde zase odebrat `leaveChat` přes Bot API.

### Veřejný odkaz v úpravě chatu

Úprava skupiny nebo kanálu má tentýž blok Soukromý / Veřejný jako založení (`ChatTypeSection`
v `core/ui`, sdílený s `CreateChatScreen`). Odkaz se ukládá tlačítkem Uložit spolu s názvem
a popisem (`ProfileRepository.setUsername` → `setSupergroupUsername`, prázdné = soukromý).

- **Jen vlastník** (`EditableChat.canChangeUsername`). Základní skupině se blok nabízí taky,
  uložení s odkazem ji ale napřed převede na supergroupu — viz „Převod základní skupiny".
- **Bere se `usernames.editableUsername`**, ne první aktivní jméno: sběratelská jména z Fragmentu
  (`collectibleUsernames`) se tudy měnit nedají a do pole nepatří.
- **Přepnutí na soukromý řekne předem, že se odkaz uvolní** (`chat_type_link_released`) — vrátit
  ho nemusí jít, když si ho mezitím vezme někdo jiný.
- **Dostupnost se ptá `checkChatUsername(chatId)` existujícího chatu**; současné jméno se
  nekontroluje (bylo by „obsazené" sebou).
- **Limit vlastních veřejných chatů se zjišťuje hned po volbě Veřejný** a nabídne odebrat odkaz
  jinému chatu — jen u chatu, který veřejný ještě není. Viz „Limit veřejných odkazů"
  v `docs/poznatky/hledani-a-zakladani-chatu.md`.
- **Profil chatu se po návratu z úpravy čte znovu** (`ChatProfileViewModel.refreshOnReturn`).
  Data čte jen při otevření a TDLib změnu názvu, popisu ani odkazu profilu nehlásí — po uložení
  ukazoval staré hodnoty, dokud se neotevřel znovu. Platilo to i pro název a popis, jen to dřív
  nikdo nezkoušel na zařízení.
- **Ověřeno na emulátoru** na testovacím kanálu: načtení veřejného odkazu, varování
  o uvolnění, přejmenování (Bot API `getChat` najde nové jméno, staré ne) a přepnutí na soukromý
  (Bot API hlásí `CHANNEL_PRIVATE`), profil po návratu pokaždé aktuální. **Neověřeno**:
  veřejná skupina (kód je společný), iOS.

### Převod základní skupiny

Veřejný odkaz ani témata základní skupina nemá. V úpravě skupiny se vlastníkovi nabízejí obojí
a uložení odkazu (nebo zapnutí témat) se napřed zeptá a skupinu převede na supergroupu
(`upgradeBasicGroupChatToSupergroupChat`, `ProfileRepository.upgradeToSupergroup`). Plume samo
základní skupiny nezakládá (viz „Založení skupiny a kanálu"), týká se to skupin odjinud.

- **Převod dá NOVÝ chat s jiným id** a je nevratný. Stará skupina zůstane jako neaktivní chat
  (`BasicGroup.isActive == false`, `upgradedToSupergroupId`) s dosavadními zprávami a ze seznamu
  chatů zmizí; nová začíná oznámením „Skupina byla převedena". Oficiální klienti obě historie
  slévají, Plume ne — proto **oznámení vede do té druhé skupiny** (`ChatMessage.upgradeLink`:
  „Zobrazit starší zprávy" / „Otevřít novou skupinu") a stará skupina má místo pole tlačítko do
  nové (`ChatInfo.upgradedToChatId`). Potvrzovací dialog to říká předem.
- **Pruh „Pozvat členy" drží TDLib i u převedené skupiny** — konverzace ho tam schová, zvát se
  dá jen do nové.
- **Úprava zůstává otevřená a její ViewModel se přepne na nové id** (`EditChatViewModel.chatId`
  je `var`); ostatní obrazovky staré skupiny přepíše navigace (`replaceUpgradedChat`, testy
  `ChatsStackTest`). Nahradit i úpravu by zahodilo rozepsané texty a hlášku o výsledku.
- **Dostupnost jména se u základní skupiny ptá s `chatId = 0`**, jako při zakládání —
  `checkChatUsername` základní skupinu nebere.
- **Id druhé strany se skládá konvencí TDLib** (základní skupina −id, supergroupa
  `supergroupChatId`) v čisté funkci `upgradeLink`, ne dotazem v `toChatMessage`.
- **Ověřeno na emulátoru** na čtyřech testovacích skupinách (sondou `createNewBasicGroupChat`
  s testovacím botem): odkaz (Bot API `getChat @…` → `supergroup`) i témata, oznámení oběma
  směry, tlačítko ve staré skupině, nová skupina v seznamu hned po převodu. iOS jen přeložené.
- **Nová skupina po převodu v seznamu chatů chyběla až do restartu** — souběh updatů, viz
  „Seznam chatů": `UpdateChatPosition` doběhl dřív než `UpdateNewChat` a `upsert` ho přepsal.

### Diskuse ke kanálu

V úpravě kanálu je řádek „Diskuse" (`DiscussionSheet`): „Bez diskuse" a skupiny, které jde
připojit (`ChatRepository.suitableDiscussionChats` nad `getSuitableDiscussionChats`).
Připojení i odpojení je `ProfileRepository.setDiscussionGroup` (`setChatDiscussionGroup`,
prázdné = `0`). Pod příspěvky se pak objeví „Komentovat" a vlákno vede do připojené skupiny
(viz „Komentáře").

- **Smí správce kanálu s právem měnit údaje** (`EditableChat.canSetDiscussion`). Skupina musí
  být supergroupa, kde je uživatel vlastník nebo správce — tu podmínku hlídá server výběrem
  vhodných skupin, aplikace ji nepočítá.
- **Připojení se potvrzuje, odpojení ne.** Připojená skupina dostává všechny příspěvky a její
  historie bude vidět i novým členům; odpojení jen vypne komentáře a jde vrátit.
- **Propojený chat čte `SupergroupFullInfo.linkedChatId`** (`EditableChat.linkedChat`); řádek se
  změní až po potvrzení serverem.
- **Ve výběru jsou i skutečné skupiny uživatele** (na testovacím účtu „Kuřáci 2.0", „Testing
  group") — při ověřování ťukat jen podle textu z čerstvého výpisu.
- **Bot API `getChat` hlásí `linked_chat_id` s pár vteřinovým zpožděním** — po propojení ještě
  `None`, o chvíli později správně. Ověřovat s odstupem, jinak to vypadá jako nepovedené volání.
- **Automatická kopie příspěvku v diskusní skupině dorazí jako zpráva skupiny** a vyvolá
  notifikaci skupiny, i když uživatel zrovna čte kanál. Jestli ji oficiální klienti potlačují,
  ověřené není.
- **Ověřeno na emulátoru**: testovací kanál ↔ testovací skupina (Bot API `linked_chat_id` na obou
  stranách), příspěvek od bota s „Komentovat", komentář ve vlákně a „1 komentář" pod
  příspěvkem, odpojení (`linked_chat_id` zmizel) a znovu připojení.
- **Profil ukazuje propojený chat v Informacích** (`ChatProfile.linkedChat`): u kanálu „Diskusní
  skupina", u skupiny „Propojený kanál"; ťuknutí ho otevře. Řádek stojí za popisem — je to údaj
  o chatu, ne nastavení. Chat, který TDLib lokálně nezná (soukromý kanál, kde uživatel není),
  se vynechá. Ověřeno na emulátoru oběma směry. Odpojit jde jen z úpravy KANÁLU
  (`setChatDiscussionGroup` bere id kanálu a chce jeho správce).

### Sada samolepek skupiny

V úpravě supergroupy je řádek „Sada samolepek" (`GroupStickerSetSheet`): „Žádná sada"
a nainstalované sady uživatele. Ukládá se hned (`ProfileRepository.setStickerSet` →
`setSupergroupStickerSet`, `0` = zrušit), bez potvrzení — sada jen přibude členům ve výběru
samolepek (záložka za Oblíbenými, viz „Výběr samolepek" v emoji-samolepky-reakce.md) a jde vrátit.

- **O tom, jestli řádek je, rozhoduje server** (`SupergroupFullInfo.canSetStickerSet`), ne práva.
  Malé skupině ho nedá ani vlastníkovi: „Plume test pozvánek" (vlastník, 2 členové) má `false`.
  Odmítne to už TDLib sama („Can't set supergroup sticker set"), dřív než server.
- **Nabízí se jen nainstalované sady**, jako v oficiálních klientech; současná sada (mohl ji
  nastavit jiný správce) se ukáže vždycky, i když ji uživatel nemá.
- **Výběr samolepek si sadu skupiny čte jednou za otevření konverzace** (`loadGroupSet`), takže
  změna se v téže konverzaci projeví až po jejím znovuotevření.
- **Ověřeno jen částečně**: v testovací skupině se řádek neukáže; s dočasně vynuceným řádkem
  sheet se sadami a chybová hláška po odmítnutí (řádek zůstal „Žádná sada"). Skutečné nastavení
  ověřené NENÍ — účet nemá skupinu, kde by to server dovolil, a cizí skupiny se nemění.

### Boosty

Úprava supergroupy nebo kanálu má pro správce řádek „Boosty" (stejná podmínka jako záznam akcí,
`canViewEventLog`). Sheet (`BoostStatusSheet`, `EditChatViewModel.openBoosts`) ukáže úroveň,
počet boostů a kolik chybí do další úrovně s průběhem (`BoostStatus.progress`, testy
`BoostStatusTest`), kolik členů má Premium (číslo dostane jen správce) a odkaz na boost ke
zkopírování nebo sdílení — tím se o boost žádá členů.

- **Boostovat samo Plume neumí** — chce to Premium, které cizí klient neprodá. Řádek je pro
  správce, kteří chtějí vědět, kde chat stojí, a poslat odkaz dál.
- **Průběh se počítá od současné úrovně** (`currentLevelBoostCount` → `nextLevelBoostCount`),
  ne od nuly; u nejvyšší úrovně (`nextLevelBoostCount == 0`) je plný a nic nechybí.
- **Znak procenta v textu je jednoduchý** (`%`): Compose Resources `%%` nerozbalí a ukázalo by
  se zdvojené.
- **Ověřeno na emulátoru** u vlastního testovacího kanálu: „Úroveň 0, 0 boostů · do úrovně 1
  chybí 1", průběh a tlačítka odkazu. Kopírování a sdílení odkazu se neťukalo.
- **Pod stavem je „Kdo boostuje"** (`getChatBoosts`, `ProfileRepository.boosts`, stránky po 50
  přes `nextOffset`, „Načíst další"): tvář a jméno, do kdy boost platí, odkud je (dárkový kód,
  soutěž; Premium se nepíše, je to běžný případ) a „3×" u víc boostů od téhož (Premium má víc
  slotů). Soutěž, ve které se výherce nenašel (`user_id` 0), je řádek „Soutěž bez výherce".
  - **Stav a seznam se čtou souběžně a selhání seznamu stav nezahodí** — seznam se pak jen
    neukáže. Bez boostů se oddíl nekreslí vůbec; „0 boostů" říká už stav nad ním.
  - **Ověřeno na emulátoru**: testovací kanál boosty nemá (`total_count` 0), oddíl se neukázal.
    Vzhled řádků a „Načíst další" se sondou (dva podstrčené boosty, další stránka od serveru
    prázdná, tlačítko pak zmizelo). **Neověřené:** skutečné boosty a dárkové kódy.

### Statistiky

Úprava kanálu nebo skupiny má řádek **Statistiky** (`ChatStatisticsRoute`,
`features/statistics/`), jen když je server vydá (`SupergroupFullInfo.canGetStatistics` →
`EditableChat.canGetStatistics`): kanál zhruba od 500 odběratelů, větší skupina. Statistika
jednoho příspěvku (`PostStatisticsRoute`) se otevře z přehledu kanálu (Nedávné příspěvky) nebo
z „Informace o zprávě" v konverzaci (`MessageInfo.canGetStatistics` z `MessageProperties`).
Doména je v `core/telegram/ChatStatistics.kt`, TDLib v `TdlStatisticsRepository`.

- **Kanál a skupina mají jiná data** (`ChatStatisticsChannel` / `…Supergroup`): kanál přehled
  odběratelů, průměrů na příspěvek a příběh a nedávné příspěvky, skupina zprávy, čtenáře,
  pisatele a žebříčky členů, správců a těch, kdo zvali. Grafy mají oba, každý jiné.
- **Graf je JSON ve formátu, který Telegram posílá všem klientům** (`StatisticalGraphData.json_data`:
  sloupce `x`, `y0…`, `types`, `names`, příznaky `stacked`, `percentage`, `y_scaled`). Převádí
  ho `parseStatChart` (testy `ChatStatisticsTest`), nikdy nevyhodí — formát patří serveru.
  Barvy a formátovací funkce v JavaScriptu (`xTickFormatter`…) se vědomě nečtou.
- **Část grafů server dopočítává** (`StatisticalGraphAsync` s tokenem). Obrazovka se na ně
  nečeká: ukáže čekání v kartě a ViewModel je dočte souběžně (`getStatisticalGraph`).
  `StatisticalGraphError` a nesrozumitelný JSON jsou `StatGraph.Failed` a karta se nekreslí,
  stejně jako graf ze samých nul — chybový text od serveru na obrazovku nepatří.
- **Graf kreslí vlastní Canvas** (`StatisticsChart.kt`, geometrie v `ChartGeometry.kt`,
  testy `ChartGeometryTest`): čáry, schody, sloupce na sobě, plochy na sobě a podíl ze sta
  procent. Ťuknutí nebo vodorovný tah vybere bod (svislý tah patří posunu obrazovky),
  druhé ťuknutí výběr zruší.
  - **Dvě měřítka (`y_scaled`) = dva grafy pod sebou**, ne dvě osy y v jednom: zhlédnutí
    v tisících a sdílení v desítkách by se na společné ose nedala číst. Okraj s popisky osy y
    je pro oba díly SPOLEČNÝ — s vlastním by „2,5 tis." a „20" posunuly začátek grafů a týž
    den by nestál nad sebou (stalo se, viděno na emulátoru).
  - **Popisky osy y mají vlastní okraj vlevo.** Uvnitř grafu je překrývaly čáry a sloupce.
  - **Čára daleko od nuly má osu od své nejmenší hodnoty** (`axisMin`, když je minimum nad
    polovinou maxima): růst odběratelů z 1 490 na 1 577 byl s osou od nuly vodorovná čára.
    Sloupce a plochy začínají od nuly vždycky — jejich výška je hodnota.
  - **Vybraný bod ukazuje přesné číslo** (`grouped`), ne zkrácené jako osa: `compactCount`
    zaokrouhluje dolů a bod u 1 395 se četl jako „1,3 tis.".
  - **Jediná řada má hodnotu pod datem vždycky** (bez výběru poslední bod) — řádek, který
    naskočil až s výběrem, posunul graf pod prstem. U víc řad nesou hodnoty položky legendy
    a ta se při výběru může o řádek prodloužit; zatím to tak zůstává.
  - **Víc než osm řad se sečte do „Ostatní"** (`StatChart.folded`, nejmenší podle součtu) —
    graf jazyků jich mívá přes deset a víc barev od sebe oko nerozezná.
  - **Data a hodiny jsou v UTC**, jako je Telegram počítá; v pásmu na západ od Greenwiche by
    půlnoc bodu spadla na předchozí den. Hodinové grafy to mají v nadpisu („(UTC)").
- **Barvy řad jsou pevná paleta, ne odvozené z motivu** — vědomá výjimka z pravidla „barvy jen
  z `colorScheme`". Odstíny po kruhu od `primary` (jako `senderColor`, HCT sytost 48)
  propadly při změření validátorem palet: sousední barvy pro deuteranopa ΔE 2,8–3,6 při cíli 8
  a čtyři z osmi pod prahem sytosti (četly se šedě). Paleta v `seriesColors` je referenční
  paleta pro grafy ověřená na barvoslepost ve světlém i tmavém režimu (ΔE 9,1 / 8,4) a má
  pevné pořadí, ať barva patří pořád téže řadě. **Graf s jedinou řadou je `primary`** —
  barva tam nic nerozlišuje a má ladit s aplikací. Změna nebarví podle stavu (zelená nahoru,
  červená dolů): směr nese znaménko a barvoslepý by zelenou s červenou nerozeznal.
- **Názvy řad („Views", „Joined", jazyky) jsou od serveru** a v aplikaci se nepřekládají.
  Nadpisy grafů jsou naše (`StatGraphKind.title`) — v JSONu žádný není.
- **Veřejná sdílení příspěvku** (`getMessagePublicForwards`, stránky po 50) vedou do cizího
  kanálu na tu zprávu (`openTarget`); sdílení do příběhu otevřít nejde, má jen štítek.
  Soukromá přeposlání server vypisuje jen počtem.
- **Ověřeno na emulátoru JEN SONDOU** (26. 10. 2026): testovací kanál má dva odběratele
  a server mu statistiky nevydá, takže sonda (necommituje se) podstrčila data ve formátu od
  serveru — čára, schody hodin, sloupce na sobě, podíl jazyků dočtený se zpožděním, graf se
  dvěma měřítky, chybějící graf, příspěvek, příběh i nenačtená zpráva v přehledu, veřejná
  sdílení. Viděné ve světlém i tmavém režimu, výběr bodu u čáry, sloupců i ploch, cesta
  z „Informace o zprávě". **Neověřené:** skutečný kanál a skupina, žebříčky skupiny, statistika
  příspěvku od serveru.

### Gigaskupina

Úprava supergroupy nabízí vlastníkovi **Převést na gigaskupinu** (`toggleSupergroupIsBroadcastGroup`,
`ProfileRepository.convertToBroadcastGroup`): skupina bez stropu členů, psát smějí jen správci.
Převod je nevratný, proto potvrzení (`ConfirmDialog`, bez chybové barvy — nic se nemaže).

- **Nabízí se jen tam, kde ho Telegram sám doporučil** (`suggestedActionConvertToBroadcastGroup`
  s id supergroupy, `AccountNotices.broadcastGroupSuggestions`; pravidlo `offersBroadcastGroup`,
  testy `BroadcastGroupTest`). Server převod dovolí až skupině blízko stropu členů a hranici
  klientovi jinak neprozradí; řádek nabízený pořád by u běžné skupiny jen skončil chybou.
- **Doporučení nejde do karty nahoře v seznamu chatů** — je to rada vlastníkovi jedné skupiny,
  ne účtu. Karta ho dál vynechává (`toDomain` vrací `null`), drží se zvlášť po skupinách.
- **Stav doporučení se v úpravě čte jako Compose stav**, ne holé `StateFlow.value`: doporučení
  může dorazit až po otevření úpravy a řádek se pak musí ukázat sám.
- **Neověřeno na zařízení.** Testovací účet žádnou tak velkou skupinu nevlastní, takže
  doporučení nikdy nepřijde a řádek se neukáže. Ověřené jen testy pravidla a to, že úprava
  běžné skupiny řádek neukazuje a nic nespadne.

### Uvítací zprávy

Úprava skupiny nebo kanálu má řádek **Uvítací zprávy** (`WelcomeMessagesSheet`,
`EditChatViewModel.openWelcomeMessages`, `ProfileRepository.welcomeMessages`): zprávy, které
noví členové dostanou po vstupu, v pořadí, v jakém odejdou, se smazáním u každé a polem pro
novou textovou. Novinka TDLib (`addChatWelcomeMessage`, `deleteChatWelcomeMessage`,
`loadChatWelcomeMessages`).

- **Kdo je smí spravovat:** vlastník, nebo správce s právem `canSendWelcomeMessages`
  (`ChatEditRights.canManageWelcomeMessages`, test v `WriteAccessTest`). Je to samostatné právo
  — správce, který má jen ho, úpravu chatu otevře taky.
- **Seznam přichází updatem** (`updateChatWelcomeMessages`), `loadChatWelcomeMessages` jen řekne
  o načtení. Odběr proto musí běžet DŘÍV (`channelFlow` se sběrem spuštěným `UNDISPATCHED`),
  jinak by první dávka odešla do prázdna. Po přidání i smazání (i z jiného zařízení) přijde nový
  seznam sám, sheet ho jen sleduje, dokud je otevřený.
- **Pole se vyprázdní až po potvrzení serveru** (`WelcomeSheet.added`), ne při ťuknutí: po chybě
  by text zmizel.
- **Média přidávat Plume neumí** (TDLib bere i fotku, video, samolepku…). Zpráva s médiem od
  jiného klienta se ukáže popisem obsahu („Fotka", `previewTextOrNull`) a jde smazat.
  Úprava existující zprávy (`editChatWelcomeMessage`) ani smazání všech naráz nejsou.
- **Ověřeno na emulátoru** ve vlastní testovací skupině „Plume test zalozeni" (vlastník):
  prázdný seznam od serveru, přidání „Vitejte v Plume testu" (vrátilo se updatem, pole se
  vyprázdnilo), smazání (seznam zase prázdný). **Neověřené:** že noví členové zprávu opravdu
  dostanou (chtělo by druhý účet), správce jen s tímhle právem, kanál.

### Pozvánky a žádosti o vstup

V úpravě skupiny (⋮ v profilu → Upravit) je blok Pozvánky se dvěma řádky: **Odkazy k pozvání**
(`InviteLinksRoute`, `InviteLinksScreen`) a **Žádosti o vstup** (`JoinRequestsRoute`,
`JoinRequestsScreen`) s počtem čekajících. Hranice je `InviteLinkRepository` v `core/telegram`,
pravidla čisté funkce (`inviteLinkStatus`, `InviteLinkDraft.normalized`, `InviteLinkEditor`,
testy `InviteLinkEditorTest`).

- **Spravovat smí vlastník a správce s `canInviteUsers`** (`ChatEditRights.canManageInvites`).
  Členové smějí zvát podle výchozích práv, ale odkazy spravovat ne — `createChatInviteLink` chce
  správce. Správce, který smí JEN zvát, dostane úpravu skupiny jen s tímhle blokem.
- **Odkaz jde sdílet systémovou nabídkou** (`MediaSharer.shareText`): na kartě hlavního odkazu
  je „Sdílet" hlavní tlačítko a kopírování ikona vedle (tři textová tlačítka se česky na
  telefon nevešla), u dalších odkazů je „Sdílet" první položka nabídky. Odkaz se zakládá, aby
  šel někomu poslat — i mimo Telegram. Ověřeno na emulátoru: nabídka se otevře s adresou,
  zpět vrátí do Plume; skutečné odeslání se nezkoušelo.
- **Seznam odkazů je vlastní** (`getChatInviteLinks` bere tvůrce): hlavní odkaz, další
  odkazy a zrušené. **Odkazy ostatních správců vidí jen vlastník** — sekce pod vlastními
  (`getChatInviteLinkCounts`, ostatním správcům vrací chybu, a to je prázdná sekce, ne
  selhání) a ťuknutí na správce otevře sheet s jeho odkazy. Vlastník je smí zrušit a smazat,
  **upravit ne** — nastavení patří tomu, kdo odkaz založil, takže nabídka nad cizím odkazem
  „Upravit" nemá.
- **Odkazy bota smí zrušit a smazat jen bot sám.** Vlastníkovi server odpoví
  `EDIT_BOT_INVITE_FORBIDDEN`; sheet bota proto nabízí jen sdílet a kopírovat a řekne proč
  (`AdminInviteLinks.isBot`). Našlo se to až zkouškou — z dokumentace to poznat nejde.
- **Počty u správce se můžou chvíli rozcházet se seznamem.** Hned po založení dvou odkazů hlásil
  server u bota 1 platný, jeho seznam vrátil dva (hlavní a založený), po pár minutách 2. Pravdu
  má seznam, který se čte při otevření sheetu.
- **Chyba akce v sheetu se ukazuje v sheetu** (`AdminLinksView.error`), ne snackbarem —
  ten by zůstal pod modálním sheetem a selhání by vypadalo jako ťuknutí, které nic neudělalo.
  Přesně tak se schoval `EDIT_BOT_INVITE_FORBIDDEN` při první zkoušce.
- **Kdo se přidal** (`getChatInviteLinkMembers`, stránky po 100 přes `offsetMember`): sheet
  s tváří, jménem, dnem vstupu a „přes sdílenou složku". Na kartě hlavního odkazu je tlačítko
  „Kdo se přidal · N" (nabídku nad sebou karta nemá), u dalších odkazů položka nabídky —
  i u zrušeného, lidé ve skupině zůstávají. Jen s `memberCount > 0`.
- **Dialog zrušení odkazu má „Ponechat", ne „Zrušit"** — česky by byla obě tlačítka „Zrušit".
- **Po každé změně se seznam čte znovu**, nic se nepřepisuje lokálně — zrušení hlavního odkazu
  vyrobí na serveru nový a jeho adresu zná jen server. Hlavní odkaz se proto „vyměňuje", ne ruší.
- **Se schvalováním se limit nesmí poslat** — TDLib `member_limit` u odkazu se žádostmi odmítne.
  Koncept ho zahodí sám (`normalized`) a formulář limit se schvalováním schová.
- **Platnost a limit jsou volby, ne čísla** (hodina / den / týden / napořád, bez limitu / 1 / 10
  / 100). „Za den" se počítá až při uložení (`toDraft(now)`), ne při otevření formuláře. Úprava
  odkazu s koncem nabídne „Ponechat"; neobvyklý limit nastavený jinde se do voleb přidá.
- **Vypršelý a naplněný odkaz server sám nezruší**, jen přestane pouštět dovnitř; bez
  `inviteLinkStatus` by v seznamu vypadal jako platný.
- **Nový odkaz se rovnou zkopíruje** — vytváří se proto, aby se poslal dál. Sdílení textu
  aplikace zatím nemá (`MediaSharer` sdílí jen soubory).
- **Vyřizovaná žádost z řádku hned zmizí** (`processing`) a seznam se čte znovu až po vyřízení —
  jinak by na okamžik naskočila zpátky. Seznam se čte znovu i na `UpdateChatPendingJoinRequests`
  (žádost vyřídil jiný správce). Hromadné přijetí a odmítnutí je za ⋮ s potvrzením: týká se
  i žádostí, které na obrazovce nejsou (`processChatJoinRequests` s prázdným odkazem).
- **Ověřeno na emulátoru** v testovací skupině (založené sondou `createNewSupergroupChat`, Plume
  zakládat skupiny neumí): vytvoření odkazu s platností a limitem, úprava na schvalování
  (limit zmizel, platnost zůstala), zrušení s potvrzením, smazání zrušených, prázdné žádosti,
  anglicky i česky. **Skutečná žádost o vstup ověřená NENÍ** — bot se přes pozvánku nepřidá,
  chce to druhý účet; seznam žádostí jen podstrčenými daty a vyřízení vůbec.
  **Dvěma účty ověřené 12. 10. 2026** (níž).
- **Ověřeno se dvěma skutečnými účty** (Pixel 7 vlastník, Pixel 5 žadatel, skupina „Plume test
  zadosti" založená v Plume bez členů): odkaz se schvalováním (limit zmizel), náhled pozvánky
  s „Požádat o vstup", hláška „žádost odeslána", pruh „1 žádost o vstup · Pixel 5" v konverzaci,
  přijetí ze seznamu žádostí (Pixel 5 skupinu hned uvidí), „Kdo se přidal" u odkazu, po odchodu
  a nové žádosti odmítnutí (počet v úpravě skupiny „Žádné nečekají").
- **Vstup přes sdílenou složku server připíše HLAVNÍMU odkazu** („Kdo se přidal · 1" na kartě
  hlavního odkazu s „přes sdílenou složku"), ne odkazu složky — ten ve výpisu odkazů skupiny
  není. Servisní zprávu o vstupu přes složku server nevloží vůbec; skupina se to dozví jen
  z počtu členů.
- **Kdo odešel, se z „Kdo se přidal" ztratí** — odkaz se schvalováním po odchodu Pixelu 5 ukázal
  „přidalo se 0". Server počítá jen ty, kdo ve skupině pořád jsou.
- **Odkazy ostatních správců ověřené skutečnými daty**: testovací bot je v testovací skupině
  správce s právem zvát a odkazy si zakládá a ruší přes Bot API (`createChatInviteLink`,
  `revokeChatInviteLink`). „Kdo se přidal" s lidmi jen sondou (přidat se odkazem chce druhý
  účet), prázdný seznam skutečně.

#### Pruh s čekajícími žádostmi

Nad konverzací skupiny nebo kanálu, kde jsou žádosti o vstup, je pruh „5 žádostí o vstup"
s tvářemi posledních žadatelů a jmény (`JoinRequestsBar`, `InviteLinkRepository
.pendingJoinRequests` → `PendingJoinRequests`). Ťuknutí otevře Žádosti o vstup, ✕ ho schová.

- **Kdo pruh vidí, rozhoduje TDLib**: `Chat.pendingJoinRequests` dostane jen správce, který
  žádosti smí vyřizovat — aplikace práva neřeší.
- **✕ schová pruh, dokud nepřibude další žádost** (pamatuje se počet, `rememberSaveable` podle
  chatu). Napořád by správce o nové žádosti nevěděl, při každém otevření chatu by zase ukazoval
  totéž.
- **Tvarem a sklem jako videochat a připnuté**, hned pod pruhem spamu — obojí čeká na
  rozhodnutí. Ve vlákně komentářů není, v tématu skupiny ano (žádosti patří skupině).
- **Skupina s tématy ho má i nad seznamem témat** — otevírá se jím, takže by správce o žádostech
  věděl až po otevření tématu. Tam je to první blok seznamu nad hledáním (`floating = false`:
  bez stínu a vlastního odsazení, plocha `surfaceContainerHigh`), ne plovoucí pruh — seznam
  témat nad sebou nic nescrolluje a sklo by nemělo co rozostřit. ✕ platí pro každou obrazovku
  zvlášť.
- **Ověřeno sondou** (podstrčený počet a žadatelé pro testovací skupinu): pruh v tématu
  General, ťuknutí na žádosti, ✕. Skutečná žádost chce druhý účet.

#### Povýšení na správce s výchozími právy

Při povýšení jsou předvyplněná výchozí práva (`AdminRights.Default`) a „Uložit" bylo zašedlé:
porovnání s `MemberDetail.adminRights` vyšlo „beze změny", protože u nesprávce nese právě tu
výchozí nabídku. **Povýšit šlo jen s upraveným přepínačem.** Povýšení je proto změna i bez
úprav (`MemberRightsViewModel.changed`). Našlo se to až povýšením bota v testovací skupině —
předchozí ověření správy členů skutečné uložení nezkoušelo.

### Sdílená média v profilu

Pod hlavičkou profilu jsou záložky: **Členové** (kde je server vydá), **Média**, **Soubory**
a **Odkazy**. Obsah jde přes `searchChatMessages` s filtrem
(`SearchMessagesFilterPhotoAndVideo` / `Document` / `Url`), ne procházením historie — sdíleného
obsahu bývá zlomek zpráv.

- **Nabízejí se jen neprázdné záložky.** Počty se čtou naráz (`getChatMessageCount` na všechny
  druhy současně) a teprve podle nich se záložky vykreslí; prázdná záložka by slibovala obsah,
  který chat nemá. Dokud počty nedorazí, nejsou žádné — záložka, která by po chvíli zmizela,
  vypadá jako chyba.
- **Profil kvůli tomu NENÍ `LazyColumn`.** Obsah záložky je sám svislý seznam a dva svislé
  scrolly do sebe Compose nezměří, když vnitřní dostane nekonečnou výšku (`Vertically scrollable
  component was measured with an infinity maximum height constraints`). Profil je proto
  `verticalScroll` sloupec (hlavička, záložky, pager) a **pager má PEVNOU výšku** = plocha bez
  záložek. Vnější scroll tím sahá přesně o výšku hlavičky a **záložky se přilepí samy**; žádný
  `stickyHeader`.
- **Hlavička se musí dát táhnout prstem.** První verze kreslila hlavičku PŘES stránky a posouvala
  ji jen `NestedScrollConnection` ze seznamu pod ní: tah přes fotku nebo údaje nedělal nic
  a u krátké záložky (tři členové) nebylo čím hlavičku sbalit. Teď ji nese vnější scroll.
- **Scroll dolů si napřed vezme hlavička** (`collapseFirst` na pageru, `dispatchRawDelta` do
  vnějšího `ScrollState` v `onPreScroll`). Nahoru to platí samo: vnější scroll dostane jen to,
  co seznam nespotřeboval, tedy až po dojetí na začátek — i u flingu. Ověřuje se `input swipe`:
  malý tah dolů v odscrollované mřížce hýbe mřížkou, hlavička zůstává sbalená.
- **Dotažení další stránky NESMÍ stát na `layoutInfo`.** Porovnání indexu poslední viditelné
  položky s počtem se u mřížky minulo a další stránka nedorazila NIKDY — u seznamu s jedinou
  položkou naopak prošlo hned, takže vada byla vidět jen na mřížce. Spouští ho proto kompozice
  jedné z posledních položek (`LoadMoreAtEnd`): skládá se, teprve když je blízko viewportu.
- **Konec pozná jen prázdná odpověď od serveru**, ne počet po filtrování: stránka samých
  mizejících fotek vyjde prázdná i uprostřed chatu.
- **Mizející a tajná média se vyřazují** stejně jako z listování v prohlížeči — trvalý přehled
  není místo pro obsah, který má po zhlédnutí zmizet.
- **Dlaždice mřížky nese sdílený prvek, ale s JINÝM klíčem než bublina v konverzaci**
  (`LocalSharedMediaFlight`, který podle `MediaRoute.fromSharedMedia` poskytuje i prohlížeč).
  Prohlížeč z dlaždice vyroste a při zavření se do ní vrátí, i na široké ploše (`inTwoPane`
  jako u bubliny — prohlížeč nahradí celý dvoupanel). **Se společným klíčem se ty dvě strany spárovaly mezi sebou** —
  tady stávalo, že „konverzace pod profilem je nesložená, takže se o klíč nic nepře", jenže při
  VÝMĚNĚ obrazovek jsou složené obě, a otevření profilu (i návrat z něj) je výměna jako každá
  jiná. Fotka proto při každém otevření profilu přelétla přes celou obrazovku z bubliny na
  dlaždici a zpátky. **Obecně: „druhá strana zrovna není složená" o sdíleném prvku nic neříká,
  protože přechod je právě ten okamžik, kdy složené jsou obě.**
- **Obrázek a video poslané jako soubor se ze záložky Soubory otevřou v prohlížeči**, jako
  v konverzaci (`opensInViewer`; co prohlížeč neumí, dostane dál systém). Prohlížeč takovou
  zprávu ukáže samotnou, bez listování — dokument ve výřezu fotek a videí není. Ověřeno na
  emulátoru (JPEG od testovacího bota, zpět vrací do záložky).
- **Ťuknutí vede tam, kam z konverzace**: médium do prohlížeče (táž `MediaRoute`), soubor se
  stáhne a předá systému (včetně průběhu v řádku a druhého ťuknutí jako zrušení), odkaz do
  prohlížeče.
- **Titulek „null" posílá server doslova** (u `share.google/…` a podobných). `ifBlank` ho
  nechytil a stál v záložce Odkazy i v bublině; texty náhledu jdou proto přes `previewText`
  (`MessageContent.kt`, testy `LinkPreviewTextTest`), který ho zahodí jako prázdný.
- **Soubory, odkazy a hudba mají nad seznamem pole hledání** (`SharedTabFrame`, `searchShared`,
  pravidlo `offersSharedSearch`, přechody `SharedMediaTab`, testy `SharedMediaTabTest`). Hledá
  server: `searchChatMessages` s dotazem a TÍMŽ filtrem jako záložka — název souboru, titulek
  skladby, text zprávy s odkazem. **Média hledání nemají**: fotka se dá najít jen podle popisku,
  který většina nemá, a „Nic nenalezeno" nad mřížkou plnou fotek by vypadalo jako chyba.
  **Pole je až od devíti položek** (`SHARED_SEARCH_MIN_COUNT`) — nad třemi soubory by jen
  ubíralo místo; rozepsané hledání se neschová. Pauza 300 ms a zrušení předchozího dotazu jako
  u členů; **stránka patří dotazu i místu** (`withNextPage` porovná dotaz a `nextFromMessageId`),
  jinak by pomalá stránka ze starého hledání vmíchala cizí výsledky.
- **Další stránka se čte od poslední VRÁCENÉ zprávy, ne od poslední ukázané**
  (`SharedMediaPage.nextFromMessageId`). Stránka samých mizejících fotek je po odfiltrování
  prázdná; pokračovat od poslední ukázané by vrátilo tutéž stránku a dotahování by se zaseklo.
- **Ověřeno na emulátoru** v chatu s Notifikátorem: „klip" v Souborech našel `klip.mp4`,
  nesmysl ukázal „Nic nenalezeno", ✕ vrátil celý seznam, „meet" v Odkazech našel zprávu podle
  textu, Hudba s pár skladbami pole nemá.
- **GIFy a kolečka jsou další dva druhy** (`SharedMediaKind.Gifs` / `VideoNotes`, filtry
  `SearchMessagesFilterAnimation` / `…VideoNote`): mřížka statických náhledů (`SharedClipsGrid`),
  u GIFu štítek, u kolečka kruh a délka. **Prohlížeč ani jedno neumí**, takže ťuknutí zavře
  profil a skočí na zprávu v konverzaci (`onOpenMessage`, táž cesta jako záložky zpráv) — tam
  se přehrají jako vždycky. Hledání nemají (není podle čeho), mizející kolečko se vyřazuje.
- **Hlasovky jsou vlastní záložka** (`SharedMediaKind.Voice`, `SearchMessagesFilterVoiceNote`,
  `SharedVoiceList`): řádek s tlačítkem, délkou (při přehrávání pozice / délka) a kdo a kdy.
  **Hrají přímo v záložce**, tímtéž `VoicePlayer` a klíčem (`VoiceKey(chatId, messageId)`) jako
  v konverzaci, takže po návratu do chatu běží dál ve své bublině. Poslech se nehlásí jako
  přečtený (`reportListened` je věc konverzace). Hledání nemají, mizející se vyřazují.
  - Ověřeno na emulátoru (profil „Pixel 7 Pro", sedm záložek): hlasovka „0:04 · Pondělí",
    přehrání s pauzou a během „0:02 / 0:03". **Sedm záložek se nevešlo a řada se posouvala**,
    vybraná se posunula do viditelné části — tím je ověřený i posuvný režim řady.
- **Společné skupiny a podobné kanály jsou záložky se seznamem chatů** (`ProfileTab.CommonGroups`
  / `SimilarChannels`, `getGroupsInCommon` / `getChatSimilarChats`, nejvýš 100). Čtou se
  samostatně po načtení profilu a **přidávají se NA KONEC řady**: záložka vložená doprostřed by
  posunula tu, na které uživatel zrovna stojí. Selhání se nehlásí, záložka se jen neukáže.
- **Víc než čtyři záložky mají každá svou šířku** (`ProfileTabSelector`, vlastní `Layout`):
  když se vejdou, rozdělí se zbylé místo rovným dílem, když ne, řada se posouvá do strany
  a vybraná se po změně posune do viditelné části. První verze jen posouvala — pět záložek se
  vešlo a vpravo zela mezera, což vypadalo nedodělaně. Do čtyř zůstávají váhy s rozšířenou
  vybranou, jak byly.
- **Ověřeno na emulátoru** (14. 10. 2026, účet …527, profil „Pixel 7 Pro"): záložky Média,
  Odkazy, GIFy, Kolečka, Skupiny přes celou šířku i po výběru poslední; kulaté kolečko s délkou,
  GIF se štítkem, ťuknutí na obojí skočilo na zprávu; pět společných skupin s počty, ťuknutí
  otevřelo skupinu. Řada, která se nevejde, se ověřila se záložkou Hlasovky (sedm záložek).
- **Záložka Podobné je u kanálu i u bota** (`ProfileTab.Similar(bots)`, `getChatSimilarChats`
  / `getBotSimilarBots`). U bota stojí za Společnými skupinami, má ikonu robota místo megafonu
  a pod jménem počet uživatelů za měsíc (`UserTypeBot.activeUserCount`; nula = server nevydal,
  řádek se nekreslí).
  - **Otevření z Podobných se TDLib hlásí** (`openChatSimilarChat`, u bota `openBotSimilarBot`;
    `ChatProfileViewModel.openSimilar`) — dokumentace to po klientovi chce, server podle toho
    ladí doporučení. Selhání nic nezastaví, jen se zapíše. Bot nemusí mít chat založený, proto
    se otevírá přes `privateChat` jako člen skupiny.
  - **Ověřeno na emulátoru** (23. 10. 2026): BBC News | Русская служба → Podobné (Медуза, Дождь…
    s počty odběratelů), ťuknutí otevřelo kanál a TDLib oznámení přijal (žádné varování v logu);
    @wallet → Podobné s deseti boty a „1 844 403 uživatelů měsíčně", ťuknutí otevřelo bota.
    Durger King podobné boty nemá (server vrátí prázdný seznam) a záložka se neukáže.
- **Ukázky mini aplikace** (`getBotMediaPreviews`, `ProfileRepository.botMediaPreviews`,
  `BotAppPreviews`): u bota s hlavní mini aplikací (`hasMainWebApp`) řada náhledů na výšku pod
  tlačítky, jako v obchodě s aplikacemi. Bez ukázek se blok nekreslí; ostatní boti se ani
  neptají.
  - **Obsah je `StoryContent`** — fotka nebo video jako u příběhu, takže převod je tentýž
    (`Photo.toMedia`, `StoryVideo.toMedia`). Živé vysílání ukázka být nemůže a vynechá se.
  - **Ťuknutí otevře prohlížeč přes celou obrazovku** (`AppPreviewViewer`): posun do stran,
    „N z M", video ve smyčce jen na právě ukázané stránce. Je to dialog, ne routa — k ukázkám
    se nikam dál nechodí. **Dialog nesahal pod systémové lišty** a profil nad a pod ukázkou
    prosvítal; společné `DialogProperties` to neumí, proto `FullScreenDialog` (`core/ui`,
    Android `decorFitsSystemWindows = false`, iOS `usePlatformInsets = false`), jako zámek aplikace.
  - **Ověřeno na emulátoru**: @wallet ukázal tři videa, prohlížeč „2 z 3" přes celé okno
    a video hrálo. iOS a desktop jen přeložené.
- **`mailto:` se v adrese neukazuje** (`displayUrl`, testy `SharedMediaTest`). Filtr odkazů bere
  i e-maily a schéma u nich nic neříká; `https` od `tg` odlišit potřeba je, takže se zahazuje
  jen ono.

#### Kalendář médií

Nad mřížkou Médií je tlačítko **Kalendář** (`SharedTabFrame.onOpenCalendar`, od stejného počtu
jako hledání v jiných záložkách — `offersMediaCalendar`). Otevře sheet s měsíci od nejnovějšího
(`MediaCalendarSheet`); den s fotkou nebo videem je kruh s náhledem první fotky dne, ťuknutí
zavře kalendář a otevře na ní prohlížeč, ve kterém se listuje dál jako z mřížky. Data
`getChatMessageCalendar` (`mediaCalendar`), logika `core/telegram/MediaCalendar.kt`
(`mergeCalendarDays`, `calendarMonths`, testy `MediaCalendarTest`).

- **Každý den nese PRVNÍ zprávu dne**, ne poslední, a dny jdou od nejnovějšího. Další stránka se
  proto čte od zprávy posledního (nejstaršího) dne.
- **Poslední den stránky bývá neúplný** a na další stránce přijde znovu se zbytkem — počty se
  sečtou a první zprávou zůstane ta starší.
- **Den se počítá místně** z času zprávy. TDLib dělí dny podle volby `utc_time_offset`, kterou
  Plume nenastavuje, takže dva jeho dny můžou být jeden místní; sečtou se.
- **Měsíce bez médií se vynechávají** — chat s fotkami jednou za čas by jinak byl hlavně prázdné
  stránky. **Týden začíná pondělím** v obou jazycích (angličtina v aplikaci není americká).
- **Nadpis měsíce chce v češtině 1. pád** („říjen 2026"), ne 2. pád za dnem (`month_N`, „října") —
  proto vlastní řada `month_standalone_N` a `monthTitle`.
- **Skryté médium (spoiler) náhled v kalendáři neukáže**, kruh zůstane tónovaný; odkryje se až
  v prohlížeči, jako z dlaždice. Kruh bez náhledu mají i videa, ke kterým server náhled nedal.
- **Ověřeno na emulátoru** v chatu s Notifikátorem (67 médií od 20. 9.): říjen a září s náhledy,
  ťuknutí na 10. 10. otevřelo prohlížeč na fotce „sobota 19:21 · 66 z 67". **Dotahování starších
  měsíců ověřené NENÍ** — celá historie se vešla do první stránky.
- **Další stránka se žádá podle místa, odkud se pokračuje** (`nextFromMessageId`), ne podle
  počtu měsíců. Stránka rušného chatu přinese jen pár dnů TÉHOŽ měsíce; s počtem měsíců jako
  klíčem efektu se další stránka už nevyžádala a nejstarší den zůstal napůl spočtený. Přišlo se
  na to v kalendáři konverzace (odkazy kanálu, 250 denně), kde stejný kód je — viz
  `konverzace-pohyb.md`, „Kalendář zpráv".

#### Rychlé rolování v Médiích

U víc než 90 médií (`offersFastScroll`) má mřížka Médií u pravého okraje úchyt
(`MediaFastScroller`): tahem jde dojet kamkoli do historie, bublina ukazuje měsíc pod prstem
a puštění na to místo skočí. Pozice dává `getChatSparseMessagePositions` (`sharedMediaPositions`,
`SharedMediaPositions`, testy `SharedMediaPositionsTest`), skok načte okno
(`SharedMediaTab.withWindow`, testy v `SharedMediaTabTest`).

- **Jen pro sdílený obsah, ne pro konverzaci.** API nebere prázdný filtr
  (`searchMessagesFilterEmpty` je výslovně nepodporovaný) — je stavěné pro „Shared Media scroll".
- **Pozice jsou ŘÍDKÉ**: server vydá nejvýš 2000 bodů po celé historii (pořadí od nejnovější,
  id, datum). Úchyt i bublina z nich čtou bez načítání; puštění skočí na nejbližší bod. Poloha
  úchytu při běžném rolování je odhad — mezi body se interpoluje podle id
  (`estimatePosition`), id zpráv rostou s časem.
- **Poloha je podíl v CELÉ historii** (`totalCount`), ne v načteném seznamu — ten je jen malý
  kus a úchyt by s každou stránkou poskočil.
- **Skok načte okno, jako skok na zprávu v konverzaci**: `searchChatMessages` se záporným
  `offset` vrátí cílovou zprávu, 15 novějších nad ní a zbytek stránky starších. Seznam se tím
  NAHRADÍ; ke starším se pokračuje jako vždy, k novějším dotahováním při rolování nahoru
  (`withNewerPage`, konec = stránka bez novějších). Blízko začátku (pozice < 15) se jen načte
  seznam od nejnovější. Limit stránky musí být větší než `-offset`, jinak by TDLib nevrátil
  nic staršího.
- **Dotahování novějších se zakládá znovu s každou stránkou** (`LaunchedEffect` klíčovaný
  první dlaždicí a `canLoadNewer`) a hned se ptá, jestli je mřížka pořád u začátku. Dokud hlídal
  jen ZMĚNU „u začátku", zůstala mřížka po rychlém rolování nahoře i po dotažení a další stránka
  se nenačetla (první pokus na emulátoru).
- **Dokud okno k nejnovější nesahá, mřížka si nechá zbytek tahu i hodu nahoru**
  (`NestedScrollConnection` kolem mřížky). Jinak u začátku okna, než novější dorazí, rozbalil
  hlavičku profilu, další tahy šly po ní a dotahování se zastavilo uprostřed historie (druhý
  pokus na emulátoru). Hlavička se rozbalí až nad nejnovější fotkou.
- **Dotyk bere jen úchyt**, ne pruh přes celou výšku: překrývající se sourozenci si dotyk
  nedělí, takže pruh by u pravého okraje mřížku přestal rolovat i ťukat. **Úchyt je vidět jen
  při rolování a tažení** (a 1,5 s po něm); trvale by ležel přes pravý sloupec.
- **Při tažení tiká s každým novým měsícem** (`tick`, procházení po dílcích).
- **Ověřeno na emulátoru** v kanálu se 181 médii: úchyt při rolování, bublina „September 2026"
  při tažení, skok doprostřed (prohlížeč pak ukázal „62 of 181" z 1. října), skok na konec
  (neúplná poslední řada se zaobleným rohem) a rolování odtud nahoru přes celou historii až
  k nejnovějším. **Neověřeno**: tik při změně měsíce (kanál má média z jediného měsíce)
  a dlouhá historie přes roky; iOS jen přeložené.

### Hudba v profilu

Skladby, které si člověk dal do profilu (`getUserProfileAudios`), jsou v jeho profilu jako blok
**Hudba v profilu** pod údaji (`ProfileMusic`). První skladbu nese `UserFullInfo.firstProfileAudio`
a ukáže se hned (`ChatProfile.firstProfileAudio`), celý seznam (nejvýš 100) se dočte zvlášť.
Nejvýš tři řádky, pak „Zobrazit všech N skladeb".

- **Přehrává fronta hudby se SEZNAMEM z profilu** (`MusicQueue.togglePlaylist`): skladba
  v profilu není zpráva, takže sousedé se nehledají v chatu (`musicNeighbors`), ale berou se ze
  seznamu. Po dohrání naváže další skladba profilu, notifikace má „další" i „předchozí"
  a ťuknutí na ni otevře chat s tím člověkem. Klíč skladby je `VoiceKey(chat, -fileId)`
  (`profileAudioKey`) — záporný, aby se nepotkal s id zprávy v tomtéž chatu.
- **Seznam se dočte znovu, když se změní první skladba** (`syncProfileAudios`, podle souboru).
  **Otevřený profil cizího člověka se ale po změně jeho hudby NEaktualizuje sám** — TDLib
  update `UserFullInfo` nepošle (ověřeno: …527 skladbu odebral, profil na …029 ji ukazoval dál,
  po znovuotevření zmizela).
- **Profil je jen ke čtení.** Vlastní profil se v aplikaci neotevírá (Uložené zprávy profil
  nemají), takže vlastní hudba se spravuje v úpravě profilu (`docs/poznatky/nastaveni.md`,
  „Hudba ve vlastním profilu" — i přidání ze souboru a pořadí) a přidává i z nabídky nad
  zprávou se skladbou („Přidat do profilu",
  `MessageMenuAction.ToggleProfileAudio`; jestli už v profilu je, zjistí `isProfileAudio` při
  otevření nabídky). V tajném chatu se nenabízí. `addProfileAudio` bere `InputFileId` — soubor
  už na serveru je, TDLib ho nenahrává znovu.
- **Kdo hudbu vidí**, řídí Soukromí účtu → „Hudba v profilu" (`PrivacySetting.ProfileAudio`).
- **Ověřeno na dvou emulátorech s testovacími účty** (`Plume_Barvy` = …527, `Plume_A11` = …029):
  M4A poslaná do Uložených odešla jako skladba, „Přidat do profilu" → snackbar, nabídka pak
  nabízí „Odebrat z profilu"; v úpravě profilu skladba hraje (0:01 / 0:06); na druhém účtu
  v profilu „Pixel 5" blok „Music in profile", skladba hraje s přehrávačem MediaStyle
  v notifikaci; odebrání křížkem v úpravě profilu ukáže prázdnou sekci s radou. **Neověřené:**
  víc skladeb (navazování, „předchozí"), obal alba, iOS.

### Profil firmy

Firemní účet (Telegram Business) má v údajích profilu **Otevírací dobu** a **Adresu**
(`ChatProfile.business`, doména `core/telegram/Business.kt`, testy `BusinessTest`, UI
`BusinessInfo.kt`). Řádek doby říká stav jednou větou („Otevřeno · do 17:00", „Zavřeno ·
otevírá zítra v 9:00") a ťuknutí otevře sheet s dny v týdnu, dnešek tučně. Adresa otevře mapy
platformy — podle souřadnic, a když je firma nezadala, hledáním adresy (`mapsUrl` /
`mapsSearchUrl`).

- **Bere se `localOpeningHours`, ne `openingHours`.** Ta druhá je v časovém pásmu firmy;
  „otevřeno do 17:00" musí sedět s hodinami v telefonu. Sheet to říká větou pod tabulkou.
  `localOpeningHours` se mění bez `updateUserFullInfo`, takže platí pro chvíli načtení profilu.
- **Stav se počítá z otevírací doby, ne z `nextOpenIn` / `nextCloseIn`** — ty jsou snímek
  z doby načtení a TDLib je neobnovuje. Výpočet (`stateAt`) je čistá funkce nad minutami týdne:
  úseky přes půlnoc (So 20–24 a Ne 0–2) spojí do jednoho, v neděli večer najde pondělní ráno.
- **Úvodní stránka firmy (`startPage`) v profilu není** — patří do prázdné konverzace jako
  popis bota (`BusinessIntroPage`, viz níž). Uvítací a nepřítomnou zprávu TDLib cizím nevydá.
- **Ověřeno na emulátoru pokusným buildem** (vymyšlená doba a adresa u testovacího bota,
  necommituje se): v úterý ve 12:14 v polední pauze „Zavřeno · otevírá v 13:00", sheet
  s dvěma úseky po–pá, sobotou a zavřenou nedělí, adresa otevřela Google Mapy. Skutečný firemní
  účet ověřený NENÍ — v kontaktech žádný není.

#### Úvodní stránka firmy v prázdném chatu

Prázdný chat s firemním účtem ukazuje místo „Zatím tu nic není" stránku, kterou si firma
nastavila (`businessInfo.startPage`: nadpis, text, uvítací samolepka) — `BusinessIntroPage`,
`ConversationRepository.businessIntro`, rozhodnutí `showsBusinessIntro` (testy v `BotStartTest`).

- **Skladba jako profil bota** (`BotIntroPage`): tvář a jméno nahoře, hlavička svůj avatar
  i hledání schová (`showsIntroPage`). Samolepka je nad nadpisem, jak ji kreslí oficiální
  klienti; ťuknutí na ni nic nedělá, není to zpráva.
- **Tentýž důkaz prázdnoty jako u bota**: až nad načtenou prázdnou historií (`reachedStart`,
  ne během načítání), jinak by stránka u každého chatu na okamžik blikla.
- **Bot má přednost** — jeho stránka nese i tlačítko Start. Vlastní účet (Uložené zprávy) se
  neptá.
- **Ověřeno na emulátoru s podstrčenou stránkou** (každému soukromému chatu, prázdný chat
  s kontaktem, necommituje se): tvář, jméno, animovaná samolepka, nadpis a text, hlavička bez
  avataru. **Skutečný firemní účet s úvodní stránkou ověřený NENÍ** — v kontaktech žádný není.

### Dárky v profilu

Profil člověka a kanálu má záložku **Dárky** (`ProfileTab.Gifts`, `ProfileGifts.kt`): mřížka
dlaždic se samolepkou dárku, sběratelský na pozadí ve svých barvách s pořadovým číslem, běžný
s cenou ve hvězdách, připnutý s připínáčkem. Ťuknutí otevře sheet s tou samou kartou, jakou
kreslí dárek ve zprávě (`GiftCard`), a pod ní „Obdrženo 14. října".

- **Záložka se ukáže, jen když `giftCount > 0`** (`UserFullInfo` / `SupergroupFullInfo`) a první
  stránka něco vrátí. Přidává se na KONEC řady jako společné skupiny — dorazí vlastním dotazem
  a doprostřed by posunula záložku, na které uživatel stojí. Selhání se nehlásí.
- **Cizím TDLib vydá jen dárky vystavené v profilu**, `excludeUnsaved` vynutí sám. Vlastní profil
  (Uložené zprávy) a skupiny dárky nemají; spravovat vlastní dárky (vystavit, připnout,
  proměnit, zakládat sbírky) Plume neumí, to je pro oficiální aplikaci.
- **Sbírky jsou čipy nad mřížkou** (`getGiftCollections`, `ProfileRepository.giftCollections`),
  jako alba příběhů: „Vše" a každá neprázdná sbírka s počtem dárků. Výběr čte dárky znovu
  s `collectionId` (`selectGiftCollection`); odpověď ke sbírce, kterou uživatel mezitím přepnul,
  se zahodí. Sbírka, ze které cizí nic nevidí, ukáže „Žádný dárek z téhle sbírky není v profilu
  vystavený" (počet ve sbírce započítává i nevystavené).
  - **Záložka Dárky musí přežít prázdnou mřížku.** Při přepnutí sbírky jsou položky na chvíli
    prázdné; s podmínkou jen na položky záložka zmizela a pager uskočil na Příběhy (ověřeno na
    emulátoru při návratu na „Vše"). Záložka proto zůstává, dokud jsou sbírky nebo vybraná sbírka.
  - **Ověřeno na emulátoru**: `getGiftCollections` u kanálu @durov a dalších velkých kanálů
    prošel a vrátil prázdný seznam (sbírky se zatím skoro nepoužívají), čipy se pak nekreslí.
    Čipy, přepnutí, prázdná sbírka a návrat na „Vše" s podstrčenými sbírkami. **Skutečná
    sbírka s dárky ověřená NENÍ.**
- **Stránky po 30 přes `nextOffset`**, dotahuje je `LoadMoreAtEnd` jako sdílená média.
  `receivedGiftId` má jen příjemce, takže klíč dlaždice se u cizího profilu skládá z id dárku,
  dárce a data; dvojí výskyt téhož klíče se zahodí.
- **Samolepky v mřížce STOJÍ** (`playing = false`), hraje jen ta v sheetu. Desítky animovaných
  samolepek naráz by zbytečně zatěžovaly.
- **Pozadí sběratelského dárku určuje Telegram, ne motiv**, proto má číslo a připínáček na něm
  bílou barvu napevno.
- **Dárek bez `background` shodí wrapper** — týká se i `getReceivedGifts`. Na Androidu a desktopu
  to obchází `TdlibJsonRepair`, **na iOS ne** (viz „Odpovědi TDLib, které wrapper nerozebere").
- **Ověřeno na emulátoru** na kanálu @durov (jen čtení): záložka, sběratelské i běžné dárky,
  připnuté, dotahování dalších stránek, sheet sběratelského dárku (model, symbol, pozadí)
  i běžného (dárce, vzkaz, hodnota). Profil člověka s dárky a iOS ověřené nejsou.

### Návrh profilové fotky

Člověku jde z profilu navrhnout profilovku (⋮ → „Navrhnout profilovou fotku",
`suggestUserProfilePhoto`) a návrh od protějšku jde v konverzaci nastavit. Obdoba návrhu data
narození (`docs/poznatky/nastaveni.md`, „Narozeniny").

- **Odeslání jde stejným výběrem a ořezem jako vlastní profilovka** (`ProfilePhotoCrop`,
  `PhotoCropOverlay`, i video jako animovaná profilovka). Ořez je překryv přes celou obrazovku,
  takže profil kolem `Scaffold` dostal `Box`. Nabízí se jen člověku (ne botovi a ne sobě).
- **Přijetí je oznámení s fotkou v kruhu** (`ChatMessage.suggestedPhoto`, `SuggestedPhotoNote`):
  „Jana navrhuje profilovou fotku" a „Nastavit" — tak, jak by fotka jako profilovka vypadala.
  Vlastní návrh ukazuje fotku bez „Nastavit". Na ukázku se bere nejmenší velikost aspoň 320 px.
- **Nastavení stáhne fotku a nahraje ji znovu** (`acceptSuggestedPhoto`): je to cizí soubor,
  `InputChatPhotoPrevious` bere jen fotky vlastního účtu. Animovaná se nahraje jako video se
  snímkem pro statickou podobu (`mainFrameTimestamp` z návrhu), jinak největší velikost.
  Mění se až po potvrzení v dialogu — profilovku vidí i ostatní.
- **Ověřeno mezi telefony** (Pixel 7 → Pixel 5): výběr fotky, ořez, „Návrh fotky odeslán";
  Pixel 5 (anglicky) ukázal fotku v kruhu s „Pixel 7 Pro suggests a profile photo" a „Set",
  po potvrzení „Profile photo set" a profil na Pixelu 7 pak ukázal novou fotku. Odesílatel
  vidí „Navrhl(a) jsi profilovou fotku" bez „Nastavit". **Animovaná profilovka jako návrh
  ověřená není.** Testovací účet …527 má od té doby tuhle fotku (výhled na město).


### Úprava kontaktu

⋮ v profilu kontaktu → „Upravit kontakt" (`EditContactSheet`): jméno, pod kterým ho má
uživatel uložené, poznámka a vlastní fotka. Nic z toho kontakt nevidí, ostatní taky ne. Doména
`ChatProfile.contact` (`ContactDetails`), repository `editContact` a `setPersonalPhoto`.

- **Jméno i poznámka jdou jedním voláním `addContact`** s `ImportedContact.note`. Kontakt
  podle id se tím přepíše a číslo může zůstat prázdné. `setUserNote` zvlášť potřeba není.
- **Poznámka se posílá jen změněná** (`note = null` = nechat). Pole upravuje holý text, takže
  nezměněná poznámka si nechá formátování, které jí dal jiný klient. V profilu se kreslí
  i s formátováním (`RichText`), hned za bio.
- **Pole poznámky má jen server, který poznámky umí** (`user_note_text_length_max` > 0). Délka
  pole se řídí touž volbou.
- **Vlastní fotka jde stejným výběrem a ořezem jako návrh fotky** (`ProfilePhotoCrop`); ořez
  pozná podle `cropForPersonalPhoto`, kam výsledek patří (`confirmCrop`). **Fotka sheet zavře**:
  ořez je překryv nad profilem a modální sheet je okno nad ním, ořez by zůstal schovaný. Fotka
  se ukládá hned, nečeká na Uložit.
- **Vlastní fotku TDLib vrací místo té, kterou si kontakt nastavil** (`User.profilePhoto`
  i `Chat.photo`), takže ji profil, seznam chatů i konverzace ukážou samy. Že nějaká je, říká
  jen `UserFullInfo.personalPhoto`. Sheet pak nabízí „Změnit" a „Odebrat".
- **Tlačítko Uložit po otevření klávesnice vyjede nahoru**, přímo pod pole, na které se
  pak ťuká. Při testu přes `adb` tak druhé ťuknutí trefilo Uložit místo pole příjmení: souřadnice
  se berou z výpisu PO otevření klávesnice, ne ze snímku před ním.
- **Ověřeno na emulátoru** (…527, kontakt „Pixel 7 Pro" = …029):
  - Přejmenování na „Pixel 7 Testovaci" s poznámkou se v profilu hned ukázalo.
  - Vlastní fotka z výběru a ořezu se nastavila a sheet pak nabízel „Změnit" a „Odebrat".
  - Odebrání fotky vrátilo iniciály.
  - Návrat jména a smazání poznámky (prázdná poznámka) vrátily kontakt do původního stavu.
  - **Neověřené:** iOS a poznámka s formátováním od jiného klienta.

### Sběratelská jména a čísla z Fragmentu

Ťuknutí na řádek se jménem, když je mezi jmény sběratelské, nebo na číslo +888 otevře sheet
„Sběratelské položky" (`CollectibleInfoSheet`): u každé položky kdy a za kolik se koupila
na Fragmentu (v TONech a přibližně v dolarech) a tlačítko na její stránku
(`getCollectibleItemInfo`, `CollectibleItem`, `CollectibleInfo`; testy `CollectibleTest`).

- **TDLib sběratelská jména neoznačuje.** Pozná se to rozdílem: vlastní jméno je jen jedno
  (`usernames.editableUsername`), každé další zapnuté (`activeUsernames`) se dá jen koupit
  (`ChatProfile.collectibleUsernames`). Vlastní jméno může chybět — pak je sběratelské i to
  hlavní. Oficiální klient ho bere stejně.
- **Číslo je sběratelské, když začíná 888** (anonymní čísla jsou jen z Fragmentu); TDLib ho chce
  bez „+".
- **Údaje se čtou až po ťuknutí**, pro každou položku zvlášť a souběžně; položka, o které server
  nic neřekne, má větu místo ceny. Většina profilů žádnou sběratelskou položku nemá, takže se
  s profilem nic navíc nenačítá.
- **Částka v kryptoměně je v nejmenších jednotkách** (TON má devět desetinných míst,
  `cryptoAmountParts` ořízne koncové nuly); cena v měně jde přes `Price` jako u faktur.
- **Ověřeno na emulátoru**: kanál @durov (pět sběratelských jmen pod hlavním) ukázal pět bloků
  s daty a cenami, „Zobrazit na Fragmentu" otevřel prohlížeč. Číslo +888 ověřené není (žádný
  dostupný profil ho neukazuje).
