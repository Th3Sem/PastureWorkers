/*
 * Copyright (c) 2026 Accieo
 * Copyright (c) 2026 Th3Sem
 *
 * SPDX-License-Identifier: MPL-2.0
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package accieo.pastureworkers.api

object JobRegistry {
    private val registered = mutableListOf<WorkJob>()

    /** Immutable snapshot, rebuilt only on registration, so the scan loop never allocates. */
    @Volatile
    var jobs: List<WorkJob> = emptyList()
        private set

    fun register(job: WorkJob) {
        registered.add(job)
        jobs = registered.toList()
    }
}
