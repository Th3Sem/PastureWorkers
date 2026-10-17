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

import accieo.pastureworkers.PastureWorkers
import accieo.pastureworkers.api.JobType
import accieo.pastureworkers.api.WorkJob
import com.cobblemon.mod.common.api.pokemon.stats.Stat
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files

/** Current config schema version. Bump when a default change needs a migration. */
const val CURRENT_SCHEMA = 2

object PastureWorkersConfig {

    lateinit var config: PastureWorkersConfigData
        private set

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val configPath by lazy {
        FabricLoader.getInstance().configDir.resolve("pastureworkers.json")
    }

    fun load() {
        val path = configPath
        Files.createDirectories(path.parent)
        if (Files.exists(path)) {
            val json = Files.readString(path)

            // Read the raw on-disk JSON BEFORE merging. deepMerge backfills schemaVersion from the
            // defaults, which would hide a legacy file's real (pre-migration) version.
            val raw = runCatching { gson.fromJson(json, JsonObject::class.java) }.getOrNull()

            // Gson ignores Kotlin field initializers when deserializing, so a key missing from a
            // config file written by an older build would land as the JVM default (false / 0) instead
            // of the intended Kotlin default. Deep-merge the on-disk JSON over a freshly serialized
            // default so absent keys keep their defaults while present keys still win.
            val defaults = gson.toJsonTree(PastureWorkersConfigData()).asJsonObject
            val merged = deepMerge(defaults, raw)

            val onDiskVersion = raw?.get("schemaVersion")?.asInt ?: 0
            config = gson.fromJson(merged, PastureWorkersConfigData::class.java)
            ConfigMigrations.apply(config, raw, onDiskVersion)
            config.coerce()
        } else {
            config = PastureWorkersConfigData().apply { coerce() }
        }
        save()
    }

    /** Recursively overlays [override] onto [base]; missing keys keep [base]'s value. */
    private fun deepMerge(base: JsonObject, override: JsonObject?): JsonObject {
        if (override == null) return base
        val result = JsonObject()
        for (key in base.keySet() + override.keySet()) {
            val baseValue = base.get(key)
            val overrideValue = override.get(key)
            when {
                baseValue is JsonObject && overrideValue is JsonObject ->
                    result.add(key, deepMerge(baseValue, overrideValue))
                overrideValue != null && !overrideValue.isJsonNull -> result.add(key, overrideValue)
                baseValue != null -> result.add(key, baseValue)
            }
        }
        return result
    }

    fun save() {
        config.coerce()
        Files.writeString(configPath, gson.toJson(config))
    }

    class GeneralGroup {
        /** Ticks between work attempts for each tethered Pokemon. Range: 20..1200. */
        var workIntervalTicks: Int = 200
        /** Maximum number of blocks scanned per server tick. Range: 64..4096. */
        var blocksPerScanTick: Int = 512
        /** Horizontal search radius around the pasture block, in blocks. Range: 1..32. */
        var searchRadius: Int = 8
        /** Vertical search height around the pasture block, in blocks. Range: 1..256. */
        var searchHeight: Int = 32
        /** Ticks before the block cache is scanned again. Range: 200..7200. */
        var rescanCooldownTicks: Int = 1200
        /**
         * When true, only the contiguous walkable space connected to the pasture is worked; closed
         * perimeter walls stop the scan. When false, the full search box is scanned (legacy behaviour).
         */
        var requireReachableSpace: Boolean = true

        fun coerce() {
            workIntervalTicks = workIntervalTicks.coerceIn(20, 1200)
            blocksPerScanTick = blocksPerScanTick.coerceIn(64, 4096)
            searchRadius = searchRadius.coerceIn(1, 32)
            searchHeight = searchHeight.coerceIn(1, 256)
            rescanCooldownTicks = rescanCooldownTicks.coerceIn(200, 7200)
        }
    }

