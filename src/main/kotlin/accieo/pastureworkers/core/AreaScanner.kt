/*
 * Copyright (c) 2026 Accieo
 * Copyright (c) 2026 Th3Sem
 *
 * SPDX-License-Identifier: MPL-2.0
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package accieo.pastureworkers.core

import accieo.pastureworkers.PastureWorkers
import accieo.pastureworkers.api.JobRegistry
import accieo.pastureworkers.api.JobType
import accieo.pastureworkers.api.WorkJob
import accieo.pastureworkers.config.PastureWorkersConfig
import accieo.pastureworkers.core.storage.StorageResolver
import net.minecraft.block.BlockState
import net.minecraft.block.HorizontalFacingBlock
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos

class AreaScanner(
    private val pasturePos: BlockPos,
    private val world: ServerWorld,
    private val session: PastureSession
) {
    // Recalculated at the start of every scan so config changes apply without recreating the session.
    private var minX = 0
    private var maxX = 0
    private var minY = 0
    private var maxY = 0
    private var minZ = 0
    private var maxZ = 0

    // Plain box-iteration cursor, used when requireReachableSpace is false.
    private var currentX = 0
    private var currentY = 0
    private var currentZ = 0
    private var scanning = false

    // Flood-fill traversal state, persisted across ticks and reset at the start of each scan.
    private val visited = HashSet<Long>()
    private val frontier = ArrayDeque<Long>()
    private var floodFillScan = false

    // Seed + region state. Seeds are the pasture's passable face-neighbours; a small union-find over
    // the ≤6 seeds isolates connected components that must not merge through the solid pasture.
    private var seedCells = LongArray(0)
    private val seedIndexOf = HashMap<Long, Int>()
    private val reachable = HashMap<Long, Int>()
    private var ufParent = IntArray(0)
    private var regionStats: Array<RegionStats> = emptyArray()

    // Detection is accumulated globally so it can be filtered by the winning region at commit time.
    private val detectedStorage = HashSet<Long>()
    private val detectedTargets = HashMap<JobType, MutableSet<Long>>()
    private val boundaryCells = HashSet<Long>()

    // Job types whose `matches` has already thrown during this scanner's lifetime; used to log once.
    private val matchFailuresLogged = HashSet<JobType>()

    fun step(currentTime: Long) {
        if (!shouldScan(currentTime)) return

        if (!scanning) {
            val general = PastureWorkersConfig.config.general
            recomputeBounds(general)
            session.clearBlockTargets()
            session.chests.clear()
            session.reachableBoundary.clear()
            scanning = true
            floodFillScan = general.requireReachableSpace
            if (floodFillScan) {
                seedFloodFill()
            } else {
                currentX = minX
                currentY = minY
                currentZ = minZ
            }
        }

        var budget = PastureWorkersConfig.config.general.blocksPerScanTick.coerceAtLeast(1)
        while (budget-- > 0) {
            if (floodFillScan) {
                if (frontier.isEmpty()) {
                    finishScan(currentTime)
                    return
                }
                stepFloodFill()
            } else {
                scanBlock(BlockPos(currentX, currentY, currentZ))
                if (!advance()) {
                    finishScan(currentTime)
                    return
                }
            }
        }
    }

    private fun recomputeBounds(general: PastureWorkersConfig.GeneralGroup) {
        minX = pasturePos.x - general.searchRadius
        maxX = pasturePos.x + general.searchRadius
        minZ = pasturePos.z - general.searchRadius
        maxZ = pasturePos.z + general.searchRadius
        minY = (pasturePos.y - general.searchHeight).coerceAtLeast(world.bottomY)
        maxY = (pasturePos.y + general.searchHeight).coerceAtMost(world.topY - 1)
    }

    private fun shouldScan(currentTime: Long): Boolean {
        val last = session.lastScanCompleteTime
        return last == 0L || currentTime - last >= PastureWorkersConfig.config.general.rescanCooldownTicks
    }

    // ponytail: passability is a collision-shape heuristic (no collision box => walkable: air, crops,
    // plants, water and pressure plates pass; walls, fences and solid blocks block). Upgrade path =
    // per-block custom rules if a server needs finer control.
    private fun isPassable(pos: BlockPos): Boolean =
        world.getBlockState(pos).getCollisionShape(world, pos).isEmpty

    // ---------------------------------------------------------------------------------------------
    // Flood-fill seeding + regions
    // ---------------------------------------------------------------------------------------------

    private fun seedFloodFill() {
        visited.clear()
        frontier.clear()
        reachable.clear()
        seedIndexOf.clear()
        detectedStorage.clear()
        detectedTargets.clear()
        boundaryCells.clear()

        val seeds = ArrayList<Long>(NEIGHBOR_OFFSETS.size)
        // Seeds are the pasture's passable face-neighbours. The pasture block is solid and must never
        // act as a connector, otherwise an inset pasture bridges its interior and exterior air.
        for (offset in NEIGHBOR_OFFSETS) {
            val x = pasturePos.x + offset[0]
            val y = pasturePos.y + offset[1]
            val z = pasturePos.z + offset[2]
            if (outOfBounds(x, y, z)) continue
            val neighbor = BlockPos(x, y, z)
            if (!world.isChunkLoaded(neighbor)) continue
            if (!isPassable(neighbor)) continue
            val packed = neighbor.asLong()
            if (seedIndexOf.containsKey(packed)) continue
            seedIndexOf[packed] = seeds.size
            seeds.add(packed)
        }

        if (seeds.isEmpty()) {
            // Graceful degradation: the pasture is fully enclosed by solids. Seed the pasture cell and
            // let it expand, reproducing the legacy behaviour instead of scanning nothing.
            seedIndexOf[pasturePos.asLong()] = 0
            seeds.add(pasturePos.asLong())
        }

        seedCells = seeds.toLongArray()
        val count = seedCells.size
        ufParent = IntArray(count) { it }
        regionStats = Array(count) { RegionStats() }

        for (i in 0 until count) {
            val packed = seedCells[i]
            reachable[packed] = i
            frontier.addLast(packed)
        }
    }

    private fun stepFloodFill() {
        val packed = frontier.removeFirst()
        val owner = reachable[packed] ?: return
        if (!visited.add(packed)) return

        val pos = BlockPos.fromLong(packed)
        if (!world.isChunkLoaded(pos)) return

        val component = find(owner)
        // Detection runs on every visited cell, passable or not: chests, barrels, beehives, gem and
        // amethyst clusters are solid blocks and must still be registered as storage/targets.
        if (detectCell(pos)) regionStats[component].resource++
        regionStats[component].size++

        // Only expansion is gated on passability. A wall/pillar is worked but not traversed; only the
        // fallback seed (the solid pasture cell) is allowed to expand despite being solid.
        if (isPassable(pos) || seedIndexOf.containsKey(packed)) {
            if (isBoundaryCell(pos)) {
                regionStats[component].boundary++
                boundaryCells.add(packed)
            }
            enqueueNeighbors(pos, component)
        }
    }

    private fun enqueueNeighbors(pos: BlockPos, component: Int) {
        for (offset in NEIGHBOR_OFFSETS) {
            val x = pos.x + offset[0]
            val y = pos.y + offset[1]
            val z = pos.z + offset[2]
            if (outOfBounds(x, y, z)) continue
            val packed = BlockPos.asLong(x, y, z)
            if (visited.contains(packed)) continue

            val existing = reachable[packed]
            if (existing != null) {
                // A cell shared by two explorations only proves they are connected when the cell is
                // walkable. A solid block (e.g. the pasture itself) must not bridge two regions.
                if (isPassable(BlockPos(x, y, z))) union(existing, find(component))
                continue
            }
            reachable[packed] = find(component)
            frontier.addLast(packed)
        }
    }

    private fun find(index: Int): Int {
        var root = index
        while (ufParent[root] != root) root = ufParent[root]
        var node = index
        while (ufParent[node] != root) {
            val next = ufParent[node]
            ufParent[node] = root
            node = next
        }
        return root
    }

    private fun union(a: Int, b: Int) {
        val rootA = find(a)
        val rootB = find(b)
        if (rootA != rootB) ufParent[rootB] = rootA
    }

    private fun outOfBounds(x: Int, y: Int, z: Int): Boolean =
        x < minX || x > maxX || y < minY || y > maxY || z < minZ || z > maxZ

    // A reachable cell is on the region boundary when any face-neighbour is outside the search box or
    // is not walkable (a wall, a solid catalyst block, etc.).
    private fun isBoundaryCell(pos: BlockPos): Boolean {
        for (offset in NEIGHBOR_OFFSETS) {
            val x = pos.x + offset[0]
            val y = pos.y + offset[1]
            val z = pos.z + offset[2]
            if (outOfBounds(x, y, z)) return true
            if (!isPassable(BlockPos(x, y, z))) return true
        }
        return false
    }

    /** Records this cell's storage/target detections globally; returns true when it matches a job. */
    private fun detectCell(pos: BlockPos): Boolean {
        if (!world.isChunkLoaded(pos)) return false

        val state = world.getBlockState(pos)

        if (StorageResolver.isStorage(world, pos)) {
            detectedStorage.add(pos.asLong())
        }

        var matched = false
        for (job in JobRegistry.jobs) {
            if (!job.needsBlockTargets) continue
            if (matchesSafely(job, state, pos)) {
                detectedTargets.getOrPut(job.jobType) { mutableSetOf() }.add(pos.asLong())
                matched = true
            }
        }
        return matched
    }

    /**
     * Guards [WorkJob.matches] so a modded block that belongs to a matching tag but lacks an expected
     * property skips only this cell for this job instead of aborting the whole scan tick. Logs once per
     * job type.
     */
    private fun matchesSafely(job: WorkJob, state: BlockState, pos: BlockPos): Boolean =
        runCatching { job.matches(state, world, pos) }
            .onFailure { e ->
                if (matchFailuresLogged.add(job.jobType)) {
                    PastureWorkers.LOGGER.error(
                        "[PastureWorkers] Job {} failed to match a block; skipping that cell",
                        job.jobType,
                        e
                    )
                }
            }
            .getOrDefault(false)

    // ---------------------------------------------------------------------------------------------
    // Winner selection + commit
    // ---------------------------------------------------------------------------------------------

    private class RegionStats {
        var size = 0
        var resource = 0
        var boundary = 0
    }

    /** Index of the seed cell in the pasture's facing direction, or -1 when it is not a seed. */
    private fun facingSeedIndex(): Int {
        val state = world.getBlockState(pasturePos)
        if (!state.contains(HorizontalFacingBlock.FACING)) return -1
        val direction = state.get(HorizontalFacingBlock.FACING)
        return seedIndexOf[pasturePos.offset(direction).asLong()] ?: -1
    }

    /**
     * Commits exactly one region to the session, by priority: the region in the pasture's facing
     * direction, then most job-relevant resource cells, then fewest boundary cells, then the largest.
     * A detection is kept iff it lies in the winning region: passable cells by ownership, solid cells
     * by having a passable face-neighbour owned by the winner. Non-winning detections leave no residue.
     */
    private fun commitWinner() {
        val count = seedCells.size
        if (count == 0) return

        val statsByRoot = HashMap<Int, RegionStats>()
        for (i in 0 until count) {
            val root = find(i)
            val accumulated = statsByRoot.getOrPut(root) { RegionStats() }
            accumulated.size += regionStats[i].size
            accumulated.resource += regionStats[i].resource
            accumulated.boundary += regionStats[i].boundary
        }

        val facingRoot = facingSeedIndex().let { if (it >= 0) find(it) else -1 }
        val winner = statsByRoot.entries.maxWithOrNull(
            compareBy<Map.Entry<Int, RegionStats>> { if (it.key == facingRoot) 1 else 0 }
                .thenBy { it.value.resource }
                .thenByDescending { it.value.boundary }
                .thenBy { it.value.size }
        ) ?: return
        val winnerRoot = winner.key

        for (packed in detectedStorage) {
            if (inWinner(packed, winnerRoot)) session.chests.add(BlockPos.fromLong(packed))
        }
        for ((jobType, cells) in detectedTargets) {
            for (packed in cells) {
                if (inWinner(packed, winnerRoot)) session.addTarget(jobType, BlockPos.fromLong(packed))
            }
        }
        for (packed in boundaryCells) {
            val owner = reachable[packed] ?: continue
            if (find(owner) == winnerRoot) session.reachableBoundary.add(BlockPos.fromLong(packed))
        }
    }

    /**
     * Whether a detected cell belongs to the winning region: passable cells by resolved ownership,
     * solid cells when any passable face-neighbour resolves to the winner.
     */
    private fun inWinner(packed: Long, winnerRoot: Int): Boolean {
        val owner = reachable[packed] ?: return false
        val pos = BlockPos.fromLong(packed)
        if (isPassable(pos)) return find(owner) == winnerRoot

        for (offset in NEIGHBOR_OFFSETS) {
            val x = pos.x + offset[0]
            val y = pos.y + offset[1]
            val z = pos.z + offset[2]
            if (outOfBounds(x, y, z)) continue
            val neighbor = BlockPos(x, y, z)
            if (!isPassable(neighbor)) continue
            val neighborOwner = reachable[BlockPos.asLong(x, y, z)] ?: continue
            if (find(neighborOwner) == winnerRoot) return true
        }
        return false
    }

    // ---------------------------------------------------------------------------------------------
    // Box path (requireReachableSpace == false) — unchanged legacy full-box scan
    // ---------------------------------------------------------------------------------------------

    private fun scanBlock(pos: BlockPos) {
        if (!world.isChunkLoaded(pos)) return

        val state = world.getBlockState(pos)

        if (StorageResolver.isStorage(world, pos)) {
            session.chests.add(pos.toImmutable())
        }

        for (job in JobRegistry.jobs) {
            if (!job.needsBlockTargets) continue
            if (matchesSafely(job, state, pos)) {
                session.addTarget(job.jobType, pos.toImmutable())
            }
        }
    }

    private fun advance(): Boolean {
        currentX++
        if (currentX > maxX) {
            currentX = minX
            currentZ++
            if (currentZ > maxZ) {
                currentZ = minZ
                currentY++
                if (currentY > maxY) {
                    return false
                }
            }
        }
        return true
    }

    private fun finishScan(currentTime: Long) {
        if (floodFillScan) commitWinner()
        session.lastScanCompleteTime = currentTime
        scanning = false
    }

    private companion object {
        /** The six face-neighbour offsets used by the flood-fill. */
        val NEIGHBOR_OFFSETS = arrayOf(
            intArrayOf(1, 0, 0),
            intArrayOf(-1, 0, 0),
            intArrayOf(0, 1, 0),
            intArrayOf(0, -1, 0),
            intArrayOf(0, 0, 1),
            intArrayOf(0, 0, -1)
        )
    }
}