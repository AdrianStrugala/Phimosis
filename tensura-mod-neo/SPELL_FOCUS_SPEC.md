# Katalizator zaklęć — spec implementacyjna

**Data:** 2026-09-08
**Status:** do przedyskutowania, nie zatwierdzony
**Dotyczy:** tensura-mod-neo (NeoForge 1.21.1) + datapack `predator_skills`
**Dokument siostrzany:** [start serwera publicznego](../PUBLIC_SERVER_LAUNCH.md)

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
| Kontrakt drzewka | `DEVOUR_TREE_CONTRACT.md` + walidator `validateSkillTrees` pod `check` |
| `devourRecover` | Waliduje `hasAbsorbed`, wydaje `SpellItem`, odracza `lockDispenser` o jeden tick |
| Keybindy | **Nie ma ani jednego** — `RegisterKeyMappingsEvent` to grunt zerowy |
| Receptury | Jedna, `recipes/recall_station.json` — wzorzec dla katalizatora |

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
| Katalizator | Craftowalny, w trzech tierach |
| Stare `SpellItem` | Zostają castowalne; nowe egzemplarze nie są już wydawane |
| Drzewko | Jedyne miejsce progresji i przeglądania |

---

## 4. Item

Trzy zarejestrowane itemy dzielące jedną klasę `SpellFocusItem`, `maxSlots`
w konstruktorze. Trzy itemy zamiast tieru w NBT, bo receptury i modele są wtedy trywialne.

| Item | Sloty | Charakter receptury |
|---|---|---|
| `spell_focus_simple` | 3 | Wczesna gra, tanie materiały |
| `spell_focus_reinforced` | 5 | Materiały z pochłaniania / Ice and Fire |
| `spell_focus_arch` | 8 | Późna gra, składnik z bossa lub questa FTB Quests |

`stacksTo(1)`.

### NBT

| Klucz | Typ | Znaczenie |
|---|---|---|
| `AttunedSpells` | `ListTag<StringTag>` | ID zaklęć, długość ≤ `maxSlots`; pusty string = slot wolny |
| `ActiveIndex` | `int` | Indeks aktywnego slotu, `[0, maxSlots)` |

`maxSlots` nie jest w NBT — wynika z itemu.

### Zachowanie

- `use(level, player, hand)` — deleguje do `SpellCasting` dla zaklęcia z aktywnego
  slotu. Cała logika instant/windup/kanał przeniesiona z `SpellItem` bez zmian.
- Pusty aktywny slot → `InteractionResultHolder.fail`, bez komunikatu na czacie
  (spam przy przypadkowym kliknięciu).
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

Konflikt jest w Epic Knights (`config/epicknights/weapons.json5`):

| Cecha | Liczba broni | Skutek |
|---|---|---|
| `canBlock: true` | 13 | PPM idzie w blok, offhand nie dostaje `use()` |
| `twoHanded: 1` | 9 | Zajmuje obie ręce |
| `twoHanded: 2` | 6 | Jw. |

`nobleSword`, `bastardSword` i `estoc` są w tej grupie.

**Rozwiązanie w configu, nie w kodzie** — `config/epicknights/general.json5`:

```
"disableTwoHanded": true,
"disableWeaponBlocking": true,
```

Oba domyślnie `false`; komentarz w configu sam zaleca ich włączenie przy modach
bojowych. Cena: blokowanie mieczem znika dla wszystkich, a katalizator w offhandzie
i tak wyklucza tarczę. To świadomy trade-off buildu — mag-rycerz oddaje obronę za magię.

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
niezmienniki: `DEVOUR_TREE_CONTRACT.md`, który jest dla tego drzewka
źródłem prawdy ważniejszym niż ten spec.

---

## 10. Pliki

### Nowe

| Plik | Rola |
|---|---|
| `item/SpellFocusItem.java` | Item, trzy warianty, NBT z sekcji 4 |
| `item/SpellCasting.java` | Logika use/channel wyciągnięta z `SpellItem`, wspólna; plus `findFocus(player)` |
| `client/SpellRadialScreen.java` | Jedyne UI katalizatora |
| `client/TensuraKeybinds.java` | `RegisterKeyMappingsEvent`, `R` |
| `network/OpenRadialPacket.java` | S→C |
| `network/SetActiveSpellPacket.java` | C→S |
| `network/AttuneSpellPacket.java` | C→S |
| `data/tensura/recipes/spell_focus_*.json` | Trzy receptury, wzorzec: `recall_station.json` |

### Zmienione

| Miejsce | Zmiana |
|---|---|
| `TensuraCommands.devourRecover` | `addItem` → `OpenRadialPacket`; walidacja i re-lock bez zmian |
| `SpellItem` | Logika przeniesiona do `SpellCasting`; item zostaje castowalny dla starych egzemplarzy |
| `TensuraItemRegistry` | +3 katalizatory, −`PREDATOR_CODEX` |
| `config/epicknights/general.json5` | `disableTwoHanded`, `disableWeaponBlocking` → `true` |

### Usunięte

`item/PredatorCodexItem.java`, `network/OpenCodexPacket.java`,
`network/RetrieveAbsorbedSpellPacket.java`, wpis `PREDATOR_CODEX`.

`gui/PredatorCodexScreen.java` **zostaje jako baza radiala** — ma już listę zaklęć
i obsługę kliknięcia. To zmiana nazwy, nie wskrzeszanie Kodeksu.

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
5. Wycięcie Kodeksu i dropu `SpellItem`.
6. Offhand: `findFocus` + przełączniki Epic Knights + playtest bronią, którą realnie gracie.

---

## 12. Kryteria akceptacji

- [ ] Drzewko pod `K` pokazuje 215 node'ów devour, log mówi `loaded successfully`
- [ ] Katalizator craftuje się w trzech tierach, ma 3/5/8 slotów
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

---

## Otwarte pytania

- Czy pochłonięcie nadal dropi `SpellItem`, czy tylko zapala node (sekcja 6)?
- Konkretne receptury trzech tierów.
- Koszty node'ów devour — płaskie czy zależne od siły zaklęcia?
