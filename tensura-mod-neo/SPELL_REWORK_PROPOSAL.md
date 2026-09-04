# Rework pochlonietych spelli

Status: propozycja projektowa  
Zakres: 100 spelli, mechaniki RPG, balans, VFX, animacje i migracja

## Stan implementacji - 2026-09-04

Wdrozone:

- kompatybilne wstecz rozszerzenie `SpellDefinition` o Pokemon type, category, bezposrednie power, cast time, charges, parametry delivery, profile visual i sound;
- bezposrednie obrazenia nowych spelli niezalezne od vanilla attack cooldown;
- impacty `full_heal` i `cleanse` oraz wybor odbiorcy impactu;
- status `Asleep` dla graczy i mobow, z blokada ruchu/ataku oraz wybudzeniem po otrzymaniu obrazen;
- wspolny, serwerowy kontroler dashy z kolizja, limitem celow i deduplikacja trafien;
- `Quick Attack` jako czterotickowy dash na 6 blokow, zatrzymujacy sie na pierwszym celu;
- `Rest` jako instant full heal + cleanse, po ktorym nastepuje 5 sekund Asleep;
- mapowanie ruchow Cobblemona `quick_attack -> quick_attack` i `rest -> rest`;
- komplet pionowego wycinka: `Ember`, `Tri Attack`, `Vine Whip`, `Whirlpool`, `Ice Beam`, `Quick Attack`, `Future Sight`, `Sucker Punch`, `Iron Defense` i `Draco Meteor`;
- wspolny runtime vortex, delayed mark, counter, Guard, channelingu i grupowanych meteorow;
- statusy `Wet`, `Frozen`, `Paralyzed` i `Exhausted`, w tym combo Wet/Frozen;
- dwa regenerujace sie ladunki `Quick Attack`;
- widoczne modele projectile, telegraphy particles, tether, efekty impactu oraz dzwieki cast/impact.

Walidacja:

- analizator VS Code nie zglasza bledow w zmienionych klasach;
- wszystkie pliki JSON zasobow przechodza `jq empty`;
- JDK Temurin 21 i Gradle 8.10.2 zostaly skonfigurowane lokalnie;
- pelny `compileJava` dochodzi do kompilatora, ale repo nie zawiera ignorowanych plikow `libs/cobblemon.jar`, `libs/kotlinforforge.jar` i `libs/minecolonies.jar`. Bez zgodnych wersji tych trzech zaleznosci nie mozna zakonczyc pelnego buildu ani uruchomic klienta.

Nastepny krok po przywroceniu `libs`: test calego pionowego wycinka w kliencie i balans parametrow na podstawie rozgrywki.

## 1. Kierunek

Obecny system ma wiele nazw, ale niewiele odmiennych zachowan. Rework nie powinien jednak usuwac wszystkich prostych pociskow: sa naturalna czescia ruchow Pokemon i dobrze sprawdzaja sie jako szybkie, czytelne skille podstawowe.

Docelowe zalozenia:

- Roster zawiera dokladnie 100 ruchow o kanonicznych nazwach i typach Pokemon.
- Obslugujemy wszystkie 18 typow: `normal`, `fire`, `water`, `electric`, `grass`, `ice`, `fighting`, `poison`, `ground`, `flying`, `psychic`, `bug`, `rock`, `ghost`, `dragon`, `dark`, `steel`, `fairy`.
- Pokemon przekazuje tylko spell o tej samej nazwie i typie co ruch w jego movesecie. Usuwamy fallback zmieniajacy nieobslugiwany ruch w inny spell.
- Okolo 25% rosteru pozostaje pociskami. Roznia sie predkoscia, liczba, torem, naprowadzaniem, przebiciem i efektem trafienia, a nie tylko kolorem.
- Pozostale spelle wykorzystuja dash, melee, beam, channel, cone, wave, vortex, persistent zone, trap, counter, teleport i delayed cast.
- Obrazenia spelli nie zaleza od cooldownu zwyklego ataku Minecrafta.
- Friendly fire jest domyslnie wylaczony dla gracza, jego Pokemonow i obywateli tej samej kolonii.

## 2. Docelowy roster - 100 spelli

`DMG` oznacza bazowe HP przed pancerzem, a `CD` czas odnowienia w sekundach. Wartosci sa punktem startowym do testow balansu.

### Normal - 6

| Spell | DMG / CD | Delivery | Mechanika RPG | Animacja i VFX |
|---|---:|---|---|---|
| `Tackle` | 7 / 3 | Dash | Krotka szarza; pierwsze trafienie zatrzymuje ruch. | Pochylenie ciala, smuga predkosci i pyl przy hamowaniu. |
| `Quick Attack` | 8 / 5 | Dash | Blyskawiczna szarza przez jeden cel; dwa ladunki. | Biale afterimage gracza i ostry slash na wyjsciu. |
| `Swift` | 12 / 8 | Homing projectile | Trzy gwiazdy po 4 DMG; naprowadzanie slabnie za przeszkoda. | Obracajace sie modele gwiazd z jasnym lukiem. |
| `Tri Attack` | 15 / 12 | Projectile volley | Trzy pociski; ostatni naklada Burn, Chill albo Paralysis zalezne od aktywnej sekwencji. | Czerwony trojkat, niebieski romb i zolty okrag leca w ciasnej formacji. |
| `Hyper Voice` | 18 / 14 | Cone | Fala dzwieku przebija cele i przerywa przygotowywany cast. | Koncentryczne teksturowane fale od ust gracza i drganie powietrza. |
| `Hyper Beam` | 32 / 25 | Channel beam | Ladowanie 1.4 s, przebija cele; po uzyciu Exhausted na 4 s. | Rosnaca kula miedzy dlonmi, szeroki promien i fala uderzeniowa. |

### Fire - 6

