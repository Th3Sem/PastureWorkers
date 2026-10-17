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

import accieo.pastureworkers.config.PastureWorkersConfig
import com.cobblemon.mod.common.api.pokemon.stats.Stat
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.Nature

object EfficiencyCalculator {

    fun compute(pokemon: Pokemon, stat: Stat, multiplier: Float = 1f): Float {
        val scaling = PastureWorkersConfig.config.scaling
        val levelBonus = (pokemon.level.coerceAtMost(scaling.levelCap) / scaling.levelCap.toFloat()) * scaling.levelWeight
        val statValue = pokemon.getStat(stat)
        val statBonus = ((statValue - scaling.statBaseline) / scaling.statDivisor)
            .coerceIn(-scaling.statBonusCap, scaling.statBonusCap)
        val natureBonus = computeNatureBonus(pokemon, stat, scaling.natureBonus)

        return ((levelBonus + statBonus + natureBonus) * multiplier).coerceIn(0f, scaling.efficiencyCap)
    }

    private fun computeNatureBonus(pokemon: Pokemon, stat: Stat, bonus: Float): Float {
        val nature: Nature = pokemon.effectiveNature

        return when {
            nature.increasedStat == stat -> bonus
            nature.decreasedStat == stat -> -bonus
            else -> 0f
        }
    }

    /** Ticks between work attempts: the base interval reduced by [efficiency]. */
    fun intervalTicks(baseTicks: Int, efficiency: Float): Long {
        val scaling = PastureWorkersConfig.config.scaling
        val multiplier = 1f - efficiency * scaling.intervalReduction
        return (baseTicks * multiplier).toLong().coerceAtLeast(scaling.minIntervalTicks.toLong())
    }

    /** Fraction of the base interval removed by [efficiency], for the GUI's speed display. */
    fun speedReduction(efficiency: Float): Float =
        efficiency * PastureWorkersConfig.config.scaling.intervalReduction
}
