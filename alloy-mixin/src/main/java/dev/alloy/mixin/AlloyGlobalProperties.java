package dev.alloy.mixin;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.spongepowered.asm.service.IGlobalPropertyService;
import org.spongepowered.asm.service.IPropertyKey;

/** The shared key/value store Mixin keeps its state in; here a plain map owned by the host. */
public final class AlloyGlobalProperties implements IGlobalPropertyService {

    private final Map<String, Object> values = new ConcurrentHashMap<>();

    @Override
    public IPropertyKey resolveKey(String name) {
        return new Key(name);
    }

    @Override
    @SuppressWarnings("unchecked") // the caller decides the type, as in Mixin's own implementations
    public <T> T getProperty(IPropertyKey key) {
        return (T) this.values.get(AlloyGlobalProperties.nameOf(key));
    }

    @Override
    public void setProperty(IPropertyKey key, Object value) {
        if (value == null) {
            this.values.remove(AlloyGlobalProperties.nameOf(key));
        } else {
            this.values.put(AlloyGlobalProperties.nameOf(key), value);
        }
    }

    @Override
    public <T> T getProperty(IPropertyKey key, T defaultValue) {
        T value = this.getProperty(key);
        return value == null ? defaultValue : value;
    }

    @Override
    public String getPropertyString(IPropertyKey key, String defaultValue) {
        Object value = this.values.get(AlloyGlobalProperties.nameOf(key));
        return value == null ? defaultValue : value.toString();
    }

    private static String nameOf(IPropertyKey key) {
        return ((Key) key).name();
    }

    private record Key(String name) implements IPropertyKey {
    }
}
