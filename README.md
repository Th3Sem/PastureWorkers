# PastureWorkers

A Fabric mod for Minecraft 1.21.1 that lets tethered Cobblemon Pokémon perform pasture jobs without moving. Zero pathfinding, zero AI goals, pure server-side logical scanning on tick intervals.

[![License: MPL-2.0](https://img.shields.io/badge/License-MPL--2.0-brightgreen.svg)](https://opensource.org/licenses/MPL-2.0)
[![Minecraft 1.21.1](https://img.shields.io/badge/Minecraft-1.21.1-62b47a.svg)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/Fabric-0.19.5+-0099cc.svg)](https://fabricmc.net/)
[![Cobblemon 1.8.1](https://img.shields.io/badge/Cobblemon-1.8.1-ff6b6b.svg)](https://cobblemon.com/)
[![GitHub](https://img.shields.io/badge/GitHub-Th3Sem%2FPastureWorkers-181717.svg)](https://github.com/Th3Sem/PastureWorkers)

## Virtual Background Engine

PastureWorkers replaces the traditional "worker walks to block, interacts, walks back" loop with a **virtual background engine**:

- **Zero pathfinding** — no navigation mesh, no AI goals, no movement code paths.
- **Zero AI goals** — Pokémon never leave the pasture block entity; work is purely logical.
- **Server-side scanning** — on each work interval the server scans a configurable cuboid around the pasture, budgets block checks per tick (`blocksPerScanTick`), and performs harvest/interaction actions instantly.
- **Budgeted block scanning** — `blocksPerScanTick` (default 512, range 64–4096) caps CPU cost per tick; large radii scale gracefully.
- **Zero tick-lag focus** — the engine is designed to keep per-tick cost predictable and low; no entity movement, no chunk loading from wandering workers.

### Cached Room Reachability (Flood-Fill)

When `requireReachableSpace` is `true` (default), the scanner performs a **cached flood-fill** from the pasture block across ticks, expanding only through blocks without a collision box (air, plants, crops, water pass; walls, fences, solid blocks block). The result is a contiguous walkable region:

- A closed perimeter wall **contains** the Pokémon — crops and chests outside the walls are never reached, even if within radius/height.
- Internal pillars/obstacles are routed around.
- The region is recomputed on the normal rescan cadence (`rescanCooldownTicks`, default 1200).
- An unroofed/open farm is **not contained** — the region escapes into open air and effectively fills the whole search box.

**Region-based room model:** Detection (storage + job targets) is accumulated globally across the search box. The flood-fill splits the walkable space into connected components (seeded from the pasture's passable face-neighbours; the solid pasture itself never bridges components). A single priority rule selects the primary region: the component in the pasture's facing direction, then the one with the most job-relevant resource cells, then fewest boundary cells, then largest. At commit time, a detection is kept iff it lies in or is adjacent to the winning region — so a chest embedded in the perimeter wall is still recognized when the interior region wins (order-independent).

Set `requireReachableSpace: false` in the `general` config to disable the flood-fill and fall back to the full solid box scan.

## Comparison vs. Original Cobbleworkers

| Feature | Cobbleworkers (original) | PastureWorkers (this mod) |
|---------|--------------------------|---------------------------|
| Movement | Vanilla navigation, workers walk to targets | **None** — virtual, no movement |
| Pathfinding | Full vanilla pathfinder | **Removed** |
| Vertical reach limit | Hard-coded 8 blocks search height | **Configurable Y** (`searchHeight`, default 32, max 256) |
| Persistence across restarts | Not persisted | **Persisted** — cooldowns, scan state, sessions saved |
| Config reload | Not supported | **`/pastureworkers reload`** (op level 2) |
| GUI | ModMenu / Cloth Config client screen | **Server-side virtual chest GUI** (read-only) |
| Scaling by stats/nature | Basic | **Full** — level, IVs, EVs, nature, relevant stat per job |
| Client requirement | Required (client mod) | **None** — server-side only |

## Jobs

Each job has a required Pokémon type and an efficiency stat that scales bonus-drop chance.

| Job Type | Required Type | Relevant Stat | Config Group |
|----------|---------------|---------------|--------------|
| Crop Harvest | GRASS | Attack | `cropHarvest` |
| Crop Irrigation | WATER | Special Attack | `cropIrrigation` |
| Amethyst Harvest | ROCK | Attack | `amethystHarvest` |
| Gem Harvest | ROCK | Attack | `gemHarvest` |
| Apricorn Harvest | BUG | Attack | `apricornHarvest` |
| Berry Harvest | GRASS | Attack | `berryHarvest` |
| Mint Harvest | FAIRY | Special Attack | `mintHarvest` |
| Nether Wart Harvest | GHOST | Special Attack | `netherwartHarvest` |
| Tumblestone Harvest | STEEL | Attack | `tumblestoneHarvest` |
| Honey Collect | NONE | — (time-based) | `honey` |
| Ground Item Gather | PSYCHIC | Speed | `groundItemGather` |

Jobs run in **round-robin** order per worker per tick. A worker picks the first eligible job from the registry.

### Honey Production

The `HONEY_COLLECT` job ("Honey Harvester") uses beehives/nests and flowers as **persistent catalysts** in the reachable area (never consumed, except the optional `consumeFullHive` bonus):

- **Beehive mode** (`hiveProducesHoneycomb`, default `true`): when a beehive or bee nest is within the reachable region, the worker produces honeycomb each work cycle. The hive does **not** need to be full. `honeycombPerHarvest` (default `1`, `0` disables) sets the output count.
- **Flower mode** (`flowerProducesHoneyBottles`, default `true`): when flowers (`#minecraft:flowers`) are within the reachable region, the worker fills a honey bottle — it withdraws one empty `GLASS_BOTTLE` from linked storage and deposits the produced `HONEY_BOTTLE` back into linked storage. `honeyBottlesPerHarvest` (default `1`, `0` disables) sets the output count. `requireEmptyBottle` (default `true`) controls whether a bottle must be available.
- **Optional bonus** (`consumeFullHive`, default `false`): if a matching hive is full (`honey_level == 5`), it is drained to `0` for one extra honeycomb.
- The honey group is eligibility-configured like any other job: `enabled`, `type` (default `BUG`), `species` (default `[combee, vespiquen, beedrill, teddiursa, ursaring, ribombee]`).

Both modes are independent — you can enable/disable each via its toggle.

## Commands

All commands are under `/pastureworkers` and require **operator permission level 2**, except `/gui`.

| Command | Permission | Description |
|---------|------------|-------------|
| `/pastureworkers status` | Op level 2 | Shows tethered workers, cooldowns, scan cache, linked chests, watchdog status, and current radius/height/interval. |
| `/pastureworkers radius` | Op level 2 | Displays a particle outline of the current scan cuboid (X/Z radius, Y height). |
| `/pastureworkers reload` | Op level 2 | Reloads `pastureworkers.json` from disk, re-coerces all ranges. |
| `/pastureworkers gui` | Any player | Opens the virtual chest GUI for the nearest pasture (requires `shiftRightClickGui: true` in config). |

## Virtual Chest GUI

### Opening
- **Shift + Right-click** a Cobblemon Pasture block with an empty hand (requires `interaction.shiftRightClickGui: true`, default `true`).
- **Command**: `/pastureworkers gui` (searches up to 5 blocks from your crosshair, then 16-block radius around you).

### Layout (9×3 = 27 slots)

| Row | Slots | Content |
|-----|-------|---------|
| 0 (top) | 0–8 | **Slot 4**: Pasture Info (compass) — coordinates, radius, height, interval, chest count.<br>**Slot 8**: Toggle Radius Particles (lever) — click to show/hide scan outline for you only.<br>Others: gray glass panes (filler). |
| 1 (middle) | 9–17 | **Worker slots** (up to 9) — each shows a player head with lore: species, level, **all viable roles** with valid targets, relevant stat value, efficiency %, bonus-drop chance %. |
| 2 (bottom) | 18–26 | **Slot 18**: Reload Config (command block if op, barrier otherwise).<br>**Slot 20**: Watchdog Status (lime dye = active, red dye = hook not attached).<br>Others: filler. |

### Read-only / Anti-theft
- **All slots are display-only**. The screen handler cancels every slot interaction (`onSlotClick`, `quickMove`, `canInsertIntoSlot` all return empty/no-op).
- Items cannot be taken, moved, inserted, or shift-clicked. The GUI is a diagnostic/control surface, not a container.

**Unified job selection:** The GUI now lists **all** roles a Pokémon can currently perform with valid targets (via the single `JobSelection` source of truth shared by the manager, GUI, and commands). This fixes cases where a Pokémon with multiple eligible jobs (e.g., Combee) would only show the first registry match instead of every viable role.

## Configuration

**File location**: `config/pastureworkers.json` (created on first run).

### Example `pastureworkers.json` (all keys with defaults)

```json
{
  "schemaVersion": 1,
  "general": {
    "workIntervalTicks": 200,
    "blocksPerScanTick": 512,
    "searchRadius": 8,
    "searchHeight": 32,
    "rescanCooldownTicks": 1200,
    "requireReachableSpace": true
  },
  "interaction": {
    "shiftRightClickGui": true,
    "guiOpenCooldownTicks": 10
  },
  "cropHarvest": { "enabled": true, "type": "GRASS", "species": ["ditto"] },
  "cropIrrigation": { "enabled": true, "type": "WATER", "species": ["ditto"] },
  "amethystHarvest": { "enabled": true, "type": "ROCK", "species": ["ditto"] },
  "gemHarvest": { "enabled": true, "type": "ROCK", "species": ["ditto"] },
  "apricornHarvest": { "enabled": true, "type": "BUG", "species": ["ditto"] },
  "berryHarvest": { "enabled": true, "type": "GRASS", "species": ["ditto"] },
  "mintHarvest": { "enabled": true, "type": "FAIRY", "species": ["ditto"] },
  "netherwartHarvest": { "enabled": true, "type": "GHOST", "species": ["ditto"] },
  "tumblestoneHarvest": { "enabled": true, "type": "STEEL", "species": ["ditto"] },
  "honey": { "enabled": true, "type": "BUG", "species": ["combee", "vespiquen", "beedrill", "teddiursa", "ursaring", "ribombee"], "hiveProducesHoneycomb": true, "honeycombPerHarvest": 1, "flowerProducesHoneyBottles": true, "honeyBottlesPerHarvest": 1, "requireEmptyBottle": true, "consumeFullHive": false },
  "groundItemGather": { "enabled": true, "type": "PSYCHIC", "species": ["ditto"] }
}
```

### Groups explained

- **general** — Core engine tuning: how often each worker attempts work (`workIntervalTicks`), how many blocks scanned per server tick (`blocksPerScanTick`), horizontal radius (`searchRadius`), vertical height (`searchHeight`), cache rescan cooldown (`rescanCooldownTicks`), and whether to limit scanning to the contiguous walkable space connected to the pasture (`requireReachableSpace`, default `true`).
- **interaction** — GUI behaviour: enable shift+right-click (`shiftRightClickGui`), per-player open cooldown in ticks (`guiOpenCooldownTicks`).
- **Job groups** (`cropHarvest`, `cropIrrigation`, …, `honey`, `groundItemGather`) — Per-job toggles:
  - `enabled` — master switch for that job.
  - `type` — required Pokémon type (matches Cobblemon type names: `GRASS`, `WATER`, `ROCK`, `BUG`, `FAIRY`, `GHOST`, `STEEL`, `PSYCHIC`, `NONE`).
  - `species` — allow-list of species IDs (e.g., `"bulbasaur"`); default `["ditto"]` means any species with the correct type qualifies.
  - `honey` group adds:
    - `hiveProducesHoneycomb` (Boolean, default `true`) — enable honeycomb production when a beehive/nest is in the reachable area.
    - `honeycombPerHarvest` (Int, default `1`) — honeycomb count per work cycle; `0` disables.
    - `flowerProducesHoneyBottles` (Boolean, default `true`) — enable honey bottle production when flowers are in the reachable area.
    - `honeyBottlesPerHarvest` (Int, default `1`) — honey bottle count per work cycle; `0` disables.
    - `requireEmptyBottle` (Boolean, default `true`) — when `true`, a `GLASS_BOTTLE` must be available in linked storage for honey bottles; `false` produces bottles without consuming one.
    - `consumeFullHive` (Boolean, default `false`) — if a matching hive is full (`honey_level == 5`), drain it to `0` for one bonus honeycomb.

### Authoritative Storage Layer

PastureWorkers now resolves storage capability-first via the **Fabric Transfer API** (`ItemStorage.SIDED`), which auto-covers vanilla `Inventory`/`SidedInventory`/`InventoryProvider` and modded storage such as the Fabric Sophisticated Storage port, Tom's Simple Storage, and Storage Drawers. No new dependency is required — the Transfer API ships with Fabric API.

Deposits are **verified**: the mod snapshots the target's contents before and after each insert and trusts only the *actual* delta, not the inventory's success claim. This eliminates silent item loss (e.g., with Sophisticated Storage double chests). Anything not provably stored is ground-dropped at the pasture.

When the Transfer API is available, mutations run inside a committed outer transaction so items are only considered stored once the transaction succeeds.

**Config schema versioning:** The config file includes a `schemaVersion` field. On upgrade, a small migration rebuilds legacy honey defaults (type/species) only when they were never changed by the user — genuine user edits are always preserved.

All numeric values are **coerced** to their valid ranges on load/save.

## Installation

**Requirements (server only):**
- Fabric Loader ≥ 0.19.5
- Fabric API
- Fabric Language Kotlin
- Cobblemon 1.8.1 (for Minecraft 1.21.1)

**Clients do not need the mod.** PastureWorkers is fully server-side; vanilla clients can join and use the GUI (particles are server-spawned).

1. Install the above dependencies on your Fabric server.
2. Drop `pastureworkers-1.0.0.jar` into the `mods/` folder.
3. Start the server once to generate `config/pastureworkers.json`.
4. Adjust config as desired, then `/pastureworkers reload` or restart.

## Build

```bash
./gradlew build
```

Output: `build/libs/pastureworkers-1.0.0.jar` (name from `archives_base_name` + `mod_version`).

Requires JDK 21 (configured via toolchain).

## License

MPL-2.0 — see [LICENSE](LICENSE).

## Credits & Attribution

PastureWorkers is a **clean-room rewrite** based on the ideas and logical architecture of **Cobbleworkers** by **Accieo** (MPL-2.0).

Original repository: https://github.com/Accieo/Cobbleworkers

## Disclaimer

> PastureWorkers is an independent, community-driven project. It is not affiliated with, endorsed by, or connected to the original author of Cobbleworkers or the official Cobblemon team.