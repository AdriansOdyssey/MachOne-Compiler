package mach.semantic;

import mach.ast.Ast;
import mach.ast.Ast.*;
import mach.frontend.Diagnostic;
import java.util.*;

/** Pure AST expansion. Generated declarations go through the ordinary semantic pipeline. */
public final class CompileTimeExpander {
    private static final Type STR = new Type("str"), TAG = new Type("tag");
    private static final int MAX_STEPS = 100_000, MAX_DEPTH = 128;
    private final Map<String, Ast.Record> records = new LinkedHashMap<>();
    private final Map<String, Type> aliases = new LinkedHashMap<>();
    private Map<String,Object> constants = Map.of();
    private int steps;
    private record TagValue(String name) {}

    public Map<String,Object> constants() { return constants; }

    public Ast.Program expand(Ast.Program program) {return expand(program,Map.of());}
    public Ast.Program expand(Ast.Program program,Map<String,Ast.Record> contexts) {
        records.clear(); aliases.clear(); steps=0;
        for (Stmt statement:program.body()) {
            if (statement instanceof Ast.Record r) records.put(r.name(),r);
            else if (statement instanceof Alias a) aliases.put(a.name(),a.type());
        }
        records.putAll(contexts);
        var env=new LinkedHashMap<String,Object>();
        var result=expandBody(program.body(),null,env,0);
        constants=Collections.unmodifiableMap(new LinkedHashMap<>(env));
        return new Ast.Program(result);
    }

    public List<Stmt> expand(List<Stmt> body, Ast.Record record, Map<String,Object> environment) {
        return expandBody(body,record,new LinkedHashMap<>(environment),0);
    }

    private void step(Node node,int depth) {
        if (++steps>MAX_STEPS || depth>MAX_DEPTH) error(node,"M3001","limite de expansão em tempo de compilação excedido");
    }
    private static void error(Node node,String code,String message) { throw new Diagnostic(code,node.span(),message); }
    private List<Stmt> expandBody(List<Stmt> body,Ast.Record record,Map<String,Object> env,int depth) {
        var result=new ArrayList<Stmt>();
        for (Stmt statement:body) {
            step(statement,depth);
            if (statement instanceof CompileFor loop) {
                Object values=evaluate(loop.values(),record,env);
                if (!(values instanceof List<?>)) error(loop,"M3002","@for exige uma coleção conhecida em tempo de compilação");
                for(Object value:(List<?>)values) {
                    var local=new LinkedHashMap<>(env); local.put(loop.variable(),value);
                    result.addAll(expandBody(loop.body(),record,local,depth+1));
                }
            } else if (statement instanceof CompileIf conditional) {
                boolean selected=false;
                for(int i=0;i<conditional.conditions().size();i++) {
                    if (booleanValue(evaluate(conditional.conditions().get(i),record,env),conditional)) {
                        result.addAll(expandBody(conditional.arms().get(i),record,new LinkedHashMap<>(env),depth+1)); selected=true;break;
                    }
                }
                if(!selected)result.addAll(expandBody(conditional.otherwise(),record,new LinkedHashMap<>(env),depth+1));
            } else if(statement instanceof TemplateMethod template) {
                var parts=template.parts().stream().map(p->identifier(evaluate(p,record,env),p)).toList();
                result.add(new Method(null,parts,template.params(),template.result(),rewrite(template.guard(),record,env,depth),expandBody(template.body(),record,new LinkedHashMap<>(env),depth+1),template.span()));
            } else if(statement instanceof Method method) {
                result.add(new Method(method.receiver(),method.parts(),method.params(),method.result(),rewrite(method.guard(),record,env,depth),expandBody(method.body(),record,new LinkedHashMap<>(env),depth+1),method.span()));
            } else if(statement instanceof Impl impl) {
                Ast.Record context=recordOf(impl.receiver());
                // Standalone expander calls can be given their context directly by ObjectModel.
                if(context==null)context=record;
                result.add(new Impl(impl.receiver(),expandBody(impl.body(),context,new LinkedHashMap<>(env),depth+1),impl.span()));
            } else if(statement instanceof Abstract || statement instanceof BoundedImpl) {
                // Templates require a concrete record and remain untouched until instantiation.
                result.add(statement);
            } else if(statement instanceof Bind binding) {
                Expr value=rewrite(binding.value(),record,env,depth);
                result.add(new Bind(binding.kind(),binding.name(),binding.type(),binding.converting(),value,binding.span()));
                boolean known=false;Object constant=null;
                if(!binding.kind().equals("var")) {
                    try {constant=evaluate(value,record,env);known=true;}
                    catch(Diagnostic notConstant) { /* Runtime bindings remain runtime bindings. */ }
                }
                env.remove(binding.name());if(known)env.put(binding.name(),constant);
            } else if(statement instanceof Assign assign) {
                env.remove(assign.name());
                result.add(new Assign(assign.name(),assign.op(),rewrite(assign.value(),record,env,depth),assign.span()));
            } else if(statement instanceof Eval eval) result.add(new Eval(rewrite(eval.value(),record,env,depth),eval.span()));
            else if(statement instanceof Return ret) result.add(new Return(rewrite(ret.value(),record,env,depth),ret.span()));
            else if(statement instanceof Branch branch) result.add(new Branch(branch.conditions().stream().map(e->rewrite(e,record,env,depth)).toList(),branch.arms().stream().map(b->expandBody(b,record,new LinkedHashMap<>(env),depth+1)).toList(),expandBody(branch.otherwise(),record,new LinkedHashMap<>(env),depth+1),branch.span()));
            else if(statement instanceof BeingLoop loop) result.add(new BeingLoop(loop.name(),rewrite(loop.initial(),record,env,depth),rewrite(loop.condition(),record,env,depth),expandBody(loop.body(),record,new LinkedHashMap<>(env),depth+1),loop.span()));
            else if(statement instanceof While loop) result.add(new While(rewrite(loop.condition(),record,env,depth),expandBody(loop.body(),record,new LinkedHashMap<>(env),depth+1),loop.span()));
            else if(statement instanceof For loop) result.add(new For(loop.names(),rewrite(loop.values(),record,env,depth),expandBody(loop.body(),record,new LinkedHashMap<>(env),depth+1),loop.span()));
            else result.add(statement);
        }
        return List.copyOf(result);
    }

