# Backrooms Flashlight 4.2.0 - FABRIC, Minecraft 26.2 (Java 25)
(Port of the 26.3 version. See "26.2 port" at the bottom first.)
Fabric version of the 26.3 flashlight. UNTESTED, never compiled (Gradle/Maven are unreachable from this workspace).
APIs were checked against the Fabric API 26.3 source (networking, key mapping, attachments, entity events, creative tabs) and the NeoForge 26.3 repo.
Versions (from the official fabric-example-mod, branch 26.3): Loom 1.18-SNAPSHOT, loader 0.19.5, Fabric API 0.161.0+26.3, Gradle 9.7.1.
- Flashlight item with off/on JSON models (has_component backrooms_flashlight:lit), infinite battery, F key or right click.
- Light = invisible light blocks where the beam lands (works with every shader pack). Put back on logout/death/dimension change/stop/after a crash.
- Spawn with it: first join, and after dying if you have none.
- Config: config/backrooms_flashlight.json (spawn_with_flashlight, give_on_respawn, light_blocks, light_level, light_distance).
Later: Oleg Rendering can add a volumetric beam on top.

## v3.1.0: visible beam
Client-side cone of light (BeamRenderer, from Oleg Rendering step 2) for every player holding a switched-on flashlight. It follows the hand holding the flashlight, stops at the first block, and is only drawn while lit. Config: volumetric_beam (true/false) in config/backrooms_flashlight.json (read by the client too).
Do NOT install the standalone Oleg Rendering test mod together with this: it draws its own always-on beam.

## v3.2.0: light pool
Where the beam lands on a block, a soft glowing disc (4 rings, opacity falling to the edge, stretched at glancing angles) is drawn 2 cm off the surface. SPOT_ALPHA in BeamRenderer.java controls the strength.
A true depth-based light pass (the surface itself gets brighter, follows terrain, shadows) is the next, risky step: needs a post-processing pass in the 26.3 pipeline.

## v3.3.0
- Beam cone now very faint (8 layers, alpha 0.012); light pool disc off.
- Star-shaped lens glare added (camera-facing, brighter when the light points at you).
- Mid-beam light block removed: one light at the beam end only (tighter spot).

## v4.0.0 - real spotlight
- New `SpotlightPass`: while you hold a lit flashlight in first person, a post effect (`post_effect/spotlight.json`, `shaders/post/spotlight.fsh`) reads the depth buffer and lights everything in the cone (from Oleg Rendering step 3b).
- `light_blocks` now defaults to false (the pass does the lighting). Turn it on for shader packs, third person, or to light the area for other players. NOTE: an existing config file keeps its old value, so edit it or delete `config/backrooms_flashlight.json`.
- New config `spotlight_pass` (true). Tuning without rebuilding: edit `SpotConfig` in `post_effect/spotlight.json` (strength, range, cone angles).
- Limits: first person only, assumes FOV 70, lamp fixed at the right hand, not shader-pack compatible, no shadows.

## v4.1.0
- Light blocks removed completely (placement code and the light_blocks / light_level / light_distance config). The spotlight pass is the only light.
- `LightCleanup` only removes light blocks that an older version left in a save, once at world start.
- Not shader-pack compatible any more (the pass is skipped with shader packs).

## v4.2.0
- Solid beam mesh and the light-pool disc removed. `BeamRenderer` now only draws the star lens glare (config `lens_glare`, replaces `volumetric_beam`).
- Volumetric look now comes from the spotlight shader: it marches 16 samples along each view ray and adds the light that is in the air (stops at walls, shows against the sky). Strength: `Haze` x in the post effect files (0.018).
- Beam movement delay: the cone trails behind the camera and catches up (`DELAY` 0.10 s in SpotlightPass, max 6 degrees). Post effects cannot take per-frame values, so there are 169 generated files `post_effect/spot_x<-6..6>_y<-6..6>.json` (m = minus), one per 1 degree offset, and the mod switches between them each frame. Change the shader once, edit values in all files (a script can rewrite them).

