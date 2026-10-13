# Desktop (JVM)

Desktopová aplikace pro macOS, Windows a Linux: `jvm()` target v `:client:shared` (source set
`jvmMain`, pod `nonDynamicThemeMain` vedle iOS) a tenký vstupní bod `:client:desktopApp`
(Compose Desktop, `compose.desktop.application`). Stejné rozdělení jako u Androidu: platformní
`actual` zůstávají ve sdíleném modulu, vstupní bod jen spouští Koin a otevírá okno.

```bash
./gradlew :client:desktopApp:run                 # ladicí běh, data v .desktop-data/
./gradlew :client:desktopApp:createDistributable # Plume.app / složka s aplikací a runtime z jlinku
./gradlew :client:desktopApp:packageDmg          # balíček pro systém, na kterém se staví (Dmg/Msi/Deb)
```

## TDLib na desktopu: wrapper ho má, ale macOS knihovna je rozbitá

`tdl-coroutines` publikuje `tdl-coroutines-jvm` s nativní TDLib pro macOS, Linux i Windows
(x64 i arm64) v jednom jaru (109 MB). Wrapper si při prvním použití sám vybere knihovnu podle
`os.name`/`os.arch`, rozbalí ji do dočasného adresáře a nahraje. Vlastní build TDLib ani fork
wrapperu tedy potřeba nejsou.

- **macOS knihovna se mimo stroj autora nenačte.** `libtdjsonjava.dylib` (15.0.0) odkazuje
  absolutní cestou na Homebrew autora: `/opt/homebrew/opt/openjdk/…/libjvm.dylib` a
  `/opt/homebrew/opt/openssl@3/lib/libssl.3.dylib` (x64 totéž pod `/usr/local`). Výsledek je
  `UnsatisfiedLinkError: Library not loaded: …/libjvm.dylib` s jakýmkoli JDK, které není
  Homebrew `openjdk` — i na Macu s Homebrew OpenSSL. Nahlášeno jako
  [g000sha256/tdl-coroutines#163](https://github.com/g000sha256/tdl-coroutines/issues/163).
  Ukáže to `otool -L` nad knihovnou z jaru.
- **Obcházka je přepis jaru při buildu** (`TdlibJarTransform` v
  `client/desktopApp/build.gradle.kts`, Gradle artifact transform na `runtimeClasspath`):
  1. **jen nativní knihovna tohoto systému a architektury** (jar spadne na ~50 MB; jpackage
     stejně cizí systém zabalit neumí),
  2. **`install_name_tool`**: `libjvm` → `@rpath/libjvm.dylib`, OpenSSL → `@rpath/libssl.3.dylib`
     a `@rpath/libcrypto.3.dylib`, a **nový ad-hoc podpis** (`codesign -s -`) — arm64 Mac
     změněnou knihovnu s neplatným podpisem nenahraje,
  3. **přibalené OpenSSL** z Homebrew stroje, který staví (`-PopensslDir=…` jinak), s install
     name `@rpath/…`,
  4. **hook `TdlibJsonRepair`** (viz níž).
- **`@rpath` závislost dyld najde mezi UŽ NAHRANÝMI knihovnami podle install name.** `libjvm`
  je v procesu dřív než cokoli jiného a jeho install name je `@rpath/libjvm.dylib`; OpenSSL
  nahraje `preloadTdlibNativeDependencies()` v `main` dřív, než `startKoin` spustí TDLib.
  Wrapper rozbaluje jen svoji knihovnu, takže přibalené OpenSSL musí rozbalit a nahrát někdo
  jiný. Pořadí v `main` je proto povinné.
- **Ověřeno**: ladicí běh na macOS 26.6 (arm64, JDK 21 mimo Homebrew) se přihlásil QR kódem,
  načetl seznam chatů a odeslal zprávu; zabalená `Plume.app` (runtime z jlinku) dojde
  k přihlášení. Původní jar ve stejném JVM padá na `libjvm`. **Windows a Linux neověřené.**
- **Až #163 upstream opraví**, body 2 a 3 z transformu zmizí (a s nimi
  `preloadTdlibNativeDependencies` a požadavek na Homebrew OpenSSL při buildu). Body 1 a 4
  můžou zůstat.

### Další omezení binárek wrapperu (15.0.0)