    private Ast.Record recordOf(Type type) {
        if(type.name().equals("type")&&type.args().size()==1)return records.get(type.args().getFirst().name());
        return records.get(type.name());
    }

    private Expr rewrite(Expr expr,Ast.Record record,Map<String,Object> env,int depth) {
        if(expr==null)return null;
        step(expr,depth);
        if(expr instanceof Splice splice)return literal(evaluate(splice.value(),record,env),splice.span());
        if(expr instanceof TemplateName name)return new Name(identifier(evaluate(name,record,env),name),name.span());
        if(expr instanceof DynamicTag tag)return new Literal(tagName(evaluate(tag.value(),record,env),tag),TAG,tag.span());
        if(expr instanceof DynamicObject object) {
            var keys=object.keys().stream().map(k->tagName(evaluate(k,record,env),k)).toList();
            return new ObjectLiteral(keys,object.values().stream().map(v->rewrite(v,record,env,depth+1)).toList(),object.span());
        }
        if(expr instanceof Literal l)return new Literal(l.value(),l.type(),l.span());
        if(expr instanceof Name n)return new Name(n.name(),n.span());
        if(expr instanceof Unary u)return new Unary(u.op(),rewrite(u.value(),record,env,depth+1),u.span());
        if(expr instanceof Binary b)return new Binary(b.op(),rewrite(b.left(),record,env,depth+1),rewrite(b.right(),record,env,depth+1),b.span());
        if(expr instanceof Call c)return new Call(rewrite(c.receiver(),record,env,depth+1),c.parts(),c.args().stream().map(a->rewrite(a,record,env,depth+1)).toList(),rewrite(c.block(),record,env,depth+1),c.span());
        if(expr instanceof ObjectAccess a)return new ObjectAccess(rewrite(a.selector(),record,env,depth+1),rewrite(a.target(),record,env,depth+1),a.span());
        if(expr instanceof VectorAccess a)return new VectorAccess(rewrite(a.index(),record,env,depth+1),rewrite(a.target(),record,env,depth+1),a.span());
        if(expr instanceof Get g)return new Get(rewrite(g.receiver(),record,env,depth+1),rewrite(g.key(),record,env,depth+1),g.span());
        if(expr instanceof ObjectLiteral o)return new ObjectLiteral(o.keys(),o.values().stream().map(v->rewrite(v,record,env,depth+1)).toList(),o.span());
        if(expr instanceof Convert c)return new Convert(rewrite(c.value(),record,env,depth+1),c.target(),c.mode(),c.span());
        if(expr instanceof Rescue r)return new Rescue(rewrite(r.value(),record,env,depth+1),rewrite(r.handler(),record,env,depth+1),r.span());
        if(expr instanceof Choice c)return new Choice(rewrite(c.condition(),record,env,depth+1),rewrite(c.yes(),record,env,depth+1),rewrite(c.no(),record,env,depth+1),c.span());
        if(expr instanceof Range r)return new Range(rewrite(r.start(),record,env,depth+1),rewrite(r.end(),record,env,depth+1),rewrite(r.step(),record,env,depth+1),r.inclusive(),r.span());
        if(expr instanceof Comprehension c)return new Comprehension(c.names(),rewrite(c.values(),record,env,depth+1),rewrite(c.key(),record,env,depth+1),rewrite(c.value(),record,env,depth+1),rewrite(c.filter(),record,env,depth+1),c.span());
        if(expr instanceof ComposedArg c)return new ComposedArg(c.alias(),c.args().stream().map(e->rewrite(e,record,env,depth+1)).toList(),c.span());
        if(expr instanceof Ast.CompactVector v)return new Ast.CompactVector(v.items().stream().map(e->rewrite(e,record,env,depth+1)).toList(),v.span());
        if(expr instanceof Ast.Vector v)return new Ast.Vector(v.items().stream().map(e->rewrite(e,record,env,depth+1)).toList(),v.span());
        if(expr instanceof Lambda l)return new Lambda(l.params(),l.result(),expandBody(l.body(),record,new LinkedHashMap<>(env),depth+1),l.nonLocal(),l.span());
        if(expr instanceof BlockExpr b)return new BlockExpr(expandBody(b.body(),record,new LinkedHashMap<>(env),depth+1),b.span());
        if(expr instanceof TypeExpr t)return new TypeExpr(t.type(),t.span());
        if(expr instanceof Match m)return new Match(rewrite(m.value(),record,env,depth+1),m.arms().stream().map(a->new MatchArm(a.patterns().stream().map(p->rewrite(p,record,env,depth+1)).toList(),rewrite(a.guard(),record,env,depth+1),rewrite(a.body(),record,env,depth+1),a.span())).toList(),rewrite(m.otherwise(),record,env,depth+1),m.span());
        if(expr instanceof ChainedDo d)return new ChainedDo(expandBody(d.yes(),record,new LinkedHashMap<>(env),depth+1),expandBody(d.no(),record,new LinkedHashMap<>(env),depth+1),d.span());
        if(expr instanceof TypeTest t)return new TypeTest(t.op(),rewrite(t.value(),record,env,depth+1),t.target(),t.span());
        error(expr,"M3003","construção desconhecida durante expansão");return null;
    }

