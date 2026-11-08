package mach.semantic;
import mach.ast.Ast.Type;
import mach.ast.Ast.Span;
import java.util.*;
/** Native library signatures; user impls are selected before these contracts. */
public final class StandardLibrary {
    private StandardLibrary() {}
    public static Set<String> types(Set<String> languageTypes){var types=new LinkedHashSet<>(languageTypes);types.addAll(Set.of("message","Integer","Signed","Unsigned","Real","Collection","Sequence","Mapping","Comparable","Iterable","Enumerable","File","Path","Dir","Env","Process","System","Json","Csv","Base64","Hex","Hash","Uuid","Url","Date","DateTime","Console","Reflect","Debug","Log","Test","Bench","Math","Random","Fn","Text","Bits","Duration","Instant","Time","Init","Repr","Prop","Eq","Ord","Iter","Block","Vec","Proxy"));types.addAll(ExtraCollectionContracts.TYPES);return Collections.unmodifiableSet(types);}
    public static Type resolve(Type receiver,String selector,List<Type> args,Type block,Span span,TypeSystem types){Type result=LibraryContracts.resolve(receiver,selector,args,block,span,types);if(result==null)result=CollectionContracts.resolve(receiver,selector,args,block,span,types);if(result==null)result=ExtraCollectionContracts.resolve(receiver,selector,args,block,span,types);if(result==null)result=SystemContracts.resolve(receiver,selector,args,block,span);return result;}
    public static List<Type> blockInputs(Type receiver,String selector,List<Type> args){List<Type> result=LibraryContracts.expectedBlockInputs(receiver,selector,args);if(result==null)result=CollectionContracts.blockInputs(receiver,selector,args);if(result==null)result=ExtraCollectionContracts.blockInputs(receiver,selector,args);if(result==null)result=SystemContracts.blockInputs(receiver,selector,args);return result;}
}
