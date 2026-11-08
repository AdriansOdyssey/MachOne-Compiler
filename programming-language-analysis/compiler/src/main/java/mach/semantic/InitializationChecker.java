package mach.semantic;
import mach.ast.Ast;
import mach.ast.Ast.*;
import mach.frontend.Diagnostic;
import java.util.*;

/** Definite initialization on normal constructor paths; failure paths do not publish an object. */
final class InitializationChecker {
    private final Ast.Record record;
    private final Map<Expr,Type> types;
    private final TypeSystem ts;
    private final Set<String> required=new LinkedHashSet<>();
    private record Flow(Set<String> fields,boolean exits) {}
    InitializationChecker(Ast.Record record,Map<Expr,Type> types,TypeSystem ts){this.record=record;this.types=types;this.ts=ts;for(var f:record.fields())if(!ts.includesNil(f.type()))required.add(name(f.name()));}
    void check(Method method){Flow end=body(method.body(),new LinkedHashSet<>());if(!end.exits())complete(method,end.fields());}
    private static String name(String n){return n.startsWith("_")?n.substring(1):n;}
    private void complete(Node origin,Set<String> assigned){var missing=new LinkedHashSet<>(required);missing.removeAll(assigned);if(!missing.isEmpty())throw new Diagnostic("M2315",origin.span(),"initialize não inicializa todos os atributos: "+String.join(", ",missing));}
    private Flow body(List<Stmt> statements,Set<String> before){Flow flow=new Flow(new LinkedHashSet<>(before),false);for(var stmt:statements){if(flow.exits())break;flow=statement(stmt,flow.fields());}return flow;}
    private Flow statement(Stmt stmt,Set<String> fields){
        if(stmt instanceof Return ret){Flow value=expression(ret.value(),fields);Type type=types.getOrDefault(ret.value(),TypeSystem.ANY);if(!type.equals(TypeSystem.FAIL)&&!type.equals(TypeSystem.NEVER))complete(ret,value.fields());return new Flow(value.fields(),true);}
        if(stmt instanceof Assign assign){Flow value=expression(assign.value(),fields);var next=new LinkedHashSet<>(value.fields());if(assign.name().equals("self")&&assign.value() instanceof Binary binary&&binary.op().equals("|")&&binary.right() instanceof ObjectLiteral update)for(String key:update.keys())next.add(name(key));return new Flow(next,value.exits());}
        if(stmt instanceof Bind bind)return expression(bind.value(),fields);
        if(stmt instanceof Eval eval)return expression(eval.value(),fields);
        if(stmt instanceof Branch branch){var arms=new ArrayList<Flow>();for(int i=0;i<branch.conditions().size();i++){Flow test=expression(branch.conditions().get(i),fields);arms.add(body(branch.arms().get(i),test.fields()));}arms.add(body(branch.otherwise(),fields));return merge(arms);}
        if(stmt instanceof BeingLoop loop){var next=new LinkedHashSet<>(fields);expression(loop.initial(),next);expression(loop.condition(),next);body(loop.body(),new LinkedHashSet<>(next));return new Flow(fields,false);}
        if(stmt instanceof While loop){expression(loop.condition(),fields);body(loop.body(),new LinkedHashSet<>(fields));return new Flow(fields,false);}
        if(stmt instanceof For loop){expression(loop.values(),fields);body(loop.body(),new LinkedHashSet<>(fields));return new Flow(fields,false);}
        return new Flow(fields,false);
    }
    private Flow merge(List<Flow> arms){Set<String> merged=null;boolean exits=true;for(var arm:arms)if(!arm.exits()){exits=false;if(merged==null)merged=new LinkedHashSet<>(arm.fields());else merged.retainAll(arm.fields());}return new Flow(merged==null?Set.of():merged,exits);}
    private Flow expression(Expr expr,Set<String> fields){
        if(expr==null)return new Flow(fields,false);
        if(expr instanceof BlockExpr block)return body(block.body(),fields);
        if(expr instanceof Choice choice){Flow condition=expression(choice.condition(),fields);return merge(List.of(expression(choice.yes(),new LinkedHashSet<>(condition.fields())),expression(choice.no(),new LinkedHashSet<>(condition.fields()))));}
        if(expr instanceof Match match){Flow subject=expression(match.value(),fields);var arms=new ArrayList<Flow>();for(var arm:match.arms())arms.add(expression(arm.body(),new LinkedHashSet<>(subject.fields())));arms.add(expression(match.otherwise(),new LinkedHashSet<>(subject.fields())));return merge(arms);}
        if(expr instanceof ObjectAccess access){
            expression(access.target(),fields);expression(access.selector(),fields);
            if(access.target() instanceof Name receiver&&receiver.name().equals("self")&&access.selector() instanceof Literal key&&required.contains(name(key.value().toString()))&&!fields.contains(name(key.value().toString())))throw new Diagnostic("M2316",access.span(),"atributo lido antes de ser inicializado: "+key.value());
        }
        else if(expr instanceof Get get){expression(get.receiver(),fields);expression(get.key(),fields);if(get.receiver() instanceof Name receiver&&receiver.name().equals("self")&&get.key() instanceof Literal key&&required.contains(name(key.value().toString()))&&!fields.contains(name(key.value().toString())))throw new Diagnostic("M2316",get.span(),"atributo lido antes de ser inicializado: "+key.value());}
        else if(expr instanceof Binary binary){expression(binary.left(),fields);expression(binary.right(),fields);}
        else if(expr instanceof Unary unary)expression(unary.value(),fields);
        else if(expr instanceof Call call){expression(call.receiver(),fields);for(var arg:call.args())expression(arg,fields);}
        else if(expr instanceof Convert convert)expression(convert.value(),fields);
        else if(expr instanceof Rescue rescue)expression(rescue.value(),fields);
        else if(expr instanceof ObjectLiteral object)for(var value:object.values())expression(value,fields);
        else if(expr instanceof Ast.CompactVector vector)for(var value:vector.items())expression(value,fields);
        else if(expr instanceof Ast.Vector vector)for(var value:vector.items())expression(value,fields);
        return new Flow(fields,types.getOrDefault(expr,TypeSystem.ANY).equals(TypeSystem.NEVER));
    }
}
