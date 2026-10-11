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

import com.cobblemon.mod.common.api.pokemon.stats.Stat
import net.minecraft.block.BlockState
import net.minecraft.util.math.BlockPos
import net.minecraft.item.ItemStack
import net.minecraft.world.World

interface WorkJob {
    val jobType: JobType
    val relevantStat: Stat
    val needsBlockTargets: Boolean

    fun matches(state: BlockState, world: World, pos: BlockPos): Boolean
    fun perform(ctx: WorkContext, targets: MutableSet<BlockPos>): List<ItemStack>
}
