package me.lucko.luckperms.minestom;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import me.lucko.luckperms.common.config.generic.adapter.ConfigurationAdapter;
import me.lucko.luckperms.common.config.generic.adapter.EnvironmentVariableConfigAdapter;
import me.lucko.luckperms.minestom.context.ContextProvider;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LuckPermsMinestom {

    private static final Logger LOGGER = LoggerFactory.getLogger(LuckPermsMinestom.class);

    private static LPMinestomBootstrap bootstrap = null;

    private LuckPermsMinestom() {}

    public static @NotNull Builder builder(@NotNull Path dataDirectory) {
        if (bootstrap != null) throw new RuntimeException("Cannot initialize LuckPerms Minestom - it is already initialized!");
        return new BuilderImpl(dataDirectory.toAbsolutePath());
    }

    public static void disable() {
        if (bootstrap == null) throw new RuntimeException("Cannot disable LuckPerms Minestom - it is not initialized!");
        bootstrap.onDisable();
        bootstrap = null;
    }

    public interface Builder {
        /**
         * Sets whether the LuckPerms commands should be registered
         * @param enabled if the commands should be registered
         * @return the builder instance
         */
        @NotNull Builder commands(boolean enabled);


        /**
         * Adds a context provider to the platform
         * @param provider the provider to add
         * @return the builder instance
         */
        @NotNull Builder contextProvider(@NotNull ContextProvider provider);

        /**
         * Adds a collection of context providers to the platform
         * @param providers the providers to add
         * @return the builder instance
         */
        @NotNull Builder contextProviders(@NotNull ContextProvider... providers);

        /**
         * Adds a collection of context providers to the platform
         * @param providers the providers to add
         * @return the builder instance
         */
        @NotNull Builder contextProviders(@NotNull Iterable<ContextProvider> providers);


        /**
         * Suggests a permission to be registered with the platform
         * @param permission the permission to suggest
         * @return the builder instance
         */
        @NotNull Builder permissionSuggestion(@NotNull String permission);

        /**
         * Suggests a collection of permissions to be registered with the platform
         * @param permissions the permissions to suggest
         * @return the builder instance
         */
        @NotNull Builder permissionSuggestions(@NotNull String... permissions);

        /**
         * Suggests a collection of permissions to be registered with the platform
         * @param permissions the permissions to suggest
         * @return the builder instance
         */
        @NotNull Builder permissionSuggestions(@NotNull Iterable<String> permissions);


        /**
         * Sets the configuration adapter to use. Provided options are:
         * - {@link me.lucko.luckperms.common.config.generic.adapter.EnvironmentVariableConfigAdapter}
         * - {@link me.lucko.luckperms.common.config.generic.adapter.MultiConfigurationAdapter}
         * - {@link me.lucko.luckperms.common.config.generic.adapter.SystemPropertyConfigAdapter}
         * - {@link me.lucko.luckperms.common.config.generic.adapter.ConfigurateConfigAdapter}
         * @param adapter the adapter to use
         * @return the builder instance
         */
        @NotNull Builder configurationAdapter(@NotNull Function<LPMinestomPlugin, ConfigurationAdapter> adapter);


        /**
         * Enables the dependency manager, which will automatically download and load LuckPerms dependencies
         * during runtime.
         * @param enabled if the dependency manager should be enabled
         * @return the builder instance
         */
        @NotNull Builder dependencyManager(boolean enabled);


        /**
         * Enables LuckPerms
         * @return the LuckPerms instance
         */
        @NotNull LuckPerms enable();
    }

    private static class BuilderImpl implements Builder {
        private final @NotNull Set<ContextProvider> contextProviders = new HashSet<>();
        private final @NotNull Set<String> permissionSuggestions = new HashSet<>();

        private final Path dataDirectory;
        private boolean commands = true;
        private @NotNull Function<LPMinestomPlugin, ConfigurationAdapter> configurationAdapter = EnvironmentVariableConfigAdapter::new;
        private boolean dependencyManager = false;

        private BuilderImpl(@NotNull Path dataDirectory) {
            this.dataDirectory = dataDirectory;
        }

        @Override
        public @NotNull Builder commands(boolean enabled) {
            this.commands = enabled;
            return this;
        }

        @Override
        public @NotNull Builder contextProvider(@NotNull ContextProvider provider) {
            this.contextProviders.add(provider);
            return this;
        }

        @Override
        public @NotNull Builder contextProviders(@NotNull ContextProvider... providers) {
            return this.contextProviders(Arrays.asList(providers));
        }

        @Override
        public @NotNull Builder contextProviders(@NotNull Iterable<ContextProvider> providers) {
            providers.forEach(this.contextProviders::add);
            return this;
        }

        @Override
        public @NotNull Builder permissionSuggestion(@NotNull String permission) {
            this.permissionSuggestions.add(permission);
            return this;
        }

        @Override
        public @NotNull Builder permissionSuggestions(@NotNull String... permissions) {
            return this.permissionSuggestions(Arrays.asList(permissions));
        }

        @Override
        public @NotNull Builder permissionSuggestions(@NotNull Iterable<String> permissions) {
            permissions.forEach(this.permissionSuggestions::add);
            return this;
        }

        @Override
        public @NotNull Builder configurationAdapter(@NotNull Function<LPMinestomPlugin, ConfigurationAdapter> adapter) {
            this.configurationAdapter = adapter;
            return this;
        }

        @Override
        public @NotNull Builder dependencyManager(boolean enabled) {
            this.dependencyManager = enabled;
            return this;
        }

        @Override
        public @NotNull LuckPerms enable() {
            bootstrap = new LPMinestomBootstrap(LOGGER, dataDirectory, this.contextProviders, this.configurationAdapter, this.dependencyManager, this.permissionSuggestions, this.commands);
            bootstrap.onEnable();
            return LuckPermsProvider.get();
        }
    }

}
