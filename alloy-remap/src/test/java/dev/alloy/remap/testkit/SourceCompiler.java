package dev.alloy.remap.testkit;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.junit.jupiter.api.Assumptions;

/**
 * Test helper: compiles small Java sources written in the test itself, in memory, with the JDK's
 * {@code javac}, to get real bytecode (stack frames, lambdas, debug tables).
 */
public final class SourceCompiler {

    private static final Pattern PACKAGE = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;");
    /** First type declaration: optional modifiers, a keyword, then the name. */
    private static final Pattern TYPE_NAME =
            Pattern.compile("(?m)^[\\w ]*?(?:\\b(?:class|interface|enum)|@interface)\\s+(\\w+)");

    private SourceCompiler() {
        // Utility class.
    }

    /**
     * Compiles source files.
     *
     * @param release target Java version (8 gives version 52 classes, like Forge mods)
     * @param sources content of each file; its name comes from the {@code package} line and the
     *                first declared type
     * @return internal class name to {@code .class} bytes
     */
    public static Map<String, byte[]> compile(int release, String... sources) {
        return SourceCompiler.compile(release, List.of(), sources);
    }

    /**
     * Compiles source files against already compiled classes.
     *
     * @param classPath directories or jars where the compiler looks for classes the sources use
     * @return internal class name to {@code .class} bytes
     * @throws AssertionError if the sources do not compile; the message holds the {@code javac} errors
     */
    public static Map<String, byte[]> compile(int release, List<Path> classPath, String... sources) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Assumptions.assumeTrue(compiler != null, "these tests need a JDK (javac), not a JRE");

        List<JavaFileObject> units = new ArrayList<>();
        for (String source : sources) {
            units.add(SourceCompiler.sourceFile(source));
        }
        Map<String, ByteArrayOutputStream> outputs = new LinkedHashMap<>();
        StandardJavaFileManager standard = compiler.getStandardFileManager(null, Locale.ROOT, StandardCharsets.UTF_8);
        JavaFileManager inMemory = new ForwardingJavaFileManager<>(standard) {
            @Override
            public JavaFileObject getJavaFileForOutput(
                    Location location, String className, JavaFileObject.Kind kind, FileObject sibling) {
                String internalName = className.replace('.', '/');
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                outputs.put(internalName, bytes);
                return new SimpleJavaFileObject(URI.create("mem:///" + internalName + kind.extension), kind) {
                    @Override
                    public OutputStream openOutputStream() {
                        return bytes;
                    }
                };
            }
        };
        StringWriter log = new StringWriter();
        List<String> options = new ArrayList<>(
                List.of("--release", String.valueOf(release), "-g", "-proc:none", "-Xlint:-options"));
        if (!classPath.isEmpty()) {
            options.add("-classpath");
            options.add(classPath.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator)));
        }
        boolean success = compiler.getTask(log, inMemory, null, options, null, units).call();
        if (!success) {
            throw new AssertionError("The test sources do not compile:\n" + log);
        }
        Map<String, byte[]> classFiles = new LinkedHashMap<>();
        outputs.forEach((name, bytes) -> classFiles.put(name, bytes.toByteArray()));
        return classFiles;
    }

    private static JavaFileObject sourceFile(String source) {
        Matcher packageLine = SourceCompiler.PACKAGE.matcher(source);
        String directory = packageLine.find() ? packageLine.group(1).replace('.', '/') + "/" : "";
        Matcher type = SourceCompiler.TYPE_NAME.matcher(source);
        if (!type.find()) {
            throw new IllegalArgumentException("No type declaration found in:\n" + source);
        }
        URI location = URI.create("string:///" + directory + type.group(1) + ".java");
        return new SimpleJavaFileObject(location, JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return source;
            }
        };
    }
}