    private Object evaluate(Expr expr,Ast.Record record,Map<String,Object> env) {
        step(expr,0);
        if(expr instanceof Literal l)return l.type().name().equals("tag")?new TagValue(l.value().toString()):l.value();
        if(expr instanceof TypeExpr type)return type.type();
        if(expr instanceof TypeTest test) {
            Object value=evaluate(test.value(),record,env);
            Type actual;
            if(test.op().equals("&")) {
                if(!(value instanceof Type))error(test,"M3015","teste de subtipo exige tipo em compile time");
                actual=(Type)value;
            } else actual=valueType(value);
            return new TypeSystem(aliases,records).assignable(actual,test.target());
        }
        if(expr instanceof Name name) {
            if(name.name().equals("&attrs")) {
                if(record==null)error(name,"M3004","&attrs exige um record concreto durante a expansão");
                var attrs=new ArrayList<Object>();
                for(Field field:record.fields()) {
                    var attr=new LinkedHashMap<String,Object>();
                    attr.put("name",field.name().startsWith("_")?field.name().substring(1):field.name());
                    attr.put("type",field.type());attr.put("labels",field.labels().stream().map(TagValue::new).toList());attrs.add(Collections.unmodifiableMap(attr));
                }
                return Collections.unmodifiableList(attrs);
            }
            if(env.containsKey(name.name()))return env.get(name.name());
            if(records.containsKey(name.name())||aliases.containsKey(name.name())||Set.of("int","str","bool","tag","long","double","float","byte","short","ubyte","ushort","uint","ulong","nil","Number","any").contains(name.name()))return aliases.getOrDefault(name.name(),new Type(name.name()));
            error(name,"M3005","valor não conhecido em tempo de compilação: "+name.name());
        }
        if(expr instanceof Splice splice)return evaluate(splice.value(),record,env);
        if(expr instanceof TemplateName template) {
            var name=new StringBuilder();for(Expr part:template.parts())name.append(text(evaluate(part,record,env)));
            return name.toString();
        }
        if(expr instanceof DynamicTag tag)return new TagValue(tagName(evaluate(tag.value(),record,env),tag));
        if(expr instanceof ObjectAccess access) return evaluate(new Get(access.target(),access.selector(),access.span()),record,env);
        if(expr instanceof VectorAccess access) return evaluate(new Call(access.target(),List.of("nth"),List.of(access.index()),access.span()),record,env);
        if(expr instanceof Get get) {
            Object receiver=evaluate(get.receiver(),record,env),key=evaluate(get.key(),record,env);
            if(!(receiver instanceof Map<?,?>))error(get,"M3006","acesso em compile time exige objeto de metadados");
            return ((Map<?,?>)receiver).get(tagName(key,get));
        }
        if(expr instanceof Ast.CompactVector vector)return vector.items().stream().map(e->evaluate(e,record,env)).toList();
        if(expr instanceof Ast.Vector vector)return vector.items().stream().map(e->evaluate(e,record,env)).toList();
        if(expr instanceof ObjectLiteral object) {
            var map=new LinkedHashMap<String,Object>();for(int i=0;i<object.keys().size();i++)map.put(object.keys().get(i),evaluate(object.values().get(i),record,env));return Collections.unmodifiableMap(map);
        }
        if(expr instanceof Range range) {
            long first=integral(evaluate(range.start(),record,env),range),last=integral(evaluate(range.end(),record,env),range),by=integral(evaluate(range.step(),record,env),range);
            if(by==0)error(range,"M3007","passo de intervalo em compile time não pode ser zero");
            var values=new ArrayList<Object>();
            for(long n=first;by>0?(range.inclusive()?n<=last:n<last):(range.inclusive()?n>=last:n>last);) {
                step(range,0);values.add(n);long next=n+by;if(by>0?next<n:next>n)break;n=next;
            }
            return Collections.unmodifiableList(values);
        }
        if(expr instanceof Unary unary) {
            Object value=evaluate(unary.value(),record,env);
            if(unary.op().equals("not"))return !booleanValue(value,unary);
            if(unary.op().equals("-")) {if(value instanceof Double)return -number(value,unary);return Math.negateExact(integral(value,unary));}
            if(unary.op().equals("~~~"))return ~integral(value,unary);
            if(unary.op().equals("stringify"))return text(value);
            error(unary,"M3008","operação não permitida em tempo de compilação: "+unary.op());
        }
        if(expr instanceof Binary binary) {
            Object left=evaluate(binary.left(),record,env);
            if(Set.of("and","&&").contains(binary.op()))return booleanValue(left,binary)&&booleanValue(evaluate(binary.right(),record,env),binary);
            if(Set.of("or","||").contains(binary.op()))return booleanValue(left,binary)||booleanValue(evaluate(binary.right(),record,env),binary);
            if(binary.op().equals("?:"))return left==null?evaluate(binary.right(),record,env):left;
            Object right=evaluate(binary.right(),record,env);
            return binary(binary,left,right);
        }
        if(expr instanceof Choice choice)return booleanValue(evaluate(choice.condition(),record,env),choice)?evaluate(choice.yes(),record,env):evaluate(choice.no(),record,env);
        if(expr instanceof Call call) {
            if(call.receiver()==null)error(call,"M3008","métodos de runtime não executam durante expansão");
            Object receiver=evaluate(call.receiver(),record,env);var args=call.args().stream().map(a->evaluate(a,record,env)).toList();
            if(Set.of("has?","has_value?","has_any?","has_all?").contains(call.selector())&&receiver instanceof List<?> list) {
                if(call.selector().equals("has_all?"))return list.containsAll(args);
                return args.stream().anyMatch(list::contains);
            }
            if(call.selector().equals("has_key?")&&receiver instanceof Map<?,?> map&&args.size()==1)return map.containsKey(tagName(args.getFirst(),call));
            if(Set.of("get","get_element","maybe_get").contains(call.selector())&&receiver instanceof Map<?,?> map&&args.size()==1)return map.get(tagName(args.getFirst(),call));
            if(call.selector().equals("length")&&args.isEmpty()) {
                if(receiver instanceof List<?> list)return (long)list.size();if(receiver instanceof Map<?,?> map)return (long)map.size();if(receiver instanceof String s)return (long)s.length();
            }
            if(args.isEmpty()&&receiver instanceof String s) {
                if(call.selector().equals("upcase"))return s.toUpperCase(Locale.ROOT);if(call.selector().equals("downcase"))return s.toLowerCase(Locale.ROOT);
            }
            error(call,"M3008","mensagem não permitida em tempo de compilação: "+call.selector());
        }
        error(expr,"M3008","expressão não avaliável em tempo de compilação");return null;
    }

