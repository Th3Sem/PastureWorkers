/*
 * Copyright (c) 2026 Accieo
 * Copyright (c) 2026 Th3Sem
 *
 * SPDX-License-Identifier: MPL-2.0
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package accieo.pastureworkers

import accieo.pastureworkers.commands.Commands
import accieo.pastureworkers.config.PastureWorkersConfig
import accieo.pastureworkers.core.HookStatus
import accieo.pastureworkers.gui.PastureGui
import accieo.pastureworkers.jobs.Jobs
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents

object PastureWorkersInitializer : ModInitializer {

    override fun onInitialize() {
        PastureWorkersConfig.load()
        Jobs.registerAll()
        Commands.register()
        PastureGui.register()
        ServerTickEvents.END_SERVER_TICK.register { HookStatus.onServerTick() }
        PastureWorkers.LOGGER.info("[PastureWorkers] Initialised")
    }
}
