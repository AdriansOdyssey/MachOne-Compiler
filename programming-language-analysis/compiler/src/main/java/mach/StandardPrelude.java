package mach;
import mach.ast.Ast;
import mach.frontend.Diagnostic;
import mach.frontend.Frontend;
import mach.frontend.MessageSignatures;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
/** Portable standard library is parsed, type checked and emitted with user code. */
final class StandardPrelude {
    private StandardPrelude() {}
    static Ast.Program parse(String file,String source){
        String library;try(var input=StandardPrelude.class.getResourceAsStream("/mach/stdlib/portable.mach")){if(input==null)throw new IllegalStateException("biblioteca padrão portable.mach ausente");library=new String(input.readAllBytes(),StandardCharsets.UTF_8);}catch(IOException e){throw new UncheckedIOException(e);}
        var signatures=new MessageSignatures();var prelude=new Frontend("<stdlib/portable.mach>",signatures).parse(library);
        var user=new Frontend(file,signatures).parse(source);
        var resolved=resolveModules(file,user,signatures,new LinkedHashSet<>());
        var declarations=new HashSet<String>();for(var s:resolved.body()){if(s instanceof Ast.Record r)declarations.add(r.name());else if(s instanceof Ast.Abstract a)declarations.add(a.name());else if(s instanceof Ast.Alias a)declarations.add(a.name());}
        var body=new ArrayList<Ast.Stmt>();for(var s:prelude.body()){if(s instanceof Ast.Record r&&declarations.contains(r.name()))continue;if(s instanceof Ast.Impl i&&declarations.contains(i.receiver().name()))continue;body.add(s);}body.addAll(resolved.body());return new Ast.Program(List.copyOf(body));
    }
    private static Ast.Program resolveModules(String file,Ast.Program program,MessageSignatures signatures,Set<String> stack){
        var normalized=Path.of(file).toAbsolutePath().normalize().toString();
        if(stack.contains(normalized))throw new Diagnostic("M2402",new Ast.Span(file,1,1),"ciclo de dependência de módulos: "+String.join(" -> ",stack));
        stack.add(normalized);
        var merged=new ArrayList<Ast.Stmt>();
        boolean seenModule=false;
        for(var stmt:program.body()) {
            if(stmt instanceof Ast.Module module) {
                if(seenModule)throw new Diagnostic("M2404",module.span(),"módulo duplicado: "+module.name());
                seenModule=true;
                continue;
            }
            if(stmt instanceof Ast.Require require) {
                var imported=resolveRequire(file,require,signatures,stack);
                for(var item:imported) {if(item instanceof Ast.Module||item instanceof Ast.Require)continue;mergeImported(merged,item);}
                continue;
            }
            merged.add(stmt);
        }
        stack.remove(normalized);
        return new Ast.Program(List.copyOf(merged));
    }
    private static List<Ast.Stmt> resolveRequire(String file,Ast.Require require,MessageSignatures signatures,Set<String> stack){
        var sourcePath=findModule(file,require.target());
        String source;
        try {source=Files.readString(sourcePath,StandardCharsets.UTF_8);} catch(IOException error){throw new UncheckedIOException(error);}
        var moduleProgram=resolveModules(sourcePath.toString(),new Frontend(sourcePath.toString(),signatures).parse(source),signatures,stack);
        var exported=new ArrayList<Ast.Stmt>();
        var exportedByName=new LinkedHashMap<String,Ast.Stmt>();
        for(var stmt:moduleProgram.body()) {
            if(stmt instanceof Ast.Module||stmt instanceof Ast.Require) continue;
            var name=exportName(stmt);
            if(name!=null)exportedByName.put(name,stmt);
        }
        if(require.items().isEmpty()) {
            if(require.alias()!=null)throw new Diagnostic("M2406",require.span(),"alias de módulo exige seleção explícita: require "+require.target()+"/{nome as alias}");
            for(var entry:exportedByName.entrySet())exported.add(rename(entry.getValue(),entry.getKey()));
            return exported;
        }
        var seen=new HashSet<String>();
        for(var item:require.items()) {
            var stmt=exportedByName.get(item.name());
            if(stmt==null)throw new Diagnostic("M2405",item.span(),"nome não exportado: "+item.name()+" em "+require.target());
            var importedName=item.alias()!=null?item.alias():item.name();
            if(!seen.add(importedName))throw new Diagnostic("M2403",item.span(),"conflito de nomes importados: "+importedName);
            exported.add(rename(stmt,importedName));
        }
        return exported;
    }
    private static void mergeImported(List<Ast.Stmt> body,Ast.Stmt stmt){
        var name=exportName(stmt);if(name==null){body.add(stmt);return;}
        for(var existing:body){var other=exportName(existing);if(Objects.equals(name,other))throw new Diagnostic("M2403",stmt.span(),"conflito de nomes importados: "+name);}
        body.add(stmt);
    }
    private static String exportName(Ast.Stmt stmt){
        if(stmt instanceof Ast.Record r)return r.name();
        if(stmt instanceof Ast.Abstract a)return a.name();
        if(stmt instanceof Ast.Alias a)return a.name();
        if(stmt instanceof Ast.Method m){return m.selector();}
        if(stmt instanceof Ast.Impl i)return i.receiver().name();
        if(stmt instanceof Ast.Bind b)return b.name();
        return null;
    }
    private static Ast.Stmt rename(Ast.Stmt stmt,String name){
        if(stmt instanceof Ast.Record r)return new Ast.Record(name,r.parameters(),r.parent(),r.derives(),r.fields(),r.uses(),r.span());
        if(stmt instanceof Ast.Abstract a)return new Ast.Abstract(name,a.hierarchy(),a.body(),a.span());
        if(stmt instanceof Ast.Alias a)return new Ast.Alias(name,a.type(),a.span());
        if(stmt instanceof Ast.Method m){var parts=new ArrayList<String>();parts.add(name);for(int i=1;i<m.parts().size();i++)parts.add(m.parts().get(i));return new Ast.Method(m.receiver(),parts,m.params(),m.result(),m.guard(),m.body(),m.span());}
        if(stmt instanceof Ast.Impl i){return new Ast.Impl(new Ast.Type(name,i.receiver().args()),i.body(),i.span());}
        if(stmt instanceof Ast.Bind b)return new Ast.Bind(b.kind(),name,b.type(),b.value(),b.span());
        return stmt;
    }
    private static Path findModule(String file,String target){
        var normalized=target.replace('.', '/');
        var relative=Path.of(normalized.replace('/', File.separatorChar));
        var candidates=new ArrayList<Path>();
        for(var cursor=Path.of(file).toAbsolutePath().normalize().getParent();cursor!=null;cursor=cursor.getParent()) {
            addModuleCandidates(candidates,cursor,relative);
            var stdlib=cursor.resolve("stdlib");if(Files.isDirectory(stdlib))addModuleCandidates(candidates,stdlib,relative);
        }
        var userDir=Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for(var cursor=userDir;cursor!=null;cursor=cursor.getParent()) {
            addModuleCandidates(candidates,cursor,relative);
            var stdlib=cursor.resolve("stdlib");if(Files.isDirectory(stdlib))addModuleCandidates(candidates,stdlib,relative);
        }
        for(var candidate:candidates){if(Files.isRegularFile(candidate))return candidate;}
        throw new Diagnostic("M2401",new Ast.Span(file,1,1),"módulo não encontrado: "+target);
    }
    private static void addModuleCandidates(List<Path> candidates,Path base,Path relative){
        candidates.add(base.resolve(relative.toString()+".mach"));
        candidates.add(base.resolve(relative.toString()).resolve("main.mach"));
        candidates.add(base.resolve(relative.toString()).resolve("index.mach"));
    }
}
