# Systémová oznámení od Telegramu

Poznatky k jedné oblasti aplikace. Obecná pravidla projektu a rejstřík ostatních souborů jsou
v [CLAUDE.md](../../CLAUDE.md); poučení z opravy v téhle oblasti patří sem.

Co Telegram účtu sděluje mimo chaty a TDLib předepisuje ukázat: oznámení od služby
(`updateServiceNotification`), nové podmínky služby (`updateTermsOfService`) a zmrazený účet
(`updateFreezeState`), a k tomu doporučené akce (`updateSuggestedActions`). Dlouho je nikdo
neodebíral. Drží je `AccountNotices` (`core/telegram`),
dialogy kreslí `AccountNoticesHost` nad přihlášenou částí aplikace (`App`), zmrazení je karta
nahoře v seznamu chatů (`FrozenAccountCard`).

## Proč eagerně a v `core/telegram`

- **Update bez odběratele se ztratí** — wrapper je drží v `SharedFlow` bez paměti. Proto
  `TdlAccountNotices` vzniká se startem aplikace (`createdAtStart`) a drží POSLEDNÍ stav;
  obrazovky si ho čtou, kdy chtějí.
- **Zmrazení TDLib pošle hned při načtení účtu**, ze svého binlogu (`UserManager`, klíč
  `freeze_state`), dřív než se cokoli složí — a znovu jen při změně. Odběr z obrazovky by ho
  minul a karta by se do restartu neukázala.
- **Podmínky si TDLib stáhne po startu** (`TermsOfServiceManager`, dotaz hned a pak podle
  `expires`) a pošle updatem. Nepřijaté pošle po každém startu znovu.
- **Oznámení od služby chodí jednou.** Kdo ho neodebírá, nedozví se ho nikdy.
- Po odhlášení (`holder.replaced`) se všechno zahodí — nový účet nic z toho starého nedědí.

## Oznámení od služby

- **Popup s textem**, odkazy klikací (`RichText`). Bývá to holý text, výjimečně médium
  s popiskem — médium se v popupu neukazuje, popisek ano.
- **`AUTH_KEY_DROP_…` = „Zrušit" a „Odhlásit"** (předepisuje TDLib). Odhlásit znamená zahodit
  lokální data (`destroy`, u nás `restartLogin`), **ne `logOut`**: klíč relace server zahodil
  a `logOut` by s ním neprošel.
- Víc oznámení najednou jde po jednom, od nejstaršího; oznámení má přednost před podmínkami
  (bývá to bezpečnost relace).

## Podmínky služby

