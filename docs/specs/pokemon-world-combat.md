# Pokemon world combat

Status: wdrozone 2026-09-09; oczekuje pelnego buildu i playtestu
Zakres: combat companion AI dla wypuszczonych Pokemonow Cobblemon

Stan walidacji: `validate_done_spells.rb`, `validateSkillTrees`,
`validateResourceLayout`, diagnostyka Javy i `git diff --check` przechodza.
`compileJava` i `build` nie dotarly do kompilatora, poniewaz polaczenia z
`piston-meta.mojang.com` i `libraries.minecraft.net` sa resetowane, a lokalny
cache nie zawiera wszystkich bibliotek Minecrafta.

## 1. Cel

Wypuszczony Pokemon gracza uzywa poza walkami Cobblemon combat spelli Tensury
odpowiadajacych ruchom w swoim aktualnym movesecie. System jest reaktywny: Pokemon
nie szuka sam przeciwnikow i nie rozpoczyna walki z najblizszym agresywnym mobem.

## 2. Aktywacja

World combat dziala tylko, gdy Pokemon:

- ma aktywnego `ServerPlayer` jako wlasciciela;
- zostal normalnie wypuszczony do swiata;
- nie jest `tensura:village_resident`, fainted, usuniety ani recallowany;
- nie uczestniczy w walce Cobblemon (`!pokemon.isBattling()`);
- nie jest aktualnie dosiadany;
- zna co najmniej jeden ruch mapowany 1:1 na istniejacy spell Tensury.

Feature nie obejmuje dzikich Pokemonow, Pokemon-citizenow ani Pokemonow trenerow
NPC.

## 3. Targetowanie reaktywne

Pokemon przyjmuje cel tylko z jednego z trzech zrodel, w tej kolejnosci
priorytetu:

1. Mob atakujacy wlasciciela.
2. Mob atakujacy Pokemona.
3. Cel zaatakowany przez wlasciciela.

Nie ma okresowego skanowania pobliskich `Monster`. Pokemon nie atakuje graczy,
wlasciciela, samego siebie, Pokemonow wlasciciela, innych combat companionow ani
obywateli kolonii wlasciciela. Cel jest porzucany, gdy umrze, przestanie byc
legalny, Pokemon wejdzie do walki Cobblemon, zostanie dosiadany albo oddali sie
ponad 24 bloki od wlasciciela.

## 4. Wybor spella

- Zrodlem jest aktualny moveset Pokemona; niezmapowane ruchy sa pomijane.
- Nie ma aliasow ani fallbacku wedlug typu lub power.
- Spell musi byc gotowy, legalny dla celu i w swoim rzeczywistym zasiegu.
- Bezposrednie `aim`, projectile i beam wymagaja line of sight.
- AI preferuje spell pasujacy do odleglosci i nie powtarza ostatniego wyboru,
  jezeli istnieje gotowa alternatywa.
- Heal i recover sa dostepne ponizej 60% HP.
- Defensywne self spelle sa dostepne ponizej 75% HP albo po wejsciu w combat.
- Counter wymaga aktywnego przeciwnika.
- Offensive status moves sa legalnymi wyborami.
- AoE wymaga co najmniej jednego legalnego przeciwnika w obszarze.

V1 nie dodaje Pokemon type effectiveness przeciw vanilla mobom.

## 5. Cooldown i PP

- Cooldown jest kluczowany przez `(pokemon.getPokemon().getUuid(), spellId)`.
- Cooldown Pokemona jest niezalezny od gracza i innych Pokemonow.
- Recall i ponowne wypuszczenie nie resetuja cooldownu.
- Restart serwera moze wyczyscic cooldowny tak jak obecny runtime gracza.
- World combat nie zuzywa PP; PP pozostaje zasobem walk Cobblemon.
- Decyzja AI jest wykonywana najwyzej raz na 10 tickow.

## 6. Channeling i cast time

Companionowe `hold_to_channel` trwa maksymalnie 40 tickow. Recall, faint, wejscie
do walki Cobblemon, utrata celu, dosiadanie albo odejscie wlasciciela natychmiast
przerywa przygotowywanie i channel. Normalny cooldown zaczyna sie po udanym
rozpoczeciu spella.

## 7. Balans i atrybucja

- Pokemon uzywa 100% power definicji spella, bez companionowego mnoznika.
- Leczenie, recoil, cleanse i self buff odnosza sie do Pokemona jako effect caster.
- Obrazenia pozostaja przypisane wlascicielowi jako `playerAttack`, aby zachowac
  vanilla dropy, XP i integracje.
- Spell Pokemona nie korzysta z attack cooldownu ani broni wlasciciela.

## 8. Lifecycle

Jedna operacja detach usuwa target, aktywny cast, channel, dash, delayed hit,
strefe i inne efekty runtime nalezace do encji Pokemona. Jest wywolywana przy
recall, faint/smierci, wejsciu do walki Cobblemon, logout wlasciciela, usunieciu
encji, zmianie wymiaru i zastapieniu aktywnego Pokemona innym.

## 9. Kryteria akceptacji

1. Pokemon nie wybiera celu tylko dlatego, ze w poblizu znajduje sie zombie.
2. Pokemon odpowiada spellem na atak moba przeciw wlascicielowi.
3. Pokemon odpowiada spellem na atak moba przeciw sobie.
4. Pokemon atakuje legalny cel trafiony przez wlasciciela.
5. Nie atakuje gracza, pasywnego moba bez prowokacji ani chronionego sojusznika.
6. Po rozpoczeciu walki Cobblemon world cast i aktywne efekty sa przerwane.
7. Kazdy Pokemon ma osobne cooldowny, zachowane przez recall.
8. Heal nie jest wybierany przy pelnym zdrowiu, a channel zawsze sie konczy.
9. AoE i chain damage nie trafiaja chronionych sojusznikow.
10. Recall, faint i logout czyszcza aktywny runtime Pokemona.
11. Spell kill zachowuje atrybucje wlasciciela oraz normalne dropy i XP.
12. Walidator spelli, walidatory Gradle i pelny build przechodza.