| Spell | DMG / CD | Delivery | Mechanika RPG | Animacja i VFX |
|---|---:|---|---|---|
| `Ember` | 6 / 3 | Projectile | Szybki pocisk naklada Burn na 3 s. | Mala bryla zarzacego wegla, iskry i dym na trafieniu. |
| `Flame Charge` | 9 / 6 | Dash | Ognista szarza; trafienie daje 20% predkosci na 3 s. | Ognista otoczka i plonacy slad gasnacy za graczem. |
| `Flamethrower` | 14 / 9 | Channel cone | Strumien przez 1.2 s; kolejne ticki odnawiaja Burn. | Warstwowy ogien z bialym rdzeniem zamiast linii pojedynczych particles. |
| `Fire Spin` | 12 / 14 | Vortex | Wir przez 5 s lekko przyciaga i podpala cele. | Teksturowany cylinder ognia z czytelnym pustym srodkiem. |
| `Fire Blast` | 22 / 17 | Projectile AoE | Wolny duzy pocisk wybucha w promieniu 4 m i naklada Burn. | Symbol ognia formuje sie podczas lotu, potem rozpada na piec ramion eksplozji. |
| `Overheat` | 28 / 22 | Cone burst | Szeroki wybuch przed graczem; Exhausted na 6 s. | Bialy rdzen, pomaranczowy front ciepla i gesty dym po eksplozji. |

### Water - 6

| Spell | DMG / CD | Delivery | Mechanika RPG | Animacja i VFX |
|---|---:|---|---|---|
| `Water Gun` | 7 / 3 | Beam | Krotki strumien naklada Wet i odpycha. | Gest wskazania, teksturowany strumien i splash na celu. |
| `Bubble Beam` | 10 / 7 | Projectile cluster | Piec wolnych baniek; pekajac tworza na 2 s male pola spowolnienia. | Przezroczyste banki o roznych rozmiarach i mokry rozbryzg. |
| `Aqua Jet` | 10 / 6 | Dash | Sterowalny dash; trafienie naklada Wet. | Wodna otoczka wokol gracza i rozbryzg na koncu. |
| `Whirlpool` | 14 / 15 | Vortex | Lej przez 5 s przyciaga do centrum i naklada Wet. | Obracajaca sie woda z piankowym brzegiem i opadajacym srodkiem. |
| `Surf` | 20 / 18 | Wave | Szeroka fala jedzie po podlozu, niesie cele i naklada Wet. | Model fali z grzebieniem; mokry slad znika po 2 s. |
| `Hydro Pump` | 25 / 20 | Channel beam | Ladowanie 0.8 s; silny strumien odpycha wraz z kolejnymi tickami. | Gruba spirala wody, mgla przy bokach i duzy splash na przeszkodzie. |

### Electric - 6

| Spell | DMG / CD | Delivery | Mechanika RPG | Animacja i VFX |
|---|---:|---|---|---|
| `Thunder Shock` | 6 / 3 | Projectile | Szybki bolt daje 1 stack Paralysis; skacze do jednego Wet celu. | Poszarpany pocisk pradu i krotkie iskry na ciele. |
| `Spark` | 9 / 6 | Dash | Krotka szarza; przy Wet celu razi takze najblizszego przeciwnika. | Elektryczna otoczka, rozgalezienie na trafieniu i afterglow. |
| `Electro Ball` | 8-18 / 8 | Homing projectile | Obrazenia rosna wraz z przewaga predkosci rzucajacego. | Kula gestnieje i rosnie zalezne od wyliczonej mocy. |
| `Volt Tackle` | 23 / 16 | Dash | Dluga szarza z duzym Stagger; rzucajacy otrzymuje 25% recoil. | Gruba powloka pradu, jasny impact frame i iskry cofajace sie do gracza. |
| `Discharge` | 16 / 14 | Radial burst | Wet cele przewodza atak do jednego kolejnego celu. | Pierscien elektryczny po ziemi i lancuchy miedzy jednostkami. |
| `Thunder` | 26 / 22 | Delayed | Uderzenie po telegraphie 1 s; Wet gwarantuje pelny buildup Paralysis. | Znacznik na ziemi, blysk chmur i gruby bolt z afterglow. |

### Grass - 6

| Spell | DMG / CD | Delivery | Mechanika RPG | Animacja i VFX |
|---|---:|---|---|---|
| `Vine Whip` | 7 / 4 | Tether | Przyciaga trafiony cel do 3 blokow w strone gracza. | Teksturowane pnacze laczy dlon z celem i napina sie przed pull. |
| `Razor Leaf` | 11 / 7 | Projectile fan | Wachlarz pieciu lisci; jeden cel moze dostac maksymalnie trzy trafienia. | Modele lisci wiruja po lekko zakrzywionych torach. |
| `Leech Seed` | 4 / 14 | Projectile status | Seeded na 8 s; co sekunde przenosi 2 HP do rzucajacego. | Nasiono kielkuje na celu, a zielona wstega wraca do gracza. |
| `Giga Drain` | 14 / 13 | Channel tether | Kanal przez 1.5 s leczy za 60% faktycznie zadanych obrazen. | Kilka zielonych wsteg wyciaga energie z celu do klatki piersiowej gracza. |
| `Petal Blizzard` | 17 / 15 | Moving zone | Burza platkow otacza gracza przez 4 s i odpycha pobliskie cele. | Gesty wir modeli platkow z okazjonalnymi jasnymi cieciami. |
| `Solar Beam` | 27 / 22 | Channel beam | Ladowanie 1.8 s, w swietle dnia 1 s; przebija cele. | Swiatlo zbiera sie z gory w orb, potem tworzy zielono-zloty beam. |

### Ice - 6

