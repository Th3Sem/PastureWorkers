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
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity

/**
 * The mixin tick hook is injected with `require = 0` so a Cobblemon target change degrades
 * gracefully instead of crashing at startup. This checks once at startup that the injection
 * target still exists — cheaper and more accurate than waiting for a pasture to tick, which may
 * never happen in the first minute on a server where nobody is near one.
 */
object HookStatus {
    private const val TICK_METHOD = "TICKER\$lambda\$0"

    private var targetPresent = false
    private var verified = false

    val tickHookActive: Boolean get() = targetPresent

    fun verify() {
        if (verified) return
        verified = true
        targetPresent = PokemonPastureBlockEntity::class.java.declaredMethods.any { it.name == TICK_METHOD }
        if (!targetPresent) {
            PastureWorkers.LOGGER.warn(
                "[PastureWorkers] Cobblemon tick target {} not found (Cobblemon changed it?). " +
                    "Automation is disabled, but the game is not crashed. Update the mod or report the mismatch.",
                TICK_METHOD
            )
        }
    }
}
