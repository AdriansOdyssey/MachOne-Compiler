package mach.semantic;
import mach.ast.Ast.*;
import mach.frontend.Diagnostic;
import java.util.*;

/** Static collection contracts and callback shapes, independent of storage and the JVM backend. */
public final class CollectionContracts {
    private CollectionContracts() {}
    private static final Type ANY=new Type("any"),INT=new Type("int"),BOOL=new Type("bool"),TAG=new Type("tag"),NIL=new Type("nil"),STR=new Type("str");
    private static final TypeSystem DEFAULT=new TypeSystem(Map.of(),Map.of());private static final ThreadLocal<TypeSystem> CONTEXT=new ThreadLocal<>();
    private static TypeSystem types(){var types=CONTEXT.get();return types==null?DEFAULT:types;}
    private static Type vec(Type type){return new Type("vec",List.of(type));}private static Type obj(Type type){return new Type("obj",List.of(type));}private static Type maybe(Type type){return new Type("maybe",List.of(type));}private static Type attempt(Type type){return new Type("try",List.of(type));}
    private static Type element(Type type){return type.args().isEmpty()?ANY:type.args().getFirst();}private static boolean callable(Type type){return type.name().equals("lambda");}
    private static void error(Span span,String text){throw new Diagnostic("M2101",span,text);}private static void arity(Span span,List<Type> args,int count){if(args.size()!=count)throw new Diagnostic("M2201",span,"mensagem exige "+count+" argumento(s)");}
    private static void require(Span span,Type actual,Type expected){if(!types().assignable(actual,expected))error(span,"tipo "+actual+" incompatível com "+expected);}
    private static void integer(Span span,Type type){if(!Set.of("byte","ubyte","short","ushort","int","uint","long","ulong").contains(type.name()))error(span,"índice deve ser inteiro, recebido "+type);}
    private static Type output(Type fn){return fn.args().isEmpty()?ANY:fn.args().getLast();}private static Type withEffect(Type result,Type fn){return types().includesFail(output(fn))?attempt(result):result;}private static Type success(Type type){return types().successOfTry(type);}
    private static final Set<String> CALLBACKS=Set.of("each","each_with_index","collect","map","select","filter","reject","flat_map","find","detect","find_index","all?","any?","none?","one?","count","partition","group_by","sum_by","min_by","max_by","sort_by","sort_with","take_while","drop_while","chunk_while","reduce:with","fold:with","inject:with","each_slice","each_cons","map_values","map_keys","update:with");
    private static final Set<String> PREDICATES=Set.of("select","filter","reject","find","detect","find_index","all?","any?","none?","one?","count","partition","take_while","drop_while","chunk_while");
    public static List<Type> blockInputs(Type receiver,String selector,List<Type> args){if(receiver==null||!Set.of("vec","obj").contains(receiver.name())||!CALLBACKS.contains(selector))return null;Type value=element(receiver);if(Set.of("reduce:with","fold:with","inject:with").contains(selector))return receiver.name().equals("obj")?List.of(args.isEmpty()?ANY:args.getFirst(),TAG,value):List.of(args.isEmpty()?ANY:args.getFirst(),value);if(receiver.name().equals("obj"))return switch(selector){case "map_values","update:with"->List.of(value);case "map_keys"->List.of(TAG);default->List.of(TAG,value);};return switch(selector){case "each_with_index"->List.of(value,INT);case "each_slice","each_cons"->List.of(receiver);case "sort_with","chunk_while"->List.of(value,value);default->List.of(value);};}
    public static Type blockResult(String selector){return PREDICATES.contains(selector)?BOOL:ANY;}
    public static Type resolve(Type receiver,String selector,List<Type> args,Type block,Span span,TypeSystem types){TypeSystem old=CONTEXT.get();CONTEXT.set(types);try{return resolve(receiver,selector,args,block,span);}finally{if(old==null)CONTEXT.remove();else CONTEXT.set(old);}}
    public static Type resolve(Type receiver,String selector,List<Type> args,Type block,Span span){
        if(selector.equals("\\\\"))selector="delete";if(receiver==null)return null;
        if(receiver.name().equals("type")&&receiver.args().size()==1&&Set.of("Vector","vec").contains(receiver.args().getFirst().name())&&Set.of("new","create").contains(selector)){if(args.size()>1)throw new Diagnostic("M2201",span,"Vector new exige zero ou um argumento");Type target=receiver.args().getFirst();if(args.isEmpty())return target.name().equals("vec")?target:vec(ANY);if(!args.getFirst().name().equals("vec"))error(span,"Vector new exige vetor");return args.getFirst();}
        if(!Set.of("vec","obj").contains(receiver.name()))return null;boolean map=receiver.name().equals("obj");Type value=element(receiver);
        if(selector.equals("head")){arity(span,args,0);return maybe(value);}if(selector.equals("tail")){arity(span,args,0);return receiver;}
        if(selector.equals("nth")&&!map){arity(span,args,1);Type key=args.getFirst();if(key.name().equals("vec")){integer(span,element(key));return attempt(receiver);}integer(span,key);return attempt(value);}
        if(selector.equals("*")&&!map){arity(span,args,1);integer(span,args.getFirst());return attempt(receiver);}if(selector.equals("+")&&!map){arity(span,args,1);require(span,args.getFirst(),receiver);return receiver;}if(selector.equals("<<")){arity(span,args,1);require(span,args.getFirst(),map?receiver:value);return receiver;}if(selector.equals("|")&&map){arity(span,args,1);require(span,args.getFirst(),receiver);return receiver;}
        Type fn=block!=null?block:!args.isEmpty()&&callable(args.getLast())?args.getLast():null;List<Type> ordinary=fn!=null&&block==null?args.subList(0,args.size()-1):args;
        if(fn!=null&&CALLBACKS.contains(selector)){
            List<Type> inputs=blockInputs(receiver,selector,ordinary);if(fn.args().size()!=inputs.size()+1)throw new Diagnostic("M2201",span,"aridade inválida do bloco de "+selector);for(int i=0;i<inputs.size();i++)require(span,inputs.get(i),fn.args().get(i));Type successful=success(output(fn));if(PREDICATES.contains(selector))require(span,successful,BOOL);
            Type result=switch(selector){case "each","each_with_index","each_slice","each_cons"->NIL;case "collect","map"->vec(successful);case "map_values"->obj(successful);case "map_keys"->{require(span,successful,TAG);yield receiver;}case "flat_map"->successful.name().equals("vec")?successful:vec(successful);case "find","detect","min_by","max_by"->maybe(map?receiver:value);case "find_index"->maybe(INT);case "all?","any?","none?","one?"->BOOL;case "count"->INT;case "partition"->vec(receiver);case "group_by"->{require(span,successful,TAG);yield obj(receiver);}case "sum_by"->successful;case "sort_by"->map?vec(vec(ANY)):receiver;case "sort_with"->{if(!successful.equals(BOOL))integer(span,successful);yield receiver;}case "reduce:with","fold:with","inject:with"->{arity(span,ordinary,1);require(span,successful,ordinary.getFirst());yield successful;}case "update:with"->{arity(span,ordinary,1);require(span,ordinary.getFirst(),TAG);require(span,successful,value);yield attempt(receiver);}default->receiver;};
            if(Set.of("each_slice","each_cons").contains(selector)){arity(span,ordinary,1);integer(span,ordinary.getFirst());result=attempt(NIL);}else if(!Set.of("reduce:with","fold:with","inject:with","update:with").contains(selector))arity(span,ordinary,0);return withEffect(result,fn);
        }
        if(Set.of("length","size","count").contains(selector)&&args.isEmpty())return INT;if(Set.of("empty?","any?","none?").contains(selector)&&args.isEmpty())return BOOL;if(selector.equals("copy")){arity(span,args,0);return receiver;}
        if(map)return objectContract(receiver,value,selector,args,span);
        switch(selector){
            case "append","unshift":arity(span,args,1);require(span,args.getFirst(),value);return receiver;
            case "push":for(Type arg:args)require(span,arg,value);return receiver;
            case "merge":for(Type arg:args)require(span,arg,arg.name().equals("vec")?receiver:value);return receiver;
            case "concat","union","intersect","difference":arity(span,args,1);require(span,args.getFirst(),receiver);return receiver;
            case "insert:at","put:at":arity(span,args,2);require(span,args.getFirst(),value);integer(span,args.getLast());return attempt(receiver);
            case "delete":if(args.isEmpty())throw new Diagnostic("M2201",span,"delete exige valor");for(Type arg:args)require(span,arg,value);return receiver;
            case "delete_nth":arity(span,args,1);integer(span,args.getFirst());return attempt(receiver);
            case "element_at":arity(span,args,1);integer(span,args.getFirst());return attempt(value);
            case "first","last":if(args.isEmpty())return maybe(value);arity(span,args,1);integer(span,args.getFirst());return receiver;
            case "has?","has_value?","has_all?","has_any?":for(Type arg:args)require(span,arg,value);return BOOL;
            case "index_of","last_index_of":arity(span,args,1);require(span,args.getFirst(),value);return maybe(INT);
            case "count":arity(span,args,1);require(span,args.getFirst(),value);return INT;
            case "take","drop","rotate":arity(span,args,1);integer(span,args.getFirst());return receiver;
            case "slice:to":arity(span,args,2);for(Type arg:args)integer(span,arg);return receiver;
            case "compact":arity(span,args,0);return vec(value.name().equals("maybe")?value.args().getFirst():value);
            case "flatten":arity(span,args,0);while(value.name().equals("vec"))value=element(value);return vec(value);
            case "pop","shift","reverse","uniq","sort","shuffle","to_vec":arity(span,args,0);return receiver;
            case "sample","min","max":arity(span,args,0);return maybe(value);
            case "sum":arity(span,args,0);numeric(span,value);return value;
            case "product":if(args.isEmpty()){numeric(span,value);return value;}arity(span,args,1);if(!args.getFirst().name().equals("vec"))error(span,"product exige vetor");return vec(vec(ANY));
            case "average":arity(span,args,0);numeric(span,value);return maybe(new Type("double"));
            case "join":if(args.size()>1)throw new Diagnostic("M2201",span,"join exige zero ou um argumento");if(!args.isEmpty())require(span,args.getFirst(),STR);return STR;
            case "zip":arity(span,args,1);if(!args.getFirst().name().equals("vec"))error(span,"zip exige vetor");return vec(vec(ANY));
            case "unzip":arity(span,args,0);if(!value.name().equals("vec"))error(span,"unzip exige vetor de pares");return receiver;
            case "to_obj":arity(span,args,0);if(!value.name().equals("vec"))error(span,"to_obj exige vetor de pares");return attempt(obj(ANY));
            case "tally":arity(span,args,0);require(span,value,TAG);return obj(INT);
            case "combinations":arity(span,args,1);integer(span,args.getFirst());return attempt(vec(receiver));
            case "permutations":arity(span,args,0);return vec(receiver);
            case "binary_search":arity(span,args,1);require(span,args.getFirst(),value);return attempt(INT);default:return null;
        }
    }
    private static void numeric(Span span,Type type){if(!Set.of("byte","ubyte","short","ushort","int","uint","long","ulong","float","double","Number","Integer","Signed","Unsigned","Real").contains(type.name()))error(span,"operação exige elementos numéricos");}
    private static Type objectContract(Type receiver,Type value,String selector,List<Type> args,Span span){switch(selector){
        case "delete":if(args.isEmpty())throw new Diagnostic("M2201",span,"delete exige valor");for(Type arg:args)require(span,arg,value);return receiver;
        case "has?":if(args.size()==2){require(span,args.getFirst(),TAG);require(span,args.getLast(),value);return BOOL;}if(args.size()==1){require(span,args.getFirst(),receiver);return BOOL;}throw new Diagnostic("M2201",span,"has? exige chave e valor ou objeto");
        case "keys":arity(span,args,0);return vec(TAG);case "values":arity(span,args,0);return vec(value);case "pairs","to_vec":arity(span,args,0);return vec(vec(ANY));
        case "key_of":arity(span,args,1);return maybe(TAG);
        case "get","get_element","maybe_get","has_key?":arity(span,args,1);require(span,args.getFirst(),TAG);return selector.equals("has_key?")?BOOL:selector.equals("maybe_get")?maybe(value):attempt(value);
        case "has_value?":arity(span,args,1);return BOOL;
        case "fetch:default":arity(span,args,2);require(span,args.getFirst(),TAG);require(span,args.getLast(),value);return value;
        case "dig":arity(span,args,1);require(span,args.getFirst(),vec(TAG));return maybe(ANY);
        case "element_at":arity(span,args,1);integer(span,args.getFirst());return attempt(receiver);
        case "copy_with","merge","deep_merge","append":arity(span,args,1);require(span,args.getFirst(),receiver);return receiver;
        case "delete_key":arity(span,args,1);require(span,args.getFirst(),TAG);return receiver;
        case "except","filter_keys","slice":arity(span,args,1);require(span,args.getFirst(),vec(TAG));return receiver;
        case "invert":arity(span,args,0);require(span,value,TAG);return obj(TAG);default:return null;
    }}
}
