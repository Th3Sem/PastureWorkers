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

import accieo.pastureworkers.PastureWorkers
import accieo.pastureworkers.api.JobType
import accieo.pastureworkers.api.WorkContext
import accieo.pastureworkers.config.PastureWorkersConfig
import accieo.pastureworkers.core.storage.StorageDeposit
import accieo.pastureworkers.core.storage.StorageResolver
import accieo.pastureworkers.core.work.JobSelection
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.item.ItemStack
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.random.Random
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object PastureWorkManager {

    private const val ORPHAN_SWEEP_INTERVAL = 200L

    private val sessions = ConcurrentHashMap<LocationKey, PastureSession>()
    private val loggedJobFailures = ConcurrentHashMap.newKeySet<JobType>()
    private var lastSweepTime = Long.MIN_VALUE

    fun serverTick(world: ServerWorld, pos: BlockPos, blockEntity: PokemonPastureBlockEntity) {
        val state = PastureWorkersState.get(world)
        val key = LocationKey(world.registryKey, pos.toImmutable())
        val session = sessions.computeIfAbsent(key) {
            PastureSession(world, pos.toImmutable(), state.getData(pos.toImmutable()))
        }

        session.scanner.step(world.time)
        tickWorkers(world, pos, blockEntity, session, state)
        sweepOrphansIfDue(world.time)
    }

    fun session(world: ServerWorld, pos: BlockPos): PastureSession? =
        sessions[LocationKey(world.registryKey, pos.toImmutable())]

    fun removePasture(world: ServerWorld, pos: BlockPos) {
        val key = LocationKey(world.registryKey, pos.toImmutable())
        sessions.remove(key)?.also {
            PastureWorkersState.get(it.world).removePasture(it.pasturePos)
        }
    }

    private fun tickWorkers(
        world: ServerWorld,
        pasturePos: BlockPos,
        blockEntity: PokemonPastureBlockEntity,
        session: PastureSession,
        state: PastureWorkersState
    ) {
        val tethered = PastureWorkersAccess.tetheredPokemon(blockEntity)
        for (pokemon in tethered) {
            if (pokemon.isFainted()) continue

            val pokemonEntity = pokemon.entity ?: continue
            val poseType = pokemonEntity.getDataTracker().get(PokemonEntity.POSE_TYPE)
            if (poseType == PoseType.SLEEP) continue

            val uuid = pokemon.uuid
            val nextTime = session.nextWorkTimes[uuid] ?: 0L
            if (world.time < nextTime) continue

            val worked = tryWork(world, pasturePos, pokemonEntity, pokemon, session)
            if (worked) {
                state.requestSave(world.time)
            }
        }
    }

    private fun tryWork(
        world: ServerWorld,
        pasturePos: BlockPos,
        pokemonEntity: PokemonEntity,
        pokemon: Pokemon,
        session: PastureSession
    ): Boolean {
        val uuid = pokemon.uuid
        val random = world.random
        val general = PastureWorkersConfig.config.general

        // Collect every eligible job that currently has something to work on.
        val candidates = JobSelection.viableJobs(pokemonEntity, pokemon, session, world)

        if (candidates.isEmpty()) {
            session.nextWorkTimes[uuid] = world.time + general.workIntervalTicks / 4
            return false
        }

        // Deterministic round-robin so no single job starves the others.
        val start = session.jobCursor % candidates.size
        session.jobCursor = (session.jobCursor + 1) and Int.MAX_VALUE

        for (offset in candidates.indices) {
            val candidate = candidates[(start + offset) % candidates.size]
            val job = candidate.job
            val efficiency = EfficiencyCalculator.compute(pokemon, job.relevantStat)
            val interval = computeInterval(general.workIntervalTicks, efficiency)
            val actionPos = if (job.needsBlockTargets) candidate.targets.first() else pasturePos
            val ctx = WorkContext(world, pasturePos, pokemonEntity, pokemon, random, efficiency, session.chests)

            val drops = try {
                job.perform(ctx, candidate.targets)
            } catch (e: Exception) {
                // ponytail: defensive guard for block-property mismatches (e.g. a replant property
                // missing on a Cobblemon block). Clear this job's targets so we stop retrying the
                // bad blocks until the next scan; log only once per job type.
                session.cache[job.jobType]?.clear()
                if (loggedJobFailures.add(job.jobType)) {
                    PastureWorkers.LOGGER.error(
                        "[PastureWorkers] Job {} failed; skipping its targets until the next scan",
                        job.jobType,
                        e
                    )
                }
                continue
            }

            val finalDrops = applyBonusRoll(drops, efficiency, random)
            val targets = session.chests.mapNotNull { StorageResolver.resolve(world, it) }
            val remainder = StorageDeposit.deposit(world, pasturePos, targets, finalDrops)
            DepositUtils.dropAt(world, pasturePos, remainder)

            spawnWorkParticles(world, actionPos)
            session.nextWorkTimes[uuid] = world.time + interval
            return true
        }

        session.nextWorkTimes[uuid] = world.time + general.workIntervalTicks / 4
        return false
    }

    private fun sweepOrphansIfDue(currentTime: Long) {
        if (currentTime - lastSweepTime < ORPHAN_SWEEP_INTERVAL) return
        lastSweepTime = currentTime

        val iterator = sessions.entries.iterator()
        while (iterator.hasNext()) {
            val session = iterator.next().value
            val blockEntity = session.world.getBlockEntity(session.pasturePos)
            if (blockEntity !is PokemonPastureBlockEntity) {
                iterator.remove()
                PastureWorkersState.get(session.world).removePasture(session.pasturePos)
                continue
            }
            // Drop per-worker state for Pokemon no longer tethered here so a removed Pokemon does not
            // leak an entry forever.
            val tethered = PastureWorkersAccess.tetheredPokemon(blockEntity).mapTo(HashSet()) { it.uuid }
            session.nextWorkTimes.keys.retainAll(tethered)
        }
    }

    private fun computeInterval(baseTicks: Int, efficiency: Float): Long {
        val multiplier = 1f - efficiency * 0.8f
        return (baseTicks * multiplier).toLong().coerceAtLeast(20L)
    }

    private fun applyBonusRoll(drops: List<ItemStack>, efficiency: Float, random: Random): List<ItemStack> {
        if (drops.isEmpty() || random.nextFloat() >= efficiency) return drops
        val eligible = drops.filter { !it.isEmpty }
        if (eligible.isEmpty()) return drops
        val chosen = eligible[random.nextInt(eligible.size)]
        return drops + chosen.copy()
    }

    private fun spawnWorkParticles(world: ServerWorld, pos: BlockPos) {
        world.spawnParticles(
            ParticleTypes.HAPPY_VILLAGER,
            pos.x + 0.5,
            pos.y + 0.5,
            pos.z + 0.5,
            1,
            0.2,
            0.2,
            0.2,
            0.0
        )
    }
}
