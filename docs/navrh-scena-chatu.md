# Návrh: seznam | oblast chatu (vlastní scéna Chatů s jednou lištou)

> **Stav:** scéna Chatů je v kódu a na široké ploše výchozí (VladimirTintera/plume#70): kroky 1–3
> v jednom PR. Prototyp měl přepínač ve Vzhledu, po ověření na desktopu a emulátoru Fold zmizel.
> Lišta: `PaneHeader` ve scéně posílá hlavičku do lišty scény (`SceneBar`) se zachyceným kontextem
> položky, obrazovky beze změny; akce konverzace stojí na konci lišty (`PaneBandEnd`). Úklid (krok 4)
> odstranil knihovní šířky a táhlo pro Chaty, měření pásu v okně i poslední cestu, kudy Chaty
> kreslila knihovna. Krok 5: stejná scéna kreslí i Nastavení
> (sekce | podstránka) a knihovna adaptive-navigation3 z projektu zmizela. Krok 6: kontrola
> `verifyPaneHeaders` hlídá místo pro akce konverzace v lištách `PaneHeader` a že hlavička nad
> kartou jde přes `PaneHeader`. Návrh je tím hotový; `PaneTopBar` jako API se ukázal zbytečný,
> stačilo přesměrovat `PaneHeader` do lišty scény. Poznatky:
> `docs/poznatky/navigace.md`, „Vlastní scéna Chatů". Rozhodnutí jít směrem „dva panely, druhý je sám dvousloupcový" padlo 25. 11. 2026.

Chaty dnes stojí na třípanelovém `ThreePaneScaffold` z knihovny (`adaptive-navigation3`):
seznam | konverzace | boční panel (profil, kalendář, úprava kanálu, pozvánky…). Boční panel je
ale **podčást konverzace**, ne třetí rovnocenný panel. Návrh to obrací:

```
┌ seznam ┐ ┌──────────── oblast chatu (jedna scéna) ─────────────┐
│ pilulka│ │ ← konverzace (avatar, název)  │ nadpis panelu · akce · panel · ⋮ │  ← JEDNA lišta
│ řádky  │ │ zprávy                        │ profil / úprava / kalendář        │
└────────┘ └──────────────────────────────────────────────────────┘
```

Rozvržení je **dvoupanelové: seznam | oblast chatu**. Oblast chatu je jedna věc, kterou
kreslí jedna scéna: konverzaci (nebo seznam témat, vlákno, Uložené po chatech), nad ní volitelně
boční sloupec a nad oběma jednu lištu. Scéna sama rozhoduje, jestli boční sloupec ukáže, jak ho
animuje a co je v liště; obrazovky jen popíšou svůj obsah.

## Proč

### Bojujeme s třetím panelem knihovny

Velká část `core/adaptive` a shellu existuje jen proto, že `ThreePaneScaffold` počítá se třemi
rovnocennými panely (podrobně `docs/poznatky/navigace.md`):

- **Prázdný třetí sloupec:** scaffold se třemi oddíly rozbalí třetí panel i prázdný, proto se
  třetí oddíl zapíná jen s extra položkou navrchu.
- **Odjezd panelu:** zavřený panel by zmizel problikem, `rememberWithLeavingExtras` drží
  odcházející položky 600 ms na začátku zásobníku.
- **Profil jiného chatu:** po přepnutí chatu by v panelu visel starý profil (scéna kreslí POSLEDNÍ
  položku role), `coveredExtras` a `followProfile` proto přepisují zásobník.
- **Gesto zpět z profilu** během tahu neukáže zavírání panelu: předchozí stav se počítá s toutéž
  direktivou tří oddílů.
- **Táhlo** knihovny funguje jen mezi dvěma rozbalenými panely, na profil je vlastní (`PaneResize`).
- **Lišta:** každá obrazovka kreslí svůj `TopAppBar` a shell z nich skládá jednu řadu dodatečně.

### Lišta se skládá z nezávislých kusů

| Mechanismus | K čemu je |
|---|---|
| `Panes.headerOutside`, `PaneHeader`, `PaneHeaderHost`, `HeaderOutsideShape` | hlavička hlásí kartě výšku, karta se ořízne až pod ní |
| `paneScaffoldColor`, `PaneBody`, `paneHeaderOutside()` | obrazovka si podle polohy hlavičky kreslí plochu jinak |
| `PaneBand.detailCardTop`, `LocalPaneHeaderMinHeight` (#67) | měření v okně, aby karta panelu lícovala s kartou konverzace |
| `publishToPaneBand`, `PaneBandActions()` (#67) | akce konverzace se přenášejí do lišty JINÉ obrazovky |
| `inPaneBand()` (#67), `paneRoot`, `standsOnDetail`, `besideOwnConversation` | každá lišta si sama rozhoduje o šipce a nadpisu |

Přes sto odkazů. Každé pravidlo musí dodržet každá lišta zvlášť a nic to nehlídá — proto úprava
kanálu a kalendář skončily s druhou lištou v kartě (#67). A `publishToPaneBand` skládá akce
konverzace v kompozici bočního panelu, takže `koinViewModel` v nich vrátí ViewModel panelu
(obchází se to předáním hotových ViewModelů).

## Zvážené varianty

**A. Jedna řada nad celou scénou, kreslená shellem** (nad dnešním třípanelem). Pruh mimo položky
`NavDisplay` s nimi nejezdí: přechody, gesto zpět, odjezd panelu i táhla by se musely stavět
znovu a synchronizovat s animovanými šířkami panelů, které knihovna ven nedává. Zamítnuto.

**B. Úsek lišty kreslí dekorátor panelu (`PaneCard`) z popisu od obrazovky** — doporučení první
verze návrhu (#68). Levnější: nemění navigaci a přechody zůstanou, jak jsou. Ale nechává všechny
obcházky třetího panelu z „Bojujeme s třetím panelem" a akce konverzace nad bočním panelem dál
jdou přes hranici dvou panelů. **Záložní varianta**, kdyby prototyp vlastní scény nevyšel.

**C. Vnořená navigace: oblast chatu jako položka s vlastním `NavDisplay` a vlastním zásobníkem**
pro boční panel. Doslovné „druhý panel si své položky hlídá sám" a stav panelu by patřil
konverzaci. Zamítnuto, protože:
- **dva zásobníky** — u každého `onOpen…` (profil, člen, sdílená skupina, prohlížeč, jiný chat)
  by se řešilo, do kterého patří; totéž odkazy zvenčí, notifikace, obnova stavu a víc účtů
  (`inRouteAccount`);
- **telefon a Fold** — na telefonu profil nahrazuje konverzaci; buď by vnitřní zásobník měl vlastní
  jednopanelovou scénu, nebo by se položky při složení stěhovaly mezi zásobníky (ztráta stavu);
- **gesto zpět přes dvě úrovně** `NavigationEventDispatcher` — pořadí obsluhy by se muselo ověřit;
- **každý „detail", který panel má** (konverzace, témata, vlákno, Uložené po chatech, archiv),
  by hostil vnitřní zásobník.

**D. Vlastní scéna Chatů nad JEDNÍM plochým zásobníkem — doporučení.** Navigation3 má na „scéna
si rozvrhne své položky" přímo `SceneStrategy`. Zásobník zůstane, jak je (`ConversationRoute`,
`ChatProfileRoute`, `EditChatRoute`…), jen místo `ThreePaneScaffold` ho v Chatech na širokém okně
nakreslí naše scéna. Výhody C (scéna má oblast chatu celou pod sebou) bez dvou zásobníků.

## Jak by D vypadala

### Strategie a scéna

- **`PaneSceneStrategy`** stojí v `sceneStrategies` před `rememberListDetailSceneStrategy`
  (za `SheetSceneStrategy`, sheet se dál kreslí nad scénou). Vrací scénu jen pro položky Chatů
  a Archivu na širokém okně; Nastavení a telefon propadnou dál, ke knihovně a standardní výměně
  obrazovek. Nastavení může později přejít na stejnou scénu (sekce | podstránka), ale není to
  podmínka.
- **Scéna má JEDEN klíč pro všechny stavy Chatů** — jen seznam, seznam + placeholder, seznam +
  chat, seznam + chat + boční sloupec. Dnes to dělá `shouldHandleSinglePaneLayout = wide`, aby
  přechod mezi nimi byl plynulý a ne výměna scén; vlastní scéna musí totéž, jinak by `NavDisplay`
  každé otevření profilu animoval jako novou obrazovku.
- **Z položek si scéna vezme:** poslední položku seznamu, poslední detail a nad ním boční položky,
  **které k tomu detailu patří** (stejný chat; člen nad skupinou je výjimka, viz otázky). Položky,
  které k detailu nepatří (zakrytý profil jiného chatu), se nekreslí. Úklid zásobníku
  (`coveredExtras`, `followProfile`) může zůstat kvůli zpět a ukládání, ale už není podmínkou
  správného obrazu.
- **Role položek** zůstávají v metadatech (`Panes.list/detail/extra`), jen `extra` se přejmenuje
  na boční (`side`) a scéna ji čte jako „patří k detailu pod ní".

### Rozvržení a animace

- **Seznam | oblast chatu** je obyčejný řádek se dvěma sloupci a táhlem mezi nimi. Kotvy, šířky,
  sbalený seznam, karty a okraje už máme vlastní (`PaneResize`, `ListPane`, `PaneCard`), knihovna
  z toho dnes dodává jen výpočet oddílů a `AnimatedPane`.
- **Oblast chatu** = lišta nahoře a pod ní karta konverzace + karta bočního sloupce s táhlem
  (dnešní `ProfilePane` šířky a kotvy). Otevření a zavření sloupce animuje scéna sama
  (`AnimatedVisibility` / animovaná šířka, specy z `AppMotion`) — **`rememberWithLeavingExtras`
  a „prázdný třetí sloupec" zmizí**, odcházející obsah drží `AnimatedContent`.
- **Výměna obsahu sloupce** (profil → úprava → pozvánky) `AnimatedContent` s klíčem položky,
  stejně jako dnešní výměna obsahu v `AnimatedPane`.

### Co dnes dělá knihovna a scéna musí převzít

Ze zdrojů `adaptive-navigation3` a z naší direktivy (`PaneDirective.kt`):

- **Pant skládačky.** Direktiva nese `excludedBounds` z `windowPosture` (pant) a scaffold panely
  kolem nich rozvrhne, aby hranice ležela na pantu a nic přes něj nevedlo. Vlastní scéna musí
  v pozici kniha (napůl otevřený Fold, svislý pant) dát hranici seznam | oblast chatu, případně
  konverzace | boční sloupec, na pant sama. Pozice stolek (vodorovný pant) se dnes řeší zvlášť
  (`rememberTabletopHinge`) a zůstane.
- **Počet panelů podle plochy obsahu**, ne okna (`contentPaneScaffoldDirective`), a práh tří
  panelů (`THREE_PANE_MIN_WIDTH`) — pro scénu práh, od kdy se boční sloupec vejde vedle
  konverzace, jinak boční položka konverzaci nahradí jako na telefonu.
- **Žádný automatický fokus** při změně panelu (`shouldAutoFocusCurrentDestination = false`):
  scaffold jinak po otevření konverzace dal fokus vstupnímu poli a vyjela klávesnice. Vlastní
  scéna fokus nepřesouvá vůbec — jen to nesmí znovu zavést.
- **`AnimatedPane`**: příjezd a odjezd panelu a výměna obsahu. Nahradí `AnimatedVisibility`
  / `AnimatedContent` se specy z `AppMotion` (pravidlo `verifyMotionSpecs` platí i tady).

### Gesto zpět

Knihovní scéna (`ThreePaneScaffoldScene.content`, `adaptive-navigation3` 1.3.0-beta02, přečteno
ze zdrojového jaru) řeší gesto UVNITŘ scény sama, veřejným API:
`rememberNavigationEventState` + `NavigationBackHandler(isBackEnabled = předchozí stav != null)`,
během gesta (`NavigationEventTransitionState.InProgress`) posouvá stav scaffoldu
`seekTo(progress)` k předchozímu, po dokončení zavolá `onBack()` tolikrát, kolik položek ubude.
Vlastní scéna udělá totéž se svým stavem (je otevřený boční sloupec, který obsah v něm je),
a **během tahu se boční sloupec konečně bude zavírat** — dnes kvůli společné direktivě nejde.
`previousEntries` scény určí, kam zpět vede (jako dnešní `PopUntilContentChange`).

### Jedna lišta

Lištu kreslí scéna nad oběma sloupci; obrazovky ji jen popíšou:

```kotlin
PaneTopBar(
    title = { … },                        // slot: konverzace tam má avatar, stav, hledání, výběr zpráv
    subtitle = { … },                     // volitelné
    navigation = PaneNavigation.Back(onBack), // scéna ji v bočním sloupci vynechá
    actions = { SaveButton(…) },          // vlastní akce obrazovky
    size = PaneTopBarSize.Medium,         // na jednom panelu: Small / Medium / Large (flexibilní)
)
```

**Pravidla jsou ve scéně, ne v obrazovkách:**
- řada je `← detail (nadpis) | nadpis bočního sloupce · jeho akce · akce detailu · přepínač panelu`;
  úseky lícují s okraji karet pod nimi, protože je rozvrhuje tentýž `Layout`, nic se neměří;
- **přepínač panelu je ovládací prvek scény**, ne akce konverzace (dnes `ProfilePaneButton`
  v hlavičce konverzace);
- boční sloupec nemá šipku zpět (zavírá ho přepínač), kořen profilu vedle vlastní konverzace nemá
  nadpis — dnešní `inPaneBand`, `paneRoot`, `besideOwnConversation` jako parametry obrazovek
  zmizí;
- místo pro tlačítka okna, insety a plocha lišty jen tady; `verifyWindowControls` hlídá jedno
  místo místo třiceti.

**Kompoziční kontext:** scéna kreslí lištu mimo obsah položek, takže popis si při zavolání zachytí
kontext položky (`currentCompositionLocalContext`) a scéna ho vykreslí uvnitř
`CompositionLocalProvider(context)`. Akce tak mají úložiště ViewModelů, barvu chatu i účet své
položky a `koinViewModel` v nich vrátí správnou instanci — obcházka „předej ViewModel hotový"
odpadne. Ověřit: `LocalSaveableStateRegistry` a `LocalLifecycleOwner` ze zachyceného kontextu
(otevřená nabídka ⋮ má žít s položkou).

**Na telefonu a v úzkém okně** je to dál výměna obrazovek. Aby obrazovka nekreslila lištu jednou
sama a jednou přes scénu (ztráta stavu rozepsaného hledání při rozložení Foldu, viz `PaneHeader`),
kreslí popis lišty **vždy dekorátor položky** (`PaneCard`) a scéna Chatů ho na širokém okně jen
přesměruje do své řady. Obrazovka tak nemá dvě větve.

### Co zmizí

`rememberWithLeavingExtras` a prázdný třetí oddíl, `headerOutside` / `HeaderOutsideShape` /
`PaneHeaderHost` u bočních obrazovek, `PaneBand` (`detailCardTop`, `publishToPaneBand`,
`PaneBandActions`, `LocalPaneHeaderMinHeight`, `inPaneBand`), `PaneBody` a `paneScaffoldColor`
v obsahu, parametry `paneRoot` a `besideOwnConversation`, `ProfilePaneButton` v hlavičce
konverzace; testy `CoveredExtrasTest`, `StandsOnDetailTest` a `FollowProfileTest` se přepíšou na
test výběru položek scény.

## Co ověřit prototypem

Jen Chaty, konverzace s profilem a úpravou, za přepínačem (`PaneSceneStrategy` vrací `null`, když
je vypnutý — návrat ke knihovně je jeden řádek):

1. **Gesto zpět**: z podstránky v bočním sloupci, z profilu (sloupec se během tahu zavírá),
   z konverzace s otevřeným sloupcem, a přechod mezi jednopanelovou výměnou a scénou při změně
   šířky okna.
2. **Fold**: `adb emu fold` / `unfold` v cyklu s otevřeným profilem a s rozepsaným hledáním v chatu
   — stav přežije, aplikace nespadne (Gotcha o insetech v `CLAUDE.md`).
3. **Přechod seznam → konverzace na telefonu** (let nadpisu, `sharedBounds`) beze změny.
4. **Otevření a zavření sloupce**: animace bez probliknutí, akce detailu v liště nepřeskakují.
5. **Barva chatu**: lišta v barvách plochy okna (`LocalPanePlaneScheme`), karty v barvě chatu;
   zachycený kontext lištu nepřebarví.
6. **Desktop**: tlačítka okna (Mac vlevo, Windows vpravo), táhla, zkratka ⌥⌘I.
7. **Pant**: Fold v pozici kniha — hranice panelů na pantu, nic přes něj.
8. **Registry `ConversationScreen`** (Gotchas v `CLAUDE.md`) po přesunu lišty ven porovnat.

## Postup

1. **Prototyp** za přepínačem, body výš. Nevyjde-li gesto zpět nebo Fold, zpět k variantě B.
2. **Scéna Chatů a Archivu** naostro: seznam | oblast chatu, boční sloupec, gesto zpět, táhla.
   Knihovní třípanel v Chatech pryč.
3. **Lišta**: `PaneTopBar` v konverzaci, seznamu témat, profilu, kalendáři
   a `ProfileSubpageTopBar`; zmizí `PaneBand` a spol.
4. **Úklid**: `rememberWithLeavingExtras`, `headerOutside` u bočních obrazovek, testy.
5. **Nastavení** (volitelně) stejnou scénou: sekce | podstránka.
6. **Kontrola zdrojáků**: obrazovka s rolí panelu nevolá `TopAppBar` přímo.

Každý krok vlastní PR, aplikace mezi nimi funguje (do kroku 2 jen za přepínačem).

## Otevřené otázky

- **Profil člena nad konverzací skupiny** je jiný chat. Patří do bočního sloupce skupiny (dnes ano),
  a pak scéna musí „patří k detailu" chápat jako „otevřeno z něj", ne „týž chat"?
- **Úzký boční sloupec (360 dp):** nadpis, akce sloupce a 2–3 akce detailu. Akce sloupce nad určitý
  počet do ⋮, nebo výpustka nadpisu?
- **Dvě ⋮ vedle sebe** (profil člena a konverzace): sloučit do jedné nabídky se skupinami?
- **Zpátky z podstránky ve sloupci** je bez šipky jen gestem / Esc. Stačí to, nebo má nadpis
  fungovat jako drobečková navigace?
- **Stav sloupce po přepnutí chatu**: dnes `followProfile` otevře profil nového chatu. Ve scéně jde
  o pravidlo „sloupec otevřený → ukaž profil detailu" bez přepisování zásobníku; má si scéna
  pamatovat i podstránku (úprava), nebo vždy začít profilem?
