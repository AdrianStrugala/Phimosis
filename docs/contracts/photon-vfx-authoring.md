# Photon VFX runtime

Tensura uses Photon 2.2.5 as its client VFX runtime. Java sends one compact
playback event when a spell phase starts; Photon owns particle simulation,
batching, rendering, timelines, and cleanup.

Initial spell effects use `ProgrammaticSpellFx` unless an integration is listed
explicitly below. Runtime `.fx` resources are optional art overrides, not a
requirement. The client first attempts to load `tensura:<visual value>` and
falls back to the code-generated effect when that resource does not exist.

## Runtime contract

- Runtime IDs resolve as `tensura:<visual value>`.
- Cobblemon Snowstorm overlays are allowed for spell phases with verified effect
   IDs. Photon remains responsible for directional geometry, telegraphs and a
   visible fallback when an integration asset is unavailable. Entity-bound
   Snowstorm packets are sent only for `PosableEntity`; player casts use position
   packets.
- Flamethrower Pokemon streams use
   `cobblemon:flamethrower_actor`, select the first available source locator from
   `special` and `target`, and aim at locator `middle` or the target position.
   Player casts use a thin Photon core inside two short-lived volumetric flame
   layers because players do not expose Cobblemon locators. Their visual origin
   is offset forward, down, and toward the main hand so the first-person camera
   does not clip it. Cast start and per-caster throttled target hits use
   `cobblemon:flamethrower_targetburst`; throttle entries expire after 20 ticks.
   - Ultimate overlays are emitted only on phase entry or through a bounded,
     per-caster-target refresh. Area detonations send one central effect rather
     than one full effect stack for every target in the area.
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

## Reference profile: Flamethrower

Status: visually accepted after gameplay validation on 2026-09-10.

Flamethrower is the reference for how a finished Tensura spell should read in
motion. It is not a template that every spell copies literally; it establishes
the required relationship between silhouette, motion, anchoring, impact and
gameplay readability.

### Why it works

- The primary silhouette is made by particles with volume, not by a widened
   laser. Two short-lived flame layers overlap across the full path: a broad red
   envelope and a denser orange middle.
- A thin near-white Photon beam remains inside the volume only as a directional
   core. It helps the eye read aim and range, but it is not the dominant shape.
- Colors describe heat and depth: pale core, orange body, red outer flame and
   transparent fade. Alpha falls to zero instead of ending at a hard edge.
- The player origin is offset `0.7` blocks forward, `0.3` blocks down and `0.22`
   blocks toward the main hand. This keeps the stream outside the first-person
   near plane and makes it emerge from the casting arm in third person.
- Pokemon use their native `special`/`target` locator fallback and aim at the
   target's `middle` locator. Players use world-space Photon geometry because
   they do not expose Cobblemon `PosableEntity` locators.
- Release, travel and hit are distinct. The continuous volume communicates the
   active hit path; Snowstorm `flamethrower_targetburst` punctuates start and
   throttled contact without replacing the stream.
- Visual width agrees with the gameplay width. The effect communicates the
   dangerous space instead of showing a hairline inside a much larger hitbox.
- Server traffic stays bounded. Long channels refresh at most once per 20 ticks,
   and hit throttling is keyed per caster-target pair with expired entries pruned.

### Current authored recipe

| Layer | Role | Key parameters |
|---|---|---|
| Outer flame volume | broad, unstable silhouette | red `0x99EF4444`, emission `34`, size `0.34`, diameter `0.95` |
| Middle flame volume | dense moving body | orange `0xDDFFB020`, emission `48`, size `0.22`, diameter `0.58` |
| Directional core | aim and continuity only | white-to-transparent `0xFFFFF3C4 -> 0x00FFC857`, width `0.11` |
| Particle lifetime | prevents a solid tube | `7` ticks with upward drift |
| Start and hit accent | phase punctuation | `cobblemon:flamethrower_targetburst` |

The numeric values are the accepted Flamethrower profile, not global constants.
Other spells should preserve the principles while choosing geometry, motion and
timing appropriate to their element and mechanic.

### Rules derived from the reference

1. Start with the spell's readable silhouette at gameplay distance. Add a bright
    core only when it clarifies direction; never let the core become the whole
    effect unless the fantasy is explicitly a laser.
2. Build depth with at least two visually distinct roles, such as core/body,
    body/debris or field/boundary. Merely stacking wider copies of one beam does
    not create volume.
3. Anchor the effect to the action. First-person clipping and third-person source
    position must be solved separately from the server damage ray.
4. Match visual extent to the authoritative hitbox and telegraph. A spectacular
    effect that communicates the wrong danger area fails review.
5. Give anticipation, active delivery, impact and aftermath different visual
    jobs. Do not replay one large burst for every phase or every target in an AoE.
6. Prefer continuous client-side simulation plus sparse phase packets over
    server-spawned particle lines every tick.
7. Combine Photon and Snowstorm when each solves a different problem. Reuse is
    valuable only when the resulting spell remains directional, visible and
    coherent for both players and Pokemon.

### Acceptance checklist for future spells

- Visible and correctly anchored in first person and front/back third person.
- Recognizable as its intended material or force without relying on its color.
- Telegraph, rendered extent and server hitbox agree.
- Start, active, hit and aftermath phases are distinguishable at a glance.
- Still readable against bright sky, dark terrain and dense combat particles.
- No full-screen flash, camera occlusion or bright opaque shape at the near plane.
- Multiple simultaneous casts remain understandable and within the packet and
   particle budgets.
- Minimal particle settings preserve the telegraph and dangerous boundary.

## Polished signature profiles - 2026-09-10

Status: implemented and compile-validated; awaiting screenshot and gameplay
sign-off in first person, front/back third person and Pokemon companion casts.

These profiles follow the Flamethrower reference by owning a recognizable
silhouette instead of relying on a generic recolored sphere or ring.

| Spell | Primary silhouette | Distinct phase treatment |
|---|---|---|
| Hyper Beam | turbulent blue-white energy body around a thin stable core | converging charge, double focus ring, three line aftershocks and layered terminal blast |
| Thunder | vertical spark column and central white flash | double-ring warning, cloud/bolt Snowstorm overlay and fading ground arcs |
| Earthquake | low dust field and wide ground waves | full-radius boundary, rock/fissure burst on each pulse and persistent settling dust |
| Draco Meteor | bright meteor core inside a Dragon shell with a broad tail | individual landing shadow, constellation fall and layered rock/energy crater |
| Surf | seven-block-wide body, bright crest and foam base | broad release front, moving water mass and radial splash on contact |
| Blizzard | dense low mist plus faster snow inside a stable boundary | cast shroud, visible danger ring, moving storm volume and crystalline shatter |
| Trick Room | box edges and faint translucent faces, not a sphere | corner-forming cast, ground grid, persistent room shell and local inversion pulse |
| Stealth Rock | six individually visible shards around the caster | rune activation, persistent halo and separate shard-consumption burst |
| Crunch | two opposing jaw volumes around a marked area | maw warning, closing-jaw impact and dark residue after the bite |
| Solar Beam | gold-green energy body around a white solar core | overhead sunlight focus, ground alignment ring and layered solar flare impact |

Surf's `targeting.width` is the full visual/gameplay width. Runtime converts it
to a half-width only for collision queries; the accepted value is `7.0` blocks.

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
   the programmatic Photon implementation. Do not add vanilla particle fallback
   to the Snowstorm-owned Flamethrower phases.

Enable GPU instancing for tile, trail, and beam renderers. Prefer one material
per phase, bounded particle counts, and Timeline activation over spawning a new
effect every tick.