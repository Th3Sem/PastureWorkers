/*
 * Copyright (c) 2026 Accieo
 * Copyright (c) 2026 Th3Sem
 *
 * SPDX-License-Identifier: MPL-2.0
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package accieo.pastureworkers.api

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.random.Random

data class WorkContext(
    val world: ServerWorld,
    val pasturePos: BlockPos,
    val pokemonEntity: PokemonEntity,
    val pokemon: Pokemon,
    val random: Random,
    val efficiency: Float,
    /** Positions of the containers linked to this pasture, for jobs that need to withdraw items. */
    val chests: Set<BlockPos>
)
