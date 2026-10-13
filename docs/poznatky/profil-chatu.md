# Profil chatu

Poznatky k jedné oblasti aplikace. Obecná pravidla projektu a rejstřík ostatních souborů jsou
v [CLAUDE.md](../../CLAUDE.md); poučení z opravy v téhle oblasti patří sem.

## Profil chatu

Ťuknutí na hlavičku konverzace otevře profil (`ChatProfileRoute`, `features/profile`): velká fotka
a jméno, podtitul (dostupnost / počet členů / „Bot"), dlaždice Ztlumit a Hledat, údaje
(uživatelské jméno, telefon, bio nebo popis) a u skupiny členové s rolí.

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
- **Údaje i obsah záložek jsou bloky zaoblených řádků** (`groupItemShape`, `surfaceContainerLowest`,
  spára 2 dp), stejný jazyk jako seznam chatů. Dřív byly údaje karta `SettingsGroup`
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

- **Méně časté akce jsou za ⋮ v liště** (`ProfileMenu`): kopírovat odkaz (veřejné jméno, jinak
  pozvánka — tu server vydá jen správci s právem zvát), zablokovat / odblokovat uživatele či
  bota a smazat chat / opustit skupinu nebo kanál. Dlaždice nesou to, kvůli čemu se do profilu
  chodí; tohle se dělá jednou za čas a dvě z toho jsou nevratné, s potvrzením a stejnými texty
  jako v seznamu chatů. Odblokovat je bez potvrzení.
- **Po smazání nebo odchodu se zavře všechno, co chat ukazuje** (`closeChat` v `ChatsStack.kt`,
  test v `ChatsStackTest`): konverzace, profil a prohlížeč médií, v hlavním seznamu i archivu.
- **Blokuje se hlavní seznam** (`BlockListMain`) — ten brání psát i volat. Stav se čte
  z `UserFullInfo.blockList` a po akci přepíše lokálně: profil `UpdateUserFullInfo` neodebírá.
- **Opustit se nabízí jen tam, kde uživatel je** (`isMember` z vlastního postavení) — z chatu
  otevřeného z globálního hledání není odkud odejít.
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
- **`mailto:` se v adrese neukazuje** (`displayUrl`, testy `SharedMediaTest`). Filtr odkazů bere
  i e-maily a schéma u nich nic neříká; `https` od `tg` odlišit potřeba je, takže se zahazuje
  jen ono.
