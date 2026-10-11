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
import accieo.pastureworkers.config.PastureWorkersConfig
import accieo.pastureworkers.core.EfficiencyCalculator
import accieo.pastureworkers.core.HookStatus
import accieo.pastureworkers.core.PastureWorkManager
import accieo.pastureworkers.core.PastureWorkersAccess
import accieo.pastureworkers.core.work.JobSelection
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
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.BlockPos
import kotlin.math.roundToInt

object Commands {

    /** Upper bound on particles drawn for the reachable-region outline, so it stays crisp. */
    private const val MAX_OUTLINE_PARTICLES = 3000

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
                JobSelection.viableJobs(entity, pokemon, session, player.serverWorld)
                    .joinToString { candidate ->
                        val eff = EfficiencyCalculator.compute(pokemon, candidate.job.relevantStat)
                        "${candidate.job.jobType.name} ${(eff * 100).roundToInt()}%"
                    }
                    .ifEmpty { "none" }
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
        val general = PastureWorkersConfig.config.general

        // Prefer the reachable region's boundary when the flood-fill scan is active.
        val reachable = if (general.requireReachableSpace) {
            PastureWorkManager.session(world, pos)?.reachableBoundary
        } else {
            null
        }
        if (reachable != null && reachable.isNotEmpty()) {
            spawnReachableOutline(world, player, reachable)
            source.sendFeedback({ Text.literal("Reachable outline displayed.") }, false)
            return
        }

        // Fallback (reachability off, or no completed scan yet): the search box perimeter.
        // Same inclusive bounds the AreaScanner scans, with the vertical range clamped to the world.
        val minX = pos.x - general.searchRadius
        val maxX = pos.x + general.searchRadius
        val minZ = pos.z - general.searchRadius
        val maxZ = pos.z + general.searchRadius
        val minY = (pos.y - general.searchHeight).coerceAtLeast(world.bottomY)
        val maxY = (pos.y + general.searchHeight).coerceAtMost(world.topY - 1)

        // Targeted at the requester so the outline is always visible regardless of view distance.
        spawnBoxOutline(world, player, minX, minY, minZ, maxX, maxY, maxZ)
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

    private fun spawnReachableOutline(world: ServerWorld, player: ServerPlayerEntity, boundary: Set<BlockPos>) {
        // Deterministic ordering keeps the sampled outline stable between invocations. When the
        // boundary is large we stride through it so the particle count stays bounded and crisp.
        val ordered = boundary.sortedWith(compareBy({ it.y }, { it.x }, { it.z }))
        val stride = (ordered.size / MAX_OUTLINE_PARTICLES + 1).coerceAtLeast(1)
        var index = 0
        for (pos in ordered) {
            if (index++ % stride != 0) continue
            spawnParticle(world, player, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5)
        }
    }

    private fun spawnBoxOutline(world: ServerWorld, player: ServerPlayerEntity, minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int) {
        // Bottom and top rectangles, one particle per block position (count=1 => crisp line).
        for (x in minX..maxX) {
            spawnParticle(world, player, x + 0.5, minY + 0.1, minZ + 0.5)
            spawnParticle(world, player, x + 0.5, minY + 0.1, maxZ + 0.5)
            spawnParticle(world, player, x + 0.5, maxY + 1.1, minZ + 0.5)
            spawnParticle(world, player, x + 0.5, maxY + 1.1, maxZ + 0.5)
        }
        for (z in minZ..maxZ) {
            spawnParticle(world, player, minX + 0.5, minY + 0.1, z + 0.5)
            spawnParticle(world, player, maxX + 0.5, minY + 0.1, z + 0.5)
            spawnParticle(world, player, minX + 0.5, maxY + 1.1, z + 0.5)
            spawnParticle(world, player, maxX + 0.5, maxY + 1.1, z + 0.5)
        }

        // Vertical edges connecting the corners, sparse (every 2 blocks).
        for (y in minY..maxY step 2) {
            spawnParticle(world, player, minX + 0.5, y + 0.5, minZ + 0.5)
            spawnParticle(world, player, maxX + 0.5, y + 0.5, minZ + 0.5)
            spawnParticle(world, player, minX + 0.5, y + 0.5, maxZ + 0.5)
            spawnParticle(world, player, maxX + 0.5, y + 0.5, maxZ + 0.5)
        }
    }

    private fun spawnParticle(world: ServerWorld, player: ServerPlayerEntity, x: Double, y: Double, z: Double) {
        world.spawnParticles(player, ParticleTypes.END_ROD, true, x, y, z, 1, 0.0, 0.0, 0.0, 0.0)
    }
}
