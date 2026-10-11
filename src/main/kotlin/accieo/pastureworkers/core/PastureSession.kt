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

import accieo.pastureworkers.api.JobType
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class PastureSession(
    val world: ServerWorld,
    val pasturePos: BlockPos,
    private val stateData: PastureWorkersState.PastureData
) {
    val cache: MutableMap<JobType, MutableSet<BlockPos>> = ConcurrentHashMap()
    val chests: MutableSet<BlockPos> = ConcurrentHashMap.newKeySet()

    /**
     * Cells on the boundary of the reachable region discovered by the flood-fill (reachable cells
     * with a non-passable or out-of-bounds face-neighbour). Empty while reachability is disabled.
     */
    val reachableBoundary: MutableSet<BlockPos> = ConcurrentHashMap.newKeySet()
    val nextWorkTimes: MutableMap<UUID, Long> = stateData.nextWorkTimes
    var lastScanCompleteTime: Long
        get() = stateData.lastScanCompleteTime
        set(value) { stateData.lastScanCompleteTime = value }

    /** Round-robin cursor so every eligible job gets a turn instead of the first one starving the rest. */
    var jobCursor: Int = 0

    val scanner = AreaScanner(pasturePos, world, this)

    fun addTarget(jobType: JobType, pos: BlockPos) {
        cache.getOrPut(jobType) { ConcurrentHashMap.newKeySet() }.add(pos)
    }

    fun clearBlockTargets() {
        cache.clear()
    }
}