- **Popup jen se `showPopup`.** Bez něj Telegram blokující dialog nechce a nevnucujeme ho.
- **Ťuknutí mimo ani zpět ho nezavřou** (blokující), ale **odmítnout tu nejde, jen odložit**
  („Teď ne", do příštího startu). Odmítnutí podle TDLib znamená smazat účet (`deleteAccount`
  s důvodem „Decline ToS update") — nevratný krok, který do dialogu s dlouhým právním textem
  nepatří. Kdo chce účet smazat, udělá to vědomě jinde (Soukromí účtu → Smazat účet hned).
- **Dlouhý text se v dialogu posouvá**, tlačítka zůstávají vidět. Věková hranice
  (`minUserAge`) je nad textem.
- Chyba přijetí zůstane v dialogu (věta, ne hláška TDLib); dialog se nezavře.

## Zmrazený účet

- **Karta v chybové roli nahoře v hlavním seznamu** (ne v archivu), nad novým přihlášením
  i aktualizací. Nedá se zavřít — zmizí, až TDLib ohlásí, že účet zmrazený není. Datum smazání
  (`deletionDate`) a „Odvolat se" (`appealLink`), když ho Telegram dá.
- **Odkaz na Telegram otevírá aplikace sama** (`rememberNoticeLinkOpener` → `LinkOpenRequests`,
  tatáž cesta jako odkaz z jiné aplikace). Odvolání vede na `@SpamBot`; přes `UrlOpener` se
  otevřel Chrome (ověřeno), takže by uživatel skončil v prohlížeči místo u bota. Totéž pro
  odkazy v textu oznámení a podmínek.
- Akcent přes `accentOn` (paleta se zvýšeným kontrastem), jako karta s aktualizací.

## Ověření

- `AccountNoticesViewModelTest`: pořadí oznámení, odhlášení přes `destroy`, podmínky bez popupu,
  chyba přijetí a „Teď ne".
- **Na emulátoru sondou** (podstrčený stav v `TdlAccountNotices`, NECOMMITUJE se): karta
  zmrazení s datem a odvoláním, oznámení s „Rozumím", oznámení `AUTH_KEY_DROP` se „Zrušit"
  a „Odhlásit" (na Odhlásit se neťukalo — zavřelo by skutečný účet), dlouhé podmínky s věkovou
  hranicí, „Teď ne", „Odvolat se" otevřelo Spam Info Bota v Plume.
- **Skutečný update od serveru ověřený NENÍ** — ani jedno z toho se na testovacím účtu
  vyvolat nedá. Přijetí podmínek se nezkoušelo vůbec (sonda by poslala vymyšlené id).

## Doporučené akce

`updateSuggestedActions` nese přidané a odebrané akce; po startu TDLib pošle všechny aktuální
jako přidané. Drží je tentýž eagerní `AccountNotices` (`suggestedActions`), kreslí je karta
nahoře v hlavním seznamu (`SuggestedActionCard`) — **vždy jen první**, aby rady nevytlačily chaty.

- **Plume nabízí jen to, co umí splnit, a neprodává**: ověř heslo, ověř číslo, nastav heslo,
  přihlašovací e-mail, profilová fotka, datum narození, archivace chatů od neznámých a vlastní akce
  od Telegramu (`Custom`, s odkazem). Premium, hvězdy, dárky, přístupové klíče (přidat je cizí
  klient nemůže, viz `docs/poznatky/nastaveni.md`), převod skupiny na kanál a nápověda k fajfkám
  se vynechávají (`toDomain`, `when` bez `else` — nová akce v TDLib
  rozbije překlad a musí se rozhodnout, ne tiše zmizet).
- **„Teď ne", „Pamatuji", „Ano, je" schovají akci na serveru** (`hideSuggestedAction`), takže
  se neukáže ani v jiném klientovi. Ze seznamu ji vyřadí až update, který TDLib pošle potom —
  kdyby server schování odmítl, karta zůstane. `hideSuggestedAction` chce zpátky PŘESNĚ objekt
  od TDLib, repository si ho ke každé akci pamatuje.
- **Navigace jde přes `AppScreen`** (dvoufázové ověření přibylo jako `TwoStepVerification`,
  úprava profilu už byla): „Nastavit heslo" i „Zapomněl(a) jsem" vedou na dvoufázové ověření,
  „Přidat" fotku na úpravu profilu. Zpět vede po krocích přes Nastavení do Chatů.
- **Archivace od neznámých se zapíná celým nastavením archivu** (ostatní volby se přenesou)
  a po úspěchu se akce schová sama.
- **Přihlašovací e-mail vede na vlastní obrazovku** („Přidat", `AppScreen.LoginEmail`; viz
  `docs/poznatky/nastaveni.md`, „Přihlašovací e-mail"). **Vyžadovaný** e-mail (`canBeHidden ==
  false`, u Telegramu `SETUP_LOGIN_EMAIL_NOSKIP`) má podle dokumentace blokovat celou aplikaci;
  Plume ukáže kartu, kterou nejde schovat, jen splnit — blokovat celou aplikaci by bylo víc, než
  je potřeba, když cesta ke splnění je jedno ťuknutí.
- **„Je … pořád tvoje číslo?"** má „Ano, je" (schová) a „Změnit" (změna čísla, viz
  `docs/poznatky/nastaveni.md`, „Změna telefonního čísla"). Číslo je naformátované od TDLib
  (`displayPhone`, „+420 601 234 567").

**Ověřeno na emulátoru sondou** (podstrčené akce, schování jen lokálně; NECOMMITUJE se): všech
sedm karet, „Ano, je" / „Pamatuji" / „Teď ne" přepínají na další, „Nastavit" otevřelo dvoufázové
ověření a zpět se vrátilo do seznamu, vyžadovaný e-mail bez tlačítek. **Neověřené**: skutečný
update od serveru, schování na serveru a zapnutí archivace (měnilo by nastavení skutečného účtu).