    /** Efficiency scaling constants. Previously hardcoded in [accieo.pastureworkers.core.EfficiencyCalculator]. */
    class ScalingGroup {
        /** Level at which the level bonus reaches its maximum. Range: 1..1000. */
        var levelCap: Int = 100
        /** Bonus fraction granted at [levelCap] level. Range: 0..1. */
        var levelWeight: Float = 0.20f
        /** Stat value at which the stat bonus is zero. */
        var statBaseline: Int = 70
        /** Divisor applied to (stat - statBaseline). Coerced to >= 1. */
        var statDivisor: Float = 500f
        /** Symmetric cap on the stat bonus. Range: 0..1. */
        var statBonusCap: Float = 0.10f
        /** Bonus for a nature raising the scaling stat (negative for one lowering it). Range: 0..1. */
        var natureBonus: Float = 0.10f
        /** Upper clamp on the final efficiency. Range: 0..1. */
        var efficiencyCap: Float = 0.5f
        /** Fraction of the base interval removed at efficiency 1.0. Range: 0..1. */
        var intervalReduction: Float = 0.8f
        /** Lower clamp on the computed work interval, in ticks. Range: 1..1200. */
        var minIntervalTicks: Int = 20
        /** Divisor applied to the base interval for an idle retry. Coerced to >= 1. */
        var idleRetryDivisor: Int = 4
        /** Upper bound on the extra-yield roll chance. Range: 0..1. */
        var maxExtraYieldChance: Float = 0.5f

        fun coerce() {
            levelCap = levelCap.coerceIn(1, 1000)
            levelWeight = levelWeight.coerceIn(0f, 1f)
            statDivisor = statDivisor.coerceAtLeast(1f)
            statBonusCap = statBonusCap.coerceIn(0f, 1f)
            natureBonus = natureBonus.coerceIn(0f, 1f)
            efficiencyCap = efficiencyCap.coerceIn(0f, 1f)
            intervalReduction = intervalReduction.coerceIn(0f, 1f)
            minIntervalTicks = minIntervalTicks.coerceIn(1, 1200)
            idleRetryDivisor = idleRetryDivisor.coerceAtLeast(1)
            maxExtraYieldChance = maxExtraYieldChance.coerceIn(0f, 1f)
        }
    }

    open class JobGroup {
        /** Whether this job is active. */
        var enabled: Boolean = true
        /** Required Pokemon type for this job (e.g. GRASS, WATER, NONE). */
        var type: String = ""
        /** Species allowed to perform this job. */
        var species: MutableList<String> = mutableListOf("ditto")
        /**
         * Scaling stat name for this job (a Cobblemon Stats name such as ATTACK, DEFENCE, SPEED).
         * Blank keeps the job's built-in relevantStat.
         */
        var stat: String = ""
        /** Multiplies this job's computed efficiency before the final clamp. Coerced to >= 0. */
        var efficiencyMultiplier: Float = 1f

        open fun coerce() {
            efficiencyMultiplier = efficiencyMultiplier.coerceAtLeast(0f)
        }
    }

    class HoneyJobGroup : JobGroup() {
        /** Honeycomb produced per work cycle while a beehive/nest catalyst is present. Coerced to >= 0. */
        var honeycombPerHarvest: Int = 1
        /** Honey bottles produced per work cycle while a flower catalyst is present. Coerced to >= 0. */
        var honeyBottlesPerHarvest: Int = 1
        /** When true, honey-bottle production withdraws one GLASS_BOTTLE from a linked container. */
        var requireEmptyBottle: Boolean = true
        /** When true, a matched full hive is also drained to 0 for one bonus honeycomb. */
        var consumeFullHive: Boolean = false
        /**
         * When true and a beehive or bee nest is within the reachable area, the worker produces
         * honeycomb each cycle. The hive does not need to be full.
         */
        var hiveProducesHoneycomb: Boolean = true
        /**
         * When true and flowers are within the reachable area, the worker fills a honey bottle,
         * consuming one empty glass bottle from linked storage when requireEmptyBottle is true.
         */
        var flowerProducesHoneyBottles: Boolean = true

        override fun coerce() {
            super.coerce()
            honeycombPerHarvest = honeycombPerHarvest.coerceAtLeast(0)
            honeyBottlesPerHarvest = honeyBottlesPerHarvest.coerceAtLeast(0)
        }
    }

    class InteractionGroup {
        /** Whether shift + right-clicking a pasture with an empty hand opens the GUI. */
        var shiftRightClickGui: Boolean = true
        /** Minimum ticks between two GUI opens by the same player. Coerced to >= 0. */
        var guiOpenCooldownTicks: Int = 10

