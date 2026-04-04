# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository Overview

This repository contains two distinct Minecraft projects:

1. **Phimosis** (root) — Legacy Bukkit plugin (Eclipse project, single-file architecture)
2. **tensura-mod-neo/** — Active NeoForge mod for Minecraft 1.21.1

Most active development happens in `tensura-mod-neo/`.

## Build Commands (Tensura Mod)

All commands run from `tensura-mod-neo/`:

```bash
./gradlew build          # Compile and produce mod JAR
./gradlew runClient      # Launch Minecraft client with mod loaded
./gradlew runServer      # Launch dedicated server with mod loaded
./gradlew genIntellijRuns  # Generate IntelliJ run configurations
```

No test suite exists — NeoForge mods are typically tested by running the client/server.

## Deploying the Built JAR

After `./gradlew build`, the JAR is in `tensura-mod-neo/build/libs/`. Copy it to:

- **Client**: `%APPDATA%\.minecraft\mods\` ← always deploy here alongside server
- **Test server**: `D:\Serv Test\mods\` ← always deploy here during development
- **Production server**: `D:\Serv Phimosis 2k37\mods\` ← NEVER deploy here without explicit confirmation

**Deploy rule**: Every build must go to BOTH test server AND client. Never to production without explicit user instruction.

## Tensura Mod Architecture

**Entry point**: `TensuraMod.java` — registers all components and event buses.

**Registry pattern**: All game objects are registered using NeoForge's `DeferredRegister` system, split across:
- `TensuraItemRegistry`, `TensuraBlockRegistry`, `TensuraEntityRegistry`
- `TensuraMenuRegistry`, `TensuraAttributes`, `TensuraMobEffects`

**Spell/Predator system** (core mechanic): Players absorb abilities from Pokemon on kill via `PredatorEvents.java`. Spells are defined as data in `SpellDefinition`, loaded by `SpellLoader`, executed by `SpellExecutor`, and centrally managed by `SpellRegistry`.

**Event-driven logic**: All game behavior is in `events/` package — handlers registered to NeoForge's event bus. Key files: `PredatorEvents`, `CombatCompanionEvents`, `ColonyGamemodeEvents`.

**Client/server split**: Client-only code lives in `client/` (rendering, screens, cooldown tracking). Networking via `NetworkHandler` + `OpenSkillSelectPacket` bridges the two sides.

**AI goals**: Custom entity behavior in `goals/` (`AllyAttackGoal`, `AllyFollowGoal`, `CompanionSpellGoal`).

**External mod integrations**:
- **Cobblemon** (`/libs/cobblemon.jar`) — Pokemon are the source of absorbed skills
- **MineColonies** (`/libs/minecolonies.jar`) — Colony/citizen mechanics and species system
- **Kotlin for NeoForge** (`/libs/kotlinforforge.jar`) — Kotlin runtime support

**Data persistence**: `PredatorData.java` handles per-player skill/predator state. `DynamicCitizenSpeciesData.java` tracks citizen species for MineColonies.

## Phimosis Plugin (Legacy)

Single-file plugin (`src/Phimosis/Main.java`) built via Eclipse. Contains 50+ economy/roleplay commands and a death event listener that drops player skulls. Uses deprecated Bukkit APIs (`Material.SKULL_ITEM`) — this project is not under active development.
