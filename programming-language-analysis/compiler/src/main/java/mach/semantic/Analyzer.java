package mach.semantic;
import mach.ast.Ast;
import mach.ast.Ast.*;
import mach.frontend.Diagnostic;
import mach.ir.MachIR;
import java.util.*;

/** Resolves symbols and checks expanded AST before any backend is invoked. */
public final class Analyzer {
    private static final Type ANY=new Type("any"),BOOL=new Type("bool"),INT=new Type("int"),NIL=new Type("nil"),STR=new Type("str"),FAIL=new Type("fail"),NEVER=TypeSystem.NEVER;
    private final Set<Name> implicitCalls=Collections.newSetFromMap(new IdentityHashMap<>()),typeNames=Collections.newSetFromMap(new IdentityHashMap<>()),patternBindings=Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<Expr,Type> types=new IdentityHashMap<>();
    private final Set<Expr> operatorCalls=Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<Sign> methodSigns=Collections.newSetFromMap(new IdentityHashMap<>());
    private final List<Method> methods=new ArrayList<>();
    private final Map<String,Ast.Record> records=new LinkedHashMap<>();
    private final Map<String,Type> aliases=new LinkedHashMap<>();
    private final Map<String,Abstract> abstracts=new LinkedHashMap<>();
    private final Set<String> primitive=StandardLibrary.types(Set.of("any","fail","Fail","nil","bool","str","tag","char","regex","byte","ubyte","short","ushort","int","uint","long","ulong","float","double","Number","type","vec","obj","union","maybe","try","lambda","task","inbox","outbox","Vector"));
    private final TypeSystem ts=new TypeSystem(aliases,records);
    private ObjectModel model;
    private boolean discovering;
    private final Map<Method,Type> inferredResults=new IdentityHashMap<>();
    private final Map<Method,List<Type>> methodBlocks=new IdentityHashMap<>();
    private final Map<Method,Map<String,Type>> parameterConstraints=new IdentityHashMap<>();
    private final Scope globals=new Scope(null);
    private ReturnContext context;
    private int loopDepth;
    private record Symbol(Type type,boolean mutable,boolean frozen,Type storage) {
        Symbol(Type type,boolean mutable,boolean frozen){this(type,mutable,frozen,type);}
        Symbol narrowed(Type value){return new Symbol(value,mutable,frozen,storage);}
    }
    private static final class Scope {
        final Scope parent;final Map<String,Symbol> names=new LinkedHashMap<>();final Map<String,Boolean> nonZero=new HashMap<>();
        Scope(Scope parent){this.parent=parent;}
        boolean nonZero(String name){return nonZero.containsKey(name)?nonZero.get(name):parent!=null&&parent.nonZero(name);}
        Symbol get(String name){var s=names.get(name);return s!=null?s:parent==null?null:parent.get(name);}
    }
    private static final class ReturnContext {
        final Type declared;final List<Type> returns=new ArrayList<>();
        Type owner;Scope parameterScope;Set<String> inferable=Set.of();final Map<String,Type> constraints=new HashMap<>();List<Type> blockInputs;
        ReturnContext(Type declared){this.declared=declared;}
    }
    private void error(Node n,String code,String message){throw new Diagnostic(code,n.span(),message);}
    public MachIR analyze(Ast.Program input) {
        var expander=new CompileTimeExpander();Ast.Program program=expander.expand(input,ObjectModel.compositionContexts(input));
        model=new ObjectModel(program,expander.constants());program=model.program();records.putAll(model.records());aliases.putAll(model.aliases());abstracts.putAll(model.abstracts());
        collect(program.body(),null);
        for(var impl:model.derivedImplementations())collect(impl.body(),impl.receiver());
        validateDuplicates();
        List<Stmt> main=program.body().stream().filter(s->!(s instanceof Method||s instanceof Impl||s instanceof BoundedImpl||s instanceof Ast.Record||s instanceof Abstract||s instanceof Alias||(s instanceof Sign sign&&methodSigns.contains(sign)))).toList();
        discovering=true;
        checkBody(main,globals);
        for(int pass=0;pass<=methods.size()+1;pass++) {
            var previous=new IdentityHashMap<>(inferredResults);
            for(var method:methods)checkMethod(method);
            if(previous.equals(inferredResults))break;
        }
        discovering=false;globals.names.clear();context=null;loopDepth=0;
        checkBody(main,globals);
        for(var method:methods)checkMethod(method);
        var emitted=methods.stream().map(m->new Method(m.receiver(),m.parts(),m.params().stream().map(p->new Param(p.name(),effectiveParameter(m,p),p.variadic(),p.converting())).toList(),m.result(),m.guard(),m.body(),m.span())).toList();
        return new MachIR(main,emitted,new ArrayList<>(records.values()),types,aliases,implicitCalls,typeNames,patternBindings,operatorCalls);
    }
    private void collect(List<Stmt> body,Type receiver) {
        Sign pending=null;
        for(var stmt:body) {
            if(stmt instanceof Sign sign){pending=sign;continue;}
            if(stmt instanceof Method m) {
                var params=m.params();Type result=m.result();
                if(pending!=null) {
                    methodSigns.add(pending);
                    if(params.stream().anyMatch(p->p.type()!=null)||result!=null)error(m,"M2005","sign e tipos inline não podem ser combinados");
                    if(pending.inputs().size()!=params.size())error(m,"M2006","aridade incompatível com sign");
                    var signed=new ArrayList<Param>();for(int i=0;i<params.size();i++)signed.add(new Param(params.get(i).name(),pending.inputs().get(i),params.get(i).variadic(),params.get(i).converting()));
                    params=signed;result=pending.output();pending=null;
                }
                methods.add(new Method(receiver,m.parts(),params,result,m.guard(),m.body(),m.span()));
            } else if(stmt instanceof Impl impl){pending=null;collect(impl.body(),impl.receiver());}
            else pending=null;
        }
    }
    private void validateDuplicates() {
        for(int i=0;i<methods.size();i++)for(int j=i+1;j<methods.size();j++) {
            var a=methods.get(i);var b=methods.get(j);
            if(a.guard()==null&&b.guard()==null&&a.params().stream().map(Param::variadic).toList().equals(b.params().stream().map(Param::variadic).toList())&&a.selector().equals(b.selector())&&Objects.equals(a.receiver(),b.receiver())&&a.params().stream().map(p->p.type()==null?ANY:p.type()).toList().equals(b.params().stream().map(p->p.type()==null?ANY:p.type()).toList()))error(b,"M2007","despacho duplicado: "+b.selector());
        }
    }
    private void checkMethod(Method method) {
        Scope scope=new Scope(globals);scope.names.put("&block",new Symbol(new Type("<block>"),false,false));
        if(method.receiver()!=null){Type self=resolve(method.receiver());var declaration=records.get(self.name());if(declaration!=null&&self.args().isEmpty()&&!declaration.parameters().isEmpty())self=new Type(self.name(),declaration.parameters().stream().map(Type::new).toList());scope.names.put("self",new Symbol(self,true,false));}
        for(var param:method.params())if(scope.names.put(param.name(),new Symbol(effectiveParameter(method,param),false,false))!=null)error(method,"M2003","parâmetro duplicado: "+param.name());
        ReturnContext old=context;int oldLoops=loopDepth;context=new ReturnContext(method.result()==null?null:resolve(method.result()));loopDepth=0;
        context.owner=method.receiver();context.parameterScope=scope;context.inferable=method.params().stream().filter(p->p.type()==null).map(Param::name).collect(java.util.stream.Collectors.toSet());
        try {
            if(method.guard()!=null){require(method.guard(),infer(method.guard(),scope),BOOL);applyRefinement(scope,method.guard(),true);}
            Type last=checkBody(method.body(),scope);
            var returns=new ArrayList<>(context.returns);returns.add(last);Type actual=ts.union(returns);
            if(context.declared!=null)require(method,actual,context.declared);
            if(Set.of("eq","==","!=").contains(method.selector()))require(method,actual,BOOL);
            if(method.selector().equals("stringify"))require(method,actual,STR);
            if(method.receiver()!=null&&records.containsKey(method.receiver().name())&&records.get(method.receiver().name()).derives().contains("Ord")&&Set.of("compare","<=>").contains(method.selector()))require(method,actual,INT);
            if(!discovering&&method.receiver()!=null&&method.parts().getFirst().equals("initialize")&&records.containsKey(method.receiver().name()))new InitializationChecker(records.get(method.receiver().name()),types,ts).check(method);
            inferredResults.put(method,actual);if(context.blockInputs!=null)methodBlocks.put(method,context.blockInputs);
            for(var node:descendants(method.body()))if(node instanceof Call call&&call.receiver() instanceof Name name&&name.name().equals("&block")&&call.selector().equals("result"))methodBlocks.put(method,call.args().stream().map(arg->types.getOrDefault(arg,ANY)).toList());
            if(!context.constraints.isEmpty()){var merged=new HashMap<>(parameterConstraints.getOrDefault(method,Map.of()));merged.putAll(context.constraints);parameterConstraints.put(method,merged);}
        } finally {context=old;loopDepth=oldLoops;}
    }
    private static List<Node> descendants(Object source) {
        var result=new ArrayList<Node>();var pending=new ArrayDeque<Object>();pending.add(source);
        while(!pending.isEmpty()) {
            Object value=pending.removeFirst();
            if(value instanceof Iterable<?> items){for(Object item:items)if(item!=null)pending.add(item);}
            else if(value instanceof Node node&&value.getClass().isRecord()) {
                result.add(node);
                for(var component:value.getClass().getRecordComponents())try{Object child=component.getAccessor().invoke(value);if(child instanceof Node||child instanceof Iterable<?>)pending.add(child);}catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
            }
        }
        return result;
    }
    private Type effectiveParameter(Method method,Param param){
        if(param.type()==null&&Set.of("forward","intercept").contains(method.selector())&&method.params().size()==1)return new Type("message");
        Type type=param.type()==null?parameterConstraints.getOrDefault(method,Map.of()).getOrDefault(param.name(),ANY):resolve(param.type());
        return param.variadic()?(param.type()!=null||!type.name().equals("vec")?new Type("vec",List.of(type)):type):type;
    }
    private boolean variadic(Method method){return !method.params().isEmpty()&&method.params().getLast().variadic();}
    private boolean acceptsArity(Method method,int count){return variadic(method)?count>=method.params().size()-1:count==method.params().size();}
    private Type argumentType(Method method,int index){Param param=method.params().get(Math.min(index,method.params().size()-1));Type type=effectiveParameter(method,param);return param.variadic()?type.args().getFirst():type;}
    private Param argumentParam(Method method,int index){return method.params().get(Math.min(index,method.params().size()-1));}
    private Type resolve(Type type){return ts.resolve(type);}
    private Type resolve(Type type,Scope scope) {
        var symbol=scope.get(type.name());
        if(symbol!=null&&symbol.type().name().equals("type")&&symbol.type().args().size()==1&&type.args().isEmpty())return symbol.type().args().getFirst();
        Type bound=type.bound()==null?null:resolve(type.bound(),scope);
        return resolve(new Type(type.name(),type.args().stream().map(a->resolve(a,scope)).toList(),type.flavor(),bound));
    }
    private boolean numeric(Type type){return ts.numeric(type);}
    private boolean integer(Type type){return ts.integer(type);}
    private boolean compatible(Type actual,Type expected){return ts.assignable(actual,expected);}
    private void require(Node node,Type actual,Type expected) {
        if(discovering&&actual.equals(ANY))return;
        if(!compatible(actual,expected))error(node,"M2101","tipo "+actual+" incompatível com "+expected);
    }
    private Type checkBody(List<Stmt> body,Scope scope) {
        Type result=NIL,signed=null;boolean exits=false;
        for(var stmt:body) {
            Type value=NIL;
            if(stmt instanceof Bind b) {
                if(b.kind().equals("var")!=b.name().startsWith("@"))error(b,"M2008","var exige nome iniciado por @");
                var old=scope.names.get(b.name());if(old!=null&&old.frozen())error(b,"M2009","final não permite rebinding: "+b.name());
                Type expected=b.type()==null?signed:resolve(b.type(),scope);signed=null;
                Type actual;
                if(b.converting()){
                    if(expected==null)error(b,"M2105","conversão de binding exige tipo explícito com ^");
                    actual=infer(new Convert(b.value(),expected,ConversionMode.FORCED,b.span()),scope,expected);
                }else actual=infer(b.value(),scope,expected);
                if(expected!=null){require(b,actual,expected);if(!expected.name().equals("type")||!expected.args().isEmpty())actual=expected;}
                boolean nonZero=definitelyNonZero(b.value(),scope);scope.names.put(b.name(),new Symbol(actual,b.kind().equals("var"),b.kind().equals("final")));scope.nonZero.put(b.name(),nonZero);
            } else if(stmt instanceof Assign a) {
                var symbol=scope.get(a.name());if(symbol==null)error(a,"M2001","nome não definido: "+a.name());
                if(!symbol.mutable())error(a,"M2010","atribuição a binding imutável: "+a.name());
                Type actual=a.op().equals("=")?infer(a.value(),scope,symbol.storage()):infer(new Binary(a.op().substring(0,1),new Name(a.name(),a.span()),a.value(),a.span()),scope,symbol.storage());require(a,actual,symbol.storage());
                boolean nonZero=a.op().equals("=")&&definitelyNonZero(a.value(),scope);scope.names.put(a.name(),new Symbol(actual,symbol.mutable(),symbol.frozen(),symbol.storage()));scope.nonZero.put(a.name(),nonZero);
            } else if(stmt instanceof Eval eval)value=infer(eval.value(),scope);
            else if(stmt instanceof Return ret) {
                if(context==null)error(ret,"M2011","return fora de método ou lambda");
                Type actual=infer(ret.value(),scope,context.declared);if(context.declared!=null)require(ret,actual,context.declared);
                context.returns.add(actual);value=NEVER;
            } else if(stmt instanceof Branch branch) {
                boolean allExit=!branch.otherwise().isEmpty();Scope remaining=new Scope(scope);
                for(int i=0;i<branch.conditions().size();i++) {
                    Expr condition=branch.conditions().get(i);require(condition,infer(condition,remaining),BOOL);
                    Scope yes=refine(remaining,condition,true);Type arm=checkBody(branch.arms().get(i),yes);
                    allExit&=arm.equals(NEVER);remaining=refine(remaining,condition,false);
                    if(branch.conditions().size()==1&&branch.otherwise().isEmpty()&&arm.equals(NEVER))applyRefinement(scope,condition,false);
                }
                Type other=checkBody(branch.otherwise(),remaining);allExit&=other.equals(NEVER);
                if(allExit)value=NEVER;
            } else if(stmt instanceof BeingLoop loop) {
                var inner=new Scope(scope);
                Type initial=infer(loop.initial(),scope);
                inner.names.put(loop.name(),new Symbol(initial,false,false));
                require(loop,infer(loop.condition(),inner),BOOL);loopDepth++;
                try{checkBody(loop.body(),inner);}finally{loopDepth--;}
            } else if(stmt instanceof While loop) {
                require(loop,infer(loop.condition(),scope),BOOL);loopDepth++;
                try{checkBody(loop.body(),refine(scope,loop.condition(),true));}finally{loopDepth--;}
            } else if(stmt instanceof For loop) {
                Type values=infer(loop.values(),scope);var inner=iterationScope(loop.names(),values,scope,loop);loopDepth++;
                try{checkBody(loop.body(),inner);}finally{loopDepth--;}
            } else if(stmt instanceof Jump jump){if(loopDepth==0)error(jump,"M2012","break/next fora de laço");value=NEVER;}
            else if(stmt instanceof Sign sign){if(sign.inputs().size()!=1||sign.output()!=null)error(sign,"M2006","sign de binding exige um tipo");signed=resolve(sign.inputs().getFirst(),scope);}
            else error(stmt,"M2999","construção não expandida neste contexto");
            if(!exits){result=value;exits=value.equals(NEVER);}
        }
        return exits?NEVER:result;
    }
    private Scope iterationScope(List<String> names,Type values,Scope scope,Node origin) {
        var inner=new Scope(scope);List<Type> bindings;
        if(values.name().equals("vec")&&names.size()==1)bindings=List.of(values.args().isEmpty()?ANY:values.args().getFirst());
        else if(values.name().equals("obj")&&names.size()==2)bindings=List.of(new Type("tag"),values.args().isEmpty()?ANY:values.args().getFirst());
        else if(values.name().equals("obj")&&names.size()==1)bindings=List.of(values);
        else if(values.equals(STR)&&names.size()==1)bindings=List.of(new Type("char"));
        else if(Set.of("Dictionary","SortedMap").contains(values.name())&&names.size()==2)bindings=List.of(values.args().size()>0?values.args().get(0):ANY,values.args().size()>1?values.args().get(1):ANY);
        else if(ExtraCollectionContracts.TYPES.contains(values.name())&&names.size()==1)bindings=List.of(Set.of("Dictionary","SortedMap").contains(values.name())?new Type("vec",List.of(ANY)):values.args().isEmpty()?ANY:values.args().getFirst());
        else if(records.containsKey(values.name())&&records.get(values.name()).derives().contains("Iter")&&names.size()==1)bindings=List.of(ts.union(records.get(values.name()).fields().stream().map(f->ts.substitute(f.type(),recordBindings(values))).toList()));
        else {error(origin,"M2102","iteração exige coleção com aridade compatível");return inner;}
        var seen=new HashSet<String>();for(int i=0;i<names.size();i++){if(!seen.add(names.get(i)))error(origin,"M2003","nome de iteração duplicado: "+names.get(i));inner.names.put(names.get(i),new Symbol(bindings.get(i),false,false));}return inner;
    }
    private boolean definitelyNonZero(Expr value,Scope scope) {
        if(value instanceof Name name)return scope.nonZero(name.name());
        if(value instanceof Unary unary&&unary.op().equals("-"))return definitelyNonZero(unary.value(),scope);
        if(value instanceof Literal literal&&literal.value() instanceof Number number){Type type=types.getOrDefault(value,literal.type());return type.name().equals("float")?(float)number.doubleValue()!=0:number instanceof java.math.BigInteger integer?integer.signum()!=0:number.doubleValue()!=0;}
        return false;
    }
    private Scope refine(Scope outer,Expr predicate,boolean positive){var result=new Scope(outer);applyRefinement(result,predicate,positive);return result;}
    private void applyRefinement(Scope scope,Expr predicate,boolean positive) {
        if(predicate instanceof Binary conjunction&&Set.of("and","&&").contains(conjunction.op())&&positive){applyRefinement(scope,conjunction.left(),true);applyRefinement(scope,conjunction.right(),true);}
        if(predicate instanceof Binary comparison&&Set.of("!=","==","<",">","<=",">=").contains(comparison.op())) {
            Name name=comparison.left() instanceof Name n?n:comparison.right() instanceof Name n?n:null;
            Expr other=comparison.left()==name?comparison.right():comparison.left();
            if(name!=null&&other instanceof Literal literal&&literal.value() instanceof Number number&&number.doubleValue()==0) {
                var symbol=scope.get(name.name());if(symbol!=null&&numeric(symbol.type())) {
                    String op=comparison.op();if(comparison.right()==name)op=switch(op){case "<"->">";case ">"->"<";case "<="->">=";case ">="->"<=";default->op;};
                    if(positive&&Set.of("!=","<",">").contains(op)||!positive&&Set.of("==","<=",">=").contains(op))scope.nonZero.put(name.name(),true);
                }
            }
        }
        if(predicate instanceof Unary unary&&unary.op().equals("not")){applyRefinement(scope,unary.value(),!positive);return;}
        if(predicate instanceof Call call&&call.receiver() instanceof Name name&&call.args().isEmpty()&&Set.of("ok?","fail?").contains(call.selector())) {
            var symbol=scope.get(name.name());if(symbol==null)return;
            boolean success=call.selector().equals("ok?")==positive;
            Type narrowed=success?ts.successOfTry(symbol.type()):ts.failurePart(symbol.type());
            scope.names.put(name.name(),symbol.narrowed(narrowed));return;
        }
        if(predicate instanceof Binary binary&&Set.of("==","!=").contains(binary.op())) {
            Name name=binary.left() instanceof Name n?n:binary.right() instanceof Name n?n:null;
            Expr other=binary.left()==name?binary.right():binary.left();
            if(name!=null&&other instanceof Literal literal&&literal.value()==null) {
                var symbol=scope.get(name.name());if(symbol!=null)scope.names.put(name.name(),symbol.narrowed((binary.op().equals("==")==positive)?ts.nilPart(symbol.type()):ts.withoutNil(symbol.type())));
            }
        }
        if(predicate instanceof TypeTest test&&test.op().equals("isa")&&test.value() instanceof Name name) {
            var symbol=scope.get(name.name());if(symbol==null)return;
            var members=ts.alternatives(symbol.type()).stream().filter(t->compatible(t,test.target())==positive).toList();
            scope.names.put(name.name(),symbol.narrowed(ts.union(members)));
        }
    }
    private Type contextualLiteral(Literal literal,Type expected) {
        Type target=expected;
        while(target!=null&&Set.of("try","maybe").contains(target.name())&&target.args().size()==1)target=target.args().getFirst();
        if(target!=null&&integer(target)&&(literal.value() instanceof Long||literal.value() instanceof java.math.BigInteger)) {
            java.math.BigInteger n=literal.value() instanceof Long l?java.math.BigInteger.valueOf(l):(java.math.BigInteger)literal.value();
            int bits=switch(target.name()){case "byte","ubyte"->8;case "short","ushort"->16;case "int","uint"->32;default->64;};
            boolean unsigned=target.name().startsWith("u");var min=unsigned?java.math.BigInteger.ZERO:java.math.BigInteger.ONE.shiftLeft(bits-1).negate();var max=java.math.BigInteger.ONE.shiftLeft(unsigned?bits:bits-1).subtract(java.math.BigInteger.ONE);
            if(n.compareTo(min)<0||n.compareTo(max)>0)error(literal,"M2103","literal fora do intervalo de "+target);
            return target;
        }
        if(target!=null&&Set.of("float","double").contains(target.name())&&literal.value() instanceof Number)return target;
        return literal.type();
    }
    private Type infer(Expr expr,Scope scope){return infer(expr,scope,null);}
    private Type infer(Expr expr,Scope scope,Type expected) {
        Type result;
        if(expr instanceof Literal literal)result=contextualLiteral(literal,expected);
        else if(expr instanceof Name name) {
            var symbol=scope.get(name.name());
            if(name.name().equals("&attrs")) {
                if(scope.get("self")==null||!records.containsKey(scope.get("self").type().name()))error(name,"M2013","&attrs exige contexto de record");
                result=new Type("vec",List.of(new Type("obj",List.of(ANY))));
            } else if(name.name().equals("&block")) {
                if(scope.get("&block")==null)error(name,"M2014","&block só é válido dentro de um método com bloco anônimo");
                result=scope.get("&block").type();
            } else if(name.name().equals("&it")) {
                var implicitIt=scope.get("&it");
                if(implicitIt==null)error(name,"M2014","&it só é válido dentro de um bloco anônimo com parâmetro implícito");
                result=implicitIt.type();
            } else if(symbol!=null) {
                if(symbol.type().equals(ANY)&&expected!=null&&!expected.equals(ANY)&&context!=null&&context.inferable.contains(name.name())&&context.parameterScope.get(name.name())==symbol) {
                    context.constraints.put(name.name(),expected);context.parameterScope.names.put(name.name(),symbol.narrowed(expected));result=expected;
                } else result=symbol.type();
            }
            else if(records.containsKey(name.name())||primitive.contains(name.name())||abstracts.containsKey(name.name())||aliases.containsKey(name.name())) {
                if(model.isAbstractDispatch(name.name()))error(name,"M2304","abstract dispatch exige concretização em um record: "+name.name());
                typeNames.add(name);result=new Type("type",List.of(resolve(new Type(name.name()))));
            } else {implicitCalls.add(name);result=call(new Call(null,List.of(name.name()),List.of(),name.span()),scope,expected);}
        } else if(expr instanceof TypeExpr value)result=new Type("type",List.of(resolve(value.type(),scope)));
        else if(expr instanceof Unary unary) {
            if(Set.of("-","~~~","not").contains(unary.op())) {
                Type operand=infer(unary.value(),scope);
                if(methods.stream().anyMatch(m->m.receiver()!=null&&m.selector().equals(unary.op())&&m.params().isEmpty()&&compatible(operand,m.receiver()))) {
                    operatorCalls.add(expr);result=call(new Call(unary.value(),List.of(unary.op()),List.of(),unary.span()),scope,expected);types.put(expr,result);return result;
                }
            }
            if(unary.op().equals("-")&&unary.value() instanceof Literal literal&&(literal.value() instanceof Long||literal.value() instanceof java.math.BigInteger)) {
                java.math.BigInteger number=(literal.value() instanceof Long n?java.math.BigInteger.valueOf(n):(java.math.BigInteger)literal.value()).negate();
                Type defaultType=number.compareTo(java.math.BigInteger.valueOf(Integer.MIN_VALUE))>=0?INT:new Type("long");
                Type inferred=contextualLiteral(new Literal(number,defaultType,unary.span()),expected==null?defaultType:expected);
                types.put(unary.value(),literal.type());types.put(expr,inferred);return inferred;
            }
            Type type=infer(unary.value(),scope,expected);
            result=unary.op().startsWith("acknowledge:")?NIL:unary.op().startsWith("format:")?STR:switch(unary.op()) {
                case "stringify"->STR;case "show"->NIL;case "read"->{require(unary,type,STR);yield STR;}
                case "not"->{require(unary,type,BOOL);yield BOOL;}
                case "-","~~~"->{if((unary.op().equals("~~~")?!integer(type):!numeric(type))&&!type.equals(ANY))error(unary,"M2104","operando numérico inválido");yield type;}
                default->throw new IllegalStateException(unary.op());
            };
        } else if(expr instanceof Binary binary){result=binary(binary,scope,expected);result=interceptResult(new Call(binary.left(),List.of(binary.op()),List.of(binary.right()),binary.span()),types.get(binary.left()),result);}
        else if(expr instanceof ObjectLiteral object) {
            Type element=expected!=null&&expected.name().equals("obj")&&!expected.args().isEmpty()?expected.args().getFirst():ANY;
            for(var value:object.values()){Type valueType=infer(value,scope,element);if(!element.equals(ANY))require(value,valueType,element);}
            result=new Type("obj",List.of(element));
        } else if(expr instanceof ObjectAccess access) {
            result=infer(new Get(access.target(),access.selector(),access.span()),scope,expected);
            types.put(expr,result);
            return result;
        } else if(expr instanceof VectorAccess access) {
            result=infer(new Call(access.target(),List.of("nth"),List.of(access.index()),access.span()),scope,expected);
            types.put(expr,result);
            return result;
        } else if(expr instanceof Get get) {
            Type receiver=infer(get.receiver(),scope),key=infer(get.key(),scope);
            boolean selectorPath=key.name().equals("vec") && key.args().size() == 1;
            if(!key.name().equals("tag") && !selectorPath) require(get,key,new Type("tag"));
            var record=records.get(receiver.name());
            if(methods.stream().anyMatch(m->m.receiver()!=null&&m.selector().equals("'s")&&(compatible(receiver,m.receiver())||receiver.name().equals(m.receiver().name())))) {
                result=call(new Call(get.receiver(),List.of("'s"),List.of(get.key()),get.span()),scope,expected);types.put(expr,result);return result;
            }
            if(receiver.name().equals("message")&&get.key() instanceof Literal literal) {
                result=switch(literal.value().toString()){case "receiver"->ANY;case "name"->new Type("tag");case "parts"->new Type("vec",List.of(new Type("tag")));case "args"->new Type("vec",List.of(ANY));case "block"->new Type("maybe",List.of(new Type("lambda")));default->throw new Diagnostic("M2107",get.span(),"atributo de mensagem inexistente: "+literal.value());};
            } else if(record!=null&&get.key() instanceof Literal literal) {
                String fieldName=literal.value().toString();var field=field(record,fieldName,get);
                privateAccess(get,get.receiver(),field);result=ts.substitute(field.type(),recordBindings(receiver));
            } else if(receiver.name().equals("obj")||selectorPath)result=new Type("maybe",List.of(receiver.name().equals("obj")&& !receiver.args().isEmpty()?receiver.args().getFirst():ANY));
            else if(receiver.name().equals("maybe")&&!receiver.args().isEmpty()) {
                Type inner=receiver.args().getFirst();
                if(inner.name().equals("obj"))result=new Type("maybe",List.of(inner.args().isEmpty()?ANY:inner.args().getFirst()));
                else if(inner.equals(ANY)||inner.name().equals("nil"))result=new Type("maybe",List.of(ANY));
                else if(ts.alternatives(inner).stream().allMatch(t->t.name().equals("obj")||t.name().equals("nil")))result=new Type("maybe",List.of(ts.union(ts.alternatives(inner).stream().filter(t->t.name().equals("obj")).map(t->t.args().isEmpty()?ANY:t.args().getFirst()).toList())));
                else {error(get,"M2107","acesso a atributo não aplicável");return ANY;}
            }
            else if(ts.alternatives(receiver).stream().allMatch(t->t.name().equals("obj")||t.name().equals("nil")))result=new Type("maybe",List.of(ts.union(ts.alternatives(receiver).stream().filter(t->t.name().equals("obj")).map(t->t.args().isEmpty()?ANY:t.args().getFirst()).toList())));
            else {error(get,"M2107","acesso a atributo não aplicável");return ANY;}
            result=interceptResult(new Call(get.receiver(),List.of("'s"),List.of(get.key()),get.span()),receiver,result);
        } else if(expr instanceof Convert convert) {
            Type from=infer(convert.value(),scope),target=resolve(convert.target(),scope);
            if(!(numeric(target)||Set.of("str","bool","char").contains(target.name())))error(convert,"M2105","conversão não implementada para "+target);
            if(!(target.name().equals("str")||numeric(from)||Set.of("str","bool","char").contains(from.name())))error(convert,"M2105","conversão não aplicável a "+from);
            result=convert.mode()==ConversionMode.FORCED?target:new Type("try",List.of(target));
        } else if(expr instanceof Rescue rescue) {
            Type value=infer(rescue.value(),scope);
            Type handler=infer(rescue.handler(),scope,new Type("lambda",List.of(FAIL,expected==null?ANY:expected)));
            if(!handler.name().equals("lambda")||handler.args().size()!=2)error(rescue,"M2106","rescue exige lambda com um argumento");
            result=ts.rescued(value,handler.args().getLast());
        } else if(expr instanceof Choice choice) {
            require(choice,infer(choice.condition(),scope),BOOL);
            Type yes=infer(choice.yes(),refine(scope,choice.condition(),true),expected),no=infer(choice.no(),refine(scope,choice.condition(),false),expected);
            if(yes.equals(NEVER)&&!no.equals(NEVER))applyRefinement(scope,choice.condition(),false);
            if(no.equals(NEVER)&&!yes.equals(NEVER))applyRefinement(scope,choice.condition(),true);
            result=ts.join(yes,no);
        } else if(expr instanceof BlockExpr block)result=checkBody(block.body(),new Scope(scope));
        else if(expr instanceof TypeTest test){infer(test.value(),scope);result=BOOL;}
        else if(expr instanceof Match match) {
            Type subject=infer(match.value(),scope);var outputs=new ArrayList<Type>();
            for(var arm:match.arms()) {
                var inner=new Scope(scope);Set<String> armBindings=null;
                for(var pattern:arm.patterns()) {
                    Set<String> bindings=new LinkedHashSet<>();
                    if(pattern instanceof Name name&&scope.get(name.name())==null&&!primitive.contains(name.name())&&!records.containsKey(name.name())) {
                        patternBindings.add(name);if(!name.name().equals("_")){bindings.add(name.name());inner.names.put(name.name(),new Symbol(subject,false,false));}
                    } else infer(pattern,scope);
                    if(armBindings!=null&&!armBindings.equals(bindings))error(pattern,"M2110","alternativas do mesmo braço devem ligar os mesmos nomes");armBindings=bindings;
                }
                if(arm.guard()!=null)require(arm.guard(),infer(arm.guard(),inner),BOOL);
                outputs.add(infer(arm.body(),inner,expected));
            }
            outputs.add(match.otherwise()==null?NIL:infer(match.otherwise(),scope,expected));result=ts.union(outputs);
        }
        else if(expr instanceof Range range) {
            Type from=infer(range.start(),scope),to=infer(range.end(),scope),step=infer(range.step(),scope);if(!integer(from)||!integer(to)||!integer(step))error(range,"M2104","range exige limites e passo inteiros");result=new Type("vec",List.of(from));
        } else if(expr instanceof Comprehension comprehension) {
            Type source=infer(comprehension.values(),scope);Scope inner=iterationScope(comprehension.names(),source,scope,comprehension);
            if(comprehension.filter()!=null)require(comprehension.filter(),infer(comprehension.filter(),inner),BOOL);
            Type value=infer(comprehension.value(),inner,expected!=null&&!expected.args().isEmpty()?expected.args().getFirst():null);
            if(comprehension.key()!=null){require(comprehension,infer(comprehension.key(),inner),new Type("tag"));result=new Type("obj",List.of(value));}else result=new Type("vec",List.of(value));
        } else if(expr instanceof Ast.CompactVector vector) {
            Type element=expected!=null&&expected.name().equals("vec")&&!expected.args().isEmpty()?expected.args().getFirst():null;
            if(element==null) {
                if(vector.items().isEmpty()) element=ANY;
                else {
                    var inferred=vector.items().stream().map(item->infer(item,scope)).toList();
                    Type first=inferred.getFirst();
                    if(inferred.stream().anyMatch(type->!type.equals(first)))error(vector,"M2101","literal compacto %[...] exige elementos do mesmo tipo");
                    element=first;
                }
            }
            if(element!=null&&element.flavor()==TypeFlavor.BOUND_VARIABLE) {
                Type bound=element.bound()==null?ANY:element.bound();
                Type chosen=null;
                for(var item:vector.items()) {
                    Type inferred=infer(item,scope,bound);
                    require(item,inferred,bound);
                    if(chosen==null)chosen=inferred;
                    else if(!chosen.equals(inferred))error(vector,"M2101","coleção vec[T & ...] exige elementos homogêneos para T");
                }
                element=chosen==null?bound:chosen;
            }
            for(var item:vector.items())require(item,infer(item,scope,element),element);result=new Type("vec",List.of(element));
        } else if(expr instanceof Ast.Vector vector) {
            Type element=expected!=null&&expected.name().equals("vec")&&!expected.args().isEmpty()?expected.args().getFirst():null;
            if(element==null) {
                if(vector.items().isEmpty()) element=ANY;
                else {
                    var inferred=vector.items().stream().map(item->infer(item,scope)).toList();
                    element=inferred.stream().distinct().reduce((a,b)->ts.union(List.of(a,b))).orElse(ANY);
                    if(inferred.stream().map(Type::toString).distinct().count() == 1) element=inferred.getFirst();
                }
            }
            if(element!=null&&element.flavor()==TypeFlavor.BOUND_VARIABLE) {
                Type bound=element.bound()==null?ANY:element.bound();
                Type chosen=null;
                for(var item:vector.items()) {
                    Type inferred=infer(item,scope,bound);
                    require(item,inferred,bound);
                    if(chosen==null)chosen=inferred;
                    else if(!chosen.equals(inferred))error(vector,"M2101","coleção vec[T & ...] exige elementos homogêneos para T");
                }
                element=chosen==null?bound:chosen;
            }
            for(var item:vector.items())require(item,infer(item,scope,element),element);result=new Type("vec",List.of(element));
        } else if(expr instanceof Lambda lambda) {
            var inner=new Scope(scope);var inputs=new ArrayList<Type>();
            for(int i=0;i<lambda.params().size();i++) {
                var param=lambda.params().get(i);Type type=param.type()==null?ANY:resolve(param.type(),scope);
                if(param.type()==null&&expected!=null&&expected.name().equals("lambda")&&expected.args().size()==lambda.params().size()+1)type=expected.args().get(i);
                inputs.add(type);inner.names.put(param.name(),new Symbol(type,false,false));
            }
            ReturnContext previous=context;int previousLoops=loopDepth;if(!lambda.nonLocal()){context=new ReturnContext(lambda.result()==null?null:resolve(lambda.result(),scope));context.owner=previous==null?null:previous.owner;}loopDepth=0;
            try {
                Type last=checkBody(lambda.body(),inner);var outputs=new ArrayList<Type>();if(!lambda.nonLocal())outputs.addAll(context.returns);outputs.add(last);Type output=ts.union(outputs);
                if(!lambda.nonLocal()&&context.declared!=null){require(lambda,output,context.declared);output=context.declared;}
                inputs.add(output);result=new Type("lambda",List.copyOf(inputs));
            } finally{context=previous;loopDepth=previousLoops;}
        } else if(expr instanceof Call call)result=call(call,scope,expected);
        else throw new IllegalStateException("AST não expandida: "+expr);
        types.put(expr,result);return result;
    }
    private Field field(Ast.Record record,String name,Node origin) {
        var found=record.fields().stream().filter(f->f.name().equals(name)||f.name().equals("_"+name)).findFirst();
        if(found.isEmpty())error(origin,"M2107","atributo inexistente: "+name);return found.get();
    }
    private void privateAccess(Node origin,Expr receiver,Field field){
        Type recipient=types.get(receiver);boolean sameImpl=context!=null&&context.owner!=null&&recipient!=null&&context.owner.name().equals(recipient.name());
        if(field.name().startsWith("_")&&!sameImpl)error(origin,"M2108","atributo privado: "+field.name());
    }
    private Map<String,Type> recordBindings(Type receiver) {
        var map=new HashMap<String,Type>();var record=records.get(receiver.name());
        if(record!=null)for(int i=0;i<Math.min(record.parameters().size(),receiver.args().size());i++)map.put(record.parameters().get(i),receiver.args().get(i));
        return map;
    }
    private Type binary(Binary binary,Scope scope,Type expected) {
        String op=binary.op();
        boolean comparison=Set.of("==","!=","like","~","<",">","<=",">=").contains(op);
        Type hint=!comparison&&expected!=null&&(numeric(expected)||expected.equals(STR))?expected:null;
        Type left=infer(binary.left(),scope,hint);
        Type right=infer(binary.right(),scope,hint!=null?hint:!comparison&&binary.right() instanceof Name&&numeric(left)?left:null);
        boolean overloaded=false;
        for(var method:methods)if(method.receiver()!=null&&method.selector().equals(op)&&method.params().size()==1) {
            var bindings=new HashMap<String,Type>(recordBindings(left));
            if(instantiate(left,method.receiver(),bindings)&&instantiate(right,effectiveParameter(method,method.params().getFirst()),bindings)){overloaded=true;break;}
        }
        if(overloaded){operatorCalls.add(binary);return call(new Call(binary.left(),List.of(op),List.of(binary.right()),binary.span()),scope,expected);}
        if(op.equals("+")&&left.name().equals("vec")&&right.name().equals("vec")){require(binary,right,left);return left;}
        if(op.equals("<<")&&left.name().equals("vec")){require(binary,right,left.args().getFirst());return left;}
        if(op.equals(">>")&&right.name().equals("vec")){require(binary,left,right.args().getFirst());return right;}
        if(Set.of("|","<<").contains(op)&&left.name().equals("obj")){require(binary,right,left);return left;}
        if(op.equals("|")) {
            var record=records.get(left.name());if(record==null||!(binary.right() instanceof ObjectLiteral))error(binary,"M2999","copy-with exige record e mapa literal");
            var update=(ObjectLiteral)binary.right();
            for(int i=0;i<update.keys().size();i++){var field=field(record,update.keys().get(i),binary);privateAccess(binary,binary.left(),field);require(binary,infer(update.values().get(i),scope,ts.substitute(field.type(),recordBindings(left))),ts.substitute(field.type(),recordBindings(left)));}
            return left;
        }
        if(Set.of("and","or","&&","||").contains(op)){require(binary,left,BOOL);require(binary,right,BOOL);return BOOL;}
        if(op.equals("?:"))return ts.elvis(left,right);
        if(Set.of("==","!=","like","~").contains(op))return BOOL;
        if(Set.of("<",">","<=",">=").contains(op)&&(left.equals(STR)&&right.equals(STR)||left.name().equals("char")&&right.name().equals("char")))return BOOL;
        if(op.equals("+")&&left.equals(STR)&&right.equals(STR))return STR;
        if(op.equals("*")&&left.equals(STR)&&integer(right))return STR;
        if(left.equals(ANY)||right.equals(ANY))return Set.of("<",">","<=",">=").contains(op)?BOOL:ANY;
        if(numeric(left)&&numeric(right)) {
            if(Set.of("<<<",">>>","&&&","^^^","|||").contains(op)&&(!integer(left)||!integer(right)))error(binary,"M2104","bitwise exige inteiros");
            Type result=op.equals("/")||left.name().equals("double")||right.name().equals("double")?new Type("double"):left.name().equals("float")||right.name().equals("float")?new Type("float"):left;
            if(Set.of("<",">","<=",">=").contains(op))return BOOL;
            return Set.of("//","%").contains(op)||op.equals("/")&&!definitelyNonZero(binary.right(),scope)?new Type("try",List.of(result)):result;
        }
        return call(new Call(binary.left(),List.of(op),List.of(binary.right()),binary.span()),scope,expected);
    }
    private boolean generic(Type type){return type.flavor()==TypeFlavor.BOUND_VARIABLE||type.name().matches("[A-Z]")&&type.args().isEmpty()&&!records.containsKey(type.name())&&!abstracts.containsKey(type.name());}
    private boolean instantiate(Type actual,Type expected,Map<String,Type> bindings) {
        expected=resolve(expected);actual=resolve(actual);
        if(expected.flavor()==TypeFlavor.ABSTRACT_CONSTRAINT)return compatible(actual,new Type(expected.name(),expected.args()));
        if(expected.flavor()==TypeFlavor.BOUND_VARIABLE){
            if(expected.bound()!=null&&!compatible(actual,expected.bound()))return false;
            Type previous=bindings.putIfAbsent(expected.name(),actual);
            return previous==null||previous.equals(actual);
        }
        if(generic(expected)){Type previous=bindings.putIfAbsent(expected.name(),actual);return previous==null||previous.equals(actual);}
        if(actual.name().equals(expected.name())&&actual.args().size()==expected.args().size()&&!expected.args().isEmpty()) {
            for(int i=0;i<actual.args().size();i++)if(!instantiate(actual.args().get(i),expected.args().get(i),bindings))return false;return true;
        }
        if(actual.name().equals(expected.name())&&expected.args().isEmpty()&&records.containsKey(actual.name()))return true;
        return compatible(actual,expected);
    }
    private boolean canConvertType(Type from,Type to){
        from=resolve(from);to=resolve(to);
        if(compatible(from,to))return true;
        if(to.name().equals("char"))return integer(from)||from.name().equals("str");
        if(Set.of("str","bool").contains(to.name()))return Set.of("str","bool","char").contains(from.name())||numeric(from);
        return numeric(to)&&(numeric(from)||Set.of("str","char","bool").contains(from.name()));
    }
    private boolean matchesArgument(Method method,int index,Type actual,Map<String,Type> bindings,boolean allowConversions){
        Type expected=ts.substitute(argumentType(method,index),bindings);
        Param parameter=argumentParam(method,index);
        if(instantiate(actual,expected,bindings))return true;
        return allowConversions&&parameter.converting()&&canConvertType(actual,expected);
    }
    private boolean moreSpecific(Method a,Method b,int arity) {
        boolean strict=false;
        if(a.receiver()!=null){if(!compatible(a.receiver(),b.receiver()))return false;strict=!a.receiver().equals(b.receiver());}
        for(int i=0;i<arity;i++) {
            Type x=argumentType(a,i),y=argumentType(b,i);
            if(generic(x))x=ANY;if(generic(y))y=ANY;if(!compatible(x,y))return false;strict|=!x.equals(y);
        }
        return strict||!variadic(a)&&variadic(b);
    }
    private Type argumentHint(Call call,Type receiver,int index,Type expected) {
        if(call.args().get(index) instanceof Lambda){var prefix=new ArrayList<Type>();for(int i=0;i<index;i++)prefix.add(types.getOrDefault(call.args().get(i),ANY));var inputs=StandardLibrary.blockInputs(receiver,call.selector(),prefix);if(inputs!=null){var hint=new ArrayList<>(inputs);hint.add(ANY);return new Type("lambda",hint);}}

        if(index==0&&receiver!=null&&Set.of("each","collect","select","reject","times").contains(call.selector())) {
            if(receiver.name().equals("vec"))return new Type("lambda",List.of(receiver.args().getFirst(),Set.of("select","reject").contains(call.selector())?BOOL:ANY));
            if(receiver.name().equals("obj"))return new Type("lambda",List.of(new Type("tag"),receiver.args().getFirst(),ANY));
            if(integer(receiver)&&call.selector().equals("times"))return new Type("lambda",List.of(ANY));
        }
        if(receiver!=null&&receiver.name().equals("type")&&receiver.args().size()==1&&(call.selector().equals("new")||call.selector().equals("create"))) {
            Type factory=receiver.args().getFirst(),target=expected;
            while(target!=null&&Set.of("try","maybe").contains(target.name()))target=target.args().getFirst();
            if(factory.args().isEmpty()&&target!=null&&target.name().equals(factory.name()))factory=target;
            var record=records.get(factory.name());
            if(record!=null&&record.derives().contains("Init")&&index<record.fields().size())return ts.substitute(record.fields().get(index).type(),recordBindings(factory));
        }
        var hints=new LinkedHashSet<Type>();
        for(var m:methods)if(m.selector().equals(call.selector())&&acceptsArity(m,call.args().size())&&(receiver==null)==(m.receiver()==null)) {
            if(receiver==null||compatible(receiver,m.receiver())||receiver.name().equals(m.receiver().name()))hints.add(ts.substitute(argumentType(m,index),receiver==null?Map.of():recordBindings(receiver)));
        }
        return hints.size()==1?hints.iterator().next():null;
    }
    private Type call(Call call,Scope scope,Type expected) {
        Type receiver=call.receiver()==null?null:infer(call.receiver(),scope);
        boolean conditional=receiver!=null&&receiver.equals(BOOL)&&Set.of("then","otherwise","then:otherwise").contains(call.selector());
        var arguments=new ArrayList<Type>();
        for(int i=0;i<call.args().size();i++){boolean positive=call.selector().equals("then")||call.selector().equals("then:otherwise")&&i==0;Type argument=infer(call.args().get(i),conditional?refine(scope,call.receiver(),positive):scope,argumentHint(call,receiver,i,expected));arguments.add(argument);if(conditional&&call.args().get(i) instanceof Lambda lambda&&lambda.nonLocal()&&!argument.args().isEmpty()&&argument.args().getLast().equals(NEVER))applyRefinement(scope,call.receiver(),!positive);}
        Type anonymous=null;
        if(call.block()!=null){List<Type> inputs=StandardLibrary.blockInputs(receiver,call.selector(),arguments);Type hint=null;if(inputs!=null){var signature=new ArrayList<>(inputs);signature.add(ANY);hint=new Type("lambda",signature);}anonymous=infer(call.block(),scope,hint);if(!anonymous.name().equals("lambda"))error(call,"M2204","bloco anônimo exige lambda");}
        for(var method:methods)if(discovering&&variadic(method)&&method.params().getLast().type()==null&&method.selector().equals(call.selector())&&acceptsArity(method,arguments.size())&&(receiver==null)==(method.receiver()==null)&&(receiver==null||compatible(receiver,method.receiver())||receiver.name().equals(method.receiver().name()))) {
            int start=method.params().size()-1;if(arguments.size()>start){Type element=ts.union(arguments.subList(start,arguments.size()));var constraints=new HashMap<>(parameterConstraints.getOrDefault(method,Map.of()));Type old=constraints.get(method.params().getLast().name());if(old!=null&&old.name().equals("vec"))element=ts.union(old.args().getFirst(),element);constraints.put(method.params().getLast().name(),new Type("vec",List.of(element)));parameterConstraints.put(method,constraints);}
        }
        var applicable=new ArrayList<Method>();var substitutions=new IdentityHashMap<Method,Map<String,Type>>();
        for(var method:methods) {
            if(!method.selector().equals(call.selector())||!acceptsArity(method,arguments.size())||(receiver==null)!=(method.receiver()==null))continue;
            var bindings=receiver==null?new HashMap<String,Type>():new HashMap<>(recordBindings(receiver));
            if(receiver!=null&&!instantiate(receiver,method.receiver(),bindings))continue;
            boolean fits=true;for(int i=0;i<arguments.size();i++)if(!matchesArgument(method,i,arguments.get(i),bindings,false))fits=false;
            if(fits){applicable.add(method);substitutions.put(method,bindings);}
        }
        if(applicable.isEmpty())for(var method:methods) {
            if(!method.selector().equals(call.selector())||!acceptsArity(method,arguments.size())||(receiver==null)!=(method.receiver()==null))continue;
            var bindings=receiver==null?new HashMap<String,Type>():new HashMap<>(recordBindings(receiver));
            if(receiver!=null&&!instantiate(receiver,method.receiver(),bindings))continue;
            boolean fits=true;for(int i=0;i<arguments.size();i++)if(!matchesArgument(method,i,arguments.get(i),bindings,true))fits=false;
            if(fits){applicable.add(method);substitutions.put(method,bindings);}
        }
        if(!applicable.isEmpty()) {
            var maximal=applicable.stream().filter(m->applicable.stream().noneMatch(other->moreSpecific(other,m,arguments.size()))).toList();
            if(maximal.size()>1) {
                var first=maximal.getFirst();if(maximal.stream().anyMatch(m->!Objects.equals(m.receiver(),first.receiver())||!m.params().stream().map(Param::type).toList().equals(first.params().stream().map(Param::type).toList())))error(call,"M2203","despacho ambíguo: "+call.selector());
            }
            Method method=maximal.getFirst();
            if(methodBlocks.containsKey(method)) {
                if(call.block()==null)error(call,"M2204","mensagem exige bloco anônimo =>: "+call.selector());
                var blockInput=methodBlocks.get(method);var hint=new ArrayList<Type>(blockInput);hint.add(ANY);
                anonymous=infer(call.block(),scope,new Type("lambda",hint));
                if(anonymous.args().size()!=blockInput.size()+1)error(call,"M2201","aridade inválida do bloco anônimo");
                for(int i=0;i<blockInput.size();i++)require(call,blockInput.get(i),anonymous.args().get(i));
            }
            var results=new ArrayList<Type>();for(var selected:maximal){Type result=selected.result()==null?inferredResults.getOrDefault(selected,ANY):resolve(selected.result());results.add(ts.substitute(result,substitutions.get(selected)));}
            return interceptResult(call,receiver,ts.union(results));
        }
        Type builtin=builtin(call,receiver,arguments,scope,expected);
        if(builtin!=null)return interceptResult(call,receiver,builtin);
        var receiverVariants=receiver==null?Collections.<Type>singletonList(null):dispatchVariants(receiver);
        var argumentVariants=arguments.stream().map(this::dispatchVariants).toList();
        if(receiverVariants.size()>1||argumentVariants.stream().anyMatch(v->v.size()>1)||receiver!=null&&model.isAbstractData(receiver.name())) {
            var tuples=new ArrayList<List<Type>>();expandTuples(argumentVariants,0,new ArrayList<>(),tuples);
            var outputs=new ArrayList<Type>();boolean covered=true;
            for(Type actualReceiver:receiverVariants)for(var tuple:tuples) {
                var candidates=new ArrayList<Method>();var bindings=new IdentityHashMap<Method,Map<String,Type>>();
                for(var method:methods) {
                    if(!method.selector().equals(call.selector())||!acceptsArity(method,tuple.size())||(actualReceiver==null)!=(method.receiver()==null))continue;
                    var vars=actualReceiver==null?new HashMap<String,Type>():new HashMap<>(recordBindings(actualReceiver));
                    if(actualReceiver!=null&&!instantiate(actualReceiver,method.receiver(),vars))continue;
                    boolean fits=true;for(int i=0;i<tuple.size();i++)if(!matchesArgument(method,i,tuple.get(i),vars,true))fits=false;
                    if(fits){candidates.add(method);bindings.put(method,vars);}
                }
                if(candidates.isEmpty()){covered=false;break;}
                var selected=candidates.stream().filter(m->candidates.stream().noneMatch(other->moreSpecific(other,m,tuple.size()))).toList();
                if(selected.size()>1) {
                    var first=selected.getFirst();if(selected.stream().anyMatch(m->!Objects.equals(m.receiver(),first.receiver())||!m.params().stream().map(Param::type).toList().equals(first.params().stream().map(Param::type).toList())))error(call,"M2203","despacho ambíguo para alternativa de tipo: "+call.selector());
                }
                for(var method:selected)outputs.add(ts.substitute(method.result()==null?inferredResults.getOrDefault(method,ANY):resolve(method.result()),bindings.get(method)));
            }
            if(covered&&!outputs.isEmpty())return ts.union(outputs);
        }
        if(receiver!=null&&hasDynamicProtocol(receiver))return new Type("try",List.of(ANY));
        error(call,"M2202","nenhum despacho aplicável: "+call.selector()+" "+arguments);return ANY;
    }
    private boolean hasDynamicProtocol(Type receiver){var record=records.get(receiver.name());return record!=null&&(record.derives().contains("Proxy")||methods.stream().anyMatch(m->m.receiver()!=null&&m.receiver().name().equals(receiver.name())&&Set.of("forward","intercept").contains(m.selector())));}
    private Type interceptResult(Call call,Type receiver,Type result){if(receiver==null||call.receiver() instanceof Name name&&name.name().equals("self"))return result;for(var hook:methods)if(hook.receiver()!=null&&hook.receiver().name().equals(receiver.name())&&hook.selector().equals("intercept")&&hook.params().size()==1)return hook.result()==null?inferredResults.getOrDefault(hook,new Type("try",List.of(ANY))):resolve(hook.result());return result;}
    private List<Type> dispatchVariants(Type type) {
        var result=new ArrayList<Type>();
        for(var member:ts.alternatives(type)) {
            if(model.isAbstractData(member.name())) {
                var concrete=records.values().stream().filter(r->compatible(new Type(r.name()),member)).map(r->new Type(r.name())).toList();
                if(concrete.isEmpty())result.add(member);else result.addAll(concrete);
            } else result.add(member);
        }
        return result;
    }
    private void expandTuples(List<List<Type>> alternatives,int index,List<Type> prefix,List<List<Type>> result) {
        if(result.size()>1024)throw new IllegalArgumentException("alternativas de despacho excedem o limite de análise");
        if(index==alternatives.size()){result.add(List.copyOf(prefix));return;}
        for(var type:alternatives.get(index)){prefix.add(type);expandTuples(alternatives,index+1,prefix,result);prefix.removeLast();}
    }
    private Type builtin(Call call,Type receiver,List<Type> args,Scope scope,Type expected) {
        String selector=call.selector();
        if(receiver==null&&Set.of("show","read").contains(selector)&&args.size()==1){if(selector.equals("read"))require(call,args.getFirst(),STR);return selector.equals("show")?NIL:STR;}
        if(receiver!=null&&receiver.name().equals("type")&&Set.of("name","attrs","methods","supertypes","subtypes","derives?").contains(selector)) {
            if(selector.equals("derives?")){if(args.size()!=1)error(call,"M2201","derives? exige um argumento");return BOOL;}
            if(!args.isEmpty())error(call,"M2201","aridade inválida de "+selector);return switch(selector){case "name"->STR;case "attrs"->new Type("vec",List.of(new Type("obj",List.of(ANY))));case "methods"->new Type("vec",List.of(new Type("tag")));default->new Type("vec",List.of(new Type("type")));};
        }
        if(receiver!=null&&records.containsKey(receiver.name())&&selector.equals("slots")&&args.isEmpty())return new Type("vec",List.of(new Type("tag")));
        boolean sourceFactory=receiver!=null&&receiver.name().equals("type")&&receiver.args().size()==1&&records.containsKey(receiver.args().getFirst().name())&&(selector.equals("new")||selector.equals("create")||selector.startsWith("new:")||selector.startsWith("create:"));
        Type nativeResult=sourceFactory?null:StandardLibrary.resolve(receiver,selector,args,call.block()==null?null:types.get(call.block()),call.span(),ts);if(nativeResult!=null)return nativeResult;
        if(receiver==null)return null;
        if(receiver.equals(NEVER))return NEVER;
        if(args.size()==1&&Set.of("==","!=","like","~").contains(selector))return BOOL;
        if(args.size()==1&&Set.of("and","or","&&","||").contains(selector)&&receiver.equals(BOOL)){require(call,args.getFirst(),BOOL);return BOOL;}
        if(args.size()==1&&numeric(receiver)&&numeric(args.getFirst())&&Set.of("+","-","*","/","//","%","**","<",">","<=",">=","&&&","|||","^^^","<<<",">>>").contains(selector))return binary(new Binary(selector,call.receiver(),call.args().getFirst(),call.span()),scope,expected);
        if(args.isEmpty()&&Set.of("-","~~~").contains(selector)&&numeric(receiver)){if(selector.equals("~~~")&&!integer(receiver))error(call,"M2104","bitwise exige inteiro");return receiver;}
        if(args.size()==1&&receiver.equals(STR)&&Set.of("+","*","<",">","<=",">=").contains(selector)){if(selector.equals("*")){if(!integer(args.getFirst()))require(call,args.getFirst(),INT);}else require(call,args.getFirst(),STR);return Set.of("<",">","<=",">=").contains(selector)?BOOL:STR;}
        if(args.size()==1&&(receiver.name().equals("vec")||receiver.name().equals("obj"))&&Set.of("+","<<","|",">>").contains(selector))return binary(new Binary(selector,call.receiver(),call.args().getFirst(),call.span()),scope,expected);
        if(selector.equals("?:")&&args.size()==1)return ts.elvis(receiver,args.getFirst());
        if(selector.equals("perform")||selector.equals("perform:with")){if(args.isEmpty()||args.size()>2)error(call,"M2201","perform exige seletor e argumentos opcionais");Type target=args.getFirst();if(!target.name().equals("tag")&&!(target.name().equals("vec")&&target.args().getFirst().name().equals("tag")))error(call,"M2101","perform exige tag ou vec[tag]");return new Type("try",List.of(ANY));}
        if(receiver.name().equals("message")&&Set.of("proceed","proceed_with","redirect").contains(selector)){if(args.size()!=(selector.equals("proceed")?0:1))error(call,"M2201","aridade inválida de "+selector);return new Type("try",List.of(ANY));}
        if(receiver.name().equals("type")&&receiver.args().size()==1&&receiver.args().getFirst().name().equals("Fail")&&selector.equals("raise")) {
            if(args.size()==1)require(call,args.getFirst(),STR);else if(args.size()==2){require(call,args.getFirst(),new Type("tag"));require(call,args.getLast(),STR);}else error(call,"M2201","raise exige mensagem ou tag e mensagem");return FAIL;
        }
        if(Set.of("ok?","fail?").contains(selector)&&args.isEmpty())return BOOL;
        if(receiver.name().equals("fail")&&selector.equals("kind")&&args.isEmpty())return new Type("tag");
        if(selector.equals("type")&&args.isEmpty())return new Type("type",List.of(receiver));
        if(receiver.name().equals("<block>")&&selector.equals("result")){if(context!=null)context.blockInputs=List.copyOf(args);return ANY;}
        if(args.size()==1&&Set.of("each","collect","select","reject","times").contains(selector)) {
            Type fn=args.getFirst();if(!fn.name().equals("lambda"))error(call,"M2106","iteração exige bloco ou lambda");
            if(selector.equals("times")&&integer(receiver)){if(fn.args().size()!=1)error(call,"M2201","times exige bloco sem parâmetros");return NIL;}
            if(receiver.name().equals("vec")) {
                if(fn.args().size()!=2)error(call,"M2201","iteração de vetor exige um parâmetro");
                if(Set.of("select","reject").contains(selector))require(call,fn.args().getLast(),BOOL);
                return selector.equals("each")?NIL:selector.equals("collect")?new Type("vec",List.of(fn.args().getLast())):receiver;
            }
            if(receiver.name().equals("obj")&&selector.equals("each")){if(fn.args().size()!=3)error(call,"M2201","iteração de objeto exige chave e valor");return NIL;}
        }
        if(selector.equals("responds_to?")&&args.size()==1)return BOOL;
        if(selector.equals("stringify")&&args.isEmpty())return STR;
        if(Set.of("slot","maybe_slot").contains(selector)&&args.size()==1&&records.containsKey(receiver.name())) {
            require(call,args.getFirst(),new Type("tag"));var record=records.get(receiver.name());
            if(call.args().getFirst() instanceof Literal key) {
                String name=key.value().toString();var slot=record.fields().stream().filter(f->f.name().equals(name)||f.name().equals("_"+name)).findFirst();
                if(slot.isPresent()){privateAccess(call,call.receiver(),slot.get());Type type=ts.substitute(slot.get().type(),recordBindings(receiver));return selector.equals("slot")?type:new Type("maybe",List.of(type));}
                if(selector.equals("maybe_slot"))return new Type("maybe",List.of(ANY));
                error(call,"M2107","atributo inexistente: "+name);
            }
            if(context==null||context.owner==null||!context.owner.name().equals(receiver.name()))error(call,"M2108","acesso dinâmico direto exige implementação do próprio record");
            return new Type(selector.equals("slot")?"try":"maybe",List.of(ANY));
        }
        if(receiver.name().equals("lambda")&&selector.equals("result")) {
            if(receiver.args().size()!=args.size()+1)error(call,"M2201","aridade inválida de lambda");
            for(int i=0;i<args.size();i++)require(call,args.get(i),receiver.args().get(i));return receiver.args().getLast();
        }
        if(Set.of("even?","odd?").contains(selector)&&args.isEmpty()&&integer(receiver))return BOOL;
        if(Set.of("upcase","downcase").contains(selector)&&args.isEmpty()&&receiver.equals(STR))return STR;
        if(selector.equals("length")&&args.isEmpty()&&Set.of("str","vec","obj").contains(receiver.name()))return INT;
        if(receiver.name().equals("obj")) {
            Type element=receiver.args().getFirst();
            if(Set.of("get","get_element","maybe_get","has_key?").contains(selector)&&args.size()==1) {
                require(call,args.getFirst(),new Type("tag"));return selector.equals("has_key?")?BOOL:new Type(selector.equals("maybe_get")?"maybe":"try",List.of(element));
            }
            if(selector.equals("has_value?")&&args.size()==1)return BOOL;
        }
        if(receiver.name().equals("vec")&&selector.equals("element_at")&&args.size()==1){require(call,args.getFirst(),INT);return new Type("try",List.of(receiver.args().getFirst()));}
        if(receiver.equals(STR)&&selector.equals("parse")&&args.size()==1){
            Type target=args.getFirst();
            if(!target.name().equals("type")||target.args().size()!=1)error(call,"M2105","parse: exige um Type como argumento");
            Type parsed=target.args().getFirst();
            if(!(numeric(parsed)||Set.of("str","bool","char").contains(parsed.name())))error(call,"M2105","parse: não suporta "+parsed);
            return new Type("try",List.of(parsed));
        }
        if(receiver.equals(STR)&&selector.equals("maybe_parse")&&args.size()==1){
            Type target=args.getFirst();
            if(!target.name().equals("type")||target.args().size()!=1)error(call,"M2105","maybe_parse: exige um Type como argumento");
            Type parsed=target.args().getFirst();
            if(!(numeric(parsed)||Set.of("str","bool","char").contains(parsed.name())))error(call,"M2105","maybe_parse: não suporta "+parsed);
            return new Type("maybe",List.of(parsed));
        }
        if(receiver.name().equals("type")&&receiver.args().size()==1&&(selector.equals("new")||selector.equals("create")||selector.startsWith("new:")||selector.startsWith("create:"))) {
            Type factory=receiver.args().getFirst();
            if(model.isAbstractData(factory.name())||model.isAbstractDispatch(factory.name()))error(call,"M2304","tipo abstrato não pode ser instanciado: "+factory);
            var record=records.get(factory.name());
            if(record!=null) {
                Type target=expected;while(target!=null&&Set.of("try","maybe").contains(target.name()))target=target.args().getFirst();
                var bindings=new HashMap<>(recordBindings(factory));
                if(factory.args().isEmpty()&&target!=null&&target.name().equals(record.name()))bindings.putAll(recordBindings(target));
                String initializer="initialize"+(selector.contains(":")?selector.substring(selector.indexOf(":")):"");
                var initializers=methods.stream().filter(m->m.receiver()!=null&&m.receiver().name().equals(record.name())&&m.selector().equals(initializer)&&m.params().size()==args.size()).toList();
                if(!initializers.isEmpty()) {
                    for(var init:initializers) {
                        var current=new HashMap<>(bindings);boolean fits=true;
                        for(int i=0;i<args.size();i++)if(init.params().get(i).type()!=null&&!instantiate(args.get(i),init.params().get(i).type(),current))fits=false;
                        if(fits){Type concrete=recordType(record,current,factory);Type output=init.result()==null?inferredResults.getOrDefault(init,ANY):resolve(init.result());return ts.includesFail(output)?new Type("try",List.of(concrete)):concrete;}
                    }
                    error(call,"M2202","initialize não aplicável a "+args);
                }
                if(record.derives().contains("Init")||selector.equals("create")) {
                    if(args.size()!=record.fields().size())error(call,"M2201","aridade inválida no construtor");
                    for(int i=0;i<args.size();i++) {
                        Type fieldType=record.fields().get(i).type();
                        if(!instantiate(args.get(i),ts.substitute(fieldType,bindings),bindings))require(call,args.get(i),ts.substitute(fieldType,bindings));
                    }
                    return recordType(record,bindings,factory);
                }
            }
        }
        var record=records.get(receiver.name());
        if(record!=null){var bindings=recordBindings(receiver);
            if(record.derives().contains("Vec")&&selector.equals("to_vec")&&args.isEmpty())return new Type("vec",List.of(record.fields().isEmpty()?ANY:ts.union(record.fields().stream().map(f->ts.substitute(f.type(),bindings)).toList())));
            if(record.derives().contains("Ord")&&Set.of("compare","<=>","<",">","<=",">=").contains(selector)){if(args.size()!=1)error(call,"M2201","comparação exige um argumento");require(call,args.getFirst(),receiver);return Set.of("compare","<=>").contains(selector)?INT:BOOL;}
            if(record.derives().contains("Block")&&selector.equals("value")){var field=record.fields().stream().filter(f->resolve(f.type()).name().equals("lambda")).findFirst();if(field.isPresent()){Type fn=ts.substitute(resolve(field.get().type()),bindings);if(fn.args().size()!=args.size()+1)error(call,"M2201","aridade inválida de Block.value");for(int i=0;i<args.size();i++)require(call,args.get(i),fn.args().get(i));return fn.args().getLast();}}
            if(record.derives().contains("Iter")&&selector.equals("each")){Type fn=call.block()!=null?types.get(call.block()):args.size()==1?args.getFirst():null;if(fn==null||!fn.name().equals("lambda")||fn.args().size()!=2)error(call,"M2201","Iter.each exige bloco de um parâmetro");Type value=record.fields().isEmpty()?ANY:ts.union(record.fields().stream().map(f->ts.substitute(f.type(),bindings)).toList());require(call,value,fn.args().getFirst());return ts.includesFail(fn.args().getLast())?new Type("try",List.of(NIL)):NIL;}
        }
        if(record!=null&&record.derives().contains("Prop"))for(var field:record.fields()) {
            String name=field.name().startsWith("_")?field.name().substring(1):field.name();Type fieldType=ts.substitute(field.type(),recordBindings(receiver));
            if(selector.equals(name)) {
                if(args.isEmpty()&&(field.labels().contains("readable")||field.labels().contains("accessible")))return fieldType;
                if(args.size()==1&&(field.labels().contains("configurable")||field.labels().contains("accessible"))){require(call,args.getFirst(),fieldType);return NIL;}
            }
        }
        return null;
    }
    private Type recordType(Ast.Record record,Map<String,Type> bindings,Type fallback) {
        if(record.parameters().isEmpty())return new Type(record.name());
        return new Type(record.name(),record.parameters().stream().map(p->bindings.getOrDefault(p,ANY)).toList());
    }
}
