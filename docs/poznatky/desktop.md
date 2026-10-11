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
