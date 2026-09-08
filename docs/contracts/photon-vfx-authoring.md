# Photon VFX runtime

Tensura uses Photon 2.2.5 as its client VFX runtime. Java sends one compact
playback event when a spell phase starts; Photon owns particle simulation,
batching, rendering, timelines, and cleanup.

All initial spell effects are built programmatically by `ProgrammaticSpellFx`.
Runtime `.fx` resources are optional art overrides, not a requirement. The
client first attempts to load `tensura:<visual value>` and falls back to the
code-generated effect when that resource does not exist.

## Runtime contract

- Runtime IDs resolve as `tensura:<visual value>`.
- Optional overrides belong at `src/main/resources/assets/tensura/fx/<id>.fx`.
- Keep editable `.fxproj` files outside runtime resources.
- World-space directional effects point along local `+X`. Java rotates `+X`
  toward the packet target and scales it to the requested beam length.
- Beam width, area radius, and aura radius arrive as root scale.
- Projectile FX follows the projectile entity. Aura roots follow the caster at
  foot level. Other attachments follow the center of the caster.
- Timed effects are retired when the server-provided phase duration expires.
- Do not make persistent emitters infinite. Their Timeline should end at the
  same duration so particles can drain naturally.

## Initial effect matrix

| Spell | FX ID | Anchor | Duration | Authoring intent |
|---|---|---:|---:|---|
| Aqua Jet | `dash_forward` | attachment | 5 | compressed forward anticipation |
| Aqua Jet | `water_shell` | attachment | 5 | refractive water shell and short ribbon |
| Aqua Jet | `water_burst` | impact | authored | radial splash and droplets |
| Hydro Pump | `two_hand_channel` | attachment | 16 | water gathering at cast point |
| Hydro Pump | `water_spiral` | beam | 24 | unit-length GPU beam along `+X`; corkscrew trail |
| Hydro Pump | `heavy_splash` | impact | authored | dense fan splash and mist |
| Electro Ball | `cast_point` | attachment | authored | compact generic release flash |
| Electro Ball | `electro_ball` | projectile | travel | bright modeled core |
| Electro Ball | `electric_arc` | projectile | travel | short arcing trail |
| Electro Ball | `electric_burst` | impact | authored | branching discharge |
| Thunder | `sky_call` | attachment | 20 | upward charge and sparks |
| Thunder | `electric_ground_ring` | telegraph | 20 | radius-1 ring; root scale supplies radius 3 |
| Thunder | `lightning_column` | impact | authored | vertical bolt column and flash |
| Thunder | `electric_afterglow` | aftermath | authored | fading ground arcs |
| Aurora Veil | `guard_stance` | attachment | authored | cold cast glow |
| Aurora Veil | `aurora_dome` | aura | 20 | opening radius-1 dome; root scale supplies radius 6 |
| Aurora Veil | `aurora_curtain` | aura | 160 | low-cost looping veil with finite Timeline |
| Blizzard | `storm_cast` | attachment | authored | snow gathering around caster |
| Blizzard | `snow_zone` | zone | 20 | radius-1 warning ring; root scale supplies radius 5 |
| Blizzard | `moving_blizzard` | zone | 120 | snow, wind ribbon, and ice shards moving along local `+X` |

## Optional art overrides

1. Start a single-player development client and run `/photon_editor`.
2. Create and retain each editable `.fxproj` under the development game
   directory.
3. Export each runtime FX to
   `src/main/resources/assets/tensura/fx/<id>.fx`.
4. Reload resources and clear stale definitions with
   `/photon_client clear_client_fx_cache`.
5. Preview an export with `/photon fx tensura:<id> block ~ ~-1 ~`.
6. Test the real spell path in multiplayer. Missing exports automatically use
   the programmatic Photon implementation. Existing vanilla particles remain
   as an additional compatibility fallback.

Enable GPU instancing for tile, trail, and beam renderers. Prefer one material
per phase, bounded particle counts, and Timeline activation over spawning a new
effect every tick.