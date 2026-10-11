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
import accieo.pastureworkers.integration.FarmersDelightBlocks
import com.cobblemon.mod.common.CobblemonBlocks
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.block.HeartyGrainsBlock
import com.cobblemon.mod.common.block.MedicinalLeekBlock
import com.cobblemon.mod.common.block.NutBushBlock
import com.cobblemon.mod.common.block.RevivalHerbBlock
import net.minecraft.block.BeetrootsBlock
import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.block.CarrotsBlock
import net.minecraft.block.CaveVines
import net.minecraft.block.CaveVinesBodyBlock
import net.minecraft.block.CropBlock
import net.minecraft.block.PotatoesBlock
import net.minecraft.block.SweetBerryBushBlock
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.state.property.Properties
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

object CropHarvestJob : WorkJob {

    override val jobType = JobType.CROP_HARVEST
    override val relevantStat = Stats.ATTACK
    override val needsBlockTargets = true

    private val validCropBlocks by lazy {
        mutableSetOf(
            Blocks.POTATOES,
            Blocks.BEETROOTS,
            Blocks.CARROTS,
            Blocks.WHEAT,
            Blocks.SWEET_BERRY_BUSH,
            Blocks.CAVE_VINES,
            Blocks.CAVE_VINES_PLANT,
            CobblemonBlocks.REVIVAL_HERB,
            CobblemonBlocks.MEDICINAL_LEEK,
            CobblemonBlocks.VIVICHOKE_SEEDS,
            CobblemonBlocks.HEARTY_GRAINS,
            CobblemonBlocks.GALARICA_NUT_BUSH
        ).also { set ->
            FarmersDelightBlocks.ALL.forEach { id ->
                Registries.BLOCK.getOrEmpty(Identifier.of("farmersdelight", id)).ifPresent { set.add(it) }
            }
        }
    }

    override fun matches(state: net.minecraft.block.BlockState, world: World, pos: BlockPos): Boolean {
        return state.block in validCropBlocks && isMature(world, pos, state)
    }

    override fun perform(ctx: WorkContext, targets: MutableSet<BlockPos>): List<ItemStack> {
        val pos = targets.firstOrNull() ?: return emptyList()
        targets.remove(pos)

        val state = ctx.world.getBlockState(pos)
        if (!matches(state, ctx.world, pos)) return emptyList()

        val drops = HarvestDrops.getDrops(ctx.world, pos, state, ctx.pokemonEntity)
        replant(ctx.world, pos, state)
        return drops
    }

    private fun isMature(world: World, pos: BlockPos, state: net.minecraft.block.BlockState): Boolean {
        val block = state.block
        val blockId = Registries.BLOCK.getId(block).path

        return when {
            block is HeartyGrainsBlock -> block.getAge(state) == HeartyGrainsBlock.MATURE_AGE
            block is CropBlock -> block.getAge(state) == block.maxAge
            block is CaveVines -> state.get(CaveVinesBodyBlock.BERRIES)
            block is SweetBerryBushBlock -> state.get(SweetBerryBushBlock.AGE) == SweetBerryBushBlock.MAX_AGE
            block is NutBushBlock -> state.get(NutBushBlock.AGE) == NutBushBlock.MAX_AGE
            blockId in FarmersDelightBlocks.MUSHROOMS && state.contains(Properties.AGE_3) -> state.get(Properties.AGE_3) == 3
            else -> false
        }
    }

    private fun replant(world: World, pos: BlockPos, state: net.minecraft.block.BlockState) {
        val block = state.block
        val blockId = Registries.BLOCK.getId(block).path

        val isFarmersDelightCrop = blockId in FarmersDelightBlocks.ALL
        val isTomato = blockId == FarmersDelightBlocks.TOMATOES
        val isRice = blockId == FarmersDelightBlocks.RICE_PANICLES
        val isMushroomColony = blockId in FarmersDelightBlocks.MUSHROOMS

        if (block == CobblemonBlocks.HEARTY_GRAINS) {
            val belowPos = pos.down()
            val belowState = world.getBlockState(belowPos)
            if (belowState.block == CobblemonBlocks.HEARTY_GRAINS) {
                world.setBlockState(pos, Blocks.AIR.defaultState, Block.NOTIFY_LISTENERS)
            }
            return
        }

        val newState = when {
            isRice && state.contains(Properties.AGE_3) -> Blocks.AIR.defaultState
            isTomato && state.contains(Properties.AGE_3) -> state.with(Properties.AGE_3, 0)
            isMushroomColony && state.contains(Properties.AGE_3) -> state.with(Properties.AGE_3, 0)
            isFarmersDelightCrop && state.contains(CropBlock.AGE) -> state.with(CropBlock.AGE, 0)
            block == Blocks.POTATOES -> state.with(PotatoesBlock.AGE, 0)
            block == Blocks.BEETROOTS -> state.with(BeetrootsBlock.AGE, 0)
            block == Blocks.CARROTS -> state.with(CarrotsBlock.AGE, 0)
            block == Blocks.WHEAT -> state.with(CropBlock.AGE, 0)
            block == Blocks.SWEET_BERRY_BUSH -> state.with(SweetBerryBushBlock.AGE, 1)
            block == Blocks.CAVE_VINES -> state.with(CaveVinesBodyBlock.BERRIES, false)
            block == Blocks.CAVE_VINES_PLANT -> state.with(CaveVinesBodyBlock.BERRIES, false)
            block == CobblemonBlocks.REVIVAL_HERB -> state.with(RevivalHerbBlock.AGE, RevivalHerbBlock.MIN_AGE)
            block == CobblemonBlocks.MEDICINAL_LEEK -> state.with(MedicinalLeekBlock.AGE, 0)
            block == CobblemonBlocks.VIVICHOKE_SEEDS -> state.with(CropBlock.AGE, 0)
            block == CobblemonBlocks.GALARICA_NUT_BUSH -> state.with(NutBushBlock.AGE, 1)
            else -> Blocks.AIR.defaultState
        }

        world.setBlockState(pos, newState, Block.NOTIFY_LISTENERS)
    }
}
