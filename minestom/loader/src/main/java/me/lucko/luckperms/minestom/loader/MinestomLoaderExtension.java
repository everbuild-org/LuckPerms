package me.lucko.luckperms.minestom.loader;

import me.lucko.luckperms.common.loader.JarInJarClassLoader;
import me.lucko.luckperms.common.loader.LoaderBootstrap;
import net.minestom.server.extensions.Extension;

public final class MinestomLoaderExtension extends Extension {

    private static final String JAR_NAME = "luckperms-minestom.jarinjar";
    private static final String BOOTSTRAP_CLASS = "me.lucko.luckperms.minestom.LPMinestomBootstrap";

    private final LoaderBootstrap plugin;

    public MinestomLoaderExtension() {
        JarInJarClassLoader loader = new JarInJarClassLoader(getClass().getClassLoader(), "luckperms-minestom.jarinjar");
        this.plugin = loader.instantiatePlugin(BOOTSTRAP_CLASS, Extension.class, this);
    }

    @Override
    public void preInitialize() {
        this.plugin.onLoad();
    }

    @Override
    public void initialize() {
        this.plugin.onEnable();
    }

    @Override
    public void terminate() {
        this.plugin.onDisable();
    }

}