## 26.2 port
Versions (official fabric-example-mod, branch 26.2): Loom 1.18-SNAPSHOT, loader 0.19.5, Fabric API 0.161.0+26.2, Gradle 9.7.1, Java 25.
Differences from the 26.3 build:
- KeyMapping uses the 26.2 constructor: (name, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F, category).
- 26.2 does not use the SPIR-V shader compiler: `spotlight.fsh` is plain `#version 330` (no layout(location) on inputs).
- Depth convention is selectable because it could not be checked for 26.2: config `reverse_depth` (default true = reverse-Z like 26.3).
  true  -> effect files spot_x*_y*.json, false -> spots_x*_y*.json (Haze.y = 1, standard depth, far 1024).
  If the lit area looks wrong (nothing lit, sky lit, light only far away), set reverse_depth to false.
- Everything else is the same code. Never compiled or run; the first build may show small API differences.
- StarterGift: 26.2 has no net.minecraft.util.Prediction; uses Player.drop(stack, false).
- 26.2: GameRenderer methods are setPostEffect(Identifier) / clearPostEffect() (26.3 has setSpectatedEntityPostEffect / clearSpectatedEntityPostEffect). SpotlightPass tries both names.
- 4.2.2: reverse_depth now defaults to false on 26.2 (haze visible but surfaces unlit = depth read the wrong way). An existing config keeps its old value.
- 4.2.3: surface light also adds a pattern-following term in dark scenes (true-darkness style mods leave the scene almost black, so multiplying did nothing).
- 4.2.4: stronger surface light in the dark (base 0.30 + 0.90*pattern, constant SURFACE_LIGHT at the top of spotlight.fsh) and gentler distance falloff (pow 1.2). 4.2.3 was too faint: k is about 0.25 on the ground and the old term was 0.05+0.22*pattern.
- 4.2.5: sharper spot. Cone edge 0.993/0.975 (about 6.8 to 12.8 degrees half angle, was 0.985/0.93 = 10 to 21), haze 0.012 (was 0.018). Rewritten in all post_effect files (Cone and Haze in SpotConfig).
- 4.3.0: surface light is now an auto exposure inside the cone (EXPOSURE_TARGET 0.38, MAX_GAIN 60 at the top of spotlight.fsh): it scales each pixel up to a comfortable brightness and keeps its colour/texture, instead of adding flat light. Pixels that are exactly black (nothing drawn at all) only get a faint flat glow.

## 4.4.0 - texture floor (option 3, lightweight form)
Overrides `minecraft:include/sample_lightmap.glsl` (used by terrain, block, entity, item, particle, text shaders)
so lightmap output never goes below 4%. Surfaces keep a faint texture even under true-darkness, and the
spotlight's auto exposure lifts it. If another mod/pack also overrides that file, only one wins.
Tune BF_FLOOR (0.012 default; 0.02 brighter).

## 5.0.0 - real surface light (albedo buffer)
The 4% lightmap floor is gone. Instead, while the spotlight is on, the opaque terrain pass gets a second colour
attachment that holds the unlit texture colour (mixins: ChunkSectionsToRender, CommandEncoder, RenderPass; shader
`core/terrain_albedo`). The spotlight shader lights that buffer, so surfaces show their real texture even where
`true-darkness` makes the picture black. Only terrain is recorded for now; entities, items and the hand fall back to
the old auto-exposure. Idea taken from how Veil's dynamic buffers work (Veil is LGPLv3; no code copied).

## 5.0.2 - no more depth buffer
The depth buffer on 26.2 was being misread (every pixel counted as sky, so surfaces never got light). The terrain
shader now also writes each pixel's view-space position into a third buffer (RGBA16F), and the spotlight uses that
instead of depth. The `reverse_depth` option no longer does anything on 26.2. Non-terrain pixels (mobs, hand, items,
sky) only get haze for now.

## 5.1.0 - light on everything (entities, items, block models)
Every pass that draws into the main picture during the world pass now gets the two extra buffers. Shaders for terrain,
entities, items and block models have copies (`core/*_albedo`) that write unlit colour + position; every other shader
is copied with the extra outputs switched off. Fixes mobs/items looking see-through: before, they were drawn over
leftover terrain data. Not recorded: translucent terrain (water, glass), particles, weather, entities drawn in the
separate item-entity/translucent targets, and possibly the first-person hand.

