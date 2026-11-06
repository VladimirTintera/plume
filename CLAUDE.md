# CLAUDE.md

Material 3 Expressive klient pro Telegram. Kotlin Multiplatform, Android + iOS + desktop (JVM), sdílené Compose UI.

**Pracovní název je „Plume"**, balíček `eu.tintera.plume`. Podle [Telegram API ToS](https://core.telegram.org/api/terms)
nesmí název obsahovat „Telegram" bez předřazeného „Unofficial" a nesmí se použít oficiální logo.

## Stav

Hotové: téma, přihlašovací automat, Navigation3 + adaptivní shell (bez railu i navigační lišty, Nastavení z menu účtu), živý seznam chatů a archiv,
konverzace (historie, živé zprávy, odesílání, přílohy a alba, hlasovky a video kolečka, samolepky a emoji, úprava,
přeposílání, připnuté zprávy, ankety a kvízy, témata ve skupinách), prohlížení příběhů (lem u avataru, hromádka v pilulce), prohlížeč médií s fotkami i videem, reakce, odpovědi, notifikace
a hlášení pádů na Androidu, nabídka aktualizace z Play, sponzorované zprávy, stav spojení a jeho obnova po návratu z pozadí.

Není: hlášení pádů z iOS. Notifikace na iOS (APNs) jsou napojené, ale čekají na certifikát APNs u Telegramu a ověření skutečným pushem.
Hledání umí chaty, veřejné chaty, zprávy napříč chaty i uvnitř jedné konverzace. Nastavení je list-detail: nahoře karta účtu, sekce Soukromí, Notifikace, Chaty, Vzhled, Úložiště, Zařízení a AI v zařízení, řádek Jazyk, Pomoc s Co je nového a odhlášení; vědomě
nastavitelné je téma aplikace (motiv a barvy z tapety), vyčlenění nepřečtených chatů do vlastního
bloku, upozornění po rozsazích (soukromé chaty / skupiny / kanály) s výjimkami, velikost písma
zpráv a samolepek, řádky náhledu v seznamu chatů, rychlá reakce dvojitým ťuknutím, odkazy v aplikaci, jazyk aplikace a tiché hodiny notifikací.
Rozpracovaný detail chatu má vlastní seznam:
[docs/roadmap-konverzace.md](docs/roadmap-konverzace.md).

## Build

```bash
./gradlew :client:androidApp:assembleDebug          # Android APK
./gradlew :client:shared:linkDebugFrameworkIosSimulatorArm64   # ověření iOS linku
./gradlew :client:shared:testAndroidHostTest :client:core:testAndroidHostTest   # testy commonTest na JVM (sekundy, bez emulátoru)
./gradlew :client:desktopApp:run                    # desktop
open iosApp/iosApp.xcodeproj                         # iOS se staví z Xcode
```

**llama.cpp je git submodul** (`client/ai-llamacpp/llama.cpp`, EuroLLM na Androidu i desktopu):
po klonu `git submodule update --init --depth 1`, jinak build Androidu skončí na chybějícím
`CMakeLists.txt`. Desktop se přeloží i bez něj (a bez CMake), jen EuroLLM nenabídne. **NDK
a jeho licenci potřebuje KAŽDÝ build** (AGP ho chce při konfiguraci :client:ai-llamacpp-android),
CMake `cmake;3.31.0` z SDK Manageru.

Před prvním spuštěním doplň do `local.properties` (gitignorovaný):

```
telegramApiId=<z my.telegram.org>
telegramApiHash=<z my.telegram.org>
```

Bez nich se projekt přeloží, ale přihlášení skončí hláškou `AuthStage.Failed` s vysvětlením.

## Poznatky k oblastem (`docs/poznatky/`)

Tenhle soubor nese jen to, co platí pro celý projekt. Poznatky ke konkrétním funkcím jsou
v `docs/poznatky/` — **než sáhneš na oblast, přečti její soubor**, a poučení z opravy zapiš tam,
ne sem. Odkaz „viz …“ míří na nadpis; najde ho `grep -rn '^#.*<nadpis>' CLAUDE.md docs/poznatky`.

- [Konverzace: zobrazení zpráv](docs/poznatky/konverzace-zobrazeni.md) — skladba obrazovky a ViewModelu (který soubor nese kterou část), bloky seznamu, bublina a její tvar, média bez bubliny, alba, předěly, doručení, vzhled a sklo, barva konverzace, text s entitami a odkazy, sbalení dlouhé zprávy, přeposlání, soubory, počet členů v hlavičce, reklamy a jejich nahlášení, metriky zobrazení
- [Konverzace: pohyb v historii](docs/poznatky/konverzace-pohyb.md) — otevřený chat, skok na nepřečtené / citaci / zmínky / datum, kalendář zpráv (počty po dnech), připnuté, hledání (i seznam výsledků), okno v historii, hlášení přečtení, gesta tahem do strany, rychlý přepínač chatů
- [Konverzace: psaní a odesílání](docs/poznatky/konverzace-psani.md) — vstupní pole, Esc a šipka nahoru, fokus pole na desktopu, přílohy a galerie (i víc než deset a poslat jednotlivě), návrh popisku fotky modelem, poslední odeslané soubory, hudba jako skladba, úprava fotky, práva psát, „píše…“, plánování, zvoneček tichého odesílání v kanálu, úprava, formátování, náhled odkazu, koncept, napovídání, odpovědi (i v jiném chatu), let odeslané zprávy, výměna přílohy při úpravě
- [Konverzace: akce nad zprávami](docs/poznatky/konverzace-akce.md) — nabídka nad zprávou, její skupiny a „Další", citace z ní, smazání cizích zpráv správcem (spam, vše od autora, vyhodit), žádost o vypnutí ochrany obsahu, překlad, shrnutí od serveru, automatické mazání, vymazání historie (celé i za vybrané dny), výběr víc zpráv (i tahem), informace o zprávě, připomenutí, přeposílání
- [Boti a mini aplikace](docs/poznatky/boti.md) — tlačítka pod zprávou, vlastní klávesnice, příkazy, mizející zprávy, Start, inline boti, mini aplikace (WebView most; zápis, sdílení zprávy, výběr chatu a stažení z ní, úložiště, poloha, celá plocha, zástupce na ploše), streamované odpovědi a Zastavit, strážní bot u vstupu do chatu
- [Ankety a kvízy](docs/poznatky/ankety.md) — hlasování, výsledky, zakládání, média v anketě, místa a poloha, seznamy úkolů
- [Hledání a zakládání chatů](docs/poznatky/hledani-a-zakladani-chatu.md) — hledání chatů a zpráv s filtry i přirozeným jazykem, sponzorované chaty, doporučené kanály, nová konverzace, kontakty, synchronizace adresáře, Můj QR kód a odkazy zvenčí (i neznámé `tg://` s vysvětlením od serveru), založení skupiny a kanálu, limit skupin a kanálů, ChatPicker
- [Přímé zprávy kanálu](docs/poznatky/prime-zpravy-kanalu.md) — zapnutí v nastavení kanálu, „Napsat" u odběratele, vlákna po odběratelích u správce, navrhované příspěvky, proč se historie vlákna nečte od nuly
- [Spam, nahlášení a blokování](docs/poznatky/spam-a-blokovani.md) — průvodce nahlášením, úklid spamu, pruh nad konverzací (ChatActionBar), seznam blokovaných
- [Seznam chatů](docs/poznatky/seznam-chatu.md) — skupiny řádků, složky a FAB menu, návrh složek modelem, ověření, hlavička s pilulkou, Uložené zprávy, přehled konceptů, archiv a tah do archivu, akce, odložení a výběr víc chatů, čeká na odpověď, widget na ploše
- [Navigace](docs/poznatky/navigace.md) — Navigation3, list-detail scény, insety, přechody a predictive back, zásobník pro každou sekci
- [Profil chatu](docs/poznatky/profil-chatu.md) — profil, členové a jejich správa, společné skupiny, podobné kanály a boti, ukázky mini aplikace, omezení a vyhození, poslední akce správců a omyl antispamu, statistiky kanálu, skupiny a příspěvku (grafy a proč pevná paleta), nastavení skupiny a kanálu (pomalý režim, reakce, přepínače, boosty, reklamy, hlavní záložka, smazání pro všechny), úprava skupiny, gigaskupina, pozvánky, uvítací zprávy, témata, sdílená média, historie fotek a jejich nahlášení, návrh profilové fotky, fotka z emoji, úprava kontaktu (jméno, poznámka, vlastní fotka), hudba a dárky v profilu, profil firmy a jeho úvodní stránka, sběratelská jména a čísla z Fragmentu
- [Prohlížeč médií a média](docs/poznatky/prohlizec-medii.md) — prohlížeč, video a PiP, kvalita videa, reklamy ve videu, GIFy, kolečka a jejich nahrávání, automatické ukládání do galerie, seznam stahování, poloha a mapy, spoilery, mizející a citlivý obsah, text z fotky, popis fotky a hledání fotek podle obsahu, FileRepository
- [Hlasovky a hudba](docs/poznatky/zvuk.md) — přehrávání, u ucha, nahrávání a náhled hlasovky, nahrávání hlasovky a kolečka do Telegramu během záznamu (fragmentované MP4), hudba na pozadí a dock přehrávače
- [Příběhy](docs/poznatky/pribehy.md) — odkud se ví, kdo má příběh, lem po tvaru avataru s dílky, hromádka v pilulce hledání, prohlížeč (zmražení autorů, openStory/closeStory, pauza podržením, ztlumení, dlouhý popisek, čtečka), poslat dál a odkaz na příběh, správa vlastního příběhu (i výměna média) a Moje příběhy (archiv), příběh za kanál, limit jednoho příběhu za den u free účtu
- [Typy zpráv](docs/poznatky/typy-zprav.md) — popisy a servisní zprávy, články a Instant View, počty a komentáře, témata ve skupinách, placená média, dárky, příběhy, kontakt, hovor
- [Emoji, samolepky a reakce](docs/poznatky/emoji-samolepky-reakce.md) — velká emoji, kostky, efekty, vlastní emoji, kreslení samolepek mimo hlavní vlákno, tvorba samolepky z fotky a úprava vlastní sady, výběry samolepek / GIFů / emoji, samolepka na pozdrav v prázdném chatu, reakce a jejich čipy
- [Notifikace](docs/poznatky/notifikace.md) — TDLib a push, kanály a nastavení, zvuky z Telegramu, akce z notifikace a její odhrnutí, iOS (APNs), ohlášení hovoru, pruh videochatu
- [Sdílení do aplikace](docs/poznatky/sdileni.md) — ShareRequests, čip „Sdílíte: …“ a jeho uložení, Direct Share a obrazovka „Komu: …“, import chatu z WhatsAppu, iOS rozšíření a návrhy chatů
- [Přihlášení a odhlášení](docs/poznatky/prihlaseni.md) — QR kód, přihlašovací obrazovka (kód, heslo, obnova, země), přihlášení na webu z odkazu (a proč ne OAuth), odhlášení a nový klient
- [Šifrovaná databáze, spojení a proxy](docs/poznatky/databaze-spojeni-proxy.md) — klíč databáze a jeho migrace, stav spojení a obnova po pozadí, proxy a sponzorovaný chat
- [Systémová oznámení od Telegramu](docs/poznatky/systemova-oznameni.md) — oznámení od služby, nové podmínky služby, zmrazený účet, doporučené akce; proč se odebírají eagerně
- [Nastavení](docs/poznatky/nastaveni.md) — AppSettings (DataStore), obrazovka nastavení, hledání v Nastavení, pomoc (Zeptat se), soukromí, úprava profilu a ořez fotky, narozeniny, osobní kanál, víc uživatelských jmen, dvoufázové ověření, přihlašovací e-mail, přístupové klíče, změna čísla, smazání účtu, velikost písma zpráv (i štípnutím v konverzaci), diskrétní čtení, zařízení, úložiště a přenesená data, „Co je nového“, jazykový balíček Telegramu, synchronizace bez backendu (WebApp CloudStorage)
- [Zámek aplikace](docs/poznatky/zamek-aplikace.md) — PIN a biometrie, záznam v Keystore / Keychainu, kdy se zamyká, brzda pokusů, co zámek nechrání
- [AI v zařízení](docs/poznatky/ai-v-zarizeni.md) — Gemini Nano, Gemma a Qwen (i víc naráz a co čím počítat, porovnání modelů, další modely přes LiteRT-LM, EuroLLM přes llama.cpp, runtime jako součást z Google Play), shrnutí hlasovky, úsporný režim a přehřátí, Apple Intelligence na iOS, stahování modelu, shrnutí a návrhy odpovědí, varování před podvodem, triáž chatů, přepis a kontrola tónu před odesláním (i úpravy od Telegramu na serveru), vysvětlení zprávy, popis fotky
- [Release, R8 a hlášení pádů](docs/poznatky/release.md) — vydání do Play, self-hosted runnery (Mac a HQDEV50), R8 a keep pravidla, Crashlytics (i iOS přes Swift most), aktualizace z obchodu
- [Výkon a paměť](docs/poznatky/vykon.md) — reporty Compose kompilátoru, LeakCanary, heap dumpy, composition tracing, měření ne-debuggable buildu, start a plynulost
- [Tajné chaty](docs/poznatky/tajne-chaty.md) — přijetí a založení (`useSecretChats`), id není id uživatele, stav jako právo psát, časovač po přečtení, notifikace, zákaz snímků
- [Hovory](docs/poznatky/hovory.md) — co dělá TDLib a co knihovna (ntgcalls), verze a servery, licence, obálka na Androidu (služba, Telecom, výstup zvuku), ticho a zablokování v knihovně, videohovory a obraz v obraze, sdílení obrazovky a ukazovátko, hovor z konverzace a chybové hlášky, historie hovorů (sloučení opakovaných, smazání jednoho) a hodnocení, desktop (JNA, zvonění a okénko příchozího hovoru, video)
- [Wear OS](docs/poznatky/wear-os.md) — skladba `wearApp` nad `:client:core`, vzhled M3 Expressive (zmenšování u okraje, barvy z ciferníku, potvrzovací dialogy), přihlášení QR kódem na kulatém displeji a heslo přes `RemoteInput`, seznam chatů bez klíčů položek, dlaždice a komplikace s počtem nepřečtených, dlaždice s chaty (fotky přes `onTileResourcesRequest`, `singleTop`), emulátor `Plume_Wear`
- [Desktop (JVM)](docs/poznatky/desktop.md) — přepis jaru TDLib (jen knihovna systému, hook), data a Klíčenka, notifikace (JNI most k UNUserNotificationCenter, tlačítka oznámení), titulek okna v barvách motivu, celá obrazovka okna pro video, vložení ze schránky a přetažení souborů, kopírování příloh do schránky a jejich přetažení ven, release MSI a DMG a ikony, bubliny z kořene kompozice, zubaté a rozmazané obrázky (dekodér Coilu, přiblížení), úprava fotky a EXIF, zvuk a nahrávání hlasovek (FFmpeg a javax.sound), co desktop neumí

