package me.lucko.luckperms.minestom.configuration;

import java.nio.file.Path;
import me.lucko.luckperms.common.config.generic.adapter.ConfigurateConfigAdapter;
import me.lucko.luckperms.common.config.generic.adapter.ConfigurationAdapter;
import me.lucko.luckperms.common.plugin.LuckPermsPlugin;
import me.lucko.luckperms.minestom.LPMinestomPlugin;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.hocon.HoconConfigurationLoader;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import org.jetbrains.annotations.NotNull;

public final class HoconConfigAdapter extends ConfigurateConfigAdapter implements ConfigurationAdapter {

    public HoconConfigAdapter(LuckPermsPlugin plugin) {
        super(plugin, ((LPMinestomPlugin) plugin).resolveConfig("luckperms.conf"));
    }

    @Override
    protected ConfigurationLoader<? extends ConfigurationNode> createLoader(Path path) {
        return HoconConfigurationLoader.builder().setPath(path).build();
    }

}
