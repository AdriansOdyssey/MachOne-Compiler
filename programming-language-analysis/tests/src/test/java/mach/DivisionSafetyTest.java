package mach;
import mach.frontend.Diagnostic;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
class DivisionSafetyTest {
    private String run(String source) throws Exception {byte[] code=new Compiler().compile("division.ma",source,"DivisionProgram");Class<?> program=new ClassLoader(getClass().getClassLoader()){Class<?> load(){return defineClass("DivisionProgram",code,0,code.length);}}.load();var output=new ByteArrayOutputStream();var previous=System.out;try{System.setOut(new PrintStream(output,true,StandardCharsets.UTF_8));program.getMethod("main",String[].class).invoke(null,(Object)new String[0]);}finally{System.setOut(previous);}return output.toString(StandardCharsets.UTF_8);}
    @Test void unknownDivisorRequiresTryOrRescue() {assertThrows(Diagnostic.class,()->new Compiler().check("bad.ma","on divide: x double, by_value: y double -> double = x/y"));new Compiler().check("safe.ma","on divide: x double, by_value: y double -> try[double] = x/y");}
    @Test void knownNonzeroLiteralAndBindingStayPlain() throws Exception {assertEquals("2.5\n5.0\n",run("let a double=5/2\nlet denominator=2\nlet b double=10/denominator\nshow a\nshow b"));}
    @Test void guardsRefineNonzeroDivisors() throws Exception {assertEquals("2.5\n",run("on divide: a double, by_value: b double -> double, if b!=0.0 = a/b\nshow divide 5.0 by_value 2.0"));}
    @Test void zeroDivisionReturnsClassifiedFailure() throws Exception {assertEquals("#zero_division\n#zero_division\n#zero_division\n",run("show (1/0) rescue |f| { (f) kind }\nshow (1.0/0.0) rescue |f| { (f) kind }\nshow (1//0) rescue |f| { (f) kind }"));}
    @Test void mutationInvalidatesNonzeroProof() {assertThrows(Diagnostic.class,()->new Compiler().check("bad.ma","var @n=2\n@n=0\nlet x double=5/@n"));}
}
