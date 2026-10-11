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
import net.minecraft.item.ItemStack
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

object AmethystHarvestJob : WorkJob {

    override val jobType = JobType.AMETHYST_HARVEST
    override val relevantStat = Stats.ATTACK
    override val needsBlockTargets = true

    override fun matches(state: net.minecraft.block.BlockState, world: World, pos: BlockPos): Boolean {
        // The vanilla amethyst cluster *is* the largest growth stage: small/medium/large buds are
        // separate blocks, and the cluster exposes no stage property. Absent a stage property the
        // cluster is always mature, so matching the block is sufficient.
        return state.block == Blocks.AMETHYST_CLUSTER
    }

    override fun perform(ctx: WorkContext, targets: MutableSet<BlockPos>): List<ItemStack> {
        val pos = targets.firstOrNull() ?: return emptyList()
        targets.remove(pos)

        val state = ctx.world.getBlockState(pos)
        if (!matches(state, ctx.world, pos)) return emptyList()

        val drops = HarvestDrops.getDrops(ctx.world, pos, state, ctx.pokemonEntity)
        ctx.world.setBlockState(pos, Blocks.AIR.defaultState, Block.NOTIFY_LISTENERS)
        return drops
    }
}
