# Wear OS

Samostatná aplikace pro hodinky (`client/wearApp`). Plán a co zbývá: `docs/roadmap.md`, „Wear OS".

## Skladba

- **Stojí jen na `:client:core`** (a přes něj `:client:resources`). Vrstvu kolem TDLib dostane
  celou z `coreModule()` (`WearApplication`); UI telefonu (`shared`, `appModule()`) ani jeho
  knihovny do APK nejdou. TDLib se startuje s procesem, stejně jako na telefonu.
- **UI je Compose for Wear OS 1.7** (`androidx.wear.compose:compose-material3`), ne M3
  z `shared`: kulatý displej má vlastní komponenty (`AppScaffold`, `ScreenScaffold`,
  `TransformingLazyColumn`, `TitleCard`). Texty jsou v `:client:resources` s prefixem `wear_`.
- **Hook `TdlibJsonRepair`** dává plugin `plume.tdlib-android` jako u telefonu (CLAUDE.md:
  každý vstupní bod s TDLib). Ověřeno `dexdump -d` nad APK hodinek.
- **`applicationId` je stejné jako telefon** (`eu.tintera.plume`): Wear OS aplikace jde v Play do
  téhož záznamu jen se stejným id. Třídy mají vlastní balíček `eu.tintera.plume.wear`.
- **ABI jen hodinek** (`arm64-v8a`, `armeabi-v7a`): TDLib má na ABI přes 15 MB a x86 hodinky
  nepotřebují. Debug APK má 83 MB.
- **`minSdk` 30** (Wear OS 3), `com.google.android.wearable.standalone = true`.

## Vzhled (M3 Expressive)

Podle [Apply M3 Expressive](https://developer.android.com/design/ui/wear/guides/get-started/apply);
dřív už bylo `EdgeButton`, `TimeText`, scaffoldy a číslice na dlaždici.

- **Položky seznamů se u okraje displeje zmenšují a morfují** (`rememberTransformationSpec`):
  komponenty s parametrem `transformation` (`TitleCard`, `Button`, `ListHeader`) dostanou
  `SurfaceTransformation(spec)` a `Modifier.transformedHeight(this, spec)`, vlastní obsah
  (bublina zprávy, poznámka, řádek s `CompactButton`) `Modifier.transformedItem` z
  `ListTransformations.kt` — dvě vrstvy `graphicsLayer`, kontejner a obsah, protože na jedné by
  průhlednost obsahu přepsala tu kontejneru. **Kontejner u okraje i ořízne bočními šikminami**:
  bublina, která sahá ke kraji položky (odchozí vpravo), má v horní a dolní třetině displeje
  zkosené rohy. Tak to knihovna dělá i kartám, chyba to není. **QR kód při přihlášení
  se netransformuje** — zmenšený by ho čtečka nemusela přečíst.
- **Barvy z ciferníku** (`dynamicColorScheme(context)`, Wear OS 6+; jinak výchozí `ColorScheme()`
  Wear M3, vrací `null`). Vlastní výběr barvy jako na telefonu (`ThemeColor`) hodinky nemají —
  barvu tam volí ciferník. Dlaždice ji mají samy: `materialScope` v ProtoLayout bere dynamické
  barvy ve výchozím nastavení. **Neověřené**: emulátor `Plume_Wear` je Wear OS 5 (API 34),
  `dynamicColorScheme` tam vrací `null`.
- **Animované tvary jen u ikonových tlačítek** (`shapes = IconButtonDefaults.animatedShapes()`:
  přehrát v bublině hlasovky, tlačítko nahrávání). `Button`, `CompactButton` ani karty ve Wear
  M3 1.7 tvarové páry nemají (jen `shape`), animované tvary má ještě `TextButton`, který hodinky
  nepoužívají.
- **Potvrzovací dialogy** (`SuccessConfirmation` / `FailureConfirmation` v `Confirmations.kt`,
  nad `SuccessConfirmationDialog` / `FailureConfirmationDialog`): velká ikona, pár slov po
  obvodu, samy zmizí. Po vyčištění mezipaměti („Vyčištěno", kolik se uvolnilo zůstává pod
  tlačítkem), po odeslání hlasovky („Odesláno" / „Neodesláno", po zmizení se obrazovka zavře —
  dřív se zavřela hned a nezdar šel jen do logu) a při chybě v konverzaci (neodeslaná odpověď,
  hlas v anketě, hlasovka, která nejde přehrát). **Chyba v konverzaci byla poznámka na konci
  seznamu** — při hlasování ve starší anketě nebo přehrávání starší hlasovky odrolovaná mimo
  displej, takže ji nikdo neviděl. **Text po obvodu musí být krátký** (vlastní řetězce
  `wear_confirm_*`), dlouhá věta se do oblouku nevejde.
- **Ověřeno na emulátoru 2026-10-24** (anglicky): zmenšování karet, tlačítek a bublin u horního
  i dolního okraje, „Cleared" po vyčištění mezipaměti, stisk tlačítka nahrávání (kruh → zaoblený
  čtverec, `input motionevent DOWN`), „Sent" po hlasovce do chatu Notifikátora. Dialog nezdaru
  vyvolaný není (chyba se na emulátoru neudělá), čeština v oblouku nekontrolovaná.

- **Prázdné a chybové stavy s ikonou** (`MessageScreen(text, icon, error)`): ikona 32 dp nad
  textem (prázdný seznam chatů, žádné reakce, chybějící mikrofon; chyba načtení a přihlášení
  v chybové barvě). Holý text uprostřed vypadal jako nedodělek. Na emulátoru nevyvolané.

## Přihlášení

- **Telefonní číslo se na hodinkách přeskočí:** na `WaitPhoneNumber` se rovnou volá
  `startQrLogin()`. Kód z SMS, e-mail a registrace obrazovka nepíše a nabídne návrat k QR kódu
  (`restartLogin()`).