    private Object binary(Binary expr,Object left,Object right) {
        String op=expr.op();
        if(op.equals("=="))return Objects.equals(left,right);
        if(op.equals("!="))return !Objects.equals(left,right);
        if(op.equals("like")||op.equals("~"))return text(left).equals(text(right));
        if(op.equals("+")&&left instanceof String a&&right instanceof String b)return a+b;
        double l=number(left,expr),r=number(right,expr);boolean floating=left instanceof Double||right instanceof Double;
        if(Set.of("<",">","<=",">=").contains(op))return switch(op){case "<"->l<r;case ">"->l>r;case "<="->l<=r;default->l>=r;};
        if(floating||op.equals("/"))return switch(op){case "+"->l+r;case "-"->l-r;case "*"->l*r;case "/"->l/r;case "**"->Math.pow(l,r);default->invalidBinary(expr);};
        long a=integral(left,expr),b=integral(right,expr);
        try {
            return switch(op) {
                case "+"->Math.addExact(a,b);case "-"->Math.subtractExact(a,b);case "*"->Math.multiplyExact(a,b);
                case "**"->(long)Math.pow(a,b);case "&&&"->a&b;case "|||"->a|b;case "^^^"->a^b;case "<<<"->a<<b;case ">>>"->a>>b;
                default->invalidBinary(expr);
            };
        }catch(ArithmeticException ex){error(expr,"M3009","overflow em expressão de compile time");return null;}
    }
    private Object invalidBinary(Binary expr) {error(expr,"M3008","operador não permitido em tempo de compilação: "+expr.op());return null;}
    private static String text(Object value) {if(value==null)return "nil";if(value instanceof TagValue tag)return tag.name();return value.toString();}
    private static String tagName(Object value,Node origin) {
        if(value instanceof TagValue tag)return tag.name();
        if(value instanceof String text&&!text.isEmpty())return text;
        error(origin,"M3010","nome/tag gerado exige texto ou tag");return null;
    }
    private static String identifier(Object value,Node origin) {
        String valueText=text(value);
        if(!valueText.matches("[A-Za-z_][A-Za-z_0-9]*\\??"))error(origin,"M3011","identificador gerado inválido: "+valueText);
        return valueText;
    }
    private static boolean booleanValue(Object value,Node origin) {if(value instanceof Boolean b)return b;error(origin,"M3012","condição de compile time exige bool");return false;}
    private static double number(Object value,Node origin) {if(value instanceof Number n)return n.doubleValue();error(origin,"M3013","operação de compile time exige número");return 0;}
    private static long integral(Object value,Node origin) {if(value instanceof Byte||value instanceof Short||value instanceof Integer||value instanceof Long)return ((Number)value).longValue();error(origin,"M3013","operação de compile time exige inteiro");return 0;}
    private static Type valueType(Object value) {
        if(value==null)return new Type("nil");
        if(value instanceof Type type)return new Type("type",List.of(type));
        if(value instanceof TagValue)return TAG;
        if(value instanceof String)return STR;
        if(value instanceof Boolean)return new Type("bool");
        if(value instanceof Double||value instanceof Float)return new Type("double");
        if(value instanceof Number)return new Type("int");
        if(value instanceof List<?>)return new Type("vec",List.of(new Type("any")));
        return new Type("obj",List.of(new Type("any")));
    }
    private Expr literal(Object value,Span span) {
        if(value==null)return new Literal(null,new Type("nil"),span);
        if(value instanceof Boolean)return new Literal(value,new Type("bool"),span);
        if(value instanceof TagValue tag)return new Literal(tag.name(),TAG,span);
        if(value instanceof String)return new Literal(value,STR,span);
        if(value instanceof Byte||value instanceof Short||value instanceof Integer||value instanceof Long)return new Literal(((Number)value).longValue(),new Type("int"),span);
        if(value instanceof Number)return new Literal(((Number)value).doubleValue(),new Type("double"),span);
        if(value instanceof Type type)return new TypeExpr(type,span);
        if(value instanceof List<?> list)return new Ast.Vector(list.stream().map(v->literal(v,span)).toList(),span);
        if(value instanceof Map<?,?> map) {
            var keys=new ArrayList<String>();var values=new ArrayList<Expr>();
            for(var entry:map.entrySet()){keys.add(tagName(entry.getKey(),new Literal(null,STR,span)));values.add(literal(entry.getValue(),span));}
            return new ObjectLiteral(keys,values,span);
        }
        throw new Diagnostic("M3014",span,"valor não representável em splice");
    }
}
