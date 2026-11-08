package mach;
import mach.frontend.Diagnostic;
import mach.frontend.Frontend;
import mach.ast.Ast;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ComprehensionTest {
    private String run(String source) throws Exception {
        byte[] code=new Compiler().compile("syntax.ma",source,"SyntaxProgram");
        Class<?> program=new ClassLoader(getClass().getClassLoader()){Class<?> load(){return defineClass("SyntaxProgram",code,0,code.length);}}.load();
        var out=new ByteArrayOutputStream();var previous=System.out;
        try{System.setOut(new PrintStream(out,true,StandardCharsets.UTF_8));program.getMethod("main",String[].class).invoke(null,(Object)new String[0]);}finally{System.setOut(previous);}
        return out.toString(StandardCharsets.UTF_8).replace("\r\n","\n");
    }

    @Test void vectorAndFilteredRangePreserveNormativeSyntax() throws Exception {
        assertEquals("[10, 20, 30, 40, 50]\n[2, 4, 6, 8, 10]\n",run("""
            let nums = [1, 2, 3, 4, 5]
            let new_list = for x in nums yield x*10
            show new_list
            let even = for x in 1 to 11 yield x if (x) even?
            show even
            """));
    }
    @Test void objectComprehensionUsesDynamicTagKeys() throws Exception {
        assertEquals("{#ana: 25, #bob: 30}\n",run("""
            let users = #{ana: 25, bob: 30, charlie: 17}
            let adults = for name, age in users yield (name: age) if age >= 18
            show adults
            """));
    }
    @Test void filtersRunBeforeYieldAndIterationNamesDoNotLeak() throws Exception {
        assertEquals("[2, 4]\n2\n",run("""
            var @count = 0
            on mark: x int -> int
                @count += 1
                x
            end
            show for x in 1..4 yield mark x if (x) even?
            show @count
            """));
        assertThrows(Diagnostic.class,()->new Compiler().check("scope.ma","let xs = for x in [1] yield x\nshow x"));
    }
    @Test void nestedComprehensionsAndTypedResults() throws Exception {
        assertEquals("[[2, 3], [3, 4]]\n",run("""
            let result vec[vec[int]] = for x in [1, 2] yield (for y in [1, 2] yield x + y)
            show result
            """));
    }
    @Test void closuresCaptureDistinctIterationBindings() throws Exception {
        assertEquals("1\n2\n3\n",run("""
            let closures = for x in 1..3 yield || { x }
            (closures) each |f| { show (f) result }
            """));
    }
    @Test void objectForBindsBothKeyAndValue() throws Exception {
        assertEquals("#a\n1\n#b\n2\n",run("""
            for k, v in #{a: 1, b: 2}
                show k
                show v
            end
            """));
    }
    @Test void stringComprehensionUsesUnicodeCodePoints() throws Exception {
        assertEquals("[A, 😀]\n",run("show for c in \"A😀\" yield c"));
    }
    @Test void invalidSourcesKeysAndFiltersAreRejectedBeforeExecution() {
        for(String source:new String[]{"let xs = for x in 7 yield x","let xs = for k, v in [1, 2] yield v","let xs = for x in [1] yield (x: x)","let xs = for x in [1] yield x if 1","let xs vec[str] = for x in [1] yield x"})assertThrows(Diagnostic.class,()->new Compiler().check("bad.ma",source));
    }

}
