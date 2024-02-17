package me.lucko.luckperms.minestom;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import me.lucko.luckperms.minestom.configuration.EnvironmentConfigAdapter;
import me.lucko.luckperms.minestom.configuration.HoconConfigAdapter;
import net.kyori.adventure.text.Component;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.context.ContextSet;
import net.luckperms.api.context.DefaultContextKeys;
import net.luckperms.api.context.ImmutableContextSet;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.CommandManager;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.Argument;
import net.minestom.server.command.builder.arguments.ArgumentString;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.event.player.PlayerChatEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.extras.MojangAuth;
import net.minestom.server.extras.lan.OpenToLAN;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.block.Block;
import net.minestom.server.network.ConnectionManager;
import org.checkerframework.checker.units.qual.C;

public final class MinestomServer {

    public static void main(String[] args) throws IOException {
        MinecraftServer server = MinecraftServer.init();
        MinecraftServer.setBrandName("LU15");

        // initialize LuckPerms
        Path directory = Path.of("luckperms");
        LuckPerms luckPerms = LuckPermsMinestom.builder(directory)
                .commands(true)
                .contextProvider(new DummyContextProvider())
                .configurationAdapter(plugin -> new EnvironmentConfigAdapter(plugin, new HoconConfigAdapter(plugin)))
                .permissionSuggestions("test.permission", "test.other")
                .enable();

        // set custom player provider (optional)
        ConnectionManager connectionManager = MinecraftServer.getConnectionManager();
        connectionManager.setPlayerProvider((uuid, username, connection) -> new ExamplePlayer(luckPerms, uuid, username, connection));

        // set up Minestom
        InstanceContainer instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        instance.setGenerator(unit -> unit.modifier().fillHeight(0, 40, Block.GRASS_BLOCK));

        EventNode<Event> eventNode = MinecraftServer.getGlobalEventHandler();
        eventNode.addListener(AsyncPlayerConfigurationEvent.class, event -> {
            event.setSpawningInstance(instance);
            event.getPlayer().setRespawnPoint(new Pos(0, 41, 0));
        });

        // set custom chat handling (optional)
        eventNode.addListener(PlayerChatEvent.class, event -> {
            if (!(event.getPlayer() instanceof ExamplePlayer player)) return;
            event.setChatFormat(e -> Component.text().append(
                    player.getPrefix(),
                    player.getName(),
                    player.getSuffix(),
                    Component.text(": "),
                    Component.text(e.getMessage())
            ).build());
        });

        // example of adding permissions to a player via the custom player class
        eventNode.addListener(PlayerSpawnEvent.class, event -> {
            if (!(event.getPlayer() instanceof ExamplePlayer player)) return;
            player.setPermission(
                    Node.builder("*")
                            //.expiry(10, TimeUnit.SECONDS)
                            .context(
                                    ImmutableContextSet.builder()
                                            .add(DefaultContextKeys.DIMENSION_TYPE_KEY, "overworld")
                                            .add("dummy", "true")
                                            .build()
                            ).build(),
                    true
            );
        });

        // command to check if a player has a permission
        CommandManager commandManager = MinecraftServer.getCommandManager();
        Command command = new Command("has");
        ArgumentString permissionArgument = ArgumentType.String("permission");
        command.addSyntax((sender, context) -> {
            String permission = context.get(permissionArgument);
            if (sender instanceof ExamplePlayer player) sender.sendMessage(String.valueOf(player.hasPermission(permission)));
            else sender.sendMessage("Sender is not a player");
        }, permissionArgument);
        commandManager.register(command);

        // register shutdown hook to delete the temp directory
        MinecraftServer.getSchedulerManager().buildShutdownTask(() -> {
            try {
                LuckPermsMinestom.disable();
                Files.deleteIfExists(directory);
            } catch (IOException ignored) {
                // oh well...
            }
        });

        OpenToLAN.open();
        MojangAuth.init();

        server.start("0.0.0.0", 25565);
    }

}
