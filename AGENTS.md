# AGENTS.md

Ten plik jest glownym indeksem stanu repozytorium dla agentow AI. Przeczytaj go
przed planowaniem, edycja lub raportowaniem stanu. Nie wyciagaj liczb ani decyzji
projektowych z historii rozmowy, jesli mozna je sprawdzic w repozytorium.

## Zakres repozytorium

- `tensura-mod-neo/` to aktywny mod NeoForge 1.21.1 i domyslny obszar prac.
- `src/Phimosis/Main.java` to legacy plugin Bukkit. Nie zmieniaj go bez wyraznego
  polecenia.
- Komendy Gradle uruchamiaj z `tensura-mod-neo/`.
- Nie wdrazaj na serwer produkcyjny bez wyraznej zgody uzytkownika.

## Hierarchia zrodel prawdy

W przypadku sprzecznosci stosuj ponizsza kolejnosc:

1. Najnowsze wyrazne polecenie uzytkownika.
2. Dzialajacy kod, zasoby i generatory oraz wynik ich walidacji.
3. Kontrakty techniczne opisujace niezmienniki systemu.
4. Dokumenty planistyczne i trackery.
5. Historia rozmowy i stare podsumowania.

Nie traktuj planu jako wdrozenia. Nie traktuj liczby wpisow w drzewku Devour jako
liczby ukonczonych spelli.

## Rejestr dokumentow

| Dokument | Rola | Autorytatywny dla | Nie jest autorytatywny dla |
|---|---|---|---|
| `CLAUDE.md` | Ogolny opis repo i komendy | struktura projektow, deploy | biezacy roster i postep spelli |
| `tensura-mod-neo/SPELL_REWORK_PROPOSAL.md` | Plan docelowy | kanoniczny roster 110 i projekt przyszlych mechanik | aktualnie wdrozone spelle; sekcja stanu moze byc historycznym snapshotem |
| `tensura-mod-neo/DEVOUR_TREE_CONTRACT.md` | Kontrakt Puffish Skills | pary `_owned`/dispenser, `root: true`, koszt 0, nagrody i schemat 0.17.3 | aktualna liczba spelli i ikon, dopoki nie potwierdzi jej walidator |
| `tensura-mod-neo/PHOTON_VFX_AUTHORING.md` | Kontrakt VFX | runtime Photon, anchory, kierunki i eksport `.fx` | komplet aktualnych profili wszystkich spelli |
| `tensura-mod-neo/ANIMATION_FIX_PLAN.md` | Plan diagnostyczny | kolejnosc rozwazanych napraw animacji | wynik ostatniego testu |
| `tensura-mod-neo/ANIMATION_FIX_TRACKER.md` | Tracker eksperymentow | ostatni zapisany wynik testu animacji | stan spelli i rosteru |

Gdy powstaje nowy dokument projektowy lub tracker, dopisz go do tej tabeli i
okresl jednoznacznie, za co odpowiada.

## Aktualny checkpoint spelli

Stan zweryfikowany 2026-09-07:

- plan docelowy: 110 unikalnych spelli w `SPELL_REWORK_PROPOSAL.md`;
- implementacja done-done: 70 spelli;
- drzewko Devour: 18 rays i 88 par spell/dispenser;
- customowe ikony: 70;
- unikalne profile cast VFX: 70, w 8 rodzinach geometrii;
- `energy_ball`, `explosion`, `iron_tail`, `sacred_fire`, `scald`, `sludge_bomb`,
  `thunderbolt`, `water_pulse` i `will_o_wisp` zostaly trwale usuniete z runtime,
  mapowan i Devour;
- nowa rodzina `arc_strike` jest tylko zaplanowana, nie jest wdrozona;
- dziesiec dodatkow do planu 110 nie zostalo jeszcze wdrozonych jako jeden pakiet.

Checkpoint jest wskazowka startowa, nie zamiennikiem walidacji. Po zmianie rosteru,
generatorow, ikon, mapowan, VFX albo Devour zaktualizuj go dopiero po przejsciu
odpowiednich kontroli.

## Zasady zapobiegajace rozjazdom

Przed praca nad spellami jawnie ustal, ktorego poziomu dotyczy zadanie:

- `PLAN` - tylko roster, balans i opis przyszlej mechaniki;
- `IMPLEMENTATION` - Java, JSON, mapowania, ikony, VFX i runtime;
- `DEVOUR` - wygenerowane drzewko Puffish Skills;
- `ANIMATION` - osobny problem renderowania Pokemon-citizen.

Jesli polecenie mozna rozumiec jako plan albo implementacje, nie edytuj runtime.
Najpierw popros o rozstrzygniecie. Lista propozycji, dyskusja balansu i slowa
"planujemy" nie sa zgoda na implementacje.

Przed pierwsza zmiana:

1. Sprawdz `git status --short` i nie nadpisuj zmian uzytkownika.
2. Odczytaj dokument autorytatywny dla danego poziomu.
3. Sprawdz najblizszy kod, generator lub walidator kontrolujacy zachowanie.
4. Zapisz jedna hipoteze i jeden tani test, ktory moze ja obalic.

Po zmianie:

1. Uruchom najwezsza kontrole dla zmienionego obszaru.
2. Nie raportuj nowych liczb z pamieci; wyprowadz je z plikow lub skryptu.
3. Zaktualizuj dokument autorytatywny i ten checkpoint, jesli stan faktycznie sie
   zmienil.
4. W odpowiedzi rozdziel `wdrozone` od `zaplanowane`.

## Niezmienniki Devour

- Zachowaj 18 rays, dopoki uzytkownik jawnie nie zmieni architektury.
- Kazdy spell ma ukryty `<spell>_owned` i widoczny dispenser `<spell>` w tej samej
  pozycji.
- Kazdy `_owned` ma `root: true`, koszt 0, brak nagrod i niewidoczna oprawe.
- Polaczenie `<spell>_owned -> <spell>` jest wymagane.
- Dispenser uzywa `tensura devour_recover @s <spell>` i pozostaje wielokrotnego
  uzytku.
- `scripts/sync_devour_tree.rb` jest generatorem autorytatywnym. Nie poprawiaj
  wygenerowanego JSON recznie, jesli zmiana nalezy do generatora.

## Walidacja

Z katalogu `tensura-mod-neo/`:

```bash
ruby scripts/validate_done_spells.rb
./gradlew validateSkillTrees
./gradlew build
```

- Dla zmian dokumentacyjnych wykonaj co najmniej `git diff --check` oraz kontrole
  liczb, ktore dokument deklaruje.
- `scripts/validate_done_spells.rb` jest podstawowym zrodlem liczby done-done, ikon,
  profili VFX i par Devour.
- `validateSkillTrees` sprawdza kontrakt Puffish Skills.
- Pelny build moze byc zablokowany przez brak lokalnych zaleznosci albo dostep do
  serwerow Mojang. Raportuj taki blocker; nie przedstawiaj go jako bledu kodu.

## Aktualizacja tego pliku

Aktualizuj `AGENTS.md` w tej samej zmianie, gdy:

- zmienia sie liczba planowanych, wdrozonych lub dostepnych w Devour spelli;
- dochodzi lub znika dokument stanu/kontraktu;
- zmienia sie generator albo komenda walidacyjna;
- decyzja uzytkownika uniewaznia zapisana tu zasade.

Nie dopisuj spekulacji. Kazdy checkpoint musi miec date i przejsc walidacje.