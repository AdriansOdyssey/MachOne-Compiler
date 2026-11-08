package mach.runtime;

import java.util.*;
import mach.runtime.RuntimeSupport.*;

/** Iteration and rest-argument containers used by generated JVM instructions. */
public final class IterationSupport {
    private IterationSupport() {}
    public static Object tail(Object[] arguments,int start) {
        return Collections.unmodifiableList(Arrays.asList(Arrays.copyOfRange(arguments,start,arguments.length)));
    }
    public static Iterator<Object[]> iterator(Object value,int arity) {
        if(value instanceof ExtraCollectionsLibrary.Value extra)return ExtraCollectionsLibrary.iterator(extra,arity);
        if(value instanceof Map<?,?> map) {
            if(arity!=1&&arity!=2)throw new MachFailure("arity","obj exige um elemento ou chave e valor");
            Iterator<? extends Map.Entry<?,?>> entries=map.entrySet().iterator();
            return new Iterator<>() {
                public boolean hasNext(){return entries.hasNext();}
                public Object[] next(){var entry=entries.next();if(arity==2)return new Object[]{entry.getKey(),entry.getValue()};var single=new LinkedHashMap<Object,Object>();single.put(entry.getKey(),entry.getValue());return new Object[]{Collections.unmodifiableMap(single)};}
            };
        }
        if(arity!=1)throw new MachFailure("arity","coleção exige uma variável de iteração");
        Iterator<?> values=value instanceof String text?text.codePoints().mapToObj(CharacterValue::new).iterator():RuntimeSupport.iterator(value);
        return new Iterator<>() {public boolean hasNext(){return values.hasNext();}public Object[] next(){return new Object[]{values.next()};}};
    }
    public static Object builder(boolean object) {return object?new LinkedHashMap<Tag,Object>():new ArrayList<Object>();}
    @SuppressWarnings("unchecked") public static void add(Object builder,Object value) {((List<Object>)builder).add(value);}
    @SuppressWarnings("unchecked") public static void put(Object builder,Object key,Object value) {
        if(!(key instanceof Tag))throw new MachFailure("type","chave de comprehension deve ser tag");
        ((Map<Tag,Object>)builder).put((Tag)key,value);
    }
    public static Object finish(Object builder) {
        if(builder instanceof Map<?,?> map)return Collections.unmodifiableMap(new LinkedHashMap<>(map));
        return Collections.unmodifiableList(new ArrayList<>((List<?>)builder));
    }
}
