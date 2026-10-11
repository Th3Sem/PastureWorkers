/*
 * Copyright (c) 2026 Accieo
 * Copyright (c) 2026 Th3Sem
 *
 * SPDX-License-Identifier: MPL-2.0
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package accieo.pastureworkers.core

import net.minecraft.entity.ItemEntity
import net.minecraft.item.ItemStack
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

object DepositUtils {

    /**
     * Last-resort fallback: drops the returned remainder at the pasture block so nothing vanishes
     * when no storage target accepted it.
     */
    // ponytail: residue lands at the pasture instead of per-Pokémon held items. Ceiling: items may
    // pile up if no storage is reachable; add voiding/remote storage if it matters.
    fun dropAt(world: World, pasturePos: BlockPos, stacks: List<ItemStack>) {
        if (stacks.isEmpty()) return
        stacks.filter { !it.isEmpty }.forEach { stack ->
            val entity = ItemEntity(
                world,
                pasturePos.x + 0.5,
                pasturePos.y + 0.5,
                pasturePos.z + 0.5,
                stack
            )
            entity.setToDefaultPickupDelay()
            world.spawnEntity(entity)
        }
    }
}