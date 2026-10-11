/*
 * Copyright (c) 2026 Accieo
 * Copyright (c) 2026 Th3Sem
 *
 * SPDX-License-Identifier: MPL-2.0
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package accieo.pastureworkers.mixin;

import accieo.pastureworkers.PastureWorkers;
import accieo.pastureworkers.core.HookStatus;
import accieo.pastureworkers.core.PastureWorkManager;
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PokemonPastureBlockEntity.class)
public class PokemonPastureBlockEntityMixin {

    // require = 0: if a future Cobblemon changes the synthetic ticker lambda, the mod degrades
    // gracefully instead of failing at class-transformation time. HookStatus warns once if the
    // hook never runs.
    @Inject(method = "TICKER$lambda$0", at = @At("TAIL"), require = 0)
    private static void onPastureTick(World world, BlockPos pos, BlockState state, PokemonPastureBlockEntity blockEntity, CallbackInfo ci) {
        if (world.isClient) return;

        HookStatus.INSTANCE.markTickHookSeen();
        try {
            PastureWorkManager.INSTANCE.serverTick((ServerWorld) world, pos, blockEntity);
        } catch (Exception e) {
            PastureWorkers.INSTANCE.getLOGGER().error("[PastureWorkers] Error in PastureWorkManager.serverTick", e);
        }
    }

    @Inject(method = "onBroken()V", at = @At("TAIL"), remap = false, require = 0)
    private void onPastureBroken(CallbackInfo ci) {
        PokemonPastureBlockEntity self = (PokemonPastureBlockEntity) (Object) this;
        World world = self.getWorld();
        if (world != null && !world.isClient) {
            PastureWorkManager.INSTANCE.removePasture((ServerWorld) world, self.getPos());
        }
    }
}
