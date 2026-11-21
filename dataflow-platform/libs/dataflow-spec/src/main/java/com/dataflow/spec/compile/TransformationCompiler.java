package com.dataflow.spec.compile;

import com.dataflow.transformation.DataTransformation;
import com.dataflow.transformation.Transformation;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

public final class TransformationCompiler {

    public static final String FOLDER = "transformations";
    public static final String JAVA = ".java";
    private static final String CLASS = ".class";

    public static Map<String, String> read(Path projectRoot) {
        Map<String, String> sources = new TreeMap<>();
        Path folder = projectRoot == null ? null : projectRoot.resolve(FOLDER);
        if (folder == null || !Files.isDirectory(folder)) {
            return sources;
        }
        try (Stream<Path> paths = Files.walk(folder)) {
            for (Path path : paths.filter(path -> path.toString().endsWith(JAVA))
                    .sorted()
                    .toList()) {
                sources.put(projectRoot.relativize(path).toString().replace('\\', '/'), Files.readString(path));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the transformations under " + folder, e);
        }
        return sources;
    }

    private static CompiledTransformations load(Path output, ClassLoader parent) {
        try (Stream<Path> paths = Files.walk(output)) {
            URLClassLoader loader = new URLClassLoader(new URL[] {output.toUri().toURL()}, parent);
            Map<String, Class<?>> byId = new TreeMap<>();
            List<CompilationFailedException.Diagnosis> duplicates = new ArrayList<>();
            for (Path file : paths.filter(path -> path.toString().endsWith(CLASS))
                    .sorted()
                    .toList()) {
                String name = output.relativize(file).toString().replace('\\', '/');
                name = name.substring(0, name.length() - CLASS.length()).replace('/', '.');
                Class<?> type = Class.forName(name, false, loader);
                DataTransformation annotation = type.getAnnotation(DataTransformation.class);
                if (annotation == null || !Transformation.class.isAssignableFrom(type)) {
                    continue;
                }
                String id = annotation.name() + "@" + annotation.version();
                if (byId.put(id, type) != null) {
                    duplicates.add(new CompilationFailedException.Diagnosis(null, null, id + " is declared twice"));
                }
            }
            if (!duplicates.isEmpty()) {
                throw new CompilationFailedException(duplicates);
            }
            return new CompiledTransformations(loader, byId, artifact(output));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("A compiled class disappeared: " + e.getMessage(), e);
        }
    }

    private static Path artifact(Path output) throws IOException {
        Path jar = output.resolve("transformations.jar");
        try (Stream<Path> paths = Files.walk(output);
                JarOutputStream archive = new JarOutputStream(Files.newOutputStream(jar))) {
            for (Path file : paths.filter(path -> path.toString().endsWith(CLASS))
                    .sorted()
                    .toList()) {
                JarEntry entry = new JarEntry(output.relativize(file).toString().replace('\\', '/'));
                entry.setTime(0);
                archive.putNextEntry(entry);
                Files.copy(file, archive);
                archive.closeEntry();
            }
        }
        return jar;
    }

    private static CompilationFailedException.Diagnosis describe(Diagnostic<? extends JavaFileObject> diagnostic) {
        return new CompilationFailedException.Diagnosis(
                diagnostic.getSource() == null ? null : diagnostic.getSource().getName(),
                diagnostic.getSource() == null ? null : diagnostic.getLineNumber(),
                diagnostic.getMessage(Locale.ROOT));
    }

    private static Path temporary() {
        try {
            return Files.createTempDirectory("transformations-");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public CompiledTransformations compile(Map<String, String> sources, ClassLoader parent) {
        if (sources.isEmpty()) {
            return new CompiledTransformations(parent, Map.of(), null);
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("This runtime has no Java compiler (module jdk.compiler)");
        }
        Path output = temporary();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager files =
                compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            files.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(output));
            List<String> options = List.of(
                    "-classpath",
                    System.getProperty("java.class.path"),
                    "-proc:none",
                    "-parameters",
                    "--release",
                    "21",
                    "-encoding",
                    "UTF-8",
                    "-nowarn");
            List<JavaFileObject> units = sources.entrySet().stream()
                    .map(entry -> (JavaFileObject) new Source(entry.getKey(), entry.getValue()))
                    .toList();
            boolean ok = compiler.getTask(null, files, diagnostics, options, null, units)
                    .call();
            List<CompilationFailedException.Diagnosis> errors = diagnostics.getDiagnostics().stream()
                    .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)
                    .map(TransformationCompiler::describe)
                    .toList();
            if (!ok || !errors.isEmpty()) {
                throw new CompilationFailedException(errors);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return load(output, parent);
    }

    private static final class Source extends SimpleJavaFileObject {

        private final String path;
        private final String code;

        Source(String path, String code) {
            super(URI.create("string:///" + path.replace(' ', '_')), Kind.SOURCE);
            this.path = path;
            this.code = code;
        }

        @Override
        public String getName() {
            return path;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return code;
        }
    }
}
