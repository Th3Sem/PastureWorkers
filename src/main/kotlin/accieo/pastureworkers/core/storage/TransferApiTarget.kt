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

import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant
import net.fabricmc.fabric.api.transfer.v1.storage.Storage
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.util.math.BlockPos

/**
 * [StorageTarget] backed by the Fabric Transfer API. All mutations go through an outer transaction
 * that is committed before the change is considered durable; no state changes if commit is skipped.
 */
class TransferApiTarget(
    override val pos: BlockPos,
    private val storage: Storage<ItemVariant>
) : StorageTarget {

    override fun snapshot(): List<ItemStack> {
        val result = ArrayList<ItemStack>()
        for (view in storage.nonEmptyViews()) {
            if (view.isResourceBlank) continue
            var remaining = view.amount
            if (remaining <= 0) continue
            val proto = view.resource.toStack(1)
            if (proto.isEmpty) continue
            val per = proto.maxCount
            while (remaining > 0) {
                val take = minOf(remaining, per.toLong()).toInt()
                result.add(proto.copyWithCount(take))
                remaining -= take
            }
        }
        return result
    }

    override fun insert(stack: ItemStack): ItemStack {
        if (stack.isEmpty) return ItemStack.EMPTY
        val variant = ItemVariant.of(stack)
        val inserted = Transaction.openOuter().use { tx ->
            val accepted = storage.insert(variant, stack.count.toLong(), tx)
            tx.commit()
            accepted
        }
        val remaining = (stack.count - inserted).toInt().coerceAtLeast(0)
        return if (remaining <= 0) ItemStack.EMPTY else stack.copyWithCount(remaining)
    }

    override fun extract(item: Item, count: Int): Int {
        if (count <= 0) return 0
        val variant = ItemVariant.of(item)
        return Transaction.openOuter().use { tx ->
            val extracted = storage.extract(variant, count.toLong(), tx)
            tx.commit()
            extracted.toInt()
        }
    }
}