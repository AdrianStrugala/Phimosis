# Walking Animation Fix Tracker — Pokemon-Citizen

Cel: naprawić animację chodzenia dla villagera-pokemona (MineColonies citizen z modelem Cobblemon).

Plik do edycji: `src/main/java/com/tensura/client/PokemonCitizenRenderHandler.java`

---

## Zidentyfikowane root causes

### Bug A — POSE_TYPE utknęło na STAND
- `PokemonEntity.POSE_TYPE` (SynchedEntityData) domyślnie = `PoseType.STAND`
- `updatePoseType()` jest server-only w `PokemonServerDelegate` — nigdy nie działa dla client-only fake entity
- Ustawienie `MOVING=true` NIE zmienia POSE_TYPE automatycznie po stronie klienta
- Efekt: zawsze renderuje idle/stand, nigdy walk

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
| 32 | 2026-04-03 | FIX A + FIX B + sync `deltaMovement` z citizena | 🔄 do testu | Cobblemon delegate.tick() czyta deltaMovement do decyzji o POSE_TYPE |

---

## Kluczowe odkrycie

Cobblemon używa **własnego systemu Bedrock/blockbench animations** (`PosableState.currentPose`),
nie vanilla `walkAnimation.speed`. `PokemonClientDelegate.tick()` decyduje o pozie na podstawie
`entity.getDeltaMovement()`. Fake entity bez zsynchronizowanego `deltaMovement` zawsze widzi
prędkość = 0 → zawsze STAND.

## Aktualne podejście do testowania

### Podejście 3b (aktywne) — deltaMovement + POSE_TYPE

Zmiana w `onRenderLivingPre`, po linii `fake.getEntityData().set(PokemonEntity.getMOVING(), moving);`:

```java
// FIX A: Sync POSE_TYPE so Cobblemon selects walk animations
PoseType targetPose = moving ? PoseType.WALK : PoseType.STAND;
if (fake.entityData.get(PokemonEntity.getPOSE_TYPE()) != targetPose) {
    fake.entityData.set(PokemonEntity.getPOSE_TYPE(), targetPose);
}
```

Import do dodania: `import com.cobblemon.mod.common.entity.PoseType;`

### Podejście B — Napraw walkAnimation (per-tick update)

Dodać pole do klasy: `private static final Map<Integer, Long> lastAnimTick = new HashMap<>();`

W `onRenderLivingPre`, po obliczeniu `dx`/`dz`/`moving`:

```java
// FIX B: Update walkAnimation once per tick (not per frame) with citizen's actual movement
long currentTick = citizen.level().getGameTime();
if (lastAnimTick.getOrDefault(citizenId, -1L) != currentTick) {
    float walkSpeed = moving ? Math.min((float) Math.sqrt(dx * dx + dz * dz) * 4.0f, 1.0f) : 0.0f;
    fake.walkAnimation.update(walkSpeed, 0.4f);
    lastAnimTick.put(citizenId, currentTick);
}
```

### Podejście C — calculateEntityAnimation() (najczystsze)

Zamiast ręcznego liczenia, użyj publicznej metody MC po synchronizacji pozycji:

```java
long currentTick = citizen.level().getGameTime();
if (lastAnimTick.getOrDefault(citizenId, -1L) != currentTick) {
    fake.calculateEntityAnimation(false);
    lastAnimTick.put(citizenId, currentTick);
}
```

### Podejście D — Kopiuj walkAnimation przez reflection

```java
try {
    java.lang.reflect.Field fSpeed    = WalkAnimationState.class.getDeclaredField("speed");
    java.lang.reflect.Field fSpeedOld = WalkAnimationState.class.getDeclaredField("speedOld");
    java.lang.reflect.Field fPosition = WalkAnimationState.class.getDeclaredField("position");
    fSpeed.setAccessible(true); fSpeedOld.setAccessible(true); fPosition.setAccessible(true);
    fSpeed.setFloat(fake.walkAnimation,    fSpeed.getFloat(citizen.walkAnimation));
    fSpeedOld.setFloat(fake.walkAnimation, fSpeedOld.getFloat(citizen.walkAnimation));
    fPosition.setFloat(fake.walkAnimation, fPosition.getFloat(citizen.walkAnimation));
} catch (Exception e) {
    fake.walkAnimation.setSpeed(citizen.walkAnimation.speed());
}
```

---

## Kolejność testowania (rekomendowana)

1. **Podejście A** (POSE_TYPE fix) — samodzielnie → sprawdź
2. **Podejście A + B** — razem → sprawdź
3. **Podejście A + C** (calculateEntityAnimation) → sprawdź
4. **Podejście A + D** (reflection) → sprawdź
5. Jeśli nadal nie działa → patrz plan `C:\Users\adist\.claude\plans\compiled-questing-beaver.md` (Podejście 6–8)

---

## Wyniki testów

*(uzupełniać po każdym teście)*

### Test 1 — data: —
- Zastosowane podejście: 
- Wynik: 
- Obserwacje: 
