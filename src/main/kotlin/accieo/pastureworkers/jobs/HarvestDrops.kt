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
        return Block.getDroppedStacks(state, world, pos, null, pokemonEntity, ItemStack.EMPTY)
    }
}
