7package me.lucko.luckperms.minestom.context;

import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.jetbrains.annotations.NotNull;

public interface ContextProvider {

    @NotNull String key();

    @NotNull Optional<String> query(@NotNull Player subject);

    default @NotNull Set<String> potentialValues() {
        return Set.of();
    }

    default void register(@NonNull Consumer<Player> contextUpdateSignaller, @NonNull EventNode<Event> eventNode) {
        // do nothing by default
    }

}
