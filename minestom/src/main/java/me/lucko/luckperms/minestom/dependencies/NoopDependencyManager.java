package me.lucko.luckperms.minestom.dependencies;

import java.util.Set;
import me.lucko.luckperms.common.dependencies.Dependency;
import me.lucko.luckperms.common.dependencies.DependencyManager;
import me.lucko.luckperms.common.storage.StorageType;

public final class NoopDependencyManager implements DependencyManager {

    @Override
    public void loadDependencies(Set<Dependency> dependencies) {
        // no-op
    }

    @Override
    public void loadStorageDependencies(Set<StorageType> storageTypes, boolean redis, boolean rabbitmq, boolean nats) {
        // no-op
    }

    @Override
    public ClassLoader obtainClassLoaderWith(Set<Dependency> dependencies) {
        return NoopDependencyManager.class.getClassLoader();
    }

    @Override
    public void close() {
        // no-op
    }

}
