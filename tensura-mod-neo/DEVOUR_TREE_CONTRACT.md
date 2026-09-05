# Kontrakt drzewka Devour

Ten plik opisuje, **dlaczego** drzewko `devour` wygląda tak, a nie inaczej.
Reguły są egzekwowane automatycznie przez `gradle/skill-tree-validation.gradle`
(zadanie `validateSkillTrees`, podpięte pod `check`), więc `./gradlew build`
wywali się przy ich złamaniu. Zmieniasz reguły — zmień też walidator i ten plik.

Wersja moda: **puffish_skills 0.17.3**. To istotne, patrz sekcja o schemacie.

---

## Jak działa drzewko

Każdy spell ma w drzewku **dwa węzły**:

| węzeł | rola | widoczność |
|---|---|---|
| `<spell>_owned` | znacznik stanu — czy spell został pochłonięty | niewidoczny (`size: 0.1`, `blank.png`) |
| `<spell>` | dispenser — kliknięcie wydaje kopię itemu | widoczny, z ikoną i ramką |

Przepływ przy zabiciu pokemona (`PredatorAbsorption.absorb`):

1. `PredatorData.markAbsorbed` — ustawia flagę w NBT gracza (`tensura:absorbed`)
2. `unlockOwned` — odpala `puffish_skills skills unlock <gracz> tensura:devour <spell>_owned`
3. item ląduje na ziemi

Przepływ przy kliknięciu dispensera:

1. Puffish odblokowuje węzeł `<spell>` i odpala nagrodę `tensura devour_recover @s <spell>`
2. `devourRecover` sprawdza `PredatorData.hasAbsorbed` — bez tego odmawia
3. wydaje item
4. `lockDispenser` **z powrotem blokuje** węzeł `<spell>`, żeby dało się go kliknąć ponownie

Punkt 4 jest sednem: dispenser to przycisk wielokrotnego użytku, realizowany przez
ciągłe zdejmowanie i nakładanie blokady na ten sam węzeł.

---

## Reguły i ich uzasadnienie

### 1. Wszystkie koszty muszą być `0`

Kategoria `devour` ma `"sources": []` w `experience.json` — **nie ma żadnego źródła XP**,
więc gracz zawsze dysponuje zerową pulą punktów umiejętności. Każdy węzeł z `cost > 0`
jest w tej kategorii nieosiągalny.

To nie jest teoria. 2026-09-05 węzły `_owned` dostały `cost: 1` (97 węzłów × 1 punkt
przy puli 0) i dispensery przestały wydawać itemy po raz drugi. Walidator sprawdza
ogólniej: *jeśli kategoria nie ma źródeł XP, żaden węzeł nie może mieć kosztu*.

Chcesz wprowadzić koszty — najpierw dodaj źródła XP do `experience.json`.

### 2. Każdy `<spell>_owned` musi być `root: true`

Znaczniki są **niezależne od siebie**. Pochłonięcie Embera nie może wymagać
wcześniejszego pochłonięcia czegokolwiek innego, bo o kolejności decyduje to, na jakie
pokemony gracz trafi. Gdy `_owned` przestaje być rootem, jego odblokowanie zaczyna
zależeć od rodzica i mod nie może go zapalić w dowolnym momencie.

Połączenia `_owned → _owned` mogą istnieć jako **linie dekoracyjne** grupujące spelle
po typach — przy `root: true` nie tworzą realnej zależności.

### 3. `<spell>_owned` musi być niewidoczny i bez nagród

`size: 0.1`, ikona `tensura:textures/gui/blank.png`, ramka z `blank.png` na wszystkich
czterech stanach, `rewards: []`.

Znacznik to **stan, nie interfejs**. Gracz ma widzieć jeden klikalny obiekt na spell —
dispenser. Gdy znacznik dostaje prawdziwą ikonę i duży rozmiar, w drzewku pojawiają się
dwa obiekty na spell i nie wiadomo, który kliknąć. Nagrody na znaczniku rozbiłyby
mechanizm z punktu 4 przepływu, bo znacznik nigdy nie jest z powrotem blokowany.

