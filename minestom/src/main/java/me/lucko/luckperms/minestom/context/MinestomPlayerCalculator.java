package me.lucko.luckperms.minestom.context;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import me.lucko.luckperms.common.context.ImmutableContextSetImpl;
import me.lucko.luckperms.minestom.LPMinestomPlugin;
import me.lucko.luckperms.minestom.context.defaults.DimensionTypeContextProvider;
import me.lucko.luckperms.minestom.context.defaults.GameModeContextProvider;
import net.luckperms.api.context.ContextCalculator;
import net.luckperms.api.context.ContextConsumer;
import net.luckperms.api.context.ContextSet;
import net.luckperms.api.context.ImmutableContextSet;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.jetbrains.annotations.NotNull;

public final class MinestomPlayerCalculator implements ContextCalculator<Player> {

    private final @NonNull Set<ContextProvider> providers;

    public MinestomPlayerCalculator(@NotNull LPMinestomPlugin plugin, @NotNull EventNode<Event> eventNode, @NotNull Set<ContextProvider> providers, @NotNull Set<String> disabled) {
        providers = new HashSet<>(providers);

        // register the default providers
        providers.add(new GameModeContextProvider());
        providers.add(new DimensionTypeContextProvider());

        this.providers = providers.stream()
                .filter(p -> !disabled.contains(p.key()))
                .peek(p -> p.register(player -> plugin.getContextManager().signalContextUpdate(player), eventNode))
                .collect(Collectors.toSet());
    }

    @Override
    public void calculate(@NonNull Player subject, @NonNull ContextConsumer consumer) {
        this.providers.forEach(p -> p.query(subject).ifPresent(value -> consumer.accept(p.key(), value)));
    }

    @Override
    public @NonNull ContextSet estimatePotentialContexts() {
        ImmutableContextSet.Builder builder = new ImmutableContextSetImpl.BuilderImpl();
        this.providers.forEach(p -> p.potentialValues().forEach(value -> builder.add(p.key(), value)));
        return builder.build();
    }

}
