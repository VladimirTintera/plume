# Plume

Neoficiální Telegram klient postavený důsledně na Material 3 Expressive.
Kotlin Multiplatform, sdílené Compose UI pro Android a iOS.

> Používá [Telegram API](https://core.telegram.org/) přes [TDLib](https://core.telegram.org/tdlib).
> Není to oficiální produkt Telegramu a není s ním nijak spojený.

## Stav

Vývoj je nejdál na Androidu; iOS je pozadu o notifikace a hlášení pádů. Hotové obrazovky:
přihlášení (telefon → kód → 2FA, plus QR), seznam chatů, konverzace, prohlížeč médií, výběr chatu
pro přeposlání a Nastavení s účtem a odhlášením.

**Konverzace** umí historii se stránkováním, skok na první nepřečtenou, předěly dne, živé zprávy,
odesílání s letem bubliny, koncept uložený v TDLib, „píše…" oběma směry, úpravu vlastní zprávy,
odpovědi (i tahem doprava), reakce, připnuté zprávy, přeposílání, formátování textu a náhledy
odkazů. Přílohou může být fotka, video i soubor, jednotlivě nebo jako album, s průběhem nahrávání
a možností ho zrušit; fotky a videa se otevírají v prohlížeči médií, dokument se předá systému.
Hlasovky se nahrávají i přehrávají přímo v bublině. Samolepky a emoji mají vlastní výběr (sady,
hledání, oblíbené, trendy, nedávná emoji, odstíny pleti), animované `.tgs` se přehrávají.

**Seznam chatů** je seskupený do bloků se společným zaoblením (připnuté, nepřečtené, ostatní),
jak to dělá M3 Expressive místo karet per položku. U položky je čas poslední zprávy, ztlumení,
zelená tečka u online protějšku a živé „píše…"; dlouhý stisk nabídne připnout, ztlumit, označit
jako nepřečtené, archivovat, smazat nebo opustit. Archiv je samostatný seznam v menu ⋮. Když spojení nestojí, řekne to hlavička místo názvu sekce.

**Notifikace** na Androidu vedou do konkrétního chatu, ať aplikace běží, nebo se startuje od nuly
(nastavení účtů viz níž). Release build je minifikovaný a hlásí pády do Crashlytics.

**Co ještě není:** hledání, profil chatu a nová konverzace nemají obrazovku; `.webm`
samolepky zůstávají statické a ankety, polohy nebo video kolečka se zatím jen popisují textem.
Úplný seznam se vede v roadmapách, ne tady: [docs/roadmap.md](docs/roadmap.md) přes celou
aplikaci, [docs/roadmap-konverzace.md](docs/roadmap-konverzace.md) pro detail chatu.

## Rozjetí

1. Vygeneruj si `api_id` a `api_hash` na [my.telegram.org](https://my.telegram.org) → API development tools.
2. Vlož je do `local.properties` (je v `.gitignore`):

   ```properties
   sdk.dir=/cesta/k/Android/sdk
   telegramApiId=1234567
   telegramApiHash=abcdef…
   ```

3. Android:

   ```bash
   ./gradlew :client:androidApp:installDebug
   ```

4. iOS — otevři `iosApp/iosApp.xcodeproj` v Xcode a spusť. Framework se staví automaticky
   (build fáze „Compile Kotlin Framework"). Do `iosApp/Configuration/Config.xcconfig` doplň `TEAM_ID`,
   pokud chceš běžet na zařízení.

## Push notifikace

Aplikace zobrazuje notifikace o nových zprávách **i bez jakéhokoli nastavení** — dokud proces běží
a je připojený, vydává je TDLib sám. Push je jen doprava pro případ, kdy aplikace neběží, a ten
se bez tvých účtů zprovoznit nedá.

Stav řetězu na Androidu:

| Krok | Stav |
|---|---|
| Zobrazení notifikace (TDLib → systémová lišta) | ✅ ověřeno |
| Získání FCM tokenu | ✅ ověřeno |
| `registerDevice` — Telegram token přijal | ✅ ověřeno |
| FCM klíč nahraný na my.telegram.org | ✅ service account JSON (FCM v1) |
| Telegram → FCM → zařízení → zobrazení | ✅ ověřeno na zařízení (i ze zmrazeného procesu) |

**Nastavení Androidu (hotové):** `google-services.json` je v repozitáři v `client/androidApp/`,
takže push funguje hned po klonu, i v debug buildu. Tajemství v něm nejsou — API klíč Firebase je
identifikátor projektu. Bez souboru by se projekt přeložil normálně, jen bez pushe.

**FCM klíč:** Telegram přijímá **service account JSON** (Firebase Console → Project settings →
Service accounts → Generate new private key). Dokumentace na core.telegram.org pořád mluví
o „application key (GCM)" — to je zastaralé, legacy endpoint Google vypnul v červnu 2024.

**Jak push otestovat:** aplikaci **odswipuj z přehledu úloh**, nezavírej ji přes Vynutit ukončení.
Force-stop dostane aplikaci do „stopped state", ve kterém jí systém FCM zprávy nedoručí, dokud ji
ručně nespustíš — testem přes force-stop by to vypadalo, že push nefunguje.

**Pro iOS:** v Apple Developer účtu vytvoř APNs klíč, nahraj ho tamtéž a v Xcode zapni capability
*Push Notifications*. Samotné zobrazení notifikací ale na iOS zatím není postavené (viz níž),
takže to zatím nemá co využít.

**iOS zatím notifikace nezobrazuje vůbec.** Lokální notifikace z běžící aplikace tam nemají cenu
(na pozadí systém proces uspí) a skutečná cesta vede přes notification service extension, která
payload rozšifruje — to je samostatný kus práce.

## Jazyky

Aplikace je **anglicky s českou mutací**. Texty jsou v `client/shared/src/commonMain/composeResources`:
`values/strings.xml` je výchozí (anglický), `values-cs/strings.xml` česká verze. Jazyk se bere
ze systému; přepnout jen aplikaci jde na Androidu 13+ v systémovém nastavení aplikace.

Nový text musí přibýt **do obou souborů** — `./gradlew :client:shared:verifyTranslations` (běží
i jako součást `check`) shodí build, když se rozejdou.

Vyzkoušet druhý jazyk bez přepínání celého telefonu:

```bash
adb shell cmd locale set-app-locales eu.tintera.plume --locales cs-CZ
```

Prázdná hodnota (`--locales ""`) vrátí systémový jazyk.

## Hlášení pádů

Release build hlásí pády do **Firebase Crashlytics** — do téhož Firebase projektu, ze kterého je
`google-services.json` pro push, takže není co nastavovat navíc. Hlášení se objeví ve Firebase
Console → Crashlytics (první může chvíli trvat, konzole projekt při něm dokončí onboarding).

Kromě samotných pádů tam chodí:

- **zachycené chyby** — všechno, co aplikace zaloguje jako `error` i s výjimkou, se založí jako
  samostatný problém, aniž by aplikace spadla;
- **kontext k pádu** — poslední řádky logu (`info` a výš, nejvýš 64 kB), takže je vidět, co se
  dělo těsně předtím. Obsah zpráv se do logu nepíše.

Co je dobré vědět:

- **Sběr je zapnutý jen v release buildu.** V debug buildu je vypnutý natvrdo (manifest), aby
  se konzole nezaplnila pády z vývoje.
- **Hlášení odejde až při příštím spuštění aplikace**, ne v okamžiku pádu. Po pádu je tedy potřeba
  aplikaci ještě jednou spustit, jinak se v konzoli nic neukáže.
- **Bez `google-services.json` aplikace funguje dál**, jen bez hlášení pádů — stejně jako bez pushe.
- **iOS zatím pády nehlásí.** Firebase v Xcode projektu není; kód je připravený tak, že stačí
  doplnit implementaci ze Swiftu.

## Release do Google Play

Vydává se **pushnutím tagu** `android-<versionName>-<versionCode>` (např. `android-0.2.1-13`).
Workflow [`android-release.yml`](.github/workflows/android-release.yml) z něj vytáhne verzi,
postaví podepsaný AAB, nahraje ho na **internal track** i s textem „Co je nového" (cs + en,
z poznámek v [`release-notes/`](release-notes), které přidává každý PR) a založí GitHub Release
s poznámkami a changelogem.
Žádný verzovací commit není potřeba — verze se do buildu dostane přes `-P`.

Běžné vydání se spouští z GitHubu: **Actions → „Release — bump & tag" → Run workflow**, vybrat
typ bumpu (`build` / `patch` / `minor` / `major`). Workflow dopočítá verzi z posledního tagu,
tag pushne a release spustí. `versionCode` se zvyšuje vždy — Play si pamatuje každé nahrané
číslo a stejné podruhé nevezme.

### Co je potřeba nastavit jednou

**1. Service account pro Play Console.** Patří do **vlastního GCP projektu** (např. `mobile-ci`),
ne do Firebase projektu aplikace — do toho potřebuje přístup každý, kdo ladí push notifikace,
a takový člověk by tím zároveň mohl vystavit klíč s právem publikovat do Play. Vlastní projekt
se navíc dá použít i pro další aplikace a přežije případné smazání Firebase projektu.

Postup: v [Google Cloud](https://console.cloud.google.com/) založ projekt (*název* `mobile-ci`,
ID musí být globálně unikátní, takže klidně vygenerované), povol v něm **Google Play Android
Developer API**, vytvoř service account a stáhni jeho **JSON klíč**. Pak v Play Console →
*Users and permissions* → *Invite new users* pozvi e‑mail toho service accountu a dej mu
u aplikace Plume práva **Release → Release apps to testing tracks** (plus *View app information*).

Oprávnění k publikování tedy **nejde přes GCP IAM**, ale přes Play Console; GCP projekt jen
hostuje identitu a má povolené API. Propojení se nastavuje v Play Console → *Setup* → *API access*
— pozor, **linkuje se jeden Cloud projekt na celý developer účet**. Když už tam nějaký připojený
je, je čistší service account založit v něm než link přepisovat.

**2. Secrets v repozitáři** (Settings → Secrets and variables → Actions):

| Secret | Odkud |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | `base64 -i plume-release.jks \| tr -d '\n' \| pbcopy` |
| `ANDROID_KEYSTORE_PASSWORD` | `releaseStorePassword` z `local.properties` |
| `ANDROID_KEY_ALIAS` | `releaseKeyAlias` (`plume`) |
| `ANDROID_KEY_PASSWORD` | `releaseKeyPassword` |
| `TELEGRAM_API_ID` | `telegramApiId` z my.telegram.org |
| `TELEGRAM_API_HASH` | `telegramApiHash` z my.telegram.org |
| `PLAY_SERVICE_ACCOUNT_JSON` | celý obsah JSON klíče service accountu |

`google-services.json` secret nepotřebuje, je v repozitáři. Bez `TELEGRAM_API_ID` build **spadne**: zapekla by se nula a appka by se nepřihlásila.

**3. První AAB nahrát ručně.** Dokud aplikace nemá v Play žádný release, androidpublisher API
upload odmítne — první bundle tedy musí do Play Console projít přes web. Postav ho lokálně:

```bash
./gradlew :client:androidApp:bundleRelease -PandroidVersionName=0.2.0 -PandroidVersionCode=12
```

Výsledek je v `client/androidApp/build/outputs/bundle/release/androidApp-release.aab`.
Při nahrání nech zapnuté **Play App Signing** — `plume-release.jks` se tím stane *upload* klíčem
a Play si drží vlastní podpisový. Ten keystore proto **nesmí být ztracen**: bez něj už nejde
nahrát další verze (dá se resetovat přes podporu, ale je to otrava).

**4. Zaznamenat výchozí tag**, aby bump workflow věděl, odkud počítat. Ručně nahraný release
se značí suffixem `-manual`, který release workflow *nespustí*:

```bash
git tag android-0.2.0-12-manual && git push origin android-0.2.0-12-manual
```

Od téhle chvíle už stačí spouštět „Release — bump & tag".

### Lokální release build

Podpisové hodnoty a api_id se berou v pořadí **gradle property → ENV → `local.properties`**,
takže lokálně stačí mít v `local.properties`:

```
releaseStoreFile=plume-release.jks
releaseStorePassword=...
releaseKeyAlias=plume
releaseKeyPassword=...
```

`verifyReleaseConfiguration` běží před každým `assembleRelease`/`bundleRelease` a shodí build,
když něco z toho chybí — nepodepsaný AAB by Play stejně nevzal.

## Velikosti

TDLib je velká nativní knihovna a je to vidět:

| | Velikost |
|---|---|
| Debug APK (všechny 4 ABI) | ~100 MB |
| Release APK arm64 (R8) | **26 MB** |
| `libtdjsonjava.so` na jedno ABI | 15–25 MB |
| iOS debug framework | ~383 MB (nestripovaný) |
| iOS release framework (arm64) | 242 MB → **199 MB** po stripnutí symbolů → **~58 MB** zabalený |

Čísla vypadají hrozivě, ale to, co reálně stahuje uživatel, je poslední sloupec:

- **Android:** AAB s ABI splity — uživatel stáhne jen svoje ABI, tedy ~21 MB nativního kódu místo 84 MB.
  Nic se pro to nemusí konfigurovat, Play to dělá sám z App Bundle. K tomu R8, který dex zmenšil
  z 43,8 MB na 7,2 MB — TDLib generuje tisíce tříd API, které klient nevolá.
- **iOS:** `.ipa` je zip a Xcode při archivaci symboly stripuje. Naměřeno ~58 MB zabaleno; App Store
  aplikuje ještě vlastní kompresi a thinning, takže reálná velikost ke stažení bude kolem toho nebo níž.

Není to malá appka, ale je to v normálu pro klienta s vlastním MTProto stackem — oficiální Telegram
pro iOS se pohybuje v podobných řádech.

## Licenční podmínky, na které nezapomenout

Z [Telegram API ToS](https://core.telegram.org/api/terms) — tohle shodí review, když se to přehlédne:

- Název **nesmí obsahovat „Telegram"** bez předřazeného „Unofficial". Oficiální logo je registrovaná známka.
- V popisu na storu musí být **prominentně uvedeno**, že appka používá Telegram API.
- **Sponsored messages v kanálech se musí zobrazovat** a nesmí se do nich zasahovat — implementováno
  včetně hlášení zhlédnutí a kliknutí.
- Zákaz použití dat k trénování AI/ML.
- Povinnost open-source **není** — TDLib je pod Boost licencí, ne GPL.

## Dokumentace

Architektura, konvence a pasti jsou v [CLAUDE.md](CLAUDE.md).

Co zbývá udělat, se vede ve dvou živých dokumentech — v README ani v CLAUDE.md ten seznam
není, aby se nerozešly: [docs/roadmap.md](docs/roadmap.md) přes celou aplikaci,
[docs/roadmap-konverzace.md](docs/roadmap-konverzace.md) pro detail chatu.

## Licence

[MIT](LICENSE).

Použité cizí komponenty mají vlastní licence a MIT je nepřebíjí:

- **TDLib** — [Boost Software License 1.0](https://github.com/tdlib/td/blob/master/LICENSE_1_0.txt).
  Permisivní, bez povinnosti zveřejnit zdrojáky.
- **Material Symbols** (ikony v `composeResources`) — [Apache License 2.0](https://github.com/google/material-design-icons/blob/master/LICENSE).
- **Telegram API** se řídí [API Terms of Service](https://core.telegram.org/api/terms) nezávisle
  na licenci téhle aplikace — viz omezení výš.
