# Start serwera publicznego — co jest potrzebne

**Data:** 2026-09-08
**Status:** do przedyskutowania, nie zatwierdzony
**Dotyczy:** serwer 2k37 (NeoForge 1.21.1)
**Dokument siostrzany:** [katalizator — spec implementacyjna](../specs/spell-focus.md)

---

## 1. Czym ten serwer ma być

Odpowiedź na „czy to siądzie dla obcych ludzi" jest twierdząca, ale nie dlatego, że
zestaw modów jest dobry. Dlatego, że mod tensura ma mechanikę, której nie ma nikt inny.

**Pokemony są obywatelami kolonii, a praca w kolonii jest ich treningiem.**
Wrzucasz pokemona pokeballem w ratuszu, on staje się mieszkańcem (renderowanym jako
pokemon, z gatunkiem dobranym do zawodu), pracuje, a przy odbiorze **przyrost skilli
wraca jako EV, z nadmiarem przelewającym się w IV**. Miesiąc w kopalni to wypasiony
atak i podbite IV, czyli statystyka w Cobblemonie normalnie nietykalna.

Z tego wychodzi cała warstwa polityczna bez dorabiania fikcji:

- Królestwo = kolonia = populacja wytrenowanych pokemonów. Siła państwa jest
  policzalna i widoczna gołym okiem.
- Gracze są szlachtą. Trener nie pracuje — trener rządzi tymi, którzy pracują.
- Praca najemna jest dyplomacją: cudzy Charmander w twojej hucie wraca właścicielowi
  z EV w ataku. Traktat o pracy jest umową z twardą mechaniką, nie odgrywką.
- Wojna wyludnia zamiast burzyć — a to znacznie lepszy model dla serwera, na którym
  budowanie zajmuje setki godzin.
- Predator jest ciemną stroną tej samej osi: pokemona wychowujesz albo pożerasz.
  Królestwa pasterzy kontra herezja pożeraczy — casus belli pisze się sam.

**To jest cała przewaga nad sceną Cobblemonową**, która w 2026 składa się z wariacji
na temat gymów i odznak. Wszystko poniżej służy temu, żeby ta przewaga dotarła do ludzi.

---

## 2. Blokery — bez tego nie otwieramy

### 2.1 Konwersja pokemon ↔ obywatel — autoryzacja wdrożona

Status 2026-09-11: implementacja wymaga `MANAGE_HUTS` przy zasiedleniu i recall,
a recall dodatkowo dopuszcza wyłącznie pierwotnego właściciela zapisanego w
`DynamicCitizenSpeciesData`. Niezapisany citizen nie może zostać zamieniony w
Pokémona zwykłą interakcją ani pakietem GUI. Zmiana oczekuje pełnego buildu i
playtestu bezpieczeństwa.

Napisana pod zamknięte grono. Na serwerze publicznym każdy z tych trzech punktów
jest krytyczny:

| # | Dziura | Skutek |
|---|---|---|
| 1 | `ConversionEvents.onPlayerInteract` nie weryfikuje właściciela ani praw w kolonii | Obcy z pokeballem **wyludnia cudze królestwo** w kilka minut |
| 2 | Dla obywatela spoza `DynamicCitizenSpeciesData`: `ownerUUID = event.getEntity().getUUID()` | Klikający **staje się właścicielem** i dostaje pokemona z IV/EV ze skilli tego obywatela — cała Caledonia jest farmą |
| 3 | Zasiedlenie sprawdza tylko, czy stoisz w ratuszu, nie czyim | Obcy dorzuca ci mieszkańców i zjada limit populacji |

**Fix wdrożony:** `colony.getPermissions().hasPermission(player, Action.MANAGE_HUTS)`
w każdym wejściu oraz porównanie `ownerMap` z UUID gracza przy recall. Obywatel
niezapisany w `DynamicCitizenSpeciesData` nie jest konwertowalny poza trybem admina.

**Bonus:** punkt 1 odblokowany wyłącznie między stronami zadeklarowanej wojny zamienia
się z griefu w legalny najazd. To jest gotowa mechanika wojenna, nie tylko łatka.

### 2.2 Serwer ładuje martwy datapack

`world/datapacks/predator_skills/` przesłania kopię w jarze. Żywa kategoria `devour`
ma 67 node'ów i **zero nagród**, więc `tensura devour_recover` nie jest wołane nigdy —
klik w drzewku nie robi nic. Procedura naprawy jest krokiem 0
w [specu katalizatora](../specs/spell-focus.md).

(Zerowe koszty w devour to osobna sprawa i są **zamierzone** — patrz
`../contracts/devour-tree.md`. Bramką jest pochłonięcie, nie punkty.)

### 2.3 Drobne, ale do zrobienia przed otwarciem

