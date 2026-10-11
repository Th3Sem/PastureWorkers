/*
 * Copyright (c) 2026 Accieo
 * Copyright (c) 2026 Th3Sem
 *
 * SPDX-License-Identifier: MPL-2.0
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package accieo.pastureworkers.jobs

import accieo.pastureworkers.api.JobRegistry

object Jobs {
    fun registerAll() {
        JobRegistry.register(CropHarvestJob)
        JobRegistry.register(CropIrrigationJob)
        JobRegistry.register(AmethystHarvestJob)
        JobRegistry.register(GemHarvestJob)
        JobRegistry.register(ApricornHarvestJob)
        JobRegistry.register(BerryHarvestJob)
        JobRegistry.register(MintHarvestJob)
        JobRegistry.register(NetherwartHarvestJob)
        JobRegistry.register(TumblestoneHarvestJob)
        JobRegistry.register(HoneyJob)
        JobRegistry.register(GroundItemGatherJob)
    }
}
