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

import accieo.pastureworkers.api.JobType
import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files

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
        config = if (Files.exists(path)) {
            val json = Files.readString(path)
            gson.fromJson(json, PastureWorkersConfigData::class.java).apply { coerce() }
        } else {
            PastureWorkersConfigData().apply { coerce() }
        }
        save()
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

    open class JobGroup {
        /** Whether this job is active. */
        var enabled: Boolean = true
        /** Required Pokemon type for this job (e.g. GRASS, WATER, NONE). */
        var type: String = ""
        /** Species allowed to perform this job. */
        var species: MutableList<String> = mutableListOf("ditto")
    }

    class HoneyJobGroup : JobGroup() {
        /** Whether honey is generated over time while a valid worker is tethered. */
        var generateHoney: Boolean = true
        /** Seconds between honey generation ticks. */
        var honeyGenerationCooldownSeconds: Long = 60
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
        var general: GeneralGroup = GeneralGroup()
        var interaction: InteractionGroup = InteractionGroup()
        var cropHarvest: JobGroup = JobGroup().apply { type = "GRASS" }
        var cropIrrigation: JobGroup = JobGroup().apply { type = "WATER" }
        var amethystHarvest: JobGroup = JobGroup().apply { type = "ROCK" }
        var gemHarvest: JobGroup = JobGroup().apply { type = "ROCK" }
        var apricornHarvest: JobGroup = JobGroup().apply { type = "BUG" }
        var berryHarvest: JobGroup = JobGroup().apply { type = "GRASS" }
        var mintHarvest: JobGroup = JobGroup().apply { type = "FAIRY" }
        var netherwartHarvest: JobGroup = JobGroup().apply { type = "GHOST" }
        var tumblestoneHarvest: JobGroup = JobGroup().apply { type = "STEEL" }
        var honey: HoneyJobGroup = HoneyJobGroup().apply { type = "NONE" }
        var groundItemGather: JobGroup = JobGroup().apply { type = "PSYCHIC" }

        fun coerce() {
            general.coerce()
            interaction.coerce()
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
}
