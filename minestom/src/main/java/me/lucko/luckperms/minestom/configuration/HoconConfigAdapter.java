package me.lucko.luckperms.minestom.configuration;

import java.nio.file.Path;
import me.lucko.luckperms.common.config.generic.adapter.ConfigurateConfigAdapter;
import me.lucko.luckperms.common.config.generic.adapter.ConfigurationAdapter;
import me.lucko.luckperms.common.plugin.LuckPermsPlugin;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.hocon.HoconConfigurationLoader;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import org.jetbrains.annotations.NotNull;

public final class HoconConfigAdapter extends ConfigurateConfigAdapter implements ConfigurationAdapter {

    public HoconConfigAdapter(LuckPermsPlugin plugin, Path path) {
        super(plugin, ensureExists(path.resolve("luckperms.conf")));
    }

    @Override
    protected ConfigurationLoader<? extends ConfigurationNode> createLoader(Path path) {
        return HoconConfigurationLoader.builder().setPath(path).build();
    }

    private static @NotNull Path ensureExists(Path path) {
        if (!path.toFile().exists()) {
            try {
                path.toFile().createNewFile();
            } catch (Exception e) {
                throw new RuntimeException("Unable to create file at " + path, e);
            }
        }
        return path;
    }

}
