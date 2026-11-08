package mach;
import mach.frontend.Diagnostic;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
class AnonymousBlocksTest {
    private String run(String source)throws Exception{
        byte[] bytes=new Compiler().compile("anonymous.ma",source,"AnonymousProgram");
        Class<?> cls=new ClassLoader(getClass().getClassLoader()){Class<?> loadProgram(){return defineClass("AnonymousProgram",bytes,0,bytes.length);}}.loadProgram();
        var out=new ByteArrayOutputStream();PrintStream old=System.out;
        try{System.setOut(new PrintStream(out,true,StandardCharsets.UTF_8));cls.getMethod("main",String[].class).invoke(null,(Object)new String[0]);}finally{System.setOut(old);}
        return out.toString(StandardCharsets.UTF_8).replace("\r\n","\n");
    }
    @Test void anonymousBlockFromSpecification()throws Exception{assertEquals("100.0\n",run("on with_margin: cost double\n(&block) result cost * 1.25\nend\nwith_margin 80.0 => |price| { show price }"));}
    @Test void missingAnonymousBlockIsCompileError(){var error=assertThrows(Diagnostic.class,()->new Compiler().check("bad.ma","on apply: value int\n(&block) result value\nend\napply 1"));assertEquals("M2204",error.code());}
    @Test void nestedLambdaCapturesMethodBlock()throws Exception{assertEquals("7\n",run("on apply: value int\nlet invoke_block = || { (&block) result value }\n(invoke_block) result\nend\napply 7 => |n| { show n }"));}
    @Test void vectorEachAndCollectInferArguments()throws Exception{assertEquals("2\n4\n6\n[3, 6, 9]\n",run("let values = [1,2,3]\n(values) each |n| { show n * 2 }\nshow (values) collect |n| { n * 3 }"));}
    @Test void implicitItBlockAndSelection()throws Exception{assertEquals("[2, 4]\n",run("show (1..5) select { (&it) even? }"));}
    @Test void nonLocalReturnFromEachDoClosure()throws Exception{assertEquals("5\n",run("on choose -> int\nlet exit = do\nreturn 5\nend\n([1,2,3]) each |n| { (exit) result }\n99\nend\nshow choose"));}
    @Test void timesDo()throws Exception{assertEquals("x\nx\nx\n",run("(3) times do\nshow \"x\"\nend"));}
    @Test void typedHigherOrderMethodFromSpecification()throws Exception{assertEquals("180.0\n",run("sign { double, lambda[[double], double] -> double }\non apply_rule: price, with: rule\n(rule) result price\nend\nshow apply_rule 200.0 with |p| { p * 0.9 }"));}
    @Test void anonymousBlockRequiredEvenThroughNestedLambda(){var error=assertThrows(Diagnostic.class,()->new Compiler().check("bad.ma","on apply: value int\nlet f = || { (&block) result value }\n(f) result\nend\napply 7"));assertEquals("M2204",error.code());}
}
