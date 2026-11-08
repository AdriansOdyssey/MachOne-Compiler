package mach.runtime;

import java.util.*;
import static mach.runtime.RuntimeSupport.*;

/** Immutable extra collections; this storage backend makes no HAMT/tree complexity claims. */
public final class ExtraCollectionsLibrary {
    private ExtraCollectionsLibrary() {}
    public static final Set<String> TYPES=Set.of("Array","MutableArray","Set","SortedSet","Dictionary","SortedMap","Deque","Bag","PriorityQueue","Queue","Stack","Pair","Range");
    public interface Value { String descriptor(); List<Object> values(); }
    private record SequenceValue(String descriptor,List<Object> values) implements Value {
        private SequenceValue { values=freeze(values); }
        @Override public String toString(){return base(descriptor)+RuntimeSupport.stringify(values);}
    }
    private static final class ArrayValue implements Value {
        private final String descriptor; private final ArrayList<Object> values;
        private ArrayValue(String descriptor,Collection<?> values){this.descriptor=descriptor;this.values=new ArrayList<>(values);} 
        public String descriptor(){return descriptor;} 
        public List<Object> values(){return Collections.unmodifiableList(new ArrayList<>(values));}
        @Override public String toString(){return RuntimeSupport.stringify(values());}
    }
    private static final class MutableArrayValue implements Value {
        private final String descriptor; private final ArrayList<Object> values;
        private MutableArrayValue(String descriptor,Collection<?> values){this.descriptor=descriptor;this.values=new ArrayList<>(values);} 
        public String descriptor(){return descriptor;} 
        public List<Object> values(){return Collections.unmodifiableList(new ArrayList<>(values));}
        @Override public String toString(){return RuntimeSupport.stringify(values());}
    }
    private record Entry(Object key,Object value) {}
    private record MappingValue(String descriptor,List<Entry> entries) implements Value {
        private MappingValue { entries=List.copyOf(entries); }
        public List<Object> values(){return freeze(entries.stream().map(e->freeze(Arrays.asList(e.key(),e.value()))).toList());}
        @Override public String toString(){return base(descriptor)+RuntimeSupport.stringify(values());}
    }
    private record Priority(Object value,Object priority) {}
    private record PriorityValue(String descriptor,List<Priority> entries) implements Value {
        private PriorityValue { entries=List.copyOf(entries); }
        public List<Object> values(){return freeze(entries.stream().map(Priority::value).toList());}
        @Override public String toString(){return "PriorityQueue"+RuntimeSupport.stringify(values());}
    }
    private static List<Object> freeze(Collection<?> values){return Collections.unmodifiableList(new ArrayList<>(values));}
    private static String base(String descriptor){int open=descriptor.indexOf('[');return open<0?descriptor:descriptor.substring(0,open);}
    private static List<String> parameters(String descriptor){
        int open=descriptor.indexOf('[');if(open<0)return List.of();var values=new ArrayList<String>();int depth=0,start=open+1;
        for(int i=start;i<descriptor.length()-1;i++){char c=descriptor.charAt(i);if(c=='[')depth++;else if(c==']')depth--;else if(c==','&&depth==0){values.add(descriptor.substring(start,i));start=i+1;}}
        values.add(descriptor.substring(start,descriptor.length()-1));return values;
    }
    private static String inferred(Collection<?> values){String result=null;for(Object value:values){String type=typeName(value);if(result==null)result=type;else if(!result.equals(type))return "any";}return result==null?"any":result;}
    private static boolean same(Context context,Object a,Object b){Object result=binary(context,"==",a,b);if(result instanceof FailureValue)throw new CallbackFailure(result);return truth(result);}
    private static int index(Context context,List<?> values,Object needle){for(int i=0;i<values.size();i++)if(same(context,values.get(i),needle))return i;return -1;}
    private static int entryIndex(Context context,List<Entry> values,Object needle){for(int i=0;i<values.size();i++)if(same(context,values.get(i).key(),needle))return i;return -1;}
    private static int position(Object value,int length){
        int index = value instanceof NumberValue n ? (int) n.integral() : value instanceof LargeInteger large ? large.value().intValue() : value instanceof CharacterValue c ? c.codepoint() : 0;
        return index < 0 ? length + index : index;
    }
    private static int compare(Context context,Object a,Object b){
        if(same(context,a,b))return 0;
        if(number(a)&&number(b)){
            boolean floating=a instanceof NumberValue x&&x.floating()||b instanceof NumberValue y&&y.floating();
            return floating?Double.compare(real(a),real(b)):integral(a).compareTo(integral(b));
        }
        if(a instanceof String x&&b instanceof String y)return Integer.signum(x.compareTo(y));
        if(a instanceof CharacterValue x&&b instanceof CharacterValue y)return Integer.compare(x.codepoint(),y.codepoint());
        Object result=binary(context,"<",a,b);if(result instanceof FailureValue)throw new CallbackFailure(result);return truth(result)?-1:1;
    }
    private static final class CallbackFailure extends RuntimeException {final Object value;CallbackFailure(Object value){this.value=value;}}
    private static Object invoke(Context context,Object callback,Object... arguments){Object value=send(context,callback,"result",arguments);if(value instanceof FailureValue)throw new CallbackFailure(value);return value;}
    private static List<Object> unique(Context context,Collection<?> input){var output=new ArrayList<Object>();for(Object value:input)if(index(context,output,value)<0)output.add(value);return output;}
    private static void check(Context context,Object value,String descriptor){if(!satisfies(context,value,descriptor,false))throw new CallbackFailure(fail(context,"type","tipo "+typeName(value)+" incompatível com "+descriptor));}
    private static SequenceValue sequence(Context context,String descriptor,Collection<?> values){
        String kind=base(descriptor);var result=kind.equals("Set")||kind.equals("SortedSet")?unique(context,values):new ArrayList<Object>(values);
        if(kind.equals("SortedSet"))result.sort((a,b)->compare(context,a,b));return new SequenceValue(descriptor,result);
    }
    private static MappingValue mapping(Context context,String descriptor,Collection<Entry> input){
        var output=new ArrayList<Entry>();for(Entry entry:input){int old=entryIndex(context,output,entry.key());if(old<0)output.add(entry);else output.set(old,entry);}
        if(base(descriptor).equals("SortedMap"))output.sort((a,b)->compare(context,a.key(),b.key()));return new MappingValue(descriptor,output);
    }
    private static final Set<String> SEQUENCE_MESSAGES=Set.of("add","delete","has?","union","intersect","difference","subset?","length","size","empty?","copy","to_vec","push_front","push_back","pop_front","pop_back","front","back","count","first","last","range:to","range");
    private static final Set<String> MAPPING_MESSAGES=Set.of("get","maybe_get","put","delete","has_key?","keys","values","pairs","merge","length","size","empty?","copy","to_vec","first","last","range:to","range","each","collect","map","select","filter","reject","all?","any?","none?","count","find");
    private static final Set<String> ENUM_MESSAGES=Set.of("count","each","each_with_index","collect","map","select","filter","reject","reduce:with","fold:with","inject:with","sum","product","min","max","min_by","max_by","sort","sort_by","sort_with","reverse","uniq","flatten","flat_map","take","drop","take_while","drop_while","find","find_index","all?","any?","none?","one?","group_by","partition","tally","each_slice","each_cons","chunk_while","rotate","shuffle","combinations","permutations","join","sum_by","average","binary_search");
    public static boolean understands(Object receiver,String selector){
        if(receiver instanceof List<?> &&selector.equals("to_set"))return true;
        if(receiver instanceof TypeValue type&&TYPES.contains(base(type.name())))return Set.of("new","create","new:","create:","of","of:").contains(selector);
        if(receiver instanceof MappingValue map)return MAPPING_MESSAGES.contains(selector)&&(!Set.of("first","last","range:to","range").contains(selector)||base(map.descriptor()).equals("SortedMap"));
        if(receiver instanceof PriorityValue)return Set.of("insert:with","pop_min","peek","length","size","empty?","to_vec","copy").contains(selector);
        if(receiver instanceof ArrayValue || receiver instanceof MutableArrayValue)return SEQUENCE_MESSAGES.contains(selector)||ENUM_MESSAGES.contains(selector);
        return receiver instanceof SequenceValue sequence&&(SEQUENCE_MESSAGES.contains(selector)||ENUM_MESSAGES.contains(selector))&&(!Set.of("range:to","range").contains(selector)||base(sequence.descriptor()).equals("SortedSet"));
    }
    public static Object dispatch(Context context,Object receiver,String selector,Object[] args,Object block){
        try {
            if(receiver instanceof List<?> values&&selector.equals("to_set")&&args.length==0){String descriptor=typeName(values);List<String> arguments=parameters(descriptor);String element=arguments.isEmpty()?inferred(values):arguments.getFirst();return sequence(context,"Set["+element+"]",values);}
            if(receiver instanceof TypeValue type&&TYPES.contains(base(type.name()))&&Set.of("new","create","new:","create:","of","of:").contains(selector))return construct(context,type,args);
            if(receiver instanceof MappingValue map)return mappingDispatch(context,map,selector,args,block);
            if(receiver instanceof PriorityValue queue)return priorityDispatch(context,queue,selector,args);
            if(receiver instanceof ArrayValue array)return arrayDispatch(context,array,selector,args,block);
            if(receiver instanceof MutableArrayValue array)return mutableArrayDispatch(context,array,selector,args,block);
            if(receiver instanceof SequenceValue values)return sequenceDispatch(context,values,selector,args,block);
            return UNHANDLED;
        } catch(CallbackFailure failure){return failure.value;}
    }
    /** The shared iteration protocol supplies one sequence value or two mapping components. */
    public static Iterator<Object[]> iterator(Value value,int arity){
        if(value instanceof MappingValue mapping){
            if(arity!=1&&arity!=2)throw new MachFailure("arity","mapa exige um par ou chave e valor");
            Iterator<Entry> entries=mapping.entries().iterator();
            return new Iterator<>(){
                public boolean hasNext(){return entries.hasNext();}
                public Object[] next(){Entry entry=entries.next();return arity==2?new Object[]{entry.key(),entry.value()}:new Object[]{freeze(Arrays.asList(entry.key(),entry.value()))};}
            };
        }
        if(arity!=1)throw new MachFailure("arity","coleção exige uma variável de iteração");
        Iterator<Object> values=value.values().iterator();
        return new Iterator<>(){public boolean hasNext(){return values.hasNext();}public Object[] next(){return new Object[]{values.next()};}};
    }
    private static Object construct(Context context,TypeValue type,Object[] args){
        String kind=base(type.name());List<String> declared=parameters(type.name());
        if(args.length>1)return fail(context,"arity",kind+" exige zero ou um argumento");
        if(kind.equals("PriorityQueue")){if(args.length!=0)return fail(context,"arity","PriorityQueue não recebe argumentos");return new PriorityValue(kind+"["+(declared.isEmpty()?"any":declared.getFirst())+"]",List.of());}
        if(kind.equals("Array")||kind.equals("MutableArray")){
            List<?> values=args.length==0?List.of():args[0] instanceof List<?> input?input:args[0] instanceof Collection<?> collection?List.copyOf(collection):List.of(args[0]);
            String element=declared.isEmpty()?inferred(values):declared.getFirst();
            for(Object value:values)check(context,value,element);
            String descriptor=kind+"["+element+"]";
            return kind.equals("MutableArray")?new MutableArrayValue(descriptor,values):new ArrayValue(descriptor,values);
        }
        if(kind.equals("Dictionary")||kind.equals("SortedMap")){
            var entries=new ArrayList<Entry>();
            if(args.length==1){
                if(args[0] instanceof Map<?,?> values)for(var entry:values.entrySet())entries.add(new Entry(entry.getKey(),entry.getValue()));
                else if(args[0] instanceof List<?> values){for(Object value:values){if(!(value instanceof List<?> pair)||pair.size()!=2)return fail(context,"type",kind+" new exige pares [chave, valor]");entries.add(new Entry(pair.getFirst(),pair.getLast()));}}
                else return fail(context,"type",kind+" new exige vetor de pares ou obj");
            }
            String key=declared.isEmpty()?inferred(entries.stream().map(Entry::key).toList()):declared.getFirst();String value=declared.isEmpty()?inferred(entries.stream().map(Entry::value).toList()):declared.getLast();
            for(Entry entry:entries){check(context,entry.key(),key);check(context,entry.value(),value);}
            return mapping(context,kind+"["+key+","+value+"]",entries);
        }
        List<?> values=args.length==0?List.of():args[0] instanceof List<?> input?input:null;
        if(values==null)return fail(context,"type",kind+" new exige vetor");String element=declared.isEmpty()?inferred(values):declared.getFirst();for(Object value:values)check(context,value,element);
        return sequence(context,kind+"["+element+"]",values);
    }
    private static Object arrayDispatch(Context context,ArrayValue receiver,String selector,Object[] args,Object block){
        Object result=sequenceDispatch(context,new SequenceValue(receiver.descriptor(),receiver.values()),selector,args,block);
        if(result instanceof SequenceValue seq && base(seq.descriptor()).equals(base(receiver.descriptor())))return new ArrayValue(receiver.descriptor(),seq.values());
        return result;
    }
    private static Object mutableArrayDispatch(Context context,MutableArrayValue receiver,String selector,Object[] args,Object block){
        var values=receiver.values();
        switch(selector){
            case "copy":if(args.length==0)return receiver;break;
            case "length","size":if(args.length==0)return integer(values.size(),"int");break;
            case "empty?":if(args.length==0)return values.isEmpty();break;
            case "to_vec":if(args.length==0){String element=parameters(receiver.descriptor()).isEmpty()?"any":parameters(receiver.descriptor()).getFirst();return typedVector("vec["+element+"]",values);}break;
            case "element_at":if(args.length==1){int index=position(args[0],values.size());if(index<0||index>=values.size())return fail(context,"index","índice fora do vetor");return values.get(index);}break;
            case "put:at":if(args.length==2){int index=position(args[1],values.size());if(index<0||index>=values.size())return fail(context,"index","índice fora do vetor");receiver.values.set(index,args[0]);return receiver;}break;
            case "insert:at":if(args.length==2){int index=position(args[1],values.size()+1);if(index<0||index>values.size())return fail(context,"index","índice fora do vetor");receiver.values.add(index,args[0]);return receiver;}break;
            case "append","push_back","push_front":if(args.length==1){if(selector.equals("push_front"))receiver.values.add(0,args[0]);else receiver.values.add(args[0]);return receiver;}break;
            case "delete_nth":if(args.length==1){int index=position(args[0],values.size());if(index<0||index>=values.size())return fail(context,"index","índice fora do vetor");receiver.values.remove(index);return receiver;}break;
            case "delete":if(args.length>=1){ArrayList<Object> next=new ArrayList<>();for(Object value:values)if(!Set.of(args).contains(value))next.add(value);receiver.values.clear();receiver.values.addAll(next);return receiver;}break;
            case "sort":if(args.length==0){receiver.values.sort((a,b)->compare(context,a,b));return receiver;}break;
            case "reverse":if(args.length==0){Collections.reverse(receiver.values);return receiver;}break;
        }
        Object result=sequenceDispatch(context,new SequenceValue(receiver.descriptor(),receiver.values()),selector,args,block);
        if(result instanceof SequenceValue seq && base(seq.descriptor()).equals(base(receiver.descriptor())))return new MutableArrayValue(receiver.descriptor(),seq.values());
        return result;
    }
    private static Object sequenceDispatch(Context context,SequenceValue receiver,String selector,Object[] args,Object block){
        var values=receiver.values();String kind=base(receiver.descriptor()),element=parameters(receiver.descriptor()).getFirst();
        switch(selector){
            case "copy":if(args.length==0)return receiver;break;
            case "length","size":if(args.length==0)return integer(values.size(),"int");break;
            case "empty?":if(args.length==0)return values.isEmpty();break;
            case "to_vec":if(args.length==0)return typedVector("vec["+element+"]",values);break;
            case "has?":if(args.length==1)return index(context,values,args[0])>=0;break;
            case "add","push_front","push_back":if(args.length==1){check(context,args[0],element);var output=new ArrayList<>(values);output.add(selector.equals("push_front")?0:output.size(),args[0]);return sequence(context,receiver.descriptor(),output);}break;
            case "delete":if(args.length==1){var output=new ArrayList<Object>();for(Object value:values)if(!same(context,value,args[0]))output.add(value);return sequence(context,receiver.descriptor(),output);}break;
            case "front","first","back","last":if(args.length==0)return values.isEmpty()?null:selector.equals("front")||selector.equals("first")?values.getFirst():values.getLast();break;
            case "pop_front","pop_back":if(args.length==0){var output=new ArrayList<>(values);if(!output.isEmpty())output.remove(selector.equals("pop_front")?0:output.size()-1);return sequence(context,receiver.descriptor(),output);}break;
            case "count":if(args.length==1&&!CoreLibrary.callable(args[0])){int count=0;for(Object value:values)if(same(context,value,args[0]))count++;return integer(count,"int");}if(args.length==0&&block==null)return integer(values.size(),"int");break;
            case "union","intersect","difference","subset?":if(args.length==1&&args[0] instanceof SequenceValue other){for(Object value:other.values())check(context,value,element);var output=new ArrayList<Object>();if(selector.equals("subset?")){for(Object value:values)if(index(context,other.values(),value)<0)return false;return true;}for(Object value:values)if(selector.equals("union")||(index(context,other.values(),value)>=0)==selector.equals("intersect"))output.add(value);if(selector.equals("union"))output.addAll(other.values());return sequence(context,receiver.descriptor(),output);}break;
            case "range":if(kind.equals("SortedSet")&&args.length==1&&args[0] instanceof List<?> selected){var output=new ArrayList<Object>();for(Object value:values)if(index(context,selected,value)>=0)output.add(value);return sequence(context,receiver.descriptor(),output);}break;
            case "range:to":if(kind.equals("SortedSet")&&args.length==2){var output=new ArrayList<Object>();for(Object value:values)if(compare(context,value,args[0])>=0&&compare(context,value,args[1])<0)output.add(value);return sequence(context,receiver.descriptor(),output);}break;
        }
        if(ENUM_MESSAGES.contains(selector)){
            Object result=CollectionsLibrary.dispatch(context,values,selector,args,block);
            if(result instanceof FailureValue)return result;
            if(result instanceof List<?> selected&&Set.of("select","filter","reject","reverse","sort","sort_by","sort_with","take","drop","take_while","drop_while","rotate","shuffle","uniq").contains(selector))return sequence(context,receiver.descriptor(),selected);
            if(result instanceof List<?> output&&Set.of("collect","map","flat_map").contains(selector)){
                Object callback=block!=null?block:args.length==0?null:args[args.length-1];
                if(CoreLibrary.callable(callback)){List<String> signature=parameters(typeName(callback));if(!signature.isEmpty()){String out=signature.getLast();if(out.startsWith("try["))out=parameters(out).getFirst();if(selector.equals("flat_map")&&out.startsWith("vec["))out=parameters(out).getFirst();return typedVector("vec["+out+"]",output);}}
            }
            return result;
        }
        return UNHANDLED;
    }
    private static Object transformedVector(Object callback,List<?> values){List<String> signature=parameters(typeName(callback));String output=signature.isEmpty()?"any":signature.getLast();if(output.startsWith("try["))output=parameters(output).getFirst();return typedVector("vec["+output+"]",values);}
    private static Object mappingDispatch(Context context,MappingValue receiver,String selector,Object[] args,Object block){
        var entries=receiver.entries();List<String> types=parameters(receiver.descriptor());
        switch(selector){
            case "length","size","count":if(args.length==0&&block==null)return integer(entries.size(),"int");break;
            case "empty?":if(args.length==0)return entries.isEmpty();break;
            case "copy":if(args.length==0)return receiver;break;
            case "to_vec","pairs":if(args.length==0)return receiver.values();break;
            case "keys":if(args.length==0)return typedVector("vec["+types.getFirst()+"]",entries.stream().map(Entry::key).toList());break;
            case "values":if(args.length==0)return typedVector("vec["+types.getLast()+"]",entries.stream().map(Entry::value).toList());break;
            case "get","maybe_get","has_key?":if(args.length==1){int index=entryIndex(context,entries,args[0]);if(selector.equals("has_key?"))return index>=0;return index<0?selector.equals("maybe_get")?null:fail(context,"not_found","chave ausente: "+stringify(args[0])):entries.get(index).value();}break;
            case "put":if(args.length==2){check(context,args[0],types.getFirst());check(context,args[1],types.getLast());var output=new ArrayList<>(entries);output.add(new Entry(args[0],args[1]));return mapping(context,receiver.descriptor(),output);}break;
            case "delete":if(args.length==1){var output=new ArrayList<Entry>();for(Entry entry:entries)if(!same(context,entry.key(),args[0]))output.add(entry);return mapping(context,receiver.descriptor(),output);}break;
            case "merge":if(args.length==1&&args[0] instanceof MappingValue other){for(Entry entry:other.entries()){check(context,entry.key(),types.getFirst());check(context,entry.value(),types.getLast());}var output=new ArrayList<>(entries);output.addAll(other.entries());return mapping(context,receiver.descriptor(),output);}break;
            case "first","last":if(args.length==0&&base(receiver.descriptor()).equals("SortedMap")){if(entries.isEmpty())return null;Entry selected=selector.equals("first")?entries.getFirst():entries.getLast();return freeze(Arrays.asList(selected.key(),selected.value()));}break;
            case "range":if(args.length==1&&base(receiver.descriptor()).equals("SortedMap")&&args[0] instanceof List<?> selected){var output=new ArrayList<Entry>();for(Entry entry:entries)if(index(context,selected,entry.key())>=0)output.add(entry);return mapping(context,receiver.descriptor(),output);}break;
            case "range:to":if(args.length==2&&base(receiver.descriptor()).equals("SortedMap")){var output=new ArrayList<Entry>();for(Entry entry:entries)if(compare(context,entry.key(),args[0])>=0&&compare(context,entry.key(),args[1])<0)output.add(entry);return mapping(context,receiver.descriptor(),output);}break;
        }
        Object callback=block!=null?block:args.length==1&&CoreLibrary.callable(args[0])?args[0]:null;
        if(callback!=null&&Set.of("each","collect","map","select","filter","reject","all?","any?","none?","count","find").contains(selector)){
            var selected=new ArrayList<Entry>();var transformed=new ArrayList<Object>();int count=0;
            for(Entry entry:entries){Object value=invoke(context,callback,entry.key(),entry.value());if(selector.equals("collect")||selector.equals("map")){transformed.add(value);continue;}if(selector.equals("each"))continue;boolean matches=truth(value);if(matches)count++;
                if(selector.equals("find")&&matches)return freeze(Arrays.asList(entry.key(),entry.value()));if(selector.equals("any?")&&matches)return true;if(selector.equals("all?")&&!matches)return false;if(selector.equals("none?")&&matches)return false;
                if(Set.of("select","filter","reject").contains(selector)&&matches!=selector.equals("reject"))selected.add(entry);
            }
            return switch(selector){case "each"->null;case "collect","map"->transformedVector(callback,transformed);case "select","filter","reject"->mapping(context,receiver.descriptor(),selected);case "count"->integer(count,"int");case "find"->null;case "any?"->false;default->true;};
        }
        return UNHANDLED;
    }
    private static Object priorityDispatch(Context context,PriorityValue receiver,String selector,Object[] args){
        switch(selector){
            case "length","size":if(args.length==0)return integer(receiver.entries().size(),"int");break;
            case "empty?":if(args.length==0)return receiver.entries().isEmpty();break;
            case "copy":if(args.length==0)return receiver;break;
            case "to_vec":if(args.length==0)return typedVector("vec["+parameters(receiver.descriptor()).getFirst()+"]",receiver.values());break;
            case "peek":if(args.length==0)return receiver.entries().isEmpty()?null:receiver.entries().getFirst().value();break;
            case "insert:with":if(args.length==2){check(context,args[0],parameters(receiver.descriptor()).getFirst());if(!number(args[1]))return fail(context,"type","prioridade deve ser numérica");var entries=new ArrayList<>(receiver.entries());entries.add(new Priority(args[0],args[1]));entries.sort((a,b)->compare(context,a.priority(),b.priority()));return new PriorityValue(receiver.descriptor(),entries);}break;
            case "pop_min":if(args.length==0){var entries=new ArrayList<>(receiver.entries());if(!entries.isEmpty())entries.removeFirst();return new PriorityValue(receiver.descriptor(),entries);}break;
        }
        return UNHANDLED;
    }
}
