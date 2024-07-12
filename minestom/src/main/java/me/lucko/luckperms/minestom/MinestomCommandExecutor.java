package me.lucko.luckperms.minestom;

import java.util.List;
import me.lucko.luckperms.common.command.CommandManager;
import me.lucko.luckperms.common.command.utils.ArgumentTokenizer;
import me.lucko.luckperms.common.sender.Sender;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.command.builder.suggestion.SuggestionEntry;
import org.jetbrains.annotations.NotNull;

public final class MinestomCommandExecutor extends CommandManager {

    private final @NotNull LuckPermsCommand command = new LuckPermsCommand();

    private final @NotNull LPMinestomPlugin plugin;
    private final @NotNull CommandRegistry registry;

    public MinestomCommandExecutor(@NotNull LPMinestomPlugin plugin, @NotNull CommandRegistry registry) {
        super(plugin);
        this.plugin = plugin;
        this.registry = registry;
    }

    public void register() {
        this.registry.register(this.command);
    }

    public void unregister() {
        this.registry.unregister(this.command);
    }

    private class LuckPermsCommand extends Command {

        public LuckPermsCommand() {
            super("luckperms", "lp", "perm", "perms", "permission", "permissions");

            final var params = ArgumentType.StringArray("params");

            params.setSuggestionCallback((sender, context, suggestion) -> {
                Sender wrapped = plugin.getSenderFactory().wrap(sender);
                String input = context.getInput();
                String[] split = input.split(" ", 2);
                String args = split.length > 1 ? split[1] : "";
                List<String> arguments = ArgumentTokenizer.TAB_COMPLETE.tokenizeInput(args);
                tabCompleteCommand(wrapped, arguments).stream().map(SuggestionEntry::new).forEach(suggestion::addEntry);
            });

            this.setDefaultExecutor((sender, context) -> process(sender, context.getCommandName(), new String[0]));

            this.addSyntax((sender, context) -> process(sender, context.getCommandName(), context.get(params)), params);
        }

        public void process(@NotNull CommandSender sender, @NotNull String command, String @NotNull[] args) {
            List<String> arguments = ArgumentTokenizer.EXECUTE.tokenizeInput(args);

            executeCommand(plugin.getSenderFactory().wrap(sender), command, arguments);
        }

    }
}