package mach;
import mach.frontend.Diagnostic;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class QuotedCollectionSyntaxTest {
    private String run(String source) throws Exception {
        byte[] code=new Compiler().compile("quoted.ma",source,"QuotedProgram");
        Class<?> program=new ClassLoader(getClass().getClassLoader()){Class<?> load(){return defineClass("QuotedProgram",code,0,code.length);}}.load();
        var output=new ByteArrayOutputStream();PrintStream previous=System.out;
        try{System.setOut(new PrintStream(output,true,StandardCharsets.UTF_8));program.getMethod("main",String[].class).invoke(null,(Object)new String[0]);}finally{System.setOut(previous);}
        return output.toString(StandardCharsets.UTF_8).replace("\r\n","\n");
    }
    @Test void newAccessOperatorsWorkInTheCurrentCompiler() throws Exception {
        assertEquals("Ada\nAda\nBob\n",run("""
            let users = ["Ada", "Bob", "Charlie"]
            let user = #{name: "Ada", company: #{employees: ["Alice", "Bob"]}}
            show (#name of user)
            show (1 nth users)
            show ([#company, #employees, 2] of user)
            """));
    }
    @Test void messageHeadTailRemainAvailable() throws Exception {
        assertEquals("John\n[\"Mary\", \"Bob\"]\nA\n😀B\n",run("""
            let names = ["John", "Mary", "Bob"]
            let text = "A😀B"
            show (names) head
            show (names) tail
            show (text) head
            show (text) tail
            """));
    }
    @Test void possessiveAccessSupportsOnlySingleSelector() throws Exception {
        assertEquals("{#city: \"New York\"}\n",run("""
            let user = #{address: #{city: "New York"}}
            show user's #address
            """));
        assertThrows(Diagnostic.class,()->new Compiler().check("possessive-invalid.ma","let user = #{address: #{city: \"New York\"}}\nshow user's [#address, #city]"));
    }
    @Test void pathAccessIsSafeForMissingSegments() throws Exception {
        assertEquals("New York\nmissing\n",run("""
            let user = #{address: #{city: "New York"}}
            show ([#address, #city] of user)
            show ([#address, #zip] of user) ?: "missing"
            """));
    }
    @Test void objectAccessSupportsIntermediateBinding() throws Exception {
        assertEquals("12345\n",run("""
            let person = #{
                name: "Ana",
                age: 30,
                address: #{
                    street: "123 Main St",
                    city: "Somewhere",
                    zip: "12345"
                }
            }
            let p = #address of person
            show #zip of p
            """));
    }
    @Test void deletionSupportsMultipleValuesAndPreservesTheOriginal() throws Exception {
        assertEquals("[\"Mary\"]\n[\"John\", \"Mary\", \"Bob\"]\n{#age: 61}\n",run("""
            let names = ["John", "Mary", "Bob"]
            show names \\ "John", "Bob"
            show names
            let user = #{name: "John", surname: "Wick", age: 61}
            show user \\ "John", "Wick"
            """.replace("\\","\\\\")));
    }
    @Test void nthIsOneBasedAndRecoverableOnErrors() throws Exception {
        assertEquals("out\nempty\n",run("""
            show (0 nth []) rescue |failure| { "out" }
            show (([]) head) ?: "empty"
            """));
    }
    @Test void legacyQuotedOperatorsAreRejected() {
        assertThrows(Diagnostic.class,()->new Compiler().check("legacy-quoted-nth.ma","let users = [\"Ada\"]\nshow users'nth 1"));
        assertThrows(Diagnostic.class,()->new Compiler().check("legacy-quoted-head.ma","let users = [\"Ada\"]\nshow users'head"));
        assertThrows(Diagnostic.class,()->new Compiler().check("legacy-quoted-tail.ma","let users = [\"Ada\"]\nshow users'tail"));
    }
    @Test void nthRejectsWrongTypesStatically() {
        assertThrows(Diagnostic.class,()->new Compiler().check("nth-invalid.ma","let index str = \"bad\"\nshow index nth [1,2]"));
    }
    @Test void compactLiteralFormsMatchTheOrdinaryCollections() throws Exception {
        assertEquals("true\ntrue\n",run("""
            show %[1 2 3] == [1, 2, 3]
            show %{type: "Object" style: "Without Comma"} == #{type: "Object", style: "Without Comma"}
            """));
        assertThrows(Diagnostic.class,()->new Compiler().check("compact-invalid.ma","let xs = %[1 \"two\"]"));
    }
}
