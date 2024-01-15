package me.lucko.luckperms.minestom;

import me.lucko.luckperms.common.api.LuckPermsApiProvider;
import me.lucko.luckperms.common.event.AbstractEventBus;
import me.lucko.luckperms.common.plugin.LuckPermsPlugin;

public final class MinestomEventBus extends AbstractEventBus<Object> {

    public MinestomEventBus(LuckPermsPlugin plugin, LuckPermsApiProvider apiProvider) {
        super(plugin, apiProvider);
    }

    @Override
    protected Object checkPlugin(Object plugin) throws IllegalArgumentException {
        return plugin;
    }

}
