package dev.jadyen.blunt;

import lombok.Getter;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Getter
public class JarReader {
    private final JarFile jar;
    public final ClassPool pool;
    public final HashMap<String, byte[]> files = new HashMap<>();

    @SneakyThrows
    public JarReader(final JarFile jar) {
        if (jar == null) throw new NullPointerException("invalid jar");
        this.jar = jar;
        this.pool = new ClassPool();

        final Enumeration<JarEntry> entries = this.jar.entries();
        while (entries.hasMoreElements()) {
            final JarEntry entry = entries.nextElement();
            if (entry.getName().endsWith(".class")) {
                pool.add(read(entry));
            }
        }
        try (ZipInputStream jarInputStream = new ZipInputStream(Files.newInputStream(new File(jar.getName()).toPath()))) {
            ZipEntry zipEntry;
            while ((zipEntry = jarInputStream.getNextEntry()) != null) {
                if (!zipEntry.getName().endsWith(".class")) {
                    files.put(zipEntry.getName(), IOUtils.toByteArray(jarInputStream));
                }
            }
        }
    }

    public static JarReader read(final File file) {
        try {
            return new JarReader(new JarFile(file));
        } catch (final Throwable _t) { throw new RuntimeException("failed to create jarfile"); }
    }

    private ClassNode read(final JarEntry entry) throws IOException {
        try (final InputStream is = jar.getInputStream(entry)) {
            ClassNode node = new ClassNode();
            new ClassReader(is).accept(node, 0);
            return node;
        }
    }

    public void export(final String path) {
        export(new File(path));
    }

    @SneakyThrows
    public void export(final File output) {
        try (final JarOutputStream jos = new JarOutputStream(Files.newOutputStream(output.toPath()))) {
            pool.forEach(node -> {
                try {
                    jos.putNextEntry(new JarEntry(node.name.concat(".class")));

                    final BluntClassWriter writer = new BluntClassWriter();
                    node.accept(writer);

                    jos.write(writer.toByteArray());
                    jos.closeEntry();
                } catch (final Throwable _t) {
                    throw new RuntimeException("Error writing class: " + node.name, _t);
                }
            });
            files.forEach((name, bytes) -> {
                try {
                    jos.putNextEntry(new JarEntry(name));
                    jos.write(bytes);
                    jos.closeEntry();
                } catch (final Throwable _t) {
                    throw new RuntimeException("Error writing file: " + name, _t);
                }
            });
        }
    }
}