- **QR kód má 0,62 kratší strany displeje a na začátku je svisle uprostřed.** Do kulatého
  displeje se vejde čtverec o straně 1/√2 průměru; první verze (150 dp pod nadpisem) měla spodní
  roh i s pozičním čtvercem uříznutý a čtečka ho nemusela najít. Bílý okraj kolem kódu, na
  černém pozadí hodinek by ho čtečka nenašla. Odkaz po vypršení vymění TDLib sám.
- **Heslo dvoufázového ověření systémovou klávesnicí** (`RemoteInput` přes `androidx.wear:wear-input`):
  Compose for Wear OS textové pole nemá. Klávesnice heslo nemaskuje — jiná cesta na hodinkách není.
- **Ověřeno na emulátoru 2026-10-24:** sken telefonem (Nastavení → Zařízení → Připojit zařízení),
  `AuthorizationState -> Ready`, pak seznam chatů. Heslo nevyzkoušené (účet ho nechtěl).
- **Odhlášení je v Nastavení hodinek** (`SettingsScreen`, cesta `settings`): `CompactButton`
  „Nastavení" hned pod nadpisem seznamu chatů, na obrazovce „Odhlásit" a `AlertDialog` z Compose
  for Wear OS s křížkem a fajfkou. **Menu v liště Wear OS nemá** — vedlejší akce patří na začátek
  nebo konec seznamu. Napřed bylo odhlášení na konci seznamu chatů, a ten je dlouhý: uživatele by
  nenapadlo tam rolovat. Bez toho by hodinky šly odhlásit jen z telefonu. Odhlásí
  jen sezení hodinek (`TelegramSession.logOut` v `APP_SCOPE` — obrazovka po odhlášení zmizí);
  automat pak sám přejde k QR kódu, notifikace smaže `NotificationUpdate.Cleared`, dlaždice
  a komplikace dostanou stav odhlášeno a zrcadlení z telefonu se zase zapne.
  **Ověřené jen po dialog a zrušení** — emulátor je na skutečném účtu a znovu ho přihlásí jen
  uživatel QR kódem; samotné odhlášení stojí na `logOut` z `core` jako na telefonu.

## Seznam chatů

- `ChatRepository.chats()` jako na telefonu; repository seznam po přihlášení načte sama.
  Karta: fotka chatu a název, náhled poslední zprávy (`UiText.resolve()`), v rohu odznak
  nepřečtených, a když nic nečeká, čas poslední zprávy (dnes hodina, jinak datum).
- **Odznak nepřečtených** (`UnreadBadge`): vyplněná pilulka s počtem v `primary`, nad 99 „99+".
  Holé číslo v rohu karty splývalo s časem u ostatních chatů a nepřečtený chat nebyl na první
  pohled vidět. Ztlumený chat má odznak tlumený (`surfaceContainerHigh`), ručně označený jako
  nepřečtený bez počtu jen tečku. Ověřeno na emulátoru 2026-10-24 (chat služby Telegram s 1).
- **„Píše…" a připnutí v kartě** (`ChatRepository.typing`, popisek `ChatActivity.label()`
  z `core`): místo náhledu v barvě motivu, ve skupině bez jména (do řádku se nevejde). Odebírá ho
  každá karta sama (`remember(chat.id)` + `collectAsState`) — lazy seznam drží jen viditelné
  položky, odběr skončí s kartou. Připnutý chat má před časem / odznakem špendlík. Ověřeno na
  emulátoru 2026-10-25: `sendChatAction typing` od Notifikátora → „typing…" v jeho kartě;
  připnutý chat účet na emulátoru nemá, špendlík neověřený.
- **Fotka chatu** (`ChatAvatar`): malá varianta (`avatarFileId`), stažení spustí odběr
  `FileRepository.file()` sám; dekóduje se mimo hlavní vlákno rovnou zmenšená (`inSampleSize`
  mocninou dvou) na velikost avataru — hodinky mají málo paměti. Bez fotky iniciála.
- **Uložené zprávy svým jménem a se záložkou** (`savedMessagesChatId` z `ChatRepository`,
  `chatTitle` a `ChatAvatar(savedMessages = true)` v `SavedMessages.kt`): v TDLib nesou jméno
  a fotku vlastního účtu a hodinky je tak ukazovaly — jako chat se sebou samým (nahlásil
  uživatel z Pixel Watch). Platí v seznamu, v hlavičce konverzace i na dlaždici s chaty, kde je
  záložka prostředek Androidu (`ic_saved_messages`) v `onTileResourcesRequest` a fotka účtu se
  pro ně nestahuje. Ověřeno na emulátoru.
- **Položky seznamu BEZ klíče.** S `key = id` seznam po přihlášení začínal uprostřed: chaty
  přibývají postupně a lazy seznam drží první viditelnou položku, takže nové nahoře odjely nad
  displej i s nadpisem. Na hodinkách se má začínat nahoře.

## Konverzace

- **Stav konverzace je ve ViewModelu položky navigace** (`ConversationViewModel`: zprávy, odběry,
  dočítání historie, citace). Výběr reakce, fotka přes celý displej a nahrávání jsou samostatné
  cesty a obrazovka konverzace při nich opustí kompozici; se stavem v `remember` se po návratu
  načetla znovu a sjela na nejnovější zprávu — reakce na starší zprávu uživatele vždycky vrátila
  dolů (nahlášeno z Pixel Watch). Pozici drží `rememberTransformingLazyColumnState` (je
  `rememberSaveable`), dolů se sjíždí jen s opravdu novou zprávou (`scrolledToId` ve ViewModelu).
  `openChat` při vzniku ViewModelu, `closeChat` až v `onCleared` — chat zůstává otevřený i během
  výběru reakce. Přehrávání hlasovky patří obrazovce a s odchodem z ní skončí.
  Ověřeno na emulátoru: reakce na starší zprávu i zavření fotky vrátí na totéž místo, nová zpráva
  dolů sjede.
- **Jen pro čtení** (`ConversationScreen`): posledních 30 zpráv, nejnovější dole a seznam se na
  ni postaví při otevření i s každou novou. Nové, upravené a smazané zprávy se odebírají DŘÍV
  než historie, ať se mezi tím nic neztratí.