| Spell | DMG / CD | Delivery | Mechanika RPG | Animacja i VFX |
|---|---:|---|---|---|
| `Powder Snow` | 6 / 4 | Cone | Krotki podmuch naklada 1 Chill. | Mgla przy ziemi, drobny snieg i oszronienie trafionego celu. |
| `Ice Shard` | 7 / 3 | Projectile | Bardzo szybki odlamek daje 1 Chill; dwa ladunki. | Ostry model lodu, zimna smuga i pekniecie przy trafieniu. |
| `Icy Wind` | 10 / 9 | Cone | Szeroki podmuch naklada 2 Chill i odpycha lekkie cele. | Platki, mgla i kierunkowe smugi wiatru. |
| `Ice Beam` | 15 / 13 | Beam | Daje 2 Chill; Wet cel zostaje natychmiast Frozen. | Niebiesko-bialy promien, narastajacy lod i efekt rozbicia. |
| `Aurora Veil` | 0 / 20 | Self dome | Przez 8 s redukuje o 25% obrazenia sojusznikow wewnatrz kopuly. | Polprzezroczysta kurtyna zorzy z heksagonalnym refleksem przy trafieniu. |
| `Blizzard` | 22 / 22 | Moving zone | Burza przez 6 s podaza powoli do celu i naklada Chill co 2 s. | Gesty lokalny snieg z wirem, lecz z widoczna granica zagrozenia. |

### Fighting - 6

| Spell | DMG / CD | Delivery | Mechanika RPG | Animacja i VFX |
|---|---:|---|---|---|
| `Mach Punch` | 8 / 4 | Dash melee | Krotki dash-punch i maly Stagger. | Animacja ciosu, kompresja powietrza i okragly impact frame. |
| `Force Palm` | 11 / 7 | Melee cone | Odrzut; trafienie z bliska daje 1 Paralysis. | Dlon rozswietla sie przed fala cisnienia w ksztalcie dysku. |
| `Drain Punch` | 14 / 10 | Melee | Leczy rzucajacego za 40% faktycznie zadanych obrazen. | Zielona energia zostaje wyrwana z celu i wraca do piesci. |
| `Seismic Toss` | 14 / 13 | Grab | Chwyta zwykly cel i rzuca nim w kierunku celownika; boss dostaje Stagger bez podnoszenia. | Luk ruchu celu, pyl przy ladowaniu i mocny ground impact. |
| `Close Combat` | 24 / 16 | Melee combo | Trzy ciosy w 0.8 s; po combo rzucajacy dostaje 1 Exposed. | Sekwencja lewy-prawy-finisher z trzema roznymi impactami. |
| `Focus Blast` | 27 / 20 | Projectile AoE | Wolny duzy pocisk, eksplozja 3 m i Stagger; cast 1 s. | Aura skupia sie w dloniach, ciezki orb deformuje trail przed wybuchem. |

### Poison - 5

| Spell | DMG / CD | Delivery | Mechanika RPG | Animacja i VFX |
|---|---:|---|---|---|
| `Poison Sting` | 5 / 3 | Projectile volley | Trzy igly; komplet trafien naklada 1 Poison. | Cienkie fioletowe kolce z kroplami toksyny. |
| `Acid Spray` | 7 / 7 | Cone | Kwas daje 1 Exposed i Poison na 4 s. | Zielono-fioletowe krople, opary i syczenie na pancerzu. |
| `Venoshock` | 11 / 8 | Projectile AoE | Podwaja bezposrednie obrazenia na celu z Poison albo Toxic. | Fioletowy orb sciska sie przed uderzeniem i rozpryskuje krople. |
| `Toxic Spikes` | 0 / 15 | Trap | Trzy pulapki na 20 s; pierwsze wejscie daje Poison, kolejne Toxic. | Kolce wysuwaja sie z ziemi i maja subtelny fioletowy telegraph. |
| `Toxic` | 0 / 20 | Homing projectile | Naklada rosnacy Toxic na 10 s; boss na 6 s. | Ciezka kropla z dymnym ogonem, pulsujaca coraz szybciej. |

### Ground - 5

| Spell | DMG / CD | Delivery | Mechanika RPG | Animacja i VFX |
|---|---:|---|---|---|
| `Mud Shot` | 7 / 4 | Projectile | Spowalnia o 30% i daje Exposed na 4 s. | Bryla blota zostawia krople i plame znikajaca po chwili. |
| `Bulldoze` | 11 / 8 | Ground cone | Fala ziemi spowalnia cele stojace na podlozu. | Bloki nie sa niszczone; wizualne plyty gruntu przesuwaja sie do przodu. |
| `Dig` | 16 / 12 | Burrow dash | Gracz znika pod ziemia na maks. 1.5 s i wyskakuje pod celem. | Zapadniecie w pyl, ruchomy slad ziemi i erupcja przy wyjsciu. |
| `Earth Power` | 18 / 14 | Delayed line | Seria erupcji biegnie po ziemi i daje 1 Exposed. | Pomaranczowe pekniecia poprzedzaja kazdy pionowy wybuch energii. |
| `Earthquake` | 23 / 20 | Radial waves | Trzy fale; nie trafia celow wysoko nad ziemia. | Pekniecia i pyl biegna pierscieniami bez niszczenia blokow. |

### Flying - 5

| Spell | DMG / CD | Delivery | Mechanika RPG | Animacja i VFX |
|---|---:|---|---|---|
| `Gust` | 6 / 3 | Cone | Odpycha cele i lekkie wrogie pociski. | Przezroczyste luki powietrza, liscie i pyl pokazuja kierunek. |
| `Air Cutter` | 10 / 6 | Projectile fan | Trzy szerokie ostrza powietrza przebijaja pierwszy cel. | Jasne polksiezyce obracaja sie lekko podczas lotu. |
| `Aerial Ace` | 12 / 7 | Targeted dash | Przelot przez namierzony cel konczy sie za jego plecami. | Biala smuga w ksztalcie skrzydla i krzyzowe ciecie. |
| `Tailwind` | 0 / 18 | Moving aura | Przez 8 s sojusznicy w poblizu maja 25% predkosci ruchu. | Kierunkowe wstegi powietrza oplataja nogi druzyny. |
| `Hurricane` | 22 / 20 | Vortex | Tornado przez 5 s przyciaga, unosi i wyrzuca na koncu. | Teksturowany lej z odlamkami; kierunek wyrzutu jest pokazany wczesniej. |

### Psychic - 6

