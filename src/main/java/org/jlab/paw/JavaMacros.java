package org.jlab.paw;

import java.nio.file.*;
import java.net.URLClassLoader;
import java.util.*;
import javax.tools.*;

/** Compiles a local .jmacro body against the running workstation, using the installed JDK. */
final class JavaMacros {
    static void run(Path file, MacroContext context) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IllegalStateException("Java tutorial macros need a JDK 21+ (not a JRE)");
        String body = Files.readString(file);
        Path temp = Files.createTempDirectory("paw-java-macro-");
        String name = "PawMacro" + UUID.randomUUID().toString().replace("-", "");
        // Macros are ordinary trusted local Java programs, with the user's process privileges.
        String prefix = "import org.jlab.paw.*; import twig.data.*; import twig.math.*; import twig.widgets.*; import java.util.*; import java.nio.file.*; import java.util.function.*; import static java.lang.Math.*;\npublic class " + name + " { public static void run(MacroContext paw) throws Exception {\n";
        Path source = temp.resolve(name + ".java");
        try {
            Files.writeString(source, prefix + body + "\n}}\n");
            var diagnostics = new DiagnosticCollector<JavaFileObject>();
            try (var manager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, java.nio.charset.StandardCharsets.UTF_8)) {
                String location = Path.of(MacroContext.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
                String cp = System.getProperty("java.class.path") + java.io.File.pathSeparator + location;
                boolean ok = compiler.getTask(null, manager, diagnostics, List.of("-classpath", cp, "-d", temp.toString(), "-proc:none"), null, manager.getJavaFileObjects(source)).call();
                if (!ok) {
                    StringBuilder errors = new StringBuilder();
                    for (var diagnostic : diagnostics.getDiagnostics()) errors.append(file).append(':').append(Math.max(1, diagnostic.getLineNumber() - 2)).append(": ").append(diagnostic.getMessage(Locale.ROOT)).append('\n');
                    throw new IllegalArgumentException(errors.toString());
                }
            }
            try (var loader = new URLClassLoader(new java.net.URL[]{temp.toUri().toURL()}, MacroContext.class.getClassLoader())) {
                try { loader.loadClass(name).getMethod("run", MacroContext.class).invoke(null, context); }
                catch (java.lang.reflect.InvocationTargetException ex) {
                    if (ex.getCause() instanceof java.util.concurrent.CancellationException cause) throw cause;
                    if (ex.getCause() instanceof Exception cause) {
                        int line=1;for(StackTraceElement frame:cause.getStackTrace())if(frame.getClassName().equals(name)){line=Math.max(1,frame.getLineNumber()-2);break;}
                        throw new IllegalArgumentException(file+":"+line+": "+cause.getMessage(),cause);
                    }
                    if (ex.getCause() instanceof Error cause) throw cause;
                    throw ex;
                }
            }
        } finally {
            try (var paths = Files.walk(temp)) { for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path); }
        }
    }
}
