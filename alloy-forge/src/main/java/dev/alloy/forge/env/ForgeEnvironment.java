package dev.alloy.forge.env;

import com.google.common.collect.HashBiMap;
import java.nio.file.Path;
import java.util.HashMap;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.client.FMLClientHandler;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.FMLInjectionData;
import net.minecraftforge.fml.relauncher.FMLRelaunchLog;
import net.minecraftforge.fml.relauncher.Side;
import org.apache.logging.log4j.LogManager;

/**
 * Puts Forge's classes in the state their own startup would have left them in. Real Forge starts
 * through LaunchWrapper, which does not exist under Lunar, so Alloy keeps Forge's classes as they
 * are and fills the few fields they need: the logger, the injection data (game folder, versions)
 * and, once Minecraft exists, the client and the resource pack list.
 */
public final class ForgeEnvironment {

    private static final String FORGE_MAJOR = "11";
    private static final String FORGE_MINOR = "15";
    private static final String FORGE_REVISION = "1";
    private static final String FORGE_BUILD = "2318";
    private static final String MCP_VERSION = "9.19";

    private ForgeEnvironment() {
    }

    /**
     * First step, before any Forge class is used.
     *
     * @param minecraftVersion game version, for example {@code "1.8.9"}
     * @param gameDirectory    game folder
     */
    public static void prepare(String minecraftVersion, Path gameDirectory) {
        // Without these three lines, Forge's first message would replace System.out and System.err.
        Fields.setStatic(FMLRelaunchLog.class, "side", Side.CLIENT);
        Fields.setStatic(FMLRelaunchLog.class, "configured", Boolean.TRUE);
        Fields.set(FMLRelaunchLog.class, FMLRelaunchLog.log, "myLog", LogManager.getLogger("FML"));

        Fields.setStatic(FMLInjectionData.class, "minecraftHome", gameDirectory.toFile());
        Fields.setStatic(FMLInjectionData.class, "major", ForgeEnvironment.FORGE_MAJOR);
        Fields.setStatic(FMLInjectionData.class, "minor", ForgeEnvironment.FORGE_MINOR);
        Fields.setStatic(FMLInjectionData.class, "rev", ForgeEnvironment.FORGE_REVISION);
        Fields.setStatic(FMLInjectionData.class, "build", ForgeEnvironment.FORGE_BUILD);
        Fields.setStatic(FMLInjectionData.class, "mccversion", minecraftVersion);
        Fields.setStatic(FMLInjectionData.class, "mcpversion", ForgeEnvironment.MCP_VERSION);
    }

    /**
     * Second step, as soon as the {@code Minecraft} object exists: plugs in Forge's client side.
     *
     * @param minecraft the game client
     */
    public static void attachClient(Minecraft minecraft) {
        FMLClientHandler clientHandler = FMLClientHandler.instance();
        Fields.set(FMLClientHandler.class, clientHandler, "client", minecraft);
        Fields.set(FMLClientHandler.class, clientHandler, "resourcePackList", minecraft.defaultResourcePacks);
        Fields.set(FMLClientHandler.class, clientHandler, "resourcePackMap", new HashMap<>());
        Fields.set(FMLClientHandler.class, clientHandler, "guiFactories", HashBiMap.create());
        Fields.set(FMLCommonHandler.class, FMLCommonHandler.instance(), "sidedDelegate", clientHandler);
    }

    /** Last step, once the mods are initialised: Forge no longer considers itself "loading". */
    public static void loadingFinished() {
        Fields.set(FMLClientHandler.class, FMLClientHandler.instance(), "loading", Boolean.FALSE);
    }
}
