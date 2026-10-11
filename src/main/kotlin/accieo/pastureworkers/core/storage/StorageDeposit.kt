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

import accieo.pastureworkers.PastureWorkers
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import java.util.concurrent.ConcurrentHashMap

/**
 * Provider-agnostic deposit/withdraw layer. Never trusts a stated acceptance: every operation is
 * verified against a before/after [StorageTarget.snapshot], so items can neither silently vanish nor
 * be double-counted. Any storage that claims acceptance but persists nothing is marked suspect and
 * skipped afterwards.
 */
object StorageDeposit {

    // ponytail: suspect state is process-global rather than per-session; a broken adapter stays
    // skipped for the server lifetime. Upgrade path = per-session sets if a hot-swapped adapter
    // needs to be retried.
    private val SUSPECT = ConcurrentHashMap.newKeySet<Long>()
    private val SUSPECT_LOGGED = ConcurrentHashMap.newKeySet<Long>()

    /** Deposits [drops] nearest-first; returns the exact remainder that no target accepted. */
    fun deposit(
        world: World,
        pasturePos: BlockPos,
        targets: List<StorageTarget>,
        drops: List<ItemStack>
    ): List<ItemStack> {
        if (drops.isEmpty()) return emptyList()
        var remaining = drops.filterNot { it.isEmpty }.map { it.copy() }.toMutableList()
        val sorted = targets.sortedBy { it.pos.getSquaredDistance(pasturePos) }

        for (target in sorted) {
            if (remaining.isEmpty()) break
            if (SUSPECT.contains(target.pos.asLong())) continue

            val before = runCatching { target.snapshot() }.getOrElse { continue }
            val original = remaining.map { it.copy() }
            val returned = try {
                remaining.map { target.insert(it) }.filterNot { it.isEmpty }
            } catch (e: Exception) {
                // A misbehaving target cannot abort the deposit: keep the whole remainder for the next.
                original.map { it.copy() }
            }

            val after = runCatching { target.snapshot() }.getOrElse { before }
            val inserted = netInserted(before, after)
            val claimed = stackCount(original) - stackCount(returned)

            // Trust only the verified snapshot delta, never the adapter's claim.
            remaining = subtract(original, inserted)

            if (claimed > 0 && inserted.isEmpty()) {
                SUSPECT.add(target.pos.asLong())
                if (SUSPECT_LOGGED.add(target.pos.asLong())) {
                    PastureWorkers.LOGGER.warn(
                        "[PastureWorkers] Storage at {} claimed {} item(s) but persisted none; skipping it",
                        target.pos.toShortString(),
                        claimed
                    )
                }
            }
        }

        return remaining.filterNot { it.isEmpty }
    }

    /** Withdraws up to [count] of [item]; returns only the amount verified as durably removed. */
    fun withdraw(world: World, targets: List<StorageTarget>, item: Item, count: Int): Int {
        if (count <= 0) return 0
        var remaining = count
        var extractedTotal = 0

        for (target in targets) {
            if (remaining <= 0) break
            val before = runCatching { target.snapshot() }.getOrElse { continue }
            try {
                target.extract(item, remaining)
            } catch (e: Exception) {
                continue
            }
            val after = runCatching { target.snapshot() }.getOrElse { before }
            val actual = maxOf(0, countOf(before, item) - countOf(after, item))
            if (actual > 0) {
                remaining -= actual
                extractedTotal += actual
            }
        }
        return extractedTotal
    }

    private fun stackCount(stacks: List<ItemStack>): Int {
        var total = 0
        for (stack in stacks) if (!stack.isEmpty) total += stack.count
        return total
    }

    private fun countOf(stacks: List<ItemStack>, item: Item): Int {
        var total = 0
        for (stack in stacks) if (!stack.isEmpty && stack.isOf(item)) total += stack.count
        return total
    }

    /** Multiset difference `after - before`, i.e. exactly what was durably inserted. */
    private fun netInserted(before: List<ItemStack>, after: List<ItemStack>): List<ItemStack> {
        val remainingBefore = before.map { it.copy() }.toMutableList()
        val result = ArrayList<ItemStack>()
        for (a in after) {
            if (a.isEmpty) continue
            var extra = a.count
            val iter = remainingBefore.listIterator()
            while (iter.hasNext() && extra > 0) {
                val b = iter.next()
                if (b.isEmpty || !ItemStack.areItemsAndComponentsEqual(b, a)) continue
                val consume = minOf(extra, b.count)
                b.decrement(consume)
                extra -= consume
                if (b.isEmpty) iter.remove()
            }
            if (extra > 0) result.add(a.copyWithCount(extra))
        }
        return result
    }

    /** Removes the [remove] multiset from [original], returning the leftover. */
    private fun subtract(original: List<ItemStack>, remove: List<ItemStack>): MutableList<ItemStack> {
        val toRemove = remove.map { it.copy() }.toMutableList()
        val result = ArrayList<ItemStack>()
        for (o in original) {
            var remaining = o.count
            val iter = toRemove.listIterator()
            while (iter.hasNext() && remaining > 0) {
                val r = iter.next()
                if (r.isEmpty || !ItemStack.areItemsAndComponentsEqual(r, o)) continue
                val take = minOf(remaining, r.count)
                remaining -= take
                r.decrement(take)
                if (r.isEmpty) iter.remove()
            }
            if (remaining > 0) result.add(o.copyWithCount(remaining))
        }
        return result
    }
}