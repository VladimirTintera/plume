# Plume

Neoficiální Telegram klient postavený důsledně na Material 3 Expressive.
Kotlin Multiplatform, sdílené Compose UI pro Android a iOS.

> Používá [Telegram API](https://core.telegram.org/) přes [TDLib](https://core.telegram.org/tdlib).
> Není to oficiální produkt Telegramu a není s ním nijak spojený.

## Stav: fáze 7

**Fáze 0 (de-risking)** ověřila na reálném běhu, že architektura drží:

| Co | Výsledek |
|---|---|
| TDLib se slinkuje do iOS frameworku | ✅ symboly `_td_create_client_id`, `_td_send`, `_td_receive`, `_td_execute` |
| TDLib naběhne na Androidu | ✅ `libtdjsonjava.so … ok` |
| Přihlášení projde proti serverům Telegramu | ✅ automat dojde do `WaitPhoneNumber` |
| Material 3 Expressive ze sdíleného kódu | ✅ `MaterialExpressiveTheme` + vlastní `MotionScheme` |
| Material You na Androidu / paleta ze seedu na iOS | ✅ |

**Fáze 1** přidala Navigation3, adaptivní shell a živý seznam chatů — ověřeno na reálném účtu:
seznam se naplní z TDLib, na šířce naskočí `WideNavigationRail` a dvoupanelové list-detail
rozvržení s vybraným chatem zvýrazněným přes `secondaryContainer`.

**Fáze 2** přidala textovou konverzaci — ověřeno na reálném účtu: načte se historie, bubliny se
seskupují po odesílateli, nové zprávy naskakují živě a seznam chatů se pod nimi přeskládá.

**Fáze 3** přidala média — avatary v seznamu i v hlavičce konverzace a fotky přímo v bublinách,
včetně stahování na pozadí a progresivního náhledu. Chaty bez obrázku dostanou iniciály na barvě
odvozené z id, takže je barva stabilní mezi spuštěními.

**Fáze 4** přidala reakce — čipy pod bublinami s emoji a počtem, dlouhý stisk otevře výběr
z nabídky, kterou dává TDLib pro daný chat. Vlastní reakce se odliší tvarem i barvou naráz
(zaoblení se stáhne z kapsle do měkkého obdélníku) přes `MaterialTheme.motionScheme`.
U reakcí s pár lidmi se místo čísla ukážou jejich profilovky; nad stropem se čip sbalí na počet.

Hotové obrazovky: přihlášení (telefon → kód → 2FA, plus QR), seznam chatů, konverzace,
Nastavení s účtem a odhlášením.

**Fáze 5** přidala odpovědi — dlouhý stisk nabídne „Odpovědět", citace se ukáže nad vstupním
polem i v bublině odpovědi.

**Fáze 7** doladila odesílání: zpráva se zobrazí okamžitě, se stavem doručení (hodiny → fajfka →
dvojitá fajfka) a s automatickým odrolováním. Příchozí zprávy scrollují jen když jsi dole, jinak
se objeví odznak s počtem čekajících.

**Fáze 6** přidala notifikace o nových zprávách (viz níž — část vyžaduje tvoje účty), včetně
vysvětlení před systémovým dialogem a řádku v Nastavení, kterým je lze kdykoliv zapnout zpět.

**Notifikace vedou do konkrétního chatu** — ťuknutí otevře konverzaci, ať aplikace běží, nebo
se startuje od nuly.

**Emoji a samolepky:** zpráva jen z emoji se kreslí velká a bez bubliny, samolepky se vykreslují
jako obrázek (animované zatím jako nehybný náhled).

**Seznam chatů je seskupený** do bloků se společným zaoblením (připnuté, nepřečtené, ostatní),
jak to dělá M3 Expressive místo karet per položku. U položky je čas poslední zprávy, ztlumení,
zelená tečka u online protějšku a živé „píše…"; v hlavičce konverzace „naposledy viděn".
Dlouhý stisk nabídne akce: připnout, ztlumit, označit jako nepřečtené, smazat nebo opustit.

**Ostatní typy zpráv** (ankety, polohy, kontakty, video kolečka, platby) se zatím nevykreslují,
ale mají popis; systémová oznámení se kreslí na střed jako v běžném klientovi.

**Není hotové:** přehrávání videa a zvuku, otevírání dokumentů, editace, skok na první
nepřečtenou zprávu, animované samolepky (.tgs/.webm), interakce s anketami a polohami,
notifikace na iOS, perzistence nastavení.

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
| Telegram → FCM → zařízení → zobrazení | ❓ neodzkoušeno |

**Nastavení Androidu (hotové):** `google-services.json` patří do `client/androidApp/`. Je
v `.gitignore`, takže po klonu na jiném stroji ho tam musíš dát znovu — bez něj se projekt přeloží
normálně, jen bez pushe, a build to vypíše.

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

## Release do Google Play

Vydává se **pushnutím tagu** `android-<versionName>-<versionCode>` (např. `android-0.2.1-13`).
Workflow [`android-release.yml`](.github/workflows/android-release.yml) z něj vytáhne verzi,
postaví podepsaný AAB, nahraje ho na **internal track** a založí GitHub Release s changelogem.
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
| `GOOGLE_SERVICES_JSON` | celý obsah `client/androidApp/google-services.json` (volitelné) |

Bez `GOOGLE_SERVICES_JSON` se release postaví, jen bez push notifikací — workflow to ohlásí
varováním. Bez `TELEGRAM_API_ID` build **spadne**: zapekla by se nula a appka by se nepřihlásila.

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
| `libtdjsonjava.so` na jedno ABI | 15–25 MB |
| iOS debug framework | ~383 MB (nestripovaný) |
| iOS release framework (arm64) | 242 MB → **199 MB** po stripnutí symbolů → **~58 MB** zabalený |

Čísla vypadají hrozivě, ale to, co reálně stahuje uživatel, je poslední sloupec:

- **Android:** AAB s ABI splity — uživatel stáhne jen svoje ABI, tedy ~21 MB nativního kódu místo 84 MB.
  Nic se pro to nemusí konfigurovat, Play to dělá sám z App Bundle.
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

## Licence

[MIT](LICENSE).

Použité cizí komponenty mají vlastní licence a MIT je nepřebíjí:

- **TDLib** — [Boost Software License 1.0](https://github.com/tdlib/td/blob/master/LICENSE_1_0.txt).
  Permisivní, bez povinnosti zveřejnit zdrojáky.
- **Material Symbols** (ikony v `composeResources`) — [Apache License 2.0](https://github.com/google/material-design-icons/blob/master/LICENSE).
- **Telegram API** se řídí [API Terms of Service](https://core.telegram.org/api/terms) nezávisle
  na licenci téhle aplikace — viz omezení výš.
