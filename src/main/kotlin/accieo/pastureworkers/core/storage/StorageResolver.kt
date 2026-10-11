/*
 * Copyright (c) 2026 Accieo
 * Copyright (c) 2026 Th3Sem
 *
 * SPDX-License-Identifier: MPL-2.0
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package accieo.pastureworkers.core.storage

import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant
import net.fabricmc.fabric.api.transfer.v1.storage.Storage
import net.minecraft.block.InventoryProvider
import net.minecraft.inventory.Inventory
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.world.World

/**
 * Resolves a world position to an authoritative [StorageTarget].
 *
 * Detection order: (1) the Fabric Transfer API capability (a superset: it auto-wraps vanilla
 * chests/barrels/hoppers and any [Inventory]/[InventoryProvider], and modded storage such as
 * Sophisticated Storage / Tom's Simple Storage / Storage Drawers register here); (2) a direct
 * block-entity [Inventory]; (3) a block/block-entity [InventoryProvider] (unsided access).
 * Every resolution is guarded so a throwing provider cannot abort a scan or a work tick.
 */
object StorageResolver {

    private val SIDES: Array<Direction?> = arrayOf(
        null,
        Direction.DOWN,
        Direction.UP,
        Direction.NORTH,
        Direction.SOUTH,
        Direction.WEST,
        Direction.EAST
    )

    fun resolve(world: World, pos: BlockPos): StorageTarget? {
        resolveSided(world, pos)?.let { return TransferApiTarget(pos, it) }

        val blockEntity = runCatching { world.getBlockEntity(pos) }.getOrNull()
        if (blockEntity is Inventory) return VanillaInventoryTarget(pos, blockEntity)

        val state = runCatching { world.getBlockState(pos) }.getOrNull() ?: return null
        val provider = runCatching {
            (blockEntity as? InventoryProvider) ?: (state.block as? InventoryProvider)
        }.getOrNull() ?: return null

        val inventory = runCatching { provider.getInventory(state, world, pos) }.getOrNull()
            ?: return null
        return VanillaInventoryTarget(pos, inventory)
    }

    fun isStorage(world: World, pos: BlockPos): Boolean = resolve(world, pos) != null

    /**
     * Finds the first distinct storage exposed by the position. Candidates are deduped by storage
     * identity so a double chest (whose dummy half redirects to the main storage through the
     * capability) resolves to a single logical target rather than two wrappers over the same store.
     */
    private fun resolveSided(world: World, pos: BlockPos): Storage<ItemVariant>? {
        val seen = HashSet<Int>()
        for (side in SIDES) {
            val storage = runCatching { ItemStorage.SIDED.find(world, pos, side) }.getOrNull()
                ?: continue
            if (seen.add(System.identityHashCode(storage))) return storage
        }
        return null
    }
}