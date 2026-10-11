/*
 * Copyright (c) 2026 Accieo
 * Copyright (c) 2026 Th3Sem
 *
 * SPDX-License-Identifier: MPL-2.0
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package accieo.pastureworkers.jobs

import accieo.pastureworkers.api.JobType
import accieo.pastureworkers.api.WorkContext
import accieo.pastureworkers.api.WorkJob
import accieo.pastureworkers.config.PastureWorkersConfig
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import net.minecraft.entity.ItemEntity
import net.minecraft.item.ItemStack
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d

object GroundItemGatherJob : WorkJob {

    override val jobType = JobType.GROUND_ITEM_GATHER
    override val relevantStat = Stats.SPEED
    override val needsBlockTargets = false

    private const val CAP = 20
    private const val Y_RANGE = 2.0

    override fun matches(state: net.minecraft.block.BlockState, world: net.minecraft.world.World, pos: net.minecraft.util.math.BlockPos): Boolean {
        return false
    }

    override fun perform(ctx: WorkContext, targets: MutableSet<net.minecraft.util.math.BlockPos>): List<ItemStack> {
        val radius = PastureWorkersConfig.config.general.searchRadius.toDouble()
        val box = Box.of(Vec3d.ofCenter(ctx.pasturePos), radius, Y_RANGE, radius)
        val items = ctx.world.getEntitiesByClass(ItemEntity::class.java, box) { true }
            // ponytail: ItemEntity.pickupDelay has no public getter in Yarn 1.21.1.
            // `cannotPickup()` covers the "never pickable" case; normal delays (<=10 ticks) are
            // irrelevant at the manager's pace (>=20 ticks), so we treat them as pickable.
            .filter { !it.cannotPickup() }
            .take(CAP)

        val drops = mutableListOf<ItemStack>()
        for (item in items) {
            drops.add(item.stack.copy())
            item.discard()
        }
        return drops
    }
}
