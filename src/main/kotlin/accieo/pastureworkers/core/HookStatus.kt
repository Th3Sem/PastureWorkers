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
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The mixin tick hook is injected with `require = 0` so a Cobblemon target change degrades
 * gracefully instead of crashing at startup. This object records whether the hook ever ran and
 * emits a single explanatory warning if it stays silent.
 */
object HookStatus {
    private const val WARN_AFTER_TICKS = 1200

    private val tickHookSeen = AtomicBoolean(false)
    private val watchdogLogged = AtomicBoolean(false)
    private var ticksWithoutHook = 0

    val tickHookActive: Boolean get() = tickHookSeen.get()

    fun markTickHookSeen() {
        tickHookSeen.set(true)
    }

    fun onServerTick() {
        if (tickHookSeen.get()) return
        ticksWithoutHook++
        if (ticksWithoutHook >= WARN_AFTER_TICKS && watchdogLogged.compareAndSet(false, true)) {
            PastureWorkers.LOGGER.warn(
                "[PastureWorkers] Pasture tick hook not attached after {}s (Cobblemon target changed?). " +
                    "Automation is disabled, but the game is not crashed. Update the mod or report the mismatch.",
                WARN_AFTER_TICKS / 20
            )
        }
    }
}
