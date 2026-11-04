# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.1.0] - 2026-11-04

### Added
- **New `scaling` keys** — `baseWeight`, `baseStatReference`, `ivWeight` and `evWeight` expose the species base stat, IV and EV axes of the efficiency formula; `statBonusCap` now defaults to `0.30` (`baseWeight + ivWeight + evWeight`), so only a genuinely perfect breeder reaches it.
- **Self-documenting config** — every group in `pastureworkers.json` now starts with a `_comment` key describing it, and `config/pastureworkers.readme.md` is regenerated on every load with a key-by-key reference (allowed `type`/`stat` values, and that `stat: ""` falls back to the job default).

### Changed
- **Per-job scaling stats are now diverse** — only `cropHarvest` and `amethystHarvest` still scale on `ATTACK`; the other jobs use `SPECIAL_ATTACK` (irrigation, mint), `DEFENCE` (gem, tumblestone), `SPECIAL_DEFENCE` (berry, nether wart) and `SPEED` (apricorn, honey, ground items). Jobs sharing a required type no longer share a stat, so two GRASS or two BUG Pokémon can be good at different jobs. No job scales on HP, whose totals would make that axis systematically generous.
- **Efficiency scaling reworked into three independent axes** — level drives work speed only; species base stat + IV + EV drive both speed and extra yield; nature stays a small bonus or penalty. The old `statBaseline`/`statDivisor` pair scaled on `pokemon.getStat()`, which is level-scaled and saturated the stat bonus above roughly level 40 — that is why testers could not tell whether IVs mattered. IVs and EVs are exactly what breeding improves.
- **Config schema v3 migration** — job `stat` values are rewritten to the new defaults only when the on-disk value still equals the v2 default, and the `scaling` group is replaced only when it still carries the v2 signature (`statBaseline: 70`, `statDivisor: 500`). Genuine user tuning is preserved; the removed keys disappear from the file on the next save.
- **GUI worker info** — each role line shows the stat actually used for that job, the scanned area is reported as a 17×17 box, a bounded-area indicator shows whether the reachable region is contained, and the extra-yield display reports the real chance instead of a stale value.
- **Extra yield is quality-only** — the bonus-drop roll now uses the base/IV/EV contribution alone, so level no longer raises drop chance. Level is purely a speed axis, matching what the GUI tells the player.
- **Hook check at startup** — the Cobblemon tick target is verified once on init rather than waiting for the first pasture tick, which could never happen on an idle server.

### Removed
- `scaling.statBaseline` and `scaling.statDivisor` — replaced by the base/IV/EV axes (see above).

## [1.0.0] - 2026-10-11

