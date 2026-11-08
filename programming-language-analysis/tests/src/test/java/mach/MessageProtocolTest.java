package mach;

import mach.runtime.RuntimeSupport;
import mach.runtime.RuntimeSupport.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real runtime and generated JVM bytecode tests for the four message protocols. */
public class MessageProtocolTest {
    private static Object n(long value){return RuntimeSupport.integer(value,"int");}
    private static Object tag(String name){return RuntimeSupport.tag(name);}
    private static Context context(){return new Context();}
    private static void record(Context c,String name,String[] fields,String[] types,String[] labels,String... derives){RuntimeSupport.defineRecord(c,name,"",fields,types,labels,derives);}
    private static void method(Context c,String owner,String selector,String body,String... args){RuntimeSupport.register(c,MessageProtocolTest.class,body,"",owner,selector,args);}
    private static Object create(Context c,String name,Object... args){return RuntimeSupport.send(c,RuntimeSupport.type(name),"new",args);}
    private static Object send(Context c,Object receiver,String selector,Object... args){return RuntimeSupport.send(c,receiver,selector,args);}
    public static Object twice(Context c,Object self,Object[] args){return RuntimeSupport.binary(c,"*",args[0],n(2));}
    public static Object add(Context c,Object self,Object[] args){return RuntimeSupport.binary(c,"+",args[0],args[1]);}
    public static Object value(Context c,Object self,Object[] args){return RuntimeSupport.get(c,self,tag("value"));}
    public static Object plus(Context c,Object self,Object[] args){return RuntimeSupport.binary(c,"+",RuntimeSupport.get(c,self,tag("value")),args[0]);}
    public static Object internalRead(Context c,Object self,Object[] args){return send(c,self,"value");}
    public static Object forward(Context c,Object self,Object[] args){return RuntimeSupport.get(c,self,tag("target"));}
    public static Object decline(Context c,Object self,Object[] args){return null;}
    public static Object interceptor(Context c,Object self,Object[] args){c.globals.set("count",RuntimeSupport.binary(c,"+",c.globals.get("count"),n(1)));return send(c,args[0],"proceed");}
    public static Object replacingInterceptor(Context c,Object self,Object[] args){
        assertEquals(tag("plus"),RuntimeSupport.get(c,args[0],tag("name")));assertEquals(List.of(tag("plus")),RuntimeSupport.get(c,args[0],tag("parts")));assertSame(self,RuntimeSupport.get(c,args[0],tag("receiver")));
        return send(c,args[0],"proceed_with",List.of(n(10)));
    }
    public static Object redirectInterceptor(Context c,Object self,Object[] args){return send(c,args[0],"redirect",c.globals.get("other"));}
    public static Object blocked(Context c,Object self,Object[] args){return RuntimeSupport.fail(c,"denied","blocked");}
    public static Object blockPlus(Context c,Object self,Object[] args){return tag("plus").equals(RuntimeSupport.get(c,args[0],tag("name")))?RuntimeSupport.fail(c,"denied","blocked"):send(c,args[0],"proceed");}
    public static Object dynamicTarget(Context c,Object self,Object[] args){return c.globals.get("other");}
    public static Object runBlock(Context c,Object self,Object[] args){return send(c,RuntimeSupport.currentBlock(c),"result",args);}
    public static Object takeTarget(Context c,Object self,Object[] args){return send(c,args[0],"value");}
    public static Object self(Context c,Object self,Object[] args){return self;}
    private static Context calculator(){Context c=context();record(c,"Calculator",new String[0],new String[0],new String[0],"Init");method(c,"type[Calculator]","twice","twice","int");method(c,"type[Calculator]","add:with","add","int","int");return c;}

