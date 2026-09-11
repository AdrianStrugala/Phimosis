# Katalizator zaklęć — spec implementacyjna

**Data:** 2026-09-08
**Status:** wdrożony, przeszedł pierwszy playtest (2.0.43)
**Dotyczy:** tensura-mod-neo (NeoForge 1.21.1) + datapack `predator_skills`
**Dokument siostrzany:** [start serwera publicznego](../operations/public-server-launch.md)

---

## 0. Stan wdrozenia

Zbudowane i wgrane na TEST oraz klienta jako `tensura-2.0.43.jar` (2026-09-09).
**Produkcja nietknięta** — 2k37 nadal ma 2.0.18.

| Krok | Stan |
|---|---|
| 0. Migracja datapacka | **Częściowo** — na TEST zdjęta zawartość pakietu ze świata, `pack.mcmeta` zostawiony. Pełne usunięcie + `/datapack disable` na produkcji nadal do zrobienia pod nadzorem |
| 1. `SpellCasting` | Zrobione — `SpellItem` i katalizator dzielą jedną ścieżkę rzucania |
| 2. `SpellFocusItem` + receptura + Shift+scroll | Zrobione |
| 3. `SpellRadialScreen` na `R` | Zrobione |
| 4. `devourRecover` → `OpenRadialPacket` + `AttuneSpellPacket` | Zrobione |
| 5. Wycięcie Kodeksu | Zrobione |
| 6. Offhand | `findFocus` zrobione; config Epic Knights **celowo nietknięty** — patrz sekcja 7 |

**Nie zweryfikowane w grze.** Kompiluje się, walidacje przechodzą, klasy i assety
są w jarze — ale nikt tego jeszcze nie kliknął. Kryteria akceptacji w sekcji 12
są nadal do odhaczenia.

Decyzje użytkownika, które zmieniły ten spec względem pierwotnej wersji:
jeden katalizator zamiast trzech tierów (5 slotów, receptura ze slime ballem),
pochłonięcie **nadal dropi** `SpellItem`, wycięcie Kodeksu wchodzi od razu.

### Poprawki z review 2026-09-09 (2.0.39)

- **Receptura była w martwym katalogu.** 1.21 przemianowało katalogi datapacka na
  nazwy rejestrów w liczbie pojedynczej, a plik leżał w `data/tensura/recipes/`.
  Loader tam nie zagląda i nie zgłasza błędu — katalizator był niecraftowalny,
  tak samo `recall_station` (dużo dłużej). Przeniesione do `recipe/`.
- **Nowy `validateResourceLayout`** (`gradle/resource-layout-validation.gradle`,
  wpięty w `check`) łapie stare nazwy katalogów, brakujące modele i tekstury
  z `overrides` oraz wynik receptury wskazujący na niezarejestrowany item.
  Lista zarejestrowanych itemów czytana jest z `TensuraItemRegistry`, a nie
  przepisywana ręcznie — poprzednia przeżyła usunięcie `predator_codex`.
- **Katalizator ma własną teksturę** (`textures/item/spell_focus.png`) zamiast
  `minecraft:item/slime_ball`. Przy okazji szkoła `physical` dostała jawny model
  `spell_physical` — w `spell_item.json` była nią `layer0`, więc na katalizatorze
  `tackle` i `hyper_beam` renderowały się jako slime ball.
- `getActiveSpell` czyta tylko aktywny slot; leciało to z dwóch `ItemProperties`,
  czyli co klatkę na każdy wyrenderowany katalizator.
- Radial wraca do drzewka Devour zamiast do świata; polling klawisza obsługuje
  też przycisk myszy; wyczyszczenie aktywnego slotu przestawia wybór na pierwszy
  niepusty; tooltip mówi o Shift+scroll.
- **Config Epic Knights cofnięty do domyślnego** na TEST i kliencie. Zalecenie
  włączenia obu przełączników było błędne — sekcja 7 tłumaczy, dlaczego.

### Po pierwszym playteście (2.0.40)

Katalizator działa. Zgłoszone i poprawione:

- **PPM z pustym aktywnym slotem otwiera radial** zamiast nie robić nic. Ten sam
  ekran co pod `R`, tylko bez trzymanego klawisza — zamyka go klik w wycinek
  (puszczenie przycisku, więc przeciągnięcie do środka nadal czyści slot).
