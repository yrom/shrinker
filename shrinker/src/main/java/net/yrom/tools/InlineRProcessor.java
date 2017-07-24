package net.yrom.tools;

import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.TransformInput;

import org.apache.commons.io.IOUtils;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Collection;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import groovy.lang.Closure;

/**
 * @author yrom
 */
final class InlineRProcessor {
    private static final Logger log = Logging.getLogger(InlineRProcessor.class);
    private InlineRProcessor() {}

    static void proceed(Collection<TransformInput> inputs,
            Closure<Path> getTargetPath,
            Function<byte[], byte[]> transform) {
        Stream.concat(
                streamOf(inputs, TransformInput::getDirectoryInputs),
                streamOf(inputs, TransformInput::getJarInputs))
                .forEach((QualifiedContent input) -> {
                    long start = System.currentTimeMillis();
                    Path src = input.getFile().toPath();
                    Path dst = getTargetPath.call(input);
                    if (input instanceof DirectoryInput) {
                        transformDir(src, dst, transform);
                    } else if (input instanceof JarInput) {
                        transformJar(src, dst, transform);
                    } else {
                        throw new RuntimeException();
                    }
                    log.info((System.currentTimeMillis() - start) + "ms " + src);
                });
    }

    private static <T extends QualifiedContent> Stream<T> streamOf(
            Collection<TransformInput> inputs,
            Function<TransformInput, Collection<T>> mapping) {
        Collection<T> list = inputs.stream()
                    .map(mapping)
                    .flatMap(Collection::stream)
                    .collect(Collectors.toList());
        if (list.size() >= Runtime.getRuntime().availableProcessors())
            return list.parallelStream();
        else
            return list.stream();
    }

    private static PathMatcher CASE_R_FILE
            = FileSystems.getDefault().getPathMatcher("regex:^R\\.class|R\\$(?!styleable)[a-z]+\\.class$");

    private static DirectoryStream.Filter<Path> CLASS_TRANSFORM_FILTER
            = path -> Files.isDirectory(path)
                    || (Files.isRegularFile(path) && !CASE_R_FILE.matches(path.getFileName()));

    private static void transformDir(Path src, Path dest, Function<byte[], byte[]> classTransform) {
        try {
            for (Path file : Files.newDirectoryStream(src, CLASS_TRANSFORM_FILTER)) {
                String name = file.getFileName().toString();
                Path target = dest.resolve(name);
                if (Files.isDirectory(file)) {
                    transformDir(file, target, classTransform);
                } else if (Files.isRegularFile(file)) {
                    log.debug("transform class {}...", file);
                    byte[] bytes = classTransform.apply(Files.readAllBytes(file));
                    if (Files.notExists(dest)) {
                        Files.createDirectories(dest);
                    }
                    Files.write(target, bytes);
                }
            }

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void transformJar(Path src, Path dst, Function<byte[], byte[]> classTransform) {
        if (Files.notExists(src)) throw new IllegalArgumentException("No such file " + src);
        try (ZipInputStream in = new ZipInputStream(new BufferedInputStream(Files.newInputStream(src)));
             JarOutputStream out = new JarOutputStream(new BufferedOutputStream(Files.newOutputStream(dst)))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                if (!name.endsWith(".class")) {
                    // skip
                    continue;
                }
                JarEntry newEntry;
                if (entry.getMethod() == ZipEntry.STORED) {
                    newEntry = new JarEntry(entry);
                } else {
                    newEntry = new JarEntry(name);
                }
                // put new entry
                out.putNextEntry(newEntry);
                byte[] bytes = classTransform.apply(IOUtils.toByteArray(in));
                // write bytes of entry
                out.write(bytes);
                out.closeEntry();
                in.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
