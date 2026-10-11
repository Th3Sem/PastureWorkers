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

import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.util.math.BlockPos

/**
 * Provider-agnostic, authoritative view of a single storage position. Implementations must never
 * claim a mutation that did not durably persist: the caller verifies each operation with [snapshot].
 */
interface StorageTarget {
    val pos: BlockPos

    /** Insert as much as possible; return the EXACT remainder not durably accepted. */
    fun insert(stack: ItemStack): ItemStack

    /** Extract up to [count]; return the EXACT amount durably removed. */
    fun extract(item: Item, count: Int): Int

    /** Live contents, copied, for persistence verification. */
    fun snapshot(): List<ItemStack>
}