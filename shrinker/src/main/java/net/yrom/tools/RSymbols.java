package net.yrom.tools;

import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.TransformInput;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.objectweb.asm.ClassReader.SKIP_CODE;
import static org.objectweb.asm.ClassReader.SKIP_DEBUG;
import static org.objectweb.asm.ClassReader.SKIP_FRAMES;

/**
 * @author yrom
 */
public class RSymbols {
    private Map<String, Integer> symbols = new HashMap<>(512);

    public Integer get(String key) {
        return symbols.get(key);
    }

    public boolean containsKey(String key) {
        return symbols.containsKey(key);
    }

    public boolean isEmpty() {
        return symbols.isEmpty();
    }

    public RSymbols from(Collection<TransformInput> inputs) {
        final PathMatcher rClassMatcher = FileSystems.getDefault().getPathMatcher("glob:R$*.class");
        final List<Path> paths = inputs.stream()
                .map(TransformInput::getDirectoryInputs)
                .flatMap(Collection::stream)
                .map(this::toStream)
                .reduce(Stream.empty(), Stream::concat)
                .collect(Collectors.toList());
        Stream<Path> stream;
        if (paths.size() >= Runtime.getRuntime().availableProcessors() * 3) {
            // use parallel here!
            stream = paths.parallelStream();
        } else {
            stream = paths.stream();
        }
        stream.filter(path -> rClassMatcher.matches(path.getFileName()))
                .forEach(this::drainSymbols);
        return this;
    }

    private void drainSymbols(Path file) {
        final String filename = file.getFileName().toString();
        String typeName = filename.substring(0, filename.length() - ".class".length());
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        ClassVisitor visitor = new ClassVisitor(Opcodes.ASM5) {
            @Override
            public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
                // read constant value
                if (value instanceof Integer) {
                    String key = typeName + '.' + name;
                    symbols.putIfAbsent(key, (Integer) value);
                }
                return null;
            }
        };
        new ClassReader(bytes).accept(visitor, SKIP_CODE | SKIP_DEBUG | SKIP_FRAMES);
    }

    private Stream<Path> toStream(DirectoryInput dir) {
        try {
            return Files.walk(dir.getFile().toPath()).filter(Files::isRegularFile);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
