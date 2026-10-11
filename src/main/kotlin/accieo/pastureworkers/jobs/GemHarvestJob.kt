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
import com.cobblemon.mod.common.block.TypeGemClusterBlock
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.item.ItemStack
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.tag.TagKey
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

object GemHarvestJob : WorkJob {

    override val jobType = JobType.GEM_HARVEST
    override val relevantStat = Stats.ATTACK
    override val needsBlockTargets = true

    private val TYPE_GEM_CLUSTERS_TAG =
        TagKey.of(RegistryKeys.BLOCK, Identifier.of("cobblemon", "type_gem_clusters"))

    // Cobblemon grows a gem cluster through stages 0..3 inside the same block; stage 3 is mature.
    private val MAX_STAGE = TypeGemClusterBlock.STAGE.values.maxOrNull() ?: 3

    override fun matches(state: BlockState, world: World, pos: BlockPos): Boolean {
        return state.isIn(TYPE_GEM_CLUSTERS_TAG) &&
                state.get(TypeGemClusterBlock.STAGE) == MAX_STAGE
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
