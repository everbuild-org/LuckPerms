package me.lucko.luckperms.minestom;

import java.nio.file.Path;
import me.lucko.luckperms.common.plugin.classpath.ClassPathAppender;

public final class NoopClassPathAppender implements ClassPathAppender {

    @Override
    public void addJarToClasspath(Path file) {
        // no-op
    }

}
