package mach.runtime;
import java.util.*;
import java.math.BigInteger;
import static mach.runtime.RuntimeSupport.*;

/** Immutable collection messages. RRB/CHAMP storage and complexity are deliberately deferred. */
public final class CollectionsLibrary {
    private CollectionsLibrary() {}
    private static List<Object> frozen(Collection<?> values){return PersistentCollections.frozen(values);}
    private static Map<Object,Object> frozen(Map<?,?> values){return (Map<Object,Object>) PersistentCollections.frozen(values);}
    private static Object n(int value){return integer(value,"int");}
    private static int index(Object value){BigInteger number=value instanceof LargeInteger large?large.value():BigInteger.valueOf(((NumberValue)value).integral());return number.compareTo(BigInteger.valueOf(Integer.MAX_VALUE))>0?Integer.MAX_VALUE:number.compareTo(BigInteger.valueOf(Integer.MIN_VALUE))<0?Integer.MIN_VALUE:number.intValue();}
    private static int position(Object value,int length){int index=index(value);return index<0?length+index:index;}
    private static int nthPosition(Object value,int length){int index=index(value);if(index<1)return -1;index-=1;return index>=length?-1:index;}
    private static int bounded(int value,int length){return Math.max(0,Math.min(length,value));}
    private static final class CallbackFailure extends RuntimeException{final Object value;CallbackFailure(Object value){this.value=value;}}
    private static Object invoke(Context c,Object fn,Object...args){Object result=send(c,fn,"result",args);if(result instanceof FailureValue)throw new CallbackFailure(result);return result;}
    private static boolean same(Context c,Object left,Object right){Object result=binary(c,"==",left,right);if(result instanceof FailureValue)throw new CallbackFailure(result);return truth(result);}
    private static int find(Context c,List<?> values,Object value,boolean backwards){for(int i=backwards?values.size()-1:0;backwards?i>=0:i<values.size();i+=backwards?-1:1)if(same(c,values.get(i),value))return i;return -1;}
    private static int compare(Context c,Object left,Object right){if(same(c,left,right))return 0;Object less=binary(c,"<",left,right);if(less instanceof FailureValue)throw new CallbackFailure(less);return truth(less)?-1:1;}
    private static boolean predicate(Context c,Object fn,Object...args){return truth(invoke(c,fn,args));}
    private static Object callback(Object[] args,Object block){Object candidate=block!=null?block:args.length==0?null:args[args.length-1];return CoreLibrary.callable(candidate)?candidate:null;}
    private static List<Object> distinct(Context c,List<?> values){var result=new ArrayList<Object>();for(Object value:values)if(find(c,result,value,false)<0)result.add(value);return result;}
    public static Object dispatch(Context c,Object receiver,String selector,Object[] args,Object block){
        if(selector.equals("\\\\"))selector="delete";
        try{
            if(receiver instanceof TypeValue type&&(type.name().equals("Vector")||type.name().startsWith("vec["))&&Set.of("new","create").contains(selector)){if(args.length==0)return frozen(List.of());if(args.length==1&&args[0] instanceof List<?> values)return frozen(values);return fail(c,"no_applicable","Vector new exige vetor");}
            if(receiver instanceof List<?> values)return vector(c,values,selector,args,block);
            if(receiver instanceof Map<?,?> values)return object(c,values,selector,args,block);
            return UNHANDLED;
        }catch(CallbackFailure failure){return failure.value;}
        catch(ClassCastException error){return fail(c,"no_applicable","tipos incompatíveis na mensagem "+selector);}
        catch(IndexOutOfBoundsException|ArithmeticException error){return fail(c,"index","índice fora da coleção");}
    }
    private static Object vector(Context c,List<?> values,String selector,Object[] args,Object block){
        switch(selector){
            case "head":if(args.length==0)return values.isEmpty()?null:values.getFirst();break;
            case "tail":if(args.length==0)return frozen(values.subList(Math.min(1,values.size()),values.size()));break;
            case "nth":if(args.length==1){
                if(args[0] instanceof List<?> indices){
                    Object current=values;
                    for(Object key:indices){
                        if(!(current instanceof List<?> nested))return fail(c,"no_applicable","caminho nth exige vetores aninhados");
                        int at=nthPosition(key,nested.size());
                        if(at<0||at>=nested.size())return fail(c,"index","índice fora do vetor");
                        current=nested.get(at);
                    }
                    return current;
                }
                int at=nthPosition(args[0],values.size());
                return at<0||at>=values.size()?fail(c,"index","índice fora do vetor"):values.get(at);
            }break;
            case "size","length":if(args.length==0)return n(values.size());break;
            case "copy","to_vec":if(args.length==0)return values;break;
            case "empty?":if(args.length==0)return values.isEmpty();break;
            case "reverse":if(args.length==0){var out=new ArrayList<>(values);Collections.reverse(out);return frozen(out);}break;
            case "*":if(args.length==1){int count=index(args[0]);if(count<0)return fail(c,"range","repetição negativa");var out=new ArrayList<Object>();for(int i=0;i<count;i++)out.addAll(values);return frozen(out);}break;
            case "+","concat":if(args.length==1&&args[0] instanceof List<?> other){var out=new ArrayList<Object>(values);out.addAll(other);return frozen(out);}break;
            case "append","unshift","<<":if(args.length==1){var out=new ArrayList<Object>(values);out.add(selector.equals("unshift")?0:out.size(),args[0]);return frozen(out);}break;
            case "push","merge":{var out=new ArrayList<Object>(values);for(Object value:args){if(selector.equals("merge")&&value instanceof List<?> nested)out.addAll(nested);else out.add(value);}return frozen(out);}
            case "insert:at":if(args.length==2){int at=position(args[1],values.size()+1);if(at<0||at>values.size())return fail(c,"index","índice fora do vetor");var out=new ArrayList<Object>(values);out.add(at,args[0]);return frozen(out);}break;
            case "put:at":if(args.length==2){int at=position(args[1],values.size());if(at<0||at>=values.size())return fail(c,"index","índice fora do vetor");var out=new ArrayList<Object>(values);out.set(at,args[0]);return frozen(out);}break;
            case "pop","shift":if(args.length==0){var out=new ArrayList<>(values);if(!out.isEmpty())out.remove(selector.equals("pop")?out.size()-1:0);return frozen(out);}break;
            case "delete":if(args.length>0){var out=new ArrayList<Object>();outer:for(Object value:values){for(Object target:args)if(same(c,value,target))continue outer;out.add(value);}return frozen(out);}break;
            case "delete_nth":if(args.length==1){int at=position(args[0],values.size());if(at<0||at>=values.size())return fail(c,"index","índice fora do vetor");var out=new ArrayList<>(values);out.remove(at);return frozen(out);}break;
            case "has?","has_value?":if(args.length==1)return find(c,values,args[0],false)>=0;break;
            case "has_all?","has_any?":{boolean all=selector.equals("has_all?");for(Object target:args)if((find(c,values,target,false)>=0)!=all)return !all;return all;}
            case "index_of","last_index_of":if(args.length==1){int at=find(c,values,args[0],selector.equals("last_index_of"));return at<0?null:n(at);}break;
            case "first","last":if(args.length==1){int count=Math.max(0,index(args[0]));return frozen(values.subList(selector.equals("first")?0:Math.max(0,values.size()-count),selector.equals("first")?Math.min(count,values.size()):values.size()));}break;
            case "take","drop":if(args.length==1){int count=bounded(index(args[0]),values.size());return frozen(values.subList(selector.equals("take")?0:count,selector.equals("take")?count:values.size()));}break;
            case "slice:to":if(args.length==2){int from=bounded(position(args[0],values.size()),values.size()),to=bounded(position(args[1],values.size()),values.size());return frozen(values.subList(from,Math.max(from,to)));}break;
            case "compact":if(args.length==0)return frozen(values.stream().filter(Objects::nonNull).toList());break;
            case "uniq":if(args.length==0)return frozen(distinct(c,values));break;
            case "flatten":if(args.length==0){var out=new ArrayList<Object>();flatten(values,out);return frozen(out);}break;
            case "rotate":if(args.length==1){var out=new ArrayList<>(values);if(!out.isEmpty())Collections.rotate(out,-Math.floorMod(index(args[0]),out.size()));return frozen(out);}break;
            case "shuffle":if(args.length==0){var out=new ArrayList<>(values);Collections.shuffle(out);return frozen(out);}break;
            case "sample":if(args.length==0)return values.isEmpty()?null:values.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(values.size()));break;
            case "join":if(args.length<=1)return String.join(args.length==0?"":(String)args[0],values.stream().map(RuntimeSupport::stringify).toList());break;
            case "zip":if(args.length==1&&args[0] instanceof List<?> other){var out=new ArrayList<Object>();for(int i=0;i<Math.min(values.size(),other.size());i++)out.add(frozen(Arrays.asList(values.get(i),other.get(i))));return frozen(out);}break;
            case "unzip":if(args.length==0){var left=new ArrayList<Object>();var right=new ArrayList<Object>();for(Object value:values){var pair=(List<?>)value;left.add(pair.get(0));right.add(pair.get(1));}return frozen(List.of(frozen(left),frozen(right)));}break;
            case "to_obj":if(args.length==0){var out=new LinkedHashMap<Object,Object>();for(Object value:values){var pair=(List<?>)value;if(!(pair.getFirst() instanceof Tag))return fail(c,"type","to_obj exige chaves tag");out.put(pair.getFirst(),pair.get(1));}return frozen(out);}break;
            case "union","intersect","difference":if(args.length==1&&args[0] instanceof List<?> other){var out=new ArrayList<Object>();for(Object value:values)if(selector.equals("union")||(find(c,other,value,false)>=0)==selector.equals("intersect"))out.add(value);if(selector.equals("union"))out.addAll(other);return frozen(distinct(c,out));}break;
            case "product":if(args.length==1&&args[0] instanceof List<?> other){var out=new ArrayList<Object>();for(Object left:values)for(Object right:other)out.add(frozen(Arrays.asList(left,right)));return frozen(out);}break;
            case "combinations":if(args.length==1){int k=index(args[0]);if(k<0)return fail(c,"invalid","tamanho da combinação negativo");var out=new ArrayList<Object>();combinations(values,k,0,new ArrayList<>(),out);return frozen(out);}break;
            case "permutations":if(args.length==0){var out=new ArrayList<Object>();permutations(values,new boolean[values.size()],new ArrayList<>(),out);return frozen(out);}break;
            case "tally":if(args.length==0){var out=new LinkedHashMap<Object,Object>();for(Object value:values){if(!(value instanceof Tag))return fail(c,"type","tally em obj exige elementos tag");out.put(value,n(out.containsKey(value)?index(out.get(value))+1:1));}return frozen(out);}break;
            case "binary_search":if(args.length==1){int low=0,high=values.size()-1;while(low<=high){int middle=(low+high)>>>1,order=compare(c,values.get(middle),args[0]);if(order==0)return n(middle);if(order<0)low=middle+1;else high=middle-1;}return fail(c,"not_found","elemento ausente");}break;
        }
        Object fn=callback(args,block);
        if(fn!=null&&Set.of("each","each_with_index","collect","map","select","filter","reject","flat_map","find","detect","find_index","all?","any?","none?","one?","count","partition","group_by","sum_by","min_by","max_by","sort_by","sort_with","take_while","drop_while","chunk_while").contains(selector)){
            var out=new ArrayList<Object>();var no=new ArrayList<Object>();var groups=new LinkedHashMap<Object,Object>();int count=0;Object extreme=null,key=null;boolean boundary=false,haveExtreme=false;
            if(selector.equals("sort_by")||selector.equals("sort_with")){
                if(selector.equals("sort_with")){out.addAll(values);out.sort((x,y)->{Object result=invoke(c,fn,x,y);return result instanceof Boolean?truth(result)?-1:truth(invoke(c,fn,y,x))?1:0:index(result);});return frozen(out);}
                record Decorated(Object value,Object key){}var decorated=new ArrayList<Decorated>();for(Object value:values)decorated.add(new Decorated(value,invoke(c,fn,value)));decorated.sort((x,y)->compare(c,x.key(),y.key()));for(var value:decorated)out.add(value.value());return frozen(out);
            }
            if(selector.equals("chunk_while")){var current=new ArrayList<Object>();Object previous=null;for(Object value:values){if(!current.isEmpty()&&!predicate(c,fn,previous,value)){out.add(frozen(current));current.clear();}current.add(value);previous=value;}if(!current.isEmpty())out.add(frozen(current));return frozen(out);}
            for(int i=0;i<values.size();i++){Object value=values.get(i),result=invoke(c,fn,selector.equals("each_with_index")?new Object[]{value,n(i)}:new Object[]{value});switch(selector){
                case "each","each_with_index":break;case "collect","map":out.add(result);break;
                case "flat_map":if(result instanceof List<?> nested)out.addAll(nested);else out.add(result);break;
                case "select","filter","reject":if(truth(result)!=selector.equals("reject"))out.add(value);break;
                case "find","detect":if(truth(result))return value;break;case "find_index":if(truth(result))return n(i);break;
                case "all?":if(!truth(result))return false;break;case "any?":if(truth(result))return true;break;case "none?":if(truth(result))return false;break;
                case "one?","count":if(truth(result))count++;break;case "partition":(truth(result)?out:no).add(value);break;
                case "group_by":if(!(result instanceof Tag))return fail(c,"type","group_by exige chaves tag");var group=(List<?>)groups.get(result);var updated=new ArrayList<Object>(group==null?List.of():group);updated.add(value);groups.put(result,frozen(updated));break;
                case "sum_by":extreme=extreme==null?result:binary(c,"+",extreme,result);break;
                case "min_by","max_by":if(!haveExtreme||(compare(c,result,key)<0)==selector.equals("min_by")){extreme=value;key=result;haveExtreme=true;}break;
                case "take_while":if(!truth(result))return frozen(out);out.add(value);break;case "drop_while":if(!truth(result))boundary=true;if(boundary)out.add(value);break;default:break;
            }}
            return switch(selector){case "each","each_with_index"->null;case "all?","none?"->true;case "any?"->false;case "one?"->count==1;case "count"->n(count);case "find","detect","find_index"->null;case "partition"->frozen(List.of(frozen(out),frozen(no)));case "group_by"->frozen(groups);case "sum_by"->extreme==null?numericIdentity(callbackOutput(fn),false):extreme;case "min_by","max_by"->extreme;default->frozen(out);};
        }
        if(Set.of("reduce:with","fold:with","inject:with").contains(selector)&&args.length>=1&&fn!=null){Object accumulator=args[0];for(Object value:values)accumulator=invoke(c,fn,accumulator,value);return accumulator;}
        if(Set.of("each_slice","each_cons").contains(selector)&&args.length>=1&&fn!=null){int width=index(args[0]);if(width<=0)return fail(c,"invalid","tamanho da janela deve ser positivo");for(int i=0;i<values.size()&&(selector.equals("each_slice")||i+width<=values.size());i+=selector.equals("each_slice")?width:1)invoke(c,fn,frozen(values.subList(i,Math.min(i+width,values.size()))));return null;}
        if(args.length==0&&block==null){
            if(selector.equals("count"))return n(values.size());if(selector.equals("any?"))return !values.isEmpty();if(selector.equals("none?"))return values.isEmpty();
            if(selector.equals("sort")){var out=new ArrayList<Object>(values);out.sort((x,y)->compare(c,x,y));return frozen(out);}
            if(selector.equals("min")||selector.equals("max")){Object best=null;for(Object value:values)if(best==null||(compare(c,value,best)<0)==selector.equals("min"))best=value;return best;}
            if(Set.of("sum","product","average").contains(selector)){Object total=numericIdentity(values instanceof TypedVector typed?typed.descriptor().substring(4,typed.descriptor().length()-1):"int",selector.equals("product"));boolean first=true;for(Object value:values){total=first?value:binary(c,selector.equals("product")?"*":"+",total,value);first=false;if(total instanceof FailureValue)return total;}return selector.equals("average")?values.isEmpty()?null:binary(c,"/",total,n(values.size())):total;}
        }
        if(selector.equals("count")&&args.length==1){int count=0;for(Object value:values)if(same(c,value,args[0]))count++;return n(count);}return UNHANDLED;
    }
    private static Object numericIdentity(String type,boolean product){while(type.startsWith("try[")&&type.endsWith("]"))type=type.substring(4,type.length()-1);if(type.equals("double")||type.equals("float"))return floating(product?1:0,type);if(!Set.of("byte","ubyte","short","ushort","int","uint","long","ulong").contains(type))type="int";return integer(product?1:0,type);}
    private static String callbackOutput(Object fn){String signature=typeName(fn);int depth=0,start=signature.indexOf('[')+1;for(int i=start;i<signature.length()-1;i++){char ch=signature.charAt(i);if(ch=='[')depth++;else if(ch==']')depth--;else if(ch==','&&depth==0)start=i+1;}return signature.substring(start,signature.length()-1);}
    private static void flatten(List<?> values,List<Object> output){for(Object value:values)if(value instanceof List<?> nested)flatten(nested,output);else output.add(value);}
    private static void combinations(List<?> values,int count,int from,List<Object> selected,List<Object> output){if(count==0){output.add(frozen(selected));return;}for(int i=from;i<=values.size()-count;i++){selected.add(values.get(i));combinations(values,count-1,i+1,selected,output);selected.removeLast();}}
    private static void permutations(List<?> values,boolean[] used,List<Object> selected,List<Object> output){if(selected.size()==values.size()){output.add(frozen(selected));return;}for(int i=0;i<values.size();i++)if(!used[i]){used[i]=true;selected.add(values.get(i));permutations(values,used,selected,output);selected.removeLast();used[i]=false;}}
    private static Object object(Context c,Map<?,?> values,String selector,Object[] args,Object block){
        switch(selector){
            case "head":if(args.length==0)return values.isEmpty()?null:values.values().iterator().next();break;
            case "tail":if(args.length==0){var out=new LinkedHashMap<Object,Object>(values);if(!out.isEmpty())out.remove(out.keySet().iterator().next());return frozen(out);}break;
            case "size","length":if(args.length==0)return n(values.size());break;case "empty?":if(args.length==0)return values.isEmpty();break;
            case "keys":if(args.length==0)return frozen(values.keySet());break;case "values":if(args.length==0)return frozen(values.values());break;
            case "pairs","to_vec":if(args.length==0){var out=new ArrayList<Object>();for(var entry:values.entrySet())out.add(frozen(Arrays.asList(entry.getKey(),entry.getValue())));return frozen(out);}break;
            case "key_of":if(args.length==1){for(var entry:values.entrySet())if(same(c,entry.getValue(),args[0]))return entry.getKey();return null;}break;
            case "fetch:default":if(args.length==2)return values.containsKey(args[0])?values.get(args[0]):args[1];break;case "dig":if(args.length==1)return get(c,values,args[0]);break;
            case "element_at":if(args.length==1){int at=position(args[0],values.size());if(at<0||at>=values.size())return fail(c,"index","índice fora do objeto");var entry=new ArrayList<>(values.entrySet()).get(at);var out=new LinkedHashMap<Object,Object>();out.put(entry.getKey(),entry.getValue());return frozen(out);}break;
            case "copy":if(args.length==0)return values;break;
            case "copy_with","merge","append","|","<<":if(args.length==1&&args[0] instanceof Map<?,?> updates){var out=new LinkedHashMap<Object,Object>(values);out.putAll(updates);return frozen(out);}break;
            case "deep_merge":if(args.length==1&&args[0] instanceof Map<?,?> updates)return deepMerge(values,updates);break;
            case "delete_key":if(args.length==1){var out=new LinkedHashMap<Object,Object>(values);out.remove(args[0]);return frozen(out);}break;
            case "delete":if(args.length>0){var out=new LinkedHashMap<Object,Object>();outer:for(var entry:values.entrySet()){for(Object target:args)if(same(c,entry.getValue(),target))continue outer;out.put(entry.getKey(),entry.getValue());}return frozen(out);}break;
            case "has?":if(args.length==2)return values.containsKey(args[0])&&same(c,values.get(args[0]),args[1]);if(args.length==1&&args[0] instanceof Map<?,?> expected){for(var entry:expected.entrySet())if(!values.containsKey(entry.getKey())||!same(c,values.get(entry.getKey()),entry.getValue()))return false;return true;}break;
            case "except","filter_keys","slice":if(args.length==1&&args[0] instanceof List<?> keys){var out=new LinkedHashMap<Object,Object>();for(var entry:values.entrySet())if(keys.contains(entry.getKey())!=selector.equals("except"))out.put(entry.getKey(),entry.getValue());return frozen(out);}break;
            case "invert":if(args.length==0){var out=new LinkedHashMap<Object,Object>();for(var entry:values.entrySet()){if(!(entry.getValue() instanceof Tag))return fail(c,"type","invert em obj exige valores tag");out.put(entry.getValue(),entry.getKey());}return frozen(out);}break;
        }
        Object fn=callback(args,block);
        if(selector.equals("update:with")&&args.length>=1&&fn!=null){if(!values.containsKey(args[0]))return fail(c,"key","chave ausente");var out=new LinkedHashMap<Object,Object>(values);out.put(args[0],invoke(c,fn,values.get(args[0])));return frozen(out);}
        if(fn!=null&&Set.of("each","collect","map","select","filter","reject","map_values","map_keys","all?","any?","none?","one?","count","find","detect","find_index","partition","group_by","min_by","max_by","sort_by").contains(selector)){
            var out=new LinkedHashMap<Object,Object>();var no=new LinkedHashMap<Object,Object>();var collected=new ArrayList<Object>();int count=0,at=0;Object extreme=null,key=null;
            for(var entry:values.entrySet()){Object result=invoke(c,fn,selector.equals("map_values")?new Object[]{entry.getValue()}:selector.equals("map_keys")?new Object[]{entry.getKey()}:new Object[]{entry.getKey(),entry.getValue()});switch(selector){
                case "each":break;case "collect","map":collected.add(result);break;case "select","filter","reject":if(truth(result)!=selector.equals("reject"))out.put(entry.getKey(),entry.getValue());break;
                case "map_values":out.put(entry.getKey(),result);break;case "map_keys":if(!(result instanceof Tag))return fail(c,"type","map_keys exige tag");out.put(result,entry.getValue());break;
                case "all?":if(!truth(result))return false;break;case "any?":if(truth(result))return true;break;case "none?":if(truth(result))return false;break;case "count","one?":if(truth(result))count++;break;
                case "find","detect":if(truth(result))return frozen(Collections.singletonMap(entry.getKey(),entry.getValue()));break;case "find_index":if(truth(result))return n(at);break;
                case "partition":(truth(result)?out:no).put(entry.getKey(),entry.getValue());break;
                case "group_by":if(!(result instanceof Tag))return fail(c,"type","group_by exige tag");var group=(Map<?,?>)out.get(result);var updated=new LinkedHashMap<Object,Object>(group==null?Map.of():group);updated.put(entry.getKey(),entry.getValue());out.put(result,frozen(updated));break;
                case "min_by","max_by":if(extreme==null||(compare(c,result,key)<0)==selector.equals("min_by")){extreme=frozen(Collections.singletonMap(entry.getKey(),entry.getValue()));key=result;}break;
                case "sort_by":collected.add(frozen(Arrays.asList(result,frozen(Arrays.asList(entry.getKey(),entry.getValue())))));break;default:break;
            }at++;}
            return switch(selector){case "each"->null;case "collect","map"->frozen(collected);case "all?","none?"->true;case "any?"->false;case "count"->n(count);case "one?"->count==1;case "find","detect","find_index"->null;case "min_by","max_by"->extreme;case "partition"->frozen(List.of(frozen(out),frozen(no)));case "sort_by"->{collected.sort((x,y)->compare(c,((List<?>)x).getFirst(),((List<?>)y).getFirst()));yield frozen(collected.stream().map(x->((List<?>)x).get(1)).toList());}default->frozen(out);};
        }
        if(Set.of("reduce:with","fold:with","inject:with").contains(selector)&&args.length>=1&&fn!=null){Object accumulator=args[0];for(var entry:values.entrySet())accumulator=invoke(c,fn,accumulator,entry.getKey(),entry.getValue());return accumulator;}
        if(args.length==0&&block==null){if(selector.equals("count"))return n(values.size());if(selector.equals("any?"))return !values.isEmpty();if(selector.equals("none?"))return values.isEmpty();}return UNHANDLED;
    }
    private static Map<Object,Object> deepMerge(Map<?,?> original,Map<?,?> updates){var out=new LinkedHashMap<Object,Object>(original);for(var entry:updates.entrySet())out.put(entry.getKey(),out.get(entry.getKey()) instanceof Map<?,?> old&&entry.getValue() instanceof Map<?,?> next?deepMerge(old,next):entry.getValue());return frozen(out);}
}
