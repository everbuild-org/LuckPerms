package me.lucko.luckperms.minestom;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
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
        return new BuilderImpl(dataDirectory);
    }

    public static void disable() {
        if (bootstrap == null) throw new RuntimeException("Cannot disable LuckPerms Minestom - it is not initialized!");
        bootstrap.onDisable();
        bootstrap = null;
    }

    public interface Builder {
        @NotNull Builder commands(boolean enabled);
        @NotNull Builder enableCommands();
        @NotNull Builder disableCommands();

        @NotNull Builder contextProvider(@NotNull ContextProvider provider);
        @NotNull Builder contextProviders(@NotNull ContextProvider... providers);
        @NotNull Builder contextProviders(@NotNull Iterable<ContextProvider> providers);

        @NotNull LuckPerms enable();
    }

    private static class BuilderImpl implements Builder {
        private final @NotNull Set<ContextProvider> contextProviders = new HashSet<>();

        private final Path dataDirectory;
        private boolean commands = true;

        private BuilderImpl(Path dataDirectory) {
            this.dataDirectory = dataDirectory;
        }

        @Override
        public @NotNull Builder commands(boolean enabled) {
            this.commands = enabled;
            return this;
        }

        @Override
        public @NotNull Builder enableCommands() {
            return this.commands(true);
        }

        @Override
        public @NotNull Builder disableCommands() {
            return this.commands(false);
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
        public @NotNull LuckPerms enable() {
            bootstrap = new LPMinestomBootstrap(LOGGER, dataDirectory, this.contextProviders, this.commands);
            bootstrap.onEnable();
            return LuckPermsProvider.get();
        }
    }

}
