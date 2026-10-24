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

## Seznam chatů

- `ChatRepository.chats()` jako na telefonu; repository seznam po přihlášení načte sama.
  Karta: název, náhled poslední zprávy (`UiText.resolve()`), počet nepřečtených v rohu.
- **Položky seznamu BEZ klíče.** S `key = id` seznam po přihlášení začínal uprostřed: chaty
  přibývají postupně a lazy seznam drží první viditelnou položku, takže nové nahoře odjely nad
  displej i s nadpisem. Na hodinkách se má začínat nahoře.

## Emulátor

- AVD **`Plume_Wear`** (`wearos_large_round`, obraz `system-images;android-34;android-wear;arm64-v8a`,
  Wear OS 5), port 5600, spouštět s oknem — QR kód se skenuje z obrazovky.
- První start ukazuje systémové „Starting…" ještě po `sys.boot_completed`; aplikace je pod ním.
- Varování `CompositionLocals: SecurityException … reduce_motion … targetSdkVersion lower than
  or equal to: 34` hlásí Compose for Wear OS na Wear OS 5 s vyšším `targetSdk`; knihovna ho ošetří
  a jede dál.
