package mach;
import mach.frontend.Diagnostic;
import mach.frontend.Frontend;
import mach.ast.Ast;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class VarargsTest {
    private String run(String source) throws Exception {
        byte[] code=new Compiler().compile("syntax.ma",source,"SyntaxProgram");
        Class<?> program=new ClassLoader(getClass().getClassLoader()){Class<?> load(){return defineClass("SyntaxProgram",code,0,code.length);}}.load();
        var out=new ByteArrayOutputStream();var previous=System.out;
        try{System.setOut(new PrintStream(out,true,StandardCharsets.UTF_8));program.getMethod("main",String[].class).invoke(null,(Object)new String[0]);}finally{System.setOut(previous);}
        return out.toString(StandardCharsets.UTF_8).replace("\r\n","\n");
    }

    @Test void originalVariadicNamesExample() throws Exception {
        assertEquals("- Mouse\n- Teclado\n- Monitor\n",run("""
            on list_items: ...names
                (names) each |name| do
                    show "- $name"
                end
            end
            list_items: "Mouse", "Teclado", "Monitor"
            """));
    }
    @Test void groupedPositionalsCanUseBracketsAtCallSite() throws Exception {
        assertEquals("7\n7\n",run("on total: [a int, b int] -> int = a + b\nshow total 3, 4\nshow total [3, 4]"));
    }
    @Test void untypedRestIsInferredAsVectorFromActualArguments() throws Exception {
        assertEquals("50.0\n",run("""
            on cart_total: ...prices
                var @sum = 0.0
                (prices) each |p| { @sum += p }
                @sum
            end
            show cart_total: 19.9, 5.1, 25.0
            """));
    }
    @Test void zeroRestArgumentsAndFixedPrefix() throws Exception {
        assertEquals("[]\n[1, 2]\n",run("""
            on items: ...xs = xs
            show items
            on label: prefix str, values: ...xs int = xs
            show label "x" values 1, 2
            """));
    }
    @Test void fixedArityWinsEqualTypedVariadicCandidate() throws Exception {
        assertEquals("fixed\nrest\n",run("""
            on pick: ...xs int -> str = "rest"
            on pick: x int -> str = "fixed"
            show pick 1
            show pick 1, 2
            """));
    }
    @Test void variadicGuardBindsTheWholeTail() throws Exception {
        assertEquals("many\n",run("""
            on describe: ...xs int, if (xs) length > 1 = "many"
            on describe: ...xs int = "few"
            show describe 1, 2, 3
            """));
    }
    @Test void variadicElementTypesAreCheckedStatically() {
        assertThrows(Diagnostic.class,()->new Compiler().check("wrong.ma","on total: ...xs int = xs\ntotal 1, \"two\""));
    }
    @Test void parserPreservesMarkerAndRejectsRestBeforeFixedParameters() {
        var method=(Ast.Method)new Frontend("varargs.ma").parse("on total: ...xs int = xs").body().getFirst();
        assertTrue(method.params().getFirst().variadic());
        assertThrows(Diagnostic.class,()->new Frontend("varargs.ma").parse("on f: ...xs, last: x int = x"));
    }
    @Test void explicitOperatorSendAndDynamicOperatorTags() throws Exception {
        assertEquals("5\n#+\n",run("""
            record Amount(Init)
                n int
            end
            impl Amount
                on op +: other Amount -> int = self's #n + other's #n
            end
            show ((Amount) new 2) +: ((Amount) new 3)
            show #+
            """));
    }

}
