package me.lucko.luckperms.minestom.context;

import java.util.Set;
import me.lucko.luckperms.common.config.ConfigKeys;
import me.lucko.luckperms.common.context.ImmutableContextSetImpl;
import me.lucko.luckperms.common.plugin.LuckPermsPlugin;
import me.lucko.luckperms.common.util.EnumNamer;
import me.lucko.luckperms.minestom.LPMinestomPlugin;
import net.luckperms.api.context.ContextCalculator;
import net.luckperms.api.context.ContextConsumer;
import net.luckperms.api.context.ContextSet;
import net.luckperms.api.context.DefaultContextKeys;
import net.luckperms.api.context.ImmutableContextSet;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerGameModeChangeEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.event.trait.PlayerEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.world.DimensionType;
import org.checkerframework.checker.nullness.qual.NonNull;

public final class MinestomPlayerCalculator implements ContextCalculator<Player> {

    private static final EnumNamer<GameMode> GAMEMODE_NAMER = new EnumNamer<>(
            GameMode.class,
            EnumNamer.LOWER_CASE_NAME
    );

    private final boolean gamemode;
    private final boolean dimensionType;

    public MinestomPlayerCalculator(LPMinestomPlugin plugin, EventNode<? super PlayerEvent> eventNode, Set<String> disabled) {
        this.gamemode = !disabled.contains(DefaultContextKeys.GAMEMODE_KEY);
        this.dimensionType = !disabled.contains(DefaultContextKeys.DIMENSION_TYPE_KEY);

        if (this.gamemode) eventNode.addListener(PlayerGameModeChangeEvent.class, event -> plugin.getContextManager().signalContextUpdate(event.getPlayer()));
        if (this.dimensionType) eventNode.addListener(PlayerSpawnEvent.class, event -> plugin.getContextManager().signalContextUpdate(event.getPlayer()));
    }

    @Override
    public void calculate(@NonNull Player subject, @NonNull ContextConsumer consumer) {
        if (this.gamemode) {
            GameMode mode = subject.getGameMode();
            if (mode != null) {
                consumer.accept(DefaultContextKeys.GAMEMODE_KEY, GAMEMODE_NAMER.name(mode));
            }
        }

        if (this.dimensionType) {
            Instance world = subject.getInstance();
            if (world != null) {
                DimensionType environment = world.getDimensionType();
                consumer.accept(DefaultContextKeys.DIMENSION_TYPE_KEY, environment.getName().value());
            }
        }
    }

    @Override
    public @NonNull ContextSet estimatePotentialContexts() {
        ImmutableContextSet.Builder builder = new ImmutableContextSetImpl.BuilderImpl();
        if (this.gamemode) {
            for (GameMode mode : GameMode.values()) {
                builder.add(DefaultContextKeys.GAMEMODE_KEY, GAMEMODE_NAMER.name(mode));
            }
        }
        if (this.dimensionType) {
            for (DimensionType type : MinecraftServer.getDimensionTypeManager().unmodifiableList()) {
                builder.add(DefaultContextKeys.DIMENSION_TYPE_KEY, type.getName().value());
            }
        }
        return builder.build();
    }

}
