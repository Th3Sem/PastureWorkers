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

import net.minecraft.block.BarrelBlock
import net.minecraft.block.Block
import net.minecraft.block.ChestBlock
import net.minecraft.block.entity.BarrelBlockEntity
import net.minecraft.entity.ItemEntity
import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemStack
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

object DepositUtils {

    private val validInventoryBlocks: Set<Block> = setOf(
        net.minecraft.block.Blocks.CHEST,
        net.minecraft.block.Blocks.TRAPPED_CHEST,
        net.minecraft.block.Blocks.BARREL
    )

    private fun isInventory(world: World, pos: BlockPos): Boolean {
        val state = world.getBlockState(pos)
        return state.block in validInventoryBlocks
    }

    fun insertOrDrop(world: World, pasturePos: BlockPos, chests: Set<BlockPos>, drops: List<ItemStack>) {
        if (drops.isEmpty()) return

        val remaining = drops.map { it.copy() }.toMutableList()
        val sortedChests = chests
            .filter { isInventory(world, it) }
            .sortedBy { it.getSquaredDistance(pasturePos) }

        for (chestPos in sortedChests) {
            val inventory = getInventory(world, chestPos) ?: continue
            remaining.removeAll { it.isEmpty }
            val newRemaining = insertStacks(inventory, remaining)
            remaining.clear()
            remaining.addAll(newRemaining)
            if (remaining.isEmpty()) return
        }

        // ponytail: dropping residue at the pasture block instead of per-Pokémon held items.
        // Ceiling: items may pile up if no chest is reachable; add voiding/remote storage if it matters.
        remaining.filter { !it.isEmpty }.forEach { stack ->
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

    private fun getInventory(world: World, pos: BlockPos): Inventory? {
        val state = world.getBlockState(pos)
        val block = state.block
        return when {
            block is ChestBlock -> ChestBlock.getInventory(block, state, world, pos, true)
            block is BarrelBlock -> world.getBlockEntity(pos) as? BarrelBlockEntity
            else -> world.getBlockEntity(pos) as? Inventory
        }
    }

    fun insertStacks(inventory: Inventory, stacks: List<ItemStack>): List<ItemStack> {
        val remaining = mutableListOf<ItemStack>()
        for (stack in stacks) {
            val leftover = insertStack(inventory, stack.copy())
            if (!leftover.isEmpty) remaining.add(leftover)
        }
        return remaining
    }

    fun insertStack(inventory: Inventory, stack: ItemStack): ItemStack {
        if (stack.isEmpty) return ItemStack.EMPTY
        var remaining = stack.copy()
        remaining = fillExistingStacks(inventory, remaining)
        if (!remaining.isEmpty) {
            remaining = fillEmptySlots(inventory, remaining)
        }
        return remaining
    }

    private fun fillExistingStacks(inventory: Inventory, stack: ItemStack): ItemStack {
        for (i in 0 until inventory.size()) {
            val invStack = inventory.getStack(i)
            if (invStack.isEmpty || !ItemStack.areItemsAndComponentsEqual(invStack, stack)) continue

            val space = invStack.maxCount - invStack.count
            if (space > 0) {
                val transfer = minOf(stack.count, space)
                invStack.increment(transfer)
                stack.decrement(transfer)
                inventory.markDirty()
            }
            if (stack.isEmpty) return ItemStack.EMPTY
        }
        return stack
    }

    private fun fillEmptySlots(inventory: Inventory, stack: ItemStack): ItemStack {
        for (i in 0 until inventory.size()) {
            if (inventory.getStack(i).isEmpty) {
                val transfer = minOf(stack.count, stack.maxCount)
                inventory.setStack(i, stack.split(transfer))
                inventory.markDirty()
            }
            if (stack.isEmpty) return ItemStack.EMPTY
        }
        return stack
    }
}