- **První `history(…, fromMessageId = 0)` vrací TDLib často jen kus z lokální databáze**
  (klidně jednu zprávu) — dočítá se od nejstarší, dokud stránka není plná nebo historie neskončí.
- **Starší historie se dočítá u začátku seznamu** (první viditelná položka do 2, `snapshotFlow`
  nad `layoutInfo.visibleItems`), po 30 zprávách od nejstarší načtené; prázdná stránka = začátek
  chatu. **Zprávy mají v seznamu klíče (`id`)** — vložené nahoře tak seznam neposunou a uživatel
  zůstane na zprávě, kterou četl. (Seznam CHATŮ je naopak bez klíčů, tam má začínat nahoře.)
  Mezitím nahoře malý indikátor.
- **Citace nad odpovědí** (`ReplyQuote`): jméno a první řádek citované zprávy s proužkem vlevo
  (`drawBehind`, ne prvek přes celou výšku — ta by se měřila intrinsicky). Zpráva nese jen
  `replyToMessageId`; bere se z načtené historie, chybějící se dočte jednou přes
  `message(chatId, id)`. V soukromém chatu zpráva odesílatele nenese, jméno je pak název chatu.
- **Ověřeno na emulátoru 2026-10-24** v chatu Notifikátora: odpověď bota s `reply_parameters`
  ukázala citaci „Notifikator / Plume test hodinek: d…", dvojí dojetí nahoru dočetlo starší
  zprávy (večerní z předchozích dnů).
- **Otevřený chat se TDLib ohlásí** (`openChat`, při odchodu `closeChat` v `APP_SCOPE`, scope
  obrazovky v `onDispose` už je zrušený) a **zobrazené zprávy se hlásí jako přečtené** (`markRead`),
  i ty, které přijdou, zatímco je konverzace otevřená. Kdo ji na hodinkách viděl, četl ji.
- Bublina: `text`, a když je prázdný, `description` (fotka bez popisku, anketa…) jako na telefonu;
  jméno odesílatele jen tam, kde se liší od názvu chatu (skupiny); servisní zprávy uprostřed.
- **Hlavička konverzace s fotkou chatu** (`ChatAvatar` 20 dp v `ListHeader`, u Uložených zpráv
  záložka): je hned jasné, kde jsi. Ověřeno na emulátoru (Notifikátor).
- **Nová zpráva přijede plynule** (`animateItem` u zpráv a předělů, specifikace z `MotionScheme`
  Wear M3; posun effects bez překmitu, pružina by rozhoupala celou konverzaci). **Pozor:
  `scrollToItem` ruší animace položek** — nová zpráva v otevřené konverzaci proto sjíždí
  `animateScrollToItem`, skokem jen při otevření. Ověřeno na emulátoru se zpomalenými animacemi
  (`animator_duration_scale 10` NASTAVIT PŘED startem aplikace, za běhu se neprojeví): obsah
  se během příjezdu posouvá.
- **Předěly dní a bloky** (`conversationEntries` v `ConversationEntries.kt`): nad první zprávou
  dne „Dnes", „Včera", jinak datum (`asDayLabel` z `core`) — bubliny ukazují jen hodinu a u
  starší historie nebylo poznat, ze kterého dne zpráva je. **Klíč předělu je číslo dne**, ne id
  první zprávy: dočtením starší historie téhož dne by se první zpráva změnila a seznam by ztratil
  kotvu. Den počítá `java.time` (hodinky jsou jen Android; `localDate()` z `core` vrací typ
  kotlinx-datetime, který `wearApp` na classpath nemá). Po sobě jdoucí zprávy téhož odesílatele
  a strany ve stejném dni bez pauzy nad 10 minut tvoří blok: odstup seznamu je 2 dp a začátek
  bloku si přidá 2 dp navíc, roh na straně odesílatele se tam, kde zprávy navazují, stáhne na
  4 dp a jméno ve skupině je jen u první. Ověřeno na emulátoru 2026-10-24 v chatu Notifikátora
  („Today", „Yesterday", bloky zpráv bota).
- **Stav doručení** za časem odchozí zprávy: fajfka odesláno, dvojitá přečteno (`DeliveryIcon`,
  popis pro čtečku z `conversation_delivery_*`). Přečtení je posuvný ukazatel na chatu
  (`ChatInfo.lastReadOutboxMessageId` při otevření, pak `readOutboxUpdates`), ne stav zprávy —
  drží ho `ConversationViewModel.lastReadOutboxId` a jen roste, protože počáteční čtení a update
  můžou přijít v libovolném pořadí. Odesílání a nezdar zůstávají slovy místo času. Fajfky
  rozlišuje tvar, ne barva (akcent na odchozí bublině kontrast nezaručuje). U samolepky a kolečka
  u spodního okraje fajfku ořízne kulatý displej — řádek končí u pravého kraje položky.
- **Navigace** `SwipeDismissableNavHost` (`compose-navigation`): zpět tahem doprava jako všude na
  hodinkách. Konverzace dostane id chatu v cestě (`chat/{chatId}`).
- **Ověřeno na emulátoru 2026-10-24** v chatu Notifikátora (skutečný účet — chaty lidí se
  neotevírají, otevření je označí přečtené): historie, živá zpráva během otevřené konverzace,
  tah zpět, v seznamu pak místo počtu nepřečtených čas.

## Odpověď a reakce

- **Odpověď tlačítkem dole** (`EdgeButton` ve `ScreenScaffold`, vzor M3 Expressive pro hodinky):
  systémové zadávání (`RemoteInput` přes `RemoteInputIntentHelper`) nabízí emoji, diktování
  a klávesnici, pod nimi naše **rychlé odpovědi jako volby** (`setChoices`, texty
  `wear_quick_reply_*`). Ťuknutí na volbu pošle zprávu rovnou, bez potvrzení.
- **Odeslaná zpráva se ukáže hned** (dočasné id ze `send`), stav „Odesílání…" / „Neodesláno"
  místo času; po potvrzení ji `sentMessageUpdates` vymění za tu se skutečným id — nahradit, ne
  aktualizovat, TDLib id změní.
