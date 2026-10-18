# Šifrovaná databáze, spojení a proxy

Poznatky k jedné oblasti aplikace. Obecná pravidla projektu a rejstřík ostatních souborů jsou
v [CLAUDE.md](../../CLAUDE.md); poučení z opravy v téhle oblasti patří sem.

## Šifrovaná databáze TDLib

TDLib drží historii zpráv v SQLite databázi v adresáři aplikace a šifruje ji klíčem, který dostane
v `setTdlibParameters`. Klíč je 32 náhodných bajtů z **`core/secrets`** — další hranice vedle
`core/telegram`, `core/logging` a `core/update`: `java.security.KeyStore` a `platform.Security.*`
smí být importované jen tam.

- **`DatabaseKeyStore` je rozhraní, ne `expect class`** (jako `ShareTargetPublisher`
  a `CrashReporter`). Na jeho hodnotách visí rozhodnutí, jestli se smažou lokální data, a to chce
  test s podstrčeným úložištěm — `expect class` se v `commonTest` nahradit nedá.
- **Úložiště je obecné `SecretStore`** (`KeystoreSecretStore` / `KeychainSecretStore`), protože
  stejně se ukládá i záznam zámku aplikace (`AppLockStore`, viz [zamek-aplikace.md](zamek-aplikace.md)).
  `DatabaseKeyStore` a `AppLockStore` jsou prázdná rozhraní nad ním a v Koinu se skládají
  delegací (`object : DatabaseKeyStore, SecretStore by KeystoreSecretStore(…)`) — jen aby
  se nedalo podstrčit jedno místo druhého. `typealias` by to nezajistil: Koin by oba viděl
  jako týž typ. Každé tajemství má vlastní soubor i vlastní klíč v Keystore / záznam v Keychainu.
- **Klíč NESMÍ vyžadovat ověření uživatele.** Push budí mrtvý proces, když telefon leží zamčený
  v kapse, a TDLib potřebuje klíč dřív, než se složí jakékoli UI. Klíč za otiskem prstu znamená,
  že z pozadí nechodí notifikace vůbec. Z téhož důvodu je iOS `kSecAttrAccessibleAfterFirstUnlock…`,
  ne `WhenUnlocked`.
- **Tím je daná i mez.** Klíč dostane každý kód běžící pod naší aplikací: chrání to proti vytažení
  disku, záloze, `adb backup` a forenznímu čtení souborů, ne proti rootnutému zařízení s běžící
  aplikací. Stejný kompromis dělá i oficiální klient — jen to musí být napsané, aby se to za rok
  nepletlo za víc.
- **Android Keystore neumí uložit bajty, jen klíče.** To je past, na které se to nejčastěji láme:
  `AndroidKeyStore` není trezor na data. Klíč se proto obaluje AES-GCM klíčem z Keystore a na disku
  leží jen šifrotext (`filesDir/tdlib-key`, `[délka IV][IV][šifrotext]`). `androidx.security:security-crypto`
  dělá totéž, ale je to závislost navíc kvůli padesáti řádkům — stejná úvaha jako u `app-update`
  proti `app-update-ktx`.
- **`setInvalidatedByBiometricEnrollment` se NENASTAVUJE.** Přidaný otisk prstu by zneplatnil klíč
  a uživatel by přišel o přihlášení kvůli nastavení telefonu.
- **Soubor s klíčem se píše přes dočasný soubor a přejmenování.** Zabitý proces uprostřed zápisu
  by nechal na disku uříznutý klíč — a s ním databázi, která už nejde otevřít.
- **Na iOS klíč NEPATŘÍ do App Group.** Rozšíření sdílení TDLib nespouští a databázi nevidí,
  takže `kSecAttrAccessGroup` by jen rozšířil plochu bez užitku.
- **`ThisDeviceOnly`, a proto se adresáře TDLib vylučují ze zálohy** (`NSURLIsExcludedFromBackupKey`
  v `TelegramPaths.prepare()`). Klíč se na jiné zařízení nepřenese, takže obnovená databáze by se
  stejně zahodila — jen by do zálohy putovala celá historie zpráv. Na Androidu to řeší
  `allowBackup="false"` pro celou aplikaci. `prepare()` běží před KAŽDÝM `setTdlibParameters`,
  ne jednou při startu: adresáře vznikají znovu i po smazání nečitelné databáze a na jediném
  místě se na to nedá zapomenout.