    @Test void performHandlesTagsMultipartAndOperatorMessages(){
        Context c=calculator();Object type=RuntimeSupport.type("Calculator");assertEquals(n(42),send(c,type,"perform:with",tag("twice"),n(21)));
        assertEquals(n(5),send(c,type,"perform:with",List.of(tag("add"),tag("with")),List.of(n(2),n(3))));assertEquals(n(7),send(c,n(3),"perform:with",tag("+"),n(4)));RuntimeSupport.finish(c);
    }
    @Test void performDistinguishesUnknownFromInapplicableAndValidatesSelectors(){
        Context c=calculator();Object type=RuntimeSupport.type("Calculator");assertEquals("not_understood",((FailureValue)send(c,type,"perform:with",tag("triple"),n(3))).kind());
        assertEquals("no_applicable",((FailureValue)send(c,type,"perform:with",tag("twice"),"x")).kind());assertEquals("type",((FailureValue)send(c,type,"perform:with",List.of("not a tag"),n(3))).kind());
    }
    @Test void forwardRunsMethodOnTargetAndDecliningHookFails(){
        Context c=context();record(c,"Engine",new String[]{"value"},new String[]{"int"},new String[]{""},"Init");record(c,"Car",new String[]{"target"},new String[]{"Engine"},new String[]{""},"Init");record(c,"Silent",new String[0],new String[0],new String[0],"Init");
        method(c,"Engine","value","value");method(c,"Car","forward","forward","message");method(c,"Silent","forward","decline","message");Object engine=create(c,"Engine",n(7));assertEquals(n(7),send(c,create(c,"Car",engine),"value"));assertEquals("not_understood",((FailureValue)send(c,create(c,"Silent"),"missing")).kind());
    }
    @Test void interceptorRunsOnceAndSelfSendsBypassIt(){
        Context c=context();c.globals.bind("count",n(0));record(c,"Counter",new String[]{"value"},new String[]{"int"},new String[]{""},"Init");method(c,"Counter","value","value");method(c,"Counter","read","internalRead");method(c,"Counter","intercept","interceptor","message");
        assertEquals(n(8),send(c,create(c,"Counter",n(8)),"read"));assertEquals(n(1),c.globals.get("count"));RuntimeSupport.finish(c);
    }
    @Test void proceedWithReplacesArgumentsAndKeepsMessageMetadata(){
        Context c=context();record(c,"Counter",new String[]{"value"},new String[]{"int"},new String[]{""},"Init");method(c,"Counter","plus","plus","int");method(c,"Counter","intercept","replacingInterceptor","message");assertEquals(n(13),send(c,create(c,"Counter",n(3)),"plus",n(1)));RuntimeSupport.finish(c);
    }
    @Test void redirectObservesTargetInterceptor(){
        Context c=context();c.globals.bind("count",n(0));record(c,"Target",new String[]{"value"},new String[]{"int"},new String[]{""},"Init");record(c,"Source",new String[0],new String[0],new String[0],"Init");method(c,"Target","value","value");method(c,"Target","intercept","interceptor","message");method(c,"Source","intercept","redirectInterceptor","message");c.globals.bind("other",create(c,"Target",n(12)));assertEquals(n(12),send(c,create(c,"Source"),"value"));assertEquals(n(1),c.globals.get("count"));
    }
    @Test void interceptionMayRejectKnownMessage(){Context c=context();record(c,"Counter",new String[]{"value"},new String[]{"int"},new String[]{""},"Init");method(c,"Counter","value","value");method(c,"Counter","intercept","blocked","message");assertEquals("denied",((FailureValue)send(c,create(c,"Counter",n(3)),"value")).kind());}
    @Test void proxyIsSubstitutableAndItsTargetRunsItsMethods(){
        Context c=context();record(c,"Target",new String[]{"value"},new String[]{"int"},new String[]{""},"Init");record(c,"Wrapper",new String[]{"target"},new String[]{"Target"},new String[]{"proxied"},"Init","Proxy");method(c,"Target","value","value");method(c,"","read_target","takeTarget","Target");Object wrapper=create(c,"Wrapper",create(c,"Target",n(17)));
        assertTrue(RuntimeSupport.satisfies(c,wrapper,"Target",false));assertEquals(n(17),send(c,wrapper,"value"));assertEquals(n(17),send(c,null,"read_target",wrapper));assertEquals(true,send(c,wrapper,"responds_to?",tag("value")));RuntimeSupport.finish(c);
    }
    @Test void targetHookOverridesProxiedFieldForEverySend(){
        Context c=context();record(c,"Target",new String[]{"value"},new String[]{"int"},new String[]{""},"Init");record(c,"Wrapper",new String[]{"target"},new String[]{"Target"},new String[]{"proxied"},"Init","Proxy");method(c,"Target","value","value");method(c,"Wrapper","target","dynamicTarget");c.globals.bind("other",create(c,"Target",n(5)));Object wrapper=create(c,"Wrapper",create(c,"Target",n(3)));assertEquals(n(5),send(c,wrapper,"value"));c.globals.set("other",create(c,"Target",n(9)));assertEquals(n(9),send(c,wrapper,"value"));
    }
    @Test void proceedPreservesAnonymousBlock(){
        Context c=context();c.globals.bind("count",n(0));record(c,"Executor",new String[0],new String[0],new String[0],"Init");method(c,"Executor","apply","runBlock","int");method(c,"Executor","intercept","interceptor","message");Object block=new NativeFunction("lambda[int,int]",args->RuntimeSupport.binary(c,"*",args[0],n(3)));assertEquals(n(12),RuntimeSupport.send(c,create(c,"Executor"),"apply",new Object[]{n(4)},block));assertEquals(n(1),c.globals.get("count"));
    }
    @Test void forwardingCyclesTerminateAsFailure(){Context c=context();record(c,"Self",new String[0],new String[0],new String[0],"Init");method(c,"Self","forward","self","message");assertEquals("not_understood",((FailureValue)send(c,create(c,"Self"),"missing")).kind());}
    @Test void fieldAndOperatorEntrypointsHonorInterceptionExactlyOnce(){
        Context c=context();c.globals.bind("count",n(0));record(c,"Counter",new String[]{"value"},new String[]{"int"},new String[]{""},"Init");method(c,"Counter","intercept","interceptor","message");method(c,"Counter","+","plus","int");method(c,"Counter","-","value");Object counter=create(c,"Counter",n(8));
        assertEquals(n(8),RuntimeSupport.get(c,counter,tag("value")));assertEquals(n(1),c.globals.get("count"));assertEquals(n(11),RuntimeSupport.binary(c,"+",counter,n(3)));assertEquals(n(2),c.globals.get("count"));assertEquals(n(8),RuntimeSupport.unary(c,"-",counter));assertEquals(n(3),c.globals.get("count"));RuntimeSupport.finish(c);
    }
    @Test void dynamicPrivateReadsRequireTheOwningImplementationEvenThroughInterceptor(){
        Context c=context();c.globals.bind("count",n(0));record(c,"Secret",new String[]{"value"},new String[]{"int"},new String[]{"<private>"},"Init");method(c,"Secret","reveal","value");method(c,"Secret","intercept","interceptor","message");Object secret=create(c,"Secret",n(42));assertEquals(n(42),send(c,secret,"reveal"));
        for(String selector:List.of("'s","slot","maybe_slot")){Object failure=send(c,secret,"perform:with",tag(selector),tag("value"));assertEquals("private",((FailureValue)failure).kind());RuntimeSupport.acknowledge(c,failure);}RuntimeSupport.finish(c);
    }
    @Test void performCannotBypassTargetInterception(){
        Context c=context();record(c,"Counter",new String[]{"value"},new String[]{"int"},new String[]{""},"Init");method(c,"Counter","plus","plus","int");method(c,"Counter","intercept","blockPlus","message");Object failure=send(c,create(c,"Counter",n(4)),"perform:with",tag("plus"),n(1));assertEquals("denied",((FailureValue)failure).kind());
    }
    @Test void nativeSelectorIntrospectionIsBroadAndDoesNotExecuteMessages(){
        Context c=context();for(String selector:List.of("abs","upto","factorial","step:every","gcd","to_int"))assertEquals(true,send(c,n(1),"responds_to?",tag(selector)),selector);for(String selector:List.of("swapcase","split_lines","casecmp","replace:with"))assertEquals(true,send(c,"AbC","responds_to?",tag(selector)),selector);
        for(String selector:List.of("partition","combinations","binary_search","reduce:with","min_by"))assertEquals(true,send(c,List.of(n(1)),"responds_to?",tag(selector)),selector);assertEquals(true,send(c,RuntimeSupport.type("File"),"responds_to?",tag("delete")));assertEquals(true,send(c,null,"responds_to?",tag("nil?")));assertEquals(false,send(c,n(1),"responds_to?",tag("nonexistent")));RuntimeSupport.finish(c);
    }
    @Test void nativeKnownSelectorsWithWrongArityAreInapplicable(){
        Context c=context();Object failure=send(c,n(7),"abs",n(1));assertEquals("no_applicable",((FailureValue)failure).kind());RuntimeSupport.acknowledge(c,failure);failure=send(c,RuntimeSupport.type("File"),"delete");assertEquals("no_applicable",((FailureValue)failure).kind());RuntimeSupport.acknowledge(c,failure);assertEquals("nil",send(c,null,"stringify"));assertEquals(RuntimeSupport.type("nil"),send(c,null,"type"));RuntimeSupport.finish(c);
    }
    @Test void dynamicLambdaArgumentsRespectTheDeclaredSignature(){Context c=context();Object function=new NativeFunction("lambda[int,int]",args->args[0]);assertEquals(n(4),send(c,function,"result",n(4)));assertEquals("no_applicable",((FailureValue)send(c,function,"perform:with",tag("result"),"bad")).kind());}
    @Test void typedEmptyVectorsRetainTheirElementType(){Object values=RuntimeSupport.vector(new Object[0],"vec[int]");assertEquals("vec[int]",RuntimeSupport.typeName(values));Context c=context();assertTrue(RuntimeSupport.satisfies(c,values,"vec[int]",false));assertFalse(RuntimeSupport.satisfies(c,values,"vec[str]",false));}