- **Samotné emoji (i z rychlé odpovědi „👍") nemá text ani popis:** Telegram ho pošle jako
  `MessageAnimatedEmoji`, `core` z něj udělá přílohu `MessageMedia.AnimatedEmoji` a text nechá
  prázdný, aby se pod animací nekreslil dvakrát. Hodinky animace nekreslí, takže znak berou
  z přílohy (i u `Dice`) a kreslí ho velkým písmem — bez toho byla odpověď prázdná bublina.
- **Reakce:** ťuknutí na zprávu otevře výběr (vlastní cesta v navigaci, zpět tahem) s emoji, která
  chat u zprávy dovoluje (`availableReactions`, nejvýš 12); daná je zvýrazněná a ťuknutí ji
  odebere (`setReaction(add = false)`). Stav si výběr načte sám (`message(chatId, messageId)`).
  V bublině „👍 2  ❤" vlevo v jednom řádku s časem vpravo (`ReactionsAndStatus`), živě
  z `interactionUpdates`. Dřív měly reakce i čas každý svůj řádek a bublina se zbytečně
  natahovala (připomínka uživatele z Pixel Watch); delší řada reakcí se zalomí ve své části
  řádku, čas zůstane vpravo dole. **Reakce je nedělitelná dvojice emoji + počet** (`FlowRow`
  po dvojicích, `ReactionCount`): jako jeden text s mezerami se u kanálu s mnoha reakcemi
  zalomila mezi emoji a počet a číslo viselo na začátku dalšího řádku u cizí reakce. Počet je
  menší (`labelSmall`, tabulkové číslice) a tlumený jako čas — stejně velký a barevný jako emoji
  s ním splýval; vlastní reakce má počet tučně (akcentní barva na tónové bublině kontrast
  nezaručuje). S reakcemi má řádek nahoře 4 dp mezeru — emoji jsou vyšší
  než čas a bez ní se lepily na obsah zprávy. U samolepky a kolečka (bez bubliny) totéž, jen podle obsahu,
  ne přes celou šířku — čas by jinak stál na opačném kraji displeje. Ověřeno na emulátoru
  2026-10-24 (hlasovka, textová bublina, kolečko). **Vlastní emoji se vynechávají**
  (`customEmojiIdOf`) — hodinky je nevykreslí a klíč reakce by se ukázal jako kód.
- Reagovat jde jen na zprávu se skutečným id (ne odesílanou), servisní ne.
- **Ověřeno na emulátoru 2026-10-24** v chatu Notifikátora: rychlá odpověď „👍" (odešla,
  potvrzená, velké emoji), reakce ❤ přidaná a znovu odebraná. Diktování a klávesnice na emulátoru
  nevyzkoušené (vracejí text stejnou cestou jako volba).

## Fotky

- **Fotka v bublině** (`MessagePhoto`): přes šířku bubliny v poměru stran fotky, popisek pod ní;
  slovo „Fotka" se pak nekreslí. Hned minináhled (`Minithumbnail`, malý JPEG přímo ve zprávě),
  po stažení varianta pro bublinu (`fileId`) dekódovaná zmenšená na šířku (`decodeScaled`,
  `inSampleSize` mocninou dvou). **Plná varianta (`fullFileId`) se na hodinkách nestahuje** —
  displej má pár set pixelů a šly by megabajty.
