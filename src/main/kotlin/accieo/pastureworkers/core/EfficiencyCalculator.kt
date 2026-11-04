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
import com.cobblemon.mod.common.pokemon.Nature
import com.cobblemon.mod.common.pokemon.Pokemon

object EfficiencyCalculator {

    /** Game constants, not config: the hard caps Cobblemon itself applies. */
    private const val MAX_IV = 31f
    private const val MAX_EV = 252f

    /**
     * Per-axis contributions to a worker's efficiency, all already in efficiency space (0..1).
     * The GUI renders these directly so a player can see *why* a Pokemon is fast or slow.
     */
    data class Breakdown(
        /** Level axis: drives work speed. */
        val level: Float,
        /** Species base stat axis. */
        val base: Float,
        /** IV axis. */
        val iv: Float,
        /** EV axis. */
        val ev: Float,
        /** Nature axis, negative when the nature lowers [stat]. */
        val nature: Float,
        /** base + iv + ev, clamped by `statBonusCap`. This is the "extra yield" axis. */
        val stat: Float,
        /** Sum of every axis before the per-job multiplier. */
        val raw: Float,
        /** Final speed value used by the manager: `raw * multiplier`, clamped to `efficiencyCap`. */
        val efficiency: Float,
        /**
         * Extra-yield value: only the quality axis (`stat * multiplier`), so level and nature never
         * raise the drop chance. Clamped like [efficiency].
         */
        val yieldEfficiency: Float
    )

    fun compute(pokemon: Pokemon, stat: Stat, multiplier: Float = 1f): Float =
        explain(pokemon, stat, multiplier).efficiency

    /**
     * Level and stat are deliberately separate axes: level (which every Pokemon gains by grinding)
     * only speeds work up, while base stat + IV + EV (the breeding axis) raise efficiency for both
     * speed and extra yield. Scaling on `pokemon.getStat(stat)` instead would be a level proxy,
     * because that value is level-scaled and saturates the stat bonus for anything above ~level 40.
     */
    fun explain(pokemon: Pokemon, stat: Stat, multiplier: Float = 1f): Breakdown {
        val scaling = PastureWorkersConfig.config.scaling

        val level = (pokemon.level.coerceAtMost(scaling.levelCap) / scaling.levelCap.toFloat()) * scaling.levelWeight
        val base = (baseStat(pokemon, stat) / scaling.baseStatReference.coerceAtLeast(1f))
            .coerceIn(0f, 1f) * scaling.baseWeight
        val iv = (pokemon.ivs.getOrDefault(stat) / MAX_IV).coerceIn(0f, 1f) * scaling.ivWeight
        val ev = (pokemon.evs.getOrDefault(stat) / MAX_EV).coerceIn(0f, 1f) * scaling.evWeight
        val statBonus = (base + iv + ev).coerceIn(-scaling.statBonusCap, scaling.statBonusCap)
        val nature = natureBonus(pokemon, stat, scaling.natureBonus)

        val raw = level + statBonus + nature
        return Breakdown(
            level = level,
            base = base,
            iv = iv,
            ev = ev,
            nature = nature,
            stat = statBonus,
            raw = raw,
            efficiency = (raw * multiplier).coerceIn(0f, scaling.efficiencyCap),
            yieldEfficiency = (statBonus * multiplier).coerceIn(0f, scaling.efficiencyCap)
        )
    }

    private fun baseStat(pokemon: Pokemon, stat: Stat): Float =
        (pokemon.form.baseStats[stat] ?: pokemon.species.baseStats[stat] ?: 0).toFloat()

    private fun natureBonus(pokemon: Pokemon, stat: Stat, bonus: Float): Float {
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

    /**
     * Chance of one extra drop from a cycle. Single source of truth: the manager rolls it, the GUI
     * shows it. Takes the [Breakdown] rather than a raw float so the quality axis cannot be confused
     * with the speed axis.
     */
    fun extraYieldChance(breakdown: Breakdown): Float =
        minOf(breakdown.yieldEfficiency, PastureWorkersConfig.config.scaling.maxExtraYieldChance)
}
