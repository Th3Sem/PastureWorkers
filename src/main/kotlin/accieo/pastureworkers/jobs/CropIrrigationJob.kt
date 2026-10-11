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
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.block.FarmlandBlock
import net.minecraft.item.ItemStack
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

object CropIrrigationJob : WorkJob {

    override val jobType = JobType.CROP_IRRIGATION
    override val relevantStat = Stats.DEFENCE
    override val needsBlockTargets = true

    override fun matches(state: net.minecraft.block.BlockState, world: World, pos: BlockPos): Boolean {
        return state.block == Blocks.FARMLAND && state.get(FarmlandBlock.MOISTURE) < FarmlandBlock.MAX_MOISTURE
    }

    override fun perform(ctx: WorkContext, targets: MutableSet<BlockPos>): List<ItemStack> {
        val pos = targets.firstOrNull() ?: return emptyList()
        targets.remove(pos)

        val state = ctx.world.getBlockState(pos)
        if (!matches(state, ctx.world, pos)) return emptyList()

        ctx.world.setBlockState(
            pos,
            state.with(FarmlandBlock.MOISTURE, FarmlandBlock.MAX_MOISTURE),
            Block.NOTIFY_LISTENERS
        )
        return emptyList()
    }
}