| Spell | DMG / CD | Delivery | Mechanika RPG | Animacja i VFX |
|---|---:|---|---|---|
| `Confusion` | 6 / 5 | Telekinetic throw | Krotko podnosi cel, odrzuca do celownika i naklada Confused. | Fioletowe pierscienie wokol glowy i telekinetyczna fala. |
| `Psybeam` | 11 / 8 | Ricochet beam | Odbija sie raz od bloku albo jednego dodatkowego celu. | Warstwowy teczowy promien z wyraznym punktem odbicia. |
| `Rest` | 0 / 90 | Self heal | Natychmiast przywraca pelne HP i oczyszcza negatywne statusy, po czym naklada Asleep na 5 s. Obrazenia moga obudzic dopiero po 2 s. | Gracz siada lub opuszcza ramiona, otacza go spokojna niebieska aura i trzy unoszace sie symbole snu. |
| `Psychic` | 17 / 14 | Hold and throw | Przytrzymuje cel do 1.5 s; ponowne uzycie rzuca go w wybranym kierunku. | Przezroczysta aura i linie wskazujace kierunek rzutu. |
| `Trick Room` | 0 / 24 | Zone | Przez 8 s szybkie jednostki sa spowolnione, a wolne przyspieszone. | Odwrocona przezroczysta kostka z siatka i rotujacymi rogami. |
| `Future Sight` | 28 / 22 | Delayed mark | Znacznik na celu wybucha po 3 s nawet po utracie line of sight. | Runa oka, trzy pulsy odliczania i jasny implozyjny impact. |

### Bug - 5

| Spell | DMG / CD | Delivery | Mechanika RPG | Animacja i VFX |
|---|---:|---|---|---|
| `String Shot` | 0 / 5 | Projectile status | Rooted na 2 s; boss i gracz na 1 s. | Nici lacza cel z podlozem i pekaja po zakonczeniu efektu. |
| `Pin Missile` | 3-15 / 7 | Projectile sequence | Wystrzeliwuje do pieciu igiel; kolejne wymagaja podtrzymania celownika. | Kazda igla ma osobny swist i doklada wizualny znacznik trafienia. |
| `U-turn` | 11 / 7 | Return dash | Dash przez cel, po trafieniu automatyczny odskok do startu. | Zielony luk wejscia i szybszy afterimage powrotu. |
| `X-Scissor` | 18 / 12 | Melee arcs | Dwa przecinajace sie ciecia; trafienie oboma daje Exposed. | Dwa duze slashe ukladaja sie w znak X. |
| `Bug Buzz` | 16 / 13 | Channel cone | Fala przez 1 s przerywa cast i daje Exposed na 4 s. | Gesty wzor fal dzwiekowych przypominajacy skrzydla owada. |

### Rock - 5

| Spell | DMG / CD | Delivery | Mechanika RPG | Animacja i VFX |
|---|---:|---|---|---|
| `Rock Throw` | 8 / 4 | Projectile | Ciezki lobowany kamien z malym knockbackiem. | Obracajacy sie model kamienia, pyl i odlamki na bloku. |
| `Smack Down` | 9 / 7 | Homing projectile | Sciaga lecacy cel na ziemie i blokuje lot na 3 s. | Glaz uderza z gory; powietrzny cel ciagnie za soba pyl podczas upadku. |
| `Rock Tomb` | 12 / 11 | Trap zone | Trzy skaly zamykaja obszar, spowalniajac wyjscie przez 4 s. | Glazy wyrastaja po bokach, ale nie tworza trwalych blokow. |
| `Rock Slide` | 15 / 12 | Delayed line | Trzy spadajace glazy; srodkowy powoduje Stagger. | Cienie na ziemi rosna przed upadkiem, glazy pekaja na kawalki. |
| `Stone Edge` | 23 / 17 | Ground line | Linia kolcow przebija 4 punkty pancerza. | Sekwencyjnie wyrastajace ostre skaly i fala pylu. |

### Ghost - 6

| Spell | DMG / CD | Delivery | Mechanika RPG | Animacja i VFX |
|---|---:|---|---|---|
| `Lick` | 5 / 4 | Melee arc | Krotki zasieg; daje 1 Paralysis i Confused na 2 s. | Widmowy luk przypominajacy jezyk znika w dymie za celem. |
| `Shadow Sneak` | 9 / 6 | Teleport strike | Teleport do cienia celu w zasiegu 10 m i szybkie ciecie. | Gracz rozpada sie w cien, ktory plynie po ziemi i sklada sie za celem. |
| `Night Shade` | 8-16 / 8 | Instant mark | Obrazenia rosna z maksymalnym HP celu, z limitem przeciw bossom. | Ciemna sylwetka celu odrywa sie i uderza z powrotem. |
| `Hex` | 10-20 / 10 | Instant curse | Podwojone obrazenia, jezeli cel ma negatywny status. | Runy statusow sa wciagane do fioletowego znaku klatwy. |
| `Shadow Ball` | 18 / 13 | Projectile | Przenika jednego przeciwnika i wybucha na drugim albo bloku. | Ciemny rdzen, spiralny trail i implozja zamiast zwyklego wybuchu. |
| `Phantom Force` | 24 / 18 | Delayed teleport | Gracz znika na 1 s, po czym uderza za celem; miejsce wyjscia ma telegraph. | Zapadniecie w portal-cien i pionowe rozdarcie przestrzeni przy powrocie. |

### Dragon - 6

| Spell | DMG / CD | Delivery | Mechanika RPG | Animacja i VFX |
|---|---:|---|---|---|
| `Dragon Breath` | 11 / 7 | Channel cone | Pelne trafienie daje 1 Paralysis. | Fioletowo-niebieski oddech z falujacym rdzeniem i iskrami. |
| `Dragon Tail` | 13 / 9 | Melee sweep | Szeroki zamach z silnym odrzutem; przerywa cast. | Smoczy ogon energii podaza za obrotem ciala. |
| `Dragon Rush` | 17 / 12 | Steerable dash | Sterowalna szarza; centralne trafienie daje Stagger. | Aura glowy smoka otacza gracza i rozpada sie na luski. |
| `Dragon Pulse` | 18 / 12 | Projectile | Spiralny pocisk przebija do trzech celow. | Dwie helisy oplataja jasny rdzen pocisku. |
| `Outrage` | 27 / 19 | Forced combo | Trzy szarze w ciagu 3 s; po zakonczeniu Confused na 4 s. | Coraz silniejsza smocza aura i trzy rozne kierunki ciecia. |
| `Draco Meteor` | 31 / 26 | Delayed meteor | Cast 1.5 s, meteory spadaja przez 2 s; potem Exhausted na 6 s. | Modele meteorow z ogonem, cienie uderzen i rozrzut skal. |

