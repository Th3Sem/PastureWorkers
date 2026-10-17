/*
 * Copyright (c) 2026 Accieo
 * Copyright (c) 2026 Th3Sem
 *
 * SPDX-License-Identifier: MPL-2.0
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package accieo.pastureworkers.config

import accieo.pastureworkers.config.PastureWorkersConfig.PastureWorkersConfigData
import com.google.gson.JsonObject

/**
 * Ordered config migrations. [apply] runs every migration for `(from, CURRENT_SCHEMA]` against both the
 * deserialized [config] and the raw on-disk [raw] JSON (needed to tell a user edit from a default).
 *
 * General rule: a default-change migration rewrites a value ONLY when the on-disk value still equals
 * the OLD default signature. A genuine user edit is always preserved.
 */
object ConfigMigrations {

    private val HONEY_OLD_SPECIES = listOf("ditto")
    private val HONEY_NEW_SPECIES =
        listOf("combee", "vespiquen", "beedrill", "teddiursa", "ursaring", "ribombee")

    fun apply(config: PastureWorkersConfigData, raw: JsonObject?, from: Int) {
        if (from >= CURRENT_SCHEMA) return
        if (from < 1) migrateV0ToV1(config, raw)
        if (from < 2) migrateV1ToV2(config, raw)
    }

    /**
     * v0 -> v1 (honey). The old defaults `type = ""` / `species = ["ditto"]` made every Ditto a
     * beekeeper and left no bee eligible. v1 defaults are `type = "BUG"` plus the bee/ursine species
     * list. Rewrite only when BOTH raw fields still carry the old default signature.
     */
    private fun migrateV0ToV1(config: PastureWorkersConfigData, raw: JsonObject?) {
        val honey = raw?.getAsJsonObject("honey") ?: return

        val rawType = honey.get("type")?.takeIf { !it.isJsonNull }?.asString
        val rawSpecies = honey.getAsJsonArray("species")?.map { it.asString }

        val oldTypeSignature = rawType == null || rawType.isEmpty() || rawType == "NONE"
        val oldSpeciesSignature = rawSpecies == HONEY_OLD_SPECIES

        if (oldTypeSignature && oldSpeciesSignature) {
            config.honey.type = "BUG"
            config.honey.species = HONEY_NEW_SPECIES.toMutableList()
        }
    }

    /**
     * v1 -> v2 (scaling config). v2 moves the hardcoded scaling/efficiency constants into the
     * `scaling` group and adds per-job `stat` / `efficiencyMultiplier` keys. [PastureWorkersConfig.load]
     * deep-merges fresh defaults over the on-disk JSON, so those new keys are backfilled with their
     * defaults while every existing user value is preserved. Nothing needs rewriting here; this step
     * exists only to keep the migration chain explicit.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun migrateV1ToV2(config: PastureWorkersConfigData, raw: JsonObject?) {
        // No-op: deep-merge backfills the new v2 keys. Do not touch user values.
    }
}
