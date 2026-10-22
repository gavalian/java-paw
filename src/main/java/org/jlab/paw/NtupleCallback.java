package org.jlab.paw;

import java.nio.file.*;
import java.net.URLClassLoader;
import java.util.*;
import javax.tools.*;

/** Compile once per query, then invoke a Java macro for each ntuple row. */
final class NtupleCallback implements AutoCloseable {
    private final Path temporary, file;
    private final String className;
    private final URLClassLoader loader;
    private final java.lang.reflect.Method method;
    private final MacroContext context;
    NtupleCallback(Path file, MacroContext context) throws Exception {
        this.file=file;this.context=context;
        JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();if(compiler==null)throw new IllegalStateException("Ntuple callbacks require JDK 21+");
        temporary=Files.createTempDirectory("paw-ntuple-callback-");className="PawRow"+UUID.randomUUID().toString().replace("-","");
        Path source=temporary.resolve(className+".java");
        try{
            Files.writeString(source,"import org.jlab.paw.*; import twig.data.*; import twig.math.*; import java.util.*; import java.nio.file.*; import static java.lang.Math.*;\npublic class "+className+" { public static void run(MacroContext paw) throws Exception {\n"+Files.readString(file)+"\n}}\n");
            var diagnostics=new DiagnosticCollector<JavaFileObject>();
            try(var manager=compiler.getStandardFileManager(diagnostics,Locale.ROOT,java.nio.charset.StandardCharsets.UTF_8)){
                String cp=System.getProperty("java.class.path")+java.io.File.pathSeparator+Path.of(MacroContext.class.getProtectionDomain().getCodeSource().getLocation().toURI());
                if(!compiler.getTask(null,manager,diagnostics,List.of("-classpath",cp,"-d",temporary.toString(),"-proc:none"),null,manager.getJavaFileObjects(source)).call()){
                    StringBuilder errors=new StringBuilder();for(var d:diagnostics.getDiagnostics())errors.append(file).append(':').append(Math.max(1,d.getLineNumber()-2)).append(": ").append(d.getMessage(Locale.ROOT)).append('\n');throw new IllegalArgumentException(errors.toString());
                }
            }
            loader=new URLClassLoader(new java.net.URL[]{temporary.toUri().toURL()},MacroContext.class.getClassLoader());method=loader.loadClass(className).getMethod("run",MacroContext.class);
        }catch(Exception e){delete();throw e;}
    }
    double apply(NtupleStore.Tuple tuple,long index,double[] row) {
        context.setNtupleRow(tuple,index,row);
        try{method.invoke(null,context);return context.selectionWeight();}
        catch(java.lang.reflect.InvocationTargetException e){if(e.getCause() instanceof java.util.concurrent.CancellationException c)throw c;int line=Arrays.stream(e.getCause().getStackTrace()).filter(frame->frame.getClassName().equals(className)).mapToInt(frame->Math.max(1,frame.getLineNumber()-2)).findFirst().orElse(1);throw new IllegalArgumentException(file+":"+line+" (row "+index+"): "+e.getCause().getMessage(),e.getCause());}
        catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    private void delete()throws java.io.IOException{try(var paths=Files.walk(temporary)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
    @Override public void close()throws Exception{try{loader.close();}finally{delete();}}
}
