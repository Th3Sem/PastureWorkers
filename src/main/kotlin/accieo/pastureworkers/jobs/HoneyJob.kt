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
import accieo.pastureworkers.core.LocationKey
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import net.minecraft.block.BeehiveBlock
import net.minecraft.block.Block
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import java.util.concurrent.ConcurrentHashMap

object HoneyJob : WorkJob {

    override val jobType = JobType.HONEY_COLLECT
    override val relevantStat = Stats.SPEED
    override val needsBlockTargets = true

    private val lastGenerationTime = ConcurrentHashMap<LocationKey, Long>()

    override fun matches(state: net.minecraft.block.BlockState, world: World, pos: BlockPos): Boolean {
        if (state.block !is BeehiveBlock) return false
        val level = state.get(BeehiveBlock.HONEY_LEVEL)
        return level == BeehiveBlock.FULL_HONEY_LEVEL ||
                (PastureWorkersConfig.config.honey.generateHoney && level < BeehiveBlock.FULL_HONEY_LEVEL)
    }

    override fun perform(ctx: WorkContext, targets: MutableSet<BlockPos>): List<ItemStack> {
        val pos = targets.firstOrNull() ?: return emptyList()

        val state = ctx.world.getBlockState(pos)
        if (!matches(state, ctx.world, pos)) {
            targets.remove(pos)
            return emptyList()
        }

        val level = state.get(BeehiveBlock.HONEY_LEVEL)
        if (level == BeehiveBlock.FULL_HONEY_LEVEL) {
            targets.remove(pos)
            ctx.world.setBlockState(pos, state.with(BeehiveBlock.HONEY_LEVEL, 0), Block.NOTIFY_LISTENERS)
            return listOf(ItemStack(Items.HONEYCOMB, 3))
        }

        if (!PastureWorkersConfig.config.honey.generateHoney) {
            targets.remove(pos)
            return emptyList()
        }

        val cooldownTicks = PastureWorkersConfig.config.honey.honeyGenerationCooldownSeconds * 20L
        val key = LocationKey(ctx.world.registryKey, pos)
        val lastTime = lastGenerationTime[key] ?: 0L
        if (ctx.world.time - lastTime < cooldownTicks) {
            return emptyList()
        }

        lastGenerationTime[key] = ctx.world.time
        ctx.world.setBlockState(pos, state.with(BeehiveBlock.HONEY_LEVEL, level + 1), Block.NOTIFY_LISTENERS)
        return emptyList()
    }
}
