package me.lucko.luckperms.minestom.context.defaults;

import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import me.lucko.luckperms.minestom.context.ContextProvider;
import net.luckperms.api.context.DefaultContextKeys;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.utils.NamespaceID;
import net.minestom.server.world.DimensionType;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.jetbrains.annotations.NotNull;

public final class DimensionTypeContextProvider implements ContextProvider {

    @Override
    public @NotNull String key() {
        return DefaultContextKeys.DIMENSION_TYPE_KEY;
    }

    @Override
    public @NotNull Optional<String> query(@NotNull Player subject) {
        return Optional.ofNullable(subject.getInstance())
                .map(Instance::getDimensionType)
                .map(DimensionType::getName)
                .map(NamespaceID::value);
    }

    @Override
    public @NotNull Set<String> potentialValues() {
        return MinecraftServer.getDimensionTypeManager().unmodifiableList().stream()
                .map(DimensionType::getName)
                .map(NamespaceID::value)
                .collect(Collectors.toSet());
    }

    @Override
    public void register(@NonNull Consumer<Player> contextUpdateSignaller, @NonNull EventNode<Event> eventNode) {
        eventNode.addListener(PlayerSpawnEvent.class, event -> contextUpdateSignaller.accept(event.getPlayer()));
    }

}