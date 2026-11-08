package mach.ir;
import mach.ast.Ast;
import java.util.*;
/** Typed, backend-independent tree IR. Identity maps avoid merging distinct source nodes. */
public record MachIR(List<Ast.Stmt> main,List<Ast.Method> methods,List<Ast.Record> records,
                     Map<Ast.Expr,Ast.Type> types,Map<String,Ast.Type> aliases,Set<Ast.Name> implicitCalls,Set<Ast.Name> typeNames,Set<Ast.Name> patternBindings,Set<Ast.Expr> operatorCalls) {
    public MachIR {
        main=List.copyOf(main); methods=List.copyOf(methods); records=List.copyOf(records);
        var names=Collections.newSetFromMap(new IdentityHashMap<Ast.Name,Boolean>());names.addAll(implicitCalls);implicitCalls=Collections.unmodifiableSet(names);
        var typeRefs=Collections.newSetFromMap(new IdentityHashMap<Ast.Name,Boolean>());typeRefs.addAll(typeNames);typeNames=Collections.unmodifiableSet(typeRefs);
        var patterns=Collections.newSetFromMap(new IdentityHashMap<Ast.Name,Boolean>());patterns.addAll(patternBindings);patternBindings=Collections.unmodifiableSet(patterns);
        var operators=Collections.newSetFromMap(new IdentityHashMap<Ast.Expr,Boolean>());operators.addAll(operatorCalls);operatorCalls=Collections.unmodifiableSet(operators);
        types=Collections.unmodifiableMap(new IdentityHashMap<>(types)); aliases=Map.copyOf(aliases);
    }
}
