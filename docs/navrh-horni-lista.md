# Návrh: horní lišta patří scéně, ne obrazovce

> **Stav:** návrh, nic z toho není v kódu. Vychází z kódu po VladimirTintera/plume#67 (hlavička
> bočního panelu v pásu s konverzací). Rozhodnutí, že tímhle směrem chceme jít, padlo
> 25. 11. 2026; podoba API a pořadí kroků jsou otevřené.

Vedle sebe stojí konverzace a její boční panel (profil, kalendář, úprava kanálu, pozvánky…).
Boční panel je **podčást konverzace**, ne samostatná obrazovka, a nahoře má být **jedna lišta**:

```
← konverzace (avatar, název, stav) | nadpis panelu · akce panelu · akce konverzace (hovor, panel, ⋮)
```

Dnes si lištu kreslí každá obrazovka sama a shell z nezávislých lišt skládá jednu řadu dodatečně.
Cíl je obrátit to: **obrazovka jen popíše, co v liště chce, a nakreslí ji scéna** podle toho, kde
obrazovka zrovna stojí (sama na telefonu, panel vedle konverzace, podstránka Nastavení).

## Proč to dnešní stavba neunese

Každá obrazovka má `Scaffold` + `TopAppBar` a o svém okolí se dozvídá z lokálů. Aby řada vypadala
jako jedna, přibylo postupně:

| Mechanismus | K čemu je |
|---|---|
| `Panes.headerOutside`, `PaneHeader`, `PaneHeaderHost`, `HeaderOutsideShape` | hlavička hlásí kartě výšku, karta se ořízne až pod ní |
| `paneScaffoldColor`, `PaneBody`, `paneHeaderOutside()` | obrazovka si podle polohy hlavičky kreslí plochu jinak |
| `PaneBand.detailCardTop`, `LocalPaneHeaderMinHeight` | měření v okně, aby karta panelu lícovala s kartou konverzace |
| `publishToPaneBand`, `PaneBandActions()` | akce konverzace se přenášejí do lišty JINÉ obrazovky |
| `inPaneBand()`, `paneRoot`, `standsOnDetail`, `besideOwnConversation` | každá lišta si sama rozhoduje o šipce a nadpisu |
| `WindowControlsSpace`, `WindowControlsEndSpace`, `verifyWindowControls` | každá lišta sama nechává místo pro tlačítka okna |

