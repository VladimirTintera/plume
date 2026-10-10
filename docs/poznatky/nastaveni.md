# Nastavení

Poznatky k jedné oblasti aplikace. Obecná pravidla projektu a rejstřík ostatních souborů jsou
v [CLAUDE.md](../../CLAUDE.md); poučení z opravy v téhle oblasti patří sem.

## Nastavení aplikace

`AppSettings` (`core/settings`) nad **DataStore Preferences** — multiplatformní, soubor
`app_settings.preferences_pb` v `filesDir` (Android) / Application Support (iOS). Jen to, co si
aplikace pamatuje o uživateli; data z Telegramu drží TDLib.

- **Hodnoty jsou `StateFlow` načtené eagerně při startu** (`createdAtStart` + `SharingStarted.Eagerly`
  v `appScope`). Obrazovka je má v prvním snímku — ikona ve vstupním poli jinak problikla
  z výchozí hodnoty na uloženou.
- **DataStore nad jedním souborem smí existovat jen jednou za proces** — proto `single` v Koinu.
  Přípona `.preferences_pb` je povinná.
- **Výčty se ukládají názvem, ne pořadím.** Nová hodnota uprostřed by jinak tiše změnila význam
  uložených dat; neznámý název (z novější verze aplikace) spadne na výchozí.
- **Zápis z obrazovky jde přes `appScope`**, ne `viewModelScope` — přepnutí těsně před odchodem
  z chatu se musí uložit.
- **Volby uživatele se od paměti aplikace neliší technikou, jen významem.** `ExpressionMode`,
  nedávná emoji a odstíny pleti si aplikace pamatuje sama; motiv a vyčlenění nepřečtených
  uživatel vědomě nastavuje. Obojí leží ve stejném DataStore — **Proto DataStore by tu byl
  krok zpátky**: znamenal by generování schématu (v KMP vlastní serializer nad kotlinx) kvůli
  hrstce skalárů, migraci při každém přidání pole a konec toho, že se neznámý klíč prostě
  ignoruje. Typovou bezpečnost nese rozhraní `AppSettings`, ne úložiště pod ním.
- **Motiv (`ThemeMode?`: podle systému / světlý / tmavý) a barvy z tapety (`dynamicColor:
  Boolean?`) jsou DVĚ volby, ne jeden výčet.** Material You byl dřív čtvrtou hodnotou
  `ThemeMode.DYNAMIC`, takže s ním nešel vynutit světlý ani tmavý režim. Teď je to přepínač pod
  koly a kombinuje se s kterýmkoli motivem; nabízí se jen tam, kde je (`supportsDynamicColor`).
  Uložené `DYNAMIC` ze starší verze se čte jako neznámá hodnota, tedy nevybraný motiv (podle
  systému) — a barvy z tapety zůstanou, protože nevybrané znamená výchozí.
- **`null` u obou znamená „uživatel si nevybral".** Výchozí barvy z tapety se liší podle
  platformy (Android 12+ zapnuto, jinde nejsou), takže zapsaná konkrétní hodnota by ten rozdíl
  zakonzervovala. Rozhoduje se až při kreslení a v Nastavení se jako vybraná ukáže ta možnost,
  podle které se aplikace opravdu kreslí — prázdný výběr by vypadal jako chyba.
- **`ThemeMode` bydlí v `core/settings`, ne u tématu.** Je to uložená hodnota a `core` nesmí
  importovat `app`; co z něj plyne pro vykreslení (`resolveIsDark`, `supportsDynamicColor`), zůstalo
  v `app/root/presentation` — závisí to na platformě a na systémovém tmavém režimu.
- **`ExpressionMode`** (Emoji / Samolepky): poslední otevřená část výběru. Podle ní se výběr
  otevře a ikona tlačítka ve vstupním poli ukazuje, co se otevře (smajlík / samolepka).
- **Nedávná emoji** (`recentEmojis`): nejnovější první, bez duplicit, nejvýš 40. Uložená jako
  jeden řetězec oddělený mezerou — `stringSetPreferencesKey` by ztratil pořadí; mezera v žádném
  emoji není. Přidání čte i zapisuje uvnitř jednoho `edit`, takže se dvě rychlá ťuknutí
  nepřepíšou, a běží v rozsahu nastavení (`recordRecentEmoji` neblokuje).
- **Odstíny pleti** (`emojiSkinTones`): základ → zvolená varianta, `základ:varianta` oddělené
  středníkem. Výchozí podoba se neukládá (záznam se smaže).
- Testy nad skutečným souborem v dočasném adresáři (`DataStoreAppSettingsTest`), každý test
  vlastní soubor. **Na skutečném čase (`runBlocking` + `Dispatchers.Default`), ne `runTest`**:
  DataStore pracuje na vlastních vláknech a s virtuálním plánovačem se to občas minulo — test
  visel do minutového limitu a selhával pokaždé jiný. Každé čekání má proto vlastní limit.
