# Zámek aplikace

Poznatky k jedné oblasti aplikace. Obecná pravidla projektu a rejstřík ostatních souborů jsou
v [CLAUDE.md](../../CLAUDE.md); poučení z opravy v téhle oblasti patří sem.

## Co zámek chrání a co ne

`AppLock` (`core/lock`) zamyká PINem, případně biometrií, **jen obrazovku**. Chrání před někým,
kdo drží odemčený telefon — ne před rootnutým zařízením, které umí smazat soubor se záznamem
(stejná mez jako u klíče k databázi, viz [databaze-spojeni-proxy.md](databaze-spojeni-proxy.md),
„Šifrovaná databáze TDLib").

- **Klíč k databázi zámek NEPOUŽÍVÁ a ověření nevyžaduje.** Push budí proces, když je telefon
  v kapse, a TDLib potřebuje databázi dřív, než se složí UI; klíč za PINem by znamenal, že
  z pozadí nechodí notifikace vůbec.
- **Notifikace se zámkem nemění.** Co ukazují, řídí volba náhledu v Nastavení → Notifikace;
  druhé tlačítko na totéž by se s ní jen přetahovalo.
- **Odhlášení zámek zahodí** (`AuthStage.LoggingOut` i `WaitPhoneNumber`). Zámek patří účtu:
  nový účet na tomtéž telefonu by jinak zdědil cizí PIN. Zároveň je to cesta ven pro toho, kdo
  PIN zapomněl — odhlásit se, přihlásit znovu.

## Záznam

Všechno, co zámek ví, je **jeden záznam v `AppLockStore`** (Keystore / Keychain, viz
`SecretStore`), ne v `AppSettings`: `v2;iterace;sůl;otisk;doba;biometrie;chyby;chybaV`.

- **`chybaV` je čas poslední chyby v hodinách od startu systému**, ne konec brzdy v čase na zdi
  (to byl formát `v1`). Záznam `v1` se dál čte — jinak by update aplikace zámek tiše vypnul
  (neznámý záznam = vypnutý zámek) —, počet chyb z něj zůstane a čas chyby se vezme jako 0
  (viz „Brzda pokusů" níž).

- **Jeden záznam = jeden zápis.** Zapnutí, doba, biometrie a počítadlo chyb se mění spolu, takže
  nemůže vzniknout stav „zámek zapnutý, ale PIN chybí". Záznam neexistuje = zámek je vypnutý.
- **Poškozený nebo neznámý záznam se zahodí a zámek je vypnutý.** Druhá možnost — zamknout
  natrvalo — by uživatele dostala ven jen odhlášením, a to kvůli chybě v aplikaci.
- **Otisk PINu je PBKDF2-HMAC-SHA256** (`PinHash.kt`, vlastní nad `hmacSha256` z Okio, ověřené
  vektory RFC 7914 v `PinHashTest`). Platformní implementace by znamenaly dvě kopie výpočtu, které
  se musí shodovat bajt po bajtu. Čtyřmístný PIN má deset tisíc možností a pomalý výpočet z něj
  silné heslo neudělá — hlavní ochrana je šifrování záznamu klíčem z Keystore a brzda pokusů;
  iterace (10 000) jsou pojistka pro případ, že by ven unikl záznam i s klíčem.
- **Porovnání otisků je bez předčasného konce** (`constantTimeEquals`).

## Kdy se zamyká

- **Studený start se zapnutým zámkem začíná zamčený**, v pozadí se zamkne po zvolené době
  (1 min, 5 min, 1 h, 5 h). **„Hned" není**: zamkl by aplikaci každý výběr fotky nebo souboru,
  protože systémový výběr ji pošle do pozadí.
- **Hodiny zámku jsou čas od startu systému VČETNĚ spánku** (`LockClock.elapsedMillis`:
  Android `SystemClock.elapsedRealtime`, iOS `CLOCK_MONOTONIC`, který na Darwinu ve spánku
  běží). Přestavit nejdou, jen je vynuluje restart. `TimeSource.Monotonic` to NENÍ: na Androidu
  je to `nanoTime` a ve spánku stojí — telefon, který ležel přes noc, by hlásil „pár vteřin".
- **Doba v pozadí** (`shouldLockAfter`) bere delší z času od startu a času na zdi. Posunutí
  hodin dopředu tak zamkne jen dřív, posunutí dozadu nic nezmění.
- **Brzda pokusů:** pět chyb projde, pak 30 s a každá další chyba dvojnásobek, nejvýš hodina
  (`blockFor`). **Počítadlo je v záznamu a přežije restart**, jinak by brzdu obešlo zabití
  aplikace.
- **Brzda se měří JEN časem od startu systému** (`blockRemaining`). Dřív to byl konec brzdy
  v čase na zdi a ošetřené bylo jen přestavení dozadu: kdo drží odemčený telefon, vypnul
  automatický čas, posunul hodiny o hodinu dopředu a měl další pokus. 10 000 PINů se tak dalo
  projít za desítky hodin klikání místo zhruba 200 dní.
- **Restart se pozná podle toho, že je čas od startu menší než při poslední chybě.** Kolik času
  uběhlo před restartem, nevíme, jistě ale aspoň čas od startu; počítá se tedy, jako by chyba
  přišla těsně před ním. Restart brzdu nezruší a nikdy ji neprotáhne přes jmenovitou délku.
  Stejně se čte záznam `v1` (čas chyby 0).
- **Ověření PINu v Nastavení počítá do brzdy taky** (`verify`). Jinak by se PIN dal hádat přes
  Nastavení odemčené aplikace — a odemčenou ji může držet kdokoli, komu ji majitel půjčil.
- **Biometrie odemkne i během brzdy a brzdu zruší.** Otisk hádáním nikdo neobejde a kdo PIN
  zapomněl, dostal by se jinak dovnitř jen odhlášením.

## Obrazovka zámku a UI

- **Zámek má VLASTNÍ OKNO** (`LockWindow`: Android `Dialog`, iOS vrstva dialogu). Vrstva
  v hlavním okně nestačila: `Dialog`, `ModalBottomSheet` i `Popup` mají vlastní okno nad hlavním,
  takže sheet otevřený před odchodem do pozadí ležel po návratu NAD zámkem a dal se ovládat
  (výběr chatu pro přeposlání, přílohy). Okno zámku vzniká až při zamčení, je tedy nad nimi.
  Na Androidu má `SecureFlagPolicy.SecureOn` (PIN nejde do snímku) a vlastní vzhled ikon
  systémových lišt — `SystemBarsAppearance` řídí jen okno activity.
- **Pod oknem zůstává kryt v hlavním okně** (`Locked` i `Unknown`): okno zámku se ukáže až
  s animací a během prvního čtení záznamu žádné není. Obsah se tak nemihne.
  **Cizí okna (sheet, popup) kryt nezakryje** — mezi zamčením a objevením okna zámku jsou nad
  ním vidět. Na zařízení jde o pár snímků; na přetíženém emulátoru to byly až dvě vteřiny.
- **Obsah pod zámkem je `Inert`** (`App.kt`): `clearAndSetSemantics {}` a skupina fokusu, do
  které fokus nevstoupí, plus `clearFocus` při zamčení. Zůstává totiž složený (jinak by se
  ztratil stav navigace a rozepsané zprávy) a bez toho ho četla čtečka obrazovky a Tab na
  fyzické klávesnici do něj přesunul fokus.
- **Zpět minimalizuje aplikaci** (`rememberReturnToCaller`, přes `onDismissRequest` okna
  zámku). Obejít zámek se Zpět nesmí.
- **Zámek se nastaví ještě v `onStart`**: `AppLock` sleduje popředí na
  `Dispatchers.Main.immediate`, takže změna stavu proběhne v tomtéž volání, dřív než se
  vykreslí první snímek. Na `Dispatchers.Default` se mohl stihnout vykreslit starý obsah.
- **Klávesnice PINu (`PinPad`):** číselník 3x4, tlačítko smazat a biometrická ikona (pokud je
  zapnutá). Haptiku dělá podle slovníku v `core/ui/Haptics.kt` (`confirm` / `reject`).
  - **Material 3 Expressive tvar a morphing:** Tlačítka (`PinDigitButton` s `FilledTonalIconButton`, `PinIconButton` s `IconButton`)
    používají standardní `IconButtonDefaults.shapes()`, které při stisku přirozeně animují změnu tvaru
    z kruhu na squircle (zaoblený čtverec) a zpět, v plném souladu se zbytkem projektu i M3 Expressive specifikací.
  - **Animované puntíky (`PinDots`):** Při zadání číslice se prázdný kruhový kroužek plynule morphne
    do vyplněného čtyřlístku (`MaterialShapes.Clover4Leaf`) se zvětšením (scale pop 1.25f s pružinou)
    a rotací o 45°. Při chybě se tečky zatřesou (`Animatable` offset).
- **Odpočet brzdy:** při zablokování po opakovaných chybách se číselník skryje a zobrazí se
  odpočet do vypršení brzdy v reálném čase.

## Biometrie (`BiometricAuth`)

- **Platformní rozhraní `BiometricAuth`:**
  - Android: **jen silná biometrie** (`BIOMETRIC_STRONG`, v promptu `setAllowedAuthenticators`).
    Dřív se brala i slabá (třída 2) — typicky obličej z přední kamery, který na některých
    telefonech oklame fotka. Systémový zámek obrazovky (`DEVICE_CREDENTIAL`) se nenabízí: zámek
    aplikace má být druhá bariéra, ne kopie té první. Android 10 `canAuthenticate(Int)` nemá
    (dřív na tom tiše padalo `runCatching` a biometrie tam nebyla nikdy); tam se volá
    `canAuthenticate()` a prompt podle CDD bere silnou biometrii sám.
  - iOS: `LocalAuthentication` (`LAContext.evaluatePolicy(LAPolicyDeviceOwnerAuthenticationWithBiometrics)`).
    **Face ID potřebuje `NSFaceIDUsageDescription`** — je v build settings projektu
    (`INFOPLIST_KEY_NSFaceIDUsageDescription`, jako ostatní usage texty), ne v `Info.plist`.
    Bez něj ověření na iPhonu s Face ID selže.
- **Biometrie NENÍ svázaná se seznamem otisků v telefonu.** Kdo zná PIN telefonu, přidá si
  vlastní otisk a aplikaci odemkne. Oprava (klíč s `setInvalidatedByBiometricEnrollment`
  přes `CryptoObject`, na iOS `evaluatedPolicyDomainState`) je otevřená — viz roadmapa.
- **Automatické vyvolání při zamčení:** jakmile se objeví `LockScreen` a biometrie je zapnutá,
  systémový dialog se nabídne automaticky, aniž by uživatel musel ťukat na ikonu otisku.

## Ochrana v přehledu úloh (`FLAG_SECURE`, iOS kryt)

- Obsah aplikace v systémovém přepínači úloh (recent apps) i na snímcích obrazovky je chráněn
  přes `SecureScreen(enabled = lockSettings != null)` v `App.kt`.
- **Chráněno pouze tehdy, když je zámek zapnutý.** Pokud uživatel zámek nepoužívá, `FLAG_SECURE`
  je vypnutý, takže snímky obrazovky a náhled v multitaskingu zůstávají povolené.
- **Žádosti o `FLAG_SECURE` se počítají** (`SecureScreen.android.kt`). Příznak je na okně
  jeden, ale žádá o něj zámek i prohlížeč mizejících médií — a prohlížeč ho při zavření
  shazoval natvrdo `clearFlags`, takže po zavření mizející fotky přehled úloh zase ukazoval
  chaty, i když byl zámek zapnutý.
- **iOS `FLAG_SECURE` nemá a systém si okno fotí po `didEnterBackground`**; zámek naskočí až po
  `didBecomeActive`, takže na snímku v přepínači byla otevřená konverzace. `PrivacyCover` proto
  při `willResignActive` přidá nad okno nativní `UIView` v barvě pozadí (Compose v té chvíli
  kreslit nemusí) a při `didBecomeActive` ho sundá. Ukáže se i při Ovládacím centru a dialogu
  Face ID; Telegram s kódem to má stejně.

## Popředí na Androidu (`AppForeground`)

- **Stav popředí se počítá od vzniku objektu, ne od odběru.** Callbacky activit se dřív
  registrovaly až s odběrem a počítadlo začínalo na nule. Zámek se přihlásí až po přečtení
  záznamu z Keystore, tedy často až po `onStart`: počítadlo pak při odchodu do pozadí spadlo
  na −1, „v pozadí" se neohlásilo a **zámek se po návratu nezamkl nikdy**. Dokud zámek sledoval
  popředí na `Dispatchers.Default`, většinou to vyšlo; po přesunu na hlavní vlákno (viz výš)
  to prohrál pokaždé — tak se na to přišlo.
- Teď je stav jeden pro celý proces (`MutableStateFlow`), výchozí hodnota se čte ze systému
  (`getMyMemoryState`, jen `IMPORTANCE_FOREGROUND` — „visible" zahrnuje i službu v popředí bez
  activity) a počítadlo pod nulu nejde.

## Nastavení zámku (`AppLockScreen`)

- Umístění: **Nastavení → Soukromí → Zámek aplikace**.
- Pokud je zámek již zapnutý, vstup do obrazovky vyžaduje ověření stávajícího PINu nebo biometrií.
- Umožňuje:
  - Zapnutí zámku se zadáním a potvrzením PINu (kontrola shody, chyba při neshodě).
  - Změnu PINu (kroky zadat nový → potvrdit nový).
  - Volbu doby neaktivity (1 min, 5 min, 1 h, 5 h).
  - Zapnutí / vypnutí odemykání biometrií (pokud je na zařízení dostupná).
  - Vypnutí zámku s potvrzovacím dialogem.

## Poučení z testování

- **`adb exec-out screencap` je se zapnutým zámkem černý** (`FLAG_SECURE`). Snímek z displeje
  emulátoru ho obchází: `adb emu screenrecord screenshot <adresář>`. Pořadí oken (je zámek nad
  sheetem?) ukáže `adb shell dumpsys window windows | grep "^  Window #"`, nahoře je první;
  okno zámku je dialog a vypisuje se pod jménem activity.
- **Zámek po návratu z pozadí se ověřuje skutečnou minutou čekání** (`input keyevent 3`,
  `sleep 65`, `am start`). Kratší doba není a přestavit hodiny bez rootu nejde.

- `AppLock` provádí PBKDF2 hash přes `withContext(hashDispatcher)`. V produkci je to `Dispatchers.Default`,
  ale v deterministických unit testech s `StandardTestDispatcher` je nutné umožnit předání testovacího
  dispečeru, jinak na pozadí vznikají race conditions.
- Testovací coroutine ve `scope.backgroundScope` (např. sledování popředí) se neodbavují přes
  `advanceUntilIdle()` samovolně, pokud jsou v nekonečném `collect`; pro jednorázové odbavení
  naplánovaných úloh na testovacím scheduleru je nutné použít `runCurrent()`.

## Stav

Hotovo včetně UI, biometrie na Androidu i iOS, `FLAG_SECURE`, krytu v přepínači na iOS,
obrazovky v Nastavení a unit testů (`AppLockTest`, `PinHashTest`, `AppLockViewModelTest`).
Otevřené: svázání biometrie se seznamem otisků (viz výš), volitelný šestimístný PIN.