### Added
- **Virtual Background Engine** — zero pathfinding, zero AI goals; logical server-side scanning and harvesting on configurable tick intervals.
- **Area scanning** with configurable X/Z radius (`searchRadius`, 1–32, default 8) and Y height (`searchHeight`, 1–256, default 32).
- **Per-dimension sessions** — each pasture block entity maintains independent scan state, cooldowns, and chest links per world.
- **Persistence** of cooldowns, scan cache, and session data across server restarts.
- **Commands**: `/pastureworkers status`, `/pastureworkers radius`, `/pastureworkers gui`, `/pastureworkers reload` (requires operator permission level 2).
- **Virtual Chest GUI** — 9×3 read-only container opened via Shift+Right-click on a pasture (empty hand) or `/pastureworkers gui`; shows pasture info, worker list with efficiency, radius toggle, config reload button, watchdog status.
- **Stat/Nature/Level efficiency scaling** — bonus-drop chance derived from relevant stat (Attack, Special Attack, Speed), IVs, EVs, level, and nature.
- **Job round-robin** — workers cycle through eligible jobs from the registry each work tick.
- **Orphan session sweep** — cleanup of stale sessions for removed/broken pasture block entities.
- **Watchdog for mixin hook** — detects whether the pasture tick hook is attached; surfaced in GUI and `/status`.
- **Farmer's Delight integration** (optional) — crop harvesting supports Farmer's Delight crops when the mod is present.
- **Authoritative verified storage layer (Transfer API + snapshot-diff)** — storage resolved capability-first via Fabric Transfer API (`ItemStorage.SIDED`); deposits verify actual persisted delta via before/after snapshots, eliminating silent loss (e.g., Sophisticated Storage double chests); committed outer transactions when API available; ground-drop fallback for unprovable items.
- **Region-based room model with order-independent perimeter-chest detection** — flood-fill splits walkable space into components seeded from passable face-neighbours (solid pasture never bridges); winner selected by facing → most resources → fewest boundary → largest; single adjacency rule commits detections in or adjacent to winning region.
- **Unified job selection** — single `JobSelection` source of truth used by manager, GUI, and commands; GUI now lists all viable roles with valid targets per Pokémon.
- **Config schema versioning + honey migration** — `schemaVersion` in config; on-disk version read before merge; v0→v1 migration rewrites legacy honey defaults only when raw type ∈ {null,"","NONE"} and raw species == ["ditto"].
- **Independent honey production toggles** — `hiveProducesHoneycomb` and `flowerProducesHoneyBottles` make the beehive and flower honey modes independently enable/disable-able; `honeycombPerHarvest` / `honeyBottlesPerHarvest` control output counts; `requireEmptyBottle` gates bottle withdrawal; `consumeFullHive` adds optional full-hive drain bonus.

### Changed
- **vs. original Cobbleworkers**: completely removed vanilla navigation and claim system; workers never move.
- **Server-side only** — no client mod required; vanilla clients can join and use the GUI.
- **Config location** moved to `config/pastureworkers.json` (was client-side Cloth Config).
- **English-only text** — all user-facing strings are English; no localization files.
- **Species matching** by stable Cobblemon species ID (e.g., `minecraft:bulbasaur`) instead of display names.
- **Storage detection order**: Transfer API first (covers vanilla + modded), then block-entity `Inventory`, then block/block-entity `InventoryProvider` — all guarded.
- **VanillaInventoryTarget** writes back via `Inventory.setStack` (no in-place getter mutation); `TransferApiTarget` uses `Transaction.openOuter()`.
- **DepositUtils** reduced to ground-drop helper only; `StorageDeposit` handles all deposit/withdraw logic with snapshot verification.
- **AreaScanner** uses global detection maps + per-region stats + union-find; `commitWinner()` applies adjacency rule; `requireReachableSpace==false` box path unchanged.
- **Commands.status / PastureGui / PastureWorkManager** route through `JobSelection.viableJobs()` / `isActive()`; `firstEligibleJob` removed.

### Removed
- ModMenu / Cloth Config client configuration screen.
- Client-side dependencies (ModMenu, Cloth Config, Fabric Language Kotlin client entrypoint).
- Native vanilla navigation/pathfinding code paths.
- Claim system (workers no longer "claim" blocks; scanning is stateless per tick).

### Fixed
- **Session collisions across dimensions** — each dimension now holds independent session maps keyed by pasture position.
- **Stale bounds on config change** — `/reload` and GUI reload button re-coerce ranges and refresh scan cuboids immediately.
- **Unbounded chest list** — chest linking now validates and deduplicates on each session load.
- **Starvation of job priority** — round-robin scheduling ensures all eligible jobs get cycles; no single job monopolizes workers.
- **Vertical search cap** — the original capped the search height at 8 blocks; now configurable up to 256.
- **Silent item loss with modded storage** — snapshot verification catches adapters that claim acceptance but persist nothing; such targets are marked suspect and skipped.
- **Perimeter chest detection order-dependence** — adjacency rule ensures a chest in the wall is recognized when interior wins, regardless of scan order.
- **GUI showing only first registry-matched job** — now shows all viable roles via `JobSelection`.
- **Config migration clobbering user edits** — raw on-disk JSON read before merge; migration triggers only on untouched legacy signatures.