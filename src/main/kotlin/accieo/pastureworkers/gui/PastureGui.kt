/*
 * Copyright (c) 2026 Accieo
 * Copyright (c) 2026 Th3Sem
 *
 * SPDX-License-Identifier: MPL-2.0
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package accieo.pastureworkers.gui

import accieo.pastureworkers.PastureWorkers
import accieo.pastureworkers.api.JobRegistry
import accieo.pastureworkers.api.WorkJob
import accieo.pastureworkers.config.PastureWorkersConfig
import accieo.pastureworkers.core.Eligibility
import accieo.pastureworkers.core.EfficiencyCalculator
import accieo.pastureworkers.core.HookStatus
import accieo.pastureworkers.core.PastureWorkManager
import accieo.pastureworkers.core.PastureWorkersAccess
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
import com.cobblemon.mod.common.pokemon.Pokemon
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.LoreComponent
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.inventory.SimpleInventory
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.particle.ParticleTypes
import net.minecraft.screen.GenericContainerScreenHandler
import net.minecraft.screen.NamedScreenHandlerFactory
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.ScreenHandlerType
import net.minecraft.screen.slot.Slot
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/**
 * Server-side, read-only "virtual chest" that exposes pasture diagnostics and controls.
 *
 * The GUI is a vanilla 9x3 [GenericContainerScreenHandler] backed by a [SimpleInventory]. Every
 * slot is display-only: [PastureGuiScreenHandler] cancels all slot interactions, so no item can
 * be taken, moved, inserted or shift-clicked. Button presses are dispatched entirely server-side.
 */
object PastureGui {

    private val openHandlers = ConcurrentHashMap<UUID, PastureGuiScreenHandler>()
    private val lastOpenTimes = ConcurrentHashMap<UUID, Long>()

    /** Registers the shift + right-click hook and the per-tick refresh/particle pump. */
    fun register() {
        UseBlockCallback.EVENT.register { player, world, hand, hitResult ->
            if (world.isClient) ActionResult.PASS else onUseBlock(player, world, hand, hitResult)
        }
        ServerTickEvents.END_SERVER_TICK.register { server -> onServerTick(server) }
    }

    private fun onUseBlock(player: PlayerEntity, world: World, hand: Hand, hitResult: BlockHitResult): ActionResult {
        if (!PastureWorkersConfig.config.interaction.shiftRightClickGui) return ActionResult.PASS
        if (!player.isSneaking) return ActionResult.PASS
        if (!player.getStackInHand(hand).isEmpty) return ActionResult.PASS

        val serverPlayer = player as? ServerPlayerEntity ?: return ActionResult.PASS
        val pasture = world.getBlockEntity(hitResult.blockPos) as? PokemonPastureBlockEntity
            ?: return ActionResult.PASS

        return if (open(serverPlayer, pasture)) ActionResult.SUCCESS else ActionResult.PASS
    }

    /**
     * Opens the GUI for [player] unless the configured per-player cooldown is still active.
     *
     * @return true if the GUI was opened.
     */
    fun open(player: ServerPlayerEntity, pasture: PokemonPastureBlockEntity): Boolean {
        val now = player.serverWorld.time
        val cooldown = PastureWorkersConfig.config.interaction.guiOpenCooldownTicks.toLong()
        val last = lastOpenTimes[player.uuid]
        if (last != null && now - last < cooldown) return false
        lastOpenTimes[player.uuid] = now

        val factory = object : NamedScreenHandlerFactory {
            override fun getDisplayName(): Text = Text.literal("Pasture Workers")

            override fun createMenu(syncId: Int, playerInventory: PlayerInventory, p: PlayerEntity): ScreenHandler {
                val handler = PastureGuiScreenHandler.create(syncId, playerInventory, pasture)
                handler.rebuild(p)
                openHandlers[player.uuid] = handler
                return handler
            }
        }
        player.openHandledScreen(factory)
        return true
    }

