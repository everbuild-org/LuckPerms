package me.lucko.luckperms.minestom;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.function.Function;
import me.lucko.luckperms.common.command.CommandManager;
import me.lucko.luckperms.common.config.generic.adapter.ConfigurationAdapter;
import me.lucko.luckperms.common.plugin.bootstrap.LuckPermsBootstrap;
import me.lucko.luckperms.common.plugin.classpath.ClassPathAppender;
import me.lucko.luckperms.common.plugin.logging.PluginLogger;
import me.lucko.luckperms.common.plugin.logging.Slf4jPluginLogger;
import me.lucko.luckperms.common.plugin.scheduler.SchedulerAdapter;
import me.lucko.luckperms.minestom.context.ContextProvider;
import me.lucko.luckperms.minestom.dependencies.NoopClassPathAppender;
import net.luckperms.api.platform.Platform;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public final class LPMinestomBootstrap implements LuckPermsBootstrap {

    private final Path dataDirectory;
    private final PluginLogger logger;
    private final SchedulerAdapter schedulerAdapter;
    private final ClassPathAppender classPathAppender;
    private final LPMinestomPlugin plugin;

    private final CountDownLatch loadLatch = new CountDownLatch(1);
    private final CountDownLatch enableLatch = new CountDownLatch(1);

    private Instant startTime;

    public LPMinestomBootstrap(
            @NotNull Logger logger,
            @NotNull Path dataDirectory,
            @NotNull Set<ContextProvider> contextProviders,
            @NotNull Function<LPMinestomPlugin, ConfigurationAdapter> configurationAdapter,
            boolean dependencyManager,
            @NotNull Set<String> permissionSuggestions,
            @Nullable CommandRegistry commandRegistry
    ) {
        this.logger = new Slf4jPluginLogger(logger);
        this.dataDirectory = dataDirectory;
        this.schedulerAdapter = new MinestomSchedulerAdapter(this);
        this.classPathAppender = new NoopClassPathAppender();
        this.plugin = new LPMinestomPlugin(this, contextProviders, configurationAdapter, dependencyManager, permissionSuggestions, commandRegistry);

        // we're about to move to a new artifact, we should warn the user
        this.logger.warn("This is the final version of LuckPerms for Minestom on this artifact.");
        this.logger.warn("Please consider moving to the new 'dev.lu15:luckperms-minestom' artifact published on both Hypera and Central repositories.");
        this.logger.warn("This artifact will no longer be updated.");
    }

    public void onEnable() {
        // load
        try {
            this.plugin.load();
        } finally {
            this.loadLatch.countDown();
        }

        // enable
        this.startTime = Instant.now();

        try {
            this.plugin.enable();
        } finally {
            this.enableLatch.countDown();
        }
    }

    public void onDisable() {
        this.plugin.disable();
    }

    @Override
    public PluginLogger getPluginLogger() {
        return this.logger;
    }

    @Override
    public SchedulerAdapter getScheduler() {
        return this.schedulerAdapter;
    }

    @Override
    public ClassPathAppender getClassPathAppender() {
        return this.classPathAppender;
    }

    @Override
    public CountDownLatch getLoadLatch() {
        return this.loadLatch;
    }

    @Override
    public CountDownLatch getEnableLatch() {
        return this.enableLatch;
    }

    @Override
    public String getVersion() {
        return "@VERSION@";
    }

    @Override
    public Instant getStartupTime() {
        return this.startTime;
    }

    @Override
    public Platform.Type getType() {
        return Platform.Type.MINESTOM;
    }

    @Override
    public String getServerBrand() {
        return MinecraftServer.getBrandName();
    }

    @Override
    public String getServerVersion() {
        return MinecraftServer.VERSION_NAME;
    }

    @Override
    public Path getDataDirectory() {
        return this.dataDirectory;
    }

    @Override
    public Optional<Player> getPlayer(UUID uniqueId) {
        return Optional.ofNullable(MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uniqueId));
    }

    @Override
    public Optional<UUID> lookupUniqueId(String username) {
        return Optional.ofNullable(MinecraftServer.getConnectionManager().getOnlinePlayerByUsername(username))
                .map(Player::getUuid);
    }

    @Override
    public Optional<String> lookupUsername(UUID uniqueId) {
        return Optional.ofNullable(MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uniqueId))
                .map(Player::getUsername);
    }

    @Override
    public int getPlayerCount() {
        return MinecraftServer.getConnectionManager().getOnlinePlayerCount();
    }

    @Override
    public Collection<String> getPlayerList() {
        return MinecraftServer.getConnectionManager().getOnlinePlayers().stream()
                .map(Player::getUsername)
                .toList();
    }

    @Override
    public Collection<UUID> getOnlinePlayers() {
        return MinecraftServer.getConnectionManager().getOnlinePlayers().stream()
                .map(Player::getUuid)
                .toList();
    }

    @Override
    public boolean isPlayerOnline(UUID uniqueId) {
        return this.getPlayer(uniqueId)
                .map(Player::isOnline)
                .orElse(false);
    }

}
