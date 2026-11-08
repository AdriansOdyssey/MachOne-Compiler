package mach.runtime;

import java.util.*;
import mach.runtime.RuntimeSupport.Context;
import mach.runtime.RuntimeSupport.RecordValue;

/** Executable record derivations, reached after explicit user methods. */
public final class DerivedSupport {
    private DerivedSupport() {}
    public static Object dispatch(Context context,Object receiver,String selector,Object[] arguments,Object block) {
        if(!(receiver instanceof RecordValue record))return RuntimeSupport.UNHANDLED;
        var derives=Arrays.asList(record.info.derives());
        if(derives.contains("Vec")&&selector.equals("to_vec")&&arguments.length==0)
            return Collections.unmodifiableList(Arrays.asList(RuntimeSupport.recordValues(record)));
        if(derives.contains("Iter")&&selector.equals("each")&&(arguments.length==1||arguments.length==0&&block!=null)) {
            Object function=arguments.length==1?arguments[0]:block;
            for(Object value:RuntimeSupport.recordValues(record)) {
                Object result=RuntimeSupport.send(context,function,"result",new Object[]{value});
                if(result instanceof RuntimeSupport.FailureValue)return result;
            }
            return null;
        }
        if(derives.contains("Block")&&selector.equals("value")) {
            Object[] values=RuntimeSupport.recordValues(record);
            for(int i=0;i<record.info.fieldTypes().length;i++)if(RuntimeSupport.concreteFieldType(record,record.info.fieldTypes()[i]).startsWith("lambda"))
                return RuntimeSupport.send(context,values[i],"result",arguments,block);
            return RuntimeSupport.fail(context,"type","Block exige um único atributo lambda");
        }
        if(derives.contains("Ord")&&Set.of("compare","<=>","<",">","<=",">=").contains(selector)&&arguments.length==1) {
            if(!(arguments[0] instanceof RecordValue other)||record.info!=other.info)
                return RuntimeSupport.fail(context,"type","Ord exige dois valores do mesmo record");
            if(!Set.of("compare","<=>").contains(selector)) {
                Object ordered=RuntimeSupport.send(context,record,"compare",arguments);
                if(ordered instanceof RuntimeSupport.FailureValue)return ordered;
                if(!(ordered instanceof RuntimeSupport.NumberValue number)||!number.type().equals("int"))
                    return RuntimeSupport.fail(context,"type","compare deve retornar int");
                return switch(selector){case "<"->number.integral()<0;case ">"->number.integral()>0;case "<="->number.integral()<=0;default->number.integral()>=0;};
            }
            Object[] left=RuntimeSupport.recordValues(record),right=RuntimeSupport.recordValues(other);
            Object result=RuntimeSupport.integer(0,"int");
            for(int i=0;i<left.length;i++) {
                result=compare(context,left[i],right[i]);
                if(result instanceof RuntimeSupport.FailureValue)return result;
                if(((RuntimeSupport.NumberValue)result).integral()!=0)break;
            }
            return result;
        }
        return RuntimeSupport.UNHANDLED;
    }
    private static Object compare(Context context,Object left,Object right) {
        if(RuntimeSupport.hasApplicable(context,left,"compare",new Object[]{right}))return RuntimeSupport.send(context,left,"compare",new Object[]{right});
        if(left instanceof String a&&right instanceof String b)return RuntimeSupport.integer(Integer.signum(a.compareTo(b)),"int");
        if(left instanceof RuntimeSupport.CharacterValue a&&right instanceof RuntimeSupport.CharacterValue b)return RuntimeSupport.integer(Integer.compare(a.codepoint(),b.codepoint()),"int");
        if(left instanceof RecordValue)return RuntimeSupport.send(context,left,"compare",new Object[]{right});
        Object less=RuntimeSupport.binary(context,"<",left,right);
        if(less instanceof RuntimeSupport.FailureValue)return less;
        if(RuntimeSupport.truth(less))return RuntimeSupport.integer(-1,"int");
        Object greater=RuntimeSupport.binary(context,">",left,right);
        if(greater instanceof RuntimeSupport.FailureValue)return greater;
        return RuntimeSupport.integer(RuntimeSupport.truth(greater)?1:0,"int");
    }
}
