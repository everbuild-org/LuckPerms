package me.lucko.luckperms.minestom;

import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LuckPermsMinestom {

    private static final Logger LOGGER = LoggerFactory.getLogger(LuckPermsMinestom.class);

    private static LPMinestomBootstrap bootstrap = null;

    private LuckPermsMinestom() {}

    public static void enable(Path dataDirectory) {
        if (bootstrap != null) throw new RuntimeException("Cannot initialize LuckPerms Minestom - it is already initialized!");
        bootstrap = new LPMinestomBootstrap(LOGGER, dataDirectory);
        bootstrap.onEnable();
    }

    public static void disable() {
        if (bootstrap == null) throw new RuntimeException("Cannot disable LuckPerms Minestom - it is not initialized!");
        bootstrap.onDisable();
        bootstrap = null;
    }

}
