package me.lucko.luckperms.minestom.context.defaults;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import me.lucko.luckperms.common.util.EnumNamer;
import me.lucko.luckperms.minestom.context.ContextProvider;
import net.luckperms.api.context.DefaultContextKeys;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerGameModeChangeEvent;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.jetbrains.annotations.NotNull;

public final class GameModeContextProvider implements ContextProvider {

    private static final EnumNamer<GameMode> GAMEMODE_NAMER = new EnumNamer<>(
            GameMode.class,
            EnumNamer.LOWER_CASE_NAME
    );

    @Override
    public @NotNull String key() {
        return DefaultContextKeys.GAMEMODE_KEY;
    }

    @Override
    public @NotNull Optional<String> query(@NotNull Player subject) {
        return Optional.of(GAMEMODE_NAMER.name(subject.getGameMode()));
    }

    @Override
    public @NotNull Set<String> potentialValues() {
        return Arrays.stream(GameMode.values())
                .map(GAMEMODE_NAMER::name)
                .collect(Collectors.toSet());
    }

    @Override
    public void register(@NonNull Consumer<Player> contextUpdateSignaller, @NonNull EventNode<Event> eventNode) {
        eventNode.addListener(PlayerGameModeChangeEvent.class, event -> contextUpdateSignaller.accept(event.getPlayer()));
    }

}
