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

## TDLib na desktopu: wrapper ji veze v jaru

`tdl-coroutines` publikuje `tdl-coroutines-jvm` s nativní TDLib pro macOS, Linux i Windows
(x64 i arm64) v jednom jaru (115 MB). Wrapper si při prvním použití sám vybere knihovnu podle
`os.name`/`os.arch`, rozbalí ji do dočasného adresáře a nahraje. Vlastní build TDLib ani fork
wrapperu tedy potřeba nejsou.

- **Jar se při buildu přepisuje** (plugin `plume.tdlib-desktop`, `TdlibJarTransform`
  v `build-logic/…/Tdlib.kt`, Gradle artifact transform na `runtimeClasspath`):
  1. **jen nativní knihovna tohoto systému a architektury** (jar spadne na ~55 MB; jpackage
     stejně cizí systém zabalit neumí),
  2. **hook `TdlibJsonRepair`** (viz níž).
- **Do 15.0.0 se macOS knihovna mimo stroj autora nenačetla.** Odkazovala absolutní cestou na
  Homebrew autora (`/opt/homebrew/opt/openjdk/…/libjvm.dylib`, `…/openssl@3/lib/libssl.3.dylib`)
  a padala na `UnsatisfiedLinkError` s jakýmkoli JDK mimo Homebrew `openjdk`. Transform proto
  přepisoval cesty (`install_name_tool` + nový ad-hoc podpis), přibaloval Homebrew OpenSSL
  a `main` ho nahrával dřív než TDLib. Nahlášeno jako
  [g000sha256/tdl-coroutines#163](https://github.com/g000sha256/tdl-coroutines/issues/163),
  **opravené v 16.0.0**: knihovna nemá jedinou závislost mimo `/usr/lib` (OpenSSL je slinkované
  staticky) a obcházka i požadavek na Homebrew `openssl@3` při buildu zmizely.
  **Při bumpu wrapperu ověřit `otool -L` nad knihovnou z jaru**: cokoli mimo `/usr/lib/`
  a `/System/` je návrat té chyby.

### Další omezení binárek wrapperu (16.0.0)

- **macOS je `minos 12.0`** na arm64 i x64 (`vtool -show-build`; v 15.0.0 arm64 26.0, x64 15.0).
  Nejnižší podporovaný systém teď určují jiné knihovny, viz „macOS (DMG)".
- **Linux potřebuje glibc 2.34+** (Ubuntu 22.04) a systémové `libssl.so.3`.
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
- **Odznak** je počet nepřečtených v neztlumených chatech hlavního seznamu od TDLib
  (`ChatRepository.unreadMessages`, viz `docs/poznatky/notifikace.md`), jako na iOS: v Docku
  text (`Taskbar.setIconBadge`), na Windows overlay na tlačítku okna (oddíl „Hlavní panel
  Windows" níž). Obojí řídí `DesktopTaskbar`.
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
- **Překládá se jako C++20.** S `/std:c++17` sáhne C++/WinRT po `<experimental/coroutine>`
  a MSVC z Visual Studia 2026 (14.51, na `windows-latest` od podzimu 2026) to odmítne chybou
  STL1011. Lokálně s VS 2022 C++17 procházelo, takže se to ukázalo až v release buildu
  `desktop-0.3.2`, který tím neprošel. Pojistka „bez MSVC na CI = chyba" přitom zafungovala:
  release nevyšel potichu bez toastů. **Pozor: CI na pull requestech nativní most nepřekládá**
  (překládá jen release z tagu), takže změna v `PlumeToasts.cpp` nebo v úloze se na CI poprvé
  přeloží až při vydání.
- **Hlavičky JNI se berou z JDK 17 toolchainu**, ne z JVM démona Gradle (`jniToolchainHome`):
  démona spuštěného z Android Studia pohání JBR, který `include/` nemá.
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
  než ji uživatel připne znovu. **Ladicí běh AUMID procesu NEnastavuje:** okno se zástupcovým
  AUMID dostane na hlavním panelu ikonu ZÁSTUPCE, ne svou — a ladicí zástupce míří na
  `java.exe`, takže tam byl Duke. Toastům AUMID procesu nechybí, stačí jim zástupce.
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
- **Řádek „Povolení v systému" otevírá Nastavení → Systém → Oznámení** (`ms-settings:notifications`;
  na řádek jedné nezabalené aplikace odkaz nevede). **Přes `explorer.exe`, ne `Desktop.browse`:**
  AWT `browse` otevřel `ms-settings:` v prohlížeči (u uživatele Chrome), ne v aplikaci
  Nastavení. Průzkumník předá schéma jeho handleru a je to okenní program, takže na rozdíl
  od `cmd /c start` nebliká konzole. Totéž platí pro každé další systémové schéma.
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

### Tlačítka toastu (odpověď, přečtení, ztlumení)

Toast chatu má tytéž akce jako notifikace na Androidu: pole „Odpovědět" s Odeslat, „Označit
jako přečtené" a „Ztlumit na hodinu" (jen kde `ChatNotification.canMute`; odpověď jen kde
`canReply`). Co se děje v TDLib, je sdílené `NotificationActions` — desktop jen zapojuje.

- **Všechna tlačítka `activationType="background"`.** Nezabalená aplikace je dostane stejně
  jako klik — voláním COM aktivátoru — jen bez toho, aby Windows čekal, že vytáhne okno.
  Aktivátor rozliší akci podle `arguments` tlačítka (`action=reply|read|mute&chat=…`); klik na
  toast a hlavičku nese `launch` bez `action` a otevírá chat jako dřív, takže toasty ze starší
  verze v Centru oznámení fungují dál (`parseActivation`, testy `ToastXmlTest`).
- **Napsaný text přichází v `NOTIFICATION_USER_INPUT_DATA`** u `Activate` pod klíčem pole
  (`reply`); dřív ho most zahazoval. Most ho předá jako druhý argument `onActivated`.
- **Odeslat jde stisknout i s prázdným polem** — prázdná (i jen mezery) odpověď se zahodí.
- **Toast po stisku tlačítka zavře Windows sám**, ať akce dopadla jakkoli. Zmizení toastu tedy
  přečtení NEDOKAZUJE; pozná se na odznaku nepřečtených (`DesktopTaskbar` v logu) nebo v logu
  presenteru. Nepovedená odpověď se proto ohlásí NOVÝM toastem („Neodesláno: …"), na Androidu
  se překresluje původní notifikace.
- **Ve skupině odpověď cituje** nejnovější zprávu oznámení, v soukromém chatu nic — stejně jako
  Android (`quoteMessageId` = `messageId`, když je `senderName`).
- **Čeká se štědřeji než na Androidu** (30 s na přihlášení, minuta na potvrzení): proces se
  neuspí a nehlídá ho systémový limit příjemce. Nepotvrzenou zprávu TDLib došle sám, jen chat
  zůstane nepřečtený.
- **Popisky se překládají při zobrazení** (`readString`) — toast je hotový text v XML, po změně
  jazyka zůstanou staré toasty v původním jazyce.
- **Ověřeno na Windows 11** (ladicí běh, Plume schovaná v oznamovací oblasti): odpověď z toastu
  odešla s diakritikou, server ji potvrdil, okno zůstalo schované a chat se přečetl.
  „Označit jako přečtené" ověřil uživatel ručně (log přečtení přibyl až po tom testu).
  **Neověřeno**: Ztlumit na hodinu, toast „Neodesláno", studený start z tlačítka (MSI).

## Hlavní panel Windows

Počet nepřečtených a upozornění na novou zprávu na tlačítku okna (`DesktopTaskbar`). Stačí na
to AWT `Taskbar`: na Windows umí `ICON_BADGE_IMAGE_WINDOW` (`ITaskbarList3::SetOverlayIcon`)
a `USER_ATTENTION_WINDOW` (`FlashWindowEx`); `ICON_BADGE_TEXT` ani `ICON_BADGE_NUMBER` ne.

- **Overlay nastavený dřív, než vznikne tlačítko, Windows TIŠE ZAHODÍ.** Tlačítko vzniká chvíli
  po ukázání okna (zpráva `TaskbarButtonCreated`, kterou AWT ven nepustí). Hned po startu se
  odznak neukázal, za 1,5 s ano — ověřeno zkouškou na holém `JFrame` s AUMID procesu i bez
  něj. Po každém ukázání okna (start, návrat z oznamovací oblasti) se proto 1,5 s čeká;
  schované okno tlačítko nemá a overlay s ním zaniká.
- **Obrázek kreslíme sami, v pixelech podle měřítka obrazovky** (16 bodů × `scaleX`):
  zmenšený větší obrázek by měl rozmazané číslice. Nad 99 „99+". Písmo je Segoe UI (systém),
  ne písmo aplikace — odznak patří k hlavnímu panelu.
- **Barvy jsou `error` / `onError` motivu** (`AppIconBadgeColors`, prázdné `expect` na
  Androidu a iOS, kde odznak kreslí systém). Overlay se kreslí mimo Compose, takže barvy
  předává composable uvnitř `AppTheme`; do první kompozice motivu se overlay nekreslí.
- **Blikání jen u oznámení se zvukem a jen u viditelného okna bez fokusu.** Tiché hodiny, tichá
  zpráva i odložený chat neblikají — „potichu" znamená nerušit. Schované okno tlačítko nemá,
  upozorní toast. Bliká se jednou za dávku, ne za každý chat.
- **Ověřeno na Windows 11** (ladicí běh, snímky hlavního panelu): odznak „1" naskočil po
  startu, zpráva od bota ho zvedla na „2" a tlačítko se rozblikalo.

## Titulek okna v barvách motivu

Bílý systémový titulek nad tmavou aplikací byl nejrušivější prvek okna. `WindowTitleBarAppearance`
(expect v `Theme.kt`, reálně jen `jvmMain`) volá `AppTheme` se ZVOLENÝM motivem, ne se systémovým.

- **Tmavý režim titulku** (`DWMWA_USE_IMMERSIVE_DARK_MODE`) nastavuje knihovna platformtools
  (`setWindowsAdaptiveTitleBar`, táž jako v Mlogu). Podle něj systém kreslí tlačítka okna.
- **Barvu titulku a textu** (`DWMWA_CAPTION_COLOR` 35, `DWMWA_TEXT_COLOR` 36) nastavujeme sami
  přes JNA na plochu okna (`surfaceContainer`, na ní leží seznam) a `onSurface`. Samotný tmavý
  titulek je neutrálně šedý (#202020) a vedle tónované palety působil jako cizí pruh. Atributy
  zná až Windows 11; na Windows 10 volání vrátí chybu a zůstane jen tmavý titulek.
- **Barva je z CÍLOVÉHO schématu**, ne z prolínaného — DWM se nevolá v každém snímku přechodu,
  titulek přeskočí na novou barvu hned.
- **Okno si bere z `DesktopWindow.window`** (zapisuje vstupní bod), protože `AppTheme` je
  v `commonMain` a k `ComposeWindow` nedosáhne.
- **macOS: vlastní most do AppKitu** (`MacTitleBar`, `jvmMain/objc/PlumeWindow.m`, v téže
  knihovně jako notifikace, nahrává ji `MacNative`). Oknu nastaví `appearance` (Aqua / DarkAqua
  podle ZVOLENÉHO motivu, za běhu), `titlebarAppearsTransparent` a `backgroundColor` = plocha
  okna; průhledný titulek pak ukáže barvu pozadí okna. Text titulku si barví systém podle
  vzhledu, `content` se na Macu nepoužije.
  - **Proč ne vlastnosti `apple.awt.*` na `JRootPane`**: vzhled jednoho okna
    (`apple.awt.windowAppearance`) má jen JetBrains Runtime, Adoptium 17 ani 25 ne (ověřeno ve
    zdrojích `CPlatformWindow`). Barvu titulku nenastaví žádná.
  - **Okno se hledá podle titulku** v `NSApp.windows`: ukazatel na `NSWindow` drží
    `sun.lwawt.macosx.CPlatformWindow` a bez `--add-opens` se k němu nedosáhne. Přejmenování
    okna za běhu by vazbu rozpojilo.
  - **`NSWindow` vzniká až při prvním zobrazení** (spuštění schované po přihlášení ho nemá),
    proto `MacTitleBar` čeká na `windowOpened`. Na okno se sahá jen z hlavního vlákna AppKitu
    (`dispatch_async`), fronta událostí AWT je jiné vlákno.
  - `setMacOsAdaptiveTitleBar()` ve vstupním bodu zůstává: drží titulek podle systému do
    chvíle, než ho přebarví motiv — jinak by při startu v tmavém systému krátce svítil bílý.
  - Ověřeno na macOS 26.6 v ladicím běhu, světlý i tmavý motiv (změřeno ze snímku: titulek
    #E7EFF8 = `surfaceContainer`).
- **Přihlašovací obrazovka leží na `surfaceContainer`**, ne na `surface`: titulek má barvu plochy
  okna (`surfaceContainer`, seznam chatů), a nad přihlášením na `surface` byl vidět jako pruh
  (#E7EFF8 nad #F6FAFF). Blok s telefonem je proto `surfaceContainerLowest` jako skupiny
  v Nastavení; jinde (změna čísla, nový kontakt) zůstává `surfaceContainerLow` na `surface`.
- Ověřeno na Windows 11 v tmavém motivu: titulek splývá s plochou okna.

## Oznamovací oblast a jediná instance

Na Windows zavření okna schová aplikaci do oznamovací oblasti (`DesktopTrayIcon`,
`desktopClosesToTray`), jako Telegram Desktop. Bez toho by s oknem skončily i toasty.

- **Vlastní AWT `TrayIcon`, ne `Tray` z Compose.** `Tray` hlásí jen `ActionListener`, a ten na
  Windows znamená DVOJKLIK; ikona se tam ale otevírá jedním kliknutím. Popisky se mění
  s jazykem aplikace bez nové ikony (`setLabels`).
- **Nabídka je nativní menu Windows (`WindowsTrayMenu`, JNA nad user32), ne AWT `PopupMenu`.**
  AWT si položky kreslí sám (owner-draw), takže jeho nabídka vypadala jako z Windows 95 a motiv
  systému ji minul; `TrayIcon` jinou nabídku neumí, a ani `Tray` z Compose. Ikona proto nabídku
  nemá a po puštění pravého tlačítka se zavolá `TrackPopupMenuEx` (`TPM_RETURNCMD`) na vlastním
  vlákně se skrytým oknem: funkce blokuje ve vlastní smyčce a vlastník musí patřit témuž vláknu.
  Okno musí jít do popředí (`SetForegroundWindow`), jinak nabídka po kliknutí jinam nezmizí,
  a po zavření patří `WM_NULL` (oboje z dokumentace funkce). „Otevřít" je tučně
  (`SetMenuDefaultItem`) — tutéž akci dělá kliknutí na ikonu. AWT `PopupMenu` zůstává jako
  záloha, kdyby se user32 nenačetla. Ověřeno na Windows 11: nabídka vypadá jako systémová.
- **Tmavá nabídka podle motivu aplikace** jde jen přes nedokumentované exporty uxtheme podle
  pořadového čísla (135 `SetPreferredAppMode` s ForceDark / ForceLight, 136 `FlushMenuThemes`,
  133 `AllowDarkModeForWindow`), jako v Notepad++ nebo Windows Terminalu. Pod 135 je až od
  buildu 18362 (Windows 10 1903) tahle funkce, proto kontrola buildu přes `RtlGetVersion`
  (`os.version` hlásí i na Windows 11 jen „10.0"). Motiv hlásí `WindowTitleBarAppearance`
  (`DesktopTray.dark`); dokud okno nic neohlásilo (start do oznamovací oblasti), jde nabídka
  podle systému.
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
- **Ladicí běh je označený** (`DesktopDevMark`): „Plume (dev)" v titulku okna a v popisku
  v oznamovací oblasti a oranžová tečka v rohu ikony na hlavním panelu i v oznamovací oblasti.
  Ladicí běh a nainstalovaná aplikace běží vedle sebe se stejným oknem a stejnou ikonou, takže
  se testovalo v té špatné. Rozhoduje `plume.debug` (nastavuje ho `run` a `-PdesktopDebug`),
  nainstalovaná aplikace se nemění. Toasty Windows nesou dál ikonu bez tečky.
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

## Poloha a velikost okna

Hlavní okno se otevře, kde a jak velké bylo naposledy (`DesktopWindowBounds`,
`window.properties` v datech aplikace vedle `instance.lock`; testy `DesktopWindowBoundsTest`).
Dřív to bylo pokaždé 1200 × 800 tam, kam ho dal systém.

- **Uložená poloha se ověří proti dnešním monitorům** (`fitted`): okno zůstane, když z něj
  je na některém monitoru vidět aspoň 100 × 100 bodů; jinak polohu vybere systém. Velikost se
  vejde do pracovní plochy monitoru (bez hlavního panelu a Docku) a přečnívající okno se posune
  dovnitř. Okno uložené na odpojeném druhém monitoru by se jinak otevřelo mimo dosah.
- **Maximalizované okno si pamatuje velikost PŘED maximalizací** a příznak; po spuštění se
  maximalizuje a zmenšení ho vrátí tam, kde bylo. Minimalizace se neukládá.
- **Ukládá se se zpožděním půl vteřiny** (`debounce`): tah oknem pošle desítky změn.
- **Souřadnice jsou logické body AWT**, tedy `dp` v Compose; na Retině se nepřepočítávají.
- **Ověřeno na macOS** (ladicí běh, `.desktop-data/window.properties`): poloha a velikost ze
  souboru se po startu skutečně použily (`CGWindowListCopyWindowInfo`, okno 900 × 888 na
  53 / 103 i po restartu), změna okna se uložila, poloha `x = 5000` (mimo monitor) skončila na
  místě od systému a `maximized = true` otevřelo okno přes celou plochu se zachovanou původní
  velikostí v souboru. **Neověřeno**: Windows, Linux a víc monitorů naživo (jen testy).

## Jazyk aplikace

Desktop systémový výběr jazyka pro jednu aplikaci nemá, a tak ho drží aplikace sama
(`DesktopAppLanguage`, řádek Jazyk v Nastavení jako na Androidu). Volba je v souboru `language`
v datech aplikace a v `main` se nastaví jako výchozí `Locale` JVM dřív, než se přečte první text
— z něj čtou Compose Resources v kompozici (`Locale.current`) i mimo ni (oznámení, ikona
v oznamovací oblasti, nabídka v liště macOS).

- **Po změně se obsah okna skládá znovu** (`key` nad `DesktopAppLanguage.changes` v `Main.kt`,
  kolem `App`, nabídky v liště a ikony v oznamovací oblasti). Jazyk se v kompozici čte jen při
  skládání a prostředí prostředků (`LocalComposeEnvironment`) je interní, zvenku nejde přepsat.
  Nová kompozice přijde o stav obrazovek — aplikace skončí na seznamu chatů, ne v Nastavení.
  U volby, která se mění jednou za čas, je to přijatelné; okno samo zůstává (poloha, velikost).
- **Jazyk systému se zachytí dřív, než ho volba přepíše** (`systemLocale`), aby „Podle systému"
  šlo vrátit bez restartu.
- **Ověřeno na macOS** (ladicí běh, systém česky): uložená angličtina platila od startu
  (seznam chatů, prázdný panel i nabídka „Chats" v liště), „Podle systému" v Nastavení přeložilo
  okno i lištu zpátky do češtiny hned a soubor s volbou smazalo. **Neověřeno**: Windows
  a Linux, oznámení v jiném jazyce, než má systém.

## Spouštění po přihlášení (Windows)

Push na desktopu není, oznámení chodí jen tehdy, když Plume běží — po restartu počítače by
nepřišlo nic, dokud ji člověk sám neotevře. Nastavení → Notifikace → Na pozadí → „Spouštět po
přihlášení" (`LaunchAtLogin`, na mobilech a zatím na macOS a Linuxu `supported = false`).

- **Záznam v `HKCU\Software\Microsoft\Windows\CurrentVersion\Run`**, hodnota `Plume` (ladicí
  balíček `Plume (debug)`, ať nepřepíše instalaci), příkaz `"…\Plume.exe" --autostart`. Totéž
  místo ukazuje Nastavení Windows → Aplikace → Po spuštění.
- **Vypnutí z Windows se nezapisuje do `Run`**, ale do `Explorer\StartupApproved\Run`: binární
  hodnota, lichý první bajt = vypnuto. `nativeCommand` takový záznam bere jako vypnutý;
  zapnutí v Plume příznak smaže, ať platí. Stav drží systém — čte se při každém návratu na
  obrazovku a po zápisu se čte zpátky, ukazuje se, co v registru opravdu je.
- **`--autostart` = okno rovnou schované** v oznamovací oblasti (`DesktopStartup.startsHidden`),
  po přihlášení nemá nic vyskakovat. Argument dojde i do procesu-potomka spouštěče jpackage.
- **Jen spouštěč z balíčku** (`WindowsNative.launcher`): holá `java` z `run` se znovu spustit
  nedá, v ladicím běhu z Gradle se přepínač neukazuje. Vyzkoušet jde přes
  `runDistributable -PdesktopDebug`. Cesta v záznamu se při každém startu opraví
  (`refreshCommand`), kdyby se aplikace přeinstalovala jinam.
- **Registr zapisuje nativní most** (`PlumeToasts.cpp`, `WindowsStartup`), ne `reg.exe`:
  konzolový proces spuštěný z okenní aplikace může bliknout oknem. Knihovnu nahrává
  `WindowsNative` odděleně od toastů — čtení stavu nesmí zakládat registraci toastů ani sahat
  na zástupce ve Startu.
- **Cesty k registru v C++ se zdvojenými lomítky, a hlídá to `/we4129`.** Kód zapsaný přes
  heredoc v Git Bashi přišel o zdvojení: `L"Software\Microsoft\…"` se přeložilo jako
  `SoftwareMicrosoft…` (neznámé escape sekvence = písmeno) a záznam skončil v nesmyslném klíči
  `HKCU\SoftwareMicrosoftWindowsCurrentVersionRun`. Přepínač se přitom tvářil zapnutě, protože
  čtení šlo do téhož špatného klíče. MSVC na to jen varoval (C4129); `/we4129` z toho dělá
  chybu. Zdrojáky s lomítky se proto píšou nástrojem pro soubory, ne heredocem.
- **`-PdesktopDebug` zapisoval do `Plume.cfg` cestu se zpětnými lomítky** a jpackage je v ní
  bere jako escape: z `C:\Develop\telegram\.desktop-data` bylo `C:Develop<tab>elegram…`.
  Teď `invariantSeparatorsPath`. A **`DesktopInstance` bral chybu zámku za „už běžím"** —
  s nesmyslnou cestou aplikace tiše skončila s kódem 0. Teď rozlišuje `null` (zámek drží jiný
  proces) od výjimky (zámek nejde založit — běží se bez něj).
- **Ověřeno na Windows 11** (`runDistributable -PdesktopDebug`): přepínač zapsal správný záznam,
  spuštění jeho příkazem otevřelo Plume se schovaným oknem (`IsWindowVisible = false`).
  **Neověřeno**: skutečné přihlášení do Windows, instalace z MSI.

## Release

Actions → „Release — bump & tag" → target **desktop** (`create-release-tag.yml`) založí tag
`desktop-X.Y.Z` a spustí `.github/workflows/desktop-release.yml` nad ním: na HQDEV50 postaví
MSI (`packageMsi -PdesktopVersion=…`), na vývojářském Macu DMG pro Apple Silicon
(`packageDmg`, příloha `Plume-X.Y.Z-macos-arm64.dmg`) a založí GitHub Release s obojím
v příloze. Runnery jsou vlastní, viz „Self-hosted runnery" v `release.md`. Secrets stejné jako Android (`TELEGRAM_API_ID`, `TELEGRAM_API_HASH`).

- **Tag je `desktop-X.Y.Z`, bez čísla buildu** (Android má `android-X.Y.Z-B`). MSI pozná
  aktualizaci jen podle X.Y.Z (X a Y do 255, Z do 65535) a podle `upgradeUuid`
  v `client/desktopApp/build.gradle.kts`, **který se nesmí měnit** — nové UUID by nainstalovalo
  druhou kopii vedle staré. Stejnou nebo nižší verzi MSI přes vyšší nenainstaluje, proto bump
  `build` u desktopu nejde a explicitní verze musí být vyšší než poslední tag (hlídá bump).
- **První desktop release** se spouští s vyplněnou Version — bez předchozího tagu není co
  bumpovat. Verze se bere z tagu, ne z commitu, takže build je z tagu reprodukovatelný.
- **macOS nepřijme verzi s MAJOR 0** („The first number in an app-version cannot be zero") —
  MSI ano. Do 0.3.7 se proto vydávalo jen MSI a s DMG se přešlo na 1.0.0 (bump major). Nulu
  odmítne už bump (`create-release-tag.yml`) i rozbor tagu ve vlastním jobu `version`
  release workflow — jinak by MSI prošlo a release skončil napůl.
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
  následkem. **A job `windows` musí klonovat s `fetch-depth: 0`**: mělký klon nad
  tagem nemá ostatní tagy, skript nenajde žádné vydání a seznam je prázdný bez jediné hlášky.
  **Na CI proto `generateChangelog` při chybě shodí build** (proměnná `CI`), lokálně dál jen
  zapíše prázdný seznam; chyba gitu jde do hlášky i s jeho stderr. Desktop 1.0.0 vyšel bez
  novinek, protože git na HQDEV50 odmítl checkout jiného vlastníka (`release.md`, „Nastavení
  HQDEV50") — Python z Microsoft Store přitom pod službou běžel v pořádku.
  Desktop má vlastní kanál vydání (tagy `desktop-…`), viz „Co je nového" v `nastaveni.md`.
- **WiX bere jen znaky z cp1252.** „ě" ve jméně dodavatele shodilo `light.exe` kódem 311
  (LGHT0311) a jpackage z toho udělal holé „exit code 1"; Compose ukáže jen cestu k logu na
  runneru. Workflow proto při chybě logy jpackage vypisuje (krok „jpackage logs") a metadata
  balíčku jdou na Windows přes `msiSafe` (bez diakritiky, když se do cp1252 nevejde).
- **Ikony** (`client/desktopApp/icons/plume.{ico,icns,png}` a ikona okna) generuje
  `play/tools/icon.py desktop` z téže geometrie pírka jako ostatní platformy: zaoblený čtverec
  s průhlednými rohy podle mřížky ikon macOS (824/1024, poloměr 185). ICO nese velikosti
  16–256, Windows si vybírá podle místa (hlavní panel, Start, Průzkumník).

### macOS (DMG)

- **Jen Apple Silicon.** jpackage balí pro architekturu JDK, které staví, a Mac runneru
  (i záložní `macos-latest`) je arm64 (job to na začátku ověří `uname -m`).
- **`minimumSystemVersion` v `build.gradle.kts` je nejvyšší `minos` přibalených nativních
  knihoven** (`vtool -show-build`): arm64 14.0 kvůli ntgcalls (hovory; TDLib 12.0, most
  k notifikacím 11.0), x64 12.0 kvůli TDLib (ntgcalls pro macOS x64 nevychází). Bez něj je
  v `Info.plist` výchozí 10.13 a aplikace by na starším systému naběhla a spadla až při nahrání
  knihovny. S wrapperem 15.0.0 to bylo arm64 26.0 a x64 15.0. **Na macOS pod 26 spuštění
  ověřené NENÍ** — není na čem.
- **Podpis je jen ad hoc** (jpackage s hardened runtime, `entitlements.plist`), bez Developer ID
  a notarizace. Gatekeeper aplikaci staženou z internetu napoprvé neotevře; povolí se
  v Nastavení systému → Soukromí a zabezpečení → „Přesto otevřít" (od macOS 15 už nejde obejít
  přes Ctrl+klik → Otevřít). Návod je v textu releasu. Podpis Developer ID chce placený účet
  Apple Developer (roadmapa).
- **Lokálně** stačí `./gradlew :client:desktopApp:packageDmg`;
  balíček je v `client/desktopApp/build/compose/binaries/main/dmg/`.

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

## Kamera a mikrofon v podepsané aplikaci

`Plume.app` z jpackage je podepsaná (i ad hoc) s **hardened runtime**, a ten na kameru
a mikrofon chce entitlementy `com.apple.security.device.camera` a
`com.apple.security.device.audio-input` (`client/desktopApp/entitlements.plist`).

- **Bez nich TCC dotaz na povolení vůbec neukáže a nic nehlásí.** Relace AVFoundation se
  „rozběhne", zelená kamera v liště svítí, ale nepřijde jediný snímek. `tccd` v systémovém
  logu píše „Prompting policy for hardened runtime; service: kTCCServiceCamera requires
  entitlement … but it is missing" a „Policy disallows prompt". Najde se to přes
  `/usr/bin/log show --predicate 'process == "tccd"'` (v zsh je `log` vestavěný příkaz,
  proto plná cesta).
- **Z Gradlu (`runDistributable`) to vypadá, že mikrofon funguje.** Aplikace je potomek
  procesu `java` a TCC se ptá za „zodpovědný" proces nahoře (Terminál, Claude). Ten mikrofon
  povolený mít může, kameru taky ne. Ověřuje se proto spuštěním přes LaunchServices:
  `open --stdout <log> --stderr <err> client/desktopApp/build/compose/binaries/main/app/Plume.app`.
- **Vlastní `entitlementsFile` nahrazuje výchozí oprávnění Compose**
  (`allow-jit`, `allow-unsigned-executable-memory`, `disable-library-validation`), proto
  je soubor opakuje. Bez nich JVM v hardened runtime nenaběhne.
- **`NSCameraUsageDescription` a `NSMicrophoneUsageDescription` v `Info.plist`**
  (`infoPlist.extraKeysRawXml`): bez popisu macOS aplikaci při prvním sáhnutí na kameru
  ukončí. Text je anglicky, přeložit by šel jen přes `.lproj` v balíčku.

## Okno mimo hlavní (okénko hovoru)

Druhé okno (`IncomingCallWindow`) se skládá přímo v `application {}` vedle hlavního `Window`.

- **V `application {}` není `LocalLifecycleOwner`**, ten dává až `Window`. Volání
  `collectAsStateWithLifecycle` mimo okno shodí celou aplikaci hned při startu
  (`IllegalStateException: CompositionLocal LocalLifecycleOwner not present`, „Failed to
  launch JVM"). Mimo okno se proto sbírá `collectAsState`; uvnitř okna zase funguje všechno.
- **Motiv si okno bere samo** (`SettingsTheme`, tentýž jako `AppRoot`) — kompozice
  `application {}` žádný motiv nemá.

## Bubliny (tooltip) z kořene kompozice

**Holý M3 `TooltipBox` na desktopu shodí vykreslování**, když jeho kotva s viditelnou bublinou
zmizí z kompozice BĚHEM MĚŘENÍ: větev `BoxWithConstraints` podle šířky (dva panely ↔ jeden,
sbalený seznam chatů ↔ plný), slot `Scaffold`u, položka lazy seznamu. Skiko
(`CanvasLayersComposeSceneImpl.doMeasureAndLayout`) si napřed zkopíruje ownery všech vrstev
a pak je měří; popup bubliny se mezitím zlikviduje a smyčka sáhne na jeho owner —
`IllegalArgumentException: RootNodeOwner is already disposed`. Na Androidu je popup samostatné
okno a nespadne. Poprvé to shodilo sbalený seznam chatů (najetí na avatar, pak tah táhlem);
mechanismus i řešení jsou převzaté z KMPMlog, kde se na tutéž chybu přišlo dřív.

- **Bublina jde jen přes `WithTooltip` (text) nebo `RootTooltipBox` (vlastní obsah, stav řízený
  programem, vypnutá gesta)** v `core/ui/WithTooltip.kt`. Obojí kreslí bublinu přes
  `RootOverlay` z kořene kompozice (`RootOverlayHost` v `AppTheme`): kotva při odchodu jen
  odhlásí záznam a vrstvu zruší až běžná rekompozice hostitele, mimo měření. **Holý
  `TooltipBox` jinde nepustí `verifyTooltipsThroughRoot`** (visí na `check`, hledá import).
- **Kotva zůstává v M3 `TooltipBox`** (najetí myší, dlouhý stisk, fokus, přístupnost), jen
  dostane stav hlásící „neviditelný" a popup nezaloží. Bublinu kreslí druhý `TooltipBox`
  v kořeni nad týmž stavem, takže `show()` / `dismiss()` zvenku i animace odjezdu fungují dál.
- **V kořeni stojí NÁHRADNÍ kotva na mezích té skutečné v okně**, ne v rohu. KMPMlog jen
  podstrkává meze poskytovateli polohy, a to na bublinu se šipkou nestačí: `caretShape` si
  stranu šipky bere z TYPU poskytovatele (`Start` a `Right` mají šipku z boku) a polohu ze
  souřadnic kotvy. Obalený poskytovatel by stranu ztratil a kotva v rohu by šipku poslala jinam.
  Meze kotvy jsou stav, takže bublina jde za kotvou, když se pod ní pohne. Náhradní kotva nemá
  vstup, dotyk na skutečnou projde (test `anchorStaysClickableUnderVisibleBubble`).
- **Ztrátu fokusu a Escape obsluhuje obal sám**: M3 je podmiňuje viditelností bubliny a kotva
  ji vidí jako skrytou. Tabulátor z kotvy do bubliny s akcí mezi dvěma `TooltipBox` přenést
  nejde — žádná dnešní bublina s akcí ovšem gesta kotvy nemá (nápověda gest je řízená programem).
- **Pojistky v `jvmTest`** (`RootTooltipTest`, headless `runComposeUiTest` nad skiko scénou,
  bez okna): bublina přežije zúžení pod práh dvou panelů, sedí u kotvy nad ní i vedle ní,
  `WithTooltip` se ukáže najetím myši. `rawTooltipRemovedDuringMeasureCrashes` dokládá chybu
  v Compose Multiplatform 1.12: **až začne padat, upstream ji opravil** a obal i kontrola můžou
  pryč. Popup (menu) přes kořen vykreslit nejde — polohuje se k rodiči.

## Vložení ze schránky a přetažení souborů

Cmd+V (macOS), Ctrl+V a Shift+Insert ve vstupním poli vloží snímek obrazovky nebo zkopírované
soubory; soubory z Finderu či Průzkumníka jde pustit kamkoli do konverzace
(`KeyboardContent.jvm.kt`, testy `DroppedFilesTest`).

- **Přetažení přijímá celá konverzace, ne pole** (`ContentDropArea` kolem obrazovky). Myší přes
  velké okno se do jednoho řádku trefuje špatně a Telegram Desktop to má stejně. Během tažení je
  přes konverzaci výzva (`ContentDropOverlay`). **O právech ale rozhoduje pole**: přihlásí se
  u plochy (`LocalContentTargetSlot`), dokud je složené, se svými `accept*`. Bez pole (kanál jen
  ke čtení, výběr zpráv, úprava zprávy nic nepřijímá) plocha tažení odmítne — podmínky, kdy se pole
  ukazuje, se tak nemusely opisovat do obrazovky.
- **Soubory jdou jedním kusem** (`KeyboardContent.Attachments`), ne po jednom jako na Androidu:
  `stageAttachments` druhy nemíchá a další příloha jiného druhu by předchozí nahradila. Jsou-li
  VŠECHNY fotky (přípona, kterou čte ImageIO), jde album fotek, jinak všechno jako soubory. Jeden
  GIF odchází hned jako animace (jako na Androidu). Adresáře se přeskočí.
- **Klávesa se zachytí `onPreviewKeyEvent` nad polem** a propustí dál, když ve schránce není
  soubor ani obrázek — text vkládá pole samo.
- **Kontextová nabídka pole má „Vložit obrázek", u souborů „Vložit jako přílohu"**, jen když
  schránka nese, co pole přijme (rozhoduje totéž `clipboardPaste` jako klávesa). Desktop kreslí
  STAROU nabídku Compose (`ContextMenuArea`; `ComposeFoundationFlags.isNewContextMenuEnabled` je
  tu ve výchozím stavu `false`), která `appendTextContextMenuComponents` z Androidu a iOS nečte.
  Položka proto jde přes její vlastní `ContextMenuDataProvider` nad polem — seznam si skládá až
  při otevření, takže se schránka čte v tu chvíli. Přepnout desktop na novou nabídku by změnilo
  vzhled všech kontextových nabídek v aplikaci; kvůli jedné položce to nestálo za to. Ověřeno
  dočasným UI testem (pravé tlačítko nad polem, klik na položku) nad skutečnou schránkou.
- **Pořadí ve schránce: soubory, pak obrázek, ale jen BEZ textu.** Finder ke zkopírovanému
  souboru přidá jméno jako text, takže soubory mají přednost. Excel a Word dávají k buňkám
  a odstavcům i obrázek a vložit se má text; snímek obrazovky (`«class PNGf»`, Java ho vidí jen
  jako `imageFlavor`) text nenese.
- **Obrázek ze schránky se zmenší na 2560 px a uloží jako JPEG** do dočasného adresáře, jako
  fotka z výběru na mobilu: výřez z Retiny má přes 5 000 px a TDLib bere fotku do 10 MB.
  Průhlednost se podloží bílou. Ze `MultiResolutionImage` se bere největší varianta.
- **Ověřeno** na macOS testem nad skutečnou systémovou schránkou (PNG nastavené přes
  `osascript … as «class PNGf»`, text, seznam souborů); test se do repa nedostal, protože
  přepisuje schránku. **Přetažení z Finderu ověřené rukou není** — tah z jiné aplikace se z testu
  vyvolat nedá.

## Pravé tlačítko myši = podržení

Podržení (`onLongClick`) otevírá v aplikaci nabídky — zpráva, chat, složka, téma, samolepka,
možnosti odeslání, přepínač chatů. Na desktopu je dlouhý stisk myši věc, na kterou nikdo
nepřijde, a pravé tlačítko dřív nedělalo NIC: `combinedClickable` i `awaitFirstDown` na myši
berou jen levé tlačítko. Teď pravé tlačítko udělá totéž co podržení.

- **Obal `contextClickable`** (`core/ui/ContextClickable.kt`) zrcadlí parametry i přetížení
  `combinedClickable` a přidá `onSecondaryClick(onLongClick)`. Výměna byla jen jméno a import
  (31 souborů). **Holé `combinedClickable` hlídá `verifyContextClickable` na `check`** —
  ověřeno sondou (import i volání chytí, zmínku v komentáři ne).
- **Vlastní gesta podržení mají `onSecondaryClick` zvlášť**: odkaz v textu (`RichText`, jen nad
  odkazem přes `accepts`, jinde patří bublině), odesílací tlačítko (`longPressInstead`)
  a šipka zpět s přepínačem chatů (`ChatSwitcherButton`).
- **Jen myš, spouští se puštěním** (jako ve Windows), stisk se spotřebuje; puštění mimo prvek
  nic nespustí. **Vnořený prvek má přednost**: hlavní průchod jde od listů, takže pravé tlačítko
  na příloze v bublině otevře nabídku přílohy a bublina už spotřebovanou událost nechá být.
- Nabídka se otevře tam, kde po podržení (u prvku), ne u kurzoru.
- **Ctrl+klik na macOS** (tamní pravé tlačítko z jednotlačítkové myši) se zatím nepřevádí —
  přijde jako levé tlačítko s Ctrl.
- Platí i pro Android s myší (Chromebook, DeX).
- **Výjimka: bublina s textem na desktopu** (`contextClickable(rightClick = false)`): nad textem
  patří pravé tlačítko běžné textové nabídce, protože text jde vybírat myší (viz „Výběr textu
  myší v bublině" v `konverzace-akce.md`). Nabídka nad zprávou je za ⋮ v pilulce po najetí.
- **Ověřeno na Windows 11** (ladicí běh, uživatel): pravé tlačítko otevírá nabídky jako
  podržení. macOS a Android s myší neověřené.

## Kolečko u vodorovných řad

Řada, která se posouvá do strany, myší bez Shiftu nešla posunout: Compose u vodorovného posunu
svislé kolečko zahodí (`toSingleAxisDeltaFromAngle`) a tah myší posun nebere (`CanDragCalculation`
vynechává myš). `wheelScrollsHorizontally` (`core/ui/WheelScroll.kt`) převede převážně svislé
kolečko na posun do strany, jen když je v tom směru kam; jinak událost propadne dál.
`horizontalScrollWithWheel()` je `horizontalScroll` i s ním.

- **Má ho:** lišta složek, filtry hledání zpráv, lišta formátování, návrhy odpovědí, filtr
  v detailu reakcí, návrhy emoji a samolepek nad polem, řada kategorií a záložky ve výběru
  samolepek a emoji (`PrimaryScrollableTabRow` bere vlastní `scrollState`).
- **NEMÁ ho řada uvnitř svislého seznamu**: čipy reakcí pod zprávou, řádek lidí v hledání chatů
  (`LazyRow` v `LazyColumn`), záložky profilu (ve `verticalScroll` profilu). Kolečko by si je
  bralo, kdykoli přes ně kurzor při posouvání seznamu přejede, a seznam by se zastavil.
- **Kam ho dát:** u `horizontalScroll` ZA posun (blíž obsahu), aby kolečko dostal dřív; u `LazyRow`
  a tab row zvenku — posun uvnitř svislé kolečko nespotřebuje, takže dojde i tam.
- Krok 64 dp na zub (`scrollDelta` je v zubech), animace `defaultEffectsSpec`, rychlé točení se
  sčítá. Platí i pro Android s myší.
- **Ověřeno na emulátoru** (`adb shell input mouse scroll X Y --axis VSCROLL,±N`): lišta složek
  a záložky kategorií ve výběru emoji se posouvají tam i zpět, kolečko nad mřížkou emoji ji dál
  posouvá svisle a řada zůstane. Desktop za běhu neověřený.

## Zubaté a rozmazané obrázky (Coil na Skie)

Zmenšené fotky byly na desktopu zubaté (písmo na letáku v prohlížeči, hrany), na Androidu ne.
**Výchozí `SkiaImageDecoder` Coilu zmenšuje nejbližším sousedem**: dekóduje celý obrázek
a na cílovou velikost ho překreslí se `SamplingMode.DEFAULT`, což je ve Skie
`FilterMipmap(NEAREST, NONE)` (`coil3.util.makeFromImage` v `nonAndroidMain`, Coil 3.5 i `main`
v 3.6). Z každých pár pixelů se vezme jeden a zbytek zahodí.

- **Oprava je vlastní dekodér** (`DesktopImageLoader`, `SmoothSkiaImageDecoder` v `jvmMain`
  `core/ui`): týž výpočet velikosti, zmenšení přes mipmapy `LINEAR` / `LINEAR`. Zapojuje ho
  `Main.kt` přes `SingletonImageLoader.setSafe`; Coil dává komponenty od uživatele PŘED výchozí,
  takže se výchozí dekodér k obrázku nedostane.
- **Při kreslení `filterQuality` NEPOMŮŽE, ani `Medium`.** Compose na Skie mapuje `Medium` na
  mipmapy, jenže bitmapu kreslí `drawImageRect` se `strict = true` (`SkiaBackedCanvas`) a Skia
  s přísným výřezem mipmapy nepoužije — změřeno, `Medium` zubatí stejně jako `Low`. Na tom by
  se zubatý zmenšený obrázek vrátil všude, kde je bitmapa větší než plocha: fotka v plném
  rozlišení v prohlížeči a fotka z paměti Coilu, kterou bublina převezme po prohlížeči (Coil
  větší obrázek z paměti přijme místo dekódování menšího).
- **Proto dekodér vrací `SmoothImage`, ne `BitmapImage`.** Coil kreslí obrázek, který není
  bitmapa, přes `ImagePainter`: zmenší canvas na plochu a zavolá `Image.draw`. Ten kreslí
  s mipmapami `LINEAR` / `LINEAR` bez přísného výřezu a úroveň mipmapy si Skia vybere z matice,
  do které se přičte i přiblížení z `graphicsLayer`. Skia obrázek drží `SmoothImage` po celý
  život, ať si GPU nechá texturu i s mipmapami (Compose dělá `Image.makeFromBitmap` při každém
  kreslení).
- **Přiblížení v prohlížeči bylo rozmazané**, i když zmenšení už zubaté nebylo: dekodér zmenší
  přesně na kartu (na desktopu kolem 360 px na šířku) a přiblížení až 5× ten malý obrázek jen
  roztahovalo. Android to nemá — `BitmapFactory` zmenšuje přes `inSampleSize` po mocninách
  dvou, nechá obrázek až dvakrát větší než kartu, a karta je široká jako displej. Prohlížeč
  proto na desktopu bere plné rozlišení (`RemoteImage(fullResolution = …)`, `Size.ORIGINAL`,
  přepínač `zoomLoadsFullResolution`); Android a iOS dál velikost karty.
- **Testy hlídají obojí i s pastmi** (`SmoothSkiaImageDecoderTest`, `SmoothImageTest`): proužky
  po pixelu zmenšené na 37 px musí z dekodéru i z `SmoothImage` vyjít šedé, výchozí dekodér
  Coilu a Compose s `Medium` je musí dál dávat zubaté. Spadne-li některý z kontrolních testů,
  knihovna to opravila a obcházka může pryč. **Zmenšení NE přesně na desetinu**: tam padá střed
  každého vzorku na hranici dvou proužků a šedá vyjde i z obyčejného bilineárního filtru —
  první verze testu tak „dokázala", že `Low` průměruje.
- **iOS jede přes týž kód Coilu** (`nonAndroidMain`), takže ho nejspíš potkává obojí. Tam to
  ověřené ani opravené není (`zoomLoadsFullResolution = false`, výchozí dekodér).

## Zvuk: hlasovky a hudba (FFmpeg + javax.sound)

### Nahrávání hlasovek

`AudioRecorder` na desktopu: `TargetDataLine` (48 kHz mono 16 bitů) do paměti, po skončení
`FfmpegOpusEncoder` (`libopus` z FFmpegu — LGPL build ho má staticky v libavcodec, ověřeno
`avcodec_find_encoder_by_name`) a zabalení do Oggu společným `oggOpusFile`, jako iOS. Gesto,
zámek, náhled, navázání (`joinOggOpus`) i waveform jsou společné.

- **Nastavení kodéru jako na mobilech**: 20ms rámce (`frame_duration`), režim `voip`,
  32 kbit/s; `preSkip` z `initial_padding` kodéru. Poslední neúplný rámec se doplní tichem.
- **Pozastavená linka (`stop()`) vrací z `read` nulu hned** — čtecí vlákno pak chvíli čeká,
  jinak by točilo naprázdno.
- **Úroveň = průměrný výkon v dB → 0..1** (−50 dB a níž ticho), stejně jako `averagePower` na
  iOS. Sinus s amplitudou 0,7 je −6 dB, tedy ~0,88 (ne 1).
- **Povolení k mikrofonu dává macOS při prvním otevření linky** (TCC); Java se předem zeptat
  neumí. Balíček má kvůli hovorům `audio-input` a popis v Info.plist (teď „calls and voice
  messages"); spuštěné přes Gradle dostane povolení aplikace, ze které běží.
- **Testy** `DesktopVoiceRecordingTest`: sinus → `libopus` → Ogg → zpátky `FfmpegAudioDecoder`
  (délka, signál), spojení dvou nahrávek, úroveň.
- **Ověřeno v ladicí aplikaci 25. 10. 2026** (macOS, Uložené, nic neodesláno): podržení, tah
  nahoru zamkne, úrovně kolísají podle mikrofonu, ■ zakóduje náhled (0:14 s vlnou), přehrání
  náhledu, koš. **Neověřené**: odeslání (soubor přijatý serverem), navázání po náhledu, pauza,
  Windows a Linux, zamítnuté povolení mikrofonu (linka pak nahrává ticho).

### Přehrávání

`AudioEngine` na desktopu: `FfmpegAudioDecoder` dekóduje cokoli, co umí FFmpeg (Ogg/Opus
hlasovky, MP3, AAC, FLAC…) a převede to na 48 kHz stereo 16 bitů, `SourceDataLine` z
`javax.sound` to hraje. Přehrávač, dock a fronta hudby jsou společné, desktop dodává jen tohle.

- **JVM sám Opus ani MP3 nedekóduje**; FFmpeg z JavaCPP už v projektu byl kvůli videu, takže
  nová závislost nepřibyla. LGPL build má nativní dekodéry Opusu, MP3 i AAC.
- **Rychlost (1,5×, 2×) dělá filtr `atempo`** v grafu `abuffer → atempo → aformat →
  abuffersink`, ne převzorkování — hlas se zrychlí, ale nezvýší. Mění se za běhu příkazem
  filtru (`avfilter_graph_send_command`), takže změna uprostřed nahrávky nic nezahodí.
- **Pozice z odehraných vzorků** (`longFramePosition`), ne ze zapsaných: v lince leží ~85 ms,
  které ještě nezazněly. Od posunu a změny rychlosti se počítá od kotvy (čas + pozice linky),
  vzorek výstupu je při 1,5× o půl vzorku delší kus nahrávky.
- **Do linky se píše jen tolik, kolik se vejde** (`available()`); zastavená linka by zápis
  zablokovala a vlákno přehrávače by nevzalo další povel (posun, uvolnění).
- **Posun je po rámcích** (Opus 20 ms, MP3 26 ms): FFmpeg skočí na paket před cílem, rámce
  končící před ním se zahodí, filtr se postaví načisto.
- **Dekodér i linku zavírá vlákno přehrávače**, ne `release()` z hlavního vlákna — to by mu je
  mohlo vzít uprostřed čtení. `release()` jen zastaví a vyprázdní linku, ať zvuk hned utichne.
- **Testy nad skutečnými soubory** (`FfmpegAudioDecoderTest`, `jvmTest/resources/audio`):
  Opus i MP3 vyrobené `ffmpeg -f lavfi -i sine=…` (amplituda 1/8, špička ~4000).
- **Ověřeno v ladicí aplikaci 25. 10. 2026** (macOS, hlasovka v Notifikátoru): přehrání do konce
  (konec se ohlásí, přehrávač se vrátí do klidu), průběh a vlna, rychlost za chodu, pauza drží
  pozici, pokračování. Rychlost se ukládá do nastavení — po zkoušce vrátit na 1×.
  **Neověřené**: hudba a dock na skutečné skladbě, posun na delší nahrávce (jen testem),
  Windows a Linux (`javax.sound` tam jde přes jiné ovladače), zvuk videa.

## Video (FFmpeg)

GIF je v Telegramu MP4 (H.264) a JVM vlastní dekodér nemá. `VideoPlayer` na desktopu proto
dekóduje přes **FFmpeg z JavaCPP** (`org.bytedeco:ffmpeg`, LGPL build, dynamicky linkované
knihovny): `FfmpegVideoDecoder` čte snímky, swscale je převede rovnou do BGRA ve velikosti
plochy a `VideoSurface` je kreslí jako obrázky Skii. Hraje GIF, kolečko (tiché i se zvukem),
příběhy a video v prohlížeči médií; HEIC dál otevírá aplikace systému.

### Zvuk videa

- **Zvuk hraje `AudioEngine`**, tentýž přehrávač jako hlasovky (`FfmpegAudioDecoder`
  + `SourceDataLine`), nad druhým otevřením téhož souboru. Dvě čtení jednoho místního souboru
  nic nestojí a obraz i zvuk si tak drží vlastní posun bez společného demuxeru.
- **Obraz se řídí zvukem, ne naopak.** Snímek se ukáže, až pozice zvuku (odehrané vzorky linky)
  dojde k jeho PTS; ucho posun o pár desítek ms pozná, oko zahozený snímek ne. Nestíhá-li obraz
  o víc než 250 ms, snímky se zahazují bez převodu swscale (ten je dražší než dekódování),
  aspoň jeden za 250 ms se ale ukáže.
- **Bez zvuku se časuje podle hodin jako dřív**: ztlumené načtení (`load(muted = true)`, GIF),
  video bez zvukové stopy (`av_find_best_stream` ji nenajde) a systém bez zvukového výstupu.
  Když zvuk dohraje dřív než obraz, kotva se založí od snímku, na kterém skončil.
- **Ztlumení za běhu (`setMuted`) píše do linky ticho**, nezastavuje ji: hodiny obrazu jdou ze
  zvuku a zastavená linka by zastavila i obraz.
- **Rychlost dělá `atempo` ve zvuku** (hlas se nezvýší) a obraz dělí čekání toutéž rychlostí.
- **Pauza zastaví linku hned z hlavního vlákna**, ne až se probudí smyčka obrazu — dozvuk po
  stisku pauzy byl slyšet.
- **Kvality videa**: přepočítané od serveru jsou h264 a AV1; desktop nabízí h264 (FFmpeg ho
  dekóduje vždycky), AV1 jen s `libdav1d`, na který se z kompozice nedá zeptat bez nahrání
  nativních knihoven. Streamuje (viz „Streamování rozestavěného souboru").
- **Testy `DesktopVideoSoundTest`** ve skutečném čase nad `sound.mp4` / `silent.mp4`
  (`jvmTest/resources/video`, 2 s `testsrc` + sinus z `ffmpeg -f lavfi`): pozice po vteřině,
  konec, ztlumení, posun s rychlostí 2×, video bez zvukové stopy. Na stroji bez zvukového
  výstupu se testy se zvukem přeskočí.
- **Ověřeno v ladicí aplikaci 25. 10. 2026** (macOS, prohlížeč médií v Notifikátoru): MP4
  poslané jako soubor se otevře v prohlížeči místo systémové aplikace, hraje se zvukem, čas na
  obraze běží s pozicí, dohraje a jde pustit znovu, 2× zrychlí obraz i zvuk, ztlumení obraz
  nezastaví, pauza; pípnutí na poslech sedí na celé vteřiny počítadla. Video bez zvukové stopy
  hraje podle hodin. Kolečko (poslané testovacím botem přes `sendVideoNote`) se po ťuknutí
  přepne na zvuk (kruh průběhu, uplynulý čas) a po dohrání se vrátí do tiché smyčky.
  **Neověřené v aplikaci**: příběhy (tentýž přehrávač), dlouhé video a zahazování snímků,
  Windows a Linux.

### Streamování rozestavěného souboru

Video hraje, zatímco ho TDLib stahuje (`loadStreaming`): FFmpeg čte přes vlastní `AVIOContext`
(`StreamingIo` v `FfmpegInput.kt`) s čtením a posunem nad souborem v `temp/`, ne cestou.

- **Na data se čeká, mimo stažený úsek se stahování přesune k nim** (`requestFrom`). MP4
  s indexem (`moov`) na konci se tak rozběhne: FFmpeg si řekne o konec, pak o začátek.
- **Obraz a zvuk čtou každý svým vstupem**, takže si stahování přetahují — kdo čeká a stahování
  jde jinde, řekne si o ně znovu až po vteřině (jako `StreamingReader` na iOS). Na 40MB videu
  s indexem na konci se stahování přesunulo pětkrát (index a začátek pro obraz, pak pro zvuk).
- **Soubor se čte přes `FileChannel`** (NIO), ne `RandomAccessFile`: dostažený soubor TDLib
  přesune z `temp/` do `videos/` a otevřený soubor to přežije na macOS i Linuxu, na Windows jen
  otevřený se `FILE_SHARE_DELETE` — ten dává NIO, `RandomAccessFile` ne. Na Windows neověřené.
- **Čtení na data blokuje vlákno**, proto korutina rozestavěného souboru běží na `Dispatchers.IO`.
  Zrušení korutiny nativní čekání nepřeruší: načtení nese `AtomicBoolean` (`cancelled`), který
  přehrávač nastaví při odchodu (jiné video, uvolnění), a čekající čtení vrátí `AVERROR_EXIT`.
  Po 30 s bez dat čtení selže (jako na Androidu) a přehrávač ohlásí chybu.
- **FFmpeg krátké skoky vpřed nepřeskakuje, ale dočte** (`avio_seek`: velikost bufferu, tady
  64 kB, a k tomu `short_seek_threshold` 32 kB): o index
  kousek za koncem malého souboru ani o skok o pár kB dál si neřekne. Test proto stojí na 200kB
  souboru (`stream.mp4`, 4 s, index na konci, klíčový snímek každou vteřinu — x264 je jinak
  dává po 10 s a skok by se vrátil na začátek), na 15kB `sound.mp4` by nic neověřil.
- **Fragmentované MP4** (přepočítané kvality od serveru) FFmpeg čte bez HLS seznamu
  (`videoFragmentedStreamingSupported = true`, `…NeedsHls = false`).
- **Testy `DesktopVideoStreamingTest`** nad falešným stahováním, které se chová jako TDLib
  (řídký soubor plněný souvisle od místa, kam se stahování přesunulo): dekodér přečte všech 100
  snímků a řekne si o index na konci, přehrávač rozestavěný soubor přehraje i se zvukem, skok
  za stažený úsek přesune stahování k cíli, zrušené čekání skončí do vteřiny.
- **Ověřeno v ladicí aplikaci 25. 10. 2026** (macOS, 90s video 720p, 40 MB, index na konci,
  poslané testovacím botem s `supports_streaming`): video se rozběhlo do 3 s od puštění, hrálo
  dál, zatímco se soubor dostahoval a přesouval do `videos/`, dohrálo a jde přehrát znovu.
  **Neověřené v aplikaci**: skok za stažený úsek (tady se 40 MB stáhlo skoro hned; ověřuje ho
  jen test), pomalá síť, přepočítané kvality, Windows a Linux.

### Obraz

- **Proč FFmpeg, ne JCodec.** JCodec je čistá Java a ~1 MB, ale pomalý (GIFů hraje v chatu víc
  naráz), od roku 2020 se nevyvíjí a pro plné video a zvuk by se FFmpeg stejně přidával. Cena:
  nativní knihovny pro jeden systém mají 20–30 MB (Windows 30 MB).
- **Nativní jar jen pro systém, na kterém se staví** (`javacppPlatform` v `build.gradle.kts`
  sdíleného modulu), stejně jako Skia a TDLib. JavaCPP knihovny při prvním použití vybalí do
  `~/.javacpp/cache` — napoprvé to trvá kolem vteřiny, proto se dekodér otevírá jen v korutině
  přehrávače, nikdy na hlavním vlákně. Na systému bez knihoven `open` vyhodí
  `UnsatisfiedLinkError`, přehrávač nahlásí `isFailed` a zůstane náhled.
- **Cesta jde FFmpegu v UTF-8 jako `BytePointer`**, ne `String`: JavaCPP by řetězec převedl
  kódováním systému (JDK 17 na českých Windows cp1250) a soubor s diakritikou v cestě by nenašel.
- **Jedno vlákno dekodéru na přehrávač** (`thread_count = 1`): GIFů hraje víc naráz a výchozí
  sada vláken pro každý by jich vyrobila desítky. Korutiny běží na `Dispatchers.Default`
  a mezi snímky spí, takže vlákno drží jen po dobu dekódování.
- **Snímek se zmenšuje už ve swscale na velikost plochy** (`fitSize`, testy `FitSizeTest`),
  nikdy nezvětšuje. GIF 1280 × 720 v bublině by jinak převáděl a nahrával devětkrát víc pixelů,
  než je vidět. Barvy podle `colorspace` snímku (BT.709 u HD, jinak BT.601), jinak by HD bylo
  do zelena.
- **Skia obrázek snímku se zavírá ručně, na hlavním vlákně**, hned po výměně za další.
  `makeRaster` drží pixely v nativní paměti a halda o nich neví: spoléhat na GC by u GIFu
  znamenalo desítky MB za vteřinu, než se uklidí. Kreslí se na hlavním vlákně, takže v tu chvíli
  obrázek nikdo nekreslí, a co už je zaznamenané, drží si ve Skie vlastní referenci. Snímek
  z předchozího souboru nebo po uvolnění přehrávače se zahodí (`generation`).
- **Časování podle PTS od kotvy** (snímek a okamžik, kdy se pustil); pauza, skok a rychlost
  kotvu zahodí. Smyčka pokračuje na téže časové ose, aby první snímek nepřeskočil délku
  posledního. Zaostane-li dekodér o víc než 250 ms, čas se srovná od aktuálního snímku místo
  zrychleného dohánění.
- **Ověřeno na Windows** testovacími MP4 z FFmpegu v balíčku (`ffmpeg.exe` leží v nativním jaru,
  kodér `libopenh264`): 60 snímků po 33 ms, skok, smyčka, pauza, konec, cesta s diakritikou,
  barvy pruhů, vykreslení `VideoSurface` přes `ImageComposeScene`; GIFy v konverzaci
  ověřené v běžící aplikaci (23. 10. 2026). **Linux jen přeložený.**

## Celá obrazovka okna

Video v prohlížeči umí okno přes celou obrazovku (tlačítko, F, dvojklik; viz
`docs/poznatky/prohlizec-medii.md`, „Myš a klávesnice"). Sdílený kód o okně neví:
`rememberWindowFullscreen()` (jen desktop, na mobilech `null`) posílá žádost přes
`DesktopWindow.requestFullscreen` a vstupní bod přepne `WindowState.placement`.

- **Compose celou obrazovku ruší JEN přechodem na Floating.** `ComposeWindow.placement = Maximized`
  jen nastaví maximalizaci a okno nechá přes celou obrazovku (zdroj `ui-desktop` 1.12). Návrat
  do maximalizovaného okna je proto Floating a po dojetí animace (na macOS vlastní plocha)
  Maximized. Napoprvé to vypadalo, že Esc nefunguje: tlačítko se přepnulo, okno zůstalo.
- **Stav se hlásí z okna** (`DesktopWindow.fullscreen`), takže i zelené tlačítko na macOS
  prohlížeč pozná. Okno, které přes celou obrazovku dal uživatel sám, prohlížeč nevrací.
- **Celá obrazovka se neukládá** jako maximalizované okno (`RememberWindowBounds` ji přeskočí):
  příští spuštění by se jinak otevřelo přes celou obrazovku.
- **Dvojklik nástroje pro ovládání počítače (computer-use) je pro Compose moc rychlý**:
  `detectTapGestures` bere druhé ťuknutí jako dvojité až po 40 ms (`doubleTapMinTimeMillis`),
  rychlejší vyhodnotí jako dvě jednoduchá. Zkouší se dvěma kliknutími po ~120 ms.
- **Ověřeno v ladicí aplikaci 25. 10. 2026** (macOS, maximalizované okno): F, dvojklik, tlačítko
  dovnitř i ven, Esc ukončí celou obrazovku a prohlížeč nechá otevřený, návrat do galerie okno
  vrátí do maximalizovaného, mezerník po kliknutí na přehrát, šipka +5 s, M. **Neověřené**:
  plovoucí okno, ukončení zeleným tlačítkem, Windows a Linux (Compose tam celou obrazovku dělá
  bez vlastní plochy).

## Úprava fotky

Ořez, otočení a kresba do fotky (`DesktopPhotoMarkupRenderer`) ve Skie — tentýž editor jako na
mobilech, stejné pořadí: tahy v souřadnicích originálu, pak otočení a ořez jednou transformací
plátna (jako iOS), JPEG s `PHOTO_JPEG_QUALITY`.

- **Skia otáčí podle EXIF sama, ImageIO ne.** `Image.makeFromEncoded` dekóduje JPEG 800 × 600
  s orientací 6 jako 600 × 800, `ImageIO.read` jako 800 × 600 (ověřeno pokusem). Editor (Coil
  na desktopu dekóduje přes `Image.makeFromEncoded`) tedy fotku ukazuje otočenou a tahy jsou
  podíly téhle podoby — renderer proto čte originál týmž voláním.
- **Rozměry přílohy se otáčejí podle EXIF** (`uprightSize` ve výběru příloh, `Codec` čte jen
  hlavičku). Dřív je dávalo ImageIO bez otočení, takže snímek z fotoaparátu na výšku měl
  prohozený poměr stran — v editoru i v bublině.
- **Skia v projektu staví cesty přes `PathBuilder`** (skiko 0.150): `Path` už `moveTo`/`lineTo`
  nemá a `Data.makeFromFileName` neexistuje (bajty přes `File.readBytes`).
- **Ověřeno v ladicí aplikaci 24. 10. 2026** (macOS): v tvorbě samolepky pero, otočení a ořez —
  vykreslená samolepka přesně odpovídá editoru. **Neověřené v aplikaci**: úprava přílohy
  v konverzaci a ve sdílení (stejný renderer, testy `DesktopPhotoMarkupRendererTest`), fotka
  s EXIF orientací (jen testem), zvýrazňovač a začernění, Windows a Linux.

## Co platforma (zatím) neumí

**Hovory** přes ntgcalls (`docs/poznatky/hovory.md`, „Desktop"), se zvukem i videem, bez
přepínání kamery; náhled kamery před spojením jen na macOS.

Prázdné `actual` volí chování, které sdílený kód už umí ohlásit, ne tiché nic: HEIC se otevírá
v aplikaci systému (`viewerUnsupportedFormats`), kamera pro kolečka je `null` (nenabídne se). Seznam chybějícího je v `docs/roadmap.md`, sekce Desktop.

- **Výběr příloh je AWT `FileDialog`** (na macOS nativní panel). Soubor se nekopíruje — na
  rozdíl od mobilů tu žádný dočasný adresář výběru není. Fotka (co přečte ImageIO) jde jako
  fotka, ostatní včetně videa jako soubor.
- **Vložení ze schránky a přetažení souborů** mají vlastní oddíl výš.
- **`Desktop.browse`/`open` na Linuxu bez GNOME knihoven nefungují**, náhradou je `xdg-open`.
- **Titulek okna na Linuxu se motivem neřídí** (`WindowTitleBarAppearance` tam nedělá nic).
  Kreslí ho správce oken a AWT běží přes X11 (na Waylandu XWayland). Zvažované cesty, nic
  z toho neověřené, protože Linux na zkoušku není:
  - **Tmavý titulek podle motivu na GNOME**: vlastnost okna `_GTK_THEME_VARIANT = "dark"`
    přes JNA (`Native.getComponentID` + `XChangeProperty` z libX11), přepnutelná za běhu.
    Nejisté je, jestli ji respektuje i `mutter-x11-frames`, který od GNOME 44 kreslí titulky
    X11 oken. Barvu titulku tak nastavit nejde.
  - **Barva titulku jen na KDE**: KWin čte `_KDE_NET_WM_COLOR_SCHEME` (cesta k souboru
    barevného schématu, `.colors`) a Breeze podle něj barví titulek; soubor by se generoval
    z palety. Na GNOME nic nedělá.
  - **Vlastní titulek v Compose** (okno bez rámu) by šel všude, ale tahání, dvojklik,
    přichytávání a změnu velikosti by bylo nutné dodělat ručně — za tu cenu nestojí.
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