    private fun onServerTick(server: MinecraftServer) {
        val iterator = openHandlers.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val player = server.playerManager.getPlayer(entry.key)
            if (player == null || player.currentScreenHandler !== entry.value) {
                iterator.remove()
                continue
            }
            entry.value.viewerTick(player)
        }
    }

    /** Sends the pasture scan outline to a single viewer only. */
    internal fun emitRadius(world: ServerWorld, box: Box, player: ServerPlayerEntity) {
        val minX = box.minX
        val minY = box.minY
        val minZ = box.minZ
        val maxX = box.maxX
        val maxY = box.maxY
        val maxZ = box.maxZ

        for (x in minX.toInt()..maxX.toInt()) {
            spawn(world, player, x + 0.5, minY + 0.1, minZ + 0.5)
            spawn(world, player, x + 0.5, minY + 0.1, maxZ + 0.5)
            spawn(world, player, x + 0.5, maxY + 0.1, minZ + 0.5)
            spawn(world, player, x + 0.5, maxY + 0.1, maxZ + 0.5)
        }
        for (z in minZ.toInt()..maxZ.toInt()) {
            spawn(world, player, minX + 0.5, minY + 0.1, z + 0.5)
            spawn(world, player, maxX + 0.5, minY + 0.1, z + 0.5)
            spawn(world, player, minX + 0.5, maxY + 0.1, z + 0.5)
            spawn(world, player, maxX + 0.5, maxY + 0.1, z + 0.5)
        }
        for (y in minY.toInt()..maxY.toInt() step 2) {
            spawn(world, player, minX + 0.5, y + 0.1, minZ + 0.5)
            spawn(world, player, maxX + 0.5, y + 0.1, minZ + 0.5)
            spawn(world, player, minX + 0.5, y + 0.1, maxZ + 0.5)
            spawn(world, player, maxX + 0.5, y + 0.1, maxZ + 0.5)
        }
    }

    private fun spawn(world: ServerWorld, player: ServerPlayerEntity, x: Double, y: Double, z: Double) {
        world.spawnParticles(player, ParticleTypes.END_ROD, true, x, y, z, 1, 0.0, 0.0, 0.0, 0.0)
    }
}

/**
 * Read-only 9x3 container handler. All slot interactions are cancelled; clicks on control slots
 * are dispatched as server-side actions by [rebuild]'s slot layout.
 */
