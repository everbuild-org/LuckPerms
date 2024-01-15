package me.lucko.luckperms.minestom.messaging;

import me.lucko.luckperms.common.messaging.pluginmsg.AbstractPluginMessageMessenger;
import net.luckperms.api.messenger.IncomingMessageConsumer;
import net.minestom.server.MinecraftServer;
import net.minestom.server.event.EventListener;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerPluginMessageEvent;
import net.minestom.server.timer.TaskSchedule;

public final class PluginMessageMessenger extends AbstractPluginMessageMessenger {

    private final EventListener<PlayerPluginMessageEvent> listener = EventListener.of(PlayerPluginMessageEvent.class, this::receiveIncomingMessage);

    private final EventNode<? super PlayerPluginMessageEvent> eventNode;

    PluginMessageMessenger(EventNode<? super PlayerPluginMessageEvent> eventNode, IncomingMessageConsumer consumer) {
        super(consumer);
        this.eventNode = eventNode;
    }

    public void init() {
        this.eventNode.addListener(this.listener);
    }

    @Override
    public void close() {
        this.eventNode.removeListener(this.listener);
    }

    @Override
    protected void sendOutgoingMessage(byte[] buf) {
        MinecraftServer.getSchedulerManager().submitTask(() -> MinecraftServer.getConnectionManager().getOnlinePlayers().stream()
                .findFirst()
                .map(player -> {
                    player.sendPluginMessage(CHANNEL, buf);
                    return TaskSchedule.stop();
                }).orElse(TaskSchedule.tick(100)));
    }

    private void receiveIncomingMessage(PlayerPluginMessageEvent event) {
        if (!event.getIdentifier().equals(CHANNEL)) {
            return;
        }

        handleIncomingMessage(event.getMessage());
    }

}
