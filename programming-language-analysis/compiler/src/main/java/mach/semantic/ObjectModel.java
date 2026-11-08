package mach.semantic;

import mach.ast.Ast;
import mach.ast.Ast.*;
import mach.frontend.Diagnostic;
import java.util.*;

/** Declaration-time model of MachOne records, abstract datas and abstract dispatches.
 * Abstract dispatches are AST templates instantiated for a concrete record; they are
 * never Java interfaces and never independently callable runtime objects.
 */
public final class ObjectModel {
    private static final Set<String> BUILTINS = Set.of(
        "any", "nil", "fail", "Fail", "bool", "str", "tag", "char", "regex",
        "byte", "ubyte", "short", "ushort", "int", "uint", "long", "ulong", "float", "double", "Number",
        "type", "vec", "obj", "union", "maybe", "try", "lambda", "task", "inbox", "outbox", "IOBox", "Vector", "Time", "message"
    );
    private static final Set<String> DERIVATIONS = Set.of("Init", "Repr", "Prop", "Ord", "Eq", "Iter", "Block", "Vec", "Proxy");
    /** Library type values may be shadowed by source declarations. */
    private static final Set<String> LIBRARY_TYPES=Set.of("File","Path","Dir","Env","Process","System","Json","Csv","Base64","Hex","Hash","Uuid","Url","Date","DateTime","Console","Reflect","Debug","Log","Test","Bench","Math","Random","Duration","Instant","Number","Fn","Text","Bits","Integer","Signed","Unsigned","Real","Collection","Sequence","Mapping","Set","Dictionary","SortedSet","SortedMap","Deque","Bag","PriorityQueue","Comparable","Enumerable","Iterable");
    private final Ast.Program program;
    private final Map<String,Object> compileConstants;
    private final Map<String, Ast.Record> records = new LinkedHashMap<>();
    private final Map<String, Ast.Record> declaredRecords=new LinkedHashMap<>();
    private final Map<String, Abstract> abstracts = new LinkedHashMap<>();
    private final Map<String, Type> aliases = new LinkedHashMap<>();
    private final Map<String, Node> declarations = new LinkedHashMap<>();
    private final List<Impl> derived;

    public ObjectModel(Ast.Program program) {this(program,Map.of());}
    public ObjectModel(Ast.Program program,Map<String,Object> compileConstants) {this(program,compileConstants,false);}
    private ObjectModel(Ast.Program program,Map<String,Object> compileConstants,boolean contextsOnly) {
        Objects.requireNonNull(program);
        this.compileConstants=Collections.unmodifiableMap(new LinkedHashMap<>(compileConstants));
        for (Stmt stmt : program.body()) {
            if (stmt instanceof Ast.Record r) {declare(r.name(), r);records.put(r.name(), r);declaredRecords.put(r.name(),r);}
            else if (stmt instanceof Abstract a) {declare(a.name(), a);abstracts.put(a.name(), a);}
            else if (stmt instanceof Alias a) {declare(a.name(), a);aliases.put(a.name(), a.type());}
        }
        collectBindingTypeAliases(program.body());
        for(Ast.Record record:declaredRecords.values())flattenRecord(record,new LinkedHashSet<>());
        this.program=new Ast.Program(program.body().stream().map(s->s instanceof Ast.Record r?records.get(r.name()):s).toList());
        if(contextsOnly){derived=List.of();return;}
        for (var entry : aliases.entrySet()) resolve(entry.getValue(), declarations.get(entry.getKey()), new LinkedHashSet<>(List.of(entry.getKey())));
        validateDeclarations();
        for (Ast.Record record : records.values()) detectHierarchyCycle(record, new LinkedHashSet<>());
        derived = List.copyOf(expandDefaults());
    }

