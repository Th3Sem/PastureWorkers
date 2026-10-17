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

import accieo.pastureworkers.api.JobRegistry
import accieo.pastureworkers.api.JobType
import accieo.pastureworkers.api.WorkJob
import accieo.pastureworkers.config.PastureWorkersConfig
import accieo.pastureworkers.core.EfficiencyCalculator
import accieo.pastureworkers.core.PastureSession
import accieo.pastureworkers.core.PastureWorkManager
import accieo.pastureworkers.core.PastureWorkersAccess
import accieo.pastureworkers.core.work.JobSelection
import com.cobblemon.mod.common.api.pokemon.stats.Stat
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
import com.cobblemon.mod.common.item.PokemonItem
import com.cobblemon.mod.common.pokemon.Pokemon
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
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
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/**
 * Server-side, read-only "virtual chest" that shows pasture diagnostics to players.
 *
 * The GUI is a vanilla 9x3 [GenericContainerScreenHandler] backed by a [SimpleInventory]. Every
 * slot is display-only: [PastureGuiScreenHandler] cancels all slot interactions, so no item can
 * be taken, moved, inserted or shift-clicked. The only interactive slot is the player-local
 * particle toggle; it performs no inventory action.
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
        // Drop the per-player cooldown entry when a player leaves so it cannot leak for the session.
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ -> lastOpenTimes.remove(handler.player.uuid) }
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
                handler.rebuild()
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
 * Read-only 9x3 container handler. All slot interactions are cancelled; the particle toggle slot
 * toggles a per-viewer particle overlay only and never moves items.
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
        if (slotIndex == TOGGLE_SLOT) {
            showRadius = !showRadius
            rebuild()
            sendContentUpdates()
        }
        // Every other slot is display-only; intentionally do nothing.
        setCursorStack(ItemStack.EMPTY)
    }

    override fun quickMove(player: PlayerEntity, slotIndex: Int): ItemStack = ItemStack.EMPTY

    override fun canInsertIntoSlot(stack: ItemStack, slot: Slot): Boolean = false

    override fun canInsertIntoSlot(slot: Slot): Boolean = false

    /** Rebuilds every slot from live pasture state. Called on open, on toggle and every 20 ticks. */
    fun rebuild() {
        for (i in 0 until display.size()) {
            display.setStack(i, filler())
        }
        // Row 3 is purely decorative: a non-interactive divider with no action.
        for (i in ROW3_START..ROW3_END) {
            display.setStack(i, decorative())
        }

        val general = PastureWorkersConfig.config.general
        val pos = pasture.pos
        val world = pasture.world as? ServerWorld
        val session = world?.let { PastureWorkManager.session(it, pos) }
        val workers = PastureWorkersAccess.tetheredPokemon(pasture)

        display.setStack(INFO_SLOT, infoStack(pos, general, session, world, workers))
        display.setStack(TOGGLE_SLOT, toggleStack())

        val workerSlots = WORKER_SLOT_END - WORKER_SLOT_START + 1
        for ((index, pokemon) in workers.take(workerSlots).withIndex()) {
            display.setStack(WORKER_SLOT_START + index, workerStack(pokemon, session, world))
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
            rebuild()
            sendContentUpdates()
        }
    }

    // --- Slot content builders -----------------------------------------------------------------

    private fun infoStack(
        pos: net.minecraft.util.math.BlockPos,
        general: PastureWorkersConfig.GeneralGroup,
        session: PastureSession?,
        world: ServerWorld?,
        workers: List<Pokemon>
    ): ItemStack {
        val lore = mutableListOf<String>()
        lore.add("Coordinates: ${pos.x}, ${pos.y}, ${pos.z}")
        lore.add("Linked containers: ${session?.chests?.size ?: 0}")
        lore.add("State: ${if (isActive(workers, session, world)) "Active" else "Idle"}")

        val resources = resourceEntries(session, world)
        if (resources.isEmpty()) {
            lore.add("Valid resources found: none")
        } else {
            lore.add("Valid resources found:")
            resources.forEach { lore.add("  $it") }
        }

        lore.add("Search radius X/Z: ${general.searchRadius}")
        lore.add("Search height Y: ${general.searchHeight}")
        return named(Items.COMPASS, "Pasture Info", lore)
    }

    private fun toggleStack(): ItemStack = named(
        Items.LEVER,
        if (showRadius) "Radius Particles: ON" else "Radius Particles: OFF",
        listOf("Click to toggle the scan outline for you")
    )

    private fun workerStack(pokemon: Pokemon, session: PastureSession?, world: ServerWorld?): ItemStack {
        val speciesName = pokemon.species.translatedName.string
        val lines = mutableListOf(
            "Species: $speciesName",
            "Level: ${pokemon.level}"
        )

        val entity = pokemon.entity
        val candidates = if (world == null || entity == null) {
            emptyList()
        } else {
            JobSelection.viableJobs(entity, pokemon, session, world)
        }

        if (candidates.isEmpty()) {
            lines.add("Role: None")
            lines.add("No eligible job for this Pokemon right now.")
        } else {
            // Show every viable role; the interval/stat summary scales on the primary (first) one.
            val roles = candidates.joinToString(", ") { JOB_DISPLAY[it.job.jobType] ?: it.job.jobType.name }
            lines.add("Roles: $roles")

            val job = candidates.first().job
            val stat = PastureWorkersConfig.statFor(job)
            val efficiency = EfficiencyCalculator.compute(
                pokemon,
                stat,
                PastureWorkersConfig.groupFor(job.jobType).efficiencyMultiplier
            )
            val baseInterval = PastureWorkersConfig.config.general.workIntervalTicks
            val ticks = EfficiencyCalculator.intervalTicks(baseInterval, efficiency)
            val seconds = ticks / 20.0
            val speedReduction = EfficiencyCalculator.speedReduction(efficiency)
            val multiplier = if (speedReduction >= 1f) 0f else 1f / (1f - speedReduction)

            lines.add("Action Interval: every ${format1(seconds)}s ($ticks ticks)")

            val base = baseStat(pokemon, stat)
            val iv = pokemon.ivs.getOrDefault(stat)
            val ev = pokemon.evs.getOrDefault(stat)
            lines.add("Scaling Stat: ${stat.displayName.string} (Base: $base, IV: $iv, EV: $ev)")

            lines.add(
                "Speed Multiplier: x${format2(multiplier)} (+${(speedReduction * 100f).roundToInt()}% speed)"
            )
            lines.add("Extra Yield Chance: +${(efficiency * 100f).roundToInt()}%")
        }

        return applyName(pokemonIcon(pokemon, candidates.firstOrNull()?.job), speciesName, lines)
    }

    // --- Live-state helpers (mirror the manager) ------------------------------------------------

    /** True when at least one tethered, non-fainted worker has a viable job right now. */
    private fun isActive(workers: List<Pokemon>, session: PastureSession?, world: ServerWorld?): Boolean {
        if (world == null) return false
        for (pokemon in workers) {
            if (pokemon.isFainted()) continue
            val entity = pokemon.entity ?: continue
            if (JobSelection.isActive(entity, pokemon, session, world)) return true
        }
        return false
    }

    /** Per-job count of currently cached, still-valid block targets, labeled by resource. */
    private fun resourceEntries(session: PastureSession?, world: ServerWorld?): List<String> {
        if (session == null || world == null) return emptyList()
        val entries = mutableListOf<String>()
        for (job in JobRegistry.jobs) {
            if (!job.needsBlockTargets) continue
            val targets = session.cache[job.jobType] ?: continue
            val live = targets.count { job.matches(world.getBlockState(it), world, it) }
            if (live > 0) {
                entries.add("${JOB_RESOURCE[job.jobType] ?: job.jobType.name}: $live")
            }
        }
        return entries
    }

    private fun baseStat(pokemon: Pokemon, stat: Stat): Int =
        pokemon.form.baseStats[stat] ?: pokemon.species.baseStats[stat] ?: 0

    // --- Icons ----------------------------------------------------------------------------------

    /**
     * Cobblemon sprite icon. Verified overload: `PokemonItem.from(Pokemon)`. The species/aspects are
     * stored in the `cobblemon:pokemon_item` component and sync through the vanilla container stream,
     * so the client renders the sprite. Falls back to a themed item only if the native icon is empty.
     */
    private fun pokemonIcon(pokemon: Pokemon, job: WorkJob?): ItemStack {
        val native = runCatching { PokemonItem.from(pokemon) }.getOrNull()
        if (native != null && !native.isEmpty) return native
        return ItemStack(fallbackItem(pokemon, job))
    }

    private fun fallbackItem(pokemon: Pokemon, job: WorkJob?): Item {
        job?.let { JOB_ICON[it.jobType] }?.let { return it }
        val type = pokemon.types.firstOrNull()?.name?.uppercase(Locale.ROOT)
        return TYPE_ICON[type] ?: Items.WHITE_DYE
    }

    // --- Stack helpers --------------------------------------------------------------------------

    private fun filler(): ItemStack = named(Items.GRAY_STAINED_GLASS_PANE, " ", emptyList())

    private fun decorative(): ItemStack = named(Items.BLACK_STAINED_GLASS_PANE, " ", emptyList())

    private fun named(item: Item, name: String, lore: List<String>): ItemStack =
        applyName(ItemStack(item), name, lore)

    private fun applyName(stack: ItemStack, name: String, lore: List<String>): ItemStack {
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name).styled { it.withItalic(false) })
        if (lore.isNotEmpty()) {
            stack.set(
                DataComponentTypes.LORE,
                LoreComponent(lore.map { Text.literal(it).styled { style -> style.withItalic(false) } })
            )
        }
        return stack
    }

    private fun format1(value: Double): String = String.format(Locale.ROOT, "%.1f", value)

    private fun format2(value: Float): String = String.format(Locale.ROOT, "%.2f", value)

    companion object {
        const val INFO_SLOT = 4
        const val TOGGLE_SLOT = 8
        const val WORKER_SLOT_START = 9
        const val WORKER_SLOT_END = 17
        const val ROW3_START = 18
        const val ROW3_END = 26

        private const val PARTICLE_INTERVAL = 10
        private const val REFRESH_INTERVAL = 20

        /** Human-readable role names shown per worker. */
        private val JOB_DISPLAY: Map<JobType, String> = mapOf(
            JobType.CROP_HARVEST to "Crop Harvester",
            JobType.CROP_IRRIGATION to "Irrigator",
            JobType.AMETHYST_HARVEST to "Amethyst Miner",
            JobType.GEM_HARVEST to "Gem Miner",
            JobType.APRICORN_HARVEST to "Apricorn Picker",
            JobType.BERRY_HARVEST to "Berry Picker",
            JobType.MINT_HARVEST to "Mint Gardener",
            JobType.NETHERWART_HARVEST to "Nether Wart Farmer",
            JobType.TUMBLESTONE_HARVEST to "Tumblestone Miner",
            JobType.HONEY_COLLECT to "Beekeeper",
            JobType.GROUND_ITEM_GATHER to "Item Collector"
        )

        /** Resource labels for the "Valid resources found" header list. */
        private val JOB_RESOURCE: Map<JobType, String> = mapOf(
            JobType.CROP_HARVEST to "Crops",
            JobType.CROP_IRRIGATION to "Dry Crops",
            JobType.AMETHYST_HARVEST to "Amethyst",
            JobType.GEM_HARVEST to "Gems",
            JobType.APRICORN_HARVEST to "Apricorns",
            JobType.BERRY_HARVEST to "Berries",
            JobType.MINT_HARVEST to "Mint",
            JobType.NETHERWART_HARVEST to "Nether Wart",
            JobType.TUMBLESTONE_HARVEST to "Tumblestone",
            JobType.HONEY_COLLECT to "Honey",
            JobType.GROUND_ITEM_GATHER to "Ground Items"
        )

        /** Fallback icon per job, used only when the native Cobblemon sprite is unavailable. */
        private val JOB_ICON: Map<JobType, Item> = mapOf(
            JobType.CROP_HARVEST to Items.GOLDEN_HOE,
            JobType.CROP_IRRIGATION to Items.WATER_BUCKET,
            JobType.AMETHYST_HARVEST to Items.AMETHYST_SHARD,
            JobType.GEM_HARVEST to Items.DIAMOND,
            JobType.APRICORN_HARVEST to Items.SWEET_BERRIES,
            JobType.BERRY_HARVEST to Items.SWEET_BERRIES,
            JobType.MINT_HARVEST to Items.LILY_OF_THE_VALLEY,
            JobType.NETHERWART_HARVEST to Items.NETHER_WART,
            JobType.TUMBLESTONE_HARVEST to Items.COBBLESTONE,
            JobType.HONEY_COLLECT to Items.HONEYCOMB,
            JobType.GROUND_ITEM_GATHER to Items.CHEST
        )

        /** Fallback icon per primary elemental type, used when no job icon applies. */
        private val TYPE_ICON: Map<String, Item> = mapOf(
            "NORMAL" to Items.WHITE_DYE,
            "FIRE" to Items.ORANGE_DYE,
            "WATER" to Items.LIGHT_BLUE_DYE,
            "ELECTRIC" to Items.YELLOW_DYE,
            "GRASS" to Items.LIME_DYE,
            "ICE" to Items.CYAN_DYE,
            "FIGHTING" to Items.RED_DYE,
            "POISON" to Items.PURPLE_DYE,
            "GROUND" to Items.BROWN_DYE,
            "FLYING" to Items.LIGHT_GRAY_DYE,
            "PSYCHIC" to Items.PINK_DYE,
            "BUG" to Items.LIME_DYE,
            "ROCK" to Items.BROWN_DYE,
            "GHOST" to Items.PURPLE_DYE,
            "DRAGON" to Items.BLUE_DYE,
            "DARK" to Items.BLACK_DYE,
            "STEEL" to Items.GRAY_DYE,
            "FAIRY" to Items.PINK_DYE
        )

        fun create(
            syncId: Int,
            playerInventory: PlayerInventory,
            pasture: PokemonPastureBlockEntity
        ): PastureGuiScreenHandler =
            PastureGuiScreenHandler(syncId, playerInventory, SimpleInventory(27), pasture)
    }
}