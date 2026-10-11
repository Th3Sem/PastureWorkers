# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- **Authoritative verified storage layer (Transfer API + snapshot-diff)** — storage resolved capability-first via Fabric Transfer API (`ItemStorage.SIDED`); deposits verify actual persisted delta via before/after snapshots, eliminating silent loss (e.g., Sophisticated Storage double chests); committed outer transactions when API available; ground-drop fallback for unprovable items.
- **Region-based room model with order-independent perimeter-chest detection** — flood-fill splits walkable space into components seeded from passable face-neighbours (solid pasture never bridges); winner selected by facing → most resources → fewest boundary → largest; single adjacency rule commits detections in or adjacent to winning region.
- **Unified job selection** — single `JobSelection` source of truth used by manager, GUI, and commands; GUI now lists all viable roles with valid targets per Pokémon.
- **Config schema versioning + honey migration** — `schemaVersion` in config; on-disk version read before merge; v0→v1 migration rewrites legacy honey defaults only when raw type ∈ {null,"","NONE"} and raw species == ["ditto"].

### Changed
- **Storage detection order**: Transfer API first (covers vanilla + modded), then block-entity `Inventory`, then block/block-entity `InventoryProvider` — all guarded.
- **VanillaInventoryTarget** writes back via `Inventory.setStack` (no in-place getter mutation); `TransferApiTarget` uses `Transaction.openOuter()`.
- **DepositUtils** reduced to ground-drop helper only; `StorageDeposit` handles all deposit/withdraw logic with snapshot verification.
- **AreaScanner** uses global detection maps + per-region stats + union-find; `commitWinner()` applies adjacency rule; `requireReachableSpace==false` box path unchanged.
- **Commands.status / PastureGui / PastureWorkManager** route through `JobSelection.viableJobs()` / `isActive()`; `firstEligibleJob` removed.

### Fixed
- **Silent item loss with modded storage** — snapshot verification catches adapters that claim acceptance but persist nothing; such targets are marked suspect and skipped.
- **Perimeter chest detection order-dependence** — adjacency rule ensures a chest in the wall is recognized when interior wins, regardless of scan order.
- **GUI showing only first registry-matched job** — now shows all viable roles via `JobSelection`.
- **Config migration clobbering user edits** — raw on-disk JSON read before merge; migration triggers only on untouched legacy signatures.

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

### Changed
- **vs. original Cobbleworkers**: completely removed vanilla navigation and claim system; workers never move.
- **Server-side only** — no client mod required; vanilla clients can join and use the GUI.
- **Config location** moved to `config/pastureworkers.json` (was client-side Cloth Config).
- **English-only text** — all user-facing strings are English; no localization files.
- **Species matching** by stable Cobblemon species ID (e.g., `minecraft:bulbasaur`) instead of display names.

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
- **Search height limit of 8** — original hard-coded vertical cap removed; now fully configurable up to 256 blocks.