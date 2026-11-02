package com.letsbeexplosive.mixin;

import com.letsbeexplosive.ProgressiveExplosion;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.PrimedTnt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PrimedTnt.class)
public class LetsBeExplosiveMixin {
    @Inject(method = "explode", at = @At("HEAD"), cancellable = true)
    private void letsBeExplosive$explode(CallbackInfo ci) {
        PrimedTnt tnt = (PrimedTnt) (Object) this;

        if (tnt.level() instanceof ServerLevel serverLevel) {
            ProgressiveExplosion.start(
                serverLevel, tnt.getX(), tnt.getY(), tnt.getZ()
            );
            ci.cancel();
        }
    }
}
