# Walking Animation Fix Tracker — Pokemon-Citizen

Cel: naprawić animację chodzenia dla villagera-pokemona (MineColonies citizen z modelem Cobblemon).

Plik do edycji: `src/main/java/com/tensura/client/PokemonCitizenRenderHandler.java`

---

## Zidentyfikowane root causes

### Bug A — `PosableState.currentPose` utknęło na pozie stojącej
- modele Cobblemon używają `PosableState`, nie vanilla `walkAnimation`
- ustawienie `PokemonEntity.POSE_TYPE` aktualizuje fizykę encji, ale nie przełącza `currentPose`
- `PokemonClientDelegate.tick()` w używanej wersji tylko zwiększa wiek animacji i obsługuje riding; nie wybiera WALK na podstawie `deltaMovement`
- właściwym API przełączającym animację jest `PosableState.setPoseToFirstSuitable(PoseType)`

### Bug B — walkAnimation.speed = 0
- `limbSwingAmount` pochodzi z `entity.walkAnimation.speed(partialTick)` w MobRenderer
- `calculateEntityAnimation()` wywoływana podczas ticka fake entity, ZANIM render event zsynchronizuje pozycje
- W momencie ticka delta pozycji = 0 → speed dąży do 0 → amplituda = 0
- Efekt: nawet przy poprawnej pozie nogi nie ruszają się

---

## Historia podejść

| # | Data | Opis zmiany | Wynik | Obserwacje |
|---|------|------------|-------|------------|
| 1–30 | przed 2026-04-03 | ~30 wcześniejszych prób (nieudokumentowane) | ❌ | — |
| 31 | 2026-04-03 | FIX A: set POSE_TYPE=WALK/STAND + FIX B: walkAnimation.update() | ❌ | walkAnimation nieistotne — Cobblemon używa Bedrock animations, nie vanilla limbSwing |
| 32 | 2026-04-03 | FIX A + FIX B + sync `deltaMovement` z citizena | ❌ | Bytecode używanej wersji potwierdza, że client delegate nie wybiera pozy z deltaMovement |
| 33 | 2026-09-09 | Bezpośrednie `PosableState.setPoseToFirstSuitable`, rodziny WALK/FLY/SWIM i cleanup fake entities | 🔄 do playtestu | `./gradlew build --max-workers=1` oraz oba walidatory przeszły |

---

## Kluczowe odkrycie

Cobblemon używa **własnego systemu Bedrock/blockbench animations** (`PosableState.currentPose`),
nie vanilla `walkAnimation.speed`. Zmiana `POSE_TYPE` nie przełącza `currentPose`; trzeba wywołać
`setPoseToFirstSuitable(WALK/STAND)` na delegacie fake entity.

## Aktualne podejście do testowania

### Podejście 33 (aktywne) — bezpośrednie przełączenie `PosableState`

Zmiana w `onRenderLivingPre`, po ustawieniu `POSE_TYPE`:

```java
PoseType targetPose = resolvePoseType(fake, moving);
if (fake.getDelegate() instanceof PosableState posableState) {
    posableState.setPoseToFirstSuitable(targetPose);
}
```

### Playtest

1. Sprawdź gatunek z wyraźną animacją chodu na płaskim terenie.
2. Sprawdź gatunek latający lub unoszący się, np. Magnetona.
3. Potwierdź przejście `stand → walk → stand` bez resetowania animacji co klatkę.
4. Sprawdź kilku citizenów jednocześnie oraz ponowne wejście do świata.
5. Jeśli model nadal stoi, zanotuj gatunek i aktualne `currentPose`; nie wracaj do `walkAnimation`.

---

## Wyniki testów

*(uzupełniać po każdym teście)*

### Test 1 — data: —
- Zastosowane podejście: 
- Wynik: 
- Obserwacje: 