### Pořadí, migrace a cesta ven

Pravidlo je čistá funkce `unlockDatabase` (`core/telegram/DatabaseUnlock.kt`, testy
`DatabaseUnlockTest`), ne tělo session: pořadí kroků je to jediné, co se na tom dá rozbít, a uvnitř
třídy s živým klientem by se to neotestovalo.

- **Klíč se ukládá DŘÍV, než se jím databáze zašifruje.** Obrácené pořadí („nejdřív zašifrovat,
  pak uložit") má okno, ve kterém zabitý proces nechá zašifrovanou databázi bez klíče — a to je
  nenávratné odhlášení. Takhle je nejhorší možný stav ten, že databáze za klíčem zaostává, a ten
  se pozná a dožene.
- **Kroky jsou tři a každý pojmenovává jeden stav světa:** otevřít uloženým klíčem (běžný start,
  jedno volání jako dřív) → nejde to, otevřít prázdným a zašifrovat (instalace z verze bez
  šifrování i přerušená migrace) → nejde ani to, databáze je zašifrovaná klíčem, který nemáme,
  takže se zahodí. První spuštění uspěje hned v prvním kroku: databáze ještě není a TDLib ji
  založí rovnou zašifrovanou, takže nikdy nevznikne ani na okamžik nešifrovaná.
- **Databázi s prázdným klíčem NELZE otevřít neprázdným** — TDLib vrátí 401 (hláška
  „Wrong database encryption key", ověřeno `strings libtdjsonjava.so | grep "encryption key"`).
  Přešifruje se až otevřená databáze, přes `setDatabaseEncryptionKey`. Není to tedy „změnit jeden
  řádek": bez migračního kroku by se každá existující instalace odhlásila.
- **Mazat se smí JEN na chybu o klíči.** Kdyby se mazalo při každém selhání `setTdlibParameters`,
  odhlásil by uživatele plný disk nebo překlep v `api_id`. Sedí na to test — a ověřilo se
  pokusem, ne úvahou: po odstranění téhle podmínky selžou přesně ty dva testy, které ji hlídají.
- **Soubory jdou pryč s databází, ne zvlášť.** `fileId` platí jen v jedné databázi TDLib, takže
  bez ní jsou stažená média neadresovatelné bajty.
- **Ztráta klíče se nevysvětluje vlastní hláškou.** Po zahození nečitelné databáze se otevře nová
  a uživatel skončí na přihlašovací obrazovce — po přenosu na nový telefon je to stejně to, co od
  Telegramu čeká. `AuthStage.Failed` by tu byl slepý konec: obrazovka chyby nemá tlačítko, kterým
  se dá pokračovat.
- **Selhání samotného zašifrování aplikaci nezastaví.** Databáze zůstane nešifrovaná, klíč uložený,
  a příští start to zkusí znovu druhým krokem. Loguje se `e` s výjimkou, takže je to v Crashlytics
  vidět jako problém.

### Prázdný klíč NEZNAMENÁ nešifrovanou databázi

**TDLib má SQLCipher zalinkovaný vždycky a `db.sqlite` je neprůhledná i s prázdným klíčem** —
ověřeno na emulátoru ještě před změnou: soubor nemá hlavičku „SQLite format 3", `strings` v něm
ani v `td.binlog` nenajde jediné jméno, adresu ani český text. Z prázdného klíče se tedy nestává
čitelná databáze, ale databáze šifrovaná **konstantou, kterou zná každý, kdo má TDLib**. To je
zamlžení, ne ochrana; smysl téhle změny je vyměnit ji za klíč, který má jen tohle zařízení.

Plyne z toho i to, **čím se to NEDÁ ověřit**. První verze tohohle odstavce radila porovnat
`strings files/tdlib/db.sqlite` před změnou a po ní — jenže ten test vyjde stejně v obou
případech a „nic nenašel" by se dalo přečíst jako úspěch i u kódu, který klíč vůbec nenastaví.
**Jediný důkaz, že klíč zabral, je, že databáze už PRÁZDNÝM klíčem otevřít nejde.**

### Jak to ověřit

Přesně to říká cesta ven, takže se obojí ověří jedním pokusem. Nová verze se nainstaluje přes
existující data (`adb install -r`, data zůstanou) a v logu musí být:

1. **při prvním spuštění** „Databáze TDLib zašifrovaná." a hned za ní `AuthorizationState -> Ready`
   — migrace proběhla a uživatel zůstal přihlášený,
2. **při druhém spuštění** už ta hláška NENÍ — databáze se otevřela rovnou uloženým klíčem,
   jedním voláním,
3. **po poškození klíče** (`run-as eu.tintera.plume sh -c 'head -c 61 /dev/urandom > files/tdlib-key'`)
   „Uložený klíč nejde rozšifrovat" (`KeystoreSecretStore`) → „Databázi TDLib nejde odemknout" s `TdlThrowable: Wrong
   database encryption key` → `AuthorizationState -> WaitPhoneNumber`, bez pádu, a `db.sqlite`
   spadne z megabajtů na jednotky kilobajtů.

**Krok 3 je ten důkaz.** Aby se došlo až k mazání, musel selhat i pokus s PRÁZDNÝM klíčem —
tedy databáze opravdu není ta, kterou TDLib zakládá sám. Ověřeno na emulátoru: 15 MB → 12 kB
a přihlašovací obrazovka.

**Push do zmrazeného procesu je čtvrtý krok** a ověřuje to, co žádný z předchozích: že se klíč
načte bez jakéhokoli UI. Aplikaci nestačí odsunout na plochu (zůstane na adj 700) — musí se
vytlačit do keše spuštěním pár jiných aplikací, pak je asi do dvaceti vteřin `isFrozen=true`
(`dumpsys activity processes eu.tintera.plume`). Zpráva od testovacího bota pak musí skončit
notifikací se správným textem. Ověřeno na emulátoru.

### Release build (R8)

Šifrování klíč nikde nedohledává reflexí, takže nová keep pravidla nepotřebuje — ověřit se to
ale musí na zařízení, protože chybějící keep pravidlo se při buildu nepozná (viz „R8").
Ověřeno na emulátoru: databáze se uloženým klíčem otevřela (`WaitTdlibParameters` → `Ready`,
bez přešifrování), a **po `am kill` z pozadí se aplikace vrátila do otevřené konverzace** —
tedy i polymorfní serializace klíčů navigace drží.

- **Release APK se dá rovnou podepsat debug klíčem**, místo stavět a pak přepodepisovat:
  `-PreleaseStoreFile=$HOME/.android/debug.keystore -PreleaseStorePassword=android
  -PreleaseKeyAlias=androiddebugkey -PreleaseKeyPassword=android`. Minifikace je vlastnost
  buildu, s podpisem nesouvisí, a `adb install -r` přes debug build nechá data i přihlášení.
- **Cesta ven se na release buildu ověřit NEDÁ.** `run-as` na nepodepsatelné aplikaci hlásí
  „package not debuggable" a `adb root` na emulátoru s Play službami neprojde, takže se
  `tdlib-key` nemá jak poškodit. Testuje se tedy na debug buildu; z R8 je v té větvi citlivé
  jen `is TdlThrowable` a řetězcová konstanta, obojí přejmenování přežije.

### Jak to ověřit na iOS

Aplikace na simulátoru **do unified logu nic nepíše** — `log stream --predicate 'process ==
"Plume"'` ukáže jen systémové vrstvy (Security, FileURL, RunningBoard), ne Kermit, a
`simctl launch --console-pty` nevypíše nic. Ověřuje se proto chováním a stavem na disku, ne logem.

- **Keychain simulátoru se dá číst**: `~/Library/Developer/CoreSimulator/Devices/<id>/data/
  Library/Keychains/keychain-2-debug.db`, tabulka `genp`. `svce` i `acct` jsou zašifrované,
  ale **`agrp` a `pdmn` jsou v plaintextu**, takže záznam se pozná podle skupiny
  (`<TEAM>.eu.tintera.plume`) a atribut přístupnosti podle `pdmn`: **`cku` =
  `AfterFirstUnlockThisDeviceOnly`** (`ak` = WhenUnlocked, `aku` = WhenUnlockedThisDeviceOnly,
  `dku` = AlwaysThisDeviceOnly). Soubor je WAL, takže se před dotazem kopíruje i s `-wal`/`-shm`.
- **Že `SecItemCopyMatching` čte**, se pozná podle toho, že se `hex(data)` toho řádku mezi starty
  NEMĚNÍ — kdyby čtení selhalo, kód vyrobí a uloží klíč nový.
- **Vyloučení ze zálohy** je v systémovém logu (`kExcludedFromBackupXattrName set on path: …`)
  a v `xattr -l` na obou adresářích (`com.apple.metadata:com_apple_backup_excludeItem`).
- **Nepřihlášená aplikace se ověřit nedá.** Bez účtu nemá TDLib co uchovávat, `td.binlog` vůbec
  nevznikne a `db.sqlite` dostává při každém startu nový inode — vypadá to jako mazání, a není
  to ono. **Sledovat se musí `td.binlog`**: přežije-li restart se stejným inodem, databáze se
  uloženým klíčem otevřela.
- **Cesta ven se vyvolá `xcrun simctl keychain <id> reset`.** Odebrat jednotlivý záznam simctl
  neumí, takže to smaže keychain celého simulátoru (i cizím aplikacím na něm). Po restartu musí
  `db.sqlite` i `td.binlog` spadnout na jednotky kilobajtů, `td.binlog` dostat nový inode
  a aplikace skončit na přihlašovací obrazovce, bez pádu. Ověřeno: 14 MB → 12 kB.

## Stav spojení a obnova po pozadí

Spojení s Telegramem je **něco jiného než přihlášení**. `AuthStage` říká, jestli máme účet;
`ConnectionState` (`ConnectionRepository`, doslova TDLib `UpdateConnectionState`), jestli má kudy
chodit. Dokud se nesledovalo, vypadala nepřipojená aplikace úplně normálně — jen nic nechodilo.

- **Aplikace se po delší době v pozadí nepřipojila, dokud se nekillnula.** Systém v dozu (a při
  přepnutí sítě) zabije sokety, jenže TDLib je zevnitř dál považuje za živé; mrtvolu pozná až na
  vlastním odpočtu, který v dozu neběžel. Killnutí pomáhalo proto, že nový klient otevírá čerstvé
  sokety.
- **Lék je `setNetworkType`.** Dokumentace: *„Calling this method forces all network connections to
  reopen … so it must be called whenever the network is changed, even if the network type remains
  the same."* Volá se tedy **při každé změně sítě i při každém návratu do popředí** — právě to
  druhé je ten případ z dozu, kdy se síť nezměnila a spojení přesto nestojí.
- **Hlásí to `ConnectionController`, startovaný ze `startKoin`**, ne z UI: síť se mění i tehdy,
  když se žádná obrazovka neskládá.
- **Šum ze sítě se musí odfiltrovat, skutečná výměna ne.** Android posílá `onCapabilitiesChanged`
  i několikrát za minutu (kvalita signálu, ověření připojení) a vynucovat kvůli tomu znovuotevření
  všech spojení je horší než nedělat nic. Naopak přelezení mezi dvěma Wi-Fi je změna, kterou
  porovnání samotného typu nezachytí — proto `NetworkStatus` nese kromě typu i pořadové číslo,
  které se zvedá jen v `onAvailable` / `onLost`.
- **Bez sítě při startu to musí monitor říct sám.** `registerDefaultNetworkCallback` se bez
  sítě neozve vůbec, takže TDLib dostal výchozí `Other` z `ConnectionController` a aplikace
  spuštěná v letovém režimu hlásila napořád „Připojování…". Monitor se proto před registrací
  zeptá na `activeNetwork` a bez ní ohlásí `None`. iOS to nepotřebuje: `NWPathMonitor` se ozve
  hned po startu i s nesplněnou cestou. Ověřuje se STUDENÝM startem v letovém režimu — zapnutí
  letového režimu za běhu chodí přes `onLost` a chybu neukáže.
- **Otočení displeje NENÍ odchod do pozadí.** `onStop` staré instance a `onStart` nové by jinak
  vypadaly jako návrat z pozadí a zbytečně shodily všechna spojení; pozná se to podle
  `isChangingConfigurations`.
- **Popředí se nebere z kompozice.** `LocalLifecycleOwner` by fungoval, ale žil by jen po dobu,
  kdy se skládá UI — stejný důvod, proč se ze `startKoin` startuje i `NotificationPresenter`.
  Android to čte z `registerActivityLifecycleCallbacks`, ne z `ProcessLifecycleOwner`: ten je
  v androidx větvi lifecycle, zatímco projekt jede na multiplatformní
  `org.jetbrains.androidx.lifecycle`, a míchat dvě verze téže knihovny kvůli jednomu příznaku
  nestojí za to. iOS bere oznámení `UIApplication`; `applicationState` se čte přes
  `dispatch_async` na hlavní frontě, protože odběr běží na `Dispatchers.Default`.
- **Volba `online` se musí nastavit, jinak nás server nikdy nevidí online.** Není to jen zelená
  tečka pro ostatní — dokud je aplikace na obrazovce, chodí s ní updaty rychleji.
- **`ConnectionState` se smí použít jen jako popisek pro člověka**, říká to dokumentace u něj.
  Nic se z něj proto neodvozuje, jen se vypisuje.
- **Stav je v hlavičce, ne ve vlastním pruhu, a všude stejně: znak a věta vedle sebe**
  (`ConnectionStatusIcon` v `core/ui`, text z `ConnectionState.label()`). Připojování
  a aktualizace mají `LoadingIndicator` (měnící se tvar), čekání na síť ikonu `cloud_off` bez
  pohybu. V seznamu chatů znak nahradí lupu a věta nápovědu vyhledávací pilulky (viz „Hlavička:
  vyhledávací pilulka"); v konverzaci jsou na místě podtitulu. Kdo se dívá na prázdný seznam,
  hledá vysvětlení nahoře.
- **Avatar stav nenese.** Dřív se avatar zvlnil, narostl a točil (`ConnectionIndicator`, září
  2026) — fotka účtu v pilulce i avatar chatu v hlavičce konverzace a v profilu bota. Uživatel
  to odmítl: avatar je identita, ne kontrolka. Roztočená tvář protějšku vypadala, jako by se
  něco dělo s NÍM, ne s naším spojením, a stav se přetahoval s tím, co avatar nese sám (tvar
  podle druhu chatu, tečka online, tečka archivu) — ořez se musel při výpadku měnit na
  obdélník, řádek hlavičky potřeboval rezervu na zvětšení a tečka se kreslila nad prstencem.
  Nic z toho už potřeba není — až na rezervu řádku hlavičky (4 dp, `HEADER_ROW_PADDING`):
  řádek ořezává tvarem vlnky stisku a bez rezervy zaoblený roh ukousl spodek teček „Waiting
  for network…", když byl podtitul nejširší prvek řádku (ověřeno na emulátoru).
- **Čekání na síť stojí, připojování se hýbe.** Stojící vlnka na avataru působila jako
  zamrzlá, a proto se dřív točila aspoň pomalu. Ikona bez sítě zamrzle nepůsobí, čte se jako
  stav. Klid proti pohybu rozliší oba stavy i bez barvy (ikona je v barvě obsahu, tvar
  v `primary`).
- **`Ready` se nehlásí.** V tom stavu aplikace stráví skoro celou dobu; „připojeno" je šum, zatímco
  „nepřipojeno" je jediná informace, která uživateli vysvětlí, proč mu nic nechodí. Proto
  `ConnectionState.label()` vrací u `Ready` `null`.
- **Hlavička konverzace: spojení nese podtitul, „online" tečka, jinak podtitul „naposledy…".**
  Bez spojení je v podtitulu znak a věta, s předností i před názvem skupiny ve vlákně nebo
  tématu — dokud spojení nestojí, je to jediné, co vysvětlí, proč nic nechodí. Mezi stavy
  výpadku se podtitul nevyměňuje celý (`contentKey` podle druhu), jinak by tvar začínal znovu.
  Tečka „online" je tatáž jako v seznamu chatů (`OnlineDot`), a proto **podtitul „online"
  nepíše** — tečka a slovo hned vedle sebe říkaly totéž dvakrát; čtečce obrazovky „online"
  řekne popis stavu hlavičky. **Bez spojení se dostupnost schová** (podtitul i tečka): dokud
  spojení nestojí, je to jen stará hodnota, kterou nemá co obnovit. „Píše…" v hlavičce není,
  viz níž.
- **Ověřuje se to bez dozu** — chování je stejné: odejít z aplikace a vrátit se (v logu musí být
  „Návrat do popředí, obnovuji spojení"), zapnout a vypnout letový režim (`adb shell cmd
  connectivity airplane-mode enable`), otočit displej (v logu nesmí být NIC).

## Proxy

Nastavení → Obecné → Připojení → Proxy (`ProxySettingsScreen`, `features/proxy`) a odkazy
`t.me/proxy?…` / `t.me/socks?…` (`InternalLink.Proxy`, dialog `ProxyLinkDialog` přes
`ProxyLinkFlow`). Hranice je `ProxyRepository` (`TdlProxyRepository`), pravidla `problem()`
a `shareLink()` v `core/telegram/Proxy.kt` (testy `ProxyTest`).

- **Proxy si drží TDLib** (`addProxy`, `enableProxy`, `disableProxy`), ne nastavení aplikace.
  Přežije restart, platí i před přihlášením a zapnutá smí být jen jedna — přepnutí řeší TDLib.
- **O změně seznamu TDLib update nepošle**, takže se seznam po každé akci ČTE ZNOVU. Odezva
  (`pingProxy`) se měří souběžně při otevření a tlačítkem v liště; zapnutí ani vypnutí ji
  neměří znovu. Nedostupná proxy odpoví až po vypršení (asi 10 s), mezitím „Měřím…".
- **Odkaz nic nezapíná sám**: dialog ukáže server, port, druh a odezvu a teprve „Připojit"
  proxy přidá a zapne. Odkaz může poslat kdokoli a provozovatel proxy vidí, kdy a odkud se
  aplikace připojuje. Dialog je třída nad `CoroutineScope` jako `ReportFlow` — odkaz může přijít
  z kterékoli obrazovky s textem a druhý ViewModel kvůli jednomu dialogu nestojí za to.
- **`InternalLinkTypeProxy.proxy` smí být `null`** (druh, který TDLib nezná) — vlastní hláška
  (`UnsupportedProxyException`), do prohlížeče by to nevedlo nikam.
- **TDLib tajný klíč MTProto PŘEVEDE**: z odkazu s hex klíčem (`ee…`) vrací `getProxies` týž klíč
  v base64url. Editor i odkaz z „Kopírovat odkaz" ukazují tuhle podobu; Telegram ji bere taky.
- **„Zapnutá", ne „připojeno přes ni"**: zapnutá proxy nemusí fungovat a připojení visí na
  „Připojování…" (v pilulce hledání místo lupy tvar). Proto je odezva u každé proxy — kdo
  si zapne mrtvou, vrátí se ťuknutím na „Přímé připojení".
- **Nově přidaná proxy se rovnou zapne**, upravená si stav nechá. HTTP proxy odkaz Telegramu
  nemá (`shareLink() == null`), „Kopírovat odkaz" se u ní nenabízí.
- **Ověřeno na emulátoru** neexistující proxy (`203.0.113.7`, dokumentační rozsah, a
  `127.0.0.1:1080`): odkaz od testovacího bota, dialog s „Nedostupná", připojení (spojení spadlo
  na „Připojování…"), přepnutí na přímé, úprava, kopírování odkazu, ruční SOCKS5 a smazání.
  **Fungující proxy ověřená NENÍ** — žádná po ruce nebyla. iOS jen přeložené.
- **Řádek Proxy v Obecných říká stav** („Zapnutá · server:port" / „Vypnutá · přímé
  připojení"; `ProxyStatusViewModel`, `proxyStatus`, testy `ProxyStatusTest`). Zapnutá mrtvá
  proxy je nejčastější důvod věčného „Připojování…" a bez stavu v řádku se na ni přišlo až na
  obrazovce proxy. Stav se čte znovu při každém návratu (`LifecycleResumeEffect`) — TDLib změnu
  proxy nehlásí. **Odezvu řádek neměří**: `pingProxy` jde přes každou uloženou proxy a patří na
  obrazovku proxy, ne ke každému otevření Nastavení. Dokud stav nedorazí, podtitul není žádný.
  Ověřeno na emulátoru: vypnutá → přidaná proxy → návrat ukáže zapnutou → smazání → vypnutá.
- **Proxy jde nastavit i na přihlašovací obrazovce** („Připojit přes proxy" pod krokem,
  `AuthStage.offersProxy`, testy `AuthViewModelTest`). Kde je Telegram blokovaný, nedorazí kód
  — a Nastavení je až za přihlášením. Je to TÁŽ `ProxySettingsScreen` přes celou plochu
  přihlášení, ne routa: přihlášení stojí mimo `NavDisplay`. Nabízí se jen v krocích, kde se čeká
  na uživatele; start klienta, odhlašování a chybu proxy nespraví.
  - **Zpět mají DVĚ obsluhy a ta přihlašovací se s otevřenou proxy vypíná**
    (`isBackEnabled = … && !showingProxy`). V kódu, hesle a QR zpět volá `restartLogin`
    (`destroy`) — bez toho by gesto zpět nad proxy zahodilo rozdělané přihlášení.
  - **Ověřeno na emulátoru podstrčeným stavem** (`WaitPhoneNumber`, pak `WaitCode`; necommituje
    se): přidání, zapnutí a smazání proxy, šipka i gesto zpět vrací na přihlášení. **V kroku
    kódu sonda nahradila `back()` logem** — skutečný `restartLogin` by účet na emulátoru zavřel;
    první zpět zavřelo jen proxy, `back()` přišlo až druhým. Nepřihlášená aplikace ověřená
    není (proxy pak platí pro první spojení), iOS jen přeložené.

### Sponzorovaný chat (proxy a oznámení)

Cizí chat, který do hlavního seznamu dal někdo jiný (TDLib `ChatPosition.source` →
`ChatSummary.sponsor`, `ChatSponsor`): kanál od provozovatele MTProto proxy, nebo veřejné
oznámení od Telegramu. Stojí nahoře ve vlastním bloku (`chatGroups`, test
`sponzorovanyChatJeNahoreZvlastNadPripnutymi`), místo času má štítek „Sponzor proxy" /
„Oznámení" a dlouhý stisk otevře vlastní nabídku s vysvětlením (`SponsoredChatSheet`).

- **Zdroj patří POZICI, ne chatu** (`FolderPosition.sponsor`), a TDLib ho posílá jen v hlavním
  seznamu. Kdo by ho hledal na `Chat`, nenajde nic.
- **Od proxy smazat ani opustit nejde** — TDLib `delete_dialog_history` u sponzorovaného chatu
  odpoví „Can't delete the chat" (přečteno ve zdroji `MessagesManager.cpp`). Jediná cesta ven je
  proxy vypnout, a proto nabídka vede do Nastavení → Obecné → Proxy
  (`AppNavigation.openProxySettings`, zpět po krocích).
- **Oznámení jde skrýt** `deleteChatHistory(removeFromChatList = true)` — tak to TDLib u
  `chatSourcePublicServiceAnnouncement` chce (jinak „Can't delete chat history without removing
  the chat"); `deleteOrLeave` z kanálu neodchází, uživatel v něm není. Text oznámení posílá
  server a stojí v nabídce; prázdný nahradí obecná věta.
- **Připnout, archivovat (ani tahem), přidat do složky, zablokovat a smazat se nenabízí** — chat
  není uživatelův. Zbývá ztlumit (navždy) a označit jako (ne)přečtené.
- **Ověřeno na emulátoru jen sondou** (Notifikátor v `publish` dočasně vydávaný za chat od proxy,
  pak za oznámení; necommituje se): blok nahoře, štítky, obě nabídky, „Nastavení proxy" a zpět.
  **Skrytí oznámení se nezkoušelo** — se sondou by smazalo historii chatu s botem. Skutečný chat
  od proxy chce fungující proxy se sponzorovaným kanálem a oznámení server posílá jen některým
  účtům.
