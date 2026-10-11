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

import com.cobblemon.mod.common.api.pokemon.stats.Stat
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.Nature

object EfficiencyCalculator {

    fun compute(pokemon: Pokemon, stat: Stat): Float {
        val levelBonus = (pokemon.level.coerceAtMost(100) / 100f) * 0.20f
        val statValue = pokemon.getStat(stat)
        val statBonus = ((statValue - 70) / 500f).coerceIn(-0.10f, 0.10f)
        val natureBonus = computeNatureBonus(pokemon, stat)

        return (levelBonus + statBonus + natureBonus).coerceIn(0f, 0.5f)
    }

    private fun computeNatureBonus(pokemon: Pokemon, stat: Stat): Float {
        val nature: Nature = pokemon.effectiveNature

        return when {
            nature.increasedStat == stat -> 0.10f
            nature.decreasedStat == stat -> -0.10f
            else -> 0f
        }
    }
}
