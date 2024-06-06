package me.lucko.luckperms.minestom;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.model.data.DataMutateResult;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.platform.PlayerAdapter;
import net.luckperms.api.util.Tristate;
import net.minestom.server.entity.Player;
import net.minestom.server.network.player.PlayerConnection;
import net.minestom.server.permission.Permission;
import net.minestom.server.permission.PermissionVerifier;
import net.minestom.server.utils.Unit;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An example implementation of permission handling in a Player using LuckPerms.
 * This class is a simple example and is not intended for production use.
 * Every situation is different, and you should consider your own requirements when implementing permission handling.
 */
public final class ExamplePlayer extends Player {

    private static final @NotNull MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final @NotNull LuckPerms luckPerms;
    private final @NonNull PlayerAdapter<Player> playerAdapter;

    public ExamplePlayer(@NotNull LuckPerms luckPerms, @NotNull UUID uuid, @NotNull String username, @NotNull PlayerConnection playerConnection) {
        super(uuid, username, playerConnection);
        this.luckPerms = luckPerms;
        this.playerAdapter = this.luckPerms.getPlayerAdapter(Player.class);
    }

    private @NotNull User getLuckPermsUser() {
        return this.playerAdapter.getUser(this);
    }

    private @NotNull CachedMetaData getLuckPermsMetaData() {
        return this.getLuckPermsUser().getCachedData().getMetaData();
    }

    /**
     * Adds a permission to the player. This method is deprecated as
     * the {@link Permission} object is not used in the LuckPerms
     * implementation and does not return a future.
     * @param permission the permission to add
     */
    @Deprecated
    @Override
    public void addPermission(@NotNull Permission permission) {
        // this method implements itself as fire-and-forget as LuckPerms
        // provides futures as responses to permission changes, which
        // Minestom does not support in this context
        this.addPermission(permission.getPermissionName());
    }

    /**
     * Adds a permission to the player. You may choose not to implement
     * this method on a production server, and leave permission management
     * to the LuckPerms web interface or in-game commands.
     * @param permission the permission to add
     * @return the result of the operation
     */
    public @NotNull CompletableFuture<DataMutateResult> addPermission(@NotNull String permission) {
        User user = this.getLuckPermsUser();
        DataMutateResult result = user.data().add(Node.builder(permission).build());
        return this.luckPerms.getUserManager().saveUser(user).thenApply(ignored -> result);
    }

    /**
     * Sets a permission for the player. This method uses a {@link Node} rather
     * than a permission name, this allows for permissions that rely on context.
     * You may choose not to implement this method on a production server, and
     * leave permission management to the LuckPerms web interface or in-game
     * commands.
     * @param permission the permission to set
     * @param value the value of the permission
     * @return the result of the operation
     */
    public @NotNull CompletableFuture<DataMutateResult> setPermission(@NotNull Node permission, boolean value) {
        User user = this.getLuckPermsUser();
        DataMutateResult result = value
                ? user.data().add(permission)
                : user.data().remove(permission);
        return this.luckPerms.getUserManager().saveUser(user).thenApply(ignored -> result);
    }

    /**
     * Removes a permission from the player. This method is deprecated as
     * the {@link Permission} object is not used in the LuckPerms implementation.
     * @param permission the permission to remove
     */
    @Deprecated
    public void removePermission(@NotNull Permission permission) {
        // this method implements itself as fire-and-forget as LuckPerms
        // provides futures as responses to permission changes, which
        // Minestom does not support in this context
        this.removePermission(permission.getPermissionName());
    }

    /**
     * Removes a permission from the player. You may choose not to implement
     * this method on a production server, and leave permission management
     * to the LuckPerms web interface or in-game commands. This method is
     * deprecated as the overridden method does not return a future.
     * @param permissionName the name of the permission to remove
     */
    @Override
    @Deprecated
    public void removePermission(@NotNull String permissionName) {
        User user = this.getLuckPermsUser();
        user.data().remove(Node.builder(permissionName).build());
        this.luckPerms.getUserManager().saveUser(user);
    }

    /**
     * Removes a permission from the player. You may choose not to implement
     * this method on a production server, and leave permission management
     * to the LuckPerms web interface or in-game commands.
     * @param permissionName the name of the permission to remove
     * @param ignored ignored parameter to differentiate from the overridden method
     */
    public @NotNull CompletableFuture<DataMutateResult> removePermission(@NotNull String permissionName, @Nullable Void ignored) {
        User user = this.getLuckPermsUser();
        DataMutateResult result = user.data().remove(Node.builder(permissionName).build());
        return this.luckPerms.getUserManager().saveUser(user).thenApply(ignored0 -> result);
    }

    /**
     * Checks if the player has a permission. This method is deprecated as
     * the {@link Permission} object is not used in the LuckPerms implementation.
     * @param permission the permission to check
     * @return true if the player has the permission
     */
    @Deprecated
    @Override
    public boolean hasPermission(@NotNull Permission permission) {
        return this.hasPermission(permission.getPermissionName());
    }

    /**
     * Gets a permission from the player. This method is deprecated as
     * the {@link Permission} object is not used in the LuckPerms implementation.
     * @param permissionName the name of the permission to check
     * @return the permission if the player has it, or null if not
     */
    @Deprecated
    @Override
    public @Nullable Permission getPermission(@NotNull String permissionName) {
        if (!this.hasPermission(permissionName)) return null;
        return new Permission(permissionName);
    }

    /**
     * Checks if the player has a permission. This method is deprecated as
     * the {@link PermissionVerifier} interface checks for NBT data, which is not
     * used in the LuckPerms implementation.
     * @param permissionName the name of the permission to check
     * @param permissionVerifier the permission verifier, unused
     * @return true if the player has the permission
     */
    @Deprecated
    @Override
    public boolean hasPermission(@NotNull String permissionName, @Nullable PermissionVerifier permissionVerifier) {
        return this.hasPermission(permissionName);
    }

    /**
     * Checks if the player has a permission.
     * @param permissionName the name of the permission to check
     * @return true if the player has the permission
     */
    @Override
    public boolean hasPermission(@NotNull String permissionName) {
        return this.getPermissionValue(permissionName).asBoolean();
    }

    /**
     * Gets the value of a permission. This passes a {@link Tristate} value
     * straight from LuckPerms, which may be a better option than using
     * boolean values in some cases.
     * @param permissionName the name of the permission to check
     * @return the value of the permission
     */
    public @NotNull Tristate getPermissionValue(@NotNull String permissionName) {
        User user = this.getLuckPermsUser();
        return user.getCachedData().getPermissionData().checkPermission(permissionName);
    }

    /**
     * Gets the prefix of the player. This method uses the MiniMessage library
     * to parse the prefix, which is a more advanced option than using legacy
     * chat formatting.
     * @return the prefix of the player
     */
    public @NotNull Component getPrefix() {
        String prefix = this.getLuckPermsMetaData().getPrefix();
        if (prefix == null) return Component.empty();
        return MINI_MESSAGE.deserialize(prefix);
    }

    /**
     * Gets the suffix of the player. This method uses the MiniMessage library
     * to parse the suffix, which is a more advanced option than using legacy
     * chat formatting.
     * @return the suffix of the player
     */
    public @NotNull Component getSuffix() {
        String suffix = this.getLuckPermsMetaData().getSuffix();
        if (suffix == null) return Component.empty();
        return MINI_MESSAGE.deserialize(suffix);
    }

}