### Dark - 5

| Spell | DMG / CD | Delivery | Mechanika RPG | Animacja i VFX |
|---|---:|---|---|---|
| `Bite` | 8 / 4 | Lunge melee | Trafienie od tylu powoduje Stagger. | Dwie polprzezroczyste szczeki zaciskaja sie na celu. |
| `Snarl` | 10 / 8 | Cone | Cel zadaje 20% mniej special damage przez 5 s. | Ciemna fala dzwieku i ostre, zanikajace linie wokol glowy celu. |
| `Sucker Punch` | 14 / 10 | Counter | Postawa 1 s; kontruje przeciwnika, gdy ten zaczyna atak albo cast. | Ciemny blysk na graczu i natychmiastowy afterimage przy kontrze. |
| `Dark Pulse` | 16 / 11 | Expanding ring | Pierscien odpycha i przerywa przygotowywane casty. | Czarno-fioletowa fala z ostrymi krawedziami i pulsujacym dzwiekiem. |
| `Foul Play` | 10-23 / 15 | Instant strike | Skaluje obrazenia z sila ataku celu zamiast rzucajacego. | Cien przeciwnika wykonuje cios przeciw wlascicielowi. |

### Steel - 5

| Spell | DMG / CD | Delivery | Mechanika RPG | Animacja i VFX |
|---|---:|---|---|---|
| `Bullet Punch` | 8 / 4 | Dash melee | Blyskawiczny cios ignoruje 2 punkty pancerza. | Metaliczny blysk na piesci i krotki stozek cisnienia. |
| `Metal Claw` | 10 / 7 | Melee arcs | Dwa ciecia; trafienie oboma daje rzucajacemu Guard 4 HP. | Chromowane slady pazurow i iskry przy kontakcie z pancerzem. |
| `Iron Defense` | 0 / 18 | Self guard | Guard 16 HP na 8 s i odpornosc na knockback podczas oslony. | Metalowe plyty skladaja sie wokol gracza i pekaja wraz z Guard. |
| `Iron Head` | 16 / 11 | Dash melee | Krotka szarza z duzym Stagger na centralnym trafieniu. | Metaliczna powloka glowy/ramion i okragla fala uderzeniowa. |
| `Flash Cannon` | 17 / 12 | Beam | Ladowanie 0.8 s; przebija cele i daje 1 Exposed. | Chromowana kula, bialy rdzen promienia i pryzmatyczny impact. |

### Fairy - 5

| Spell | DMG / CD | Delivery | Mechanika RPG | Animacja i VFX |
|---|---:|---|---|---|
| `Fairy Wind` | 6 / 3 | Projectile | Szybka spiralna wstega lekko odpycha cel. | Perlowy ribbon trail z drobnymi pryzmatycznymi iskrami. |
| `Draining Kiss` | 8 / 6 | Homing projectile | Leczy za 75% faktycznie zadanych obrazen. | Rozowa wstega wraca od celu do rzucajacego. |
| `Charm` | 0 / 11 | Cone status | Trafione cele zadaja 25% mniej physical damage przez 6 s. | Miekka fala swiatla i pekajacy symbol nad oslabionym celem. |
| `Dazzling Gleam` | 14 / 12 | Radial dome | AoE oczyszcza z rzucajacego jeden stack Poison albo Chill. | Krysztalowy blysk rozszerza sie jako kopula z teczowym brzegiem. |
| `Moonblast` | 23 / 18 | Projectile AoE | Cel zadaje 25% mniej special damage przez 5 s. | Dysk ksiezyca za graczem zasila duzy perlowy pocisk. |

## 3. Podsumowanie roznorodnosci

Docelowy udzial archetypow:

| Rodzina | Liczba | Udzial |
|---|---:|---:|
| Prosty, wielokrotny lub homing projectile | 25 | 25% |
| Dash, teleport, burrow i melee | 24 | 24% |
| Beam, channel i cone | 20 | 20% |
| Zone, aura, vortex, trap i wave | 16 | 16% |
| Delayed, mark, counter, grab, self i instant control | 15 | 15% |

Pociski pozostaja wazne, ale nie dominuja rosteru. Nawet w tej grupie wystepuja: salwy, wachlarze, homing, lob, pocisk przebijajacy, sekwencja podtrzymywana, orb skalowany predkoscia i pocisk eksplodujacy.

## 4. Fundament systemu RPG

### 4.1. Definicja spella

Kazdy JSON powinien definiowac:

- `pokemon_type` - jeden z 18 typow zamiast obecnych umownych szkol;
- `category` - `physical`, `special` albo `status`;
- `power` - bazowe obrazenia w HP, bez ukrytego mnoznika stalej `6`;
- `cooldown_ticks` i `cast_time_ticks`;
- `charges` - opcjonalna liczba ladunkow i czas odzyskania jednego ladunku;
- `delivery` - sposob wykonania oraz jego parametry;
- `targeting` - zasieg, promien, szerokosc, maksymalna liczba celow;
- `impacts` - damage, heal, status, pull, knockback, root, guard i cleanse;
- `visual` - profile cast, projectile, trail, telegraph, impact i aftermath;
- `sound` - osobne zdarzenia dla cast, travel, impact i loop.

Obrazenia powinny byc liczone bez `getAttackStrengthScale()`. W pierwszej wersji wystarczy:

```text
final_damage = power * category_modifier * target_mitigation
```

`category_modifier` moze pozniej korzystac ze statystyk gracza. Na start wynosi `1.0`, aby rework mechanik nie byl jednoczesnie reworkiem calego progression.

