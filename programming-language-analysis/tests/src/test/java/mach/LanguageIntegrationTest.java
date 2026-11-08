package mach;

import mach.frontend.Diagnostic;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class LanguageIntegrationTest {
    private String run(String source) throws Exception {
        byte[] bytes=new Compiler().compile("integration.ma",source,"IntegrationProgram");
        Class<?> program=new ClassLoader(getClass().getClassLoader()) {Class<?> loadProgram(){return defineClass("IntegrationProgram",bytes,0,bytes.length);}}.loadProgram();
        var output=new ByteArrayOutputStream();var previous=System.out;
        try{System.setOut(new PrintStream(output,true,StandardCharsets.UTF_8));program.getMethod("main",String[].class).invoke(null,(Object)new String[0]);}
        finally{System.setOut(previous);}
        return output.toString(StandardCharsets.UTF_8).replace("\r\n","\n");
    }
    @Test void abstractMethodsAreInstantiatedForEachRecord() throws Exception {
        assertEquals("Adrian\nAna\n",run("""
            abstract Named
                on name -> str = self's #name
            end
            record Person(Init, Named)
                _name str
            end
            record Customer(Init, Named)
                _name str
            end
            let person = (Person) new "Adrian"
            let customer = (Customer) new "Ana"
            show (person) name
            show (customer) name
            """));
    }
    @Test void abstractDataDefaultAndConcreteOverride() throws Exception {
        assertEquals("dog\nanimal\ntrue\ntrue\n",run("""
            abstract data Animal end
            record Dog & Animal(Init) end
            record Cat & Animal(Init) end
            impl T & Animal
                on sound -> str = "animal"
            end
            impl Dog
                on sound -> str = "dog"
            end
            let dog = (Dog) new
            let cat = (Cat) new
            show (dog) sound
            show (cat) sound
            show Dog & Animal
            show dog isa Animal
            """));
    }
    @Test void abstractDataValueReceivesConcreteDispatch() throws Exception {
        assertEquals("dog\ncat\n",run("""
            abstract data Animal end
            record Dog & Animal(Init) end
            record Cat & Animal(Init) end
            impl Dog
                on sound -> str = "dog"
            end
            impl Cat
                on sound -> str = "cat"
            end
            on hear: animal Animal -> str = (animal) sound
            show hear (Dog) new
            show hear (Cat) new
            """));
    }
    @Test void optionalValuesRequireExplicitRecovery() throws Exception {
        assertEquals("anonymous\nAna\n",run("""
            let missing maybe[str] = nil
            let present maybe[str] = "Ana"
            let first str = missing ?: "anonymous"
            let second str = present ?: "anonymous"
            show first
            show second
            """));
    }
    @Test void maybeNarrowingByNilComparison() throws Exception {
        assertEquals("ANA\n",run("""
            on normalize: name maybe[str] -> str
                if name == nil
                    return "anonymous"
                end
                (name) upcase
            end
            show normalize "Ana"
            """));
    }
    @Test void failGuardPropagatesAndNarrowsSuccess() throws Exception {
        assertEquals("29\n18\n",run("""
            on parse_age: raw str -> try[int]
                let age = (raw) parse: int
                (age) fail? then { return age }
                age + 1
            end
            let age int = (parse_age "28") rescue |failure| { 0 }
            let fallback int = (parse_age "invalid") rescue |failure| { 18 }
            show age
            show fallback
            """));
    }
    @Test void okGuardNarrowsAndHandlesErrorArm() throws Exception {
        assertEquals("ok: 3\nerror: #conversion\n",run("""
            on describe: raw str -> nil
                let value = (raw) parse: int
                (value) ok? then do
                    show "ok: $(value + 1)"
                otherwise do
                    show "error: $((value) kind)"
                end
            end
            describe "2"
            describe "bad"
            """));
    }
    @Test void recoveryCanIntentionallyReturnNil() throws Exception {
        assertEquals("nil\n",run("show ((\"bad\") parse: int) rescue |failure| { nil }"));
    }
    @Test void rescueFailureParameterIsTyped() throws Exception {
        assertEquals("#conversion\n",run("show ((\"bad\") parse: int) rescue |failure| { (failure) kind }"));
    }
    @Test void blockReturnExitsMethodButLambdaReturnIsLocal() throws Exception {
        assertEquals("5\n9\n",run("""
            on via_block -> int
                (true) then { return 5 }
                9
            end
            on via_lambda -> int
                let fn = || { return 5 }
                (fn) result
                9
            end
            show via_block
            show via_lambda
            """));
    }
    @Test void inferredGenericRecordContentIsChecked() throws Exception {
        assertEquals("Ana\n",run("""
            record Box[T](Init)
                _content T
            end
            impl Box
                on content -> T = self's #content
                on replace: value T -> nil
                    self = self | {#content: value}
                end
            end
            let box Box[str] = (Box) new "Adrian"
            (box) replace "Ana"
            show (box) content
            """));
    }
}
