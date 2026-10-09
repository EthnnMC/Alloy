package dev.alloy.remap.io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class UserFoldersTest {

    /** Root of the current drive, so the test reads the same on every system. */
    private static final Path ROOT = Path.of("").toAbsolutePath().getRoot();

    @Test
    void folderStaysInTheUserHomeWhenItsPathHasNoSpace() {
        Path userHome = UserFoldersTest.ROOT.resolve("Users").resolve("Admin");

        assertEquals(userHome.resolve(".alloy"), UserFolders.spaceFree(userHome, ".alloy"));
    }

    @Test
    void folderMovesToTheDriveRootWhenTheUserHomeHasASpace() {
        Path userHome = UserFoldersTest.ROOT.resolve("Users").resolve("John Doe");

        assertEquals(UserFoldersTest.ROOT.resolve(".alloy"), UserFolders.spaceFree(userHome, ".alloy"));
    }

    @Test
    void driveRootFolderIsOnTheDriveOfTheUserHome() {
        Path userHome = UserFoldersTest.ROOT.resolve("Users").resolve("Admin");

        assertEquals(Optional.of(UserFoldersTest.ROOT.resolve(".weave")), UserFolders.atDriveRoot(userHome, ".weave"));
    }
}