### 4.2. Delivery

| Delivery | Zachowanie |
|---|---|
| `projectile` | Widoczna encja z modelem, kolizja i trailem. |
| `homing_projectile` | Lagodnie koryguje tor, ale traci cel za przeszkoda. |
| `beam` | Jednorazowa linia z natychmiastowym trafieniem. |
| `channel` | Powtarza efekt podczas podtrzymywania; Stagger przerywa cast. |
| `cone` | Stozek przed graczem dla oddechow, sprayow i fal dzwieku. |
| `dash` | Przemieszcza rzucajacego i sprawdza trafienia na calej trasie. |
| `melee_arc` | Krotki luk albo combo w zasiegu walki wrecz. |
| `zone` | Serwerowa encja obszaru tickujaca przez zadany czas. |
| `trap` | Obszar aktywowany przez wejscie przeciwnika. |
| `vortex` | Strefa przyciagajaca do swojego srodka. |
| `wave` | Szeroki efekt poruszajacy sie po ziemi. |
| `delayed` | Telegraph, odliczenie i pozniejsze uderzenie. |
| `counter` | Krotkie okno reagujace na atak lub rozpoczecie castu przeciwnika. |
| `self` | Buff, oslona lub aura rzucajacego. |

### 4.3. Statusy

| Status | Dzialanie |
|---|---|
| `Burn` | Obrazenia co sekunde i 20% slabsze ataki physical. |
| `Poison` | Stale obrazenia okresowe; maksymalnie 3 stacki. |
| `Toxic` | Obrazenia rosna z kazda sekunda trwania; reset po wygasnieciu. |
| `Paralysis` | 30% wolniejszy ruch i cast. Pelny buildup wywoluje Stagger. |
| `Chill` | 15% spowolnienia za stack. Trzy stacki wywoluja Frozen. |
| `Frozen` | Root na 1.5 s; otrzymane bezposrednie obrazenia rozbijaja lod. |
| `Rooted` | Blokuje ruch poziomy, ale pozwala patrzec, atakowac i rzucac. |
| `Stagger` | Przerywa cast i blokuje nowy przez 0.5 s. |
| `Seeded` | Odbiera HP celowi i leczy wlasciciela nasiona. |
| `Confused` | 25% slabsze namierzanie i 20% slabsze obrazenia przez 4 s. |
| `Exposed` | Redukuje pancerz o 2 punkty za stack; maksymalnie 2 stacki. |
| `Guard` | Dodatkowa pula HP absorbujaca obrazenia przed zdrowiem. |
| `Exhausted` | 25% slabsze obrazenia i wolniejszy cast po ultimate. |
| `Wet` | Water combo tag na 5 s: wzmacnia Electric i Ice, oslabia pierwszy Burn. |
| `Asleep` | Blokuje ruch, ataki i spelle. Obrazenia budza po minimalnym czasie ochronnym efektu. |

Bossowie i gracze otrzymuja o 50% krotsze Rooted/Frozen i o 50% slabszy pull. Po twardym CC cel dostaje trzysekundowa odpornosc na kolejny Stagger oraz malejacy czas kolejnych unieruchomien przez 8 s. Companion uzywa tych samych zasad, ale zadaje 70% power i ma dodatkowo o 50% slabszy hard CC przeciw graczom.

## 5. Tozsamosc typow i synergie

| Typ | Rola RPG |
|---|---|
| Normal | Uniwersalne skille, mobilnosc i mocne finiszery z kosztem. |
| Fire | Burn, obrazenia w czasie i kontrola przestrzeni. |
| Water | Wet, odrzut, fale i grupowanie przeciwnikow. |
| Electric | Lancuchy, Paralysis i premie przeciw Wet. |
| Grass | Pull, Root, drain i podtrzymanie. |
| Ice | Chill, Frozen, bariery i spowolnienie obszaru. |
| Fighting | Dash, combo, Stagger i ryzyko walki z bliska. |
| Poison | Stacki DoT, pulapki i egzekucja zatrutych celow. |
| Ground | Fale po ziemi, erupcje i ograniczenie do celow naziemnych. |
| Flying | Mobilnosc, podrzut, odrzut i tornada. |
| Psychic | Pull, hold, rzut i opoznione ataki. |
| Bug | Root, szybkie wejscie/wyjscie i wielokrotne trafienia. |
| Rock | Ciezkie pociski, Stagger i przebicie pancerza. |
| Ghost | Teleport, klatwy i premie na cele ze statusem. |
| Dragon | Szerokie, mocne ataki z dlugim castem albo kosztem. |
| Dark | Kontry, przerwanie castu i wykorzystanie sily celu. |
| Steel | Guard, Exposed, odpornosc i przebicie. |
| Fairy | Oslabienie przeciwnika, oczyszczanie i bezpieczne AoE. |

Najwazniejsze kombinacje:

| Setup | Finisher | Rezultat |
|---|---|---|
| Water naklada Wet | Electric | Dodatkowy chain lub szybsza Paralysis. |
| Water naklada Wet | Ice | Dodatkowy Chill albo natychmiastowe Frozen z Ice Beam. |
| Wet | Fire | Pierwszy Fire hit usuwa Wet i nie naklada Burn. |
| Poison/Toxic | Venoshock | Podwojone bezposrednie obrazenia. |
| Dowolny debuff | Hex | Podwojone obrazenia. |
| Pull/Vortex | Zone/AoE | Grupowanie pod Fire Spin, Blizzard, Earthquake lub Discharge. |
| Exposed | Physical | Pancerz celu jest mniej skuteczny. |
| Guard | Melee/Dash | Bezpieczniejsze wejscie w zasieg walki wrecz. |

Nie dodajemy pelnego systemu Pokemon super-effective w pierwszym etapie. Bez typow przypisanych wszystkim vanilla mobom bylby arbitralny. Pozniej mozna go wlaczyc dla Pokemonow i citizen-species jako osobny modul.

## 6. Rework animacji i grafiki

