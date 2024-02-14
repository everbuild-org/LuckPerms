package me.lucko.luckperms.minestom;

import java.util.Optional;
import me.lucko.luckperms.minestom.context.ContextProvider;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class DummyContextProvider implements ContextProvider {

    @Override
    public @NotNull String key() {
        return "dummy";
    }

    @Override
    public @NotNull Optional<String> query(@NotNull Player subject) {
        return Optional.of("true");
    }

}