    /** Flattened metadata is available before directives execute; source record ASTs remain intact. */
    public static Map<String,Ast.Record> compositionContexts(Ast.Program input) {return new ObjectModel(input,Map.of(),true).records();}
    public Ast.Program program() {return new Ast.Program(program.body().stream().map(s->copyStatement(s,Map.of())).toList());}
    private Ast.Record flattenRecord(Ast.Record record,Set<String> path) {
        if(record.uses().isEmpty())return record;
        if(!path.add(record.name()))error(record,"M2317","composição estrutural cíclica: "+String.join(" -> ",path)+" -> "+record.name());
        var aliasesSeen=new HashSet<String>();
        for(Use use:record.uses())if(!aliasesSeen.add(use.alias()))error(use,"M2318","alias de composição duplicado: "+use.alias());
        var fields=new ArrayList<Field>();
        for(Field field:record.fields()) {
            if(!field.labels().contains("<composition>")){fields.add(field);continue;}
            Use use=record.uses().stream().filter(u->u.alias().equals(field.name())).findFirst().orElseThrow();
            Type target=resolve(use.type(),use,new LinkedHashSet<>());Ast.Record source=declaredRecords.get(target.name());
            if(source==null)error(use,"M2319","use exige um record concreto: "+target);
            if(!target.args().isEmpty()&&target.args().size()!=source.parameters().size())error(use,"M2312","aridade de tipo inválida para "+target.name());
            source=flattenRecord(source,path);var substitutions=new HashMap<String,Type>();
            for(int i=0;i<source.parameters().size();i++)substitutions.put(source.parameters().get(i),target.args().isEmpty()?new Type(source.parameters().get(i)):target.args().get(i));
            for(Field component:source.fields())fields.add(new Field(component.name(),copyType(component.type(),substitutions),component.labels()));
        }
        path.remove(record.name());
        var flattened=new Ast.Record(record.name(),record.parameters(),record.parent(),record.derives(),List.copyOf(fields),record.uses(),record.span());
        records.put(record.name(),flattened);return flattened;
    }

    public Map<String, Ast.Record> records() {return Collections.unmodifiableMap(records);}
    public Map<String, Abstract> abstracts() {return Collections.unmodifiableMap(abstracts);}
    public Map<String, Type> aliases() {return Collections.unmodifiableMap(aliases);}
    /** Append these implementations after the explicit body during symbol collection.
     * Do not also use the old per-record copy of Abstract.body: this method owns expansion.
     */
    public List<Impl> derivedImplementations() {return derived;}
    public boolean isAbstractData(String name) {return abstracts.containsKey(name) && abstracts.get(name).hierarchy();}
    public boolean isAbstractDispatch(String name) {return abstracts.containsKey(name) && !abstracts.get(name).hierarchy();}
    public boolean isSubtype(Type actual, Type expected) {return new TypeSystem(aliases, records).assignable(actual, expected);}

