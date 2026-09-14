# Plume

Neoficiální Telegram klient postavený důsledně na Material 3 Expressive.
Kotlin Multiplatform, sdílené Compose UI pro Android a iOS.

> Používá [Telegram API](https://core.telegram.org/) přes [TDLib](https://core.telegram.org/tdlib).
> Není to oficiální produkt Telegramu a není s ním nijak spojený.

## Stav: fáze 1

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

Hotové obrazovky: přihlášení (telefon → kód → 2FA, plus QR), seznam chatů, kostra konverzace,
Nastavení s účtem a odhlášením.

**Není hotové:** obsah konverzace, média, push notifikace, sponsored messages, perzistence nastavení.

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