- **Ikony w radialu 2×** (32 px zamiast 16 px), sloty 44 px, pierścień ściągnięty
  z 74 na 68 px. Wcześniej ikona zajmowała ćwiartkę tego, co panel na nią dawał.

### Rozjazd ikon po rosterze 110 (2.0.41)

Przy podbiciu rosteru z 93 na 110 zaklęć zregenerowany został tylko
`spell_item.json`; `spell_focus.json` został na 93 nadpisaniach ikon, więc
17 nowych zaklęć na katalizatorze pokazywałoby sam gem szkoły. Model katalizatora
jest teraz generowany z nadpisań `spell_item.json` plus jawny `school: 0`.

`validateResourceLayout` sprawdza dodatkowo, czy **oba** modele mają komplet
nadpisań `tensura:icon` dla całego `CUSTOM_ICON_ORDER` — to jest ten strażnik,
którego brakowało.

### Tooltip pustego slotu i ikony drzewka (2.0.42)

- **Tooltip po najechaniu na pusty slot.** Pusty wycinek był jedyną rzeczą w tym
  UI, która nic o sobie nie mówiła. Teraz tłumaczy, że zaklęcia przypisuje się
  z drzewka Devour pod `K`. W trybie przypisania treść jest inna — mówi wprost,
  co zostanie tu wrzucone.
- **Ikony w drzewku miały różne wielkości.** Nie wina katalizatora: do 2.0.40
  15 węzłów używało ikon waniliowych, w tym `minecraft:dispenser`
  i `minecraft:wither_skeleton_skull`, które są modelami bloków i renderują się
  jako bryły 3D — obok płaskich dysków 32×32 wyglądały wyraźnie większe. Roster
  110 dał wszystkim własne ikony; `validateSkillTrees` pilnuje teraz, żeby każdy
  dispenser miał dokładnie `tensura:spell_icon_<spell>`. Jedyna ikona waniliowa,
  jaka zostaje, to `devour_core`.

### Komendy admina (2.0.43)

Powierzchnia komend przeszła z „wydaj item" na „nadaj postęp", zgodnie z tym, że
przy katalizatorze zaklęcia nie są już itemami.

| Komenda | Działanie |
|---|---|
| `/tensura unlock spell <gracz> <zaklęcie>` | Zapisuje pochłonięcie w `PredatorData` i zapala znacznik w drzewku. Autouzupełnianie po ID zaklęć |
| `/tensura unlock all <gracz>` | To samo dla całego rosteru |
| `/tensura devour_recover <gracz> <zaklęcie>` | Wewnętrzna, wołana przez nagrodę węzła — nie do ręcznego użycia |
| `/tensura convert <gatunek>` / `/tensura unconvert` | Bez zmian |

Usunięte: `givespell`, `absorb_spell` (obie wydawały `SpellItem`) oraz
`absorb_all`, którego rolę przejęło `unlock all`. `SpellItem` nadal da się wziąć
z zakładki kreatywnej, jeśli będzie potrzebny do debugowania.

Autouzupełnianie czyta `SpellRegistry` w momencie podpowiadania, więc nadąża za
przeładowaniem datapacka. Provider nie jest zarejestrowany w `SuggestionProviders`,
więc vanilla serializuje go jako `minecraft:ask_server` i klient dopytuje serwer —
to jest właściwe zachowanie, bo tylko serwer zna aktualny rejestr.

---

## 1. Cel

Zastąpić model „jeden item per zaklęcie" jednym itemem trzymającym kilka zaklęć
z aktywnym wyborem — model znaków z Wiedźmina.

Powody:
- Przy 20 zaklęciach ekwipunek jest magazynem itemów.
- Kilka katalizatorów na różnych slotach paska = loadouty (ognisty, kontrolny,
  mobilność), czyli wybór budowy postaci, a nie tylko UI.
- Limit slotów jest dźwignią balansu i progresji.
- Zaklęcia zostają na pasku, bez przenoszenia wszystkiego na keybindy.

---

## 2. Stan zastany

Ustalone przez czytanie kodu, nie z pamięci. Wszystko poniżej **już działa** i jest
podstawą, na której spec się opiera.

