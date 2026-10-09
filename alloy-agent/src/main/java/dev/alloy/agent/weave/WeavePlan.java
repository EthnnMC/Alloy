package dev.alloy.agent.weave;

import java.nio.file.Path;

/**
 * What it takes to start Weave once all checks have passed.
 *
 * @param agentJar     the Weave agent jar installed by the user
 * @param premainClass the entry class declared in its manifest
 * @param modCount     number of Weave mods that will be loaded (for the log)
 */
record WeavePlan(Path agentJar, String premainClass, int modCount) {
}
