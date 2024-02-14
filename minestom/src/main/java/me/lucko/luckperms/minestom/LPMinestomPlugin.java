package me.lucko.luckperms.minestom;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import me.lucko.luckperms.common.api.LuckPermsApiProvider;
import me.lucko.luckperms.common.calculator.CalculatorFactory;
import me.lucko.luckperms.common.command.CommandManager;
import me.lucko.luckperms.common.config.ConfigKeys;
import me.lucko.luckperms.common.config.generic.adapter.ConfigurationAdapter;
import me.lucko.luckperms.common.event.AbstractEventBus;
import me.lucko.luckperms.common.messaging.MessagingFactory;
import me.lucko.luckperms.common.model.Group;
import me.lucko.luckperms.common.model.Track;
import me.lucko.luckperms.common.model.User;
import me.lucko.luckperms.common.model.manager.group.GroupManager;
import me.lucko.luckperms.common.model.manager.group.StandardGroupManager;
import me.lucko.luckperms.common.model.manager.track.StandardTrackManager;
import me.lucko.luckperms.common.model.manager.track.TrackManager;
import me.lucko.luckperms.common.model.manager.user.StandardUserManager;
import me.lucko.luckperms.common.model.manager.user.UserManager;
import me.lucko.luckperms.common.plugin.AbstractLuckPermsPlugin;
import me.lucko.luckperms.common.plugin.LuckPermsPlugin;
import me.lucko.luckperms.common.plugin.bootstrap.LuckPermsBootstrap;
import me.lucko.luckperms.common.plugin.util.AbstractConnectionListener;
import me.lucko.luckperms.common.sender.Sender;
import me.lucko.luckperms.minestom.calculator.MinestomCalculatorFactory;
import me.lucko.luckperms.minestom.configuration.HoconConfigAdapter;
import me.lucko.luckperms.minestom.context.ContextProvider;
import me.lucko.luckperms.minestom.context.MinestomContextManager;
import me.lucko.luckperms.minestom.context.MinestomPlayerCalculator;
import me.lucko.luckperms.minestom.listeners.MinestomConnectionListener;
import me.lucko.luckperms.minestom.messaging.MinestomMessagingFactory;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.query.QueryOptions;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import org.jetbrains.annotations.NotNull;

public final class LPMinestomPlugin extends AbstractLuckPermsPlugin {

    private final EventNode<Event> eventNode = EventNode.all("luckperms");

    private final LPMinestomBootstrap bootstrap;
    private final Set<ContextProvider> contextProviders;
    private final boolean commands;
    private final @NotNull ConfigurationAdapter configurationAdapter;

    private MinestomSenderFactory senderFactory;
    private MinestomContextManager contextManager;
    private StandardUserManager userManager;
    private StandardGroupManager groupManager;
    private StandardTrackManager trackManager;
    private MinestomCommandExecutor commandManager;
    private MinestomConnectionListener connectionListener;

    LPMinestomPlugin(@NotNull LPMinestomBootstrap bootstrap, @NotNull Set<ContextProvider> contextProviders, @NotNull Function<LuckPermsPlugin, ConfigurationAdapter> configurationAdapter, boolean commands) {
        this.bootstrap = bootstrap;
        this.contextProviders = contextProviders;
        this.commands = commands;
        this.configurationAdapter = configurationAdapter.apply(this);
    }

    @Override
    protected void setupSenderFactory() {
        this.senderFactory = new MinestomSenderFactory(this);
    }

    @Override
    protected ConfigurationAdapter provideConfigurationAdapter() {
        return this.configurationAdapter;
    }

    @Override
    protected void registerPlatformListeners() {
        this.connectionListener = new MinestomConnectionListener(this, this.eventNode);
        MinecraftServer.getGlobalEventHandler().addChild(this.eventNode);
    }

    @Override
    protected MessagingFactory<?> provideMessagingFactory() {
        return new MinestomMessagingFactory(this, this.eventNode);
    }

    @Override
    protected void registerCommands() {
        if (commands) {
            this.commandManager = new MinestomCommandExecutor(this);
            this.commandManager.register();
        }
    }

    @Override
    protected void setupManagers() {
        this.userManager = new StandardUserManager(this);
        this.groupManager = new StandardGroupManager(this);
        this.trackManager = new StandardTrackManager(this);
    }

    @Override
    protected CalculatorFactory provideCalculatorFactory() {
        return new MinestomCalculatorFactory(this, getConfiguration());
    }

    @Override
    protected void setupContextManager() {
        this.contextManager = new MinestomContextManager(this);
        this.contextManager.registerCalculator(new MinestomPlayerCalculator(this, this.eventNode, this.contextProviders, getConfiguration().get(ConfigKeys.DISABLED_CONTEXTS)));
    }

    @Override
    protected void setupPlatformHooks() {

    }

    @Override
    protected AbstractEventBus<?> provideEventBus(LuckPermsApiProvider apiProvider) {
        return new MinestomEventBus(this, apiProvider);
    }

    @Override
    protected void registerApiOnPlatform(LuckPerms api) {
        // minestom does not have a services manager
    }

    @Override
    protected void performFinalSetup() {

    }

    @Override
    public LuckPermsBootstrap getBootstrap() {
        return this.bootstrap;
    }

    @Override
    public UserManager<? extends User> getUserManager() {
        return this.userManager;
    }

    @Override
    public GroupManager<? extends Group> getGroupManager() {
        return this.groupManager;
    }

    @Override
    public TrackManager<? extends Track> getTrackManager() {
        return this.trackManager;
    }

    @Override
    public CommandManager getCommandManager() {
        return this.commandManager;
    }

    @Override
    public AbstractConnectionListener getConnectionListener() {
        return this.connectionListener;
    }

    @Override
    public MinestomContextManager getContextManager() {
        return this.contextManager;
    }

    @Override
    public Optional<QueryOptions> getQueryOptionsForUser(User user) {
        return this.bootstrap.getPlayer(user.getUniqueId()).map(player -> this.contextManager.getQueryOptions(player));
    }

    @Override
    public Stream<Sender> getOnlineSenders() {
        Collection<Player> players = MinecraftServer.getConnectionManager().getOnlinePlayers();
        return Stream.concat(
                Stream.of(getConsoleSender()),
                players.stream().map(getSenderFactory()::wrap)
        );
    }

    public MinestomSenderFactory getSenderFactory() {
        return this.senderFactory;
    }

    @Override
    public Sender getConsoleSender() {
        return getSenderFactory().wrap(MinecraftServer.getCommandManager().getConsoleSender());
    }
}