Přes sto odkazů ve `features/` a `core/adaptive`. **Každé pravidlo musí dodržet každá lišta zvlášť
a nic kromě tlačítek okna to nehlídá.** Proto úprava kanálu a kalendář skončily s druhou lištou
v kartě, zatímco profil ne (opraveno v #67 další vrstvou téhož). Další obrazovka v bočním panelu
by chybu zopakovala.

Druhá past je **kompoziční kontext**. `publishToPaneBand` skládá akce konverzace v kompozici
bočního panelu, takže `koinViewModel` v nich sáhne do úložiště ViewModelů panelu a vrátí jinou
instanci (ořez navrhované fotky se kvůli tomu neukázal). Dnes se to obchází tím, že konverzace
předá ViewModely hotové; obecně to vyřešené není.

## Varianty

**A. Jedna řada nad celou scénou, kreslená shellem.** Shell nakreslí pruh přes celou šířku okna
a rozdělí ho na úseky podle šířek panelů; obrazovky do úseků posílají obsah.
- Plus: řada je jedna doslova, nic nelícuje „náhodou".
- Minus: pruh leží MIMO položky `NavDisplay`, takže s nimi nejezdí. Přechody mezi obrazovkami,
  náhled gesta zpět, odjezd zavíraného panelu (`rememberWithLeavingExtras`) i táhlo šířky by se
  musely postavit znovu pro lištu zvlášť a synchronizovat s animovanými panely
  (`ThreePaneScaffold` šířky ven nedává, panely mají `clipToBounds`). Veškerý obsah lišty by se
  skládal v cizí kompozici.

**B. Úsek lišty patří panelu, kreslí ho dekorátor položky, obrazovka ho jen popisuje.**
Lištu kreslí `PaneCard` (dekorátor položky, který už dnes rozhoduje o kartě a hlavičce nad ní)
z popisu, který mu obrazovka předá. Úseky stojí v jedné výšce, protože je kreslí tentýž kód se
stejnou geometrií, ne proto, že se změří.
- Plus: lišta zůstává součástí položky, takže **přechody, gesto zpět a odjezd panelu fungují
  jako dnes zadarmo**. Dekorátor běží uvnitř dekorátorů ukládání stavu a ViewModelů
  (`entryDecorators + paneCard` v `AppShell`), takže úsek má úložiště ViewModelů své položky.
- Minus: přes hranici panelu jde pořád jedna věc, akce konverzace nad bočním panelem. Ta
  potřebuje zachycený kontext (níž), ale je to jediné takové místo a řeší ho shell, ne obrazovky.

**C. Nechat to, jak je, a hlídat kontrolou** (např. `verifyPaneBandActions`). Nejlevnější, ale
jen přidá další pravidlo k ostatním; geometrie i rozhodování o šipce zůstanou rozsypané.

**Doporučení: B.** Odstraní rozhodování z obrazovek a nerozbije to, co dnes na navigaci funguje
nejlíp (přechody a gesto zpět).

## Jak by B vypadalo

### Popis lišty

Obrazovka místo `TopAppBar` zavolá popis. Nic nekreslí, jen ho předá dekorátoru své položky:

```kotlin
PaneTopBar(
    title = { Text(…) },                  // composable: konverzace tam má avatar, stav, hledání
    subtitle = { Text(…) },               // volitelné
    navigation = PaneNavigation.Back(onBack), // nebo None; scéna ji smí potlačit
    actions = { SaveButton(…) },          // vlastní akce obrazovky
    trailingActions = { … },              // jen konverzace: akce, které stojí u kraje okna
    size = PaneTopBarSize.Medium,         // na jednom panelu: Small / Medium / Large (flexibilní)
)
```

`title` musí být slot, ne text: hlavička konverzace nese režim výběru zpráv, pole hledání
v chatu, avatar se stavem spojení. Seznam chatů s pilulkou hledání (`AppBarWithSearch`) zůstane
mimo, má vlastní tvar i chování.

### Pravidla scény (na jednom místě, v `core/adaptive`)

- **Boční panel vedle konverzace:** bez šipky (zavírá ho přepínač panelu), za vlastní akce přidá
  `trailingActions` konverzace. Kořen profilu vedle vlastní konverzace bez nadpisu.
- **Konverzace vedle bočního panelu:** `trailingActions` vynechá, kreslí je panel.
- **Jeden panel (telefon, úzké okno):** lišta podle `size`, se sbalováním při posunu. Dekorátor
  dá obsahu `nestedScroll` přes lokál (`LocalPaneTopBarScroll`), obrazovka ho připojí ke svému
  seznamu jako dnes `profileSubpageScroll`.
- **Vedle sebe:** vždy malá lišta 64 dp (dnes ručně v `ProfileSubpageTopBar`).
- **Místo pro tlačítka okna, insety a barva plochy** jen tady. `verifyWindowControls` pak hlídá
  jedno místo místo třiceti.
- **Šipka, nadpis a pás v Nastavení** stejnými pravidly (sekce | podstránka), jen bez akcí
  konverzace.

Obrazovka se tím nedozví, kde stojí: zmizí `besideOwnConversation`, `inPaneBand`, `paneRoot`
jako parametr obrazovky, `paneHeaderOutside()` v obsahu a `PaneBody`. Plochu pod lištou dá
dekorátor (obsah dostane jen odsazení, jako od `Scaffold`).

### Struktura se nesmí měnit se šířkou okna

Dnes `PaneHeader` drží stejnou strukturu na telefonu i vedle sebe, aby po rozložení Foldu
hlavička nepřišla o stav (rozepsané hledání v chatu). Proto **lištu kreslí dekorátor VŽDY**,
i na telefonu — ne „na telefonu obrazovka, vedle sebe dekorátor". Mění se jen hodnoty (velikost,
šipka, akce navíc), ne místo v kompozici. Platí i Gotcha z `CLAUDE.md` o insetech při změně
scény: modifikátory insetů dostávají popis insetů, ne čísla.

