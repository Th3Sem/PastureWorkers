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
import net.minecraft.block.BarrelBlock
import net.minecraft.block.ChestBlock
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

    private var currentX = 0
    private var currentY = 0
    private var currentZ = 0
    private var scanning = false

    fun step(currentTime: Long) {
        if (!shouldScan(currentTime)) return

        if (!scanning) {
            val general = PastureWorkersConfig.config.general
            recomputeBounds(general)
            session.clearBlockTargets()
            session.chests.clear()
            scanning = true
            currentX = minX
            currentY = minY
            currentZ = minZ
        }

        var budget = PastureWorkersConfig.config.general.blocksPerScanTick.coerceAtLeast(1)
        while (budget-- > 0) {
            val pos = BlockPos(currentX, currentY, currentZ)
            scanBlock(pos)
            if (!advance()) {
                finishScan(currentTime)
                return
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

    private fun scanBlock(pos: BlockPos) {
        if (!world.isChunkLoaded(pos)) return

        val state = world.getBlockState(pos)
        val block = state.block

        if (block is ChestBlock || block is BarrelBlock) {
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
}
