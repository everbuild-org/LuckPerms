package me.lucko.luckperms.minestom.context;

import com.github.benmanes.caffeine.cache.LoadingCache;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import me.lucko.luckperms.common.cache.LoadingMap;
import me.lucko.luckperms.common.config.ConfigKeys;
import me.lucko.luckperms.common.context.manager.ContextManager;
import me.lucko.luckperms.common.context.manager.QueryOptionsCache;
import me.lucko.luckperms.common.context.manager.QueryOptionsSupplier;
import me.lucko.luckperms.common.plugin.LuckPermsPlugin;
import me.lucko.luckperms.common.util.CaffeineFactory;
import net.luckperms.api.context.ImmutableContextSet;
import net.luckperms.api.query.QueryOptions;
import net.minestom.server.entity.Player;

public final class MinestomContextManager extends ContextManager<Player, Player> {

    private final LoadingMap<Player, QueryOptionsCache<Player>> onlineSubjectCaches = LoadingMap.of(key -> new QueryOptionsCache<>(key, this));

    private final LoadingCache<Player, QueryOptionsCache<Player>> offlineSubjectCaches = CaffeineFactory.newBuilder()
            .expireAfterAccess(1, TimeUnit.MINUTES)
            .build(key -> {
                QueryOptionsCache<Player> cache = this.onlineSubjectCaches.getIfPresent(key);
                return Objects.requireNonNullElseGet(cache, () -> new QueryOptionsCache<>(key, this));
            });

    public MinestomContextManager(LuckPermsPlugin plugin) {
        super(plugin, Player.class, Player.class);
    }

    public void onPlayerQuit(Player player) {
        this.onlineSubjectCaches.remove(player);
    }

    @Override
    public UUID getUniqueId(Player player) {
        return player.getUuid();
    }

    @Override
    public QueryOptionsSupplier getCacheFor(Player subject) {
        if (subject == null) throw new NullPointerException("subject");

        if (subject.isOnline()) return this.onlineSubjectCaches.get(subject);
        return this.offlineSubjectCaches.get(subject);
    }

    @Override
    public QueryOptions formQueryOptions(Player subject, ImmutableContextSet contextSet) {
        return this.plugin.getConfiguration().get(ConfigKeys.GLOBAL_QUERY_OPTIONS).toBuilder()
                .context(contextSet).build();
    }

    @Override
    protected void invalidateCache(Player subject) {
        QueryOptionsCache<Player> cache = this.onlineSubjectCaches.getIfPresent(subject);
        if (cache != null) {
            cache.invalidate();
        }

        cache = this.offlineSubjectCaches.getIfPresent(subject);
        if (cache != null) {
            cache.invalidate();
        }
    }

}