- **macOS arm64 je `minos 26.0`**, x64 `minos 15.0` (`vtool -show-build`). Na Apple Silicon
  tedy desktop běží až od macOS 26.
- **Linux potřebuje glibc 2.38+** (Ubuntu 24.04, Debian 13) a systémové `libssl.so.3`.
- **Windows** si OpenSSL a zlib veze vedle `tdjsonjava.dll` sám.

## Hook TdlibJsonRepair na JVM

Totéž co na Androidu (CLAUDE.md, „Odpovědi TDLib, které wrapper nerozebere"): za každé
`org.drinkless.tdlib.JsonClient.receive` uvnitř `dev/g000sha256/tdl/` se vloží
`TdlibReceiveHook.afterReceive`. Na Androidu to dělá AGP instrumentace, tady ASM přímo v transformu
jaru — JVM jar wrapperu na rozdíl od androidího **obfuskovaný není**. `ClassWriter(reader, 0)`
bez přepočtu rámců stačí ze stejného důvodu jako `COPY_FRAMES` na Androidu: vložené volání
bere `String?` a vrací `String?`. ASM je na classpath build skriptu díky AGP v kořenovém
`plugins {}`. Kontrola po přihlášení (`checkJsonRepairWired`) platí i tady,
`tdlibJsonRepairWired = true`.

## Data a tajemství

- **Data aplikace** jsou tam, kde je systém čeká: `~/Library/Application Support/Plume`,
  `%APPDATA%\Plume`, `$XDG_DATA_HOME/plume` (`desktopDataDirectory`). Ladicí běh z Gradle má
  vlastní `.desktop-data/` v kořeni repa (`-Dplume.dataDir`) a `isDebugBuild` (`-Dplume.debug`).
- **Klíč databáze a záznam zámku: na macOS Klíčenka přes `/usr/bin/security`**
  (`DesktopSecretStore`). Nativní `SecItem*` by chtělo JNA nebo JNI. Tajemství jde do nástroje
  **standardním vstupem** (`security -i`), ne argumentem — argumenty procesu vidí každý přes
  `ps`. Chybějící položka je návratový kód 44 (`errSecItemNotFound`).
- **Windows a Linux: soubor s právy jen pro vlastníka** — slabší než systémový trezor, klíč leží
  na tomtéž disku co databáze. DPAPI a Secret Service jsou v roadmapě.
- **Ladicí běh má vlastní položky v Klíčence** (služba s příponou `.debug`). Se společnou by
  odhlášení v jednom smazalo klíč i druhému a ten by databázi zahodil (`unlockDatabase` ji bez
  klíče nepřečte).

## Notifikace

TDLib notifikace vydává, dokud aplikace běží (push na desktopu není), a `NotificationPresenter`
je předává systému přes `DesktopNotifier`: na macOS `UNUserNotificationCenter`, na Windows
toasty WinRT (oddíl „Notifikace na Windows" níž), na Linuxu AWT `TrayIcon.displayMessage`.

```bash
./gradlew :client:desktopApp:runDistributable -PdesktopDebug   # na macOS se oznámení dají zkoušet jen takhle
```

- **`TrayIcon.displayMessage` na macOS 26 neukáže NIC.** AWT pod ním volá zastaralé
  `NSUserNotification`; volání projde bez chyby, banner ani záznam v Centru oznámení nevznikne
  (ověřeno). Na macOS proto vlastní JNI most v Objective-C (`jvmMain/objc/PlumeNotifications.m`,
  `MacNotificationCenter`), který překládá `compileMacNotifications` v `client/shared` jako
  univerzální binárku do resources `jvmMain`. Blocky i delegát jsou v Objective-C nativní,
  takže JNA ani Swift není potřeba.
- **`UNUserNotificationCenter` chce proces s vlastním `.app` balíčkem.** V holém `java`
  (`./gradlew :client:desktopApp:run`) vyhodí `NSInternalInconsistencyException:
  bundleProxyForCurrentProcess is nil` a SHODÍ CELÝ PROCES (výjimka z Objective-C se do JVM
  nepřeloží). Most se proto napřed ptá, jestli `mainBundle` končí na `.app`; v ladicím běhu
  oznámení prostě nejsou. Zkoušejí se v zabalené aplikaci: `-PdesktopDebug` zapeče do
  `Plume.cfg` ladicí vlastnosti (`plume.debug`, `plume.dataDir` = `.desktop-data/`), takže
  `runDistributable` jede nad stejnými daty a položkami v Klíčence jako `run`. Do balíčku
  k vydání to nepatří.
- **Ad-hoc podepsaná `Plume.app` povolení k oznámením dostane** — systémový dotaz se ukáže
  a po souhlasu oznámení chodí. Povolení se váže na bundle id (`eu.tintera.plume`), takže platí
  i pro ladicí balíček a přežije přestavění. Notifikace poslaná dřív, než uživatel na dotaz
  odpoví, se zahodí.
- **Aktivní okno oznámení nedostane** (`DesktopWindow.focused`, zapisuje vstupní bod). Po
  spuštění má okno fokus, takže první testovací zpráva „nepřijde" — to je správně.
- **Jiný přihlášený klient oznámení „ukradne".** Otevřený oficiální Telegram zprávu hned
  přečte, TDLib oznámení stáhne (`removedNotificationIds`) a presenter ho z Centra oznámení
  odebere dřív, než si ho člověk všimne. Před testem ostatní klienty zavřít.
- **Jedna zpráva = jedno oznámení**, seskupené po chatech (`threadIdentifier`), id
  `g<groupId>-n<notificationId>` — podle něj se ruší přesně to, co TDLib stáhne. Z dávky (start,
  návrat spojení) se ukáže jen nejnovější zpráva chatu.
- **Klik** vrátí v `userInfo` chat a téma, Kotlin dostane volání z vlákna `UserNotifications`
  (`AttachCurrentThreadAsDaemon`), požádá `ChatOpenRequests` a `DesktopWindow.activate()`
  vytáhne okno — i schované nebo minimalizované.
- **Příloha oznámení je KOPIE souboru**: systém soubor přílohy přesouvá do vlastního úložiště,
  takže by z TDLib zmizel. Bere se fotka ze zprávy, jinak profilovka chatu.
- **Zavření okna na macOS a Windows aplikaci neukončí**, jen okno schová (`visible = false`) —
  jinak by s posledním oknem skončily i oznámení. Na macOS ho vrací ikona v Docku
  (`AppReopenedListener`), na Windows ikona v oznamovací oblasti (oddíl „Oznamovací oblast
  a jediná instance" níž); všude klik na oznámení a další spuštění. Ukončuje Cmd+Q, resp.
  nabídka ikony. Na Linuxu zavření aplikaci ukončí.
- **Odznak v Docku** je počet nepřečtených v neztlumených chatech hlavního seznamu
  (`Taskbar.setIconBadge`), jako na iOS. Windows text v odznaku ikony neumí.
- **Tiché hodiny platí** (`supportsQuietHours = true`): oznámení přijde bez zvuku.
- **Ověřeno na macOS 26.6** (`runDistributable -PdesktopDebug`, zpráva od Notifikátora):
  banner se ukázal, klik vytáhl okno a otevřel chat. Potlačení při aktivním okně ověřené logem.
  **Neověřeno**: Windows a Linux (AWT), odznak v Docku, zavření okna a návrat z Docku,
  stažení oznámení po přečtení jinde (jen nepřímo — viz „ukradne").

### Notifikace na Windows

AWT `TrayIcon.displayMessage` umělo na Windows jen bublinu: nešla stáhnout po přečtení jinde,
klik otevřel poslední zobrazenou zprávu (ne tu, na kterou se kliklo), bez profilovky, bez
seskupení a bez tichého režimu (tiché hodiny tedy nefungovaly). Proto vlastní JNI most k toastům
WinRT v C++/WinRT (`jvmMain/cpp/PlumeToasts.cpp`, `WindowsToasts`, `WindowsNotifier`), stejný
přístup jako u macOS. XML toastu skládá Kotlin (`toastXml`, testy `ToastXmlTest`), most ho jen
předá systému — co se ladí, je čistá funkce.

- **Překládá ho `compileWindowsNotifications`** (MSVC z Visual Studia nebo Build Tools, najde ho
  `vswhere`; statické CRT `/MT`, takže DLL nepotřebuje VC++ Redistributable). Bez MSVC se
  lokálně přeskočí a oznámení půjdou přes AWT jako dřív; na CI (`CI`) je to chyba, ať release
  nevyjde potichu bez toastů. `windows-latest` MSVC má. DLL je pro architekturu JDK, které
  Gradle spouští (x64 / arm64), stejně jako TDLib.
- **Bez zástupce ve Startu s AUMID Windows 11 toast NEVYKRESLÍ.** Aplikace z MSI není
  zabalená (MSIX) a identitu si zakládá sama. Napřed jen v registru
  (`HKCU\Software\Classes\AppUserModelId\<AUMID>` s `DisplayName`, `IconUri`,
  `CustomActivator`, jak to popisuje Community Toolkit): `Show` prošel bez chyby, toast byl
  v historii i v `wpndatabase.db` (`%LOCALAPPDATA%\Microsoft\Windows\Notifications`, tabulka
  `NotificationHandler` s typem `app:desktop` a nastavením jako Chrome), ale na obrazovce ani
  v Centru oznámení nebyl — **a nic to nehlásí**. Nepomohlo ani `SetCurrentProcessExplicitAppUserModelID`.
  Se zástupcem nesoucím `System.AppUserModel.ID` se ukázal (rozhodnuto snímky rohu obrazovky,
  kontrolou byl toast pod AUMID PowerShellu). Zástupce si Průzkumník indexuje pár sekund; toast
  hned po založení se ještě nemusí ukázat.
- **Zástupce: doplnit MSI, ne zakládat druhý.** Instalace z MSI zakládá
  `Start Menu\Programs\Plume\Plume.lnk`; `WindowsToasts.shortcut` ho najde a nativní část mu
  doplní `System.AppUserModel.ID` a `…ToastActivatorCLSID` (cíl nemění, zapisuje jen při rozdílu).
  Aktualizace MSI zástupce přepíše a aplikace ho při dalším startu doplní znovu. Bez MSI
  (rozbalená distribuce) vznikne `Programs\Plume.lnk`, ladicí běh má `Plume (debug).lnk` s cílem
  `java.exe` — spustit z něj aplikaci nejde, je jen nositel identity.
- **AUMID procesu se nastavuje stejný jako na zástupci, a to DŘÍV, než vznikne okno**
  (`nativeInit` běží ze `startKoin` přes presenter, okno až potom). Okno bez něj by neslo
  implicitní AUMID podle cesty k exe, jiný než zástupce, a na hlavním panelu by se rozešlo
  s připnutou ikonou. Ikona připnutá před touhle verzí AUMID nemá — může se ukázat dvakrát,
  než ji uživatel připne znovu.
- **Registr zůstává taky** (`DisplayName`, `IconUri`, `CustomActivator`): nese název a ikonu
  v nastavení oznámení a CLSID aktivátoru.
- **Klik doručuje COM třída** (`INotificationActivationCallback`, CLSID z `CustomActivator`),
  zaregistrovaná `CoRegisterClassObject` ve vlastním vlákně, které drží MTA naživu. Windows ji
  zavolá na vlákně RPC s atributem `launch` z XML (`chat=…&topic=…`). Když aplikace neběží,
  spustí příkaz z `HKCU\Software\Classes\CLSID\{…}\LocalServer32` — `Plume.exe -ToastActivated`
  (jen zabalená aplikace; holý `java` z ladicího běhu se znovu spustit nedá). Kliknutí, které
  přijde dřív, než si ho presenter převezme, počká (`WindowsToasts.pending`).
- **CLSID se NESMÍ MĚNIT** — toasty v Centru oznámení z předchozí verze by po aktualizaci nešly
  otevřít. Ladicí běh má vlastní AUMID (`eu.tintera.plume.debug`, v nastavení „Plume (debug)")
  i CLSID, ať nepřepisuje registraci nainstalované aplikace.
- **`ToastNotifier.Setting` u nezabalené aplikace nevěřit** (bez zástupce vrátil
  `ERROR_NOT_FOUND`, ověřeno na Windows 11). Stav povolení se proto čte z registru, kam ho zapisuje Nastavení →
  Systém → Oznámení: `…\Notifications\Settings\<AUMID>\Enabled = 0` (vypnuto pro Plume)
  a `…\PushNotifications\ToastEnabled = 0` (vypnuto všechno). Windows se na povolení neptá,
  takže úvodní dialog s vysvětlením se tu neukazuje.
- **Historie toastů chce u nezabalené aplikace AUMID** (`History.Remove(tag, group, aumid)`,
  `Clear(aumid)`); přetížení bez něj platí jen pro MSIX. Skupina je jedna pro všechno (`plume`),
  protože presenter při rušení zná jen id; seskupení po chatech nese `<header>` v XML.
- **Tag smí mít nejvýš 64 znaků** — `g<groupId>-n<notificationId>` se vejde i s maximálními int.
- **Nevalidní XML odmítne `LoadXml` celé** (`0xC00CE553`) a toast se neukáže. Proto `toastXml`
  escapuje i apostrof a vyhazuje řídicí znaky, které XML 1.0 nepřipouští.
- **Obrázky jsou `file:///C:/…`** (`Path.toUri`; `File.toURI` dává jen jedno lomítko). Fotka ze
  zprávy jde pod text, profilovka chatu jako kulatá ikona (`appLogoOverride`, `hint-crop`).
  Kopie souboru jako na macOS potřeba není — Windows soubor nepřesouvá.
- **DLL a ikona se rozbalují do dat aplikace** (`native/plumenotifications.dll`,
  `notification-icon.png`), ne do TEMPu: nahranou DLL Windows smazat nedovolí, `deleteOnExit`
  by nezabralo a v TEMPu by s každým spuštěním přibyla kopie. Přepisují se jen, když se liší.
- **Odinstalace MSI klíče v registru nemaže** (zapisuje je aplikace, ne instalátor). Neškodí:
  bez aplikace je nikdo nečte.
- **Historie toastů NEDOKAZUJE, že se toast ukázal** (viz zástupce výš). Vypsat jde
  z PowerShellu (`[Windows.UI.Notifications.ToastNotificationManager, Windows.UI.Notifications, ContentType = WindowsRuntime] > $null`,
  pak `[Windows.UI.Notifications.ToastNotificationManager]::History.GetHistory('eu.tintera.plume.debug')`)
  a hodí se na tag, XML a stažení. Jestli toast opravdu vidět je, rozhodne až oko nebo snímek.
- **Testovací zprávy botem: diakritiku posílat ze souboru** (`--data-urlencode "text@soubor"`).
  Z příkazové řádky Git Bashe na Windows dorazí do `curl` v jiném kódování a Bot API odpoví
  „strings must be encoded in UTF-8".
- **Ověřeno na Windows 11** (ladicí běh, zprávy od testovacího bota): toast se zobrazí s názvem
  chatu a fotkou ze zprávy, tichá zpráva má `<audio silent="true"/>` (`potichu=true` v logu),
  klik otevře chat (`Klik na oznámení` v logu), přečtení na telefonu toast do 2 s stáhne. Zpráva
  v chatu otevřeném v Plume oznámení nevydá vůbec (TDLib ji odloží a zruší). Nevalidní XML
  vrátí HRESULT místo pádu. **Neověřeno**: spuštění zavřené aplikace kliknutím na toast (jen
  instalace z MSI, `LocalServer32`), doplnění AUMID do zástupce z MSI, zvuk (ověřen jen atribut
  v XML), vytažení okna dopředu, když je jiná aplikace v popředí.

## Oznamovací oblast a jediná instance

Na Windows zavření okna schová aplikaci do oznamovací oblasti (`DesktopTrayIcon`,
`desktopClosesToTray`), jako Telegram Desktop. Bez toho by s oknem skončily i toasty.

- **Vlastní AWT `TrayIcon`, ne `Tray` z Compose.** `Tray` hlásí jen `ActionListener`, a ten na
  Windows znamená DVOJKLIK; ikona se tam ale otevírá jedním kliknutím. Nabídka je AWT
  `PopupMenu` (jinou `TrayIcon` neumí, `Tray` ji má taky). Popisky se mění s jazykem aplikace
  bez nové ikony (`setLabels`).
- **Jen Windows.** macOS má Dock a Linux oznamovací oblast mít nemusí (GNOME bez rozšíření)
  — schované okno by nešlo vrátit. Linux dál
  zavřením končí. AWT notifikace (`TrayNotifier`, Linux a Windows bez knihovny toastů) bere
  ikonu aplikace, je-li, ať v oblasti nevisí dvě.
- **Jediná instance** (`DesktopInstance`, volá se jako úplně první věc v `main`). S aplikací
  v oznamovací oblasti se ze Startu spouští znovu běžně a druhý proces by narazil na zamčenou
  databázi TDLib. Druhý proces požádá první o okno a skončí. Zámek je `FileLock`
  (`instance.lock` v datech aplikace) — ten po pádu uvolní systém, kdežto pouhá existence
  souboru by po pádu blokovala navždy. Žádost jde po loopbacku na port z `instance.port`
  s náhodným tokenem: port vidí každý místní proces, token jen uživatel s přístupem k datům.
  Ladicí běh má jiný adresář dat, takže vedle instalace běžet může.
- **Okno vytažené na žádost druhé instance dostane fokus** (ověřeno: `GetForegroundWindow` po
  druhém spuštění). Windows ho povolí, protože popředí právě drží spouštěný proces.
- **Schované nebo minimalizované okno = aplikace v pozadí** (`AppForeground` = `DesktopWindow.shown`;
  dřív desktop hlásil popředí pořád). Volba `online` se vypne — server nás přestane vidět online
  a telefon zase upozorňuje (TDLib oznámení odkládá, dokud je účet online jinde) — zámek
  aplikace se po zvolené době zamkne a návrat do popředí obnoví spojení jako na Androidu
  (v logu „Návrat do popředí, obnovuji spojení"). Fokus o popředí nerozhoduje, jen
  o oznámeních: okno vedle jiného je pořád vidět. Platí i pro macOS (schování do Docku).
- **Ověřeno na Windows 11** (ladicí běh): `WM_CLOSE` okno schová a proces běží dál, druhé
  spuštění skončí za pár sekund a okno první instance je vidět a v popředí, návrat do popředí
  v logu. **Neověřeno**: macOS (popředí podle schovaného okna), instalace z MSI.

## Release (Windows)

Actions → „Release — bump & tag" → target **desktop** (`create-release-tag.yml`) založí tag
`desktop-X.Y.Z` a spustí `.github/workflows/desktop-release.yml` nad ním: na `windows-latest`
postaví MSI (`packageMsi -PdesktopVersion=…`), na Ubuntu založí GitHub Release s MSI v příloze
(verze 0.x jako pre-release). Secrets stejné jako Android (`TELEGRAM_API_ID`, `TELEGRAM_API_HASH`).

- **Tag je `desktop-X.Y.Z`, bez čísla buildu** (Android má `android-X.Y.Z-B`). MSI pozná
  aktualizaci jen podle X.Y.Z (X a Y do 255, Z do 65535) a podle `upgradeUuid`
  v `client/desktopApp/build.gradle.kts`, **který se nesmí měnit** — nové UUID by nainstalovalo
  druhou kopii vedle staré. Stejnou nebo nižší verzi MSI přes vyšší nenainstaluje, proto bump
  `build` u desktopu nejde a explicitní verze musí být vyšší než poslední tag (hlídá bump).
- **První desktop release** se spouští s vyplněnou Version — bez předchozího tagu není co
  bumpovat. Verze se bere z tagu, ne z commitu, takže build je z tagu reprodukovatelný.
- **macOS nepřijme verzi s MAJOR 0** („The first number in an app-version cannot be zero") —
  MSI ano. Až přibude macOS release, chce desktop verzi od 1.0.0.
- **Instalace jen pro uživatele** (`perUserInstall`, `%LOCALAPPDATA%`): bez správce, což
  u nepodepsaného instalátoru ušetří další dotaz. Data aplikace jsou dál v `%APPDATA%\Plume`.
- **Nepodepsané.** SmartScreen při prvním spuštění zastaví instalátor („Další informace" →
  „Přesto spustit"). Podpis chce certifikát pro podepisování kódu.
- **Bez ProGuardu** (`packageMsi`, ne `packageReleaseMsi`) — pravidla pro wrapper TDLib, Koin
  a serializaci na desktopu nejsou.
- **MSI jde do přílohy releasu**, artefakt běhu žije jen den: přílohy releasů se do kvóty
  Actions nepočítají.
- **Na Windows bývá jen `python`, ne `python3`** — `generateChangelog` zkouší obojí, jinak by
  „Co je nového" v aplikaci tiše zůstalo prázdné. **A konzole je v cp1252**: skript vypisuje
  češtinu a bez `PYTHONIOENCODING=utf-8` padal na `UnicodeEncodeError` — se stejným tichým
  následkem.
- **WiX bere jen znaky z cp1252.** „ě" ve jméně dodavatele shodilo `light.exe` kódem 311
  (LGHT0311) a jpackage z toho udělal holé „exit code 1"; Compose ukáže jen cestu k logu na
  runneru. Workflow proto při chybě logy jpackage vypisuje (krok „jpackage logs") a metadata
  balíčku jdou na Windows přes `msiSafe` (bez diakritiky, když se do cp1252 nevejde).
- **Ikony** (`client/desktopApp/icons/plume.{ico,icns,png}` a ikona okna) generuje
  `play/tools/icon.py desktop` z téže geometrie pírka jako ostatní platformy: zaoblený čtverec
  s průhlednými rohy podle mřížky ikon macOS (824/1024, poloměr 185). ICO nese velikosti
  16–256, Windows si vybírá podle místa (hlavní panel, Start, Průzkumník).

## Cesty k souborům na Windows

- **Obrázek ze souboru jde do Coilu jako `localImage(path)` (`okio.Path`), nikdy jako
  `"file://$path"`.** Coil řetězec rozebírá jako URI: z `file://C:\Users\…\a.jpg` udělá autoritu
  `C:` a cestu `Users\…\a.jpg`, relativní k pracovnímu adresáři. Výsledek je
  `FileNotFoundException`, kterou Coil spolkne, a na Windows nebyl vidět jediný obrázek — fotky,
  avatary, náhledy příloh — zatímco na macOS, Androidu i iOS všechno fungovalo. `Path` Coil mapuje
  na URI z částí bez rozboru (`PathMapper`). Reprodukuje to `LocalImageTest` v `jvmTest`, ovšem
  jen na Windows; na ostatních systémech prošla i stará podoba.
- **Jméno souboru z cesty se bere `toPath().name`, ne `substringAfterLast('/')`**: TDLib na
  Windows vrací cesty se zpětnými lomítky a z lomítka by vyšla celá cesta.

## Co platforma (zatím) neumí

Prázdné `actual` volí chování, které sdílený kód už umí ohlásit, ne tiché nic: přehrávání
zvuku vyhodí výjimku („hlasovku se nepodařilo přehrát"), nahrávání taky („nahrávání nejde
spustit"), video má `isFailed = true` a otevírá se v aplikaci systému
(`viewerUnsupportedFormats` obsahuje všechna videa a HEIC), kamera pro kolečka je `null`
(nenabídne se). Seznam chybějícího je v `docs/roadmap.md`, sekce Desktop.

- **Výběr příloh je AWT `FileDialog`** (na macOS nativní panel). Soubor se nekopíruje — na
  rozdíl od mobilů tu žádný dočasný adresář výběru není. Fotka (co přečte ImageIO) jde jako
  fotka, ostatní včetně videa jako soubor.
- **`Desktop.browse`/`open` na Linuxu bez GNOME knihoven nefungují**, náhradou je `xdg-open`.
- **Mini aplikace se hlásí jako `tdesktop`** (`webAppPlatform`), WebView ale není.
- **Test `DocumentKindTest` tvrdil, že MP4 otevře prohlížeč na každé platformě.** Na desktopu
  to neplatí; commonTest teď běží i na JVM (`jvmTest`), takže takové „všude" předpoklady
  vyplavou.

## Build

- **`compose.desktop.currentOs`** jen ve vstupním bodu: Skia pro systém, na kterém se staví.
- **`kotlinx-coroutines-swing` je povinné** — bez něj `Dispatchers.Main` na JVM neexistuje
  a `viewModelScope` spadne.
- **Moduly JDK pro jlink** podle `./gradlew :client:desktopApp:suggestRuntimeModules`; chybějící
  modul se ukáže až za běhu zabalené aplikace, ne při buildu.
- **`java` v `.gradle.kts` je accessor rozšíření Java pluginu**, ne balíček: `java.nio.file.Files`
  napsané plně se nepřeloží („Unresolved reference 'nio'"), musí být importované.
