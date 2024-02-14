package me.lucko.luckperms.minestom.configuration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import me.lucko.luckperms.common.config.generic.adapter.ConfigurationAdapter;
import me.lucko.luckperms.common.plugin.LuckPermsPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class EnvironmentConfigAdapter implements ConfigurationAdapter {

    private final @NotNull LuckPermsPlugin plugin;
    private final @Nullable ConfigurationAdapter parent;

    public EnvironmentConfigAdapter(@NotNull LuckPermsPlugin plugin, @Nullable ConfigurationAdapter parent) {
        this.plugin = plugin;
        this.parent = parent;
    }

    public EnvironmentConfigAdapter(@NotNull LuckPermsPlugin plugin) {
        this(plugin, null);
    }

    private @NotNull String convertPath(@NotNull String path) {
        return path
                .replace('.', '_')
                .replace('-', '_')
                .toUpperCase();
    }

    @Override
    public @NotNull LuckPermsPlugin getPlugin() {
        return this.plugin;
    }

    @Override
    public void reload() {
        if (this.parent != null) this.parent.reload();
    }

    @Override
    public String getString(String path, String def) {
        String env = System.getenv(this.convertPath(path));
        if (env != null) return env;
        return this.parent != null ? this.parent.getString(path, def) : def;
    }

    @Override
    public int getInteger(String path, int def) {
        String env = System.getenv(this.convertPath(path));
        if (env != null) return Integer.parseInt(env);
        return this.parent != null ? this.parent.getInteger(path, def) : def;
    }

    @Override
    public boolean getBoolean(String path, boolean def) {
        String env = System.getenv(this.convertPath(path));
        if (env != null) return Boolean.parseBoolean(env);
        return this.parent != null ? this.parent.getBoolean(path, def) : def;
    }

    @Override
    public List<String> getStringList(String path, List<String> def) {
        String env = System.getenv(this.convertPath(path));
        if (env != null) return List.of(env.split(","));
        return this.parent != null ? this.parent.getStringList(path, def) : def;
    }

    /**
     * Gets a map of string values from the environment.
     * Must be in the format {@code key1,value1,key2,value2,...}.
     * @param path the path to the value
     * @param def the default value
     * @return the value
     */
    @Override
    public Map<String, String> getStringMap(String path, Map<String, String> def) {
        String env = System.getenv(this.convertPath(path));
        if (env != null) {
            String[] split = env.split(",");
            if (split.length % 2 != 0) throw new IllegalArgumentException("Invalid string map format");
            Map<String, String> map = new HashMap<>();
            for (int i = 0; i < split.length; i += 2) map.put(split[i], split[i + 1]);
            return map;
        }
        return this.parent != null ? this.parent.getStringMap(path, def) : def;
    }
}