| Element | Stan |
|---|---|
| Zaklęcia | **107** JSON-ów w `data/tensura/spells/`, 5 z `hold_to_channel` |
| `SpellExecutor` | `cast`, `castHeldChannel`, `castPrepared`, `finishHeldChannel` — kompletne |
| `SpellItem.use()` | Obsługuje instant, windup (`castPrepared` w `onUseTick`) i kanał trzymany; **przyjmuje `hand`, nie sprawdza której ręki** |
| NBT `SpellItem` | `SpellId`, `School`, `ChannelWindup`, `ChannelDuration`, `HoldToChannel` |
| Pasek cooldownu | `isBarVisible`/`getBarWidth`/`getBarColor` po `ClientCooldownTracker`, keyowane po ID zaklęcia |
| Ikony | `TensuraItemRegistry.SPELL_ICONS` — **93** itemy `spell_icon_<id>` z `SpellItem.CUSTOM_ICON_ORDER` |
| Dane klienta | `ClientSpellCatalog` czyta spelle z jara (na dedyku `SpellRegistry` jest pusty) |
| Prawda o pochłoniętych | `PredatorData` w `PERSISTED_NBT_TAG` (przeżywa śmierć), `hasAbsorbed`/`markAbsorbed` |
| Drzewko | Każde zaklęcie ma **dwa node'y**: `<spell>_owned` (znacznik) i `<spell>` (przycisk, **re-lockuje się sam**) |
| Hook drzewka | Nagroda `puffish_skills:command` → `tensura devour_recover @s <spell>` na 107 node'ach |
| Kontrakt drzewka | `../contracts/devour-tree.md` + walidator `validateSkillTrees` pod `check` |
| `devourRecover` | Waliduje `hasAbsorbed`, wydaje `SpellItem`, odracza `lockDispenser` o jeden tick |
| Keybindy | **Nie ma ani jednego** — `RegisterKeyMappingsEvent` to grunt zerowy |
| Receptury | Jedna, `recipe/recall_station.json` — wzorzec dla katalizatora |

**Wniosek:** to jest refaktor UI, nie nowy silnik. Warstwa rzucania i walidacji zostaje
nietknięta.

---

## 3. Decyzje projektowe

| Pytanie | Decyzja |
|---|---|
| Ile UI ma katalizator | **Jedno** — radial. Przypisanie i wybór na tym samym ekranie |
| Skąd przypisanie | Klik node'a w drzewku Puffisha |
| Czy radial ma pulę zaklęć | **Nie** — niepotrzebna, przypisanie zawsze startuje z drzewka |
| Kodeks Predatora | **Usunięty** |
| Katalizator | Craftowalny, jeden item, 5 slotów |
| Stare `SpellItem` | Zostają castowalne; pochłonięcie nadal je dropi |
| Drzewko | Jedyne miejsce progresji i przeglądania |

---

## 4. Item

Jeden item `tensura:spell_focus`, `stacksTo(1)`, **5 slotów**. Liczba slotów siedzi
w konstruktorze `SpellFocusItem`, więc kolejne tiery to dopisanie wpisów w rejestrze,
bez zmian w logice.

Receptura — proste materiały plus slime ball, który jest tu składnikiem obowiązkowym:

```
 G      G = minecraft:gold_ingot
GSG     S = minecraft:slime_ball
 G
```

Pusty katalizator ma własną teksturę (`textures/item/spell_focus.png` — złoty
pierścień ze slime rdzeniem, pod recepturę); po przypisaniu zaklęcia model przełącza
się na ikonę aktywnego zaklęcia przez te same nadpisania, których używa `SpellItem`.

### NBT

| Klucz | Typ | Znaczenie |
|---|---|---|
| `AttunedSpells` | `ListTag<StringTag>` | ID zaklęć, długość ≤ `maxSlots`; pusty string = slot wolny |
| `ActiveIndex` | `int` | Indeks aktywnego slotu, `[0, maxSlots)` |

`maxSlots` nie jest w NBT — wynika z itemu.

### Zachowanie

- `use(level, player, hand)` — deleguje do `SpellCasting` dla zaklęcia z aktywnego
  slotu. Cała logika instant/windup/kanał przeniesiona z `SpellItem` bez zmian.
