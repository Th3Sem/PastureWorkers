/*
 * Copyright (c) 2026 Accieo
 * Copyright (c) 2026 Th3Sem
 *
 * SPDX-License-Identifier: MPL-2.0
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package accieo.pastureworkers.core;

import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import java.util.ArrayList;
import java.util.List;

/**
 * Java bridge over Cobblemon's pasture/tethering API.
 *
 * The nested {@code PokemonPastureBlockEntity.Tethering} class does not resolve cleanly from
 * Kotlin against the remapped Cobblemon 1.8.1 jar, so we call the public getters from Java and
 * hand Kotlin a plain {@code List<Pokemon>}.
 */
public final class PastureWorkersAccess {

    private PastureWorkersAccess() {
    }

    public static List<Pokemon> tetheredPokemon(PokemonPastureBlockEntity blockEntity) {
        List<Pokemon> result = new ArrayList<>();
        List<PokemonPastureBlockEntity.Tethering> tethered = blockEntity.getTetheredPokemon();
        if (tethered == null) {
            return result;
        }
        for (PokemonPastureBlockEntity.Tethering tethering : tethered) {
            if (tethering == null) {
                continue;
            }
            try {
                Pokemon pokemon = tethering.getPokemon();
                if (pokemon != null) {
                    result.add(pokemon);
                }
            } catch (Exception ignored) {
                // Mirrors the reference behaviour: a broken tethering entry is skipped.
            }
        }
        return result;
    }
}