### 4. Dispenser musi mieć ramkę `owned.png` / `unowned.png`

```json
"frame": { "type": "texture", "data": {
  "locked": "tensura:textures/gui/unowned.png",
  "available": "tensura:textures/gui/owned.png",
  "unlocked": "tensura:textures/gui/owned.png",
  "excluded": "tensura:textures/gui/unowned.png"
}}
```

Ramka to jedyny sygnał „masz / nie masz". Bez niej dispenser wygląda identycznie
niezależnie od tego, czy spell został pochłonięty, a kliknięcie niepochłoniętego
kończy się komunikatem odmowy bez żadnej uprzedzającej wskazówki.

### 5. Dispenser musi mieć nagrodę `tensura devour_recover @s <spell>`

Dokładnie ta komenda, nie `givespell`. `givespell` **omija sprawdzenie pochłonięcia**
i nie wywołuje `lockDispenser`, więc węzeł zostałby trwale odblokowany i wydałby item
tylko raz. `devour_recover` robi oba: waliduje stan i przezbraja przycisk.

### 6. Ikony dispenserów

Preferowane `tensura:spell_icon_<spell>`. Obecnie takich ikon jest 50 na 97 spelli —
reszta używa itemów waniliowych i to jest w porządku jako stan przejściowy.

Ikony są rejestrowane **pętlą po `SpellItem.CUSTOM_ICON_ORDER`**, nie osobnymi
wywołaniami `ITEMS.register("spell_icon_…")`. Szukanie literałów w rejestrze daje
fałszywy alarm o braku rejestracji. Dodajesz ikonę — dopisz do `CUSTOM_ICON_ORDER`
plus model w `assets/tensura/models/item/` i teksturę w `assets/tensura/textures/item/spell/`.

---

## Schemat puffish_skills 0.17.3

Zainstalowana wersja używa **starszego schematu** niż dokumentacja nowszych wydań.
Pliki pisane pod nowszy schemat dostają setki błędów walidacji, a **Puffish odrzuca
wtedy cały namespace `tensura`** — czyli jeden zły plik w `predator` gasi także `devour`
i gracz widzi puste okno pod klawiszem K.

| element | 0.17.3 — poprawnie | nowszy schemat — odrzucany |
|---|---|---|
| ikona | `{"type":"item","data":{"item":X}}` | `{"type":"puffish_skills:item","item":X}` |
| połączenia | `["from","to"]` | `{"from":X,"to":Y}` |
| atrybuty | `puffish_attributes:mining_speed` | `puffish:mining_speed` |
| źródła XP | `puffish_skills:kill_entity`, wartość w `data.experience` | `puffish_skills:kill`, wartość w `value` |
| krzywa XP | `experience_per_level` | tablica `levels` |
| `category.json` | bez pola `points` | z polem `points` |

Wynik ładowania widać w `logs/latest.log`:

- sukces: `[puffish_skills] Data pack 'tensura' loaded successfully!`
- porażka: `[puffish_skills] Data pack 'tensura' could not be loaded:` plus lista błędów

---

## Dwie kopie drzewka

Te same pliki mogą leżeć w dwóch miejscach:

- w jarze moda — `src/main/resources/data/tensura/puffish_skills/`
- w datapacku świata — `<serwer>/world/datapacks/predator_skills/data/tensura/puffish_skills/`

**Wygrywa kopia ze świata.** Zmiana wprowadzona tylko w repo nie wejdzie do gry, dopóki
kopia w świecie istnieje — trzeba ją zsynchronizować albo skasować datapack.

Jar zawiera komplet 11 plików (obie kategorie plus `config.json`), więc datapack świata
da się usunąć i zostawić mod jako jedyne źródło. Uwaga: datapack wpisany jako włączony
w `level.dat`, a fizycznie usunięty, potrafi zablokować start serwera.

Kompromis: datapack w świecie edytuje się na żywo i przeładowuje `/reload`, zawartość
jara wymaga przebudowy moda i restartu serwera.

---

## Uruchomienie walidacji

```
./gradlew validateSkillTrees
```

Chodzi też automatycznie przy `./gradlew build` (przez `check`).