### Akce konverzace nad bočním panelem: zachycený kontext

Jediná věc, která jde přes hranici panelů. Konverzace při popisu lišty zachytí svůj kontext
(`currentCompositionLocalContext`) a dekorátor bočního panelu ji vykreslí uvnitř
`CompositionLocalProvider(context) { trailingActions() }`. Akce tak mají úložiště ViewModelů,
barvu chatu i účet konverzace, a `koinViewModel` v nich vrátí správnou instanci — dnešní obcházka
„předej ViewModel hotový" odpadne.

- **Ověřit:** jestli zachycený `LocalSaveableStateRegistry` nebo `LocalLifecycleOwner` nevadí
  `rememberSaveable` a efektům v akcích (otevřená nabídka ⋮ by měla žít s panelem, ne
  s konverzací). Případně z kontextu přenést jen vybrané lokály.
- Akce kreslí jen karta zobrazené položky (`LocalPaneResizeOwners`), jako dnes — odjíždějící
  panel by je zdvojil i se zkratkou přepínače.

### Co ověřit dřív, než se začne převádět

Prototyp na jedné dvojici (konverzace + profil) a měření:
1. **Gesto zpět** z podstránky v panelu a z konverzace s otevřeným panelem (náhled musí ukázat
   lištu předchozí položky).
2. **Odjezd zavíraného panelu** — lišta odjede s kartou, akce konverzace přeskočí zpět do její
   lišty bez probliknutí (dnes je přeskočí v jednom snímku; s B by šlo prolnout).
3. **Rozložení / složení Foldu** v cyklu s otevřeným hledáním v chatu: stav hledání přežije,
   aplikace nespadne (Gotcha o insetech).
4. **Barva chatu**: lišta leží na ploše okna v barvách aplikace (`LocalPanePlaneScheme`), obsah
   karty v barvě chatu. Zachycený kontext nesmí lištu přebarvit.
5. **Registry**: `ConversationScreen` byla na hraně dexu (Gotchas v `CLAUDE.md`). Přesun lišty
   ven ji spíš uleví, ale počet registrů po změně porovnat.

## Postup

1. **Prototyp a rozhodnutí** — `PaneTopBar` + kreslení v `PaneCard` pro profil vedle konverzace,
   body z „Co ověřit". Když gesto zpět nebo Fold nevyjde, zpátky k variantě C.
2. **Boční panel v Chatech** — profil, kalendář, `ProfileSubpageTopBar` (osm podstránek). Zmizí
   `PaneBandActions`, `inPaneBand`, `LocalPaneHeaderMinHeight`, `besideOwnConversation`,
   `PaneBody` u těchto obrazovek.
3. **Konverzace a seznam témat** — `trailingActions`, zachycený kontext. Zmizí
   `publishToPaneBand`, `PaneBand.detailCardTop`, ruční předávání ViewModelů do akcí.
4. **Nastavení** — sekce a podstránky stejnými pravidly.
5. **Kontrola** — nová `SourceCheckTask`: obrazovka, která má položku s rolí panelu, nevolá
   `TopAppBar` přímo. Zbylé obrazovky přes celou plochu (prohlížeč, editory, dialogy) si lištu
   nechají.

Každý krok je vlastní PR; aplikace mezi nimi funguje (staré a nové lišty mohou chvíli žít vedle
sebe, protože geometrie úseku je táž jako dnešní hlavičky nad kartou).

## Otevřené otázky

- **Úzký boční panel (360 dp):** nadpis, vlastní akce panelu a 2–3 akce konverzace. Dnes se
  nadpis zkrátí. Stojí za to akce panelu nad určitý počet schovat do ⋮ (pravidlo scény), nebo
  nechat výpustku?
- **Profil člena nad konverzací skupiny** má vlastní ⋮ vedle ⋮ konverzace. Sloučit do jedné
  nabídky se skupinami („Člen" / „Skupina")?
- **Zpátky z podstránky v panelu** je bez šipky jen gestem / Esc. Stačí to, nebo má nadpis
  podstránky fungovat jako drobečková navigace (ťuknutí vrátí o úroveň)?
