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
import accieo.pastureworkers.core.storage.StorageDeposit
import accieo.pastureworkers.core.storage.StorageResolver
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import net.minecraft.block.BeehiveBlock
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.tag.BlockTags
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

/**
 * Thematic virtual honey production. Beehives/nests and flowers act as persistent catalysts: while a
 * hive is present the worker produces honeycomb, while a flower is present it produces honey bottles.
 * The job never consumes its catalysts (except the optional [PastureWorkersConfig.HoneyJobGroup.consumeFullHive]
 * bonus), so the manager's work interval is the only cooldown.
 */
object HoneyJob : WorkJob {

    override val jobType = JobType.HONEY_COLLECT
    override val relevantStat = Stats.SPEED
    override val needsBlockTargets = true

    override fun matches(state: BlockState, world: World, pos: BlockPos): Boolean {
        return state.block is BeehiveBlock || state.isIn(BlockTags.FLOWERS)
    }

    override fun perform(ctx: WorkContext, targets: MutableSet<BlockPos>): List<ItemStack> {
        val config = PastureWorkersConfig.config.honey

        var hive = false
        var flower = false
        var fullHivePos: BlockPos? = null

        // Catalysts are persistent: they are inspected, never removed.
        for (pos in targets) {
            val state = ctx.world.getBlockState(pos)
            if (state.block is BeehiveBlock) {
                hive = true
                if (config.consumeFullHive &&
                    state.get(BeehiveBlock.HONEY_LEVEL) == BeehiveBlock.FULL_HONEY_LEVEL
                ) {
                    fullHivePos = pos
                }
            } else if (state.isIn(BlockTags.FLOWERS)) {
                flower = true
            }
        }

        if (!hive && !flower) return emptyList()

        val drops = mutableListOf<ItemStack>()

        if (hive && config.honeycombPerHarvest > 0) {
            drops.add(ItemStack(Items.HONEYCOMB, config.honeycombPerHarvest))
        }

        if (flower && config.honeyBottlesPerHarvest > 0) {
            val hasBottle = !config.requireEmptyBottle || run {
                val targets = ctx.chests.mapNotNull { StorageResolver.resolve(ctx.world, it) }
                StorageDeposit.withdraw(ctx.world, targets, Items.GLASS_BOTTLE, 1) > 0
            }
            if (hasBottle) {
                drops.add(ItemStack(Items.HONEY_BOTTLE, config.honeyBottlesPerHarvest))
            }
        }

        // The only hive-mutating path, and opt-in: drain a full hive for one bonus honeycomb.
        if (fullHivePos != null) {
            val state = ctx.world.getBlockState(fullHivePos)
            if (state.block is BeehiveBlock &&
                state.get(BeehiveBlock.HONEY_LEVEL) == BeehiveBlock.FULL_HONEY_LEVEL
            ) {
                ctx.world.setBlockState(
                    fullHivePos,
                    state.with(BeehiveBlock.HONEY_LEVEL, 0),
                    Block.NOTIFY_LISTENERS
                )
                drops.add(ItemStack(Items.HONEYCOMB, 1))
            }
        }

        return drops
    }
}