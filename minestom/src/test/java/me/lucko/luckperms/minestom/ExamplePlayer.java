package me.lucko.luckperms.minestom;

import java.util.UUID;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.data.DataMutateResult;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.platform.PlayerAdapter;
import net.luckperms.api.util.Tristate;
import net.minestom.server.entity.Player;
import net.minestom.server.network.player.PlayerConnection;
import net.minestom.server.permission.Permission;
import net.minestom.server.permission.PermissionVerifier;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An example implementation of permission handling in a Player using LuckPerms.
 * This class is a simple example and is not intended for production use.
 * Every situation is different, and you should consider your own requirements when implementing permission handling.
 */
public final class ExamplePlayer extends Player {

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

    /**
     * Adds a permission to the player. This method is deprecated as
     * the {@link Permission} object is not used in the LuckPerms implementation.
     * @param permission the permission to add
     */
    @Deprecated
    @Override
    public void addPermission(@NotNull Permission permission) {
        this.addPermission(permission.getPermissionName());
    }

    /**
     * Adds a permission to the player. You may choose not to implement
     * this method on a production server, and leave permission management
     * to the LuckPerms web interface or in-game commands.
     * @param permission the permission to add
     */
    public void addPermission(@NotNull String permission) {
        User user = this.getLuckPermsUser();
        user.data().add(Node.builder(permission).build());
        this.luckPerms.getUserManager().saveUser(user);
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
    public @NotNull DataMutateResult setPermission(@NotNull Node permission, boolean value) {
        User user = this.getLuckPermsUser();
        DataMutateResult result = value
                ? user.data().add(permission)
                : user.data().remove(permission);
        this.luckPerms.getUserManager().saveUser(user);
        return result;
    }

    /**
     * Removes a permission from the player. This method is deprecated as
     * the {@link Permission} object is not used in the LuckPerms implementation.
     * @param permission the permission to remove
     */
    @Deprecated
    @Override
    public void removePermission(@NotNull Permission permission) {
        this.removePermission(permission.getPermissionName());
    }

    /**
     * Removes a permission from the player. You may choose not to implement
     * this method on a production server, and leave permission management
     * to the LuckPerms web interface or in-game commands.
     * @param permissionName the name of the permission to remove
     */
    @Override
    public void removePermission(@NotNull String permissionName) {
        User user = this.getLuckPermsUser();
        user.data().remove(Node.builder(permissionName).build());
        this.luckPerms.getUserManager().saveUser(user);
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
        User user = this.getLuckPermsUser();
        return user.getCachedData().getPermissionData().checkPermission(permissionName).asBoolean();
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

}
