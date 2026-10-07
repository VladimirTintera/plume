# Přihlášení a odhlášení

Poznatky k jedné oblasti aplikace. Obecná pravidla projektu a rejstřík ostatních souborů jsou
v [CLAUDE.md](../../CLAUDE.md); poučení z opravy v téhle oblasti patří sem.

## Přihlášení QR kódem

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
- **Zpět je `NavigationBackHandler`**, jen v kódu, hesle a QR. Na čísle zpět aplikaci zavře jako
  dřív — před číslem nic není. Uvnitř obnovy jde zpět o krok obnovy.
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
