package mach.ast;
import java.util.List;
public final class Ast {
    private Ast() {}
    public enum TypeFlavor { CONCRETE, ABSTRACT_CONSTRAINT, BOUND_VARIABLE }
    public enum ConversionMode { SAFE, FORCED }
    public record Span(String file, int line, int column) {
        @Override public String toString() { return file+":"+line+":"+column; }
    }
    public interface Node { Span span(); }
    public interface Stmt extends Node {}
    public interface Expr extends Node {}
    public record Program(List<Stmt> body) {}
    public record Module(String name, Span span) implements Stmt {}
    public record ImportItem(String name, String alias, Span span) {}
    public record Require(String target, List<ImportItem> items, String alias, Span span) implements Stmt {
        public Require(String target, List<ImportItem> items, Span span){this(target,items,null,span);}
    }
    public record Type(String name, List<Type> args, TypeFlavor flavor, Type bound) {
        public Type(String name, List<Type> args) { this(name,args,TypeFlavor.CONCRETE,null); }
        public Type(String name) { this(name,List.of(),TypeFlavor.CONCRETE,null); }
        @Override public String toString() {
            String base=(flavor==TypeFlavor.ABSTRACT_CONSTRAINT?"&":"")+name+(args.isEmpty()?"":"["+String.join(",",args.stream().map(Object::toString).toList())+"]");
            return flavor==TypeFlavor.BOUND_VARIABLE&&bound!=null?base+" & "+bound:base;
        }
    }
    public record Bind(String kind,String name,Type type,boolean converting,Expr value,Span span) implements Stmt {
        public Bind(String kind,String name,Type type,Expr value,Span span){this(kind,name,type,false,value,span);}
    }
    public record Assign(String name,String op,Expr value,Span span) implements Stmt {}
    public record Eval(Expr value,Span span) implements Stmt {}
    public record Return(Expr value,Span span) implements Stmt {}
    public record Branch(List<Expr> conditions,List<List<Stmt>> arms,List<Stmt> otherwise,Span span) implements Stmt {}
    public record While(Expr condition,List<Stmt> body,Span span) implements Stmt {}
    public record BeingLoop(String name,Expr initial,Expr condition,List<Stmt> body,Span span) implements Stmt {}
    public record For(List<String> names,Expr values,List<Stmt> body,Span span) implements Stmt {
        public For(String name,Expr values,List<Stmt> body,Span span){this(List.of(name),values,body,span);}
        public String name(){return names.getFirst();}
    }
    public record Jump(boolean isBreak,Span span) implements Stmt {}
    public record Param(String name,Type type,boolean variadic,boolean converting) {
        public Param(String name,Type type){this(name,type,false);}
        public Param(String name,Type type,boolean variadic){this(name,type,variadic,false);}
    }
    public record Method(Type receiver,List<String> parts,List<Param> params,Type result,Expr guard,List<Stmt> body,Span span) implements Stmt {
        public String selector() { return String.join(":",parts); }
    }
    public record Field(String name,Type type,List<String> labels) {}
    public record Use(String alias,Type type,Span span) implements Node {}
    public record Record(String name,List<String> parameters,Type parent,List<String> derives,List<Field> fields,List<Use> uses,Span span) implements Stmt {
        public Record(String name,List<String> parameters,Type parent,List<String> derives,List<Field> fields,Span span){this(name,parameters,parent,derives,fields,List.of(),span);}
    }
    public record Impl(Type receiver,List<Stmt> body,Span span) implements Stmt {}
    public record BoundedImpl(String variable,Type bound,List<Stmt> body,Span span) implements Stmt {}
    public record CompileFor(String variable,Expr values,List<Stmt> body,Span span) implements Stmt {}
    public record CompileIf(List<Expr> conditions,List<List<Stmt>> arms,List<Stmt> otherwise,Span span) implements Stmt {}
    public record TemplateMethod(List<Expr> parts,List<Param> params,Type result,Expr guard,List<Stmt> body,Span span) implements Stmt {}
    public record Abstract(String name,boolean hierarchy,List<Stmt> body,Span span) implements Stmt {}
    public record Alias(String name,Type type,Span span) implements Stmt {}
    public record Sign(List<Type> inputs,Type output,Span span) implements Stmt {}
    public record Literal(Object value,Type type,Span span) implements Expr {}
    public record Name(String name,Span span) implements Expr {}
    public record Unary(String op,Expr value,Span span) implements Expr {}
    public record Binary(String op,Expr left,Expr right,Span span) implements Expr {}
    public record ComposedArg(String alias,List<Expr> args,Span span) implements Expr {}
    public record Comprehension(List<String> names,Expr values,Expr key,Expr value,Expr filter,Span span) implements Expr {}
    public record Call(Expr receiver,List<String> parts,List<Expr> args,Expr block,Span span) implements Expr {
        public Call(Expr receiver,List<String> parts,List<Expr> args,Span span){this(receiver,parts,args,null,span);}
        public String selector() { return String.join(":",parts); }
    }
    public record Get(Expr receiver,Expr key,Span span) implements Expr {}
    public record ObjectAccess(Expr selector,Expr target,Span span) implements Expr {}
    public record VectorAccess(Expr index,Expr target,Span span) implements Expr {}
    public record ObjectLiteral(List<String> keys,List<Expr> values,Span span) implements Expr {}
    public record Convert(Expr value,Type target,ConversionMode mode,Span span) implements Expr {
        public Convert(Expr value,Type target,Span span){this(value,target,ConversionMode.SAFE,span);}
    }
    public record Rescue(Expr value,Expr handler,Span span) implements Expr {}
    public record BlockExpr(List<Stmt> body,Span span) implements Expr {}
    public record ChainedDo(List<Stmt> yes,List<Stmt> no,Span span) implements Expr {}
    public record TypeTest(String op,Expr value,Type target,Span span) implements Expr {}
    public record TypeExpr(Type type,Span span) implements Expr {}
    public record MatchArm(List<Expr> patterns,Expr guard,Expr body,Span span) implements Node {}
    public record Match(Expr value,List<MatchArm> arms,Expr otherwise,Span span) implements Expr {}
    public record Splice(Expr value,Span span) implements Expr {}
    public record TemplateName(List<Expr> parts,Span span) implements Expr {}
    public record DynamicTag(Expr value,Span span) implements Expr {}
    public record DynamicObject(List<Expr> keys,List<Expr> values,Span span) implements Expr {}
    public record Choice(Expr condition,Expr yes,Expr no,Span span) implements Expr {}
    public record Range(Expr start,Expr end,Expr step,boolean inclusive,Span span) implements Expr {}
    public record Lambda(List<Param> params,Type result,List<Stmt> body,boolean nonLocal,Span span) implements Expr {
        public Lambda(List<Param> params,Type result,List<Stmt> body,Span span){this(params,result,body,false,span);}
    }
    public record CompactVector(List<Expr> items,Span span) implements Expr {}
    public record Vector(List<Expr> items,Span span) implements Expr {}
}
