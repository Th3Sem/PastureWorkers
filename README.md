# PastureWorkers

Tethered Cobblemon Pokémon work your pasture **without moving**. No pathfinding, no AI goals — pure server-side scanning on a tick budget. **Server-side only: clients do not need the mod.**

[![License: MPL-2.0](https://img.shields.io/badge/License-MPL--2.0-brightgreen.svg)](https://opensource.org/licenses/MPL-2.0)
[![Minecraft 1.21.1](https://img.shields.io/badge/Minecraft-1.21.1-62b47a.svg)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/Fabric-0.19.5+-0099cc.svg)](https://fabricmc.net/)
[![Cobblemon 1.8.1](https://img.shields.io/badge/Cobblemon-1.8.1-ff6b6b.svg)](https://cobblemon.com/)
[![GitHub](https://img.shields.io/badge/GitHub-Th3Sem%2FPastureWorkers-181717.svg)](https://github.com/Th3Sem/PastureWorkers)

---

## Quick start

1. Install [Fabric Loader](https://fabricmc.net/use/) ≥ 0.19.5, [Fabric API](https://modrinth.com/mod/fabric-api), [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin) and [Cobblemon](https://modrinth.com/mod/cobblemon) 1.8.1 on the **server**.
2. Drop `pastureworkers-1.1.0.jar` into `mods/`.
3. Start the server once, then **build a fenced pasture** around a Cobblemon Pasture block and tether your workers to it.
4. **Shift + right-click** the pasture with an empty hand to open the GUI and see what each worker can do.

The config appears at `config/pastureworkers.json`. Every key is documented in [`config/pastureworkers.readme.md`](#configuration), which the mod regenerates on every load.

---

## How it works

PastureWorkers replaces the classic "worker walks to block, interacts, walks back" loop with a **virtual background engine**:

- **Zero pathfinding, zero AI goals.** Work is purely logical; no navigation mesh, no movement code.
- **Budgeted scanning.** Each interval the server scans a configurable cuboid around the pasture, capped at `blocksPerScanTick` (default 512) block checks per tick, so large radii scale gracefully.
- **Predictable tick cost.** No entity movement, no chunk loading from workers wandering off.

### Reachability and enclosed farms

With `requireReachableSpace: true` (default) the scanner runs a **cached flood-fill** from the pasture, expanding only through blocks without a collision box (air, plants, crops and water pass; walls, fences and solid blocks block it). The result is the contiguous walkable region your workers can actually use:

- A **closed fence wall contains** the workers. Crops and chests outside the wall are never touched, even within radius.
- Internal pillars are routed around.
- An **open farm is not contained** — the region escapes into open air and effectively fills the whole search box, so workers reach everything in range and visually wander before being recalled to the pasture.
- The region is recomputed on the rescan cadence (`rescanCooldownTicks`, default 1200).

> **Recommendation:** fence the pasture. It bounds the work area, keeps workers visually nearby, and stops them from harvesting the neighbours' crops. The GUI reports whether the detected area is enclosed or open.

**Region-based room model.** Detection is accumulated globally across the search box; the flood-fill splits walkable space into connected components seeded from the pasture's passable face-neighbours (the solid pasture never bridges components). A single priority rule picks the primary region — the component in the pasture's facing direction, then most job-relevant resource cells, then fewest boundary cells, then largest. A detection is kept iff it lies in or is adjacent to that region, so a chest embedded in the perimeter wall is still recognised.

Set `requireReachableSpace: false` to disable the flood-fill and scan the full box (legacy behaviour).

---

## Jobs

Each job requires a Pokémon type and scales on **one** Cobblemon stat.

| Job | Required type | Scaling stat | Config group |
|-----|---------------|--------------|--------------|
| Crop Harvest | GRASS | Attack | `cropHarvest` |
| Crop Irrigation | WATER | Special Attack | `cropIrrigation` |
| Amethyst Harvest | ROCK | Attack | `amethystHarvest` |
| Gem Harvest | ROCK | Defence | `gemHarvest` |
| Apricorn Harvest | BUG | Speed | `apricornHarvest` |
| Berry Harvest | GRASS | Special Defence | `berryHarvest` |
| Mint Harvest | FAIRY | Special Attack | `mintHarvest` |
| Nether Wart Harvest | GHOST | Special Defence | `netherwartHarvest` |
| Tumblestone Harvest | STEEL | Defence | `tumblestoneHarvest` |
| Honey Collect | BUG | Speed | `honey` |
| Ground Item Gather | PSYCHIC | Speed | `groundItemGather` |

Jobs of the same type do **not** share a stat: two GRASS Pokémon can be good at different jobs, because base stats, IVs, EVs and nature are evaluated on that job's stat axis.

A worker cycles through its viable jobs in **round-robin** order, so one job never starves the others.

### Honey production

`HONEY_COLLECT` uses beehives/nests and flowers as **persistent catalysts** in the reachable area (never consumed, except the optional `consumeFullHive` bonus):

- **Beehive mode** (`hiveProducesHoneycomb`, default `true`): a beehive or bee nest in the area produces `honeycombPerHarvest` honeycomb per cycle. The hive does **not** need to be full.
- **Flower mode** (`flowerProducesHoneyBottles`, default `true`): flowers (`#minecraft:flowers`) in the area fill honey bottles — one `GLASS_BOTTLE` is withdrawn from linked storage and the `HONEY_BOTTLE` deposited back. `honeyBottlesPerHarvest` sets the output; `requireEmptyBottle` (default `true`) controls the bottle requirement.
- **Optional bonus** (`consumeFullHive`, default `false`): a full hive (`honey_level == 5`) is drained to `0` for one extra honeycomb.

Both modes are independent toggles. The group is eligibility-configured like any other job (`enabled`, `type`, `species`).

---

## Breeding and scaling

Efficiency is split into three independent axes. This is why breeding a worker is worth it:

| Axis | What it does | Config keys |
|------|--------------|-------------|
| **Level** | Work **speed only**. Every Pokémon gains it by grinding. | `levelCap`, `levelWeight` |
| **Base stat + IV + EV** | Work speed **and** extra-yield chance. This is the **breeding axis**. | `baseWeight`, `baseStatReference`, `ivWeight`, `evWeight`, `statBonusCap` |
| **Nature** | Speed only: small bonus when it raises the job's stat, small penalty when it lowers it. | `natureBonus` |

The base/IV/EV sum is capped by `statBonusCap` (default `0.30` = `baseWeight + ivWeight + evWeight`), so only a genuinely perfect breeder reaches the cap.

> **Why not scale on the displayed stat?** `pokemon.getStat()` is level-scaled: a level-50+ Pokémon already saturates any such bonus, which makes IVs and EVs invisible. PastureWorkers therefore reads base stat, IV and EV directly.

**Worked example** (defaults, `workIntervalTicks = 200`):

| Worker | Level | Base | IV | EV | Nature | Speed efficiency | Action interval | Extra yield |
|--------|-------|------|----|----|--------|------------------|-----------------|-------------|
| Just caught | 20 | 70 | 10 | 0 | neutral | 11% | every 9.1 s | +7% |
| Bred for the job | 50 | 110 | 31 | 252 | boosting | 46% | every 6.3 s | +26% |

Extra yield is one duplicated drop item per cycle. Its chance comes from the **quality axis only**, rolled at `min(base+IV+EV contribution, maxExtraYieldChance)` — level never raises it.

---

## Virtual chest GUI

### Opening
- **Shift + right-click** a pasture block with an empty hand (`interaction.shiftRightClickGui`, default `true`).
- **`/pastureworkers gui`** — searches up to 5 blocks from your crosshair, then 16 blocks around you.

### What it shows

| Area | Content |
|------|---------|
| Pasture info | Coordinates, **work area as a box** (radius 8 = 17×17), height, action interval, linked chest count, whether the area is **enclosed or open**, active/idle state, and per-job resource counts. |
| Worker slots (up to 9) | Species, level, **every viable role with its scaling stat**, the base/IV/EV/nature contributions, the resulting action interval, speed multiplier and extra-yield chance. |
| Controls | Toggle the scan-outline particles for yourself; reload the config (operators only); hook status indicator. |

The GUI is **read-only**: every slot interaction is cancelled, so it is a diagnostic and control surface, not a container.

Roles and target counts come from the same `JobSelection` source of truth as the manager and commands, so what you see is what the worker will actually do next.

---

## Commands

All commands are under `/pastureworkers` and require **operator permission level 2**, except `/gui`.

| Command | Permission | Description |
|---------|------------|-------------|
| `/pastureworkers status` | Op 2 | Tethered workers, cooldowns, scan cache, linked chests, hook status, radius/height/interval. |
| `/pastureworkers radius` | Op 2 | Particle outline of the current scan cuboid. |
| `/pastureworkers reload` | Op 2 | Reloads `pastureworkers.json` from disk and re-coerces all ranges. |
| `/pastureworkers gui` | Any player | Opens the GUI for the nearest pasture. |

---

## Configuration

Single JSON file: `config/pastureworkers.json`, created on first run.

**JSON cannot carry comments**, so the file documents itself in two ways:

- Every group has a `_comment` key with a one-line explanation.
- `config/pastureworkers.readme.md` is rewritten on every load with a full key-by-key reference, the allowed `type` and `stat` values, and each job's current `type`/`stat`.

**Applying changes:** edit the file and run `/pastureworkers reload` (op 2), or restart. Numeric values are coerced to their valid ranges on load/save. `schemaVersion` (currently `3`) is managed by the mod — do not edit it.

### `general`

| Key | Default | Range | Meaning |
|-----|---------|-------|---------|
| `workIntervalTicks` | `200` | 20–1200 | Base ticks between a worker's actions. |
| `blocksPerScanTick` | `512` | 64–4096 | Blocks scanned per server tick. |
| `searchRadius` | `8` | 1–32 | Horizontal radius searched around the pasture (8 → 17×17). |
| `searchHeight` | `32` | 1–256 | Vertical blocks searched. |
| `rescanCooldownTicks` | `1200` | 200–7200 | Ticks before the block cache is rescanned. |
| `requireReachableSpace` | `true` | bool | Only work the walkable space connected to the pasture; `false` scans the full box (legacy). |

### `interaction`

| Key | Default | Range | Meaning |
|-----|---------|-------|---------|
| `shiftRightClickGui` | `true` | bool | Shift + right-click with an empty hand opens the GUI. |
| `guiOpenCooldownTicks` | `10` | ≥ 0 | Minimum ticks between GUI opens by the same player. |

### `scaling`

All efficiency and timing formulas read from this group.

| Key | Default | Range | Meaning |
|-----|---------|-------|---------|
| `levelCap` | `100` | 1–1000 | Level at which the level bonus stops growing. |
| `levelWeight` | `0.20` | 0–1 | Max efficiency contributed by level (speed axis only). |
| `baseWeight` | `0.10` | 0–1 | Max efficiency contributed by the species base stat. |
| `baseStatReference` | `180` | ≥ 1 | Base stat value that maps to the full `baseWeight` bonus. |
| `ivWeight` | `0.10` | 0–1 | Max efficiency contributed by a perfect (31) IV. |
| `evWeight` | `0.10` | 0–1 | Max efficiency contributed by a maxed (252) EV. |
| `statBonusCap` | `0.30` | 0–1 | Cap on base + IV + EV combined; by default equals `baseWeight + ivWeight + evWeight`. |
| `natureBonus` | `0.10` | 0–1 | Added for a nature boosting the job stat, subtracted for one lowering it. |
| `efficiencyCap` | `0.50` | 0–1 | Hard upper limit of efficiency. |
| `intervalReduction` | `0.80` | 0–1 | How much efficiency shortens the interval: `workIntervalTicks * (1 - efficiency * intervalReduction)`. |
| `minIntervalTicks` | `20` | 1–1200 | The interval never drops below this. |
| `idleRetryDivisor` | `4` | ≥ 1 | With nothing to do, a worker retries after `workIntervalTicks / idleRetryDivisor`. |
| `maxExtraYieldChance` | `0.50` | 0–1 | Extra-drop chance is `min(quality contribution, this)`. |

### Job groups

`cropHarvest`, `cropIrrigation`, `amethystHarvest`, `gemHarvest`, `apricornHarvest`, `berryHarvest`, `mintHarvest`, `netherwartHarvest`, `tumblestoneHarvest`, `honey`, `groundItemGather` share these keys:

| Key | Default | Range | Meaning |
|-----|---------|-------|---------|
| `enabled` | `true` | bool | Master switch; `false` removes the job everywhere. |
| `type` | per job (below) | type name | Required Pokémon type; blank = no type match. A Pokémon is eligible if its type **or** its species matches. |
| `species` | `["ditto"]` | lowercase showdown names | Additional eligible species. |
| `stat` | per job (below) | `HP`, `ATTACK`, `DEFENCE`, `SPECIAL_ATTACK`, `SPECIAL_DEFENCE`, `SPEED` | Scaling stat; blank = the job default. Unknown names fall back to the default with one log warning. |
| `efficiencyMultiplier` | `1.0` | ≥ 0 | Multiplies this job's efficiency only; still capped by `efficiencyCap`. |

Per-job defaults:

| Group | `type` | `stat` |
|-------|--------|--------|
| `cropHarvest` | `GRASS` | `ATTACK` |
| `cropIrrigation` | `WATER` | `SPECIAL_ATTACK` |
| `amethystHarvest` | `ROCK` | `ATTACK` |
| `gemHarvest` | `ROCK` | `DEFENCE` |
| `apricornHarvest` | `BUG` | `SPEED` |
| `berryHarvest` | `GRASS` | `SPECIAL_DEFENCE` |
| `mintHarvest` | `FAIRY` | `SPECIAL_ATTACK` |
| `netherwartHarvest` | `GHOST` | `SPECIAL_DEFENCE` |
| `tumblestoneHarvest` | `STEEL` | `DEFENCE` |
| `honey` | `BUG` | `SPEED` |
| `groundItemGather` | `PSYCHIC` | `SPEED` |

No job scales on `HP`: HP totals sit far above the other stats, so that axis would be systematically generous.

The `honey` group adds:

| Key | Default | Range | Meaning |
|-----|---------|-------|---------|
| `honeycombPerHarvest` | `1` | ≥ 0 | Honeycomb produced per cycle. |
| `honeyBottlesPerHarvest` | `1` | ≥ 0 | Honey bottles produced per cycle. |
| `requireEmptyBottle` | `true` | bool | Consumes one glass bottle from linked storage per honey bottle. |
| `consumeFullHive` | `false` | bool | Drains a full hive for one bonus honeycomb. |
| `hiveProducesHoneycomb` | `true` | bool | Produce honeycomb when a beehive/nest is in the area. |
| `flowerProducesHoneyBottles` | `true` | bool | Produce honey bottles when flowers are in the area. |

Default `honey` species: `combee`, `vespiquen`, `beedrill`, `teddiursa`, `ursaring`, `ribombee`.

### Example: disabling a job

```json
{ "cropIrrigation": { "enabled": false } }
```

Note that Slowpoke (Water/Psychic) is eligible for `cropIrrigation` and `groundItemGather` by default because those jobs match by **type**. Set `enabled: false` or change `type`/`species` to exclude it.

---

## Storage deposits

Storage is resolved capability-first via the **Fabric Transfer API** (`ItemStorage.SIDED`), which covers vanilla inventories and modded storage such as the Fabric Sophisticated Storage port, Tom's Simple Storage and Storage Drawers. No extra dependency — the Transfer API ships with Fabric API.

Deposits are **verified**: the mod snapshots the target before and after each insert and trusts only the *actual* delta, not the inventory's success claim. This eliminates silent item loss (e.g. Sophisticated Storage double chests). Anything not provably stored is dropped at the pasture.

When the Transfer API is available, mutations run inside a committed outer transaction.

---

## Compared to the original Cobbleworkers

| Feature | Cobbleworkers | PastureWorkers |
|---------|---------------|----------------|
| Movement | Vanilla navigation, workers walk to targets | **None** — virtual, no movement |
| Pathfinding | Full vanilla pathfinder | **Removed** |
| Vertical reach | Hard-coded 8 blocks | **Configurable** (`searchHeight`, default 32) |
| Persistence | Not persisted | **Persisted** — cooldowns, scan state, sessions |
| Config reload | Not supported | **`/pastureworkers reload`** |
| GUI | ModMenu / Cloth Config client screen | **Server-side virtual chest GUI** |
| Scaling | Basic | **Three axes** — level, base/IV/EV, nature |
| Client requirement | Required | **None** — server-side only |

---

## FAQ

**Do perfect IVs matter, or is it just level?**
Both, on different axes: level only makes work faster, while base stat + IV + EV raise speed *and* extra-yield chance. See [Breeding and scaling](#breeding-and-scaling).

**What is the work range?**
A radius of `8` is a **17×17** box (`2 × radius + 1`), 32 blocks tall by default. The GUI shows the exact box for your pasture; `/pastureworkers radius` outlines it in particles.

**Why do workers wander and then get yanked back?**
That is Cobblemon's pasture recall, not this mod — workers never move on their own. It is more noticeable in an open farm, where the reachable area fills the whole search box. Fence the pasture to keep them contained.

**Can I use it on a server where clients don't have the mod?**
Yes. The mod is entirely server-side, including the GUI.

---

## Build

```bash
./gradlew build
```

Output: `build/libs/pastureworkers-1.1.0.jar`. Requires JDK 21.

---

## License

MPL-2.0 — see [LICENSE](LICENSE).

## Credits

PastureWorkers is a **clean-room rewrite** based on the ideas and logical architecture of **Cobbleworkers** by **Accieo** (MPL-2.0): https://github.com/Accieo/Cobbleworkers

## Disclaimer

> PastureWorkers is an independent, community-driven project. It is not affiliated with, endorsed by, or connected to the original author of Cobbleworkers or the official Cobblemon team.
