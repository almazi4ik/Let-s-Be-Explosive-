package com.letsbeexplosive.mixin;

import com.letsbeexplosive.ProgressiveExplosion;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Creeper.class)
public class CreeperMixin {
    @Inject(method = "explodeCreeper", at = @At("HEAD"), cancellable = true)
    private void letsBeExplosive$explode(CallbackInfo ci) {
        Creeper creeper = (Creeper) (Object) this;

        if (creeper.level() instanceof ServerLevel serverLevel) {
            double x = creeper.getX();
            double y = creeper.getY();
            double z = creeper.getZ();

            serverLevel.playSound(
                null, x, y, z,
                SoundEvents.CREEPER_PRIMED,
                SoundSource.HOSTILE,
                4.0F, 1.0F
            );

            serverLevel.sendParticles(
                ParticleTypes.EXPLOSION_EMITTER,
                x, y, z,
                1, 0.0, 0.0, 0.0, 0.0
            );

            ProgressiveExplosion.start(serverLevel, x, y, z);

            creeper.discard();
            ci.cancel();
        }
    }
}
