/*
 * Copyright (c) 2026 Accieo
 * Copyright (c) 2026 Th3Sem
 *
 * SPDX-License-Identifier: MPL-2.0
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package accieo.pastureworkers.core.work

import accieo.pastureworkers.api.JobRegistry
import accieo.pastureworkers.api.WorkJob
import accieo.pastureworkers.config.PastureWorkersConfig
import accieo.pastureworkers.core.Eligibility
import accieo.pastureworkers.core.PastureSession
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

/**
 * A job the given Pokemon is eligible for and that currently has work available, together with the
 * live block targets it should work on (empty for jobs that need no block targets).
 */
data class JobCandidate(val job: WorkJob, val targets: MutableSet<BlockPos>)

/**
 * Single source of truth for "which jobs can this Pokemon do right now". The manager, the GUI and the
 * commands all route through here so their views of a worker can never diverge.
 */
object JobSelection {

    /**
     * Eligible jobs that currently have work. [WorkJob.needsBlockTargets] jobs require a live-validated
     * non-empty target cache; jobs without block targets are always viable once eligible. Registry order
     * is preserved.
     */
    fun viableJobs(
        entity: PokemonEntity,
        pokemon: Pokemon,
        session: PastureSession?,
        world: World
    ): List<JobCandidate> {
        val candidates = ArrayList<JobCandidate>()
        for (job in JobRegistry.jobs) {
            val group = PastureWorkersConfig.groupFor(job.jobType)
            if (!Eligibility.isEligible(entity, group)) continue

            val targets = if (job.needsBlockTargets) {
                val set = session?.cache?.get(job.jobType) ?: continue
                validateTargets(world, job, set)
                if (set.isEmpty()) continue
                set
            } else {
                mutableSetOf()
            }
            candidates.add(JobCandidate(job, targets))
        }
        return candidates
    }

    /** True when [entity] has at least one viable job right now. */
    fun isActive(entity: PokemonEntity, pokemon: Pokemon, session: PastureSession?, world: World): Boolean =
        viableJobs(entity, pokemon, session, world).isNotEmpty()

    /**
     * Drops positions that no longer match the job. `matches` is guarded: a modded block in a matching
     * tag that lacks an expected property must not abort the tick, so a throwing position is treated as
     * non-matching and removed.
     */
    private fun validateTargets(world: World, job: WorkJob, targets: MutableSet<BlockPos>) {
        targets.removeAll { pos ->
            !runCatching { job.matches(world.getBlockState(pos), world, pos) }.getOrDefault(false)
        }
    }
}