## Pracovní postup

**Každá změna jde přes samostatnou větev a pull request, nic se necommituje rovnou do `main`.**

- Větev se zakládá z aktuálního `main` a pojmenovává podle obsahu (`fix/…`, `feat/…`,
  `docs/…`). Jedna větev = jedna ucelená změna.
- **Průběžné dolaďování jedné oblasti je JEDEN PR s více commity**, ne řada PR postavených
  na sobě. Když uživatel ladí tutéž věc v několika kolech (vzhled nabídky nad zprávou, jeden
  blok konverzace…), každé kolo je commit v téže větvi a popis PR se průběžně doplňuje.
  Řada navazujících PR (#80–#87 u nabídky) se musela slučovat v přesném pořadí a každý
  čekal na přepnutí cíle na `main` — práce navíc bez užitku, protože se stejně slučovaly
  všechny najednou. Nová větev a nový PR až pro jinou oblast.
- Do `main` se dostane jen sloučením PR. Slučuje uživatel, ne agent — ani automatické sloučení
  se nezapíná bez výslovného pokynu.
- **Před commitem projdi `git diff --cached --stat` a hledej soubory, na které jsi nesahal.**
  Dvakrát (#650, #657) vznikl commit na čerstvém `main` z pracovního stromu se STARŠÍM stavem
  souborů a `git add -A` tiše vrátil cizí sloučené PR — nativní toasty, opravy testů, C++20
  pro CI. Neozval se žádný konflikt ani chyba; přišlo se na to až podle zmizelého kódu. Smazané
  soubory nebo `-` řádky v cizích oblastech znamenají zastaralý strom: zahodit a přenést jen
  vlastní změny na aktuální `main`.
- Popis PR říká, CO se změnilo a PROČ, a jak to ověřit na zařízení. Když změna na zařízení
  vyzkoušená není, musí to v popisu stát.
- Poučení z opravy patří do souboru oblasti v `docs/poznatky/` (obecné pravidlo pro celý projekt
  do tohohle souboru), ve stejném PR jako oprava samotná. Novou oblast = nový soubor a řádek
  v rejstříku výš. **Sem nepřidávat poznatky ke konkrétní funkci** — tenhle soubor se načítá při
  každé session a dřív kvůli nim narostl na 950 kB.
- **Každý PR přidá poznámku k vydání** `release-notes/<větev>.md` (`/` → `-`), jinak neprojde
  check „Release notes". Viz „Poznámky k vydání" níž.

## Poznámky k vydání

Text „Co je nového" v Google Play se skládá z poznámek, které nese každý PR:

```
type: feat
cs: Hledání v seznamu chatů
en: Search in the chat list
```

`type` je `feat`, `fix`, nebo `internal` (dokumentace, CI, refaktoring — pak bez `cs`/`en`).
Formát, ořez i skládání jsou v `.github/scripts/release_notes.py`.

**Změna jen pro některé platformy přidá řádek `units:`** (`android`, `ios`, `desktop`, `wear`; víc
čárkou, zkratka `all`/`client` = android, ios a desktop), třeba `units: android` u sdílení
obrazovky v hovoru. Bez řádku platí poznámka pro android, ios a desktop — tak vzniklo přes 600
starých poznámek a musí platit dál, proto je pole volitelné (v mlogu povinné). **Hodinky se
jmenují výslovně (`units: wear`)**: umí zlomek telefonu, takže poznámka, která o nich mlčí, do
jejich „Co je nového" nepatří. Podle pole se filtruje „Co je nového" v aplikaci (každý kanál
vydání jen své), Play (telefon `android`, hodinky `wear`) i GitHub Release (workflow předá kanál
`markdown`).
Neznámou platformu, prázdné `units:` a `units:` u `internal` zamítne `check`. Poznámka jen
pro `ios` dnes nejde nikam: iOS se nevydává a ukazuje vydání Androidu.

**Chybějící poznámku hlídá `pre-push` hook, ne až CI** (`.githooks/pre-push`, zapíná se jednou
na klon: `git config core.hooksPath "$(git rev-parse --show-toplevel)/.githooks"`; obejít jde
`git push --no-verify`).

- **Hlídá se až při pushi, ne při commitu.** U vícekolového PR přijde poznámka běžně až s druhým
  commitem, takže pre-commit by hlásil falešný poplach u každého předchozího. Push je okamžik,
  kdy z větve vzniká PR.
- **Tagy a `main` hook přeskakuje.** Release se vydává pushnutím tagu `android-…` a poznámky
  v něm už dávno jsou — blokovat ho by znamenalo rozbít vydávání. Do `main` se nic nedostane
  jinak než sloučením PR, který kontrolou prošel.
- **Formát se čte Z PUSHOVANÉHO COMMITU** (`git show`), ne z pracovního stromu: pushnout jde
  i starší commit, a hlídat se má to, co uvidí CI.
- **`core.hooksPath` musí být ABSOLUTNÍ cesta.** Relativní se počítá od pracovního stromu, do
  kterého se pushuje — a worktree (`.claude/worktrees/…`) stojí na vlastní větvi, kde `.githooks`
  být nemusí, protože je starší než hook. Hook tam pak neběžel a **nijak se to neohlásilo**: git
  na chybějící cestu k hooku nenadává. Config je mezi worktrees sdílený, takže absolutní cesta
  se nastavuje jednou a platí pro všechny.
- **Skript se hledá vedle hooku, teprve pak v pushovaném stromu.** Z worktree se pushuje větev,
  která `.github/scripts/` mít nemusí; hook naopak leží v hlavním stromu, kam absolutní cesta
  míří. Když není ani jeden, kontrola formátu se přeskočí a zbyde na CI — hook nemá blokovat
  push kvůli tomu, že si na něco nedosáhl.
- **Na Windows jde skriptu cesta přes `cygpath -m`.** Git Bash dává hooku cestu `/c/…`
  a Python pro Windows jí rozumí jen díky převodu argumentů v MSYS. S `MSYS_NO_PATHCONV=1`
  v prostředí (nastavuje se kvůli adb) převod neběží a Python hledal `C:\c\Develop\…` —
  hook spadl na „can't open file" a push zastavil, aniž by poznámku zkontroloval.
- **PR jen s komunitními překlady poznámku nepotřebuje** (`release_notes.py exempt`, volá ho
  workflow i hook): `values-<kód>/strings.xml` a iOS `<kód>.lproj/Localizable.strings` jazyků,
  které nepíšeme sami (`OWN_LOCALES` ve skriptu zrcadlí `completeLocales` v Gradle). Tak chodí
  PR od Weblate, který poznámku napsat neumí. Čeština, angličtina nebo cokoli jiného v témže
  PR výjimku ruší. Nový jazyk, který tím přejde práh výběru, si poznámku zaslouží — přidá ji,
  kdo vydává.
- **Kontrolu dělá TENTÝŽ skript jako workflow.** Dvě kopie pravidel by se rozešly a lokální
  kontrola by začala pouštět dál, co CI zamítne. `release_notes.py` proto jen podle
  `GITHUB_ACTIONS` přepíná, jestli chybu vypsat jako anotaci GitHubu, nebo jako větu.

- **Píše se pro uživatele, ne pro vývojáře.** „Hledání v seznamu chatů", ne „ChatSearchBar přes
  AppBarWithSearch". Co uživatel nepozná, je `internal` — i tak se soubor přidává, jinak by
  nešlo odlišit „nic pro uživatele" od „zapomenuto".
- **Nejvýš 100 znaků na jazyk.** Play bere 500 znaků na jazyk za celý release, takže se vejde
  pět poznámek. Co se nevejde, nahradí řádek „…a další drobné úpravy"; novinky jdou před
  opravami. Upload kvůli délce nikdy neselže.
- **Soubor v repu, ne sekce v popisu PR.** Release čte poznámky přidané mezi předchozím
  a aktuálním tagem PŘÍMO Z TAGU (`git show <tag>:…`), takže jsou z tagu reprodukovatelné
  stejně jako verze a nepotřebují GitHub API. Oprava textu po sloučení = další commit, který
  soubor upraví; do vydání se dostane, pokud je před tagem.
- **Soubory se po vydání nemažou.** Rozsah určuje `git log --diff-filter=A PREV..TAG`, mazání
  by znamenalo commit z release workflow — a verze ani nic jiného se z releasu necommituje.
- **Více kol téhož PR = úprava téhož souboru**, ne další soubor.
- **Do Play jdou jazyky z `PLAY_LANGS`** (čeština a angličtina). Jazyk poznámky musí být
  v záznamu aplikace v obchodě (`play/listing/`, workflow Play Listing), jinak hrozí, že API
  odmítne celý upload. Úplně nový jazyk = řádek v `LOCALES`, klíč v každé poznámce, záznam
  v `play/listing/` — a do `PLAY_LANGS` až PO jeho nahrání.
- GitHub Release dostane celý seznam v obou jazycích (bez ořezu) nad technickým changelogem.

## Struktura modulů

```
client/
  resources/   :client:resources  – KMP knihovna s composeResources (texty, ikony, fonty) a `Res`
  core/        :client:core       – vrstva kolem TDLib (core/telegram a co potřebuje); jediná s TDLib;
                                    pro Koin `coreModule()` (klient, přihlášení, repository, APP_SCOPE)
  core-testing/ :client:core-testing – náhražky typů z core pro testy core i shared
  shared/      :client:shared     – KMP knihovna klienta (features, UI, AppSettings, notifikace);
                                    `appModule()` zahrne `coreModule()` a přidá zbytek
  androidApp/  :client:androidApp – com.android.application, jen vstupní bod
  desktopApp/  :client:desktopApp – Compose Desktop (kotlin-jvm), jen vstupní bod a balení
  wearApp/     :client:wearApp    – Wear OS (com.android.application) jen nad :client:core
  ai-engine/   :client:ai-engine  – rozhraní runtime modelu v zařízení (LlmEngineFactory), bez závislostí
  ai-litert/   :client:ai-litert  – implementace nad LiteRT-LM; jediná s `com.google.ai.edge.litertlm`
  litertFeature/ :client:litertFeature – součást z Google Play na požádání, balí ai-litert (Android)
  ai-llamacpp/ :client:ai-llamacpp – llama.cpp (submodul), JNI a kód pro modely GGUF; čistě JVM
  ai-llamacpp-android/ :client:ai-llamacpp-android – knihovna llama.cpp z NDK
  ai-llamacpp-desktop/ :client:ai-llamacpp-desktop – knihovna llama.cpp pro desktop (CMake systému)
  llamacppFeature/ :client:llamacppFeature – součást z Google Play na požádání, balí ai-llamacpp-android
iosApp/        – Xcode projekt; framework `ComposeApp` produkuje :client:shared
```

**AGP 9 zakazuje `com.android.application` + KMP plugin v jednom modulu.** Proto je klient rozdělený
na tenké vstupní body nad jednou sdílenou KMP knihovnou. Platformní `expect`/`actual` zůstávají
v `:client:shared`, ne v app modulech.

**Všechny prostředky jsou v `:client:resources` a `Res` jen tam** (`eu.tintera.plume.resources`,
`publicResClass`). Přes jednu `Res` bere texty i ikony přes dvě stě souborů, takže se prostředky
mezi moduly nedělí. **Každý další modul, který závisí na knihovně prostředků, musí mít
`compose.resources { generateResClass = never }`** — vlastní `Res` by mu vznikla i bez jediného
prostředku a srazila by se s tou z `:client:resources` (stejné jméno třídy).

**Co se opakuje mezi moduly, je konvenční plugin v `build-logic/`** (included build), ne kopie
v build skriptu: `plume.source-checks` (kontroly zdrojáků na `check`), `plume.translations`
(seznam jazyků a kontrola mutací), `plume.locale-config` (`locales_config.xml` pro vstupní body
na Androidu), `plume.android-release` (verze z tagu a podpis release pro Play), `plume.tdlib-android` / `plume.tdlib-desktop` (hook `TdlibJsonRepair`). Nová kontrola zdrojáků = podtřída `SourceCheckTask` a řádek v pluginu.
**Každý vstupní bod, který přibalí TDLib, musí použít jeden z pluginů `plume.tdlib-*`** — jinak
ho dárek bez pozadí shodí a hlídání po přihlášení to ohlásí až za běhu.

**Vrstva `core` nesahá na `features` ani `app`, ani v testech** (`verifyCoreLayering`). Modul
`:client:core` to má z principu, kontrola hlídá balíčky `core.*`, které zůstaly ve `shared`
(`core/ui`, `core/notifications`…). Typ, který potřebují obě, patří do `core`; volá-li `core` kód
z `features`, rozhraní je v `core` a implementace ve `features` (Koin), viz KDoc kontroly.

**Balíčky `core.*` jsou rozdělené mezi `:client:core` a `:client:shared`** (např. `UiText`
v `core`, komponenty `core/ui` ve `shared`). Patří-li soubor do `core`, rozhoduje to, jestli ho
`core/telegram` potřebuje, ne jeho balíček. Dvě věci z toho plynou:
- **Co `shared` z `core` používá, musí být veřejné** — `internal` platí jen v modulu. Typ
  z TDLib ale do veřejného API `core` nepatří (mimo `core` by ho nikdo nepřeložil); takové
  pomocníky nech `internal`.
- **Smart cast na vlastnost třídy z `core` nefunguje** („public API property declared in
  different module"): kompilátor u cizího modulu nezaručí, že se hodnota mezi kontrolou
  a použitím nezmění. Hodnota do lokální proměnné (`val id = x.fileId; if (id != null) …`).

### Source-set hierarchie v `:client:shared`

- `commonMain` – `App.kt`, `di/`, `app/`, `core/`, `features/`
- `nonDynamicThemeMain` (→ ios, jvm) – platformy bez Material You.
- `androidMain`, `iosMain`, `jvmMain` – platformní `actual` (`jvmMain` = desktop)

## Hranice kolem TDLib — nejdůležitější pravidlo projektu

Modul `:client:core` (balíček `core/telegram`) je **jediné místo, kde smí být
`import dev.g000sha256.tdl.*`** — a od jeho vyčlenění to hlídá kompilátor: wrapper je jeho
`implementation` závislost, jinde se import nepřeloží. Zbytek
aplikace vidí jen doménová rozhraní: `TelegramSession` (přihlášení), `ChatRepository` (seznam
chatů), `ConversationRepository` (obsah konverzace), `FileRepository` (soubory a média). Klienta
sdílí přes `TelegramClientHolder` — TDLib klient je jeden na proces.

Proč to tak striktně:

1. **Upstream TDLib mění API i v patch verzích.** Wrapper to dokumentuje explicitně. Když se to stane,
   přepíše se jeden balíček a zbytek projektu se nezmění.
2. **`tdl-coroutines` je komunitní projekt jednoho autora.** Kdyby přestal vycházet, nahradí se vlastním
   `cinterop` nad C API `tdjson` (`td_create_client_id`, `td_send`, `td_receive`, `td_execute`) — zase
   pod stejným rozhraním.
3. ViewModely se testují proti `TelegramSession`, ne proti TDLib.

**Volej generované metody pojmenovanými argumenty.** Upstream do nich přidává a přejmenovává parametry;
poziční volání by se tiše rozsypalo. Platí hlavně pro `setTdlibParameters` (14 parametrů).

### Chybové hlášky pro uživatele

**Text z TDLib se uživateli nevypisuje, nikdy.** Překládá ho `userMessage(fallback)`
v `core/telegram`; surová hláška jde do logu (`logger.w` s výjimkou), a tím i do kontextu
hlášení pádů.

- **Chyby chodí ve dvou podobách a ani jedna není věta pro člověka.** Konstanta od serveru
  (`PHONE_CODE_INVALID`), nebo anglická věta od samotné TDLib („Have no write access to the
  chat", „Invalid chat identifier specified"). Které volání vyrobí kterou, se dopředu neuhádne —
  **znění se dá ověřit v knihovně**: `strings libtdjsonjava.so | grep …` najde ty, které si TDLib
  píše sama. Co tam není, přichází ze serveru.
- **`failure.message ?: "Česká věta"` je PAST, ne pojistka.** Takhle to bylo na dvaceti místech
  a ta česká věta byla mrtvý kód: výjimka z TDLib má `message` vždycky vyplněné, takže elvis
  nikdy nesepnul a uživatel viděl vždycky tu anglickou hlášku. Pořadí je proto obrácené —
  základ je věta od nás a hláška z TDLib se použije jen tam, kde z ní umíme vyrobit něco
  konkrétnějšího.
- **Tabulka překladů roste podle logu, ne podle fantazie.** Pár hlášek stojí za vlastní větu
  (limit připnutých chatů, vypršelý kód), zbytek spadne na text volajícího. Až se v logu objeví
  něco, co uživatele mate, přibude řádek.
- **Text od TDLib je i prosakování hranice.** Kdyby šel na obrazovku, měnila by novější verze
  TDLib text v UI — tedy přesně to, čemu má balíček `core/telegram` bránit.

### Odpovědi TDLib, které wrapper nerozebere

**`tdl-coroutines` si nullabilitu pole odvozuje JEN z textu dokumentace** (`td_api.tl`: „be null",
„pass null"; generátor `TdlGenerator.isNullable`). Povinné objektové pole čte `getValue`, takže
když ho TDLib vynechá, vyletí `NoSuchElementException: Key … is missing in the map` na vlákně
`TDL-Receiver-Thread` — a **shodí celý proces**. Přijímací smyčka (`TdlEngine`) je `launch` bez
ošetření chyb a engine je `lazy` na celý proces: i kdyby se výjimka spolkla, smyčka už skončila
a nepřijde jediná další odpověď ani update. Spolknout ji proto nejde, musí se jí předejít.

- **Známý případ: `gift.background`.** Dokumentace ho vede jako povinné, ale
  `StarGift::get_gift_object` (TDLib `StarGift.cpp`) nechá pozadí `nullptr`, když ho server
  k dárku nepošle. Týká se `getAvailableGifts` i zpráv `MessageGift` /
  `MessageRefundedUpgradedGift` — chat s takovým dárkem (i jako poslední zprávou v seznamu chatů)
  by aplikaci shazoval pokaždé znovu. Wrapper 15.0.0 a 16.0.0 padal; nahlášené jako
  [g000sha256/tdl-coroutines#162](https://github.com/g000sha256/tdl-coroutines/issues/162).
  Autor opravil dokumentaci v TDLib 1.8.69 (tdlib/td#3761) a **od wrapperu 17.0.0 je pole
  nullable** (`deserializeGift` čte `getObjectNullable`) — na všech platformách, i na iOS.
  Řádek z tabulky obcházky proto zmizel; mechanismus zůstává pro další případ.
- **Obcházka: odpověď se opraví dřív, než ji wrapper dostane** (`TdlibJsonRepair`, testy
  `TdlibJsonRepairTest` nad tabulkou s dřívějším řádkem `gift.background`). Tabulka „typ →
  chybějící pole → výchozí objekt" doplní pole, které TDLib vynechal. **Dnes je prázdná**
  a odpověď projde bez jediného hledání; s řádkem se odpověď bez známého typu neparsuje
  (hledá se podřetězec `"@type":"<typ>"`), oprava nikdy nevyhodí.
  **Do tabulky patří jen pole ověřená pádem nebo zdrojem TDLib**, ne preventivně.
- **Na Androidu ji zapojuje instrumentace bajtkódu** (plugin `plume.tdlib-android`,
  `TdlibReceiveHookFactory` v `build-logic/…/Tdlib.kt`, `AsmClassVisitorFactory`,
  `InstrumentationScope.ALL`):
  za jediné volání `org.drinkless.tdlib.JsonClient.receive` uvnitř wrapperu vloží
  `TdlibReceiveHook.afterReceive`. Jiná cesta dovnitř není — smyčka i parser jsou interní
  a knihovna je obfuskovaná (`dev.g000sha256.tdl.a.mt0.D6` = `deserializeGift`); `JsonClient`
  si ale jméno nést musí kvůli JNI. Ověří se `dexdump -d` nad APK (`invoke-static …
  TdlibReceiveHook;.afterReceive` hned za `JsonClient;.receive`).
- **Že vložení zabralo, hlídá `TdlTelegramSession` po přihlášení**: do `Ready` musí odpovědi
  hookem projít, jinak `logger.e` s výjimkou (v Crashlytics jako problém). Bump wrapperu nebo
  přejmenování `TdlibReceiveHook` by obcházku tiše rozpojily.
- **Desktop ji má taky**, jen ji vkládá přepis jaru wrapperu při buildu, ne AGP
  (`docs/poznatky/desktop.md`, „Hook TdlibJsonRepair na JVM").
- **iOS obcházku NEMÁ** (`tdlibJsonRepairWired = false`): Kotlin/Native klib přepsat nejde.
  Další pole téhož druhu by tam aplikaci shazovalo, dokud ho neopraví wrapper (dárek bez
  pozadí opravil 17.0.0).
- **Kandidáti téhož druhu** (sken zdroje TDLib 1.8.67: `nullptr` do konstruktoru, dokumentace
  o null mlčí; za běhu neověřené, aplikace je nevolá): `product_info` u
  `starTransactionTypeBotInvoicePurchase/Sale` — u `…BotSubscriptionPurchase/Sale` je od
  wrapperu 17.0.0 nullable.
- **Při bumpu wrapperu**: u řádků v tabulce (a kandidátů výš) se podívat, jestli je
  `TdlDeserializer.kt` ze zdrojového jaru (`tdl-coroutines-android-<ver>-sources.jar`) čte
  přes `getObjectNullable`; když ano, řádek pryč. Instrumentace i kontrola po přihlášení
  zůstávají — bez známých případů nestojí nic.

### Bump TDLib wrapperu

Verze Kotlinu v projektu **musí být ≥ verze, ze které je wrapper publikovaný** — jinak se jeho iOS klib
nenačte. Před bumpem zkontroluj:

```bash
curl -s https://repo1.maven.org/maven2/dev/g000sha256/tdl-coroutines-iosarm64/<ver>/tdl-coroutines-iosarm64-<ver>.module | grep -A2 kotlin-stdlib
```

## Jazyky

**Výchozí jazyk je angličtina** (`composeResources/values/strings.xml`), čeština je mutace
(`values-cs/`). Texty se čtou přes `stringResource(Res.string.…)`; v kódu nesmí zůstat literál,
který uvidí uživatel.

- **Zpětné lomítko před apostrofem se v Compose Multiplatform NEROZBALÍ.** Android resources
  `\'` vyžadují, tady by se uživateli ukázalo „you\'ll". Ověřeno na zařízení — do XML patří
  apostrof tak, jak je; escapují se jen XML entity (`&`, `<`).
- **`stringResource` je `@Composable`, `StringResource` ale ne.** Proto ViewModel i doménový
  stav nosí `UiText` (odkaz do prostředků + argumenty) a překládá se až v UI. Text poskládaný
  ve ViewModelu by po změně jazyka zůstal v tom starém.
- **I převedené zprávy nesou `UiText`, ne hotovou větu.** Servisní oznámení (`serviceText`),
  popis obsahu (`describe`, „Anketa: otázka") i náhled poslední zprávy (`previewText`, „Fotka")
  vrací `UiText`; zpráva ho má v `ChatMessage.description` (vlastní text zůstává v `text`,
  u popisných zpráv prázdný), seznam chatů v `ChatSummary.lastMessagePreview`, témata
  v `TopicMessagePreview.text`, hledání ve `FoundMessage.text`. Dokud se tam skládal řetězec
  přes `getString`, zůstal po přepnutí jazyka ve starém: proces změnu jazyka přežije (Activity
  se jen složí znovu) a s ním i převedené zprávy v paměti repository a ViewModelů.
  - **`UiText` umí větu (`Resource`), plurál (`Plural`) a nepřekládaný text (`Raw`)**, a argument
    smí být sám `UiText` („Automatické mazání zpráv: 1 den", délka je vnořený plurál). `Raw` je
    tu proto, aby jedno pole uneslo „popisek, a když není, slovo Fotka" (`orText`).
  - **Kdo kreslí zprávu slovy, bere `text`, a když je prázdný, `description`**: bublina
    (`MessageText`), servisní oznámení, citace, pruh připnutých a naplánované zprávy
    (`summaryLine`). Kopírování, úprava, výběr textu a překlad berou jen vlastní `text` — náš
    popis z nich ven nepatří (u ankety to platilo odjakživa).
  - **Mimo kompozici se překládá `resolveNow()`** (`suspend`, jazyk platný TEĎ): notifikace,
    snackbar, přepis pro AI shrnutí a návrhy odpovědí. Výsledek se nikam neukládá.
  - **Suspend text z prostředků se čte JEN přes `readString` / `readPluralString`**
    (`core/ui/ResourceStrings.kt`), nikdy přímým `getString` — hlídá to
    `verifyResourceReadsOffMain` na `check`. Přímé `getString` z korutiny na `Dispatchers.Main`
    (snackbar z `LaunchedEffect`, ViewModel) zamklo aplikaci natrvalo: ANR, hlavní vlákno
    v `stringResource` → `runBlocking`. Compose Resources 1.12 čte `stringResource` blokujícím
    `runBlocking` a mezipaměť (`AsyncCache`) hlídá `Mutex` z kotlinx, který při odemčení zámek
    PŘEDÁ první čekající korutině a probudí ji přes JEJÍ dispatcher. Čekala-li na Main, zámek
    je její, jenže hlavní vlákno blokuje `runBlocking`, který čeká právě na něj. Stačí k tomu
    třetí čtení na pozadí (notifikace), které zámek zrovna drží. `readString` čte na
    `Dispatchers.Default`, takže se zámek předává vláknu, které běží. Mechanismus modeluje
    `ResourceReadDeadlockTest` (s čtenářem na Main blokující čtení nedoběhne, přesunutým ano);
    upstream [CMP-6615](https://youtrack.jetbrains.com/issue/CMP-6615), opravené až
    v 1.13.0-alpha01 (zámek bez předávání, JetBrains/compose-multiplatform#5691) — **1.12.1
    opravu nemá**. Až projekt dojde na 1.13, obcházka i test můžou pryč.
  - **Vědomé výjimky, které zůstávají řetězcem:** náhradní název souboru a skladby bez jména
    („Soubor", „Audio" — je to jméno, pod kterým se soubor ukládá a které dostává systémový
    přehrávač) a štítky médií uvnitř článku („[Fotka]" — leží uprostřed textu s entitami
    a jeho offsety by se překladem posunuly). Po změně jazyka zůstanou ve starém, než se zpráva
    načte znovu.
  - **Najít, kdo text čte, pomohl překladač**: pole `ChatMessage.text` se dočasně přejmenovalo
    a chyby překladu vypsaly všech 88 míst. Grep podle `.text` by část minul (`it.text`,
    destrukturování) a ta by po změně tiše kreslila prázdno.
  - Ověřeno na emulátoru v jednom procesu (pid se nezměnil): seznam chatů, seznam témat
    a konverzace se servisními zprávami tam i zpět mezi češtinou a angličtinou.
- **Čeština má tři tvary množného čísla** (1 / 2–4 / 5+), angličtina dva. Kde se počítá, patří
  `plurals`, ne `when` nad číslem — viz „N nových zpráv".
- **Datum není jen překlad slov.** Pořadí dne a měsíce se liší, čeština chce měsíc ve 2. pádu
  („5. února"), angličtina v základním. Proto je i pořadí formátovací řetězec (`date_day_month`),
  ne slepenec v kódu.
- **`verifyTranslations` visí na `check`.** Chybějící klíč by se jinak poznal až na obrazovce:
  Compose spadne zpátky na výchozí jazyk, takže uprostřed české aplikace vyskočí anglická věta
  a nic to nenahlásí. Kontroluje všechny `values-*`: jazyky, které píšeme sami
  (`completeLocales`, dnes `cs`), musí mít všechny klíče, komunitní jazyky (Weblate) chybějící
  klíč jen ohlásí varováním — rozpracovaný překlad nesmí build shodit. **Formátovací argumenty
  jsou chyba u všech jazyků**: řetězec musí mít tytéž `%1$s` / `%1$d` jako výchozí, plurál smí
  v některém tvaru argument vynechat („Před minutou"), ale ne přidat cizí ani změnit typ.
- **`verifyNoHardcodedText` hlídá, že se text nevrátí do kódu.** Má dvě pravidla, protože
  s jedním to dvakrát propadlo: slot v UI (`Text(`, `text =`, `contentDescription = …`) nesmí
  dostat literál vůbec a český text nesmí být nikde mimo komentáře, logy a vnitřní kontroly.
  Hledat jen podle diakritiky nestačí — „Odebrat soubor" ani „Video" tak nenajdeš.
- **Prompt pro model je výjimka z JEDNOHO z těch dvou pravidel, ne z celé kontroly.** Instrukce
  pro Gemini Nano musí jmenovat „tykání/vykání" česky (bez toho model kamarádovi vykal, viz
  „Návrhy odpovědí"), takže promptové soubory v `core.ai` mají vypnuté pravidlo o diakritice —
  pravidlo o slotu v UI v nich platí dál. Prompt nejde na obrazovku, ale do modelu; je to tedy
  táž kategorie jako logy a vnitřní kontroly, které kontrola vynechává odjakživa. **Escape
  sekvence jsou na DATA, ne na prózu:** tabulka diakritiky v `SearchHighlight.kt` se čte
  strojem, kdežto prompt se ladí ČTENÍM a „tyk\u00e1n\u00ed" uprostřed anglické věty přečíst
  nejde. Výjimka visí na JMÉNU souboru, ne na balíčku, a **ověřila se sondami, ne úvahou**:
  český literál v `core/ai/OnDeviceAi.kt` i `Text("…")` uvnitř `ReplyPrompt.kt` kontrolu dál
  rozsvítí, takže se dírou nestalo nic než to, co se jí stát mělo.
- **Že výjimka platí, říká hlavička KAŽDÉHO promptového souboru, stejnou větou.** Napřed to
  stálo jen v KDoc `formalityInstruction` — tedy u jedné funkce v jednom ze dvou souborů, které
  pravidlo řídí: `SummaryPrompt.kt` o vlastní výjimce mlčel a s přepsáním té funkce by z kódu
  zmizela poslední stopa. Nese to totiž JMÉNO souboru, takže kdo zakládá třetí prompt, musí se
  o konvenci dozvědět od souboru vedle; jinak mu kontrola spadne na hlášku o composeResources,
  ze které důvod nepozná. Úplné zdůvodnění zůstává na jednom místě (komentář u kontroly
  v `build-logic/…/SourceChecks.kt` a tenhle odstavec) — hlavička je ukazatel, ne druhá kopie pravidla.
- **Ověřuje se přepnutím jazyka aplikace**, ne celého systému:
  `adb shell cmd locale set-app-locales eu.tintera.plume --locales cs-CZ` (prázdná hodnota vrátí
  systémový). Restart aplikace stačí, emulátor rebootovat netřeba.

## Material 3 / vzhled (UI)

Drž se **Material 3 Expressive guidelines a výchozích hodnot komponent**. Nečitelné změti barev
a splývajících ploch vznikají skoro vždy z ohýbání standardu, ne z jeho dodržování.

- **Verze Compose se berou z mlogu** (stejný Kotlin i AGP, ověřená kombinace): Compose, material3
  alpha, adaptive a lifecycle se zvedají spolu. Od Compose 1.12 se okno čte přes
  `currentWindowAdaptiveInfoV2` (zná navíc třídy Large a XL — na `widthClass` to nic nemění, obě
  leží nad Expanded) a stav modálního sheetu přes `rememberBottomSheetState(SheetValue.Hidden)`.
- **`material3` musí zůstat na ALPHA kanálu.** Ve stable artefaktu jsou API pod
  `@ExperimentalMaterial3ExpressiveApi` vyříznutá — `MaterialExpressiveTheme`, `MotionScheme`,
  `LoadingIndicator`, `WideNavigationRail`, `ButtonGroup`, `FloatingActionButtonMenu`. Bez alphy
  expressive vrstva neexistuje.
- **Preferuj defaulty.** Neoverriduj `containerColor`/elevation, dokud k tomu není konkrétní důvod.
- **Maximální šířky rozvržení jsou ze škály `ContentWidth`** (`core/adaptive`: Pane 360, Form 420,
  Bubble 560, Sheet 640, Reading 720, Conversation 840), ne konstanty po obrazovkách. Na ploše
  přes dva monitory se bez stropu konverzace i Nastavení natáhly přes tisíce dp. Drobné šířky
  uvnitř bublin (čip, štítek) jsou věc bubliny. Viz `docs/poznatky/navigace.md`, „Standardní šířky".
- **Zaoblení ze škály M3 je token, ne číslo** (`ShapeDefaults.ExtraSmall` 4, `Small` 8, `Medium`
  12, `Large` 16, `LargeIncreased` 20, `ExtraLarge` 28, `ExtraLargeIncreased` 32,
  `ExtraExtraLarge` 48; bloky řádků `GROUP_CORNER` / `INNER_CORNER` z `core/ui/GroupedList.kt`).
  Kopie téhož čísla se rozcházely (16 vs. 20 u bloků). Hlídá to `verifyShapeTokens`; hodnoty mimo
  škálu (bubliny, média, soustředné zaoblení uvnitř karty) jsou vlastní tvary a kontrola je pouští.
- **Vrstvení = surface container role, ne `tonalElevation`.** `surface` < `surfaceContainerLow/…` <
  `surfaceContainerHigh` (overlay) < `surfaceContainerHighest` (menu nad overlayem). `tonalElevation`
  u `surfaceContainer*` barev nic nedělá — M3 `Surface` tónuje jen `color == colorScheme.surface`.
- **Stránka je `surfaceContainer`, řádek na ní `surfaceBright`** (`groupRow`, `asListPage` v `core/ui/GroupedList.kt`).
  Stránku dává shell každé obrazovce sám (`PaneCard`), jen konverzace a obrazovky přes celou plochu si přes
  `Panes.ownSurface` nechávají `surface`. **`surfaceContainerLowest` na řádek ne:** v tmavém motivu je nejtmavší
  role (na Androidu 16 čistá černá) a z řádků by byly díry do stránky. Viz „Plocha stránky a řádku"
  v `docs/poznatky/seznam-chatu.md`.
- Barvy vždy z `MaterialTheme.colorScheme`, nikdy natvrdo hex.
- **Přepnutí motivu se PROLÍNÁ, neprobliká** (`animateColorScheme` z materialkoloru v `AppTheme`,
  spec `AppMotion.defaultEffectsSpec`). Skoková výměna schématu přebarvila celou obrazovku v jednom
  snímku a vypadala jako chyba vykreslení. Animuje se KAŽDÁ role zvlášť, ne crossfade dvou kopií
  aplikace přes sebe: v aplikaci je plno průsvitných vrstev (závoj nabídky nad zprávou, závoj pod
  stavovou lištou, karta vstupního pole) a přes sebe položené kopie by prosvítaly.
- **49 vlastních `animateColorAsState` se psát nemusí.** Materialkolor je v projektu už kvůli
  generování palety a `animateColorScheme` pokrývá všechny role. Je ale stavěný proti STARŠÍ
  material3 (4.1.1 → 1.10.0-alpha05, projekt jede na 1.12) a volá `ColorScheme.copy` — tedy
  syntetické `copy$default` s pevným počtem parametrů. Nová barevná role v material3 by ho binárně
  rozešla a **chyba by přišla až za běhu**; před bumpem materialkoloru nebo material3 se to ověří
  `javap` nad `material3-desktop` (obě verze mají `copy-_VG5OTI$default` se 48 barvami).
- **`ColorScheme` NEMÁ `equals`** (ověřeno `javap`), takže se porovnává referencí. Cíl animace proto
  musí být STABILNÍ INSTANCE: `dynamicLightColorScheme`/`dynamicDarkColorScheme` se na Androidu
  `remember`ují, jinak by `updateTransition` uvnitř `animateColorScheme` zakládalo transition
  v každé rekompozici znovu. `rememberDynamicColorScheme` z materialkoloru stabilní je.
- **Ikony systémových lišt se překlápějí skokem** (`SystemBarsAppearance`), i když barvy jedou
  animací. Čekat na dojetí by znamenalo světlé ikony na světlém pozadí po celou dobu přechodu.
- **Během přechodu rekomponuje všechno, co čte schéma** — je to nová instance v každém snímku.
  Proto `defaultEffectsSpec` (kriticky tlumená pružina, ~čtvrt vteřiny), ne `slowEffectsSpec`:
  delší animace by ten náklad jen natáhla. Co si z barev něco dopočítává (`senderColor` staví
  osm HCT odstínů, `accentOn` měří kontrast), to se po dobu přechodu počítá každý snímek.
- **Akcent na tónované ploše se musí ověřit PROTI TÉ PLOŠE** (`accentOn` v `core/ui/AccentColor.kt`,
  testy `AccentColorTest`). M3 garantuje kontrast jen mezi plochou a její `on*` rolí; že je
  `tertiary` čitelná na `tertiaryContainer` (jméno v citaci, název webu u náhledu odkazu, odkaz ve
  zvýrazněné bublině, hlavička nad vstupním polem), je vizuální zkratka — a **v paletě se zvýšeným
  kontrastem** (Android: Usnadnění → Kontrast) **neplatí**: tam jsou v tmavém režimu container role
  SVĚTLÉ a jejich `on*` tmavé, takže se `tertiary` s `tertiaryContainer` potkají na téže světlosti.
  Změřeno ze snímku od uživatele: text odkazu #8AE9B3–#B3FFD6 na ploše #96F5BF, tedy kontrast 1,05
  — odkaz nebyl vidět vůbec. Akcent proto při nedostatku kontrastu ustoupí barvě popředí té plochy
  a odlišení nese řez (u odkazu podtržení, `RichText` ho přidá samo, když `linkColor == overlayColor`).
  **Platí i pro `senderColor`** — osm odstínů je laděných na plochu konverzace, ne na bublinu.
  **A pro `primary` na `primaryContainer`** (vybraná volba režimu přílohy, úrovně v pruhu
  nahrávání, „Teď ne" na kartě aktualizace): tatáž past o roli vedle.
- **Rozhoduje se z CÍLOVÝCH barev, ne z právě animovaných.** Barva bubliny se při zvýraznění
  prolíná; kdyby se kontrast počítal z okamžité hodnoty, přepnul by akcent uprostřed přechodu
  skokem. Výsledek se pak prolíná spolu s ní (`bubble-accent` v `MessageFrame`).
- **Animace nechávej na `MotionScheme`** (`AppMotion` v `app/root/presentation/theme/Motion.kt`).
  Spatial specy mají jemný bounce (dampingRatio 0.6–0.78), effects specy bounce nemají — overshoot
  u alphy/barvy se ořízne do [0,1] a působí jako bliknutí.
  **I přechody `AnimatedVisibility` / `AnimatedContent`** — `fadeIn()` nebo `expandVertically()`
  bez specu jede na výchozí pružinu Compose, ne na `AppMotion`. Příchod `default…Spec()`, odchod
  `fast…Spec()`; `transitionSpec` u `AnimatedContent` není @Composable, motionScheme se načte
  předem. Hlídá to `verifyMotionSpecs`. **Animace, která jede k nule, nesmí mít překmit**
  (roh `RoundedCornerShape` pod nulou spadne) — tam effects spec, ne spatial.
- **`SharedTransitionLayout` je v kořenovém tématu**, ne uvnitř obrazovek — sdílený prvek musí přežít
  výměnu obrazovky. Scope se bere z `LocalSharedTransitionScope`.
- **Ikony = Material Symbols** jako vector drawables v `composeResources`, s vyplněnou i nevyplněnou
  variantou (vybraná položka má vyplněnou).

- **Icon buttony mění tvar při stisku** (`shapes = IconButtonDefaults.shapes()`: kruh → zaoblený
  čtverec, u přepínacích `toggleableShapes()`), všechny — i `Filled…`, `FilledTonal…`
  a `Outlined…`. Kdo přidává nový, předá `shapes` taky; tlačítko bez nich se chová postaru
  a vedle ostatních to vypadá jako nedodělek. U standardního tlačítka bez plochy se mění tvar
  vlnky (ořez), u plného i plocha. Výjimky: tlačítka s vlastním tvarem, který nese stav
  (odesílací tlačítko, přehrát u videa a hlasovky, náhled přílohy), a fotka účtu v pilulce —
  obsahem je kulatá fotka a hranatá vlnka by kolem ní trčela. Doplnilo se skriptem do všech
  ~100 volání naráz; ověřeno na emulátoru (šipka zpět podržená přes `input motionevent DOWN`).
- **Řádky bloku taky** — stisknutý se zaoblí na 16 dp. Dělá to `GroupedColumn`, `groupedItem`
  a `animatedGroupItemShape(…, pressed)` samy; kdo staví blok ručně přes `groupItemShape`,
  o odezvu přijde. Viz `docs/poznatky/nastaveni.md`, „Stisknutý řádek se z bloku vyčlení".
- **Textová tlačítka taky** (`shapes = ButtonDefaults.shapes()` u `TextButton`, `Button`,
  `FilledTonalButton`, `OutlinedButton`, `ElevatedButton`), i v dialozích. Ikonová to měla
  dávno a textová ze ~350 jen dvě, takže v jednom dialogu jedno tlačítko morfovalo a druhé ne.
  Hlídá to `verifyButtonShapes` na `check`. **Hlavní akce přes celou šířku je `MediumButton`**
  (`core/ui`, velikost M3E „medium" 56 dp; ikona přes `MediumButtonIcon`), ne
  `heightIn(min = 56.dp)`: tak vznikla 56dp pilulka s písmem, odsazením a tvarem 40dp tlačítka.
  S indikátorem místo popisku pevná výška (`height(MediumButtonHeight)`), jinak tlačítko při
  čekání poskočí.
- **Tlačítka vedle sebe jsou `ActionButtonGroup`** (`core/ui`, M3E `ButtonGroup`), ne `Row`:
  stisknuté se roztáhne a sousedé uhnou. Vzhled si nese volající (`GroupAction.button` dostane
  modifikátor a `interactionSource`, které MUSÍ předat tlačítku — bez nich skupina stisk
  nepozná a nic se nehne). Holé `ButtonGroup` ne: jeho ukazatel přetečení má holý `TooltipBox`.
  **Popisek je `GroupActionText`** (jeden řádek, smí přesáhnout do okraje) a `TextButton` má
  `compressionLimit = ButtonDefaults.TextButtonContentPadding`: smrštěný soused stisknutého
  tlačítka jinak zalomil popisek na dva řádky a řada poskočila („Celá fotka" v úpravě fotky).
  Dialogová tlačítka (`confirmButton` / `dismissButton`) zůstávají, jak je `AlertDialog` skládá.
- **Přepínače mají v jezdci ikonu** (`IconSwitch` v `core/ui`): fajfka zapnutý, křížek
  vypnutý. **Řádek s přepínačem je `SwitchListItem`** (`core/ui/FormRows.kt`: role, haptika,
  klikání celým řádkem), nadpis bloku `GroupTitle` / `TitledGroup` — ne vlastní kopie
  (nese `heading()` pro čtečku obrazovky; kdo přesto kreslí nadpis ručně, přidá ho taky). Stav tak nese i tvar, ne jen barva — pozná ho barvoslepý i v paletě se zvýšeným
  kontrastem. **Holý `Switch` se nepoužívá**; kdo přidává přepínač, sáhne po `IconSwitch`,
  jinak bude vedle ostatních vypadat jako nedodělek. Barvu ikony podává přepínač sám
  (`checkedIconColor` / `uncheckedIconColor`), ikona je dekorace bez popisu — stav čte čtečka
  z řádku (`toggleable(role = Switch)`). Vypnutý jezdec s ikonou je větší než prázdný; M3 to
  tak má. Ověřeno na emulátoru ve světlém i tmavém motivu (Vzhled, Notifikace).
- **Počty se přetáčejí** (`RollingNumber` v `core/ui`): odznak nepřečtených v seznamu chatů,
  počet u složky a u archivu v menu účtu, odznaky zmínek a reakcí v konverzaci i „N nových
  zpráv" na tlačítku dolů. Přetočí se jen změněné číslice, párované ODZADU (9 → 10 přetočí
  jednotky, desítky naskočí); roste-li číslo, přijíždějí zdola. Směr se bere z celého čísla,
  ne z číslice. Čtečce obrazovky jde celé číslo (`clearAndSetSemantics`).
  - **Číslo uvnitř věty z `plurals`** se nesestavuje v kódu: do plurálu se místo čísla dosadí
    značka (`NUMBER_MARK`, znak ze soukromé oblasti Unicode) a věta se kolem ní rozdělí. Jde to
    proto, že Compose Resources dosazuje argumenty jako text (`replaceWithArgs` nahradí
    `%1$d` čímkoli). Kdyby se to změnilo, ukáže se značka místo čísla.
  - Ověřeno na emulátoru zpomalenými animacemi (`animator_duration_scale 10`) a sérií surových
    snímků odznaku: 1 odjede nahoru, 2 přijede zdola.
- **„Tady nic není" a chyba celé obrazovky jsou `IntroEmptyState`** (`core/ui`: uvítací tvar,
  zvýrazněný nadpis, text, případně tlačítko), **prázdný podseznam uprostřed obrazovky je
  `GroupEmptyRow`** (jeden řádek v barvách a tvaru bloku). Holý šedý text uprostřed byl
  nejviditelnější zbytek M2: prázdný účet, chyba seznamu chatů, prázdná konverzace, chyba
  Instant View. Chybový stav má nadpis „Něco se nepovedlo" (`empty_state_error_title`) a konkrétní
  hlášku jako text; zkusit znovu je tónové tlačítko, ne `TextButton`.
- **Neurčité čekání je `LoadingIndicator` (měnící se tvar), kolečko jen jako PRŮBĚH.**
  Kolečko (`CircularProgressIndicator`) zůstává tam, kde ukazuje, kolik je hotovo (přehrávání
  kolečka, průběh v docku přehrávače), nebo kde se z čekání v průběh PŘEMĚNÍ: kruh kolem tlačítka
  v docku čeká na stažení skladby a pak plynule nese průběh — tvar by tam po načtení přeskočil
  na kruh. Totéž vlnitý kruh nahrávání, který se točí neurčitě, dokud TDLib průběh neohlásí.
  V tlačítkách se `LoadingIndicator` vejde: 24 dp v textovém tlačítku mini aplikace, 32 dp místo
  přehrát v pruhu náhledu nahrávky, 36 dp v odesílacím tlačítku sdílení. „Číst celý článek" ho
  má jen 18 dp (pod minimem M3) — větší by o pár bodů zvedl řádek s popiskem a ten by poskočil.
  Ověřeno na emulátoru v náhledu zastaveného kolečka (v kruhu i v pruhu karty, sérií snímků).
- **Nabídky jsou Expressive** (`ExpressiveMenu` v `core/ui`: `DropdownMenuPopup`
  + `DropdownMenuGroup`), ne holé `DropdownMenu`. Položky jsou ve skupinách se společným
  zaoblením krajních položek; **nevratná akce má skupinu vlastní a chybovou barvu**
  (`MenuAction.destructive`), co k sobě patří, je v jedné. Nabídka s výběrem (období v hledání)
  bere položky s `checked` — vybraná je zaškrtnutá.
  **Plocha nabídky je `surfaceContainerHighest`** (`MenuColors`, výchozí M3 má `surfaceContainerLow`,
  tedy pod stránkou i řádky); kdo kreslí `DropdownMenuPopup` sám, obalí obsah do `MenuColors`.
  **Okno nabídky si nebere klávesnici** (`MenuPopupProperties`, výchozí v `ExpressiveMenu`):
  holé `DropdownMenuPopup` / `Popup` bez vstupního pole ho dostane taky, jinak nabídka nad
  otevřenou klávesnicí klávesnici utrhne a obsah pod ní skočí. Viz „Nabídky si neberou
  klávesnici".
- **Modální sheet se otevírá rovnou celý** (`sheetState = rememberExpandedSheetState()`,
  `core/ui/ExpandedSheet.kt`) — hlídá to `verifyExpandedSheets`. Napůl rozbalený schovával pod
  spodní okraj výsledek, tlačítko Uložit nebo poslední položky a opravoval se po jednom v osmi
  sheetech; obsah, který přibude, rozbalený sheet zvětší sám.
- **Nadpis modálního sheetu je `SheetTitle`** (`core/ui`, `titleLargeEmphasized`), ne `Text`
  s vlastní rolí: dřív měla polovina sheetů `titleLarge` a polovina `titleMedium`. Odsazení
  si dává sheet. Výjimka jsou sheety, kde velký text JE obsah (náhled pozvánky, Můj QR kód).
- **Výběr jedné hodnoty v sheetu je `ChoiceListItem`** (`core/ui`): řádek v bloku zaoblených
  řádků, vybraný s plochou `secondaryContainer`, zaoblením skupiny a fajfkou, jako výběr země.
  Holé `ListItem`y s kolečkem vlevo byly poslední M2 seznam v aplikaci. V dialogu zůstává
  kolečko; řádek Nastavení s krátkým výběrem má nabídku u řádku (`ChoiceRow`).
- **Potvrzení akce je `ConfirmDialog`** (`core/ui`): ikona, otázka, věta s následkem, Zrušit
  a potvrzení. Nevratná akce `destructive = true` — ikona i potvrzení v chybové barvě přes barvy
  tlačítka. Ikona říká, CO se stane (koš, odchod, blokování), ne „pozor". Volba nebo pole, bez
  kterých akci nejde potvrdit („smazat i pro ostatní", délka vyhození, heslo), jde do slotu
  `content` pod větou; zaškrtávací volba je `DialogCheckRow`, obrázek místo ikony (fotka
  kontaktu) `iconContent`. `AlertDialog` zůstává jen tam, kde nejde o potvrzení (podmínky služby,
  popup mini aplikace s tlačítky od bota, výběr).
- **Vstupní pole formuláře je `FormTextField`** (`core/ui/BlockField.kt`): tónový blok
  s velkým zaoblením, fokus a chybu nese rámeček kolem bloku, pomocný text pod ním — tentýž
  jazyk jako kód a heslo (`BlockTextField`, `PasswordField`). `OutlinedTextField` zůstává jen
  v `AlertDialog`u, kde je to kanonický M3 vzhled. Parametry má `FormTextField` stejné jako
  `OutlinedTextField`, výměna je přejmenování.
- **Tlačítko ve slotu textového pole patří do `TextFieldSlot`** (`core/ui`): `leadingIcon` /
  `trailingIcon` s `IconButton`, avatarem a podobně. `BasicTextField` dává textový kurzor celému
  boxu i s dekorací a ikony ho dědí — na desktopu se nad avatarem účtu v hledání ukazoval
  kurzor pro psaní. Ozdobná ikona (lupa) obal nepotřebuje.
- **Bublina (tooltip) jen přes `WithTooltip` nebo `RootTooltipBox`** (`core/ui/WithTooltip.kt`),
  nikdy holý `TooltipBox` — hlídá to `verifyTooltipsThroughRoot`. Holý na desktopu shodí
  vykreslování, když kotva s viditelnou bublinou zmizí během měření (změna šířky okna). Viz
  „Bubliny (tooltip) z kořene kompozice" v `docs/poznatky/desktop.md`.
- **Podržení jen přes `contextClickable`** (`core/ui/ContextClickable.kt`), nikdy holé
  `combinedClickable` — hlídá to `verifyContextClickable`. Obal přidá pravé tlačítko myši jako
  podržení; vlastní gesto podržení dostane `onSecondaryClick`, a kde pravé tlačítko patří
  jinému (text vybíratelný myší), `rightClick = false`. Viz „Pravé tlačítko myši = podržení"
  v `docs/poznatky/desktop.md`.
- **Hláška „Zkopírováno" jen tam, kde zkopírování nepotvrdí systém** (`TextClipboard.confirmsCopy`,
  Android 13+): systém sám ukáže okénko s obsahem schránky vlevo dole a vlastní snackbar se
  s ním zdvojil a překryl (ověřeno na emulátoru). Kdo přidává kopírování, podmíní hlášku
  `if (!clipboard.confirmsCopy)`. **Hláška, která říká víc než „zkopírováno", zůstává**
  („odkaz vytvořen a zkopírován", „heslo je ve schránce, v nastavení Wi-Fi vyber síť"), stejně
  jako potvrzení uvnitř sheetu (fajfka u kódu dárku, ikona u odkazu složky) — to je stav
  ovládacího prvku, ne oznámení.

### Omezené rozostření

Mléčné sklo v konverzaci, závoj pod nabídkou nad zprávou a pozadí prohlížeče se nerozostřují,
když to systém nechce (`LocalBackgroundBlurAllowed`, `rememberBackgroundBlurAllowed` v `core/ui`);
Haze pak kreslí náhradní tónování, jako na Androidu 11.

- **Android: volba Přístupnost → Barvy a pohyb → „Omezit efekty rozmazání"** (Android 16 QPR2)
  zapisuje `Settings.Global.disable_window_blurs = 1` a aplikace se to dozví veřejným API
  `WindowManager.isCrossWindowBlurEnabled` / `addCrossWindowBlurEnabledListener` (od Androidu 12).
  Totéž hlásí úspora baterie a vývojářská volba rozostření oken. Zjištěno porovnáním
  `settings list` před přepnutím a po něm; `dumpsys window | grep mBlurEnabled` stav ukáže.
- **iOS: „Omezit průhlednost"** (`UIAccessibilityIsReduceTransparencyEnabled` a oznámení o změně).
  Jen přeložené.
- **Styl rozostření jen VYPÍNÁ** (`if (!blur) blurEnabled(false)`). `blurEnabled(true)` by přebil
  výchozí hodnotu Haze, která rozostření pod Androidem 12 sama vypíná.
- **Spoiler a citlivý obsah se rozostřují dál** — rozostření tam obsah skrývá, není to ozdoba
  pozadí, na kterou volba míří.
- **Ověřeno na emulátoru (Android 17)**: s volbou závoj nabídky jen ztmavuje (hrany za ním
  ostré), po `settings put global disable_window_blurs 0` za běhu se rozostření vrátí bez
  restartu. Původní stav emulátoru je klíč BEZ hodnoty (`settings delete`), ne nula.

### Typografie

Dvě rodiny (`theme/Typography.kt`): **Figtree** na všechno čtené, **Bricolage Grotesque** jen na
role `display*` a `headline*` — velké nadpisy obrazovek, přihlášení, iniciály v avataru. Obě
OFL z Google Fonts, licence v `docs/licenses/`.

- **Písmo se volí rolí škály, ne v obrazovce.** Žádné `fontFamily` v `features/`; kdo chce
  výrazné písmo, sáhne po `headline*`. Výměna dvojice je tím jen `Typography.kt` a `font/`.
- **`titleLarge` zůstává v primární rodině**, přestože zní jako nadpis. Nese název chatu
  v hlavičce konverzace, kam letí ze seznamu (`sharedBounds`) — změna písma by uprostřed letu
  skočila. Z téhož důvodu je sbalený nadpis `LargeFlexibleTopAppBar` (`titleLarge`) ve Figtree
  a rozbalený (`displaySmall`) v Bricolage; bar mezi nimi prolíná, takže to nevadí.
- **Statické řezy vyrobené z variabilních fontů**, ne variabilní soubory v aplikaci:
  `fonttools varLib.instancer <VF>.ttf wght=600 wdth=100 opsz=96 --static --update-name-table`.
  Jednodušší a na iOS jistější; osy se volí jednou, při výrobě.
- **Bricolage musí mít velkou optickou velikost a aspoň SemiBold.** S `opsz=48` a řezem Medium
  z ní byl obyčejný grotesk a nadpis nebyl od zbytku k rozeznání. Charakter (zúžení, zářezy
  v ohybech) je vidět až ve velkém a tučném.
- **Proložení výchozí M3 škály je laděné na Roboto** (`bodyLarge` +0,5 sp) a s jiným písmem je
  řídké. Po výměně rodiny se musí přeladit: text skoro nula, velké nadpisy lehce do minusu (Bricolage s `opsz=96` je těsná sama).
- **Text zpráv je 16/22**, ne 16/24 — bublin je hodně a hustší sazba je znát.
- **Tabulkové číslice (`tabularNumbers()`, `core/ui`)** všude, kde se číslo mění na místě nebo
  stojí ve sloupci: časy v seznamu chatů, počty nepřečtených a reakcí, čas hlasovky a videa,
  sloupec časů odkrývaný tahem, „N z M" v prohlížeči. Obě písma `tnum` mají.
- **Nepřečtený náhled v seznamu se odlišuje barvou (`onSurface`), ne řezem.** Pod polotučným
  názvem by byly na jednom řádku dvě tučnosti.
- **Zvýrazněné varianty (`*Emphasized`) jsou tatáž rodina o stupeň tučnější**, ne druhé písmo —
  zvýraznění uvnitř běžného obsahu nesmí vypadat jako cizí prvek.
  **Kde se používají:** to, co se na obrazovce čte napřed — jméno v profilu a v hovoru, nadpis
  přihlášení a prázdného stavu, karta účtu, velké číslo v Úložišti, počet vybraných, název
  vybrané složky, otázka ankety. Ruční `fontWeight = FontWeight.SemiBold` k roli `title*`
  / `label*` je totéž co její zvýrazněná varianta — patří tam ta, ať zvýraznění jde z typografie.
  U `body*` to neplatí: jejich zvýraznění je `Medium`, takže SemiBold v náhledu odkazu nebo
  v kartě polohy zůstává ručně. Nepřečtený chat má ruční SemiBold vědomě (jako Google Messages).
- **Emoji ve vlastním písmu nejsou**, berou se ze systémového fontu (na Androidu i iOS funguje
  fallback sám). Kostka „🎲 6" má proto číslo v Bricolage — velká emoji jsou `display*`.
- **Iniciály v avataru mají `lineHeight = Unspecified`.** Výška řádku `headlineSmall` (32 sp) by
  byla vyšší než malý avatar; přirozená výška se škáluje s `fontSize`.

### Material You a jeho náhrada

`colorScheme(isDark, dynamic, seed)` je `expect`. Android 12+ s barvami z tapety vrací systémové
dynamic color; jinak (iOS, Android do 11, vypnutá tapeta) se paleta generuje ze seed barvy přes
materialkolor (`expressiveColorScheme` v `theme/ColorScheme.kt`). iOS build tedy není „Android bez
barev", ale má vlastní konzistentní paletu.

**Seed si volí uživatel** — Nastavení → Vzhled → Barva (`ThemeColor`, `AppSettings.themeColor`,
`ThemeColorPicker`), osm vzorků jako „Tapeta a styl" na Pixelu.

- **Styl palety je `Vibrant`, ne `Expressive`.** Expressive (výchozí v materialkoloru, spec 2021)
  posouvá odstín primární barvy o 240° od seedu (`ColorSpec2021`) — vzorek „Červená" by nebyl
  červený a z modrého seedu vyšla jiná barva, než jaká se dala čekat. Vibrant drží odstín seedu
  a sytost dává nejvyšší, jakou odstín unese.
- **Vzorek se kreslí z TÉHOŽ schématu, jaké by aplikace dostala** (horní půlka `primary`, dole
  `secondaryContainer` a `tertiaryContainer`), v aktuálním motivu. Seed sám nic neříká — v tmavém
  motivu je primární barva světlý tón, a právě ten uživatel uvidí.
- **S barvami z tapety žádný vzorek vybraný není**, i když nějaký uložený je: vybraný vzorek by
  tvrdil, že se aplikace kreslí z něj. Řada je vidět i tak a ťuknutí tapetu vypne
  (`chooseThemeColor`), věta pod řadou to říká předem. Vypnutí přepínače tapety vrátí uloženou
  barvu.
- **`null` = modrá a výchozí se neukládá** (stejné pravidlo jako u motivu): výchozí barvu tak jde
  změnit bez migrace. Neznámý název z novější verze spadne na výchozí.
- **Vybraný vzorek se morfne do dvanáctistranné sušenky** (`ShapeMorphs.Highlight`, „to vybrané /
  podržené"); fajfka na ploše `surface` téže palety nese výběr i bez tvaru. Sušenka jde jen do
  ořezu při KRESLENÍ, vrstva a dotyk zůstávají v kruhu (viz „Vzhled konverzace").
- **Vybraný vzorek se ve výpisu `uiautomator` rozpadne do tří uzlů** (zaškrtnutelný bez popisu,
  popis, `RadioButton`), nevybraný je jeden uzel s popisem. Dělá to Compose u `selectable`
  s rolí RadioButton (vybraný přestane být „klikatelný") a stejně vypadají vybrané řádky motivu;
  přeřazení `semantics` před `selectable` na tom nic nemění. Čtečka uzly slučuje.
- **Ověřeno na emulátoru** (česky): vzorky ve světlém i tmavém motivu, výběr zelené vypne tapetu
  a přebarví aplikaci, červená v tmavém motivu, modrá, opětovné zapnutí tapety výběr zruší.
  **iOS jen přeložené.**

## Haptika

Hmatová odezva má **jeden slovník v `core/ui/Haptics.kt`** — šest pojmenovaných rolí místo
`HapticFeedbackType` rozsetého po obrazovkách: `threshold()` (gesto překročilo práh),
`confirm()` (akce se povedla), `reject()` (nepovedla), `toggle(on)` (přepnutý stav), `tick()`
(procházení po dílcích) a `pulse()` (tep, dva údery — efekt animovaného emoji pro oba). Stejná
úvaha jako u `ShapeMorphs` a `AppMotion`: význam má nést typ, ne vkus toho, kdo zrovna píše
obrazovku.

- **Dlouhý stisk si `combinedClickable` dělá SÁM.** Ruční volání navíc odezvu ZDVOJÍ — změřeno
  na emulátoru: dvě vibrace 9 ms po sobě, obě `constant=0`. Takhle to bylo na čtyřech místech
  (podržení samolepky, GIFu, emoji a samolepky v sadě). `longPress()` patří jen tam, kde si
  gesto detekujeme sami — dnes jediné místo je odkaz v textu (`RichText`).
- **Odezva patří k akci UŽIVATELE, ne k události od serveru.** Příchozí zpráva, dotažená stránka
  historie ani změna stavu spojení nevibrují; od upozornění na cizí akci jsou notifikace. Výjimka
  potvrzuje pravidlo: chyba se hlásí `reject()`, protože přišla po akci, kterou uživatel udělal.
  Druhá výjimka je vědomá: efekt animovaného emoji od protějšku tepe (`pulse()`), protože je to
  gesto člověka mířené na nás a přehrává se před očima (`docs/poznatky/emoji-samolepky-reakce.md`).
- **Jakmile vibruje všechno, přestane cokoli znamenat** — totéž pravidlo jako u tvarů. Proto
  **odkrývání časů tahem haptiku NEMÁ**: na rozdíl od archivace a odpovědi tahem tam žádný práh
  „při puštění se něco stane" není, odkrytí je vidět hned a buzení při každém nahlédnutí by byl
  jen šum.
- **Potvrzení se hlásí při ŤUKNUTÍ, ne po odpovědi serveru.** Odezva patří ke gestu; kdyby čekala
  na potvrzení odeslání, přišla by po stovkách milisekund a necítila by se jako reakce na stisk.
- **iOS je hrubší, než se ze seznamu typů zdá.** `CupertinoHapticFeedback` mapuje celou sadu na
  čtyři generátory: `confirm` a `reject` → notification, `longPress` → medium impact, `threshold`
  i `toggle` → light impact, `tick` → selection. Zapnutí a vypnutí přepínače na iPhonu nejde
  rozeznat — spolehnout se, že iOS odliší to, co Android, nelze.
- **Na Androidu část typů na starších verzích není.** `Confirm` a `Reject` jsou od API 30,
  `ToggleOn`, `ToggleOff`, `GestureThresholdActivate` a `SegmentTick` od API 34, zatímco `minSdk`
  je 26; Compose je posílá přes `HapticFeedbackConstantsCompat`, který je nahradí bližším typem,
  nebo neudělá nic. Ověřovat tedy i na starším emulátoru.
- **Oprávnění `VIBRATE` potřeba není** (jde to přes view, ne přes `Vibrator`) a vlastní přepínač
  v Nastavení taky ne — vypnout odezvu jde v systému a `performHapticFeedback` to respektuje.
- **Emulátor s Androidem 9 nemá vibrátor VŮBEC.** Obraz `google_apis_playstore;arm64-v8a` pro
  API 28 nezaznamená ani dlouhý stisk launcheru (`dumpsys vibrator` má „Previous vibrations"
  prázdné) — náhradu typů, které na starých verzích neexistují, tedy na něm ověřit nejde. Zbývá
  na to skutečné staré zařízení; co se ověřit dá, je, že nic nespadne.
- **Posuvník videa tiká po dvacetinách délky, prohlížeč při dosednutí na jinou fotku** — jen
  po tahu prstem (`collectIsDraggedAsState` nad `interactionSource` pageru); programový posun
  (smazaná fotka, skok na fotku) netiká. První dílek tahu po posuvníku netiká, tik znamená
  „přešel jsi hranici dílku".
- **Ovládání videa na celé obrazovce leželo v povinné zóně systémových gest** (emulátor Pixel 9:
  `mandatorySystemGestures` posledních 84 px, střed posuvníku na y 2350 z 2424). Vodorovný tah
  po posuvníku tam vzal systém a prohlížeč se zavřel; na telefonu s navigací gesty by přepnul
  aplikace. Lišta proto bere spodní inset `navigationBars ∪ mandatorySystemGestures`. Zónu
  ukáže `adb shell dumpsys window | grep -A2 SystemGestures`.
- **Emulátor nevibruje, ale volání zaznamenává**: `adb shell dumpsys vibrator_manager` má sekci
  „Recent vibrations" a v ní `reason: performHapticFeedback(constant=N)`. Čísla jsou
  `HapticFeedbackConstants`: **0 = LONG_PRESS, 16 = CONFIRM, 17 = REJECT, 21 = TOGGLE_ON,
  22 = TOGGLE_OFF, 23 = GESTURE_THRESHOLD_ACTIVATE, 26 = SEGMENT_TICK.** Tím se dá ověřit, že
  odezva odešla a která — **jak to sedí pod prstem (hlavně jestli dvě nejdou hned po sobě) se
  pozná jedině rukou na telefonu.**

## Gotchas

- **Modifikátor insetů s hodnotou, která se mění se změnou scény, shodí aplikaci při
  rozložení nebo složení Foldu.** `consumeWindowInsets(PaddingValues)` i `windowInsetsPadding`
  při změně hodnoty volají `insetsInvalidated()` → `traverseDescendants` a NEKONTROLUJÍ, jestli
  je uzel připojený (Compose 1.12, `WindowInsetsPadding.kt`). Když NavDisplay přesouvá obsah
  položky do jiné scény (jeden panel ↔ list-detail), aplikuje se změněný modifikátor uzlu,
  který je zrovna odpojený: `visitSubtreeIf called on an unattached node`. Compose chybu
  zachytí („Error was captured in composition") a pád pak přijde až při měření jako
  `NullPointerException` v `LookaheadPassDelegate.forEachChildAlignmentLinesOwner`, v release
  buildu `NPE … Object.getClass()`. **Hledá se tedy podle PRVNÍ chyby v logu, ne podle té,
  která aplikaci shodila.** Tak padal seznam chatů: `consumeWindowInsets(PaddingValues(bottom =
  scaffoldPadding…))`, jehož hodnota se při rozložení mění. Pravidlo: do modifikátorů insetů
  dávat POPIS insetů (`WindowInsets.navigationBars.union(WindowInsets.ime)`,
  `ScaffoldDefaults.contentWindowInsets.only(…)`), který je mezi rekompozicemi pořád stejný,
  ne čísla odvozená z paddingu. Reprodukce na emulátoru Fold: seznam chatů na složeném
  displeji, `adb emu unfold` / `fold` v cyklu (bez opravy pád do šesti cyklů).

- **Obrazovka konverzace je rozdělená do částí a nová logika patří do té své, ne do
  `ConversationScreen`.** Funkce bývala v dexu na hraně: s 275 registry otevření každého chatu
  padalo na `NullPointerException … Composer.sourceInformationMarkerStart` na `null` v
  `rememberComposableLambda` u `bottomBar = {`. **Mechanismus (D8 nebo ART) ověřený NENÍ** —
  jisté je jen, že vytažení kódu pomohlo. Před rozdělením měla 274 registrů a 5 172 řádků, po
  něm 144 a 725; nejvíc (50) ušetřilo sloučení dvaceti tří callbacků do `ConversationNavigation`,
  protože každý parametr composable funkce stojí registr i s příznaky `$changed`. Mapa částí
  a pravidla mezi nimi: „Skladba obrazovky" v `docs/poznatky/konverzace-zobrazeni.md`. Kdo
  přidává do obrazovky, ať aplikaci po změně otevře v chatu, ne jen přeloží; počet registrů
  ukáže `dexdump -d` nad APK (`registers` u `ConversationScreen(`).

- **Parametry funkce se nepřidávají donekonečna — souvisejí-li, jdou do skupiny.** `MessageInput`
  dorostl na 79 parametrů, `VideoPage` na 38, `ViewerContent` na 33 a nikdo si toho nevšiml,
  protože každý přidal „jen jeden". Víc než 20 shodí `check` (`verifyParameterCount`); kdo
  přidává parametr k funkci, která jich má přes ~15, podívá se dřív, ke které skupině patří: stav spolu se svými callbacky (`ReplyControls`,
  `RecordingControls`, `ChatListNavigation`, `PickerSelection`), volitelná funkce jako celek
  `null` místo tří nullable callbacků. Vzor a zdůvodnění `data class` (strong skipping, rovnost
  instancí) je v KDoc `ConversationNavigation`. **Skupina se seznamem, sealed typem nebo typem
  z `:client:core` potřebuje `@Immutable`** (s držákem stavu `@Stable`): bez anotace ji
  kompilátor vede jako nestabilní, porovnává identitou a nová instance z každé rekompozice
  funkci nedovolí přeskočit — tam, kde dřív stejná instance seznamu prošla. Proměnlivý objekt
  (`Animatable`) do skupiny nepatří vůbec. Ověří se reportem (`-PcomposeReports`, viz
  `docs/poznatky/vykon.md`): nová skupina má vyjít `stable`.

- **`adb shell input text` do pole BEZ fokusu jde jako klávesnice do obrazovky a mezera
  (`keyevent 62`) „ťukne" na zaostřené tlačítko.** Formulář ankety se tak zavřel přes Zavřít,
  pak šipkou zpět odešla i konverzace a zbytek textu skončil v hledání seznamu chatů — bez
  jediného cizího příkazu (ověřeno v logu `adbd service requested`). Před psaním ověřit
  `focused="true"` ve výpisu a mezery psát jako `input text "%s"`, ne `keyevent 62`.
  **Na přetíženém emulátoru `input text` ztrácí KONEC textu**: systém zahodí vložené události
  starší než 10 s (`Dropped event because it is stale` v logu) a `input text` je razítkuje
  všechny předem. Chyba aplikace to není; psát po pár znacích.

- **`ExtendedFloatingActionButton(text = …, icon = …)` popisek skrývá před čtečkou obrazovky**
  (`clearAndSetSemantics` uvnitř animace rozbalení) — tlačítko pak ve stromu přístupnosti nemá
  žádný název a `uiautomator` ho podle textu nenajde. Obsahová varianta
  (`ExtendedFloatingActionButton(onClick) { Icon; Spacer; Text }`) popisek nese.

- **„JDK home directory does not exist" s cestou do `actions-runner` = zastaralá konfigurační
  keš**, ne chyba v projektu. Keš si pamatuje cestu k toolchainu z doby, kdy na Macu běžel
  GitHub runner; po jeho smazání build padá dřív, než se dostane ke kódu. Pomůže
  `./gradlew --stop` a `rm -rf .gradle/configuration-cache`.
- **Neexistující úloha Gradle shodí CELÉ volání, i úlohy před ní.** `assembleDebug
  :client:shared:neexistujici -q | grep "^e:"` nevypíše nic (chyba je „Task … not found", ne
  `e:`) a APK zůstane STARÉ — na emulátoru pak chybí právě přidaná změna a vypadá to jako chyba
  v kódu. Po sestavení k ověření kontrolovat čas APK nebo `BUILD SUCCESSFUL`.
- **CI na pull requestech NIC NEPŘEKLÁDÁ** — kontroluje jen poznámky k vydání. Po #284 se `main`
  nedal přeložit (`vararg` hodnotové třídy `Color`: „Prohibited vararg parameter type") a nikdo
  si toho nevšiml, dokud další větev nestavěla. Před pushem proto aspoň
  `./gradlew :client:androidApp:assembleDebug`; u parametru s hodnotovou třídou (`Color`, `Dp`)
  seznam, ne `vararg`.
- **Test ViewModelu MUSÍ nastavit `Dispatchers.setMain`** (a v `@AfterTest` `resetMain`).
  `viewModelScope` běží na `Dispatchers.Main`: na JVM žádný není a lifecycle vezme prázdný
  kontext, takže test projde, kdežto na iOS je `Main` skutečná hlavní fronta, kterou `runTest`
  na hlavním vlákně nevyprázdní — ViewModel se nerozběhne a test visí do minutového limitu
  (`UncompletedCoroutinesError`). Zelené `testAndroidHostTest` proto o iOS nic neříká.
  S `StandardTestDispatcher` jako `Main` si `runTest` převezme jeho plánovač a ViewModel jede
  na tomtéž virtuálním čase (viz `NewChatViewModelTest`).
- **Blokové komentáře v Kotlinu se VNOŘUJÍ, takže `/*` uvnitř KDoc ho nechá neuzavřený.**
  Cesta s hvězdičkou (`core/ai/` + `*Prompt.kt`) napsaná do KDoc otevře vnořený komentář,
  závěrečné `*/` zavře jen ten a zbytek souboru zmizí v komentáři. Hlásí se to zavádějícím
  „Unresolved reference" na symbol o desítky řádků níž a teprve pak „Unclosed comment" na konci
  souboru — hledat se přitom musí v komentáři, kterého si nikdo nevšimne. Glob se do komentáře
  proto nepíše.
- **`androidResources { enable = true }`** v KMP modulu s prostředky (`:client:resources`) je povinné, jinak se composeResources nezabalí
  a čtení stringu spadne až za běhu na „No instrumentation registered!".
- **iOS framework musí být `isStatic = true`** — TDLib je statická knihovna, jinak link padne na
  undefined symbols `_td_*`.
- **Odběr update flow musí běžet dřív než první request.** TDLib odpovídá updatem, ne návratovou
  hodnotou; opačné pořadí zahodí první `AuthorizationState` a přihlášení zůstane viset.
- **`loadChats` vrací `Ok`, ne data.** Chyba 404 znamená „konec seznamu", ne selhání. Samotné
  chaty přicházejí **updaty**, ne návratovou hodnotou — viz `TdlChatRepository`.
- **`ChatPosition.order == 0` znamená, že chat ve složce NENÍ.** Musí ze seznamu složky zmizet,
  jinak tam zůstane viset po archivaci. Chat sám ale známý zůstává — viz „Archiv".
- **Material Symbols z Google Fonts potřebují úpravu**, než je uložíš do `composeResources`:
  vyhoď `android:tint="?attr/…"` a nahraď `@android:color/white` literální barvou. Resource
  reference commonMain parser nezná. `material-icons-core` **nepoužívej** — Google ho neudržuje,
  poslední verze je 1.7.3.
- **Repository seznamu chatů MUSÍ vznikat eagerně** (`createdAtStart = true` v Koinu). Odběry
  chat updatů se registrují v konstruktoru; při líné konstrukci vznikne až při první kompozici
  seznamu, což je (naměřeno) ~480 ms po `AuthorizationStateReady` — a první dávku `UpdateNewChat`
  TDLib do té doby rozešle do prázdna. Seznam pak zůstane navždy prázdný.
- **Updaty samy nestačí — po `loadChats` se musí přečíst stav z lokální DB** (`getChats` +
  `getChat`). Updaty chodí jen na ZMĚNY; bez tohohle seedu je seznam závislý na načasování odběru.
- **Updaty k jednomu chatu chodí v RŮZNÝCH korutinách a pořadí mezi nimi neplatí.** Každý
  odběr (`newChatUpdates`, `chatPositionUpdates`, `chatLastMessageUpdates`…) je vlastní `launch`,
  a `upsert` si před zápisem ještě dočítá ověření a fórum. `UpdateChatPosition` poslaný TDLib až
  po `UpdateNewChat` tak doběhl dřív, `upsert` ho přepsal prázdnými pozicemi z objektu chatu
  a skupina čerstvě převedená na supergroupu v seznamu chyběla až do restartu; poslední zpráva
  k ještě neznámému chatu se zahodila (`edit`). Proto u NOVÉHO chatu `upsert` pozice z updatů
  slévá a nová poslední zpráva si neznámý chat dočte (`edit(fetchUnknown = true)`).
- **Každá nová routa se musí zaregistrovat v `NavSavedStateConfiguration`.** `NavKey` je otevřená
  polymorfní hierarchie, kotlinx.serialization si podtypy neodvodí. Chybějící registrace projde
  kompilací a **spadne až za běhu** při první kompozici shellu.
- **Top-level `val` se inicializují v pořadí deklarace.** `NavSavedStateConfiguration` má proto
  `SerializersModule` vložený inline — odkaz na `private val` deklarovanou níž by byl `null`.
- **`mapNotNull` neexistuje pro primitivní pole.** `Chats.chatIds` je `LongArray`, je potřeba
  `.toList()`. Totéž u `Messages.messages`, kde jsou navíc prvky v Kotlin metadatech nullable
  (Java signatura je `Message[]`) → `.filterNotNull()`.
- **Výšku obrázku nepočítej `aspectRatio`, když ji chceš zastropovat.** `aspectRatio` si výšku
  dopočítá ze šířky a předřazené `heightIn(max = …)` neuplatní — svislý snímek obrazovky pak vyroste
  přes celou bublinu a popisek pod ním se překryje. Správně: `BoxWithConstraints` + explicitní
  velikost s `coerceAtMost` (`photoFrame`, viz „Rámeček fotky v bublině"). A **se stropem výšky
  musí jít dolů i šířka** — jinak strop výšku jen usekne a `Crop` z fotky ukrojí kraje.
- **`height(IntrinsicSize.Min)` nesmí obalit nic se `SubcomposeLayout`** (`BoxWithConstraints`,
  lazy seznamy). Intrinsické míry neumí a aplikace spadne až při vykreslení — u náhledu odkazu
  s velkým obrázkem to shodilo každý chat, kde takový náhled byl vidět. Proužek „přes celou výšku"
  se proto kreslí `drawBehind` za obsahem, ne jako `Box` s `fillMaxHeight`.
- **`implementation` závislosti z `:client:shared` nejsou tranzitivní.** Co používá vstupní bod
  (`:client:androidApp`), musí být deklarované i tam — narazilo se na tom u Kermitu.
- **Chyby ze stavu musí mít kam vyjít.** `ConversationScreen` má `SnackbarHost`; dřív se
  `state.error` vykreslovalo jen u prázdné konverzace, takže neúspěšná reakce nebo odeslání
  zmizely bez jediné stopy.
- **Barvy avatarů drž jako dvojice pozadí + popředí.** `onPrimaryContainer` na `tertiaryContainer`
  je kontrastní nesmysl, který M3 negarantuje.
- **ViewModel na detailu MUSÍ mít klíč obsahující id** (`koinViewModel(key = "conversation-$chatId")`).
  ViewModel se cachuje podle klíče a Koin u cachované instance **ignoruje nové `parameters`** —
  konstantní klíč tedy znamená, že druhá otevřená konverzace dostane ViewModel té první i se
  zprávami, a neprojeví se to chybou, jen špatným obsahem. Od dekorátoru ViewModelů (viz
  „Zásobník pro každou sekci") má každá položka vlastní úložiště a klíč je jen pojistka — ale
  obrazovka mimo `NavDisplay` by ji zase potřebovala.
- **`Info.plist` musí mít `CADisableMinimumFrameDurationOnPhone = true`.** Compose Multiplatform
  to kontroluje při startu a bez klíče aplikace na iOS hned spadne na `IllegalStateException`
  (jinak by na 120Hz displejích běžela na 60 snímků). Chyba je vidět jen v konzoli, na simulátoru
  se aplikace prostě zavře.
- **Systémové frameworky iOS se do Xcode projektu přidávat nemusí.** Kotlin/Native zapíše použité
  frameworky (PhotosUI, UniformTypeIdentifiers, CoreImage…) do objektových souborů a linker je
  přidá sám, i u statického frameworku. Ověření: `otool -L` na `Plume.app/Plume.debug.dylib`
  (v Debug je kód v `.debug.dylib`, ne v hlavní binárce). Sestavení aplikace bez podpisu:
  `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator
  -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build`.
  **Takhle sestavená aplikace ale v simulátoru hned spadne** na „Klíč se nepodařilo uložit do
  Keychainu (-34018)" (bez podpisu nemá oprávnění ke Klíčence). Ke spuštění podepsat místně:
  `CODE_SIGN_IDENTITY=- CODE_SIGN_STYLE=Manual DEVELOPMENT_TEAM= PROVISIONING_PROFILE_SPECIFIER=`.
- **Release link iOS frameworku trvá desítky minut.** Kotlin/Native pouští LTO nad celou statickou
  TDLib; naměřeno 15+ minut na M-series. Debug link je ~5 minut. Do CI to patří jako samostatný,
  cachovaný krok — ne do smyčky, kterou čeká vývojář.

## Co ještě není vyřešené

**Seznam se vede ve dvou živých dokumentech, ne tady.** Tenhle soubor se čte při každé session,
takže by v něm rostoucí seznam úkolů jen ubíral místo poučením.

- **[docs/roadmap.md](docs/roadmap.md)** — přes celou aplikaci: co chybí před vydáním, čím je
  iOS pozadu, které obrazovky a sekce nejsou (archiv, hledání, profil, nová konverzace), a taky
  co právě leží rozpracované v pracovním adresáři.
- **[docs/roadmap-konverzace.md](docs/roadmap-konverzace.md)** — podrobný seznam pro detail chatu
  i zásobník menších nálezů z čtení kódu.

**A ani ve třetím.** README mělo vlastní odstavec „Není hotové" a přestal se aktualizovat: tvrdil,
že chybí přehrávání videa a zvuku, otevírání dokumentů, úprava zprávy, skok na první nepřečtenou
a perzistence nastavení — všechno dávno hotové. Seznam úkolů vedený na dvou místech se udržuje
na jednom a to druhé tiše lže. README proto říká, co aplikace umí, a na roadmapy odkazuje;
číslování fází z něj zmizelo ze stejného důvodu (README hlásilo fázi 7, CLAUDE.md fázi 6).

Obojí je živý dokument: co se udělá, odtud zmizí a poučení z toho patří do `docs/poznatky/` (obecné sem).
