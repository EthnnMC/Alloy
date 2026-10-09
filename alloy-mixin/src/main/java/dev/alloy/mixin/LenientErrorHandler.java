package dev.alloy.mixin;

import org.spongepowered.asm.mixin.extensibility.IMixinConfig;
import org.spongepowered.asm.mixin.extensibility.IMixinErrorHandler;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Keeps the game alive when a mixin cannot be applied. Mixin's default is to stop the game; on
 * Lunar a mixin can fail simply because Lunar already rewrote the code it aims at, so the failing
 * mixin is logged and skipped while the others still apply.
 */
public final class LenientErrorHandler implements IMixinErrorHandler {

    @Override
    public ErrorAction onPrepareError(IMixinConfig config, Throwable error, IMixinInfo mixin, ErrorAction action) {
        return ErrorAction.WARN;
    }

    @Override
    public ErrorAction onApplyError(String targetClassName, Throwable error, IMixinInfo mixin, ErrorAction action) {
        return ErrorAction.WARN;
    }
}