## 5.2.0 - two styles
Config `style` in config/backrooms_flashlight.json (restart the game after changing it):
- `"item"` (default): the craftable flashlight item, F to toggle, lens star and haze. Same as 5.1.0.
- `"found_footage"`: like Backrooms: Found Footage. No item needed, press G to switch the light on/off (changeable
  under Controls). The light shines from your eyes: a wide soft cone plus a narrow bright core, range 25, beam
  delay, no star and no haze. Works in first person only, for you only (other players do not see it yet).
Effect files: `spots_*` (item style) and `ff_*` (found footage style). The old reverse-Z `spot_*` files were removed
on 26.2 (the spotlight no longer uses the depth buffer).

## 5.3.0 - in-game settings
Mod Menu now shows a settings button for this mod (needs Mod Menu installed; the mod works without it). The screen
changes style (item / found footage), lens glare, spotlight effect, spawn with flashlight and give on respawn.
Changes apply immediately and are saved when you press Done / Esc. `com/terraformersmc/**` in the source are compile
stubs and are excluded from the jar.

## 5.4.0 - range and strength, live settings
New options (config file and the in-game screen): `range` (blocks, default 22) and `strength` (brightness multiplier,
default 1.0). To make them adjustable, the spotlight's settings are now written into a uniform buffer by the mod every
frame and a RenderPass mixin hands that buffer to the post effect. Side effects: the 338 per-offset effect files are
gone (one `post_effect/spotlight.json` now), and the beam delay is smooth instead of 1-degree steps.

## 5.5.0 - volumetric light in both styles
The light haze in the air now also shines in the found_footage style. New option `volumetric` (0 = off, 1 = default,
up to 4); in-game: "Volumetric light". Applies to both styles.

## 5.5.1
Found footage beam made smaller (wide cone ~40 -> ~28 degrees, core ~14 -> ~10 degrees).

## 5.6.0
Bloom folded in: config 'bloom' (off/low/medium/high), effects spotlight_<level> and bloom_<level> (bloom alone when no light is on). Same shaders as Soft Bloom. Requires 'Spotlight effect' on.

## 5.7.0
Floor reflections: reflect.fsh (screen-space ray march on up-facing surfaces, normal from position derivatives, world-up from camera pitch via ReflectConfig live uniform). Config reflections off/low/medium/high. Chains: bloom_off, bloom_<lvl>, spotlight, spotlight_<lvl>. Position recording runs whenever reflections are on. Ray-march idea after Found Footage (GPL-3).

## 5.8.0
Water (and glass) reflections. Translucent terrain is already recorded by the terrain shader; it now writes albedo alpha 0.75 (opaque 1.0, only in non-cutout passes) so reflect.fsh knows it is see-through. Water gets stronger fresnel, 1.3x boost and animated ripples (4 summed waves in world coordinates, from camera pos/yaw in the new ReflectConfig.Cam vec4). Reflect pass now also reads the Albedo sampler. Untested: if water does not reflect, the translucent pass may not be drawing into the main target (check log "pipeline ... records albedo+position").

## 5.8.0 - water reflections
Translucent terrain (water, ice, glass) is recorded with position alpha 2 (shader define TRANSLUCENT_SURFACE on the clone of the translucent pipeline) and reflect.fsh treats those as a mirror with moving ripples (world-space wave field via ReflectConfig.Cam = camera x, z, yaw, time). Config water_reflections. UNVERIFIED: whether the translucent terrain pass draws into the main target on 26.2; CommandEncoderMixin now logs every pass it leaves alone ("left alone") so latest.log shows it.

## 5.8.1
Water tuning: stronger mirror (0.45-1.0 blend), sky reflection on misses (sky pixel looked up in the mirrored direction), fixed steps + bisection instead of random jitter, ripples calmer with distance.

## 5.9.0
Bloom fades out when the whole picture is bright (4x4 average luminance gate in bright.fsh) so daylight does not glow. Floors mirror only while raining by default (floor_mode wet/always) and only grey surfaces (saturation gate via Albedo sampler); water mirror softer, sky reflection darker/bluer.