    @Test void allNumericDivisionsByZeroReturnTheStandardizedFailure(){
        Context c=context();for(String operation:List.of("/","//","%"))for(Object denominator:List.of(n(0),RuntimeSupport.floating(0.0,"double"),RuntimeSupport.floating(-0.0,"float"))){
            Object failure=RuntimeSupport.binary(c,operation,n(7),denominator);assertEquals("zero_division",((FailureValue)failure).kind());RuntimeSupport.acknowledge(c,failure);
        }
        Object large=RuntimeSupport.integerText("18446744073709551615","ulong");Object failure=RuntimeSupport.binary(c,"/",large,n(0));assertEquals("zero_division",((FailureValue)failure).kind());RuntimeSupport.acknowledge(c,failure);RuntimeSupport.finish(c);
    }
    @Test void genericRecordPatternsBindAndPreserveConcreteArgumentTypes(){
        Context c=context();RuntimeSupport.defineRecord(c,"Box","",new String[]{"T"},new String[]{"value"},new String[]{"T"},new String[]{""},new String[]{"Init"});
        method(c,"Box[$X]","accept","value","$X");Object box=create(c,"Box[int]",n(4));assertEquals(n(4),send(c,box,"accept",n(6)));
        Object failure=send(c,box,"accept","bad");assertEquals("no_applicable",((FailureValue)failure).kind());RuntimeSupport.acknowledge(c,failure);RuntimeSupport.finish(c);
    }
    @Test void dynamicDefaultGenericConstructorInfersTypeParameters(){
        Context c=context();RuntimeSupport.defineRecord(c,"Box","",new String[]{"T"},new String[]{"value"},new String[]{"T"},new String[]{""},new String[]{"Init"});
        Object box=send(c,RuntimeSupport.type("Box"),"perform:with",tag("new"),n(5));assertEquals("Box[int]",RuntimeSupport.typeName(box));RuntimeSupport.finish(c);
    }
    private String run(String source) throws Exception{
        byte[] bytes=new Compiler().compile("message-protocol.ma",source,"MessageProtocolProgram");Class<?> program=new ClassLoader(getClass().getClassLoader()){Class<?> load(){return defineClass("MessageProtocolProgram",bytes,0,bytes.length);}}.load();var output=new java.io.ByteArrayOutputStream();var previous=System.out;try{System.setOut(new java.io.PrintStream(output,true,java.nio.charset.StandardCharsets.UTF_8));program.getMethod("main",String[].class).invoke(null,(Object)new String[0]);}finally{System.setOut(previous);}return output.toString(java.nio.charset.StandardCharsets.UTF_8).replace("\r\n","\n");
    }
    @Test void generatedBytecodeDynamicallySendsAndReifiesErrors() throws Exception{
        assertEquals("42\n5\n#not_understood\n#no_applicable\n",run("""
            record Calculator end
            impl type[Calculator]
                on twice: n int = n * 2
                on add: a int, with: b int = a + b
            end
            show (Calculator) perform #twice with 21
            show (Calculator) perform [#add, #with] with [2, 3]
            show ((Calculator) perform #triple with 3) rescue |e| { (e) kind }
            show ((Calculator) perform #twice with "x") rescue |e| { (e) kind }
            """));
    }
    @Test void generatedBytecodeForwardsToDifferentSelf() throws Exception{
        assertEquals("Motor ligado\n#not_understood\n",run("""
            record Motor(Init) end
            impl Motor
                on start = "Motor ligado"
            end
            record Car(Init)
                _motor Motor
            end
            impl Car
                on forward: msg message -> maybe[any]
                    let motor = self's #motor
                    (motor) responds_to? (msg's #parts) then { return motor }
                    nil
                end
            end
            let car = (Car) new ((Motor) new)
            show (car) start
            show ((car) fly) rescue |e| { (e) kind }
            """));
    }
    @Test void generatedInterceptorBlocksAndProceeds() throws Exception{
        assertEquals("100\n90\n#locked\n",run("""
            record Account(Init, Prop)
                readable _balance int
                readable _locked bool
            end
            impl Account
                on withdraw: amount int -> int
                    self = self | {#balance: self's #balance - amount}
                    self's #balance
                end
                on intercept: msg message -> try[any]
                    ((msg's #name) == #withdraw and (self's #locked)) then do
                        return (Fail) raise #locked, "conta bloqueada"
                    end
                    (msg) proceed
                end
            end
            let account = (Account) new 100, false
            show (account) balance
            show (account) withdraw 10
            show (((Account) new 100, true) withdraw 10) rescue |e| { (e) kind }
            """));
    }
    @Test void generatedProxySubstitutesAndForwards() throws Exception{
        assertEquals("true\n15\n",run("""
            record Account(Init, Prop)
                readable _balance int
            end
            record Guarded(Init, Proxy)
                proxied _account Account
            end
            let proxy = (Guarded) new ((Account) new 15)
            show proxy isa Account
            show (proxy) balance
            """));
    }
}
