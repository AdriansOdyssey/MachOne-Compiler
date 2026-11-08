package mach.cli;
import mach.Compiler;
import mach.frontend.Diagnostic;
import mach.runtime.RuntimeSupport;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;

public final class Main {
    public static void main(String[] args) {int code=execute(args);if(code!=0)System.exit(code);}
    public static int execute(String[] args) {
        try {
            if(args.length==0||args[0].equals("help")||args[0].equals("--help")) {System.out.println("mach --version | help | check arquivo.mach | run arquivo.mach | build arquivo.mach | test");return 0;}
            if(args[0].equals("--version")) {System.out.println("MachOne 0.3.0-dev (implementação parcial; consulte docs/implementation-status.md)");return 0;}
            if(args[0].equals("test")) {
                Path wrapper=Path.of("gradlew");if(!Files.isRegularFile(wrapper))throw new IOException("mach test deve executar na raiz do projeto com ./gradlew");
                return new ProcessBuilder("sh","./gradlew",":tests:test","--rerun-tasks").inheritIO().start().waitFor();
            }
            if(args.length!=2||!Set.of("check","build","run").contains(args[0])) {System.err.println("Uso: mach help");return 2;}
            Path source=Path.of(args[1]);String text=Files.readString(source);var compiler=new Compiler();
            if(args[0].equals("check")) {compiler.check(source.toString(),text);System.out.println("Verificação concluída.");return 0;}
            byte[] bytes=compiler.compile(source.toString(),text,"MachProgram");
            if(args[0].equals("run")) {
                Class<?> cls=new ClassLoader(Main.class.getClassLoader()) {Class<?> loadProgram(){return defineClass("MachProgram",bytes,0,bytes.length);}}.loadProgram();
                cls.getMethod("main",String[].class).invoke(null,(Object)new String[0]);return 0;
            }
            String file=source.getFileName().toString().replaceFirst("\\.mach$","");Path output=source.toAbsolutePath().getParent().resolve(file+".jar");
            writeJar(output,bytes);System.out.println(output);return 0;
        } catch(Diagnostic e) {System.err.println(e.getMessage());return 1;}
        catch(InvocationTargetException e) {Throwable cause=e.getCause();System.err.println("Erro: "+cause.getMessage());return 1;}
        catch(ReflectiveOperationException e) {throw new IllegalStateException("artefato gerado inválido",e);}
        catch(NoSuchFileException e) {System.err.println("Erro: arquivo MachOne não encontrado: "+Path.of(e.getFile()).toAbsolutePath().normalize());return 1;}
        catch(IOException|InterruptedException e) {if(e instanceof InterruptedException)Thread.currentThread().interrupt();System.err.println("Erro: "+e.getMessage());return 1;}
    }
    private static void writeJar(Path output,byte[] bytes) throws IOException {
        var manifest=new Manifest();manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION,"1.0");manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS,"MachProgram");
        Path tmp=Files.createTempFile(output.getParent(),".mach-",".jar");
        try {
            try(var jar=new JarOutputStream(Files.newOutputStream(tmp),manifest)) {
                put(jar,"MachProgram.class",bytes);
                URL location=RuntimeSupport.class.getProtectionDomain().getCodeSource().getLocation();Path runtime;
                try {runtime=Path.of(location.toURI());}catch(URISyntaxException e){throw new IOException(e);}
                if(Files.isDirectory(runtime)) {
                    try(var files=Files.walk(runtime.resolve("mach/runtime"))) {for(var f:files.filter(Files::isRegularFile).sorted().toList())put(jar,runtime.relativize(f).toString().replace('\\','/'),Files.readAllBytes(f));}
                } else try(var source=new JarFile(runtime.toFile())) {
                    for(var entry:source.stream().filter(e->!e.isDirectory()&&e.getName().startsWith("mach/runtime/")).toList())try(var in=source.getInputStream(entry)){put(jar,entry.getName(),in.readAllBytes());}
                }
            }
            Files.move(tmp,output,StandardCopyOption.REPLACE_EXISTING);
        } finally {Files.deleteIfExists(tmp);}
    }
    private static void put(JarOutputStream jar,String name,byte[] bytes) throws IOException {jar.putNextEntry(new JarEntry(name));jar.write(bytes);jar.closeEntry();}
}
