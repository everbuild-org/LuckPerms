package me.lucko.luckperms.minestom;

import java.nio.file.Path;
import me.lucko.luckperms.common.config.generic.adapter.ConfigurateConfigAdapter;
import me.lucko.luckperms.common.config.generic.adapter.ConfigurationAdapter;
import me.lucko.luckperms.common.plugin.LuckPermsPlugin;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.hocon.HoconConfigurationLoader;
import ninja.leaping.configurate.loader.ConfigurationLoader;

public final class HoconConfigurationAdapter extends ConfigurateConfigAdapter implements ConfigurationAdapter {

    public HoconConfigurationAdapter(LuckPermsPlugin plugin) {
        super(plugin, ((LPMinestomPlugin) plugin).resolveConfig("luckperms.conf"));
    }

    @Override
    protected ConfigurationLoader<? extends ConfigurationNode> createLoader(Path path) {
        return HoconConfigurationLoader.builder().setPath(path).build();
    }

}
