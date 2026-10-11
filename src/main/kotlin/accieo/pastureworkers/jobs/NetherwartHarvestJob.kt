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
import net.minecraft.block.NetherWartBlock
import net.minecraft.item.ItemStack
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

object NetherwartHarvestJob : WorkJob {

    override val jobType = JobType.NETHERWART_HARVEST
    override val relevantStat = Stats.ATTACK
    override val needsBlockTargets = true

    override fun matches(state: net.minecraft.block.BlockState, world: World, pos: BlockPos): Boolean {
        return state.block is NetherWartBlock && state.get(NetherWartBlock.AGE) == NetherWartBlock.MAX_AGE
    }

    override fun perform(ctx: WorkContext, targets: MutableSet<BlockPos>): List<ItemStack> {
        val pos = targets.firstOrNull() ?: return emptyList()
        targets.remove(pos)

        val state = ctx.world.getBlockState(pos)
        if (!matches(state, ctx.world, pos)) return emptyList()

        val drops = HarvestDrops.getDrops(ctx.world, pos, state, ctx.pokemonEntity)
        ctx.world.setBlockState(pos, state.with(NetherWartBlock.AGE, 0), Block.NOTIFY_LISTENERS)
        return drops
    }
}
