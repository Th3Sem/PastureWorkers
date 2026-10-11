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
import com.cobblemon.mod.common.CobblemonBlocks
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.item.ItemStack
import net.minecraft.state.property.Properties
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

object TumblestoneHarvestJob : WorkJob {

    override val jobType = JobType.TUMBLESTONE_HARVEST
    override val relevantStat = Stats.ATTACK
    override val needsBlockTargets = true

    private val VALID_CLUSTERS = setOf(
        CobblemonBlocks.TUMBLESTONE_CLUSTER,
        CobblemonBlocks.BLACK_TUMBLESTONE_CLUSTER,
        CobblemonBlocks.SKY_TUMBLESTONE_CLUSTER
    )

    private val REPLACEMENT_BLOCKS = mapOf(
        CobblemonBlocks.TUMBLESTONE_CLUSTER to CobblemonBlocks.SMALL_BUDDING_TUMBLESTONE,
        CobblemonBlocks.BLACK_TUMBLESTONE_CLUSTER to CobblemonBlocks.SMALL_BUDDING_BLACK_TUMBLESTONE,
        CobblemonBlocks.SKY_TUMBLESTONE_CLUSTER to CobblemonBlocks.SMALL_BUDDING_SKY_TUMBLESTONE
    )

    override fun matches(state: net.minecraft.block.BlockState, world: World, pos: BlockPos): Boolean {
        return state.block in VALID_CLUSTERS
    }

    override fun perform(ctx: WorkContext, targets: MutableSet<BlockPos>): List<ItemStack> {
        val pos = targets.firstOrNull() ?: return emptyList()
        targets.remove(pos)

        val state = ctx.world.getBlockState(pos)
        if (!matches(state, ctx.world, pos)) return emptyList()

        val drops = HarvestDrops.getDrops(ctx.world, pos, state, ctx.pokemonEntity)

        // ponytail: tumblestone is always replanted with the matching small budding block.
        // To make this toggleable later, add shouldReplant to the HoneyJobGroup/TumblestoneGroup config.
        val replacementBlock = REPLACEMENT_BLOCKS[state.block] ?: Blocks.AIR
        var replacementState = replacementBlock.defaultState

        val facing = Properties.FACING
        if (replacementState.properties.contains(facing) && state.contains(facing)) {
            replacementState = replacementState.with(facing, state.get(facing))
        }

        ctx.world.setBlockState(pos, replacementState, Block.NOTIFY_LISTENERS)
        return drops
    }
}