| Rzecz | Stan | Działanie |
|---|---|---|
| `tensura-2.0.8.jar` obok `2.0.18` w `mods/` | Martwy plik, NeoForge ładuje 2.0.18 | Usunąć |
| `enable-command-block=true` + `function-permission-level=2` | Otwarte dla graczy | Zamknąć |
| `chunkloaders` | Wektor lagu przy obcych | Limit per gracz albo wyciąć |
| `-Xmx4G` | Sufit dla ~5 osób | Patrz sekcja 4 |
| `difficulty=easy` | Do decyzji | Przy PvP i wojnach raczej `normal` |

---

## 3. Mody do dołożenia

Wszystko poniżej ma potwierdzone buildy na NeoForge 1.21.1.

| Rola | Mod | Po co |
|---|---|---|
| Claimy i ochrona | **FTB Chunks + FTB Teams** | Granice widoczne na mapie, grief poza claimem zablokowany. MineColonies claimuje kolonie, FTB Chunks resztę: posiadłości, forty, drogi |
| Komendy | **FTB Essentials** | `/home`, `/tpa`, `/back`, `/rtp` |
| Uprawnienia | **FTB Ranks** albo LuckPerms | Bez tego każdy zaufany gracz musi dostać opa |
| Logi i rollback | **GriefLogger + GriefLogger Rollback Addon** | Jedyny działający odpowiednik CoreProtect na moddowanym NeoForge; SQLite lub MySQL |
| Mapa web | **BlueMap** | Publiczna mapa z granicami — zarazem ilustracja do wątków dyplomatycznych na forum |
| Voice | **Simple Voice Chat** | Przy RP praktycznie obowiązkowy |
| Questy | **FTB Quests** | Kierunek dla nowego gracza i szkielet progresji |
| Discord | **Discord Integration** | Most czatu, statusy serwera |
| Profiler | **Spark** | Bez tego diagnoza spadków TPS to zgadywanie |
| Wydajność | **ModernFix** (FerriteCore i Noisium już są) | |

---

## 4. Infrastruktura

**Maszyna.** Minecraft tickuje jednowątkowo — liczy się zegar pojedynczego rdzenia,
nie liczba rdzeni.

| Graczy | Heap | CPU |
|---|---|---|
| 5 (dziś) | 4 GB | obecny wystarcza |
| 20–30 | **12–16 GB** | klasa 7800X3D / 9950X, dysk NVMe |

Kolonie MineColonies są najcięższym pojedynczym obciążeniem. **Limit kolonii na gracza
i minimalny dystans między nimi to ustawienia wydajnościowe, nie tylko rozgrywkowe** —
ustawić przed otwarciem, nie po pierwszym tąpnięciu.

**Reszta stosu — dziś standard, nie luksus:**

- Panel: Pterodactyl / Pelican.
- Java 21, flagi Aikara przestrojone pod większy heap (obecne są pod 4 GB).
- Restart dobowy + watchdog z auto-restartem.
- **Backupy off-site** — FTB Backups 3 → rclone → chmura. Dziś backupy leżą w
  `ftbbackups3/` na tym samym dysku co świat, czyli nie są backupem.
- DDoS: TCPShield, darmowy tier wystarcza.
- Domena + rekord SRV, żeby gracz wpisywał `play.<domena>` bez portu.
- Monitoring uptime z alertem na Discorda.

**Uwaga o hoście.** Pady 2k37 to historycznie brak pamięci hosta, nie mody. Przy
przenosinach na dedyk problem znika, ale przy zostaniu na obecnej maszynie
podniesienie heapu do 12 GB go pogłębi.

---

## 5. Dystrybucja packa — największa bariera wejścia

Większa niż hosting. Dziś dołączenie to ręczna instrukcja na 20 kroków.

- Opublikować pack na Modrinth jako `.mrpack`.
- Customowy jar tensury wchodzi w `overrides/mods/`, więc nie musi być nigdzie
  hostowany osobno.
- Gracz instaluje w Modrinth App albo Prism Launcher i dostaje auto-update.
- Mody klienckie (Embeddium, Oculus) wchodzą do packa; do folderu serwera nadal nie.

Bez tego połowa zainteresowanych odpada przed pierwszym wejściem.

---

## 6. Warstwa społeczna i play-by-forum

### 6.1 Podstawa

| Element | Decyzja | Uzasadnienie |
|---|---|---|
| Whitelist | **Zostaje**, przez podanie | Dla serwera RP to standard, nie przeszkoda; rozwiązuje grief lepiej niż mody |
| Karta postaci | Wymagana przed whitelistem | Filtr rekrutacyjny i materiał na RP w jednym |
| Platforma | Discord Forum Channels na start | Gracze już tam są, zero hostingu, wątki trwałe |
| Migracja | Discourse/Flarum przy >30 aktywnych | Discord gubi archiwum i wyszukiwanie po roku gry |
| Kanały IC | Wątek per królestwo + `#dyplomacja`, `#edykty`, `#kroniki`, `#wojny` | Rozdział IC/OOC rozjeżdża się pierwszy |
| Tempo | 1 post / 48h w wątkach dyplomatycznych | Bez deadline'u PBF umiera po trzecim tygodniu |
| Rozjemca | Jedna osoba (GM) rozstrzyga sporne akcje | Nie da się tego zapisać w regulaminie |

