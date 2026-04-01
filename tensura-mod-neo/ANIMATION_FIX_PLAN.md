# Plan: Fix Walking Animation for Pokemon-Citizen

## Context

Villager-Pokemon (MineColonies citizen z modelem Cobblemon Pokemon) nie gra animacji chodzenia.
Był ~30 nieudanych prób naprawy. Potrzebujemy systematycznie przetestować wiele podejść.

Plik do trackowania wyników: `ANIMATION_FIX_TRACKER.md`  
Plik do edycji: `src/main/java/com/tensura/client/PokemonCitizenRenderHandler.java`

---

## Analiza root cause (na podstawie źródeł Cobblemon + MC 1.21)

### Bug A — POSE_TYPE utknęło na STAND (priorytet #1)

`PokemonEntity.POSE_TYPE` to SynchedEntityData, domyślnie `PoseType.STAND`.  
Cobblemon zmienia go w `PokemonServerDelegate.updatePoseType()` — metoda **server-only**, nigdy nie wywoływana dla fake entity istniejącego tylko po stronie klienta.  
Obecny kod ustawia `MOVING=true`, ale Cobblemon **NIE** reaguje na MOVING automatycznie po stronie klienta — POSE_TYPE nie zmienia się.  
Efekt: fake entity zawsze renderuje idle/stand pose, ignorując animacje chodzenia.

### Bug B — walkAnimation.speed = 0 (priorytet #2)

`limbSwingAmount` w rendererze pochodzi z `entity.walkAnimation.speed(partialTick)`.  
`walkAnimation` jest aktualizowane w `LivingEntity.calculateEntityAnimation()` → `walkAnimation.update(dist * 4.0f, 0.4f)`.  
Ta metoda wywoływana jest **podczas ticka fake entity**, zanim render event zsynchronizuje pozycje.  
W momencie ticka fake entity stoi w miejscu (delta = 0) → `walkAnimation.speed → 0` → `limbSwingAmount = 0`.  
Efekt: nawet jeśli wybrana jest właściwa poza, nogi nie ruszają się (amplituda = 0).

### API dostępne w MC 1.21

```java
// WalkAnimationState (net.minecraft.world.entity.WalkAnimationState):
public void setSpeed(float speed)                  // PUBLICZNE — ustawia speed bezpośrednio
public void update(float targetSpeed, float scale)  // PUBLICZNE — lerp + przesuwa position
public float speed(float partialTick)              // odczyt z interpolacją
public float position(float partialTick)           // odczyt (private field "position")

// LivingEntity:
public final WalkAnimationState walkAnimation      // PUBLICZNE pole
public void calculateEntityAnimation(boolean includeY)  // PUBLICZNE

// PokemonEntity:
@JvmStatic val POSE_TYPE = SynchedEntityData.defineId(...)  // POSE_TYPE klucz
```

---

## Podejścia do testowania (kolejno, zatrzymać gdy działa)

### Podejście 1 — Napraw POSE_TYPE (tylko poza)

W `onRenderLivingPre`, po obliczeniu `moving`:
```java
PoseType targetPose = moving ? PoseType.WALK : PoseType.STAND;
if (fake.entityData.get(PokemonEntity.getPOSE_TYPE()) != targetPose) {
    fake.entityData.set(PokemonEntity.getPOSE_TYPE(), targetPose);
}
```
Import: `com.cobblemon.mod.common.entity.PoseType`

### Podejście 2 — Napraw walkAnimation (tylko amplituda)

Dodać `Map<Integer, Long> lastAnimTick = new HashMap<>()` do klasy.  
W `onRenderLivingPre`, po obliczeniu `dx/dz/moving`:
```java
long currentTick = citizen.level().getGameTime();
if (lastAnimTick.getOrDefault(citizenId, -1L) != currentTick) {
    float walkSpeed = moving ? Math.min((float)Math.sqrt(dx * dx + dz * dz) * 4.0f, 1.0f) : 0.0f;
    fake.walkAnimation.update(walkSpeed, 0.4f);
    lastAnimTick.put(citizenId, currentTick);
}
```

### Podejście 3 — Oba naraz (POSE_TYPE + walkAnimation)

Połącz Podejście 1 i 2.

### Podejście 4 — calculateEntityAnimation() zamiast ręcznego update

Zamiast ręcznego liczenia walkSpeed, użyć publicznej metody MC:
```java
long currentTick = citizen.level().getGameTime();
if (lastAnimTick.getOrDefault(citizenId, -1L) != currentTick) {
    // pozycje już zsync, calculateEntityAnimation użyje delta = citizen.pos - citizen.xo
    fake.calculateEntityAnimation(false);
    lastAnimTick.put(citizenId, currentTick);
}
```
Plus Podejście 1 (POSE_TYPE fix).

### Podejście 5 — Kopiuj walkAnimation obywatela przez reflection

Citizen ma poprawnie obliczone walkAnimation (bo naprawdę chodzi). Kopiujemy je 1:1:
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
Plus Podejście 1 (POSE_TYPE fix).

### Podejście 6 — Nie dodawaj fake entity do levelu

Problem: `level.addFreshEntity()` powoduje tickowanie fake entity (które resetuje animację).  
Próba: stwórz entity BEZ dodawania do levelu, ręcznie wywołaj inicjalizację delegata:
```java
PokemonEntity entity = new PokemonEntity(level, pokemon, CobblemonEntities.POKEMON);
entity.noPhysics = true;
// Zamiast level.addFreshEntity():
entity.setId(/* unikalny ujemny ID */);
// Sprawdzić czy Cobblemon wymaga addFreshEntity do init modelu
```
Jeśli model się nie inicjalizuje, spróbować wywołać ręcznie `entity.onAddedToLevel()` lub `entity.delegate.initialize(entity)`.

### Podejście 7 — Zablokuj naturalny tick walkAnimation po ticku fake entity

Fake entity tickuje sam i resetuje walkAnimation. Nadpisać po ticku przez event:
```java
@SubscribeEvent
public static void onClientTick(TickEvent.ClientTickEvent event) {
    if (event.phase != TickEvent.Phase.END) return;
    for (Map.Entry<Integer, PokemonEntity> entry : fakeEntities.entrySet()) {
        // re-apply correct walkAnimation after entity's own tick reset it
    }
}
```

### Podejście 8 — Renderowanie bezpośrednie przez model (bypass EntityRenderDispatcher)

Zamiast `mc.getEntityRenderDispatcher().render(fake, ...)`, pobierz renderer bezpośrednio i wywołaj render z ręcznie ustawioną walkAnimation TUŻ PRZED render wywołaniem (kombinacja z którymś z powyższych).

---

## Weryfikacja po każdym podejściu

1. `./gradlew build` z `tensura-mod-neo/`
2. Skopiuj JAR do serwera (`D:\Serv Phimosis 2k37\mods\`) lub klienta
3. Wejdź do gry, znajdź villager-Pokemon, każ mu chodzić (pathfinding do jakiegoś celu)
4. Obserwuj czy nogi się ruszają podczas ruchu
5. Zanotuj wynik w `ANIMATION_FIX_TRACKER.md`