class PastureGuiScreenHandler private constructor(
    syncId: Int,
    playerInventory: PlayerInventory,
    private val display: SimpleInventory,
    private val pasture: PokemonPastureBlockEntity
) : GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X3, syncId, playerInventory, display, 3) {

    private var showRadius = false
    private var tickCounter = 0

    override fun onSlotClick(slotIndex: Int, button: Int, clickType: SlotActionType, player: PlayerEntity) {
        when (slotIndex) {
            TOGGLE_SLOT -> {
                showRadius = !showRadius
                rebuild(player)
                sendContentUpdates()
            }
            RELOAD_SLOT -> {
                if (player.hasPermissionLevel(2)) {
                    try {
                        PastureWorkersConfig.load()
                    } catch (e: Exception) {
                        PastureWorkers.LOGGER.error("[PastureWorkers] Failed to reload configuration", e)
                    }
                    rebuild(player)
                    sendContentUpdates()
                }
            }
            // Every other slot is display-only; intentionally do nothing.
            else -> Unit
        }
        setCursorStack(ItemStack.EMPTY)
    }

    override fun quickMove(player: PlayerEntity, slotIndex: Int): ItemStack = ItemStack.EMPTY

    override fun canInsertIntoSlot(stack: ItemStack, slot: Slot): Boolean = false

    override fun canInsertIntoSlot(slot: Slot): Boolean = false

    /** Refreshes button state, worker info and watchdog status. Called on open, on click and periodically. */
    fun rebuild(player: PlayerEntity) {
        for (i in 0 until display.size()) {
            display.setStack(i, filler())
        }

        val general = PastureWorkersConfig.config.general
        val pos = pasture.pos
        val session = (pasture.world as? ServerWorld)?.let { PastureWorkManager.session(it, pos) }
        val chestCount = session?.chests?.size ?: 0

        display.setStack(
            INFO_SLOT,
            named(
                Items.COMPASS,
                "Pasture Info",
                listOf(
                    "Coordinates: ${pos.x}, ${pos.y}, ${pos.z}",
                    "Search radius X/Z: ${general.searchRadius}",
                    "Search height Y: ${general.searchHeight}",
                    "Tick interval: ${general.workIntervalTicks} ticks",
                    "Linked chests: $chestCount"
                )
            )
        )

        display.setStack(
            TOGGLE_SLOT,
            named(
                Items.LEVER,
                if (showRadius) "Radius Particles: ON" else "Radius Particles: OFF",
                listOf("Click to toggle the scan outline for you")
            )
        )

        val workers = PastureWorkersAccess.tetheredPokemon(pasture)
        val workerSlots = WORKER_SLOT_END - WORKER_SLOT_START + 1
        for ((index, pokemon) in workers.take(workerSlots).withIndex()) {
            display.setStack(WORKER_SLOT_START + index, workerStack(pokemon))
        }

        if (player.hasPermissionLevel(2)) {
            display.setStack(
                RELOAD_SLOT,
                named(Items.COMMAND_BLOCK, "Reload Config", listOf("Reloads pastureworkers.json"))
            )
        } else {
            display.setStack(
                RELOAD_SLOT,
                named(Items.BARRIER, "Reload Config", listOf("Requires operator permission"))
            )
        }

        if (HookStatus.tickHookActive) {
            display.setStack(
                WATCHDOG_SLOT,
                named(Items.LIME_DYE, "Watchdog: active", listOf("Pasture tick hook is attached"))
            )
        } else {
            display.setStack(
                WATCHDOG_SLOT,
                named(Items.RED_DYE, "Watchdog: NOT ATTACHED", listOf("Pasture tick hook has not run"))
            )
        }
    }

    /** Periodic per-viewer refresh and particle pump, driven by [PastureGui]. */
    fun viewerTick(player: ServerPlayerEntity) {
        val world = pasture.world as? ServerWorld
        if (world == null || pasture.isRemoved) {
            player.closeHandledScreen()
            return
        }

        tickCounter++
        if (showRadius && tickCounter % PARTICLE_INTERVAL == 0) {
            val general = PastureWorkersConfig.config.general
            val pos = pasture.pos
            val min = pos.add(-general.searchRadius, -general.searchHeight, -general.searchRadius)
            val max = pos.add(general.searchRadius, general.searchHeight, general.searchRadius)
            val box = Box(
                Vec3d(min.x.toDouble(), min.y.toDouble(), min.z.toDouble()),
                Vec3d(max.x + 1.0, max.y + 1.0, max.z + 1.0)
            )
            PastureGui.emitRadius(world, box, player)
        }

        if (tickCounter % REFRESH_INTERVAL == 0) {
            rebuild(player)
            sendContentUpdates()
        }
    }

    private fun workerStack(pokemon: Pokemon): ItemStack {
        val lines = mutableListOf(
            "Species: ${pokemon.species.translatedName.string}",
            "Level: ${pokemon.level}"
        )

        val job = firstEligibleJob(pokemon)
        if (job == null) {
            lines.add("Job: none")
            lines.add("Stat: -")
            lines.add("Efficiency: 0%")
            lines.add("Bonus-drop chance: 0%")
        } else {
            val stat = job.relevantStat
            val efficiency = EfficiencyCalculator.compute(pokemon, stat)
            val percent = (efficiency * 100).roundToInt()
            lines.add("Job: ${job.jobType.name}")
            lines.add("Stat: ${stat.showdownId} ${pokemon.getStat(stat)}")
            lines.add("Efficiency: $percent%")
            lines.add("Bonus-drop chance: $percent%")
        }

        return named(Items.PLAYER_HEAD, pokemon.species.translatedName.string, lines)
    }

    private fun firstEligibleJob(pokemon: Pokemon): WorkJob? {
        val entity = pokemon.entity ?: return null
        for (job in JobRegistry.jobs) {
            if (Eligibility.isEligible(entity, PastureWorkersConfig.groupFor(job.jobType))) return job
        }
        return null
    }

    private fun filler(): ItemStack = named(Items.GRAY_STAINED_GLASS_PANE, " ", emptyList())

    private fun named(item: Item, name: String, lore: List<String>): ItemStack {
        val stack = ItemStack(item)
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name).styled { it.withItalic(false) })
        if (lore.isNotEmpty()) {
            stack.set(
                DataComponentTypes.LORE,
                LoreComponent(lore.map { Text.literal(it).styled { style -> style.withItalic(false) } })
            )
        }
        return stack
    }

    companion object {
        const val INFO_SLOT = 4
        const val TOGGLE_SLOT = 8
        const val WORKER_SLOT_START = 9
        const val WORKER_SLOT_END = 17
        const val RELOAD_SLOT = 18
        const val WATCHDOG_SLOT = 20

        private const val PARTICLE_INTERVAL = 10
        private const val REFRESH_INTERVAL = 20

        fun create(
            syncId: Int,
            playerInventory: PlayerInventory,
            pasture: PokemonPastureBlockEntity
        ): PastureGuiScreenHandler =
            PastureGuiScreenHandler(syncId, playerInventory, SimpleInventory(27), pasture)
    }
}