**Do spisania przed startem, nie po pierwszym konflikcie:** metagaming, godmodding,
co jest kanoniczne przy sprzeczności forum vs. stan świata (rekomendacja:
**świat w grze jest źródłem prawdy, forum jego interpretacją**), dziedziczenie po
graczu, który zniknął.

### 6.2 Most między forum a grą

Tu ginie większość serwerów PBF: jeśli ustalenia na forum nic nie zmieniają w świecie,
forum staje się fanfikiem obok gry. Minimum:

**Terytorium** — FTB Chunks + BlueMap (sekcja 3). Granica, którą widać, jest argumentem
w dyskusji.

**Tożsamość królestwa** — nazwa, kolor, herb, stolica = kolonia, prefiks w tab-liście
i na czacie. `ColonyGamemodeEvents` już trzyma `IColonyManager` i sprawdza, w której
kolonii stoi gracz, więc mapowanie `colonyId → królestwo` jest tanie.

**Dyplomacja i wojna — jedyne, co wymaga realnego kodu:**

- `KingdomData`: `colonyId → {nazwa, kolor, władca UUID, wasale[], skarbiec, relacje[]}`
- `/kingdom create|invite|vassal|declare-war|treaty|tax`
- Stan wojny jako okno czasowe, w którym PvP, grief i odbieranie obywateli (blok 2.1
  punkt 1) są dozwolone **tylko** między stronami i **tylko** w zadeklarowanych regionach.
  Bez tego albo wieczny PvP i odpadają budowniczowie, albo wieczny pokój i odpada polityka.
- Webhooki na Discorda: założenie kolonii, deklaracja wojny, upadek królestwa, zmiana
  władcy. Automatyczna kronika napędza forum sama.

**Ekonomia** — na NeoForge 1.21.1 nie ma dobrego gotowca. Minimum: waluta jako item +
skarbiec królestwa jako blok. Docelowo `/pay` i konta w `KingdomData`, rząd wielkości
300 linii.

### 6.3 Pułapka, którą trzeba rozwiązać przed startem

**MineColonies wymaga codziennej obsługi, a król na PBF loguje się dwa razy w tygodniu.**
Kolonia bez opieki staje, a po miesiącu „królestwo" to zamarła wioska.

Rekomendacja: **kolonia jest stolicą, nie całym królestwem.** Terytorium, wasale
i dyplomacja żyją w `KingdomData` niezależnie od kondycji kolonii. Król, który nie gra,
traci gospodarkę, ale nie traci państwa.

Uzupełniająco: podnieść automatyzację w configu MineColonies i rozważyć stan stabilności
(kolonia poniżej progu aktywności wchodzi w stasis zamiast się rozpadać).

---

## 7. Checklist przed otwarciem

**Blokery kodu**
- [x] Uprawnienia konwersji w trzech wejściach (sekcja 2.1; oczekuje playtestu)
- [ ] Migracja datapacka (spec katalizatora, krok 0)
- [ ] `KingdomData` + `/kingdom` + stan wojny

**Serwer**
- [ ] Usunąć `tensura-2.0.8.jar`
- [ ] Zamknąć command blocki, ograniczyć chunkloadery
- [ ] Limit kolonii na gracza + minimalny dystans
- [ ] Heap i maszyna pod docelową liczbę graczy
- [ ] Backupy off-site
- [ ] Restart dobowy + watchdog + monitoring

**Mody**
- [ ] FTB Chunks / Teams / Essentials / Ranks
- [ ] GriefLogger + Rollback Addon
- [ ] BlueMap, Simple Voice Chat, FTB Quests, Discord Integration, Spark, ModernFix

**Wejście gracza**
- [ ] Pack na Modrinth (`.mrpack`) z jarem tensury w `overrides/`
- [ ] Domena + SRV, TCPShield
- [ ] Discord: kanały IC/OOC, formularz podania, regulamin RP

---

## Otwarte pytania

- Czy wojna odblokowuje sam PvP, czy również grief i odbieranie obywateli?
- Czy każdy gracz może założyć królestwo, czy dopiero po akceptacji na forum?
- Docelowa liczba graczy — od niej zależy maszyna i limity kolonii.
- Czy zostajemy na obecnej maszynie, czy przenosiny na dedyk (patrz uwaga o hoście).
