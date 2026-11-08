package mach.semantic;

import mach.ast.Ast.Type;
import mach.ast.Ast.Span;
import mach.frontend.Diagnostic;
import java.util.*;

/** Static contracts for immutable optional collections; source impls take precedence. */
public final class ExtraCollectionContracts {
    private ExtraCollectionContracts() {}
    public static final Set<String> TYPES=Set.of("Array","MutableArray","Set","SortedSet","Dictionary","SortedMap","Deque","Bag","PriorityQueue","Queue","Stack","Pair","Range");
    private static final Type ANY=new Type("any"),BOOL=new Type("bool"),INT=new Type("int"),NIL=new Type("nil");
    private static final TypeSystem DEFAULT_TYPES=new TypeSystem(Map.of(),Map.of());
    private static final ThreadLocal<TypeSystem> CONTEXT=new ThreadLocal<>();
    private static TypeSystem types(){TypeSystem active=CONTEXT.get();return active==null?DEFAULT_TYPES:active;}
    public static Type resolve(Type receiver,String selector,List<Type> args,Type block,Span span,TypeSystem types){
        TypeSystem old=CONTEXT.get();CONTEXT.set(types);
        try{return resolve(receiver,selector,args,block,span);}finally{if(old==null)CONTEXT.remove();else CONTEXT.set(old);}
    }
    private static Type vec(Type type){return new Type("vec",List.of(type));}
    private static Type maybe(Type type){return new Type("maybe",List.of(type));}
    private static Type attempt(Type type){return new Type("try",List.of(type));}
    private static void count(List<Type> args,int count,Span span){if(args.size()!=count)throw new Diagnostic("M2201",span,"mensagem exige "+count+" argumento(s)");}
    private static void require(Type value,Type expected,Span span){if(!types().assignable(value,expected))throw new Diagnostic("M2101",span,"tipo "+value+" incompatível com "+expected);}
    private static Type arg(Type type,int index){return type.args().size()>index?type.args().get(index):ANY;}
    private static boolean map(Type type){return Set.of("Dictionary","SortedMap").contains(type.name());}
    private static boolean sorted(Type type){return Set.of("SortedSet","SortedMap").contains(type.name());}
    private static void comparable(Type type,Span span){if(!Set.of("byte","ubyte","short","ushort","int","uint","long","ulong","float","double","str","char").contains(type.name()))throw new Diagnostic("M2101",span,"coleção ordenada exige chaves numéricas, str ou char: "+type);}
    private static final Set<String> CALLBACKS=Set.of("each","each_with_index","collect","map","select","filter","reject","flat_map","find","find_index","all?","any?","none?","one?","count","partition","group_by","sum_by","min_by","max_by","sort_by","sort_with","take_while","drop_while","chunk_while","reduce:with","fold:with","inject:with","each_slice","each_cons");
    public static List<Type> blockInputs(Type receiver,String selector,List<Type> args){
        if(receiver==null||!TYPES.contains(receiver.name())||!CALLBACKS.contains(selector)||receiver.name().equals("PriorityQueue"))return null;
        if(map(receiver))return List.of(arg(receiver,0),arg(receiver,1));
        return CollectionContracts.blockInputs(vec(arg(receiver,0)),selector,args);
    }
    public static Type resolve(Type receiver,String selector,List<Type> args,Type block,Span span){
        if(receiver==null)return null;
        if(receiver.name().equals("vec")&&selector.equals("to_set")){count(args,0,span);return new Type("Set",List.of(arg(receiver,0)));}
        if(receiver.name().equals("type")&&receiver.args().size()==1&&TYPES.contains(receiver.args().getFirst().name())&&Set.of("new","create","new:","create:","of","of:").contains(selector)){
            Type target=receiver.args().getFirst();int parameterCount=map(target)?2:1;
            if(!target.args().isEmpty()&&target.args().size()!=parameterCount)throw new Diagnostic("M2101",span,target.name()+" exige "+parameterCount+" parâmetros de tipo");
            if(target.name().equals("PriorityQueue")){count(args,0,span);return target.args().isEmpty()?new Type(target.name(),List.of(ANY)):target;}
            if(args.size()>1)throw new Diagnostic("M2201",span,target.name()+" exige zero ou um argumento");
            if(map(target)){
                Type key=arg(target,0),value=arg(target,1);
                if(!args.isEmpty()){
                    Type input=args.getFirst();
                    if(input.name().equals("obj")){if(target.args().isEmpty()){key=new Type("tag");value=arg(input,0);}else{require(new Type("tag"),key,span);require(arg(input,0),value,span);}}
                    else if(input.name().equals("vec")&&arg(input,0).name().equals("vec")){
                        Type pairElement=arg(arg(input,0),0);if(target.args().isEmpty()){key=pairElement;value=pairElement;}else if(!pairElement.equals(ANY)){require(pairElement,key,span);require(pairElement,value,span);}
                    } else throw new Diagnostic("M2101",span,"construtor de mapa exige obj ou vetor de pares");
                }
                if(sorted(target))comparable(key,span);
                Type result=new Type(target.name(),List.of(key,value));return !args.isEmpty()&&args.getFirst().name().equals("vec")?attempt(result):result;
            }
            Type element=arg(target,0);
            if(!args.isEmpty()){
                Type input=args.getFirst();if(input.name().equals("vec")){
                    if(target.args().isEmpty())element=arg(input,0);else require(arg(input,0),element,span);
                } else if(target.args().isEmpty()&&Set.of("Array","MutableArray","Queue","Stack","Deque","Bag","Set","SortedSet","Pair","Range").contains(target.name())){
                    element=input;
                } else if(Set.of("Array","MutableArray","Queue","Stack","Deque","Bag","Set","SortedSet","Pair","Range").contains(target.name())){
                    require(input,element,span);
                } else {throw new Diagnostic("M2101",span,"construtor de coleção exige vetor");}
            }
            if(sorted(target))comparable(element,span);
            return new Type(target.name(),List.of(element));
        }
        if(!TYPES.contains(receiver.name()))return null;
        Type element=arg(receiver,0);
        if(Set.of("length","size").contains(selector)){count(args,0,span);return INT;}
        if(selector.equals("empty?")){count(args,0,span);return BOOL;}
        if(selector.equals("copy")){count(args,0,span);return receiver;}
        if(map(receiver))return mapping(receiver,selector,args,block,span);
        if(receiver.name().equals("PriorityQueue"))return switch(selector){
            case "insert:with"->{count(args,2,span);require(args.getFirst(),element,span);if(!types().numeric(args.getLast()))throw new Diagnostic("M2101",span,"prioridade deve ser numérica");yield receiver;}
            case "pop_min"->{count(args,0,span);yield receiver;}
            case "peek"->{count(args,0,span);yield maybe(element);}
            case "to_vec"->{count(args,0,span);yield vec(element);}
            default->null;};
        switch(selector){
            case "add","push_front","push_back":count(args,1,span);require(args.getFirst(),element,span);return receiver;
            case "delete":count(args,1,span);require(args.getFirst(),element,span);return receiver;
            case "has?":count(args,1,span);require(args.getFirst(),element,span);return BOOL;
            case "union","intersect","difference","subset?":count(args,1,span);require(args.getFirst(),receiver,span);return selector.equals("subset?")?BOOL:receiver;
            case "front","back","first","last":count(args,0,span);return maybe(element);
            case "pop_front","pop_back":count(args,0,span);return receiver;
            case "to_vec":count(args,0,span);return vec(element);
            case "range":if(sorted(receiver)){count(args,1,span);require(args.getFirst(),vec(element),span);return receiver;}return null;
            case "range:to":if(sorted(receiver)){count(args,2,span);for(Type bound:args)require(bound,element,span);return receiver;}return null;
        }
        Type vectorResult=CollectionContracts.resolve(vec(element),selector,args,block,span);
        if(vectorResult!=null&&Set.of("select","filter","reject","reverse","sort","sort_by","sort_with","take","drop","take_while","drop_while","rotate","shuffle","uniq").contains(selector))return vectorResult.name().equals("try")?attempt(receiver):receiver;
        return vectorResult;
    }
    private static Type mapping(Type receiver,String selector,List<Type> args,Type block,Span span){
        Type key=arg(receiver,0),value=arg(receiver,1);
        if(selector.equals("count")&&args.isEmpty()&&block==null)return INT;
        switch(selector){
            case "get","maybe_get","has_key?":count(args,1,span);require(args.getFirst(),key,span);return selector.equals("get")?attempt(value):selector.equals("maybe_get")?maybe(value):BOOL;
            case "put":count(args,2,span);require(args.getFirst(),key,span);require(args.getLast(),value,span);return receiver;
            case "delete":count(args,1,span);require(args.getFirst(),key,span);return receiver;
            case "keys":count(args,0,span);return vec(key);
            case "values":count(args,0,span);return vec(value);
            case "pairs","to_vec":count(args,0,span);return vec(vec(ANY));
            case "merge":count(args,1,span);require(args.getFirst(),receiver,span);return receiver;
            case "first","last":if(sorted(receiver)){count(args,0,span);return maybe(vec(ANY));}return null;
            case "range":if(sorted(receiver)){count(args,1,span);require(args.getFirst(),vec(key),span);return receiver;}return null;
            case "range:to":if(sorted(receiver)){count(args,2,span);for(Type bound:args)require(bound,key,span);return receiver;}return null;
        }
        if(CALLBACKS.contains(selector)){
            if(block!=null)count(args,0,span);else count(args,1,span);
            Type callback=block!=null?block:args.size()==1&&args.getFirst().name().equals("lambda")?args.getFirst():null;
            if(callback==null||callback.args().size()!=3)throw new Diagnostic("M2201",span,"bloco de mapa exige chave e valor");
            require(key,callback.args().get(0),span);require(value,callback.args().get(1),span);Type result=callback.args().getLast();
            if(Set.of("select","filter","reject","all?","any?","none?","count","find").contains(selector))require(types().successOfTry(result),BOOL,span);
            Type output=switch(selector){case "each"->NIL;case "collect","map"->vec(types().successOfTry(result));case "select","filter","reject"->receiver;case "all?","any?","none?"->BOOL;case "count"->INT;case "find"->maybe(vec(ANY));default->null;};
            return output!=null&&types().includesFail(result)?attempt(output):output;
        }
        return null;
    }
}
