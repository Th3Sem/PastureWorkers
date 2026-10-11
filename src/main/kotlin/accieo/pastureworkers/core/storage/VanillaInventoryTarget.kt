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

import net.minecraft.inventory.Inventory
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.util.math.BlockPos

/**
 * [StorageTarget] backed by a plain vanilla [Inventory]. Mutations are written back with
 * [Inventory.setStack] (never by mutating the object returned from [Inventory.getStack]) so the
 * change actually persists; this is the fix for the phantom in-place write.
 */
class VanillaInventoryTarget(
    override val pos: BlockPos,
    private val inventory: Inventory
) : StorageTarget {

    override fun snapshot(): List<ItemStack> {
        val result = ArrayList<ItemStack>()
        for (i in 0 until inventory.size()) {
            val stack = inventory.getStack(i)
            if (!stack.isEmpty) result.add(stack.copy())
        }
        return result
    }

    override fun insert(stack: ItemStack): ItemStack {
        if (stack.isEmpty) return ItemStack.EMPTY
        var remaining = stack.copy()
        remaining = fillExisting(remaining)
        if (!remaining.isEmpty) remaining = fillEmptySlots(remaining)
        return remaining
    }

    override fun extract(item: Item, count: Int): Int {
        if (count <= 0) return 0
        var remaining = count
        var extracted = 0
        for (i in 0 until inventory.size()) {
            if (remaining <= 0) break
            val current = inventory.getStack(i)
            if (current.isEmpty || !current.isOf(item)) continue
            val take = minOf(remaining, current.count)
            val left = current.count - take
            inventory.setStack(i, if (left <= 0) ItemStack.EMPTY else current.copyWithCount(left))
            inventory.markDirty()
            remaining -= take
            extracted += take
        }
        return extracted
    }

    private fun fillExisting(stack: ItemStack): ItemStack {
        val slotCap = inventory.getMaxCountPerStack()
        var remaining = stack
        for (i in 0 until inventory.size()) {
            if (remaining.isEmpty) break
            val current = inventory.getStack(i)
            if (current.isEmpty || !ItemStack.areItemsAndComponentsEqual(current, remaining)) continue
            if (!inventory.isValid(i, remaining)) continue
            val perSlot = minOf(current.maxCount, slotCap)
            val space = perSlot - current.count
            if (space <= 0) continue
            val transfer = minOf(remaining.count, space)
            inventory.setStack(i, current.copyWithCount(current.count + transfer))
            inventory.markDirty()
            remaining = remaining.copyWithCount(remaining.count - transfer)
        }
        return remaining
    }

    private fun fillEmptySlots(stack: ItemStack): ItemStack {
        val slotCap = inventory.getMaxCountPerStack()
        var remaining = stack
        for (i in 0 until inventory.size()) {
            if (remaining.isEmpty) break
            if (!inventory.getStack(i).isEmpty) continue
            if (!inventory.isValid(i, remaining)) continue
            val perSlot = minOf(remaining.maxCount, slotCap)
            val transfer = minOf(remaining.count, perSlot)
            if (transfer <= 0) continue
            inventory.setStack(i, remaining.copyWithCount(transfer))
            inventory.markDirty()
            remaining = remaining.copyWithCount(remaining.count - transfer)
        }
        return remaining
    }
}