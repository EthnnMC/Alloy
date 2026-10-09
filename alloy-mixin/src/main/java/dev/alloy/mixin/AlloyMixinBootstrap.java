package dev.alloy.mixin;

import org.spongepowered.asm.service.IMixinServiceBootstrap;

/** Tells Mixin which service to use; found through {@code META-INF/services}. */
public final class AlloyMixinBootstrap implements IMixinServiceBootstrap {

    @Override
    public String getName() {
        return "Alloy";
    }

    @Override
    public String getServiceClassName() {
        return AlloyMixinService.class.getName();
    }

    @Override
    public void bootstrap() {
    }
}
