package mach;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
class IntegerWrapTest {
    private String run(String source)throws Exception{
        byte[] bytes=new Compiler().compile("wrap.ma",source,"WrapProgram");
        Class<?> cls=new ClassLoader(getClass().getClassLoader()){Class<?> loadProgram(){return defineClass("WrapProgram",bytes,0,bytes.length);}}.loadProgram();
        var out=new ByteArrayOutputStream();var previous=System.out;
        try{System.setOut(new PrintStream(out,true,StandardCharsets.UTF_8));cls.getMethod("main",String[].class).invoke(null,(Object)new String[0]);}finally{System.setOut(previous);}
        return out.toString(StandardCharsets.UTF_8).replace("\r\n","\n");
    }
    @Test void allIntegerWidthsWrapOnAddition()throws Exception{assertEquals("-128\n0\n-32768\n0\n-2147483648\n0\n-9223372036854775808\n0\n",run("""
        let a byte = 127
        let b ubyte = 255
        let c short = 32767
        let d ushort = 65535
        let e int = 2147483647
        let f uint = 4294967295
        let g long = 9223372036854775807
        let h ulong = 18446744073709551615
        show a + 1
        show b + 1
        show c + 1
        show d + 1
        show e + 1
        show f + 1
        show g + 1
        show h + 1
        """));}
    @Test void unsignedUnderflowAndSignedNegationWrap()throws Exception{assertEquals("255\n18446744073709551615\n-128\n",run("let a ubyte = 0\nlet b ulong = 0\nlet c byte = -128\nshow a - 1\nshow b - 1\nshow -c"));}
    @Test void multiplicationAndPowerWrapWithoutDoubleRounding()throws Exception{assertEquals("0\n0\n0\n",run("let a byte = 64\nlet b int = 2\nlet c ulong = 2\nshow a * 4\nshow b ** 100\nshow c ** 64"));}
    @Test void overflowPreservesTypeIdentity()throws Exception{assertEquals("[type \"byte\"]\n",run("let a byte = 127\nshow (a + 1) type"));}
    @Test void conversionsRemainCheckedInsteadOfWrapping()throws Exception{assertEquals("7\n",run("show ((\"256\") parse: ubyte) rescue |f| { 7 }"));}
    @Test void unsignedRangeKeepsItsTypeAndExactValues()throws Exception{assertEquals("18446744073709551614\n18446744073709551615\n",run("let first ulong = 18446744073709551614\nlet last ulong = 18446744073709551615\nfor n in first..last\nshow n\nend"));}
}
