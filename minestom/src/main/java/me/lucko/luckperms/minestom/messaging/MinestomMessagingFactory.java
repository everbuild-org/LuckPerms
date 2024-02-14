package me.lucko.luckperms.minestom.messaging;

import me.lucko.luckperms.common.messaging.InternalMessagingService;
import me.lucko.luckperms.common.messaging.LuckPermsMessagingService;
import me.lucko.luckperms.common.messaging.MessagingFactory;
import me.lucko.luckperms.minestom.LPMinestomPlugin;
import net.luckperms.api.messenger.IncomingMessageConsumer;
import net.luckperms.api.messenger.Messenger;
import net.luckperms.api.messenger.MessengerProvider;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerPluginMessageEvent;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.jetbrains.annotations.NotNull;

public final class MinestomMessagingFactory extends MessagingFactory<LPMinestomPlugin> {

    private final EventNode<? super PlayerPluginMessageEvent> eventNode;

    public MinestomMessagingFactory(LPMinestomPlugin plugin, EventNode<? super PlayerPluginMessageEvent> eventNode) {
        super(plugin);
        this.eventNode = eventNode;
    }

    @Override
    protected InternalMessagingService getServiceFor(String messagingType) {
        if (messagingType.equals("pluginmsg") || messagingType.equals("bungee") || messagingType.equals("velocity")) {
            try {
                return new LuckPermsMessagingService(getPlugin(), new PluginMessageMessengerProvider(this.eventNode));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return super.getServiceFor(messagingType);
    }

    private record PluginMessageMessengerProvider(
            @NotNull EventNode<? super PlayerPluginMessageEvent> eventNode
    ) implements MessengerProvider {

            @Override
            public @NonNull String getName() {
                return "PluginMessage";
            }

            @Override
            public @NonNull Messenger obtain(@NonNull IncomingMessageConsumer incomingMessageConsumer) {
                PluginMessageMessenger messenger = new PluginMessageMessenger(this.eventNode, incomingMessageConsumer);
                messenger.init();
                return messenger;
            }

        }

}
