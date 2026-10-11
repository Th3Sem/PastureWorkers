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

import accieo.pastureworkers.api.JobRegistry
import accieo.pastureworkers.config.PastureWorkersConfig
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

    // Flood-fill state, persisted across ticks and reset at the start of each reachable-space scan.
    private val visited = HashSet<Long>()
    private val frontier = ArrayDeque<Long>()
    private var floodFillScan = false

    fun step(currentTime: Long) {
        if (!shouldScan(currentTime)) return

        if (!scanning) {
            val general = PastureWorkersConfig.config.general
            recomputeBounds(general)
            session.clearBlockTargets()
            session.chests.clear()
            scanning = true
            floodFillScan = general.requireReachableSpace
            if (floodFillScan) {
                visited.clear()
                frontier.clear()
                // Anchor the flood-fill at the pasture: the pasture cell is solid, but it always
                // expands its neighbours regardless of its own passability.
                frontier.addLast(pasturePos.asLong())
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

    private fun stepFloodFill() {
        val packed = frontier.removeFirst()
        if (!visited.add(packed)) return

        val pos = BlockPos.fromLong(packed)
        if (pos != pasturePos) {
            if (!world.isChunkLoaded(pos)) return
            // A wall/pillar is neither worked nor expanded through, which is what separates an
            // interior pillar (routable around) from a closed perimeter wall (stops the region).
            if (!isPassable(pos)) return
        }

        scanBlock(pos)
        enqueueNeighbors(pos)
    }

    private fun enqueueNeighbors(pos: BlockPos) {
        for (offset in NEIGHBOR_OFFSETS) {
            val x = pos.x + offset[0]
            val y = pos.y + offset[1]
            val z = pos.z + offset[2]
            if (x < minX || x > maxX || y < minY || y > maxY || z < minZ || z > maxZ) continue
            val packed = BlockPos.asLong(x, y, z)
            if (visited.contains(packed)) continue
            frontier.addLast(packed)
        }
    }

    private fun scanBlock(pos: BlockPos) {
        if (!world.isChunkLoaded(pos)) return

        val state = world.getBlockState(pos)

        if (DepositUtils.isInventory(world, pos)) {
            session.chests.add(pos.toImmutable())
        }

        for (job in JobRegistry.jobs) {
            if (!job.needsBlockTargets) continue
            if (job.matches(state, world, pos)) {
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