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
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity

object Eligibility {

    fun isEligible(pokemonEntity: PokemonEntity, group: PastureWorkersConfig.JobGroup): Boolean {
        if (!group.enabled) return false

        val pokemon = pokemonEntity.pokemon
        val typeMatch = group.type.isNotBlank() && pokemon.types.any {
            it.name.equals(group.type, ignoreCase = true)
        }
        // Match on the stable showdown name (e.g. "ditto"), not the localized display name.
        val speciesMatch = group.species.any {
            it.equals(pokemon.species.name, ignoreCase = true)
        }

        return typeMatch || speciesMatch
    }
}