### 6.1. Piec faz spella

Kazdy rozbudowany skill moze miec piec faz:

1. `Anticipation` - poza gracza i pierwsze particles.
2. `Telegraph` - kierunek lub miejsce mocnego ataku.
3. `Release` - wystrzelenie, dash albo aktywacja.
4. `Impact` - hit VFX, dzwiek i opcjonalny lokalny camera shake.
5. `Aftermath` - slad, pekniecia, dym, strefa albo attachment statusu.

Szybkie pociski, takie jak Ember i Ice Shard, nie potrzebuja dlugiego telegraphu. Hyper Beam, Thunder, Future Sight i Draco Meteor musza byc zapowiedziane przed zadaniem obrazen.

### 6.2. Profile animacji gracza

- `cast_point` - jedna reka wskazuje cel;
- `cast_two_hand` - ladowanie miedzy dlonmi;
- `cast_overhead` - energia zbierana nad glowa;
- `channel_forward` - podtrzymywanie beam albo cone;
- `ground_slam` - uderzenie w ziemie;
- `melee_left`, `melee_right`, `melee_finisher`;
- `dash_forward`, `dash_cross`, `dash_return`;
- `throw_overhead` - rzut kamieniem albo celem;
- `self_guard` - skrzyzowanie ramion i rozlozenie oslony.

Minimalny wariant bez nowej zaleznosci modyfikuje pozy ramion w render eventach i synchronizuje `animation_id`, `start_tick` i `duration` pakietem. Pelne animacje calego ciala, blendowanie z chodem oraz first-person beda latwiejsze z biblioteka typu Player Animator, ale zgodnosc konkretnej wersji z NeoForge 1.21.1 trzeba potwierdzic przed dodaniem.

### 6.3. Wspolna biblioteka VFX

- billboard projectile dla energii, plomieni i prostych pociskow;
- modelowany projectile dla skal, lodu, lisci, gwiazd i meteorow;
- ribbon trail zapisujacy kilka ostatnich pozycji;
- beam jako teksturowana tuba albo krzyzujace sie plaszczyzny, nie rzad particles;
- cone jako kilka animowanych warstw;
- ring/dome telegraph na ziemi;
- persistent zone z osobna serwerowa encja efektu;
- decal/slad na podlozu z ograniczonym czasem zycia;
- lancuch miedzy encjami dla Electric, drain i tether;
- attachment statusu przy stopach, nad glowa albo wokol ciala;
- prosty model bloku/odlamka dla efektow Ground i Rock bez zmiany swiata.

Modele pociskow i stref nie wymagaja GeckoLib. Wlasne `EntityRenderer`, modele Blockbench/JSON, sprite sheets i custom particles wystarcza dla calego bazowego rosteru. GeckoLib ma sens dopiero przy zlozonych summonach lub encjach ze szkieletem.

### 6.4. Jezyk wizualny typow

- Normal: neutralne smugi i czytelna geometria ruchu.
- Fire: warstwy goraca, iskry, dym i bialy rdzen.
- Water: ribbon trails, pianka, bryzgi i mokre slady.
- Electric: nieregularne segmenty, afterglow i rozgalezienia.
- Grass: modele lisci/pnaczy, pylki i kielkowanie.
- Ice: ostre bryly, mgla, szron i pekniecia.
- Fighting: luki cisnienia i bardzo krotkie impact frames.
- Poison: lepkie krople, opary i pulsowanie toksyny.
- Ground/Rock: ciezkie modele, pyl i ruch zaczynajacy sie od podloza.
- Flying: przezroczyste luki i czastki otoczenia pokazujace przeplyw.
- Psychic: czyste pierscienie, refrakcja i kontrolowany ruch geometryczny.
- Bug: nici, szybkie ciecia i segmentowe wzory.
- Ghost/Dark: implozje, cienie i zanikanie krawedzi.
- Dragon: helisy, szerokie oddechy i wysoka gestosc energii.
- Steel: odbicia, ostre highlighty i skladajace sie plyty.
- Fairy: perlowe swiatlo, pryzmatyczne brzegi i miekkie wstegi.

### 6.5. Czytelnosc i wydajnosc

- Mocne AoE zawsze ma obrys obszaru przed trafieniem.
- Sojusznicze strefy maja inny brzeg niz wrogie.
- Ustawienie klienta skaluje liczbe particles, ale nie usuwa telegraphu.
- Camera shake jest lokalny, krotki i mozliwy do wylaczenia.
- Efekty nie obracaja kamery gracza i nie migaja calym ekranem.
- Persistent zone wysyla stan przy utworzeniu, zamiast pakietu particles co tick.
- Beam i trail sa renderowane klientowo na podstawie zsynchronizowanych punktow.
- Przy wielu companionach klient stosuje limit odleglosci i laczy odlegle efekty w prostsze LOD.

## 7. Absorpcja i progresja

1. `CobblemonMoveMapper` mapuje nazwe ruchu tylko do spella o tej samej nazwie.
2. Status moves, np. Rest, Toxic, Iron Defense, Tailwind i String Shot, moga byc absorbowane.
3. Niezaimplementowany ruch jest pomijany, zamiast dawac generyczny spell innego typu.
4. Drop nadal losuje kazdy unikalny ruch raz.
5. Komunikat absorpcji i Codex pokazuja Pokemona-zrodlo, typ, kategorie i opis mechaniki.
6. Spelle nie sa dzielone na sztuczne rarity wedlug samego power ruchu. Ich realna rzadkosc wynika z Pokemonow i movesetow, z ktorych mozna je pochlonac.
7. Ruchy sygnaturowe, np. Volt Tackle, zachowuja waska dostepnosc zamiast byc fallbackiem dla calego typu.

## 8. Migracja obecnych definicji

### Zachowac i przebudowac

