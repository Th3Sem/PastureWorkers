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

import net.minecraft.datafixer.DataFixTypes
import net.minecraft.nbt.NbtCompound
import net.minecraft.registry.RegistryWrapper
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.world.PersistentState
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.function.BiFunction
import java.util.function.Supplier

class PastureWorkersState : PersistentState() {

    data class PastureData(
        val nextWorkTimes: MutableMap<UUID, Long> = ConcurrentHashMap(),
        var lastScanCompleteTime: Long = 0L
    )

    val pastureData: MutableMap<BlockPos, PastureData> = ConcurrentHashMap()

    private var lastDirtyTime: Long = Long.MIN_VALUE

    fun getData(pos: BlockPos): PastureData = pastureData.getOrPut(pos.toImmutable()) { PastureData() }

    fun removePasture(pos: BlockPos) {
        if (pastureData.remove(pos.toImmutable()) != null) {
            markDirty()
        }
    }

    fun requestSave(currentTime: Long) {
        if (currentTime - lastDirtyTime >= SAVE_INTERVAL) {
            lastDirtyTime = currentTime
            markDirty()
        }
    }

    override fun writeNbt(nbt: NbtCompound, registryLookup: RegistryWrapper.WrapperLookup): NbtCompound {
        val pastures = NbtCompound()
        pastureData.forEach { (pos, data) ->
            val entry = NbtCompound()
            data.nextWorkTimes.forEach { (uuid, time) -> entry.putLong(uuid.toString(), time) }
            entry.putLong("lastScanCompleteTime", data.lastScanCompleteTime)
            pastures.put(pos.asLong().toString(), entry)
        }
        nbt.put("pastures", pastures)
        return nbt
    }

    companion object {
        private const val SAVE_INTERVAL = 200L

        private val TYPE = PersistentState.Type(
            Supplier { PastureWorkersState() },
            BiFunction<NbtCompound, RegistryWrapper.WrapperLookup, PastureWorkersState> { nbt, _ -> readNbt(nbt) },
            DataFixTypes.SAVED_DATA_MAP_DATA
        )

        fun get(serverWorld: ServerWorld): PastureWorkersState {
            return serverWorld.persistentStateManager.getOrCreate(TYPE, "pastureworkers")
        }

        private fun readNbt(nbt: NbtCompound): PastureWorkersState {
            val state = PastureWorkersState()
            if (!nbt.contains("pastures")) return state

            val pastures = nbt.getCompound("pastures")
            for (posKey in pastures.keys) {
                val pos = posKey.toLongOrNull()?.let(BlockPos::fromLong) ?: continue
                val entry = pastures.getCompound(posKey)
                val times = ConcurrentHashMap<UUID, Long>()
                for (uuidKey in entry.keys) {
                    if (uuidKey == "lastScanCompleteTime") continue
                    val uuid = runCatching { UUID.fromString(uuidKey) }.getOrNull() ?: continue
                    times[uuid] = entry.getLong(uuidKey)
                }
                state.pastureData[pos.toImmutable()] = PastureData(
                    nextWorkTimes = times,
                    lastScanCompleteTime = entry.getLong("lastScanCompleteTime")
                )
            }
            return state
        }
    }
}