- **`first()` nad `StateFlow` NEČEKÁ na načtení — vrátí počáteční hodnotu.** Test „neznámá
  hodnota spadne na výchozí" tím neověřoval vůbec nic: výsledek vyšel správně i tehdy, když
  v úložišti ležela platná jiná hodnota (ověřeno pokusem, ne úvahou — test s „Stickers" v souboru
  dál tvrdil „Emoji" a prošel). Uložená hodnota se dá testovat jen jako **přechod z výchozí na
  jinou**: napřed platná hodnota a počkat na ni, teprve pak ta, která se má přeložit.
- **Limit čekání není měřítko rychlosti, ale pojistka proti zaseknutí** — patří mu tedy velkorysá
  hodnota (15 s). Přiškrcený limit z testu dělá generátor falešných poplachů.
- **Občasné selhání pod zátěží NEBYLA pomalost, ale ztracený zápis — chyba DataStore 1.2.x.**
  Nový odběr `data` čte úvodní stav bez zámku; když zrovna běží zápis, dostane STARÁ data
  s už zvýšenou verzí (zápis zvedá verzi PŘED zápisem souboru, aby pád mezi nimi neskryl změnu).
  Nová data pak odběr zahodí jako „už viděná" (`dropWhile { version <= startState.version }`)
  a hodnota zůstane stará až do dalšího zápisu. Selhávalo to jen pod zátěží (dva emulátory,
  link Kotlin/Native) a u testů, které zapisují „odpal a zapomeň" hned po vzniku nastavení —
  ne náhodně, ale pokaždé u toho, jehož zápis se trefil do okna. Opraveno v DataStore 1.3.0
  (čtení bez zámku bere verzi z keše), stabilní zatím není.
- **Proto `DataStoreAppSettings` odebírá `data` JEDNOU a každý zápis čeká na jeho načtení**
  (`preferences`, `edit`). Po prvním načtení odběr sleduje keš DataStore, kde souboj není; hodnoty
  se z jednoho odběru odvozují v paměti. Až bude DataStore 1.3 stabilní, obchvat jde pryč —
  ale jeden odběr místo pěti má smysl i tak.
- **Ověřilo se to měřením, ne úvahou.** Zápis doběhl za desítky milisekund a `StateFlow` ho
  neviděl ani za 20 s; teprve DALŠÍ zápis starou hodnotu přepsal. Reprodukce: všechna jádra
  vytížit (`yes > /dev/null &` dvakrát na jádro) a pustit scénář stokrát v jednom běhu — původní
  kód se zasekl v 25 kolech z 2 000, obchvat v 0 z 6 000. Hlídá to `zapisHnedPoVznikuSeProjevi`
  (300 kol, 0,2 s); bez zátěže chybu nepozná, pod ní s původním kódem selhal třikrát ze tří.
- **Čekání musí při vypršení říct, NA CO se čekalo a co bylo naposledy vidět.** Holé
  „Timed out waiting for 5000 ms" neumožnilo selhání ani dodatečně vysvětlit. Ze stejného důvodu
  se čeká na CELÝ očekávaný seznam nedávných emoji, ne jen na jeho první položku: jinak se
  neprojevený zápis pozná až o krok dál závěrečným `assertEquals`, kde už není poznat, který.

### Obrazovka nastavení

Nastavení je **list-detail jako chaty**: vlevo sekce (`SettingsScreen`), vpravo obsah té vybrané
(`SettingsSectionScreen`). Sekce jsou routy (`SettingsSectionRoute`), ne stav obrazovky — jinak by
se z telefonu nedalo chodit zpět bez `BackHandler`, který v téhle aplikaci zabíjí predictive back.

- **Vlastní `sceneKey` je povinný.** `ListDetailSceneStrategy` staví scénu s klíčem podle metadat
  a výchozí je `Unit` pro všechny. Chaty i nastavení jsou dvě list-detail dvojice v jednom
  `NavDisplay`, takže bez `SETTINGS_SCENE` by pro NavDisplay měly tutéž identitu.
- **V dvoupanelu je vždycky otevřená nějaká sekce.** Placeholder („vyber sekci") by vedle dvou
  řádků vypadal jako nedodělek, a bez otevřené sekce by directive dvoupanel nezapnul a seznam
  sekcí by se roztáhl přes celou šířku.
- **Výchozí sekce musí být V ZÁSOBNÍKU, ne jen dopočítaná** (`AppNavigation.ensureSettingsSection`).
  Zpět ze dvoupanelové scény odebírá seznam i sekci naráz a `NavDisplay` kvůli tomu volá
  `onBack` dvakrát (viz „Navigace"). S jen dopočítanou sekcí první volání odešlo do Chatů
  a druhé tam zavřelo otevřenou konverzaci. Dopočítává se jen na jeden snímek, než efekt
  sekci zapíše — jinak by seznam sekcí problikl přes celou šířku.
- **Efekt, který sekci zapisuje, stojí v shellu, ne v obrazovce seznamu sekcí.** Obrazovka po
  přepnutí sekce aplikace ještě dobíhá odchodovou animací a její efekty dál běží. Dokud byl
  efekt v ní, po rychlém Nastavení → Chaty přidal `SettingsSectionRoute` do zásobníku, který
  už patřil Chatům — vybrané byly Chaty, na ploše bylo „Obecné". Shell se ptá na vybranou
  sekci aplikace; klíčem je i velikost zásobníku Nastavení (návrat na kořen). **Obecně: efekt, který zapisuje do zásobníku, nepatří do
  obrazovky, která může být zrovna na odchodu.**
- **Šipka zpět je v sekci jen na compactu.** V dvoupanelu je seznam sekcí pořád vidět vedle
  a sekce se NAHRAZUJE, nevrší se zásobník — stejně jako konverzace.
- **Do Nastavení se chodí z menu účtu na každé šířce** (fotka ve vyhledávací pilulce seznamu
  chatů, `AppNavigation.openSettings`), a proto vždycky na kořen — menu slibuje „Nastavení",
  ne sekci, kterou tam uživatel kdysi nechal. Seznam sekcí má proto šipku zpět do Chatů vždycky
  (`SettingsScreen(onBack)`), i v dvoupanelu.
- **Šipka seznamu sekcí volá `closeSettings`, ne `goBack`.** `goBack` odebírá jednu položku,
  a v dvoupanelu je to otevřená sekce — efekt `ensureSettingsSection` ji hned vrátil a šipka
  nedělala nic. Dokud vedle stál rail, šipka v dvoupanelu nebyla a nic se nepoznalo.
  Systémové zpět to nepotřebuje: `NavDisplay` ho zavolá za obě položky scény naráz.
- **Seznam sekcí nemá ViewModel.** Obsah (účet, notifikace, motiv) patří sekcím; kdyby si účet
  načítal i seznam, ptali bychom se na něj v dvoupanelu dvakrát. Ze stejného důvodu se ViewModel
  sekce vyrábí až uvnitř `when` nad sekcí — složí se jen jedna větev.
- **Nadpis skupiny a její karta jsou jeden kus** (`SettingsGroup`). Jako dva sousedi ve sloupci
  sekce by nadpis visel stejně daleko od svojí karty jako od té předchozí.
- **Řádek nese klikání celý, ovládací prvek má `onClick = null`.** Do přepínače ani kolečka se
  nemá trefovat a čtečka obrazovky má číst jednu položku, ne dvě; semantiku dodá
  `selectable(role = RadioButton)` / `toggleable(role = Switch)`.
- **`ListItem` na tónované kartě NEDĚDÍ `contentColor` karty.** Text si barví sám
  (`onSurface`), takže `Surface(contentColor = onPrimaryContainer)` kolem něj nic neudělá —
  v tmavém tématu vyšel světlý název na světlé kartě Uložených zpráv. Barva popředí se musí
  předat i do `ListItemDefaults.colors`. Totéž platí pro vybraný řádek v `secondaryContainer`
  (výběr země) a pro `onSurfaceVariant`: ten je počítaný na surface role, na vybrané položce
  patří `on*Container` (čas a ikony u vybraného chatu).
- **Pozor na názvy barev `ListItemDefaults.colors`**: `contentColor` (ne `headlineColor`)
  a `supportingContentColor` (ne `supportingColor`). Hádaný název se pozná až překladem.
  Totéž ikona: `leadingContentColor`, ne `leadingIconColor` — ta patří STARÉMU přetížení
  s `headlineColor`, a v jednom volání se obě sady míchat nedají.
- **Velký nadpis se na jednom panelu sbaluje, v dvoupanelu ne** (`SettingsTopBar`,
  `settingsScrollBehavior`, podle `LocalTwoPane`). `LargeFlexibleTopAppBar` byl bez scroll
  behavior, takže se nesbaloval vůbec a na telefonu trvale bral sedminu výšky. Sbalený je 64dp
  lišta a **barvu nemění** (`scrolledContainerColor = surface`) — tónovaný pruh přes celou šířku
  je důvod, proč seznam chatů velký bar opustil. V dvoupanelu by se sekce vpravo sbalila, zatímco
  krátký seznam sekcí vlevo zůstal velký: dvě hlavičky různé výšky vedle sebe.
- **Spodní inset patří do scrollovaného obsahu, ne na kontejner** (`settingsContentPadding`,
  `settingsContentBottom`). S paddingem ze Scaffoldu na sloupci končila dlouhá sekce ostrou
  hranou nad navigační lištou — tentýž vzor jako seznam chatů a výběr chatu.
- **Sekce mají ikony** (`SettingsSection.icon`), v barvě popisu — na vybraném řádku tedy
  `onSecondaryContainer`, ne `onSurfaceVariant`. Ikona je dekorace (`contentDescription = null`),
  název stojí hned vedle. „Co je nového" má megafon, ne raketu: raketou se uvnitř sekce značí
  novinka u jedné poznámky.

### Soukromí účtu

Obecné → Soukromí → Soukromí účtu (`AccountPrivacyRoute`, `AccountPrivacyScreen`): seznam
nastavení se stavem („Moje kontakty · 2 výjimky") a detail jednoho z nich (`PrivacySettingRoute`,
`PrivacySettingScreen`): Všichni / Moje kontakty / Nikdo a výjimky „Vždy povolit" a „Nikdy
nepovolit" (lidé i skupiny, přidávají se přes `ChatPicker`). Hranice je `PrivacyRepository`
(`TdlPrivacyRepository`), pravidla jsou čisté funkce v `core/telegram/AccountPrivacy.kt` (testy
`AccountPrivacyTest`, `PrivacySettingTest`).

- **Je to nastavení ÚČTU**: TDLib ho drží na serveru a platí na všech zařízeních. Pravidla jsou
  živá (`UpdateUserPrivacySettingRules`, odběr PŘED prvním čtením), takže změnu z jiného klienta
  obrazovka ukáže sama. **Čas přečtení (`ReadDatePrivacySettings`) update NEMÁ** — čte se při
  otevření a přepínač se pohne hned, po selhání se vrátí.
- **Pravidla jsou seznam, kde vyhrává PRVNÍ shoda** a bez shody je zákaz. Výjimky proto jdou
  před základ (`AllowAll` / `AllowContacts` / `RestrictAll`), který je poslední. Tentýž člověk
  nemůže být „vždy" i „nikdy" (`withAdded` ho z druhé strany vyřadí) — TDLib by vzal pravidlo,
  které stojí dřív, a uživatel by nepoznal proč.
- **Zapisuje se CELÝ seznam** (`setUserPrivacySettingRules`, jiný zápis TDLib nemá), takže co
  obrazovka neumí (`AllowBots`, `AllowPremiumUsers`, `RestrictBots`, `RestrictContacts`), se
  přenese z naposledy přečtených pravidel (`privacyRulesOf`) — ale jen to, co stálo PŘED
  základem. Za základem se k ničemu nedostane a s novým základem by změnilo význam.
- **Výjimky, které se základem neplatí, se nezahazují** (`mergeHiddenExceptions`): u „Všichni"
  nemá „Vždy povolit" smysl a do zápisu nejde, ale přepnutí tam a zpátky ho musí vrátit. Server
  je po zápisu nevrátí, takže je drží stav obrazovky a čtení je s ním slučuje.
- **Past: zapsaná pravidla se berou za platná HNED, ne až s updatem** (`apply` ve
  `PrivacySettingViewModel`). První verze porovnávala nový zápis s naposledy PŘEČTENÝMI
  pravidly: rychlé A → B → A pak návrat porovnala se starým A, nic neposlala — a na serveru
  zůstalo B. Hlídá to test v `PrivacySettingTest` s podstrčeným repository.
- **Hledání podle čísla jen tam, kde číslo vidět není** (`privacyRowShown`): když telefon vidí
  všichni, najde ho podle čísla každý tak jako tak. TDLib tam bere jen Všichni / Moje kontakty
  bez výjimek (`PrivacySetting.FindByPhone`).
- **Hlasové zprávy omezit jde jen s Premium** — bez něj detail stav jen ukáže
  (`PrivacySettingState.editable`), server by zápis odmítl.
- **Kanál výjimkou být nemůže** (TDLib bere jen lidi a členy skupin), výběr ho nenabízí; tajný
  chat je člověk s id uživatele (`memberOf`).
- **Řádek „Soukromí účtu" je v bloku Soukromí první**, nad blokovanými.
- **Ověřeno na emulátoru JEN ČTENÍM** (anglicky): seznam se skutečnými stavy účtu a detail
  „Naposledy viděn" s výjimkami, nic se nepřepnulo. **Zápis na skutečný účet ověřený NENÍ**
  (mění soukromí všude) — zápis, rychlé přepínání a selhání pokrývají jen testy s podstrčeným
  repository. Zaškrtnutí vybrané volby `uiautomator` u řádků nehlásil (`checked="false"` u všech
  tří), vizuálně snímkem to ověřené není. iOS jen přeložené a slinkované.

### Úprava profilu

Karta účtu v Obecných otevře `EditProfileRoute` (`EditProfileScreen`, `AccountRepository`): fotka,
jméno, příjmení, uživatelské jméno a bio.

- **Routa se vrší nad sekci Obecné** — na telefonu jako obrazovka, v dvoupanelu nahradí sekci
  vpravo. **Zpět tam musí být `PopUntilContentChange`** (jen když je úprava nahoře): dvoupanel
  zůstává dvoupanelem, takže výchozí `PopUntilScaffoldValueChange` odebral úpravu i sekci pod
  ní a gesto zpět odešlo z Nastavení rovnou do Chatů. Klik na jinou sekci úpravu ze zásobníku
  odebere taky, jinak by visela pod novou sekcí.
- **Fotka se ukládá hned, texty až tlačítkem Uložit.** Fotka má vlastní nahrávání; kdyby čekala
  na Uložit, zmizela by s odchodem. Ořízne se na čtverec podle výřezu z ořezu (`SquarePhoto`,
  platformní; viz „Ořez profilové fotky") — kreslí se v kruhu a neořezanou by každý klient
  ořízl jinak.
- **Text polí je Compose stav přímo ve ViewModelu**, ne `StateFlow` — jinak poskakuje kurzor.
- **Uživatelské jméno se kontroluje dvakrát**: místní pravidla hned při psaní
  (`usernameFormatError`, testy `UsernameFormatTest`), pak dostupnost na serveru po pauze
  (`checkChatUsername`). Vlastní účet se ptá přes **soukromý chat se sebou** (id chatu = id
  uživatele) — TDLib jiný chat odmítne větou „Can't check username for private chat with other
  user". Nezjištěná dostupnost ukládání neblokuje, rozhodne server.
- **Jméno, uživatelské jméno a bio jsou tři volání.** Když jedno selže, další se neposílají
  a co prošlo, se zapíše jako výchozí stav — další pokus ho neposílá znovu.
- **Strop bia posílá server** (`bio_length_max`, bez Premium 70); počítadlo je vidět pořád.
- **Účet je živý** (`AccountRepository.account` nad `UpdateUser`): karta v Obecných i fotka ve
  vyhledávací pilulce se po úpravě změní samy, i když se profil upravil na jiném zařízení.
- **Ověřeno bez zásahu do účtu**: načtení, všechny tři stavy kontroly jména (krátké, obsazené
  „durov", volné), odchod bez uložení a zpět v dvoupanelu. Uložení a změna fotky mění skutečný
  profil, proto se zkouší jen se souhlasem.

### Ořez profilové fotky

Vybraná fotka (vlastní profil, úprava skupiny i založení skupiny nebo kanálu) se napřed otevře
v ořezu (`PhotoCropOverlay`): fotka pod kruhovým výřezem, posun a přiblížení prsty, „Hotovo"
vrátí výřez a teprve pak se fotka ořízne a nahraje. Dřív se ořezávala vždycky ze středu.

- **Výřez je v PODÍLECH rozměrů fotky** (`CropRegion`), ne v pixelech. Ořez ukazuje fotku
  zmenšenou na 1600 px (Coil), platforma ořezává originál; poměr stran mají oba stejný.
  Na pixely ho převádí `squarePixels` a srovná na čtverec uvnitř fotky — zaokrouhlení podílů
  by jinak z kruhu udělalo elipsu o pixel.
- **Geometrie je čistá funkce** (`PhotoCropGeometry`, testy `PhotoCropGeometryTest`): fotka
  musí kruh vždycky celý zakrývat, přiblížení 1–5× kolem prstů (bod pod prsty zůstane na
  místě, jako v prohlížeči médií).
- **Překryv přes celou obrazovku, ne `Dialog`.** Dialog je na Androidu vlastní okno a ve
  společném kódu (`DialogProperties` má jen tři volby) nejde nechat sahat pod stavovou lištu;
  nad černým ořezem by svítil pruh ztmavené obrazovky. Obrazovka ho proto dává za Scaffold do
  `Box`u, zpět zavírá `NavigationBackHandler`. Stejná cesta jako náhled přílohy nad polem.
- **Fotka čeká ve ViewModelu** (`cropping`), ne v obrazovce: výběr fotky je ve ViewModelu
  odjakživa a otočení displeje ořez nesmí zavřít.
- **Ověřeno na emulátoru** ve formuláři „Nová skupina" (náhled fotky ukazuje výsledek bez
  nahrání čehokoli): fotka na šířku vyplní kruh výškou, posun doleva se zastaví na okraji
  fotky a náhled ukáže přesně výřez z kruhu; zpět ořez zavře a fotka se nezmění. **Přiblížení
  dvěma prsty ověřené jen testy** (přes `adb` dvěma prsty neťukneš), iOS jen přeložené.
- **Výběr fotek na emulátoru může být prázdný**, než si picker načte knihovnu — po pár
  vteřinách se mřížka objeví sama. Testovací obrázek jde do galerie `adb push` do
  `/sdcard/Pictures` a vysílání `MEDIA_SCANNER_SCAN_FILE`; po ověření ho smazat i z MediaStore.
- **Ořez se nesmí kreslit mimo svou plochu** (`clipToBounds` na ploše fotky). Posunutá nebo
  přiblížená fotka (`graphicsLayer`) jinak přetekla přes nadpis i stavovou lištu.

#### Video jako profilovka

Z výběru jde vybrat i video: stejný ořez, nadpis „Upravit video" a pod ořezem pás snímků,
na kterém se volí úsek (nejvýš 10 s, bez zvuku) a snímek pro statickou fotku. Nahraje se jako animovaná fotka (`InputChatPhotoAnimation`,
`ProfilePhotoFile.Animated`) — u vlastního profilu, úpravy skupiny i založení chatu. Postup
sdílí všechny tři obrazovky (`ProfilePhotoCrop` v `core/ui`, testy `ProfilePhotoCropTest`),
převádí platforma (`ProfileVideo`).

- **Podmínky Telegramu jsou přísné**: čtvercové MP4, nejvýš 10 s, strana 160–1280 px, do 2 MB.
  Video z galerie nesplní skoro nikdy, takže se vždycky převádí: výřez, zmenšení na 800 px,
  10 s od zvoleného začátku, bez zvuku.
- **Výřez se vybírá nad SNÍMKEM** (`ProfileVideo.frame`, 1600 px) a v podílech platí pro
  celé video. Náhled z výběru příloh má jen 320 px — výřez by se vybíral z rozmazaného obrázku.
  Náhled u zakládaného chatu je týž výřez ze snímku, ne video.
- **Úsek a snímek pro statickou fotku** (`VideoTrim`, testy `VideoTrimTest`; pás `VideoTrimBar`):
  úsek má pevnou délku a posouvá se tahem jako celek, snímek je čárka v něm (tahem za ni nebo
  ťuknutím do pásu). Tah se rozhoduje podle toho, kde začal — u čárky snímek, jinde úsek; u videa
  do 10 s jde každý tah na snímek. Snímek jede s úsekem a leží vždycky UVNITŘ, nejpozději
  100 ms před koncem (poslední snímek animace začíná dřív, než skončí). Do TDLib jde jako
  `mainFrameTimestamp` = čas od začátku ÚSEKU, ne videa (`ProfilePhotoFile.Animated.mainFrameSeconds`).
- **Ořez ukazuje vybraný snímek**, takže se výřez vybírá nad tím, co bude fotkou, a náhled
  u zakládaného chatu je výřez z něj. Nový snímek se tahá až po 150 ms klidu. Výměna obrázku
  nesmí probliknout: rozměry se drží z posledního načtení (snímky téhož videa jsou stejně
  velké) a Coil dostane `placeholderMemoryCacheKey` z posledního úspěchu — bez toho by ořez při
  každé změně snímku spadl do načítání a zavřel se. Geometrie a posun výřezu zůstávají klíčované
  cestou PRVNÍHO snímku (sezení ořezu), ne aktuálního.
- **Snímky mají soubor na čas a velikost** (`profile-frame-<edge>-<ms>.jpg` vedle zdroje)
  a podruhé se jen vrátí. Snímek pro fotku je PŘESNÝ (`OPTION_CLOSEST`, na iOS nulové
  tolerance), dlaždice pásu nejbližší klíčový (rychlé). Zápis přes dočasný soubor
  a přejmenování: pás a ořez čtou souběžně.
- **Čtečka obrazovky tahat neumí**: pás má popis stavu („Úsek 0:07–0:17 · fotka 0:15")
  a akce po sekundě pro úsek i snímek.
- **Android: Media3 Transformer** (`Crop`, `Presentation`, ořez `MediaItem`, `setRemoveAudio`).
  `Crop` bere souřadnice −1…1, svisle zdola nahoru. Výřez platí pro OTOČENÝ obraz — dekodér
  snímky otočí dřív, než na ně jdou efekty; ověřeno videem z kamery s `orientation=270`. Export
  sdílí s kolečky `exportVideo` (`core/video`), datový tok 1,2 Mbit/s a když soubor přesto
  přeteče 2 MB, zkusí se polovina.
- **iOS: kompozice jen se stopou obrazu** (tím zmizí zvuk), zkrácená na 10 s,
  `AVMutableVideoComposition` s výřezem a export `MediumQuality` s `fileLengthLimit` na 2 MB
  (datový tok export nastavit neumí). `fileLengthLimit` se zapisuje setterem s vlastním importem
  (`setFileLengthLimit`) — jako vlastnost ho Kotlin nevidí. **Jen přeložené.**
- **Na emulátoru Transformer padá v dekodéru H.264** (`c2.goldfish.h264.decoder`,
  `queueInputBuffer error: 14`, v logu zamítnuté `memfd` SELinuxem) — tatáž vada jako u GIFů
  a videa. Převod se ověřil sondou, která v `exportVideo` vyřadí dekodéry goldfish
  (`DefaultAssetLoaderFactory` s `DefaultDecoderFactory.setMediaCodecSelector`); necommituje se.
- **Testovací video na emulátoru natočí systémová kamera** (`am start -a
  android.media.action.VIDEO_CAMERA`, ťuknutí na spoušť): virtuální scéna, žádní lidé, a video
  na výšku s otočením v metadatech. Dotaz na polohu odmítnout. Po ověření videa i fotky smazat
  z MediaStore.
- **Úsek a snímek ověřené na emulátoru** ve formuláři „Nová skupina" (nic se nenahrálo):
  19s video, úsek posunutý na 0:07–0:17 a snímek 0:15 (haptika `constant=26` v `dumpsys
  vibrator_manager`), export 718 × 718 a 9,2 s, jeho první snímek je výřez z 0:07 a snímek
  v 7,65 s odpovídá vybrané fotce; náhled ve formuláři je výřez ze snímku 0:15. Snímky exportu
  vytáhne na Macu `AVAssetImageGenerator` v krátkém Swift skriptu (ffmpeg v systému není);
  zdrojové video z emulátorové kamery macOS NEotevře, srovnává se se snímky, které vyrobila
  aplikace. **Že server vezme `mainFrameTimestamp` z vybraného snímku, ověřené NENÍ** —
  chtělo by to nahrát profilovku (skutečný účet nebo další testovací chat).
- **Ověřeno na emulátoru** v testovací skupině „Plume test zalozeni": 21s video na výšku →
  čtverec 718 × 718, 9,7 s, 604 kB, natočený správně a přesně podle výřezu; prohlížeč
  profilovek ukazuje u fotky „Přehrát" (server ji vzal jako animovanou). Fotka jde dál starou
  cestou. **Vlastní profil a založení chatu s videem ověřené nejsou** (mění skutečný účet /
  zakládají další chat), jdou ale stejnou cestou.

## Dvoufázové ověření

Nastavení → Obecné → Soukromí → Dvoufázové ověření (poslední řádek bloku, „Zapnuto" /
„Vypnuto"; dokud stav nedorazí, podtitul není). Obrazovka stavu (`TwoStepRoute`,
`TwoStepScreen`) a průvodci: zapnutí (heslo dvakrát, nápověda, nepovinný obnovovací e-mail
a kód z něj), změna hesla, změna e-mailu, vypnutí a zapomenuté heslo — e-mailem, nebo bez něj
reset po sedmi dnech čekání. Hranice je `PasswordRepository` (`TdlPasswordRepository`,
doména `TwoStepVerification.kt`), kroky průvodce čisté funkce v `TwoStepFlow.kt` (testy
`TwoStepFlowTest`, ViewModel `TwoStepViewModelTest`).

- **Heslo s novým e-mailem začne platit až po kódu z e-mailu.** `setPassword` s e-mailem vrátí
  stav s `pendingEmail` (maska a délka kódu) a heslo zapne teprve `checkRecoveryEmailAddressCode`.
  Průvodce proto odesílá na kroku E-mail (`commitsAt`), ne na nápovědě, a z kroku Kód se zpět
  NEvrací (`previous` = `null`): změna už je na serveru, a zopakovat ji by poslalo další kód.
  Rozdělané potvrzení e-mailu (zavřený průvodce, jiné zařízení) ukazuje obrazovka stavu jako
  kartu se „Zadat kód", s posláním kódu znovu a se zrušením
  (`cancelRecoveryEmailAddressVerification`).
- **Bez e-mailu se nejdřív zeptá** (dialog „Pokračovat bez e-mailu?"): zapomenuté heslo pak
  půjde zrušit jen po týdnu. Na server do potvrzení nejde nic.
- **Kroky jsou stav ViewModelu, ne routy.** V zásobníku navigace by hesla putovala do
  serializovaného stavu, který přežije i zabití procesu. Ze stejného důvodu **hesla nejsou
  `rememberSaveable`**, drží je Compose stav ve ViewModelu a po konci průvodce (i po zpět
  z prvního kroku) je `clearSecrets()` vynuluje. Zpět uvnitř průvodce jde o krok zpátky
  (`NavigationBackHandler`).
- **Hesla se nelogují ani v chybách.** Chyba z TDLib jde do logu (`logger.w` s výjimkou), vstup
  ne. Heslo se neořezává (mezera na konci je jeho součást), kód a e-mail ano. Nápověda shodná
  s heslem se odmítne hned, stejně jako při přihlášení.
- **Vypnutí napřed ověří současné heslo** (`getRecoveryEmailAddress` — čtení, které heslo
  vyžaduje, ale nic nemění) a teprve pak se ptá dialogem. Tlačítko kroku proto říká
  „Pokračovat", ne „Uložit" — uložení je až v dialogu.
- **Zapomenuté heslo bez e-mailu je reset** (`resetPassword`): `Pending` = heslo se zruší
  k datu (karta se „Zrušit reset" a `cancelPasswordReset`), `Declined` = zkusit znovu až
  k `retryAt`, `Done` = zrušené hned. Stav čekání se čte z `PasswordState.pendingResetDate`
  (`resetProgress`).
- **`EMAIL_INVALID` má vlastní větu** v `userMessage`; zbytek chyb (`PASSWORD_HASH_INVALID`,
  `CODE_INVALID`, `PASSWORD_RECOVERY_EXPIRED`…) jde tabulkou jako jinde.
- **Past: vlastní pomocník na log s českým textem neprojde `verifyNoHardcodedText`.** Kontrola
  pouští diakritiku jen na řádcích s `logger.`; `logFailure("Nepodařilo se…")` se proto
  vrátil zpátky na `onFailure { logger.w(it) { "…" } }`.
- **Ověřeno**: na skutečném účtu jen ČTENÍ stavu (řádek „Vypnuto", obrazovka „vypnuté"
  s „Nastavit heslo"). Průvodci na emulátoru PODSTRČENÝM repository (sonda v Koinu, necommituje
  se): zapnutí s e-mailem a kódem i bez e-mailu, chyby (neshoda hesel, nápověda = heslo,
  neplatný e-mail, špatný kód), obnova e-mailem (špatný i dobrý kód, nové heslo), reset bez
  e-mailu a jeho zrušení, vypnutí se špatným i dobrým heslem. **Skutečné nastavení hesla,
  skutečný kód z e-mailu a reset ověřené NEJSOU** — mění skutečný účet; ověří je uživatel
  zapnutím na zařízení. iOS jen přeložené a slinkované.

## Zařízení (aktivní relace)

Sekce Nastavení „Zařízení" (`features/devices`): kde je účet přihlášený, odhlášení jednoho nebo
všech ostatních, weby přihlášené přes Telegram, automatické odhlášení nečinných a přihlášení
jiného zařízení QR kódem. Hranice je `DeviceRepository` v `core/telegram`.

- **Seznam se po každé akci ČTE ZNOVU**, nic se nepřepisuje lokálně. Co odhlášení udělalo
  s ostatními relacemi (a jestli server vůbec poslechl), ví jen server.
- **„Byli jste to vy?" je karta nahoře v seznamu chatů** (`UnconfirmedLoginCard`), ne jen
  v Nastavení — tam by ji nikdo nehledal. Přichází `UpdateUnconfirmedSession` hned po startu,
  proto je repository eager (`createdAtStart`). Připojený bot (`SessionTypeConnectedBot`) se
  nezobrazuje — `confirmSession` bere jen id relace zařízení.
- **Čerstvě přihlášené zařízení (asi 24 h) nesmí odhlašovat ostatní** —
  `FRESH_RESET_AUTHORISATION_FORBIDDEN`, v `userMessage` má vlastní větu. Na emulátoru, který se
  přihlásil právě teď, to tlačítko skončí touhle hláškou; chyba to není.
- **Půl roku je 183 dní, ne 180** (`INACTIVE_TTL_OPTIONS`). Tak ho server posílá u účtu, který
  volbu nikdy neměnil; se 180 se ve výběru nic neoznačilo a řádek hlásil „183 dní".
- **Odhlášení cizího zařízení je hned pod hlavičkou detailu a sheet se otevírá celý**
  (`enabledValues = Hidden, Expanded`). Dokud bylo tlačítko pod údaji a přepínači, napůl
  rozbalený sheet ho schoval pod okraj a vypadalo to, že jednotlivé zařízení odhlásit nejde —
  jen všechna naráz.
- **Odhlášení TOHOTO zařízení je v Obecných, ne v detailu zařízení** — maže lokální data TDLib
  a je to jiný následek než odhlášení cizího zařízení.
- **Čtečka QR kódů je platformní** (`rememberQrScanner`). Android: **Google code scanner**
  (`play-services-code-scanner`) — skenovací obrazovku kreslí Play služby, aplikace nepotřebuje
  oprávnění ke kameře ani CameraX s ML Kitem; bez Play služeb se nespustí. iOS: AVFoundation
  (`AVCaptureMetadataOutput`) ve vlastním dialogu — VisionKit `DataScannerViewController` je jen
  pro Swift. `startRunning` blokuje, proto mimo hlavní vlákno; delegát se drží silnou referencí.
  Simulátor kameru nemá, skončí hláškou „čtečku se nepodařilo spustit".
- **Na server jde jen `tg://login?token=…`** (`isLoginLink`). Čtečka přečte jakýkoli QR kód
  a server by na cizí obsah odpověděl chybou, ze které uživatel nic nepozná.
- **Odkaz `tg://login` se z ťuknutí NEOTEVÍRÁ**, jen ze čtečky. Přihlašovací odkaz poslaný ve
  zprávě je typický podvod („naskenuj/otevři tohle"); oficiální klienti ho taky jen skenují.

## „Co je nového" v aplikaci (`core/changelog`)

Pátá sekce Nastavení (`WhatsNewSettings`) ukazuje tytéž poznámky po vydáních, od nejnovějšího.
Po aktualizaci nic nevyskakuje — novinky jsou tam, kdo je hledá (převzato z mlogu, bez jeho
`WhatsNewGate`).

- **Poznámky se zapékají při buildu**: gradle task `:client:shared:generateChangelog` pustí
  `release_notes.py bundle` a vygeneruje `GeneratedChangelog.kt` do
  `build/generated/changelog/kotlin`, který je zdrojovým adresářem `commonMain`. **Tentýž skript
  jako pro Play** — dvě kopie pravidel (co je `internal`, rozsah mezi tagy) by se rozešly.
- **Kotlin, ne compose resource**: seznam je dostupný synchronně a chyba generátoru spadne při
  překladu. Vygenerovaný soubor leží v `build/`, takže ho `verifyNoHardcodedText` nečte.
- **Jen tagy `--merged HEAD`**: build ze starší větve nesmí ukazovat verze, které v něm nejsou.
  Release se staví z tagu, takže aktuální vydání v seznamu je; tagy se řadí podle verze a `-manual`
  projde. Vydání jen s `internal` poznámkami se vynechá.
- **Task je schválně nikdy up-to-date** (vstupem jsou i tagy) a trvá asi 2 s; překlad se přeskočí,
  když vyjde stejný obsah. **Bez pythonu nebo gitu build nespadne**: zapíše se prázdný seznam
  (sekce pak řekne, že sestava novinky nemá) a důvod se zaloguje. CI klonuje s `fetch-depth: 0`,
  bez tagů by seznam byl prázdný.
- **Jazyk poznámky se bere z prostředků** (`changelog_note_language` = `cs`/`en`), ne z locale.
  Poznámka tak vždycky sedí s jazykem, který Compose vybral pro zbytek obrazovky, i s jazykem
  nastaveným jen pro aplikaci — a funguje stejně na iOS, kde `NSLocale.currentLocale` jde za
  regionem systému (viz „Výběr země a formát čísla").
- **Vydání po deseti s „Zobrazit starší verze"**: sekce je sloupec, ne lazy seznam, a celá
  historie má stovky řádků.
- **Novinku od opravy odliší tvar ikony** (raketa, brouk), čtečce obrazovky slovo v popisu ikony.
- **Ověřeno na emulátoru** (anglicky i česky přes `set-app-locales`, stránkování, přežití změny
  jazyka). Na iOS jen přeložené a slinkované.

## Synchronizace nastavení mezi zařízeními bez vlastního backendu

Stav: **průzkum, nic z toho v kódu není.** Jde o data, která má jen Plume (barva motivu,
vyčlenění nepřečtených, rychlost hlasovek, viděné nápovědy gest…), ne o nastavení, které zná
Telegram sám.

- **Napřed to, co Telegram synchronizuje sám.** Rychlá reakce (`setDefaultReactionType`,
  `QuickReaction`), výchozí automatické mazání (`DefaultAutoDeleteSettings`), upozornění po
  rozsazích, archiv a složky už jdou přes server a platí i v oficiálních aplikacích. Kdo přidává
  volbu, kterou Telegram zná, má ji napojit na jeho API a ne na `AppSettings`. Vlastní úschova
  je jen pro to, co Telegram nemá.
- **Automatické stahování se bere ze serveru JEN PŘI PŘIHLÁŠENÍ, změna jde na server hned**
  (`AutoDownloadSync`, `TdlAutoDownloadPresets`, testy `AutoDownloadSyncTest`
  a `AutoDownloadPresetsTest`). Nové zařízení tak začne z posledního nastavení účtu, ale po
  přihlášení se každé chová samostatně: telefon s malým úložištěm a tablet chtějí jiná pravidla
  i na stejné Wi-Fi, a čtení při každém startu by změnu z jednoho zařízení vnutilo všem.
  - **Server drží tři předvolby účtu**, „nízká, střední a vysoká spotřeba dat“
    (`account.saveAutoDownloadSettings`). TDLib je přes `setAutoDownloadSettings(settings, type)`
    mapuje na roaming, Wi-Fi a ostatní sítě (`SaveAutoDownloadSettingsQuery`
    v `AutoDownloadSettings.cpp`). Proč to Telegram drží na serveru, dokumentace neříká. Nejspíš
    kvůli výchozím hodnotám, které server ladí podle země (`getAutoDownloadSettingsPresets`
    vrací předvolby „for the current user“).
  - **„Přihlášení“ = `Ready` po přihlašovací obrazovce.** Každý přihlašovací stav (číslo, kód,
    heslo, QR, odhlašování) přepne stav na `SettingsSync.Pull` a další `Ready` stáhne. Přihlášený
    účet jde při startu rovnou do `Ready` (`WaitTdlibParameters → Ready`), takže se server znovu
    nečte. Stav je v DataStore jako `auto_download_sync`.
  - **Aktualizace z verze bez synchronizace** (`auto_download_sync` chybí): kdo si úrovně
    změnil, tomu se nepřepíšou a jdou na server. Kdo ne, dostane nastavení účtu.
  - **Nepovedený zápis** (bez sítě) nechá `SettingsSync.Push` a zkusí se při dalším `Ready`, tedy
    při dalším startu aplikace. `authStage` je `StateFlow` a `Ready` se během běhu neopakuje.
  - **Úroveň ze serveru = nejbližší podle stropu videa** (GIFy a kolečka). Hranice leží
    v geometrickém středu mezi stropy Plume (1 · 5 · 20 MB), převod Plume → server → Plume vrací
    tutéž úroveň. Horní hranice (10 MB) je ostrá: výchozí předvolby Telegramu jsou nízká 0,
    střední 10 MB a vysoká 15 MB videa (naměřeno na emulátoru) a s `>=` by se střední, tedy
    mobilní data, převedla na vysokou. Takhle dají přesně výchozí úrovně Plume. Zapisují se jen sítě, jejichž úroveň se liší, a zbylá pole předvolby (přednačítání,
    datový tarif hovorů, strop fotek) zůstanou, jak je nastavil oficiální klient. Před zápisem se
    proto předvolby čtou znovu.
  - **Přepisuje to i nastavení oficiálních klientů.** Předvolby jsou účtu, ne Plume. Jestli
    si je oficiální aplikace po změně stáhnou hned, nebo až při přihlášení, ověřené není.
  - Vlastní volba je hlavní stav, ne cache: `AppSettings.loadedAutoDownload()` a
    `autoDownloadSync()` čekají na načtení souboru. Výchozí hodnota `StateFlow` před načtením by
    synchronizaci řekla, že si uživatel nic nevybral.
- **Kandidát: CloudStorage mini aplikací.** Je to úložiště klíč–hodnota pro dvojici „bot,
  uživatel“ na serverech Telegramu: nejvýš **1024 klíčů**, klíč 1–128 znaků `A-Za-z0-9_-`,
  hodnota 0–4096 znaků. Bot API pro něj žádnou metodu nemá, takže ho ani majitel bota nepřečte.
  Šifrované mezi zařízeními ale není a server ho přečte.
  - **TDLib na to nemá vlastní metody.** Dřív tu stálo `setWebAppCloudStorageValue` /
    `getWebAppCloudStorageValue`, jenže ty neexistují. Klient mini aplikace volá
    `sendWebAppCustomRequest(botUserId, method, parameters)` (MTProto
    `bots.invokeWebViewCustomMethod`) s metodami `saveStorageValue` `{"key","value"}`,
    `getStorageValues` `{"keys":[…]}`, `deleteStorageValues` `{"keys":[…]}` a `getStorageKeys`
    `{}`. V Plume to už běží jako `WebAppRepository.customRequest` v mostu mini aplikací.
  - **Jde to BEZ otevřené mini aplikace a i u bota, který mini aplikaci nemá.** Ověřila to sonda
    na emulátoru 2026-10-09 se souhlasem uživatele. Testovací Notifikátor `@NoTiBoBoBot` má
    `has_main_web_app = false` a tlačítko menu `commands`, a přesto `saveStorageValue`,
    `getStorageValues`, `getStorageKeys` i `deleteStorageValues` prošly. Totéž u
    `@DurgerKingBot`, jehož mini aplikace otevřená nebyla, a čtení u `@gif`. Podmínky z dokumentace
    metody server tedy NEKONTROLUJE. Je to ale chování serveru, ne slib v dokumentaci, a může se
    změnit. Neznámá metoda vrací `METHOD_INVALID`.
  - **Bota musí TDLib znát, jinak `User not found`.** Hned po `AuthorizationStateReady` sonda
    u Notifikátoru dvakrát narazila. Uspěla, až když TDLib bota znal, protože se mezitím načetl
    seznam chatů. Synchronizace si proto bota napřed přeloží přes `searchPublicChat(username)`.
  - **Bot se nikde neukázal.** `getTopChats(TopChatCategoryWebAppBots)` byl před zápisem i po něm
    stejný (`@BotFather` a `@DurgerKingBot`, kterého účet dřív otevřel). Chat s Notifikátorem ani
    s DurgerKingem nevznikl (`positions` 0 u DurgerKinga). **Pohled do oficiálního klienta zbývá.**
  - **Strop 4096 znaků na hodnotu sonda nepotvrdila**: logcat dlouhý řádek uřízl i s výsledkem.
  - **Postup, až se to bude stavět:** bota založí uživatel přes BotFather, jeho uživatelské jméno
    bude v kódu jako konstanta. Jeden klíč na nastavení, hodnota `{v, ts}`, a při konfliktu
    vyhraje novější zápis. Server změny neohlašuje, takže se čte při přihlášení (viz automatické
    stahování výš: ze serveru při přihlášení, změna hned na server). Zápisy se sdružují, protože
    limit rychlosti neznáme. Synchronizuje se jen to, co není vázané na zařízení: zámek aplikace,
    velikost písma, stažený model ani úložiště ne.
- **Proč ne tichý push (FCM / APNs):** potřebuje běžící server (klíče FCM/APNs, párování push
  tokenů mezi zařízeními uživatele) a iOS tiché pushe (`content-available: 1`) škrtí.
- **Proč ne zpráva v Uložených zprávách, koncept zprávy, složka nebo soukromý kanál:** oficiální
  Telegram je ukáže. Technická zpráva s JSONem na vrchu Uložených se dá omylem smazat. Koncept
  vytáhne chat do seznamu a kanál v něm svítí. Telegram Passport chce heslo a je vidět
  v Soukromí. **Výjimka je ruční záloha:** soubor s nastavením poslaný do Uložených na pokyn
  uživatele, který ho pak obnoví na druhém zařízení. Je vidět, ale uživatel o něm ví.
- **DeviceStorage a SecureStorage mini aplikací nesynchronizují**, zůstávají v zařízení.
- **Mimo Telegram:** iOS má `NSUbiquitousKeyValueStore` (iCloud, živá synchronizace mezi
  zařízeními téhož Apple ID, 1 MB), ale jen mezi zařízeními Apple. Auto Backup na Androidu data
  jen obnoví při nové instalaci, živá synchronizace to není.
- **Deterministické podbarvení chatů bez synchronizace:** pokud jde o ambientní podbarvení,
  nejčistší je barvu odvodit deterministicky z profilové fotky protějšku (`ColorScheme.fromSeed`)
  nebo z `chatId` / barvy jména z Telegramu (`senderColor`). Protože obě zařízení stahují stejnou
  fotku / data, spočítají identickou barvu lokálně bez nutnosti přenášet jakákoliv data.

## Čistič odkazů od sledování (`core/url/UrlCleaner`)

Automatické pročištění webových odkazů od sledovacích a analytických parametrů (UTM parametry, reklamní
click ID a trackery sociálních sítí / sdílení).

- **Kde se čistí:**
  - Při odeslání zprávy (`ConversationViewModel.send`) a úpravě zprávy (`ConversationViewModel.sendEdit`).
  - Při otevírání odkazů v prohlížeči (`UrlOpener.android.kt` a `UrlOpener.ios.kt`).
- **Formátování a text:** `UrlCleaner.cleanComposedText` čistí výskyt odkazů přímo v textu,
  aktualizuje a posouvá rozsah formátovacích značek (`List<FormatSpan>.afterEdit`) a čistí i cílové
  URL v explicitních hypertextových odkazech (`FormatSpan.url`).
- **Co se čistí:**
  - Univerzální parametry: `utm_source`, `utm_medium`, `utm_campaign`, `utm_term`, `utm_content`, `utm_id` atd.
  - Reklamní / click trackery: `fbclid`, `gclid`, `gclsrc`, `dclid`, `msclkid`, `twclid`, `ttclid`, `yclid`, `mc_eid` apod.
  - Služby a sociální sítě: YouTube `si` a `feature` (při zachování funkčního videa `v` a času `t`),
    Spotify `si`, TikTok, Twitter/X `s` a `t`, Instagram `igshid` a `igsh`, Amazon `ref_`.
- **Nastavení uživatele:** Uloženo v `AppSettings.stripTrackingParameters` (DataStore Preferences,
  výchozí hodnota `true`). Přepínač je v Nastavení → Soukromí a bezpečnost pod zprávami (`StripTrackingParametersRow`).



## Diskrétní čtení (`AppSettings.discreetReading`)

Nastavení → Obecné → Soukromí → „Diskrétní čtení": otevřená konverzace nehlásí přečtení
(`viewMessages`) ani „píše…" (`sendChatAction`). Výchozí vypnuto.

- **Potlačuje se v `ConversationViewModel`, ne v repository**: `markRead` (viditelné zprávy)
  a `reportTyping(true)` se vrátí dřív, než sáhnou na TDLib. Ukončení psaní projde — nic
  neprozradí a zhasne „píše…" ohlášené před zapnutím volby. Ruční „Označit jako přečtené"
  v seznamu chatů a v notifikaci dál přečte, je to vědomá akce.
- **Přečtení odejde, až uživatel v chatu sám něco pošle** (text, přílohy, hlasovka/kolečko —
  `readAfterOwnMessage`), nebo ťukne na čip „Označit jako přečtené" nad polem. Oboje volá
  `ChatRepository.readChat` — totéž co seznam chatů (nejnovější zpráva, zmínky, reakce).
  Odpověď na zprávu, kterou protějšek vidí jako nepřečtenou, by diskrétnost stejně prozradila.
- **Čip vidí jen chat samotný**, ne vlákno a téma: `readChat` přečte celý chat, tedy i to,
  co uživatel ve vláknu neviděl. Kolik je nepřečtených, nese živý `ChatRepository.unreadCount`
  — `ChatInfo.unreadCount` je jen stav z otevření chatu.
- **Skok na zmínku „@" zmínku nepřečte** — přečetla by i historii po ni. Tlačítko pak vede na
  tutéž zmínku, dokud se chat nepřečte.
- **Co volba nekryje**: poslech hlasovky (`openMessageContent` nastaví „poslechnuto"), otevření
  mizející fotky a zobrazení příspěvku v kanálu (počet zhlédnutí jde přes `viewMessages`, který
  se nevolá — v kanálu se tak zhlédnutí nepočítá vůbec).
- **Ověřeno na emulátoru**: s volbou chat Telegram (2 nepřečtené) po otevření odznak 2
  podržel a nad polem byl čip; log `Diskrétní čtení: N viditelných zpráv se nehlásí`
  a `…psaní se nehlásí`. Ťuknutí na čip a přečtení po vlastní zprávě na zařízení ověřené
  NENÍ (Notifikátor má otevřený jiná session a čte ho hned, takže se nedalo pozorovat).

## Úložiště (`features/settings/StorageSettings`)

Šestá sekce Nastavení: kolik zabírají stažené soubory po druzích a po chatech, databáze zpráv,
mazání mezipaměti (všude i v jednom chatu), „Uchovávat média" (3 dny / týden / měsíc / navždy)
a stažený model Gemmy. Hranice je `StorageRepository` (`TdlStorageRepository`: `getStorageStatistics*`
a `optimizeStorage`), pravidla jsou čisté funkce v `core/telegram/Storage.kt` a `cleanupDue`
(testy `StorageTest`, `StorageCleanerTest`). Odkaz `tg://settings/data_and_storage` vede sem.

- **Rychlý přehled (`getStorageStatisticsFast`) velikost SOUBORŮ neříká pravdu.** Na emulátoru
  hlásil 12 MB, podrobný přehled pak 65 MB — velké číslo skákalo o pětinásobek. Z rychlého
  přehledu se proto bere jen databáze (ta sedí), součet souborů až z podrobného, který prochází
  celý adresář (na emulátoru ~5 s). Mezitím je v kartě „Počítám soubory…".
- **Úklid podle stáří volá `optimizeStorage` aplikace sama** (`StorageCleaner`, ze `startKoin`,
  nejvýš jednou denně při návratu do popředí a hned po změně volby). TDLib by uměl uklízet sám
  (`use_storage_optimizer`), ale meze bere z voleb `storage_max_files_size` / `storage_max_file_count`
  s nepopsanými výchozími hodnotami — úklid podle stáří by pak mohl mazat i podle velikosti. Proto
  `size = Long.MAX_VALUE`, `count = Int.MAX_VALUE` výslovně; s `-1` by platily ty volby.
- **Ruční mazání posílá druhy souborů VŽDY výslovně.** Prázdný `fileTypes` znamená pro TDLib
  „výchozí výběr" (všechno kromě náhledů, avatarů, samolepek a tapet) — mazalo by i to, co uživatel
  odškrtl. Nuly u `size`, `ttl`, `count` a `immunityDelay` = smazat všechno z výběru bez ohledu
  na stáří; `returnDeletedFileStatistics` dá „uvolněno N MB".
- **Smazaný soubor se musí dát stáhnout znovu.** `TdlFileRepository` si pamatuje, o které soubory
  už žádal, a `request` u dalšího odběru skončil hned na začátku — smazaný obrázek by se už nikdy
  nestáhl. Hotový soubor, který TDLib updatem ohlásí jako nehotový, se proto z té paměti vyřadí.
  Po mazání se zahodí i `knownPaths` v `RemoteImage` (cesty ke smazaným souborům).
- **Chaty bez přiřazení jsou jeden řádek „Ostatní chaty a soubory"** (TDLib je vrací s id 0,
  i víckrát) a smazat ho nejde: `optimizeStorage` s chatem 0 maže soubory MIMO chaty, ne „ostatní
  chaty". Na emulátoru TDLib přiřadil chatům jen část souborů (7 chatů z limitu 30, zbytek 58 MB
  v ostatních) — soubory bez známého chatu nemá k čemu přiřadit.
- **Předvybrané mazání jsou média**, ne samolepky a „Ostatní" (avatary, náhledy): malé a pořád
  potřebné, po smazání by se seznam chatů kreslil bez fotek, než se znovu stáhnou.
- **Databáze se nemaže**: TDLib na to volání nemá, zmizí jen s odhlášením. Řádek to říká.
- **Neznámá doba uchování spadne na „navždy"** (`KeepMedia`, ukládá se názvem) — volba z novější
  verze nesmí znamenat mazání.
- **Ověřeno na emulátoru** (česky): přehled po druzích a chatech, Uložené zprávy se záložkou,
  mazání souborů chatu s testovacím botem (3,2 MB, přehled se přepočítal na avatary). Emulátor
  (Pixel_9, 4 GB) byl při tom přetížený (swap 1 GB, `Console.app` na hostiteli ~270 % CPU) a hlásil
  ANR celého systému — hlavní vlákno aplikace přitom čekalo v `epoll_wait`, ověřeno
  `run-as … cat /proc/<pid>/task/<pid>/wchan`. iOS jen přeložené.

### Balíčky pro překlad v telefonu

Skupina „Balíčky pro překlad" v Úložišti ukazuje stažené jazyky překladu v telefonu (viz
„Překlad v telefonu" v `konverzace-akce.md`) s velikostí a košem (`OnDeviceTranslator.downloadedPacks`
/ `deletePack`). Vestavěná angličtina tam není — smazat nejde. Na iOS skupina chybí (překladač
v DI není, `StorageViewModel` ho bere přes `getOrNull`).

- **ML Kit velikost nehlásí**, počítá se ze složky `no_backup/com.google.mlkit.translate.models/<jazyk>_en`.
  Je to vnitřní uspořádání knihovny: kde složka není, velikost se neukáže (`bytes = null`).
  Čeština měla 40 MB (oba směry po 6 MB a společná data), ne 30 MB, jak se psalo v sheetu.
- **`deleteDownloadedModel` uklidí jen část**: ze 40 MB nechal 12 MB (`translate_csen`
  a `translate_encs`), přestože balíček už nevedl jako stažený a jeho evidence
  (`shared_prefs/com.google.mlkit.translate.download_manager.xml`) byla prázdná. Zbytek by zůstal
  navždy, takže `deletePack` po úspěchu smaže složku jazyka sám — ale jen když ji ML Kit opravdu
  přestal vést. Ověřeno: `no_backup` ze 45 MB na 4,7 MB. Sirotky z dřívějška (před opravou)
  to neuklidí; zmizí s dalším stažením a smazáním téhož jazyka.
- **Titulek „Smazat balíček Čeština?"**, ne „Smazat Čeština?" — název jazyka v 1. pádě musí
  stát jako přívlastek, jinak věta nedává smysl.
- **Ověřeno na emulátoru Fold**: skupina s „Čeština · 40 MB", dialog, snackbar „Balíček Čeština
  smazán", zmizení skupiny, nové stažení přes sheet překladu (výzva se po smazání znovu ukázala).
  iOS jen přeložené.

## Menší přepínače

Rychlá reakce a odkazy v aplikaci jsou ve skupině Chaty v Obecných, jazyk aplikace v Obecných
nad ní, velikost písma zpráv ve Vzhledu. Enter odesílá z fyzické klávesnice (viz „Klávesové
zkratky" v `navigace.md`).

### Velikost písma zpráv (`LocalMessageTextSize`, `messageTextStyle`)

- **Rozsah 12–20 sp po jednom bodu, výchozí 16 uprostřed**; výchozí se neukládá (jako motiv).
  Hodnota mimo rozsah se při čtení přitáhne k mezi — novější verze s jiným rozsahem nic nerozbije.
- **Jde přes CompositionLocal z kořene aplikace, ne přes ViewModel konverzace.** `MessageText` je
  v bloku seznamu bez přístupu k DI a `ConversationScreen` je v dexu na hraně (viz CLAUDE.md);
  vzor je `LocalAutoDownload`. Statický local: mění se jen v Nastavení a pak má překreslit všechno.
- **Mění se jen výchozí styl `MessageText`.** Velká emoji si nesou vlastní `display*` styl a zůstávají;
  citace, náhledy odkazů a ankety taky — zvětšuje se to, co se ve zprávě čte.
- **Výška řádku jde ve stejném poměru (16/22)**, ne pevná. Systémové zvětšení písma se uplatní
  navíc (sp), nastavení ho nenahrazuje.
- **Během tahu posuvníkem se mění jen náhled**, uloží se po puštění: každý zápis by přes statický
  local překreslil celou aplikaci na každý dílek. Dílek tahu tiká (`tick`).
- **Náhled bublin leží na `surface`**, ne přímo na kartě nastavení: příchozí `surfaceContainerHigh`
  by s kartou (`surfaceContainerHighest`) splývala.

### Odkazy v aplikaci (`UrlOpener`, `AppSettings.openLinksInApp`)

- **Výchozí je v aplikaci**, jako v oficiálním klientovi. Jen `http`/`https`; `mailto:`, `tel:`
  a ostatní jdou dál přes `ACTION_VIEW`.
- **Aplikace pro odkaz má přednost i v režimu „v aplikaci"**: od Androidu 11
  `FLAG_ACTIVITY_REQUIRE_NON_BROWSER` (spadne na `ActivityNotFoundException`, když by odkaz vzal
  jen prohlížeč), na iOS `openURL` s `UniversalLinksOnly`. Do Androidu 10 jde rovnou karta.
- **Karta se spouští z activity v popředí** (slabý odkaz z `ActivityLifecycleCallbacks`). Z
  aplikačního kontextu by potřebovala `NEW_TASK` a visela by v přehledu úloh vedle Plume.
- **Ověřeno na emulátoru (Android 16)**: developer.android.com otevřel `CustomTabActivity`
  v úloze Plume (`dumpsys activity activities`, tentýž `t9`), křížek vrátil do konverzace. Odkaz
  na YouTube převzala aplikace YouTube (`UrlActivity` v logu `START u0`) — na čerstvém emulátoru
  ho pak sama přeposlala do Chromu, to už není náš krok. **Na čerstvém emulátoru Chrome napřed
  ukáže úvodní obrazovku s podmínkami**; zpět ji přeskočí a karta se otevře.

### Rychlá reakce (`QuickReaction`, `AppSettings.doubleTapReaction`)

- **Dvě úrovně**: jestli dvojité ťuknutí reaguje, si pamatuje aplikace; KTERÁ reakce, je nastavení
  účtu na serveru (`setDefaultReactionType`) a platí i v oficiálních klientech. Výběr to říká.
- **Getter TDLib nemá** — výchozí reakci (`updateDefaultReactionType`) i seznam emoji
  (`updateActiveEmojiReactions`) ohlásí jen updatem po přihlášení. `TdlQuickReaction` proto vzniká
  eagerně, stejně jako ostatní repository nad jednorázovými updaty.
- Chování v konverzaci viz „Reakce" v `emoji-samolepky-reakce.md`.

### Jazyk aplikace (`AppLanguage`)

- **Volbu drží systém, ne `AppSettings`**: Android 13+ `LocaleManager.applicationLocales`, iOS
  Nastavení u aplikace. Kopie v aplikaci by se se systémovým výběrem rozešla.
- **Android potřebuje `localeConfig`** (`res/xml/locales_config.xml` v `:client:androidApp`) —
  bez něj Plume v systémovém výběru jazyka aplikací vůbec nebyl, protože přeložené texty jsou
  v composeResources, ne v Android `values-*`.
- **Seznam jazyků se generuje ze složek `values-*`** (`:client:shared:generateAppLanguages`):
  angličtina, jazyky, které píšeme sami (`completeLocales`), a komunitní jazyky přeložené aspoň
  z 80 % (`minLanguageCoverage`). Z něj vzniká `APP_LANGUAGES` (`GENERATED_APP_LANGUAGES`)
  i `locales_config.xml` (`generateLocaleConfig` v `:client:androidApp`, přidané jako
  generovaný zdroj prostředků přes `androidComponents`). Dřív se nový jazyk zapisoval ručně
  na čtyři místa a jazyk z Weblate (jen složka `values-<kód>`) by v aplikaci byl, ale vybrat
  by nešel. Složka `values-pt-rBR` je jazyk `pt-BR`.
  - **Rozpracovaný jazyk se nenabízí** — v aplikaci by střídal věty s angličtinou. Build to
    ohlásí („de má 40 % — do výběru jazyků zatím nejde").
  - **Názvy jazyků dává systém** (`nativeLanguageName`: Android `Locale.getDisplayName`, iOS
    `NSLocale.displayNameForKey`), ne `language_name_<kód>` v každém `strings.xml`.
  - **iOS složky `<kód>.lproj` nevyrobí build** — nesou vlastní texty iOS a v balíčku podle
    nich iOS nabízí jazyky. `verifyIosLocalizations` (na `check`) jazyk bez nich jen ohlásí,
    Android ho mezitím nabízí. Nový jazyk na iOS = `Localizable.strings` v `iosApp/iosApp`
    i `iosApp/ShareExtension` (a region v Xcode).
  - Ověřeno sondou s dočasným `values-de` (úplná kopie: v seznamu, v `locales_config.xml`
    a varování iOS; jeden klíč: venku) a v APK `aapt2 dump xmltree` nad `locales_config.xml`.
- **Do Androidu 12 se skupina nezobrazuje**: systém jazyk pro jednu aplikaci nezná a obchvat by
  chtěl AppCompat. Na iOS řádek jen otevře Nastavení — z aplikace to změnit nejde.
- **Názvy jazyků jsou ve vlastním jazyce** („Čeština" i v anglické aplikaci), aby se dalo vrátit
  z jazyka, kterému člověk nerozumí.
- **Ověřeno na emulátoru (Android 16)**: přepnutí na češtinu přeložilo obrazovku hned, proces
  zůstal týž (pid), `cmd locale get-app-locales` hlásí `[cs]`; systémové Nastavení → Jazyk
  aplikace nabízí češtinu i angličtinu. iOS jen přeložené.