- **Vysoká fotka se zastropuje na 1,25 × šířky a zúží se s ní**, neořízne se (CLAUDE.md, „Výšku
  obrázku nepočítej `aspectRatio`").
- **Ťuknutí na fotku ji otevře přes celý displej** (`PhotoScreen`, cesta `photo/{fileId}`, zpět
  tahem), zbytek bubliny dál otevírá výběr reakce. Tatáž varianta jako v bublině, jen dekódovaná
  na velikost displeje — po bublině bývá stažená.
- **Spoiler, citlivý obsah, tajný chat a mizející fotka zůstávají slovem** (`shownPhoto()`):
  odkrývání ani časovač hodinky nemají a ukázat je rovnou by obešlo, co chtěl odesílatel nebo
  server.
- **Ověřeno na emulátoru 2026-10-24** v chatu Notifikátora (fotky poslané botem přes `sendPhoto`,
  vygenerované přechody bez lidí): široká s popiskem, vysoká zastropovaná a zúžená, náhled přes
  celý displej a zpět. GIFy zůstávají slovem.
- **Video jako náhled** (`MessageVideo`): `thumbnailFileId` (do 320 px) a minináhled stejnou cestou
  jako fotka (`MessageImage`), vlevo dole „▶ 0:04". **Přehrávání na hodinkách není** — dekodér,
  zvuk a stahování desítek megabajtů by vydaly za samostatnou funkci. Ťuknutí patří bublině
  (reakce). Skryté video (spoiler…) a video bez náhledu zůstávají slovem.
- **Video kolečka bez bubliny** (`MessageVideoNote`, kulatý náhled 120 dp, „▶ 0:04" dole, čas
  pod ním), minináhled hned a `thumbnailFileId` po stažení; pod ním hotový přepis řeči
  (`SpeechTranscript.Done`), pokud ho server udělal — o přepis se na hodinkách nežádá. Bez
  přehrávání, jako video. Ověřeno na emulátoru (čtvercové video ze `screenrecord` poslané
  `sendVideoNote`).
- **Samolepky bez bubliny** (`MessageSticker`, 96 dp, čas pod ní): statická (`fileId`, WebP)
  rovnou, animovaná (TGS) a videosamolepka (WebM) náhledem `thumbnailFileId` — animace by stála
  baterii. Bez staženého obrázku emoji samolepky. Citlivá samolepka zůstává slovem.
- **Ověřeno na emulátoru 2026-10-24** (Notifikátor): video natočené `adb shell screenrecord`
  a poslané `sendVideo` (náhled, „▶ 0:04", popisek), animovaná samolepka ze sady
  `AnimatedEmojies` a videosamolepka z `PandaVideo` (`sendSticker`) náhledem. Statická samolepka
  neověřená (dekóduje ji tentýž `BitmapFactory`, WebP umí).

## Ankety a kvízy

- **Hlasování v bublině** (`MessagePoll`, `ChatMessage.poll`): druh ankety, otázka, odpovědi,
  počet hlasů. Jedna odpověď = ťuknutí rovnou hlasuje (`ConversationRepository.vote`), víc
  odpovědí = `CheckboxButton` a pod nimi „Hlasovat". Po hlasování nebo skončení výsledky: pruh
  s procenty za textem (`drawBehind`), vybraná s fajfkou, u kvízu správná `primary`, špatně
  vybraná `error` (`quizMark` z `core`). Texty jsou telefonní (`poll_*`).
- **Nový stav přijde jako upravená zpráva** (`editedMessages`), kterou konverzace už odebírá —
  hodinky nic nepřepočítávají a neukazují optimisticky; mezitím `isVoting` s indikátorem.
- **Odpovědi jsou obrysová tlačítka**, ne tónová: tónová výplň má barvu příchozí bubliny a na
  emulátoru tlačítka zmizela (zbyl jen text) — stejná past jako přehrát u hlasovky.
- Po hlasování se bublina zmenší (tlačítka → řádky výsledků) a seznam pod prstem poskočí; na
  hodinkách to nevadilo, kdyby ano, pomohlo by držet výšku.
- **Ověřeno na emulátoru 2026-10-24** v Notifikátoru (ankety přes `sendPoll`): jedna odpověď
  („✓ Možná"), víc odpovědí (dvě zaškrtnuté → obě 100 %), kvíz („✓ 4" 100 %). Omezení serveru
  (členství, země) a anketa s médii neověřené.

## Kontext zprávy

- **„Přeposláno od …"** nad textem (`ChatMessage.forwardOrigin`, kurzívou; bez jména
  „Přeposlaná zpráva"), **„upraveno"** před časem (`isEdited`) a u **souboru** ikona, jméno
  a velikost (`MessageMedia.Document`, `fileSizeText` z `core`) místo slova „Soubor". Texty jsou
  telefonní (`conversation_forwarded_from`, `conversation_edited`). Soubor se na hodinkách
  neotevírá — prohlížeče dokumentů tam nejsou.
- **Úpravu od bota „upraveno" neukáže**: Bot API vrátí `edit_date`, ale TDLib u zpráv, jejichž
  úpravu server skrývá (`edit_hide`, úpravy od botů), dá `editDate = 0` — telefon se chová stejně.
  Značku tedy na testovacím botovi ověřit nejde.
- **Ověřeno na emulátoru 2026-10-24** (Notifikátor): `forwardMessage` → „Forwarded from
  Notifikator", `sendDocument` → ikona, „plume-test-soubor.txt", „27 B". „Upraveno" neověřené
  (viz výš).

## Odkazy v textu

- **Odkaz ve zprávě se otevře v telefonu** (`linkedText`: `LinkAnnotation.Clickable` nad entitami
  `MessageEntityType.Link` — holá adresa, odkaz pod textem, e-mail i telefon). Stejná cesta jako
  tlačítko bota (`openOnPhone`, potvrzení jen po úspěchu, jinak „Telefon není dostupný").
  Ťuknutí na odkaz si vezme odkaz, ne bublinu (výběr reakce se neotevře).
- **Barva**: v příchozí bublině `primary`, v odchozí barva popředí bubliny
  (`onPrimaryContainer`) — `primary` na `primaryContainer` kontrast nezaručuje; odlišení nese
  podtržení v obou.
- **Zmínky, tagy, příkazy botů a formátování zůstávají obyčejným textem**: na hodinkách by neměly
  kam vést a modrý text bez akce je horší než černý.
- **Ověřeno na emulátoru 2026-10-24**: zpráva od bota s holou adresou a `<a href>` (oba podtržené),
  ťuknutí na odkaz → „Telefon není dostupný" (emulátor bez telefonu). Otevření v telefonu
  neověřené.

## Tlačítka botů

- **Klávesnice bota pod zprávou** (`MessageBotKeyboard`, `ChatMessage.keyboard`): všechna
  tlačítka pod sebou přes šířku bubliny (na kulatém displeji se vedle sebe nevejdou), v pořadí
  řádků; obrysová (`CompactButton`), ne tónová. **Žádné se neschovává** — i to, co hodinky
  neumí, je vidět a ťuknutí řekne „Tohle tlačítko otevři v telefonu" (mini aplikace,
  přihlášení na web, inline dotaz, kontakt, platba…).
- **Zpětné volání** přes `pressBotButton` podle pozice (data do domény nejdou, viz `boti.md`),
  naráz čeká jen jedno tlačítko (indikátor místo popisku). Odpověď bota: krátký text jako
  poznámka pod konverzací, upozornění (`alert`) jako `AlertDialog` s fajfkou — **nadpis je název
  chatu, text odpovědi v těle**: nadpis dialogu má málo řádků a delší upozornění uťal.
- **Odkaz (tlačítko i odkaz v odpovědi) se otevře v telefonu** (`openOnPhone`,
  `RemoteActivityHelper` z `wear-remote-interactions`). Potvrzení `OpenOnPhoneDialog`
  („Otevřeno v telefonu") **jen po úspěchu**: na emulátoru bez spárovaného telefonu budoucnost
  selže (`RemoteIntentException`) a dialog by lhal — místo něj „Telefon není dostupný".
  `OpenOnPhoneDialogDefaults.curvedTextStyle` je composable, čte se před voláním dialogu.
- **Zkoušení s testovacím botem**: bot neběží na serveru, stisk se mu musí doručit a odpovědět
  ručně — `getUpdates` (bez `offset`, nic nepotvrzuje) a `answerCallbackQuery` (`show_alert` pro
  upozornění). Notifikátor má v `allowed_updates` jen `message` a `stopped_message_generation`;
  `callback_query` se na test přidá parametrem `allowed_updates` u `getUpdates` a **pak vrátí**.
- **Ověřeno na emulátoru 2026-10-24**: tlačítka pod zprávou, „Odložit" → poznámka s odpovědí
  bota, „Potvrdit" s `show_alert` → dialog, odkaz → „Telefon není dostupný" (bez telefonu).
  Otevření odkazu v telefonu neověřené (emulátor nemá spárovaný telefon).

## Hlasovky

- **Přehrávání vlastním tenkým přehrávačem** (`VoicePlayback`, systémový `MediaPlayer`): Ogg/Opus
  přehraje Android sám od verze 10, Media3 z telefonu sem nejde. Nejvýš jedna naráz, poslech se
  ohlásí TDLib (`openContent`) jako na telefonu. Stažení spustí odběr `FileRepository.file()`.
- **Past: `stop()` uvnitř `MediaPlayer().apply { … }` je `MediaPlayer.stop()`**, ne metoda
  obalu — příjemce `apply` ho zastíní. Po dohrání se zastavil jen přehrávač a tlačítko zůstalo
  na „hraje" (`dumpsys audio` ukázal přehrávač `stopped`). Proto `this@VoicePlayback.stop()`.
- **Nahrávání** (`VoiceRecorder`, `MediaRecorder`): Ogg/Opus mono 48 kHz, 32 kbit/s jako telefon;
  úrovně pro waveform každých 50 ms ze `maxAmplitude` (odmocnina), zabalené `encodeWaveform`
  z `core`. Kratší než vteřina se zahodí (`stop()` po krátkém nahrávání navíc vyhodí výjimku).
  Ověřeno `file` nad nahrávkou z hodinek: „Ogg data, Opus audio, mono, 48000 Hz".
- **O mikrofon se žádá až na obrazovce nahrávání**, ne při startu. Ťuknutí spustí, druhé zastaví
  a pošle (`sendVoiceNote`); zahodit jde tahem zpět, nahrávka se při odchodu smaže.
- Nahrané soubory zůstávají v `cacheDir` (`voice-*.ogg`): TDLib je nahrává z cesty na pozadí,
  smazat je hned po odeslání by nahrávání utnulo. Úklid nechává na systému.
- Tlačítko přehrát v příchozí bublině má plnou barvu: tónová výplň je stejná jako příchozí
  bublina a tlačítko v ní zmizelo.
- **Vlna a průběh** (`VoiceNoteRow`): sloupce z `waveform` zprávy, kolik se jich do šířky vejde,
  převzorkované `resampleWaveform` z `core` (totéž co telefon); bez dat rovná čára, ticho
  nejnižším sloupcem. Během přehrávání se vlna zleva vybarvuje a místo délky běží uplynulý čas —
  dřív byla jen délka a nebylo poznat, kolik zbývá. **`MediaPlayer` průběh neohlašuje**, čte se
  po snímcích (`withFrameMillis`) jen u hrající hlasovky; `duration` a `currentPosition` až po
  přípravě (`prepared` ve `VoicePlayback`), před ní hlásí chybu stavu. Barvy z popředí bubliny
  (odchozí `onPrimaryContainer`, příchozí `primary`), nepřehraná část tatáž barva na 40 %.
  Ověřeno na emulátoru 2026-10-24 (vlastní hlasovka: vybarvení a „0:01" během přehrávání,
  po dohrání zpět délka); příchozí hlasovka s vlnou neověřená.
- **Ověřeno na emulátoru 2026-10-24** v chatu Notifikátora: žádost o mikrofon („Only this time"),
  nahrávka 0:03 odešla a potvrdila se, přehrání vlastní (z disku) i příchozí (soubor poslaný
  botem přes `sendVoice`, stažení) — „Pause" během přehrávání, po dohrání zpět „Play". Mikrofon
  emulátoru bez povolení hostitele nahrává ticho; skutečný zvuk a reproduktor hodinek neověřené.

## Push a notifikace

- **Vlastní push:** hodinky mají vlastní sezení, takže i vlastní FCM token (`WearPushRegistrar`
  ho po přihlášení zaregistruje u TDLib) a vlastní službu (`WearMessagingService`: celý JSON do
  `processPush`, počká na vydání). Hovory hodinky nemají.
- **`google-services.json` je kopie z `androidApp`:** plugin ho čte jen z adresáře modulu a
  hodinky mají stejné `applicationId`, takže platí tentýž soubor. Změní-li se projekt Firebase,
  mění se oba. Bez souboru se hodinky přeloží, jen bez pushe.
- **Notifikace kreslí `WearNotifications`** (telefonní presenter je ve `shared`): jedna na chat
  (`MessagingStyle`, id = `notificationGroupId`), stažená skupina (přečteno jinde) zmizí, ťuknutí
  (na hodinkách „Open on watch") otevře rovnou konverzaci: `MainActivity.EXTRA_CHAT_ID` jako
  u dlaždice s chaty, request code podle skupiny, aby se intenty chatů neslily. Ověřeno na
  emulátoru s aplikací na pozadí i po `am kill` (notifikace zůstala, aktivita se obnovila
  a konverzaci otevřela). Název konverzace jen ve skupině — v soukromém chatu je odesílatel totéž co
  název a titulek zněl „Notifikator: Notifikator".
- **Akce Odpovědět a Přečteno** (`WearNotificationReceiver`, broadcast, `goAsync`) podle poučení
  telefonu (sekce „Akce z notifikace" v `notifikace.md`): `PendingIntent` odpovědi MUTABLE
  (systém do něj vkládá text), vlastní řádek do notifikace hned, **přečtení až po potvrzení od
  serveru** a po nejnovější zprávu NOTIFIKACE (ne chatu), potvrzení dotazem na zprávu
  (přečíslovaná dočasná = odesláno), notifikaci stáhne TDLib, vlastní řádky ji naživu nedrží.
  Mrtvý proces probudí akce jako push a čeká se na `AuthStage.Ready`. Presenter je v Koinu jeden
  na proces, aby do něj přijímač mohl připsat odpověď. Telefon má na pomalé potvrzení úlohu
  WorkManageru, hodinky ne: co se nestihne do ~9 s (`goAsync`), zůstane odeslané, ale nepřečtené.
- **Wear OS akci odpovědi vykreslí přímo v notifikaci** jako emoji / hlas / klávesnici;
  **rychlé odpovědi (`setChoices`) se tam na emulátoru neukázaly** — v konverzaci (vlastní
  `RemoteInput` aktivita) ano. Zadání emoji vloží znak do pole a odesílá se šipkou.
- **O notifikace se Android 13+ ptá za běhu** — hodinky jednou, hned po přihlášení.
- **Zrcadlení z telefonu** (`PhoneBridging`, `BridgingManager`): vypnuté, dokud jsou hodinky
  přihlášené (jinak by každá zpráva přišla dvakrát), po odhlášení zase zapnuté.
- **Ověřeno na emulátoru 2026-10-24:** FCM token a registrace u TDLib, přepnutí zrcadlení po
  přihlášení, notifikace z TDLib s aplikací na pozadí (do 2 s); odpověď „👍" z notifikace
  (v chatu se objevila, notifikace po potvrzení zmizela) a Přečteno (v seznamu chatů místo
  počtu nepřečtených čas).
- **Neověřeno: doručení pushu s ukončeným procesem.** Po `am kill` (ne `force-stop`, ten FCM
  zakáže) a zprávě od Notifikátora se FCM na emulátoru o doručení dvakrát ani nepokusil (150 s,
  v logu žádné `c2dm`). Může to být emulátor (spojení Google Play služeb bez účtu a bez
  spárovaného telefonu) i server (odklad pushe, když je účet aktivní jinde) — ověří až skutečné
  hodinky. Zrcadlení z telefonu na emulátoru bez spárovaného telefonu ověřit nejde.

## Dlaždice a komplikace

- **Dlaždice** (`UnreadTileService`): název aplikace, uprostřed velký počet nepřečtených chatů
  s popiskem z plurálu, dole `textEdgeButton` „Otevřít". Není to Compose: systém ji kreslí sám
  z popisu rozvržení (ProtoLayout, `protolayout-material3`), `TileService` vrací
  `ListenableFuture`, korutinu do ní převede `SuspendToFutureAdapter` (concurrent-futures).
- **Komplikace** (`UnreadComplicationService`, `SuspendingComplicationDataSourceService`): jen `SHORT_TEXT` — ikona
  „mark_chat_unread" (prostředek Androidu, ne composeResources: `Icon.createWithResource`) a číslo,
  ťuknutí otevře aplikaci. Bez přihlášení pomlčka, ikona zůstává.
- **Počet se drží v souboru** (`UnreadState`, SharedPreferences `wear_unread`): systém se na
  dlaždici i komplikaci ptá kdykoli, i když proces neběží, a TDLib se za tu chvíli zeptat nedá.
  Zapisuje ho `UnreadCountPublisher` (od startu procesu) a pak požádá o obnovu
  (`TileService.getUpdater`, `ComplicationDataSourceUpdateRequester`) — **pravidelná obnova
  žádná** (`UPDATE_PERIOD_SECONDS` 0, bez `freshnessInterval`).
- **Počítá TDLib** (`mainUnread.unmutedChats`): nepřečtené chaty hlavního seznamu bez
  ztlumených, jako záhlaví widgetu na telefonu. Součet nad seznamem chatů by vynechal chaty,
  ke kterým se stránkování ještě nedostalo.
- **Zapisuje se jen v jasných stavech** (přihlášeno a seznam načtený, nebo odhlášeno) a po vteřině
  klidu: při studeném startu by jinak dlaždice problikla nulou, než TDLib počet ohlásí.
- **Otevřená konverzace zprávy rovnou čte** (viz Konverzace), takže zpráva do chatu, který zůstal
  otevřený pod ciferníkem, počet nezvedne. Při zkoušení se proto vrací do seznamu chatů.
- **Na emulátoru se dlaždice přidá ladicím příkazem**: `am broadcast -a
  com.google.android.wearable.app.DEBUG_SURFACE --es operation add-tile --ecn component
  eu.tintera.plume/eu.tintera.plume.wear.UnreadTileService` (`remove-tile` ji odebere), pak tah
  doleva z ciferníku. Komplikace `--es operation set-complication … --ecn watchface <ciferník>
  --ei slot 0 --ei type 3`; výchozí ciferník Orbita má políčka vypnutá („Complication slot is not
  enabled"), jde to na AnalogClassic (`set-watchface`).
- **Jazyk aplikace potřebuje `localeConfig`** (`android:localeConfig` v manifestu, soubor
  vyrábí plugin `plume.locale-config` stejně jako u telefonu). Bez něj `set-app-locales` nezabral
  ani v aplikaci samotné, přestože ho systém uložil. **Projeví se až v novém procesu**: běžící
  aplikace zůstala ve starém jazyce i po novém vytvoření aktivity, po `am kill` mluvila česky.
  Dlaždice si texty čte při každém kreslení, po změně jazyka se ale překreslí až s další změnou
  počtu.
- Náhled dlaždice ve výběru (`androidx.wear.tiles.PREVIEW`) zatím není — obrázek by musel mít
  i českou variantu.
- **Čeština ověřená na emulátoru 2026-10-24** přes jazyk aplikace: seznam „Chaty", dlaždice
  „0 nepřečtených chatů" / „1 nepřečtený chat", „Otevřít".
- **Ověřeno na emulátoru 2026-10-24** (anglicky): dlaždice s počtem, zpráva od Notifikátora ho
  zvedla během pár vteřin (dlaždice i komplikace), přečtení v aplikaci snížilo, „Otevřít" i ťuknutí
  na komplikaci otevřely aplikaci, po `am kill` se dlaždice nakreslila z nově spuštěného procesu.
  Neověřené: stav bez přihlášení (vyžaduje odhlášení), skutečné hodinky.

## Dlaždice s chaty

- **`RecentChatsTileService`**: nadpis „Chaty", pod ním chaty ze začátku hlavního seznamu jako
  `avatarButton` (fotka, název, pod ním „N nových zpráv", a když nic nečeká, náhled poslední
  zprávy), dole `textEdgeButton` „Všechny chaty". Začátek seznamu = připnuté a pak podle poslední
  zprávy, tedy totéž co nahoře v aplikaci.
- **Dva řádky, tři až od výšky 260 dp.** Na emulátoru (227 dp) se třetí s tlačítkem dole nevešel.
- **Podklady v souboru** (`RecentChatsState`, SharedPreferences `wear_recent_chats`, JSON), zapisuje
  `RecentChatsPublisher` jako u počtu nepřečtených: jen v jasných stavech, po vteřině klidu,
  a jen když se změní, co dlaždice ukazuje (id, název, počet, náhled, fotka) — seznam se mění
  s každou zprávou kdekoli. Náhled je přeložený při zápisu a bez zalomení řádků (jeden řádek).
- **Fotky jsou miniatury 96 px připravené při zápisu** (`filesDir/tile/avatars/<fileId>.png`,
  čtverec ze středu, ořez do kruhu dělá `avatarImage`); na stažení se čeká nejvýš 5 s, pak
  iniciála na `primaryContainer`. Nepoužívané se po zápisu smažou.
- **Fotky dodává `onTileResourcesRequest`, ne automatická registrace** (`Material3TileService`,
  `avatarImage(ImageResource)` s `ProtoLayoutScope`). S rendererem **1.300** (emulátor Wear OS 5)
  prostředky ze scope do balíčku nedorazily: „Resource avatar-… is not defined in resources
  bundle" v logu systému, místo fotky prázdno a knihovna sama nezalogovala nic. Klasická cesta:
  verze prostředků = otisk jmen miniatur, obrázky jako `InlineImageResource` s komprimovaným PNG
  (formát neurčený). Verzi rendereru ukáže `requestParams.deviceConfiguration.rendererSchemaVersion`.
- **Sloupec tlačítek musí mít šířku `expand`.** `avatarButton` je `expand` a sloupec s výchozím
  `wrap` renderer zahodil celý („Column set to wrap but contents are unmeasurable") — dlaždice
  měla jen nadpis a tlačítko.
- **Ťuknutí na chat otevře konverzaci** (`LaunchAction` s `AndroidLongExtra`
  `MainActivity.EXTRA_CHAT_ID`), nad seznamem chatů, takže tah zpět vede do seznamu. **Aktivita je
  `singleTop`**: když aplikace běží, systém ji jen přivede dopředu a bez `onNewIntent` by extra
  zůstalo nepřečtené. Po obnově procesu (`savedInstanceState`) se nenaviguje podruhé.
- **Ověřeno na emulátoru 2026-10-24** (anglicky): dlaždice s Notifikátorem (iniciála, „1 new
  message") a kanálem s fotkou, obnova po zprávě od bota, ťuknutí otevřelo konverzaci Notifikátora
  ze studeného startu i s aplikací na pozadí (jedna instance aktivity), tah zpět do seznamu.
  Neověřené: stav bez přihlášení, chat s nestaženou fotkou, skutečné hodinky.

## Úložiště

- **Automatický úklid** (`WearStorageCleaner`): po přihlášení, nejvýš jednou za 24 h, smaže
  média, na která se nesáhlo 3 dny, a pak nejdéle nepoužitá nad 100 MB —
  `StorageRepository.cleanUp` z `core` (`optimizeStorage` s mezemi vypsanými výslovně, ne
  `use_storage_optimizer`; proč, viz `StorageCleaner` telefonu). Samolepky, avatary a náhledy
  nechá být. **Pevné meze, ne volby**: hodinky nastavení úložiště nemají.
- **Ruční vyčištění v Nastavení** („Vyčistit mezipaměť", pod ním „Uvolněno N") se stejným
  výběrem jako výchozí dialog telefonu (`DEFAULT_CLEAR_CATEGORIES`: média ano, samolepky
  a avatary ne). Bez potvrzení — soubory zůstávají na serveru a stáhnou se znovu. Úklid a ruční
  vyčištění hlídá jeden `Mutex` (souběžné `optimizeStorage` TDLib ruší), proto je čistič v Koinu
  jeden na proces.
- **Ověřeno na emulátoru 2026-10-24**: po startu „Úklid úložiště hodinek uvolnil 0 B" (vše
  mladší než 3 dny, pod stropem), ruční vyčištění „Freed 204 kB" (`files` 23 → 22 MB), fotka
  v konverzaci Notifikátora se pak stáhla znovu.

## Hlášení pádů

- **Crashlytics jako telefon** (`release.md`, „Hlášení pádů"): `configureLogging()` z `core`
  na začátku `WearApplication.onCreate`, před `startKoin`; plugin `crashlytics` za
  `google-services`, sběr jen v release (placeholder `crashlyticsCollectionEnabled`
  v manifestu). Release build nahrává R8 mapping soubor (`uploadCrashlyticsMappingFileRelease`).
- **Ověřeno na emulátoru 2026-10-24** release buildem přepodepsaným debug klíčem: `adb shell am
  crash eu.tintera.plume` → „Handling uncaught exception … shell-induced crash" a „Crashlytics
  report successfully enqueued to DataTransport". Podrobný log Crashlytics zapne `adb shell
  setprop log.tag.FirebaseCrashlytics DEBUG`. Testovací pád má `versionCode` 100000999 (9.9.9).

## Vydání

Postup a pasti jsou v `release.md`, „Hodinky (Wear OS)": track `wear:internal` z tagu Androidu,
`versionCode` + 100 000 000, poznámky jen s `units: wear`, minifikovaný release; snímky do Play
v `play/graphics/<locale>/wearScreenshots/` (`release.md`, záznam v obchodě).

## Emulátor

- AVD **`Plume_Wear`** (`wearos_large_round`, obraz `system-images;android-34;android-wear;arm64-v8a`,
  Wear OS 5), port 5600, spouštět s oknem — QR kód se skenuje z obrazovky.
- První start ukazuje systémové „Starting…" ještě po `sys.boot_completed`; aplikace je pod ním.
- Varování `CompositionLocals: SecurityException … reduce_motion … targetSdkVersion lower than
  or equal to: 34` hlásí Compose for Wear OS na Wear OS 5 s vyšším `targetSdk`; knihovna ho ošetří
  a jede dál.