`aerial_ace`, `blizzard`, `bubble_beam`, `close_combat`, `confusion`, `dark_pulse`, `dazzling_gleam`, `discharge`, `draco_meteor`, `dragon_breath`, `dragon_pulse`, `earthquake`, `ember`, `fire_blast`, `flamethrower`, `focus_blast`, `foul_play`, `future_sight`, `gust`, `hex`, `hydro_pump`, `hyper_beam`, `ice_beam`, `ice_shard`, `iron_tail`, `leaf_blade`, `leech_seed`, `mach_punch`, `moonblast`, `night_shade`, `outrage`, `overheat`, `petal_blizzard`, `poison_sting`, `psychic`, `psybeam`, `razor_leaf`, `rock_slide`, `rock_throw`, `sacred_fire`, `scald`, `shadow_ball`, `sludge_bomb`, `solar_beam`, `stone_edge`, `surf`, `tackle`, `thunder`, `thunderbolt`, `toxic`, `vine_whip`, `volt_tackle`, `water_gun`, `water_pulse` i `will_o_wisp` pozostaja prawidlowymi kanonicznymi ruchami. Czesc nie znajduje sie w bazowej setce, ale mozna ja przywrocic pozniej jako rozszerzenie bez zmiany silnika.

### Zmienic ID lub zastapic

| Stary spell | Migracja |
|---|---|
| `aerial_strike` | `hurricane`; Aerial Strike nie jest kanonicznym ruchem Pokemon. |
| `energy_ball` | Zachowac jako pozniejszy Grass projectile albo wymienic przedmiot na Razor Leaf. |
| `explosion` | Zachowac na pozniej; wymaga uczciwego recoil/samouszkodzenia. |
| `flash_cannon` | Zachowac w bazowej setce bez zmiany ID. |
| `frost_nova` | `icy_wind`; Frost Nova nie jest ruchem Pokemon. |
| `iron_strike` | `bullet_punch`; Iron Strike nie jest ruchem Pokemon. |
| `nature_burst` | `giga_drain`; Nature Burst nie jest ruchem i obecnie nie ma mapowania. |
| `poison_strike` | `venoshock`; Poison Strike nie jest ruchem Pokemon. |
| `powder_snow` | Zachowac w bazowej setce bez zmiany ID. |
| `psychic_blast` | `psychic`; Psychic Blast nie jest kanonicznym ruchem. |
| `seismic_slam` | `seismic_toss`; Seismic Slam nie jest kanonicznym ruchem. |
| `thundershock` | `thunder_shock`; poprawny kanoniczny zapis to Thunder Shock. |

Stary przedmiot nie moze zniknac po aktualizacji. Przy odczycie dawnego `SpellId` migrator zamienia ID zgodnie z tabela. Jezeli stary spell jest kanoniczny, ale chwilowo wypada poza aktywna setke, gracz zachowuje go jako dzialajacy `Legacy Skill`; nie jest juz losowany do czasu jego pelnego reworku.

## 9. Kolejnosc implementacji

### Etap 1 - fundament

- Nowy `SpellDefinition` z type, category, power, cast time, charges, visual i sound.
- Damage niezalezny od vanilla attack cooldown.
- Wspolny filtr sojusznikow i AoE liczone od centrum efektu.
- Statusy, diminishing returns oraz drain leczacy rzucajacego.
- Nowe delivery i migrator ID.
- Usuniecie type fallback z absorpcji.

### Etap 2 - pionowy wycinek

Najpierw implementujemy dziesiec spelli pokrywajacych najtrudniejsze archetypy:

1. `Ember` - prosty modelowany projectile i Burn.
2. `Tri Attack` - sekwencja roznych pociskow.
3. `Vine Whip` - tether i pull.
4. `Whirlpool` - persistent vortex.
5. `Ice Beam` - beam oraz Wet/Frozen.
6. `Quick Attack` - dash i animacja gracza.
7. `Future Sight` - delayed mark i telegraph.
8. `Sucker Punch` - counter.
9. `Iron Defense` - Guard i attachment na graczu.
10. `Draco Meteor` - wiele prawdziwych pociskow i telegraphy uderzen.

### Etap 3 - biblioteka VFX

- Renderery projectile, beam, ribbon, zone, decal i attachment.
- Profile animacji gracza i synchronizacja cast timeline.
- Dzwieki cast/travel/impact.
- Ustawienia intensywnosci particles i camera shake.

### Etap 4 - produkcja rosteru

Implementacja paczkami:

1. Water + Electric + Ice.
2. Grass + Poison + Bug.
3. Fighting + Rock + Ground.
4. Psychic + Ghost + Dark.
5. Normal + Flying + Steel.
6. Fairy + Dragon + Fire.

Po kazdej paczce testujemy PvE, companion cast, multiplayer, friendly fire oraz zachowanie statusow na graczu i mobach.

### Etap 5 - UI i polish

- Tooltip pokazujacy power, cooldown, cast time, range, status i synergie.
- Ikony wszystkich typow oraz statusow.
- Codex z filtrowaniem po typie, category i delivery.
- Combat log do strojenia obrazen i CC.
- Test wydajnosci przy wielu companionach i strefach.

## 10. Kryteria akceptacji

- Roster ma 100 spelli i wszystkie nazwy sa kanonicznymi ruchami Pokemon.
- Kazdy spell zachowuje kanoniczny typ ruchu.
- Skill absorbowany z Pokemona ma taka sama nazwe jak ruch w jego movesecie.
- Nie wiecej niz 30% rosteru to pociski; obecny roster ma 25%.
- Kazdy z 18 typow ma unikalna role i co najmniej piec spelli.
- Kazdy mocny atak obszarowy ma telegraph widoczny przed obrazeniami.
- Pull, Rooted, Frozen i Stagger dzialaja na moby i graczy z diminishing returns.
- Drain leczy rzucajacego na podstawie faktycznie zadanych obrazen.
- Persistent zone naprawde tickuje przez deklarowany czas.
- Kazdy projectile ma renderer/model; docelowy system nie uzywa `NoopRenderer`.
- Animacja gracza jest widoczna dla innych klientow i nie blokuje chodzenia.
- Companion nie trafia wlasciciela ani jego sojusznikow.
- Przy zredukowanych particles telegraphy i hitboxy nadal sa czytelne.