    private void collectBindingTypeAliases(List<Stmt> body) {
        var valueBindings=new HashSet<String>();
        for(Stmt statement:body)if(statement instanceof Bind binding) {
            Type target=null;
            if(!binding.kind().equals("var")&&(binding.type()==null||binding.type().name().equals("type"))) {
                if(binding.value() instanceof TypeExpr value)target=value.type();
                else if(binding.value() instanceof Name value) {
                    if(aliases.containsKey(value.name()))target=aliases.get(value.name());
                    else if(!valueBindings.contains(value.name())&&(BUILTINS.contains(value.name())||LIBRARY_TYPES.contains(value.name())||records.containsKey(value.name())||isAbstractData(value.name())))target=new Type(value.name());
                }
            }
            valueBindings.add(binding.name());
            if(target!=null&&!declarations.containsKey(binding.name())) {aliases.put(binding.name(),target);declarations.put(binding.name(),binding);}
        }
    }
    private void declare(String name, Node node) {
        if (BUILTINS.contains(name) || declarations.putIfAbsent(name, node) != null) error(node,"M2301","nome de tipo duplicado: "+name);
    }
    private static void error(Node origin, String code, String message) {throw new Diagnostic(code, origin.span(), message);}
    private Type resolve(Type type, Node origin, Set<String> path) {
        if (aliases.containsKey(type.name())) {
            if (!path.add(type.name())) error(origin,"M2302","alias cíclico: "+String.join(" -> ",path)+" -> "+type.name());
            Type result = resolve(aliases.get(type.name()), origin, path);path.remove(type.name());return result;
        }
        return new Type(type.name(),type.args().stream().map(t->resolve(t,origin,new LinkedHashSet<>(path))).toList());
    }
    private void validateDeclarations() {
        for (Stmt stmt : program.body()) {
            if (stmt instanceof Ast.Record r) validateRecord(r);
            else if (stmt instanceof Abstract a) {
                if (a.hierarchy() && !a.body().isEmpty()) error(a,"M2303","abstract data define uma hierarquia; use impl T & "+a.name()+" para comportamento");
                if (!a.hierarchy()) validateTemplateShape(a.body());
            } else if (stmt instanceof Alias a) validateType(a.type(),a,Set.of());
            else if (stmt instanceof Impl impl) {
                validateType(impl.receiver(),impl,Set.of());
                Type receiver=resolve(impl.receiver(),impl,new LinkedHashSet<>());
                if (isAbstractDispatch(receiver.name())) error(impl,"M2304","abstract dispatch não é receptor concreto: "+receiver.name());
                Set<String> variables=records.containsKey(receiver.name())?Set.copyOf(records.get(receiver.name()).parameters()):Set.of();
                validateMethodBody(impl.body(),impl,variables);
            } else if (stmt instanceof BoundedImpl impl) {
                validateType(impl.bound(),impl,Set.of());
                if (isAbstractDispatch(resolve(impl.bound(),impl,new LinkedHashSet<>()).name())) error(impl,"M2304","abstract dispatch não é limite de subtipagem: "+impl.bound());
                validateTemplateShape(impl.body());
            } else if (stmt instanceof Method m) validateMethod(m,Set.of());
        }
    }
    private void validateRecord(Ast.Record record) {
        Set<String> parameters=new HashSet<>();
        for (String parameter:record.parameters()) if(!parameters.add(parameter)) error(record,"M2305","parâmetro de tipo duplicado: "+parameter);
        var fields=new HashSet<String>();
        for (Field field:record.fields()) {
            String name=field.name().startsWith("_")?field.name().substring(1):field.name();
            if(!fields.add(name)) error(record,"M2306","atributos colidem no mesmo slot: "+name);
            validateType(field.type(),record,parameters);
        }
        if(record.parent()!=null) {
            validateType(record.parent(),record,parameters);
            Type parent=resolve(record.parent(),record,new LinkedHashSet<>());
            if(isAbstractDispatch(parent.name())) error(record,"M2307","use "+parent.name()+" nas derivações; abstract dispatch não define subtipagem");
        }
        var derives=new HashSet<String>();
        for(String derive:record.derives()) {
            if(!derives.add(derive)) error(record,"M2308","derivação duplicada: "+derive);
            if(!DERIVATIONS.contains(derive)&&!isAbstractDispatch(derive)) {
                if(isAbstractData(derive)) error(record,"M2309","abstract data exige & "+derive+", não uma derivação de métodos");
                error(record,"M2309","abstract dispatch ou derivação desconhecida: "+derive);
            }
        }
        if(record.derives().contains("Proxy")) {
            long targets=record.fields().stream().filter(f->f.labels().contains("proxied")).count();
            boolean customTarget=program.body().stream().filter(s->s instanceof Impl).map(s->(Impl)s)
                .filter(i->resolve(i.receiver(),i,new LinkedHashSet<>()).name().equals(record.name()))
                .flatMap(i->i.body().stream()).anyMatch(s->s instanceof Method m&&m.selector().equals("target")&&m.params().isEmpty());
            if(targets>1||targets==0&&!customTarget)error(record,"M2315","Proxy exige um único atributo proxied ou um método target explícito");
            for(Field field:record.fields())if(field.labels().contains("proxied"))detectProxyCycle(record,field.type(),new LinkedHashSet<>());
        }
        if(record.derives().contains("Block")&&record.fields().stream().filter(f->resolve(f.type(),record,new LinkedHashSet<>()).name().equals("lambda")).count()!=1)
            error(record,"M2323","Block exige um único atributo lambda");
    }
    private void detectProxyCycle(Ast.Record origin,Type target,Set<String> path) {
        Type concrete=resolve(target,origin,new LinkedHashSet<>());Ast.Record record=records.get(concrete.name());
        if(record==null||!record.derives().contains("Proxy"))return;
        if(!path.add(record.name()))error(origin,"M2316","ciclo de tipos Proxy: "+String.join(" -> ",path)+" -> "+record.name());
        for(Field field:record.fields())if(field.labels().contains("proxied"))detectProxyCycle(origin,field.type(),path);
        path.remove(record.name());
    }
    private void validateTemplateShape(List<Stmt> body) {
        for(Stmt statement:body)if(!(statement instanceof Method||statement instanceof Sign||statement instanceof CompileFor||statement instanceof CompileIf||statement instanceof TemplateMethod))error(statement,"M2310","abstract dispatch exige métodos, assinaturas ou diretivas de expansão");
    }
    private void validateMethodBody(List<Stmt> body,Node origin,Set<String> variables) {
        for(Stmt stmt:body) {
            if(stmt instanceof Method method)validateMethod(method,variables);
            else if(stmt instanceof CompileFor||stmt instanceof CompileIf||stmt instanceof TemplateMethod) { /* validated after record-specific expansion */ }
            else if(stmt instanceof Sign sign) {for(Type t:sign.inputs())validateType(t,sign,variables);if(sign.output()!=null)validateType(sign.output(),sign,variables);}
            else error(stmt,"M2310","corpo de implementação/abstract exige métodos ou sign");
        }
    }
    private void validateMethod(Method method,Set<String> variables) {
        for(Param parameter:method.params())if(parameter.type()!=null)validateType(parameter.type(),method,variables);
        if(method.result()!=null)validateType(method.result(),method,variables);
    }
    private void validateType(Type type,Node origin,Set<String> variables) {
        boolean variable=variables.contains(type.name()) || type.name().matches("[A-Z]")&&!declarations.containsKey(type.name());
        if(!BUILTINS.contains(type.name())&&!LIBRARY_TYPES.contains(type.name())&&!declarations.containsKey(type.name())&&!variable) error(origin,"M2311","tipo desconhecido: "+type.name());
        if(isAbstractDispatch(type.name())) error(origin,"M2311","abstract dispatch não é um tipo de valor: "+type.name());
        if(variable&&!type.args().isEmpty())error(origin,"M2312","parâmetro de tipo não admite argumentos: "+type.name());
        Ast.Record record=records.get(type.name());
        if(record!=null&&!type.args().isEmpty()&&record.parameters().size()!=type.args().size())error(origin,"M2312","aridade de tipo inválida para "+type.name());
        if(aliases.containsKey(type.name())&&!type.args().isEmpty())error(origin,"M2312","alias não parametrizado: "+type.name());
        if(Set.of("maybe","try","vec","obj","type","inbox","outbox","IOBox").contains(type.name())&&type.args().size()>1)error(origin,"M2312",type.name()+" admite um argumento de tipo");
        for(Type argument:type.args())validateType(argument,origin,variables);
    }
    private void detectHierarchyCycle(Ast.Record record,Set<String> path) {
        if(!path.add(record.name()))error(record,"M2313","ciclo na hierarquia de tipos: "+String.join(" -> ",path)+" -> "+record.name());
        if(record.parent()!=null) {
            Type parent=resolve(record.parent(),record,new LinkedHashSet<>());
            Ast.Record next=records.get(parent.name());if(next!=null)detectHierarchyCycle(next,path);
        }
        path.remove(record.name());
    }
    private record Signature(List<String> parts,List<Type> parameters,List<Boolean> variadic) {}
    private Signature signature(Method method) {return new Signature(method.parts(),method.params().stream().map(p->p.type()==null?new Type("any"):resolve(p.type(),method,new LinkedHashSet<>())).toList(),method.params().stream().map(Param::variadic).toList());}
    private List<Method> methods(List<Stmt> body,Type receiver,Map<String,Type> substitutions) {
        var result=new ArrayList<Method>();Sign pending=null;
        for(Stmt stmt:body) {
            if(stmt instanceof Sign sign){pending=sign;continue;}
            if(!(stmt instanceof Method method))continue;
            List<Param> parameters=method.params();Type output=method.result();
            if(pending!=null) {
                if(parameters.stream().anyMatch(p->p.type()!=null)||output!=null)error(method,"M2005","sign e tipos inline não podem ser combinados");
                if(parameters.size()!=pending.inputs().size())error(method,"M2006","aridade incompatível com sign");
                var signed=new ArrayList<Param>();for(int i=0;i<parameters.size();i++)signed.add(new Param(parameters.get(i).name(),pending.inputs().get(i),parameters.get(i).variadic(),parameters.get(i).converting()));parameters=signed;output=pending.output();pending=null;
            }
            result.add(new Method(receiver,method.parts(),parameters.stream().map(p->new Param(p.name(),copyType(p.type(),substitutions),p.variadic(),p.converting())).toList(),copyType(output,substitutions),copy(method.guard(),substitutions),copyBody(method.body(),substitutions),method.span()));
        }
        if(pending!=null)error(pending,"M2006","sign sem método seguinte no abstract dispatch");
        return result;
    }
    private record BoundedCandidate(Type bound,Method method) {}
    private List<Impl> expandDefaults() {
        var implementations=new ArrayList<Impl>();
        for(Ast.Record record:records.values()) {
            Type receiver=new Type(record.name());
            Set<Signature> explicit=new HashSet<>();
            for(Stmt stmt:program.body())if(stmt instanceof Impl impl&&resolve(impl.receiver(),impl,new LinkedHashSet<>()).name().equals(record.name())) {
                for(Method method:methods(impl.body(),receiver,Map.of()))if(method.guard()==null)explicit.add(signature(method));
            }
            var defaults=new ArrayList<Method>();
            for(String derive:record.derives())if(isAbstractDispatch(derive)) {
                List<Stmt> expanded=new CompileTimeExpander().expand(abstracts.get(derive).body(),record,compileConstants);
                validateMethodBody(expanded,record,Set.copyOf(record.parameters()));
                for(Method method:methods(expanded,receiver,Map.of()))if(!explicit.contains(signature(method)))defaults.add(method);
            }
            Set<Signature> concrete=new HashSet<>(explicit);
            for(Method method:defaults)if(method.guard()==null)concrete.add(signature(method));
            var bounded=new ArrayList<BoundedCandidate>();
            for(Stmt stmt:program.body())if(stmt instanceof BoundedImpl impl && isSubtype(receiver,impl.bound())) {
                Map<String,Type> substitutions=Map.of(impl.variable(),receiver);
                List<Stmt> expanded=new CompileTimeExpander().expand(impl.body(),record,compileConstants);
                validateMethodBody(expanded,impl,Set.of(impl.variable()));
                for(Method method:methods(expanded,receiver,substitutions))if(!concrete.contains(signature(method)))bounded.add(new BoundedCandidate(impl.bound(),method));
            }
            for(BoundedCandidate candidate:bounded) {
                boolean overridden=bounded.stream().anyMatch(other->signature(other.method()).equals(signature(candidate.method()))&&isSubtype(other.bound(),candidate.bound())&&!isSubtype(candidate.bound(),other.bound()));
                if(!overridden)defaults.add(candidate.method());
            }
            if(!defaults.isEmpty())implementations.add(new Impl(receiver,List.copyOf(defaults),record.span()));
        }
        for(String nativeName:List.of("byte","ubyte","short","ushort","int","uint","long","ulong","float","double","str","char","bool","tag","regex","fail","type")) {
            if(records.containsKey(nativeName))continue;
            Type receiver=new Type(nativeName);var explicit=new HashSet<Signature>();
            for(Stmt statement:program.body())if(statement instanceof Impl impl&&resolve(impl.receiver(),impl,new LinkedHashSet<>()).equals(receiver))
                for(Method method:methods(impl.body(),receiver,Map.of()))if(method.guard()==null)explicit.add(signature(method));
            var candidates=new ArrayList<BoundedCandidate>();
            for(Stmt statement:program.body())if(statement instanceof BoundedImpl impl&&isSubtype(receiver,impl.bound())) {
                var nativeContext=new Ast.Record(nativeName,List.of(),null,List.of(),List.of(),impl.span());
                List<Stmt> expanded=new CompileTimeExpander().expand(impl.body(),nativeContext,compileConstants);
                validateMethodBody(expanded,impl,Set.of(impl.variable()));
                for(Method method:methods(expanded,receiver,Map.of(impl.variable(),receiver)))if(!explicit.contains(signature(method)))candidates.add(new BoundedCandidate(impl.bound(),method));
            }
            var selected=new ArrayList<Method>();
            for(BoundedCandidate candidate:candidates) {
                boolean overridden=candidates.stream().anyMatch(other->signature(other.method()).equals(signature(candidate.method()))&&isSubtype(other.bound(),candidate.bound())&&!isSubtype(candidate.bound(),other.bound()));
                if(!overridden)selected.add(candidate.method());
            }
            if(!selected.isEmpty())implementations.add(new Impl(receiver,List.copyOf(selected),selected.getFirst().span()));
        }
        var defaultsByRecord=new LinkedHashMap<String,List<Method>>();
        for(Impl impl:implementations)defaultsByRecord.put(impl.receiver().name(),impl.body().stream().map(s->(Method)s).toList());
        var composedCache=new HashMap<String,List<Method>>();
        for(Ast.Record record:records.values()) {
            List<Method> composed=composedMethods(record,defaultsByRecord,composedCache);
            if(!composed.isEmpty())implementations.add(new Impl(new Type(record.name()),List.copyOf(composed),record.span()));
        }
        return implementations;
    }
    private List<Method> composedMethods(Ast.Record record,Map<String,List<Method>> defaults,Map<String,List<Method>> cache) {
        if(cache.containsKey(record.name()))return cache.get(record.name());
        var result=new ArrayList<Method>();var explicit=new HashSet<Signature>();Type receiver=new Type(record.name());
        for(Stmt statement:program.body())if(statement instanceof Impl impl&&resolve(impl.receiver(),impl,new LinkedHashSet<>()).name().equals(record.name()))
            for(Method method:methods(impl.body(),receiver,Map.of()))if(method.guard()==null)explicit.add(signature(method));
        for(Method method:defaults.getOrDefault(record.name(),List.of()))if(method.guard()==null)explicit.add(signature(method));
        for(Use use:record.uses()) {
            Type sourceType=resolve(use.type(),use,new LinkedHashSet<>());Ast.Record source=records.get(sourceType.name());
            var substitutions=new HashMap<String,Type>();
            for(int i=0;i<source.parameters().size();i++)substitutions.put(source.parameters().get(i),sourceType.args().isEmpty()?new Type(source.parameters().get(i)):sourceType.args().get(i));
            var imports=new ArrayList<Method>();
            for(Stmt statement:program.body())if(statement instanceof Impl impl&&resolve(impl.receiver(),impl,new LinkedHashSet<>()).name().equals(source.name()))
                imports.addAll(methods(impl.body(),receiver,substitutions));
            imports.addAll(methods(new ArrayList<>(defaults.getOrDefault(source.name(),List.of())),receiver,substitutions));
            imports.addAll(methods(new ArrayList<>(composedMethods(source,defaults,cache)),receiver,substitutions));
            if(source.derives().contains("Prop"))for(Field field:source.fields()) {
                String name=field.name().startsWith("_")?field.name().substring(1):field.name();
                Type type=copyType(field.type(),substitutions);Span span=use.span();
                if(field.labels().contains("readable")||field.labels().contains("accessible")) {
                    Method getter=new Method(receiver,List.of(name),List.of(),type,null,List.of(new Eval(new Get(new Name("self",span),new Literal(name,new Type("tag"),span),span),span)),span);
                    if(imports.stream().noneMatch(m->m.guard()==null&&signature(m).equals(signature(getter))))imports.add(getter);
                }
                if(field.labels().contains("configurable")||field.labels().contains("accessible")) {
                    Method setter=new Method(receiver,List.of(name),List.of(new Param("value",type)),new Type("nil"),null,List.of(new Assign("self","=",new Binary("|",new Name("self",span),new ObjectLiteral(List.of(name),List.of(new Name("value",span)),span),span),span)),span);
                    if(imports.stream().noneMatch(m->m.guard()==null&&signature(m).equals(signature(setter))))imports.add(setter);
                }
            }
            for(Method method:imports)if(!method.parts().getFirst().equals("initialize")&&!explicit.contains(signature(method)))result.add(method);
        }
        cache.put(record.name(),List.copyOf(result));return cache.get(record.name());
    }
    private Type copyType(Type type,Map<String,Type> substitutions) {
        if(type==null)return null;
        if(type.args().isEmpty()&&substitutions.containsKey(type.name()))return substitutions.get(type.name());
        return new Type(type.name(),type.args().stream().map(t->copyType(t,substitutions)).toList());
    }
    private List<Stmt> copyBody(List<Stmt> body,Map<String,Type> substitutions) {return body.stream().map(s->copyStatement(s,substitutions)).toList();}
    private Stmt copyStatement(Stmt statement,Map<String,Type> substitutions) {
        if(statement instanceof Ast.Record||statement instanceof Alias||statement instanceof Abstract)return statement;
        if(statement instanceof Method s)return new Method(copyType(s.receiver(),substitutions),s.parts(),s.params().stream().map(p->new Param(p.name(),copyType(p.type(),substitutions),p.variadic(),p.converting())).toList(),copyType(s.result(),substitutions),copy(s.guard(),substitutions),copyBody(s.body(),substitutions),s.span());
        if(statement instanceof Impl s)return new Impl(copyType(s.receiver(),substitutions),copyBody(s.body(),substitutions),s.span());
        if(statement instanceof BoundedImpl s)return new BoundedImpl(s.variable(),copyType(s.bound(),substitutions),copyBody(s.body(),substitutions),s.span());
        if(statement instanceof CompileFor||statement instanceof CompileIf||statement instanceof TemplateMethod)return statement;
        if(statement instanceof Bind s)return new Bind(s.kind(),s.name(),copyType(s.type(),substitutions),s.converting(),copy(s.value(),substitutions),s.span());
        if(statement instanceof Assign s)return new Assign(s.name(),s.op(),copy(s.value(),substitutions),s.span());
        if(statement instanceof Eval s)return new Eval(copy(s.value(),substitutions),s.span());
        if(statement instanceof Return s)return new Return(copy(s.value(),substitutions),s.span());
        if(statement instanceof Branch s)return new Branch(s.conditions().stream().map(e->copy(e,substitutions)).toList(),s.arms().stream().map(b->copyBody(b,substitutions)).toList(),copyBody(s.otherwise(),substitutions),s.span());
        if(statement instanceof BeingLoop s)return new BeingLoop(s.name(),copy(s.initial(),substitutions),copy(s.condition(),substitutions),copyBody(s.body(),substitutions),s.span());
        if(statement instanceof While s)return new While(copy(s.condition(),substitutions),copyBody(s.body(),substitutions),s.span());
        if(statement instanceof For s)return new For(s.names(),copy(s.values(),substitutions),copyBody(s.body(),substitutions),s.span());
        if(statement instanceof Jump s)return new Jump(s.isBreak(),s.span());
        if(statement instanceof Sign s)return new Sign(s.inputs().stream().map(t->copyType(t,substitutions)).toList(),copyType(s.output(),substitutions),s.span());
        error(statement,"M2314","declaração não permitida em método de abstract dispatch");return null;
    }
    private Expr copy(Expr expression,Map<String,Type> substitutions) {
        if(expression==null)return null;
        if(expression instanceof Literal e)return new Literal(e.value(),copyType(e.type(),substitutions),e.span());
        if(expression instanceof Name e)return new Name(e.name(),e.span());
        if(expression instanceof Unary e)return new Unary(e.op(),copy(e.value(),substitutions),e.span());
        if(expression instanceof Binary e)return new Binary(e.op(),copy(e.left(),substitutions),copy(e.right(),substitutions),e.span());
        if(expression instanceof Call e) {
            List<Expr> arguments=e.args();
            if(e.selector().equals("new")||e.selector().startsWith("new:")||e.selector().equals("create")||e.selector().startsWith("create:")) {
                Type target=e.receiver() instanceof TypeExpr t?t.type():e.receiver() instanceof Name n?new Type(n.name()):null;
                if(target!=null) {
                    target=resolve(copyType(target,substitutions),e,new LinkedHashSet<>());Ast.Record record=records.get(target.name());
                    if(record!=null&&arguments.stream().anyMatch(a->a instanceof ComposedArg))arguments=expandConstructorGroups(record,arguments,false).values();
                }
            }
            return new Call(copy(e.receiver(),substitutions),e.parts(),arguments.stream().map(a->copy(a,substitutions)).toList(),copy(e.block(),substitutions),e.span());
        }
        if(expression instanceof ComposedArg e){error(e,"M2320","(alias) has só é permitido no construtor do record que declara use ... as "+e.alias());return null;}
        if(expression instanceof ObjectAccess e)return new ObjectAccess(copy(e.selector(),substitutions),copy(e.target(),substitutions),e.span());
        if(expression instanceof VectorAccess e)return new VectorAccess(copy(e.index(),substitutions),copy(e.target(),substitutions),e.span());
        if(expression instanceof Get e)return new Get(copy(e.receiver(),substitutions),copy(e.key(),substitutions),e.span());
        if(expression instanceof ObjectLiteral e)return new ObjectLiteral(e.keys(),e.values().stream().map(v->copy(v,substitutions)).toList(),e.span());
        if(expression instanceof Convert e)return new Convert(copy(e.value(),substitutions),copyType(e.target(),substitutions),e.mode(),e.span());
        if(expression instanceof Rescue e)return new Rescue(copy(e.value(),substitutions),copy(e.handler(),substitutions),e.span());
        if(expression instanceof BlockExpr e)return new BlockExpr(copyBody(e.body(),substitutions),e.span());
        if(expression instanceof ChainedDo e)return new ChainedDo(copyBody(e.yes(),substitutions),copyBody(e.no(),substitutions),e.span());
        if(expression instanceof TypeExpr e)return new TypeExpr(copyType(e.type(),substitutions),e.span());
        if(expression instanceof Match e)return new Match(copy(e.value(),substitutions),e.arms().stream().map(a->new MatchArm(a.patterns().stream().map(p->copy(p,substitutions)).toList(),copy(a.guard(),substitutions),copy(a.body(),substitutions),a.span())).toList(),copy(e.otherwise(),substitutions),e.span());
        if(expression instanceof TypeTest e)return new TypeTest(e.op(),copy(e.value(),substitutions),copyType(e.target(),substitutions),e.span());
        if(expression instanceof Choice e)return new Choice(copy(e.condition(),substitutions),copy(e.yes(),substitutions),copy(e.no(),substitutions),e.span());
        if(expression instanceof Range e)return new Range(copy(e.start(),substitutions),copy(e.end(),substitutions),copy(e.step(),substitutions),e.inclusive(),e.span());
        if(expression instanceof Lambda e)return new Lambda(e.params().stream().map(p->new Param(p.name(),copyType(p.type(),substitutions),p.variadic(),p.converting())).toList(),copyType(e.result(),substitutions),copyBody(e.body(),substitutions),e.nonLocal(),e.span());
        if(expression instanceof Ast.CompactVector e)return new Ast.CompactVector(e.items().stream().map(v->copy(v,substitutions)).toList(),e.span());
        if(expression instanceof Ast.Vector e)return new Ast.Vector(e.items().stream().map(v->copy(v,substitutions)).toList(),e.span());
        if(expression instanceof Comprehension e)return new Comprehension(e.names(),copy(e.values(),substitutions),copy(e.key(),substitutions),copy(e.value(),substitutions),copy(e.filter(),substitutions),e.span());
        throw new IllegalStateException("Expressão AST sem cópia: "+expression.getClass().getName());
    }
    private record ConstructorGroup(List<Expr> values,List<Expr> remainder) {}
    private ConstructorGroup expandConstructorGroups(Ast.Record record,List<Expr> arguments,boolean bounded) {
        var values=new ArrayList<Expr>();var remaining=new ArrayDeque<>(arguments);var seen=new HashSet<String>();
        while(!remaining.isEmpty()&&(!bounded||values.size()<record.fields().size())) {
            Expr value=remaining.removeFirst();
            if(!(value instanceof ComposedArg group)){values.add(value);continue;}
            Use use=record.uses().stream().filter(u->u.alias().equals(group.alias())).findFirst().orElse(null);
            if(use==null)error(group,"M2320","alias de composição não pertence a "+record.name()+": "+group.alias());
            if(!seen.add(group.alias()))error(group,"M2321","grupo de composição repetido: "+group.alias());
            int offset=0;
            for(Field field:declaredRecords.get(record.name()).fields()) {
                if(field.labels().contains("<composition>")) {
                    if(field.name().equals(group.alias()))break;
                    Type prior=resolve(field.type(),group,new LinkedHashSet<>());offset+=records.get(prior.name()).fields().size();
                }else offset++;
            }
            if(values.size()!=offset)error(group,"M2321","grupo "+group.alias()+" fora da posição declarada em "+record.name());
            Type target=resolve(use.type(),use,new LinkedHashSet<>());Ast.Record source=records.get(target.name());
            ConstructorGroup expanded=expandConstructorGroups(source,group.args(),true);
            if(expanded.values().size()!=source.fields().size())error(group,"M2322","grupo "+group.alias()+" exige "+source.fields().size()+" argumentos");
            values.addAll(expanded.values());
            for(int i=expanded.remainder().size()-1;i>=0;i--)remaining.addFirst(expanded.remainder().get(i));
        }
        return new ConstructorGroup(List.copyOf(values),List.copyOf(remaining));
    }

}
