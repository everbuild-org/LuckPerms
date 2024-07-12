package me.lucko.luckperms.minestom;

import java.util.function.Consumer;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import org.jetbrains.annotations.NotNull;

public interface CommandRegistry {

    static @NotNull CommandRegistry of(
            @NotNull Consumer<Command> register,
            @NotNull Consumer<Command> unregister
    ) {
        return new CommandRegistry() {
            @Override
            public void register(@NotNull Command command) {
                register.accept(command);
            }

            @Override
            public void unregister(@NotNull Command command) {
                unregister.accept(command);
            }
        };
    }

    static @NotNull CommandRegistry minestom() {
        return new CommandRegistry() {
            @Override
            public void register(@NotNull Command command) {
                MinecraftServer.getCommandManager().register(command);
            }

            @Override
            public void unregister(@NotNull Command command) {
                MinecraftServer.getCommandManager().unregister(command);
            }
        };
    }

    void register(@NotNull Command command);

    void unregister(@NotNull Command command);

}
