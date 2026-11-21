package com.example.info2ai.testing;

import java.nio.file.Path;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.zip.*;
import net.fabricmc.loader.impl.transformer.PackageAccessFixer;
import org.objectweb.asm.*;

/** Repairs only mapping-created package visibility in a test-only derived JAR. */
public final class PackageAccessRepair {
    public static void main(String[] args) throws Exception {
        Path source = Path.of(args[0]);
        Files.createDirectories(Path.of(args[1]).getParent());
        byte[] before = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(source));
        try (var input = new ZipFile(source.toFile());
             var output = new ZipOutputStream(Files.newOutputStream(Path.of(args[1])))) {
            var entries = input.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                byte[] bytes;
                try (var stream = input.getInputStream(entry)) { bytes=stream.readAllBytes(); }
                if (entry.getName().endsWith(".class")) {
                    var reader = new ClassReader(bytes);
                    var writer = new ClassWriter(reader,0);
                    reader.accept(new PackageAccessFixer(Opcodes.ASM9,writer),0);
                    bytes=writer.toByteArray();
                }
                output.putNextEntry(new ZipEntry(entry.getName()));
                output.write(bytes); output.closeEntry();
            }
        }
        byte[] after = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(source));
        if (!MessageDigest.isEqual(before,after)) throw new AssertionError("Source JAR changed");
        System.out.println("Fabric Loader PackageAccessFixer; source unchanged SHA256="+HexFormat.of().formatHex(before));
    }
}
