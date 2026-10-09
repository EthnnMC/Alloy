package dev.alloy.remap.mapping;

/**
 * Record counts of a {@code .kin} file, for diagnostics and tests.
 *
 * @param topLevelClasses number of top-level classes
 * @param innerClasses    number of inner classes, at all nesting levels
 * @param fields          number of renamed fields
 * @param methods         number of renamed methods
 */
public record KinSummary(int topLevelClasses, int innerClasses, int fields, int methods) {

    /**
     * Adds two summaries component-wise.
     */
    public KinSummary plus(KinSummary other) {
        return new KinSummary(
                this.topLevelClasses + other.topLevelClasses,
                this.innerClasses + other.innerClasses,
                this.fields + other.fields,
                this.methods + other.methods);
    }
}