- Pusty aktywny slot → otwiera radial zamiast rzucać (`consume`, bez komunikatu na
  czacie). Wywołanie klienta siedzi za `level.isClientSide`, więc na dedyku klasa
  kliencka nigdy się nie ładuje.
- Pasek cooldownu — jak w `SpellItem`, ale dla aktywnego zaklęcia.
- Model — property override po szkole i ikonie aktywnego zaklęcia, wariant
  `getIconIndex`/`getSchoolIndex` czytający `AttunedSpells[ActiveIndex]`.

---

## 5. UI — jeden radial

**Jeden pierścień = sloty katalizatora.** Wycinek pusty rysuje się przygaszony, zajęty
pokazuje `spell_icon_<id>` i łuk cooldownu z `ClientCooldownTracker`. W środku nazwa
najechanego zaklęcia i jego szkoła.

| Wejście | Zachowanie |
|---|---|
| Przytrzymanie `R` z katalizatorem w ręce lub offhandzie | Puszczenie na wycinku → `SetActiveSpellPacket` |
| PPM katalizatorem z **pustym aktywnym slotem** | Otwiera ten sam radial; nie ma trzymanego klawisza, więc zamyka go klik w wycinek |
| Klik node'a w drzewku Puffisha | Radial otwiera się z zaklęciem „na kursorze"; klik w wycinek → `AttuneSpellPacket` (nadpisuje) |
| Przeciągnięcie wycinka do środka | `AttuneSpellPacket` z pustym ID = wyczyść slot |
| `Esc` | Zamknij bez zmian |

Zaklęcie na kursorze rysowane przy myszy; kliknięcie poza pierścieniem anuluje
przypisanie, ale nie zamyka ekranu.

**Podział ról:** drzewko przegląda i inicjuje przypisanie, radial trzyma sloty
i wybór aktywnego. Bez nakładania się.

Jeśli gracz kliknie node bez katalizatora w ekwipunku — sam komunikat na czacie,
bez otwierania ekranu.

---

## 6. Integracja z drzewkiem

**Nie piszemy własnego typu nagrody ani mixina.** Hook już istnieje: node `<spell>`
ma nagrodę `puffish_skills:command` wołającą `tensura devour_recover @s <spell>`,
a po kliknięciu sam się re-lockuje, więc jest klikalny wielokrotnie.

**Cała zmiana to ciało `TensuraCommands.devourRecover`:**

```
- target.addItem(SpellItem.create(id));
+ PacketDistributor.sendToPlayer(target, new OpenRadialPacket(ASSIGN, id));
```

Bez zmian zostaje: walidacja `PredatorData.hasAbsorbed`, komunikat o niepochłoniętym
zaklęciu, odroczony o tick `PredatorAbsorption.lockDispenser`.

Bez zmian zostaje też `PredatorAbsorption.absorb` — pochłonięcie nadal dropi
`SpellItem` na ziemię. **Do decyzji przy implementacji:** czy drop zostaje jako
„fizyczny łup" (wtedy stare itemy nadal krążą), czy pochłonięcie tylko zapala node.
Rekomendacja: zostawić drop w kroku 1–3, wyciąć w kroku 5 razem z Kodeksem.

---

## 7. Offhand — miecz na LPM, magia na PPM

Vanilla woła `use()` najpierw dla głównej ręki, a przy `PASS` dla offhandu.
`SpellItem.use()` już przyjmuje `hand`, więc **z waniliowym mieczem działa to bez
zmian w kodzie**.

### Epic Knights — nic nie zmieniamy w configu

**Decyzja (2026-09-09): oba przełączniki zostają `false` na wszystkich trzech
setupach.** Wcześniejsza wersja tej sekcji zalecała włączenie obu; to było błędne
i zostało cofnięte.

Reguła gry, ustalona przez użytkownika: **broń dwuręczna wyklucza drugą rękę i tyle.**
Mod to egzekwuje sam — `MedievalWeaponItem.inventoryTick` nakłada
`TWO_HANDED_PENALTY`, gdy `getTwoHanded() > 0` i offhand nie jest pusty. Katalizator
liczy się jako „coś w offhandzie", więc dwuręczna broń + katalizator = debuff,
zgodnie z zamysłem. `disableTwoHanded: true` zdejmowałoby tę zasadę z całej mapy,
żeby obejść ją dla jednego itemu — dlatego nie.

