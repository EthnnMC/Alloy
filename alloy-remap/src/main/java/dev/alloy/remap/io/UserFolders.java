package dev.alloy.remap.io;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Chooses where a per-user folder such as {@code .alloy} lives. Shared by the agent and the
 * developer tools so both look in the same place.
 */
public final class UserFolders {

    private UserFolders() {
    }

    /**
     * Returns {@code <user home>/<name>}, or {@code <drive root>/<name>} when the user home has a
     * space in its path. The agent jar sits in that folder and its path is typed as a JVM argument in
     * the Lunar launcher, where a space would cut it in two; Weave's installation guide uses the same
     * rule ({@code C:\.weave}).
     *
     * @param userHome the user's home folder
     * @param name     folder name, for example {@code .alloy}
     */
    public static Path spaceFree(Path userHome, String name) {
        return UserFolders.atDriveRoot(userHome, name)
                .filter(root -> userHome.toString().indexOf(' ') >= 0)
                .orElse(userHome.resolve(name));
    }

    /**
     * Returns {@code <drive root>/<name>} for the drive of the user home, or empty if the path has
     * no root.
     */
    public static Optional<Path> atDriveRoot(Path userHome, String name) {
        Path root = userHome.toAbsolutePath().getRoot();
        return root == null ? Optional.empty() : Optional.of(root.resolve(name));
    }
}