        fun coerce() {
            guiOpenCooldownTicks = guiOpenCooldownTicks.coerceAtLeast(0)
        }
    }

    class PastureWorkersConfigData {
        /** On-disk schema version, used to run the right migrations on load. */
        var schemaVersion: Int = CURRENT_SCHEMA
        var general: GeneralGroup = GeneralGroup()
        var interaction: InteractionGroup = InteractionGroup()
        var scaling: ScalingGroup = ScalingGroup()
        var cropHarvest: JobGroup = JobGroup().apply { type = "GRASS"; stat = "ATTACK" }
        var cropIrrigation: JobGroup = JobGroup().apply { type = "WATER"; stat = "DEFENCE" }
        var amethystHarvest: JobGroup = JobGroup().apply { type = "ROCK"; stat = "ATTACK" }
        var gemHarvest: JobGroup = JobGroup().apply { type = "ROCK"; stat = "ATTACK" }
        var apricornHarvest: JobGroup = JobGroup().apply { type = "BUG"; stat = "ATTACK" }
        var berryHarvest: JobGroup = JobGroup().apply { type = "GRASS"; stat = "SPECIAL_ATTACK" }
        var mintHarvest: JobGroup = JobGroup().apply { type = "FAIRY"; stat = "SPECIAL_ATTACK" }
        var netherwartHarvest: JobGroup = JobGroup().apply { type = "GHOST"; stat = "ATTACK" }
        var tumblestoneHarvest: JobGroup = JobGroup().apply { type = "STEEL"; stat = "ATTACK" }
        var honey: HoneyJobGroup = HoneyJobGroup().apply {
            type = "BUG"
            stat = "SPEED"
            species = mutableListOf("combee", "vespiquen", "beedrill", "teddiursa", "ursaring", "ribombee")
        }
        var groundItemGather: JobGroup = JobGroup().apply { type = "PSYCHIC"; stat = "SPEED" }

        fun coerce() {
            schemaVersion = CURRENT_SCHEMA
            general.coerce()
            interaction.coerce()
            scaling.coerce()
            listOf(
                cropHarvest, cropIrrigation, amethystHarvest, gemHarvest, apricornHarvest, berryHarvest,
                mintHarvest, netherwartHarvest, tumblestoneHarvest, honey, groundItemGather
            ).forEach { it.coerce() }
        }
    }

    fun groupFor(jobType: JobType): JobGroup = when (jobType) {
        JobType.CROP_HARVEST -> config.cropHarvest
        JobType.CROP_IRRIGATION -> config.cropIrrigation
        JobType.AMETHYST_HARVEST -> config.amethystHarvest
        JobType.GEM_HARVEST -> config.gemHarvest
        JobType.APRICORN_HARVEST -> config.apricornHarvest
        JobType.BERRY_HARVEST -> config.berryHarvest
        JobType.MINT_HARVEST -> config.mintHarvest
        JobType.NETHERWART_HARVEST -> config.netherwartHarvest
        JobType.TUMBLESTONE_HARVEST -> config.tumblestoneHarvest
        JobType.HONEY_COLLECT -> config.honey
        JobType.GROUND_ITEM_GATHER -> config.groundItemGather
    }

    private val STAT_BY_NAME: Map<String, Stat> = mapOf(
        "HP" to Stats.HP,
        "ATTACK" to Stats.ATTACK,
        "DEFENCE" to Stats.DEFENCE,
        "SPECIAL_ATTACK" to Stats.SPECIAL_ATTACK,
        "SPECIAL_DEFENCE" to Stats.SPECIAL_DEFENCE,
        "SPEED" to Stats.SPEED
    )
    private val warnedStats = HashSet<String>()

    /** Resolves a job's scaling stat from config, falling back to its built-in [WorkJob.relevantStat]. */
    fun statFor(job: WorkJob): Stat {
        val name = groupFor(job.jobType).stat
        if (name.isBlank()) return job.relevantStat
        return STAT_BY_NAME[name] ?: run {
            if (warnedStats.add(name)) {
                PastureWorkers.LOGGER.warn(
                    "[PastureWorkers] Unknown scaling stat '{}' for job {}; using its default",
                    name,
                    job.jobType
                )
            }
            job.relevantStat
        }
    }
}