Blokowanie (`disableWeaponBlocking`) rozbija się o liczby. Z 28 broni melee:

| Grupa | Liczba | Znaczenie dla katalizatora |
|---|---|---|
| `canBlock: true` | 13 | PPM może pójść w blok zamiast w rzucanie |
| …z tego **też dwuręczne** | 12 | Poza zakresem — przy dwuręcznej i tak nie nosisz katalizatora |
| …z tego **jednoręczne** | 1 (`messerSword`) | Jedyny realny konflikt |

Czyli globalny przełącznik zabierałby blokowanie wszystkim, żeby naprawić **jedną
broń**. Zła wymiana.

Jak dokładnie wygląda ten konflikt przy `messerSword` (z bajtkodu 10.10):

```java
// MedievalWeaponItem.use
if (canBlock(player) && blockingPriority) { startUsingItem(hand); return CONSUME; }
return super.use(...);   // SwordItem.use → PASS → offhand dostaje use()

// canBlock(Player) = canBlock() && player.getAttackStrengthScale(0f) == 1.0f
// blockingPriority (inventoryTick) = zadna reka nie trzyma ShieldItem
```

Katalizator nie jest `ShieldItem`, więc `blockingPriority` jest `true`. Ale
`canBlock(Player)` wymaga **pełnego paska ataku** — więc PPM rzuca zaraz po
machnięciu, a blokuje, gdy stoisz wypoczęty. Migotanie zależne od timingu, gorsze
niż konsekwentne „nie działa".

**Jeśli `messerSword` kiedykolwiek zacznie przeszkadzać**, chirurgiczna poprawka to
`"canBlock": false` przy tej jednej broni w `weapons.json5` — nie globalny
przełącznik. Do zrobienia dopiero, gdy ktoś się na to natnie w playteście.

Pozostałe 15 broni jednoręcznych bez blokowania i wszystkie bronie waniliowe
działają z katalizatorem bez żadnej zmiany w configu.

**Po stronie kodu:** `SpellCasting.findFocus(player)` — najpierw główna ręka, potem
offhand. Potrzebne, żeby `R` i radial znajdowały katalizator niezależnie od slotu.

**Do playtestu, nie do rozwiązywania z góry:**

1. **Bloki wygrywają z rzucaniem** — PPM w blok w zasięgu otworzy drzwi zamiast rzucić.
   Przy drzwiach pożądane, przy walce pod ścianą uciążliwe. Jeśli przeszkadza, dodać
   przełącznik w configu, a nie kombinować z `RightClickBlock` w ciemno.
2. **Kanał blokuje atak** — w trakcie `startUsingItem` nie da się machnąć mieczem.
   Dla instantów problemu nie ma; dla kanałów to sensowny balans.
3. **Better Combat** przy broni dwuręcznej potrafi chować item z offhandu wizualnie.

---

## 8. Sieć i bezpieczeństwo

| Pakiet | Kierunek | Ładunek |
|---|---|---|
| `OpenRadialPacket` | S→C | `mode` (SELECT/ASSIGN), opcjonalne `spellId` |
| `SetActiveSpellPacket` | C→S | `slot: int` |
| `AttuneSpellPacket` | C→S | `slot: int`, `spellId: ResourceLocation` (pusty = wyczyść) |

**Walidacja serwerowa — obowiązkowa w obu pakietach C→S:**

1. Gracz trzyma katalizator w głównej ręce albo offhandzie.
2. `slot` mieści się w `[0, maxSlots)` tego konkretnego itemu.
3. Dla `AttuneSpellPacket`: `PredatorData.hasAbsorbed(player, spellId)`.

Klient nie może być źródłem prawdy o tym, co gracz zna — inaczej pierwszy gracz
z modyfikowanym klientem ma wszystkie 107 zaklęć. `devourRecover` już ma tę walidację
i to jest wzorzec do powtórzenia.

---

## 9. Krok 0 — migracja datapacka

**Przed jakąkolwiek pracą nad katalizatorem**, inaczej testujesz na drzewku,
którego kod nie widzi.

