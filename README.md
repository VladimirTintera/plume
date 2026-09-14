# Plume

Neoficiální Telegram klient postavený důsledně na Material 3 Expressive.
Kotlin Multiplatform, sdílené Compose UI pro Android a iOS.

> Používá [Telegram API](https://core.telegram.org/) přes [TDLib](https://core.telegram.org/tdlib).
> Není to oficiální produkt Telegramu a není s ním nijak spojený.

## Stav: fáze 6

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

**Fáze 6** přidala notifikace o nových zprávách (viz níž — část vyžaduje tvoje účty).

**Není hotové:** přehrávání videa a zvuku, otevírání dokumentů, editace, skok na první
nepřečtenou zprávu, notifikace na iOS, sponsored messages, perzistence nastavení.

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

**Co je hotové v kódu:** registrace tokenu (`registerDevice`), příjem a předání zašifrovaného
payloadu TDLib (`processPushNotification`), zobrazení výsledku. Tahle cesta **není odzkoušená** —
bez Firebase projektu ji nejde spustit.

**Co musíš udělat ty:**

1. Založ Firebase projekt a přidej do něj Android aplikaci s balíčkem `eu.tintera.plume`.
2. Stáhni `google-services.json` a ulož ho do `client/androidApp/`.
   Bez něj se projekt přeloží normálně, jen bez pushe — build to vypíše.
3. Na [my.telegram.org](https://my.telegram.org) otevři svoji aplikaci a nahraj **FCM klíč**
   (service account). Telegram totiž posílá push přes *tvůj* Firebase projekt, ne přes svůj.
4. Pro iOS: v Apple Developer účtu vytvoř APNs klíč, nahraj ho tamtéž a v Xcode zapni capability
   *Push Notifications*.

**iOS zatím notifikace nezobrazuje vůbec.** Lokální notifikace z běžící aplikace tam nemají cenu
(na pozadí systém proces uspí) a skutečná cesta vede přes notification service extension, která
payload rozšifruje — to je samostatný kus práce.

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
- **Sponsored messages v kanálech se musí zobrazovat** a nesmí se do nich zasahovat.
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
