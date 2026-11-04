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

import accieo.pastureworkers.config.PastureWorkersConfig.JobGroup
import accieo.pastureworkers.config.PastureWorkersConfig.PastureWorkersConfigData
import accieo.pastureworkers.config.PastureWorkersConfig.ScalingGroup
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

    /** v2 per-job `stat` defaults. */
    private val JOB_STAT_V2 = mapOf(
        "cropHarvest" to "ATTACK",
        "cropIrrigation" to "DEFENCE",
        "amethystHarvest" to "ATTACK",
        "gemHarvest" to "ATTACK",
        "apricornHarvest" to "ATTACK",
        "berryHarvest" to "SPECIAL_ATTACK",
        "mintHarvest" to "SPECIAL_ATTACK",
        "netherwartHarvest" to "ATTACK",
        "tumblestoneHarvest" to "ATTACK",
        "honey" to "SPEED",
        "groundItemGather" to "SPEED"
    )

    /** v3 per-job `stat` defaults: one distinct stat axis per job family. */
    private val JOB_STAT_V3 = mapOf(
        "cropHarvest" to "ATTACK",
        "cropIrrigation" to "SPECIAL_ATTACK",
        "amethystHarvest" to "ATTACK",
        "gemHarvest" to "DEFENCE",
        "apricornHarvest" to "SPEED",
        "berryHarvest" to "SPECIAL_DEFENCE",
        "mintHarvest" to "SPECIAL_ATTACK",
        "netherwartHarvest" to "SPECIAL_DEFENCE",
        "tumblestoneHarvest" to "DEFENCE",
        "honey" to "SPEED",
        "groundItemGather" to "SPEED"
    )

    fun apply(config: PastureWorkersConfigData, raw: JsonObject?, from: Int) {
        if (from >= CURRENT_SCHEMA) return
        if (from < 1) migrateV0ToV1(config, raw)
        if (from < 2) migrateV1ToV2(config, raw)
        if (from < 3) migrateV2ToV3(config, raw)
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

    /**
     * v2 -> v3 (job stats + scaling rework). v3 spreads the scaling stat over the other stat axes and
     * replaces the level-scaled `statBaseline`/`statDivisor` pair with base/IV/EV weights. A job stat is
     * rewritten only when the raw value still equals the v2 default; a `scaling` group is replaced only
     * when it still carries the v2 default signature (statBaseline 70, statDivisor 500). The dropped keys
     * vanish from the file on the next save because they no longer exist on [ScalingGroup].
     */
    private fun migrateV2ToV3(config: PastureWorkersConfigData, raw: JsonObject?) {
        if (raw == null) return

        val groups: Map<String, JobGroup> = mapOf(
            "cropHarvest" to config.cropHarvest,
            "cropIrrigation" to config.cropIrrigation,
            "amethystHarvest" to config.amethystHarvest,
            "gemHarvest" to config.gemHarvest,
            "apricornHarvest" to config.apricornHarvest,
            "berryHarvest" to config.berryHarvest,
            "mintHarvest" to config.mintHarvest,
            "netherwartHarvest" to config.netherwartHarvest,
            "tumblestoneHarvest" to config.tumblestoneHarvest,
            "honey" to config.honey,
            "groundItemGather" to config.groundItemGather
        )

        for ((job, oldStat) in JOB_STAT_V2) {
            val rawStat = (raw.get(job) as? JsonObject)
                ?.get("stat")?.takeIf { !it.isJsonNull }?.asString
            if (rawStat != oldStat) continue
            groups.getValue(job).stat = JOB_STAT_V3.getValue(job)
        }

        val rawScaling = raw.get("scaling") as? JsonObject
        val baseline = rawScaling?.get("statBaseline")?.takeIf { !it.isJsonNull }?.asInt
        val divisor = rawScaling?.get("statDivisor")?.takeIf { !it.isJsonNull }?.asFloat
        if (baseline == 70 && divisor == 500f) config.scaling = ScalingGroup()
    }
}
