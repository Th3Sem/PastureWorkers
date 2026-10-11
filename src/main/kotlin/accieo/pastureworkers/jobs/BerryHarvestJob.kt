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
import com.cobblemon.mod.common.block.BerryBlock
import com.cobblemon.mod.common.block.entity.BerryBlockEntity
import net.minecraft.block.Block
import net.minecraft.item.ItemStack
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.tag.TagKey
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

object BerryHarvestJob : WorkJob {

    override val jobType = JobType.BERRY_HARVEST
    override val relevantStat = Stats.SPECIAL_ATTACK
    override val needsBlockTargets = true

    private val BERRIES_TAG = TagKey.of(RegistryKeys.BLOCK, Identifier.of("cobblemon", "berries"))

    override fun matches(state: net.minecraft.block.BlockState, world: World, pos: BlockPos): Boolean {
        return state.isIn(BERRIES_TAG) && state.get(BerryBlock.AGE) == BerryBlock.FRUIT_AGE
    }

    override fun perform(ctx: WorkContext, targets: MutableSet<BlockPos>): List<ItemStack> {
        val pos = targets.firstOrNull() ?: return emptyList()
        targets.remove(pos)

        val state = ctx.world.getBlockState(pos)
        if (!matches(state, ctx.world, pos)) return emptyList()

        val berryEntity = ctx.world.getBlockEntity(pos) as? BerryBlockEntity ?: return emptyList()
        val drops = berryEntity.harvest(ctx.world, state, pos, null).toList()
        ctx.world.setBlockState(pos, state.with(BerryBlock.AGE, BerryBlock.MATURE_AGE), Block.NOTIFY_LISTENERS)
        return drops
    }
}
