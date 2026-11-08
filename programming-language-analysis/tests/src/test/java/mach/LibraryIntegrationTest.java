package mach;
import mach.frontend.Diagnostic;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
class LibraryIntegrationTest {
    private String run(String source) throws Exception {
        byte[] code=new Compiler().compile("library-integration.ma",source,"LibraryIntegrationProgram");
        Class<?> program=new ClassLoader(getClass().getClassLoader()){Class<?> load(){return defineClass("LibraryIntegrationProgram",code,0,code.length);}}.load();
        var output=new ByteArrayOutputStream();var previous=System.out;
        try{System.setOut(new PrintStream(output,true,StandardCharsets.UTF_8));program.getMethod("main",String[].class).invoke(null,(Object)new String[0]);}finally{System.setOut(previous);}return output.toString(StandardCharsets.UTF_8).replace("\r\n","\n");
    }
    @Test void portablePairSwapPreservesTypes() throws Exception {assertEquals("29\n#age\n",run("let pair Pair[tag,int] = (Pair) new #age,29\nlet reversed Pair[int,tag] = (pair) swap\nshow (reversed) first\nshow (reversed) second"));}
    @Test void portableQueuesAndStacksKeepOriginals() throws Exception {assertEquals("1\n3\n2\n",run("let q=(Queue[int]) new [1,2]\nlet s=(Stack[int]) new [1,2]\nshow (q) peek ?:0\nshow ((s) push 3) peek ?:0\nshow (s) peek ?:0"));}
    @Test void portableMutationChecksGenericArguments() {assertThrows(Diagnostic.class,()->new Compiler().check("invalid.ma","let q=(Queue[int]) new [1]\n(q) enqueue \"invalid\""));}
    @Test void callbacksAndAppendRespectNominalSubtypes() throws Exception {assertEquals("1\n2\n",run("""
        abstract data Animal end
        record Cat & Animal (Init) end
        let cats=[(Cat) new]
        (cats) each |animal Animal| { show 1 }
        let animals vec[Animal]=[(Cat) new]
        show ((animals) append ((Cat) new)) length
        """));}
    @Test void thenIsAnOverridableMessage() throws Exception {assertEquals("custom\n",run("impl bool on then: action lambda[any] -> str = \"custom\"\nshow (true) then { \"native\" }"));}
    @Test void emptyTypedVectorsKeepNumericIdentities() throws Exception {assertEquals("0.0\n1.0\n[type \"vec[double]\"]\n",run("let xs vec[double]=[]\nlet sum double=(xs) sum\nlet product double=(xs) product\nshow sum\nshow product\nshow (xs) type"));}
    @Test void comprehensionBindingsStayLocalAndTyped() throws Exception {assertEquals("[2, 4]\n",run("let xs vec[int]=for x in 1..4 yield x if (x) even?\nshow xs"));assertThrows(Diagnostic.class,()->new Compiler().check("scope.ma","let xs=for x in [1] yield x\nshow x"));}
    @Test void invalidNativeCallsAreRejectedBeforeExecution() {for(var source:new String[]{"let xs=[1,2]\n(xs) append \"wrong\"","(\"abc\") replace 1 with 2","(1) upto \"wrong\" => |i| { i }"})assertThrows(Diagnostic.class,()->new Compiler().check("invalid.ma",source));}
    @Test void temporaryFailurePredicateIsEvaluatedOnce() throws Exception {assertEquals("handled\n1\n",run("""
        var @calls=0
        on produce -> try[int]
            @calls+=1
            (Fail) raise #invalid,"temporary"
        end
        ((produce) fail?) then { show "handled" }
        show @calls
        """));}
    @Test void guardedReturnTypesIncludeTheFallback() {assertThrows(Diagnostic.class,()->new Compiler().check("guard.ma","on choose: x int, if x<0 = \"negative\"\non choose: x int = 42\nlet value str = choose 1"));}
    @Test void nativeOperatorsAcceptMessageSyntax() throws Exception {assertEquals("5\n2.0\ntrue\n",run("show (2) +: 3\nshow (4) /: 2\nshow (3) >: 2"));}
    @Test void receiverGenericUnionAcceptsEachPermittedArgument() throws Exception {assertEquals("value\n",run("record Box[T](Init)\nvalue T\nend\nimpl Box\non put: value T\nself=self | {#value: value}\nend\nend\nlet box Box[union[int,str]]=(Box) new 1\n(box) put \"value\"\nshow box's #value"));}
}
