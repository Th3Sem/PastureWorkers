/*
 * Copyright (c) 2026 Accieo
 * Copyright (c) 2026 Th3Sem
 *
 * SPDX-License-Identifier: MPL-2.0
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package accieo.pastureworkers.commands

import accieo.pastureworkers.PastureWorkers
import accieo.pastureworkers.api.JobRegistry
import accieo.pastureworkers.config.PastureWorkersConfig
import accieo.pastureworkers.core.Eligibility
import accieo.pastureworkers.core.EfficiencyCalculator
import accieo.pastureworkers.core.HookStatus
import accieo.pastureworkers.core.PastureWorkManager
import accieo.pastureworkers.core.PastureWorkersAccess
import accieo.pastureworkers.gui.PastureGui
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
import com.mojang.brigadier.CommandDispatcher
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.entity.Entity
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import kotlin.math.roundToInt

object Commands {

    fun register() {
        CommandRegistrationCallback.EVENT.register { dispatcher: CommandDispatcher<ServerCommandSource>, _: CommandRegistryAccess, _: CommandManager.RegistrationEnvironment ->
            dispatcher.register(
                CommandManager.literal("pastureworkers")
                    .then(
                        CommandManager.literal("status")
                            .requires { it.hasPermissionLevel(2) }
                            .executes { ctx ->
                                status(ctx.source)
                                1
                            }
                    )
                    .then(
                        CommandManager.literal("radius")
                            .requires { it.hasPermissionLevel(2) }
                            .executes { ctx ->
                                radius(ctx.source)
                                1
                            }
                    )
                    .then(
                        CommandManager.literal("reload")
                            .requires { it.hasPermissionLevel(2) }
                            .executes { ctx ->
                                reload(ctx.source)
                                1
                            }
                    )
                    .then(
                        CommandManager.literal("gui").executes { ctx ->
                            openGui(ctx.source)
                            1
                        }
                    )
            )
        }
    }

    private fun status(source: ServerCommandSource) {
        val player = source.player ?: return
        val pasture = findPasture(player) ?: run {
            source.sendError(Text.literal("No Pasture block found nearby."))
            return
        }

        val pos = pasture.pos
        val session = PastureWorkManager.session(player.serverWorld, pos)
        val config = PastureWorkersConfig.config.general

        val tethered = PastureWorkersAccess.tetheredPokemon(pasture)
        val lines = mutableListOf<Text>()
        lines.add(Text.literal("Pasture at ${pos.toShortString()} — ${tethered.size} worker(s)"))

        for (pokemon in tethered) {
            val uuid = pokemon.uuid
            val nextTick = session?.nextWorkTimes?.get(uuid) ?: 0L
            val seconds = ((nextTick - player.serverWorld.time) / 20.0).roundToInt().coerceAtLeast(0)

            val entity = pokemon.entity
            val eligibleInfo = if (entity == null) {
                "none"
            } else {
                JobRegistry.jobs.mapNotNull { job ->
                    if (!Eligibility.isEligible(entity, PastureWorkersConfig.groupFor(job.jobType))) return@mapNotNull null
                    val eff = EfficiencyCalculator.compute(pokemon, job.relevantStat)
                    "${job.jobType.name} ${(eff * 100).roundToInt()}%"
                }.joinToString().ifEmpty { "none" }
            }

            lines.add(Text.literal("- ${pokemon.species.translatedName.string}: jobs[$eligibleInfo] next in ${seconds}s"))
        }

        val targets = session?.cache?.entries?.joinToString { "${it.key.name}: ${it.value.size}" } ?: "none"
        lines.add(Text.literal("Targets: $targets"))
        lines.add(Text.literal("Chests: ${session?.chests?.size ?: 0}"))
        lines.add(Text.literal("Tick hook: ${if (HookStatus.tickHookActive) "active" else "NOT ATTACHED"}"))
        lines.add(Text.literal("Radius=${config.searchRadius} Height=${config.searchHeight} Interval=${config.workIntervalTicks}t"))

        lines.forEach { source.sendFeedback({ it }, false) }
    }

    private fun radius(source: ServerCommandSource) {
        val player = source.player ?: return
        val pasture = findPasture(player) ?: run {
            source.sendError(Text.literal("No Pasture block found nearby."))
            return
        }

        val pos = pasture.pos
        val world = player.serverWorld
        val r = PastureWorkersConfig.config.general.searchRadius
        val h = PastureWorkersConfig.config.general.searchHeight

        val min = pos.add(-r, -h, -r)
        val max = pos.add(r, h, r)
        val box = Box(Vec3d(min.x.toDouble(), min.y.toDouble(), min.z.toDouble()), Vec3d(max.x + 1.0, max.y + 1.0, max.z + 1.0))

        // ponytail: simplified outline — bottom/top perimeters + vertical edges every 2 blocks.
        // With very large radius/height the particle count grows linearly; fine for the defaults.
        spawnBoxOutline(world, box)
        source.sendFeedback({ Text.literal("Scan outline displayed.") }, false)
    }

    private fun reload(source: ServerCommandSource) {
        try {
            PastureWorkersConfig.load()
            source.sendFeedback({ Text.literal("Configuration reloaded.") }, false)
        } catch (e: Exception) {
            PastureWorkers.LOGGER.error("[PastureWorkers] Failed to reload configuration", e)
            source.sendError(Text.literal("Failed to reload configuration: ${e.message}"))
        }
    }

    private fun openGui(source: ServerCommandSource) {
        val player = source.player
        if (player == null) {
            source.sendError(Text.literal("This command can only be used by a player."))
            return
        }
        if (!PastureWorkersConfig.config.interaction.shiftRightClickGui) {
            source.sendError(Text.literal("GUI is disabled in config"))
            return
        }

        val pasture = findPastureNear(player, 5) ?: run {
            source.sendError(Text.literal("No Pasture block found nearby."))
            return
        }
        PastureGui.open(player, pasture)
    }

    private fun findPastureNear(player: ServerPlayerEntity, range: Int): PokemonPastureBlockEntity? {
        val world = player.serverWorld
        val hit = player.raycast(5.0, 0.0f, false)
        if (hit.type == HitResult.Type.BLOCK) {
            val pos = (hit as BlockHitResult).blockPos
            world.getBlockEntity(pos)?.let { if (it is PokemonPastureBlockEntity) return it }
        }

        val origin = player.blockPos
        var closest: PokemonPastureBlockEntity? = null
        var closestDist = Double.MAX_VALUE
        for (x in -range..range) {
            for (y in -range..range) {
                for (z in -range..range) {
                    val pos = origin.add(x, y, z)
                    val be = world.getBlockEntity(pos) as? PokemonPastureBlockEntity ?: continue
                    val d = pos.getSquaredDistance(origin)
                    if (d < closestDist) {
                        closestDist = d
                        closest = be
                    }
                }
            }
        }
        return closest
    }

    private fun findPasture(player: ServerPlayerEntity): PokemonPastureBlockEntity? {
        val world = player.serverWorld
        val hit = player.raycast(8.0, 0.0f, false)
        if (hit.type == HitResult.Type.BLOCK) {
            val pos = (hit as BlockHitResult).blockPos
            world.getBlockEntity(pos)?.let { if (it is PokemonPastureBlockEntity) return it }
        }

        val origin = player.blockPos
        var closest: PokemonPastureBlockEntity? = null
        var closestDist = Double.MAX_VALUE
        val range = 16
        for (x in -range..range) {
            for (y in -range..range) {
                for (z in -range..range) {
                    val pos = origin.add(x, y, z)
                    val be = world.getBlockEntity(pos) as? PokemonPastureBlockEntity ?: continue
                    val d = pos.getSquaredDistance(origin)
                    if (d < closestDist) {
                        closestDist = d
                        closest = be
                    }
                }
            }
        }
        return closest
    }

    private fun spawnBoxOutline(world: net.minecraft.server.world.ServerWorld, box: Box) {
        val minX = box.minX
        val minY = box.minY
        val minZ = box.minZ
        val maxX = box.maxX
        val maxY = box.maxY
        val maxZ = box.maxZ

        // bottom and top perimeters
        for (x in minX.toInt()..maxX.toInt()) {
            spawnParticle(world, x.toDouble() + 0.5, minY + 0.1, minZ + 0.5)
            spawnParticle(world, x.toDouble() + 0.5, minY + 0.1, maxZ + 0.5)
            spawnParticle(world, x.toDouble() + 0.5, maxY + 0.1, minZ + 0.5)
            spawnParticle(world, x.toDouble() + 0.5, maxY + 0.1, maxZ + 0.5)
        }
        for (z in minZ.toInt()..maxZ.toInt()) {
            spawnParticle(world, minX + 0.5, minY + 0.1, z.toDouble() + 0.5)
            spawnParticle(world, maxX + 0.5, minY + 0.1, z.toDouble() + 0.5)
            spawnParticle(world, minX + 0.5, maxY + 0.1, z.toDouble() + 0.5)
            spawnParticle(world, maxX + 0.5, maxY + 0.1, z.toDouble() + 0.5)
        }

        // vertical edges every 2 blocks
        for (y in minY.toInt()..maxY.toInt() step 2) {
            spawnParticle(world, minX + 0.5, y.toDouble() + 0.1, minZ + 0.5)
            spawnParticle(world, maxX + 0.5, y.toDouble() + 0.1, minZ + 0.5)
            spawnParticle(world, minX + 0.5, y.toDouble() + 0.1, maxZ + 0.5)
            spawnParticle(world, maxX + 0.5, y.toDouble() + 0.1, maxZ + 0.5)
        }
    }

    private fun spawnParticle(world: net.minecraft.server.world.ServerWorld, x: Double, y: Double, z: Double) {
        world.spawnParticles(ParticleTypes.END_ROD, x, y, z, 1, 0.0, 0.0, 0.0, 0.0)
    }
}
