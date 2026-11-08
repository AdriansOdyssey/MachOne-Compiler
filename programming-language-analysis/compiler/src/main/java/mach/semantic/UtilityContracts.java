package mach.semantic;

import mach.ast.Ast.*;
import mach.frontend.Diagnostic;
import java.util.*;

/** Contracts for pure utilities and synchronous time values. */
public final class UtilityContracts {
    private UtilityContracts() {}
    private static final Type INT=new Type("int"),LONG=new Type("long"),DOUBLE=new Type("double"),STR=new Type("str"),BOOL=new Type("bool"),DURATION=new Type("Duration"),INSTANT=new Type("Instant");
    private static final Set<String> INTEGER=Set.of("byte","ubyte","short","ushort","int","uint","long","ulong","Integer","Signed","Unsigned");
    private static boolean numeric(Type value){return INTEGER.contains(value.name())||Set.of("float","double","Number","Real").contains(value.name());}
    private static Type attempt(Type type){return new Type("try",List.of(type));}
    private static void arity(List<Type> values,int count,Span span){if(values.size()!=count)throw new Diagnostic("M2201",span,"mensagem exige "+count+" argumento(s)");}
    private static void require(Type actual,Type expected,Span span){if(!actual.equals(expected))throw new Diagnostic("M2101",span,"tipo "+actual+" incompatível com "+expected);}
    private static void integer(Type type,Span span){if(!INTEGER.contains(type.name()))throw new Diagnostic("M2101",span,"mensagem exige inteiro, recebido "+type);}
    private static void number(Type type,Span span){if(!numeric(type))throw new Diagnostic("M2101",span,"mensagem exige número, recebido "+type);}
    public static Type resolve(Type receiver,String selector,List<Type> values,Type block,Span span) {
        if(receiver==null)return null;
        if(numeric(receiver)&&Set.of("seconds","minutes","hours","days","millis").contains(selector)){arity(values,0,span);return INTEGER.contains(receiver.name())?DURATION:attempt(DURATION);}
        if(numeric(receiver)&&selector.equals("format_thousands")){arity(values,0,span);return STR;}
        if(receiver.equals(DURATION)) {
            if(Set.of("as_seconds","as_millis","to_str","stringify").contains(selector)){arity(values,0,span);return selector.equals("as_seconds")?DOUBLE:selector.equals("as_millis")?LONG:STR;}
            if(Set.of("+","-","==","<",">","<=",">=").contains(selector)){arity(values,1,span);require(values.getFirst(),DURATION,span);return Set.of("+","-").contains(selector)?DURATION:BOOL;}
            if(selector.equals("*")){arity(values,1,span);number(values.getFirst(),span);return INTEGER.contains(values.getFirst().name())?DURATION:attempt(DURATION);}
        }
        if(receiver.equals(INSTANT)) {
            if(selector.equals("elapsed")){arity(values,0,span);return DURATION;}
            if(selector.equals("-")){arity(values,1,span);require(values.getFirst(),INSTANT,span);return DURATION;}
            if(selector.equals("after")){arity(values,1,span);require(values.getFirst(),DURATION,span);return attempt(INSTANT);}
        }
        if(receiver.name().equals("type")&&receiver.args().size()==1) {
            String module=receiver.args().getFirst().name();
            if(module.equals("Time")&&selector.equals("now")){arity(values,0,span);return INSTANT;}
            if(module.equals("Bits")&&Set.of("count","reverse","parity").contains(selector)){arity(values,1,span);integer(values.getFirst(),span);return selector.equals("count")?INT:selector.equals("parity")?BOOL:values.getFirst();}
            if(module.equals("Number")) {
                if(selector.equals("parse")){arity(values,1,span);require(values.getFirst(),STR,span);return attempt(new Type("Number"));}
                if(selector.equals("format_thousands")){arity(values,1,span);number(values.getFirst(),span);return STR;}
            }
            if(module.equals("Text")) {
                if(selector.equals("dedent")){arity(values,1,span);require(values.getFirst(),STR,span);return STR;}
                if(selector.equals("levenshtein")){arity(values,2,span);for(Type value:values)require(value,STR,span);return INT;}
                if(Set.of("wrap:width","indent:width","indent").contains(selector)){arity(values,2,span);require(values.getFirst(),STR,span);integer(values.getLast(),span);return attempt(STR);}
            }
        }
        return null;
    }
}
