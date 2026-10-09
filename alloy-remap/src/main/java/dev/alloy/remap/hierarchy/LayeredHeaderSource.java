package dev.alloy.remap.hierarchy;

import java.util.List;
import java.util.Optional;

/**
 * Several header sources queried as one, in order: the first one that knows the class answers.
 */
public final class LayeredHeaderSource implements ClassHeaderSource {

    private final List<ClassHeaderSource> layers;

    /**
     * @param layers sources to query, highest priority first
     */
    public LayeredHeaderSource(List<ClassHeaderSource> layers) {
        this.layers = List.copyOf(layers);
    }

    @Override
    public Optional<ClassHeader> find(String internalName) {
        for (ClassHeaderSource layer : this.layers) {
            Optional<ClassHeader> header = layer.find(internalName);
            if (header.isPresent()) {
                return header;
            }
        }
        return Optional.empty();
    }
}
