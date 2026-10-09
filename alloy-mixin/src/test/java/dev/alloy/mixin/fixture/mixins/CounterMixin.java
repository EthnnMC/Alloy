package dev.alloy.mixin.fixture.mixins;

import dev.alloy.mixin.fixture.Counter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The same shape as a typical one-line mod mixin. */
@Mixin(Counter.class)
public abstract class CounterMixin {

    @Shadow
    private int value;

    @Inject(method = "click", at = @At("HEAD"))
    private void onClick(CallbackInfo callback) {
        this.value = 0;
    }
}
