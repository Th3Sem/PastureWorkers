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

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.item.ItemStack
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos

object HarvestDrops {

    fun getDrops(
        world: ServerWorld,
        pos: BlockPos,
        state: BlockState,
        pokemonEntity: PokemonEntity
    ): List<ItemStack> {
        // Pass the real block entity (as vanilla Block.dropStacks does) so loot that reads the block
        // entity (e.g. a modded harvestable added to a matching tag) resolves correctly. Blocks without
        // one yield null, which is the previous behaviour for the vanilla/Cobblemon harvests.
        return Block.getDroppedStacks(state, world, pos, world.getBlockEntity(pos), pokemonEntity, ItemStack.EMPTY)
    }
}