`src/main/resources/data/tensura/puffish_skills/` w jarze zawiera obie kategorie.
Kopia w `world/datapacks/predator_skills/` **przesłania jara** i to ona jest ładowana.

| Kategoria | JAR | WORLD (żywy na 2k37) |
|---|---|---|
| devour — node'y | **215** | 67 |
| devour — nagrody `command` | **107** | **0** |
| devour — data plików | 2026-09-07 | 2026-09-03 |
| predator — node'y | 46 | 103 |
| predator — data `definitions.json` | 2026-09-05 | **2026-03-29** |
| koszty devour | wszystkie `0` | wszystkie `0` |

**Jar jest nowszy w obu kategoriach.** Większy `predator` w świecie to marcowe drzewo,
później świadomie przycięte — rozmiar pliku myli, daty nie. Nie ma czego scalać.

Skutek obecnego stanu: żywa kopia devour nie ma ani jednej nagrody, więc
`devour_recover` nie jest wołane **nigdy**. Klik w drzewku nic nie robi, a odblokowania
są darmowe (67 node'ów bez kosztu i bez nagrody).

**Procedura:**

1. Backup `world/` albo odczekanie cyklu FTB Backups 3.
2. `/datapack disable "file/predator_skills"` na żywym serwerze — **przed** usunięciem
   plików. Datapack włączony w `level.dat`, a fizycznie skasowany, potrafi zablokować start.
3. Usunąć `world/datapacks/predator_skills/`.
4. `/reload`, sprawdzić w `logs/latest.log`: `Data pack 'tensura' loaded successfully!`
5. Pod `K` powinno być 215 node'ów devour i 46 predator.

**Ostrzeżenie:** przy niezgodności schematu Puffisha okno pod `K` wstaje **puste**
zamiast rzucić błędem. To sygnał o `version` w `config.json` albo o polu, którego
0.17.3 nie zna — nie o zepsutym datapacku.

### Czego NIE ruszać: koszty devour

`cost: 0` na wszystkich node'ach devour **jest zamierzone i egzekwowane przez walidator**
(`gradle/skill-tree-validation.gradle`, zadanie `validateSkillTrees` podpięte pod `check`).
Kategoria `devour` ma `"sources": []` w `experience.json`, czyli zerową pulę punktów —
każdy węzeł z `cost > 0` staje się nieklikalny. Zdarzyło się to już 2026-09-05:
`_owned` dostały `cost: 1` i dispensery przestały wydawać itemy.

Bramką w devour jest `PredatorData.hasAbsorbed`, nie punkty. „Darmowe drzewko" to nie
jest usterka — gracz nie dostanie zaklęcia, którego nie pochłonął, bo `devourRecover`
odmawia.

Chcesz kosztów — najpierw dodaj źródła XP do `experience.json`. Szczegóły i pozostałe
niezmienniki: `../contracts/devour-tree.md`, który jest dla tego drzewka
źródłem prawdy ważniejszym niż ten spec.

---

## 10. Pliki

### Nowe

| Plik | Rola |
|---|---|
| `item/SpellFocusItem.java` | Item, jeden wariant o 5 slotach, NBT z sekcji 4 |
| `item/SpellCasting.java` | Logika use/channel wyciągnięta z `SpellItem`, wspólna; plus `findFocus(player)` |
| `client/SpellRadialScreen.java` | Jedyne UI katalizatora |
| `client/TensuraKeybinds.java` | `RegisterKeyMappingsEvent`, `R` |
| `network/OpenRadialPacket.java` | S→C |
| `network/SetActiveSpellPacket.java` | C→S |
| `network/AttuneSpellPacket.java` | C→S |
| `data/tensura/recipe/spell_focus.json` | Receptura — katalog `recipe`, nie `recipes` (1.21) |
| `assets/…/textures/item/spell_focus.png` | Ikona pustego katalizatora |
| `assets/…/models/item/spell_physical.json` | Model szkoły physical, wcześniej niejawnie `layer0` |
| `gradle/resource-layout-validation.gradle` | `validateResourceLayout`, wpięty w `check` |

### Zmienione

| Miejsce | Zmiana |
|---|---|
| `TensuraCommands.devourRecover` | `addItem` → `OpenRadialPacket`; walidacja i re-lock bez zmian |
| `SpellItem` | Logika przeniesiona do `SpellCasting`; item zostaje castowalny dla starych egzemplarzy |
| `TensuraItemRegistry` | +`SPELL_FOCUS`, −`PREDATOR_CODEX` |
| `gradle/skill-tree-validation.gradle` | Lista zarejestrowanych itemów czytana z rejestru zamiast przepisywana |

### Usunięte

`item/PredatorCodexItem.java`, `gui/PredatorCodexScreen.java`,
`network/OpenCodexPacket.java`, `network/RetrieveAbsorbedSpellPacket.java`,
wpis `PREDATOR_CODEX`.

**Dlaczego Kodeks umiera:** miał dwa zadania. Przeglądanie przejmuje drzewko,
a odzyskiwanie zgubionego `SpellItem` **przestaje mieć sens** — przy katalizatorze
nie nosisz itemów per zaklęcie, więc nie ma czego zgubić. Katalizator usuwa przyczynę,
nie objaw.

---

## 11. Kolejność prac

0. **Migracja datapacka** (sekcja 9).
1. `SpellCasting` — wyciągnięcie logiki z `SpellItem`, zero zmian zachowania.
2. `SpellFocusItem` + receptury + `SetActiveSpellPacket` + `R`. **Grywalne po tym kroku.**
3. `SpellRadialScreen` — wybór aktywnego.
4. `devourRecover` → `OpenRadialPacket` + `AttuneSpellPacket`; tryb przypisania w radialu.
5. Wycięcie Kodeksu. Drop `SpellItem` przy pochłonięciu **zostaje** (decyzja użytkownika).
6. Offhand: `findFocus` + playtest bronią, którą realnie gracie (config Epic Knights zostaje domyślny).

---

## 12. Kryteria akceptacji

- [ ] Drzewko pod `K` pokazuje 215 node'ów devour, log mówi `loaded successfully`
- [ ] Katalizator craftuje się (4 sztabki złota + slime ball) i ma 5 slotów
- [ ] `R` otwiera radial; puszczenie na wycinku zmienia aktywne zaklęcie
- [ ] PPM rzuca aktywne zaklęcie; pusty slot nic nie robi
- [ ] Kanał (`flamethrower`) działa z katalizatora tak samo jak z `SpellItem`
- [ ] Pasek cooldownu pokazuje cooldown aktywnego zaklęcia, także w offhandzie
- [ ] Klik node'a w drzewku otwiera radial z zaklęciem na kursorze; klik w wycinek przypisuje
- [ ] Klik node'a zaklęcia niepochłoniętego → komunikat, brak przypisania
- [ ] `AttuneSpellPacket` z ID spoza `PredatorData` odrzucony po stronie serwera
- [ ] Katalizator w offhandzie + miecz w głównej ręce: LPM bije, PPM rzuca
- [ ] Stare `SpellItem` z ekwipunków graczy nadal działają
- [ ] Dwa katalizatory na pasku trzymają niezależne zestawy
- [ ] Pusty katalizator ma własną ikonę (złoty pierścień ze slime rdzeniem), nie slime ball
- [ ] `tackle` na katalizatorze pokazuje ikonę physical, nie slime ball
- [ ] Po przypisaniu z drzewka radial oddaje sterowanie z powrotem do drzewka

---

## Otwarte pytania

- Czy po playteście drop `SpellItem` przy pochłonięciu ma zostać wycięty.
- Czy dochodzą kolejne tiery katalizatora i na jakich materiałach.
- **Czy radial na `R` zostaje ekranem.** `Screen` zatrzymuje ruch i kamerę gracza na
  czas trzymania klawisza — świat tyka (`isPauseScreen() == false`), ale postać stoi.
  Na razie podział ról: `R` to wybór i przypisanie poza walką, Shift+scroll to
  ścieżka bojowa (oba w tooltipie). Jeśli playtest pokaże, że to boli, alternatywą
  jest overlay na `RenderGuiEvent` z własnym czytaniem delty myszy — realna robota
  i nowa klasa błędów, więc nie robimy tego w ciemno.

(Koszty node'ów devour przestały być pytaniem — sekcja 9 wyjaśnia, dlaczego muszą
zostać zerowe.)
