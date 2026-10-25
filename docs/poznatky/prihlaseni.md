# Přihlášení a odhlášení

Poznatky k jedné oblasti aplikace. Obecná pravidla projektu a rejstřík ostatních souborů jsou
v [CLAUDE.md](../../CLAUDE.md); poučení z opravy v téhle oblasti patří sem.

## Přihlášení QR kódem

- **Hodinky se dají přihlásit i bez skenování:** pošlou odkaz z QR kódu telefonu přes Wearable
  Data Layer a ten ho po potvrzení v notifikaci potvrdí stejně jako naskenovaný
  (`docs/poznatky/wear-os.md`, „Přihlášení telefonem").
- **QR se generuje na platformě** (`encodeQrCode`): Android přes zxing, iOS přes CoreImage
  (`CIQRCodeGenerator`). Kodér v commonMain není. Platforma vrací jen mřížku modulů,
  kreslí se společně na `Canvas` — ostré v každé velikosti a nic se neškáluje z bitmapy.
- **Natvrdo černá na bílé, i v tmavém režimu.** Čtečky počítají s tmavými moduly na světlém
  podkladu; inverzní kód spousta z nich nepřečte. Stejná výjimka z rolí `colorScheme` jako
  bílé ovládání v prohlížeči médií.
- **Modul musí mít celé pixely.** Se zlomkovou velikostí nechá vyhlazování hran mezi sousedními
  moduly vlasové spáry; zbytek šířky se rozdělí do okrajů.
- **Odkaz se obnovuje.** TDLib posílá nový `AuthorizationStateWaitOtherDeviceConfirmation`
  zhruba každou půlminutu; kód se přepočítá přes `remember(content)`.
- **Z QR režimu se přes zadání čísla vrátit NEJDE** — je to smlouva TDLib, ne naše chyba.
  `setAuthenticationPhoneNumber` i `requestQrCodeAuthentication` vyjmenovávají stavy, ve kterých
  se smí volat, a `authorizationStateWaitOtherDeviceConfirmation` v žádném z těch seznamů není;
  berou ho jen `checkAuthenticationPasskey` a `checkAuthenticationWebToken` („for official
  Telegram apps only").
- **Jediné dveře ven jsou `destroy()`** (`TelegramSession.restartLogin`). Zavře klienta,
  `onClientClosed` vyrobí nového a ten dojde k zadání čísla. **Ne `logOut`**: ten chce funkční
  připojení a je to „řádné odhlášení" relace, která ještě neexistuje — a právě spojení nemusí
  mít ten, kdo se z QR vrací. `destroy` smí být volaný před autorizací a funguje offline.
  Bez tlačítka „Přihlásit se číslem" je QR slepá ulička: kdo nemá čím kód naskenovat, musel by
  aplikaci odinstalovat.
- **Zavírání klienta se v téhle situaci NEHLÁSÍ jako odhlašování** (`restartingLogin`,
  `closingStage()`). „Odhlašuji" by lhalo — uživatel přihlášený nikdy nebyl.

## Přihlašovací obrazovka

Jedna obrazovka pro všechny fáze (`AuthScreen`), fáze se střídají v `AnimatedContent`.

- **Uvítací tvar stojí NAD `AnimatedContent`, ne uvnitř.** Uvnitř by se jeho úvodní animace
  pouštěla při každém kroku přihlášení a z uvítání by byl tik. Takhle naběhne jednou a pak jen
  mění ikonu podle fáze.
- **Je to tentýž tvar jako u prázdného stavu** — `IntroShape` v `core/ui`, vytažený z
  `IntroEmptyState`. „Vítej v aplikaci" a „tady zatím nic není" jsou dvě podoby téhož okamžiku;
  dva různé vzhledy by jazyk tvarů rozmělnily (viz `ShapeMorphs`).
- **Fáze přijíždějí zdola a odjíždějí nahoru.** Přihlášení jde dopředu, tak jde i pohyb jedním
  směrem; prolnutí samo by nedalo najevo, že se postoupilo o krok.
- **Tvar se při změně kroku proanimuje znovu** (`replayKey`). Nad měnícím se obsahem by jinak
  stál jako obrázek, a přitom je to jediné, co drží souvislost mezi kroky.
- **Kroky pro tvar jsou hrubší než `AuthStage`** (`AuthPhase`). Startovní `Connecting` patří
  k zadání čísla, protože k němu automat míří — jinak by se tvar hned po náběhu proanimoval
  podruhé a vypadalo by to jako zadrhnutí. Kód a heslo jsou taky jeden krok: mezi nimi se
  nepřechází tam a zpět.
- **Text polí je Compose stav ve ViewModelu** (`AuthViewModel.input`, `hint`, `phone`), ne
  `StateFlow` — stejně jako u úpravy profilu. Hodnota ze `StateFlow` se do pole dostane až
  dalším průchodem collectoru a znak napsaný mezitím by pole překreslilo se starou hodnotou.
  **Na zařízení se tím ztráta znaků nepozorovala; je to prevence známé pasti, ne oprava.**
- **Ztracené znaky po `adb shell input text` na emulátoru zahodil SYSTÉM, ne aplikace.**
  Roadmapa vedla „rychlý `input text` přeháže znaky"; měřením (8. 10. 2026, starý i nový build)
  chyběl vždy jen KONEC textu, nikdy prostředek, a v logu bylo `InputDispatcher: Dropped event
  because it is stale` — přesně dvakrát tolik, kolik chybělo znaků (stisk a puštění). `input
  text` dá všem znakům časové razítko na začátku a dispečer zahazuje vložené události starší
  než 10 s; přetížený emulátor (1 s na znak) konec fronty nestihne. Poznáš to podle té hlášky;
  pomůže psát po třech čtyřech znacích (`for c in 777 123 456; do input text $c; done`).

### Zadání kódu

Číselný kód se píše do políček — jedno na číslici, počet podle toho, co hlásí server
(`AuthCode`, `CodeField`). Hotový kód se odešle sám.

- **Kódy nejsou pořád stejně dlouhé.** Z Telegramu chodí pětimístný, SMS a Fragment posílají
  i šestimístné. Hádat se to ale nemusí: `AuthenticationCodeInfo.type` nese `length` u každého
  přihlášení zvlášť, takže se nakreslí přesně tolik políček, kolik jich bude potřeba.
- **A nemusí to být ani číslo.** `SmsWord` (jedno slovo), `SmsPhrase` (fráze) a `FlashCall`
  (kód se čte z čísla volajícího) délku nemají; tam zůstane obyčejné pole s písmenkovou
  klávesnicí. `when` nad `AuthenticationCodeType` je bez `else` — je sealed, takže nový způsob
  doručení v novější TDLib rozbije překlad, místo aby tiše spadl na obyčejné pole.
- **Délka od serveru se bere jen v rozumném rozsahu** (`CODE_DIGITS`). Nula by znamenala nula
  políček, tedy pole, do kterého se nedá psát; mimo rozsah se spadne na obyčejné pole, které
  funguje vždycky.
- **Uvnitř je JEDNO pole, políčka jsou dekorace** (`decorationBox`), ne N samostatných
  `TextField`ů. Řada polí znamená přehazovat fokus po každém úhozu, ručně řešit backspace přes
  hranici políčka a hlavně přijít o vložení ze schránky i o autofill — celý kód by spadl do
  prvního políčka.
- **`innerTextField()` se MUSÍ složit, i když není vidět.** Nese rozvržení textu, bez kterého
  pole nefunguje; je proto průhledné (barva textu i kurzoru) a leží pod políčky. Gesta a fokus
  má na sobě kořen dekorace, ne vnitřní pole, takže ťuknutí kamkoli na řadu políček zabere.
- **Číslice čte čtečka obrazovky z POLE, ne z políček** (`clearAndSetSemantics` na řadě).
  Semantika pole je merging, takže bez toho by se do jeho popisu slily všechny `Text`y.
- **Zaoblení je v procentech kratší strany, ne v dp.** Pevných 16 dp ukrojí z políčka širokého
  45 dp (šest číslic na telefonu širokém 360 dp) skoro celou hranu a zbude ovál — ověřeno na
  emulátoru zmenšeném na 720 × 1500. Z téhož důvodu klesá i výška (`codeCellHeight`, testy
  `CodeEntryTest`): se stálými 64 dp z políček byly vysoké úzké pilulky.
- **Hotový kód se odesílá sám, ale tlačítko zůstává.** Poslední číslice říká „jdeme na to" líp
  než další ťuknutí. Bez tlačítka by ale po chybě, která s kódem nesouvisí (spadlé spojení),
  nešlo poslat TENTÝŽ kód znovu: text se nezměnil, takže se odeslání samo už nespustí.
- **Věta nad polem říká, kudy kód opravdu přišel** (`CodeDelivery`). Jedna věta pro všechno
  („dorazil do jiného klienta nebo SMS") byla odhad — a přitom to TDLib pokaždé řekne.
- **Fokus si pole bere samo.** Zadat kód je jediná věc, kterou na téhle obrazovce jde udělat.
  Výjimka z pravidla „po ťuknutí na Upravit se pole samo nefokusuje": tam je psaní vsuvka,
  tady je to celá obrazovka.
- **Autocorrect je vypnutý u kódu i u hesla.** Slovní kód by klávesnice opravila na jiné slovo
  a uživatel by nepoznal proč.
- **Ověřuje se podstrčeným stavem, ne odhlášením.** Na přihlašovací obrazovku se jinak nedá
  bez ztráty relace; v `AuthViewModel` se dočasně nahradí `stage` za
  `MutableStateFlow(AuthStage.WaitCode(AuthCode(5, CodeDelivery.TelegramApp)))`, postaví se APK
  a zdroják se hned vrátí — stejně jako u karty aktualizace. Odeslání kódu na přihlášené relaci
  skončí chybou od serveru, takže se tou samou cestou ověří i chybový stav.
- **Ověřeno na emulátoru**: pět i šest políček, doplňování zleva, rámeček u aktivního políčka,
  vložení delšího textu se ořízne, plný kód se odešle sám a chyba obarví políčka i větu pod nimi;
  slovní kód dostane obyčejné pole s písmenkovou klávesnicí a neodesílá se sám. **Autofill kódu
  z SMS ověřený NENÍ** — je to jen deklarace `ContentType.SmsOtpCode`, doručit skutečnou SMS na
  emulátor nešlo.

### Kód jinou cestou (SMS, hovor)

Když TDLib pošle kód do aplikace Telegram (`AuthenticationCodeTypeTelegramMessage`), dostane ho
jen ten, kdo má po ruce jiné přihlášené zařízení. Ztracený telefon nebo recyklované číslo
znamenaly slepou uličku — kód nikdy nepřišel a jiná cesta nebyla.

- **Kudy jinak, říká server, ne my.** `AuthenticationCodeInfo.nextType` je cesta, kterou kód
  půjde po `resendAuthenticationCode`, a `null` znamená, že jiná není. Tlačítko se kreslí jen
  s nenulovým `nextType` (`AuthStage.WaitCode.next`); jinak by slibovalo cestu, která skončí
  chybou. Popisek podle cesty: SMS (i slovo a fráze, to je pořád SMS), hovor (i prozvonění),
  Telegram, jinak obecně.
- **`timeout` je ZBÝVAJÍCÍ čas, ne původní lhůta** — TDLib ho při sestavení stavu přepočítá
  od uloženého okamžiku. Odpočítává se proto od příchodu stavu (`AuthViewModel.resendIn`,
  `collectLatest`, takže nový stav rozjetý odpočet zruší). Během lhůty tlačítko zůstává vidět
  s odpočtem („Poslat kód SMS za 0:42"), aby uživatel věděl, že jiná možnost přijde, a nezkoušel
  zpět a znovu. Před koncem lhůty se nic neposílá — TDLib by to odmítl a místo odpočtu by byla
  chyba.
- **Signatura se v TDLib mění**: novější verze přidala `resendAuthenticationCode(reason)`
  (`ResendCodeReasonUserRequest`, nebo `…VerificationFailed` po selhání Firebase). Wrapper 15.0.0
  ho má nullable s výchozím `null`; posíláme `UserRequest`, protože to víme.
- **Kudy kód odešel doopravdy, přijde novým stavem** `AuthorizationStateWaitCode` (jiný `type`,
  nový `nextType` a `timeout`). Typický řetěz je Telegram → SMS → hovor; po hovoru už `nextType`
  bývá `null`. Nový stav je jiný klíč `AnimatedContent`, takže formulář přijede znovu — kód
  je opravdu nový a může mít i jinou délku. Rozepsaný kód se po úspěchu maže.
- **Chyby**: TDLib sama odmítne `"Authentication code can't be resend"` (ověřeno `strings
  libtdjsonjava.so`), server `SEND_CODE_UNAVAILABLE` (dokumentace `auth.resendCode`); obojí má
  vlastní větu, zbytek spadne na „Kód se nepodařilo poslat jinak". `PHONE_CODE_EXPIRED` už
  větou „nech si poslat nový" neslibuje nemožné.
- **Registrace (`registerUser`) s tím nesouvisí.** Plume nové účty vědomě nezakládá
  (`AuthStage.WaitRegistration` odkazuje na oficiální klient); poslat kód jinak je jen
  doručení kódu k EXISTUJÍCÍMU přihlášení.
- **Našemu `api_id` server jinou cestu NENABÍZÍ** (zkoušeno 9. 10. 2026 na emulátoru, české
  číslo s přihlášeným Telegramem): `type=TelegramMessage, next=null, timeout=0`, takže se
  tlačítko správně nekreslí. Stejně to dopadlo se všemi kombinacemi
  `PhoneNumberAuthenticationSettings`, které jdou bez oficiální aplikace — výchozí (`null`),
  `allowMissedCall` + `allowSmsRetrieverApi`, k tomu `allowFlashCall` + `hasUnknownPhoneNumber`,
  i s `FirebaseAuthenticationSettingsAndroid()`. Firebase SMS je podle dokumentace TDLib jen
  „to the official Android application" (token z Play Integrity ověřuje Telegram pro svůj
  projekt), tudy cesta nevede. Nastavení proto zůstává výchozí; přidávat příznaky, které nic
  nezměnily, by jen slibovalo něco, co nefunguje.
- **Omezení je na `api_id`, ne na čísle.** Totéž číslo v oficiální aplikaci pro Android (týž
  den) dostalo ověření hovorem („Don't accept the call…", tedy prozvonění, které aplikace
  pozná z protokolu hovorů) a jako další cestu SMS s lhůtou hodina („You can request an SMS
  in 59:50"). Plume s povoleným `allowFlashCall` i `allowMissedCall` dostal jen kód do
  Telegramu bez další cesty — server tedy jiné cesty dává podle toho, čí je `api_id`.
  Hovor nakonec nepřišel (ani zmeškaný); po zrušení a NOVÉM přihlášení byla lhůta na SMS jen
  dvě minuty a přihlášení přes SMS prošlo. Lhůtu (`timeout`) tedy určuje server pokaždé zvlášť
  a hodina u prvního pokusu nebyla pravidlo.
- **Úplně nové číslo nedostane SMS VŮBEC, ani v oficiální aplikaci** (11. 10. 2026, Pixel 7 Pro,
  čerstvě aktivovaná SIM T-Mobile CZ, mobilní data vypnutá, přes Wi-Fi). Plume (debug build)
  dostal tutéž odpověď jako u čísla s existujícím účtem: `type=TelegramMessage(length=45),
  next=null, timeout=0` — „kód je v Telegramu na jiném zařízení“, žádná jiná cesta. U nového
  čísla tím kód nedorazí nikam a z obrazovky kódu se nedá pohnout. Oficiální Telegram
  (`org.telegram.messenger` z Play, týž telefon, týž okamžik, povolené hovory i protokol
  hovorů) nedostal SMS ani prozvonění, ale obrazovku „Choose a login email“: kódy budou chodit
  e-mailem místo SMS, zadej e-mail nebo „Sign in with Google“ (`auth.sentCodeTypeSetUpEmailRequired`,
  v TDLib `AuthorizationStateWaitEmailAddress`). **Chybějící SMS tedy neškrtí Plume, ale
  Telegram** — a našemu `api_id` neřekne ani to, že chce e-mail. Postup pro nové číslo: účet
  založit v oficiální aplikaci (e-mail nebo Google), pak se přihlásit do Plume; kód přijde do
  oficiální aplikace jako zpráva. Kdyby server Plume e-mail nabídl, zvládne ho (viz
  „Přihlašovací e-mail" níž); registraci nového účtu ale Plume nedělá, takže nové číslo po
  e-mailu stejně skončí odkazem na oficiální aplikaci.
  Jestli na čísle nevisí účet po předchozím majiteli, se nedotáhlo (rozhodlo by dokončení
  v oficiální aplikaci: nový účet se ptá na jméno); žádost o e-mail ukazuje spíš na nový účet.
- **Ani založený účet bez aktivní relace to nezmění** (týž den, o pár minut později). Uživatel
  se v oficiální aplikaci zaregistroval (kód e-mailem, ověření prozvoněním z +1 412…)
  a odhlásil; Plume s vyčištěnými daty dostal znovu `TelegramMessage(length=45), next=null,
  timeout=0` a během minuty nepřišel e-mail, SMS ani hovor. Kód tedy leží v servisním chatu
  účtu, který nikdo nemá otevřený. Plume se na číslo bez jiné přihlášené relace nedostane;
  napřed je potřeba přihlásit oficiální aplikaci (tam server nabídne e-mail) a pak Plume
  kódem ze zprávy, nebo rovnou QR kódem.
- **Obrazovka kódu to proto říká sama** (`CodeForm`, `appOnly`): když kód jde jen do Telegramu
  a `next` je `null`, stojí pod tlačítkem věta „Nemáš Telegram přihlášený na jiném zařízení?…“
  s odkazem na oficiální aplikaci a web a vedle „Jiné číslo“ tlačítko „Přihlásit se QR kódem“.
  Dřív uživatel s novým číslem čekal na kód, který nikdy nepřijde, a nedozvěděl se proč.
  **QR jde spustit přímo z čekání na kód** — `requestQrCodeAuthentication` TDLib pustí
  i z `authorizationStateWaitCode`, když neběží jiný dotaz; ověřeno na Pixelu 7 Pro
  (`WaitCode` → `WaitOtherDeviceConfirmation`, QR se nakreslil). Druhé zařízení je potřeba
  tak jako tak a QR je z něj rychlejší než opisovat kód ze zprávy.
- **Tlačítko tedy čeká na server, který jinou cestu dá** (jiné `api_id`, jiná země, účet bez
  aktivní relace). Ověřené je jen testy `AuthViewModelTest` (lhůta, poslání, chyby), skutečné
  doručení SMS či hovoru ne.
- **Co server nabídl, vypisuje debug build do logu**: `adb logcat | grep "Kód:"`. Jen v debugu
  (`TelegramPaths.isDebugBuild`, z `FLAG_DEBUGGABLE` aplikace — knihovna `:client:shared` se
  staví stejně pro oba typy), protože release logcat zůstává zapnutý.
- **`length` u kódu do Telegramu přišla 45**, přestože kód v chatu Telegram měl pět číslic
  (u jiného pokusu na telefonu přišla délka 5 a kreslilo se pět políček). Mimo `CODE_DIGITS`
  se spadne na obyčejné pole, takže se dál přihlásit dá; proč server poslal 45, se nezjistilo.
  **Při pokusu s novým číslem (11. 10. 2026) to nesedělo**: log hlásil `length=45` (jediný
  řádek `Kód:`, `toString` DTO vypisuje `length` přímo), a přesto se nakreslilo pět políček
  s číselnou klávesnicí — podle `digitsOrNull` mělo být obyčejné pole. Nevysvětlené; kdo
  to bude řešit, ať si napřed ověří, jaké `AuthStage.WaitCode.code` doopravdy dorazí do UI.
- **Zkoušet přihlášení bez odhlášení emulátoru jde v DRUHÉM UŽIVATELI Androidu**: `pm
  create-user`, `pm install-existing --user N eu.tintera.plume`, `am switch-user N`, mezi pokusy
  `pm clear --user N`. Dvě pasti: snímek obrazovky je v druhém uživateli celý černý (ovládá se
  přes `uiautomator dump`) a `input text` do pole nedojde — klávesy si bere visící klávesnice
  hlavního uživatele (`VRI[InputMethod]: Cancelling event (no window focus)`). Pomůže v tom
  uživateli klávesnici vypnout: `pm disable-user --user N com.google.android.inputmethod.latin`.

### Heslo dvoufázového ověření

Tónovaný blok jako číslo, s okem na odkrytí (`PasswordField`).

- **Odkrytí okem je to hlavní.** Cloud heslo se píše jednou za dlouho a naslepo; překlep se
  pozná až z hlášky „Heslo nesedí" a uživatel ani potom neví, co vlastně napsal. Ikona
  pojmenovává AKCI, ne stav: se skrytým heslem oko („zobrazit"), s odkrytým přeškrtnuté.
- **Fokus a chybu nese rámeček kolem celého bloku, ne podtržení `TextField`u.** Indikátor je
  široký jen jako pole, takže vedle oka končil v půlce karty a vypadal jako chyba vykreslení.
  Barvu dává `authOutlineColor` — jedno místo pro políčka kódu i pro heslo, dvě kopie téhož
  pravidla by se rozešly.
- **Plovoucí label je pryč, zůstala nápověda v poli.** „Heslo" nad polem jen opakovalo nadpis
  kroku — tatáž úvaha jako u dvojího slova „Země" v bloku s číslem.
- **`ContentType.Password`**, protože cloud heslo bývá ve správci hesel; z celého přihlášení
  se autofill hodí právě tady nejvíc.
- **Autocorrect vypnutý**, jinak klávesnice udělá z hesla jiné slovo.
- **Ověřeno na emulátoru** podstrčeným `AuthStage.WaitPassword` (na obrazovku se jinak nedá bez
  odhlášení): skryté i odkryté heslo, přepnutí ikony, rámeček při fokusu, červený rámeček i věta
  při chybě. Sonda se NECOMMITUJE. **Autofill ze správce hesel ověřený není** — je to jen
  deklarace.
- **Jeden snímek není důkaz.** Po sjednocení bloku s číslem vyšel na jednom snímku horní řádek
  karty tmavší než spodní (změřeno #DFDFE7 proti #F3F3FA) a vypadalo to jako šev, který přidal
  rámeček. Tři opakovaná čerstvá spuštění ukázala kartu jednolitou — byl to přechodný stav fokusu
  na klikacím řádku země, ne následek změny. **Ověřovací pokus, který má vyloučit regresi, se
  musí zopakovat**, jinak se z jednoho vzorku vyrobí neexistující chyba (a stejně snadno se jí
  dá přehlédnout skutečná).

### Obnova hesla a cesta zpátky k číslu

„Zapomenuté heslo?" pod heslem pošle kód na obnovovací e-mail, kód se ověří a pak se nastaví
nové heslo s nepovinnou nápovědou (`PasswordStep` v `AuthViewModel`, testy `AuthViewModelTest`).
Z kódu i hesla vede „Jiné číslo" a systémové zpět zpátky k číslu.

- **Kroky obnovy drží ViewModel, ne automat.** TDLib zná jediný stav `WaitPassword`; kód
  z e-mailu (`checkAuthenticationPasswordRecoveryCode`) i nové heslo
  (`recoverAuthenticationPassword`) se volají nad ním. Při odchodu z `WaitPassword` se
  rozdělaná obnova zahazuje.
- **Maska e-mailu přijde NOVÝM STAVEM.** `recoveryEmailAddressPattern` je prázdný, dokud kód
  neodešel; po `requestAuthenticationPasswordRecovery` pošle TDLib `WaitPassword` znovu, už s ní.
  Obsah proto animuje podle klíče (`AuthView.key`: u hesla jen krok), jinak by přijel podruhé
  ve chvíli, kdy ho uživatel začíná číst.
- **Kód se ověřuje zvlášť, dřív než nové heslo.** Překlep v kódu by se jinak ukázal až po
  vymyšlení hesla a uživatel by ho psal znovu. Zpět z nového hesla vrací kód vyplněný.
- **Bez obnovovacího e-mailu se nic neposílá**, jen dialog řekne, co zbývá: vzpomenout si,
  nebo účet resetovat v oficiální aplikaci. Server by na žádost odpověděl `PASSWORD_RECOVERY_NA`
  a z obecné chyby by uživatel nepoznal, co dál. Tlačítko je u každého účtu — schované by
  nechalo hledat cestu, která není.
- **Reset účtu (`deleteAccount` s prázdným heslem) tu NENÍ.** Maže účet u všech kontaktů,
  provede se po týdnu a na testovacím účtu se ověřit nedá. Viz `docs/roadmap.md`.
- **Heslo se neořezává, kód ano.** Dřív se `trim()` dělal u všech kroků, takže heslo s mezerou
  na konci nešlo zadat — a nové heslo nastavené odsud by rozbilo přihlášení jinde.
- **Nápověda shodná s heslem se odmítne hned** (server by to udělal taky, ale obecnou chybou).
- **Zpět k číslu je `restartLogin` (`destroy`), ne `setAuthenticationPhoneNumber`.** To TDLib
  v kódu i heslu pustí, jenže automat by dál hlásil kód a obrazovka čísla by existovala jen
  u nás. Dokud heslo nesedí, server přihlášení nedokončil (`SESSION_PASSWORD_NEEDED`), takže
  po `destroy` nezůstane viset relace.
- **Zpět je `NavigationBackHandler`**, jen v kódu, hesle, e-mailu a QR. Na čísle zpět aplikaci
  zavře jako dřív — před číslem nic není. Uvnitř obnovy jde zpět o krok obnovy.
- **Zpět s klávesnicí vidět napřed schová klávesnici** (`AuthScreen`, rozhoduje se až při zpět).
  Handler se zapíná až s novým krokem — kód přijde, když klávesnice z čísla ještě svítí —, takže
  se zaregistruje POZDĚJI než zpět klávesnice, a pozdější callback vyhrává. Zpět z kódu tak
  klávesnici přeskočilo a celé přihlášení restartovalo; ověřeno na emulátoru gestem i klávesou,
  týkalo se to kódu z SMS, hesla i e-mailu.
- **Handler se podle klávesnice NEVYPÍNÁ, jen se při zpět zeptá.** Inset klávesnice v Compose
  po gestu zpět občas zůstane viset: systém klávesnici hlásí skrytou (`dumpsys input_method`:
  `mInputShown=false`, inset `ime visible=false`), ale `WindowInsets.ime` drží výšku — obsah je
  posunutý nahoru a spodek obrazovky (tlačítka, proxy) chybí. Na emulátoru zhruba jednou
  z pěti tahů `input swipe 2 850 500 850 300`, s klávesou zpět (`keyevent 4`) ani jednou;
  spraví to další ukázání klávesnice. S handlerem vypnutým podle insetu šlo zpět v tu chvíli
  do aktivity a **zavřelo aplikaci**; takhle jen jednou nic neudělá. Totéž pravidlo jako
  u `PopupBackAfterKeyboard`. Jestli inset visí i na skutečném telefonu, ověřené není.
- **„Další" v poli nového hesla fokus přesouvá RUČNĚ** (`onNext` → `FocusRequester` nápovědy).
  Výchozí přesun skočil na tlačítko s okem a nápověda se psala naprázdno — ověřeno na emulátoru.
  Totéž čeká každé pole s ikonou, za kterým je další pole.
- **Kód z e-mailu je obyčejné pole, ne políčka** — délku TDLib neříká. `contentType` nemá:
  `SmsOtpCode` by autofill nabídl kód z SMS.
- **Ověřeno na emulátoru podstrčenou session** (testovací účet dvoufázové ověření nemá):
  delegující `TelegramSession` s `WaitPassword` a odpověďmi pro kód, obnovu a restart, zapojená
  jedním řádkem v `AuthViewModel`. **Nikdy ne přes skutečný `restartLogin`** — ten by přihlášený
  účet na emulátoru zavřel. Sonda se NECOMMITUJE; odstraňuje se tím jedním řádkem, ne
  `git checkout` souboru (ten vrátí i rozdělanou práci). **Skutečný kód z e-mailu a nastavení
  nového hesla ověřené NEJSOU** — chce to účet s heslem a obnovovacím e-mailem.
- **`adb input text` do pole nad `StateFlow` na pomalém emulátoru přeháže znaky** („abc" →
  „bca"); po znaku s pauzou vyjde správně. Pole přihlášení jedou přes `StateFlow` odjakživa
  (proti tomu varuje „Úprava profilu"); na zařízení se to zatím neprojevilo, ale při rychlém
  psaní na slabém telefonu by mohlo.

### Přihlašovací e-mail

Telegram chce u části přihlášení e-mail, na který posílá kódy místo SMS: u nového čísla
a u účtu, který se přihlašuje často (`auth.sentCodeTypeSetUpEmailRequired`, v TDLib
`AuthorizationStateWaitEmailAddress`). Účet s přihlašovacím e-mailem nastaveným dřív dostane
kód rovnou na něj (`AuthorizationStateWaitEmailCode`). Dřív obojí končilo hláškou „tohle
neumíme". Kroky jsou `AuthStage.WaitEmailAddress` a `WaitEmailCode` (testy `AuthViewModelTest`).

- **Jak se TDLib v těch stavech chová, se četlo ze zdroje** (`td/telegram/AuthManager.cpp`),
  dokumentace to neříká:
  - `setAuthenticationEmailAddress` jde i z `WaitEmailCode`, když neběží jiný dotaz — tudy
    vede **oprava překlepu v adrese** (krok „Jiný e-mail", drží ho ViewModel jako obnovu hesla).
  - `resendAuthenticationCode` ve `WaitEmailCode` pošle kód **znovu na zadanou adresu**.
    U přihlašovacího e-mailu nastaveného dřív žádnou adresu nemá (server posílá jen masku),
    takže oprava ani nové poslání se tam nenabízí (`WaitEmailCode.isSetup`).
  - **Stav TDLib ty dva případy nerozliší.** `TdlTelegramSession` si pamatuje, že kódu
    předcházel `WaitEmailAddress` (`emailSetup`); jen v paměti, takže po restartu procesu
    uprostřed kroku se oprava adresy nenabídne. Kód i „Jiné číslo" zůstanou.
  - Maska, kterou server nepošle, je u TDLib doslovně `<unknown>` — bere se jako žádná.
- **Reset přihlašovacího e-mailu** pro toho, kdo k němu ztratil přístup („Nemáš přístup
  k e-mailu?", s potvrzením). `EmailAddressResetStateAvailable(waitPeriod)` → žádost rozjede
  čekání, typicky týden; `…Pending(resetIn)` → odpočet; po něm „Přihlásit se kódem na telefon"
  a kód přijde na číslo (`WaitCode`).
  - **`TASK_ALREADY_EXISTS` po první žádosti je úspěch.** Server tak odpoví, když čekání rozjel;
    TDLib to pozná, pošle nový stav s `Pending` — a chybu vrátí volajícímu taky. Session ji proto
    u žádosti z `Available` spolkne, jinak by vedle odpočtu stála chybová věta. U `Pending` je to
    opravdu „ještě ne" (`error_email_reset_pending`).
  - `resetIn` TDLib průběžně neposílá, odpočítává ViewModel (`emailResetIn`) jako lhůtu u kódu.
  - Během čekání se reset neposílá; tlačítko přijde až s nulou.
- **Google a Apple tu nejsou**, přestože je TDLib nabízí (`allowGoogleId`, `allowAppleId`).
  Token musí vydat Google či Apple pro OAuth klienta Telegramu, a ten je svázaný s podpisem
  oficiální aplikace.
- **Hrubé síto adresy je to z dvoufázového ověření** (`isPlausibleEmail`), chyby serveru
  `EMAIL_INVALID`, `EMAIL_NOT_ALLOWED`, `CODE_INVALID` a `EMAIL_VERIFY_EXPIRED` mají vlastní
  věty (dokumentace `account.sendVerifyEmailCode`, `account.verifyEmail`, `auth.resetLoginEmail`).
- **Kód z e-mailu má políčka, když server řekne délku** (`EmailAddressAuthenticationCodeInfo.length`,
  0 = neznámá → obyčejné pole), a odesílá se sám jako kód z SMS.
- **Ověřeno na emulátoru podstrčenou session** (delegující `TelegramSession` zapojená jedním
  řádkem v `AuthViewModel`, NECOMMITUJE se): zadání adresy, hrubé síto, kód na adresu s opravou
  a novým posláním, špatný kód, kód na dřív nastavený e-mail s nabídkou resetu, potvrzovací
  dialog, odpočet, hotový reset, pole bez délky kódu, zpět. **Skutečný server ověřený NENÍ** —
  testovací účet e-mail nechce a našemu `api_id` ho server zatím nenabídl ani u nového čísla
  (viz „Kód jinou cestou"). Až se to stane, v debug logu je `Kód e-mailem:`.

### Ověření aplikace (Play Integrity, APNs, reCAPTCHA)

Server může před dokončením dotazu chtít doklad, že ho posílá oficiální aplikace: na libovolný
dotaz odpoví 403 `INTEGRITY_CHECK_CLASSIC_…` (Android), `APNS_VERIFY_CHECK_…` (iOS) nebo
`RECAPTCHA_CHECK_…`. Typicky u `auth.sendCode`, ale TDLib to dělá u všeho.

- **TDLib dotaz odloží a čeká na aplikaci bez lhůty** (`NetQueryDispatcher`, `NetQueryVerifier`
  ve zdroji TDLib): pošle `updateApplicationVerificationRequired` /
  `updateApplicationRecaptchaVerificationRequired` a dotaz pustí dál až po
  `setApplicationVerificationToken`. Dřív to nikdo neodebíral, takže by přihlášení viselo
  s točícím se tlačítkem navždy a nic by se nedozvěděl ani log.
- **Plume ověření hned odmítá prázdným tokenem** (`declineVerification` v `TdlTelegramSession`);
  dotaz skončí chybou `VERIFICATION_FAILED` a ta má vlastní větu („jen po ověření oficiální
  aplikace, zkus to v oficiální aplikaci"). Token získat nejde, dokumentace to vede „for official
  mobile applications only": Play Integrity se ptá Cloud projektu Telegramu, který ověří podpis
  oficiální aplikace; tajemství pro iOS chodí pushem jen na její APNs registraci; klíč reCAPTCHA
  je omezený na balíčky Telegramu.
- **Odběr běží od startu klienta**, v `attach` vedle stavu přihlášení. Wrapper drží updaty
  v `SharedFlow` bez paměti, takže update poslaný dřív, než se kdo přihlásí k odběru, se ztratí —
  a s ním i jediná šance dotaz uvolnit.
- **Hlásí se jako problém do Crashlytics** (`logger.e` s výjimkou `ApplicationVerificationRequired`
  a druhem ověření). Jestli a jak často to server po Plume chce, nevíme; podle toho se pozná,
  jestli stojí za víc než větu.
- **Desktopová TDLib chyby na ověření nepřevádí** (jen sestavení pro Android a iOS), tam dotaz
  rovnou skončí chybou od serveru.
- **Ověřeno jen překladem a testem věty.** Vyvolat to na emulátoru nejde: TDLib to umí podstrčit
  jen s přepínačem `TD_TEST_VERIFICATION` při sestavení a server po našem `api_id` ověření
  zatím nechtěl. Na emulátoru ověřené je, že odběry nerozbily start a přihlášení.

### Výběr země a formát čísla

**Seznam zemí ani formáty čísel se nedrží u nás** — dává je TDLib, a všechno „can be called
before authorization":

- `getCountries()` → kód, název, **emoji vlajky**, `isHidden` a předvolby. Vlajka je emoji ze
  systémového písma, takže se pro dvě stě zemí nedrží žádná sada ikon.
- `getCountryCode()` → země podle IP. **Použije se, jen dokud si uživatel nevybral sám** —
  dorazí po síti a přepsat jím ruční volbu by vypadalo jako chyba.
- `getPhoneNumberInfoSync(languageCode, prefix)` → z rozepsaného čísla pozná zemi a vrátí masku,
  ve které **`-` je očekávaná číslice**. Je to lokální dotaz, takže se smí volat při každém úhozu.

- **Fokus a chybu nese rámeček kolem celého bloku** (`authOutlineColor`, sdílený s políčky kódu
  a s heslem). Dřív to bylo podtržení `TextField`u: čára široká jen jako pole a přilepená přesně
  na spodní hranu karty, takže vypadala jako lem, který tam něco zapomnělo — a přihlašovací
  obrazovka měla pro tutéž věc tři různé projevy.
- **Číslo má nápovědu, ne plovoucí label.** Label po vyplnění odskočí nahoru a text s ním
  klesne — číslo pak nelícovalo s předvolbou vedle, a ta label nemá a mít nemůže (je to volba,
  ne psané pole). Platí obecně: **pole, které stojí v řádku vedle textu bez labelu, label mít
  nesmí.**
- **Než dorazí odhad země, stojí v řádku výzva „Vyber zemi", ne podruhé slovo „Země".** Popisek
  a hodnota se stejným textem nad sebou vypadaly jako chyba vykreslení.
- **Řadí se abecedně, ne jak to pošle TDLib.** Ten řadí podle KÓDU země (AD, AE, AF…), což pro
  čtenáře není pořadí. Vědomá výjimka z pravidla „pořadí necháváme od TDLib" — u chatů je to
  pořadí, na které je uživatel zvyklý z jiných klientů, tady je to náhodná řada.
- **Maska se ukazuje jen po poslední zadanou číslici** (`formatNationalNumber`). „777 --- ---"
  vypadá jako rozbité pole.
- **Hodnotou pole jsou syrové číslice, oddělovače dodává `VisualTransformation`.** S naformátovanou
  hodnotou by kurzor skákal pokaždé, když formátování vloží mezeru. Mapování pozic je pokryté
  testy (`PhoneEntryTest`) — chyba v něm se na zařízení pozná až skákajícím kurzorem, což se
  při klikání přehlédne.
- **Nesedící maska transformaci vypne.** Než po změně země dorazí přeformátování, je na jeden
  snímek maska od předchozí země; pole, které ukazuje jiné číslo, než v něm je, by bylo horší
  než pole bez mezer.
- **Ne každá země formát má.** Česko ano („777 123 456"), Německo ne — číslo pak zůstane
  bez mezer a je to v pořádku.
- **Názvy zemí překládá OPERAČNÍ SYSTÉM, ne TDLib** (`localizedCountryName`). `CountryInfo.name`
  má být podle dokumentace „native name", jenže TDLib před autorizací vrací anglické názvy
  (ověřeno na emulátoru se systémovou i aplikační češtinou). Kód země je ISO 3166-1 alpha-2
  a k němu mají obě platformy data z CLDR: Android `Locale.getDisplayCountry()`, iOS
  `NSLocale.displayNameForKey(NSLocaleCountryCode)`. Nestahuje se nic a nedrží se vlastní
  tabulka dvou set zemí, která by stárla.
- **Neznámý region se pozná podle toho, že se vrátí sám kód.** `getDisplayCountry` u regionu,
  který nezná, vrátí vstup — to je „nevím", ne překlad, a spadne se na název od TDLib.
- **Na Androidu stačí `Locale.getDefault()`**, ten sleduje i jazyk nastavený jen pro aplikaci
  (per-app locale), takže název země odpovídá tomu, v čem je zbytek obrazovky.
- **Na iOS se `NSLocale.currentLocale` použít NESMÍ.** Jde za regionálním nastavením systému,
  ne za jazykem aplikace: česká aplikace na anglickém simulátoru měla „Země: Czechia" k česky
  psanému zbytku obrazovky. Locale se proto staví z [platformLanguageCode]
  (`NSLocale(localeIdentifier = …)`) — tedy z téhož zdroje jazyka, jaký dostává i TDLib.
  Ověřeno na simulátoru: `simctl launch … -AppleLanguages "(cs)"` ukáže rozdíl bez přepínání
  celého systému.
- **Hledá se i podle anglického názvu** (`Country.englishName` od TDLib, nekreslí se). Kdo zná
  zemi jen anglicky, by ji v českém seznamu jinak nenašel — „Germany" vs. „Německo".
- **Seznam zemí se načítá až ve fázi zadání čísla** (`AuthStage.WaitPhoneNumber`), ne v `init`
  ViewModelu. `AuthViewModel` vzniká v `App` při KAŽDÉM startu, i u přihlášeného uživatele,
  který přihlašovací obrazovku nikdy neuvidí — a načtení zemí se tak dělalo při každém startu
  zbytečně. Načte se jednou; po neúspěchu to zkusí příští návrat k číslu. Hlídá to
  `AuthViewModelTest` (`prihlasenyUzivatelZemeNenacita`) a na emulátoru vzorkovací trace
  studeného startu (`am start -S --start-profiler /data/local/tmp/… --sampling 500`; soubor
  musí ležet v `/data/local/tmp` a mít práva 666, do `cache/` aplikace ho `am` neotevře):
  `AuthViewModel` i `AuthScreen` (fáze `Connecting`) v něm jsou, `countries` ani `foldDiacritics`
  ne. Jména metod jsou v hlavičce trace čitelná, stačí `grep -a`.
  Přihlášení ověřené odhlášením na emulátoru: ViewModel vzniklý ve stavu `Ready` po odhlášení
  země dotáhl a předvybral Česko podle IP; seznam abecedně podle českých názvů, „cad" najde
  Čad, „germ" Německo; totéž po studeném startu v odhlášeném stavu.
- **Převod a řazení zemí běží na `Dispatchers.Default`** (`TdlTelegramSession.countries`).
  Odpověď TDLib se vrací na vlákno volajícího, tedy z `viewModelScope` na HLAVNÍ. Dvě stě zemí,
  u každé překlad názvu od systému (na Androidu první sáhnutí do dat CLDR) a řazení, které
  klíč `foldDiacritics(name).lowercase()` počítalo v komparátoru — při každém porovnání, zhruba
  tři tisíce krát. ANR trace z emulátoru (8. 10. 2026, „Input dispatching timed out … FocusEvent")
  ukázaly hlavní vlákno při studeném startu v `TimSort` nad `foldDiacritics`. Klíč se teď
  počítá jednou na zemi (`sortedByName`, testy `CountryOrderTest`).

## Přihlášení na webu účtem Telegramu

Uživatel se přihlašuje na cizím webu, ne do Plume.

### Automatické přihlášení po ťuknutí na odkaz

Webový odkaz ze zprávy se napřed zeptá serveru (`getExternalLinkInfo`): „otevřít" (s potvrzením
adresy, pokud ho nevypne `skipConfirmation`), nebo „zeptat se" — pak tentýž `LoginUrlDialog`
jako u přihlašovacího tlačítka bota, a potvrzení vezme adresu z `getExternalLink`
(`ConversationLinks.openExternal`). Prázdná adresa = přihlášeno, stačí to říct hláškou.

- **Chyba nebo zavřený dialog = obyčejný odkaz**: uživatel na odkaz ťukl, web chtěl.
- **V tajném chatu se serveru na odkaz neptá** — TDLib to tam dovoluje jen se zapnutými náhledy.
- **Ověřeno na emulátoru jen to, že se běžný odkaz otevírá dál** (Play v Uložených zprávách se po
  `getExternalLinkInfo` otevřel v prohlížeči). Web, který přihlášení z odkazu skutečně nabízí,
  ověřený není.

### Proč tu není potvrzení žádosti webu (OAuth)

Sheet pro žádost webu (`updateNewOauthRequest`, `getOauthLinkInfo`, `acceptOauthRequest`…) byl
hotový, ale **se skutečným webem do něj nic nedojde, obě cesty zastaví TDLib** (wrapper 15.0.0,
ověřeno 21. 10. 2026 na ukázce `core.telegram.org/bots/telegram-login` s testovacím účtem), takže
se z PR vyndal:

- **Žádost po zadání čísla chodí JEN pushem** (`OAUTH_REQUEST`, `NotificationManager`). Push do
  Plume dorazil, ale `processPushNotification` skončil chybou `Can't find field "data_url"` —
  TDLib čeká `custom.data_url`, server ho v pushi nemá. `updateNewOauthRequest` nepřijde nikdy.
- **Odkaz `t.me/oauth?startapp=…`** (tlačítko „Continue with Telegram" / QR) tahle TDLib rozpozná
  jako MINI APLIKACI „OAuth Bot", ne `InternalLinkTypeOauth`. Plume ji otevře a web v ní ukáže
  vlastní potvrzení s pruhem „Update Telegram for a faster, native login experience". To je
  dnes jediná cesta; „Log In" v ní na emulátoru nereagoval (neověřené proč).
- **Při bumpu wrapperu** ověřit obojí znovu stejnou ukázkou; kód sheetu je v historii větve
  `feat/prihlaseni-na-webu` (VladimirTintera/telegram#929).

## Odhlášení

- **Po odhlášení je TDLib klient MRTVÝ.** `logOut` projde `LoggingOut` → `Closing` → `Closed`
  a wrapper pak ukončí všechny update flows klienta. Nový účet se na něm přihlásit nedá —
  musí vzniknout nová instance (`TelegramClientHolder.recreate()`), která začne od
  `WaitTdlibParameters` a sama dojde k zadání čísla. Dokud to tak nebylo, aplikace po odhlášení
  zůstala natrvalo stát na nápisu „Odhlášeno".
- **Klient se proto nikde nesmí zachytit do `val`.** Požadavky jdou přes `holder.client`
  (getter), odběry přes `holder.updates { … }`, které se na novou instanci přepnou samy
  (`flatMapLatest`). `client.fooUpdates.collect` na zachyceném klientovi po odhlášení tiše skončí.
- **Nový klient se zakládá, až když skončí odběr stavů, ne podle `AuthorizationStateClosed`.**
  Wrapper ukončuje update flows jedním příznakem `stopped` SPOLEČNÝM pro všechny odběry
  klienta (`TdlRepository.checkStopped`): kdo `Closed` zpracuje první, příznak nastaví a ostatním
  odběrům flow skončí DŘÍV, než k nim `Closed` dojde. Odběrů jsou desítky, takže session o `Closed`
  přicházela podle toho, kdo byl zrovna rychlejší — a po ukončení relace z jiného zařízení
  („Zařízení" → odhlásit) zůstala aplikace natrvalo na „Odhlašuji", dokud se nekillnula. Flow
  jinak než zavřením klienta neskončí, takže konec `collect` je spolehlivý signál
  (`onClientClosed`). **Obecně: na poslední update flow, který klient pošle, se nedá spolehnout —
  vidí ho jen nejrychlejší odběratel.**
- **Session se k novému klientovi přihlašuje `CoroutineStart.UNDISPATCHED`.** Odběr musí stát
  dřív než `getAuthorizationState` a nová instance nic nevydá, dokud nedostane první request —
  takže ztracený první stav by automat zastavil.
- **„Odhlášeno" není stav, jen přechod** (`AuthStage.LoggingOut` s indikátorem). Odhlášený
  uživatel je prostě na začátku přihlášení.
- **`fileId` platí jen v jedné databázi TDLib.** Nový účet čísluje soubory znovu, takže keše
  podle `fileId` (`TdlFileRepository`, `knownPaths` v `RemoteImage`) se po výměně klienta musí
  zahodit — jinak by se pod novými id ukázaly obrázky předchozího účtu. Totéž keš odesílatelů.
- **Úklid mimo aplikaci**: notifikace i zkratky konverzací (`NotificationUpdate.Cleared`). Zkratky
  jsou dlouhověké, takže `removeAllDynamicShortcuts` nestačí — je potřeba
  `removeLongLivedShortcuts`, jinak zůstanou chaty odhlášeného účtu v nabídce ikony aplikace.
